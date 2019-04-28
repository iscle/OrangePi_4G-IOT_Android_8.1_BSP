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
import android.content.Intent;
import android.provider.AlarmClock;
import android.support.test.uiautomator.By;
import android.support.test.uiautomator.Direction;
import android.support.test.uiautomator.UiDevice;
import android.support.test.uiautomator.UiObject2;
import android.support.test.uiautomator.Until;
import android.test.InstrumentationTestCase;
import android.test.suitebuilder.annotation.LargeTest;
import android.test.suitebuilder.annotation.MediumTest;
import android.view.inputmethod.InputMethodManager;

import java.util.Calendar;
import java.util.GregorianCalendar;

public class HeadsUpNotificationTests extends InstrumentationTestCase {
    private static final int SHORT_TIMEOUT = 1000;
    private static final int LONG_TIMEOUT = 2000;
    private static final int NOTIFICATION_ID_1 = 1;
    private static final int NOTIFICATION_ID_2 = 2;
    private static final String NOTIFICATION_CONTENT_TEXT = "INLINE REPLY TEST";
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
        mDevice.setOrientationNatural();
        mHelper.unlockScreen();
        mDevice.pressHome();
        mNotificationManager.cancelAll();
    }

    @Override
    public void tearDown() throws Exception {
        mNotificationManager.cancelAll();
        mDevice.pressHome();
        mDevice.unfreezeRotation();
        super.tearDown();
    }

    @MediumTest
    public void testHeadsUpNotificationInlineReply() throws Exception {
        mHelper.sendNotificationsWithInLineReply(NOTIFICATION_ID_1, true);
        Thread.sleep(SHORT_TIMEOUT);
        mDevice.wait(Until.findObject(By.text("REPLY")), LONG_TIMEOUT).click();
        try {
            UiObject2 replyBox = mDevice.wait(
                    Until.findObject(By.res("com.android.systemui:id/remote_input_send")),
                    LONG_TIMEOUT);
            InputMethodManager imm = (InputMethodManager) mContext
                    .getSystemService(Context.INPUT_METHOD_SERVICE);
            if (!imm.isAcceptingText()) {
                assertNotNull("Keyboard for inline reply has not loaded correctly", replyBox);
            }
        } finally {
            mDevice.pressBack();
        }
    }

    @MediumTest
    public void testHeadsUpNotificationManualDismiss() throws Exception {
        mHelper.sendNotificationsWithInLineReply(NOTIFICATION_ID_1, true);
        Thread.sleep(SHORT_TIMEOUT);
        UiObject2 obj = mDevice.wait(Until.findObject(By.text(NOTIFICATION_CONTENT_TEXT)),
                LONG_TIMEOUT);
        obj.swipe(Direction.LEFT, 1.0f);
        Thread.sleep(SHORT_TIMEOUT);
        if (mHelper.checkNotificationExistence(NOTIFICATION_ID_1, true)) {
            fail(String.format("Notification %s has not been auto dismissed", NOTIFICATION_ID_1));
        }
    }

    @LargeTest
    public void testHeadsUpNotificationAutoDismiss() throws Exception {
        mHelper.sendNotificationsWithInLineReply(NOTIFICATION_ID_1, true);
        Thread.sleep(LONG_TIMEOUT * 3);
        UiObject2 obj = mDevice.wait(Until.findObject(By.text(NOTIFICATION_CONTENT_TEXT)),
                LONG_TIMEOUT);
        assertNull(String.format("Notification %s has not been auto dismissed", NOTIFICATION_ID_1),
                obj);
    }

    @MediumTest
    public void testHeadsUpNotificationInlineReplyMulti() throws Exception {
        mHelper.sendNotificationsWithInLineReply(NOTIFICATION_ID_1, true);
        Thread.sleep(LONG_TIMEOUT);
        mDevice.wait(Until.findObject(By.text("REPLY")), LONG_TIMEOUT).click();
        UiObject2 replyBox = mDevice.wait(
                Until.findObject(By.res("com.android.systemui:id/remote_input_send")),
                LONG_TIMEOUT);
        InputMethodManager imm = (InputMethodManager) mContext
                .getSystemService(Context.INPUT_METHOD_SERVICE);
        if (!imm.isAcceptingText()) {
            assertNotNull("Keyboard for inline reply has not loaded correctly", replyBox);
        }
        mHelper.sendNotificationsWithInLineReply(NOTIFICATION_ID_2, true);
        Thread.sleep(LONG_TIMEOUT);
        UiObject2 obj = mDevice.wait(Until.findObject(By.text(NOTIFICATION_CONTENT_TEXT)),
                LONG_TIMEOUT);
        if (obj == null) {
            assertNull(String.format("Notification %s can not be found", NOTIFICATION_ID_1),
                    obj);
        }
    }

    @LargeTest
    public void testAlarm() throws Exception {
        try {
            setAlarmNow();
            UiObject2 obj = mDevice.wait(Until.findObject(By.text("test")), 60000);
            if (obj == null) {
                fail("Alarm heads up notifcation is not working");
            }
        } finally {
            mDevice.wait(Until.findObject(By.text("DISMISS")), LONG_TIMEOUT).click();
        }
    }

    private void setAlarmNow() throws InterruptedException {
        GregorianCalendar cal = new GregorianCalendar();
        cal.setTimeInMillis(System.currentTimeMillis());
        int hour = cal.get(Calendar.HOUR_OF_DAY);
        int minute = cal.get(Calendar.MINUTE) + 1;// to make sure it won't be set at the next day
        Intent intent = new Intent(AlarmClock.ACTION_SET_ALARM);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        intent.putExtra(AlarmClock.EXTRA_HOUR, hour);
        intent.putExtra(AlarmClock.EXTRA_MINUTES, minute);
        intent.putExtra(AlarmClock.EXTRA_SKIP_UI, true);
        intent.putExtra(AlarmClock.EXTRA_MESSAGE, "test");
        mContext.startActivity(intent);
        Thread.sleep(LONG_TIMEOUT * 2);
    }
}
