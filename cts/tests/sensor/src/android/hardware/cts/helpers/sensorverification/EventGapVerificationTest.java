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

import junit.framework.TestCase;

import android.hardware.cts.helpers.SensorStats;
import android.hardware.cts.helpers.TestSensorEnvironment;
import android.hardware.cts.helpers.TestSensorEvent;

import java.util.ArrayList;
import java.util.Collection;

/**
 * Tests for {@link EventGapVerification}.
 */
public class EventGapVerificationTest extends TestCase {

    /**
     * Test that the verification passes when there are no results.
     */
    public void testVerify_no_events() {
        // Timestamps in ns, expected in us
        runVerification(1000, new long[]{}, true, new int[]{});
    }

    /**
     * Test that the verification passes when there are not missing events.
     */
    public void testVerify_correct() {
        // Timestamps in ns, expected in us
        long[] timestamps = {1000000, 2000000, 3000000, 4000000, 5000000};
        runVerification(1000, timestamps, true, new int[]{});
    }

    /**
     * Test that the verification passes when there are not missing events but some jitter.
     */
    public void testVerify_jitter() {
        // Timestamps in ns, expected in us
        long[] timestamps = {1100000, 2050000, 2990000, 4000000, 4950000};
        runVerification(1000, timestamps, true, new int[]{});
    }

    /**
     * Test that the verification fails when there are missing events.
     */
    public void testVerify_missing_events() {
        // Timestamps in ns, expected in us
        long[] timestamps = {1000000, 2000000, 3000000, 5000000, 6000000};
        runVerification(1000, timestamps, true, new int[]{3});
    }

    private void runVerification(int expected, long[] timestamps, boolean pass,
            int[] indices) {
        SensorStats stats = new SensorStats();
        ISensorVerification verification = getVerification(expected, timestamps);
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
        assertEquals(pass, stats.getValue(EventGapVerification.PASSED_KEY));
        assertEquals(indices.length, stats.getValue(SensorStats.EVENT_GAP_COUNT_KEY));
        assertNotNull(stats.getValue(SensorStats.EVENT_GAP_POSITIONS_KEY));
        int[] actualIndices = (int[]) stats.getValue(SensorStats.EVENT_GAP_POSITIONS_KEY);
        assertEquals(indices.length, actualIndices.length);

        for (int i = 0; i < indices.length; i++) {
            assertEquals(indices[i], actualIndices[i]);
        }
    }

    private static EventGapVerification getVerification(int expected, long ... timestamps) {
        Collection<TestSensorEvent> events = new ArrayList<>(timestamps.length);
        for (long timestamp : timestamps) {
            events.add(new TestSensorEvent(null, timestamp, 0, null));
        }
        EventGapVerification verification = new EventGapVerification(expected);
        verification.addSensorEvents(events);
        return verification;
    }
}
