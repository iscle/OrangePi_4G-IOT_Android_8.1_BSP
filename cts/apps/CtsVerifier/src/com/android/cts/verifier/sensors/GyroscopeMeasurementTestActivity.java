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
import com.android.cts.verifier.sensors.renderers.GLRotationGuideRenderer;

import android.hardware.Sensor;
import android.hardware.SensorManager;
import android.hardware.cts.helpers.SensorCalibratedUncalibratedVerifier;
import android.hardware.cts.helpers.TestSensorEnvironment;
import android.hardware.cts.helpers.sensoroperations.TestSensorOperation;
import android.hardware.cts.helpers.sensorverification.GyroscopeIntegrationVerification;

import java.util.concurrent.TimeUnit;

/**
 * Semi-automated test that focuses on characteristics associated with Gyroscope measurements.
 */
public class GyroscopeMeasurementTestActivity extends SensorCtsVerifierTestActivity {
    private static final float THRESHOLD_CALIBRATED_UNCALIBRATED_RAD_SEC = 0.01f;
    private static final float THRESHOLD_AXIS_UNDER_ROTATION_DEG = 10.0f;
    private static final float THRESHOLD_AXIS_UNDER_NO_ROTATION_DEG = 50.0f;

    private static final int ROTATE_360_DEG = 360;
    private static final int ROTATION_COLLECTION_SEC = 10;

    private static final int X_AXIS = 0;
    private static final int Y_AXIS = 1;
    private static final int Z_AXIS = 2;

    private final GLRotationGuideRenderer mRenderer = new GLRotationGuideRenderer();

    public GyroscopeMeasurementTestActivity() {
        super(GyroscopeMeasurementTestActivity.class);
    }

    @Override
    protected void activitySetUp() throws InterruptedException {
        getTestLogger().logInstructions(R.string.snsr_gyro_device_placement);
        waitForUserToContinue();
        initializeGlSurfaceView(mRenderer);
    }

    @Override
    protected void activityCleanUp() {
        closeGlSurfaceView();
    }

    @SuppressWarnings("unused")
    public String testDeviceStatic() throws Throwable {
        return verifyMeasurements(
                R.string.snsr_gyro_device_static,
                -1 /* rotationAxis */,
                0 /* expectationDeg */);
    }

    @SuppressWarnings("unused")
    public String testRotateClockwise() throws Throwable {
        return verifyMeasurements(R.string.snsr_gyro_rotate_device, Z_AXIS, -ROTATE_360_DEG);
    }

    @SuppressWarnings("unused")
    public String testRotateCounterClockwise() throws Throwable {
        return verifyMeasurements(R.string.snsr_gyro_rotate_device, Z_AXIS, ROTATE_360_DEG);
    }

    @SuppressWarnings("unused")
    public String testRotateRightSide() throws Throwable {
        return verifyMeasurements(R.string.snsr_gyro_rotate_device, Y_AXIS, ROTATE_360_DEG);
    }

    @SuppressWarnings("unused")
    public String testRotateLeftSide() throws Throwable {
        return verifyMeasurements(R.string.snsr_gyro_rotate_device, Y_AXIS, -ROTATE_360_DEG);
    }

    @SuppressWarnings("unused")
    public String testRotateTopSide() throws Throwable {
        return verifyMeasurements(R.string.snsr_gyro_rotate_device, X_AXIS, -ROTATE_360_DEG);
    }

    @SuppressWarnings("unused")
    public String testRotateBottomSide() throws Throwable {
        return verifyMeasurements(R.string.snsr_gyro_rotate_device, X_AXIS, ROTATE_360_DEG);
    }

    /**
     * Verifies that the relationship between readings from calibrated and their corresponding
     * uncalibrated sensors comply to the following equation:
     *      calibrated = uncalibrated - bias
     */
    @SuppressWarnings("unused")
    public String testCalibratedAndUncalibrated() throws Throwable {
        setRendererRotation(Z_AXIS, false);

        SensorTestLogger logger = getTestLogger();
        logger.logInstructions(R.string.snsr_keep_device_rotating_clockwise);
        waitForUserToBegin();
        logger.logWaitForSound();

        TestSensorEnvironment calibratedEnvironment = new TestSensorEnvironment(
                getApplicationContext(),
                Sensor.TYPE_GYROSCOPE,
                SensorManager.SENSOR_DELAY_FASTEST);
        TestSensorEnvironment uncalibratedEnvironment = new TestSensorEnvironment(
                getApplicationContext(),
                Sensor.TYPE_GYROSCOPE_UNCALIBRATED,
                SensorManager.SENSOR_DELAY_FASTEST);
        SensorCalibratedUncalibratedVerifier verifier = new SensorCalibratedUncalibratedVerifier(
                calibratedEnvironment,
                uncalibratedEnvironment,
                THRESHOLD_CALIBRATED_UNCALIBRATED_RAD_SEC);

        try {
            verifier.execute();
        } finally {
            playSound();
        }
        return null;
    }

    /**
     * This test verifies that the Gyroscope measures the appropriate angular position.
     *
     * The test takes a set of samples from the sensor under test and calculates the angular
     * position for each axis that the sensor data collects. It then compares it against the test
     * expectations that are represented by signed values. It verifies that the readings have the
     * right magnitude.
     */
    private String verifyMeasurements(int instructionsResId, int rotationAxis, int expectationDeg)
            throws Throwable {
        setRendererRotation(rotationAxis, expectationDeg >= 0);

        SensorTestLogger logger = getTestLogger();
        logger.logInstructions(instructionsResId);
        waitForUserToBegin();
        logger.logWaitForSound();

        TestSensorEnvironment environment = new TestSensorEnvironment(
                getApplicationContext(),
                Sensor.TYPE_GYROSCOPE,
                SensorManager.SENSOR_DELAY_FASTEST);
        TestSensorOperation sensorOperation = TestSensorOperation
                .createOperation(environment, ROTATION_COLLECTION_SEC, TimeUnit.SECONDS);

        int gyroscopeAxes = environment.getSensorAxesCount();
        int[] expectationsDeg = getExpectationsDeg(gyroscopeAxes, rotationAxis, expectationDeg);
        float[] thresholdsDeg = getThresholdsDeg(gyroscopeAxes, rotationAxis);
        GyroscopeIntegrationVerification integrationVerification =
                new GyroscopeIntegrationVerification(expectationsDeg, thresholdsDeg);
        sensorOperation.addVerification(integrationVerification);

        try {
            sensorOperation.execute(getCurrentTestNode());
        } finally {
            playSound();
        }
        return null;
    }

    private int[] getExpectationsDeg(int axes, int rotationAxis, int expectationDeg) {
        int[] expectationsDeg = new int[axes];
        for (int i = 0; i < axes; ++i) {
            // tests assume that rotation is expected on one axis at a time
            expectationsDeg[i] = (i == rotationAxis) ? expectationDeg : 0;
        }
        return expectationsDeg;
    }

    private float[] getThresholdsDeg(int axes, int rotationAxis) {
        float[] thresholdsDeg = new float[axes];
        for (int i = 0; i < axes; ++i) {
            // tests set a high threshold on the axes where rotation is not expected, to account
            // for movement from the operator
            // the rotation axis has a lower threshold to ensure the gyroscope's accuracy
            thresholdsDeg[i] = (i == rotationAxis)
                    ? THRESHOLD_AXIS_UNDER_ROTATION_DEG
                    : THRESHOLD_AXIS_UNDER_NO_ROTATION_DEG;
        }
        return thresholdsDeg;
    }

    private void setRendererRotation(int rotationAxis, boolean positiveRotation) {
        int axis1 = 0;
        int axis2 = 0;
        int axis3 = 0;
        switch (rotationAxis) {
            case X_AXIS:
                axis1 = positiveRotation ? 1 : -1;
                break;
            case Y_AXIS:
                axis2 = positiveRotation ? 1 : -1;
                break;
            case Z_AXIS:
                axis3 = positiveRotation ? 1 : -1;
                break;
        }
        mRenderer.setRotation(axis1, axis2, axis3);
    }
}
