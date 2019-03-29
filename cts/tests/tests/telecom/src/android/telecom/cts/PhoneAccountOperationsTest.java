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

import static android.telecom.cts.TestUtils.*;

import android.content.ComponentName;
import android.content.Context;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.telecom.PhoneAccount;
import android.telecom.PhoneAccountHandle;
import android.telecom.TelecomManager;
import android.test.InstrumentationTestCase;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.List;


/**
 * Verifies some of the PhoneAccount registration related operations.
 */
public class PhoneAccountOperationsTest extends InstrumentationTestCase {
    public static final PhoneAccountHandle TEST_PHONE_ACCOUNT_HANDLE =
            new PhoneAccountHandle(new ComponentName(PACKAGE, COMPONENT), ACCOUNT_ID);
    public static final Bundle TEST_BUNDLE = createTestBundle();
    public static final int TEST_LENGTH = 10;
    public static final String TEST_ENCODING = "enUS";

    private TestUtils.InvokeCounter mPhoneAccountRegisteredLatch;
    private TestUtils.InvokeCounter mPhoneAccountUnRegisteredLatch;

    MockPhoneAccountChangedReceiver.IntentListener mPhoneAccountIntentListener =
            new MockPhoneAccountChangedReceiver.IntentListener() {
                @Override
                public void onPhoneAccountRegistered(PhoneAccountHandle handle) {
                    mPhoneAccountRegisteredLatch.invoke(handle);
                }

                @Override
                public void onPhoneAccountUnregistered(PhoneAccountHandle handle) {
                    mPhoneAccountUnRegisteredLatch.invoke(handle);
                }
            };

    private static Bundle createTestBundle() {
        Bundle testBundle = new Bundle();
        testBundle.putInt(PhoneAccount.EXTRA_CALL_SUBJECT_MAX_LENGTH, TEST_LENGTH);
        testBundle.putString(PhoneAccount.EXTRA_CALL_SUBJECT_CHARACTER_ENCODING, TEST_ENCODING);
        return testBundle;
    }

    public static final PhoneAccount TEST_SIM_PHONE_ACCOUNT = PhoneAccount.builder(
            TEST_PHONE_ACCOUNT_HANDLE, ACCOUNT_LABEL)
            .setAddress(Uri.parse("tel:555-TEST"))
            .setSubscriptionAddress(Uri.parse("tel:555-TEST"))
            .setCapabilities(PhoneAccount.CAPABILITY_SIM_SUBSCRIPTION)
            .setHighlightColor(Color.RED)
            .setShortDescription(ACCOUNT_LABEL)
            .setSupportedUriSchemes(Arrays.asList(PhoneAccount.SCHEME_TEL))
            .build();

    public static final PhoneAccount TEST_NO_SIM_PHONE_ACCOUNT = PhoneAccount.builder(
            TEST_PHONE_ACCOUNT_HANDLE, ACCOUNT_LABEL)
            .setAddress(Uri.parse("tel:555-TEST"))
            .setSubscriptionAddress(Uri.parse("tel:555-TEST"))
            .setCapabilities(PhoneAccount.CAPABILITY_CALL_PROVIDER |
                    PhoneAccount.CAPABILITY_VIDEO_CALLING)
            .setHighlightColor(Color.RED)
            .setShortDescription(ACCOUNT_LABEL)
            .setSupportedUriSchemes(Arrays.asList(
                    PhoneAccount.SCHEME_TEL, PhoneAccount.SCHEME_VOICEMAIL))
            .setExtras(TEST_BUNDLE)
            .build();

    public static final PhoneAccount TEST_CALL_MANAGER_PHONE_ACCOUNT = PhoneAccount.builder(
            TEST_PHONE_ACCOUNT_HANDLE, ACCOUNT_LABEL)
            .setAddress(Uri.parse("tel:555-TEST"))
            .setSubscriptionAddress(Uri.parse("tel:555-TEST"))
            .setCapabilities(PhoneAccount.CAPABILITY_CALL_PROVIDER |
                    PhoneAccount.CAPABILITY_CONNECTION_MANAGER)
            .setHighlightColor(Color.RED)
            .setShortDescription(ACCOUNT_LABEL)
            .setSupportedUriSchemes(Arrays.asList(
                    PhoneAccount.SCHEME_TEL, PhoneAccount.SCHEME_VOICEMAIL))
            .build();

    private Context mContext;
    private TelecomManager mTelecomManager;

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        mContext = getInstrumentation().getContext();
        if (!TestUtils.shouldTestTelecom(mContext)) {
            return;
        }
        mTelecomManager = (TelecomManager) mContext.getSystemService(Context.TELECOM_SERVICE);
        mPhoneAccountRegisteredLatch = new TestUtils.InvokeCounter("registerPhoneAcct");
        mPhoneAccountUnRegisteredLatch = new TestUtils.InvokeCounter("unRegisterPhoneAcct");
    }

    @Override
    protected void tearDown() throws Exception {
        if (!TestUtils.shouldTestTelecom(mContext)) {
            return;
        }
        mTelecomManager.unregisterPhoneAccount(TEST_PHONE_ACCOUNT_HANDLE);
        PhoneAccount retrievedPhoneAccount = mTelecomManager.getPhoneAccount(
                TEST_PHONE_ACCOUNT_HANDLE);
        assertNull("Test account not deregistered.", retrievedPhoneAccount);
        super.tearDown();
    }

    public void testRegisterPhoneAccount_correctlyThrowsSecurityException() throws Exception {
        if (!shouldTestTelecom(mContext)) {
            return;
        }
        try {
            mTelecomManager.registerPhoneAccount(TEST_SIM_PHONE_ACCOUNT);
            fail("TelecomManager.registerPhoneAccount should throw SecurityException if "
                    + "not a system app.");
        } catch (SecurityException e) {
            assertTrue("Unexpected security exception.", (e.getMessage().indexOf(
                    "android.permission.REGISTER_SIM_SUBSCRIPTION") >= 0));
        }
    }

    public void testRegisterPhoneAccount_NotEnabledAutomatically() throws Exception {
        if (!shouldTestTelecom(mContext)) {
            return;
        }
        mTelecomManager.registerPhoneAccount(TEST_NO_SIM_PHONE_ACCOUNT);
        PhoneAccount retrievedPhoneAccount = mTelecomManager.getPhoneAccount(
                TEST_PHONE_ACCOUNT_HANDLE);
        assertNotNull("Failed to retrieve test account.", retrievedPhoneAccount);
        assertFalse("Phone account should not be automatically enabled.",
                retrievedPhoneAccount.isEnabled());
    }

    public void testRegisterPhoneAccount_DisallowEnable() throws Exception {
        if (!shouldTestTelecom(mContext)) {
            return;
        }
        Method setIsEnabled = null;
        PhoneAccount.Builder phoneAccountBuilder = PhoneAccount.builder(
                TEST_PHONE_ACCOUNT_HANDLE, ACCOUNT_LABEL)
                .setAddress(Uri.parse("tel:555-TEST"))
                .setSubscriptionAddress(Uri.parse("tel:555-TEST"))
                .setCapabilities(PhoneAccount.CAPABILITY_CALL_PROVIDER)
                .setHighlightColor(Color.RED)
                .setShortDescription(ACCOUNT_LABEL)
                .setSupportedUriSchemes(Arrays.asList("tel"));
        try {
            setIsEnabled = PhoneAccount.Builder.class.getDeclaredMethod(
                    "setIsEnabled", boolean.class);
        } catch (NoSuchMethodException e) {
            fail("Failed to find setIsEnabled method.");
        }
        setIsEnabled.invoke(phoneAccountBuilder, true);
        final PhoneAccount phoneAccount  = phoneAccountBuilder.build();
        mTelecomManager.registerPhoneAccount(phoneAccount);
        PhoneAccount retrievedPhoneAccount = mTelecomManager.getPhoneAccount(
                TEST_PHONE_ACCOUNT_HANDLE);
        assertNotNull("Failed to retrieve test account.", retrievedPhoneAccount);
        assertFalse("3rd party app cannot enable its own phone account.",
                retrievedPhoneAccount.isEnabled());
    }

    public void testRegisterPhoneAccount_ListEnabledAccounts() throws Exception {
        if (!shouldTestTelecom(mContext)) {
            return;
        }
        mTelecomManager.registerPhoneAccount(TEST_NO_SIM_PHONE_ACCOUNT);
        final List<PhoneAccountHandle> oldAccounts = mTelecomManager.getCallCapablePhoneAccounts();
        final int oldAccountsListSize = oldAccounts.size();
        if (oldAccountsListSize > 0) {
            assertFalse("Enabled Phone accounts should not contain the test account.",
                    oldAccounts.contains(TEST_PHONE_ACCOUNT_HANDLE));
        }
        TestUtils.enablePhoneAccount(getInstrumentation(), TEST_PHONE_ACCOUNT_HANDLE);
        final List<PhoneAccountHandle> newAccounts = mTelecomManager.getCallCapablePhoneAccounts();
        assertNotNull("No enabled Phone account found.", newAccounts);
        assertEquals("1 new enabled Phone account expected.", newAccounts.size(),
                oldAccountsListSize + 1);
        assertTrue("Enabled Phone accounts do not contain the test account.",
                newAccounts.contains(TEST_PHONE_ACCOUNT_HANDLE));
    }

    public void testRegisterPhoneAccount_CheckCapabilities() throws Exception {
        if (!shouldTestTelecom(mContext)) {
            return;
        }
        mTelecomManager.registerPhoneAccount(TEST_NO_SIM_PHONE_ACCOUNT);
        PhoneAccount retrievedPhoneAccount = mTelecomManager.getPhoneAccount(
                TEST_PHONE_ACCOUNT_HANDLE);
        assertTrue("Phone account should have call provider & video calling capability.",
                retrievedPhoneAccount.hasCapabilities(PhoneAccount.CAPABILITY_CALL_PROVIDER |
                        PhoneAccount.CAPABILITY_VIDEO_CALLING));
    }

    public void testRegisterPhoneAccount_CheckExtras() throws Exception {
        if (!shouldTestTelecom(mContext)) {
            return;
        }
        mTelecomManager.registerPhoneAccount(TEST_NO_SIM_PHONE_ACCOUNT);
        PhoneAccount retrievedPhoneAccount = mTelecomManager.getPhoneAccount(
                TEST_PHONE_ACCOUNT_HANDLE);
        Bundle extras = retrievedPhoneAccount.getExtras();
        assertTrue(extras.containsKey(PhoneAccount.EXTRA_CALL_SUBJECT_CHARACTER_ENCODING));
        assertEquals(TEST_ENCODING,
                extras.getString(PhoneAccount.EXTRA_CALL_SUBJECT_CHARACTER_ENCODING));
        assertTrue(extras.containsKey(PhoneAccount.EXTRA_CALL_SUBJECT_MAX_LENGTH));
        assertEquals(TEST_LENGTH,
                extras.getInt(PhoneAccount.EXTRA_CALL_SUBJECT_MAX_LENGTH));
    }

    public void testRegisterPhoneAccount_CheckURISchemeSupported() throws Exception {
        if (!shouldTestTelecom(mContext)) {
            return;
        }
        mTelecomManager.registerPhoneAccount(TEST_NO_SIM_PHONE_ACCOUNT);
        PhoneAccount retrievedPhoneAccount = mTelecomManager.getPhoneAccount(
                TEST_PHONE_ACCOUNT_HANDLE);
        assertTrue("Phone account should support tel URI scheme.",
                retrievedPhoneAccount.supportsUriScheme(PhoneAccount.SCHEME_TEL));
        assertTrue("Phone account should support voicemail URI scheme.",
                retrievedPhoneAccount.supportsUriScheme(PhoneAccount.SCHEME_VOICEMAIL));
    }

    /**
     * Verifies that the {@link TelecomManager#ACTION_PHONE_ACCOUNT_REGISTERED} intent is sent to
     * the default dialer when a phone account is registered and,
     * {@link TelecomManager#ACTION_PHONE_ACCOUNT_UNREGISTERED} is sent when a phone account is
     * unregistered.
     * @throws Exception
     */
    public void testRegisterUnregisterPhoneAccountIntent() throws Exception {
        if (!shouldTestTelecom(mContext)) {
            return;
        }

        MockPhoneAccountChangedReceiver.setIntentListener(mPhoneAccountIntentListener);
        String previousDefaultDialer = TestUtils.getDefaultDialer(getInstrumentation());
        try {
            TestUtils.setDefaultDialer(getInstrumentation(), TestUtils.PACKAGE);

            mTelecomManager.registerPhoneAccount(TEST_NO_SIM_PHONE_ACCOUNT);

            mPhoneAccountRegisteredLatch.waitForCount(1);
            PhoneAccountHandle handle =
                    (PhoneAccountHandle) mPhoneAccountRegisteredLatch.getArgs(0)[0];
            assertEquals(TEST_PHONE_ACCOUNT_HANDLE, handle);

            mTelecomManager.unregisterPhoneAccount(TEST_PHONE_ACCOUNT_HANDLE);
            mPhoneAccountUnRegisteredLatch.waitForCount(1);
            PhoneAccountHandle handle2 =
                    (PhoneAccountHandle) mPhoneAccountUnRegisteredLatch.getArgs(0)[0];
            assertEquals(TEST_PHONE_ACCOUNT_HANDLE, handle2);
        } finally {
            TestUtils.setDefaultDialer(getInstrumentation(), previousDefaultDialer);
        }
    }
}
