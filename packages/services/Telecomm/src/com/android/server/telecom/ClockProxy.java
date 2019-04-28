/*
 * Copyright (C) 2017 The Android Open Source Project
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
 * limitations under the License
 */

package com.android.server.telecom;

import android.os.SystemClock;

/**
 * Defines common clock functionality used in Telecom.  Provided to make unit testing of clock
 * operations testable.
 */
public interface ClockProxy {
    /**
     * Returns the current wall-clock time of the system, as typically returned by
     * {@link System#currentTimeMillis()}.
     * @return The current wall-clock time.
     */
    long currentTimeMillis();

    /**
     * Returns the elapsed time since boot of the system, as typically returned by
     * {@link SystemClock#elapsedRealtime()}.
     * @return the current elapsed real time.
     */
    long elapsedRealtime();
}
