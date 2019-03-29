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

import android.content.ComponentName;
import android.os.Bundle;
import android.telecom.Connection;
import android.telecom.PhoneAccountHandle;
import android.telecom.TelecomManager;

import java.util.Collection;

import static android.telecom.cts.TestUtils.COMPONENT;
import static android.telecom.cts.TestUtils.PACKAGE;

/**
 * Tests valid/invalid incoming calls that are received from the ConnectionService
 * and registered through TelecomManager
 */
public class IncomingCallTest extends BaseTelecomTestWithMockServices {

    private static final PhoneAccountHandle TEST_INVALID_HANDLE = new PhoneAccountHandle(
            new ComponentName(PACKAGE, COMPONENT), "WRONG_ID");

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        mContext = getInstrumentation().getContext();
    }

    public void testAddNewIncomingCall_CorrectPhoneAccountHandle() throws Exception {
        if (!mShouldTestTelecom) {
            return;
        }
        setupConnectionService(null, FLAG_REGISTER | FLAG_ENABLE);
        addAndVerifyNewIncomingCall(createTestNumber(), null);
        final Connection connection3 = verifyConnectionForIncomingCall();
        Collection<Connection> connections = CtsConnectionService.getAllConnectionsFromTelecom();
        assertEquals(1, connections.size());
        assertTrue(connections.contains(connection3));
    }

    /**
     * Tests to be sure that new incoming calls can only be added using a valid PhoneAccountHandle
     * (b/26864502). If a PhoneAccount has not been registered for the PhoneAccountHandle, then
     * a SecurityException will be thrown.
     */
    public void testAddNewIncomingCall_IncorrectPhoneAccountHandle() {
        if (!mShouldTestTelecom) {
            return;
        }

        Bundle extras = new Bundle();
        extras.putParcelable(TelecomManager.EXTRA_INCOMING_CALL_ADDRESS, createTestNumber());
        try {
            mTelecomManager.addNewIncomingCall(TEST_INVALID_HANDLE, extras);
            fail();
        } catch (SecurityException e) {
            // This should create a security exception!
        }

        assertFalse(CtsConnectionService.isServiceRegisteredToTelecom());
    }

    /**
     * Tests to be sure that new incoming calls can only be added if a PhoneAccount is enabled
     * (b/26864502). If a PhoneAccount is not enabled for the PhoneAccountHandle, then
     * a SecurityException will be thrown.
     */
    public void testAddNewIncomingCall_PhoneAccountNotEnabled() throws Exception {
        if (!mShouldTestTelecom) {
            return;
        }

        // Do not enable PhoneAccount
        setupConnectionService(null, FLAG_REGISTER);
        assertFalse(mTelecomManager.getPhoneAccount(TestUtils.TEST_PHONE_ACCOUNT_HANDLE)
                .isEnabled());
        try {
            addAndVerifyNewIncomingCall(createTestNumber(), null);
            fail();
        } catch (SecurityException e) {
            // This should create a security exception!
        }

        assertFalse(CtsConnectionService.isServiceRegisteredToTelecom());
    }
}
