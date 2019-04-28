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

#include <android/hardware/wifi/1.0/IWifiRttController.h>

#include <VtsHalHidlTargetTestBase.h>

#include "wifi_hidl_test_utils.h"

using ::android::hardware::wifi::V1_0::IWifiRttController;
using ::android::sp;

/**
 * Fixture to use for all RTT controller HIDL interface tests.
 */
class WifiRttControllerHidlTest : public ::testing::VtsHalHidlTargetTestBase {
   public:
    virtual void SetUp() override {}

    virtual void TearDown() override { stopWifi(); }

   protected:
};

/*
 * Create:
 * Ensures that an instance of the IWifiRttController proxy object is
 * successfully created.
 */
TEST(WifiRttControllerHidlTestNoFixture, Create) {
    EXPECT_NE(nullptr, getWifiRttController().get());
    stopWifi();
}
