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
 * limitations under the License
 */

package android.telecom.cts;

import android.telecom.Call;
import android.telecom.Connection;
import android.telecom.ConnectionRequest;
import android.telecom.PhoneAccount;
import android.telecom.PhoneAccountHandle;

import static android.telecom.cts.TestUtils.WAIT_FOR_STATE_CHANGE_TIMEOUT_MS;

/**
 * Tests which verify functionality related to {@link android.telecom.Connection}s and
 * {@link android.telecom.Call}s with the
 * {@link android.telecom.Connection#PROPERTY_IS_EXTERNAL_CALL} and
 * {@link android.telecom.Call.Details#PROPERTY_IS_EXTERNAL_CALL} properties, respectively, set.
 */
public class ExternalCallTest extends BaseTelecomTestWithMockServices {
    public static final int CONNECTION_PROPERTIES = Connection.PROPERTY_IS_EXTERNAL_CALL;
    public static final int CONNECTION_CAPABILITIES = Connection.CAPABILITY_CAN_PULL_CALL;

    private Call mCall;
    private MockConnection mConnection;
    private MockInCallService mInCallService;

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        if (mShouldTestTelecom) {
            PhoneAccount account = setupConnectionService(
                    new MockConnectionService() {
                        @Override
                        public Connection onCreateOutgoingConnection(
                                PhoneAccountHandle connectionManagerPhoneAccount,
                                ConnectionRequest request) {
                            Connection connection = super.onCreateOutgoingConnection(
                                    connectionManagerPhoneAccount,
                                    request);
                            mConnection = (MockConnection) connection;
                            // Modify the connection object created with local values.
                            connection.setConnectionCapabilities(CONNECTION_CAPABILITIES);
                            connection.setConnectionProperties(CONNECTION_PROPERTIES);

                            lock.release();
                            return connection;
                        }
                    }, FLAG_REGISTER | FLAG_ENABLE);

            placeAndVerifyCall();
            verifyConnectionForOutgoingCall();

            mInCallService = mInCallCallbacks.getService();
            mCall = mInCallService.getLastCall();

            assertCallState(mCall, Call.STATE_DIALING);
            assertCallProperties(mCall, Call.Details.PROPERTY_IS_EXTERNAL_CALL);
            assertCallCapabilities(mCall, Call.Details.CAPABILITY_CAN_PULL_CALL);
        }
    }

    /**
     * Tests that a request to pull an external call via {@link Call#pullExternalCall()} is
     * communicated to the {@link Connection} via {@link Connection#onPullExternalCall()}.
     */
    public void testPullExternalCall() {
        if (!mShouldTestTelecom) {
            return;
        }

        final TestUtils.InvokeCounter counter = mConnection.getInvokeCounter(
                MockConnection.ON_PULL_EXTERNAL_CALL);
        mCall.pullExternalCall();
        counter.waitForCount(1, WAIT_FOR_STATE_CHANGE_TIMEOUT_MS);
    }

    public void testNonPullableExternalCall() {
        if (!mShouldTestTelecom) {
            return;
        }

        // Remove the pullable attribute of the connection.
        mConnection.setConnectionCapabilities(0);
        assertCallCapabilities(mCall, 0);

        final TestUtils.InvokeCounter counter = mConnection.getInvokeCounter(
                MockConnection.ON_PULL_EXTERNAL_CALL);
        // Try to pull -- we expect Telecom to absorb the request since the call is not pullable.
        mCall.pullExternalCall();
        counter.waitForCount(0, WAIT_FOR_STATE_CHANGE_TIMEOUT_MS);
    }
}
