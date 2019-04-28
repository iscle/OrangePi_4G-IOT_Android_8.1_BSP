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

#ifndef ANDROID_IVEHICLE_MONITOR_H
#define ANDROID_IVEHICLE_MONITOR_H

#include <binder/Parcel.h>

#include <IVehicleMonitorListener.h>

namespace android {

// ----------------------------------------------------------------------------

/**
 * Application priorities used in vehicle monitoring.
 */
enum vehicle_app_priority {
    VEHICLE_APP_PRIORITY_NONE = 0,
    VEHICLE_APP_PRIORITY_FOREGROUND = 1,
};

// ----------------------------------------------------------------------------

class IVehicleMonitor : public IInterface {
public:
    static const char SERVICE_NAME[];
    DECLARE_META_INTERFACE(VehicleMonitor);

    virtual status_t setAppPriority(
            uint32_t pid, uint32_t uid, vehicle_app_priority priority) = 0;
    virtual status_t setMonitorListener(
            const sp<IVehicleMonitorListener> &listener) = 0;
};
// ----------------------------------------------------------------------------

class BnVehicleMonitor : public BnInterface<IVehicleMonitor> {
    virtual status_t  onTransact(uint32_t code,
                                 const Parcel& data,
                                 Parcel* reply,
                                 uint32_t flags = 0);
};

// ----------------------------------------------------------------------------
}; // namespace android

#endif /* ANDROID_IVEHICLE_MONITOR_H */
