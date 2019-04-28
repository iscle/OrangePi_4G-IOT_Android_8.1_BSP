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
#ifndef CAR_VEHICLE_MONITOR_SERVICE_H_
#define CAR_VEHICLE_MONITOR_SERVICE_H_

#include <mutex>
#include <inttypes.h>
#include <cutils/compiler.h>

#include <binder/BinderService.h>
#include <binder/IBinder.h>
#include <utils/String8.h>

#include <ProcessMonitor.h>
#include <IVehicleMonitor.h>
#include <IVehicleMonitorListener.h>
#include <HandlerThread.h>

namespace android {
// ----------------------------------------------------------------------------

class VehicleMonitorService;

/**
 * MessageHandler to handle periodic data processing.
 * Init / release is handled in the handler thread to allow upper layer to
 * allocate resource for the thread.
 */
class VehicleMonitorMessageHandler : public MessageHandler {
    enum {
        COLLECT_DATA = 0,
    };

public:
    // not passing VMS as sp as this is held by VMS always.
    VehicleMonitorMessageHandler(
            const sp<Looper>& mLooper, VehicleMonitorService& service);
    virtual ~VehicleMonitorMessageHandler();

    void dump(String8& msg);

private:
    void handleMessage(const Message& message);
    void doHandleCollectData();

private:
    mutable std::mutex mLock;
    const sp<Looper> mLooper;
    ProcessMonitor mProcessMonitor;
    VehicleMonitorService& mService;
    int64_t mLastDispatchTime;
};

// ----------------------------------------------------------------------------
class VehicleMonitorService :
    public BinderService<VehicleMonitorService>,
    public BnVehicleMonitor,
    public IBinder::DeathRecipient {
public:
    static const char* getServiceName() ANDROID_API {
        return IVehicleMonitor::SERVICE_NAME;
    };

    VehicleMonitorService();
    ~VehicleMonitorService();
    virtual status_t dump(int fd, const Vector<String16>& args);
    void release();
    virtual void binderDied(const wp<IBinder>& who);
    virtual status_t setAppPriority(
            uint32_t pid, uint32_t uid, vehicle_app_priority priority);
    virtual status_t setMonitorListener(
            const sp<IVehicleMonitorListener> &listener);

private:
    // RefBase
    virtual void onFirstRef();
private:
    static VehicleMonitorService* sInstance;
    sp<HandlerThread> mHandlerThread;
    sp<VehicleMonitorMessageHandler> mHandler;
    mutable std::mutex mLock;
};

}

#endif /* CAR_VEHICLE_MONITOR_SERVICE_H_ */
