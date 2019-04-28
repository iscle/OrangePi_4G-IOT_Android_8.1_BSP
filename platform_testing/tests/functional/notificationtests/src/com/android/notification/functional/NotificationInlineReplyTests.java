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
import android.view.inputmethod.InputMethodManager;

public class NotificationInlineReplyTests extends InstrumentationTestCase {
    private static final int SHORT_TIMEOUT = 200;
    private static final int LONG_TIMEOUT = 2000;
    private static final int NOTIFICATION_ID = 1;
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
        try {
            mDevice.setOrientationNatural();
        } catch (RemoteException e) {
            throw new RuntimeException("failed to freeze device orientaion", e);
        }
        mDevice.pressHome();
        mNotificationManager.cancelAll();
    }

    @Override
    public void tearDown() throws Exception {
        mNotificationManager.cancelAll();
        mDevice.pressHome();
        super.tearDown();
    }

    @MediumTest
    public void testInLineNotificationWithLockScreen() throws Exception {
        try {
            mHelper.sendNotificationsWithInLineReply(NOTIFICATION_ID,false);
            mHelper.sleepAndWakeUpDevice();
            UiObject2 obj = mDevice.wait(Until.findObject(By.text("INLINE REPLY TEST")),
                    LONG_TIMEOUT);
            mDevice.swipe(obj.getVisibleBounds().centerX(), obj.getVisibleBounds().centerY(),
                    obj.getVisibleBounds().centerX(),
                    mDevice.getDisplayHeight(), 5);
            mDevice.wait(Until.findObject(By.text("REPLY")), LONG_TIMEOUT).click();
            UiObject2 replyBox = mDevice.wait(
                    Until.findObject(By.res("com.android.systemui:id/remote_input_send")),
                    LONG_TIMEOUT);
            InputMethodManager imm = (InputMethodManager) mContext
                    .getSystemService(Context.INPUT_METHOD_SERVICE);
            if (!imm.isAcceptingText()) {
                assertNotNull("Keyboard for inline reply has not loaded correctly", replyBox);
            }
        } finally {
            mHelper.swipeUp();
        }
    }

    @MediumTest
    public void testInLineNotificationsWithQuickSetting() throws Exception {
        mHelper.sendNotificationsWithInLineReply(NOTIFICATION_ID,false);
        Thread.sleep(SHORT_TIMEOUT);
        mDevice.openQuickSettings();
        mDevice.openNotification();
        mDevice.wait(Until.findObject(By.text("REPLY")), LONG_TIMEOUT).click();
        UiObject2 replyBox = mDevice.wait(
                Until.findObject(By.res("com.android.systemui:id/remote_input_send")),
                LONG_TIMEOUT);
        InputMethodManager imm = (InputMethodManager) mContext
                .getSystemService(Context.INPUT_METHOD_SERVICE);
        if (!imm.isAcceptingText()) {
            assertNotNull("Keyboard for inline reply has not loaded correctly", replyBox);
        }
    }
}
