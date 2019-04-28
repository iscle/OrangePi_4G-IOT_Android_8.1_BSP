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

#define LOG_TAG "android.hardware.tests.memory@1.0"

#include "MemoryTest.h"

#include <log/log.h>

#include <hidlmemory/mapping.h>

#include <android/hidl/memory/1.0/IMemory.h>

using android::hidl::memory::V1_0::IMemory;

namespace android {
namespace hardware {
namespace tests {
namespace memory {
namespace V1_0 {
namespace implementation {

// Methods from ::android::hardware::tests::memory::V1_0::IMemoryTest follow.
Return<void> Memory::haveSomeMemory(const hidl_memory& mem, haveSomeMemory_cb _hidl_cb) {
    _hidl_cb(mem);
    return Void();
}

Return<void> Memory::fillMemory(const hidl_memory& memory_in, uint8_t filler) {
    sp<IMemory> memory = mapMemory(memory_in);

    if (memory == nullptr) {
        ALOGE("Could not map hidl_memory");
        return Void();
    }

    uint8_t* data = static_cast<uint8_t*>(static_cast<void*>(memory->getPointer()));

    memory->update();

    for (size_t i = 0; i < memory->getSize(); i++) {
        data[i] = filler;
    }

    memory->commit();

    return Void();
}


IMemoryTest* HIDL_FETCH_IMemoryTest(const char* /* name */) {
    return new Memory();
}

}  // namespace implementation
}  // namespace V1_0
}  // namespace memory
}  // namespace tests
}  // namespace hardware
}  // namespace android
