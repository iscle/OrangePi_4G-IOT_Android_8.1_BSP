/*
 * Copyright (C) 2014 The Android Open Source Project
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

package com.android.cts.verifier.camera.its;

import android.util.Log;

public class Logt {
    public static void i(String tag, String msg) {
        long t = android.os.SystemClock.elapsedRealtime();
        Log.i(tag, String.format("[%d] %s", t, msg));
    }
    public static void e(String tag, String msg) {
        long t = android.os.SystemClock.elapsedRealtime();
        Log.e(tag, String.format("[%d] %s", t, msg));
    }
    public static void w(String tag, String msg) {
        long t = android.os.SystemClock.elapsedRealtime();
        Log.w(tag, String.format("[%d] %s", t, msg));
    }
    public static void e(String tag, String msg, Throwable tr) {
        long t = android.os.SystemClock.elapsedRealtime();
        Log.e(tag, String.format("[%d] %s", t, msg), tr);
    }
}

