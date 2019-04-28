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

#ifndef ANDROID_HARDWARE_DRM_V1_0__DRMPLUGIN_H
#define ANDROID_HARDWARE_DRM_V1_0__DRMPLUGIN_H

#include <android/hardware/drm/1.0/IDrmPlugin.h>
#include <android/hardware/drm/1.0/IDrmPluginListener.h>
#include <hidl/Status.h>
#include <media/drm/DrmAPI.h>

namespace android {
namespace hardware {
namespace drm {
namespace V1_0 {
namespace implementation {

using ::android::hardware::drm::V1_0::EventType;
using ::android::hardware::drm::V1_0::IDrmPlugin;
using ::android::hardware::drm::V1_0::IDrmPluginListener;
using ::android::hardware::drm::V1_0::KeyRequestType;
using ::android::hardware::drm::V1_0::KeyStatus;
using ::android::hardware::drm::V1_0::KeyType;
using ::android::hardware::drm::V1_0::KeyValue;
using ::android::hardware::drm::V1_0::SecureStop;
using ::android::hardware::hidl_array;
using ::android::hardware::hidl_string;
using ::android::hardware::hidl_vec;
using ::android::hardware::Return;
using ::android::hardware::Void;
using ::android::sp;

struct DrmPlugin : public IDrmPlugin, android::DrmPluginListener {

    DrmPlugin(android::DrmPlugin *plugin) : mLegacyPlugin(plugin) {}
    ~DrmPlugin() {delete mLegacyPlugin;}

    // Methods from ::android::hardware::drm::V1_0::IDrmPlugin follow.

    Return<void> openSession(openSession_cb _hidl_cb) override;

    Return<Status> closeSession(const hidl_vec<uint8_t>& sessionId) override;

    Return<void> getKeyRequest(const hidl_vec<uint8_t>& scope,
            const hidl_vec<uint8_t>& initData, const hidl_string& mimeType,
            KeyType keyType, const hidl_vec<KeyValue>& optionalParameters,
            getKeyRequest_cb _hidl_cb) override;

    Return<void> provideKeyResponse(const hidl_vec<uint8_t>& scope,
            const hidl_vec<uint8_t>& response, provideKeyResponse_cb _hidl_cb)
            override;

    Return<Status> removeKeys(const hidl_vec<uint8_t>& sessionId) override;

    Return<Status> restoreKeys(const hidl_vec<uint8_t>& sessionId,
            const hidl_vec<uint8_t>& keySetId) override;

    Return<void> queryKeyStatus(const hidl_vec<uint8_t>& sessionId,
            queryKeyStatus_cb _hidl_cb) override;

    Return<void> getProvisionRequest(const hidl_string& certificateType,
            const hidl_string& certificateAuthority,
            getProvisionRequest_cb _hidl_cb) override;

    Return<void> provideProvisionResponse(const hidl_vec<uint8_t>& response,
            provideProvisionResponse_cb _hidl_cb) override;

    Return<void> getSecureStops(getSecureStops_cb _hidl_cb) override;

    Return<void> getSecureStop(const hidl_vec<uint8_t>& secureStopId,
            getSecureStop_cb _hidl_cb) override;

    Return<Status> releaseAllSecureStops() override;

    Return<Status> releaseSecureStop(const hidl_vec<uint8_t>& secureStopId)
            override;

    Return<void> getPropertyString(const hidl_string& propertyName,
            getPropertyString_cb _hidl_cb) override;

    Return<void> getPropertyByteArray(const hidl_string& propertyName,
            getPropertyByteArray_cb _hidl_cb) override;

    Return<Status> setPropertyString(const hidl_string& propertyName,
            const hidl_string& value) override;

    Return<Status> setPropertyByteArray(const hidl_string& propertyName,
            const hidl_vec<uint8_t>& value) override;

    Return<Status> setCipherAlgorithm(const hidl_vec<uint8_t>& sessionId,
            const hidl_string& algorithm) override;

    Return<Status> setMacAlgorithm(const hidl_vec<uint8_t>& sessionId,
            const hidl_string& algorithm) override;

    Return<void> encrypt(const hidl_vec<uint8_t>& sessionId,
            const hidl_vec<uint8_t>& keyId, const hidl_vec<uint8_t>& input,
            const hidl_vec<uint8_t>& iv, encrypt_cb _hidl_cb) override;

    Return<void> decrypt(const hidl_vec<uint8_t>& sessionId,
            const hidl_vec<uint8_t>& keyId, const hidl_vec<uint8_t>& input,
            const hidl_vec<uint8_t>& iv, decrypt_cb _hidl_cb) override;

    Return<void> sign(const hidl_vec<uint8_t>& sessionId,
            const hidl_vec<uint8_t>& keyId, const hidl_vec<uint8_t>& message,
            sign_cb _hidl_cb) override;

    Return<void> verify(const hidl_vec<uint8_t>& sessionId,
            const hidl_vec<uint8_t>& keyId, const hidl_vec<uint8_t>& message,
            const hidl_vec<uint8_t>& signature, verify_cb _hidl_cb) override;

    Return<void> signRSA(const hidl_vec<uint8_t>& sessionId,
            const hidl_string& algorithm, const hidl_vec<uint8_t>& message,
            const hidl_vec<uint8_t>& wrappedkey, signRSA_cb _hidl_cb) override;

    Return<void> setListener(const sp<IDrmPluginListener>& listener) override;

    Return<void> sendEvent(EventType eventType,
            const hidl_vec<uint8_t>& sessionId, const hidl_vec<uint8_t>& data)
            override;

    Return<void> sendExpirationUpdate(const hidl_vec<uint8_t>& sessionId,
            int64_t expiryTimeInMS) override;

    Return<void> sendKeysChange(const hidl_vec<uint8_t>& sessionId,
            const hidl_vec<KeyStatus>& keyStatusList, bool hasNewUsableKey)
            override;

    // Methods from android::DrmPluginListener follow

    virtual void sendEvent(android::DrmPlugin::EventType eventType, int extra,
            Vector<uint8_t> const *sessionId, Vector<uint8_t> const *data);

    virtual void sendExpirationUpdate(Vector<uint8_t> const *sessionId,
            int64_t expiryTimeInMS);

    virtual void sendKeysChange(Vector<uint8_t> const *sessionId,
            Vector<android::DrmPlugin::KeyStatus> const *keyStatusList,
            bool hasNewUsableKey);

private:
    android::DrmPlugin *mLegacyPlugin;
    sp<IDrmPluginListener> mListener;

    DrmPlugin() = delete;
    DrmPlugin(const DrmPlugin &) = delete;
    void operator=(const DrmPlugin &) = delete;
};

}  // namespace implementation
}  // namespace V1_0
}  // namespace drm
}  // namespace hardware
}  // namespace android

#endif  // ANDROID_HARDWARE_DRM_V1_0__DRMPLUGIN_H
