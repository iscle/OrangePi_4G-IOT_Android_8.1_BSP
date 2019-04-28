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



#define LOG_TAG "VehicleMonitorListener"

#include <memory>

#include <utils/Errors.h>
#include <utils/Log.h>

#include <IVehicleMonitorListener.h>

namespace android {

enum {
    ON_APP_VIOLATION = IBinder::FIRST_CALL_TRANSACTION,
};

class BpVehicleMonitorListener : public BpInterface<IVehicleMonitorListener>
{
  public:
    explicit BpVehicleMonitorListener(const sp<IBinder> & impl)
            : BpInterface<IVehicleMonitorListener>(impl) {
    }

    virtual void onAppViolation(
            int32_t pid, int32_t uid, int32_t action, int32_t violation) {
        Parcel data, reply;
        data.writeInterfaceToken(IVehicleMonitorListener::getInterfaceDescriptor());
        data.writeInt32(1);
        data.writeInt32(pid);
        data.writeInt32(uid);
        data.writeInt32(action);
        data.writeInt32(violation);
        remote()->transact(ON_APP_VIOLATION, data, &reply, IBinder::FLAG_ONEWAY);
    }
};

IMPLEMENT_META_INTERFACE(VehicleMonitorListener, "com.android.car.vehiclemonitor.IVehicleMonitorListener");

// ----------------------------------------------------------------------

status_t BnVehicleMonitorListener::onTransact(uint32_t code, const Parcel& data, Parcel* reply,
                                              uint32_t flags) {
    status_t r;
    switch (code) {
        case ON_APP_VIOLATION: {
            CHECK_INTERFACE(IVehicleMonitorListener, data, reply);
            if (data.readInt32() == 0) { // java side allows passing null with this.
                return BAD_VALUE;
            }
            int32_t pid = data.readInt32();
            int32_t uid = data.readInt32();
            int32_t action = data.readInt32();
            int32_t violation = data.readInt32();
            onAppViolation(pid, uid, action, violation);
            return NO_ERROR;
        } break;
        default:
            return BBinder::onTransact(code, data, reply, flags);
    }
}

}; // namespace android
