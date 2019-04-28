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

#include <system/audio_effects/effect_loudnessenhancer.h>

#define LOG_TAG "LoudnessEnhancer_HAL"
#include <system/audio_effects/effect_aec.h>
#include <android/log.h>

#include "LoudnessEnhancerEffect.h"

namespace android {
namespace hardware {
namespace audio {
namespace effect {
namespace V2_0 {
namespace implementation {

LoudnessEnhancerEffect::LoudnessEnhancerEffect(effect_handle_t handle)
        : mEffect(new Effect(handle)) {
}

LoudnessEnhancerEffect::~LoudnessEnhancerEffect() {}

// Methods from ::android::hardware::audio::effect::V2_0::IEffect follow.
Return<Result> LoudnessEnhancerEffect::init() {
    return mEffect->init();
}

Return<Result> LoudnessEnhancerEffect::setConfig(
        const EffectConfig& config,
        const sp<IEffectBufferProviderCallback>& inputBufferProvider,
        const sp<IEffectBufferProviderCallback>& outputBufferProvider) {
    return mEffect->setConfig(config, inputBufferProvider, outputBufferProvider);
}

Return<Result> LoudnessEnhancerEffect::reset() {
    return mEffect->reset();
}

Return<Result> LoudnessEnhancerEffect::enable() {
    return mEffect->enable();
}

Return<Result> LoudnessEnhancerEffect::disable() {
    return mEffect->disable();
}

Return<Result> LoudnessEnhancerEffect::setDevice(AudioDevice device) {
    return mEffect->setDevice(device);
}

Return<void> LoudnessEnhancerEffect::setAndGetVolume(
        const hidl_vec<uint32_t>& volumes, setAndGetVolume_cb _hidl_cb) {
    return mEffect->setAndGetVolume(volumes, _hidl_cb);
}

Return<Result> LoudnessEnhancerEffect::volumeChangeNotification(
        const hidl_vec<uint32_t>& volumes) {
    return mEffect->volumeChangeNotification(volumes);
}

Return<Result> LoudnessEnhancerEffect::setAudioMode(AudioMode mode) {
    return mEffect->setAudioMode(mode);
}

Return<Result> LoudnessEnhancerEffect::setConfigReverse(
        const EffectConfig& config,
        const sp<IEffectBufferProviderCallback>& inputBufferProvider,
        const sp<IEffectBufferProviderCallback>& outputBufferProvider) {
    return mEffect->setConfigReverse(config, inputBufferProvider, outputBufferProvider);
}

Return<Result> LoudnessEnhancerEffect::setInputDevice(AudioDevice device) {
    return mEffect->setInputDevice(device);
}

Return<void> LoudnessEnhancerEffect::getConfig(getConfig_cb _hidl_cb) {
    return mEffect->getConfig(_hidl_cb);
}

Return<void> LoudnessEnhancerEffect::getConfigReverse(getConfigReverse_cb _hidl_cb) {
    return mEffect->getConfigReverse(_hidl_cb);
}

Return<void> LoudnessEnhancerEffect::getSupportedAuxChannelsConfigs(
        uint32_t maxConfigs, getSupportedAuxChannelsConfigs_cb _hidl_cb) {
    return mEffect->getSupportedAuxChannelsConfigs(maxConfigs, _hidl_cb);
}

Return<void> LoudnessEnhancerEffect::getAuxChannelsConfig(getAuxChannelsConfig_cb _hidl_cb) {
    return mEffect->getAuxChannelsConfig(_hidl_cb);
}

Return<Result> LoudnessEnhancerEffect::setAuxChannelsConfig(
        const EffectAuxChannelsConfig& config) {
    return mEffect->setAuxChannelsConfig(config);
}

Return<Result> LoudnessEnhancerEffect::setAudioSource(AudioSource source) {
    return mEffect->setAudioSource(source);
}

Return<Result> LoudnessEnhancerEffect::offload(const EffectOffloadParameter& param) {
    return mEffect->offload(param);
}

Return<void> LoudnessEnhancerEffect::getDescriptor(getDescriptor_cb _hidl_cb) {
    return mEffect->getDescriptor(_hidl_cb);
}

Return<void> LoudnessEnhancerEffect::prepareForProcessing(
        prepareForProcessing_cb _hidl_cb) {
    return mEffect->prepareForProcessing(_hidl_cb);
}

Return<Result> LoudnessEnhancerEffect::setProcessBuffers(
        const AudioBuffer& inBuffer, const AudioBuffer& outBuffer) {
    return mEffect->setProcessBuffers(inBuffer, outBuffer);
}

Return<void> LoudnessEnhancerEffect::command(
        uint32_t commandId,
        const hidl_vec<uint8_t>& data,
        uint32_t resultMaxSize,
        command_cb _hidl_cb) {
    return mEffect->command(commandId, data, resultMaxSize, _hidl_cb);
}

Return<Result> LoudnessEnhancerEffect::setParameter(
        const hidl_vec<uint8_t>& parameter, const hidl_vec<uint8_t>& value) {
    return mEffect->setParameter(parameter, value);
}

Return<void> LoudnessEnhancerEffect::getParameter(
        const hidl_vec<uint8_t>& parameter,
        uint32_t valueMaxSize,
        getParameter_cb _hidl_cb) {
    return mEffect->getParameter(parameter, valueMaxSize, _hidl_cb);
}

Return<void> LoudnessEnhancerEffect::getSupportedConfigsForFeature(
        uint32_t featureId,
        uint32_t maxConfigs,
        uint32_t configSize,
        getSupportedConfigsForFeature_cb _hidl_cb) {
    return mEffect->getSupportedConfigsForFeature(featureId, maxConfigs, configSize, _hidl_cb);
}

Return<void> LoudnessEnhancerEffect::getCurrentConfigForFeature(
        uint32_t featureId,
        uint32_t configSize,
        getCurrentConfigForFeature_cb _hidl_cb) {
    return mEffect->getCurrentConfigForFeature(featureId, configSize, _hidl_cb);
}

Return<Result> LoudnessEnhancerEffect::setCurrentConfigForFeature(
        uint32_t featureId, const hidl_vec<uint8_t>& configData) {
    return mEffect->setCurrentConfigForFeature(featureId, configData);
}

Return<Result> LoudnessEnhancerEffect::close() {
    return mEffect->close();
}

// Methods from ::android::hardware::audio::effect::V2_0::ILoudnessEnhancerEffect follow.
Return<Result> LoudnessEnhancerEffect::setTargetGain(int32_t targetGainMb)  {
    return mEffect->setParam(LOUDNESS_ENHANCER_DEFAULT_TARGET_GAIN_MB, targetGainMb);
}

Return<void> LoudnessEnhancerEffect::getTargetGain(getTargetGain_cb _hidl_cb)  {
    // AOSP Loudness Enhancer expects the size of the request to not include the
    // size of the parameter.
    uint32_t paramId = LOUDNESS_ENHANCER_DEFAULT_TARGET_GAIN_MB;
    uint32_t targetGainMb = 0;
    Result retval = mEffect->getParameterImpl(
            sizeof(paramId), &paramId,
            0, sizeof(targetGainMb),
            [&] (uint32_t, const void* valueData) {
                memcpy(&targetGainMb, valueData, sizeof(targetGainMb));
            });
    _hidl_cb(retval, targetGainMb);
    return Void();
}

} // namespace implementation
}  // namespace V2_0
}  // namespace effect
}  // namespace audio
}  // namespace hardware
}  // namespace android
