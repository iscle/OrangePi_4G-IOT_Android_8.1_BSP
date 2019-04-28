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

#include <inttypes.h>

#define LOG_TAG "StreamHAL"

#include <hardware/audio.h>
#include <hardware/audio_effect.h>
#include <media/TypeConverter.h>
#include <android/log.h>
#include <utils/SortedVector.h>
#include <utils/Vector.h>

#include "Conversions.h"
#include "EffectMap.h"
#include "Stream.h"

namespace android {
namespace hardware {
namespace audio {
namespace V2_0 {
namespace implementation {

Stream::Stream(audio_stream_t* stream)
        : mStream(stream) {
}

Stream::~Stream() {
    mStream = nullptr;
}

// static
Result Stream::analyzeStatus(const char* funcName, int status) {
    static const std::vector<int> empty;
    return analyzeStatus(funcName, status, empty);
}

template <typename T>
inline bool element_in(T e, const std::vector<T>& v) {
    return std::find(v.begin(), v.end(), e) != v.end();
}

// static
Result Stream::analyzeStatus(const char* funcName, int status,
                             const std::vector<int>& ignoreErrors) {
    if (status != 0 && (ignoreErrors.empty() || !element_in(-status, ignoreErrors))) {
        ALOGW("Error from HAL stream in function %s: %s", funcName, strerror(-status));
    }
    switch (status) {
        case 0: return Result::OK;
        case -EINVAL: return Result::INVALID_ARGUMENTS;
        case -ENODATA: return Result::INVALID_STATE;
        case -ENODEV: return Result::NOT_INITIALIZED;
        case -ENOSYS: return Result::NOT_SUPPORTED;
        default: return Result::INVALID_STATE;
    }
}

char* Stream::halGetParameters(const char* keys) {
    return mStream->get_parameters(mStream, keys);
}

int Stream::halSetParameters(const char* keysAndValues) {
    return mStream->set_parameters(mStream, keysAndValues);
}

// Methods from ::android::hardware::audio::V2_0::IStream follow.
Return<uint64_t> Stream::getFrameSize()  {
    // Needs to be implemented by interface subclasses. But can't be declared as pure virtual,
    // since interface subclasses implementation do not inherit from this class.
    LOG_ALWAYS_FATAL("Stream::getFrameSize is pure abstract");
    return uint64_t {};
}

Return<uint64_t> Stream::getFrameCount()  {
    int halFrameCount;
    Result retval = getParam(AudioParameter::keyFrameCount, &halFrameCount);
    return retval == Result::OK ? halFrameCount : 0;
}

Return<uint64_t> Stream::getBufferSize()  {
    return mStream->get_buffer_size(mStream);
}

Return<uint32_t> Stream::getSampleRate()  {
    return mStream->get_sample_rate(mStream);
}

Return<void> Stream::getSupportedSampleRates(getSupportedSampleRates_cb _hidl_cb)  {
    String8 halListValue;
    Result result = getParam(AudioParameter::keyStreamSupportedSamplingRates, &halListValue);
    hidl_vec<uint32_t> sampleRates;
    SortedVector<uint32_t> halSampleRates;
    if (result == Result::OK) {
        halSampleRates = samplingRatesFromString(
                halListValue.string(), AudioParameter::valueListSeparator);
        sampleRates.setToExternal(halSampleRates.editArray(), halSampleRates.size());
    }
    _hidl_cb(sampleRates);
    return Void();
}

Return<Result> Stream::setSampleRate(uint32_t sampleRateHz)  {
    return setParam(AudioParameter::keySamplingRate, static_cast<int>(sampleRateHz));
}

Return<AudioChannelMask> Stream::getChannelMask()  {
    return AudioChannelMask(mStream->get_channels(mStream));
}

Return<void> Stream::getSupportedChannelMasks(getSupportedChannelMasks_cb _hidl_cb)  {
    String8 halListValue;
    Result result = getParam(AudioParameter::keyStreamSupportedChannels, &halListValue);
    hidl_vec<AudioChannelMask> channelMasks;
    SortedVector<audio_channel_mask_t> halChannelMasks;
    if (result == Result::OK) {
        halChannelMasks = channelMasksFromString(
                halListValue.string(), AudioParameter::valueListSeparator);
        channelMasks.resize(halChannelMasks.size());
        for (size_t i = 0; i < halChannelMasks.size(); ++i) {
            channelMasks[i] = AudioChannelMask(halChannelMasks[i]);
        }
    }
     _hidl_cb(channelMasks);
    return Void();
}

Return<Result> Stream::setChannelMask(AudioChannelMask mask)  {
    return setParam(AudioParameter::keyChannels, static_cast<int>(mask));
}

Return<AudioFormat> Stream::getFormat()  {
    return AudioFormat(mStream->get_format(mStream));
}

Return<void> Stream::getSupportedFormats(getSupportedFormats_cb _hidl_cb)  {
    String8 halListValue;
    Result result = getParam(AudioParameter::keyStreamSupportedFormats, &halListValue);
    hidl_vec<AudioFormat> formats;
    Vector<audio_format_t> halFormats;
    if (result == Result::OK) {
        halFormats = formatsFromString(halListValue.string(), AudioParameter::valueListSeparator);
        formats.resize(halFormats.size());
        for (size_t i = 0; i < halFormats.size(); ++i) {
            formats[i] = AudioFormat(halFormats[i]);
        }
    }
     _hidl_cb(formats);
    return Void();
}

Return<Result> Stream::setFormat(AudioFormat format)  {
    return setParam(AudioParameter::keyFormat, static_cast<int>(format));
}

Return<void> Stream::getAudioProperties(getAudioProperties_cb _hidl_cb)  {
    uint32_t halSampleRate = mStream->get_sample_rate(mStream);
    audio_channel_mask_t halMask = mStream->get_channels(mStream);
    audio_format_t halFormat = mStream->get_format(mStream);
    _hidl_cb(halSampleRate, AudioChannelMask(halMask), AudioFormat(halFormat));
    return Void();
}

Return<Result> Stream::addEffect(uint64_t effectId)  {
    effect_handle_t halEffect = EffectMap::getInstance().get(effectId);
    if (halEffect != NULL) {
        return analyzeStatus("add_audio_effect", mStream->add_audio_effect(mStream, halEffect));
    } else {
        ALOGW("Invalid effect ID passed from client: %" PRIu64, effectId);
        return Result::INVALID_ARGUMENTS;
    }
}

Return<Result> Stream::removeEffect(uint64_t effectId)  {
    effect_handle_t halEffect = EffectMap::getInstance().get(effectId);
    if (halEffect != NULL) {
        return analyzeStatus(
                "remove_audio_effect", mStream->remove_audio_effect(mStream, halEffect));
    } else {
        ALOGW("Invalid effect ID passed from client: %" PRIu64, effectId);
        return Result::INVALID_ARGUMENTS;
    }
}

Return<Result> Stream::standby()  {
    return analyzeStatus("standby", mStream->standby(mStream));
}

Return<AudioDevice> Stream::getDevice()  {
    int device;
    Result retval = getParam(AudioParameter::keyRouting, &device);
    return retval == Result::OK ? static_cast<AudioDevice>(device) : AudioDevice::NONE;
}

Return<Result> Stream::setDevice(const DeviceAddress& address)  {
    char* halDeviceAddress =
            audio_device_address_to_parameter(
                    static_cast<audio_devices_t>(address.device),
                    deviceAddressToHal(address).c_str());
    AudioParameter params((String8(halDeviceAddress)));
    free(halDeviceAddress);
    params.addInt(
            String8(AudioParameter::keyRouting), static_cast<audio_devices_t>(address.device));
    return setParams(params);
}

Return<Result> Stream::setConnectedState(const DeviceAddress& address, bool connected)  {
    return setParam(
            connected ? AudioParameter::keyStreamConnect : AudioParameter::keyStreamDisconnect,
            deviceAddressToHal(address).c_str());
}

Return<Result> Stream::setHwAvSync(uint32_t hwAvSync)  {
    return setParam(AudioParameter::keyStreamHwAvSync, static_cast<int>(hwAvSync));
}

Return<void> Stream::getParameters(const hidl_vec<hidl_string>& keys, getParameters_cb _hidl_cb)  {
    getParametersImpl(keys, _hidl_cb);
    return Void();
}

Return<Result> Stream::setParameters(const hidl_vec<ParameterValue>& parameters)  {
    return setParametersImpl(parameters);
}

Return<void> Stream::debugDump(const hidl_handle& fd)  {
    if (fd.getNativeHandle() != nullptr && fd->numFds == 1) {
        analyzeStatus("dump", mStream->dump(mStream, fd->data[0]));
    }
    return Void();
}

Return<Result>  Stream::start() {
    return Result::NOT_SUPPORTED;
}

Return<Result>  Stream::stop() {
    return Result::NOT_SUPPORTED;
}

Return<void>  Stream::createMmapBuffer(int32_t minSizeFrames __unused,
                                       createMmapBuffer_cb _hidl_cb) {
    Result retval(Result::NOT_SUPPORTED);
    MmapBufferInfo info;
    _hidl_cb(retval, info);
    return Void();
}

Return<void>  Stream::getMmapPosition(getMmapPosition_cb _hidl_cb) {
    Result retval(Result::NOT_SUPPORTED);
    MmapPosition position;
    _hidl_cb(retval, position);
    return Void();
}

Return<Result> Stream::close()  {
    return Result::NOT_SUPPORTED;
}

} // namespace implementation
}  // namespace V2_0
}  // namespace audio
}  // namespace hardware
}  // namespace android
