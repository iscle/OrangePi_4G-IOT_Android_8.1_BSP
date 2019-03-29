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

package com.android.cts.verifier.audio.peripheralprofile;

public class ListsHelper {
    static public boolean isMatch(int[] a, int[] b) {
        if (a.length != b.length) {
            return false;
        }

        int len = a.length;
        for (int index = 0; index < len; index++) {
            if (a[index] != b[index]) {
                return false;
            }
        }

        return true;
    }

    static private boolean hasValue(int[] a, int value) {
        // one can't use indexOf on an int[], so just scan.
        for (int aVal : a) {
            if (aVal == value) {
                return true;
            }
        }

        return false;
    }

    /**
     * return true if the values in "a" are a subset of those in "b".
     * Assume values are represented no more than once in each array.
     */
    static public boolean isSubset(int[] a, int[] b) {
        if (a.length > b.length) {
            return false;
        }

        for (int aVal : a) {
            if (!hasValue(b, aVal)) {
                return false;
            }
        }

        return true;
    }
}
