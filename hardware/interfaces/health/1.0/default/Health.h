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
#ifndef ANDROID_HARDWARE_HEALTH_V1_0_HEALTH_H
#define ANDROID_HARDWARE_HEALTH_V1_0_HEALTH_H

#include <android/hardware/health/1.0/IHealth.h>
#include <hidl/Status.h>
#include <hidl/MQDescriptor.h>
#include <healthd/healthd.h>
#include <utils/String8.h>

namespace android {
namespace hardware {
namespace health {
namespace V1_0 {
namespace implementation {

using ::android::hardware::health::V1_0::HealthInfo;
using ::android::hardware::health::V1_0::HealthConfig;
using ::android::hardware::health::V1_0::IHealth;
using ::android::hardware::Return;
using ::android::hardware::Void;
using ::android::hardware::hidl_vec;
using ::android::hardware::hidl_string;
using ::android::sp;

struct Health : public IHealth {
    // Methods from ::android::hardware::health::V1_0::IHealth follow.
    Return<void> init(const HealthConfig& config, init_cb _hidl_cb)  override;
    Return<void> update(const HealthInfo& info, update_cb _hidl_cb)  override;
    Return<void> energyCounter(energyCounter_cb _hidl_cb) override;
private:
    std::function<int(int64_t *)> mGetEnergyCounter;
};

extern "C" IHealth* HIDL_FETCH_IHealth(const char* name);

}  // namespace implementation
}  // namespace V1_0
}  // namespace health
}  // namespace hardware
}  // namespace android

#endif  // ANDROID_HARDWARE_HEALTH_V1_0_HEALTH_H
