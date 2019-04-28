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

#include <string.h>

#define LOG_TAG "DevicesFactoryHalHidl"
//#define LOG_NDEBUG 0

#include <android/hardware/audio/2.0/IDevice.h>
#include <media/audiohal/hidl/HalDeathHandler.h>
#include <utils/Log.h>

#include "ConversionHelperHidl.h"
#include "DeviceHalHidl.h"
#include "DevicesFactoryHalHidl.h"

using ::android::hardware::audio::V2_0::IDevice;
using ::android::hardware::audio::V2_0::Result;
using ::android::hardware::Return;

namespace android {

DevicesFactoryHalHidl::DevicesFactoryHalHidl() {
    mDevicesFactory = IDevicesFactory::getService();
    if (mDevicesFactory != 0) {
        // It is assumed that DevicesFactory is owned by AudioFlinger
        // and thus have the same lifespan.
        mDevicesFactory->linkToDeath(HalDeathHandler::getInstance(), 0 /*cookie*/);
    } else {
        ALOGE("Failed to obtain IDevicesFactory service, terminating process.");
        exit(1);
    }
}

DevicesFactoryHalHidl::~DevicesFactoryHalHidl() {
}

// static
status_t DevicesFactoryHalHidl::nameFromHal(const char *name, IDevicesFactory::Device *device) {
    if (strcmp(name, AUDIO_HARDWARE_MODULE_ID_PRIMARY) == 0) {
        *device = IDevicesFactory::Device::PRIMARY;
        return OK;
    } else if(strcmp(name, AUDIO_HARDWARE_MODULE_ID_A2DP) == 0) {
        *device = IDevicesFactory::Device::A2DP;
        return OK;
    } else if(strcmp(name, AUDIO_HARDWARE_MODULE_ID_USB) == 0) {
        *device = IDevicesFactory::Device::USB;
        return OK;
    } else if(strcmp(name, AUDIO_HARDWARE_MODULE_ID_REMOTE_SUBMIX) == 0) {
        *device = IDevicesFactory::Device::R_SUBMIX;
        return OK;
    } else if(strcmp(name, AUDIO_HARDWARE_MODULE_ID_STUB) == 0) {
        *device = IDevicesFactory::Device::STUB;
        return OK;
    }
    ALOGE("Invalid device name %s", name);
    return BAD_VALUE;
}

status_t DevicesFactoryHalHidl::openDevice(const char *name, sp<DeviceHalInterface> *device) {
    if (mDevicesFactory == 0) return NO_INIT;
    IDevicesFactory::Device hidlDevice;
    status_t status = nameFromHal(name, &hidlDevice);
    if (status != OK) return status;
    Result retval = Result::NOT_INITIALIZED;
    Return<void> ret = mDevicesFactory->openDevice(
            hidlDevice,
            [&](Result r, const sp<IDevice>& result) {
                retval = r;
                if (retval == Result::OK) {
                    *device = new DeviceHalHidl(result);
                }
            });
    if (ret.isOk()) {
        if (retval == Result::OK) return OK;
        else if (retval == Result::INVALID_ARGUMENTS) return BAD_VALUE;
        else return NO_INIT;
    }
    return FAILED_TRANSACTION;
}

} // namespace android
