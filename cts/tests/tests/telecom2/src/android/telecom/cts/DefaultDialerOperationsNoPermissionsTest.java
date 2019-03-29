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

import android.content.Context;
import android.telecom.TelecomManager;
import android.test.InstrumentationTestCase;
import android.text.TextUtils;

/**
 * Verifies that certain privileged operations can only be performed by the default dialer.
 */
public class DefaultDialerOperationsNoPermissionsTest extends InstrumentationTestCase {
    private Context mContext;
    private TelecomManager mTelecomManager;
    private String mPreviousDefaultDialer = null;
    private String mSystemDialer = null;

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        mContext = getInstrumentation().getContext();
        if (!TestUtils.shouldTestTelecom(mContext)) {
            return;
        }
        TestUtils.PACKAGE = mContext.getPackageName();
        mPreviousDefaultDialer = TestUtils.getDefaultDialer(getInstrumentation());
        // Reset the current dialer to the system dialer, to ensure that we start each test
        // without being the default dialer.
        mSystemDialer = TestUtils.getSystemDialer(getInstrumentation());
        if (!TextUtils.isEmpty(mSystemDialer)) {
            TestUtils.setDefaultDialer(getInstrumentation(), mSystemDialer);
        }
        mTelecomManager = (TelecomManager) mContext.getSystemService(Context.TELECOM_SERVICE);
    }

    @Override
    protected void tearDown() throws Exception {
        if (!TextUtils.isEmpty(mPreviousDefaultDialer)) {
            // Restore the default dialer to whatever the default dialer was before the tests
            // were started. This may or may not be the system dialer.
            TestUtils.setDefaultDialer(getInstrumentation(), mPreviousDefaultDialer);
        }
        super.tearDown();
    }

    public void testShowInCallScreenPermissions() throws Exception {
        if (!TestUtils.shouldTestTelecom(mContext)) {
            return;
        }
        verifyForReadPhoneStateOrDefaultDialer(new Runnable() {
            @Override
            public void run() {
                mTelecomManager.showInCallScreen(false);
            }
        }, "showInCallScreen");
    }

    public void testGetCallCapableAccountsPermissions() throws Exception {
        if (!TestUtils.shouldTestTelecom(mContext)) {
            return;
        }
        verifyForReadPhoneStateOrDefaultDialer(new Runnable() {
            @Override
            public void run() {
                mTelecomManager.getCallCapablePhoneAccounts();
            }
        }, "getCallCapableAccounts");
    }

    public void testGetDefaultOutgoingPhoneAccount() throws Exception {
        if (!TestUtils.shouldTestTelecom(mContext)) {
            return;
        }
        verifyForReadPhoneStateOrDefaultDialer(new Runnable() {
            @Override
            public void run() {
                mTelecomManager.getDefaultOutgoingPhoneAccount("tel");
            }
        }, "getDefaultOutgoingPhoneAccount");
    }

    public void testGetLine1Number() throws Exception {
        if (!TestUtils.shouldTestTelecom(mContext)) {
            return;
        }
        verifyForReadPhoneStateOrDefaultDialer(new Runnable() {
            @Override
            public void run() {
                mTelecomManager.getLine1Number(null);
            }
        }, "getLine1Number");
    }

    public void testGetVoicemailNumber() throws Exception {
        if (!TestUtils.shouldTestTelecom(mContext)) {
            return;
        }
        verifyForReadPhoneStateOrDefaultDialer(new Runnable() {
            @Override
            public void run() {
                mTelecomManager.getVoiceMailNumber(null);
            }
        }, "getVoiceMailNumber");
    }

    public void testIsVoicemailNumber() throws Exception {
        if (!TestUtils.shouldTestTelecom(mContext)) {
            return;
        }
        verifyForReadPhoneStateOrDefaultDialer(new Runnable() {
            @Override
            public void run() {
                mTelecomManager.isVoiceMailNumber(null, null);
            }
        }, "isVoiceMailNumber");
    }

    public void testIsInCall() throws Exception {
        if (!TestUtils.shouldTestTelecom(mContext)) {
            return;
        }
        verifyForReadPhoneStateOrDefaultDialer(new Runnable() {
            @Override
            public void run() {
                mTelecomManager.isInCall();
            }
        }, "isInCall");
    }

    private void verifyForReadPhoneStateOrDefaultDialer(Runnable runnable, String methodName)
            throws Exception{
        try {
            runnable.run();
            fail("TelecomManager." + methodName + " should throw SecurityException if no "
                    + "READ_PHONE_STATE permission");
        } catch (SecurityException e) {
        }

        TestUtils.setDefaultDialer(getInstrumentation(), TestUtils.PACKAGE);
        runnable.run();
    }
}
