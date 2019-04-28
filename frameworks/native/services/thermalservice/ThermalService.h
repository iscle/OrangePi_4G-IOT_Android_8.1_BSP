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

#ifndef ANDROID_THERMALSERVICE_THERMALSERVICE_H
#define ANDROID_THERMALSERVICE_THERMALSERVICE_H

#include <android/os/BnThermalService.h>
#include <android/os/IThermalEventListener.h>
#include <android/os/Temperature.h>
#include <utils/Mutex.h>
#include <utils/String16.h>
#include <utils/Vector.h>

namespace android {
namespace os {

class ThermalService : public BnThermalService,
                       public IBinder::DeathRecipient {
public:
  ThermalService() : mThrottled(false) {};
    void publish(const sp<ThermalService>& service);
    binder::Status notifyThrottling(
        const bool isThrottling, const Temperature& temperature);

private:
    Mutex mListenersLock;
    Vector<sp<IThermalEventListener> > mListeners;
    bool mThrottled;
    Temperature mThrottleTemperature;

    binder::Status registerThermalEventListener(
        const sp<IThermalEventListener>& listener);
    binder::Status unregisterThermalEventListener(
        const sp<IThermalEventListener>& listener);
    binder::Status isThrottling(bool* _aidl_return);
    void binderDied(const wp<IBinder>& who);
};

};  // namespace os
};  // namespace android

#endif // ANDROID_THERMALSERVICE_THERMALSERVICE_H
