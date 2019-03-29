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
import android.hardware.cts.helpers.reporting.ISensorTestNode;

import java.util.ArrayList;

/**
 * A {@link SensorOperation} that executes a set of children {@link SensorOperation}s in a
 * sequence. The children are executed in the order they are added. This class can be combined to
 * compose complex {@link SensorOperation}s.
 */
public class SequentialSensorOperation extends SensorOperation {
    public static final String STATS_TAG = "sequential";

    private final ArrayList<SensorOperation> mOperations = new ArrayList<>();

    /**
     * Add a set of {@link SensorOperation}s.
     */
    public void add(SensorOperation ... operations) {
        for (SensorOperation operation : operations) {
            if (operation == null) {
                throw new IllegalArgumentException("Arguments cannot be null");
            }
            mOperations.add(operation);
        }
    }

    /**
     * Executes the {@link SensorOperation}s in the order they were added. If an exception occurs
     * in one operation, it is thrown and all subsequent operations will not run.
     */
    @Override
    public void execute(ISensorTestNode parent) throws InterruptedException {
        ISensorTestNode currentNode = asTestNode(parent);
        for (int i = 0; i < mOperations.size(); i++) {
            SensorOperation operation = mOperations.get(i);
            try {
                operation.execute(currentNode);
            } catch (AssertionError e) {
                String msg = String.format("Operation %d failed: \"%s\"", i, e.getMessage());
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
    public SequentialSensorOperation clone() {
        SequentialSensorOperation operation = new SequentialSensorOperation();
        for (SensorOperation subOperation : mOperations) {
            operation.add(subOperation.clone());
        }
        return operation;
    }
}
