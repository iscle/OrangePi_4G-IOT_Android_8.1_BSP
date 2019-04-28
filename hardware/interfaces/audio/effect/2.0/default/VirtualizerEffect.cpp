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

#define LOG_TAG "Virtualizer_HAL"
#include <system/audio_effects/effect_virtualizer.h>
#include <android/log.h>

#include "VirtualizerEffect.h"

namespace android {
namespace hardware {
namespace audio {
namespace effect {
namespace V2_0 {
namespace implementation {

VirtualizerEffect::VirtualizerEffect(effect_handle_t handle)
        : mEffect(new Effect(handle)) {
}

VirtualizerEffect::~VirtualizerEffect() {}

void VirtualizerEffect::speakerAnglesFromHal(
        const int32_t* halAngles, uint32_t channelCount, hidl_vec<SpeakerAngle>& speakerAngles) {
    speakerAngles.resize(channelCount);
    for (uint32_t i = 0; i < channelCount; ++i) {
        speakerAngles[i].mask = AudioChannelMask(*halAngles++);
        speakerAngles[i].azimuth = *halAngles++;
        speakerAngles[i].elevation = *halAngles++;
    }
}

// Methods from ::android::hardware::audio::effect::V2_0::IEffect follow.
Return<Result> VirtualizerEffect::init() {
    return mEffect->init();
}

Return<Result> VirtualizerEffect::setConfig(
        const EffectConfig& config,
        const sp<IEffectBufferProviderCallback>& inputBufferProvider,
        const sp<IEffectBufferProviderCallback>& outputBufferProvider) {
    return mEffect->setConfig(config, inputBufferProvider, outputBufferProvider);
}

Return<Result> VirtualizerEffect::reset() {
    return mEffect->reset();
}

Return<Result> VirtualizerEffect::enable() {
    return mEffect->enable();
}

Return<Result> VirtualizerEffect::disable() {
    return mEffect->disable();
}

Return<Result> VirtualizerEffect::setDevice(AudioDevice device) {
    return mEffect->setDevice(device);
}

Return<void> VirtualizerEffect::setAndGetVolume(
        const hidl_vec<uint32_t>& volumes, setAndGetVolume_cb _hidl_cb) {
    return mEffect->setAndGetVolume(volumes, _hidl_cb);
}

Return<Result> VirtualizerEffect::volumeChangeNotification(
        const hidl_vec<uint32_t>& volumes) {
    return mEffect->volumeChangeNotification(volumes);
}

Return<Result> VirtualizerEffect::setAudioMode(AudioMode mode) {
    return mEffect->setAudioMode(mode);
}

Return<Result> VirtualizerEffect::setConfigReverse(
        const EffectConfig& config,
        const sp<IEffectBufferProviderCallback>& inputBufferProvider,
        const sp<IEffectBufferProviderCallback>& outputBufferProvider) {
    return mEffect->setConfigReverse(config, inputBufferProvider, outputBufferProvider);
}

Return<Result> VirtualizerEffect::setInputDevice(AudioDevice device) {
    return mEffect->setInputDevice(device);
}

Return<void> VirtualizerEffect::getConfig(getConfig_cb _hidl_cb) {
    return mEffect->getConfig(_hidl_cb);
}

Return<void> VirtualizerEffect::getConfigReverse(getConfigReverse_cb _hidl_cb) {
    return mEffect->getConfigReverse(_hidl_cb);
}

Return<void> VirtualizerEffect::getSupportedAuxChannelsConfigs(
        uint32_t maxConfigs, getSupportedAuxChannelsConfigs_cb _hidl_cb) {
    return mEffect->getSupportedAuxChannelsConfigs(maxConfigs, _hidl_cb);
}

Return<void> VirtualizerEffect::getAuxChannelsConfig(getAuxChannelsConfig_cb _hidl_cb) {
    return mEffect->getAuxChannelsConfig(_hidl_cb);
}

Return<Result> VirtualizerEffect::setAuxChannelsConfig(
        const EffectAuxChannelsConfig& config) {
    return mEffect->setAuxChannelsConfig(config);
}

Return<Result> VirtualizerEffect::setAudioSource(AudioSource source) {
    return mEffect->setAudioSource(source);
}

Return<Result> VirtualizerEffect::offload(const EffectOffloadParameter& param) {
    return mEffect->offload(param);
}

Return<void> VirtualizerEffect::getDescriptor(getDescriptor_cb _hidl_cb) {
    return mEffect->getDescriptor(_hidl_cb);
}

Return<void> VirtualizerEffect::prepareForProcessing(
        prepareForProcessing_cb _hidl_cb) {
    return mEffect->prepareForProcessing(_hidl_cb);
}

Return<Result> VirtualizerEffect::setProcessBuffers(
        const AudioBuffer& inBuffer, const AudioBuffer& outBuffer) {
    return mEffect->setProcessBuffers(inBuffer, outBuffer);
}

Return<void> VirtualizerEffect::command(
        uint32_t commandId,
        const hidl_vec<uint8_t>& data,
        uint32_t resultMaxSize,
        command_cb _hidl_cb) {
    return mEffect->command(commandId, data, resultMaxSize, _hidl_cb);
}

Return<Result> VirtualizerEffect::setParameter(
        const hidl_vec<uint8_t>& parameter, const hidl_vec<uint8_t>& value) {
    return mEffect->setParameter(parameter, value);
}

Return<void> VirtualizerEffect::getParameter(
        const hidl_vec<uint8_t>& parameter,
        uint32_t valueMaxSize,
        getParameter_cb _hidl_cb) {
    return mEffect->getParameter(parameter, valueMaxSize, _hidl_cb);
}

Return<void> VirtualizerEffect::getSupportedConfigsForFeature(
        uint32_t featureId,
        uint32_t maxConfigs,
        uint32_t configSize,
        getSupportedConfigsForFeature_cb _hidl_cb) {
    return mEffect->getSupportedConfigsForFeature(featureId, maxConfigs, configSize, _hidl_cb);
}

Return<void> VirtualizerEffect::getCurrentConfigForFeature(
        uint32_t featureId,
        uint32_t configSize,
        getCurrentConfigForFeature_cb _hidl_cb) {
    return mEffect->getCurrentConfigForFeature(featureId, configSize, _hidl_cb);
}

Return<Result> VirtualizerEffect::setCurrentConfigForFeature(
        uint32_t featureId, const hidl_vec<uint8_t>& configData) {
    return mEffect->setCurrentConfigForFeature(featureId, configData);
}

Return<Result> VirtualizerEffect::close() {
    return mEffect->close();
}

// Methods from ::android::hardware::audio::effect::V2_0::IVirtualizerEffect follow.
Return<bool> VirtualizerEffect::isStrengthSupported()  {
    bool halSupported = false;
    mEffect->getParam(VIRTUALIZER_PARAM_STRENGTH_SUPPORTED, halSupported);
    return halSupported;
}

Return<Result> VirtualizerEffect::setStrength(uint16_t strength)  {
    return mEffect->setParam(VIRTUALIZER_PARAM_STRENGTH, strength);
}

Return<void> VirtualizerEffect::getStrength(getStrength_cb _hidl_cb)  {
    return mEffect->getIntegerParam(VIRTUALIZER_PARAM_STRENGTH, _hidl_cb);
}

Return<void> VirtualizerEffect::getVirtualSpeakerAngles(
        AudioChannelMask mask, AudioDevice device, getVirtualSpeakerAngles_cb _hidl_cb)  {
    uint32_t channelCount = audio_channel_count_from_out_mask(
            static_cast<audio_channel_mask_t>(mask));
    size_t halSpeakerAnglesSize = sizeof(int32_t) * 3 * channelCount;
    uint32_t halParam[3] = {
        VIRTUALIZER_PARAM_VIRTUAL_SPEAKER_ANGLES,
        static_cast<audio_channel_mask_t>(mask),
        static_cast<audio_devices_t>(device)
    };
    hidl_vec<SpeakerAngle> speakerAngles;
    Result retval = mEffect->getParameterImpl(
            sizeof(halParam), halParam,
            halSpeakerAnglesSize,
            [&] (uint32_t valueSize, const void* valueData) {
                if (valueSize > halSpeakerAnglesSize) {
                    valueSize = halSpeakerAnglesSize;
                } else if (valueSize < halSpeakerAnglesSize) {
                    channelCount = valueSize / (sizeof(int32_t) * 3);
                }
                speakerAnglesFromHal(
                        reinterpret_cast<const int32_t*>(valueData), channelCount, speakerAngles);
            });
    _hidl_cb(retval, speakerAngles);
    return Void();
}

Return<Result> VirtualizerEffect::forceVirtualizationMode(AudioDevice device)  {
    return mEffect->setParam(
            VIRTUALIZER_PARAM_FORCE_VIRTUALIZATION_MODE, static_cast<audio_devices_t>(device));
}

Return<void> VirtualizerEffect::getVirtualizationMode(getVirtualizationMode_cb _hidl_cb)  {
    uint32_t halMode = 0;
    Result retval = mEffect->getParam(VIRTUALIZER_PARAM_FORCE_VIRTUALIZATION_MODE, halMode);
    _hidl_cb(retval, AudioDevice(halMode));
    return Void();
}

} // namespace implementation
}  // namespace V2_0
}  // namespace effect
}  // namespace audio
}  // namespace hardware
}  // namespace android
