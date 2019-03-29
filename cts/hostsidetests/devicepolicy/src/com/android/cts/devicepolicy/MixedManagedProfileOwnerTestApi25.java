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

package com.android.cts.devicepolicy;

/**
 * Set of tests for managed profile owner use cases that also apply to device owners.
 * Tests that should be run identically in both cases are added in DeviceAndProfileOwnerTestApi25.
 */
public class MixedManagedProfileOwnerTestApi25 extends DeviceAndProfileOwnerTestApi25 {

    private int mParentUserId = -1;

    @Override
    protected void setUp() throws Exception {
        super.setUp();

        // We need managed users to be supported in order to create a profile of the user owner.
        mHasFeature &= hasDeviceFeature("android.software.managed_users");

        if (mHasFeature) {
            removeTestUsers();
            mParentUserId = mPrimaryUserId;
            createManagedProfile();
        }
    }

    private void createManagedProfile() throws Exception {
        mUserId = createManagedProfile(mParentUserId);
        switchUser(mParentUserId);
        startUser(mUserId);

        installAppAsUser(DEVICE_ADMIN_APK, mUserId);
        setProfileOwnerOrFail(DEVICE_ADMIN_PKG + "/" + ADMIN_RECEIVER_TEST_CLASS, mUserId);
        startUser(mUserId);
    }

    @Override
    protected void tearDown() throws Exception {
        if (mHasFeature) {
            removeUser(mUserId);
        }
        super.tearDown();
    }

    /**
     * Verify the Profile Owner of a managed profile can create and change the password,
     * but cannot remove it.
     */
    @Override
    public void testResetPassword() throws Exception {
        if (!mHasFeature) {
            return;
        }

        executeDeviceTestMethod(RESET_PASSWORD_TEST_CLASS, "testResetPasswordManagedProfile");
    }

    /**
     *  Verify the Profile Owner of a managed profile can only change the password when FBE is
     *  unlocked, and cannot remove the password even when FBE is unlocked.
     */
    @Override
    public void testResetPasswordFbe() throws Exception {
        if (!mHasFeature || !mSupportsFbe) {
            return;
        }

        // Lock FBE and verify resetPassword is disabled
        executeDeviceTestMethod(FBE_HELPER_CLASS, "testSetPassword");
        rebootAndWaitUntilReady();
        executeDeviceTestMethod(RESET_PASSWORD_TEST_CLASS, "testResetPasswordDisabled");

        // Start an activity in managed profile to trigger work challenge
        startSimpleActivityAsUser(mUserId);

        // Unlock FBE and verify resetPassword is enabled again
        executeDeviceTestMethod(FBE_HELPER_CLASS, "testUnlockFbe");
        executeDeviceTestMethod(RESET_PASSWORD_TEST_CLASS, "testResetPasswordManagedProfile");
    }
}
