/*
 * Copyright (C) 2015 The Android Open Source Project
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
 * limitations under the License
 */

package com.android.server.telecom.tests;

import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.ComponentName;
import android.content.ContentProvider;
import android.content.Context;
import android.content.IContentProvider;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.ICancellationSignal;
import android.os.Looper;
import android.os.UserHandle;
import android.provider.CallLog;
import android.telecom.PhoneAccount;
import android.telecom.PhoneAccountHandle;
import android.telecom.TelecomManager;
import android.telephony.TelephonyManager;
import android.test.suitebuilder.annotation.SmallTest;

import com.android.internal.telephony.CallerInfo;
import com.android.server.telecom.CallerInfoLookupHelper;
import com.android.server.telecom.Constants;
import com.android.server.telecom.DefaultDialerCache;
import com.android.server.telecom.MissedCallNotifier;
import com.android.server.telecom.PhoneAccountRegistrar;
import com.android.server.telecom.TelecomBroadcastIntentProcessor;
import com.android.server.telecom.TelecomSystem;
import com.android.server.telecom.components.TelecomBroadcastReceiver;
import com.android.server.telecom.ui.MissedCallNotifierImpl;
import com.android.server.telecom.ui.MissedCallNotifierImpl.NotificationBuilderFactory;

import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.Arrays;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;

import static org.mockito.ArgumentMatchers.nullable;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.anyString;
import static org.mockito.Matchers.eq;
import static org.mockito.Matchers.isNull;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class MissedCallNotifierImplTest extends TelecomTestCase {

    private static class MockMissedCallCursorBuilder {
        private class CallLogRow {
            String number;
            int presentation;
            long date;

            public CallLogRow(String number, int presentation, long date) {
                this.number = number;
                this.presentation = presentation;
                this.date = date;
            }
        }

        private List<CallLogRow> mRows;

        MockMissedCallCursorBuilder() {
            mRows = new LinkedList<>();
            mRows.add(null);
        }

        public MockMissedCallCursorBuilder addEntry(String number, int presentation, long date) {
            mRows.add(new CallLogRow(number, presentation, date));
            return this;
        }

        public Cursor build() {
            Cursor c = mock(Cursor.class);
            when(c.moveToNext()).thenAnswer(unused -> {
                mRows.remove(0);
                return mRows.size() > 0;
            });
            when(c.getString(MissedCallNotifierImpl.CALL_LOG_COLUMN_NUMBER))
                    .thenAnswer(unused -> mRows.get(0).number);
            when(c.getInt(MissedCallNotifierImpl.CALL_LOG_COLUMN_NUMBER_PRESENTATION))
                    .thenAnswer(unused -> mRows.get(0).presentation);
            when(c.getLong(MissedCallNotifierImpl.CALL_LOG_COLUMN_DATE))
                    .thenAnswer(unused -> mRows.get(0).date);
            return c;
        }
    }

    private static final Uri TEL_CALL_HANDLE = Uri.parse("tel:+11915552620");
    private static final Uri SIP_CALL_HANDLE = Uri.parse("sip:testaddress@testdomain.com");
    private static final String CALLER_NAME = "Fake Name";
    private static final String MISSED_CALL_TITLE = "Missed Call";
    private static final String MISSED_CALLS_TITLE = "Missed Calls";
    private static final String MISSED_CALLS_MSG = "%s missed calls";
    private static final String USER_CALL_ACTIVITY_LABEL = "Phone";

    private static final int REQUEST_ID = 0;
    private static final long CALL_TIMESTAMP;
    static {
         CALL_TIMESTAMP = System.currentTimeMillis() - 60 * 1000 * 5;
    }

    private static final UserHandle PRIMARY_USER = UserHandle.of(0);
    private static final UserHandle SECONARY_USER = UserHandle.of(12);
    private static final int NO_CAPABILITY = 0;
    private static final int TEST_TIMEOUT = 1000;

    @Mock
    private NotificationManager mNotificationManager;

    @Mock
    private PhoneAccountRegistrar mPhoneAccountRegistrar;

    @Mock
    private TelecomManager mTelecomManager;

    @Mock TelecomSystem mTelecomSystem;
    @Mock private DefaultDialerCache mDefaultDialerCache;

    @Override
    public void setUp() throws Exception {
        super.setUp();
        MockitoAnnotations.initMocks(this);

        mContext = mComponentContextFixture.getTestDouble().getApplicationContext();
        mNotificationManager = (NotificationManager) mContext.getSystemService(
                Context.NOTIFICATION_SERVICE);
        TelephonyManager fakeTelephonyManager = (TelephonyManager) mContext.getSystemService(
                Context.TELEPHONY_SERVICE);
        when(fakeTelephonyManager.getNetworkCountryIso()).thenReturn("US");
        doReturn(new ApplicationInfo()).when(mContext).getApplicationInfo();
        doReturn("com.android.server.telecom.tests").when(mContext).getPackageName();

        mComponentContextFixture.putResource(R.string.notification_missedCallTitle,
                MISSED_CALL_TITLE);
        mComponentContextFixture.putResource(R.string.notification_missedCallsTitle,
                MISSED_CALLS_TITLE);
        mComponentContextFixture.putResource(R.string.notification_missedCallsMsg,
                MISSED_CALLS_MSG);
        mComponentContextFixture.putResource(R.string.userCallActivityLabel,
                USER_CALL_ACTIVITY_LABEL);
        mComponentContextFixture.setTelecomManager(mTelecomManager);
    }

    @Override
    public void tearDown() throws Exception {
        TelecomSystem.setInstance(null);
        when(mTelecomSystem.isBootComplete()).thenReturn(false);
    }

    @SmallTest
    public void testCancelNotificationInPrimaryUser() {
        cancelNotificationTestInternal(PRIMARY_USER);
    }

    @SmallTest
    public void testCancelNotificationInSecondaryUser() {
        cancelNotificationTestInternal(SECONARY_USER);
    }

    private void cancelNotificationTestInternal(UserHandle userHandle) {
        Notification.Builder builder1 = makeNotificationBuilder("builder1");
        Notification.Builder builder2 = makeNotificationBuilder("builder2");
        MissedCallNotifierImpl.NotificationBuilderFactory fakeBuilderFactory =
                makeNotificationBuilderFactory(builder1, builder1, builder2, builder2);

        MissedCallNotifier missedCallNotifier = makeMissedCallNotifier(fakeBuilderFactory,
                PRIMARY_USER);
        PhoneAccount phoneAccount = makePhoneAccount(userHandle, NO_CAPABILITY);
        MissedCallNotifier.CallInfo fakeCall = makeFakeCallInfo(TEL_CALL_HANDLE, CALLER_NAME,
                CALL_TIMESTAMP, phoneAccount.getAccountHandle());

        missedCallNotifier.showMissedCallNotification(fakeCall);
        missedCallNotifier.clearMissedCalls(userHandle);
        missedCallNotifier.showMissedCallNotification(fakeCall);

        ArgumentCaptor<Integer> requestIdCaptor = ArgumentCaptor.forClass(
                Integer.class);
        verify(mNotificationManager, times(2)).notifyAsUser(nullable(String.class),
                requestIdCaptor.capture(), nullable(Notification.class), eq(userHandle));
        verify(mNotificationManager).cancelAsUser(nullable(String.class),
                eq(requestIdCaptor.getValue()), eq(userHandle));

        // Verify that the second call to showMissedCallNotification behaves like it were the first.
        verify(builder2).setContentText(CALLER_NAME);
    }

    @SmallTest
    public void testNotifyMultipleMissedCalls() {
        Notification.Builder[] builders = new Notification.Builder[4];

        for (int i = 0; i < 4; i++) {
            builders[i] = makeNotificationBuilder("builder" + Integer.toString(i));
        }

        PhoneAccount phoneAccount = makePhoneAccount(PRIMARY_USER, NO_CAPABILITY);
        MissedCallNotifier.CallInfo fakeCall = makeFakeCallInfo(TEL_CALL_HANDLE, CALLER_NAME,
                CALL_TIMESTAMP, phoneAccount.getAccountHandle());

        MissedCallNotifierImpl.NotificationBuilderFactory fakeBuilderFactory =
                makeNotificationBuilderFactory(builders);

        MissedCallNotifier missedCallNotifier = new MissedCallNotifierImpl(mContext,
                mPhoneAccountRegistrar, mDefaultDialerCache, fakeBuilderFactory);

        missedCallNotifier.showMissedCallNotification(fakeCall);
        missedCallNotifier.showMissedCallNotification(fakeCall);

        // The following captor is to capture the two notifications that got passed into
        // notifyAsUser. This distinguishes between the builders used for the full notification
        // (i.e. the one potentially containing sensitive information, such as phone numbers),
        // and the builders used for the notifications shown on the lockscreen, which have been
        // scrubbed of such sensitive info. The notifications which are used as arguments
        // to notifyAsUser are the versions which contain sensitive information.
        ArgumentCaptor<Notification> notificationArgumentCaptor = ArgumentCaptor.forClass(
                Notification.class);
        verify(mNotificationManager, times(2)).notifyAsUser(nullable(String.class), eq(1),
                notificationArgumentCaptor.capture(), eq(PRIMARY_USER));
        HashSet<String> privateNotifications = new HashSet<>();
        for (Notification n : notificationArgumentCaptor.getAllValues()) {
            privateNotifications.add(n.toString());
        }

        for (int i = 0; i < 4; i++) {
            Notification.Builder builder = builders[i];
            verify(builder).setWhen(CALL_TIMESTAMP);
            if (i >= 2) {
                // The builders after the first two are for multiple missed calls. The notification
                // for subsequent missed calls is expected to be different in terms of the text
                // contents of the notification, and that is verified here.
                if (privateNotifications.contains(builder.toString())) {
                    verify(builder).setContentText(String.format(MISSED_CALLS_MSG, 2));
                    verify(builder).setContentTitle(MISSED_CALLS_TITLE);
                } else {
                    verify(builder).setContentText(MISSED_CALLS_TITLE);
                    verify(builder).setContentTitle(USER_CALL_ACTIVITY_LABEL);
                }
                verify(builder, never()).addAction(any(Notification.Action.class));
            } else {
                if (privateNotifications.contains(builder.toString())) {
                    verify(builder).setContentText(CALLER_NAME);
                    verify(builder).setContentTitle(MISSED_CALL_TITLE);
                } else {
                    verify(builder).setContentText(MISSED_CALL_TITLE);
                    verify(builder).setContentTitle(USER_CALL_ACTIVITY_LABEL);
                }
            }
        }
    }

    @SmallTest
    public void testNotifySingleCallInPrimaryUser() {
        PhoneAccount phoneAccount = makePhoneAccount(PRIMARY_USER, NO_CAPABILITY);
        notifySingleCallTestInternal(phoneAccount, PRIMARY_USER);
    }

    @SmallTest
    public void testNotifySingleCallInSecondaryUser() {
        PhoneAccount phoneAccount = makePhoneAccount(SECONARY_USER, NO_CAPABILITY);
        notifySingleCallTestInternal(phoneAccount, PRIMARY_USER);
    }

    @SmallTest
    public void testNotifySingleCallInSecondaryUserWithMultiUserCapability() {
        PhoneAccount phoneAccount = makePhoneAccount(PRIMARY_USER,
                PhoneAccount.CAPABILITY_MULTI_USER);
        notifySingleCallTestInternal(phoneAccount, PRIMARY_USER);
    }

    @SmallTest
    public void testNotifySingleCallWhenCurrentUserIsSecondaryUser() {
        PhoneAccount phoneAccount = makePhoneAccount(PRIMARY_USER, NO_CAPABILITY);
        notifySingleCallTestInternal(phoneAccount, SECONARY_USER);
    }

    @SmallTest
    public void testNotifySingleCall() {
        PhoneAccount phoneAccount = makePhoneAccount(PRIMARY_USER, NO_CAPABILITY);
        notifySingleCallTestInternal(phoneAccount, PRIMARY_USER);
    }

    private void notifySingleCallTestInternal(PhoneAccount phoneAccount, UserHandle currentUser) {
        Notification.Builder builder1 = makeNotificationBuilder("builder1");
        Notification.Builder builder2 = makeNotificationBuilder("builder2");
        MissedCallNotifierImpl.NotificationBuilderFactory fakeBuilderFactory =
                makeNotificationBuilderFactory(builder1, builder2);

        MissedCallNotifier missedCallNotifier = makeMissedCallNotifier(fakeBuilderFactory,
                currentUser);

        MissedCallNotifier.CallInfo fakeCall = makeFakeCallInfo(TEL_CALL_HANDLE, CALLER_NAME,
                CALL_TIMESTAMP, phoneAccount.getAccountHandle());
        missedCallNotifier.showMissedCallNotification(fakeCall);

        ArgumentCaptor<Notification> notificationArgumentCaptor = ArgumentCaptor.forClass(
                Notification.class);

        UserHandle expectedUserHandle;
        if (phoneAccount.hasCapabilities(PhoneAccount.CAPABILITY_MULTI_USER)) {
            expectedUserHandle = currentUser;
        } else {
            expectedUserHandle = phoneAccount.getAccountHandle().getUserHandle();
        }
        verify(mNotificationManager).notifyAsUser(nullable(String.class), eq(1),
                notificationArgumentCaptor.capture(), eq((expectedUserHandle)));

        Notification.Builder builder;
        Notification.Builder publicBuilder;

        if (notificationArgumentCaptor.getValue().toString().equals("builder1")) {
            builder = builder1;
            publicBuilder = builder2;
        } else {
            builder = builder2;
            publicBuilder = builder1;
        }

        verify(builder).setWhen(CALL_TIMESTAMP);
        verify(publicBuilder).setWhen(CALL_TIMESTAMP);

        verify(builder).setContentText(CALLER_NAME);
        verify(publicBuilder).setContentText(MISSED_CALL_TITLE);

        verify(builder).setContentTitle(MISSED_CALL_TITLE);
        verify(publicBuilder).setContentTitle(USER_CALL_ACTIVITY_LABEL);

        // Create two intents that correspond to call-back and respond back with SMS, and assert
        // that these pending intents have in fact been registered.
        Intent callBackIntent = new Intent(
                TelecomBroadcastIntentProcessor.ACTION_CALL_BACK_FROM_NOTIFICATION,
                TEL_CALL_HANDLE,
                mContext,
                TelecomBroadcastReceiver.class);
        Intent smsIntent = new Intent(
                TelecomBroadcastIntentProcessor.ACTION_SEND_SMS_FROM_NOTIFICATION,
                Uri.fromParts(Constants.SCHEME_SMSTO, TEL_CALL_HANDLE.getSchemeSpecificPart(), null),
                mContext,
                TelecomBroadcastReceiver.class);

        assertNotNull(PendingIntent.getBroadcast(mContext, REQUEST_ID,
                callBackIntent, PendingIntent.FLAG_NO_CREATE));
        assertNotNull(PendingIntent.getBroadcast(mContext, REQUEST_ID,
                smsIntent, PendingIntent.FLAG_NO_CREATE));
    }

    @SmallTest
    public void testNoSmsBackAfterMissedSipCall() {
        Notification.Builder builder1 = makeNotificationBuilder("builder1");
        MissedCallNotifierImpl.NotificationBuilderFactory fakeBuilderFactory =
                makeNotificationBuilderFactory(builder1);

        MissedCallNotifier missedCallNotifier = new MissedCallNotifierImpl(mContext,
                mPhoneAccountRegistrar, mDefaultDialerCache, fakeBuilderFactory);
        PhoneAccount phoneAccount = makePhoneAccount(PRIMARY_USER, NO_CAPABILITY);

        MissedCallNotifier.CallInfo fakeCall =
                makeFakeCallInfo(SIP_CALL_HANDLE, CALLER_NAME, CALL_TIMESTAMP,
                phoneAccount.getAccountHandle());
        missedCallNotifier.showMissedCallNotification(fakeCall);

        // Create two intents that correspond to call-back and respond back with SMS, and assert
        // that in the case of a SIP call, no SMS intent is generated.
        Intent callBackIntent = new Intent(
                TelecomBroadcastIntentProcessor.ACTION_CALL_BACK_FROM_NOTIFICATION,
                SIP_CALL_HANDLE,
                mContext,
                TelecomBroadcastReceiver.class);
        Intent smsIntent = new Intent(
                TelecomBroadcastIntentProcessor.ACTION_SEND_SMS_FROM_NOTIFICATION,
                Uri.fromParts(Constants.SCHEME_SMSTO, SIP_CALL_HANDLE.getSchemeSpecificPart(),
                        null),
                mContext,
                TelecomBroadcastReceiver.class);

        assertNotNull(PendingIntent.getBroadcast(mContext, REQUEST_ID,
                callBackIntent, PendingIntent.FLAG_NO_CREATE));
        assertNull(PendingIntent.getBroadcast(mContext, REQUEST_ID,
                smsIntent, PendingIntent.FLAG_NO_CREATE));
    }

    @SmallTest
    public void testLoadOneCallFromDb() throws Exception {
        CallerInfoLookupHelper mockCallerInfoLookupHelper = mock(CallerInfoLookupHelper.class);
        MissedCallNotifier.CallInfoFactory mockCallInfoFactory =
                mock(MissedCallNotifier.CallInfoFactory.class);

        Uri queryUri = ContentProvider.maybeAddUserId(CallLog.Calls.CONTENT_URI,
                PRIMARY_USER.getIdentifier());
        IContentProvider cp = getContentProviderForUser(PRIMARY_USER.getIdentifier());

        Cursor mockMissedCallsCursor = new MockMissedCallCursorBuilder()
                .addEntry(TEL_CALL_HANDLE.getSchemeSpecificPart(),
                        CallLog.Calls.PRESENTATION_ALLOWED, CALL_TIMESTAMP)
                .build();

        when(cp.query(anyString(), eq(queryUri), nullable(String[].class),
                nullable(Bundle.class), nullable(ICancellationSignal.class)))
                .thenReturn(mockMissedCallsCursor);

        PhoneAccount phoneAccount = makePhoneAccount(PRIMARY_USER, NO_CAPABILITY);
        MissedCallNotifier.CallInfo fakeCallInfo = makeFakeCallInfo(TEL_CALL_HANDLE,
                CALLER_NAME, CALL_TIMESTAMP, phoneAccount.getAccountHandle());
        when(mockCallInfoFactory.makeCallInfo(nullable(CallerInfo.class),
                nullable(PhoneAccountHandle.class), nullable(Uri.class), eq(CALL_TIMESTAMP)))
                .thenReturn(fakeCallInfo);

        Notification.Builder builder1 = makeNotificationBuilder("builder1");
        MissedCallNotifierImpl.NotificationBuilderFactory fakeBuilderFactory =
                makeNotificationBuilderFactory(builder1);

        MissedCallNotifier missedCallNotifier = new MissedCallNotifierImpl(mContext,
                mPhoneAccountRegistrar, mDefaultDialerCache, fakeBuilderFactory);

        // AsyncQueryHandler used in reloadFromDatabase interacts poorly with the below
        // timeout-verify, so run this in a new handler to mitigate that.
        Handler h = new Handler(Looper.getMainLooper());
        h.post(() -> missedCallNotifier.reloadFromDatabase(
                mockCallerInfoLookupHelper, mockCallInfoFactory, PRIMARY_USER));
        waitForHandlerAction(h, TEST_TIMEOUT);

        // TelecomSystem.getInstance returns null in this test, so we expect that nothing will
        // happen.
        verify(mockCallerInfoLookupHelper, never()).startLookup(any(Uri.class),
                any(CallerInfoLookupHelper.OnQueryCompleteListener.class));
        // Simulate a boot-complete
        TelecomSystem.setInstance(mTelecomSystem);
        when(mTelecomSystem.isBootComplete()).thenReturn(true);
        h.post(() -> missedCallNotifier.reloadAfterBootComplete(mockCallerInfoLookupHelper,
                mockCallInfoFactory));
        waitForHandlerAction(h, TEST_TIMEOUT);

        Uri escapedHandle = Uri.fromParts(PhoneAccount.SCHEME_TEL,
                TEL_CALL_HANDLE.getSchemeSpecificPart(), null);
        ArgumentCaptor<CallerInfoLookupHelper.OnQueryCompleteListener> listenerCaptor =
                ArgumentCaptor.forClass(CallerInfoLookupHelper.OnQueryCompleteListener.class);
        verify(mockCallerInfoLookupHelper, timeout(TEST_TIMEOUT)).startLookup(eq(escapedHandle),
                listenerCaptor.capture());

        CallerInfo ci = new CallerInfo();
        listenerCaptor.getValue().onCallerInfoQueryComplete(escapedHandle, ci);
        verify(mockCallInfoFactory).makeCallInfo(eq(ci), isNull(PhoneAccountHandle.class),
                eq(escapedHandle), eq(CALL_TIMESTAMP));
    }

    @SmallTest
    public void testLoadTwoCallsFromDb() throws Exception {
        TelecomSystem.setInstance(mTelecomSystem);
        when(mTelecomSystem.isBootComplete()).thenReturn(true);
        CallerInfoLookupHelper mockCallerInfoLookupHelper = mock(CallerInfoLookupHelper.class);
        MissedCallNotifier.CallInfoFactory mockCallInfoFactory =
                mock(MissedCallNotifier.CallInfoFactory.class);

        Cursor mockMissedCallsCursor = new MockMissedCallCursorBuilder()
                .addEntry(TEL_CALL_HANDLE.getSchemeSpecificPart(),
                        CallLog.Calls.PRESENTATION_ALLOWED, CALL_TIMESTAMP)
                .addEntry(SIP_CALL_HANDLE.getSchemeSpecificPart(),
                        CallLog.Calls.PRESENTATION_ALLOWED, CALL_TIMESTAMP)
                .build();

        Uri queryUri = ContentProvider.maybeAddUserId(CallLog.Calls.CONTENT_URI,
                PRIMARY_USER.getIdentifier());
        IContentProvider cp = getContentProviderForUser(PRIMARY_USER.getIdentifier());

        when(cp.query(anyString(), eq(queryUri), nullable(String[].class),
                nullable(Bundle.class), nullable(ICancellationSignal.class)))
                .thenReturn(mockMissedCallsCursor);

        PhoneAccount phoneAccount = makePhoneAccount(PRIMARY_USER, NO_CAPABILITY);
        MissedCallNotifier.CallInfo fakeCallInfo = makeFakeCallInfo(TEL_CALL_HANDLE,
                CALLER_NAME, CALL_TIMESTAMP, phoneAccount.getAccountHandle());
        when(mockCallInfoFactory.makeCallInfo(nullable(CallerInfo.class),
                nullable(PhoneAccountHandle.class), nullable(Uri.class), eq(CALL_TIMESTAMP)))
                .thenReturn(fakeCallInfo);

        Notification.Builder builder1 = makeNotificationBuilder("builder1");
        MissedCallNotifierImpl.NotificationBuilderFactory fakeBuilderFactory =
                makeNotificationBuilderFactory(builder1);

        MissedCallNotifier missedCallNotifier = new MissedCallNotifierImpl(mContext,
                mPhoneAccountRegistrar, mDefaultDialerCache, fakeBuilderFactory);

        // AsyncQueryHandler used in reloadFromDatabase interacts poorly with the below
        // timeout-verify, so run this in a new handler to mitigate that.
        Handler h = new Handler(Looper.getMainLooper());
        h.post(() -> missedCallNotifier.reloadFromDatabase(
                mockCallerInfoLookupHelper, mockCallInfoFactory, PRIMARY_USER));
        waitForHandlerAction(h, TEST_TIMEOUT);

        Uri escapedTelHandle = Uri.fromParts(PhoneAccount.SCHEME_TEL,
                TEL_CALL_HANDLE.getSchemeSpecificPart(), null);
        Uri escapedSipHandle = Uri.fromParts(PhoneAccount.SCHEME_SIP,
                SIP_CALL_HANDLE.getSchemeSpecificPart(), null);

        ArgumentCaptor<CallerInfoLookupHelper.OnQueryCompleteListener> listenerCaptor =
                ArgumentCaptor.forClass(CallerInfoLookupHelper.OnQueryCompleteListener.class);
        verify(mockCallerInfoLookupHelper, timeout(TEST_TIMEOUT)).startLookup(eq(escapedTelHandle),
                listenerCaptor.capture());
        verify(mockCallerInfoLookupHelper, timeout(TEST_TIMEOUT)).startLookup(eq(escapedSipHandle),
                listenerCaptor.capture());

        CallerInfo ci = new CallerInfo();
        listenerCaptor.getAllValues().get(0).onCallerInfoQueryComplete(escapedTelHandle, ci);
        listenerCaptor.getAllValues().get(1).onCallerInfoQueryComplete(escapedSipHandle, ci);

        // Verify that two notifications were generated, both with the same id.
        verify(mNotificationManager, times(2)).notifyAsUser(nullable(String.class), eq(1),
                nullable(Notification.class), eq(PRIMARY_USER));
    }

    private Notification.Builder makeNotificationBuilder(String label) {
        Notification.Builder builder = spy(new Notification.Builder(mContext));
        Notification notification = mock(Notification.class);
        when(notification.toString()).thenReturn(label);
        when(builder.toString()).thenReturn(label);
        doReturn(notification).when(builder).build();
        return builder;
    }

    private MissedCallNotifier.CallInfo makeFakeCallInfo(Uri handle, String name, long timestamp,
            PhoneAccountHandle phoneAccountHandle) {
        MissedCallNotifier.CallInfo fakeCall = mock(MissedCallNotifier.CallInfo.class);
        when(fakeCall.getHandle()).thenReturn(handle);
        when(fakeCall.getHandleSchemeSpecificPart()).thenReturn(handle.getSchemeSpecificPart());
        when(fakeCall.getName()).thenReturn(name);
        when(fakeCall.getCreationTimeMillis()).thenReturn(timestamp);
        when(fakeCall.getPhoneAccountHandle()).thenReturn(phoneAccountHandle);
        return fakeCall;
    }

    private MissedCallNotifierImpl.NotificationBuilderFactory makeNotificationBuilderFactory(
            Notification.Builder... builders) {
        MissedCallNotifierImpl.NotificationBuilderFactory builderFactory =
                mock(MissedCallNotifierImpl.NotificationBuilderFactory.class);
        when(builderFactory.getBuilder(mContext)).thenReturn(builders[0],
                Arrays.copyOfRange(builders, 1, builders.length));
        return builderFactory;
    }

    private MissedCallNotifier makeMissedCallNotifier(
            NotificationBuilderFactory fakeBuilderFactory, UserHandle currentUser) {
        MissedCallNotifier missedCallNotifier = new MissedCallNotifierImpl(mContext,
                mPhoneAccountRegistrar, mDefaultDialerCache, fakeBuilderFactory);
        missedCallNotifier.setCurrentUserHandle(currentUser);
        return missedCallNotifier;
    }

    private PhoneAccount makePhoneAccount(UserHandle userHandle, int capability) {
        ComponentName componentName = new ComponentName("com.anything", "com.whatever");
        PhoneAccountHandle phoneAccountHandle = new PhoneAccountHandle(componentName, "id",
                userHandle);
        PhoneAccount.Builder builder = new PhoneAccount.Builder(phoneAccountHandle, "test");
        builder.setCapabilities(capability);
        PhoneAccount phoneAccount = builder.build();
        when(mPhoneAccountRegistrar.getPhoneAccountUnchecked(phoneAccountHandle))
                .thenReturn(phoneAccount);
        return phoneAccount;
    }

    private IContentProvider getContentProviderForUser(int userId) {
        return mContext.getContentResolver().acquireProvider(userId + "@call_log");
    }

}
