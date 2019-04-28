#include "hardware_composer.h"

#include <cutils/properties.h>
#include <cutils/sched_policy.h>
#include <fcntl.h>
#include <log/log.h>
#include <poll.h>
#include <stdint.h>
#include <sync/sync.h>
#include <sys/eventfd.h>
#include <sys/prctl.h>
#include <sys/resource.h>
#include <sys/system_properties.h>
#include <sys/timerfd.h>
#include <sys/types.h>
#include <time.h>
#include <unistd.h>
#include <utils/Trace.h>

#include <algorithm>
#include <chrono>
#include <functional>
#include <map>
#include <sstream>
#include <string>
#include <tuple>

#include <dvr/dvr_display_types.h>
#include <dvr/performance_client_api.h>
#include <private/dvr/clock_ns.h>
#include <private/dvr/ion_buffer.h>

using android::hardware::Return;
using android::hardware::Void;
using android::pdx::ErrorStatus;
using android::pdx::LocalHandle;
using android::pdx::Status;
using android::pdx::rpc::EmptyVariant;
using android::pdx::rpc::IfAnyOf;

using namespace std::chrono_literals;

namespace android {
namespace dvr {

namespace {

const char kBacklightBrightnessSysFile[] =
    "/sys/class/leds/lcd-backlight/brightness";

const char kDvrPerformanceProperty[] = "sys.dvr.performance";
const char kDvrStandaloneProperty[] = "ro.boot.vr";

const char kRightEyeOffsetProperty[] = "dvr.right_eye_offset_ns";

// Get time offset from a vsync to when the pose for that vsync should be
// predicted out to. For example, if scanout gets halfway through the frame
// at the halfway point between vsyncs, then this could be half the period.
// With global shutter displays, this should be changed to the offset to when
// illumination begins. Low persistence adds a frame of latency, so we predict
// to the center of the next frame.
inline int64_t GetPosePredictionTimeOffset(int64_t vsync_period_ns) {
  return (vsync_period_ns * 150) / 100;
}

// Attempts to set the scheduler class and partiton for the current thread.
// Returns true on success or false on failure.
bool SetThreadPolicy(const std::string& scheduler_class,
                     const std::string& partition) {
  int error = dvrSetSchedulerClass(0, scheduler_class.c_str());
  if (error < 0) {
    ALOGE(
        "SetThreadPolicy: Failed to set scheduler class \"%s\" for "
        "thread_id=%d: %s",
        scheduler_class.c_str(), gettid(), strerror(-error));
    return false;
  }
  error = dvrSetCpuPartition(0, partition.c_str());
  if (error < 0) {
    ALOGE(
        "SetThreadPolicy: Failed to set cpu partiton \"%s\" for thread_id=%d: "
        "%s",
        partition.c_str(), gettid(), strerror(-error));
    return false;
  }
  return true;
}

// Utility to generate scoped tracers with arguments.
// TODO(eieio): Move/merge this into utils/Trace.h?
class TraceArgs {
 public:
  template <typename... Args>
  TraceArgs(const char* format, Args&&... args) {
    std::array<char, 1024> buffer;
    snprintf(buffer.data(), buffer.size(), format, std::forward<Args>(args)...);
    atrace_begin(ATRACE_TAG, buffer.data());
  }

  ~TraceArgs() { atrace_end(ATRACE_TAG); }

 private:
  TraceArgs(const TraceArgs&) = delete;
  void operator=(const TraceArgs&) = delete;
};

// Macro to define a scoped tracer with arguments. Uses PASTE(x, y) macro
// defined in utils/Trace.h.
#define TRACE_FORMAT(format, ...) \
  TraceArgs PASTE(__tracer, __LINE__) { format, ##__VA_ARGS__ }

}  // anonymous namespace

HardwareComposer::HardwareComposer()
    : initialized_(false), request_display_callback_(nullptr) {}

HardwareComposer::~HardwareComposer(void) {
  UpdatePostThreadState(PostThreadState::Quit, true);
  if (post_thread_.joinable())
    post_thread_.join();
}

bool HardwareComposer::Initialize(
    Hwc2::Composer* composer, RequestDisplayCallback request_display_callback) {
  if (initialized_) {
    ALOGE("HardwareComposer::Initialize: already initialized.");
    return false;
  }

  is_standalone_device_ = property_get_bool(kDvrStandaloneProperty, false);

  request_display_callback_ = request_display_callback;

  HWC::Error error = HWC::Error::None;

  Hwc2::Config config;
  error = composer->getActiveConfig(HWC_DISPLAY_PRIMARY, &config);

  if (error != HWC::Error::None) {
    ALOGE("HardwareComposer: Failed to get current display config : %d",
          config);
    return false;
  }

  error = GetDisplayMetrics(composer, HWC_DISPLAY_PRIMARY, config,
                            &native_display_metrics_);

  if (error != HWC::Error::None) {
    ALOGE(
        "HardwareComposer: Failed to get display attributes for current "
        "configuration : %d",
        error.value);
    return false;
  }

  ALOGI(
      "HardwareComposer: primary display attributes: width=%d height=%d "
      "vsync_period_ns=%d DPI=%dx%d",
      native_display_metrics_.width, native_display_metrics_.height,
      native_display_metrics_.vsync_period_ns, native_display_metrics_.dpi.x,
      native_display_metrics_.dpi.y);

  // Set the display metrics but never use rotation to avoid the long latency of
  // rotation processing in hwc.
  display_transform_ = HWC_TRANSFORM_NONE;
  display_metrics_ = native_display_metrics_;

  // Setup the display metrics used by all Layer instances.
  Layer::SetDisplayMetrics(native_display_metrics_);

  post_thread_event_fd_.Reset(eventfd(0, EFD_CLOEXEC | EFD_NONBLOCK));
  LOG_ALWAYS_FATAL_IF(
      !post_thread_event_fd_,
      "HardwareComposer: Failed to create interrupt event fd : %s",
      strerror(errno));

  post_thread_ = std::thread(&HardwareComposer::PostThread, this);

  initialized_ = true;

  return initialized_;
}

void HardwareComposer::Enable() {
  UpdatePostThreadState(PostThreadState::Suspended, false);
}

void HardwareComposer::Disable() {
  UpdatePostThreadState(PostThreadState::Suspended, true);
}

// Update the post thread quiescent state based on idle and suspended inputs.
void HardwareComposer::UpdatePostThreadState(PostThreadStateType state,
                                             bool suspend) {
  std::unique_lock<std::mutex> lock(post_thread_mutex_);

  // Update the votes in the state variable before evaluating the effective
  // quiescent state. Any bits set in post_thread_state_ indicate that the post
  // thread should be suspended.
  if (suspend) {
    post_thread_state_ |= state;
  } else {
    post_thread_state_ &= ~state;
  }

  const bool quit = post_thread_state_ & PostThreadState::Quit;
  const bool effective_suspend = post_thread_state_ != PostThreadState::Active;
  if (quit) {
    post_thread_quiescent_ = true;
    eventfd_write(post_thread_event_fd_.Get(), 1);
    post_thread_wait_.notify_one();
  } else if (effective_suspend && !post_thread_quiescent_) {
    post_thread_quiescent_ = true;
    eventfd_write(post_thread_event_fd_.Get(), 1);
  } else if (!effective_suspend && post_thread_quiescent_) {
    post_thread_quiescent_ = false;
    eventfd_t value;
    eventfd_read(post_thread_event_fd_.Get(), &value);
    post_thread_wait_.notify_one();
  }

  // Wait until the post thread is in the requested state.
  post_thread_ready_.wait(lock, [this, effective_suspend] {
    return effective_suspend != post_thread_resumed_;
  });
}

void HardwareComposer::OnPostThreadResumed() {
  // Phones create a new composer client on resume and destroy it on pause.
  // Standalones only create the composer client once and then use SetPowerMode
  // to control the screen on pause/resume.
  if (!is_standalone_device_ || !composer_) {
    composer_.reset(new Hwc2::Composer(false));
    composer_callback_ = new ComposerCallback;
    composer_->registerCallback(composer_callback_);
    Layer::SetComposer(composer_.get());
  } else {
    SetPowerMode(true);
  }

  EnableVsync(true);

  // TODO(skiazyk): We need to do something about accessing this directly,
  // supposedly there is a backlight service on the way.
  // TODO(steventhomas): When we change the backlight setting, will surface
  // flinger (or something else) set it back to its original value once we give
  // control of the display back to surface flinger?
  SetBacklightBrightness(255);

  // Trigger target-specific performance mode change.
  property_set(kDvrPerformanceProperty, "performance");
}

void HardwareComposer::OnPostThreadPaused() {
  retire_fence_fds_.clear();
  layers_.clear();

  if (composer_) {
    EnableVsync(false);
  }

  if (!is_standalone_device_) {
    composer_callback_ = nullptr;
    composer_.reset(nullptr);
    Layer::SetComposer(nullptr);
  } else {
    SetPowerMode(false);
  }

  // Trigger target-specific performance mode change.
  property_set(kDvrPerformanceProperty, "idle");
}

HWC::Error HardwareComposer::Validate(hwc2_display_t display) {
  uint32_t num_types;
  uint32_t num_requests;
  HWC::Error error =
      composer_->validateDisplay(display, &num_types, &num_requests);

  if (error == HWC2_ERROR_HAS_CHANGES) {
    // TODO(skiazyk): We might need to inspect the requested changes first, but
    // so far it seems like we shouldn't ever hit a bad state.
    // error = hwc2_funcs_.accept_display_changes_fn_(hardware_composer_device_,
    //                                               display);
    error = composer_->acceptDisplayChanges(display);
  }

  return error;
}

HWC::Error HardwareComposer::EnableVsync(bool enabled) {
  return composer_->setVsyncEnabled(
      HWC_DISPLAY_PRIMARY,
      (Hwc2::IComposerClient::Vsync)(enabled ? HWC2_VSYNC_ENABLE
                                             : HWC2_VSYNC_DISABLE));
}

HWC::Error HardwareComposer::SetPowerMode(bool active) {
  HWC::PowerMode power_mode = active ? HWC::PowerMode::On : HWC::PowerMode::Off;
  return composer_->setPowerMode(
      HWC_DISPLAY_PRIMARY, power_mode.cast<Hwc2::IComposerClient::PowerMode>());
}

HWC::Error HardwareComposer::Present(hwc2_display_t display) {
  int32_t present_fence;
  HWC::Error error = composer_->presentDisplay(display, &present_fence);

  // According to the documentation, this fence is signaled at the time of
  // vsync/DMA for physical displays.
  if (error == HWC::Error::None) {
    ATRACE_INT("HardwareComposer: VsyncFence", present_fence);
    retire_fence_fds_.emplace_back(present_fence);
  } else {
    ATRACE_INT("HardwareComposer: PresentResult", error);
  }

  return error;
}

HWC::Error HardwareComposer::GetDisplayAttribute(Hwc2::Composer* composer,
                                                 hwc2_display_t display,
                                                 hwc2_config_t config,
                                                 hwc2_attribute_t attribute,
                                                 int32_t* out_value) const {
  return composer->getDisplayAttribute(
      display, config, (Hwc2::IComposerClient::Attribute)attribute, out_value);
}

HWC::Error HardwareComposer::GetDisplayMetrics(
    Hwc2::Composer* composer, hwc2_display_t display, hwc2_config_t config,
    HWCDisplayMetrics* out_metrics) const {
  HWC::Error error;

  error = GetDisplayAttribute(composer, display, config, HWC2_ATTRIBUTE_WIDTH,
                              &out_metrics->width);
  if (error != HWC::Error::None) {
    ALOGE(
        "HardwareComposer::GetDisplayMetrics: Failed to get display width: %s",
        error.to_string().c_str());
    return error;
  }

  error = GetDisplayAttribute(composer, display, config, HWC2_ATTRIBUTE_HEIGHT,
                              &out_metrics->height);
  if (error != HWC::Error::None) {
    ALOGE(
        "HardwareComposer::GetDisplayMetrics: Failed to get display height: %s",
        error.to_string().c_str());
    return error;
  }

  error = GetDisplayAttribute(composer, display, config,
                              HWC2_ATTRIBUTE_VSYNC_PERIOD,
                              &out_metrics->vsync_period_ns);
  if (error != HWC::Error::None) {
    ALOGE(
        "HardwareComposer::GetDisplayMetrics: Failed to get display height: %s",
        error.to_string().c_str());
    return error;
  }

  error = GetDisplayAttribute(composer, display, config, HWC2_ATTRIBUTE_DPI_X,
                              &out_metrics->dpi.x);
  if (error != HWC::Error::None) {
    ALOGE(
        "HardwareComposer::GetDisplayMetrics: Failed to get display DPI X: %s",
        error.to_string().c_str());
    return error;
  }

  error = GetDisplayAttribute(composer, display, config, HWC2_ATTRIBUTE_DPI_Y,
                              &out_metrics->dpi.y);
  if (error != HWC::Error::None) {
    ALOGE(
        "HardwareComposer::GetDisplayMetrics: Failed to get display DPI Y: %s",
        error.to_string().c_str());
    return error;
  }

  return HWC::Error::None;
}

std::string HardwareComposer::Dump() {
  std::unique_lock<std::mutex> lock(post_thread_mutex_);
  std::ostringstream stream;

  stream << "Display metrics:     " << display_metrics_.width << "x"
         << display_metrics_.height << " " << (display_metrics_.dpi.x / 1000.0)
         << "x" << (display_metrics_.dpi.y / 1000.0) << " dpi @ "
         << (1000000000.0 / display_metrics_.vsync_period_ns) << " Hz"
         << std::endl;

  stream << "Post thread resumed: " << post_thread_resumed_ << std::endl;
  stream << "Active layers:       " << layers_.size() << std::endl;
  stream << std::endl;

  for (size_t i = 0; i < layers_.size(); i++) {
    stream << "Layer " << i << ":";
    stream << " type=" << layers_[i].GetCompositionType().to_string();
    stream << " surface_id=" << layers_[i].GetSurfaceId();
    stream << " buffer_id=" << layers_[i].GetBufferId();
    stream << std::endl;
  }
  stream << std::endl;

  if (post_thread_resumed_) {
    stream << "Hardware Composer Debug Info:" << std::endl;
    stream << composer_->dumpDebugInfo();
  }

  return stream.str();
}

void HardwareComposer::PostLayers() {
  ATRACE_NAME("HardwareComposer::PostLayers");

  // Setup the hardware composer layers with current buffers.
  for (auto& layer : layers_) {
    layer.Prepare();
  }

  HWC::Error error = Validate(HWC_DISPLAY_PRIMARY);
  if (error != HWC::Error::None) {
    ALOGE("HardwareComposer::PostLayers: Validate failed: %s",
          error.to_string().c_str());
    return;
  }

  // Now that we have taken in a frame from the application, we have a chance
  // to drop the frame before passing the frame along to HWC.
  // If the display driver has become backed up, we detect it here and then
  // react by skipping this frame to catch up latency.
  while (!retire_fence_fds_.empty() &&
         (!retire_fence_fds_.front() ||
          sync_wait(retire_fence_fds_.front().Get(), 0) == 0)) {
    // There are only 2 fences in here, no performance problem to shift the
    // array of ints.
    retire_fence_fds_.erase(retire_fence_fds_.begin());
  }

  const bool is_fence_pending = static_cast<int32_t>(retire_fence_fds_.size()) >
                                post_thread_config_.allowed_pending_fence_count;

  if (is_fence_pending) {
    ATRACE_INT("frame_skip_count", ++frame_skip_count_);

    ALOGW_IF(is_fence_pending,
             "Warning: dropping a frame to catch up with HWC (pending = %zd)",
             retire_fence_fds_.size());

    for (auto& layer : layers_) {
      layer.Drop();
    }
    return;
  } else {
    // Make the transition more obvious in systrace when the frame skip happens
    // above.
    ATRACE_INT("frame_skip_count", 0);
  }

#if TRACE > 1
  for (size_t i = 0; i < layers_.size(); i++) {
    ALOGI("HardwareComposer::PostLayers: layer=%zu buffer_id=%d composition=%s",
          i, layers_[i].GetBufferId(),
          layers_[i].GetCompositionType().to_string().c_str());
  }
#endif

  error = Present(HWC_DISPLAY_PRIMARY);
  if (error != HWC::Error::None) {
    ALOGE("HardwareComposer::PostLayers: Present failed: %s",
          error.to_string().c_str());
    return;
  }

  std::vector<Hwc2::Layer> out_layers;
  std::vector<int> out_fences;
  error = composer_->getReleaseFences(HWC_DISPLAY_PRIMARY, &out_layers,
                                      &out_fences);
  ALOGE_IF(error != HWC::Error::None,
           "HardwareComposer::PostLayers: Failed to get release fences: %s",
           error.to_string().c_str());

  // Perform post-frame bookkeeping.
  uint32_t num_elements = out_layers.size();
  for (size_t i = 0; i < num_elements; ++i) {
    for (auto& layer : layers_) {
      if (layer.GetLayerHandle() == out_layers[i]) {
        layer.Finish(out_fences[i]);
      }
    }
  }
}

void HardwareComposer::SetDisplaySurfaces(
    std::vector<std::shared_ptr<DirectDisplaySurface>> surfaces) {
  ALOGI("HardwareComposer::SetDisplaySurfaces: surface count=%zd",
        surfaces.size());
  const bool display_idle = surfaces.size() == 0;
  {
    std::unique_lock<std::mutex> lock(post_thread_mutex_);
    pending_surfaces_ = std::move(surfaces);
  }

  if (request_display_callback_ && (!is_standalone_device_ || !composer_))
    request_display_callback_(!display_idle);

  // Set idle state based on whether there are any surfaces to handle.
  UpdatePostThreadState(PostThreadState::Idle, display_idle);
}

int HardwareComposer::OnNewGlobalBuffer(DvrGlobalBufferKey key,
                                        IonBuffer& ion_buffer) {
  if (key == DvrGlobalBuffers::kVsyncBuffer) {
    vsync_ring_ = std::make_unique<CPUMappedBroadcastRing<DvrVsyncRing>>(
        &ion_buffer, CPUUsageMode::WRITE_OFTEN);

    if (vsync_ring_->IsMapped() == false) {
      return -EPERM;
    }
  }

  if (key == DvrGlobalBuffers::kVrFlingerConfigBufferKey) {
    return MapConfigBuffer(ion_buffer);
  }

  return 0;
}

void HardwareComposer::OnDeletedGlobalBuffer(DvrGlobalBufferKey key) {
  if (key == DvrGlobalBuffers::kVrFlingerConfigBufferKey) {
    ConfigBufferDeleted();
  }
}

int HardwareComposer::MapConfigBuffer(IonBuffer& ion_buffer) {
  std::lock_guard<std::mutex> lock(shared_config_mutex_);
  shared_config_ring_ = DvrConfigRing();

  if (ion_buffer.width() < DvrConfigRing::MemorySize()) {
    ALOGE("HardwareComposer::MapConfigBuffer: invalid buffer size.");
    return -EINVAL;
  }

  void* buffer_base = 0;
  int result = ion_buffer.Lock(ion_buffer.usage(), 0, 0, ion_buffer.width(),
                               ion_buffer.height(), &buffer_base);
  if (result != 0) {
    ALOGE(
        "HardwareComposer::MapConfigBuffer: Failed to map vrflinger config "
        "buffer.");
    return -EPERM;
  }

  shared_config_ring_ = DvrConfigRing::Create(buffer_base, ion_buffer.width());
  ion_buffer.Unlock();

  return 0;
}

void HardwareComposer::ConfigBufferDeleted() {
  std::lock_guard<std::mutex> lock(shared_config_mutex_);
  shared_config_ring_ = DvrConfigRing();
}

void HardwareComposer::UpdateConfigBuffer() {
  std::lock_guard<std::mutex> lock(shared_config_mutex_);
  if (!shared_config_ring_.is_valid())
    return;
  // Copy from latest record in shared_config_ring_ to local copy.
  DvrConfig record;
  if (shared_config_ring_.GetNewest(&shared_config_ring_sequence_, &record)) {
    post_thread_config_ = record;
  }
}

int HardwareComposer::PostThreadPollInterruptible(
    const pdx::LocalHandle& event_fd, int requested_events, int timeout_ms) {
  pollfd pfd[2] = {
      {
          .fd = event_fd.Get(),
          .events = static_cast<short>(requested_events),
          .revents = 0,
      },
      {
          .fd = post_thread_event_fd_.Get(),
          .events = POLLPRI | POLLIN,
          .revents = 0,
      },
  };
  int ret, error;
  do {
    ret = poll(pfd, 2, timeout_ms);
    error = errno;
    ALOGW_IF(ret < 0,
             "HardwareComposer::PostThreadPollInterruptible: Error during "
             "poll(): %s (%d)",
             strerror(error), error);
  } while (ret < 0 && error == EINTR);

  if (ret < 0) {
    return -error;
  } else if (ret == 0) {
    return -ETIMEDOUT;
  } else if (pfd[0].revents != 0) {
    return 0;
  } else if (pfd[1].revents != 0) {
    ALOGI("VrHwcPost thread interrupted: revents=%x", pfd[1].revents);
    return kPostThreadInterrupted;
  } else {
    return 0;
  }
}

Status<int64_t> HardwareComposer::GetVSyncTime() {
  auto status = composer_callback_->GetVsyncTime(HWC_DISPLAY_PRIMARY);
  ALOGE_IF(!status,
           "HardwareComposer::GetVSyncTime: Failed to get vsync timestamp: %s",
           status.GetErrorMessage().c_str());
  return status;
}

// Waits for the next vsync and returns the timestamp of the vsync event. If
// vsync already passed since the last call, returns the latest vsync timestamp
// instead of blocking.
Status<int64_t> HardwareComposer::WaitForVSync() {
  const int64_t predicted_vsync_time =
      last_vsync_timestamp_ +
      display_metrics_.vsync_period_ns * vsync_prediction_interval_;
  const int error = SleepUntil(predicted_vsync_time);
  if (error < 0) {
    ALOGE("HardwareComposer::WaifForVSync:: Failed to sleep: %s",
          strerror(-error));
    return error;
  }
  return {predicted_vsync_time};
}

int HardwareComposer::SleepUntil(int64_t wakeup_timestamp) {
  const int timer_fd = vsync_sleep_timer_fd_.Get();
  const itimerspec wakeup_itimerspec = {
      .it_interval = {.tv_sec = 0, .tv_nsec = 0},
      .it_value = NsToTimespec(wakeup_timestamp),
  };
  int ret =
      timerfd_settime(timer_fd, TFD_TIMER_ABSTIME, &wakeup_itimerspec, nullptr);
  int error = errno;
  if (ret < 0) {
    ALOGE("HardwareComposer::SleepUntil: Failed to set timerfd: %s",
          strerror(error));
    return -error;
  }

  return PostThreadPollInterruptible(vsync_sleep_timer_fd_, POLLIN,
                                     /*timeout_ms*/ -1);
}

void HardwareComposer::PostThread() {
  // NOLINTNEXTLINE(runtime/int)
  prctl(PR_SET_NAME, reinterpret_cast<unsigned long>("VrHwcPost"), 0, 0, 0);

  // Set the scheduler to SCHED_FIFO with high priority. If this fails here
  // there may have been a startup timing issue between this thread and
  // performanced. Try again later when this thread becomes active.
  bool thread_policy_setup =
      SetThreadPolicy("graphics:high", "/system/performance");

#if ENABLE_BACKLIGHT_BRIGHTNESS
  // TODO(hendrikw): This isn't required at the moment. It's possible that there
  //                 is another method to access this when needed.
  // Open the backlight brightness control sysfs node.
  backlight_brightness_fd_ = LocalHandle(kBacklightBrightnessSysFile, O_RDWR);
  ALOGW_IF(!backlight_brightness_fd_,
           "HardwareComposer: Failed to open backlight brightness control: %s",
           strerror(errno));
#endif  // ENABLE_BACKLIGHT_BRIGHTNESS

  // Create a timerfd based on CLOCK_MONOTINIC.
  vsync_sleep_timer_fd_.Reset(timerfd_create(CLOCK_MONOTONIC, 0));
  LOG_ALWAYS_FATAL_IF(
      !vsync_sleep_timer_fd_,
      "HardwareComposer: Failed to create vsync sleep timerfd: %s",
      strerror(errno));

  const int64_t ns_per_frame = display_metrics_.vsync_period_ns;
  const int64_t photon_offset_ns = GetPosePredictionTimeOffset(ns_per_frame);

  // TODO(jbates) Query vblank time from device, when such an API is available.
  // This value (6.3%) was measured on A00 in low persistence mode.
  int64_t vblank_ns = ns_per_frame * 63 / 1000;
  int64_t right_eye_photon_offset_ns = (ns_per_frame - vblank_ns) / 2;

  // Check property for overriding right eye offset value.
  right_eye_photon_offset_ns =
      property_get_int64(kRightEyeOffsetProperty, right_eye_photon_offset_ns);

  bool was_running = false;

  while (1) {
    ATRACE_NAME("HardwareComposer::PostThread");

    // Check for updated config once per vsync.
    UpdateConfigBuffer();

    while (post_thread_quiescent_) {
      std::unique_lock<std::mutex> lock(post_thread_mutex_);
      ALOGI("HardwareComposer::PostThread: Entering quiescent state.");

      // Tear down resources if necessary.
      if (was_running)
        OnPostThreadPaused();

      was_running = false;
      post_thread_resumed_ = false;
      post_thread_ready_.notify_all();

      if (post_thread_state_ & PostThreadState::Quit) {
        ALOGI("HardwareComposer::PostThread: Quitting.");
        return;
      }

      post_thread_wait_.wait(lock, [this] { return !post_thread_quiescent_; });

      post_thread_resumed_ = true;
      post_thread_ready_.notify_all();

      ALOGI("HardwareComposer::PostThread: Exiting quiescent state.");
    }

    if (!was_running) {
      // Setup resources.
      OnPostThreadResumed();
      was_running = true;

      // Try to setup the scheduler policy if it failed during startup. Only
      // attempt to do this on transitions from inactive to active to avoid
      // spamming the system with RPCs and log messages.
      if (!thread_policy_setup) {
        thread_policy_setup =
            SetThreadPolicy("graphics:high", "/system/performance");
      }

      // Initialize the last vsync timestamp with the current time. The
      // predictor below uses this time + the vsync interval in absolute time
      // units for the initial delay. Once the driver starts reporting vsync the
      // predictor will sync up with the real vsync.
      last_vsync_timestamp_ = GetSystemClockNs();
    }

    int64_t vsync_timestamp = 0;
    {
      TRACE_FORMAT("wait_vsync|vsync=%u;last_timestamp=%" PRId64
                   ";prediction_interval=%d|",
                   vsync_count_ + 1, last_vsync_timestamp_,
                   vsync_prediction_interval_);

      auto status = WaitForVSync();
      ALOGE_IF(
          !status,
          "HardwareComposer::PostThread: Failed to wait for vsync event: %s",
          status.GetErrorMessage().c_str());

      // If there was an error either sleeping was interrupted due to pausing or
      // there was an error getting the latest timestamp.
      if (!status)
        continue;

      // Predicted vsync timestamp for this interval. This is stable because we
      // use absolute time for the wakeup timer.
      vsync_timestamp = status.get();
    }

    // Advance the vsync counter only if the system is keeping up with hardware
    // vsync to give clients an indication of the delays.
    if (vsync_prediction_interval_ == 1)
      ++vsync_count_;

    const bool layer_config_changed = UpdateLayerConfig();

    // Publish the vsync event.
    if (vsync_ring_) {
      DvrVsync vsync;
      vsync.vsync_count = vsync_count_;
      vsync.vsync_timestamp_ns = vsync_timestamp;
      vsync.vsync_left_eye_offset_ns = photon_offset_ns;
      vsync.vsync_right_eye_offset_ns = right_eye_photon_offset_ns;
      vsync.vsync_period_ns = ns_per_frame;

      vsync_ring_->Publish(vsync);
    }

    // Signal all of the vsync clients. Because absolute time is used for the
    // wakeup time below, this can take a little time if necessary.
    if (vsync_callback_)
      vsync_callback_(HWC_DISPLAY_PRIMARY, vsync_timestamp,
                      /*frame_time_estimate*/ 0, vsync_count_);

    {
      // Sleep until shortly before vsync.
      ATRACE_NAME("sleep");

      const int64_t display_time_est_ns = vsync_timestamp + ns_per_frame;
      const int64_t now_ns = GetSystemClockNs();
      const int64_t sleep_time_ns = display_time_est_ns - now_ns -
                                    post_thread_config_.frame_post_offset_ns;
      const int64_t wakeup_time_ns =
          display_time_est_ns - post_thread_config_.frame_post_offset_ns;

      ATRACE_INT64("sleep_time_ns", sleep_time_ns);
      if (sleep_time_ns > 0) {
        int error = SleepUntil(wakeup_time_ns);
        ALOGE_IF(error < 0, "HardwareComposer::PostThread: Failed to sleep: %s",
                 strerror(-error));
        if (error == kPostThreadInterrupted) {
          if (layer_config_changed) {
            // If the layer config changed we need to validateDisplay() even if
            // we're going to drop the frame, to flush the Composer object's
            // internal command buffer and apply our layer changes.
            Validate(HWC_DISPLAY_PRIMARY);
          }
          continue;
        }
      }
    }

    {
      auto status = GetVSyncTime();
      if (!status) {
        ALOGE("HardwareComposer::PostThread: Failed to get VSYNC time: %s",
              status.GetErrorMessage().c_str());
      }

      // If we failed to read vsync there might be a problem with the driver.
      // Since there's nothing we can do just behave as though we didn't get an
      // updated vsync time and let the prediction continue.
      const int64_t current_vsync_timestamp =
          status ? status.get() : last_vsync_timestamp_;

      const bool vsync_delayed =
          last_vsync_timestamp_ == current_vsync_timestamp;
      ATRACE_INT("vsync_delayed", vsync_delayed);

      // If vsync was delayed advance the prediction interval and allow the
      // fence logic in PostLayers() to skip the frame.
      if (vsync_delayed) {
        ALOGW(
            "HardwareComposer::PostThread: VSYNC timestamp did not advance "
            "since last frame: timestamp=%" PRId64 " prediction_interval=%d",
            current_vsync_timestamp, vsync_prediction_interval_);
        vsync_prediction_interval_++;
      } else {
        // We have an updated vsync timestamp, reset the prediction interval.
        last_vsync_timestamp_ = current_vsync_timestamp;
        vsync_prediction_interval_ = 1;
      }
    }

    PostLayers();
  }
}

// Checks for changes in the surface stack and updates the layer config to
// accomodate the new stack.
bool HardwareComposer::UpdateLayerConfig() {
  std::vector<std::shared_ptr<DirectDisplaySurface>> surfaces;
  {
    std::unique_lock<std::mutex> lock(post_thread_mutex_);
    if (pending_surfaces_.empty())
      return false;

    surfaces = std::move(pending_surfaces_);
  }

  ATRACE_NAME("UpdateLayerConfig_HwLayers");

  // Sort the new direct surface list by z-order to determine the relative order
  // of the surfaces. This relative order is used for the HWC z-order value to
  // insulate VrFlinger and HWC z-order semantics from each other.
  std::sort(surfaces.begin(), surfaces.end(), [](const auto& a, const auto& b) {
    return a->z_order() < b->z_order();
  });

  // Prepare a new layer stack, pulling in layers from the previous
  // layer stack that are still active and updating their attributes.
  std::vector<Layer> layers;
  size_t layer_index = 0;
  for (const auto& surface : surfaces) {
    // The bottom layer is opaque, other layers blend.
    HWC::BlendMode blending =
        layer_index == 0 ? HWC::BlendMode::None : HWC::BlendMode::Coverage;

    // Try to find a layer for this surface in the set of active layers.
    auto search =
        std::lower_bound(layers_.begin(), layers_.end(), surface->surface_id());
    const bool found = search != layers_.end() &&
                       search->GetSurfaceId() == surface->surface_id();
    if (found) {
      // Update the attributes of the layer that may have changed.
      search->SetBlending(blending);
      search->SetZOrder(layer_index);  // Relative z-order.

      // Move the existing layer to the new layer set and remove the empty layer
      // object from the current set.
      layers.push_back(std::move(*search));
      layers_.erase(search);
    } else {
      // Insert a layer for the new surface.
      layers.emplace_back(surface, blending, display_transform_,
                          HWC::Composition::Device, layer_index);
    }

    ALOGI_IF(
        TRACE,
        "HardwareComposer::UpdateLayerConfig: layer_index=%zu surface_id=%d",
        layer_index, layers[layer_index].GetSurfaceId());

    layer_index++;
  }

  // Sort the new layer stack by ascending surface id.
  std::sort(layers.begin(), layers.end());

  // Replace the previous layer set with the new layer set. The destructor of
  // the previous set will clean up the remaining Layers that are not moved to
  // the new layer set.
  layers_ = std::move(layers);

  ALOGD_IF(TRACE, "HardwareComposer::UpdateLayerConfig: %zd active layers",
           layers_.size());
  return true;
}

void HardwareComposer::SetVSyncCallback(VSyncCallback callback) {
  vsync_callback_ = callback;
}

void HardwareComposer::SetBacklightBrightness(int brightness) {
  if (backlight_brightness_fd_) {
    std::array<char, 32> text;
    const int length = snprintf(text.data(), text.size(), "%d", brightness);
    write(backlight_brightness_fd_.Get(), text.data(), length);
  }
}

Return<void> HardwareComposer::ComposerCallback::onHotplug(
    Hwc2::Display display, IComposerCallback::Connection /*conn*/) {
  // See if the driver supports the vsync_event node in sysfs.
  if (display < HWC_NUM_PHYSICAL_DISPLAY_TYPES &&
      !displays_[display].driver_vsync_event_fd) {
    std::array<char, 1024> buffer;
    snprintf(buffer.data(), buffer.size(),
             "/sys/class/graphics/fb%" PRIu64 "/vsync_event", display);
    if (LocalHandle handle{buffer.data(), O_RDONLY}) {
      ALOGI(
          "HardwareComposer::ComposerCallback::onHotplug: Driver supports "
          "vsync_event node for display %" PRIu64,
          display);
      displays_[display].driver_vsync_event_fd = std::move(handle);
    } else {
      ALOGI(
          "HardwareComposer::ComposerCallback::onHotplug: Driver does not "
          "support vsync_event node for display %" PRIu64,
          display);
    }
  }

  return Void();
}

Return<void> HardwareComposer::ComposerCallback::onRefresh(
    Hwc2::Display /*display*/) {
  return hardware::Void();
}

Return<void> HardwareComposer::ComposerCallback::onVsync(Hwc2::Display display,
                                                         int64_t timestamp) {
  TRACE_FORMAT("vsync_callback|display=%" PRIu64 ";timestamp=%" PRId64 "|",
               display, timestamp);
  if (display < HWC_NUM_PHYSICAL_DISPLAY_TYPES) {
    displays_[display].callback_vsync_timestamp = timestamp;
  } else {
    ALOGW(
        "HardwareComposer::ComposerCallback::onVsync: Received vsync on "
        "non-physical display: display=%" PRId64,
        display);
  }
  return Void();
}

Status<int64_t> HardwareComposer::ComposerCallback::GetVsyncTime(
    Hwc2::Display display) {
  if (display >= HWC_NUM_PHYSICAL_DISPLAY_TYPES) {
    ALOGE(
        "HardwareComposer::ComposerCallback::GetVsyncTime: Invalid physical "
        "display requested: display=%" PRIu64,
        display);
    return ErrorStatus(EINVAL);
  }

  // See if the driver supports direct vsync events.
  LocalHandle& event_fd = displays_[display].driver_vsync_event_fd;
  if (!event_fd) {
    // Fall back to returning the last timestamp returned by the vsync
    // callback.
    std::lock_guard<std::mutex> autolock(vsync_mutex_);
    return displays_[display].callback_vsync_timestamp;
  }

  // When the driver supports the vsync_event sysfs node we can use it to
  // determine the latest vsync timestamp, even if the HWC callback has been
  // delayed.

  // The driver returns data in the form "VSYNC=<timestamp ns>".
  std::array<char, 32> data;
  data.fill('\0');

  // Seek back to the beginning of the event file.
  int ret = lseek(event_fd.Get(), 0, SEEK_SET);
  if (ret < 0) {
    const int error = errno;
    ALOGE(
        "HardwareComposer::ComposerCallback::GetVsyncTime: Failed to seek "
        "vsync event fd: %s",
        strerror(error));
    return ErrorStatus(error);
  }

  // Read the vsync event timestamp.
  ret = read(event_fd.Get(), data.data(), data.size());
  if (ret < 0) {
    const int error = errno;
    ALOGE_IF(error != EAGAIN,
             "HardwareComposer::ComposerCallback::GetVsyncTime: Error "
             "while reading timestamp: %s",
             strerror(error));
    return ErrorStatus(error);
  }

  int64_t timestamp;
  ret = sscanf(data.data(), "VSYNC=%" PRIu64,
               reinterpret_cast<uint64_t*>(&timestamp));
  if (ret < 0) {
    const int error = errno;
    ALOGE(
        "HardwareComposer::ComposerCallback::GetVsyncTime: Error while "
        "parsing timestamp: %s",
        strerror(error));
    return ErrorStatus(error);
  }

  return {timestamp};
}

Hwc2::Composer* Layer::composer_{nullptr};
HWCDisplayMetrics Layer::display_metrics_{0, 0, {0, 0}, 0};

void Layer::Reset() {
  if (hardware_composer_layer_) {
    composer_->destroyLayer(HWC_DISPLAY_PRIMARY, hardware_composer_layer_);
    hardware_composer_layer_ = 0;
  }

  z_order_ = 0;
  blending_ = HWC::BlendMode::None;
  transform_ = HWC::Transform::None;
  composition_type_ = HWC::Composition::Invalid;
  target_composition_type_ = composition_type_;
  source_ = EmptyVariant{};
  acquire_fence_.Close();
  surface_rect_functions_applied_ = false;
  pending_visibility_settings_ = true;
  cached_buffer_map_.clear();
}

Layer::Layer(const std::shared_ptr<DirectDisplaySurface>& surface,
             HWC::BlendMode blending, HWC::Transform transform,
             HWC::Composition composition_type, size_t z_order)
    : z_order_{z_order},
      blending_{blending},
      transform_{transform},
      target_composition_type_{composition_type},
      source_{SourceSurface{surface}} {
  CommonLayerSetup();
}

Layer::Layer(const std::shared_ptr<IonBuffer>& buffer, HWC::BlendMode blending,
             HWC::Transform transform, HWC::Composition composition_type,
             size_t z_order)
    : z_order_{z_order},
      blending_{blending},
      transform_{transform},
      target_composition_type_{composition_type},
      source_{SourceBuffer{buffer}} {
  CommonLayerSetup();
}

Layer::~Layer() { Reset(); }

Layer::Layer(Layer&& other) { *this = std::move(other); }

Layer& Layer::operator=(Layer&& other) {
  if (this != &other) {
    Reset();
    using std::swap;
    swap(hardware_composer_layer_, other.hardware_composer_layer_);
    swap(z_order_, other.z_order_);
    swap(blending_, other.blending_);
    swap(transform_, other.transform_);
    swap(composition_type_, other.composition_type_);
    swap(target_composition_type_, other.target_composition_type_);
    swap(source_, other.source_);
    swap(acquire_fence_, other.acquire_fence_);
    swap(surface_rect_functions_applied_,
         other.surface_rect_functions_applied_);
    swap(pending_visibility_settings_, other.pending_visibility_settings_);
    swap(cached_buffer_map_, other.cached_buffer_map_);
  }
  return *this;
}

void Layer::UpdateBuffer(const std::shared_ptr<IonBuffer>& buffer) {
  if (source_.is<SourceBuffer>())
    std::get<SourceBuffer>(source_) = {buffer};
}

void Layer::SetBlending(HWC::BlendMode blending) {
  if (blending_ != blending) {
    blending_ = blending;
    pending_visibility_settings_ = true;
  }
}

void Layer::SetZOrder(size_t z_order) {
  if (z_order_ != z_order) {
    z_order_ = z_order;
    pending_visibility_settings_ = true;
  }
}

IonBuffer* Layer::GetBuffer() {
  struct Visitor {
    IonBuffer* operator()(SourceSurface& source) { return source.GetBuffer(); }
    IonBuffer* operator()(SourceBuffer& source) { return source.GetBuffer(); }
    IonBuffer* operator()(EmptyVariant) { return nullptr; }
  };
  return source_.Visit(Visitor{});
}

void Layer::UpdateVisibilitySettings() {
  if (pending_visibility_settings_) {
    pending_visibility_settings_ = false;

    HWC::Error error;
    hwc2_display_t display = HWC_DISPLAY_PRIMARY;

    error = composer_->setLayerBlendMode(
        display, hardware_composer_layer_,
        blending_.cast<Hwc2::IComposerClient::BlendMode>());
    ALOGE_IF(error != HWC::Error::None,
             "Layer::UpdateLayerSettings: Error setting layer blend mode: %s",
             error.to_string().c_str());

    error =
        composer_->setLayerZOrder(display, hardware_composer_layer_, z_order_);
    ALOGE_IF(error != HWC::Error::None,
             "Layer::UpdateLayerSettings: Error setting z_ order: %s",
             error.to_string().c_str());
  }
}

void Layer::UpdateLayerSettings() {
  HWC::Error error;
  hwc2_display_t display = HWC_DISPLAY_PRIMARY;

  UpdateVisibilitySettings();

  // TODO(eieio): Use surface attributes or some other mechanism to control
  // the layer display frame.
  error = composer_->setLayerDisplayFrame(
      display, hardware_composer_layer_,
      {0, 0, display_metrics_.width, display_metrics_.height});
  ALOGE_IF(error != HWC::Error::None,
           "Layer::UpdateLayerSettings: Error setting layer display frame: %s",
           error.to_string().c_str());

  error = composer_->setLayerVisibleRegion(
      display, hardware_composer_layer_,
      {{0, 0, display_metrics_.width, display_metrics_.height}});
  ALOGE_IF(error != HWC::Error::None,
           "Layer::UpdateLayerSettings: Error setting layer visible region: %s",
           error.to_string().c_str());

  error =
      composer_->setLayerPlaneAlpha(display, hardware_composer_layer_, 1.0f);
  ALOGE_IF(error != HWC::Error::None,
           "Layer::UpdateLayerSettings: Error setting layer plane alpha: %s",
           error.to_string().c_str());
}

void Layer::CommonLayerSetup() {
  HWC::Error error =
      composer_->createLayer(HWC_DISPLAY_PRIMARY, &hardware_composer_layer_);
  ALOGE_IF(error != HWC::Error::None,
           "Layer::CommonLayerSetup: Failed to create layer on primary "
           "display: %s",
           error.to_string().c_str());
  UpdateLayerSettings();
}

bool Layer::CheckAndUpdateCachedBuffer(std::size_t slot, int buffer_id) {
  auto search = cached_buffer_map_.find(slot);
  if (search != cached_buffer_map_.end() && search->second == buffer_id)
    return true;

  // Assign or update the buffer slot.
  if (buffer_id >= 0)
    cached_buffer_map_[slot] = buffer_id;
  return false;
}

void Layer::Prepare() {
  int right, bottom, id;
  sp<GraphicBuffer> handle;
  std::size_t slot;

  // Acquire the next buffer according to the type of source.
  IfAnyOf<SourceSurface, SourceBuffer>::Call(&source_, [&](auto& source) {
    std::tie(right, bottom, id, handle, acquire_fence_, slot) =
        source.Acquire();
  });

  TRACE_FORMAT("Layer::Prepare|buffer_id=%d;slot=%zu|", id, slot);

  // Update any visibility (blending, z-order) changes that occurred since
  // last prepare.
  UpdateVisibilitySettings();

  // When a layer is first setup there may be some time before the first
  // buffer arrives. Setup the HWC layer as a solid color to stall for time
  // until the first buffer arrives. Once the first buffer arrives there will
  // always be a buffer for the frame even if it is old.
  if (!handle.get()) {
    if (composition_type_ == HWC::Composition::Invalid) {
      composition_type_ = HWC::Composition::SolidColor;
      composer_->setLayerCompositionType(
          HWC_DISPLAY_PRIMARY, hardware_composer_layer_,
          composition_type_.cast<Hwc2::IComposerClient::Composition>());
      Hwc2::IComposerClient::Color layer_color = {0, 0, 0, 0};
      composer_->setLayerColor(HWC_DISPLAY_PRIMARY, hardware_composer_layer_,
                               layer_color);
    } else {
      // The composition type is already set. Nothing else to do until a
      // buffer arrives.
    }
  } else {
    if (composition_type_ != target_composition_type_) {
      composition_type_ = target_composition_type_;
      composer_->setLayerCompositionType(
          HWC_DISPLAY_PRIMARY, hardware_composer_layer_,
          composition_type_.cast<Hwc2::IComposerClient::Composition>());
    }

    // See if the HWC cache already has this buffer.
    const bool cached = CheckAndUpdateCachedBuffer(slot, id);
    if (cached)
      handle = nullptr;

    HWC::Error error{HWC::Error::None};
    error =
        composer_->setLayerBuffer(HWC_DISPLAY_PRIMARY, hardware_composer_layer_,
                                  slot, handle, acquire_fence_.Get());

    ALOGE_IF(error != HWC::Error::None,
             "Layer::Prepare: Error setting layer buffer: %s",
             error.to_string().c_str());

    if (!surface_rect_functions_applied_) {
      const float float_right = right;
      const float float_bottom = bottom;
      error = composer_->setLayerSourceCrop(HWC_DISPLAY_PRIMARY,
                                            hardware_composer_layer_,
                                            {0, 0, float_right, float_bottom});

      ALOGE_IF(error != HWC::Error::None,
               "Layer::Prepare: Error setting layer source crop: %s",
               error.to_string().c_str());

      surface_rect_functions_applied_ = true;
    }
  }
}

void Layer::Finish(int release_fence_fd) {
  IfAnyOf<SourceSurface, SourceBuffer>::Call(
      &source_, [release_fence_fd](auto& source) {
        source.Finish(LocalHandle(release_fence_fd));
      });
}

void Layer::Drop() { acquire_fence_.Close(); }

}  // namespace dvr
}  // namespace android
