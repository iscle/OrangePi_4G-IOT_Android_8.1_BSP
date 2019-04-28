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


import android.content.ComponentName;
import android.content.ContentProvider;
import android.content.ContentValues;
import android.content.Context;
import android.content.IContentProvider;
import android.content.pm.UserInfo;
import android.location.Country;
import android.location.CountryDetector;
import android.location.CountryListener;
import android.net.Uri;
import android.os.Looper;
import android.os.PersistableBundle;
import android.os.UserHandle;
import android.os.UserManager;
import android.provider.CallLog;
import android.provider.CallLog.Calls;
import android.support.test.filters.FlakyTest;
import android.telecom.DisconnectCause;
import android.telecom.PhoneAccount;
import android.telecom.PhoneAccountHandle;
import android.telecom.VideoProfile;
import android.telephony.CarrierConfigManager;
import android.telephony.PhoneNumberUtils;
import android.test.suitebuilder.annotation.MediumTest;
import android.test.suitebuilder.annotation.SmallTest;

import com.android.server.telecom.Call;
import com.android.server.telecom.CallLogManager;
import com.android.server.telecom.CallState;
import com.android.server.telecom.MissedCallNotifier;
import com.android.server.telecom.PhoneAccountRegistrar;
import com.android.server.telecom.R;
import com.android.server.telecom.TelephonyUtil;

import static org.mockito.Matchers.any;
import static org.mockito.Matchers.anyString;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.stubbing.Answer;

import java.util.Arrays;
import java.util.Locale;

public class CallLogManagerTest extends TelecomTestCase {

    private CallLogManager mCallLogManager;
    private IContentProvider mContentProvider;
    private PhoneAccountHandle mDefaultAccountHandle;
    private PhoneAccountHandle mOtherUserAccountHandle;
    private PhoneAccountHandle mManagedProfileAccountHandle;

    private static final Uri TEL_PHONEHANDLE = Uri.parse("tel:5555551234");

    private static final PhoneAccountHandle EMERGENCY_ACCT_HANDLE = TelephonyUtil
            .getDefaultEmergencyPhoneAccount()
            .getAccountHandle();

    private static final int NO_VIDEO_STATE = VideoProfile.STATE_AUDIO_ONLY;
    private static final int BIDIRECTIONAL_VIDEO_STATE = VideoProfile.STATE_BIDIRECTIONAL;
    private static final String POST_DIAL_STRING = ";12345";
    private static final String VIA_NUMBER_STRING = "5555555678";
    private static final String TEST_PHONE_ACCOUNT_ID= "testPhoneAccountId";

    private static final int TEST_TIMEOUT_MILLIS = 200;
    private static final int CURRENT_USER_ID = 0;
    private static final int OTHER_USER_ID = 10;
    private static final int MANAGED_USER_ID = 11;

    private static final String TEST_ISO = "KR";
    private static final String TEST_ISO_2 = "JP";

    @Mock PhoneAccountRegistrar mMockPhoneAccountRegistrar;

    @Mock
    MissedCallNotifier mMissedCallNotifier;

    @Override
    public void setUp() throws Exception {
        super.setUp();
        mContext = mComponentContextFixture.getTestDouble().getApplicationContext();
        mCallLogManager = new CallLogManager(mContext, mMockPhoneAccountRegistrar,
                mMissedCallNotifier);
        mContentProvider =
                mContext.getContentResolver().acquireProvider("0@call_log");
        mDefaultAccountHandle = new PhoneAccountHandle(
                new ComponentName("com.android.server.telecom.tests", "CallLogManagerTest"),
                TEST_PHONE_ACCOUNT_ID,
                UserHandle.of(CURRENT_USER_ID)
        );

        mOtherUserAccountHandle = new PhoneAccountHandle(
                new ComponentName("com.android.server.telecom.tests", "CallLogManagerTest"),
                TEST_PHONE_ACCOUNT_ID,
                UserHandle.of(OTHER_USER_ID)
        );

        mManagedProfileAccountHandle = new PhoneAccountHandle(
                new ComponentName("com.android.server.telecom.tests", "CallLogManagerTest"),
                TEST_PHONE_ACCOUNT_ID,
                UserHandle.of(MANAGED_USER_ID)
        );

        UserManager userManager = (UserManager) mContext.getSystemService(Context.USER_SERVICE);
        UserInfo userInfo = new UserInfo(CURRENT_USER_ID, "test", 0);
        UserInfo otherUserInfo = new UserInfo(OTHER_USER_ID, "test2", 0);
        UserInfo managedProfileUserInfo = new UserInfo(MANAGED_USER_ID, "test3",
                UserInfo.FLAG_MANAGED_PROFILE);

        doAnswer(new Answer<Uri>() {
            @Override
            public Uri answer(InvocationOnMock invocation) throws Throwable {
                return (Uri) invocation.getArguments()[1];
            }
        }).when(mContentProvider).insert(anyString(), any(Uri.class), any(ContentValues.class));

        when(userManager.isUserRunning(any(UserHandle.class))).thenReturn(true);
        when(userManager.isUserUnlocked(any(UserHandle.class))).thenReturn(true);
        when(userManager.hasUserRestriction(any(String.class), any(UserHandle.class)))
                .thenReturn(false);
        when(userManager.getUsers(any(Boolean.class)))
                .thenReturn(Arrays.asList(userInfo, otherUserInfo, managedProfileUserInfo));
        when(userManager.getUserInfo(eq(CURRENT_USER_ID))).thenReturn(userInfo);
        when(userManager.getUserInfo(eq(OTHER_USER_ID))).thenReturn(otherUserInfo);
        when(userManager.getUserInfo(eq(MANAGED_USER_ID))).thenReturn(managedProfileUserInfo);
    }

    @MediumTest
    public void testDontLogCancelledCall() {
        Call fakeCall = makeFakeCall(
                DisconnectCause.CANCELED,
                false, // isConference
                false, // isIncoming
                1L, // creationTimeMillis
                1000L, // ageMillis
                TEL_PHONEHANDLE, // callHandle
                mDefaultAccountHandle, // phoneAccountHandle
                NO_VIDEO_STATE, // callVideoState
                POST_DIAL_STRING, // postDialDigits
                VIA_NUMBER_STRING, // viaNumber
                UserHandle.of(CURRENT_USER_ID)
        );
        mCallLogManager.onCallStateChanged(fakeCall, CallState.DIALING, CallState.DISCONNECTED);
        verifyNoInsertion();
        mCallLogManager.onCallStateChanged(fakeCall, CallState.DIALING, CallState.ABORTED);
        verifyNoInsertion();
    }

    @MediumTest
    public void testDontLogChoosingAccountCall() {
        when(mMockPhoneAccountRegistrar.getPhoneAccountUnchecked(any(PhoneAccountHandle.class)))
                .thenReturn(makeFakePhoneAccount(mDefaultAccountHandle, CURRENT_USER_ID));
        Call fakeCall = makeFakeCall(
                DisconnectCause.OTHER, // disconnectCauseCode
                false, // isConference
                false, // isIncoming
                1L, // creationTimeMillis
                1000L, // ageMillis
                TEL_PHONEHANDLE, // callHandle
                mDefaultAccountHandle, // phoneAccountHandle
                NO_VIDEO_STATE, // callVideoState
                POST_DIAL_STRING, // postDialDigits
                VIA_NUMBER_STRING, // viaNumber
                UserHandle.of(CURRENT_USER_ID)
        );
        mCallLogManager.onCallStateChanged(fakeCall, CallState.SELECT_PHONE_ACCOUNT,
                CallState.DISCONNECTED);
        verifyNoInsertion();
    }

    @MediumTest
    public void testDontLogCallsFromEmergencyAccount() {
        when(mMockPhoneAccountRegistrar.getPhoneAccountUnchecked(any(PhoneAccountHandle.class)))
                .thenReturn(makeFakePhoneAccount(EMERGENCY_ACCT_HANDLE, 0));
        CarrierConfigManager mockCarrierConfigManager =
                (CarrierConfigManager) mComponentContextFixture.getTestDouble()
                        .getApplicationContext().getSystemService(Context.CARRIER_CONFIG_SERVICE);
        PersistableBundle bundle = new PersistableBundle();
        bundle.putBoolean(CarrierConfigManager.KEY_ALLOW_EMERGENCY_NUMBERS_IN_CALL_LOG_BOOL, false);
        when(mockCarrierConfigManager.getConfig()).thenReturn(bundle);

        Call fakeCall = makeFakeCall(
                DisconnectCause.OTHER, // disconnectCauseCode
                false, // isConference
                false, // isIncoming
                1L, // creationTimeMillis
                1000L, // ageMillis
                TEL_PHONEHANDLE, // callHandle
                EMERGENCY_ACCT_HANDLE, // phoneAccountHandle
                NO_VIDEO_STATE, // callVideoState
                POST_DIAL_STRING, // postDialDigits
                VIA_NUMBER_STRING, // viaNumber
                UserHandle.of(CURRENT_USER_ID)
        );
        mCallLogManager.onCallStateChanged(fakeCall, CallState.ACTIVE, CallState.DISCONNECTED);
        verifyNoInsertion();
    }

    @MediumTest
    public void testLogCallDirectionOutgoing() {
        when(mMockPhoneAccountRegistrar.getPhoneAccountUnchecked(any(PhoneAccountHandle.class)))
                .thenReturn(makeFakePhoneAccount(mDefaultAccountHandle, CURRENT_USER_ID));
        Call fakeOutgoingCall = makeFakeCall(
                DisconnectCause.OTHER, // disconnectCauseCode
                false, // isConference
                false, // isIncoming
                1L, // creationTimeMillis
                1000L, // ageMillis
                TEL_PHONEHANDLE, // callHandle
                mDefaultAccountHandle, // phoneAccountHandle
                NO_VIDEO_STATE, // callVideoState
                POST_DIAL_STRING, // postDialDigits
                VIA_NUMBER_STRING, // viaNumber
                UserHandle.of(CURRENT_USER_ID)
        );
        mCallLogManager.onCallStateChanged(fakeOutgoingCall, CallState.ACTIVE,
                CallState.DISCONNECTED);
        ContentValues insertedValues = verifyInsertionWithCapture(CURRENT_USER_ID);
        assertEquals(insertedValues.getAsInteger(CallLog.Calls.TYPE),
                Integer.valueOf(CallLog.Calls.OUTGOING_TYPE));
    }

    @MediumTest
    public void testLogCallDirectionIncoming() {
        when(mMockPhoneAccountRegistrar.getPhoneAccountUnchecked(any(PhoneAccountHandle.class)))
                .thenReturn(makeFakePhoneAccount(mDefaultAccountHandle, CURRENT_USER_ID));
        Call fakeIncomingCall = makeFakeCall(
                DisconnectCause.OTHER, // disconnectCauseCode
                false, // isConference
                true, // isIncoming
                1L, // creationTimeMillis
                1000L, // ageMillis
                TEL_PHONEHANDLE, // callHandle
                mDefaultAccountHandle, // phoneAccountHandle
                NO_VIDEO_STATE, // callVideoState
                POST_DIAL_STRING, // postDialDigits
                VIA_NUMBER_STRING, // viaNumber
                null
        );
        mCallLogManager.onCallStateChanged(fakeIncomingCall, CallState.ACTIVE,
                CallState.DISCONNECTED);
        ContentValues insertedValues = verifyInsertionWithCapture(CURRENT_USER_ID);
        assertEquals(insertedValues.getAsInteger(CallLog.Calls.TYPE),
                Integer.valueOf(CallLog.Calls.INCOMING_TYPE));
    }

    @MediumTest
    public void testLogCallDirectionMissed() {
        when(mMockPhoneAccountRegistrar.getPhoneAccountUnchecked(any(PhoneAccountHandle.class)))
                .thenReturn(makeFakePhoneAccount(mDefaultAccountHandle, CURRENT_USER_ID));
        Call fakeMissedCall = makeFakeCall(
                DisconnectCause.MISSED, // disconnectCauseCode
                false, // isConference
                true, // isIncoming
                1L, // creationTimeMillis
                1000L, // ageMillis
                TEL_PHONEHANDLE, // callHandle
                mDefaultAccountHandle, // phoneAccountHandle
                NO_VIDEO_STATE, // callVideoState
                POST_DIAL_STRING, // postDialDigits
                VIA_NUMBER_STRING, // viaNumber
                null
        );

        mCallLogManager.onCallStateChanged(fakeMissedCall, CallState.ACTIVE,
                CallState.DISCONNECTED);
        ContentValues insertedValues = verifyInsertionWithCapture(CURRENT_USER_ID);
        assertEquals(insertedValues.getAsInteger(CallLog.Calls.TYPE),
                Integer.valueOf(CallLog.Calls.MISSED_TYPE));
        // Timeout needed because showMissedCallNotification is called from onPostExecute.
        verify(mMissedCallNotifier, timeout(TEST_TIMEOUT_MILLIS))
                .showMissedCallNotification(any(MissedCallNotifier.CallInfo.class));
    }

    @MediumTest
    public void testLogCallDirectionRejected() {
        when(mMockPhoneAccountRegistrar.getPhoneAccountUnchecked(any(PhoneAccountHandle.class)))
                .thenReturn(makeFakePhoneAccount(mDefaultAccountHandle, CURRENT_USER_ID));
        Call fakeMissedCall = makeFakeCall(
                DisconnectCause.REJECTED, // disconnectCauseCode
                false, // isConference
                true, // isIncoming
                1L, // creationTimeMillis
                1000L, // ageMillis
                TEL_PHONEHANDLE, // callHandle
                mDefaultAccountHandle, // phoneAccountHandle
                NO_VIDEO_STATE, // callVideoState
                POST_DIAL_STRING, // postDialDigits
                VIA_NUMBER_STRING, // viaNumber
                null
        );

        mCallLogManager.onCallStateChanged(fakeMissedCall, CallState.ACTIVE,
                CallState.DISCONNECTED);
        ContentValues insertedValues = verifyInsertionWithCapture(CURRENT_USER_ID);
        assertEquals(insertedValues.getAsInteger(CallLog.Calls.TYPE),
                Integer.valueOf(Calls.REJECTED_TYPE));
    }

    @MediumTest
    public void testCreationTimeAndAge() {
        when(mMockPhoneAccountRegistrar.getPhoneAccountUnchecked(any(PhoneAccountHandle.class)))
                .thenReturn(makeFakePhoneAccount(mDefaultAccountHandle, CURRENT_USER_ID));
        long currentTime = System.currentTimeMillis();
        long duration = 1000L;
        Call fakeCall = makeFakeCall(
                DisconnectCause.OTHER, // disconnectCauseCode
                false, // isConference
                false, // isIncoming
                currentTime, // creationTimeMillis
                duration, // ageMillis
                TEL_PHONEHANDLE, // callHandle
                mDefaultAccountHandle, // phoneAccountHandle
                NO_VIDEO_STATE, // callVideoState
                POST_DIAL_STRING, // postDialDigits
                VIA_NUMBER_STRING, // viaNumber
                UserHandle.of(CURRENT_USER_ID)
        );
        mCallLogManager.onCallStateChanged(fakeCall, CallState.ACTIVE, CallState.DISCONNECTED);
        ContentValues insertedValues = verifyInsertionWithCapture(CURRENT_USER_ID);
        assertEquals(insertedValues.getAsLong(CallLog.Calls.DATE),
                Long.valueOf(currentTime));
        assertEquals(insertedValues.getAsLong(CallLog.Calls.DURATION),
                Long.valueOf(duration / 1000));
    }

    @MediumTest
    public void testLogPhoneAccountId() {
        when(mMockPhoneAccountRegistrar.getPhoneAccountUnchecked(any(PhoneAccountHandle.class)))
                .thenReturn(makeFakePhoneAccount(mDefaultAccountHandle, CURRENT_USER_ID));
        Call fakeCall = makeFakeCall(
                DisconnectCause.OTHER, // disconnectCauseCode
                false, // isConference
                true, // isIncoming
                1L, // creationTimeMillis
                1000L, // ageMillis
                TEL_PHONEHANDLE, // callHandle
                mDefaultAccountHandle, // phoneAccountHandle
                NO_VIDEO_STATE, // callVideoState
                POST_DIAL_STRING, // postDialDigits
                VIA_NUMBER_STRING, // viaNumber
                UserHandle.of(CURRENT_USER_ID)
        );
        mCallLogManager.onCallStateChanged(fakeCall, CallState.ACTIVE, CallState.DISCONNECTED);
        ContentValues insertedValues = verifyInsertionWithCapture(CURRENT_USER_ID);
        assertEquals(insertedValues.getAsString(CallLog.Calls.PHONE_ACCOUNT_ID),
                TEST_PHONE_ACCOUNT_ID);
    }

    @MediumTest
    public void testLogCorrectPhoneNumber() {
        when(mMockPhoneAccountRegistrar.getPhoneAccountUnchecked(any(PhoneAccountHandle.class)))
                .thenReturn(makeFakePhoneAccount(mDefaultAccountHandle, CURRENT_USER_ID));
        Call fakeCall = makeFakeCall(
                DisconnectCause.OTHER, // disconnectCauseCode
                false, // isConference
                true, // isIncoming
                1L, // creationTimeMillis
                1000L, // ageMillis
                TEL_PHONEHANDLE, // callHandle
                mDefaultAccountHandle, // phoneAccountHandle
                NO_VIDEO_STATE, // callVideoState
                POST_DIAL_STRING, // postDialDigits
                VIA_NUMBER_STRING, // viaNumber
                UserHandle.of(CURRENT_USER_ID)
        );
        mCallLogManager.onCallStateChanged(fakeCall, CallState.ACTIVE, CallState.DISCONNECTED);
        ContentValues insertedValues = verifyInsertionWithCapture(CURRENT_USER_ID);
        assertEquals(insertedValues.getAsString(CallLog.Calls.NUMBER),
                TEL_PHONEHANDLE.getSchemeSpecificPart());
        assertEquals(insertedValues.getAsString(CallLog.Calls.POST_DIAL_DIGITS), POST_DIAL_STRING);
        String expectedNumber = PhoneNumberUtils.formatNumber(VIA_NUMBER_STRING, "US");
        assertEquals(insertedValues.getAsString(Calls.VIA_NUMBER), expectedNumber);
    }

    @MediumTest
    public void testLogCallVideoFeatures() {
        when(mMockPhoneAccountRegistrar.getPhoneAccountUnchecked(any(PhoneAccountHandle.class)))
                .thenReturn(makeFakePhoneAccount(mDefaultAccountHandle, CURRENT_USER_ID));
        Call fakeVideoCall = makeFakeCall(
                DisconnectCause.OTHER, // disconnectCauseCode
                false, // isConference
                true, // isIncoming
                1L, // creationTimeMillis
                1000L, // ageMillis
                TEL_PHONEHANDLE, // callHandle
                mDefaultAccountHandle, // phoneAccountHandle
                BIDIRECTIONAL_VIDEO_STATE, // callVideoState
                POST_DIAL_STRING, // postDialDigits
                VIA_NUMBER_STRING, // viaNumber
                UserHandle.of(CURRENT_USER_ID)
        );
        mCallLogManager.onCallStateChanged(fakeVideoCall, CallState.ACTIVE, CallState.DISCONNECTED);
        ContentValues insertedValues = verifyInsertionWithCapture(CURRENT_USER_ID);
        assertTrue((insertedValues.getAsInteger(CallLog.Calls.FEATURES)
                & CallLog.Calls.FEATURES_VIDEO) == CallLog.Calls.FEATURES_VIDEO);
    }

    @MediumTest
    @FlakyTest
    public void testLogCallDirectionOutgoingWithMultiUserCapability() {
        when(mMockPhoneAccountRegistrar.getPhoneAccountUnchecked(any(PhoneAccountHandle.class)))
                .thenReturn(makeFakePhoneAccount(mOtherUserAccountHandle,
                        PhoneAccount.CAPABILITY_MULTI_USER));
        Call fakeOutgoingCall = makeFakeCall(
                DisconnectCause.OTHER, // disconnectCauseCode
                false, // isConference
                false, // isIncoming
                1L, // creationTimeMillis
                1000L, // ageMillis
                TEL_PHONEHANDLE, // callHandle
                mDefaultAccountHandle, // phoneAccountHandle
                NO_VIDEO_STATE, // callVideoState
                POST_DIAL_STRING, // postDialDigits
                VIA_NUMBER_STRING, // viaNumber
                UserHandle.of(CURRENT_USER_ID)
        );
        mCallLogManager.onCallStateChanged(fakeOutgoingCall, CallState.ACTIVE,
                CallState.DISCONNECTED);

        // Outgoing call placed through a phone account with multi user capability is inserted to
        // all users except managed profile.
        ContentValues insertedValues = verifyInsertionWithCapture(CURRENT_USER_ID);
        assertEquals(insertedValues.getAsInteger(CallLog.Calls.TYPE),
                Integer.valueOf(CallLog.Calls.OUTGOING_TYPE));
        insertedValues = verifyInsertionWithCapture(OTHER_USER_ID);
        assertEquals(insertedValues.getAsInteger(CallLog.Calls.TYPE),
                Integer.valueOf(CallLog.Calls.OUTGOING_TYPE));
        verifyNoInsertionInUser(MANAGED_USER_ID);
    }

    @MediumTest
    public void testLogCallDirectionIncomingWithMultiUserCapability() {
        when(mMockPhoneAccountRegistrar.getPhoneAccountUnchecked(any(PhoneAccountHandle.class)))
                .thenReturn(makeFakePhoneAccount(mOtherUserAccountHandle,
                        PhoneAccount.CAPABILITY_MULTI_USER));
        Call fakeIncomingCall = makeFakeCall(
                DisconnectCause.OTHER, // disconnectCauseCode
                false, // isConference
                true, // isIncoming
                1L, // creationTimeMillis
                1000L, // ageMillis
                TEL_PHONEHANDLE, // callHandle
                mDefaultAccountHandle, // phoneAccountHandle
                NO_VIDEO_STATE, // callVideoState
                POST_DIAL_STRING, // postDialDigits
                VIA_NUMBER_STRING, // viaNumber
                null
        );
        mCallLogManager.onCallStateChanged(fakeIncomingCall, CallState.ACTIVE,
                CallState.DISCONNECTED);

        // Incoming call using a phone account with multi user capability is inserted to all users
        // except managed profile.
        ContentValues insertedValues = verifyInsertionWithCapture(CURRENT_USER_ID);
        assertEquals(insertedValues.getAsInteger(CallLog.Calls.TYPE),
                Integer.valueOf(CallLog.Calls.INCOMING_TYPE));
        insertedValues = verifyInsertionWithCapture(OTHER_USER_ID);
        assertEquals(insertedValues.getAsInteger(CallLog.Calls.TYPE),
                Integer.valueOf(CallLog.Calls.INCOMING_TYPE));
        verifyNoInsertionInUser(MANAGED_USER_ID);
    }

    @MediumTest
    public void testLogCallDirectionOutgoingWithMultiUserCapabilityFromManagedProfile() {
        when(mMockPhoneAccountRegistrar.getPhoneAccountUnchecked(any(PhoneAccountHandle.class)))
                .thenReturn(makeFakePhoneAccount(mManagedProfileAccountHandle,
                        PhoneAccount.CAPABILITY_MULTI_USER));
        Call fakeOutgoingCall = makeFakeCall(
                DisconnectCause.OTHER, // disconnectCauseCode
                false, // isConference
                false, // isIncoming
                1L, // creationTimeMillis
                1000L, // ageMillis
                TEL_PHONEHANDLE, // callHandle
                mManagedProfileAccountHandle, // phoneAccountHandle
                NO_VIDEO_STATE, // callVideoState
                POST_DIAL_STRING, // postDialDigits
                VIA_NUMBER_STRING, // viaNumber
                UserHandle.of(MANAGED_USER_ID)
        );
        mCallLogManager.onCallStateChanged(fakeOutgoingCall, CallState.ACTIVE,
                CallState.DISCONNECTED);

        // Outgoing call placed through work dialer should be inserted to managed profile only.
        verifyNoInsertionInUser(CURRENT_USER_ID);
        verifyNoInsertionInUser(OTHER_USER_ID);
        ContentValues insertedValues = verifyInsertionWithCapture(MANAGED_USER_ID);
        assertEquals(insertedValues.getAsInteger(CallLog.Calls.TYPE),
                Integer.valueOf(CallLog.Calls.OUTGOING_TYPE));
    }

    @MediumTest
    @FlakyTest
    public void testLogCallDirectionOutgoingFromManagedProfile() {
        when(mMockPhoneAccountRegistrar.getPhoneAccountUnchecked(any(PhoneAccountHandle.class)))
                .thenReturn(makeFakePhoneAccount(mManagedProfileAccountHandle, 0));
        Call fakeOutgoingCall = makeFakeCall(
                DisconnectCause.OTHER, // disconnectCauseCode
                false, // isConference
                false, // isIncoming
                1L, // creationTimeMillis
                1000L, // ageMillis
                TEL_PHONEHANDLE, // callHandle
                mManagedProfileAccountHandle, // phoneAccountHandle
                NO_VIDEO_STATE, // callVideoState
                POST_DIAL_STRING, // postDialDigits
                VIA_NUMBER_STRING, // viaNumber
                UserHandle.of(MANAGED_USER_ID)
        );
        mCallLogManager.onCallStateChanged(fakeOutgoingCall, CallState.ACTIVE,
                CallState.DISCONNECTED);

        // Outgoing call using phone account in managed profile should be inserted to managed
        // profile only.
        verifyNoInsertionInUser(CURRENT_USER_ID);
        verifyNoInsertionInUser(OTHER_USER_ID);
        ContentValues insertedValues = verifyInsertionWithCapture(MANAGED_USER_ID);
        assertEquals(insertedValues.getAsInteger(CallLog.Calls.TYPE),
                Integer.valueOf(CallLog.Calls.OUTGOING_TYPE));
    }

    @MediumTest
    public void testLogCallDirectionIngoingFromManagedProfile() {
        when(mMockPhoneAccountRegistrar.getPhoneAccountUnchecked(any(PhoneAccountHandle.class)))
                .thenReturn(makeFakePhoneAccount(mManagedProfileAccountHandle, 0));
        Call fakeOutgoingCall = makeFakeCall(
                DisconnectCause.OTHER, // disconnectCauseCode
                false, // isConference
                true, // isIncoming
                1L, // creationTimeMillis
                1000L, // ageMillis
                TEL_PHONEHANDLE, // callHandle
                mManagedProfileAccountHandle, // phoneAccountHandle
                NO_VIDEO_STATE, // callVideoState
                POST_DIAL_STRING, // postDialDigits
                VIA_NUMBER_STRING, // viaNumber
                null
        );
        mCallLogManager.onCallStateChanged(fakeOutgoingCall, CallState.ACTIVE,
                CallState.DISCONNECTED);

        // Incoming call using phone account in managed profile should be inserted to managed
        // profile only.
        verifyNoInsertionInUser(CURRENT_USER_ID);
        verifyNoInsertionInUser(OTHER_USER_ID);
        ContentValues insertedValues = verifyInsertionWithCapture(MANAGED_USER_ID);
        assertEquals(insertedValues.getAsInteger(CallLog.Calls.TYPE),
                Integer.valueOf(Calls.INCOMING_TYPE));
    }

    /**
     * Ensure call data usage is persisted to the call log when present in the call.
     */
    @MediumTest
    public void testLogCallDataUsageSet() {
        when(mMockPhoneAccountRegistrar.getPhoneAccountUnchecked(any(PhoneAccountHandle.class)))
                .thenReturn(makeFakePhoneAccount(mDefaultAccountHandle, CURRENT_USER_ID));
        Call fakeVideoCall = makeFakeCall(
                DisconnectCause.OTHER, // disconnectCauseCode
                false, // isConference
                true, // isIncoming
                1L, // creationTimeMillis
                1000L, // ageMillis
                TEL_PHONEHANDLE, // callHandle
                mDefaultAccountHandle, // phoneAccountHandle
                BIDIRECTIONAL_VIDEO_STATE, // callVideoState
                POST_DIAL_STRING, // postDialDigits
                VIA_NUMBER_STRING, // viaNumber
                UserHandle.of(CURRENT_USER_ID), // initiatingUser
                1000 // callDataUsage
        );
        mCallLogManager.onCallStateChanged(fakeVideoCall, CallState.ACTIVE, CallState.DISCONNECTED);
        ContentValues insertedValues = verifyInsertionWithCapture(CURRENT_USER_ID);
        assertEquals(Long.valueOf(1000), insertedValues.getAsLong(CallLog.Calls.DATA_USAGE));
    }

    /**
     * Ensures call data usage is null in the call log when not set on the call.
     */
    @MediumTest
    public void testLogCallDataUsageNotSet() {
        when(mMockPhoneAccountRegistrar.getPhoneAccountUnchecked(any(PhoneAccountHandle.class)))
                .thenReturn(makeFakePhoneAccount(mDefaultAccountHandle, CURRENT_USER_ID));
        Call fakeVideoCall = makeFakeCall(
                DisconnectCause.OTHER, // disconnectCauseCode
                false, // isConference
                true, // isIncoming
                1L, // creationTimeMillis
                1000L, // ageMillis
                TEL_PHONEHANDLE, // callHandle
                mDefaultAccountHandle, // phoneAccountHandle
                BIDIRECTIONAL_VIDEO_STATE, // callVideoState
                POST_DIAL_STRING, // postDialDigits
                VIA_NUMBER_STRING, // viaNumber
                UserHandle.of(CURRENT_USER_ID), // initiatingUser
                Call.DATA_USAGE_NOT_SET // callDataUsage
        );
        mCallLogManager.onCallStateChanged(fakeVideoCall, CallState.ACTIVE, CallState.DISCONNECTED);
        ContentValues insertedValues = verifyInsertionWithCapture(CURRENT_USER_ID);
        assertNull(insertedValues.getAsLong(CallLog.Calls.DATA_USAGE));
    }

    @SmallTest
    public void testCountryIso_setCache() {
        Country testCountry = new Country(TEST_ISO, Country.COUNTRY_SOURCE_LOCALE);
        CountryDetector mockDetector = (CountryDetector) mContext.getSystemService(
                Context.COUNTRY_DETECTOR);
        when(mockDetector.detectCountry()).thenReturn(testCountry);

        String resultIso = mCallLogManager.getCountryIso();

        verifyCountryIso(mockDetector, resultIso);
    }

    @SmallTest
    public void testCountryIso_newCountryDetected() {
        Country testCountry = new Country(TEST_ISO, Country.COUNTRY_SOURCE_LOCALE);
        Country testCountry2 = new Country(TEST_ISO_2, Country.COUNTRY_SOURCE_LOCALE);
        CountryDetector mockDetector = (CountryDetector) mContext.getSystemService(
                Context.COUNTRY_DETECTOR);
        when(mockDetector.detectCountry()).thenReturn(testCountry);
        // Put TEST_ISO in the Cache
        String resultIso = mCallLogManager.getCountryIso();
        ArgumentCaptor<CountryListener> captor = verifyCountryIso(mockDetector, resultIso);

        // Change ISO to TEST_ISO_2
        CountryListener listener = captor.getValue();
        listener.onCountryDetected(testCountry2);

        String resultIso2 = mCallLogManager.getCountryIso();
        assertEquals(TEST_ISO_2, resultIso2);
    }

    private ArgumentCaptor<CountryListener> verifyCountryIso(CountryDetector mockDetector,
            String resultIso) {
        ArgumentCaptor<CountryListener> captor = ArgumentCaptor.forClass(CountryListener.class);
        verify(mockDetector).addCountryListener(captor.capture(), any(Looper.class));
        assertEquals(TEST_ISO, resultIso);
        return captor;
    }

    private void verifyNoInsertion() {
        try {
            Thread.sleep(TEST_TIMEOUT_MILLIS);
            verify(mContentProvider, never()).insert(any(String.class),
                    any(Uri.class), any(ContentValues.class));
        } catch (android.os.RemoteException e) {
            fail("Remote exception occurred during test execution");
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }


    private void verifyNoInsertionInUser(int userId) {
        try {
            Uri uri = ContentProvider.maybeAddUserId(CallLog.Calls.CONTENT_URI, userId);
            Thread.sleep(TEST_TIMEOUT_MILLIS);
            verify(getContentProviderForUser(userId), never())
                    .insert(any(String.class), eq(uri), any(ContentValues.class));
        } catch (android.os.RemoteException e) {
            fail("Remote exception occurred during test execution");
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }

    private ContentValues verifyInsertionWithCapture(int userId) {
        ArgumentCaptor<ContentValues> captor = ArgumentCaptor.forClass(ContentValues.class);
        try {
            Uri uri = ContentProvider.maybeAddUserId(CallLog.Calls.CONTENT_URI, userId);
            verify(getContentProviderForUser(userId), timeout(TEST_TIMEOUT_MILLIS).atLeastOnce())
                    .insert(any(String.class), eq(uri), captor.capture());
        } catch (android.os.RemoteException e) {
            fail("Remote exception occurred during test execution");
        }

        return captor.getValue();
    }

    private IContentProvider getContentProviderForUser(int userId) {
        return mContext.getContentResolver().acquireProvider(userId + "@call_log");
    }

    private Call makeFakeCall(int disconnectCauseCode, boolean isConference, boolean isIncoming,
            long creationTimeMillis, long ageMillis, Uri callHandle,
            PhoneAccountHandle phoneAccountHandle, int callVideoState,
            String postDialDigits, String viaNumber, UserHandle initiatingUser) {
        return makeFakeCall(disconnectCauseCode, isConference, isIncoming, creationTimeMillis,
                ageMillis, callHandle, phoneAccountHandle, callVideoState, postDialDigits,
                viaNumber, initiatingUser, Call.DATA_USAGE_NOT_SET);
    }

    private Call makeFakeCall(int disconnectCauseCode, boolean isConference, boolean isIncoming,
            long creationTimeMillis, long ageMillis, Uri callHandle,
            PhoneAccountHandle phoneAccountHandle, int callVideoState,
            String postDialDigits, String viaNumber, UserHandle initiatingUser,
            long callDataUsage) {
        Call fakeCall = mock(Call.class);
        when(fakeCall.getDisconnectCause()).thenReturn(
                new DisconnectCause(disconnectCauseCode));
        when(fakeCall.isConference()).thenReturn(isConference);
        when(fakeCall.isIncoming()).thenReturn(isIncoming);
        when(fakeCall.getCreationTimeMillis()).thenReturn(creationTimeMillis);
        when(fakeCall.getAgeMillis()).thenReturn(ageMillis);
        when(fakeCall.getOriginalHandle()).thenReturn(callHandle);
        when(fakeCall.getTargetPhoneAccount()).thenReturn(phoneAccountHandle);
        when(fakeCall.getVideoStateHistory()).thenReturn(callVideoState);
        when(fakeCall.getPostDialDigits()).thenReturn(postDialDigits);
        when(fakeCall.getViaNumber()).thenReturn(viaNumber);
        when(fakeCall.getInitiatingUser()).thenReturn(initiatingUser);
        when(fakeCall.getCallDataUsage()).thenReturn(callDataUsage);
        when(fakeCall.isEmergencyCall()).thenReturn(
                phoneAccountHandle.equals(EMERGENCY_ACCT_HANDLE));
        return fakeCall;
    }

    private PhoneAccount makeFakePhoneAccount(PhoneAccountHandle phoneAccountHandle,
            int capabilities) {
        return PhoneAccount.builder(phoneAccountHandle, "testing")
                .setCapabilities(capabilities).build();
    }
}
