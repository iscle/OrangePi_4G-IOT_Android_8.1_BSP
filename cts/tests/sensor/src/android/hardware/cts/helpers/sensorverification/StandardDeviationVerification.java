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

package android.hardware.cts.helpers.sensorverification;

import junit.framework.Assert;

import android.content.Context;
import android.content.pm.PackageManager;
import android.hardware.Sensor;
import android.hardware.cts.helpers.SensorStats;
import android.hardware.cts.helpers.TestSensorEnvironment;
import android.hardware.cts.helpers.TestSensorEvent;
import android.hardware.cts.helpers.SensorCtsHelper;
import android.util.Log;

import java.util.concurrent.TimeUnit;
import java.util.HashMap;
import java.util.Map;

/**
 * A {@link ISensorVerification} which verifies that the standard deviations is within the expected
 * range.
 */
public class StandardDeviationVerification extends AbstractSensorVerification {
    public static final String PASSED_KEY = "standard_deviation_passed";

    // sensorType: threshold
    private static final Map<Integer, float[]> DEFAULTS = new HashMap<Integer, float[]>(12);
    static {
        // Use a method so that the @deprecation warning can be set for that method only
        setDefaults();
    }

    private final float[] mThreshold;

    private float[] mMeans = null;
    private float[] mM2s = null;
    private int mCount = 0;

    /**
     * Construct a {@link StandardDeviationVerification}
     *
     * @param threshold the thresholds
     */
    public StandardDeviationVerification(float[] threshold) {
        mThreshold = threshold;
    }

    /**
     * Get the default {@link StandardDeviationVerification} for a sensor.
     *
     * @param environment the test environment
     * @return the verification or null if the verification does not apply to the sensor.
     */
    public static StandardDeviationVerification getDefault(TestSensorEnvironment environment) {
        int sensorType = environment.getSensor().getType();
        float graceFactorAccelGyro = 2.0f;
        float graceFactorMagPressure = 4.0f;
        float currOperatingFreq = (float) environment.getFrequencyHz();
        float maxBandWidth = (float)SensorCtsHelper.getFrequency(
                environment.getSensor().getMinDelay(), TimeUnit.MICROSECONDS);
        float minBandWidth = (float) SensorCtsHelper.getFrequency(
                environment.getSensor().getMaxDelay(), TimeUnit.MICROSECONDS);

        if (Float.isInfinite(currOperatingFreq)) {
            currOperatingFreq = maxBandWidth;
        }

        if (currOperatingFreq > maxBandWidth && !Float.isInfinite(maxBandWidth)) {
            currOperatingFreq = maxBandWidth;
        }

        if (currOperatingFreq < minBandWidth && !Float.isInfinite(minBandWidth)) {
            currOperatingFreq = minBandWidth;
        }

        float mAccelNoise = (float)(graceFactorAccelGyro * Math.sqrt(currOperatingFreq) *
                (9.81 * 0.0004));
        float mGyroNoise = (float)(graceFactorAccelGyro * Math.sqrt(currOperatingFreq) *
                (Math.PI/180.0 * 0.014));
        float mMagNoise = (float)((graceFactorMagPressure) * 0.5); // Allow extra grace for mag
        float mPressureNoise = (float)(graceFactorMagPressure * 0.02 *
                (float)Math.sqrt(currOperatingFreq)); // Allow extra grace for pressure

        if (!DEFAULTS.containsKey(sensorType)) {
            return null;
        }
        boolean hasHifiSensors = environment.getContext().getPackageManager().hasSystemFeature(
                PackageManager.FEATURE_HIFI_SENSORS);

        if (hasHifiSensors) {

            DEFAULTS.put(Sensor.TYPE_ACCELEROMETER, new float[]{mAccelNoise, mAccelNoise, mAccelNoise});
            // Max gyro deviation: 0.014°/s/√Hz
            DEFAULTS.put(Sensor.TYPE_GYROSCOPE,
                    new float[]{mGyroNoise, mGyroNoise, mGyroNoise});
            // Max magnetometer deviation: 0.1uT/√Hz
            DEFAULTS.put(Sensor.TYPE_MAGNETIC_FIELD, new float[]{mMagNoise, mMagNoise, mMagNoise});
            // Max pressure deviation: 2Pa/√Hz
            DEFAULTS.put(Sensor.TYPE_PRESSURE, new float[]{mPressureNoise});
        }
        return new StandardDeviationVerification(DEFAULTS.get(sensorType));
    }

    /**
     * Verify that the standard deviation is in the acceptable range. Add {@value #PASSED_KEY} and
     * {@value SensorStats#STANDARD_DEVIATION_KEY} keys to {@link SensorStats}.
     *
     * @throws AssertionError if the verification failed.
     */
    @Override
    public void verify(TestSensorEnvironment environment, SensorStats stats) {
        verify(stats);
    }

    /**
     * Visible for unit tests only.
     */
    void verify(SensorStats stats) {
        if (mCount < 2) {
            stats.addValue(PASSED_KEY, true);
            return;
        }

        float[] stdDevs = new float[mM2s.length];
        for (int i = 0; i < mM2s.length; i++) {
            stdDevs[i] = (float) Math.sqrt(mM2s[i] / (mCount - 1));
        }

        boolean failed = false;
        StringBuilder stddevSb = new StringBuilder();
        StringBuilder expectedSb = new StringBuilder();

        if (stdDevs.length > 1) {
            stddevSb.append("(");
            expectedSb.append("(");
        }
        for (int i = 0; i < stdDevs.length; i++) {
            if (stdDevs[i] > mThreshold[i]) {
                failed = true;
            }
            stddevSb.append(String.format("%.6f", stdDevs[i]));
            if (i != stdDevs.length - 1) stddevSb.append(", ");
            expectedSb.append(String.format("<%.6f", mThreshold[i]));
            if (i != stdDevs.length - 1) expectedSb.append(", ");
        }
        if (stdDevs.length > 1) {
            stddevSb.append(")");
            expectedSb.append(")");
        }

        stats.addValue(PASSED_KEY, !failed);
        stats.addValue(SensorStats.STANDARD_DEVIATION_KEY, stdDevs);

        if (failed) {
            Assert.fail(String.format("Standard deviation out of range: stddev=%s (expected %s)",
                    stddevSb.toString(), expectedSb.toString()));
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public StandardDeviationVerification clone() {
        return new StandardDeviationVerification(mThreshold);
    }

    /**
     * {@inheritDoc}
     * <p>
     * Computes the standard deviation using
     * <a href="http://en.wikipedia.org/wiki/Algorithms_for_calculating_variance#On-line_algorithm">
     * Welford's algorith</a>.
     * </p>
     */
    @Override
    protected void addSensorEventInternal(TestSensorEvent event) {
        if (mMeans == null || mM2s == null) {
            mMeans = new float[event.values.length];
            mM2s = new float[event.values.length];
        }

        Assert.assertEquals(mMeans.length, event.values.length);
        Assert.assertEquals(mM2s.length, event.values.length);

        mCount++;

        for (int i = 0; i < event.values.length; i++) {
            float delta = event.values[i] - mMeans[i];
            mMeans[i] += delta / mCount;
            mM2s[i] += delta * (event.values[i] - mMeans[i]);
        }
    }

    @SuppressWarnings("deprecation")
    private static void setDefaults() {
        DEFAULTS.put(Sensor.TYPE_ACCELEROMETER, new float[]{1.0f, 1.0f, 1.0f});
        DEFAULTS.put(Sensor.TYPE_GYROSCOPE, new float[]{0.5f, 0.5f, 0.5f});
        // Sensors that we don't want to test at this time but still want to record the values.
        DEFAULTS.put(Sensor.TYPE_MAGNETIC_FIELD,
                new float[]{Float.MAX_VALUE, Float.MAX_VALUE, Float.MAX_VALUE});
        DEFAULTS.put(Sensor.TYPE_ORIENTATION,
                new float[]{Float.MAX_VALUE, Float.MAX_VALUE, Float.MAX_VALUE});
        DEFAULTS.put(Sensor.TYPE_PRESSURE,
                new float[]{Float.MAX_VALUE, Float.MAX_VALUE, Float.MAX_VALUE});
        DEFAULTS.put(Sensor.TYPE_GRAVITY,
                new float[]{Float.MAX_VALUE, Float.MAX_VALUE, Float.MAX_VALUE});
        DEFAULTS.put(Sensor.TYPE_LINEAR_ACCELERATION,
                new float[]{Float.MAX_VALUE, Float.MAX_VALUE, Float.MAX_VALUE});
        DEFAULTS.put(Sensor.TYPE_ROTATION_VECTOR,
                new float[]{Float.MAX_VALUE, Float.MAX_VALUE, Float.MAX_VALUE, Float.MAX_VALUE,
                Float.MAX_VALUE});
        DEFAULTS.put(Sensor.TYPE_MAGNETIC_FIELD_UNCALIBRATED,
                new float[]{Float.MAX_VALUE, Float.MAX_VALUE, Float.MAX_VALUE, Float.MAX_VALUE,
                Float.MAX_VALUE, Float.MAX_VALUE});
        DEFAULTS.put(Sensor.TYPE_GAME_ROTATION_VECTOR,
                new float[]{Float.MAX_VALUE, Float.MAX_VALUE, Float.MAX_VALUE, Float.MAX_VALUE,
                Float.MAX_VALUE});
        DEFAULTS.put(Sensor.TYPE_GYROSCOPE_UNCALIBRATED,
                new float[]{Float.MAX_VALUE, Float.MAX_VALUE, Float.MAX_VALUE, Float.MAX_VALUE,
                Float.MAX_VALUE, Float.MAX_VALUE});
        DEFAULTS.put(Sensor.TYPE_GEOMAGNETIC_ROTATION_VECTOR,
                new float[]{Float.MAX_VALUE, Float.MAX_VALUE, Float.MAX_VALUE, Float.MAX_VALUE,
                Float.MAX_VALUE});
    }
}
