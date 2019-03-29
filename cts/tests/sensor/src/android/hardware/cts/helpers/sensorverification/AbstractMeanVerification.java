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

import junit.framework.Assert;

import android.hardware.cts.helpers.TestSensorEvent;

/**
 * Abstract class that calculates of the mean event values.
 */
public abstract class AbstractMeanVerification extends AbstractSensorVerification {
    private float[] mSums = null;
    private int mCount = 0;

    /**
     * {@inheritDoc}
     */
    @Override
    protected void addSensorEventInternal(TestSensorEvent event) {
        if (mSums == null) {
            mSums = new float[event.values.length];
        }
        Assert.assertEquals(mSums.length, event.values.length);
        for (int i = 0; i < mSums.length; i++) {
            mSums[i] += event.values[i];
        }
        mCount++;
    }

    /**
     * Return the number of events.
     */
    protected int getCount() {
        return mCount;
    }

    /**
     * Return the means of the event values.
     */
    protected float[] getMeans() {
        if (mCount < 0) {
            return null;
        }

        float[] means = new float[mSums.length];
        for (int i = 0; i < mSums.length; i++) {
            means[i] = mSums[i] / mCount;
        }
        return means;
    }
}
