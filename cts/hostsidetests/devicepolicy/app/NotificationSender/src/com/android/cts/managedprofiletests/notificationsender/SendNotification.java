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

package com.android.cts.managedprofiletests.notificationsender;

import android.app.Activity;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.util.Log;

import java.lang.Override;

/**
 * A simple activity to post notifications to test notification
 * listener whitelists.
 */
public class SendNotification extends Activity {

    private static final String TAG = "ListenerTest";

    static final int NOTIFICATION_ID = 98765;
    static final String NOTIFICATION_CHANNEL = "NotificationListenerTest";

    @Override
    public void onCreate(Bundle icicle) {
        super.onCreate(icicle);
        Intent intent = getIntent();
        if (intent != null && "POST_NOTIFICATION".equals(intent.getAction())) {
            Log.i(TAG, "posting from " + android.os.Process.myUserHandle());
            sendNotification();
        } else if (intent != null && "CANCEL_NOTIFICATION".equals(intent.getAction())) {
            Log.i(TAG, "cancelling from " + android.os.Process.myUserHandle());
            cancelNotification();
        }
        finish();
    }

    private void sendNotification() {
        NotificationManager notificationManager =
                (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        notificationManager.createNotificationChannel(new NotificationChannel(
                        NOTIFICATION_CHANNEL, "Test channel", NotificationManager.IMPORTANCE_DEFAULT));
        notificationManager.notify(NOTIFICATION_ID,
                new Notification.Builder(getApplicationContext(), NOTIFICATION_CHANNEL)
                .setSmallIcon(R.raw.ic_contact_picture)
                .setContentTitle("Test title")
                .build());
    }

    private void cancelNotification() {
        NotificationManager notificationManager =
                (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        notificationManager.cancel(NOTIFICATION_ID);
        notificationManager.deleteNotificationChannel(NOTIFICATION_CHANNEL);
    }
}
