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

public abstract class DspBufferBase {
    private int mSize;

    public DspBufferBase() {
        setSize(0);
    }

    public DspBufferBase(int size) {
        setSize(size);
    }

    public int getSize() {
        return mSize;
    }

    public void setSize(int size) {
        mSize = size;
    }

    public abstract void setValue(int index, double value);

    public abstract void setValues(int index, double... values);

    @Override
    public String toString() {
        return String.format("Size: %d", mSize);
    }
}
