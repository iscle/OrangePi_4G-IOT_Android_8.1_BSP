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
package com.android.tv.util;

import android.os.SystemClock;

/**
 * An interface through which system clocks can be read. The {@link #SYSTEM} implementation
 * must be used for all non-test cases.
 */
public interface Clock {
    /**
     * Returns the current time in milliseconds since January 1, 1970 00:00:00.0 UTC.
     * See {@link System#currentTimeMillis()}.
     */
    long currentTimeMillis();

    /**
     * Returns milliseconds since boot, including time spent in sleep.
     *
     * @see SystemClock#elapsedRealtime()
     */
    long elapsedRealtime();

    /**
     * Waits a given number of milliseconds (of uptimeMillis) before returning.
     *
     * @param ms to sleep before returning, in milliseconds of uptime.
     * @see SystemClock#sleep(long)
     */
    void sleep(long ms);

    /**
     * The default implementation of Clock.
     */
    Clock SYSTEM = new Clock() {
        @Override
        public long currentTimeMillis() {
            return System.currentTimeMillis();
        }

        @Override
        public long elapsedRealtime() {
            return SystemClock.elapsedRealtime();
        }

        @Override
        public void sleep(long ms) {
            SystemClock.sleep(ms);
        }
    };
}
