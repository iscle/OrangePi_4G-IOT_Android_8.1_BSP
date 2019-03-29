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

package com.android.cts.verifier.audio.audiolib;

/**
 * A AudioFiller implementation for feeding data from a PCMFLOAT wavetable.
 */
public class WaveTableFloatFiller implements AudioFiller {
    @SuppressWarnings("unused")
    private static String TAG = "WaveTableFloatFiller";

    private float[] mWaveTbl = null;
    private int mNumWaveTblSamples = 0;
    private float mSrcPhase = 0.0f;

    private float mSampleRate = 48000;
    private float mFreq = 1000; // some arbitrary frequency
    private float mFN = 1.0f;   // The "nominal" frequency, essentially how much much of the
                                // wave table needs to be played to get one cycle at the
                                // sample rate. Used to calculate the phase increment

    public WaveTableFloatFiller(float[] waveTbl) {
        setWaveTable(waveTbl);
    }

    private void calcFN() {
        mFN = mSampleRate / (float)mNumWaveTblSamples;
    }

    public void setWaveTable(float[] waveTbl) {
        mWaveTbl = waveTbl;
        mNumWaveTblSamples = waveTbl != null ? mWaveTbl.length - 1 : 0;

        calcFN();
    }

    public void setSampleRate(float sampleRate) {
        mSampleRate = sampleRate;
        calcFN();
    }

    public void setFreq(float freq) {
        mFreq = freq;
    }

    @Override
    public void reset() {
        mSrcPhase = 0.0f;
    }

    public int fill(float[] buffer, int numFrames, int numChans) {
        final float phaseIncr = mFreq / mFN;
        int outIndex = 0;
        for (int frameIndex = 0; frameIndex < numFrames; frameIndex++) {
            // 'mod' back into the waveTable
            while (mSrcPhase >= (float)mNumWaveTblSamples) {
                mSrcPhase -= (float)mNumWaveTblSamples;
            }

            // linear-interpolate
            int srcIndex = (int)mSrcPhase;
            float delta0 = mSrcPhase - (float)srcIndex;
            float delta1 = 1.0f - delta0;
            float value = ((mWaveTbl[srcIndex] * delta0) + (mWaveTbl[srcIndex + 1] * delta1));

            for (int chanIndex = 0; chanIndex < numChans; chanIndex++) {
                buffer[outIndex++] = value;
            }

            mSrcPhase += phaseIncr;
        }

        return numFrames;
    }
}
