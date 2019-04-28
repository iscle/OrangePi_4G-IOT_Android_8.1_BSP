/*
 * Copyright (C) 2015 The Android Open Source Project
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

package com.android.support.car.apitest;

import android.util.Log;

import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;

import junit.framework.Assert;

/**
 * A generic test action, whose run method will be called with a parameter.
 */
public abstract class TestAction<T> {
    /** for passing additional test specific parameters */
    public volatile Object arg0;
    public volatile Object arg1;
    private static final String TAG = "CAR.TEST";
    private Throwable mLastThrowable;
    private Semaphore mWaitSemaphore = new Semaphore(0);

    abstract public void run(T param);

    public void doRun(T param) {
        Log.i(TAG, "TestAction doRun " + this + " param:" + param);
        try {
            run(param);
        } catch (Throwable t) {
            Log.i(TAG, "TestAction doRun get exception", t);
            mLastThrowable = t;
        }
        mWaitSemaphore.release();
    }

    public void assertTestRun(long timeoutMs) throws Throwable {
        if (!mWaitSemaphore.tryAcquire(timeoutMs, TimeUnit.MILLISECONDS)) {
            Assert.fail("timeout");
        }
        if (mLastThrowable != null) {
            throw mLastThrowable;
        }
    }

    public void assertNotRun(long waitMs) throws Throwable {
        if (!mWaitSemaphore.tryAcquire(waitMs, TimeUnit.MILLISECONDS)) {
            return;
        }
        if (mLastThrowable != null) {
            Log.e(TAG, "Action was run, but failed");
            throw mLastThrowable;
        }
        Assert.fail("Action was run");
    }

    public boolean waitForTest(long timeoutMs) throws Throwable {
        if (mWaitSemaphore.tryAcquire(timeoutMs, TimeUnit.MILLISECONDS)) {
            if (mLastThrowable != null) {
                throw mLastThrowable;
            }
            return true;
        }
        return false;
    }

    public void resetRunState() {
        mWaitSemaphore.drainPermits();
    }

    public static <U> TestAction<U> buildEmptyAction() {
        return new TestAction<U>() {
            @Override
            public void run(U param) {
            }
        };
    }
}
