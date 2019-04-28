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

package android.system.helpers;

import android.app.IntentService;
import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.RemoteInput;
import android.content.Context;
import android.content.Intent;
import android.os.Handler;
import android.service.notification.StatusBarNotification;
import android.support.test.InstrumentationRegistry;
import android.support.test.uiautomator.By;
import android.support.test.uiautomator.Direction;
import android.support.test.uiautomator.UiDevice;
import android.support.test.uiautomator.UiObject2;
import android.view.KeyEvent;
import android.view.inputmethod.InputMethodManager;
import android.test.suitebuilder.annotation.LargeTest;
import android.test.suitebuilder.annotation.MediumTest;
import android.util.Log;
import android.view.inputmethod.InputMethodManager;
import android.widget.Toast;
import java.util.ArrayList;
import java.util.List;

/**
 * Implement common helper methods for Notification.
 */
public class NotificationHelper {
    private static final String LOG_TAG = NotificationHelper.class.getSimpleName();
    public static final int SHORT_TIMEOUT = 200;
    public static final int LONG_TIMEOUT = 2000;
    private static NotificationHelper sInstance = null;
    private Context mContext = null;
    private UiDevice mDevice = null;

    public NotificationHelper() {
        mContext = InstrumentationRegistry.getTargetContext();
        mDevice = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation());
    }

    public static NotificationHelper getInstance() {
        if (sInstance == null) {
            sInstance = new NotificationHelper();
        }
        return sInstance;
    }

    /**
     * check if notification exists
     * @param id notification id
     * @param mNotificationManager NotificationManager
     * @return true/false
     * @throws Exception
     */
    public boolean checkNotificationExistence(int id, NotificationManager mNotificationManager)
            throws Exception {
        boolean isFound = false;
        for (int tries = 3; tries-- > 0;) {
            isFound = false;
            StatusBarNotification[] sbns = mNotificationManager.getActiveNotifications();
            for (StatusBarNotification sbn : sbns) {
                if (sbn.getId() == id) {
                    isFound = true;
                    break;
                }
            }
            if (isFound) {
                break;
            }
            Thread.sleep(SHORT_TIMEOUT);
        }
        Log.i(LOG_TAG, "checkNotificationExistence..." + isFound);
        return isFound;
    }

    /**
     * send out a group of notifications
     * @param lists notification list for a group of notifications which includes two child
     *            notifications and one summary notification
     * @param groupKey the group key of group notification
     * @param mNotificationManager NotificationManager
     * @throws Exception
     */
    public void sendBundlingNotifications(List<Integer> lists, String groupKey,
            NotificationManager mNotificationManager) throws Exception {
        Notification childNotification = new Notification.Builder(mContext)
                .setContentTitle(lists.get(1).toString())
                .setSmallIcon(android.R.drawable.stat_notify_chat)
                .setContentText("test1")
                .setWhen(System.currentTimeMillis())
                .setGroup(groupKey)
                .build();
        mNotificationManager.notify(lists.get(1),
                childNotification);
        childNotification = new Notification.Builder(mContext)
                .setContentTitle(lists.get(2).toString())
                .setContentText("test2")
                .setSmallIcon(android.R.drawable.stat_notify_chat)
                .setWhen(System.currentTimeMillis())
                .setGroup(groupKey)
                .build();
        mNotificationManager.notify(lists.get(2),
                childNotification);
        Notification notification = new Notification.Builder(mContext)
                .setContentTitle(lists.get(0).toString())
                .setSubText(groupKey)
                .setSmallIcon(android.R.drawable.stat_notify_chat)
                .setGroup(groupKey)
                .setGroupSummary(true)
                .build();
        mNotificationManager.notify(lists.get(0),
                notification);
    }

    /**
     * send out a notification with inline reply
     * @param notificationId An identifier for this notification
     * @param title notification title
     * @param inLineReply inline reply text
     * @param mNotificationManager NotificationManager
     */
    public void sendNotificationsWithInLineReply(int notificationId, String title,
            String inLineReply,PendingIntent pendingIntent, NotificationManager mNotificationManager) {
        Notification.Action action = new Notification.Action.Builder(
                android.R.drawable.stat_notify_chat, "Reply",
                pendingIntent).addRemoteInput(new RemoteInput.Builder(inLineReply)
                        .setLabel("Quick reply").build())
                        .build();
        Notification.Builder n = new Notification.Builder(mContext)
                .setContentTitle(Integer.toString(notificationId))
                .setContentText(title)
                .setWhen(System.currentTimeMillis())
                .setSmallIcon(android.R.drawable.stat_notify_chat)
                .addAction(action)
                .setDefaults(Notification.DEFAULT_VIBRATE);
        mNotificationManager.notify(notificationId, n.build());
    }

    /**
     * dismiss notification
     * @param mNotificationManager NotificationManager
     */
    public void dismissNotifications(NotificationManager mNotificationManager){
        mNotificationManager.cancelAll();
    }

    /**
     * open notification shade
     */
    public void openNotification(){
        mDevice.openNotification();
    }
}