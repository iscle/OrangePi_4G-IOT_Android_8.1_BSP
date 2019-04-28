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

// Play sine waves using an AAudio callback.

#ifndef AAUDIO_SIMPLE_PLAYER_H
#define AAUDIO_SIMPLE_PLAYER_H

#include <unistd.h>
#include <sched.h>

#include <aaudio/AAudio.h>
#include <atomic>
#include "AAudioArgsParser.h"
#include "SineGenerator.h"

//#define SHARING_MODE  AAUDIO_SHARING_MODE_EXCLUSIVE
#define SHARING_MODE  AAUDIO_SHARING_MODE_SHARED
#define PERFORMANCE_MODE AAUDIO_PERFORMANCE_MODE_NONE

// Arbitrary period for glitches, once per second at 48000 Hz.
#define FORCED_UNDERRUN_PERIOD_FRAMES    48000
// How long to sleep in a callback to cause an intentional glitch. For testing.
#define FORCED_UNDERRUN_SLEEP_MICROS     (10 * 1000)

#define MAX_TIMESTAMPS   16

typedef struct Timestamp {
    int64_t position;
    int64_t nanoseconds;
} Timestamp;

/**
 * Simple wrapper for AAudio that opens an output stream either in callback or blocking write mode.
 */
class AAudioSimplePlayer {
public:
    AAudioSimplePlayer() {}
    ~AAudioSimplePlayer() {
        close();
    };

    /**
     * Call this before calling open().
     * @param requestedSharingMode
     */
    void setSharingMode(aaudio_sharing_mode_t requestedSharingMode) {
        mRequestedSharingMode = requestedSharingMode;
    }

    /**
     * Call this before calling open().
     * @param requestedPerformanceMode
     */
    void setPerformanceMode(aaudio_performance_mode_t requestedPerformanceMode) {
        mRequestedPerformanceMode = requestedPerformanceMode;
    }

    // TODO Extract a common base class for record and playback.
    /**
     * Also known as "sample rate"
     * Only call this after open() has been called.
     */
    int32_t getFramesPerSecond() const {
        return getSampleRate(); // alias
    }

    /**
     * Only call this after open() has been called.
     */
    int32_t getSampleRate() const {
        if (mStream == nullptr) {
            return AAUDIO_ERROR_INVALID_STATE;
        }
        return AAudioStream_getSampleRate(mStream);
    }

    /**
     * Only call this after open() has been called.
     */
    int32_t getChannelCount() {
        if (mStream == nullptr) {
            return AAUDIO_ERROR_INVALID_STATE;
        }
        return AAudioStream_getChannelCount(mStream);
    }

    /**
     * Open a stream
     */
    aaudio_result_t open(const AAudioParameters &parameters,
                         AAudioStream_dataCallback dataCallback = nullptr,
                         AAudioStream_errorCallback errorCallback = nullptr,
                         void *userContext = nullptr) {
        aaudio_result_t result = AAUDIO_OK;

        // Use an AAudioStreamBuilder to contain requested parameters.
        AAudioStreamBuilder *builder = nullptr;
        result = AAudio_createStreamBuilder(&builder);
        if (result != AAUDIO_OK) return result;

        parameters.applyParameters(builder); // apply args

        AAudioStreamBuilder_setDirection(builder, AAUDIO_DIRECTION_OUTPUT);

        if (dataCallback != nullptr) {
            AAudioStreamBuilder_setDataCallback(builder, dataCallback, userContext);
        }
        if (errorCallback != nullptr) {
            AAudioStreamBuilder_setErrorCallback(builder, errorCallback, userContext);
        }
        //AAudioStreamBuilder_setFramesPerDataCallback(builder, CALLBACK_SIZE_FRAMES);
        //AAudioStreamBuilder_setBufferCapacityInFrames(builder, 48 * 8);

        // Open an AAudioStream using the Builder.
        result = AAudioStreamBuilder_openStream(builder, &mStream);

        if (result == AAUDIO_OK) {
            int32_t sizeInBursts = parameters.getNumberOfBursts();
            if (sizeInBursts > 0) {
                int32_t framesPerBurst = AAudioStream_getFramesPerBurst(mStream);
                AAudioStream_setBufferSizeInFrames(mStream, sizeInBursts * framesPerBurst);
            }
        }

        AAudioStreamBuilder_delete(builder);
        return result;
    }

    aaudio_result_t open(int channelCount, int sampSampleRate, aaudio_format_t format,
                         AAudioStream_dataCallback dataProc,
                         AAudioStream_errorCallback errorProc,
                         void *userContext) {
        aaudio_result_t result = AAUDIO_OK;

        // Use an AAudioStreamBuilder to contain requested parameters.
        AAudioStreamBuilder *builder = nullptr;
        result = AAudio_createStreamBuilder(&builder);
        if (result != AAUDIO_OK) return result;

        AAudioStreamBuilder_setDirection(builder, AAUDIO_DIRECTION_OUTPUT);
        AAudioStreamBuilder_setPerformanceMode(builder, mRequestedPerformanceMode);
        AAudioStreamBuilder_setSharingMode(builder, mRequestedSharingMode);

        AAudioStreamBuilder_setChannelCount(builder, channelCount);
        AAudioStreamBuilder_setSampleRate(builder, sampSampleRate);
        AAudioStreamBuilder_setFormat(builder, format);

        if (dataProc != nullptr) {
            AAudioStreamBuilder_setDataCallback(builder, dataProc, userContext);
        }
        if (errorProc != nullptr) {
            AAudioStreamBuilder_setErrorCallback(builder, errorProc, userContext);
        }
        //AAudioStreamBuilder_setFramesPerDataCallback(builder, CALLBACK_SIZE_FRAMES);
        //AAudioStreamBuilder_setBufferCapacityInFrames(builder, 48 * 8);

        // Open an AAudioStream using the Builder.
        result = AAudioStreamBuilder_openStream(builder, &mStream);

        AAudioStreamBuilder_delete(builder);
        return result;
    }

    aaudio_result_t close() {
        if (mStream != nullptr) {
            printf("call AAudioStream_close(%p)\n", mStream);  fflush(stdout);
            AAudioStream_close(mStream);
            mStream = nullptr;
        }
        return AAUDIO_OK;
    }

    // Write zero data to fill up the buffer and prevent underruns.
    aaudio_result_t prime() {
        int32_t samplesPerFrame = AAudioStream_getChannelCount(mStream);
        const int numFrames = 32;
        float zeros[numFrames * samplesPerFrame];
        memset(zeros, 0, sizeof(zeros));
        aaudio_result_t result = numFrames;
        while (result == numFrames) {
            result = AAudioStream_write(mStream, zeros, numFrames, 0);
        }
        return result;
    }

    // Start the stream. AAudio will start calling your callback function.
     aaudio_result_t start() {
        aaudio_result_t result = AAudioStream_requestStart(mStream);
        if (result != AAUDIO_OK) {
            printf("ERROR - AAudioStream_requestStart() returned %d %s\n",
                    result, AAudio_convertResultToText(result));
        }
        return result;
    }

    // Stop the stream. AAudio will stop calling your callback function.
    aaudio_result_t stop() {
        aaudio_result_t result = AAudioStream_requestStop(mStream);
        if (result != AAUDIO_OK) {
            printf("ERROR - AAudioStream_requestStop() returned %d %s\n",
                    result, AAudio_convertResultToText(result));
        }
        int32_t xRunCount = AAudioStream_getXRunCount(mStream);
        printf("AAudioStream_getXRunCount %d\n", xRunCount);
        return result;
    }

    AAudioStream *getStream() const {
        return mStream;
    }

private:
    AAudioStream             *mStream = nullptr;
    aaudio_sharing_mode_t     mRequestedSharingMode = SHARING_MODE;
    aaudio_performance_mode_t mRequestedPerformanceMode = PERFORMANCE_MODE;

};

typedef struct SineThreadedData_s {

    SineGenerator  sineOsc1;
    SineGenerator  sineOsc2;
    Timestamp      timestamps[MAX_TIMESTAMPS];
    int64_t        framesTotal = 0;
    int64_t        nextFrameToGlitch = FORCED_UNDERRUN_PERIOD_FRAMES;
    int32_t        minNumFrames = INT32_MAX;
    int32_t        maxNumFrames = 0;
    int32_t        timestampCount = 0; // in timestamps

    int            scheduler = 0;
    bool           schedulerChecked = false;
    bool           forceUnderruns = false;

    AAudioSimplePlayer simplePlayer;
    int32_t            callbackCount = 0;
    WakeUp             waker{AAUDIO_OK};

} SineThreadedData_t;

// Callback function that fills the audio output buffer.
aaudio_data_callback_result_t SimplePlayerDataCallbackProc(
        AAudioStream *stream,
        void *userData,
        void *audioData,
        int32_t numFrames
        ) {

    // should not happen but just in case...
    if (userData == nullptr) {
        printf("ERROR - SimplePlayerDataCallbackProc needs userData\n");
        return AAUDIO_CALLBACK_RESULT_STOP;
    }
    SineThreadedData_t *sineData = (SineThreadedData_t *) userData;
    sineData->callbackCount++;

    sineData->framesTotal += numFrames;

    if (sineData->forceUnderruns) {
        if (sineData->framesTotal > sineData->nextFrameToGlitch) {
            usleep(FORCED_UNDERRUN_SLEEP_MICROS);
            printf("Simulate glitch at %lld\n", (long long) sineData->framesTotal);
            sineData->nextFrameToGlitch += FORCED_UNDERRUN_PERIOD_FRAMES;
        }
    }

    if (!sineData->schedulerChecked) {
        sineData->scheduler = sched_getscheduler(gettid());
        sineData->schedulerChecked = true;
    }

    if (sineData->timestampCount < MAX_TIMESTAMPS) {
        Timestamp *timestamp = &sineData->timestamps[sineData->timestampCount];
        aaudio_result_t result = AAudioStream_getTimestamp(stream,
            CLOCK_MONOTONIC, &timestamp->position, &timestamp->nanoseconds);
        if (result == AAUDIO_OK && // valid?
                (sineData->timestampCount == 0 || // first one?
                (timestamp->position != (timestamp - 1)->position))) { // advanced position?
            sineData->timestampCount++; // keep this one
        }
    }

    if (numFrames > sineData->maxNumFrames) {
        sineData->maxNumFrames = numFrames;
    }
    if (numFrames < sineData->minNumFrames) {
        sineData->minNumFrames = numFrames;
    }

    int32_t samplesPerFrame = AAudioStream_getChannelCount(stream);
    // This code only plays on the first one or two channels.
    // TODO Support arbitrary number of channels.
    switch (AAudioStream_getFormat(stream)) {
        case AAUDIO_FORMAT_PCM_I16: {
            int16_t *audioBuffer = (int16_t *) audioData;
            // Render sine waves as shorts to first channel.
            sineData->sineOsc1.render(&audioBuffer[0], samplesPerFrame, numFrames);
            // Render sine waves to second channel if there is one.
            if (samplesPerFrame > 1) {
                sineData->sineOsc2.render(&audioBuffer[1], samplesPerFrame, numFrames);
            }
        }
        break;
        case AAUDIO_FORMAT_PCM_FLOAT: {
            float *audioBuffer = (float *) audioData;
            // Render sine waves as floats to first channel.
            sineData->sineOsc1.render(&audioBuffer[0], samplesPerFrame, numFrames);
            // Render sine waves to second channel if there is one.
            if (samplesPerFrame > 1) {
                sineData->sineOsc2.render(&audioBuffer[1], samplesPerFrame, numFrames);
            }
        }
        break;
        default:
            return AAUDIO_CALLBACK_RESULT_STOP;
    }

    return AAUDIO_CALLBACK_RESULT_CONTINUE;
}

void SimplePlayerErrorCallbackProc(
        AAudioStream *stream __unused,
        void *userData __unused,
        aaudio_result_t error) {
    // should not happen but just in case...
    if (userData == nullptr) {
        printf("ERROR - MyPlayerErrorCallbackProc needs userData\n");
        return;
    }
    SineThreadedData_t *sineData = (SineThreadedData_t *) userData;
    android::status_t ret = sineData->waker.wake(error);
    printf("Error Callback, error: %d, futex wake returns %d\n", error, ret);
}


#endif //AAUDIO_SIMPLE_PLAYER_H
