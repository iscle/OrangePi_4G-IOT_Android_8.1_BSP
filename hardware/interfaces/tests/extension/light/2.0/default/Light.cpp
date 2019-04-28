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
#include "Light.h"

namespace android {
namespace hardware {
namespace tests {
namespace extension {
namespace light {
namespace V2_0 {
namespace implementation {

// Methods from ::android::hardware::light::V2_0::ILight follow.
Return<Status> Light::setLight(Type type, const LightState& state)  {
    // Forward types for new methods.

    ExtLightState extState {
        .state = state,
        .interpolationOmega =
            static_cast<int32_t>(Default::INTERPOLATION_OMEGA),
        .brightness = // ExtBrightness inherits from Brightness
            static_cast<ExtBrightness>(state.brightnessMode)
    };

    return setExtLight(type, extState);
}

Return<void> Light::getSupportedTypes(getSupportedTypes_cb _hidl_cb)  {
    // implement unchanged method as you would always
    hidl_vec<Type> vec{};

    // ******************************************************
    // Note: awesome proprietary hardware implementation here
    // ******************************************************

    _hidl_cb(vec);

    return Void();
}

// Methods from ::android::hardware::example::extension::light::V2_0::ILight follow.
Return<Status> Light::setExtLight(Type /* type */,
                                  const ExtLightState& /* state */)  {

    // ******************************************************
    // Note: awesome proprietary hardware implementation here
    // ******************************************************

    return Status::SUCCESS;
}

} // namespace implementation
}  // namespace V2_0
}  // namespace light
}  // namespace extension
}  // namespace tests
}  // namespace hardware
}  // namespace android
