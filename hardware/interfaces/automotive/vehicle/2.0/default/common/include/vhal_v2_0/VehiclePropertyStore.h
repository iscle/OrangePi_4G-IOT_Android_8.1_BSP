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

#ifndef android_hardware_automotive_vehicle_V2_0_impl_PropertyDb_H_
#define android_hardware_automotive_vehicle_V2_0_impl_PropertyDb_H_

#include <cstdint>
#include <unordered_map>
#include <memory>
#include <mutex>

#include <android/hardware/automotive/vehicle/2.0/IVehicle.h>

namespace android {
namespace hardware {
namespace automotive {
namespace vehicle {
namespace V2_0 {

/**
 * Encapsulates work related to storing and accessing configuration, storing and modifying
 * vehicle property values.
 *
 * VehiclePropertyValues stored in a sorted map thus it makes easier to get range of values, e.g.
 * to get value for all areas for particular property.
 *
 * This class is thread-safe, however it uses blocking synchronization across all methods.
 */
class VehiclePropertyStore {
public:
    /* Function that used to calculate unique token for given VehiclePropValue */
    using TokenFunction = std::function<int64_t(const VehiclePropValue& value)>;

private:
    struct RecordConfig {
        VehiclePropConfig propConfig;
        TokenFunction tokenFunction;
    };

    struct RecordId {
        int32_t prop;
        int32_t area;
        int64_t token;

        bool operator==(const RecordId& other) const;
        bool operator<(const RecordId& other) const;
    };

    using PropertyMap = std::map<RecordId, VehiclePropValue>;
    using PropertyMapRange = std::pair<PropertyMap::const_iterator, PropertyMap::const_iterator>;

public:
    void registerProperty(const VehiclePropConfig& config, TokenFunction tokenFunc = nullptr);

    /* Stores provided value. Returns true if value was written returns false if config for
     * example wasn't registered. */
    bool writeValue(const VehiclePropValue& propValue);

    void removeValue(const VehiclePropValue& propValue);
    void removeValuesForProperty(int32_t propId);

    std::vector<VehiclePropValue> readAllValues() const;
    std::vector<VehiclePropValue> readValuesForProperty(int32_t propId) const;
    std::unique_ptr<VehiclePropValue> readValueOrNull(const VehiclePropValue& request) const;
    std::unique_ptr<VehiclePropValue> readValueOrNull(int32_t prop, int32_t area = 0,
                                                      int64_t token = 0) const;

    std::vector<VehiclePropConfig> getAllConfigs() const;
    const VehiclePropConfig* getConfigOrNull(int32_t propId) const;
    const VehiclePropConfig* getConfigOrDie(int32_t propId) const;

private:
    RecordId getRecordIdLocked(const VehiclePropValue& valuePrototype) const;
    const VehiclePropValue* getValueOrNullLocked(const RecordId& recId) const;
    PropertyMapRange findRangeLocked(int32_t propId) const;

private:
    using MuxGuard = std::lock_guard<std::mutex>;
    mutable std::mutex mLock;
    std::unordered_map<int32_t /* VehicleProperty */, RecordConfig> mConfigs;

    PropertyMap mPropertyValues;  // Sorted map of RecordId : VehiclePropValue.
};

}  // namespace V2_0
}  // namespace vehicle
}  // namespace automotive
}  // namespace hardware
}  // namespace android

#endif //android_hardware_automotive_vehicle_V2_0_impl_PropertyDb_H_
