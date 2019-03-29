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
import android.hardware.cts.helpers.SensorCtsHelper;
import android.hardware.cts.helpers.SensorStats;
import android.hardware.cts.helpers.TestSensorEnvironment;
import android.hardware.cts.helpers.TestSensorEvent;
import android.util.Log;

import java.util.concurrent.TimeUnit;

/**
 * A {@link ISensorVerification} which verifies that the sensor frequency are within the expected
 * range.
 */
public class FrequencyVerification extends AbstractSensorVerification {
    public static final String PASSED_KEY = "frequency_passed";
    private static final String LOG_TAG = FrequencyVerification.class.getSimpleName();

    // Lowest acceptable frequency, as percentage of the requested one.
    private static final int DEFAULT_LOWER_THRESHOLD = 90;
    // Highest acceptable frequency, as percentage of the requested one.
    private static final int DEFAULT_UPPER_THRESHOLD = 220;

    private final double mLowerThresholdHz;
    private final double mUpperThresholdHz;

    private long mMinTimestamp = 0;
    private long mMaxTimestamp = 0;
    private int mCount = 0;

    /**
     * Construct a {@link FrequencyVerification}.
     *
     * @param lowerTheshold Lowest acceptable frequency Hz.
     * @param upperThreshold Highest acceptable frequency Hz.
     */
    public FrequencyVerification(double lowerTheshold, double upperThreshold) {
        mLowerThresholdHz = lowerTheshold;
        mUpperThresholdHz = upperThreshold;
    }

    /**
     * Get the default {@link FrequencyVerification} for a sensor.
     *
     * @param environment the test environment
     * @return the verification or null if the verification does not apply to the sensor.
     */
    public static FrequencyVerification getDefault(TestSensorEnvironment environment) {
        Sensor sensor = environment.getSensor();
        if (sensor.getReportingMode() != Sensor.REPORTING_MODE_CONTINUOUS) {
            return null;
        }

        Log.i(LOG_TAG, String.format(
                "Preparing frequency test for \"%s\" for which minDelay=%dus and maxDelay=%dus",
                sensor.getName(),
                sensor.getMinDelay(),
                sensor.getMaxDelay()));

        double minDelayUs = sensor.getMinDelay();
        double maxDelayUs = sensor.getMaxDelay();

        if (maxDelayUs <= 0) {
            // This sensor didn't report its maxDelay.
            // It might be only capable of producing its max rate.
            // Do as if it reported its minDelay as its maxDelay.
            Log.w(LOG_TAG, "This sensor (" + sensor.getName() + ") didn't report its maxDelay."
                    + "Please update it in the sensor HAL to avoid failures in coming "
                    + "android releases.");
            maxDelayUs = minDelayUs;
        }

        if (environment.isSensorSamplingRateOverloaded()) {
            maxDelayUs = minDelayUs;
        }

        // Convert the rateUs parameter into a delay in microseconds and rate in Hz.
        double delayUs = environment.getRequestedSamplingPeriodUs();

        double lowerExpectedHz = 0;
        double upperExpectedHz = 0;

        // If sensor reports its maxDelay, then
        // lowerExpectedHz = upperExpectedHz and minRate <= expectedHz <= maxRate.
        // If sensor do not report its maxDelay, then
        // lowerExpectedHz is as per request (obviously <= maxRate) & upperExpectedHz = maxRate.
        if (sensor.getMaxDelay() > 0) {
            delayUs = SensorCtsHelper.clamp(delayUs, minDelayUs, maxDelayUs);
            double expectedHz = SensorCtsHelper.getFrequency(delayUs, TimeUnit.MICROSECONDS);
            lowerExpectedHz = upperExpectedHz = expectedHz;
        } else {
            delayUs = Math.max(minDelayUs, delayUs);
            lowerExpectedHz = SensorCtsHelper.getFrequency(delayUs, TimeUnit.MICROSECONDS);
            upperExpectedHz = SensorCtsHelper.getFrequency(minDelayUs, TimeUnit.MICROSECONDS);
        }

        // Set the pass thresholds based on default multipliers.
        double lowerThresholdHz = lowerExpectedHz * DEFAULT_LOWER_THRESHOLD / 100;
        double upperThresholdHz = upperExpectedHz * DEFAULT_UPPER_THRESHOLD / 100;

        return new FrequencyVerification(lowerThresholdHz, upperThresholdHz);
    }

    /**
     * Verify that the frequency is correct. Add {@value #PASSED_KEY} and
     * {@value SensorStats#FREQUENCY_KEY} keys to {@link SensorStats}.
     *
     * @throws AssertionError if the verification failed.
     */
    @Override
    public void verify(TestSensorEnvironment environment, SensorStats stats) {
        if (mCount < 2) {
            stats.addValue(PASSED_KEY, true);
            return;
        }

        double measuredFrequencyHz = SensorCtsHelper.getFrequency(
                ((double) (mMaxTimestamp - mMinTimestamp)) / (mCount - 1), TimeUnit.NANOSECONDS);
        boolean failed = (measuredFrequencyHz <= mLowerThresholdHz
                || measuredFrequencyHz >= mUpperThresholdHz);

        stats.addValue(SensorStats.FREQUENCY_KEY, measuredFrequencyHz);
        stats.addValue(PASSED_KEY, !failed);
        String resultString = String.format(
                "Requested \"%s\" at %s (expecting between %.2fHz and %.2fHz, measured %.2fHz)",
                environment.getSensor().getName(),
                environment.getFrequencyString(),
                mLowerThresholdHz,
                mUpperThresholdHz,
                measuredFrequencyHz);

        if (failed) {
            Log.e(LOG_TAG, "Frequency test FAIL: " + resultString);
            Assert.fail(String.format("Frequency out of range: " + resultString));
        } else {
            Log.i(LOG_TAG, "Frequency test pass: " + resultString);
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public FrequencyVerification clone() {
        return new FrequencyVerification(mLowerThresholdHz, mUpperThresholdHz);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected void addSensorEventInternal(TestSensorEvent event) {
        if (mCount == 0) {
            mMinTimestamp = event.timestamp;
            mMaxTimestamp = event.timestamp;
        } else {
            if (mMinTimestamp > event.timestamp) {
                mMinTimestamp = event.timestamp;
            }
            if (mMaxTimestamp < event.timestamp) {
                mMaxTimestamp = event.timestamp;
            }
        }
        mCount++;
    }
}
