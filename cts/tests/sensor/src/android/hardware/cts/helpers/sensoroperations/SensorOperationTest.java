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

package android.hardware.cts.helpers.sensoroperations;

import junit.framework.TestCase;

import android.hardware.cts.helpers.SensorStats;
import android.hardware.cts.helpers.SensorTestPlatformException;
import android.hardware.cts.helpers.reporting.ISensorTestNode;

import java.util.Set;
import java.util.concurrent.TimeUnit;

/**
 * Tests for the primitive {@link SensorOperation}s including {@link DelaySensorOperation},
 * {@link ParallelSensorOperation}, {@link RepeatingSensorOperation} and
 * {@link SequentialSensorOperation}.
 */
public class SensorOperationTest extends TestCase {
    private static final long TEST_DURATION_THRESHOLD_MS = TimeUnit.SECONDS.toMillis(5);

    private final ISensorTestNode mTestNode = new ISensorTestNode() {
        @Override
        public String getName() throws SensorTestPlatformException {
            return "SensorOperationUnitTest";
        }
    };

    /**
     * Test that the {@link FakeSensorOperation} functions correctly. Other tests in this class
     * rely on this operation.
     */
    public void testFakeSensorOperation() throws InterruptedException {
        final int opDurationMs = 100;

        SensorOperation op = new FakeSensorOperation(opDurationMs, TimeUnit.MILLISECONDS);

        assertFalse(op.getStats().flatten().containsKey("executed"));
        long start = System.currentTimeMillis();
        op.execute(mTestNode);
        long duration = System.currentTimeMillis() - start;
        assertTrue(Math.abs(opDurationMs - duration) < TEST_DURATION_THRESHOLD_MS);
        assertTrue(op.getStats().flatten().containsKey("executed"));

        op = new FakeSensorOperation(true, 0, TimeUnit.MILLISECONDS);
        try {
            op.execute(mTestNode);
            throw new Error("AssertionError expected");
        } catch (AssertionError e) {
            // Expected
        }
        assertTrue(op.getStats().flatten().keySet().contains(SensorStats.ERROR));
    }

    /**
     * Test that the {@link DelaySensorOperation} functions correctly.
     */
    public void testDelaySensorOperation() throws InterruptedException {
        final int opDurationMs = 500;
        final int subOpDurationMs = 100;

        FakeSensorOperation subOp = new FakeSensorOperation(subOpDurationMs, TimeUnit.MILLISECONDS);
        SensorOperation op = new DelaySensorOperation(subOp, opDurationMs, TimeUnit.MILLISECONDS);

        long startMs = System.currentTimeMillis();
        op.execute(mTestNode);
        long durationMs = System.currentTimeMillis() - startMs;
        long durationDeltaMs = Math.abs(opDurationMs + subOpDurationMs - durationMs);
        assertTrue(durationDeltaMs < TEST_DURATION_THRESHOLD_MS);
    }

    /**
     * Test that the {@link ParallelSensorOperation} functions correctly.
     */
    public void testParallelSensorOperation() throws InterruptedException {
        final int subOpCount = 100;
        final int subOpDurationMs = 500;

        ParallelSensorOperation op = new ParallelSensorOperation();
        for (int i = 0; i < subOpCount; i++) {
            SensorOperation subOp = new FakeSensorOperation(subOpDurationMs,
                    TimeUnit.MILLISECONDS);
            op.add(subOp);
        }

        Set<String> statsKeys = op.getStats().flatten().keySet();
        assertEquals(0, statsKeys.size());

        long start = System.currentTimeMillis();
        op.execute(mTestNode);
        long durationMs = System.currentTimeMillis() - start;
        long durationDeltaMs = Math.abs(subOpDurationMs - durationMs);
        String message = String.format(
                "Expected duration=%sms, observed=%sms, delta=%sms, thresold=%sms",
                subOpDurationMs,
                durationMs,
                durationDeltaMs,
                TEST_DURATION_THRESHOLD_MS);
        // starting threads might have an impact in the order of 100s ms, depending on the load of
        // the system, so we relax the benchmark part of the test, and we just expect all operations
        // to complete
        assertTrue(message, durationDeltaMs < TEST_DURATION_THRESHOLD_MS);

        statsKeys = op.getStats().flatten().keySet();
        assertEquals(subOpCount, statsKeys.size());
        for (int i = 0; i < subOpCount; i++) {
            assertTrue(statsKeys.contains(String.format("%s_%03d%sexecuted",
                    ParallelSensorOperation.STATS_TAG, i, SensorStats.DELIMITER)));
        }
    }

    /**
     * Test that the {@link ParallelSensorOperation} functions correctly if there is a failure in
     * a child operation.
     */
    public void testParallelSensorOperation_fail() throws InterruptedException {
        final int subOpCount = 100;

        ParallelSensorOperation op = new ParallelSensorOperation();
        for (int i = 0; i < subOpCount; i++) {
            // Trigger failures in the 5th, 55th operations at t=5ms, t=55ms
            SensorOperation subOp = new FakeSensorOperation(i % 50 == 5, i, TimeUnit.MILLISECONDS);
            op.add(subOp);
        }

        Set<String> statsKeys = op.getStats().flatten().keySet();
        assertEquals(0, statsKeys.size());

        try {
            op.execute(mTestNode);
            fail("AssertionError expected");
        } catch (AssertionError e) {
            // Expected
            System.out.println(e.getMessage());
        }

        statsKeys = op.getStats().flatten().keySet();
        assertEquals(subOpCount + 3, statsKeys.size());
        for (int i = 0; i < subOpCount; i++) {
            assertTrue(statsKeys.contains(String.format("%s_%03d%sexecuted",
                    ParallelSensorOperation.STATS_TAG, i, SensorStats.DELIMITER)));
            if (i % 50 == 5) {
                assertTrue(statsKeys.contains(String.format("%s_%03d%s%s",
                        ParallelSensorOperation.STATS_TAG, i, SensorStats.DELIMITER,
                        SensorStats.ERROR)));
            }

        }
        assertTrue(statsKeys.contains(SensorStats.ERROR));
    }

    /**
     * Test that the {@link ParallelSensorOperation} functions correctly if a child exceeds the
     * timeout.
     */
    public void testParallelSensorOperation_timeout() throws InterruptedException {
        final int subOpCount = 100;

        ParallelSensorOperation op = new ParallelSensorOperation(1, TimeUnit.SECONDS);
        for (int i = 0; i < subOpCount; i++) {
            // Trigger timeouts in the 5th, 55th operations (5 seconds vs 1 seconds)
            SensorOperation subOp = new FakeSensorOperation(i % 50 == 5 ? 5 : 0, TimeUnit.SECONDS);
            op.add(subOp);
        }

        Set<String> statsKeys = op.getStats().flatten().keySet();
        assertEquals(0, statsKeys.size());

        try {
            op.execute(mTestNode);
            fail("AssertionError expected");
        } catch (AssertionError e) {
            // Expected
            System.out.println(e.getMessage());
        }

        statsKeys = op.getStats().flatten().keySet();
        assertEquals(subOpCount - 2, statsKeys.size());
        for (int i = 0; i < subOpCount; i++) {
            if (i % 50 != 5) {
                assertTrue(statsKeys.contains(String.format("%s_%03d%sexecuted",
                        ParallelSensorOperation.STATS_TAG, i, SensorStats.DELIMITER)));
            }
        }
    }

    /**
     * Test that the {@link RepeatingSensorOperation} functions correctly.
     */
    public void testRepeatingSensorOperation() throws InterruptedException {
        final int iterations = 10;
        final int subOpDurationMs = 100;

        SensorOperation subOp = new FakeSensorOperation(subOpDurationMs, TimeUnit.MILLISECONDS);
        SensorOperation op = new RepeatingSensorOperation(subOp, iterations);

        Set<String> statsKeys = op.getStats().flatten().keySet();
        assertEquals(0, statsKeys.size());

        long start = System.currentTimeMillis();
        op.execute(mTestNode);
        long duration = System.currentTimeMillis() - start;
        assertTrue(Math.abs(subOpDurationMs * iterations - duration) < TEST_DURATION_THRESHOLD_MS);

        statsKeys = op.getStats().flatten().keySet();
        assertEquals(iterations, statsKeys.size());
        for (int i = 0; i < iterations; i++) {
            assertTrue(statsKeys.contains(String.format("%s_%03d%sexecuted",
                    RepeatingSensorOperation.STATS_TAG, i, SensorStats.DELIMITER)));
        }
    }

    /**
     * Test that the {@link RepeatingSensorOperation} functions correctly if there is a failure in
     * a child operation.
     */
    public void testRepeatingSensorOperation_fail() throws InterruptedException {
        final int iterations = 100;
        final int failCount = 75;

        SensorOperation subOp = new FakeSensorOperation(0, TimeUnit.MILLISECONDS) {
            private int mExecutedCount = 0;
            private SensorStats mFakeStats = new SensorStats();

            @Override
            public void execute(ISensorTestNode parent) {
                super.execute(parent);
                mExecutedCount++;

                if (failCount == mExecutedCount) {
                    doFail();
                }
            }

            @Override
            public FakeSensorOperation clone() {
                // Don't clone
                mFakeStats = new SensorStats();
                return this;
            }

            @Override
            public SensorStats getStats() {
                return mFakeStats;
            }
        };
        SensorOperation op = new RepeatingSensorOperation(subOp, iterations);

        Set<String> statsKeys = op.getStats().flatten().keySet();
        assertEquals(0, statsKeys.size());

        try {
            op.execute(mTestNode);
            fail("AssertionError expected");
        } catch (AssertionError e) {
            // Expected
            System.out.println(e.getMessage());
        }

        statsKeys = op.getStats().flatten().keySet();
        assertEquals(failCount + 2, statsKeys.size());
        for (int i = 0; i < failCount; i++) {
            assertTrue(statsKeys.contains(String.format("%s_%03d%sexecuted",
                    RepeatingSensorOperation.STATS_TAG, i, SensorStats.DELIMITER)));
        }
        assertTrue(statsKeys.contains(String.format("%s_%03d%s%s",
                RepeatingSensorOperation.STATS_TAG, failCount - 1, SensorStats.DELIMITER,
                SensorStats.ERROR)));
        assertTrue(statsKeys.contains(SensorStats.ERROR));
    }

    /**
     * Test that the {@link SequentialSensorOperation} functions correctly.
     */
    public void testSequentialSensorOperation() throws InterruptedException {
        final int subOpCount = 10;
        final int subOpDurationMs = 100;

        SequentialSensorOperation op = new SequentialSensorOperation();
        for (int i = 0; i < subOpCount; i++) {
            SensorOperation subOp = new FakeSensorOperation(subOpDurationMs,
                    TimeUnit.MILLISECONDS);
            op.add(subOp);
        }

        Set<String> statsKeys = op.getStats().flatten().keySet();
        assertEquals(0, statsKeys.size());

        long start = System.currentTimeMillis();
        op.execute(mTestNode);
        long duration = System.currentTimeMillis() - start;
        assertTrue(Math.abs(subOpDurationMs * subOpCount - duration) < TEST_DURATION_THRESHOLD_MS);

        statsKeys = op.getStats().flatten().keySet();
        assertEquals(subOpCount, statsKeys.size());
        for (int i = 0; i < subOpCount; i++) {
            assertTrue(statsKeys.contains(String.format("%s_%03d%sexecuted",
                    SequentialSensorOperation.STATS_TAG, i, SensorStats.DELIMITER)));
        }
    }

    /**
     * Test that the {@link SequentialSensorOperation} functions correctly if there is a failure in
     * a child operation.
     */
    public void testSequentialSensorOperation_fail() throws InterruptedException {
        final int subOpCount = 100;
        final int failCount = 75;

        SequentialSensorOperation op = new SequentialSensorOperation();
        for (int i = 0; i < subOpCount; i++) {
            // Trigger a failure in the 75th operation only
            SensorOperation subOp = new FakeSensorOperation(i + 1 == failCount, 0,
                    TimeUnit.MILLISECONDS);
            op.add(subOp);
        }

        Set<String> statsKeys = op.getStats().flatten().keySet();
        assertEquals(0, statsKeys.size());

        try {
            op.execute(mTestNode);
            fail("AssertionError expected");
        } catch (AssertionError e) {
            // Expected
            System.out.println(e.getMessage());
        }

        statsKeys = op.getStats().flatten().keySet();
        assertEquals(failCount + 2, statsKeys.size());
        for (int i = 0; i < failCount; i++) {
            assertTrue(statsKeys.contains(String.format("%s_%03d%sexecuted",
                    SequentialSensorOperation.STATS_TAG, i, SensorStats.DELIMITER)));
        }
        assertTrue(statsKeys.contains(String.format("%s_%03d%s%s",
                SequentialSensorOperation.STATS_TAG, failCount - 1, SensorStats.DELIMITER,
                SensorStats.ERROR)));
        assertTrue(statsKeys.contains(SensorStats.ERROR));
    }
}
