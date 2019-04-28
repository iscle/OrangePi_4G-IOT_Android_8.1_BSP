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
#ifndef ANDROID_HARDWARE_BROADCASTRADIO_V1_1_BROADCASTRADIOFACTORY_H
#define ANDROID_HARDWARE_BROADCASTRADIO_V1_1_BROADCASTRADIOFACTORY_H

#include <android/hardware/broadcastradio/1.1/IBroadcastRadio.h>
#include <android/hardware/broadcastradio/1.1/IBroadcastRadioFactory.h>
#include <android/hardware/broadcastradio/1.1/types.h>

namespace android {
namespace hardware {
namespace broadcastradio {
namespace V1_1 {
namespace implementation {

extern "C" IBroadcastRadioFactory* HIDL_FETCH_IBroadcastRadioFactory(const char* name);

struct BroadcastRadioFactory : public IBroadcastRadioFactory {
    BroadcastRadioFactory();

    // V1_0::IBroadcastRadioFactory methods
    Return<void> connectModule(V1_0::Class classId, connectModule_cb _hidl_cb) override;

   private:
    std::map<V1_0::Class, sp<IBroadcastRadio>> mRadioModules;
};

}  // namespace implementation
}  // namespace V1_1
}  // namespace broadcastradio
}  // namespace hardware
}  // namespace android

#endif  // ANDROID_HARDWARE_BROADCASTRADIO_V1_1_BROADCASTRADIOFACTORY_H
