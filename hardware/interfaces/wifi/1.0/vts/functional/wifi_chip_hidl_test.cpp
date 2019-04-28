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

#include <android/hardware/wifi/1.0/IWifiChip.h>

#include <VtsHalHidlTargetTestBase.h>

#include "wifi_hidl_call_util.h"
#include "wifi_hidl_test_utils.h"

using ::android::sp;
using ::android::hardware::hidl_string;
using ::android::hardware::hidl_vec;
using ::android::hardware::wifi::V1_0::IfaceType;
using ::android::hardware::wifi::V1_0::ChipId;
using ::android::hardware::wifi::V1_0::ChipModeId;
using ::android::hardware::wifi::V1_0::WifiDebugRingBufferStatus;
using ::android::hardware::wifi::V1_0::WifiDebugRingBufferVerboseLevel;
using ::android::hardware::wifi::V1_0::WifiDebugHostWakeReasonStats;
using ::android::hardware::wifi::V1_0::WifiStatus;
using ::android::hardware::wifi::V1_0::WifiStatusCode;
using ::android::hardware::wifi::V1_0::IWifiChip;
using ::android::hardware::wifi::V1_0::IWifiApIface;
using ::android::hardware::wifi::V1_0::IWifiIface;
using ::android::hardware::wifi::V1_0::IWifiNanIface;
using ::android::hardware::wifi::V1_0::IWifiP2pIface;
using ::android::hardware::wifi::V1_0::IWifiRttController;
using ::android::hardware::wifi::V1_0::IWifiStaIface;

extern WifiHidlEnvironment* gEnv;

namespace {
constexpr WifiDebugRingBufferVerboseLevel kDebugRingBufferVerboseLvl =
    WifiDebugRingBufferVerboseLevel::VERBOSE;
constexpr uint32_t kDebugRingBufferMaxInterval = 5;
constexpr uint32_t kDebugRingBufferMaxDataSize = 1024;

/**
 * Check if any of the ring buffer capabilities are set.
 */
bool hasAnyRingBufferCapabilities(uint32_t caps) {
    return (caps &
            (IWifiChip::ChipCapabilityMask::DEBUG_RING_BUFFER_CONNECT_EVENT |
             IWifiChip::ChipCapabilityMask::DEBUG_RING_BUFFER_POWER_EVENT |
             IWifiChip::ChipCapabilityMask::DEBUG_RING_BUFFER_WAKELOCK_EVENT |
             IWifiChip::ChipCapabilityMask::DEBUG_RING_BUFFER_VENDOR_DATA));
}
}  // namespace

/**
 * Fixture to use for all Wifi chip HIDL interface tests.
 */
class WifiChipHidlTest : public ::testing::VtsHalHidlTargetTestBase {
   public:
    virtual void SetUp() override {
        wifi_chip_ = getWifiChip();
        ASSERT_NE(nullptr, wifi_chip_.get());
    }

    virtual void TearDown() override { stopWifi(); }

   protected:
    // Helper function to configure the Chip in one of the supported modes.
    // Most of the non-mode-configuration-related methods require chip
    // to be first configured.
    ChipModeId configureChipForIfaceType(IfaceType type, bool expectSuccess) {
        ChipModeId mode_id;
        EXPECT_EQ(expectSuccess,
            configureChipToSupportIfaceType(wifi_chip_, type, &mode_id));
        return mode_id;
    }

    uint32_t configureChipForStaIfaceAndGetCapabilities() {
        configureChipForIfaceType(IfaceType::STA, true);
        const auto& status_and_caps = HIDL_INVOKE(wifi_chip_, getCapabilities);
        EXPECT_EQ(WifiStatusCode::SUCCESS, status_and_caps.first.code);
        return status_and_caps.second;
    }

    std::string getIfaceName(const sp<IWifiIface>& iface) {
        const auto& status_and_name = HIDL_INVOKE(iface, getName);
        EXPECT_EQ(WifiStatusCode::SUCCESS, status_and_name.first.code);
        return status_and_name.second;
    }

    WifiStatusCode createApIface(sp<IWifiApIface>* ap_iface) {
        const auto& status_and_iface = HIDL_INVOKE(wifi_chip_, createApIface);
        *ap_iface = status_and_iface.second;
        return status_and_iface.first.code;
    }

    WifiStatusCode removeApIface(const std::string& name) {
        return HIDL_INVOKE(wifi_chip_, removeApIface, name).code;
    }

    WifiStatusCode createNanIface(sp<IWifiNanIface>* nan_iface) {
        const auto& status_and_iface = HIDL_INVOKE(wifi_chip_, createNanIface);
        *nan_iface = status_and_iface.second;
        return status_and_iface.first.code;
    }

    WifiStatusCode removeNanIface(const std::string& name) {
        return HIDL_INVOKE(wifi_chip_, removeNanIface, name).code;
    }

    WifiStatusCode createP2pIface(sp<IWifiP2pIface>* p2p_iface) {
        const auto& status_and_iface = HIDL_INVOKE(wifi_chip_, createP2pIface);
        *p2p_iface = status_and_iface.second;
        return status_and_iface.first.code;
    }

    WifiStatusCode removeP2pIface(const std::string& name) {
        return HIDL_INVOKE(wifi_chip_, removeP2pIface, name).code;
    }

    WifiStatusCode createStaIface(sp<IWifiStaIface>* sta_iface) {
        const auto& status_and_iface = HIDL_INVOKE(wifi_chip_, createStaIface);
        *sta_iface = status_and_iface.second;
        return status_and_iface.first.code;
    }

    WifiStatusCode removeStaIface(const std::string& name) {
        return HIDL_INVOKE(wifi_chip_, removeStaIface, name).code;
    }

    sp<IWifiChip> wifi_chip_;
};

/*
 * Create:
 * Ensures that an instance of the IWifiChip proxy object is
 * successfully created.
 */
TEST(WifiChipHidlTestNoFixture, Create) {
    EXPECT_NE(nullptr, getWifiChip().get());
    stopWifi();
}

/*
 * GetId:
 */
TEST_F(WifiChipHidlTest, GetId) {
    EXPECT_EQ(WifiStatusCode::SUCCESS,
              HIDL_INVOKE(wifi_chip_, getId).first.code);
}

/*
 * GetAvailableMode:
 */
TEST_F(WifiChipHidlTest, GetAvailableModes) {
    const auto& status_and_modes = HIDL_INVOKE(wifi_chip_, getAvailableModes);
    EXPECT_EQ(WifiStatusCode::SUCCESS, status_and_modes.first.code);
    EXPECT_LT(0u, status_and_modes.second.size());
}

/*
 * ConfigureChip:
 */
TEST_F(WifiChipHidlTest, ConfigureChip) {
    const auto& status_and_modes = HIDL_INVOKE(wifi_chip_, getAvailableModes);
    EXPECT_EQ(WifiStatusCode::SUCCESS, status_and_modes.first.code);
    EXPECT_LT(0u, status_and_modes.second.size());
    for (const auto& mode : status_and_modes.second) {
        // configureChip() requires to be called with a fresh IWifiChip object.
        wifi_chip_ = getWifiChip();
        ASSERT_NE(nullptr, wifi_chip_.get());
        EXPECT_EQ(WifiStatusCode::SUCCESS,
                  HIDL_INVOKE(wifi_chip_, configureChip, mode.id).code);
        stopWifi();
        // Sleep for 5 milliseconds between each wifi state toggle.
        usleep(5000);
    }
}

/*
 * GetCapabilities:
 */
TEST_F(WifiChipHidlTest, GetCapabilities) {
    configureChipForIfaceType(IfaceType::STA, true);
    const auto& status_and_caps = HIDL_INVOKE(wifi_chip_, getCapabilities);
    EXPECT_EQ(WifiStatusCode::SUCCESS, status_and_caps.first.code);
    EXPECT_NE(0u, status_and_caps.second);
}

/*
 * GetMode:
 */
TEST_F(WifiChipHidlTest, GetMode) {
    ChipModeId chip_mode_id = configureChipForIfaceType(IfaceType::STA, true);
    const auto& status_and_mode = HIDL_INVOKE(wifi_chip_, getMode);
    EXPECT_EQ(WifiStatusCode::SUCCESS, status_and_mode.first.code);
    EXPECT_EQ(chip_mode_id, status_and_mode.second);
}

/*
 * RequestChipDebugInfo:
 */
TEST_F(WifiChipHidlTest, RequestChipDebugInfo) {
    configureChipForIfaceType(IfaceType::STA, true);
    const auto& status_and_chip_info =
        HIDL_INVOKE(wifi_chip_, requestChipDebugInfo);
    EXPECT_EQ(WifiStatusCode::SUCCESS, status_and_chip_info.first.code);
    EXPECT_LT(0u, status_and_chip_info.second.driverDescription.size());
    EXPECT_LT(0u, status_and_chip_info.second.firmwareDescription.size());
}

/*
 * RequestFirmwareDebugDump
 */
TEST_F(WifiChipHidlTest, RequestFirmwareDebugDump) {
    uint32_t caps = configureChipForStaIfaceAndGetCapabilities();
    const auto& status_and_firmware_dump =
        HIDL_INVOKE(wifi_chip_, requestFirmwareDebugDump);
    if (caps & IWifiChip::ChipCapabilityMask::DEBUG_MEMORY_FIRMWARE_DUMP) {
        EXPECT_EQ(WifiStatusCode::SUCCESS, status_and_firmware_dump.first.code);
    } else {
        EXPECT_EQ(WifiStatusCode::ERROR_NOT_SUPPORTED,
                  status_and_firmware_dump.first.code);
    }
}

/*
 * RequestDriverDebugDump
 */
TEST_F(WifiChipHidlTest, RequestDriverDebugDump) {
    uint32_t caps = configureChipForStaIfaceAndGetCapabilities();
    const auto& status_and_driver_dump =
        HIDL_INVOKE(wifi_chip_, requestDriverDebugDump);
    if (caps & IWifiChip::ChipCapabilityMask::DEBUG_MEMORY_DRIVER_DUMP) {
        EXPECT_EQ(WifiStatusCode::SUCCESS, status_and_driver_dump.first.code);
    } else {
      // API semantics (today) are such that function cannot be called if not capable!
      //
      //  EXPECT_EQ(WifiStatusCode::ERROR_NOT_SUPPORTED,
      //            status_and_driver_dump.first.code);
    }
}

/*
 * GetDebugRingBuffersStatus
 */
TEST_F(WifiChipHidlTest, GetDebugRingBuffersStatus) {
    uint32_t caps = configureChipForStaIfaceAndGetCapabilities();
    const auto& status_and_ring_buffer_status =
        HIDL_INVOKE(wifi_chip_, getDebugRingBuffersStatus);
    if (hasAnyRingBufferCapabilities(caps)) {
        EXPECT_EQ(WifiStatusCode::SUCCESS,
                  status_and_ring_buffer_status.first.code);
        for (const auto& ring_buffer : status_and_ring_buffer_status.second) {
            EXPECT_LT(0u, ring_buffer.ringName.size());
        }
    } else {
        EXPECT_EQ(WifiStatusCode::ERROR_NOT_SUPPORTED,
                  status_and_ring_buffer_status.first.code);
    }
}

/*
 * StartLoggingToDebugRingBuffer
 */
TEST_F(WifiChipHidlTest, StartLoggingToDebugRingBuffer) {
    uint32_t caps = configureChipForStaIfaceAndGetCapabilities();
    std::string ring_name;
    const auto& status_and_ring_buffer_status =
        HIDL_INVOKE(wifi_chip_, getDebugRingBuffersStatus);
    if (hasAnyRingBufferCapabilities(caps)) {
        EXPECT_EQ(WifiStatusCode::SUCCESS,
                  status_and_ring_buffer_status.first.code);
        ASSERT_LT(0u, status_and_ring_buffer_status.second.size());
        ring_name = status_and_ring_buffer_status.second[0].ringName.c_str();
    } else {
        EXPECT_EQ(WifiStatusCode::ERROR_NOT_SUPPORTED,
                  status_and_ring_buffer_status.first.code);
    }
    const auto& status =
        HIDL_INVOKE(wifi_chip_, startLoggingToDebugRingBuffer, ring_name,
                    kDebugRingBufferVerboseLvl, kDebugRingBufferMaxInterval,
                    kDebugRingBufferMaxDataSize);
    if (hasAnyRingBufferCapabilities(caps)) {
        EXPECT_EQ(WifiStatusCode::SUCCESS, status.code);
    } else {
        EXPECT_EQ(WifiStatusCode::ERROR_NOT_SUPPORTED, status.code);
    }
}

/*
 * ForceDumpToDebugRingBuffer
 */
TEST_F(WifiChipHidlTest, ForceDumpToDebugRingBuffer) {
    uint32_t caps = configureChipForStaIfaceAndGetCapabilities();
    std::string ring_name;
    const auto& status_and_ring_buffer_status =
        HIDL_INVOKE(wifi_chip_, getDebugRingBuffersStatus);
    if (hasAnyRingBufferCapabilities(caps)) {
        EXPECT_EQ(WifiStatusCode::SUCCESS,
                  status_and_ring_buffer_status.first.code);
        ASSERT_LT(0u, status_and_ring_buffer_status.second.size());
        ring_name = status_and_ring_buffer_status.second[0].ringName.c_str();
    } else {
        EXPECT_EQ(WifiStatusCode::ERROR_NOT_SUPPORTED,
                  status_and_ring_buffer_status.first.code);
    }
    const auto& status =
        HIDL_INVOKE(wifi_chip_, forceDumpToDebugRingBuffer, ring_name);
    if (hasAnyRingBufferCapabilities(caps)) {
        EXPECT_EQ(WifiStatusCode::SUCCESS, status.code);
    } else {
        EXPECT_EQ(WifiStatusCode::ERROR_NOT_SUPPORTED, status.code);
    }
}

/*
 * GetDebugHostWakeReasonStats
 */
TEST_F(WifiChipHidlTest, GetDebugHostWakeReasonStats) {
    uint32_t caps = configureChipForStaIfaceAndGetCapabilities();
    const auto& status_and_debug_wake_reason =
        HIDL_INVOKE(wifi_chip_, getDebugHostWakeReasonStats);
    if (caps & IWifiChip::ChipCapabilityMask::DEBUG_HOST_WAKE_REASON_STATS) {
        EXPECT_EQ(WifiStatusCode::SUCCESS,
                  status_and_debug_wake_reason.first.code);
    } else {
        EXPECT_EQ(WifiStatusCode::ERROR_NOT_SUPPORTED,
                  status_and_debug_wake_reason.first.code);
    }
}

/*
 * CreateApIface
 * Configures the chip in AP mode and ensures that only 1 iface creation
 * succeeds. The 2nd iface creation should be rejected.
 */
TEST_F(WifiChipHidlTest, CreateApIface) {
    configureChipForIfaceType(IfaceType::AP, true);

    sp<IWifiApIface> iface;
    EXPECT_EQ(WifiStatusCode::SUCCESS, createApIface(&iface));
    EXPECT_NE(nullptr, iface.get());

    EXPECT_EQ(WifiStatusCode::ERROR_NOT_AVAILABLE, createApIface(&iface));
}

/*
 * GetApIfaceNames
 * Configures the chip in AP mode and ensures that the iface list is empty
 * before creating the iface. Then, create the iface and ensure that
 * iface name is returned via the list.
 */
TEST_F(WifiChipHidlTest, GetApIfaceNames) {
    configureChipForIfaceType(IfaceType::AP, true);

    const auto& status_and_iface_names1 =
        HIDL_INVOKE(wifi_chip_, getApIfaceNames);
    EXPECT_EQ(WifiStatusCode::SUCCESS, status_and_iface_names1.first.code);
    EXPECT_EQ(0u, status_and_iface_names1.second.size());

    sp<IWifiApIface> iface;
    EXPECT_EQ(WifiStatusCode::SUCCESS, createApIface(&iface));
    EXPECT_NE(nullptr, iface.get());

    std::string iface_name = getIfaceName(iface);
    const auto& status_and_iface_names2 =
        HIDL_INVOKE(wifi_chip_, getApIfaceNames);
    EXPECT_EQ(WifiStatusCode::SUCCESS, status_and_iface_names2.first.code);
    EXPECT_EQ(1u, status_and_iface_names2.second.size());
    EXPECT_EQ(iface_name, status_and_iface_names2.second[0]);

    EXPECT_EQ(WifiStatusCode::SUCCESS, removeApIface(iface_name));
    const auto& status_and_iface_names3 =
        HIDL_INVOKE(wifi_chip_, getApIfaceNames);
    EXPECT_EQ(WifiStatusCode::SUCCESS, status_and_iface_names3.first.code);
    EXPECT_EQ(0u, status_and_iface_names3.second.size());
}

/*
 * GetApIface
 * Configures the chip in AP mode and create an iface. Then, retrieve
 * the iface object using the correct name and ensure any other name
 * doesn't retrieve an iface object.
 */
TEST_F(WifiChipHidlTest, GetApIface) {
    configureChipForIfaceType(IfaceType::AP, true);

    sp<IWifiApIface> ap_iface;
    EXPECT_EQ(WifiStatusCode::SUCCESS, createApIface(&ap_iface));
    EXPECT_NE(nullptr, ap_iface.get());

    std::string iface_name = getIfaceName(ap_iface);
    const auto& status_and_iface1 =
        HIDL_INVOKE(wifi_chip_, getApIface, iface_name);
    EXPECT_EQ(WifiStatusCode::SUCCESS, status_and_iface1.first.code);
    EXPECT_NE(nullptr, status_and_iface1.second.get());

    std::string invalid_name = iface_name + "0";
    const auto& status_and_iface2 =
        HIDL_INVOKE(wifi_chip_, getApIface, invalid_name);
    EXPECT_EQ(WifiStatusCode::ERROR_INVALID_ARGS, status_and_iface2.first.code);
    EXPECT_EQ(nullptr, status_and_iface2.second.get());
}

/*
 * RemoveApIface
 * Configures the chip in AP mode and create an iface. Then, remove
 * the iface object using the correct name and ensure any other name
 * doesn't remove the iface.
 */
TEST_F(WifiChipHidlTest, RemoveApIface) {
    configureChipForIfaceType(IfaceType::AP, true);

    sp<IWifiApIface> ap_iface;
    EXPECT_EQ(WifiStatusCode::SUCCESS, createApIface(&ap_iface));
    EXPECT_NE(nullptr, ap_iface.get());

    std::string iface_name = getIfaceName(ap_iface);
    std::string invalid_name = iface_name + "0";
    EXPECT_EQ(WifiStatusCode::ERROR_INVALID_ARGS, removeApIface(invalid_name));
    EXPECT_EQ(WifiStatusCode::SUCCESS, removeApIface(iface_name));

    // No such iface exists now. So, this should return failure.
    EXPECT_EQ(WifiStatusCode::ERROR_INVALID_ARGS, removeApIface(iface_name));
}

/*
 * CreateNanIface
 * Configures the chip in NAN mode and ensures that only 1 iface creation
 * succeeds. The 2nd iface creation should be rejected.
 */
TEST_F(WifiChipHidlTest, CreateNanIface) {
    configureChipForIfaceType(IfaceType::NAN, gEnv->isNanOn);
    if (!gEnv->isNanOn) return;

    sp<IWifiNanIface> iface;
    ASSERT_EQ(WifiStatusCode::SUCCESS, createNanIface(&iface));
    EXPECT_NE(nullptr, iface.get());

    EXPECT_EQ(WifiStatusCode::ERROR_NOT_AVAILABLE, createNanIface(&iface));
}

/*
 * GetNanIfaceNames
 * Configures the chip in NAN mode and ensures that the iface list is empty
 * before creating the iface. Then, create the iface and ensure that
 * iface name is returned via the list.
 */
TEST_F(WifiChipHidlTest, GetNanIfaceNames) {
    configureChipForIfaceType(IfaceType::NAN, gEnv->isNanOn);
    if (!gEnv->isNanOn) return;

    const auto& status_and_iface_names1 =
        HIDL_INVOKE(wifi_chip_, getNanIfaceNames);
    ASSERT_EQ(WifiStatusCode::SUCCESS, status_and_iface_names1.first.code);
    EXPECT_EQ(0u, status_and_iface_names1.second.size());

    sp<IWifiNanIface> iface;
    EXPECT_EQ(WifiStatusCode::SUCCESS, createNanIface(&iface));
    EXPECT_NE(nullptr, iface.get());

    std::string iface_name = getIfaceName(iface);
    const auto& status_and_iface_names2 =
        HIDL_INVOKE(wifi_chip_, getNanIfaceNames);
    EXPECT_EQ(WifiStatusCode::SUCCESS, status_and_iface_names2.first.code);
    EXPECT_EQ(1u, status_and_iface_names2.second.size());
    EXPECT_EQ(iface_name, status_and_iface_names2.second[0]);

    EXPECT_EQ(WifiStatusCode::SUCCESS, removeNanIface(iface_name));
    const auto& status_and_iface_names3 =
        HIDL_INVOKE(wifi_chip_, getNanIfaceNames);
    EXPECT_EQ(WifiStatusCode::SUCCESS, status_and_iface_names3.first.code);
    EXPECT_EQ(0u, status_and_iface_names3.second.size());
}

/*
 * GetNanIface
 * Configures the chip in NAN mode and create an iface. Then, retrieve
 * the iface object using the correct name and ensure any other name
 * doesn't retrieve an iface object.
 */
TEST_F(WifiChipHidlTest, GetNanIface) {
    configureChipForIfaceType(IfaceType::NAN, gEnv->isNanOn);
    if (!gEnv->isNanOn) return;

    sp<IWifiNanIface> nan_iface;
    EXPECT_EQ(WifiStatusCode::SUCCESS, createNanIface(&nan_iface));
    EXPECT_NE(nullptr, nan_iface.get());

    std::string iface_name = getIfaceName(nan_iface);
    const auto& status_and_iface1 =
        HIDL_INVOKE(wifi_chip_, getNanIface, iface_name);
    EXPECT_EQ(WifiStatusCode::SUCCESS, status_and_iface1.first.code);
    EXPECT_NE(nullptr, status_and_iface1.second.get());

    std::string invalid_name = iface_name + "0";
    const auto& status_and_iface2 =
        HIDL_INVOKE(wifi_chip_, getNanIface, invalid_name);
    EXPECT_EQ(WifiStatusCode::ERROR_INVALID_ARGS, status_and_iface2.first.code);
    EXPECT_EQ(nullptr, status_and_iface2.second.get());
}

/*
 * RemoveNanIface
 * Configures the chip in NAN mode and create an iface. Then, remove
 * the iface object using the correct name and ensure any other name
 * doesn't remove the iface.
 */
TEST_F(WifiChipHidlTest, RemoveNanIface) {
    configureChipForIfaceType(IfaceType::NAN, gEnv->isNanOn);
    if (!gEnv->isNanOn) return;

    sp<IWifiNanIface> nan_iface;
    EXPECT_EQ(WifiStatusCode::SUCCESS, createNanIface(&nan_iface));
    EXPECT_NE(nullptr, nan_iface.get());

    std::string iface_name = getIfaceName(nan_iface);
    std::string invalid_name = iface_name + "0";
    EXPECT_EQ(WifiStatusCode::ERROR_INVALID_ARGS, removeNanIface(invalid_name));

    EXPECT_EQ(WifiStatusCode::SUCCESS, removeNanIface(iface_name));

    // No such iface exists now. So, this should return failure.
    EXPECT_EQ(WifiStatusCode::ERROR_INVALID_ARGS, removeNanIface(iface_name));
}

/*
 * CreateP2pIface
 * Configures the chip in P2P mode and ensures that only 1 iface creation
 * succeeds. The 2nd iface creation should be rejected.
 */
TEST_F(WifiChipHidlTest, CreateP2pIface) {
    configureChipForIfaceType(IfaceType::P2P, true);

    sp<IWifiP2pIface> iface;
    EXPECT_EQ(WifiStatusCode::SUCCESS, createP2pIface(&iface));
    EXPECT_NE(nullptr, iface.get());

    EXPECT_EQ(WifiStatusCode::ERROR_NOT_AVAILABLE, createP2pIface(&iface));
}

/*
 * GetP2pIfaceNames
 * Configures the chip in P2P mode and ensures that the iface list is empty
 * before creating the iface. Then, create the iface and ensure that
 * iface name is returned via the list.
 */
TEST_F(WifiChipHidlTest, GetP2pIfaceNames) {
    configureChipForIfaceType(IfaceType::P2P, true);

    const auto& status_and_iface_names1 =
        HIDL_INVOKE(wifi_chip_, getP2pIfaceNames);
    EXPECT_EQ(WifiStatusCode::SUCCESS, status_and_iface_names1.first.code);
    EXPECT_EQ(0u, status_and_iface_names1.second.size());

    sp<IWifiP2pIface> iface;
    EXPECT_EQ(WifiStatusCode::SUCCESS, createP2pIface(&iface));
    EXPECT_NE(nullptr, iface.get());

    std::string iface_name = getIfaceName(iface);
    const auto& status_and_iface_names2 =
        HIDL_INVOKE(wifi_chip_, getP2pIfaceNames);
    EXPECT_EQ(WifiStatusCode::SUCCESS, status_and_iface_names2.first.code);
    EXPECT_EQ(1u, status_and_iface_names2.second.size());
    EXPECT_EQ(iface_name, status_and_iface_names2.second[0]);

    EXPECT_EQ(WifiStatusCode::SUCCESS, removeP2pIface(iface_name));
    const auto& status_and_iface_names3 =
        HIDL_INVOKE(wifi_chip_, getP2pIfaceNames);
    EXPECT_EQ(WifiStatusCode::SUCCESS, status_and_iface_names3.first.code);
    EXPECT_EQ(0u, status_and_iface_names3.second.size());
}

/*
 * GetP2pIface
 * Configures the chip in P2P mode and create an iface. Then, retrieve
 * the iface object using the correct name and ensure any other name
 * doesn't retrieve an iface object.
 */
TEST_F(WifiChipHidlTest, GetP2pIface) {
    configureChipForIfaceType(IfaceType::P2P, true);

    sp<IWifiP2pIface> p2p_iface;
    EXPECT_EQ(WifiStatusCode::SUCCESS, createP2pIface(&p2p_iface));
    EXPECT_NE(nullptr, p2p_iface.get());

    std::string iface_name = getIfaceName(p2p_iface);
    const auto& status_and_iface1 =
        HIDL_INVOKE(wifi_chip_, getP2pIface, iface_name);
    EXPECT_EQ(WifiStatusCode::SUCCESS, status_and_iface1.first.code);
    EXPECT_NE(nullptr, status_and_iface1.second.get());

    std::string invalid_name = iface_name + "0";
    const auto& status_and_iface2 =
        HIDL_INVOKE(wifi_chip_, getP2pIface, invalid_name);
    EXPECT_EQ(WifiStatusCode::ERROR_INVALID_ARGS, status_and_iface2.first.code);
    EXPECT_EQ(nullptr, status_and_iface2.second.get());
}

/*
 * RemoveP2pIface
 * Configures the chip in P2P mode and create an iface. Then, remove
 * the iface object using the correct name and ensure any other name
 * doesn't remove the iface.
 */
TEST_F(WifiChipHidlTest, RemoveP2pIface) {
    configureChipForIfaceType(IfaceType::P2P, true);

    sp<IWifiP2pIface> p2p_iface;
    EXPECT_EQ(WifiStatusCode::SUCCESS, createP2pIface(&p2p_iface));
    EXPECT_NE(nullptr, p2p_iface.get());

    std::string iface_name = getIfaceName(p2p_iface);
    std::string invalid_name = iface_name + "0";
    EXPECT_EQ(WifiStatusCode::ERROR_INVALID_ARGS, removeP2pIface(invalid_name));
    EXPECT_EQ(WifiStatusCode::SUCCESS, removeP2pIface(iface_name));

    // No such iface exists now. So, this should return failure.
    EXPECT_EQ(WifiStatusCode::ERROR_INVALID_ARGS, removeP2pIface(iface_name));
}

/*
 * CreateStaIface
 * Configures the chip in STA mode and ensures that only 1 iface creation
 * succeeds. The 2nd iface creation should be rejected.
 */
TEST_F(WifiChipHidlTest, CreateStaIface) {
    configureChipForIfaceType(IfaceType::STA, true);

    sp<IWifiStaIface> iface;
    EXPECT_EQ(WifiStatusCode::SUCCESS, createStaIface(&iface));
    EXPECT_NE(nullptr, iface.get());

    EXPECT_EQ(WifiStatusCode::ERROR_NOT_AVAILABLE, createStaIface(&iface));
}

/*
 * GetStaIfaceNames
 * Configures the chip in STA mode and ensures that the iface list is empty
 * before creating the iface. Then, create the iface and ensure that
 * iface name is returned via the list.
 */
TEST_F(WifiChipHidlTest, GetStaIfaceNames) {
    configureChipForIfaceType(IfaceType::STA, true);

    const auto& status_and_iface_names1 =
        HIDL_INVOKE(wifi_chip_, getStaIfaceNames);
    EXPECT_EQ(WifiStatusCode::SUCCESS, status_and_iface_names1.first.code);
    EXPECT_EQ(0u, status_and_iface_names1.second.size());

    sp<IWifiStaIface> iface;
    EXPECT_EQ(WifiStatusCode::SUCCESS, createStaIface(&iface));
    EXPECT_NE(nullptr, iface.get());

    std::string iface_name = getIfaceName(iface);
    const auto& status_and_iface_names2 =
        HIDL_INVOKE(wifi_chip_, getStaIfaceNames);
    EXPECT_EQ(WifiStatusCode::SUCCESS, status_and_iface_names2.first.code);
    EXPECT_EQ(1u, status_and_iface_names2.second.size());
    EXPECT_EQ(iface_name, status_and_iface_names2.second[0]);

    EXPECT_EQ(WifiStatusCode::SUCCESS, removeStaIface(iface_name));
    const auto& status_and_iface_names3 =
        HIDL_INVOKE(wifi_chip_, getStaIfaceNames);
    EXPECT_EQ(WifiStatusCode::SUCCESS, status_and_iface_names3.first.code);
    EXPECT_EQ(0u, status_and_iface_names3.second.size());
}

/*
 * GetStaIface
 * Configures the chip in STA mode and create an iface. Then, retrieve
 * the iface object using the correct name and ensure any other name
 * doesn't retrieve an iface object.
 */
TEST_F(WifiChipHidlTest, GetStaIface) {
    configureChipForIfaceType(IfaceType::STA, true);

    sp<IWifiStaIface> sta_iface;
    EXPECT_EQ(WifiStatusCode::SUCCESS, createStaIface(&sta_iface));
    EXPECT_NE(nullptr, sta_iface.get());

    std::string iface_name = getIfaceName(sta_iface);
    const auto& status_and_iface1 =
        HIDL_INVOKE(wifi_chip_, getStaIface, iface_name);
    EXPECT_EQ(WifiStatusCode::SUCCESS, status_and_iface1.first.code);
    EXPECT_NE(nullptr, status_and_iface1.second.get());

    std::string invalid_name = iface_name + "0";
    const auto& status_and_iface2 =
        HIDL_INVOKE(wifi_chip_, getStaIface, invalid_name);
    EXPECT_EQ(WifiStatusCode::ERROR_INVALID_ARGS, status_and_iface2.first.code);
    EXPECT_EQ(nullptr, status_and_iface2.second.get());
}

/*
 * RemoveStaIface
 * Configures the chip in STA mode and create an iface. Then, remove
 * the iface object using the correct name and ensure any other name
 * doesn't remove the iface.
 */
TEST_F(WifiChipHidlTest, RemoveStaIface) {
    configureChipForIfaceType(IfaceType::STA, true);

    sp<IWifiStaIface> sta_iface;
    EXPECT_EQ(WifiStatusCode::SUCCESS, createStaIface(&sta_iface));
    EXPECT_NE(nullptr, sta_iface.get());

    std::string iface_name = getIfaceName(sta_iface);
    std::string invalid_name = iface_name + "0";
    EXPECT_EQ(WifiStatusCode::ERROR_INVALID_ARGS, removeStaIface(invalid_name));
    EXPECT_EQ(WifiStatusCode::SUCCESS, removeStaIface(iface_name));

    // No such iface exists now. So, this should return failure.
    EXPECT_EQ(WifiStatusCode::ERROR_INVALID_ARGS, removeStaIface(iface_name));
}

/*
 * CreateRttController
 */
TEST_F(WifiChipHidlTest, CreateRttController) {
    configureChipForIfaceType(IfaceType::AP, true);

    sp<IWifiApIface> iface;
    EXPECT_EQ(WifiStatusCode::SUCCESS, createApIface(&iface));
    EXPECT_NE(nullptr, iface.get());

    const auto& status_and_rtt_controller =
        HIDL_INVOKE(wifi_chip_, createRttController, iface);
    EXPECT_EQ(WifiStatusCode::SUCCESS, status_and_rtt_controller.first.code);
    EXPECT_NE(nullptr, status_and_rtt_controller.second.get());
}
