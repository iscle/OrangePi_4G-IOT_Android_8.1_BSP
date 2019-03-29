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

package com.android.cts.verifier.telecom;

import android.content.Intent;
import android.os.Bundle;
import android.telecom.Connection;
import android.telecom.ConnectionRequest;
import android.telecom.ConnectionService;
import android.telecom.PhoneAccountHandle;
import android.telecom.TelecomManager;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/**
 * CTS Verifier ConnectionService implementation.
 */
public class CtsConnectionService extends ConnectionService {
    static final int TIMEOUT_MILLIS = 10000;

    private CtsConnection.Listener mConnectionListener =
            new CtsConnection.Listener() {
                @Override
                void onDestroyed(CtsConnection connection) {
                    synchronized (mConnectionsLock) {
                        mConnections.remove(connection);
                    }
                }
            };

    private static CtsConnectionService sConnectionService;
    private static CountDownLatch sBindingLatch = new CountDownLatch(1);

    private List<CtsConnection> mConnections = new ArrayList<>();
    private Object mConnectionsLock = new Object();
    private CountDownLatch mConnectionLatch = new CountDownLatch(1);

    public static CtsConnectionService getConnectionService() {
        return sConnectionService;
    }

    public static CtsConnectionService waitForAndGetConnectionService() {
        if (sConnectionService == null) {
            try {
                sBindingLatch.await(TIMEOUT_MILLIS, TimeUnit.MILLISECONDS);
            } catch (InterruptedException e) {
            }
        }
        return sConnectionService;
    }

    public CtsConnectionService() throws Exception {
        super();
        sConnectionService = this;
        if (sBindingLatch != null) {
            sBindingLatch.countDown();
        }
        sBindingLatch = new CountDownLatch(1);
    }

    public List<CtsConnection> getConnections() {
        synchronized (mConnectionsLock) {
            return new ArrayList<CtsConnection>(mConnections);
        }
    }

    public CtsConnection waitForAndGetConnection() {
        try {
            mConnectionLatch.await(TIMEOUT_MILLIS, TimeUnit.MILLISECONDS);
        } catch (InterruptedException e) {
        }
        mConnectionLatch = new CountDownLatch(1);
        synchronized (mConnectionsLock) {
            if (mConnections.size() > 0) {
                return mConnections.get(0);
            } else {
                return null;
            }
        }
    }

    @Override
    public boolean onUnbind(Intent intent) {
        sConnectionService = null;
        return super.onUnbind(intent);
    }

    @Override
    public Connection onCreateOutgoingConnection(PhoneAccountHandle connectionManagerAccount,
                                                 final ConnectionRequest request) {

        return createManagedConnection(request, false);
    }

    @Override
    public Connection onCreateIncomingConnection(PhoneAccountHandle connectionManagerPhoneAccount,
                                                 ConnectionRequest request) {

        return createManagedConnection(request, true);
    }

    @Override
    public void onCreateIncomingConnectionFailed(PhoneAccountHandle connectionManagerHandle,
                                                 ConnectionRequest request) {
    }

    @Override
    public void onCreateOutgoingConnectionFailed(PhoneAccountHandle connectionManagerHandle,
                                                 ConnectionRequest request) {
    }

    private Connection createManagedConnection(ConnectionRequest request, boolean isIncoming) {
        boolean isSelfManaged = request.getAccountHandle().equals(
                PhoneAccountUtils.TEST_SELF_MANAGED_PHONE_ACCOUNT_HANDLE);

        boolean useAudioClip =
                request.getExtras().getBoolean(CtsConnection.EXTRA_PLAY_CS_AUDIO, false);
        CtsConnection connection = new CtsConnection(getApplicationContext(), isIncoming,
                mConnectionListener, useAudioClip);
        if (isSelfManaged) {
            connection.setConnectionProperties(Connection.PROPERTY_SELF_MANAGED);
        }
        connection.setConnectionCapabilities(Connection.CAPABILITY_SUPPORT_HOLD |
                Connection.CAPABILITY_HOLD);
        connection.setAddress(request.getAddress(), TelecomManager.PRESENTATION_ALLOWED);
        connection.setExtras(request.getExtras());

        Bundle moreExtras = new Bundle();
        moreExtras.putParcelable(TelecomManager.EXTRA_PHONE_ACCOUNT_HANDLE,
                request.getAccountHandle());
        connection.putExtras(moreExtras);
        connection.setVideoState(request.getVideoState());

        synchronized (mConnectionsLock) {
            mConnections.add(connection);
        }
        if (mConnectionLatch != null) {
            mConnectionLatch.countDown();
        }
        return connection;
    }
}
