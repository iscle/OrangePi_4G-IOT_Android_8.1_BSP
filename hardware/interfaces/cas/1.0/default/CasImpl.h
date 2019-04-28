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

#ifndef ANDROID_HARDWARE_CAS_V1_0_CAS_IMPL_H_
#define ANDROID_HARDWARE_CAS_V1_0_CAS_IMPL_H_

#include <media/stagefright/foundation/ABase.h>
#include <android/hardware/cas/1.0/ICas.h>

namespace android {
struct CasPlugin;

namespace hardware {
namespace cas {
namespace V1_0 {
struct ICasListener;
namespace implementation {

class SharedLibrary;

class CasImpl : public ICas {
public:
    CasImpl(const sp<ICasListener> &listener);
    virtual ~CasImpl();

    static void OnEvent(
            void *appData,
            int32_t event,
            int32_t arg,
            uint8_t *data,
            size_t size);

    void init(const sp<SharedLibrary>& library, CasPlugin *plugin);
    void onEvent(
            int32_t event,
            int32_t arg,
            uint8_t *data,
            size_t size);

    // ICas inherits

    virtual Return<Status> setPrivateData(
            const HidlCasData& pvtData) override;

    virtual Return<void> openSession(
            openSession_cb _hidl_cb) override;

    virtual Return<Status> closeSession(
            const HidlCasSessionId& sessionId) override;

    virtual Return<Status> setSessionPrivateData(
            const HidlCasSessionId& sessionId,
            const HidlCasData& pvtData) override;

    virtual Return<Status> processEcm(
            const HidlCasSessionId& sessionId,
            const HidlCasData& ecm) override;

    virtual Return<Status> processEmm(
            const HidlCasData& emm) override;

    virtual Return<Status> sendEvent(
            int32_t event, int32_t arg,
            const HidlCasData& eventData) override;

    virtual Return<Status> provision(
            const hidl_string& provisionString) override;

    virtual Return<Status> refreshEntitlements(
            int32_t refreshType,
            const HidlCasData& refreshData) override;

    virtual Return<Status> release() override;

private:
    struct PluginHolder;
    sp<SharedLibrary> mLibrary;
    sp<PluginHolder> mPluginHolder;
    sp<ICasListener> mListener;

    DISALLOW_EVIL_CONSTRUCTORS(CasImpl);
};

} // namespace implementation
} // namespace V1_0
} // namespace cas
} // namespace hardware
} // namespace android

#endif // ANDROID_HARDWARE_CAS_V1_0_CAS_IMPL_H_
