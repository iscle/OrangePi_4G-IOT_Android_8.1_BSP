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

#ifndef SENSOREVENT_H_
#define SENSOREVENT_H_

#include "contexthub.h"
#include "nanomessage.h"

namespace android {

// Copied from sensors.h in nanohub firmware/inc
struct SensorFirstSample
{
    uint8_t numSamples;
    uint8_t numFlushes;
    uint8_t biasCurrent : 1;
    uint8_t biasPresent : 1;
    uint8_t biasSample : 6;
    uint8_t interrupt;
};

struct SingleAxisDataPoint {
    union {
        uint32_t deltaTime; //delta since last sample, for 0th sample this is firstSample
        struct SensorFirstSample firstSample;
    };
    union {
        float fdata;
        int32_t idata;
    };
} __attribute__((packed));

struct CompressedTripleAxisDataPoint {
    uint32_t deltaTime;
    int16_t ix;
    int16_t iy;
    int16_t iz;
} __attribute__((packed));

struct TripleAxisDataPoint {
    union {
        uint32_t deltaTime; //delta since last sample, for 0th sample this is firstSample
        struct SensorFirstSample firstSample;
    };
    union {
        float x;
        int32_t ix;
    };
    union {
        float y;
        int32_t iy;
    };
    union {
        float z;
        int32_t iz;
    };
} __attribute__((packed));

/*
 * Common timestamped sensor event structure is SensorEventHeader followed by
 * a variable length array of sensor samples, each starting with
 * SensorSampleHeader.
 */
struct SensorEventHeader : public Event {
    uint64_t reference_time;
} __attribute__((packed));

struct SensorSampleHeader {
    union {
        struct SensorFirstSample first_sample_header;
        uint32_t delta_time;
    };
} __attribute__((packed));

class SensorEvent : public ReadEventResponse {
  public:
    /*
     * Returns a pointer to a ReadEventResponse that will be constructed from
     * one of the sensor event types and populated from byte stream. If
     * this function is called, it's assumed that the event type is within the
     * range [EVT_NO_FIRST_SENSOR_EVENT, EVT_NO_SENSOR_CONFIG_EVENT)
     */
    static std::unique_ptr<SensorEvent> FromBytes(
        const std::vector<uint8_t>& buffer);

    SensorType GetSensorType() const;
    std::string GetSensorName() const;

    /*
     * Subclasses should override this function to return the number of samples
     * contained in the event.
     */
    virtual uint8_t GetNumSamples() const = 0;

  protected:
    virtual bool SizeIsValid() const = 0;
};

class TimestampedSensorEvent : public SensorEvent {
  public:
    uint8_t GetNumSamples() const override;
    uint64_t GetReferenceTime() const;
    uint64_t GetSampleTime(uint8_t index) const;
    std::string GetSampleTimeStr(uint8_t index) const;
    const SensorSampleHeader *GetSampleAtIndex(uint8_t index) const;

    std::string ToString() const override;

    virtual std::string StringForSample(uint8_t index) const = 0;

  protected:
    bool SizeIsValid() const override;
    std::string StringForAllSamples() const;

    /*
     * Subclasses must implement this to be the size of each data point,
     * including struct SensorSampleHeader.
     */
    virtual uint8_t GetSampleDataSize() const = 0;
};

class SingleAxisSensorEvent : public TimestampedSensorEvent {
  public:
    virtual std::string StringForSample(uint8_t index) const override;

  protected:
    uint8_t GetSampleDataSize() const override;
};

// Same as SingleAxisSensorEvent, but data is interpreted as an integer instead
// of float
class SingleAxisIntSensorEvent : public SingleAxisSensorEvent {
  public:
    std::string StringForSample(uint8_t index) const override;
};

class TripleAxisSensorEvent : public TimestampedSensorEvent {
  public:
    std::string StringForSample(uint8_t index) const override;

  protected:
    uint8_t GetSampleDataSize() const override;
};

class CompressedTripleAxisSensorEvent : public TimestampedSensorEvent {
  public:
    std::string StringForSample(uint8_t index) const override;

  protected:
    uint8_t GetSampleDataSize() const override;
};

}  // namespace android

#endif // SENSOREVENT_H_
