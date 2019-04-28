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

#ifndef ANDROID_VEHICLE_MONITOR_H
#define ANDROID_VEHICLE_MONITOR_H

#include <binder/IInterface.h>

#include <utils/Mutex.h>

#include "IVehicleMonitor.h"

namespace android {

// ----------------------------------------------------------------------------

/**
 * Vehicle monitor API for low level components like HALs to access
 * monitoring service.
 * This is reference counted. So use with sp<>.
 */
class VehicleMonitor : public IBinder::DeathRecipient {
public:
    /**
     * Factory method for VehicleMonitor. Client should use this method
     * to create a new instance.
     */
    static sp<VehicleMonitor> createVehicleMonitor();

    virtual ~VehicleMonitor();

    status_t setAppPriority(
            uint32_t pid, uint32_t uid, vehicle_app_priority priority);

    //IBinder::DeathRecipient, not for client
    void binderDied(const wp<IBinder>& who);

private:
    VehicleMonitor(sp<IVehicleMonitor>& vehicleMonitor);
    // RefBase
    virtual void onFirstRef();
    sp<IVehicleMonitor> getService();

private:
    sp<IVehicleMonitor> mService;
    Mutex mLock;
};

// ----------------------------------------------------------------------------

}; // namespace android

#endif /* ANDROID_VEHICLE_MONITOR_H */
