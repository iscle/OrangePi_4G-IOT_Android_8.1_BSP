/*
 * Copyright (C) 2010 The Android Open Source Project
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

package com.android.providers.calendar;

import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.ContentProvider;
import android.content.Context;
import android.content.IContentProvider;
import android.content.Intent;
import android.provider.CalendarContract;
import android.util.Log;
import android.util.Slog;

public class CalendarProviderBroadcastReceiver extends BroadcastReceiver {
    private static final String TAG = CalendarProvider2.TAG;

    @Override
    public void onReceive(Context context, Intent intent) {
        String action = intent.getAction();
        if (!CalendarAlarmManager.ACTION_CHECK_NEXT_ALARM.equals(action)
                && !CalendarContract.ACTION_EVENT_REMINDER.equals(action)) {
            Log.e(TAG, "Received invalid intent: " + intent);
            setResultCode(Activity.RESULT_CANCELED);
            return;
        }
        if (Log.isLoggable(TAG, Log.DEBUG)) {
            Log.d(TAG, "Received intent: " + intent);
        }
        final IContentProvider iprovider =
                context.getContentResolver().acquireProvider(CalendarContract.AUTHORITY);
        final ContentProvider cprovider = ContentProvider.coerceToLocalContentProvider(iprovider);

        if (!(cprovider instanceof CalendarProvider2)) {
            Slog.wtf(TAG, "CalendarProvider2 not found in CalendarProviderBroadcastReceiver.");
            return;
        }

        final CalendarProvider2 provider = (CalendarProvider2) cprovider;

        final PendingResult result = goAsync();

        new Thread(() -> {
            // Schedule the next alarm. Please be noted that for ACTION_EVENT_REMINDER broadcast,
            // we never remove scheduled alarms.
            final boolean removeAlarms = intent
                    .getBooleanExtra(CalendarAlarmManager.KEY_REMOVE_ALARMS, false);
            provider.getOrCreateCalendarAlarmManager().runScheduleNextAlarm(removeAlarms, provider);

            if (Log.isLoggable(TAG, Log.DEBUG)) {
                Log.d(TAG, "Next alarm set.");
            }

            result.setResultCode(Activity.RESULT_OK);
            result.finish();
        }).start();

    }
}
