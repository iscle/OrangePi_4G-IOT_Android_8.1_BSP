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

#define LOG_TAG "drm_hal_vendor_test@1.0"

#include <android/hardware/drm/1.0/ICryptoFactory.h>
#include <android/hardware/drm/1.0/ICryptoPlugin.h>
#include <android/hardware/drm/1.0/IDrmFactory.h>
#include <android/hardware/drm/1.0/IDrmPlugin.h>
#include <android/hardware/drm/1.0/IDrmPluginListener.h>
#include <android/hardware/drm/1.0/types.h>
#include <android/hidl/allocator/1.0/IAllocator.h>
#include <gtest/gtest.h>
#include <hidlmemory/mapping.h>
#include <log/log.h>
#include <memory>
#include <openssl/aes.h>
#include <random>

#include "drm_hal_vendor_module_api.h"
#include "vendor_modules.h"
#include <VtsHalHidlTargetCallbackBase.h>
#include <VtsHalHidlTargetTestBase.h>

using ::android::hardware::drm::V1_0::BufferType;
using ::android::hardware::drm::V1_0::DestinationBuffer;
using ::android::hardware::drm::V1_0::EventType;
using ::android::hardware::drm::V1_0::ICryptoFactory;
using ::android::hardware::drm::V1_0::ICryptoPlugin;
using ::android::hardware::drm::V1_0::IDrmFactory;
using ::android::hardware::drm::V1_0::IDrmPlugin;
using ::android::hardware::drm::V1_0::IDrmPluginListener;
using ::android::hardware::drm::V1_0::KeyedVector;
using ::android::hardware::drm::V1_0::KeyRequestType;
using ::android::hardware::drm::V1_0::KeyStatus;
using ::android::hardware::drm::V1_0::KeyStatusType;
using ::android::hardware::drm::V1_0::KeyType;
using ::android::hardware::drm::V1_0::KeyValue;
using ::android::hardware::drm::V1_0::Mode;
using ::android::hardware::drm::V1_0::Pattern;
using ::android::hardware::drm::V1_0::SecureStop;
using ::android::hardware::drm::V1_0::SecureStopId;
using ::android::hardware::drm::V1_0::SessionId;
using ::android::hardware::drm::V1_0::SharedBuffer;
using ::android::hardware::drm::V1_0::Status;
using ::android::hardware::drm::V1_0::SubSample;

using ::android::hardware::hidl_array;
using ::android::hardware::hidl_memory;
using ::android::hardware::hidl_string;
using ::android::hardware::hidl_vec;
using ::android::hardware::Return;
using ::android::hardware::Void;
using ::android::hidl::allocator::V1_0::IAllocator;
using ::android::hidl::memory::V1_0::IMemory;
using ::android::sp;

using std::string;
using std::unique_ptr;
using std::random_device;
using std::map;
using std::mt19937;
using std::vector;

using ContentConfiguration = ::DrmHalVTSVendorModule_V1::ContentConfiguration;
using Key = ::DrmHalVTSVendorModule_V1::ContentConfiguration::Key;
using VtsTestBase = ::testing::VtsHalHidlTargetTestBase;

#define ASSERT_OK(ret) ASSERT_TRUE(ret.isOk())
#define EXPECT_OK(ret) EXPECT_TRUE(ret.isOk())

#define RETURN_IF_SKIPPED \
    if (!vendorModule->isInstalled()) { \
        std::cout << "[  SKIPPED ] This drm scheme not supported." << \
                " library:" << GetParam() << " service-name:" << \
                vendorModule->getServiceName() << std::endl; \
        return; \
    }

static const uint8_t kInvalidUUID[16] = {
        0x10, 0x20, 0x30, 0x40, 0x50, 0x60, 0x70, 0x80,
        0x10, 0x20, 0x30, 0x40, 0x50, 0x60, 0x70, 0x80,
};

static drm_vts::VendorModules* gVendorModules = nullptr;

class DrmHalVendorFactoryTest : public testing::TestWithParam<std::string> {
   public:
    DrmHalVendorFactoryTest()
        : vendorModule(static_cast<DrmHalVTSVendorModule_V1*>(
                        gVendorModules->getModule(GetParam()))),
          contentConfigurations(vendorModule->getContentConfigurations()) {}

    virtual ~DrmHalVendorFactoryTest() {}

    virtual void SetUp() {
        const ::testing::TestInfo* const test_info =
                ::testing::UnitTest::GetInstance()->current_test_info();
        ALOGD("Running test %s.%s from vendor module %s",
              test_info->test_case_name(), test_info->name(),
              GetParam().c_str());

        ASSERT_NE(nullptr, vendorModule.get());

        // First try the binderized service name provided by the vendor module.
        // If that fails, which it can on non-binderized devices, try the default
        // service.
        string name = vendorModule->getServiceName();
        drmFactory = VtsTestBase::getService<IDrmFactory>(name);
        if (drmFactory == nullptr) {
            drmFactory = VtsTestBase::getService<IDrmFactory>();
        }
        ASSERT_NE(nullptr, drmFactory.get());

        // Do the same for the crypto factory
        cryptoFactory = VtsTestBase::getService<ICryptoFactory>(name);
        if (cryptoFactory == nullptr) {
            cryptoFactory = VtsTestBase::getService<ICryptoFactory>();
        }
        ASSERT_NE(nullptr, cryptoFactory.get());

        // If drm scheme not installed skip subsequent tests
        if (!drmFactory->isCryptoSchemeSupported(getVendorUUID())) {
            vendorModule->setInstalled(false);
            return;
        }
    }

    virtual void TearDown() override {}

   protected:
    hidl_array<uint8_t, 16> getVendorUUID() {
        vector<uint8_t> uuid = vendorModule->getUUID();
        return hidl_array<uint8_t, 16>(&uuid[0]);
    }

    sp<IDrmFactory> drmFactory;
    sp<ICryptoFactory> cryptoFactory;
    unique_ptr<DrmHalVTSVendorModule_V1> vendorModule;
    const vector<ContentConfiguration> contentConfigurations;
};

TEST_P(DrmHalVendorFactoryTest, ValidateConfigurations) {
    const char* kVendorStr = "Vendor module ";
    size_t count = 0;
    for (auto config : contentConfigurations) {
        ASSERT_TRUE(config.name.size() > 0) << kVendorStr << "has no name";
        ASSERT_TRUE(config.serverUrl.size() > 0) << kVendorStr
                                                 << "has no serverUrl";
        ASSERT_TRUE(config.initData.size() > 0) << kVendorStr
                                                << "has no init data";
        ASSERT_TRUE(config.mimeType.size() > 0) << kVendorStr
                                                << "has no mime type";
        ASSERT_TRUE(config.keys.size() >= 1) << kVendorStr << "has no keys";
        for (auto key : config.keys) {
            ASSERT_TRUE(key.keyId.size() > 0) << kVendorStr
                                              << " has zero length keyId";
            ASSERT_TRUE(key.keyId.size() > 0) << kVendorStr
                                              << " has zero length key value";
        }
        count++;
    }
    EXPECT_NE(0u, count);
}

/**
 * Ensure the factory doesn't support an invalid scheme UUID
 */
TEST_P(DrmHalVendorFactoryTest, InvalidPluginNotSupported) {
    EXPECT_FALSE(drmFactory->isCryptoSchemeSupported(kInvalidUUID));
    EXPECT_FALSE(cryptoFactory->isCryptoSchemeSupported(kInvalidUUID));
}

/**
 * Ensure the factory doesn't support an empty UUID
 */
TEST_P(DrmHalVendorFactoryTest, EmptyPluginUUIDNotSupported) {
    hidl_array<uint8_t, 16> emptyUUID;
    memset(emptyUUID.data(), 0, 16);
    EXPECT_FALSE(drmFactory->isCryptoSchemeSupported(emptyUUID));
    EXPECT_FALSE(cryptoFactory->isCryptoSchemeSupported(emptyUUID));
}

/**
 * Check if the factory supports the scheme uuid in the config.
 */
TEST_P(DrmHalVendorFactoryTest, PluginConfigUUIDSupported) {
    RETURN_IF_SKIPPED;
    EXPECT_TRUE(drmFactory->isCryptoSchemeSupported(getVendorUUID()));
    EXPECT_TRUE(cryptoFactory->isCryptoSchemeSupported(getVendorUUID()));
}

/**
 * Ensure empty content type is not supported
 */
TEST_P(DrmHalVendorFactoryTest, EmptyContentTypeNotSupported) {
    hidl_string empty;
    EXPECT_FALSE(drmFactory->isContentTypeSupported(empty));
}

/**
 * Ensure invalid content type is not supported
 */
TEST_P(DrmHalVendorFactoryTest, InvalidContentTypeNotSupported) {
    hidl_string invalid("abcdabcd");
    EXPECT_FALSE(drmFactory->isContentTypeSupported(invalid));
}

/**
 * Ensure valid content types in the configs are supported
 */
TEST_P(DrmHalVendorFactoryTest, ValidContentTypeSupported) {
    RETURN_IF_SKIPPED;
    for (auto config : contentConfigurations) {
        EXPECT_TRUE(drmFactory->isContentTypeSupported(config.mimeType));
    }
}

/**
 * Ensure vendor drm plugin can be created
 */
TEST_P(DrmHalVendorFactoryTest, CreateVendorDrmPlugin) {
    RETURN_IF_SKIPPED;
    hidl_string packageName("android.hardware.drm.test");
    auto res = drmFactory->createPlugin(
            getVendorUUID(), packageName,
            [&](Status status, const sp<IDrmPlugin>& plugin) {
                EXPECT_EQ(Status::OK, status);
                EXPECT_NE(nullptr, plugin.get());
            });
    EXPECT_OK(res);
}

/**
 * Ensure vendor crypto plugin can be created
 */
TEST_P(DrmHalVendorFactoryTest, CreateVendorCryptoPlugin) {
    RETURN_IF_SKIPPED;
    hidl_vec<uint8_t> initVec;
    auto res = cryptoFactory->createPlugin(
            getVendorUUID(), initVec,
            [&](Status status, const sp<ICryptoPlugin>& plugin) {
                EXPECT_EQ(Status::OK, status);
                EXPECT_NE(nullptr, plugin.get());
            });
    EXPECT_OK(res);
}

/**
 * Ensure invalid drm plugin can't be created
 */
TEST_P(DrmHalVendorFactoryTest, CreateInvalidDrmPlugin) {
    RETURN_IF_SKIPPED;
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
TEST_P(DrmHalVendorFactoryTest, CreateInvalidCryptoPlugin) {
    RETURN_IF_SKIPPED;
    hidl_vec<uint8_t> initVec;
    auto res = cryptoFactory->createPlugin(
            kInvalidUUID, initVec,
            [&](Status status, const sp<ICryptoPlugin>& plugin) {
                EXPECT_EQ(Status::ERROR_DRM_CANNOT_HANDLE, status);
                EXPECT_EQ(nullptr, plugin.get());
            });
    EXPECT_OK(res);
}

class DrmHalVendorPluginTest : public DrmHalVendorFactoryTest {
   public:
    virtual ~DrmHalVendorPluginTest() {}
    virtual void SetUp() override {
        // Create factories
        DrmHalVendorFactoryTest::SetUp();
        RETURN_IF_SKIPPED;

        hidl_string packageName("android.hardware.drm.test");
        auto res = drmFactory->createPlugin(
                getVendorUUID(), packageName,
                [this](Status status, const sp<IDrmPlugin>& plugin) {
                    EXPECT_EQ(Status::OK, status);
                    ASSERT_NE(nullptr, plugin.get());
                    drmPlugin = plugin;
                });
        ASSERT_OK(res);

        hidl_vec<uint8_t> initVec;
        res = cryptoFactory->createPlugin(
                getVendorUUID(), initVec,
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
    sp<IMemory> getDecryptMemory(size_t size, size_t index);
    KeyedVector toHidlKeyedVector(const map<string, string>& params);
    hidl_vec<uint8_t> loadKeys(const SessionId& sessionId,
                               const ContentConfiguration& configuration,
                               const KeyType& type);

   protected:
    sp<IDrmPlugin> drmPlugin;
    sp<ICryptoPlugin> cryptoPlugin;
};

/**
 *  DrmPlugin tests
 */

/**
 * Test that a DRM plugin can handle provisioning.  While
 * it is not required that a DRM scheme require provisioning,
 * it should at least return appropriate status values. If
 * a provisioning request is returned, it is passed to the
 * vendor module which should provide a provisioning response
 * that is delivered back to the HAL.
 */

TEST_P(DrmHalVendorPluginTest, DoProvisioning) {
    RETURN_IF_SKIPPED;
    hidl_string certificateType;
    hidl_string certificateAuthority;
    hidl_vec<uint8_t> provisionRequest;
    hidl_string defaultUrl;
    auto res = drmPlugin->getProvisionRequest(
            certificateType, certificateAuthority,
            [&](Status status, const hidl_vec<uint8_t>& request,
                const hidl_string& url) {
                if (status == Status::OK) {
                    EXPECT_NE(request.size(), 0u);
                    provisionRequest = request;
                    defaultUrl = url;
                } else if (status == Status::ERROR_DRM_CANNOT_HANDLE) {
                    EXPECT_EQ(0u, request.size());
                }
            });
    EXPECT_OK(res);

    if (provisionRequest.size() > 0) {
        vector<uint8_t> response = vendorModule->handleProvisioningRequest(
                provisionRequest, defaultUrl);
        ASSERT_NE(0u, response.size());

        auto res = drmPlugin->provideProvisionResponse(
                response, [&](Status status, const hidl_vec<uint8_t>&,
                              const hidl_vec<uint8_t>&) {
                    EXPECT_EQ(Status::OK, status);
                });
        EXPECT_OK(res);
    }
}

/**
 * The DRM HAL should return BAD_VALUE if an empty provisioning
 * response is provided.
 */
TEST_P(DrmHalVendorPluginTest, ProvideEmptyProvisionResponse) {
    RETURN_IF_SKIPPED;
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
SessionId DrmHalVendorPluginTest::openSession() {
    SessionId sessionId;

    auto res = drmPlugin->openSession([&](Status status, const SessionId& id) {
        EXPECT_EQ(Status::OK, status);
        EXPECT_NE(id.size(), 0u);
        sessionId = id;
    });
    EXPECT_OK(res);
    return sessionId;
}

/**
 * Helper method to close a session
 */
void DrmHalVendorPluginTest::closeSession(const SessionId& sessionId) {
    Status status = drmPlugin->closeSession(sessionId);
    EXPECT_EQ(Status::OK, status);
}

KeyedVector DrmHalVendorPluginTest::toHidlKeyedVector(
    const map<string, string>& params) {
    std::vector<KeyValue> stdKeyedVector;
    for (auto it = params.begin(); it != params.end(); ++it) {
        KeyValue keyValue;
        keyValue.key = it->first;
        keyValue.value = it->second;
        stdKeyedVector.push_back(keyValue);
    }
    return KeyedVector(stdKeyedVector);
}

/**
 * Helper method to load keys for subsequent decrypt tests.
 * These tests use predetermined key request/response to
 * avoid requiring a round trip to a license server.
 */
hidl_vec<uint8_t> DrmHalVendorPluginTest::loadKeys(
    const SessionId& sessionId, const ContentConfiguration& configuration,
    const KeyType& type = KeyType::STREAMING) {
    hidl_vec<uint8_t> keyRequest;
    auto res = drmPlugin->getKeyRequest(
        sessionId, configuration.initData, configuration.mimeType, type,
        toHidlKeyedVector(configuration.optionalParameters),
        [&](Status status, const hidl_vec<uint8_t>& request,
            KeyRequestType type, const hidl_string&) {
            EXPECT_EQ(Status::OK, status) << "Failed to get "
                                             "key request for configuration "
                                          << configuration.name;
            EXPECT_EQ(type, KeyRequestType::INITIAL);
            EXPECT_NE(request.size(), 0u) << "Expected key request size"
                                             " to have length > 0 bytes";
            keyRequest = request;
        });
    EXPECT_OK(res);

    /**
     * Get key response from vendor module
     */
    hidl_vec<uint8_t> keyResponse =
        vendorModule->handleKeyRequest(keyRequest, configuration.serverUrl);

    EXPECT_NE(keyResponse.size(), 0u) << "Expected key response size "
                                         "to have length > 0 bytes";

    hidl_vec<uint8_t> keySetId;
    res = drmPlugin->provideKeyResponse(
        sessionId, keyResponse,
        [&](Status status, const hidl_vec<uint8_t>& myKeySetId) {
            EXPECT_EQ(Status::OK, status) << "Failure providing "
                                             "key response for configuration "
                                          << configuration.name;
            keySetId = myKeySetId;
        });
    EXPECT_OK(res);
    return keySetId;
}

/**
 * Test that a session can be opened and closed
 */
TEST_P(DrmHalVendorPluginTest, OpenCloseSession) {
    RETURN_IF_SKIPPED;
    auto sessionId = openSession();
    closeSession(sessionId);
}

/**
 * Test that attempting to close an invalid (empty) sessionId
 * is prohibited with the documented error code.
 */
TEST_P(DrmHalVendorPluginTest, CloseInvalidSession) {
    RETURN_IF_SKIPPED;
    SessionId invalidSessionId;
    Status status = drmPlugin->closeSession(invalidSessionId);
    EXPECT_EQ(Status::BAD_VALUE, status);
}

/**
 * Test that attempting to close a valid session twice
 * is prohibited with the documented error code.
 */
TEST_P(DrmHalVendorPluginTest, CloseClosedSession) {
    RETURN_IF_SKIPPED;
    auto sessionId = openSession();
    closeSession(sessionId);
    Status status = drmPlugin->closeSession(sessionId);
    EXPECT_EQ(Status::ERROR_DRM_SESSION_NOT_OPENED, status);
}

/**
 * A get key request should fail if no sessionId is provided
 */
TEST_P(DrmHalVendorPluginTest, GetKeyRequestNoSession) {
    RETURN_IF_SKIPPED;
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
 * Test that an empty sessionID returns BAD_VALUE
 */
TEST_P(DrmHalVendorPluginTest, ProvideKeyResponseEmptySessionId) {
    RETURN_IF_SKIPPED;
    SessionId session;

    hidl_vec<uint8_t> keyResponse = {0x7b, 0x22, 0x6b, 0x65,
                                     0x79, 0x73, 0x22, 0x3a};
    auto res = drmPlugin->provideKeyResponse(
            session, keyResponse,
            [&](Status status, const hidl_vec<uint8_t>& keySetId) {
                EXPECT_EQ(Status::BAD_VALUE, status);
                EXPECT_EQ(keySetId.size(), 0u);
            });
    EXPECT_OK(res);
}

/**
 * Test that an empty key response returns BAD_VALUE
 */
TEST_P(DrmHalVendorPluginTest, ProvideKeyResponseEmptyResponse) {
    RETURN_IF_SKIPPED;
    SessionId session = openSession();
    hidl_vec<uint8_t> emptyResponse;
    auto res = drmPlugin->provideKeyResponse(
            session, emptyResponse,
            [&](Status status, const hidl_vec<uint8_t>& keySetId) {
                EXPECT_EQ(Status::BAD_VALUE, status);
                EXPECT_EQ(keySetId.size(), 0u);
            });
    EXPECT_OK(res);
    closeSession(session);
}

/**
 * Test that a removeKeys on an empty sessionID returns BAD_VALUE
 */
TEST_P(DrmHalVendorPluginTest, RemoveKeysEmptySessionId) {
    RETURN_IF_SKIPPED;
    SessionId sessionId;
    Status status = drmPlugin->removeKeys(sessionId);
    EXPECT_TRUE(status == Status::BAD_VALUE);
}

/**
 * Test that remove keys returns okay on an initialized session
 * that has no keys.
 */
TEST_P(DrmHalVendorPluginTest, RemoveKeysNewSession) {
    RETURN_IF_SKIPPED;
    SessionId sessionId = openSession();
    Status status = drmPlugin->removeKeys(sessionId);
    EXPECT_TRUE(status == Status::OK);
    closeSession(sessionId);
}

/**
 * Test that keys are successfully restored to a new session
 * for all content having a policy that allows offline use.
 */
TEST_P(DrmHalVendorPluginTest, RestoreKeys) {
    RETURN_IF_SKIPPED;
    for (auto config : contentConfigurations) {
        if (config.policy.allowOffline) {
            auto sessionId = openSession();
            hidl_vec<uint8_t> keySetId =
                    loadKeys(sessionId, config, KeyType::OFFLINE);
            closeSession(sessionId);
            sessionId = openSession();
            EXPECT_NE(0u, keySetId.size());
            Status status = drmPlugin->restoreKeys(sessionId, keySetId);
            EXPECT_EQ(Status::OK, status);
            closeSession(sessionId);
        }
    }
}

/**
 * Test that restoreKeys fails with a null key set ID.
 * Error message is expected to be Status::BAD_VALUE.
 */
TEST_P(DrmHalVendorPluginTest, RestoreKeysNull) {
    RETURN_IF_SKIPPED;
    SessionId sessionId = openSession();
    hidl_vec<uint8_t> nullKeySetId;
    Status status = drmPlugin->restoreKeys(sessionId, nullKeySetId);
    EXPECT_EQ(Status::BAD_VALUE, status);
    closeSession(sessionId);
}

/**
 * Test that restoreKeys fails to restore keys to a closed
 * session. Error message is expected to be
 * Status::ERROR_DRM_SESSION_NOT_OPENED.
 */
TEST_P(DrmHalVendorPluginTest, RestoreKeysClosedSession) {
    RETURN_IF_SKIPPED;
    for (auto config : contentConfigurations) {
        if (config.policy.allowOffline) {
            auto sessionId = openSession();
            hidl_vec<uint8_t> keySetId =
                    loadKeys(sessionId, config, KeyType::OFFLINE);
            EXPECT_NE(0u, keySetId.size());
            closeSession(sessionId);
            sessionId = openSession();
            closeSession(sessionId);
            Status status = drmPlugin->restoreKeys(sessionId, keySetId);
            EXPECT_EQ(Status::ERROR_DRM_SESSION_NOT_OPENED, status);
        }
    }
}

/**
 * Test that the plugin either doesn't support getting
 * secure stops, or has no secure stops available after
 * clearing them.
 */
TEST_P(DrmHalVendorPluginTest, GetSecureStops) {
    RETURN_IF_SKIPPED;
    // There may be secure stops, depending on if there were keys
    // loaded and unloaded previously. Clear them to get to a known
    // state, then make sure there are none.
    auto res = drmPlugin->getSecureStops(
            [&](Status status, const hidl_vec<SecureStop>&) {
                if (status != Status::OK) {
                    EXPECT_EQ(Status::ERROR_DRM_CANNOT_HANDLE, status);
                }
            });
    EXPECT_OK(res);

    res = drmPlugin->getSecureStops(
            [&](Status status, const hidl_vec<SecureStop>& secureStops) {
                if (status == Status::OK) {
                    EXPECT_EQ(secureStops.size(), 0u);
                } else {
                    EXPECT_EQ(Status::ERROR_DRM_CANNOT_HANDLE, status);
                }
            });
    EXPECT_OK(res);
}

/**
 * Test that the clearkey plugin returns BAD_VALUE if
 * an empty ssid is provided.
 */
TEST_P(DrmHalVendorPluginTest, GetSecureStopEmptySSID) {
    RETURN_IF_SKIPPED;
    SecureStopId ssid;
    auto res = drmPlugin->getSecureStop(
            ssid, [&](Status status, const SecureStop&) {
                EXPECT_EQ(Status::BAD_VALUE, status);
            });
    EXPECT_OK(res);
}

/**
 * Test that releasing all secure stops either isn't supported
 * or is completed successfully
 */
TEST_P(DrmHalVendorPluginTest, ReleaseAllSecureStops) {
    RETURN_IF_SKIPPED;
    Status status = drmPlugin->releaseAllSecureStops();
    EXPECT_TRUE(status == Status::OK ||
                status == Status::ERROR_DRM_CANNOT_HANDLE);
}

/**
 * Releasing a secure stop without first getting one and sending it to the
 * server to get a valid SSID should return ERROR_DRM_INVALID_STATE.
 * This is an optional API so it can also return CANNOT_HANDLE.
 */
TEST_P(DrmHalVendorPluginTest, ReleaseSecureStopSequenceError) {
    RETURN_IF_SKIPPED;
    SecureStopId ssid = {1, 2, 3, 4};
    Status status = drmPlugin->releaseSecureStop(ssid);
    EXPECT_TRUE(status == Status::ERROR_DRM_INVALID_STATE ||
                status == Status::ERROR_DRM_CANNOT_HANDLE);
}

/**
 * Test that releasing a specific secure stop with an empty ssid
 * return BAD_VALUE. This is an optional API so it can also return
 * CANNOT_HANDLE.
 */
TEST_P(DrmHalVendorPluginTest, ReleaseSecureStopEmptySSID) {
    RETURN_IF_SKIPPED;
    SecureStopId ssid;
    Status status = drmPlugin->releaseSecureStop(ssid);
    EXPECT_TRUE(status == Status::BAD_VALUE ||
                status == Status::ERROR_DRM_CANNOT_HANDLE);
}

/**
 * The following five tests verify that the properties
 * defined in the MediaDrm API are supported by
 * the plugin.
 */
TEST_P(DrmHalVendorPluginTest, GetVendorProperty) {
    RETURN_IF_SKIPPED;
    auto res = drmPlugin->getPropertyString(
            "vendor", [&](Status status, const hidl_string& value) {
                EXPECT_EQ(Status::OK, status);
                EXPECT_NE(value.size(), 0u);
            });
    EXPECT_OK(res);
}

TEST_P(DrmHalVendorPluginTest, GetVersionProperty) {
    RETURN_IF_SKIPPED;
    auto res = drmPlugin->getPropertyString(
            "version", [&](Status status, const hidl_string& value) {
                EXPECT_EQ(Status::OK, status);
                EXPECT_NE(value.size(), 0u);
            });
    EXPECT_OK(res);
}

TEST_P(DrmHalVendorPluginTest, GetDescriptionProperty) {
    RETURN_IF_SKIPPED;
    auto res = drmPlugin->getPropertyString(
            "description", [&](Status status, const hidl_string& value) {
                EXPECT_EQ(Status::OK, status);
                EXPECT_NE(value.size(), 0u);
            });
    EXPECT_OK(res);
}

TEST_P(DrmHalVendorPluginTest, GetAlgorithmsProperty) {
    RETURN_IF_SKIPPED;
    auto res = drmPlugin->getPropertyString(
            "algorithms", [&](Status status, const hidl_string& value) {
                if (status == Status::OK) {
                    EXPECT_NE(value.size(), 0u);
                } else {
                    EXPECT_EQ(Status::ERROR_DRM_CANNOT_HANDLE, status);
                }
            });
    EXPECT_OK(res);
}

TEST_P(DrmHalVendorPluginTest, GetPropertyUniqueDeviceID) {
    RETURN_IF_SKIPPED;
    auto res = drmPlugin->getPropertyByteArray(
            "deviceUniqueId",
            [&](Status status, const hidl_vec<uint8_t>& value) {
                if (status == Status::OK) {
                    EXPECT_NE(value.size(), 0u);
                } else {
                    EXPECT_EQ(Status::ERROR_DRM_CANNOT_HANDLE, status);
                }
            });
    EXPECT_OK(res);
}

/**
 * Test that attempting to read invalid string and byte array
 * properties returns the documented error code.
 */
TEST_P(DrmHalVendorPluginTest, GetInvalidStringProperty) {
    RETURN_IF_SKIPPED;
    auto res = drmPlugin->getPropertyString(
            "invalid", [&](Status status, const hidl_string&) {
                EXPECT_EQ(Status::ERROR_DRM_CANNOT_HANDLE, status);
            });
    EXPECT_OK(res);
}

TEST_P(DrmHalVendorPluginTest, GetInvalidByteArrayProperty) {
    RETURN_IF_SKIPPED;
    auto res = drmPlugin->getPropertyByteArray(
            "invalid", [&](Status status, const hidl_vec<uint8_t>&) {
                EXPECT_EQ(Status::ERROR_DRM_CANNOT_HANDLE, status);
            });
    EXPECT_OK(res);
}

/**
 * Test that setting invalid string and byte array properties returns
 * the expected status value.
 */
TEST_P(DrmHalVendorPluginTest, SetStringPropertyNotSupported) {
    RETURN_IF_SKIPPED;
    EXPECT_EQ(drmPlugin->setPropertyString("awefijaeflijwef", "value"),
              Status::ERROR_DRM_CANNOT_HANDLE);
}

TEST_P(DrmHalVendorPluginTest, SetByteArrayPropertyNotSupported) {
    RETURN_IF_SKIPPED;
    hidl_vec<uint8_t> value;
    EXPECT_EQ(drmPlugin->setPropertyByteArray("awefijaeflijwef", value),
              Status::ERROR_DRM_CANNOT_HANDLE);
}

/**
 * Test that setting an invalid cipher algorithm returns
 * the expected status value.
 */
TEST_P(DrmHalVendorPluginTest, SetCipherInvalidAlgorithm) {
    RETURN_IF_SKIPPED;
    SessionId session = openSession();
    hidl_string algorithm;
    Status status = drmPlugin->setCipherAlgorithm(session, algorithm);
    EXPECT_EQ(Status::BAD_VALUE, status);
    closeSession(session);
}

/**
 * Test that setting a cipher algorithm with no session returns
 * the expected status value.
 */
TEST_P(DrmHalVendorPluginTest, SetCipherAlgorithmNoSession) {
    RETURN_IF_SKIPPED;
    SessionId session;
    hidl_string algorithm = "AES/CBC/NoPadding";
    Status status = drmPlugin->setCipherAlgorithm(session, algorithm);
    EXPECT_EQ(Status::BAD_VALUE, status);
}

/**
 * Test that setting a valid cipher algorithm returns
 * the expected status value. It is not required that all
 * vendor modules support this algorithm, but they must
 * either accept it or return ERROR_DRM_CANNOT_HANDLE
 */
TEST_P(DrmHalVendorPluginTest, SetCipherAlgorithm) {
    RETURN_IF_SKIPPED;
    SessionId session = openSession();
    ;
    hidl_string algorithm = "AES/CBC/NoPadding";
    Status status = drmPlugin->setCipherAlgorithm(session, algorithm);
    EXPECT_TRUE(status == Status::OK ||
                status == Status::ERROR_DRM_CANNOT_HANDLE);
    closeSession(session);
}

/**
 * Test that setting an invalid mac algorithm returns
 * the expected status value.
 */
TEST_P(DrmHalVendorPluginTest, SetMacInvalidAlgorithm) {
    RETURN_IF_SKIPPED;
    SessionId session = openSession();
    hidl_string algorithm;
    Status status = drmPlugin->setMacAlgorithm(session, algorithm);
    EXPECT_EQ(Status::BAD_VALUE, status);
    closeSession(session);
}

/**
 * Test that setting a mac algorithm with no session returns
 * the expected status value.
 */
TEST_P(DrmHalVendorPluginTest, SetMacNullAlgorithmNoSession) {
    RETURN_IF_SKIPPED;
    SessionId session;
    hidl_string algorithm = "HmacSHA256";
    Status status = drmPlugin->setMacAlgorithm(session, algorithm);
    EXPECT_EQ(Status::BAD_VALUE, status);
}

/**
 * Test that setting a valid mac algorithm returns
 * the expected status value. It is not required that all
 * vendor modules support this algorithm, but they must
 * either accept it or return ERROR_DRM_CANNOT_HANDLE
 */
TEST_P(DrmHalVendorPluginTest, SetMacAlgorithm) {
    RETURN_IF_SKIPPED;
    SessionId session = openSession();
    hidl_string algorithm = "HmacSHA256";
    Status status = drmPlugin->setMacAlgorithm(session, algorithm);
    EXPECT_TRUE(status == Status::OK ||
                status == Status::ERROR_DRM_CANNOT_HANDLE);
    closeSession(session);
}

/**
 * The Generic* methods provide general purpose crypto operations
 * that may be used for applications other than DRM. They leverage
 * the hardware root of trust and secure key distribution mechanisms
 * of a DRM system to enable app-specific crypto functionality where
 * the crypto keys are not exposed outside of the trusted execution
 * environment.
 *
 * Generic encrypt/decrypt/sign/verify should fail on invalid
 * inputs, e.g. empty sessionId
 */
TEST_P(DrmHalVendorPluginTest, GenericEncryptNoSession) {
    RETURN_IF_SKIPPED;
    SessionId session;
    hidl_vec<uint8_t> keyId, input, iv;
    auto res = drmPlugin->encrypt(
            session, keyId, input, iv,
            [&](Status status, const hidl_vec<uint8_t>&) {
                EXPECT_EQ(Status::ERROR_DRM_SESSION_NOT_OPENED, status);
            });
    EXPECT_OK(res);
}

TEST_P(DrmHalVendorPluginTest, GenericDecryptNoSession) {
    RETURN_IF_SKIPPED;
    SessionId session;
    hidl_vec<uint8_t> keyId, input, iv;
    auto res = drmPlugin->decrypt(
            session, keyId, input, iv,
            [&](Status status, const hidl_vec<uint8_t>&) {
                EXPECT_EQ(Status::ERROR_DRM_SESSION_NOT_OPENED, status);
            });
    EXPECT_OK(res);
}

TEST_P(DrmHalVendorPluginTest, GenericSignNoSession) {
    RETURN_IF_SKIPPED;
    SessionId session;
    hidl_vec<uint8_t> keyId, message;
    auto res = drmPlugin->sign(
            session, keyId, message,
            [&](Status status, const hidl_vec<uint8_t>&) {
                EXPECT_EQ(Status::ERROR_DRM_SESSION_NOT_OPENED, status);
            });
    EXPECT_OK(res);
}

TEST_P(DrmHalVendorPluginTest, GenericVerifyNoSession) {
    RETURN_IF_SKIPPED;
    SessionId session;
    hidl_vec<uint8_t> keyId, message, signature;
    auto res = drmPlugin->verify(
            session, keyId, message, signature, [&](Status status, bool) {
                EXPECT_EQ(Status::ERROR_DRM_SESSION_NOT_OPENED, status);
            });
    EXPECT_OK(res);
}

TEST_P(DrmHalVendorPluginTest, GenericSignRSANoSession) {
    RETURN_IF_SKIPPED;
    SessionId session;
    hidl_string algorithm;
    hidl_vec<uint8_t> message, wrappedKey;
    auto res = drmPlugin->signRSA(session, algorithm, message, wrappedKey,
                                  [&](Status status, const hidl_vec<uint8_t>&) {
                                      EXPECT_EQ(Status::BAD_VALUE, status);
                                  });
    EXPECT_OK(res);
}

/**
 * Exercise the requiresSecureDecoderComponent method. Additional tests
 * will verify positive cases with specific vendor content configurations.
 * Below we just test the negative cases.
 */

/**
 * Verify that requiresSecureDecoderComponent handles empty mimetype.
 */
TEST_P(DrmHalVendorPluginTest, RequiresSecureDecoderEmptyMimeType) {
    RETURN_IF_SKIPPED;
    EXPECT_FALSE(cryptoPlugin->requiresSecureDecoderComponent(""));
}

/**
 * Verify that requiresSecureDecoderComponent handles invalid mimetype.
 */
TEST_P(DrmHalVendorPluginTest, RequiresSecureDecoderInvalidMimeType) {
    RETURN_IF_SKIPPED;
    EXPECT_FALSE(cryptoPlugin->requiresSecureDecoderComponent("bad"));
}

/**
 * Verify that requiresSecureDecoderComponent returns true for secure
 * configurations
 */
TEST_P(DrmHalVendorPluginTest, RequiresSecureDecoderConfig) {
    RETURN_IF_SKIPPED;
    for (auto config : contentConfigurations) {
        for (auto key : config.keys) {
            if (key.isSecure) {
                EXPECT_TRUE(cryptoPlugin->requiresSecureDecoderComponent(config.mimeType));
                break;
            }
        }
    }
}

/**
 *  Event Handling tests
 */
struct ListenerEventArgs {
    EventType eventType;
    SessionId sessionId;
    hidl_vec<uint8_t> data;
    int64_t expiryTimeInMS;
    hidl_vec<KeyStatus> keyStatusList;
    bool hasNewUsableKey;
};

const char *kCallbackEvent = "SendEvent";
const char *kCallbackExpirationUpdate = "SendExpirationUpdate";
const char *kCallbackKeysChange = "SendKeysChange";

class TestDrmPluginListener
    : public ::testing::VtsHalHidlTargetCallbackBase<ListenerEventArgs>,
      public IDrmPluginListener {
public:
    TestDrmPluginListener() {
        SetWaitTimeoutDefault(std::chrono::milliseconds(500));
    }
    virtual ~TestDrmPluginListener() {}

    virtual Return<void> sendEvent(EventType eventType, const hidl_vec<uint8_t>& sessionId,
            const hidl_vec<uint8_t>& data) override {
        ListenerEventArgs args;
        args.eventType = eventType;
        args.sessionId = sessionId;
        args.data = data;
        NotifyFromCallback(kCallbackEvent, args);
        return Void();
    }

    virtual Return<void> sendExpirationUpdate(const hidl_vec<uint8_t>& sessionId,
            int64_t expiryTimeInMS) override {
        ListenerEventArgs args;
        args.sessionId = sessionId;
        args.expiryTimeInMS = expiryTimeInMS;
        NotifyFromCallback(kCallbackExpirationUpdate, args);
        return Void();
    }

    virtual Return<void> sendKeysChange(const hidl_vec<uint8_t>& sessionId,
            const hidl_vec<KeyStatus>& keyStatusList, bool hasNewUsableKey) override {
        ListenerEventArgs args;
        args.sessionId = sessionId;
        args.keyStatusList = keyStatusList;
        args.hasNewUsableKey = hasNewUsableKey;
        NotifyFromCallback(kCallbackKeysChange, args);
        return Void();
    }
};


/**
 * Simulate the plugin sending events. Make sure the listener
 * gets them.
 */
TEST_P(DrmHalVendorPluginTest, ListenerEvents) {
    RETURN_IF_SKIPPED;
    sp<TestDrmPluginListener> listener = new TestDrmPluginListener();
    drmPlugin->setListener(listener);
    auto sessionId = openSession();
    hidl_vec<uint8_t> data = {0, 1, 2};
    EventType eventTypes[] = {EventType::PROVISION_REQUIRED,
                              EventType::KEY_NEEDED,
                              EventType::KEY_EXPIRED,
                              EventType::VENDOR_DEFINED,
                              EventType::SESSION_RECLAIMED};
    for (auto eventType : eventTypes) {
        drmPlugin->sendEvent(eventType, sessionId, data);
        auto result = listener->WaitForCallback(kCallbackEvent);
        EXPECT_TRUE(result.no_timeout);
        EXPECT_TRUE(result.args);
        EXPECT_EQ(eventType, result.args->eventType);
        EXPECT_EQ(sessionId, result.args->sessionId);
        EXPECT_EQ(data, result.args->data);
    }
    closeSession(sessionId);
}

/**
 * Simulate the plugin sending expiration updates and make sure
 * the listener gets them.
 */
TEST_P(DrmHalVendorPluginTest, ListenerExpirationUpdate) {
    RETURN_IF_SKIPPED;
    sp<TestDrmPluginListener> listener = new TestDrmPluginListener();
    drmPlugin->setListener(listener);
    auto sessionId = openSession();
    drmPlugin->sendExpirationUpdate(sessionId, 100);
    auto result = listener->WaitForCallback(kCallbackExpirationUpdate);
    EXPECT_TRUE(result.no_timeout);
    EXPECT_TRUE(result.args);
    EXPECT_EQ(sessionId, result.args->sessionId);
    EXPECT_EQ(100, result.args->expiryTimeInMS);
    closeSession(sessionId);
}

/**
 * Simulate the plugin sending keys change and make sure
 * the listener gets them.
 */
TEST_P(DrmHalVendorPluginTest, ListenerKeysChange) {
    RETURN_IF_SKIPPED;
    sp<TestDrmPluginListener> listener = new TestDrmPluginListener();
    drmPlugin->setListener(listener);
    auto sessionId = openSession();
    const hidl_vec<KeyStatus> keyStatusList = {
        {{1}, KeyStatusType::USABLE},
        {{2}, KeyStatusType::EXPIRED},
        {{3}, KeyStatusType::OUTPUTNOTALLOWED},
        {{4}, KeyStatusType::STATUSPENDING},
        {{5}, KeyStatusType::INTERNALERROR},
    };

    drmPlugin->sendKeysChange(sessionId, keyStatusList, true);
    auto result = listener->WaitForCallback(kCallbackKeysChange);
    EXPECT_TRUE(result.no_timeout);
    EXPECT_TRUE(result.args);
    EXPECT_EQ(sessionId, result.args->sessionId);
    EXPECT_EQ(keyStatusList, result.args->keyStatusList);
    closeSession(sessionId);
}

/**
 * Negative listener tests. Call send methods with no
 * listener set.
 */
TEST_P(DrmHalVendorPluginTest, NotListening) {
    RETURN_IF_SKIPPED;
    sp<TestDrmPluginListener> listener = new TestDrmPluginListener();
    drmPlugin->setListener(listener);
    drmPlugin->setListener(nullptr);

    SessionId sessionId;
    hidl_vec<uint8_t> data;
    hidl_vec<KeyStatus> keyStatusList;
    drmPlugin->sendEvent(EventType::PROVISION_REQUIRED, sessionId, data);
    drmPlugin->sendExpirationUpdate(sessionId, 100);
    drmPlugin->sendKeysChange(sessionId, keyStatusList, true);
    auto result = listener->WaitForCallbackAny(
            {kCallbackEvent, kCallbackExpirationUpdate, kCallbackKeysChange});
    EXPECT_FALSE(result.no_timeout);
}


/**
 *  CryptoPlugin tests
 */

/**
 * Exercise the NotifyResolution API. There is no observable result,
 * just call the method for coverage.
 */
TEST_P(DrmHalVendorPluginTest, NotifyResolution) {
    RETURN_IF_SKIPPED;
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
sp<IMemory> DrmHalVendorPluginTest::getDecryptMemory(size_t size,
                                                     size_t index) {
    sp<IAllocator> ashmemAllocator = IAllocator::getService("ashmem");
    EXPECT_NE(nullptr, ashmemAllocator.get());

    hidl_memory hidlMemory;
    auto res = ashmemAllocator->allocate(
            size, [&](bool success, const hidl_memory& memory) {
                EXPECT_EQ(success, true);
                EXPECT_EQ(memory.size(), size);
                hidlMemory = memory;
            });

    EXPECT_OK(res);

    sp<IMemory> mappedMemory = mapMemory(hidlMemory);
    EXPECT_NE(nullptr, mappedMemory.get());
    res = cryptoPlugin->setSharedBufferBase(hidlMemory, index);
    EXPECT_OK(res);
    return mappedMemory;
}

/**
 * Exercise the setMediaDrmSession method. setMediaDrmSession
 * is used to associate a drm session with a crypto session.
 */
TEST_P(DrmHalVendorPluginTest, SetMediaDrmSession) {
    RETURN_IF_SKIPPED;
    auto sessionId = openSession();
    Status status = cryptoPlugin->setMediaDrmSession(sessionId);
    EXPECT_EQ(Status::OK, status);
    closeSession(sessionId);
}

/**
 * setMediaDrmSession with a closed session id
 */
TEST_P(DrmHalVendorPluginTest, SetMediaDrmSessionClosedSession) {
    RETURN_IF_SKIPPED;
    auto sessionId = openSession();
    closeSession(sessionId);
    Status status = cryptoPlugin->setMediaDrmSession(sessionId);
    EXPECT_EQ(Status::ERROR_DRM_SESSION_NOT_OPENED, status);
}

/**
 * setMediaDrmSession with a empty session id: BAD_VALUE
 */
TEST_P(DrmHalVendorPluginTest, SetMediaDrmSessionEmptySession) {
    RETURN_IF_SKIPPED;
    SessionId sessionId;
    Status status = cryptoPlugin->setMediaDrmSession(sessionId);
    EXPECT_EQ(Status::BAD_VALUE, status);
}

/**
 * Decrypt tests
 */

class DrmHalVendorDecryptTest : public DrmHalVendorPluginTest {
   public:
    DrmHalVendorDecryptTest() = default;
    virtual ~DrmHalVendorDecryptTest() {}

   protected:
    void fillRandom(const sp<IMemory>& memory);
    hidl_array<uint8_t, 16> toHidlArray(const vector<uint8_t>& vec) {
        EXPECT_EQ(vec.size(), 16u);
        return hidl_array<uint8_t, 16>(&vec[0]);
    }
    hidl_vec<KeyValue> queryKeyStatus(SessionId sessionId);
    void removeKeys(SessionId sessionId);
    uint32_t decrypt(Mode mode, bool isSecure,
            const hidl_array<uint8_t, 16>& keyId, uint8_t* iv,
            const hidl_vec<SubSample>& subSamples, const Pattern& pattern,
            const vector<uint8_t>& key, Status expectedStatus);
    void aes_ctr_decrypt(uint8_t* dest, uint8_t* src, uint8_t* iv,
            const hidl_vec<SubSample>& subSamples, const vector<uint8_t>& key);
    void aes_cbc_decrypt(uint8_t* dest, uint8_t* src, uint8_t* iv,
            const hidl_vec<SubSample>& subSamples, const vector<uint8_t>& key);
};

void DrmHalVendorDecryptTest::fillRandom(const sp<IMemory>& memory) {
    random_device rd;
    mt19937 rand(rd());
    for (size_t i = 0; i < memory->getSize() / sizeof(uint32_t); i++) {
        auto p = static_cast<uint32_t*>(
                static_cast<void*>(memory->getPointer()));
        p[i] = rand();
    }
}

hidl_vec<KeyValue> DrmHalVendorDecryptTest::queryKeyStatus(SessionId sessionId) {
    hidl_vec<KeyValue> keyStatus;
    auto res = drmPlugin->queryKeyStatus(sessionId,
            [&](Status status, KeyedVector info) {
                EXPECT_EQ(Status::OK, status);
                keyStatus = info;
            });
    EXPECT_OK(res);
    return keyStatus;
}

void DrmHalVendorDecryptTest::removeKeys(SessionId sessionId) {
    auto res = drmPlugin->removeKeys(sessionId);
    EXPECT_OK(res);
}

uint32_t DrmHalVendorDecryptTest::decrypt(Mode mode, bool isSecure,
        const hidl_array<uint8_t, 16>& keyId, uint8_t* iv,
        const hidl_vec<SubSample>& subSamples, const Pattern& pattern,
        const vector<uint8_t>& key, Status expectedStatus) {
    const size_t kSegmentIndex = 0;

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

    SharedBuffer sourceBuffer = {
            .bufferId = kSegmentIndex, .offset = 0, .size = totalSize};
    fillRandom(sharedMemory);

    DestinationBuffer destBuffer = {.type = BufferType::SHARED_MEMORY,
                                    {.bufferId = kSegmentIndex,
                                     .offset = totalSize,
                                     .size = totalSize},
                                    .secureMemory = nullptr};
    uint64_t offset = 0;
    uint32_t bytesWritten = 0;
    auto res = cryptoPlugin->decrypt(isSecure, keyId, localIv, mode, pattern,
            subSamples, sourceBuffer, offset, destBuffer,
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
        aes_ctr_decrypt(&reference[0], base, localIv, subSamples, key);
        break;
    case Mode::AES_CBC:
        aes_cbc_decrypt(&reference[0], base, localIv, subSamples, key);
        break;
    case Mode::AES_CBC_CTS:
        EXPECT_TRUE(false) << "AES_CBC_CTS mode not supported";
        break;
    }

    // compare reference to decrypted data which is at base + total size
    EXPECT_EQ(0, memcmp(static_cast<void*>(&reference[0]),
                        static_cast<void*>(base + totalSize), totalSize))
            << "decrypt data mismatch";
    return totalSize;
}

/**
 * Decrypt a list of clear+encrypted subsamples using the specified key
 * in AES-CTR mode
 */
void DrmHalVendorDecryptTest::aes_ctr_decrypt(uint8_t* dest, uint8_t* src,
        uint8_t* iv, const hidl_vec<SubSample>& subSamples,
        const vector<uint8_t>& key) {

    AES_KEY decryptionKey;
    AES_set_encrypt_key(&key[0], 128, &decryptionKey);

    size_t offset = 0;
    unsigned blockOffset = 0;
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
void DrmHalVendorDecryptTest::aes_cbc_decrypt(uint8_t* dest, uint8_t* src,
        uint8_t* iv, const hidl_vec<SubSample>& subSamples,
        const vector<uint8_t>& key) {
    AES_KEY decryptionKey;
    AES_set_encrypt_key(&key[0], 128, &decryptionKey);

    size_t offset = 0;
    for (size_t i = 0; i < subSamples.size(); i++) {
        const SubSample& subSample = subSamples[i];

        memcpy(dest + offset, src + offset, subSample.numBytesOfClearData);
        offset += subSample.numBytesOfClearData;

        AES_cbc_encrypt(src + offset, dest + offset, subSample.numBytesOfEncryptedData,
                &decryptionKey, iv, 0 /* decrypt */);
        offset += subSample.numBytesOfEncryptedData;
    }
}


/**
 * Test key status with empty session id, should return BAD_VALUE
 */
TEST_P(DrmHalVendorDecryptTest, QueryKeyStatusInvalidSession) {
    RETURN_IF_SKIPPED;
    SessionId sessionId;
    auto res = drmPlugin->queryKeyStatus(sessionId,
            [&](Status status, KeyedVector /* info */) {
                EXPECT_EQ(Status::BAD_VALUE, status);
            });
    EXPECT_OK(res);
}


/**
 * Test key status.  There should be no key status prior to loading keys
 */
TEST_P(DrmHalVendorDecryptTest, QueryKeyStatusWithNoKeys) {
    RETURN_IF_SKIPPED;
    auto sessionId = openSession();
    auto keyStatus = queryKeyStatus(sessionId);
    EXPECT_EQ(0u, keyStatus.size());
    closeSession(sessionId);
}


/**
 * Test key status.  There should be key status after loading keys.
 */
TEST_P(DrmHalVendorDecryptTest, QueryKeyStatus) {
    RETURN_IF_SKIPPED;
    for (auto config : contentConfigurations) {
        auto sessionId = openSession();
        loadKeys(sessionId, config);
        auto keyStatus = queryKeyStatus(sessionId);
        EXPECT_NE(0u, keyStatus.size());
        closeSession(sessionId);
    }
}

/**
 * Positive decrypt test. "Decrypt" a single clear segment and verify.
 */
TEST_P(DrmHalVendorDecryptTest, ClearSegmentTest) {
    RETURN_IF_SKIPPED;
    for (auto config : contentConfigurations) {
        for (auto key : config.keys) {
            const size_t kSegmentSize = 1024;
            vector<uint8_t> iv(AES_BLOCK_SIZE, 0);
            const Pattern noPattern = {0, 0};
            const vector<SubSample> subSamples = {{.numBytesOfClearData = kSegmentSize,
                                                   .numBytesOfEncryptedData = 0}};
            auto sessionId = openSession();
            loadKeys(sessionId, config);

            Status status = cryptoPlugin->setMediaDrmSession(sessionId);
            EXPECT_EQ(Status::OK, status);

            uint32_t byteCount = decrypt(Mode::UNENCRYPTED, key.isSecure, toHidlArray(key.keyId),
                    &iv[0], subSamples, noPattern, key.clearContentKey, Status::OK);
            EXPECT_EQ(kSegmentSize, byteCount);

            closeSession(sessionId);
        }
    }
}

/**
 * Positive decrypt test.  Decrypt a single segment using aes_ctr.
 * Verify data matches.
 */
TEST_P(DrmHalVendorDecryptTest, EncryptedAesCtrSegmentTest) {
    RETURN_IF_SKIPPED;
    for (auto config : contentConfigurations) {
        for (auto key : config.keys) {
            const size_t kSegmentSize = 1024;
            vector<uint8_t> iv(AES_BLOCK_SIZE, 0);
            const Pattern noPattern = {0, 0};
            const vector<SubSample> subSamples = {{.numBytesOfClearData = kSegmentSize,
                                                   .numBytesOfEncryptedData = 0}};
            auto sessionId = openSession();
            loadKeys(sessionId, config);

            Status status = cryptoPlugin->setMediaDrmSession(sessionId);
            EXPECT_EQ(Status::OK, status);

            uint32_t byteCount = decrypt(Mode::AES_CTR, key.isSecure, toHidlArray(key.keyId),
                    &iv[0], subSamples, noPattern, key.clearContentKey, Status::OK);
            EXPECT_EQ(kSegmentSize, byteCount);

            closeSession(sessionId);
        }
    }
}

/**
 * Negative decrypt test. Decrypt without loading keys.
 */
TEST_P(DrmHalVendorDecryptTest, EncryptedAesCtrSegmentTestNoKeys) {
    RETURN_IF_SKIPPED;
    for (auto config : contentConfigurations) {
        for (auto key : config.keys) {
            vector<uint8_t> iv(AES_BLOCK_SIZE, 0);
            const Pattern noPattern = {0, 0};
            const vector<SubSample> subSamples = {{.numBytesOfClearData = 256,
                                                   .numBytesOfEncryptedData = 256}};
            auto sessionId = openSession();

            Status status = cryptoPlugin->setMediaDrmSession(sessionId);
            EXPECT_EQ(Status::OK, status);

            uint32_t byteCount = decrypt(Mode::AES_CTR, key.isSecure,
                    toHidlArray(key.keyId), &iv[0], subSamples, noPattern,
                    key.clearContentKey, Status::ERROR_DRM_NO_LICENSE);
            EXPECT_EQ(0u, byteCount);

            closeSession(sessionId);
        }
    }
}

/**
 * Test key removal.  Load keys then remove them and verify that
 * decryption can't be performed.
 */
TEST_P(DrmHalVendorDecryptTest, AttemptDecryptWithKeysRemoved) {
    RETURN_IF_SKIPPED;
    for (auto config : contentConfigurations) {
        for (auto key : config.keys) {
            vector<uint8_t> iv(AES_BLOCK_SIZE, 0);
            const Pattern noPattern = {0, 0};
            const vector<SubSample> subSamples = {{.numBytesOfClearData = 256,
                                                   .numBytesOfEncryptedData = 256}};
            auto sessionId = openSession();

            Status status = cryptoPlugin->setMediaDrmSession(sessionId);
            EXPECT_EQ(Status::OK, status);

            loadKeys(sessionId, config);
            removeKeys(sessionId);

            uint32_t byteCount = decrypt(Mode::AES_CTR, key.isSecure,
                    toHidlArray(key.keyId), &iv[0], subSamples, noPattern,
                    key.clearContentKey, Status::ERROR_DRM_NO_LICENSE);
            EXPECT_EQ(0u, byteCount);

            closeSession(sessionId);
        }
    }
}


/**
 * Instantiate the set of test cases for each vendor module
 */

INSTANTIATE_TEST_CASE_P(
        DrmHalVendorFactoryTestCases, DrmHalVendorFactoryTest,
        testing::ValuesIn(gVendorModules->getPathList()));

INSTANTIATE_TEST_CASE_P(
        DrmHalVendorPluginTestCases, DrmHalVendorPluginTest,
        testing::ValuesIn(gVendorModules->getPathList()));

INSTANTIATE_TEST_CASE_P(
        DrmHalVendorDecryptTestCases, DrmHalVendorDecryptTest,
        testing::ValuesIn(gVendorModules->getPathList()));

int main(int argc, char** argv) {
#if defined(__LP64__)
    const char* kModulePath = "/data/local/tmp/64/lib";
#else
    const char* kModulePath = "/data/local/tmp/32/lib";
#endif
    gVendorModules = new drm_vts::VendorModules(kModulePath);
    if (gVendorModules->getPathList().size() == 0) {
        std::cerr << "WARNING: No vendor modules found in " << kModulePath <<
                ", all vendor tests will be skipped" << std::endl;
    }
    ::testing::InitGoogleTest(&argc, argv);
    return RUN_ALL_TESTS();
}
