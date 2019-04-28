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

#define LOG_TAG "AEC_Effect_HAL"
#include <system/audio_effects/effect_aec.h>
#include <android/log.h>

#include "AcousticEchoCancelerEffect.h"

namespace android {
namespace hardware {
namespace audio {
namespace effect {
namespace V2_0 {
namespace implementation {

AcousticEchoCancelerEffect::AcousticEchoCancelerEffect(effect_handle_t handle)
        : mEffect(new Effect(handle)) {
}

AcousticEchoCancelerEffect::~AcousticEchoCancelerEffect() {}

// Methods from ::android::hardware::audio::effect::V2_0::IEffect follow.
Return<Result> AcousticEchoCancelerEffect::init() {
    return mEffect->init();
}

Return<Result> AcousticEchoCancelerEffect::setConfig(
        const EffectConfig& config,
        const sp<IEffectBufferProviderCallback>& inputBufferProvider,
        const sp<IEffectBufferProviderCallback>& outputBufferProvider) {
    return mEffect->setConfig(config, inputBufferProvider, outputBufferProvider);
}

Return<Result> AcousticEchoCancelerEffect::reset() {
    return mEffect->reset();
}

Return<Result> AcousticEchoCancelerEffect::enable() {
    return mEffect->enable();
}

Return<Result> AcousticEchoCancelerEffect::disable() {
    return mEffect->disable();
}

Return<Result> AcousticEchoCancelerEffect::setDevice(AudioDevice device) {
    return mEffect->setDevice(device);
}

Return<void> AcousticEchoCancelerEffect::setAndGetVolume(
        const hidl_vec<uint32_t>& volumes, setAndGetVolume_cb _hidl_cb) {
    return mEffect->setAndGetVolume(volumes, _hidl_cb);
}

Return<Result> AcousticEchoCancelerEffect::volumeChangeNotification(
        const hidl_vec<uint32_t>& volumes) {
    return mEffect->volumeChangeNotification(volumes);
}

Return<Result> AcousticEchoCancelerEffect::setAudioMode(AudioMode mode) {
    return mEffect->setAudioMode(mode);
}

Return<Result> AcousticEchoCancelerEffect::setConfigReverse(
        const EffectConfig& config,
        const sp<IEffectBufferProviderCallback>& inputBufferProvider,
        const sp<IEffectBufferProviderCallback>& outputBufferProvider) {
    return mEffect->setConfigReverse(config, inputBufferProvider, outputBufferProvider);
}

Return<Result> AcousticEchoCancelerEffect::setInputDevice(AudioDevice device) {
    return mEffect->setInputDevice(device);
}

Return<void> AcousticEchoCancelerEffect::getConfig(getConfig_cb _hidl_cb) {
    return mEffect->getConfig(_hidl_cb);
}

Return<void> AcousticEchoCancelerEffect::getConfigReverse(getConfigReverse_cb _hidl_cb) {
    return mEffect->getConfigReverse(_hidl_cb);
}

Return<void> AcousticEchoCancelerEffect::getSupportedAuxChannelsConfigs(
        uint32_t maxConfigs, getSupportedAuxChannelsConfigs_cb _hidl_cb) {
    return mEffect->getSupportedAuxChannelsConfigs(maxConfigs, _hidl_cb);
}

Return<void> AcousticEchoCancelerEffect::getAuxChannelsConfig(getAuxChannelsConfig_cb _hidl_cb) {
    return mEffect->getAuxChannelsConfig(_hidl_cb);
}

Return<Result> AcousticEchoCancelerEffect::setAuxChannelsConfig(
        const EffectAuxChannelsConfig& config) {
    return mEffect->setAuxChannelsConfig(config);
}

Return<Result> AcousticEchoCancelerEffect::setAudioSource(AudioSource source) {
    return mEffect->setAudioSource(source);
}

Return<Result> AcousticEchoCancelerEffect::offload(const EffectOffloadParameter& param) {
    return mEffect->offload(param);
}

Return<void> AcousticEchoCancelerEffect::getDescriptor(getDescriptor_cb _hidl_cb) {
    return mEffect->getDescriptor(_hidl_cb);
}

Return<void> AcousticEchoCancelerEffect::prepareForProcessing(
        prepareForProcessing_cb _hidl_cb) {
    return mEffect->prepareForProcessing(_hidl_cb);
}

Return<Result> AcousticEchoCancelerEffect::setProcessBuffers(
        const AudioBuffer& inBuffer, const AudioBuffer& outBuffer) {
    return mEffect->setProcessBuffers(inBuffer, outBuffer);
}

Return<void> AcousticEchoCancelerEffect::command(
        uint32_t commandId,
        const hidl_vec<uint8_t>& data,
        uint32_t resultMaxSize,
        command_cb _hidl_cb) {
    return mEffect->command(commandId, data, resultMaxSize, _hidl_cb);
}

Return<Result> AcousticEchoCancelerEffect::setParameter(
        const hidl_vec<uint8_t>& parameter, const hidl_vec<uint8_t>& value) {
    return mEffect->setParameter(parameter, value);
}

Return<void> AcousticEchoCancelerEffect::getParameter(
        const hidl_vec<uint8_t>& parameter,
        uint32_t valueMaxSize,
        getParameter_cb _hidl_cb) {
    return mEffect->getParameter(parameter, valueMaxSize, _hidl_cb);
}

Return<void> AcousticEchoCancelerEffect::getSupportedConfigsForFeature(
        uint32_t featureId,
        uint32_t maxConfigs,
        uint32_t configSize,
        getSupportedConfigsForFeature_cb _hidl_cb) {
    return mEffect->getSupportedConfigsForFeature(featureId, maxConfigs, configSize, _hidl_cb);
}

Return<void> AcousticEchoCancelerEffect::getCurrentConfigForFeature(
        uint32_t featureId,
        uint32_t configSize,
        getCurrentConfigForFeature_cb _hidl_cb) {
    return mEffect->getCurrentConfigForFeature(featureId, configSize, _hidl_cb);
}

Return<Result> AcousticEchoCancelerEffect::setCurrentConfigForFeature(
        uint32_t featureId, const hidl_vec<uint8_t>& configData) {
    return mEffect->setCurrentConfigForFeature(featureId, configData);
}

Return<Result> AcousticEchoCancelerEffect::close() {
    return mEffect->close();
}

// Methods from ::android::hardware::audio::effect::V2_0::IAcousticEchoCancelerEffect follow.
Return<Result> AcousticEchoCancelerEffect::setEchoDelay(uint32_t echoDelayMs)  {
    return mEffect->setParam(AEC_PARAM_ECHO_DELAY, echoDelayMs);
}

Return<void> AcousticEchoCancelerEffect::getEchoDelay(getEchoDelay_cb _hidl_cb)  {
    return mEffect->getIntegerParam(AEC_PARAM_ECHO_DELAY, _hidl_cb);
}

} // namespace implementation
}  // namespace V2_0
}  // namespace effect
}  // namespace audio
}  // namespace hardware
}  // namespace android
