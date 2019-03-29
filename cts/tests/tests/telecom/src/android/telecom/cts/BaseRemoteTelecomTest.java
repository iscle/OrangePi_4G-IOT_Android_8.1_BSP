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

import static org.hamcrest.CoreMatchers.not;
import static org.hamcrest.CoreMatchers.equalTo;
import static org.junit.Assert.assertThat;

import android.content.ComponentName;
import android.telecom.Connection;
import android.telecom.PhoneAccount;
import android.telecom.PhoneAccountHandle;
import android.telecom.RemoteConference;
import android.telecom.RemoteConnection;
import android.text.TextUtils;
import android.util.Log;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * Base class for Telecom CTS tests that require a {@link CtsConnectionService} and
 * {@link CtsRemoteConnectionService} to verify Telecom functionality. This class
 * extends from the {@link BaseTelecomTestWithMockServices} and should be extended
 * for all RemoteConnection/RemoteConferencTest.
 */
public class BaseRemoteTelecomTest extends BaseTelecomTestWithMockServices {

    public static final PhoneAccountHandle TEST_REMOTE_PHONE_ACCOUNT_HANDLE =
            new PhoneAccountHandle(new ComponentName(PACKAGE, REMOTE_COMPONENT), REMOTE_ACCOUNT_ID);
    public static final String TEST_REMOTE_PHONE_ACCOUNT_ADDRESS = "tel:666-TEST";

    MockConnectionService remoteConnectionService = null;

    @Override
    protected void tearDown() throws Exception {
        if (mShouldTestTelecom) {
            tearDownRemoteConnectionService(TEST_REMOTE_PHONE_ACCOUNT_HANDLE);
        }
        super.tearDown();
    }

    protected void setupConnectionServices(MockConnectionService connectionService,
            MockConnectionService remoteConnectionService, int flags) throws Exception {
        // Setup the primary connection service first
        setupConnectionService(connectionService, flags);
        setupRemoteConnectionService(remoteConnectionService, flags);
    }

    protected void setupRemoteConnectionService(MockConnectionService remoteConnectionService,
            int flags) throws Exception {
        if (remoteConnectionService != null) {
            this.remoteConnectionService = remoteConnectionService;
        } else {
            // Generate a vanilla mock connection service, if not provided.
            this.remoteConnectionService = new MockConnectionService();
        }
        CtsRemoteConnectionService.setUp(TEST_REMOTE_PHONE_ACCOUNT_HANDLE,
                this.remoteConnectionService);

        if ((flags & FLAG_REGISTER) != 0) {
            // This needs SIM subscription, so register via adb commands to get system permission.
            TestUtils.registerSimPhoneAccount(getInstrumentation(),
                    TEST_REMOTE_PHONE_ACCOUNT_HANDLE,
                    REMOTE_ACCOUNT_LABEL,
                    TEST_REMOTE_PHONE_ACCOUNT_ADDRESS);
            // Wait till the adb commands have executed and account is in Telecom database.
            assertPhoneAccountRegistered(TEST_REMOTE_PHONE_ACCOUNT_HANDLE);
        }
        if ((flags & FLAG_ENABLE) != 0) {
            TestUtils.enablePhoneAccount(getInstrumentation(), TEST_REMOTE_PHONE_ACCOUNT_HANDLE);
            // Wait till the adb commands have executed and account is enabled in Telecom database.
            assertPhoneAccountEnabled(TEST_REMOTE_PHONE_ACCOUNT_HANDLE);
        }
    }

    protected void tearDownRemoteConnectionService(PhoneAccountHandle remoteAccountHandle)
            throws Exception {
        assertNumConnections(this.remoteConnectionService, 0);
        mTelecomManager.unregisterPhoneAccount(remoteAccountHandle);
        CtsRemoteConnectionService.tearDown();
        //Telecom doesn't unbind the remote connection service at the end of all calls today.
        //assertCtsRemoteConnectionServiceUnbound();
        this.remoteConnectionService = null;
    }

    MockConnection verifyConnectionForOutgoingCallOnRemoteCS() {
        // Assuming only 1 connection present
        return verifyConnectionForOutgoingCallOnRemoteCS(0);
    }

    MockConnection verifyConnectionForOutgoingCallOnRemoteCS(int connectionIndex) {
        try {
            if (!remoteConnectionService.lock.tryAcquire(TestUtils.WAIT_FOR_STATE_CHANGE_TIMEOUT_MS,
                    TimeUnit.MILLISECONDS)) {
                fail("No outgoing call connection requested by Telecom");
            }
        } catch (InterruptedException e) {
            Log.i(TAG, "Test interrupted!");
        }

        assertThat("Telecom should create outgoing connection for remote outgoing call",
                remoteConnectionService.outgoingConnections.size(), not(equalTo(0)));
        assertEquals("Telecom should not create incoming connections for remote outgoing calls",
                0, remoteConnectionService.incomingConnections.size());
        MockConnection connection = remoteConnectionService.outgoingConnections.get(connectionIndex);
        return connection;
    }

    MockConnection verifyConnectionForIncomingCallOnRemoteCS() {
        // Assuming only 1 connection present
        return verifyConnectionForIncomingCallOnRemoteCS(0);
    }

    MockConnection verifyConnectionForIncomingCallOnRemoteCS(int connectionIndex) {
        try {
            if (!remoteConnectionService.lock.tryAcquire(TestUtils.WAIT_FOR_STATE_CHANGE_TIMEOUT_MS,
                    TimeUnit.MILLISECONDS)) {
                fail("No outgoing call connection requested by Telecom");
            }
        } catch (InterruptedException e) {
            Log.i(TAG, "Test interrupted!");
        }

        assertThat("Telecom should create incoming connections for remote incoming calls",
                remoteConnectionService.incomingConnections.size(), not(equalTo(0)));
        assertEquals("Telecom should not create outgoing connections for remote incoming calls",
                0, remoteConnectionService.outgoingConnections.size());
        MockConnection connection = remoteConnectionService.incomingConnections.get(connectionIndex);
        setAndVerifyConnectionForIncomingCall(connection);
        return connection;
    }

    void setAndVerifyConferenceablesForOutgoingConnectionOnRemoteCS(int connectionIndex) {
        assertEquals("Lock should have no permits!", 0, mInCallCallbacks.lock.availablePermits());
        /**
         * Set the conferenceable connections on the given connection and it's remote connection
         * counterpart.
         */
        // Make all other outgoing connections as conferenceable with this remote connection.
        MockConnection connection = remoteConnectionService.outgoingConnections.get(connectionIndex);
        List<Connection> confConnections =
                new ArrayList<>(remoteConnectionService.outgoingConnections.size());
        for (Connection c : remoteConnectionService.outgoingConnections) {
            if (c != connection) {
                confConnections.add(c);
            }
        }
        connection.setConferenceableConnections(confConnections);
        assertEquals(connection.getConferenceables(), confConnections);
    }

    MockConference verifyConferenceForOutgoingCallOnRemoteCS() {
        try {
            if (!remoteConnectionService.lock.tryAcquire(TestUtils.WAIT_FOR_STATE_CHANGE_TIMEOUT_MS,
                    TimeUnit.MILLISECONDS)) {
                fail("No outgoing conference requested by Telecom");
            }
        } catch (InterruptedException e) {
            Log.i(TAG, "Test interrupted!");
        }
        // Return the newly created conference object to the caller
        MockConference conference = remoteConnectionService.conferences.get(0);
        setAndVerifyConferenceForOutgoingCall(conference);
        return conference;
    }

    void assertRemoteConnectionState(final RemoteConnection connection, final int state) {
        waitUntilConditionIsTrueOrTimeout(
                new Condition() {
                    @Override
                    public Object expected() {
                        return state;
                    }

                    @Override
                    public Object actual() {
                        return connection.getState();
                    }
                },
                WAIT_FOR_STATE_CHANGE_TIMEOUT_MS,
                "Remote Connection should be in state " + state
        );
    }

    void assertRemoteConferenceState(final RemoteConference conference, final int state) {
        waitUntilConditionIsTrueOrTimeout(
                new Condition() {
                    @Override
                    public Object expected() {
                        return state;
                    }

                    @Override
                    public Object actual() {
                        return conference.getState();
                    }
                },
                WAIT_FOR_STATE_CHANGE_TIMEOUT_MS,
                "Remote Conference should be in state " + state
        );
    }

    void assertCtsRemoteConnectionServiceUnbound() {
        waitUntilConditionIsTrueOrTimeout(
                new Condition() {
                    @Override
                    public Object expected(){
                        return true;
                    }

                    @Override
                    public Object actual() {
                        return CtsRemoteConnectionService.isServiceUnbound();
                    }
                },
                WAIT_FOR_STATE_CHANGE_TIMEOUT_MS,
                "CtsRemoteConnectionService not yet unbound!"
        );
    }
}
