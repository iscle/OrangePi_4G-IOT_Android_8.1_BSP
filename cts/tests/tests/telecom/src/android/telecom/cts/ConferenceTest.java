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

import static android.telecom.cts.TestUtils.*;

import android.os.Bundle;
import android.telecom.Call;
import android.telecom.Conference;
import android.telecom.Connection;
import android.telecom.ConnectionRequest;
import android.telecom.DisconnectCause;
import android.telecom.PhoneAccount;
import android.telecom.PhoneAccountHandle;
import android.telecom.StatusHints;
import android.telecom.TelecomManager;
import android.telecom.VideoProfile;
import android.util.Log;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Extended suite of tests that use {@link CtsConnectionService} and {@link MockInCallService} to
 * verify the functionality of Call Conferencing.
 */
public class ConferenceTest extends BaseTelecomTestWithMockServices {

    private static final String TEST_EXTRA_KEY_1 = "android.telecom.test.KEY1";
    private static final String TEST_EXTRA_KEY_2 = "android.telecom.test.KEY2";
    private static final String TEST_EXTRA_VALUE_1 = "test";
    private static final int TEST_EXTRA_VALUE_2 = 42;

    public static final int CONF_CAPABILITIES = Connection.CAPABILITY_SEPARATE_FROM_CONFERENCE |
            Connection.CAPABILITY_DISCONNECT_FROM_CONFERENCE | Connection.CAPABILITY_HOLD |
            Connection.CAPABILITY_MERGE_CONFERENCE | Connection.CAPABILITY_SWAP_CONFERENCE;

    private Call mCall1, mCall2;
    private MockConnection mConnection1, mConnection2;
    MockInCallService mInCallService;
    Conference mConferenceObject;
    MockConference mConferenceVerficationObject;

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        if (mShouldTestTelecom) {
            addOutgoingCalls();
            addConferenceCall(mCall1, mCall2);
            mConferenceVerficationObject = verifyConferenceForOutgoingCall();
            // Use vanilla conference object so that the CTS coverage tool detects the usage.
            mConferenceObject = mConferenceVerficationObject;
            verifyConferenceObject(mConferenceObject, mConnection1, mConnection2);
        }
    }

    public void testConferenceCreate() {
        if (!mShouldTestTelecom) {
            return;
        }
        final Call conf = mInCallService.getLastConferenceCall();
        assertCallState(conf, Call.STATE_ACTIVE);

        if (mCall1.getParent() != conf || mCall2.getParent() != conf) {
            fail("The 2 participating calls should contain the conference call as its parent");
        }
        if (!(conf.getChildren().contains(mCall1) && conf.getChildren().contains(mCall2))) {
            fail("The conference call should contain the 2 participating calls as its children");
        }
        assertTrue(mConferenceObject.getConnections().contains(mConnection1));

        assertConnectionState(mConferenceObject.getConnections().get(0), Connection.STATE_ACTIVE);
        assertConnectionState(mConferenceObject.getConnections().get(1), Connection.STATE_ACTIVE);
        assertConferenceState(mConferenceObject, Connection.STATE_ACTIVE);
    }

    public void testConferenceSplit() {
        if (!mShouldTestTelecom) {
            return;
        }
        final Call conf = mInCallService.getLastConferenceCall();
        assertCallState(conf, Call.STATE_ACTIVE);

        if (!(mCall1.getParent() == conf) && (conf.getChildren().contains(mCall1))) {
            fail("Call 1 not conferenced");
        }
        assertTrue(mConferenceObject.getConnections().contains(mConnection1));

        splitFromConferenceCall(mCall1);

        if ((mCall1.getParent() == conf) || (conf.getChildren().contains(mCall1))) {
            fail("Call 1 should not be still conferenced");
        }
        assertFalse(mConferenceObject.getConnections().contains(mConnection1));
    }

    public void testConferenceHoldAndUnhold() {
        if (!mShouldTestTelecom) {
            return;
        }
        final Call conf = mInCallService.getLastConferenceCall();
        assertCallState(conf, Call.STATE_ACTIVE);

        conf.hold();
        assertCallState(conf, Call.STATE_HOLDING);
        assertCallState(mCall1, Call.STATE_HOLDING);
        assertCallState(mCall2, Call.STATE_HOLDING);

        conf.unhold();
        assertCallState(conf, Call.STATE_ACTIVE);
        assertCallState(mCall1, Call.STATE_ACTIVE);
        assertCallState(mCall2, Call.STATE_ACTIVE);
    }

    public void testConferenceMergeAndSwap() {
        if (!mShouldTestTelecom) {
            return;
        }
        final Call conf = mInCallService.getLastConferenceCall();
        assertCallState(conf, Call.STATE_ACTIVE);

        conf.mergeConference();
        assertCallDisplayName(mCall1, TestUtils.MERGE_CALLER_NAME);
        assertCallDisplayName(mCall2, TestUtils.MERGE_CALLER_NAME);
        assertConnectionCallDisplayName(mConferenceObject.getConnections().get(0),
                TestUtils.MERGE_CALLER_NAME);
        assertConnectionCallDisplayName(mConferenceObject.getConnections().get(1),
                TestUtils.MERGE_CALLER_NAME);

        conf.swapConference();
        assertCallDisplayName(mCall1, TestUtils.SWAP_CALLER_NAME);
        assertCallDisplayName(mCall2, TestUtils.SWAP_CALLER_NAME);
        assertConnectionCallDisplayName(mConferenceObject.getConnections().get(0),
                TestUtils.SWAP_CALLER_NAME);
        assertConnectionCallDisplayName(mConferenceObject.getConnections().get(1),
                TestUtils.SWAP_CALLER_NAME);

    }

    public void testConferenceSetters() {
        if (!mShouldTestTelecom) {
            return;
        }
        final Call conf = mInCallService.getLastConferenceCall();
        assertCallState(conf, Call.STATE_ACTIVE);

        placeAndVerifyCall();
        MockConnection newConnection = verifyConnectionForOutgoingCall(2);
        final Call newCall = mInCallService.getLastCall();

        ArrayList<Connection> connectionList = new ArrayList<>();
        connectionList.add(newConnection);
        ArrayList<Call> callList = new ArrayList<>();
        callList.add(newCall);

        assertFalse(conf.getDetails().can(Call.Details.CAPABILITY_MUTE));
        int capabilities = mConferenceObject.getConnectionCapabilities() |
                Connection.CAPABILITY_MUTE;
        mConferenceObject.setConnectionCapabilities(capabilities);
        assertCallCapability(conf, Call.Details.CAPABILITY_MUTE);

        assertFalse(conf.getConferenceableCalls().contains(newCall));
        mConferenceObject.setConferenceableConnections(connectionList);
        assertCallConferenceableList(conf, callList);

        mConferenceObject.setConnectionTime(0);

        Bundle extras = new Bundle();
        extras.putString(TelecomManager.EXTRA_CALL_DISCONNECT_MESSAGE, "Test");
        assertNull(conf.getDetails().getExtras());
        mConferenceObject.setExtras(extras);
        assertCallExtras(conf, TelecomManager.EXTRA_CALL_DISCONNECT_MESSAGE, "Test");

        StatusHints hints = new StatusHints("Test", null, null);
        assertNull(conf.getDetails().getStatusHints());
        mConferenceObject.setStatusHints(hints);
        assertCallStatusHints(conf, hints);

        assertFalse(conf.getChildren().contains(newCall));
        mConferenceObject.addConnection(newConnection);
        assertCallChildrenContains(conf, newCall, true);

        assertTrue(conf.getChildren().contains(newCall));
        mConferenceObject.removeConnection(newConnection);
        assertCallChildrenContains(conf, newCall, false);

        assertVideoState(conf, VideoProfile.STATE_AUDIO_ONLY);
        final MockVideoProvider mockVideoProvider = mConnection1.getMockVideoProvider();
        mConferenceObject.setVideoProvider(mConnection1, mockVideoProvider);
        mConferenceObject.setVideoState(mConnection1, VideoProfile.STATE_BIDIRECTIONAL);
        assertVideoState(conf, VideoProfile.STATE_BIDIRECTIONAL);

        // Dialing state is unsupported for conference calls. so, the state remains active.
        mConferenceObject.setDialing();
        // just assert call state is not dialing, the state remains as previous one.
        assertTrue(conf.getState() != Call.STATE_DIALING);

        mConferenceObject.setOnHold();
        assertCallState(conf, Call.STATE_HOLDING);

        mConferenceObject.setActive();
        assertCallState(conf, Call.STATE_ACTIVE);

        mConferenceObject.setDisconnected(new DisconnectCause(DisconnectCause.LOCAL));
        assertCallState(conf, Call.STATE_DISCONNECTED);

        // Destroy state is unsupported for conference calls. so, the state remains active.
        mConferenceObject.destroy();
        assertCallState(conf, Call.STATE_DISCONNECTED);
    }

    /**
     * Tests end to end propagation of the {@link Conference} properties to the associated
     * {@link Call}.
     */
    public void testConferenceProperties() {
        if (!mShouldTestTelecom) {
            return;
        }
        final Call conf = mInCallService.getLastConferenceCall();
        assertCallState(conf, Call.STATE_ACTIVE);

        int properties  = mConferenceObject.getConnectionProperties();
        properties |= Connection.PROPERTY_HAS_CDMA_VOICE_PRIVACY;

        mConferenceObject.setConnectionProperties(properties);

        // Wait for 2nd properties change; the first will be when the conference is marked with
        // Call.Details.PROPERTY_CONFERENCE.
        assertCallProperties(conf, Call.Details.PROPERTY_HAS_CDMA_VOICE_PRIVACY);
        assertTrue(conf.getDetails().hasProperty(Call.Details.PROPERTY_HAS_CDMA_VOICE_PRIVACY));
    }

    /**
     * Verifies {@link Conference#putExtras(Bundle)} calls are propagated to
     * {@link android.telecom.Call.Callback#onDetailsChanged(Call, Call.Details)}.
     */
    public void testConferencePutExtras() {
        if (!mShouldTestTelecom) {
            return;
        }
        final Call conf = mInCallService.getLastConferenceCall();
        assertCallState(conf, Call.STATE_ACTIVE);

        Bundle extras = new Bundle();
        extras.putString(TEST_EXTRA_KEY_1, TEST_EXTRA_VALUE_1);
        extras.putInt(TEST_EXTRA_KEY_2, TEST_EXTRA_VALUE_2);
        mConferenceObject.putExtras(extras);

        mOnExtrasChangedCounter.waitForCount(1);

        assertTrue(areBundlesEqual(extras, conf.getDetails().getExtras()));
    }

    /**
     * Verifies {@link Conference#removeExtras(List)} calls are propagated to
     * {@link android.telecom.Call.Callback#onDetailsChanged(Call, Call.Details)}.
     */
    public void testConferenceRemoveExtras() {
        if (!mShouldTestTelecom) {
            return;
        }
        final Call conf = mInCallService.getLastConferenceCall();
        assertCallState(conf, Call.STATE_ACTIVE);

        setupExtras();

        mConferenceObject.removeExtras(Arrays.asList(TEST_EXTRA_KEY_1));
        mOnExtrasChangedCounter.waitForCount(2);
        Bundle extras = mConferenceObject.getExtras();

        assertFalse(extras.containsKey(TEST_EXTRA_KEY_1));
        assertTrue(extras.containsKey(TEST_EXTRA_KEY_2));
    }

    /**
     * Verifies {@link Conference#removeExtras(String[])} calls are propagated to
     * {@link android.telecom.Call.Callback#onDetailsChanged(Call, Call.Details)}.
     */
    public void testConferenceRemoveExtras2() {
        if (!mShouldTestTelecom) {
            return;
        }
        final Call conf = mInCallService.getLastConferenceCall();
        assertCallState(conf, Call.STATE_ACTIVE);

        setupExtras();

        mConferenceObject.removeExtras(TEST_EXTRA_KEY_1, TEST_EXTRA_KEY_2);
        mOnExtrasChangedCounter.waitForCount(2);
        Bundle extras = mConferenceObject.getExtras();

        assertTrue(extras == null || extras.isEmpty());
    }

    private void setupExtras() {
        Bundle extras = new Bundle();
        extras.putString(TEST_EXTRA_KEY_1, TEST_EXTRA_VALUE_1);
        extras.putInt(TEST_EXTRA_KEY_2, TEST_EXTRA_VALUE_2);
        mConferenceObject.putExtras(extras);
        mOnExtrasChangedCounter.waitForCount(1);
    }

    /**
     * Verifies {@link android.telecom.Call#putExtras(Bundle)} changes are propagated to
     * {@link Conference#onExtrasChanged(Bundle)}.
     */
    public void testConferenceOnExtraschanged() {
        if (!mShouldTestTelecom) {
            return;
        }
        final Call conf = mInCallService.getLastConferenceCall();
        assertCallState(conf, Call.STATE_ACTIVE);

        Bundle extras = new Bundle();
        extras.putString(TEST_EXTRA_KEY_1, TEST_EXTRA_VALUE_1);
        extras.putInt(TEST_EXTRA_KEY_2, TEST_EXTRA_VALUE_2);
        conf.putExtras(extras);
        mConferenceVerficationObject.mOnExtrasChanged.waitForCount(1);

        assertTrue(areBundlesEqual(extras, mConferenceObject.getExtras()));
    }

    public void testConferenceAddAndRemoveConnection() {
        if (!mShouldTestTelecom) {
            return;
        }
        final Call conf = mInCallService.getLastConferenceCall();
        assertCallState(conf, Call.STATE_ACTIVE);

        placeAndVerifyCall();
        MockConnection newConnection = verifyConnectionForOutgoingCall(2);
        final Call newCall = mInCallService.getLastCall();

        ArrayList<Connection> connectionList = new ArrayList<>();
        connectionList.add(newConnection);
        ArrayList<Call> callList = new ArrayList<>();
        callList.add(newCall);

        assertFalse(conf.getChildren().contains(newCall));
        mConferenceObject.addConnection(newConnection);
        assertCallChildrenContains(conf, newCall, true);

        assertTrue(conf.getChildren().contains(newCall));
        mConferenceObject.removeConnection(newConnection);
        assertCallChildrenContains(conf, newCall, false);
    }

    public void testConferenceDTMFTone() {
        if (!mShouldTestTelecom) {
            return;
        }
        final Call conf = mInCallService.getLastConferenceCall();
        assertCallState(conf, Call.STATE_ACTIVE);

        assertTrue(((MockConference)mConferenceObject).getDtmfString().isEmpty());
        conf.playDtmfTone('1');
        assertDtmfString((MockConference)mConferenceObject, "1");
        conf.stopDtmfTone();
        assertDtmfString((MockConference)mConferenceObject, "1.");
        conf.playDtmfTone('3');
        assertDtmfString((MockConference)mConferenceObject, "1.3");
        conf.stopDtmfTone();
        assertDtmfString((MockConference)mConferenceObject, "1.3.");
    }

    private void verifyConferenceObject(Conference mConferenceObject, MockConnection connection1,
            MockConnection connection2) {
        assertNull(mConferenceObject.getCallAudioState());
        assertTrue(mConferenceObject.getConferenceableConnections().isEmpty());
        assertEquals(connection1.getConnectionCapabilities(),
                mConferenceObject.getConnectionCapabilities());
        assertEquals(connection1.getState(), mConferenceObject.getState());
        assertEquals(connection2.getState(), mConferenceObject.getState());
        assertTrue(mConferenceObject.getConnections().contains(connection1));
        assertTrue(mConferenceObject.getConnections().contains(connection2));
        assertEquals(connection1.getDisconnectCause(), mConferenceObject.getDisconnectCause());
        assertEquals(connection1.getExtras(), mConferenceObject.getExtras());
        assertEquals(connection1.getPhoneAccountHandle(), mConferenceObject.getPhoneAccountHandle());
        assertEquals(connection1.getStatusHints(), mConferenceObject.getStatusHints());
        assertEquals(VideoProfile.STATE_AUDIO_ONLY, mConferenceObject.getVideoState());
        assertNull(mConferenceObject.getVideoProvider());
    }

    private void addOutgoingCalls() {
        try {
            PhoneAccount account = setupConnectionService(
                    new MockConnectionService() {
                        @Override
                        public Connection onCreateOutgoingConnection(
                                PhoneAccountHandle connectionManagerPhoneAccount,
                                ConnectionRequest request) {
                            Connection connection = super.onCreateOutgoingConnection(
                                    connectionManagerPhoneAccount,
                                    request);
                            // Modify the connection object created with local values.
                            int capabilities = connection.getConnectionCapabilities();
                            connection.setConnectionCapabilities(capabilities | CONF_CAPABILITIES);
                            return connection;
                        }
                    }, FLAG_REGISTER | FLAG_ENABLE);
        } catch(Exception e) {
            fail("Error in setting up the connection services");
        }

        placeAndVerifyCall();
        mConnection1 = verifyConnectionForOutgoingCall(0);
        mInCallService = mInCallCallbacks.getService();
        mCall1 = mInCallService.getLastCall();
        assertCallState(mCall1, Call.STATE_DIALING);
        mConnection1.setActive();
        assertCallState(mCall1, Call.STATE_ACTIVE);

        placeAndVerifyCall();
        mConnection2 = verifyConnectionForOutgoingCall(1);
        mCall2 = mInCallService.getLastCall();
        assertCallState(mCall2, Call.STATE_DIALING);
        mConnection2.setActive();
        assertCallState(mCall2, Call.STATE_ACTIVE);

        setAndVerifyConferenceablesForOutgoingConnection(0);
        setAndVerifyConferenceablesForOutgoingConnection(1);
    }

    private void assertCallCapability(final Call call, final int capability) {
        waitUntilConditionIsTrueOrTimeout(
                new Condition() {
                    @Override
                    public Object expected() {
                        return true;
                    }

                    @Override
                    public Object actual() {
                        return call.getDetails().can(capability);
                    }
                },
                WAIT_FOR_STATE_CHANGE_TIMEOUT_MS,
                "Call should have capability " + capability
        );
    }

    private void assertCallConnectTime(final Call call, final int connectTime) {
        waitUntilConditionIsTrueOrTimeout(
                new Condition() {
                    @Override
                    public Object expected() {
                        return connectTime;
                    }

                    @Override
                    public Object actual() {
                        return call.getDetails().getConnectTimeMillis();
                    }
                },
                WAIT_FOR_STATE_CHANGE_TIMEOUT_MS,
                "Call should have connect time " + connectTime
        );
    }

    private void assertCallExtras(final Call call, final String key, final String value) {
        waitUntilConditionIsTrueOrTimeout(
                new Condition() {
                    @Override
                    public Object expected() {
                        return value;
                    }

                    @Override
                    public Object actual() {
                        return call.getDetails().getExtras() != null ?
                            call.getDetails().getExtras().getString(key) : null;
                    }
                },
                WAIT_FOR_STATE_CHANGE_TIMEOUT_MS,
                "Call should have extra " + key + "=" + value
        );
    }

    private void assertCallStatusHints(final Call call, final StatusHints hints) {
        waitUntilConditionIsTrueOrTimeout(
                new Condition() {
                    @Override
                    public Object expected() {
                        return hints;
                    }

                    @Override
                    public Object actual() {
                        return call.getDetails().getStatusHints();
                    }
                },
                WAIT_FOR_STATE_CHANGE_TIMEOUT_MS,
                "Call should have status hints " + hints
        );
    }

    private void assertCallChildrenContains(final Call call, final Call childrenCall,
                                            final boolean expected) {
        waitUntilConditionIsTrueOrTimeout(
                new Condition() {
                    @Override
                    public Object expected() {
                        return expected;
                    }

                    @Override
                    public Object actual() {
                        return call.getChildren().contains(childrenCall);
                    }
                },
                WAIT_FOR_STATE_CHANGE_TIMEOUT_MS,
                expected == true ? "Call should have child call " + childrenCall :
                        "Call should not have child call " + childrenCall
        );
    }

    private void assertVideoState(final Call call, final int videoState) {
        waitUntilConditionIsTrueOrTimeout(
                new Condition() {
                    @Override
                    public Object expected() {
                        return videoState;
                    }

                    @Override
                    public Object actual() {
                        return call.getDetails().getVideoState();
                    }
                },
                TestUtils.WAIT_FOR_STATE_CHANGE_TIMEOUT_MS,
                "Call should be in videoState " + videoState
        );
    }
}
