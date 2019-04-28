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

package com.android.car;

import android.car.hardware.CarSensorEvent;
import android.car.hardware.CarSensorManager;
import android.content.Context;
import android.util.Log;

import com.android.car.hal.SensorHalService.SensorListener;
import java.io.PrintWriter;

//TODO implement default one based on time or other sensors. bug: 32066909
public class DayNightModePolicy extends CarSensorService.LogicalSensor {

    private final Context mContext;
    private SensorListener mSensorListener;
    private boolean mIsReady = false;
    private boolean mStarted = false;

    private static final int[] SUPPORTED_SENSORS = { CarSensorManager.SENSOR_TYPE_NIGHT };

    public DayNightModePolicy(Context context) {
        mContext = context;
    }

    @Override
    public synchronized void init() {
        mIsReady = true;
    }

    @Override
    public synchronized void release() {
    }

    public static CarSensorEvent getDefaultValue(int sensorType) {
        // There's a race condition and timestamp from vehicle HAL could be slightly less
        // then current call to SystemClock.elapsedRealtimeNanos() will return.
        // We want vehicle HAL value always override this default value so we set timestamp to 0.
        return createEvent(true /* isNight */, 0 /* timestamp */);
    }

    public synchronized void registerSensorListener(SensorListener listener) {
        mSensorListener = listener;
    }

    @Override
    public synchronized void onSensorServiceReady() {
    }

    @Override
    public synchronized boolean isReady() {
        return mIsReady;
    }

    @Override
    public synchronized int[] getSupportedSensors() {
        return SUPPORTED_SENSORS;
    }

    @Override
    public synchronized boolean requestSensorStart(int sensorType, int rate) {
        mStarted = true;
        Log.w(CarLog.TAG_SENSOR,
                "DayNightModePolicy.requestSensorStart, default policy not implemented");
        return false;
    }

    @Override
    public synchronized void requestSensorStop(int sensorType) {
    }

    private static CarSensorEvent createEvent(boolean isNight, long timestamp) {
        CarSensorEvent event = new CarSensorEvent(CarSensorManager.SENSOR_TYPE_NIGHT,
                timestamp, 0, 1, 0);
        event.intValues[0] = isNight ? 1 : 0;
        return event;
    }

    @Override
    public synchronized void dump(PrintWriter writer) {
    }
}
