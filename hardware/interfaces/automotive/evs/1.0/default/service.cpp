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

#define LOG_TAG "android.hardware.automotive.evs@1.0-service"

#include <unistd.h>

#include <hidl/HidlTransportSupport.h>
#include <log/log.h>
#include <utils/Errors.h>
#include <utils/StrongPointer.h>

#include "ServiceNames.h"
#include "EvsEnumerator.h"
#include "EvsDisplay.h"


// libhidl:
using android::hardware::configureRpcThreadpool;
using android::hardware::joinRpcThreadpool;

// Generated HIDL files
using android::hardware::automotive::evs::V1_0::IEvsEnumerator;
using android::hardware::automotive::evs::V1_0::IEvsDisplay;

// The namespace in which all our implementation code lives
using namespace android::hardware::automotive::evs::V1_0::implementation;
using namespace android;


int main() {
    ALOGI("EVS Hardware Enumerator service is starting");
    android::sp<IEvsEnumerator> service = new EvsEnumerator();

    configureRpcThreadpool(1, true /* callerWillJoin */);

    // Register our service -- if somebody is already registered by our name,
    // they will be killed (their thread pool will throw an exception).
    status_t status = service->registerAsService(kEnumeratorServiceName);
    if (status == OK) {
        ALOGD("%s is ready.", kEnumeratorServiceName);
        joinRpcThreadpool();
    } else {
        ALOGE("Could not register service %s (%d).", kEnumeratorServiceName, status);
    }

    // In normal operation, we don't expect the thread pool to exit
    ALOGE("EVS Hardware Enumerator is shutting down");
    return 1;
}
