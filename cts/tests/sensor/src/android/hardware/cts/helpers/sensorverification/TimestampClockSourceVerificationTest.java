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
 * limitations under the License.
 */

package android.hardware.cts.helpers.sensorverification;

import junit.framework.TestCase;

import android.hardware.cts.helpers.SensorStats;
import android.hardware.cts.helpers.TestSensorEnvironment;
import android.hardware.cts.helpers.TestSensorEvent;
import android.os.SystemClock;

import java.util.ArrayList;
import java.util.Collection;

import android.util.Log;

/**
 * Tests for {@link TimestampClockSourceVerification}.
 */
public class TimestampClockSourceVerificationTest extends TestCase {
    private final String TAG = "TimestampClockSourceVerificationTest";

    private final int MIN_DELTA_BETWEEN_CLOCKS_MS = 2000;
    private boolean mAdjustUptime = false;

    private long getValidTimestamp() {
        return SystemClock.elapsedRealtimeNanos();
    }

    private long getInvalidTimestamp() {
        long ms = SystemClock.uptimeMillis();
        if (mAdjustUptime == true) {
            ms -= MIN_DELTA_BETWEEN_CLOCKS_MS;
        }
        return (ms * 1000000);
    }

    private void verifyClockDelta() throws Throwable {
        long uptimeMs = SystemClock.uptimeMillis();
        long realtimeNs = SystemClock.elapsedRealtimeNanos();
        long deltaMs = (realtimeNs/1000000 - uptimeMs);
        if (deltaMs < MIN_DELTA_BETWEEN_CLOCKS_MS) {
            Log.i(TAG, "Device has not slept, will use different clock source for test purposes");
            mAdjustUptime = true;
        } else {
            mAdjustUptime = false;
            Log.i(TAG, "CLOCK_MONOTONIC="+uptimeMs*1000000+", CLOCK_BOOTTIME="+realtimeNs+", delta=" + deltaMs + " mS");
        }
    }


    /**
     * Test that the verification passes when there are not missing events.
     */
    public void testVerify_pass() throws Throwable {
        try {
            verifyClockDelta();
            long ts = getValidTimestamp();
            long[] timestamps = {ts-4000000, ts-3000000, ts-2000000, ts-1000000, ts};
            // Timestamps in ns, expected in us
            runVerification(MIN_DELTA_BETWEEN_CLOCKS_MS*1000, timestamps, true, new int[]{});
        } finally {
        }
    }

    /**
     * Test that the verification fails when there are not missing events,
     * but wrong clock source is used.
     */
    public void testVerify_fail() throws Throwable {
        try {
            verifyClockDelta();
            long ts = getInvalidTimestamp();
            long[] timestamps = {ts-4000000, ts-3000000, ts-2000000, ts-1000000, ts};
            // Timestamps in ns, expected in us
            runVerification(MIN_DELTA_BETWEEN_CLOCKS_MS*1000, timestamps, false, new int[]{0,1,2,3,4});
        } finally {
        }
    }

    /**
     * Test that the verification passes when there are not missing events but some jitter.
     */
    public void testVerify_jitter_pass() throws Throwable {
        try {
            verifyClockDelta();
            long ts = getValidTimestamp();
            long[] timestamps = {ts-3900000, ts-2950000, ts-2050000, ts-1000000, ts-50000};
            // Timestamps in ns, expected in us
            runVerification(MIN_DELTA_BETWEEN_CLOCKS_MS*1000, timestamps, true, new int[]{});
        } finally {
        }
    }

    /**
     * Test that the verification passes when there are not missing events but some jitter.
     */
    public void testVerify_jitter_fail() throws Throwable {
        try {
            verifyClockDelta();
            long ts = getInvalidTimestamp();
            long[] timestamps = {ts-3900000, ts-2950000, ts-2050000, ts-1000000, ts-50000};
            // Timestamps in ns, expected in us
            runVerification(MIN_DELTA_BETWEEN_CLOCKS_MS*1000, timestamps, false, new int[]{0,1,2,3,4});
        } finally {
        }
    }

    /**
     * Test that the verification does not fail when there are missing events.
     */
    public void testVerify_missing_events_pass() throws Throwable {
        try {
            verifyClockDelta();
            long ts = getValidTimestamp();
            long[] timestamps = {ts-4000000, ts-3000000, ts-1000000, ts};
            // Timestamps in ns, expected in us
            runVerification(MIN_DELTA_BETWEEN_CLOCKS_MS*1000, timestamps, true, new int[]{});
        } finally {
        }
    }

    /**
     * Test that the verification fails when there are missing events, but wrong
     * timestamp
     */
    public void testVerify_missing_events_fail() throws Throwable {
        try {
            verifyClockDelta();
            long ts = getInvalidTimestamp();
            long[] timestamps = {ts-4000000, ts-3000000, ts-1000000, ts};
            // Timestamps in ns, expected in us
            runVerification(MIN_DELTA_BETWEEN_CLOCKS_MS*1000, timestamps, false, new int[]{0,1,2,3});
        } finally {
        }
    }

    /**
     * Test that the verification fails when there are no results.
     */
    public void testVerify_no_events_fail() throws Throwable {
        try {
            verifyClockDelta();
            // Timestamps in ns, expected in us
            runVerification(MIN_DELTA_BETWEEN_CLOCKS_MS*1000, new long[]{}, false, new int[]{});
        } finally {
        }
    }

    private void runVerification(int expectedUs, long[] timestamps, boolean pass,
            int[] indices) {
        SensorStats stats = new SensorStats();
        ISensorVerification verification = getVerification(expectedUs, timestamps);
        TestSensorEnvironment environment = new TestSensorEnvironment(null, null, false, 0, 0);
        if (pass) {
            verification.verify(environment, stats);
        } else {
            boolean failed = false;
            try {
                verification.verify(environment, stats);
            } catch (AssertionError e) {
                // Expected;
                failed = true;
            }
            assertTrue("Expected an AssertionError", failed);
        }

        assertEquals(pass, stats.getValue(TimestampClockSourceVerification.PASSED_KEY));
        assertEquals(indices.length, stats.getValue(SensorStats.EVENT_TIME_WRONG_CLOCKSOURCE_COUNT_KEY));
        if (0 != (Integer) stats.getValue(SensorStats.EVENT_TIME_WRONG_CLOCKSOURCE_COUNT_KEY)) {
            assertNotNull(stats.getValue(SensorStats.EVENT_TIME_WRONG_CLOCKSOURCE_POSITIONS_KEY));
        }
        try {
            int[] actualIndices = (int[]) stats.getValue(SensorStats.EVENT_TIME_WRONG_CLOCKSOURCE_POSITIONS_KEY);
            assertEquals(indices.length, actualIndices.length);

            for (int i = 0; i < indices.length; i++) {
                assertEquals(indices[i], actualIndices[i]);
            }
        } catch (Exception t) {
        }
    }

    private static TimestampClockSourceVerification getVerification(int expectedUs, long ... timestamps) {
        Collection<TestSensorEvent> events = new ArrayList<>(timestamps.length);
        long expectedNs = expectedUs * 1000;
        long now = SystemClock.elapsedRealtimeNanos();
        long receiveTime;
        for (long timestamp : timestamps) {
            //receiveTime = now - (expectedNs * count);
            receiveTime = SystemClock.elapsedRealtimeNanos();
            events.add(new TestSensorEvent(null, timestamp, receiveTime, 0, null));
        }
        TimestampClockSourceVerification verification = new TimestampClockSourceVerification(expectedUs);
        verification.addSensorEvents(events);
        return verification;
    }
}
