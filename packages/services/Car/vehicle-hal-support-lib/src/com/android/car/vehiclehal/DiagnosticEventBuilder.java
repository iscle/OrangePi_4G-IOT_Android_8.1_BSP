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

import android.hardware.automotive.vehicle.V2_0.VehiclePropConfig;
import android.hardware.automotive.vehicle.V2_0.VehiclePropValue;
import android.hardware.automotive.vehicle.V2_0.DiagnosticFloatSensorIndex;
import android.hardware.automotive.vehicle.V2_0.DiagnosticIntegerSensorIndex;
import android.util.SparseArray;
import java.util.BitSet;
import java.util.Iterator;

/**
 * A builder class for a VehiclePropValue that encapsulates a diagnostic event. This is the Java
 * equivalent of Obd2SensorStore.cpp in the native layer.
 *
 * @hide
 */
public class DiagnosticEventBuilder {
    /**
     * An array-like container that knows to return a default value for any unwritten-to index.
     *
     * @param <T> the element type
     */
    class DefaultedArray<T> implements Iterable<T> {
        private final SparseArray<T> mElements = new SparseArray<>();
        private final int mSize;
        private final T mDefaultValue;

        DefaultedArray(int size, T defaultValue) {
            mSize = size;
            mDefaultValue = defaultValue;
        }

        private int checkIndex(int index) {
            if (index < 0 || index >= mSize)
                throw new IndexOutOfBoundsException(
                        String.format("Index: %d, Size: %d", index, mSize));
            return index;
        }

        DefaultedArray<T> set(int index, T element) {
            checkIndex(index);
            mElements.put(index, element);
            return this;
        }

        T get(int index) {
            checkIndex(index);
            return mElements.get(index, mDefaultValue);
        }

        int size() {
            return mSize;
        }

        void clear() {
            mElements.clear();
        }

        @Override
        public Iterator<T> iterator() {
            return new Iterator<T>() {
                private int mIndex = 0;

                @Override
                public boolean hasNext() {
                    return (mIndex >= 0) && (mIndex < mSize);
                }

                @Override
                public T next() {
                    int index = mIndex++;
                    return get(index);
                }
            };
        }
    }

    private final int mPropertyId;
    private final int mNumIntSensors;
    private final DefaultedArray<Integer> mIntValues;
    private final DefaultedArray<Float> mFloatValues;
    private final BitSet mBitmask;
    private String mDtc = null;

    public DiagnosticEventBuilder(VehiclePropConfig propConfig) {
        this(propConfig.prop, propConfig.configArray.get(0), propConfig.configArray.get(1));
    }

    public DiagnosticEventBuilder(int propertyId) {
        this(propertyId, 0, 0);
    }

    public DiagnosticEventBuilder(
            int propertyId, int numVendorIntSensors, int numVendorFloatSensors) {
        mPropertyId = propertyId;
        mNumIntSensors = DiagnosticIntegerSensorIndex.LAST_SYSTEM_INDEX + 1 + numVendorIntSensors;
        final int numFloatSensors =
                DiagnosticFloatSensorIndex.LAST_SYSTEM_INDEX + 1 + numVendorFloatSensors;
        mBitmask = new BitSet(mNumIntSensors + numFloatSensors);
        mIntValues = new DefaultedArray<>(mNumIntSensors, 0);
        mFloatValues = new DefaultedArray<>(numFloatSensors, 0.0f);
    }

    public DiagnosticEventBuilder clear() {
        mIntValues.clear();
        mFloatValues.clear();
        mBitmask.clear();
        mDtc = null;
        return this;
    }

    public DiagnosticEventBuilder addIntSensor(int index, int value) {
        mIntValues.set(index, value);
        mBitmask.set(index);
        return this;
    }

    public DiagnosticEventBuilder addFloatSensor(int index, float value) {
        mFloatValues.set(index, value);
        mBitmask.set(mNumIntSensors + index);
        return this;
    }

    public DiagnosticEventBuilder setDTC(String dtc) {
        mDtc = dtc;
        return this;
    }

    public VehiclePropValue build() {
        return build(0);
    }

    public VehiclePropValue build(long timestamp) {
        VehiclePropValueBuilder propValueBuilder = VehiclePropValueBuilder.newBuilder(mPropertyId);
        if (0 == timestamp) {
            propValueBuilder.setTimestamp();
        } else {
            propValueBuilder.setTimestamp(timestamp);
        }
        mIntValues.forEach(propValueBuilder::addIntValue);
        mFloatValues.forEach(propValueBuilder::addFloatValue);
        return propValueBuilder.addByteValue(mBitmask.toByteArray()).setStringValue(mDtc).build();
    }
}
