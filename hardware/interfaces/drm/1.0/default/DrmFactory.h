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
#ifndef ANDROID_HARDWARE_DRM_V1_0__DRMFACTORY_H
#define ANDROID_HARDWARE_DRM_V1_0__DRMFACTORY_H

#include <android/hardware/drm/1.0/IDrmFactory.h>
#include <hidl/Status.h>
#include <media/drm/DrmAPI.h>
#include <PluginLoader.h>

namespace android {
namespace hardware {
namespace drm {
namespace V1_0 {
namespace implementation {

using ::android::hardware::drm::V1_0::helper::PluginLoader;
using ::android::hardware::drm::V1_0::IDrmFactory;
using ::android::hardware::drm::V1_0::IDrmPlugin;
using ::android::hardware::hidl_array;
using ::android::hardware::hidl_string;
using ::android::hardware::hidl_vec;
using ::android::hardware::Return;
using ::android::hardware::Void;
using ::android::sp;

struct DrmFactory : public IDrmFactory {
    DrmFactory();
    virtual ~DrmFactory() {}

    // Methods from ::android::hardware::drm::V1_0::IDrmFactory follow.

    Return<bool> isCryptoSchemeSupported(const hidl_array<uint8_t, 16>& uuid)
            override;

    Return<bool> isContentTypeSupported(const hidl_string &mimeType)
            override;

    Return<void> createPlugin(const hidl_array<uint8_t, 16>& uuid,
            const hidl_string& appPackageName, createPlugin_cb _hidl_cb) override;

private:
    PluginLoader<android::DrmFactory> loader;

    DrmFactory(const DrmFactory &) = delete;
    void operator=(const DrmFactory &) = delete;
};

extern "C" IDrmFactory* HIDL_FETCH_IDrmFactory(const char* name);

}  // namespace implementation
}  // namespace V1_0
}  // namespace drm
}  // namespace hardware
}  // namespace android

#endif  // ANDROID_HARDWARE_DRM_V1_0__DRMFACTORY_H
