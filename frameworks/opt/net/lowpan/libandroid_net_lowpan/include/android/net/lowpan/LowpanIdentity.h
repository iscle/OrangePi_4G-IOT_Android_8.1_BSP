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

#ifndef ANDROID_LOWPAN_IDENTITY_H
#define ANDROID_LOWPAN_IDENTITY_H

#include <binder/Parcelable.h>
#include <utils/String16.h>
#include <utils/StrongPointer.h>
#include <string>

namespace android {

namespace net {

namespace lowpan {

/*
 * C++ implementation of the Java class android.net.lowpan.LowpanIdentity
 */
class LowpanIdentity : public Parcelable {
public:
    class Builder;
    static const int32_t UNSPECIFIED_PANID = 0xFFFFFFFF;
    static const int32_t UNSPECIFIED_CHANNEL = -1;

    LowpanIdentity();
    virtual ~LowpanIdentity() = default;
    LowpanIdentity(const LowpanIdentity& x) = default;

    bool operator==(const LowpanIdentity& rhs);
    bool operator!=(const LowpanIdentity& rhs) { return !(*this == rhs); }

    bool getName(std::string* value) const;
    bool getType(std::string* value) const;
    bool getXpanid(std::vector<uint8_t>* value) const;
    int32_t getPanid(void) const;
    int32_t getChannel(void) const;

public:
    // Overrides
    status_t writeToParcel(Parcel* parcel) const override;
    status_t readFromParcel(const Parcel* parcel) override;

private:
    // Data
    std::string mName;
    std::string mType;
    std::vector<uint8_t> mXpanid;
    int32_t mPanid;
    int32_t mChannel;
};

class LowpanIdentity::Builder {
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

    LowpanIdentity build(void) const;
private:
    LowpanIdentity mIdentity;
};

}  // namespace lowpan

}  // namespace net

}  // namespace android

#endif  // ANDROID_LOWPAN_IDENTITY_H
