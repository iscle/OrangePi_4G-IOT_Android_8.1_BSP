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
 * limitations under the License
 */

package com.android.server.telecom;

/**
 * Create a container for the Android Logging class so that it can be mocked in testing.
 */
public class SystemLoggingContainer {

    public void v(String TAG, String msg) {
        android.util.Slog.v(TAG, msg);
    }

    public void d(String TAG, String msg) {
        android.util.Slog.d(TAG, msg);
    }

    public void i(String TAG, String msg) {
        android.util.Slog.i(TAG, msg);
    }

    public void w(String TAG, String msg) {
        android.util.Slog.w(TAG, msg);
    }

    public void e(String TAG, String msg, Throwable tr) {
        android.util.Slog.e(TAG, msg, tr);
    }

    public void wtf(String TAG, String msg, Throwable tr) {
        android.util.Slog.wtf(TAG, msg, tr);
    }
}
