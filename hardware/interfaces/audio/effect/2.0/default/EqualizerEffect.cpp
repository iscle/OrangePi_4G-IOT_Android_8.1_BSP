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

#include <memory.h>

#define LOG_TAG "Equalizer_HAL"
#include <android/log.h>

#include "EqualizerEffect.h"

namespace android {
namespace hardware {
namespace audio {
namespace effect {
namespace V2_0 {
namespace implementation {

EqualizerEffect::EqualizerEffect(effect_handle_t handle)
        : mEffect(new Effect(handle)) {
}

EqualizerEffect::~EqualizerEffect() {}

void EqualizerEffect::propertiesFromHal(
        const t_equalizer_settings& halProperties,
        IEqualizerEffect::AllProperties* properties) {
    properties->curPreset = halProperties.curPreset;
    // t_equalizer_settings incorrectly defines bandLevels as uint16_t,
    // whereas the actual type of values used by effects is int16_t.
    const int16_t* signedBandLevels =
            reinterpret_cast<const int16_t*>(&halProperties.bandLevels[0]);
    properties->bandLevels.setToExternal(
            const_cast<int16_t*>(signedBandLevels), halProperties.numBands);
}

std::vector<uint8_t> EqualizerEffect::propertiesToHal(
        const IEqualizerEffect::AllProperties& properties,
        t_equalizer_settings** halProperties) {
    size_t bandsSize = properties.bandLevels.size() * sizeof(uint16_t);
    std::vector<uint8_t> halBuffer(sizeof(t_equalizer_settings) + bandsSize, 0);
    *halProperties = reinterpret_cast<t_equalizer_settings*>(&halBuffer[0]);
    (*halProperties)->curPreset = properties.curPreset;
    (*halProperties)->numBands = properties.bandLevels.size();
    memcpy((*halProperties)->bandLevels, &properties.bandLevels[0], bandsSize);
    return halBuffer;
}

// Methods from ::android::hardware::audio::effect::V2_0::IEffect follow.
Return<Result> EqualizerEffect::init() {
    return mEffect->init();
}

Return<Result> EqualizerEffect::setConfig(
        const EffectConfig& config,
        const sp<IEffectBufferProviderCallback>& inputBufferProvider,
        const sp<IEffectBufferProviderCallback>& outputBufferProvider) {
    return mEffect->setConfig(config, inputBufferProvider, outputBufferProvider);
}

Return<Result> EqualizerEffect::reset() {
    return mEffect->reset();
}

Return<Result> EqualizerEffect::enable() {
    return mEffect->enable();
}

Return<Result> EqualizerEffect::disable() {
    return mEffect->disable();
}

Return<Result> EqualizerEffect::setDevice(AudioDevice device) {
    return mEffect->setDevice(device);
}

Return<void> EqualizerEffect::setAndGetVolume(
        const hidl_vec<uint32_t>& volumes, setAndGetVolume_cb _hidl_cb) {
    return mEffect->setAndGetVolume(volumes, _hidl_cb);
}

Return<Result> EqualizerEffect::volumeChangeNotification(
        const hidl_vec<uint32_t>& volumes) {
    return mEffect->volumeChangeNotification(volumes);
}

Return<Result> EqualizerEffect::setAudioMode(AudioMode mode) {
    return mEffect->setAudioMode(mode);
}

Return<Result> EqualizerEffect::setConfigReverse(
        const EffectConfig& config,
        const sp<IEffectBufferProviderCallback>& inputBufferProvider,
        const sp<IEffectBufferProviderCallback>& outputBufferProvider) {
    return mEffect->setConfigReverse(config, inputBufferProvider, outputBufferProvider);
}

Return<Result> EqualizerEffect::setInputDevice(AudioDevice device) {
    return mEffect->setInputDevice(device);
}

Return<void> EqualizerEffect::getConfig(getConfig_cb _hidl_cb) {
    return mEffect->getConfig(_hidl_cb);
}

Return<void> EqualizerEffect::getConfigReverse(getConfigReverse_cb _hidl_cb) {
    return mEffect->getConfigReverse(_hidl_cb);
}

Return<void> EqualizerEffect::getSupportedAuxChannelsConfigs(
        uint32_t maxConfigs, getSupportedAuxChannelsConfigs_cb _hidl_cb) {
    return mEffect->getSupportedAuxChannelsConfigs(maxConfigs, _hidl_cb);
}

Return<void> EqualizerEffect::getAuxChannelsConfig(getAuxChannelsConfig_cb _hidl_cb) {
    return mEffect->getAuxChannelsConfig(_hidl_cb);
}

Return<Result> EqualizerEffect::setAuxChannelsConfig(
        const EffectAuxChannelsConfig& config) {
    return mEffect->setAuxChannelsConfig(config);
}

Return<Result> EqualizerEffect::setAudioSource(AudioSource source) {
    return mEffect->setAudioSource(source);
}

Return<Result> EqualizerEffect::offload(const EffectOffloadParameter& param) {
    return mEffect->offload(param);
}

Return<void> EqualizerEffect::getDescriptor(getDescriptor_cb _hidl_cb) {
    return mEffect->getDescriptor(_hidl_cb);
}

Return<void> EqualizerEffect::prepareForProcessing(
        prepareForProcessing_cb _hidl_cb) {
    return mEffect->prepareForProcessing(_hidl_cb);
}

Return<Result> EqualizerEffect::setProcessBuffers(
        const AudioBuffer& inBuffer, const AudioBuffer& outBuffer) {
    return mEffect->setProcessBuffers(inBuffer, outBuffer);
}

Return<void> EqualizerEffect::command(
        uint32_t commandId,
        const hidl_vec<uint8_t>& data,
        uint32_t resultMaxSize,
        command_cb _hidl_cb) {
    return mEffect->command(commandId, data, resultMaxSize, _hidl_cb);
}

Return<Result> EqualizerEffect::setParameter(
        const hidl_vec<uint8_t>& parameter, const hidl_vec<uint8_t>& value) {
    return mEffect->setParameter(parameter, value);
}

Return<void> EqualizerEffect::getParameter(
        const hidl_vec<uint8_t>& parameter,
        uint32_t valueMaxSize,
        getParameter_cb _hidl_cb) {
    return mEffect->getParameter(parameter, valueMaxSize, _hidl_cb);
}

Return<void> EqualizerEffect::getSupportedConfigsForFeature(
        uint32_t featureId,
        uint32_t maxConfigs,
        uint32_t configSize,
        getSupportedConfigsForFeature_cb _hidl_cb) {
    return mEffect->getSupportedConfigsForFeature(featureId, maxConfigs, configSize, _hidl_cb);
}

Return<void> EqualizerEffect::getCurrentConfigForFeature(
        uint32_t featureId,
        uint32_t configSize,
        getCurrentConfigForFeature_cb _hidl_cb) {
    return mEffect->getCurrentConfigForFeature(featureId, configSize, _hidl_cb);
}

Return<Result> EqualizerEffect::setCurrentConfigForFeature(
        uint32_t featureId, const hidl_vec<uint8_t>& configData) {
    return mEffect->setCurrentConfigForFeature(featureId, configData);
}

Return<Result> EqualizerEffect::close() {
    return mEffect->close();
}

// Methods from ::android::hardware::audio::effect::V2_0::IEqualizerEffect follow.
Return<void> EqualizerEffect::getNumBands(getNumBands_cb _hidl_cb)  {
    return mEffect->getIntegerParam(EQ_PARAM_NUM_BANDS, _hidl_cb);
}

Return<void> EqualizerEffect::getLevelRange(getLevelRange_cb _hidl_cb)  {
    int16_t halLevels[2] = { 0, 0 };
    Result retval = mEffect->getParam(EQ_PARAM_LEVEL_RANGE, halLevels);
    _hidl_cb(retval, halLevels[0], halLevels[1]);
    return Void();
}

Return<Result> EqualizerEffect::setBandLevel(uint16_t band, int16_t level)  {
    return mEffect->setParam(EQ_PARAM_BAND_LEVEL, band, level);
}

Return<void> EqualizerEffect::getBandLevel(uint16_t band, getBandLevel_cb _hidl_cb)  {
    int16_t halLevel = 0;
    Result retval = mEffect->getParam(EQ_PARAM_BAND_LEVEL, band, halLevel);
    _hidl_cb(retval, halLevel);
    return Void();
}

Return<void> EqualizerEffect::getBandCenterFrequency(
        uint16_t band, getBandCenterFrequency_cb _hidl_cb)  {
    uint32_t halFreq = 0;
    Result retval = mEffect->getParam(EQ_PARAM_CENTER_FREQ, band, halFreq);
    _hidl_cb(retval, halFreq);
    return Void();
}

Return<void> EqualizerEffect::getBandFrequencyRange(
        uint16_t band, getBandFrequencyRange_cb _hidl_cb)  {
    uint32_t halFreqs[2] = { 0, 0 };
    Result retval = mEffect->getParam(EQ_PARAM_BAND_FREQ_RANGE, band, halFreqs);
    _hidl_cb(retval, halFreqs[0], halFreqs[1]);
    return Void();
}

Return<void> EqualizerEffect::getBandForFrequency(uint32_t freq, getBandForFrequency_cb _hidl_cb)  {
    uint16_t halBand = 0;
    Result retval = mEffect->getParam(EQ_PARAM_GET_BAND, freq, halBand);
    _hidl_cb(retval, halBand);
    return Void();
}

Return<void> EqualizerEffect::getPresetNames(getPresetNames_cb _hidl_cb)  {
    uint16_t halPresetCount = 0;
    Result retval = mEffect->getParam(EQ_PARAM_GET_NUM_OF_PRESETS, halPresetCount);
    hidl_vec<hidl_string> presetNames;
    if (retval == Result::OK) {
        presetNames.resize(halPresetCount);
        for (uint16_t i = 0; i < halPresetCount; ++i) {
            char halPresetName[EFFECT_STRING_LEN_MAX];
            retval = mEffect->getParam(EQ_PARAM_GET_PRESET_NAME, i, halPresetName);
            if (retval == Result::OK) {
                presetNames[i] = halPresetName;
            } else {
                presetNames.resize(i);
            }
        }
    }
    _hidl_cb(retval, presetNames);
    return Void();
}

Return<Result> EqualizerEffect::setCurrentPreset(uint16_t preset)  {
    return mEffect->setParam(EQ_PARAM_CUR_PRESET, preset);
}

Return<void> EqualizerEffect::getCurrentPreset(getCurrentPreset_cb _hidl_cb)  {
    return mEffect->getIntegerParam(EQ_PARAM_CUR_PRESET, _hidl_cb);
}

Return<Result> EqualizerEffect::setAllProperties(
        const IEqualizerEffect::AllProperties& properties)  {
    t_equalizer_settings *halPropertiesPtr = nullptr;
    std::vector<uint8_t> halBuffer = propertiesToHal(properties, &halPropertiesPtr);
    uint32_t paramId = EQ_PARAM_PROPERTIES;
    return mEffect->setParameterImpl(
            sizeof(paramId), &paramId, halBuffer.size(), halPropertiesPtr);
}

Return<void> EqualizerEffect::getAllProperties(getAllProperties_cb _hidl_cb)  {
    uint16_t numBands = 0;
    Result retval = mEffect->getParam(EQ_PARAM_NUM_BANDS, numBands);
    AllProperties properties;
    if (retval != Result::OK) {
        _hidl_cb(retval, properties);
        return Void();
    }
    size_t valueSize = sizeof(t_equalizer_settings) + sizeof(int16_t) * numBands;
    uint32_t paramId = EQ_PARAM_PROPERTIES;
    retval = mEffect->getParameterImpl(
            sizeof(paramId), &paramId, valueSize,
            [&] (uint32_t, const void* valueData) {
                const t_equalizer_settings* halProperties =
                        reinterpret_cast<const t_equalizer_settings*>(valueData);
                propertiesFromHal(*halProperties, &properties);
            });
    _hidl_cb(retval, properties);
    return Void();
}

} // namespace implementation
}  // namespace V2_0
}  // namespace effect
}  // namespace audio
}  // namespace hardware
}  // namespace android
