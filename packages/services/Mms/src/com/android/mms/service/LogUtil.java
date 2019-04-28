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

package com.android.mms.service;

import android.util.Log;

/**
 * Logging utility
 */
public class LogUtil {
    private static final String TAG = "MmsService";

    public static void i(final String requestId, final String message) {
        Log.i(TAG, "[" + requestId + "] " + message);
    }

    public static void i(final String message) {
        Log.i(TAG, message);
    }

    public static void d(final String requestId, final String message) {
        Log.d(TAG, "[" + requestId + "] " + message);
    }

    public static void d(final String message) {
        Log.d(TAG, message);
    }

    public static void v(final String requestId, final String message) {
        Log.v(TAG, "[" + requestId + "] " + message);
    }

    public static void v(final String message) {
        Log.v(TAG, message);
    }

    public static void e(final String requestId, final String message, final Throwable t) {
        Log.e(TAG, "[" + requestId + "] " + message, t);
    }

    public static void e(final String message, final Throwable t) {
        Log.e(TAG, message, t);
    }

    public static void e(final String requestId, final String message) {
        Log.e(TAG, "[" + requestId + "] " + message);
    }

    public static void e(final String message) {
        Log.e(TAG, message);
    }

    public static void w(final String requestId, final String message, final Throwable t) {
        Log.w(TAG, "[" + requestId + "] " + message, t);
    }

    public static void w(final String message, final Throwable t) {
        Log.w(TAG, message, t);
    }

    public static void w(final String requestId, final String message) {
        Log.w(TAG, "[" + requestId + "] " + message);
    }

    public static void w(final String message) {
        Log.w(TAG, message);
    }

    public static boolean isLoggable(final int logLevel) {
        return Log.isLoggable(TAG, logLevel);
    }
}
