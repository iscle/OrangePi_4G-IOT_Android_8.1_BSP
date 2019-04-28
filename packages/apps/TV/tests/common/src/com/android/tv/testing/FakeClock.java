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

package com.android.tv.testing;

import com.android.tv.util.Clock;

import java.util.concurrent.TimeUnit;

/**
 * Fake implementation of Clock suitable for testing.
 *
 * <p>The current time only changes if {@link #setCurrentTimeMillis(long)}, {@link #increment} or
 * {@link #sleep(long)} is called.
 */
public class FakeClock implements Clock {
    /**
     * Creates a fake clock with the time set to now and the boot time set to now - 100,000.
     */
    public static FakeClock createWithCurrentTime() {
        long now = System.currentTimeMillis();
        return new FakeClock(now, now - 100_000);
    }

    /**
     * Creates a fake clock with the time set to zero.
     */
    public static FakeClock createWithTimeOne() {
        return new FakeClock(1L, 0L);
    }


    private long mCurrentTimeMillis;

    private long mBootTimeMillis;

    private FakeClock(long currentTimeMillis, long bootTimeMillis) {
        mCurrentTimeMillis = currentTimeMillis;
        mBootTimeMillis = bootTimeMillis;
    }

    public void setCurrentTimeMillis(long ms) {
        if (ms < mBootTimeMillis) {
            throw new IllegalStateException("current time can not be before boot time");
        }
        mCurrentTimeMillis = ms;
    }

    public void setBootTimeMillis(long ms) {
        if (ms > mCurrentTimeMillis) {
            throw new IllegalStateException("boot time can not be after current time");
        }
        mBootTimeMillis = ms;
    }

    /**
     * Increment the current time by one unit of time.
     *
     * @param unit The time unit to increment by.
     */
    public void increment(TimeUnit unit) {
        increment(unit, 1);
    }

    /**
     * Increment the current time by {@code amount} unit of time.
     *
     * @param unit The time unit to increment by.
     * @param amount The amount of time units to increment by.
     */
    public void increment(TimeUnit unit, long amount) {
        mCurrentTimeMillis += unit.toMillis(amount);
    }

    @Override
    public long currentTimeMillis() {
        return mCurrentTimeMillis;
    }

    @Override
    public long elapsedRealtime() {
        return mCurrentTimeMillis - mBootTimeMillis;
    }

    /**
     * Sleep does not block it just updates the current time.
     */
    @Override
    public void sleep(long ms) {
        // TODO: implement blocking if needed.
        mCurrentTimeMillis += ms;
    }
}
