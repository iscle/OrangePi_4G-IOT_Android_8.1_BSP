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

#ifndef _AUDIORECORDER_H_
#define _AUDIORECORDER_H_

#include <SLES/OpenSLES.h>
#include <SLES/OpenSLES_Android.h>

namespace ndkaudio {

class AudioSink;

class AudioRecorder {
 public:
    AudioRecorder();
    ~AudioRecorder();

    void Open(int numChannels, AudioSink* sink);
    void Close();

    void RealizeRecorder();
    void RealizeRoutingProxy();

    void Start();
    void Stop();

    inline bool isRecording() {
        return recording_;
    }
    inline AudioSink* getSink() {
        return sink_;
    }

    SLAndroidConfigurationItf getConfigItf() {
        return configItf_;
    }

    // public, but don't call directly (called by the OSLES callback)
    SLresult enqueBuffer();

    int GetNumBufferSamples() {
        return numBufferSamples_;
    }
    float* GetRecordBuffer();

 private:
    AudioSink* sink_;
    bool recording_;

    int sampleRate_;
    int numChannels_;

    int numBufferSamples_;

    // OpenSL ES stuff
    // - Engine
    SLObjectItf engineObj_;
    SLEngineItf engineItf_;

    // - Recorder
    SLObjectItf recorderObj_;
    SLRecordItf recorderItf_;
    SLAndroidSimpleBufferQueueItf recBuffQueueItf_;
    SLAndroidConfigurationItf configItf_;
};

} // namespace ndkaudio

#endif // _AUDIORECORDER_H_
