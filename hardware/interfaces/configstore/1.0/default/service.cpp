/*
 * Copyright (C) 2017 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.1 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.1
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

#define LOG_TAG "android.hardware.configstore@1.0-service"

#include <android/hardware/configstore/1.0/ISurfaceFlingerConfigs.h>
#include <hidl/HidlTransportSupport.h>
#include <hwminijail/HardwareMinijail.h>

#include "SurfaceFlingerConfigs.h"

using android::hardware::configureRpcThreadpool;
using android::hardware::joinRpcThreadpool;
using android::hardware::configstore::V1_0::ISurfaceFlingerConfigs;
using android::hardware::configstore::V1_0::implementation::SurfaceFlingerConfigs;
using android::hardware::SetupMinijail;
using android::sp;
using android::status_t;
using android::OK;

int main() {
    configureRpcThreadpool(10, true);

    SetupMinijail("/vendor/etc/seccomp_policy/configstore@1.0.policy");

    sp<ISurfaceFlingerConfigs> surfaceFlingerConfigs = new SurfaceFlingerConfigs;
    status_t status = surfaceFlingerConfigs->registerAsService();
    LOG_ALWAYS_FATAL_IF(status != OK, "Could not register ISurfaceFlingerConfigs");

    // other interface registration comes here
    joinRpcThreadpool();
    return 0;
}
