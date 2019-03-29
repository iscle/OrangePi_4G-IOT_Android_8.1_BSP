/*
 * Copyright (C) 2016 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

#ifndef ANDROIDCONTEXTHUB_H_
#define ANDROIDCONTEXTHUB_H_

#include "contexthub.h"

#include <poll.h>
#include <unistd.h>

namespace android {

/*
 * Communicates with a context hub via the /dev/nanohub interface
 */
class AndroidContextHub : public ContextHub {
  public:
    ~AndroidContextHub();

    // Performs system resource cleanup in the event that the program is
    // terminated abnormally (via std::terminate)
    static void TerminateHandler();

    bool Initialize() override;
    bool LoadCalibration() override;
    void SetLoggingEnabled(bool logging_enabled) override;

  protected:
    ContextHub::TransportResult WriteEvent(
        const std::vector<uint8_t>& request) override;
    ContextHub::TransportResult ReadEvent(std::vector<uint8_t>& response,
        int timeout_ms) override;
    bool FlashSensorHub(const std::vector<uint8_t>& bytes) override;

    bool SetCalibration(SensorType sensor_type, int32_t data) override;
    bool SetCalibration(SensorType sensor_type, float data) override;
    bool SetCalibration(SensorType sensor_type, int32_t x, int32_t y, int32_t z)
        override;
    bool SetCalibration(SensorType sensor_type, int32_t x, int32_t y,
        int32_t z, int32_t w) override;
    bool SaveCalibration() override;

  private:
    int sensor_fd_ = -1;
    int comms_fd_ = -1;

    ContextHub::TransportResult ReadEventFromFd(int fd,
        std::vector<uint8_t>& message);
    int ResetPollFds(struct pollfd *pfds, size_t count);
    static const char *SensorTypeToCalibrationKey(SensorType sensor_type);
};

}  // namespace android

#endif  // ANDROIDCONTEXTHUB_H_
