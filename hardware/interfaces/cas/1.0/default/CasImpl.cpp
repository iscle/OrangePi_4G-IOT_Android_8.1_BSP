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
#define LOG_TAG "android.hardware.cas@1.0-CasImpl"

#include <android/hardware/cas/1.0/ICasListener.h>
#include <media/cas/CasAPI.h>
#include <utils/Log.h>

#include "CasImpl.h"
#include "SharedLibrary.h"
#include "TypeConvert.h"

namespace android {
namespace hardware {
namespace cas {
namespace V1_0 {
namespace implementation {

struct CasImpl::PluginHolder : public RefBase {
public:
    explicit PluginHolder(CasPlugin *plugin) : mPlugin(plugin) {}
    ~PluginHolder() { if (mPlugin != NULL) delete mPlugin; }
    CasPlugin* get() { return mPlugin; }

private:
    CasPlugin *mPlugin;
    DISALLOW_EVIL_CONSTRUCTORS(PluginHolder);
};

CasImpl::CasImpl(const sp<ICasListener> &listener)
    : mPluginHolder(NULL), mListener(listener) {
    ALOGV("CTOR");
}

CasImpl::~CasImpl() {
    ALOGV("DTOR");
    release();
}

//static
void CasImpl::OnEvent(
        void *appData,
        int32_t event,
        int32_t arg,
        uint8_t *data,
        size_t size) {
    if (appData == NULL) {
        ALOGE("Invalid appData!");
        return;
    }
    CasImpl *casImpl = static_cast<CasImpl *>(appData);
    casImpl->onEvent(event, arg, data, size);
}

void CasImpl::init(const sp<SharedLibrary>& library, CasPlugin *plugin) {
    mLibrary = library;
    mPluginHolder = new PluginHolder(plugin);
}

void CasImpl::onEvent(
        int32_t event, int32_t arg, uint8_t *data, size_t size) {
    if (mListener == NULL) {
        return;
    }

    HidlCasData eventData;
    if (data != NULL) {
        eventData.setToExternal(data, size);
    }

    mListener->onEvent(event, arg, eventData);
}

Return<Status> CasImpl::setPrivateData(const HidlCasData& pvtData) {
    ALOGV("%s", __FUNCTION__);
    sp<PluginHolder> holder = mPluginHolder;
    if (holder == NULL) {
        return toStatus(INVALID_OPERATION);
    }
    return toStatus(holder->get()->setPrivateData(pvtData));
}

Return<void> CasImpl::openSession(openSession_cb _hidl_cb) {
    ALOGV("%s", __FUNCTION__);
    CasSessionId sessionId;

    sp<PluginHolder> holder = mPluginHolder;
    status_t err = INVALID_OPERATION;
    if (holder != NULL) {
        err = holder->get()->openSession(&sessionId);
    }

    _hidl_cb(toStatus(err), sessionId);

    return Void();
}

Return<Status> CasImpl::setSessionPrivateData(
        const HidlCasSessionId &sessionId, const HidlCasData& pvtData) {
    ALOGV("%s: sessionId=%s", __FUNCTION__,
            sessionIdToString(sessionId).string());
    sp<PluginHolder> holder = mPluginHolder;
    if (holder == NULL) {
        return toStatus(INVALID_OPERATION);
    }
    return toStatus(
            holder->get()->setSessionPrivateData(
                    sessionId, pvtData));
}

Return<Status> CasImpl::closeSession(const HidlCasSessionId &sessionId) {
    ALOGV("%s: sessionId=%s", __FUNCTION__,
            sessionIdToString(sessionId).string());
    sp<PluginHolder> holder = mPluginHolder;
    if (holder == NULL) {
        return toStatus(INVALID_OPERATION);
    }
    return toStatus(holder->get()->closeSession(sessionId));
}

Return<Status> CasImpl::processEcm(
        const HidlCasSessionId &sessionId, const HidlCasData& ecm) {
    ALOGV("%s: sessionId=%s", __FUNCTION__,
            sessionIdToString(sessionId).string());
    sp<PluginHolder> holder = mPluginHolder;
    if (holder == NULL) {
        return toStatus(INVALID_OPERATION);
    }

    return toStatus(holder->get()->processEcm(sessionId, ecm));
}

Return<Status> CasImpl::processEmm(const HidlCasData& emm) {
    ALOGV("%s", __FUNCTION__);
    sp<PluginHolder> holder = mPluginHolder;
    if (holder == NULL) {
        return toStatus(INVALID_OPERATION);
    }

    return toStatus(holder->get()->processEmm(emm));
}

Return<Status> CasImpl::sendEvent(
        int32_t event, int32_t arg,
        const HidlCasData& eventData) {
    ALOGV("%s", __FUNCTION__);
    sp<PluginHolder> holder = mPluginHolder;
    if (holder == NULL) {
        return toStatus(INVALID_OPERATION);
    }

    status_t err = holder->get()->sendEvent(event, arg, eventData);
    return toStatus(err);
}

Return<Status> CasImpl::provision(const hidl_string& provisionString) {
    ALOGV("%s: provisionString=%s", __FUNCTION__, provisionString.c_str());
    sp<PluginHolder> holder = mPluginHolder;
    if (holder == NULL) {
        return toStatus(INVALID_OPERATION);
    }

    return toStatus(holder->get()->provision(String8(provisionString.c_str())));
}

Return<Status> CasImpl::refreshEntitlements(
        int32_t refreshType,
        const HidlCasData& refreshData) {
    ALOGV("%s", __FUNCTION__);
    sp<PluginHolder> holder = mPluginHolder;
    if (holder == NULL) {
        return toStatus(INVALID_OPERATION);
    }

    status_t err = holder->get()->refreshEntitlements(refreshType, refreshData);
    return toStatus(err);
}

Return<Status> CasImpl::release() {
    ALOGV("%s: plugin=%p", __FUNCTION__,
            mPluginHolder != NULL ? mPluginHolder->get() : NULL);
    mPluginHolder.clear();
    return Status::OK;
}

} // namespace implementation
} // namespace V1_0
} // namespace cas
} // namespace hardware
} // namespace android
