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

import android.content.ComponentName;
import android.content.Context;
import android.graphics.Color;
import android.net.Uri;
import android.telecom.Call;
import android.telecom.PhoneAccount;
import android.telecom.PhoneAccountHandle;
import android.telecom.TelecomManager;
import android.test.InstrumentationTestCase;
import android.text.TextUtils;

import static android.telecom.cts.TestUtils.SELF_MANAGED_ACCOUNT_ID_1;
import static android.telecom.cts.TestUtils.SELF_MANAGED_ACCOUNT_LABEL;
import static android.telecom.cts.TestUtils.SELF_MANAGED_COMPONENT;

/**
 * Tests that an {@link android.telecom.InCallService} which has declared support for
 * self-managed {@link android.telecom.Call}s is able to receive those calls.
 */
public class SelfManagedAwareInCallServiceTest extends InstrumentationTestCase {
    private Uri TEST_ADDRESS_1 = Uri.fromParts("sip", "call1@test.com", null);
    private Uri TEST_ADDRESS_2 = Uri.fromParts("sip", "call2@test.com", null);
    private static String PACKAGE = "android.telecom3.cts";

    private TelecomManager mTelecomManager;
    private boolean mShouldTestTelecom = true;
    private String mPreviousDefaultDialer;

    public static final PhoneAccountHandle TEST_SELF_MANAGED_HANDLE =
            new PhoneAccountHandle(new ComponentName(PACKAGE, SELF_MANAGED_COMPONENT),
                    SELF_MANAGED_ACCOUNT_ID_1);

    public static final PhoneAccount TEST_SELF_MANAGED_PHONE_ACCOUNT = PhoneAccount.builder(
            TEST_SELF_MANAGED_HANDLE, SELF_MANAGED_ACCOUNT_LABEL)
            .setAddress(Uri.parse("sip:test@test.com"))
            .setSubscriptionAddress(Uri.parse("sip:test@test.com"))
            .setCapabilities(PhoneAccount.CAPABILITY_SELF_MANAGED |
                    PhoneAccount.CAPABILITY_SUPPORTS_VIDEO_CALLING |
                    PhoneAccount.CAPABILITY_VIDEO_CALLING)
            .setHighlightColor(Color.BLUE)
            .setShortDescription(SELF_MANAGED_ACCOUNT_LABEL)
            .addSupportedUriScheme(PhoneAccount.SCHEME_TEL)
            .addSupportedUriScheme(PhoneAccount.SCHEME_SIP)
            .build();

    @Override
    protected void setUp() throws Exception {
        super.setUp();

        Context context = getInstrumentation().getContext();
        mTelecomManager = (TelecomManager) context.getSystemService(Context.TELECOM_SERVICE);

        mShouldTestTelecom = TestUtils.shouldTestTelecom(context);
        if (mShouldTestTelecom) {
            mPreviousDefaultDialer = TestUtils.getDefaultDialer(getInstrumentation());
            TestUtils.setDefaultDialer(getInstrumentation(), PACKAGE);

            mTelecomManager.registerPhoneAccount(TEST_SELF_MANAGED_PHONE_ACCOUNT);

            TestUtils.waitOnAllHandlers(getInstrumentation());
        }

    }

    @Override
    protected void tearDown() throws Exception {
        super.tearDown();

        if (mShouldTestTelecom) {
            if (!TextUtils.isEmpty(mPreviousDefaultDialer)) {
                TestUtils.setDefaultDialer(getInstrumentation(), mPreviousDefaultDialer);
            }

            mTelecomManager.unregisterPhoneAccount(TEST_SELF_MANAGED_HANDLE);

            CtsSelfManagedConnectionService connectionService =
                    CtsSelfManagedConnectionService.getConnectionService();
            if (connectionService != null) {
                connectionService.tearDown();
            }

            SelfManagedAwareInCallService inCallService =
                    SelfManagedAwareInCallService.getInCallService();
            if (inCallService != null) {
                inCallService.tearDown();

                SelfManagedAwareInCallService.waitForUnBinding();
            }
        }
    }

    /**
     * Verifies that a {@link android.telecom.InCallService} which has specified
     * {@link TelecomManager#METADATA_INCLUDE_SELF_MANAGED_CALLS} will receive self-managed calls.
     */
    public void testInCallServiceOutgoing() {
        if (!mShouldTestTelecom) {
            return;
        }

        TestUtils.placeOutgoingCall(getInstrumentation(), mTelecomManager, TEST_SELF_MANAGED_HANDLE,
                TEST_ADDRESS_1);
        assertTrue(SelfManagedAwareInCallService.waitForBinding());
        SelfManagedConnection connection = TestUtils.waitForAndGetConnection(TEST_ADDRESS_1);
        assertNotNull(connection);

        Call call = SelfManagedAwareInCallService.getInCallService().waitForCallAdded();
        assertNotNull(call);
        assertTrue(call.getDetails().hasProperty(Call.Details.PROPERTY_SELF_MANAGED));
        assertEquals(TEST_ADDRESS_1, call.getDetails().getHandle());

        // Now, disconnect call and clean it up.
        connection.disconnectAndDestroy();
    }

    /**
     * Verifies that a {@link android.telecom.InCallService} which has specified
     * {@link TelecomManager#METADATA_INCLUDE_SELF_MANAGED_CALLS} will receive self-managed calls.
     */
    public void testInCallServiceIncoming() {
        if (!mShouldTestTelecom) {
            return;
        }

        TestUtils.addIncomingCall(getInstrumentation(), mTelecomManager, TEST_SELF_MANAGED_HANDLE,
                TEST_ADDRESS_1);
        assertTrue(SelfManagedAwareInCallService.waitForBinding());
        SelfManagedConnection connection = TestUtils.waitForAndGetConnection(TEST_ADDRESS_1);
        assertNotNull(connection);

        Call call = SelfManagedAwareInCallService.getInCallService().waitForCallAdded();
        assertNotNull(call);
        assertTrue(call.getDetails().hasProperty(Call.Details.PROPERTY_SELF_MANAGED));
        assertEquals(TEST_ADDRESS_1, call.getDetails().getHandle());

        // Now, disconnect call and clean it up.
        connection.disconnectAndDestroy();
    }

    /**
     * Verifies basic end to end call signalling for self-managed connctions.
     */
    public void testSelfManagedSignalling() {
        if (!mShouldTestTelecom) {
            return;
        }

        TestUtils.placeOutgoingCall(getInstrumentation(), mTelecomManager, TEST_SELF_MANAGED_HANDLE,
                TEST_ADDRESS_1);
        assertTrue(SelfManagedAwareInCallService.waitForBinding());
        SelfManagedConnection connection = TestUtils.waitForAndGetConnection(TEST_ADDRESS_1);
        assertNotNull(connection);

        Call call = SelfManagedAwareInCallService.getInCallService().waitForCallAdded();
        assertNotNull(call);
        assertTrue(call.getDetails().hasProperty(Call.Details.PROPERTY_SELF_MANAGED));
        assertEquals(TEST_ADDRESS_1, call.getDetails().getHandle());

        SelfManagedAwareInCallService.CallCallback callbacks =
                SelfManagedAwareInCallService.getInCallService().getCallCallback(call);
        assertNotNull(callbacks);

        // Call will first be dialing.
        assertEquals(Call.STATE_DIALING, callbacks.waitOnStateChanged());

        // Set active from the connection side.
        connection.setActive();
        assertEquals(Call.STATE_ACTIVE, callbacks.waitOnStateChanged());

        // Request hold from the call side.
        call.hold();
        assertTrue(connection.waitOnHold());

        // Set held from the connection side.
        connection.setOnHold();
        assertEquals(Call.STATE_HOLDING, callbacks.waitOnStateChanged());

        // Now, disconnect call and clean it up.
        connection.disconnectAndDestroy();
    }
}
