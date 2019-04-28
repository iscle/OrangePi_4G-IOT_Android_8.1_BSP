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

#ifndef ANDROID_HARDWARE_CAS_V1_0_MEDIA_CAS_SERVICE_H_
#define ANDROID_HARDWARE_CAS_V1_0_MEDIA_CAS_SERVICE_H_

#include <android/hardware/cas/1.0/IMediaCasService.h>

#include "FactoryLoader.h"

namespace android {
struct CasFactory;
struct DescramblerFactory;
namespace hardware {
namespace cas {
namespace V1_0 {
namespace implementation {

class MediaCasService : public IMediaCasService {
public:
    MediaCasService();

    virtual Return<void> enumeratePlugins(
            enumeratePlugins_cb _hidl_cb) override;

    virtual Return<bool> isSystemIdSupported(
            int32_t CA_system_id) override;

    virtual Return<sp<ICas>> createPlugin(
            int32_t CA_system_id, const sp<ICasListener>& listener) override;

    virtual Return<bool> isDescramblerSupported(
            int32_t CA_system_id) override;

    virtual Return<sp<IDescramblerBase>> createDescrambler(
            int32_t CA_system_id) override;

private:
    FactoryLoader<CasFactory> mCasLoader;
    FactoryLoader<DescramblerFactory> mDescramblerLoader;

    virtual ~MediaCasService();
};

} // namespace implementation
} // namespace V1_0
} // namespace cas
} // namespace hardware
} // namespace android

#endif // ANDROID_HARDWARE_CAS_V1_0_MEDIA_CAS_SERVICE_H_
