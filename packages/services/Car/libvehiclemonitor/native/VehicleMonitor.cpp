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
#define LOG_TAG "VehicleMonitor.Lib"

#include <assert.h>
#include <binder/IServiceManager.h>
#include <binder/ProcessState.h>

#include <VehicleMonitor.h>

namespace android {
// ----------------------------------------------------------------------------

static const int MAX_SERVICE_RETRY = 4;

sp<VehicleMonitor> VehicleMonitor::createVehicleMonitor() {
    sp<IBinder> binder;
    int retry = 0;
    while (true) {
        binder = defaultServiceManager()->getService(String16(IVehicleMonitor::SERVICE_NAME));
        if (binder.get() != NULL) {
            break;
        }
        retry++;
        if (retry > MAX_SERVICE_RETRY) {
            ALOGE("cannot get VMS, will crash");
            break;
        }
    }
    assert(binder.get() != NULL);
    sp<IVehicleMonitor> ivm(interface_cast<IVehicleMonitor>(binder));
    sp<VehicleMonitor> vm;
    vm = new VehicleMonitor(ivm);
    assert(vm.get() != NULL);
    // in case thread pool is not started, start it.
    ProcessState::self()->startThreadPool();
    return vm;
}

VehicleMonitor::VehicleMonitor(sp<IVehicleMonitor>& vehicleMonitor) :
        mService(vehicleMonitor) {
}

VehicleMonitor::~VehicleMonitor() {
    sp<IVehicleMonitor> service = getService();
    IInterface::asBinder(service)->unlinkToDeath(this);
}

void VehicleMonitor::onFirstRef() {
    sp<IVehicleMonitor> service = getService();
    IInterface::asBinder(service)->linkToDeath(this);
}

status_t VehicleMonitor::setAppPriority(
        uint32_t pid, uint32_t uid, vehicle_app_priority priority) {
    return getService()->setAppPriority(pid, uid, priority);
}

void VehicleMonitor::binderDied(const wp<IBinder>& who) {
    ALOGE("service died");
    {
        Mutex::Autolock autoLock(mLock);
        sp<IBinder> ibinder = who.promote();
        ibinder->unlinkToDeath(this);
        sp<IBinder> binder = defaultServiceManager()->getService(
                String16(IVehicleMonitor::SERVICE_NAME));
        mService = interface_cast<IVehicleMonitor>(binder);
        IInterface::asBinder(mService)->linkToDeath(this);
    };
}

sp<IVehicleMonitor> VehicleMonitor::getService() {
    Mutex::Autolock autoLock(mLock);
    return mService;
}

}; // namespace android
