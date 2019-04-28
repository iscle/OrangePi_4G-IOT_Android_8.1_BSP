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
package com.android.car.hal;

import android.util.SparseIntArray;

/**
 * Helper class that maintains bi-directional mapping between int values.
 *
 * <p>This class is immutable. Use {@link #create(int[])} factory method to instantiate this class.
 */
class BidirectionalSparseIntArray {
    private final SparseIntArray mMap;
    private final SparseIntArray mInverseMap;

    /**
     * Creates {@link BidirectionalSparseIntArray} for provided int pairs.
     *
     * <p> The input array should have an even number of elements.
     */
    static BidirectionalSparseIntArray create(int[] keyValuePairs) {
        int inputLength = keyValuePairs.length;
        if (inputLength % 2 != 0) {
            throw new IllegalArgumentException("Odd number of key-value elements");
        }

        BidirectionalSparseIntArray biMap = new BidirectionalSparseIntArray(inputLength / 2);
        for (int i = 0; i < keyValuePairs.length; i += 2) {
            biMap.put(keyValuePairs[i], keyValuePairs[i + 1]);
        }
        return biMap;
    }

    private BidirectionalSparseIntArray(int initialCapacity) {
        mMap = new SparseIntArray(initialCapacity);
        mInverseMap = new SparseIntArray(initialCapacity);
    }

    private void put(int key, int value) {
        mMap.put(key, value);
        mInverseMap.put(value, key);
    }

    int getValue(int key, int defaultValue) {
        return mMap.get(key, defaultValue);
    }

    int getKey(int value, int defaultKey) {
        return mInverseMap.get(value, defaultKey);
    }
}
