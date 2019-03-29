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

#include "nanomessage.h"

#include <inttypes.h>
#include <stdio.h>

#include "apptohostevent.h"
#include "log.h"
#include "logevent.h"
#include "resetreasonevent.h"
#include "sensorevent.h"

namespace android {

/* HardwareVersionInfo ********************************************************/

bool HardwareVersionInfo::Populate(const std::vector<uint8_t>& buffer) {
    if (buffer.size() != sizeof(VersionInfo)) {
        return false;
    }

    const uint8_t *data = buffer.data();
    const VersionInfo *source = reinterpret_cast<const VersionInfo *>(data);
    info = *source;
    return true;
}

std::string HardwareVersionInfo::ToString() const {
    const char format_string[] = "Hardware version info:\n"
        "    Hardware type: %04x\n"
        "    Hardware version: %04x\n"
        "    Bootloader version: %04x\n"
        "    Operating system version: %04x\n"
        "    Variant version: %08x\n";

    char buffer[1024];
    snprintf(buffer, sizeof(buffer), format_string,
        info.hardware_type,
        info.hardware_version,
        info.bootloader_version,
        info.operating_system_version,
        info.variant_version);
    return std::string(buffer);
}

/* WriteEventResponse *********************************************************/

std::string WriteEventResponse::ToString() const {
    const char format_string[] = "Write event accepted: %s\n";

    char buffer[128];
    snprintf(buffer, sizeof(buffer), format_string,
        response.accepted ? "true" : "false");
    return std::string(buffer);
}

bool WriteEventResponse::Populate(const std::vector<uint8_t>& buffer) {
    if (buffer.size() != sizeof(Response)) {
        return false;
    }

    const uint8_t *data = buffer.data();
    const Response *source = reinterpret_cast<const Response *>(data);
    response = *source;
    return true;

}

/* ReadEventRequest ***********************************************************/

std::vector<uint8_t> ReadEventRequest::GetBytes() const {
    std::vector<uint8_t> buffer(sizeof(Request));

    uint8_t *data = buffer.data();
    Request *req = reinterpret_cast<Request *>(data);
    *req = request;
    return buffer;
}

std::string ReadEventRequest::ToString() const {
    const char format_string[] = "Read event at time: %" PRIx64 "\n";

    char buffer[128];
    snprintf(buffer, sizeof(buffer), format_string,
        request.boot_time);
    return std::string(buffer);
}

/* ReadEventResponse **********************************************************/

std::string ReadEventResponse::ToString() const {
    char buffer[32];
    snprintf(buffer, sizeof(buffer), "ReadEventResponse %u\n", GetEventType());
    return std::string(buffer);
}

std::unique_ptr<ReadEventResponse> ReadEventResponse::FromBytes(
        const std::vector<uint8_t>& buffer) {
    // The first 4 bytes of any event must be the event type - use it to figure
    // out which class to construct
    uint32_t event_type = ReadEventResponse::EventTypeFromBuffer(buffer);
    if (ReadEventResponse::IsSensorEvent(event_type)) {
        return SensorEvent::FromBytes(buffer);
    } else if (ReadEventResponse::IsAppToHostEvent(event_type)) {
        return AppToHostEvent::FromBytes(buffer);
    } else if (ReadEventResponse::IsResetReasonEvent(event_type)) {
        return ResetReasonEvent::FromBytes(buffer);
    } else if (ReadEventResponse::IsLogEvent(event_type)) {
        return LogEvent::FromBytes(buffer);
    } else {
        LOGW("Received unexpected/unsupported event type %u", event_type);
        return nullptr;
    }
}

bool ReadEventResponse::Populate(const std::vector<uint8_t>& buffer) {
    if (buffer.size() < sizeof(Event)) {
        return false;
    }

    event_data.resize(buffer.size());
    std::copy(buffer.begin(), buffer.end(), event_data.begin());
    return true;
}

bool ReadEventResponse::IsAppToHostEvent() const {
    return ReadEventResponse::IsAppToHostEvent(GetEventType());
}

bool ReadEventResponse::IsSensorEvent() const {
    return ReadEventResponse::IsSensorEvent(GetEventType());
}

bool ReadEventResponse::IsResetReasonEvent() const {
    return ReadEventResponse::IsResetReasonEvent(GetEventType());
}

bool ReadEventResponse::IsLogEvent() const {
    return ReadEventResponse::IsLogEvent(GetEventType());
}

uint32_t ReadEventResponse::GetEventType() const {
    return ReadEventResponse::EventTypeFromBuffer(event_data);
}

bool ReadEventResponse::IsSensorEvent(uint32_t event_type) {
    return (event_type >= static_cast<uint32_t>(EventType::FirstSensorEvent) &&
            event_type <= static_cast<uint32_t>(EventType::LastSensorEvent));
}

bool ReadEventResponse::IsAppToHostEvent(uint32_t event_type) {
    return (event_type == static_cast<uint32_t>(EventType::AppToHostEvent));
}

bool ReadEventResponse::IsResetReasonEvent(uint32_t event_type) {
    return (event_type == static_cast<uint32_t>(EventType::ResetReasonEvent));
}

bool ReadEventResponse::IsLogEvent(uint32_t event_type) {
    return (event_type == static_cast<uint32_t>(EventType::LogEvent));
}

uint32_t ReadEventResponse::EventTypeFromBuffer(const std::vector<uint8_t>& buffer) {
    if (buffer.size() < sizeof(uint32_t)) {
        LOGW("Invalid/short event of size %zu", buffer.size());
        return 0;
    }
    return *reinterpret_cast<const uint32_t *>(buffer.data());
}

/* ConfigureSensorRequest *****************************************************/

ConfigureSensorRequest::ConfigureSensorRequest() {
    config.event_type = static_cast<uint32_t>(EventType::ConfigureSensor);
}

uint32_t ConfigureSensorRequest::FloatRateToFixedPoint(float rate) {
    return rate * 1024.0f;
}

float ConfigureSensorRequest::FixedPointRateToFloat(uint32_t rate) {
    return rate / 1024.0f;
}

// TODO(aarossig): Consider writing a template function for this.
std::vector<uint8_t> ConfigureSensorRequest::GetBytes() const {
    std::vector<uint8_t> buffer(sizeof(Configuration));

    uint8_t *data = buffer.data();
    Configuration *configuration = reinterpret_cast<Configuration *>(data);
    *configuration = config;
    buffer.insert(buffer.end(), extra_data_.begin(), extra_data_.end());

    return buffer;
}

void ConfigureSensorRequest::SetAdditionalData(const std::vector<uint8_t>& data) {
    extra_data_ = data;
}

std::string ConfigureSensorRequest::ToString() const {
    const char format_string[] = "Sensor configuration:\n"
        "    latency: %" PRIx64 "\n"
        "    rate (fixed point): %08x\n"
        "    sensor_type: %02x\n"
        "    command: %02x\n"
        "    flags: %04x\n";

    char buffer[1024];
    snprintf(buffer, sizeof(buffer), format_string,
            config.latency,
            config.rate,
            config.sensor_type,
            config.command,
            config.flags);
    return std::string(buffer);
}

EventType ConfigureSensorRequest::GetEventType() const {
    return static_cast<EventType>(config.event_type);
}

/* BridgeVersionInfoRequest ***************************************************/

std::vector<uint8_t> BridgeVersionInfoRequest::GetBytes() const {
    struct VersionInfoRequestEvent : public Event {
        struct BrHostEventTx event_data;
    } __attribute__((packed));

    std::vector<uint8_t> buffer(sizeof(VersionInfoRequestEvent));

    std::fill(buffer.begin(), buffer.end(), 0);
    auto event = reinterpret_cast<VersionInfoRequestEvent *>(buffer.data());
    event->event_type = static_cast<uint32_t>(EventType::AppFromHostEvent);
    event->event_data.hdr.appId   = kAppIdBridge;
    event->event_data.hdr.dataLen = sizeof(BrHostEventData);
    event->event_data.data.msgId  = BRIDGE_HOST_EVENT_MSG_VERSION_INFO;

    return buffer;
}

EventType BridgeVersionInfoRequest::GetEventType() const {
    return EventType::AppFromHostEvent;
}

std::string BridgeVersionInfoRequest::ToString() const {
    return std::string("Bridge version info request\n");
}

}  // namespace android
