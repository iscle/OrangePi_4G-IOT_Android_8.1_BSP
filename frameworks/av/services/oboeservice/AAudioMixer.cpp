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

#define LOG_TAG "AAudioService"
//#define LOG_NDEBUG 0
#include <utils/Log.h>

#define ATRACE_TAG ATRACE_TAG_AUDIO

#include <cstring>
#include <utils/Trace.h>

#include "AAudioMixer.h"

#ifndef AAUDIO_MIXER_ATRACE_ENABLED
#define AAUDIO_MIXER_ATRACE_ENABLED    1
#endif

using android::WrappingBuffer;
using android::FifoBuffer;
using android::fifo_frames_t;

AAudioMixer::~AAudioMixer() {
    delete[] mOutputBuffer;
}

void AAudioMixer::allocate(int32_t samplesPerFrame, int32_t framesPerBurst) {
    mSamplesPerFrame = samplesPerFrame;
    mFramesPerBurst = framesPerBurst;
    int32_t samplesPerBuffer = samplesPerFrame * framesPerBurst;
    mOutputBuffer = new float[samplesPerBuffer];
    mBufferSizeInBytes = samplesPerBuffer * sizeof(float);
}

void AAudioMixer::clear() {
    memset(mOutputBuffer, 0, mBufferSizeInBytes);
}

bool AAudioMixer::mix(int trackIndex, FifoBuffer *fifo, float volume) {
    WrappingBuffer wrappingBuffer;
    float *destination = mOutputBuffer;
    fifo_frames_t framesLeft = mFramesPerBurst;

#if AAUDIO_MIXER_ATRACE_ENABLED
    ATRACE_BEGIN("aaMix");
#endif /* AAUDIO_MIXER_ATRACE_ENABLED */

    // Gather the data from the client. May be in two parts.
    fifo_frames_t fullFrames = fifo->getFullDataAvailable(&wrappingBuffer);
#if AAUDIO_MIXER_ATRACE_ENABLED
    if (ATRACE_ENABLED()) {
        char rdyText[] = "aaMixRdy#";
        char letter = 'A' + (trackIndex % 26);
        rdyText[sizeof(rdyText) - 2] = letter;
        ATRACE_INT(rdyText, fullFrames);
    }
#else /* MIXER_ATRACE_ENABLED */
    (void) trackIndex;
    (void) fullFrames;
#endif /* AAUDIO_MIXER_ATRACE_ENABLED */

    // Mix data in one or two parts.
    int partIndex = 0;
    while (framesLeft > 0 && partIndex < WrappingBuffer::SIZE) {
        fifo_frames_t framesToMix = framesLeft;
        fifo_frames_t framesAvailable = wrappingBuffer.numFrames[partIndex];
        if (framesAvailable > 0) {
            if (framesToMix > framesAvailable) {
                framesToMix = framesAvailable;
            }
            mixPart(destination, (float *)wrappingBuffer.data[partIndex], framesToMix, volume);

            destination += framesToMix * mSamplesPerFrame;
            framesLeft -= framesToMix;
        }
        partIndex++;
    }
    // Always advance by one burst even if we do not have the data.
    // Otherwise the stream timing will drift whenever there is an underflow.
    // This actual underflow can then be detected by the client for XRun counting.
    fifo->getFifoControllerBase()->advanceReadIndex(mFramesPerBurst);

#if AAUDIO_MIXER_ATRACE_ENABLED
    ATRACE_END();
#endif /* AAUDIO_MIXER_ATRACE_ENABLED */

    return (framesLeft > 0); // did not get all the frames we needed, ie. "underflow"
}

void AAudioMixer::mixPart(float *destination, float *source, int32_t numFrames, float volume) {
    int32_t numSamples = numFrames * mSamplesPerFrame;
    // TODO maybe optimize using SIMD
    for (int sampleIndex = 0; sampleIndex < numSamples; sampleIndex++) {
        *destination++ += *source++ * volume;
    }
}

float *AAudioMixer::getOutputBuffer() {
    return mOutputBuffer;
}
