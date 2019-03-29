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

import java.util.HashMap;
import java.util.Map;

/**
 * A {@link ISensorVerification} which verifies that the means matches the expected measurement.
 */
public class MeanVerification extends AbstractMeanVerification {
    public static final String PASSED_KEY = "mean_passed";

    // sensorType: {expected, threshold}
    private static final Map<Integer, ExpectedValuesAndThresholds> DEFAULTS
        = new HashMap<Integer, ExpectedValuesAndThresholds>(5);
    static {
        // Use a method so that the @deprecation warning can be set for that method only
        setDefaults();
    }

    private final float[] mExpected;
    private final float[] mUpperThresholds;
    private final float[] mLowerThresholds;

    /**
     * Construct a {@link MeanVerification}
     *
     * @param expected the expected values
     * @param upperThresholds the upper thresholds
     * @param lowerThresholds the lower thresholds
     */
    public MeanVerification(float[] expected, float[] upperThresholds, float[] lowerThresholds) {
        mExpected = expected;
        mUpperThresholds = upperThresholds;
        mLowerThresholds = lowerThresholds;
    }

    /**
     * Get the default {@link MeanVerification} for a sensor.
     *
     * @param environment the test environment
     * @return the verification or null if the verification does not apply to the sensor.
     */
    public static MeanVerification getDefault(TestSensorEnvironment environment) {
        int sensorType = environment.getSensor().getType();
        if (!DEFAULTS.containsKey(sensorType)) {
            return null;
        }
        float[] expected = DEFAULTS.get(sensorType).mExpectedValues;
        float[] upperThresholds = DEFAULTS.get(sensorType).mUpperThresholds;
        float[] lowerThresholds = DEFAULTS.get(sensorType).mLowerThresholds;
        return new MeanVerification(expected, upperThresholds, lowerThresholds);
    }

    /**
     * Verify that the mean is in the acceptable range. Add {@value #PASSED_KEY} and
     * {@value SensorStats#MEAN_KEY} keys to {@link SensorStats}.
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
        if (getCount() < 1) {
            stats.addValue(PASSED_KEY, true);
            return;
        }

        float[] means = getMeans();

        boolean failed = false;
        for (int i = 0; i < means.length; i++) {
            if (means[i]  > mExpected[i] + mUpperThresholds[i]) {
                failed = true;
            }
            if (means[i] < mExpected[i] - mLowerThresholds[i]) {
                failed = true;
            }
        }

        stats.addValue(PASSED_KEY, !failed);
        stats.addValue(SensorStats.MEAN_KEY, means);

        if (failed) {
            Assert.fail(String.format("Mean out of range: mean=%s (expected %s)",
                    SensorCtsHelper.formatFloatArray(means),
                    SensorCtsHelper.formatFloatArray(mExpected)));
        }
    }

    @Override
    public MeanVerification clone() {
        return new MeanVerification(mExpected, mUpperThresholds, mLowerThresholds);
    }

    @SuppressWarnings("deprecation")
    private static void setDefaults() {
        // Sensors that we don't want to test at this time but still want to record the values.
        // Gyroscope should be 0 for a static device
        DEFAULTS.put(Sensor.TYPE_GYROSCOPE,
            new ExpectedValuesAndThresholds(new float[]{0.0f, 0.0f, 0.0f},
                                            new float[]{Float.MAX_VALUE,
                                                        Float.MAX_VALUE,
                                                        Float.MAX_VALUE},
                                            new float[]{Float.MAX_VALUE,
                                                        Float.MAX_VALUE,
                                                        Float.MAX_VALUE}));
        // Pressure will not be exact in a controlled environment but should be relatively close to
        // sea level (400HPa and 200HPa are very lax thresholds).
        // Second values should always be 0.
        DEFAULTS.put(Sensor.TYPE_PRESSURE,
            new ExpectedValuesAndThresholds(new float[]{SensorManager.PRESSURE_STANDARD_ATMOSPHERE,
                                                        0.0f,
                                                        0.0f},
                                            new float[]{100f,
                                                        Float.MAX_VALUE,
                                                        Float.MAX_VALUE},
                                            new float[]{400f,
                                                        Float.MAX_VALUE,
                                                        Float.MAX_VALUE}));
        // Linear acceleration should be 0 in all directions for a static device
        DEFAULTS.put(Sensor.TYPE_LINEAR_ACCELERATION,
            new ExpectedValuesAndThresholds(new float[]{0.0f, 0.0f, 0.0f},
                                            new float[]{Float.MAX_VALUE,
                                                        Float.MAX_VALUE,
                                                        Float.MAX_VALUE},
                                            new float[]{Float.MAX_VALUE,
                                                        Float.MAX_VALUE,
                                                        Float.MAX_VALUE}));
        // Game rotation vector should be (0, 0, 0, 1, 0) for a static device
        DEFAULTS.put(Sensor.TYPE_GAME_ROTATION_VECTOR,
            new ExpectedValuesAndThresholds(new float[]{0.0f, 0.0f, 0.0f, 1.0f, 0.0f},
                                            new float[]{Float.MAX_VALUE,
                                                        Float.MAX_VALUE,
                                                        Float.MAX_VALUE,
                                                        Float.MAX_VALUE,
                                                        Float.MAX_VALUE},
                                            new float[]{Float.MAX_VALUE,
                                                        Float.MAX_VALUE,
                                                        Float.MAX_VALUE,
                                                        Float.MAX_VALUE,
                                                        Float.MAX_VALUE}));
        // Uncalibrated gyroscope should be 0 for a static device but allow a bigger threshold
        DEFAULTS.put(Sensor.TYPE_GYROSCOPE_UNCALIBRATED,
            new ExpectedValuesAndThresholds(new float[]{0.0f, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f},
                                            new float[]{Float.MAX_VALUE,
                                                        Float.MAX_VALUE,
                                                        Float.MAX_VALUE,
                                                        Float.MAX_VALUE,
                                                        Float.MAX_VALUE,
                                                        Float.MAX_VALUE},
                                            new float[]{Float.MAX_VALUE,
                                                        Float.MAX_VALUE,
                                                        Float.MAX_VALUE,
                                                        Float.MAX_VALUE,
                                                        Float.MAX_VALUE,
                                                        Float.MAX_VALUE}));
    }

    private static final class ExpectedValuesAndThresholds {
        private float[] mExpectedValues;
        private float[] mUpperThresholds;
        private float[] mLowerThresholds;
        private ExpectedValuesAndThresholds(float[] expectedValues,
                                            float[] upperThresholds,
                                            float[] lowerThresholds) {
            mExpectedValues = expectedValues;
            mUpperThresholds = upperThresholds;
            mLowerThresholds = lowerThresholds;
        }
    }
}
