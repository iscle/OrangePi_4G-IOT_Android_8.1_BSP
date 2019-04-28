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

#include <android-base/logging.h>
#include <hidl/HidlTransportSupport.h>
#include <utils/Looper.h>
#include <utils/StrongPointer.h>

#include "wifi.h"

using android::hardware::configureRpcThreadpool;
using android::hardware::joinRpcThreadpool;

int main(int /*argc*/, char** argv) {
  android::base::InitLogging(argv,
                             android::base::LogdLogger(android::base::SYSTEM));
  LOG(INFO) << "Wifi Hal is booting up...";

  configureRpcThreadpool(1, true /* callerWillJoin */);

  // Setup hwbinder service
  android::sp<android::hardware::wifi::V1_1::IWifi> service =
      new android::hardware::wifi::V1_1::implementation::Wifi();
  CHECK_EQ(service->registerAsService(), android::NO_ERROR)
      << "Failed to register wifi HAL";

  joinRpcThreadpool();

  LOG(INFO) << "Wifi Hal is terminating...";
  return 0;
}
