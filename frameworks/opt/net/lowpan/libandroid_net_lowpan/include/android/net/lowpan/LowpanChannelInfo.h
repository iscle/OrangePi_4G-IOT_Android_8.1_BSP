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

#ifndef ANDROID_LOWPAN_CHANNEL_INFO_H
#define ANDROID_LOWPAN_CHANNEL_INFO_H

#include <binder/Parcelable.h>
#include <utils/String16.h>
#include <utils/StrongPointer.h>
#include <string>

namespace android {

namespace net {

namespace lowpan {

/*
 * C++ implementation of the Java class android.net.lowpan.LowpanChannelInfo
 */
class LowpanChannelInfo : public Parcelable {
public:
    LowpanChannelInfo() = default;
    virtual ~LowpanChannelInfo() = default;
    LowpanChannelInfo(const LowpanChannelInfo& x) = default;

    bool operator==(const LowpanChannelInfo& rhs);
    bool operator!=(const LowpanChannelInfo& rhs) { return !(*this == rhs); }

public:
    // Overrides
    status_t writeToParcel(Parcel* parcel) const override;
    status_t readFromParcel(const Parcel* parcel) override;

private:
    // Data
    int32_t mIndex;
    std::string mName;
    float mSpectrumCenterFrequency;
    float mSpectrumBandwidth;
    int32_t mMaxTxPower;
    bool mIsMaskedByRegulatoryDomain;
};

}  // namespace lowpan

}  // namespace net

}  // namespace android

#endif  // ANDROID_LOWPAN_CHANNEL_INFO_H
