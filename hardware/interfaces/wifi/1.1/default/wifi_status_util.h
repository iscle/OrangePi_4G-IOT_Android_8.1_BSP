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

#ifndef WIFI_STATUS_UTIL_H_
#define WIFI_STATUS_UTIL_H_

#include <android/hardware/wifi/1.0/IWifi.h>

#include "wifi_legacy_hal.h"

namespace android {
namespace hardware {
namespace wifi {
namespace V1_1 {
namespace implementation {
using namespace android::hardware::wifi::V1_0;

std::string legacyErrorToString(legacy_hal::wifi_error error);
WifiStatus createWifiStatus(WifiStatusCode code,
                            const std::string& description);
WifiStatus createWifiStatus(WifiStatusCode code);
WifiStatus createWifiStatusFromLegacyError(legacy_hal::wifi_error error,
                                           const std::string& description);
WifiStatus createWifiStatusFromLegacyError(legacy_hal::wifi_error error);

}  // namespace implementation
}  // namespace V1_1
}  // namespace wifi
}  // namespace hardware
}  // namespace android

#endif  // WIFI_STATUS_UTIL_H_
