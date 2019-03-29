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

#define LOG_TAG "VrDevice"

#include <android-base/logging.h>
#include <android-base/properties.h>
#include "VrDevice.h"

namespace android {
namespace hardware {
namespace vr {
namespace V1_0 {
namespace implementation {

VrDevice::VrDevice() {}

Return<void> VrDevice::init() {
    // NOOP
    return Void();
}

Return<void> VrDevice::setVrMode(bool enabled) {
    if (enabled) {
        if (!android::base::SetProperty("sys.qcom.thermalcfg", "/vendor/etc/thermal-engine-vr.conf")) {
            LOG(ERROR) << "Couldn't set thermal_engine enable property";
            return Void();
        }
    } else {
        if (!android::base::SetProperty("sys.qcom.thermalcfg", "/vendor/etc/thermal-engine.conf")) {
            LOG(ERROR) << "Couldn't set thermal_engine disable property";
            return Void();
        }
    }
    if (!android::base::SetProperty("ctl.restart", "thermal-engine")) {
        LOG(ERROR) << "Couldn't set thermal_engine restart property";
    }
    if (!access("/sys/devices/virtual/input/ftm4_touch/vrmode", W_OK)) {
        FILE *f = fopen("/sys/devices/virtual/input/ftm4_touch/vrmode", "w");
        if (f) {
            fprintf(f, "%d", (enabled ? 1 : 0));
            fclose(f);
        }
        else {
            LOG(ERROR) << "Couldn't open vrmode sysfs node";
        }
    }
    return Void();
}

}  // namespace implementation
}  // namespace V1_0
}  // namespace vr
}  // namespace hardware
}  // namespace android
