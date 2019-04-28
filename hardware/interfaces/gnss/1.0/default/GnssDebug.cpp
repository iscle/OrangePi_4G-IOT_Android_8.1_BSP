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

#define LOG_TAG "GnssHAL_GnssDebugInterface"

#include <log/log.h>

#include "GnssDebug.h"

namespace android {
namespace hardware {
namespace gnss {
namespace V1_0 {
namespace implementation {

GnssDebug::GnssDebug(const GpsDebugInterface* gpsDebugIface) : mGnssDebugIface(gpsDebugIface) {}

// Methods from ::android::hardware::gnss::V1_0::IGnssDebug follow.
Return<void> GnssDebug::getDebugData(getDebugData_cb _hidl_cb)  {
    /*
     * This is a new interface and hence there is no way to retrieve the
     * debug data from the HAL.
     */
    DebugData data = {};

    _hidl_cb(data);

    /*
     * Log the debug data sent from the conventional Gnss HAL. This code is
     * moved here from GnssLocationProvider.
     */
    if (mGnssDebugIface) {
        char buffer[kMaxDebugStrLen + 1];
        size_t length = mGnssDebugIface->get_internal_state(buffer, kMaxDebugStrLen);
        length = std::max(length, kMaxDebugStrLen);
        buffer[length] = '\0';
        ALOGD("Gnss Debug Data: %s", buffer);
    }
    return Void();
}

}  // namespace implementation
}  // namespace V1_0
}  // namespace gnss
}  // namespace hardware
}  // namespace android
