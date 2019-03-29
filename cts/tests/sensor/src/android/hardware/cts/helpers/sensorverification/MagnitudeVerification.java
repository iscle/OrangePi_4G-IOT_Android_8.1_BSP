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

import android.hardware.Sensor;
import android.hardware.SensorManager;
import android.hardware.cts.helpers.SensorCtsHelper;
import android.hardware.cts.helpers.SensorStats;
import android.hardware.cts.helpers.TestSensorEnvironment;
import android.hardware.cts.helpers.TestSensorEvent;

import java.util.HashMap;
import java.util.Map;

/**
 * A {@link ISensorVerification} which verifies that the mean of the magnitude of the sensors vector
 * is within the expected range.
 */
public class MagnitudeVerification extends AbstractSensorVerification {
    public static final String PASSED_KEY = "magnitude_passed";

    // sensorType: {expected, threshold}
    private static Map<Integer, Float[]> DEFAULTS = new HashMap<Integer, Float[]>(3);
    static {
        // Use a method so that the @deprecation warning can be set for that method only
        setDefaults();
    }

    private final float mExpected;
    private final float mThreshold;

    private float mSum = 0.0f;
    private int mCount = 0;

    /**
     * Construct a {@link MagnitudeVerification}
     *
     * @param expected the expected value
     * @param threshold the threshold
     */
    public MagnitudeVerification(float expected, float threshold) {
        mExpected = expected;
        mThreshold = threshold;
    }

    /**
     * Get the default {@link MagnitudeVerification} for a sensor.
     *
     * @param environment the test environment
     * @return the verification or null if the verification does not apply to the sensor.
     */
    public static MagnitudeVerification getDefault(TestSensorEnvironment environment) {
        int sensorType = environment.getSensor().getType();
        if (!DEFAULTS.containsKey(sensorType)) {
            return null;
        }
        Float expected = DEFAULTS.get(sensorType)[0];
        Float threshold = DEFAULTS.get(sensorType)[1];
        return new MagnitudeVerification(expected, threshold);
    }

    /**
     * Verify that the magnitude is in the acceptable range. Add {@value #PASSED_KEY} and
     * {@value SensorStats#MAGNITUDE_KEY} keys to {@link SensorStats}.
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
        if (mCount < 1) {
            stats.addValue(PASSED_KEY, true);
            return;
        }

        float mean = mSum / mCount;
        boolean failed = Math.abs(mean - mExpected) > mThreshold;

        stats.addValue(PASSED_KEY, !failed);
        stats.addValue(SensorStats.MAGNITUDE_KEY, mean);

        if (failed) {
            Assert.fail(String.format("Magnitude mean out of range: mean=%s (expected %s+/-%s)",
                    mean, mExpected, mThreshold));
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public MagnitudeVerification clone() {
        return new MagnitudeVerification(mExpected, mThreshold);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected void addSensorEventInternal(TestSensorEvent event) {
        mSum += SensorCtsHelper.getMagnitude(event.values);
        mCount++;
    }

    @SuppressWarnings("deprecation")
    private static void setDefaults() {
        DEFAULTS.put(Sensor.TYPE_ACCELEROMETER, new Float[]{SensorManager.STANDARD_GRAVITY, 1.5f});
        DEFAULTS.put(Sensor.TYPE_GYROSCOPE, new Float[]{0.0f, 1.5f});
        // Sensors that we don't want to test at this time but still want to record the values.
        DEFAULTS.put(Sensor.TYPE_GRAVITY,
                new Float[]{SensorManager.STANDARD_GRAVITY, Float.MAX_VALUE});
    }
}
