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
#include "BroadcastRadioFactory.h"
#include "BroadcastRadio.h"

namespace android {
namespace hardware {
namespace broadcastradio {
namespace V1_0 {
namespace implementation {

// Methods from ::android::hardware::broadcastradio::V1_0::IBroadcastRadioFactory follow.
Return<void> BroadcastRadioFactory::connectModule(Class classId, connectModule_cb _hidl_cb)  {
    sp<BroadcastRadio> impl = new BroadcastRadio(classId);
    Result retval = Result::NOT_INITIALIZED;
    if (impl != 0) {
        retval = impl->initCheck();
    }
    _hidl_cb(retval, impl);
    return Void();
}


IBroadcastRadioFactory* HIDL_FETCH_IBroadcastRadioFactory(const char* /* name */) {
    return new BroadcastRadioFactory();
}

} // namespace implementation
}  // namespace V1_0
}  // namespace broadcastradio
}  // namespace hardware
}  // namespace android
