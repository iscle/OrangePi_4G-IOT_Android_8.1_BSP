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

//#define LOG_NDEBUG 0
#define LOG_TAG "android.hardware.cas@1.0-MediaCasService"

#include <android/hardware/cas/1.0/ICasListener.h>
#include <media/cas/CasAPI.h>
#include <media/cas/DescramblerAPI.h>
#include <utils/Log.h>

#include "CasImpl.h"
#include "DescramblerImpl.h"
#include "MediaCasService.h"

namespace android {
namespace hardware {
namespace cas {
namespace V1_0 {
namespace implementation {

MediaCasService::MediaCasService() :
    mCasLoader("createCasFactory"),
    mDescramblerLoader("createDescramblerFactory") {
}

MediaCasService::~MediaCasService() {
}

Return<void> MediaCasService::enumeratePlugins(enumeratePlugins_cb _hidl_cb) {

    ALOGV("%s", __FUNCTION__);

    vector<HidlCasPluginDescriptor> results;
    mCasLoader.enumeratePlugins(&results);

    _hidl_cb(results);
    return Void();
}

Return<bool> MediaCasService::isSystemIdSupported(int32_t CA_system_id) {
    ALOGV("isSystemIdSupported: CA_system_id=%d", CA_system_id);

    return mCasLoader.findFactoryForScheme(CA_system_id);
}

Return<sp<ICas>> MediaCasService::createPlugin(
        int32_t CA_system_id, const sp<ICasListener>& listener) {

    ALOGV("%s: CA_system_id=%d", __FUNCTION__, CA_system_id);

    sp<ICas> result;

    CasFactory *factory;
    sp<SharedLibrary> library;
    if (mCasLoader.findFactoryForScheme(CA_system_id, &library, &factory)) {
        CasPlugin *plugin = NULL;
        sp<CasImpl> casImpl = new CasImpl(listener);
        if (factory->createPlugin(CA_system_id, (uint64_t)casImpl.get(),
                &CasImpl::OnEvent, &plugin) == OK && plugin != NULL) {
            casImpl->init(library, plugin);
            result = casImpl;
        }
    }

    return result;
}

Return<bool> MediaCasService::isDescramblerSupported(int32_t CA_system_id) {
    ALOGV("%s: CA_system_id=%d", __FUNCTION__, CA_system_id);

    return mDescramblerLoader.findFactoryForScheme(CA_system_id);
}

Return<sp<IDescramblerBase>> MediaCasService::createDescrambler(int32_t CA_system_id) {

    ALOGV("%s: CA_system_id=%d", __FUNCTION__, CA_system_id);

    sp<IDescrambler> result;

    DescramblerFactory *factory;
    sp<SharedLibrary> library;
    if (mDescramblerLoader.findFactoryForScheme(
            CA_system_id, &library, &factory)) {
        DescramblerPlugin *plugin = NULL;
        if (factory->createPlugin(CA_system_id, &plugin) == OK
                && plugin != NULL) {
            result = new DescramblerImpl(library, plugin);
        }
    }

    return result;
}

} // namespace implementation
} // namespace V1_0
} // namespace cas
} // namespace hardware
} // namespace android
