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

package android.hardware.cts;

import android.hardware.Sensor;
import android.hardware.SensorManager;
import android.hardware.cts.helpers.SensorCtsHelper;
import android.hardware.cts.helpers.SensorStats;
import android.hardware.cts.helpers.TestSensorEnvironment;
import android.hardware.cts.helpers.sensoroperations.TestSensorOperation;
import android.hardware.cts.helpers.sensorverification.EventBasicVerification;
import android.hardware.cts.helpers.sensorverification.ISensorVerification;

import java.util.concurrent.TimeUnit;

/**
 * Set of tests to verify that sensors operate correctly when operating in batching mode.
 * This class defines tests for continuous sensors when the device is awake.
 * On-change and special sensors are tested separately inside CtsVerifier, and they are defined in:
 * {@link com.android.cts.verifier.sensors.BatchingTestActivity}.
 *
 * Each test is expected to pass even if batching is not supported for a particular sensor. This is
 * usually achieved by ensuring that {@link ISensorVerification}s fallback accordingly.
 *
 * <p>To execute these test cases, the following command can be used:</p>
 * <pre>
 * adb shell am instrument -e class android.hardware.cts.SensorBatchingTests \
 *     -w android.hardware.cts/android.test.AndroidJUnitRunner
 * </pre>
 */
public class SensorBatchingTests extends SensorTestCase {
    private static final String TAG = "SensorBatchingTests";

    private static final int BATCHING_PERIOD = 10;  // sec
    private static final int RATE_50HZ = 20000;
    private static final int RATE_FASTEST = SensorManager.SENSOR_DELAY_FASTEST;

    /**
     * An arbitrary 'padding' time slot to wait for events after batching latency expires.
     * This allows for the test to wait for event arrivals after batching was expected.
     */
    private static final int BATCHING_PADDING_TIME_S = (int) Math.ceil(BATCHING_PERIOD * 0.1f + 2);

    public void testAccelerometer_fastest_batching() throws Throwable {
        runBatchingSensorTest(Sensor.TYPE_ACCELEROMETER, RATE_FASTEST, BATCHING_PERIOD);
    }

    public void testAccelerometer_50hz_batching() throws Throwable {
        runBatchingSensorTest(Sensor.TYPE_ACCELEROMETER, RATE_50HZ, BATCHING_PERIOD);
    }

    public void testAccelerometer_fastest_flush() throws Throwable {
        runFlushSensorTest(Sensor.TYPE_ACCELEROMETER, RATE_FASTEST, BATCHING_PERIOD);
    }

    public void testAccelerometer_50hz_flush() throws Throwable {
        runFlushSensorTest(Sensor.TYPE_ACCELEROMETER, RATE_50HZ, BATCHING_PERIOD);
    }

    public void testAccelerometerUncalibrated_fastest_batching() throws Throwable {
        runBatchingSensorTest(Sensor.TYPE_ACCELEROMETER_UNCALIBRATED,
                              RATE_FASTEST,
                              BATCHING_PERIOD);
    }

    public void testAccelUncalibrated() throws Throwable {
        runBatchingSensorTest(Sensor.TYPE_ACCELEROMETER_UNCALIBRATED,
                              RATE_FASTEST,
                              BATCHING_PERIOD);
    }

    public void testAccelUncalibrated_50hz_batching() throws Throwable {
        runBatchingSensorTest(Sensor.TYPE_ACCELEROMETER_UNCALIBRATED, RATE_50HZ, BATCHING_PERIOD);
    }

    public void testAccelUncalibrated_fastest_flush() throws Throwable {
        runFlushSensorTest(Sensor.TYPE_ACCELEROMETER_UNCALIBRATED, RATE_FASTEST, BATCHING_PERIOD);
    }

    public void testAccelUncalibrated_50hz_flush() throws Throwable {
        runFlushSensorTest(Sensor.TYPE_ACCELEROMETER_UNCALIBRATED, RATE_50HZ, BATCHING_PERIOD);
    }

    public void testMagneticField_fastest_batching() throws Throwable {
        runBatchingSensorTest(Sensor.TYPE_MAGNETIC_FIELD, RATE_FASTEST, BATCHING_PERIOD);
    }

    public void testMagneticField_50hz_batching() throws Throwable {
        runBatchingSensorTest(Sensor.TYPE_MAGNETIC_FIELD, RATE_50HZ, BATCHING_PERIOD);
    }

    public void testMagneticField_fastest_flush() throws Throwable {
        runFlushSensorTest(Sensor.TYPE_MAGNETIC_FIELD, RATE_FASTEST, BATCHING_PERIOD);
    }

    public void testMagneticField_50hz_flush() throws Throwable {
        runFlushSensorTest(Sensor.TYPE_MAGNETIC_FIELD, RATE_50HZ, BATCHING_PERIOD);
    }

    @SuppressWarnings("deprecation")
    public void testOrientation_fastest_batching() throws Throwable {
        runBatchingSensorTest(Sensor.TYPE_ORIENTATION, RATE_FASTEST, BATCHING_PERIOD);
    }

    @SuppressWarnings("deprecation")
    public void testOrientation_50hz_batching() throws Throwable {
        runBatchingSensorTest(Sensor.TYPE_ORIENTATION, RATE_50HZ, BATCHING_PERIOD);
    }

    @SuppressWarnings("deprecation")
    public void testOrientation_fastest_flush() throws Throwable {
        runFlushSensorTest(Sensor.TYPE_ORIENTATION, RATE_FASTEST, BATCHING_PERIOD);
    }

    @SuppressWarnings("deprecation")
    public void testOrientation_50hz_flush() throws Throwable {
        runFlushSensorTest(Sensor.TYPE_ORIENTATION, RATE_50HZ, BATCHING_PERIOD);
    }

    public void testGyroscope_fastest_batching() throws Throwable {
        runBatchingSensorTest(Sensor.TYPE_GYROSCOPE, RATE_FASTEST, BATCHING_PERIOD);
    }

    public void testGyroscope_50hz_batching() throws Throwable {
        runBatchingSensorTest(Sensor.TYPE_GYROSCOPE, RATE_50HZ, BATCHING_PERIOD);
    }

    public void testGyroscope_fastest_flush() throws Throwable {
        runFlushSensorTest(Sensor.TYPE_GYROSCOPE, SensorManager.SENSOR_DELAY_FASTEST, BATCHING_PERIOD);
    }

    public void testGyroscope_50hz_flush() throws Throwable {
        runFlushSensorTest(Sensor.TYPE_GYROSCOPE, RATE_50HZ, BATCHING_PERIOD);
    }

    public void testPressure_fastest_batching() throws Throwable {
        runBatchingSensorTest(Sensor.TYPE_PRESSURE, RATE_FASTEST, BATCHING_PERIOD);
    }

    public void testPressure_50hz_batching() throws Throwable {
        runBatchingSensorTest(Sensor.TYPE_PRESSURE, RATE_50HZ, BATCHING_PERIOD);
    }

    public void testPressure_fastest_flush() throws Throwable {
        runFlushSensorTest(Sensor.TYPE_PRESSURE, SensorManager.SENSOR_DELAY_FASTEST, BATCHING_PERIOD);
    }

    public void testPressure_50hz_flush() throws Throwable {
        runFlushSensorTest(Sensor.TYPE_PRESSURE, RATE_50HZ, BATCHING_PERIOD);
    }

    public void testGravity_fastest_batching() throws Throwable {
        runBatchingSensorTest(Sensor.TYPE_GRAVITY, RATE_FASTEST, BATCHING_PERIOD);
    }

    public void testGravity_50hz_batching() throws Throwable {
        runBatchingSensorTest(Sensor.TYPE_GRAVITY, RATE_50HZ, BATCHING_PERIOD);
    }

    public void testGravity_fastest_flush() throws Throwable {
        runFlushSensorTest(Sensor.TYPE_GRAVITY, SensorManager.SENSOR_DELAY_FASTEST, BATCHING_PERIOD);
    }

    public void testGravity_50hz_flush() throws Throwable {
        runFlushSensorTest(Sensor.TYPE_GRAVITY, RATE_50HZ, BATCHING_PERIOD);
    }

    public void testRotationVector_fastest_batching() throws Throwable {
        runBatchingSensorTest(Sensor.TYPE_ROTATION_VECTOR, RATE_FASTEST, BATCHING_PERIOD);
    }

    public void testRotationVector_50hz_batching() throws Throwable {
        runBatchingSensorTest(Sensor.TYPE_ROTATION_VECTOR, RATE_50HZ, BATCHING_PERIOD);
    }

    public void testRotationVector_fastest_flush() throws Throwable {
        runFlushSensorTest(Sensor.TYPE_ROTATION_VECTOR, RATE_FASTEST, BATCHING_PERIOD);
    }

    public void testRotationVector_50hz_flush() throws Throwable {
        runFlushSensorTest(Sensor.TYPE_ROTATION_VECTOR, RATE_50HZ, BATCHING_PERIOD);
    }

    public void testMagneticFieldUncalibrated_fastest_batching() throws Throwable {
        runBatchingSensorTest(Sensor.TYPE_MAGNETIC_FIELD_UNCALIBRATED, RATE_FASTEST, BATCHING_PERIOD);
    }

    public void testMagneticFieldUncalibrated_50hz_batching() throws Throwable {
        runBatchingSensorTest(Sensor.TYPE_MAGNETIC_FIELD_UNCALIBRATED, RATE_50HZ, BATCHING_PERIOD);
    }

    public void testMagneticFieldUncalibrated_fastest_flush() throws Throwable {
        runFlushSensorTest(Sensor.TYPE_MAGNETIC_FIELD_UNCALIBRATED, RATE_FASTEST, BATCHING_PERIOD);
    }

    public void testMagneticFieldUncalibrated_50hz_flush() throws Throwable {
        runFlushSensorTest(Sensor.TYPE_MAGNETIC_FIELD_UNCALIBRATED, RATE_50HZ, BATCHING_PERIOD);
    }

    public void testGameRotationVector_fastest_batching() throws Throwable {
        runBatchingSensorTest(Sensor.TYPE_GAME_ROTATION_VECTOR, RATE_FASTEST, BATCHING_PERIOD);
    }

    public void testGameRotationVector_50hz_batching() throws Throwable {
        runBatchingSensorTest(Sensor.TYPE_GAME_ROTATION_VECTOR, RATE_50HZ, BATCHING_PERIOD);
    }

    public void testGameRotationVector_fastest_flush() throws Throwable {
        runFlushSensorTest(Sensor.TYPE_GAME_ROTATION_VECTOR, RATE_FASTEST, BATCHING_PERIOD);
    }

    public void testGameRotationVector_50hz_flush() throws Throwable {
        runFlushSensorTest(Sensor.TYPE_GAME_ROTATION_VECTOR, RATE_50HZ, BATCHING_PERIOD);
    }

    public void testGyroscopeUncalibrated_fastest_batching() throws Throwable {
        runBatchingSensorTest(Sensor.TYPE_GYROSCOPE_UNCALIBRATED, RATE_FASTEST, BATCHING_PERIOD);
    }

    public void testGyroscopeUncalibrated_50hz_batching() throws Throwable {
        runBatchingSensorTest(Sensor.TYPE_GYROSCOPE_UNCALIBRATED, RATE_50HZ, BATCHING_PERIOD);
    }

    public void testGyroscopeUncalibrated_fastest_flush() throws Throwable {
        runFlushSensorTest(Sensor.TYPE_GYROSCOPE_UNCALIBRATED, RATE_FASTEST, BATCHING_PERIOD);
    }

    public void testGyroscopeUncalibrated_50hz_flush() throws Throwable {
        runFlushSensorTest(Sensor.TYPE_GYROSCOPE_UNCALIBRATED, RATE_50HZ, BATCHING_PERIOD);
    }

    public void testLinearAcceleration_fastest_batching() throws Throwable {
        runBatchingSensorTest(Sensor.TYPE_LINEAR_ACCELERATION, RATE_FASTEST, BATCHING_PERIOD);
    }

    public void testLinearAcceleration_50hz_batching() throws Throwable {
        runBatchingSensorTest(Sensor.TYPE_LINEAR_ACCELERATION, RATE_50HZ, BATCHING_PERIOD);
    }

    public void testLinearAcceleration_fastest_flush() throws Throwable {
        runFlushSensorTest(Sensor.TYPE_LINEAR_ACCELERATION, RATE_FASTEST, BATCHING_PERIOD);
    }

    public void testLinearAcceleration_50hz_flush() throws Throwable {
        runFlushSensorTest(Sensor.TYPE_LINEAR_ACCELERATION, RATE_50HZ, BATCHING_PERIOD);
    }

    public void testGeomagneticRotationVector_fastest_batching() throws Throwable {
        runBatchingSensorTest(Sensor.TYPE_GEOMAGNETIC_ROTATION_VECTOR, RATE_FASTEST, BATCHING_PERIOD);
    }

    public void testGeomagneticRotationVector_50hz_batching() throws Throwable {
        runBatchingSensorTest(Sensor.TYPE_GEOMAGNETIC_ROTATION_VECTOR, RATE_50HZ, BATCHING_PERIOD);
    }

    public void testGeomagneticRotationVector_fastest_flush() throws Throwable {
        runFlushSensorTest(Sensor.TYPE_GEOMAGNETIC_ROTATION_VECTOR, RATE_FASTEST, BATCHING_PERIOD);
    }

    public void testGeomagneticRotationVector_50hz_flush() throws Throwable {
        runFlushSensorTest(Sensor.TYPE_GEOMAGNETIC_ROTATION_VECTOR, RATE_50HZ, BATCHING_PERIOD);
    }

    private void runBatchingSensorTest(int sensorType, int rateUs, int maxBatchReportLatencySec)
            throws Throwable {
        int maxBatchReportLatencyUs = (int) TimeUnit.SECONDS.toMicros(maxBatchReportLatencySec);
        int testDurationSec = maxBatchReportLatencySec + BATCHING_PADDING_TIME_S;

        TestSensorEnvironment environment = new TestSensorEnvironment(
                getContext(),
                sensorType,
                shouldEmulateSensorUnderLoad(),
                rateUs,
                maxBatchReportLatencyUs);
        TestSensorOperation operation =
                TestSensorOperation.createOperation(environment, testDurationSec, TimeUnit.SECONDS);

        operation.addVerification(
                EventBasicVerification.getDefault(
                        environment, TimeUnit.SECONDS.toMicros(testDurationSec)
                )
        );

        executeTest(environment, operation, false /* flushExpected */);
    }

    private void runFlushSensorTest(int sensorType, int rateUs, int maxBatchReportLatencySec)
            throws Throwable {
        int maxBatchReportLatencyUs = (int) TimeUnit.SECONDS.toMicros(maxBatchReportLatencySec);
        int flushDurationSec = maxBatchReportLatencySec / 2;

        TestSensorEnvironment environment = new TestSensorEnvironment(
                getContext(),
                sensorType,
                shouldEmulateSensorUnderLoad(),
                rateUs,
                maxBatchReportLatencyUs);
        TestSensorOperation operation = TestSensorOperation
                .createFlushOperation(environment, flushDurationSec, TimeUnit.SECONDS);

        executeTest(environment, operation, true /* flushExpected */);
    }

    private void executeTest(
            TestSensorEnvironment environment,
            TestSensorOperation operation,
            boolean flushExpected) throws Throwable {
        SensorCtsHelper.sleep(3, TimeUnit.SECONDS);
        operation.addDefaultVerifications();

        try {
            operation.execute(getCurrentTestNode());
        } finally {
            SensorStats stats = operation.getStats();
            stats.log(TAG);

            String sensorRate;
            if (environment.getRequestedSamplingPeriodUs() == SensorManager.SENSOR_DELAY_FASTEST) {
                sensorRate = "fastest";
            } else {
                sensorRate = String.format("%.0fhz", environment.getFrequencyHz());
            }
            String batching = environment.getMaxReportLatencyUs() > 0 ? "_batching" : "";
            String flush = flushExpected ? "_flush" : "";
            String fileName = String.format(
                    "batching_%s_%s%s%s.txt",
                    SensorStats.getSanitizedSensorName(environment.getSensor()),
                    sensorRate,
                    batching,
                    flush);
            stats.logToFile(fileName);
        }
    }
}
