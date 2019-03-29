/*
 * Copyright (C) 2014 The Android Open Source Project
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
 * limitations under the License
 */

package android.hardware.cts.helpers;

import junit.framework.Assert;

import android.content.Context;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;

/**
 * Helper class that provides a way to identify if movement has been detected in the device.
 *
 * Notes:
 * Alpha is calculated as:
 *      t / ( t + dT)
 * Where
 *       t - low-pass filter's time-constant
 *      dT - event delivery rate
 */
public abstract class MovementDetectorHelper implements SensorEventListener {
    private static final float MOVEMENT_DETECTION_ACCELERATION_THRESHOLD = 4.0f;
    private static final float ALPHA = 0.8f;

    private final float[] mGravity = {0.0f, 0.0f, 0.0f};

    private final SensorManager mSensorManager;
    private final Sensor mAccelerometer;

    private boolean mInitialized;

    public MovementDetectorHelper(Context context) {
        mSensorManager = (SensorManager) context.getSystemService(Context.SENSOR_SERVICE);
        Assert.assertNotNull("SensorManager", mSensorManager);

        mAccelerometer = mSensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
        if (mAccelerometer == null) {
            throw new SensorNotSupportedException(Sensor.TYPE_ACCELEROMETER);
        }
    }

    public synchronized void start() {
        if (mInitialized) {
            return;
        }

        mInitialized = mSensorManager
                .registerListener(this, mAccelerometer,SensorManager.SENSOR_DELAY_NORMAL);
        Assert.assertTrue(mInitialized);
    }

    public synchronized void stop() {
        if (!mInitialized) {
            return;
        }

        mSensorManager.unregisterListener(this);
        mInitialized = false;
    }

    protected abstract void onMovementDetected();

    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {}

    @Override
    public void onSensorChanged(SensorEvent event) {
        float[] linearAcceleration = {0.0f, 0.0f, 0.0f};

        if (mGravity[0] == 0f && mGravity[2] == 0f) {
            mGravity[0] = event.values[0];
            mGravity[1] = event.values[1];
            mGravity[2] = event.values[2];
        } else {
            // Isolate the force of gravity with the low-pass filter.
            mGravity[0] = ALPHA * mGravity[0] + (1 - ALPHA) * event.values[0];
            mGravity[1] = ALPHA * mGravity[1] + (1 - ALPHA) * event.values[1];
            mGravity[2] = ALPHA * mGravity[2] + (1 - ALPHA) * event.values[2];
        }

        // Remove the gravity contribution with the high-pass filter.
        linearAcceleration[0] = event.values[0] - mGravity[0];
        linearAcceleration[1] = event.values[1] - mGravity[1];
        linearAcceleration[2] = event.values[2] - mGravity[2];

        float totalAcceleration = Math.abs(linearAcceleration[0])
                + Math.abs(linearAcceleration[1])
                + Math.abs(linearAcceleration[2]);

        if (totalAcceleration > MOVEMENT_DETECTION_ACCELERATION_THRESHOLD) {
            onMovementDetected();
        }
    }
}
