/*
 * Copyright (C) 2016 The Android Open Source Project
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

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * A class only for help developers.
 */
public class Debug {
    /**
     * A threshold of start up time, when the start up time of Live TV is more than it,
     * a warning will show to the developer.
     */
    public static final long TIME_START_UP_DURATION_THRESHOLD = TimeUnit.SECONDS.toMillis(6);
    /**
     * Tag for measuring start up time of Live TV.
     */
    public static final String TAG_START_UP_TIMER = "start_up_timer";

    /**
     * A global map for duration timers.
     */
    private final static Map<String, DurationTimer> sTimerMap = new HashMap<>();

    /**
     * Returns the global duration timer by tag.
     */
    public static DurationTimer getTimer(String tag) {
        if (sTimerMap.get(tag) != null) {
            return sTimerMap.get(tag);
        }
        DurationTimer timer = new DurationTimer(tag, true);
        sTimerMap.put(tag, timer);
        return timer;
    }

    /**
     * Removes the global duration timer by tag.
     */
    public static DurationTimer removeTimer(String tag) {
        return sTimerMap.remove(tag);
    }
}
