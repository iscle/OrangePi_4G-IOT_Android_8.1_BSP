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
 * limitations under the License.
 */

package android.telecom.cts;

import android.telecom.Call;
import android.telecom.CallAudioState;
import android.telecom.Connection;

/**
 * Verifies Telecom behavior with regards to interactions with a wired headset. These tests
 * validate behavior that occurs as a result of short pressing or long pressing a wired headset's
 * media button.
 */
public class WiredHeadsetTest extends BaseTelecomTestWithMockServices {

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        if (mShouldTestTelecom) {
            setupConnectionService(null, FLAG_REGISTER | FLAG_ENABLE);
        }
    }

    public void testIncomingCallShortPress_acceptsCall() throws Exception {
        if (!mShouldTestTelecom) {
            return;
        }

        addAndVerifyNewIncomingCall(createTestNumber(), null);
        final MockConnection connection = verifyConnectionForIncomingCall();

        final Call call = mInCallCallbacks.getService().getLastCall();
        assertCallState(call, Call.STATE_RINGING);
        assertConnectionState(connection, Connection.STATE_RINGING);

        sendMediaButtonShortPress();
        assertCallState(call,  Call.STATE_ACTIVE);
        assertConnectionState(connection, Connection.STATE_ACTIVE);
    }

    public void testIncomingCallLongPress_rejectsCall() throws Exception {
        if (!mShouldTestTelecom) {
            return;
        }

        addAndVerifyNewIncomingCall(createTestNumber(), null);
        final MockConnection connection = verifyConnectionForIncomingCall();

        final Call call = mInCallCallbacks.getService().getLastCall();
        assertCallState(call, Call.STATE_RINGING);
        assertConnectionState(connection, Connection.STATE_RINGING);

        sendMediaButtonLongPress();
        assertCallState(call, Call.STATE_DISCONNECTED);
        assertConnectionState(connection, Connection.STATE_DISCONNECTED);
    }

    public void testInCallLongPress_togglesMute() throws Exception {
        if (!mShouldTestTelecom) {
            return;
        }

        placeAndVerifyCall();
        final MockConnection connection = verifyConnectionForOutgoingCall();
        final MockInCallService incallService = mInCallCallbacks.getService();

        // Verify that sending short presses in succession toggles the mute state of the
        // connection.
        // Before the audio state is changed for the first time, the connection might not
        // know about its audio state yet.
        assertMuteState(incallService, false);
        sendMediaButtonLongPress();
        assertMuteState(connection, true);
        assertMuteState(incallService, true);
        sendMediaButtonLongPress();
        assertMuteState(connection, false);
        assertMuteState(incallService, false);
    }

    public void testInCallShortPress_hangupCall() throws Exception {
        if (!mShouldTestTelecom) {
            return;
        }

        placeAndVerifyCall();
        final MockConnection connection = verifyConnectionForOutgoingCall();

        final Call call = mInCallCallbacks.getService().getLastCall();
        assertCallState(call, Call.STATE_DIALING);

        connection.setActive();
        assertCallState(call, Call.STATE_ACTIVE);

        sendMediaButtonShortPress();
        assertCallState(call, Call.STATE_DISCONNECTED);
        assertConnectionState(connection, Connection.STATE_DISCONNECTED);
    }

    private void sendMediaButtonShortPress() throws Exception {
        sendMediaButtonPress(false /* longPress */);
    }

    private void sendMediaButtonLongPress() throws Exception {
        sendMediaButtonPress(true /* longPress */);
    }

    private void sendMediaButtonPress(boolean longPress) throws Exception {
        final String command = "input keyevent " + (longPress ? "--longpress" : "--shortpress")
                + " KEYCODE_HEADSETHOOK";
        TestUtils.executeShellCommand(getInstrumentation(), command);
    }
}
