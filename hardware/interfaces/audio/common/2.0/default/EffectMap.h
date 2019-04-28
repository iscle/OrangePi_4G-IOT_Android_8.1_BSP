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

#ifndef android_hardware_audio_V2_0_EffectMap_H_
#define android_hardware_audio_V2_0_EffectMap_H_

#include <mutex>

#include <hardware/audio_effect.h>
#include <utils/KeyedVector.h>
#include <utils/Singleton.h>

namespace android {

// This class needs to be in 'android' ns because Singleton macros require that.
class EffectMap : public Singleton<EffectMap> {
  public:
    static const uint64_t INVALID_ID;

    uint64_t add(effect_handle_t handle);
    effect_handle_t get(const uint64_t& id);
    void remove(effect_handle_t handle);

  private:
    static uint64_t makeUniqueId();

    std::mutex mLock;
    KeyedVector<uint64_t, effect_handle_t> mEffects;
};

}  // namespace android

#endif  // android_hardware_audio_V2_0_EffectMap_H_
