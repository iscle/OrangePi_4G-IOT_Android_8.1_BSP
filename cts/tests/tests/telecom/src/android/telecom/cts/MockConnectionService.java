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

import android.telecom.Connection;
import android.telecom.ConnectionRequest;
import android.telecom.ConnectionService;
import android.telecom.PhoneAccountHandle;
import android.telecom.RemoteConference;
import android.telecom.RemoteConnection;
import android.telecom.TelecomManager;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Semaphore;

/**
 * Default implementation of a {@link CtsConnectionService}. This is used for the majority
 * of Telecom CTS tests that simply require that a outgoing call is placed, or incoming call is
 * received.
 */
public class MockConnectionService extends ConnectionService {
    public static final int CONNECTION_PRESENTATION =  TelecomManager.PRESENTATION_ALLOWED;

    /**
     * Used to control whether the {@link MockVideoProvider} will be created when connections are
     * created.  Used by {@link VideoCallTest#testVideoCallDelayProvider()} to test scenario where
     * the {@link MockVideoProvider} is not created immediately when the Connection is created.
     */
    private boolean mCreateVideoProvider = true;

    public Semaphore lock = new Semaphore(0);
    public List<MockConnection> outgoingConnections = new ArrayList<MockConnection>();
    public List<MockConnection> incomingConnections = new ArrayList<MockConnection>();
    public List<RemoteConnection> remoteConnections = new ArrayList<RemoteConnection>();
    public List<MockConference> conferences = new ArrayList<MockConference>();
    public List<RemoteConference> remoteConferences = new ArrayList<RemoteConference>();

    @Override
    public Connection onCreateOutgoingConnection(PhoneAccountHandle connectionManagerPhoneAccount,
            ConnectionRequest request) {
        final MockConnection connection = new MockConnection();
        connection.setAddress(request.getAddress(), CONNECTION_PRESENTATION);
        connection.setPhoneAccountHandle(connectionManagerPhoneAccount);
        connection.setConnectionCapabilities(Connection.CAPABILITY_SUPPORT_HOLD |
                Connection.CAPABILITY_HOLD);
        if (mCreateVideoProvider) {
            connection.createMockVideoProvider();
        } else {
            mCreateVideoProvider = true;
        }
        connection.setVideoState(request.getVideoState());
        connection.setInitializing();
        if (request.isRequestingRtt()) {
            connection.setRttTextStream(request.getRttTextStream());
            connection.setConnectionProperties(connection.getConnectionProperties() |
                    Connection.PROPERTY_IS_RTT);
        }

        outgoingConnections.add(connection);
        lock.release();
        return connection;
    }

    @Override
    public Connection onCreateIncomingConnection(PhoneAccountHandle connectionManagerPhoneAccount,
            ConnectionRequest request) {
        final MockConnection connection = new MockConnection();
        connection.setAddress(request.getAddress(), CONNECTION_PRESENTATION);
        connection.setConnectionCapabilities(connection.getConnectionCapabilities()
                | Connection.CAPABILITY_CAN_SEND_RESPONSE_VIA_CONNECTION
                | Connection.CAPABILITY_SUPPORT_HOLD
                | Connection.CAPABILITY_HOLD);
        connection.createMockVideoProvider();
        ((Connection) connection).setVideoState(request.getVideoState());
        if (request.isRequestingRtt()) {
            connection.setRttTextStream(request.getRttTextStream());
            connection.setConnectionProperties(connection.getConnectionProperties() |
                    Connection.PROPERTY_IS_RTT);
        }
        connection.setRinging();

        incomingConnections.add(connection);
        lock.release();
        return connection;
    }

    @Override
    public void onConference(Connection connection1, Connection connection2) {
        // Make sure that these connections are already not conferenced.
        if (connection1.getConference() == null && connection2.getConference() == null) {
            MockConference conference = new MockConference(
                    (MockConnection)connection1, (MockConnection)connection2);
            CtsConnectionService.addConferenceToTelecom(conference);
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

    @Override
    public void onRemoteExistingConnectionAdded(RemoteConnection connection) {
        // Keep track of the remote connections added to the service
        remoteConnections.add(connection);
    }

    @Override
    public void onRemoteConferenceAdded(RemoteConference conference) {
        // Keep track of the remote connections added to the service
        remoteConferences.add(conference);
    }

    public void setCreateVideoProvider(boolean createVideoProvider) {
        mCreateVideoProvider = createVideoProvider;
    }
}
