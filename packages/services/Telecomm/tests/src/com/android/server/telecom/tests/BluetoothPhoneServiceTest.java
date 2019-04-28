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

import android.bluetooth.BluetoothAdapter;
import android.content.ComponentName;
import android.content.Intent;
import android.graphics.drawable.Icon;
import android.net.Uri;
import android.os.Binder;
import android.os.Debug;
import android.telecom.Connection;
import android.telecom.GatewayInfo;
import android.telecom.PhoneAccount;
import android.telecom.PhoneAccountHandle;
import android.telecom.TelecomManager;
import android.telephony.PhoneNumberUtils;
import android.telephony.TelephonyManager;
import android.test.suitebuilder.annotation.MediumTest;
import android.test.suitebuilder.annotation.SmallTest;

import com.android.server.telecom.BluetoothAdapterProxy;
import com.android.server.telecom.BluetoothHeadsetProxy;
import com.android.server.telecom.BluetoothPhoneServiceImpl;
import com.android.server.telecom.Call;
import com.android.server.telecom.CallState;
import com.android.server.telecom.CallsManager;
import com.android.server.telecom.PhoneAccountRegistrar;
import com.android.server.telecom.TelecomSystem;

import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.ArrayList;
import java.util.LinkedList;

import static org.mockito.ArgumentMatchers.nullable;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.anyBoolean;
import static org.mockito.Matchers.anyChar;
import static org.mockito.Matchers.anyInt;
import static org.mockito.Matchers.anyString;
import static org.mockito.Matchers.eq;
import static org.mockito.Matchers.isNull;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.verify;

public class BluetoothPhoneServiceTest extends TelecomTestCase {

    private static final int TEST_DTMF_TONE = 0;
    private static final String TEST_ACCOUNT_ADDRESS = "//foo.com/";
    private static final int TEST_ACCOUNT_INDEX = 0;

    // match up with BluetoothPhoneServiceImpl
    private static final int CALL_STATE_ACTIVE = 0;
    private static final int CALL_STATE_HELD = 1;
    private static final int CALL_STATE_DIALING = 2;
    private static final int CALL_STATE_ALERTING = 3;
    private static final int CALL_STATE_INCOMING = 4;
    private static final int CALL_STATE_WAITING = 5;
    private static final int CALL_STATE_IDLE = 6;
    // Terminate all held or set UDUB("busy") to a waiting call
    private static final int CHLD_TYPE_RELEASEHELD = 0;
    // Terminate all active calls and accepts a waiting/held call
    private static final int CHLD_TYPE_RELEASEACTIVE_ACCEPTHELD = 1;
    // Hold all active calls and accepts a waiting/held call
    private static final int CHLD_TYPE_HOLDACTIVE_ACCEPTHELD = 2;
    // Add all held calls to a conference
    private static final int CHLD_TYPE_ADDHELDTOCONF = 3;

    private BluetoothPhoneServiceImpl mBluetoothPhoneService;
    private final TelecomSystem.SyncRoot mLock = new TelecomSystem.SyncRoot() {
    };

    @Mock CallsManager mMockCallsManager;
    @Mock PhoneAccountRegistrar mMockPhoneAccountRegistrar;
    @Mock BluetoothHeadsetProxy mMockBluetoothHeadset;

    @Override
    public void setUp() throws Exception {
        super.setUp();
        MockitoAnnotations.initMocks(this);
        mContext = mComponentContextFixture.getTestDouble().getApplicationContext();

        // Ensure initialization does not actually try to access any of the CallsManager fields.
        // This also works to return null if it is not overwritten later in the test.
        doNothing().when(mMockCallsManager).addListener(any(
                CallsManager.CallsManagerListener.class));
        doReturn(null).when(mMockCallsManager).getActiveCall();
        doReturn(null).when(mMockCallsManager).getRingingCall();
        doReturn(null).when(mMockCallsManager).getHeldCall();
        doReturn(null).when(mMockCallsManager).getOutgoingCall();
        doReturn(0).when(mMockCallsManager).getNumHeldCalls();
        mBluetoothPhoneService = new BluetoothPhoneServiceImpl(mContext, mLock, mMockCallsManager,
                mock(BluetoothAdapterProxy.class), mMockPhoneAccountRegistrar);

        // Bring in test Bluetooth Headset
        mBluetoothPhoneService.setBluetoothHeadset(mMockBluetoothHeadset);
    }

    @Override
    public void tearDown() throws Exception {

        mBluetoothPhoneService = null;
        super.tearDown();
    }

    @SmallTest
    public void testHeadsetAnswerCall() throws Exception {
        Call mockCall = createRingingCall();

        boolean callAnswered = mBluetoothPhoneService.mBinder.answerCall();

        verify(mMockCallsManager).answerCall(eq(mockCall), any(int.class));
        assertEquals(callAnswered, true);
    }

    @SmallTest
    public void testHeadsetAnswerCallNull() throws Exception {
        when(mMockCallsManager.getRingingCall()).thenReturn(null);

        boolean callAnswered = mBluetoothPhoneService.mBinder.answerCall();

        verify(mMockCallsManager,never()).answerCall(any(Call.class), any(int.class));
        assertEquals(callAnswered, false);
    }

    @SmallTest
    public void testHeadsetHangupCall() throws Exception {
        Call mockCall = createForegroundCall();

        boolean callHungup = mBluetoothPhoneService.mBinder.hangupCall();

        verify(mMockCallsManager).disconnectCall(eq(mockCall));
        assertEquals(callHungup, true);
    }

    @SmallTest
    public void testHeadsetHangupCallNull() throws Exception {
        when(mMockCallsManager.getForegroundCall()).thenReturn(null);

        boolean callHungup = mBluetoothPhoneService.mBinder.hangupCall();

        verify(mMockCallsManager,never()).disconnectCall(any(Call.class));
        assertEquals(callHungup, false);
    }

    @SmallTest
    public void testHeadsetSendDTMF() throws Exception {
        Call mockCall = createForegroundCall();

        boolean sentDtmf = mBluetoothPhoneService.mBinder.sendDtmf(TEST_DTMF_TONE);

        verify(mMockCallsManager).playDtmfTone(eq(mockCall), eq((char) TEST_DTMF_TONE));
        verify(mMockCallsManager).stopDtmfTone(eq(mockCall));
        assertEquals(sentDtmf, true);
    }

    @SmallTest
    public void testHeadsetSendDTMFNull() throws Exception {
        when(mMockCallsManager.getForegroundCall()).thenReturn(null);

        boolean sentDtmf = mBluetoothPhoneService.mBinder.sendDtmf(TEST_DTMF_TONE);

        verify(mMockCallsManager,never()).playDtmfTone(any(Call.class), anyChar());
        verify(mMockCallsManager,never()).stopDtmfTone(any(Call.class));
        assertEquals(sentDtmf, false);
    }

    @SmallTest
    public void testGetNetworkOperator() throws Exception {
        Call mockCall = createForegroundCall();
        PhoneAccount fakePhoneAccount = makeQuickAccount("id0", TEST_ACCOUNT_INDEX);
        when(mMockPhoneAccountRegistrar.getPhoneAccountOfCurrentUser(
                nullable(PhoneAccountHandle.class))).thenReturn(fakePhoneAccount);

        String networkOperator = mBluetoothPhoneService.mBinder.getNetworkOperator();

        assertEquals(networkOperator, "label0");
    }

    @SmallTest
    public void testGetNetworkOperatorNoPhoneAccount() throws Exception {
        when(mMockCallsManager.getForegroundCall()).thenReturn(null);

        String networkOperator = mBluetoothPhoneService.mBinder.getNetworkOperator();

        assertEquals(networkOperator, "label1");
    }

    @SmallTest
    public void testGetSubscriberNumber() throws Exception {
        Call mockCall = createForegroundCall();
        PhoneAccount fakePhoneAccount = makeQuickAccount("id0", TEST_ACCOUNT_INDEX);
        when(mMockPhoneAccountRegistrar.getPhoneAccountOfCurrentUser(
                nullable(PhoneAccountHandle.class))).thenReturn(fakePhoneAccount);

        String subscriberNumber = mBluetoothPhoneService.mBinder.getSubscriberNumber();

        assertEquals(subscriberNumber, TEST_ACCOUNT_ADDRESS + TEST_ACCOUNT_INDEX);
    }

    @SmallTest
    public void testGetSubscriberNumberFallbackToTelephony() throws Exception {
        Call mockCall = createForegroundCall();
        String fakeNumber = "8675309";
        when(mMockPhoneAccountRegistrar.getPhoneAccountOfCurrentUser(
                nullable(PhoneAccountHandle.class))).thenReturn(null);
        when(mMockPhoneAccountRegistrar.getPhoneAccountUnchecked(
                nullable(PhoneAccountHandle.class))).thenReturn(null);
        when(TelephonyManager.from(mContext).getLine1Number()).thenReturn(fakeNumber);

        String subscriberNumber = mBluetoothPhoneService.mBinder.getSubscriberNumber();

        assertEquals(subscriberNumber, fakeNumber);
    }

    @MediumTest
    public void testListCurrentCallsOneCall() throws Exception {
        ArrayList<Call> calls = new ArrayList<>();
        Call activeCall = createActiveCall();
        when(activeCall.getState()).thenReturn(CallState.ACTIVE);
        calls.add(activeCall);
        when(activeCall.isConference()).thenReturn(false);
        when(activeCall.getHandle()).thenReturn(Uri.parse("tel:555-000"));
        when(mMockCallsManager.getCalls()).thenReturn(calls);

        mBluetoothPhoneService.mBinder.listCurrentCalls();

        verify(mMockBluetoothHeadset).clccResponse(eq(1), eq(0), eq(0), eq(0), eq(false),
                eq("555000"), eq(PhoneNumberUtils.TOA_Unknown));
        verify(mMockBluetoothHeadset).clccResponse(0, 0, 0, 0, false, null, 0);
    }

    @MediumTest
    public void testConferenceInProgressCDMA() throws Exception {
        // If two calls are being conferenced and updateHeadsetWithCallState runs while this is
        // still occuring, it will look like there is an active and held call still while we are
        // transitioning into a conference.
        // Call has been put into a CDMA "conference" with one call on hold.
        ArrayList<Call> calls = new ArrayList<>();
        Call parentCall = createActiveCall();
        final Call confCall1 = mock(Call.class);
        final Call confCall2 = createHeldCall();
        calls.add(parentCall);
        calls.add(confCall1);
        calls.add(confCall2);
        when(mMockCallsManager.getCalls()).thenReturn(calls);
        when(confCall1.getState()).thenReturn(CallState.ACTIVE);
        when(confCall2.getState()).thenReturn(CallState.ACTIVE);
        when(confCall1.isIncoming()).thenReturn(false);
        when(confCall2.isIncoming()).thenReturn(true);
        when(confCall1.getGatewayInfo()).thenReturn(new GatewayInfo(null, null,
                Uri.parse("tel:555-0000")));
        when(confCall2.getGatewayInfo()).thenReturn(new GatewayInfo(null, null,
                Uri.parse("tel:555-0001")));
        addCallCapability(parentCall, Connection.CAPABILITY_MERGE_CONFERENCE);
        addCallCapability(parentCall, Connection.CAPABILITY_SWAP_CONFERENCE);
        removeCallCapability(parentCall, Connection.CAPABILITY_CONFERENCE_HAS_NO_CHILDREN);
        when(parentCall.getConferenceLevelActiveCall()).thenReturn(confCall1);
        when(parentCall.isConference()).thenReturn(true);
        when(parentCall.getChildCalls()).thenReturn(new LinkedList<Call>() {{
            add(confCall1);
            add(confCall2);
        }});
        //Add links from child calls to parent
        when(confCall1.getParentCall()).thenReturn(parentCall);
        when(confCall2.getParentCall()).thenReturn(parentCall);

        mBluetoothPhoneService.mBinder.queryPhoneState();
        verify(mMockBluetoothHeadset).phoneStateChanged(eq(1), eq(1), eq(CALL_STATE_IDLE),
                eq(""), eq(128));
        when(parentCall.wasConferencePreviouslyMerged()).thenReturn(true);
        mBluetoothPhoneService.mCallsManagerListener.onIsConferencedChanged(parentCall);
        verify(mMockBluetoothHeadset).phoneStateChanged(eq(1), eq(0), eq(CALL_STATE_IDLE),
                eq(""), eq(128));
        when(mMockCallsManager.getHeldCall()).thenReturn(null);
        // Spurious call to onIsConferencedChanged.
        mBluetoothPhoneService.mCallsManagerListener.onIsConferencedChanged(parentCall);
        // Make sure the call has only occurred collectively 2 times (not on the third)
        verify(mMockBluetoothHeadset, times(2)).phoneStateChanged(any(int.class),
                any(int.class), any(int.class), nullable(String.class), any(int.class));
    }

    @MediumTest
    public void testListCurrentCallsCdmaHold() throws Exception {
        // Call has been put into a CDMA "conference" with one call on hold.
        ArrayList<Call> calls = new ArrayList<>();
        Call parentCall = createActiveCall();
        final Call foregroundCall = mock(Call.class);
        final Call heldCall = createHeldCall();
        calls.add(parentCall);
        calls.add(foregroundCall);
        calls.add(heldCall);
        when(mMockCallsManager.getCalls()).thenReturn(calls);
        when(foregroundCall.getState()).thenReturn(CallState.ACTIVE);
        when(heldCall.getState()).thenReturn(CallState.ACTIVE);
        when(foregroundCall.isIncoming()).thenReturn(false);
        when(heldCall.isIncoming()).thenReturn(true);
        when(foregroundCall.getGatewayInfo()).thenReturn(new GatewayInfo(null, null,
                Uri.parse("tel:555-0000")));
        when(heldCall.getGatewayInfo()).thenReturn(new GatewayInfo(null, null,
                Uri.parse("tel:555-0001")));
        addCallCapability(parentCall, Connection.CAPABILITY_MERGE_CONFERENCE);
        addCallCapability(parentCall, Connection.CAPABILITY_SWAP_CONFERENCE);
        removeCallCapability(parentCall, Connection.CAPABILITY_CONFERENCE_HAS_NO_CHILDREN);
        when(parentCall.getConferenceLevelActiveCall()).thenReturn(foregroundCall);
        when(parentCall.isConference()).thenReturn(true);
        when(parentCall.getChildCalls()).thenReturn(new LinkedList<Call>() {{
            add(foregroundCall);
            add(heldCall);
        }});
        //Add links from child calls to parent
        when(foregroundCall.getParentCall()).thenReturn(parentCall);
        when(heldCall.getParentCall()).thenReturn(parentCall);

        mBluetoothPhoneService.mBinder.listCurrentCalls();

        verify(mMockBluetoothHeadset).clccResponse(eq(1), eq(0), eq(CALL_STATE_ACTIVE), eq(0),
                eq(false), eq("5550000"), eq(PhoneNumberUtils.TOA_Unknown));
        verify(mMockBluetoothHeadset).clccResponse(eq(2), eq(1), eq(CALL_STATE_HELD), eq(0),
                eq(false), eq("5550001"), eq(PhoneNumberUtils.TOA_Unknown));
        verify(mMockBluetoothHeadset).clccResponse(0, 0, 0, 0, false, null, 0);
    }

    @MediumTest
    public void testListCurrentCallsCdmaConference() throws Exception {
        // Call is in a true CDMA conference
        ArrayList<Call> calls = new ArrayList<>();
        Call parentCall = createActiveCall();
        final Call confCall1 = mock(Call.class);
        final Call confCall2 = createHeldCall();
        calls.add(parentCall);
        calls.add(confCall1);
        calls.add(confCall2);
        when(mMockCallsManager.getCalls()).thenReturn(calls);
        when(confCall1.getState()).thenReturn(CallState.ACTIVE);
        when(confCall2.getState()).thenReturn(CallState.ACTIVE);
        when(confCall1.isIncoming()).thenReturn(false);
        when(confCall2.isIncoming()).thenReturn(true);
        when(confCall1.getGatewayInfo()).thenReturn(new GatewayInfo(null, null,
                Uri.parse("tel:555-0000")));
        when(confCall2.getGatewayInfo()).thenReturn(new GatewayInfo(null, null,
                Uri.parse("tel:555-0001")));
        removeCallCapability(parentCall, Connection.CAPABILITY_MERGE_CONFERENCE);
        removeCallCapability(parentCall, Connection.CAPABILITY_CONFERENCE_HAS_NO_CHILDREN);
        when(parentCall.wasConferencePreviouslyMerged()).thenReturn(true);
        when(parentCall.getConferenceLevelActiveCall()).thenReturn(confCall1);
        when(parentCall.isConference()).thenReturn(true);
        when(parentCall.getChildCalls()).thenReturn(new LinkedList<Call>() {{
            add(confCall1);
            add(confCall2);
        }});
        //Add links from child calls to parent
        when(confCall1.getParentCall()).thenReturn(parentCall);
        when(confCall2.getParentCall()).thenReturn(parentCall);

        mBluetoothPhoneService.mBinder.listCurrentCalls();

        verify(mMockBluetoothHeadset).clccResponse(eq(1), eq(0), eq(CALL_STATE_ACTIVE), eq(0),
                eq(true), eq("5550000"), eq(PhoneNumberUtils.TOA_Unknown));
        verify(mMockBluetoothHeadset).clccResponse(eq(2), eq(1), eq(CALL_STATE_ACTIVE), eq(0),
                eq(true), eq("5550001"), eq(PhoneNumberUtils.TOA_Unknown));
        verify(mMockBluetoothHeadset).clccResponse(0, 0, 0, 0, false, null, 0);
    }

    @MediumTest
    public void testWaitingCallClccResponse() throws Exception {
        ArrayList<Call> calls = new ArrayList<>();
        when(mMockCallsManager.getCalls()).thenReturn(calls);
        // This test does not define a value for getForegroundCall(), so this ringing call will
        // be treated as if it is a waiting call when listCurrentCalls() is invoked.
        Call waitingCall = createRingingCall();
        calls.add(waitingCall);
        when(waitingCall.isIncoming()).thenReturn(true);
        when(waitingCall.getGatewayInfo()).thenReturn(new GatewayInfo(null, null,
                Uri.parse("tel:555-0000")));
        when(waitingCall.getState()).thenReturn(CallState.RINGING);
        when(waitingCall.isConference()).thenReturn(false);

        mBluetoothPhoneService.mBinder.listCurrentCalls();
        verify(mMockBluetoothHeadset).clccResponse(1, 1, CALL_STATE_WAITING, 0, false,
                "5550000", PhoneNumberUtils.TOA_Unknown);
        verify(mMockBluetoothHeadset).clccResponse(0, 0, 0, 0, false, null, 0);
        verify(mMockBluetoothHeadset, times(2)).clccResponse(anyInt(),
                anyInt(), anyInt(), anyInt(), anyBoolean(), nullable(String.class), anyInt());
    }

    @MediumTest
    public void testNewCallClccResponse() throws Exception {
        ArrayList<Call> calls = new ArrayList<>();
        when(mMockCallsManager.getCalls()).thenReturn(calls);
        Call newCall = createForegroundCall();
        calls.add(newCall);
        when(newCall.getState()).thenReturn(CallState.NEW);
        when(newCall.isConference()).thenReturn(false);

        mBluetoothPhoneService.mBinder.listCurrentCalls();
        verify(mMockBluetoothHeadset).clccResponse(0, 0, 0, 0, false, null, 0);
        verify(mMockBluetoothHeadset, times(1)).clccResponse(anyInt(),
                anyInt(), anyInt(), anyInt(), anyBoolean(), nullable(String.class), anyInt());
    }

    @MediumTest
    public void testRingingCallClccResponse() throws Exception {
        ArrayList<Call> calls = new ArrayList<>();
        when(mMockCallsManager.getCalls()).thenReturn(calls);
        Call ringingCall = createForegroundCall();
        calls.add(ringingCall);
        when(ringingCall.getState()).thenReturn(CallState.RINGING);
        when(ringingCall.isIncoming()).thenReturn(true);
        when(ringingCall.isConference()).thenReturn(false);
        when(ringingCall.getGatewayInfo()).thenReturn(new GatewayInfo(null, null,
                Uri.parse("tel:555-0000")));

        mBluetoothPhoneService.mBinder.listCurrentCalls();
        verify(mMockBluetoothHeadset).clccResponse(1, 1, CALL_STATE_INCOMING, 0, false,
                "5550000", PhoneNumberUtils.TOA_Unknown);
        verify(mMockBluetoothHeadset).clccResponse(0, 0, 0, 0, false, null, 0);
        verify(mMockBluetoothHeadset, times(2)).clccResponse(anyInt(),
                anyInt(), anyInt(), anyInt(), anyBoolean(), nullable(String.class), anyInt());
    }

    @MediumTest
    public void testCallClccCache() throws Exception {
        ArrayList<Call> calls = new ArrayList<>();
        when(mMockCallsManager.getCalls()).thenReturn(calls);
        Call ringingCall = createForegroundCall();
        calls.add(ringingCall);
        when(ringingCall.getState()).thenReturn(CallState.RINGING);
        when(ringingCall.isIncoming()).thenReturn(true);
        when(ringingCall.isConference()).thenReturn(false);
        when(ringingCall.getGatewayInfo()).thenReturn(new GatewayInfo(null, null,
                Uri.parse("tel:5550000")));

        mBluetoothPhoneService.mBinder.listCurrentCalls();
        verify(mMockBluetoothHeadset).clccResponse(1, 1, CALL_STATE_INCOMING, 0, false,
                "5550000", PhoneNumberUtils.TOA_Unknown);

        // Test Caching of old call indicies in clcc
        when(ringingCall.getState()).thenReturn(CallState.ACTIVE);
        Call newHoldingCall = createHeldCall();
        calls.add(0, newHoldingCall);
        when(newHoldingCall.getState()).thenReturn(CallState.ON_HOLD);
        when(newHoldingCall.isIncoming()).thenReturn(true);
        when(newHoldingCall.isConference()).thenReturn(false);
        when(newHoldingCall.getGatewayInfo()).thenReturn(new GatewayInfo(null, null,
                Uri.parse("tel:555-0001")));

        mBluetoothPhoneService.mBinder.listCurrentCalls();
        verify(mMockBluetoothHeadset).clccResponse(1, 1, CALL_STATE_ACTIVE, 0, false,
                "5550000", PhoneNumberUtils.TOA_Unknown);
        verify(mMockBluetoothHeadset).clccResponse(2, 1, CALL_STATE_HELD, 0, false,
                "5550001", PhoneNumberUtils.TOA_Unknown);
        verify(mMockBluetoothHeadset, times(2)).clccResponse(0, 0, 0, 0, false, null, 0);
    }

    @MediumTest
    public void testAlertingCallClccResponse() throws Exception {
        ArrayList<Call> calls = new ArrayList<>();
        when(mMockCallsManager.getCalls()).thenReturn(calls);
        Call dialingCall = createForegroundCall();
        calls.add(dialingCall);
        when(dialingCall.getState()).thenReturn(CallState.DIALING);
        when(dialingCall.isIncoming()).thenReturn(false);
        when(dialingCall.isConference()).thenReturn(false);
        when(dialingCall.getGatewayInfo()).thenReturn(new GatewayInfo(null, null,
                Uri.parse("tel:555-0000")));

        mBluetoothPhoneService.mBinder.listCurrentCalls();
        verify(mMockBluetoothHeadset).clccResponse(1, 0, CALL_STATE_ALERTING, 0, false,
                "5550000", PhoneNumberUtils.TOA_Unknown);
        verify(mMockBluetoothHeadset).clccResponse(0, 0, 0, 0, false, null, 0);
        verify(mMockBluetoothHeadset, times(2)).clccResponse(anyInt(),
                anyInt(), anyInt(), anyInt(), anyBoolean(), nullable(String.class), anyInt());
    }

    @MediumTest
    public void testHoldingCallClccResponse() throws Exception {
        ArrayList<Call> calls = new ArrayList<>();
        when(mMockCallsManager.getCalls()).thenReturn(calls);
        Call dialingCall = createForegroundCall();
        calls.add(dialingCall);
        when(dialingCall.getState()).thenReturn(CallState.DIALING);
        when(dialingCall.isIncoming()).thenReturn(false);
        when(dialingCall.isConference()).thenReturn(false);
        when(dialingCall.getGatewayInfo()).thenReturn(new GatewayInfo(null, null,
                Uri.parse("tel:555-0000")));
        Call holdingCall = createHeldCall();
        calls.add(holdingCall);
        when(holdingCall.getState()).thenReturn(CallState.ON_HOLD);
        when(holdingCall.isIncoming()).thenReturn(true);
        when(holdingCall.isConference()).thenReturn(false);
        when(holdingCall.getGatewayInfo()).thenReturn(new GatewayInfo(null, null,
                Uri.parse("tel:555-0001")));

        mBluetoothPhoneService.mBinder.listCurrentCalls();
        verify(mMockBluetoothHeadset).clccResponse(1, 0, CALL_STATE_ALERTING, 0, false,
                "5550000", PhoneNumberUtils.TOA_Unknown);
        verify(mMockBluetoothHeadset).clccResponse(2, 1, CALL_STATE_HELD, 0, false,
                "5550001", PhoneNumberUtils.TOA_Unknown);
        verify(mMockBluetoothHeadset).clccResponse(0, 0, 0, 0, false, null, 0);
        verify(mMockBluetoothHeadset, times(3)).clccResponse(anyInt(),
                anyInt(), anyInt(), anyInt(), anyBoolean(), nullable(String.class), anyInt());
    }

    @MediumTest
    public void testListCurrentCallsImsConference() throws Exception {
        ArrayList<Call> calls = new ArrayList<>();
        Call parentCall = createActiveCall();
        calls.add(parentCall);
        addCallCapability(parentCall, Connection.CAPABILITY_CONFERENCE_HAS_NO_CHILDREN);
        when(parentCall.isConference()).thenReturn(true);
        when(parentCall.getState()).thenReturn(CallState.ACTIVE);
        when(parentCall.isIncoming()).thenReturn(true);
        when(mMockCallsManager.getCalls()).thenReturn(calls);

        mBluetoothPhoneService.mBinder.listCurrentCalls();

        verify(mMockBluetoothHeadset).clccResponse(eq(1), eq(1), eq(CALL_STATE_ACTIVE), eq(0),
                eq(true), (String) isNull(), eq(-1));
        verify(mMockBluetoothHeadset).clccResponse(0, 0, 0, 0, false, null, 0);
    }

    @MediumTest
    public void testListCurrentCallsHeldImsCepConference() throws Exception {
        ArrayList<Call> calls = new ArrayList<>();
        Call parentCall = createHeldCall();
        Call childCall1 = createActiveCall();
        Call childCall2 = createActiveCall();
        calls.add(parentCall);
        calls.add(childCall1);
        calls.add(childCall2);
        addCallCapability(parentCall, Connection.CAPABILITY_MANAGE_CONFERENCE);
        when(childCall1.getParentCall()).thenReturn(parentCall);
        when(childCall2.getParentCall()).thenReturn(parentCall);

        when(parentCall.isConference()).thenReturn(true);
        when(parentCall.getState()).thenReturn(CallState.ON_HOLD);
        when(childCall1.getState()).thenReturn(CallState.ACTIVE);
        when(childCall2.getState()).thenReturn(CallState.ACTIVE);

        when(parentCall.isIncoming()).thenReturn(true);
        when(mMockCallsManager.getCalls()).thenReturn(calls);

        mBluetoothPhoneService.mBinder.listCurrentCalls();

        verify(mMockBluetoothHeadset).clccResponse(eq(1), eq(0), eq(CALL_STATE_HELD), eq(0),
                eq(true), (String) isNull(), eq(-1));
        verify(mMockBluetoothHeadset).clccResponse(eq(2), eq(0), eq(CALL_STATE_HELD), eq(0),
                eq(true), (String) isNull(), eq(-1));
        verify(mMockBluetoothHeadset).clccResponse(0, 0, 0, 0, false, null, 0);
    }

    @MediumTest
    public void testQueryPhoneState() throws Exception {
        Call ringingCall = createRingingCall();
        when(ringingCall.getHandle()).thenReturn(Uri.parse("tel:5550000"));

        mBluetoothPhoneService.mBinder.queryPhoneState();

        verify(mMockBluetoothHeadset).phoneStateChanged(eq(0), eq(0), eq(CALL_STATE_INCOMING),
                eq("5550000"), eq(PhoneNumberUtils.TOA_Unknown));
    }

    @MediumTest
    public void testCDMAConferenceQueryState() throws Exception {
        Call parentConfCall = createActiveCall();
        final Call confCall1 = mock(Call.class);
        final Call confCall2 = mock(Call.class);
        when(parentConfCall.getHandle()).thenReturn(Uri.parse("tel:555-0000"));
        addCallCapability(parentConfCall, Connection.CAPABILITY_SWAP_CONFERENCE);
        removeCallCapability(parentConfCall, Connection.CAPABILITY_CONFERENCE_HAS_NO_CHILDREN);
        when(parentConfCall.wasConferencePreviouslyMerged()).thenReturn(true);
        when(parentConfCall.isConference()).thenReturn(true);
        when(parentConfCall.getChildCalls()).thenReturn(new LinkedList<Call>() {{
            add(confCall1);
            add(confCall2);
        }});

        mBluetoothPhoneService.mBinder.queryPhoneState();
        verify(mMockBluetoothHeadset).phoneStateChanged(eq(1), eq(0), eq(CALL_STATE_IDLE),
                eq(""), eq(128));
    }

    @MediumTest
    public void testProcessChldTypeReleaseHeldRinging() throws Exception {
        Call ringingCall = createRingingCall();

        boolean didProcess = mBluetoothPhoneService.mBinder.processChld(CHLD_TYPE_RELEASEHELD);

        verify(mMockCallsManager).rejectCall(eq(ringingCall), eq(false), nullable(String.class));
        assertEquals(didProcess, true);
    }

    @MediumTest
    public void testProcessChldTypeReleaseHeldHold() throws Exception {
        Call onHoldCall = createHeldCall();

        boolean didProcess = mBluetoothPhoneService.mBinder.processChld(CHLD_TYPE_RELEASEHELD);

        verify(mMockCallsManager).disconnectCall(eq(onHoldCall));
        assertEquals(didProcess, true);
    }

    @MediumTest
    public void testProcessChldReleaseActiveRinging() throws Exception {
        Call activeCall = createActiveCall();
        Call ringingCall = createRingingCall();

        boolean didProcess = mBluetoothPhoneService.mBinder.processChld(
                CHLD_TYPE_RELEASEACTIVE_ACCEPTHELD);

        verify(mMockCallsManager).disconnectCall(eq(activeCall));
        verify(mMockCallsManager).answerCall(eq(ringingCall), any(int.class));
        assertEquals(didProcess, true);
    }

    @MediumTest
    public void testProcessChldReleaseActiveHold() throws Exception {
        Call activeCall = createActiveCall();
        Call heldCall = createHeldCall();

        boolean didProcess = mBluetoothPhoneService.mBinder.processChld(
                CHLD_TYPE_RELEASEACTIVE_ACCEPTHELD);

        verify(mMockCallsManager).disconnectCall(eq(activeCall));
        verify(mMockCallsManager).unholdCall(eq(heldCall));
        assertEquals(didProcess, true);
    }

    @MediumTest
    public void testProcessChldHoldActiveRinging() throws Exception {
        Call ringingCall = createRingingCall();

        boolean didProcess = mBluetoothPhoneService.mBinder.processChld(
                CHLD_TYPE_HOLDACTIVE_ACCEPTHELD);

        verify(mMockCallsManager).answerCall(eq(ringingCall), any(int.class));
        assertEquals(didProcess, true);
    }

    @MediumTest
    public void testProcessChldHoldActiveUnhold() throws Exception {
        Call heldCall = createHeldCall();

        boolean didProcess = mBluetoothPhoneService.mBinder.processChld(
                CHLD_TYPE_HOLDACTIVE_ACCEPTHELD);

        verify(mMockCallsManager).unholdCall(eq(heldCall));
        assertEquals(didProcess, true);
    }

    @MediumTest
    public void testProcessChldHoldActiveHold() throws Exception {
        Call activeCall = createActiveCall();
        addCallCapability(activeCall, Connection.CAPABILITY_HOLD);

        boolean didProcess = mBluetoothPhoneService.mBinder.processChld(
                CHLD_TYPE_HOLDACTIVE_ACCEPTHELD);

        verify(mMockCallsManager).holdCall(eq(activeCall));
        assertEquals(didProcess, true);
    }

    @MediumTest
    public void testProcessChldAddHeldToConfHolding() throws Exception {
        Call activeCall = createActiveCall();
        addCallCapability(activeCall, Connection.CAPABILITY_MERGE_CONFERENCE);

        boolean didProcess = mBluetoothPhoneService.mBinder.processChld(CHLD_TYPE_ADDHELDTOCONF);

        verify(activeCall).mergeConference();
        assertEquals(didProcess, true);
    }

    @MediumTest
    public void testProcessChldAddHeldToConf() throws Exception {
        Call activeCall = createActiveCall();
        removeCallCapability(activeCall, Connection.CAPABILITY_MERGE_CONFERENCE);
        Call conferenceableCall = mock(Call.class);
        ArrayList<Call> conferenceableCalls = new ArrayList<>();
        conferenceableCalls.add(conferenceableCall);
        when(activeCall.getConferenceableCalls()).thenReturn(conferenceableCalls);

        boolean didProcess = mBluetoothPhoneService.mBinder.processChld(CHLD_TYPE_ADDHELDTOCONF);

        verify(mMockCallsManager).conference(activeCall, conferenceableCall);
        assertEquals(didProcess, true);
    }

    @MediumTest
    public void testProcessChldHoldActiveSwapConference() throws Exception {
        // Create an active CDMA Call with a call on hold and simulate a swapConference().
        Call parentCall = createActiveCall();
        final Call foregroundCall = mock(Call.class);
        final Call heldCall = createHeldCall();
        addCallCapability(parentCall, Connection.CAPABILITY_SWAP_CONFERENCE);
        removeCallCapability(parentCall, Connection.CAPABILITY_CONFERENCE_HAS_NO_CHILDREN);
        when(parentCall.isConference()).thenReturn(true);
        when(parentCall.wasConferencePreviouslyMerged()).thenReturn(false);
        when(parentCall.getChildCalls()).thenReturn(new LinkedList<Call>() {{
            add(foregroundCall);
            add(heldCall);
        }});

        boolean didProcess = mBluetoothPhoneService.mBinder.processChld(
                CHLD_TYPE_HOLDACTIVE_ACCEPTHELD);

        verify(parentCall).swapConference();
        verify(mMockBluetoothHeadset).phoneStateChanged(eq(1), eq(1), eq(CALL_STATE_IDLE), eq(""),
                eq(128));
        assertEquals(didProcess, true);
    }

    // Testing the CallsManager Listener Functionality on Bluetooth
    @MediumTest
    public void testOnCallAddedRinging() throws Exception {
        Call ringingCall = createRingingCall();
        when(ringingCall.getHandle()).thenReturn(Uri.parse("tel:555000"));

        mBluetoothPhoneService.mCallsManagerListener.onCallAdded(ringingCall);

        verify(mMockBluetoothHeadset).phoneStateChanged(eq(0), eq(0), eq(CALL_STATE_INCOMING),
                eq("555000"), eq(PhoneNumberUtils.TOA_Unknown));

    }

    @MediumTest
    public void testOnCallAddedCdmaActiveHold() throws Exception {
        // Call has been put into a CDMA "conference" with one call on hold.
        Call parentCall = createActiveCall();
        final Call foregroundCall = mock(Call.class);
        final Call heldCall = createHeldCall();
        addCallCapability(parentCall, Connection.CAPABILITY_MERGE_CONFERENCE);
        removeCallCapability(parentCall, Connection.CAPABILITY_CONFERENCE_HAS_NO_CHILDREN);
        when(parentCall.isConference()).thenReturn(true);
        when(parentCall.getChildCalls()).thenReturn(new LinkedList<Call>() {{
            add(foregroundCall);
            add(heldCall);
        }});

        mBluetoothPhoneService.mCallsManagerListener.onCallAdded(parentCall);

        verify(mMockBluetoothHeadset).phoneStateChanged(eq(1), eq(1), eq(CALL_STATE_IDLE),
                eq(""), eq(128));

    }

    @MediumTest
    public void testOnCallRemoved() throws Exception {
        Call activeCall = createActiveCall();
        mBluetoothPhoneService.mCallsManagerListener.onCallAdded(activeCall);
        doReturn(null).when(mMockCallsManager).getActiveCall();
        mBluetoothPhoneService.mCallsManagerListener.onCallRemoved(activeCall);

        verify(mMockBluetoothHeadset).phoneStateChanged(eq(0), eq(0), eq(CALL_STATE_IDLE),
                eq(""), eq(128));
    }

    @MediumTest
    public void testOnCallStateChangedConnectingCall() throws Exception {
        Call activeCall = mock(Call.class);
        Call connectingCall = mock(Call.class);
        when(connectingCall.getState()).thenReturn(CallState.CONNECTING);
        ArrayList<Call> calls = new ArrayList<>();
        calls.add(connectingCall);
        calls.add(activeCall);
        when(mMockCallsManager.getCalls()).thenReturn(calls);

        mBluetoothPhoneService.mCallsManagerListener.onCallStateChanged(activeCall,
                CallState.ACTIVE, CallState.ON_HOLD);

        verify(mMockBluetoothHeadset, never()).phoneStateChanged(anyInt(), anyInt(), anyInt(),
                anyString(), anyInt());
    }

    @MediumTest
    public void testOnCallStateChangedDialing() throws Exception {
        Call activeCall = createActiveCall();

        mBluetoothPhoneService.mCallsManagerListener.onCallStateChanged(activeCall,
                CallState.CONNECTING, CallState.DIALING);

        verify(mMockBluetoothHeadset, never()).phoneStateChanged(anyInt(), anyInt(), anyInt(),
                anyString(), anyInt());
    }

    @MediumTest
    public void testOnCallStateChangedAlerting() throws Exception {
        Call outgoingCall = createOutgoingCall();

        mBluetoothPhoneService.mCallsManagerListener.onCallStateChanged(outgoingCall,
                CallState.NEW, CallState.DIALING);

        verify(mMockBluetoothHeadset).phoneStateChanged(0, 0, CALL_STATE_DIALING, "", 128);
        verify(mMockBluetoothHeadset).phoneStateChanged(0, 0, CALL_STATE_ALERTING, "", 128);
    }

    @MediumTest
    public void testOnCallStateChanged() throws Exception {
        Call ringingCall = createRingingCall();
        when(ringingCall.getHandle()).thenReturn(Uri.parse("tel:555-0000"));
        mBluetoothPhoneService.mCallsManagerListener.onCallAdded(ringingCall);

        verify(mMockBluetoothHeadset).phoneStateChanged(eq(0), eq(0), eq(CALL_STATE_INCOMING),
                eq("555-0000"), eq(PhoneNumberUtils.TOA_Unknown));

        //Switch to active
        doReturn(null).when(mMockCallsManager).getRingingCall();
        when(mMockCallsManager.getActiveCall()).thenReturn(ringingCall);

        mBluetoothPhoneService.mCallsManagerListener.onCallStateChanged(ringingCall,
                CallState.RINGING, CallState.ACTIVE);

        verify(mMockBluetoothHeadset).phoneStateChanged(eq(1), eq(0), eq(CALL_STATE_IDLE),
                eq(""), eq(128));
    }

    @MediumTest
    public void testOnCallStateChangedGSMSwap() throws Exception {
        Call heldCall = createHeldCall();
        when(heldCall.getHandle()).thenReturn(Uri.parse("tel:555-0000"));
        doReturn(2).when(mMockCallsManager).getNumHeldCalls();
        mBluetoothPhoneService.mCallsManagerListener.onCallStateChanged(heldCall,
                CallState.ACTIVE, CallState.ON_HOLD);

        verify(mMockBluetoothHeadset, never()).phoneStateChanged(eq(0), eq(2), eq(CALL_STATE_HELD),
                eq("5550000"), eq(PhoneNumberUtils.TOA_Unknown));
    }

    @MediumTest
    public void testOnIsConferencedChanged() throws Exception {
        // Start with two calls that are being merged into a CDMA conference call. The
        // onIsConferencedChanged method will be called multiple times during the call. Make sure
        // that the bluetooth phone state is updated properly.
        Call parentCall = createActiveCall();
        Call activeCall = mock(Call.class);
        Call heldCall = createHeldCall();
        when(activeCall.getParentCall()).thenReturn(parentCall);
        when(heldCall.getParentCall()).thenReturn(parentCall);
        ArrayList<Call> calls = new ArrayList<>();
        calls.add(activeCall);
        when(parentCall.getChildCalls()).thenReturn(calls);
        when(parentCall.isConference()).thenReturn(true);
        removeCallCapability(parentCall, Connection.CAPABILITY_CONFERENCE_HAS_NO_CHILDREN);
        addCallCapability(parentCall, Connection.CAPABILITY_SWAP_CONFERENCE);
        when(parentCall.wasConferencePreviouslyMerged()).thenReturn(false);

        // Be sure that onIsConferencedChanged rejects spurious changes during set up of
        // CDMA "conference"
        mBluetoothPhoneService.mCallsManagerListener.onIsConferencedChanged(activeCall);
        verify(mMockBluetoothHeadset, never()).phoneStateChanged(anyInt(), anyInt(), anyInt(),
                anyString(), anyInt());
        mBluetoothPhoneService.mCallsManagerListener.onIsConferencedChanged(heldCall);
        verify(mMockBluetoothHeadset, never()).phoneStateChanged(anyInt(), anyInt(), anyInt(),
                anyString(), anyInt());
        mBluetoothPhoneService.mCallsManagerListener.onIsConferencedChanged(parentCall);
        verify(mMockBluetoothHeadset, never()).phoneStateChanged(anyInt(), anyInt(), anyInt(),
                anyString(), anyInt());

        calls.add(heldCall);
        mBluetoothPhoneService.mCallsManagerListener.onIsConferencedChanged(parentCall);
        verify(mMockBluetoothHeadset).phoneStateChanged(eq(1), eq(1), eq(CALL_STATE_IDLE),
                eq(""), eq(128));
    }

    @MediumTest
    public void testBluetoothAdapterReceiver() throws Exception {
        Call ringingCall = createRingingCall();
        when(ringingCall.getHandle()).thenReturn(Uri.parse("tel:5550000"));

        Intent intent = new Intent();
        intent.putExtra(BluetoothAdapter.EXTRA_STATE, BluetoothAdapter.STATE_ON);
        mBluetoothPhoneService.mBluetoothAdapterReceiver.onReceive(mContext, intent);

        verify(mMockBluetoothHeadset).phoneStateChanged(eq(0), eq(0), eq(CALL_STATE_INCOMING),
                eq("5550000"), eq(PhoneNumberUtils.TOA_Unknown));
    }

    private void addCallCapability(Call call, int capability) {
        when(call.can(capability)).thenReturn(true);
    }

    private void removeCallCapability(Call call, int capability) {
        when(call.can(capability)).thenReturn(false);
    }

    private Call createActiveCall() {
        Call call = mock(Call.class);
        when(mMockCallsManager.getActiveCall()).thenReturn(call);
        return call;
    }

    private Call createRingingCall() {
        Call call = mock(Call.class);
        when(mMockCallsManager.getRingingCall()).thenReturn(call);
        return call;
    }

    private Call createHeldCall() {
        Call call = mock(Call.class);
        when(mMockCallsManager.getHeldCall()).thenReturn(call);
        return call;
    }

    private Call createOutgoingCall() {
        Call call = mock(Call.class);
        when(mMockCallsManager.getOutgoingCall()).thenReturn(call);
        return call;
    }

    private Call createForegroundCall() {
        Call call = mock(Call.class);
        when(mMockCallsManager.getForegroundCall()).thenReturn(call);
        return call;
    }

    private static ComponentName makeQuickConnectionServiceComponentName() {
        return new ComponentName("com.android.server.telecom.tests",
                "com.android.server.telecom.tests.MockConnectionService");
    }

    private static PhoneAccountHandle makeQuickAccountHandle(String id) {
        return new PhoneAccountHandle(makeQuickConnectionServiceComponentName(), id,
                Binder.getCallingUserHandle());
    }

    private PhoneAccount.Builder makeQuickAccountBuilder(String id, int idx) {
        return new PhoneAccount.Builder(makeQuickAccountHandle(id), "label" + idx);
    }

    private PhoneAccount makeQuickAccount(String id, int idx) {
        return makeQuickAccountBuilder(id, idx)
                .setAddress(Uri.parse(TEST_ACCOUNT_ADDRESS + idx))
                .setSubscriptionAddress(Uri.parse("tel:555-000" + idx))
                .setCapabilities(idx)
                .setIcon(Icon.createWithResource(
                        "com.android.server.telecom.tests", R.drawable.stat_sys_phone_call))
                .setShortDescription("desc" + idx)
                .setIsEnabled(true)
                .build();
    }
}
