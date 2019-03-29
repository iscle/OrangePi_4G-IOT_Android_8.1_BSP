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
 * limitations under the License
 */
package com.android.cts.devicepolicy;

import org.junit.Test;

/**
 * This class tests the provisioning flow with an APK that declares a single receiver with
 * BIND_DEVICE_ADMIN permissions, which was a requirement for the app sending the
 * ACTION_PROVISION_MANAGED_PROFILE intent before Android M.
 */
public class ManagedProfileProvisioningSingleAdminTest extends BaseDevicePolicyTest {

    private static final String SINGLE_ADMIN_PKG = "com.android.cts.devicepolicy.singleadmin";
    private static final String SINGLE_ADMIN_APP_APK = "CtsDevicePolicySingleAdminTestApp.apk";

    private int mProfileUserId;

    @Override
    protected void setUp() throws Exception {
        super.setUp();

        // We need multi user to be supported in order to create a profile of the user owner.
        mHasFeature = mHasFeature && hasDeviceFeature("android.software.managed_users");

        if (mHasFeature) {
            removeTestUsers();
            installAppAsUser(SINGLE_ADMIN_APP_APK, mPrimaryUserId);
            mProfileUserId = 0;
        }
    }

    @Override
    protected void tearDown() throws Exception {
        if (mHasFeature) {
            if (mProfileUserId != 0) {
                removeUser(mProfileUserId);
            }
            getDevice().uninstallPackage(SINGLE_ADMIN_PKG);
        }
        super.tearDown();
    }

    @Test
    public void testEXTRA_PROVISIONING_DEVICE_ADMIN_PACKAGE_NAME() throws Exception {
        if (!mHasFeature) {
            return;
        }

        runDeviceTestsAsUser(SINGLE_ADMIN_PKG, ".ProvisioningSingleAdminTest",
                "testManagedProfileProvisioning", mPrimaryUserId);

        mProfileUserId = getFirstManagedProfileUserId();
    }
}
