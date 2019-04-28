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

package com.android.car.cluster.sample;

/**
 * Utility functions / constants that make sense for debugging.
 */
public final class DebugUtil {
    /** The default factor is 1, increasing this number results in slowing down animation */
    public static final long ANIMATION_FACTOR = 1;

    public static final String TAG = "CLUSTER";

    public static final boolean DEBUG = true;

    public static String getTag(Class clazz) {
        return TAG + "." + clazz.getSimpleName();
    }
}
