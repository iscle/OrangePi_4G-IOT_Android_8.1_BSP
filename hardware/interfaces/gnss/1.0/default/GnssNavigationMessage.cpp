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

#define LOG_TAG "GnssHAL_GnssNavigationMessageInterface"

#include <log/log.h>

#include "GnssNavigationMessage.h"

namespace android {
namespace hardware {
namespace gnss {
namespace V1_0 {
namespace implementation {

sp<IGnssNavigationMessageCallback> GnssNavigationMessage::sGnssNavigationMsgCbIface = nullptr;

GpsNavigationMessageCallbacks GnssNavigationMessage::sGnssNavigationMessageCb = {
    .size = sizeof(GpsNavigationMessageCallbacks),
    .navigation_message_callback = nullptr,
    .gnss_navigation_message_callback = gnssNavigationMessageCb
};

GnssNavigationMessage::GnssNavigationMessage(
        const GpsNavigationMessageInterface* gpsNavigationMessageIface) :
    mGnssNavigationMessageIface(gpsNavigationMessageIface) {}

void GnssNavigationMessage::gnssNavigationMessageCb(LegacyGnssNavigationMessage* message) {
    if (sGnssNavigationMsgCbIface == nullptr) {
        ALOGE("%s: GnssNavigation Message Callback Interface configured incorrectly", __func__);
        return;
    }

    if (message == nullptr) {
        ALOGE("%s, received invalid GnssNavigationMessage from GNSS HAL", __func__);
        return;
    }

    IGnssNavigationMessageCallback::GnssNavigationMessage navigationMsg;

    navigationMsg.svid = message->svid;
    navigationMsg.type =
            static_cast<IGnssNavigationMessageCallback::GnssNavigationMessageType>(message->type);
    navigationMsg.status = message->status;
    navigationMsg.messageId = message->message_id;
    navigationMsg.submessageId = message->submessage_id;
    navigationMsg.data.setToExternal(message->data, message->data_length);

    auto ret = sGnssNavigationMsgCbIface->gnssNavigationMessageCb(navigationMsg);
    if (!ret.isOk()) {
        ALOGE("%s: Unable to invoke callback", __func__);
    }
}

// Methods from ::android::hardware::gnss::V1_0::IGnssNavigationMessage follow.
Return<GnssNavigationMessage::GnssNavigationMessageStatus> GnssNavigationMessage::setCallback(
        const sp<IGnssNavigationMessageCallback>& callback)  {
    if (mGnssNavigationMessageIface == nullptr) {
        ALOGE("%s: GnssNavigationMessage not available", __func__);
        return GnssNavigationMessageStatus::ERROR_GENERIC;
    }

    sGnssNavigationMsgCbIface = callback;

    return static_cast<GnssNavigationMessage::GnssNavigationMessageStatus>(
            mGnssNavigationMessageIface->init(&sGnssNavigationMessageCb));
}

Return<void> GnssNavigationMessage::close()  {
    if (mGnssNavigationMessageIface == nullptr) {
        ALOGE("%s: GnssNavigationMessage not available", __func__);
    } else {
        mGnssNavigationMessageIface->close();
    }
    return Void();
}

}  // namespace implementation
}  // namespace V1_0
}  // namespace gnss
}  // namespace hardware
}  // namespace android
