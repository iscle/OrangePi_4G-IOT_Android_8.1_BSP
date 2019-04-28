/*
 * Copyright (C) 2013 The Android Open Source Project
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

package com.android.server.telecom.testapps;

import android.net.Uri;
import android.telecom.CallAudioState;
import android.telecom.Conference;
import android.telecom.Connection;
import android.telecom.ConnectionRequest;
import android.telecom.ConnectionService;
import android.telecom.DisconnectCause;
import android.telecom.PhoneAccountHandle;
import android.telecom.RemoteConference;
import android.telecom.RemoteConnection;
import android.telecom.StatusHints;
import android.telecom.VideoProfile;
import android.util.Log;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Service which acts as a fake ConnectionManager if so configured.
 * TODO(santoscordon): Rename all classes in the directory to Dummy* (e.g., DummyConnectionService).
 */
public class TestConnectionManager extends ConnectionService {
    public final class TestManagedConnection extends Connection {
        private final RemoteConnection.Callback mRemoteCallback = new RemoteConnection.Callback() {
            @Override
            public void onStateChanged(RemoteConnection connection, int state) {
                setState(state);
            }

            @Override
            public void onDisconnected(
                    RemoteConnection connection, DisconnectCause disconnectCause) {
                setDisconnected(disconnectCause);
                destroy();
            }

            @Override
            public void onRingbackRequested(RemoteConnection connection, boolean ringback) {
                setRingbackRequested(ringback);
            }

            @Override
            public void onConnectionCapabilitiesChanged(RemoteConnection connection,
                    int connectionCapabilities) {
                setConnectionCapabilities(connectionCapabilities);
            }

            @Override
            public void onConnectionPropertiesChanged(RemoteConnection connection,
                    int connectionProperties) {
                setConnectionProperties(connectionProperties);
            }

            @Override
            public void onPostDialWait(RemoteConnection connection, String remainingDigits) {
                setPostDialWait(remainingDigits);
            }

            @Override
            public void onVoipAudioChanged(RemoteConnection connection, boolean isVoip) {
                setAudioModeIsVoip(isVoip);
            }

            @Override
            public void onStatusHintsChanged(RemoteConnection connection, StatusHints statusHints) {
                setStatusHints(statusHints);
            }

            @Override
            public void onVideoStateChanged(RemoteConnection connection, int videoState) {
                if (videoState == VideoProfile.STATE_BIDIRECTIONAL) {
                    setVideoProvider(new TestManagedVideoProvider(connection.getVideoProvider()));
                }
                setVideoState(videoState);
            }

            @Override
            public void onAddressChanged(
                    RemoteConnection connection, Uri address, int presentation) {
                setAddress(address, presentation);
            }

            @Override
            public void onCallerDisplayNameChanged(
                    RemoteConnection connection, String callerDisplayName, int presentation) {
                setCallerDisplayName(callerDisplayName, presentation);
            }

            @Override
            public void onDestroyed(RemoteConnection connection) {
                destroy();
                mManagedConnectionByRemote.remove(mRemote);
            }

            @Override
            public void onConferenceableConnectionsChanged(
                    RemoteConnection connect,
                    List<RemoteConnection> conferenceable) {
                List<Connection> c = new ArrayList<>();
                for (RemoteConnection remote : conferenceable) {
                    if (mManagedConnectionByRemote.containsKey(remote)) {
                        c.add(mManagedConnectionByRemote.get(remote));
                    }
                }
                setConferenceableConnections(c);
            }

            @Override
            public void onRttInitiationSuccess(RemoteConnection connection) {
                sendRttInitiationSuccess();
            }

            @Override
            public void onRttInitiationFailure(RemoteConnection connection, int reason) {
                sendRttInitiationFailure(reason);
            }

            @Override
            public void onRttSessionRemotelyTerminated(RemoteConnection connection) {
                sendRttSessionRemotelyTerminated();
            }

            @Override
            public void onRemoteRttRequest(RemoteConnection connection) {
                sendRemoteRttRequest();
            }
        };

        private final RemoteConnection mRemote;
        private final boolean mIsIncoming;

        TestManagedConnection(RemoteConnection remote, boolean isIncoming) {
            mRemote = remote;
            mIsIncoming = isIncoming;
            mRemote.registerCallback(mRemoteCallback);
            setState(mRemote.getState());
            setVideoState(mRemote.getVideoState());
            setConnectionProperties(remote.getConnectionProperties());
        }

        @Override
        public void onAbort() {
            mRemote.abort();
        }

        /**
         * ${inheritDoc}
         */
        @Override
        public void onAnswer(int videoState) {
            mRemote.answer(videoState);
        }

        /**
         * ${inheritDoc}
         */
        @Override
        public void onDisconnect() {
            mRemote.disconnect();
        }

        @Override
        public void onPlayDtmfTone(char c) {
            mRemote.playDtmfTone(c);
        }

        /**
         * ${inheritDoc}
         */
        @Override
        public void onHold() {
            mRemote.hold();
        }

        /**
         * ${inheritDoc}
         */
        @Override
        public void onReject() {
            mRemote.reject();
        }

        /**
         * ${inheritDoc}
         */
        @Override
        public void onUnhold() {
            mRemote.unhold();
        }

        @Override
        public void onCallAudioStateChanged(CallAudioState state) {
            mRemote.setCallAudioState(state);
        }

        @Override
        public void onStartRtt(RttTextStream rttTextStream) {
            mRemote.startRtt(rttTextStream);
        }

        @Override
        public void onStopRtt() {
            mRemote.stopRtt();
        }

        @Override
        public void handleRttUpgradeResponse(RttTextStream rttTextStream) {
            mRemote.sendRttUpgradeResponse(rttTextStream);
        }

        private void setState(int state) {
            log("setState: " + state);
            switch (state) {
                case STATE_ACTIVE:
                    setActive();
                    break;
                case STATE_HOLDING:
                    setOnHold();
                    break;
                case STATE_DIALING:
                    setDialing();
                    break;
                case STATE_RINGING:
                    setRinging();
                    break;
            }
        }
    }
    public final class TestManagedConference extends Conference {
        private final RemoteConference.Callback mRemoteCallback = new RemoteConference.Callback() {
            @Override
            public void onStateChanged(RemoteConference conference, int oldState, int newState) {
                switch (newState) {
                    case Connection.STATE_DISCONNECTED:
                        // See onDisconnected below
                        break;
                    case Connection.STATE_HOLDING:
                        setOnHold();
                        break;
                    case Connection.STATE_ACTIVE:
                        setActive();
                        break;
                    default:
                        log("unrecognized state for Conference: " + newState);
                        break;
                }
            }

            @Override
            public void onDisconnected(RemoteConference conference,
                    DisconnectCause disconnectCause) {
                setDisconnected(disconnectCause);
            }

            @Override
            public void onConnectionAdded(
                    RemoteConference conference,
                    RemoteConnection connection) {
                TestManagedConnection c = mManagedConnectionByRemote.get(connection);
                if (c == null) {
                    log("onConnectionAdded cannot find remote connection: " + connection);
                } else {
                    addConnection(c);
                }
            }

            @Override
            public void onConnectionRemoved(
                    RemoteConference conference,
                    RemoteConnection connection) {
                TestManagedConnection c = mManagedConnectionByRemote.get(connection);
                if (c == null) {
                    log("onConnectionRemoved cannot find remote connection: " + connection);
                } else {
                    removeConnection(c);
                }
            }

            @Override
            public void onConnectionCapabilitiesChanged(RemoteConference conference,
                    int connectionCapabilities) {
                setConnectionCapabilities(connectionCapabilities);
            }

            @Override
            public void onConnectionPropertiesChanged(RemoteConference conference,
                    int connectionProperties) {
                setConnectionProperties(connectionProperties);
            }

            @Override
            public void onDestroyed(RemoteConference conference) {
                destroy();
                mRemote.unregisterCallback(mRemoteCallback);
                mManagedConferenceByRemote.remove(mRemote);
            }

        };

        @Override
        public void onPlayDtmfTone(char c) {
            mRemote.playDtmfTone(c);
        };

        @Override
        public void onStopDtmfTone() {
            mRemote.stopDtmfTone();
        };

        private final RemoteConference mRemote;

        public TestManagedConference(RemoteConference remote) {
            super(null);
            mRemote = remote;
            remote.registerCallback(mRemoteCallback);
            setActive();
            for (RemoteConnection r : remote.getConnections()) {
                TestManagedConnection c = mManagedConnectionByRemote.get(r);
                if (c != null) {
                    addConnection(c);
                }
            }
        }
    }

    static void log(String msg) {
        Log.w("telecomtestcs", "[TestConnectionManager] " + msg);
    }

    private final Map<RemoteConference, TestManagedConference> mManagedConferenceByRemote
            = new HashMap<>();
    private final Map<RemoteConnection, TestManagedConnection> mManagedConnectionByRemote
            = new HashMap<>();

    @Override
    public Connection onCreateOutgoingConnection(
            PhoneAccountHandle connectionManagerAccount,
            final ConnectionRequest request) {
        return makeConnection(request, false);
    }

    @Override
    public Connection onCreateIncomingConnection(
            PhoneAccountHandle connectionManagerAccount,
            final ConnectionRequest request) {
        return makeConnection(request, true);
    }

    @Override
    public void onConference(Connection a, Connection b) {
        conferenceRemoteConnections(
                ((TestManagedConnection) a).mRemote,
                ((TestManagedConnection) b).mRemote);
    }

    @Override
    public void onRemoteConferenceAdded(RemoteConference remoteConference) {
        addConference(new TestManagedConference(remoteConference));
    }

    Map<RemoteConnection, TestManagedConnection> getManagedConnectionByRemote() {
        return mManagedConnectionByRemote;
    }

    private Connection makeConnection(ConnectionRequest request, boolean incoming) {
        RemoteConnection remote = incoming
                ? createRemoteIncomingConnection(request.getAccountHandle(), request)
                : createRemoteOutgoingConnection(request.getAccountHandle(), request);
        TestManagedConnection local = new TestManagedConnection(remote, false);
        mManagedConnectionByRemote.put(remote, local);
        return local;
    }
}
