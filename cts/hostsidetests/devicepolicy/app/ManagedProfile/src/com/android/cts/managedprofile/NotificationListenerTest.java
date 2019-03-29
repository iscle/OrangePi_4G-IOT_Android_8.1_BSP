/*
 * Copyright (C) 2017 The Android Open Source Project
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
package com.android.cts.managedprofile;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.UiAutomation;
import android.app.admin.DevicePolicyManager;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Bundle;
import android.support.test.InstrumentationRegistry;
import android.support.test.filters.SmallTest;
import android.support.test.uiautomator.UiDevice;
import android.support.v4.content.LocalBroadcastManager;
import android.util.Log;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.io.IOException;
import java.util.Collections;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

@SmallTest
public class NotificationListenerTest {

    static final String TAG = "ListenerTest";
    static final String ACTION_NOTIFICATION_POSTED = "notification_posted";
    static final String ACTION_NOTIFICATION_REMOVED = "notification_removed";
    static final String ACTION_LISTENER_CONNECTED = "listener_connected";

    private static final String PARAM_PROFILE_ID = "profile-id";

    static final String SENDER_COMPONENT =
            "com.android.cts.managedprofiletests.notificationsender/.SendNotification";

    private final LocalBroadcastReceiver mReceiver = new LocalBroadcastReceiver();
    private Context mContext;
    private DevicePolicyManager mDpm;
    private UiDevice mDevice;
    private int mProfileUserId;

    @Before
    public void setUp() throws Exception {
        mContext = InstrumentationRegistry.getTargetContext();
        mDpm = mContext.getSystemService(DevicePolicyManager.class);
        mDevice = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation());
        mProfileUserId = getParam(InstrumentationRegistry.getArguments(), PARAM_PROFILE_ID);
        IntentFilter filter = new IntentFilter();
        filter.addAction(ACTION_NOTIFICATION_POSTED);
        filter.addAction(ACTION_NOTIFICATION_REMOVED);
        filter.addAction(ACTION_LISTENER_CONNECTED);
        LocalBroadcastManager.getInstance(mContext).registerReceiver(mReceiver, filter);
    }

    @After
    public void tearDown() throws Exception {
        LocalBroadcastManager.getInstance(mContext).unregisterReceiver(mReceiver);
        toggleNotificationListener(false);
    }

    @Test
    public void testSetEmptyWhitelist() throws Exception {
        mDpm.setPermittedCrossProfileNotificationListeners(
                BaseManagedProfileTest.ADMIN_RECEIVER_COMPONENT,
                Collections.<String>emptyList());
    }

    @Test
    public void testAddListenerToWhitelist() throws Exception {
        mDpm.setPermittedCrossProfileNotificationListeners(
                BaseManagedProfileTest.ADMIN_RECEIVER_COMPONENT,
                Collections.singletonList(mContext.getPackageName()));
    }

    @Test
    public void testSetNullWhitelist() throws Exception {
        mDpm.setPermittedCrossProfileNotificationListeners(
                BaseManagedProfileTest.ADMIN_RECEIVER_COMPONENT, null);
    }

    @Test
    public void testCanReceiveNotifications() throws Exception {
        toggleNotificationListener(true);

        sendProfileNotification();
        assertTrue(mReceiver.waitForNotificationPostedReceived());
        cancelProfileNotification();
        assertTrue(mReceiver.waitForNotificationRemovedReceived());

        mReceiver.reset();

        sendPersonalNotification();
        assertTrue(mReceiver.waitForNotificationPostedReceived());
        cancelPersonalNotification();
        assertTrue(mReceiver.waitForNotificationRemovedReceived());
    }

    @Test
    public void testCannotReceiveProfileNotifications() throws Exception {
        toggleNotificationListener(true);

        sendProfileNotification();
        // Don't see notification or cancellation from work profile.
        assertFalse(mReceiver.waitForNotificationPostedReceived());
        cancelProfileNotification();
        assertFalse(mReceiver.waitForNotificationRemovedReceived());

        mReceiver.reset();

        // Do see the one from the personal side.
        sendPersonalNotification();
        assertTrue(mReceiver.waitForNotificationPostedReceived());
        cancelPersonalNotification();
        assertTrue(mReceiver.waitForNotificationRemovedReceived());
    }

    private void cancelProfileNotification() throws IOException {
        mDevice.executeShellCommand(
                "am start --user " + mProfileUserId + " -a CANCEL_NOTIFICATION -n "
                + SENDER_COMPONENT);
    }

    private void cancelPersonalNotification() throws IOException {
        mDevice.executeShellCommand(
                "am start -a CANCEL_NOTIFICATION -n "
                + SENDER_COMPONENT);
    }

    private void sendProfileNotification() throws IOException {
        mDevice.executeShellCommand(
                "am start --user " + mProfileUserId + " -a POST_NOTIFICATION -n "
                + SENDER_COMPONENT);
    }

    private void sendPersonalNotification() throws IOException {
        mDevice.executeShellCommand(
                "am start -a POST_NOTIFICATION -n "
                + SENDER_COMPONENT);
    }

    private void toggleNotificationListener(boolean enable) throws Exception {
        String testListener = new ComponentName(
                mContext, CrossProfileNotificationListenerService.class).flattenToString();
        mDevice.executeShellCommand("cmd notification "
                + (enable ?  "allow_listener " : "disallow_listener ")
                + testListener);
        Log.i(TAG, "Toggled notification listener state" + testListener + " to state " + enable);
        if (enable) {
            assertTrue(mReceiver.waitForListenerConnected());
        }
    }

    private int getParam(Bundle arguments, String key) throws Exception {
        String serial = arguments.getString(key);
        if (serial == null) {
            throw new IllegalArgumentException("Missing argument " + key);
        }
        return Integer.parseInt(serial);
    }

    static class LocalBroadcastReceiver extends BroadcastReceiver {

        private static final int TIMEOUT_SECONDS = 10;

        private CountDownLatch mNotificationPostedLatch = new CountDownLatch(1);
        private CountDownLatch mNotificationRemovedLatch = new CountDownLatch(1);
        private CountDownLatch mListenerConnectedLatch = new CountDownLatch(1);

        public void reset() {
            mNotificationPostedLatch = new CountDownLatch(1);
            mNotificationRemovedLatch = new CountDownLatch(1);
            mListenerConnectedLatch = new CountDownLatch(1);
        }

        @Override
        public void onReceive(Context context, Intent intent) {
            Log.i(TAG, "onReceive(" + intent + ")");
            if (ACTION_NOTIFICATION_POSTED.equals(intent.getAction())) {
                mNotificationPostedLatch.countDown();
            } else if (ACTION_NOTIFICATION_REMOVED.equals(intent.getAction())) {
                mNotificationRemovedLatch.countDown();
            } else if (ACTION_LISTENER_CONNECTED.equals(intent.getAction())) {
                mListenerConnectedLatch.countDown();
            } else {
                Log.e(TAG, "Received broadcast for unknown action: " + intent.getAction());
            }
        }

        public boolean waitForNotificationPostedReceived() throws InterruptedException {
            return mNotificationPostedLatch.await(TIMEOUT_SECONDS, TimeUnit.SECONDS);
        }

        public boolean waitForNotificationRemovedReceived() throws InterruptedException {
            return mNotificationRemovedLatch.await(TIMEOUT_SECONDS, TimeUnit.SECONDS);
        }

        public boolean waitForListenerConnected() throws InterruptedException {
            return mListenerConnectedLatch.await(TIMEOUT_SECONDS, TimeUnit.SECONDS);
        }
    }

}
