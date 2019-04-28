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

#define LOG_TAG "keymaster_hidl_hal_test"
#include <cutils/log.h>

#include <iostream>

#include <openssl/evp.h>
#include <openssl/x509.h>

#include <android/hardware/keymaster/3.0/IKeymasterDevice.h>
#include <android/hardware/keymaster/3.0/types.h>

#include <cutils/properties.h>

#include <keymaster/keymaster_configuration.h>

#include "authorization_set.h"
#include "key_param_output.h"

#include <VtsHalHidlTargetTestBase.h>

#include "attestation_record.h"
#include "openssl_utils.h"

using ::android::sp;

using ::std::string;

// This service_name will be passed to getService when retrieving the keymaster service to test.  To
// change it from "default" specify the selected service name on the command line.  The first
// non-gtest argument will be used as the service name.
string service_name = "default";

static bool arm_deleteAllKeys = false;
static bool dump_Attestations = false;

namespace android {
namespace hardware {

template <typename T> bool operator==(const hidl_vec<T>& a, const hidl_vec<T>& b) {
    if (a.size() != b.size()) {
        return false;
    }
    for (size_t i = 0; i < a.size(); ++i) {
        if (a[i] != b[i]) {
            return false;
        }
    }
    return true;
}

namespace keymaster {
namespace V3_0 {

bool operator==(const KeyParameter& a, const KeyParameter& b) {
    if (a.tag != b.tag) {
        return false;
    }

    switch (a.tag) {

    /* Boolean tags */
    case Tag::INVALID:
    case Tag::CALLER_NONCE:
    case Tag::INCLUDE_UNIQUE_ID:
    case Tag::ECIES_SINGLE_HASH_MODE:
    case Tag::BOOTLOADER_ONLY:
    case Tag::NO_AUTH_REQUIRED:
    case Tag::ALLOW_WHILE_ON_BODY:
    case Tag::EXPORTABLE:
    case Tag::ALL_APPLICATIONS:
    case Tag::ROLLBACK_RESISTANT:
    case Tag::RESET_SINCE_ID_ROTATION:
        return true;

    /* Integer tags */
    case Tag::KEY_SIZE:
    case Tag::MIN_MAC_LENGTH:
    case Tag::MIN_SECONDS_BETWEEN_OPS:
    case Tag::MAX_USES_PER_BOOT:
    case Tag::ALL_USERS:
    case Tag::USER_ID:
    case Tag::OS_VERSION:
    case Tag::OS_PATCHLEVEL:
    case Tag::MAC_LENGTH:
    case Tag::AUTH_TIMEOUT:
        return a.f.integer == b.f.integer;

    /* Long integer tags */
    case Tag::RSA_PUBLIC_EXPONENT:
    case Tag::USER_SECURE_ID:
        return a.f.longInteger == b.f.longInteger;

    /* Date-time tags */
    case Tag::ACTIVE_DATETIME:
    case Tag::ORIGINATION_EXPIRE_DATETIME:
    case Tag::USAGE_EXPIRE_DATETIME:
    case Tag::CREATION_DATETIME:
        return a.f.dateTime == b.f.dateTime;

    /* Bytes tags */
    case Tag::APPLICATION_ID:
    case Tag::APPLICATION_DATA:
    case Tag::ROOT_OF_TRUST:
    case Tag::UNIQUE_ID:
    case Tag::ATTESTATION_CHALLENGE:
    case Tag::ATTESTATION_APPLICATION_ID:
    case Tag::ATTESTATION_ID_BRAND:
    case Tag::ATTESTATION_ID_DEVICE:
    case Tag::ATTESTATION_ID_PRODUCT:
    case Tag::ATTESTATION_ID_SERIAL:
    case Tag::ATTESTATION_ID_IMEI:
    case Tag::ATTESTATION_ID_MEID:
    case Tag::ATTESTATION_ID_MANUFACTURER:
    case Tag::ATTESTATION_ID_MODEL:
    case Tag::ASSOCIATED_DATA:
    case Tag::NONCE:
    case Tag::AUTH_TOKEN:
        return a.blob == b.blob;

    /* Enum tags */
    case Tag::PURPOSE:
        return a.f.purpose == b.f.purpose;
    case Tag::ALGORITHM:
        return a.f.algorithm == b.f.algorithm;
    case Tag::BLOCK_MODE:
        return a.f.blockMode == b.f.blockMode;
    case Tag::DIGEST:
        return a.f.digest == b.f.digest;
    case Tag::PADDING:
        return a.f.paddingMode == b.f.paddingMode;
    case Tag::EC_CURVE:
        return a.f.ecCurve == b.f.ecCurve;
    case Tag::BLOB_USAGE_REQUIREMENTS:
        return a.f.keyBlobUsageRequirements == b.f.keyBlobUsageRequirements;
    case Tag::USER_AUTH_TYPE:
        return a.f.integer == b.f.integer;
    case Tag::ORIGIN:
        return a.f.origin == b.f.origin;

    /* Unsupported tags */
    case Tag::KDF:
        return false;
    }
}

bool operator==(const AuthorizationSet& a, const AuthorizationSet& b) {
    return a.size() == b.size() && std::equal(a.begin(), a.end(), b.begin());
}

bool operator==(const KeyCharacteristics& a, const KeyCharacteristics& b) {
    // This isn't very efficient. Oh, well.
    AuthorizationSet a_sw(a.softwareEnforced);
    AuthorizationSet b_sw(b.softwareEnforced);
    AuthorizationSet a_tee(b.teeEnforced);
    AuthorizationSet b_tee(b.teeEnforced);

    a_sw.Sort();
    b_sw.Sort();
    a_tee.Sort();
    b_tee.Sort();

    return a_sw == b_sw && a_tee == b_sw;
}

::std::ostream& operator<<(::std::ostream& os, const AuthorizationSet& set) {
    if (set.size() == 0)
        os << "(Empty)" << ::std::endl;
    else {
        os << "\n";
        for (size_t i = 0; i < set.size(); ++i)
            os << set[i] << ::std::endl;
    }
    return os;
}

namespace test {
namespace {

template <TagType tag_type, Tag tag, typename ValueT>
bool contains(hidl_vec<KeyParameter>& set, TypedTag<tag_type, tag> ttag, ValueT expected_value) {
    size_t count = std::count_if(set.begin(), set.end(), [&](const KeyParameter& param) {
        return param.tag == tag && accessTagValue(ttag, param) == expected_value;
    });
    return count == 1;
}

template <TagType tag_type, Tag tag>
bool contains(hidl_vec<KeyParameter>& set, TypedTag<tag_type, tag>) {
    size_t count = std::count_if(set.begin(), set.end(),
                                 [&](const KeyParameter& param) { return param.tag == tag; });
    return count > 0;
}

constexpr char hex_value[256] = {0, 0,  0,  0,  0,  0,  0,  0, 0, 0, 0, 0, 0, 0, 0, 0,  //
                                 0, 0,  0,  0,  0,  0,  0,  0, 0, 0, 0, 0, 0, 0, 0, 0,  //
                                 0, 0,  0,  0,  0,  0,  0,  0, 0, 0, 0, 0, 0, 0, 0, 0,  //
                                 0, 1,  2,  3,  4,  5,  6,  7, 8, 9, 0, 0, 0, 0, 0, 0,  // '0'..'9'
                                 0, 10, 11, 12, 13, 14, 15, 0, 0, 0, 0, 0, 0, 0, 0, 0,  // 'A'..'F'
                                 0, 0,  0,  0,  0,  0,  0,  0, 0, 0, 0, 0, 0, 0, 0, 0,  //
                                 0, 10, 11, 12, 13, 14, 15, 0, 0, 0, 0, 0, 0, 0, 0, 0,  // 'a'..'f'
                                 0, 0,  0,  0,  0,  0,  0,  0, 0, 0, 0, 0, 0, 0, 0, 0,  //
                                 0, 0,  0,  0,  0,  0,  0,  0, 0, 0, 0, 0, 0, 0, 0, 0,  //
                                 0, 0,  0,  0,  0,  0,  0,  0, 0, 0, 0, 0, 0, 0, 0, 0,  //
                                 0, 0,  0,  0,  0,  0,  0,  0, 0, 0, 0, 0, 0, 0, 0, 0,  //
                                 0, 0,  0,  0,  0,  0,  0,  0, 0, 0, 0, 0, 0, 0, 0, 0,  //
                                 0, 0,  0,  0,  0,  0,  0,  0, 0, 0, 0, 0, 0, 0, 0, 0,  //
                                 0, 0,  0,  0,  0,  0,  0,  0, 0, 0, 0, 0, 0, 0, 0, 0,  //
                                 0, 0,  0,  0,  0,  0,  0,  0, 0, 0, 0, 0, 0, 0, 0, 0,  //
                                 0, 0,  0,  0,  0,  0,  0,  0, 0, 0, 0, 0, 0, 0, 0, 0};

string hex2str(string a) {
    string b;
    size_t num = a.size() / 2;
    b.resize(num);
    for (size_t i = 0; i < num; i++) {
        b[i] = (hex_value[a[i * 2] & 0xFF] << 4) + (hex_value[a[i * 2 + 1] & 0xFF]);
    }
    return b;
}

char nibble2hex[16] = {'0', '1', '2', '3', '4', '5', '6', '7',
                       '8', '9', 'a', 'b', 'c', 'd', 'e', 'f'};

string bin2hex(const hidl_vec<uint8_t>& data) {
    string retval;
    retval.reserve(data.size() * 2 + 1);
    for (uint8_t byte : data) {
        retval.push_back(nibble2hex[0x0F & (byte >> 4)]);
        retval.push_back(nibble2hex[0x0F & byte]);
    }
    return retval;
}

string rsa_key = hex2str(
    "30820275020100300d06092a864886f70d01010105000482025f3082025b"
    "02010002818100c6095409047d8634812d5a218176e45c41d60a75b13901"
    "f234226cffe776521c5a77b9e389417b71c0b6a44d13afe4e4a2805d46c9"
    "da2935adb1ff0c1f24ea06e62b20d776430a4d435157233c6f916783c30e"
    "310fcbd89b85c2d56771169785ac12bca244abda72bfb19fc44d27c81e1d"
    "92de284f4061edfd99280745ea6d2502030100010281801be0f04d9cae37"
    "18691f035338308e91564b55899ffb5084d2460e6630257e05b3ceab0297"
    "2dfabcd6ce5f6ee2589eb67911ed0fac16e43a444b8c861e544a05933657"
    "72f8baf6b22fc9e3c5f1024b063ac080a7b2234cf8aee8f6c47bbf4fd3ac"
    "e7240290bef16c0b3f7f3cdd64ce3ab5912cf6e32f39ab188358afcccd80"
    "81024100e4b49ef50f765d3b24dde01aceaaf130f2c76670a91a61ae08af"
    "497b4a82be6dee8fcdd5e3f7ba1cfb1f0c926b88f88c92bfab137fba2285"
    "227b83c342ff7c55024100ddabb5839c4c7f6bf3d4183231f005b31aa58a"
    "ffdda5c79e4cce217f6bc930dbe563d480706c24e9ebfcab28a6cdefd324"
    "b77e1bf7251b709092c24ff501fd91024023d4340eda3445d8cd26c14411"
    "da6fdca63c1ccd4b80a98ad52b78cc8ad8beb2842c1d280405bc2f6c1bea"
    "214a1d742ab996b35b63a82a5e470fa88dbf823cdd02401b7b57449ad30d"
    "1518249a5f56bb98294d4b6ac12ffc86940497a5a5837a6cf946262b4945"
    "26d328c11e1126380fde04c24f916dec250892db09a6d77cdba351024077"
    "62cd8f4d050da56bd591adb515d24d7ccd32cca0d05f866d583514bd7324"
    "d5f33645e8ed8b4a1cb3cc4a1d67987399f2a09f5b3fb68c88d5e5d90ac3"
    "3492d6");

string ec_256_key = hex2str(
    "308187020100301306072a8648ce3d020106082a8648ce3d030107046d30"
    "6b0201010420737c2ecd7b8d1940bf2930aa9b4ed3ff941eed09366bc032"
    "99986481f3a4d859a14403420004bf85d7720d07c25461683bc648b4778a"
    "9a14dd8a024e3bdd8c7ddd9ab2b528bbc7aa1b51f14ebbbb0bd0ce21bcc4"
    "1c6eb00083cf3376d11fd44949e0b2183bfe");

string ec_521_key = hex2str(
    "3081EE020100301006072A8648CE3D020106052B810400230481D63081D3"
    "02010104420011458C586DB5DAA92AFAB03F4FE46AA9D9C3CE9A9B7A006A"
    "8384BEC4C78E8E9D18D7D08B5BCFA0E53C75B064AD51C449BAE0258D54B9"
    "4B1E885DED08ED4FB25CE9A1818903818600040149EC11C6DF0FA122C6A9"
    "AFD9754A4FA9513A627CA329E349535A5629875A8ADFBE27DCB932C05198"
    "6377108D054C28C6F39B6F2C9AF81802F9F326B842FF2E5F3C00AB7635CF"
    "B36157FC0882D574A10D839C1A0C049DC5E0D775E2EE50671A208431BB45"
    "E78E70BEFE930DB34818EE4D5C26259F5C6B8E28A652950F9F88D7B4B2C9"
    "D9");

struct RSA_Delete {
    void operator()(RSA* p) { RSA_free(p); }
};

X509* parse_cert_blob(const hidl_vec<uint8_t>& blob) {
    const uint8_t* p = blob.data();
    return d2i_X509(nullptr, &p, blob.size());
}

bool verify_chain(const hidl_vec<hidl_vec<uint8_t>>& chain) {
    for (size_t i = 0; i < chain.size() - 1; ++i) {
        X509_Ptr key_cert(parse_cert_blob(chain[i]));
        X509_Ptr signing_cert;
        if (i < chain.size() - 1) {
            signing_cert.reset(parse_cert_blob(chain[i + 1]));
        } else {
            signing_cert.reset(parse_cert_blob(chain[i]));
        }
        EXPECT_TRUE(!!key_cert.get() && !!signing_cert.get());
        if (!key_cert.get() || !signing_cert.get()) return false;

        EVP_PKEY_Ptr signing_pubkey(X509_get_pubkey(signing_cert.get()));
        EXPECT_TRUE(!!signing_pubkey.get());
        if (!signing_pubkey.get()) return false;

        EXPECT_EQ(1, X509_verify(key_cert.get(), signing_pubkey.get()))
            << "Verification of certificate " << i << " failed";

        char* cert_issuer =  //
            X509_NAME_oneline(X509_get_issuer_name(key_cert.get()), nullptr, 0);
        char* signer_subj =
            X509_NAME_oneline(X509_get_subject_name(signing_cert.get()), nullptr, 0);
        EXPECT_STREQ(cert_issuer, signer_subj) << "Cert " << i
                                               << " has wrong issuer.  (Possibly b/38394614)";
        if (i == 0) {
            char* cert_sub = X509_NAME_oneline(X509_get_subject_name(key_cert.get()), nullptr, 0);
            EXPECT_STREQ("/CN=Android Keystore Key", cert_sub)
                << "Cert " << i << " has wrong subject.  (Possibly b/38394614)";
            free(cert_sub);
        }

        free(cert_issuer);
        free(signer_subj);

        if (dump_Attestations) std::cout << bin2hex(chain[i]) << std::endl;
    }

    return true;
}

// Extract attestation record from cert. Returned object is still part of cert; don't free it
// separately.
ASN1_OCTET_STRING* get_attestation_record(X509* certificate) {
    ASN1_OBJECT_Ptr oid(OBJ_txt2obj(kAttestionRecordOid, 1 /* dotted string format */));
    EXPECT_TRUE(!!oid.get());
    if (!oid.get()) return nullptr;

    int location = X509_get_ext_by_OBJ(certificate, oid.get(), -1 /* search from beginning */);
    EXPECT_NE(-1, location);
    if (location == -1) return nullptr;

    X509_EXTENSION* attest_rec_ext = X509_get_ext(certificate, location);
    EXPECT_TRUE(!!attest_rec_ext);
    if (!attest_rec_ext) return nullptr;

    ASN1_OCTET_STRING* attest_rec = X509_EXTENSION_get_data(attest_rec_ext);
    EXPECT_TRUE(!!attest_rec);
    return attest_rec;
}

bool tag_in_list(const KeyParameter& entry) {
    // Attestations don't contain everything in key authorization lists, so we need to filter
    // the key lists to produce the lists that we expect to match the attestations.
    auto tag_list = {
        Tag::USER_ID, Tag::INCLUDE_UNIQUE_ID, Tag::BLOB_USAGE_REQUIREMENTS,
        Tag::EC_CURVE /* Tag::EC_CURVE will be included by KM2 implementations */,
    };
    return std::find(tag_list.begin(), tag_list.end(), entry.tag) != tag_list.end();
}

AuthorizationSet filter_tags(const AuthorizationSet& set) {
    AuthorizationSet filtered;
    std::remove_copy_if(set.begin(), set.end(), std::back_inserter(filtered), tag_in_list);
    return filtered;
}

std::string make_string(const uint8_t* data, size_t length) {
    return std::string(reinterpret_cast<const char*>(data), length);
}

template <size_t N> std::string make_string(const uint8_t (&a)[N]) {
    return make_string(a, N);
}

class HidlBuf : public hidl_vec<uint8_t> {
    typedef hidl_vec<uint8_t> super;

  public:
    HidlBuf() {}
    HidlBuf(const super& other) : super(other) {}
    HidlBuf(super&& other) : super(std::move(other)) {}
    explicit HidlBuf(const std::string& other) : HidlBuf() { *this = other; }

    HidlBuf& operator=(const super& other) {
        super::operator=(other);
        return *this;
    }

    HidlBuf& operator=(super&& other) {
        super::operator=(std::move(other));
        return *this;
    }

    HidlBuf& operator=(const string& other) {
        resize(other.size());
        for (size_t i = 0; i < other.size(); ++i) {
            (*this)[i] = static_cast<uint8_t>(other[i]);
        }
        return *this;
    }

    string to_string() const { return string(reinterpret_cast<const char*>(data()), size()); }
};

constexpr uint64_t kOpHandleSentinel = 0xFFFFFFFFFFFFFFFF;

}  // namespace

class KeymasterHidlTest : public ::testing::VtsHalHidlTargetTestBase {
  public:
    void TearDown() override {
        if (key_blob_.size()) {
            CheckedDeleteKey();
        }
        AbortIfNeeded();
    }

    // SetUpTestCase runs only once per test case, not once per test.
    static void SetUpTestCase() {
        keymaster_ = IKeymasterDevice::getService(service_name);
        ASSERT_NE(keymaster_, nullptr);

        ASSERT_TRUE(
            keymaster_
                ->getHardwareFeatures([&](bool isSecure, bool supportsEc, bool supportsSymmetric,
                                          bool supportsAttestation, bool supportsAllDigests,
                                          const hidl_string& name, const hidl_string& author) {
                    is_secure_ = isSecure;
                    supports_ec_ = supportsEc;
                    supports_symmetric_ = supportsSymmetric;
                    supports_attestation_ = supportsAttestation;
                    supports_all_digests_ = supportsAllDigests;
                    name_ = name;
                    author_ = author;
                })
                .isOk());

        os_version_ = ::keymaster::GetOsVersion();
        os_patch_level_ = ::keymaster::GetOsPatchlevel();
    }

    static void TearDownTestCase() { keymaster_.clear(); }

    static IKeymasterDevice& keymaster() { return *keymaster_; }
    static uint32_t os_version() { return os_version_; }
    static uint32_t os_patch_level() { return os_patch_level_; }

    AuthorizationSet UserAuths() { return AuthorizationSetBuilder().Authorization(TAG_USER_ID, 7); }

    ErrorCode GenerateKey(const AuthorizationSet& key_desc, HidlBuf* key_blob,
                          KeyCharacteristics* key_characteristics) {
        EXPECT_NE(key_blob, nullptr);
        EXPECT_NE(key_characteristics, nullptr);
        EXPECT_EQ(0U, key_blob->size());

        ErrorCode error;
        EXPECT_TRUE(keymaster_
                        ->generateKey(key_desc.hidl_data(),
                                      [&](ErrorCode hidl_error, const HidlBuf& hidl_key_blob,
                                          const KeyCharacteristics& hidl_key_characteristics) {
                                          error = hidl_error;
                                          *key_blob = hidl_key_blob;
                                          *key_characteristics = hidl_key_characteristics;
                                      })
                        .isOk());
        // On error, blob & characteristics should be empty.
        if (error != ErrorCode::OK) {
            EXPECT_EQ(0U, key_blob->size());
            EXPECT_EQ(0U, (key_characteristics->softwareEnforced.size() +
                           key_characteristics->teeEnforced.size()));
        }
        return error;
    }

    ErrorCode GenerateKey(const AuthorizationSet& key_desc) {
        return GenerateKey(key_desc, &key_blob_, &key_characteristics_);
    }

    ErrorCode ImportKey(const AuthorizationSet& key_desc, KeyFormat format,
                        const string& key_material, HidlBuf* key_blob,
                        KeyCharacteristics* key_characteristics) {
        ErrorCode error;
        EXPECT_TRUE(keymaster_
                        ->importKey(key_desc.hidl_data(), format, HidlBuf(key_material),
                                    [&](ErrorCode hidl_error, const HidlBuf& hidl_key_blob,
                                        const KeyCharacteristics& hidl_key_characteristics) {
                                        error = hidl_error;
                                        *key_blob = hidl_key_blob;
                                        *key_characteristics = hidl_key_characteristics;
                                    })
                        .isOk());
        // On error, blob & characteristics should be empty.
        if (error != ErrorCode::OK) {
            EXPECT_EQ(0U, key_blob->size());
            EXPECT_EQ(0U, (key_characteristics->softwareEnforced.size() +
                           key_characteristics->teeEnforced.size()));
        }
        return error;
    }

    ErrorCode ImportKey(const AuthorizationSet& key_desc, KeyFormat format,
                        const string& key_material) {
        return ImportKey(key_desc, format, key_material, &key_blob_, &key_characteristics_);
    }

    ErrorCode ExportKey(KeyFormat format, const HidlBuf& key_blob, const HidlBuf& client_id,
                        const HidlBuf& app_data, HidlBuf* key_material) {
        ErrorCode error;
        EXPECT_TRUE(
            keymaster_
                ->exportKey(format, key_blob, client_id, app_data,
                            [&](ErrorCode hidl_error_code, const HidlBuf& hidl_key_material) {
                                error = hidl_error_code;
                                *key_material = hidl_key_material;
                            })
                .isOk());
        // On error, blob should be empty.
        if (error != ErrorCode::OK) {
            EXPECT_EQ(0U, key_material->size());
        }
        return error;
    }

    ErrorCode ExportKey(KeyFormat format, HidlBuf* key_material) {
        HidlBuf client_id, app_data;
        return ExportKey(format, key_blob_, client_id, app_data, key_material);
    }

    ErrorCode DeleteKey(HidlBuf* key_blob, bool keep_key_blob = false) {
        auto rc = keymaster_->deleteKey(*key_blob);
        if (!keep_key_blob) *key_blob = HidlBuf();
        if (!rc.isOk()) return ErrorCode::UNKNOWN_ERROR;
        return rc;
    }

    ErrorCode DeleteKey(bool keep_key_blob = false) {
        return DeleteKey(&key_blob_, keep_key_blob);
    }

    ErrorCode DeleteAllKeys() {
        ErrorCode error = keymaster_->deleteAllKeys();
        return error;
    }

    void CheckedDeleteKey(HidlBuf* key_blob, bool keep_key_blob = false) {
        auto rc = DeleteKey(key_blob, keep_key_blob);
        EXPECT_TRUE(rc == ErrorCode::OK || rc == ErrorCode::UNIMPLEMENTED);
    }

    void CheckedDeleteKey() { CheckedDeleteKey(&key_blob_); }

    ErrorCode GetCharacteristics(const HidlBuf& key_blob, const HidlBuf& client_id,
                                 const HidlBuf& app_data, KeyCharacteristics* key_characteristics) {
        ErrorCode error = ErrorCode::UNKNOWN_ERROR;
        EXPECT_TRUE(
            keymaster_
                ->getKeyCharacteristics(
                    key_blob, client_id, app_data,
                    [&](ErrorCode hidl_error, const KeyCharacteristics& hidl_key_characteristics) {
                        error = hidl_error, *key_characteristics = hidl_key_characteristics;
                    })
                .isOk());
        return error;
    }

    ErrorCode GetCharacteristics(const HidlBuf& key_blob, KeyCharacteristics* key_characteristics) {
        HidlBuf client_id, app_data;
        return GetCharacteristics(key_blob, client_id, app_data, key_characteristics);
    }

    ErrorCode Begin(KeyPurpose purpose, const HidlBuf& key_blob, const AuthorizationSet& in_params,
                    AuthorizationSet* out_params, OperationHandle* op_handle) {
        SCOPED_TRACE("Begin");
        ErrorCode error;
        OperationHandle saved_handle = *op_handle;
        EXPECT_TRUE(
            keymaster_
                ->begin(purpose, key_blob, in_params.hidl_data(),
                        [&](ErrorCode hidl_error, const hidl_vec<KeyParameter>& hidl_out_params,
                            uint64_t hidl_op_handle) {
                            error = hidl_error;
                            *out_params = hidl_out_params;
                            *op_handle = hidl_op_handle;
                        })
                .isOk());
        if (error != ErrorCode::OK) {
            // Some implementations may modify *op_handle on error.
            *op_handle = saved_handle;
        }
        return error;
    }

    ErrorCode Begin(KeyPurpose purpose, const AuthorizationSet& in_params,
                    AuthorizationSet* out_params) {
        SCOPED_TRACE("Begin");
        EXPECT_EQ(kOpHandleSentinel, op_handle_);
        return Begin(purpose, key_blob_, in_params, out_params, &op_handle_);
    }

    ErrorCode Begin(KeyPurpose purpose, const AuthorizationSet& in_params) {
        SCOPED_TRACE("Begin");
        AuthorizationSet out_params;
        ErrorCode error = Begin(purpose, in_params, &out_params);
        EXPECT_TRUE(out_params.empty());
        return error;
    }

    ErrorCode Update(OperationHandle op_handle, const AuthorizationSet& in_params,
                     const string& input, AuthorizationSet* out_params, string* output,
                     size_t* input_consumed) {
        SCOPED_TRACE("Update");
        ErrorCode error;
        EXPECT_TRUE(keymaster_
                        ->update(op_handle, in_params.hidl_data(), HidlBuf(input),
                                 [&](ErrorCode hidl_error, uint32_t hidl_input_consumed,
                                     const hidl_vec<KeyParameter>& hidl_out_params,
                                     const HidlBuf& hidl_output) {
                                     error = hidl_error;
                                     out_params->push_back(AuthorizationSet(hidl_out_params));
                                     output->append(hidl_output.to_string());
                                     *input_consumed = hidl_input_consumed;
                                 })
                        .isOk());
        return error;
    }

    ErrorCode Update(const string& input, string* out, size_t* input_consumed) {
        SCOPED_TRACE("Update");
        AuthorizationSet out_params;
        ErrorCode error = Update(op_handle_, AuthorizationSet() /* in_params */, input, &out_params,
                                 out, input_consumed);
        EXPECT_TRUE(out_params.empty());
        return error;
    }

    ErrorCode Finish(OperationHandle op_handle, const AuthorizationSet& in_params,
                     const string& input, const string& signature, AuthorizationSet* out_params,
                     string* output) {
        SCOPED_TRACE("Finish");
        ErrorCode error;
        EXPECT_TRUE(
            keymaster_
                ->finish(op_handle, in_params.hidl_data(), HidlBuf(input), HidlBuf(signature),
                         [&](ErrorCode hidl_error, const hidl_vec<KeyParameter>& hidl_out_params,
                             const HidlBuf& hidl_output) {
                             error = hidl_error;
                             *out_params = hidl_out_params;
                             output->append(hidl_output.to_string());
                         })
                .isOk());
        op_handle_ = kOpHandleSentinel;  // So dtor doesn't Abort().
        return error;
    }

    ErrorCode Finish(const string& message, string* output) {
        SCOPED_TRACE("Finish");
        AuthorizationSet out_params;
        string finish_output;
        ErrorCode error = Finish(op_handle_, AuthorizationSet() /* in_params */, message,
                                 "" /* signature */, &out_params, output);
        if (error != ErrorCode::OK) {
            return error;
        }
        EXPECT_EQ(0U, out_params.size());
        return error;
    }

    ErrorCode Finish(const string& message, const string& signature, string* output) {
        SCOPED_TRACE("Finish");
        AuthorizationSet out_params;
        ErrorCode error = Finish(op_handle_, AuthorizationSet() /* in_params */, message, signature,
                                 &out_params, output);
        op_handle_ = kOpHandleSentinel;  // So dtor doesn't Abort().
        if (error != ErrorCode::OK) {
            return error;
        }
        EXPECT_EQ(0U, out_params.size());
        return error;
    }

    ErrorCode Abort(OperationHandle op_handle) {
        SCOPED_TRACE("Abort");
        auto retval = keymaster_->abort(op_handle);
        EXPECT_TRUE(retval.isOk());
        return retval;
    }

    void AbortIfNeeded() {
        SCOPED_TRACE("AbortIfNeeded");
        if (op_handle_ != kOpHandleSentinel) {
            EXPECT_EQ(ErrorCode::OK, Abort(op_handle_));
            op_handle_ = kOpHandleSentinel;
        }
    }

    ErrorCode AttestKey(const HidlBuf& key_blob, const AuthorizationSet& attest_params,
                        hidl_vec<hidl_vec<uint8_t>>* cert_chain) {
        SCOPED_TRACE("AttestKey");
        ErrorCode error;
        auto rc = keymaster_->attestKey(
            key_blob, attest_params.hidl_data(),
            [&](ErrorCode hidl_error, const hidl_vec<hidl_vec<uint8_t>>& hidl_cert_chain) {
                error = hidl_error;
                *cert_chain = hidl_cert_chain;
            });

        EXPECT_TRUE(rc.isOk()) << rc.description();
        if (!rc.isOk()) return ErrorCode::UNKNOWN_ERROR;

        return error;
    }

    ErrorCode AttestKey(const AuthorizationSet& attest_params,
                        hidl_vec<hidl_vec<uint8_t>>* cert_chain) {
        SCOPED_TRACE("AttestKey");
        return AttestKey(key_blob_, attest_params, cert_chain);
    }

    string ProcessMessage(const HidlBuf& key_blob, KeyPurpose operation, const string& message,
                          const AuthorizationSet& in_params, AuthorizationSet* out_params) {
        SCOPED_TRACE("ProcessMessage");
        AuthorizationSet begin_out_params;
        EXPECT_EQ(ErrorCode::OK,
                  Begin(operation, key_blob, in_params, &begin_out_params, &op_handle_));

        string unused;
        AuthorizationSet finish_params;
        AuthorizationSet finish_out_params;
        string output;
        EXPECT_EQ(ErrorCode::OK,
                  Finish(op_handle_, finish_params, message, unused, &finish_out_params, &output));
        op_handle_ = kOpHandleSentinel;

        out_params->push_back(begin_out_params);
        out_params->push_back(finish_out_params);
        return output;
    }

    string SignMessage(const HidlBuf& key_blob, const string& message,
                       const AuthorizationSet& params) {
        SCOPED_TRACE("SignMessage");
        AuthorizationSet out_params;
        string signature = ProcessMessage(key_blob, KeyPurpose::SIGN, message, params, &out_params);
        EXPECT_TRUE(out_params.empty());
        return signature;
    }

    string SignMessage(const string& message, const AuthorizationSet& params) {
        SCOPED_TRACE("SignMessage");
        return SignMessage(key_blob_, message, params);
    }

    string MacMessage(const string& message, Digest digest, size_t mac_length) {
        SCOPED_TRACE("MacMessage");
        return SignMessage(
            key_blob_, message,
            AuthorizationSetBuilder().Digest(digest).Authorization(TAG_MAC_LENGTH, mac_length));
    }

    void CheckHmacTestVector(const string& key, const string& message, Digest digest,
                             const string& expected_mac) {
        SCOPED_TRACE("CheckHmacTestVector");
        ASSERT_EQ(ErrorCode::OK,
                  ImportKey(AuthorizationSetBuilder()
                                .Authorization(TAG_NO_AUTH_REQUIRED)
                                .HmacKey(key.size() * 8)
                                .Authorization(TAG_MIN_MAC_LENGTH, expected_mac.size() * 8)
                                .Digest(digest),
                            KeyFormat::RAW, key));
        string signature = MacMessage(message, digest, expected_mac.size() * 8);
        EXPECT_EQ(expected_mac, signature) << "Test vector didn't match for digest " << (int)digest;
        CheckedDeleteKey();
    }

    void CheckAesCtrTestVector(const string& key, const string& nonce, const string& message,
                               const string& expected_ciphertext) {
        SCOPED_TRACE("CheckAesCtrTestVector");
        ASSERT_EQ(ErrorCode::OK, ImportKey(AuthorizationSetBuilder()
                                               .Authorization(TAG_NO_AUTH_REQUIRED)
                                               .AesEncryptionKey(key.size() * 8)
                                               .BlockMode(BlockMode::CTR)
                                               .Authorization(TAG_CALLER_NONCE)
                                               .Padding(PaddingMode::NONE),
                                           KeyFormat::RAW, key));

        auto params = AuthorizationSetBuilder()
                          .Authorization(TAG_NONCE, nonce.data(), nonce.size())
                          .BlockMode(BlockMode::CTR)
                          .Padding(PaddingMode::NONE);
        AuthorizationSet out_params;
        string ciphertext = EncryptMessage(key_blob_, message, params, &out_params);
        EXPECT_EQ(expected_ciphertext, ciphertext);
    }

    void VerifyMessage(const HidlBuf& key_blob, const string& message, const string& signature,
                       const AuthorizationSet& params) {
        SCOPED_TRACE("VerifyMessage");
        AuthorizationSet begin_out_params;
        ASSERT_EQ(ErrorCode::OK,
                  Begin(KeyPurpose::VERIFY, key_blob, params, &begin_out_params, &op_handle_));

        string unused;
        AuthorizationSet finish_params;
        AuthorizationSet finish_out_params;
        string output;
        EXPECT_EQ(ErrorCode::OK, Finish(op_handle_, finish_params, message, signature,
                                        &finish_out_params, &output));
        op_handle_ = kOpHandleSentinel;
        EXPECT_TRUE(output.empty());
    }

    void VerifyMessage(const string& message, const string& signature,
                       const AuthorizationSet& params) {
        SCOPED_TRACE("VerifyMessage");
        VerifyMessage(key_blob_, message, signature, params);
    }

    string EncryptMessage(const HidlBuf& key_blob, const string& message,
                          const AuthorizationSet& in_params, AuthorizationSet* out_params) {
        SCOPED_TRACE("EncryptMessage");
        return ProcessMessage(key_blob, KeyPurpose::ENCRYPT, message, in_params, out_params);
    }

    string EncryptMessage(const string& message, const AuthorizationSet& params,
                          AuthorizationSet* out_params) {
        SCOPED_TRACE("EncryptMessage");
        return EncryptMessage(key_blob_, message, params, out_params);
    }

    string EncryptMessage(const string& message, const AuthorizationSet& params) {
        SCOPED_TRACE("EncryptMessage");
        AuthorizationSet out_params;
        string ciphertext = EncryptMessage(message, params, &out_params);
        EXPECT_TRUE(out_params.empty())
            << "Output params should be empty. Contained: " << out_params;
        return ciphertext;
    }

    string DecryptMessage(const HidlBuf& key_blob, const string& ciphertext,
                          const AuthorizationSet& params) {
        SCOPED_TRACE("DecryptMessage");
        AuthorizationSet out_params;
        string plaintext =
            ProcessMessage(key_blob, KeyPurpose::DECRYPT, ciphertext, params, &out_params);
        EXPECT_TRUE(out_params.empty());
        return plaintext;
    }

    string DecryptMessage(const string& ciphertext, const AuthorizationSet& params) {
        SCOPED_TRACE("DecryptMessage");
        return DecryptMessage(key_blob_, ciphertext, params);
    }

    template <TagType tag_type, Tag tag, typename ValueT>
    void CheckKm0CryptoParam(TypedTag<tag_type, tag> ttag, ValueT expected) {
        SCOPED_TRACE("CheckKm0CryptoParam");
        if (is_secure_) {
            EXPECT_TRUE(contains(key_characteristics_.teeEnforced, ttag, expected));
            EXPECT_FALSE(contains(key_characteristics_.softwareEnforced, ttag));
        } else {
            EXPECT_TRUE(contains(key_characteristics_.softwareEnforced, ttag, expected));
            EXPECT_FALSE(contains(key_characteristics_.teeEnforced, ttag));
        }
    }

    template <TagType tag_type, Tag tag, typename ValueT>
    void CheckKm1CryptoParam(TypedTag<tag_type, tag> ttag, ValueT expected) {
        SCOPED_TRACE("CheckKm1CryptoParam");
        if (is_secure_ && supports_symmetric_) {
            EXPECT_TRUE(contains(key_characteristics_.teeEnforced, ttag, expected));
            EXPECT_FALSE(contains(key_characteristics_.softwareEnforced, ttag));
        } else {
            EXPECT_TRUE(contains(key_characteristics_.softwareEnforced, ttag, expected));
            EXPECT_FALSE(contains(key_characteristics_.teeEnforced, ttag));
        }
    }

    template <TagType tag_type, Tag tag, typename ValueT>
    void CheckKm2CryptoParam(TypedTag<tag_type, tag> ttag, ValueT expected) {
        SCOPED_TRACE("CheckKm2CryptoParam");
        if (supports_attestation_) {
            EXPECT_TRUE(contains(key_characteristics_.teeEnforced, ttag, expected));
            EXPECT_FALSE(contains(key_characteristics_.softwareEnforced, ttag));
        } else if (!supports_symmetric_ /* KM version < 1 or SW */) {
            EXPECT_TRUE(contains(key_characteristics_.softwareEnforced, ttag, expected));
            EXPECT_FALSE(contains(key_characteristics_.teeEnforced, ttag));
        }
    }

    void CheckOrigin() {
        SCOPED_TRACE("CheckOrigin");
        if (is_secure_ && supports_symmetric_) {
            EXPECT_TRUE(
                contains(key_characteristics_.teeEnforced, TAG_ORIGIN, KeyOrigin::IMPORTED));
        } else if (is_secure_) {
            EXPECT_TRUE(contains(key_characteristics_.teeEnforced, TAG_ORIGIN, KeyOrigin::UNKNOWN));
        } else {
            EXPECT_TRUE(
                contains(key_characteristics_.softwareEnforced, TAG_ORIGIN, KeyOrigin::IMPORTED));
        }
    }

    static bool IsSecure() { return is_secure_; }
    static bool SupportsEc() { return supports_ec_; }
    static bool SupportsSymmetric() { return supports_symmetric_; }
    static bool SupportsAllDigests() { return supports_all_digests_; }
    static bool SupportsAttestation() { return supports_attestation_; }

    static bool Km2Profile() {
        return SupportsAttestation() && SupportsAllDigests() && SupportsSymmetric() &&
               SupportsEc() && IsSecure();
    }

    static bool Km1Profile() {
        return !SupportsAttestation() && SupportsSymmetric() && SupportsEc() && IsSecure();
    }

    static bool Km0Profile() {
        return !SupportsAttestation() && !SupportsAllDigests() && !SupportsSymmetric() &&
               IsSecure();
    }

    static bool SwOnlyProfile() {
        return !SupportsAttestation() && !SupportsAllDigests() && !SupportsSymmetric() &&
               !SupportsEc() && !IsSecure();
    }

    HidlBuf key_blob_;
    KeyCharacteristics key_characteristics_;
    OperationHandle op_handle_ = kOpHandleSentinel;

  private:
    static sp<IKeymasterDevice> keymaster_;
    static uint32_t os_version_;
    static uint32_t os_patch_level_;

    static bool is_secure_;
    static bool supports_ec_;
    static bool supports_symmetric_;
    static bool supports_attestation_;
    static bool supports_all_digests_;
    static hidl_string name_;
    static hidl_string author_;
};

bool verify_attestation_record(const string& challenge, const string& app_id,
                               AuthorizationSet expected_sw_enforced,
                               AuthorizationSet expected_tee_enforced,
                               const hidl_vec<uint8_t>& attestation_cert) {
    X509_Ptr cert(parse_cert_blob(attestation_cert));
    EXPECT_TRUE(!!cert.get());
    if (!cert.get()) return false;

    ASN1_OCTET_STRING* attest_rec = get_attestation_record(cert.get());
    EXPECT_TRUE(!!attest_rec);
    if (!attest_rec) return false;

    AuthorizationSet att_sw_enforced;
    AuthorizationSet att_tee_enforced;
    uint32_t att_attestation_version;
    uint32_t att_keymaster_version;
    SecurityLevel att_attestation_security_level;
    SecurityLevel att_keymaster_security_level;
    HidlBuf att_challenge;
    HidlBuf att_unique_id;
    HidlBuf att_app_id;
    EXPECT_EQ(ErrorCode::OK,
              parse_attestation_record(attest_rec->data,                 //
                                       attest_rec->length,               //
                                       &att_attestation_version,         //
                                       &att_attestation_security_level,  //
                                       &att_keymaster_version,           //
                                       &att_keymaster_security_level,    //
                                       &att_challenge,                   //
                                       &att_sw_enforced,                 //
                                       &att_tee_enforced,                //
                                       &att_unique_id));

    EXPECT_TRUE(att_attestation_version == 1 || att_attestation_version == 2);

    expected_sw_enforced.push_back(TAG_ATTESTATION_APPLICATION_ID,
                                   HidlBuf(app_id));

    if (!KeymasterHidlTest::IsSecure()) {
        // SW is KM2
        EXPECT_EQ(att_keymaster_version, 2U);
    }

    if (KeymasterHidlTest::SupportsSymmetric()) {
        EXPECT_GE(att_keymaster_version, 1U);
    }

    if (KeymasterHidlTest::SupportsAttestation()) {
        EXPECT_GE(att_keymaster_version, 2U);
    }

    EXPECT_EQ(KeymasterHidlTest::IsSecure() ? SecurityLevel::TRUSTED_ENVIRONMENT
                                            : SecurityLevel::SOFTWARE,
              att_keymaster_security_level);
    EXPECT_EQ(KeymasterHidlTest::SupportsAttestation() ? SecurityLevel::TRUSTED_ENVIRONMENT
                                                       : SecurityLevel::SOFTWARE,
              att_attestation_security_level);

    EXPECT_EQ(challenge.length(), att_challenge.size());
    EXPECT_EQ(0, memcmp(challenge.data(), att_challenge.data(), challenge.length()));

    att_sw_enforced.Sort();
    expected_sw_enforced.Sort();
    EXPECT_EQ(filter_tags(expected_sw_enforced), filter_tags(att_sw_enforced))
        << "(Possibly b/38394619)";

    att_tee_enforced.Sort();
    expected_tee_enforced.Sort();
    EXPECT_EQ(filter_tags(expected_tee_enforced), filter_tags(att_tee_enforced))
        << "(Possibly b/38394619)";

    return true;
}

sp<IKeymasterDevice> KeymasterHidlTest::keymaster_;
uint32_t KeymasterHidlTest::os_version_;
uint32_t KeymasterHidlTest::os_patch_level_;
bool KeymasterHidlTest::is_secure_;
bool KeymasterHidlTest::supports_ec_;
bool KeymasterHidlTest::supports_symmetric_;
bool KeymasterHidlTest::supports_all_digests_;
bool KeymasterHidlTest::supports_attestation_;
hidl_string KeymasterHidlTest::name_;
hidl_string KeymasterHidlTest::author_;

typedef KeymasterHidlTest KeymasterVersionTest;

/*
 * KeymasterVersionTest.SensibleFeatures:
 *
 * Queries keymaster to find the set of features it supports. Fails if the combination doesn't
 * correspond to any well-defined keymaster version.
 */
TEST_F(KeymasterVersionTest, SensibleFeatures) {
    EXPECT_TRUE(Km2Profile() || Km1Profile() || Km0Profile() || SwOnlyProfile())
        << "Keymaster feature set doesn't fit any reasonable profile.  Reported features:"
        << "SupportsAttestation [" << SupportsAttestation() << "], "
        << "SupportsSymmetric [" << SupportsSymmetric() << "], "
        << "SupportsAllDigests [" << SupportsAllDigests() << "], "
        << "SupportsEc [" << SupportsEc() << "], "
        << "IsSecure [" << IsSecure() << "]";
}

class NewKeyGenerationTest : public KeymasterHidlTest {
  protected:
    void CheckBaseParams(const KeyCharacteristics& keyCharacteristics) {
        // TODO(swillden): Distinguish which params should be in which auth list.

        AuthorizationSet auths(keyCharacteristics.teeEnforced);
        auths.push_back(AuthorizationSet(keyCharacteristics.softwareEnforced));

        EXPECT_TRUE(auths.Contains(TAG_ORIGIN, KeyOrigin::GENERATED));

        EXPECT_TRUE(auths.Contains(TAG_PURPOSE, KeyPurpose::SIGN));
        EXPECT_TRUE(auths.Contains(TAG_PURPOSE, KeyPurpose::VERIFY));
        EXPECT_TRUE(auths.Contains(TAG_USER_ID, 7))
            << "User ID should be 7, was " << auths.GetTagValue(TAG_USER_ID);

        // Verify that App ID, App data and ROT are NOT included.
        EXPECT_FALSE(auths.Contains(TAG_ROOT_OF_TRUST));
        EXPECT_FALSE(auths.Contains(TAG_APPLICATION_ID));
        EXPECT_FALSE(auths.Contains(TAG_APPLICATION_DATA));

        // Check that some unexpected tags/values are NOT present.
        EXPECT_FALSE(auths.Contains(TAG_PURPOSE, KeyPurpose::ENCRYPT));
        EXPECT_FALSE(auths.Contains(TAG_PURPOSE, KeyPurpose::DECRYPT));
        EXPECT_FALSE(auths.Contains(TAG_AUTH_TIMEOUT, 301));

        // Now check that unspecified, defaulted tags are correct.
        EXPECT_TRUE(auths.Contains(TAG_CREATION_DATETIME));

        if (SupportsAttestation()) {
            EXPECT_TRUE(auths.Contains(TAG_OS_VERSION, os_version()))
                << "OS version is " << os_version() << " key reported "
                << auths.GetTagValue(TAG_OS_VERSION);
            EXPECT_TRUE(auths.Contains(TAG_OS_PATCHLEVEL, os_patch_level()))
                << "OS patch level is " << os_patch_level() << " key reported "
                << auths.GetTagValue(TAG_OS_PATCHLEVEL);
        }
    }
};

/*
 * NewKeyGenerationTest.Rsa
 *
 * Verifies that keymaster can generate all required RSA key sizes, and that the resulting keys have
 * correct characteristics.
 */
TEST_F(NewKeyGenerationTest, Rsa) {
    for (auto key_size : {1024, 2048, 3072, 4096}) {
        HidlBuf key_blob;
        KeyCharacteristics key_characteristics;
        ASSERT_EQ(ErrorCode::OK, GenerateKey(AuthorizationSetBuilder()
                                                 .RsaSigningKey(key_size, 3)
                                                 .Digest(Digest::NONE)
                                                 .Padding(PaddingMode::NONE)
                                                 .Authorizations(UserAuths()),
                                             &key_blob, &key_characteristics));

        ASSERT_GT(key_blob.size(), 0U);
        CheckBaseParams(key_characteristics);

        AuthorizationSet crypto_params;
        if (IsSecure()) {
            crypto_params = key_characteristics.teeEnforced;
        } else {
            crypto_params = key_characteristics.softwareEnforced;
        }

        EXPECT_TRUE(crypto_params.Contains(TAG_ALGORITHM, KM_ALGORITHM_RSA));
        EXPECT_TRUE(crypto_params.Contains(TAG_KEY_SIZE, key_size));
        EXPECT_TRUE(crypto_params.Contains(TAG_RSA_PUBLIC_EXPONENT, 3));

        CheckedDeleteKey(&key_blob);
    }
}

/*
 * NewKeyGenerationTest.RsaNoDefaultSize
 *
 * Verifies that failing to specify a key size for RSA key generation returns UNSUPPORTED_KEY_SIZE.
 */
TEST_F(NewKeyGenerationTest, RsaNoDefaultSize) {
    ASSERT_EQ(ErrorCode::UNSUPPORTED_KEY_SIZE,
              GenerateKey(AuthorizationSetBuilder()
                              .Authorization(TAG_ALGORITHM, Algorithm::RSA)
                              .Authorization(TAG_RSA_PUBLIC_EXPONENT, 3)
                              .SigningKey()));
}

/*
 * NewKeyGenerationTest.Ecdsa
 *
 * Verifies that keymaster can generate all required EC key sizes, and that the resulting keys have
 * correct characteristics.
 */
TEST_F(NewKeyGenerationTest, Ecdsa) {
    for (auto key_size : {224, 256, 384, 521}) {
        HidlBuf key_blob;
        KeyCharacteristics key_characteristics;
        ASSERT_EQ(ErrorCode::OK, GenerateKey(AuthorizationSetBuilder()
                                                 .EcdsaSigningKey(key_size)
                                                 .Digest(Digest::NONE)
                                                 .Authorizations(UserAuths()),
                                             &key_blob, &key_characteristics));
        ASSERT_GT(key_blob.size(), 0U);
        CheckBaseParams(key_characteristics);

        AuthorizationSet crypto_params;
        if (IsSecure()) {
            crypto_params = key_characteristics.teeEnforced;
        } else {
            crypto_params = key_characteristics.softwareEnforced;
        }

        EXPECT_TRUE(crypto_params.Contains(TAG_ALGORITHM, Algorithm::EC));
        EXPECT_TRUE(crypto_params.Contains(TAG_KEY_SIZE, key_size));

        CheckedDeleteKey(&key_blob);
    }
}

/*
 * NewKeyGenerationTest.EcdsaDefaultSize
 *
 * Verifies that failing to specify a key size for EC key generation returns UNSUPPORTED_KEY_SIZE.
 */
TEST_F(NewKeyGenerationTest, EcdsaDefaultSize) {
    ASSERT_EQ(ErrorCode::UNSUPPORTED_KEY_SIZE,
              GenerateKey(AuthorizationSetBuilder()
                              .Authorization(TAG_ALGORITHM, Algorithm::EC)
                              .SigningKey()
                              .Digest(Digest::NONE)));
}

/*
 * NewKeyGenerationTest.EcdsaInvalidSize
 *
 * Verifies that failing to specify an invalid key size for EC key generation returns
 * UNSUPPORTED_KEY_SIZE.
 */
TEST_F(NewKeyGenerationTest, EcdsaInvalidSize) {
    ASSERT_EQ(ErrorCode::UNSUPPORTED_KEY_SIZE,
              GenerateKey(AuthorizationSetBuilder().EcdsaSigningKey(190).Digest(Digest::NONE)));
}

/*
 * NewKeyGenerationTest.EcdsaMismatchKeySize
 *
 * Verifies that specifying mismatched key size and curve for EC key generation returns
 * INVALID_ARGUMENT.
 */
TEST_F(NewKeyGenerationTest, EcdsaMismatchKeySize) {
    ASSERT_EQ(ErrorCode::INVALID_ARGUMENT,
              GenerateKey(AuthorizationSetBuilder()
                              .EcdsaSigningKey(224)
                              .Authorization(TAG_EC_CURVE, EcCurve::P_256)
                              .Digest(Digest::NONE)))
        << "(Possibly b/36233343)";
}

TEST_F(NewKeyGenerationTest, EcdsaAllValidSizes) {
    size_t valid_sizes[] = {224, 256, 384, 521};
    for (size_t size : valid_sizes) {
        EXPECT_EQ(ErrorCode::OK,
                  GenerateKey(AuthorizationSetBuilder().EcdsaSigningKey(size).Digest(Digest::NONE)))
            << "Failed to generate size: " << size;
        CheckedDeleteKey();
    }
}

/*
 * NewKeyGenerationTest.EcdsaAllValidCurves
 *
 * Verifies that keymaster supports all required EC curves.
 */
TEST_F(NewKeyGenerationTest, EcdsaAllValidCurves) {
    EcCurve curves[] = {EcCurve::P_224, EcCurve::P_256, EcCurve::P_384, EcCurve::P_521};
    for (auto curve : curves) {
        EXPECT_EQ(
            ErrorCode::OK,
            GenerateKey(AuthorizationSetBuilder().EcdsaSigningKey(curve).Digest(Digest::SHA_2_512)))
            << "Failed to generate key on curve: " << curve;
        CheckedDeleteKey();
    }
}

/*
 * NewKeyGenerationTest.Hmac
 *
 * Verifies that keymaster supports all required digests, and that the resulting keys have correct
 * characteristics.
 */
TEST_F(NewKeyGenerationTest, Hmac) {
    for (auto digest : {Digest::MD5, Digest::SHA1, Digest::SHA_2_224, Digest::SHA_2_256,
                        Digest::SHA_2_384, Digest::SHA_2_512}) {
        HidlBuf key_blob;
        KeyCharacteristics key_characteristics;
        constexpr size_t key_size = 128;
        ASSERT_EQ(ErrorCode::OK, GenerateKey(AuthorizationSetBuilder()
                                                 .HmacKey(key_size)
                                                 .Digest(digest)
                                                 .Authorization(TAG_MIN_MAC_LENGTH, 128)
                                                 .Authorizations(UserAuths()),
                                             &key_blob, &key_characteristics));

        ASSERT_GT(key_blob.size(), 0U);
        CheckBaseParams(key_characteristics);

        AuthorizationSet teeEnforced = key_characteristics.teeEnforced;
        AuthorizationSet softwareEnforced = key_characteristics.softwareEnforced;
        if (SupportsAttestation() || SupportsAllDigests()) {
            // Either KM2, which must support all, or KM1 that claims full support
            EXPECT_TRUE(teeEnforced.Contains(TAG_ALGORITHM, Algorithm::HMAC));
            EXPECT_TRUE(teeEnforced.Contains(TAG_KEY_SIZE, key_size));
        } else if (SupportsSymmetric()) {
            if (digest == Digest::SHA1 || digest == Digest::SHA_2_256) {
                // KM1 must support SHA1 and SHA256 in hardware
                EXPECT_TRUE(teeEnforced.Contains(TAG_ALGORITHM, Algorithm::HMAC));
                EXPECT_TRUE(teeEnforced.Contains(TAG_KEY_SIZE, key_size));
            } else {
                // Othere digests may or may not be supported
                EXPECT_TRUE(teeEnforced.Contains(TAG_ALGORITHM, Algorithm::HMAC) ||
                            softwareEnforced.Contains(TAG_ALGORITHM, Algorithm::HMAC));
                EXPECT_TRUE(teeEnforced.Contains(TAG_KEY_SIZE, key_size) ||
                            softwareEnforced.Contains(TAG_KEY_SIZE, key_size));
            }
        } else {
            // KM0 and SW KM do all digests in SW.
            EXPECT_TRUE(softwareEnforced.Contains(TAG_ALGORITHM, Algorithm::HMAC));
            EXPECT_TRUE(softwareEnforced.Contains(TAG_KEY_SIZE, key_size));
        }

        CheckedDeleteKey(&key_blob);
    }
}

/*
 * NewKeyGenerationTest.HmacCheckKeySizes
 *
 * Verifies that keymaster supports all key sizes, and rejects all invalid key sizes.
 */
TEST_F(NewKeyGenerationTest, HmacCheckKeySizes) {
    for (size_t key_size = 0; key_size <= 512; ++key_size) {
        if (key_size < 64 || key_size % 8 != 0) {
            // To keep this test from being very slow, we only test a random fraction of non-byte
            // key sizes.  We test only ~10% of such cases. Since there are 392 of them, we expect
            // to run ~40 of them in each run.
            if (key_size % 8 == 0 || random() % 10 == 0) {
                EXPECT_EQ(ErrorCode::UNSUPPORTED_KEY_SIZE,
                          GenerateKey(AuthorizationSetBuilder()
                                          .HmacKey(key_size)
                                          .Digest(Digest::SHA_2_256)
                                          .Authorization(TAG_MIN_MAC_LENGTH, 256)))
                    << "HMAC key size " << key_size << " invalid (Possibly b/33462346)";
            }
        } else {
            EXPECT_EQ(ErrorCode::OK, GenerateKey(AuthorizationSetBuilder()
                                                     .HmacKey(key_size)
                                                     .Digest(Digest::SHA_2_256)
                                                     .Authorization(TAG_MIN_MAC_LENGTH, 256)));
            CheckedDeleteKey();
        }
    }
}

/*
 * NewKeyGenerationTest.HmacCheckMinMacLengths
 *
 * Verifies that keymaster supports all required MAC lengths and rejects all invalid lengths.  This
 * test is probabilistic in order to keep the runtime down, but any failure prints out the specific
 * MAC length that failed, so reproducing a failed run will be easy.
 */
TEST_F(NewKeyGenerationTest, HmacCheckMinMacLengths) {
    for (size_t min_mac_length = 0; min_mac_length <= 256; ++min_mac_length) {
        if (min_mac_length < 64 || min_mac_length % 8 != 0) {
            // To keep this test from being very long, we only test a random fraction of non-byte
            // lengths.  We test only ~10% of such cases. Since there are 172 of them, we expect to
            // run ~17 of them in each run.
            if (min_mac_length % 8 == 0 || random() % 10 == 0) {
                EXPECT_EQ(ErrorCode::UNSUPPORTED_MIN_MAC_LENGTH,
                          GenerateKey(AuthorizationSetBuilder()
                                          .HmacKey(128)
                                          .Digest(Digest::SHA_2_256)
                                          .Authorization(TAG_MIN_MAC_LENGTH, min_mac_length)))
                    << "HMAC min mac length " << min_mac_length << " invalid.";
            }
        } else {
            EXPECT_EQ(ErrorCode::OK,
                      GenerateKey(AuthorizationSetBuilder()
                                      .HmacKey(128)
                                      .Digest(Digest::SHA_2_256)
                                      .Authorization(TAG_MIN_MAC_LENGTH, min_mac_length)));
            CheckedDeleteKey();
        }
    }
}

/*
 * NewKeyGenerationTest.HmacMultipleDigests
 *
 * Verifies that keymaster rejects HMAC key generation with multiple specified digest algorithms.
 */
TEST_F(NewKeyGenerationTest, HmacMultipleDigests) {
    ASSERT_EQ(ErrorCode::UNSUPPORTED_DIGEST,
              GenerateKey(AuthorizationSetBuilder()
                              .HmacKey(128)
                              .Digest(Digest::SHA1)
                              .Digest(Digest::SHA_2_256)
                              .Authorization(TAG_MIN_MAC_LENGTH, 128)));
}

/*
 * NewKeyGenerationTest.HmacDigestNone
 *
 * Verifies that keymaster rejects HMAC key generation with no digest or Digest::NONE
 */
TEST_F(NewKeyGenerationTest, HmacDigestNone) {
    ASSERT_EQ(
        ErrorCode::UNSUPPORTED_DIGEST,
        GenerateKey(AuthorizationSetBuilder().HmacKey(128).Authorization(TAG_MIN_MAC_LENGTH, 128)));

    ASSERT_EQ(ErrorCode::UNSUPPORTED_DIGEST,
              GenerateKey(AuthorizationSetBuilder()
                              .HmacKey(128)
                              .Digest(Digest::NONE)
                              .Authorization(TAG_MIN_MAC_LENGTH, 128)));
}

typedef KeymasterHidlTest GetKeyCharacteristicsTest;

/*
 * GetKeyCharacteristicsTest.HmacDigestNone
 *
 * Verifies that getKeyCharacteristics functions, and that generated and retrieved key
 * characteristics match.
 */
TEST_F(GetKeyCharacteristicsTest, SimpleRsa) {
    ASSERT_EQ(ErrorCode::OK, GenerateKey(AuthorizationSetBuilder()
                                             .RsaSigningKey(1024, 3)
                                             .Digest(Digest::NONE)
                                             .Padding(PaddingMode::NONE)));

    KeyCharacteristics retrieved_chars;
    ASSERT_EQ(ErrorCode::OK, GetCharacteristics(key_blob_, &retrieved_chars));

    AuthorizationSet gen_sw = key_characteristics_.softwareEnforced;
    AuthorizationSet gen_tee = key_characteristics_.teeEnforced;
    AuthorizationSet retrieved_sw = retrieved_chars.softwareEnforced;
    AuthorizationSet retrieved_tee = retrieved_chars.teeEnforced;

    EXPECT_EQ(gen_sw, retrieved_sw);
    EXPECT_EQ(gen_tee, retrieved_tee);
}

typedef KeymasterHidlTest SigningOperationsTest;

/*
 * SigningOperationsTest.RsaSuccess
 *
 * Verifies that raw RSA signature operations succeed.
 */
TEST_F(SigningOperationsTest, RsaSuccess) {
    ASSERT_EQ(ErrorCode::OK, GenerateKey(AuthorizationSetBuilder()
                                             .RsaSigningKey(1024, 3)
                                             .Digest(Digest::NONE)
                                             .Padding(PaddingMode::NONE)
                                             .Authorization(TAG_NO_AUTH_REQUIRED)));
    string message = "12345678901234567890123456789012";
    string signature = SignMessage(
        message, AuthorizationSetBuilder().Digest(Digest::NONE).Padding(PaddingMode::NONE));
}

/*
 * SigningOperationsTest.RsaPssSha256Success
 *
 * Verifies that RSA-PSS signature operations succeed.
 */
TEST_F(SigningOperationsTest, RsaPssSha256Success) {
    ASSERT_EQ(ErrorCode::OK, GenerateKey(AuthorizationSetBuilder()
                                             .RsaSigningKey(1024, 3)
                                             .Digest(Digest::SHA_2_256)
                                             .Padding(PaddingMode::RSA_PSS)
                                             .Authorization(TAG_NO_AUTH_REQUIRED)));
    // Use large message, which won't work without digesting.
    string message(1024, 'a');
    string signature = SignMessage(
        message, AuthorizationSetBuilder().Digest(Digest::SHA_2_256).Padding(PaddingMode::RSA_PSS));
}

/*
 * SigningOperationsTest.RsaPaddingNoneDoesNotAllowOther
 *
 * Verifies that keymaster rejects signature operations that specify a padding mode when the key
 * supports only unpadded operations.
 */
TEST_F(SigningOperationsTest, RsaPaddingNoneDoesNotAllowOther) {
    ASSERT_EQ(ErrorCode::OK, GenerateKey(AuthorizationSetBuilder()
                                             .RsaSigningKey(1024, 3)
                                             .Digest(Digest::NONE)
                                             .Authorization(TAG_NO_AUTH_REQUIRED)
                                             .Padding(PaddingMode::NONE)));
    string message = "12345678901234567890123456789012";
    string signature;

    EXPECT_EQ(ErrorCode::INCOMPATIBLE_PADDING_MODE,
              Begin(KeyPurpose::SIGN, AuthorizationSetBuilder()
                                          .Digest(Digest::NONE)
                                          .Padding(PaddingMode::RSA_PKCS1_1_5_SIGN)));
}

/*
 * SigningOperationsTest.RsaPkcs1Sha256Success
 *
 * Verifies that digested RSA-PKCS1 signature operations succeed.
 */
TEST_F(SigningOperationsTest, RsaPkcs1Sha256Success) {
    ASSERT_EQ(ErrorCode::OK, GenerateKey(AuthorizationSetBuilder()
                                             .RsaSigningKey(1024, 3)
                                             .Digest(Digest::SHA_2_256)
                                             .Authorization(TAG_NO_AUTH_REQUIRED)
                                             .Padding(PaddingMode::RSA_PKCS1_1_5_SIGN)));
    string message(1024, 'a');
    string signature = SignMessage(message, AuthorizationSetBuilder()
                                                .Digest(Digest::SHA_2_256)
                                                .Padding(PaddingMode::RSA_PKCS1_1_5_SIGN));
}

/*
 * SigningOperationsTest.RsaPkcs1NoDigestSuccess
 *
 * Verifies that undigested RSA-PKCS1 signature operations succeed.
 */
TEST_F(SigningOperationsTest, RsaPkcs1NoDigestSuccess) {
    ASSERT_EQ(ErrorCode::OK, GenerateKey(AuthorizationSetBuilder()
                                             .RsaSigningKey(1024, 3)
                                             .Digest(Digest::NONE)
                                             .Authorization(TAG_NO_AUTH_REQUIRED)
                                             .Padding(PaddingMode::RSA_PKCS1_1_5_SIGN)));
    string message(53, 'a');
    string signature = SignMessage(
        message,
        AuthorizationSetBuilder().Digest(Digest::NONE).Padding(PaddingMode::RSA_PKCS1_1_5_SIGN));
}

/*
 * SigningOperationsTest.RsaPkcs1NoDigestTooLarge
 *
 * Verifies that undigested RSA-PKCS1 signature operations fail with the correct error code when
 * given a too-long message.
 */
TEST_F(SigningOperationsTest, RsaPkcs1NoDigestTooLong) {
    ASSERT_EQ(ErrorCode::OK, GenerateKey(AuthorizationSetBuilder()
                                             .RsaSigningKey(1024, 3)
                                             .Digest(Digest::NONE)
                                             .Authorization(TAG_NO_AUTH_REQUIRED)
                                             .Padding(PaddingMode::RSA_PKCS1_1_5_SIGN)));
    string message(129, 'a');

    EXPECT_EQ(ErrorCode::OK,
              Begin(KeyPurpose::SIGN, AuthorizationSetBuilder()
                                          .Digest(Digest::NONE)
                                          .Padding(PaddingMode::RSA_PKCS1_1_5_SIGN)));
    string signature;
    EXPECT_EQ(ErrorCode::INVALID_INPUT_LENGTH, Finish(message, &signature));
}

/*
 * SigningOperationsTest.RsaPssSha512TooSmallKey
 *
 * Verifies that undigested RSA-PSS signature operations fail with the correct error code when
 * used with a key that is too small for the message.
 *
 * A PSS-padded message is of length salt_size + digest_size + 16 (sizes in bits), and the keymaster
 * specification requires that salt_size == digest_size, so the message will be digest_size * 2 +
 * 16. Such a message can only be signed by a given key if the key is at least that size. This test
 * uses SHA512, which has a digest_size == 512, so the message size is 1040 bits, too large for a
 * 1024-bit key.
 */
TEST_F(SigningOperationsTest, RsaPssSha512TooSmallKey) {
    ASSERT_EQ(ErrorCode::OK, GenerateKey(AuthorizationSetBuilder()
                                             .RsaSigningKey(1024, 3)
                                             .Digest(Digest::SHA_2_512)
                                             .Authorization(TAG_NO_AUTH_REQUIRED)
                                             .Padding(PaddingMode::RSA_PSS)));
    EXPECT_EQ(
        ErrorCode::INCOMPATIBLE_DIGEST,
        Begin(KeyPurpose::SIGN,
              AuthorizationSetBuilder().Digest(Digest::SHA_2_512).Padding(PaddingMode::RSA_PSS)))
        << "(Possibly b/33346750)";
}

/*
 * SigningOperationsTest.RsaNoPaddingTooLong
 *
 * Verifies that raw RSA signature operations fail with the correct error code when
 * given a too-long message.
 */
TEST_F(SigningOperationsTest, RsaNoPaddingTooLong) {
    ASSERT_EQ(ErrorCode::OK, GenerateKey(AuthorizationSetBuilder()
                                             .RsaSigningKey(1024, 3)
                                             .Digest(Digest::NONE)
                                             .Authorization(TAG_NO_AUTH_REQUIRED)
                                             .Padding(PaddingMode::RSA_PKCS1_1_5_SIGN)));
    // One byte too long
    string message(1024 / 8 + 1, 'a');
    ASSERT_EQ(ErrorCode::OK,
              Begin(KeyPurpose::SIGN, AuthorizationSetBuilder()
                                          .Digest(Digest::NONE)
                                          .Padding(PaddingMode::RSA_PKCS1_1_5_SIGN)));
    string result;
    EXPECT_EQ(ErrorCode::INVALID_INPUT_LENGTH, Finish(message, &result));

    // Very large message that should exceed the transfer buffer size of any reasonable TEE.
    message = string(128 * 1024, 'a');
    ASSERT_EQ(ErrorCode::OK,
              Begin(KeyPurpose::SIGN, AuthorizationSetBuilder()
                                          .Digest(Digest::NONE)
                                          .Padding(PaddingMode::RSA_PKCS1_1_5_SIGN)));
    EXPECT_EQ(ErrorCode::INVALID_INPUT_LENGTH, Finish(message, &result));
}

/*
 * SigningOperationsTest.RsaAbort
 *
 * Verifies that operations can be aborted correctly.  Uses an RSA signing operation for the test,
 * but the behavior should be algorithm and purpose-independent.
 */
TEST_F(SigningOperationsTest, RsaAbort) {
    ASSERT_EQ(ErrorCode::OK, GenerateKey(AuthorizationSetBuilder()
                                             .RsaSigningKey(1024, 3)
                                             .Digest(Digest::NONE)
                                             .Authorization(TAG_NO_AUTH_REQUIRED)
                                             .Padding(PaddingMode::NONE)));

    ASSERT_EQ(ErrorCode::OK,
              Begin(KeyPurpose::SIGN,
                    AuthorizationSetBuilder().Digest(Digest::NONE).Padding(PaddingMode::NONE)));
    EXPECT_EQ(ErrorCode::OK, Abort(op_handle_));

    // Another abort should fail
    EXPECT_EQ(ErrorCode::INVALID_OPERATION_HANDLE, Abort(op_handle_));

    // Set to sentinel, so TearDown() doesn't try to abort again.
    op_handle_ = kOpHandleSentinel;
}

/*
 * SigningOperationsTest.RsaUnsupportedPadding
 *
 * Verifies that RSA operations fail with the correct error (but key gen succeeds) when used with a
 * padding mode inappropriate for RSA.
 */
TEST_F(SigningOperationsTest, RsaUnsupportedPadding) {
    ASSERT_EQ(ErrorCode::OK, GenerateKey(AuthorizationSetBuilder()
                                             .RsaSigningKey(1024, 3)
                                             .Authorization(TAG_NO_AUTH_REQUIRED)
                                             .Digest(Digest::SHA_2_256 /* supported digest */)
                                             .Padding(PaddingMode::PKCS7)));
    ASSERT_EQ(
        ErrorCode::UNSUPPORTED_PADDING_MODE,
        Begin(KeyPurpose::SIGN,
              AuthorizationSetBuilder().Digest(Digest::SHA_2_256).Padding(PaddingMode::PKCS7)));
}

/*
 * SigningOperationsTest.RsaPssNoDigest
 *
 * Verifies that RSA PSS operations fail when no digest is used.  PSS requires a digest.
 */
TEST_F(SigningOperationsTest, RsaNoDigest) {
    ASSERT_EQ(ErrorCode::OK, GenerateKey(AuthorizationSetBuilder()
                                             .RsaSigningKey(1024, 3)
                                             .Authorization(TAG_NO_AUTH_REQUIRED)
                                             .Digest(Digest::NONE)
                                             .Padding(PaddingMode::RSA_PSS)));
    ASSERT_EQ(ErrorCode::INCOMPATIBLE_DIGEST,
              Begin(KeyPurpose::SIGN,
                    AuthorizationSetBuilder().Digest(Digest::NONE).Padding(PaddingMode::RSA_PSS)));

    ASSERT_EQ(ErrorCode::UNSUPPORTED_DIGEST,
              Begin(KeyPurpose::SIGN, AuthorizationSetBuilder().Padding(PaddingMode::RSA_PSS)));
}

/*
 * SigningOperationsTest.RsaPssNoDigest
 *
 * Verifies that RSA operations fail when no padding mode is specified.  PaddingMode::NONE is
 * supported in some cases (as validated in other tests), but a mode must be specified.
 */
TEST_F(SigningOperationsTest, RsaNoPadding) {
    // Padding must be specified
    ASSERT_EQ(ErrorCode::OK, GenerateKey(AuthorizationSetBuilder()
                                             .RsaKey(1024, 3)
                                             .Authorization(TAG_NO_AUTH_REQUIRED)
                                             .SigningKey()
                                             .Digest(Digest::NONE)));
    ASSERT_EQ(ErrorCode::UNSUPPORTED_PADDING_MODE,
              Begin(KeyPurpose::SIGN, AuthorizationSetBuilder().Digest(Digest::NONE)));
}

/*
 * SigningOperationsTest.RsaShortMessage
 *
 * Verifies that raw RSA signatures succeed with a message shorter than the key size.
 */
TEST_F(SigningOperationsTest, RsaTooShortMessage) {
    ASSERT_EQ(ErrorCode::OK, GenerateKey(AuthorizationSetBuilder()
                                             .Authorization(TAG_NO_AUTH_REQUIRED)
                                             .RsaSigningKey(1024, 3)
                                             .Digest(Digest::NONE)
                                             .Padding(PaddingMode::NONE)));

    // Barely shorter
    string message(1024 / 8 - 1, 'a');
    SignMessage(message, AuthorizationSetBuilder().Digest(Digest::NONE).Padding(PaddingMode::NONE));

    // Much shorter
    message = "a";
    SignMessage(message, AuthorizationSetBuilder().Digest(Digest::NONE).Padding(PaddingMode::NONE));
}

/*
 * SigningOperationsTest.RsaSignWithEncryptionKey
 *
 * Verifies that RSA encryption keys cannot be used to sign.
 */
TEST_F(SigningOperationsTest, RsaSignWithEncryptionKey) {
    ASSERT_EQ(ErrorCode::OK, GenerateKey(AuthorizationSetBuilder()
                                             .Authorization(TAG_NO_AUTH_REQUIRED)
                                             .RsaEncryptionKey(1024, 3)
                                             .Digest(Digest::NONE)
                                             .Padding(PaddingMode::NONE)));
    ASSERT_EQ(ErrorCode::INCOMPATIBLE_PURPOSE,
              Begin(KeyPurpose::SIGN,
                    AuthorizationSetBuilder().Digest(Digest::NONE).Padding(PaddingMode::NONE)));
}

/*
 * SigningOperationsTest.RsaSignTooLargeMessage
 *
 * Verifies that attempting a raw signature of a message which is the same length as the key, but
 * numerically larger than the public modulus, fails with the correct error.
 */
TEST_F(SigningOperationsTest, RsaSignTooLargeMessage) {
    ASSERT_EQ(ErrorCode::OK, GenerateKey(AuthorizationSetBuilder()
                                             .Authorization(TAG_NO_AUTH_REQUIRED)
                                             .RsaSigningKey(1024, 3)
                                             .Digest(Digest::NONE)
                                             .Padding(PaddingMode::NONE)));

    // Largest possible message will always be larger than the public modulus.
    string message(1024 / 8, static_cast<char>(0xff));
    ASSERT_EQ(ErrorCode::OK, Begin(KeyPurpose::SIGN, AuthorizationSetBuilder()
                                                         .Authorization(TAG_NO_AUTH_REQUIRED)
                                                         .Digest(Digest::NONE)
                                                         .Padding(PaddingMode::NONE)));
    string signature;
    ASSERT_EQ(ErrorCode::INVALID_ARGUMENT, Finish(message, &signature));
}

/*
 * SigningOperationsTest.EcdsaAllSizesAndHashes
 *
 * Verifies that ECDSA operations succeed with all possible key sizes and hashes.
 */
TEST_F(SigningOperationsTest, EcdsaAllSizesAndHashes) {
    for (auto key_size : {224, 256, 384, 521}) {
        for (auto digest : {
                 Digest::SHA1, Digest::SHA_2_224, Digest::SHA_2_256, Digest::SHA_2_384,
                 Digest::SHA_2_512,
             }) {
            ErrorCode error = GenerateKey(AuthorizationSetBuilder()
                                              .Authorization(TAG_NO_AUTH_REQUIRED)
                                              .EcdsaSigningKey(key_size)
                                              .Digest(digest));
            EXPECT_EQ(ErrorCode::OK, error) << "Failed to generate ECDSA key with size " << key_size
                                            << " and digest " << digest;
            if (error != ErrorCode::OK) continue;

            string message(1024, 'a');
            if (digest == Digest::NONE) message.resize(key_size / 8);
            SignMessage(message, AuthorizationSetBuilder().Digest(digest));
            CheckedDeleteKey();
        }
    }
}

/*
 * SigningOperationsTest.EcdsaAllCurves
 *
 * Verifies that ECDSA operations succeed with all possible curves.
 */
TEST_F(SigningOperationsTest, EcdsaAllCurves) {
    for (auto curve : {EcCurve::P_224, EcCurve::P_256, EcCurve::P_384, EcCurve::P_521}) {
        ErrorCode error = GenerateKey(AuthorizationSetBuilder()
                                          .Authorization(TAG_NO_AUTH_REQUIRED)
                                          .EcdsaSigningKey(curve)
                                          .Digest(Digest::SHA_2_256));
        EXPECT_EQ(ErrorCode::OK, error) << "Failed to generate ECDSA key with curve " << curve;
        if (error != ErrorCode::OK) continue;

        string message(1024, 'a');
        SignMessage(message, AuthorizationSetBuilder().Digest(Digest::SHA_2_256));
        CheckedDeleteKey();
    }
}

/*
 * SigningOperationsTest.EcdsaNoDigestHugeData
 *
 * Verifies that ECDSA operations support very large messages, even without digesting.  This should
 * work because ECDSA actually only signs the leftmost L_n bits of the message, however large it may
 * be.  Not using digesting is a bad idea, but in some cases digesting is done by the framework.
 */
TEST_F(SigningOperationsTest, EcdsaNoDigestHugeData) {
    ASSERT_EQ(ErrorCode::OK, GenerateKey(AuthorizationSetBuilder()
                                             .Authorization(TAG_NO_AUTH_REQUIRED)
                                             .EcdsaSigningKey(224)
                                             .Digest(Digest::NONE)));
    string message(2 * 1024, 'a');
    SignMessage(message, AuthorizationSetBuilder().Digest(Digest::NONE));
}

/*
 * SigningOperationsTest.AesEcbSign
 *
 * Verifies that attempts to use AES keys to sign fail in the correct way.
 */
TEST_F(SigningOperationsTest, AesEcbSign) {
    ASSERT_EQ(ErrorCode::OK, GenerateKey(AuthorizationSetBuilder()
                                             .Authorization(TAG_NO_AUTH_REQUIRED)
                                             .SigningKey()
                                             .AesEncryptionKey(128)
                                             .Authorization(TAG_BLOCK_MODE, BlockMode::ECB)))
        << "(Possibly b/36252957)";

    AuthorizationSet out_params;
    EXPECT_EQ(ErrorCode::UNSUPPORTED_PURPOSE,
              Begin(KeyPurpose::SIGN, AuthorizationSet() /* in_params */, &out_params))
        << "(Possibly b/36233187)";

    EXPECT_EQ(ErrorCode::UNSUPPORTED_PURPOSE,
              Begin(KeyPurpose::VERIFY, AuthorizationSet() /* in_params */, &out_params))
        << "(Possibly b/36233187)";
}

/*
 * SigningOperationsTest.HmacAllDigests
 *
 * Verifies that HMAC works with all digests.
 */
TEST_F(SigningOperationsTest, HmacAllDigests) {
    for (auto digest : {Digest::SHA1, Digest::SHA_2_224, Digest::SHA_2_256, Digest::SHA_2_384,
                        Digest::SHA_2_512}) {
        ASSERT_EQ(ErrorCode::OK, GenerateKey(AuthorizationSetBuilder()
                                                 .Authorization(TAG_NO_AUTH_REQUIRED)
                                                 .HmacKey(128)
                                                 .Digest(digest)
                                                 .Authorization(TAG_MIN_MAC_LENGTH, 160)))
            << "Failed to create HMAC key with digest " << digest;
        string message = "12345678901234567890123456789012";
        string signature = MacMessage(message, digest, 160);
        EXPECT_EQ(160U / 8U, signature.size())
            << "Failed to sign with HMAC key with digest " << digest;
        CheckedDeleteKey();
    }
}

/*
 * SigningOperationsTest.HmacSha256TooLargeMacLength
 *
 * Verifies that HMAC fails in the correct way when asked to generate a MAC larger than the digest
 * size.
 */
TEST_F(SigningOperationsTest, HmacSha256TooLargeMacLength) {
    ASSERT_EQ(ErrorCode::OK, GenerateKey(AuthorizationSetBuilder()
                                             .Authorization(TAG_NO_AUTH_REQUIRED)
                                             .HmacKey(128)
                                             .Digest(Digest::SHA_2_256)
                                             .Authorization(TAG_MIN_MAC_LENGTH, 256)));
    AuthorizationSet output_params;
    EXPECT_EQ(
        ErrorCode::UNSUPPORTED_MAC_LENGTH,
        Begin(
            KeyPurpose::SIGN, key_blob_,
            AuthorizationSetBuilder().Digest(Digest::SHA_2_256).Authorization(TAG_MAC_LENGTH, 264),
            &output_params, &op_handle_));
}

/*
 * SigningOperationsTest.HmacSha256TooSmallMacLength
 *
 * Verifies that HMAC fails in the correct way when asked to generate a MAC smaller than the
 * specified minimum MAC length.
 */
TEST_F(SigningOperationsTest, HmacSha256TooSmallMacLength) {
    ASSERT_EQ(ErrorCode::OK, GenerateKey(AuthorizationSetBuilder()
                                             .Authorization(TAG_NO_AUTH_REQUIRED)
                                             .HmacKey(128)
                                             .Digest(Digest::SHA_2_256)
                                             .Authorization(TAG_MIN_MAC_LENGTH, 128)));
    AuthorizationSet output_params;
    EXPECT_EQ(
        ErrorCode::INVALID_MAC_LENGTH,
        Begin(
            KeyPurpose::SIGN, key_blob_,
            AuthorizationSetBuilder().Digest(Digest::SHA_2_256).Authorization(TAG_MAC_LENGTH, 120),
            &output_params, &op_handle_));
}

/*
 * SigningOperationsTest.HmacRfc4231TestCase3
 *
 * Validates against the test vectors from RFC 4231 test case 3.
 */
TEST_F(SigningOperationsTest, HmacRfc4231TestCase3) {
    string key(20, 0xaa);
    string message(50, 0xdd);
    uint8_t sha_224_expected[] = {
        0x7f, 0xb3, 0xcb, 0x35, 0x88, 0xc6, 0xc1, 0xf6, 0xff, 0xa9, 0x69, 0x4d, 0x7d, 0x6a,
        0xd2, 0x64, 0x93, 0x65, 0xb0, 0xc1, 0xf6, 0x5d, 0x69, 0xd1, 0xec, 0x83, 0x33, 0xea,
    };
    uint8_t sha_256_expected[] = {
        0x77, 0x3e, 0xa9, 0x1e, 0x36, 0x80, 0x0e, 0x46, 0x85, 0x4d, 0xb8,
        0xeb, 0xd0, 0x91, 0x81, 0xa7, 0x29, 0x59, 0x09, 0x8b, 0x3e, 0xf8,
        0xc1, 0x22, 0xd9, 0x63, 0x55, 0x14, 0xce, 0xd5, 0x65, 0xfe,
    };
    uint8_t sha_384_expected[] = {
        0x88, 0x06, 0x26, 0x08, 0xd3, 0xe6, 0xad, 0x8a, 0x0a, 0xa2, 0xac, 0xe0,
        0x14, 0xc8, 0xa8, 0x6f, 0x0a, 0xa6, 0x35, 0xd9, 0x47, 0xac, 0x9f, 0xeb,
        0xe8, 0x3e, 0xf4, 0xe5, 0x59, 0x66, 0x14, 0x4b, 0x2a, 0x5a, 0xb3, 0x9d,
        0xc1, 0x38, 0x14, 0xb9, 0x4e, 0x3a, 0xb6, 0xe1, 0x01, 0xa3, 0x4f, 0x27,
    };
    uint8_t sha_512_expected[] = {
        0xfa, 0x73, 0xb0, 0x08, 0x9d, 0x56, 0xa2, 0x84, 0xef, 0xb0, 0xf0, 0x75, 0x6c,
        0x89, 0x0b, 0xe9, 0xb1, 0xb5, 0xdb, 0xdd, 0x8e, 0xe8, 0x1a, 0x36, 0x55, 0xf8,
        0x3e, 0x33, 0xb2, 0x27, 0x9d, 0x39, 0xbf, 0x3e, 0x84, 0x82, 0x79, 0xa7, 0x22,
        0xc8, 0x06, 0xb4, 0x85, 0xa4, 0x7e, 0x67, 0xc8, 0x07, 0xb9, 0x46, 0xa3, 0x37,
        0xbe, 0xe8, 0x94, 0x26, 0x74, 0x27, 0x88, 0x59, 0xe1, 0x32, 0x92, 0xfb,
    };

    CheckHmacTestVector(key, message, Digest::SHA_2_224, make_string(sha_224_expected));
    CheckHmacTestVector(key, message, Digest::SHA_2_256, make_string(sha_256_expected));
    CheckHmacTestVector(key, message, Digest::SHA_2_384, make_string(sha_384_expected));
    CheckHmacTestVector(key, message, Digest::SHA_2_512, make_string(sha_512_expected));
}

/*
 * SigningOperationsTest.HmacRfc4231TestCase5
 *
 * Validates against the test vectors from RFC 4231 test case 5.
 */
TEST_F(SigningOperationsTest, HmacRfc4231TestCase5) {
    string key(20, 0x0c);
    string message = "Test With Truncation";

    uint8_t sha_224_expected[] = {
        0x0e, 0x2a, 0xea, 0x68, 0xa9, 0x0c, 0x8d, 0x37,
        0xc9, 0x88, 0xbc, 0xdb, 0x9f, 0xca, 0x6f, 0xa8,
    };
    uint8_t sha_256_expected[] = {
        0xa3, 0xb6, 0x16, 0x74, 0x73, 0x10, 0x0e, 0xe0,
        0x6e, 0x0c, 0x79, 0x6c, 0x29, 0x55, 0x55, 0x2b,
    };
    uint8_t sha_384_expected[] = {
        0x3a, 0xbf, 0x34, 0xc3, 0x50, 0x3b, 0x2a, 0x23,
        0xa4, 0x6e, 0xfc, 0x61, 0x9b, 0xae, 0xf8, 0x97,
    };
    uint8_t sha_512_expected[] = {
        0x41, 0x5f, 0xad, 0x62, 0x71, 0x58, 0x0a, 0x53,
        0x1d, 0x41, 0x79, 0xbc, 0x89, 0x1d, 0x87, 0xa6,
    };

    CheckHmacTestVector(key, message, Digest::SHA_2_224, make_string(sha_224_expected));
    CheckHmacTestVector(key, message, Digest::SHA_2_256, make_string(sha_256_expected));
    CheckHmacTestVector(key, message, Digest::SHA_2_384, make_string(sha_384_expected));
    CheckHmacTestVector(key, message, Digest::SHA_2_512, make_string(sha_512_expected));
}

/*
 * SigningOperationsTest.HmacRfc4231TestCase6
 *
 * Validates against the test vectors from RFC 4231 test case 6.
 */
TEST_F(SigningOperationsTest, HmacRfc4231TestCase6) {
    string key(131, 0xaa);
    string message = "Test Using Larger Than Block-Size Key - Hash Key First";

    uint8_t sha_224_expected[] = {
        0x95, 0xe9, 0xa0, 0xdb, 0x96, 0x20, 0x95, 0xad, 0xae, 0xbe, 0x9b, 0x2d, 0x6f, 0x0d,
        0xbc, 0xe2, 0xd4, 0x99, 0xf1, 0x12, 0xf2, 0xd2, 0xb7, 0x27, 0x3f, 0xa6, 0x87, 0x0e,
    };
    uint8_t sha_256_expected[] = {
        0x60, 0xe4, 0x31, 0x59, 0x1e, 0xe0, 0xb6, 0x7f, 0x0d, 0x8a, 0x26,
        0xaa, 0xcb, 0xf5, 0xb7, 0x7f, 0x8e, 0x0b, 0xc6, 0x21, 0x37, 0x28,
        0xc5, 0x14, 0x05, 0x46, 0x04, 0x0f, 0x0e, 0xe3, 0x7f, 0x54,
    };
    uint8_t sha_384_expected[] = {
        0x4e, 0xce, 0x08, 0x44, 0x85, 0x81, 0x3e, 0x90, 0x88, 0xd2, 0xc6, 0x3a,
        0x04, 0x1b, 0xc5, 0xb4, 0x4f, 0x9e, 0xf1, 0x01, 0x2a, 0x2b, 0x58, 0x8f,
        0x3c, 0xd1, 0x1f, 0x05, 0x03, 0x3a, 0xc4, 0xc6, 0x0c, 0x2e, 0xf6, 0xab,
        0x40, 0x30, 0xfe, 0x82, 0x96, 0x24, 0x8d, 0xf1, 0x63, 0xf4, 0x49, 0x52,
    };
    uint8_t sha_512_expected[] = {
        0x80, 0xb2, 0x42, 0x63, 0xc7, 0xc1, 0xa3, 0xeb, 0xb7, 0x14, 0x93, 0xc1, 0xdd,
        0x7b, 0xe8, 0xb4, 0x9b, 0x46, 0xd1, 0xf4, 0x1b, 0x4a, 0xee, 0xc1, 0x12, 0x1b,
        0x01, 0x37, 0x83, 0xf8, 0xf3, 0x52, 0x6b, 0x56, 0xd0, 0x37, 0xe0, 0x5f, 0x25,
        0x98, 0xbd, 0x0f, 0xd2, 0x21, 0x5d, 0x6a, 0x1e, 0x52, 0x95, 0xe6, 0x4f, 0x73,
        0xf6, 0x3f, 0x0a, 0xec, 0x8b, 0x91, 0x5a, 0x98, 0x5d, 0x78, 0x65, 0x98,
    };

    CheckHmacTestVector(key, message, Digest::SHA_2_224, make_string(sha_224_expected));
    CheckHmacTestVector(key, message, Digest::SHA_2_256, make_string(sha_256_expected));
    CheckHmacTestVector(key, message, Digest::SHA_2_384, make_string(sha_384_expected));
    CheckHmacTestVector(key, message, Digest::SHA_2_512, make_string(sha_512_expected));
}

/*
 * SigningOperationsTest.HmacRfc4231TestCase7
 *
 * Validates against the test vectors from RFC 4231 test case 7.
 */
TEST_F(SigningOperationsTest, HmacRfc4231TestCase7) {
    string key(131, 0xaa);
    string message = "This is a test using a larger than block-size key and a larger than "
                     "block-size data. The key needs to be hashed before being used by the HMAC "
                     "algorithm.";

    uint8_t sha_224_expected[] = {
        0x3a, 0x85, 0x41, 0x66, 0xac, 0x5d, 0x9f, 0x02, 0x3f, 0x54, 0xd5, 0x17, 0xd0, 0xb3,
        0x9d, 0xbd, 0x94, 0x67, 0x70, 0xdb, 0x9c, 0x2b, 0x95, 0xc9, 0xf6, 0xf5, 0x65, 0xd1,
    };
    uint8_t sha_256_expected[] = {
        0x9b, 0x09, 0xff, 0xa7, 0x1b, 0x94, 0x2f, 0xcb, 0x27, 0x63, 0x5f,
        0xbc, 0xd5, 0xb0, 0xe9, 0x44, 0xbf, 0xdc, 0x63, 0x64, 0x4f, 0x07,
        0x13, 0x93, 0x8a, 0x7f, 0x51, 0x53, 0x5c, 0x3a, 0x35, 0xe2,
    };
    uint8_t sha_384_expected[] = {
        0x66, 0x17, 0x17, 0x8e, 0x94, 0x1f, 0x02, 0x0d, 0x35, 0x1e, 0x2f, 0x25,
        0x4e, 0x8f, 0xd3, 0x2c, 0x60, 0x24, 0x20, 0xfe, 0xb0, 0xb8, 0xfb, 0x9a,
        0xdc, 0xce, 0xbb, 0x82, 0x46, 0x1e, 0x99, 0xc5, 0xa6, 0x78, 0xcc, 0x31,
        0xe7, 0x99, 0x17, 0x6d, 0x38, 0x60, 0xe6, 0x11, 0x0c, 0x46, 0x52, 0x3e,
    };
    uint8_t sha_512_expected[] = {
        0xe3, 0x7b, 0x6a, 0x77, 0x5d, 0xc8, 0x7d, 0xba, 0xa4, 0xdf, 0xa9, 0xf9, 0x6e,
        0x5e, 0x3f, 0xfd, 0xde, 0xbd, 0x71, 0xf8, 0x86, 0x72, 0x89, 0x86, 0x5d, 0xf5,
        0xa3, 0x2d, 0x20, 0xcd, 0xc9, 0x44, 0xb6, 0x02, 0x2c, 0xac, 0x3c, 0x49, 0x82,
        0xb1, 0x0d, 0x5e, 0xeb, 0x55, 0xc3, 0xe4, 0xde, 0x15, 0x13, 0x46, 0x76, 0xfb,
        0x6d, 0xe0, 0x44, 0x60, 0x65, 0xc9, 0x74, 0x40, 0xfa, 0x8c, 0x6a, 0x58,
    };

    CheckHmacTestVector(key, message, Digest::SHA_2_224, make_string(sha_224_expected));
    CheckHmacTestVector(key, message, Digest::SHA_2_256, make_string(sha_256_expected));
    CheckHmacTestVector(key, message, Digest::SHA_2_384, make_string(sha_384_expected));
    CheckHmacTestVector(key, message, Digest::SHA_2_512, make_string(sha_512_expected));
}

typedef KeymasterHidlTest VerificationOperationsTest;

/*
 * VerificationOperationsTest.RsaSuccess
 *
 * Verifies that a simple RSA signature/verification sequence succeeds.
 */
TEST_F(VerificationOperationsTest, RsaSuccess) {
    ASSERT_EQ(ErrorCode::OK, GenerateKey(AuthorizationSetBuilder()
                                             .Authorization(TAG_NO_AUTH_REQUIRED)
                                             .RsaSigningKey(1024, 3)
                                             .Digest(Digest::NONE)
                                             .Padding(PaddingMode::NONE)));
    string message = "12345678901234567890123456789012";
    string signature = SignMessage(
        message, AuthorizationSetBuilder().Digest(Digest::NONE).Padding(PaddingMode::NONE));
    VerifyMessage(message, signature,
                  AuthorizationSetBuilder().Digest(Digest::NONE).Padding(PaddingMode::NONE));
}

/*
 * VerificationOperationsTest.RsaSuccess
 *
 * Verifies RSA signature/verification for all padding modes and digests.
 */
TEST_F(VerificationOperationsTest, RsaAllPaddingsAndDigests) {
    ASSERT_EQ(ErrorCode::OK,
              GenerateKey(AuthorizationSetBuilder()
                              .Authorization(TAG_NO_AUTH_REQUIRED)
                              .RsaSigningKey(2048, 3)
                              .Digest(Digest::NONE, Digest::MD5, Digest::SHA1, Digest::SHA_2_224,
                                      Digest::SHA_2_256, Digest::SHA_2_384, Digest::SHA_2_512)
                              .Padding(PaddingMode::NONE)
                              .Padding(PaddingMode::RSA_PSS)
                              .Padding(PaddingMode::RSA_PKCS1_1_5_SIGN)));

    string message(128, 'a');
    string corrupt_message(message);
    ++corrupt_message[corrupt_message.size() / 2];

    for (auto padding :
         {PaddingMode::NONE, PaddingMode::RSA_PSS, PaddingMode::RSA_PKCS1_1_5_SIGN}) {

        for (auto digest : {Digest::NONE, Digest::MD5, Digest::SHA1, Digest::SHA_2_224,
                            Digest::SHA_2_256, Digest::SHA_2_384, Digest::SHA_2_512}) {
            if (padding == PaddingMode::NONE && digest != Digest::NONE) {
                // Digesting only makes sense with padding.
                continue;
            }

            if (padding == PaddingMode::RSA_PSS && digest == Digest::NONE) {
                // PSS requires digesting.
                continue;
            }

            string signature =
                SignMessage(message, AuthorizationSetBuilder().Digest(digest).Padding(padding));
            VerifyMessage(message, signature,
                          AuthorizationSetBuilder().Digest(digest).Padding(padding));

            if (digest != Digest::NONE) {
                // Verify with OpenSSL.
                HidlBuf pubkey;
                ASSERT_EQ(ErrorCode::OK, ExportKey(KeyFormat::X509, &pubkey));

                const uint8_t* p = pubkey.data();
                EVP_PKEY_Ptr pkey(d2i_PUBKEY(nullptr /* alloc new */, &p, pubkey.size()));
                ASSERT_TRUE(pkey.get());

                EVP_MD_CTX digest_ctx;
                EVP_MD_CTX_init(&digest_ctx);
                EVP_PKEY_CTX* pkey_ctx;
                const EVP_MD* md = openssl_digest(digest);
                ASSERT_NE(md, nullptr);
                EXPECT_EQ(1, EVP_DigestVerifyInit(&digest_ctx, &pkey_ctx, md, nullptr /* engine */,
                                                  pkey.get()));

                switch (padding) {
                case PaddingMode::RSA_PSS:
                    EXPECT_GT(EVP_PKEY_CTX_set_rsa_padding(pkey_ctx, RSA_PKCS1_PSS_PADDING), 0);
                    EXPECT_GT(EVP_PKEY_CTX_set_rsa_pss_saltlen(pkey_ctx, EVP_MD_size(md)), 0);
                    break;
                case PaddingMode::RSA_PKCS1_1_5_SIGN:
                    // PKCS1 is the default; don't need to set anything.
                    break;
                default:
                    FAIL();
                    break;
                }

                EXPECT_EQ(1, EVP_DigestVerifyUpdate(&digest_ctx, message.data(), message.size()));
                EXPECT_EQ(1, EVP_DigestVerifyFinal(
                                 &digest_ctx, reinterpret_cast<const uint8_t*>(signature.data()),
                                 signature.size()));
                EVP_MD_CTX_cleanup(&digest_ctx);
            }

            // Corrupt signature shouldn't verify.
            string corrupt_signature(signature);
            ++corrupt_signature[corrupt_signature.size() / 2];

            EXPECT_EQ(ErrorCode::OK,
                      Begin(KeyPurpose::VERIFY,
                            AuthorizationSetBuilder().Digest(digest).Padding(padding)));
            string result;
            EXPECT_EQ(ErrorCode::VERIFICATION_FAILED, Finish(message, corrupt_signature, &result));

            // Corrupt message shouldn't verify
            EXPECT_EQ(ErrorCode::OK,
                      Begin(KeyPurpose::VERIFY,
                            AuthorizationSetBuilder().Digest(digest).Padding(padding)));
            EXPECT_EQ(ErrorCode::VERIFICATION_FAILED, Finish(corrupt_message, signature, &result));
        }
    }
}

/*
 * VerificationOperationsTest.RsaSuccess
 *
 * Verifies ECDSA signature/verification for all digests and curves.
 */
TEST_F(VerificationOperationsTest, EcdsaAllDigestsAndCurves) {
    auto digests = {
        Digest::NONE,      Digest::SHA1,      Digest::SHA_2_224,
        Digest::SHA_2_256, Digest::SHA_2_384, Digest::SHA_2_512,
    };

    string message = "1234567890";
    string corrupt_message = "2234567890";
    for (auto curve : {EcCurve::P_224, EcCurve::P_256, EcCurve::P_384, EcCurve::P_521}) {
        ErrorCode error = GenerateKey(AuthorizationSetBuilder()
                                          .Authorization(TAG_NO_AUTH_REQUIRED)
                                          .EcdsaSigningKey(curve)
                                          .Digest(digests));
        EXPECT_EQ(ErrorCode::OK, error) << "Failed to generate key for EC curve " << curve;
        if (error != ErrorCode::OK) {
            continue;
        }

        for (auto digest : digests) {
            string signature = SignMessage(message, AuthorizationSetBuilder().Digest(digest));
            VerifyMessage(message, signature, AuthorizationSetBuilder().Digest(digest));

            // Verify with OpenSSL
            if (digest != Digest::NONE) {
                HidlBuf pubkey;
                ASSERT_EQ(ErrorCode::OK, ExportKey(KeyFormat::X509, &pubkey))
                    << curve << ' ' << digest;

                const uint8_t* p = pubkey.data();
                EVP_PKEY_Ptr pkey(d2i_PUBKEY(nullptr /* alloc new */, &p, pubkey.size()));
                ASSERT_TRUE(pkey.get());

                EVP_MD_CTX digest_ctx;
                EVP_MD_CTX_init(&digest_ctx);
                EVP_PKEY_CTX* pkey_ctx;
                const EVP_MD* md = openssl_digest(digest);

                EXPECT_EQ(1, EVP_DigestVerifyInit(&digest_ctx, &pkey_ctx, md, nullptr /* engine */,
                                                  pkey.get()))
                    << curve << ' ' << digest;

                EXPECT_EQ(1, EVP_DigestVerifyUpdate(&digest_ctx, message.data(), message.size()))
                    << curve << ' ' << digest;

                EXPECT_EQ(1, EVP_DigestVerifyFinal(
                                 &digest_ctx, reinterpret_cast<const uint8_t*>(signature.data()),
                                 signature.size()))
                    << curve << ' ' << digest;

                EVP_MD_CTX_cleanup(&digest_ctx);
            }

            // Corrupt signature shouldn't verify.
            string corrupt_signature(signature);
            ++corrupt_signature[corrupt_signature.size() / 2];

            EXPECT_EQ(ErrorCode::OK,
                      Begin(KeyPurpose::VERIFY, AuthorizationSetBuilder().Digest(digest)))
                << curve << ' ' << digest;

            string result;
            EXPECT_EQ(ErrorCode::VERIFICATION_FAILED, Finish(message, corrupt_signature, &result))
                << curve << ' ' << digest;

            // Corrupt message shouldn't verify
            EXPECT_EQ(ErrorCode::OK,
                      Begin(KeyPurpose::VERIFY, AuthorizationSetBuilder().Digest(digest)))
                << curve << ' ' << digest;

            EXPECT_EQ(ErrorCode::VERIFICATION_FAILED, Finish(corrupt_message, signature, &result))
                << curve << ' ' << digest;
        }

        auto rc = DeleteKey();
        ASSERT_TRUE(rc == ErrorCode::OK || rc == ErrorCode::UNIMPLEMENTED);
    }
}

/*
 * VerificationOperationsTest.HmacSigningKeyCannotVerify
 *
 * Verifies HMAC signing and verification, but that a signing key cannot be used to verify.
 */
TEST_F(VerificationOperationsTest, HmacSigningKeyCannotVerify) {
    string key_material = "HelloThisIsAKey";

    HidlBuf signing_key, verification_key;
    KeyCharacteristics signing_key_chars, verification_key_chars;
    EXPECT_EQ(ErrorCode::OK,
              ImportKey(AuthorizationSetBuilder()
                            .Authorization(TAG_NO_AUTH_REQUIRED)
                            .Authorization(TAG_ALGORITHM, Algorithm::HMAC)
                            .Authorization(TAG_PURPOSE, KeyPurpose::SIGN)
                            .Digest(Digest::SHA1)
                            .Authorization(TAG_MIN_MAC_LENGTH, 160),
                        KeyFormat::RAW, key_material, &signing_key, &signing_key_chars));
    EXPECT_EQ(ErrorCode::OK,
              ImportKey(AuthorizationSetBuilder()
                            .Authorization(TAG_NO_AUTH_REQUIRED)
                            .Authorization(TAG_ALGORITHM, Algorithm::HMAC)
                            .Authorization(TAG_PURPOSE, KeyPurpose::VERIFY)
                            .Digest(Digest::SHA1)
                            .Authorization(TAG_MIN_MAC_LENGTH, 160),
                        KeyFormat::RAW, key_material, &verification_key, &verification_key_chars));

    string message = "This is a message.";
    string signature = SignMessage(
        signing_key, message,
        AuthorizationSetBuilder().Digest(Digest::SHA1).Authorization(TAG_MAC_LENGTH, 160));

    // Signing key should not work.
    AuthorizationSet out_params;
    EXPECT_EQ(ErrorCode::INCOMPATIBLE_PURPOSE,
              Begin(KeyPurpose::VERIFY, signing_key, AuthorizationSetBuilder().Digest(Digest::SHA1),
                    &out_params, &op_handle_));

    // Verification key should work.
    VerifyMessage(verification_key, message, signature,
                  AuthorizationSetBuilder().Digest(Digest::SHA1));

    CheckedDeleteKey(&signing_key);
    CheckedDeleteKey(&verification_key);
}

typedef KeymasterHidlTest ExportKeyTest;

/*
 * ExportKeyTest.RsaUnsupportedKeyFormat
 *
 * Verifies that attempting to export RSA keys in PKCS#8 format fails with the correct error.
 */
TEST_F(ExportKeyTest, RsaUnsupportedKeyFormat) {
    ASSERT_EQ(ErrorCode::OK, GenerateKey(AuthorizationSetBuilder()
                                             .RsaSigningKey(1024, 3)
                                             .Digest(Digest::NONE)
                                             .Padding(PaddingMode::NONE)));
    HidlBuf export_data;
    ASSERT_EQ(ErrorCode::UNSUPPORTED_KEY_FORMAT, ExportKey(KeyFormat::PKCS8, &export_data));
}

/*
 * ExportKeyTest.RsaCorruptedKeyBlob
 *
 * Verifies that attempting to export RSA keys from corrupted key blobs fails.  This is essentially
 * a poor-man's key blob fuzzer.
 */
// Disabled due to b/33385206
TEST_F(ExportKeyTest, DISABLED_RsaCorruptedKeyBlob) {
    ASSERT_EQ(ErrorCode::OK, GenerateKey(AuthorizationSetBuilder()
                                             .Authorization(TAG_NO_AUTH_REQUIRED)
                                             .RsaSigningKey(1024, 3)
                                             .Digest(Digest::NONE)
                                             .Padding(PaddingMode::NONE)));
    for (size_t i = 0; i < key_blob_.size(); ++i) {
        HidlBuf corrupted(key_blob_);
        ++corrupted[i];

        HidlBuf export_data;
        EXPECT_EQ(ErrorCode::INVALID_KEY_BLOB,
                  ExportKey(KeyFormat::X509, corrupted, HidlBuf(), HidlBuf(), &export_data))
            << "Blob corrupted at offset " << i << " erroneously accepted as valid";
    }
}

/*
 * ExportKeyTest.RsaCorruptedKeyBlob
 *
 * Verifies that attempting to export ECDSA keys from corrupted key blobs fails.  This is
 * essentially a poor-man's key blob fuzzer.
 */
// Disabled due to b/33385206
TEST_F(ExportKeyTest, DISABLED_EcCorruptedKeyBlob) {
    ASSERT_EQ(ErrorCode::OK, GenerateKey(AuthorizationSetBuilder()
                                             .Authorization(TAG_NO_AUTH_REQUIRED)
                                             .EcdsaSigningKey(EcCurve::P_256)
                                             .Digest(Digest::NONE)));
    for (size_t i = 0; i < key_blob_.size(); ++i) {
        HidlBuf corrupted(key_blob_);
        ++corrupted[i];

        HidlBuf export_data;
        EXPECT_EQ(ErrorCode::INVALID_KEY_BLOB,
                  ExportKey(KeyFormat::X509, corrupted, HidlBuf(), HidlBuf(), &export_data))
            << "Blob corrupted at offset " << i << " erroneously accepted as valid";
    }
}

/*
 * ExportKeyTest.AesKeyUnexportable
 *
 * Verifies that attempting to export AES keys fails in the expected way.
 */
TEST_F(ExportKeyTest, AesKeyUnexportable) {
    ASSERT_EQ(ErrorCode::OK, GenerateKey(AuthorizationSetBuilder()
                                             .Authorization(TAG_NO_AUTH_REQUIRED)
                                             .AesEncryptionKey(128)
                                             .EcbMode()
                                             .Padding(PaddingMode::NONE)));

    HidlBuf export_data;
    EXPECT_EQ(ErrorCode::UNSUPPORTED_KEY_FORMAT, ExportKey(KeyFormat::X509, &export_data));
    EXPECT_EQ(ErrorCode::UNSUPPORTED_KEY_FORMAT, ExportKey(KeyFormat::PKCS8, &export_data));
    EXPECT_EQ(ErrorCode::UNSUPPORTED_KEY_FORMAT, ExportKey(KeyFormat::RAW, &export_data));
}
typedef KeymasterHidlTest ImportKeyTest;

/*
 * ImportKeyTest.RsaSuccess
 *
 * Verifies that importing and using an RSA key pair works correctly.
 */
TEST_F(ImportKeyTest, RsaSuccess) {
    ASSERT_EQ(ErrorCode::OK, ImportKey(AuthorizationSetBuilder()
                                           .Authorization(TAG_NO_AUTH_REQUIRED)
                                           .RsaSigningKey(1024, 65537)
                                           .Digest(Digest::SHA_2_256)
                                           .Padding(PaddingMode::RSA_PSS),
                                       KeyFormat::PKCS8, rsa_key));

    CheckKm0CryptoParam(TAG_ALGORITHM, Algorithm::RSA);
    CheckKm0CryptoParam(TAG_KEY_SIZE, 1024U);
    CheckKm0CryptoParam(TAG_RSA_PUBLIC_EXPONENT, 65537U);
    CheckKm1CryptoParam(TAG_DIGEST, Digest::SHA_2_256);
    CheckKm1CryptoParam(TAG_PADDING, PaddingMode::RSA_PSS);
    CheckOrigin();

    string message(1024 / 8, 'a');
    auto params = AuthorizationSetBuilder().Digest(Digest::SHA_2_256).Padding(PaddingMode::RSA_PSS);
    string signature = SignMessage(message, params);
    VerifyMessage(message, signature, params);
}

/*
 * ImportKeyTest.RsaKeySizeMismatch
 *
 * Verifies that importing an RSA key pair with a size that doesn't match the key fails in the
 * correct way.
 */
TEST_F(ImportKeyTest, RsaKeySizeMismatch) {
    ASSERT_EQ(ErrorCode::IMPORT_PARAMETER_MISMATCH,
              ImportKey(AuthorizationSetBuilder()
                            .RsaSigningKey(2048 /* Doesn't match key */, 65537)
                            .Digest(Digest::NONE)
                            .Padding(PaddingMode::NONE),
                        KeyFormat::PKCS8, rsa_key));
}

/*
 * ImportKeyTest.RsaPublicExponentMismatch
 *
 * Verifies that importing an RSA key pair with a public exponent that doesn't match the key fails
 * in the correct way.
 */
TEST_F(ImportKeyTest, RsaPublicExponentMismatch) {
    ASSERT_EQ(ErrorCode::IMPORT_PARAMETER_MISMATCH,
              ImportKey(AuthorizationSetBuilder()
                            .RsaSigningKey(1024, 3 /* Doesn't match key */)
                            .Digest(Digest::NONE)
                            .Padding(PaddingMode::NONE),
                        KeyFormat::PKCS8, rsa_key));
}

/*
 * ImportKeyTest.EcdsaSuccess
 *
 * Verifies that importing and using an ECDSA P-256 key pair works correctly.
 */
TEST_F(ImportKeyTest, EcdsaSuccess) {
    ASSERT_EQ(ErrorCode::OK, ImportKey(AuthorizationSetBuilder()
                                           .Authorization(TAG_NO_AUTH_REQUIRED)
                                           .EcdsaSigningKey(256)
                                           .Digest(Digest::SHA_2_256),
                                       KeyFormat::PKCS8, ec_256_key))
        << "(Possibly b/33945114)";

    CheckKm0CryptoParam(TAG_ALGORITHM, Algorithm::EC);
    CheckKm0CryptoParam(TAG_KEY_SIZE, 256U);
    CheckKm1CryptoParam(TAG_DIGEST, Digest::SHA_2_256);
    CheckKm2CryptoParam(TAG_EC_CURVE, EcCurve::P_256);

    CheckOrigin();

    string message(32, 'a');
    auto params = AuthorizationSetBuilder().Digest(Digest::SHA_2_256);
    string signature = SignMessage(message, params);
    VerifyMessage(message, signature, params);
}

/*
 * ImportKeyTest.Ecdsa521Success
 *
 * Verifies that importing and using an ECDSA P-521 key pair works correctly.
 */
TEST_F(ImportKeyTest, Ecdsa521Success) {
    ASSERT_EQ(ErrorCode::OK, ImportKey(AuthorizationSetBuilder()
                                           .Authorization(TAG_NO_AUTH_REQUIRED)
                                           .EcdsaSigningKey(521)
                                           .Digest(Digest::SHA_2_256),
                                       KeyFormat::PKCS8, ec_521_key))
        << "(Possibly b/33945114)";

    CheckKm0CryptoParam(TAG_ALGORITHM, Algorithm::EC);
    CheckKm0CryptoParam(TAG_KEY_SIZE, 521U);
    CheckKm1CryptoParam(TAG_DIGEST, Digest::SHA_2_256);
    CheckKm2CryptoParam(TAG_EC_CURVE, EcCurve::P_521);

    CheckOrigin();

    string message(32, 'a');
    auto params = AuthorizationSetBuilder().Digest(Digest::SHA_2_256);
    string signature = SignMessage(message, params);
    VerifyMessage(message, signature, params);
}

/*
 * ImportKeyTest.EcdsaSizeMismatch
 *
 * Verifies that importing an ECDSA key pair with a size that doesn't match the key fails in the
 * correct way.
 */
TEST_F(ImportKeyTest, EcdsaSizeMismatch) {
    ASSERT_EQ(ErrorCode::IMPORT_PARAMETER_MISMATCH,
              ImportKey(AuthorizationSetBuilder()
                            .EcdsaSigningKey(224 /* Doesn't match key */)
                            .Digest(Digest::NONE),
                        KeyFormat::PKCS8, ec_256_key));
}

/*
 * ImportKeyTest.EcdsaCurveMismatch
 *
 * Verifies that importing an ECDSA key pair with a curve that doesn't match the key fails in the
 * correct way.
 */
TEST_F(ImportKeyTest, EcdsaCurveMismatch) {
    if (SupportsSymmetric() && !SupportsAttestation()) {
        // KM1 hardware doesn't know about curves
        return;
    }

    ASSERT_EQ(ErrorCode::IMPORT_PARAMETER_MISMATCH,
              ImportKey(AuthorizationSetBuilder()
                            .EcdsaSigningKey(EcCurve::P_224 /* Doesn't match key */)
                            .Digest(Digest::NONE),
                        KeyFormat::PKCS8, ec_256_key))
        << "(Possibly b/36233241)";
}

/*
 * ImportKeyTest.AesSuccess
 *
 * Verifies that importing and using an AES key works.
 */
TEST_F(ImportKeyTest, AesSuccess) {
    string key = {0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0};
    ASSERT_EQ(ErrorCode::OK, ImportKey(AuthorizationSetBuilder()
                                           .Authorization(TAG_NO_AUTH_REQUIRED)
                                           .AesEncryptionKey(key.size() * 8)
                                           .EcbMode()
                                           .Padding(PaddingMode::PKCS7),
                                       KeyFormat::RAW, key));

    CheckKm1CryptoParam(TAG_ALGORITHM, Algorithm::AES);
    CheckKm1CryptoParam(TAG_KEY_SIZE, 128U);
    CheckKm1CryptoParam(TAG_PADDING, PaddingMode::PKCS7);
    CheckKm1CryptoParam(TAG_BLOCK_MODE, BlockMode::ECB);
    CheckOrigin();

    string message = "Hello World!";
    auto params = AuthorizationSetBuilder().BlockMode(BlockMode::ECB).Padding(PaddingMode::PKCS7);
    string ciphertext = EncryptMessage(message, params);
    string plaintext = DecryptMessage(ciphertext, params);
    EXPECT_EQ(message, plaintext);
}

/*
 * ImportKeyTest.AesSuccess
 *
 * Verifies that importing and using an HMAC key works.
 */
TEST_F(ImportKeyTest, HmacKeySuccess) {
    string key = {0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0};
    ASSERT_EQ(ErrorCode::OK, ImportKey(AuthorizationSetBuilder()
                                           .Authorization(TAG_NO_AUTH_REQUIRED)
                                           .HmacKey(key.size() * 8)
                                           .Digest(Digest::SHA_2_256)
                                           .Authorization(TAG_MIN_MAC_LENGTH, 256),
                                       KeyFormat::RAW, key));

    CheckKm1CryptoParam(TAG_ALGORITHM, Algorithm::HMAC);
    CheckKm1CryptoParam(TAG_KEY_SIZE, 128U);
    CheckKm1CryptoParam(TAG_DIGEST, Digest::SHA_2_256);
    CheckOrigin();

    string message = "Hello World!";
    string signature = MacMessage(message, Digest::SHA_2_256, 256);
    VerifyMessage(message, signature, AuthorizationSetBuilder().Digest(Digest::SHA_2_256));
}

typedef KeymasterHidlTest EncryptionOperationsTest;

/*
 * EncryptionOperationsTest.RsaNoPaddingSuccess
 *
 * Verifies that raw RSA encryption works.
 */
TEST_F(EncryptionOperationsTest, RsaNoPaddingSuccess) {
    ASSERT_EQ(ErrorCode::OK, GenerateKey(AuthorizationSetBuilder()
                                             .Authorization(TAG_NO_AUTH_REQUIRED)
                                             .RsaEncryptionKey(1024, 3)
                                             .Padding(PaddingMode::NONE)));

    string message = string(1024 / 8, 'a');
    auto params = AuthorizationSetBuilder().Padding(PaddingMode::NONE);
    string ciphertext1 = EncryptMessage(message, params);
    EXPECT_EQ(1024U / 8, ciphertext1.size());

    string ciphertext2 = EncryptMessage(message, params);
    EXPECT_EQ(1024U / 8, ciphertext2.size());

    // Unpadded RSA is deterministic
    EXPECT_EQ(ciphertext1, ciphertext2);
}

/*
 * EncryptionOperationsTest.RsaNoPaddingShortMessage
 *
 * Verifies that raw RSA encryption of short messages works.
 */
TEST_F(EncryptionOperationsTest, RsaNoPaddingShortMessage) {
    ASSERT_EQ(ErrorCode::OK, GenerateKey(AuthorizationSetBuilder()
                                             .Authorization(TAG_NO_AUTH_REQUIRED)
                                             .RsaEncryptionKey(1024, 3)
                                             .Padding(PaddingMode::NONE)));

    string message = "1";
    auto params = AuthorizationSetBuilder().Padding(PaddingMode::NONE);

    string ciphertext = EncryptMessage(message, params);
    EXPECT_EQ(1024U / 8, ciphertext.size());

    string expected_plaintext = string(1024 / 8 - 1, 0) + message;
    string plaintext = DecryptMessage(ciphertext, params);

    EXPECT_EQ(expected_plaintext, plaintext);

    // Degenerate case, encrypting a numeric 1 yields 0x00..01 as the ciphertext.
    message = static_cast<char>(1);
    ciphertext = EncryptMessage(message, params);
    EXPECT_EQ(1024U / 8, ciphertext.size());
    EXPECT_EQ(ciphertext, string(1024 / 8 - 1, 0) + message);
}

/*
 * EncryptionOperationsTest.RsaNoPaddingTooLong
 *
 * Verifies that raw RSA encryption of too-long messages fails in the expected way.
 */
TEST_F(EncryptionOperationsTest, RsaNoPaddingTooLong) {
    ASSERT_EQ(ErrorCode::OK, GenerateKey(AuthorizationSetBuilder()
                                             .Authorization(TAG_NO_AUTH_REQUIRED)
                                             .RsaEncryptionKey(1024, 3)
                                             .Padding(PaddingMode::NONE)));

    string message(1024 / 8 + 1, 'a');

    auto params = AuthorizationSetBuilder().Padding(PaddingMode::NONE);
    EXPECT_EQ(ErrorCode::OK, Begin(KeyPurpose::ENCRYPT, params));

    string result;
    EXPECT_EQ(ErrorCode::INVALID_INPUT_LENGTH, Finish(message, &result));
}

/*
 * EncryptionOperationsTest.RsaNoPaddingTooLarge
 *
 * Verifies that raw RSA encryption of too-large (numerically) messages fails in the expected way.
 */
TEST_F(EncryptionOperationsTest, RsaNoPaddingTooLarge) {
    ASSERT_EQ(ErrorCode::OK, GenerateKey(AuthorizationSetBuilder()
                                             .Authorization(TAG_NO_AUTH_REQUIRED)
                                             .RsaEncryptionKey(1024, 3)
                                             .Padding(PaddingMode::NONE)));

    HidlBuf exported;
    ASSERT_EQ(ErrorCode::OK, ExportKey(KeyFormat::X509, &exported));

    const uint8_t* p = exported.data();
    EVP_PKEY_Ptr pkey(d2i_PUBKEY(nullptr /* alloc new */, &p, exported.size()));
    RSA_Ptr rsa(EVP_PKEY_get1_RSA(pkey.get()));

    size_t modulus_len = BN_num_bytes(rsa->n);
    ASSERT_EQ(1024U / 8, modulus_len);
    std::unique_ptr<uint8_t[]> modulus_buf(new uint8_t[modulus_len]);
    BN_bn2bin(rsa->n, modulus_buf.get());

    // The modulus is too big to encrypt.
    string message(reinterpret_cast<const char*>(modulus_buf.get()), modulus_len);

    auto params = AuthorizationSetBuilder().Padding(PaddingMode::NONE);
    EXPECT_EQ(ErrorCode::OK, Begin(KeyPurpose::ENCRYPT, params));

    string result;
    EXPECT_EQ(ErrorCode::INVALID_ARGUMENT, Finish(message, &result));

    // One smaller than the modulus is okay.
    BN_sub(rsa->n, rsa->n, BN_value_one());
    modulus_len = BN_num_bytes(rsa->n);
    ASSERT_EQ(1024U / 8, modulus_len);
    BN_bn2bin(rsa->n, modulus_buf.get());
    message = string(reinterpret_cast<const char*>(modulus_buf.get()), modulus_len);
    EXPECT_EQ(ErrorCode::OK, Begin(KeyPurpose::ENCRYPT, params));
    EXPECT_EQ(ErrorCode::OK, Finish(message, &result));
}

/*
 * EncryptionOperationsTest.RsaOaepSuccess
 *
 * Verifies that RSA-OAEP encryption operations work, with all digests.
 */
TEST_F(EncryptionOperationsTest, RsaOaepSuccess) {
    auto digests = {Digest::MD5,       Digest::SHA1,      Digest::SHA_2_224,
                    Digest::SHA_2_256, Digest::SHA_2_384, Digest::SHA_2_512};

    size_t key_size = 2048;  // Need largish key for SHA-512 test.
    ASSERT_EQ(ErrorCode::OK, GenerateKey(AuthorizationSetBuilder()
                                             .Authorization(TAG_NO_AUTH_REQUIRED)
                                             .RsaEncryptionKey(key_size, 3)
                                             .Padding(PaddingMode::RSA_OAEP)
                                             .Digest(digests)));

    string message = "Hello";

    for (auto digest : digests) {
        auto params = AuthorizationSetBuilder().Digest(digest).Padding(PaddingMode::RSA_OAEP);
        string ciphertext1 = EncryptMessage(message, params);
        if (HasNonfatalFailure()) std::cout << "-->" << digest << std::endl;
        EXPECT_EQ(key_size / 8, ciphertext1.size());

        string ciphertext2 = EncryptMessage(message, params);
        EXPECT_EQ(key_size / 8, ciphertext2.size());

        // OAEP randomizes padding so every result should be different (with astronomically high
        // probability).
        EXPECT_NE(ciphertext1, ciphertext2);

        string plaintext1 = DecryptMessage(ciphertext1, params);
        EXPECT_EQ(message, plaintext1) << "RSA-OAEP failed with digest " << digest;
        string plaintext2 = DecryptMessage(ciphertext2, params);
        EXPECT_EQ(message, plaintext2) << "RSA-OAEP failed with digest " << digest;

        // Decrypting corrupted ciphertext should fail.
        size_t offset_to_corrupt = random() % ciphertext1.size();
        char corrupt_byte;
        do {
            corrupt_byte = static_cast<char>(random() % 256);
        } while (corrupt_byte == ciphertext1[offset_to_corrupt]);
        ciphertext1[offset_to_corrupt] = corrupt_byte;

        EXPECT_EQ(ErrorCode::OK, Begin(KeyPurpose::DECRYPT, params));
        string result;
        EXPECT_EQ(ErrorCode::UNKNOWN_ERROR, Finish(ciphertext1, &result));
        EXPECT_EQ(0U, result.size());
    }
}

/*
 * EncryptionOperationsTest.RsaOaepInvalidDigest
 *
 * Verifies that RSA-OAEP encryption operations fail in the correct way when asked to operate
 * without a digest.
 */
TEST_F(EncryptionOperationsTest, RsaOaepInvalidDigest) {
    ASSERT_EQ(ErrorCode::OK, GenerateKey(AuthorizationSetBuilder()
                                             .Authorization(TAG_NO_AUTH_REQUIRED)
                                             .RsaEncryptionKey(1024, 3)
                                             .Padding(PaddingMode::RSA_OAEP)
                                             .Digest(Digest::NONE)));
    string message = "Hello World!";

    auto params = AuthorizationSetBuilder().Padding(PaddingMode::RSA_OAEP).Digest(Digest::NONE);
    EXPECT_EQ(ErrorCode::INCOMPATIBLE_DIGEST, Begin(KeyPurpose::ENCRYPT, params));
}

/*
 * EncryptionOperationsTest.RsaOaepInvalidDigest
 *
 * Verifies that RSA-OAEP encryption operations fail in the correct way when asked to decrypt with a
 * different digest than was used to encrypt.
 */
TEST_F(EncryptionOperationsTest, RsaOaepDecryptWithWrongDigest) {
    ASSERT_EQ(ErrorCode::OK, GenerateKey(AuthorizationSetBuilder()
                                             .Authorization(TAG_NO_AUTH_REQUIRED)
                                             .RsaEncryptionKey(1024, 3)
                                             .Padding(PaddingMode::RSA_OAEP)
                                             .Digest(Digest::SHA_2_256, Digest::SHA_2_224)));
    string message = "Hello World!";
    string ciphertext = EncryptMessage(
        message,
        AuthorizationSetBuilder().Digest(Digest::SHA_2_224).Padding(PaddingMode::RSA_OAEP));

    EXPECT_EQ(
        ErrorCode::OK,
        Begin(KeyPurpose::DECRYPT,
              AuthorizationSetBuilder().Digest(Digest::SHA_2_256).Padding(PaddingMode::RSA_OAEP)));
    string result;
    EXPECT_EQ(ErrorCode::UNKNOWN_ERROR, Finish(ciphertext, &result));
    EXPECT_EQ(0U, result.size());
}

/*
 * EncryptionOperationsTest.RsaOaepTooLarge
 *
 * Verifies that RSA-OAEP encryption operations fail in the correct way when asked to encrypt a
 * too-large message.
 */
TEST_F(EncryptionOperationsTest, RsaOaepTooLarge) {
    ASSERT_EQ(ErrorCode::OK, GenerateKey(AuthorizationSetBuilder()
                                             .Authorization(TAG_NO_AUTH_REQUIRED)
                                             .RsaEncryptionKey(1024, 3)
                                             .Padding(PaddingMode::RSA_OAEP)
                                             .Digest(Digest::SHA1)));
    constexpr size_t digest_size = 160 /* SHA1 */ / 8;
    constexpr size_t oaep_overhead = 2 * digest_size + 2;
    string message(1024 / 8 - oaep_overhead + 1, 'a');
    EXPECT_EQ(ErrorCode::OK,
              Begin(KeyPurpose::ENCRYPT,
                    AuthorizationSetBuilder().Padding(PaddingMode::RSA_OAEP).Digest(Digest::SHA1)));
    string result;
    auto error = Finish(message, &result);
    EXPECT_TRUE(error == ErrorCode::INVALID_INPUT_LENGTH || error == ErrorCode::INVALID_ARGUMENT);
    EXPECT_EQ(0U, result.size());
}

/*
 * EncryptionOperationsTest.RsaPkcs1Success
 *
 * Verifies that RSA PKCS encryption/decrypts works.
 */
TEST_F(EncryptionOperationsTest, RsaPkcs1Success) {
    ASSERT_EQ(ErrorCode::OK, GenerateKey(AuthorizationSetBuilder()
                                             .Authorization(TAG_NO_AUTH_REQUIRED)
                                             .RsaEncryptionKey(1024, 3)
                                             .Padding(PaddingMode::RSA_PKCS1_1_5_ENCRYPT)));

    string message = "Hello World!";
    auto params = AuthorizationSetBuilder().Padding(PaddingMode::RSA_PKCS1_1_5_ENCRYPT);
    string ciphertext1 = EncryptMessage(message, params);
    EXPECT_EQ(1024U / 8, ciphertext1.size());

    string ciphertext2 = EncryptMessage(message, params);
    EXPECT_EQ(1024U / 8, ciphertext2.size());

    // PKCS1 v1.5 randomizes padding so every result should be different.
    EXPECT_NE(ciphertext1, ciphertext2);

    string plaintext = DecryptMessage(ciphertext1, params);
    EXPECT_EQ(message, plaintext);

    // Decrypting corrupted ciphertext should fail.
    size_t offset_to_corrupt = random() % ciphertext1.size();
    char corrupt_byte;
    do {
        corrupt_byte = static_cast<char>(random() % 256);
    } while (corrupt_byte == ciphertext1[offset_to_corrupt]);
    ciphertext1[offset_to_corrupt] = corrupt_byte;

    EXPECT_EQ(ErrorCode::OK, Begin(KeyPurpose::DECRYPT, params));
    string result;
    EXPECT_EQ(ErrorCode::UNKNOWN_ERROR, Finish(ciphertext1, &result));
    EXPECT_EQ(0U, result.size());
}

/*
 * EncryptionOperationsTest.RsaPkcs1TooLarge
 *
 * Verifies that RSA PKCS encryption fails in the correct way when the mssage is too large.
 */
TEST_F(EncryptionOperationsTest, RsaPkcs1TooLarge) {
    ASSERT_EQ(ErrorCode::OK, GenerateKey(AuthorizationSetBuilder()
                                             .Authorization(TAG_NO_AUTH_REQUIRED)
                                             .RsaEncryptionKey(1024, 3)
                                             .Padding(PaddingMode::RSA_PKCS1_1_5_ENCRYPT)));
    string message(1024 / 8 - 10, 'a');

    auto params = AuthorizationSetBuilder().Padding(PaddingMode::RSA_PKCS1_1_5_ENCRYPT);
    EXPECT_EQ(ErrorCode::OK, Begin(KeyPurpose::ENCRYPT, params));
    string result;
    auto error = Finish(message, &result);
    EXPECT_TRUE(error == ErrorCode::INVALID_INPUT_LENGTH || error == ErrorCode::INVALID_ARGUMENT);
    EXPECT_EQ(0U, result.size());
}

/*
 * EncryptionOperationsTest.EcdsaEncrypt
 *
 * Verifies that attempting to use ECDSA keys to encrypt fails in the correct way.
 */
TEST_F(EncryptionOperationsTest, EcdsaEncrypt) {
    ASSERT_EQ(ErrorCode::OK, GenerateKey(AuthorizationSetBuilder()
                                             .Authorization(TAG_NO_AUTH_REQUIRED)
                                             .EcdsaSigningKey(224)
                                             .Digest(Digest::NONE)));
    auto params = AuthorizationSetBuilder().Digest(Digest::NONE);
    ASSERT_EQ(ErrorCode::UNSUPPORTED_PURPOSE, Begin(KeyPurpose::ENCRYPT, params))
        << "(Possibly b/33543625)";
    ASSERT_EQ(ErrorCode::UNSUPPORTED_PURPOSE, Begin(KeyPurpose::DECRYPT, params))
        << "(Possibly b/33543625)";
}

/*
 * EncryptionOperationsTest.HmacEncrypt
 *
 * Verifies that attempting to use HMAC keys to encrypt fails in the correct way.
 */
TEST_F(EncryptionOperationsTest, HmacEncrypt) {
    ASSERT_EQ(ErrorCode::OK, GenerateKey(AuthorizationSetBuilder()
                                             .Authorization(TAG_NO_AUTH_REQUIRED)
                                             .HmacKey(128)
                                             .Digest(Digest::SHA_2_256)
                                             .Padding(PaddingMode::NONE)
                                             .Authorization(TAG_MIN_MAC_LENGTH, 128)));
    auto params = AuthorizationSetBuilder()
                      .Digest(Digest::SHA_2_256)
                      .Padding(PaddingMode::NONE)
                      .Authorization(TAG_MAC_LENGTH, 128);
    ASSERT_EQ(ErrorCode::UNSUPPORTED_PURPOSE, Begin(KeyPurpose::ENCRYPT, params))
        << "(Possibly b/33543625)";
    ASSERT_EQ(ErrorCode::UNSUPPORTED_PURPOSE, Begin(KeyPurpose::DECRYPT, params))
        << "(Possibly b/33543625)";
}

/*
 * EncryptionOperationsTest.AesEcbRoundTripSuccess
 *
 * Verifies that AES ECB mode works.
 */
TEST_F(EncryptionOperationsTest, AesEcbRoundTripSuccess) {
    ASSERT_EQ(ErrorCode::OK, GenerateKey(AuthorizationSetBuilder()
                                             .Authorization(TAG_NO_AUTH_REQUIRED)
                                             .AesEncryptionKey(128)
                                             .Authorization(TAG_BLOCK_MODE, BlockMode::ECB)
                                             .Padding(PaddingMode::NONE)));

    auto params = AuthorizationSetBuilder().BlockMode(BlockMode::ECB).Padding(PaddingMode::NONE);

    // Two-block message.
    string message = "12345678901234567890123456789012";
    string ciphertext1 = EncryptMessage(message, params);
    EXPECT_EQ(message.size(), ciphertext1.size());

    string ciphertext2 = EncryptMessage(string(message), params);
    EXPECT_EQ(message.size(), ciphertext2.size());

    // ECB is deterministic.
    EXPECT_EQ(ciphertext1, ciphertext2);

    string plaintext = DecryptMessage(ciphertext1, params);
    EXPECT_EQ(message, plaintext);
}

/*
 * EncryptionOperationsTest.AesEcbRoundTripSuccess
 *
 * Verifies that AES encryption fails in the correct way when an unauthorized mode is specified.
 */
TEST_F(EncryptionOperationsTest, AesWrongMode) {
    ASSERT_EQ(ErrorCode::OK, GenerateKey(AuthorizationSetBuilder()
                                             .Authorization(TAG_NO_AUTH_REQUIRED)
                                             .AesEncryptionKey(128)
                                             .Authorization(TAG_BLOCK_MODE, BlockMode::CBC)
                                             .Padding(PaddingMode::NONE)));
    // Two-block message.
    string message = "12345678901234567890123456789012";
    EXPECT_EQ(
        ErrorCode::INCOMPATIBLE_BLOCK_MODE,
        Begin(KeyPurpose::ENCRYPT,
              AuthorizationSetBuilder().BlockMode(BlockMode::ECB).Padding(PaddingMode::NONE)));
}

/*
 * EncryptionOperationsTest.AesEcbNoPaddingWrongInputSize
 *
 * Verifies that AES encryption fails in the correct way when provided an input that is not a
 * multiple of the block size and no padding is specified.
 */
TEST_F(EncryptionOperationsTest, AesEcbNoPaddingWrongInputSize) {
    ASSERT_EQ(ErrorCode::OK, GenerateKey(AuthorizationSetBuilder()
                                             .Authorization(TAG_NO_AUTH_REQUIRED)
                                             .AesEncryptionKey(128)
                                             .Authorization(TAG_BLOCK_MODE, BlockMode::ECB)
                                             .Padding(PaddingMode::NONE)));
    // Message is slightly shorter than two blocks.
    string message(16 * 2 - 1, 'a');

    auto params = AuthorizationSetBuilder().BlockMode(BlockMode::ECB).Padding(PaddingMode::NONE);
    EXPECT_EQ(ErrorCode::OK, Begin(KeyPurpose::ENCRYPT, params));
    string ciphertext;
    EXPECT_EQ(ErrorCode::INVALID_INPUT_LENGTH, Finish(message, &ciphertext));
    EXPECT_EQ(0U, ciphertext.size());
}

/*
 * EncryptionOperationsTest.AesEcbPkcs7Padding
 *
 * Verifies that AES PKCS7 padding works for any message length.
 */
TEST_F(EncryptionOperationsTest, AesEcbPkcs7Padding) {
    ASSERT_EQ(ErrorCode::OK, GenerateKey(AuthorizationSetBuilder()
                                             .Authorization(TAG_NO_AUTH_REQUIRED)
                                             .AesEncryptionKey(128)
                                             .Authorization(TAG_BLOCK_MODE, BlockMode::ECB)
                                             .Padding(PaddingMode::PKCS7)));

    auto params = AuthorizationSetBuilder().BlockMode(BlockMode::ECB).Padding(PaddingMode::PKCS7);

    // Try various message lengths; all should work.
    for (size_t i = 0; i < 32; ++i) {
        string message(i, 'a');
        string ciphertext = EncryptMessage(message, params);
        EXPECT_EQ(i + 16 - (i % 16), ciphertext.size());
        string plaintext = DecryptMessage(ciphertext, params);
        EXPECT_EQ(message, plaintext);
    }
}

/*
 * EncryptionOperationsTest.AesEcbWrongPadding
 *
 * Verifies that AES enryption fails in the correct way when an unauthorized padding mode is
 * specified.
 */
TEST_F(EncryptionOperationsTest, AesEcbWrongPadding) {
    ASSERT_EQ(ErrorCode::OK, GenerateKey(AuthorizationSetBuilder()
                                             .Authorization(TAG_NO_AUTH_REQUIRED)
                                             .AesEncryptionKey(128)
                                             .Authorization(TAG_BLOCK_MODE, BlockMode::ECB)
                                             .Padding(PaddingMode::NONE)));

    auto params = AuthorizationSetBuilder().BlockMode(BlockMode::ECB).Padding(PaddingMode::PKCS7);

    // Try various message lengths; all should fail
    for (size_t i = 0; i < 32; ++i) {
        string message(i, 'a');
        EXPECT_EQ(ErrorCode::INCOMPATIBLE_PADDING_MODE, Begin(KeyPurpose::ENCRYPT, params));
    }
}

/*
 * EncryptionOperationsTest.AesEcbPkcs7PaddingCorrupted
 *
 * Verifies that AES decryption fails in the correct way when the padding is corrupted.
 */
TEST_F(EncryptionOperationsTest, AesEcbPkcs7PaddingCorrupted) {
    ASSERT_EQ(ErrorCode::OK, GenerateKey(AuthorizationSetBuilder()
                                             .Authorization(TAG_NO_AUTH_REQUIRED)
                                             .AesEncryptionKey(128)
                                             .Authorization(TAG_BLOCK_MODE, BlockMode::ECB)
                                             .Padding(PaddingMode::PKCS7)));

    auto params = AuthorizationSetBuilder().BlockMode(BlockMode::ECB).Padding(PaddingMode::PKCS7);

    string message = "a";
    string ciphertext = EncryptMessage(message, params);
    EXPECT_EQ(16U, ciphertext.size());
    EXPECT_NE(ciphertext, message);
    ++ciphertext[ciphertext.size() / 2];

    EXPECT_EQ(ErrorCode::OK, Begin(KeyPurpose::DECRYPT, params));
    string plaintext;
    EXPECT_EQ(ErrorCode::INVALID_INPUT_LENGTH, Finish(message, &plaintext));
}

HidlBuf CopyIv(const AuthorizationSet& set) {
    auto iv = set.GetTagValue(TAG_NONCE);
    EXPECT_TRUE(iv.isOk());
    return iv.value();
}

/*
 * EncryptionOperationsTest.AesCtrRoundTripSuccess
 *
 * Verifies that AES CTR mode works.
 */
TEST_F(EncryptionOperationsTest, AesCtrRoundTripSuccess) {
    ASSERT_EQ(ErrorCode::OK, GenerateKey(AuthorizationSetBuilder()
                                             .Authorization(TAG_NO_AUTH_REQUIRED)
                                             .AesEncryptionKey(128)
                                             .Authorization(TAG_BLOCK_MODE, BlockMode::CTR)
                                             .Padding(PaddingMode::NONE)));

    auto params = AuthorizationSetBuilder().BlockMode(BlockMode::CTR).Padding(PaddingMode::NONE);

    string message = "123";
    AuthorizationSet out_params;
    string ciphertext1 = EncryptMessage(message, params, &out_params);
    HidlBuf iv1 = CopyIv(out_params);
    EXPECT_EQ(16U, iv1.size());

    EXPECT_EQ(message.size(), ciphertext1.size());

    out_params.Clear();
    string ciphertext2 = EncryptMessage(message, params, &out_params);
    HidlBuf iv2 = CopyIv(out_params);
    EXPECT_EQ(16U, iv2.size());

    // IVs should be random, so ciphertexts should differ.
    EXPECT_NE(ciphertext1, ciphertext2);

    auto params_iv1 =
        AuthorizationSetBuilder().Authorizations(params).Authorization(TAG_NONCE, iv1);
    auto params_iv2 =
        AuthorizationSetBuilder().Authorizations(params).Authorization(TAG_NONCE, iv2);

    string plaintext = DecryptMessage(ciphertext1, params_iv1);
    EXPECT_EQ(message, plaintext);
    plaintext = DecryptMessage(ciphertext2, params_iv2);
    EXPECT_EQ(message, plaintext);

    // Using the wrong IV will result in a "valid" decryption, but the data will be garbage.
    plaintext = DecryptMessage(ciphertext1, params_iv2);
    EXPECT_NE(message, plaintext);
    plaintext = DecryptMessage(ciphertext2, params_iv1);
    EXPECT_NE(message, plaintext);
}

/*
 * EncryptionOperationsTest.AesIncremental
 *
 * Verifies that AES works, all modes, when provided data in various size increments.
 */
TEST_F(EncryptionOperationsTest, AesIncremental) {
    auto block_modes = {
        BlockMode::ECB, BlockMode::CBC, BlockMode::CTR, BlockMode::GCM,
    };

    ASSERT_EQ(ErrorCode::OK, GenerateKey(AuthorizationSetBuilder()
                                             .Authorization(TAG_NO_AUTH_REQUIRED)
                                             .AesEncryptionKey(128)
                                             .BlockMode(block_modes)
                                             .Padding(PaddingMode::NONE)
                                             .Authorization(TAG_MIN_MAC_LENGTH, 128)));

    for (int increment = 1; increment <= 240; ++increment) {
        for (auto block_mode : block_modes) {
            string message(240, 'a');
            auto params = AuthorizationSetBuilder()
                              .BlockMode(block_mode)
                              .Padding(PaddingMode::NONE)
                              .Authorization(TAG_MAC_LENGTH, 128) /* for GCM */;

            AuthorizationSet output_params;
            EXPECT_EQ(ErrorCode::OK, Begin(KeyPurpose::ENCRYPT, params, &output_params));

            string ciphertext;
            size_t input_consumed;
            string to_send;
            for (size_t i = 0; i < message.size(); i += increment) {
                to_send.append(message.substr(i, increment));
                EXPECT_EQ(ErrorCode::OK, Update(to_send, &ciphertext, &input_consumed));
                to_send = to_send.substr(input_consumed);

                switch (block_mode) {
                case BlockMode::ECB:
                case BlockMode::CBC:
                    // Implementations must take as many blocks as possible, leaving less than
                    // a block.
                    EXPECT_LE(to_send.length(), 16U);
                    break;
                case BlockMode::GCM:
                case BlockMode::CTR:
                    // Implementations must always take all the data.
                    EXPECT_EQ(0U, to_send.length());
                    break;
                }
            }
            EXPECT_EQ(ErrorCode::OK, Finish(to_send, &ciphertext)) << "Error sending " << to_send;

            switch (block_mode) {
            case BlockMode::GCM:
                EXPECT_EQ(message.size() + 16, ciphertext.size());
                break;
            case BlockMode::CTR:
                EXPECT_EQ(message.size(), ciphertext.size());
                break;
            case BlockMode::CBC:
            case BlockMode::ECB:
                EXPECT_EQ(message.size() + message.size() % 16, ciphertext.size());
                break;
            }

            auto iv = output_params.GetTagValue(TAG_NONCE);
            switch (block_mode) {
            case BlockMode::CBC:
            case BlockMode::GCM:
            case BlockMode::CTR:
                ASSERT_TRUE(iv.isOk()) << "No IV for block mode " << block_mode;
                EXPECT_EQ(block_mode == BlockMode::GCM ? 12U : 16U, iv.value().size());
                params.push_back(TAG_NONCE, iv.value());
                break;

            case BlockMode::ECB:
                EXPECT_FALSE(iv.isOk()) << "ECB mode should not generate IV";
                break;
            }

            EXPECT_EQ(ErrorCode::OK, Begin(KeyPurpose::DECRYPT, params))
                << "Decrypt begin() failed for block mode " << block_mode;

            string plaintext;
            for (size_t i = 0; i < ciphertext.size(); i += increment) {
                to_send.append(ciphertext.substr(i, increment));
                EXPECT_EQ(ErrorCode::OK, Update(to_send, &plaintext, &input_consumed));
                to_send = to_send.substr(input_consumed);
            }
            ErrorCode error = Finish(to_send, &plaintext);
            ASSERT_EQ(ErrorCode::OK, error)
                << "Decryption failed for block mode " << block_mode << " and increment "
                << increment << " (Possibly b/33584622)";
            if (error == ErrorCode::OK) {
                ASSERT_EQ(message, plaintext) << "Decryption didn't match for block mode "
                                              << block_mode << " and increment " << increment;
            }
        }
    }
}

struct AesCtrSp80038aTestVector {
    const char* key;
    const char* nonce;
    const char* plaintext;
    const char* ciphertext;
};

// These test vectors are taken from
// http://csrc.nist.gov/publications/nistpubs/800-38a/sp800-38a.pdf, section F.5.
static const AesCtrSp80038aTestVector kAesCtrSp80038aTestVectors[] = {
    // AES-128
    {
        "2b7e151628aed2a6abf7158809cf4f3c", "f0f1f2f3f4f5f6f7f8f9fafbfcfdfeff",
        "6bc1bee22e409f96e93d7e117393172aae2d8a571e03ac9c9eb76fac45af8e51"
        "30c81c46a35ce411e5fbc1191a0a52eff69f2445df4f9b17ad2b417be66c3710",
        "874d6191b620e3261bef6864990db6ce9806f66b7970fdff8617187bb9fffdff"
        "5ae4df3edbd5d35e5b4f09020db03eab1e031dda2fbe03d1792170a0f3009cee",
    },
    // AES-192
    {
        "8e73b0f7da0e6452c810f32b809079e562f8ead2522c6b7b", "f0f1f2f3f4f5f6f7f8f9fafbfcfdfeff",
        "6bc1bee22e409f96e93d7e117393172aae2d8a571e03ac9c9eb76fac45af8e51"
        "30c81c46a35ce411e5fbc1191a0a52eff69f2445df4f9b17ad2b417be66c3710",
        "1abc932417521ca24f2b0459fe7e6e0b090339ec0aa6faefd5ccc2c6f4ce8e94"
        "1e36b26bd1ebc670d1bd1d665620abf74f78a7f6d29809585a97daec58c6b050",
    },
    // AES-256
    {
        "603deb1015ca71be2b73aef0857d77811f352c073b6108d72d9810a30914dff4",
        "f0f1f2f3f4f5f6f7f8f9fafbfcfdfeff",
        "6bc1bee22e409f96e93d7e117393172aae2d8a571e03ac9c9eb76fac45af8e51"
        "30c81c46a35ce411e5fbc1191a0a52eff69f2445df4f9b17ad2b417be66c3710",
        "601ec313775789a5b7a7f504bbf3d228f443e3ca4d62b59aca84e990cacaf5c5"
        "2b0930daa23de94ce87017ba2d84988ddfc9c58db67aada613c2dd08457941a6",
    },
};

/*
 * EncryptionOperationsTest.AesCtrSp80038aTestVector
 *
 * Verifies AES CTR implementation against SP800-38A test vectors.
 */
TEST_F(EncryptionOperationsTest, AesCtrSp80038aTestVector) {
    for (size_t i = 0; i < 3; i++) {
        const AesCtrSp80038aTestVector& test(kAesCtrSp80038aTestVectors[i]);
        const string key = hex2str(test.key);
        const string nonce = hex2str(test.nonce);
        const string plaintext = hex2str(test.plaintext);
        const string ciphertext = hex2str(test.ciphertext);
        CheckAesCtrTestVector(key, nonce, plaintext, ciphertext);
    }
}

/*
 * EncryptionOperationsTest.AesCtrIncompatiblePaddingMode
 *
 * Verifies that keymaster rejects use of CTR mode with PKCS7 padding in the correct way.
 */
TEST_F(EncryptionOperationsTest, AesCtrIncompatiblePaddingMode) {
    ASSERT_EQ(ErrorCode::OK, GenerateKey(AuthorizationSetBuilder()
                                             .Authorization(TAG_NO_AUTH_REQUIRED)
                                             .AesEncryptionKey(128)
                                             .Authorization(TAG_BLOCK_MODE, BlockMode::CTR)
                                             .Padding(PaddingMode::PKCS7)));
    auto params = AuthorizationSetBuilder().BlockMode(BlockMode::CTR).Padding(PaddingMode::NONE);
    EXPECT_EQ(ErrorCode::INCOMPATIBLE_PADDING_MODE, Begin(KeyPurpose::ENCRYPT, params));
}

/*
 * EncryptionOperationsTest.AesCtrInvalidCallerNonce
 *
 * Verifies that keymaster fails correctly when the user supplies an incorrect-size nonce.
 */
TEST_F(EncryptionOperationsTest, AesCtrInvalidCallerNonce) {
    ASSERT_EQ(ErrorCode::OK, GenerateKey(AuthorizationSetBuilder()
                                             .Authorization(TAG_NO_AUTH_REQUIRED)
                                             .AesEncryptionKey(128)
                                             .Authorization(TAG_BLOCK_MODE, BlockMode::CTR)
                                             .Authorization(TAG_CALLER_NONCE)
                                             .Padding(PaddingMode::NONE)));

    auto params = AuthorizationSetBuilder()
                      .BlockMode(BlockMode::CTR)
                      .Padding(PaddingMode::NONE)
                      .Authorization(TAG_NONCE, HidlBuf(string(1, 'a')));
    EXPECT_EQ(ErrorCode::INVALID_NONCE, Begin(KeyPurpose::ENCRYPT, params));

    params = AuthorizationSetBuilder()
                 .BlockMode(BlockMode::CTR)
                 .Padding(PaddingMode::NONE)
                 .Authorization(TAG_NONCE, HidlBuf(string(15, 'a')));
    EXPECT_EQ(ErrorCode::INVALID_NONCE, Begin(KeyPurpose::ENCRYPT, params));

    params = AuthorizationSetBuilder()
                 .BlockMode(BlockMode::CTR)
                 .Padding(PaddingMode::NONE)
                 .Authorization(TAG_NONCE, HidlBuf(string(17, 'a')));
    EXPECT_EQ(ErrorCode::INVALID_NONCE, Begin(KeyPurpose::ENCRYPT, params));
}

/*
 * EncryptionOperationsTest.AesCtrInvalidCallerNonce
 *
 * Verifies that keymaster fails correctly when the user supplies an incorrect-size nonce.
 */
TEST_F(EncryptionOperationsTest, AesCbcRoundTripSuccess) {
    ASSERT_EQ(ErrorCode::OK, GenerateKey(AuthorizationSetBuilder()
                                             .Authorization(TAG_NO_AUTH_REQUIRED)
                                             .AesEncryptionKey(128)
                                             .Authorization(TAG_BLOCK_MODE, BlockMode::CBC)
                                             .Padding(PaddingMode::NONE)));
    // Two-block message.
    string message = "12345678901234567890123456789012";
    auto params = AuthorizationSetBuilder().BlockMode(BlockMode::CBC).Padding(PaddingMode::NONE);
    AuthorizationSet out_params;
    string ciphertext1 = EncryptMessage(message, params, &out_params);
    HidlBuf iv1 = CopyIv(out_params);
    EXPECT_EQ(message.size(), ciphertext1.size());

    out_params.Clear();

    string ciphertext2 = EncryptMessage(message, params, &out_params);
    HidlBuf iv2 = CopyIv(out_params);
    EXPECT_EQ(message.size(), ciphertext2.size());

    // IVs should be random, so ciphertexts should differ.
    EXPECT_NE(ciphertext1, ciphertext2);

    params.push_back(TAG_NONCE, iv1);
    string plaintext = DecryptMessage(ciphertext1, params);
    EXPECT_EQ(message, plaintext);
}

/*
 * EncryptionOperationsTest.AesCallerNonce
 *
 * Verifies that AES caller-provided nonces work correctly.
 */
TEST_F(EncryptionOperationsTest, AesCallerNonce) {
    ASSERT_EQ(ErrorCode::OK, GenerateKey(AuthorizationSetBuilder()
                                             .Authorization(TAG_NO_AUTH_REQUIRED)
                                             .AesEncryptionKey(128)
                                             .Authorization(TAG_BLOCK_MODE, BlockMode::CBC)
                                             .Authorization(TAG_CALLER_NONCE)
                                             .Padding(PaddingMode::NONE)));

    string message = "12345678901234567890123456789012";

    // Don't specify nonce, should get a random one.
    AuthorizationSetBuilder params =
        AuthorizationSetBuilder().BlockMode(BlockMode::CBC).Padding(PaddingMode::NONE);
    AuthorizationSet out_params;
    string ciphertext = EncryptMessage(message, params, &out_params);
    EXPECT_EQ(message.size(), ciphertext.size());
    EXPECT_EQ(16U, out_params.GetTagValue(TAG_NONCE).value().size());

    params.push_back(TAG_NONCE, out_params.GetTagValue(TAG_NONCE).value());
    string plaintext = DecryptMessage(ciphertext, params);
    EXPECT_EQ(message, plaintext);

    // Now specify a nonce, should also work.
    params = AuthorizationSetBuilder()
                 .BlockMode(BlockMode::CBC)
                 .Padding(PaddingMode::NONE)
                 .Authorization(TAG_NONCE, HidlBuf("abcdefghijklmnop"));
    out_params.Clear();
    ciphertext = EncryptMessage(message, params, &out_params);

    // Decrypt with correct nonce.
    plaintext = DecryptMessage(ciphertext, params);
    EXPECT_EQ(message, plaintext);

    // Try with wrong nonce.
    params = AuthorizationSetBuilder()
                 .BlockMode(BlockMode::CBC)
                 .Padding(PaddingMode::NONE)
                 .Authorization(TAG_NONCE, HidlBuf("aaaaaaaaaaaaaaaa"));
    plaintext = DecryptMessage(ciphertext, params);
    EXPECT_NE(message, plaintext);
}

/*
 * EncryptionOperationsTest.AesCallerNonceProhibited
 *
 * Verifies that caller-provided nonces are not permitted when not specified in the key
 * authorizations.
 */
TEST_F(EncryptionOperationsTest, AesCallerNonceProhibited) {
    ASSERT_EQ(ErrorCode::OK, GenerateKey(AuthorizationSetBuilder()
                                             .Authorization(TAG_NO_AUTH_REQUIRED)
                                             .AesEncryptionKey(128)
                                             .Authorization(TAG_BLOCK_MODE, BlockMode::CBC)
                                             .Padding(PaddingMode::NONE)));

    string message = "12345678901234567890123456789012";

    // Don't specify nonce, should get a random one.
    AuthorizationSetBuilder params =
        AuthorizationSetBuilder().BlockMode(BlockMode::CBC).Padding(PaddingMode::NONE);
    AuthorizationSet out_params;
    string ciphertext = EncryptMessage(message, params, &out_params);
    EXPECT_EQ(message.size(), ciphertext.size());
    EXPECT_EQ(16U, out_params.GetTagValue(TAG_NONCE).value().size());

    params.push_back(TAG_NONCE, out_params.GetTagValue(TAG_NONCE).value());
    string plaintext = DecryptMessage(ciphertext, params);
    EXPECT_EQ(message, plaintext);

    // Now specify a nonce, should fail
    params = AuthorizationSetBuilder()
                 .BlockMode(BlockMode::CBC)
                 .Padding(PaddingMode::NONE)
                 .Authorization(TAG_NONCE, HidlBuf("abcdefghijklmnop"));
    out_params.Clear();
    EXPECT_EQ(ErrorCode::CALLER_NONCE_PROHIBITED, Begin(KeyPurpose::ENCRYPT, params, &out_params));
}

/*
 * EncryptionOperationsTest.AesGcmRoundTripSuccess
 *
 * Verifies that AES GCM mode works.
 */
TEST_F(EncryptionOperationsTest, AesGcmRoundTripSuccess) {
    ASSERT_EQ(ErrorCode::OK, GenerateKey(AuthorizationSetBuilder()
                                             .Authorization(TAG_NO_AUTH_REQUIRED)
                                             .AesEncryptionKey(128)
                                             .Authorization(TAG_BLOCK_MODE, BlockMode::GCM)
                                             .Padding(PaddingMode::NONE)
                                             .Authorization(TAG_MIN_MAC_LENGTH, 128)));

    string aad = "foobar";
    string message = "123456789012345678901234567890123456";

    auto begin_params = AuthorizationSetBuilder()
                            .BlockMode(BlockMode::GCM)
                            .Padding(PaddingMode::NONE)
                            .Authorization(TAG_MAC_LENGTH, 128);

    auto update_params =
        AuthorizationSetBuilder().Authorization(TAG_ASSOCIATED_DATA, aad.data(), aad.size());

    // Encrypt
    AuthorizationSet begin_out_params;
    ASSERT_EQ(ErrorCode::OK, Begin(KeyPurpose::ENCRYPT, begin_params, &begin_out_params))
        << "Begin encrypt";
    string ciphertext;
    AuthorizationSet update_out_params;
    ASSERT_EQ(ErrorCode::OK,
              Finish(op_handle_, update_params, message, "", &update_out_params, &ciphertext));

    // Grab nonce
    begin_params.push_back(begin_out_params);

    // Decrypt.
    ASSERT_EQ(ErrorCode::OK, Begin(KeyPurpose::DECRYPT, begin_params)) << "Begin decrypt";
    string plaintext;
    size_t input_consumed;
    ASSERT_EQ(ErrorCode::OK, Update(op_handle_, update_params, ciphertext, &update_out_params,
                                    &plaintext, &input_consumed));
    EXPECT_EQ(ciphertext.size(), input_consumed);
    EXPECT_EQ(ErrorCode::OK, Finish("", &plaintext));

    EXPECT_EQ(message, plaintext);
}

/*
 * EncryptionOperationsTest.AesGcmTooShortTag
 *
 * Verifies that AES GCM mode fails correctly when a too-short tag length is specified.
 */
TEST_F(EncryptionOperationsTest, AesGcmTooShortTag) {
    ASSERT_EQ(ErrorCode::OK, GenerateKey(AuthorizationSetBuilder()
                                             .Authorization(TAG_NO_AUTH_REQUIRED)
                                             .AesEncryptionKey(128)
                                             .BlockMode(BlockMode::GCM)
                                             .Padding(PaddingMode::NONE)
                                             .Authorization(TAG_MIN_MAC_LENGTH, 128)));
    string message = "123456789012345678901234567890123456";
    auto params = AuthorizationSetBuilder()
                      .BlockMode(BlockMode::GCM)
                      .Padding(PaddingMode::NONE)
                      .Authorization(TAG_MAC_LENGTH, 96);

    EXPECT_EQ(ErrorCode::INVALID_MAC_LENGTH, Begin(KeyPurpose::ENCRYPT, params));
}

/*
 * EncryptionOperationsTest.AesGcmTooShortTagOnDecrypt
 *
 * Verifies that AES GCM mode fails correctly when a too-short tag is provided to decryption.
 */
TEST_F(EncryptionOperationsTest, AesGcmTooShortTagOnDecrypt) {
    ASSERT_EQ(ErrorCode::OK, GenerateKey(AuthorizationSetBuilder()
                                             .Authorization(TAG_NO_AUTH_REQUIRED)
                                             .AesEncryptionKey(128)
                                             .BlockMode(BlockMode::GCM)
                                             .Padding(PaddingMode::NONE)
                                             .Authorization(TAG_MIN_MAC_LENGTH, 128)));
    string aad = "foobar";
    string message = "123456789012345678901234567890123456";
    auto params = AuthorizationSetBuilder()
                      .BlockMode(BlockMode::GCM)
                      .Padding(PaddingMode::NONE)
                      .Authorization(TAG_MAC_LENGTH, 128);

    auto finish_params =
        AuthorizationSetBuilder().Authorization(TAG_ASSOCIATED_DATA, aad.data(), aad.size());

    // Encrypt
    AuthorizationSet begin_out_params;
    EXPECT_EQ(ErrorCode::OK, Begin(KeyPurpose::ENCRYPT, params, &begin_out_params));
    EXPECT_EQ(1U, begin_out_params.size());
    ASSERT_TRUE(begin_out_params.GetTagValue(TAG_NONCE).isOk());

    AuthorizationSet finish_out_params;
    string ciphertext;
    EXPECT_EQ(ErrorCode::OK, Finish(op_handle_, finish_params, message, "" /* signature */,
                                    &finish_out_params, &ciphertext));

    params = AuthorizationSetBuilder()
                 .Authorizations(begin_out_params)
                 .BlockMode(BlockMode::GCM)
                 .Padding(PaddingMode::NONE)
                 .Authorization(TAG_MAC_LENGTH, 96);

    // Decrypt.
    EXPECT_EQ(ErrorCode::INVALID_MAC_LENGTH, Begin(KeyPurpose::DECRYPT, params));
}

/*
 * EncryptionOperationsTest.AesGcmCorruptKey
 *
 * Verifies that AES GCM mode fails correctly when the decryption key is incorrect.
 */
TEST_F(EncryptionOperationsTest, AesGcmCorruptKey) {
    const uint8_t nonce_bytes[] = {
        0xb7, 0x94, 0x37, 0xae, 0x08, 0xff, 0x35, 0x5d, 0x7d, 0x8a, 0x4d, 0x0f,
    };
    string nonce = make_string(nonce_bytes);
    const uint8_t ciphertext_bytes[] = {
        0xb3, 0xf6, 0x79, 0x9e, 0x8f, 0x93, 0x26, 0xf2, 0xdf, 0x1e, 0x80, 0xfc, 0xd2, 0xcb, 0x16,
        0xd7, 0x8c, 0x9d, 0xc7, 0xcc, 0x14, 0xbb, 0x67, 0x78, 0x62, 0xdc, 0x6c, 0x63, 0x9b, 0x3a,
        0x63, 0x38, 0xd2, 0x4b, 0x31, 0x2d, 0x39, 0x89, 0xe5, 0x92, 0x0b, 0x5d, 0xbf, 0xc9, 0x76,
        0x76, 0x5e, 0xfb, 0xfe, 0x57, 0xbb, 0x38, 0x59, 0x40, 0xa7, 0xa4, 0x3b, 0xdf, 0x05, 0xbd,
        0xda, 0xe3, 0xc9, 0xd6, 0xa2, 0xfb, 0xbd, 0xfc, 0xc0, 0xcb, 0xa0,
    };
    string ciphertext = make_string(ciphertext_bytes);

    auto params = AuthorizationSetBuilder()
                      .BlockMode(BlockMode::GCM)
                      .Padding(PaddingMode::NONE)
                      .Authorization(TAG_MAC_LENGTH, 128)
                      .Authorization(TAG_NONCE, nonce.data(), nonce.size());

    auto import_params = AuthorizationSetBuilder()
                             .Authorization(TAG_NO_AUTH_REQUIRED)
                             .AesEncryptionKey(128)
                             .BlockMode(BlockMode::GCM)
                             .Padding(PaddingMode::NONE)
                             .Authorization(TAG_CALLER_NONCE)
                             .Authorization(TAG_MIN_MAC_LENGTH, 128);

    // Import correct key and decrypt
    const uint8_t key_bytes[] = {
        0xba, 0x76, 0x35, 0x4f, 0x0a, 0xed, 0x6e, 0x8d,
        0x91, 0xf4, 0x5c, 0x4f, 0xf5, 0xa0, 0x62, 0xdb,
    };
    string key = make_string(key_bytes);
    ASSERT_EQ(ErrorCode::OK, ImportKey(import_params, KeyFormat::RAW, key));
    string plaintext = DecryptMessage(ciphertext, params);
    CheckedDeleteKey();

    // Corrupt key and attempt to decrypt
    key[0] = 0;
    ASSERT_EQ(ErrorCode::OK, ImportKey(import_params, KeyFormat::RAW, key));
    EXPECT_EQ(ErrorCode::OK, Begin(KeyPurpose::DECRYPT, params));
    EXPECT_EQ(ErrorCode::VERIFICATION_FAILED, Finish(ciphertext, &plaintext));
    CheckedDeleteKey();
}

/*
 * EncryptionOperationsTest.AesGcmAadNoData
 *
 * Verifies that AES GCM mode works when provided additional authenticated data, but no data to
 * encrypt.
 */
TEST_F(EncryptionOperationsTest, AesGcmAadNoData) {
    ASSERT_EQ(ErrorCode::OK, GenerateKey(AuthorizationSetBuilder()
                                             .Authorization(TAG_NO_AUTH_REQUIRED)
                                             .AesEncryptionKey(128)
                                             .BlockMode(BlockMode::GCM)
                                             .Padding(PaddingMode::NONE)
                                             .Authorization(TAG_MIN_MAC_LENGTH, 128)));

    string aad = "1234567890123456";
    auto params = AuthorizationSetBuilder()
                      .BlockMode(BlockMode::GCM)
                      .Padding(PaddingMode::NONE)
                      .Authorization(TAG_MAC_LENGTH, 128);

    auto finish_params =
        AuthorizationSetBuilder().Authorization(TAG_ASSOCIATED_DATA, aad.data(), aad.size());

    // Encrypt
    AuthorizationSet begin_out_params;
    EXPECT_EQ(ErrorCode::OK, Begin(KeyPurpose::ENCRYPT, params, &begin_out_params));
    string ciphertext;
    AuthorizationSet finish_out_params;
    EXPECT_EQ(ErrorCode::OK, Finish(op_handle_, finish_params, "" /* input */, "" /* signature */,
                                    &finish_out_params, &ciphertext));
    EXPECT_TRUE(finish_out_params.empty());

    // Grab nonce
    params.push_back(begin_out_params);

    // Decrypt.
    EXPECT_EQ(ErrorCode::OK, Begin(KeyPurpose::DECRYPT, params));
    string plaintext;
    EXPECT_EQ(ErrorCode::OK, Finish(op_handle_, finish_params, ciphertext, "" /* signature */,
                                    &finish_out_params, &plaintext))
        << "(Possibly b/33615032)";

    EXPECT_TRUE(finish_out_params.empty());

    EXPECT_EQ("", plaintext);
}

/*
 * EncryptionOperationsTest.AesGcmMultiPartAad
 *
 * Verifies that AES GCM mode works when provided additional authenticated data in multiple chunks.
 */
TEST_F(EncryptionOperationsTest, AesGcmMultiPartAad) {
    ASSERT_EQ(ErrorCode::OK, GenerateKey(AuthorizationSetBuilder()
                                             .Authorization(TAG_NO_AUTH_REQUIRED)
                                             .AesEncryptionKey(128)
                                             .BlockMode(BlockMode::GCM)
                                             .Padding(PaddingMode::NONE)
                                             .Authorization(TAG_MIN_MAC_LENGTH, 128)));

    string message = "123456789012345678901234567890123456";
    auto begin_params = AuthorizationSetBuilder()
                            .BlockMode(BlockMode::GCM)
                            .Padding(PaddingMode::NONE)
                            .Authorization(TAG_MAC_LENGTH, 128);
    AuthorizationSet begin_out_params;

    auto update_params =
        AuthorizationSetBuilder().Authorization(TAG_ASSOCIATED_DATA, "foo", (size_t)3);

    EXPECT_EQ(ErrorCode::OK, Begin(KeyPurpose::ENCRYPT, begin_params, &begin_out_params));

    // No data, AAD only.
    string ciphertext;
    size_t input_consumed;
    AuthorizationSet update_out_params;
    EXPECT_EQ(ErrorCode::OK, Update(op_handle_, update_params, "" /* input */, &update_out_params,
                                    &ciphertext, &input_consumed));
    EXPECT_EQ(0U, input_consumed);
    EXPECT_EQ(0U, ciphertext.size());
    EXPECT_TRUE(update_out_params.empty());

    // AAD and data.
    EXPECT_EQ(ErrorCode::OK, Update(op_handle_, update_params, message, &update_out_params,
                                    &ciphertext, &input_consumed));
    EXPECT_EQ(message.size(), input_consumed);
    EXPECT_EQ(message.size(), ciphertext.size());
    EXPECT_TRUE(update_out_params.empty());

    EXPECT_EQ(ErrorCode::OK, Finish("" /* input */, &ciphertext));

    // Grab nonce.
    begin_params.push_back(begin_out_params);

    // Decrypt
    update_params =
        AuthorizationSetBuilder().Authorization(TAG_ASSOCIATED_DATA, "foofoo", (size_t)6);

    EXPECT_EQ(ErrorCode::OK, Begin(KeyPurpose::DECRYPT, begin_params));
    string plaintext;
    EXPECT_EQ(ErrorCode::OK, Finish(op_handle_, update_params, ciphertext, "" /* signature */,
                                    &update_out_params, &plaintext));
    EXPECT_TRUE(update_out_params.empty());
    EXPECT_EQ(message, plaintext);
}

/*
 * EncryptionOperationsTest.AesGcmAadOutOfOrder
 *
 * Verifies that AES GCM mode fails correctly when given AAD after data to encipher.
 */
TEST_F(EncryptionOperationsTest, AesGcmAadOutOfOrder) {
    ASSERT_EQ(ErrorCode::OK, GenerateKey(AuthorizationSetBuilder()
                                             .Authorization(TAG_NO_AUTH_REQUIRED)
                                             .AesEncryptionKey(128)
                                             .BlockMode(BlockMode::GCM)
                                             .Padding(PaddingMode::NONE)
                                             .Authorization(TAG_MIN_MAC_LENGTH, 128)));

    string message = "123456789012345678901234567890123456";
    auto begin_params = AuthorizationSetBuilder()
                            .BlockMode(BlockMode::GCM)
                            .Padding(PaddingMode::NONE)
                            .Authorization(TAG_MAC_LENGTH, 128);
    AuthorizationSet begin_out_params;

    auto update_params =
        AuthorizationSetBuilder().Authorization(TAG_ASSOCIATED_DATA, "foo", (size_t)3);

    EXPECT_EQ(ErrorCode::OK, Begin(KeyPurpose::ENCRYPT, begin_params, &begin_out_params));

    // No data, AAD only.
    string ciphertext;
    size_t input_consumed;
    AuthorizationSet update_out_params;
    EXPECT_EQ(ErrorCode::OK, Update(op_handle_, update_params, "" /* input */, &update_out_params,
                                    &ciphertext, &input_consumed));
    EXPECT_EQ(0U, input_consumed);
    EXPECT_EQ(0U, ciphertext.size());
    EXPECT_TRUE(update_out_params.empty());

    // AAD and data.
    EXPECT_EQ(ErrorCode::OK, Update(op_handle_, update_params, message, &update_out_params,
                                    &ciphertext, &input_consumed));
    EXPECT_EQ(message.size(), input_consumed);
    EXPECT_EQ(message.size(), ciphertext.size());
    EXPECT_TRUE(update_out_params.empty());

    // More AAD
    EXPECT_EQ(ErrorCode::INVALID_TAG, Update(op_handle_, update_params, "", &update_out_params,
                                             &ciphertext, &input_consumed));

    op_handle_ = kOpHandleSentinel;
}

/*
 * EncryptionOperationsTest.AesGcmBadAad
 *
 * Verifies that AES GCM decryption fails correctly when additional authenticated date is wrong.
 */
TEST_F(EncryptionOperationsTest, AesGcmBadAad) {
    ASSERT_EQ(ErrorCode::OK, GenerateKey(AuthorizationSetBuilder()
                                             .Authorization(TAG_NO_AUTH_REQUIRED)
                                             .AesEncryptionKey(128)
                                             .BlockMode(BlockMode::GCM)
                                             .Padding(PaddingMode::NONE)
                                             .Authorization(TAG_MIN_MAC_LENGTH, 128)));

    string message = "12345678901234567890123456789012";
    auto begin_params = AuthorizationSetBuilder()
                            .BlockMode(BlockMode::GCM)
                            .Padding(PaddingMode::NONE)
                            .Authorization(TAG_MAC_LENGTH, 128);

    auto finish_params =
        AuthorizationSetBuilder().Authorization(TAG_ASSOCIATED_DATA, "foobar", (size_t)6);

    // Encrypt
    AuthorizationSet begin_out_params;
    EXPECT_EQ(ErrorCode::OK, Begin(KeyPurpose::ENCRYPT, begin_params, &begin_out_params));
    string ciphertext;
    AuthorizationSet finish_out_params;
    EXPECT_EQ(ErrorCode::OK, Finish(op_handle_, finish_params, message, "" /* signature */,
                                    &finish_out_params, &ciphertext));

    // Grab nonce
    begin_params.push_back(begin_out_params);

    finish_params = AuthorizationSetBuilder().Authorization(TAG_ASSOCIATED_DATA,
                                                            "barfoo" /* Wrong AAD */, (size_t)6);

    // Decrypt.
    EXPECT_EQ(ErrorCode::OK, Begin(KeyPurpose::DECRYPT, begin_params, &begin_out_params));
    string plaintext;
    EXPECT_EQ(ErrorCode::VERIFICATION_FAILED,
              Finish(op_handle_, finish_params, ciphertext, "" /* signature */, &finish_out_params,
                     &plaintext));
}

/*
 * EncryptionOperationsTest.AesGcmWrongNonce
 *
 * Verifies that AES GCM decryption fails correctly when the nonce is incorrect.
 */
TEST_F(EncryptionOperationsTest, AesGcmWrongNonce) {
    ASSERT_EQ(ErrorCode::OK, GenerateKey(AuthorizationSetBuilder()
                                             .Authorization(TAG_NO_AUTH_REQUIRED)
                                             .AesEncryptionKey(128)
                                             .BlockMode(BlockMode::GCM)
                                             .Padding(PaddingMode::NONE)
                                             .Authorization(TAG_MIN_MAC_LENGTH, 128)));

    string message = "12345678901234567890123456789012";
    auto begin_params = AuthorizationSetBuilder()
                            .BlockMode(BlockMode::GCM)
                            .Padding(PaddingMode::NONE)
                            .Authorization(TAG_MAC_LENGTH, 128);

    auto finish_params =
        AuthorizationSetBuilder().Authorization(TAG_ASSOCIATED_DATA, "foobar", (size_t)6);

    // Encrypt
    AuthorizationSet begin_out_params;
    EXPECT_EQ(ErrorCode::OK, Begin(KeyPurpose::ENCRYPT, begin_params, &begin_out_params));
    string ciphertext;
    AuthorizationSet finish_out_params;
    EXPECT_EQ(ErrorCode::OK, Finish(op_handle_, finish_params, message, "" /* signature */,
                                    &finish_out_params, &ciphertext));

    // Wrong nonce
    begin_params.push_back(TAG_NONCE, HidlBuf("123456789012"));

    // Decrypt.
    EXPECT_EQ(ErrorCode::OK, Begin(KeyPurpose::DECRYPT, begin_params, &begin_out_params));
    string plaintext;
    EXPECT_EQ(ErrorCode::VERIFICATION_FAILED,
              Finish(op_handle_, finish_params, ciphertext, "" /* signature */, &finish_out_params,
                     &plaintext));

    // With wrong nonce, should have gotten garbage plaintext (or none).
    EXPECT_NE(message, plaintext);
}

/*
 * EncryptionOperationsTest.AesGcmCorruptTag
 *
 * Verifies that AES GCM decryption fails correctly when the tag is wrong.
 */
TEST_F(EncryptionOperationsTest, AesGcmCorruptTag) {
    ASSERT_EQ(ErrorCode::OK, GenerateKey(AuthorizationSetBuilder()
                                             .Authorization(TAG_NO_AUTH_REQUIRED)
                                             .AesEncryptionKey(128)
                                             .BlockMode(BlockMode::GCM)
                                             .Padding(PaddingMode::NONE)
                                             .Authorization(TAG_MIN_MAC_LENGTH, 128)));

    string aad = "1234567890123456";
    string message = "123456789012345678901234567890123456";

    auto params = AuthorizationSetBuilder()
                      .BlockMode(BlockMode::GCM)
                      .Padding(PaddingMode::NONE)
                      .Authorization(TAG_MAC_LENGTH, 128);

    auto finish_params =
        AuthorizationSetBuilder().Authorization(TAG_ASSOCIATED_DATA, aad.data(), aad.size());

    // Encrypt
    AuthorizationSet begin_out_params;
    EXPECT_EQ(ErrorCode::OK, Begin(KeyPurpose::ENCRYPT, params, &begin_out_params));
    string ciphertext;
    AuthorizationSet finish_out_params;
    EXPECT_EQ(ErrorCode::OK, Finish(op_handle_, finish_params, message, "" /* signature */,
                                    &finish_out_params, &ciphertext));
    EXPECT_TRUE(finish_out_params.empty());

    // Corrupt tag
    ++(*ciphertext.rbegin());

    // Grab nonce
    params.push_back(begin_out_params);

    // Decrypt.
    EXPECT_EQ(ErrorCode::OK, Begin(KeyPurpose::DECRYPT, params));
    string plaintext;
    EXPECT_EQ(ErrorCode::VERIFICATION_FAILED,
              Finish(op_handle_, finish_params, ciphertext, "" /* signature */, &finish_out_params,
                     &plaintext));
    EXPECT_TRUE(finish_out_params.empty());
}

typedef KeymasterHidlTest MaxOperationsTest;

/*
 * MaxOperationsTest.TestLimitAes
 *
 * Verifies that the max uses per boot tag works correctly with AES keys.
 */
TEST_F(MaxOperationsTest, TestLimitAes) {
    ASSERT_EQ(ErrorCode::OK, GenerateKey(AuthorizationSetBuilder()
                                             .Authorization(TAG_NO_AUTH_REQUIRED)
                                             .AesEncryptionKey(128)
                                             .EcbMode()
                                             .Padding(PaddingMode::NONE)
                                             .Authorization(TAG_MAX_USES_PER_BOOT, 3)));

    string message = "1234567890123456";

    auto params = AuthorizationSetBuilder().EcbMode().Padding(PaddingMode::NONE);

    EncryptMessage(message, params);
    EncryptMessage(message, params);
    EncryptMessage(message, params);

    // Fourth time should fail.
    EXPECT_EQ(ErrorCode::KEY_MAX_OPS_EXCEEDED, Begin(KeyPurpose::ENCRYPT, params));
}

/*
 * MaxOperationsTest.TestLimitAes
 *
 * Verifies that the max uses per boot tag works correctly with RSA keys.
 */
TEST_F(MaxOperationsTest, TestLimitRsa) {
    ASSERT_EQ(ErrorCode::OK, GenerateKey(AuthorizationSetBuilder()
                                             .Authorization(TAG_NO_AUTH_REQUIRED)
                                             .RsaSigningKey(1024, 3)
                                             .NoDigestOrPadding()
                                             .Authorization(TAG_MAX_USES_PER_BOOT, 3)));

    string message = "1234567890123456";

    auto params = AuthorizationSetBuilder().NoDigestOrPadding();

    SignMessage(message, params);
    SignMessage(message, params);
    SignMessage(message, params);

    // Fourth time should fail.
    EXPECT_EQ(ErrorCode::KEY_MAX_OPS_EXCEEDED, Begin(KeyPurpose::SIGN, params));
}

typedef KeymasterHidlTest AddEntropyTest;

/*
 * AddEntropyTest.AddEntropy
 *
 * Verifies that the addRngEntropy method doesn't blow up.  There's no way to test that entropy is
 * actually added.
 */
TEST_F(AddEntropyTest, AddEntropy) {
    EXPECT_EQ(ErrorCode::OK, keymaster().addRngEntropy(HidlBuf("foo")));
}

/*
 * AddEntropyTest.AddEmptyEntropy
 *
 * Verifies that the addRngEntropy method doesn't blow up when given an empty buffer.
 */
TEST_F(AddEntropyTest, AddEmptyEntropy) {
    EXPECT_EQ(ErrorCode::OK, keymaster().addRngEntropy(HidlBuf()));
}

/*
 * AddEntropyTest.AddLargeEntropy
 *
 * Verifies that the addRngEntropy method doesn't blow up when given a largish amount of data.
 */
TEST_F(AddEntropyTest, AddLargeEntropy) {
    EXPECT_EQ(ErrorCode::OK, keymaster().addRngEntropy(HidlBuf(string(2 * 1024, 'a'))));
}

typedef KeymasterHidlTest AttestationTest;

/*
 * AttestationTest.RsaAttestation
 *
 * Verifies that attesting to RSA keys works and generates the expected output.
 */
TEST_F(AttestationTest, RsaAttestation) {
    ASSERT_EQ(ErrorCode::OK, GenerateKey(AuthorizationSetBuilder()
                                             .Authorization(TAG_NO_AUTH_REQUIRED)
                                             .RsaSigningKey(1024, 3)
                                             .Digest(Digest::NONE)
                                             .Padding(PaddingMode::NONE)
                                             .Authorization(TAG_INCLUDE_UNIQUE_ID)));

    hidl_vec<hidl_vec<uint8_t>> cert_chain;
    ASSERT_EQ(ErrorCode::OK,
              AttestKey(AuthorizationSetBuilder()
                            .Authorization(TAG_ATTESTATION_CHALLENGE, HidlBuf("challenge"))
                            .Authorization(TAG_ATTESTATION_APPLICATION_ID, HidlBuf("foo")),
                        &cert_chain));
    EXPECT_GE(cert_chain.size(), 2U);
    EXPECT_TRUE(verify_chain(cert_chain));
    EXPECT_TRUE(
        verify_attestation_record("challenge", "foo",                     //
                                  key_characteristics_.softwareEnforced,  //
                                  key_characteristics_.teeEnforced,       //
                                  cert_chain[0]));
}

/*
 * AttestationTest.RsaAttestationRequiresAppId
 *
 * Verifies that attesting to RSA requires app ID.
 */
TEST_F(AttestationTest, RsaAttestationRequiresAppId) {
    ASSERT_EQ(ErrorCode::OK,
              GenerateKey(AuthorizationSetBuilder()
                              .Authorization(TAG_NO_AUTH_REQUIRED)
                              .RsaSigningKey(1024, 3)
                              .Digest(Digest::NONE)
                              .Padding(PaddingMode::NONE)
                              .Authorization(TAG_INCLUDE_UNIQUE_ID)));

    hidl_vec<hidl_vec<uint8_t>> cert_chain;
    EXPECT_EQ(ErrorCode::ATTESTATION_APPLICATION_ID_MISSING,
              AttestKey(AuthorizationSetBuilder().Authorization(
                            TAG_ATTESTATION_CHALLENGE, HidlBuf("challenge")),
                        &cert_chain));
}

/*
 * AttestationTest.EcAttestation
 *
 * Verifies that attesting to EC keys works and generates the expected output.
 */
TEST_F(AttestationTest, EcAttestation) {
    ASSERT_EQ(ErrorCode::OK, GenerateKey(AuthorizationSetBuilder()
                                             .Authorization(TAG_NO_AUTH_REQUIRED)
                                             .EcdsaSigningKey(EcCurve::P_256)
                                             .Digest(Digest::SHA_2_256)
                                             .Authorization(TAG_INCLUDE_UNIQUE_ID)));

    hidl_vec<hidl_vec<uint8_t>> cert_chain;
    ASSERT_EQ(ErrorCode::OK,
              AttestKey(AuthorizationSetBuilder()
                            .Authorization(TAG_ATTESTATION_CHALLENGE, HidlBuf("challenge"))
                            .Authorization(TAG_ATTESTATION_APPLICATION_ID, HidlBuf("foo")),
                        &cert_chain));
    EXPECT_GE(cert_chain.size(), 2U);
    EXPECT_TRUE(verify_chain(cert_chain));

    EXPECT_TRUE(
        verify_attestation_record("challenge", "foo",                     //
                                  key_characteristics_.softwareEnforced,  //
                                  key_characteristics_.teeEnforced,       //
                                  cert_chain[0]));
}

/*
 * AttestationTest.EcAttestationRequiresAttestationAppId
 *
 * Verifies that attesting to EC keys requires app ID
 */
TEST_F(AttestationTest, EcAttestationRequiresAttestationAppId) {
    ASSERT_EQ(ErrorCode::OK,
              GenerateKey(AuthorizationSetBuilder()
                              .Authorization(TAG_NO_AUTH_REQUIRED)
                              .EcdsaSigningKey(EcCurve::P_256)
                              .Digest(Digest::SHA_2_256)
                              .Authorization(TAG_INCLUDE_UNIQUE_ID)));

    hidl_vec<hidl_vec<uint8_t>> cert_chain;
    EXPECT_EQ(ErrorCode::ATTESTATION_APPLICATION_ID_MISSING,
              AttestKey(AuthorizationSetBuilder().Authorization(
                            TAG_ATTESTATION_CHALLENGE, HidlBuf("challenge")),
                        &cert_chain));
}

/*
 * AttestationTest.AesAttestation
 *
 * Verifies that attesting to AES keys fails in the expected way.
 */
TEST_F(AttestationTest, AesAttestation) {
    ASSERT_EQ(ErrorCode::OK,
              GenerateKey(AuthorizationSetBuilder()
                              .Authorization(TAG_NO_AUTH_REQUIRED)
                              .AesEncryptionKey(128)
                              .EcbMode()
                              .Padding(PaddingMode::PKCS7)));

    hidl_vec<hidl_vec<uint8_t>> cert_chain;
    EXPECT_EQ(
        ErrorCode::INCOMPATIBLE_ALGORITHM,
        AttestKey(
            AuthorizationSetBuilder()
                .Authorization(TAG_ATTESTATION_CHALLENGE, HidlBuf("challenge"))
                .Authorization(TAG_ATTESTATION_APPLICATION_ID, HidlBuf("foo")),
            &cert_chain));
}

/*
 * AttestationTest.HmacAttestation
 *
 * Verifies that attesting to HMAC keys fails in the expected way.
 */
TEST_F(AttestationTest, HmacAttestation) {
    ASSERT_EQ(ErrorCode::OK,
              GenerateKey(AuthorizationSetBuilder()
                              .Authorization(TAG_NO_AUTH_REQUIRED)
                              .HmacKey(128)
                              .EcbMode()
                              .Digest(Digest::SHA_2_256)
                              .Authorization(TAG_MIN_MAC_LENGTH, 128)));

    hidl_vec<hidl_vec<uint8_t>> cert_chain;
    EXPECT_EQ(
        ErrorCode::INCOMPATIBLE_ALGORITHM,
        AttestKey(
            AuthorizationSetBuilder()
                .Authorization(TAG_ATTESTATION_CHALLENGE, HidlBuf("challenge"))
                .Authorization(TAG_ATTESTATION_APPLICATION_ID, HidlBuf("foo")),
            &cert_chain));
}

typedef KeymasterHidlTest KeyDeletionTest;

/**
 * KeyDeletionTest.DeleteKey
 *
 * This test checks that if rollback protection is implemented, DeleteKey invalidates a formerly
 * valid key blob.
 */
TEST_F(KeyDeletionTest, DeleteKey) {
    ASSERT_EQ(ErrorCode::OK, GenerateKey(AuthorizationSetBuilder()
                                             .RsaSigningKey(1024, 3)
                                             .Digest(Digest::NONE)
                                             .Padding(PaddingMode::NONE)
                                             .Authorization(TAG_NO_AUTH_REQUIRED)));

    // Delete must work if rollback protection is implemented
    AuthorizationSet teeEnforced(key_characteristics_.teeEnforced);
    bool rollback_protected = teeEnforced.Contains(TAG_ROLLBACK_RESISTANT);

    if (rollback_protected) {
        ASSERT_EQ(ErrorCode::OK, DeleteKey(true /* keep key blob */));
    } else {
        auto delete_result = DeleteKey(true /* keep key blob */);
        ASSERT_TRUE(delete_result == ErrorCode::OK | delete_result == ErrorCode::UNIMPLEMENTED);
    }

    string message = "12345678901234567890123456789012";
    AuthorizationSet begin_out_params;

    if (rollback_protected) {
        EXPECT_EQ(
            ErrorCode::INVALID_KEY_BLOB,
            Begin(KeyPurpose::SIGN, key_blob_, AuthorizationSetBuilder()
                                                   .Digest(Digest::NONE)
                                                   .Padding(PaddingMode::NONE),
                  &begin_out_params, &op_handle_))
            << " (Possibly b/37623742)";
    } else {
        EXPECT_EQ(ErrorCode::OK, Begin(KeyPurpose::SIGN, key_blob_,
                                       AuthorizationSetBuilder()
                                           .Digest(Digest::NONE)
                                           .Padding(PaddingMode::NONE),
                                       &begin_out_params, &op_handle_));
    }
    AbortIfNeeded();
    key_blob_ = HidlBuf();
}

/**
 * KeyDeletionTest.DeleteInvalidKey
 *
 * This test checks that the HAL excepts invalid key blobs.
 */
TEST_F(KeyDeletionTest, DeleteInvalidKey) {
    // Generate key just to check if rollback protection is implemented
    ASSERT_EQ(ErrorCode::OK, GenerateKey(AuthorizationSetBuilder()
                                             .RsaSigningKey(1024, 3)
                                             .Digest(Digest::NONE)
                                             .Padding(PaddingMode::NONE)
                                             .Authorization(TAG_NO_AUTH_REQUIRED)));

    // Delete must work if rollback protection is implemented
    AuthorizationSet teeEnforced(key_characteristics_.teeEnforced);
    bool rollback_protected = teeEnforced.Contains(TAG_ROLLBACK_RESISTANT);

    // Delete the key we don't care about the result at this point.
    DeleteKey();

    // Now create an invalid key blob and delete it.
    key_blob_ = HidlBuf("just some garbage data which is not a valid key blob");

    if (rollback_protected) {
        ASSERT_EQ(ErrorCode::OK, DeleteKey());
    } else {
        auto delete_result = DeleteKey();
        ASSERT_TRUE(delete_result == ErrorCode::OK | delete_result == ErrorCode::UNIMPLEMENTED);
    }
}

/**
 * KeyDeletionTest.DeleteAllKeys
 *
 * This test is disarmed by default. To arm it use --arm_deleteAllKeys.
 *
 * BEWARE: This test has serious side effects. All user keys will be lost! This includes
 * FBE/FDE encryption keys, which means that the device will not even boot until after the
 * device has been wiped manually (e.g., fastboot flashall -w), and new FBE/FDE keys have
 * been provisioned. Use this test only on dedicated testing devices that have no valuable
 * credentials stored in Keystore/Keymaster.
 */
TEST_F(KeyDeletionTest, DeleteAllKeys) {
    if (!arm_deleteAllKeys) return;
    ASSERT_EQ(ErrorCode::OK, GenerateKey(AuthorizationSetBuilder()
                                             .RsaSigningKey(1024, 3)
                                             .Digest(Digest::NONE)
                                             .Padding(PaddingMode::NONE)
                                             .Authorization(TAG_NO_AUTH_REQUIRED)));

    // Delete must work if rollback protection is implemented
    AuthorizationSet teeEnforced(key_characteristics_.teeEnforced);
    bool rollback_protected = teeEnforced.Contains(TAG_ROLLBACK_RESISTANT);

    ASSERT_EQ(ErrorCode::OK, DeleteAllKeys());

    string message = "12345678901234567890123456789012";
    AuthorizationSet begin_out_params;

    if (rollback_protected) {
        EXPECT_EQ(
            ErrorCode::INVALID_KEY_BLOB,
            Begin(KeyPurpose::SIGN, key_blob_, AuthorizationSetBuilder()
                                                   .Digest(Digest::NONE)
                                                   .Padding(PaddingMode::NONE),
                  &begin_out_params, &op_handle_));
    } else {
        EXPECT_EQ(ErrorCode::OK, Begin(KeyPurpose::SIGN, key_blob_,
                                       AuthorizationSetBuilder()
                                           .Digest(Digest::NONE)
                                           .Padding(PaddingMode::NONE),
                                       &begin_out_params, &op_handle_));
    }
    AbortIfNeeded();
    key_blob_ = HidlBuf();
}

}  // namespace test
}  // namespace V3_0
}  // namespace keymaster
}  // namespace hardware
}  // namespace android

int main(int argc, char** argv) {
    ::testing::InitGoogleTest(&argc, argv);
    std::vector<std::string> positional_args;
    for (int i = 1; i < argc; ++i) {
        if (argv[i][0] == '-') {
            if (std::string(argv[i]) == "--arm_deleteAllKeys") {
                arm_deleteAllKeys = true;
            }
            if (std::string(argv[i]) == "--dump_attestations") {
                dump_Attestations = true;
            }
        } else {
            positional_args.push_back(argv[i]);
        }
    }
    if (positional_args.size()) {
        ALOGI("Running keymaster VTS against service \"%s\"", positional_args[0].c_str());
        service_name = positional_args[0];
    }
    int status = RUN_ALL_TESTS();
    ALOGI("Test result = %d", status);
    return status;
}
