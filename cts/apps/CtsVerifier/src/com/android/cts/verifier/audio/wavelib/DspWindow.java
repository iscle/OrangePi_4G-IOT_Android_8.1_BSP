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

package com.android.cts.verifier.audio.wavelib;

public class DspWindow {
    public DspBufferDouble mBuffer;
    private int mWindowType = WINDOW_RECTANGULAR;
    private int mSize;
    private int mOverlap;

    private static final double TWOPI = Math.PI * 2;

    public static final int WINDOW_RECTANGULAR = 0;
    public static final int WINDOW_TRIANGULAR = 1;
    public static final int WINDOW_TRIANGULAR_FLAT_TOP = 2;
    public static final int WINDOW_HAMMING = 3;
    public static final int WINDOW_HAMMING_FLAT_TOP = 4;
    public static final int WINDOW_HANNING = 5;
    public static final int WINDOW_HANNING_FLAT_TOP = 6;

    public DspWindow(int windowType, int size, int overlap) {
        init(windowType, size, overlap);
    }

    public DspWindow(int windowType, int size)  {
        init(windowType, size, size / 2);
    }

    public void init(int windowType, int size, int overlap) {
        if (size > 0 && overlap > 0) {
            mSize = size;
            mOverlap = overlap;
            if (mOverlap > mSize / 2) {
                mOverlap = mSize / 2;
            }

            mBuffer = new DspBufferDouble(mSize);
            if (fillWindow(mBuffer, windowType, mOverlap)) {
                mWindowType = windowType;
            }
        }
    }

    public void scale(double scale) {
        DspBufferMath.mult(mBuffer, mBuffer, scale);
    }

    public static boolean fillWindow(DspBufferDouble r, int type, int overlap) {
        boolean status = false;
        int size = r.getSize();
        if (overlap > size / 2) {
            overlap = size / 2;
        }

        switch(type) {
            case WINDOW_RECTANGULAR:
                status = fillRectangular(r);
                break;
            case WINDOW_TRIANGULAR:
                status = fillTriangular(r, size / 2);
                break;
            case WINDOW_TRIANGULAR_FLAT_TOP:
                status = fillTriangular(r, overlap);
                break;
            case WINDOW_HAMMING:
                status = fillHamming(r, size / 2);
                break;
            case WINDOW_HAMMING_FLAT_TOP:
                status = fillHamming(r, overlap);
                break;
            case WINDOW_HANNING:
                status = fillHanning(r, size / 2);
                break;
            case WINDOW_HANNING_FLAT_TOP:
                status = fillHanning(r, overlap);
                break;
        }
        return status;
    }

    private static boolean fillRectangular(DspBufferDouble r) {
        if (DspBufferMath.set(r, 1.0) == DspBufferMath.MATH_RESULT_SUCCESS) {
            return true;
        }
        return false;
    }

    private static boolean fillTriangular(DspBufferDouble b, int overlap) {
        int size = b.getSize();
        if (overlap > size / 2) {
            overlap = size / 2;
        }

        double value;
        //ramp up
        int i = 0;
        if (overlap > 0) {
            for (i = 0; i < overlap; i++) {
                value = (2.0 * i + 1) / (2 * overlap);
                b.mData[i] = value;
            }
        }

        //flat top
        for (; i < size - overlap; i++) {
            b.mData[i] = 1.0;
        }

        //ramp down
        if (overlap > 0) {
            for (; i < size; i++) {
                value = (2.0 * (size - i) - 1) / (2 * overlap);
                b.mData[i] = value;
            }
        }
        return true;
    }

    private static boolean fillHamming(DspBufferDouble b, int overlap) {
        int size = b.getSize();
        if (overlap > size / 2)
            overlap = size / 2;

        //create window, then copy
        double value;

        int twoOverlap = 2 * overlap;
        //ramp up
        int i = 0;
        if (overlap > 0) {
            for (i = 0; i < overlap; i++) {
                value = 0.54 - 0.46 * Math.cos(TWOPI * i / (twoOverlap - 1));
                b.mData[i] = value;
            }
        }

        //flat top
        for (; i < size - overlap; i++) {
            b.mData[i] = 1.0;
        }

        //ramp down
        int k;
        if (overlap > 0) {
            for (; i < size; i++) {
                k = i - (size - 2 * overlap);
                value = 0.54 - 0.46 * Math.cos(TWOPI * k / (twoOverlap - 1));
                b.mData[i] = value;
            }
        }
        return true;
    }

    private static boolean fillHanning(DspBufferDouble b, int overlap) {
        int size = b.getSize();
        if (overlap > size / 2)
            overlap = size / 2;

        //create window, then copy
        double value;

        int twoOverlap = 2*overlap;
        //ramp up
        int i = 0;
        if (overlap > 0) {
            for (i = 0; i < overlap; i++) {
                value = 0.5 * (1.0 - Math.cos(TWOPI * i / (twoOverlap - 1)));
                b.mData[i] = value;
            }
        }

        //flat top
        for (; i < size - overlap; i++) {
            b.mData[i] = 1.0;
        }

        //ramp down
        if (overlap > 0) {
            for (; i < size; i++) {
                int k = i - (size - 2 * overlap);
                value = 0.5 * (1.0 - Math.cos(TWOPI * k / (twoOverlap - 1)));
                b.mData[i] = value;
            }
        }
        return true;
    }
}
