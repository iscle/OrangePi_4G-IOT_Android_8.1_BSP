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
import android.os.Handler;
import android.os.HandlerThread;
import android.telecom.Call;
import android.telecom.Connection;
import android.telecom.ConnectionRequest;
import android.telecom.DisconnectCause;
import android.telecom.PhoneAccountHandle;
import android.telecom.RemoteConference;
import android.telecom.RemoteConnection;
import android.telecom.TelecomManager;

import java.util.ArrayList;
import java.util.List;

/**
 * Extended suite of tests that use {@link CtsConnectionService} and {@link MockInCallService} to
 * verify the functionality of Remote Conferences.
 * We make 2 connections on the {@link CtsConnectionService} & we create 2 connections on the
 * {@link CtsRemoteConnectionService} via the {@link RemoteConnection} object. We store this
 * corresponding RemoteConnection object on the connections to plumb the modifications on
 * the connections in {@link CtsConnectionService} to the connections on
 * {@link CtsRemoteConnectionService}. Then we create a remote conference on the
 * {@link CtsRemoteConnectionService} and control it via the {@link RemoteConference}
 * object. The onConference method on the managerConnectionService will initiate a remote conference
 * creation on the remoteConnectionService and once that is completed, we create a local conference
 * on the managerConnectionService.
 */
public class RemoteConferenceTest extends BaseRemoteTelecomTest {

    public static final int CONF_CAPABILITIES = Connection.CAPABILITY_SEPARATE_FROM_CONFERENCE |
            Connection.CAPABILITY_DISCONNECT_FROM_CONFERENCE | Connection.CAPABILITY_HOLD |
            Connection.CAPABILITY_MERGE_CONFERENCE | Connection.CAPABILITY_SWAP_CONFERENCE;

    MockConnection mConnection1, mConnection2;
    MockConnection mRemoteConnection1, mRemoteConnection2;
    Call mCall1, mCall2;
    MockConference mConference, mRemoteConference;
    RemoteConference mRemoteConferenceObject;

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        if (mShouldTestTelecom) {
            addRemoteConferenceCall();
            verifyRemoteConferenceObject(mRemoteConferenceObject, mRemoteConference, mConference);
        }
    }

    public void testRemoteConferenceCreate() {
        if (!mShouldTestTelecom) {
            return;
        }
        final Call confCall = mInCallCallbacks.getService().getLastConferenceCall();
        assertCallState(confCall, Call.STATE_ACTIVE);

        if (mCall1.getParent() != confCall || mCall2.getParent() != confCall) {
            fail("The 2 participating calls should contain the conference call as its parent");
        }
        if (!(confCall.getChildren().contains(mCall1) && confCall.getChildren().contains(mCall2))) {
            fail("The conference call should contain the 2 participating calls as its children");
        }

        assertConnectionState(mConnection1, Connection.STATE_ACTIVE);
        assertConnectionState(mConnection2, Connection.STATE_ACTIVE);
        assertConnectionState(mRemoteConnection1, Connection.STATE_ACTIVE);
        assertConnectionState(mRemoteConnection2, Connection.STATE_ACTIVE);
        assertConferenceState(mConference, Connection.STATE_ACTIVE);
        assertConferenceState(mRemoteConference, Connection.STATE_ACTIVE);
        assertRemoteConferenceState(mRemoteConferenceObject, Connection.STATE_ACTIVE);
    }

    public void testRemoteConferenceSplit() {
        if (!mShouldTestTelecom) {
            return;
        }
        final Call confCall = mInCallCallbacks.getService().getLastConferenceCall();
        assertCallState(confCall, Call.STATE_ACTIVE);

        if (!(mCall1.getParent() == confCall) && (confCall.getChildren().contains(mCall1))) {
            fail("Call 1 not conferenced");
        }
        assertTrue(mConference.getConnections().contains(mConnection1));
        assertTrue(mRemoteConference.getConnections().contains(mRemoteConnection1));

        splitFromConferenceCall(mCall1);

        if ((mCall1.getParent() == confCall) || (confCall.getChildren().contains(mCall1))) {
            fail("Call 1 should not be still conferenced");
        }
        assertFalse(mConference.getConnections().contains(mConnection1));
        assertFalse(mRemoteConference.getConnections().contains(mRemoteConnection1));
    }

    public void testRemoteConferenceHoldAndUnhold() {
        if (!mShouldTestTelecom) {
            return;
        }
        final Call confCall = mInCallCallbacks.getService().getLastConferenceCall();
        assertCallState(confCall, Call.STATE_ACTIVE);

        confCall.hold();
        assertCallState(confCall, Call.STATE_HOLDING);
        assertCallState(mCall1, Call.STATE_HOLDING);
        assertCallState(mCall2, Call.STATE_HOLDING);
        assertConnectionState(mConnection1, Connection.STATE_HOLDING);
        assertConnectionState(mConnection2, Connection.STATE_HOLDING);
        assertConnectionState(mRemoteConnection1, Connection.STATE_HOLDING);
        assertConnectionState(mRemoteConnection2, Connection.STATE_HOLDING);
        assertConferenceState(mConference, Connection.STATE_HOLDING);
        assertConferenceState(mRemoteConference, Connection.STATE_HOLDING);
        assertRemoteConferenceState(mRemoteConferenceObject, Connection.STATE_HOLDING);

        confCall.unhold();
        assertCallState(confCall, Call.STATE_ACTIVE);
        assertCallState(mCall1, Call.STATE_ACTIVE);
        assertCallState(mCall2, Call.STATE_ACTIVE);
        assertConnectionState(mConnection1, Connection.STATE_ACTIVE);
        assertConnectionState(mConnection2, Connection.STATE_ACTIVE);
        assertConnectionState(mRemoteConnection1, Connection.STATE_ACTIVE);
        assertConnectionState(mRemoteConnection2, Connection.STATE_ACTIVE);
        assertConferenceState(mConference, Connection.STATE_ACTIVE);
        assertConferenceState(mRemoteConference, Connection.STATE_ACTIVE);
        assertRemoteConferenceState(mRemoteConferenceObject, Connection.STATE_ACTIVE);
    }

    public void testRemoteConferenceMergeAndSwap() {
        if (!mShouldTestTelecom) {
            return;
        }
        final Call confCall = mInCallCallbacks.getService().getLastConferenceCall();
        assertCallState(confCall, Call.STATE_ACTIVE);

        confCall.mergeConference();
        assertCallDisplayName(mCall1, TestUtils.MERGE_CALLER_NAME);
        assertCallDisplayName(mCall2, TestUtils.MERGE_CALLER_NAME);
        assertConnectionCallDisplayName(mConnection1,
                TestUtils.MERGE_CALLER_NAME);
        assertConnectionCallDisplayName(mConnection2,
                TestUtils.MERGE_CALLER_NAME);
        assertConnectionCallDisplayName(mRemoteConnection1,
                TestUtils.MERGE_CALLER_NAME);
        assertConnectionCallDisplayName(mRemoteConnection2,
                TestUtils.MERGE_CALLER_NAME);

        confCall.swapConference();
        assertCallDisplayName(mCall1, TestUtils.SWAP_CALLER_NAME);
        assertCallDisplayName(mCall2, TestUtils.SWAP_CALLER_NAME);
        assertConnectionCallDisplayName(mConnection1,
                TestUtils.SWAP_CALLER_NAME);
        assertConnectionCallDisplayName(mConnection2,
                TestUtils.SWAP_CALLER_NAME);
        assertConnectionCallDisplayName(mRemoteConnection1,
                TestUtils.SWAP_CALLER_NAME);
        assertConnectionCallDisplayName(mRemoteConnection2,
                TestUtils.SWAP_CALLER_NAME);
    }

    public void testRemoteConferenceDTMFTone() {
        if (!mShouldTestTelecom) {
            return;
        }
        final Call confCall = mInCallCallbacks.getService().getLastConferenceCall();
        assertCallState(confCall, Call.STATE_ACTIVE);

        assertTrue(mConference.getDtmfString().isEmpty());
        assertTrue(mRemoteConference.getDtmfString().isEmpty());
        confCall.playDtmfTone('1');
        assertDtmfString(mConference, "1");
        assertDtmfString(mRemoteConference, "1");
        confCall.stopDtmfTone();
        assertDtmfString(mConference, "1.");
        assertDtmfString(mRemoteConference, "1.");
        confCall.playDtmfTone('3');
        assertDtmfString(mConference, "1.3");
        assertDtmfString(mRemoteConference, "1.3");
        confCall.stopDtmfTone();
        assertDtmfString(mConference, "1.3.");
        assertDtmfString(mRemoteConference, "1.3.");
    }

    public void testRemoteConferenceCallbacks_StateChange() {
        if (!mShouldTestTelecom) {
            return;
        }
        Handler handler = setupRemoteConferenceCallbacksTest();

        final InvokeCounter callbackInvoker =
                new InvokeCounter("testRemoteConferenceCallbacks_StateChange");
        RemoteConference.Callback callback;

        callback = new RemoteConference.Callback() {
            @Override
            public void onStateChanged(RemoteConference conference, int oldState, int newState) {
                super.onStateChanged(conference, oldState, newState);
                callbackInvoker.invoke(conference, oldState, newState);
            }
        };
        mRemoteConferenceObject.registerCallback(callback, handler);
        mRemoteConference.setOnHold();
        callbackInvoker.waitForCount(1, WAIT_FOR_STATE_CHANGE_TIMEOUT_MS);
        assertEquals(mRemoteConferenceObject, callbackInvoker.getArgs(0)[0]);
        assertEquals(Connection.STATE_ACTIVE, callbackInvoker.getArgs(0)[1]);
        assertEquals(Connection.STATE_HOLDING, callbackInvoker.getArgs(0)[2]);
        mRemoteConferenceObject.unregisterCallback(callback);
    }

    public void testRemoteConferenceCallbacks_Disconnect() {
        if (!mShouldTestTelecom) {
            return;
        }
        Handler handler = setupRemoteConferenceCallbacksTest();

        final InvokeCounter callbackInvoker =
                new InvokeCounter("testRemoteConferenceCallbacks_Disconnect");
        RemoteConference.Callback callback;

        callback = new RemoteConference.Callback() {
            @Override
            public void onDisconnected(RemoteConference conference,
                                       DisconnectCause disconnectCause) {
                super.onDisconnected(conference, disconnectCause);
                callbackInvoker.invoke(conference, disconnectCause);
            }
        };
        mRemoteConferenceObject.registerCallback(callback, handler);
        DisconnectCause cause = new DisconnectCause(DisconnectCause.LOCAL);
        mRemoteConference.setDisconnected(cause);
        callbackInvoker.waitForCount(1, WAIT_FOR_STATE_CHANGE_TIMEOUT_MS);
        assertEquals(mRemoteConferenceObject, callbackInvoker.getArgs(0)[0]);
        assertEquals(cause, callbackInvoker.getArgs(0)[1]);
        mRemoteConferenceObject.unregisterCallback(callback);
    }

    public void testRemoteConferenceCallbacks_ConnectionAdd() {
        if (!mShouldTestTelecom) {
            return;
        }
        Handler handler = setupRemoteConferenceCallbacksTest();

        final InvokeCounter callbackInvoker =
                new InvokeCounter("testRemoteConferenceCallbacks_ConnectionAdd");
        RemoteConference.Callback callback;

        callback = new RemoteConference.Callback() {
            @Override
            public void onConnectionAdded(RemoteConference conference,
                                          RemoteConnection connection) {
                super.onConnectionAdded(conference, connection);
                callbackInvoker.invoke(conference, connection);
            }
        };
        mRemoteConferenceObject.registerCallback(callback, handler);
        placeAndVerifyCall();
        RemoteConnection newRemoteConnectionObject =
                verifyConnectionForOutgoingCall(1).getRemoteConnection();
        MockConnection newConnection = verifyConnectionForOutgoingCallOnRemoteCS(2);
        mRemoteConference.addConnection(newConnection);
        callbackInvoker.waitForCount(1, WAIT_FOR_STATE_CHANGE_TIMEOUT_MS);
        assertEquals(mRemoteConferenceObject, callbackInvoker.getArgs(0)[0]);
        // No "equals" method in RemoteConnection
        //assertEquals(newRemoteConnectionObject, callbackInvoker.getArgs(0)[1]);
        mRemoteConferenceObject.unregisterCallback(callback);
    }

    public void testRemoteConferenceCallbacks_ConnectionRemove() {
        if (!mShouldTestTelecom) {
            return;
        }
        Handler handler = setupRemoteConferenceCallbacksTest();

        final InvokeCounter callbackInvoker =
                new InvokeCounter("testRemoteConferenceCallbacks_ConnectionRemove");
        RemoteConference.Callback callback;

        callback = new RemoteConference.Callback() {
            @Override
            public void onConnectionRemoved(RemoteConference conference,
                                            RemoteConnection connection) {
                super.onConnectionRemoved(conference, connection);
                callbackInvoker.invoke(conference, connection);
            }
        };
        mRemoteConferenceObject.registerCallback(callback, handler);
        placeAndVerifyCall();
        RemoteConnection newRemoteConnectionObject =
                verifyConnectionForOutgoingCall(1).getRemoteConnection();
        MockConnection newConnection = verifyConnectionForOutgoingCallOnRemoteCS(2);
        mRemoteConference.addConnection(newConnection);
        mRemoteConference.removeConnection(newConnection);
        callbackInvoker.waitForCount(1, WAIT_FOR_STATE_CHANGE_TIMEOUT_MS);
        assertEquals(mRemoteConferenceObject, callbackInvoker.getArgs(0)[0]);
        //assertEquals(newRemoteConnectionObject, callbackInvoker.getArgs(0)[1]);
        // No "equals" method in RemoteConnection
        mRemoteConferenceObject.unregisterCallback(callback);
    }

    public void testRemoteConferenceCallbacks_ConnectionCapabilities() {
        if (!mShouldTestTelecom) {
            return;
        }
        Handler handler = setupRemoteConferenceCallbacksTest();

        final InvokeCounter callbackInvoker =
                new InvokeCounter("testRemoteConferenceCallbacks_ConnectionCapabilities");
        RemoteConference.Callback callback;

        callback = new RemoteConference.Callback() {
            @Override
            public void onConnectionCapabilitiesChanged(
                    RemoteConference conference,
                    int connectionCapabilities) {
                super.onConnectionCapabilitiesChanged(conference, connectionCapabilities);
                callbackInvoker.invoke(conference, connectionCapabilities);
            }
        };
        mRemoteConferenceObject.registerCallback(callback, handler);
        int capabilities = mRemoteConference.getConnectionCapabilities() | Connection.CAPABILITY_MUTE;
        mRemoteConference.setConnectionCapabilities(capabilities);
        callbackInvoker.waitForCount(1, WAIT_FOR_STATE_CHANGE_TIMEOUT_MS);
        assertEquals(mRemoteConferenceObject, callbackInvoker.getArgs(0)[0]);
        assertEquals(capabilities, callbackInvoker.getArgs(0)[1]);
        mRemoteConferenceObject.unregisterCallback(callback);
    }

    public void testRemoteConferenceCallbacks_ConnectionProperties() {
        if (!mShouldTestTelecom) {
            return;
        }
        Handler handler = setupRemoteConferenceCallbacksTest();

        final InvokeCounter callbackInvoker =
                new InvokeCounter("testRemoteConferenceCallbacks_ConnectionProperties");
        RemoteConference.Callback callback;

        callback = new RemoteConference.Callback() {
            @Override
            public void onConnectionPropertiesChanged(
                    RemoteConference conference,
                    int connectionProperties) {
                super.onConnectionPropertiesChanged(conference, connectionProperties);
                callbackInvoker.invoke(conference, connectionProperties);
            }
        };
        mRemoteConferenceObject.registerCallback(callback, handler);
        int properties = mRemoteConference.getConnectionCapabilities()
                | Connection.PROPERTY_IS_EXTERNAL_CALL;
        mRemoteConference.setConnectionProperties(properties);
        callbackInvoker.waitForCount(1, WAIT_FOR_STATE_CHANGE_TIMEOUT_MS);
        assertEquals(mRemoteConferenceObject, callbackInvoker.getArgs(0)[0]);
        assertEquals(properties, callbackInvoker.getArgs(0)[1]);
        mRemoteConferenceObject.unregisterCallback(callback);
    }

    public void testRemoteConferenceCallbacks_ConferenceableConnections() {
        if (!mShouldTestTelecom) {
            return;
        }
        Handler handler = setupRemoteConferenceCallbacksTest();

        final InvokeCounter callbackInvoker =
                new InvokeCounter("testRemoteConferenceCallbacks_ConferenceableConnections");
        RemoteConference.Callback callback;

        callback = new RemoteConference.Callback() {
            @Override
            public void onConferenceableConnectionsChanged(
                    RemoteConference conference,
                    List<RemoteConnection> conferenceableConnections) {
                super.onConferenceableConnectionsChanged(conference, conferenceableConnections);
                callbackInvoker.invoke(conference, conferenceableConnections);
            }
        };
        mRemoteConferenceObject.registerCallback(callback, handler);
        placeAndVerifyCall();
        RemoteConnection newRemoteConnectionObject =
            verifyConnectionForOutgoingCall(1).getRemoteConnection();
        MockConnection newConnection = verifyConnectionForOutgoingCallOnRemoteCS(1);
        ArrayList<Connection> confList = new ArrayList<>();
        confList.add(newConnection);
        mRemoteConference.setConferenceableConnections(confList);
        callbackInvoker.waitForCount(1, WAIT_FOR_STATE_CHANGE_TIMEOUT_MS);
        assertEquals(mRemoteConferenceObject, callbackInvoker.getArgs(0)[0]);
        // No "equals" method in RemoteConnection
        //assertTrue(((List<RemoteConnection>)callbackInvoker.getArgs(0)[1]).contains(
                //newRemoteConnectionObject));
        mRemoteConferenceObject.unregisterCallback(callback);
    }

    public void testRemoteConferenceCallbacks_Destroy() {
        if (!mShouldTestTelecom) {
            return;
        }
        Handler handler = setupRemoteConferenceCallbacksTest();

        final InvokeCounter callbackInvoker =
                new InvokeCounter("testRemoteConferenceCallbacks_Destroy");
        RemoteConference.Callback callback;

        callback = new RemoteConference.Callback() {
            @Override
            public void onDestroyed(RemoteConference conference) {
                super.onDestroyed(conference);
                callbackInvoker.invoke(conference);
            }
        };
        mRemoteConferenceObject.registerCallback(callback, handler);
        mRemoteConference.destroy();
        callbackInvoker.waitForCount(1, WAIT_FOR_STATE_CHANGE_TIMEOUT_MS);
        assertEquals(mRemoteConferenceObject, callbackInvoker.getArgs(0)[0]);
        mRemoteConferenceObject.unregisterCallback(callback);
    }


    public void testRemoteConferenceCallbacks_Extras() {
        if (!mShouldTestTelecom) {
            return;
        }
        Handler handler = setupRemoteConferenceCallbacksTest();

        final InvokeCounter callbackInvoker =
                new InvokeCounter("testRemoteConferenceCallbacks_Extras");
        RemoteConference.Callback callback;

        callback = new RemoteConference.Callback() {
            @Override
            public void onExtrasChanged(RemoteConference conference, Bundle extras) {
                super.onExtrasChanged(conference, extras);
                callbackInvoker.invoke(conference, extras);
            }
        };
        mRemoteConferenceObject.registerCallback(callback, handler);
        Bundle extras = new Bundle();
        extras.putString(TelecomManager.EXTRA_CALL_DISCONNECT_MESSAGE, "Test");
        mRemoteConference.setExtras(extras);
        callbackInvoker.waitForCount(1, WAIT_FOR_STATE_CHANGE_TIMEOUT_MS);
        assertEquals(mRemoteConferenceObject, callbackInvoker.getArgs(0)[0]);
        assertTrue(((Bundle) callbackInvoker.getArgs(0)[1]).containsKey(
                TelecomManager.EXTRA_CALL_DISCONNECT_MESSAGE));
        mRemoteConferenceObject.unregisterCallback(callback);
    }

    private void verifyRemoteConferenceObject(RemoteConference remoteConferenceObject,
            MockConference remoteConference, MockConference conference) {
        assertEquals(remoteConference.getConnectionCapabilities(),
                remoteConferenceObject.getConnectionCapabilities());
        assertTrue(remoteConferenceObject.getConferenceableConnections().isEmpty());
        List<RemoteConnection> remoteConnections = new ArrayList<>();
        for (Connection c: conference.getConnections()) {
            remoteConnections.add(((MockConnection)c).getRemoteConnection());
        }
        assertEquals(remoteConnections, remoteConferenceObject.getConnections());
        assertEquals(remoteConference.getDisconnectCause(),
                remoteConferenceObject.getDisconnectCause());
        assertTrue(areBundlesEqual(remoteConferenceObject.getExtras(), conference.getExtras()));
    }

    private void addRemoteConnectionOutgoingCalls() {
        try {
            MockConnectionService managerConnectionService = new MockConnectionService() {
                @Override
                public Connection onCreateOutgoingConnection(
                        PhoneAccountHandle connectionManagerPhoneAccount,
                        ConnectionRequest request) {
                    MockConnection connection = (MockConnection)super.onCreateOutgoingConnection(
                            connectionManagerPhoneAccount, request);
                    ConnectionRequest remoteRequest = new ConnectionRequest(
                            TEST_REMOTE_PHONE_ACCOUNT_HANDLE,
                            request.getAddress(),
                            request.getExtras());
                    RemoteConnection remoteConnection =
                            CtsConnectionService.createRemoteOutgoingConnectionToTelecom(
                                    TEST_REMOTE_PHONE_ACCOUNT_HANDLE, remoteRequest);
                    connection.setRemoteConnection(remoteConnection);
                    // Modify the connection object created with local values.
                    int capabilities = connection.getConnectionCapabilities();
                    connection.setConnectionCapabilities(capabilities | CONF_CAPABILITIES);
                    return connection;
                }
                @Override
                public void onConference(Connection connection1, Connection connection2) {
                    /**
                     * Fetch the corresponding remoteConnection objects and instantiate a remote
                     * conference creation on the remoteConnectionService instead of this
                     * managerConnectionService.
                     */
                    RemoteConnection remoteConnection1 =
                            ((MockConnection)connection1).getRemoteConnection();
                    RemoteConnection remoteConnection2 =
                            ((MockConnection)connection2).getRemoteConnection();
                    if (remoteConnection1.getConference() == null &&
                            remoteConnection2.getConference() == null) {
                        conferenceRemoteConnections(remoteConnection1, remoteConnection2);
                    }

                    if (connection1.getState() == Connection.STATE_HOLDING){
                        connection1.setActive();
                    }
                    if(connection2.getState() == Connection.STATE_HOLDING){
                        connection2.setActive();
                    }
                }
                @Override
                public void onRemoteConferenceAdded(RemoteConference remoteConference) {
                    /**
                     * Now that the remote conference has been created,
                     * let's create a local conference on this ConnectionService.
                     */
                    MockConference conference = new MockConference(mConnection1, mConnection2);
                    conference.setRemoteConference(remoteConference);
                    CtsConnectionService.addConferenceToTelecom(conference);
                    conferences.add(conference);
                    lock.release();
                }
            };
            /**
             * We want the conference to be instantiated on the remoteConnectionService registered
             * with telecom.
             */
            MockConnectionService remoteConnectionService= new MockConnectionService() {
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
                @Override
                public void onConference(Connection connection1, Connection connection2) {
                    // Make sure that these connections are already not conferenced.
                    if (connection1.getConference() == null &&
                            connection2.getConference() == null) {
                        MockConference conference = new MockConference(
                                (MockConnection)connection1, (MockConnection)connection2);
                        CtsRemoteConnectionService.addConferenceToTelecom(conference);
                        conferences.add(conference);

                        if (connection1.getState() == Connection.STATE_HOLDING){
                            connection1.setActive();
                        }
                        if(connection2.getState() == Connection.STATE_HOLDING){
                            connection2.setActive();
                        }

                        lock.release();
                    }
                }
            };
            setupConnectionServices(managerConnectionService, remoteConnectionService,
                    FLAG_REGISTER | FLAG_ENABLE);
        } catch(Exception e) {
            fail("Error in setting up the connection services: " + e.toString());
        }

        placeAndVerifyCall();
        mConnection1 = verifyConnectionForOutgoingCall(0);
        mRemoteConnection1 = verifyConnectionForOutgoingCallOnRemoteCS(0);
        mCall1 = mInCallCallbacks.getService().getLastCall();
        assertCallState(mCall1, Call.STATE_DIALING);
        mConnection1.setActive();
        mRemoteConnection1.setActive();
        assertCallState(mCall1, Call.STATE_ACTIVE);

        placeAndVerifyCall();
        mConnection2 = verifyConnectionForOutgoingCall(1);
        mRemoteConnection2 = verifyConnectionForOutgoingCallOnRemoteCS(1);
        mCall2 = mInCallCallbacks.getService().getLastCall();
        assertCallState(mCall2, Call.STATE_DIALING);
        mConnection2.setActive();
        mRemoteConnection2.setActive();
        assertCallState(mCall2, Call.STATE_ACTIVE);

        setAndVerifyConferenceablesForOutgoingConnection(0);
        setAndVerifyConferenceablesForOutgoingConnection(1);
        setAndVerifyConferenceablesForOutgoingConnectionOnRemoteCS(0);
        setAndVerifyConferenceablesForOutgoingConnectionOnRemoteCS(1);
    }

    private void addRemoteConferenceCall() {
        addRemoteConnectionOutgoingCalls();
        /**
         * We've 2 connections on the local connectionService which have 2 corresponding
         * connections on the remoteConnectionService controlled via 2 RemoteConnection objects
         * on the connectionService. We now create a conference on the local two connections
         * which triggers a creation of conference on the remoteConnectionService via the
         * RemoteConference object.
         */

        addConferenceCall(mCall1, mCall2);
        mConference = verifyConferenceForOutgoingCall();
        mRemoteConference = verifyConferenceForOutgoingCallOnRemoteCS();
        mRemoteConferenceObject = mConference.getRemoteConference();
        mRemoteConnection1 = (MockConnection)mRemoteConference.getConnections().get(0);
        mRemoteConnection2 = (MockConnection)mRemoteConference.getConnections().get(1);
    }

    private Handler setupRemoteConferenceCallbacksTest() {
        final Call confCall = mInCallCallbacks.getService().getLastConferenceCall();
        assertCallState(confCall, Call.STATE_ACTIVE);

        // Create a looper thread for the callbacks.
        HandlerThread workerThread = new HandlerThread("CallbackThread");
        workerThread.start();
        Handler handler = new Handler(workerThread.getLooper());
        return handler;
    }
}
