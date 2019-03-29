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
 * limitations under the License
 */

package android.telecom.cts;

import android.os.Bundle;
import android.telecom.Call;
import android.telecom.Connection;
import android.telecom.TelecomManager;

import java.io.IOException;

public class RttOperationsTest extends BaseTelecomTestWithMockServices {
    private static final int RTT_SEND_TIMEOUT_MILLIS = 1000;
    private static final String[] TEST_STRINGS = {
            "A",
            "AB",
            "ABCDEFG",
            "お疲れ様でした",
            "😂😂😂💯"
    };
    private static final int RTT_FAILURE_REASON = 2;

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        if (mShouldTestTelecom) {
            setupConnectionService(null, FLAG_REGISTER | FLAG_ENABLE);
        }
    }

    public void testOutgoingRttCall() throws Exception {
        if (!mShouldTestTelecom) {
            return;
        }

        placeRttCall(false);
        final MockConnection connection = verifyConnectionForOutgoingCall();
        final MockInCallService inCallService = mInCallCallbacks.getService();
        final Call call = inCallService.getLastCall();
        TestUtils.waitOnAllHandlers(getInstrumentation());
        verifyRttEnabled(call, connection);
    }

    public void testIncomingRttCall() throws Exception {
        if (!mShouldTestTelecom) {
            return;
        }

        placeRttCall(true);
        final MockConnection connection = verifyConnectionForIncomingCall();
        final MockInCallService inCallService = mInCallCallbacks.getService();
        final Call call = inCallService.getLastCall();
        TestUtils.waitOnAllHandlers(getInstrumentation());
        verifyRttEnabled(call, connection);
    }

    public void testLocalRttUpgradeAccepted() throws Exception {
        if (!mShouldTestTelecom) {
            return;
        }

        placeAndVerifyCall();
        final MockConnection connection = verifyConnectionForOutgoingCall();
        final MockInCallService inCallService = mInCallCallbacks.getService();
        final Call call = inCallService.getLastCall();
        verifyRttDisabled(call);

        TestUtils.InvokeCounter startRttCounter =
                connection.getInvokeCounter(MockConnection.ON_START_RTT);
        call.sendRttRequest();
        startRttCounter.waitForCount(1, TestUtils.WAIT_FOR_STATE_CHANGE_TIMEOUT_MS);

        connection.setRttTextStream((Connection.RttTextStream) startRttCounter.getArgs(0)[0]);
        connection.sendRttInitiationSuccess();
        TestUtils.waitOnAllHandlers(getInstrumentation());
        verifyRttEnabled(call, connection);
    }

    public void testLocalRttUpgradeRejected() throws Exception {
        if (!mShouldTestTelecom) {
            return;
        }

        placeAndVerifyCall();
        final MockConnection connection = verifyConnectionForOutgoingCall();
        final MockInCallService inCallService = mInCallCallbacks.getService();
        final Call call = inCallService.getLastCall();
        verifyRttDisabled(call);

        TestUtils.InvokeCounter startRttCounter =
                connection.getInvokeCounter(MockConnection.ON_START_RTT);
        call.sendRttRequest();
        startRttCounter.waitForCount(1, TestUtils.WAIT_FOR_STATE_CHANGE_TIMEOUT_MS);

        connection.sendRttInitiationFailure(RTT_FAILURE_REASON);
        TestUtils.waitOnAllHandlers(getInstrumentation());
        mOnRttInitiationFailedCounter.waitForCount(1, TestUtils.WAIT_FOR_STATE_CHANGE_TIMEOUT_MS);
        assertEquals(call, mOnRttInitiationFailedCounter.getArgs(0)[0]);
        assertEquals(RTT_FAILURE_REASON, mOnRttInitiationFailedCounter.getArgs(0)[1]);
        verifyRttDisabled(call);
    }

    public void testAcceptRemoteRttUpgrade() throws Exception {
        if (!mShouldTestTelecom) {
            return;
        }

        placeAndVerifyCall();
        final MockConnection connection = verifyConnectionForOutgoingCall();
        final MockInCallService inCallService = mInCallCallbacks.getService();
        final Call call = inCallService.getLastCall();
        verifyRttDisabled(call);

        TestUtils.InvokeCounter rttRequestResponseCounter =
                connection.getInvokeCounter(MockConnection.ON_RTT_REQUEST_RESPONSE);
        connection.sendRemoteRttRequest();
        mOnRttRequestCounter.waitForCount(1, TestUtils.WAIT_FOR_STATE_CHANGE_TIMEOUT_MS);
        int requestId = (Integer) mOnRttRequestCounter.getArgs(0)[1];
        call.respondToRttRequest(requestId, true /* accept */);

        rttRequestResponseCounter.waitForCount(1, TestUtils.WAIT_FOR_STATE_CHANGE_TIMEOUT_MS);
        TestUtils.waitOnAllHandlers(getInstrumentation());
        verifyRttEnabled(call, connection);
    }

    public void testRejectRemoteRttRequest() throws Exception {
        if (!mShouldTestTelecom) {
            return;
        }

        placeAndVerifyCall();
        final MockConnection connection = verifyConnectionForOutgoingCall();
        final MockInCallService inCallService = mInCallCallbacks.getService();
        final Call call = inCallService.getLastCall();
        verifyRttDisabled(call);

        TestUtils.InvokeCounter rttRequestResponseCounter =
                connection.getInvokeCounter(MockConnection.ON_RTT_REQUEST_RESPONSE);
        connection.sendRemoteRttRequest();
        mOnRttRequestCounter.waitForCount(1, TestUtils.WAIT_FOR_STATE_CHANGE_TIMEOUT_MS);
        int requestId = (Integer) mOnRttRequestCounter.getArgs(0)[1];
        call.respondToRttRequest(requestId, false /* accept */);

        rttRequestResponseCounter.waitForCount(1, TestUtils.WAIT_FOR_STATE_CHANGE_TIMEOUT_MS);
        assertNull(rttRequestResponseCounter.getArgs(0)[0]);
        TestUtils.waitOnAllHandlers(getInstrumentation());
        verifyRttDisabled(call);
    }

    public void testLocalRttTermination() throws Exception {
        if (!mShouldTestTelecom) {
            return;
        }

        placeRttCall(false);
        final MockConnection connection = verifyConnectionForOutgoingCall();
        final MockInCallService inCallService = mInCallCallbacks.getService();
        final Call call = inCallService.getLastCall();

        // Skipping RTT verification since that's tested by another test
        TestUtils.InvokeCounter stopRttCounter =
                connection.getInvokeCounter(MockConnection.ON_STOP_RTT);
        call.stopRtt();
        stopRttCounter.waitForCount(1, TestUtils.WAIT_FOR_STATE_CHANGE_TIMEOUT_MS);
        TestUtils.waitOnAllHandlers(getInstrumentation());
        verifyRttDisabled(call);
    }

    public void testRemoteRttTermination() throws Exception {
        if (!mShouldTestTelecom) {
            return;
        }

        placeRttCall(false);
        final MockConnection connection = verifyConnectionForOutgoingCall();
        final MockInCallService inCallService = mInCallCallbacks.getService();
        final Call call = inCallService.getLastCall();

        // Skipping RTT verification since that's tested by another test
        connection.sendRttSessionRemotelyTerminated();
        TestUtils.InvokeCounter stopRttCounter =
                connection.getInvokeCounter(MockConnection.ON_STOP_RTT);
        call.stopRtt();
        stopRttCounter.waitForCount(1, TestUtils.WAIT_FOR_STATE_CHANGE_TIMEOUT_MS);
        TestUtils.waitOnAllHandlers(getInstrumentation());
        verifyRttDisabled(call);
    }

    private void verifyRttDisabled(Call call) {
        TestUtils.waitOnLocalMainLooper(TestUtils.WAIT_FOR_STATE_CHANGE_TIMEOUT_MS);
        assertFalse(call.isRttActive());
        assertNull(call.getRttCall());
    }

    private void verifyRttEnabled(Call call, MockConnection connection) {
        TestUtils.waitOnLocalMainLooper(TestUtils.WAIT_FOR_STATE_CHANGE_TIMEOUT_MS);
        Connection.RttTextStream connectionSideRtt = connection.getRttTextStream();
        Call.RttCall inCallSideRtt = call.getRttCall();
        assertNotNull(connectionSideRtt);
        assertTrue(call.isRttActive());
        assertNotNull(inCallSideRtt);

        verifyRttPipeIntegrity(inCallSideRtt, connectionSideRtt);
    }

    private void verifyRttPipeIntegrity(Call.RttCall inCallSide, Connection.RttTextStream
            connectionSide) {
        for (String s : TEST_STRINGS) {
            try {
                inCallSide.write(s);
                waitUntilConditionIsTrueOrTimeout(new Condition() {
                    String readSoFar = "";
                    @Override
                    public Object expected() {
                        return s;
                    }

                    @Override
                    public Object actual() {
                        try {
                            String newRead = connectionSide.readImmediately();
                            if (newRead != null) {
                                readSoFar += newRead;
                            }
                            return readSoFar;
                        } catch (IOException e) {
                            fail("IOException while reading from connection side");
                            return null;
                        }
                    }
                }, RTT_SEND_TIMEOUT_MILLIS, String.format("%s failed to send correctly.", s));

                connectionSide.write(s);
                waitUntilConditionIsTrueOrTimeout(new Condition() {
                    String readSoFar = "";
                    @Override
                    public Object expected() {
                        return s;
                    }

                    @Override
                    public Object actual() {
                        try {
                            String newRead = inCallSide.readImmediately();
                            if (newRead != null) {
                                readSoFar += newRead;
                            }
                            return readSoFar;
                        } catch (IOException e) {
                            fail("IOException while reading from incall side");
                            return null;
                        }
                    }
                }, RTT_SEND_TIMEOUT_MILLIS, String.format("%s failed to send correctly.", s));
            } catch (IOException e) {
                fail(String.format(
                        "Caught IOException when verifying %s", s));
            }

        }
    }
    private void placeRttCall(boolean incoming) {
        Bundle extras = new Bundle();
        extras.putBoolean(TelecomManager.EXTRA_START_CALL_WITH_RTT, true);
        if (incoming) {
            addAndVerifyNewIncomingCall(createTestNumber(), extras);
        } else {
            Bundle outgoingCallExtras = new Bundle();
            outgoingCallExtras.putParcelable(TelecomManager.EXTRA_OUTGOING_CALL_EXTRAS, extras);
            placeAndVerifyCall(outgoingCallExtras);
        }
    }
}
