/*
 * Copyright (C) 2016 The Android Open Source Project
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

package android.hardware.cts.helpers.sensorverification;

import junit.framework.Assert;

import android.hardware.SensorEvent;
import android.hardware.cts.helpers.SensorStats;
import android.hardware.cts.helpers.TestSensorEnvironment;
import android.hardware.cts.helpers.TestSensorEvent;
import android.os.SystemClock;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import android.util.Log;

/**
 * A {@link ISensorVerification} which verifies that the timestamp of the {@link SensorEvent} is
 * synchronized with {@link SystemClock#elapsedRealtimeNanos()}, based on a given threshold.
 */
public class TimestampClockSourceVerification extends AbstractSensorVerification {
    public static final String TAG = "TimestampClockSourceVerification";
    public static final String PASSED_KEY = "timestamp_verification_passed";

    // number of indices to print in assertion message before truncating
    private static final int TRUNCATE_MESSAGE_LENGTH = 3;

    private static final long DEFAULT_THRESHOLD_NS = TimeUnit.MILLISECONDS.toNanos(1000);
    private static final float ALLOWED_LATENCY_ERROR = 0.1f; //10%

    private final ArrayList<TestSensorEvent> mCollectedEvents = new ArrayList<TestSensorEvent>();

    private long mMaximumLatencyNs;

    /**
     * Constructs an instance of {@link TimestampClockSourceVerification}.
     *
     * @param maxLatencyNs Maximum allowed timestamp delta between event timestamp and current time
     */
    public TimestampClockSourceVerification (
            long maxLatencyUs) {
        mMaximumLatencyNs = maxLatencyUs * 1000;
    }

    /**
     * Gets a default {@link TimestampClockSourceVerification}.
     *
     * @param environment The test environment
     * @return The verification or null if the verification is not supported in the given
     *         environment.
     */
    public static TimestampClockSourceVerification getDefault(
            TestSensorEnvironment environment) {
        long reportLatencyUs = environment.getMaxReportLatencyUs();
        long fifoMaxEventCount = environment.getSensor().getFifoMaxEventCount();
        int maximumExpectedSamplingPeriodUs = environment.getMaximumExpectedSamplingPeriodUs();
        if (fifoMaxEventCount > 0 && maximumExpectedSamplingPeriodUs != Integer.MAX_VALUE) {
            long fifoBasedReportLatencyUs = fifoMaxEventCount * maximumExpectedSamplingPeriodUs;
            // If the device goes into suspend mode and the sensor is a non wake-up sensor, the
            // FIFO will keep overwriting itself and the reportLatency will be equal to the time
            // it takes to fill up the FIFO.
            if (environment.isDeviceSuspendTest() && !environment.getSensor().isWakeUpSensor()) {
                reportLatencyUs = fifoBasedReportLatencyUs;
            } else {
                // In this case the sensor under test is either a wake-up sensor OR it
                // is a non wake-up sensor but the device does not go into suspend.
                // So the expected delay of a sensor_event is the minimum of the
                // fifoBasedReportLatencyUs and the requested latency by the application.
                reportLatencyUs = Math.min(reportLatencyUs, fifoBasedReportLatencyUs);
            }
        }
        // Add an additional filter delay which is a function of the samplingPeriod.
        long filterDelayUs = (long)(2.5 * maximumExpectedSamplingPeriodUs);

        long expectedSyncLatencyNs = TimeUnit.MICROSECONDS.toNanos(reportLatencyUs + filterDelayUs);

        return new TimestampClockSourceVerification(expectedSyncLatencyNs);
    }

    @Override
    public void verify(TestSensorEnvironment environment, SensorStats stats) {
        StringBuilder errorMessageBuilder =
                new StringBuilder(" Incorrect timestamp clock source failures: ");
        boolean success = false;
        int failuresCount = 0;
        List<IndexedEvent> failures;

        try {
            failures = verifyTimestampClockSource(errorMessageBuilder);
            failuresCount = failures.size();
            stats.addValue(SensorStats.EVENT_TIME_WRONG_CLOCKSOURCE_COUNT_KEY, failuresCount);
            stats.addValue(
                    SensorStats.EVENT_TIME_WRONG_CLOCKSOURCE_POSITIONS_KEY,
                    getIndexArray(failures));
            success = failures.isEmpty();
        } catch (Throwable e) {
            failuresCount++;
            stats.addValue(SensorStats.EVENT_TIME_WRONG_CLOCKSOURCE_COUNT_KEY, 0);
        }
        stats.addValue(PASSED_KEY, success);
        errorMessageBuilder.insert(0, failuresCount);
        Assert.assertTrue(errorMessageBuilder.toString(), success);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public TimestampClockSourceVerification clone() {
        return new TimestampClockSourceVerification(
                mMaximumLatencyNs);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected void addSensorEventInternal(TestSensorEvent event) {
        mCollectedEvents.add(event);
    }

    /**
     * Verifies timestamp clock source for each collected event
     *
     * @param builder A string builder to store error messaged found in the collected sensor events.
     * @return A list of events tha failed the verification.
     */
    private List<IndexedEvent> verifyTimestampClockSource(StringBuilder builder) throws Throwable {
        int collectedEventsCount = mCollectedEvents.size();
        ArrayList<IndexedEvent> failures = new ArrayList<IndexedEvent>();

        if (collectedEventsCount == 0) {
            if (failures.size() < TRUNCATE_MESSAGE_LENGTH) {
                builder.append("No events received !");
            }
            Assert.assertTrue("No events received !", false);
        }

        for (int i = 0; i < collectedEventsCount; ++i) {
            TestSensorEvent event = mCollectedEvents.get(i);
            long eventTimestampNs = event.timestamp;
            long receivedTimestampNs = event.receivedTimestamp;
            long upperThresholdNs = receivedTimestampNs;
            long lowerThresholdNs = receivedTimestampNs - mMaximumLatencyNs;

            if (eventTimestampNs < lowerThresholdNs || eventTimestampNs > upperThresholdNs) {
                if (failures.size() < TRUNCATE_MESSAGE_LENGTH) {
                    builder.append("position=").append(i);
                    builder.append(", timestamp=").append(String.format("%.2fms",
                                nanosToMillis(eventTimestampNs)));
                    builder.append(", expected=[").append(String.format("%.2fms",
                                nanosToMillis(lowerThresholdNs)));
                    builder.append(", ").append(String.format("%.2f]ms; ",
                                nanosToMillis(upperThresholdNs)));
                }
                failures.add(new IndexedEvent(i, event));
            }
        }
        if (failures.size() >= TRUNCATE_MESSAGE_LENGTH) {
            builder.append("more; ");
        }
        return failures;
    }
}
