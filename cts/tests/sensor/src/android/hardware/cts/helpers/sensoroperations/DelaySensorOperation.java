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

import android.hardware.cts.helpers.SensorCtsHelper;
import android.hardware.cts.helpers.reporting.ISensorTestNode;

import java.util.concurrent.TimeUnit;

/**
 * An {@link SensorOperation} which delays for a specified period of time before performing another
 * {@link SensorOperation}.
 */
public class DelaySensorOperation extends SensorOperation {
    private final SensorOperation mOperation;
    private final long mDelay;
    private final TimeUnit mTimeUnit;

    /**
     * Constructor for {@link DelaySensorOperation}
     *
     * @param operation the child {@link SensorOperation} to perform after the delay
     * @param delay the amount of time to delay
     * @param timeUnit the unit of the delay
     */
    public DelaySensorOperation(SensorOperation operation, long delay, TimeUnit timeUnit) {
        super(operation.getStats());
        mOperation = operation;
        mDelay = delay;
        mTimeUnit = timeUnit;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void execute(ISensorTestNode parent) throws InterruptedException {
        SensorCtsHelper.sleep(mDelay, mTimeUnit);
        mOperation.execute(asTestNode(parent));
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public DelaySensorOperation clone() {
        return new DelaySensorOperation(mOperation.clone(), mDelay, mTimeUnit);
    }
}
