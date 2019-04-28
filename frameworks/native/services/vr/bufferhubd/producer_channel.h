#ifndef ANDROID_DVR_BUFFERHUBD_PRODUCER_CHANNEL_H_
#define ANDROID_DVR_BUFFERHUBD_PRODUCER_CHANNEL_H_

#include "buffer_hub.h"

#include <functional>
#include <memory>
#include <vector>

#include <pdx/channel_handle.h>
#include <pdx/file_handle.h>
#include <pdx/rpc/buffer_wrapper.h>
#include <private/dvr/bufferhub_rpc.h>
#include <private/dvr/ion_buffer.h>

namespace android {
namespace dvr {

// The buffer changes ownership according to the following sequence:
// POST -> ACQUIRE/RELEASE (all consumers) -> GAIN (producer acquires) -> POST

// The producer channel is owned by a single app that writes into buffers and
// calls POST when drawing is complete. This channel has a set of consumer
// channels associated with it that are waiting for notifications.
class ProducerChannel : public BufferHubChannel {
 public:
  using Message = pdx::Message;
  using BorrowedHandle = pdx::BorrowedHandle;
  using RemoteChannelHandle = pdx::RemoteChannelHandle;
  template <typename T>
  using BufferWrapper = pdx::rpc::BufferWrapper<T>;

  static pdx::Status<std::shared_ptr<ProducerChannel>> Create(
      BufferHubService* service, int channel_id, uint32_t width,
      uint32_t height, uint32_t layer_count, uint32_t format, uint64_t usage,
      size_t user_metadata_size);

  ~ProducerChannel() override;

  bool HandleMessage(Message& message) override;
  void HandleImpulse(Message& message) override;

  BufferInfo GetBufferInfo() const override;

  BufferDescription<BorrowedHandle> GetBuffer(uint64_t buffer_state_bit);

  pdx::Status<RemoteChannelHandle> CreateConsumer(Message& message);
  pdx::Status<RemoteChannelHandle> OnNewConsumer(Message& message);

  pdx::Status<LocalFence> OnConsumerAcquire(Message& message);
  pdx::Status<void> OnConsumerRelease(Message& message,
                                      LocalFence release_fence);

  void OnConsumerIgnored();
  void OnConsumerOrphaned(ConsumerChannel* channel);

  void AddConsumer(ConsumerChannel* channel);
  void RemoveConsumer(ConsumerChannel* channel);

  bool CheckAccess(int euid, int egid);
  bool CheckParameters(uint32_t width, uint32_t height, uint32_t layer_count,
                       uint32_t format, uint64_t usage,
                       size_t user_metadata_size);

  pdx::Status<void> OnProducerMakePersistent(Message& message,
                                             const std::string& name,
                                             int user_id, int group_id);
  pdx::Status<void> OnRemovePersistence(Message& message);

 private:
  std::vector<ConsumerChannel*> consumer_channels_;
  // This counts the number of consumers left to process this buffer. If this is
  // zero then the producer can re-acquire ownership.
  int pending_consumers_;

  IonBuffer buffer_;

  // IonBuffer that is shared between bufferhubd, producer, and consumers.
  IonBuffer metadata_buffer_;
  BufferHubDefs::MetadataHeader* metadata_header_ = nullptr;
  std::atomic<uint64_t>* buffer_state_ = nullptr;
  std::atomic<uint64_t>* fence_state_ = nullptr;

  // All active consumer bits. Valid bits are the lower 63 bits, while the
  // highest bit is reserved for the producer and should not be set.
  uint64_t active_consumer_bit_mask_{0ULL};
  // All orphaned consumer bits. Valid bits are the lower 63 bits, while the
  // highest bit is reserved for the producer and should not be set.
  uint64_t orphaned_consumer_bit_mask_{0ULL};

  bool producer_owns_;
  LocalFence post_fence_;
  LocalFence returned_fence_;
  size_t user_metadata_size_;  // size of user requested buffer buffer size.
  size_t metadata_buf_size_;  // size of the ion buffer that holds metadata.

  pdx::LocalHandle acquire_fence_fd_;
  pdx::LocalHandle release_fence_fd_;
  pdx::LocalHandle dummy_fence_fd_;

  static constexpr int kNoCheckId = -1;
  static constexpr int kUseCallerId = 0;
  static constexpr int kRootId = 0;

  // User and group id to check when obtaining a persistent buffer.
  int owner_user_id_ = kNoCheckId;
  int owner_group_id_ = kNoCheckId;

  std::string name_;

  ProducerChannel(BufferHubService* service, int channel, uint32_t width,
                  uint32_t height, uint32_t layer_count, uint32_t format,
                  uint64_t usage, size_t user_metadata_size, int* error);

  pdx::Status<BufferDescription<BorrowedHandle>> OnGetBuffer(Message& message);
  pdx::Status<void> OnProducerPost(Message& message, LocalFence acquire_fence);
  pdx::Status<LocalFence> OnProducerGain(Message& message);

  ProducerChannel(const ProducerChannel&) = delete;
  void operator=(const ProducerChannel&) = delete;
};

}  // namespace dvr
}  // namespace android

#endif  // ANDROID_DVR_BUFFERHUBD_PRODUCER_CHANNEL_H_
