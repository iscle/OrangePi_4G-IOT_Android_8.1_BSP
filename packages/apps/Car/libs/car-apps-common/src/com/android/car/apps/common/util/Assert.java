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
package com.android.car.apps.common.util;

import android.os.Looper;
import android.util.Log;

/**
 * A helper class which checks a given runtime assumption. If the assumption fails this class will
 * log a error and if the current BuildConfig is set to debug the failed assertion will throw an
 * exception.
 */
public class Assert {
    private static final String TAG = "Em.MediaAssert";

    private Assert() {}

    /**
     * Checks that the current thread is the main thread and throws an {@link AssertionError} if it
     * is not.
     */
    public static void isMainThread() {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            fail("Expected to run on main thread.");
        }
    }

    /**
     * Throws an {@link AssertionError} with the given message. Also will write the message to
     * {@link Log}.
     */
    public static void fail(final String message) {
        Log.e(TAG, "Assert.fail() called: " + message);
        throw new AssertionError(message);
    }
}