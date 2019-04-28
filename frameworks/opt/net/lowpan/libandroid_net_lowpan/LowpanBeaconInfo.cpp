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

#define LOG_TAG "LowpanBeaconInfo"

#include <android/net/lowpan/LowpanBeaconInfo.h>

#include <binder/Parcel.h>
#include <log/log.h>
#include <utils/Errors.h>

using android::BAD_TYPE;
using android::BAD_VALUE;
using android::NO_ERROR;
using android::Parcel;
using android::status_t;
using android::UNEXPECTED_NULL;
using android::net::lowpan::LowpanBeaconInfo;
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

LowpanBeaconInfo::Builder::Builder() {
}

LowpanBeaconInfo::Builder& LowpanBeaconInfo::Builder::setName(const std::string& value) {
    mIdentityBuilder.setName(value);
    return *this;
}

LowpanBeaconInfo::Builder& LowpanBeaconInfo::Builder::setType(const std::string& value) {
    mIdentityBuilder.setType(value);
    return *this;
}

LowpanBeaconInfo::Builder& LowpanBeaconInfo::Builder::setType(const ::android::String16& value) {
    mIdentityBuilder.setType(value);
    return *this;
}

LowpanBeaconInfo::Builder& LowpanBeaconInfo::Builder::setXpanid(const std::vector<uint8_t>& value) {
    mIdentityBuilder.setXpanid(value);
    return *this;
}

LowpanBeaconInfo::Builder& LowpanBeaconInfo::Builder::setXpanid(const uint8_t* valuePtr, int32_t valueLen) {
    mIdentityBuilder.setXpanid(valuePtr, valueLen);
    return *this;
}

LowpanBeaconInfo::Builder& LowpanBeaconInfo::Builder::setPanid(int32_t value) {
    mIdentityBuilder.setPanid(value);
    return *this;
}

LowpanBeaconInfo::Builder& LowpanBeaconInfo::Builder::setChannel(int32_t value) {
    mIdentityBuilder.setChannel(value);
    return *this;
}

LowpanBeaconInfo::Builder& LowpanBeaconInfo::Builder::setLowpanIdentity(const LowpanIdentity& value) {
    mIdentityBuilder.setLowpanIdentity(value);
    return *this;
}

LowpanBeaconInfo::Builder& LowpanBeaconInfo::Builder::setRssi(int32_t value) {
    mRssi = value;
    return *this;
}

LowpanBeaconInfo::Builder& LowpanBeaconInfo::Builder::setLqi(int32_t value) {
    mLqi = value;
    return *this;
}

LowpanBeaconInfo::Builder& LowpanBeaconInfo::Builder::setBeaconAddress(const std::vector<uint8_t>& value) {
    mBeaconAddress = value;
    return *this;
}

LowpanBeaconInfo::Builder& LowpanBeaconInfo::Builder::setBeaconAddress(const uint8_t* valuePtr, int32_t valueLen) {
    mBeaconAddress.clear();
    mBeaconAddress.insert(mBeaconAddress.end(), valuePtr, valuePtr + valueLen);
    return *this;
}

LowpanBeaconInfo::Builder& LowpanBeaconInfo::Builder::setFlag(int32_t value) {
    mFlags.insert(value);
    return *this;
}

LowpanBeaconInfo::Builder& LowpanBeaconInfo::Builder::clearFlag(int32_t value) {
    mFlags.erase(value);
    return *this;
}

LowpanBeaconInfo LowpanBeaconInfo::Builder::build(void) const {
    return LowpanBeaconInfo(*this);
}

LowpanBeaconInfo::LowpanBeaconInfo(const LowpanBeaconInfo::Builder& builder) :
    mIdentity(builder.mIdentityBuilder.build()),
    mRssi(builder.mRssi),
    mLqi(builder.mLqi),
    mBeaconAddress(builder.mBeaconAddress),
    mFlags(builder.mFlags)
{
}

status_t LowpanBeaconInfo::writeToParcel(Parcel* parcel) const {
    /*
     * Keep implementation in sync with writeToParcel() in
     * frameworks/base/lowpan/java/android/net/android/net/lowpan/LowpanBeaconInfo.java.
     */

    RETURN_IF_FAILED(mIdentity.writeToParcel(parcel));
    RETURN_IF_FAILED(parcel->writeInt32(mRssi));
    RETURN_IF_FAILED(parcel->writeInt32(mLqi));
    RETURN_IF_FAILED(parcel->writeByteVector(mBeaconAddress));
    RETURN_IF_FAILED(parcel->writeInt32(mFlags.size()));

    std::set<int32_t>::const_iterator iter;
    std::set<int32_t>::const_iterator end = mFlags.end();

    for (iter = mFlags.begin(); iter != end; ++iter) {
        RETURN_IF_FAILED(parcel->writeInt32(*iter));
    }

    return NO_ERROR;
}

status_t LowpanBeaconInfo::readFromParcel(const Parcel* parcel) {
    /*
     * Keep implementation in sync with readFromParcel() in
     * frameworks/base/lowpan/java/android/net/android/net/lowpan/LowpanBeaconInfo.java.
     */

    RETURN_IF_FAILED(mIdentity.readFromParcel(parcel));
    RETURN_IF_FAILED(parcel->readInt32(&mRssi));
    RETURN_IF_FAILED(parcel->readInt32(&mLqi));
    RETURN_IF_FAILED(parcel->readByteVector(&mBeaconAddress));

    int32_t flagCount = 0;

    RETURN_IF_FAILED(parcel->readInt32(&flagCount));

    if (flagCount < 0) {
        ALOGE("Bad flag count");
        return BAD_VALUE;
    }

    mFlags.clear();

    while (flagCount--) {
        int32_t flag = 0;
        RETURN_IF_FAILED(parcel->readInt32(&flag));
        mFlags.insert(flag);
    }

    return NO_ERROR;
}

bool LowpanBeaconInfo::operator==(const LowpanBeaconInfo& rhs)
{
    if (mIdentity != rhs.mIdentity) {
        return false;
    }

    if (mRssi != rhs.mRssi) {
        return false;
    }

    if (mLqi != rhs.mLqi) {
        return false;
    }

    if (mBeaconAddress != rhs.mBeaconAddress) {
        return false;
    }

    if (mFlags != rhs.mFlags) {
        return false;
    }

    return true;
}

}  // namespace lowpan

}  // namespace net

}  // namespace android
