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


#define LOG_TAG "AAudio"
#include <utils/Log.h>
#include <hardware/audio.h>

#include "AAudioStreamParameters.h"

using namespace aaudio;

// TODO These defines should be moved to a central place in audio.
#define SAMPLES_PER_FRAME_MIN        1
// TODO Remove 8 channel limitation.
#define SAMPLES_PER_FRAME_MAX        FCC_8
#define SAMPLE_RATE_HZ_MIN           8000
// HDMI supports up to 32 channels at 1536000 Hz.
#define SAMPLE_RATE_HZ_MAX           1600000

AAudioStreamParameters::AAudioStreamParameters() {}
AAudioStreamParameters::~AAudioStreamParameters() {}

void AAudioStreamParameters::copyFrom(const AAudioStreamParameters &other) {
    mSamplesPerFrame = other.mSamplesPerFrame;
    mSampleRate      = other.mSampleRate;
    mDeviceId        = other.mDeviceId;
    mSharingMode     = other.mSharingMode;
    mAudioFormat     = other.mAudioFormat;
    mDirection       = other.mDirection;
    mBufferCapacity  = other.mBufferCapacity;
}

aaudio_result_t AAudioStreamParameters::validate() const {
    if (mSamplesPerFrame != AAUDIO_UNSPECIFIED
        && (mSamplesPerFrame < SAMPLES_PER_FRAME_MIN || mSamplesPerFrame > SAMPLES_PER_FRAME_MAX)) {
        ALOGE("AAudioStreamParameters: channelCount out of range = %d", mSamplesPerFrame);
        return AAUDIO_ERROR_OUT_OF_RANGE;
    }

    if (mDeviceId < 0) {
        ALOGE("AAudioStreamParameters: deviceId out of range = %d", mDeviceId);
        return AAUDIO_ERROR_OUT_OF_RANGE;
    }

    switch (mSharingMode) {
        case AAUDIO_SHARING_MODE_EXCLUSIVE:
        case AAUDIO_SHARING_MODE_SHARED:
            break;
        default:
            ALOGE("AAudioStreamParameters: illegal sharingMode = %d", mSharingMode);
            return AAUDIO_ERROR_ILLEGAL_ARGUMENT;
            // break;
    }

    switch (mAudioFormat) {
        case AAUDIO_FORMAT_UNSPECIFIED:
        case AAUDIO_FORMAT_PCM_I16:
        case AAUDIO_FORMAT_PCM_FLOAT:
            break; // valid
        default:
            ALOGE("AAudioStreamParameters: audioFormat not valid = %d", mAudioFormat);
            return AAUDIO_ERROR_INVALID_FORMAT;
            // break;
    }

    if (mSampleRate != AAUDIO_UNSPECIFIED
        && (mSampleRate < SAMPLE_RATE_HZ_MIN || mSampleRate > SAMPLE_RATE_HZ_MAX)) {
        ALOGE("AAudioStreamParameters: sampleRate out of range = %d", mSampleRate);
        return AAUDIO_ERROR_INVALID_RATE;
    }

    if (mBufferCapacity < 0) {
        ALOGE("AAudioStreamParameters: bufferCapacity out of range = %d", mBufferCapacity);
        return AAUDIO_ERROR_OUT_OF_RANGE;
    }

    switch (mDirection) {
        case AAUDIO_DIRECTION_INPUT:
        case AAUDIO_DIRECTION_OUTPUT:
            break; // valid
        default:
            ALOGE("AAudioStreamParameters: direction not valid = %d", mDirection);
            return AAUDIO_ERROR_ILLEGAL_ARGUMENT;
            // break;
    }

    return AAUDIO_OK;
}

void AAudioStreamParameters::dump() const {
    ALOGD("AAudioStreamParameters mDeviceId        = %d", mDeviceId);
    ALOGD("AAudioStreamParameters mSampleRate      = %d", mSampleRate);
    ALOGD("AAudioStreamParameters mSamplesPerFrame = %d", mSamplesPerFrame);
    ALOGD("AAudioStreamParameters mSharingMode     = %d", (int)mSharingMode);
    ALOGD("AAudioStreamParameters mAudioFormat     = %d", (int)mAudioFormat);
    ALOGD("AAudioStreamParameters mDirection       = %d", mDirection);
    ALOGD("AAudioStreamParameters mBufferCapacity  = %d", mBufferCapacity);
}

