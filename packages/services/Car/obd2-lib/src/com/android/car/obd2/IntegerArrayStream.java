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

package com.android.car.obd2;

import java.util.function.Function;

/**
 * A wrapper over an int[] that offers a moving offset into the array, allowing for sequential
 * consumption of the array data
 */
public class IntegerArrayStream {
    private final int[] mData;
    private int mIndex;

    public IntegerArrayStream(int[] data) {
        mData = data;
        mIndex = 0;
    }

    public int peek() {
        return mData[mIndex];
    }

    public int consume() {
        return mData[mIndex++];
    }

    public int residualLength() {
        return mData.length - mIndex;
    }

    public boolean isEmpty() {
        return residualLength() == 0;
    }

    public boolean hasAtLeast(int n) {
        return residualLength() >= n;
    }

    public <T> T hasAtLeast(int n, Function<IntegerArrayStream, T> ifTrue) {
        return hasAtLeast(n, ifTrue, null);
    }

    public <T> T hasAtLeast(
            int n,
            Function<IntegerArrayStream, T> ifTrue,
            Function<IntegerArrayStream, T> ifFalse) {
        if (hasAtLeast(n)) {
            return ifTrue.apply(this);
        } else {
            if (ifFalse != null) {
                return ifFalse.apply(this);
            } else {
                return null;
            }
        }
    }

    /**
     * Validates the content of this stream against an expected data-set.
     *
     * <p>If any element of values causes a mismatch, that element will not be consumed and this
     * method will return false. All elements that do match are consumed from the stream.
     *
     * <p>For instance, given a stream with {1,2,3,4}, a call of expect(1,2,5) will consume 1 and 2,
     * will return false, and stream.peek() will return 3 since it is the first element that did not
     * match and was not consumed.
     *
     * @param values The values to compare this stream's elements against.
     * @return true if all elements of values match this stream, false otherwise.
     */
    public boolean expect(int... values) {
        if (!hasAtLeast(values.length)) {
            return false;
        }
        for (int value : values) {
            if (value != peek()) {
                return false;
            } else {
                consume();
            }
        }
        return true;
    }
}
