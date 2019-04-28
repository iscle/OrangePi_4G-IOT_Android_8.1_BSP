#ifndef ANDROID_DVR_SENSOR_CLIENT_H_
#define ANDROID_DVR_SENSOR_CLIENT_H_

#include <hardware/sensors.h>
#include <pdx/client.h>
#include <poll.h>

namespace android {
namespace dvr {

// SensorClient is a remote interface to the sensor service in sensord.
class SensorClient : public pdx::ClientBase<SensorClient> {
 public:
  ~SensorClient();

  int StartSensor();
  int StopSensor();
  int Poll(sensors_event_t* events, int max_count);

 private:
  friend BASE;

  // Set up a channel associated with the sensor of the indicated type.
  // NOTE(segal): If our hardware ends up with multiple sensors of the same
  // type, we'll have to change this.
  explicit SensorClient(int sensor_type);

  int sensor_type_;

  SensorClient(const SensorClient&);
  SensorClient& operator=(const SensorClient&);
};

}  // namespace dvr
}  // namespace android

#endif  // ANDROID_DVR_SENSOR_CLIENT_H_
