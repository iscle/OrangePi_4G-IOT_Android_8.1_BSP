#include "include/private/dvr/vsync_client.h"

#include <log/log.h>

#include <pdx/default_transport/client_channel_factory.h>
#include <private/dvr/display_protocol.h>

using android::dvr::display::VSyncProtocol;
using android::pdx::Transaction;

namespace android {
namespace dvr {

VSyncClient::VSyncClient(long timeout_ms)
    : BASE(pdx::default_transport::ClientChannelFactory::Create(
               VSyncProtocol::kClientPath),
           timeout_ms) {}

VSyncClient::VSyncClient()
    : BASE(pdx::default_transport::ClientChannelFactory::Create(
          VSyncProtocol::kClientPath)) {}

int VSyncClient::Wait(int64_t* timestamp_ns) {
  auto status = InvokeRemoteMethod<VSyncProtocol::Wait>();
  if (!status) {
    ALOGE("VSyncClient::Wait: Failed to wait for vsync: %s",
          status.GetErrorMessage().c_str());
    return -status.error();
  }

  if (timestamp_ns != nullptr) {
    *timestamp_ns = status.get();
  }
  return 0;
}

int VSyncClient::GetFd() { return event_fd(); }

int VSyncClient::GetLastTimestamp(int64_t* timestamp_ns) {
  auto status = InvokeRemoteMethod<VSyncProtocol::GetLastTimestamp>();
  if (!status) {
    ALOGE("VSyncClient::GetLastTimestamp: Failed to get vsync timestamp: %s",
          status.GetErrorMessage().c_str());
    return -status.error();
  }
  *timestamp_ns = status.get();
  return 0;
}

int VSyncClient::GetSchedInfo(int64_t* vsync_period_ns, int64_t* timestamp_ns,
                              uint32_t* next_vsync_count) {
  if (!vsync_period_ns || !timestamp_ns || !next_vsync_count)
    return -EINVAL;

  auto status = InvokeRemoteMethod<VSyncProtocol::GetSchedInfo>();
  if (!status) {
    ALOGE("VSyncClient::GetSchedInfo:: Failed to get warp timestamp: %s",
          status.GetErrorMessage().c_str());
    return -status.error();
  }

  *vsync_period_ns = status.get().vsync_period_ns;
  *timestamp_ns = status.get().timestamp_ns;
  *next_vsync_count = status.get().next_vsync_count;
  return 0;
}

int VSyncClient::Acknowledge() {
  auto status = InvokeRemoteMethod<VSyncProtocol::Acknowledge>();
  ALOGE_IF(!status, "VSuncClient::Acknowledge: Failed to ack vsync because: %s",
           status.GetErrorMessage().c_str());
  return ReturnStatusOrError(status);
}

}  // namespace dvr
}  // namespace android
