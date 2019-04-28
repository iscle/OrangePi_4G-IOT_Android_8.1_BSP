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

#define LOG_TAG "automotive.vehicle@2.0-impl"

#include "VehicleUtils.h"

#include <log/log.h>

namespace android {
namespace hardware {
namespace automotive {
namespace vehicle {
namespace V2_0 {

//namespace utils {

std::unique_ptr<VehiclePropValue> createVehiclePropValue(
    VehiclePropertyType type, size_t vecSize) {
    auto val = std::unique_ptr<VehiclePropValue>(new VehiclePropValue);
    switch (type) {
        case VehiclePropertyType::INT32:      // fall through
        case VehiclePropertyType::INT32_VEC:  // fall through
        case VehiclePropertyType::BOOLEAN:
            val->value.int32Values.resize(vecSize);
            break;
        case VehiclePropertyType::FLOAT:      // fall through
        case VehiclePropertyType::FLOAT_VEC:
            val->value.floatValues.resize(vecSize);
            break;
        case VehiclePropertyType::INT64:
            val->value.int64Values.resize(vecSize);
            break;
        case VehiclePropertyType::BYTES:
            val->value.bytes.resize(vecSize);
            break;
        case VehiclePropertyType::STRING:
        case VehiclePropertyType::COMPLEX:
            break; // Valid, but nothing to do.
        default:
            ALOGE("createVehiclePropValue: unknown type: %d", type);
            val.reset(nullptr);
    }
    return val;
}

size_t getVehicleRawValueVectorSize(
    const VehiclePropValue::RawValue& value, VehiclePropertyType type) {
    switch (type) {
        case VehiclePropertyType::INT32:      // fall through
        case VehiclePropertyType::INT32_VEC:  // fall through
        case VehiclePropertyType::BOOLEAN:
            return value.int32Values.size();
        case VehiclePropertyType::FLOAT:      // fall through
        case VehiclePropertyType::FLOAT_VEC:
            return value.floatValues.size();
        case VehiclePropertyType::INT64:
            return value.int64Values.size();
        case VehiclePropertyType::BYTES:
            return value.bytes.size();
        default:
            return 0;
    }
}

template<typename T>
inline void copyHidlVec(hidl_vec <T>* dest, const hidl_vec <T>& src) {
    for (size_t i = 0; i < std::min(dest->size(), src.size()); i++) {
        (*dest)[i] = src[i];
    }
}

void copyVehicleRawValue(VehiclePropValue::RawValue* dest,
                         const VehiclePropValue::RawValue& src) {
    dest->int32Values = src.int32Values;
    dest->floatValues = src.floatValues;
    dest->int64Values = src.int64Values;
    dest->bytes = src.bytes;
    dest->stringValue = src.stringValue;
}

template<typename T>
void shallowCopyHidlVec(hidl_vec <T>* dest, const hidl_vec <T>& src) {
    if (src.size() > 0) {
        dest->setToExternal(const_cast<T*>(&src[0]), src.size());
    } else if (dest->size() > 0) {
        dest->resize(0);
    }
}

void shallowCopyHidlStr(hidl_string* dest, const hidl_string& src) {
    if (src.empty()) {
        dest->clear();
    } else {
        dest->setToExternal(src.c_str(), src.size());
    }
}

void shallowCopy(VehiclePropValue* dest, const VehiclePropValue& src) {
    dest->prop = src.prop;
    dest->areaId = src.areaId;
    dest->timestamp = src.timestamp;
    shallowCopyHidlVec(&dest->value.int32Values, src.value.int32Values);
    shallowCopyHidlVec(&dest->value.int64Values, src.value.int64Values);
    shallowCopyHidlVec(&dest->value.floatValues, src.value.floatValues);
    shallowCopyHidlVec(&dest->value.bytes, src.value.bytes);
    shallowCopyHidlStr(&dest->value.stringValue, src.value.stringValue);
}


//}  // namespace utils

}  // namespace V2_0
}  // namespace vehicle
}  // namespace automotive
}  // namespace hardware
}  // namespace android
