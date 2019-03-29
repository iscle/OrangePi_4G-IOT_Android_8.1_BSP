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

#ifndef WAVETABLEOSCILLATOR_H_
#define WAVETABLEOSCILLATOR_H_

#include "PeriodicAudioSource.h"

namespace ndkaudio {

/*
 * The assumption here is that the provided wave table contains 1 cycle of the wave
 * and that the first and last samples are the same.
 */
class WaveTableOscillator : public PeriodicAudioSource {
 public:
    WaveTableOscillator(int numChannels, float* waveTable, int waveTableSize);

    void setWaveTable(float* waveTable, int waveTableSize);

    int getData(long time, float* outBuff, int numFrames, int outChans);

 private:
    float* waveTable_;
    int waveTableSize_;

    // 'nominal' frequency (i.e. how many times we step through the
    // 1-cycle wave table in a second
    float fN_;

    // current pointer into the wave table
    float srcPhase_;

    // profiling
    long prevFillTime_;
};

} // namespace ndkaudio

#endif /* WAVETABLEOSCILLATOR_H_ */
