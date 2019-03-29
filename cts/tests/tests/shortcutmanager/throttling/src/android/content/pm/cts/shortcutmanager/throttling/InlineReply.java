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
package android.content.pm.cts.shortcutmanager.throttling;

import static android.content.pm.cts.shortcutmanager.common.Constants.INLINE_REPLY_REMOTE_INPUT_CAPTION;
import static android.content.pm.cts.shortcutmanager.common.Constants.INLINE_REPLY_TITLE;

import android.app.Notification;
import android.app.Notification.Action;
import android.app.Notification.Builder;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.RemoteInput;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;

public class InlineReply extends BroadcastReceiver {
    private static final String EXTRA_REPLY_ACTION = "reply";

    @Override
    public void onReceive(Context context, Intent intent) {
        context.getSystemService(NotificationManager.class).cancelAll();
    }

    public static void showNotificationWithInlineReply(Context context) {
        final PendingIntent receiverIntent =
                PendingIntent.getBroadcast(context, 0,
                        new Intent().setComponent(new ComponentName(context, InlineReply.class)),
                        PendingIntent.FLAG_UPDATE_CURRENT);
        final RemoteInput ri = new RemoteInput.Builder("result").setLabel("Remote input").build();

        final Notification.Builder nb = new Builder(context)
                .setContentText("Test")
                .setContentTitle(INLINE_REPLY_TITLE)
                .setSmallIcon(android.R.drawable.ic_popup_sync)
                .addAction(new Action.Builder(0, INLINE_REPLY_REMOTE_INPUT_CAPTION, receiverIntent)
                        .addRemoteInput(ri)
                        .build());
        context.getSystemService(NotificationManager.class).notify(0, nb.build());
    }
}
