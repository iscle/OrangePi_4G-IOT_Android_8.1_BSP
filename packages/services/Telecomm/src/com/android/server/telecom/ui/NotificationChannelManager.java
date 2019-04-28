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
 * limitations under the License
 */

package com.android.server.telecom.ui;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.media.AudioAttributes;
import android.net.Uri;
import android.telecom.Log;

import com.android.server.telecom.R;

/**
 * Manages the {@link android.app.NotificationChannel}s for Telecom.
 */
public class NotificationChannelManager {
    public static final String CHANNEL_ID_NAME = "Telecom-";

    public static final String CHANNEL_ID_MISSED_CALLS = "TelecomMissedCalls";
    public static final String CHANNEL_ID_INCOMING_CALLS = "TelecomIncomingCalls";

    private BroadcastReceiver mLocaleChangeReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            Log.i(this, "Locale change; recreating channels.");
            createOrUpdateAll(context);
        }
    };

    public void createChannels(Context context) {
        context.registerReceiver(mLocaleChangeReceiver,
                new IntentFilter(Intent.ACTION_LOCALE_CHANGED));

        createOrUpdateAll(context);
    }

    private void createOrUpdateAll(Context context) {
        createOrUpdateChannel(context, CHANNEL_ID_MISSED_CALLS);
        createOrUpdateChannel(context, CHANNEL_ID_INCOMING_CALLS);
    }

    private void createOrUpdateChannel(Context context, String channelId) {
        NotificationChannel channel = createChannel(context, channelId);
        getNotificationManager(context).createNotificationChannel(channel);
    }

    private NotificationChannel createChannel(Context context, String channelId) {
        Uri silentRingtone = Uri.parse("");

        CharSequence name = "";
        int importance = NotificationManager.IMPORTANCE_DEFAULT;
        boolean canShowBadge = false;
        boolean lights = false;
        boolean vibration = false;
        Uri sound = silentRingtone;
        switch (channelId) {
            case CHANNEL_ID_INCOMING_CALLS:
                name = context.getText(R.string.notification_channel_incoming_call);
                importance = NotificationManager.IMPORTANCE_MAX;
                canShowBadge = false;
                lights = true;
                vibration = false;
                sound = silentRingtone;
                break;
            case CHANNEL_ID_MISSED_CALLS:
                name = context.getText(R.string.notification_channel_missed_call);
                importance = NotificationManager.IMPORTANCE_DEFAULT;
                canShowBadge = true;
                lights = true;
                vibration = true;
                sound = silentRingtone;
                break;
        }

        NotificationChannel channel = new NotificationChannel(channelId, name, importance);
        channel.setShowBadge(canShowBadge);
        if (sound != null) {
            channel.setSound(
                    sound,
                    new AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_NOTIFICATION)
                            .build());
        }
        channel.enableLights(lights);
        channel.enableVibration(vibration);
        return channel;
    }

    private NotificationManager getNotificationManager(Context context) {
        return context.getSystemService(NotificationManager.class);
    }
}
