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
import android.support.test.uiautomator.UiDevice;
import android.support.test.uiautomator.By;
import android.support.test.uiautomator.Until;
import android.test.InstrumentationTestCase;
import android.test.suitebuilder.annotation.LargeTest;
import android.util.Log;

public class NotificationSecurityLargeTests extends InstrumentationTestCase {

    private static final String LOG_TAG = NotificationSecurityLargeTests.class.getSimpleName();
    private static final int SHORT_TIMEOUT = 200;
    private static final int NOTIFICATION_ID_SECRET = 1;
    private static final int NOTIFICATION_ID_PUBLIC = 2;
    private static final int PIN = 1234;
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
        Log.v(LOG_TAG, "set up notification...");
        mHelper = new NotificationHelper(mDevice, getInstrumentation(), mNotificationManager);
        mHelper.setScreenLockPin(PIN);
        mHelper.sleepAndWakeUpDevice();
    }

    @Override
    public void tearDown() throws Exception {
        mHelper.swipeUp();
        Thread.sleep(SHORT_TIMEOUT);
        mHelper.unlockScreenByPin(PIN);
        mHelper.removeScreenLock(PIN, "Swipe");
        mNotificationManager.cancelAll();
        super.tearDown();
    }

    @LargeTest
    public void testVisibilitySecret() throws Exception {
        String title = "Secret Title";
        Log.i(LOG_TAG, "Begin test visibility equals VISIBILITY_SECRET ");
        mHelper.sendNotification(NOTIFICATION_ID_SECRET, Notification.VISIBILITY_SECRET, title);
        if (!mHelper.checkNotificationExistence(NOTIFICATION_ID_SECRET, true)) {
            fail("couldn't find posted notification id=" + NOTIFICATION_ID_PUBLIC);
        }
        assertFalse(mDevice.wait(Until.hasObject(By.res("android:id/title").text(title)),
                SHORT_TIMEOUT));
    }

    @LargeTest
    public void testVisibilityPrivate() throws Exception {
        Log.i(LOG_TAG, "Begin test visibility equals VISIBILITY_PRIVATE ");
        mHelper.sendNotification(NOTIFICATION_ID_PUBLIC, Notification.VISIBILITY_PRIVATE, "");
        if (!mHelper.checkNotificationExistence(NOTIFICATION_ID_PUBLIC, true)) {
            fail("couldn't find posted notification id=" + NOTIFICATION_ID_PUBLIC);
        }
        assertNotNull(
                Until.findObject(By.res("android:id/title").text("Contents hidden"))); 
    }
}
