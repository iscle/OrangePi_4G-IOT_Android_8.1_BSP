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

#include <android/hardware/wifi/supplicant/1.0/ISupplicantStaNetwork.h>

#include <android/hardware/wifi/supplicant/1.0/ISupplicantStaNetwork.h>

#include "supplicant_hidl_call_util.h"
#include "supplicant_hidl_test_utils.h"

using ::android::sp;
using ::android::hardware::hidl_array;
using ::android::hardware::hidl_string;
using ::android::hardware::hidl_vec;
using ::android::hardware::Return;
using ::android::hardware::Void;
using ::android::hardware::wifi::supplicant::V1_0::IfaceType;
using ::android::hardware::wifi::supplicant::V1_0::ISupplicantStaIface;
using ::android::hardware::wifi::supplicant::V1_0::ISupplicantStaNetwork;
using ::android::hardware::wifi::supplicant::V1_0::
    ISupplicantStaNetworkCallback;
using ::android::hardware::wifi::supplicant::V1_0::SupplicantStatus;
using ::android::hardware::wifi::supplicant::V1_0::SupplicantStatusCode;

namespace {
constexpr char kTestSsidStr[] = "TestSsid1234";
constexpr char kTestPskPassphrase[] = "TestPsk123";
constexpr char kTestIdStr[] = "TestIdstr";
constexpr char kTestEapPasswdStr[] = "TestEapPasswd1234";
constexpr char kTestEapCert[] = "keystore://CERT";
constexpr char kTestEapPrivateKeyId[] = "key_id";
constexpr char kTestEapMatch[] = "match";
constexpr char kTestEapEngineID[] = "engine_id";
constexpr uint8_t kTestBssid[] = {0x56, 0x67, 0x67, 0xf4, 0x56, 0x92};
constexpr uint8_t kTestWepKey[] = {0x56, 0x67, 0x67, 0xf4, 0x56};
constexpr uint8_t kTestKc[] = {0x56, 0x67, 0x67, 0xf4, 0x76, 0x87, 0x98, 0x12};
constexpr uint8_t kTestSres[] = {0x56, 0x67, 0x67, 0xf4};
constexpr uint8_t kTestRes[] = {0x56, 0x67, 0x67, 0xf4, 0x67};
constexpr uint8_t kTestIk[] = {[0 ... 15] = 0x65};
constexpr uint8_t kTestCk[] = {[0 ... 15] = 0x45};
constexpr uint8_t kTestIdentity[] = {0x45, 0x67, 0x98, 0x67, 0x56};
constexpr uint8_t kTestPsk[] = {[0 ... 31] = 0x12};
constexpr uint8_t kTestAutParam[] = {[0 ... 13] = 0xe1};
constexpr uint32_t kTestWepTxKeyIdx = 2;
constexpr uint32_t kTestUpdateIdentifier = 21;
constexpr uint32_t kTestKeyMgmt = (ISupplicantStaNetwork::KeyMgmtMask::WPA_PSK |
                                   ISupplicantStaNetwork::KeyMgmtMask::WPA_EAP);
constexpr uint32_t kTestProto = (ISupplicantStaNetwork::ProtoMask::OSEN |
                                 ISupplicantStaNetwork::ProtoMask::RSN);
constexpr uint32_t kTestAuthAlg = (ISupplicantStaNetwork::AuthAlgMask::OPEN |
                                   ISupplicantStaNetwork::AuthAlgMask::SHARED);
constexpr uint32_t kTestGroupCipher =
    (ISupplicantStaNetwork::GroupCipherMask::CCMP |
     ISupplicantStaNetwork::GroupCipherMask::WEP104);
constexpr uint32_t kTestPairwiseCipher =
    (ISupplicantStaNetwork::PairwiseCipherMask::CCMP |
     ISupplicantStaNetwork::PairwiseCipherMask::TKIP);
}  // namespace

class SupplicantStaNetworkHidlTest : public ::testing::VtsHalHidlTargetTestBase {
   public:
    virtual void SetUp() override {
        startSupplicantAndWaitForHidlService();
        EXPECT_TRUE(turnOnExcessiveLogging());
        sta_network_ = createSupplicantStaNetwork();
        ASSERT_NE(sta_network_.get(), nullptr);

        ssid_.assign(kTestSsidStr, kTestSsidStr + strlen(kTestSsidStr));
    }

    virtual void TearDown() override { stopSupplicant(); }

   protected:
    void removeNetwork() {
      sp<ISupplicantStaIface> sta_iface = getSupplicantStaIface();
      ASSERT_NE(nullptr, sta_iface.get());
      uint32_t net_id;
      sta_network_->getId([&](const SupplicantStatus& status, int network_id) {
              ASSERT_EQ(SupplicantStatusCode::SUCCESS, status.code);
              net_id = network_id;
          });
      sta_iface->removeNetwork(net_id, [](const SupplicantStatus& status) {
              ASSERT_EQ(SupplicantStatusCode::SUCCESS, status.code);
          });
    }

    // ISupplicantStaNetwork object used for all tests in this fixture.
    sp<ISupplicantStaNetwork> sta_network_;
    // SSID to use for various tests.
    std::vector<uint8_t> ssid_;
};

class NetworkCallback : public ISupplicantStaNetworkCallback {
    Return<void> onNetworkEapSimGsmAuthRequest(
        const ISupplicantStaNetworkCallback::NetworkRequestEapSimGsmAuthParams&
        /* params */) override {
        return Void();
    }
    Return<void> onNetworkEapSimUmtsAuthRequest(
        const ISupplicantStaNetworkCallback::NetworkRequestEapSimUmtsAuthParams&
        /* params */) override {
        return Void();
    }
    Return<void> onNetworkEapIdentityRequest() override { return Void(); }
};

/*
 * Create:
 * Ensures that an instance of the ISupplicantStaNetwork proxy object is
 * successfully created.
 */
TEST(SupplicantStaNetworkHidlTestNoFixture, Create) {
    startSupplicantAndWaitForHidlService();
    EXPECT_NE(nullptr, createSupplicantStaNetwork().get());
    stopSupplicant();
}

/*
 * RegisterCallback
 */
TEST_F(SupplicantStaNetworkHidlTest, RegisterCallback) {
    sta_network_->registerCallback(
        new NetworkCallback(), [](const SupplicantStatus& status) {
            EXPECT_EQ(SupplicantStatusCode::SUCCESS, status.code);
        });
}

/*
 * GetInterfaceName
 */
TEST_F(SupplicantStaNetworkHidlTest, GetInterfaceName) {
    const auto& status_and_interface_name =
        HIDL_INVOKE(sta_network_, getInterfaceName);
    EXPECT_EQ(SupplicantStatusCode::SUCCESS,
              status_and_interface_name.first.code);
    EXPECT_FALSE(std::string(status_and_interface_name.second).empty());
}

/*
 * GetType
 */
TEST_F(SupplicantStaNetworkHidlTest, GetType) {
    const auto& status_and_interface_type = HIDL_INVOKE(sta_network_, getType);
    EXPECT_EQ(SupplicantStatusCode::SUCCESS,
              status_and_interface_type.first.code);
    EXPECT_EQ(status_and_interface_type.second, IfaceType::STA);
}

/* Tests out the various setter/getter methods. */
/*
 * SetGetSsid
 */
TEST_F(SupplicantStaNetworkHidlTest, SetGetSsid) {
    sta_network_->setSsid(ssid_, [](const SupplicantStatus& status) {
        EXPECT_EQ(SupplicantStatusCode::SUCCESS, status.code);
    });
    sta_network_->getSsid(
        [&](const SupplicantStatus& status, const hidl_vec<uint8_t>& get_ssid) {
            EXPECT_EQ(SupplicantStatusCode::SUCCESS, status.code);
            EXPECT_EQ(ssid_, std::vector<uint8_t>(get_ssid));
        });
}

/*
 * SetGetBssid
 */
TEST_F(SupplicantStaNetworkHidlTest, SetGetBssid) {
    std::array<uint8_t, 6> set_bssid;
    memcpy(set_bssid.data(), kTestBssid, set_bssid.size());
    sta_network_->setBssid(set_bssid, [](const SupplicantStatus& status) {
        EXPECT_EQ(SupplicantStatusCode::SUCCESS, status.code);
    });
    sta_network_->getBssid([&](const SupplicantStatus& status,
                               const hidl_array<uint8_t, 6>& get_bssid_hidl) {
        EXPECT_EQ(SupplicantStatusCode::SUCCESS, status.code);
        std::array<uint8_t, 6> get_bssid;
        memcpy(get_bssid.data(), get_bssid_hidl.data(), get_bssid.size());
        EXPECT_EQ(set_bssid, get_bssid);
    });
}

/*
 * SetGetKeyMgmt
 */
TEST_F(SupplicantStaNetworkHidlTest, SetGetKeyMgmt) {
    sta_network_->setKeyMgmt(kTestKeyMgmt, [](const SupplicantStatus& status) {
        EXPECT_EQ(SupplicantStatusCode::SUCCESS, status.code);
    });
    sta_network_->getKeyMgmt(
        [&](const SupplicantStatus& status, uint32_t key_mgmt) {
            EXPECT_EQ(SupplicantStatusCode::SUCCESS, status.code);
            EXPECT_EQ(key_mgmt, kTestKeyMgmt);
        });
}

/*
 * SetGetProto
 */
TEST_F(SupplicantStaNetworkHidlTest, SetGetProto) {
    sta_network_->setProto(kTestProto, [](const SupplicantStatus& status) {
        EXPECT_EQ(SupplicantStatusCode::SUCCESS, status.code);
    });
    sta_network_->getProto([&](const SupplicantStatus& status, uint32_t proto) {
        EXPECT_EQ(SupplicantStatusCode::SUCCESS, status.code);
        EXPECT_EQ(proto, kTestProto);
    });
}

/*
 * SetGetKeyAuthAlg
 */
TEST_F(SupplicantStaNetworkHidlTest, SetGetAuthAlg) {
    sta_network_->setAuthAlg(kTestAuthAlg, [](const SupplicantStatus& status) {
        EXPECT_EQ(SupplicantStatusCode::SUCCESS, status.code);
    });
    sta_network_->getAuthAlg(
        [&](const SupplicantStatus& status, uint32_t auth_alg) {
            EXPECT_EQ(SupplicantStatusCode::SUCCESS, status.code);
            EXPECT_EQ(auth_alg, kTestAuthAlg);
        });
}

/*
 * SetGetGroupCipher
 */
TEST_F(SupplicantStaNetworkHidlTest, SetGetGroupCipher) {
    sta_network_->setGroupCipher(
        kTestGroupCipher, [](const SupplicantStatus& status) {
            EXPECT_EQ(SupplicantStatusCode::SUCCESS, status.code);
        });
    sta_network_->getGroupCipher(
        [&](const SupplicantStatus& status, uint32_t group_cipher) {
            EXPECT_EQ(SupplicantStatusCode::SUCCESS, status.code);
            EXPECT_EQ(group_cipher, kTestGroupCipher);
        });
}

/*
 * SetGetPairwiseCipher
 */
TEST_F(SupplicantStaNetworkHidlTest, SetGetPairwiseCipher) {
    sta_network_->setPairwiseCipher(
        kTestPairwiseCipher, [](const SupplicantStatus& status) {
            EXPECT_EQ(SupplicantStatusCode::SUCCESS, status.code);
        });
    sta_network_->getPairwiseCipher(
        [&](const SupplicantStatus& status, uint32_t pairwise_cipher) {
            EXPECT_EQ(SupplicantStatusCode::SUCCESS, status.code);
            EXPECT_EQ(pairwise_cipher, kTestPairwiseCipher);
        });
}

/*
 * SetGetPskPassphrase
 */
TEST_F(SupplicantStaNetworkHidlTest, SetGetPskPassphrase) {
    sta_network_->setPskPassphrase(
        kTestPskPassphrase, [](const SupplicantStatus& status) {
            EXPECT_EQ(SupplicantStatusCode::SUCCESS, status.code);
        });
    sta_network_->getPskPassphrase(
        [&](const SupplicantStatus& status, const hidl_string& psk) {
            EXPECT_EQ(SupplicantStatusCode::SUCCESS, status.code);
            EXPECT_EQ(kTestPskPassphrase, std::string(psk.c_str()));
        });
}

/*
 * SetGetPsk
 */
TEST_F(SupplicantStaNetworkHidlTest, SetGetPsk) {
    EXPECT_EQ(SupplicantStatusCode::SUCCESS,
              HIDL_INVOKE(sta_network_, setPsk, kTestPsk).code);
    const auto& status_and_psk = HIDL_INVOKE(sta_network_, getPsk);
    EXPECT_EQ(SupplicantStatusCode::SUCCESS, status_and_psk.first.code);
    hidl_array<uint8_t, 32> expected_psk(kTestPsk);
    EXPECT_EQ(expected_psk, status_and_psk.second);
}

/*
 * SetGetWepKeys
 */
TEST_F(SupplicantStaNetworkHidlTest, SetGetWepTxKeyIdx) {
    sta_network_->setWepTxKeyIdx(
        kTestWepTxKeyIdx, [](const SupplicantStatus& status) {
            EXPECT_EQ(SupplicantStatusCode::SUCCESS, status.code);
        });
    sta_network_->getWepTxKeyIdx(
        [&](const SupplicantStatus& status, uint32_t key_idx) {
            EXPECT_EQ(SupplicantStatusCode::SUCCESS, status.code);
            EXPECT_EQ(kTestWepTxKeyIdx, key_idx);
        });
}

/*
 * SetGetWepKeys
 */
TEST_F(SupplicantStaNetworkHidlTest, SetGetWepKeys) {
    for (uint32_t i = 0;
         i < static_cast<uint32_t>(
                 ISupplicantStaNetwork::ParamSizeLimits::WEP_KEYS_MAX_NUM);
         i++) {
        std::vector<uint8_t> set_wep_key(std::begin(kTestWepKey),
                                         std::end(kTestWepKey));
        sta_network_->setWepKey(
            i, set_wep_key, [](const SupplicantStatus& status) {
                EXPECT_EQ(SupplicantStatusCode::SUCCESS, status.code);
            });
        sta_network_->getWepKey(i, [&](const SupplicantStatus& status,
                                       const hidl_vec<uint8_t>& get_wep_key) {
            EXPECT_EQ(SupplicantStatusCode::SUCCESS, status.code);
            EXPECT_EQ(set_wep_key, std::vector<uint8_t>(get_wep_key));
        });
    }
}

/*
 * SetGetScanSsid
 */
TEST_F(SupplicantStaNetworkHidlTest, SetGetScanSsid) {
    sta_network_->setScanSsid(
        true, [](const SupplicantStatus& status) {
            EXPECT_EQ(SupplicantStatusCode::SUCCESS, status.code);
        });
    sta_network_->getScanSsid(
        [&](const SupplicantStatus& status, bool scan_ssid) {
            EXPECT_EQ(SupplicantStatusCode::SUCCESS, status.code);
            EXPECT_EQ(true, scan_ssid);
        });
}

/*
 * SetGetRequirePmf
 */
TEST_F(SupplicantStaNetworkHidlTest, SetGetRequirePmf) {
    sta_network_->setRequirePmf(
        true, [](const SupplicantStatus& status) {
            EXPECT_EQ(SupplicantStatusCode::SUCCESS, status.code);
        });
    sta_network_->getRequirePmf(
        [&](const SupplicantStatus& status, bool require_pmf) {
            EXPECT_EQ(SupplicantStatusCode::SUCCESS, status.code);
            EXPECT_EQ(true, require_pmf);
        });
}

/*
 * SetGetIdStr
 */
TEST_F(SupplicantStaNetworkHidlTest, SetGetIdStr) {
    sta_network_->setIdStr(
        kTestIdStr, [](const SupplicantStatus& status) {
            EXPECT_EQ(SupplicantStatusCode::SUCCESS, status.code);
        });
    sta_network_->getIdStr(
        [&](const SupplicantStatus& status, const hidl_string& id_str) {
            EXPECT_EQ(SupplicantStatusCode::SUCCESS, status.code);
            EXPECT_EQ(kTestIdStr, std::string(id_str.c_str()));
        });
}


/*
 * SetGetEapMethod
 */
TEST_F(SupplicantStaNetworkHidlTest, SetGetEapMethod) {
    ISupplicantStaNetwork::EapMethod set_eap_method =
        ISupplicantStaNetwork::EapMethod::PEAP;
    sta_network_->setEapMethod(
        set_eap_method, [](const SupplicantStatus& status) {
            EXPECT_EQ(SupplicantStatusCode::SUCCESS, status.code);
        });
    sta_network_->getEapMethod(
        [&](const SupplicantStatus& status,
            ISupplicantStaNetwork::EapMethod eap_method) {
            EXPECT_EQ(SupplicantStatusCode::SUCCESS, status.code);
            EXPECT_EQ(set_eap_method, eap_method);
        });
}

/*
 * SetGetEapPhase2Method
 */
TEST_F(SupplicantStaNetworkHidlTest, SetGetEapPhase2Method) {
    ISupplicantStaNetwork::EapMethod set_eap_method =
        ISupplicantStaNetwork::EapMethod::PEAP;
    sta_network_->setEapMethod(
        set_eap_method, [](const SupplicantStatus& status) {
            EXPECT_EQ(SupplicantStatusCode::SUCCESS, status.code);
        });
    ISupplicantStaNetwork::EapPhase2Method set_eap_phase2_method =
        ISupplicantStaNetwork::EapPhase2Method::NONE;
    sta_network_->setEapPhase2Method(
        set_eap_phase2_method, [](const SupplicantStatus& status) {
            EXPECT_EQ(SupplicantStatusCode::SUCCESS, status.code);
        });
    sta_network_->getEapPhase2Method(
        [&](const SupplicantStatus& status,
            ISupplicantStaNetwork::EapPhase2Method eap_phase2_method) {
            EXPECT_EQ(SupplicantStatusCode::SUCCESS, status.code);
            EXPECT_EQ(set_eap_phase2_method, eap_phase2_method);
        });
}

/*
 * SetGetEapIdentity
 */
TEST_F(SupplicantStaNetworkHidlTest, SetGetEapIdentity) {
    std::vector<uint8_t> set_identity(kTestIdentity, kTestIdentity + sizeof(kTestIdentity));
    sta_network_->setEapIdentity(
        set_identity, [](const SupplicantStatus& status) {
            EXPECT_EQ(SupplicantStatusCode::SUCCESS, status.code);
        });
    sta_network_->getEapIdentity(
        [&](const SupplicantStatus& status, const std::vector<uint8_t>& identity) {
            EXPECT_EQ(SupplicantStatusCode::SUCCESS, status.code);
            EXPECT_EQ(set_identity, identity);
        });
}

/*
 * SetGetEapAnonymousIdentity
 */
TEST_F(SupplicantStaNetworkHidlTest, SetGetEapAnonymousIdentity) {
    std::vector<uint8_t> set_identity(kTestIdentity, kTestIdentity + sizeof(kTestIdentity));
    sta_network_->setEapAnonymousIdentity(
        set_identity, [](const SupplicantStatus& status) {
            EXPECT_EQ(SupplicantStatusCode::SUCCESS, status.code);
        });
    sta_network_->getEapAnonymousIdentity(
        [&](const SupplicantStatus& status, const std::vector<uint8_t>& identity) {
            EXPECT_EQ(SupplicantStatusCode::SUCCESS, status.code);
            EXPECT_EQ(set_identity, identity);
        });
}

/*
 * SetGetEapPassword
 */
TEST_F(SupplicantStaNetworkHidlTest, SetGetEapPassword) {
    std::vector<uint8_t> set_eap_passwd(
        kTestEapPasswdStr, kTestEapPasswdStr + strlen(kTestEapPasswdStr));
    sta_network_->setEapPassword(
        set_eap_passwd, [](const SupplicantStatus& status) {
            EXPECT_EQ(SupplicantStatusCode::SUCCESS, status.code);
        });
    sta_network_->getEapPassword([&](const SupplicantStatus& status,
                                     const hidl_vec<uint8_t>& eap_passwd) {
        EXPECT_EQ(SupplicantStatusCode::SUCCESS, status.code);
        EXPECT_EQ(set_eap_passwd, std::vector<uint8_t>(eap_passwd));
    });
}

/*
 * SetGetEapCACert
 */
TEST_F(SupplicantStaNetworkHidlTest, SetGetEapCACert) {
    sta_network_->setEapCACert(
        kTestEapCert, [](const SupplicantStatus& status) {
            EXPECT_EQ(SupplicantStatusCode::SUCCESS, status.code);
        });
    sta_network_->getEapCACert([&](const SupplicantStatus& status,
                                   const hidl_string& eap_cert) {
        EXPECT_EQ(SupplicantStatusCode::SUCCESS, status.code);
        EXPECT_EQ(kTestEapCert, std::string(eap_cert.c_str()));
    });
}

/*
 * SetGetEapCAPath
 */
TEST_F(SupplicantStaNetworkHidlTest, SetGetEapCAPath) {
    sta_network_->setEapCAPath(
        kTestEapCert, [](const SupplicantStatus& status) {
            EXPECT_EQ(SupplicantStatusCode::SUCCESS, status.code);
        });
    sta_network_->getEapCAPath([&](const SupplicantStatus& status,
                                   const hidl_string& eap_cert) {
        EXPECT_EQ(SupplicantStatusCode::SUCCESS, status.code);
        EXPECT_EQ(kTestEapCert, std::string(eap_cert.c_str()));
    });
}

/*
 * SetGetEapClientCert
 */
TEST_F(SupplicantStaNetworkHidlTest, SetGetEapClientCert) {
    sta_network_->setEapClientCert(
        kTestEapCert, [](const SupplicantStatus& status) {
            EXPECT_EQ(SupplicantStatusCode::SUCCESS, status.code);
        });
    sta_network_->getEapClientCert([&](const SupplicantStatus& status,
                                       const hidl_string& eap_cert) {
        EXPECT_EQ(SupplicantStatusCode::SUCCESS, status.code);
        EXPECT_EQ(kTestEapCert, std::string(eap_cert.c_str()));
    });
}

/*
 * SetGetEapPrivateKeyId
 */
TEST_F(SupplicantStaNetworkHidlTest, SetGetEapPrivateKeyId) {
    sta_network_->setEapPrivateKeyId(
        kTestEapPrivateKeyId, [](const SupplicantStatus& status) {
            EXPECT_EQ(SupplicantStatusCode::SUCCESS, status.code);
        });
    sta_network_->getEapPrivateKeyId([&](const SupplicantStatus& status,
                                         const hidl_string& key_id) {
        EXPECT_EQ(SupplicantStatusCode::SUCCESS, status.code);
        EXPECT_EQ(kTestEapPrivateKeyId, std::string(key_id.c_str()));
    });
}

/*
 * SetGetEapAltSubjectMatch
 */
TEST_F(SupplicantStaNetworkHidlTest, SetGetEapAltSubjectMatch) {
    sta_network_->setEapAltSubjectMatch(
        kTestEapMatch, [](const SupplicantStatus& status) {
            EXPECT_EQ(SupplicantStatusCode::SUCCESS, status.code);
        });
    sta_network_->getEapAltSubjectMatch([&](const SupplicantStatus& status,
                                            const hidl_string& match) {
        EXPECT_EQ(SupplicantStatusCode::SUCCESS, status.code);
        EXPECT_EQ(kTestEapMatch, std::string(match.c_str()));
    });
}

/*
 * SetGetEapSubjectMatch
 */
TEST_F(SupplicantStaNetworkHidlTest, SetGetEapSubjectMatch) {
    EXPECT_EQ(
        SupplicantStatusCode::SUCCESS,
        HIDL_INVOKE(sta_network_, setEapSubjectMatch, kTestEapMatch).code);
    const auto& status_and_subject_match =
        HIDL_INVOKE(sta_network_, getEapSubjectMatch);
    EXPECT_EQ(SupplicantStatusCode::SUCCESS,
              status_and_subject_match.first.code);
    EXPECT_EQ(kTestEapMatch,
              std::string(status_and_subject_match.second.c_str()));
}

/*
 * SetGetEapDomainSuffixMatch
 */
TEST_F(SupplicantStaNetworkHidlTest, SetGetEapDomainSuffixMatch) {
    sta_network_->setEapDomainSuffixMatch(
        kTestEapMatch, [](const SupplicantStatus& status) {
            EXPECT_EQ(SupplicantStatusCode::SUCCESS, status.code);
        });
    sta_network_->getEapDomainSuffixMatch([&](const SupplicantStatus& status,
                                              const hidl_string& match) {
        EXPECT_EQ(SupplicantStatusCode::SUCCESS, status.code);
        EXPECT_EQ(kTestEapMatch, std::string(match.c_str()));
    });
}

/*
 * SetGetEapEngine
 */
TEST_F(SupplicantStaNetworkHidlTest, SetGetEapEngine) {
    sta_network_->setEapEngine(
        true, [](const SupplicantStatus& status) {
            EXPECT_EQ(SupplicantStatusCode::SUCCESS, status.code);
        });
    sta_network_->getEapEngine([&](const SupplicantStatus& status,
                                   bool enable) {
        EXPECT_EQ(SupplicantStatusCode::SUCCESS, status.code);
        EXPECT_EQ(true, enable);
    });
}

/*
 * SetGetEapEngineID
 */
TEST_F(SupplicantStaNetworkHidlTest, SetGetEapEngineID) {
    sta_network_->setEapEngineID(
        kTestEapEngineID, [](const SupplicantStatus& status) {
            EXPECT_EQ(SupplicantStatusCode::SUCCESS, status.code);
        });
    sta_network_->getEapEngineID([&](const SupplicantStatus& status,
                                     const hidl_string& id) {
        EXPECT_EQ(SupplicantStatusCode::SUCCESS, status.code);
        EXPECT_EQ(kTestEapEngineID, std::string(id.c_str()));
    });
}

/*
 * Enable
 */
TEST_F(SupplicantStaNetworkHidlTest, Enable) {
    // wpa_supplicant doesn't perform any connection initiation
    // unless atleast the Ssid and Ket mgmt params are set.
    sta_network_->setSsid(ssid_, [](const SupplicantStatus& status) {
        EXPECT_EQ(SupplicantStatusCode::SUCCESS, status.code);
    });
    sta_network_->setKeyMgmt(kTestKeyMgmt, [](const SupplicantStatus& status) {
        EXPECT_EQ(SupplicantStatusCode::SUCCESS, status.code);
    });

    sta_network_->enable(false, [](const SupplicantStatus& status) {
        EXPECT_EQ(SupplicantStatusCode::SUCCESS, status.code);
    });
    sta_network_->enable(true, [](const SupplicantStatus& status) {
        EXPECT_EQ(SupplicantStatusCode::SUCCESS, status.code);
    });

    // Now remove the network and ensure that the calls fail.
    removeNetwork();
    sta_network_->enable(true, [](const SupplicantStatus& status) {
        EXPECT_EQ(SupplicantStatusCode::FAILURE_NETWORK_INVALID, status.code);
    });
}

/*
 * Disable
 */
TEST_F(SupplicantStaNetworkHidlTest, Disable) {
    // wpa_supplicant doesn't perform any connection initiation
    // unless atleast the Ssid and Ket mgmt params are set.
    sta_network_->setSsid(ssid_, [](const SupplicantStatus& status) {
        EXPECT_EQ(SupplicantStatusCode::SUCCESS, status.code);
    });
    sta_network_->setKeyMgmt(kTestKeyMgmt, [](const SupplicantStatus& status) {
        EXPECT_EQ(SupplicantStatusCode::SUCCESS, status.code);
    });

    sta_network_->disable([](const SupplicantStatus& status) {
        EXPECT_EQ(SupplicantStatusCode::SUCCESS, status.code);
    });
    // Now remove the network and ensure that the calls fail.
    removeNetwork();
    sta_network_->disable([](const SupplicantStatus& status) {
        EXPECT_EQ(SupplicantStatusCode::FAILURE_NETWORK_INVALID, status.code);
    });
}

/*
 * Select.
 */
TEST_F(SupplicantStaNetworkHidlTest, Select) {
    // wpa_supplicant doesn't perform any connection initiation
    // unless atleast the Ssid and Ket mgmt params are set.
    sta_network_->setSsid(ssid_, [](const SupplicantStatus& status) {
        EXPECT_EQ(SupplicantStatusCode::SUCCESS, status.code);
    });
    sta_network_->setKeyMgmt(kTestKeyMgmt, [](const SupplicantStatus& status) {
        EXPECT_EQ(SupplicantStatusCode::SUCCESS, status.code);
    });

    sta_network_->select([](const SupplicantStatus& status) {
        EXPECT_EQ(SupplicantStatusCode::SUCCESS, status.code);
    });
    // Now remove the network and ensure that the calls fail.
    removeNetwork();
    sta_network_->select([](const SupplicantStatus& status) {
        EXPECT_EQ(SupplicantStatusCode::FAILURE_NETWORK_INVALID, status.code);
    });
}

/*
 * SendNetworkEapSimGsmAuthResponse
 */
TEST_F(SupplicantStaNetworkHidlTest, SendNetworkEapSimGsmAuthResponse) {
    std::vector<ISupplicantStaNetwork::NetworkResponseEapSimGsmAuthParams>
        params;
    ISupplicantStaNetwork::NetworkResponseEapSimGsmAuthParams param;
    memcpy(param.kc.data(), kTestKc, param.kc.size());
    memcpy(param.sres.data(), kTestSres, param.sres.size());
    params.push_back(param);
    sta_network_->sendNetworkEapSimGsmAuthResponse(
        params, [](const SupplicantStatus& status) {
            EXPECT_EQ(SupplicantStatusCode::SUCCESS, status.code);
        });
}

/*
 * SendNetworkEapSimGsmAuthFailure
 */
TEST_F(SupplicantStaNetworkHidlTest, SendNetworkEapSimGsmAuthFailure) {
    EXPECT_EQ(SupplicantStatusCode::SUCCESS,
              HIDL_INVOKE(sta_network_, sendNetworkEapSimGsmAuthFailure).code);
}

/*
 * SendNetworkEapSimUmtsAuthResponse
 */
TEST_F(SupplicantStaNetworkHidlTest, SendNetworkEapSimUmtsAuthResponse) {
    ISupplicantStaNetwork::NetworkResponseEapSimUmtsAuthParams params;
    params.res = std::vector<uint8_t>(kTestRes, kTestRes + sizeof(kTestRes));
    memcpy(params.ik.data(), kTestIk, params.ik.size());
    memcpy(params.ck.data(), kTestCk, params.ck.size());
    sta_network_->sendNetworkEapSimUmtsAuthResponse(
        params, [](const SupplicantStatus& status) {
            EXPECT_EQ(SupplicantStatusCode::SUCCESS, status.code);
        });
}

/*
 * SendNetworkEapSimUmtsAuthFailure
 */
TEST_F(SupplicantStaNetworkHidlTest, SendNetworkEapSimUmtsAuthFailure) {
    EXPECT_EQ(SupplicantStatusCode::SUCCESS,
              HIDL_INVOKE(sta_network_, sendNetworkEapSimUmtsAuthFailure).code);
}

/*
 * SendNetworkEapSimUmtsAutsResponse
 */
TEST_F(SupplicantStaNetworkHidlTest, SendNetworkEapSimUmtsAutsResponse) {
    EXPECT_EQ(SupplicantStatusCode::SUCCESS,
              HIDL_INVOKE(sta_network_, sendNetworkEapSimUmtsAutsResponse,
                          kTestAutParam)
                  .code);
}

/*
 * SendNetworkEapIdentityResponse
 */
TEST_F(SupplicantStaNetworkHidlTest, SendNetworkEapIdentityResponse) {
    sta_network_->sendNetworkEapIdentityResponse(
        std::vector<uint8_t>(kTestIdentity,
                             kTestIdentity + sizeof(kTestIdentity)),
        [](const SupplicantStatus& status) {
            EXPECT_EQ(SupplicantStatusCode::SUCCESS, status.code);
        });
}

/*
 * SetUpdateIdentifier
 */
TEST_F(SupplicantStaNetworkHidlTest, SetUpdateIdentifier) {
    EXPECT_EQ(
        SupplicantStatusCode::SUCCESS,
        HIDL_INVOKE(sta_network_, setUpdateIdentifier, kTestUpdateIdentifier)
            .code);
}

/*
 * SetProactiveKeyCaching
 */
TEST_F(SupplicantStaNetworkHidlTest, SetProactiveKeyCaching) {
    EXPECT_EQ(SupplicantStatusCode::SUCCESS,
              HIDL_INVOKE(sta_network_, setProactiveKeyCaching, true).code);
    EXPECT_EQ(SupplicantStatusCode::SUCCESS,
              HIDL_INVOKE(sta_network_, setProactiveKeyCaching, false).code);
}

/*
 * GetWpsNfcConfigurationToken
 */
TEST_F(SupplicantStaNetworkHidlTest, GetWpsNfcConfigurationToken) {
    ASSERT_EQ(SupplicantStatusCode::SUCCESS,
              HIDL_INVOKE(sta_network_, setSsid, ssid_).code);
    ASSERT_EQ(SupplicantStatusCode::SUCCESS,
              HIDL_INVOKE(sta_network_, setKeyMgmt, kTestKeyMgmt).code);
    ASSERT_EQ(
        SupplicantStatusCode::SUCCESS,
        HIDL_INVOKE(sta_network_, setPskPassphrase, kTestPskPassphrase).code);
    const auto& status_and_token =
        HIDL_INVOKE(sta_network_, getWpsNfcConfigurationToken);
    EXPECT_EQ(SupplicantStatusCode::SUCCESS, status_and_token.first.code);
    EXPECT_FALSE(0 == status_and_token.second.size());
}
