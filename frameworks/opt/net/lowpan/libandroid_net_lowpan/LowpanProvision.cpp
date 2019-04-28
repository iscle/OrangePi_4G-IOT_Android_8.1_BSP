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

#define LOG_TAG "LowpanProvision"

#include <android/net/lowpan/LowpanProvision.h>

#include <binder/Parcel.h>
#include <log/log.h>
#include <utils/Errors.h>

using android::BAD_TYPE;
using android::BAD_VALUE;
using android::NO_ERROR;
using android::Parcel;
using android::status_t;
using android::UNEXPECTED_NULL;
using android::net::lowpan::LowpanProvision;
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
LowpanProvision::LowpanProvision(const LowpanIdentity& identity, const LowpanCredential& credential)
    : mIdentity(identity), mCredential(credential), mHasCredential(true)
{
}

LowpanProvision::LowpanProvision(const LowpanIdentity& identity)
    : mIdentity(identity), mHasCredential(false)
{
}

const LowpanIdentity* LowpanProvision::getLowpanIdentity() const {
    return &mIdentity;
}

const LowpanCredential* LowpanProvision::getLowpanCredential() const {
    return mHasCredential
        ? &mCredential
        : NULL;
}

status_t LowpanProvision::writeToParcel(Parcel* parcel) const {
    /*
     * Keep implementation in sync with writeToParcel() in
     * frameworks/base/lowpan/java/android/net/android/net/lowpan/LowpanProvision.java.
     */

    RETURN_IF_FAILED(mIdentity.writeToParcel(parcel));
    RETURN_IF_FAILED(parcel->writeBool(mHasCredential));

    if (mHasCredential) {
        RETURN_IF_FAILED(mCredential.writeToParcel(parcel));
    }

    return NO_ERROR;
}

status_t LowpanProvision::readFromParcel(const Parcel* parcel) {
    /*
     * Keep implementation in sync with readFromParcel() in
     * frameworks/base/lowpan/java/android/net/android/net/lowpan/LowpanProvision.java.
     */

    RETURN_IF_FAILED(mIdentity.readFromParcel(parcel));
    RETURN_IF_FAILED(parcel->readBool(&mHasCredential));

    if (mHasCredential) {
        RETURN_IF_FAILED(mCredential.readFromParcel(parcel));
    }

    return NO_ERROR;
}

bool LowpanProvision::operator==(const LowpanProvision& rhs)
{
    if (mIdentity != rhs.mIdentity) {
        return false;
    }

    if (mHasCredential != rhs.mHasCredential) {
        return false;
    }

    if (mHasCredential && mCredential != rhs.mCredential) {
        return false;
    }

    return true;
}

}  // namespace lowpan

}  // namespace net

}  // namespace android
