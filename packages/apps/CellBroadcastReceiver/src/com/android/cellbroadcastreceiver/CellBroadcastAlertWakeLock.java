/*
 * Copyright (C) 2012 The Android Open Source Project
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

package com.android.cellbroadcastreceiver;

import android.content.Context;
import android.os.PowerManager;
import android.os.PowerManager.WakeLock;
import android.util.Log;

/**
 * Manage wakelocks that are used by Cell broadcast receiver various services.
 */
class CellBroadcastAlertWakeLock {
    private static final String TAG = "CellBroadcastAlertWakeLock";

    private static final long MAX_PARTIAL_WAKELOCK_DURATION = 1000;                  // 1 sec
    private static final long MAX_SCREEN_BRIGHT_WAKELOCK_DURATION = 1000 * 60 * 5;   // 5 minutes

    private static WakeLock sPartialWakeLock;
    private static WakeLock sScreenBrightWakeLock;

    private CellBroadcastAlertWakeLock() {}

    static void acquirePartialWakeLock(Context context) {
        // Make sure we don't acquire the partial lock for more than 1 second. This lock
        // is currently used to make sure the alert reminder tone and vibration could be played
        // properly in timely manner.
        acquirePartialWakeLock(context, MAX_PARTIAL_WAKELOCK_DURATION);
    }

    static void acquirePartialWakeLock(Context context, long timeout) {
        if (sPartialWakeLock == null) {
            PowerManager pm = (PowerManager) context.getSystemService(Context.POWER_SERVICE);
            sPartialWakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, TAG);
        }

        if (!sPartialWakeLock.isHeld()) {
            sPartialWakeLock.acquire(timeout);
            Log.d(TAG, "acquired partial wakelock");
        }
    }

    static void releasePartialWakeLock() {
        if (sPartialWakeLock != null && sPartialWakeLock.isHeld()) {
            sPartialWakeLock.release();
            Log.d(TAG, "released partial wakelock");
        }
    }

    static void acquireScreenBrightWakeLock(Context context) {
        // Make sure we don't acquire the full lock for more than 5 minutes. This lock
        // is currently used by the main alert tone playing. Normally we hold the lock while
        // the audio is playing for about 10 ~ 20 seconds.
        acquireScreenBrightWakeLock(context, MAX_SCREEN_BRIGHT_WAKELOCK_DURATION);
    }

    static void acquireScreenBrightWakeLock(Context context, long timeout) {
        if (sScreenBrightWakeLock == null) {
            PowerManager pm = (PowerManager) context.getSystemService(Context.POWER_SERVICE);
            sScreenBrightWakeLock = pm.newWakeLock(
                    PowerManager.SCREEN_BRIGHT_WAKE_LOCK | PowerManager.ON_AFTER_RELEASE, TAG);
        }

        if (!sScreenBrightWakeLock.isHeld()) {
            sScreenBrightWakeLock.acquire(timeout);
            Log.d(TAG, "acquired screen bright wakelock");
        }
    }

    static void releaseScreenBrightWakeLock() {
        if (sScreenBrightWakeLock != null && sScreenBrightWakeLock.isHeld()) {
            sScreenBrightWakeLock.release();
            Log.d(TAG, "released screen bright wakelock");
        }
    }
}
