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

public class ManagedProfileProvisioningTest extends BaseDevicePolicyTest {
    private static final String MANAGED_PROFILE_PKG = "com.android.cts.managedprofile";
    private static final String MANAGED_PROFILE_APK = "CtsManagedProfileApp.apk";

    private int mProfileUserId;
    private int mParentUserId;

    @Override
    protected void setUp() throws Exception {
        super.setUp();

        // We need multi user to be supported in order to create a profile of the user owner.
        mHasFeature = mHasFeature && hasDeviceFeature(
                "android.software.managed_users");

        if (mHasFeature) {
            removeTestUsers();
            mParentUserId = mPrimaryUserId;
            installAppAsUser(MANAGED_PROFILE_APK, mParentUserId);
            mProfileUserId = 0;
        }
    }

    @Override
    protected void tearDown() throws Exception {
        if (mHasFeature) {
            if (mProfileUserId != 0) {
                removeUser(mProfileUserId);
            }
            // Remove the test app account: also done by uninstallPackage
            getDevice().uninstallPackage(MANAGED_PROFILE_PKG);
        }
        super.tearDown();
    }

    public void testManagedProfileProvisioning() throws Exception {
        if (!mHasFeature) {
            return;
        }

        provisionManagedProfile();

        runDeviceTestsAsUser(MANAGED_PROFILE_PKG, ".ProvisioningTest",
                "testIsManagedProfile", mProfileUserId);
    }

    public void testEXTRA_PROVISIONING_ADMIN_EXTRAS_BUNDLE() throws Exception {
        if (!mHasFeature) {
            return;
        }

        provisionManagedProfile();

        runDeviceTestsAsUser(MANAGED_PROFILE_PKG, ".ProvisioningTest",
                "testVerifyAdminExtraBundle", mProfileUserId);
    }

    public void testVerifySuccessfulIntentWasReceived() throws Exception {
        if (!mHasFeature) {
            return;
        }

        provisionManagedProfile();

        runDeviceTestsAsUser(MANAGED_PROFILE_PKG, ".ProvisioningTest",
                "testVerifySuccessfulIntentWasReceived", mProfileUserId);
    }

    public void testAccountMigration() throws Exception {
        if (!mHasFeature) {
            return;
        }

        provisionManagedProfile();

        runDeviceTestsAsUser(MANAGED_PROFILE_PKG, ".ProvisioningTest",
                "testAccountExist", mProfileUserId);

        runDeviceTestsAsUser(MANAGED_PROFILE_PKG, ".ProvisioningTest",
                "testAccountNotExist", mParentUserId);
    }

    public void testAccountCopy() throws Exception {
        if (!mHasFeature) {
            return;
        }

        provisionManagedProfile_accountCopy();

        runDeviceTestsAsUser(MANAGED_PROFILE_PKG, ".ProvisioningTest",
                "testAccountExist", mProfileUserId);

        runDeviceTestsAsUser(MANAGED_PROFILE_PKG, ".ProvisioningTest",
                "testAccountExist", mParentUserId);
    }

    private void provisionManagedProfile() throws Exception {
        runDeviceTestsAsUser(MANAGED_PROFILE_PKG, ".ProvisioningTest",
                "testProvisionManagedProfile", mParentUserId);
        mProfileUserId = getFirstManagedProfileUserId();
    }

    private void provisionManagedProfile_accountCopy() throws Exception {
        runDeviceTestsAsUser(MANAGED_PROFILE_PKG, ".ProvisioningTest",
                "testProvisionManagedProfile_accountCopy", mParentUserId);
        mProfileUserId = getFirstManagedProfileUserId();
    }
}
