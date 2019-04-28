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

#ifndef ANDROID_HARDWARE_EFFECTS_FACTORY_HAL_HIDL_H
#define ANDROID_HARDWARE_EFFECTS_FACTORY_HAL_HIDL_H

#include <android/hardware/audio/effect/2.0/IEffectsFactory.h>
#include <android/hardware/audio/effect/2.0/types.h>
#include <media/audiohal/EffectsFactoryHalInterface.h>

namespace android {

using ::android::hardware::audio::effect::V2_0::EffectDescriptor;
using ::android::hardware::audio::effect::V2_0::IEffectsFactory;
using ::android::hardware::hidl_vec;

class EffectsFactoryHalHidl : public EffectsFactoryHalInterface, public ConversionHelperHidl
{
  public:
    // Returns the number of different effects in all loaded libraries.
    virtual status_t queryNumberEffects(uint32_t *pNumEffects);

    // Returns a descriptor of the next available effect.
    virtual status_t getDescriptor(uint32_t index,
            effect_descriptor_t *pDescriptor);

    virtual status_t getDescriptor(const effect_uuid_t *pEffectUuid,
            effect_descriptor_t *pDescriptor);

    // Creates an effect engine of the specified type.
    // To release the effect engine, it is necessary to release references
    // to the returned effect object.
    virtual status_t createEffect(const effect_uuid_t *pEffectUuid,
            int32_t sessionId, int32_t ioId,
            sp<EffectHalInterface> *effect);

    virtual status_t dumpEffects(int fd);

  private:
    friend class EffectsFactoryHalInterface;

    sp<IEffectsFactory> mEffectsFactory;
    hidl_vec<EffectDescriptor> mLastDescriptors;

    // Can not be constructed directly by clients.
    EffectsFactoryHalHidl();
    virtual ~EffectsFactoryHalHidl();

    status_t queryAllDescriptors();
};

} // namespace android

#endif // ANDROID_HARDWARE_EFFECTS_FACTORY_HAL_HIDL_H
