/*
 * Copyright (C) 2014 The Android Open Source Project
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

import com.android.ddmlib.Log.LogLevel;
import com.android.tradefed.device.DeviceNotAvailableException;
import com.android.tradefed.log.LogUtil.CLog;

import java.util.Collections;

/**
 * Set of tests for LauncherApps with managed profiles.
 */
public class LauncherAppsProfileTest extends BaseLauncherAppsTest {

    private static final String MANAGED_PROFILE_PKG = "com.android.cts.managedprofile";
    private static final String MANAGED_PROFILE_APK = "CtsManagedProfileApp.apk";
    private static final String ADMIN_RECEIVER_TEST_CLASS =
            MANAGED_PROFILE_PKG + ".BaseManagedProfileTest$BasicAdminReceiver";

    private int mProfileUserId;
    private int mParentUserId;
    private String mProfileSerialNumber;
    private String mMainUserSerialNumber;

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        mHasFeature = mHasFeature && hasDeviceFeature("android.software.managed_users");
        if (mHasFeature) {
            removeTestUsers();
            // Create a managed profile
            mParentUserId = mPrimaryUserId;
            mProfileUserId = createManagedProfile(mParentUserId);
            installAppAsUser(MANAGED_PROFILE_APK, mProfileUserId);
            setProfileOwnerOrFail(MANAGED_PROFILE_PKG + "/" + ADMIN_RECEIVER_TEST_CLASS,
                    mProfileUserId);
            mProfileSerialNumber = Integer.toString(getUserSerialNumber(mProfileUserId));
            mMainUserSerialNumber = Integer.toString(getUserSerialNumber(mParentUserId));
            startUser(mProfileUserId);

            // Install test APK.
            installTestApps();

            // Also install on the managed profile too.
            installAppAsUser(LAUNCHER_TESTS_APK, mProfileUserId);
            installAppAsUser(LAUNCHER_TESTS_SUPPORT_APK, mProfileUserId);
        }
    }

    @Override
    protected void tearDown() throws Exception {
        if (mHasFeature) {
            removeUser(mProfileUserId);
            uninstallTestApps();
        }
        super.tearDown();
    }

    public void testGetActivitiesWithProfile() throws Exception {
        if (!mHasFeature) {
            return;
        }
        // Install app for all users.
        installAppAsUser(SIMPLE_APP_APK, mParentUserId);
        installAppAsUser(SIMPLE_APP_APK, mProfileUserId);

        // Run tests to check SimpleApp exists in both profile and main user.
        runDeviceTestsAsUser(LAUNCHER_TESTS_PKG,
                LAUNCHER_TESTS_CLASS, "testSimpleAppInstalledForUser",
                mParentUserId, Collections.singletonMap(PARAM_TEST_USER, mProfileSerialNumber));
        runDeviceTestsAsUser(LAUNCHER_TESTS_PKG,
                LAUNCHER_TESTS_CLASS, "testSimpleAppInstalledForUser",
                mParentUserId, Collections.singletonMap(PARAM_TEST_USER, mMainUserSerialNumber));

        // Run the same test on the managed profile.  This still works.
        runDeviceTestsAsUser(LAUNCHER_TESTS_PKG,
                LAUNCHER_TESTS_CLASS, "testSimpleAppInstalledForUser",
                mProfileUserId, Collections.singletonMap(PARAM_TEST_USER, mProfileSerialNumber));

        // This tries to access main prorfile from work profiel, which is not allowed.
        runDeviceTestsAsUser(LAUNCHER_TESTS_PKG,
                LAUNCHER_TESTS_CLASS, "testAccessPrimaryProfileFromManagedProfile",
                mProfileUserId, Collections.singletonMap(PARAM_TEST_USER, mMainUserSerialNumber));

        // Test for getProfiles.
        runDeviceTestsAsUser(LAUNCHER_TESTS_PKG,
                LAUNCHER_TESTS_CLASS, "testGetProfiles_fromMainProfile",
                mParentUserId);
        runDeviceTestsAsUser(LAUNCHER_TESTS_PKG,
                LAUNCHER_TESTS_CLASS, "testGetProfiles_fromManagedProfile",
                mProfileUserId);
    }

    public void testLauncherCallbackPackageAddedProfile() throws Exception {
        if (!mHasFeature) {
            return;
        }
        startCallbackService();
        installAppAsUser(SIMPLE_APP_APK, mProfileUserId);
        runDeviceTestsAsUser(LAUNCHER_TESTS_PKG,
                LAUNCHER_TESTS_CLASS,
                "testPackageAddedCallbackForUser",
                mParentUserId, Collections.singletonMap(PARAM_TEST_USER, mProfileSerialNumber));
    }

    public void testLauncherCallbackPackageRemovedProfile() throws Exception {
        if (!mHasFeature) {
            return;
        }
        installAppAsUser(SIMPLE_APP_APK, mProfileUserId);
        startCallbackService();
        getDevice().uninstallPackage(SIMPLE_APP_PKG);
        runDeviceTestsAsUser(LAUNCHER_TESTS_PKG,
                LAUNCHER_TESTS_CLASS,
                "testPackageRemovedCallbackForUser",
                mParentUserId, Collections.singletonMap(PARAM_TEST_USER, mProfileSerialNumber));
    }

    public void testLauncherCallbackPackageChangedProfile() throws Exception {
        if (!mHasFeature) {
            return;
        }
        installAppAsUser(SIMPLE_APP_APK, mProfileUserId);
        startCallbackService();
        installAppAsUser(SIMPLE_APP_APK, mProfileUserId);
        runDeviceTestsAsUser(LAUNCHER_TESTS_PKG,
                LAUNCHER_TESTS_CLASS,
                "testPackageChangedCallbackForUser",
                mParentUserId, Collections.singletonMap(PARAM_TEST_USER, mProfileSerialNumber));
    }
}
