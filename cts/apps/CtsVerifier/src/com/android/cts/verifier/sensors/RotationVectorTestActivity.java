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
 * limitations under the License.
 */

package com.android.cts.verifier.sensors;

import com.android.cts.verifier.R;
import com.android.cts.verifier.sensors.base.SensorCtsVerifierTestActivity;
import com.android.cts.verifier.sensors.renderers.GLArrowSensorTestRenderer;

import junit.framework.Assert;

import android.content.Context;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.hardware.cts.helpers.SensorNotSupportedException;
import android.hardware.cts.helpers.SensorTestStateNotSupportedException;
import android.os.Bundle;
import android.util.Log;

import java.util.concurrent.TimeUnit;

/**
 * This test verifies that mobile device can detect it's orientation in space and after device
 * movement in space it correctly detects original (reference) position.
 * All three rotation vectors are tested:
 * - ROTATION_VECTOR,
 * - GEOMAGNETIC_ROTATION_VECTOR,
 * - GAME_ROTATION_VECTOR.
 */
public class RotationVectorTestActivity
        extends SensorCtsVerifierTestActivity
        implements SensorEventListener {
    public RotationVectorTestActivity() {
        super(RotationVectorTestActivity.class);
    }

    private SensorManager mSensorManager;
    private SensorEventListener mListener;

    /**
     * Defines the thresholds for each rotation vector in degrees.
     */
    private static final double[] MAX_DEVIATION_DEGREES = {
        10.0, // ROTATION_VECTOR
        10.0, // GEOMAGNETIC ROTATION_VECTOR
        40.0, // GAME_ROTATION_VECTOR
    };

    private static final int MAX_SENSORS_AVAILABLE = 3;
    private static final int ROTATION_VECTOR_INDEX = 0;
    private static final int GEOMAGNETIC_ROTATION_VECTOR_INDEX = 1;
    private static final int GAME_ROTATION_VECTOR_INDEX = 2;

    private float[][] mLastEvent = new float[3][5];
    private final float[][] mReference = new float[3][16];
    private final float[][] mAngularChange = new float[3][3];
    private final Sensor[] mSensor = new Sensor[3];

    /**
     * The activity setup collects all the required data for test cases.
     * This approach allows to test all sensors at once.
     */
    @Override
    protected void activitySetUp() throws InterruptedException {
        if (mSensor[ROTATION_VECTOR_INDEX] == null
                && mSensor[GEOMAGNETIC_ROTATION_VECTOR_INDEX] == null
                && mSensor[GAME_ROTATION_VECTOR_INDEX] == null) {
            // if none of the sensors is supported, skip the test by throwing an exception
            throw new SensorTestStateNotSupportedException("Rotation vectors are not supported.");
        }

        // TODO: take reference value automatically when device is 'still'
        clearText();
        appendText(R.string.snsr_rotation_vector_set_reference);
        waitForUserToContinue();

        clearText();
        for (int i = 0; i < MAX_SENSORS_AVAILABLE; ++i) {
            SensorManager.getRotationMatrixFromVector(mReference[i], mLastEvent[i].clone());
        }

        // TODO: check the user actually moved the device during the test
        appendText(R.string.snsr_rotation_vector_reference_set);
        appendText(R.string.snsr_rotation_vector_move_info);
        appendText(R.string.snsr_test_play_sound);
        Thread.sleep(TimeUnit.SECONDS.toMillis(30));
        playSound();

        // TODO: take final value automatically when device becomes 'still' at the end
        clearText();
        appendText(R.string.snsr_rotation_vector_set_final);
        waitForUserToContinue();

        clearText();
        closeGlSurfaceView();

        float[] finalVector = new float[16];
        for (int i = 0; i < MAX_SENSORS_AVAILABLE; ++i) {
            SensorManager.getRotationMatrixFromVector(finalVector, mLastEvent[i].clone());
            SensorManager.getAngleChange(mAngularChange[i], mReference[i], finalVector);
        }
    }

    /**
     * Verifies that a given 'Rotation Vector' sensor does not drift over time.
     * The test takes in consideration a reference measurement, and a final measurement. It then
     * calculates its angular change.
     */
    private String verifyVector(int sensorIndex, int sensorType)
            throws Throwable {
        Sensor sensor = mSensor[sensorIndex];
        if (sensor == null) {
            throw new SensorNotSupportedException(sensorType);
        }

        float[] angularChange = mAngularChange[sensorIndex];
        double maxDeviationDegrees = MAX_DEVIATION_DEGREES[sensorIndex];
        double maxComponentDegrees = findMaxComponentDegrees(angularChange);
        String message = getString(
                R.string.snsr_rotation_vector_verification,
                Math.toDegrees(angularChange[0]),
                Math.toDegrees(angularChange[1]),
                Math.toDegrees(angularChange[2]),
                maxComponentDegrees,
                maxDeviationDegrees);

        Assert.assertEquals(message, 0, maxComponentDegrees, maxDeviationDegrees);
        return message;
    }

    /**
     * Test cases.
     */
    public String testRotationVector() throws Throwable {
        return verifyVector(ROTATION_VECTOR_INDEX, Sensor.TYPE_ROTATION_VECTOR);
    }

    public String testGeomagneticRotationVector() throws Throwable {
        return verifyVector(
                GEOMAGNETIC_ROTATION_VECTOR_INDEX,
                Sensor.TYPE_GEOMAGNETIC_ROTATION_VECTOR);
    }

    public String testGameRotationVector() throws Throwable {
        return verifyVector(GAME_ROTATION_VECTOR_INDEX, Sensor.TYPE_GAME_ROTATION_VECTOR);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        // set up sensors first, so activitySetUp has the state in place
        mSensorManager = (SensorManager) getApplicationContext().getSystemService(
                Context.SENSOR_SERVICE);
        mSensor[ROTATION_VECTOR_INDEX] =
                mSensorManager.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR);
        mSensor[GEOMAGNETIC_ROTATION_VECTOR_INDEX] =
                mSensorManager.getDefaultSensor(Sensor.TYPE_GEOMAGNETIC_ROTATION_VECTOR);
        mSensor[GAME_ROTATION_VECTOR_INDEX] =
                mSensorManager.getDefaultSensor(Sensor.TYPE_GAME_ROTATION_VECTOR);

        super.onCreate(savedInstanceState);

        GLArrowSensorTestRenderer renderer =
                new GLArrowSensorTestRenderer(this, Sensor.TYPE_ROTATION_VECTOR);
        mListener = renderer;

        initializeGlSurfaceView(renderer);
    }

    @Override
    protected void onPause() {
        super.onPause();
        mSensorManager.unregisterListener(mListener);
        mSensorManager.unregisterListener(this);
    }

    @Override
    protected void onResume() {
        super.onResume();

        // listener for rendering
        boolean renderListenerRegistered = false;
        for (int i = 0; (!renderListenerRegistered && i < MAX_SENSORS_AVAILABLE); ++i) {
            Sensor sensor = mSensor[i];
            if (sensor != null) {
                renderListenerRegistered = mSensorManager
                        .registerListener(mListener, sensor, SensorManager.SENSOR_DELAY_GAME);
                Log.v(LOG_TAG, "Renderer using sensor: " + sensor.getName());
            }
        }

        // listeners for testing
        for (int i = 0; i < MAX_SENSORS_AVAILABLE; ++i) {
            mSensorManager.registerListener(this, mSensor[i], SensorManager.SENSOR_DELAY_GAME);
        }
    }

    @Override
    public void onSensorChanged(SensorEvent event) {
        if (event.sensor.getType() == Sensor.TYPE_ROTATION_VECTOR) {
            mLastEvent[ROTATION_VECTOR_INDEX] = event.values.clone();
        }
        if (event.sensor.getType() == Sensor.TYPE_GEOMAGNETIC_ROTATION_VECTOR) {
            mLastEvent[GEOMAGNETIC_ROTATION_VECTOR_INDEX] = event.values.clone();
        }
        if (event.sensor.getType() == Sensor.TYPE_GAME_ROTATION_VECTOR) {
            mLastEvent[GAME_ROTATION_VECTOR_INDEX] = event.values.clone();
        }
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {
    }

    private static double findMaxComponentDegrees(float[] vec) {
        float maxComponent = 0;
        for (int i = 0; i < vec.length; i++) {
            float absComp = Math.abs(vec[i]);
            if (maxComponent < absComp) {
                maxComponent = absComp;
            }
        }
        return Math.toDegrees(maxComponent);
    }
}
