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

#ifndef SUPPLICANT_HIDL_TEST_UTILS_H
#define SUPPLICANT_HIDL_TEST_UTILS_H

#include <android/hardware/wifi/supplicant/1.0/ISupplicant.h>
#include <android/hardware/wifi/supplicant/1.0/ISupplicantP2pIface.h>
#include <android/hardware/wifi/supplicant/1.0/ISupplicantStaIface.h>
#include <android/hardware/wifi/supplicant/1.0/ISupplicantStaNetwork.h>

// Used to stop the android wifi framework before every test.
void stopWifiFramework();
void startWifiFramework();
void stopSupplicant();
// Used to configure the chip, driver and start wpa_supplicant before every
// test.
void startSupplicantAndWaitForHidlService();

// Helper functions to obtain references to the various HIDL interface objects.
// Note: We only have a single instance of each of these objects currently.
// These helper functions should be modified to return vectors if we support
// multiple instances.
android::sp<android::hardware::wifi::supplicant::V1_0::ISupplicant>
getSupplicant();
android::sp<android::hardware::wifi::supplicant::V1_0::ISupplicantStaIface>
getSupplicantStaIface();
android::sp<android::hardware::wifi::supplicant::V1_0::ISupplicantStaNetwork>
createSupplicantStaNetwork();
android::sp<android::hardware::wifi::supplicant::V1_0::ISupplicantP2pIface>
getSupplicantP2pIface();

bool turnOnExcessiveLogging();

#endif /* SUPPLICANT_HIDL_TEST_UTILS_H */
