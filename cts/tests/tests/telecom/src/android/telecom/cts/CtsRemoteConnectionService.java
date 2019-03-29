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

import static org.junit.Assert.assertFalse;

import android.content.Intent;
import android.telecom.Conference;
import android.telecom.Connection;
import android.telecom.ConnectionRequest;
import android.telecom.ConnectionService;
import android.telecom.PhoneAccountHandle;
import android.util.Log;

/**
 * This is the Remote ConnectionService for Telecom's CTS App. Since telecom requires that a
 * CS be registered in the AndroidManifest.xml file, we have to have a single implementation
 * of a CS and this is it. To test specific CS behavior, tests will implement their own CS and
 * tell CtsRemoteConnectionService to forward any method invocations to that test's implementation.
 * This is set up using {@link #setUp} and should be cleaned up before the end of the test using
 * {@link #tearDown}.
 *
 * sConnectionService: Contains the connection service object provided by the current test in
 *                     progress. We use this object to forward any communication received from the
 *                     Telecom framework to the test connection service.
 * sTelecomConnectionService: Contains the connection service object registered to the Telecom
 *                            framework. We use this object to forward any communication from the
 *                            test connection service to the Telecom framework.
 *
 */
public class CtsRemoteConnectionService extends ConnectionService {
    private static String LOG_TAG = "CtsConnectionService";
    // This is the connection service implemented by the test
    private static ConnectionService sConnectionService;
    // This is the connection service registered with Telecom
    private static ConnectionService sTelecomConnectionService;
    private static boolean mIsServiceUnbound;

    public CtsRemoteConnectionService() throws Exception {
        super();
        if (sTelecomConnectionService != null) {
            throw new Exception("Telecom ConnectionService exists");
        }
        sTelecomConnectionService = this;
    }

    // ConnectionService used by default as a fallback if no connection service is specified
    // during test setup.
    private static ConnectionService mMockConnectionService = new MockConnectionService();

    /**
     * Used to control whether the {@link MockVideoProvider} will be created when connections are
     * created.  Used by {@link VideoCallTest#testVideoCallDelayProvider()} to test scenario where
     * the {@link MockVideoProvider} is not created immediately when the Connection is created.
     */
    private static Object sLock = new Object();

    public static void setUp(PhoneAccountHandle phoneAccountHandle,
            ConnectionService connectionService) throws Exception {
        synchronized(sLock) {
            if (sConnectionService != null) {
                throw new Exception("Mock ConnectionService exists.  Failed to call tearDown().");
            }
            sConnectionService = connectionService;
            // Cant override the onBind method for ConnectionService, so reset it here.
            mIsServiceUnbound = false;
        }
    }

    public static void tearDown() {
        synchronized(sLock) {
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
                return mMockConnectionService.onCreateOutgoingConnection(
                        connectionManagerPhoneAccount, request);
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
                return mMockConnectionService.onCreateIncomingConnection(
                        connectionManagerPhoneAccount, request);
            }
        }
    }

    @Override
    public void onConference(Connection connection1, Connection connection2) {
        synchronized(sLock) {
            if (sConnectionService != null) {
                sConnectionService.onConference(connection1, connection2);
            } else {
                mMockConnectionService.onConference(connection1, connection2);
            }
        }
    }

    public static void addConferenceToTelecom(Conference conference) {
        synchronized(sLock) {
            sTelecomConnectionService.addConference(conference);
        }
    }

    @Override
    public boolean onUnbind(Intent intent) {
        Log.i(LOG_TAG, "Service unbounded");
        assertFalse(mIsServiceUnbound);
        mIsServiceUnbound = true;
        return super.onUnbind(intent);
    }

    public static boolean isServiceUnbound() {
        return mIsServiceUnbound;
    }
}
