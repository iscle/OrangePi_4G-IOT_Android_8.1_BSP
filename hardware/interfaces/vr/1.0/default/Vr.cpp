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

#define LOG_TAG "VrService"

#include <log/log.h>

#include <hardware/hardware.h>
#include <hardware/vr.h>

#include "Vr.h"

namespace android {
namespace hardware {
namespace vr {
namespace V1_0 {
namespace implementation {

Vr::Vr(vr_module_t *device) : mDevice(device) {}

// Methods from ::android::hardware::vr::V1_0::IVr follow.
Return<void> Vr::init() {
    mDevice->init(mDevice);
    return Void();
}

Return<void> Vr::setVrMode(bool enabled)  {
    mDevice->set_vr_mode(mDevice, enabled);
    return Void();
}

IVr* HIDL_FETCH_IVr(const char * /*name*/) {
    const hw_module_t *hw_module = NULL;

    int ret = hw_get_module(VR_HARDWARE_MODULE_ID, &hw_module);
    if (ret == 0) {
        return new Vr(reinterpret_cast<vr_module_t*>(
                const_cast<hw_module_t*>(hw_module)));
    } else {
        ALOGE("hw_get_module %s failed: %d", VR_HARDWARE_MODULE_ID, ret);
        return nullptr;
    }
}

} // namespace implementation
}  // namespace V1_0
}  // namespace vr
}  // namespace hardware
}  // namespace android
