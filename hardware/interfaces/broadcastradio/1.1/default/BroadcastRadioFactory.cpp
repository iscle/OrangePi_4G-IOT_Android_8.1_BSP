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
#define LOG_TAG "BroadcastRadioDefault.factory"
#define LOG_NDEBUG 0

#include "BroadcastRadioFactory.h"

#include "BroadcastRadio.h"

#include <log/log.h>

namespace android {
namespace hardware {
namespace broadcastradio {
namespace V1_1 {
namespace implementation {

using V1_0::Class;

using std::vector;

static const vector<Class> gAllClasses = {
    Class::AM_FM, Class::SAT, Class::DT,
};

IBroadcastRadioFactory* HIDL_FETCH_IBroadcastRadioFactory(const char* name __unused) {
    return new BroadcastRadioFactory();
}

BroadcastRadioFactory::BroadcastRadioFactory() {
    for (auto&& classId : gAllClasses) {
        if (!BroadcastRadio::isSupported(classId)) continue;
        mRadioModules[classId] = new BroadcastRadio(classId);
    }
}

Return<void> BroadcastRadioFactory::connectModule(Class classId, connectModule_cb _hidl_cb) {
    ALOGV("%s(%s)", __func__, toString(classId).c_str());

    auto moduleIt = mRadioModules.find(classId);
    if (moduleIt == mRadioModules.end()) {
        _hidl_cb(Result::INVALID_ARGUMENTS, nullptr);
    } else {
        _hidl_cb(Result::OK, moduleIt->second);
    }

    return Void();
}

}  // namespace implementation
}  // namespace V1_1
}  // namespace broadcastradio
}  // namespace hardware
}  // namespace android
