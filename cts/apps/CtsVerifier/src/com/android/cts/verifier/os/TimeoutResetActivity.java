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
package com.android.cts.verifier.os;

import android.app.Activity;
import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.WindowManager;

import java.lang.reflect.Field;

/**
 * Activity resets the screen timeout to its original timeout. Used devices without Device Admin.
 */
public class TimeoutResetActivity extends Activity {
    public static final String EXTRA_OLD_TIMEOUT = "com.android.cts.verifier.extra.OLD_TIMEOUT";
    /** Set the timeout to the default to reset the activity to if not specified. */
    public static final long FALLBACK_TIMEOUT = -1L;
    /**
     * Empirically determined buffer time in milliseconds between setting short timeout time and
     * resetting the timeout.
     */
    public static final long RESET_BUFFER_TIME = 2000L;
    /** Short timeout to trigger screen off. */
    public static final long SCREEN_OFF_TIMEOUT = 0L;
    public static final String TAG = TimeoutResetActivity.class.getSimpleName();

    private static long getUserActivityTimeout(WindowManager.LayoutParams params) {
        try {
            return getUserActivityTimeoutField(params).getLong(params);
        } catch (Exception e) {
            Log.e(TAG, "error loading the userActivityTimeout field", e);
            return -1;
        }
    }

    private static Field getUserActivityTimeoutField(WindowManager.LayoutParams params)
            throws NoSuchFieldException {
        return params.getClass().getField("userActivityTimeout");
    }

    private static void setUserActivityTimeout(WindowManager.LayoutParams params, long timeout) {
        try {
            getUserActivityTimeoutField(params).setLong(params, timeout);
            Log.d(TAG, "UserActivityTimeout set to " + timeout);
        } catch (Exception e) {
            Log.e(TAG, "error setting the userActivityTimeout field", e);
            throw new RuntimeException(e);
        }
    }

    public static void turnOffScreen(final Activity activity) {
        activity.runOnUiThread(new Runnable() {
            @Override
            public void run() {
                WindowManager.LayoutParams params = activity.getWindow().getAttributes();

                // to restore timeout after shutoff
                final long oldTimeout = getUserActivityTimeout(params);

                final long timeout = SCREEN_OFF_TIMEOUT;
                setUserActivityTimeout(params, timeout);

                // upon setting this, timeout will be reduced
                activity.getWindow().setAttributes(params);

                ((AlarmManager) activity.getSystemService(ALARM_SERVICE)).setExact(
                        AlarmManager.RTC,
                        System.currentTimeMillis() + RESET_BUFFER_TIME,
                        PendingIntent.getActivity(
                                activity.getApplicationContext(),
                                0,
                                new Intent(activity, TimeoutResetActivity.class)
                                        .putExtra(EXTRA_OLD_TIMEOUT, oldTimeout),
                                0));
            }
        });
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        long timeout = getIntent().getLongExtra(EXTRA_OLD_TIMEOUT, FALLBACK_TIMEOUT);
        if (timeout < 1000) { // in case the old timeout was super low by accident
            timeout = FALLBACK_TIMEOUT;
        }

        WindowManager.LayoutParams params = getWindow().getAttributes();
        setUserActivityTimeout(params, timeout);
        getWindow().setAttributes(params);

        finish();
    }
}
