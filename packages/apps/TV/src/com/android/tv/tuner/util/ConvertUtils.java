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

package com.android.tv.tuner.util;

/**
 * Utility class for converting date and time.
 */
public class ConvertUtils {
    // Time diff between 1.1.1970 00:00:00 and 6.1.1980 00:00:00
    private static final long DIFF_BETWEEN_UNIX_EPOCH_AND_GPS = 315964800;

    private ConvertUtils() { }

    public static long convertGPSTimeToUnixEpoch(long gpsTime) {
        return gpsTime + DIFF_BETWEEN_UNIX_EPOCH_AND_GPS;
    }

    public static long convertUnixEpochToGPSTime(long epochTime) {
        return epochTime - DIFF_BETWEEN_UNIX_EPOCH_AND_GPS;
    }
}
