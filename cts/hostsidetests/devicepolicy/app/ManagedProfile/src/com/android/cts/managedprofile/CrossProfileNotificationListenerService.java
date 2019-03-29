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
package com.android.cts.managedprofile;

import static com.android.cts.managedprofile.NotificationListenerTest.ACTION_NOTIFICATION_POSTED;
import static com.android.cts.managedprofile.NotificationListenerTest.ACTION_NOTIFICATION_REMOVED;
import static com.android.cts.managedprofile.NotificationListenerTest.ACTION_LISTENER_CONNECTED;

import android.content.Intent;
import android.service.notification.NotificationListenerService;
import android.service.notification.StatusBarNotification;
import android.support.v4.content.LocalBroadcastManager;
import android.util.Log;

public class CrossProfileNotificationListenerService extends NotificationListenerService {

    static final String NOTIFICATION_CHANNEL = "NotificationListenerTest";
    private static final String TAG = NotificationListenerTest.TAG;

    @Override
    public void onNotificationPosted(StatusBarNotification sbn) {
        Log.i(TAG, "onNotificationPosted(" + sbn + ")");
        sendBroadcastForNotification(sbn, ACTION_NOTIFICATION_POSTED);
    }

    @Override
    public void onNotificationRemoved(StatusBarNotification sbn) {
        Log.i(TAG, "onNotificationRemoved(" + sbn + ")");
        sendBroadcastForNotification(sbn, ACTION_NOTIFICATION_REMOVED);
    }

    @Override
    public void onListenerConnected() {
        Log.i(TAG, "onListenerConnected() " + android.os.Process.myPid());
        LocalBroadcastManager.getInstance(this).sendBroadcast(
                new Intent(ACTION_LISTENER_CONNECTED));
    }

    @Override
    public void onListenerDisconnected() {
        Log.i(TAG, "onListenerDisconnected()");
    }

    private void sendBroadcastForNotification(StatusBarNotification sbn, String action) {
        if (NOTIFICATION_CHANNEL.equals(sbn.getNotification().getChannelId())) {
            LocalBroadcastManager.getInstance(this).sendBroadcast(new Intent(action));
        } else {
            Log.i(TAG, "Notification is for different channel "
                    + sbn.getNotification().getChannelId());
        }
    }
}
