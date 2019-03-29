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

package android.hardware.cts.helpers.sensorverification;

import junit.framework.Assert;

import com.android.compatibility.common.util.ApiLevelUtil;

import android.os.Build;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.cts.helpers.SensorStats;
import android.hardware.cts.helpers.TestSensorEnvironment;
import android.hardware.cts.helpers.TestSensorEvent;
import android.os.SystemClock;

import java.util.concurrent.TimeUnit;

/**
 * A {@link ISensorVerification} which verifies if the collected sensor events have any obvious
 * problems, such as no sample, wrong sensor type, etc.
 */
public class EventBasicVerification extends AbstractSensorVerification {

    public static final String PASSED_KEY = "event_basic_passed";
    // allowed time from registration to sensor start sampling
    private static final long ALLOWED_SENSOR_START_DELAY_US =
            TimeUnit.MILLISECONDS.toMicros(1000);

    // allowed time for entire sensor system to send sample to test app
    private static final long ALLOWED_SENSOR_EVENT_LATENCY_US =
            TimeUnit.MILLISECONDS.toMicros(1000);

    // mercy added for recently added test. remove this mercy factor for next letter release.
    private final float NUM_EVENT_MERCY_FACTOR; // 0~1, 0 means most strict

    private final long mExpectedMinNumEvent;
    private final Object mSensor;
    private long  mNumEvent;
    private boolean mWrongSensorObserved;

    /**
     * Constructs an instance of {@link EventBasicVerification}.
     *
     * @param maximumSynchronizationErrorNs The valid threshold for timestamp synchronization.
     * @param reportLatencyNs The latency on which batching events are received
     */
    public EventBasicVerification(
            long expectedMinNumEvent,
            Sensor sensor) {
        mExpectedMinNumEvent = expectedMinNumEvent;
        mSensor = sensor;

        mNumEvent = 0;
        mWrongSensorObserved = false;

        if (ApiLevelUtil.isAfter(Build.VERSION_CODES.M)) {
            NUM_EVENT_MERCY_FACTOR = 0;
        } else {
            NUM_EVENT_MERCY_FACTOR = 0.3f;
        }
    }

    /**
     * Gets a default {@link EventBasicVerification}.
     *
     * @param environment The test environment
     * @return The verification or null if the verification is not supported in the given
     *         environment.
     */
    public static EventBasicVerification getDefault(
            TestSensorEnvironment environment,
            long testDurationUs) {

        // The calculation is still OK if sampleUs is not the actual sensor hardware
        // sample period since the actual sample period by definition only goes smaller, which
        // result in more samples.
        long sampleUs = environment.getExpectedSamplingPeriodUs();

        long askedBatchUs = environment.getMaxReportLatencyUs();

        long reservedFifoUs = sampleUs * environment.getSensor().getFifoReservedEventCount(); //>=0

        // max() prevent loop-hole if HAL specify smaller max fifo than reserved fifo.
        long maximumFifoUs = Math.max(
                sampleUs * environment.getSensor().getFifoMaxEventCount(), reservedFifoUs); //>=0

        long effectiveDurationUs = Math.max(testDurationUs -
                Math.max(ALLOWED_SENSOR_START_DELAY_US, environment.getAllowedSensorStartDelay()) -
                ALLOWED_SENSOR_EVENT_LATENCY_US, 0);

        boolean isSingleSensorTest = !environment.isIntegrationTest();

        long expectedMinUs;
        if (isSingleSensorTest) {
            // When the sensor under test is the only one active, max fifo size is assumed to be
            // available.
            long expectedBatchUs = Math.min(maximumFifoUs, askedBatchUs);
            if (expectedBatchUs > 0) {
                // This sensor should be running in batching mode.
                expectedMinUs =
                        effectiveDurationUs / expectedBatchUs * expectedBatchUs
                        - expectedBatchUs / 5;
            } else {
                // streaming, allow actual rate to be as slow as 80% of the asked rate.
                expectedMinUs = effectiveDurationUs * 4 / 5;
            }
        } else {
            // More convoluted case. Batch size can vary from reserved fifo length to max fifo size.
            long minBatchUs = Math.min(reservedFifoUs, askedBatchUs);
            long maxBatchUs = Math.min(maximumFifoUs, askedBatchUs);

            // The worst scenario happens when the sensor batch time being just above half of the
            // test time, then the test can only receive one batch which halves the expected number
            // of samples. The expected number of samples received have a lower bound like the
            // figure below.
            //
            // expected samples
            //  ^
            //  |                          ______
            //  |\                        /
            //  |  \                    /
            //  |    \                /
            //  |      \            /
            //  |        \        /
            //  |          \    /
            //  |            \/
            //  |
            //  |
            //  |
            //  |
            //  +------------+-----------+------->  actual batch size in time
            //  0   1/2*testDuration   testDuration
            //
            long worstBatchUs = effectiveDurationUs / 2 + 1;
            if ((minBatchUs > worstBatchUs) ==  (maxBatchUs > worstBatchUs)) {
                // same side
                double ratio = Math.min(Math.abs(worstBatchUs - minBatchUs),
                        Math.abs(worstBatchUs - maxBatchUs)) / (double)worstBatchUs;
                expectedMinUs = (long)((ratio + 1) / 2 * testDurationUs) * 4 / 5;
            } else {
                // the worst case is possible
                expectedMinUs = worstBatchUs * 4 / 5;
            }
        }
        long expectedMinNumEvent = expectedMinUs/sampleUs;

        return new EventBasicVerification(expectedMinNumEvent, environment.getSensor());
    }

    @Override
    public void verify(TestSensorEnvironment environment, SensorStats stats) {
        verify(stats);
    }

    /* visible to unit test */
    void verify(SensorStats stats) {

        stats.addValue(SensorStats.EVENT_COUNT_KEY, mNumEvent);
        stats.addValue(SensorStats.EVENT_COUNT_EXPECTED_KEY, mExpectedMinNumEvent);
        stats.addValue(SensorStats.WRONG_SENSOR_KEY, mWrongSensorObserved);

        boolean enoughSample = mNumEvent >= mExpectedMinNumEvent * ( 1 - NUM_EVENT_MERCY_FACTOR );
        boolean noWrongSensor = !mWrongSensorObserved;

        boolean success = enoughSample && noWrongSensor;
        stats.addValue(PASSED_KEY, success);

        if (!success) {
            Assert.fail(String.format("Failed due to (%s%s)",
                        enoughSample?"":"insufficient events " + mNumEvent + "/" +
                                mExpectedMinNumEvent + ", ",
                        noWrongSensor?"":"wrong sensor observed, "));
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public EventBasicVerification clone() {
        return new EventBasicVerification( mExpectedMinNumEvent, (Sensor)mSensor );
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected void addSensorEventInternal(TestSensorEvent event) {
        if (event.sensor == mSensor) {
            ++mNumEvent;
        } else {
            mWrongSensorObserved = true;
        }
    }

}
