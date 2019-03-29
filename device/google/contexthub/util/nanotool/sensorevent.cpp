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

#include "sensorevent.h"

#include <inttypes.h>
#include <string.h>

#include "contexthub.h"
#include "log.h"

namespace android {

constexpr float kCompressedSampleRatio(8.0f * 9.81f / 32768.0f);

/* SensorEvent ****************************************************************/

std::unique_ptr<SensorEvent> SensorEvent::FromBytes(
        const std::vector<uint8_t>& buffer) {
    SensorEvent *sensor_event = nullptr;

    SensorType sensor_type = static_cast<SensorType>(
        ReadEventResponse::EventTypeFromBuffer(buffer) -
        static_cast<uint32_t>(EventType::FirstSensorEvent));

    switch (sensor_type) {
      case SensorType::Accel:
      case SensorType::Gyro:
      case SensorType::GyroUncal:
      case SensorType::Magnetometer:
      case SensorType::MagnetometerUncal:
      case SensorType::Orientation:
      case SensorType::Gravity:
      case SensorType::LinearAccel:
      case SensorType::RotationVector:
      case SensorType::GeomagneticRotationVector:
      case SensorType::GameRotationVector:
        sensor_event = new TripleAxisSensorEvent();
        break;

      case SensorType::Barometer:
      case SensorType::Temperature:
      case SensorType::AmbientLightSensor:
      case SensorType::Proximity:
      case SensorType::Humidity:
        sensor_event = new SingleAxisSensorEvent();
        break;

      // TODO: Activity uses a special struct, it should have its own class
      case SensorType::Activity:
      case SensorType::AnyMotion:
      case SensorType::NoMotion:
      case SensorType::SignificantMotion:
      case SensorType::Flat:
      case SensorType::WindowOrientation:
      case SensorType::Tilt:
      case SensorType::Hall:
      case SensorType::HeartRateECG: // Heart rates not implemented, guessing
      case SensorType::HeartRatePPG: // data type here...
      case SensorType::StepCount:
      case SensorType::StepDetect:
      case SensorType::Gesture:
      case SensorType::DoubleTwist:
      case SensorType::DoubleTap:
      case SensorType::Vsync:
      case SensorType::WristTilt:
          sensor_event = new SingleAxisIntSensorEvent();
          break;

      case SensorType::CompressedAccel:
          sensor_event = new CompressedTripleAxisSensorEvent();
          break;

    default:
        LOGW("Can't create SensorEvent for unknown/invalid sensor type %d",
             static_cast<int>(sensor_type));
    }

    if (sensor_event &&
        (!sensor_event->Populate(buffer) || !sensor_event->SizeIsValid())) {
        LOGW("Couldn't populate sensor event, or invalid size");
        delete sensor_event;
        sensor_event = nullptr;
    }

    return std::unique_ptr<SensorEvent>(sensor_event);
}

SensorType SensorEvent::GetSensorType() const {
    return static_cast<SensorType>(
        GetEventType() - static_cast<uint32_t>(EventType::FirstSensorEvent));
}

/* TimestampedSensorEvent *****************************************************/

uint8_t TimestampedSensorEvent::GetNumSamples() const {
    // Perform size check, but don't depend on SizeIsValid since it will call us
    if (event_data.size() < (sizeof(struct SensorEventHeader) +
                             sizeof(struct SensorFirstSample))) {
        LOGW("Short/invalid timestamped sensor event; length %zu",
             event_data.size());
        return 0;
    }

    const struct SensorFirstSample *first_sample_header =
        reinterpret_cast<const struct SensorFirstSample *>(
            event_data.data() + sizeof(struct SensorEventHeader));

    return first_sample_header->numSamples;
}

uint64_t TimestampedSensorEvent::GetReferenceTime() const {
    if (!SizeIsValid()) {
        return 0;
    }
    const struct SensorEventHeader *header =
        reinterpret_cast<const struct SensorEventHeader *>(event_data.data());
    return header->reference_time;
}

uint64_t TimestampedSensorEvent::GetSampleTime(uint8_t index) const {
    const SensorSampleHeader *sample;
    uint64_t sample_time = GetReferenceTime();

    // For index 0, the sample time is the reference time. For each subsequent
    // sample, sum the delta to the previous sample to get the sample time.
    for (uint8_t i = 1; i <= index; i++) {
        sample = GetSampleAtIndex(index);
        sample_time += sample->delta_time;
    }

    return sample_time;
}

std::string TimestampedSensorEvent::GetSampleTimeStr(uint8_t index) const {
    uint64_t sample_time = GetSampleTime(index);

    char buffer[32];
    snprintf(buffer, sizeof(buffer), "%" PRIu64 ".%06" PRIu64 " ms",
             sample_time / 1000000, sample_time % 1000000);

    return std::string(buffer);
}

const SensorSampleHeader *TimestampedSensorEvent::GetSampleAtIndex(
        uint8_t index) const {
    if (index >= GetNumSamples()) {
        LOGW("Requested sample at invalid index %u", index);
        return nullptr;
    }

    unsigned int offset = (sizeof(struct SensorEventHeader) +
        index * GetSampleDataSize());
    return reinterpret_cast<const struct SensorSampleHeader *>(
        event_data.data() + offset);
}

std::string TimestampedSensorEvent::ToString() const {
    uint8_t num_samples = GetNumSamples();
    char buffer[64];
    snprintf(buffer, sizeof(buffer),
             "Event from sensor %d (%s) with %d sample%s\n",
             static_cast<int>(GetSensorType()),
             ContextHub::SensorTypeToAbbrevName(GetSensorType()).c_str(),
             num_samples, (num_samples != 1) ? "s" : "");

    return std::string(buffer) + StringForAllSamples();
}

bool TimestampedSensorEvent::SizeIsValid() const {
    unsigned int min_size = (sizeof(struct SensorEventHeader) +
        GetNumSamples() * GetSampleDataSize());
    if (event_data.size() < min_size) {
        LOGW("Got short sensor event with %zu bytes, expected >= %u",
             event_data.size(), min_size);
        return false;
    }

    return true;
}

std::string TimestampedSensorEvent::StringForAllSamples() const {
    std::string str;
    for (unsigned int i = 0; i < GetNumSamples(); i++) {
        str += StringForSample(i);
    }
    return str;
}

/* SingleAxisSensorEvent ******************************************************/

std::string SingleAxisSensorEvent::StringForSample(uint8_t index) const {
    const SingleAxisDataPoint *sample =
        reinterpret_cast<const SingleAxisDataPoint *>(GetSampleAtIndex(index));

    char buffer[64];
    snprintf(buffer, sizeof(buffer), "  %f @ %s\n",
             sample->fdata, GetSampleTimeStr(index).c_str());

    return std::string(buffer);
}

uint8_t SingleAxisSensorEvent::GetSampleDataSize() const {
    return sizeof(struct SingleAxisDataPoint);
}

/* SingleAxisIntSensorEvent ***************************************************/

std::string SingleAxisIntSensorEvent::StringForSample(uint8_t index) const {
    const SingleAxisDataPoint *sample =
        reinterpret_cast<const SingleAxisDataPoint *>(GetSampleAtIndex(index));

    char buffer[64];
    snprintf(buffer, sizeof(buffer), "  %d @ %s\n",
             sample->idata, GetSampleTimeStr(index).c_str());

    return std::string(buffer);
}

/* TripleAxisSensorEvent ******************************************************/

std::string TripleAxisSensorEvent::StringForSample(uint8_t index) const {
    const TripleAxisDataPoint *sample =
        reinterpret_cast<const TripleAxisDataPoint *>(
            GetSampleAtIndex(index));

    const struct SensorFirstSample *first_sample =
        reinterpret_cast<const struct SensorFirstSample *>(
            event_data.data() + sizeof(struct SensorEventHeader));
    bool is_bias_sample = first_sample->biasPresent
        && first_sample->biasSample == index;

    char buffer[128];
    snprintf(buffer, sizeof(buffer), "  X:%f Y:%f Z:%f @ %s%s\n",
             sample->x, sample->y, sample->z, GetSampleTimeStr(index).c_str(),
             is_bias_sample ? " (Bias Sample)" : "");

    return std::string(buffer);
}

uint8_t TripleAxisSensorEvent::GetSampleDataSize() const {
    return sizeof(struct TripleAxisDataPoint);
}

/* CompressedTripleAxisSensorEvent ********************************************/

std::string CompressedTripleAxisSensorEvent::StringForSample(
        uint8_t index) const {
    const CompressedTripleAxisDataPoint *sample =
        reinterpret_cast<const CompressedTripleAxisDataPoint *>(
            GetSampleAtIndex(index));

    const struct SensorFirstSample *first_sample =
        reinterpret_cast<const struct SensorFirstSample *>(
            event_data.data() + sizeof(struct SensorEventHeader));
    bool is_bias_sample = first_sample->biasPresent
        && first_sample->biasSample == index;

    float x = sample->ix * kCompressedSampleRatio;
    float y = sample->iy * kCompressedSampleRatio;
    float z = sample->iz * kCompressedSampleRatio;

    char buffer[128];
    snprintf(buffer, sizeof(buffer), "  X:%f Y:%f Z:%f @ %s%s\n",
             x, y, z, GetSampleTimeStr(index).c_str(),
             is_bias_sample ? " (Bias Sample)" : "");

    return std::string(buffer);
}

uint8_t CompressedTripleAxisSensorEvent::GetSampleDataSize() const {
    return sizeof(CompressedTripleAxisDataPoint);
}

}  // namespace android
