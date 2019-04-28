/*
 * Copyright (C) 2013 The Android Open Source Project
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

package com.android.example.notificationshowcase;

import android.app.Activity;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.os.PowerManager;
import android.view.View;
import android.view.WindowManager;

public class FullScreenActivity extends Activity {
    private static final String TAG = "NotificationShowcase";

    public static final String EXTRA_ID = "id";
    private Handler mHandler = new Handler(Looper.getMainLooper());
    private PowerManager.WakeLock mWakeLock;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.full_screen);

        PowerManager pm = (PowerManager) getSystemService(Context.POWER_SERVICE);
        mWakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "Incoming Call");
        mWakeLock.acquire(15 * 1000);

        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON |
                WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD |
                WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED |
                WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON);

        final Intent intent = getIntent();
        if (intent != null && intent.hasExtra(EXTRA_ID)) {
            final int id = intent.getIntExtra(EXTRA_ID, -1);
            if (id >= 0) {
                NotificationManager noMa =
                        (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
                noMa.cancel(NotificationService.NOTIFICATION_ID + id);
            }
        }

        mHandler.postDelayed(new Runnable() {
            @Override
            public void run() {
                dismiss(null);
            }
        }, 30 * 1000);
    }

    public void dismiss(View v) {
        if (mWakeLock.isHeld()) {
            mWakeLock.release();
        }
        finish();
    }

    public static PendingIntent getPendingIntent(Context context, int id) {
        Intent fullScreenIntent = new Intent(context, FullScreenActivity.class);
        fullScreenIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);

        fullScreenIntent.putExtra(EXTRA_ID, id);
        PendingIntent pi = PendingIntent.getActivity(
                context, 22, fullScreenIntent, PendingIntent.FLAG_UPDATE_CURRENT);
        return pi;
    }
}
