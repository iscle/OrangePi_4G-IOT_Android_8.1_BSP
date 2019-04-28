#include "include/private/dvr/buffer_hub_queue_producer.h"

#include <dvr/dvr_api.h>
#include <inttypes.h>
#include <log/log.h>
#include <system/window.h>

namespace android {
namespace dvr {

/* static */
sp<BufferHubQueueProducer> BufferHubQueueProducer::Create() {
  sp<BufferHubQueueProducer> producer = new BufferHubQueueProducer;
  auto config = ProducerQueueConfigBuilder()
                    .SetMetadata<DvrNativeBufferMetadata>()
                    .Build();
  producer->queue_ = ProducerQueue::Create(config, UsagePolicy{});
  return producer;
}

/* static */
sp<BufferHubQueueProducer> BufferHubQueueProducer::Create(
    const std::shared_ptr<ProducerQueue>& queue) {
  if (queue->metadata_size() != sizeof(DvrNativeBufferMetadata)) {
    ALOGE(
        "BufferHubQueueProducer::Create producer's metadata size is different "
        "than the size of DvrNativeBufferMetadata");
    return nullptr;
  }

  sp<BufferHubQueueProducer> producer = new BufferHubQueueProducer;
  producer->queue_ = queue;
  return producer;
}

status_t BufferHubQueueProducer::requestBuffer(int slot,
                                               sp<GraphicBuffer>* buf) {
  ALOGD_IF(TRACE, "requestBuffer: slot=%d", slot);

  std::unique_lock<std::mutex> lock(mutex_);

  if (connected_api_ == kNoConnectedApi) {
    ALOGE("requestBuffer: BufferHubQueueProducer has no connected producer");
    return NO_INIT;
  }

  if (slot < 0 || slot >= max_buffer_count_) {
    ALOGE("requestBuffer: slot index %d out of range [0, %d)", slot,
          max_buffer_count_);
    return BAD_VALUE;
  } else if (!buffers_[slot].mBufferState.isDequeued()) {
    ALOGE("requestBuffer: slot %d is not owned by the producer (state = %s)",
          slot, buffers_[slot].mBufferState.string());
    return BAD_VALUE;
  } else if (buffers_[slot].mGraphicBuffer != nullptr) {
    ALOGE("requestBuffer: slot %d is not empty.", slot);
    return BAD_VALUE;
  } else if (buffers_[slot].mBufferProducer == nullptr) {
    ALOGE("requestBuffer: slot %d is not dequeued.", slot);
    return BAD_VALUE;
  }

  const auto& buffer_producer = buffers_[slot].mBufferProducer;
  sp<GraphicBuffer> graphic_buffer = buffer_producer->buffer()->buffer();

  buffers_[slot].mGraphicBuffer = graphic_buffer;
  buffers_[slot].mRequestBufferCalled = true;

  *buf = graphic_buffer;
  return NO_ERROR;
}

status_t BufferHubQueueProducer::setMaxDequeuedBufferCount(
    int max_dequeued_buffers) {
  ALOGD_IF(TRACE, "setMaxDequeuedBufferCount: max_dequeued_buffers=%d",
           max_dequeued_buffers);

  std::unique_lock<std::mutex> lock(mutex_);

  if (max_dequeued_buffers <= 0 ||
      max_dequeued_buffers >
          static_cast<int>(BufferHubQueue::kMaxQueueCapacity -
                           kDefaultUndequeuedBuffers)) {
    ALOGE("setMaxDequeuedBufferCount: %d out of range (0, %zu]",
          max_dequeued_buffers, BufferHubQueue::kMaxQueueCapacity);
    return BAD_VALUE;
  }

  // The new dequeued_buffers count should not be violated by the number
  // of currently dequeued buffers.
  int dequeued_count = 0;
  for (const auto& buf : buffers_) {
    if (buf.mBufferState.isDequeued()) {
      dequeued_count++;
    }
  }
  if (dequeued_count > max_dequeued_buffers) {
    ALOGE(
        "setMaxDequeuedBufferCount: the requested dequeued_buffers"
        "count (%d) exceeds the current dequeued buffer count (%d)",
        max_dequeued_buffers, dequeued_count);
    return BAD_VALUE;
  }

  max_dequeued_buffer_count_ = max_dequeued_buffers;
  return NO_ERROR;
}

status_t BufferHubQueueProducer::setAsyncMode(bool async) {
  if (async) {
    // TODO(b/36724099) BufferHubQueue's consumer end always acquires the buffer
    // automatically and behaves differently from IGraphicBufferConsumer. Thus,
    // android::BufferQueue's async mode (a.k.a. allocating an additional buffer
    // to prevent dequeueBuffer from being blocking) technically does not apply
    // here.
    //
    // In Daydream, non-blocking producer side dequeue is guaranteed by careful
    // buffer consumer implementations. In another word, BufferHubQueue based
    // dequeueBuffer should never block whether setAsyncMode(true) is set or
    // not.
    //
    // See: IGraphicBufferProducer::setAsyncMode and
    // BufferQueueProducer::setAsyncMode for more about original implementation.
    ALOGW(
        "BufferHubQueueProducer::setAsyncMode: BufferHubQueue should always be "
        "asynchronous. This call makes no effact.");
    return NO_ERROR;
  }
  return NO_ERROR;
}

status_t BufferHubQueueProducer::dequeueBuffer(
    int* out_slot, sp<Fence>* out_fence, uint32_t width, uint32_t height,
    PixelFormat format, uint64_t usage, uint64_t* /*outBufferAge*/,
    FrameEventHistoryDelta* /* out_timestamps */) {
  ALOGD_IF(TRACE, "dequeueBuffer: w=%u, h=%u, format=%d, usage=%" PRIu64, width,
           height, format, usage);

  status_t ret;
  std::unique_lock<std::mutex> lock(mutex_);

  if (connected_api_ == kNoConnectedApi) {
    ALOGE("dequeueBuffer: BufferQueue has no connected producer");
    return NO_INIT;
  }

  const uint32_t kLayerCount = 1;
  if (static_cast<int32_t>(queue_->capacity()) <
      max_dequeued_buffer_count_ + kDefaultUndequeuedBuffers) {
    // Lazy allocation. When the capacity of |queue_| has not reached
    // |max_dequeued_buffer_count_|, allocate new buffer.
    // TODO(jwcai) To save memory, the really reasonable thing to do is to go
    // over existing slots and find first existing one to dequeue.
    ret = AllocateBuffer(width, height, kLayerCount, format, usage);
    if (ret < 0)
      return ret;
  }

  size_t slot;
  std::shared_ptr<BufferProducer> buffer_producer;

  for (size_t retry = 0; retry < BufferHubQueue::kMaxQueueCapacity; retry++) {
    LocalHandle fence;
    auto buffer_status = queue_->Dequeue(dequeue_timeout_ms_, &slot, &fence);
    if (!buffer_status)
      return NO_MEMORY;

    buffer_producer = buffer_status.take();
    if (!buffer_producer)
      return NO_MEMORY;

    if (width == buffer_producer->width() &&
        height == buffer_producer->height() &&
        static_cast<uint32_t>(format) == buffer_producer->format()) {
      // The producer queue returns a buffer producer matches the request.
      break;
    }

    // Needs reallocation.
    // TODO(jwcai) Consider use VLOG instead if we find this log is not useful.
    ALOGI(
        "dequeueBuffer: requested buffer (w=%u, h=%u, format=%u) is different "
        "from the buffer returned at slot: %zu (w=%u, h=%u, format=%u). Need "
        "re-allocattion.",
        width, height, format, slot, buffer_producer->width(),
        buffer_producer->height(), buffer_producer->format());
    // Mark the slot as reallocating, so that later we can set
    // BUFFER_NEEDS_REALLOCATION when the buffer actually get dequeued.
    buffers_[slot].mIsReallocating = true;

    // Remove the old buffer once the allocation before allocating its
    // replacement.
    RemoveBuffer(slot);

    // Allocate a new producer buffer with new buffer configs. Note that if
    // there are already multiple buffers in the queue, the next one returned
    // from |queue_->Dequeue| may not be the new buffer we just reallocated.
    // Retry up to BufferHubQueue::kMaxQueueCapacity times.
    ret = AllocateBuffer(width, height, kLayerCount, format, usage);
    if (ret < 0)
      return ret;
  }

  // With the BufferHub backed solution. Buffer slot returned from
  // |queue_->Dequeue| is guaranteed to avaiable for producer's use.
  // It's either in free state (if the buffer has never been used before) or
  // in queued state (if the buffer has been dequeued and queued back to
  // BufferHubQueue).
  LOG_ALWAYS_FATAL_IF(
      (!buffers_[slot].mBufferState.isFree() &&
       !buffers_[slot].mBufferState.isQueued()),
      "dequeueBuffer: slot %zu is not free or queued, actual state: %s.", slot,
      buffers_[slot].mBufferState.string());

  buffers_[slot].mBufferState.freeQueued();
  buffers_[slot].mBufferState.dequeue();
  ALOGD_IF(TRACE, "dequeueBuffer: slot=%zu", slot);

  // TODO(jwcai) Handle fence properly. |BufferHub| has full fence support, we
  // just need to exopose that through |BufferHubQueue| once we need fence.
  *out_fence = Fence::NO_FENCE;
  *out_slot = slot;
  ret = NO_ERROR;

  if (buffers_[slot].mIsReallocating) {
    ret |= BUFFER_NEEDS_REALLOCATION;
    buffers_[slot].mIsReallocating = false;
  }

  return ret;
}

status_t BufferHubQueueProducer::detachBuffer(int /* slot */) {
  ALOGE("BufferHubQueueProducer::detachBuffer not implemented.");
  return INVALID_OPERATION;
}

status_t BufferHubQueueProducer::detachNextBuffer(
    sp<GraphicBuffer>* /* out_buffer */, sp<Fence>* /* out_fence */) {
  ALOGE("BufferHubQueueProducer::detachNextBuffer not implemented.");
  return INVALID_OPERATION;
}

status_t BufferHubQueueProducer::attachBuffer(
    int* /* out_slot */, const sp<GraphicBuffer>& /* buffer */) {
  // With this BufferHub backed implementation, we assume (for now) all buffers
  // are allocated and owned by the BufferHub. Thus the attempt of transfering
  // ownership of a buffer to the buffer queue is intentionally unsupported.
  LOG_ALWAYS_FATAL("BufferHubQueueProducer::attachBuffer not supported.");
  return INVALID_OPERATION;
}

status_t BufferHubQueueProducer::queueBuffer(int slot,
                                             const QueueBufferInput& input,
                                             QueueBufferOutput* output) {
  ALOGD_IF(TRACE, "queueBuffer: slot %d", slot);

  if (output == nullptr) {
    return BAD_VALUE;
  }

  int64_t timestamp;
  bool is_auto_timestamp;
  android_dataspace dataspace;
  Rect crop(Rect::EMPTY_RECT);
  int scaling_mode;
  uint32_t transform;
  sp<Fence> fence;

  input.deflate(&timestamp, &is_auto_timestamp, &dataspace, &crop,
                &scaling_mode, &transform, &fence);

  // Check input scaling mode is valid.
  switch (scaling_mode) {
    case NATIVE_WINDOW_SCALING_MODE_FREEZE:
    case NATIVE_WINDOW_SCALING_MODE_SCALE_TO_WINDOW:
    case NATIVE_WINDOW_SCALING_MODE_SCALE_CROP:
    case NATIVE_WINDOW_SCALING_MODE_NO_SCALE_CROP:
      break;
    default:
      ALOGE("queueBuffer: unknown scaling mode %d", scaling_mode);
      return BAD_VALUE;
  }

  // Check input fence is valid.
  if (fence == nullptr) {
    ALOGE("queueBuffer: fence is NULL");
    return BAD_VALUE;
  }

  status_t ret;
  std::unique_lock<std::mutex> lock(mutex_);

  if (connected_api_ == kNoConnectedApi) {
    ALOGE("queueBuffer: BufferQueue has no connected producer");
    return NO_INIT;
  }

  if (slot < 0 || slot >= max_buffer_count_) {
    ALOGE("queueBuffer: slot index %d out of range [0, %d)", slot,
          max_buffer_count_);
    return BAD_VALUE;
  } else if (!buffers_[slot].mBufferState.isDequeued()) {
    ALOGE("queueBuffer: slot %d is not owned by the producer (state = %s)",
          slot, buffers_[slot].mBufferState.string());
    return BAD_VALUE;
  } else if ((!buffers_[slot].mRequestBufferCalled ||
              buffers_[slot].mGraphicBuffer == nullptr)) {
    ALOGE(
        "queueBuffer: slot %d is not requested (mRequestBufferCalled=%d, "
        "mGraphicBuffer=%p)",
        slot, buffers_[slot].mRequestBufferCalled,
        buffers_[slot].mGraphicBuffer.get());
    return BAD_VALUE;
  }

  // Post the buffer producer with timestamp in the metadata.
  const auto& buffer_producer = buffers_[slot].mBufferProducer;

  // Check input crop is not out of boundary of current buffer.
  Rect buffer_rect(buffer_producer->width(), buffer_producer->height());
  Rect cropped_rect(Rect::EMPTY_RECT);
  crop.intersect(buffer_rect, &cropped_rect);
  if (cropped_rect != crop) {
    ALOGE("queueBuffer: slot %d has out-of-boundary crop.", slot);
    return BAD_VALUE;
  }

  LocalHandle fence_fd(fence->isValid() ? fence->dup() : -1);

  DvrNativeBufferMetadata meta_data;
  meta_data.timestamp = timestamp;
  meta_data.is_auto_timestamp = static_cast<int32_t>(is_auto_timestamp);
  meta_data.dataspace = static_cast<int32_t>(dataspace);
  meta_data.crop_left = crop.left;
  meta_data.crop_top = crop.top;
  meta_data.crop_right = crop.right;
  meta_data.crop_bottom = crop.bottom;
  meta_data.scaling_mode = static_cast<int32_t>(scaling_mode);
  meta_data.transform = static_cast<int32_t>(transform);

  buffer_producer->PostAsync(&meta_data, fence_fd);
  buffers_[slot].mBufferState.queue();

  output->width = buffer_producer->width();
  output->height = buffer_producer->height();
  output->transformHint = 0;  // default value, we don't use it yet.

  // |numPendingBuffers| counts of the number of buffers that has been enqueued
  // by the producer but not yet acquired by the consumer. Due to the nature
  // of BufferHubQueue design, this is hard to trace from the producer's client
  // side, but it's safe to assume it's zero.
  output->numPendingBuffers = 0;

  // Note that we are not setting nextFrameNumber here as it seems to be only
  // used by surface flinger. See more at b/22802885, ag/791760.
  output->nextFrameNumber = 0;

  return NO_ERROR;
}

status_t BufferHubQueueProducer::cancelBuffer(int slot,
                                              const sp<Fence>& fence) {
  ALOGD_IF(TRACE, __FUNCTION__);

  std::unique_lock<std::mutex> lock(mutex_);

  if (connected_api_ == kNoConnectedApi) {
    ALOGE("cancelBuffer: BufferQueue has no connected producer");
    return NO_INIT;
  }

  if (slot < 0 || slot >= max_buffer_count_) {
    ALOGE("cancelBuffer: slot index %d out of range [0, %d)", slot,
          max_buffer_count_);
    return BAD_VALUE;
  } else if (!buffers_[slot].mBufferState.isDequeued()) {
    ALOGE("cancelBuffer: slot %d is not owned by the producer (state = %s)",
          slot, buffers_[slot].mBufferState.string());
    return BAD_VALUE;
  } else if (fence == nullptr) {
    ALOGE("cancelBuffer: fence is NULL");
    return BAD_VALUE;
  }

  auto buffer_producer = buffers_[slot].mBufferProducer;
  queue_->Enqueue(buffer_producer, slot, 0ULL);
  buffers_[slot].mBufferState.cancel();
  buffers_[slot].mFence = fence;
  ALOGD_IF(TRACE, "cancelBuffer: slot %d", slot);

  return NO_ERROR;
}

status_t BufferHubQueueProducer::query(int what, int* out_value) {
  ALOGD_IF(TRACE, __FUNCTION__);

  std::unique_lock<std::mutex> lock(mutex_);

  if (out_value == nullptr) {
    ALOGE("query: out_value was NULL");
    return BAD_VALUE;
  }

  int value = 0;
  switch (what) {
    case NATIVE_WINDOW_MIN_UNDEQUEUED_BUFFERS:
      // TODO(b/36187402) This should be the maximum number of buffers that this
      // producer queue's consumer can acquire. Set to be at least one. Need to
      // find a way to set from the consumer side.
      value = kDefaultUndequeuedBuffers;
      break;
    case NATIVE_WINDOW_BUFFER_AGE:
      value = 0;
      break;
    case NATIVE_WINDOW_WIDTH:
      value = queue_->default_width();
      break;
    case NATIVE_WINDOW_HEIGHT:
      value = queue_->default_height();
      break;
    case NATIVE_WINDOW_FORMAT:
      value = queue_->default_format();
      break;
    case NATIVE_WINDOW_CONSUMER_RUNNING_BEHIND:
      // BufferHubQueue is always operating in async mode, thus semantically
      // consumer can never be running behind. See BufferQueueCore.cpp core
      // for more information about the original meaning of this flag.
      value = 0;
      break;
    case NATIVE_WINDOW_CONSUMER_USAGE_BITS:
      // TODO(jwcai) This is currently not implement as we don't need
      // IGraphicBufferConsumer parity.
      value = 0;
      break;
    case NATIVE_WINDOW_DEFAULT_DATASPACE:
      // TODO(jwcai) Return the default value android::BufferQueue is using as
      // there is no way dvr::ConsumerQueue can set it.
      value = 0;  // HAL_DATASPACE_UNKNOWN
      break;
    case NATIVE_WINDOW_STICKY_TRANSFORM:
      // TODO(jwcai) Return the default value android::BufferQueue is using as
      // there is no way dvr::ConsumerQueue can set it.
      value = 0;
      break;
    case NATIVE_WINDOW_CONSUMER_IS_PROTECTED:
      // In Daydream's implementation, the consumer end (i.e. VR Compostior)
      // knows how to handle protected buffers.
      value = 1;
      break;
    default:
      return BAD_VALUE;
  }

  ALOGD_IF(TRACE, "query: key=%d, v=%d", what, value);
  *out_value = value;
  return NO_ERROR;
}

status_t BufferHubQueueProducer::connect(
    const sp<IProducerListener>& /* listener */, int api,
    bool /* producer_controlled_by_app */, QueueBufferOutput* output) {
  // Consumer interaction are actually handled by buffer hub, and we need
  // to maintain consumer operations here. We only need to perform basic input
  // parameter checks here.
  ALOGD_IF(TRACE, __FUNCTION__);

  if (output == nullptr) {
    return BAD_VALUE;
  }

  std::unique_lock<std::mutex> lock(mutex_);

  if (connected_api_ != kNoConnectedApi) {
    return BAD_VALUE;
  }

  switch (api) {
    case NATIVE_WINDOW_API_EGL:
    case NATIVE_WINDOW_API_CPU:
    case NATIVE_WINDOW_API_MEDIA:
    case NATIVE_WINDOW_API_CAMERA:
      connected_api_ = api;

      output->width = queue_->default_width();
      output->height = queue_->default_height();

      // default values, we don't use them yet.
      output->transformHint = 0;
      output->numPendingBuffers = 0;
      output->nextFrameNumber = 0;
      output->bufferReplaced = false;

      break;
    default:
      ALOGE("BufferHubQueueProducer::connect: unknow API %d", api);
      return BAD_VALUE;
  }

  return NO_ERROR;
}

status_t BufferHubQueueProducer::disconnect(int api, DisconnectMode /*mode*/) {
  // Consumer interaction are actually handled by buffer hub, and we need
  // to maintain consumer operations here.  We only need to perform basic input
  // parameter checks here.
  ALOGD_IF(TRACE, __FUNCTION__);

  std::unique_lock<std::mutex> lock(mutex_);

  if (kNoConnectedApi == connected_api_) {
    return NO_INIT;
  } else if (api != connected_api_) {
    return BAD_VALUE;
  }

  FreeAllBuffers();
  connected_api_ = kNoConnectedApi;
  return NO_ERROR;
}

status_t BufferHubQueueProducer::setSidebandStream(
    const sp<NativeHandle>& stream) {
  if (stream != nullptr) {
    // TODO(jwcai) Investigate how is is used, maybe use BufferHubBuffer's
    // metadata.
    ALOGE("SidebandStream is not currently supported.");
    return INVALID_OPERATION;
  }
  return NO_ERROR;
}

void BufferHubQueueProducer::allocateBuffers(uint32_t /* width */,
                                             uint32_t /* height */,
                                             PixelFormat /* format */,
                                             uint64_t /* usage */) {
  // TODO(jwcai) |allocateBuffers| aims to preallocate up to the maximum number
  // of buffers permitted by the current BufferQueue configuration (aka
  // |max_buffer_count_|).
  ALOGE("BufferHubQueueProducer::allocateBuffers not implemented.");
}

status_t BufferHubQueueProducer::allowAllocation(bool /* allow */) {
  ALOGE("BufferHubQueueProducer::allowAllocation not implemented.");
  return INVALID_OPERATION;
}

status_t BufferHubQueueProducer::setGenerationNumber(
    uint32_t generation_number) {
  ALOGD_IF(TRACE, __FUNCTION__);

  std::unique_lock<std::mutex> lock(mutex_);
  generation_number_ = generation_number;
  return NO_ERROR;
}

String8 BufferHubQueueProducer::getConsumerName() const {
  // BufferHub based implementation could have one to many producer/consumer
  // relationship, thus |getConsumerName| from the producer side does not
  // make any sense.
  ALOGE("BufferHubQueueProducer::getConsumerName not supported.");
  return String8("BufferHubQueue::DummyConsumer");
}

status_t BufferHubQueueProducer::setSharedBufferMode(bool shared_buffer_mode) {
  if (shared_buffer_mode) {
    ALOGE(
        "BufferHubQueueProducer::setSharedBufferMode(true) is not supported.");
    // TODO(b/36373181) Front buffer mode for buffer hub queue as ANativeWindow.
    return INVALID_OPERATION;
  }
  // Setting to default should just work as a no-op.
  return NO_ERROR;
}

status_t BufferHubQueueProducer::setAutoRefresh(bool auto_refresh) {
  if (auto_refresh) {
    ALOGE("BufferHubQueueProducer::setAutoRefresh(true) is not supported.");
    return INVALID_OPERATION;
  }
  // Setting to default should just work as a no-op.
  return NO_ERROR;
}

status_t BufferHubQueueProducer::setDequeueTimeout(nsecs_t timeout) {
  ALOGD_IF(TRACE, __FUNCTION__);

  std::unique_lock<std::mutex> lock(mutex_);
  dequeue_timeout_ms_ = static_cast<int>(timeout / (1000 * 1000));
  return NO_ERROR;
}

status_t BufferHubQueueProducer::getLastQueuedBuffer(
    sp<GraphicBuffer>* /* out_buffer */, sp<Fence>* /* out_fence */,
    float /*out_transform_matrix*/[16]) {
  ALOGE("BufferHubQueueProducer::getLastQueuedBuffer not implemented.");
  return INVALID_OPERATION;
}

void BufferHubQueueProducer::getFrameTimestamps(
    FrameEventHistoryDelta* /*outDelta*/) {
  ALOGE("BufferHubQueueProducer::getFrameTimestamps not implemented.");
}

status_t BufferHubQueueProducer::getUniqueId(uint64_t* out_id) const {
  ALOGD_IF(TRACE, __FUNCTION__);

  *out_id = unique_id_;
  return NO_ERROR;
}

status_t BufferHubQueueProducer::getConsumerUsage(uint64_t* out_usage) const {
  ALOGD_IF(TRACE, __FUNCTION__);

  // same value as returned by querying NATIVE_WINDOW_CONSUMER_USAGE_BITS
  *out_usage = 0;
  return NO_ERROR;
}

status_t BufferHubQueueProducer::AllocateBuffer(uint32_t width, uint32_t height,
                                                uint32_t layer_count,
                                                PixelFormat format,
                                                uint64_t usage) {
  auto status =
      queue_->AllocateBuffer(width, height, layer_count, format, usage);
  if (!status) {
    ALOGE(
        "BufferHubQueueProducer::AllocateBuffer: Failed to allocate buffer: %s",
        status.GetErrorMessage().c_str());
    return NO_MEMORY;
  }

  size_t slot = status.get();
  auto buffer_producer = queue_->GetBuffer(slot);

  LOG_ALWAYS_FATAL_IF(buffer_producer == nullptr,
                      "Failed to get buffer producer at slot: %zu", slot);

  buffers_[slot].mBufferProducer = buffer_producer;

  return NO_ERROR;
}

status_t BufferHubQueueProducer::RemoveBuffer(size_t slot) {
  auto status = queue_->RemoveBuffer(slot);
  if (!status) {
    ALOGE("BufferHubQueueProducer::RemoveBuffer: Failed to remove buffer: %s",
          status.GetErrorMessage().c_str());
    return INVALID_OPERATION;
  }

  // Reset in memory objects related the the buffer.
  buffers_[slot].mBufferProducer = nullptr;
  buffers_[slot].mGraphicBuffer = nullptr;
  buffers_[slot].mBufferState.detachProducer();
  return NO_ERROR;
}

status_t BufferHubQueueProducer::FreeAllBuffers() {
  for (size_t slot = 0; slot < BufferHubQueue::kMaxQueueCapacity; slot++) {
    // Reset in memory objects related the the buffer.
    buffers_[slot].mGraphicBuffer = nullptr;
    buffers_[slot].mBufferState.reset();
    buffers_[slot].mRequestBufferCalled = false;
    buffers_[slot].mBufferProducer = nullptr;
    buffers_[slot].mFence = Fence::NO_FENCE;
  }

  auto status = queue_->FreeAllBuffers();
  if (!status) {
    ALOGE(
        "BufferHubQueueProducer::FreeAllBuffers: Failed to free all buffers on "
        "the queue: %s",
        status.GetErrorMessage().c_str());
  }

  if (queue_->capacity() != 0 || queue_->count() != 0) {
    LOG_ALWAYS_FATAL(
        "BufferHubQueueProducer::FreeAllBuffers: Not all buffers are freed.");
  }

  return NO_ERROR;
}

}  // namespace dvr
}  // namespace android
