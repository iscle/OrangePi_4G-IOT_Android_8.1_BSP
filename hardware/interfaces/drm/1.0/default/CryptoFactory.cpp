/*
 * Copyright (C) 2016 The Android Open Source Project
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
#define LOG_TAG "android.hardware.drm@1.0-impl"

#include "CryptoFactory.h"
#include <log/log.h>
#include "CryptoPlugin.h"
#include "LegacyPluginPath.h"
#include "TypeConvert.h"

namespace android {
namespace hardware {
namespace drm {
namespace V1_0 {
namespace implementation {

    CryptoFactory::CryptoFactory() :
        loader(getDrmPluginPath(), "createCryptoFactory") {
    }

    // Methods from ::android::hardware::drm::V1_0::ICryptoFactory follow.
    Return<bool> CryptoFactory::isCryptoSchemeSupported(
            const hidl_array<uint8_t, 16>& uuid) {
        for (size_t i = 0; i < loader.factoryCount(); i++) {
            if (loader.getFactory(i)->isCryptoSchemeSupported(uuid.data())) {
                return true;
            }
        }
        return false;
    }

    Return<void> CryptoFactory::createPlugin(const hidl_array<uint8_t, 16>& uuid,
            const hidl_vec<uint8_t>& initData, createPlugin_cb _hidl_cb) {
        for (size_t i = 0; i < loader.factoryCount(); i++) {
            if (loader.getFactory(i)->isCryptoSchemeSupported(uuid.data())) {
                android::CryptoPlugin *legacyPlugin = NULL;
                status_t status = loader.getFactory(i)->createPlugin(uuid.data(),
                        initData.data(), initData.size(), &legacyPlugin);
                CryptoPlugin *newPlugin = NULL;
                if (legacyPlugin == NULL) {
                    ALOGE("Crypto legacy HAL: failed to create crypto plugin");
                } else {
                    newPlugin = new CryptoPlugin(legacyPlugin);
                }
                _hidl_cb(toStatus(status), newPlugin);
                return Void();
            }
        }
        _hidl_cb(Status::ERROR_DRM_CANNOT_HANDLE, NULL);
        return Void();
    }

    ICryptoFactory* HIDL_FETCH_ICryptoFactory(const char* /* name */) {
        return new CryptoFactory();
    }

}  // namespace implementation
}  // namespace V1_0
}  // namespace drm
}  // namespace hardware
}  // namespace android
