#include "consumer_queue_channel.h"

#include <pdx/channel_handle.h>

#include "producer_channel.h"

using android::pdx::ErrorStatus;
using android::pdx::RemoteChannelHandle;
using android::pdx::Status;
using android::pdx::rpc::DispatchRemoteMethod;
using android::pdx::rpc::RemoteMethodError;

namespace android {
namespace dvr {

ConsumerQueueChannel::ConsumerQueueChannel(
    BufferHubService* service, int buffer_id, int channel_id,
    const std::shared_ptr<Channel>& producer, bool silent)
    : BufferHubChannel(service, buffer_id, channel_id, kConsumerQueueType),
      producer_(producer),
      capacity_(0),
      silent_(silent) {
  GetProducer()->AddConsumer(this);
}

ConsumerQueueChannel::~ConsumerQueueChannel() {
  ALOGD_IF(TRACE, "ConsumerQueueChannel::~ConsumerQueueChannel: channel_id=%d",
           channel_id());

  if (auto producer = GetProducer()) {
    producer->RemoveConsumer(this);
  }
}

bool ConsumerQueueChannel::HandleMessage(Message& message) {
  ATRACE_NAME("ConsumerQueueChannel::HandleMessage");
  auto producer = GetProducer();
  if (!producer) {
    RemoteMethodError(message, EPIPE);
    return true;
  }

  switch (message.GetOp()) {
    case BufferHubRPC::CreateConsumerQueue::Opcode:
      DispatchRemoteMethod<BufferHubRPC::CreateConsumerQueue>(
          *producer, &ProducerQueueChannel::OnCreateConsumerQueue, message);
      return true;

    case BufferHubRPC::GetQueueInfo::Opcode:
      DispatchRemoteMethod<BufferHubRPC::GetQueueInfo>(
          *producer, &ProducerQueueChannel::OnGetQueueInfo, message);
      return true;

    case BufferHubRPC::ConsumerQueueImportBuffers::Opcode:
      DispatchRemoteMethod<BufferHubRPC::ConsumerQueueImportBuffers>(
          *this, &ConsumerQueueChannel::OnConsumerQueueImportBuffers, message);
      return true;

    default:
      return false;
  }
}

std::shared_ptr<ProducerQueueChannel> ConsumerQueueChannel::GetProducer()
    const {
  return std::static_pointer_cast<ProducerQueueChannel>(producer_.lock());
}

void ConsumerQueueChannel::HandleImpulse(Message& /* message */) {
  ATRACE_NAME("ConsumerQueueChannel::HandleImpulse");
}

BufferHubChannel::BufferInfo ConsumerQueueChannel::GetBufferInfo() const {
  BufferHubChannel::BufferInfo info;
  if (auto producer = GetProducer()) {
    // If producer has not hung up, copy most buffer info from the producer.
    info = producer->GetBufferInfo();
  }
  info.id = buffer_id();
  info.capacity = capacity_;
  return info;
}

void ConsumerQueueChannel::RegisterNewBuffer(
    const std::shared_ptr<ProducerChannel>& producer_channel, size_t slot) {
  ALOGD_IF(TRACE,
           "ConsumerQueueChannel::RegisterNewBuffer: queue_id=%d buffer_id=%d "
           "slot=%zu silent=%d",
           buffer_id(), producer_channel->buffer_id(), slot, silent_);
  // Only register buffers if the queue is not silent.
  if (!silent_) {
    pending_buffer_slots_.emplace(producer_channel, slot);

    // Signal the client that there is new buffer available.
    SignalAvailable();
  }
}

Status<std::vector<std::pair<RemoteChannelHandle, size_t>>>
ConsumerQueueChannel::OnConsumerQueueImportBuffers(Message& message) {
  std::vector<std::pair<RemoteChannelHandle, size_t>> buffer_handles;
  ATRACE_NAME("ConsumerQueueChannel::OnConsumerQueueImportBuffers");
  ALOGD_IF(TRACE,
           "ConsumerQueueChannel::OnConsumerQueueImportBuffers: "
           "pending_buffer_slots=%zu",
           pending_buffer_slots_.size());

  // Indicate this is a silent queue that will not import buffers.
  if (silent_)
    return ErrorStatus(EBADR);

  while (!pending_buffer_slots_.empty()) {
    auto producer_channel = pending_buffer_slots_.front().first.lock();
    size_t producer_slot = pending_buffer_slots_.front().second;
    pending_buffer_slots_.pop();

    // It's possible that the producer channel has expired. When this occurs,
    // ignore the producer channel.
    if (producer_channel == nullptr) {
      ALOGW(
          "ConsumerQueueChannel::OnConsumerQueueImportBuffers: producer "
          "channel has already been expired.");
      continue;
    }

    auto status = producer_channel->CreateConsumer(message);

    // If no buffers are imported successfully, clear available and return an
    // error. Otherwise, return all consumer handles already imported
    // successfully, but keep available bits on, so that the client can retry
    // importing remaining consumer buffers.
    if (!status) {
      ALOGE(
          "ConsumerQueueChannel::OnConsumerQueueImportBuffers: Failed create "
          "consumer: %s",
          status.GetErrorMessage().c_str());
      if (buffer_handles.empty()) {
        ClearAvailable();
        return status.error_status();
      } else {
        return {std::move(buffer_handles)};
      }
    }

    buffer_handles.emplace_back(status.take(), producer_slot);
  }

  ClearAvailable();
  return {std::move(buffer_handles)};
}

void ConsumerQueueChannel::OnProducerClosed() {
  ALOGD_IF(TRACE, "ConsumerQueueChannel::OnProducerClosed: queue_id=%d",
           buffer_id());
  producer_.reset();
  Hangup();
}

}  // namespace dvr
}  // namespace android
