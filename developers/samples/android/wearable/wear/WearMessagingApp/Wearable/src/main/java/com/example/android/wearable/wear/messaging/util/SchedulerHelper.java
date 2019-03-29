/*
 * Copyright 2017 Google Inc.
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
package com.example.android.wearable.wear.messaging.util;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.os.SystemClock;
import android.util.Log;
import com.example.android.wearable.wear.messaging.chat.MockIncomingMessageReceiver;
import com.example.android.wearable.wear.messaging.model.Chat;
import com.example.android.wearable.wear.messaging.model.Message;
import java.util.concurrent.TimeUnit;

/**
 * Manage an alarm manager to trigger a notification after 5 seconds.
 *
 * <p>Demonstrates the receiving of a notification. In a real app, you would want to use FCM to
 * handle pushing notifications to a device.
 */
public class SchedulerHelper {

    private static final String TAG = "SchedulerHelper";

    public static void scheduleMockNotification(Context context, Chat chat, Message message) {
        AlarmManager alarmManger = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        PendingIntent alarmIntent = createPendingIntentToNotifyMessage(context, chat, message);

        Log.d(TAG, "Setting up alarm to be triggered shortly.");
        alarmManger.set(
                AlarmManager.ELAPSED_REALTIME_WAKEUP,
                SystemClock.elapsedRealtime() + TimeUnit.SECONDS.toMillis(5),
                alarmIntent);
    }

    private static PendingIntent createPendingIntentToNotifyMessage(
            Context context, Chat chat, Message message) {
        Intent intent = new Intent(context, MockIncomingMessageReceiver.class);
        intent.setAction(Constants.ACTION_RECEIVE_MESSAGE);
        intent.putExtra(Constants.EXTRA_CHAT, chat.getId());
        intent.putExtra(Constants.EXTRA_MESSAGE, message.getId());

        return PendingIntent.getBroadcast(context, 0, intent, PendingIntent.FLAG_CANCEL_CURRENT);
    }
}
