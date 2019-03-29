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

#ifndef AUDIOPLAYER_H_
#define AUDIOPLAYER_H_

#include <SLES/OpenSLES.h>
#include <SLES/OpenSLES_Android.h>

namespace ndkaudio {

class AudioSource;

class AudioPlayer {
 public:
    AudioPlayer();
    ~AudioPlayer();

    SLresult Open(int numChannels, AudioSource* filler);
    void Close();

    SLresult RealizePlayer();
    SLresult RealizeRoutingProxy();

    SLresult Start();
    void Stop();

    inline bool isPlaying() {
        return playing_;
    }
    inline AudioSource* getSource() {
        return source_;
    }

    // This is public because it needs to be called by the OpenSL ES callback, but it should not
    // be called by anyone else.
    SLresult enqueBuffer();

    SLPlayItf getPlayerObject() {
        return bqPlayerPlay_;
    }

    SLAndroidConfigurationItf getConfigItf() {
        return configItf_;
    }

 private:
    // void fill();

    AudioSource* source_;
    int sampleRate_;
    int numChannels_;

    float* playBuff_;
    long numPlayBuffFrames_;
    long playBuffSizeInBytes_;

    bool playing_;

    long time_;

    // OpenSLES stuff
    SLObjectItf bqPlayerObject_;
    SLPlayItf bqPlayerPlay_;
    SLAndroidSimpleBufferQueueItf bq_;
    SLAndroidConfigurationItf configItf_;
};

} // namespace ndkaudio

#endif /* AUDIOPLAYER_H_ */
