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

package com.android.cts.numberblocking.hostside;

import android.content.ComponentName;
import android.net.Uri;
import android.os.Bundle;
import android.os.Process;
import android.provider.CallLog;
import android.telecom.Connection;
import android.telecom.ConnectionRequest;
import android.telecom.ConnectionService;
import android.telecom.DisconnectCause;
import android.telecom.PhoneAccount;
import android.telecom.PhoneAccountHandle;
import android.telecom.TelecomManager;

import java.util.Arrays;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/**
 * Tests call blocking in a multi-user environment.
 */
public class CallBlockingTest extends BaseNumberBlockingClientTest {
    private static final String QUERY_CALL_THROUGH_OUR_CONNECTION_SERVICE = CallLog.Calls.NUMBER
            + " = ? AND " + CallLog.Calls.PHONE_ACCOUNT_COMPONENT_NAME + " = ?";

    private static CountDownLatch callRejectionCountDownLatch;

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        callRejectionCountDownLatch = new CountDownLatch(1);
    }

    public void testRegisterPhoneAccount() {
        PhoneAccountHandle phoneAccountHandle = getPhoneAccountHandle();
        final PhoneAccount phoneAccount = PhoneAccount.builder(
                phoneAccountHandle, phoneAccountHandle.getId())
                .setAddress(Uri.parse("tel:333-TEST"))
                .setCapabilities(PhoneAccount.CAPABILITY_CALL_PROVIDER)
                .setShortDescription("a short description for the call provider")
                .setSupportedUriSchemes(Arrays.asList(PhoneAccount.SCHEME_TEL))
                .build();
        mTelecomManager.registerPhoneAccount(phoneAccount);
    }

    public void testUnregisterPhoneAccount() {
        mTelecomManager.unregisterPhoneAccount(getPhoneAccountHandle());
        assertNull(mTelecomManager.getPhoneAccount(getPhoneAccountHandle()));
    }

    public void testIncomingCallFromBlockedNumberIsRejected() throws Exception {
        // Make sure no lingering values from previous runs.
        cleanupCall(false /* verifyNoCallLogsWritten */);

        // Register the phone account.
        // final PhoneAccountHandle phoneAccountHandle = registerPhoneAccount().getAccountHandle();
        PhoneAccount phoneAccount = mTelecomManager.getPhoneAccount(getPhoneAccountHandle());
        assertNotNull(phoneAccount);
        try {
            // Add a incoming call.
            final Bundle bundle = new Bundle();
            final Uri phoneUri = Uri.fromParts(PhoneAccount.SCHEME_TEL, mBlockedPhoneNumber, null);
            bundle.putParcelable(TelecomManager.EXTRA_INCOMING_CALL_ADDRESS, phoneUri);
            mTelecomManager.addNewIncomingCall(phoneAccount.getAccountHandle(), bundle);

            // Make sure the call is rejected.
            assertTrue(callRejectionCountDownLatch.await(10, TimeUnit.SECONDS));
        } finally {
            cleanupCall(true /* verifyNoCallLogsWritten */ );
            unregisterPhoneAccount();
        }
    }

    private void cleanupCall(boolean verifyNoCallLogsWritten) {
        final String connectionServiceComponentName = new ComponentName(mContext,
                DummyConnectionService.class).flattenToString();
        int numRowDeleted = mContext.getContentResolver()
                .delete(CallLog.Calls.CONTENT_URI, QUERY_CALL_THROUGH_OUR_CONNECTION_SERVICE,
                        new String[]{mBlockedPhoneNumber, connectionServiceComponentName});
        if (verifyNoCallLogsWritten) {
            assertEquals(0, numRowDeleted);
        }
    }

    private void unregisterPhoneAccount() {
        mTelecomManager.unregisterPhoneAccount(getPhoneAccountHandle());
        assertNull(mTelecomManager.getPhoneAccount(getPhoneAccountHandle()));
    }

    private PhoneAccountHandle getPhoneAccountHandle() {
        return new PhoneAccountHandle(
                new ComponentName(
                        CallBlockingTest.class.getPackage().getName(),
                        DummyConnectionService.class.getName()),
                mPhoneAccountId,
                Process.myUserHandle());
    }

    public static class DummyConnection extends Connection {
        @Override
        public void onReject() {
            super.onReject();
            setDisconnected(new DisconnectCause(DisconnectCause.REJECTED));
            destroy();
            callRejectionCountDownLatch.countDown();
        }
    }

    public static class DummyConnectionService extends ConnectionService {
        @Override
        public Connection onCreateIncomingConnection(
                PhoneAccountHandle connectionManagerAccount, ConnectionRequest request) {
            final Connection connection = new DummyConnection();
            connection.setVideoState(request.getVideoState());
            final Uri address =
                    request.getExtras().getParcelable(TelecomManager.EXTRA_INCOMING_CALL_ADDRESS);
            connection.setAddress(address, TelecomManager.PRESENTATION_ALLOWED);
            return connection;
        }
    }
}
