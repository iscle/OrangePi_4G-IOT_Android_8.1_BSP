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

#define LOG_TAG "NS_Effect_HAL"
#include <android/log.h>

#include "NoiseSuppressionEffect.h"

namespace android {
namespace hardware {
namespace audio {
namespace effect {
namespace V2_0 {
namespace implementation {

NoiseSuppressionEffect::NoiseSuppressionEffect(effect_handle_t handle)
        : mEffect(new Effect(handle)) {
}

NoiseSuppressionEffect::~NoiseSuppressionEffect() {}

void NoiseSuppressionEffect::propertiesFromHal(
        const t_ns_settings& halProperties,
        INoiseSuppressionEffect::AllProperties* properties) {
    properties->level = Level(halProperties.level);
    properties->type = Type(halProperties.type);
}

void NoiseSuppressionEffect::propertiesToHal(
        const INoiseSuppressionEffect::AllProperties& properties,
        t_ns_settings* halProperties) {
    halProperties->level = static_cast<uint32_t>(properties.level);
    halProperties->type = static_cast<uint32_t>(properties.type);
}

// Methods from ::android::hardware::audio::effect::V2_0::IEffect follow.
Return<Result> NoiseSuppressionEffect::init() {
    return mEffect->init();
}

Return<Result> NoiseSuppressionEffect::setConfig(
        const EffectConfig& config,
        const sp<IEffectBufferProviderCallback>& inputBufferProvider,
        const sp<IEffectBufferProviderCallback>& outputBufferProvider) {
    return mEffect->setConfig(config, inputBufferProvider, outputBufferProvider);
}

Return<Result> NoiseSuppressionEffect::reset() {
    return mEffect->reset();
}

Return<Result> NoiseSuppressionEffect::enable() {
    return mEffect->enable();
}

Return<Result> NoiseSuppressionEffect::disable() {
    return mEffect->disable();
}

Return<Result> NoiseSuppressionEffect::setDevice(AudioDevice device) {
    return mEffect->setDevice(device);
}

Return<void> NoiseSuppressionEffect::setAndGetVolume(
        const hidl_vec<uint32_t>& volumes, setAndGetVolume_cb _hidl_cb) {
    return mEffect->setAndGetVolume(volumes, _hidl_cb);
}

Return<Result> NoiseSuppressionEffect::volumeChangeNotification(
        const hidl_vec<uint32_t>& volumes) {
    return mEffect->volumeChangeNotification(volumes);
}

Return<Result> NoiseSuppressionEffect::setAudioMode(AudioMode mode) {
    return mEffect->setAudioMode(mode);
}

Return<Result> NoiseSuppressionEffect::setConfigReverse(
        const EffectConfig& config,
        const sp<IEffectBufferProviderCallback>& inputBufferProvider,
        const sp<IEffectBufferProviderCallback>& outputBufferProvider) {
    return mEffect->setConfigReverse(config, inputBufferProvider, outputBufferProvider);
}

Return<Result> NoiseSuppressionEffect::setInputDevice(AudioDevice device) {
    return mEffect->setInputDevice(device);
}

Return<void> NoiseSuppressionEffect::getConfig(getConfig_cb _hidl_cb) {
    return mEffect->getConfig(_hidl_cb);
}

Return<void> NoiseSuppressionEffect::getConfigReverse(getConfigReverse_cb _hidl_cb) {
    return mEffect->getConfigReverse(_hidl_cb);
}

Return<void> NoiseSuppressionEffect::getSupportedAuxChannelsConfigs(
        uint32_t maxConfigs, getSupportedAuxChannelsConfigs_cb _hidl_cb) {
    return mEffect->getSupportedAuxChannelsConfigs(maxConfigs, _hidl_cb);
}

Return<void> NoiseSuppressionEffect::getAuxChannelsConfig(getAuxChannelsConfig_cb _hidl_cb) {
    return mEffect->getAuxChannelsConfig(_hidl_cb);
}

Return<Result> NoiseSuppressionEffect::setAuxChannelsConfig(
        const EffectAuxChannelsConfig& config) {
    return mEffect->setAuxChannelsConfig(config);
}

Return<Result> NoiseSuppressionEffect::setAudioSource(AudioSource source) {
    return mEffect->setAudioSource(source);
}

Return<Result> NoiseSuppressionEffect::offload(const EffectOffloadParameter& param) {
    return mEffect->offload(param);
}

Return<void> NoiseSuppressionEffect::getDescriptor(getDescriptor_cb _hidl_cb) {
    return mEffect->getDescriptor(_hidl_cb);
}

Return<void> NoiseSuppressionEffect::prepareForProcessing(
        prepareForProcessing_cb _hidl_cb) {
    return mEffect->prepareForProcessing(_hidl_cb);
}

Return<Result> NoiseSuppressionEffect::setProcessBuffers(
        const AudioBuffer& inBuffer, const AudioBuffer& outBuffer) {
    return mEffect->setProcessBuffers(inBuffer, outBuffer);
}

Return<void> NoiseSuppressionEffect::command(
        uint32_t commandId,
        const hidl_vec<uint8_t>& data,
        uint32_t resultMaxSize,
        command_cb _hidl_cb) {
    return mEffect->command(commandId, data, resultMaxSize, _hidl_cb);
}

Return<Result> NoiseSuppressionEffect::setParameter(
        const hidl_vec<uint8_t>& parameter, const hidl_vec<uint8_t>& value) {
    return mEffect->setParameter(parameter, value);
}

Return<void> NoiseSuppressionEffect::getParameter(
        const hidl_vec<uint8_t>& parameter,
        uint32_t valueMaxSize,
        getParameter_cb _hidl_cb) {
    return mEffect->getParameter(parameter, valueMaxSize, _hidl_cb);
}

Return<void> NoiseSuppressionEffect::getSupportedConfigsForFeature(
        uint32_t featureId,
        uint32_t maxConfigs,
        uint32_t configSize,
        getSupportedConfigsForFeature_cb _hidl_cb) {
    return mEffect->getSupportedConfigsForFeature(featureId, maxConfigs, configSize, _hidl_cb);
}

Return<void> NoiseSuppressionEffect::getCurrentConfigForFeature(
        uint32_t featureId,
        uint32_t configSize,
        getCurrentConfigForFeature_cb _hidl_cb) {
    return mEffect->getCurrentConfigForFeature(featureId, configSize, _hidl_cb);
}

Return<Result> NoiseSuppressionEffect::setCurrentConfigForFeature(
        uint32_t featureId, const hidl_vec<uint8_t>& configData) {
    return mEffect->setCurrentConfigForFeature(featureId, configData);
}

Return<Result> NoiseSuppressionEffect::close() {
    return mEffect->close();
}

// Methods from ::android::hardware::audio::effect::V2_0::INoiseSuppressionEffect follow.
Return<Result> NoiseSuppressionEffect::setSuppressionLevel(INoiseSuppressionEffect::Level level)  {
    return mEffect->setParam(NS_PARAM_LEVEL, static_cast<int32_t>(level));
}

Return<void> NoiseSuppressionEffect::getSuppressionLevel(getSuppressionLevel_cb _hidl_cb)  {
    int32_t halLevel = 0;
    Result retval = mEffect->getParam(NS_PARAM_LEVEL, halLevel);
    _hidl_cb(retval, Level(halLevel));
    return Void();
}

Return<Result> NoiseSuppressionEffect::setSuppressionType(INoiseSuppressionEffect::Type type)  {
    return mEffect->setParam(NS_PARAM_TYPE, static_cast<int32_t>(type));
}

Return<void> NoiseSuppressionEffect::getSuppressionType(getSuppressionType_cb _hidl_cb)  {
    int32_t halType = 0;
    Result retval = mEffect->getParam(NS_PARAM_TYPE, halType);
    _hidl_cb(retval, Type(halType));
    return Void();
}

Return<Result> NoiseSuppressionEffect::setAllProperties(
        const INoiseSuppressionEffect::AllProperties& properties)  {
    t_ns_settings halProperties;
    propertiesToHal(properties, &halProperties);
    return mEffect->setParam(NS_PARAM_PROPERTIES, halProperties);
}

Return<void> NoiseSuppressionEffect::getAllProperties(getAllProperties_cb _hidl_cb)  {
    t_ns_settings halProperties;
    Result retval = mEffect->getParam(NS_PARAM_PROPERTIES, halProperties);
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
