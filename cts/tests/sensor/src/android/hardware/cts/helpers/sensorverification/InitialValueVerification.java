/*
 * Copyright (C) 2017 The Android Open Source Project
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

import android.hardware.Sensor;
import android.hardware.cts.helpers.SensorCtsHelper;
import android.hardware.cts.helpers.SensorStats;
import android.hardware.cts.helpers.TestSensorEnvironment;
import android.hardware.cts.helpers.TestSensorEvent;
import android.util.Pair;

import junit.framework.Assert;

import java.util.HashMap;
import java.util.Map;

/**
 * A {@link ISensorVerification} which verifies that there are no ramps when starting the
 * collection. To verify this, we compute the mean value at the beginning of the collection and
 * compare it to the mean value at the end of the collection.
 */
public class InitialValueVerification extends AbstractSensorVerification {
    public static final String PASSED_KEY = "initial_value_passed";
    // Default length of the initial window: 2 seconds in ns
    private static final long DEFAULT_INITIAL_WINDOW_LENGTH = 2_000_000_000L;

    // sensorType: max absolute delta between the two means and initial window length
    private static final Map<Integer, Pair<Float, Long>> DEFAULTS =
        new HashMap<Integer, Pair<Float, Long>>(12);

    static {
        // Use a method so that the @deprecation warning can be set for that method only
        setDefaults();
    }

    // First time stamp in nano seconds
    private long mFirstTimestamp;
    private float[] mInitialSum = null;
    private int mInitialCount = 0;
    private float[] mLaterSum = null;
    private int mLaterCount = 0;

    private final float mMaxAbsoluteDelta;
    private final long mInitialWindowLength;

    /**
     * Construct a {@link InitialValueVerification}
     *
     * @param maxAbsoluteDelta the acceptable max absolute delta between the two means.
     */
    public InitialValueVerification(float maxAbsoluteDelta, long initialWindowLength) {
        mMaxAbsoluteDelta = maxAbsoluteDelta;
        mInitialWindowLength = initialWindowLength;
    }

    /**
     * Get the default {@link InitialValueVerification} for a sensor.
     *
     * @param environment the test environment
     * @return the verification or null if the verification does not apply to the sensor.
     */
    public static InitialValueVerification getDefault(TestSensorEnvironment environment) {
        int sensorType = environment.getSensor().getType();
        if (!DEFAULTS.containsKey(sensorType)) {
            return null;
        }
        Pair<Float, Long> maxAbsoluteDeltaAndInitialWindowLength = DEFAULTS.get(sensorType);
        return new InitialValueVerification(maxAbsoluteDeltaAndInitialWindowLength.first,
                                            maxAbsoluteDeltaAndInitialWindowLength.second);
    }

    /**
     * Verify that the mean at the initial window and later are similar to each other. Add
     * {@value #PASSED_KEY}, {@value SensorStats#INITIAL_MEAN_KEY},
     * {@value SensorStats#LATER_MEAN_KEY} keys to {@link SensorStats}.
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
        if (mInitialCount == 0) {
            Assert.fail("Didn't collect any measurements");
        }
        if (mLaterCount == 0) {
            Assert.fail(String.format("Didn't collect any measurements after %dns",
                    mInitialWindowLength));
        }
        float[] initialMeans = new float[mInitialSum.length];
        float[] laterMeans = new float[mInitialSum.length];
        boolean success = true;
        for (int i = 0; i < mInitialSum.length; i++) {
            initialMeans[i] = mInitialSum[i] / mInitialCount;
            laterMeans[i] = mLaterSum[i] / mLaterCount;
            if (Math.abs(initialMeans[i] - laterMeans[i]) > mMaxAbsoluteDelta) {
                success = false;
            }
        }
        stats.addValue(SensorStats.INITIAL_MEAN_KEY, initialMeans);
        stats.addValue(SensorStats.LATER_MEAN_KEY, laterMeans);
        stats.addValue(PASSED_KEY, success);
        if (!success) {
            Assert.fail(String.format(
                    "Means too far from each other: initial means = %s,"
                            + "later means = %s, max allowed delta = %.2f",
                    SensorCtsHelper.formatFloatArray(initialMeans),
                    SensorCtsHelper.formatFloatArray(laterMeans),
                    mMaxAbsoluteDelta));
        }
    }

    /** {@inheritDoc} */
    @Override
    public InitialValueVerification clone() {
        return new InitialValueVerification(mMaxAbsoluteDelta, mInitialWindowLength);
    }

    /** {@inheritDoc} */
    @Override
    protected void addSensorEventInternal(TestSensorEvent event) {
        if (mInitialSum == null) {
            mFirstTimestamp = event.timestamp;
            mInitialSum = new float[event.values.length];
            mLaterSum = new float[event.values.length];
        }
        if (event.timestamp - mFirstTimestamp <= mInitialWindowLength) {
            for (int i = 0; i < event.values.length; i++) {
                mInitialSum[i] += event.values[i];
            }
            mInitialCount++;
        } else {
            for (int i = 0; i < event.values.length; i++) {
                mLaterSum[i] += event.values[i];
            }
            mLaterCount++;
        }
    }

    @SuppressWarnings("deprecation")
    private static void setDefaults() {
        DEFAULTS.put(Sensor.TYPE_ACCELEROMETER,
                new Pair<Float, Long>(Float.MAX_VALUE, DEFAULT_INITIAL_WINDOW_LENGTH));
        DEFAULTS.put(Sensor.TYPE_MAGNETIC_FIELD,
                new Pair<Float, Long>(Float.MAX_VALUE, DEFAULT_INITIAL_WINDOW_LENGTH));
        DEFAULTS.put(Sensor.TYPE_MAGNETIC_FIELD_UNCALIBRATED,
                new Pair<Float, Long>(Float.MAX_VALUE, DEFAULT_INITIAL_WINDOW_LENGTH));
        DEFAULTS.put(Sensor.TYPE_GYROSCOPE,
                new Pair<Float, Long>(Float.MAX_VALUE, DEFAULT_INITIAL_WINDOW_LENGTH));
        DEFAULTS.put(Sensor.TYPE_GYROSCOPE_UNCALIBRATED,
                new Pair<Float, Long>(Float.MAX_VALUE, DEFAULT_INITIAL_WINDOW_LENGTH));
        DEFAULTS.put(Sensor.TYPE_ORIENTATION,
                new Pair<Float, Long>(Float.MAX_VALUE, DEFAULT_INITIAL_WINDOW_LENGTH));
        // Very tight absolute delta for the barometer.
        DEFAULTS.put(Sensor.TYPE_PRESSURE,
                new Pair<Float, Long>(3f, DEFAULT_INITIAL_WINDOW_LENGTH));
        DEFAULTS.put(Sensor.TYPE_GRAVITY,
                new Pair<Float, Long>(Float.MAX_VALUE, DEFAULT_INITIAL_WINDOW_LENGTH));
        DEFAULTS.put(Sensor.TYPE_LINEAR_ACCELERATION,
                new Pair<Float, Long>(Float.MAX_VALUE, DEFAULT_INITIAL_WINDOW_LENGTH));
        DEFAULTS.put(Sensor.TYPE_ROTATION_VECTOR,
                new Pair<Float, Long>(Float.MAX_VALUE, DEFAULT_INITIAL_WINDOW_LENGTH));
        DEFAULTS.put(Sensor.TYPE_GAME_ROTATION_VECTOR,
                new Pair<Float, Long>(Float.MAX_VALUE, DEFAULT_INITIAL_WINDOW_LENGTH));
        DEFAULTS.put(Sensor.TYPE_GEOMAGNETIC_ROTATION_VECTOR,
                new Pair<Float, Long>(Float.MAX_VALUE, DEFAULT_INITIAL_WINDOW_LENGTH));
    }
}

