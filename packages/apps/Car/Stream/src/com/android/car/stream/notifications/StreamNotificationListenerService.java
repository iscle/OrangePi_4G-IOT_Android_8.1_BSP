/*
 * Copyright (c) 2016, The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.android.car.stream.notifications;

import android.service.notification.NotificationListenerService;
import android.service.notification.StatusBarNotification;
import android.util.Log;

/**
 * A listener to intercept notifications for the stream.
 */
public class StreamNotificationListenerService extends NotificationListenerService {
    private static final String TAG = "NotificationListener";

    @Override
    public void onNotificationPosted(StatusBarNotification sbn) {
        if (Log.isLoggable(TAG, Log.DEBUG)) {
            Log.d(TAG, "Notification received");
        }
    }

    @Override
    public void onNotificationRemoved(StatusBarNotification sbn) {
        if (Log.isLoggable(TAG, Log.DEBUG)) {
            Log.d(TAG, "Notification removed");
        }
    }
}
