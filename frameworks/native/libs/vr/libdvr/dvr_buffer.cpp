#include "include/dvr/dvr_buffer.h"

#include <android/hardware_buffer.h>
#include <dvr/dvr_shared_buffers.h>
#include <private/dvr/buffer_hub_client.h>
#include <ui/GraphicBuffer.h>

#include "dvr_internal.h"

using namespace android;

namespace android {
namespace dvr {

DvrBuffer* CreateDvrBufferFromIonBuffer(
    const std::shared_ptr<IonBuffer>& ion_buffer) {
  if (!ion_buffer)
    return nullptr;
  return new DvrBuffer{std::move(ion_buffer)};
}

}  // namespace dvr
}  // namespace android

namespace {

int ConvertToAHardwareBuffer(GraphicBuffer* graphic_buffer,
                             AHardwareBuffer** hardware_buffer) {
  if (!hardware_buffer || !graphic_buffer) {
    return -EINVAL;
  }
  *hardware_buffer = reinterpret_cast<AHardwareBuffer*>(graphic_buffer);
  AHardwareBuffer_acquire(*hardware_buffer);
  return 0;
}

}  // anonymous namespace

extern "C" {

void dvrWriteBufferCreateEmpty(DvrWriteBuffer** write_buffer) {
  if (write_buffer)
    *write_buffer = new DvrWriteBuffer;
}

void dvrWriteBufferDestroy(DvrWriteBuffer* write_buffer) {
  if (write_buffer != nullptr) {
    ALOGW_IF(
        write_buffer->slot != -1,
        "dvrWriteBufferDestroy: Destroying a buffer associated with a valid "
        "buffer queue slot. This may indicate possible leaks.");
    delete write_buffer;
  }
}

int dvrWriteBufferIsValid(DvrWriteBuffer* write_buffer) {
  return write_buffer && write_buffer->write_buffer;
}

int dvrWriteBufferClear(DvrWriteBuffer* write_buffer) {
  if (!write_buffer)
    return -EINVAL;

  write_buffer->write_buffer = nullptr;
  return 0;
}

int dvrWriteBufferGetId(DvrWriteBuffer* write_buffer) {
  if (!write_buffer || !write_buffer->write_buffer)
    return -EINVAL;

  return write_buffer->write_buffer->id();
}

int dvrWriteBufferGetAHardwareBuffer(DvrWriteBuffer* write_buffer,
                                     AHardwareBuffer** hardware_buffer) {
  if (!write_buffer || !write_buffer->write_buffer)
    return -EINVAL;

  return ConvertToAHardwareBuffer(
      write_buffer->write_buffer->buffer()->buffer().get(), hardware_buffer);
}

int dvrWriteBufferPost(DvrWriteBuffer* write_buffer, int ready_fence_fd,
                       const void* meta, size_t meta_size_bytes) {
  if (!write_buffer || !write_buffer->write_buffer)
    return -EINVAL;

  pdx::LocalHandle fence(ready_fence_fd);
  int result = write_buffer->write_buffer->Post(fence, meta, meta_size_bytes);
  return result;
}

int dvrWriteBufferGain(DvrWriteBuffer* write_buffer, int* release_fence_fd) {
  if (!write_buffer || !write_buffer->write_buffer || !release_fence_fd)
    return -EINVAL;

  pdx::LocalHandle release_fence;
  int result = write_buffer->write_buffer->Gain(&release_fence);
  *release_fence_fd = release_fence.Release();
  return result;
}

int dvrWriteBufferGainAsync(DvrWriteBuffer* write_buffer) {
  if (!write_buffer || !write_buffer->write_buffer)
    return -EINVAL;

  return write_buffer->write_buffer->GainAsync();
}

void dvrReadBufferCreateEmpty(DvrReadBuffer** read_buffer) {
  if (read_buffer)
    *read_buffer = new DvrReadBuffer;
}

void dvrReadBufferDestroy(DvrReadBuffer* read_buffer) {
  if (read_buffer != nullptr) {
    ALOGW_IF(
        read_buffer->slot != -1,
        "dvrReadBufferDestroy: Destroying a buffer associated with a valid "
        "buffer queue slot. This may indicate possible leaks.");
    delete read_buffer;
  }
}

int dvrReadBufferIsValid(DvrReadBuffer* read_buffer) {
  return read_buffer && read_buffer->read_buffer;
}

int dvrReadBufferClear(DvrReadBuffer* read_buffer) {
  if (!read_buffer)
    return -EINVAL;

  read_buffer->read_buffer = nullptr;
  return 0;
}

int dvrReadBufferGetId(DvrReadBuffer* read_buffer) {
  if (!read_buffer || !read_buffer->read_buffer)
    return -EINVAL;

  return read_buffer->read_buffer->id();
}

int dvrReadBufferGetAHardwareBuffer(DvrReadBuffer* read_buffer,
                                    AHardwareBuffer** hardware_buffer) {
  if (!read_buffer || !read_buffer->read_buffer)
    return -EINVAL;

  return ConvertToAHardwareBuffer(
      read_buffer->read_buffer->buffer()->buffer().get(), hardware_buffer);
}

int dvrReadBufferAcquire(DvrReadBuffer* read_buffer, int* ready_fence_fd,
                         void* meta, size_t meta_size_bytes) {
  if (!read_buffer || !read_buffer->read_buffer)
    return -EINVAL;

  pdx::LocalHandle ready_fence;
  int result =
      read_buffer->read_buffer->Acquire(&ready_fence, meta, meta_size_bytes);
  *ready_fence_fd = ready_fence.Release();
  return result;
}

int dvrReadBufferRelease(DvrReadBuffer* read_buffer, int release_fence_fd) {
  if (!read_buffer || !read_buffer->read_buffer)
    return -EINVAL;

  pdx::LocalHandle fence(release_fence_fd);
  int result = read_buffer->read_buffer->Release(fence);
  return result;
}

int dvrReadBufferReleaseAsync(DvrReadBuffer* read_buffer) {
  if (!read_buffer || !read_buffer->read_buffer)
    return -EINVAL;

  return read_buffer->read_buffer->ReleaseAsync();
}

void dvrBufferDestroy(DvrBuffer* buffer) { delete buffer; }

int dvrBufferGetAHardwareBuffer(DvrBuffer* buffer,
                                AHardwareBuffer** hardware_buffer) {
  if (!buffer || !buffer->buffer || !hardware_buffer) {
    return -EINVAL;
  }

  return ConvertToAHardwareBuffer(buffer->buffer->buffer().get(),
                                  hardware_buffer);
}

// Retrieve the shared buffer layout version defined in dvr_shared_buffers.h.
int dvrBufferGlobalLayoutVersionGet() {
  return android::dvr::kSharedBufferLayoutVersion;
}

const struct native_handle* dvrWriteBufferGetNativeHandle(
    DvrWriteBuffer* write_buffer) {
  if (!write_buffer || !write_buffer->write_buffer)
    return nullptr;

  return write_buffer->write_buffer->native_handle();
}

const struct native_handle* dvrReadBufferGetNativeHandle(
    DvrReadBuffer* read_buffer) {
  if (!read_buffer || !read_buffer->read_buffer)
    return nullptr;

  return read_buffer->read_buffer->native_handle();
}

const struct native_handle* dvrBufferGetNativeHandle(DvrBuffer* buffer) {
  if (!buffer || !buffer->buffer)
    return nullptr;

  return buffer->buffer->handle();
}

}  // extern "C"
