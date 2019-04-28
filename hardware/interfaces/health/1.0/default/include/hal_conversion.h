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

#ifndef HARDWARE_INTERFACES_HEALTH_V1_0_DEFAULT_INCLUDE_HAL_CONVERSION_H_
#define HARDWARE_INTERFACES_HEALTH_V1_0_DEFAULT_INCLUDE_HAL_CONVERSION_H_

#include <android/hardware/health/1.0/IHealth.h>
#include <healthd/healthd.h>

namespace android {
namespace hardware {
namespace health {
namespace V1_0 {
namespace hal_conversion {

void convertToHealthConfig(const struct healthd_config *hc,
                            HealthConfig& config);
void convertFromHealthConfig(const HealthConfig& c, struct healthd_config *hc);

void convertToHealthInfo(const struct android::BatteryProperties *p,
                                 HealthInfo& info);
void convertFromHealthInfo(const HealthInfo& info,
                                 struct android::BatteryProperties *p);

}  // namespace hal_conversion
}  // namespace V1_0
}  // namespace sensors
}  // namespace hardware
}  // namespace android

#endif  // HARDWARE_INTERFACES_HEALTH_V1_0_DEFAULT_INCLUDE_HAL_CONVERSION_H_
