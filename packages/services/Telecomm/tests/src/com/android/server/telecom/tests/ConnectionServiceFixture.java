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

package com.android.server.telecom.tests;

import com.android.internal.telecom.IConnectionService;
import com.android.internal.telecom.IConnectionServiceAdapter;
import com.android.internal.telecom.IVideoProvider;
import com.android.internal.telecom.RemoteServiceCallback;

import junit.framework.TestCase;

import org.mockito.Mockito;

import android.content.ComponentName;
import android.net.Uri;
import android.os.Bundle;
import android.os.IBinder;
import android.os.IInterface;
import android.os.ParcelFileDescriptor;
import android.os.RemoteException;
import android.telecom.CallAudioState;
import android.telecom.Conference;
import android.telecom.Connection;
import android.telecom.ConnectionRequest;
import android.telecom.ConnectionService;
import android.telecom.DisconnectCause;
import android.telecom.Log;
import android.telecom.Logging.Session;
import android.telecom.ParcelableConference;
import android.telecom.ParcelableConnection;
import android.telecom.PhoneAccountHandle;
import android.telecom.StatusHints;
import android.telecom.TelecomManager;

import com.google.android.collect.Lists;

import java.lang.Override;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/**
 * Controls a test {@link IConnectionService} as would be provided by a source of connectivity
 * to the Telecom framework.
 */
public class ConnectionServiceFixture implements TestFixture<IConnectionService> {
    static int INVALID_VIDEO_STATE = -1;
    public CountDownLatch mExtrasLock = new CountDownLatch(1);
    static int NOT_SPECIFIED = 0;

    /**
     * Implementation of ConnectionService that performs no-ops for tasks normally meant for
     * Telephony and reports success back to Telecom
     */
    public class FakeConnectionServiceDelegate extends ConnectionService {
        int mVideoState = INVALID_VIDEO_STATE;
        int mCapabilities = NOT_SPECIFIED;
        int mProperties = NOT_SPECIFIED;

        @Override
        public Connection onCreateUnknownConnection(
                PhoneAccountHandle connectionManagerPhoneAccount, ConnectionRequest request) {
            mLatestConnection = new FakeConnection(request.getVideoState(), request.getAddress());
            return mLatestConnection;
        }

        @Override
        public Connection onCreateIncomingConnection(
                PhoneAccountHandle connectionManagerPhoneAccount, ConnectionRequest request) {
            FakeConnection fakeConnection =  new FakeConnection(
                    mVideoState == INVALID_VIDEO_STATE ? request.getVideoState() : mVideoState,
                    request.getAddress());
            mLatestConnection = fakeConnection;
            if (mCapabilities != NOT_SPECIFIED) {
                fakeConnection.setConnectionCapabilities(mCapabilities);
            }
            if (mProperties != NOT_SPECIFIED) {
                fakeConnection.setConnectionProperties(mProperties);
            }

            return fakeConnection;
        }

        @Override
        public Connection onCreateOutgoingConnection(
                PhoneAccountHandle connectionManagerPhoneAccount, ConnectionRequest request) {
            FakeConnection fakeConnection = new FakeConnection(request.getVideoState(),
                    request.getAddress());
            mLatestConnection = fakeConnection;
            if (mCapabilities != NOT_SPECIFIED) {
                fakeConnection.setConnectionCapabilities(mCapabilities);
            }
            if (mProperties != NOT_SPECIFIED) {
                fakeConnection.setConnectionProperties(mProperties);
            }
            return fakeConnection;
        }

        @Override
        public void onCreateConnectionComplete(Connection connection) {
        }

        @Override
        public void onConference(Connection cxn1, Connection cxn2) {
            if (((FakeConnection) cxn1).getIsConferenceCreated()) {
                // Usually, this is implemented by something in Telephony, which does a bunch of
                // radio work to conference the two connections together. Here we just short-cut
                // that and declare them conferenced.
                Conference fakeConference = new FakeConference();
                fakeConference.addConnection(cxn1);
                fakeConference.addConnection(cxn2);
                mLatestConference = fakeConference;
                addConference(fakeConference);
            } else {
                try {
                    sendSetConferenceMergeFailed(cxn1.getTelecomCallId());
                } catch (Exception e) {
                    Log.w(this, "Exception on sendSetConferenceMergeFailed: " + e.getMessage());
                }
            }
        }
    }

    public class FakeConnection extends Connection {
        // Set to false if you wish the Conference merge to fail.
        boolean mIsConferenceCreated = true;

        public FakeConnection(int videoState, Uri address) {
            super();
            int capabilities = getConnectionCapabilities();
            capabilities |= CAPABILITY_MUTE;
            capabilities |= CAPABILITY_SUPPORT_HOLD;
            capabilities |= CAPABILITY_HOLD;
            setVideoState(videoState);
            setConnectionCapabilities(capabilities);
            setDialing();
            setAddress(address, TelecomManager.PRESENTATION_ALLOWED);
        }

        @Override
        public void onExtrasChanged(Bundle extras) {
            mExtrasLock.countDown();
        }

        public boolean getIsConferenceCreated() {
            return mIsConferenceCreated;
        }

        public void setIsConferenceCreated(boolean isConferenceCreated) {
            mIsConferenceCreated = isConferenceCreated;
        }
    }

    public class FakeConference extends Conference {
        public FakeConference() {
            super(null);
            setConnectionCapabilities(
                    Connection.CAPABILITY_SUPPORT_HOLD
                            | Connection.CAPABILITY_HOLD
                            | Connection.CAPABILITY_MUTE
                            | Connection.CAPABILITY_MANAGE_CONFERENCE);
        }

        @Override
        public void onMerge(Connection connection) {
            // Do nothing besides inform the connection that it was merged into this conference.
            connection.setConference(this);
        }

        @Override
        public void onExtrasChanged(Bundle extras) {
            Log.w(this, "FakeConference onExtrasChanged");
            mExtrasLock.countDown();
        }
    }

    public class FakeConnectionService extends IConnectionService.Stub {
        List<String> rejectedCallIds = Lists.newArrayList();

        @Override
        public void addConnectionServiceAdapter(IConnectionServiceAdapter adapter,
                Session.Info info) throws RemoteException {
            if (!mConnectionServiceAdapters.add(adapter)) {
                throw new RuntimeException("Adapter already added: " + adapter);
            }
            mConnectionServiceDelegateAdapter.addConnectionServiceAdapter(adapter,
                    null /*Session.Info*/);
        }

        @Override
        public void removeConnectionServiceAdapter(IConnectionServiceAdapter adapter,
                Session.Info info) throws RemoteException {
            if (!mConnectionServiceAdapters.remove(adapter)) {
                throw new RuntimeException("Adapter never added: " + adapter);
            }
            mConnectionServiceDelegateAdapter.removeConnectionServiceAdapter(adapter,
                    null /*Session.Info*/);
        }

        @Override
        public void createConnection(PhoneAccountHandle connectionManagerPhoneAccount,
                String id, ConnectionRequest request, boolean isIncoming, boolean isUnknown,
                Session.Info info) throws RemoteException {
            Log.i(ConnectionServiceFixture.this, "createConnection --> " + id);

            if (mConnectionById.containsKey(id)) {
                throw new RuntimeException("Connection already exists: " + id);
            }
            mLatestConnectionId = id;
            ConnectionInfo c = new ConnectionInfo();
            c.connectionManagerPhoneAccount = connectionManagerPhoneAccount;
            c.id = id;
            c.request = request;
            c.isIncoming = isIncoming;
            c.isUnknown = isUnknown;
            c.capabilities |= Connection.CAPABILITY_HOLD | Connection.CAPABILITY_SUPPORT_HOLD;
            c.videoState = request.getVideoState();
            c.mockVideoProvider = new MockVideoProvider();
            c.videoProvider = c.mockVideoProvider.getInterface();
            c.isConferenceCreated = true;
            mConnectionById.put(id, c);
            mConnectionServiceDelegateAdapter.createConnection(connectionManagerPhoneAccount,
                    id, request, isIncoming, isUnknown, null /*Session.Info*/);
        }

        @Override
        public void createConnectionComplete(String id, Session.Info info) throws RemoteException {
            mConnectionServiceDelegateAdapter.createConnectionComplete(id, null /*Session.Info*/);
        }

        @Override
        public void createConnectionFailed(PhoneAccountHandle connectionManagerPhoneAccount,
                String callId, ConnectionRequest request, boolean isIncoming,
                Session.Info sessionInfo) throws RemoteException {
            Log.i(ConnectionServiceFixture.this, "createConnectionFailed --> " + callId);

            if (mConnectionById.containsKey(callId)) {
                throw new RuntimeException("Connection already exists: " + callId);
            }

            // TODO(3p-calls): Implement this.
        }

        @Override
        public void abort(String callId, Session.Info info) throws RemoteException { }

        @Override
        public void answerVideo(String callId, int videoState,
                Session.Info info) throws RemoteException { }

        @Override
        public void answer(String callId, Session.Info info) throws RemoteException { }

        @Override
        public void reject(String callId, Session.Info info) throws RemoteException {
            rejectedCallIds.add(callId);
        }

        @Override
        public void rejectWithMessage(String callId, String message,
                Session.Info info) throws RemoteException { }

        @Override
        public void disconnect(String callId, Session.Info info) throws RemoteException { }

        @Override
        public void silence(String callId, Session.Info info) throws RemoteException { }

        @Override
        public void hold(String callId, Session.Info info) throws RemoteException { }

        @Override
        public void unhold(String callId, Session.Info info) throws RemoteException { }

        @Override
        public void onCallAudioStateChanged(String activeCallId, CallAudioState audioState,
                Session.Info info)
                throws RemoteException { }

        @Override
        public void playDtmfTone(String callId, char digit,
                Session.Info info) throws RemoteException { }

        @Override
        public void stopDtmfTone(String callId, Session.Info info) throws RemoteException { }

        @Override
        public void conference(String conferenceCallId, String callId,
                Session.Info info) throws RemoteException {
            mConnectionServiceDelegateAdapter.conference(conferenceCallId, callId, info);
        }

        @Override
        public void splitFromConference(String callId, Session.Info info) throws RemoteException { }

        @Override
        public void mergeConference(String conferenceCallId,
                Session.Info info) throws RemoteException { }

        @Override
        public void swapConference(String conferenceCallId,
                Session.Info info) throws RemoteException { }

        @Override
        public void onPostDialContinue(String callId, boolean proceed,
                Session.Info info) throws RemoteException { }

        @Override
        public void pullExternalCall(String callId, Session.Info info) throws RemoteException { }

        @Override
        public void sendCallEvent(String callId, String event, Bundle extras,
                Session.Info info) throws RemoteException
        {}

        public void onExtrasChanged(String callId, Bundle extras,
                Session.Info info) throws RemoteException {
            mConnectionServiceDelegateAdapter.onExtrasChanged(callId, extras, info);
        }

        @Override
        public void startRtt(String callId, ParcelFileDescriptor fromInCall,
                ParcelFileDescriptor toInCall, Session.Info sessionInfo) throws RemoteException {

        }

        @Override
        public void stopRtt(String callId, Session.Info sessionInfo) throws RemoteException {

        }

        @Override
        public void respondToRttUpgradeRequest(String callId, ParcelFileDescriptor fromInCall,
                ParcelFileDescriptor toInCall, Session.Info sessionInfo) throws RemoteException {

        }

        @Override
        public IBinder asBinder() {
            return this;
        }

        @Override
        public IInterface queryLocalInterface(String descriptor) {
            return this;
        }
    }

    FakeConnectionServiceDelegate mConnectionServiceDelegate =
            new FakeConnectionServiceDelegate();
    private IConnectionService mConnectionServiceDelegateAdapter =
            IConnectionService.Stub.asInterface(mConnectionServiceDelegate.onBind(null));

    FakeConnectionService mConnectionService = new FakeConnectionService();
    private IConnectionService.Stub mConnectionServiceSpy = Mockito.spy(mConnectionService);

    public class ConnectionInfo {
        PhoneAccountHandle connectionManagerPhoneAccount;
        String id;
        boolean ringing;
        ConnectionRequest request;
        boolean isIncoming;
        boolean isUnknown;
        int state;
        int addressPresentation;
        int capabilities;
        int properties;
        int supportedAudioRoutes;
        StatusHints statusHints;
        DisconnectCause disconnectCause;
        String conferenceId;
        String callerDisplayName;
        int callerDisplayNamePresentation;
        final List<String> conferenceableConnectionIds = new ArrayList<>();
        IVideoProvider videoProvider;
        Connection.VideoProvider videoProviderImpl;
        MockVideoProvider mockVideoProvider;
        int videoState;
        boolean isVoipAudioMode;
        Bundle extras;
        boolean isConferenceCreated;
    }

    public class ConferenceInfo {
        PhoneAccountHandle phoneAccount;
        int state;
        int capabilities;
        int properties;
        final List<String> connectionIds = new ArrayList<>();
        IVideoProvider videoProvider;
        int videoState;
        long connectTimeMillis;
        long connectElapsedTimeMillis;
        StatusHints statusHints;
        Bundle extras;
    }

    public String mLatestConnectionId;
    public Connection mLatestConnection;
    public Conference mLatestConference;
    public final Set<IConnectionServiceAdapter> mConnectionServiceAdapters = new HashSet<>();
    public final Map<String, ConnectionInfo> mConnectionById = new HashMap<>();
    public final Map<String, ConferenceInfo> mConferenceById = new HashMap<>();
    public final List<ComponentName> mRemoteConnectionServiceNames = new ArrayList<>();
    public final List<IBinder> mRemoteConnectionServices = new ArrayList<>();

    public ConnectionServiceFixture() throws Exception { }

    @Override
    public IConnectionService getTestDouble() {
        return mConnectionServiceSpy;
    }

    public void sendHandleCreateConnectionComplete(String id) throws Exception {
        for (IConnectionServiceAdapter a : mConnectionServiceAdapters) {
            a.handleCreateConnectionComplete(
                    id,
                    mConnectionById.get(id).request,
                    parcelable(mConnectionById.get(id)), null /*Session.Info*/);
        }
    }

    public void sendSetActive(String id) throws Exception {
        mConnectionById.get(id).state = Connection.STATE_ACTIVE;
        for (IConnectionServiceAdapter a : mConnectionServiceAdapters) {
            a.setActive(id, null /*Session.Info*/);
        }
    }

    public void sendSetRinging(String id) throws Exception {
        mConnectionById.get(id).state = Connection.STATE_RINGING;
        for (IConnectionServiceAdapter a : mConnectionServiceAdapters) {
            a.setRinging(id, null /*Session.Info*/);
        }
    }

    public void sendSetDialing(String id) throws Exception {
        mConnectionById.get(id).state = Connection.STATE_DIALING;
        for (IConnectionServiceAdapter a : mConnectionServiceAdapters) {
            a.setDialing(id, null /*Session.Info*/);
        }
    }

    public void sendSetDisconnected(String id, int disconnectCause) throws Exception {
        mConnectionById.get(id).state = Connection.STATE_DISCONNECTED;
        mConnectionById.get(id).disconnectCause = new DisconnectCause(disconnectCause);
        for (IConnectionServiceAdapter a : mConnectionServiceAdapters) {
            a.setDisconnected(id, mConnectionById.get(id).disconnectCause, null /*Session.Info*/);
        }
    }

    public void sendSetOnHold(String id) throws Exception {
        mConnectionById.get(id).state = Connection.STATE_HOLDING;
        for (IConnectionServiceAdapter a : mConnectionServiceAdapters) {
            a.setOnHold(id, null /*Session.Info*/);
        }
    }

    public void sendSetRingbackRequested(String id) throws Exception {
        for (IConnectionServiceAdapter a : mConnectionServiceAdapters) {
            a.setRingbackRequested(id, mConnectionById.get(id).ringing, null /*Session.Info*/);
        }
    }

    public void sendSetConnectionCapabilities(String id) throws Exception {
        for (IConnectionServiceAdapter a : mConnectionServiceAdapters) {
            a.setConnectionCapabilities(id, mConnectionById.get(id).capabilities,
                    null /*Session.Info*/);
        }
    }

    public void sendSetConnectionProperties(String id) throws Exception {
        for (IConnectionServiceAdapter a : mConnectionServiceAdapters) {
            a.setConnectionProperties(id, mConnectionById.get(id).properties, null /*Session.Info*/);
        }
    }
    public void sendSetIsConferenced(String id) throws Exception {
        for (IConnectionServiceAdapter a : mConnectionServiceAdapters) {
            a.setIsConferenced(id, mConnectionById.get(id).conferenceId, null /*Session.Info*/);
        }
    }

    public void sendAddConferenceCall(String id) throws Exception {
        for (IConnectionServiceAdapter a : mConnectionServiceAdapters) {
            a.addConferenceCall(id, parcelable(mConferenceById.get(id)), null /*Session.Info*/);
        }
    }

    public void sendRemoveCall(String id) throws Exception {
        for (IConnectionServiceAdapter a : mConnectionServiceAdapters) {
            a.removeCall(id, null /*Session.Info*/);
        }
    }

    public void sendOnPostDialWait(String id, String remaining) throws Exception {
        for (IConnectionServiceAdapter a : mConnectionServiceAdapters) {
            a.onPostDialWait(id, remaining, null /*Session.Info*/);
        }
    }

    public void sendOnPostDialChar(String id, char nextChar) throws Exception {
        for (IConnectionServiceAdapter a : mConnectionServiceAdapters) {
            a.onPostDialChar(id, nextChar, null /*Session.Info*/);
        }
    }

    public void sendQueryRemoteConnectionServices() throws Exception {
        mRemoteConnectionServices.clear();
        for (IConnectionServiceAdapter a : mConnectionServiceAdapters) {
            a.queryRemoteConnectionServices(new RemoteServiceCallback.Stub() {
                @Override
                public void onError() throws RemoteException {
                    throw new RuntimeException();
                }

                @Override
                public void onResult(
                        List<ComponentName> names,
                        List<IBinder> services)
                        throws RemoteException {
                    TestCase.assertEquals(names.size(), services.size());
                    mRemoteConnectionServiceNames.addAll(names);
                    mRemoteConnectionServices.addAll(services);
                }

                @Override
                public IBinder asBinder() {
                    return this;
                }
            }, null /*Session.Info*/);
        }
    }

    public void sendSetVideoProvider(String id) throws Exception {
        for (IConnectionServiceAdapter a : mConnectionServiceAdapters) {
            a.setVideoProvider(id, mConnectionById.get(id).videoProvider, null /*Session.Info*/);
        }
    }

    public void sendSetVideoState(String id) throws Exception {
        for (IConnectionServiceAdapter a : mConnectionServiceAdapters) {
            a.setVideoState(id, mConnectionById.get(id).videoState, null /*Session.Info*/);
        }
    }

    public void sendSetIsVoipAudioMode(String id) throws Exception {
        for (IConnectionServiceAdapter a : mConnectionServiceAdapters) {
            a.setIsVoipAudioMode(id, mConnectionById.get(id).isVoipAudioMode,
                    null /*Session.Info*/);
        }
    }

    public void sendSetStatusHints(String id) throws Exception {
        for (IConnectionServiceAdapter a : mConnectionServiceAdapters) {
            a.setStatusHints(id, mConnectionById.get(id).statusHints, null /*Session.Info*/);
        }
    }

    public void sendSetAddress(String id) throws Exception {
        for (IConnectionServiceAdapter a : mConnectionServiceAdapters) {
            a.setAddress(
                    id,
                    mConnectionById.get(id).request.getAddress(),
                    mConnectionById.get(id).addressPresentation, null /*Session.Info*/);
        }
    }

    public void sendSetCallerDisplayName(String id) throws Exception {
        for (IConnectionServiceAdapter a : mConnectionServiceAdapters) {
            a.setCallerDisplayName(
                    id,
                    mConnectionById.get(id).callerDisplayName,
                    mConnectionById.get(id).callerDisplayNamePresentation, null /*Session.Info*/);
        }
    }

    public void sendSetConferenceableConnections(String id) throws Exception {
        for (IConnectionServiceAdapter a : mConnectionServiceAdapters) {
            a.setConferenceableConnections(id, mConnectionById.get(id).conferenceableConnectionIds,
                    null /*Session.Info*/);
        }
    }

    public void sendAddExistingConnection(String id) throws Exception {
        for (IConnectionServiceAdapter a : mConnectionServiceAdapters) {
            a.addExistingConnection(id, parcelable(mConnectionById.get(id)), null /*Session.Info*/);
        }
    }

    public void sendConnectionEvent(String id, String event, Bundle extras) throws Exception {
        for (IConnectionServiceAdapter a : mConnectionServiceAdapters) {
            a.onConnectionEvent(id, event, extras, null /*Session.Info*/);
        }
    }

    public void sendSetConferenceMergeFailed(String id) throws Exception {
        for (IConnectionServiceAdapter a : mConnectionServiceAdapters) {
            a.setConferenceMergeFailed(id, null /*Session.Info*/);
        }
    }

    /**
     * Waits until the {@link Connection#onExtrasChanged(Bundle)} API has been called on a
     * {@link Connection} or {@link Conference}.
     */
    public void waitForExtras() {
        try {
            mExtrasLock.await(TelecomSystemTest.TEST_TIMEOUT, TimeUnit.MILLISECONDS);
        } catch (InterruptedException ie) {
        }
        mExtrasLock = new CountDownLatch(1);
    }

    private ParcelableConference parcelable(ConferenceInfo c) {
        return new ParcelableConference(
                c.phoneAccount,
                c.state,
                c.capabilities,
                c.properties,
                c.connectionIds,
                c.videoProvider,
                c.videoState,
                c.connectTimeMillis,
                c.connectElapsedTimeMillis,
                c.statusHints,
                c.extras);
    }

    private ParcelableConnection parcelable(ConnectionInfo c) {
        return new ParcelableConnection(
                c.request.getAccountHandle(),
                c.state,
                c.capabilities,
                c.properties,
                c.supportedAudioRoutes,
                c.request.getAddress(),
                c.addressPresentation,
                c.callerDisplayName,
                c.callerDisplayNamePresentation,
                c.videoProvider,
                c.videoState,
                false, /* ringback requested */
                false, /* voip audio mode */
                0, /* Connect Time for conf call on this connection */
                0, /* Connect Real Time comes from conference call */
                c.statusHints,
                c.disconnectCause,
                c.conferenceableConnectionIds,
                c.extras);
    }
}
