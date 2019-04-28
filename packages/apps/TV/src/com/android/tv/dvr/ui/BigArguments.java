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

package com.android.tv.dvr.ui;

import android.support.annotation.NonNull;

import com.android.tv.common.SoftPreconditions;

import java.util.HashMap;
import java.util.Map;

/**
 * Stores the object to pass through activities/fragments.
 */
public class BigArguments {
    private final static String TAG = "BigArguments";
    private static Map<String, Object> sBigArgumentMap = new HashMap<>();

    /**
     * Sets the argument.
     */
    public static void setArgument(String name, @NonNull Object value) {
        SoftPreconditions.checkState(value != null, TAG, "Set argument, but value is null");
        sBigArgumentMap.put(name, value);
    }

    /**
     * Returns the argument which is associated to the name.
     */
    public static Object getArgument(String name) {
        return sBigArgumentMap.get(name);
    }

    /**
     * Resets the arguments.
     */
    public static void reset() {
        sBigArgumentMap.clear();
    }
}
