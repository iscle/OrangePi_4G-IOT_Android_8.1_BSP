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

package com.android.server.telecom.testapps;

import android.content.Intent;
import android.os.Bundle;
import android.telecom.Connection;
import android.telecom.ConnectionRequest;
import android.telecom.ConnectionService;
import android.telecom.Log;
import android.telecom.PhoneAccountHandle;
import android.telecom.TelecomManager;
import android.telecom.VideoProfile;

import java.util.Objects;
import java.util.Random;

/**
 * Sample implementation of the self-managed {@link ConnectionService} API.
 * <p>
 * See {@link android.telecom} for more information on self-managed {@link ConnectionService}s.
 */
public class SelfManagedConnectionService extends ConnectionService {
    private static final String[] TEST_NAMES = {"Tom Smith", "Jane Appleseed", "Joseph Engleton",
            "Claudia McPherson", "Chris P. Bacon", "Seymour Butz", "Hugh Mungus", "Anita Bath"};
    private final SelfManagedCallList mCallList = SelfManagedCallList.getInstance();

    @Override
    public Connection onCreateOutgoingConnection(
            PhoneAccountHandle connectionManagerAccount,
            final ConnectionRequest request) {

        return createSelfManagedConnection(request, false);
    }

    @Override
    public Connection onCreateIncomingConnection(PhoneAccountHandle connectionManagerPhoneAccount,
            ConnectionRequest request) {
        return createSelfManagedConnection(request, true);
    }

    @Override
    public void onCreateIncomingConnectionFailed(PhoneAccountHandle connectionManagerPhoneAccount,
                                                 ConnectionRequest request) {
        mCallList.notifyCreateIncomingConnectionFailed(request);
    }

    @Override
    public void onCreateOutgoingConnectionFailed(PhoneAccountHandle connectionManagerPhoneAccount,
                                                 ConnectionRequest request) {
        mCallList.notifyCreateOutgoingConnectionFailed(request);
    }

    private Connection createSelfManagedConnection(ConnectionRequest request, boolean isIncoming) {
        SelfManagedConnection connection = new SelfManagedConnection(mCallList,
                getApplicationContext(), isIncoming);
        connection.setListener(mCallList.getConnectionListener());
        connection.setConnectionProperties(Connection.PROPERTY_SELF_MANAGED);
        connection.setAddress(request.getAddress(), TelecomManager.PRESENTATION_ALLOWED);
        connection.setAudioModeIsVoip(true);
        connection.setVideoState(request.getVideoState());
        Random random = new Random();
        connection.setCallerDisplayName(TEST_NAMES[random.nextInt(TEST_NAMES.length)],
                TelecomManager.PRESENTATION_ALLOWED);
        connection.setExtras(request.getExtras());
        if (isIncoming) {
            connection.setIsIncomingCallUiShowing(request.shouldShowIncomingCallUi());
        }
        Bundle requestExtras = request.getExtras();
        if (requestExtras != null) {
            Log.i(this, "createConnection: isHandover=%b, handoverFrom=%s",
                    requestExtras.getBoolean(TelecomManager.EXTRA_IS_HANDOVER),
                    requestExtras.getString(TelecomManager.EXTRA_HANDOVER_FROM_PHONE_ACCOUNT));
            connection.setIsHandover(requestExtras.getBoolean(TelecomManager.EXTRA_IS_HANDOVER,
                    false));
            if (!isIncoming && connection.isHandover()) {
                Intent intent = new Intent(Intent.ACTION_MAIN, null);
                intent.setFlags(Intent.FLAG_ACTIVITY_NO_USER_ACTION | Intent.FLAG_ACTIVITY_NEW_TASK);
                intent.setClass(this, HandoverActivity.class);
                intent.putExtra(HandoverActivity.EXTRA_CALL_ID, connection.getCallId());
                startActivity(intent);
            } else {
                Log.i(this, "Handover incoming call created.");
            }
        }

        // Track the phone account handle which created this connection so we can distinguish them
        // in the sample call list later.
        Bundle moreExtras = new Bundle();
        moreExtras.putParcelable(SelfManagedConnection.EXTRA_PHONE_ACCOUNT_HANDLE,
                request.getAccountHandle());
        connection.putExtras(moreExtras);
        connection.setVideoState(request.getVideoState());
        Log.i(this, "createSelfManagedConnection %s", connection);
        mCallList.addConnection(connection);
        return connection;
    }
}
