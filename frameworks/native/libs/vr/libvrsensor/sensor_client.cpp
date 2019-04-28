#define LOG_TAG "SensorClient"
#include <private/dvr/sensor_client.h>

#include <log/log.h>
#include <poll.h>

#include <pdx/default_transport/client_channel_factory.h>
#include <private/dvr/sensor-ipc.h>

using android::pdx::Transaction;

namespace android {
namespace dvr {

SensorClient::SensorClient(int sensor_type)
    : BASE(pdx::default_transport::ClientChannelFactory::Create(
          DVR_SENSOR_SERVICE_CLIENT)),
      sensor_type_(sensor_type) {}

SensorClient::~SensorClient() {}

int SensorClient::StartSensor() {
  Transaction trans{*this};
  auto status = trans.Send<int>(DVR_SENSOR_START, &sensor_type_,
                                sizeof(sensor_type_), nullptr, 0);
  ALOGE_IF(!status, "startSensor() failed because: %s\n",
           status.GetErrorMessage().c_str());
  return ReturnStatusOrError(status);
}

int SensorClient::StopSensor() {
  Transaction trans{*this};
  auto status = trans.Send<int>(DVR_SENSOR_STOP);
  ALOGE_IF(!status, "stopSensor() failed because: %s\n",
           status.GetErrorMessage().c_str());
  return ReturnStatusOrError(status);
}

int SensorClient::Poll(sensors_event_t* events, int max_events) {
  int num_events = 0;
  struct iovec rvec[] = {
      {.iov_base = &num_events, .iov_len = sizeof(int)},
      {.iov_base = events, .iov_len = max_events * sizeof(sensors_event_t)},
  };
  Transaction trans{*this};
  auto status = trans.SendVector<int>(DVR_SENSOR_POLL, nullptr, rvec);
  ALOGE_IF(!status, "Sensor poll() failed because: %s\n",
           status.GetErrorMessage().c_str());
  return !status ? -status.error() : num_events;
}

}  // namespace dvr
}  // namespace android

// Entrypoints to simplify using the library when programmatically dynamicly
// loading it.
// Allows us to call this library without linking it, as, for instance,
// when compiling GVR in Google3.
// NOTE(segal): It's kind of a hack.

extern "C" uint64_t dvrStartSensor(int type) {
  android::dvr::SensorClient* service =
      android::dvr::SensorClient::Create(type).release();
  service->StartSensor();
  return (uint64_t)service;
}

extern "C" void dvrStopSensor(uint64_t service) {
  android::dvr::SensorClient* iss =
      reinterpret_cast<android::dvr::SensorClient*>(service);
  iss->StopSensor();
  delete iss;
}

extern "C" int dvrPollSensor(uint64_t service, int max_count,
                             sensors_event_t* events) {
  return reinterpret_cast<android::dvr::SensorClient*>(service)->Poll(
      events, max_count);
}
