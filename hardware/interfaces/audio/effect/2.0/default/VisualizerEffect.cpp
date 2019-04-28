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

#define LOG_TAG "Visualizer_HAL"
#include <system/audio_effects/effect_visualizer.h>
#include <android/log.h>

#include "VisualizerEffect.h"

namespace android {
namespace hardware {
namespace audio {
namespace effect {
namespace V2_0 {
namespace implementation {

VisualizerEffect::VisualizerEffect(effect_handle_t handle)
        : mEffect(new Effect(handle)), mCaptureSize(0), mMeasurementMode(MeasurementMode::NONE) {
}

VisualizerEffect::~VisualizerEffect() {}

// Methods from ::android::hardware::audio::effect::V2_0::IEffect follow.
Return<Result> VisualizerEffect::init() {
    return mEffect->init();
}

Return<Result> VisualizerEffect::setConfig(
        const EffectConfig& config,
        const sp<IEffectBufferProviderCallback>& inputBufferProvider,
        const sp<IEffectBufferProviderCallback>& outputBufferProvider) {
    return mEffect->setConfig(config, inputBufferProvider, outputBufferProvider);
}

Return<Result> VisualizerEffect::reset() {
    return mEffect->reset();
}

Return<Result> VisualizerEffect::enable() {
    return mEffect->enable();
}

Return<Result> VisualizerEffect::disable() {
    return mEffect->disable();
}

Return<Result> VisualizerEffect::setDevice(AudioDevice device) {
    return mEffect->setDevice(device);
}

Return<void> VisualizerEffect::setAndGetVolume(
        const hidl_vec<uint32_t>& volumes, setAndGetVolume_cb _hidl_cb) {
    return mEffect->setAndGetVolume(volumes, _hidl_cb);
}

Return<Result> VisualizerEffect::volumeChangeNotification(
        const hidl_vec<uint32_t>& volumes) {
    return mEffect->volumeChangeNotification(volumes);
}

Return<Result> VisualizerEffect::setAudioMode(AudioMode mode) {
    return mEffect->setAudioMode(mode);
}

Return<Result> VisualizerEffect::setConfigReverse(
        const EffectConfig& config,
        const sp<IEffectBufferProviderCallback>& inputBufferProvider,
        const sp<IEffectBufferProviderCallback>& outputBufferProvider) {
    return mEffect->setConfigReverse(config, inputBufferProvider, outputBufferProvider);
}

Return<Result> VisualizerEffect::setInputDevice(AudioDevice device) {
    return mEffect->setInputDevice(device);
}

Return<void> VisualizerEffect::getConfig(getConfig_cb _hidl_cb) {
    return mEffect->getConfig(_hidl_cb);
}

Return<void> VisualizerEffect::getConfigReverse(getConfigReverse_cb _hidl_cb) {
    return mEffect->getConfigReverse(_hidl_cb);
}

Return<void> VisualizerEffect::getSupportedAuxChannelsConfigs(
        uint32_t maxConfigs, getSupportedAuxChannelsConfigs_cb _hidl_cb) {
    return mEffect->getSupportedAuxChannelsConfigs(maxConfigs, _hidl_cb);
}

Return<void> VisualizerEffect::getAuxChannelsConfig(getAuxChannelsConfig_cb _hidl_cb) {
    return mEffect->getAuxChannelsConfig(_hidl_cb);
}

Return<Result> VisualizerEffect::setAuxChannelsConfig(
        const EffectAuxChannelsConfig& config) {
    return mEffect->setAuxChannelsConfig(config);
}

Return<Result> VisualizerEffect::setAudioSource(AudioSource source) {
    return mEffect->setAudioSource(source);
}

Return<Result> VisualizerEffect::offload(const EffectOffloadParameter& param) {
    return mEffect->offload(param);
}

Return<void> VisualizerEffect::getDescriptor(getDescriptor_cb _hidl_cb) {
    return mEffect->getDescriptor(_hidl_cb);
}

Return<void> VisualizerEffect::prepareForProcessing(
        prepareForProcessing_cb _hidl_cb) {
    return mEffect->prepareForProcessing(_hidl_cb);
}

Return<Result> VisualizerEffect::setProcessBuffers(
        const AudioBuffer& inBuffer, const AudioBuffer& outBuffer) {
    return mEffect->setProcessBuffers(inBuffer, outBuffer);
}

Return<void> VisualizerEffect::command(
        uint32_t commandId,
        const hidl_vec<uint8_t>& data,
        uint32_t resultMaxSize,
        command_cb _hidl_cb) {
    return mEffect->command(commandId, data, resultMaxSize, _hidl_cb);
}

Return<Result> VisualizerEffect::setParameter(
        const hidl_vec<uint8_t>& parameter, const hidl_vec<uint8_t>& value) {
    return mEffect->setParameter(parameter, value);
}

Return<void> VisualizerEffect::getParameter(
        const hidl_vec<uint8_t>& parameter,
        uint32_t valueMaxSize,
        getParameter_cb _hidl_cb) {
    return mEffect->getParameter(parameter, valueMaxSize, _hidl_cb);
}

Return<void> VisualizerEffect::getSupportedConfigsForFeature(
        uint32_t featureId,
        uint32_t maxConfigs,
        uint32_t configSize,
        getSupportedConfigsForFeature_cb _hidl_cb) {
    return mEffect->getSupportedConfigsForFeature(featureId, maxConfigs, configSize, _hidl_cb);
}

Return<void> VisualizerEffect::getCurrentConfigForFeature(
        uint32_t featureId,
        uint32_t configSize,
        getCurrentConfigForFeature_cb _hidl_cb) {
    return mEffect->getCurrentConfigForFeature(featureId, configSize, _hidl_cb);
}

Return<Result> VisualizerEffect::setCurrentConfigForFeature(
        uint32_t featureId, const hidl_vec<uint8_t>& configData) {
    return mEffect->setCurrentConfigForFeature(featureId, configData);
}

Return<Result> VisualizerEffect::close() {
    return mEffect->close();
}

// Methods from ::android::hardware::audio::effect::V2_0::IVisualizerEffect follow.
Return<Result> VisualizerEffect::setCaptureSize(uint16_t captureSize)  {
    Result retval = mEffect->setParam(VISUALIZER_PARAM_CAPTURE_SIZE, captureSize);
    if (retval == Result::OK) {
        mCaptureSize = captureSize;
    }
    return retval;
}

Return<void> VisualizerEffect::getCaptureSize(getCaptureSize_cb _hidl_cb)  {
    return mEffect->getIntegerParam(VISUALIZER_PARAM_CAPTURE_SIZE, _hidl_cb);
}

Return<Result> VisualizerEffect::setScalingMode(IVisualizerEffect::ScalingMode scalingMode)  {
    return mEffect->setParam(VISUALIZER_PARAM_SCALING_MODE, static_cast<int32_t>(scalingMode));
}

Return<void> VisualizerEffect::getScalingMode(getScalingMode_cb _hidl_cb)  {
    int32_t halMode;
    Result retval = mEffect->getParam(VISUALIZER_PARAM_SCALING_MODE, halMode);
    _hidl_cb(retval, ScalingMode(halMode));
    return Void();
}

Return<Result> VisualizerEffect::setLatency(uint32_t latencyMs)  {
    return mEffect->setParam(VISUALIZER_PARAM_LATENCY, latencyMs);
}

Return<void> VisualizerEffect::getLatency(getLatency_cb _hidl_cb)  {
    return mEffect->getIntegerParam(VISUALIZER_PARAM_LATENCY, _hidl_cb);
}

Return<Result> VisualizerEffect::setMeasurementMode(
        IVisualizerEffect::MeasurementMode measurementMode)  {
    Result retval = mEffect->setParam(
            VISUALIZER_PARAM_MEASUREMENT_MODE, static_cast<int32_t>(measurementMode));
    if (retval == Result::OK) {
        mMeasurementMode = measurementMode;
    }
    return retval;
}

Return<void> VisualizerEffect::getMeasurementMode(getMeasurementMode_cb _hidl_cb)  {
    int32_t halMode;
    Result retval = mEffect->getParam(VISUALIZER_PARAM_MEASUREMENT_MODE, halMode);
    _hidl_cb(retval, MeasurementMode(halMode));
    return Void();
}

Return<void> VisualizerEffect::capture(capture_cb _hidl_cb)  {
    if (mCaptureSize == 0) {
        _hidl_cb(Result::NOT_INITIALIZED, hidl_vec<uint8_t>());
        return Void();
    }
    uint32_t halCaptureSize = mCaptureSize;
    uint8_t halCapture[mCaptureSize];
    Result retval = mEffect->sendCommandReturningData(
            VISUALIZER_CMD_CAPTURE, "VISUALIZER_CAPTURE", &halCaptureSize, halCapture);
    hidl_vec<uint8_t> capture;
    if (retval == Result::OK) {
        capture.setToExternal(&halCapture[0], halCaptureSize);
    }
    _hidl_cb(retval, capture);
    return Void();
}

Return<void> VisualizerEffect::measure(measure_cb _hidl_cb)  {
    if (mMeasurementMode == MeasurementMode::NONE) {
        _hidl_cb(Result::NOT_INITIALIZED, Measurement());
        return Void();
    }
    int32_t halMeasurement[MEASUREMENT_COUNT];
    uint32_t halMeasurementSize = sizeof(halMeasurement);
    Result retval = mEffect->sendCommandReturningData(
            VISUALIZER_CMD_MEASURE, "VISUALIZER_MEASURE", &halMeasurementSize, halMeasurement);
    Measurement measurement = { .mode = MeasurementMode::PEAK_RMS };
    measurement.value.peakAndRms.peakMb = 0;
    measurement.value.peakAndRms.rmsMb = 0;
    if (retval == Result::OK) {
        measurement.value.peakAndRms.peakMb = halMeasurement[MEASUREMENT_IDX_PEAK];
        measurement.value.peakAndRms.rmsMb = halMeasurement[MEASUREMENT_IDX_RMS];
    }
    _hidl_cb(retval, measurement);
    return Void();
}

} // namespace implementation
}  // namespace V2_0
}  // namespace effect
}  // namespace audio
}  // namespace hardware
}  // namespace android
