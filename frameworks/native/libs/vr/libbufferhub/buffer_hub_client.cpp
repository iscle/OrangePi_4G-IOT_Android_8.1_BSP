#include <private/dvr/buffer_hub_client.h>

#include <log/log.h>
#include <poll.h>
#include <sys/epoll.h>
#include <utils/Trace.h>

#include <mutex>

#include <pdx/default_transport/client_channel.h>
#include <pdx/default_transport/client_channel_factory.h>

#include "include/private/dvr/bufferhub_rpc.h"

using android::pdx::LocalChannelHandle;
using android::pdx::LocalHandle;
using android::pdx::Status;

namespace android {
namespace dvr {

BufferHubBuffer::BufferHubBuffer(LocalChannelHandle channel_handle)
    : Client{pdx::default_transport::ClientChannel::Create(
          std::move(channel_handle))},
      id_(-1) {}
BufferHubBuffer::BufferHubBuffer(const std::string& endpoint_path)
    : Client{pdx::default_transport::ClientChannelFactory::Create(
          endpoint_path)},
      id_(-1) {}

BufferHubBuffer::~BufferHubBuffer() {
  if (metadata_header_ != nullptr) {
    metadata_buffer_.Unlock();
  }
}

Status<LocalChannelHandle> BufferHubBuffer::CreateConsumer() {
  Status<LocalChannelHandle> status =
      InvokeRemoteMethod<BufferHubRPC::NewConsumer>();
  ALOGE_IF(!status,
           "BufferHub::CreateConsumer: Failed to create consumer channel: %s",
           status.GetErrorMessage().c_str());
  return status;
}

int BufferHubBuffer::ImportBuffer() {
  ATRACE_NAME("BufferHubBuffer::ImportBuffer");

  Status<BufferDescription<LocalHandle>> status =
      InvokeRemoteMethod<BufferHubRPC::GetBuffer>();
  if (!status) {
    ALOGE("BufferHubBuffer::ImportBuffer: Failed to get buffer: %s",
          status.GetErrorMessage().c_str());
    return -status.error();
  } else if (status.get().id() < 0) {
    ALOGE("BufferHubBuffer::ImportBuffer: Received an invalid id!");
    return -EIO;
  }

  auto buffer_desc = status.take();

  // Stash the buffer id to replace the value in id_.
  const int new_id = buffer_desc.id();

  // Import the buffer.
  IonBuffer ion_buffer;
  ALOGD_IF(TRACE, "BufferHubBuffer::ImportBuffer: id=%d.", buffer_desc.id());

  if (const int ret = buffer_desc.ImportBuffer(&ion_buffer))
    return ret;

  // Import the metadata.
  IonBuffer metadata_buffer;
  if (const int ret = buffer_desc.ImportMetadata(&metadata_buffer)) {
    ALOGE("Failed to import metadata buffer, error=%d", ret);
    return ret;
  }
  size_t metadata_buf_size = metadata_buffer.width();
  if (metadata_buf_size < BufferHubDefs::kMetadataHeaderSize) {
    ALOGE("BufferHubBuffer::ImportBuffer: metadata buffer too small: %zu",
          metadata_buf_size);
    return -ENOMEM;
  }

  // If all imports succee, replace the previous buffer and id.
  buffer_ = std::move(ion_buffer);
  metadata_buffer_ = std::move(metadata_buffer);
  metadata_buf_size_ = metadata_buf_size;
  user_metadata_size_ = metadata_buf_size_ - BufferHubDefs::kMetadataHeaderSize;

  void* metadata_ptr = nullptr;
  if (const int ret =
          metadata_buffer_.Lock(BufferHubDefs::kMetadataUsage, /*x=*/0,
                                /*y=*/0, metadata_buf_size_,
                                /*height=*/1, &metadata_ptr)) {
    ALOGE("BufferHubBuffer::ImportBuffer: Failed to lock metadata.");
    return ret;
  }

  // Set up shared fences.
  shared_acquire_fence_ = buffer_desc.take_acquire_fence();
  shared_release_fence_ = buffer_desc.take_release_fence();
  if (!shared_acquire_fence_ || !shared_release_fence_) {
    ALOGE("BufferHubBuffer::ImportBuffer: Failed to import shared fences.");
    return -EIO;
  }

  metadata_header_ =
      reinterpret_cast<BufferHubDefs::MetadataHeader*>(metadata_ptr);
  if (user_metadata_size_) {
    user_metadata_ptr_ =
        reinterpret_cast<void*>(reinterpret_cast<uintptr_t>(metadata_ptr) +
                                BufferHubDefs::kMetadataHeaderSize);
  } else {
    user_metadata_ptr_ = nullptr;
  }

  id_ = new_id;
  buffer_state_bit_ = buffer_desc.buffer_state_bit();

  // Note that here the buffer state is mapped from shared memory as an atomic
  // object. The std::atomic's constructor will not be called so that the
  // original value stored in the memory region will be preserved.
  buffer_state_ = &metadata_header_->buffer_state;
  ALOGD_IF(TRACE,
           "BufferHubBuffer::ImportBuffer: id=%d, buffer_state=%" PRIx64 ".",
           id(), buffer_state_->load());
  fence_state_ = &metadata_header_->fence_state;
  ALOGD_IF(TRACE,
           "BufferHubBuffer::ImportBuffer: id=%d, fence_state=%" PRIx64 ".",
           id(), fence_state_->load());

  return 0;
}

inline int BufferHubBuffer::CheckMetadata(size_t user_metadata_size) const {
  if (user_metadata_size && !user_metadata_ptr_) {
    ALOGE("BufferHubBuffer::CheckMetadata: doesn't support custom metadata.");
    return -EINVAL;
  }
  if (user_metadata_size > user_metadata_size_) {
    ALOGE("BufferHubBuffer::CheckMetadata: too big: %zu, maximum: %zu.",
          user_metadata_size, user_metadata_size_);
    return -E2BIG;
  }
  return 0;
}

int BufferHubBuffer::UpdateSharedFence(const LocalHandle& new_fence,
                                       const LocalHandle& shared_fence) {
  if (pending_fence_fd_.Get() != new_fence.Get()) {
    // First, replace the old fd if there was already one. Skipping if the new
    // one is the same as the old.
    if (pending_fence_fd_.IsValid()) {
      const int ret = epoll_ctl(shared_fence.Get(), EPOLL_CTL_DEL,
                                pending_fence_fd_.Get(), nullptr);
      ALOGW_IF(ret,
               "BufferHubBuffer::UpdateSharedFence: failed to remove old fence "
               "fd from epoll set, error: %s.",
               strerror(errno));
    }

    if (new_fence.IsValid()) {
      // If ready fence is valid, we put that into the epoll set.
      epoll_event event;
      event.events = EPOLLIN;
      event.data.u64 = buffer_state_bit();
      pending_fence_fd_ = new_fence.Duplicate();
      if (epoll_ctl(shared_fence.Get(), EPOLL_CTL_ADD, pending_fence_fd_.Get(),
                    &event) < 0) {
        const int error = errno;
        ALOGE(
            "BufferHubBuffer::UpdateSharedFence: failed to add new fence fd "
            "into epoll set, error: %s.",
            strerror(error));
        return -error;
      }
      // Set bit in fence state to indicate that there is a fence from this
      // producer or consumer.
      fence_state_->fetch_or(buffer_state_bit());
    } else {
      // Unset bit in fence state to indicate that there is no fence, so that
      // when consumer to acquire or producer to acquire, it knows no need to
      // check fence for this buffer.
      fence_state_->fetch_and(~buffer_state_bit());
    }
  }

  return 0;
}

int BufferHubBuffer::Poll(int timeout_ms) {
  ATRACE_NAME("BufferHubBuffer::Poll");
  pollfd p = {event_fd(), POLLIN, 0};
  return poll(&p, 1, timeout_ms);
}

int BufferHubBuffer::Lock(int usage, int x, int y, int width, int height,
                          void** address) {
  return buffer_.Lock(usage, x, y, width, height, address);
}

int BufferHubBuffer::Unlock() { return buffer_.Unlock(); }

int BufferHubBuffer::GetBlobReadWritePointer(size_t size, void** addr) {
  int width = static_cast<int>(size);
  int height = 1;
  int ret = Lock(usage(), 0, 0, width, height, addr);
  if (ret == 0)
    Unlock();
  return ret;
}

int BufferHubBuffer::GetBlobReadOnlyPointer(size_t size, void** addr) {
  return GetBlobReadWritePointer(size, addr);
}

void BufferHubBuffer::GetBlobFds(int* fds, size_t* fds_count,
                                 size_t max_fds_count) const {
  size_t numFds = static_cast<size_t>(native_handle()->numFds);
  *fds_count = std::min(max_fds_count, numFds);
  std::copy(native_handle()->data, native_handle()->data + *fds_count, fds);
}

BufferConsumer::BufferConsumer(LocalChannelHandle channel)
    : BASE(std::move(channel)) {
  const int ret = ImportBuffer();
  if (ret < 0) {
    ALOGE("BufferConsumer::BufferConsumer: Failed to import buffer: %s",
          strerror(-ret));
    Close(ret);
  }
}

std::unique_ptr<BufferConsumer> BufferConsumer::Import(
    LocalChannelHandle channel) {
  ATRACE_NAME("BufferConsumer::Import");
  ALOGD_IF(TRACE, "BufferConsumer::Import: channel=%d", channel.value());
  return BufferConsumer::Create(std::move(channel));
}

std::unique_ptr<BufferConsumer> BufferConsumer::Import(
    Status<LocalChannelHandle> status) {
  return Import(status ? status.take()
                       : LocalChannelHandle{nullptr, -status.error()});
}

int BufferConsumer::LocalAcquire(DvrNativeBufferMetadata* out_meta,
                                 LocalHandle* out_fence) {
  if (!out_meta)
    return -EINVAL;

  // Only check producer bit and this consumer buffer's particular consumer bit.
  // The buffer is can be acquired iff: 1) producer bit is set; 2) consumer bit
  // is not set.
  uint64_t buffer_state = buffer_state_->load();
  if (!BufferHubDefs::IsBufferPosted(buffer_state, buffer_state_bit())) {
    ALOGE("BufferConsumer::LocalAcquire: not posted, id=%d state=%" PRIx64
          " buffer_state_bit=%" PRIx64 ".",
          id(), buffer_state, buffer_state_bit());
    return -EBUSY;
  }

  // Copy the canonical metadata.
  void* metadata_ptr = reinterpret_cast<void*>(&metadata_header_->metadata);
  memcpy(out_meta, metadata_ptr, sizeof(DvrNativeBufferMetadata));
  // Fill in the user_metadata_ptr in address space of the local process.
  if (out_meta->user_metadata_size) {
    out_meta->user_metadata_ptr =
        reinterpret_cast<uint64_t>(user_metadata_ptr_);
  } else {
    out_meta->user_metadata_ptr = 0;
  }

  uint64_t fence_state = fence_state_->load();
  // If there is an acquire fence from producer, we need to return it.
  if (fence_state & BufferHubDefs::kProducerStateBit) {
    *out_fence = shared_acquire_fence_.Duplicate();
  }

  // Set the consumer bit unique to this consumer.
  BufferHubDefs::ModifyBufferState(buffer_state_, 0ULL, buffer_state_bit());
  return 0;
}

int BufferConsumer::Acquire(LocalHandle* ready_fence) {
  return Acquire(ready_fence, nullptr, 0);
}

int BufferConsumer::Acquire(LocalHandle* ready_fence, void* meta,
                            size_t user_metadata_size) {
  ATRACE_NAME("BufferConsumer::Acquire");

  if (const int error = CheckMetadata(user_metadata_size))
    return error;

  DvrNativeBufferMetadata canonical_meta;
  if (const int error = LocalAcquire(&canonical_meta, ready_fence))
    return error;

  if (meta && user_metadata_size) {
    void* metadata_src =
        reinterpret_cast<void*>(canonical_meta.user_metadata_ptr);
    if (metadata_src) {
      memcpy(meta, metadata_src, user_metadata_size);
    } else {
      ALOGW("BufferConsumer::Acquire: no user-defined metadata.");
    }
  }

  auto status = InvokeRemoteMethod<BufferHubRPC::ConsumerAcquire>();
  if (!status)
    return -status.error();
  return 0;
}

int BufferConsumer::AcquireAsync(DvrNativeBufferMetadata* out_meta,
                                 LocalHandle* out_fence) {
  ATRACE_NAME("BufferConsumer::AcquireAsync");

  if (const int error = LocalAcquire(out_meta, out_fence))
    return error;

  auto status = SendImpulse(BufferHubRPC::ConsumerAcquire::Opcode);
  if (!status)
    return -status.error();
  return 0;
}

int BufferConsumer::LocalRelease(const DvrNativeBufferMetadata* meta,
                                 const LocalHandle& release_fence) {
  if (const int error = CheckMetadata(meta->user_metadata_size))
    return error;

  // Check invalid state transition.
  uint64_t buffer_state = buffer_state_->load();
  if (!BufferHubDefs::IsBufferAcquired(buffer_state)) {
    ALOGE("BufferConsumer::LocalRelease: not acquired id=%d state=%" PRIx64 ".",
          id(), buffer_state);
    return -EBUSY;
  }

  // On release, only the user requested metadata is copied back into the shared
  // memory for metadata. Since there are multiple consumers, it doesn't make
  // sense to send the canonical metadata back to the producer. However, one of
  // the consumer can still choose to write up to user_metadata_size bytes of
  // data into user_metadata_ptr.
  if (meta->user_metadata_ptr && meta->user_metadata_size) {
    void* metadata_src = reinterpret_cast<void*>(meta->user_metadata_ptr);
    memcpy(user_metadata_ptr_, metadata_src, meta->user_metadata_size);
  }

  // Send out the release fence through the shared epoll fd. Note that during
  // releasing the producer is not expected to be polling on the fence.
  if (const int error = UpdateSharedFence(release_fence, shared_release_fence_))
    return error;

  // For release operation, the client don't need to change the state as it's
  // bufferhubd's job to flip the produer bit once all consumers are released.
  return 0;
}

int BufferConsumer::Release(const LocalHandle& release_fence) {
  ATRACE_NAME("BufferConsumer::Release");

  DvrNativeBufferMetadata meta;
  if (const int error = LocalRelease(&meta, release_fence))
    return error;

  return ReturnStatusOrError(InvokeRemoteMethod<BufferHubRPC::ConsumerRelease>(
      BorrowedFence(release_fence.Borrow())));
}

int BufferConsumer::ReleaseAsync() {
  DvrNativeBufferMetadata meta;
  return ReleaseAsync(&meta, LocalHandle());
}

int BufferConsumer::ReleaseAsync(const DvrNativeBufferMetadata* meta,
                                 const LocalHandle& release_fence) {
  ATRACE_NAME("BufferConsumer::ReleaseAsync");

  if (const int error = LocalRelease(meta, release_fence))
    return error;

  return ReturnStatusOrError(
      SendImpulse(BufferHubRPC::ConsumerRelease::Opcode));
}

int BufferConsumer::Discard() { return Release(LocalHandle()); }

int BufferConsumer::SetIgnore(bool ignore) {
  return ReturnStatusOrError(
      InvokeRemoteMethod<BufferHubRPC::ConsumerSetIgnore>(ignore));
}

BufferProducer::BufferProducer(uint32_t width, uint32_t height, uint32_t format,
                               uint32_t usage, size_t user_metadata_size)
    : BufferProducer(width, height, format, usage, usage, user_metadata_size) {}

BufferProducer::BufferProducer(uint32_t width, uint32_t height, uint32_t format,
                               uint64_t producer_usage, uint64_t consumer_usage,
                               size_t user_metadata_size)
    : BASE(BufferHubRPC::kClientPath) {
  ATRACE_NAME("BufferProducer::BufferProducer");
  ALOGD_IF(TRACE,
           "BufferProducer::BufferProducer: fd=%d width=%u height=%u format=%u "
           "producer_usage=%" PRIx64 " consumer_usage=%" PRIx64
           " user_metadata_size=%zu",
           event_fd(), width, height, format, producer_usage, consumer_usage,
           user_metadata_size);

  // (b/37881101) Deprecate producer/consumer usage
  auto status = InvokeRemoteMethod<BufferHubRPC::CreateBuffer>(
      width, height, format, (producer_usage | consumer_usage),
      user_metadata_size);
  if (!status) {
    ALOGE(
        "BufferProducer::BufferProducer: Failed to create producer buffer: %s",
        status.GetErrorMessage().c_str());
    Close(-status.error());
    return;
  }

  const int ret = ImportBuffer();
  if (ret < 0) {
    ALOGE(
        "BufferProducer::BufferProducer: Failed to import producer buffer: %s",
        strerror(-ret));
    Close(ret);
  }
}

BufferProducer::BufferProducer(const std::string& name, int user_id,
                               int group_id, uint32_t width, uint32_t height,
                               uint32_t format, uint32_t usage,
                               size_t user_metadata_size)
    : BufferProducer(name, user_id, group_id, width, height, format, usage,
                     usage, user_metadata_size) {}

BufferProducer::BufferProducer(const std::string& name, int user_id,
                               int group_id, uint32_t width, uint32_t height,
                               uint32_t format, uint64_t producer_usage,
                               uint64_t consumer_usage,
                               size_t user_metadata_size)
    : BASE(BufferHubRPC::kClientPath) {
  ATRACE_NAME("BufferProducer::BufferProducer");
  ALOGD_IF(TRACE,
           "BufferProducer::BufferProducer: fd=%d name=%s user_id=%d "
           "group_id=%d width=%u height=%u format=%u producer_usage=%" PRIx64
           " consumer_usage=%" PRIx64 " user_metadata_size=%zu",
           event_fd(), name.c_str(), user_id, group_id, width, height, format,
           producer_usage, consumer_usage, user_metadata_size);

  // (b/37881101) Deprecate producer/consumer usage
  auto status = InvokeRemoteMethod<BufferHubRPC::CreatePersistentBuffer>(
      name, user_id, group_id, width, height, format,
      (producer_usage | consumer_usage), user_metadata_size);
  if (!status) {
    ALOGE(
        "BufferProducer::BufferProducer: Failed to create/get persistent "
        "buffer \"%s\": %s",
        name.c_str(), status.GetErrorMessage().c_str());
    Close(-status.error());
    return;
  }

  const int ret = ImportBuffer();
  if (ret < 0) {
    ALOGE(
        "BufferProducer::BufferProducer: Failed to import producer buffer "
        "\"%s\": %s",
        name.c_str(), strerror(-ret));
    Close(ret);
  }
}

BufferProducer::BufferProducer(uint32_t usage, size_t size)
    : BufferProducer(usage, usage, size) {}

BufferProducer::BufferProducer(uint64_t producer_usage, uint64_t consumer_usage,
                               size_t size)
    : BASE(BufferHubRPC::kClientPath) {
  ATRACE_NAME("BufferProducer::BufferProducer");
  ALOGD_IF(TRACE,
           "BufferProducer::BufferProducer: producer_usage=%" PRIx64
           " consumer_usage=%" PRIx64 " size=%zu",
           producer_usage, consumer_usage, size);
  const int width = static_cast<int>(size);
  const int height = 1;
  const int format = HAL_PIXEL_FORMAT_BLOB;
  const size_t user_metadata_size = 0;

  // (b/37881101) Deprecate producer/consumer usage
  auto status = InvokeRemoteMethod<BufferHubRPC::CreateBuffer>(
      width, height, format, (producer_usage | consumer_usage),
      user_metadata_size);
  if (!status) {
    ALOGE("BufferProducer::BufferProducer: Failed to create blob: %s",
          status.GetErrorMessage().c_str());
    Close(-status.error());
    return;
  }

  const int ret = ImportBuffer();
  if (ret < 0) {
    ALOGE(
        "BufferProducer::BufferProducer: Failed to import producer buffer: %s",
        strerror(-ret));
    Close(ret);
  }
}

BufferProducer::BufferProducer(const std::string& name, int user_id,
                               int group_id, uint32_t usage, size_t size)
    : BufferProducer(name, user_id, group_id, usage, usage, size) {}

BufferProducer::BufferProducer(const std::string& name, int user_id,
                               int group_id, uint64_t producer_usage,
                               uint64_t consumer_usage, size_t size)
    : BASE(BufferHubRPC::kClientPath) {
  ATRACE_NAME("BufferProducer::BufferProducer");
  ALOGD_IF(TRACE,
           "BufferProducer::BufferProducer: name=%s user_id=%d group=%d "
           "producer_usage=%" PRIx64 " consumer_usage=%" PRIx64 " size=%zu",
           name.c_str(), user_id, group_id, producer_usage, consumer_usage,
           size);
  const int width = static_cast<int>(size);
  const int height = 1;
  const int format = HAL_PIXEL_FORMAT_BLOB;
  const size_t user_metadata_size = 0;

  // (b/37881101) Deprecate producer/consumer usage
  auto status = InvokeRemoteMethod<BufferHubRPC::CreatePersistentBuffer>(
      name, user_id, group_id, width, height, format,
      (producer_usage | consumer_usage), user_metadata_size);
  if (!status) {
    ALOGE(
        "BufferProducer::BufferProducer: Failed to create persistent "
        "buffer \"%s\": %s",
        name.c_str(), status.GetErrorMessage().c_str());
    Close(-status.error());
    return;
  }

  const int ret = ImportBuffer();
  if (ret < 0) {
    ALOGE(
        "BufferProducer::BufferProducer: Failed to import producer buffer "
        "\"%s\": %s",
        name.c_str(), strerror(-ret));
    Close(ret);
  }
}

BufferProducer::BufferProducer(const std::string& name)
    : BASE(BufferHubRPC::kClientPath) {
  ATRACE_NAME("BufferProducer::BufferProducer");
  ALOGD_IF(TRACE, "BufferProducer::BufferProducer: name=%s", name.c_str());

  auto status = InvokeRemoteMethod<BufferHubRPC::GetPersistentBuffer>(name);
  if (!status) {
    ALOGE(
        "BufferProducer::BufferProducer: Failed to get producer buffer by name "
        "\"%s\": %s",
        name.c_str(), status.GetErrorMessage().c_str());
    Close(-status.error());
    return;
  }

  const int ret = ImportBuffer();
  if (ret < 0) {
    ALOGE(
        "BufferProducer::BufferProducer: Failed to import producer buffer "
        "\"%s\": %s",
        name.c_str(), strerror(-ret));
    Close(ret);
  }
}

BufferProducer::BufferProducer(LocalChannelHandle channel)
    : BASE(std::move(channel)) {
  const int ret = ImportBuffer();
  if (ret < 0) {
    ALOGE(
        "BufferProducer::BufferProducer: Failed to import producer buffer: %s",
        strerror(-ret));
    Close(ret);
  }
}

int BufferProducer::LocalPost(const DvrNativeBufferMetadata* meta,
                              const LocalHandle& ready_fence) {
  if (const int error = CheckMetadata(meta->user_metadata_size))
    return error;

  // Check invalid state transition.
  uint64_t buffer_state = buffer_state_->load();
  if (!BufferHubDefs::IsBufferGained(buffer_state)) {
    ALOGE("BufferProducer::LocalPost: not gained, id=%d state=%" PRIx64 ".",
          id(), buffer_state);
    return -EBUSY;
  }

  // Copy the canonical metadata.
  void* metadata_ptr = reinterpret_cast<void*>(&metadata_header_->metadata);
  memcpy(metadata_ptr, meta, sizeof(DvrNativeBufferMetadata));
  // Copy extra user requested metadata.
  if (meta->user_metadata_ptr && meta->user_metadata_size) {
    void* metadata_src = reinterpret_cast<void*>(meta->user_metadata_ptr);
    memcpy(user_metadata_ptr_, metadata_src, meta->user_metadata_size);
  }

  // Send out the acquire fence through the shared epoll fd. Note that during
  // posting no consumer is not expected to be polling on the fence.
  if (const int error = UpdateSharedFence(ready_fence, shared_acquire_fence_))
    return error;

  // Set the producer bit atomically to transit into posted state.
  BufferHubDefs::ModifyBufferState(buffer_state_, 0ULL,
                                   BufferHubDefs::kProducerStateBit);
  return 0;
}

int BufferProducer::Post(const LocalHandle& ready_fence, const void* meta,
                         size_t user_metadata_size) {
  ATRACE_NAME("BufferProducer::Post");

  // Populate cononical metadata for posting.
  DvrNativeBufferMetadata canonical_meta;
  canonical_meta.user_metadata_ptr = reinterpret_cast<uint64_t>(meta);
  canonical_meta.user_metadata_size = user_metadata_size;

  if (const int error = LocalPost(&canonical_meta, ready_fence))
    return error;

  return ReturnStatusOrError(InvokeRemoteMethod<BufferHubRPC::ProducerPost>(
      BorrowedFence(ready_fence.Borrow())));
}

int BufferProducer::PostAsync(const DvrNativeBufferMetadata* meta,
                              const LocalHandle& ready_fence) {
  ATRACE_NAME("BufferProducer::PostAsync");

  if (const int error = LocalPost(meta, ready_fence))
    return error;

  return ReturnStatusOrError(SendImpulse(BufferHubRPC::ProducerPost::Opcode));
}

int BufferProducer::LocalGain(DvrNativeBufferMetadata* out_meta,
                              LocalHandle* out_fence) {
  uint64_t buffer_state = buffer_state_->load();
  ALOGD_IF(TRACE, "BufferProducer::LocalGain: buffer=%d, state=%" PRIx64 ".",
           id(), buffer_state);

  if (!out_meta)
    return -EINVAL;

  if (!BufferHubDefs::IsBufferReleased(buffer_state)) {
    if (BufferHubDefs::IsBufferGained(buffer_state)) {
      // We don't want to log error when gaining a newly allocated
      // buffer.
      ALOGI("BufferProducer::LocalGain: already gained id=%d.", id());
      return -EALREADY;
    }
    ALOGE("BufferProducer::LocalGain: not released id=%d state=%" PRIx64 ".",
          id(), buffer_state);
    return -EBUSY;
  }

  // Canonical metadata is undefined on Gain. Except for user_metadata and
  // release_fence_mask. Fill in the user_metadata_ptr in address space of the
  // local process.
  if (metadata_header_->metadata.user_metadata_size && user_metadata_ptr_) {
    out_meta->user_metadata_size =
        metadata_header_->metadata.user_metadata_size;
    out_meta->user_metadata_ptr =
        reinterpret_cast<uint64_t>(user_metadata_ptr_);
  } else {
    out_meta->user_metadata_size = 0;
    out_meta->user_metadata_ptr = 0;
  }

  uint64_t fence_state = fence_state_->load();
  // If there is an release fence from consumer, we need to return it.
  if (fence_state & BufferHubDefs::kConsumerStateMask) {
    *out_fence = shared_release_fence_.Duplicate();
    out_meta->release_fence_mask =
        fence_state & BufferHubDefs::kConsumerStateMask;
  }

  // Clear out all bits and the buffer is now back to gained state.
  buffer_state_->store(0ULL);
  return 0;
}

int BufferProducer::Gain(LocalHandle* release_fence) {
  ATRACE_NAME("BufferProducer::Gain");

  DvrNativeBufferMetadata meta;
  if (const int error = LocalGain(&meta, release_fence))
    return error;

  auto status = InvokeRemoteMethod<BufferHubRPC::ProducerGain>();
  if (!status)
    return -status.error();
  return 0;
}

int BufferProducer::GainAsync(DvrNativeBufferMetadata* out_meta,
                              LocalHandle* release_fence) {
  ATRACE_NAME("BufferProducer::GainAsync");

  if (const int error = LocalGain(out_meta, release_fence))
    return error;

  return ReturnStatusOrError(SendImpulse(BufferHubRPC::ProducerGain::Opcode));
}

int BufferProducer::GainAsync() {
  DvrNativeBufferMetadata meta;
  LocalHandle fence;
  return GainAsync(&meta, &fence);
}

std::unique_ptr<BufferProducer> BufferProducer::Import(
    LocalChannelHandle channel) {
  ALOGD_IF(TRACE, "BufferProducer::Import: channel=%d", channel.value());
  return BufferProducer::Create(std::move(channel));
}

std::unique_ptr<BufferProducer> BufferProducer::Import(
    Status<LocalChannelHandle> status) {
  return Import(status ? status.take()
                       : LocalChannelHandle{nullptr, -status.error()});
}

int BufferProducer::MakePersistent(const std::string& name, int user_id,
                                   int group_id) {
  ATRACE_NAME("BufferProducer::MakePersistent");
  return ReturnStatusOrError(
      InvokeRemoteMethod<BufferHubRPC::ProducerMakePersistent>(name, user_id,
                                                               group_id));
}

int BufferProducer::RemovePersistence() {
  ATRACE_NAME("BufferProducer::RemovePersistence");
  return ReturnStatusOrError(
      InvokeRemoteMethod<BufferHubRPC::ProducerRemovePersistence>());
}

}  // namespace dvr
}  // namespace android
