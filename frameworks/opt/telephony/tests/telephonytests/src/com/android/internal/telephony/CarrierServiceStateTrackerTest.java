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

package com.android.internal.telephony;

import android.app.Notification;
import android.app.NotificationManager;
import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.content.res.Resources;
import android.os.HandlerThread;
import android.os.Message;
import android.test.suitebuilder.annotation.SmallTest;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import static org.mockito.Mockito.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.isA;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link com.android.internal.telephony.CarrierServiceStateTracker}.
 */
@SmallTest
public class CarrierServiceStateTrackerTest extends TelephonyTest {
    public static final String LOG_TAG = "CSST";
    public static final int TEST_TIMEOUT = 5000;

    private CarrierServiceStateTracker mCarrierSST;
    private CarrierServiceStateTrackerTestHandler mCarrierServiceStateTrackerTestHandler;
    private  CarrierServiceStateTracker.PrefNetworkNotification mPrefNetworkNotification;
    private  CarrierServiceStateTracker.EmergencyNetworkNotification mEmergencyNetworkNotification;

    @Mock Context mContext;
    @Mock ServiceStateTracker mServiceStateTracker;
    @Mock NotificationManager mNotificationManager;
    @Mock Resources mResources;

    private class CarrierServiceStateTrackerTestHandler extends HandlerThread {

        private CarrierServiceStateTrackerTestHandler(String name) {
            super(name);
        }

        @Override
        public void onLooperPrepared() {
            mCarrierSST = spy(new CarrierServiceStateTracker(mPhone, mServiceStateTracker));
            setReady(true);
        }
    }

    @Before
    public void setUp() throws Exception {
        MockitoAnnotations.initMocks(this);
        logd(LOG_TAG + "Setup!");
        super.setUp(getClass().getSimpleName());
        mCarrierServiceStateTrackerTestHandler =
                new CarrierServiceStateTrackerTestHandler(getClass().getSimpleName());
        mCarrierServiceStateTrackerTestHandler.start();
        when(mContext.getResources()).thenReturn(mResources);
        when(mContext.getPackageManager()).thenReturn(mPackageManager);
        when(mContext.getApplicationInfo()).thenReturn(new ApplicationInfo());
        waitUntilReady();
    }

    @After
    public void tearDown() throws Exception {
        mCarrierServiceStateTrackerTestHandler.quit();
        super.tearDown();
    }

    @Test
    @SmallTest
    public void testCancelBothNotifications() {
        logd(LOG_TAG + ":testCancelBothNotifications()");
        Message notificationMsg = mCarrierSST.obtainMessage(
                CarrierServiceStateTracker.CARRIER_EVENT_DATA_REGISTRATION, null);
        doReturn(false).when(mCarrierSST).evaluateSendingMessage(any());
        doReturn(mNotificationManager).when(mCarrierSST).getNotificationManager(any());
        mCarrierSST.handleMessage(notificationMsg);
        waitForHandlerAction(mCarrierSST, TEST_TIMEOUT);
        verify(mNotificationManager).cancel(
                CarrierServiceStateTracker.NOTIFICATION_EMERGENCY_NETWORK);
        verify(mNotificationManager).cancel(
                CarrierServiceStateTracker.NOTIFICATION_PREF_NETWORK);
    }

    @Test
    @SmallTest
    public void testSendBothNotifications() {
        logd(LOG_TAG + ":testSendBothNotifications()");
        Notification.Builder mNotificationBuilder = new Notification.Builder(mContext);
        Message notificationMsg = mCarrierSST.obtainMessage(
                CarrierServiceStateTracker.CARRIER_EVENT_DATA_DEREGISTRATION, null);
        doReturn(true).when(mCarrierSST).evaluateSendingMessage(any());
        doReturn(false).when(mCarrierSST).isRadioOffOrAirplaneMode();
        doReturn(0).when(mCarrierSST).getDelay(any());
        doReturn(mNotificationBuilder).when(mCarrierSST).getNotificationBuilder(any());
        doReturn(mNotificationManager).when(mCarrierSST).getNotificationManager(any());
        mCarrierSST.handleMessage(notificationMsg);
        waitForHandlerAction(mCarrierSST, TEST_TIMEOUT);
        verify(mNotificationManager).notify(
                eq(CarrierServiceStateTracker.NOTIFICATION_PREF_NETWORK), isA(Notification.class));
        verify(mNotificationManager).notify(
                eq(CarrierServiceStateTracker.NOTIFICATION_EMERGENCY_NETWORK), any());
    }
}
