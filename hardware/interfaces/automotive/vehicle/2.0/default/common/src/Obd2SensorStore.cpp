/*
 * Copyright (C) 2017 The Android Open Source Project
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

#include "Obd2SensorStore.h"

#include <utils/SystemClock.h>
#include "VehicleUtils.h"

namespace android {
namespace hardware {
namespace automotive {
namespace vehicle {
namespace V2_0 {

Obd2SensorStore::BitmaskInVector::BitmaskInVector(size_t numBits) {
    resize(numBits);
}

void Obd2SensorStore::BitmaskInVector::resize(size_t numBits) {
    mStorage = std::vector<uint8_t>((numBits + 7) / 8, 0);
}

void Obd2SensorStore::BitmaskInVector::set(size_t index, bool value) {
    const size_t byteIndex = index / 8;
    const size_t bitIndex = index % 8;
    const uint8_t byte = mStorage[byteIndex];
    uint8_t newValue = value ? (byte | (1 << bitIndex)) : (byte & ~(1 << bitIndex));
    mStorage[byteIndex] = newValue;
}

bool Obd2SensorStore::BitmaskInVector::get(size_t index) const {
    const size_t byteIndex = index / 8;
    const size_t bitIndex = index % 8;
    const uint8_t byte = mStorage[byteIndex];
    return (byte & (1 << bitIndex)) != 0;
}

const std::vector<uint8_t>& Obd2SensorStore::BitmaskInVector::getBitmask() const {
    return mStorage;
}

Obd2SensorStore::Obd2SensorStore(size_t numVendorIntegerSensors, size_t numVendorFloatSensors) {
    // because the last index is valid *inclusive*
    const size_t numSystemIntegerSensors =
        toInt(DiagnosticIntegerSensorIndex::LAST_SYSTEM_INDEX) + 1;
    const size_t numSystemFloatSensors = toInt(DiagnosticFloatSensorIndex::LAST_SYSTEM_INDEX) + 1;
    mIntegerSensors = std::vector<int32_t>(numSystemIntegerSensors + numVendorIntegerSensors, 0);
    mFloatSensors = std::vector<float>(numSystemFloatSensors + numVendorFloatSensors, 0);
    mSensorsBitmask.resize(mIntegerSensors.size() + mFloatSensors.size());
}

StatusCode Obd2SensorStore::setIntegerSensor(DiagnosticIntegerSensorIndex index, int32_t value) {
    return setIntegerSensor(toInt(index), value);
}
StatusCode Obd2SensorStore::setFloatSensor(DiagnosticFloatSensorIndex index, float value) {
    return setFloatSensor(toInt(index), value);
}

StatusCode Obd2SensorStore::setIntegerSensor(size_t index, int32_t value) {
    mIntegerSensors[index] = value;
    mSensorsBitmask.set(index, true);
    return StatusCode::OK;
}

StatusCode Obd2SensorStore::setFloatSensor(size_t index, float value) {
    mFloatSensors[index] = value;
    mSensorsBitmask.set(index + mIntegerSensors.size(), true);
    return StatusCode::OK;
}

const std::vector<int32_t>& Obd2SensorStore::getIntegerSensors() const {
    return mIntegerSensors;
}

const std::vector<float>& Obd2SensorStore::getFloatSensors() const {
    return mFloatSensors;
}

const std::vector<uint8_t>& Obd2SensorStore::getSensorsBitmask() const {
    return mSensorsBitmask.getBitmask();
}

void Obd2SensorStore::fillPropValue(const std::string& dtc, VehiclePropValue* propValue) const {
    propValue->timestamp = elapsedRealtimeNano();
    propValue->value.int32Values = getIntegerSensors();
    propValue->value.floatValues = getFloatSensors();
    propValue->value.bytes = getSensorsBitmask();
    propValue->value.stringValue = dtc;
}

}  // namespace V2_0
}  // namespace vehicle
}  // namespace automotive
}  // namespace hardware
}  // namespace android
