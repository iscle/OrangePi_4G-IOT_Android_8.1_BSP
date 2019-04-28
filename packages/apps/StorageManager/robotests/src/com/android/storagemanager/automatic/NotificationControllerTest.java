/*
 * Copyright (C) 2016 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License. You may obtain a copy of
 * the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 */

package com.android.storagemanager.automatic;

import android.app.Notification;
import android.app.NotificationManager;
import android.content.Context;
import android.content.Intent;
import android.provider.Settings;
import com.android.storagemanager.testing.TestingConstants;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import org.robolectric.shadows.ShadowApplication;

import java.util.concurrent.TimeUnit;

import static com.google.common.truth.Truth.assertThat;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.anyInt;
import static org.mockito.Mockito.*;

@RunWith(RobolectricTestRunner.class)
@Config(manifest=TestingConstants.MANIFEST, sdk=TestingConstants.SDK_VERSION)
public class NotificationControllerTest {
    @Mock
    private NotificationManager mNotificationManager;
    private Context mContext;
    private NotificationController mController;
    private FakeClock mClock;

    @Before
    public void setUp() {
        MockitoAnnotations.initMocks(this);
        ShadowApplication application = ShadowApplication.getInstance();
        application.setSystemService(Context.NOTIFICATION_SERVICE, mNotificationManager);
        mController = new NotificationController();
        mClock = new FakeClock();
        mController.setClock(mClock);
        mContext = application.getApplicationContext();
    }

    @Test
    public void testShouldShowNotificationFirstTime() {
        mController.onReceive(mContext,
                new Intent(NotificationController.INTENT_ACTION_SHOW_NOTIFICATION));

        verify(mNotificationManager).notify(anyInt(), any(Notification.class));
        mController.onReceive(mContext,
                getNotificationIntent(NotificationController.INTENT_ACTION_DISMISS, 1));
        verify(mNotificationManager).cancel(1);
    }

    @Test
    public void testNotificationNotShownIfShownTooManyTimes() {
        // Show the notification 4 times.
        for (int i = 1; i < 5; i++) {
            mController.onReceive(mContext,
                    new Intent(NotificationController.INTENT_ACTION_SHOW_NOTIFICATION));
            verify(mNotificationManager, times(i)).notify(anyInt(), any(Notification.class));
            mController.onReceive(mContext,
                    getNotificationIntent(NotificationController.INTENT_ACTION_NO_THANKS, 1));
            verify(mNotificationManager, times(i)).cancel(1);
            mClock.time += TimeUnit.DAYS.toMillis(91);
        }

        // The next time should show nothing.
        mController.onReceive(mContext,
                new Intent(NotificationController.INTENT_ACTION_SHOW_NOTIFICATION));
        verifyZeroInteractions(mNotificationManager);
    }

    @Test
    public void testNotificationNotShownIfDismissedTooManyTimes() {
        // Show the notification 9 times.
        for (int i = 0; i < 9; i++) {
            mController.onReceive(mContext,
                    new Intent(NotificationController.INTENT_ACTION_SHOW_NOTIFICATION));
            verify(mNotificationManager, times(i + 1)).notify(anyInt(), any(Notification.class));
            mController.onReceive(mContext,
                    getNotificationIntent(NotificationController.INTENT_ACTION_DISMISS, 1));
            verify(mNotificationManager, times(i + 1)).cancel(1);
            mClock.time += TimeUnit.DAYS.toMillis(14);
        }

        // The next time should show nothing.
        mController.onReceive(mContext,
                new Intent(NotificationController.INTENT_ACTION_SHOW_NOTIFICATION));
        verifyZeroInteractions(mNotificationManager);
    }

    @Test
    public void testDismissNotificationDelay() {
        mController.onReceive(mContext,
                new Intent(NotificationController.INTENT_ACTION_SHOW_NOTIFICATION));
        verify(mNotificationManager).notify(anyInt(), any(Notification.class));
        mController.onReceive(mContext,
                getNotificationIntent(NotificationController.INTENT_ACTION_DISMISS, 1));
        verify(mNotificationManager).cancel(1);

        // Another attempt should not show a notification.
        mController.onReceive(mContext,
                new Intent(NotificationController.INTENT_ACTION_SHOW_NOTIFICATION));
        verifyZeroInteractions(mNotificationManager);

        // The notification should show against after 14 days.
        mClock.time = TimeUnit.DAYS.toMillis(14);
        mController.onReceive(mContext,
                new Intent(NotificationController.INTENT_ACTION_SHOW_NOTIFICATION));
        verify(mNotificationManager, times(2)).notify(anyInt(), any(Notification.class));
    }

    @Test
    public void testNoThanksNotificationDelay() {
        mController.onReceive(mContext,
                new Intent(NotificationController.INTENT_ACTION_SHOW_NOTIFICATION));
        verify(mNotificationManager).notify(anyInt(), any(Notification.class));
        mController.onReceive(mContext,
                getNotificationIntent(NotificationController.INTENT_ACTION_NO_THANKS, 1));
        verify(mNotificationManager).cancel(1);

        // Another attempt should not show a notification.
        mController.onReceive(mContext,
                new Intent(NotificationController.INTENT_ACTION_SHOW_NOTIFICATION));
        verifyZeroInteractions(mNotificationManager);

        // The notification should show against after 90 days.
        mClock.time = TimeUnit.DAYS.toMillis(90);
        mController.onReceive(mContext,
                new Intent(NotificationController.INTENT_ACTION_SHOW_NOTIFICATION));
        verify(mNotificationManager, times(2)).notify(anyInt(), any(Notification.class));
    }

    @Test
    public void testActivateStorageManagerIntent() throws Exception {
        mController.onReceive(mContext,
                new Intent(NotificationController.INTENT_ACTION_ACTIVATE_ASM));
        assertThat(Settings.Secure.getInt(mContext.getContentResolver(),
                Settings.Secure.AUTOMATIC_STORAGE_MANAGER_ENABLED)).isEqualTo(1);
    }

    @Test
    public void testNotificationIsLocalOnly(){
        ArgumentCaptor<Notification> captor = ArgumentCaptor.forClass(Notification.class);
        mController.onReceive(mContext,
                new Intent(NotificationController.INTENT_ACTION_SHOW_NOTIFICATION));
        verify(mNotificationManager).notify(anyInt(), captor.capture());
        assertThat(captor.getValue().flags & Notification.FLAG_LOCAL_ONLY)
                .isEqualTo(Notification.FLAG_LOCAL_ONLY);
    }

    @Test
    public void testIntentFromNotificationAreExplicit() {
        Intent baseIntent =
                mController.getBaseIntent(mContext, NotificationController.INTENT_ACTION_DISMISS);

        assertThat(baseIntent.getComponent().getPackageName())
                .isEqualTo("com.android.storagemanager");
        assertThat(baseIntent.getComponent().getClassName())
                .isEqualTo("com.android.storagemanager.automatic.NotificationController");
    }

    @Test
    public void testTappingGoesToStorageSettings() {
        mController.onReceive(mContext, new Intent(NotificationController.INTENT_ACTION_TAP));

        assertThat(ShadowApplication.getInstance().getNextStartedActivity().getAction())
                .isEqualTo(Settings.ACTION_INTERNAL_STORAGE_SETTINGS);
    }

    private Intent getNotificationIntent(String action, int id) {
        Intent intent = new Intent(action);
        intent.putExtra(NotificationController.INTENT_EXTRA_ID, id);
        return intent;
    }

    private class FakeClock extends NotificationController.Clock {
        public long time = 0L;

        @Override
        public long currentTimeMillis() {
            return time;
        }
    }
}
