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

/**
 * Host side tests for profile owner.  Run the CtsProfileOwnerApp device side test.
 */
public class ProfileOwnerTest extends BaseDevicePolicyTest {
    private static final String PROFILE_OWNER_PKG = "com.android.cts.profileowner";
    private static final String PROFILE_OWNER_APK = "CtsProfileOwnerApp.apk";

    private static final String ADMIN_RECEIVER_TEST_CLASS =
            PROFILE_OWNER_PKG + ".BaseProfileOwnerTest$BasicAdminReceiver";

    private int mUserId = 0;

    @Override
    protected void setUp() throws Exception {
        super.setUp();

        mUserId = getPrimaryUser();


        if (mHasFeature) {
            installAppAsUser(PROFILE_OWNER_APK, mUserId);
            if (!setProfileOwner(
                    PROFILE_OWNER_PKG + "/" + ADMIN_RECEIVER_TEST_CLASS, mUserId,
                    /* expectFailure */ false)) {
                removeAdmin(PROFILE_OWNER_PKG + "/" + ADMIN_RECEIVER_TEST_CLASS, mUserId);
                getDevice().uninstallPackage(PROFILE_OWNER_PKG);
                fail("Failed to set profile owner");
            }
        }
    }

    public void testWifi() throws Exception {
        if (!mHasFeature || !hasDeviceFeature("android.hardware.wifi")) {
            return;
        }
        executeProfileOwnerTest("WifiTest");
    }

    public void testManagement() throws Exception {
        if (!mHasFeature) {
            return;
        }
        executeProfileOwnerTest("ManagementTest");
    }

    public void testAdminActionBookkeeping() throws Exception {
        if (!mHasFeature) {
            return;
        }
        executeProfileOwnerTest("AdminActionBookkeepingTest");
    }

    @Override
    protected void tearDown() throws Exception {
        if (mHasFeature) {
            assertTrue("Failed to remove profile owner.",
                    removeAdmin(PROFILE_OWNER_PKG + "/" + ADMIN_RECEIVER_TEST_CLASS, mUserId));
            getDevice().uninstallPackage(PROFILE_OWNER_PKG);
        }

        super.tearDown();
    }

    private void executeProfileOwnerTest(String testClassName) throws Exception {
        if (!mHasFeature) {
            return;
        }
        String testClass = PROFILE_OWNER_PKG + "." + testClassName;
        runDeviceTestsAsUser(PROFILE_OWNER_PKG, testClass, mPrimaryUserId);
    }
}
