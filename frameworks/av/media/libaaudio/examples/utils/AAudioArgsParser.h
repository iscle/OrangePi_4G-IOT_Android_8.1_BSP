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

#ifndef AAUDIO_EXAMPLE_ARGS_PARSER_H
#define AAUDIO_EXAMPLE_ARGS_PARSER_H

#include <cctype>
#include <unistd.h>
#include <stdio.h>
#include <stdlib.h>

#include <aaudio/AAudio.h>
#include <aaudio/AAudioTesting.h>

#include "AAudioExampleUtils.h"

// TODO use this as a base class within AAudio
class AAudioParameters {
public:

    /**
     * This is also known as samplesPerFrame.
     */
    int32_t getChannelCount() const {
        return mChannelCount;
    }

    void setChannelCount(int32_t channelCount) {
        mChannelCount = channelCount;
    }

    int32_t getSampleRate() const {
        return mSampleRate;
    }

    void setSampleRate(int32_t sampleRate) {
        mSampleRate = sampleRate;
    }

    aaudio_format_t getFormat() const {
        return mFormat;
    }

    void setFormat(aaudio_format_t format) {
        mFormat = format;
    }

    aaudio_sharing_mode_t getSharingMode() const {
        return mSharingMode;
    }

    void setSharingMode(aaudio_sharing_mode_t sharingMode) {
        mSharingMode = sharingMode;
    }

    int32_t getBufferCapacity() const {
        return mBufferCapacity;
    }

    void setBufferCapacity(int32_t frames) {
        mBufferCapacity = frames;
    }

    int32_t getPerformanceMode() const {
        return mPerformanceMode;
    }

    void setPerformanceMode(aaudio_performance_mode_t performanceMode) {
        mPerformanceMode = performanceMode;
    }

    int32_t getDeviceId() const {
        return mDeviceId;
    }

    void setDeviceId(int32_t deviceId) {
        mDeviceId = deviceId;
    }

    int32_t getNumberOfBursts() const {
        return mNumberOfBursts;
    }

    void setNumberOfBursts(int32_t numBursts) {
        mNumberOfBursts = numBursts;
    }

    /**
     * Apply these parameters to a stream builder.
     * @param builder
     */
    void applyParameters(AAudioStreamBuilder *builder) const {
        AAudioStreamBuilder_setChannelCount(builder, mChannelCount);
        AAudioStreamBuilder_setFormat(builder, mFormat);
        AAudioStreamBuilder_setSampleRate(builder, mSampleRate);
        AAudioStreamBuilder_setBufferCapacityInFrames(builder, mBufferCapacity);
        AAudioStreamBuilder_setDeviceId(builder, mDeviceId);
        AAudioStreamBuilder_setSharingMode(builder, mSharingMode);
        AAudioStreamBuilder_setPerformanceMode(builder, mPerformanceMode);
    }

private:
    int32_t                    mChannelCount    = AAUDIO_UNSPECIFIED;
    aaudio_format_t            mFormat          = AAUDIO_FORMAT_UNSPECIFIED;
    int32_t                    mSampleRate      = AAUDIO_UNSPECIFIED;

    int32_t                    mBufferCapacity  = AAUDIO_UNSPECIFIED;
    int32_t                    mDeviceId        = AAUDIO_UNSPECIFIED;
    aaudio_sharing_mode_t      mSharingMode     = AAUDIO_SHARING_MODE_SHARED;
    aaudio_performance_mode_t  mPerformanceMode = AAUDIO_PERFORMANCE_MODE_NONE;

    int32_t                    mNumberOfBursts  = AAUDIO_UNSPECIFIED;
};

class AAudioArgsParser : public AAudioParameters {
public:
    AAudioArgsParser() = default;
    ~AAudioArgsParser() = default;

    enum {
        DEFAULT_DURATION_SECONDS = 5
    };

    /**
     * @param arg
     * @return true if the argument was not handled
     */
    bool parseArg(const char *arg) {
        bool unrecognized = false;
        if (arg[0] == '-') {
            char option = arg[1];
            switch (option) {
                case 'b':
                    setBufferCapacity(atoi(&arg[2]));
                    break;
                case 'c':
                    setChannelCount(atoi(&arg[2]));
                    break;
                case 'd':
                    setDeviceId(atoi(&arg[2]));
                    break;
                case 's':
                    mDurationSeconds = atoi(&arg[2]);
                    break;
                case 'm': {
                    aaudio_policy_t policy = AAUDIO_POLICY_AUTO;
                    if (strlen(arg) > 2) {
                        policy = atoi(&arg[2]);
                    }
                    AAudio_setMMapPolicy(policy);
                } break;
                case 'n':
                    setNumberOfBursts(atoi(&arg[2]));
                    break;
                case 'p':
                    setPerformanceMode(parsePerformanceMode(arg[2]));
                    break;
                case 'r':
                    setSampleRate(atoi(&arg[2]));
                    break;
                case 'x':
                    setSharingMode(AAUDIO_SHARING_MODE_EXCLUSIVE);
                    break;
                default:
                    unrecognized = true;
                    break;
            }
        }
        return unrecognized;
    }

    /**
     *
     * @param argc
     * @param argv
     * @return true if an unrecognized argument was passed
     */
    bool parseArgs(int argc, const char **argv) {
        for (int i = 1; i < argc; i++) {
            const char *arg = argv[i];
            if (parseArg(arg)) {
                usage();
                return true;
            }

        }
        return false;
    }

    static void usage() {
        printf("-c{channels} -d{duration} -m -n{burstsPerBuffer} -p{perfMode} -r{rate} -x\n");
        printf("      Default values are UNSPECIFIED unless otherwise stated.\n");
        printf("      -b{bufferCapacity} frames\n");
        printf("      -c{channels} for example 2 for stereo\n");
        printf("      -d{deviceId} default is %d\n", AAUDIO_UNSPECIFIED);
        printf("      -s{duration} in seconds, default is %d\n", DEFAULT_DURATION_SECONDS);
        printf("      -m{0|1|2|3} set MMAP policy\n");
        printf("          0 = _UNSPECIFIED, default\n");
        printf("          1 = _NEVER\n");
        printf("          2 = _AUTO, also if -m is used with no number\n");
        printf("          3 = _ALWAYS\n");
        printf("      -n{numberOfBursts} for setBufferSize\n");
        printf("      -p{performanceMode} set output AAUDIO_PERFORMANCE_MODE*, default NONE\n");
        printf("          n for _NONE\n");
        printf("          l for _LATENCY\n");
        printf("          p for _POWER_SAVING;\n");
        printf("      -r{sampleRate} for example 44100\n");
        printf("      -x to use EXCLUSIVE mode\n");
    }

    static aaudio_performance_mode_t parsePerformanceMode(char c) {
        aaudio_performance_mode_t mode = AAUDIO_PERFORMANCE_MODE_NONE;
        switch (c) {
            case 'n':
                mode = AAUDIO_PERFORMANCE_MODE_NONE;
                break;
            case 'l':
                mode = AAUDIO_PERFORMANCE_MODE_LOW_LATENCY;
                break;
            case 'p':
                mode = AAUDIO_PERFORMANCE_MODE_POWER_SAVING;
                break;
            default:
                printf("ERROR invalid performance mode %c\n", c);
                break;
        }
        return mode;
    }

    /**
     * Print stream parameters in comparison with requested values.
     * @param stream
     */
    void compareWithStream(AAudioStream *stream) const {

        printf("  DeviceId:     requested = %d, actual = %d\n",
               getDeviceId(), AAudioStream_getDeviceId(stream));

        aaudio_stream_state_t state = AAudioStream_getState(stream);
        printf("  State:        %s\n", AAudio_convertStreamStateToText(state));

        // Check to see what kind of stream we actually got.
        printf("  SampleRate:   requested = %d, actual = %d\n",
               getSampleRate(), AAudioStream_getSampleRate(stream));

        printf("  ChannelCount: requested = %d, actual = %d\n",
               getChannelCount(), AAudioStream_getChannelCount(stream));

        printf("  DataFormat:   requested = %d, actual = %d\n",
               getFormat(), AAudioStream_getFormat(stream));

        int32_t framesPerBurst = AAudioStream_getFramesPerBurst(stream);
        int32_t sizeFrames = AAudioStream_getBufferSizeInFrames(stream);
        printf("  Buffer:       burst     = %d\n", framesPerBurst);
        if (framesPerBurst > 0) {
            printf("  Buffer:       size      = %d = (%d * %d) + %d\n",
                   sizeFrames,
                   (sizeFrames / framesPerBurst),
                   framesPerBurst,
                   (sizeFrames % framesPerBurst));
        }
        printf("  Capacity:     requested = %d, actual = %d\n", getBufferCapacity(),
               AAudioStream_getBufferCapacityInFrames(stream));

        printf("  SharingMode:  requested = %s, actual = %s\n",
               getSharingModeText(getSharingMode()),
               getSharingModeText(AAudioStream_getSharingMode(stream)));

        printf("  PerformanceMode: requested = %d, actual = %d\n",
               getPerformanceMode(), AAudioStream_getPerformanceMode(stream));
        printf("  Is MMAP used? %s\n", AAudioStream_isMMapUsed(stream)
               ? "yes" : "no");

    }

    int32_t getDurationSeconds() const {
        return mDurationSeconds;
    }

    void setDurationSeconds(int32_t seconds) {
        mDurationSeconds = seconds;
    }

private:
    int32_t      mDurationSeconds = DEFAULT_DURATION_SECONDS;
};

#endif // AAUDIO_EXAMPLE_ARGS_PARSER_H
