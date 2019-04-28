/*
 * Copyright (C) 2017 The Android Open Source Project
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

#define LOG_TAG "drm_hal_clearkey_test@1.0"

#include <android/hardware/drm/1.0/ICryptoFactory.h>
#include <android/hardware/drm/1.0/ICryptoPlugin.h>
#include <android/hardware/drm/1.0/IDrmFactory.h>
#include <android/hardware/drm/1.0/IDrmPlugin.h>
#include <android/hardware/drm/1.0/types.h>
#include <android/hidl/allocator/1.0/IAllocator.h>
#include <gtest/gtest.h>
#include <hidl/HidlSupport.h>
#include <hidlmemory/mapping.h>
#include <log/log.h>
#include <memory>
#include <openssl/aes.h>
#include <random>

#include "VtsHalHidlTargetTestBase.h"

using ::android::hardware::drm::V1_0::BufferType;
using ::android::hardware::drm::V1_0::DestinationBuffer;
using ::android::hardware::drm::V1_0::ICryptoFactory;
using ::android::hardware::drm::V1_0::ICryptoPlugin;
using ::android::hardware::drm::V1_0::IDrmFactory;
using ::android::hardware::drm::V1_0::IDrmPlugin;
using ::android::hardware::drm::V1_0::KeyedVector;
using ::android::hardware::drm::V1_0::KeyValue;
using ::android::hardware::drm::V1_0::KeyRequestType;
using ::android::hardware::drm::V1_0::KeyType;
using ::android::hardware::drm::V1_0::Mode;
using ::android::hardware::drm::V1_0::Pattern;
using ::android::hardware::drm::V1_0::SecureStop;
using ::android::hardware::drm::V1_0::SecureStopId;
using ::android::hardware::drm::V1_0::SessionId;
using ::android::hardware::drm::V1_0::SharedBuffer;
using ::android::hardware::drm::V1_0::Status;
using ::android::hardware::drm::V1_0::SubSample;

using ::android::hardware::hidl_array;
using ::android::hardware::hidl_string;
using ::android::hardware::hidl_memory;
using ::android::hardware::hidl_vec;
using ::android::hardware::Return;
using ::android::hidl::allocator::V1_0::IAllocator;
using ::android::hidl::memory::V1_0::IMemory;
using ::android::sp;

using std::string;
using std::unique_ptr;
using std::random_device;
using std::map;
using std::mt19937;
using std::vector;

/**
 * These clearkey tests use white box knowledge of the legacy clearkey
 * plugin to verify that the HIDL HAL services and interfaces are working.
 * It is not intended to verify any vendor's HAL implementation. If you
 * are looking for vendor HAL tests, see drm_hal_vendor_test.cpp
 */
#define ASSERT_OK(ret) ASSERT_TRUE(ret.isOk())
#define EXPECT_OK(ret) EXPECT_TRUE(ret.isOk())

static const uint8_t kCommonPsshBoxUUID[16] = {
    0x10, 0x77, 0xEF, 0xEC, 0xC0, 0xB2, 0x4D, 0x02,
    0xAC, 0xE3, 0x3C, 0x1E, 0x52, 0xE2, 0xFB, 0x4B};

// To be used in mpd to specify drm scheme for players
static const uint8_t kClearKeyUUID[16] = {
    0xE2, 0x71, 0x9D, 0x58, 0xA9, 0x85, 0xB3, 0xC9,
    0x78, 0x1A, 0xB0, 0x30, 0xAF, 0x78, 0xD3, 0x0E};

static const uint8_t kInvalidUUID[16] = {
    0x10, 0x20, 0x30, 0x40, 0x50, 0x60, 0x70, 0x80,
    0x10, 0x20, 0x30, 0x40, 0x50, 0x60, 0x70, 0x80};

static const uint32_t k256SubSampleByteCount = 256;
static const uint32_t k512SubSampleClearBytes = 512;
static const uint32_t k512SubSampleEncryptedBytes = 512;

class DrmHalClearkeyFactoryTest : public ::testing::VtsHalHidlTargetTestBase {
   public:
    virtual void SetUp() override {
        const ::testing::TestInfo* const test_info =
                ::testing::UnitTest::GetInstance()->current_test_info();
        ALOGD("Running test %s.%s", test_info->test_case_name(),
              test_info->name());

        drmFactory =
                ::testing::VtsHalHidlTargetTestBase::getService<IDrmFactory>();
        ASSERT_NE(nullptr, drmFactory.get());
        cryptoFactory =
                ::testing::VtsHalHidlTargetTestBase::getService<ICryptoFactory>();
        ASSERT_NE(nullptr, cryptoFactory.get());
    }

    virtual void TearDown() override {}

   protected:
    sp<IDrmFactory> drmFactory;
    sp<ICryptoFactory> cryptoFactory;
};

/**
 * Ensure the factory supports both Common Pssh Box UUID and Clearkey Scheme UUID
 */
TEST_F(DrmHalClearkeyFactoryTest, ClearKeyPluginSupported) {
    EXPECT_TRUE(drmFactory->isCryptoSchemeSupported(kCommonPsshBoxUUID));
    EXPECT_TRUE(cryptoFactory->isCryptoSchemeSupported(kCommonPsshBoxUUID));

    EXPECT_TRUE(drmFactory->isCryptoSchemeSupported(kClearKeyUUID));
    EXPECT_TRUE(cryptoFactory->isCryptoSchemeSupported(kClearKeyUUID));
}

/**
 * Ensure the factory doesn't support an invalid scheme UUID
 */
TEST_F(DrmHalClearkeyFactoryTest, InvalidPluginNotSupported) {
    EXPECT_FALSE(drmFactory->isCryptoSchemeSupported(kInvalidUUID));
    EXPECT_FALSE(cryptoFactory->isCryptoSchemeSupported(kInvalidUUID));
}

/**
 * Ensure the factory doesn't support an empty UUID
 */
TEST_F(DrmHalClearkeyFactoryTest, EmptyPluginUUIDNotSupported) {
    hidl_array<uint8_t, 16> emptyUUID;
    memset(emptyUUID.data(), 0, 16);
    EXPECT_FALSE(drmFactory->isCryptoSchemeSupported(emptyUUID));
    EXPECT_FALSE(cryptoFactory->isCryptoSchemeSupported(emptyUUID));
}

/**
 * Ensure empty content type is not supported
 */
TEST_F(DrmHalClearkeyFactoryTest, EmptyContentTypeNotSupported) {
    hidl_string empty;
    EXPECT_FALSE(drmFactory->isContentTypeSupported(empty));
}

/**
 * Ensure invalid content type is not supported
 */
TEST_F(DrmHalClearkeyFactoryTest, InvalidContentTypeNotSupported) {
    hidl_string invalid("abcdabcd");
    EXPECT_FALSE(drmFactory->isContentTypeSupported(invalid));
}

/**
 * Ensure valid content type is supported
 */
TEST_F(DrmHalClearkeyFactoryTest, ValidContentTypeSupported) {
    hidl_string cencType("cenc");
    EXPECT_TRUE(drmFactory->isContentTypeSupported(cencType));
}

/**
 * Ensure clearkey drm plugin can be created using Common Pssh Box UUID
 */
TEST_F(DrmHalClearkeyFactoryTest, CreateClearKeyDrmPluginUsingCommonPsshBoxUuid) {
    hidl_string packageName("android.hardware.drm.test");
    auto res = drmFactory->createPlugin(
            kCommonPsshBoxUUID, packageName,
            [&](Status status, const sp<IDrmPlugin>& plugin) {
                EXPECT_EQ(Status::OK, status);
                EXPECT_NE(nullptr, plugin.get());
            });
    EXPECT_OK(res);
}

/**
 * Ensure clearkey drm plugin can be created using ClearKey UUID
 */
 TEST_F(DrmHalClearkeyFactoryTest, CreateClearKeyDrmPluginUsingClearKeyUuid) {
    hidl_string packageName("android.hardware.drm.test");
    auto res = drmFactory->createPlugin(
            kClearKeyUUID, packageName,
            [&](Status status, const sp<IDrmPlugin>& plugin) {
                EXPECT_EQ(Status::OK, status);
                EXPECT_NE(nullptr, plugin.get());
            });
    EXPECT_OK(res);
}

/**
 * Ensure clearkey crypto plugin can be created using Common Pssh Box UUID
 */
TEST_F(DrmHalClearkeyFactoryTest, CreateClearKeyCryptoPluginUsingCommonPsshBoxUuid) {
    hidl_vec<uint8_t> initVec;
    auto res = cryptoFactory->createPlugin(
            kCommonPsshBoxUUID, initVec,
            [&](Status status, const sp<ICryptoPlugin>& plugin) {
                EXPECT_EQ(Status::OK, status);
                EXPECT_NE(nullptr, plugin.get());
            });
    EXPECT_OK(res);
}

/**
 * Ensure clearkey crypto plugin can be created using ClearKey UUID
 */
TEST_F(DrmHalClearkeyFactoryTest, CreateClearKeyCryptoPluginUsingClearKeyUuid) {
    hidl_vec<uint8_t> initVec;
    auto res = cryptoFactory->createPlugin(
            kClearKeyUUID, initVec,
            [&](Status status, const sp<ICryptoPlugin>& plugin) {
                EXPECT_EQ(Status::OK, status);
                EXPECT_NE(nullptr, plugin.get());
            });
    EXPECT_OK(res);
}

/**
 * Ensure invalid drm plugin can't be created
 */
TEST_F(DrmHalClearkeyFactoryTest, CreateInvalidDrmPlugin) {
    hidl_string packageName("android.hardware.drm.test");
    auto res = drmFactory->createPlugin(
            kInvalidUUID, packageName,
            [&](Status status, const sp<IDrmPlugin>& plugin) {
                EXPECT_EQ(Status::ERROR_DRM_CANNOT_HANDLE, status);
                EXPECT_EQ(nullptr, plugin.get());
            });
    EXPECT_OK(res);
}

/**
 * Ensure invalid crypto plugin can't be created
 */
TEST_F(DrmHalClearkeyFactoryTest, CreateInvalidCryptoPlugin) {
    hidl_vec<uint8_t> initVec;
    auto res = cryptoFactory->createPlugin(
            kInvalidUUID, initVec,
            [&](Status status, const sp<ICryptoPlugin>& plugin) {
                EXPECT_EQ(Status::ERROR_DRM_CANNOT_HANDLE, status);
                EXPECT_EQ(nullptr, plugin.get());
            });
    EXPECT_OK(res);
}

class DrmHalClearkeyPluginTest : public DrmHalClearkeyFactoryTest {
   public:
    virtual void SetUp() override {
        // Create factories
        DrmHalClearkeyFactoryTest::SetUp();

        ASSERT_NE(nullptr, drmFactory.get());
        hidl_string packageName("android.hardware.drm.test");
        auto res = drmFactory->createPlugin(
                kClearKeyUUID, packageName,
                [this](Status status, const sp<IDrmPlugin>& plugin) {
                    EXPECT_EQ(Status::OK, status);
                    ASSERT_NE(nullptr, plugin.get());
                    drmPlugin = plugin;
                });
        ASSERT_OK(res);

        hidl_vec<uint8_t> initVec;
        res = cryptoFactory->createPlugin(
                kClearKeyUUID, initVec,
                [this](Status status, const sp<ICryptoPlugin>& plugin) {
                    EXPECT_EQ(Status::OK, status);
                    ASSERT_NE(nullptr, plugin.get());
                    cryptoPlugin = plugin;
                });
        ASSERT_OK(res);
    }

    virtual void TearDown() override {}

    SessionId openSession();
    void closeSession(const SessionId& sessionId);
    hidl_vec<uint8_t> loadKeys(const SessionId& sessionId, const KeyType& type);
    sp<IMemory> getDecryptMemory(size_t size, size_t index);

   protected:
    sp<IDrmPlugin> drmPlugin;
    sp<ICryptoPlugin> cryptoPlugin;
};

/**
 *  DrmPlugin tests
 */

/**
 * Test that the plugin can return a provision request.  Since
 * the clearkey plugin doesn't support provisioning, it is
 * expected to return Status::ERROR_DRM_CANNOT_HANDLE.
 */
TEST_F(DrmHalClearkeyPluginTest, GetProvisionRequest) {
    hidl_string certificateType;
    hidl_string certificateAuthority;
    auto res = drmPlugin->getProvisionRequest(
            certificateType, certificateAuthority,
            [&](Status status, const hidl_vec<uint8_t>&, const hidl_string&) {
                // clearkey doesn't require provisioning
                EXPECT_EQ(Status::ERROR_DRM_CANNOT_HANDLE, status);
            });
    EXPECT_OK(res);
}

/**
 * The DRM HAL should return BAD_VALUE if an empty provisioning
 * response is provided.
 */
TEST_F(DrmHalClearkeyPluginTest, ProvideEmptyProvisionResponse) {
    hidl_vec<uint8_t> response;
    auto res = drmPlugin->provideProvisionResponse(
            response, [&](Status status, const hidl_vec<uint8_t>&,
                          const hidl_vec<uint8_t>&) {
                EXPECT_EQ(Status::BAD_VALUE, status);
            });
    EXPECT_OK(res);
}

/**
 * Helper method to open a session and verify that a non-empty
 * session ID is returned
 */
SessionId DrmHalClearkeyPluginTest::openSession() {
    SessionId sessionId;

    auto res = drmPlugin->openSession(
            [&sessionId](Status status, const SessionId& id) {
                EXPECT_EQ(Status::OK, status);
                EXPECT_NE(0u, id.size());
                sessionId = id;
            });
    EXPECT_OK(res);
    return sessionId;
}

/**
 * Helper method to close a session
 */
void DrmHalClearkeyPluginTest::closeSession(const SessionId& sessionId) {
    auto result = drmPlugin->closeSession(sessionId);
    EXPECT_EQ(Status::OK, result);
}

/**
 * Helper method to load keys for subsequent decrypt tests.
 * These tests use predetermined key request/response to
 * avoid requiring a round trip to a license server.
 */
hidl_vec<uint8_t> DrmHalClearkeyPluginTest::loadKeys(
    const SessionId& sessionId, const KeyType& type = KeyType::STREAMING) {
    hidl_vec<uint8_t> initData = {
        // BMFF box header (4 bytes size + 'pssh')
        0x00, 0x00, 0x00, 0x34, 0x70, 0x73, 0x73, 0x68,
        // full box header (version = 1 flags = 0)
        0x01, 0x00, 0x00, 0x00,
        // system id
        0x10, 0x77, 0xef, 0xec, 0xc0, 0xb2, 0x4d, 0x02, 0xac, 0xe3, 0x3c,
        0x1e, 0x52, 0xe2, 0xfb, 0x4b,
        // number of key ids
        0x00, 0x00, 0x00, 0x01,
        // key id
        0x60, 0x06, 0x1e, 0x01, 0x7e, 0x47, 0x7e, 0x87, 0x7e, 0x57, 0xd0,
        0x0d, 0x1e, 0xd0, 0x0d, 0x1e,
        // size of data, must be zero
        0x00, 0x00, 0x00, 0x00};

    hidl_vec<uint8_t> expectedKeyRequest = {
        0x7b, 0x22, 0x6b, 0x69, 0x64, 0x73, 0x22, 0x3a, 0x5b, 0x22, 0x59, 0x41, 0x59, 0x65,
        0x41, 0x58, 0x35, 0x48, 0x66, 0x6f, 0x64, 0x2d, 0x56, 0x39, 0x41, 0x4e, 0x48, 0x74,
        0x41, 0x4e, 0x48, 0x67, 0x22, 0x5d, 0x2c, 0x22, 0x74, 0x79, 0x70, 0x65, 0x22, 0x3a,
        0x22, 0x74, 0x65, 0x6d, 0x70, 0x6f, 0x72, 0x61, 0x72, 0x79, 0x22, 0x7d};

    hidl_vec<uint8_t> knownKeyResponse = {
        0x7b, 0x22, 0x6b, 0x65, 0x79, 0x73, 0x22, 0x3a, 0x5b, 0x7b, 0x22, 0x6b, 0x74, 0x79, 0x22,
        0x3a, 0x22, 0x6f, 0x63, 0x74, 0x22, 0x2c, 0x22, 0x6b, 0x69, 0x64, 0x22, 0x3a, 0x22, 0x59,
        0x41, 0x59, 0x65, 0x41, 0x58, 0x35, 0x48, 0x66, 0x6f, 0x64, 0x2d, 0x56, 0x39, 0x41, 0x4e,
        0x48, 0x74, 0x41, 0x4e, 0x48, 0x67, 0x22, 0x2c, 0x22, 0x6b, 0x22, 0x3a, 0x22, 0x47, 0x6f,
        0x6f, 0x67, 0x6c, 0x65, 0x54, 0x65, 0x73, 0x74, 0x4b, 0x65, 0x79, 0x42, 0x61, 0x73, 0x65,
        0x36, 0x34, 0x67, 0x67, 0x67, 0x22, 0x7d, 0x5d, 0x7d, 0x0a};

    hidl_string mimeType = "video/mp4";
    KeyedVector optionalParameters;
    auto res = drmPlugin->getKeyRequest(
        sessionId, initData, mimeType, type, optionalParameters,
        [&](Status status, const hidl_vec<uint8_t>& request,
            KeyRequestType requestType, const hidl_string&) {
            EXPECT_EQ(Status::OK, status);
            EXPECT_EQ(KeyRequestType::INITIAL, requestType);
            EXPECT_EQ(request, expectedKeyRequest);
        });
    EXPECT_OK(res);

    hidl_vec<uint8_t> keySetId;
    res = drmPlugin->provideKeyResponse(
        sessionId, knownKeyResponse,
        [&](Status status, const hidl_vec<uint8_t>& myKeySetId) {
            EXPECT_EQ(Status::OK, status);
            EXPECT_EQ(0u, myKeySetId.size());
            keySetId = myKeySetId;
        });
    EXPECT_OK(res);
    return keySetId;
}

/**
 * Test that a session can be opened and closed
 */
TEST_F(DrmHalClearkeyPluginTest, OpenCloseSession) {
    auto sessionId = openSession();
    closeSession(sessionId);
}

/**
 * Test that attempting to close an invalid (empty) sessionId
 * is prohibited with the documented error code.
 */
TEST_F(DrmHalClearkeyPluginTest, CloseInvalidSession) {
    SessionId invalidSessionId;
    Status result = drmPlugin->closeSession(invalidSessionId);
    EXPECT_EQ(Status::BAD_VALUE, result);
}

/**
 * Test that attempting to close a session that is already closed
 * is prohibited with the documented error code.
 */
TEST_F(DrmHalClearkeyPluginTest, CloseClosedSession) {
    SessionId sessionId = openSession();
    closeSession(sessionId);
    Status result = drmPlugin->closeSession(sessionId);
    EXPECT_EQ(Status::ERROR_DRM_SESSION_NOT_OPENED, result);
}

/**
 * A get key request should fail if no sessionId is provided
 */
TEST_F(DrmHalClearkeyPluginTest, GetKeyRequestNoSession) {
    SessionId invalidSessionId;
    hidl_vec<uint8_t> initData;
    hidl_string mimeType = "video/mp4";
    KeyedVector optionalParameters;
    auto res = drmPlugin->getKeyRequest(
            invalidSessionId, initData, mimeType, KeyType::STREAMING,
            optionalParameters,
            [&](Status status, const hidl_vec<uint8_t>&, KeyRequestType,
                const hidl_string&) { EXPECT_EQ(Status::BAD_VALUE, status); });
    EXPECT_OK(res);
}

/**
 * The clearkey plugin doesn't support offline key requests.
 * Test that the plugin returns the expected error code in
 * this case.
 */
TEST_F(DrmHalClearkeyPluginTest, GetKeyRequestOfflineKeyTypeNotSupported) {
    auto sessionId = openSession();
    hidl_vec<uint8_t> initData;
    hidl_string mimeType = "video/mp4";
    KeyedVector optionalParameters;

    auto res = drmPlugin->getKeyRequest(
            sessionId, initData, mimeType, KeyType::OFFLINE, optionalParameters,
            [&](Status status, const hidl_vec<uint8_t>&, KeyRequestType,
                const hidl_string&) {
                // Clearkey plugin doesn't support offline key type
                EXPECT_EQ(Status::ERROR_DRM_CANNOT_HANDLE, status);
            });
    EXPECT_OK(res);
    closeSession(sessionId);
}

/**
 * Test that the plugin returns the documented error for the
 * case of attempting to generate a key request using an
 * invalid mime type
 */
TEST_F(DrmHalClearkeyPluginTest, GetKeyRequestBadMime) {
    auto sessionId = openSession();
    hidl_vec<uint8_t> initData;
    hidl_string mimeType = "video/unknown";
    KeyedVector optionalParameters;
    auto res = drmPlugin->getKeyRequest(
            sessionId, initData, mimeType, KeyType::STREAMING,
            optionalParameters, [&](Status status, const hidl_vec<uint8_t>&,
                                    KeyRequestType, const hidl_string&) {
                EXPECT_EQ(Status::ERROR_DRM_CANNOT_HANDLE, status);
            });
    EXPECT_OK(res);
    closeSession(sessionId);
}

/**
 * Test that a closed sessionID returns SESSION_NOT_OPENED
 */
TEST_F(DrmHalClearkeyPluginTest, ProvideKeyResponseClosedSession) {
    SessionId session = openSession();
    closeSession(session);

    hidl_vec<uint8_t> keyResponse = {0x7b, 0x22, 0x6b, 0x65,
                                     0x79, 0x73, 0x22, 0x3a};
    auto res = drmPlugin->provideKeyResponse(
            session, keyResponse,
            [&](Status status, const hidl_vec<uint8_t>& keySetId) {
                EXPECT_EQ(Status::ERROR_DRM_SESSION_NOT_OPENED, status);
                EXPECT_EQ(0u, keySetId.size());
            });
    EXPECT_OK(res);
}

/**
 * Test that an empty sessionID returns BAD_VALUE
 */
TEST_F(DrmHalClearkeyPluginTest, ProvideKeyResponseInvalidSessionId) {
    SessionId session;

    hidl_vec<uint8_t> keyResponse = {0x7b, 0x22, 0x6b, 0x65,
                                     0x79, 0x73, 0x22, 0x3a};
    auto res = drmPlugin->provideKeyResponse(
            session, keyResponse,
            [&](Status status, const hidl_vec<uint8_t>& keySetId) {
                EXPECT_EQ(Status::BAD_VALUE, status);
                EXPECT_EQ(0u, keySetId.size());
            });
    EXPECT_OK(res);
}

/**
 * Test that an empty key response returns BAD_VALUE
 */
TEST_F(DrmHalClearkeyPluginTest, ProvideKeyResponseEmptyResponse) {
    SessionId session = openSession();
    hidl_vec<uint8_t> emptyResponse;
    auto res = drmPlugin->provideKeyResponse(
            session, emptyResponse,
            [&](Status status, const hidl_vec<uint8_t>& keySetId) {
                EXPECT_EQ(Status::BAD_VALUE, status);
                EXPECT_EQ(0u, keySetId.size());
            });
    EXPECT_OK(res);
    closeSession(session);
}

/**
 * Test that a removeKeys on an empty sessionID returns BAD_VALUE
 */
TEST_F(DrmHalClearkeyPluginTest, RemoveKeysEmptySessionId) {
    SessionId sessionId;
    Status status = drmPlugin->removeKeys(sessionId);
    EXPECT_TRUE(status == Status::BAD_VALUE);
}

/**
 * Remove keys is not supported for clearkey.
 */
TEST_F(DrmHalClearkeyPluginTest, RemoveKeysNewSession) {
    SessionId sessionId = openSession();
    Status status = drmPlugin->removeKeys(sessionId);
    // Clearkey plugin doesn't support remove keys
    EXPECT_EQ(Status::ERROR_DRM_CANNOT_HANDLE, status);
    closeSession(sessionId);
}

/**
 * Test that ClearKey cannot handle key restoring.
 * Expected message is Status::ERROR_DRM_CANNOT_HANDLE.
 */
TEST_F(DrmHalClearkeyPluginTest, RestoreKeysCannotHandle) {
    hidl_vec<uint8_t> keySetId = {0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 1};
    SessionId sessionId = openSession();
    Status status = drmPlugin->restoreKeys(sessionId, keySetId);
    EXPECT_EQ(Status::ERROR_DRM_CANNOT_HANDLE, status);
    closeSession(sessionId);
}

/**
 * Test that restoreKeys fails with a null key set ID.
 * Error message is expected to be Status::BAD_VALUE.
 */
TEST_F(DrmHalClearkeyPluginTest, RestoreKeysNull) {
    SessionId sessionId = openSession();
    hidl_vec<uint8_t> nullKeySetId;
    Status status = drmPlugin->restoreKeys(sessionId, nullKeySetId);
    EXPECT_EQ(Status::BAD_VALUE, status);
    closeSession(sessionId);
}

/**
 * Test that the clearkey plugin doesn't support getting
 * secure stops.
 */
TEST_F(DrmHalClearkeyPluginTest, GetSecureStops) {
    auto res = drmPlugin->getSecureStops(
            [&](Status status, const hidl_vec<SecureStop>&) {
                // Clearkey plugin doesn't support secure stops
                EXPECT_EQ(Status::ERROR_DRM_CANNOT_HANDLE, status);
            });
    EXPECT_OK(res);
}

/**
 * Test that the clearkey plugin returns BAD_VALUE if
 * an empty ssid is provided.
 */
TEST_F(DrmHalClearkeyPluginTest, GetSecureStopEmptySSID) {
    SecureStopId ssid;
    auto res = drmPlugin->getSecureStop(
            ssid, [&](Status status, const SecureStop&) {
                EXPECT_EQ(Status::BAD_VALUE, status);
            });
    EXPECT_OK(res);
}

/**
 * Test that releasing all secure stops isn't handled by
 * clearkey.
 */
TEST_F(DrmHalClearkeyPluginTest, ReleaseAllSecureStops) {
    EXPECT_EQ(Status::ERROR_DRM_CANNOT_HANDLE,
              drmPlugin->releaseAllSecureStops());
}

/**
 * Test that releasing a specific secure stop with an empty
 * SSID returns BAD_VALUE.
 */
TEST_F(DrmHalClearkeyPluginTest, ReleaseSecureStopEmptySSID) {
    SecureStopId ssid;
    Status status = drmPlugin->releaseSecureStop(ssid);
    EXPECT_EQ(Status::BAD_VALUE, status);
}

/**
 * The following four tests verify that the properties
 * defined in the MediaDrm API are supported by
 * the plugin.
 */
TEST_F(DrmHalClearkeyPluginTest, GetVendorProperty) {
    auto res = drmPlugin->getPropertyString(
            "vendor", [&](Status status, const hidl_string& value) {
                EXPECT_EQ(Status::OK, status);
                EXPECT_EQ("Google", value);
            });
    EXPECT_OK(res);
}

TEST_F(DrmHalClearkeyPluginTest, GetVersionProperty) {
    auto res = drmPlugin->getPropertyString(
            "version", [&](Status status, const hidl_string& value) {
                EXPECT_EQ(Status::OK, status);
                EXPECT_EQ("1.0", value);
            });
    EXPECT_OK(res);
}

TEST_F(DrmHalClearkeyPluginTest, GetDescriptionProperty) {
    auto res = drmPlugin->getPropertyString(
            "description", [&](Status status, const hidl_string& value) {
                EXPECT_EQ(Status::OK, status);
                EXPECT_EQ("ClearKey CDM", value);
            });
    EXPECT_OK(res);
}

TEST_F(DrmHalClearkeyPluginTest, GetAlgorithmsProperty) {
    auto res = drmPlugin->getPropertyString(
            "algorithms", [&](Status status, const hidl_string& value) {
                EXPECT_EQ(Status::OK, status);
                EXPECT_EQ("", value);
            });
    EXPECT_OK(res);
}

/**
 * Test that attempting to read invalid string and byte array
 * properties returns the documented error code.
 */
TEST_F(DrmHalClearkeyPluginTest, GetInvalidStringProperty) {
    auto res = drmPlugin->getPropertyString(
            "invalid", [&](Status status, const hidl_string&) {
                EXPECT_EQ(Status::ERROR_DRM_CANNOT_HANDLE, status);
            });
    EXPECT_OK(res);
}

TEST_F(DrmHalClearkeyPluginTest, GetByteArrayPropertyNotSupported) {
    auto res = drmPlugin->getPropertyByteArray(
            "deviceUniqueId", [&](Status status, const hidl_vec<uint8_t>&) {
                EXPECT_EQ(Status::ERROR_DRM_CANNOT_HANDLE, status);
            });
    EXPECT_OK(res);
}

/**
 * Clearkey doesn't support setting string or byte array properties,
 * particularly an undefined one.
 */
TEST_F(DrmHalClearkeyPluginTest, SetStringPropertyNotSupported) {
    Status status = drmPlugin->setPropertyString("property", "value");
    EXPECT_EQ(Status::ERROR_DRM_CANNOT_HANDLE, status);
}

TEST_F(DrmHalClearkeyPluginTest, SetByteArrayPropertyNotSupported) {
    hidl_vec<uint8_t> value;
    Status status = drmPlugin->setPropertyByteArray("property", value);
    EXPECT_EQ(Status::ERROR_DRM_CANNOT_HANDLE, status);
}

/**
 * Clearkey doesn't support setting cipher algorithms, verify it
 */
TEST_F(DrmHalClearkeyPluginTest, SetCipherAlgorithmNotSupported) {
    SessionId session = openSession();
    hidl_string algorithm = "AES/CBC/NoPadding";
    Status status = drmPlugin->setCipherAlgorithm(session, algorithm);
    EXPECT_EQ(Status::ERROR_DRM_CANNOT_HANDLE, status);
    closeSession(session);
}

/**
 * Setting an empty algorithm should return BAD_VALUE
 */
TEST_F(DrmHalClearkeyPluginTest, SetCipherEmptyAlgorithm) {
    SessionId session = openSession();
    hidl_string algorithm;
    Status status = drmPlugin->setCipherAlgorithm(session, algorithm);
    EXPECT_EQ(Status::BAD_VALUE, status);
    closeSession(session);
}

/**
 * Setting a cipher algorithm with no session returns BAD_VALUE
 */
TEST_F(DrmHalClearkeyPluginTest, SetCipherAlgorithmNoSession) {
    SessionId session;
    hidl_string algorithm = "AES/CBC/NoPadding";
    Status status = drmPlugin->setCipherAlgorithm(session, algorithm);
    EXPECT_EQ(Status::BAD_VALUE, status);
}

/**
 * Clearkey doesn't support setting mac algorithms, verify it
 */
TEST_F(DrmHalClearkeyPluginTest, SetMacAlgorithmNotSupported) {
    SessionId session = openSession();
    hidl_string algorithm = "HmacSHA256";
    Status status = drmPlugin->setMacAlgorithm(session, algorithm);
    EXPECT_EQ(Status::ERROR_DRM_CANNOT_HANDLE, status);
    closeSession(session);
}

/**
 * Setting an empty algorithm should return BAD_VALUE
 */
TEST_F(DrmHalClearkeyPluginTest, SetMacEmptyAlgorithm) {
    SessionId session = openSession();
    hidl_string algorithm;
    Status status = drmPlugin->setMacAlgorithm(session, algorithm);
    EXPECT_EQ(Status::BAD_VALUE, status);
    closeSession(session);
}

/**
 * Setting a mac algorithm with no session should return BAD_VALUE
 */
TEST_F(DrmHalClearkeyPluginTest, SetMacAlgorithmNoSession) {
    SessionId session;
    hidl_string algorithm = "HmacSHA256";
    Status status = drmPlugin->setMacAlgorithm(session, algorithm);
    EXPECT_EQ(Status::BAD_VALUE, status);
}

/**
 * The Generic* methods provide general purpose crypto operations
 * that may be used for applications other than DRM. They leverage
 * the hardware root of trust and secure key distribution mechanisms
 * of a DRM system to enable app-specific crypto functionality where
 * the crypto keys are not exposed outside of the trusted execution
 * environment.
 *
 * Clearkey doesn't support generic encrypt/decrypt/sign/verify.
 */
TEST_F(DrmHalClearkeyPluginTest, GenericEncryptNotSupported) {
    SessionId session = openSession();
    ;
    hidl_vec<uint8_t> keyId = {0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 1};
    hidl_vec<uint8_t> input = {1, 2, 3, 4, 5};
    hidl_vec<uint8_t> iv = std::vector<uint8_t>(AES_BLOCK_SIZE, 0);
    auto res = drmPlugin->encrypt(session, keyId, input, iv,
                                  [&](Status status, const hidl_vec<uint8_t>&) {
                                      EXPECT_EQ(Status::ERROR_DRM_CANNOT_HANDLE,
                                                status);
                                  });
    EXPECT_OK(res);
    closeSession(session);
}

TEST_F(DrmHalClearkeyPluginTest, GenericDecryptNotSupported) {
    SessionId session = openSession();
    hidl_vec<uint8_t> keyId = {0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 1};
    hidl_vec<uint8_t> input = {1, 2, 3, 4, 5};
    hidl_vec<uint8_t> iv = std::vector<uint8_t>(AES_BLOCK_SIZE, 0);
    auto res = drmPlugin->decrypt(session, keyId, input, iv,
                                  [&](Status status, const hidl_vec<uint8_t>&) {
                                      EXPECT_EQ(Status::ERROR_DRM_CANNOT_HANDLE,
                                                status);
                                  });
    EXPECT_OK(res);
    closeSession(session);
}

TEST_F(DrmHalClearkeyPluginTest, GenericSignNotSupported) {
    SessionId session = openSession();
    ;
    hidl_vec<uint8_t> keyId = {0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 1};
    hidl_vec<uint8_t> message = {1, 2, 3, 4, 5};
    auto res = drmPlugin->sign(session, keyId, message,
                               [&](Status status, const hidl_vec<uint8_t>&) {
                                   EXPECT_EQ(Status::ERROR_DRM_CANNOT_HANDLE,
                                             status);
                               });
    EXPECT_OK(res);
    closeSession(session);
}

TEST_F(DrmHalClearkeyPluginTest, GenericVerifyNotSupported) {
    SessionId session = openSession();
    ;
    hidl_vec<uint8_t> keyId = {0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 1};
    hidl_vec<uint8_t> message = {1, 2, 3, 4, 5};
    hidl_vec<uint8_t> signature = {0, 0, 0, 0, 0, 0, 0, 0,
                                   0, 0, 0, 0, 0, 0, 0, 0};
    auto res = drmPlugin->verify(
            session, keyId, message, signature, [&](Status status, bool) {
                EXPECT_EQ(Status::ERROR_DRM_CANNOT_HANDLE, status);
            });
    EXPECT_OK(res);
    closeSession(session);
}

TEST_F(DrmHalClearkeyPluginTest, GenericSignRSANotSupported) {
    SessionId session = openSession();
    hidl_string algorithm = "RSASSA-PSS-SHA1";
    hidl_vec<uint8_t> message = {1, 2, 3, 4, 5};
    hidl_vec<uint8_t> wrappedKey = {0, 0, 0, 0, 0, 0, 0, 0,
                                    0, 0, 0, 0, 0, 0, 0, 0};
    auto res = drmPlugin->signRSA(session, algorithm, message, wrappedKey,
                                  [&](Status status, const hidl_vec<uint8_t>&) {
                                      EXPECT_EQ(Status::ERROR_DRM_CANNOT_HANDLE,
                                                status);
                                  });
    EXPECT_OK(res);
    closeSession(session);
}

/**
 *  CryptoPlugin tests
 */

/**
 * Clearkey doesn't support secure decoder and is expected to
 * return false.
 */
TEST_F(DrmHalClearkeyPluginTest, RequiresSecureDecoder) {
    EXPECT_FALSE(cryptoPlugin->requiresSecureDecoderComponent("cenc"));
}

/**
 * Verify that requiresSecureDecoderComponent handles empty mimetype
 */
TEST_F(DrmHalClearkeyPluginTest, RequiresSecureDecoderEmptyMimeType) {
    EXPECT_FALSE(cryptoPlugin->requiresSecureDecoderComponent(""));
}

/**
 * Exercise the NotifyResolution API. There is no observable result,
 * just call the method for coverage.
 */
TEST_F(DrmHalClearkeyPluginTest, NotifyResolution) {
    cryptoPlugin->notifyResolution(1920, 1080);
}

/**
 * getDecryptMemory allocates memory for decryption, then sets it
 * as a shared buffer base in the crypto hal.  The allocated and
 * mapped IMemory is returned.
 *
 * @param size the size of the memory segment to allocate
 * @param the index of the memory segment which will be used
 * to refer to it for decryption.
 */
sp<IMemory> DrmHalClearkeyPluginTest::getDecryptMemory(size_t size,
                                                       size_t index) {
    sp<IAllocator> ashmemAllocator = IAllocator::getService("ashmem");
    EXPECT_NE(nullptr, ashmemAllocator.get());

    hidl_memory hidlMemory;
    auto res = ashmemAllocator->allocate(
            size, [&](bool success, const hidl_memory& memory) {
                EXPECT_EQ(true, success);
                EXPECT_OK(cryptoPlugin->setSharedBufferBase(memory, index));
                hidlMemory = memory;
            });
    EXPECT_OK(res);

    sp<IMemory> mappedMemory = mapMemory(hidlMemory);
    EXPECT_NE(nullptr, mappedMemory.get());
    EXPECT_OK(cryptoPlugin->setSharedBufferBase(hidlMemory, index));
    return mappedMemory;
}

/**
 * Exercise the setMediaDrmSession method. setMediaDrmSession
 * is used to associate a drm session with a crypto session.
 */
TEST_F(DrmHalClearkeyPluginTest, SetMediaDrmSession) {
    auto sessionId = openSession();
    Status status = cryptoPlugin->setMediaDrmSession(sessionId);
    EXPECT_EQ(Status::OK, status);
    closeSession(sessionId);
}

/**
 * setMediaDrmSession with a closed session id
 */
TEST_F(DrmHalClearkeyPluginTest, SetMediaDrmSessionClosedSession) {
    auto sessionId = openSession();
    closeSession(sessionId);
    Status status = cryptoPlugin->setMediaDrmSession(sessionId);
    EXPECT_EQ(Status::ERROR_DRM_SESSION_NOT_OPENED, status);
}

/**
 * setMediaDrmSession with an empty session id: BAD_VALUE.  An
 * empty session clears the previously set session and should
 * return OK.
 */
TEST_F(DrmHalClearkeyPluginTest, SetMediaDrmSessionEmptySession) {
    SessionId sessionId;
    Status status = cryptoPlugin->setMediaDrmSession(sessionId);
    EXPECT_EQ(Status::OK, status);
}

/**
 * Decrypt tests
 */

class DrmHalClearkeyDecryptTest : public DrmHalClearkeyPluginTest {
   public:
    void fillRandom(const sp<IMemory>& memory);
    hidl_array<uint8_t, 16> toHidlArray(const vector<uint8_t>& vec) {
        EXPECT_EQ(16u, vec.size());
        return hidl_array<uint8_t, 16>(&vec[0]);
    }
    uint32_t decrypt(Mode mode, uint8_t* iv, const hidl_vec<SubSample>& subSamples,
            const Pattern& pattern, Status status);
    void aes_ctr_decrypt(uint8_t* dest, uint8_t* src, uint8_t* iv,
            const hidl_vec<SubSample>& subSamples, const vector<uint8_t>& key);
    void aes_cbc_decrypt(uint8_t* dest, uint8_t* src, uint8_t* iv,
            const hidl_vec<SubSample>& subSamples, const vector<uint8_t>& key);
};

void DrmHalClearkeyDecryptTest::fillRandom(const sp<IMemory>& memory) {
    random_device rd;
    mt19937 rand(rd());
    for (size_t i = 0; i < memory->getSize() / sizeof(uint32_t); i++) {
        auto p = static_cast<uint32_t*>(
                static_cast<void*>(memory->getPointer()));
        p[i] = rand();
    }
}

uint32_t DrmHalClearkeyDecryptTest::decrypt(Mode mode,
        uint8_t* iv, const hidl_vec<SubSample>& subSamples,
        const Pattern& pattern, Status expectedStatus) {
    const size_t kSegmentIndex = 0;
    const vector<uint8_t> keyId = {0x60, 0x06, 0x1e, 0x01, 0x7e, 0x47,
                                   0x7e, 0x87, 0x7e, 0x57, 0xd0, 0x0d,
                                   0x1e, 0xd0, 0x0d, 0x1e};
    const vector<uint8_t> contentKey = {0x1a, 0x8a, 0x20, 0x95, 0xe4,
                                        0xde, 0xb2, 0xd2, 0x9e, 0xc8,
                                        0x16, 0xac, 0x7b, 0xae, 0x20, 0x82};
    uint8_t localIv[AES_BLOCK_SIZE];
    memcpy(localIv, iv, AES_BLOCK_SIZE);

    size_t totalSize = 0;
    for (size_t i = 0; i < subSamples.size(); i++) {
        totalSize += subSamples[i].numBytesOfClearData;
        totalSize += subSamples[i].numBytesOfEncryptedData;
    }

    // The first totalSize bytes of shared memory is the encrypted
    // input, the second totalSize bytes is the decrypted output.
    sp<IMemory> sharedMemory =
            getDecryptMemory(totalSize * 2, kSegmentIndex);

    const SharedBuffer sourceBuffer = {
        .bufferId = kSegmentIndex, .offset = 0, .size = totalSize};
    fillRandom(sharedMemory);

    const DestinationBuffer destBuffer = {.type = BufferType::SHARED_MEMORY,
                                          {.bufferId = kSegmentIndex,
                                           .offset = totalSize,
                                           .size = totalSize},
                                          .secureMemory = nullptr};
    const uint64_t offset = 0;
    const bool kNotSecure = false;
    uint32_t bytesWritten = 0;
    auto res = cryptoPlugin->decrypt(kNotSecure, toHidlArray(keyId), localIv, mode,
            pattern, subSamples, sourceBuffer, offset, destBuffer,
            [&](Status status, uint32_t count, string detailedError) {
                EXPECT_EQ(expectedStatus, status) << "Unexpected decrypt status " <<
                detailedError;
                bytesWritten = count;
            });
    EXPECT_OK(res);

    if (bytesWritten != totalSize) {
        return bytesWritten;
    }
    uint8_t* base = static_cast<uint8_t*>(
            static_cast<void*>(sharedMemory->getPointer()));

    // generate reference vector
    vector<uint8_t> reference(totalSize);

    memcpy(localIv, iv, AES_BLOCK_SIZE);
    switch (mode) {
    case Mode::UNENCRYPTED:
        memcpy(&reference[0], base, totalSize);
        break;
    case Mode::AES_CTR:
        aes_ctr_decrypt(&reference[0], base, localIv, subSamples, contentKey);
        break;
    case Mode::AES_CBC:
        aes_cbc_decrypt(&reference[0], base, localIv, subSamples, contentKey);
        break;
    case Mode::AES_CBC_CTS:
        EXPECT_TRUE(false) << "AES_CBC_CTS mode not supported";
        break;
    }

    // compare reference to decrypted data which is at base + total size
    EXPECT_EQ(0, memcmp(static_cast<void *>(&reference[0]),
                        static_cast<void*>(base + totalSize), totalSize))
            << "decrypt data mismatch";
    return totalSize;
}

/**
 * Decrypt a list of clear+encrypted subsamples using the specified key
 * in AES-CTR mode
 */
void DrmHalClearkeyDecryptTest::aes_ctr_decrypt(uint8_t* dest, uint8_t* src,
        uint8_t* iv, const hidl_vec<SubSample>& subSamples,
        const vector<uint8_t>& key) {
    AES_KEY decryptionKey;
    AES_set_encrypt_key(&key[0], 128, &decryptionKey);

    size_t offset = 0;
    unsigned int blockOffset = 0;
    uint8_t previousEncryptedCounter[AES_BLOCK_SIZE];
    memset(previousEncryptedCounter, 0, AES_BLOCK_SIZE);

    for (size_t i = 0; i < subSamples.size(); i++) {
        const SubSample& subSample = subSamples[i];

        if (subSample.numBytesOfClearData > 0) {
            memcpy(dest + offset, src + offset, subSample.numBytesOfClearData);
            offset += subSample.numBytesOfClearData;
        }

        if (subSample.numBytesOfEncryptedData > 0) {
            AES_ctr128_encrypt(src + offset, dest + offset,
                    subSample.numBytesOfEncryptedData, &decryptionKey,
                    iv, previousEncryptedCounter, &blockOffset);
            offset += subSample.numBytesOfEncryptedData;
        }
    }
}

/**
 * Decrypt a list of clear+encrypted subsamples using the specified key
 * in AES-CBC mode
 */
void DrmHalClearkeyDecryptTest::aes_cbc_decrypt(uint8_t* dest, uint8_t* src,
        uint8_t* iv, const hidl_vec<SubSample>& subSamples,
        const vector<uint8_t>& key) {
    AES_KEY decryptionKey;
    AES_set_encrypt_key(&key[0], 128, &decryptionKey);

    size_t offset = 0;
    for (size_t i = 0; i < subSamples.size(); i++) {
        memcpy(dest + offset, src + offset, subSamples[i].numBytesOfClearData);
        offset += subSamples[i].numBytesOfClearData;

        AES_cbc_encrypt(src + offset, dest + offset, subSamples[i].numBytesOfEncryptedData,
                &decryptionKey, iv, 0 /* decrypt */);
        offset += subSamples[i].numBytesOfEncryptedData;
    }
}

/**
 * Test query key status
 */
TEST_F(DrmHalClearkeyDecryptTest, TestQueryKeyStatus) {
    auto sessionId = openSession();
    auto res = drmPlugin->queryKeyStatus(
        sessionId, [&](Status status, KeyedVector /* info */) { EXPECT_EQ(Status::OK, status); });
    EXPECT_OK(res);

    closeSession(sessionId);
}

/**
 * Positive decrypt test.  "Decrypt" a single clear segment
 */
TEST_F(DrmHalClearkeyDecryptTest, ClearSegmentTest) {
    vector<uint8_t> iv(AES_BLOCK_SIZE, 0);
    const Pattern noPattern = {0, 0};
    const vector<SubSample> subSamples = {
        {.numBytesOfClearData = k256SubSampleByteCount,
         .numBytesOfEncryptedData = 0}};
    auto sessionId = openSession();
    loadKeys(sessionId);

    Status status = cryptoPlugin->setMediaDrmSession(sessionId);
    EXPECT_EQ(Status::OK, status);

    uint32_t byteCount = decrypt(Mode::UNENCRYPTED, &iv[0], subSamples,
            noPattern, Status::OK);
    EXPECT_EQ(k256SubSampleByteCount, byteCount);

    closeSession(sessionId);
}

/**
 * Positive decrypt test.  Decrypt a single segment using AES_CTR.
 * Verify data matches.
 */
TEST_F(DrmHalClearkeyDecryptTest, EncryptedAesCtrSegmentTest) {
    vector<uint8_t> iv(AES_BLOCK_SIZE, 0);
    const Pattern noPattern = {0, 0};
    const vector<SubSample> subSamples = {
        {.numBytesOfClearData = k512SubSampleClearBytes,
         .numBytesOfEncryptedData = k512SubSampleEncryptedBytes}};
    auto sessionId = openSession();
    loadKeys(sessionId);

    Status status = cryptoPlugin->setMediaDrmSession(sessionId);
    EXPECT_EQ(Status::OK, status);

    uint32_t byteCount = decrypt(Mode::AES_CTR, &iv[0], subSamples,
            noPattern, Status::OK);
    EXPECT_EQ(k512SubSampleClearBytes + k512SubSampleEncryptedBytes, byteCount);

    closeSession(sessionId);
}

/**
 * Negative decrypt test. Decrypt without loading keys.
 */
TEST_F(DrmHalClearkeyDecryptTest, EncryptedAesCtrSegmentTestNoKeys) {
    vector<uint8_t> iv(AES_BLOCK_SIZE, 0);
    const Pattern noPattern = {0, 0};
    const vector<SubSample> subSamples = {
        {.numBytesOfClearData = k256SubSampleByteCount,
         .numBytesOfEncryptedData = k256SubSampleByteCount}};
    auto sessionId = openSession();

    Status status = cryptoPlugin->setMediaDrmSession(sessionId);
    EXPECT_EQ(Status::OK, status);

    uint32_t byteCount = decrypt(Mode::AES_CTR, &iv[0], subSamples,
            noPattern, Status::ERROR_DRM_NO_LICENSE);
    EXPECT_EQ(0u, byteCount);

    closeSession(sessionId);
}

/**
 * Negative decrypt test. Decrypt with invalid key.
 */
TEST_F(DrmHalClearkeyDecryptTest, DecryptWithEmptyKey) {
    vector<uint8_t> iv(AES_BLOCK_SIZE, 0);
    const Pattern noPattern = {0, 0};
    const vector<SubSample> subSamples = {
        {.numBytesOfClearData = k512SubSampleClearBytes,
         .numBytesOfEncryptedData = k512SubSampleEncryptedBytes}};

    // base 64 encoded JSON response string, must not contain padding character '='
    const hidl_string emptyKeyResponse =
            "{\"keys\":[" \
                "{" \
                    "\"kty\":\"oct\"" \
                    "\"alg\":\"A128KW2\"" \
                    "\"k\":\"SGVsbG8gRnJpZW5kIQ\"" \
                    "\"kid\":\"Y2xlYXJrZXlrZXlpZDAyAy\"" \
                "}" \
                "{" \
                    "\"kty\":\"oct\"," \
                    "\"alg\":\"A128KW2\"" \
                    "\"kid\":\"Y2xlYXJrZXlrZXlpZDAzAy\"," \
                    // empty key follows
                    "\"k\":\"R\"" \
                "}]" \
            "}";
    const size_t kEmptyKeyResponseSize = emptyKeyResponse.size();

    hidl_vec<uint8_t> invalidResponse;
    invalidResponse.resize(kEmptyKeyResponseSize);
    memcpy(invalidResponse.data(), emptyKeyResponse.c_str(), kEmptyKeyResponseSize);

    auto sessionId = openSession();

    auto res = drmPlugin->provideKeyResponse(
        sessionId, invalidResponse,
        [&](Status status, const hidl_vec<uint8_t>& myKeySetId) {
            EXPECT_EQ(Status::OK, status);
            EXPECT_EQ(0u, myKeySetId.size());
        });
    EXPECT_OK(res);

    Status status = cryptoPlugin->setMediaDrmSession(sessionId);
    EXPECT_EQ(Status::OK, status);

    uint32_t byteCount = decrypt(Mode::AES_CTR, &iv[0], subSamples,
            noPattern, Status::ERROR_DRM_NO_LICENSE);
    EXPECT_EQ(0u, byteCount);

    closeSession(sessionId);
}

/**
 * Negative decrypt test. Decrypt with a key exceeds AES_BLOCK_SIZE.
 */
TEST_F(DrmHalClearkeyDecryptTest, DecryptWithKeyTooLong) {
    vector<uint8_t> iv(AES_BLOCK_SIZE, 0);
    const Pattern noPattern = {0, 0};
    const vector<SubSample> subSamples = {
        {.numBytesOfClearData = k512SubSampleClearBytes,
         .numBytesOfEncryptedData = k512SubSampleEncryptedBytes}};

    // base 64 encoded JSON response string, must not contain padding character '='
    const hidl_string keyTooLongResponse =
            "{\"keys\":[" \
                "{" \
                    "\"kty\":\"oct\"," \
                    "\"alg\":\"A128KW2\"" \
                    "\"kid\":\"Y2xlYXJrZXlrZXlpZDAzAy\"," \
                    // key too long
                    "\"k\":\"V2lubmllIHRoZSBwb29oIVdpbm5pZSB0aGUgcG9vaCE=\"" \
                "}]" \
            "}";
    const size_t kKeyTooLongResponseSize = keyTooLongResponse.size();

    hidl_vec<uint8_t> invalidResponse;
    invalidResponse.resize(kKeyTooLongResponseSize);
    memcpy(invalidResponse.data(), keyTooLongResponse.c_str(), kKeyTooLongResponseSize);

    auto sessionId = openSession();

    auto res = drmPlugin->provideKeyResponse(
        sessionId, invalidResponse,
        [&](Status status, const hidl_vec<uint8_t>& myKeySetId) {
            EXPECT_EQ(Status::OK, status);
            EXPECT_EQ(0u, myKeySetId.size());
        });
    EXPECT_OK(res);

    Status status = cryptoPlugin->setMediaDrmSession(sessionId);
    EXPECT_EQ(Status::OK, status);

    uint32_t byteCount = decrypt(Mode::AES_CTR, &iv[0], subSamples,
            noPattern, Status::ERROR_DRM_NO_LICENSE);
    EXPECT_EQ(0u, byteCount);

    closeSession(sessionId);
}
