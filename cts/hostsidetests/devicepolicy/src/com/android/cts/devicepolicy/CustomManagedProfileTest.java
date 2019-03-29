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
package com.android.cts.devicepolicy;

import com.android.tradefed.device.DeviceNotAvailableException;

public class CustomManagedProfileTest extends BaseDevicePolicyTest {

    private static final String MANAGED_PROFILE_PKG = "com.android.cts.managedprofile";
    private static final String MANAGED_PROFILE_APK = "CtsManagedProfileApp.apk";

    @Override
    protected void setUp() throws Exception {
        super.setUp();

        // We need multi user to be supported in order to create a profile of the user owner.
        mHasFeature = mHasFeature && hasDeviceFeature("android.software.managed_users");
    }

    public void testIsProvisioningAllowed() throws Exception {
        final int primaryUserId = getPrimaryUser();
        // Must install the apk since the test runs in the ManagedProfile apk.
        installAppAsUser(MANAGED_PROFILE_APK, mPrimaryUserId);
        try {
            if (mHasFeature) {
                // Since we assume, in ManagedProfileTest, provisioning has to be successful,
                // DevicePolicyManager.isProvisioningAllowed must return true
                assertIsProvisioningAllowed(true, primaryUserId);
            } else {
                // Test the case when feature flag is off
                assertIsProvisioningAllowed(false, primaryUserId);
            }
        } finally {
            getDevice().uninstallPackage(MANAGED_PROFILE_PKG);
        }
    }

    private void assertIsProvisioningAllowed(boolean expected, int userId)
            throws DeviceNotAvailableException {
        final String testName = expected ? "testIsProvisioningAllowedTrue"
                : "testIsProvisioningAllowedFalse";
        runDeviceTestsAsUser(MANAGED_PROFILE_PKG, ".PreManagedProfileTest", testName, userId);
    }
}
