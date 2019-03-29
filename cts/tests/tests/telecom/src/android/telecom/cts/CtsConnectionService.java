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

import static org.junit.Assert.assertTrue;

import android.content.Intent;
import android.telecom.Conference;
import android.telecom.Connection;
import android.telecom.ConnectionRequest;
import android.telecom.ConnectionService;
import android.telecom.PhoneAccountHandle;
import android.telecom.RemoteConference;
import android.telecom.RemoteConnection;
import android.util.Log;

import java.util.Collection;
import java.util.Collections;
import java.util.concurrent.CountDownLatch;

/**
 * This is the official ConnectionService for Telecom's CTS App. Since telecom requires that a
 * CS be registered in the AndroidManifest.xml file, we have to have a single implementation
 * of a CS and this is it. To test specific CS behavior, tests will implement their own CS and
 * tell CtsConnectionService to forward any method invocations to that test's implementation.
 * This is set up using {@link #setUp} and should be cleaned up before the end of the test using
 * {@link #tearDown}.
 *
 * sConnectionService: Contains the connection service object provided by the current test in
 *                     progress. We use this object to forward any communication received from the
 *                     Telecom framework to the test connection service.
 * sTelecomConnectionService: Contains the connection service object registered to the Telecom
 *                            framework. We use this object to forward any communication from the
 *                            test connection service to the Telecom framework. After Telecom
 *                            binds to CtsConnectionService, this is set to be the instance of
 *                            CtsConnectionService created by the framework after Telecom binds.
 */
public class CtsConnectionService extends ConnectionService {
    private static String LOG_TAG = "CtsConnectionService";
    // This is the connection service implemented by the test
    private static ConnectionService sConnectionService;
    // This is the connection service registered with Telecom
    private static ConnectionService sTelecomConnectionService;
    private static boolean sIsBound = false;
    private static CountDownLatch sServiceUnBoundLatch = new CountDownLatch(1);

    public CtsConnectionService() throws Exception {
        super();
        sTelecomConnectionService = this;
        sIsBound = true;
    }

    private static Object sLock = new Object();

    public static void setUp(ConnectionService connectionService) throws Exception {
        synchronized(sLock) {
            if (sConnectionService != null) {
                throw new Exception("Mock ConnectionService exists.  Failed to call tearDown().");
            }
            sConnectionService = connectionService;
        }
    }

    public static void tearDown() {
        synchronized(sLock) {
            sTelecomConnectionService = null;
            sConnectionService = null;
        }
    }

    @Override
    public Connection onCreateOutgoingConnection(PhoneAccountHandle connectionManagerPhoneAccount,
            ConnectionRequest request) {
        synchronized(sLock) {
            if (sConnectionService != null) {
                return sConnectionService.onCreateOutgoingConnection(
                        connectionManagerPhoneAccount, request);
            } else {
                return null;
            }
        }
    }

    @Override
    public Connection onCreateIncomingConnection(PhoneAccountHandle connectionManagerPhoneAccount,
            ConnectionRequest request) {
        synchronized(sLock) {
            if (sConnectionService != null) {
                return sConnectionService.onCreateIncomingConnection(
                        connectionManagerPhoneAccount, request);
            } else {
                return null;
            }
        }
    }

    @Override
    public void onConference(Connection connection1, Connection connection2) {
        synchronized(sLock) {
            if (sConnectionService != null) {
                sConnectionService.onConference(connection1, connection2);
            }
        }
    }

    @Override
    public void onRemoteExistingConnectionAdded(RemoteConnection connection) {
        synchronized(sLock) {
            if (sConnectionService != null) {
                sConnectionService.onRemoteExistingConnectionAdded(connection);
            }
        }
    }

    public static void addConferenceToTelecom(Conference conference) {
        synchronized(sLock) {
            sTelecomConnectionService.addConference(conference);
        }
    }

    public static void addExistingConnectionToTelecom(
            PhoneAccountHandle phoneAccountHandle, Connection connection) {
        synchronized(sLock) {
            sTelecomConnectionService.addExistingConnection(phoneAccountHandle, connection);
        }
    }

    public static Collection<Connection> getAllConnectionsFromTelecom() {
        synchronized(sLock) {
            if (sTelecomConnectionService == null) {
                return Collections.EMPTY_LIST;
            }
            return sTelecomConnectionService.getAllConnections();
        }
    }

    public static RemoteConnection createRemoteOutgoingConnectionToTelecom(
            PhoneAccountHandle connectionManagerPhoneAccount,
            ConnectionRequest request) {
        synchronized(sLock) {
            return sTelecomConnectionService.createRemoteOutgoingConnection(
                    connectionManagerPhoneAccount, request);
        }
    }

    public static RemoteConnection createRemoteIncomingConnectionToTelecom(
            PhoneAccountHandle connectionManagerPhoneAccount,
            ConnectionRequest request) {
        synchronized(sLock) {
            return sTelecomConnectionService.createRemoteIncomingConnection(
                    connectionManagerPhoneAccount, request);
        }
    }

    @Override
    public void onRemoteConferenceAdded(RemoteConference conference) {
        synchronized(sLock) {
            if (sConnectionService != null) {
                sConnectionService.onRemoteConferenceAdded(conference);
            }
        }
    }

    @Override
    public boolean onUnbind(Intent intent) {
        Log.i(LOG_TAG, "Service has been unbound");
        sServiceUnBoundLatch.countDown();
        sIsBound = false;
        sConnectionService = null;
        return super.onUnbind(intent);
    }

    public static boolean isServiceRegisteredToTelecom() {
        return sTelecomConnectionService != null;
    }

    public static boolean isBound() {
        return sIsBound;
    }

    public static boolean waitForUnBinding() {
        sServiceUnBoundLatch = TestUtils.waitForLock(sServiceUnBoundLatch);
        return sServiceUnBoundLatch != null;
    }
}
