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

import android.content.ComponentName;
import android.content.ContentValues;
import android.content.Context;
import android.content.pm.PackageManager;
import android.provider.VoicemailContract.Voicemails;
import android.telecom.PhoneAccountHandle;
import android.telecom.TelecomManager;
import android.test.InstrumentationTestCase;
import android.text.TextUtils;

import java.util.List;


/**
 * Verifies that certain privileged operations can only be performed by the default dialer.
 */
public class DefaultDialerOperationsTest extends InstrumentationTestCase {
    private Context mContext;
    private TelecomManager mTelecomManager;
    private PhoneAccountHandle mPhoneAccountHandle;
    private String mPreviousDefaultDialer = null;
    private String mSystemDialer = null;

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        mContext = getInstrumentation().getContext();

        if (!TestUtils.shouldTestTelecom(mContext)) {
            return;
        }
        mPreviousDefaultDialer = TestUtils.getDefaultDialer(getInstrumentation());
        // Reset the current dialer to the system dialer, to ensure that we start each test
        // without being the default dialer.
        mSystemDialer = TestUtils.getSystemDialer(getInstrumentation());
        if (!TextUtils.isEmpty(mSystemDialer)) {
            TestUtils.setDefaultDialer(getInstrumentation(), mSystemDialer);
        }
        mTelecomManager = (TelecomManager) mContext.getSystemService(Context.TELECOM_SERVICE);
        final List<PhoneAccountHandle> accounts = mTelecomManager.getCallCapablePhoneAccounts();
        if (accounts != null && !accounts.isEmpty()) {
            mPhoneAccountHandle = accounts.get(0);
        }
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

    public void testGetDefaultDialerPackage() throws Exception {
        if (!TestUtils.shouldTestTelecom(mContext)) {
            return;
        }
        assertEquals(mSystemDialer, mTelecomManager.getDefaultDialerPackage());
        TestUtils.setDefaultDialer(getInstrumentation(), TestUtils.PACKAGE);
        assertEquals(TestUtils.PACKAGE, mTelecomManager.getDefaultDialerPackage());
    }

    public void testVoicemailReadWritePermissions() throws Exception {
        if (!TestUtils.shouldTestTelecom(mContext)) {
            return;
        }
        try {
            mContext.getContentResolver().query(Voicemails.CONTENT_URI, null, null, null, null);
            fail("Reading voicemails should throw SecurityException if not default Dialer");
        } catch (SecurityException e) {
        }

        try {
            mContext.getContentResolver().delete(Voicemails.CONTENT_URI,
                    Voicemails._ID + "=999 AND 1=2", null);
            fail("Deleting voicemails should throw SecurityException if not default Dialer");
        } catch (SecurityException e) {
        }

        try {
            mContext.getContentResolver().update(
                    Voicemails.CONTENT_URI.buildUpon().appendPath("999").build(),
                    new ContentValues(),
                    null,
                    null);
            fail("Updating voicemails should throw SecurityException if not default Dialer");
        } catch (SecurityException e) {
        }

        TestUtils.setDefaultDialer(getInstrumentation(), TestUtils.PACKAGE);
        // No exception if the calling package is the default dialer.
        mContext.getContentResolver().query(Voicemails.CONTENT_URI, null, null, null, null);
        mContext.getContentResolver().delete(Voicemails.CONTENT_URI,
                Voicemails._ID + "=999 AND 1=2", null);
    }

    public void testSilenceRingerPermissions() throws Exception {
        if (!TestUtils.shouldTestTelecom(mContext)) {
            return;
        }
        try {
            mTelecomManager.silenceRinger();
            fail("TelecomManager.silenceRinger should throw SecurityException if not default "
                    + "dialer");
        } catch (SecurityException e) {
        }

        TestUtils.setDefaultDialer(getInstrumentation(), TestUtils.PACKAGE);
        // No exception if the calling package is the default dialer.
        mTelecomManager.silenceRinger();
    }

    public void testCancelMissedCallsNotificationPermissions()
            throws Exception {
        if (!TestUtils.shouldTestTelecom(mContext)) {
            return;
        }
        try {
            mTelecomManager.cancelMissedCallsNotification();
            fail("TelecomManager.cancelMissedCallsNotification should throw SecurityException if "
                    + "not default dialer");
        } catch (SecurityException e) {
        }

        TestUtils.setDefaultDialer(getInstrumentation(), TestUtils.PACKAGE);
        // No exception if the calling package is the default dialer.
        mTelecomManager.cancelMissedCallsNotification();
    }

    public void testHandlePinMmPermissions()
            throws Exception {
        if (!TestUtils.shouldTestTelecom(mContext)) {
            return;
        }
        try {
            mTelecomManager.handleMmi("0");
            fail("TelecomManager.handleMmi should throw SecurityException if not default dialer");
        } catch (SecurityException e) {
        }

        try {
            mTelecomManager.handleMmi("0", mPhoneAccountHandle);
            fail("TelecomManager.handleMmi should throw SecurityException if not default dialer");
        } catch (SecurityException e) {
        }

        TestUtils.setDefaultDialer(getInstrumentation(), TestUtils.PACKAGE);
        // No exception if the calling package is the default dialer.
        mTelecomManager.handleMmi("0");
        mTelecomManager.handleMmi("0", mPhoneAccountHandle);
    }

    public void testGetAdnForPhoneAccountPermissions() throws Exception {
        if (!TestUtils.shouldTestTelecom(mContext)) {
            return;
        }
        try {
            mTelecomManager.getAdnUriForPhoneAccount(mPhoneAccountHandle);
            fail("TelecomManager.getAdnUriForPhoneAccount should throw SecurityException if "
                    + "not default dialer");
        } catch (SecurityException e) {
        }

        TestUtils.setDefaultDialer(getInstrumentation(), TestUtils.PACKAGE);
        // No exception if the calling package is the default dialer.
        mTelecomManager.getAdnUriForPhoneAccount(mPhoneAccountHandle);
    }

    public void testSetDefaultDialerNoDialIntent_notSupported() throws Exception {
        if (!TestUtils.shouldTestTelecom(mContext)) {
            return;
        }
        final PackageManager pm = mContext.getPackageManager();
        final ComponentName name = new ComponentName(mContext,
                "android.telecom.cts.MockDialerActivity");
        try {
            pm.setComponentEnabledSetting(name, PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
                    PackageManager.DONT_KILL_APP);

            final String result =
                    TestUtils.setDefaultDialer(getInstrumentation(), TestUtils.PACKAGE);
            assertNotSame(result, TestUtils.PACKAGE);
            assertTrue("Expected failure indicating that this was not an installed dialer app",
                    result.contains("is not an installed Dialer app"));
        } finally {
            pm.setComponentEnabledSetting(name, PackageManager.COMPONENT_ENABLED_STATE_ENABLED,
                    PackageManager.DONT_KILL_APP);
        }

        // Now that the activity is present again in the package manager, this should succeed.
        final String result = TestUtils.setDefaultDialer(getInstrumentation(), TestUtils.PACKAGE);
        assertTrue("Expected success message indicating that " + TestUtils.PACKAGE + " was set as "
                + "default dialer.", result.contains("set as default dialer"));
        assertEquals(TestUtils.PACKAGE, TestUtils.getDefaultDialer(getInstrumentation()));
    }
}
