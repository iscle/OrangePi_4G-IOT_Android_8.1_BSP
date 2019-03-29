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

import com.android.compatibility.common.tradefed.build.CompatibilityBuildHelper;
import com.android.tradefed.build.IBuildInfo;
import com.android.tradefed.device.DeviceNotAvailableException;
import com.android.tradefed.testtype.DeviceTestCase;
import com.android.tradefed.testtype.IAbi;
import com.android.tradefed.testtype.IAbiReceiver;
import com.android.tradefed.testtype.IBuildReceiver;

import java.util.ArrayList;

/**
 * Tests for ephemeral packages.
 */
public class InstantAppUserTest extends DeviceTestCase
        implements IAbiReceiver, IBuildReceiver {

    // an application to verify instant/full app per user
    private static final String USER_APK = "CtsEphemeralTestsUserApp.apk";
    private static final String USER_PKG = "com.android.cts.userapp";

    private static final String USER_TEST_APK = "CtsEphemeralTestsUserAppTest.apk";
    private static final String USER_TEST_PKG = "com.android.cts.userapptest";

    private static final String TEST_CLASS = ".ClientTest";

    private static final boolean MATCH_UNINSTALLED = true;
    private static final boolean MATCH_NORMAL = false;

    private static final int USER_SYSTEM = 0; // From the UserHandle class.

    private String mOldVerifierValue;
    private IAbi mAbi;
    private IBuildInfo mBuildInfo;
    private boolean mSupportsMultiUser;
    private int mPrimaryUserId;
    /** Users we shouldn't delete in the tests */
    private ArrayList<Integer> mFixedUsers;
    private int[] mTestUser = new int[2];

    @Override
    public void setAbi(IAbi abi) {
        mAbi = abi;
    }

    @Override
    public void setBuild(IBuildInfo buildInfo) {
        mBuildInfo = buildInfo;
    }

    public void setUp() throws Exception {
        super.setUp();

        assertNotNull(mAbi);
        assertNotNull(mBuildInfo);

        // This test only runs when we have at least 3 users to work with
        final int[] users = Utils.prepareMultipleUsers(getDevice(), 3);
        mSupportsMultiUser = (users.length == 3);
        if (mSupportsMultiUser) {
            mPrimaryUserId = getDevice().getPrimaryUserId();
            mFixedUsers = new ArrayList<>();
            mFixedUsers.add(mPrimaryUserId);
            if (mPrimaryUserId != USER_SYSTEM) {
                mFixedUsers.add(USER_SYSTEM);
            }
            getDevice().switchUser(mPrimaryUserId);

            mTestUser[0] = users[1];
            mTestUser[1] = users[2];

            uninstallTestPackages();
            installTestPackages();
        }
    }

    public void tearDown() throws Exception {
        if (mSupportsMultiUser) {
            uninstallTestPackages();
        }
        super.tearDown();
    }

    public void testInstallInstant() throws Exception {
        if (!mSupportsMultiUser) {
            return;
        }
        installInstantApp(USER_APK);
        runDeviceTestsAsUser(USER_TEST_PKG, TEST_CLASS, "testQueryInstant", mPrimaryUserId);
        runDeviceTestsAsUser(USER_TEST_PKG, TEST_CLASS, "testQueryInstant", mTestUser[0]);
        runDeviceTestsAsUser(USER_TEST_PKG, TEST_CLASS, "testQueryInstant", mTestUser[1]);
    }

    public void testInstallFull() throws Exception {
        if (!mSupportsMultiUser) {
            return;
        }
        installApp(USER_APK);
        runDeviceTestsAsUser(USER_TEST_PKG, TEST_CLASS, "testQueryFull", mPrimaryUserId);
        runDeviceTestsAsUser(USER_TEST_PKG, TEST_CLASS, "testQueryFull", mTestUser[0]);
        runDeviceTestsAsUser(USER_TEST_PKG, TEST_CLASS, "testQueryFull", mTestUser[1]);
    }

    public void testInstallMultiple() throws Exception {
        if (!mSupportsMultiUser) {
            return;
        }
        installAppAsUser(USER_APK, mPrimaryUserId);
        installExistingInstantAppAsUser(USER_PKG, mTestUser[0]);
        installExistingFullAppAsUser(USER_PKG, mTestUser[1]);
        runDeviceTestsAsUser(USER_TEST_PKG, TEST_CLASS, "testQueryFull", mPrimaryUserId);
        runDeviceTestsAsUser(USER_TEST_PKG, TEST_CLASS, "testQueryInstant", mTestUser[0]);
        runDeviceTestsAsUser(USER_TEST_PKG, TEST_CLASS, "testQueryFull", mTestUser[1]);
    }

    public void testUpgradeExisting() throws Exception {
        if (!mSupportsMultiUser) {
            return;
        }
        installInstantApp(USER_APK);
        runDeviceTestsAsUser(USER_TEST_PKG, TEST_CLASS, "testQueryInstant", mPrimaryUserId);
        runDeviceTestsAsUser(USER_TEST_PKG, TEST_CLASS, "testQueryInstant", mTestUser[0]);
        runDeviceTestsAsUser(USER_TEST_PKG, TEST_CLASS, "testQueryInstant", mTestUser[1]);

        installExistingFullAppAsUser(USER_PKG, mTestUser[0]);
        runDeviceTestsAsUser(USER_TEST_PKG, TEST_CLASS, "testQueryInstant", mPrimaryUserId);
        runDeviceTestsAsUser(USER_TEST_PKG, TEST_CLASS, "testQueryFull", mTestUser[0]);
        runDeviceTestsAsUser(USER_TEST_PKG, TEST_CLASS, "testQueryInstant", mTestUser[1]);

        installExistingFullAppAsUser(USER_PKG, mTestUser[1]);
        runDeviceTestsAsUser(USER_TEST_PKG, TEST_CLASS, "testQueryInstant", mPrimaryUserId);
        runDeviceTestsAsUser(USER_TEST_PKG, TEST_CLASS, "testQueryFull", mTestUser[0]);
        runDeviceTestsAsUser(USER_TEST_PKG, TEST_CLASS, "testQueryFull", mTestUser[1]);
    }

    public void testReplaceExisting() throws Exception {
        if (!mSupportsMultiUser) {
            return;
        }
        installInstantApp(USER_APK);
        runDeviceTestsAsUser(USER_TEST_PKG, TEST_CLASS, "testQueryInstant", mPrimaryUserId);
        runDeviceTestsAsUser(USER_TEST_PKG, TEST_CLASS, "testQueryInstant", mTestUser[0]);
        runDeviceTestsAsUser(USER_TEST_PKG, TEST_CLASS, "testQueryInstant", mTestUser[1]);

        replaceFullAppAsUser(USER_APK, mTestUser[0]);
        runDeviceTestsAsUser(USER_TEST_PKG, TEST_CLASS, "testQueryInstant", mPrimaryUserId);
        runDeviceTestsAsUser(USER_TEST_PKG, TEST_CLASS, "testQueryFull", mTestUser[0]);
        runDeviceTestsAsUser(USER_TEST_PKG, TEST_CLASS, "testQueryInstant", mTestUser[1]);

        replaceFullAppAsUser(USER_APK, mTestUser[1]);
        runDeviceTestsAsUser(USER_TEST_PKG, TEST_CLASS, "testQueryInstant", mPrimaryUserId);
        runDeviceTestsAsUser(USER_TEST_PKG, TEST_CLASS, "testQueryFull", mTestUser[0]);
        runDeviceTestsAsUser(USER_TEST_PKG, TEST_CLASS, "testQueryFull", mTestUser[1]);
    }

    private void installTestPackages() throws Exception {
        installApp(USER_TEST_APK);
    }

    private void uninstallTestPackages() throws Exception {
        getDevice().uninstallPackage(USER_TEST_PKG);
        getDevice().uninstallPackage(USER_PKG);
    }

    private void runDeviceTests(String packageName, String testClassName, String testMethodName)
            throws DeviceNotAvailableException {
        Utils.runDeviceTests(getDevice(), packageName, testClassName, testMethodName);
    }

    private void runDeviceTestsAsUser(String packageName, String testClassName,
            String testMethodName, int userId)
            throws DeviceNotAvailableException {
        Utils.runDeviceTests(getDevice(), packageName, testClassName, testMethodName, userId);
    }

    private void installApp(String apk) throws Exception {
        CompatibilityBuildHelper buildHelper = new CompatibilityBuildHelper(mBuildInfo);
        assertNull(getDevice().installPackage(buildHelper.getTestFile(apk), false));
    }

    private void installInstantApp(String apk) throws Exception {
        CompatibilityBuildHelper buildHelper = new CompatibilityBuildHelper(mBuildInfo);
        assertNull(getDevice().installPackage(buildHelper.getTestFile(apk), false, "--instant"));
    }

    private void installAppAsUser(String apk, int userId) throws Exception {
        CompatibilityBuildHelper buildHelper = new CompatibilityBuildHelper(mBuildInfo);
        assertNull(getDevice().installPackage(buildHelper.getTestFile(apk), false));
    }

    private void replaceFullAppAsUser(String apk, int userId) throws Exception {
        CompatibilityBuildHelper buildHelper = new CompatibilityBuildHelper(mBuildInfo);
        assertNull(getDevice().installPackageForUser(
                buildHelper.getTestFile(apk), true, userId, "--full"));
    }

    private void installExistingInstantAppAsUser(String packageName, int userId) throws Exception {
        final String installString =
                "Package " + packageName + " installed for user: " + userId + "\n";
        CompatibilityBuildHelper buildHelper = new CompatibilityBuildHelper(mBuildInfo);
        assertEquals(installString, getDevice().executeShellCommand(
                "cmd package install-existing --instant"
                        + " --user " + Integer.toString(userId)
                        + " " + packageName));
    }

    private void installExistingFullAppAsUser(String packageName, int userId) throws Exception {
        final String installString =
                "Package " + packageName + " installed for user: " + userId + "\n";
        CompatibilityBuildHelper buildHelper = new CompatibilityBuildHelper(mBuildInfo);
        assertEquals(installString, getDevice().executeShellCommand(
                "cmd package install-existing --full"
                        + " --user " + Integer.toString(userId)
                        + " " + packageName));
    }
}
