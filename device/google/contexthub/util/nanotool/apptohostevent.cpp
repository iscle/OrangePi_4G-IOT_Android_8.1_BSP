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

#include "apptohostevent.h"

#include "contexthub.h"
#include "log.h"

namespace android {

/* AppToHostEvent *************************************************************/

std::unique_ptr<AppToHostEvent> AppToHostEvent::FromBytes(
        const std::vector<uint8_t>& buffer) {
    auto event = std::unique_ptr<AppToHostEvent>(new AppToHostEvent());
    event->Populate(buffer);
    if (!event->IsValid()) {
        return nullptr;
    }

    return event;
}

uint64_t AppToHostEvent::GetAppId() const {
    return GetTypedData()->appId;
}

uint8_t AppToHostEvent::GetDataLen() const {
    return GetTypedData()->dataLen;
}

const uint8_t *AppToHostEvent::GetDataPtr() const {
    return (reinterpret_cast<const uint8_t*>(GetTypedData())
              + sizeof(struct HostHubRawPacket));
}

bool AppToHostEvent::CheckAppId(SensorType sensor_type) const {
    // Make sure the app ID matches what we expect for the sensor type, bail out
    // early if it doesn't
    switch (sensor_type) {
      case SensorType::Accel:
      case SensorType::Gyro:
        if (GetAppId() != kAppIdBoschBmi160Bmm150 && GetAppId() != kAppIdSTMicroLsm6dsm) {
            return false;
        }
        break;

      case SensorType::Magnetometer:
        if (GetAppId() != kAppIdSTMicroLsm6dsm && GetAppId() != kAppIdSTMicroMag40) {
            return false;
        }
        break;

      case SensorType::Proximity:
        if (GetAppId() != kAppIdAmsTmd2772 && GetAppId() != kAppIdRohmRpr0521 &&
            GetAppId() != kAppIdAmsTmd4903) {
            return false;
        }
        break;

      case SensorType::Barometer:
        if (GetAppId() != kAppIdBoschBmp280 && GetAppId() != kAppIdSTMicroLps22hb) {
            return false;
        }
        break;

      case SensorType::AmbientLightSensor:
        if (GetAppId() != kAppIdAmsTmd4903) {
            return false;
        }
        break;

      default:
        return false;
    }

    return true;
}

bool AppToHostEvent::CheckEventHeader(SensorType sensor_type) const {
    if (GetDataLen() < sizeof(struct SensorAppEventHeader)) {
        return false;
    }

    if (!CheckAppId(sensor_type)) {
        return false;
    }

    return true;
}

bool AppToHostEvent::IsCalibrationEventForSensor(SensorType sensor_type) const {
    if (!CheckEventHeader(sensor_type)) {
        return false;
    }

    // If we made it this far, we only need to confirm the message ID
    auto header = reinterpret_cast<const struct SensorAppEventHeader *>(
        GetDataPtr());
    return (header->msgId == SENSOR_APP_MSG_CALIBRATION_RESULT);
}

bool AppToHostEvent::IsTestEventForSensor(SensorType sensor_type) const {
    if (!CheckEventHeader(sensor_type)) {
        return false;
    }

    // If we made it this far, we only need to confirm the message ID
    auto header = reinterpret_cast<const struct SensorAppEventHeader *>(
        GetDataPtr());
    return (header->msgId == SENSOR_APP_MSG_TEST_RESULT);
}

bool AppToHostEvent::IsValid() const {
    const HostHubRawPacket *packet = GetTypedData();
    if (!packet) {
        return false;
    }

    // dataLen should specify the amount of data that follows the event type
    // and HostHubRawPacket headers
    if (event_data.size() < (sizeof(uint32_t) + sizeof(struct HostHubRawPacket)
                               + packet->dataLen)) {
        LOGW("Invalid/short AppToHost event of size %zu", event_data.size());
        return false;
    }

    return true;
}

const HostHubRawPacket *AppToHostEvent::GetTypedData() const {
    // After the event type header (uint32_t), we should have struct
    // HostHubRawPacket
    if (event_data.size() < sizeof(uint32_t) + sizeof(struct HostHubRawPacket)) {
        LOGW("Invalid/short AppToHost event of size %zu", event_data.size());
        return nullptr;
    }
    return reinterpret_cast<const HostHubRawPacket *>(
        event_data.data() + sizeof(uint32_t));
}

}  // namespace android
