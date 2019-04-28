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

#define LOG_TAG "android.hardware.memtrack@1.0-impl"

#include <log/log.h>

#include <hardware/hardware.h>
#include <hardware/memtrack.h>

#include "Memtrack.h"

namespace android {
namespace hardware {
namespace memtrack {
namespace V1_0 {
namespace implementation {

Memtrack::Memtrack(const memtrack_module_t *module) : mModule(module) {
    if (mModule)
        mModule->init(mModule);
}

Memtrack::~Memtrack() {
    delete(mModule);
}

Return<void> Memtrack::getMemory(int32_t pid, MemtrackType type,
        getMemory_cb _hidl_cb)  {
    hidl_vec<MemtrackRecord> records;
    size_t temp = 0;
    size_t *size = &temp;
    int ret = 0;

    if (mModule->getMemory == nullptr)
    {
        _hidl_cb(MemtrackStatus::SUCCESS, records);
        return Void();
    }
    ret = mModule->getMemory(mModule, pid, static_cast<memtrack_type>(type),
            NULL, size);
    if (ret == 0)
    {
        memtrack_record *legacy_records = new memtrack_record[*size];
        ret = mModule->getMemory(mModule, pid,
                static_cast<memtrack_type>(type), legacy_records, size);
        if (ret == 0)
        {
            records.resize(*size);
            for(size_t i = 0; i < *size; i++)
            {
                records[i].sizeInBytes = legacy_records[i].size_in_bytes;
                records[i].flags = legacy_records[i].flags;
            }
        }
        delete[] legacy_records;
    }
    _hidl_cb(MemtrackStatus::SUCCESS, records);
    return Void();
}


IMemtrack* HIDL_FETCH_IMemtrack(const char* /* name */) {
    const hw_module_t* hw_module = nullptr;
    const memtrack_module_t* memtrack_module = nullptr;
    int err = hw_get_module(MEMTRACK_HARDWARE_MODULE_ID, &hw_module);
    if (err) {
        ALOGE ("hw_get_module %s failed: %d", MEMTRACK_HARDWARE_MODULE_ID, err);
        return nullptr;
    }

    if (!hw_module->methods || !hw_module->methods->open) {
        memtrack_module = reinterpret_cast<const memtrack_module_t*>(hw_module);
    } else {
        err = hw_module->methods->open(hw_module, MEMTRACK_HARDWARE_MODULE_ID,
                reinterpret_cast<hw_device_t**>(const_cast<memtrack_module_t**>(&memtrack_module)));
        if (err) {
            ALOGE("Passthrough failed to load legacy HAL.");
            return nullptr;
        }
    }
    return new Memtrack(memtrack_module);
}

} // namespace implementation
}  // namespace V1_0
}  // namespace memtrack
}  // namespace hardware
}  // namespace android
