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

#define LOG_TAG "AAudio"
//#define LOG_NDEBUG 0
#include <utils/Log.h>

#include <time.h>
#include <pthread.h>

#include <aaudio/AAudio.h>
#include <aaudio/AAudioTesting.h>

#include "AudioClock.h"
#include "AudioStreamBuilder.h"
#include "AudioStream.h"
#include "binding/AAudioCommon.h"
#include "client/AudioStreamInternal.h"

using namespace aaudio;

// Macros for common code that includes a return.
// TODO Consider using do{}while(0) construct. I tried but it hung AndroidStudio
#define CONVERT_BUILDER_HANDLE_OR_RETURN() \
    convertAAudioBuilderToStreamBuilder(builder);

#define COMMON_GET_FROM_BUILDER_OR_RETURN(resultPtr) \
    CONVERT_BUILDER_HANDLE_OR_RETURN() \
    if ((resultPtr) == nullptr) { \
        return AAUDIO_ERROR_NULL; \
    }

#define AAUDIO_CASE_ENUM(name) case name: return #name

AAUDIO_API const char * AAudio_convertResultToText(aaudio_result_t returnCode) {
    switch (returnCode) {
        AAUDIO_CASE_ENUM(AAUDIO_OK);
        AAUDIO_CASE_ENUM(AAUDIO_ERROR_DISCONNECTED);
        AAUDIO_CASE_ENUM(AAUDIO_ERROR_ILLEGAL_ARGUMENT);
        // reserved
        AAUDIO_CASE_ENUM(AAUDIO_ERROR_INTERNAL);
        AAUDIO_CASE_ENUM(AAUDIO_ERROR_INVALID_STATE);
        // reserved
        // reserved
        AAUDIO_CASE_ENUM(AAUDIO_ERROR_INVALID_HANDLE);
         // reserved
        AAUDIO_CASE_ENUM(AAUDIO_ERROR_UNIMPLEMENTED);
        AAUDIO_CASE_ENUM(AAUDIO_ERROR_UNAVAILABLE);
        AAUDIO_CASE_ENUM(AAUDIO_ERROR_NO_FREE_HANDLES);
        AAUDIO_CASE_ENUM(AAUDIO_ERROR_NO_MEMORY);
        AAUDIO_CASE_ENUM(AAUDIO_ERROR_NULL);
        AAUDIO_CASE_ENUM(AAUDIO_ERROR_TIMEOUT);
        AAUDIO_CASE_ENUM(AAUDIO_ERROR_WOULD_BLOCK);
        AAUDIO_CASE_ENUM(AAUDIO_ERROR_INVALID_FORMAT);
        AAUDIO_CASE_ENUM(AAUDIO_ERROR_OUT_OF_RANGE);
        AAUDIO_CASE_ENUM(AAUDIO_ERROR_NO_SERVICE);
        AAUDIO_CASE_ENUM(AAUDIO_ERROR_INVALID_RATE);
    }
    return "Unrecognized AAudio error.";
}

AAUDIO_API const char * AAudio_convertStreamStateToText(aaudio_stream_state_t state) {
    switch (state) {
        AAUDIO_CASE_ENUM(AAUDIO_STREAM_STATE_UNINITIALIZED);
        AAUDIO_CASE_ENUM(AAUDIO_STREAM_STATE_UNKNOWN);
        AAUDIO_CASE_ENUM(AAUDIO_STREAM_STATE_OPEN);
        AAUDIO_CASE_ENUM(AAUDIO_STREAM_STATE_STARTING);
        AAUDIO_CASE_ENUM(AAUDIO_STREAM_STATE_STARTED);
        AAUDIO_CASE_ENUM(AAUDIO_STREAM_STATE_PAUSING);
        AAUDIO_CASE_ENUM(AAUDIO_STREAM_STATE_PAUSED);
        AAUDIO_CASE_ENUM(AAUDIO_STREAM_STATE_FLUSHING);
        AAUDIO_CASE_ENUM(AAUDIO_STREAM_STATE_FLUSHED);
        AAUDIO_CASE_ENUM(AAUDIO_STREAM_STATE_STOPPING);
        AAUDIO_CASE_ENUM(AAUDIO_STREAM_STATE_STOPPED);
        AAUDIO_CASE_ENUM(AAUDIO_STREAM_STATE_DISCONNECTED);
        AAUDIO_CASE_ENUM(AAUDIO_STREAM_STATE_CLOSING);
        AAUDIO_CASE_ENUM(AAUDIO_STREAM_STATE_CLOSED);
    }
    return "Unrecognized AAudio state.";
}

#undef AAUDIO_CASE_ENUM


/******************************************
 * Static globals.
 */
static aaudio_policy_t s_MMapPolicy = AAUDIO_UNSPECIFIED;

static AudioStream *convertAAudioStreamToAudioStream(AAudioStream* stream)
{
    return (AudioStream*) stream;
}

static AudioStreamBuilder *convertAAudioBuilderToStreamBuilder(AAudioStreamBuilder* builder)
{
    return (AudioStreamBuilder*) builder;
}

AAUDIO_API aaudio_result_t AAudio_createStreamBuilder(AAudioStreamBuilder** builder)
{
    AudioStreamBuilder *audioStreamBuilder =  new(std::nothrow) AudioStreamBuilder();
    if (audioStreamBuilder == nullptr) {
        return AAUDIO_ERROR_NO_MEMORY;
    }
    *builder = (AAudioStreamBuilder*) audioStreamBuilder;
    return AAUDIO_OK;
}

AAUDIO_API void AAudioStreamBuilder_setPerformanceMode(AAudioStreamBuilder* builder,
                                                       aaudio_performance_mode_t mode)
{
    AudioStreamBuilder *streamBuilder = convertAAudioBuilderToStreamBuilder(builder);
    streamBuilder->setPerformanceMode(mode);
}

AAUDIO_API void AAudioStreamBuilder_setDeviceId(AAudioStreamBuilder* builder,
                                                int32_t deviceId)
{
    AudioStreamBuilder *streamBuilder = convertAAudioBuilderToStreamBuilder(builder);
    streamBuilder->setDeviceId(deviceId);
}

AAUDIO_API void AAudioStreamBuilder_setSampleRate(AAudioStreamBuilder* builder,
                                              int32_t sampleRate)
{
    AudioStreamBuilder *streamBuilder = convertAAudioBuilderToStreamBuilder(builder);
    streamBuilder->setSampleRate(sampleRate);
}

AAUDIO_API void AAudioStreamBuilder_setChannelCount(AAudioStreamBuilder* builder,
                                                    int32_t channelCount)
{
    AudioStreamBuilder *streamBuilder = convertAAudioBuilderToStreamBuilder(builder);
    streamBuilder->setSamplesPerFrame(channelCount);
}

AAUDIO_API void AAudioStreamBuilder_setSamplesPerFrame(AAudioStreamBuilder* builder,
                                                       int32_t channelCount)
{
    AAudioStreamBuilder_setChannelCount(builder, channelCount);
}

AAUDIO_API void AAudioStreamBuilder_setDirection(AAudioStreamBuilder* builder,
                                             aaudio_direction_t direction)
{
    AudioStreamBuilder *streamBuilder = convertAAudioBuilderToStreamBuilder(builder);
    streamBuilder->setDirection(direction);
}

AAUDIO_API void AAudioStreamBuilder_setFormat(AAudioStreamBuilder* builder,
                                                   aaudio_format_t format)
{
    AudioStreamBuilder *streamBuilder = convertAAudioBuilderToStreamBuilder(builder);
    streamBuilder->setFormat(format);
}

AAUDIO_API void AAudioStreamBuilder_setSharingMode(AAudioStreamBuilder* builder,
                                                        aaudio_sharing_mode_t sharingMode)
{
    AudioStreamBuilder *streamBuilder = convertAAudioBuilderToStreamBuilder(builder);
    streamBuilder->setSharingMode(sharingMode);
}

AAUDIO_API void AAudioStreamBuilder_setBufferCapacityInFrames(AAudioStreamBuilder* builder,
                                                        int32_t frames)
{
    AudioStreamBuilder *streamBuilder = convertAAudioBuilderToStreamBuilder(builder);
    streamBuilder->setBufferCapacity(frames);
}

AAUDIO_API void AAudioStreamBuilder_setDataCallback(AAudioStreamBuilder* builder,
                                                    AAudioStream_dataCallback callback,
                                                    void *userData)
{
    AudioStreamBuilder *streamBuilder = convertAAudioBuilderToStreamBuilder(builder);
    streamBuilder->setDataCallbackProc(callback);
    streamBuilder->setDataCallbackUserData(userData);
}

AAUDIO_API void AAudioStreamBuilder_setErrorCallback(AAudioStreamBuilder* builder,
                                                 AAudioStream_errorCallback callback,
                                                 void *userData)
{
    AudioStreamBuilder *streamBuilder = convertAAudioBuilderToStreamBuilder(builder);
    streamBuilder->setErrorCallbackProc(callback);
    streamBuilder->setErrorCallbackUserData(userData);
}

AAUDIO_API void AAudioStreamBuilder_setFramesPerDataCallback(AAudioStreamBuilder* builder,
                                                int32_t frames)
{
    AudioStreamBuilder *streamBuilder = convertAAudioBuilderToStreamBuilder(builder);
    streamBuilder->setFramesPerDataCallback(frames);
}

AAUDIO_API aaudio_result_t  AAudioStreamBuilder_openStream(AAudioStreamBuilder* builder,
                                                     AAudioStream** streamPtr)
{
    AudioStream *audioStream = nullptr;
    // Please leave these logs because they are very helpful when debugging.
    ALOGD("AAudioStreamBuilder_openStream() called ----------------------------------------");
    AudioStreamBuilder *streamBuilder = COMMON_GET_FROM_BUILDER_OR_RETURN(streamPtr);
    aaudio_result_t result = streamBuilder->build(&audioStream);
    ALOGD("AAudioStreamBuilder_openStream() returns %d = %s for (%p) ----------------",
          result, AAudio_convertResultToText(result), audioStream);
    if (result == AAUDIO_OK) {
        audioStream->registerPlayerBase();
        *streamPtr = (AAudioStream*) audioStream;
    } else {
        *streamPtr = nullptr;
    }
    return result;
}

AAUDIO_API aaudio_result_t  AAudioStreamBuilder_delete(AAudioStreamBuilder* builder)
{
    AudioStreamBuilder *streamBuilder = convertAAudioBuilderToStreamBuilder(builder);
    if (streamBuilder != nullptr) {
        delete streamBuilder;
        return AAUDIO_OK;
    }
    return AAUDIO_ERROR_NULL;
}

AAUDIO_API aaudio_result_t  AAudioStream_close(AAudioStream* stream)
{
    AudioStream *audioStream = convertAAudioStreamToAudioStream(stream);
    ALOGD("AAudioStream_close(%p)", stream);
    if (audioStream != nullptr) {
        audioStream->close();
        audioStream->unregisterPlayerBase();
        delete audioStream;
        return AAUDIO_OK;
    }
    return AAUDIO_ERROR_NULL;
}

AAUDIO_API aaudio_result_t  AAudioStream_requestStart(AAudioStream* stream)
{
    AudioStream *audioStream = convertAAudioStreamToAudioStream(stream);
    ALOGD("AAudioStream_requestStart(%p) called --------------", stream);
    aaudio_result_t result = audioStream->systemStart();
    ALOGD("AAudioStream_requestStart(%p) returned %d ---------", stream, result);
    return result;
}

AAUDIO_API aaudio_result_t  AAudioStream_requestPause(AAudioStream* stream)
{
    AudioStream *audioStream = convertAAudioStreamToAudioStream(stream);
    ALOGD("AAudioStream_requestPause(%p)", stream);
    return audioStream->systemPause();
}

AAUDIO_API aaudio_result_t  AAudioStream_requestFlush(AAudioStream* stream)
{
    AudioStream *audioStream = convertAAudioStreamToAudioStream(stream);
    ALOGD("AAudioStream_requestFlush(%p)", stream);
    return audioStream->requestFlush();
}

AAUDIO_API aaudio_result_t  AAudioStream_requestStop(AAudioStream* stream)
{
    AudioStream *audioStream = convertAAudioStreamToAudioStream(stream);
    ALOGD("AAudioStream_requestStop(%p)", stream);
    return audioStream->systemStop();
}

AAUDIO_API aaudio_result_t AAudioStream_waitForStateChange(AAudioStream* stream,
                                            aaudio_stream_state_t inputState,
                                            aaudio_stream_state_t *nextState,
                                            int64_t timeoutNanoseconds)
{

    AudioStream *audioStream = convertAAudioStreamToAudioStream(stream);
    return audioStream->waitForStateChange(inputState, nextState, timeoutNanoseconds);
}

// ============================================================
// Stream - non-blocking I/O
// ============================================================

AAUDIO_API aaudio_result_t AAudioStream_read(AAudioStream* stream,
                               void *buffer,
                               int32_t numFrames,
                               int64_t timeoutNanoseconds)
{
    AudioStream *audioStream = convertAAudioStreamToAudioStream(stream);
    if (buffer == nullptr) {
        return AAUDIO_ERROR_NULL;
    }
    if (numFrames < 0) {
        return AAUDIO_ERROR_ILLEGAL_ARGUMENT;
    } else if (numFrames == 0) {
        return 0;
    }

    aaudio_result_t result = audioStream->read(buffer, numFrames, timeoutNanoseconds);

    return result;
}

AAUDIO_API aaudio_result_t AAudioStream_write(AAudioStream* stream,
                               const void *buffer,
                               int32_t numFrames,
                               int64_t timeoutNanoseconds)
{
    AudioStream *audioStream = convertAAudioStreamToAudioStream(stream);
    if (buffer == nullptr) {
        return AAUDIO_ERROR_NULL;
    }

    // Don't allow writes when playing with a callback.
    if (audioStream->getDataCallbackProc() != nullptr && audioStream->isActive()) {
        ALOGE("Cannot write to a callback stream when running.");
        return AAUDIO_ERROR_INVALID_STATE;
    }

    if (numFrames < 0) {
        return AAUDIO_ERROR_ILLEGAL_ARGUMENT;
    } else if (numFrames == 0) {
        return 0;
    }

    aaudio_result_t result = audioStream->write(buffer, numFrames, timeoutNanoseconds);

    return result;
}

// ============================================================
// Stream - queries
// ============================================================

AAUDIO_API int32_t AAudioStream_getSampleRate(AAudioStream* stream)
{
    AudioStream *audioStream = convertAAudioStreamToAudioStream(stream);
    return audioStream->getSampleRate();
}

AAUDIO_API int32_t AAudioStream_getChannelCount(AAudioStream* stream)
{
    AudioStream *audioStream = convertAAudioStreamToAudioStream(stream);
    return audioStream->getSamplesPerFrame();
}

AAUDIO_API int32_t AAudioStream_getSamplesPerFrame(AAudioStream* stream)
{
    return AAudioStream_getChannelCount(stream);
}

AAUDIO_API aaudio_stream_state_t AAudioStream_getState(AAudioStream* stream)
{
    AudioStream *audioStream = convertAAudioStreamToAudioStream(stream);
    return audioStream->getState();
}

AAUDIO_API aaudio_format_t AAudioStream_getFormat(AAudioStream* stream)
{
    AudioStream *audioStream = convertAAudioStreamToAudioStream(stream);
    return audioStream->getFormat();
}

AAUDIO_API aaudio_result_t AAudioStream_setBufferSizeInFrames(AAudioStream* stream,
                                                int32_t requestedFrames)
{
    AudioStream *audioStream = convertAAudioStreamToAudioStream(stream);
    return audioStream->setBufferSize(requestedFrames);
}

AAUDIO_API int32_t AAudioStream_getBufferSizeInFrames(AAudioStream* stream)
{
    AudioStream *audioStream = convertAAudioStreamToAudioStream(stream);
    return audioStream->getBufferSize();
}

AAUDIO_API aaudio_direction_t AAudioStream_getDirection(AAudioStream* stream)
{
    AudioStream *audioStream = convertAAudioStreamToAudioStream(stream);
    return audioStream->getDirection();
}

AAUDIO_API int32_t AAudioStream_getFramesPerBurst(AAudioStream* stream)
{
    AudioStream *audioStream = convertAAudioStreamToAudioStream(stream);
    return audioStream->getFramesPerBurst();
}

AAUDIO_API int32_t AAudioStream_getFramesPerDataCallback(AAudioStream* stream)
{
    AudioStream *audioStream = convertAAudioStreamToAudioStream(stream);
    return audioStream->getFramesPerDataCallback();
}

AAUDIO_API int32_t AAudioStream_getBufferCapacityInFrames(AAudioStream* stream)
{
    AudioStream *audioStream = convertAAudioStreamToAudioStream(stream);
    return audioStream->getBufferCapacity();
}

AAUDIO_API int32_t AAudioStream_getXRunCount(AAudioStream* stream)
{
    AudioStream *audioStream = convertAAudioStreamToAudioStream(stream);
    return audioStream->getXRunCount();
}

AAUDIO_API aaudio_performance_mode_t AAudioStream_getPerformanceMode(AAudioStream* stream)
{
    AudioStream *audioStream = convertAAudioStreamToAudioStream(stream);
    return audioStream->getPerformanceMode();
}

AAUDIO_API int32_t AAudioStream_getDeviceId(AAudioStream* stream)
{
    AudioStream *audioStream = convertAAudioStreamToAudioStream(stream);
    return audioStream->getDeviceId();
}

AAUDIO_API aaudio_sharing_mode_t AAudioStream_getSharingMode(AAudioStream* stream)
{
    AudioStream *audioStream = convertAAudioStreamToAudioStream(stream);
    return audioStream->getSharingMode();
}

AAUDIO_API int64_t AAudioStream_getFramesWritten(AAudioStream* stream)
{
    AudioStream *audioStream = convertAAudioStreamToAudioStream(stream);
    return audioStream->getFramesWritten();
}

AAUDIO_API int64_t AAudioStream_getFramesRead(AAudioStream* stream)
{
    AudioStream *audioStream = convertAAudioStreamToAudioStream(stream);
    return audioStream->getFramesRead();
}

AAUDIO_API aaudio_result_t AAudioStream_getTimestamp(AAudioStream* stream,
                                      clockid_t clockid,
                                      int64_t *framePosition,
                                      int64_t *timeNanoseconds)
{
    AudioStream *audioStream = convertAAudioStreamToAudioStream(stream);
    if (framePosition == nullptr) {
        return AAUDIO_ERROR_NULL;
    } else if (timeNanoseconds == nullptr) {
        return AAUDIO_ERROR_NULL;
    } else if (clockid != CLOCK_MONOTONIC && clockid != CLOCK_BOOTTIME) {
        return AAUDIO_ERROR_ILLEGAL_ARGUMENT;
    }

    return audioStream->getTimestamp(clockid, framePosition, timeNanoseconds);
}

AAUDIO_API aaudio_policy_t AAudio_getMMapPolicy() {
    return s_MMapPolicy;
}

AAUDIO_API aaudio_result_t AAudio_setMMapPolicy(aaudio_policy_t policy) {
    aaudio_result_t result = AAUDIO_OK;
    switch(policy) {
        case AAUDIO_UNSPECIFIED:
        case AAUDIO_POLICY_NEVER:
        case AAUDIO_POLICY_AUTO:
        case AAUDIO_POLICY_ALWAYS:
            s_MMapPolicy = policy;
            break;
        default:
            result = AAUDIO_ERROR_ILLEGAL_ARGUMENT;
            break;
    }
    return result;
}

AAUDIO_API bool AAudioStream_isMMapUsed(AAudioStream* stream)
{
    AudioStream *audioStream = convertAAudioStreamToAudioStream(stream);
    return audioStream->isMMap();
}
