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

#ifndef ANDROID_LOWPAN_BEACON_INFO_H
#define ANDROID_LOWPAN_BEACON_INFO_H

#include <binder/Parcelable.h>
#include <utils/String16.h>
#include <utils/StrongPointer.h>
#include <set>

#include "LowpanIdentity.h"

namespace android {

namespace net {

namespace lowpan {

/*
 * C++ implementation of the Java class android.net.lowpan.LowpanBeaconInfo
 */
class LowpanBeaconInfo : public Parcelable {
public:
    static const int32_t FLAG_CAN_ASSIST = 1;

    class Builder;
    LowpanBeaconInfo() = default;
    virtual ~LowpanBeaconInfo() = default;
    LowpanBeaconInfo(const LowpanBeaconInfo& x) = default;

    bool operator==(const LowpanBeaconInfo& rhs);
    bool operator!=(const LowpanBeaconInfo& rhs) { return !(*this == rhs); }

public:
    // Overrides
    status_t writeToParcel(Parcel* parcel) const override;
    status_t readFromParcel(const Parcel* parcel) override;

private:
    LowpanBeaconInfo(const Builder& builder);

private:
    // Data
    LowpanIdentity mIdentity;
    int32_t mRssi;
    int32_t mLqi;
    std::vector<uint8_t> mBeaconAddress;
    std::set<int32_t> mFlags;
};

class LowpanBeaconInfo::Builder {
    friend class LowpanBeaconInfo;
public:
    Builder();
    Builder& setName(const std::string& value);
    Builder& setType(const std::string& value);
    Builder& setType(const ::android::String16& value);
    Builder& setXpanid(const std::vector<uint8_t>& value);
    Builder& setXpanid(const uint8_t* valuePtr, int32_t valueLen);
    Builder& setPanid(int32_t value);
    Builder& setChannel(int32_t value);
    Builder& setLowpanIdentity(const LowpanIdentity& value);

    Builder& setRssi(int32_t value);
    Builder& setLqi(int32_t value);
    Builder& setBeaconAddress(const std::vector<uint8_t>& value);
    Builder& setBeaconAddress(const uint8_t* valuePtr, int32_t valueLen);
    Builder& setFlag(int32_t value);
    Builder& clearFlag(int32_t value);

    LowpanBeaconInfo build(void) const;
private:
    LowpanIdentity::Builder mIdentityBuilder;

    int32_t mRssi;
    int32_t mLqi;
    std::vector<uint8_t> mBeaconAddress;
    std::set<int32_t> mFlags;
};

}  // namespace lowpan

}  // namespace net

}  // namespace android

#endif  // ANDROID_LOWPAN_BEACON_INFO_H
