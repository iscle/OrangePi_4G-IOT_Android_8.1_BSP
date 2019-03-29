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
 * limitations under the License.
 */
package com.android.cts.deviceandprofileowner;

import android.app.KeyguardManager;
import android.app.admin.DevicePolicyManager;
import android.support.test.InstrumentationRegistry;

public class ResetPasswordWithTokenTest extends BaseDeviceAdminTest {

    private static final String SHORT_PASSWORD = "1234";
    private static final String COMPLEX_PASSWORD = "abc123.";

    private static final byte[] TOKEN0 = "abcdefghijklmnopqrstuvwxyz0123456789".getBytes();
    private static final byte[] TOKEN1 = "abcdefghijklmnopqrstuvwxyz012345678*".getBytes();

    private static final String ARG_ALLOW_FAILURE = "allowFailure";

    private boolean mShouldRun;

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        Boolean allowFailure = Boolean.parseBoolean(InstrumentationRegistry.getArguments()
                .getString(ARG_ALLOW_FAILURE));
        mShouldRun = setUpResetPasswordToken(allowFailure);
    }

    @Override
    protected void tearDown() throws Exception {
        if (mShouldRun) {
            cleanUpResetPasswordToken();
        }
        super.tearDown();
    }

    public void testBadTokenShouldFail() {
        if (!mShouldRun) {
            return;
        }
        // resetting password with wrong token should fail
        assertFalse(mDevicePolicyManager.resetPasswordWithToken(ADMIN_RECEIVER_COMPONENT,
                SHORT_PASSWORD, TOKEN1, 0));
    }

    public void testChangePasswordWithToken() {
        if (!mShouldRun) {
            return;
        }
        // try changing password with token
        assertTrue(mDevicePolicyManager.resetPasswordWithToken(ADMIN_RECEIVER_COMPONENT,
                SHORT_PASSWORD, TOKEN0, 0));

        // Set a strong password constraint and expect the sufficiency check to fail
        mDevicePolicyManager.setPasswordQuality(ADMIN_RECEIVER_COMPONENT,
                DevicePolicyManager.PASSWORD_QUALITY_NUMERIC);
        mDevicePolicyManager.setPasswordMinimumLength(ADMIN_RECEIVER_COMPONENT, 6);
        assertPasswordSufficiency(false);

        // try changing to a stronger password and verify it satisfies requested constraint
        assertTrue(mDevicePolicyManager.resetPasswordWithToken(ADMIN_RECEIVER_COMPONENT,
                COMPLEX_PASSWORD, TOKEN0, 0));
        assertPasswordSufficiency(true);
    }

    public void testResetPasswordFailIfQualityNotMet() {
        if (!mShouldRun) {
            return;
        }
        mDevicePolicyManager.setPasswordQuality(ADMIN_RECEIVER_COMPONENT,
                DevicePolicyManager.PASSWORD_QUALITY_NUMERIC);
        mDevicePolicyManager.setPasswordMinimumLength(ADMIN_RECEIVER_COMPONENT, 6);

        assertFalse(mDevicePolicyManager.resetPasswordWithToken(ADMIN_RECEIVER_COMPONENT,
                SHORT_PASSWORD, TOKEN0, 0));

        assertTrue(mDevicePolicyManager.resetPasswordWithToken(ADMIN_RECEIVER_COMPONENT,
                COMPLEX_PASSWORD, TOKEN0, 0));
    }

    public void testPasswordMetricAfterResetPassword() {
        if (!mShouldRun) {
            return;
        }
        mDevicePolicyManager.setPasswordQuality(ADMIN_RECEIVER_COMPONENT,
                DevicePolicyManager.PASSWORD_QUALITY_COMPLEX);
        mDevicePolicyManager.setPasswordMinimumNumeric(ADMIN_RECEIVER_COMPONENT, 1);
        mDevicePolicyManager.setPasswordMinimumLetters(ADMIN_RECEIVER_COMPONENT, 1);
        mDevicePolicyManager.setPasswordMinimumSymbols(ADMIN_RECEIVER_COMPONENT, 0);
        assertTrue(mDevicePolicyManager.resetPasswordWithToken(ADMIN_RECEIVER_COMPONENT,
                COMPLEX_PASSWORD, TOKEN0, 0));

        // Change required complexity and verify new password satisfies it
        // First set a slightly stronger requirement and expect password sufficiency is false
        mDevicePolicyManager.setPasswordMinimumNumeric(ADMIN_RECEIVER_COMPONENT, 3);
        mDevicePolicyManager.setPasswordMinimumLetters(ADMIN_RECEIVER_COMPONENT, 3);
        mDevicePolicyManager.setPasswordMinimumSymbols(ADMIN_RECEIVER_COMPONENT, 2);
        assertPasswordSufficiency(false);
        // Then sets the appropriate quality and verify it should pass
        mDevicePolicyManager.setPasswordMinimumSymbols(ADMIN_RECEIVER_COMPONENT, 1);
        assertPasswordSufficiency(true);
    }

    public void testClearPasswordWithToken() {
        if (!mShouldRun) {
            return;
        }
        KeyguardManager km = mContext.getSystemService(KeyguardManager.class);
        // First set a password
        assertTrue(mDevicePolicyManager.resetPasswordWithToken(ADMIN_RECEIVER_COMPONENT,
                SHORT_PASSWORD, TOKEN0, 0));
        assertTrue(km.isDeviceSecure());

        // clear password with token
        assertTrue(mDevicePolicyManager.resetPasswordWithToken(ADMIN_RECEIVER_COMPONENT, null,
                TOKEN0, 0));
        assertFalse(km.isDeviceSecure());
    }

    private boolean setUpResetPasswordToken(boolean acceptFailure) {
        // set up a token
        assertFalse(mDevicePolicyManager.isResetPasswordTokenActive(ADMIN_RECEIVER_COMPONENT));

        try {
            // On devices with password token disabled, calling this method will throw
            // a security exception. If that's anticipated, then return early without failing.
            assertTrue(mDevicePolicyManager.setResetPasswordToken(ADMIN_RECEIVER_COMPONENT,
                    TOKEN0));
        } catch (SecurityException e) {
            if (acceptFailure &&
                    e.getMessage().equals("Escrow token is disabled on the current user")) {
                return false;
            } else {
                throw e;
            }
        }
        assertTrue(mDevicePolicyManager.isResetPasswordTokenActive(ADMIN_RECEIVER_COMPONENT));
        return true;
    }

    private void cleanUpResetPasswordToken() {
        // First remove device lock
        mDevicePolicyManager.setPasswordQuality(ADMIN_RECEIVER_COMPONENT,
                DevicePolicyManager.PASSWORD_QUALITY_UNSPECIFIED);
        mDevicePolicyManager.setPasswordMinimumLength(ADMIN_RECEIVER_COMPONENT, 0);
        assertTrue(mDevicePolicyManager.resetPasswordWithToken(ADMIN_RECEIVER_COMPONENT, null,
                TOKEN0, 0));

        // Then remove token and check it succeeds
        assertTrue(mDevicePolicyManager.clearResetPasswordToken(ADMIN_RECEIVER_COMPONENT));
        assertFalse(mDevicePolicyManager.isResetPasswordTokenActive(ADMIN_RECEIVER_COMPONENT));
        assertFalse(mDevicePolicyManager.resetPasswordWithToken(ADMIN_RECEIVER_COMPONENT,
                SHORT_PASSWORD, TOKEN0, 0));
    }
}