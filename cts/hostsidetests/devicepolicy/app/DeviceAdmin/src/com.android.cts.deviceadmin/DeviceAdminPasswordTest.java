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
 * limitations under the License.
 */
package com.android.cts.deviceadmin;

import android.app.admin.DevicePolicyManager;
import android.test.MoreAsserts;

/**
 * Tests that:
 * - need to be run as device admin (as opposed to device owner) and
 * - require resetting the password at the end.
 *
 * Note: when adding a new method, make sure to add a corresponding method in
 * BaseDeviceAdminHostSideTest.
 */
public class DeviceAdminPasswordTest extends BaseDeviceAdminTest {

    @Override
    protected void setUp() throws Exception {
        super.setUp();

        assertNotDeviceOwner();
    }

    private void checkSetPassword_nycRestrictions_success() {
        assertTrue(dpm.resetPassword("1234", /* flags= */ 0));
    }

    private void checkSetPassword_nycRestrictions_failure() {
        try {
            assertFalse(dpm.resetPassword("1234", /* flags= */ 0));
            if (shouldResetPasswordThrow()) {
                fail("Didn't throw");
            }
        } catch (SecurityException e) {
            if (!shouldResetPasswordThrow()) {
                fail("Shouldn't throw");
            }
            MoreAsserts.assertContainsRegex("Admin cannot change current password", e.getMessage());
        }
    }

    private void checkClearPassword_nycRestrictions_failure() {
        try {
            assertFalse(dpm.resetPassword("", /* flags= */ 0));
            if (shouldResetPasswordThrow()) {
                fail("Didn't throw");
            }
        } catch (SecurityException e) {
            if (!shouldResetPasswordThrow()) {
                fail("Shouldn't throw");
            }
            MoreAsserts.assertContainsRegex("Cannot call with null password", e.getMessage());
        }
    }

    private void assertHasPassword() {
        final int currentQuality = dpm.getPasswordQuality(mAdminComponent);
        dpm.setPasswordQuality(mAdminComponent, DevicePolicyManager.PASSWORD_QUALITY_SOMETHING);
        try {
            assertTrue("No password set", dpm.isActivePasswordSufficient());
        } finally {
            dpm.setPasswordQuality(mAdminComponent, currentQuality);
        }
    }

    private void assertNoPassword() {
        final int currentQuality = dpm.getPasswordQuality(mAdminComponent);
        dpm.setPasswordQuality(mAdminComponent, DevicePolicyManager.PASSWORD_QUALITY_SOMETHING);
        try {
            assertFalse("Password is set", dpm.isActivePasswordSufficient());
        } finally {
            dpm.setPasswordQuality(mAdminComponent, currentQuality);
        }
    }

    /**
     * Tests for the new restrictions on {@link DevicePolicyManager#resetPassword} introduced
     * on NYC.
     */
    public void testResetPassword_nycRestrictions() throws Exception {

        assertNoPassword();

        // Can't clear the password, even if there's no password set currently.
        checkClearPassword_nycRestrictions_failure();

        assertNoPassword();

        // No password -> setting one is okay.
        checkSetPassword_nycRestrictions_success();

        assertHasPassword();

        // But once set, DA can't change the password.
        checkSetPassword_nycRestrictions_failure();

        assertHasPassword();

        // Still can't clear the password.
        checkClearPassword_nycRestrictions_failure();

        assertHasPassword();
    }
}
