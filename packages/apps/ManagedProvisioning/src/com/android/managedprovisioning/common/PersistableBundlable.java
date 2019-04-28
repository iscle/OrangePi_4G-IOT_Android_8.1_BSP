/*
 * Copyright 2016, The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.android.managedprovisioning.common;

import android.os.Parcel;
import android.os.Parcelable;
import android.os.PersistableBundle;
import android.support.annotation.NonNull;
import android.text.TextUtils;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Set;

public abstract class PersistableBundlable implements Parcelable {
    public abstract @NonNull PersistableBundle toPersistableBundle();

    public static PersistableBundle getPersistableBundleFromParcel(Parcel parcel) {
        return parcel.readParcelable(PersistableBundle.class.getClassLoader());
    }

    @Override
    public boolean equals(Object object) {
        return isPersistableBundlableEquals(this, object);
    }

    @Override
    public int hashCode() {
        // Concatenated sorted keys should be good enough as a hash
        List<String> keys = new ArrayList(toPersistableBundle().keySet());
        Collections.sort(keys);
        return TextUtils.join(",", keys).hashCode();
    }

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeParcelable(toPersistableBundle(), flags);
    }

    private static boolean isPersistableBundlableEquals(PersistableBundlable pb1, Object obj) {
        if (pb1 == obj) {
            return true;
        }
        if (obj == null || pb1.getClass() != obj.getClass()) {
            return false;
        }

        // obj has to be PersistableBundlable as it has the same class
        PersistableBundlable pb2 = (PersistableBundlable) obj;
        return isPersistableBundleEquals(pb1.toPersistableBundle(), pb2.toPersistableBundle());
    }

    /**
     * Compares two {@link PersistableBundle} objects are equals.
     */
    private static boolean isPersistableBundleEquals(PersistableBundle obj1, PersistableBundle obj2) {
        if (obj1 == obj2) {
            return true;
        }
        if (obj1 == null || obj2 == null || obj1.size() != obj2.size()) {
            return false;
        }
        Set<String> keys = obj1.keySet();
        for (String key : keys) {
            Object val1 = obj1.get(key);
            Object val2 = obj2.get(key);
            if (!isPersistableBundleSupportedValueEquals(val1, val2)) {
                return false;
            }
        }
        return true;
    }

    /**
     * Compares two values which type is supported by {@link PersistableBundle}.
     *
     * <p>If the type isn't supported. The equality is done by {@link Object#equals(Object)}.
     */
    private static boolean isPersistableBundleSupportedValueEquals(Object val1, Object val2) {
        if (val1 == val2) {
            return true;
        } else if (val1 == null || val2 == null || !val1.getClass().equals(val2.getClass())) {
            return false;
        } else if (val1 instanceof PersistableBundle) {
            return isPersistableBundleEquals((PersistableBundle) val1, (PersistableBundle) val2);
        } else if (val1 instanceof int[]) {
            return Arrays.equals((int[]) val1, (int[]) val2);
        } else if (val1 instanceof long[]) {
            return Arrays.equals((long[]) val1, (long[]) val2);
        } else if (val1 instanceof double[]) {
            return Arrays.equals((double[]) val1, (double[]) val2);
        } else if (val1 instanceof boolean[]) {
            return Arrays.equals((boolean[]) val1, (boolean[]) val2);
        } else if (val1 instanceof String[]) {
            return Arrays.equals((String[]) val1, (String[]) val2);
        } else {
            return Objects.equals(val1, val2);
        }
    }

}
