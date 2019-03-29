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
 * limitations under the License
 */

package android.appsecurity.cts;

import com.android.tradefed.device.DeviceNotAvailableException;

/**
 * Tests for visibility of packages installed in one user, in a different user.
 */
public class PackageVisibilityTest extends BaseAppSecurityTest {

    private static final String TINY_APK = "CtsPkgInstallTinyApp.apk";
    private static final String TINY_PKG = "android.appsecurity.cts.tinyapp";

    private static final String TEST_APK = "CtsPkgAccessApp.apk";
    private static final String TEST_PKG = "com.android.cts.packageaccessapp";

    private static final boolean MATCH_UNINSTALLED = true;
    private static final boolean MATCH_NORMAL = false;

    private int[] mUsers;
    private String mOldVerifierValue;

    public void setUp() throws Exception {
        super.setUp();

        mUsers = Utils.prepareMultipleUsers(getDevice());
        mOldVerifierValue =
                getDevice().executeShellCommand("settings get global package_verifier_enable");
        getDevice().executeShellCommand("settings put global package_verifier_enable 0");
        getDevice().uninstallPackage(TEST_PKG);
        getDevice().uninstallPackage(TINY_PKG);
        installTestAppForUser(TEST_APK, mPrimaryUserId);
    }

    public void tearDown() throws Exception {
        getDevice().uninstallPackage(TEST_PKG);
        getDevice().uninstallPackage(TINY_PKG);
        getDevice().executeShellCommand("settings put global package_verifier_enable "
                + mOldVerifierValue);
        super.tearDown();
    }

    public void testUninstalledPackageVisibility() throws Exception {
        if (!mSupportsMultiUser) {
            return;
        }

        int userId = mUsers[1];
        assertTrue(userId > 0);
        getDevice().startUser(userId);
        installTestAppForUser(TEST_APK, userId);
        installTestAppForUser(TEST_APK, mPrimaryUserId);

        installTestAppForUser(TINY_APK, mPrimaryUserId);

        // It is visible for the installed user, using shell commands
        assertTrue(isAppVisibleForUser(TINY_PKG, mPrimaryUserId, MATCH_NORMAL));
        assertTrue(isAppVisibleForUser(TINY_PKG, mPrimaryUserId, MATCH_UNINSTALLED));

        // Try the same from an app
        Utils.runDeviceTests(getDevice(), TEST_PKG,
                ".PackageAccessTest", "testPackageAccess_inUser", mPrimaryUserId);
        Utils.runDeviceTests(getDevice(), TEST_PKG,
                ".PackageAccessTest", "testPackageAccess_inUserUninstalled", mPrimaryUserId);

        // It is not visible for the other user using shell commands
        assertFalse(isAppVisibleForUser(TINY_PKG, userId, MATCH_NORMAL));
        assertFalse(isAppVisibleForUser(TINY_PKG, userId, MATCH_UNINSTALLED));

        // Try the same from an app
        Utils.runDeviceTests(getDevice(), TEST_PKG,
                ".PackageAccessTest", "testPackageAccess_notInOtherUser", userId);
        Utils.runDeviceTests(getDevice(), TEST_PKG,
                ".PackageAccessTest", "testPackageAccess_notInOtherUserUninstalled", userId);

        Utils.runDeviceTests(getDevice(), TEST_PKG,
                ".PackageAccessTest", "testPackageAccess_getPackagesCantSeeTiny", userId);

        getDevice().uninstallPackage(TINY_PKG);

        // Install for the new user
        installTestAppForUser(TINY_APK, userId);

        // It is visible for the installed user
        assertTrue(isAppVisibleForUser(TINY_PKG, userId, MATCH_NORMAL));
        assertTrue(isAppVisibleForUser(TINY_PKG, userId, MATCH_UNINSTALLED));

        // It is not visible for the other user
        assertFalse(isAppVisibleForUser(TINY_PKG, mPrimaryUserId, MATCH_NORMAL));
        assertFalse(isAppVisibleForUser(TINY_PKG, mPrimaryUserId, MATCH_UNINSTALLED));

        // Uninstall with keep data
        uninstallWithKeepDataForUser(TINY_PKG, userId);

        // It is visible for the installed user, but only if match uninstalled
        assertFalse(isAppVisibleForUser(TINY_PKG, userId, MATCH_NORMAL));
        assertTrue(isAppVisibleForUser(TINY_PKG, userId, MATCH_UNINSTALLED));

        Utils.runDeviceTests(getDevice(), TEST_PKG,
                ".PackageAccessTest", "testPackageAccess_getPackagesCanSeeTiny", userId);

        Utils.runDeviceTests(getDevice(), TEST_PKG,
                ".PackageAccessTest", "testPackageAccess_notInOtherUserUninstalled",
                mPrimaryUserId);
        Utils.runDeviceTests(getDevice(), TEST_PKG,
                ".PackageAccessTest", "testPackageAccess_getPackagesCantSeeTiny", mPrimaryUserId);

        getDevice().uninstallPackage(TINY_PKG);
        getDevice().uninstallPackage(TEST_PKG);
    }

    protected void uninstallWithKeepDataForUser(String packageName, int userId)
            throws DeviceNotAvailableException {
        final String command = "pm uninstall -k --user " + userId + " " + packageName;
        getDevice().executeShellCommand(command);
    }
}
