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
import android.metrics.LogMaker;
import android.service.notification.StatusBarNotification;
import android.support.test.metricshelper.MetricsAsserts;
import android.support.test.uiautomator.By;
import android.support.test.uiautomator.Direction;
import android.support.test.uiautomator.UiDevice;
import android.support.test.uiautomator.UiObject2;
import android.support.test.uiautomator.Until;
import android.test.InstrumentationTestCase;
import android.test.suitebuilder.annotation.MediumTest;
import android.util.Log;

import android.metrics.MetricsReader;
import com.android.internal.logging.nano.MetricsProto.MetricsEvent;

import java.util.HashMap;
import java.util.Map;
import java.util.Queue;

public class NotificationInteractionTests extends InstrumentationTestCase {
    private static final String LOG_TAG = NotificationInteractionTests.class.getSimpleName();
    private static final int LONG_TIMEOUT = 3000;
    private static final int SHORT_TIMEOUT = 200;
    private final boolean DEBUG = false;
    private NotificationManager mNotificationManager;
    private UiDevice mDevice = null;
    private Context mContext;
    private NotificationHelper mHelper;
    private static final int CUSTOM_NOTIFICATION_ID = 1;
    private static final int NOTIFICATIONS_COUNT = 3;
    private MetricsReader mMetricsReader;

    @Override
    public void setUp() throws Exception {
        super.setUp();
        mDevice = UiDevice.getInstance(getInstrumentation());
        mContext = getInstrumentation().getContext();
        mNotificationManager = (NotificationManager) mContext
                .getSystemService(Context.NOTIFICATION_SERVICE);
        mHelper = new NotificationHelper(mDevice, getInstrumentation(), mNotificationManager);
        mDevice.setOrientationNatural();
        mNotificationManager.cancelAll();
        mMetricsReader = new MetricsReader();
        mMetricsReader.checkpoint(); // clear out old logs
    }

    @Override
    public void tearDown() throws Exception {
        super.tearDown();
        mDevice.unfreezeRotation();
        mDevice.pressHome();
        mNotificationManager.cancelAll();
    }

    @MediumTest
    public void testNonDismissNotification() throws Exception {
        String text = "USB debugging connected";
        mDevice.openNotification();
        Thread.sleep(LONG_TIMEOUT);
        UiObject2 obj = findByText(text);
        assertNotNull(String.format("Couldn't find %s notification", text), obj);
        obj.swipe(Direction.LEFT, 1.0f);
        Thread.sleep(LONG_TIMEOUT);
        obj = mDevice.wait(Until.findObject(By.text(text)),
                LONG_TIMEOUT);
        assertNotNull("USB debugging notification has been dismissed", obj);
    }

    /** send out multiple notifications in order to test CLEAR ALL function */
    @MediumTest
    public void testDismissAll() throws Exception {
        String text = "CLEAR ALL";
        Map<Integer, String> lists = new HashMap<Integer, String>();
        StatusBarNotification[] sbns = mNotificationManager.getActiveNotifications();
        int currentSbns = sbns.length;
        for (int i = 0; i < NOTIFICATIONS_COUNT; i++) {
            lists.put(CUSTOM_NOTIFICATION_ID + i, Integer.toString(CUSTOM_NOTIFICATION_ID + i));
        }
        mHelper.sendNotifications(lists, false);

        if (DEBUG) {
            Log.d(LOG_TAG,
                    String.format("posted %s notifications, here they are: ", NOTIFICATIONS_COUNT));
            sbns = mNotificationManager.getActiveNotifications();
            for (StatusBarNotification sbn : sbns) {
                Log.d(LOG_TAG, "  " + sbn);
            }
        }
        if (mDevice.openNotification()) {
            Thread.sleep(LONG_TIMEOUT);
            UiObject2 clearAll = findByText(text);
            assertNotNull("could not find clear all target", clearAll);
            clearAll.click();
        }
        Thread.sleep(LONG_TIMEOUT);
        sbns = mNotificationManager.getActiveNotifications();
        assertTrue(String.format("%s notifications have not been cleared", sbns.length),
                sbns.length == currentSbns);

        MetricsAsserts.assertHasVisibilityLog("missing panel revealed log", mMetricsReader,
                MetricsEvent.NOTIFICATION_PANEL, true);
        MetricsAsserts.assertHasLog("missing notification visibility log", mMetricsReader,
                new LogMaker(MetricsEvent.NOTIFICATION_ITEM)
                        .setType(MetricsEvent.TYPE_OPEN)
                        .addTaggedData(MetricsEvent.NOTIFICATION_ID, CUSTOM_NOTIFICATION_ID)
                        .setPackageName(mContext.getPackageName()));
        MetricsAsserts.assertHasLog("missing notification cancel log", mMetricsReader,
                new LogMaker(MetricsEvent.NOTIFICATION_ITEM)
                        .setType(MetricsEvent.TYPE_DISMISS)
                        .addTaggedData(MetricsEvent.NOTIFICATION_ID, CUSTOM_NOTIFICATION_ID)
                        .setPackageName(mContext.getPackageName()));
        MetricsAsserts.assertHasActionLog("missing dismiss-all log", mMetricsReader,
                MetricsEvent.ACTION_DISMISS_ALL_NOTES);
        MetricsAsserts.assertHasVisibilityLog("missing panel hidden log", mMetricsReader,
                MetricsEvent.NOTIFICATION_PANEL, false);
    }

    /** send notifications, then open and close the shade to test visibility metrics. */
    @MediumTest
    public void testNotificationShadeMetricsl() throws Exception {
        Map<Integer, String> lists = new HashMap<Integer, String>();
        int firstId = CUSTOM_NOTIFICATION_ID;
        int secondId = CUSTOM_NOTIFICATION_ID + 1;
        lists.put(firstId, Integer.toString(firstId));
        lists.put(secondId, Integer.toString(secondId));
        // post
        mHelper.sendNotifications(lists, true);
        Thread.sleep(LONG_TIMEOUT);
        // update
        mHelper.sendNotifications(lists, true);

        if (mDevice.openNotification()) {
            Thread.sleep(LONG_TIMEOUT);
        }
        MetricsAsserts.assertHasVisibilityLog("missing panel revealed log", mMetricsReader,
                MetricsEvent.NOTIFICATION_PANEL, true);
        Queue<LogMaker> firstLog = MetricsAsserts.findMatchingLogs(mMetricsReader,
                new LogMaker(MetricsEvent.NOTIFICATION_ITEM)
                        .setType(MetricsEvent.TYPE_OPEN)
                        .addTaggedData(MetricsEvent.NOTIFICATION_ID, firstId)
                        .setPackageName(mContext.getPackageName()));
        assertTrue("missing first note visibility log", !firstLog.isEmpty());
        Queue<LogMaker> secondLog = MetricsAsserts.findMatchingLogs(mMetricsReader,
                new LogMaker(MetricsEvent.NOTIFICATION_ITEM)
                        .setType(MetricsEvent.TYPE_OPEN)
                        .addTaggedData(MetricsEvent.NOTIFICATION_ID, secondId));
        assertTrue("missing second note visibility log", !secondLog.isEmpty());
        int firstRank = (Integer) firstLog.peek()
                .getTaggedData(MetricsEvent.NOTIFICATION_SHADE_INDEX);
        int secondRank = (Integer) secondLog.peek()
                .getTaggedData(MetricsEvent.NOTIFICATION_SHADE_INDEX);
        assertTrue("note must have distinct ranks", firstRank != secondRank);
        int lifespan = (Integer) firstLog.peek()
                .getTaggedData(MetricsEvent.NOTIFICATION_SINCE_CREATE_MILLIS);
        int freshness = (Integer) firstLog.peek()
                .getTaggedData(MetricsEvent.NOTIFICATION_SINCE_UPDATE_MILLIS);
        int exposure = (Integer) firstLog.peek()
                .getTaggedData(MetricsEvent.NOTIFICATION_SINCE_VISIBLE_MILLIS);
        assertTrue("first note updated before it was created", lifespan >  freshness);
        assertTrue("first note visible before it was updated", freshness >  exposure);
        assertTrue("first note visibility log should have zero exposure time", exposure == 0);
        int secondLifespan = (Integer) secondLog.peek()
                .getTaggedData(MetricsEvent.NOTIFICATION_SINCE_CREATE_MILLIS);
        assertTrue("first note created after second note", lifespan >  secondLifespan);

        mMetricsReader.checkpoint(); // clear out old logs again
        firstLog.clear();
        secondLog.clear();
        // close the shade
        if (mDevice.pressHome()) {
            Thread.sleep(LONG_TIMEOUT);
        }

        MetricsAsserts.assertHasVisibilityLog("missing panel hidden log", mMetricsReader,
                MetricsEvent.NOTIFICATION_PANEL, false);
        firstLog = MetricsAsserts.findMatchingLogs(mMetricsReader,
                new LogMaker(MetricsEvent.NOTIFICATION_ITEM)
                        .setType(MetricsEvent.TYPE_CLOSE)
                        .addTaggedData(MetricsEvent.NOTIFICATION_ID, firstId)
                        .setPackageName(mContext.getPackageName()));
        assertTrue("missing first note hidden log", !firstLog.isEmpty());
        exposure = (Integer) firstLog.peek()
                .getTaggedData(MetricsEvent.NOTIFICATION_SINCE_VISIBLE_MILLIS);
        assertTrue("first note visibility log should have nonzero exposure time", exposure > 0);
        secondLog = MetricsAsserts.findMatchingLogs(mMetricsReader,
                new LogMaker(MetricsEvent.NOTIFICATION_ITEM)
                        .setType(MetricsEvent.TYPE_CLOSE)
                        .addTaggedData(MetricsEvent.NOTIFICATION_ID, secondId)
                        .setPackageName(mContext.getPackageName()));
        assertTrue("missing second note hidden log", !secondLog.isEmpty());
    }

    /** send a notification, click on first it. */
    @MediumTest
    public void testNotificationClicks() throws Exception {
        int id = CUSTOM_NOTIFICATION_ID;
        mHelper.sendNotification(id, Notification.VISIBILITY_PUBLIC,
                NotificationHelper.CONTENT_TITLE, true);

        UiObject2 target = null;
        if (mDevice.openNotification()) {
            target = mDevice.wait(
                    Until.findObject(By.text(NotificationHelper.FIRST_ACTION)),
                    LONG_TIMEOUT);
            assertNotNull("could not find first action button", target);
            target.click();
        }
        Thread.sleep(SHORT_TIMEOUT);
        // top item is always expanded
        MetricsAsserts.assertHasLog("missing notification expansion log", mMetricsReader,
                new LogMaker(MetricsEvent.NOTIFICATION_ITEM)
                        .setType(MetricsEvent.TYPE_DETAIL)
                        .addTaggedData(MetricsEvent.NOTIFICATION_ID, id)
                        .setPackageName(mContext.getPackageName()));
        MetricsAsserts.assertHasLog("missing notification alert log", mMetricsReader,
                new LogMaker(MetricsEvent.NOTIFICATION_ALERT)
                        .setType(MetricsEvent.TYPE_OPEN)
                        .addTaggedData(MetricsEvent.NOTIFICATION_ID, id)
                        .setSubtype(MetricsEvent.ALERT_BUZZ) // no BEEP or BLINK
                        .setPackageName(mContext.getPackageName()));
        MetricsAsserts.assertHasLog("missing notification action 0 click log", mMetricsReader,
                new LogMaker(MetricsEvent.NOTIFICATION_ITEM_ACTION)
                        .setType(MetricsEvent.TYPE_ACTION)
                        .addTaggedData(MetricsEvent.NOTIFICATION_ID, id)
                        .setSubtype(0)  // first action button, zero indexed
                        .setPackageName(mContext.getPackageName()));

        mMetricsReader.checkpoint(); // clear out old logs again
        target = mDevice.wait(Until.findObject(By.text(NotificationHelper.SECOND_ACTION)),
                LONG_TIMEOUT);
        assertNotNull("could not find second action button", target);
        target.click();
        Thread.sleep(SHORT_TIMEOUT);
        MetricsAsserts.assertHasLog("missing notification action 1 click log", mMetricsReader,
                new LogMaker(MetricsEvent.NOTIFICATION_ITEM_ACTION)
                        .setType(MetricsEvent.TYPE_ACTION)
                        .addTaggedData(MetricsEvent.NOTIFICATION_ID, id)
                        .setSubtype(1)  // second action button, zero indexed
                        .setPackageName(mContext.getPackageName()));

        mMetricsReader.checkpoint(); // clear out old logs again\
        target = mDevice.wait(Until.findObject(By.text(NotificationHelper.CONTENT_TITLE)),
                LONG_TIMEOUT);
        assertNotNull("could not find content click target", target);
        target.click();
        Thread.sleep(SHORT_TIMEOUT);
        MetricsAsserts.assertHasLog("missing notification content click log", mMetricsReader,
                new LogMaker(MetricsEvent.NOTIFICATION_ITEM)
                        .setType(MetricsEvent.TYPE_ACTION)
                        .addTaggedData(MetricsEvent.NOTIFICATION_ID, id)
                        .setPackageName(mContext.getPackageName()));
    }

    private UiObject2 findByText(String text) throws Exception {
        int maxAttempt = 5;
        UiObject2 item = null;
        while (maxAttempt-- > 0) {
            item = mDevice.wait(Until.findObject(By.text(text)), LONG_TIMEOUT);
            if (item == null) {
                mDevice.swipe(mDevice.getDisplayWidth() / 2, mDevice.getDisplayHeight() / 2,
                        mDevice.getDisplayWidth() / 2, 0, 30);
            } else {
                return item;
            }
        }
        return null;
    }
}
