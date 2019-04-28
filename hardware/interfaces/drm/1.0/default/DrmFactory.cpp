/*
 * Copyright (C) 2016 The Android Open Source Project
` *
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

#include "DrmFactory.h"
#include <log/log.h>
#include "DrmPlugin.h"
#include "LegacyPluginPath.h"
#include "TypeConvert.h"

namespace android {
namespace hardware {
namespace drm {
namespace V1_0 {
namespace implementation {

    DrmFactory::DrmFactory() :
        loader(getDrmPluginPath(), "createDrmFactory") {
    }

    // Methods from ::android::hardware::drm::V1_0::IDrmFactory follow.
    Return<bool> DrmFactory::isCryptoSchemeSupported (
            const hidl_array<uint8_t, 16>& uuid) {
        for (size_t i = 0; i < loader.factoryCount(); i++) {
            if (loader.getFactory(i)->isCryptoSchemeSupported(uuid.data())) {
                return true;
            }
        }
        return false;
    }

    Return<bool> DrmFactory::isContentTypeSupported (
            const hidl_string& mimeType) {
        for (size_t i = 0; i < loader.factoryCount(); i++) {
            if (loader.getFactory(i)->isContentTypeSupported(String8(mimeType.c_str()))) {
                return true;
            }
        }
        return false;
    }

    Return<void> DrmFactory::createPlugin(const hidl_array<uint8_t, 16>& uuid,
            const hidl_string& /* appPackageName */, createPlugin_cb _hidl_cb) {

        for (size_t i = 0; i < loader.factoryCount(); i++) {
            if (loader.getFactory(i)->isCryptoSchemeSupported(uuid.data())) {
                android::DrmPlugin *legacyPlugin = NULL;
                status_t status = loader.getFactory(i)->createDrmPlugin(
                        uuid.data(), &legacyPlugin);
                DrmPlugin *newPlugin = NULL;
                if (legacyPlugin == NULL) {
                    ALOGE("Drm legacy HAL: failed to create drm plugin");
                } else {
                    newPlugin = new DrmPlugin(legacyPlugin);
                }
                _hidl_cb(toStatus(status), newPlugin);
                return Void();
            }
        }
        _hidl_cb(Status::ERROR_DRM_CANNOT_HANDLE, NULL);
        return Void();
    }

    IDrmFactory* HIDL_FETCH_IDrmFactory(const char* /* name */) {
        return new DrmFactory();
    }

}  // namespace implementation
}  // namespace V1_0
}  // namespace drm
}  // namespace hardware
}  // namespace android
