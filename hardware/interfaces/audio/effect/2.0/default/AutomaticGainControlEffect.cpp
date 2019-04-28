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

#define LOG_TAG "AGC_Effect_HAL"
#include <android/log.h>

#include "AutomaticGainControlEffect.h"

namespace android {
namespace hardware {
namespace audio {
namespace effect {
namespace V2_0 {
namespace implementation {

AutomaticGainControlEffect::AutomaticGainControlEffect(effect_handle_t handle)
        : mEffect(new Effect(handle)) {
}

AutomaticGainControlEffect::~AutomaticGainControlEffect() {}

void AutomaticGainControlEffect::propertiesFromHal(
        const t_agc_settings& halProperties,
        IAutomaticGainControlEffect::AllProperties* properties) {
    properties->targetLevelMb = halProperties.targetLevel;
    properties->compGainMb = halProperties.compGain;
    properties->limiterEnabled = halProperties.limiterEnabled;
}

void AutomaticGainControlEffect::propertiesToHal(
        const IAutomaticGainControlEffect::AllProperties& properties,
        t_agc_settings* halProperties) {
    halProperties->targetLevel = properties.targetLevelMb;
    halProperties->compGain = properties.compGainMb;
    halProperties->limiterEnabled = properties.limiterEnabled;
}

// Methods from ::android::hardware::audio::effect::V2_0::IEffect follow.
Return<Result> AutomaticGainControlEffect::init() {
    return mEffect->init();
}

Return<Result> AutomaticGainControlEffect::setConfig(
        const EffectConfig& config,
        const sp<IEffectBufferProviderCallback>& inputBufferProvider,
        const sp<IEffectBufferProviderCallback>& outputBufferProvider) {
    return mEffect->setConfig(config, inputBufferProvider, outputBufferProvider);
}

Return<Result> AutomaticGainControlEffect::reset() {
    return mEffect->reset();
}

Return<Result> AutomaticGainControlEffect::enable() {
    return mEffect->enable();
}

Return<Result> AutomaticGainControlEffect::disable() {
    return mEffect->disable();
}

Return<Result> AutomaticGainControlEffect::setDevice(AudioDevice device) {
    return mEffect->setDevice(device);
}

Return<void> AutomaticGainControlEffect::setAndGetVolume(
        const hidl_vec<uint32_t>& volumes, setAndGetVolume_cb _hidl_cb) {
    return mEffect->setAndGetVolume(volumes, _hidl_cb);
}

Return<Result> AutomaticGainControlEffect::volumeChangeNotification(
        const hidl_vec<uint32_t>& volumes) {
    return mEffect->volumeChangeNotification(volumes);
}

Return<Result> AutomaticGainControlEffect::setAudioMode(AudioMode mode) {
    return mEffect->setAudioMode(mode);
}

Return<Result> AutomaticGainControlEffect::setConfigReverse(
        const EffectConfig& config,
        const sp<IEffectBufferProviderCallback>& inputBufferProvider,
        const sp<IEffectBufferProviderCallback>& outputBufferProvider) {
    return mEffect->setConfigReverse(config, inputBufferProvider, outputBufferProvider);
}

Return<Result> AutomaticGainControlEffect::setInputDevice(AudioDevice device) {
    return mEffect->setInputDevice(device);
}

Return<void> AutomaticGainControlEffect::getConfig(getConfig_cb _hidl_cb) {
    return mEffect->getConfig(_hidl_cb);
}

Return<void> AutomaticGainControlEffect::getConfigReverse(getConfigReverse_cb _hidl_cb) {
    return mEffect->getConfigReverse(_hidl_cb);
}

Return<void> AutomaticGainControlEffect::getSupportedAuxChannelsConfigs(
        uint32_t maxConfigs, getSupportedAuxChannelsConfigs_cb _hidl_cb) {
    return mEffect->getSupportedAuxChannelsConfigs(maxConfigs, _hidl_cb);
}

Return<void> AutomaticGainControlEffect::getAuxChannelsConfig(getAuxChannelsConfig_cb _hidl_cb) {
    return mEffect->getAuxChannelsConfig(_hidl_cb);
}

Return<Result> AutomaticGainControlEffect::setAuxChannelsConfig(
        const EffectAuxChannelsConfig& config) {
    return mEffect->setAuxChannelsConfig(config);
}

Return<Result> AutomaticGainControlEffect::setAudioSource(AudioSource source) {
    return mEffect->setAudioSource(source);
}

Return<Result> AutomaticGainControlEffect::offload(const EffectOffloadParameter& param) {
    return mEffect->offload(param);
}

Return<void> AutomaticGainControlEffect::getDescriptor(getDescriptor_cb _hidl_cb) {
    return mEffect->getDescriptor(_hidl_cb);
}

Return<void> AutomaticGainControlEffect::prepareForProcessing(
        prepareForProcessing_cb _hidl_cb) {
    return mEffect->prepareForProcessing(_hidl_cb);
}

Return<Result> AutomaticGainControlEffect::setProcessBuffers(
        const AudioBuffer& inBuffer, const AudioBuffer& outBuffer) {
    return mEffect->setProcessBuffers(inBuffer, outBuffer);
}

Return<void> AutomaticGainControlEffect::command(
        uint32_t commandId,
        const hidl_vec<uint8_t>& data,
        uint32_t resultMaxSize,
        command_cb _hidl_cb) {
    return mEffect->command(commandId, data, resultMaxSize, _hidl_cb);
}

Return<Result> AutomaticGainControlEffect::setParameter(
        const hidl_vec<uint8_t>& parameter, const hidl_vec<uint8_t>& value) {
    return mEffect->setParameter(parameter, value);
}

Return<void> AutomaticGainControlEffect::getParameter(
        const hidl_vec<uint8_t>& parameter,
        uint32_t valueMaxSize,
        getParameter_cb _hidl_cb) {
    return mEffect->getParameter(parameter, valueMaxSize, _hidl_cb);
}

Return<void> AutomaticGainControlEffect::getSupportedConfigsForFeature(
        uint32_t featureId,
        uint32_t maxConfigs,
        uint32_t configSize,
        getSupportedConfigsForFeature_cb _hidl_cb) {
    return mEffect->getSupportedConfigsForFeature(featureId, maxConfigs, configSize, _hidl_cb);
}

Return<void> AutomaticGainControlEffect::getCurrentConfigForFeature(
        uint32_t featureId,
        uint32_t configSize,
        getCurrentConfigForFeature_cb _hidl_cb) {
    return mEffect->getCurrentConfigForFeature(featureId, configSize, _hidl_cb);
}

Return<Result> AutomaticGainControlEffect::setCurrentConfigForFeature(
        uint32_t featureId, const hidl_vec<uint8_t>& configData) {
    return mEffect->setCurrentConfigForFeature(featureId, configData);
}

Return<Result> AutomaticGainControlEffect::close() {
    return mEffect->close();
}

// Methods from ::android::hardware::audio::effect::V2_0::IAutomaticGainControlEffect follow.
Return<Result> AutomaticGainControlEffect::setTargetLevel(int16_t targetLevelMb)  {
    return mEffect->setParam(AGC_PARAM_TARGET_LEVEL, targetLevelMb);
}

Return<void> AutomaticGainControlEffect::getTargetLevel(getTargetLevel_cb _hidl_cb)  {
    return mEffect->getIntegerParam(AGC_PARAM_TARGET_LEVEL, _hidl_cb);
}

Return<Result> AutomaticGainControlEffect::setCompGain(int16_t compGainMb)  {
    return mEffect->setParam(AGC_PARAM_COMP_GAIN, compGainMb);
}

Return<void> AutomaticGainControlEffect::getCompGain(getCompGain_cb _hidl_cb)  {
    return mEffect->getIntegerParam(AGC_PARAM_COMP_GAIN, _hidl_cb);
}

Return<Result> AutomaticGainControlEffect::setLimiterEnabled(bool enabled)  {
    return mEffect->setParam(AGC_PARAM_LIMITER_ENA, enabled);
}

Return<void> AutomaticGainControlEffect::isLimiterEnabled(isLimiterEnabled_cb _hidl_cb)  {
    return mEffect->getIntegerParam(AGC_PARAM_LIMITER_ENA, _hidl_cb);
}

Return<Result> AutomaticGainControlEffect::setAllProperties(const IAutomaticGainControlEffect::AllProperties& properties)  {
    t_agc_settings halProperties;
    propertiesToHal(properties, &halProperties);
    return mEffect->setParam(AGC_PARAM_PROPERTIES, halProperties);
}

Return<void> AutomaticGainControlEffect::getAllProperties(getAllProperties_cb _hidl_cb)  {
    t_agc_settings halProperties;
    Result retval = mEffect->getParam(AGC_PARAM_PROPERTIES, halProperties);
    AllProperties properties;
    propertiesFromHal(halProperties, &properties);
    _hidl_cb(retval, properties);
    return Void();
}

} // namespace implementation
}  // namespace V2_0
}  // namespace effect
}  // namespace audio
}  // namespace hardware
}  // namespace android
