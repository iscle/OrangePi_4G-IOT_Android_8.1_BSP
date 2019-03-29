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

public class DspBufferComplex extends DspBufferBase {
    public double[] mReal;
    public double[] mImag;

    public DspBufferComplex(int size) {
        super(size);
    }

    @Override
    public void setSize(int size) {
        if (size == getSize()) {
            return;
        }
        mReal = new double[size];
        mImag = new double[size];
        super.setSize(size);
    }

    // Warning, these methods don't check for bounds!
    @Override
    public void setValues(int index, double... values) {
        mReal[index] = values[0];
        mImag[index] = values[1];
    }

    @Override
    public void setValue(int index, double value) {
        mReal[index] = value;
        mImag[index] = 0;
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        int size = getSize();
        sb.append(String.format("Size: %d { ", size));
        for (int i = 0; i < size; i++) {
            sb.append(String.format("(%.3f, %3f) ", mReal[i], mImag[i]));
        }
        sb.append("}");
        return sb.toString();
    }
}
