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

#ifndef android_hardware_audio_effect_V2_0_Conversions_H_
#define android_hardware_audio_effect_V2_0_Conversions_H_

#include <string>

#include <android/hardware/audio/effect/2.0/types.h>
#include <system/audio_effect.h>

namespace android {
namespace hardware {
namespace audio {
namespace effect {
namespace V2_0 {
namespace implementation {

using ::android::hardware::audio::effect::V2_0::EffectDescriptor;

void effectDescriptorFromHal(
        const effect_descriptor_t& halDescriptor, EffectDescriptor* descriptor);
std::string uuidToString(const effect_uuid_t& halUuid);

} // namespace implementation
}  // namespace V2_0
}  // namespace effect
}  // namespace audio
}  // namespace hardware
}  // namespace android

#endif  // android_hardware_audio_effect_V2_0_Conversions_H_
