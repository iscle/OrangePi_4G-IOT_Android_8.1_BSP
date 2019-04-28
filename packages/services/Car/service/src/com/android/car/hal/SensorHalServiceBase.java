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
 * limitations under the License.
 */

package com.android.car.hal;

import static java.lang.Integer.toHexString;

import android.annotation.Nullable;
import android.car.hardware.CarSensorEvent;
import android.hardware.automotive.vehicle.V2_0.VehiclePropConfig;
import android.hardware.automotive.vehicle.V2_0.VehiclePropValue;
import android.util.Log;
import android.util.SparseArray;
import com.android.car.CarLog;
import java.util.Collection;
import java.util.LinkedList;
import java.util.List;

/**
 * Common base for all SensorHal implementation.
 * It is wholly based on subscription and there is no explicit API for polling, but each sensor
 * should report its initial state immediately after {@link #requestSensorStart(int, int)} call.
 * It is ok to report sensor data {@link SensorListener#onSensorData(CarSensorEvent)} inside
 * the {@link #requestSensorStart(int, int)} call.
 */
public abstract class SensorHalServiceBase extends HalServiceBase implements SensorBase {
    private static final String TAG = CarLog.concatTag(CarLog.TAG_SENSOR,
                                                       SensorHalServiceBase.class);
    protected static final boolean DBG = false;

    private boolean mIsReady = false;

    protected static final int SENSOR_TYPE_INVALID = NOT_SUPPORTED_PROPERTY;
    protected final VehicleHal mHal;
    protected final SparseArray<VehiclePropConfig> mSensorToPropConfig = new SparseArray<>();

    public SensorHalServiceBase(VehicleHal hal) {
        mHal = hal;
    }


    @Override
    public synchronized Collection<VehiclePropConfig> takeSupportedProperties(
        Collection<VehiclePropConfig> allProperties) {
        if (DBG) Log.d(TAG, "takeSupportedProperties");
        LinkedList<VehiclePropConfig> supportedProperties = new LinkedList<>();
        for (VehiclePropConfig halProperty : allProperties) {
            int sensor = getTokenForProperty(halProperty);
            boolean mapped = sensor != SENSOR_TYPE_INVALID;
            if (DBG) {
                Log.d(TAG, "takeSupportedProperties, hal property "
                        + " 0x" + toHexString(halProperty.prop) +
                        (mapped ? (" mapped to " + sensor) : " ignored"));
            }
            if (mapped) {
                supportedProperties.add(halProperty);
                mSensorToPropConfig.append(sensor, halProperty);
            }
        }
        return supportedProperties;
    }

    @Override
    public synchronized void init() {
        mIsReady = true;
    }

    @Override
    public synchronized void release() {
        mIsReady = false;
    }

    /**
     * Sensor HAL should be ready after init call.
     * @return
     */
    @Override
    public synchronized boolean isReady() {
        return mIsReady;
    }

    /**
     * This should work after {@link #init()}.
     * @return
     */
    @Override
    public synchronized int[] getSupportedSensors() {
        int[] supportedSensors = new int[mSensorToPropConfig.size()];
        for (int i = 0; i < supportedSensors.length; i++) {
            supportedSensors[i] = mSensorToPropConfig.keyAt(i);
        }
        return supportedSensors;
    }

    @Override
    public synchronized boolean requestSensorStart(int sensorType, int rate) {
        if (DBG) Log.d(TAG, "requestSensorStart, sensorType: " + sensorType + ", rate: " + rate);
        VehiclePropConfig config = mSensorToPropConfig.get(sensorType);
        if (config == null) {
            Log.e(TAG, "requesting to start sensor " + sensorType + ", but VHAL config not found");
            return false;
        }
        //TODO calculate sampling rate properly, bug: 32095903
        mHal.subscribeProperty(this, config.prop, fixSamplingRateForProperty(config, rate));
        return true;
    }

    @Override
    public synchronized void requestSensorStop(int sensorType) {
        if (DBG) Log.d(TAG, "requestSensorStop, sensorType: " + sensorType);
        VehiclePropConfig config = mSensorToPropConfig.get(sensorType);
        if (config == null) {
            return;
        }
        mHal.unsubscribeProperty(this, config.prop);
    }

    @Nullable
    public VehiclePropValue getCurrentSensorVehiclePropValue(int sensorType) {
        VehiclePropConfig config;
        synchronized (this) {
            config = mSensorToPropConfig.get(sensorType);
        }
        if (config == null) {
            Log.e(TAG, "sensor type not available 0x" + toHexString(sensorType));
            return null;
        }
        try {
            return mHal.get(config.prop);
        } catch (PropertyTimeoutException e) {
            Log.e(TAG, "property not ready 0x" + toHexString(config.prop), e);
            return null;
        }
    }


    @Override
    public void handleHalEvents(List<VehiclePropValue> values) {
        // default no-op impl. Necessary to not propagate this HAL specific event to logical
        // sensor provider.
        throw new RuntimeException("should not be called");
    }

    protected abstract float fixSamplingRateForProperty(VehiclePropConfig prop,
        int carSensorManagerRate);

    /**
     * Returns a unique token to be used to map this property to a higher-level sensor
     * This token will be stored in mSensorToPropConfig to allow callers to go from unique
     * sensor identifiers to VehiclePropConfig objects
     * @param config
     * @return SENSOR_TYPE_INVALID or a locally unique token
     */
    protected abstract int getTokenForProperty(VehiclePropConfig config);
}
