/*
 * Copyright (C) 2015 The Android Open Source Project
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

#ifndef ANDROID_IVEHICLE_MONITOR_LISTENER_H
#define ANDROID_IVEHICLE_MONITOR_LISTENER_H

#include <binder/Parcel.h>

namespace android {

// ----------------------------------------------------------------------------

class IVehicleMonitorListener : public IInterface
{
public:
    DECLARE_META_INTERFACE(VehicleMonitorListener);

    /**
     * Notifies when app misbehaved. Client (Bn implementor) should
     * hold sp to keep the data received outside this call.
     */
    virtual void onAppViolation(
            int32_t pid, int32_t uid, int32_t action, int32_t violation) = 0;
};

// ----------------------------------------------------------------------------

class BnVehicleMonitorListener : public BnInterface<IVehicleMonitorListener>
{
    virtual status_t  onTransact(uint32_t code,
                                 const Parcel& data,
                                 Parcel* reply,
                                 uint32_t flags = 0);
};

// ----------------------------------------------------------------------------

}; // namespace android

#endif /* ANDROID_IVEHICLE_MONITOR_LISTENER_H */
