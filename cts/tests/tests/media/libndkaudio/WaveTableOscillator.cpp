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

#include <android/log.h>

//static const char * const TAG = "WaveTableOscillator";

#include "WaveTableOscillator.h"
#include "SystemParams.h"

namespace ndkaudio {

WaveTableOscillator::WaveTableOscillator(int numChannels, float* waveTable,
                                         int waveTableSize)
 : PeriodicAudioSource(numChannels),
   waveTable_(waveTable),
   waveTableSize_(waveTableSize),
   fN_(0.0f),
   srcPhase_(0.0f),
   prevFillTime_(0)
{
    setWaveTable(waveTable, waveTableSize);
}

void WaveTableOscillator::setWaveTable(float* waveTable, int waveTableSize) {
    waveTable_ = waveTable;
    waveTableSize_ = waveTableSize - 1;

    // The frequency that would be played if we took every sample from the table and
    // played it at the system sample-rate. The "Nominal" frequency
    fN_ = SystemParams::getSampleRate() / (float) waveTableSize_;
}

int WaveTableOscillator::getData(long time, float* outBuff, int numFrames,
                                 int /*outChans*/) {
    prevFillTime_ = time;

    // Frequency - main
    float currentFreq = targetFreq_;

    float phaseIncr = currentFreq / fN_;

//	__android_log_print(ANDROID_LOG_INFO, TAG, "getData() freq:%f, fN_:%f, phs:%f incr:%f", currentFreq, fN_, srcPhase_, phaseIncr);

    if (numChannels_ == 1) {
        // calculate wave values
        for (int dstIndex = 0; dstIndex < numFrames; ++dstIndex) {
            // 'mod' back into the waveTable
            if (srcPhase_ >= (float) waveTableSize_) {
                srcPhase_ -= (float) waveTableSize_;
            }

            // linear-interpolate
            int srcIndex = (int) srcPhase_;
            float delta0 = srcPhase_ - srcIndex;
            float delta1 = 1.0f - delta0;
            outBuff[dstIndex] = ((waveTable_[srcIndex] * delta1)
                    + (waveTable_[srcIndex + 1] * delta0)) / 2.0f;

            srcPhase_ += phaseIncr;
        }
    } else if (numChannels_ == 2) {
        // calculate wave values
        int dstIndex = 0;
        for (int frameIndex = 0; frameIndex < numFrames; frameIndex++) {
            // 'mod' back into the waveTable
            if (srcPhase_ >= (float) waveTableSize_) {
                srcPhase_ -= (float) waveTableSize_;
            }

            // linear-interpolate
            int srcIndex = (int) srcPhase_;
            float delta0 = srcPhase_ - srcIndex;
            float delta1 = 1.0f - delta0;
            float out = ((waveTable_[srcIndex] * delta1)
                    + (waveTable_[srcIndex + 1] * delta0)) / 2.0f;

            outBuff[dstIndex++] = out;
            outBuff[dstIndex++] = out;

            srcPhase_ += phaseIncr;
        }
    }

//	__android_log_print(ANDROID_LOG_INFO, TAG, "    %d samples", numSamples);
    return numFrames;
}

} // namespace ndkaudio
