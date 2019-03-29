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

package android.hardware.cts.helpers.sensorverification;

import junit.framework.Assert;

import android.hardware.Sensor;
import android.hardware.cts.helpers.SensorCtsHelper;
import android.hardware.cts.helpers.SensorStats;
import android.hardware.cts.helpers.TestSensorEnvironment;
import android.hardware.cts.helpers.TestSensorEvent;
import android.util.Log;

import java.util.LinkedList;

/**
 * A {@link ISensorVerification} which verifies that each batch of events has the FIFO
 *  length within the 5% of the expected value.
 */
public class FifoLengthVerification extends AbstractSensorVerification {
    public static final String PASSED_KEY = "fifo_length_passed";

    private static double FIFO_LENGTH_TOLERANCE = 0.8;

    private final int mExpectedFifoLength;

    private int mIndex = 0;
    private LinkedList<Long> mRecvdTimeStampDiffs = new LinkedList<>();
    private long mPrevRecvdTimeStampMs = -1,  mExpectedReportLatencyUs;

    /**
     * Construct a {@link FifoLengthVerification}
     *
     * @param expectedLength the expected FIFO length for the batch.
     */
    public FifoLengthVerification(int expectedLength, long expectedReportLatencyUs) {
        mExpectedFifoLength = expectedLength;
        mExpectedReportLatencyUs = expectedReportLatencyUs;
    }

    /**
     * Get the default {@link FifoLengthVerification}.
     *
     * @param environment the test environment
     * @return the verification or null if the verification is not a continuous mode sensor.
     */
    public static FifoLengthVerification getDefault(
            TestSensorEnvironment environment) {
        if (environment.getSensor().getReportingMode() != Sensor.REPORTING_MODE_CONTINUOUS) {
            return null;
        }
        long expectedReportLatencyUs = environment.getMaxReportLatencyUs();
        long fifoReservedEventCount = environment.getSensor().getFifoReservedEventCount();
        int maximumExpectedSamplingPeriodUs = environment.getMaximumExpectedSamplingPeriodUs();
        if (fifoReservedEventCount > 0 && maximumExpectedSamplingPeriodUs != Integer.MAX_VALUE) {
            long fifoBasedReportLatencyUs = fifoReservedEventCount * maximumExpectedSamplingPeriodUs;
            // If the device goes into suspend mode and the sensor is a non wake-up sensor, the
            // FIFO will keep overwriting itself and the reportLatency will be equal to the time
            // it takes to fill up the FIFO.
            if (environment.isDeviceSuspendTest() && !environment.getSensor().isWakeUpSensor()) {
                expectedReportLatencyUs = fifoBasedReportLatencyUs;
            } else {
                // In this case the sensor under test is either a wake-up sensor OR it
                // is a non wake-up sensor but the device does not go into suspend.
                // So the expected delay of a sensor_event is the minimum of the
                // fifoBasedReportLatencyUs and the requested latency by the application.
                expectedReportLatencyUs = Math.min(expectedReportLatencyUs,
                        fifoBasedReportLatencyUs);
            }
        }

        return new FifoLengthVerification((int) fifoReservedEventCount, expectedReportLatencyUs);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void verify(TestSensorEnvironment environment, SensorStats stats) {
        if (mExpectedFifoLength <= 0) {
            // the expected length isn't defined.
            stats.addValue(PASSED_KEY, "skipped (no fifo length requirements)");
            return;
        }
        int batchCount = 0;
        int maxBatchCount = 0;
        boolean success, endofbatch = false;
        long maxTsDiff = -1;
        for (long timestampDiff : mRecvdTimeStampDiffs) {
            if (maxTsDiff < timestampDiff) maxTsDiff = timestampDiff;
            // Any event that arrives within before 0.5*expectedReportLatency is considered
            // to be in the same batch of events, else it is considered as the beginning of a new
            // batch.
            if (timestampDiff < mExpectedReportLatencyUs/1000/2) {
                batchCount++;
            } else {
                endofbatch = true;
                maxBatchCount = (maxBatchCount >= batchCount) ? maxBatchCount : batchCount;
                batchCount = 0;
            }
        }
        Log.v("SensorFifoLengthVerification", "batchCount =" +batchCount + " mExpected=" +
                mExpectedFifoLength + " maxTsDiff=" + maxTsDiff + " expectedReportLatency=" +
                mExpectedReportLatencyUs/1000 + " recvdEventCount=" + mRecvdTimeStampDiffs.size());
        // Fifo length must be at least 80% of the advertized FIFO length.
        success = endofbatch && (batchCount >= mExpectedFifoLength * FIFO_LENGTH_TOLERANCE);

        stats.addValue(PASSED_KEY, success);
        stats.addValue(SensorStats.EVENT_FIFO_LENGTH, batchCount);

        if (!success) {
            StringBuilder sb = new StringBuilder();
            if (endofbatch) {
                sb.append(String.format("Fifo length verification error: Fifo length found=%d," +
                            "expected fifo length ~%d, maxReportLatencyObserved=%dms, " +
                            "expectedMaxReportLantency=%dms",
                            batchCount, mExpectedFifoLength, maxTsDiff,
                            mExpectedReportLatencyUs/1000));
            } else {
               sb.append(String.format("End of batch NOT observed maxReportLatencyObserved=%dms,"
                            + " expectedMaxReportLantency=%dms", maxTsDiff,
                            mExpectedReportLatencyUs/1000));
            }
            Assert.fail(sb.toString());
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public FifoLengthVerification clone() {
        return new FifoLengthVerification(mExpectedFifoLength, mExpectedReportLatencyUs);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected void addSensorEventInternal(TestSensorEvent event) {
        if (mPrevRecvdTimeStampMs == -1) {
            mPrevRecvdTimeStampMs = (long)event.receivedTimestamp/(1000 * 1000);
        } else {
            long currRecvdTimeStampMs = (long) event.receivedTimestamp/(1000 * 1000);
            mRecvdTimeStampDiffs.add(currRecvdTimeStampMs - mPrevRecvdTimeStampMs);
            mPrevRecvdTimeStampMs = currRecvdTimeStampMs;
        }
        mIndex++;
    }
}
