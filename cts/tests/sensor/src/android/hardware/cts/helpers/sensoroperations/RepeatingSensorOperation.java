/*
 * Copyright (C) 2013 The Android Open Source Project
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

import android.hardware.cts.helpers.SensorStats;
import android.hardware.cts.helpers.SensorTestPlatformException;
import android.hardware.cts.helpers.reporting.ISensorTestNode;

/**
 * A {@link SensorOperation} that executes a single {@link SensorOperation} a given number of
 * times. This class can be combined to compose complex {@link SensorOperation}s.
 */
public class RepeatingSensorOperation extends SensorOperation {
    public static final String STATS_TAG = "repeating";

    private final SensorOperation mOperation;
    private final int mIterations;

    /**
     * Constructor for {@link RepeatingSensorOperation}.
     *
     * @param operation the {@link SensorOperation} to run.
     * @param iterations the number of iterations to run the operation for.
     */
    public RepeatingSensorOperation(SensorOperation operation, int iterations) {
        if (operation == null) {
            throw new IllegalArgumentException("Arguments cannot be null");
        }
        mOperation = operation;
        mIterations = iterations;
    }

    /**
     * Executes the {@link SensorOperation}s the given number of times. If an exception occurs in
     * one iterations, it is thrown and all subsequent iterations will not run.
     */
    @Override
    public void execute(ISensorTestNode parent) throws InterruptedException {
        ISensorTestNode currentNode = asTestNode(parent);
        for(int i = 0; i < mIterations; ++i) {
            SensorOperation operation = mOperation.clone();
            try {
                operation.execute(new TestNode(currentNode, i));
            } catch (AssertionError e) {
                String msg = String.format("Iteration %d failed: \"%s\"", i, e.getMessage());
                getStats().addValue(SensorStats.ERROR, msg);
                throw new AssertionError(msg, e);
            } finally {
                addSensorStats(STATS_TAG, i, operation.getStats());
            }
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public RepeatingSensorOperation clone() {
        return new RepeatingSensorOperation(mOperation.clone(), mIterations);
    }

    private class TestNode implements ISensorTestNode {
        private final ISensorTestNode mTestNode;
        private final int mIteration;

        public TestNode(ISensorTestNode parent, int iteration) {
            mTestNode = asTestNode(parent);
            mIteration = iteration;
        }

        @Override
        public String getName() throws SensorTestPlatformException {
            return String.format("%s-iteration%d", mTestNode.getName(), mIteration);
        }
    }
}
