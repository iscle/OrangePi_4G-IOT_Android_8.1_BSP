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

#define LOG_TAG "EnvReverb_HAL"
#include <android/log.h>

#include "EnvironmentalReverbEffect.h"

namespace android {
namespace hardware {
namespace audio {
namespace effect {
namespace V2_0 {
namespace implementation {

EnvironmentalReverbEffect::EnvironmentalReverbEffect(effect_handle_t handle)
        : mEffect(new Effect(handle)) {
}

EnvironmentalReverbEffect::~EnvironmentalReverbEffect() {}

void EnvironmentalReverbEffect::propertiesFromHal(
        const t_reverb_settings& halProperties,
        IEnvironmentalReverbEffect::AllProperties* properties) {
    properties->roomLevel = halProperties.roomLevel;
    properties->roomHfLevel = halProperties.roomHFLevel;
    properties->decayTime = halProperties.decayTime;
    properties->decayHfRatio = halProperties.decayHFRatio;
    properties->reflectionsLevel = halProperties.reflectionsLevel;
    properties->reflectionsDelay = halProperties.reflectionsDelay;
    properties->reverbLevel = halProperties.reverbLevel;
    properties->reverbDelay = halProperties.reverbDelay;
    properties->diffusion = halProperties.diffusion;
    properties->density = halProperties.density;
}

void EnvironmentalReverbEffect::propertiesToHal(
        const IEnvironmentalReverbEffect::AllProperties& properties,
        t_reverb_settings* halProperties) {
    halProperties->roomLevel = properties.roomLevel;
    halProperties->roomHFLevel = properties.roomHfLevel;
    halProperties->decayTime = properties.decayTime;
    halProperties->decayHFRatio = properties.decayHfRatio;
    halProperties->reflectionsLevel = properties.reflectionsLevel;
    halProperties->reflectionsDelay = properties.reflectionsDelay;
    halProperties->reverbLevel = properties.reverbLevel;
    halProperties->reverbDelay = properties.reverbDelay;
    halProperties->diffusion = properties.diffusion;
    halProperties->density = properties.density;
}

// Methods from ::android::hardware::audio::effect::V2_0::IEffect follow.
Return<Result> EnvironmentalReverbEffect::init() {
    return mEffect->init();
}

Return<Result> EnvironmentalReverbEffect::setConfig(
        const EffectConfig& config,
        const sp<IEffectBufferProviderCallback>& inputBufferProvider,
        const sp<IEffectBufferProviderCallback>& outputBufferProvider) {
    return mEffect->setConfig(config, inputBufferProvider, outputBufferProvider);
}

Return<Result> EnvironmentalReverbEffect::reset() {
    return mEffect->reset();
}

Return<Result> EnvironmentalReverbEffect::enable() {
    return mEffect->enable();
}

Return<Result> EnvironmentalReverbEffect::disable() {
    return mEffect->disable();
}

Return<Result> EnvironmentalReverbEffect::setDevice(AudioDevice device) {
    return mEffect->setDevice(device);
}

Return<void> EnvironmentalReverbEffect::setAndGetVolume(
        const hidl_vec<uint32_t>& volumes, setAndGetVolume_cb _hidl_cb) {
    return mEffect->setAndGetVolume(volumes, _hidl_cb);
}

Return<Result> EnvironmentalReverbEffect::volumeChangeNotification(
        const hidl_vec<uint32_t>& volumes) {
    return mEffect->volumeChangeNotification(volumes);
}

Return<Result> EnvironmentalReverbEffect::setAudioMode(AudioMode mode) {
    return mEffect->setAudioMode(mode);
}

Return<Result> EnvironmentalReverbEffect::setConfigReverse(
        const EffectConfig& config,
        const sp<IEffectBufferProviderCallback>& inputBufferProvider,
        const sp<IEffectBufferProviderCallback>& outputBufferProvider) {
    return mEffect->setConfigReverse(config, inputBufferProvider, outputBufferProvider);
}

Return<Result> EnvironmentalReverbEffect::setInputDevice(AudioDevice device) {
    return mEffect->setInputDevice(device);
}

Return<void> EnvironmentalReverbEffect::getConfig(getConfig_cb _hidl_cb) {
    return mEffect->getConfig(_hidl_cb);
}

Return<void> EnvironmentalReverbEffect::getConfigReverse(getConfigReverse_cb _hidl_cb) {
    return mEffect->getConfigReverse(_hidl_cb);
}

Return<void> EnvironmentalReverbEffect::getSupportedAuxChannelsConfigs(
        uint32_t maxConfigs, getSupportedAuxChannelsConfigs_cb _hidl_cb) {
    return mEffect->getSupportedAuxChannelsConfigs(maxConfigs, _hidl_cb);
}

Return<void> EnvironmentalReverbEffect::getAuxChannelsConfig(getAuxChannelsConfig_cb _hidl_cb) {
    return mEffect->getAuxChannelsConfig(_hidl_cb);
}

Return<Result> EnvironmentalReverbEffect::setAuxChannelsConfig(
        const EffectAuxChannelsConfig& config) {
    return mEffect->setAuxChannelsConfig(config);
}

Return<Result> EnvironmentalReverbEffect::setAudioSource(AudioSource source) {
    return mEffect->setAudioSource(source);
}

Return<Result> EnvironmentalReverbEffect::offload(const EffectOffloadParameter& param) {
    return mEffect->offload(param);
}

Return<void> EnvironmentalReverbEffect::getDescriptor(getDescriptor_cb _hidl_cb) {
    return mEffect->getDescriptor(_hidl_cb);
}

Return<void> EnvironmentalReverbEffect::prepareForProcessing(
        prepareForProcessing_cb _hidl_cb) {
    return mEffect->prepareForProcessing(_hidl_cb);
}

Return<Result> EnvironmentalReverbEffect::setProcessBuffers(
        const AudioBuffer& inBuffer, const AudioBuffer& outBuffer) {
    return mEffect->setProcessBuffers(inBuffer, outBuffer);
}

Return<void> EnvironmentalReverbEffect::command(
        uint32_t commandId,
        const hidl_vec<uint8_t>& data,
        uint32_t resultMaxSize,
        command_cb _hidl_cb) {
    return mEffect->command(commandId, data, resultMaxSize, _hidl_cb);
}

Return<Result> EnvironmentalReverbEffect::setParameter(
        const hidl_vec<uint8_t>& parameter, const hidl_vec<uint8_t>& value) {
    return mEffect->setParameter(parameter, value);
}

Return<void> EnvironmentalReverbEffect::getParameter(
        const hidl_vec<uint8_t>& parameter,
        uint32_t valueMaxSize,
        getParameter_cb _hidl_cb) {
    return mEffect->getParameter(parameter, valueMaxSize, _hidl_cb);
}

Return<void> EnvironmentalReverbEffect::getSupportedConfigsForFeature(
        uint32_t featureId,
        uint32_t maxConfigs,
        uint32_t configSize,
        getSupportedConfigsForFeature_cb _hidl_cb) {
    return mEffect->getSupportedConfigsForFeature(featureId, maxConfigs, configSize, _hidl_cb);
}

Return<void> EnvironmentalReverbEffect::getCurrentConfigForFeature(
        uint32_t featureId,
        uint32_t configSize,
        getCurrentConfigForFeature_cb _hidl_cb) {
    return mEffect->getCurrentConfigForFeature(featureId, configSize, _hidl_cb);
}

Return<Result> EnvironmentalReverbEffect::setCurrentConfigForFeature(
        uint32_t featureId, const hidl_vec<uint8_t>& configData) {
    return mEffect->setCurrentConfigForFeature(featureId, configData);
}

Return<Result> EnvironmentalReverbEffect::close() {
    return mEffect->close();
}

// Methods from ::android::hardware::audio::effect::V2_0::IEnvironmentalReverbEffect follow.
Return<Result> EnvironmentalReverbEffect::setBypass(bool bypass)  {
    return mEffect->setParam(REVERB_PARAM_BYPASS, bypass);
}

Return<void> EnvironmentalReverbEffect::getBypass(getBypass_cb _hidl_cb)  {
    return mEffect->getIntegerParam(REVERB_PARAM_BYPASS, _hidl_cb);
}

Return<Result> EnvironmentalReverbEffect::setRoomLevel(int16_t roomLevel)  {
    return mEffect->setParam(REVERB_PARAM_ROOM_LEVEL, roomLevel);
}

Return<void> EnvironmentalReverbEffect::getRoomLevel(getRoomLevel_cb _hidl_cb)  {
    return mEffect->getIntegerParam(REVERB_PARAM_ROOM_LEVEL, _hidl_cb);
}

Return<Result> EnvironmentalReverbEffect::setRoomHfLevel(int16_t roomHfLevel)  {
    return mEffect->setParam(REVERB_PARAM_ROOM_HF_LEVEL, roomHfLevel);
}

Return<void> EnvironmentalReverbEffect::getRoomHfLevel(getRoomHfLevel_cb _hidl_cb)  {
    return mEffect->getIntegerParam(REVERB_PARAM_ROOM_HF_LEVEL, _hidl_cb);
}

Return<Result> EnvironmentalReverbEffect::setDecayTime(uint32_t decayTime)  {
    return mEffect->setParam(REVERB_PARAM_DECAY_TIME, decayTime);
}

Return<void> EnvironmentalReverbEffect::getDecayTime(getDecayTime_cb _hidl_cb)  {
    return mEffect->getIntegerParam(REVERB_PARAM_DECAY_TIME, _hidl_cb);
}

Return<Result> EnvironmentalReverbEffect::setDecayHfRatio(int16_t decayHfRatio)  {
    return mEffect->setParam(REVERB_PARAM_DECAY_HF_RATIO, decayHfRatio);
}

Return<void> EnvironmentalReverbEffect::getDecayHfRatio(getDecayHfRatio_cb _hidl_cb)  {
    return mEffect->getIntegerParam(REVERB_PARAM_DECAY_HF_RATIO, _hidl_cb);
}

Return<Result> EnvironmentalReverbEffect::setReflectionsLevel(int16_t reflectionsLevel)  {
    return mEffect->setParam(REVERB_PARAM_REFLECTIONS_LEVEL, reflectionsLevel);
}

Return<void> EnvironmentalReverbEffect::getReflectionsLevel(getReflectionsLevel_cb _hidl_cb)  {
    return mEffect->getIntegerParam(REVERB_PARAM_REFLECTIONS_LEVEL, _hidl_cb);
}

Return<Result> EnvironmentalReverbEffect::setReflectionsDelay(uint32_t reflectionsDelay)  {
    return mEffect->setParam(REVERB_PARAM_REFLECTIONS_DELAY, reflectionsDelay);
}

Return<void> EnvironmentalReverbEffect::getReflectionsDelay(getReflectionsDelay_cb _hidl_cb)  {
    return mEffect->getIntegerParam(REVERB_PARAM_REFLECTIONS_DELAY, _hidl_cb);
}

Return<Result> EnvironmentalReverbEffect::setReverbLevel(int16_t reverbLevel)  {
    return mEffect->setParam(REVERB_PARAM_REVERB_LEVEL, reverbLevel);
}

Return<void> EnvironmentalReverbEffect::getReverbLevel(getReverbLevel_cb _hidl_cb)  {
    return mEffect->getIntegerParam(REVERB_PARAM_REVERB_LEVEL, _hidl_cb);
}

Return<Result> EnvironmentalReverbEffect::setReverbDelay(uint32_t reverbDelay) {
    return mEffect->setParam(REVERB_PARAM_REVERB_DELAY, reverbDelay);
}

Return<void> EnvironmentalReverbEffect::getReverbDelay(getReverbDelay_cb _hidl_cb) {
    return mEffect->getIntegerParam(REVERB_PARAM_REVERB_DELAY, _hidl_cb);
}

Return<Result> EnvironmentalReverbEffect::setDiffusion(int16_t diffusion)  {
    return mEffect->setParam(REVERB_PARAM_DIFFUSION, diffusion);
}

Return<void> EnvironmentalReverbEffect::getDiffusion(getDiffusion_cb _hidl_cb)  {
    return mEffect->getIntegerParam(REVERB_PARAM_DIFFUSION, _hidl_cb);
}

Return<Result> EnvironmentalReverbEffect::setDensity(int16_t density)  {
    return mEffect->setParam(REVERB_PARAM_DENSITY, density);
}

Return<void> EnvironmentalReverbEffect::getDensity(getDensity_cb _hidl_cb)  {
    return mEffect->getIntegerParam(REVERB_PARAM_DENSITY, _hidl_cb);
}

Return<Result> EnvironmentalReverbEffect::setAllProperties(
        const IEnvironmentalReverbEffect::AllProperties& properties)  {
    t_reverb_settings halProperties;
    propertiesToHal(properties, &halProperties);
    return mEffect->setParam(REVERB_PARAM_PROPERTIES, halProperties);
}

Return<void> EnvironmentalReverbEffect::getAllProperties(getAllProperties_cb _hidl_cb)  {
    t_reverb_settings halProperties;
    Result retval = mEffect->getParam(REVERB_PARAM_PROPERTIES, halProperties);
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
