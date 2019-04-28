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

package com.android.documentsui.testing;

import static junit.framework.Assert.assertEquals;
import static junit.framework.Assert.assertTrue;

import android.os.Parcel;
import android.os.Parcelable;

import java.util.function.BiPredicate;

public class Parcelables {

    private Parcelables() {}

    public static <T extends Parcelable> void assertParcelable(T p, int flags) {
        final T restored = parcel(p, flags);

        assertEquals(p, restored);
    }

    public static <T extends Parcelable> void assertParcelable(
            T p, int flags, BiPredicate<T, T> pred) {
        T restored = parcel(p, flags);

        assertTrue(pred.test(p, restored));
    }

    private static <T extends Parcelable> T parcel(T p, int flags) {
        Parcel write = Parcel.obtain();
        Parcel read = Parcel.obtain();
        final T restored;
        try {
            write.writeParcelable(p, flags);
            final byte[] data = write.marshall();

            read.unmarshall(data, 0, data.length);
            read.setDataPosition(0);
            restored = read.readParcelable(p.getClass().getClassLoader());
        } finally {
            write.recycle();
            read.recycle();
        }

        return restored;
    }
}
