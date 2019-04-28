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

import android.media.AudioManager;
import android.test.suitebuilder.annotation.LargeTest;
import android.test.suitebuilder.annotation.SmallTest;

import com.android.server.telecom.CallAudioManager;
import com.android.server.telecom.CallAudioModeStateMachine;

import org.mockito.Mock;

import java.util.ArrayList;
import java.util.List;

import static org.mockito.Matchers.anyInt;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class CallAudioModeStateMachineTest extends StateMachineTestBase<CallAudioModeStateMachine> {
    private static class ModeTestParameters extends TestParameters {
        public String name;
        public int initialAudioState; // One of the explicit switch focus constants in CAMSM
        public int messageType; // Any of the commands from the state machine
        public CallAudioModeStateMachine.MessageArgs externalState;
        public String expectedFinalStateName;
        public int expectedFocus; // one of the FOCUS_* constants below
        public int expectedMode; // NO_CHANGE, or an AudioManager.MODE_* constant
        public int expectedRingingInteraction; // NO_CHANGE, ON, or OFF
        public int expectedCallWaitingInteraction; // NO_CHANGE, ON, or OFF

        public ModeTestParameters(String name, int initialAudioState, int messageType,
                CallAudioModeStateMachine.MessageArgs externalState, String
                expectedFinalStateName, int expectedFocus, int expectedMode, int
                expectedRingingInteraction, int expectedCallWaitingInteraction) {
            this.name = name;
            this.initialAudioState = initialAudioState;
            this.messageType = messageType;
            this.externalState = externalState;
            this.expectedFinalStateName = expectedFinalStateName;
            this.expectedFocus = expectedFocus;
            this.expectedMode = expectedMode;
            this.expectedRingingInteraction = expectedRingingInteraction;
            this.expectedCallWaitingInteraction = expectedCallWaitingInteraction;
        }

        @Override
        public String toString() {
            return "ModeTestParameters{" +
                    "name='" + name + '\'' +
                    ", initialAudioState=" + initialAudioState +
                    ", messageType=" + messageType +
                    ", externalState=" + externalState +
                    ", expectedFinalStateName='" + expectedFinalStateName + '\'' +
                    ", expectedFocus=" + expectedFocus +
                    ", expectedMode=" + expectedMode +
                    ", expectedRingingInteraction=" + expectedRingingInteraction +
                    ", expectedCallWaitingInteraction=" + expectedCallWaitingInteraction +
                    '}';
        }
    }

    private static final int FOCUS_NO_CHANGE = 0;
    private static final int FOCUS_RING = 1;
    private static final int FOCUS_VOICE = 2;
    private static final int FOCUS_OFF = 3;

    private static final int NO_CHANGE = -1;
    private static final int ON = 0;
    private static final int OFF = 1;

    @Mock private AudioManager mAudioManager;
    @Mock private CallAudioManager mCallAudioManager;

    @Override
    public void setUp() throws Exception {
        super.setUp();
        mContext = mComponentContextFixture.getTestDouble().getApplicationContext();
    }

    @LargeTest
    public void testTransitions() throws Throwable {
        List<ModeTestParameters> testCases = generateTestCases();
        parametrizedTestStateMachine(testCases);
    }

    @SmallTest
    public void testNoFocusWhenRingerSilenced() throws Throwable {
        CallAudioModeStateMachine sm = new CallAudioModeStateMachine(mAudioManager);
        sm.setCallAudioManager(mCallAudioManager);
        sm.sendMessage(CallAudioModeStateMachine.ABANDON_FOCUS_FOR_TESTING);
        waitForStateMachineActionCompletion(sm, CallAudioModeStateMachine.RUN_RUNNABLE);

        resetMocks();
        when(mCallAudioManager.startRinging()).thenReturn(false);

        sm.sendMessage(CallAudioModeStateMachine.NEW_RINGING_CALL,
                new CallAudioModeStateMachine.MessageArgs(
                        false, // hasActiveOrDialingCalls
                        true, // hasRingingCalls
                        false, // hasHoldingCalls
                        false, // isTonePlaying
                        false, // foregroundCallIsVoip
                        null // session
                ));
        waitForStateMachineActionCompletion(sm, CallAudioModeStateMachine.RUN_RUNNABLE);

        assertEquals(CallAudioModeStateMachine.RING_STATE_NAME, sm.getCurrentStateName());

        verify(mAudioManager, never()).requestAudioFocusForCall(anyInt(), anyInt());
        verify(mAudioManager, never()).setMode(anyInt());

        verify(mCallAudioManager, never()).stopRinging();

        verify(mCallAudioManager).stopCallWaiting();
    }

    private List<ModeTestParameters> generateTestCases() {
        List<ModeTestParameters> result = new ArrayList<>();
        result.add(new ModeTestParameters(
                "New active/dialing call with no other calls when unfocused",
                CallAudioModeStateMachine.ABANDON_FOCUS_FOR_TESTING, // initialAudioState
                CallAudioModeStateMachine.NEW_ACTIVE_OR_DIALING_CALL, // messageType
                new CallAudioModeStateMachine.MessageArgs(
                        true, // hasActiveOrDialingCalls
                        false, // hasRingingCalls
                        false, // hasHoldingCalls
                        false, // isTonePlaying
                        false, // foregroundCallIsVoip
                        null // session
                ),
                CallAudioModeStateMachine.CALL_STATE_NAME, // expectedFinalStateName
                FOCUS_VOICE, // expectedFocus
                AudioManager.MODE_IN_CALL, // expectedMode
                NO_CHANGE, // expectedRingingInteraction
                NO_CHANGE // expectedCallWaitingInteraction
        ));

        result.add(new ModeTestParameters(
                "New active/dialing voip call with no other calls when unfocused",
                CallAudioModeStateMachine.ABANDON_FOCUS_FOR_TESTING, // initialAudioState
                CallAudioModeStateMachine.NEW_ACTIVE_OR_DIALING_CALL, // messageType
                new CallAudioModeStateMachine.MessageArgs(
                        true, // hasActiveOrDialingCalls
                        false, // hasRingingCalls
                        false, // hasHoldingCalls
                        false, // isTonePlaying
                        true, // foregroundCallIsVoip
                        null // session
                ),
                CallAudioModeStateMachine.COMMS_STATE_NAME, // expectedFinalStateName
                FOCUS_VOICE, // expectedFocus
                AudioManager.MODE_IN_COMMUNICATION, // expectedMode
                NO_CHANGE, // expectedRingingInteraction
                NO_CHANGE // expectedCallWaitingInteraction
        ));

        result.add(new ModeTestParameters(
                "New ringing call with no other calls when unfocused",
                CallAudioModeStateMachine.ABANDON_FOCUS_FOR_TESTING, // initialAudioState
                CallAudioModeStateMachine.NEW_RINGING_CALL, // messageType
                new CallAudioModeStateMachine.MessageArgs(
                        false, // hasActiveOrDialingCalls
                        true, // hasRingingCalls
                        false, // hasHoldingCalls
                        false, // isTonePlaying
                        false, // foregroundCallIsVoip
                        null // session
                ),
                CallAudioModeStateMachine.RING_STATE_NAME, // expectedFinalStateName
                FOCUS_RING, // expectedFocus
                AudioManager.MODE_RINGTONE, // expectedMode
                ON, // expectedRingingInteraction
                OFF // expectedCallWaitingInteraction
        ));

        result.add(new ModeTestParameters(
                "New ringing call coming in on top of active/dialing call",
                CallAudioModeStateMachine.ENTER_CALL_FOCUS_FOR_TESTING, // initialAudioState
                CallAudioModeStateMachine.NEW_RINGING_CALL, // messageType
                new CallAudioModeStateMachine.MessageArgs(
                        true, // hasActiveOrDialingCalls
                        true, // hasRingingCalls
                        false, // hasHoldingCalls
                        false, // isTonePlaying
                        false, // foregroundCallIsVoip
                        null // session
                ),
                CallAudioModeStateMachine.CALL_STATE_NAME, // expectedFinalStateName
                NO_CHANGE, // expectedFocus
                NO_CHANGE, // expectedMode
                NO_CHANGE, // expectedRingingInteraction
                ON // expectedCallWaitingInteraction
        ));

        result.add(new ModeTestParameters(
                "Ringing call becomes active, part 1",
                CallAudioModeStateMachine.ENTER_RING_FOCUS_FOR_TESTING, // initialAudioState
                CallAudioModeStateMachine.NEW_ACTIVE_OR_DIALING_CALL, // messageType
                new CallAudioModeStateMachine.MessageArgs(
                        true, // hasActiveOrDialingCalls
                        false, // hasRingingCalls
                        false, // hasHoldingCalls
                        false, // isTonePlaying
                        false, // foregroundCallIsVoip
                        null // session
                ),
                CallAudioModeStateMachine.CALL_STATE_NAME, // expectedFinalStateName
                FOCUS_VOICE, // expectedFocus
                AudioManager.MODE_IN_CALL, // expectedMode
                OFF, // expectedRingingInteraction
                NO_CHANGE // expectedCallWaitingInteraction
        ));

        result.add(new ModeTestParameters(
                "Ringing call becomes active, part 2",
                CallAudioModeStateMachine.ENTER_CALL_FOCUS_FOR_TESTING, // initialAudioState
                CallAudioModeStateMachine.NO_MORE_RINGING_CALLS, // messageType
                new CallAudioModeStateMachine.MessageArgs(
                        true, // hasActiveOrDialingCalls
                        false, // hasRingingCalls
                        false, // hasHoldingCalls
                        false, // isTonePlaying
                        false, // foregroundCallIsVoip
                        null // session
                ),
                CallAudioModeStateMachine.CALL_STATE_NAME, // expectedFinalStateName
                NO_CHANGE, // expectedFocus
                NO_CHANGE, // expectedMode
                NO_CHANGE, // expectedRingingInteraction
                NO_CHANGE // expectedCallWaitingInteraction
        ));

        result.add(new ModeTestParameters(
                "Active call disconnects, but tone is playing",
                CallAudioModeStateMachine.ENTER_CALL_FOCUS_FOR_TESTING, // initialAudioState
                CallAudioModeStateMachine.NO_MORE_ACTIVE_OR_DIALING_CALLS, // messageType
                new CallAudioModeStateMachine.MessageArgs(
                        false, // hasActiveOrDialingCalls
                        false, // hasRingingCalls
                        false, // hasHoldingCalls
                        true, // isTonePlaying
                        false, // foregroundCallIsVoip
                        null // session
                ),
                CallAudioModeStateMachine.TONE_HOLD_STATE_NAME, // expectedFinalStateName
                FOCUS_VOICE, // expectedFocus
                AudioManager.MODE_IN_CALL, // expectedMode
                NO_CHANGE, // expectedRingingInteraction
                NO_CHANGE // expectedCallWaitingInteraction
        ));

        result.add(new ModeTestParameters(
                "Tone stops playing, with no active calls",
                CallAudioModeStateMachine.ENTER_TONE_OR_HOLD_FOCUS_FOR_TESTING, // initialAudioState
                CallAudioModeStateMachine.TONE_STOPPED_PLAYING, // messageType
                new CallAudioModeStateMachine.MessageArgs(
                        false, // hasActiveOrDialingCalls
                        false, // hasRingingCalls
                        false, // hasHoldingCalls
                        false, // isTonePlaying
                        false, // foregroundCallIsVoip
                        null // session
                ),
                CallAudioModeStateMachine.UNFOCUSED_STATE_NAME, // expectedFinalStateName
                FOCUS_OFF, // expectedFocus
                AudioManager.MODE_NORMAL, // expectedMode
                NO_CHANGE, // expectedRingingInteraction
                NO_CHANGE // expectedCallWaitingInteraction
        ));

        result.add(new ModeTestParameters(
                "Ringing call disconnects",
                CallAudioModeStateMachine.ENTER_RING_FOCUS_FOR_TESTING, // initialAudioState
                CallAudioModeStateMachine.NO_MORE_RINGING_CALLS, // messageType
                new CallAudioModeStateMachine.MessageArgs(
                        false, // hasActiveOrDialingCalls
                        false, // hasRingingCalls
                        false, // hasHoldingCalls
                        false, // isTonePlaying
                        false, // foregroundCallIsVoip
                        null // session
                ),
                CallAudioModeStateMachine.UNFOCUSED_STATE_NAME, // expectedFinalStateName
                FOCUS_OFF, // expectedFocus
                AudioManager.MODE_NORMAL, // expectedMode
                OFF, // expectedRingingInteraction
                NO_CHANGE // expectedCallWaitingInteraction
        ));

        result.add(new ModeTestParameters(
                "Call-waiting call disconnects",
                CallAudioModeStateMachine.ENTER_CALL_FOCUS_FOR_TESTING, // initialAudioState
                CallAudioModeStateMachine.NO_MORE_RINGING_CALLS, // messageType
                new CallAudioModeStateMachine.MessageArgs(
                        true, // hasActiveOrDialingCalls
                        false, // hasRingingCalls
                        false, // hasHoldingCalls
                        true, // isTonePlaying
                        false, // foregroundCallIsVoip
                        null // session
                ),
                CallAudioModeStateMachine.CALL_STATE_NAME, // expectedFinalStateName
                FOCUS_NO_CHANGE, // expectedFocus
                NO_CHANGE, // expectedMode
                NO_CHANGE, // expectedRingingInteraction
                OFF // expectedCallWaitingInteraction
        ));

        result.add(new ModeTestParameters(
                "Call is placed on hold - 1",
                CallAudioModeStateMachine.ENTER_CALL_FOCUS_FOR_TESTING, // initialAudioState
                CallAudioModeStateMachine.NO_MORE_ACTIVE_OR_DIALING_CALLS, // messageType
                new CallAudioModeStateMachine.MessageArgs(
                        false, // hasActiveOrDialingCalls
                        false, // hasRingingCalls
                        true, // hasHoldingCalls
                        false, // isTonePlaying
                        false, // foregroundCallIsVoip
                        null // session
                ),
                CallAudioModeStateMachine.TONE_HOLD_STATE_NAME, // expectedFinalStateName
                FOCUS_VOICE, // expectedFocus
                AudioManager.MODE_IN_CALL, // expectedMode
                NO_CHANGE, // expectedRingingInteraction
                NO_CHANGE // expectedCallWaitingInteraction
        ));

        result.add(new ModeTestParameters(
                "Call is placed on hold - 2",
                CallAudioModeStateMachine.ENTER_TONE_OR_HOLD_FOCUS_FOR_TESTING, // initialAudioState
                CallAudioModeStateMachine.NEW_HOLDING_CALL, // messageType
                new CallAudioModeStateMachine.MessageArgs(
                        false, // hasActiveOrDialingCalls
                        false, // hasRingingCalls
                        true, // hasHoldingCalls
                        false, // isTonePlaying
                        false, // foregroundCallIsVoip
                        null // session
                ),
                CallAudioModeStateMachine.TONE_HOLD_STATE_NAME, // expectedFinalStateName
                FOCUS_NO_CHANGE, // expectedFocus
                NO_CHANGE, // expectedMode
                NO_CHANGE, // expectedRingingInteraction
                NO_CHANGE // expectedCallWaitingInteraction
        ));

        result.add(new ModeTestParameters(
                "Call is taken off hold - 1",
                CallAudioModeStateMachine.ENTER_TONE_OR_HOLD_FOCUS_FOR_TESTING, // initialAudioState
                CallAudioModeStateMachine.NO_MORE_HOLDING_CALLS, // messageType
                new CallAudioModeStateMachine.MessageArgs(
                        true, // hasActiveOrDialingCalls
                        false, // hasRingingCalls
                        false, // hasHoldingCalls
                        false, // isTonePlaying
                        false, // foregroundCallIsVoip
                        null // session
                ),
                CallAudioModeStateMachine.CALL_STATE_NAME, // expectedFinalStateName
                FOCUS_VOICE, // expectedFocus
                AudioManager.MODE_IN_CALL, // expectedMode
                NO_CHANGE, // expectedRingingInteraction
                NO_CHANGE // expectedCallWaitingInteraction
        ));

        result.add(new ModeTestParameters(
                "Call is taken off hold - 2",
                CallAudioModeStateMachine.ENTER_CALL_FOCUS_FOR_TESTING, // initialAudioState
                CallAudioModeStateMachine.NEW_ACTIVE_OR_DIALING_CALL, // messageType
                new CallAudioModeStateMachine.MessageArgs(
                        true, // hasActiveOrDialingCalls
                        false, // hasRingingCalls
                        false, // hasHoldingCalls
                        false, // isTonePlaying
                        false, // foregroundCallIsVoip
                        null // session
                ),
                CallAudioModeStateMachine.CALL_STATE_NAME, // expectedFinalStateName
                FOCUS_NO_CHANGE, // expectedFocus
                NO_CHANGE, // expectedMode
                NO_CHANGE, // expectedRingingInteraction
                NO_CHANGE // expectedCallWaitingInteraction
        ));

        result.add(new ModeTestParameters(
                "Active call disconnects while there's a call-waiting call",
                CallAudioModeStateMachine.ENTER_CALL_FOCUS_FOR_TESTING, // initialAudioState
                CallAudioModeStateMachine.NO_MORE_ACTIVE_OR_DIALING_CALLS, // messageType
                new CallAudioModeStateMachine.MessageArgs(
                        false, // hasActiveOrDialingCalls
                        true, // hasRingingCalls
                        false, // hasHoldingCalls
                        true, // isTonePlaying
                        false, // foregroundCallIsVoip
                        null // session
                ),
                CallAudioModeStateMachine.RING_STATE_NAME, // expectedFinalStateName
                FOCUS_RING, // expectedFocus
                AudioManager.MODE_RINGTONE, // expectedMode
                ON, // expectedRingingInteraction
                OFF // expectedCallWaitingInteraction
        ));

        result.add(new ModeTestParameters(
                "New dialing call when there's a call on hold",
                CallAudioModeStateMachine.ENTER_TONE_OR_HOLD_FOCUS_FOR_TESTING, // initialAudioState
                CallAudioModeStateMachine.NEW_ACTIVE_OR_DIALING_CALL, // messageType
                new CallAudioModeStateMachine.MessageArgs(
                        true, // hasActiveOrDialingCalls
                        false, // hasRingingCalls
                        true, // hasHoldingCalls
                        false, // isTonePlaying
                        false, // foregroundCallIsVoip
                        null // session
                ),
                CallAudioModeStateMachine.CALL_STATE_NAME, // expectedFinalStateName
                FOCUS_VOICE, // expectedFocus
                AudioManager.MODE_IN_CALL, // expectedMode
                NO_CHANGE, // expectedRingingInteraction
                NO_CHANGE // expectedCallWaitingInteraction
        ));

        result.add(new ModeTestParameters(
                "Ringing call disconnects with a holding call in the background",
                CallAudioModeStateMachine.ENTER_RING_FOCUS_FOR_TESTING, // initialAudioState
                CallAudioModeStateMachine.NO_MORE_RINGING_CALLS, // messageType
                new CallAudioModeStateMachine.MessageArgs(
                        false, // hasActiveOrDialingCalls
                        false, // hasRingingCalls
                        true, // hasHoldingCalls
                        false, // isTonePlaying
                        false, // foregroundCallIsVoip
                        null // session
                ),
                CallAudioModeStateMachine.TONE_HOLD_STATE_NAME, // expectedFinalStateName
                FOCUS_VOICE, // expectedFocus
                AudioManager.MODE_NORMAL, // expectedMode -- we're expecting this because
                                          // mMostRecentMode hasn't been set properly.
                OFF, // expectedRingingInteraction
                NO_CHANGE // expectedCallWaitingInteraction
        ));

        result.add(new ModeTestParameters(
                "Foreground call transitions from sim to voip",
                CallAudioModeStateMachine.ENTER_CALL_FOCUS_FOR_TESTING, // initialAudioState
                CallAudioModeStateMachine.FOREGROUND_VOIP_MODE_CHANGE, // messageType
                new CallAudioModeStateMachine.MessageArgs(
                        true, // hasActiveOrDialingCalls
                        false, // hasRingingCalls
                        false, // hasHoldingCalls
                        false, // isTonePlaying
                        true, // foregroundCallIsVoip
                        null // session
                ),
                CallAudioModeStateMachine.COMMS_STATE_NAME, // expectedFinalStateName
                FOCUS_VOICE, // expectedFocus
                AudioManager.MODE_IN_COMMUNICATION, // expectedMode
                NO_CHANGE, // expectedRingingInteraction
                NO_CHANGE // expectedCallWaitingInteraction
        ));

        result.add(new ModeTestParameters(
                "Foreground call transitions from voip to sim",
                CallAudioModeStateMachine.ENTER_COMMS_FOCUS_FOR_TESTING, // initialAudioState
                CallAudioModeStateMachine.FOREGROUND_VOIP_MODE_CHANGE, // messageType
                new CallAudioModeStateMachine.MessageArgs(
                        true, // hasActiveOrDialingCalls
                        false, // hasRingingCalls
                        false, // hasHoldingCalls
                        false, // isTonePlaying
                        false, // foregroundCallIsVoip
                        null // session
                ),
                CallAudioModeStateMachine.CALL_STATE_NAME, // expectedFinalStateName
                FOCUS_VOICE, // expectedFocus
                AudioManager.MODE_IN_CALL, // expectedMode
                NO_CHANGE, // expectedRingingInteraction
                NO_CHANGE // expectedCallWaitingInteraction
        ));

        result.add(new ModeTestParameters(
                "Call-waiting hangs up before being answered, with another sim call in " +
                        "foreground",
                CallAudioModeStateMachine.ENTER_CALL_FOCUS_FOR_TESTING, // initialAudioState
                CallAudioModeStateMachine.NO_MORE_RINGING_CALLS, // messageType
                new CallAudioModeStateMachine.MessageArgs(
                        true, // hasActiveOrDialingCalls
                        false, // hasRingingCalls
                        false, // hasHoldingCalls
                        true, // isTonePlaying
                        false, // foregroundCallIsVoip
                        null // session
                ),
                CallAudioModeStateMachine.CALL_STATE_NAME, // expectedFinalStateName
                FOCUS_NO_CHANGE, // expectedFocus
                NO_CHANGE, // expectedMode
                NO_CHANGE, // expectedRingingInteraction
                OFF // expectedCallWaitingInteraction
        ));

        result.add(new ModeTestParameters(
                "Call-waiting hangs up before being answered, with another voip call in " +
                        "foreground",
                CallAudioModeStateMachine.ENTER_COMMS_FOCUS_FOR_TESTING, // initialAudioState
                CallAudioModeStateMachine.NO_MORE_RINGING_CALLS, // messageType
                new CallAudioModeStateMachine.MessageArgs(
                        true, // hasActiveOrDialingCalls
                        false, // hasRingingCalls
                        false, // hasHoldingCalls
                        true, // isTonePlaying
                        true, // foregroundCallIsVoip
                        null // session
                ),
                CallAudioModeStateMachine.COMMS_STATE_NAME, // expectedFinalStateName
                FOCUS_NO_CHANGE, // expectedFocus
                NO_CHANGE, // expectedMode
                NO_CHANGE, // expectedRingingInteraction
                OFF // expectedCallWaitingInteraction
        ));

        return result;
    }

    @Override
    protected void runParametrizedTestCase(TestParameters _params) {
        ModeTestParameters params = (ModeTestParameters) _params;
        CallAudioModeStateMachine sm = new CallAudioModeStateMachine(mAudioManager);
        sm.setCallAudioManager(mCallAudioManager);
        sm.sendMessage(params.initialAudioState);
        waitForStateMachineActionCompletion(sm, CallAudioModeStateMachine.RUN_RUNNABLE);

        resetMocks();
        when(mCallAudioManager.startRinging()).thenReturn(true);

        sm.sendMessage(params.messageType, params.externalState);
        waitForStateMachineActionCompletion(sm, CallAudioModeStateMachine.RUN_RUNNABLE);

        assertEquals(params.expectedFinalStateName, sm.getCurrentStateName());

        switch (params.expectedFocus) {
            case FOCUS_NO_CHANGE:
                verify(mAudioManager, never()).requestAudioFocusForCall(anyInt(), anyInt());
                break;
            case FOCUS_OFF:
                verify(mAudioManager).abandonAudioFocusForCall();
                break;
            case FOCUS_RING:
                verify(mAudioManager).requestAudioFocusForCall(
                        eq(AudioManager.STREAM_RING), anyInt());
                break;
            case FOCUS_VOICE:
                verify(mAudioManager).requestAudioFocusForCall(
                        eq(AudioManager.STREAM_VOICE_CALL), anyInt());
                break;
        }

        if (params.expectedMode != NO_CHANGE) {
            verify(mAudioManager).setMode(eq(params.expectedMode));
        } else {
            verify(mAudioManager, never()).setMode(anyInt());
        }

        switch (params.expectedRingingInteraction) {
            case NO_CHANGE:
                verify(mCallAudioManager, never()).startRinging();
                verify(mCallAudioManager, never()).stopRinging();
                break;
            case ON:
                verify(mCallAudioManager).startRinging();
                break;
            case OFF:
                verify(mCallAudioManager).stopRinging();
                break;
        }

        switch (params.expectedCallWaitingInteraction) {
            case NO_CHANGE:
                verify(mCallAudioManager, never()).startCallWaiting();
                verify(mCallAudioManager, never()).stopCallWaiting();
                break;
            case ON:
                verify(mCallAudioManager).startCallWaiting();
                break;
            case OFF:
                verify(mCallAudioManager).stopCallWaiting();
                break;
        }

        sm.quitNow();
    }

    private void resetMocks() {
        reset(mCallAudioManager, mAudioManager);
    }
}
