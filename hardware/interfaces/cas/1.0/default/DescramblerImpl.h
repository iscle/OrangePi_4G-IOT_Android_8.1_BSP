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

#ifndef ANDROID_HARDWARE_CAS_V1_0_DESCRAMBLER_IMPL_H_
#define ANDROID_HARDWARE_CAS_V1_0_DESCRAMBLER_IMPL_H_

#include <media/stagefright/foundation/ABase.h>
#include <android/hardware/cas/native/1.0/IDescrambler.h>

namespace android {
struct DescramblerPlugin;
using namespace hardware::cas::native::V1_0;

namespace hardware {
namespace cas {
namespace V1_0 {
namespace implementation {

class SharedLibrary;

class DescramblerImpl : public IDescrambler {
public:
    DescramblerImpl(const sp<SharedLibrary>& library, DescramblerPlugin *plugin);
    virtual ~DescramblerImpl();

    virtual Return<Status> setMediaCasSession(
            const HidlCasSessionId& sessionId) override;

    virtual Return<bool> requiresSecureDecoderComponent(
            const hidl_string& mime) override;

    virtual Return<void> descramble(
            ScramblingControl scramblingControl,
            const hidl_vec<SubSample>& subSamples,
            const SharedBuffer& srcBuffer,
            uint64_t srcOffset,
            const DestinationBuffer& dstBuffer,
            uint64_t dstOffset,
            descramble_cb _hidl_cb) override;

    virtual Return<Status> release() override;

private:
    sp<SharedLibrary> mLibrary;
    DescramblerPlugin *mPlugin;

    DISALLOW_EVIL_CONSTRUCTORS(DescramblerImpl);
};

} // namespace implementation
} // namespace V1_0
} // namespace cas
} // namespace hardware
} // namespace android

#endif // ANDROID_HARDWARE_CAS_V1_0_DESCRAMBLER_IMPL_H_
