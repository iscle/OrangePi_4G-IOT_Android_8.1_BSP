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
package com.android.cts.robot;

import android.app.Notification;
import android.app.Notification.Action;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.RemoteInput;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ShortcutInfo;
import android.content.pm.ShortcutManager;
import android.os.SystemClock;
import android.util.Log;

import java.util.ArrayList;
import java.util.List;


public class NotificationBot extends BroadcastReceiver {
    private static final String TAG = "NotificationBot";
    private static final String NOTIFICATION_CHANNEL_ID = TAG;
    private static final String EXTRA_ID = "ID";
    private static final String EXTRA_NOTIFICATION = "NOTIFICATION";
    private static final String ACTION_POST = "com.android.cts.robot.ACTION_POST";
    private static final String ACTION_CANCEL = "com.android.cts.robot.ACTION_CANCEL";
    private static final String ACTION_RESET_SETUP_NOTIFICATION =
            "com.android.cts.robot.ACTION_RESET_SETUP_NOTIFICATION";

    private static final String ACTION_INLINE_REPLY =
            "com.android.cts.robot.ACTION_INLINE_REPLY";

    private static final String EXTRA_RESET_REPLY_PACKAGE = "EXTRA_RESET_REPLY_PACKAGE";
    private static final String EXTRA_RESET_REPLY_ACTION = "EXTRA_RESET_REPLY_ACTION";
    private static final String EXTRA_NOTIFICATION_TITLE = "EXTRA_NOTIFICATION_TITLE";

    private static final String EXTRA_RESET_REPLY_ERROR = "EXTRA_RESET_REPLY_ERROR";

    private static final String EXTRA_RESET_REQUEST_INTENT = "EXTRA_RESET_REQUEST_INTENT";

    private static final String SUCCESS = "**SUCCESS**";

    @Override
    public void onReceive(Context context, Intent intent) {
        Log.i(TAG, "received intent: " + intent);
        if (ACTION_POST.equals(intent.getAction())) {
            Log.i(TAG, ACTION_POST);
            if (!intent.hasExtra(EXTRA_NOTIFICATION) || !intent.hasExtra(EXTRA_ID)) {
                Log.e(TAG, "received post action with missing content");
                return;
            }
            int id = intent.getIntExtra(EXTRA_ID, -1);
            Log.i(TAG, "id: " + id);
            Notification n = (Notification) intent.getParcelableExtra(EXTRA_NOTIFICATION);
            Log.i(TAG, "n: " + n);
            NotificationManager noMa =
                    (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
            noMa.notify(id, n);

        } else if (ACTION_CANCEL.equals(intent.getAction())) {
            Log.i(TAG, ACTION_CANCEL);
            int id = intent.getIntExtra(EXTRA_ID, -1);
            Log.i(TAG, "id: " + id);
            if (id < 0) {
                Log.e(TAG, "received cancel action with no ID");
                return;
            }
            NotificationManager noMa =
                    (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
            noMa.cancel(id);

        } else if (ACTION_RESET_SETUP_NOTIFICATION.equals(intent.getAction())) {
            testShortcutResetSetupNotification(context, intent);

        } else if (ACTION_INLINE_REPLY.equals(intent.getAction())) {
            testShortcutResetInlineReplyReceived(context, intent);

        } else {
            Log.i(TAG, "received unexpected action: " + intent.getAction());
        }
    }

    /**
     * Test start request from CTS verifier.  Show a notification with inline reply, which will
     * trigger {@link #testShortcutResetInlineReplyReceived}.
     */
    private static void testShortcutResetSetupNotification(Context context, Intent intent) {
        final NotificationManager nm = context.getSystemService(NotificationManager.class);
        nm.cancelAll();

        final ShortcutManager sm = context.getSystemService(ShortcutManager.class);

        final List<ShortcutInfo> EMPTY_LIST = new ArrayList<>();

        long timeout = SystemClock.elapsedRealtime() + 10 * 1000;

        // First, make sure this package is throttled.
        while (!sm.isRateLimitingActive()) {
            sm.setDynamicShortcuts(EMPTY_LIST);
            try {
                Thread.sleep(0);
            } catch (InterruptedException e) {
            }
            if (SystemClock.elapsedRealtime() >= timeout) {
                sendShortcutResetReply(context, intent,
                        "ShortcutMager rate-limiting not activated.");
                return;
            }
        }

        // Show a notification with inline reply.
        final PendingIntent receiverIntent =
                PendingIntent.getBroadcast(context, 0,
                        new Intent(ACTION_INLINE_REPLY)
                                .setComponent(new ComponentName(context, NotificationBot.class))
                                .putExtra(EXTRA_RESET_REQUEST_INTENT, intent),
                        PendingIntent.FLAG_UPDATE_CURRENT);
        final RemoteInput ri = new RemoteInput.Builder("result")
                .setLabel("Type something here and press send button").build();

        NotificationManager notificationManager =
                context.getSystemService(NotificationManager.class);
        notificationManager.createNotificationChannel(new NotificationChannel(
                NOTIFICATION_CHANNEL_ID, NOTIFICATION_CHANNEL_ID,
                NotificationManager.IMPORTANCE_DEFAULT));
        final Notification.Builder nb = new Notification.Builder(context, NOTIFICATION_CHANNEL_ID)
                .setContentTitle(intent.getStringExtra(EXTRA_NOTIFICATION_TITLE))
                .setSmallIcon(android.R.drawable.ic_popup_sync)
                .addAction(new Action.Builder(0,
                        "Type something here and press send button", receiverIntent)
                        .addRemoteInput(ri)
                        .build());
        notificationManager.notify(0, nb.build());
    }

    /**
     * Invoked when the inline reply from {@link #testShortcutResetSetupNotification} is performed.
     *
     * Check the shortcut manager rate-limiting state, and post the reply to CTS verifier.
     */
    private static void testShortcutResetInlineReplyReceived(Context context, Intent intent) {
        Log.i(TAG, "Inline reply received");

        final NotificationManager nm = context.getSystemService(NotificationManager.class);
        nm.cancelAll();

        // Close notification shade.
        context.sendBroadcast(new Intent(Intent.ACTION_CLOSE_SYSTEM_DIALOGS));

        // Check if rate-limiting has been reset.
        final ShortcutManager sm = context.getSystemService(ShortcutManager.class);

        String error;
        final boolean success = !sm.isRateLimitingActive();
        if (success) {
            error = SUCCESS;
        } else {
            error = "Inline reply received, but ShortcutManager rate-limiting is still active.";
        }

        // Send back the result.
        sendShortcutResetReply(context,
                intent.getParcelableExtra(EXTRA_RESET_REQUEST_INTENT), error);
    }

    /**
     * Reply an error message, or {@link #SUCCESS} for success, to CTS verifier for shortcut manager
     * reset rate-limiting test.

     * @param requestIntent original intent sent from the verifier to
     *     {@link #testShortcutResetSetupNotification}.
     * @param error error message, or {@link #SUCCESS} if success.
     */
    private static void sendShortcutResetReply(Context context, Intent requestIntent, String error) {
        final Intent replyIntent = new Intent();
        replyIntent.setAction(requestIntent.getStringExtra(EXTRA_RESET_REPLY_ACTION));
        replyIntent.putExtra(EXTRA_RESET_REPLY_ERROR, error);

        if (error != null) {
            Log.e(TAG, error);
        }

        context.sendBroadcast(replyIntent);
    }
}
