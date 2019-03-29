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
import android.hardware.cts.helpers.TestSensorEvent;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * Tests for {@link EventOrderingVerification}.
 */
public class EventOrderingVerificationTest extends TestCase {

    /**
     * Test that the verification passes when there are no results.
     */
    public void testNoEvents() {
        SensorStats stats = new SensorStats();
        EventOrderingVerification verification = getVerification();
        verification.verify(stats);
        verifyStats(stats, true, 0);
    }

    /**
     * Test that the verification passes when the timestamps are increasing.
     */
    public void testSequentialTimestamp() {
        SensorStats stats = new SensorStats();
        EventOrderingVerification verification = getVerification(0, 1, 2, 3, 4);
        verification.verify(stats);
        verifyStats(stats, true, 0);
    }

    /**
     * Test that the verification fails when there is one event out of order.
     */
    public void testSingleOutofOrder() {
        SensorStats stats = new SensorStats();
        EventOrderingVerification verification = getVerification(0, 2, 1, 3, 4);
        try {
            verification.verify(stats);
            throw new Error("Expected an AssertionError");
        } catch (AssertionError e) {
            // Expected;
        }
        verifyStats(stats, false, 1);
        List<Integer> indices = getIndices(stats);
        assertTrue(indices.contains(2));
    }

    /**
     * Test that the verification fails when there are multiple events out of order.
     */
    public void testMultipleOutOfOrder() {
        SensorStats stats = new SensorStats();
        EventOrderingVerification verification = getVerification(4, 0, 1, 2, 3);
        try {
            verification.verify(stats);
            throw new Error("Expected an AssertionError");
        } catch (AssertionError e) {
            // Expected;
        }
        verifyStats(stats, false, 4);
        List<Integer> indices = getIndices(stats);
        assertTrue(indices.contains(1));
        assertTrue(indices.contains(2));
        assertTrue(indices.contains(3));
        assertTrue(indices.contains(4));
    }

    private static EventOrderingVerification getVerification(long ... timestamps) {
        Collection<TestSensorEvent> events = new ArrayList<>(timestamps.length);
        for (long timestamp : timestamps) {
            events.add(new TestSensorEvent(null, timestamp, 0, null));
        }
        EventOrderingVerification verification = new EventOrderingVerification();
        verification.addSensorEvents(events);
        return verification;
    }

    private void verifyStats(SensorStats stats, boolean passed, int count) {
        assertEquals(passed, stats.getValue(EventOrderingVerification.PASSED_KEY));
        assertEquals(count, stats.getValue(SensorStats.EVENT_OUT_OF_ORDER_COUNT_KEY));
        assertNotNull(stats.getValue(SensorStats.EVENT_OUT_OF_ORDER_POSITIONS_KEY));
    }

    private List<Integer> getIndices(SensorStats stats) {
        int[] primitiveIndices = (int[]) stats.getValue(
                SensorStats.EVENT_OUT_OF_ORDER_POSITIONS_KEY);
        List<Integer> indices = new ArrayList<Integer>(primitiveIndices.length);
        for (int index : primitiveIndices) {
            indices.add(index);
        }
        return indices;
    }
}
