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

#define LOG_TAG "LowpanChannelInfo"

#include <android/net/lowpan/LowpanChannelInfo.h>

#include <binder/Parcel.h>
#include <log/log.h>
#include <utils/Errors.h>

using android::BAD_TYPE;
using android::BAD_VALUE;
using android::NO_ERROR;
using android::Parcel;
using android::status_t;
using android::UNEXPECTED_NULL;
using android::net::lowpan::LowpanChannelInfo;
using namespace ::android::binder;

namespace android {

namespace net {

namespace lowpan {

#define RETURN_IF_FAILED(calledOnce)                                     \
    {                                                                    \
        status_t returnStatus = calledOnce;                              \
        if (returnStatus) {                                              \
            ALOGE("Failed at %s:%d (%s)", __FILE__, __LINE__, __func__); \
            return returnStatus;                                         \
         }                                                               \
    }

status_t LowpanChannelInfo::writeToParcel(Parcel* parcel) const {
    /*
     * Keep implementation in sync with writeToParcel() in
     * frameworks/base/lowpan/java/android/net/android/net/lowpan/LowpanChannelInfo.java.
     */

    RETURN_IF_FAILED(parcel->writeInt32(mIndex));
    RETURN_IF_FAILED(parcel->writeUtf8AsUtf16(mName));
    RETURN_IF_FAILED(parcel->writeFloat(mSpectrumCenterFrequency));
    RETURN_IF_FAILED(parcel->writeFloat(mSpectrumBandwidth));
    RETURN_IF_FAILED(parcel->writeInt32(mMaxTxPower));
    RETURN_IF_FAILED(parcel->writeBool(mIsMaskedByRegulatoryDomain));

    return NO_ERROR;
}

status_t LowpanChannelInfo::readFromParcel(const Parcel* parcel) {
    /*
     * Keep implementation in sync with readFromParcel() in
     * frameworks/base/lowpan/java/android/net/android/net/lowpan/LowpanChannelInfo.java.
     */

    RETURN_IF_FAILED(parcel->readInt32(&mIndex));
    RETURN_IF_FAILED(parcel->readUtf8FromUtf16(&mName));
    RETURN_IF_FAILED(parcel->readFloat(&mSpectrumCenterFrequency));
    RETURN_IF_FAILED(parcel->readFloat(&mSpectrumBandwidth));
    RETURN_IF_FAILED(parcel->readInt32(&mMaxTxPower));
    RETURN_IF_FAILED(parcel->readBool(&mIsMaskedByRegulatoryDomain));

    return NO_ERROR;
}

bool LowpanChannelInfo::operator==(const LowpanChannelInfo& rhs)
{
    if (mIndex != rhs.mIndex) {
        return false;
    }

    if (mName != rhs.mName) {
        return false;
    }

    if (mSpectrumCenterFrequency != rhs.mSpectrumCenterFrequency) {
        return false;
    }

    if (mSpectrumBandwidth != rhs.mSpectrumBandwidth) {
        return false;
    }

    if (mMaxTxPower != rhs.mMaxTxPower) {
        return false;
    }

    if (mIsMaskedByRegulatoryDomain != rhs.mIsMaskedByRegulatoryDomain) {
        return false;
    }

    return true;
}

}  // namespace lowpan

}  // namespace net

}  // namespace android
