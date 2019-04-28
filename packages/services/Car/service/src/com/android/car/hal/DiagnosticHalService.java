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

package com.android.car.hal;

import android.annotation.Nullable;
import android.car.diagnostic.CarDiagnosticEvent;
import android.car.diagnostic.CarDiagnosticManager;
import android.car.hardware.CarSensorManager;
import android.hardware.automotive.vehicle.V2_0.VehiclePropConfig;
import android.hardware.automotive.vehicle.V2_0.VehiclePropValue;
import android.hardware.automotive.vehicle.V2_0.VehiclePropertyChangeMode;
import android.hardware.automotive.vehicle.V2_0.DiagnosticFloatSensorIndex;
import android.hardware.automotive.vehicle.V2_0.DiagnosticIntegerSensorIndex;
import android.hardware.automotive.vehicle.V2_0.VehicleProperty;
import android.util.Log;
import android.util.SparseArray;
import com.android.car.CarLog;
import com.android.car.CarServiceUtils;
import com.android.car.vehiclehal.VehiclePropValueBuilder;
import java.io.PrintWriter;
import java.util.BitSet;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArraySet;

/**
 * Diagnostic HAL service supporting gathering diagnostic info from VHAL and translating it into
 * higher-level semantic information
 */
public class DiagnosticHalService extends SensorHalServiceBase {
    public static class DiagnosticCapabilities {
        private final CopyOnWriteArraySet<Integer> mProperties = new CopyOnWriteArraySet<>();

        void setSupported(int propertyId) {
            mProperties.add(propertyId);
        }

        boolean isSupported(int propertyId) {
            return mProperties.contains(propertyId);
        }

        public boolean isLiveFrameSupported() {
            return isSupported(VehicleProperty.OBD2_LIVE_FRAME);
        }

        public boolean isFreezeFrameSupported() {
            return isSupported(VehicleProperty.OBD2_FREEZE_FRAME);
        }

        public boolean isFreezeFrameInfoSupported() {
            return isSupported(VehicleProperty.OBD2_FREEZE_FRAME_INFO);
        }

        public boolean isFreezeFrameClearSupported() {
            return isSupported(VehicleProperty.OBD2_FREEZE_FRAME_CLEAR);
        }

        void clear() {
            mProperties.clear();
        }
    }

    private final DiagnosticCapabilities mDiagnosticCapabilities = new DiagnosticCapabilities();
    private DiagnosticListener mDiagnosticListener;
    protected final SparseArray<VehiclePropConfig> mVehiclePropertyToConfig = new SparseArray<>();

    public DiagnosticHalService(VehicleHal hal) {
        super(hal);
    }

    @Override
    protected int getTokenForProperty(VehiclePropConfig propConfig) {
        switch (propConfig.prop) {
            case VehicleProperty.OBD2_LIVE_FRAME:
                mDiagnosticCapabilities.setSupported(propConfig.prop);
                mVehiclePropertyToConfig.put(propConfig.prop, propConfig);
                Log.i(CarLog.TAG_DIAGNOSTIC, String.format("configArray for OBD2_LIVE_FRAME is %s",
                    propConfig.configArray));
                return CarDiagnosticManager.FRAME_TYPE_LIVE;
            case VehicleProperty.OBD2_FREEZE_FRAME:
                mDiagnosticCapabilities.setSupported(propConfig.prop);
                mVehiclePropertyToConfig.put(propConfig.prop, propConfig);
                Log.i(CarLog.TAG_DIAGNOSTIC, String.format("configArray for OBD2_FREEZE_FRAME is %s",
                    propConfig.configArray));
                return CarDiagnosticManager.FRAME_TYPE_FREEZE;
            case VehicleProperty.OBD2_FREEZE_FRAME_INFO:
                mDiagnosticCapabilities.setSupported(propConfig.prop);
                return propConfig.prop;
            case VehicleProperty.OBD2_FREEZE_FRAME_CLEAR:
                mDiagnosticCapabilities.setSupported(propConfig.prop);
                return propConfig.prop;
            default:
                return SENSOR_TYPE_INVALID;
        }
    }

    @Override
    public synchronized void release() {
        super.release();
        mDiagnosticCapabilities.clear();
    }

    private VehiclePropConfig getPropConfig(int halPropId) {
        return mVehiclePropertyToConfig.get(halPropId, null);
    }

    private List<Integer> getPropConfigArray(int halPropId) {
        VehiclePropConfig propConfig = getPropConfig(halPropId);
        return propConfig.configArray;
    }

    private int getNumIntegerSensors(int halPropId) {
        int count = DiagnosticIntegerSensorIndex.LAST_SYSTEM_INDEX + 1;
        List<Integer> configArray = getPropConfigArray(halPropId);
        if(configArray.size() < 2) {
            Log.e(CarLog.TAG_DIAGNOSTIC, String.format(
                    "property 0x%x does not specify the number of vendor-specific properties." +
                            "assuming 0.", halPropId));
        }
        else {
            count += configArray.get(0);
        }
        return count;
    }

    private int getNumFloatSensors(int halPropId) {
        int count = DiagnosticFloatSensorIndex.LAST_SYSTEM_INDEX + 1;
        List<Integer> configArray = getPropConfigArray(halPropId);
        if(configArray.size() < 2) {
            Log.e(CarLog.TAG_DIAGNOSTIC, String.format(
                "property 0x%x does not specify the number of vendor-specific properties." +
                    "assuming 0.", halPropId));
        }
        else {
            count += configArray.get(1);
        }
        return count;
    }

    private CarDiagnosticEvent createCarDiagnosticEvent(VehiclePropValue value) {
        if (null == value)
            return null;

        final boolean isFreezeFrame = value.prop == VehicleProperty.OBD2_FREEZE_FRAME;

        CarDiagnosticEvent.Builder builder =
                (isFreezeFrame
                                ? CarDiagnosticEvent.Builder.newFreezeFrameBuilder()
                                : CarDiagnosticEvent.Builder.newLiveFrameBuilder())
                        .atTimestamp(value.timestamp);

        BitSet bitset = BitSet.valueOf(CarServiceUtils.toByteArray(value.value.bytes));

        int numIntegerProperties = getNumIntegerSensors(value.prop);
        int numFloatProperties = getNumFloatSensors(value.prop);

        for (int i = 0; i < numIntegerProperties; ++i) {
            if (bitset.get(i)) {
                builder.withIntValue(i, value.value.int32Values.get(i));
            }
        }

        for (int i = 0; i < numFloatProperties; ++i) {
            if (bitset.get(numIntegerProperties + i)) {
                builder.withFloatValue(i, value.value.floatValues.get(i));
            }
        }

        builder.withDtc(value.value.stringValue);

        return builder.build();
    }

    /** Listener for monitoring diagnostic event. */
    public interface DiagnosticListener {
        /**
         * Diagnostic events are available.
         *
         * @param events
         */
        void onDiagnosticEvents(List<CarDiagnosticEvent> events);
    }

    // Should be used only inside handleHalEvents method.
    private final LinkedList<CarDiagnosticEvent> mEventsToDispatch = new LinkedList<>();

    @Override
    public void handleHalEvents(List<VehiclePropValue> values) {
        for (VehiclePropValue value : values) {
            CarDiagnosticEvent event = createCarDiagnosticEvent(value);
            if (event != null) {
                mEventsToDispatch.add(event);
            }
        }

        DiagnosticListener listener = null;
        synchronized (this) {
            listener = mDiagnosticListener;
        }
        if (listener != null) {
            listener.onDiagnosticEvents(mEventsToDispatch);
        }
        mEventsToDispatch.clear();
    }

    public synchronized void setDiagnosticListener(DiagnosticListener listener) {
        mDiagnosticListener = listener;
    }

    public DiagnosticListener getDiagnosticListener() {
        return mDiagnosticListener;
    }

    @Override
    public void dump(PrintWriter writer) {
        writer.println("*Diagnostic HAL*");
    }

    @Override
    protected float fixSamplingRateForProperty(VehiclePropConfig prop, int carSensorManagerRate) {
        //TODO(egranata): tweak this for diagnostics
        switch (prop.changeMode) {
            case VehiclePropertyChangeMode.ON_CHANGE:
            case VehiclePropertyChangeMode.ON_SET:
                return 0;
        }
        float rate = 1.0f;
        switch (carSensorManagerRate) {
            case CarSensorManager.SENSOR_RATE_FASTEST:
            case CarSensorManager.SENSOR_RATE_FAST:
                rate = 10f;
                break;
            case CarSensorManager.SENSOR_RATE_UI:
                rate = 5f;
                break;
            default: // fall back to default.
                break;
        }
        if (rate > prop.maxSampleRate) {
            rate = prop.maxSampleRate;
        }
        if (rate < prop.minSampleRate) {
            rate = prop.minSampleRate;
        }
        return rate;
    }

    public DiagnosticCapabilities getDiagnosticCapabilities() {
        return mDiagnosticCapabilities;
    }

    @Nullable
    public CarDiagnosticEvent getCurrentLiveFrame() {
        try {
            VehiclePropValue value = mHal.get(VehicleProperty.OBD2_LIVE_FRAME);
            return createCarDiagnosticEvent(value);
        } catch (PropertyTimeoutException e) {
            Log.e(CarLog.TAG_DIAGNOSTIC, "timeout trying to read OBD2_LIVE_FRAME");
            return null;
        } catch (IllegalArgumentException e) {
            Log.e(CarLog.TAG_DIAGNOSTIC, "illegal argument trying to read OBD2_LIVE_FRAME", e);
            return null;
        }
    }

    @Nullable
    public long[] getFreezeFrameTimestamps() {
        try {
            VehiclePropValue value = mHal.get(VehicleProperty.OBD2_FREEZE_FRAME_INFO);
            long[] timestamps = new long[value.value.int64Values.size()];
            for (int i = 0; i < timestamps.length; ++i) {
                timestamps[i] = value.value.int64Values.get(i);
            }
            return timestamps;
        } catch (PropertyTimeoutException e) {
            Log.e(CarLog.TAG_DIAGNOSTIC, "timeout trying to read OBD2_FREEZE_FRAME_INFO");
            return null;
        } catch (IllegalArgumentException e) {
            Log.e(CarLog.TAG_DIAGNOSTIC,
                    "illegal argument trying to read OBD2_FREEZE_FRAME_INFO", e);
            return null;
        }
    }

    @Nullable
    public CarDiagnosticEvent getFreezeFrame(long timestamp) {
        VehiclePropValueBuilder builder = VehiclePropValueBuilder.newBuilder(
            VehicleProperty.OBD2_FREEZE_FRAME);
        builder.setInt64Value(timestamp);
        try {
            VehiclePropValue value = mHal.get(builder.build());
            return createCarDiagnosticEvent(value);
        } catch (PropertyTimeoutException e) {
            Log.e(CarLog.TAG_DIAGNOSTIC, "timeout trying to read OBD2_FREEZE_FRAME");
            return null;
        } catch (IllegalArgumentException e) {
            Log.e(CarLog.TAG_DIAGNOSTIC,
                    "illegal argument trying to read OBD2_FREEZE_FRAME", e);
            return null;
        }
    }

    public void clearFreezeFrames(long... timestamps) {
        VehiclePropValueBuilder builder = VehiclePropValueBuilder.newBuilder(
            VehicleProperty.OBD2_FREEZE_FRAME_CLEAR);
        builder.setInt64Value(timestamps);
        try {
            mHal.set(builder.build());
        } catch (PropertyTimeoutException e) {
            Log.e(CarLog.TAG_DIAGNOSTIC, "timeout trying to write OBD2_FREEZE_FRAME_CLEAR");
        } catch (IllegalArgumentException e) {
            Log.e(CarLog.TAG_DIAGNOSTIC,
                "illegal argument trying to write OBD2_FREEZE_FRAME_CLEAR", e);
        }
    }
}
