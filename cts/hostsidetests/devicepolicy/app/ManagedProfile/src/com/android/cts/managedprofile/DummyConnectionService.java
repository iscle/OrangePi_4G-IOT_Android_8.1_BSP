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
 * limitations under the License.
 */

package com.android.cts.managedprofile;

import android.net.Uri;
import android.telecom.Connection;
import android.telecom.ConnectionRequest;
import android.telecom.ConnectionService;
import android.telecom.DisconnectCause;
import android.telecom.PhoneAccountHandle;
import android.telecom.RemoteConference;
import android.telecom.RemoteConnection;
import android.telecom.TelecomManager;

/**
 * A simple connection service that hangs up automatically for incoming and outgoing call.
 */
public class DummyConnectionService extends ConnectionService {

    public static final String MISSED_PHONE_NUMBER = "520";
    public static final String NORMAL_PHONE_NUMBER = "886";

    @Override
    public Connection onCreateOutgoingConnection(PhoneAccountHandle connectionManagerPhoneAccount,
            ConnectionRequest request) {
        final DummyConnection connection = new DummyConnection();
        connection.setAddress(request.getAddress(), TelecomManager.PRESENTATION_ALLOWED);
        connection.setVideoState(request.getVideoState());
        hangUpAsync(connection);
        return connection;
    }

    @Override
    public Connection onCreateIncomingConnection(PhoneAccountHandle connectionManagerPhoneAccount,
            ConnectionRequest request) {
        final DummyConnection connection = new DummyConnection();
        connection.setVideoState(request.getVideoState());
        final Uri address =
                request.getExtras().getParcelable(TelecomManager.EXTRA_INCOMING_CALL_ADDRESS);
        connection.setAddress(address, TelecomManager.PRESENTATION_ALLOWED);
        hangUpAsync(connection);
        return connection;
    }

    @Override
    public void onConference(Connection connection1, Connection connection2) {
    }

    @Override
    public void onRemoteExistingConnectionAdded(RemoteConnection connection) {
    }

    @Override
    public void onRemoteConferenceAdded(RemoteConference conference) {
    }

    public static class DummyConnection extends Connection {

        @Override
        public void onAnswer() {
            super.onAnswer();
        }

        @Override
        public void onAnswer(int videoState) {
            super.onAnswer(videoState);
            setActive();
        }

        @Override
        public void onReject() {
            super.onReject();
            setDisconnected(new DisconnectCause(DisconnectCause.REJECTED));
            destroy();
        }

        @Override
        public void onHold() {
            super.onHold();
            setOnHold();
        }

        @Override
        public void onUnhold() {
            super.onUnhold();
            setActive();
        }

        @Override
        public void onDisconnect() {
            super.onDisconnect();
            setDisconnected(new DisconnectCause(DisconnectCause.LOCAL));
            destroy();
        }

        @Override
        public void onAbort() {
            super.onAbort();
            setDisconnected(new DisconnectCause(DisconnectCause.UNKNOWN));
            destroy();
        }
    }

    private static int convertNumberToCause(String number) {
        switch (number) {
            case NORMAL_PHONE_NUMBER:
                return DisconnectCause.LOCAL;
            case MISSED_PHONE_NUMBER:
                return DisconnectCause.MISSED;
        }
        throw new IllegalArgumentException("Should not happen");
    }

    /**
     * Hang up the call after 1 second in a background thread.
     * TODO: It is better if we could have a callback to know when we can disconnect the call.
     */
    private static void hangUpAsync(final Connection connection) {
        final int cause = convertNumberToCause(connection.getAddress().getSchemeSpecificPart());
        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    Thread.sleep(1000);
                    connection.setDisconnected(new DisconnectCause(cause));
                } catch (InterruptedException ex) {
                    // let it be
                }
            }
        }).start();
    }
}