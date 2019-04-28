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

#define LOG_TAG "android.hardware.sensors@1.0-service"

#include <android/hardware/sensors/1.0/ISensors.h>
#include <hidl/LegacySupport.h>

using android::hardware::sensors::V1_0::ISensors;
using android::hardware::defaultPassthroughServiceImplementation;

int main() {
    /* Sensors framework service needs at least two threads.
     * One thread blocks on a "poll"
     * The second thread is needed for all other HAL methods.
     */
    return defaultPassthroughServiceImplementation<ISensors>(2);
}
