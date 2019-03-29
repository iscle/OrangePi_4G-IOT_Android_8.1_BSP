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

package com.android.cts.devicepolicy;

import android.platform.test.annotations.RequiresDevice;

import com.android.compatibility.common.tradefed.build.CompatibilityBuildHelper;
import com.android.tradefed.device.DeviceNotAvailableException;
import com.android.tradefed.log.LogUtil.CLog;

import java.io.File;
import java.io.FileNotFoundException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * Set of tests for use cases that apply to profile and device owner.
 * This class is the base class of MixedProfileOwnerTest, MixedDeviceOwnerTest and
 * MixedManagedProfileOwnerTest and is abstract to avoid running spurious tests.
 *
 * NOTE: Not all tests are executed in the subclasses.  Sometimes, if a test is not applicable to
 * a subclass, they override it with an empty method.
 */
public abstract class DeviceAndProfileOwnerTest extends BaseDevicePolicyTest {

    protected static final String DEVICE_ADMIN_PKG = "com.android.cts.deviceandprofileowner";
    protected static final String DEVICE_ADMIN_APK = "CtsDeviceAndProfileOwnerApp.apk";
    protected static final String ADMIN_RECEIVER_TEST_CLASS
            = ".BaseDeviceAdminTest$BasicAdminReceiver";

    private static final String INTENT_RECEIVER_PKG = "com.android.cts.intent.receiver";
    private static final String INTENT_RECEIVER_APK = "CtsIntentReceiverApp.apk";

    private static final String INTENT_SENDER_PKG = "com.android.cts.intent.sender";
    private static final String INTENT_SENDER_APK = "CtsIntentSenderApp.apk";

    private static final String PERMISSIONS_APP_PKG = "com.android.cts.permissionapp";
    private static final String PERMISSIONS_APP_APK = "CtsPermissionApp.apk";

    private static final String SIMPLE_PRE_M_APP_PKG = "com.android.cts.launcherapps.simplepremapp";
    private static final String SIMPLE_PRE_M_APP_APK = "CtsSimplePreMApp.apk";

    private static final String APP_RESTRICTIONS_TARGET_APP_PKG
            = "com.android.cts.apprestrictions.targetapp";
    private static final String APP_RESTRICTIONS_TARGET_APP_APK = "CtsAppRestrictionsTargetApp.apk";

    private static final String CERT_INSTALLER_PKG = "com.android.cts.certinstaller";
    private static final String CERT_INSTALLER_APK = "CtsCertInstallerApp.apk";

    private static final String DELEGATE_APP_PKG = "com.android.cts.delegate";
    private static final String DELEGATE_APP_APK = "CtsDelegateApp.apk";
    private static final String DELEGATION_CERT_INSTALL = "delegation-cert-install";
    private static final String DELEGATION_APP_RESTRICTIONS = "delegation-app-restrictions";
    private static final String DELEGATION_BLOCK_UNINSTALL = "delegation-block-uninstall";
    private static final String DELEGATION_PERMISSION_GRANT = "delegation-permission-grant";
    private static final String DELEGATION_PACKAGE_ACCESS = "delegation-package-access";
    private static final String DELEGATION_ENABLE_SYSTEM_APP = "delegation-enable-system-app";

    private static final String TEST_APP_APK = "CtsSimpleApp.apk";
    private static final String TEST_APP_PKG = "com.android.cts.launcherapps.simpleapp";
    private static final String TEST_APP_LOCATION = "/data/local/tmp/";

    private static final String PACKAGE_INSTALLER_PKG = "com.android.cts.packageinstaller";
    private static final String PACKAGE_INSTALLER_APK = "CtsPackageInstallerApp.apk";

    private static final String ACCOUNT_MANAGEMENT_PKG
            = "com.android.cts.devicepolicy.accountmanagement";
    private static final String ACCOUNT_MANAGEMENT_APK = "CtsAccountManagementDevicePolicyApp.apk";

    private static final String VPN_APP_PKG = "com.android.cts.vpnfirewall";
    private static final String VPN_APP_APK = "CtsVpnFirewallApp.apk";
    private static final String VPN_APP_API23_APK = "CtsVpnFirewallAppApi23.apk";
    private static final String VPN_APP_API24_APK = "CtsVpnFirewallAppApi24.apk";
    private static final String VPN_APP_NOT_ALWAYS_ON_APK = "CtsVpnFirewallAppNotAlwaysOn.apk";

    private static final String COMMAND_BLOCK_ACCOUNT_TYPE = "block-accounttype";
    private static final String COMMAND_UNBLOCK_ACCOUNT_TYPE = "unblock-accounttype";

    private static final String DISALLOW_MODIFY_ACCOUNTS = "no_modify_accounts";
    private static final String DISALLOW_REMOVE_USER = "no_remove_user";
    private static final String ACCOUNT_TYPE
            = "com.android.cts.devicepolicy.accountmanagement.account.type";

    private static final String CUSTOMIZATION_APP_PKG = "com.android.cts.customizationapp";
    private static final String CUSTOMIZATION_APP_APK = "CtsCustomizationApp.apk";

    private static final String AUTOFILL_APP_PKG = "com.android.cts.devicepolicy.autofillapp";
    private static final String AUTOFILL_APP_APK = "CtsDevicePolicyAutofillApp.apk";

    protected static final String ASSIST_APP_PKG = "com.android.cts.devicepolicy.assistapp";
    protected static final String ASSIST_APP_APK = "CtsDevicePolicyAssistApp.apk";

    private static final String ENABLED_NOTIFICATION_POLICY_ACCESS_PACKAGES
            = "enabled_notification_policy_access_packages";

    protected static final String ASSIST_INTERACTION_SERVICE =
            ASSIST_APP_PKG + "/.MyInteractionService";

    private static final String ARG_ALLOW_FAILURE = "allowFailure";
    // ID of the user all tests are run as. For device owner this will be the primary user, for
    // profile owner it is the user id of the created profile.
    protected int mUserId;

    @Override
    protected void tearDown() throws Exception {
        if (mHasFeature) {
            getDevice().uninstallPackage(DEVICE_ADMIN_PKG);
            getDevice().uninstallPackage(PERMISSIONS_APP_PKG);
            getDevice().uninstallPackage(SIMPLE_PRE_M_APP_PKG);
            getDevice().uninstallPackage(APP_RESTRICTIONS_TARGET_APP_PKG);
            getDevice().uninstallPackage(CERT_INSTALLER_PKG);
            getDevice().uninstallPackage(DELEGATE_APP_PKG);
            getDevice().uninstallPackage(ACCOUNT_MANAGEMENT_PKG);
            getDevice().uninstallPackage(VPN_APP_PKG);
            getDevice().uninstallPackage(VPN_APP_API23_APK);
            getDevice().uninstallPackage(VPN_APP_API24_APK);
            getDevice().uninstallPackage(VPN_APP_NOT_ALWAYS_ON_APK);
            getDevice().uninstallPackage(INTENT_RECEIVER_PKG);
            getDevice().uninstallPackage(INTENT_SENDER_PKG);
            getDevice().uninstallPackage(CUSTOMIZATION_APP_PKG);
            getDevice().uninstallPackage(AUTOFILL_APP_APK);
            getDevice().uninstallPackage(TEST_APP_PKG);

            // Press the HOME key to close any alart dialog that may be shown.
            getDevice().executeShellCommand("input keyevent 3");
        }
        super.tearDown();
    }

    public void testCaCertManagement() throws Exception {
        if (!mHasFeature) {
            return;
        }
        executeDeviceTestClass(".CaCertManagementTest");
    }

    public void testApplicationRestrictions() throws Exception {
        if (!mHasFeature) {
            return;
        }

        installAppAsUser(DELEGATE_APP_APK, mUserId);
        installAppAsUser(APP_RESTRICTIONS_TARGET_APP_APK, mUserId);

        try {
            // Only the DPC can manage app restrictions by default.
            executeDeviceTestClass(".ApplicationRestrictionsTest");
            executeAppRestrictionsManagingPackageTest("testCannotAccessApis");

            // Letting the DELEGATE_APP_PKG manage app restrictions too.
            changeApplicationRestrictionsManagingPackage(DELEGATE_APP_PKG);
            executeAppRestrictionsManagingPackageTest("testCanAccessApis");
            runDeviceTestsAsUser(DELEGATE_APP_PKG, ".GeneralDelegateTest",
                    "testSettingAdminComponentNameThrowsException", mUserId);

            // The DPC should still be able to manage app restrictions normally.
            executeDeviceTestClass(".ApplicationRestrictionsTest");

            // The app shouldn't be able to manage app restrictions for other users.
            int parentUserId = getPrimaryUser();
            if (parentUserId != mUserId) {
                installAppAsUser(DELEGATE_APP_APK, parentUserId);
                installAppAsUser(APP_RESTRICTIONS_TARGET_APP_APK, parentUserId);
                runDeviceTestsAsUser(DELEGATE_APP_PKG, ".AppRestrictionsDelegateTest",
                        "testCannotAccessApis", parentUserId);
            }

            // Revoking the permission for DELEGAYE_APP_PKG to manage restrictions.
            changeApplicationRestrictionsManagingPackage(null);
            executeAppRestrictionsManagingPackageTest("testCannotAccessApis");

            // The DPC should still be able to manage app restrictions normally.
            executeDeviceTestClass(".ApplicationRestrictionsTest");
        } finally {
            changeApplicationRestrictionsManagingPackage(null);
        }
    }

    public void testDelegation() throws Exception {
        if (!mHasFeature) {
            return;
        }

        final String delegationTests[] = {
            ".AppRestrictionsDelegateTest",
            ".CertInstallDelegateTest",
            ".BlockUninstallDelegateTest",
            ".PermissionGrantDelegateTest",
            ".PackageAccessDelegateTest",
            ".EnableSystemAppDelegateTest"
        };

        // Set a device lockscreen password (precondition for installing private key pairs).
        changeUserCredential("1234", null, mPrimaryUserId);

        // Install relevant apps.
        installAppAsUser(DELEGATE_APP_APK, mUserId);
        installAppAsUser(TEST_APP_APK, mUserId);
        installAppAsUser(APP_RESTRICTIONS_TARGET_APP_APK, mUserId);

        try {
            // APIs are not accessible by default.
            executeDelegationTests(delegationTests, false /* negative result */);

            // Granting the appropriate delegation scopes makes APIs accessible.
            setDelegatedScopes(DELEGATE_APP_PKG, Arrays.asList(
                    DELEGATION_APP_RESTRICTIONS,
                    DELEGATION_CERT_INSTALL,
                    DELEGATION_BLOCK_UNINSTALL,
                    DELEGATION_PERMISSION_GRANT,
                    DELEGATION_PACKAGE_ACCESS,
                    DELEGATION_ENABLE_SYSTEM_APP));
            runDeviceTestsAsUser(DELEGATE_APP_PKG, ".GeneralDelegateTest", mUserId);
            executeDelegationTests(delegationTests, true /* positive result */);

            // APIs are not accessible after revoking delegations.
            setDelegatedScopes(DELEGATE_APP_PKG, null);
            executeDelegationTests(delegationTests, false /* negative result */);

            // Additional delegation tests.
            executeDeviceTestClass(".DelegationTest");

        } finally {
            // Clear lockscreen password previously set for installing private key pairs.
            changeUserCredential(null, "1234", mPrimaryUserId);
            // Remove any remaining delegations.
            setDelegatedScopes(DELEGATE_APP_PKG, null);
        }
    }

    public void testPermissionGrant() throws Exception {
        if (!mHasFeature) {
            return;
        }
        installAppPermissionAppAsUser();
        executeDeviceTestMethod(".PermissionsTest", "testPermissionGrantState");
    }

    /**
     * Require a device for tests that use the network stack. Headless Androids running in
     * data centres might need their network rules un-tampered-with in order to keep the ADB / VNC
     * connection alive.
     *
     * This is only a problem on device owner / profile owner running on USER_SYSTEM, because
     * network rules for this user will affect UID 0.
     */
    @RequiresDevice
    public void testAlwaysOnVpn() throws Exception {
        if (!mHasFeature) {
            return;
        }
        installAppAsUser(VPN_APP_APK, mUserId);
        executeDeviceTestClass(".AlwaysOnVpnTest");
    }

    @RequiresDevice
    public void testAlwaysOnVpnLockDown() throws Exception {
        if (!mHasFeature) {
            return;
        }

        installAppAsUser(VPN_APP_APK, mUserId);
        try {
            executeDeviceTestMethod(".AlwaysOnVpnMultiStageTest", "testAlwaysOnSet");
            forceStopPackageForUser(VPN_APP_PKG, mUserId);
            executeDeviceTestMethod(".AlwaysOnVpnMultiStageTest", "testNetworkBlocked");
        } finally {
            executeDeviceTestMethod(".AlwaysOnVpnMultiStageTest", "testCleanup");
        }
    }

    @RequiresDevice
    public void testAlwaysOnVpnAcrossReboot() throws Exception {
        // Note: Always-on VPN is supported on non-FBE devices as well, and the behavior should be
        // the same. However we're only testing the FBE case here as we need to set a device
        // password during the test. This would cause FDE devices (e.g. angler) to prompt for the
        // password during reboot, which we can't handle easily.
        if (!mHasFeature || !mSupportsFbe) {
            return;
        }

        // Set a password to encrypt the user
        final String testPassword = "1234";
        changeUserCredential(testPassword, null /*oldCredential*/, mUserId);

        try {
            installAppAsUser(VPN_APP_APK, mUserId);
            executeDeviceTestMethod(".AlwaysOnVpnMultiStageTest", "testAlwaysOnSet");
            rebootAndWaitUntilReady();
            verifyUserCredential(testPassword, mUserId);
            executeDeviceTestMethod(".AlwaysOnVpnMultiStageTest", "testAlwaysOnSetAfterReboot");
        } finally {
            changeUserCredential(null /*newCredential*/, testPassword, mUserId);
            executeDeviceTestMethod(".AlwaysOnVpnMultiStageTest", "testCleanup");
        }
    }

    @RequiresDevice
    public void testAlwaysOnVpnPackageUninstalled() throws Exception {
        if (!mHasFeature) {
            return;
        }

        installAppAsUser(VPN_APP_APK, mUserId);
        try {
            executeDeviceTestMethod(".AlwaysOnVpnMultiStageTest", "testAlwaysOnSet");
            getDevice().uninstallPackage(VPN_APP_PKG);
            executeDeviceTestMethod(".AlwaysOnVpnMultiStageTest", "testAlwaysOnVpnDisabled");
            executeDeviceTestMethod(".AlwaysOnVpnMultiStageTest", "testSetNonExistingPackage");
        } finally {
            executeDeviceTestMethod(".AlwaysOnVpnMultiStageTest", "testCleanup");
        }
    }

    @RequiresDevice
    public void testAlwaysOnVpnUnsupportedPackage() throws Exception {
        if (!mHasFeature) {
            return;
        }

        try {
            // Target SDK = 23: unsupported
            installAppAsUser(VPN_APP_API23_APK, mUserId);
            executeDeviceTestMethod(".AlwaysOnVpnUnsupportedTest", "testSetUnsupportedVpnAlwaysOn");

            // Target SDK = 24: supported
            installAppAsUser(VPN_APP_API24_APK, mUserId);
            executeDeviceTestMethod(".AlwaysOnVpnUnsupportedTest", "testSetSupportedVpnAlwaysOn");
            executeDeviceTestMethod(".AlwaysOnVpnUnsupportedTest", "testClearAlwaysOnVpn");

            // Explicit opt-out: unsupported
            installAppAsUser(VPN_APP_NOT_ALWAYS_ON_APK, mUserId);
            executeDeviceTestMethod(".AlwaysOnVpnUnsupportedTest", "testSetUnsupportedVpnAlwaysOn");
        } finally {
            executeDeviceTestMethod(".AlwaysOnVpnUnsupportedTest", "testClearAlwaysOnVpn");
        }
    }

    @RequiresDevice
    public void testAlwaysOnVpnUnsupportedPackageReplaced() throws Exception {
        if (!mHasFeature) {
            return;
        }

        try {
            // Target SDK = 24: supported
            executeDeviceTestMethod(".AlwaysOnVpnUnsupportedTest", "testAssertNoAlwaysOnVpn");
            installAppAsUser(VPN_APP_API24_APK, mUserId);
            executeDeviceTestMethod(".AlwaysOnVpnUnsupportedTest", "testSetSupportedVpnAlwaysOn");
            // Update the app to target higher API level, but with manifest opt-out
            installAppAsUser(VPN_APP_NOT_ALWAYS_ON_APK, mUserId);
            executeDeviceTestMethod(".AlwaysOnVpnUnsupportedTest", "testAssertNoAlwaysOnVpn");
        } finally {
            executeDeviceTestMethod(".AlwaysOnVpnUnsupportedTest", "testClearAlwaysOnVpn");
        }
    }

    public void testPermissionPolicy() throws Exception {
        if (!mHasFeature) {
            return;
        }
        installAppPermissionAppAsUser();
        executeDeviceTestMethod(".PermissionsTest", "testPermissionPolicy");
    }

    public void testPermissionMixedPolicies() throws Exception {
        if (!mHasFeature) {
            return;
        }
        installAppPermissionAppAsUser();
        executeDeviceTestMethod(".PermissionsTest", "testPermissionMixedPolicies");
    }

    // Test flakey; suppressed.
//    public void testPermissionPrompts() throws Exception {
//        if (!mHasFeature) {
//            return;
//        }
//        installAppPermissionAppAsUser();
//        executeDeviceTestMethod(".PermissionsTest", "testPermissionPrompts");
//    }

    public void testPermissionAppUpdate() throws Exception {
        if (!mHasFeature) {
            return;
        }
        installAppPermissionAppAsUser();
        executeDeviceTestMethod(".PermissionsTest", "testPermissionUpdate_setDeniedState");
        executeDeviceTestMethod(".PermissionsTest", "testPermissionUpdate_checkDenied");
        installAppPermissionAppAsUser();
        executeDeviceTestMethod(".PermissionsTest", "testPermissionUpdate_checkDenied");

        assertNull(getDevice().uninstallPackage(PERMISSIONS_APP_PKG));
        installAppPermissionAppAsUser();
        executeDeviceTestMethod(".PermissionsTest", "testPermissionUpdate_setGrantedState");
        executeDeviceTestMethod(".PermissionsTest", "testPermissionUpdate_checkGranted");
        installAppPermissionAppAsUser();
        executeDeviceTestMethod(".PermissionsTest", "testPermissionUpdate_checkGranted");

        assertNull(getDevice().uninstallPackage(PERMISSIONS_APP_PKG));
        installAppPermissionAppAsUser();
        executeDeviceTestMethod(".PermissionsTest", "testPermissionUpdate_setAutoDeniedPolicy");
        executeDeviceTestMethod(".PermissionsTest", "testPermissionUpdate_checkDenied");
        installAppPermissionAppAsUser();
        executeDeviceTestMethod(".PermissionsTest", "testPermissionUpdate_checkDenied");

        assertNull(getDevice().uninstallPackage(PERMISSIONS_APP_PKG));
        installAppPermissionAppAsUser();
        executeDeviceTestMethod(".PermissionsTest", "testPermissionUpdate_setAutoGrantedPolicy");
        executeDeviceTestMethod(".PermissionsTest", "testPermissionUpdate_checkGranted");
        installAppPermissionAppAsUser();
        executeDeviceTestMethod(".PermissionsTest", "testPermissionUpdate_checkGranted");
    }

    public void testPermissionGrantPreMApp() throws Exception {
        if (!mHasFeature) {
            return;
        }
        installAppAsUser(SIMPLE_PRE_M_APP_APK, mUserId);
        executeDeviceTestMethod(".PermissionsTest", "testPermissionGrantStatePreMApp");
    }

    public void testPersistentIntentResolving() throws Exception {
        if (!mHasFeature) {
            return;
        }
        executeDeviceTestClass(".PersistentIntentResolvingTest");
    }

    public void testScreenCaptureDisabled() throws Exception {
        if (!mHasFeature) {
            return;
        }
        // We need to ensure that the policy is deactivated for the device owner case, so making
        // sure the second test is run even if the first one fails
        try {
            setScreenCaptureDisabled(mUserId, true);
        } finally {
            setScreenCaptureDisabled(mUserId, false);
        }
    }

    public void testScreenCaptureDisabled_assist() throws Exception {
        if (!mHasFeature) {
            return;
        }
        try {
            // Install and enable assistant, notice that profile can't have assistant.
            installAppAsUser(ASSIST_APP_APK, mPrimaryUserId);
            setVoiceInteractionService(ASSIST_INTERACTION_SERVICE);
            setScreenCaptureDisabled_assist(mUserId, true /* disabled */);
        } finally {
            setScreenCaptureDisabled_assist(mUserId, false /* disabled */);
            clearVoiceInteractionService();
        }
    }

    public void testSupportMessage() throws Exception {
        if (!mHasFeature) {
            return;
        }
        executeDeviceTestClass(".SupportMessageTest");
    }

    public void testApplicationHidden() throws Exception {
        if (!mHasFeature) {
            return;
        }
        installAppPermissionAppAsUser();
        executeDeviceTestClass(".ApplicationHiddenTest");
    }

    public void testAccountManagement_deviceAndProfileOwnerAlwaysAllowed() throws Exception {
        if (!mHasFeature) {
            return;
        }

        installAppAsUser(ACCOUNT_MANAGEMENT_APK, mUserId);
        executeDeviceTestClass(".DpcAllowedAccountManagementTest");
    }

    public void testAccountManagement_userRestrictionAddAccount() throws Exception {
        if (!mHasFeature) {
            return;
        }

        installAppAsUser(ACCOUNT_MANAGEMENT_APK, mUserId);
        try {
            changeUserRestrictionOrFail(DISALLOW_MODIFY_ACCOUNTS, true, mUserId);
            executeAccountTest("testAddAccount_blocked");
        } finally {
            // Ensure we clear the user restriction
            changeUserRestrictionOrFail(DISALLOW_MODIFY_ACCOUNTS, false, mUserId);
        }
        executeAccountTest("testAddAccount_allowed");
    }

    public void testAccountManagement_userRestrictionRemoveAccount() throws Exception {
        if (!mHasFeature) {
            return;
        }

        installAppAsUser(ACCOUNT_MANAGEMENT_APK, mUserId);
        try {
            changeUserRestrictionOrFail(DISALLOW_MODIFY_ACCOUNTS, true, mUserId);
            executeAccountTest("testRemoveAccount_blocked");
        } finally {
            // Ensure we clear the user restriction
            changeUserRestrictionOrFail(DISALLOW_MODIFY_ACCOUNTS, false, mUserId);
        }
        executeAccountTest("testRemoveAccount_allowed");
    }

    public void testAccountManagement_disabledAddAccount() throws Exception {
        if (!mHasFeature) {
            return;
        }

        installAppAsUser(ACCOUNT_MANAGEMENT_APK, mUserId);
        try {
            changeAccountManagement(COMMAND_BLOCK_ACCOUNT_TYPE, ACCOUNT_TYPE, mUserId);
            executeAccountTest("testAddAccount_blocked");
        } finally {
            // Ensure we remove account management policies
            changeAccountManagement(COMMAND_UNBLOCK_ACCOUNT_TYPE, ACCOUNT_TYPE, mUserId);
        }
        executeAccountTest("testAddAccount_allowed");
    }

    public void testAccountManagement_disabledRemoveAccount() throws Exception {
        if (!mHasFeature) {
            return;
        }

        installAppAsUser(ACCOUNT_MANAGEMENT_APK, mUserId);
        try {
            changeAccountManagement(COMMAND_BLOCK_ACCOUNT_TYPE, ACCOUNT_TYPE, mUserId);
            executeAccountTest("testRemoveAccount_blocked");
        } finally {
            // Ensure we remove account management policies
            changeAccountManagement(COMMAND_UNBLOCK_ACCOUNT_TYPE, ACCOUNT_TYPE, mUserId);
        }
        executeAccountTest("testRemoveAccount_allowed");
    }

    public void testDelegatedCertInstaller() throws Exception {
        if (!mHasFeature) {
            return;
        }

        installAppAsUser(CERT_INSTALLER_APK, mUserId);

        boolean isManagedProfile = (mPrimaryUserId != mUserId);

        try {
            // Set a non-empty device lockscreen password, which is a precondition for installing
            // private key pairs.
            changeUserCredential("1234", null, mUserId);

            runDeviceTestsAsUser(DEVICE_ADMIN_PKG, ".DelegatedCertInstallerTest", mUserId);
        } finally {
            if (!isManagedProfile) {
                // Skip managed profile as dpm doesn't allow clear password
                changeUserCredential(null, "1234", mUserId);
            }
        }
    }

    // Sets restrictions and launches non-admin app, that tries to set wallpaper.
    // Non-admin apps must not violate any user restriction.
    public void testSetWallpaper_disallowed() throws Exception {
        // UserManager.DISALLOW_SET_WALLPAPER
        final String DISALLOW_SET_WALLPAPER = "no_set_wallpaper";
        if (!mHasFeature) {
            return;
        }

        installAppAsUser(CUSTOMIZATION_APP_APK, mUserId);
        try {
            changeUserRestrictionOrFail(DISALLOW_SET_WALLPAPER, true, mUserId);
            runDeviceTestsAsUser(CUSTOMIZATION_APP_PKG, ".CustomizationTest",
                "testSetWallpaper_disallowed", mUserId);
        } finally {
            changeUserRestrictionOrFail(DISALLOW_SET_WALLPAPER, false, mUserId);
        }
    }

    // Runs test with admin privileges. The test methods set all the tested restrictions
    // inside. But these restrictions must have no effect on the device/profile owner behavior.
    public void testDisallowSetWallpaper_allowed() throws Exception {
        if (!mHasFeature) {
            return;
        }
        executeDeviceTestMethod(".CustomizationRestrictionsTest",
                "testDisallowSetWallpaper_allowed");
    }

    public void testDisallowSetUserIcon_allowed() throws Exception {
        if (!mHasFeature) {
            return;
        }
        executeDeviceTestMethod(".CustomizationRestrictionsTest",
                "testDisallowSetUserIcon_allowed");
    }

    public void testDisallowAutofill_allowed() throws Exception {
        if (!mHasFeature) {
            return;
        }
        boolean mHasAutofill = hasDeviceFeature("android.software.autofill");
        if (!mHasAutofill) {
          return;
        }
        installAppAsUser(AUTOFILL_APP_APK, mUserId);

        executeDeviceTestMethod(".AutofillRestrictionsTest",
                "testDisallowAutofill_allowed");
    }

    public void testPackageInstallUserRestrictions() throws Exception {
        if (!mHasFeature) {
            return;
        }
        boolean mIsWatch = hasDeviceFeature("android.hardware.type.watch");
        if (mIsWatch) {
            return;
        }
        // UserManager.DISALLOW_INSTALL_UNKNOWN_SOURCES
        final String DISALLOW_INSTALL_UNKNOWN_SOURCES = "no_install_unknown_sources";
        final String PACKAGE_VERIFIER_USER_CONSENT_SETTING = "package_verifier_user_consent";
        final String PACKAGE_VERIFIER_ENABLE_SETTING = "package_verifier_enable";
        final String SECURE_SETTING_CATEGORY = "secure";
        final String GLOBAL_SETTING_CATEGORY = "global";
        CompatibilityBuildHelper buildHelper = new CompatibilityBuildHelper(mCtsBuild);
        final File apk = buildHelper.getTestFile(TEST_APP_APK);
        String packageVerifierEnableSetting = null;
        String packageVerifierUserConsentSetting = null;
        try {
            // Install the test and prepare the test apk.
            installAppAsUser(PACKAGE_INSTALLER_APK, mUserId);
            assertTrue(getDevice().pushFile(apk, TEST_APP_LOCATION + apk.getName()));
            setInstallPackageAppOps(PACKAGE_INSTALLER_PKG, true, mUserId);

            // Add restrictions and test if we can install the apk.
            getDevice().uninstallPackage(TEST_APP_PKG);
            changeUserRestrictionOrFail(DISALLOW_INSTALL_UNKNOWN_SOURCES, true, mUserId);
            runDeviceTestsAsUser(PACKAGE_INSTALLER_PKG, ".ManualPackageInstallTest",
                    "testManualInstallBlocked", mUserId);

            // Clear restrictions and test if we can install the apk.
            changeUserRestrictionOrFail(DISALLOW_INSTALL_UNKNOWN_SOURCES, false, mUserId);

            // Disable verifier.
            packageVerifierUserConsentSetting = getSettings(SECURE_SETTING_CATEGORY,
                    PACKAGE_VERIFIER_USER_CONSENT_SETTING, mUserId);
            packageVerifierEnableSetting = getSettings(GLOBAL_SETTING_CATEGORY,
                    PACKAGE_VERIFIER_ENABLE_SETTING, mUserId);

            putSettings(SECURE_SETTING_CATEGORY, PACKAGE_VERIFIER_USER_CONSENT_SETTING, "-1",
                    mUserId);
            putSettings(GLOBAL_SETTING_CATEGORY, PACKAGE_VERIFIER_ENABLE_SETTING, "0", mUserId);
            // Skip verifying above setting values as some of them may be overrided.
            runDeviceTestsAsUser(PACKAGE_INSTALLER_PKG, ".ManualPackageInstallTest",
                    "testManualInstallSucceeded", mUserId);
        } finally {
            setInstallPackageAppOps(PACKAGE_INSTALLER_PKG, false, mUserId);
            String command = "rm " + TEST_APP_LOCATION + apk.getName();
            getDevice().executeShellCommand(command);
            getDevice().uninstallPackage(TEST_APP_PKG);
            getDevice().uninstallPackage(PACKAGE_INSTALLER_PKG);
            if (packageVerifierEnableSetting != null) {
                putSettings(GLOBAL_SETTING_CATEGORY, PACKAGE_VERIFIER_ENABLE_SETTING,
                        packageVerifierEnableSetting, mUserId);
            }
            if (packageVerifierUserConsentSetting != null) {
                putSettings(SECURE_SETTING_CATEGORY, PACKAGE_VERIFIER_USER_CONSENT_SETTING,
                        packageVerifierUserConsentSetting, mUserId);
            }
        }
    }

    public void testAudioRestriction() throws Exception {
        if (!mHasFeature) {
            return;
        }
        // This package may need to toggle zen mode for this test, so allow it to do so.
        allowNotificationPolicyAccess(DEVICE_ADMIN_PKG, mUserId);
        try {
            executeDeviceTestClass(".AudioRestrictionTest");
        } finally {
            disallowNotificationPolicyAccess(DEVICE_ADMIN_PKG, mUserId);
        }
    }

    public void testSuspendPackage() throws Exception {
        if (!mHasFeature) {
            return;
        }
        installAppAsUser(INTENT_SENDER_APK, mUserId);
        installAppAsUser(INTENT_RECEIVER_APK, mUserId);
        // Suspend a testing package.
        executeDeviceTestMethod(".SuspendPackageTest", "testSetPackagesSuspended");
        // Verify that the package is suspended.
        executeSuspendPackageTestMethod("testPackageSuspended");
        // Undo the suspend.
        executeDeviceTestMethod(".SuspendPackageTest", "testSetPackagesNotSuspended");
        // Verify that the package is not suspended.
        executeSuspendPackageTestMethod("testPackageNotSuspended");
        // Verify we cannot suspend not suspendable packages.
        executeDeviceTestMethod(".SuspendPackageTest", "testSuspendNotSuspendablePackages");
    }

    public void testTrustAgentInfo() throws Exception {
        if (!mHasFeature) {
            return;
        }
        executeDeviceTestClass(".TrustAgentInfoTest");
    }

    public void testCannotRemoveUserIfRestrictionSet() throws Exception {
        // Outside of the primary user, setting DISALLOW_REMOVE_USER would not work.
        if (!mHasFeature || !canCreateAdditionalUsers(1) || mUserId != getPrimaryUser()) {
            return;
        }
        final int userId = createUser();
        try {
            changeUserRestrictionOrFail(DISALLOW_REMOVE_USER, true, mUserId);
            assertFalse(getDevice().removeUser(userId));
        } finally {
            changeUserRestrictionOrFail(DISALLOW_REMOVE_USER, false, mUserId);
            assertTrue(getDevice().removeUser(userId));
        }
    }

    public void testCannotEnableOrDisableDeviceOwnerOrProfileOwner() throws Exception {
        if (!mHasFeature) {
            return;
        }
        // Try to disable a component in device owner/ profile owner.
        String result = disableComponentOrPackage(
                mUserId, DEVICE_ADMIN_PKG + "/.SetPolicyActivity");
        assertTrue("Should throw SecurityException",
                result.contains("java.lang.SecurityException"));
        // Try to disable the device owner/ profile owner package.
        result = disableComponentOrPackage(mUserId, DEVICE_ADMIN_PKG);
        assertTrue("Should throw SecurityException",
                result.contains("java.lang.SecurityException"));
        // Try to enable a component in device owner/ profile owner.
        result = enableComponentOrPackage(
                mUserId, DEVICE_ADMIN_PKG + "/.SetPolicyActivity");
        assertTrue("Should throw SecurityException",
                result.contains("java.lang.SecurityException"));
        // Try to enable the device owner/ profile owner package.
        result = enableComponentOrPackage(mUserId, DEVICE_ADMIN_PKG);
        assertTrue("Should throw SecurityException",
                result.contains("java.lang.SecurityException"));

    }

    public void testRequiredStrongAuthTimeout() throws Exception {
        if (!mHasFeature) {
            return;
        }
        executeDeviceTestClass(".RequiredStrongAuthTimeoutTest");
    }

    public void testCreateAdminSupportIntent() throws Exception {
        if (!mHasFeature) {
            return;
        }
        executeDeviceTestClass(".PolicyTransparencyTest");
    }

    public void testResetPasswordWithToken() throws Exception {
        if (!mHasFeature) {
            return;
        }
        // If ResetPasswordWithTokenTest for managed profile is executed before device owner and
        // primary user profile owner tests, password reset token would have been disabled for
        // the primary user, so executing ResetPasswordWithTokenTest on user 0 would fail. We allow
        // this and do not fail the test in this case.
        // This is the default test for MixedDeviceOwnerTest and MixedProfileOwnerTest,
        // MixedManagedProfileOwnerTest overrides this method to execute the same test more strictly
        // without allowing failures.
        executeResetPasswordWithTokenTests(true);
    }

    public void testPasswordSufficientInitially() throws Exception {
        if (!mHasFeature) {
            return;
        }
        executeDeviceTestClass(".PasswordSufficientInitiallyTest");
    }

    protected void executeResetPasswordWithTokenTests(Boolean allowFailures) throws Exception {
        runDeviceTestsAsUser(DEVICE_ADMIN_PKG, ".ResetPasswordWithTokenTest", null, mUserId,
                Collections.singletonMap(ARG_ALLOW_FAILURE, Boolean.toString(allowFailures)));
    }

    protected void executeDeviceTestClass(String className) throws Exception {
        runDeviceTestsAsUser(DEVICE_ADMIN_PKG, className, mUserId);
    }

    protected void executeDeviceTestMethod(String className, String testName) throws Exception {
        runDeviceTestsAsUser(DEVICE_ADMIN_PKG, className, testName, mUserId);
    }

    private void installAppPermissionAppAsUser()
            throws FileNotFoundException, DeviceNotAvailableException {
        installAppAsUser(PERMISSIONS_APP_APK, false, mUserId);
    }

    private void executeSuspendPackageTestMethod(String testName) throws Exception {
        runDeviceTestsAsUser(INTENT_SENDER_PKG, ".SuspendPackageTest",
                testName, mUserId);
    }

    private void executeAccountTest(String testName) throws DeviceNotAvailableException {
        runDeviceTestsAsUser(ACCOUNT_MANAGEMENT_PKG, ".AccountManagementTest",
                testName, mUserId);
        // Send a home intent to dismiss an error dialog.
        String command = "am start -a android.intent.action.MAIN"
                + " -c android.intent.category.HOME";
        CLog.i("Output for command " + command + ": " + getDevice().executeShellCommand(command));
    }

    private void executeAppRestrictionsManagingPackageTest(String testName) throws Exception {
        runDeviceTestsAsUser(DELEGATE_APP_PKG,
                ".AppRestrictionsDelegateTest", testName, mUserId);
    }

    private void executeDelegationTests(String[] delegationTests, boolean positive)
            throws Exception {
        for (String delegationTestClass : delegationTests) {
            runDeviceTestsAsUser(DELEGATE_APP_PKG, delegationTestClass,
                positive ? "testCanAccessApis" : "testCannotAccessApis", mUserId);
        }
    }

    private void changeUserRestrictionOrFail(String key, boolean value, int userId)
            throws DeviceNotAvailableException {
        changeUserRestrictionOrFail(key, value, userId, DEVICE_ADMIN_PKG);
    }

    private void changeAccountManagement(String command, String accountType, int userId)
            throws DeviceNotAvailableException {
        changePolicyOrFail(command, "--es extra-account-type " + accountType, userId);
    }

    private void changeApplicationRestrictionsManagingPackage(String packageName)
            throws DeviceNotAvailableException {
        String packageNameExtra = (packageName != null)
                ? "--es extra-package-name " + packageName : "";
        changePolicyOrFail("set-app-restrictions-manager", packageNameExtra, mUserId);
    }

    private void setDelegatedScopes(String packageName, List<String> scopes)
            throws DeviceNotAvailableException {
        final String packageNameExtra = "--es extra-package-name " + packageName;
        String scopesExtra = "";
        if (scopes != null && scopes.size() > 0) {
            scopesExtra = "--esa extra-scopes-list " + scopes.get(0);
            for (int i = 1; i < scopes.size(); ++i) {
                scopesExtra += "," + scopes.get(i);
            }
        }
        final String extras = packageNameExtra + " " + scopesExtra;

        changePolicyOrFail("set-delegated-scopes", extras, mUserId);
    }

    private void setInstallPackageAppOps(String packageName, boolean allowed, int userId)
            throws DeviceNotAvailableException {
        String command = "appops set --user " + userId + " " + packageName + " " +
                "REQUEST_INSTALL_PACKAGES "
                + (allowed ? "allow" : "default");
        CLog.d("Output for command " + command + ": " + getDevice().executeShellCommand(command));
    }

    private void changePolicyOrFail(String command, String extras, int userId)
            throws DeviceNotAvailableException {
        changePolicyOrFail(command, extras, userId, DEVICE_ADMIN_PKG);
    }

    /**
     * Start SimpleActivity synchronously in a particular user.
     */
    protected void startSimpleActivityAsUser(int userId) throws Exception {
        installAppAsUser(TEST_APP_APK, userId);
        wakeupAndDismissKeyguard();
        String command = "am start -W --user " + userId + " " + TEST_APP_PKG + "/"
                + TEST_APP_PKG + ".SimpleActivity";
        getDevice().executeShellCommand(command);
    }

    protected void setScreenCaptureDisabled(int userId, boolean disabled) throws Exception {
        String testMethodName = disabled
                ? "testSetScreenCaptureDisabled_true"
                : "testSetScreenCaptureDisabled_false";
        executeDeviceTestMethod(".ScreenCaptureDisabledTest", testMethodName);
        startSimpleActivityAsUser(userId);
        testMethodName = disabled
                ? "testScreenCaptureImpossible"
                : "testScreenCapturePossible";
        executeDeviceTestMethod(".ScreenCaptureDisabledTest", testMethodName);
    }

    protected void setScreenCaptureDisabled_assist(int userId, boolean disabled) throws Exception {
        // Set the policy.
        String testMethodName = disabled
                ? "testSetScreenCaptureDisabled_true"
                : "testSetScreenCaptureDisabled_false";
        executeDeviceTestMethod(".ScreenCaptureDisabledTest", testMethodName);
        // Make sure the foreground activity is from the target user.
        startSimpleActivityAsUser(userId);
        // Check whether the VoiceInteractionService can retrieve the screenshot.
        testMethodName = disabled
                ? "testScreenCaptureImpossible_assist"
                : "testScreenCapturePossible_assist";
        installAppAsUser(DEVICE_ADMIN_APK, mPrimaryUserId);
        runDeviceTestsAsUser(
                DEVICE_ADMIN_PKG,
                ".AssistScreenCaptureDisabledTest",
                testMethodName,
                mPrimaryUserId);
    }

    /**
     * Allows packageName to manage notification policy configuration, which
     * includes toggling zen mode.
     */
    private void allowNotificationPolicyAccess(String packageName, int userId)
            throws DeviceNotAvailableException {
        List<String> enabledPackages = getEnabledNotificationPolicyPackages(userId);
        if (!enabledPackages.contains(packageName)) {
            enabledPackages.add(packageName);
            setEnabledNotificationPolicyPackages(enabledPackages, userId);
        }
    }

    /**
     * Disallows packageName to manage notification policy configuration, which
     * includes toggling zen mode.
     */
    private void disallowNotificationPolicyAccess(String packageName, int userId)
            throws DeviceNotAvailableException {
        List<String> enabledPackages = getEnabledNotificationPolicyPackages(userId);
        if (enabledPackages.contains(packageName)) {
            enabledPackages.remove(packageName);
            setEnabledNotificationPolicyPackages(enabledPackages, userId);
        }
    }

    private void setEnabledNotificationPolicyPackages(List<String> packages, int userId)
            throws DeviceNotAvailableException {
        getDevice().setSetting(userId, "secure", ENABLED_NOTIFICATION_POLICY_ACCESS_PACKAGES,
                String.join(":", packages));
    }

    private List<String> getEnabledNotificationPolicyPackages(int userId)
            throws DeviceNotAvailableException {
        String settingValue = getDevice().getSetting(userId, "secure",
                ENABLED_NOTIFICATION_POLICY_ACCESS_PACKAGES);
        if (settingValue == null) {
            return new ArrayList<String>();
        }
        return new ArrayList<String>(Arrays.asList(settingValue.split(":|\n")));
    }

    protected void setVoiceInteractionService(String componentName)
            throws DeviceNotAvailableException {
        getDevice().setSetting(
                mPrimaryUserId, "secure", "voice_interaction_service", componentName);
        getDevice().setSetting(mPrimaryUserId, "secure", "assist_structure_enabled", "1");
        getDevice().setSetting(mPrimaryUserId, "secure", "assist_screenshot_enabled", "1");
    }

    protected void clearVoiceInteractionService() throws DeviceNotAvailableException {
        getDevice().executeShellCommand("settings delete secure voice_interaction_service");
    }
}
