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

#define LOG_TAG "LowpanIdentity"

#include <android/net/lowpan/LowpanIdentity.h>

#include <binder/Parcel.h>
#include <log/log.h>
#include <utils/Errors.h>

using android::BAD_TYPE;
using android::BAD_VALUE;
using android::NO_ERROR;
using android::Parcel;
using android::status_t;
using android::UNEXPECTED_NULL;
using android::net::lowpan::LowpanIdentity;
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

bool LowpanIdentity::getName(std::string* value) const {
    if (value != NULL) {
        *value = mName;
    }
    return true;
}
bool LowpanIdentity::getType(std::string* value) const {
    if (value != NULL) {
        *value = mType;
    }
    return true;
}
bool LowpanIdentity::getXpanid(std::vector<uint8_t>* value) const {
    if (value != NULL) {
        *value = mXpanid;
    }
    return true;
}
int32_t LowpanIdentity::getPanid(void) const {
    return mPanid;
}
int32_t LowpanIdentity::getChannel(void) const {
    return mChannel;
}

LowpanIdentity::Builder::Builder() {
}

LowpanIdentity::Builder& LowpanIdentity::Builder::setName(const std::string& value) {
    mIdentity.mName = value;
    return *this;
}

LowpanIdentity::Builder& LowpanIdentity::Builder::setType(const std::string& value) {
    mIdentity.mType = value;
    return *this;
}

LowpanIdentity::Builder& LowpanIdentity::Builder::setType(const ::android::String16& value) {
    return setType(String8(value).string());
}

LowpanIdentity::Builder& LowpanIdentity::Builder::setXpanid(const std::vector<uint8_t>& value) {
    mIdentity.mXpanid = value;
    return *this;
}

LowpanIdentity::Builder& LowpanIdentity::Builder::setXpanid(const uint8_t* valuePtr, int32_t valueLen) {
    mIdentity.mXpanid.clear();
    mIdentity.mXpanid.insert(mIdentity.mXpanid.end(), valuePtr, valuePtr + valueLen);
    return *this;
}

LowpanIdentity::Builder& LowpanIdentity::Builder::setPanid(int32_t value) {
    mIdentity.mPanid = value;
    return *this;
}

LowpanIdentity::Builder& LowpanIdentity::Builder::setChannel(int32_t value) {
    mIdentity.mChannel = value;
    return *this;
}

LowpanIdentity::Builder& LowpanIdentity::Builder::setLowpanIdentity(const LowpanIdentity& value) {
    mIdentity = value;
    return *this;
}

LowpanIdentity LowpanIdentity::Builder::build(void) const {
    return mIdentity;
}

LowpanIdentity::LowpanIdentity() : mPanid(UNSPECIFIED_PANID), mChannel(UNSPECIFIED_CHANNEL) {
}

status_t LowpanIdentity::writeToParcel(Parcel* parcel) const {
    /*
     * Keep implementation in sync with writeToParcel() in
     * frameworks/base/lowpan/java/android/net/android/net/lowpan/LowpanIdentity.java.
     */

    std::vector<int8_t> rawName(mName.begin(), mName.end());

    RETURN_IF_FAILED(parcel->writeByteVector(rawName));
    RETURN_IF_FAILED(parcel->writeUtf8AsUtf16(mType));
    RETURN_IF_FAILED(parcel->writeByteVector(mXpanid));
    RETURN_IF_FAILED(parcel->writeInt32(mPanid));
    RETURN_IF_FAILED(parcel->writeInt32(mChannel));
    return NO_ERROR;
}

status_t LowpanIdentity::readFromParcel(const Parcel* parcel) {
    /*
     * Keep implementation in sync with readFromParcel() in
     * frameworks/base/lowpan/java/android/net/android/net/lowpan/LowpanIdentity.java.
     */

    std::vector<int8_t> rawName;

    RETURN_IF_FAILED(parcel->readByteVector(&rawName));

    mName = std::string((const char*)&rawName.front(), rawName.size());

    RETURN_IF_FAILED(parcel->readUtf8FromUtf16(&mType));
    RETURN_IF_FAILED(parcel->readByteVector(&mXpanid));
    RETURN_IF_FAILED(parcel->readInt32(&mPanid));
    RETURN_IF_FAILED(parcel->readInt32(&mChannel));
    return NO_ERROR;
}

bool LowpanIdentity::operator==(const LowpanIdentity& rhs)
{
    const LowpanIdentity& lhs = *this;

    if (lhs.mName != rhs.mName) {
        return false;
    }

    if (lhs.mType != rhs.mType) {
        return false;
    }

    if (lhs.mXpanid != rhs.mXpanid) {
        return false;
    }

    if (lhs.mPanid != rhs.mPanid) {
        return false;
    }

    if (lhs.mChannel != rhs.mChannel) {
        return false;
    }
    return true;
}

}  // namespace lowpan

}  // namespace net

}  // namespace android
