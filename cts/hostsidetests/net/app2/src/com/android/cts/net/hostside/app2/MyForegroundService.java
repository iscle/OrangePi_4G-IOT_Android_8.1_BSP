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
package com.android.cts.net.hostside.app2;

import static com.android.cts.net.hostside.app2.Common.TAG;
import static com.android.cts.net.hostside.app2.Common.TEST_PKG;

import android.R;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Intent;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.IBinder;
import android.os.RemoteException;
import android.util.Log;

import com.android.cts.net.hostside.INetworkStateObserver;

/**
 * Service used to change app state to FOREGROUND_SERVICE.
 */
public class MyForegroundService extends Service {
    private static final String NOTIFICATION_CHANNEL_ID = "cts/MyForegroundService";
    private static final int FLAG_START_FOREGROUND = 1;
    private static final int FLAG_STOP_FOREGROUND = 2;

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        Log.v(TAG, "MyForegroundService.onStartCommand(): " + intent);
        NotificationManager notificationManager = getSystemService(NotificationManager.class);
        notificationManager.createNotificationChannel(new NotificationChannel(
                NOTIFICATION_CHANNEL_ID, NOTIFICATION_CHANNEL_ID,
                NotificationManager.IMPORTANCE_DEFAULT));
        switch (intent.getFlags()) {
            case FLAG_START_FOREGROUND:
                Log.d(TAG, "Starting foreground");
                startForeground(42, new Notification.Builder(this, NOTIFICATION_CHANNEL_ID)
                        .setSmallIcon(R.drawable.ic_dialog_alert) // any icon is fine
                        .build());
                Common.notifyNetworkStateObserver(this, intent);
                break;
            case FLAG_STOP_FOREGROUND:
                Log.d(TAG, "Stopping foreground");
                stopForeground(true);
                break;
            default:
                Log.wtf(TAG, "Invalid flag on intent " + intent);
        }
        return START_STICKY;
    }
}
