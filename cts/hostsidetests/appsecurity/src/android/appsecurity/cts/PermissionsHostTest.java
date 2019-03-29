/*
 * Copyright (C) 2015 The Android Open Source Project
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
import com.android.tradefed.testtype.DeviceTestCase;
import com.android.tradefed.testtype.IAbi;
import com.android.tradefed.testtype.IAbiReceiver;
import com.android.tradefed.testtype.IBuildReceiver;

/**
 * Set of tests that verify behavior of runtime permissions, including both
 * dynamic granting and behavior of legacy apps.
 */
public class PermissionsHostTest extends DeviceTestCase implements IAbiReceiver, IBuildReceiver {
    private static final String USES_PERMISSION_PKG = "com.android.cts.usepermission";
    private static final String ESCALATE_PERMISSION_PKG = "com.android.cts.escalate.permission";

    private static final String APK_22 = "CtsUsePermissionApp22.apk";
    private static final String APK_23 = "CtsUsePermissionApp23.apk";
    private static final String APK_25 = "CtsUsePermissionApp25.apk";
    private static final String APK_26 = "CtsUsePermissionApp26.apk";
    private static final String APK_Latest = "CtsUsePermissionAppLatest.apk";

    private static final String APK_PERMISSION_POLICY_25 = "CtsPermissionPolicyTest25.apk";
    private static final String PERMISSION_POLICY_25_PKG = "com.android.cts.permission.policy";

    private static final String APK_DECLARE_NON_RUNTIME_PERMISSIONS =
            "CtsDeclareNonRuntimePermissions.apk";
    private static final String APK_ESCLATE_TO_RUNTIME_PERMISSIONS =
            "CtsEscalateToRuntimePermissions.apk";

    private static final String SCREEN_OFF_TIMEOUT_NS = "system";
    private static final String SCREEN_OFF_TIMEOUT_KEY = "screen_off_timeout";
    private String mScreenTimeoutBeforeTest;

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

        getDevice().uninstallPackage(USES_PERMISSION_PKG);
        getDevice().uninstallPackage(ESCALATE_PERMISSION_PKG);
        getDevice().uninstallPackage(PERMISSION_POLICY_25_PKG);

        // Set screen timeout to 30 min to not timeout while waiting for UI to change
        mScreenTimeoutBeforeTest = getDevice().getSetting(SCREEN_OFF_TIMEOUT_NS,
                SCREEN_OFF_TIMEOUT_KEY);
        getDevice().setSetting(SCREEN_OFF_TIMEOUT_NS, SCREEN_OFF_TIMEOUT_KEY, "1800000");

        // Wake up device
        getDevice().executeShellCommand("input keyevent KEYCODE_WAKEUP");
        getDevice().disableKeyguard();
    }

    @Override
    protected void tearDown() throws Exception {
        super.tearDown();

        getDevice().setSetting(SCREEN_OFF_TIMEOUT_NS, SCREEN_OFF_TIMEOUT_KEY,
                mScreenTimeoutBeforeTest);

        getDevice().uninstallPackage(USES_PERMISSION_PKG);
        getDevice().uninstallPackage(ESCALATE_PERMISSION_PKG);
        getDevice().uninstallPackage(PERMISSION_POLICY_25_PKG);
    }

    public void testFail() throws Exception {
        // Sanity check that remote failure is host failure
        assertNull(getDevice().installPackage(mBuildHelper.getTestFile(APK_23), false, false));
        boolean didThrow = false;
        try {
            runDeviceTests(USES_PERMISSION_PKG, "com.android.cts.usepermission.UsePermissionTest23",
                    "testFail");
        } catch (AssertionError expected) {
            didThrow = true;
        }
        if (!didThrow) {
            fail("Expected remote failure");
        }
    }

    public void testKill() throws Exception {
        // Sanity check that remote kill is host failure
        assertNull(getDevice().installPackage(mBuildHelper.getTestFile(APK_23), false, false));
        boolean didThrow = false;
        try {
            runDeviceTests(USES_PERMISSION_PKG, "com.android.cts.usepermission.UsePermissionTest23",
                    "testKill");
        } catch (AssertionError expected) {
            didThrow = true;
        }
        if (!didThrow) {
            fail("Expected remote failure");
        }
    }

    public void testCompatDefault22() throws Exception {
        assertNull(getDevice().installPackage(mBuildHelper.getTestFile(APK_22), false, false));
        runDeviceTests(USES_PERMISSION_PKG, "com.android.cts.usepermission.UsePermissionTest22",
                "testCompatDefault");
    }

    public void testCompatRevoked22() throws Exception {
        assertNull(getDevice().installPackage(mBuildHelper.getTestFile(APK_22), false, false));
        boolean didThrow = false;
        try {
            runDeviceTests(USES_PERMISSION_PKG, "com.android.cts.usepermission.UsePermissionTest22",
                    "testCompatRevoked_part1");
        } catch (AssertionError expected) {
            didThrow = true;
        }
        if (!didThrow) {
            fail("App must be killed on a permission revoke");
        }
        runDeviceTests(USES_PERMISSION_PKG, "com.android.cts.usepermission.UsePermissionTest22",
                "testCompatRevoked_part2");
    }

    public void testNoRuntimePrompt22() throws Exception {
        assertNull(getDevice().installPackage(mBuildHelper.getTestFile(APK_22), false, false));
        runDeviceTests(USES_PERMISSION_PKG, "com.android.cts.usepermission.UsePermissionTest22",
                "testNoRuntimePrompt");
    }

    public void testDefault23() throws Exception {
        assertNull(getDevice().installPackage(mBuildHelper.getTestFile(APK_23), false, false));
        runDeviceTests(USES_PERMISSION_PKG, "com.android.cts.usepermission.UsePermissionTest23",
                "testDefault");
    }

    public void testGranted23() throws Exception {
        assertNull(getDevice().installPackage(mBuildHelper.getTestFile(APK_23), false, false));
        runDeviceTests(USES_PERMISSION_PKG, "com.android.cts.usepermission.UsePermissionTest23",
                "testGranted");
    }

    public void testInteractiveGrant23() throws Exception {
        assertNull(getDevice().installPackage(mBuildHelper.getTestFile(APK_23), false, false));
        runDeviceTests(USES_PERMISSION_PKG, "com.android.cts.usepermission.UsePermissionTest23",
                "testInteractiveGrant");
    }

    public void testRuntimeGroupGrantSpecificity23() throws Exception {
        assertNull(getDevice().installPackage(mBuildHelper.getTestFile(APK_23), false, false));
        runDeviceTests(USES_PERMISSION_PKG, "com.android.cts.usepermission.UsePermissionTest23",
                "testRuntimeGroupGrantSpecificity");
    }

    public void testRuntimeGroupGrantExpansion23() throws Exception {
        assertNull(getDevice().installPackage(mBuildHelper.getTestFile(APK_23), false, false));
        runDeviceTests(USES_PERMISSION_PKG, "com.android.cts.usepermission.UsePermissionTest23",
                "testRuntimeGroupGrantExpansion");
    }

    public void testRuntimeGroupGrantExpansion25() throws Exception {
        assertNull(getDevice().installPackage(mBuildHelper.getTestFile(APK_25), false, false));
        runDeviceTests(USES_PERMISSION_PKG, "com.android.cts.usepermission.UsePermissionTest23",
                "testRuntimeGroupGrantExpansion");
    }

    public void testRuntimeGroupGrantExpansion26() throws Exception {
        assertNull(getDevice().installPackage(mBuildHelper.getTestFile(APK_26), false, false));
        runDeviceTests(USES_PERMISSION_PKG, "com.android.cts.usepermission.UsePermissionTest26",
                "testRuntimeGroupGrantNoExpansion");
    }

    public void testRuntimeGroupGrantExpansionLatest() throws Exception {
        assertNull(getDevice().installPackage(mBuildHelper.getTestFile(APK_Latest), false, false));
        runDeviceTests(USES_PERMISSION_PKG, "com.android.cts.usepermission.UsePermissionTest26",
                "testRuntimeGroupGrantNoExpansion");
    }

    public void testCancelledPermissionRequest23() throws Exception {
        assertNull(getDevice().installPackage(mBuildHelper.getTestFile(APK_23), false, false));
        runDeviceTests(USES_PERMISSION_PKG, "com.android.cts.usepermission.UsePermissionTest23",
                "testCancelledPermissionRequest");
    }

    public void testRequestGrantedPermission23() throws Exception {
        assertNull(getDevice().installPackage(mBuildHelper.getTestFile(APK_23), false, false));
        runDeviceTests(USES_PERMISSION_PKG, "com.android.cts.usepermission.UsePermissionTest23",
                "testRequestGrantedPermission");
    }

    public void testDenialWithPrejudice23() throws Exception {
        assertNull(getDevice().installPackage(mBuildHelper.getTestFile(APK_23), false, false));
        runDeviceTests(USES_PERMISSION_PKG, "com.android.cts.usepermission.UsePermissionTest23",
                "testDenialWithPrejudice");
    }

    public void testRevokeAffectsWholeGroup23() throws Exception {
        assertNull(getDevice().installPackage(mBuildHelper.getTestFile(APK_23), false, false));
        boolean didThrow = false;
        try {
            runDeviceTests(USES_PERMISSION_PKG, "com.android.cts.usepermission.UsePermissionTest23",
                    "testRevokeAffectsWholeGroup_part1");
        } catch (AssertionError expected) {
            didThrow = true;
        }
        if (!didThrow) {
            fail("Should have thrown an exception.");
        }
        runDeviceTests(USES_PERMISSION_PKG, "com.android.cts.usepermission.UsePermissionTest23",
                "testRevokeAffectsWholeGroup_part2");
    }

    public void testGrantPreviouslyRevokedWithPrejudiceShowsPrompt23() throws Exception {
        assertNull(getDevice().installPackage(mBuildHelper.getTestFile(APK_23), false, false));
        boolean didThrow = false;
        try {
            runDeviceTests(USES_PERMISSION_PKG, "com.android.cts.usepermission.UsePermissionTest23",
                    "testGrantPreviouslyRevokedWithPrejudiceShowsPrompt_part1");
        } catch (Throwable expected) {
            didThrow = true;
        }
        if (!didThrow) {
            fail("App must be killed on a permission revoke");
        }
        runDeviceTests(USES_PERMISSION_PKG, "com.android.cts.usepermission.UsePermissionTest23",
                "testGrantPreviouslyRevokedWithPrejudiceShowsPrompt_part2");
    }

    public void testRequestNonRuntimePermission23() throws Exception {
        assertNull(getDevice().installPackage(mBuildHelper.getTestFile(APK_23), false, false));
        runDeviceTests(USES_PERMISSION_PKG, "com.android.cts.usepermission.UsePermissionTest23",
                "testRequestNonRuntimePermission");
    }

    public void testRequestNonExistentPermission23() throws Exception {
        assertNull(getDevice().installPackage(mBuildHelper.getTestFile(APK_23), false, false));
        runDeviceTests(USES_PERMISSION_PKG, "com.android.cts.usepermission.UsePermissionTest23",
                "testRequestNonExistentPermission");
    }

    public void testRequestPermissionFromTwoGroups23() throws Exception {
        assertNull(getDevice().installPackage(mBuildHelper.getTestFile(APK_23), false, false));
        runDeviceTests(USES_PERMISSION_PKG, "com.android.cts.usepermission.UsePermissionTest23",
                "testRequestPermissionFromTwoGroups");
    }

    public void testUpgradeKeepsPermissions() throws Exception {
        assertNull(getDevice().installPackage(mBuildHelper.getTestFile(APK_22), false, false));
        runDeviceTests(USES_PERMISSION_PKG, "com.android.cts.usepermission.UsePermissionTest22",
                "testAllPermissionsGrantedByDefault");
        assertNull(getDevice().installPackage(mBuildHelper.getTestFile(APK_23), true, false));
        runDeviceTests(USES_PERMISSION_PKG, "com.android.cts.usepermission.UsePermissionTest23",
                "testAllPermissionsGrantedOnUpgrade");
    }

    public void testNoDowngradePermissionModel() throws Exception {
        assertNull(getDevice().installPackage(mBuildHelper.getTestFile(APK_23), false, false));
        boolean didThrow = false;
        try {
            assertNull(getDevice().installPackage(mBuildHelper.getTestFile(APK_22), true, false));
        } catch (AssertionError expected) {
            didThrow = true;
        }
        if (!didThrow) {
            fail("Permission mode downgrade not allowed");
        }
    }

    public void testNoResidualPermissionsOnUninstall() throws Exception {
        assertNull(getDevice().installPackage(mBuildHelper.getTestFile(APK_23), false, false));
        runDeviceTests(USES_PERMISSION_PKG, "com.android.cts.usepermission.UsePermissionTest23",
                "testNoResidualPermissionsOnUninstall_part1");
        assertNull(getDevice().uninstallPackage(USES_PERMISSION_PKG));
        assertNull(getDevice().installPackage(mBuildHelper.getTestFile(APK_23), false, false));
        runDeviceTests(USES_PERMISSION_PKG, "com.android.cts.usepermission.UsePermissionTest23",
                "testNoResidualPermissionsOnUninstall_part2");
    }

    public void testRevokePropagatedOnUpgradeOldToNewModel() throws Exception {
        assertNull(getDevice().installPackage(mBuildHelper.getTestFile(APK_22), false, false));
        boolean didThrow = false;
        try {
            runDeviceTests(USES_PERMISSION_PKG, "com.android.cts.usepermission.UsePermissionTest22",
                    "testRevokePropagatedOnUpgradeOldToNewModel_part1");
        } catch (AssertionError expected) {
            didThrow = true;
        }
        if (!didThrow) {
            fail("App must be killed on a permission revoke");
        }
        assertNull(getDevice().installPackage(mBuildHelper.getTestFile(APK_23), true, false));
        runDeviceTests(USES_PERMISSION_PKG, "com.android.cts.usepermission.UsePermissionTest23",
                "testRevokePropagatedOnUpgradeOldToNewModel_part2");
    }

    public void testRevokePropagatedOnUpgradeNewToNewModel() throws Exception {
        assertNull(getDevice().installPackage(mBuildHelper.getTestFile(APK_23), false, false));
        runDeviceTests(USES_PERMISSION_PKG, "com.android.cts.usepermission.UsePermissionTest23",
                "testRevokePropagatedOnUpgradeNewToNewModel_part1");
        assertNull(getDevice().installPackage(mBuildHelper.getTestFile(APK_23), true, false));
        runDeviceTests(USES_PERMISSION_PKG, "com.android.cts.usepermission.UsePermissionTest23",
                "testRevokePropagatedOnUpgradeNewToNewModel_part2");
    }

    public void testNoPermissionEscalation() throws Exception {
        assertNull(getDevice().installPackage(mBuildHelper.getTestFile(
                APK_DECLARE_NON_RUNTIME_PERMISSIONS), false, false));
        assertNull(getDevice().installPackage(mBuildHelper.getTestFile(
                APK_ESCLATE_TO_RUNTIME_PERMISSIONS), true, false));
        runDeviceTests(ESCALATE_PERMISSION_PKG,
                "com.android.cts.escalatepermission.PermissionEscalationTest",
                "testCannotEscalateNonRuntimePermissionsToRuntime");
    }

    public void testNoProtectionFlagsAddedToNonSignatureProtectionPermissions25() throws Exception {
        assertNull(getDevice().installPackage(mBuildHelper.getTestFile(
                APK_PERMISSION_POLICY_25), false, false));
        runDeviceTests(PERMISSION_POLICY_25_PKG,
                "com.android.cts.permission.policy.PermissionPolicyTest25",
                "testNoProtectionFlagsAddedToNonSignatureProtectionPermissions");
    }

    private void runDeviceTests(String packageName, String testClassName, String testMethodName)
            throws DeviceNotAvailableException {
        Utils.runDeviceTests(getDevice(), packageName, testClassName, testMethodName);
    }
}
