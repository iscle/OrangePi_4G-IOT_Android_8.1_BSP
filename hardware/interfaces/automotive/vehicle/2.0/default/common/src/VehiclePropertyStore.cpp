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
#define LOG_TAG "VehiclePropertyStore"
#include <log/log.h>

#include <common/include/vhal_v2_0/VehicleUtils.h>
#include "VehiclePropertyStore.h"

namespace android {
namespace hardware {
namespace automotive {
namespace vehicle {
namespace V2_0 {

bool VehiclePropertyStore::RecordId::operator==(const VehiclePropertyStore::RecordId& other) const {
    return prop == other.prop && area == other.area && token == other.token;
}

bool VehiclePropertyStore::RecordId::operator<(const VehiclePropertyStore::RecordId& other) const  {
    return prop < other.prop
           || (prop == other.prop && area < other.area)
           || (prop == other.prop && area == other.area && token < other.token);
}

void VehiclePropertyStore::registerProperty(const VehiclePropConfig& config,
                                            VehiclePropertyStore::TokenFunction tokenFunc) {
    MuxGuard g(mLock);
    mConfigs.insert({ config.prop, RecordConfig { config, tokenFunc } });
}

bool VehiclePropertyStore::writeValue(const VehiclePropValue& propValue) {
    MuxGuard g(mLock);
    if (!mConfigs.count(propValue.prop)) return false;

    RecordId recId = getRecordIdLocked(propValue);
    VehiclePropValue* valueToUpdate = const_cast<VehiclePropValue*>(getValueOrNullLocked(recId));
    if (valueToUpdate == nullptr) {
        mPropertyValues.insert({ recId, propValue });
    } else {
        valueToUpdate->timestamp = propValue.timestamp;
        valueToUpdate->value = propValue.value;
    }
    return true;
}

void VehiclePropertyStore::removeValue(const VehiclePropValue& propValue) {
    MuxGuard g(mLock);
    RecordId recId = getRecordIdLocked(propValue);
    auto it = mPropertyValues.find(recId);
    if (it != mPropertyValues.end()) {
        mPropertyValues.erase(it);
    }
}

void VehiclePropertyStore::removeValuesForProperty(int32_t propId) {
    MuxGuard g(mLock);
    auto range = findRangeLocked(propId);
    mPropertyValues.erase(range.first, range.second);
}

std::vector<VehiclePropValue> VehiclePropertyStore::readAllValues() const {
    MuxGuard g(mLock);
    std::vector<VehiclePropValue> allValues;
    allValues.reserve(mPropertyValues.size());
    for (auto&& it : mPropertyValues) {
        allValues.push_back(it.second);
    }
    return allValues;
}

std::vector<VehiclePropValue> VehiclePropertyStore::readValuesForProperty(int32_t propId) const {
    std::vector<VehiclePropValue> values;
    MuxGuard g(mLock);
    auto range = findRangeLocked(propId);
    for (auto it = range.first; it != range.second; ++it) {
        values.push_back(it->second);
    }

    return values;
}

std::unique_ptr<VehiclePropValue> VehiclePropertyStore::readValueOrNull(
        const VehiclePropValue& request) const {
    MuxGuard g(mLock);
    RecordId recId = getRecordIdLocked(request);
    const VehiclePropValue* internalValue = getValueOrNullLocked(recId);
    return internalValue ? std::make_unique<VehiclePropValue>(*internalValue) : nullptr;
}

std::unique_ptr<VehiclePropValue> VehiclePropertyStore::readValueOrNull(
        int32_t prop, int32_t area, int64_t token) const {
    RecordId recId = {prop, isGlobalProp(prop) ? 0 : area, token };
    MuxGuard g(mLock);
    const VehiclePropValue* internalValue = getValueOrNullLocked(recId);
    return internalValue ? std::make_unique<VehiclePropValue>(*internalValue) : nullptr;
}


std::vector<VehiclePropConfig> VehiclePropertyStore::getAllConfigs() const {
    MuxGuard g(mLock);
    std::vector<VehiclePropConfig> configs;
    configs.reserve(mConfigs.size());
    for (auto&& recordConfigIt: mConfigs) {
        configs.push_back(recordConfigIt.second.propConfig);
    }
    return configs;
}

const VehiclePropConfig* VehiclePropertyStore::getConfigOrNull(int32_t propId) const {
    MuxGuard g(mLock);
    auto recordConfigIt = mConfigs.find(propId);
    return recordConfigIt != mConfigs.end() ? &recordConfigIt->second.propConfig : nullptr;
}

const VehiclePropConfig* VehiclePropertyStore::getConfigOrDie(int32_t propId) const {
    auto cfg = getConfigOrNull(propId);
    if (!cfg) {
        ALOGW("%s: config not found for property: 0x%x", __func__, propId);
        abort();
    }
    return cfg;
}

VehiclePropertyStore::RecordId VehiclePropertyStore::getRecordIdLocked(
        const VehiclePropValue& valuePrototype) const {
    RecordId recId = {
        .prop = valuePrototype.prop,
        .area = isGlobalProp(valuePrototype.prop) ? 0 : valuePrototype.areaId,
        .token = 0
    };

    auto it = mConfigs.find(recId.prop);
    if (it == mConfigs.end()) return {};

    if (it->second.tokenFunction != nullptr) {
        recId.token = it->second.tokenFunction(valuePrototype);
    }
    return recId;
}

const VehiclePropValue* VehiclePropertyStore::getValueOrNullLocked(
        const VehiclePropertyStore::RecordId& recId) const  {
    auto it = mPropertyValues.find(recId);
    return it == mPropertyValues.end() ? nullptr : &it->second;
}

VehiclePropertyStore::PropertyMapRange VehiclePropertyStore::findRangeLocked(int32_t propId) const {
    // Based on the fact that mPropertyValues is a sorted map by RecordId.
    auto beginIt = mPropertyValues.lower_bound( RecordId { propId, INT32_MIN, 0 });
    auto endIt = mPropertyValues.lower_bound( RecordId { propId + 1, INT32_MIN, 0 });

    return  PropertyMapRange { beginIt, endIt };
}

}  // namespace V2_0
}  // namespace vehicle
}  // namespace automotive
}  // namespace hardware
}  // namespace android
