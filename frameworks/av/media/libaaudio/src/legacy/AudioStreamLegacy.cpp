/*
 * Copyright 2017 The Android Open Source Project
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

#define LOG_TAG "AudioStreamLegacy"
//#define LOG_NDEBUG 0
#include <utils/Log.h>

#include <stdint.h>
#include <utils/String16.h>
#include <media/AudioTrack.h>
#include <media/AudioTimestamp.h>
#include <aaudio/AAudio.h>

#include "core/AudioStream.h"
#include "legacy/AudioStreamLegacy.h"

using namespace android;
using namespace aaudio;

AudioStreamLegacy::AudioStreamLegacy()
        : AudioStream()
        , mDeviceCallback(new StreamDeviceCallback(this)) {
}

AudioStreamLegacy::~AudioStreamLegacy() {
}

// Called from AudioTrack.cpp or AudioRecord.cpp
static void AudioStreamLegacy_callback(int event, void* userData, void *info) {
    AudioStreamLegacy *streamLegacy = (AudioStreamLegacy *) userData;
    streamLegacy->processCallback(event, info);
}

aaudio_legacy_callback_t AudioStreamLegacy::getLegacyCallback() {
    return AudioStreamLegacy_callback;
}

int32_t AudioStreamLegacy::callDataCallbackFrames(uint8_t *buffer, int32_t numFrames) {
    if (getDirection() == AAUDIO_DIRECTION_INPUT) {
        // Increment before because we already got the data from the device.
        incrementFramesRead(numFrames);
    }

    // Call using the AAudio callback interface.
    AAudioStream_dataCallback appCallback = getDataCallbackProc();
    aaudio_data_callback_result_t callbackResult = (*appCallback)(
            (AAudioStream *) this,
            getDataCallbackUserData(),
            buffer,
            numFrames);

    if (callbackResult == AAUDIO_CALLBACK_RESULT_CONTINUE
            && getDirection() == AAUDIO_DIRECTION_OUTPUT) {
        // Increment after because we are going to write the data to the device.
        incrementFramesWritten(numFrames);
    }
    return callbackResult;
}

// Implement FixedBlockProcessor
int32_t AudioStreamLegacy::onProcessFixedBlock(uint8_t *buffer, int32_t numBytes) {
    int32_t numFrames = numBytes / getBytesPerFrame();
    return callDataCallbackFrames(buffer, numFrames);
}

void AudioStreamLegacy::processCallbackCommon(aaudio_callback_operation_t opcode, void *info) {
    aaudio_data_callback_result_t callbackResult;

    switch (opcode) {
        case AAUDIO_CALLBACK_OPERATION_PROCESS_DATA: {
            checkForDisconnectRequest();

            // Note that this code assumes an AudioTrack::Buffer is the same as
            // AudioRecord::Buffer
            // TODO define our own AudioBuffer and pass it from the subclasses.
            AudioTrack::Buffer *audioBuffer = static_cast<AudioTrack::Buffer *>(info);
            if (getState() == AAUDIO_STREAM_STATE_DISCONNECTED || !mCallbackEnabled.load()) {
                audioBuffer->size = 0; // silence the buffer
            } else {
                if (audioBuffer->frameCount == 0) {
                    return;
                }

                // If the caller specified an exact size then use a block size adapter.
                if (mBlockAdapter != nullptr) {
                    int32_t byteCount = audioBuffer->frameCount * getBytesPerFrame();
                    callbackResult = mBlockAdapter->processVariableBlock(
                            (uint8_t *) audioBuffer->raw, byteCount);
                } else {
                    // Call using the AAudio callback interface.
                    callbackResult = callDataCallbackFrames((uint8_t *)audioBuffer->raw,
                                                            audioBuffer->frameCount);
                }
                if (callbackResult == AAUDIO_CALLBACK_RESULT_CONTINUE) {
                    audioBuffer->size = audioBuffer->frameCount * getBytesPerFrame();
                } else {
                    audioBuffer->size = 0;
                }

                if (updateStateMachine() != AAUDIO_OK) {
                    forceDisconnect();
                    mCallbackEnabled.store(false);
                }
            }
        }
            break;

        // Stream got rerouted so we disconnect.
        case AAUDIO_CALLBACK_OPERATION_DISCONNECTED:
            ALOGD("processCallbackCommon() stream disconnected");
            forceDisconnect();
            mCallbackEnabled.store(false);
            break;

        default:
            break;
    }
}



void AudioStreamLegacy::checkForDisconnectRequest() {
    if (mRequestDisconnect.isRequested()) {
        ALOGD("checkForDisconnectRequest() mRequestDisconnect acknowledged");
        forceDisconnect();
        mRequestDisconnect.acknowledge();
        mCallbackEnabled.store(false);
    }
}

void AudioStreamLegacy::forceDisconnect() {
    if (getState() != AAUDIO_STREAM_STATE_DISCONNECTED) {
        setState(AAUDIO_STREAM_STATE_DISCONNECTED);
        if (getErrorCallbackProc() != nullptr) {
            (*getErrorCallbackProc())(
                    (AAudioStream *) this,
                    getErrorCallbackUserData(),
                    AAUDIO_ERROR_DISCONNECTED
            );
        }
    }
}

aaudio_result_t AudioStreamLegacy::getBestTimestamp(clockid_t clockId,
                                                   int64_t *framePosition,
                                                   int64_t *timeNanoseconds,
                                                   ExtendedTimestamp *extendedTimestamp) {
    int timebase;
    switch (clockId) {
        case CLOCK_BOOTTIME:
            timebase = ExtendedTimestamp::TIMEBASE_BOOTTIME;
            break;
        case CLOCK_MONOTONIC:
            timebase = ExtendedTimestamp::TIMEBASE_MONOTONIC;
            break;
        default:
            ALOGE("getTimestamp() - Unrecognized clock type %d", (int) clockId);
            return AAUDIO_ERROR_ILLEGAL_ARGUMENT;
            break;
    }
    ExtendedTimestamp::Location location = ExtendedTimestamp::Location::LOCATION_INVALID;
    int64_t localPosition;
    status_t status = extendedTimestamp->getBestTimestamp(&localPosition, timeNanoseconds,
                                                          timebase, &location);
    // use MonotonicCounter to prevent retrograde motion.
    mTimestampPosition.update32((int32_t)localPosition);
    *framePosition = mTimestampPosition.get();

//    ALOGD("getBestTimestamp() fposition: server = %6lld, kernel = %6lld, location = %d",
//          (long long) extendedTimestamp->mPosition[ExtendedTimestamp::Location::LOCATION_SERVER],
//          (long long) extendedTimestamp->mPosition[ExtendedTimestamp::Location::LOCATION_KERNEL],
//          (int)location);
    if (status == WOULD_BLOCK) {
        return AAUDIO_ERROR_INVALID_STATE;
    } else {
        return AAudioConvert_androidToAAudioResult(status);
    }
}

void AudioStreamLegacy::onAudioDeviceUpdate(audio_port_handle_t deviceId)
{
    ALOGD("onAudioDeviceUpdate() deviceId %d", (int)deviceId);
    if (getDeviceId() != AAUDIO_UNSPECIFIED && getDeviceId() != deviceId &&
            getState() != AAUDIO_STREAM_STATE_DISCONNECTED) {
        // Note that isDataCallbackActive() is affected by state so call it before DISCONNECTING.
        // If we have a data callback and the stream is active, then ask the data callback
        // to DISCONNECT and call the error callback.
        if (isDataCallbackActive()) {
            ALOGD("onAudioDeviceUpdate() request DISCONNECT in data callback due to device change");
            // If the stream is stopped before the data callback has a chance to handle the
            // request then the requestStop() and requestPause() methods will handle it after
            // the callback has stopped.
            mRequestDisconnect.request();
        } else {
            ALOGD("onAudioDeviceUpdate() DISCONNECT the stream now");
            forceDisconnect();
        }
    }
    setDeviceId(deviceId);
}
