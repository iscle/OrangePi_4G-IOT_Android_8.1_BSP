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

#ifndef ANDROID_LOWPAN_PROVISION_H
#define ANDROID_LOWPAN_PROVISION_H

#include <binder/Parcelable.h>
#include <utils/String16.h>
#include <utils/StrongPointer.h>

#include "LowpanIdentity.h"
#include "LowpanCredential.h"

namespace android {

namespace net {

namespace lowpan {

/*
 * C++ implementation of the Java class android.net.lowpan.LowpanProvision
 */
class LowpanProvision : public Parcelable {
public:
    LowpanProvision() = default;
    virtual ~LowpanProvision() = default;
    LowpanProvision(const LowpanProvision& x) = default;

    bool operator==(const LowpanProvision& rhs);
    bool operator!=(const LowpanProvision& rhs) { return !(*this == rhs); }

    LowpanProvision(const LowpanIdentity& identity, const LowpanCredential& credential);
    LowpanProvision(const LowpanIdentity& identity);

    const LowpanIdentity* getLowpanIdentity() const;
    const LowpanCredential* getLowpanCredential() const;

public:
    // Overrides
    status_t writeToParcel(Parcel* parcel) const override;
    status_t readFromParcel(const Parcel* parcel) override;

private:
    // Data
    LowpanIdentity mIdentity;
    LowpanCredential mCredential;
    bool mHasCredential;
};

}  // namespace lowpan

}  // namespace net

}  // namespace android

#endif  // ANDROID_LOWPAN_PROVISION_H
