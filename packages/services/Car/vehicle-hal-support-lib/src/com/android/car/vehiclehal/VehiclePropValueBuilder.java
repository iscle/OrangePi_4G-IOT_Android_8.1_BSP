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

package com.android.car.vehiclehal;

import android.annotation.CheckResult;
import android.hardware.automotive.vehicle.V2_0.VehiclePropValue;
import android.os.SystemClock;

/** A builder class for {@link android.hardware.automotive.vehicle.V2_0.VehiclePropValue} */
public class VehiclePropValueBuilder {
    private final VehiclePropValue mPropValue;

    public static VehiclePropValueBuilder newBuilder(int propId) {
        return new VehiclePropValueBuilder(propId);
    }

    public static VehiclePropValueBuilder newBuilder(VehiclePropValue propValue) {
        return new VehiclePropValueBuilder(propValue);
    }

    private VehiclePropValueBuilder(int propId) {
        mPropValue = new VehiclePropValue();
        mPropValue.prop = propId;
    }

    private VehiclePropValueBuilder(VehiclePropValue propValue) {
        mPropValue = clone(propValue);
    }

    private VehiclePropValue clone(VehiclePropValue propValue) {
        VehiclePropValue newValue = new VehiclePropValue();

        newValue.prop = propValue.prop;
        newValue.areaId = propValue.areaId;
        newValue.timestamp = propValue.timestamp;
        newValue.value.stringValue = propValue.value.stringValue;
        newValue.value.int32Values.addAll(propValue.value.int32Values);
        newValue.value.floatValues.addAll(propValue.value.floatValues);
        newValue.value.int64Values.addAll(propValue.value.int64Values);
        newValue.value.bytes.addAll(propValue.value.bytes);

        return newValue;
    }

    @CheckResult
    public VehiclePropValueBuilder setAreaId(int areaId) {
        mPropValue.areaId = areaId;
        return this;
    }

    @CheckResult
    public VehiclePropValueBuilder setTimestamp(long timestamp) {
        mPropValue.timestamp = timestamp;
        return this;
    }

    @CheckResult
    public VehiclePropValueBuilder setTimestamp() {
        mPropValue.timestamp = SystemClock.elapsedRealtimeNanos();
        return this;
    }

    @CheckResult
    public VehiclePropValueBuilder addIntValue(int... values) {
        for (int val : values) {
            mPropValue.value.int32Values.add(val);
        }
        return this;
    }

    @CheckResult
    public VehiclePropValueBuilder addFloatValue(float... values) {
        for (float val : values) {
            mPropValue.value.floatValues.add(val);
        }
        return this;
    }

    @CheckResult
    public VehiclePropValueBuilder addByteValue(byte... values) {
        for (byte val : values) {
            mPropValue.value.bytes.add(val);
        }
        return this;
    }

    @CheckResult
    public VehiclePropValueBuilder setInt64Value(long... values) {
        for (long val : values) {
            mPropValue.value.int64Values.add(val);
        }
        return this;
    }

    @CheckResult
    public VehiclePropValueBuilder setBooleanValue(boolean value) {
        mPropValue.value.int32Values.clear();
        mPropValue.value.int32Values.add(value ? 1 : 0);
        return this;
    }

    @CheckResult
    public VehiclePropValueBuilder setStringValue(String val) {
        mPropValue.value.stringValue = val;
        return this;
    }

    public VehiclePropValue build() {
        return clone(mPropValue);
    }
}
