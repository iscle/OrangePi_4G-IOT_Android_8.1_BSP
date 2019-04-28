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
package com.android.devcamera;

import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.util.Log;

import java.util.ArrayDeque;

/**
 * Put all the Gyro stuff here.
 */
public class GyroOperations {
    private static final String TAG = "DevCamera_GYRO";

    private SensorManager mSensorManager;
    private GyroListener mListener;

    private SensorEventListener mSensorEventListener = new SensorEventListener() {
        @Override
        public void onSensorChanged(SensorEvent event) {
            delayGyroData(event);
        }
        @Override
        public void onAccuracyChanged(Sensor sensor, int accuracy) {
        }
    };

    public GyroOperations(SensorManager sensorManager) {
        mSensorManager = sensorManager;
    }

    public void startListening(GyroListener listener) {
        mSensorManager.registerListener(mSensorEventListener, mSensorManager.getDefaultSensor(Sensor.TYPE_GYROSCOPE), SensorManager.SENSOR_DELAY_FASTEST);
        mListener = listener;
    }

    public void stopListening() {
        mSensorManager.unregisterListener(mSensorEventListener);
    }

    // We need to make a copy of SensorEvent so we can put it in our delay-line.
    class GyroEvent2D {
        public long timestamp;
        public final float[] values = new float[2];

        public GyroEvent2D(SensorEvent event) {
            this.timestamp = event.timestamp;
            this.values[0] = event.values[0];
            this.values[1] = event.values[1];
        }
    }

    private long mGyroLastTimestamp = 0;
    private float[] mGyroAngle = new float[]{0f, 0f}; // radians, X and Y axes.
    // Gyro arrives at 230 Hz on N6: 23 samples in 100 ms.  Viewfinder latency is 70 ms.  Delay about 15 samples.
    private ArrayDeque<GyroEvent2D> mSensorDelayLine = new ArrayDeque<>();
    private static final int DELAY_SIZE = 10;

    void delayGyroData(SensorEvent event) {
        mSensorDelayLine.addLast(new GyroEvent2D(event));
        if (mSensorDelayLine.size() < DELAY_SIZE) {
            return;
        }
        GyroEvent2D delayedEvent = mSensorDelayLine.removeFirst();
        integrateGyroForPosition(delayedEvent);
    }

    void integrateGyroForPosition(GyroEvent2D event) {
        if (mGyroLastTimestamp == 0) {
            mGyroLastTimestamp = event.timestamp;
            return;
        }
        long dt = (event.timestamp - mGyroLastTimestamp) / 1000; // microseconds between samples
        if (dt > 10000) { // below 100 Hz
            Log.v(TAG, " ===============> GYRO STALL <==============");
        }
        mGyroAngle[0] += event.values[0] * 0.000001f * dt;
        mGyroAngle[1] += event.values[1] * 0.000001f * dt;
        mGyroLastTimestamp = event.timestamp;

        // TODO: Add UI
        //updateOrientationUI(mGyroAngle, dt);
        //Log.v(TAG, String.format("Gyro: theta_x = %.2f  theta_y = %.2f   dt = %d", mGyroAngle[0]*180f/3.14f, mGyroAngle[1]*180f/3.14f, dt));

        mListener.updateGyroAngles(mGyroAngle);
    }

}
