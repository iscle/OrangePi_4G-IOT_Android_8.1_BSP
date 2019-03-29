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
package com.android.cts.net.hostside;

import android.app.Notification;
import android.app.PendingIntent;
import android.app.PendingIntent.CanceledException;
import android.app.RemoteInput;
import android.content.ComponentName;
import android.os.Bundle;
import android.service.notification.NotificationListenerService;
import android.service.notification.StatusBarNotification;
import android.util.Log;

/**
 * NotificationListenerService implementation that executes the notification actions once they're
 * created.
 */
public class MyNotificationListenerService extends NotificationListenerService {
    private static final String TAG = "MyNotificationListenerService";

    @Override
    public void onListenerConnected() {
        Log.d(TAG, "onListenerConnected()");
    }

    @Override
    public void onNotificationPosted(StatusBarNotification sbn) {
        Log.d(TAG, "onNotificationPosted(): "  + sbn);
        if (!sbn.getPackageName().startsWith(getPackageName())) {
            Log.v(TAG, "ignoring notification from a different package");
            return;
        }
        final PendingIntentSender sender = new PendingIntentSender();
        final Notification notification = sbn.getNotification();
        if (notification.contentIntent != null) {
            sender.send("content", notification.contentIntent);
        }
        if (notification.deleteIntent != null) {
            sender.send("delete", notification.deleteIntent);
        }
        if (notification.fullScreenIntent != null) {
            sender.send("full screen", notification.fullScreenIntent);
        }
        if (notification.actions != null) {
            for (Notification.Action action : notification.actions) {
                sender.send("action", action.actionIntent);
                sender.send("action extras", action.getExtras());
                final RemoteInput[] remoteInputs = action.getRemoteInputs();
                if (remoteInputs != null && remoteInputs.length > 0) {
                    for (RemoteInput remoteInput : remoteInputs) {
                        sender.send("remote input extras", remoteInput.getExtras());
                    }
                }
            }
        }
        sender.send("notification extras", notification.extras);
    }

    static String getId() {
        return String.format("%s/%s", MyNotificationListenerService.class.getPackage().getName(),
                MyNotificationListenerService.class.getName());
    }

    static ComponentName getComponentName() {
        return new ComponentName(MyNotificationListenerService.class.getPackage().getName(),
                MyNotificationListenerService.class.getName());
    }

    private static final class PendingIntentSender {
        private PendingIntent mSentIntent = null;
        private String mReason = null;

        private void send(String reason, PendingIntent pendingIntent) {
            if (pendingIntent == null) {
                // Could happen on action that only has extras
                Log.v(TAG, "Not sending null pending intent for " + reason);
                return;
            }
            if (mSentIntent != null || mReason != null) {
                // Sanity check: make sure test case set up just one pending intent in the
                // notification, otherwise it could pass because another pending intent caused the
                // whitelisting.
                throw new IllegalStateException("Already sent a PendingIntent (" + mSentIntent
                        + ") for reason '" + mReason + "' when requested another for '" + reason
                        + "' (" + pendingIntent + ")");
            }
            Log.i(TAG, "Sending pending intent for " + reason + ":" + pendingIntent);
            try {
                pendingIntent.send();
                mSentIntent = pendingIntent;
                mReason = reason;
            } catch (CanceledException e) {
                Log.w(TAG, "Pending intent " + pendingIntent + " canceled");
            }
        }

        private void send(String reason, Bundle extras) {
            if (extras != null) {
                for (String key : extras.keySet()) {
                    Object value = extras.get(key);
                    if (value instanceof PendingIntent) {
                        send(reason + " with key '" + key + "'", (PendingIntent) value);
                    }
                }
            }
        }

    }
}
