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
#ifndef CTS_MEDIA_TEST_AAUDIO_UTILS_H
#define CTS_MEDIA_TEST_AAUDIO_UTILS_H

#include <aaudio/AAudio.h>

int64_t getNanoseconds(clockid_t clockId = CLOCK_MONOTONIC);
const char* performanceModeToString(aaudio_performance_mode_t mode);
const char* sharingModeToString(aaudio_sharing_mode_t mode);

class StreamBuilderHelper {
  public:
    struct Parameters {
        int32_t sampleRate;
        int32_t channelCount;
        aaudio_format_t dataFormat;
        aaudio_sharing_mode_t sharingMode;
        aaudio_performance_mode_t perfMode;
    };

    void initBuilder();
    void createAndVerifyStream(bool *success);
    void close();

    void startStream() {
        streamCommand(&AAudioStream_requestStart,
                AAUDIO_STREAM_STATE_STARTING, AAUDIO_STREAM_STATE_STARTED);
    }
    void pauseStream() {
        streamCommand(&AAudioStream_requestPause,
                AAUDIO_STREAM_STATE_PAUSING, AAUDIO_STREAM_STATE_PAUSED);
    }
    void stopStream() {
        streamCommand(&AAudioStream_requestStop,
                AAUDIO_STREAM_STATE_STOPPING, AAUDIO_STREAM_STATE_STOPPED);
    }
    void flushStream() {
        streamCommand(&AAudioStream_requestFlush,
                AAUDIO_STREAM_STATE_FLUSHING, AAUDIO_STREAM_STATE_FLUSHED);
    }

    AAudioStreamBuilder* builder() const { return mBuilder; }
    AAudioStream* stream() const { return mStream; }
    const Parameters& actual() const { return mActual; }
    int32_t framesPerBurst() const { return mFramesPerBurst; }

  protected:
    StreamBuilderHelper(aaudio_direction_t direction, int32_t sampleRate,
            int32_t channelCount, aaudio_format_t dataFormat,
            aaudio_sharing_mode_t sharingMode, aaudio_performance_mode_t perfMode);
    ~StreamBuilderHelper();

    typedef aaudio_result_t (StreamCommand)(AAudioStream*);
    void streamCommand(
            StreamCommand cmd, aaudio_stream_state_t fromState, aaudio_stream_state_t toState);

    const aaudio_direction_t mDirection;
    const Parameters mRequested;
    Parameters mActual;
    int32_t mFramesPerBurst;
    AAudioStreamBuilder *mBuilder;
    AAudioStream *mStream;
};

class InputStreamBuilderHelper : public StreamBuilderHelper {
  public:
    InputStreamBuilderHelper(
            aaudio_sharing_mode_t requestedSharingMode,
            aaudio_performance_mode_t requestedPerfMode);
    void createAndVerifyStream(bool *success);
};

class OutputStreamBuilderHelper : public StreamBuilderHelper {
  public:
    OutputStreamBuilderHelper(
            aaudio_sharing_mode_t requestedSharingMode,
            aaudio_performance_mode_t requestedPerfMode);
    void initBuilder();
    void createAndVerifyStream(bool *success);

  private:
    const int32_t kBufferCapacityFrames = 2000;
};

#endif  // CTS_MEDIA_TEST_AAUDIO_UTILS_H
