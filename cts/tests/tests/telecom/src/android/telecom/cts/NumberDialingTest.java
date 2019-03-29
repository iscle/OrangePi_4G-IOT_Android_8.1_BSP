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

import static android.telecom.cts.TestUtils.shouldTestTelecom;

import android.net.Uri;
import android.telecom.Connection;
import android.telecom.ConnectionRequest;
import android.telecom.PhoneAccount;
import android.telecom.PhoneAccountHandle;

/**
 * Tests that certain numbers make their way through to the connection service.
 */
public class NumberDialingTest extends BaseTelecomTestWithMockServices {

    /**
     * Amount of time to wait for an asynchronous method invocation to ConnectionService.
     */
    private static final int CS_WAIT_MILLIS = 2000;

    public void testEndInPound() throws Exception {
        if (!mShouldTestTelecom) {
            return;
        }

        final Object[] res = new Object[1];
        Uri address = Uri.fromParts("tel", "*1234#", null);

        PhoneAccount account = setupConnectionService(
                new MockConnectionService() {
                    @Override
                    public Connection onCreateOutgoingConnection(
                            PhoneAccountHandle connectionManagerPhoneAccount,
                            ConnectionRequest request) {
                        res[0] = request.getAddress();
                        synchronized(res) {
                            res.notify();
                        }
                        return null;  // do not actually place the call.
                    }

                }, FLAG_REGISTER | FLAG_ENABLE);

        startCallTo(address, account.getAccountHandle());
        synchronized(res) {
            res.wait(CS_WAIT_MILLIS);
        }
        assertEquals(address, res[0]);
    }
}
