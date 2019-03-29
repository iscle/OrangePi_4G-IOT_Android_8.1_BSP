/*
 * Copyright (C) 2015 The Android Open Source Project
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

package com.android.cts.verifier.audio;

public class AudioBandSpecs {
    double mFreqStart;
    double mFreqStop;
    double mRippleStartTop;
    double mRippleStartBottom;

    double mRippleStopTop;
    double mRippleStopBottom;

    double mOffset;

    public AudioBandSpecs(double fStart, double fStop, double startTop, double startBottom,
            double stopTop, double stopBottom) {
        initFreq(fStart, fStop);
        initRipple(startTop, startBottom, stopTop, stopBottom);
        setOffset(0);
    }

    public void initRipple(double startTop, double startBottom, double stopTop, double stopBottom) {
        mRippleStartTop = startTop;
        mRippleStartBottom = startBottom;
        mRippleStopTop = stopTop;
        mRippleStopBottom = stopBottom;
        // note: top should be >= bottom, but no check is done here.
    }

    public void initFreq(double fStart, double fStop) {
        mFreqStart = fStart;
        mFreqStop = fStop;
    }

    public void setOffset(double offset) {
        mOffset = offset;
    }

    /**
     * Check if the given point is in bounds in this band.
     */
    public boolean isInBounds(double freq, double value) {
        if (freq < mFreqStart || freq > mFreqStop) {
            return false;
        }

        double d = mFreqStop - mFreqStart;
        if (d <= 0) {
            return false;
        }

        double e = freq - mFreqStart;
        double vTop = (e / d) * (mRippleStopTop - mRippleStartTop) + mRippleStartTop + mOffset;
        if (value > vTop) {
            return false;
        }

        double vBottom = (e / d) * (mRippleStopBottom - mRippleStartBottom) + mRippleStartBottom
                + mOffset;

        if (value < vBottom) {
            return false;
        }
        return true;
    }

    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append(String.format("Freq %.1f - %.1f |", mFreqStart, mFreqStop));
        sb.append(String.format("start [%.1f : %.1f] |", mRippleStartTop, mRippleStartBottom));
        sb.append(String.format("stop  [%.1f : %.1f] |", mRippleStopTop, mRippleStopBottom));
        sb.append(String.format("offset %.1f", mOffset));
        return sb.toString();
    }
}