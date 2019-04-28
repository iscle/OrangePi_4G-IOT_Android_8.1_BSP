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

#include <android/hardware/wifi/1.0/IWifiApIface.h>

#include <VtsHalHidlTargetTestBase.h>

#include "wifi_hidl_call_util.h"
#include "wifi_hidl_test_utils.h"

using ::android::hardware::wifi::V1_0::IfaceType;
using ::android::hardware::wifi::V1_0::IWifiApIface;
using ::android::hardware::wifi::V1_0::WifiBand;
using ::android::hardware::wifi::V1_0::WifiStatusCode;
using ::android::sp;

/**
 * Fixture to use for all AP Iface HIDL interface tests.
 */
class WifiApIfaceHidlTest : public ::testing::VtsHalHidlTargetTestBase {
   public:
    virtual void SetUp() override {
        wifi_ap_iface_ = getWifiApIface();
        ASSERT_NE(nullptr, wifi_ap_iface_.get());
    }

    virtual void TearDown() override { stopWifi(); }

   protected:
    sp<IWifiApIface> wifi_ap_iface_;
};

/*
 * Create:
 * Ensures that an instance of the IWifiApIface proxy object is
 * successfully created.
 */
TEST(WifiApIfaceHidlTestNoFixture, Create) {
    EXPECT_NE(nullptr, getWifiApIface().get());
    stopWifi();
}

/*
 * GetType:
 * Ensures that the correct interface type is returned for AP interface.
 */
TEST_F(WifiApIfaceHidlTest, GetType) {
    const auto& status_and_type = HIDL_INVOKE(wifi_ap_iface_, getType);
    EXPECT_EQ(WifiStatusCode::SUCCESS, status_and_type.first.code);
    EXPECT_EQ(IfaceType::AP, status_and_type.second);
}

/*
 * SetCountryCode:
 * Ensures that a call to set the country code will return with a success
 * status code.
 */
TEST_F(WifiApIfaceHidlTest, SetCountryCode) {
    const android::hardware::hidl_array<int8_t, 2> kCountryCode{
        std::array<int8_t, 2>{{0x55, 0x53}}};
    EXPECT_EQ(WifiStatusCode::SUCCESS,
              HIDL_INVOKE(wifi_ap_iface_, setCountryCode, kCountryCode).code);
}

/*
 * GetValidFrequenciesForBand:
 * Ensures that we can retrieve valid frequencies for 2.4 GHz band.
 */
TEST_F(WifiApIfaceHidlTest, GetValidFrequenciesForBand) {
    const auto& status_and_freqs = HIDL_INVOKE(
        wifi_ap_iface_, getValidFrequenciesForBand, WifiBand::BAND_24GHZ);
    EXPECT_EQ(WifiStatusCode::SUCCESS, status_and_freqs.first.code);
    EXPECT_GT(status_and_freqs.second.size(), 0u);
}
