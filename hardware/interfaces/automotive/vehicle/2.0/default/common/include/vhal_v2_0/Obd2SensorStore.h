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

#ifndef android_hardware_automotive_vehicle_V2_0_Obd2SensorStore_H_
#define android_hardware_automotive_vehicle_V2_0_Obd2SensorStore_H_

#include <vector>

#include <android/hardware/automotive/vehicle/2.0/types.h>

namespace android {
namespace hardware {
namespace automotive {
namespace vehicle {
namespace V2_0 {

// This class wraps all the logic required to create an OBD2 frame.
// It allows storing sensor values, setting appropriate bitmasks as needed,
// and returning appropriately laid out storage of sensor values suitable
// for being returned via a VehicleHal implementation.
class Obd2SensorStore {
   public:
    // Creates a sensor storage with a given number of vendor-specific sensors.
    Obd2SensorStore(size_t numVendorIntegerSensors, size_t numVendorFloatSensors);

    // Stores an integer-valued sensor.
    StatusCode setIntegerSensor(DiagnosticIntegerSensorIndex index, int32_t value);
    // Stores an integer-valued sensor.
    StatusCode setIntegerSensor(size_t index, int32_t value);

    // Stores a float-valued sensor.
    StatusCode setFloatSensor(DiagnosticFloatSensorIndex index, float value);
    // Stores a float-valued sensor.
    StatusCode setFloatSensor(size_t index, float value);

    // Returns a vector that contains all integer sensors stored.
    const std::vector<int32_t>& getIntegerSensors() const;
    // Returns a vector that contains all float sensors stored.
    const std::vector<float>& getFloatSensors() const;
    // Returns a vector that contains a bitmask for all stored sensors.
    const std::vector<uint8_t>& getSensorsBitmask() const;

    // Given a stringValue, fill in a VehiclePropValue
    void fillPropValue(const std::string& dtc, VehiclePropValue* propValue) const;

   private:
    class BitmaskInVector {
       public:
        BitmaskInVector(size_t numBits = 0);
        void resize(size_t numBits);
        bool get(size_t index) const;
        void set(size_t index, bool value);

        const std::vector<uint8_t>& getBitmask() const;

       private:
        std::vector<uint8_t> mStorage;
    };

    std::vector<int32_t> mIntegerSensors;
    std::vector<float> mFloatSensors;
    BitmaskInVector mSensorsBitmask;
};

}  // namespace V2_0
}  // namespace vehicle
}  // namespace automotive
}  // namespace hardware
}  // namespace android

#endif  // android_hardware_automotive_vehicle_V2_0_Obd2SensorStore_H_
