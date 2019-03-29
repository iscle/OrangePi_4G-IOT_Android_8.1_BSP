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
package com.android.cts.deviceandprofileowner;

import android.util.Log;

import java.lang.IllegalStateException;

/**
 * Test cases for {@link android.app.admin.DevicePolicyManager#resetPassword(String, int)}.
 *
 * As of O, resetPassword is only accessible to DPCs targeting Sdk level before O, so this
 * is exercised by CtsDeviceAndProfileOwnerApp25 only.
 *
 * <p>These tests verify that the device password:
 * <ul>
 *     <li>can be created, changed and cleared when FBE is not locked, and
 *     <li>cannot be changed or cleared when FBE is locked.
 * </ul>
 */
public class ResetPasswordTest extends BaseDeviceAdminTest {

    private static final String TAG = "ResetPasswordTest";

    private static final String PASSWORD_1 = "12345";
    private static final String PASSWORD_2 = "12345abcdef!!##1";

    /**
     * Test: a Device Owner or (un-managed) Profile Owner can create, change and remove a password.
     */
    public void testResetPassword() {
        testResetPasswordEnabled(true, true);
    }

    /**
     * Test: a managed Profile Owner can create and change, but not remove, a password.
     */
    public void testResetPasswordManagedProfile() {
        testResetPasswordEnabled(true, false);
    }

    /**
     * Test: a Device Owner or Profile Owner (managed or un-managed) cannot change or remove the
     * password when FBE is locked.
     */
    public void testResetPasswordDisabled() throws Exception {
        assertFalse("Failed to lock FBE", mUserManager.isUserUnlocked());
        testResetPasswordEnabled(false, false);
    }

    private void testResetPasswordEnabled(boolean canChange, boolean canRemove) {
        try {
            assertResetPasswordEnabled(canChange, PASSWORD_1);
            assertResetPasswordEnabled(canChange, PASSWORD_2);
        } finally {
            assertResetPasswordEnabled(canRemove, "");
        }
    }

    private void assertResetPasswordEnabled(boolean enabled, String password) {
        boolean passwordChanged;
        try {
            passwordChanged = mDevicePolicyManager.resetPassword(password, 0);
        } catch (IllegalStateException | SecurityException e) {
            passwordChanged = false;
            if (enabled) {
                Log.d(TAG, e.getMessage(), e);
            }
        }

        if (enabled) {
            assertTrue("Failed to change password", passwordChanged);
        } else {
            assertFalse("Failed to prevent password change", passwordChanged);
        }
    }
}
