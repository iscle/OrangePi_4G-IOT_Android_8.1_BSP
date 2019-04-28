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
#ifndef ANDROID_HARDWARE_TESTS_EXTENSION_LIGHT_V2_0_LIGHT_H
#define ANDROID_HARDWARE_TESTS_EXTENSION_LIGHT_V2_0_LIGHT_H

#include <android/hardware/tests/extension/light/2.0/IExtLight.h>
#include <hidl/Status.h>

#include <hidl/MQDescriptor.h>
namespace android {
namespace hardware {
namespace tests {
namespace extension {
namespace light {
namespace V2_0 {
namespace implementation {

using ::android::hardware::tests::extension::light::V2_0::ExtLightState;
using ::android::hardware::tests::extension::light::V2_0::IExtLight;
using ::android::hardware::light::V2_0::ILight;
using ::android::hardware::light::V2_0::LightState;
using ::android::hardware::light::V2_0::Status;
using ::android::hardware::light::V2_0::Type;
using ::android::hardware::Return;
using ::android::hardware::Void;
using ::android::hardware::hidl_vec;
using ::android::hardware::hidl_string;
using ::android::sp;

struct Light : public IExtLight {
    // Methods from ::android::hardware::light::V2_0::ILight follow.
    Return<Status> setLight(Type type, const LightState& state)  override;
    Return<void> getSupportedTypes(getSupportedTypes_cb _hidl_cb)  override;

    // Methods from ::android::hardware::example::extension::light::V2_0::ILight follow.
    Return<Status> setExtLight(Type type, const ExtLightState& state)  override;

};

}  // namespace implementation
}  // namespace V2_0
}  // namespace light
}  // namespace extension
}  // namespace tests
}  // namespace hardware
}  // namespace android

#endif  // ANDROID_HARDWARE_TESTS_EXTENSION_LIGHT_V2_0_LIGHT_H
