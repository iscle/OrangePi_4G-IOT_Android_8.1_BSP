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

#ifndef AUDIOSOURCE_H_
#define AUDIOSOURCE_H_

namespace ndkaudio {

class AudioSource {
 public:
    AudioSource(int numChannels);
    virtual ~AudioSource();

    int getLastReadSize() {
        return lastReadSize_;
    }
    int getNumChannels() {
        return numChannels_;
    }
    int getNumBufferFrames() {
        return numBuffFrames_;
    }

    virtual int getData(long time, float * buff, int numFrames,
                        int numChannels) =0;

 protected:
    int numChannels_;
    int lastReadSize_;
    int numBuffFrames_;
};

} //namespace ndkaudio

#endif /* AUDIOSOURCE_H_ */
