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

#define LOG_TAG "StreamHalLocal"
//#define LOG_NDEBUG 0

#include <hardware/audio.h>
#include <utils/Log.h>

#include "DeviceHalLocal.h"
#include "EffectHalLocal.h"
#include "StreamHalLocal.h"

namespace android {

StreamHalLocal::StreamHalLocal(audio_stream_t *stream, sp<DeviceHalLocal> device)
        : mDevice(device),
          mStream(stream) {
    // Instrument audio signal power logging.
    // Note: This assumes channel mask, format, and sample rate do not change after creation.
    if (mStream != nullptr && mStreamPowerLog.isUserDebugOrEngBuild()) {
        mStreamPowerLog.init(mStream->get_sample_rate(mStream),
                mStream->get_channels(mStream),
                mStream->get_format(mStream));
    }
}

StreamHalLocal::~StreamHalLocal() {
    mStream = 0;
    mDevice.clear();
}

status_t StreamHalLocal::getSampleRate(uint32_t *rate) {
    *rate = mStream->get_sample_rate(mStream);
    return OK;
}

status_t StreamHalLocal::getBufferSize(size_t *size) {
    *size = mStream->get_buffer_size(mStream);
    return OK;
}

status_t StreamHalLocal::getChannelMask(audio_channel_mask_t *mask) {
    *mask = mStream->get_channels(mStream);
    return OK;
}

status_t StreamHalLocal::getFormat(audio_format_t *format) {
    *format = mStream->get_format(mStream);
    return OK;
}

status_t StreamHalLocal::getAudioProperties(
        uint32_t *sampleRate, audio_channel_mask_t *mask, audio_format_t *format) {
    *sampleRate = mStream->get_sample_rate(mStream);
    *mask = mStream->get_channels(mStream);
    *format = mStream->get_format(mStream);
    return OK;
}

status_t StreamHalLocal::setParameters(const String8& kvPairs) {
    return mStream->set_parameters(mStream, kvPairs.string());
}

status_t StreamHalLocal::getParameters(const String8& keys, String8 *values) {
    char *halValues = mStream->get_parameters(mStream, keys.string());
    if (halValues != NULL) {
        values->setTo(halValues);
        free(halValues);
    } else {
        values->clear();
    }
    return OK;
}

status_t StreamHalLocal::addEffect(sp<EffectHalInterface> effect) {
    LOG_ALWAYS_FATAL_IF(!effect->isLocal(), "Only local effects can be added for a local stream");
    return mStream->add_audio_effect(mStream,
            static_cast<EffectHalLocal*>(effect.get())->handle());
}

status_t StreamHalLocal::removeEffect(sp<EffectHalInterface> effect) {
    LOG_ALWAYS_FATAL_IF(!effect->isLocal(), "Only local effects can be removed for a local stream");
    return mStream->remove_audio_effect(mStream,
            static_cast<EffectHalLocal*>(effect.get())->handle());
}

status_t StreamHalLocal::standby() {
    return mStream->standby(mStream);
}

status_t StreamHalLocal::dump(int fd) {
    status_t status = mStream->dump(mStream, fd);
    mStreamPowerLog.dump(fd);
    return status;
}

status_t StreamHalLocal::setHalThreadPriority(int) {
    // Don't need to do anything as local hal is executed by audioflinger directly
    // on the same thread.
    return OK;
}

StreamOutHalLocal::StreamOutHalLocal(audio_stream_out_t *stream, sp<DeviceHalLocal> device)
        : StreamHalLocal(&stream->common, device), mStream(stream) {
}

StreamOutHalLocal::~StreamOutHalLocal() {
    mCallback.clear();
    mDevice->closeOutputStream(mStream);
    mStream = 0;
}

status_t StreamOutHalLocal::getFrameSize(size_t *size) {
    *size = audio_stream_out_frame_size(mStream);
    return OK;
}

status_t StreamOutHalLocal::getLatency(uint32_t *latency) {
    *latency = mStream->get_latency(mStream);
    return OK;
}

status_t StreamOutHalLocal::setVolume(float left, float right) {
    if (mStream->set_volume == NULL) return INVALID_OPERATION;
    return mStream->set_volume(mStream, left, right);
}

status_t StreamOutHalLocal::write(const void *buffer, size_t bytes, size_t *written) {
    ssize_t writeResult = mStream->write(mStream, buffer, bytes);
    if (writeResult > 0) {
        *written = writeResult;
        mStreamPowerLog.log(buffer, *written);
        return OK;
    } else {
        *written = 0;
        return writeResult;
    }
}

status_t StreamOutHalLocal::getRenderPosition(uint32_t *dspFrames) {
    return mStream->get_render_position(mStream, dspFrames);
}

status_t StreamOutHalLocal::getNextWriteTimestamp(int64_t *timestamp) {
    if (mStream->get_next_write_timestamp == NULL) return INVALID_OPERATION;
    return mStream->get_next_write_timestamp(mStream, timestamp);
}

status_t StreamOutHalLocal::setCallback(wp<StreamOutHalInterfaceCallback> callback) {
    if (mStream->set_callback == NULL) return INVALID_OPERATION;
    status_t result = mStream->set_callback(mStream, StreamOutHalLocal::asyncCallback, this);
    if (result == OK) {
        mCallback = callback;
    }
    return result;
}

// static
int StreamOutHalLocal::asyncCallback(stream_callback_event_t event, void*, void *cookie) {
    // We act as if we gave a wp<StreamOutHalLocal> to HAL. This way we should handle
    // correctly the case when the callback is invoked while StreamOutHalLocal's destructor is
    // already running, because the destructor is invoked after the refcount has been atomically
    // decremented.
    wp<StreamOutHalLocal> weakSelf(static_cast<StreamOutHalLocal*>(cookie));
    sp<StreamOutHalLocal> self = weakSelf.promote();
    if (self == 0) return 0;
    sp<StreamOutHalInterfaceCallback> callback = self->mCallback.promote();
    if (callback == 0) return 0;
    ALOGV("asyncCallback() event %d", event);
    switch (event) {
        case STREAM_CBK_EVENT_WRITE_READY:
            callback->onWriteReady();
            break;
        case STREAM_CBK_EVENT_DRAIN_READY:
            callback->onDrainReady();
            break;
        case STREAM_CBK_EVENT_ERROR:
            callback->onError();
            break;
        default:
            ALOGW("asyncCallback() unknown event %d", event);
            break;
    }
    return 0;
}

status_t StreamOutHalLocal::supportsPauseAndResume(bool *supportsPause, bool *supportsResume) {
    *supportsPause = mStream->pause != NULL;
    *supportsResume = mStream->resume != NULL;
    return OK;
}

status_t StreamOutHalLocal::pause() {
    if (mStream->pause == NULL) return INVALID_OPERATION;
    return mStream->pause(mStream);
}

status_t StreamOutHalLocal::resume() {
    if (mStream->resume == NULL) return INVALID_OPERATION;
    return mStream->resume(mStream);
}

status_t StreamOutHalLocal::supportsDrain(bool *supportsDrain) {
    *supportsDrain = mStream->drain != NULL;
    return OK;
}

status_t StreamOutHalLocal::drain(bool earlyNotify) {
    if (mStream->drain == NULL) return INVALID_OPERATION;
    return mStream->drain(mStream, earlyNotify ? AUDIO_DRAIN_EARLY_NOTIFY : AUDIO_DRAIN_ALL);
}

status_t StreamOutHalLocal::flush() {
    if (mStream->flush == NULL) return INVALID_OPERATION;
    return mStream->flush(mStream);
}

status_t StreamOutHalLocal::getPresentationPosition(uint64_t *frames, struct timespec *timestamp) {
    if (mStream->get_presentation_position == NULL) return INVALID_OPERATION;
    return mStream->get_presentation_position(mStream, frames, timestamp);
}

status_t StreamOutHalLocal::start() {
    if (mStream->start == NULL) return INVALID_OPERATION;
    return mStream->start(mStream);
}

status_t StreamOutHalLocal::stop() {
    if (mStream->stop == NULL) return INVALID_OPERATION;
    return mStream->stop(mStream);
}

status_t StreamOutHalLocal::createMmapBuffer(int32_t minSizeFrames,
                                  struct audio_mmap_buffer_info *info) {
    if (mStream->create_mmap_buffer == NULL) return INVALID_OPERATION;
    return mStream->create_mmap_buffer(mStream, minSizeFrames, info);
}

status_t StreamOutHalLocal::getMmapPosition(struct audio_mmap_position *position) {
    if (mStream->get_mmap_position == NULL) return INVALID_OPERATION;
    return mStream->get_mmap_position(mStream, position);
}

StreamInHalLocal::StreamInHalLocal(audio_stream_in_t *stream, sp<DeviceHalLocal> device)
        : StreamHalLocal(&stream->common, device), mStream(stream) {
}

StreamInHalLocal::~StreamInHalLocal() {
    mDevice->closeInputStream(mStream);
    mStream = 0;
}

status_t StreamInHalLocal::getFrameSize(size_t *size) {
    *size = audio_stream_in_frame_size(mStream);
    return OK;
}

status_t StreamInHalLocal::setGain(float gain) {
    return mStream->set_gain(mStream, gain);
}

status_t StreamInHalLocal::read(void *buffer, size_t bytes, size_t *read) {
    ssize_t readResult = mStream->read(mStream, buffer, bytes);
    if (readResult > 0) {
        *read = readResult;
        mStreamPowerLog.log( buffer, *read);
        return OK;
    } else {
        *read = 0;
        return readResult;
    }
}

status_t StreamInHalLocal::getInputFramesLost(uint32_t *framesLost) {
    *framesLost = mStream->get_input_frames_lost(mStream);
    return OK;
}

status_t StreamInHalLocal::getCapturePosition(int64_t *frames, int64_t *time) {
    if (mStream->get_capture_position == NULL) return INVALID_OPERATION;
    return mStream->get_capture_position(mStream, frames, time);
}

status_t StreamInHalLocal::start() {
    if (mStream->start == NULL) return INVALID_OPERATION;
    return mStream->start(mStream);
}

status_t StreamInHalLocal::stop() {
    if (mStream->stop == NULL) return INVALID_OPERATION;
    return mStream->stop(mStream);
}

status_t StreamInHalLocal::createMmapBuffer(int32_t minSizeFrames,
                                  struct audio_mmap_buffer_info *info) {
    if (mStream->create_mmap_buffer == NULL) return INVALID_OPERATION;
    return mStream->create_mmap_buffer(mStream, minSizeFrames, info);
}

status_t StreamInHalLocal::getMmapPosition(struct audio_mmap_position *position) {
    if (mStream->get_mmap_position == NULL) return INVALID_OPERATION;
    return mStream->get_mmap_position(mStream, position);
}

} // namespace android
