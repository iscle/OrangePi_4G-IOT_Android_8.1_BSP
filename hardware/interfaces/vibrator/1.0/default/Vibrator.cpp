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

#define LOG_TAG "VibratorService"

#include <inttypes.h>

#include <log/log.h>

#include <hardware/hardware.h>
#include <hardware/vibrator.h>

#include "Vibrator.h"

namespace android {
namespace hardware {
namespace vibrator {
namespace V1_0 {
namespace implementation {

Vibrator::Vibrator(vibrator_device_t *device) : mDevice(device) {}

// Methods from ::android::hardware::vibrator::V1_0::IVibrator follow.
Return<Status> Vibrator::on(uint32_t timeout_ms) {
    int32_t ret = mDevice->vibrator_on(mDevice, timeout_ms);
    if (ret != 0) {
        ALOGE("on command failed : %s", strerror(-ret));
        return Status::UNKNOWN_ERROR;
    }
    return Status::OK;
}

Return<Status> Vibrator::off()  {
    int32_t ret = mDevice->vibrator_off(mDevice);
    if (ret != 0) {
        ALOGE("off command failed : %s", strerror(-ret));
        return Status::UNKNOWN_ERROR;
    }
    return Status::OK;
}

Return<bool> Vibrator::supportsAmplitudeControl()  {
    return false;
}

Return<Status> Vibrator::setAmplitude(uint8_t) {
    return Status::UNSUPPORTED_OPERATION;
}

Return<void> Vibrator::perform(Effect, EffectStrength, perform_cb _hidl_cb) {
    _hidl_cb(Status::UNSUPPORTED_OPERATION, 0);
    return Void();
}

IVibrator* HIDL_FETCH_IVibrator(const char * /*hal*/) {
    vibrator_device_t *vib_device;
    const hw_module_t *hw_module = nullptr;

    int ret = hw_get_module(VIBRATOR_HARDWARE_MODULE_ID, &hw_module);
    if (ret == 0) {
        ret = vibrator_open(hw_module, &vib_device);
        if (ret != 0) {
            ALOGE("vibrator_open failed: %d", ret);
        }
    } else {
        ALOGE("hw_get_module %s failed: %d", VIBRATOR_HARDWARE_MODULE_ID, ret);
    }

    if (ret == 0) {
        return new Vibrator(vib_device);
    } else {
        ALOGE("Passthrough failed to open legacy HAL.");
        return nullptr;
    }
}

} // namespace implementation
}  // namespace V1_0
}  // namespace vibrator
}  // namespace hardware
}  // namespace android
