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

package android.appsecurity.cts;

import com.android.compatibility.common.tradefed.build.CompatibilityBuildHelper;
import com.android.tradefed.build.IBuildInfo;
import com.android.tradefed.device.DeviceNotAvailableException;
import com.android.tradefed.device.ITestDevice;
import com.android.tradefed.testtype.DeviceTestCase;
import com.android.tradefed.testtype.IAbi;
import com.android.tradefed.testtype.IAbiReceiver;
import com.android.tradefed.testtype.IBuildReceiver;
import com.android.tradefed.util.AbiUtils;

/**
 * Tests that verify intent filters.
 */
public class PrivilegedUpdateTests extends DeviceTestCase implements IAbiReceiver, IBuildReceiver {
    private static final String SHIM_PKG = "com.android.cts.priv.ctsshim";
    /** Package name of the tests to be run */
    private static final String TEST_PKG = "com.android.cts.privilegedupdate";

    /** APK that contains the shim update; to test upgrading */
    private static final String SHIM_UPDATE_APK = "CtsShimPrivUpgradePrebuilt.apk";
    /** APK that contains the shim update w/ incorrect SHA; to test upgrade fails */
    private static final String SHIM_UPDATE_FAIL_APK = "CtsShimPrivUpgradeWrongSHAPrebuilt.apk";
    /** APK that contains individual shim test cases */
    private static final String TEST_APK = "CtsPrivilegedUpdateTests.apk";

    private static final String RESTRICTED_UPGRADE_FAILURE =
            "INSTALL_FAILED_INVALID_APK:"
            + " New package fails restrict-update check:"
            + " com.android.cts.priv.ctsshim";

    private IAbi mAbi;
    private CompatibilityBuildHelper mBuildHelper;

    @Override
    public void setAbi(IAbi abi) {
        mAbi = abi;
    }

    @Override
    public void setBuild(IBuildInfo buildInfo) {
        mBuildHelper = new CompatibilityBuildHelper(buildInfo);
    }

    @Override
    protected void setUp() throws Exception {
        super.setUp();

        Utils.prepareSingleUser(getDevice());
        assertNotNull(mAbi);
        assertNotNull(mBuildHelper);

        getDevice().uninstallPackage(SHIM_PKG);
        getDevice().uninstallPackage(TEST_PKG);

        assertNull(getDevice().installPackage(mBuildHelper.getTestFile(TEST_APK), false));
        getDevice().executeShellCommand("pm enable " + SHIM_PKG);
    }

    @Override
    protected void tearDown() throws Exception {
        super.tearDown();

        getDevice().uninstallPackage(SHIM_PKG);
        getDevice().uninstallPackage(TEST_PKG);
        getDevice().executeShellCommand("pm enable " + SHIM_PKG);
    }

    public void testPrivilegedAppUpgradeRestricted() throws Exception {
        getDevice().uninstallPackage(SHIM_PKG);
        assertEquals(RESTRICTED_UPGRADE_FAILURE, getDevice().installPackage(
                mBuildHelper.getTestFile(SHIM_UPDATE_FAIL_APK), true));
    }

    public void testSystemAppPriorities() throws Exception {
        runDeviceTests(TEST_PKG, ".PrivilegedUpdateTest", "testSystemAppPriorities");
    }

    public void testPrivilegedAppPriorities() throws Exception {
        runDeviceTests(TEST_PKG, ".PrivilegedUpdateTest", "testPrivilegedAppPriorities");
    }

    public void testPrivilegedAppUpgradePriorities() throws Exception {
        getDevice().uninstallPackage(SHIM_PKG);
        
        try {
            assertNull(getDevice().installPackage(
                    mBuildHelper.getTestFile(SHIM_UPDATE_APK), true));
            runDeviceTests(TEST_PKG, ".PrivilegedUpdateTest", "testPrivilegedAppUpgradePriorities");
        } finally {
            getDevice().uninstallPackage(SHIM_PKG);
        }
    }

    public void testDisableSystemApp() throws Exception {
        getDevice().executeShellCommand("pm enable " + SHIM_PKG);
        runDeviceTests(TEST_PKG, ".PrivilegedAppDisableTest", "testPrivAppAndEnabled");
        getDevice().executeShellCommand("pm disable-user " + SHIM_PKG);
        runDeviceTests(TEST_PKG, ".PrivilegedAppDisableTest", "testPrivAppAndDisabled");
    }

    public void testDisableUpdatedSystemApp() throws Exception {
        getDevice().executeShellCommand("pm enable " + SHIM_PKG);
        runDeviceTests(TEST_PKG, ".PrivilegedAppDisableTest", "testPrivAppAndEnabled");
        try {
            assertNull(getDevice().installPackage(
                    mBuildHelper.getTestFile(SHIM_UPDATE_APK), true));
            getDevice().executeShellCommand("pm disable-user " + SHIM_PKG);
            runDeviceTests(TEST_PKG, ".PrivilegedAppDisableTest", "testUpdatedPrivAppAndDisabled");
            getDevice().executeShellCommand("pm enable " + SHIM_PKG);
            runDeviceTests(TEST_PKG, ".PrivilegedAppDisableTest", "testUpdatedPrivAppAndEnabled");
        } finally {
            getDevice().uninstallPackage(SHIM_PKG);
        }
    }

    private void runDeviceTests(String packageName, String testClassName, String testMethodName)
            throws DeviceNotAvailableException {
        Utils.runDeviceTests(getDevice(), packageName, testClassName, testMethodName);
    }
}
