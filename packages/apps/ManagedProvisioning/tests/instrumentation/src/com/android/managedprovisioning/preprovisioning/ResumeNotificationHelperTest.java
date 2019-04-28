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
package com.android.managedprovisioning.preprovisioning;

import static com.android.managedprovisioning.preprovisioning.EncryptionController.CHANNEL_ID;
import static com.android.managedprovisioning.preprovisioning.EncryptionController.NOTIFICATION_ID;
import static org.hamcrest.Matchers.equalTo;
import static org.junit.Assert.assertEquals;

import android.app.Notification;
import android.app.NotificationManager;
import android.content.Context;
import android.content.Intent;
import android.os.SystemClock;
import android.service.notification.StatusBarNotification;
import android.support.test.InstrumentationRegistry;
import android.support.test.filters.SmallTest;
import com.android.managedprovisioning.R;
import com.android.managedprovisioning.common.Globals;
import com.android.managedprovisioning.preprovisioning.EncryptionController.ResumeNotificationHelper;
import org.hamcrest.MatcherAssert;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

@SmallTest
public class ResumeNotificationHelperTest {

    private static final int NOTIFICATION_TIMEOUT_MS = 5000;

    private ResumeNotificationHelper mResumeNotificationHelper;
    private NotificationManager mNotificationManager;
    private Context mContext;

    @Before
    public void setUp() {
        mContext = InstrumentationRegistry.getTargetContext();
        mResumeNotificationHelper = new ResumeNotificationHelper(mContext);
        mNotificationManager = mContext.getSystemService(NotificationManager.class);
        removeAllNotifications();
    }

    @After
    public void tearDown() {
        removeAllNotifications();
    }

    @Test
    public void testShowResumeNotification() throws Exception {
        MatcherAssert.assertThat(mNotificationManager.getActiveNotifications().length,
                equalTo(0));

        Intent intent = new Intent(Globals.ACTION_RESUME_PROVISIONING);
        mResumeNotificationHelper.showResumeNotification(intent);

        waitForNotification();
        StatusBarNotification[] notifications = mNotificationManager.getActiveNotifications();
        MatcherAssert.assertThat(notifications.length, equalTo(1));
        StatusBarNotification notification = notifications[0];
        assertEquals(notification.getId(), NOTIFICATION_ID);
        assertEquals(notification.getNotification().getChannel(), CHANNEL_ID);
        assertEquals(notification.getNotification().extras.getString(Notification.EXTRA_TITLE),
                mContext.getString(R.string.continue_provisioning_notify_title));
    }

    private void waitForNotification() throws InterruptedException {
        long elapsed = SystemClock.elapsedRealtime();
        while(SystemClock.elapsedRealtime() - elapsed < NOTIFICATION_TIMEOUT_MS
                && mNotificationManager.getActiveNotifications().length == 0) {
            Thread.sleep(10);
        }
    }

    private void removeAllNotifications() {
        mNotificationManager.cancelAll();
    }
}
