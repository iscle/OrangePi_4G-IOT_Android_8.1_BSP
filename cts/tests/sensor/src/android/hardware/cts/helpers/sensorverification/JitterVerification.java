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

import android.content.Context;
import android.content.pm.PackageManager;

import android.util.Log;
import android.hardware.Sensor;
import android.hardware.cts.helpers.SensorCtsHelper;
import android.hardware.cts.helpers.SensorStats;
import android.hardware.cts.helpers.TestSensorEnvironment;
import android.hardware.cts.helpers.TestSensorEvent;
import android.util.SparseIntArray;

import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import junit.framework.Assert;

/**
 * A {@link ISensorVerification} which verifies that the sensor jitter is in an acceptable range.
 */
public class JitterVerification extends AbstractSensorVerification {
    public static final String PASSED_KEY = "jitter_passed";

    // sensorType: threshold (% of expected period)
    private static final SparseIntArray DEFAULTS = new SparseIntArray(12);
    // Max allowed jitter in +/- sense (in percentage).
    private static final int GRACE_FACTOR = 2;
    private static final int THRESHOLD_PERCENT_FOR_HIFI_SENSORS = 1 * GRACE_FACTOR;

    // Margin sample intervals that considered outliers, lower and higher margin is discarded
    // before verification
    private static final float OUTLIER_MARGIN = 0.025f; //2.5%

    static {
        // Use a method so that the @deprecation warning can be set for that method only
        setDefaults();
    }

    private final float     mOutlierMargin;
    private final long      mThresholdNs;
    private final long      mExpectedPeriodNs; // for error message only
    private final List<Long> mTimestamps = new LinkedList<Long>();

    /**
     * Construct a {@link JitterVerification}
     *
     * @param thresholdAsPercentage the acceptable margin of error as a percentage
     */
    public JitterVerification(float outlierMargin, long thresholdNs, long expectedPeriodNs) {
        mExpectedPeriodNs = expectedPeriodNs;
        mOutlierMargin = outlierMargin;
        mThresholdNs = thresholdNs;
    }

    /**
     * Get the default {@link JitterVerification} for a sensor.
     *
     * @param environment the test environment
     * @return the verification or null if the verification does not apply to the sensor.
     */
    public static JitterVerification getDefault(TestSensorEnvironment environment) {
        int sensorType = environment.getSensor().getType();

        int thresholdPercent = DEFAULTS.get(sensorType, -1);
        if (thresholdPercent == -1) {
            return null;
        }
        boolean hasHifiSensors = environment.getContext().getPackageManager().hasSystemFeature(
                PackageManager.FEATURE_HIFI_SENSORS);
        if (hasHifiSensors) {
           thresholdPercent = THRESHOLD_PERCENT_FOR_HIFI_SENSORS;
        }

        long expectedPeriodNs = (long) environment.getExpectedSamplingPeriodUs() * 1000;
        long jitterThresholdNs = expectedPeriodNs * thresholdPercent * 2 / 100; // *2 is for +/-
        return new JitterVerification(OUTLIER_MARGIN, jitterThresholdNs, expectedPeriodNs);
    }

    /**
     * Verify that the 95th percentile of the jitter is in the acceptable range. Add
     * {@value #PASSED_KEY} and {@value SensorStats#JITTER_95_PERCENTILE_PERCENT_KEY} keys to
     * {@link SensorStats}.
     *
     * @throws AssertionError if the verification failed.
     */
    @Override
    public void verify(TestSensorEnvironment environment, SensorStats stats) {
        int timestampsCount = mTimestamps.size();
        if (timestampsCount < 2 || environment.isSensorSamplingRateOverloaded()) {
            // the verification is not reliable in environments under load
            stats.addValue(PASSED_KEY, true);
            return;
        }

        List<Long> deltas = getDeltaValues();
        List<Long> percentileValues =
                SensorCtsHelper.getPercentileValue(deltas, mOutlierMargin, 1 - mOutlierMargin);

        double normalizedRange =
                (double)(percentileValues.get(1) - percentileValues.get(0)) / mThresholdNs;

        double percentageJitter =
                (double)(percentileValues.get(1) - percentileValues.get(0)) /
                        mExpectedPeriodNs / 2 * 100; //one side variation comparing to sample time

        stats.addValue(SensorStats.JITTER_95_PERCENTILE_PERCENT_KEY, percentageJitter);

        boolean success = normalizedRange <= 1.0;
        stats.addValue(PASSED_KEY, success);

        if (!success) {
            String message = String.format(
                    "Jitter out of range: requested period = %dns, " +
                    "jitter min, max, range (95th percentile) = (%dns, %dns, %dns), " +
                    "jitter expected range <= %dns",
                    mExpectedPeriodNs,
                    percentileValues.get(0), percentileValues.get(1),
                    percentileValues.get(1) - percentileValues.get(0),
                    mThresholdNs);
            Assert.fail(message);
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public JitterVerification clone() {
        return new JitterVerification(mOutlierMargin, mThresholdNs, mExpectedPeriodNs);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected void addSensorEventInternal(TestSensorEvent event) {
        mTimestamps.add(event.timestamp);
    }

    /**
     * Get the list of delta values. Exposed for unit testing.
     */
    List<Long> getDeltaValues() {
        List<Long> deltas = new ArrayList<Long>(mTimestamps.size() - 1);
        for (int i = 1; i < mTimestamps.size(); i++) {
            deltas.add(mTimestamps.get(i) - mTimestamps.get(i - 1));
        }
        return deltas;
    }

    @SuppressWarnings("deprecation")
    private static void setDefaults() {
        DEFAULTS.put(Sensor.TYPE_ACCELEROMETER, Integer.MAX_VALUE);
        DEFAULTS.put(Sensor.TYPE_MAGNETIC_FIELD, Integer.MAX_VALUE);
        DEFAULTS.put(Sensor.TYPE_MAGNETIC_FIELD_UNCALIBRATED, Integer.MAX_VALUE);
        DEFAULTS.put(Sensor.TYPE_GYROSCOPE, Integer.MAX_VALUE);
        DEFAULTS.put(Sensor.TYPE_GYROSCOPE_UNCALIBRATED, Integer.MAX_VALUE);
        DEFAULTS.put(Sensor.TYPE_ORIENTATION, Integer.MAX_VALUE);
        DEFAULTS.put(Sensor.TYPE_PRESSURE, Integer.MAX_VALUE);
        DEFAULTS.put(Sensor.TYPE_GRAVITY, Integer.MAX_VALUE);
        DEFAULTS.put(Sensor.TYPE_LINEAR_ACCELERATION, Integer.MAX_VALUE);
        DEFAULTS.put(Sensor.TYPE_ROTATION_VECTOR, Integer.MAX_VALUE);
        DEFAULTS.put(Sensor.TYPE_GAME_ROTATION_VECTOR, Integer.MAX_VALUE);
        DEFAULTS.put(Sensor.TYPE_GEOMAGNETIC_ROTATION_VECTOR, Integer.MAX_VALUE);
    }
}
