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

import android.net.Uri;
import android.os.Bundle;
import android.telecom.CallAudioState;
import android.telecom.PhoneAccount;
import android.telecom.PhoneAccountHandle;
import android.telecom.TelecomManager;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;

import static android.telecom.cts.TestUtils.WAIT_FOR_STATE_CHANGE_TIMEOUT_MS;
import static android.telecom.cts.TestUtils.waitOnAllHandlers;

/**
 * CTS tests for the self-managed {@link android.telecom.ConnectionService} APIs.
 * For more information about these APIs, see {@link android.telecom}, and
 * {@link android.telecom.PhoneAccount#CAPABILITY_SELF_MANAGED}.
 */

public class SelfManagedConnectionServiceTest extends BaseTelecomTestWithMockServices {
    private Uri TEST_ADDRESS_1 = Uri.fromParts("sip", "call1@test.com", null);
    private Uri TEST_ADDRESS_2 = Uri.fromParts("sip", "call2@test.com", null);

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        mContext = getInstrumentation().getContext();
        if (mShouldTestTelecom) {
            // Register and enable the CTS ConnectionService; we want to be able to test a managed
            // ConnectionService alongside a self-managed ConnectionService.
            setupConnectionService(null, FLAG_REGISTER | FLAG_ENABLE);

            mTelecomManager.registerPhoneAccount(TestUtils.TEST_SELF_MANAGED_PHONE_ACCOUNT_1);
            mTelecomManager.registerPhoneAccount(TestUtils.TEST_SELF_MANAGED_PHONE_ACCOUNT_2);
        }

    }

    @Override
    protected void tearDown() throws Exception {
        super.tearDown();

        CtsSelfManagedConnectionService connectionService =
                CtsSelfManagedConnectionService.getConnectionService();
        if (connectionService != null) {
            connectionService.tearDown();
            mTelecomManager.unregisterPhoneAccount(TestUtils.TEST_SELF_MANAGED_HANDLE_1);
            mTelecomManager.unregisterPhoneAccount(TestUtils.TEST_SELF_MANAGED_HANDLE_2);
        }
    }

    /**
     * Tests {@link TelecomManager#getSelfManagedPhoneAccounts()} API to ensure it returns a list of
     * the registered self-managed {@link android.telecom.PhoneAccount}s.
     */
    public void testTelecomManagerGetSelfManagedPhoneAccounts() {
        if (!mShouldTestTelecom) {
            return;
        }

        List<PhoneAccountHandle> phoneAccountHandles =
                mTelecomManager.getSelfManagedPhoneAccounts();

        assertTrue(phoneAccountHandles.contains(TestUtils.TEST_SELF_MANAGED_HANDLE_1));
        assertTrue(phoneAccountHandles.contains(TestUtils.TEST_SELF_MANAGED_HANDLE_2));
        assertFalse(phoneAccountHandles.contains(TestUtils.TEST_PHONE_ACCOUNT_HANDLE));
    }

    /**
     * Tests the ability to successfully register a self-managed
     * {@link android.telecom.PhoneAccount}.
     */
    public void testRegisterSelfManagedConnectionService() {
        if (!mShouldTestTelecom) {
            return;
        }

        // The phone account is registered in the setup method.
        assertPhoneAccountRegistered(TestUtils.TEST_SELF_MANAGED_HANDLE_1);
        assertPhoneAccountEnabled(TestUtils.TEST_SELF_MANAGED_HANDLE_1);
        PhoneAccount registeredAccount = mTelecomManager.getPhoneAccount(
                TestUtils.TEST_SELF_MANAGED_HANDLE_1);

        // It should exist and be the same as the previously registered one.
        assertNotNull(registeredAccount);

        // We cannot just check for equality of the PhoneAccount since the one we registered is not
        // enabled, and the one we get back after registration is.
        assertPhoneAccountEquals(TestUtils.TEST_SELF_MANAGED_PHONE_ACCOUNT_1, registeredAccount);

        // An important asumption is that self-managed PhoneAccounts are automatically
        // enabled by default.
        assertTrue("Self-managed PhoneAccounts must be enabled by default.",
                registeredAccount.isEnabled());
    }

    /**
     * This test ensures that a {@link android.telecom.PhoneAccount} declared as self-managed cannot
     * but is also registered as a call provider is not permitted.
     *
     * A self-managed {@link android.telecom.PhoneAccount} cannot also be a call provider.
     */
    public void testRegisterCallCapableSelfManagedConnectionService() {
        if (!mShouldTestTelecom) {
            return;
        }

        // Attempt to register both a call provider and self-managed account.
        PhoneAccount toRegister = TestUtils.TEST_SELF_MANAGED_PHONE_ACCOUNT_1.toBuilder()
                .setCapabilities(PhoneAccount.CAPABILITY_SELF_MANAGED |
                        PhoneAccount.CAPABILITY_CALL_PROVIDER)
                .build();

        registerAndExpectFailure(toRegister);
    }

    /**
     * This test ensures that a {@link android.telecom.PhoneAccount} declared as self-managed cannot
     * but is also registered as a sim subscription is not permitted.
     *
     * A self-managed {@link android.telecom.PhoneAccount} cannot also be a SIM subscription.
     */
    public void testRegisterSimSelfManagedConnectionService() {
        if (!mShouldTestTelecom) {
            return;
        }

        // Attempt to register both a call provider and self-managed account.
        PhoneAccount toRegister = TestUtils.TEST_SELF_MANAGED_PHONE_ACCOUNT_1.toBuilder()
                .setCapabilities(PhoneAccount.CAPABILITY_SELF_MANAGED |
                        PhoneAccount.CAPABILITY_SIM_SUBSCRIPTION)
                .build();

        registerAndExpectFailure(toRegister);
    }

    /**
     * This test ensures that a {@link android.telecom.PhoneAccount} declared as self-managed cannot
     * but is also registered as a connection manager is not permitted.
     *
     * A self-managed {@link android.telecom.PhoneAccount} cannot also be a connection manager.
     */
    public void testRegisterConnectionManagerSelfManagedConnectionService() {
        if (!mShouldTestTelecom) {
            return;
        }

        // Attempt to register both a call provider and self-managed account.
        PhoneAccount toRegister = TestUtils.TEST_SELF_MANAGED_PHONE_ACCOUNT_1.toBuilder()
                .setCapabilities(PhoneAccount.CAPABILITY_SELF_MANAGED |
                        PhoneAccount.CAPABILITY_CONNECTION_MANAGER)
                .build();

        registerAndExpectFailure(toRegister);
    }

    /**
     * Attempts to register a {@link android.telecom.PhoneAccount}, expecting a security exception
     * which indicates that invalid capabilities were specified.
     *
     * @param toRegister The PhoneAccount to register.
     */
    private void registerAndExpectFailure(PhoneAccount toRegister) {
        try {
            mTelecomManager.registerPhoneAccount(toRegister);
        } catch (SecurityException se) {
            assertEquals("Self-managed ConnectionServices cannot also be call capable, " +
                    "connection managers, or SIM accounts.", se.getMessage());
            return;
        }
        fail("Expected SecurityException");
    }

    /**
     * Tests ability to add a new self-managed incoming connection.
     */
    public void testAddSelfManagedIncomingConnection() throws Exception {
        if (!mShouldTestTelecom) {
            return;
        }

        TestUtils.addIncomingCall(getInstrumentation(), mTelecomManager,
                TestUtils.TEST_SELF_MANAGED_HANDLE_1, TEST_ADDRESS_1);

        // Ensure Telecom bound to the self managed CS
        if (!CtsSelfManagedConnectionService.waitForBinding()) {
            fail("Could not bind to Self-Managed ConnectionService");
        }

        SelfManagedConnection connection = TestUtils.waitForAndGetConnection(TEST_ADDRESS_1);

        // Expect callback indicating that UI should be shown.
        connection.getOnShowIncomingUiInvokeCounter().waitForCount(1);
        setActiveAndVerify(connection);

        // Expect there to be no managed calls at the moment.
        assertFalse(mTelecomManager.isInManagedCall());

        setDisconnectedAndVerify(connection);
    }

    /**
     * Tests ability to add a new self-managed outgoing connection.
     */
    public void testAddSelfManagedOutgoingConnection() throws Exception {
        if (!mShouldTestTelecom) {
            return;
        }
        TestUtils.placeOutgoingCall(getInstrumentation(), mTelecomManager,
                TestUtils.TEST_SELF_MANAGED_HANDLE_1, TEST_ADDRESS_1);

        // Ensure Telecom bound to the self managed CS
        if (!CtsSelfManagedConnectionService.waitForBinding()) {
            fail("Could not bind to Self-Managed ConnectionService");
        }

        SelfManagedConnection connection = TestUtils.waitForAndGetConnection(TEST_ADDRESS_1);
        assert(!connection.isIncomingCall());

        assertEquals(connection.getOnShowIncomingUiInvokeCounter().getInvokeCount(), 0);

        setActiveAndVerify(connection);

        // Expect there to be no managed calls at the moment.
        assertFalse(mTelecomManager.isInManagedCall());

        setDisconnectedAndVerify(connection);
    }

    /**
     * Tests ability to change the audio route via the
     * {@link android.telecom.Connection#setAudioRoute(int)} API.
     */
    public void testAudioRoute() throws Exception {
        if (!mShouldTestTelecom) {
            return;
        }
        TestUtils.placeOutgoingCall(getInstrumentation(), mTelecomManager,
                TestUtils.TEST_SELF_MANAGED_HANDLE_1, TEST_ADDRESS_1);
        SelfManagedConnection connection = TestUtils.waitForAndGetConnection(TEST_ADDRESS_1);
        setActiveAndVerify(connection);

        TestUtils.InvokeCounter counter = connection.getCallAudioStateChangedInvokeCounter();
        counter.waitForCount(WAIT_FOR_STATE_CHANGE_TIMEOUT_MS);
        CallAudioState callAudioState = (CallAudioState) counter.getArgs(0)[0];
        int availableRoutes = callAudioState.getSupportedRouteMask();

        // Both the speaker and either wired or earpiece are required to test changing the audio
        // route. Skip this test if either of these routes is unavailable.
        if ((availableRoutes & CallAudioState.ROUTE_SPEAKER) == 0
                || (availableRoutes & CallAudioState.ROUTE_WIRED_OR_EARPIECE) == 0) {
            return;
        }

        // Determine what the second route after SPEAKER should be, depending on what's supported.
        int secondRoute = (availableRoutes & CallAudioState.ROUTE_EARPIECE) == 0
                ? CallAudioState.ROUTE_WIRED_HEADSET
                : CallAudioState.ROUTE_EARPIECE;

        connection.setAudioRoute(CallAudioState.ROUTE_SPEAKER);
        counter.waitForPredicate(new Predicate<CallAudioState>() {
                @Override
                public boolean test(CallAudioState cas) {
                    return cas.getRoute() == CallAudioState.ROUTE_SPEAKER;
                }
            }, WAIT_FOR_STATE_CHANGE_TIMEOUT_MS);

        connection.setAudioRoute(secondRoute);
        counter.waitForPredicate(new Predicate<CallAudioState>() {
            @Override
            public boolean test(CallAudioState cas) {
                return cas.getRoute() == secondRoute;
            }
        }, WAIT_FOR_STATE_CHANGE_TIMEOUT_MS);

        setDisconnectedAndVerify(connection);
    }

    /**
     * Tests that Telecom will disallow an outgoing call when there is already an ongoing call in
     * another third-party app.
     * @throws Exception
     */
    public void testDisallowOutgoingCall() throws Exception {
        if (!mShouldTestTelecom) {
            return;
        }

        // Create an ongoing call in the first self-managed PhoneAccount.
        TestUtils.placeOutgoingCall(getInstrumentation(), mTelecomManager,
                TestUtils.TEST_SELF_MANAGED_HANDLE_1, TEST_ADDRESS_1);
        SelfManagedConnection connection = TestUtils.waitForAndGetConnection(TEST_ADDRESS_1);
        setActiveAndVerify(connection);

        // Attempt to create a new outgoing call for the other PhoneAccount; it should fail.
        TestUtils.placeOutgoingCall(getInstrumentation(), mTelecomManager,
                TestUtils.TEST_SELF_MANAGED_HANDLE_2, TEST_ADDRESS_2);
        assertTrue("Expected onCreateOutgoingConnectionFailed callback",
                CtsSelfManagedConnectionService.getConnectionService().waitForUpdate(
                    CtsSelfManagedConnectionService.CREATE_OUTGOING_CONNECTION_FAILED_LOCK));

        setDisconnectedAndVerify(connection);
    }

    /**
     * Tests that Telecom will disallow an outgoing call when there is already an ongoing call in
     * another third-party app.
     * @throws Exception
     */
    public void testIncomingWhileOngoing() throws Exception {
        if (!mShouldTestTelecom) {
            return;
        }

        // Create an ongoing call in the first self-managed PhoneAccount.
        TestUtils.placeOutgoingCall(getInstrumentation(), mTelecomManager,
                TestUtils.TEST_SELF_MANAGED_HANDLE_1, TEST_ADDRESS_1);
        SelfManagedConnection connection = TestUtils.waitForAndGetConnection(TEST_ADDRESS_1);
        setActiveAndVerify(connection);

        // Attempt to create a new outgoing call for the other PhoneAccount; it should succeed.
        TestUtils.addIncomingCall(getInstrumentation(), mTelecomManager,
                TestUtils.TEST_SELF_MANAGED_HANDLE_2, TEST_ADDRESS_2);
        SelfManagedConnection connection2 = TestUtils.waitForAndGetConnection(TEST_ADDRESS_2);

        connection2.disconnectAndDestroy();
        setDisconnectedAndVerify(connection);
    }

    /**
     * Tests that Telecom enforces a maximum number of calls for a self-managed ConnectionService.
     *
     * @throws Exception
     */
    public void testCallLimit() throws Exception {
        if (!mShouldTestTelecom) {
            return;
        }

        List<SelfManagedConnection> connections = new ArrayList<>();
        // Create 10 calls; they should succeed.
        for (int ix = 0; ix < 10; ix++) {
            Uri address = Uri.fromParts("sip", "test" + ix + "@test.com", null);
            // Create an ongoing call in the first self-managed PhoneAccount.
            TestUtils.placeOutgoingCall(getInstrumentation(), mTelecomManager,
                    TestUtils.TEST_SELF_MANAGED_HANDLE_1, address);
            SelfManagedConnection connection = TestUtils.waitForAndGetConnection(address);
            setActiveAndVerify(connection);
            connections.add(connection);
        }

        // Try adding an 11th.  It should fail to be created.
        TestUtils.addIncomingCall(getInstrumentation(), mTelecomManager,
                TestUtils.TEST_SELF_MANAGED_HANDLE_1, TEST_ADDRESS_2);
        assertTrue("Expected onCreateIncomingConnectionFailed callback",
                CtsSelfManagedConnectionService.getConnectionService().waitForUpdate(
                        CtsSelfManagedConnectionService.CREATE_INCOMING_CONNECTION_FAILED_LOCK));

        connections.forEach((selfManagedConnection) ->
                selfManagedConnection.disconnectAndDestroy());

        waitOnAllHandlers(getInstrumentation());
    }

    /**
     * Disabled for now; there is not a reliable means of setting a phone number as a test emergency
     * number.
     * @throws Exception
     */
    public void DONOTtestEmergencyCallOngoing() throws Exception {
        if (!mShouldTestTelecom) {
            return;
        }

        // TODO: Need to find a reliable way to set a test emergency number.
        // Set 555-1212 as a test emergency number.
        TestUtils.executeShellCommand(getInstrumentation(), "setprop ril.ecclist 5551212");

        Bundle extras = new Bundle();
        extras.putParcelable(TelecomManager.EXTRA_PHONE_ACCOUNT_HANDLE,
                TestUtils.TEST_PHONE_ACCOUNT_HANDLE);
        mTelecomManager.placeCall(Uri.fromParts(PhoneAccount.SCHEME_TEL, "5551212", null), extras);
        assertIsInCall(true);
        assertIsInManagedCall(true);
        try {
            TestUtils.waitOnAllHandlers(getInstrumentation());
        } catch (Exception e) {
            fail("Failed to wait on handlers " + e);
        }

        // Try adding a self managed call.  It should fail to be created.
        TestUtils.addIncomingCall(getInstrumentation(), mTelecomManager,
                TestUtils.TEST_SELF_MANAGED_HANDLE_1, TEST_ADDRESS_1);
        assertTrue("Expected onCreateIncomingConnectionFailed callback",
                CtsSelfManagedConnectionService.getConnectionService().waitForUpdate(
                        CtsSelfManagedConnectionService.CREATE_INCOMING_CONNECTION_FAILED_LOCK));
    }

    /**
     * Sets a connection active, and verifies TelecomManager thinks we're in call but not in a
     * managed call.
     * @param connection The connection.
     */
    private void setActiveAndVerify(SelfManagedConnection connection) throws Exception {
        // Set the connection active.
        connection.setActive();

        // Check with Telecom if we're in a call.
        assertIsInCall(true);
        assertIsInManagedCall(false);
    }

    /**
     * Sets a connection to be disconnected, and then waits until the TelecomManager reports it is
     * no longer in a call.
     *
     * @param connection The connection to disconnect/destroy.
     */
    private void setDisconnectedAndVerify(SelfManagedConnection connection) {
        // Now, disconnect call and clean it up.
        connection.disconnectAndDestroy();

        assertIsInCall(false);
        assertIsInManagedCall(false);
    }
}
