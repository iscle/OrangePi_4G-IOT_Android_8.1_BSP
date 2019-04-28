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

#include <VtsHalHidlTargetTestBase.h>

#include <android/hardware/wifi/supplicant/1.0/ISupplicantP2pIface.h>

#include "supplicant_hidl_call_util.h"
#include "supplicant_hidl_test_utils.h"

using ::android::sp;
using ::android::hardware::hidl_array;
using ::android::hardware::hidl_string;
using ::android::hardware::hidl_vec;
using ::android::hardware::Return;
using ::android::hardware::Void;
using ::android::hardware::wifi::supplicant::V1_0::IfaceType;
using ::android::hardware::wifi::supplicant::V1_0::ISupplicantP2pIface;
using ::android::hardware::wifi::supplicant::V1_0::ISupplicantP2pIfaceCallback;
using ::android::hardware::wifi::supplicant::V1_0::SupplicantNetworkId;
using ::android::hardware::wifi::supplicant::V1_0::SupplicantStatus;
using ::android::hardware::wifi::supplicant::V1_0::SupplicantStatusCode;

namespace {
constexpr uint8_t kTestSsidPostfix[] = {'t', 'e', 's', 't'};
constexpr uint8_t kTestMacAddr[] = {0x56, 0x67, 0x67, 0xf4, 0x56, 0x92};
constexpr uint8_t kTestPeerMacAddr[] = {0x56, 0x67, 0x55, 0xf4, 0x56, 0x92};
constexpr uint8_t kTestBonjourServiceQuery[] = {'t', 'e', 's', 't', 'q',
                                                'u', 'e', 'r', 'y'};
constexpr uint8_t kTestBonjourServiceResponse[] = {
    't', 'e', 's', 't', 'r', 'e', 's', 'p', 'o', 'n', 's', 'e'};
constexpr uint8_t kTestWfdDeviceInfo[] = {[0 ... 5] = 0x01};
constexpr char kTestConnectPin[] = "34556665";
constexpr char kTestGroupIfName[] = "TestGroup";
constexpr char kTestWpsDeviceName[] = "TestWpsDeviceName";
constexpr char kTestWpsManufacturer[] = "TestManufacturer";
constexpr char kTestWpsModelName[] = "TestModelName";
constexpr char kTestWpsModelNumber[] = "TestModelNumber";
constexpr char kTestWpsSerialNumber[] = "TestSerialNumber";
constexpr char kTestUpnpServiceName[] = "TestServiceName";
constexpr uint8_t kTestWpsDeviceType[] = {[0 ... 7] = 0x01};
constexpr uint16_t kTestWpsConfigMethods = 0xffff;
constexpr uint32_t kTestConnectGoIntent = 6;
constexpr uint32_t kTestFindTimeout = 5;
constexpr uint32_t kTestSetGroupIdleTimeout = 6;
constexpr uint32_t kTestChannel = 1;
constexpr uint32_t kTestOperatingClass = 81;
constexpr uint32_t kTestFreqRange[] = {2412, 2432};
constexpr uint32_t kTestExtListenPeriod = 400;
constexpr uint32_t kTestExtListenInterval = 400;
constexpr SupplicantNetworkId kTestNetworkId = 5;
}  // namespace

class SupplicantP2pIfaceHidlTest : public ::testing::VtsHalHidlTargetTestBase {
   public:
    virtual void SetUp() override {
        startSupplicantAndWaitForHidlService();
        EXPECT_TRUE(turnOnExcessiveLogging());
        p2p_iface_ = getSupplicantP2pIface();
        ASSERT_NE(p2p_iface_.get(), nullptr);

        memcpy(mac_addr_.data(), kTestMacAddr, mac_addr_.size());
        memcpy(peer_mac_addr_.data(), kTestPeerMacAddr, peer_mac_addr_.size());
    }

    virtual void TearDown() override { stopSupplicant(); }

   protected:
    // ISupplicantP2pIface object used for all tests in this fixture.
    sp<ISupplicantP2pIface> p2p_iface_;
    // MAC address to use for various tests.
    std::array<uint8_t, 6> mac_addr_;
    std::array<uint8_t, 6> peer_mac_addr_;
};

class IfaceCallback : public ISupplicantP2pIfaceCallback {
    Return<void> onNetworkAdded(uint32_t /* id */) override { return Void(); }
    Return<void> onNetworkRemoved(uint32_t /* id */) override { return Void(); }
    Return<void> onDeviceFound(
        const hidl_array<uint8_t, 6>& /* srcAddress */,
        const hidl_array<uint8_t, 6>& /* p2pDeviceAddress */,
        const hidl_array<uint8_t, 8>& /* primaryDeviceType */,
        const hidl_string& /* deviceName */, uint16_t /* configMethods */,
        uint8_t /* deviceCapabilities */, uint32_t /* groupCapabilities */,
        const hidl_array<uint8_t, 6>& /* wfdDeviceInfo */) override {
        return Void();
    }
    Return<void> onDeviceLost(
        const hidl_array<uint8_t, 6>& /* p2pDeviceAddress */) override {
        return Void();
    }
    Return<void> onFindStopped() override { return Void(); }
    Return<void> onGoNegotiationRequest(
        const hidl_array<uint8_t, 6>& /* srcAddress */,
        ISupplicantP2pIfaceCallback::WpsDevPasswordId /* passwordId */)
        override {
        return Void();
    }
    Return<void> onGoNegotiationCompleted(
        ISupplicantP2pIfaceCallback::P2pStatusCode /* status */) override {
        return Void();
    }
    Return<void> onGroupFormationSuccess() override { return Void(); }
    Return<void> onGroupFormationFailure(
        const hidl_string& /* failureReason */) override {
        return Void();
    }
    Return<void> onGroupStarted(
        const hidl_string& /* groupIfname */, bool /* isGo */,
        const hidl_vec<uint8_t>& /* ssid */, uint32_t /* frequency */,
        const hidl_array<uint8_t, 32>& /* psk */,
        const hidl_string& /* passphrase */,
        const hidl_array<uint8_t, 6>& /* goDeviceAddress */,
        bool /* isPersistent */) override {
        return Void();
    }
    Return<void> onGroupRemoved(const hidl_string& /* groupIfname */,
                                bool /* isGo */) override {
        return Void();
    }
    Return<void> onInvitationReceived(
        const hidl_array<uint8_t, 6>& /* srcAddress */,
        const hidl_array<uint8_t, 6>& /* goDeviceAddress */,
        const hidl_array<uint8_t, 6>& /* bssid */,
        uint32_t /* persistentNetworkId */,
        uint32_t /* operatingFrequency */) override {
        return Void();
    }
    Return<void> onInvitationResult(
        const hidl_array<uint8_t, 6>& /* bssid */,
        ISupplicantP2pIfaceCallback::P2pStatusCode /* status */) override {
        return Void();
    }
    Return<void> onProvisionDiscoveryCompleted(
        const hidl_array<uint8_t, 6>& /* p2pDeviceAddress */,
        bool /* isRequest */,
        ISupplicantP2pIfaceCallback::P2pProvDiscStatusCode /* status */,
        uint16_t /* configMethods */,
        const hidl_string& /* generatedPin */) override {
        return Void();
    }
    Return<void> onServiceDiscoveryResponse(
        const hidl_array<uint8_t, 6>& /* srcAddress */,
        uint16_t /* updateIndicator */,
        const hidl_vec<uint8_t>& /* tlvs */) override {
        return Void();
    }
    Return<void> onStaAuthorized(
        const hidl_array<uint8_t, 6>& /* srcAddress */,
        const hidl_array<uint8_t, 6>& /* p2pDeviceAddress */) override {
        return Void();
    }
    Return<void> onStaDeauthorized(
        const hidl_array<uint8_t, 6>& /* srcAddress */,
        const hidl_array<uint8_t, 6>& /* p2pDeviceAddress */) override {
        return Void();
    }
};

/*
 * Create:
 * Ensures that an instance of the ISupplicantP2pIface proxy object is
 * successfully created.
 */
TEST(SupplicantP2pIfaceHidlTestNoFixture, Create) {
    startSupplicantAndWaitForHidlService();
    EXPECT_NE(nullptr, getSupplicantP2pIface().get());
    stopSupplicant();
}

/*
 * RegisterCallback
 */
TEST_F(SupplicantP2pIfaceHidlTest, RegisterCallback) {
    p2p_iface_->registerCallback(
        new IfaceCallback(), [](const SupplicantStatus& status) {
            EXPECT_EQ(SupplicantStatusCode::SUCCESS, status.code);
        });
}

/*
 * GetName
 */
TEST_F(SupplicantP2pIfaceHidlTest, GetName) {
    const auto& status_and_interface_name = HIDL_INVOKE(p2p_iface_, getName);
    EXPECT_EQ(SupplicantStatusCode::SUCCESS,
              status_and_interface_name.first.code);
    EXPECT_FALSE(std::string(status_and_interface_name.second).empty());
}

/*
 * GetType
 */
TEST_F(SupplicantP2pIfaceHidlTest, GetType) {
    const auto& status_and_interface_type = HIDL_INVOKE(p2p_iface_, getType);
    EXPECT_EQ(SupplicantStatusCode::SUCCESS,
              status_and_interface_type.first.code);
    EXPECT_EQ(status_and_interface_type.second, IfaceType::P2P);
}

/*
 * GetDeviceAddress
 */
TEST_F(SupplicantP2pIfaceHidlTest, GetDeviceAddress) {
    p2p_iface_->getDeviceAddress(
        [](const SupplicantStatus& status,
           const hidl_array<uint8_t, 6>& /* mac_addr */) {
            EXPECT_EQ(SupplicantStatusCode::SUCCESS, status.code);
        });
}

/*
 * SetSsidPostfix
 */
TEST_F(SupplicantP2pIfaceHidlTest, SetSsidPostfix) {
    std::vector<uint8_t> ssid(kTestSsidPostfix,
                              kTestSsidPostfix + sizeof(kTestSsidPostfix));
    p2p_iface_->setSsidPostfix(ssid, [](const SupplicantStatus& status) {
        EXPECT_EQ(SupplicantStatusCode::SUCCESS, status.code);
    });
}

/*
 * Find
 */
TEST_F(SupplicantP2pIfaceHidlTest, Find) {
    p2p_iface_->find(kTestFindTimeout, [](const SupplicantStatus& status) {
        EXPECT_EQ(SupplicantStatusCode::SUCCESS, status.code);
    });
}

/*
 * StopFind
 */
TEST_F(SupplicantP2pIfaceHidlTest, StopFind) {
    p2p_iface_->find(kTestFindTimeout, [](const SupplicantStatus& status) {
        EXPECT_EQ(SupplicantStatusCode::SUCCESS, status.code);
    });

    p2p_iface_->stopFind([](const SupplicantStatus& status) {
        EXPECT_EQ(SupplicantStatusCode::SUCCESS, status.code);
    });
}

/*
 * Flush
 */
TEST_F(SupplicantP2pIfaceHidlTest, Flush) {
    p2p_iface_->flush([](const SupplicantStatus& status) {
        EXPECT_EQ(SupplicantStatusCode::SUCCESS, status.code);
    });
}

/*
 * Connect
 */
TEST_F(SupplicantP2pIfaceHidlTest, Connect) {
    p2p_iface_->connect(
        mac_addr_, ISupplicantP2pIface::WpsProvisionMethod::PBC,
        kTestConnectPin, false, false, kTestConnectGoIntent,
        [](const SupplicantStatus& status, const hidl_string& /* pin */) {
            // This is not going to work with fake values.
            EXPECT_EQ(SupplicantStatusCode::FAILURE_UNKNOWN, status.code);
        });
}

/*
 * CancelConnect
 */
TEST_F(SupplicantP2pIfaceHidlTest, CancelConnect) {
    p2p_iface_->connect(
        mac_addr_, ISupplicantP2pIface::WpsProvisionMethod::PBC,
        kTestConnectPin, false, false, kTestConnectGoIntent,
        [](const SupplicantStatus& status, const hidl_string& /* pin */) {
            // This is not going to work with fake values.
            EXPECT_EQ(SupplicantStatusCode::FAILURE_UNKNOWN, status.code);
        });

    p2p_iface_->cancelConnect([](const SupplicantStatus& status) {
        EXPECT_EQ(SupplicantStatusCode::FAILURE_UNKNOWN, status.code);
    });
}

/*
 * ProvisionDiscovery
 */
TEST_F(SupplicantP2pIfaceHidlTest, ProvisionDiscovery) {
    p2p_iface_->provisionDiscovery(
        mac_addr_, ISupplicantP2pIface::WpsProvisionMethod::PBC,
        [](const SupplicantStatus& status) {
            // This is not going to work with fake values.
            EXPECT_EQ(SupplicantStatusCode::FAILURE_UNKNOWN, status.code);
        });
}

/*
 * AddGroup
 */
TEST_F(SupplicantP2pIfaceHidlTest, AddGroup) {
    p2p_iface_->addGroup(false, kTestNetworkId,
                         [](const SupplicantStatus& /* status */) {
                             // TODO: Figure out the initialization sequence for
                             // this to work.
                             // EXPECT_EQ(SupplicantStatusCode::SUCCESS,
                             // status.code);
                         });
}

/*
 * RemoveGroup
 */
TEST_F(SupplicantP2pIfaceHidlTest, RemoveGroup) {
    // This is not going to work with fake values.
    EXPECT_NE(SupplicantStatusCode::SUCCESS,
              HIDL_INVOKE(p2p_iface_, removeGroup, kTestGroupIfName).code);
}

/*
 * Reject
 */
TEST_F(SupplicantP2pIfaceHidlTest, Reject) {
    p2p_iface_->reject(mac_addr_, [](const SupplicantStatus& status) {
        // This is not going to work with fake values.
        EXPECT_EQ(SupplicantStatusCode::FAILURE_UNKNOWN, status.code);
    });
}

/*
 * Invite
 */
TEST_F(SupplicantP2pIfaceHidlTest, Invite) {
    p2p_iface_->invite(kTestGroupIfName, mac_addr_, peer_mac_addr_,
                       [](const SupplicantStatus& status) {
                           // This is not going to work with fake values.
                           EXPECT_EQ(SupplicantStatusCode::FAILURE_UNKNOWN,
                                     status.code);
                       });
}

/*
 * Reinvoke
 */
TEST_F(SupplicantP2pIfaceHidlTest, Reinvoke) {
    p2p_iface_->reinvoke(
        kTestNetworkId, mac_addr_, [](const SupplicantStatus& status) {
            // This is not going to work with fake values.
            EXPECT_EQ(SupplicantStatusCode::FAILURE_NETWORK_UNKNOWN,
                      status.code);
        });
}

/*
 * ConfigureExtListen
 */
TEST_F(SupplicantP2pIfaceHidlTest, ConfigureExtListen) {
    p2p_iface_->configureExtListen(kTestExtListenPeriod, kTestExtListenInterval,
                                   [](const SupplicantStatus& status) {
                                       EXPECT_EQ(SupplicantStatusCode::SUCCESS,
                                                 status.code);
                                   });
}

/*
 * SetListenChannel
 */
TEST_F(SupplicantP2pIfaceHidlTest, SetListenChannel) {
    p2p_iface_->setListenChannel(
        kTestChannel, kTestOperatingClass, [](const SupplicantStatus& status) {
            EXPECT_EQ(SupplicantStatusCode::SUCCESS, status.code);
        });
}

/*
 * SetDisallowedFrequencies
 */
TEST_F(SupplicantP2pIfaceHidlTest, SetDisallowedFrequencies) {
    std::vector<ISupplicantP2pIface::FreqRange> ranges = {
        {kTestFreqRange[0], kTestFreqRange[1]}};
    p2p_iface_->setDisallowedFrequencies(
        ranges, [](const SupplicantStatus& status) {
            EXPECT_EQ(SupplicantStatusCode::SUCCESS, status.code);
        });
}

/*
 * GetSsid
 */
TEST_F(SupplicantP2pIfaceHidlTest, GetSsid) {
    std::array<uint8_t, 6> mac_addr;
    memcpy(mac_addr.data(), kTestMacAddr, mac_addr.size());
    p2p_iface_->getSsid(mac_addr, [](const SupplicantStatus& status,
                                     const hidl_vec<uint8_t>& /* ssid */) {
        // This is not going to work with fake values.
        EXPECT_EQ(SupplicantStatusCode::FAILURE_UNKNOWN, status.code);
    });
}

/*
 * GetGroupCapability
 */
TEST_F(SupplicantP2pIfaceHidlTest, GetGroupCapability) {
    std::array<uint8_t, 6> mac_addr;
    memcpy(mac_addr.data(), kTestMacAddr, mac_addr.size());
    p2p_iface_->getGroupCapability(
        mac_addr, [](const SupplicantStatus& status, uint32_t /* caps */) {
            // This is not going to work with fake values.
            EXPECT_EQ(SupplicantStatusCode::FAILURE_UNKNOWN, status.code);
        });
}

/*
 * FlushServices
 */
TEST_F(SupplicantP2pIfaceHidlTest, FlushServices) {
    p2p_iface_->flushServices([](const SupplicantStatus& status) {
        EXPECT_EQ(SupplicantStatusCode::SUCCESS, status.code);
    });
}

/*
 * SetMiracastMode
 */
TEST_F(SupplicantP2pIfaceHidlTest, SetMiracastMode) {
    p2p_iface_->setMiracastMode(ISupplicantP2pIface::MiracastMode::DISABLED,
                                [](const SupplicantStatus& status) {
                                    EXPECT_EQ(SupplicantStatusCode::SUCCESS,
                                              status.code);
                                });
    p2p_iface_->setMiracastMode(ISupplicantP2pIface::MiracastMode::SOURCE,
                                [](const SupplicantStatus& status) {
                                    EXPECT_EQ(SupplicantStatusCode::SUCCESS,
                                              status.code);
                                });
    p2p_iface_->setMiracastMode(ISupplicantP2pIface::MiracastMode::SINK,
                                [](const SupplicantStatus& status) {
                                    EXPECT_EQ(SupplicantStatusCode::SUCCESS,
                                              status.code);
                                });
}

/*
 * SetGroupIdle
 */
TEST_F(SupplicantP2pIfaceHidlTest, SetGroupIdle) {
    // This is not going to work with fake values.
    EXPECT_NE(SupplicantStatusCode::SUCCESS,
              HIDL_INVOKE(p2p_iface_, setGroupIdle, kTestGroupIfName,
                          kTestSetGroupIdleTimeout)
                  .code);
}

/*
 * SetPowerSave
 */
TEST_F(SupplicantP2pIfaceHidlTest, SetPowerSave) {
    // This is not going to work with fake values.
    EXPECT_NE(
        SupplicantStatusCode::SUCCESS,
        HIDL_INVOKE(p2p_iface_, setPowerSave, kTestGroupIfName, true).code);
    // This is not going to work with fake values.
    EXPECT_NE(
        SupplicantStatusCode::SUCCESS,
        HIDL_INVOKE(p2p_iface_, setPowerSave, kTestGroupIfName, false).code);
}

/*
 * SetWpsDeviceName
 */
TEST_F(SupplicantP2pIfaceHidlTest, SetWpsDeviceName) {
    EXPECT_EQ(
        SupplicantStatusCode::SUCCESS,
        HIDL_INVOKE(p2p_iface_, setWpsDeviceName, kTestWpsDeviceName).code);
}

/*
 * SetWpsDeviceType
 */
TEST_F(SupplicantP2pIfaceHidlTest, SetWpsDeviceType) {
    EXPECT_EQ(
        SupplicantStatusCode::SUCCESS,
        HIDL_INVOKE(p2p_iface_, setWpsDeviceType, kTestWpsDeviceType).code);
}

/*
 * SetWpsManufacturer
 */
TEST_F(SupplicantP2pIfaceHidlTest, SetWpsManufacturer) {
    EXPECT_EQ(
        SupplicantStatusCode::SUCCESS,
        HIDL_INVOKE(p2p_iface_, setWpsManufacturer, kTestWpsManufacturer).code);
}

/*
 * SetWpsModelName
 */
TEST_F(SupplicantP2pIfaceHidlTest, SetWpsModelName) {
    EXPECT_EQ(SupplicantStatusCode::SUCCESS,
              HIDL_INVOKE(p2p_iface_, setWpsModelName, kTestWpsModelName).code);
}

/*
 * SetWpsModelNumber
 */
TEST_F(SupplicantP2pIfaceHidlTest, SetWpsModelNumber) {
    EXPECT_EQ(
        SupplicantStatusCode::SUCCESS,
        HIDL_INVOKE(p2p_iface_, setWpsModelNumber, kTestWpsModelNumber).code);
}

/*
 * SetWpsSerialNumber
 */
TEST_F(SupplicantP2pIfaceHidlTest, SetWpsSerialNumber) {
    EXPECT_EQ(
        SupplicantStatusCode::SUCCESS,
        HIDL_INVOKE(p2p_iface_, setWpsSerialNumber, kTestWpsSerialNumber).code);
}

/*
 * SetWpsConfigMethods
 */
TEST_F(SupplicantP2pIfaceHidlTest, SetWpsConfigMethods) {
    EXPECT_EQ(
        SupplicantStatusCode::SUCCESS,
        HIDL_INVOKE(p2p_iface_, setWpsConfigMethods, kTestWpsConfigMethods)
            .code);
}

/*
 * AddAndRemoveBonjourService
 * This tests that we are able to add a bonjour service, and we can remove it
 * by using the same query data.
 * This also tests that removeBonjourSerive() returns error when there is no
 * existing bonjour service with the same query data.
 */
TEST_F(SupplicantP2pIfaceHidlTest, AddAndRemoveBonjourService) {
    EXPECT_EQ(SupplicantStatusCode::SUCCESS,
              HIDL_INVOKE(
                  p2p_iface_, addBonjourService,
                  std::vector<uint8_t>(kTestBonjourServiceQuery,
                                       kTestBonjourServiceQuery +
                                           sizeof(kTestBonjourServiceQuery)),
                  std::vector<uint8_t>(kTestBonjourServiceResponse,
                                       kTestBonjourServiceResponse +
                                           sizeof(kTestBonjourServiceResponse)))
                  .code);
    EXPECT_EQ(
        SupplicantStatusCode::SUCCESS,
        HIDL_INVOKE(p2p_iface_, removeBonjourService,
                    std::vector<uint8_t>(kTestBonjourServiceQuery,
                                         kTestBonjourServiceQuery +
                                             sizeof(kTestBonjourServiceQuery)))
            .code);
    // This will fail because boujour service with kTestBonjourServiceQuery was
    // already removed.
    EXPECT_NE(
        SupplicantStatusCode::SUCCESS,
        HIDL_INVOKE(p2p_iface_, removeBonjourService,
                    std::vector<uint8_t>(kTestBonjourServiceQuery,
                                         kTestBonjourServiceQuery +
                                             sizeof(kTestBonjourServiceQuery)))
            .code);
}

/*
 * AddAndRemoveUpnpService
 * This tests that we are able to add a upnp service, and we can remove it
 * by using the same service name.
 * This also tests that removeUpnpService() returns error when there is no
 * exsiting upnp service with the same service name.
 */
TEST_F(SupplicantP2pIfaceHidlTest, AddAndRemoveUpnpService) {
    EXPECT_EQ(SupplicantStatusCode::SUCCESS,
              HIDL_INVOKE(p2p_iface_, addUpnpService, 0 /* version */,
                          kTestUpnpServiceName)
                  .code);
    EXPECT_EQ(SupplicantStatusCode::SUCCESS,
              HIDL_INVOKE(p2p_iface_, removeUpnpService, 0 /* version */,
                          kTestUpnpServiceName)
                  .code);
    // This will fail because Upnp service with kTestUpnpServiceName was
    // already removed.
    EXPECT_NE(SupplicantStatusCode::SUCCESS,
              HIDL_INVOKE(p2p_iface_, removeUpnpService, 0 /* version */,
                          kTestUpnpServiceName)
                  .code);
}

/*
 * EnableWfd
 */
TEST_F(SupplicantP2pIfaceHidlTest, EnableWfd) {
    EXPECT_EQ(SupplicantStatusCode::SUCCESS,
              HIDL_INVOKE(p2p_iface_, enableWfd, true).code);
    EXPECT_EQ(SupplicantStatusCode::SUCCESS,
              HIDL_INVOKE(p2p_iface_, enableWfd, false).code);
}

/*
 * SetWfdDeviceInfo
 */
TEST_F(SupplicantP2pIfaceHidlTest, SetWfdDeviceInfo) {
    EXPECT_EQ(
        SupplicantStatusCode::SUCCESS,
        HIDL_INVOKE(p2p_iface_, setWfdDeviceInfo, kTestWfdDeviceInfo).code);
}
