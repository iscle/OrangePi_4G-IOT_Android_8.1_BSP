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
#define LOG_TAG "android.hardware.vibrator@1.0-service.marlin"

#include <android/hardware/vibrator/1.0/IVibrator.h>
#include <hidl/HidlSupport.h>
#include <hidl/HidlTransportSupport.h>
#include <utils/Errors.h>
#include <utils/StrongPointer.h>

#include "Vibrator.h"

using android::hardware::configureRpcThreadpool;
using android::hardware::joinRpcThreadpool;
using android::hardware::vibrator::V1_0::IVibrator;
using android::hardware::vibrator::V1_0::implementation::Vibrator;
using namespace android;

static const char *ENABLE_PATH = "/sys/class/timed_output/vibrator/enable";
static const char *AMPLITUDE_PATH = "/sys/class/timed_output/vibrator/voltage_level";

status_t registerVibratorService() {
    std::ofstream enable{ENABLE_PATH};
    if (!enable) {
        int error = errno;
        ALOGE("Failed to open %s (%d): %s", ENABLE_PATH, error, strerror(error));
        return -error;
    }

    std::ofstream amplitude{AMPLITUDE_PATH};
    if (!amplitude) {
        int error = errno;
        ALOGE("Failed to open %s (%d): %s", AMPLITUDE_PATH, error, strerror(error));
        return -error;
    }

    sp<IVibrator> vibrator = new Vibrator(std::move(enable), std::move(amplitude));
    vibrator->registerAsService();
    return OK;
}

int main() {
    configureRpcThreadpool(1, true);
    status_t status = registerVibratorService();

    if (status != OK) {
        return status;
    }

    joinRpcThreadpool();
}
