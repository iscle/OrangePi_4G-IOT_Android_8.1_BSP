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

#include <android/hardware/wifi/1.1/IWifi.h>
#include <android/hardware/wifi/1.1/IWifiChip.h>

#include <VtsHalHidlTargetTestBase.h>

#include "wifi_hidl_call_util.h"
#include "wifi_hidl_test_utils.h"

using ::android::sp;
using ::android::hardware::hidl_string;
using ::android::hardware::hidl_vec;
using ::android::hardware::wifi::V1_0::IfaceType;
using ::android::hardware::wifi::V1_0::ChipId;
using ::android::hardware::wifi::V1_0::ChipModeId;
using ::android::hardware::wifi::V1_0::WifiStatus;
using ::android::hardware::wifi::V1_0::WifiStatusCode;
using ::android::hardware::wifi::V1_1::IWifi;
using ::android::hardware::wifi::V1_1::IWifiChip;
using ::android::hardware::wifi::V1_0::IWifiStaIface;

namespace {
constexpr IWifiChip::TxPowerScenario kFakePowerScenario =
    IWifiChip::TxPowerScenario::VOICE_CALL;
}; //namespace

/**
 * Fixture to use for all Wifi chip HIDL interface tests.
 */
class WifiChipHidlTest : public ::testing::VtsHalHidlTargetTestBase {
   public:
    virtual void SetUp() override {
        wifi_chip_ = IWifiChip::castFrom(getWifiChip());
        ASSERT_NE(nullptr, wifi_chip_.get());
    }

    virtual void TearDown() override { stopWifi(); }

   protected:
    uint32_t configureChipForStaIfaceAndGetCapabilities() {
        ChipModeId mode_id;
        EXPECT_TRUE(configureChipToSupportIfaceType(
            wifi_chip_, IfaceType::STA, &mode_id));
        const auto& status_and_caps = HIDL_INVOKE(wifi_chip_, getCapabilities);
        EXPECT_EQ(WifiStatusCode::SUCCESS, status_and_caps.first.code);
        return status_and_caps.second;
    }

    sp<IWifiChip> wifi_chip_;
};

/*
 * SelectTxPowerScenario
 */
TEST_F(WifiChipHidlTest, SelectTxPowerScenario) {
    uint32_t caps = configureChipForStaIfaceAndGetCapabilities();
    const auto& status =
        HIDL_INVOKE(wifi_chip_, selectTxPowerScenario, kFakePowerScenario);
    if (caps & IWifiChip::ChipCapabilityMask::SET_TX_POWER_LIMIT) {
        EXPECT_EQ(WifiStatusCode::SUCCESS, status.code);
    } else {
        EXPECT_EQ(WifiStatusCode::ERROR_NOT_SUPPORTED, status.code);
    }
}

/*
 * ResetTxPowerScenario
 */
TEST_F(WifiChipHidlTest, ResetTxPowerScenario) {
    uint32_t caps = configureChipForStaIfaceAndGetCapabilities();
    const auto& status =
        HIDL_INVOKE(wifi_chip_, resetTxPowerScenario);
    if (caps & IWifiChip::ChipCapabilityMask::SET_TX_POWER_LIMIT) {
        EXPECT_EQ(WifiStatusCode::SUCCESS, status.code);
    } else {
        EXPECT_EQ(WifiStatusCode::ERROR_NOT_SUPPORTED, status.code);
    }
}
