/*
 * Copyright (C) 2016 The Android Open Source Project
 *
 * Licensed under the Staache License, Version 2.0 (the "License");
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

#include <android/hardware/wifi/1.0/IWifiStaIface.h>

#include <VtsHalHidlTargetTestBase.h>

#include "wifi_hidl_call_util.h"
#include "wifi_hidl_test_utils.h"

using ::android::sp;
using ::android::hardware::wifi::V1_0::Bssid;
using ::android::hardware::wifi::V1_0::CommandId;
using ::android::hardware::wifi::V1_0::IfaceType;
using ::android::hardware::wifi::V1_0::IWifiStaIface;
using ::android::hardware::wifi::V1_0::Rssi;
using ::android::hardware::wifi::V1_0::Ssid;
using ::android::hardware::wifi::V1_0::StaApfPacketFilterCapabilities;
using ::android::hardware::wifi::V1_0::StaRoamingConfig;
using ::android::hardware::wifi::V1_0::StaRoamingState;
using ::android::hardware::wifi::V1_0::WifiBand;
using ::android::hardware::wifi::V1_0::WifiStatus;
using ::android::hardware::wifi::V1_0::WifiStatusCode;

/**
 * Fixture to use for all STA Iface HIDL interface tests.
 */
class WifiStaIfaceHidlTest : public ::testing::VtsHalHidlTargetTestBase {
   public:
    virtual void SetUp() override {
        wifi_sta_iface_ = getWifiStaIface();
        ASSERT_NE(nullptr, wifi_sta_iface_.get());
    }

    virtual void TearDown() override { stopWifi(); }

   protected:
    bool isCapabilitySupported(IWifiStaIface::StaIfaceCapabilityMask cap_mask) {
        const auto& status_and_caps =
            HIDL_INVOKE(wifi_sta_iface_, getCapabilities);
        EXPECT_EQ(WifiStatusCode::SUCCESS, status_and_caps.first.code);
        return (status_and_caps.second & cap_mask) != 0;
    }

    sp<IWifiStaIface> wifi_sta_iface_;
};

/*
 * Create:
 * Ensures that an instance of the IWifiStaIface proxy object is
 * successfully created.
 */
TEST(WifiStaIfaceHidlTestNoFixture, Create) {
    EXPECT_NE(nullptr, getWifiStaIface().get());
    stopWifi();
}

/*
 * GetCapabilities:
 */
TEST_F(WifiStaIfaceHidlTest, GetCapabilities) {
    const auto& status_and_caps = HIDL_INVOKE(wifi_sta_iface_, getCapabilities);
    EXPECT_EQ(WifiStatusCode::SUCCESS, status_and_caps.first.code);
    EXPECT_GT(status_and_caps.second, 0u);
}

/*
 * GetType:
 * Ensures that the correct interface type is returned for station interface.
 */
TEST_F(WifiStaIfaceHidlTest, GetType) {
    const auto& status_and_type = HIDL_INVOKE(wifi_sta_iface_, getType);
    EXPECT_EQ(WifiStatusCode::SUCCESS, status_and_type.first.code);
    EXPECT_EQ(IfaceType::STA, status_and_type.second);
}

/*
 * GetApfPacketFilterCapabilities:
 * Ensures that we can retrieve APF packet filter capabilites.
 */
TEST_F(WifiStaIfaceHidlTest, GetApfPacketFilterCapabilities) {
    if (!isCapabilitySupported(IWifiStaIface::StaIfaceCapabilityMask::APF)) {
        // No-op if APF packet filer is not supported.
        return;
    }

    const auto& status_and_caps =
        HIDL_INVOKE(wifi_sta_iface_, getApfPacketFilterCapabilities);
    EXPECT_EQ(WifiStatusCode::SUCCESS, status_and_caps.first.code);
}

/*
 * GetBackgroundScanCapabilities:
 * Ensures that we can retrieve background scan capabilities.
 */
TEST_F(WifiStaIfaceHidlTest, GetBackgroundScanCapabilities) {
    if (!isCapabilitySupported(
            IWifiStaIface::StaIfaceCapabilityMask::BACKGROUND_SCAN)) {
        // No-op if background scan is not supported.
        return;
    }

    const auto& status_and_caps =
        HIDL_INVOKE(wifi_sta_iface_, getBackgroundScanCapabilities);
    EXPECT_EQ(WifiStatusCode::SUCCESS, status_and_caps.first.code);
}

/*
 * GetValidFrequenciesForBand:
 * Ensures that we can retrieve valid frequencies for 2.4 GHz band.
 */
TEST_F(WifiStaIfaceHidlTest, GetValidFrequenciesForBand) {
    const auto& status_and_freqs = HIDL_INVOKE(
        wifi_sta_iface_, getValidFrequenciesForBand, WifiBand::BAND_24GHZ);
    EXPECT_EQ(WifiStatusCode::SUCCESS, status_and_freqs.first.code);
    EXPECT_GT(status_and_freqs.second.size(), 0u);
}

/*
 * LinkLayerStatsCollection:
 * Ensures that calls to enable, disable, and retrieve link layer stats
 * will return a success status code.
 */
TEST_F(WifiStaIfaceHidlTest, LinkLayerStatsCollection) {
    if (!isCapabilitySupported(
            IWifiStaIface::StaIfaceCapabilityMask::LINK_LAYER_STATS)) {
        // No-op if link layer stats is not supported.
        return;
    }

    // Enable link layer stats collection.
    EXPECT_EQ(WifiStatusCode::SUCCESS,
              HIDL_INVOKE(wifi_sta_iface_, enableLinkLayerStatsCollection, true)
                  .code);
    // Retrieve link layer stats.
    EXPECT_EQ(WifiStatusCode::SUCCESS,
              HIDL_INVOKE(wifi_sta_iface_, getLinkLayerStats).first.code);
    // Disable link layer stats collection.
    EXPECT_EQ(
        WifiStatusCode::SUCCESS,
        HIDL_INVOKE(wifi_sta_iface_, disableLinkLayerStatsCollection).code);
}

/*
 * RSSIMonitoring:
 * Ensures that calls to enable RSSI monitoring will return an error status
 * code if device is not connected to an AP.
 * Ensures that calls to disable RSSI monitoring will return an error status
 * code if RSSI monitoring is not enabled.
 */
TEST_F(WifiStaIfaceHidlTest, RSSIMonitoring) {
    if (!isCapabilitySupported(
            IWifiStaIface::StaIfaceCapabilityMask::RSSI_MONITOR)) {
        // No-op if RSSI monitor is not supported.
        return;
    }

    const CommandId kCmd = 1;
    const Rssi kMaxRssi = -50;
    const Rssi kMinRssi = -90;
    // This is going to fail because device is not connected to an AP.
    EXPECT_NE(WifiStatusCode::SUCCESS,
              HIDL_INVOKE(wifi_sta_iface_, startRssiMonitoring, kCmd, kMaxRssi,
                          kMinRssi)
                  .code);
    // This is going to fail because RSSI monitoring is not enabled.
    EXPECT_NE(WifiStatusCode::SUCCESS,
              HIDL_INVOKE(wifi_sta_iface_, stopRssiMonitoring, kCmd).code);
}

/*
 * RoamingControl:
 * Ensures that calls to configure and enable roaming will return a success
 * status code.
 */
TEST_F(WifiStaIfaceHidlTest, RoamingControl) {
    if (!isCapabilitySupported(
            IWifiStaIface::StaIfaceCapabilityMask::CONTROL_ROAMING)) {
        // No-op if roaming control is not supported.
        return;
    }

    // Retrieve roaming capabilities.
    const auto& status_and_cap =
        HIDL_INVOKE(wifi_sta_iface_, getRoamingCapabilities);
    EXPECT_EQ(WifiStatusCode::SUCCESS, status_and_cap.first.code);

    // Setup roaming configuration based on roaming capabilities.
    const auto& cap = status_and_cap.second;
    StaRoamingConfig roaming_config;
    if (cap.maxBlacklistSize > 0) {
        Bssid black_list_bssid{
            std::array<uint8_t, 6>{{0x11, 0x22, 0x33, 0x44, 0x55, 0x66}}};
        roaming_config.bssidBlacklist =
            android::hardware::hidl_vec<Bssid>{black_list_bssid};
    }
    if (cap.maxWhitelistSize > 0) {
        Ssid white_list_ssid{
            std::array<uint8_t, 32>{{0x77, 0x88, 0x99, 0xAA, 0xBB, 0xCC}}};
        roaming_config.ssidWhitelist =
            android::hardware::hidl_vec<Ssid>{white_list_ssid};
    }

    // Configure roaming.
    EXPECT_EQ(
        WifiStatusCode::SUCCESS,
        HIDL_INVOKE(wifi_sta_iface_, configureRoaming, roaming_config).code);

    // Enable roaming.
    EXPECT_EQ(
        WifiStatusCode::SUCCESS,
        HIDL_INVOKE(wifi_sta_iface_, setRoamingState, StaRoamingState::ENABLED)
            .code);
}

/*
 * EnableNDOffload:
 * Ensures that calls to enable neighbor discovery offload will return a success
 * status code.
 */
TEST_F(WifiStaIfaceHidlTest, EnableNDOffload) {
    EXPECT_EQ(WifiStatusCode::SUCCESS,
              HIDL_INVOKE(wifi_sta_iface_, enableNdOffload, true).code);
}

/*
 * SetScanningMacOui:
 * Ensures that calls to set scanning MAC OUI will return a success status
 * code.
 */
TEST_F(WifiStaIfaceHidlTest, SetScanningMacOui) {
    const android::hardware::hidl_array<uint8_t, 3> kOui{
        std::array<uint8_t, 3>{{0x10, 0x22, 0x33}}};
    EXPECT_EQ(WifiStatusCode::SUCCESS,
              HIDL_INVOKE(wifi_sta_iface_, setScanningMacOui, kOui).code);
}

/*
 * PacketFateMonitoring:
 * Ensures that calls to start packet fate monitoring and retrieve TX/RX
 * packets will return a success status code.
 */
TEST_F(WifiStaIfaceHidlTest, PacketFateMonitoring) {
    // Start packet fate monitoring.
    EXPECT_EQ(
        WifiStatusCode::SUCCESS,
        HIDL_INVOKE(wifi_sta_iface_, startDebugPacketFateMonitoring).code);

    // Retrieve packets.
    EXPECT_EQ(WifiStatusCode::SUCCESS,
              HIDL_INVOKE(wifi_sta_iface_, getDebugTxPacketFates).first.code);
    EXPECT_EQ(WifiStatusCode::SUCCESS,
              HIDL_INVOKE(wifi_sta_iface_, getDebugRxPacketFates).first.code);
}
