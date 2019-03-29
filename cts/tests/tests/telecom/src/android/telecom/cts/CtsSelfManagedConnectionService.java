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

import android.content.Intent;
import android.os.Bundle;
import android.telecom.Connection;
import android.telecom.ConnectionRequest;
import android.telecom.ConnectionService;
import android.telecom.DisconnectCause;
import android.telecom.PhoneAccountHandle;
import android.telecom.TelecomManager;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.junit.Assert.assertTrue;

/**
 * CTS test self-managed {@link ConnectionService} implementation.
 */
public class CtsSelfManagedConnectionService extends ConnectionService {
    // Constants used to address into the mLocks array.
    public static int CONNECTION_CREATED_LOCK = 0;
    public static int CREATE_INCOMING_CONNECTION_FAILED_LOCK = 1;
    public static int CREATE_OUTGOING_CONNECTION_FAILED_LOCK = 2;
    private static int NUM_LOCKS = CREATE_OUTGOING_CONNECTION_FAILED_LOCK + 1;

    private static CtsSelfManagedConnectionService sConnectionService;

    // Lock used to determine when binding to CS is complete.
    private static CountDownLatch sBindingLock = new CountDownLatch(1);

    private SelfManagedConnection.Listener mConnectionListener =
            new SelfManagedConnection.Listener() {
        @Override
        void onDestroyed(SelfManagedConnection connection) {
            mConnections.remove(connection);
        }
    };

    private CountDownLatch[] mLocks = new CountDownLatch[NUM_LOCKS];

    private Object mLock = new Object();
    private List<SelfManagedConnection> mConnections = new ArrayList<>();

    public static CtsSelfManagedConnectionService getConnectionService() {
        return sConnectionService;
    }

    public CtsSelfManagedConnectionService() throws Exception {
        super();
        sConnectionService = this;
        Arrays.setAll(mLocks, i -> new CountDownLatch(1));

        // Inform anyone waiting on binding that we're bound.
        sBindingLock.countDown();
    }

    @Override
    public boolean onUnbind(Intent intent) {
        sBindingLock = new CountDownLatch(1);
        return super.onUnbind(intent);
    }

    @Override
    public Connection onCreateOutgoingConnection(PhoneAccountHandle connectionManagerAccount,
            final ConnectionRequest request) {

        return createSelfManagedConnection(request, false);
    }

    @Override
    public Connection onCreateIncomingConnection(PhoneAccountHandle connectionManagerPhoneAccount,
            ConnectionRequest request) {

        return createSelfManagedConnection(request, true);
    }

    @Override
    public void onCreateIncomingConnectionFailed(PhoneAccountHandle connectionManagerHandle,
                                                 ConnectionRequest request) {
        mLocks[CREATE_INCOMING_CONNECTION_FAILED_LOCK].countDown();
    }

    @Override
    public void onCreateOutgoingConnectionFailed(PhoneAccountHandle connectionManagerHandle,
                                                 ConnectionRequest request) {
        mLocks[CREATE_OUTGOING_CONNECTION_FAILED_LOCK].countDown();
    }

    public void tearDown() {
        synchronized(mLock) {
            if (mConnections != null && mConnections.size() > 0) {
                mConnections.forEach(connection -> {
                            connection.setDisconnected(new DisconnectCause(DisconnectCause.LOCAL));
                            connection.destroy();
                        }
                );
                mConnections.clear();
            }
        }
        sBindingLock = new CountDownLatch(1);
    }

    private Connection createSelfManagedConnection(ConnectionRequest request, boolean isIncoming) {
        SelfManagedConnection connection = new SelfManagedConnection(isIncoming,
                mConnectionListener);
        connection.setConnectionProperties(Connection.PROPERTY_SELF_MANAGED);
        connection.setAddress(request.getAddress(), TelecomManager.PRESENTATION_ALLOWED);
        connection.setExtras(request.getExtras());

        Bundle moreExtras = new Bundle();
        moreExtras.putParcelable(TelecomManager.EXTRA_PHONE_ACCOUNT_HANDLE,
                request.getAccountHandle());
        connection.putExtras(moreExtras);
        connection.setVideoState(request.getVideoState());

        synchronized(mLock) {
            mConnections.add(connection);
        }
        mLocks[CONNECTION_CREATED_LOCK].countDown();
        return connection;
    }

    public List<SelfManagedConnection> getConnections() {
        synchronized(mLock) {
            return new ArrayList<>(mConnections);
        }
    }

    /**
     * Waits on a lock for maximum of 5 seconds.
     *
     * @param lock one of the {@code *_LOCK} constants defined above.
     * @return {@code true} if the lock was released within the time limit, {@code false} if the
     *      timeout expired without the lock being released.
     */
    public boolean waitForUpdate(int lock) {
        mLocks[lock] = waitForLock(mLocks[lock]);
        return mLocks[lock] != null;
    }

    /**
     * Waits for the {@link ConnectionService} to be found.
     * @return {@code true} if binding happened within the time limit, or {@code false} otherwise.
     */
    public static boolean waitForBinding() {
        sBindingLock = waitForLock(sBindingLock);
        return sBindingLock != null;
    }

    /**
     * Given a {@link CountDownLatch}, wait for the latch to reach zero for 5 seconds.  If the lock
     * was released, return a new instance.  Otherwise, return null to indicate that the timeout
     * expired without the lock being released.
     *
     * @param lock The lock to wait on.
     * @return {@code true} if the lock was released, and {@code false} if it failed to be released.
     */
    private static CountDownLatch waitForLock(CountDownLatch lock) {
        boolean success;
        try {
            success = lock.await(5000, TimeUnit.MILLISECONDS);
        } catch (InterruptedException ie) {
            return null;
        }

        if (success) {
            return new CountDownLatch(1);
        } else {
            return null;
        }
    }
}
