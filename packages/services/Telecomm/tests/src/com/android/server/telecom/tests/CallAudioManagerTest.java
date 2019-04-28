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
 * limitations under the License
 */

package com.android.server.telecom.tests;

import android.media.ToneGenerator;
import android.telecom.DisconnectCause;
import android.test.suitebuilder.annotation.MediumTest;
import android.util.SparseArray;

import com.android.server.telecom.Call;
import com.android.server.telecom.CallAudioModeStateMachine;
import com.android.server.telecom.CallAudioRouteStateMachine;
import com.android.server.telecom.CallState;
import com.android.server.telecom.CallsManager;
import com.android.server.telecom.CallAudioManager;
import com.android.server.telecom.DtmfLocalTonePlayer;
import com.android.server.telecom.InCallTonePlayer;
import com.android.server.telecom.RingbackPlayer;
import com.android.server.telecom.Ringer;

import org.mockito.ArgumentCaptor;
import org.mockito.Mock;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.stream.Collectors;

import static org.mockito.Matchers.any;
import static org.mockito.Matchers.anyInt;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class CallAudioManagerTest extends TelecomTestCase {
    @Mock private CallAudioRouteStateMachine mCallAudioRouteStateMachine;
    @Mock private CallsManager mCallsManager;
    @Mock private CallAudioModeStateMachine mCallAudioModeStateMachine;
    @Mock private InCallTonePlayer.Factory mPlayerFactory;
    @Mock private Ringer mRinger;
    @Mock private RingbackPlayer mRingbackPlayer;
    @Mock private DtmfLocalTonePlayer mDtmfLocalTonePlayer;

    private CallAudioManager mCallAudioManager;

    @Override
    public void setUp() throws Exception {
        super.setUp();
        doAnswer((invocation) -> {
            InCallTonePlayer mockInCallTonePlayer = mock(InCallTonePlayer.class);
            doAnswer((invocation2) -> {
                mCallAudioManager.setIsTonePlaying(true);
                return null;
            }).when(mockInCallTonePlayer).startTone();
            return mockInCallTonePlayer;
        }).when(mPlayerFactory).createPlayer(anyInt());
        mCallAudioManager = new CallAudioManager(
                mCallAudioRouteStateMachine,
                mCallsManager,
                mCallAudioModeStateMachine,
                mPlayerFactory,
                mRinger,
                mRingbackPlayer,
                mDtmfLocalTonePlayer);
    }

    @MediumTest
    public void testUnmuteOfSecondIncomingCall() {
        // Start with a single incoming call.
        Call call = createIncomingCall();
        when(call.can(android.telecom.Call.Details.CAPABILITY_SPEED_UP_MT_AUDIO))
                .thenReturn(false);
        when(call.getId()).thenReturn("1");

        ArgumentCaptor<CallAudioModeStateMachine.MessageArgs> captor =
                ArgumentCaptor.forClass(CallAudioModeStateMachine.MessageArgs.class);
        // Answer the incoming call
        mCallAudioManager.onIncomingCallAnswered(call);
        when(call.getState()).thenReturn(CallState.ACTIVE);
        mCallAudioManager.onCallStateChanged(call, CallState.RINGING, CallState.ACTIVE);
        verify(mCallAudioModeStateMachine).sendMessageWithArgs(
                eq(CallAudioModeStateMachine.NO_MORE_RINGING_CALLS), captor.capture());
        CallAudioModeStateMachine.MessageArgs correctArgs =
                new CallAudioModeStateMachine.MessageArgs(
                        true, // hasActiveOrDialingCalls
                        false, // hasRingingCalls
                        false, // hasHoldingCalls
                        false, // isTonePlaying
                        false, // foregroundCallIsVoip
                        null // session
                );
        assertMessageArgEquality(correctArgs, captor.getValue());
        verify(mCallAudioModeStateMachine).sendMessageWithArgs(
                eq(CallAudioModeStateMachine.NEW_ACTIVE_OR_DIALING_CALL), captor.capture());
        assertMessageArgEquality(correctArgs, captor.getValue());

        // Mute the current ongoing call.
        mCallAudioManager.mute(true);

        // Create a second incoming call.
        Call call2 = mock(Call.class);
        when(call2.getState()).thenReturn(CallState.RINGING);
        when(call2.can(android.telecom.Call.Details.CAPABILITY_SPEED_UP_MT_AUDIO))
                .thenReturn(false);
        when(call2.getId()).thenReturn("2");
        mCallAudioManager.onCallAdded(call2);

        // Answer the incoming call
        mCallAudioManager.onIncomingCallAnswered(call);

        // Capture the calls to sendMessageWithSessionInfo; we want to look for mute on and off
        // messages and make sure that there was a mute on before the mute off.
        ArgumentCaptor<Integer> muteCaptor = ArgumentCaptor.forClass(Integer.class);
        verify(mCallAudioRouteStateMachine, atLeastOnce())
                .sendMessageWithSessionInfo(muteCaptor.capture());
        List<Integer> values = muteCaptor.getAllValues();
        values = values.stream()
                .filter(value -> value == CallAudioRouteStateMachine.MUTE_ON ||
                        value == CallAudioRouteStateMachine.MUTE_OFF)
                .collect(Collectors.toList());

        // Make sure we got a mute on and a mute off.
        assertTrue(values.contains(CallAudioRouteStateMachine.MUTE_ON));
        assertTrue(values.contains(CallAudioRouteStateMachine.MUTE_OFF));
        // And that the mute on happened before the off.
        assertTrue(values.indexOf(CallAudioRouteStateMachine.MUTE_ON) < values
                .lastIndexOf(CallAudioRouteStateMachine.MUTE_OFF));
    }

    @MediumTest
    public void testSingleIncomingCallFlowWithoutMTSpeedUp() {
        Call call = createIncomingCall();
        when(call.can(android.telecom.Call.Details.CAPABILITY_SPEED_UP_MT_AUDIO))
                .thenReturn(false);

        ArgumentCaptor<CallAudioModeStateMachine.MessageArgs> captor =
                ArgumentCaptor.forClass(CallAudioModeStateMachine.MessageArgs.class);
        // Answer the incoming call
        mCallAudioManager.onIncomingCallAnswered(call);
        when(call.getState()).thenReturn(CallState.ACTIVE);
        mCallAudioManager.onCallStateChanged(call, CallState.RINGING, CallState.ACTIVE);
        verify(mCallAudioModeStateMachine).sendMessageWithArgs(
                eq(CallAudioModeStateMachine.NO_MORE_RINGING_CALLS), captor.capture());
        CallAudioModeStateMachine.MessageArgs correctArgs =
                new CallAudioModeStateMachine.MessageArgs(
                        true, // hasActiveOrDialingCalls
                        false, // hasRingingCalls
                        false, // hasHoldingCalls
                        false, // isTonePlaying
                        false, // foregroundCallIsVoip
                        null // session
                );
        assertMessageArgEquality(correctArgs, captor.getValue());
        verify(mCallAudioModeStateMachine).sendMessageWithArgs(
                eq(CallAudioModeStateMachine.NEW_ACTIVE_OR_DIALING_CALL), captor.capture());
        assertMessageArgEquality(correctArgs, captor.getValue());

        disconnectCall(call);
        stopTone();

        mCallAudioManager.onCallRemoved(call);
        verifyProperCleanup();
    }

    @MediumTest
    public void testSingleIncomingCallFlowWithMTSpeedUp() {
        Call call = createIncomingCall();
        when(call.can(android.telecom.Call.Details.CAPABILITY_SPEED_UP_MT_AUDIO))
                .thenReturn(true);

        ArgumentCaptor<CallAudioModeStateMachine.MessageArgs> captor =
                ArgumentCaptor.forClass(CallAudioModeStateMachine.MessageArgs.class);
        // Answer the incoming call
        mCallAudioManager.onIncomingCallAnswered(call);
        verify(mCallAudioModeStateMachine).sendMessageWithArgs(
                eq(CallAudioModeStateMachine.MT_AUDIO_SPEEDUP_FOR_RINGING_CALL), captor.capture());
        CallAudioModeStateMachine.MessageArgs correctArgs =
                new CallAudioModeStateMachine.MessageArgs(
                        true, // hasActiveOrDialingCalls
                        false, // hasRingingCalls
                        false, // hasHoldingCalls
                        false, // isTonePlaying
                        false, // foregroundCallIsVoip
                        null // session
                );
        assertMessageArgEquality(correctArgs, captor.getValue());
        assertMessageArgEquality(correctArgs, captor.getValue());
        when(call.getState()).thenReturn(CallState.ACTIVE);
        mCallAudioManager.onCallStateChanged(call, CallState.RINGING, CallState.ACTIVE);

        disconnectCall(call);
        stopTone();

        mCallAudioManager.onCallRemoved(call);
        verifyProperCleanup();
    }

    @MediumTest
    public void testSingleOutgoingCall() {
        Call call = mock(Call.class);
        when(call.getState()).thenReturn(CallState.CONNECTING);

        mCallAudioManager.onCallAdded(call);
        assertEquals(call, mCallAudioManager.getForegroundCall());
        ArgumentCaptor<CallAudioModeStateMachine.MessageArgs> captor =
                ArgumentCaptor.forClass(CallAudioModeStateMachine.MessageArgs.class);
        verify(mCallAudioRouteStateMachine).sendMessageWithSessionInfo(
                CallAudioRouteStateMachine.UPDATE_SYSTEM_AUDIO_ROUTE);
        verify(mCallAudioModeStateMachine).sendMessageWithArgs(
                eq(CallAudioModeStateMachine.NEW_ACTIVE_OR_DIALING_CALL), captor.capture());
        CallAudioModeStateMachine.MessageArgs expectedArgs =
                new CallAudioModeStateMachine.MessageArgs(
                        true, // hasActiveOrDialingCalls
                        false, // hasRingingCalls
                        false, // hasHoldingCalls
                        false, // isTonePlaying
                        false, // foregroundCallIsVoip
                        null // session
                );
        assertMessageArgEquality(expectedArgs, captor.getValue());

        when(call.getState()).thenReturn(CallState.DIALING);
        mCallAudioManager.onCallStateChanged(call, CallState.CONNECTING, CallState.DIALING);
        verify(mCallAudioModeStateMachine, times(2)).sendMessageWithArgs(
                eq(CallAudioModeStateMachine.NEW_ACTIVE_OR_DIALING_CALL), captor.capture());
        assertMessageArgEquality(expectedArgs, captor.getValue());
        verify(mCallAudioModeStateMachine, times(2)).sendMessageWithArgs(
                anyInt(), any(CallAudioModeStateMachine.MessageArgs.class));


        when(call.getState()).thenReturn(CallState.ACTIVE);
        mCallAudioManager.onCallStateChanged(call, CallState.DIALING, CallState.ACTIVE);
        verify(mCallAudioModeStateMachine, times(3)).sendMessageWithArgs(
                eq(CallAudioModeStateMachine.NEW_ACTIVE_OR_DIALING_CALL), captor.capture());
        assertMessageArgEquality(expectedArgs, captor.getValue());
        verify(mCallAudioModeStateMachine, times(3)).sendMessageWithArgs(
                anyInt(), any(CallAudioModeStateMachine.MessageArgs.class));

        disconnectCall(call);
        stopTone();

        mCallAudioManager.onCallRemoved(call);
        verifyProperCleanup();
    }

    private Call createIncomingCall() {
        Call call = mock(Call.class);
        when(call.getState()).thenReturn(CallState.RINGING);

        mCallAudioManager.onCallAdded(call);
        assertEquals(call, mCallAudioManager.getForegroundCall());
        ArgumentCaptor<CallAudioModeStateMachine.MessageArgs> captor =
                ArgumentCaptor.forClass(CallAudioModeStateMachine.MessageArgs.class);
        verify(mCallAudioRouteStateMachine).sendMessageWithSessionInfo(
                CallAudioRouteStateMachine.UPDATE_SYSTEM_AUDIO_ROUTE);
        verify(mCallAudioModeStateMachine).sendMessageWithArgs(
                eq(CallAudioModeStateMachine.NEW_RINGING_CALL), captor.capture());
        assertMessageArgEquality(new CallAudioModeStateMachine.MessageArgs(
                false, // hasActiveOrDialingCalls
                true, // hasRingingCalls
                false, // hasHoldingCalls
                false, // isTonePlaying
                false, // foregroundCallIsVoip
                null // session
        ), captor.getValue());

        return call;
    }

    private void disconnectCall(Call call) {
        ArgumentCaptor<CallAudioModeStateMachine.MessageArgs> captor =
                ArgumentCaptor.forClass(CallAudioModeStateMachine.MessageArgs.class);
        CallAudioModeStateMachine.MessageArgs correctArgs;

        when(call.getState()).thenReturn(CallState.DISCONNECTED);
        when(call.getDisconnectCause()).thenReturn(new DisconnectCause(DisconnectCause.LOCAL,
                "", "", "", ToneGenerator.TONE_PROP_PROMPT));

        mCallAudioManager.onCallStateChanged(call, CallState.ACTIVE, CallState.DISCONNECTED);
        verify(mPlayerFactory).createPlayer(InCallTonePlayer.TONE_CALL_ENDED);
        correctArgs = new CallAudioModeStateMachine.MessageArgs(
                false, // hasActiveOrDialingCalls
                false, // hasRingingCalls
                false, // hasHoldingCalls
                true, // isTonePlaying
                false, // foregroundCallIsVoip
                null // session
        );
        verify(mCallAudioModeStateMachine).sendMessageWithArgs(
                eq(CallAudioModeStateMachine.NO_MORE_ACTIVE_OR_DIALING_CALLS), captor.capture());
        assertMessageArgEquality(correctArgs, captor.getValue());
        verify(mCallAudioModeStateMachine).sendMessageWithArgs(
                eq(CallAudioModeStateMachine.TONE_STARTED_PLAYING), captor.capture());
        assertMessageArgEquality(correctArgs, captor.getValue());
    }

    private void stopTone() {
        ArgumentCaptor<CallAudioModeStateMachine.MessageArgs> captor =
                ArgumentCaptor.forClass(CallAudioModeStateMachine.MessageArgs.class);
        mCallAudioManager.setIsTonePlaying(false);
        CallAudioModeStateMachine.MessageArgs correctArgs =
                new CallAudioModeStateMachine.MessageArgs(
                        false, // hasActiveOrDialingCalls
                        false, // hasRingingCalls
                        false, // hasHoldingCalls
                        false, // isTonePlaying
                        false, // foregroundCallIsVoip
                        null // session
                );
        verify(mCallAudioModeStateMachine).sendMessageWithArgs(
                eq(CallAudioModeStateMachine.TONE_STOPPED_PLAYING), captor.capture());
        assertMessageArgEquality(correctArgs, captor.getValue());
    }

    private void verifyProperCleanup() {
        assertEquals(0, mCallAudioManager.getTrackedCalls().size());
        SparseArray<LinkedHashSet<Call>> callStateToCalls = mCallAudioManager.getCallStateToCalls();
        for (int i = 0; i < callStateToCalls.size(); i++) {
            assertEquals(0, callStateToCalls.valueAt(i).size());
        }
    }

    private void assertMessageArgEquality(CallAudioModeStateMachine.MessageArgs expected,
            CallAudioModeStateMachine.MessageArgs actual) {
        assertEquals(expected.hasActiveOrDialingCalls, actual.hasActiveOrDialingCalls);
        assertEquals(expected.hasHoldingCalls, actual.hasHoldingCalls);
        assertEquals(expected.hasRingingCalls, actual.hasRingingCalls);
        assertEquals(expected.isTonePlaying, actual.isTonePlaying);
        assertEquals(expected.foregroundCallIsVoip, actual.foregroundCallIsVoip);
    }
}
