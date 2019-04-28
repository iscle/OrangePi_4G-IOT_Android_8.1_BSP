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

import android.app.NotificationManager;
import android.content.Context;
import android.os.RemoteException;
import android.support.test.uiautomator.By;
import android.support.test.uiautomator.UiDevice;
import android.support.test.uiautomator.UiObject2;
import android.support.test.uiautomator.Until;
import android.test.InstrumentationTestCase;
import android.test.suitebuilder.annotation.MediumTest;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class NotificationBundlingTests extends InstrumentationTestCase {
    private static final int SHORT_TIMEOUT = 200;
    private static final int LONG_TIMEOUT = 2000;
    private static final int GROUP_NOTIFICATION_ID = 1;
    private static final int CHILD_NOTIFICATION_ID = 500;
    private static final int SECOND_CHILD_NOTIFICATION_ID = 501;
    private static final String BUNDLE_GROUP_KEY = "group_key ";
    private NotificationManager mNotificationManager;
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
        mHelper = new NotificationHelper(mDevice, getInstrumentation(), mNotificationManager);
        mDevice.pressHome();
        mNotificationManager.cancelAll();
        try {
            mDevice.setOrientationNatural();
        } catch (RemoteException e) {
            throw new RuntimeException("failed to freeze device orientaion", e);
        }
    }

    @Override
    public void tearDown() throws Exception {
        mNotificationManager.cancelAll();
        mDevice.pressHome();
        super.tearDown();
    }

    @MediumTest
    public void testBundlingNotification() throws Exception {
        List<Integer> lists = new ArrayList<Integer>(Arrays.asList(GROUP_NOTIFICATION_ID,
                CHILD_NOTIFICATION_ID, SECOND_CHILD_NOTIFICATION_ID));
        mHelper.sendBundlingNotifications(lists, BUNDLE_GROUP_KEY);
        Thread.sleep(SHORT_TIMEOUT);
        mHelper.swipeDown();
        UiObject2 obj = mDevice.wait(
                Until.findObject(By.res("com.android.systemui", "notification_title")),
                LONG_TIMEOUT);
        int currentY = obj.getVisibleCenter().y;
        mDevice.wait(Until.findObject(By.res("android:id/expand_button")), LONG_TIMEOUT).click();
        obj = mDevice.wait(Until.findObject(By.textContains(lists.get(1).toString())),
                LONG_TIMEOUT);
        assertFalse("The notifications have not been bundled",
                obj.getVisibleCenter().y == currentY);
    }

    @MediumTest
    public void testDismissBundlingNotification() throws Exception {
        List<Integer> lists = new ArrayList<Integer>(Arrays.asList(GROUP_NOTIFICATION_ID,
                CHILD_NOTIFICATION_ID, SECOND_CHILD_NOTIFICATION_ID));
        mHelper.sendBundlingNotifications(lists, BUNDLE_GROUP_KEY);
        mHelper.swipeDown();
        dismissObject(Integer.toString(CHILD_NOTIFICATION_ID));
        Thread.sleep(LONG_TIMEOUT);
        for (int n : lists) {
            if (mHelper.checkNotificationExistence(n, true)) {
                fail(String.format("Notification %s has not been dismissed", n));
            }
        }
    }

    @MediumTest
    public void testDismissIndividualNotification() throws Exception {
        List<Integer> lists = new ArrayList<Integer>(Arrays.asList(GROUP_NOTIFICATION_ID,
                CHILD_NOTIFICATION_ID, SECOND_CHILD_NOTIFICATION_ID));
        mHelper.sendBundlingNotifications(lists, BUNDLE_GROUP_KEY);
        Thread.sleep(SHORT_TIMEOUT);
        mDevice.openNotification();
        mDevice.wait(Until.findObject(By.res("android:id/expand_button")), LONG_TIMEOUT).click();
        dismissObject(Integer.toString(CHILD_NOTIFICATION_ID));
        Thread.sleep(LONG_TIMEOUT);
        if (mHelper.checkNotificationExistence(CHILD_NOTIFICATION_ID, true)) {
            fail(String.format("Notification %s has not been dismissed", CHILD_NOTIFICATION_ID));
        }
        if (mHelper.checkNotificationExistence(GROUP_NOTIFICATION_ID, false)) {
            fail(String.format("Notification %s has been dismissed ", GROUP_NOTIFICATION_ID));
        }
    }

    private void dismissObject(String text) {
        UiObject2 obj = mDevice.wait(
                Until.findObject(By.textContains(text)),
                LONG_TIMEOUT);
        int y = obj.getVisibleBounds().centerY();
        mDevice.swipe(0, y, mDevice.getDisplayWidth(),
                y, 5);
    }
}
