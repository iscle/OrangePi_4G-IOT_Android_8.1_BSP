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

public class DspFftServer {
    private int mN = 0;
    private int mOrder = 0;

    DspBufferDouble mCos;
    DspBufferDouble mSin;
    public boolean isInitialized = false;

    public DspFftServer(int size) {
        init(size);
    }

    public boolean init(int size) {
        boolean status = false;
        mN=size;

        mOrder = (int) (Math.log(mN) / Math.log(2));
        if (mN == (1 << mOrder)) {
            mCos = new DspBufferDouble(mN / 2);
            mSin = new DspBufferDouble(mN / 2);
            for (int i = 0; i < mN / 2; i++) {
                mCos.mData[i] = Math.cos(-2 * Math.PI * i / mN);
                mSin.mData[i] = Math.sin(-2 * Math.PI * i / mN);
            }
            status = true;
        } else {
            mN = 0;
            throw new RuntimeException("FFT must be power of 2");
        }
        isInitialized = status;
        return status;
    }

    public void fft(DspBufferComplex r, int sign) {
        int ii, jj, kk, n1, n2, aa;
        double cc, ss, t1, t2;

        // Bit-reverse
        jj = 0;
        n2 = mN / 2;
        for (ii = 1; ii < mN - 1; ii++) {
            n1 = n2;
            while (jj >= n1) {
                jj = jj - n1;
                n1 = n1 / 2;
            }
            jj = jj + n1;

            if (ii < jj) {
                t1 =  r.mReal[ii];
                r.mReal[ii] = r.mReal[jj];
                r.mReal[jj] = t1;
                t1 = r.mImag[ii];
                r.mImag[ii] = r.mImag[jj];
                r.mImag[jj] = t1;
            }
        }

        // FFT
        n1 = 0;
        n2 = 1;
        for (ii = 0; ii < mOrder; ii++) {
            n1 = n2;
            n2 = n2 + n2;
            aa = 0;

            for (jj = 0; jj < n1; jj++) {
                cc = mCos.mData[aa];
                ss = sign * mSin.mData[aa];
                aa += 1 << (mOrder - ii - 1);
                for (kk = jj; kk < mN; kk = kk + n2) {
                    t1 = cc * r.mReal[kk + n1] - ss * r.mImag[kk + n1];
                    t2 = ss * r.mReal[kk + n1] + cc * r.mImag[kk + n1];
                    r.mReal[kk + n1] = r.mReal[kk] - t1;
                    r.mImag[kk + n1] = r.mImag[kk] - t2;
                    r.mReal[kk] = r.mReal[kk] + t1;
                    r.mImag[kk] = r.mImag[kk] + t2;
                }
            }
        }
    }
}
