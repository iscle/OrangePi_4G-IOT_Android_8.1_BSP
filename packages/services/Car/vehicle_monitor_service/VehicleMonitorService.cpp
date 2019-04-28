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

#include <assert.h>

#include <binder/PermissionCache.h>
#include <utils/Errors.h>
#include <utils/SystemClock.h>

#include "VehicleMonitorService.h"

//#define DBG_VERBOSE
#ifdef DBG_VERBOSE
#define LOG_VERBOSE(x...) ALOGD(x)
#else
#define LOG_VERBOSE(x...)
#endif

namespace android {

const nsecs_t monitorInterval = 15000000000; // 15s

VehicleMonitorMessageHandler::VehicleMonitorMessageHandler(const sp<Looper>& looper,
        VehicleMonitorService& service)
    : mLooper(looper),
      mService(service),
      mLastDispatchTime(0) {
    mLooper->sendMessageDelayed(monitorInterval, this, Message(COLLECT_DATA));
}

VehicleMonitorMessageHandler::~VehicleMonitorMessageHandler() {
}

void VehicleMonitorMessageHandler::dump(String8& msg) {
    msg.appendFormat("mLastDispatchTime:%" PRId64 "\n", mLastDispatchTime);
    mProcessMonitor.dump(msg);
}

void VehicleMonitorMessageHandler::doHandleCollectData() {
    {
        std::lock_guard<std::mutex> autoLock(mLock);
        mLastDispatchTime = elapsedRealtime();
        mProcessMonitor.process();
    }

    // TODO: do better timing for sendMessage
    mLooper->sendMessageDelayed(monitorInterval, this, Message(COLLECT_DATA));
}

void VehicleMonitorMessageHandler::handleMessage(const Message& message) {
    switch (message.what) {
    case COLLECT_DATA:
        doHandleCollectData();
        break;
    default:
        // TODO?
        break;
    }
}

// ----------------------------------------------------
VehicleMonitorService* VehicleMonitorService::sInstance = NULL;

status_t VehicleMonitorService::dump(int fd, const Vector<String16>& /*args*/) {
    static const String16 sDump("android.permission.DUMP");
    String8 msg;
    if (!PermissionCache::checkCallingPermission(sDump)) {
        msg.appendFormat("Permission Denial: "
                         "can't dump VMS from pid=%d, uid=%d\n",
                         IPCThreadState::self()->getCallingPid(),
                         IPCThreadState::self()->getCallingUid());
        write(fd, msg.string(), msg.size());
        return NO_ERROR;
    }
    msg.appendFormat("*Handler, now in ms:%" PRId64 "\n", elapsedRealtime());
    mHandler->dump(msg);
    write(fd, msg.string(), msg.size());
    return NO_ERROR;
}

VehicleMonitorService::VehicleMonitorService() {
    sInstance = this;
}

VehicleMonitorService::~VehicleMonitorService() {
    sInstance = NULL;
}

void VehicleMonitorService::binderDied(const wp<IBinder>& who) {
    std::lock_guard<std::mutex> autoLock(mLock);
    sp<IBinder> ibinder = who.promote();
    ibinder->unlinkToDeath(this);
    // TODO: reset all priorities set by CarService.
}

void VehicleMonitorService::release() {
    std::lock_guard<std::mutex> autoLock(mLock);
    mHandlerThread->quit();
}

void VehicleMonitorService::onFirstRef() {
    std::lock_guard<std::mutex> autoLock(mLock);
    mHandlerThread = new HandlerThread();
    status_t r = mHandlerThread->start("VMS.NATIVE_LOOP");
    if (r != NO_ERROR) {
        ALOGE("cannot start handler thread, error:%d", r);
        return;
    }
    sp<VehicleMonitorMessageHandler> handler(
            new VehicleMonitorMessageHandler(mHandlerThread->getLooper(), *this));
    assert(handler.get() != NULL);
    mHandler = handler;
}

status_t VehicleMonitorService::setAppPriority(uint32_t, uint32_t, vehicle_app_priority) {
    //TODO
    return NO_ERROR;
}

status_t VehicleMonitorService::setMonitorListener(
        const sp<IVehicleMonitorListener> &listener) {
    sp<IBinder> ibinder = IInterface::asBinder(listener);
    LOG_VERBOSE("setMonitorListener, binder 0x%x", ibinder.get());
    //TODO
    return NO_ERROR;
}

}; // namespace android
