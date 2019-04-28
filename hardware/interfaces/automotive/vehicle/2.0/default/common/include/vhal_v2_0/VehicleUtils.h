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

#ifndef android_hardware_automotive_vehicle_V2_0_VehicleUtils_H_
#define android_hardware_automotive_vehicle_V2_0_VehicleUtils_H_

#include <memory>

#include <hidl/HidlSupport.h>

#include <android/hardware/automotive/vehicle/2.0/types.h>

namespace android {
namespace hardware {
namespace automotive {
namespace vehicle {
namespace V2_0 {

/** Represents all supported areas for a property. Can be used is  */
constexpr int32_t kAllSupportedAreas = 0;

/** Returns underlying (integer) value for given enum. */
template<typename ENUM>
inline constexpr typename std::underlying_type<ENUM>::type toInt(
        ENUM const value) {
    return static_cast<typename std::underlying_type<ENUM>::type>(value);
}

inline constexpr VehiclePropertyType getPropType(int32_t prop) {
    return static_cast<VehiclePropertyType>(
            prop & toInt(VehiclePropertyType::MASK));
}

inline constexpr VehiclePropertyGroup getPropGroup(int32_t prop) {
    return static_cast<VehiclePropertyGroup>(
            prop & toInt(VehiclePropertyGroup::MASK));
}

inline constexpr VehicleArea getPropArea(int32_t prop) {
    return static_cast<VehicleArea>(prop & toInt(VehicleArea::MASK));
}

inline constexpr bool isGlobalProp(int32_t prop) {
    return getPropArea(prop) == VehicleArea::GLOBAL;
}

inline constexpr bool isSystemProperty(int32_t prop) {
    return VehiclePropertyGroup::SYSTEM == getPropGroup(prop);
}

std::unique_ptr<VehiclePropValue> createVehiclePropValue(
    VehiclePropertyType type, size_t vecSize);

size_t getVehicleRawValueVectorSize(
    const VehiclePropValue::RawValue& value, VehiclePropertyType type);

void copyVehicleRawValue(VehiclePropValue::RawValue* dest,
                                const VehiclePropValue::RawValue& src);

template<typename T>
void shallowCopyHidlVec(hidl_vec<T>* dest, const hidl_vec<T>& src);

void shallowCopyHidlStr(hidl_string* dest, const hidl_string& src);

void shallowCopy(VehiclePropValue* dest, const VehiclePropValue& src);

}  // namespace V2_0
}  // namespace vehicle
}  // namespace automotive
}  // namespace hardware
}  // namespace android

#endif // android_hardware_automotive_vehicle_V2_0_VehicleUtils_H_
