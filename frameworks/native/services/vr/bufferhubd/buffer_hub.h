#ifndef ANDROID_DVR_BUFFERHUBD_BUFFER_HUB_H_
#define ANDROID_DVR_BUFFERHUBD_BUFFER_HUB_H_

#include <memory>
#include <string>
#include <unordered_map>

#include <hardware/gralloc.h>
#include <pdx/service.h>
#include <private/dvr/bufferhub_rpc.h>

namespace android {
namespace dvr {

class BufferHubService;
class ConsumerChannel;
class ProducerChannel;
class ConsumerQueueChannel;
class ProducerQueueChannel;

class BufferHubChannel : public pdx::Channel {
 public:
  enum ChannelType {
    kProducerType,
    kConsumerType,
    kProducerQueueType,
    kConsumerQueueType,
  };

  enum : int { kDetachedId = -1 };

  BufferHubChannel(BufferHubService* service, int buffer_id, int channel_id,
                   ChannelType channel_type)
      : service_(service),
        buffer_id_(buffer_id),
        channel_id_(channel_id),
        channel_type_(channel_type) {}
  virtual ~BufferHubChannel() {}

  virtual bool HandleMessage(pdx::Message& message) = 0;
  virtual void HandleImpulse(pdx::Message& message) = 0;

  // Captures buffer info for use by BufferHubService::DumpState().
  struct BufferInfo {
    // Common data field shared by BufferProducer and ProducerQueue.
    int id = -1;
    int type = -1;
    size_t consumer_count = 0;

    // Data field for buffer producer.
    uint32_t width = 0;
    uint32_t height = 0;
    uint32_t layer_count = 0;
    uint32_t format = 0;
    uint64_t usage = 0;
    size_t pending_count = 0;
    uint64_t state = 0;
    uint64_t signaled_mask = 0;
    uint64_t index = 0;
    std::string name;

    // Data filed for producer queue.
    size_t capacity = 0;
    UsagePolicy usage_policy{0, 0, 0, 0};

    BufferInfo(int id, size_t consumer_count, uint32_t width, uint32_t height,
               uint32_t layer_count, uint32_t format, uint64_t usage,
               size_t pending_count, uint64_t state, uint64_t signaled_mask,
               uint64_t index, const std::string& name)
        : id(id),
          type(kProducerType),
          consumer_count(consumer_count),
          width(width),
          height(height),
          layer_count(layer_count),
          format(format),
          usage(usage),
          pending_count(pending_count),
          state(state),
          signaled_mask(signaled_mask),
          index(index),
          name(name) {}

    BufferInfo(int id, size_t consumer_count, size_t capacity,
               const UsagePolicy& usage_policy)
        : id(id),
          type(kProducerQueueType),
          consumer_count(consumer_count),
          capacity(capacity),
          usage_policy(usage_policy) {}

    BufferInfo() {}
  };

  // Returns the buffer info for this buffer.
  virtual BufferInfo GetBufferInfo() const = 0;

  // Signal the client fd that an ownership change occurred using POLLIN.
  void SignalAvailable();

  // Clear the ownership change event.
  void ClearAvailable();

  // Signal hangup event.
  void Hangup();

  BufferHubService* service() const { return service_; }
  ChannelType channel_type() const { return channel_type_; }
  int buffer_id() const { return buffer_id_; }

  int channel_id() const { return channel_id_; }
  bool IsDetached() const { return channel_id_ == kDetachedId; }

  bool signaled() const { return signaled_; }

  void Detach() {
    if (channel_type_ == kProducerType)
      channel_id_ = kDetachedId;
  }
  void Attach(int channel_id) {
    if (channel_type_ == kProducerType && channel_id_ == kDetachedId)
      channel_id_ = channel_id;
  }

 private:
  BufferHubService* service_;

  // Static id of the buffer for logging and informational purposes. This id
  // does not change for the life of the buffer.
  // TODO(eieio): Consider using an id allocator instead of the originating
  // channel id; channel ids wrap after 2^31 ids, but this is not a problem in
  // general because channel ids are not used for any lookup in this service.
  int buffer_id_;

  // The channel id of the buffer. This may change for a persistent producer
  // buffer if it is detached and re-attached to another channel.
  int channel_id_;

  bool signaled_;

  ChannelType channel_type_;

  BufferHubChannel(const BufferHubChannel&) = delete;
  void operator=(const BufferHubChannel&) = delete;
};

class BufferHubService : public pdx::ServiceBase<BufferHubService> {
 public:
  BufferHubService();
  ~BufferHubService() override;

  pdx::Status<void> HandleMessage(pdx::Message& message) override;
  void HandleImpulse(pdx::Message& message) override;

  void OnChannelClose(pdx::Message& message,
                      const std::shared_ptr<pdx::Channel>& channel) override;

  bool IsInitialized() const override;
  std::string DumpState(size_t max_length) override;

  bool AddNamedBuffer(const std::string& name,
                      const std::shared_ptr<ProducerChannel>& buffer);
  std::shared_ptr<ProducerChannel> GetNamedBuffer(const std::string& name);
  bool RemoveNamedBuffer(const ProducerChannel& buffer);

 private:
  friend BASE;

  std::unordered_map<std::string, std::shared_ptr<ProducerChannel>>
      named_buffers_;

  pdx::Status<void> OnCreateBuffer(pdx::Message& message, uint32_t width,
                                   uint32_t height, uint32_t format,
                                   uint64_t usage, size_t meta_size_bytes);
  pdx::Status<void> OnCreatePersistentBuffer(pdx::Message& message,
                                             const std::string& name,
                                             int user_id, int group_id,
                                             uint32_t width, uint32_t height,
                                             uint32_t format, uint64_t usage,
                                             size_t meta_size_bytes);
  pdx::Status<void> OnGetPersistentBuffer(pdx::Message& message,
                                          const std::string& name);
  pdx::Status<QueueInfo> OnCreateProducerQueue(
      pdx::Message& message, const ProducerQueueConfig& producer_config,
      const UsagePolicy& usage_policy);

  BufferHubService(const BufferHubService&) = delete;
  void operator=(const BufferHubService&) = delete;
};

}  // namespace dvr
}  // namespace android

#endif  // ANDROID_DVR_BUFFERHUBD_BUFFER_HUB_H_
