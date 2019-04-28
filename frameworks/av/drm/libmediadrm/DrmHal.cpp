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

//#define LOG_NDEBUG 0
#define LOG_TAG "DrmHal"
#include <utils/Log.h>

#include <binder/IPCThreadState.h>
#include <binder/IServiceManager.h>

#include <android/hardware/drm/1.0/IDrmFactory.h>
#include <android/hardware/drm/1.0/IDrmPlugin.h>
#include <android/hardware/drm/1.0/types.h>
#include <android/hidl/manager/1.0/IServiceManager.h>
#include <hidl/ServiceManagement.h>

#include <media/DrmHal.h>
#include <media/DrmSessionClientInterface.h>
#include <media/DrmSessionManager.h>
#include <media/PluginMetricsReporting.h>
#include <media/drm/DrmAPI.h>
#include <media/stagefright/foundation/ADebug.h>
#include <media/stagefright/foundation/AString.h>
#include <media/stagefright/foundation/hexdump.h>
#include <media/stagefright/MediaErrors.h>

using ::android::hardware::drm::V1_0::EventType;
using ::android::hardware::drm::V1_0::IDrmFactory;
using ::android::hardware::drm::V1_0::IDrmPlugin;
using ::android::hardware::drm::V1_0::KeyedVector;
using ::android::hardware::drm::V1_0::KeyRequestType;
using ::android::hardware::drm::V1_0::KeyStatus;
using ::android::hardware::drm::V1_0::KeyStatusType;
using ::android::hardware::drm::V1_0::KeyType;
using ::android::hardware::drm::V1_0::KeyValue;
using ::android::hardware::drm::V1_0::SecureStop;
using ::android::hardware::drm::V1_0::Status;
using ::android::hardware::hidl_array;
using ::android::hardware::hidl_string;
using ::android::hardware::hidl_vec;
using ::android::hardware::Return;
using ::android::hardware::Void;
using ::android::hidl::manager::V1_0::IServiceManager;
using ::android::sp;

namespace android {

static inline int getCallingPid() {
    return IPCThreadState::self()->getCallingPid();
}

static bool checkPermission(const char* permissionString) {
    if (getpid() == IPCThreadState::self()->getCallingPid()) return true;
    bool ok = checkCallingPermission(String16(permissionString));
    if (!ok) ALOGE("Request requires %s", permissionString);
    return ok;
}

static const Vector<uint8_t> toVector(const hidl_vec<uint8_t> &vec) {
    Vector<uint8_t> vector;
    vector.appendArray(vec.data(), vec.size());
    return *const_cast<const Vector<uint8_t> *>(&vector);
}

static hidl_vec<uint8_t> toHidlVec(const Vector<uint8_t> &vector) {
    hidl_vec<uint8_t> vec;
    vec.setToExternal(const_cast<uint8_t *>(vector.array()), vector.size());
    return vec;
}

static String8 toString8(const hidl_string &string) {
    return String8(string.c_str());
}

static hidl_string toHidlString(const String8& string) {
    return hidl_string(string.string());
}


static ::KeyedVector toHidlKeyedVector(const KeyedVector<String8, String8>&
        keyedVector) {
    std::vector<KeyValue> stdKeyedVector;
    for (size_t i = 0; i < keyedVector.size(); i++) {
        KeyValue keyValue;
        keyValue.key = toHidlString(keyedVector.keyAt(i));
        keyValue.value = toHidlString(keyedVector.valueAt(i));
        stdKeyedVector.push_back(keyValue);
    }
    return ::KeyedVector(stdKeyedVector);
}

static KeyedVector<String8, String8> toKeyedVector(const ::KeyedVector&
        hKeyedVector) {
    KeyedVector<String8, String8> keyedVector;
    for (size_t i = 0; i < hKeyedVector.size(); i++) {
        keyedVector.add(toString8(hKeyedVector[i].key),
                toString8(hKeyedVector[i].value));
    }
    return keyedVector;
}

static List<Vector<uint8_t>> toSecureStops(const hidl_vec<SecureStop>&
        hSecureStops) {
    List<Vector<uint8_t>> secureStops;
    for (size_t i = 0; i < hSecureStops.size(); i++) {
        secureStops.push_back(toVector(hSecureStops[i].opaqueData));
    }
    return secureStops;
}

static status_t toStatusT(Status status) {
    switch (status) {
    case Status::OK:
        return OK;
        break;
    case Status::ERROR_DRM_NO_LICENSE:
        return ERROR_DRM_NO_LICENSE;
        break;
    case Status::ERROR_DRM_LICENSE_EXPIRED:
        return ERROR_DRM_LICENSE_EXPIRED;
        break;
    case Status::ERROR_DRM_SESSION_NOT_OPENED:
        return ERROR_DRM_SESSION_NOT_OPENED;
        break;
    case Status::ERROR_DRM_CANNOT_HANDLE:
        return ERROR_DRM_CANNOT_HANDLE;
        break;
    case Status::ERROR_DRM_INVALID_STATE:
        return ERROR_DRM_TAMPER_DETECTED;
        break;
    case Status::BAD_VALUE:
        return BAD_VALUE;
        break;
    case Status::ERROR_DRM_NOT_PROVISIONED:
        return ERROR_DRM_NOT_PROVISIONED;
        break;
    case Status::ERROR_DRM_RESOURCE_BUSY:
        return ERROR_DRM_RESOURCE_BUSY;
        break;
    case Status::ERROR_DRM_DEVICE_REVOKED:
        return ERROR_DRM_DEVICE_REVOKED;
        break;
    case Status::ERROR_DRM_UNKNOWN:
    default:
        return ERROR_DRM_UNKNOWN;
        break;
    }
}


Mutex DrmHal::mLock;

struct DrmSessionClient : public DrmSessionClientInterface {
    explicit DrmSessionClient(DrmHal* drm) : mDrm(drm) {}

    virtual bool reclaimSession(const Vector<uint8_t>& sessionId) {
        sp<DrmHal> drm = mDrm.promote();
        if (drm == NULL) {
            return true;
        }
        status_t err = drm->closeSession(sessionId);
        if (err != OK) {
            return false;
        }
        drm->sendEvent(EventType::SESSION_RECLAIMED,
                toHidlVec(sessionId), hidl_vec<uint8_t>());
        return true;
    }

protected:
    virtual ~DrmSessionClient() {}

private:
    wp<DrmHal> mDrm;

    DISALLOW_EVIL_CONSTRUCTORS(DrmSessionClient);
};

DrmHal::DrmHal()
   : mDrmSessionClient(new DrmSessionClient(this)),
     mFactories(makeDrmFactories()),
     mInitCheck((mFactories.size() == 0) ? ERROR_UNSUPPORTED : NO_INIT) {
}

void DrmHal::closeOpenSessions() {
    if (mPlugin != NULL) {
        for (size_t i = 0; i < mOpenSessions.size(); i++) {
            mPlugin->closeSession(toHidlVec(mOpenSessions[i]));
            DrmSessionManager::Instance()->removeSession(mOpenSessions[i]);
        }
    }
    mOpenSessions.clear();
}

DrmHal::~DrmHal() {
    closeOpenSessions();
    DrmSessionManager::Instance()->removeDrm(mDrmSessionClient);
}

Vector<sp<IDrmFactory>> DrmHal::makeDrmFactories() {
    Vector<sp<IDrmFactory>> factories;

    auto manager = hardware::defaultServiceManager();

    if (manager != NULL) {
        manager->listByInterface(IDrmFactory::descriptor,
                [&factories](const hidl_vec<hidl_string> &registered) {
                    for (const auto &instance : registered) {
                        auto factory = IDrmFactory::getService(instance);
                        if (factory != NULL) {
                            factories.push_back(factory);
                            ALOGI("makeDrmFactories: factory instance %s is %s",
                                    instance.c_str(),
                                    factory->isRemote() ? "Remote" : "Not Remote");
                        }
                    }
                }
            );
    }

    if (factories.size() == 0) {
        // must be in passthrough mode, load the default passthrough service
        auto passthrough = IDrmFactory::getService();
        if (passthrough != NULL) {
            ALOGI("makeDrmFactories: using default drm instance");
            factories.push_back(passthrough);
        } else {
            ALOGE("Failed to find any drm factories");
        }
    }
    return factories;
}

sp<IDrmPlugin> DrmHal::makeDrmPlugin(const sp<IDrmFactory>& factory,
        const uint8_t uuid[16], const String8& appPackageName) {

    sp<IDrmPlugin> plugin;
    Return<void> hResult = factory->createPlugin(uuid, appPackageName.string(),
            [&](Status status, const sp<IDrmPlugin>& hPlugin) {
                if (status != Status::OK) {
                    ALOGE("Failed to make drm plugin");
                    return;
                }
                plugin = hPlugin;
            }
        );

    if (!hResult.isOk()) {
        ALOGE("createPlugin remote call failed");
    }

    return plugin;
}

status_t DrmHal::initCheck() const {
    return mInitCheck;
}

status_t DrmHal::setListener(const sp<IDrmClient>& listener)
{
    Mutex::Autolock lock(mEventLock);
    if (mListener != NULL){
        IInterface::asBinder(mListener)->unlinkToDeath(this);
    }
    if (listener != NULL) {
        IInterface::asBinder(listener)->linkToDeath(this);
    }
    mListener = listener;
    return NO_ERROR;
}

Return<void> DrmHal::sendEvent(EventType hEventType,
        const hidl_vec<uint8_t>& sessionId, const hidl_vec<uint8_t>& data) {

    mEventLock.lock();
    sp<IDrmClient> listener = mListener;
    mEventLock.unlock();

    if (listener != NULL) {
        Parcel obj;
        writeByteArray(obj, sessionId);
        writeByteArray(obj, data);

        Mutex::Autolock lock(mNotifyLock);
        DrmPlugin::EventType eventType;
        switch(hEventType) {
        case EventType::PROVISION_REQUIRED:
            eventType = DrmPlugin::kDrmPluginEventProvisionRequired;
            break;
        case EventType::KEY_NEEDED:
            eventType = DrmPlugin::kDrmPluginEventKeyNeeded;
            break;
        case EventType::KEY_EXPIRED:
            eventType = DrmPlugin::kDrmPluginEventKeyExpired;
            break;
        case EventType::VENDOR_DEFINED:
            eventType = DrmPlugin::kDrmPluginEventVendorDefined;
            break;
        case EventType::SESSION_RECLAIMED:
            eventType = DrmPlugin::kDrmPluginEventSessionReclaimed;
            break;
        default:
            return Void();
        }
        listener->notify(eventType, 0, &obj);
    }
    return Void();
}

Return<void> DrmHal::sendExpirationUpdate(const hidl_vec<uint8_t>& sessionId,
        int64_t expiryTimeInMS) {

    mEventLock.lock();
    sp<IDrmClient> listener = mListener;
    mEventLock.unlock();

    if (listener != NULL) {
        Parcel obj;
        writeByteArray(obj, sessionId);
        obj.writeInt64(expiryTimeInMS);

        Mutex::Autolock lock(mNotifyLock);
        listener->notify(DrmPlugin::kDrmPluginEventExpirationUpdate, 0, &obj);
    }
    return Void();
}

Return<void> DrmHal::sendKeysChange(const hidl_vec<uint8_t>& sessionId,
        const hidl_vec<KeyStatus>& keyStatusList, bool hasNewUsableKey) {

    mEventLock.lock();
    sp<IDrmClient> listener = mListener;
    mEventLock.unlock();

    if (listener != NULL) {
        Parcel obj;
        writeByteArray(obj, sessionId);

        size_t nKeys = keyStatusList.size();
        obj.writeInt32(nKeys);
        for (size_t i = 0; i < nKeys; ++i) {
            const KeyStatus &keyStatus = keyStatusList[i];
            writeByteArray(obj, keyStatus.keyId);
            uint32_t type;
            switch(keyStatus.type) {
            case KeyStatusType::USABLE:
                type = DrmPlugin::kKeyStatusType_Usable;
                break;
            case KeyStatusType::EXPIRED:
                type = DrmPlugin::kKeyStatusType_Expired;
                break;
            case KeyStatusType::OUTPUTNOTALLOWED:
                type = DrmPlugin::kKeyStatusType_OutputNotAllowed;
                break;
            case KeyStatusType::STATUSPENDING:
                type = DrmPlugin::kKeyStatusType_StatusPending;
                break;
            case KeyStatusType::INTERNALERROR:
            default:
                type = DrmPlugin::kKeyStatusType_InternalError;
                break;
            }
            obj.writeInt32(type);
        }
        obj.writeInt32(hasNewUsableKey);

        Mutex::Autolock lock(mNotifyLock);
        listener->notify(DrmPlugin::kDrmPluginEventKeysChange, 0, &obj);
    }
    return Void();
}

bool DrmHal::isCryptoSchemeSupported(const uint8_t uuid[16], const String8 &mimeType) {
    Mutex::Autolock autoLock(mLock);

    for (size_t i = 0; i < mFactories.size(); i++) {
        if (mFactories[i]->isCryptoSchemeSupported(uuid)) {
            if (mimeType != "") {
                if (mFactories[i]->isContentTypeSupported(mimeType.string())) {
                    return true;
                }
            } else {
                return true;
            }
        }
    }
    return false;
}

status_t DrmHal::createPlugin(const uint8_t uuid[16],
        const String8& appPackageName) {
    Mutex::Autolock autoLock(mLock);

    for (size_t i = 0; i < mFactories.size(); i++) {
        if (mFactories[i]->isCryptoSchemeSupported(uuid)) {
            mPlugin = makeDrmPlugin(mFactories[i], uuid, appPackageName);
        }
    }

    if (mPlugin == NULL) {
        mInitCheck = ERROR_UNSUPPORTED;
    } else {
        if (!mPlugin->setListener(this).isOk()) {
            mInitCheck = DEAD_OBJECT;
        } else {
            mInitCheck = OK;
        }
    }

    return mInitCheck;
}

status_t DrmHal::destroyPlugin() {
    Mutex::Autolock autoLock(mLock);
    if (mInitCheck != OK) {
        return mInitCheck;
    }

    closeOpenSessions();
    reportMetrics();
    setListener(NULL);
    mInitCheck = NO_INIT;

    if (mPlugin != NULL) {
        if (!mPlugin->setListener(NULL).isOk()) {
            mInitCheck = DEAD_OBJECT;
        }
    }
    mPlugin.clear();
    return OK;
}

status_t DrmHal::openSession(Vector<uint8_t> &sessionId) {
    Mutex::Autolock autoLock(mLock);

    if (mInitCheck != OK) {
        return mInitCheck;
    }

    status_t  err = UNKNOWN_ERROR;

    bool retry = true;
    do {
        hidl_vec<uint8_t> hSessionId;

        Return<void> hResult = mPlugin->openSession(
                [&](Status status, const hidl_vec<uint8_t>& id) {
                    if (status == Status::OK) {
                        sessionId = toVector(id);
                    }
                    err = toStatusT(status);
                }
            );

        if (!hResult.isOk()) {
            err = DEAD_OBJECT;
        }

        if (err == ERROR_DRM_RESOURCE_BUSY && retry) {
            mLock.unlock();
            // reclaimSession may call back to closeSession, since mLock is
            // shared between Drm instances, we should unlock here to avoid
            // deadlock.
            retry = DrmSessionManager::Instance()->reclaimSession(getCallingPid());
            mLock.lock();
        } else {
            retry = false;
        }
    } while (retry);

    if (err == OK) {
        DrmSessionManager::Instance()->addSession(getCallingPid(),
                mDrmSessionClient, sessionId);
        mOpenSessions.push(sessionId);
    }
    return err;
}

status_t DrmHal::closeSession(Vector<uint8_t> const &sessionId) {
    Mutex::Autolock autoLock(mLock);

    if (mInitCheck != OK) {
        return mInitCheck;
    }

    Return<Status> status = mPlugin->closeSession(toHidlVec(sessionId));
    if (status.isOk()) {
        if (status == Status::OK) {
            DrmSessionManager::Instance()->removeSession(sessionId);
            for (size_t i = 0; i < mOpenSessions.size(); i++) {
                if (mOpenSessions[i] == sessionId) {
                    mOpenSessions.removeAt(i);
                    break;
                }
            }
        }
        reportMetrics();
        return toStatusT(status);
    }
    return DEAD_OBJECT;
}

status_t DrmHal::getKeyRequest(Vector<uint8_t> const &sessionId,
        Vector<uint8_t> const &initData, String8 const &mimeType,
        DrmPlugin::KeyType keyType, KeyedVector<String8,
        String8> const &optionalParameters, Vector<uint8_t> &request,
        String8 &defaultUrl, DrmPlugin::KeyRequestType *keyRequestType) {
    Mutex::Autolock autoLock(mLock);

    if (mInitCheck != OK) {
        return mInitCheck;
    }

    DrmSessionManager::Instance()->useSession(sessionId);

    KeyType hKeyType;
    if (keyType == DrmPlugin::kKeyType_Streaming) {
        hKeyType = KeyType::STREAMING;
    } else if (keyType == DrmPlugin::kKeyType_Offline) {
        hKeyType = KeyType::OFFLINE;
    } else if (keyType == DrmPlugin::kKeyType_Release) {
        hKeyType = KeyType::RELEASE;
    } else {
        return BAD_VALUE;
    }

    ::KeyedVector hOptionalParameters = toHidlKeyedVector(optionalParameters);

    status_t err = UNKNOWN_ERROR;

    Return<void> hResult = mPlugin->getKeyRequest(toHidlVec(sessionId),
            toHidlVec(initData), toHidlString(mimeType), hKeyType, hOptionalParameters,
            [&](Status status, const hidl_vec<uint8_t>& hRequest,
                    KeyRequestType hKeyRequestType, const hidl_string& hDefaultUrl) {

                if (status == Status::OK) {
                    request = toVector(hRequest);
                    defaultUrl = toString8(hDefaultUrl);

                    switch (hKeyRequestType) {
                    case KeyRequestType::INITIAL:
                        *keyRequestType = DrmPlugin::kKeyRequestType_Initial;
                        break;
                    case KeyRequestType::RENEWAL:
                        *keyRequestType = DrmPlugin::kKeyRequestType_Renewal;
                        break;
                    case KeyRequestType::RELEASE:
                        *keyRequestType = DrmPlugin::kKeyRequestType_Release;
                        break;
                    default:
                        *keyRequestType = DrmPlugin::kKeyRequestType_Unknown;
                        break;
                    }
                    err = toStatusT(status);
                }
            });

    return hResult.isOk() ? err : DEAD_OBJECT;
}

status_t DrmHal::provideKeyResponse(Vector<uint8_t> const &sessionId,
        Vector<uint8_t> const &response, Vector<uint8_t> &keySetId) {
    Mutex::Autolock autoLock(mLock);

    if (mInitCheck != OK) {
        return mInitCheck;
    }

    DrmSessionManager::Instance()->useSession(sessionId);

    status_t err = UNKNOWN_ERROR;

    Return<void> hResult = mPlugin->provideKeyResponse(toHidlVec(sessionId),
            toHidlVec(response),
            [&](Status status, const hidl_vec<uint8_t>& hKeySetId) {
                if (status == Status::OK) {
                    keySetId = toVector(hKeySetId);
                }
                err = toStatusT(status);
            }
        );

    return hResult.isOk() ? err : DEAD_OBJECT;
}

status_t DrmHal::removeKeys(Vector<uint8_t> const &keySetId) {
    Mutex::Autolock autoLock(mLock);

    if (mInitCheck != OK) {
        return mInitCheck;
    }

    return toStatusT(mPlugin->removeKeys(toHidlVec(keySetId)));
}

status_t DrmHal::restoreKeys(Vector<uint8_t> const &sessionId,
        Vector<uint8_t> const &keySetId) {
    Mutex::Autolock autoLock(mLock);

    if (mInitCheck != OK) {
        return mInitCheck;
    }

    DrmSessionManager::Instance()->useSession(sessionId);

    return toStatusT(mPlugin->restoreKeys(toHidlVec(sessionId),
                    toHidlVec(keySetId)));
}

status_t DrmHal::queryKeyStatus(Vector<uint8_t> const &sessionId,
        KeyedVector<String8, String8> &infoMap) const {
    Mutex::Autolock autoLock(mLock);

    if (mInitCheck != OK) {
        return mInitCheck;
    }

    DrmSessionManager::Instance()->useSession(sessionId);

    ::KeyedVector hInfoMap;

    status_t err = UNKNOWN_ERROR;

    Return<void> hResult = mPlugin->queryKeyStatus(toHidlVec(sessionId),
            [&](Status status, const hidl_vec<KeyValue>& map) {
                if (status == Status::OK) {
                    infoMap = toKeyedVector(map);
                }
                err = toStatusT(status);
            }
        );

    return hResult.isOk() ? err : DEAD_OBJECT;
}

status_t DrmHal::getProvisionRequest(String8 const &certType,
        String8 const &certAuthority, Vector<uint8_t> &request,
        String8 &defaultUrl) {
    Mutex::Autolock autoLock(mLock);

    if (mInitCheck != OK) {
        return mInitCheck;
    }

    status_t err = UNKNOWN_ERROR;

    Return<void> hResult = mPlugin->getProvisionRequest(
            toHidlString(certType), toHidlString(certAuthority),
            [&](Status status, const hidl_vec<uint8_t>& hRequest,
                    const hidl_string& hDefaultUrl) {
                if (status == Status::OK) {
                    request = toVector(hRequest);
                    defaultUrl = toString8(hDefaultUrl);
                }
                err = toStatusT(status);
            }
        );

    return hResult.isOk() ? err : DEAD_OBJECT;
}

status_t DrmHal::provideProvisionResponse(Vector<uint8_t> const &response,
        Vector<uint8_t> &certificate, Vector<uint8_t> &wrappedKey) {
    Mutex::Autolock autoLock(mLock);

    if (mInitCheck != OK) {
        return mInitCheck;
    }

    status_t err = UNKNOWN_ERROR;

    Return<void> hResult = mPlugin->provideProvisionResponse(toHidlVec(response),
            [&](Status status, const hidl_vec<uint8_t>& hCertificate,
                    const hidl_vec<uint8_t>& hWrappedKey) {
                if (status == Status::OK) {
                    certificate = toVector(hCertificate);
                    wrappedKey = toVector(hWrappedKey);
                }
                err = toStatusT(status);
            }
        );

    return hResult.isOk() ? err : DEAD_OBJECT;
}

status_t DrmHal::getSecureStops(List<Vector<uint8_t>> &secureStops) {
    Mutex::Autolock autoLock(mLock);

    if (mInitCheck != OK) {
        return mInitCheck;
    }

    status_t err = UNKNOWN_ERROR;

    Return<void> hResult = mPlugin->getSecureStops(
            [&](Status status, const hidl_vec<SecureStop>& hSecureStops) {
                if (status == Status::OK) {
                    secureStops = toSecureStops(hSecureStops);
                }
                err = toStatusT(status);
            }
    );

    return hResult.isOk() ? err : DEAD_OBJECT;
}


status_t DrmHal::getSecureStop(Vector<uint8_t> const &ssid, Vector<uint8_t> &secureStop) {
    Mutex::Autolock autoLock(mLock);

    if (mInitCheck != OK) {
        return mInitCheck;
    }

    status_t err = UNKNOWN_ERROR;

    Return<void> hResult = mPlugin->getSecureStop(toHidlVec(ssid),
            [&](Status status, const SecureStop& hSecureStop) {
                if (status == Status::OK) {
                    secureStop = toVector(hSecureStop.opaqueData);
                }
                err = toStatusT(status);
            }
    );

    return hResult.isOk() ? err : DEAD_OBJECT;
}

status_t DrmHal::releaseSecureStops(Vector<uint8_t> const &ssRelease) {
    Mutex::Autolock autoLock(mLock);

    if (mInitCheck != OK) {
        return mInitCheck;
    }

    return toStatusT(mPlugin->releaseSecureStop(toHidlVec(ssRelease)));
}

status_t DrmHal::releaseAllSecureStops() {
    Mutex::Autolock autoLock(mLock);

    if (mInitCheck != OK) {
        return mInitCheck;
    }

    return toStatusT(mPlugin->releaseAllSecureStops());
}

status_t DrmHal::getPropertyString(String8 const &name, String8 &value ) const {
    Mutex::Autolock autoLock(mLock);
    return getPropertyStringInternal(name, value);
}

status_t DrmHal::getPropertyStringInternal(String8 const &name, String8 &value) const {
    // This function is internal to the class and should only be called while
    // mLock is already held.

    if (mInitCheck != OK) {
        return mInitCheck;
    }

    status_t err = UNKNOWN_ERROR;

    Return<void> hResult = mPlugin->getPropertyString(toHidlString(name),
            [&](Status status, const hidl_string& hValue) {
                if (status == Status::OK) {
                    value = toString8(hValue);
                }
                err = toStatusT(status);
            }
    );

    return hResult.isOk() ? err : DEAD_OBJECT;
}

status_t DrmHal::getPropertyByteArray(String8 const &name, Vector<uint8_t> &value ) const {
    Mutex::Autolock autoLock(mLock);
    return getPropertyByteArrayInternal(name, value);
}

status_t DrmHal::getPropertyByteArrayInternal(String8 const &name, Vector<uint8_t> &value ) const {
    // This function is internal to the class and should only be called while
    // mLock is already held.

    if (mInitCheck != OK) {
        return mInitCheck;
    }

    status_t err = UNKNOWN_ERROR;

    Return<void> hResult = mPlugin->getPropertyByteArray(toHidlString(name),
            [&](Status status, const hidl_vec<uint8_t>& hValue) {
                if (status == Status::OK) {
                    value = toVector(hValue);
                }
                err = toStatusT(status);
            }
    );

    return hResult.isOk() ? err : DEAD_OBJECT;
}

status_t DrmHal::setPropertyString(String8 const &name, String8 const &value ) const {
    Mutex::Autolock autoLock(mLock);

    if (mInitCheck != OK) {
        return mInitCheck;
    }

    Status status =  mPlugin->setPropertyString(toHidlString(name),
            toHidlString(value));
    return toStatusT(status);
}

status_t DrmHal::setPropertyByteArray(String8 const &name,
                                   Vector<uint8_t> const &value ) const {
    Mutex::Autolock autoLock(mLock);

    if (mInitCheck != OK) {
        return mInitCheck;
    }

    Status status = mPlugin->setPropertyByteArray(toHidlString(name),
            toHidlVec(value));
    return toStatusT(status);
}


status_t DrmHal::setCipherAlgorithm(Vector<uint8_t> const &sessionId,
                                 String8 const &algorithm) {
    Mutex::Autolock autoLock(mLock);

    if (mInitCheck != OK) {
        return mInitCheck;
    }

    DrmSessionManager::Instance()->useSession(sessionId);

    Status status = mPlugin->setCipherAlgorithm(toHidlVec(sessionId),
            toHidlString(algorithm));
    return toStatusT(status);
}

status_t DrmHal::setMacAlgorithm(Vector<uint8_t> const &sessionId,
                              String8 const &algorithm) {
    Mutex::Autolock autoLock(mLock);

    if (mInitCheck != OK) {
        return mInitCheck;
    }

    DrmSessionManager::Instance()->useSession(sessionId);

    Status status = mPlugin->setMacAlgorithm(toHidlVec(sessionId),
            toHidlString(algorithm));
    return toStatusT(status);
}

status_t DrmHal::encrypt(Vector<uint8_t> const &sessionId,
        Vector<uint8_t> const &keyId, Vector<uint8_t> const &input,
        Vector<uint8_t> const &iv, Vector<uint8_t> &output) {
    Mutex::Autolock autoLock(mLock);

    if (mInitCheck != OK) {
        return mInitCheck;
    }

    DrmSessionManager::Instance()->useSession(sessionId);

    status_t err = UNKNOWN_ERROR;

    Return<void> hResult = mPlugin->encrypt(toHidlVec(sessionId),
            toHidlVec(keyId), toHidlVec(input), toHidlVec(iv),
            [&](Status status, const hidl_vec<uint8_t>& hOutput) {
                if (status == Status::OK) {
                    output = toVector(hOutput);
                }
                err = toStatusT(status);
            }
    );

    return hResult.isOk() ? err : DEAD_OBJECT;
}

status_t DrmHal::decrypt(Vector<uint8_t> const &sessionId,
        Vector<uint8_t> const &keyId, Vector<uint8_t> const &input,
        Vector<uint8_t> const &iv, Vector<uint8_t> &output) {
    Mutex::Autolock autoLock(mLock);

    if (mInitCheck != OK) {
        return mInitCheck;
    }

    DrmSessionManager::Instance()->useSession(sessionId);

    status_t  err = UNKNOWN_ERROR;

    Return<void> hResult = mPlugin->decrypt(toHidlVec(sessionId),
            toHidlVec(keyId), toHidlVec(input), toHidlVec(iv),
            [&](Status status, const hidl_vec<uint8_t>& hOutput) {
                if (status == Status::OK) {
                    output = toVector(hOutput);
                }
                err = toStatusT(status);
            }
    );

    return hResult.isOk() ? err : DEAD_OBJECT;
}

status_t DrmHal::sign(Vector<uint8_t> const &sessionId,
        Vector<uint8_t> const &keyId, Vector<uint8_t> const &message,
        Vector<uint8_t> &signature) {
    Mutex::Autolock autoLock(mLock);

    if (mInitCheck != OK) {
        return mInitCheck;
    }

    DrmSessionManager::Instance()->useSession(sessionId);

    status_t err = UNKNOWN_ERROR;

    Return<void> hResult = mPlugin->sign(toHidlVec(sessionId),
            toHidlVec(keyId), toHidlVec(message),
            [&](Status status, const hidl_vec<uint8_t>& hSignature)  {
                if (status == Status::OK) {
                    signature = toVector(hSignature);
                }
                err = toStatusT(status);
            }
    );

    return hResult.isOk() ? err : DEAD_OBJECT;
}

status_t DrmHal::verify(Vector<uint8_t> const &sessionId,
        Vector<uint8_t> const &keyId, Vector<uint8_t> const &message,
        Vector<uint8_t> const &signature, bool &match) {
    Mutex::Autolock autoLock(mLock);

    if (mInitCheck != OK) {
        return mInitCheck;
    }

    DrmSessionManager::Instance()->useSession(sessionId);

    status_t err = UNKNOWN_ERROR;

    Return<void> hResult = mPlugin->verify(toHidlVec(sessionId),toHidlVec(keyId),
            toHidlVec(message), toHidlVec(signature),
            [&](Status status, bool hMatch) {
                if (status == Status::OK) {
                    match = hMatch;
                } else {
                    match = false;
                }
                err = toStatusT(status);
            }
    );

    return hResult.isOk() ? err : DEAD_OBJECT;
}

status_t DrmHal::signRSA(Vector<uint8_t> const &sessionId,
        String8 const &algorithm, Vector<uint8_t> const &message,
        Vector<uint8_t> const &wrappedKey, Vector<uint8_t> &signature) {
    Mutex::Autolock autoLock(mLock);

    if (mInitCheck != OK) {
        return mInitCheck;
    }

    if (!checkPermission("android.permission.ACCESS_DRM_CERTIFICATES")) {
        return -EPERM;
    }

    DrmSessionManager::Instance()->useSession(sessionId);

    status_t err = UNKNOWN_ERROR;

    Return<void> hResult = mPlugin->signRSA(toHidlVec(sessionId),
            toHidlString(algorithm), toHidlVec(message), toHidlVec(wrappedKey),
            [&](Status status, const hidl_vec<uint8_t>& hSignature) {
                if (status == Status::OK) {
                    signature = toVector(hSignature);
                }
                err = toStatusT(status);
            }
        );

    return hResult.isOk() ? err : DEAD_OBJECT;
}

void DrmHal::binderDied(const wp<IBinder> &the_late_who __unused)
{
    Mutex::Autolock autoLock(mLock);
    closeOpenSessions();
    setListener(NULL);
    mInitCheck = NO_INIT;

    if (mPlugin != NULL) {
        if (!mPlugin->setListener(NULL).isOk()) {
            mInitCheck = DEAD_OBJECT;
        }
    }
    mPlugin.clear();
}

void DrmHal::writeByteArray(Parcel &obj, hidl_vec<uint8_t> const &vec)
{
    if (vec.size()) {
        obj.writeInt32(vec.size());
        obj.write(vec.data(), vec.size());
    } else {
        obj.writeInt32(0);
    }
}

void DrmHal::reportMetrics() const
{
    Vector<uint8_t> metrics;
    String8 vendor;
    String8 description;
    if (getPropertyStringInternal(String8("vendor"), vendor) == OK &&
            getPropertyStringInternal(String8("description"), description) == OK &&
            getPropertyByteArrayInternal(String8("metrics"), metrics) == OK) {
        status_t res = android::reportDrmPluginMetrics(
                metrics, vendor, description);
        if (res != OK) {
            ALOGE("Metrics were retrieved but could not be reported: %i", res);
        }
    }
}

}  // namespace android
