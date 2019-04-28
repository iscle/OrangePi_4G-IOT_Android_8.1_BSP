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

#define LOG_TAG "EffectFactoryHAL"
#include <media/EffectsFactoryApi.h>
#include <system/audio_effects/effect_aec.h>
#include <system/audio_effects/effect_agc.h>
#include <system/audio_effects/effect_bassboost.h>
#include <system/audio_effects/effect_downmix.h>
#include <system/audio_effects/effect_environmentalreverb.h>
#include <system/audio_effects/effect_equalizer.h>
#include <system/audio_effects/effect_loudnessenhancer.h>
#include <system/audio_effects/effect_ns.h>
#include <system/audio_effects/effect_presetreverb.h>
#include <system/audio_effects/effect_virtualizer.h>
#include <system/audio_effects/effect_visualizer.h>
#include <android/log.h>

#include "AcousticEchoCancelerEffect.h"
#include "AutomaticGainControlEffect.h"
#include "BassBoostEffect.h"
#include "Conversions.h"
#include "DownmixEffect.h"
#include "EffectsFactory.h"
#include "HidlUtils.h"
#include "Effect.h"
#include "EffectMap.h"
#include "EnvironmentalReverbEffect.h"
#include "EqualizerEffect.h"
#include "LoudnessEnhancerEffect.h"
#include "NoiseSuppressionEffect.h"
#include "PresetReverbEffect.h"
#include "VirtualizerEffect.h"
#include "VisualizerEffect.h"

namespace android {
namespace hardware {
namespace audio {
namespace effect {
namespace V2_0 {
namespace implementation {

// static
sp<IEffect> EffectsFactory::dispatchEffectInstanceCreation(
        const effect_descriptor_t& halDescriptor, effect_handle_t handle) {
    const effect_uuid_t *halUuid = &halDescriptor.type;
    if (memcmp(halUuid, FX_IID_AEC, sizeof(effect_uuid_t)) == 0) {
        return new AcousticEchoCancelerEffect(handle);
    } else if (memcmp(halUuid, FX_IID_AGC, sizeof(effect_uuid_t)) == 0) {
        return new AutomaticGainControlEffect(handle);
    } else if (memcmp(halUuid, SL_IID_BASSBOOST, sizeof(effect_uuid_t)) == 0) {
        return new BassBoostEffect(handle);
    } else if (memcmp(halUuid, EFFECT_UIID_DOWNMIX, sizeof(effect_uuid_t)) == 0) {
        return new DownmixEffect(handle);
    } else if (memcmp(halUuid, SL_IID_ENVIRONMENTALREVERB, sizeof(effect_uuid_t)) == 0) {
        return new EnvironmentalReverbEffect(handle);
    } else if (memcmp(halUuid, SL_IID_EQUALIZER, sizeof(effect_uuid_t)) == 0) {
        return new EqualizerEffect(handle);
    } else if (memcmp(halUuid, FX_IID_LOUDNESS_ENHANCER, sizeof(effect_uuid_t)) == 0) {
        return new LoudnessEnhancerEffect(handle);
    } else if (memcmp(halUuid, FX_IID_NS, sizeof(effect_uuid_t)) == 0) {
        return new NoiseSuppressionEffect(handle);
    } else if (memcmp(halUuid, SL_IID_PRESETREVERB, sizeof(effect_uuid_t)) == 0) {
        return new PresetReverbEffect(handle);
    } else if (memcmp(halUuid, SL_IID_VIRTUALIZER, sizeof(effect_uuid_t)) == 0) {
        return new VirtualizerEffect(handle);
    } else if (memcmp(halUuid, SL_IID_VISUALIZATION, sizeof(effect_uuid_t)) == 0) {
        return new VisualizerEffect(handle);
    }
    return new Effect(handle);
}

// Methods from ::android::hardware::audio::effect::V2_0::IEffectsFactory follow.
Return<void> EffectsFactory::getAllDescriptors(getAllDescriptors_cb _hidl_cb)  {
    Result retval(Result::OK);
    hidl_vec<EffectDescriptor> result;
    uint32_t numEffects;
    status_t status;

restart:
    numEffects = 0;
    status = EffectQueryNumberEffects(&numEffects);
    if (status != OK) {
        retval = Result::NOT_INITIALIZED;
        ALOGE("Error querying number of effects: %s", strerror(-status));
        goto exit;
    }
    result.resize(numEffects);
    for (uint32_t i = 0; i < numEffects; ++i) {
        effect_descriptor_t halDescriptor;
        status = EffectQueryEffect(i, &halDescriptor);
        if (status == OK) {
            effectDescriptorFromHal(halDescriptor, &result[i]);
        } else {
            ALOGE("Error querying effect at position %d / %d: %s",
                    i, numEffects, strerror(-status));
            switch (status) {
                case -ENOSYS: {
                    // Effect list has changed.
                    goto restart;
                }
                case -ENOENT: {
                    // No more effects available.
                    result.resize(i);
                }
                default: {
                    result.resize(0);
                    retval = Result::NOT_INITIALIZED;
                }
            }
            break;
        }
    }

exit:
    _hidl_cb(retval, result);
    return Void();
}

Return<void> EffectsFactory::getDescriptor(const Uuid& uid, getDescriptor_cb _hidl_cb)  {
    effect_uuid_t halUuid;
    HidlUtils::uuidToHal(uid, &halUuid);
    effect_descriptor_t halDescriptor;
    status_t status = EffectGetDescriptor(&halUuid, &halDescriptor);
    EffectDescriptor descriptor;
    effectDescriptorFromHal(halDescriptor, &descriptor);
    Result retval(Result::OK);
    if (status != OK) {
        ALOGE("Error querying effect descriptor for %s: %s",
                uuidToString(halUuid).c_str(), strerror(-status));
        if (status == -ENOENT) {
            retval = Result::INVALID_ARGUMENTS;
        } else {
            retval = Result::NOT_INITIALIZED;
        }
    }
    _hidl_cb(retval, descriptor);
    return Void();
}

Return<void> EffectsFactory::createEffect(
        const Uuid& uid, int32_t session, int32_t ioHandle, createEffect_cb _hidl_cb)  {
    effect_uuid_t halUuid;
    HidlUtils::uuidToHal(uid, &halUuid);
    effect_handle_t handle;
    Result retval(Result::OK);
    status_t status = EffectCreate(&halUuid, session, ioHandle, &handle);
    sp<IEffect> effect;
    uint64_t effectId = EffectMap::INVALID_ID;
    if (status == OK) {
        effect_descriptor_t halDescriptor;
        memset(&halDescriptor, 0, sizeof(effect_descriptor_t));
        status = (*handle)->get_descriptor(handle, &halDescriptor);
        if (status == OK) {
            effect = dispatchEffectInstanceCreation(halDescriptor, handle);
            effectId = EffectMap::getInstance().add(handle);
        } else {
            ALOGE("Error querying effect descriptor for %s: %s",
                    uuidToString(halUuid).c_str(), strerror(-status));
            EffectRelease(handle);
        }
    }
    if (status != OK) {
        ALOGE("Error creating effect %s: %s", uuidToString(halUuid).c_str(), strerror(-status));
        if (status == -ENOENT) {
            retval = Result::INVALID_ARGUMENTS;
        } else {
            retval = Result::NOT_INITIALIZED;
        }
    }
    _hidl_cb(retval, effect, effectId);
    return Void();
}

Return<void> EffectsFactory::debugDump(const hidl_handle& fd)  {
    if (fd.getNativeHandle() != nullptr && fd->numFds == 1) {
        EffectDumpEffects(fd->data[0]);
    }
    return Void();
}


IEffectsFactory* HIDL_FETCH_IEffectsFactory(const char* /* name */) {
    return new EffectsFactory();
}

} // namespace implementation
}  // namespace V2_0
}  // namespace effect
}  // namespace audio
}  // namespace hardware
}  // namespace android
