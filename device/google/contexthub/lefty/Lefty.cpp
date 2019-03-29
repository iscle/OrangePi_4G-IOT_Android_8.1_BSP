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
#define LOG_TAG "Lefty"
#include <utils/Log.h>

#include "Lefty.h"

namespace vendor {
namespace google_clockwork {
namespace lefty {
namespace V1_0 {
namespace implementation {

Lefty::Lefty() : mHubConnection(HubConnection::getInstance()) {
    ALOGI("Created a Lefty interface instance");
}

// Methods from ::vendor::google_clockwork::lefty::V1_0::ILefty follow.
Return<void> Lefty::setLeftyMode(bool enabled) {
    if (mHubConnection->initCheck() == ::android::OK
            && mHubConnection->getAliveCheck() == ::android::OK) {
        mHubConnection->setLeftyMode(enabled);
    }
    return Void();
}


ILefty* HIDL_FETCH_ILefty(const char* /* name */) {
    return new Lefty();
}

}  // namespace implementation
}  // namespace V1_0
}  // namespace lefty
}  // namespace google_clockwork
}  // namespace vendor
