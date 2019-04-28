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

#ifndef WIFI_LEGACY_HAL_STUBS_H_
#define WIFI_LEGACY_HAL_STUBS_H_

namespace android {
namespace hardware {
namespace wifi {
namespace V1_1 {
namespace implementation {
namespace legacy_hal {
#include <hardware_legacy/wifi_hal.h>

bool initHalFuncTableWithStubs(wifi_hal_fn* hal_fn);
}  // namespace legacy_hal
}  // namespace implementation
}  // namespace V1_1
}  // namespace wifi
}  // namespace hardware
}  // namespace android

#endif  // WIFI_LEGACY_HAL_STUBS_H_
