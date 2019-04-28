#ifndef ANDROID_DVR_BUFFER_HUB_CLIENT_H_
#define ANDROID_DVR_BUFFER_HUB_CLIENT_H_

#include <hardware/gralloc.h>
#include <pdx/channel_handle.h>
#include <pdx/client.h>
#include <pdx/file_handle.h>
#include <pdx/status.h>

#include <vector>

#include <private/dvr/ion_buffer.h>

#include "bufferhub_rpc.h"

namespace android {
namespace dvr {

class BufferHubBuffer : public pdx::Client {
 public:
  using LocalHandle = pdx::LocalHandle;
  using LocalChannelHandle = pdx::LocalChannelHandle;
  template <typename T>
  using Status = pdx::Status<T>;

  // Create a new consumer channel that is attached to the producer. Returns
  // a file descriptor for the new channel or a negative error code.
  Status<LocalChannelHandle> CreateConsumer();

  // Polls the fd for |timeout_ms| milliseconds (-1 for infinity).
  int Poll(int timeout_ms);

  // Locks the area specified by (x, y, width, height) for a specific usage. If
  // the usage is software then |addr| will be updated to point to the address
  // of the buffer in virtual memory. The caller should only access/modify the
  // pixels in the specified area. anything else is undefined behavior.
  int Lock(int usage, int x, int y, int width, int height, void** addr);

  // Must be called after Lock() when the caller has finished changing the
  // buffer.
  int Unlock();

  // Gets a blob buffer that was created with BufferProducer::CreateBlob.
  // Locking and Unlocking is handled internally. There's no need to Unlock
  // after calling this method.
  int GetBlobReadWritePointer(size_t size, void** addr);

  // Gets a blob buffer that was created with BufferProducer::CreateBlob.
  // Locking and Unlocking is handled internally. There's no need to Unlock
  // after calling this method.
  int GetBlobReadOnlyPointer(size_t size, void** addr);

  // Returns a dup'd file descriptor for accessing the blob shared memory. The
  // caller takes ownership of the file descriptor and must close it or pass on
  // ownership. Some GPU API extensions can take file descriptors to bind shared
  // memory gralloc buffers to GPU buffer objects.
  LocalHandle GetBlobFd() const {
    // Current GPU vendor puts the buffer allocation in one FD. If we change GPU
    // vendors and this is the wrong fd, late-latching and EDS will very clearly
    // stop working and we will need to correct this. The alternative is to use
    // a GL context in the pose service to allocate this buffer or to use the
    // ION API directly instead of gralloc.
    return LocalHandle(dup(native_handle()->data[0]));
  }

  // Get up to |max_fds_count| file descriptors for accessing the blob shared
  // memory. |fds_count| will contain the actual number of file descriptors.
  void GetBlobFds(int* fds, size_t* fds_count, size_t max_fds_count) const;

  using Client::event_fd;

  Status<int> GetEventMask(int events) {
    if (auto* client_channel = GetChannel()) {
      return client_channel->GetEventMask(events);
    } else {
      return pdx::ErrorStatus(EINVAL);
    }
  }

  std::vector<pdx::ClientChannel::EventSource> GetEventSources() const {
    if (auto* client_channel = GetChannel()) {
      return client_channel->GetEventSources();
    } else {
      return {};
    }
  }

  native_handle_t* native_handle() const {
    return const_cast<native_handle_t*>(buffer_.handle());
  }

  IonBuffer* buffer() { return &buffer_; }
  const IonBuffer* buffer() const { return &buffer_; }

  int id() const { return id_; }

  // A state mask which is unique to a buffer hub client among all its siblings
  // sharing the same concrete graphic buffer.
  uint64_t buffer_state_bit() const { return buffer_state_bit_; }

  // The following methods return settings of the first buffer. Currently,
  // it is only possible to create multi-buffer BufferHubBuffers with the same
  // settings.
  uint32_t width() const { return buffer_.width(); }
  uint32_t height() const { return buffer_.height(); }
  uint32_t stride() const { return buffer_.stride(); }
  uint32_t format() const { return buffer_.format(); }
  uint32_t usage() const { return buffer_.usage(); }
  uint32_t layer_count() const { return buffer_.layer_count(); }

  // TODO(b/37881101) Clean up producer/consumer usage.
  uint64_t producer_usage() const { return buffer_.usage(); }
  uint64_t consumer_usage() const { return buffer_.usage(); }

  uint64_t GetQueueIndex() const { return metadata_header_->queue_index; }
  void SetQueueIndex(uint64_t index) { metadata_header_->queue_index = index; }

 protected:
  explicit BufferHubBuffer(LocalChannelHandle channel);
  explicit BufferHubBuffer(const std::string& endpoint_path);
  virtual ~BufferHubBuffer();

  // Initialization helper.
  int ImportBuffer();

  // Check invalid metadata operation. Returns 0 if requested metadata is valid.
  int CheckMetadata(size_t user_metadata_size) const;

  // Send out the new fence by updating the shared fence (shared_release_fence
  // for producer and shared_acquire_fence for consumer). Note that during this
  // should only be used in LocalPost() or LocalRelease, and the shared fence
  // shouldn't be poll'ed by the other end.
  int UpdateSharedFence(const LocalHandle& new_fence,
                        const LocalHandle& shared_fence);

  // IonBuffer that is shared between bufferhubd, producer, and consumers.
  size_t metadata_buf_size_{0};
  size_t user_metadata_size_{0};
  BufferHubDefs::MetadataHeader* metadata_header_{nullptr};
  void* user_metadata_ptr_{nullptr};
  std::atomic<uint64_t>* buffer_state_{nullptr};
  std::atomic<uint64_t>* fence_state_{nullptr};

  LocalHandle shared_acquire_fence_;
  LocalHandle shared_release_fence_;

  // A local fence fd that holds the ownership of the fence fd on Post (for
  // producer) and Release (for consumer).
  LocalHandle pending_fence_fd_;

 private:
  BufferHubBuffer(const BufferHubBuffer&) = delete;
  void operator=(const BufferHubBuffer&) = delete;

  // Global id for the buffer that is consistent across processes. It is meant
  // for logging and debugging purposes only and should not be used for lookup
  // or any other functional purpose as a security precaution.
  int id_;
  uint64_t buffer_state_bit_{0ULL};
  IonBuffer buffer_;
  IonBuffer metadata_buffer_;
};

// This represents a writable buffer. Calling Post notifies all clients and
// makes the buffer read-only. Call Gain to acquire write access. A buffer
// may have many consumers.
//
// The user of BufferProducer is responsible with making sure that the Post() is
// done with the correct metadata type and size. The user is also responsible
// for making sure that remote ends (BufferConsumers) are also using the correct
// metadata when acquiring the buffer. The API guarantees that a Post() with a
// metadata of wrong size will fail. However, it currently does not do any
// type checking.
// The API also assumes that metadata is a serializable type (plain old data).
class BufferProducer : public pdx::ClientBase<BufferProducer, BufferHubBuffer> {
 public:
  // Imports a bufferhub producer channel, assuming ownership of its handle.
  static std::unique_ptr<BufferProducer> Import(LocalChannelHandle channel);
  static std::unique_ptr<BufferProducer> Import(
      Status<LocalChannelHandle> status);

  // Asynchronously posts a buffer. The fence and metadata are passed to
  // consumer via shared fd and shared memory.
  int PostAsync(const DvrNativeBufferMetadata* meta,
                const LocalHandle& ready_fence);

  // Post this buffer, passing |ready_fence| to the consumers. The bytes in
  // |meta| are passed unaltered to the consumers. The producer must not modify
  // the buffer until it is re-gained.
  // This returns zero or a negative unix error code.
  int Post(const LocalHandle& ready_fence, const void* meta,
           size_t user_metadata_size);

  template <typename Meta,
            typename = typename std::enable_if<std::is_void<Meta>::value>::type>
  int Post(const LocalHandle& ready_fence) {
    return Post(ready_fence, nullptr, 0);
  }
  template <typename Meta, typename = typename std::enable_if<
                               !std::is_void<Meta>::value>::type>
  int Post(const LocalHandle& ready_fence, const Meta& meta) {
    return Post(ready_fence, &meta, sizeof(meta));
  }

  // Attempt to re-gain the buffer for writing. If |release_fence| is valid, it
  // must be waited on before using the buffer. If it is not valid then the
  // buffer is free for immediate use. This call will only succeed if the buffer
  // is in the released state.
  // This returns zero or a negative unix error code.
  int Gain(LocalHandle* release_fence);
  int GainAsync();

  // Asynchronously marks a released buffer as gained. This method is similar to
  // the synchronous version above, except that it does not wait for BufferHub
  // to acknowledge success or failure. Because of the asynchronous nature of
  // the underlying message, no error is returned if this method is called when
  // the buffer is in an incorrect state. Returns zero if sending the message
  // succeeded, or a negative errno code if local error check fails.
  int GainAsync(DvrNativeBufferMetadata* out_meta, LocalHandle* out_fence);

  // Attaches the producer to |name| so that it becomes a persistent buffer that
  // may be retrieved by name at a later time. This may be used in cases where a
  // shared memory buffer should persist across the life of the producer process
  // (i.e. the buffer may be held by clients across a service restart). The
  // buffer may be associated with a user and/or group id to restrict access to
  // the buffer. If user_id or group_id is -1 then checks for the respective id
  // are disabled. If user_id or group_id is 0 then the respective id of the
  // calling process is used instead.
  int MakePersistent(const std::string& name, int user_id, int group_id);

  // Removes the persistence of the producer.
  int RemovePersistence();

 private:
  friend BASE;

  // Constructors are automatically exposed through BufferProducer::Create(...)
  // static template methods inherited from ClientBase, which take the same
  // arguments as the constructors.

  // Constructs a buffer with the given geometry and parameters.
  BufferProducer(uint32_t width, uint32_t height, uint32_t format,
                 uint32_t usage, size_t metadata_size = 0);
  BufferProducer(uint32_t width, uint32_t height, uint32_t format,
                 uint64_t producer_usage, uint64_t consumer_usage,
                 size_t metadata_size);

  // Constructs a persistent buffer with the given geometry and parameters and
  // binds it to |name| in one shot. If a persistent buffer with the same name
  // and settings already exists and matches the given geometry and parameters,
  // that buffer is connected to this client instead of creating a new buffer.
  // If the name matches but the geometry or settings do not match then
  // construction fails and BufferProducer::Create() returns nullptr.
  //
  // Access to the persistent buffer may be restricted by |user_id| and/or
  // |group_id|; these settings are established only when the buffer is first
  // created and cannot be changed. A user or group id of -1 disables checks for
  // that respective id. A user or group id of 0 is substituted with the
  // effective user or group id of the calling process.
  BufferProducer(const std::string& name, int user_id, int group_id,
                 uint32_t width, uint32_t height, uint32_t format,
                 uint32_t usage, size_t metadata_size = 0);
  BufferProducer(const std::string& name, int user_id, int group_id,
                 uint32_t width, uint32_t height, uint32_t format,
                 uint64_t producer_usage, uint64_t consumer_usage,
                 size_t user_metadata_size);

  // Constructs a blob (flat) buffer with the given usage flags.
  BufferProducer(uint32_t usage, size_t size);
  BufferProducer(uint64_t producer_usage, uint64_t consumer_usage, size_t size);

  // Constructs a persistent blob (flat) buffer and binds it to |name|.
  BufferProducer(const std::string& name, int user_id, int group_id,
                 uint32_t usage, size_t size);
  BufferProducer(const std::string& name, int user_id, int group_id,
                 uint64_t producer_usage, uint64_t consumer_usage, size_t size);

  // Constructs a channel to persistent buffer by name only. The buffer must
  // have been previously created or made persistent.
  explicit BufferProducer(const std::string& name);

  // Imports the given file handle to a producer channel, taking ownership.
  explicit BufferProducer(LocalChannelHandle channel);

  // Local state transition helpers.
  int LocalGain(DvrNativeBufferMetadata* out_meta, LocalHandle* out_fence);
  int LocalPost(const DvrNativeBufferMetadata* meta,
                const LocalHandle& ready_fence);
};

// This is a connection to a producer buffer, which can be located in another
// application. When that buffer is Post()ed, this fd will be signaled and
// Acquire allows read access. The user is responsible for making sure that
// Acquire is called with the correct metadata structure. The only guarantee the
// API currently provides is that an Acquire() with metadata of the wrong size
// will fail.
class BufferConsumer : public pdx::ClientBase<BufferConsumer, BufferHubBuffer> {
 public:
  // This call assumes ownership of |fd|.
  static std::unique_ptr<BufferConsumer> Import(LocalChannelHandle channel);
  static std::unique_ptr<BufferConsumer> Import(
      Status<LocalChannelHandle> status);

  // Attempt to retrieve a post event from buffer hub. If successful,
  // |ready_fence| will be set to a fence to wait on until the buffer is ready.
  // This call will only succeed after the fd is signalled. This call may be
  // performed as an alternative to the Acquire() with metadata. In such cases
  // the metadata is not read.
  //
  // This returns zero or negative unix error code.
  int Acquire(LocalHandle* ready_fence);

  // Attempt to retrieve a post event from buffer hub. If successful,
  // |ready_fence| is set to a fence signaling that the contents of the buffer
  // are available. This call will only succeed if the buffer is in the posted
  // state.
  // Returns zero on success, or a negative errno code otherwise.
  int Acquire(LocalHandle* ready_fence, void* meta, size_t user_metadata_size);

  // Attempt to retrieve a post event from buffer hub. If successful,
  // |ready_fence| is set to a fence to wait on until the buffer is ready. This
  // call will only succeed after the fd is signaled. This returns zero or a
  // negative unix error code.
  template <typename Meta>
  int Acquire(LocalHandle* ready_fence, Meta* meta) {
    return Acquire(ready_fence, meta, sizeof(*meta));
  }

  // Asynchronously acquires a bufer.
  int AcquireAsync(DvrNativeBufferMetadata* out_meta, LocalHandle* out_fence);

  // This should be called after a successful Acquire call. If the fence is
  // valid the fence determines the buffer usage, otherwise the buffer is
  // released immediately.
  // This returns zero or a negative unix error code.
  int Release(const LocalHandle& release_fence);
  int ReleaseAsync();

  // Asynchronously releases a buffer. Similar to the synchronous version above,
  // except that it does not wait for BufferHub to reply with success or error.
  // The fence and metadata are passed to consumer via shared fd and shared
  // memory.
  int ReleaseAsync(const DvrNativeBufferMetadata* meta,
                   const LocalHandle& release_fence);

  // May be called after or instead of Acquire to indicate that the consumer
  // does not need to access the buffer this cycle. This returns zero or a
  // negative unix error code.
  int Discard();

  // When set, this consumer is no longer notified when this buffer is
  // available. The system behaves as if Discard() is immediately called
  // whenever the buffer is posted. If ignore is set to true while a buffer is
  // pending, it will act as if Discard() was also called.
  // This returns zero or a negative unix error code.
  int SetIgnore(bool ignore);

 private:
  friend BASE;

  explicit BufferConsumer(LocalChannelHandle channel);

  // Local state transition helpers.
  int LocalAcquire(DvrNativeBufferMetadata* out_meta, LocalHandle* out_fence);
  int LocalRelease(const DvrNativeBufferMetadata* meta,
                   const LocalHandle& release_fence);
};

}  // namespace dvr
}  // namespace android

#endif  // ANDROID_DVR_BUFFER_HUB_CLIENT_H_
