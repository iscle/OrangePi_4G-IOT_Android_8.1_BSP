/*
 * Copyright (C) 2015 The Android Open Source Project
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
package com.android.car;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.Context;
import android.os.Build;
import android.util.Log;

import com.android.internal.annotations.GuardedBy;

import java.util.HashSet;
import java.util.Set;

/**
 * Class used to notify user about CAN bus failure.
 */
final class CanBusErrorNotifier {
    private static final String TAG = CarLog.TAG_CAN_BUS + ".NOTIFIER";
    private static final int NOTIFICATION_ID = 1;
    private static final boolean IS_RELEASE_BUILD = "user".equals(Build.TYPE);

    private final Context mContext;
    private final NotificationManager mNotificationManager;

    // Contains a set of objects that reported failure. The notification will be hidden only when
    // this set is empty (all reported objects are in love and peace with the vehicle).
    @GuardedBy("this")
    private final Set<Object> mReportedObjects = new HashSet<>();

    CanBusErrorNotifier(Context context) {
        mNotificationManager = (NotificationManager) context.getSystemService(
                Context.NOTIFICATION_SERVICE);
        mContext = context;
    }

    public void removeFailureReport(Object sender) {
        setCanBusFailure(false, sender);
    }

    public void reportFailure(Object sender) {
        setCanBusFailure(true, sender);
    }

    private void setCanBusFailure(boolean failed, Object sender) {
        boolean shouldShowNotification;
        synchronized (this) {
            boolean changed = failed
                    ? mReportedObjects.add(sender) : mReportedObjects.remove(sender);

            if (!changed) {
                return;
            }

            shouldShowNotification = !mReportedObjects.isEmpty();
        }

        if (Log.isLoggable(TAG, Log.INFO)) {
            Log.i(TAG, "Changing CAN bus failure state to " + shouldShowNotification);
        }

        if (shouldShowNotification) {
            showNotification();
        } else {
            hideNotification();
        }
    }

    private void showNotification() {
        if (IS_RELEASE_BUILD) {
            // TODO: for user, we should show message to take car to the dealer. bug:32096297
            return;
        }
        Notification notification =
                new Notification.Builder(mContext, NotificationChannel.DEFAULT_CHANNEL_ID)
                        .setContentTitle(mContext.getString(R.string.car_can_bus_failure))
                        .setContentText(mContext.getString(R.string.car_can_bus_failure_desc))
                        .setSmallIcon(R.drawable.car_ic_error)
                        .setOngoing(true)
                        .build();
        mNotificationManager.notify(TAG, NOTIFICATION_ID, notification);
    }

    private void hideNotification() {
        if (IS_RELEASE_BUILD) {
            // TODO: for user, we should show message to take car to the dealer. bug:32096297
            return;
        }
        mNotificationManager.cancel(TAG, NOTIFICATION_ID);
    }
}
