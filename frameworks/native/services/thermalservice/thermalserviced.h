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

#ifndef ANDROID_THERMALSERVICE_THERMALSERVICED_H
#define ANDROID_THERMALSERVICE_THERMALSERVICED_H

#include "ThermalService.h"
#include "libthermalcallback/ThermalCallback.h"

using namespace android;
using ::android::hardware::thermal::V1_0::Temperature;
using ::android::hardware::thermal::V1_1::implementation::ThermalCallback;
using ::android::os::ThermalService;

class ThermalServiceDaemon {
 public:
    void thermalServiceStartup();
    void thermalCallbackStartup();
    void getThermalHal();
    ThermalServiceDaemon() {};

 private:
    sp<ThermalService> mThermalService;
    sp<ThermalCallback> mThermalCallback;
};

#endif  // ANDROID_THERMALSERVICE_THERMALSERVICED_H
