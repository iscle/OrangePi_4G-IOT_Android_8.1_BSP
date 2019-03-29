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

package android.hardware.cts;

import android.content.Context;
import android.content.pm.PackageManager;
import android.hardware.Sensor;
import android.hardware.SensorManager;
import android.hardware.cts.helpers.SensorCtsHelper;
import android.hardware.cts.helpers.TestSensorEnvironment;
import android.hardware.cts.helpers.sensoroperations.TestSensorOperation;
import android.hardware.cts.helpers.sensorverification.FifoLengthVerification;
import android.platform.test.annotations.Presubmit;

import java.util.concurrent.TimeUnit;

/**
 * Checks the minimum Hardware FIFO length for each of the Hardware sensor.
 * Further verifies if the advertised FIFO (Sensor.getFifoMaxEventCount()) is actually allocated
 * for the sensor.
 *
 */
public class SensorBatchingFifoTest extends SensorTestCase {
    private static final int SAMPLING_INTERVAL = 1000; /* every 1ms */
    private static final String TAG = "batching_fifo_test";

    private SensorManager mSensorManager;
    private boolean mHasHifiSensors;
    @Override
    protected void setUp() throws Exception {
        mSensorManager = (SensorManager) getContext().getSystemService(Context.SENSOR_SERVICE);
        mHasHifiSensors = getContext().getPackageManager().hasSystemFeature(
                PackageManager.FEATURE_HIFI_SENSORS);
    }

    @Presubmit
    public void testAccelerometerFifoLength() throws Throwable {
        if (!mHasHifiSensors) return;
        runBatchingSensorFifoTest(
                Sensor.TYPE_ACCELEROMETER,
                getReservedFifoLength(Sensor.TYPE_ACCELEROMETER));
    }

    public void testUncalMagnetometerFifoLength() throws Throwable {
        if (!mHasHifiSensors) return;
        runBatchingSensorFifoTest(
                Sensor.TYPE_MAGNETIC_FIELD_UNCALIBRATED,
                getReservedFifoLength(Sensor.TYPE_MAGNETIC_FIELD_UNCALIBRATED));
    }

    public void testPressureFifoLength() throws Throwable {
        if (!mHasHifiSensors) return;
        runBatchingSensorFifoTest(
                Sensor.TYPE_PRESSURE,
                getReservedFifoLength(Sensor.TYPE_PRESSURE));
    }

    public void testGameRotationVectorFifoLength() throws Throwable {
        if (!mHasHifiSensors) return;
        runBatchingSensorFifoTest(
                Sensor.TYPE_GAME_ROTATION_VECTOR,
                getReservedFifoLength(Sensor.TYPE_GAME_ROTATION_VECTOR));
    }

    private int getReservedFifoLength(int sensorType) {
        Sensor sensor = mSensorManager.getDefaultSensor(sensorType);
        assertTrue(String.format("sensor of type=%d (null)", sensorType), sensor != null);
        return sensor.getFifoReservedEventCount();
    }

    private void runBatchingSensorFifoTest(int sensorType, int fifoLength) throws Throwable {
        if (fifoLength == 0) {
            return;
        }
        Sensor sensor = mSensorManager.getDefaultSensor(sensorType);
        TestSensorEnvironment environment =  new TestSensorEnvironment(getContext(),
                sensor,
                false, /* sensorMightHaveMoreListeners */
                sensor.getMinDelay(),
                Integer.MAX_VALUE /*maxReportLatencyUs*/);

        int preFlushMs = 2000;  // 2 sec to make sure there is sample at the time of flush
        int postFlushMs = environment.getExpectedSamplingPeriodUs() * 100 /1000;
        int testFlushMs =
                environment.getSensor().getFifoReservedEventCount() *
                environment.getExpectedSamplingPeriodUs() / (int)(1000 / 1.2); // 120%

        TestSensorOperation op = TestSensorOperation.createFlushOperation(
                environment, new int [] { preFlushMs, testFlushMs, postFlushMs }, -1);

        op.addVerification(FifoLengthVerification.getDefault(environment));
        op.execute(getCurrentTestNode());
        op.getStats().log(TAG);
    }
}
