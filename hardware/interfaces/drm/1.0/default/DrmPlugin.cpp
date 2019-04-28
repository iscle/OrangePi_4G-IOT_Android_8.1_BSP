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
#define LOG_TAG "android.hardware.drm@1.0-impl"

#include <utils/KeyedVector.h>
#include <utils/String8.h>

#include "DrmPlugin.h"
#include "TypeConvert.h"

namespace android {
namespace hardware {
namespace drm {
namespace V1_0 {
namespace implementation {

    // Methods from ::android::hardware::drm::V1_0::IDrmPlugin follow.

    Return<void> DrmPlugin::openSession(openSession_cb _hidl_cb) {
        Vector<uint8_t> legacySessionId;
        status_t status = mLegacyPlugin->openSession(legacySessionId);
        _hidl_cb(toStatus(status), toHidlVec(legacySessionId));
        return Void();
    }

    Return<Status> DrmPlugin::closeSession(const hidl_vec<uint8_t>& sessionId) {
        return toStatus(mLegacyPlugin->closeSession(toVector(sessionId)));
    }

    Return<void> DrmPlugin::getKeyRequest(const hidl_vec<uint8_t>& scope,
            const hidl_vec<uint8_t>& initData, const hidl_string& mimeType,
            KeyType keyType, const hidl_vec<KeyValue>& optionalParameters,
            getKeyRequest_cb _hidl_cb) {

        status_t status = android::OK;

        android::DrmPlugin::KeyType legacyKeyType;
        switch(keyType) {
        case KeyType::OFFLINE:
            legacyKeyType = android::DrmPlugin::kKeyType_Offline;
            break;
        case KeyType::STREAMING:
            legacyKeyType = android::DrmPlugin::kKeyType_Streaming;
            break;
        case KeyType::RELEASE:
            legacyKeyType = android::DrmPlugin::kKeyType_Release;
            break;
        default:
            status = android::BAD_VALUE;
            break;
        }

        Vector<uint8_t> legacyRequest;
        KeyRequestType requestType = KeyRequestType::UNKNOWN;
        String8 defaultUrl;

        if (status == android::OK) {
            android::KeyedVector<String8, String8> legacyOptionalParameters;
            for (size_t i = 0; i < optionalParameters.size(); i++) {
                legacyOptionalParameters.add(String8(optionalParameters[i].key.c_str()),
                        String8(optionalParameters[i].value.c_str()));
            }

            android::DrmPlugin::KeyRequestType legacyRequestType =
                    android::DrmPlugin::kKeyRequestType_Unknown;

            status = mLegacyPlugin->getKeyRequest(toVector(scope),
                    toVector(initData), String8(mimeType.c_str()), legacyKeyType,
                    legacyOptionalParameters, legacyRequest, defaultUrl,
                    &legacyRequestType);

            switch(legacyRequestType) {
            case android::DrmPlugin::kKeyRequestType_Initial:
                requestType = KeyRequestType::INITIAL;
                break;
            case android::DrmPlugin::kKeyRequestType_Renewal:
                requestType = KeyRequestType::RENEWAL;
                break;
            case android::DrmPlugin::kKeyRequestType_Release:
                requestType = KeyRequestType::RELEASE;
                break;
            case android::DrmPlugin::kKeyRequestType_Unknown:
                requestType = KeyRequestType::UNKNOWN;
                break;
            }
        }
        _hidl_cb(toStatus(status), toHidlVec(legacyRequest), requestType,
                 defaultUrl.string());
        return Void();
    }

    Return<void> DrmPlugin::provideKeyResponse(const hidl_vec<uint8_t>& scope,
            const hidl_vec<uint8_t>& response, provideKeyResponse_cb _hidl_cb) {

        Vector<uint8_t> keySetId;
        status_t status = mLegacyPlugin->provideKeyResponse(toVector(scope),
                toVector(response), keySetId);
        _hidl_cb(toStatus(status), toHidlVec(keySetId));
        return Void();
    }

    Return<Status> DrmPlugin::removeKeys(const hidl_vec<uint8_t>& sessionId) {
        return toStatus(mLegacyPlugin->removeKeys(toVector(sessionId)));
    }

    Return<Status> DrmPlugin::restoreKeys(const hidl_vec<uint8_t>& sessionId,
            const hidl_vec<uint8_t>& keySetId) {
        status_t legacyStatus = mLegacyPlugin->restoreKeys(toVector(sessionId),
                toVector(keySetId));
        return toStatus(legacyStatus);
    }

    Return<void> DrmPlugin::queryKeyStatus(const hidl_vec<uint8_t>& sessionId,
            queryKeyStatus_cb _hidl_cb) {

        android::KeyedVector<String8, String8> legacyInfoMap;
        status_t status = mLegacyPlugin->queryKeyStatus(toVector(sessionId),
                legacyInfoMap);

        Vector<KeyValue> infoMapVec;
        for (size_t i = 0; i < legacyInfoMap.size(); i++) {
            KeyValue keyValuePair;
            keyValuePair.key = String8(legacyInfoMap.keyAt(i));
            keyValuePair.value = String8(legacyInfoMap.valueAt(i));
            infoMapVec.push_back(keyValuePair);
        }
        _hidl_cb(toStatus(status), toHidlVec(infoMapVec));
        return Void();
    }

    Return<void> DrmPlugin::getProvisionRequest(
            const hidl_string& certificateType,
            const hidl_string& certificateAuthority,
            getProvisionRequest_cb _hidl_cb) {

        Vector<uint8_t> legacyRequest;
        String8 legacyDefaultUrl;
        status_t status = mLegacyPlugin->getProvisionRequest(
                String8(certificateType.c_str()), String8(certificateAuthority.c_str()),
                legacyRequest, legacyDefaultUrl);

        _hidl_cb(toStatus(status), toHidlVec(legacyRequest),
                hidl_string(legacyDefaultUrl));
        return Void();
    }

    Return<void> DrmPlugin::provideProvisionResponse(
            const hidl_vec<uint8_t>& response,
            provideProvisionResponse_cb _hidl_cb) {

        Vector<uint8_t> certificate;
        Vector<uint8_t> wrappedKey;

        status_t legacyStatus = mLegacyPlugin->provideProvisionResponse(
                toVector(response), certificate, wrappedKey);

        _hidl_cb(toStatus(legacyStatus), toHidlVec(certificate),
                toHidlVec(wrappedKey));
        return Void();
    }

    Return<void> DrmPlugin::getSecureStops(getSecureStops_cb _hidl_cb) {
        List<Vector<uint8_t> > legacySecureStops;
        status_t status = mLegacyPlugin->getSecureStops(legacySecureStops);

        Vector<SecureStop> secureStopsVec;
        List<Vector<uint8_t> >::iterator iter = legacySecureStops.begin();

        while (iter != legacySecureStops.end()) {
            SecureStop secureStop;
            secureStop.opaqueData = toHidlVec(*iter++);
            secureStopsVec.push_back(secureStop);
        }

        _hidl_cb(toStatus(status), toHidlVec(secureStopsVec));
        return Void();
    }

    Return<void> DrmPlugin::getSecureStop(const hidl_vec<uint8_t>& secureStopId,
            getSecureStop_cb _hidl_cb) {

        Vector<uint8_t> legacySecureStop;
        status_t status = mLegacyPlugin->getSecureStop(toVector(secureStopId),
                legacySecureStop);

        SecureStop secureStop;
        secureStop.opaqueData = toHidlVec(legacySecureStop);
        _hidl_cb(toStatus(status), secureStop);
        return Void();
    }

    Return<Status> DrmPlugin::releaseAllSecureStops() {
        return toStatus(mLegacyPlugin->releaseAllSecureStops());
    }

    Return<Status> DrmPlugin::releaseSecureStop(
            const hidl_vec<uint8_t>& secureStopId) {
        status_t legacyStatus =
            mLegacyPlugin->releaseSecureStops(toVector(secureStopId));
        return toStatus(legacyStatus);
    }

    Return<void> DrmPlugin::getPropertyString(const hidl_string& propertyName,
            getPropertyString_cb _hidl_cb) {
        String8 legacyValue;
        status_t status = mLegacyPlugin->getPropertyString(
                String8(propertyName.c_str()), legacyValue);
        _hidl_cb(toStatus(status), legacyValue.string());
        return Void();
    }

    Return<void> DrmPlugin::getPropertyByteArray(const hidl_string& propertyName,
            getPropertyByteArray_cb _hidl_cb) {
        Vector<uint8_t> legacyValue;
        status_t status = mLegacyPlugin->getPropertyByteArray(
                String8(propertyName.c_str()), legacyValue);
        _hidl_cb(toStatus(status), toHidlVec(legacyValue));
        return Void();
    }

    Return<Status> DrmPlugin::setPropertyString(const hidl_string& propertyName,
            const hidl_string& value) {
        status_t legacyStatus =
            mLegacyPlugin->setPropertyString(String8(propertyName.c_str()),
                    String8(value.c_str()));
        return toStatus(legacyStatus);
    }

    Return<Status> DrmPlugin::setPropertyByteArray(
            const hidl_string& propertyName, const hidl_vec<uint8_t>& value) {
        status_t legacyStatus =
            mLegacyPlugin->setPropertyByteArray(String8(propertyName.c_str()),
                    toVector(value));
        return toStatus(legacyStatus);
    }

    Return<Status> DrmPlugin::setCipherAlgorithm(
            const hidl_vec<uint8_t>& sessionId, const hidl_string& algorithm) {
        status_t legacyStatus =
            mLegacyPlugin->setCipherAlgorithm(toVector(sessionId),
                String8(algorithm.c_str()));
        return toStatus(legacyStatus);
    }

    Return<Status> DrmPlugin::setMacAlgorithm(
            const hidl_vec<uint8_t>& sessionId, const hidl_string& algorithm) {
        status_t legacyStatus =
            mLegacyPlugin->setMacAlgorithm(toVector(sessionId),
                String8(algorithm.c_str()));
        return toStatus(legacyStatus);
    }

    Return<void> DrmPlugin::encrypt(const hidl_vec<uint8_t>& sessionId,
            const hidl_vec<uint8_t>& keyId, const hidl_vec<uint8_t>& input,
            const hidl_vec<uint8_t>& iv, encrypt_cb _hidl_cb) {

        Vector<uint8_t> legacyOutput;
        status_t status = mLegacyPlugin->encrypt(toVector(sessionId),
                toVector(keyId), toVector(input), toVector(iv), legacyOutput);
        _hidl_cb(toStatus(status), toHidlVec(legacyOutput));
        return Void();
    }

    Return<void> DrmPlugin::decrypt(const hidl_vec<uint8_t>& sessionId,
            const hidl_vec<uint8_t>& keyId, const hidl_vec<uint8_t>& input,
            const hidl_vec<uint8_t>& iv, decrypt_cb _hidl_cb) {

        Vector<uint8_t> legacyOutput;
        status_t status = mLegacyPlugin->decrypt(toVector(sessionId),
                toVector(keyId), toVector(input), toVector(iv), legacyOutput);
        _hidl_cb(toStatus(status), toHidlVec(legacyOutput));
        return Void();
    }

    Return<void> DrmPlugin::sign(const hidl_vec<uint8_t>& sessionId,
            const hidl_vec<uint8_t>& keyId, const hidl_vec<uint8_t>& message,
            sign_cb _hidl_cb) {
        Vector<uint8_t> legacySignature;
        status_t status = mLegacyPlugin->sign(toVector(sessionId),
                toVector(keyId), toVector(message), legacySignature);
        _hidl_cb(toStatus(status), toHidlVec(legacySignature));
        return Void();
    }

    Return<void> DrmPlugin::verify(const hidl_vec<uint8_t>& sessionId,
            const hidl_vec<uint8_t>& keyId, const hidl_vec<uint8_t>& message,
            const hidl_vec<uint8_t>& signature, verify_cb _hidl_cb) {

        bool match;
        status_t status = mLegacyPlugin->verify(toVector(sessionId),
                toVector(keyId), toVector(message), toVector(signature),
                match);
        _hidl_cb(toStatus(status), match);
        return Void();
    }

    Return<void> DrmPlugin::signRSA(const hidl_vec<uint8_t>& sessionId,
            const hidl_string& algorithm, const hidl_vec<uint8_t>& message,
            const hidl_vec<uint8_t>& wrappedKey, signRSA_cb _hidl_cb) {

        Vector<uint8_t> legacySignature;
        status_t status = mLegacyPlugin->signRSA(toVector(sessionId),
                String8(algorithm.c_str()), toVector(message), toVector(wrappedKey),
                legacySignature);
        _hidl_cb(toStatus(status), toHidlVec(legacySignature));
        return Void();
    }

    Return<void> DrmPlugin::setListener(const sp<IDrmPluginListener>& listener) {
        mListener = listener;
        mLegacyPlugin->setListener(listener == NULL ? NULL : this);
        return Void();
    }

    Return<void> DrmPlugin::sendEvent(EventType eventType,
            const hidl_vec<uint8_t>& sessionId, const hidl_vec<uint8_t>& data) {
        if (mListener != nullptr) {
            mListener->sendEvent(eventType, sessionId, data);
        }
        return Void();
    }

    Return<void> DrmPlugin::sendExpirationUpdate(
            const hidl_vec<uint8_t>& sessionId, int64_t expiryTimeInMS) {
        if (mListener != nullptr) {
            mListener->sendExpirationUpdate(sessionId, expiryTimeInMS);
        }
        return Void();
    }

    Return<void> DrmPlugin::sendKeysChange(const hidl_vec<uint8_t>& sessionId,
            const hidl_vec<KeyStatus>& keyStatusList, bool hasNewUsableKey) {
        if (mListener != nullptr) {
            mListener->sendKeysChange(sessionId, keyStatusList, hasNewUsableKey);
        }
        return Void();
    }


    // Methods from android::DrmPluginListener

    void DrmPlugin::sendEvent(android::DrmPlugin::EventType legacyEventType,
            int /*unused*/, Vector<uint8_t> const *sessionId,
            Vector<uint8_t> const *data) {

        EventType eventType;
        bool sendEvent = true;
        switch(legacyEventType) {
        case android::DrmPlugin::kDrmPluginEventProvisionRequired:
            eventType = EventType::PROVISION_REQUIRED;
            break;
        case android::DrmPlugin::kDrmPluginEventKeyNeeded:
            eventType = EventType::KEY_NEEDED;
            break;
        case android::DrmPlugin::kDrmPluginEventKeyExpired:
            eventType = EventType::KEY_EXPIRED;
            break;
        case android::DrmPlugin::kDrmPluginEventVendorDefined:
            eventType = EventType::VENDOR_DEFINED;
            break;
        case android::DrmPlugin::kDrmPluginEventSessionReclaimed:
            eventType = EventType::SESSION_RECLAIMED;
            break;
        default:
            sendEvent = false;
            break;
        }
        if (sendEvent) {
            Vector<uint8_t> emptyVector;
            mListener->sendEvent(eventType,
                    toHidlVec(sessionId == NULL ? emptyVector: *sessionId),
                    toHidlVec(data == NULL ? emptyVector: *data));
        }
    }

    void DrmPlugin::sendExpirationUpdate(Vector<uint8_t> const *sessionId,
            int64_t expiryTimeInMS) {
        mListener->sendExpirationUpdate(toHidlVec(*sessionId), expiryTimeInMS);
    }

    void DrmPlugin::sendKeysChange(Vector<uint8_t> const *sessionId,
            Vector<android::DrmPlugin::KeyStatus> const *legacyKeyStatusList,
            bool hasNewUsableKey) {

        Vector<KeyStatus> keyStatusVec;
        for (size_t i = 0; i < legacyKeyStatusList->size(); i++) {
            const android::DrmPlugin::KeyStatus &legacyKeyStatus =
                legacyKeyStatusList->itemAt(i);

            KeyStatus keyStatus;

            switch(legacyKeyStatus.mType) {
            case android::DrmPlugin::kKeyStatusType_Usable:
                keyStatus.type = KeyStatusType::USABLE;
                break;
            case android::DrmPlugin::kKeyStatusType_Expired:
                keyStatus.type = KeyStatusType::EXPIRED;
                break;
            case android::DrmPlugin::kKeyStatusType_OutputNotAllowed:
                keyStatus.type = KeyStatusType::OUTPUTNOTALLOWED;
                break;
            case android::DrmPlugin::kKeyStatusType_StatusPending:
                keyStatus.type = KeyStatusType::STATUSPENDING;
                break;
            case android::DrmPlugin::kKeyStatusType_InternalError:
            default:
                keyStatus.type = KeyStatusType::INTERNALERROR;
                break;
            }

            keyStatus.keyId = toHidlVec(legacyKeyStatus.mKeyId);
            keyStatusVec.push_back(keyStatus);
        }
        mListener->sendKeysChange(toHidlVec(*sessionId),
                toHidlVec(keyStatusVec), hasNewUsableKey);
    }

}  // namespace implementation
}  // namespace V1_0
}  // namespace drm
}  // namespace hardware
}  // namespace android
