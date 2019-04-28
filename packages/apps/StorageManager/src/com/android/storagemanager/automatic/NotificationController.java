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

package com.android.storagemanager.automatic;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.Resources;
import android.os.SystemProperties;
import android.provider.Settings;
import android.support.annotation.VisibleForTesting;
import android.support.v4.os.BuildCompat;

import com.android.storagemanager.R;

import java.util.concurrent.TimeUnit;

/**
 * NotificationController handles the responses to the Automatic Storage Management low storage
 * notification.
 */
public class NotificationController extends BroadcastReceiver {
    /**
     * Intent action for if the user taps "Turn on" for the automatic storage manager.
     */
    public static final String INTENT_ACTION_ACTIVATE_ASM =
            "com.android.storagemanager.automatic.ACTIVATE";

    /**
     * Intent action for if the user swipes the notification away.
     */
    public static final String INTENT_ACTION_DISMISS =
            "com.android.storagemanager.automatic.DISMISS";

    /**
     * Intent action for if the user explicitly hits "No thanks" on the notification.
     */
    public static final String INTENT_ACTION_NO_THANKS =
            "com.android.storagemanager.automatic.NO_THANKS";

    /**
     * Intent action to maybe show the ASM upsell notification.
     */
    public static final String INTENT_ACTION_SHOW_NOTIFICATION =
            "com.android.storagemanager.automatic.show_notification";

    /**
     * Intent action for forcefully showing the notification, even if the conditions are not valid.
     */
    private static final String INTENT_ACTION_DEBUG_NOTIFICATION =
            "com.android.storagemanager.automatic.DEBUG_SHOW_NOTIFICATION";

    /** Intent action for if the user taps on the notification. */
    @VisibleForTesting
    static final String INTENT_ACTION_TAP = "com.android.storagemanager.automatic.SHOW_SETTINGS";

    /**
     * Intent extra for the notification id.
     */
    public static final String INTENT_EXTRA_ID = "id";

    private static final String SHARED_PREFERENCES_NAME = "NotificationController";
    private static final String NOTIFICATION_NEXT_SHOW_TIME = "notification_next_show_time";
    private static final String NOTIFICATION_SHOWN_COUNT = "notification_shown_count";
    private static final String NOTIFICATION_DISMISS_COUNT = "notification_dismiss_count";
    private static final String STORAGE_MANAGER_PROPERTY = "ro.storage_manager.enabled";
    private static final String CHANNEL_ID = "storage";

    private static final long DISMISS_DELAY = TimeUnit.DAYS.toMillis(14);
    private static final long NO_THANKS_DELAY = TimeUnit.DAYS.toMillis(90);
    private static final long MAXIMUM_SHOWN_COUNT = 4;
    private static final long MAXIMUM_DISMISS_COUNT = 9;
    private static final int NOTIFICATION_ID = 0;

    // Keeps the time for test purposes.
    private Clock mClock;

    @Override
    public void onReceive(Context context, Intent intent) {
        switch (intent.getAction()) {
            case INTENT_ACTION_ACTIVATE_ASM:
                Settings.Secure.putInt(context.getContentResolver(),
                        Settings.Secure.AUTOMATIC_STORAGE_MANAGER_ENABLED,
                        1);
                // Provide a warning if storage manager is not defaulted on.
                if (!SystemProperties.getBoolean(STORAGE_MANAGER_PROPERTY, false)) {
                    Intent warningIntent = new Intent(context, WarningDialogActivity.class);
                    context.startActivity(warningIntent);
                }
                break;
            case INTENT_ACTION_NO_THANKS:
                delayNextNotification(context, NO_THANKS_DELAY);
                break;
            case INTENT_ACTION_DISMISS:
                delayNextNotification(context, DISMISS_DELAY);
                break;
            case INTENT_ACTION_SHOW_NOTIFICATION:
                maybeShowNotification(context);
                return;
            case INTENT_ACTION_DEBUG_NOTIFICATION:
                showNotification(context);
                return;
            case INTENT_ACTION_TAP:
                Intent storageIntent = new Intent(Settings.ACTION_INTERNAL_STORAGE_SETTINGS);
                storageIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                context.startActivity(storageIntent);
                break;
        }
        cancelNotification(context, intent);
    }

    /**
     * Sets a time provider for the controller.
     * @param clock The time provider.
     */
    protected void setClock(Clock clock) {
        mClock = clock;
    }

    /**
     * If the conditions for showing the activation notification are met, show the activation
     * notification.
     * @param context Context to use for getting resources and to display the notification.
     */
    private void maybeShowNotification(Context context) {
        if (shouldShowNotification(context)) {
            showNotification(context);
        }
    }

    private boolean shouldShowNotification(Context context) {
        SharedPreferences sp = context.getSharedPreferences(
                SHARED_PREFERENCES_NAME,
                Context.MODE_PRIVATE);
        int timesShown = sp.getInt(NOTIFICATION_SHOWN_COUNT, 0);
        int timesDismissed = sp.getInt(NOTIFICATION_DISMISS_COUNT, 0);
        if (timesShown >= MAXIMUM_SHOWN_COUNT || timesDismissed >= MAXIMUM_DISMISS_COUNT) {
            return false;
        }

        long nextTimeToShow = sp.getLong(NOTIFICATION_NEXT_SHOW_TIME, 0);

        return getCurrentTime() >= nextTimeToShow;
    }

    private void showNotification(Context context) {
        Resources res = context.getResources();
        Intent noThanksIntent = getBaseIntent(context, INTENT_ACTION_NO_THANKS);
        noThanksIntent.putExtra(INTENT_EXTRA_ID, NOTIFICATION_ID);
        Notification.Action.Builder cancelAction = new Notification.Action.Builder(null,
                res.getString(R.string.automatic_storage_manager_cancel_button),
                PendingIntent.getBroadcast(context, 0, noThanksIntent,
                        PendingIntent.FLAG_UPDATE_CURRENT));


        Intent activateIntent = getBaseIntent(context, INTENT_ACTION_ACTIVATE_ASM);
        activateIntent.putExtra(INTENT_EXTRA_ID, NOTIFICATION_ID);
        Notification.Action.Builder activateAutomaticAction = new Notification.Action.Builder(null,
                res.getString(R.string.automatic_storage_manager_activate_button),
                PendingIntent.getBroadcast(context, 0, activateIntent,
                        PendingIntent.FLAG_UPDATE_CURRENT));

        Intent dismissIntent = getBaseIntent(context, INTENT_ACTION_DISMISS);
        dismissIntent.putExtra(INTENT_EXTRA_ID, NOTIFICATION_ID);
        PendingIntent deleteIntent = PendingIntent.getBroadcast(context, 0,
                dismissIntent,
                PendingIntent.FLAG_ONE_SHOT);

        Intent contentIntent = getBaseIntent(context, INTENT_ACTION_TAP);
        contentIntent.putExtra(INTENT_EXTRA_ID, NOTIFICATION_ID);
        PendingIntent tapIntent = PendingIntent.getBroadcast(context, 0,  contentIntent,
                PendingIntent.FLAG_ONE_SHOT);

        Notification.Builder builder;
        // We really should only have the path with the notification channel set. The other path is
        // only for legacy Robolectric reasons -- Robolectric does not have the Notification
        // builder with a channel id, so it crashes when it hits that code path.
        if (BuildCompat.isAtLeastO()) {
            makeNotificationChannel(context);
            builder = new Notification.Builder(context, CHANNEL_ID);
        } else {
            builder = new Notification.Builder(context);
        }

        builder.setSmallIcon(R.drawable.ic_settings_24dp)
                .setContentTitle(
                        res.getString(R.string.automatic_storage_manager_notification_title))
                .setContentText(
                        res.getString(R.string.automatic_storage_manager_notification_summary))
                .setStyle(
                        new Notification.BigTextStyle()
                                .bigText(
                                        res.getString(
                                                R.string
                                                        .automatic_storage_manager_notification_summary)))
                .addAction(cancelAction.build())
                .addAction(activateAutomaticAction.build())
                .setContentIntent(tapIntent)
                .setDeleteIntent(deleteIntent)
                .setLocalOnly(true);

        NotificationManager manager =
                ((NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE));
        manager.notify(NOTIFICATION_ID, builder.build());
    }

    private void makeNotificationChannel(Context context) {
        final NotificationManager nm = context.getSystemService(NotificationManager.class);
        final NotificationChannel channel =
                new NotificationChannel(
                        CHANNEL_ID,
                        context.getString(R.string.app_name),
                        NotificationManager.IMPORTANCE_LOW);
        nm.createNotificationChannel(channel);
    }

    private void cancelNotification(Context context, Intent intent) {
        if (intent.getAction() == INTENT_ACTION_DISMISS) {
            incrementNotificationDismissedCount(context);
        } else {
            incrementNotificationShownCount(context);
        }

        int id = intent.getIntExtra(INTENT_EXTRA_ID, -1);
        if (id == -1) {
            return;
        }
        NotificationManager manager = (NotificationManager) context
                .getSystemService(Context.NOTIFICATION_SERVICE);
        manager.cancel(id);
    }

    private void incrementNotificationShownCount(Context context) {
        SharedPreferences sp = context.getSharedPreferences(SHARED_PREFERENCES_NAME,
                Context.MODE_PRIVATE);
        SharedPreferences.Editor editor = sp.edit();
        int shownCount = sp.getInt(NotificationController.NOTIFICATION_SHOWN_COUNT, 0) + 1;
        editor.putInt(NotificationController.NOTIFICATION_SHOWN_COUNT, shownCount);
        editor.apply();
    }

    private void incrementNotificationDismissedCount(Context context) {
        SharedPreferences sp = context.getSharedPreferences(SHARED_PREFERENCES_NAME,
                Context.MODE_PRIVATE);
        SharedPreferences.Editor editor = sp.edit();
        int dismissCount = sp.getInt(NOTIFICATION_DISMISS_COUNT, 0) + 1;
        editor.putInt(NOTIFICATION_DISMISS_COUNT, dismissCount);
        editor.apply();
    }

    private void delayNextNotification(Context context, long timeInMillis) {
        SharedPreferences sp = context.getSharedPreferences(SHARED_PREFERENCES_NAME,
                Context.MODE_PRIVATE);
        SharedPreferences.Editor editor = sp.edit();
        editor.putLong(NOTIFICATION_NEXT_SHOW_TIME,
                getCurrentTime() + timeInMillis);
        editor.apply();
    }

    private long getCurrentTime() {
        if (mClock == null) {
            mClock = new Clock();
        }

        return mClock.currentTimeMillis();
    }

    @VisibleForTesting
    Intent getBaseIntent(Context context, String action) {
        return new Intent(context, NotificationController.class).setAction(action);
    }

    /**
     * Clock provides the current time.
     */
    protected static class Clock {
        /**
         * Returns the current time in milliseconds.
         */
        public long currentTimeMillis() {
            return System.currentTimeMillis();
        }
    }
}
