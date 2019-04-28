#ifndef ANDROID_DVR_BUFFER_H_
#define ANDROID_DVR_BUFFER_H_

#include <stdbool.h>
#include <stdint.h>
#include <sys/cdefs.h>
#include <memory>

__BEGIN_DECLS

typedef struct DvrWriteBuffer DvrWriteBuffer;
typedef struct DvrReadBuffer DvrReadBuffer;
typedef struct DvrBuffer DvrBuffer;
typedef struct AHardwareBuffer AHardwareBuffer;
struct native_handle;

// Creates an empty write buffer that may be filled with an acutal buffer by
// other functions.
void dvrWriteBufferCreateEmpty(DvrWriteBuffer** write_buffer);

// Destroys the write buffer.
void dvrWriteBufferDestroy(DvrWriteBuffer* write_buffer);

// Returns 1 if the given write buffer object contains a buffer, 0 otherwise.
int dvrWriteBufferIsValid(DvrWriteBuffer* write_buffer);

// Clears the contents of the buffer object. After a call to this function
// dvrWriteBufferIsValid on the same buffer object returns 0.
int dvrWriteBufferClear(DvrWriteBuffer* write_buffer);

// Returns the global BufferHub id of this buffer.
int dvrWriteBufferGetId(DvrWriteBuffer* write_buffer);

// Returns an AHardwareBuffer for the underlying buffer.
// Caller must call AHardwareBuffer_release on hardware_buffer.
int dvrWriteBufferGetAHardwareBuffer(DvrWriteBuffer* write_buffer,
                                     AHardwareBuffer** hardware_buffer);

// Posts the buffer, notifying any connected read buffers. Takes ownership of
// |ready_fence_fd|.
int dvrWriteBufferPost(DvrWriteBuffer* write_buffer, int ready_fence_fd,
                       const void* meta, size_t meta_size_bytes);

// Gains a buffer that has been released by all connected read buffers.
int dvrWriteBufferGain(DvrWriteBuffer* write_buffer, int* release_fence_fd);
int dvrWriteBufferGainAsync(DvrWriteBuffer* write_buffer);

// TODO(eieio): Switch to return int and take an out parameter for the native
// handle.
const struct native_handle* dvrWriteBufferGetNativeHandle(
    DvrWriteBuffer* write_buffer);

// Creates an empty read buffer that may be filled with and actual buffer by
// other functions.
void dvrReadBufferCreateEmpty(DvrReadBuffer** read_buffer);

// Destroys the read buffer.
void dvrReadBufferDestroy(DvrReadBuffer* read_buffer);

// Returns 1 if the given write buffer object contains a buffer, 0 otherwise.
int dvrReadBufferIsValid(DvrReadBuffer* read_buffer);

// Clears the contents of the buffer object. After a call to this function
// dvrReadBufferIsValid on the same buffer object returns 0.
int dvrReadBufferClear(DvrReadBuffer* read_buffer);

// Returns the global BufferHub id of this buffer.
int dvrReadBufferGetId(DvrReadBuffer* read_buffer);

// Returns an AHardwareBuffer for the underlying buffer.
// Caller must call AHardwareBuffer_release on hardware_buffer.
int dvrReadBufferGetAHardwareBuffer(DvrReadBuffer* read_buffer,
                                    AHardwareBuffer** hardware_buffer);

// Acquires the read buffer after it has been posted by the write buffer it is
// connected to.
int dvrReadBufferAcquire(DvrReadBuffer* read_buffer, int* ready_fence_fd,
                         void* meta, size_t meta_size_bytes);

// Releases the read buffer, notifying the write buffer it is connected to.
// Takes ownership of |release_fence_fd|.
int dvrReadBufferRelease(DvrReadBuffer* read_buffer, int release_fence_fd);
int dvrReadBufferReleaseAsync(DvrReadBuffer* read_buffer);

// TODO(eieio): Switch to return int and take an out parameter for the native
// handle.
const struct native_handle* dvrReadBufferGetNativeHandle(
    DvrReadBuffer* read_buffer);

// Destroys the buffer.
void dvrBufferDestroy(DvrBuffer* buffer);

// Gets an AHardwareBuffer from the buffer.
// Caller must call AHardwareBuffer_release on hardware_buffer.
int dvrBufferGetAHardwareBuffer(DvrBuffer* buffer,
                                AHardwareBuffer** hardware_buffer);

// Retrieve the shared buffer layout version defined in dvr_shared_buffers.h.
int dvrBufferGlobalLayoutVersionGet();

// TODO(eieio): Switch to return int and take an out parameter for the native
// handle.
const struct native_handle* dvrBufferGetNativeHandle(DvrBuffer* buffer);

__END_DECLS

#endif  // ANDROID_DVR_BUFFER_H_
