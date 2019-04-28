/*
 * Copyright 2016 The Android Open Source Project
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

#include <cutils/properties.h>
#include <stdint.h>
#include <sys/types.h>
#include <utils/Errors.h>

#include "aaudio/AAudio.h"
#include <aaudio/AAudioTesting.h>

#include "utility/AAudioUtilities.h"

using namespace android;

// This is 3 dB, (10^(3/20)), to match the maximum headroom in AudioTrack for float data.
// It is designed to allow occasional transient peaks.
#define MAX_HEADROOM (1.41253754f)
#define MIN_HEADROOM (0 - MAX_HEADROOM)

int32_t AAudioConvert_formatToSizeInBytes(aaudio_format_t format) {
    int32_t size = AAUDIO_ERROR_ILLEGAL_ARGUMENT;
    switch (format) {
        case AAUDIO_FORMAT_PCM_I16:
            size = sizeof(int16_t);
            break;
        case AAUDIO_FORMAT_PCM_FLOAT:
            size = sizeof(float);
            break;
        default:
            break;
    }
    return size;
}


// TODO expose and call clamp16_from_float function in primitives.h
static inline int16_t clamp16_from_float(float f) {
    /* Offset is used to expand the valid range of [-1.0, 1.0) into the 16 lsbs of the
     * floating point significand. The normal shift is 3<<22, but the -15 offset
     * is used to multiply by 32768.
     */
    static const float offset = (float)(3 << (22 - 15));
    /* zero = (0x10f << 22) =  0x43c00000 (not directly used) */
    static const int32_t limneg = (0x10f << 22) /*zero*/ - 32768; /* 0x43bf8000 */
    static const int32_t limpos = (0x10f << 22) /*zero*/ + 32767; /* 0x43c07fff */

    union {
        float f;
        int32_t i;
    } u;

    u.f = f + offset; /* recenter valid range */
    /* Now the valid range is represented as integers between [limneg, limpos].
     * Clamp using the fact that float representation (as an integer) is an ordered set.
     */
    if (u.i < limneg)
        u.i = -32768;
    else if (u.i > limpos)
        u.i = 32767;
    return u.i; /* Return lower 16 bits, the part of interest in the significand. */
}

// Same but without clipping.
// Convert -1.0f to +1.0f to -32768 to +32767
static inline int16_t floatToInt16(float f) {
    static const float offset = (float)(3 << (22 - 15));
    union {
        float f;
        int32_t i;
    } u;
    u.f = f + offset; /* recenter valid range */
    return u.i; /* Return lower 16 bits, the part of interest in the significand. */
}

static float clipAndClampFloatToPcm16(float sample, float scaler) {
    // Clip to valid range of a float sample to prevent excessive volume.
    if (sample > MAX_HEADROOM) sample = MAX_HEADROOM;
    else if (sample < MIN_HEADROOM) sample = MIN_HEADROOM;

    // Scale and convert to a short.
    float fval = sample * scaler;
    return clamp16_from_float(fval);
}

void AAudioConvert_floatToPcm16(const float *source,
                                int16_t *destination,
                                int32_t numSamples,
                                float amplitude) {
    float scaler = amplitude;
    for (int i = 0; i < numSamples; i++) {
        float sample = *source++;
        *destination++ = clipAndClampFloatToPcm16(sample, scaler);
    }
}

void AAudioConvert_floatToPcm16(const float *source,
                                int16_t *destination,
                                int32_t numFrames,
                                int32_t samplesPerFrame,
                                float amplitude1,
                                float amplitude2) {
    float scaler = amplitude1;
    // divide by numFrames so that we almost reach amplitude2
    float delta = (amplitude2 - amplitude1) / numFrames;
    for (int frameIndex = 0; frameIndex < numFrames; frameIndex++) {
        for (int sampleIndex = 0; sampleIndex < samplesPerFrame; sampleIndex++) {
            float sample = *source++;
            *destination++ = clipAndClampFloatToPcm16(sample, scaler);
        }
        scaler += delta;
    }
}

#define SHORT_SCALE  32768

void AAudioConvert_pcm16ToFloat(const int16_t *source,
                                float *destination,
                                int32_t numSamples,
                                float amplitude) {
    float scaler = amplitude / SHORT_SCALE;
    for (int i = 0; i < numSamples; i++) {
        destination[i] = source[i] * scaler;
    }
}

// This code assumes amplitude1 and amplitude2 are between 0.0 and 1.0
void AAudioConvert_pcm16ToFloat(const int16_t *source,
                                float *destination,
                                int32_t numFrames,
                                int32_t samplesPerFrame,
                                float amplitude1,
                                float amplitude2) {
    float scaler = amplitude1 / SHORT_SCALE;
    float delta = (amplitude2 - amplitude1) / (SHORT_SCALE * (float) numFrames);
    for (int frameIndex = 0; frameIndex < numFrames; frameIndex++) {
        for (int sampleIndex = 0; sampleIndex < samplesPerFrame; sampleIndex++) {
            *destination++ = *source++ * scaler;
        }
        scaler += delta;
    }
}

// This code assumes amplitude1 and amplitude2 are between 0.0 and 1.0
void AAudio_linearRamp(const float *source,
                       float *destination,
                       int32_t numFrames,
                       int32_t samplesPerFrame,
                       float amplitude1,
                       float amplitude2) {
    float scaler = amplitude1;
    float delta = (amplitude2 - amplitude1) / numFrames;
    for (int frameIndex = 0; frameIndex < numFrames; frameIndex++) {
        for (int sampleIndex = 0; sampleIndex < samplesPerFrame; sampleIndex++) {
            float sample = *source++;

            // Clip to valid range of a float sample to prevent excessive volume.
            if (sample > MAX_HEADROOM) sample = MAX_HEADROOM;
            else if (sample < MIN_HEADROOM) sample = MIN_HEADROOM;

            *destination++ = sample * scaler;
        }
        scaler += delta;
    }
}

// This code assumes amplitude1 and amplitude2 are between 0.0 and 1.0
void AAudio_linearRamp(const int16_t *source,
                       int16_t *destination,
                       int32_t numFrames,
                       int32_t samplesPerFrame,
                       float amplitude1,
                       float amplitude2) {
    float scaler = amplitude1 / SHORT_SCALE;
    float delta = (amplitude2 - amplitude1) / (SHORT_SCALE * (float) numFrames);
    for (int frameIndex = 0; frameIndex < numFrames; frameIndex++) {
        for (int sampleIndex = 0; sampleIndex < samplesPerFrame; sampleIndex++) {
            // No need to clip because int16_t range is inherently limited.
            float sample =  *source++ * scaler;
            *destination++ =  floatToInt16(sample);
        }
        scaler += delta;
    }
}

status_t AAudioConvert_aaudioToAndroidStatus(aaudio_result_t result) {
    // This covers the case for AAUDIO_OK and for positive results.
    if (result >= 0) {
        return result;
    }
    status_t status;
    switch (result) {
    case AAUDIO_ERROR_DISCONNECTED:
    case AAUDIO_ERROR_NO_SERVICE:
        status = DEAD_OBJECT;
        break;
    case AAUDIO_ERROR_INVALID_HANDLE:
        status = BAD_TYPE;
        break;
    case AAUDIO_ERROR_INVALID_STATE:
        status = INVALID_OPERATION;
        break;
    case AAUDIO_ERROR_INVALID_RATE:
    case AAUDIO_ERROR_INVALID_FORMAT:
    case AAUDIO_ERROR_ILLEGAL_ARGUMENT:
    case AAUDIO_ERROR_OUT_OF_RANGE:
        status = BAD_VALUE;
        break;
    case AAUDIO_ERROR_WOULD_BLOCK:
        status = WOULD_BLOCK;
        break;
    case AAUDIO_ERROR_NULL:
        status = UNEXPECTED_NULL;
        break;
    case AAUDIO_ERROR_UNAVAILABLE:
        status = NOT_ENOUGH_DATA;
        break;

    // TODO translate these result codes
    case AAUDIO_ERROR_INTERNAL:
    case AAUDIO_ERROR_UNIMPLEMENTED:
    case AAUDIO_ERROR_NO_FREE_HANDLES:
    case AAUDIO_ERROR_NO_MEMORY:
    case AAUDIO_ERROR_TIMEOUT:
    default:
        status = UNKNOWN_ERROR;
        break;
    }
    return status;
}

aaudio_result_t AAudioConvert_androidToAAudioResult(status_t status) {
    // This covers the case for OK and for positive result.
    if (status >= 0) {
        return status;
    }
    aaudio_result_t result;
    switch (status) {
    case BAD_TYPE:
        result = AAUDIO_ERROR_INVALID_HANDLE;
        break;
    case DEAD_OBJECT:
        result = AAUDIO_ERROR_NO_SERVICE;
        break;
    case INVALID_OPERATION:
        result = AAUDIO_ERROR_INVALID_STATE;
        break;
    case UNEXPECTED_NULL:
        result = AAUDIO_ERROR_NULL;
        break;
    case BAD_VALUE:
        result = AAUDIO_ERROR_ILLEGAL_ARGUMENT;
        break;
    case WOULD_BLOCK:
        result = AAUDIO_ERROR_WOULD_BLOCK;
        break;
    case NOT_ENOUGH_DATA:
        result = AAUDIO_ERROR_UNAVAILABLE;
        break;
    default:
        result = AAUDIO_ERROR_INTERNAL;
        break;
    }
    return result;
}

audio_format_t AAudioConvert_aaudioToAndroidDataFormat(aaudio_format_t aaudioFormat) {
    audio_format_t androidFormat;
    switch (aaudioFormat) {
    case AAUDIO_FORMAT_PCM_I16:
        androidFormat = AUDIO_FORMAT_PCM_16_BIT;
        break;
    case AAUDIO_FORMAT_PCM_FLOAT:
        androidFormat = AUDIO_FORMAT_PCM_FLOAT;
        break;
    default:
        androidFormat = AUDIO_FORMAT_DEFAULT;
        ALOGE("AAudioConvert_aaudioToAndroidDataFormat 0x%08X unrecognized", aaudioFormat);
        break;
    }
    return androidFormat;
}

aaudio_format_t AAudioConvert_androidToAAudioDataFormat(audio_format_t androidFormat) {
    aaudio_format_t aaudioFormat = AAUDIO_FORMAT_INVALID;
    switch (androidFormat) {
    case AUDIO_FORMAT_PCM_16_BIT:
        aaudioFormat = AAUDIO_FORMAT_PCM_I16;
        break;
    case AUDIO_FORMAT_PCM_FLOAT:
        aaudioFormat = AAUDIO_FORMAT_PCM_FLOAT;
        break;
    default:
        aaudioFormat = AAUDIO_FORMAT_INVALID;
        ALOGE("AAudioConvert_androidToAAudioDataFormat 0x%08X unrecognized", androidFormat);
        break;
    }
    return aaudioFormat;
}

int32_t AAudioConvert_framesToBytes(int32_t numFrames,
                                            int32_t bytesPerFrame,
                                            int32_t *sizeInBytes) {
    // TODO implement more elegantly
    const int32_t maxChannels = 256; // ridiculously large
    const int32_t maxBytesPerFrame = maxChannels * sizeof(float);
    // Prevent overflow by limiting multiplicands.
    if (bytesPerFrame > maxBytesPerFrame || numFrames > (0x3FFFFFFF / maxBytesPerFrame)) {
        ALOGE("size overflow, numFrames = %d, frameSize = %zd", numFrames, bytesPerFrame);
        return AAUDIO_ERROR_OUT_OF_RANGE;
    }
    *sizeInBytes = numFrames * bytesPerFrame;
    return AAUDIO_OK;
}

static int32_t AAudioProperty_getMMapProperty(const char *propName,
                                              int32_t defaultValue,
                                              const char * caller) {
    int32_t prop = property_get_int32(propName, defaultValue);
    switch (prop) {
        case AAUDIO_UNSPECIFIED:
        case AAUDIO_POLICY_NEVER:
        case AAUDIO_POLICY_ALWAYS:
        case AAUDIO_POLICY_AUTO:
            break;
        default:
            ALOGE("%s: invalid = %d", caller, prop);
            prop = defaultValue;
            break;
    }
    return prop;
}

int32_t AAudioProperty_getMMapPolicy() {
    return AAudioProperty_getMMapProperty(AAUDIO_PROP_MMAP_POLICY,
                                          AAUDIO_UNSPECIFIED, __func__);
}

int32_t AAudioProperty_getMMapExclusivePolicy() {
    return AAudioProperty_getMMapProperty(AAUDIO_PROP_MMAP_EXCLUSIVE_POLICY,
                                          AAUDIO_UNSPECIFIED, __func__);
}

int32_t AAudioProperty_getMixerBursts() {
    const int32_t defaultBursts = 2; // arbitrary, use 2 for double buffered
    const int32_t maxBursts = 1024; // arbitrary
    int32_t prop = property_get_int32(AAUDIO_PROP_MIXER_BURSTS, defaultBursts);
    if (prop < 1 || prop > maxBursts) {
        ALOGE("AAudioProperty_getMixerBursts: invalid = %d", prop);
        prop = defaultBursts;
    }
    return prop;
}

int32_t AAudioProperty_getWakeupDelayMicros() {
    const int32_t minMicros = 0; // arbitrary
    const int32_t defaultMicros = 200; // arbitrary, based on some observed jitter
    const int32_t maxMicros = 5000; // arbitrary, probably don't want more than 500
    int32_t prop = property_get_int32(AAUDIO_PROP_WAKEUP_DELAY_USEC, defaultMicros);
    if (prop < minMicros) {
        ALOGW("AAudioProperty_getWakeupDelayMicros: clipped %d to %d", prop, minMicros);
        prop = minMicros;
    } else if (prop > maxMicros) {
        ALOGW("AAudioProperty_getWakeupDelayMicros: clipped %d to %d", prop, maxMicros);
        prop = maxMicros;
    }
    return prop;
}

int32_t AAudioProperty_getMinimumSleepMicros() {
    const int32_t minMicros = 20; // arbitrary
    const int32_t defaultMicros = 200; // arbitrary
    const int32_t maxMicros = 2000; // arbitrary
    int32_t prop = property_get_int32(AAUDIO_PROP_MINIMUM_SLEEP_USEC, defaultMicros);
    if (prop < minMicros) {
        ALOGW("AAudioProperty_getMinimumSleepMicros: clipped %d to %d", prop, minMicros);
        prop = minMicros;
    } else if (prop > maxMicros) {
        ALOGW("AAudioProperty_getMinimumSleepMicros: clipped %d to %d", prop, maxMicros);
        prop = maxMicros;
    }
    return prop;
}

int32_t AAudioProperty_getHardwareBurstMinMicros() {
    const int32_t defaultMicros = 1000; // arbitrary
    const int32_t maxMicros = 1000 * 1000; // arbitrary
    int32_t prop = property_get_int32(AAUDIO_PROP_HW_BURST_MIN_USEC, defaultMicros);
    if (prop < 1 || prop > maxMicros) {
        ALOGE("AAudioProperty_getHardwareBurstMinMicros: invalid = %d, use %d",
              prop, defaultMicros);
        prop = defaultMicros;
    }
    return prop;
}
