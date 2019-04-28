#include "producer_channel.h"

#include <log/log.h>
#include <sync/sync.h>
#include <sys/epoll.h>
#include <sys/eventfd.h>
#include <sys/poll.h>
#include <utils/Trace.h>

#include <algorithm>
#include <atomic>
#include <thread>

#include <private/dvr/bufferhub_rpc.h>
#include "consumer_channel.h"

using android::pdx::BorrowedHandle;
using android::pdx::ErrorStatus;
using android::pdx::Message;
using android::pdx::RemoteChannelHandle;
using android::pdx::Status;
using android::pdx::rpc::BufferWrapper;
using android::pdx::rpc::DispatchRemoteMethod;
using android::pdx::rpc::WrapBuffer;

namespace android {
namespace dvr {

namespace {

static inline uint64_t FindNextClearedBit(uint64_t bits) {
  return ~bits - (~bits & (~bits - 1));
}

}  // namespace

ProducerChannel::ProducerChannel(BufferHubService* service, int channel_id,
                                 uint32_t width, uint32_t height,
                                 uint32_t layer_count, uint32_t format,
                                 uint64_t usage, size_t user_metadata_size,
                                 int* error)
    : BufferHubChannel(service, channel_id, channel_id, kProducerType),
      pending_consumers_(0),
      producer_owns_(true),
      user_metadata_size_(user_metadata_size),
      metadata_buf_size_(BufferHubDefs::kMetadataHeaderSize +
                         user_metadata_size) {
  if (int ret = buffer_.Alloc(width, height, layer_count, format, usage)) {
    ALOGE("ProducerChannel::ProducerChannel: Failed to allocate buffer: %s",
          strerror(-ret));
    *error = ret;
    return;
  }

  if (int ret = metadata_buffer_.Alloc(metadata_buf_size_, /*height=*/1,
                                       /*layer_count=*/1,
                                       BufferHubDefs::kMetadataFormat,
                                       BufferHubDefs::kMetadataUsage)) {
    ALOGE("ProducerChannel::ProducerChannel: Failed to allocate metadata: %s",
          strerror(-ret));
    *error = ret;
    return;
  }

  void* metadata_ptr = nullptr;
  if (int ret = metadata_buffer_.Lock(BufferHubDefs::kMetadataUsage, /*x=*/0,
                                      /*y=*/0, metadata_buf_size_,
                                      /*height=*/1, &metadata_ptr)) {
    ALOGE("ProducerChannel::ProducerChannel: Failed to lock metadata.");
    *error = -ret;
    return;
  }
  metadata_header_ =
      reinterpret_cast<BufferHubDefs::MetadataHeader*>(metadata_ptr);

  // Using placement new here to reuse shared memory instead of new allocation
  // and also initialize the value to zero.
  buffer_state_ =
      new (&metadata_header_->buffer_state) std::atomic<uint64_t>(0);
  fence_state_ =
      new (&metadata_header_->fence_state) std::atomic<uint64_t>(0);

  acquire_fence_fd_.Reset(epoll_create1(EPOLL_CLOEXEC));
  release_fence_fd_.Reset(epoll_create1(EPOLL_CLOEXEC));
  if (!acquire_fence_fd_ || !release_fence_fd_) {
    ALOGE("ProducerChannel::ProducerChannel: Failed to create shared fences.");
    *error = -EIO;
    return;
  }

  dummy_fence_fd_.Reset(eventfd(0, EFD_CLOEXEC | EFD_NONBLOCK));
  if (!dummy_fence_fd_) {
    ALOGE("ProducerChannel::ProducerChannel: Failed to create dummy fences.");
    *error = -EIO;
    return;
  }

  epoll_event event;
  event.events = 0;
  event.data.u64 = 0ULL;
  if (epoll_ctl(release_fence_fd_.Get(), EPOLL_CTL_ADD, dummy_fence_fd_.Get(),
                &event) < 0) {
    ALOGE(
        "ProducerChannel::ProducerChannel: Failed to modify the shared "
        "release fence to include the dummy fence: %s",
        strerror(errno));
    *error = -EIO;
    return;
  }

  // Success.
  *error = 0;
}

Status<std::shared_ptr<ProducerChannel>> ProducerChannel::Create(
    BufferHubService* service, int channel_id, uint32_t width, uint32_t height,
    uint32_t layer_count, uint32_t format, uint64_t usage,
    size_t user_metadata_size) {
  int error;
  std::shared_ptr<ProducerChannel> producer(
      new ProducerChannel(service, channel_id, width, height, layer_count,
                          format, usage, user_metadata_size, &error));
  if (error < 0)
    return ErrorStatus(-error);
  else
    return {std::move(producer)};
}

ProducerChannel::~ProducerChannel() {
  ALOGD_IF(TRACE,
           "ProducerChannel::~ProducerChannel: channel_id=%d buffer_id=%d "
           "state=%" PRIx64 ".",
           channel_id(), buffer_id(), buffer_state_->load());
  for (auto consumer : consumer_channels_)
    consumer->OnProducerClosed();
}

BufferHubChannel::BufferInfo ProducerChannel::GetBufferInfo() const {
  // Derive the mask of signaled buffers in this producer / consumer set.
  uint64_t signaled_mask = signaled() ? BufferHubDefs::kProducerStateBit : 0;
  for (const ConsumerChannel* consumer : consumer_channels_) {
    signaled_mask |= consumer->signaled() ? consumer->consumer_state_bit() : 0;
  }

  return BufferInfo(buffer_id(), consumer_channels_.size(), buffer_.width(),
                    buffer_.height(), buffer_.layer_count(), buffer_.format(),
                    buffer_.usage(), pending_consumers_, buffer_state_->load(),
                    signaled_mask, metadata_header_->queue_index, name_);
}

void ProducerChannel::HandleImpulse(Message& message) {
  ATRACE_NAME("ProducerChannel::HandleImpulse");
  switch (message.GetOp()) {
    case BufferHubRPC::ProducerGain::Opcode:
      OnProducerGain(message);
      break;
    case BufferHubRPC::ProducerPost::Opcode:
      OnProducerPost(message, {});
      break;
  }
}

bool ProducerChannel::HandleMessage(Message& message) {
  ATRACE_NAME("ProducerChannel::HandleMessage");
  switch (message.GetOp()) {
    case BufferHubRPC::GetBuffer::Opcode:
      DispatchRemoteMethod<BufferHubRPC::GetBuffer>(
          *this, &ProducerChannel::OnGetBuffer, message);
      return true;

    case BufferHubRPC::NewConsumer::Opcode:
      DispatchRemoteMethod<BufferHubRPC::NewConsumer>(
          *this, &ProducerChannel::OnNewConsumer, message);
      return true;

    case BufferHubRPC::ProducerPost::Opcode:
      DispatchRemoteMethod<BufferHubRPC::ProducerPost>(
          *this, &ProducerChannel::OnProducerPost, message);
      return true;

    case BufferHubRPC::ProducerGain::Opcode:
      DispatchRemoteMethod<BufferHubRPC::ProducerGain>(
          *this, &ProducerChannel::OnProducerGain, message);
      return true;

    case BufferHubRPC::ProducerMakePersistent::Opcode:
      DispatchRemoteMethod<BufferHubRPC::ProducerMakePersistent>(
          *this, &ProducerChannel::OnProducerMakePersistent, message);
      return true;

    case BufferHubRPC::ProducerRemovePersistence::Opcode:
      DispatchRemoteMethod<BufferHubRPC::ProducerRemovePersistence>(
          *this, &ProducerChannel::OnRemovePersistence, message);
      return true;

    default:
      return false;
  }
}

BufferDescription<BorrowedHandle> ProducerChannel::GetBuffer(
    uint64_t buffer_state_bit) {
  return {
      buffer_,          metadata_buffer_,           buffer_id(),
      buffer_state_bit, acquire_fence_fd_.Borrow(), release_fence_fd_.Borrow()};
}

Status<BufferDescription<BorrowedHandle>> ProducerChannel::OnGetBuffer(
    Message& /*message*/) {
  ATRACE_NAME("ProducerChannel::OnGetBuffer");
  ALOGD_IF(TRACE, "ProducerChannel::OnGetBuffer: buffer=%d, state=%" PRIx64 ".",
           buffer_id(), buffer_state_->load());
  return {GetBuffer(BufferHubDefs::kProducerStateBit)};
}

Status<RemoteChannelHandle> ProducerChannel::CreateConsumer(Message& message) {
  ATRACE_NAME("ProducerChannel::CreateConsumer");
  ALOGD_IF(TRACE,
           "ProducerChannel::CreateConsumer: buffer_id=%d, producer_owns=%d",
           buffer_id(), producer_owns_);

  int channel_id;
  auto status = message.PushChannel(0, nullptr, &channel_id);
  if (!status) {
    ALOGE(
        "ProducerChannel::CreateConsumer: Failed to push consumer channel: %s",
        status.GetErrorMessage().c_str());
    return ErrorStatus(ENOMEM);
  }

  // Try find the next consumer state bit which has not been claimed by any
  // consumer yet.
  uint64_t consumer_state_bit = FindNextClearedBit(
      active_consumer_bit_mask_ | orphaned_consumer_bit_mask_ |
      BufferHubDefs::kProducerStateBit);
  if (consumer_state_bit == 0ULL) {
    ALOGE(
        "ProducerChannel::CreateConsumer: reached the maximum mumber of "
        "consumers per producer: 63.");
    return ErrorStatus(E2BIG);
  }

  auto consumer =
      std::make_shared<ConsumerChannel>(service(), buffer_id(), channel_id,
                                        consumer_state_bit, shared_from_this());
  const auto channel_status = service()->SetChannel(channel_id, consumer);
  if (!channel_status) {
    ALOGE(
        "ProducerChannel::CreateConsumer: failed to set new consumer channel: "
        "%s",
        channel_status.GetErrorMessage().c_str());
    return ErrorStatus(ENOMEM);
  }

  if (!producer_owns_ &&
      !BufferHubDefs::IsBufferReleased(buffer_state_->load())) {
    // Signal the new consumer when adding it to a posted producer.
    if (consumer->OnProducerPosted())
      pending_consumers_++;
  }

  active_consumer_bit_mask_ |= consumer_state_bit;
  return {status.take()};
}

Status<RemoteChannelHandle> ProducerChannel::OnNewConsumer(Message& message) {
  ATRACE_NAME("ProducerChannel::OnNewConsumer");
  ALOGD_IF(TRACE, "ProducerChannel::OnNewConsumer: buffer_id=%d", buffer_id());
  return CreateConsumer(message);
}

Status<void> ProducerChannel::OnProducerPost(
    Message&, LocalFence acquire_fence) {
  ATRACE_NAME("ProducerChannel::OnProducerPost");
  ALOGD_IF(TRACE, "ProducerChannel::OnProducerPost: buffer_id=%d", buffer_id());
  if (!producer_owns_) {
    ALOGE("ProducerChannel::OnProducerPost: Not in gained state!");
    return ErrorStatus(EBUSY);
  }

  epoll_event event;
  event.events = 0;
  event.data.u64 = 0ULL;
  int ret = epoll_ctl(release_fence_fd_.Get(), EPOLL_CTL_MOD,
                      dummy_fence_fd_.Get(), &event);
  ALOGE_IF(ret < 0,
      "ProducerChannel::OnProducerPost: Failed to modify the shared "
      "release fence to include the dummy fence: %s",
      strerror(errno));

  eventfd_t dummy_fence_count = 0ULL;
  if (eventfd_read(dummy_fence_fd_.Get(), &dummy_fence_count) < 0) {
    const int error = errno;
    if (error != EAGAIN) {
      ALOGE(
          "ProducerChannel::ProducerChannel: Failed to read dummy fence, "
          "error: %s",
          strerror(error));
      return ErrorStatus(error);
    }
  }

  ALOGW_IF(dummy_fence_count > 0,
           "ProducerChannel::ProducerChannel: %" PRIu64
           " dummy fence(s) was signaled during last release/gain cycle "
           "buffer_id=%d.",
           dummy_fence_count, buffer_id());

  post_fence_ = std::move(acquire_fence);
  producer_owns_ = false;

  // Signal any interested consumers. If there are none, the buffer will stay
  // in posted state until a consumer comes online. This behavior guarantees
  // that no frame is silently dropped.
  pending_consumers_ = 0;
  for (auto consumer : consumer_channels_) {
    if (consumer->OnProducerPosted())
      pending_consumers_++;
  }
  ALOGD_IF(TRACE, "ProducerChannel::OnProducerPost: %d pending consumers",
           pending_consumers_);

  return {};
}

Status<LocalFence> ProducerChannel::OnProducerGain(Message& /*message*/) {
  ATRACE_NAME("ProducerChannel::OnGain");
  ALOGD_IF(TRACE, "ProducerChannel::OnGain: buffer_id=%d", buffer_id());
  if (producer_owns_) {
    ALOGE("ProducerChanneL::OnGain: Already in gained state: channel=%d",
          channel_id());
    return ErrorStatus(EALREADY);
  }

  // There are still pending consumers, return busy.
  if (pending_consumers_ > 0) {
    ALOGE(
        "ProducerChannel::OnGain: Producer (id=%d) is gaining a buffer that "
        "still has %d pending consumer(s).",
        buffer_id(), pending_consumers_);
    return ErrorStatus(EBUSY);
  }

  ClearAvailable();
  producer_owns_ = true;
  post_fence_.close();
  return {std::move(returned_fence_)};
}

Status<LocalFence> ProducerChannel::OnConsumerAcquire(Message& /*message*/) {
  ATRACE_NAME("ProducerChannel::OnConsumerAcquire");
  ALOGD_IF(TRACE, "ProducerChannel::OnConsumerAcquire: buffer_id=%d",
           buffer_id());
  if (producer_owns_) {
    ALOGE("ProducerChannel::OnConsumerAcquire: Not in posted state!");
    return ErrorStatus(EBUSY);
  }

  // Return a borrowed fd to avoid unnecessary duplication of the underlying fd.
  // Serialization just needs to read the handle.
  return {std::move(post_fence_)};
}

Status<void> ProducerChannel::OnConsumerRelease(Message&,
                                                LocalFence release_fence) {
  ATRACE_NAME("ProducerChannel::OnConsumerRelease");
  ALOGD_IF(TRACE, "ProducerChannel::OnConsumerRelease: buffer_id=%d",
           buffer_id());
  if (producer_owns_) {
    ALOGE("ProducerChannel::OnConsumerRelease: Not in acquired state!");
    return ErrorStatus(EBUSY);
  }

  // Attempt to merge the fences if necessary.
  if (release_fence) {
    if (returned_fence_) {
      LocalFence merged_fence(sync_merge("bufferhub_merged",
                                         returned_fence_.get_fd(),
                                         release_fence.get_fd()));
      const int error = errno;
      if (!merged_fence) {
        ALOGE("ProducerChannel::OnConsumerRelease: Failed to merge fences: %s",
              strerror(error));
        return ErrorStatus(error);
      }
      returned_fence_ = std::move(merged_fence);
    } else {
      returned_fence_ = std::move(release_fence);
    }
  }

  OnConsumerIgnored();
  if (pending_consumers_ == 0) {
    // Clear the producer bit atomically to transit into released state. This
    // has to done by BufferHub as it requries synchronization among all
    // consumers.
    BufferHubDefs::ModifyBufferState(buffer_state_,
                                     BufferHubDefs::kProducerStateBit, 0ULL);
    ALOGD_IF(TRACE,
             "ProducerChannel::OnConsumerRelease: releasing last consumer: "
             "buffer_id=%d state=%" PRIx64 ".",
             buffer_id(), buffer_state_->load());

    if (orphaned_consumer_bit_mask_) {
      ALOGW(
          "ProducerChannel::OnConsumerRelease: orphaned buffer detected "
          "during the this acquire/release cycle: id=%d orphaned=0x%" PRIx64
          " queue_index=%" PRIu64 ".",
          buffer_id(), orphaned_consumer_bit_mask_,
          metadata_header_->queue_index);
      orphaned_consumer_bit_mask_ = 0;
    }

    SignalAvailable();
  }

  ALOGE_IF(pending_consumers_ &&
               BufferHubDefs::IsBufferReleased(buffer_state_->load()),
           "ProducerChannel::OnConsumerRelease: buffer state inconsistent: "
           "pending_consumers=%d, buffer buffer is in releaed state.",
           pending_consumers_);
  return {};
}

void ProducerChannel::OnConsumerIgnored() {
  if (pending_consumers_ == 0) {
    ALOGE("ProducerChannel::OnConsumerIgnored: no pending consumer.");
    return;
  }

  --pending_consumers_;
  ALOGD_IF(TRACE,
           "ProducerChannel::OnConsumerIgnored: buffer_id=%d %d consumers left",
           buffer_id(), pending_consumers_);
}

void ProducerChannel::OnConsumerOrphaned(ConsumerChannel* channel) {
  // Ignore the orphaned consumer.
  OnConsumerIgnored();

  const uint64_t consumer_state_bit = channel->consumer_state_bit();
  ALOGE_IF(orphaned_consumer_bit_mask_ & consumer_state_bit,
           "ProducerChannel::OnConsumerOrphaned: Consumer "
           "(consumer_state_bit=%" PRIx64 ") is already orphaned.",
           consumer_state_bit);
  orphaned_consumer_bit_mask_ |= consumer_state_bit;

  // Atomically clear the fence state bit as an orphaned consumer will never
  // signal a release fence. Also clear the buffer state as it won't be released
  // as well.
  fence_state_->fetch_and(~consumer_state_bit);
  BufferHubDefs::ModifyBufferState(buffer_state_, consumer_state_bit, 0ULL);

  ALOGW(
      "ProducerChannel::OnConsumerOrphaned: detected new orphaned consumer "
      "buffer_id=%d consumer_state_bit=%" PRIx64 " queue_index=%" PRIu64
      " buffer_state=%" PRIx64 " fence_state=%" PRIx64 ".",
      buffer_id(), consumer_state_bit, metadata_header_->queue_index,
      buffer_state_->load(), fence_state_->load());
}

Status<void> ProducerChannel::OnProducerMakePersistent(Message& message,
                                                       const std::string& name,
                                                       int user_id,
                                                       int group_id) {
  ATRACE_NAME("ProducerChannel::OnProducerMakePersistent");
  ALOGD_IF(TRACE,
           "ProducerChannel::OnProducerMakePersistent: buffer_id=%d name=%s "
           "user_id=%d group_id=%d",
           buffer_id(), name.c_str(), user_id, group_id);

  if (name.empty() || (user_id < 0 && user_id != kNoCheckId) ||
      (group_id < 0 && group_id != kNoCheckId)) {
    return ErrorStatus(EINVAL);
  }

  // Try to add this buffer with the requested name.
  if (service()->AddNamedBuffer(name, std::static_pointer_cast<ProducerChannel>(
                                          shared_from_this()))) {
    // If successful, set the requested permissions.

    // A value of zero indicates that the ids from the sending process should be
    // used.
    if (user_id == kUseCallerId)
      user_id = message.GetEffectiveUserId();
    if (group_id == kUseCallerId)
      group_id = message.GetEffectiveGroupId();

    owner_user_id_ = user_id;
    owner_group_id_ = group_id;
    name_ = name;
    return {};
  } else {
    // Otherwise a buffer with that name already exists.
    return ErrorStatus(EALREADY);
  }
}

Status<void> ProducerChannel::OnRemovePersistence(Message&) {
  if (service()->RemoveNamedBuffer(*this))
    return {};
  else
    return ErrorStatus(ENOENT);
}

void ProducerChannel::AddConsumer(ConsumerChannel* channel) {
  consumer_channels_.push_back(channel);
}

void ProducerChannel::RemoveConsumer(ConsumerChannel* channel) {
  consumer_channels_.erase(
      std::find(consumer_channels_.begin(), consumer_channels_.end(), channel));
  active_consumer_bit_mask_ &= ~channel->consumer_state_bit();

  const uint64_t buffer_state = buffer_state_->load();
  if (BufferHubDefs::IsBufferPosted(buffer_state) ||
      BufferHubDefs::IsBufferAcquired(buffer_state)) {
    // The consumer client is being destoryed without releasing. This could
    // happen in corner cases when the consumer crashes. Here we mark it
    // orphaned before remove it from producer.
    OnConsumerOrphaned(channel);
  }

  if (BufferHubDefs::IsBufferReleased(buffer_state) ||
      BufferHubDefs::IsBufferGained(buffer_state)) {
    // The consumer is being close while it is suppose to signal a release
    // fence. Signal the dummy fence here.
    if (fence_state_->load() & channel->consumer_state_bit()) {
      epoll_event event;
      event.events = EPOLLIN;
      event.data.u64 = channel->consumer_state_bit();
      if (epoll_ctl(release_fence_fd_.Get(), EPOLL_CTL_MOD,
                    dummy_fence_fd_.Get(), &event) < 0) {
        ALOGE(
            "ProducerChannel::RemoveConsumer: Failed to modify the shared "
            "release fence to include the dummy fence: %s",
            strerror(errno));
        return;
      }
      ALOGW(
          "ProducerChannel::RemoveConsumer: signal dummy release fence "
          "buffer_id=%d",
          buffer_id());
      eventfd_write(dummy_fence_fd_.Get(), 1);
    }
  }
}

// Returns true if either the user or group ids match the owning ids or both
// owning ids are not set, in which case access control does not apply.
bool ProducerChannel::CheckAccess(int euid, int egid) {
  const bool no_check =
      owner_user_id_ == kNoCheckId && owner_group_id_ == kNoCheckId;
  const bool euid_check = euid == owner_user_id_ || euid == kRootId;
  const bool egid_check = egid == owner_group_id_ || egid == kRootId;
  return no_check || euid_check || egid_check;
}

// Returns true if the given parameters match the underlying buffer parameters.
bool ProducerChannel::CheckParameters(uint32_t width, uint32_t height,
                                      uint32_t layer_count, uint32_t format,
                                      uint64_t usage,
                                      size_t user_metadata_size) {
  return user_metadata_size == user_metadata_size_ &&
         buffer_.width() == width && buffer_.height() == height &&
         buffer_.layer_count() == layer_count && buffer_.format() == format &&
         buffer_.usage() == usage;
}

}  // namespace dvr
}  // namespace android
