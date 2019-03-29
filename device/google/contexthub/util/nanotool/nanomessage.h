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

#ifndef NANOMESSAGE_H_
#define NANOMESSAGE_H_

#include <memory>
#include <string>
#include <vector>

#include "noncopyable.h"

namespace android {

/*
 * Events types that can be pushed back and forth between the ContextHub and
 * host software.
 */
enum class EventType {
    AppFromHostEvent = 0x000000F8,
    FirstSensorEvent = 0x00000200,
    LastSensorEvent  = 0x000002FF,
    ConfigureSensor  = 0x00000300,
    AppToHostEvent   = 0x00000401,
    ResetReasonEvent = 0x00000403,
    LogEvent         = 0x474F4C41,
};

/*
 * An interface for all messages passed to and from the ContextHub.
 */
class NanoMessage : public NonCopyable {
  public:
    virtual ~NanoMessage() {};

    // Generates a string intended to be printed to a console or saved to logs.
    // This interface requires that the string be terminated with a newline.
    virtual std::string ToString() const = 0;
};

/*
 * An interface for requests sent to the ContextHub.
 */
class NanoRequest : public NanoMessage {
  public:
    // Returns a payload of bytes to be packaged into a NanoPacket.
    virtual std::vector<uint8_t> GetBytes() const = 0;
};

/*
 * An interface for responses from the ContextHub.
 */
class NanoResponse : public NanoMessage {
  public:
    // Populates the fields of the NanoMessage given a NanoPacket. Returns
    // false if the packet is incomplete or incorrect message.
    virtual bool Populate(const std::vector<uint8_t>& buffer) = 0;
};

/*
 * Version information for a ContextHub.
 */
class HardwareVersionInfo : public NanoResponse {
  public:
    bool Populate(const std::vector<uint8_t>& buffer) override;
    std::string ToString() const override;

    struct VersionInfo {
        uint16_t hardware_type;
        uint16_t hardware_version;
        uint16_t bootloader_version;
        uint16_t operating_system_version;
        uint32_t variant_version;
    } __attribute__((packed)) info;
};

/*
 * The base event for all event data.
 */
struct Event {
    uint32_t event_type;
} __attribute__((packed));

/*
 * A request to write an event to the ContextHub.
 */
class WriteEventRequest : public NanoRequest {
  public:
    virtual EventType GetEventType() const = 0;
};

/*
 * A response to writing an event to the ContextHub.
 */
class WriteEventResponse : public NanoResponse {
  public:
    std::string ToString() const override;
    bool Populate(const std::vector<uint8_t>& buffer) override;

    struct Response {
        bool accepted;
    } __attribute__((packed)) response;
};

/*
 * A response to reading an event from the ContextHub.
 */
class ReadEventRequest : public NanoRequest {
  public:
    std::vector<uint8_t> GetBytes() const override;
    std::string ToString() const override;

    struct Request {
        uint64_t boot_time;
    } __attribute__((packed)) request;
};

class ReadEventResponse : public NanoResponse {
  public:
    virtual std::string ToString() const override;

    // Construct and populate a concrete ReadEventResponse from the given buffer
    static std::unique_ptr<ReadEventResponse> FromBytes(
        const std::vector<uint8_t>& buffer);

    bool Populate(const std::vector<uint8_t>& buffer) override;

    bool IsAppToHostEvent() const;
    bool IsSensorEvent() const;
    bool IsResetReasonEvent() const;
    bool IsLogEvent() const;
    uint32_t GetEventType() const;

    // Event data associated with this response.
    std::vector<uint8_t> event_data;

  protected:
    static uint32_t EventTypeFromBuffer(const std::vector<uint8_t>& buffer);
    static bool IsAppToHostEvent(uint32_t event_type);
    static bool IsSensorEvent(uint32_t event_type);
    static bool IsResetReasonEvent(uint32_t event_type);
    static bool IsLogEvent(uint32_t event_type);
};

/*
 * An event used to configure a sensor with specific attributes.
 */
class ConfigureSensorRequest : public WriteEventRequest {
  public:
    enum class CommandType {
        Disable,
        Enable,
        Flush,
        ConfigData,
        Calibrate,
        SelfTest
    };

    ConfigureSensorRequest();

    static uint32_t FloatRateToFixedPoint(float rate);
    static float FixedPointRateToFloat(uint32_t rate);

    std::vector<uint8_t> GetBytes() const override;
    std::string ToString() const override;
    EventType GetEventType() const override;

    // Appends some data to the configuration request, e.g. for the ConfigData
    // command
    void SetAdditionalData(const std::vector<uint8_t>& data);

    struct Configuration : public Event {
        uint64_t latency;
        uint32_t rate;
        uint8_t sensor_type;
        uint8_t command;
        uint16_t flags;
    }  __attribute__((packed)) config = {};

  private:
    std::vector<uint8_t> extra_data_;
};

class BridgeVersionInfoRequest : public WriteEventRequest {
  public:
    //BridgeVersionInfoRequest() {};
    std::vector<uint8_t> GetBytes() const override;
    EventType GetEventType() const override;
    std::string ToString() const override;
};

}  // namespace android

#endif  // NANOMESSAGE_H_
