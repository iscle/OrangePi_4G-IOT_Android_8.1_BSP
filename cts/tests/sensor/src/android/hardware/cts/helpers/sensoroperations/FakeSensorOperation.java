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

import android.hardware.cts.helpers.SensorStats;
import android.hardware.cts.helpers.reporting.ISensorTestNode;

import junit.framework.Assert;

import java.util.concurrent.TimeUnit;

/**
 * A fake {@link SensorOperation} that will run for a specified time and then pass or fail. Useful
 * when debugging the framework.
 */
public class FakeSensorOperation extends SensorOperation {
    private static final int NANOS_PER_MILLI = 1000000;

    private final boolean mFail;
    private final long mDelay;
    private final TimeUnit mTimeUnit;

    /**
     * Constructor for {@link FakeSensorOperation} that passes
     */
    public FakeSensorOperation(long delay, TimeUnit timeUnit) {
        this(false, delay, timeUnit);
    }

    /**
     * Constructor for {@link FakeSensorOperation}
     */
    public FakeSensorOperation(boolean fail, long delay, TimeUnit timeUnit) {
        if (timeUnit == null) {
            throw new IllegalArgumentException("Arguments cannot be null");
        }
        mFail = fail;
        mDelay = delay;
        mTimeUnit = timeUnit;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void execute(ISensorTestNode parent) {
        long delayNs = TimeUnit.NANOSECONDS.convert(mDelay, mTimeUnit);
        try {
            Thread.sleep(delayNs / NANOS_PER_MILLI, (int) (delayNs % NANOS_PER_MILLI));
            getStats().addValue("executed", true);
            if (mFail) {
                doFail();
            }
        }catch (InterruptedException e) {
            // Ignore
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public FakeSensorOperation clone() {
        return new FakeSensorOperation(mFail, mDelay, mTimeUnit);
    }

    /**
     * Fails the operation.
     */
    protected void doFail() {
        String msg = "FakeSensorOperation failed";
        getStats().addValue(SensorStats.ERROR, msg);
        Assert.fail(msg);
    }
}
