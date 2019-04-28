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

#define LOG_TAG "VehicleMonitor"

#include <binder/IPCThreadState.h>
#include <binder/Status.h>
#include <private/android_filesystem_config.h>

#include <utils/Log.h>

#include <IVehicleMonitor.h>

namespace android {
enum {
    SET_APP_PRIORITY = IBinder::FIRST_CALL_TRANSACTION,
    SET_MONITOR_LISTENER,
};

const char IVehicleMonitor::SERVICE_NAME[] = "com.android.car.vehiclemonitor.IVehicleMonitor";

// ----------------------------------------------------------------------------

class BpVehicleMonitor : public BpInterface<IVehicleMonitor> {
public:
    explicit BpVehicleMonitor(const sp<IBinder> & impl)
            : BpInterface<IVehicleMonitor>(impl) {
    }

    virtual status_t setAppPriority(
            uint32_t pid, uint32_t uid, vehicle_app_priority priority) {
        Parcel data, reply;
        data.writeInterfaceToken(IVehicleMonitor::getInterfaceDescriptor());
        data.writeInt32(1);
        data.writeInt32(pid);
        data.writeInt32(uid);
        data.writeInt32(priority);
        status_t status = remote()->transact(SET_APP_PRIORITY, data, &reply);
        if (status == NO_ERROR) {
            int32_t exceptionCode = reply.readExceptionCode();
            if (exceptionCode != NO_ERROR) {
                if (exceptionCode == binder::Status::EX_SERVICE_SPECIFIC) {
                    return -EAGAIN;
                } else if (exceptionCode == binder::Status::EX_ILLEGAL_STATE) {
                    return -ESHUTDOWN;
                }
                return exceptionCode;
            }
        }
        return status;
    }

    virtual status_t setMonitorListener(
            const sp<IVehicleMonitorListener> &listener) {
        Parcel data, reply;
        data.writeInterfaceToken(IVehicleMonitor::getInterfaceDescriptor());
        data.writeStrongBinder(IInterface::asBinder(listener));
        status_t status = remote()->transact(SET_MONITOR_LISTENER, data, &reply);
        return status;
    }
};

IMPLEMENT_META_INTERFACE(VehicleMonitor, IVehicleMonitor::SERVICE_NAME);

// ----------------------------------------------------------------------

static bool isSystemUser() {
    uid_t uid =  IPCThreadState::self()->getCallingUid();
    switch (uid) {
        // This list will be expanded. Only these UIDs are allowed to access vehicle monitor.
        case AID_ROOT:
        case AID_SYSTEM: {
            return true;
        } break;
        default: {
            ALOGE("non-system user tried access, uid %d", uid);
        } break;
    }
    return false;
}

status_t BnVehicleMonitor::onTransact(uint32_t code, const Parcel& data, Parcel* reply,
        uint32_t flags) {
    if (!isSystemUser()) {
        return PERMISSION_DENIED;
    }
    status_t r;
    switch (code) {
        case SET_APP_PRIORITY: {
            CHECK_INTERFACE(IVehicleMonitor, data, reply);
            if (data.readInt32() == 0) { // no data
                ALOGE("null data");
                return BAD_VALUE;
            }
            int32_t pid = data.readInt32();
            int32_t uid = data.readInt32();
            int32_t priority = data.readInt32();
            r = setAppPriority(pid, uid, (vehicle_app_priority) priority);
            reply->writeNoException();
            return r;
        } break;
        case SET_MONITOR_LISTENER: {
            CHECK_INTERFACE(IVehicleMonitor, data, reply);
            sp<IVehicleMonitorListener> listener =
                    interface_cast<IVehicleMonitorListener>(data.readStrongBinder());
            r = setMonitorListener(listener);
            reply->writeNoException();
            return r;
        } break;
        default:
            return BBinder::onTransact(code, data, reply, flags);
    }
}

}; // namespace android
