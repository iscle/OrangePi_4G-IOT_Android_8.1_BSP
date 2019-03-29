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
import android.text.TextUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * Test min-max frequency, max range parameters for sensors.
 *
 * <p>To execute these test cases, the following command can be used:</p>
 * <pre>
 * adb shell am instrument -e class android.hardware.cts.SensorParameterRangeTest \
 *     -w android.hardware.cts/android.test.AndroidJUnitRunner
 * </pre>
 */
public class SensorParameterRangeTest extends SensorTestCase {

    private static final double ACCELEROMETER_MAX_RANGE = 8 * 9.80; // 8G minus a slop
    private static final double ACCELEROMETER_MIN_FREQUENCY = 12.50;
    private static final int ACCELEROMETER_MAX_FREQUENCY = 200;

    private static final double GYRO_MAX_RANGE = 1000/57.295 - 1.0; // 1000 degrees per sec minus a slop
    private static final double GYRO_MIN_FREQUENCY = 12.50;
    private static final double GYRO_MAX_FREQUENCY = 200.0;

    private static final int MAGNETOMETER_MAX_RANGE = 900;   // micro telsa
    private static final double MAGNETOMETER_MIN_FREQUENCY = 5.0;
    private static final double MAGNETOMETER_MAX_FREQUENCY = 50.0;

    private static final double PRESSURE_MAX_RANGE = 1100.0;     // hecto-pascal
    private static final double PRESSURE_MIN_FREQUENCY = 1.0;
    private static final double PRESSURE_MAX_FREQUENCY = 10.0;

    // Note these FIFO minimum constants come from the CCD.  In version
    // 6.0 of the CCD, these are in section 7.3.9.
    private static final int ACCELEROMETER_MIN_FIFO_LENGTH = 3000;
    private static final int UNCAL_MAGNETOMETER_MIN_FIFO_LENGTH = 600;
    private static final int PRESSURE_MIN_FIFO_LENGTH = 300;
    private static final int GAME_ROTATION_VECTOR_MIN_FIFO_LENGTH = 300;
    private static final int PROXIMITY_SENSOR_MIN_FIFO_LENGTH = 100;
    private static final int STEP_DETECTOR_MIN_FIFO_LENGTH = 100;

    private boolean mHasHifiSensors;
    private boolean mVrModeHighPerformance;
    private SensorManager mSensorManager;

    @Override
    public void setUp() {
        PackageManager pm = getContext().getPackageManager();
        mSensorManager = (SensorManager) getContext().getSystemService(Context.SENSOR_SERVICE);
        mHasHifiSensors = pm.hasSystemFeature(PackageManager.FEATURE_HIFI_SENSORS);
        mVrModeHighPerformance = pm.hasSystemFeature(PackageManager.FEATURE_VR_MODE_HIGH_PERFORMANCE);
    }

    public void testAccelerometerRange() {
        checkSensorRangeAndFrequency(
                mSensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER),
                ACCELEROMETER_MAX_RANGE,
                ACCELEROMETER_MIN_FREQUENCY,
                ACCELEROMETER_MAX_FREQUENCY);
  }

  public void testGyroscopeRange() {
        checkSensorRangeAndFrequency(
                mSensorManager.getDefaultSensor(Sensor.TYPE_GYROSCOPE),
                GYRO_MAX_RANGE,
                GYRO_MIN_FREQUENCY,
                GYRO_MAX_FREQUENCY);
  }

    public void testMagnetometerRange() {
        checkSensorRangeAndFrequency(
                mSensorManager.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD),
                MAGNETOMETER_MAX_RANGE,
                MAGNETOMETER_MIN_FREQUENCY,
                MAGNETOMETER_MAX_FREQUENCY);
    }

    public void testPressureRange() {
        if (mHasHifiSensors) {
            checkSensorRangeAndFrequency(
                    mSensorManager.getDefaultSensor(Sensor.TYPE_PRESSURE),
                    PRESSURE_MAX_RANGE,
                    PRESSURE_MIN_FREQUENCY,
                    PRESSURE_MAX_FREQUENCY);
        }
    }

    private void checkSensorRangeAndFrequency(
          Sensor sensor, double maxRange, double minFrequency, double maxFrequency) {
        if (!mHasHifiSensors && !mVrModeHighPerformance) return;
        assertTrue(String.format("%s Range actual=%.2f expected=%.2f %s",
                    sensor.getName(), sensor.getMaximumRange(), maxRange,
                    SensorCtsHelper.getUnitsForSensor(sensor)),
                sensor.getMaximumRange() >= maxRange);
        double actualMinFrequency = SensorCtsHelper.getFrequency(sensor.getMaxDelay(),
                TimeUnit.MICROSECONDS);
        assertTrue(String.format("%s Min Frequency actual=%.2f expected=%.2fHz",
                    sensor.getName(), actualMinFrequency, minFrequency), actualMinFrequency <=
                minFrequency + 0.1);

        double actualMaxFrequency = SensorCtsHelper.getFrequency(sensor.getMinDelay(),
                TimeUnit.MICROSECONDS);
        assertTrue(String.format("%s Max Frequency actual=%.2f expected=%.2fHz",
                    sensor.getName(), actualMaxFrequency, maxFrequency), actualMaxFrequency >=
                maxFrequency - 0.1);
    }

    public void testAccelerometerFifoLength() throws Throwable {
        if (!mHasHifiSensors) return;
        checkMinFifoLength(Sensor.TYPE_ACCELEROMETER, ACCELEROMETER_MIN_FIFO_LENGTH);
    }

    public void testUncalMagnetometerFifoLength() throws Throwable {
        if (!mHasHifiSensors) return;
        checkMinFifoLength(
                Sensor.TYPE_MAGNETIC_FIELD_UNCALIBRATED,
                UNCAL_MAGNETOMETER_MIN_FIFO_LENGTH);
    }

    public void testPressureFifoLength() throws Throwable {
        if (!mHasHifiSensors) return;
        checkMinFifoLength(Sensor.TYPE_PRESSURE, PRESSURE_MIN_FIFO_LENGTH);
    }

    public void testGameRotationVectorFifoLength() throws Throwable {
        if (!mHasHifiSensors) return;
        checkMinFifoLength(Sensor.TYPE_GAME_ROTATION_VECTOR, GAME_ROTATION_VECTOR_MIN_FIFO_LENGTH);
    }

    public void testProximityFifoLength() throws Throwable {
        if (!mHasHifiSensors) return;
        checkMinFifoLength(Sensor.TYPE_PROXIMITY, PROXIMITY_SENSOR_MIN_FIFO_LENGTH);
    }

    public void testStepDetectorFifoLength() throws Throwable {
        if (!mHasHifiSensors) return;
        checkMinFifoLength(Sensor.TYPE_STEP_DETECTOR, STEP_DETECTOR_MIN_FIFO_LENGTH);
    }

    private void checkMinFifoLength(int sensorType, int minRequiredLength) {
        Sensor sensor = mSensorManager.getDefaultSensor(sensorType);
        assertTrue(String.format("sensor of type=%d (null)", sensorType), sensor != null);
        int reservedLength = sensor.getFifoReservedEventCount();
        assertTrue(String.format("Sensor=%s, min required fifo length=%d actual=%d",
                    sensor.getName(), minRequiredLength, reservedLength),
                    reservedLength >= minRequiredLength);
    }

    public void testStaticSensorId() {
        // all static sensors should have id of 0
        List<Sensor> sensors = mSensorManager.getSensorList(Sensor.TYPE_ALL);
        List<String> errors = new ArrayList<>();
        for (Sensor s : sensors) {
            int id = s.getId();
            if (id != 0) {
                errors.add(String.format("sensor \"%s\" has id %d", s.getName(), id));
            }
        }
        if (errors.size() > 0) {
            String message = "Static sensors should have id of 0, violations: < " +
                    TextUtils.join(", ", errors) + " >";
            fail(message);
        }
    }
}
