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
 * limitations under the License
 */

package com.android.tv.util;

import android.support.annotation.VisibleForTesting;
import android.util.ArraySet;
import android.util.LongSparseArray;

import java.util.Collections;
import java.util.Set;

/**
 * Uses a {@link LongSparseArray} to hold sets of {@code T}.
 *
 * <p>This has the same memory and performance trade offs listed in {@link LongSparseArray}.
 */
public class MultiLongSparseArray<T> {
    @VisibleForTesting
    static final int DEFAULT_MAX_EMPTIES_KEPT = 4;
    private final LongSparseArray<Set<T>> mSparseArray;
    private final Set<T>[] mEmptySets;
    private int mEmptyIndex = -1;

    public MultiLongSparseArray() {
        mSparseArray = new LongSparseArray<>();
        mEmptySets = new Set[DEFAULT_MAX_EMPTIES_KEPT];
    }

    public MultiLongSparseArray(int initialCapacity, int emptyCacheSize) {
        mSparseArray = new LongSparseArray<>(initialCapacity);
        mEmptySets = new Set[emptyCacheSize];
    }

    /**
     * Adds a mapping from the specified key to the specified value,
     * replacing the previous mapping from the specified key if there
     * was one.
     */
    public void put(long key, T value) {
        Set<T> values = mSparseArray.get(key);
        if (values == null) {
            values = getEmptySet();
            mSparseArray.put(key, values);
        }
        values.add(value);
    }

    /**
     * Removes the value at the specified index.
     */
    public void remove(long key, T value) {
        Set<T> values = mSparseArray.get(key);
        if (values != null) {
            values.remove(value);
            if (values.isEmpty()) {
                mSparseArray.remove(key);
                cacheEmptySet(values);
            }
        }
    }

    /**
     * Gets the set of Objects mapped from the specified key, or an empty set
     * if no such mapping has been made.
     */
    public Iterable<T> get(long key) {
        Set<T> values = mSparseArray.get(key);
        return values == null ? Collections.EMPTY_SET : values;
    }

    /**
     * Clears cached empty sets.
     */
    public void clearEmptyCache() {
        while (mEmptyIndex >= 0) {
            mEmptySets[mEmptyIndex--] = null;
        }
    }

    @VisibleForTesting
    int getEmptyCacheSize() {
        return mEmptyIndex + 1;
    }

    private void cacheEmptySet(Set<T> emptySet) {
        if (mEmptyIndex < DEFAULT_MAX_EMPTIES_KEPT - 1) {
            mEmptySets[++mEmptyIndex] = emptySet;
        }
    }

    private Set<T> getEmptySet() {
        if (mEmptyIndex < 0) {
            return new ArraySet<>();
        }
        Set<T> emptySet = mEmptySets[mEmptyIndex];
        mEmptySets[mEmptyIndex--] = null;
        return emptySet;
    }

    @Override
    public String toString() {
        return mSparseArray.toString() + "(emptyCacheSize=" + getEmptyCacheSize() + ")";
    }
}
