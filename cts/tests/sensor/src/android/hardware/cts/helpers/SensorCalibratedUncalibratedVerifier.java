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
 * limitations under the License
 */

package android.hardware.cts.helpers;

import junit.framework.Assert;

import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * A bundled sensor test operation/verification.
 *
 * It verifies the relationship between measurements from calibrated sensors and their corresponding
 * uncalibrated sensors comply to the following equation:
 *  calibrated = uncalibrated - bias
 */
// TODO: refactor into proper test operation/verification classes:
// currently this is the only verification that requires input from multiple sensors, and the class
// is factored into: operation, verification, and listener as other sensor test operations
public class SensorCalibratedUncalibratedVerifier {

    private final TestSensorManager mCalibratedSensorManager;
    private final TestSensorManager mUncalibratedSensorManager;
    private final TestSensorEventListener mCalibratedTestListener;
    private final TestSensorEventListener mUncalibratedTestListener;
    private final float mThreshold;

    public SensorCalibratedUncalibratedVerifier(
            TestSensorEnvironment calibratedEnvironment,
            TestSensorEnvironment uncalibratedEnvironment,
            float threshold) {
        mCalibratedSensorManager = new TestSensorManager(calibratedEnvironment);
        mUncalibratedSensorManager = new TestSensorManager(uncalibratedEnvironment);
        mCalibratedTestListener = new TestSensorEventListener(calibratedEnvironment);
        mUncalibratedTestListener = new TestSensorEventListener(uncalibratedEnvironment);
        mThreshold = threshold;
    }

    /**
     * Executes the operation: it collects the data and run verifications on it.
     */
    public void execute() throws Throwable {
        mCalibratedSensorManager.registerListener(mCalibratedTestListener);
        mUncalibratedSensorManager.registerListener(mUncalibratedTestListener);

        Thread.sleep(TimeUnit.SECONDS.toMillis(10));

        mCalibratedSensorManager.unregisterListener();
        mUncalibratedSensorManager.unregisterListener();

        verifyMeasurements(
                mCalibratedTestListener.getCollectedEvents(),
                mUncalibratedTestListener.getCollectedEvents(),
                mThreshold);
    }

    private void verifyMeasurements(
            List<TestSensorEvent> calibratedEvents,
            List<TestSensorEvent> uncalibratedEvents,
            float threshold) {
        long measuredSamplingPeriodNs = SensorCtsHelper.getSamplingPeriodNs(calibratedEvents);
        long synchronizationPeriodNs = measuredSamplingPeriodNs / 2;
        int eventsValidated = 0;

        // TODO: this makes the algorithm O(n^2) when we could have it O(n), but it has little
        // impact on the overall test duration because the data collection is what takes the most
        // time
        for (TestSensorEvent calibratedEvent : calibratedEvents) {
            long calibratedTimestampNs = calibratedEvent.timestamp;
            long lowerTimestampThresholdNs = calibratedTimestampNs - synchronizationPeriodNs;
            long upperTimestampThresholdNs = calibratedTimestampNs + synchronizationPeriodNs;

            for (TestSensorEvent uncalibratedEvent : uncalibratedEvents) {
                long uncalibratedTimestampNs = uncalibratedEvent.timestamp;
                if (uncalibratedTimestampNs > lowerTimestampThresholdNs
                        && uncalibratedTimestampNs < upperTimestampThresholdNs) {
                    // perform validation
                    verifyCalibratedUncalibratedPair(
                            calibratedEvent,
                            uncalibratedEvent,
                            threshold);
                    ++eventsValidated;
                }
            }
        }

        String eventsValidatedMessage = String.format(
                "Expected to find at least one Calibrated/Uncalibrated event pair for validation."
                        + " Found=%d",
                eventsValidated);
        Assert.assertTrue(eventsValidatedMessage, eventsValidated > 0);
    }

    private void verifyCalibratedUncalibratedPair(
            TestSensorEvent calibratedEvent,
            TestSensorEvent uncalibratedEvent,
            float threshold) {
        for (int i = 0; i < 3; ++i) {
            float calibrated = calibratedEvent.values[i];
            float uncalibrated = uncalibratedEvent.values[i];
            float bias = uncalibratedEvent.values[i + 3];
            String message = String.format(
                    "Calibrated (%s) and Uncalibrated (%s) sensor readings are expected to satisfy:"
                            + " calibrated = uncalibrated - bias. Axis=%d, Calibrated=%s, "
                            + "Uncalibrated=%s, Bias=%s, Threshold=%s",
                    calibratedEvent.sensor.getName(),
                    uncalibratedEvent.sensor.getName(),
                    i,
                    calibrated,
                    uncalibrated,
                    bias,
                    threshold);
            Assert.assertEquals(message, calibrated, uncalibrated - bias, threshold);
        }
    }
}
