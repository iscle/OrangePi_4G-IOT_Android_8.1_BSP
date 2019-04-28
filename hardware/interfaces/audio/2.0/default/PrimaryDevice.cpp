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

#define LOG_TAG "PrimaryDeviceHAL"

#include "PrimaryDevice.h"
#include "Util.h"

namespace android {
namespace hardware {
namespace audio {
namespace V2_0 {
namespace implementation {

PrimaryDevice::PrimaryDevice(audio_hw_device_t* device)
        : mDevice(new Device(device)) {
}

PrimaryDevice::~PrimaryDevice() {}

// Methods from ::android::hardware::audio::V2_0::IDevice follow.
Return<Result> PrimaryDevice::initCheck() {
    return mDevice->initCheck();
}

Return<Result> PrimaryDevice::setMasterVolume(float volume) {
    return mDevice->setMasterVolume(volume);
}

Return<void> PrimaryDevice::getMasterVolume(getMasterVolume_cb _hidl_cb) {
    return mDevice->getMasterVolume(_hidl_cb);
}

Return<Result> PrimaryDevice::setMicMute(bool mute) {
    return mDevice->setMicMute(mute);
}

Return<void> PrimaryDevice::getMicMute(getMicMute_cb _hidl_cb) {
    return mDevice->getMicMute(_hidl_cb);
}

Return<Result> PrimaryDevice::setMasterMute(bool mute) {
    return mDevice->setMasterMute(mute);
}

Return<void> PrimaryDevice::getMasterMute(getMasterMute_cb _hidl_cb) {
    return mDevice->getMasterMute(_hidl_cb);
}

Return<void> PrimaryDevice::getInputBufferSize(const AudioConfig& config,
                                               getInputBufferSize_cb _hidl_cb) {
    return mDevice->getInputBufferSize(config, _hidl_cb);
}

Return<void> PrimaryDevice::openOutputStream(int32_t ioHandle,
                                             const DeviceAddress& device,
                                             const AudioConfig& config,
                                             AudioOutputFlag flags,
                                             openOutputStream_cb _hidl_cb) {
    return mDevice->openOutputStream(ioHandle, device, config, flags, _hidl_cb);
}

Return<void> PrimaryDevice::openInputStream(
    int32_t ioHandle, const DeviceAddress& device, const AudioConfig& config,
    AudioInputFlag flags, AudioSource source, openInputStream_cb _hidl_cb) {
    return mDevice->openInputStream(ioHandle, device, config, flags, source,
                                    _hidl_cb);
}

Return<bool> PrimaryDevice::supportsAudioPatches() {
    return mDevice->supportsAudioPatches();
}

Return<void> PrimaryDevice::createAudioPatch(
    const hidl_vec<AudioPortConfig>& sources,
    const hidl_vec<AudioPortConfig>& sinks, createAudioPatch_cb _hidl_cb) {
    return mDevice->createAudioPatch(sources, sinks, _hidl_cb);
}

Return<Result> PrimaryDevice::releaseAudioPatch(int32_t patch) {
    return mDevice->releaseAudioPatch(patch);
}

Return<void> PrimaryDevice::getAudioPort(const AudioPort& port,
                                         getAudioPort_cb _hidl_cb) {
    return mDevice->getAudioPort(port, _hidl_cb);
}

Return<Result> PrimaryDevice::setAudioPortConfig(
    const AudioPortConfig& config) {
    return mDevice->setAudioPortConfig(config);
}

Return<AudioHwSync> PrimaryDevice::getHwAvSync() {
    return mDevice->getHwAvSync();
}

Return<Result> PrimaryDevice::setScreenState(bool turnedOn) {
    return mDevice->setScreenState(turnedOn);
}

Return<void> PrimaryDevice::getParameters(const hidl_vec<hidl_string>& keys,
                                          getParameters_cb _hidl_cb) {
    return mDevice->getParameters(keys, _hidl_cb);
}

Return<Result> PrimaryDevice::setParameters(
    const hidl_vec<ParameterValue>& parameters) {
    return mDevice->setParameters(parameters);
}

Return<void> PrimaryDevice::debugDump(const hidl_handle& fd) {
    return mDevice->debugDump(fd);
}

// Methods from ::android::hardware::audio::V2_0::IPrimaryDevice follow.
Return<Result> PrimaryDevice::setVoiceVolume(float volume) {
    if (!isGainNormalized(volume)) {
        ALOGW("Can not set a voice volume (%f) outside [0,1]", volume);
        return Result::INVALID_ARGUMENTS;
    }
    return mDevice->analyzeStatus(
        "set_voice_volume",
        mDevice->device()->set_voice_volume(mDevice->device(), volume));
}

Return<Result> PrimaryDevice::setMode(AudioMode mode) {
    // INVALID, CURRENT, CNT, MAX are reserved for internal use.
    // TODO: remove the values from the HIDL interface
    switch (mode) {
        case AudioMode::NORMAL:
        case AudioMode::RINGTONE:
        case AudioMode::IN_CALL:
        case AudioMode::IN_COMMUNICATION:
            break;  // Valid values
        default:
            return Result::INVALID_ARGUMENTS;
    };

    return mDevice->analyzeStatus(
        "set_mode", mDevice->device()->set_mode(
                        mDevice->device(), static_cast<audio_mode_t>(mode)));
}

Return<void> PrimaryDevice::getBtScoNrecEnabled(
    getBtScoNrecEnabled_cb _hidl_cb) {
    bool enabled;
    Result retval = mDevice->getParam(AudioParameter::keyBtNrec, &enabled);
    _hidl_cb(retval, enabled);
    return Void();
}

Return<Result> PrimaryDevice::setBtScoNrecEnabled(bool enabled) {
    return mDevice->setParam(AudioParameter::keyBtNrec, enabled);
}

Return<void> PrimaryDevice::getBtScoWidebandEnabled(
    getBtScoWidebandEnabled_cb _hidl_cb) {
    bool enabled;
    Result retval = mDevice->getParam(AUDIO_PARAMETER_KEY_BT_SCO_WB, &enabled);
    _hidl_cb(retval, enabled);
    return Void();
}

Return<Result> PrimaryDevice::setBtScoWidebandEnabled(bool enabled) {
    return mDevice->setParam(AUDIO_PARAMETER_KEY_BT_SCO_WB, enabled);
}

Return<void> PrimaryDevice::getTtyMode(getTtyMode_cb _hidl_cb) {
    int halMode;
    Result retval = mDevice->getParam(AUDIO_PARAMETER_KEY_TTY_MODE, &halMode);
    TtyMode mode = retval == Result::OK ? TtyMode(halMode) : TtyMode::OFF;
    _hidl_cb(retval, mode);
    return Void();
}

Return<Result> PrimaryDevice::setTtyMode(IPrimaryDevice::TtyMode mode) {
    return mDevice->setParam(AUDIO_PARAMETER_KEY_TTY_MODE,
                             static_cast<int>(mode));
}

Return<void> PrimaryDevice::getHacEnabled(getHacEnabled_cb _hidl_cb) {
    bool enabled;
    Result retval = mDevice->getParam(AUDIO_PARAMETER_KEY_HAC, &enabled);
    _hidl_cb(retval, enabled);
    return Void();
}

Return<Result> PrimaryDevice::setHacEnabled(bool enabled) {
    return mDevice->setParam(AUDIO_PARAMETER_KEY_HAC, enabled);
}

}  // namespace implementation
}  // namespace V2_0
}  // namespace audio
}  // namespace hardware
}  // namespace android
