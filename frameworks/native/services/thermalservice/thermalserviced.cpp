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

#define LOG_TAG "thermalserviced"
#include <log/log.h>

#include "thermalserviced.h"
#include "ThermalService.h"
#include "libthermalcallback/ThermalCallback.h"

#include <android/hardware/thermal/1.1/IThermal.h>
#include <binder/IPCThreadState.h>
#include <binder/IServiceManager.h>
#include <hidl/HidlTransportSupport.h>

using namespace android;
using ::android::hardware::thermal::V1_1::IThermal;
using ::android::hardware::thermal::V1_0::Temperature;
using ::android::hardware::thermal::V1_1::IThermalCallback;
using ::android::hardware::thermal::V1_1::implementation::ThermalCallback;
using ::android::hardware::configureRpcThreadpool;
using ::android::hardware::hidl_death_recipient;
using ::android::hidl::base::V1_0::IBase;
using ::android::os::ThermalService;

template<typename T>
using Return = hardware::Return<T>;

namespace {

// Our thermalserviced main object
ThermalServiceDaemon* gThermalServiceDaemon;

// Thermal HAL client
sp<IThermal> gThermalHal = nullptr;

// Binder death notifier informing of Thermal HAL death.
struct ThermalServiceDeathRecipient : hidl_death_recipient {
    virtual void serviceDied(
        uint64_t cookie __unused, const wp<IBase>& who __unused) {
        gThermalHal = nullptr;
        ALOGE("IThermal HAL died");
        gThermalServiceDaemon->getThermalHal();
    }
};

sp<ThermalServiceDeathRecipient> gThermalHalDied = nullptr;

}  // anonymous namespace

void ThermalServiceDaemon::thermalServiceStartup() {
    // Binder IThermalService startup
    mThermalService = new android::os::ThermalService;
    mThermalService->publish(mThermalService);
    // Register IThermalService object with IThermalCallback
    if (mThermalCallback != nullptr)
        mThermalCallback->registerThermalService(mThermalService);
    IPCThreadState::self()->joinThreadPool();
}

// Lookup Thermal HAL, register death notifier, register our
// ThermalCallback with the Thermal HAL.
void ThermalServiceDaemon::getThermalHal() {
    gThermalHal = IThermal::getService();
    if (gThermalHal == nullptr) {
        ALOGW("Unable to get Thermal HAL V1.1, vendor thermal event notification not available");
        return;
    }

    // Binder death notifier for Thermal HAL
    if (gThermalHalDied == nullptr)
        gThermalHalDied = new ThermalServiceDeathRecipient();

    if (gThermalHalDied != nullptr)
        gThermalHal->linkToDeath(gThermalHalDied, 0x451F /* cookie */);

    if (mThermalCallback != nullptr) {
        Return<void> ret = gThermalHal->registerThermalCallback(
            mThermalCallback);
        if (!ret.isOk())
            ALOGE("registerThermalCallback failed, status: %s",
                  ret.description().c_str());
    }
}

void ThermalServiceDaemon::thermalCallbackStartup() {
    // HIDL IThermalCallback startup
    // Need at least 2 threads in thread pool since we wait for dead HAL
    // to come back on the binder death notification thread and we need
    // another thread for the incoming service now available call.
    configureRpcThreadpool(2, false /* callerWillJoin */);
    mThermalCallback = new ThermalCallback();
    // Lookup Thermal HAL and register our ThermalCallback.
    getThermalHal();
}

int main(int /*argc*/, char** /*argv*/) {
    gThermalServiceDaemon = new ThermalServiceDaemon();
    gThermalServiceDaemon->thermalCallbackStartup();
    gThermalServiceDaemon->thermalServiceStartup();
    /* NOTREACHED */
}
