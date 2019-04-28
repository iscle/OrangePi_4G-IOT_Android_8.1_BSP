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

package com.android.car.vehiclehal;

import android.util.SparseArray;
import java.util.Iterator;

class Utils {
    private Utils() {}

    static class SparseArrayIterator<T>
            implements Iterable<SparseArrayIterator.SparseArrayEntry<T>>,
                Iterator<SparseArrayIterator.SparseArrayEntry<T>> {
        static class SparseArrayEntry<U> {
            public final int key;
            public final U value;

            SparseArrayEntry(SparseArray<U> array, int index) {
                key = array.keyAt(index);
                value = array.valueAt(index);
            }
        }

        private final SparseArray<T> mArray;
        private int mIndex = 0;

        SparseArrayIterator(SparseArray<T> array) {
            mArray = array;
        }

        @Override
        public Iterator<SparseArrayEntry<T>> iterator() {
            return this;
        }

        @Override
        public boolean hasNext() {
            return mIndex < mArray.size();
        }

        @Override
        public SparseArrayEntry<T> next() {
            return new SparseArrayEntry<>(mArray, mIndex++);
        }
    }
}
