/*
 * Copyright 2014, The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.server.telecom;

import android.os.Looper;
import android.telecom.Log;

/**
 * Helper methods to deal with threading related tasks.
 */
public final class ThreadUtil {
    private static final String TAG = ThreadUtil.class.getSimpleName();

    private ThreadUtil() {}

    /** @return whether this method is being called from the main (UI) thread. */
    public static boolean isOnMainThread() {
        return Looper.getMainLooper() == Looper.myLooper();
    }

    /**
     * Checks that this is being executed on the main thread. If not, a message is logged at
     * WTF-level priority.
     */
    public static void checkOnMainThread() {
        if (!isOnMainThread()) {
            Log.wtf(TAG, new IllegalStateException(), "Must be on the main thread!");
        }
    }

    /**
     * Checks that this is not being executed on the main thread. If so, a message is logged at
     * WTF-level priority.
     */
    public static void checkNotOnMainThread() {
        if (isOnMainThread()) {
            Log.wtf(TAG, new IllegalStateException(), "Must not be on the main thread!");
        }
    }
}
