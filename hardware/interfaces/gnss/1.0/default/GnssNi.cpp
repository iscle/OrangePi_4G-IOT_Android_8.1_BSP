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

#define LOG_TAG "GnssHAL_GnssNiInterface"

#include "GnssNi.h"

namespace android {
namespace hardware {
namespace gnss {
namespace V1_0 {
namespace implementation {

std::vector<std::unique_ptr<ThreadFuncArgs>> GnssNi::sThreadFuncArgsList;
sp<IGnssNiCallback> GnssNi::sGnssNiCbIface = nullptr;
bool GnssNi::sInterfaceExists = false;

GpsNiCallbacks GnssNi::sGnssNiCb = {
    .notify_cb = niNotifyCb,
    .create_thread_cb = createThreadCb
};

GnssNi::GnssNi(const GpsNiInterface* gpsNiIface) : mGnssNiIface(gpsNiIface) {
    /* Error out if an instance of the interface already exists. */
    LOG_ALWAYS_FATAL_IF(sInterfaceExists);
    sInterfaceExists = true;
}

GnssNi::~GnssNi() {
    sThreadFuncArgsList.clear();
    sInterfaceExists = false;
}

pthread_t GnssNi::createThreadCb(const char* name, void (*start)(void*), void* arg) {
    return createPthread(name, start, arg, &sThreadFuncArgsList);
}

void GnssNi::niNotifyCb(GpsNiNotification* notification) {
    if (sGnssNiCbIface == nullptr) {
        ALOGE("%s: GNSS NI Callback Interface configured incorrectly", __func__);
        return;
    }

    if (notification == nullptr) {
        ALOGE("%s: Invalid GpsNotification callback from GNSS HAL", __func__);
        return;
    }

    IGnssNiCallback::GnssNiNotification notificationGnss = {
        .notificationId =  notification->notification_id,
        .niType = static_cast<IGnssNiCallback::GnssNiType>(notification->ni_type),
        .notifyFlags = notification->notify_flags,
        .timeoutSec = static_cast<uint32_t>(notification->timeout),
        .defaultResponse =
                static_cast<IGnssNiCallback::GnssUserResponseType>(notification->default_response),
        .requestorId = notification->requestor_id,
        .notificationMessage = notification->text,
        .requestorIdEncoding =
                static_cast<IGnssNiCallback::GnssNiEncodingType>(notification->requestor_id_encoding),
        .notificationIdEncoding =
                static_cast<IGnssNiCallback::GnssNiEncodingType>(notification->text_encoding)
    };

    auto ret = sGnssNiCbIface->niNotifyCb(notificationGnss);
    if (!ret.isOk()) {
        ALOGE("%s: Unable to invoke callback", __func__);
    }
}

// Methods from ::android::hardware::gnss::V1_0::IGnssNi follow.
Return<void> GnssNi::setCallback(const sp<IGnssNiCallback>& callback)  {
    if (mGnssNiIface == nullptr) {
       ALOGE("%s: GnssNi interface is unavailable", __func__);
       return Void();
    }

    sGnssNiCbIface = callback;

    mGnssNiIface->init(&sGnssNiCb);
    return Void();
}

Return<void> GnssNi::respond(int32_t notifId, IGnssNiCallback::GnssUserResponseType userResponse)  {
    if (mGnssNiIface == nullptr) {
        ALOGE("%s: GnssNi interface is unavailable", __func__);
    } else {
        mGnssNiIface->respond(notifId, static_cast<GpsUserResponseType>(userResponse));
    }
    return Void();
}

}  // namespace implementation
}  // namespace V1_0
}  // namespace gnss
}  // namespace hardware
}  // namespace android
