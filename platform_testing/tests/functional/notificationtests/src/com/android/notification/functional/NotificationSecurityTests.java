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

package com.android.notification.functional;

import android.app.Notification;
import android.app.NotificationManager;
import android.content.Context;
import android.service.notification.StatusBarNotification;
import android.support.test.uiautomator.UiDevice;
import android.test.InstrumentationTestCase;
import android.test.suitebuilder.annotation.MediumTest;
import android.util.Log;

public class NotificationSecurityTests extends InstrumentationTestCase {

    private static final String LOG_TAG = NotificationSecurityTests.class.getSimpleName();
    private static final int NOTIFICATION_ID_PUBLIC = 1;
    private NotificationManager mNotificationManager = null;
    private UiDevice mDevice = null;
    private Context mContext;
    private NotificationHelper mHelper;

    @Override
    public void setUp() throws Exception {
        super.setUp();
        mDevice = UiDevice.getInstance(getInstrumentation());
        mContext = getInstrumentation().getContext();
        mNotificationManager = (NotificationManager) mContext
                .getSystemService(Context.NOTIFICATION_SERVICE);
        Log.i(LOG_TAG, "set up notification...");
        mHelper = new NotificationHelper(mDevice, getInstrumentation(), mNotificationManager);
        mDevice.freezeRotation();
        mHelper.sleepAndWakeUpDevice();
    }

    @Override
    public void tearDown() throws Exception {
        mNotificationManager.cancelAll();
        mHelper.swipeUp();
        super.tearDown();
    }

    @MediumTest
    public void testVisibilityPublic() throws Exception {
        mHelper.enableNotificationViaAdb(true);
        String title = "Public Notification";
        Log.i(LOG_TAG, "Begin test visibility equals VISIBILITY_PUBLIC ");
        mHelper.sendNotification(NOTIFICATION_ID_PUBLIC, Notification.VISIBILITY_PUBLIC, title);
        if (mHelper.checkNotificationExistence(NOTIFICATION_ID_PUBLIC, false)) {
            fail("couldn't find posted notification id=" + NOTIFICATION_ID_PUBLIC);
        }
        StatusBarNotification[] sbns = mNotificationManager.getActiveNotifications();
        for (StatusBarNotification sbn : sbns) {
            Log.i(LOG_TAG, sbn.getNotification().extras.getString(Notification.EXTRA_TITLE));
            if (sbn.getId() == NOTIFICATION_ID_PUBLIC) {
                String sentTitle = sbn.getNotification().extras.getString(Notification.EXTRA_TITLE);
                assertEquals(sentTitle, title);
            }
        }
    }
}
