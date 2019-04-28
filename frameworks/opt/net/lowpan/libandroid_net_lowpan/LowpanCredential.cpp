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

#define LOG_TAG "LowpanCredential"

#include <android/net/lowpan/LowpanCredential.h>

#include <binder/Parcel.h>
#include <log/log.h>
#include <utils/Errors.h>

using android::BAD_TYPE;
using android::BAD_VALUE;
using android::NO_ERROR;
using android::Parcel;
using android::status_t;
using android::UNEXPECTED_NULL;
using android::net::lowpan::LowpanCredential;
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

LowpanCredential::LowpanCredential() : mMasterKeyIndex(UNSPECIFIED_MASTER_KEY_INDEX) { }

status_t LowpanCredential::initMasterKey(LowpanCredential& out, const uint8_t* masterKeyBytes, int masterKeyLen, int masterKeyIndex)
{
    if (masterKeyLen < 0) {
        return BAD_INDEX;
    } else if (masterKeyLen > MASTER_KEY_MAX_SIZE) {
        return BAD_INDEX;
    } else if (masterKeyBytes == NULL) {
        return BAD_VALUE;
    }

    out.mMasterKey.clear();
    out.mMasterKey.insert(out.mMasterKey.end(), masterKeyBytes, masterKeyBytes + masterKeyLen);
    out.mMasterKeyIndex = masterKeyIndex;

    return NO_ERROR;
}

status_t LowpanCredential::initMasterKey(LowpanCredential& out, const uint8_t* masterKeyBytes, int masterKeyLen)
{
    return LowpanCredential::initMasterKey(out, masterKeyBytes, masterKeyLen, 0);
}

status_t LowpanCredential::initMasterKey(LowpanCredential& out, const std::vector<uint8_t>& masterKey, int masterKeyIndex)
{
    return LowpanCredential::initMasterKey(out, &masterKey.front(), masterKey.size(), masterKeyIndex);
}

status_t LowpanCredential::initMasterKey(LowpanCredential& out, const std::vector<uint8_t>& masterKey)
{
    return LowpanCredential::initMasterKey(out, masterKey, 0);
}

bool LowpanCredential::isMasterKey() const {
    return mMasterKey.size() > 0;
}

bool LowpanCredential::getMasterKey(std::vector<uint8_t>* masterKey) const {
    if (isMasterKey()) {
        *masterKey = mMasterKey;
        return true;
    }
    return false;
}

bool LowpanCredential::getMasterKey(const uint8_t** masterKey, int* masterKeyLen) const {
    if (isMasterKey()) {
        if (masterKey) {
            *masterKey = &mMasterKey.front();
        }
        if (masterKeyLen) {
            *masterKeyLen = mMasterKey.size();
        }
        return true;
    }
    return false;
}

int LowpanCredential::getMasterKeyIndex() const {
    return mMasterKeyIndex;
}

status_t LowpanCredential::writeToParcel(Parcel* parcel) const {
    /*
     * Keep implementation in sync with writeToParcel() in
     * frameworks/base/lowpan/java/android/net/android/net/lowpan/LowpanCredential.java.
     */
    RETURN_IF_FAILED(parcel->writeByteVector(mMasterKey));
    RETURN_IF_FAILED(parcel->writeInt32(mMasterKeyIndex));
    return NO_ERROR;
}

status_t LowpanCredential::readFromParcel(const Parcel* parcel) {
    /*
     * Keep implementation in sync with readFromParcel() in
     * frameworks/base/lowpan/java/android/net/android/net/lowpan/LowpanCredential.java.
     */
    RETURN_IF_FAILED(parcel->readByteVector(&mMasterKey));
    RETURN_IF_FAILED(parcel->readInt32(&mMasterKeyIndex));
    return NO_ERROR;
}

bool LowpanCredential::operator==(const LowpanCredential& rhs)
{
    if (mMasterKey != rhs.mMasterKey) {
        return false;
    }

    if (mMasterKeyIndex != rhs.mMasterKeyIndex) {
        return false;
    }

    return true;
}

}  // namespace lowpan

}  // namespace net

}  // namespace android
