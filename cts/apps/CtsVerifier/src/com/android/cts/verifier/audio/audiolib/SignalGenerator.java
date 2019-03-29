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
 * Generates buffers of PCM data.
 */
public class SignalGenerator {
    @SuppressWarnings("unused")
    private static final String TAG = "SignalGenerator";

    /**
     * Fills a PCMFloat buffer with 1 cycle of a sine wave.
     * NOTE: The first and last (index 0 and size-1) are filled with the
     * sample value because WaveTableFloatFiller assumes this (to make the
     * interpolation calculation at the end of wavetable more efficient.
     */
    static public void fillFloatSine(float[] buffer) {
        int size = buffer.length;
        float incr = ((float)Math.PI  * 2.0f) / (float)(size - 1);
        for(int index = 0; index < size; index++) {
            buffer[index] = (float)Math.sin(index * incr);
        }
    }
}
