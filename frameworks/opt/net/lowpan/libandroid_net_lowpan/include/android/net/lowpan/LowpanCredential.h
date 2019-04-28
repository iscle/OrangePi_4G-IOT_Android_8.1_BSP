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

#ifndef ANDROID_LOWPAN_CREDENTIAL_H
#define ANDROID_LOWPAN_CREDENTIAL_H

#include <binder/Parcelable.h>
#include <utils/String16.h>
#include <utils/StrongPointer.h>

namespace android {

namespace net {

namespace lowpan {

/*
 * C++ implementation of the Java class android.net.lowpan.LowpanCredential
 */
class LowpanCredential : public Parcelable {
public:
    static const int32_t UNSPECIFIED_MASTER_KEY_INDEX = 0;
    static const int MASTER_KEY_MAX_SIZE = 1048576;

    LowpanCredential();
    virtual ~LowpanCredential() = default;
    LowpanCredential(const LowpanCredential& x) = default;

    static status_t initMasterKey(LowpanCredential& out, const std::vector<uint8_t>& masterKey, int32_t masterKeyIndex);
    static status_t initMasterKey(LowpanCredential& out, const std::vector<uint8_t>& masterKey);
    static status_t initMasterKey(LowpanCredential& out, const uint8_t* masterKeyBytes, int masterKeyLen, int32_t masterKeyIndex);
    static status_t initMasterKey(LowpanCredential& out, const uint8_t* masterKeyBytes, int masterKeyLen);

    bool isMasterKey()const;
    bool getMasterKey(std::vector<uint8_t>* masterKey)const;
    bool getMasterKey(const uint8_t** masterKey, int* masterKeyLen)const;
    int32_t getMasterKeyIndex()const;

    bool operator==(const LowpanCredential& rhs);
    bool operator!=(const LowpanCredential& rhs) { return !(*this == rhs); }

public:
    // Overrides
    status_t writeToParcel(Parcel* parcel) const override;
    status_t readFromParcel(const Parcel* parcel) override;

private:
    // Data
    std::vector<uint8_t> mMasterKey;
    int32_t mMasterKeyIndex;
};

}  // namespace lowpan

}  // namespace net

}  // namespace android

#endif  // ANDROID_LOWPAN_CREDENTIAL_H
