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

import android.content.pm.PackageManager;
import android.hardware.Sensor;
import android.hardware.SensorManager;
import android.hardware.cts.helpers.TestSensorEnvironment;
import android.hardware.cts.helpers.sensoroperations.TestSensorOperation;

import java.util.concurrent.TimeUnit;

/**
 * Activity that verifies batching capabilities for sensors
 * (https://source.android.com/devices/sensors/batching.html).
 *
 * If a sensor supports the batching mode, FifoReservedEventCount for that sensor should be greater
 * than one.
 */
public class BatchingTestActivity extends SensorCtsVerifierTestActivity {
    public BatchingTestActivity() {
        super(BatchingTestActivity.class);
    }

    private static final int SENSOR_BATCHING_RATE_US = SensorManager.SENSOR_DELAY_FASTEST;
    private static final int REPORT_LATENCY_10_SEC = 10;
    private static final int BATCHING_PADDING_TIME_S = 2;

    // we are testing sensors that only trigger based on external events, so leave enough time for
    // such events to generate
    private static final int REPORT_LATENCY_25_SEC = 25;

    // TODO: refactor to discover all available sensors of each type and dynamically generate test
    // cases for all of them
    @SuppressWarnings("unused")
    public String testStepCounter_batching() throws Throwable {
        return runBatchTest(
                Sensor.TYPE_STEP_COUNTER,
                REPORT_LATENCY_25_SEC,
                R.string.snsr_batching_walking_needed);
    }

    @SuppressWarnings("unused")
    public String testStepCounter_flush() throws Throwable {
        return runFlushTest(
                Sensor.TYPE_STEP_COUNTER,
                REPORT_LATENCY_25_SEC,
                R.string.snsr_batching_walking_needed);
    }

    @SuppressWarnings("unused")
    public String testStepDetector_batching() throws Throwable {
        return  runBatchTest(
                Sensor.TYPE_STEP_DETECTOR,
                REPORT_LATENCY_25_SEC,
                R.string.snsr_batching_walking_needed);
    }

    @SuppressWarnings("unused")
    public String testStepDetector_flush() throws Throwable {
        return  runFlushTest(
                Sensor.TYPE_STEP_DETECTOR,
                REPORT_LATENCY_25_SEC,
                R.string.snsr_batching_walking_needed);
    }

    @SuppressWarnings("unused")
    public String testProximity_batching() throws Throwable {
        if (getPackageManager().hasSystemFeature(PackageManager.FEATURE_SENSOR_PROXIMITY)) {
            return null;
        }
        return runBatchTest(
                Sensor.TYPE_PROXIMITY,
                REPORT_LATENCY_10_SEC,
                R.string.snsr_interaction_needed);
    }

    @SuppressWarnings("unused")
    public String testProximity_flush() throws Throwable {
        if (getPackageManager().hasSystemFeature(PackageManager.FEATURE_SENSOR_PROXIMITY)) {
            return null;
        }
        return runFlushTest(
                Sensor.TYPE_PROXIMITY,
                REPORT_LATENCY_10_SEC,
                R.string.snsr_interaction_needed);
    }

    @SuppressWarnings("unused")
    public String testLight_batching() throws Throwable {
        return runBatchTest(
                Sensor.TYPE_LIGHT,
                REPORT_LATENCY_10_SEC,
                R.string.snsr_interaction_needed);
    }

    @SuppressWarnings("unused")
    public String testLight_flush() throws Throwable {
        return runFlushTest(
                Sensor.TYPE_LIGHT,
                REPORT_LATENCY_10_SEC,
                R.string.snsr_interaction_needed);
    }

    private String runBatchTest(int sensorType, int maxBatchReportLatencySec, int instructionsResId)
            throws Throwable {
        getTestLogger().logInstructions(instructionsResId);
        waitForUserToBegin();

        int maxBatchReportLatencyUs = (int) TimeUnit.SECONDS.toMicros(maxBatchReportLatencySec);
        TestSensorEnvironment environment = new TestSensorEnvironment(
                getApplicationContext(),
                sensorType,
                SENSOR_BATCHING_RATE_US,
                maxBatchReportLatencyUs);

        int testDurationSec = maxBatchReportLatencySec + BATCHING_PADDING_TIME_S;
        TestSensorOperation operation =
                TestSensorOperation.createOperation(environment, testDurationSec,TimeUnit.SECONDS);
        return executeTest(operation);
    }

    private String runFlushTest(int sensorType, int maxBatchReportLatencySec, int instructionsResId)
            throws Throwable {
        getTestLogger().logInstructions(instructionsResId);
        waitForUserToBegin();

        int maxBatchReportLatencyUs = (int) TimeUnit.SECONDS.toMicros(maxBatchReportLatencySec);
        TestSensorEnvironment environment = new TestSensorEnvironment(
                getApplicationContext(),
                sensorType,
                SENSOR_BATCHING_RATE_US,
                maxBatchReportLatencyUs);

        int flushDurationSec = maxBatchReportLatencySec / 2;
        TestSensorOperation operation = TestSensorOperation
                .createFlushOperation(environment, flushDurationSec, TimeUnit.SECONDS);
        return executeTest(operation);
    }

    private String executeTest(TestSensorOperation operation) throws InterruptedException {
        operation.addDefaultVerifications();
        operation.execute(getCurrentTestNode());
        return null;
    }
}
