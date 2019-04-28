/*
 * Copyright (C) 2017 The Android Open Source Project
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

#define LOG_TAG (mInService ? "AAudioService" : "AAudio")
//#define LOG_NDEBUG 0
#include <utils/Log.h>

#include <algorithm>
#include <aaudio/AAudio.h>

#include "client/AudioStreamInternalCapture.h"
#include "utility/AudioClock.h"

#define ATRACE_TAG ATRACE_TAG_AUDIO
#include <utils/Trace.h>

using android::WrappingBuffer;

using namespace aaudio;

AudioStreamInternalCapture::AudioStreamInternalCapture(AAudioServiceInterface  &serviceInterface,
                                                 bool inService)
    : AudioStreamInternal(serviceInterface, inService) {

}

AudioStreamInternalCapture::~AudioStreamInternalCapture() {}

void AudioStreamInternalCapture::advanceClientToMatchServerPosition() {
    int64_t readCounter = mAudioEndpoint.getDataReadCounter();
    int64_t writeCounter = mAudioEndpoint.getDataWriteCounter();

    // Bump offset so caller does not see the retrograde motion in getFramesRead().
    int64_t offset = readCounter - writeCounter;
    mFramesOffsetFromService += offset;
    ALOGD("advanceClientToMatchServerPosition() readN = %lld, writeN = %lld, offset = %lld",
          (long long)readCounter, (long long)writeCounter, (long long)mFramesOffsetFromService);

    // Force readCounter to match writeCounter.
    // This is because we cannot change the write counter in the hardware.
    mAudioEndpoint.setDataReadCounter(writeCounter);
}

// Write the data, block if needed and timeoutMillis > 0
aaudio_result_t AudioStreamInternalCapture::read(void *buffer, int32_t numFrames,
                                               int64_t timeoutNanoseconds)
{
    return processData(buffer, numFrames, timeoutNanoseconds);
}

// Read as much data as we can without blocking.
aaudio_result_t AudioStreamInternalCapture::processDataNow(void *buffer, int32_t numFrames,
                                                  int64_t currentNanoTime, int64_t *wakeTimePtr) {
    aaudio_result_t result = processCommands();
    if (result != AAUDIO_OK) {
        return result;
    }

    const char *traceName = "aaRdNow";
    ATRACE_BEGIN(traceName);

    if (mClockModel.isStarting()) {
        // Still haven't got any timestamps from server.
        // Keep waiting until we get some valid timestamps then start writing to the
        // current buffer position.
        ALOGD("processDataNow() wait for valid timestamps");
        // Sleep very briefly and hope we get a timestamp soon.
        *wakeTimePtr = currentNanoTime + (2000 * AAUDIO_NANOS_PER_MICROSECOND);
        ATRACE_END();
        return 0;
    }
    // If we have gotten this far then we have at least one timestamp from server.

    if (mAudioEndpoint.isFreeRunning()) {
        //ALOGD("AudioStreamInternalCapture::processDataNow() - update remote counter");
        // Update data queue based on the timing model.
        int64_t estimatedRemoteCounter = mClockModel.convertTimeToPosition(currentNanoTime);
        // TODO refactor, maybe use setRemoteCounter()
        mAudioEndpoint.setDataWriteCounter(estimatedRemoteCounter);
    }

    // This code assumes that we have already received valid timestamps.
    if (mNeedCatchUp.isRequested()) {
        // Catch an MMAP pointer that is already advancing.
        // This will avoid initial underruns caused by a slow cold start.
        advanceClientToMatchServerPosition();
        mNeedCatchUp.acknowledge();
    }

    // If the write index passed the read index then consider it an overrun.
    if (mAudioEndpoint.getEmptyFramesAvailable() < 0) {
        mXRunCount++;
        if (ATRACE_ENABLED()) {
            ATRACE_INT("aaOverRuns", mXRunCount);
        }
    }

    // Read some data from the buffer.
    //ALOGD("AudioStreamInternalCapture::processDataNow() - readNowWithConversion(%d)", numFrames);
    int32_t framesProcessed = readNowWithConversion(buffer, numFrames);
    //ALOGD("AudioStreamInternalCapture::processDataNow() - tried to read %d frames, read %d",
    //    numFrames, framesProcessed);
    if (ATRACE_ENABLED()) {
        ATRACE_INT("aaRead", framesProcessed);
    }

    // Calculate an ideal time to wake up.
    if (wakeTimePtr != nullptr && framesProcessed >= 0) {
        // By default wake up a few milliseconds from now.  // TODO review
        int64_t wakeTime = currentNanoTime + (1 * AAUDIO_NANOS_PER_MILLISECOND);
        aaudio_stream_state_t state = getState();
        //ALOGD("AudioStreamInternalCapture::processDataNow() - wakeTime based on %s",
        //      AAudio_convertStreamStateToText(state));
        switch (state) {
            case AAUDIO_STREAM_STATE_OPEN:
            case AAUDIO_STREAM_STATE_STARTING:
                break;
            case AAUDIO_STREAM_STATE_STARTED:
            {
                // When do we expect the next write burst to occur?

                // Calculate frame position based off of the readCounter because
                // the writeCounter might have just advanced in the background,
                // causing us to sleep until a later burst.
                int64_t nextPosition = mAudioEndpoint.getDataReadCounter() + mFramesPerBurst;
                wakeTime = mClockModel.convertPositionToTime(nextPosition);
            }
                break;
            default:
                break;
        }
        *wakeTimePtr = wakeTime;

    }

    ATRACE_END();
    return framesProcessed;
}

aaudio_result_t AudioStreamInternalCapture::readNowWithConversion(void *buffer,
                                                                int32_t numFrames) {
    // ALOGD("AudioStreamInternalCapture::readNowWithConversion(%p, %d)",
    //              buffer, numFrames);
    WrappingBuffer wrappingBuffer;
    uint8_t *destination = (uint8_t *) buffer;
    int32_t framesLeft = numFrames;

    mAudioEndpoint.getFullFramesAvailable(&wrappingBuffer);

    // Read data in one or two parts.
    for (int partIndex = 0; framesLeft > 0 && partIndex < WrappingBuffer::SIZE; partIndex++) {
        int32_t framesToProcess = framesLeft;
        int32_t framesAvailable = wrappingBuffer.numFrames[partIndex];
        if (framesAvailable <= 0) break;

        if (framesToProcess > framesAvailable) {
            framesToProcess = framesAvailable;
        }

        int32_t numBytes = getBytesPerFrame() * framesToProcess;
        int32_t numSamples = framesToProcess * getSamplesPerFrame();

        // TODO factor this out into a utility function
        if (mDeviceFormat == getFormat()) {
            memcpy(destination, wrappingBuffer.data[partIndex], numBytes);
        } else if (mDeviceFormat == AAUDIO_FORMAT_PCM_I16
                   && getFormat() == AAUDIO_FORMAT_PCM_FLOAT) {
            AAudioConvert_pcm16ToFloat(
                    (const int16_t *) wrappingBuffer.data[partIndex],
                    (float *) destination,
                    numSamples,
                    1.0f);
        } else if (mDeviceFormat == AAUDIO_FORMAT_PCM_FLOAT
                   && getFormat() == AAUDIO_FORMAT_PCM_I16) {
            AAudioConvert_floatToPcm16(
                    (const float *) wrappingBuffer.data[partIndex],
                    (int16_t *) destination,
                    numSamples,
                    1.0f);
        } else {
            ALOGE("Format conversion not supported!");
            return AAUDIO_ERROR_INVALID_FORMAT;
        }
        destination += numBytes;
        framesLeft -= framesToProcess;
    }

    int32_t framesProcessed = numFrames - framesLeft;
    mAudioEndpoint.advanceReadIndex(framesProcessed);

    //ALOGD("AudioStreamInternalCapture::readNowWithConversion() returns %d", framesProcessed);
    return framesProcessed;
}

int64_t AudioStreamInternalCapture::getFramesWritten() {
    int64_t framesWrittenHardware;
    if (isActive()) {
        framesWrittenHardware = mClockModel.convertTimeToPosition(AudioClock::getNanoseconds());
    } else {
        framesWrittenHardware = mAudioEndpoint.getDataWriteCounter();
    }
    // Prevent retrograde motion.
    mLastFramesWritten = std::max(mLastFramesWritten,
                                  framesWrittenHardware + mFramesOffsetFromService);
    //ALOGD("AudioStreamInternalCapture::getFramesWritten() returns %lld",
    //      (long long)mLastFramesWritten);
    return mLastFramesWritten;
}

int64_t AudioStreamInternalCapture::getFramesRead() {
    int64_t frames = mAudioEndpoint.getDataReadCounter() + mFramesOffsetFromService;
    //ALOGD("AudioStreamInternalCapture::getFramesRead() returns %lld", (long long)frames);
    return frames;
}

// Read data from the stream and pass it to the callback for processing.
void *AudioStreamInternalCapture::callbackLoop() {
    aaudio_result_t result = AAUDIO_OK;
    aaudio_data_callback_result_t callbackResult = AAUDIO_CALLBACK_RESULT_CONTINUE;
    AAudioStream_dataCallback appCallback = getDataCallbackProc();
    if (appCallback == nullptr) return NULL;

    // result might be a frame count
    while (mCallbackEnabled.load() && isActive() && (result >= 0)) {

        // Read audio data from stream.
        int64_t timeoutNanos = calculateReasonableTimeout(mCallbackFrames);

        // This is a BLOCKING READ!
        result = read(mCallbackBuffer, mCallbackFrames, timeoutNanos);
        if ((result != mCallbackFrames)) {
            ALOGE("AudioStreamInternalCapture(): callbackLoop: read() returned %d", result);
            if (result >= 0) {
                // Only read some of the frames requested. Must have timed out.
                result = AAUDIO_ERROR_TIMEOUT;
            }
            AAudioStream_errorCallback errorCallback = getErrorCallbackProc();
            if (errorCallback != nullptr) {
                (*errorCallback)(
                        (AAudioStream *) this,
                        getErrorCallbackUserData(),
                        result);
            }
            break;
        }

        // Call application using the AAudio callback interface.
        callbackResult = (*appCallback)(
                (AAudioStream *) this,
                getDataCallbackUserData(),
                mCallbackBuffer,
                mCallbackFrames);

        if (callbackResult == AAUDIO_CALLBACK_RESULT_STOP) {
            ALOGD("AudioStreamInternalCapture(): callback returned AAUDIO_CALLBACK_RESULT_STOP");
            break;
        }
    }

    ALOGD("AudioStreamInternalCapture(): callbackLoop() exiting, result = %d, isActive() = %d",
          result, (int) isActive());
    return NULL;
}
