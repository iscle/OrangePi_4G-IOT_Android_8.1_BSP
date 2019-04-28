#include "include/private/dvr/buffer_hub_queue_client.h"

#include <inttypes.h>
#include <log/log.h>
#include <poll.h>
#include <sys/epoll.h>

#include <array>

#include <pdx/default_transport/client_channel.h>
#include <pdx/default_transport/client_channel_factory.h>
#include <pdx/file_handle.h>
#include <pdx/trace.h>

#define RETRY_EINTR(fnc_call)                 \
  ([&]() -> decltype(fnc_call) {              \
    decltype(fnc_call) result;                \
    do {                                      \
      result = (fnc_call);                    \
    } while (result == -1 && errno == EINTR); \
    return result;                            \
  })()

using android::pdx::ErrorStatus;
using android::pdx::LocalChannelHandle;
using android::pdx::LocalHandle;
using android::pdx::Status;

namespace android {
namespace dvr {

namespace {

// Polls an fd for the given events.
Status<int> PollEvents(int fd, short events) {
  const int kTimeoutMs = 0;
  pollfd pfd{fd, events, 0};
  const int count = RETRY_EINTR(poll(&pfd, 1, kTimeoutMs));
  if (count < 0) {
    return ErrorStatus(errno);
  } else if (count == 0) {
    return ErrorStatus(ETIMEDOUT);
  } else {
    return {pfd.revents};
  }
}

std::pair<int32_t, int32_t> Unstuff(uint64_t value) {
  return {static_cast<int32_t>(value >> 32),
          static_cast<int32_t>(value & ((1ull << 32) - 1))};
}

uint64_t Stuff(int32_t a, int32_t b) {
  const uint32_t ua = static_cast<uint32_t>(a);
  const uint32_t ub = static_cast<uint32_t>(b);
  return (static_cast<uint64_t>(ua) << 32) | static_cast<uint64_t>(ub);
}

}  // anonymous namespace

BufferHubQueue::BufferHubQueue(LocalChannelHandle channel_handle)
    : Client{pdx::default_transport::ClientChannel::Create(
          std::move(channel_handle))} {
  Initialize();
}

BufferHubQueue::BufferHubQueue(const std::string& endpoint_path)
    : Client{
          pdx::default_transport::ClientChannelFactory::Create(endpoint_path)} {
  Initialize();
}

void BufferHubQueue::Initialize() {
  int ret = epoll_fd_.Create();
  if (ret < 0) {
    ALOGE("BufferHubQueue::BufferHubQueue: Failed to create epoll fd: %s",
          strerror(-ret));
    return;
  }

  epoll_event event = {
      .events = EPOLLIN | EPOLLET,
      .data = {.u64 = Stuff(-1, BufferHubQueue::kEpollQueueEventIndex)}};
  ret = epoll_fd_.Control(EPOLL_CTL_ADD, event_fd(), &event);
  if (ret < 0) {
    ALOGE("BufferHubQueue::Initialize: Failed to add event fd to epoll set: %s",
          strerror(-ret));
  }
}

Status<void> BufferHubQueue::ImportQueue() {
  auto status = InvokeRemoteMethod<BufferHubRPC::GetQueueInfo>();
  if (!status) {
    ALOGE("BufferHubQueue::ImportQueue: Failed to import queue: %s",
          status.GetErrorMessage().c_str());
    return ErrorStatus(status.error());
  } else {
    SetupQueue(status.get());
    return {};
  }
}

void BufferHubQueue::SetupQueue(const QueueInfo& queue_info) {
  is_async_ = queue_info.producer_config.is_async;
  default_width_ = queue_info.producer_config.default_width;
  default_height_ = queue_info.producer_config.default_height;
  default_format_ = queue_info.producer_config.default_format;
  user_metadata_size_ = queue_info.producer_config.user_metadata_size;
  id_ = queue_info.id;
}

std::unique_ptr<ConsumerQueue> BufferHubQueue::CreateConsumerQueue() {
  if (auto status = CreateConsumerQueueHandle(/*silent*/ false))
    return std::unique_ptr<ConsumerQueue>(new ConsumerQueue(status.take()));
  else
    return nullptr;
}

std::unique_ptr<ConsumerQueue> BufferHubQueue::CreateSilentConsumerQueue() {
  if (auto status = CreateConsumerQueueHandle(/*silent*/ true))
    return std::unique_ptr<ConsumerQueue>(new ConsumerQueue(status.take()));
  else
    return nullptr;
}

Status<LocalChannelHandle> BufferHubQueue::CreateConsumerQueueHandle(
    bool silent) {
  auto status = InvokeRemoteMethod<BufferHubRPC::CreateConsumerQueue>(silent);
  if (!status) {
    ALOGE(
        "BufferHubQueue::CreateConsumerQueue: Failed to create consumer queue: "
        "%s",
        status.GetErrorMessage().c_str());
    return ErrorStatus(status.error());
  }

  return status;
}

bool BufferHubQueue::WaitForBuffers(int timeout) {
  ATRACE_NAME("BufferHubQueue::WaitForBuffers");
  std::array<epoll_event, kMaxEvents> events;

  // Loop at least once to check for hangups.
  do {
    ALOGD_IF(
        TRACE,
        "BufferHubQueue::WaitForBuffers: queue_id=%d count=%zu capacity=%zu",
        id(), count(), capacity());

    // If there is already a buffer then just check for hangup without waiting.
    const int ret = epoll_fd_.Wait(events.data(), events.size(),
                                   count() == 0 ? timeout : 0);

    if (ret == 0) {
      ALOGI_IF(TRACE,
               "BufferHubQueue::WaitForBuffers: No events before timeout: "
               "queue_id=%d",
               id());
      return count() != 0;
    }

    if (ret < 0 && ret != -EINTR) {
      ALOGE("BufferHubQueue::WaitForBuffers: Failed to wait for buffers: %s",
            strerror(-ret));
      return false;
    }

    const int num_events = ret;

    // A BufferQueue's epoll fd tracks N+1 events, where there are N events,
    // one for each buffer in the queue, and one extra event for the queue
    // client itself.
    for (int i = 0; i < num_events; i++) {
      int32_t event_fd;
      int32_t index;
      std::tie(event_fd, index) = Unstuff(events[i].data.u64);

      PDX_TRACE_FORMAT(
          "epoll_event|queue_id=%d;num_events=%d;event_index=%d;event_fd=%d;"
          "slot=%d|",
          id(), num_events, i, event_fd, index);

      ALOGD_IF(TRACE,
               "BufferHubQueue::WaitForBuffers: event %d: event_fd=%d index=%d",
               i, event_fd, index);

      if (is_buffer_event_index(index)) {
        HandleBufferEvent(static_cast<size_t>(index), event_fd,
                          events[i].events);
      } else if (is_queue_event_index(index)) {
        HandleQueueEvent(events[i].events);
      } else {
        ALOGW(
            "BufferHubQueue::WaitForBuffers: Unknown event type event_fd=%d "
            "index=%d",
            event_fd, index);
      }
    }
  } while (count() == 0 && capacity() > 0 && !hung_up());

  return count() != 0;
}

Status<void> BufferHubQueue::HandleBufferEvent(size_t slot, int event_fd,
                                               int poll_events) {
  ATRACE_NAME("BufferHubQueue::HandleBufferEvent");
  if (!buffers_[slot]) {
    ALOGW("BufferHubQueue::HandleBufferEvent: Invalid buffer slot: %zu", slot);
    return ErrorStatus(ENOENT);
  }

  auto status = buffers_[slot]->GetEventMask(poll_events);
  if (!status) {
    ALOGW("BufferHubQueue::HandleBufferEvent: Failed to get event mask: %s",
          status.GetErrorMessage().c_str());
    return status.error_status();
  }

  const int events = status.get();
  PDX_TRACE_FORMAT(
      "buffer|queue_id=%d;buffer_id=%d;slot=%zu;event_fd=%d;poll_events=%x;"
      "events=%d|",
      id(), buffers_[slot]->id(), slot, event_fd, poll_events, events);

  if (events & EPOLLIN) {
    return Enqueue({buffers_[slot], slot, buffers_[slot]->GetQueueIndex()});
  } else if (events & EPOLLHUP) {
    ALOGW(
        "BufferHubQueue::HandleBufferEvent: Received EPOLLHUP event: slot=%zu "
        "event_fd=%d buffer_id=%d",
        slot, buffers_[slot]->event_fd(), buffers_[slot]->id());
    return RemoveBuffer(slot);
  } else {
    ALOGW(
        "BufferHubQueue::HandleBufferEvent: Unknown event, slot=%zu, epoll "
        "events=%d",
        slot, events);
  }

  return {};
}

Status<void> BufferHubQueue::HandleQueueEvent(int poll_event) {
  ATRACE_NAME("BufferHubQueue::HandleQueueEvent");
  auto status = GetEventMask(poll_event);
  if (!status) {
    ALOGW("BufferHubQueue::HandleQueueEvent: Failed to get event mask: %s",
          status.GetErrorMessage().c_str());
    return status.error_status();
  }

  const int events = status.get();
  if (events & EPOLLIN) {
    // Note that after buffer imports, if |count()| still returns 0, epoll
    // wait will be tried again to acquire the newly imported buffer.
    auto buffer_status = OnBufferAllocated();
    if (!buffer_status) {
      ALOGE("BufferHubQueue::HandleQueueEvent: Failed to import buffer: %s",
            buffer_status.GetErrorMessage().c_str());
    }
  } else if (events & EPOLLHUP) {
    ALOGD_IF(TRACE, "BufferHubQueue::HandleQueueEvent: hang up event!");
    hung_up_ = true;
  } else {
    ALOGW("BufferHubQueue::HandleQueueEvent: Unknown epoll events=%x", events);
  }

  return {};
}

Status<void> BufferHubQueue::AddBuffer(
    const std::shared_ptr<BufferHubBuffer>& buffer, size_t slot) {
  ALOGD_IF(TRACE, "BufferHubQueue::AddBuffer: buffer_id=%d slot=%zu",
           buffer->id(), slot);

  if (is_full()) {
    ALOGE("BufferHubQueue::AddBuffer queue is at maximum capacity: %zu",
          capacity_);
    return ErrorStatus(E2BIG);
  }

  if (buffers_[slot]) {
    // Replace the buffer if the slot is occupied. This could happen when the
    // producer side replaced the slot with a newly allocated buffer. Remove the
    // buffer before setting up with the new one.
    auto remove_status = RemoveBuffer(slot);
    if (!remove_status)
      return remove_status.error_status();
  }

  for (const auto& event_source : buffer->GetEventSources()) {
    epoll_event event = {.events = event_source.event_mask | EPOLLET,
                         .data = {.u64 = Stuff(buffer->event_fd(), slot)}};
    const int ret =
        epoll_fd_.Control(EPOLL_CTL_ADD, event_source.event_fd, &event);
    if (ret < 0) {
      ALOGE("BufferHubQueue::AddBuffer: Failed to add buffer to epoll set: %s",
            strerror(-ret));
      return ErrorStatus(-ret);
    }
  }

  buffers_[slot] = buffer;
  capacity_++;
  return {};
}

Status<void> BufferHubQueue::RemoveBuffer(size_t slot) {
  ALOGD_IF(TRACE, "BufferHubQueue::RemoveBuffer: slot=%zu", slot);

  if (buffers_[slot]) {
    for (const auto& event_source : buffers_[slot]->GetEventSources()) {
      const int ret =
          epoll_fd_.Control(EPOLL_CTL_DEL, event_source.event_fd, nullptr);
      if (ret < 0) {
        ALOGE(
            "BufferHubQueue::RemoveBuffer: Failed to remove buffer from epoll "
            "set: %s",
            strerror(-ret));
        return ErrorStatus(-ret);
      }
    }

    // Trigger OnBufferRemoved callback if registered.
    if (on_buffer_removed_)
      on_buffer_removed_(buffers_[slot]);

    buffers_[slot] = nullptr;
    capacity_--;
  }

  return {};
}

Status<void> BufferHubQueue::Enqueue(Entry entry) {
  if (!is_full()) {
    available_buffers_.push(std::move(entry));

    // Trigger OnBufferAvailable callback if registered.
    if (on_buffer_available_)
      on_buffer_available_();

    return {};
  } else {
    ALOGE("BufferHubQueue::Enqueue: Buffer queue is full!");
    return ErrorStatus(E2BIG);
  }
}

Status<std::shared_ptr<BufferHubBuffer>> BufferHubQueue::Dequeue(int timeout,
                                                                 size_t* slot) {
  ALOGD_IF(TRACE, "BufferHubQueue::Dequeue: count=%zu, timeout=%d", count(),
           timeout);

  PDX_TRACE_FORMAT("BufferHubQueue::Dequeue|count=%zu|", count());

  if (count() == 0) {
    if (!WaitForBuffers(timeout))
      return ErrorStatus(ETIMEDOUT);
  }

  auto& entry = available_buffers_.top();
  PDX_TRACE_FORMAT("buffer|buffer_id=%d;slot=%zu|", entry.buffer->id(),
                   entry.slot);

  std::shared_ptr<BufferHubBuffer> buffer = std::move(entry.buffer);
  *slot = entry.slot;

  available_buffers_.pop();

  return {std::move(buffer)};
}

void BufferHubQueue::SetBufferAvailableCallback(
    BufferAvailableCallback callback) {
  on_buffer_available_ = callback;
}

void BufferHubQueue::SetBufferRemovedCallback(BufferRemovedCallback callback) {
  on_buffer_removed_ = callback;
}

pdx::Status<void> BufferHubQueue::FreeAllBuffers() {
  // Clear all available buffers.
  while (!available_buffers_.empty())
    available_buffers_.pop();

  pdx::Status<void> last_error;  // No error.
  // Clear all buffers this producer queue is tracking.
  for (size_t slot = 0; slot < BufferHubQueue::kMaxQueueCapacity; slot++) {
    if (buffers_[slot] != nullptr) {
      auto status = RemoveBuffer(slot);
      if (!status) {
        ALOGE(
            "ProducerQueue::FreeAllBuffers: Failed to remove buffer at "
            "slot=%zu.",
            slot);
        last_error = status.error_status();
      }
    }
  }

  return last_error;
}

ProducerQueue::ProducerQueue(LocalChannelHandle handle)
    : BASE(std::move(handle)) {
  auto status = ImportQueue();
  if (!status) {
    ALOGE("ProducerQueue::ProducerQueue: Failed to import queue: %s",
          status.GetErrorMessage().c_str());
    Close(-status.error());
  }
}

ProducerQueue::ProducerQueue(const ProducerQueueConfig& config,
                             const UsagePolicy& usage)
    : BASE(BufferHubRPC::kClientPath) {
  auto status =
      InvokeRemoteMethod<BufferHubRPC::CreateProducerQueue>(config, usage);
  if (!status) {
    ALOGE("ProducerQueue::ProducerQueue: Failed to create producer queue: %s",
          status.GetErrorMessage().c_str());
    Close(-status.error());
    return;
  }

  SetupQueue(status.get());
}

Status<std::vector<size_t>> ProducerQueue::AllocateBuffers(
    uint32_t width, uint32_t height, uint32_t layer_count, uint32_t format,
    uint64_t usage, size_t buffer_count) {
  if (capacity() + buffer_count > kMaxQueueCapacity) {
    ALOGE(
        "ProducerQueue::AllocateBuffers: queue is at capacity: %zu, cannot "
        "allocate %zu more buffer(s).",
        capacity(), buffer_count);
    return ErrorStatus(E2BIG);
  }

  Status<std::vector<std::pair<LocalChannelHandle, size_t>>> status =
      InvokeRemoteMethod<BufferHubRPC::ProducerQueueAllocateBuffers>(
          width, height, layer_count, format, usage, buffer_count);
  if (!status) {
    ALOGE("ProducerQueue::AllocateBuffers: failed to allocate buffers: %s",
          status.GetErrorMessage().c_str());
    return status.error_status();
  }

  auto buffer_handle_slots = status.take();
  LOG_ALWAYS_FATAL_IF(buffer_handle_slots.size() != buffer_count,
                      "BufferHubRPC::ProducerQueueAllocateBuffers should "
                      "return %zu buffer handle(s), but returned %zu instead.",
                      buffer_count, buffer_handle_slots.size());

  std::vector<size_t> buffer_slots;
  buffer_slots.reserve(buffer_count);

  // Bookkeeping for each buffer.
  for (auto& hs : buffer_handle_slots) {
    auto& buffer_handle = hs.first;
    size_t buffer_slot = hs.second;

    // Note that import might (though very unlikely) fail. If so, buffer_handle
    // will be closed and included in returned buffer_slots.
    if (AddBuffer(BufferProducer::Import(std::move(buffer_handle)),
                  buffer_slot)) {
      ALOGD_IF(TRACE, "ProducerQueue::AllocateBuffers: new buffer at slot: %zu",
               buffer_slot);
      buffer_slots.push_back(buffer_slot);
    }
  }

  if (buffer_slots.size() == 0) {
    // Error out if no buffer is allocated and improted.
    ALOGE_IF(TRACE, "ProducerQueue::AllocateBuffers: no buffer allocated.");
    ErrorStatus(ENOMEM);
  }

  return {std::move(buffer_slots)};
}

Status<size_t> ProducerQueue::AllocateBuffer(uint32_t width, uint32_t height,
                                             uint32_t layer_count,
                                             uint32_t format, uint64_t usage) {
  // We only allocate one buffer at a time.
  constexpr size_t buffer_count = 1;
  auto status =
      AllocateBuffers(width, height, layer_count, format, usage, buffer_count);
  if (!status) {
    ALOGE("ProducerQueue::AllocateBuffer: Failed to allocate buffer: %s",
          status.GetErrorMessage().c_str());
    return status.error_status();
  }

  if (status.get().size() == 0) {
    ALOGE_IF(TRACE, "ProducerQueue::AllocateBuffer: no buffer allocated.");
    ErrorStatus(ENOMEM);
  }

  return {status.get()[0]};
}

Status<void> ProducerQueue::AddBuffer(
    const std::shared_ptr<BufferProducer>& buffer, size_t slot) {
  ALOGD_IF(TRACE, "ProducerQueue::AddBuffer: queue_id=%d buffer_id=%d slot=%zu",
           id(), buffer->id(), slot);
  // For producer buffer, we need to enqueue the newly added buffer
  // immediately. Producer queue starts with all buffers in available state.
  auto status = BufferHubQueue::AddBuffer(buffer, slot);
  if (!status)
    return status;

  return BufferHubQueue::Enqueue({buffer, slot, 0ULL});
}

Status<void> ProducerQueue::RemoveBuffer(size_t slot) {
  auto status =
      InvokeRemoteMethod<BufferHubRPC::ProducerQueueRemoveBuffer>(slot);
  if (!status) {
    ALOGE("ProducerQueue::RemoveBuffer: Failed to remove producer buffer: %s",
          status.GetErrorMessage().c_str());
    return status.error_status();
  }

  return BufferHubQueue::RemoveBuffer(slot);
}

Status<std::shared_ptr<BufferProducer>> ProducerQueue::Dequeue(
    int timeout, size_t* slot, LocalHandle* release_fence) {
  DvrNativeBufferMetadata canonical_meta;
  return Dequeue(timeout, slot, &canonical_meta, release_fence);
}

pdx::Status<std::shared_ptr<BufferProducer>> ProducerQueue::Dequeue(
    int timeout, size_t* slot, DvrNativeBufferMetadata* out_meta,
    pdx::LocalHandle* release_fence) {
  ATRACE_NAME("ProducerQueue::Dequeue");
  if (slot == nullptr || out_meta == nullptr || release_fence == nullptr) {
    ALOGE("ProducerQueue::Dequeue: Invalid parameter.");
    return ErrorStatus(EINVAL);
  }

  auto status = BufferHubQueue::Dequeue(timeout, slot);
  if (!status)
    return status.error_status();

  auto buffer = std::static_pointer_cast<BufferProducer>(status.take());
  const int ret = buffer->GainAsync(out_meta, release_fence);
  if (ret < 0 && ret != -EALREADY)
    return ErrorStatus(-ret);

  return {std::move(buffer)};
}

ConsumerQueue::ConsumerQueue(LocalChannelHandle handle)
    : BufferHubQueue(std::move(handle)) {
  auto status = ImportQueue();
  if (!status) {
    ALOGE("ConsumerQueue::ConsumerQueue: Failed to import queue: %s",
          status.GetErrorMessage().c_str());
    Close(-status.error());
  }

  auto import_status = ImportBuffers();
  if (import_status) {
    ALOGI("ConsumerQueue::ConsumerQueue: Imported %zu buffers.",
          import_status.get());
  } else {
    ALOGE("ConsumerQueue::ConsumerQueue: Failed to import buffers: %s",
          import_status.GetErrorMessage().c_str());
  }
}

Status<size_t> ConsumerQueue::ImportBuffers() {
  auto status = InvokeRemoteMethod<BufferHubRPC::ConsumerQueueImportBuffers>();
  if (!status) {
    if (status.error() == EBADR) {
      ALOGI(
          "ConsumerQueue::ImportBuffers: Queue is silent, no buffers "
          "imported.");
      return {0};
    } else {
      ALOGE(
          "ConsumerQueue::ImportBuffers: Failed to import consumer buffer: %s",
          status.GetErrorMessage().c_str());
      return status.error_status();
    }
  }

  int ret;
  Status<void> last_error;
  size_t imported_buffers_count = 0;

  auto buffer_handle_slots = status.take();
  for (auto& buffer_handle_slot : buffer_handle_slots) {
    ALOGD_IF(TRACE, "ConsumerQueue::ImportBuffers: buffer_handle=%d",
             buffer_handle_slot.first.value());

    std::unique_ptr<BufferConsumer> buffer_consumer =
        BufferConsumer::Import(std::move(buffer_handle_slot.first));
    if (!buffer_consumer) {
      ALOGE("ConsumerQueue::ImportBuffers: Failed to import buffer: slot=%zu",
            buffer_handle_slot.second);
      last_error = ErrorStatus(EPIPE);
      continue;
    }

    auto add_status =
        AddBuffer(std::move(buffer_consumer), buffer_handle_slot.second);
    if (!add_status) {
      ALOGE("ConsumerQueue::ImportBuffers: Failed to add buffer: %s",
            add_status.GetErrorMessage().c_str());
      last_error = add_status;
    } else {
      imported_buffers_count++;
    }
  }

  if (imported_buffers_count > 0)
    return {imported_buffers_count};
  else
    return last_error.error_status();
}

Status<void> ConsumerQueue::AddBuffer(
    const std::shared_ptr<BufferConsumer>& buffer, size_t slot) {
  ALOGD_IF(TRACE, "ConsumerQueue::AddBuffer: queue_id=%d buffer_id=%d slot=%zu",
           id(), buffer->id(), slot);
  auto status = BufferHubQueue::AddBuffer(buffer, slot);
  if (!status)
    return status;

  // Check to see if the buffer is already signaled. This is necessary to catch
  // cases where buffers are already available; epoll edge triggered mode does
  // not fire until an edge transition when adding new buffers to the epoll
  // set. Note that we only poll the fd events because HandleBufferEvent() takes
  // care of checking the translated buffer events.
  auto poll_status = PollEvents(buffer->event_fd(), POLLIN);
  if (!poll_status && poll_status.error() != ETIMEDOUT) {
    ALOGE("ConsumerQueue::AddBuffer: Failed to poll consumer buffer: %s",
          poll_status.GetErrorMessage().c_str());
    return poll_status.error_status();
  }

  // Update accounting if the buffer is available.
  if (poll_status)
    return HandleBufferEvent(slot, buffer->event_fd(), poll_status.get());
  else
    return {};
}

Status<std::shared_ptr<BufferConsumer>> ConsumerQueue::Dequeue(
    int timeout, size_t* slot, void* meta, size_t user_metadata_size,
    LocalHandle* acquire_fence) {
  if (user_metadata_size != user_metadata_size_) {
    ALOGE(
        "ConsumerQueue::Dequeue: Metadata size (%zu) for the dequeuing buffer "
        "does not match metadata size (%zu) for the queue.",
        user_metadata_size, user_metadata_size_);
    return ErrorStatus(EINVAL);
  }

  DvrNativeBufferMetadata canonical_meta;
  auto status = Dequeue(timeout, slot, &canonical_meta, acquire_fence);
  if (!status)
    return status.error_status();

  if (meta && user_metadata_size) {
    void* metadata_src =
        reinterpret_cast<void*>(canonical_meta.user_metadata_ptr);
    if (metadata_src) {
      memcpy(meta, metadata_src, user_metadata_size);
    } else {
      ALOGW("ConsumerQueue::Dequeue: no user-defined metadata.");
    }
  }

  return status;
}

Status<std::shared_ptr<BufferConsumer>> ConsumerQueue::Dequeue(
    int timeout, size_t* slot, DvrNativeBufferMetadata* out_meta,
    pdx::LocalHandle* acquire_fence) {
  ATRACE_NAME("ConsumerQueue::Dequeue");
  if (slot == nullptr || out_meta == nullptr || acquire_fence == nullptr) {
    ALOGE("ConsumerQueue::Dequeue: Invalid parameter.");
    return ErrorStatus(EINVAL);
  }

  auto status = BufferHubQueue::Dequeue(timeout, slot);
  if (!status)
    return status.error_status();

  auto buffer = std::static_pointer_cast<BufferConsumer>(status.take());
  const int ret = buffer->AcquireAsync(out_meta, acquire_fence);
  if (ret < 0)
    return ErrorStatus(-ret);

  return {std::move(buffer)};
}

Status<void> ConsumerQueue::OnBufferAllocated() {
  ALOGD_IF(TRACE, "ConsumerQueue::OnBufferAllocated: queue_id=%d", id());

  auto status = ImportBuffers();
  if (!status) {
    ALOGE("ConsumerQueue::OnBufferAllocated: Failed to import buffers: %s",
          status.GetErrorMessage().c_str());
    return ErrorStatus(status.error());
  } else if (status.get() == 0) {
    ALOGW("ConsumerQueue::OnBufferAllocated: No new buffers allocated!");
    return ErrorStatus(ENOBUFS);
  } else {
    ALOGD_IF(TRACE,
             "ConsumerQueue::OnBufferAllocated: Imported %zu consumer buffers.",
             status.get());
    return {};
  }
}

}  // namespace dvr
}  // namespace android
