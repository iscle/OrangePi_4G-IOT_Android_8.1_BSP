/*
 * Copyright (C) 2013 The Android Open Source Project
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

import android.hardware.Sensor;
import android.hardware.SensorManager;
import android.hardware.cts.helpers.TestSensorEnvironment;
import android.hardware.cts.helpers.sensoroperations.TestSensorOperation;
import android.hardware.cts.helpers.sensorverification.MeanVerification;

import java.util.concurrent.TimeUnit;

/**
 * Semi-automated test that focuses on characteristics associated with Accelerometer measurements.
 */
public class AccelerometerMeasurementTestActivity extends SensorCtsVerifierTestActivity {
    public AccelerometerMeasurementTestActivity() {
        super(AccelerometerMeasurementTestActivity.class);
    }

    public String testFaceUp() throws Throwable {
        return verifyMeasurements(
                R.string.snsr_accel_test_face_up,
                0, 0, SensorManager.STANDARD_GRAVITY);
    }

    public String testFaceDown() throws Throwable {
        return delayedVerifyMeasurements(
                R.string.snsr_accel_test_face_down,
                0, 0, -SensorManager.STANDARD_GRAVITY);
    }

    public String testRightSide() throws Throwable {
        return verifyMeasurements(
                R.string.snsr_accel_test_right_side,
                -SensorManager.STANDARD_GRAVITY, 0, 0);
    }

    public String testLeftSide() throws Throwable {
        return verifyMeasurements(
                R.string.snsr_accel_test_left_side,
                SensorManager.STANDARD_GRAVITY, 0, 0);
    }

    public String testTopSide() throws Throwable {
        return verifyMeasurements(
                R.string.snsr_accel_test_top_side,
                0, -SensorManager.STANDARD_GRAVITY, 0);
    }

    public String testBottomSide() throws Throwable {
        return verifyMeasurements(
                R.string.snsr_accel_test_bottom_side,
                0, SensorManager.STANDARD_GRAVITY, 0);
    }

    /**
     * This test verifies that the Accelerometer measurements are close to the expected reference
     * values (range and scale).
     *
     * The test takes a set of samples from the sensor under test and calculates the mean of each
     * axis that the sensor data collects. It then compares it against the test expectations that
     * are represented by reference values and a threshold.

     * The reference values are coupled to the orientation of the device. The test is susceptible to
     * errors when the device is not oriented properly, or the units in which the data are reported
     * and the expectations are set are different.
     *
     * The error message associated with the test provides the required data needed to identify any
     * possible issue. It provides:
     * - the thread id on which the failure occurred
     * - the sensor type and sensor handle that caused the failure
     * - the values representing the expectation of the test
     * - the mean of values sampled from the sensor
     */
    private String verifyMeasurements(float ... expectations) throws Throwable {
        Thread.sleep(500 /*ms*/);
        TestSensorEnvironment environment = new TestSensorEnvironment(
                getApplicationContext(),
                Sensor.TYPE_ACCELEROMETER,
                SensorManager.SENSOR_DELAY_FASTEST);
        TestSensorOperation verifyMeasurements =
                TestSensorOperation.createOperation(environment, 100 /* event count */);
        verifyMeasurements.addVerification(new MeanVerification(
                expectations,
                new float[]{1.95f, 1.95f, 1.95f} /* m / s^2 */,
                new float[]{1.95f, 1.95f, 1.95f} /* m / s^2 */));
        verifyMeasurements.execute(getCurrentTestNode());
        return null;
    }

    private String delayedVerifyMeasurements(int descriptionResId, float ... expectations)
            throws Throwable {
        SensorTestLogger logger = getTestLogger();
        logger.logInstructions(descriptionResId);
        logger.logWaitForSound();
        waitForUserToBegin();
        Thread.sleep(TimeUnit.MILLISECONDS.convert(7, TimeUnit.SECONDS));

        try {
            return verifyMeasurements(expectations);
        } finally {
            playSound();
        }
    }

    private String verifyMeasurements(int descriptionResId, float ... expectations)
            throws Throwable {
        SensorTestLogger logger = getTestLogger();
        logger.logInstructions(descriptionResId);
        logger.logInstructions(R.string.snsr_device_steady);
        waitForUserToBegin();

        return verifyMeasurements(expectations);
    }
}
