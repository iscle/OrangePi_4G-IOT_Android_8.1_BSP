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

#ifndef ANDROID_HARDWARE_AUDIO_EFFECT_V2_0_EFFECTSFACTORY_H
#define ANDROID_HARDWARE_AUDIO_EFFECT_V2_0_EFFECTSFACTORY_H

#include <system/audio_effect.h>

#include <android/hardware/audio/effect/2.0/IEffectsFactory.h>
#include <hidl/Status.h>

#include <hidl/MQDescriptor.h>
namespace android {
namespace hardware {
namespace audio {
namespace effect {
namespace V2_0 {
namespace implementation {

using ::android::hardware::audio::common::V2_0::Uuid;
using ::android::hardware::audio::effect::V2_0::EffectDescriptor;
using ::android::hardware::audio::effect::V2_0::IEffect;
using ::android::hardware::audio::effect::V2_0::IEffectsFactory;
using ::android::hardware::audio::effect::V2_0::Result;
using ::android::hardware::Return;
using ::android::hardware::Void;
using ::android::hardware::hidl_vec;
using ::android::hardware::hidl_string;
using ::android::sp;

struct EffectsFactory : public IEffectsFactory {
    // Methods from ::android::hardware::audio::effect::V2_0::IEffectsFactory follow.
    Return<void> getAllDescriptors(getAllDescriptors_cb _hidl_cb)  override;
    Return<void> getDescriptor(const Uuid& uid, getDescriptor_cb _hidl_cb)  override;
    Return<void> createEffect(
            const Uuid& uid, int32_t session, int32_t ioHandle, createEffect_cb _hidl_cb)  override;
    Return<void> debugDump(const hidl_handle& fd)  override;

  private:
    static sp<IEffect> dispatchEffectInstanceCreation(
            const effect_descriptor_t& halDescriptor, effect_handle_t handle);
};

extern "C" IEffectsFactory* HIDL_FETCH_IEffectsFactory(const char* name);

}  // namespace implementation
}  // namespace V2_0
}  // namespace effect
}  // namespace audio
}  // namespace hardware
}  // namespace android

#endif  // ANDROID_HARDWARE_AUDIO_EFFECT_V2_0_EFFECTSFACTORY_H
