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

import java.util.concurrent.TimeUnit;

/**
 * A class that includes convenience methods for time shift plays.
 */
public class TimeShiftUtils {
    private static final String TAG = "TimeShiftUtils";
    private static final boolean DEBUG = false;

    private static final long SHORT_PROGRAM_THRESHOLD_MILLIS = TimeUnit.MINUTES.toMillis(46);
    private static final int[] SHORT_PROGRAM_SPEED_FACTORS = new int[] {2, 4, 12, 48};
    private static final int[] LONG_PROGRAM_SPEED_FACTORS = new int[] {2, 8, 32, 128};

    /**
     * The maximum play speed level support by time shift play. In other words, the valid
     * speed levels are ranged from 0 to MAX_SPEED_LEVEL (included).
     */
    public static final int MAX_SPEED_LEVEL = SHORT_PROGRAM_SPEED_FACTORS.length - 1;

    /**
     * Returns real speeds used in time shift play. This method is only for fast-forwarding and
     * rewinding. The normal play speed is not addressed here.
     *
     * @param speedLevel the valid value is ranged from 0 to {@link #MAX_SPEED_LEVEL}.
     * @param programDurationMillis the length of program under playing.
     * @throws IndexOutOfBoundsException if speed level is out of its range.
     */
    public static int getPlaybackSpeed(int speedLevel, long programDurationMillis)
            throws IndexOutOfBoundsException {
        return (programDurationMillis > SHORT_PROGRAM_THRESHOLD_MILLIS) ?
                LONG_PROGRAM_SPEED_FACTORS[speedLevel] : SHORT_PROGRAM_SPEED_FACTORS[speedLevel];
    }

    /**
     * Returns the maxium possible play speed according to the program's length.
     * @param programDurationMillis the length of program under playing.
     */
    public static int getMaxPlaybackSpeed(long programDurationMillis) {
        return (programDurationMillis > SHORT_PROGRAM_THRESHOLD_MILLIS) ?
                LONG_PROGRAM_SPEED_FACTORS[MAX_SPEED_LEVEL]
                : SHORT_PROGRAM_SPEED_FACTORS[MAX_SPEED_LEVEL];
    }
}
