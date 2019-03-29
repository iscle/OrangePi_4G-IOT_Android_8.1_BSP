/*
 * Copyright (C) 2017 The Android Open Source Project
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
package com.android.server.cts.procstatshelper;

import android.app.IntentService;
import android.app.Notification;
import android.content.Intent;
import android.util.Log;

public class ProcStatsHelperServiceBase extends IntentService {
    private static final String TAG = "ProcStatsHelperService";

    public ProcStatsHelperServiceBase() {
        super(TAG);
    }

    @Override
    public void onCreate() {
        super.onCreate();
        Log.e(TAG, "onCreate: " + getClass().getName());
    }

    @Override
    protected void onHandleIntent(Intent intent) {
        Log.e(TAG, "onHandleIntent: " + getClass().getName());

        // Run as a background service for 500 ms.
        try {
            Thread.sleep(500);
        } catch (InterruptedException e) {
            Log.e(TAG, "Caught exception", e);
        }

        Notification notification = new Notification.Builder(getApplicationContext())
                .setContentTitle("FgService")
                .setSmallIcon(android.R.drawable.ic_popup_sync)
                .build();
        startForeground(1, notification);
        // Run as a foreground service for 1 second.
        try {
            Thread.sleep(1000);
        } catch (InterruptedException e) {
            Log.e(TAG, "Caught exception", e);
        }
    }
}
