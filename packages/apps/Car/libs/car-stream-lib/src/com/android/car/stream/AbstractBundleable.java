/**
 * Copyright (c) 2016, The Android Open Source Project
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
package com.android.car.stream;

import android.os.Bundle;
import android.os.Parcel;
import android.os.Parcelable;
import android.support.annotation.VisibleForTesting;
import android.util.Log;

import java.lang.reflect.Array;

/**
 * Base class for Parcelable classes that serialize/deserialize themselves using Bundles for
 * backward compatibility.
 *
 * <p>
 * By using Bundles which require explicit key-value pairs, unknown fields can be handled
 * gracefully. Also, by ensuring that custom classes are serialized to a Bundle containing
 * primitives or system classes only, ClassNotFoundExceptions can be prevented when deserializing.
 * </p>
 *
 * <p>
 * Subclass must expose a default constructor, implement {@link #writeToBundle(Bundle)} and
 * {@link #readFromBundle(Bundle)} instead of {@link #writeToParcel(Parcel, int)}.
 * It should also define a CREATOR, as required of all Parcelables, by instantiating a
 * {@link BundleableCreator}.
 *
 * Example:
 *
 * public static final Creator<MyClass> CREATOR = new BundleableCreator<>(MyClass.class);
 *
 * @Override
 * protected void writeToBundle(Bundle bundle) {
 *     bundle.putInt(FIRST_FIELD_KEY, mFirstField);
 *     if (mCustomField != null) {
 *         Bundle customFieldBundle = new Bundle();
 *         mCustomField.writeToBundle(customFieldBundle);
 *         bundle.putBundle(CUSTOM_FIELD_KEY, customFieldBundle);
 *     }
 *     bundle.putParcelable(INTENT_KEY, mIntent);
 * }
 *
 * @Override
 * protected void readFromBundle(Bundle bundle) {
 *     mFirstField = bundle.getInt(FIRST_FIELD_KEY);
 *     Bundle customFieldBundle = bundle.getBundle(CUSTOM_FIELD_KEY);
 *     if (customFieldBundle != null) {
 *         mCustomField = new CustomClass();
 *         mCustomField.readFromBundle(customFieldBundle);
 *     }
 *     mIntent = bundle.getParcelable(INTENT_KEY);
 * }
 * </p>
 *
 * <p>
 * All subclasses should be added to BundleableTest#BUNDLEABLE_CLASSES list to be tested.
 * </p>
 */
public abstract class AbstractBundleable implements Parcelable {
    private static final String TAG = "Bundleable";

    /**
     * Creator class for unmarshalling subclasses of {@link AbstractBundleable}.
     */
    @VisibleForTesting
    public static class BundleableCreator<T extends AbstractBundleable>
            implements Creator<T> {
        private Class<T> clazz;

        public BundleableCreator(Class<T> bundleableClazz) {
            clazz = bundleableClazz;
        }

        @Override
        public final T createFromParcel(Parcel source) {
            T instance = null;
            try {
                instance = clazz.newInstance();
                instance.readFromBundle(source.readBundle());
            } catch (Exception e) {
                Log.e(TAG, "Failed to instantiate " + clazz.getSimpleName(), e);
            }
            return instance;
        }

        @SuppressWarnings("unchecked")
        @Override
        public final T[] newArray(int size) {
            return (T[]) Array.newInstance(clazz, size);
        }
    }

    @Override
    public final int describeContents() {
        return 0;
    }

    @Override
    public final void writeToParcel(Parcel dest, int flags) {
        Bundle bundle = new Bundle();
        writeToBundle(bundle);
        dest.writeBundle(bundle);
    }

    @Override
    public String toString() {
        Bundle bundle = new Bundle();
        writeToBundle(bundle);
        return bundle.toString();
    }

    /**
     * Writes the states of the instance to the given Bundle. Only primitives or system classes
     * can be written into the Bundle. If a field of a custom class needs to be serialized,
     * serialize it into a new Bundle, and then write that Bundle into the outer Bundle. A list or
     * array of custom class instances should similarly be converted into an array of Bundles first.
     */
    protected abstract void writeToBundle(Bundle bundle);

    /**
     * Reads the states saved in the Bundle into the current instance. The implementation should
     * mirror that of {@link #writeToBundle(Bundle)}.
     */
    protected abstract void readFromBundle(Bundle bundle);
}