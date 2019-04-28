#ifndef ANDROID_DVR_BUFFERHUBD_CONSUMER_CHANNEL_H_
#define ANDROID_DVR_BUFFERHUBD_CONSUMER_CHANNEL_H_

#include "buffer_hub.h"

#include <pdx/rpc/buffer_wrapper.h>
#include <private/dvr/bufferhub_rpc.h>

namespace android {
namespace dvr {

// Consumer channels are attached to a Producer channel
class ConsumerChannel : public BufferHubChannel {
 public:
  using BorrowedHandle = pdx::BorrowedHandle;
  using Channel = pdx::Channel;
  using Message = pdx::Message;

  ConsumerChannel(BufferHubService* service, int buffer_id, int channel_id,
                  uint64_t consumer_state_bit,
                  const std::shared_ptr<Channel> producer);
  ~ConsumerChannel() override;

  bool HandleMessage(Message& message) override;
  void HandleImpulse(Message& message) override;

  uint64_t consumer_state_bit() const { return consumer_state_bit_; }
  BufferInfo GetBufferInfo() const override;

  bool OnProducerPosted();
  void OnProducerClosed();

 private:
  std::shared_ptr<ProducerChannel> GetProducer() const;

  pdx::Status<BufferDescription<BorrowedHandle>> OnGetBuffer(Message& message);

  pdx::Status<LocalFence> OnConsumerAcquire(Message& message);
  pdx::Status<void> OnConsumerRelease(Message& message,
                                      LocalFence release_fence);
  pdx::Status<void> OnConsumerSetIgnore(Message& message, bool ignore);

  uint64_t consumer_state_bit_{0};
  bool acquired_{false};
  bool released_{true};
  bool ignored_{false};  // True if we are ignoring events.
  std::weak_ptr<Channel> producer_;

  ConsumerChannel(const ConsumerChannel&) = delete;
  void operator=(const ConsumerChannel&) = delete;
};

}  // namespace dvr
}  // namespace android

#endif  // ANDROID_DVR_BUFFERHUBD_CONSUMER_CHANNEL_H_
