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

#ifndef ANDROID_HARDWARE_AUDIO_EFFECT_V2_0_NOISESUPPRESSIONEFFECT_H
#define ANDROID_HARDWARE_AUDIO_EFFECT_V2_0_NOISESUPPRESSIONEFFECT_H

#include <system/audio_effects/effect_ns.h>

#include <android/hardware/audio/effect/2.0/INoiseSuppressionEffect.h>
#include <hidl/Status.h>

#include <hidl/MQDescriptor.h>

#include "Effect.h"

namespace android {
namespace hardware {
namespace audio {
namespace effect {
namespace V2_0 {
namespace implementation {

using ::android::hardware::audio::common::V2_0::AudioDevice;
using ::android::hardware::audio::common::V2_0::AudioMode;
using ::android::hardware::audio::common::V2_0::AudioSource;
using ::android::hardware::audio::effect::V2_0::AudioBuffer;
using ::android::hardware::audio::effect::V2_0::EffectAuxChannelsConfig;
using ::android::hardware::audio::effect::V2_0::EffectConfig;
using ::android::hardware::audio::effect::V2_0::EffectDescriptor;
using ::android::hardware::audio::effect::V2_0::EffectOffloadParameter;
using ::android::hardware::audio::effect::V2_0::IEffect;
using ::android::hardware::audio::effect::V2_0::IEffectBufferProviderCallback;
using ::android::hardware::audio::effect::V2_0::INoiseSuppressionEffect;
using ::android::hardware::audio::effect::V2_0::Result;
using ::android::hardware::Return;
using ::android::hardware::Void;
using ::android::hardware::hidl_vec;
using ::android::hardware::hidl_string;
using ::android::sp;

struct NoiseSuppressionEffect : public INoiseSuppressionEffect {
    explicit NoiseSuppressionEffect(effect_handle_t handle);

    // Methods from ::android::hardware::audio::effect::V2_0::IEffect follow.
    Return<Result> init()  override;
    Return<Result> setConfig(
            const EffectConfig& config,
            const sp<IEffectBufferProviderCallback>& inputBufferProvider,
            const sp<IEffectBufferProviderCallback>& outputBufferProvider)  override;
    Return<Result> reset()  override;
    Return<Result> enable()  override;
    Return<Result> disable()  override;
    Return<Result> setDevice(AudioDevice device)  override;
    Return<void> setAndGetVolume(
            const hidl_vec<uint32_t>& volumes, setAndGetVolume_cb _hidl_cb)  override;
    Return<Result> volumeChangeNotification(const hidl_vec<uint32_t>& volumes)  override;
    Return<Result> setAudioMode(AudioMode mode)  override;
    Return<Result> setConfigReverse(
            const EffectConfig& config,
            const sp<IEffectBufferProviderCallback>& inputBufferProvider,
            const sp<IEffectBufferProviderCallback>& outputBufferProvider)  override;
    Return<Result> setInputDevice(AudioDevice device)  override;
    Return<void> getConfig(getConfig_cb _hidl_cb)  override;
    Return<void> getConfigReverse(getConfigReverse_cb _hidl_cb)  override;
    Return<void> getSupportedAuxChannelsConfigs(
            uint32_t maxConfigs, getSupportedAuxChannelsConfigs_cb _hidl_cb)  override;
    Return<void> getAuxChannelsConfig(getAuxChannelsConfig_cb _hidl_cb)  override;
    Return<Result> setAuxChannelsConfig(const EffectAuxChannelsConfig& config)  override;
    Return<Result> setAudioSource(AudioSource source)  override;
    Return<Result> offload(const EffectOffloadParameter& param)  override;
    Return<void> getDescriptor(getDescriptor_cb _hidl_cb)  override;
    Return<void> prepareForProcessing(prepareForProcessing_cb _hidl_cb)  override;
    Return<Result> setProcessBuffers(
            const AudioBuffer& inBuffer, const AudioBuffer& outBuffer)  override;
    Return<void> command(
            uint32_t commandId,
            const hidl_vec<uint8_t>& data,
            uint32_t resultMaxSize,
            command_cb _hidl_cb)  override;
    Return<Result> setParameter(
            const hidl_vec<uint8_t>& parameter, const hidl_vec<uint8_t>& value)  override;
    Return<void> getParameter(
            const hidl_vec<uint8_t>& parameter,
            uint32_t valueMaxSize,
            getParameter_cb _hidl_cb)  override;
    Return<void> getSupportedConfigsForFeature(
            uint32_t featureId,
            uint32_t maxConfigs,
            uint32_t configSize,
            getSupportedConfigsForFeature_cb _hidl_cb)  override;
    Return<void> getCurrentConfigForFeature(
            uint32_t featureId,
            uint32_t configSize,
            getCurrentConfigForFeature_cb _hidl_cb)  override;
    Return<Result> setCurrentConfigForFeature(
            uint32_t featureId, const hidl_vec<uint8_t>& configData)  override;
    Return<Result> close()  override;

    // Methods from ::android::hardware::audio::effect::V2_0::INoiseSuppressionEffect follow.
    Return<Result> setSuppressionLevel(INoiseSuppressionEffect::Level level)  override;
    Return<void> getSuppressionLevel(getSuppressionLevel_cb _hidl_cb)  override;
    Return<Result> setSuppressionType(INoiseSuppressionEffect::Type type)  override;
    Return<void> getSuppressionType(getSuppressionType_cb _hidl_cb)  override;
    Return<Result> setAllProperties(
            const INoiseSuppressionEffect::AllProperties& properties)  override;
    Return<void> getAllProperties(getAllProperties_cb _hidl_cb)  override;

  private:
    sp<Effect> mEffect;

    virtual ~NoiseSuppressionEffect();

    void propertiesFromHal(
            const t_ns_settings& halProperties,
            INoiseSuppressionEffect::AllProperties* properties);
    void propertiesToHal(
            const INoiseSuppressionEffect::AllProperties& properties,
            t_ns_settings* halProperties);
};

}  // namespace implementation
}  // namespace V2_0
}  // namespace effect
}  // namespace audio
}  // namespace hardware
}  // namespace android

#endif  // ANDROID_HARDWARE_AUDIO_EFFECT_V2_0_NOISESUPPRESSIONEFFECT_H
