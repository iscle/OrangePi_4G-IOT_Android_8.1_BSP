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

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.cts.shortcutmanager.common.Constants;
import android.content.pm.cts.shortcutmanager.common.ReplyUtil;
import android.os.IBinder;
import android.util.Log;

/**
 * Make sure that when a fg service is running, shortcut manager calls are not throttled.
 */
public class FgService extends Service {
    private static final String NOTIFICATION_CHANNEL_ID = "cts/shortcutmanager/FgService";

    public static void start(Context context, String replyAction) {
        final Intent i =
                new Intent().setComponent(new ComponentName(context, FgService.class))
                        .putExtra(Constants.EXTRA_REPLY_ACTION, replyAction);
        context.startService(i);
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {

        // Start as foreground.
        NotificationManager notificationManager = getSystemService(NotificationManager.class);
        notificationManager.createNotificationChannel(new NotificationChannel(
                NOTIFICATION_CHANNEL_ID, NOTIFICATION_CHANNEL_ID,
                NotificationManager.IMPORTANCE_DEFAULT));
        Notification notification =
                new Notification.Builder(getApplicationContext(), NOTIFICATION_CHANNEL_ID)
                        .setContentTitle("FgService")
                        .setSmallIcon(android.R.drawable.ic_popup_sync)
                        .build();
        startForeground(1, notification);

        final String replyAction = intent.getStringExtra(Constants.EXTRA_REPLY_ACTION);

        Log.i(ThrottledTests.TAG, Constants.TEST_FG_SERVICE_UNTHROTTLED);

        // Actual test.
        ReplyUtil.runTestAndReply(this, replyAction, () -> {
            ThrottledTests.assertCallNotThrottled(this);
        });

        // Stop self.
        stopForeground(true);
        stopSelf();

        return Service.START_NOT_STICKY;
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}
