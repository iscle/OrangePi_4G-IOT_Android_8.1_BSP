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
#ifndef VENDOR_GOOGLE_CLOCKWORK_LEFTY_V1_0_LEFTY_H
#define VENDOR_GOOGLE_CLOCKWORK_LEFTY_V1_0_LEFTY_H

#include "hubconnection.h"
#undef LIKELY
#undef UNLIKELY
#include <vendor/google_clockwork/lefty/1.0/ILefty.h>

namespace vendor {
namespace google_clockwork {
namespace lefty {
namespace V1_0 {
namespace implementation {

using ::vendor::google_clockwork::lefty::V1_0::ILefty;
using ::android::hardware::Return;
using ::android::hardware::Void;
using ::android::sp;
using ::android::HubConnection;

struct Lefty : public ILefty {
    Lefty();

    // Methods from ::vendor::google_clockwork::lefty::V1_0::ILefty follow.
    Return<void> setLeftyMode(bool enabled) override;

private:
    sp<HubConnection> mHubConnection;
};

extern "C" ILefty* HIDL_FETCH_ILefty(const char* name);

}  // namespace implementation
}  // namespace V1_0
}  // namespace lefty
}  // namespace google_clockwork
}  // namespace vendor

#endif  // VENDOR_GOOGLE_CLOCKWORK_LEFTY_V1_0_LEFTY_H
