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

import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * Set of tests for Device Owner use cases.
 */
public class DeviceOwnerTest extends BaseDevicePolicyTest {

    private static final String DEVICE_OWNER_PKG = "com.android.cts.deviceowner";
    private static final String DEVICE_OWNER_APK = "CtsDeviceOwnerApp.apk";

    private static final String MANAGED_PROFILE_PKG = "com.android.cts.managedprofile";
    private static final String MANAGED_PROFILE_APK = "CtsManagedProfileApp.apk";
    private static final String MANAGED_PROFILE_ADMIN =
            MANAGED_PROFILE_PKG + ".BaseManagedProfileTest$BasicAdminReceiver";

    private static final String FEATURE_BACKUP = "android.software.backup";

    private static final String INTENT_RECEIVER_PKG = "com.android.cts.intent.receiver";
    private static final String INTENT_RECEIVER_APK = "CtsIntentReceiverApp.apk";

    private static final String WIFI_CONFIG_CREATOR_PKG =
            "com.android.cts.deviceowner.wificonfigcreator";
    private static final String WIFI_CONFIG_CREATOR_APK = "CtsWifiConfigCreator.apk";

    private static final String ADMIN_RECEIVER_TEST_CLASS =
            DEVICE_OWNER_PKG + ".BaseDeviceOwnerTest$BasicAdminReceiver";
    private static final String DEVICE_OWNER_COMPONENT = DEVICE_OWNER_PKG + "/"
            + ADMIN_RECEIVER_TEST_CLASS;

    /** The ephemeral users are implemented and supported on the device. */
    private boolean mHasEphemeralUserFeature;

    /**
     * Ephemeral users are implemented, but unsupported on the device (because of missing split
     * system user).
     */
    private boolean mHasDisabledEphemeralUserFeature;

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        if (mHasFeature) {
            installAppAsUser(DEVICE_OWNER_APK, mPrimaryUserId);
            if (!setDeviceOwner(DEVICE_OWNER_COMPONENT, mPrimaryUserId,
                    /*expectFailure*/ false)) {
                removeAdmin(DEVICE_OWNER_COMPONENT, mPrimaryUserId);
                getDevice().uninstallPackage(DEVICE_OWNER_PKG);
                fail("Failed to set device owner");
            }
        }
        mHasEphemeralUserFeature = mHasFeature && canCreateAdditionalUsers(1) && hasUserSplit();
        mHasDisabledEphemeralUserFeature =
                mHasFeature && canCreateAdditionalUsers(1) && !hasUserSplit();
    }

    @Override
    protected void tearDown() throws Exception {
        if (mHasFeature) {
            assertTrue("Failed to remove device owner.",
                    removeAdmin(DEVICE_OWNER_COMPONENT, mPrimaryUserId));
            getDevice().uninstallPackage(DEVICE_OWNER_PKG);
            switchUser(USER_SYSTEM);
            removeTestUsers();
        }

        super.tearDown();
    }

    public void testDeviceOwnerSetup() throws Exception {
        executeDeviceOwnerTest("DeviceOwnerSetupTest");
    }

    public void testKeyManagement() throws Exception {
        try {
            changeUserCredential("1234", null, mPrimaryUserId);

            executeDeviceOwnerTest("KeyManagementTest");
        } finally {
            changeUserCredential(null, "1234", mPrimaryUserId);
        }
    }

    public void testLockScreenInfo() throws Exception {
        executeDeviceOwnerTest("LockScreenInfoTest");
    }

    public void testWifi() throws Exception {
        if (!hasDeviceFeature("android.hardware.wifi")) {
            return;
        }
        executeDeviceOwnerTest("WifiTest");
    }

    public void testRemoteBugreportWithTwoUsers() throws Exception {
        if (!mHasFeature || !canCreateAdditionalUsers(1)) {
            return;
        }
        final int userId = createUser();
        try {
            executeDeviceTestMethod(".RemoteBugreportTest",
                    "testRequestBugreportThrowsSecurityException");
        } finally {
            removeUser(userId);
        }
    }

    /** Tries to toggle the force-ephemeral-users on and checks it was really set. */
    public void testSetForceEphemeralUsers() throws Exception {
        if (!mHasEphemeralUserFeature) {
            return;
        }
        // Set force-ephemeral-users policy and verify it was set.
        executeDeviceTestMethod(".ForceEphemeralUsersTest", "testSetForceEphemeralUsers");
    }

    /**
     * Setting force-ephemeral-users policy to true without a split system user should fail.
     */
    public void testSetForceEphemeralUsersFailsWithoutSplitSystemUser() throws Exception {
        if (mHasDisabledEphemeralUserFeature) {
            executeDeviceTestMethod(".ForceEphemeralUsersTest", "testSetForceEphemeralUsersFails");
        }
    }

    /**
     * All users (except of the system user) must be removed after toggling the
     * force-ephemeral-users policy to true.
     *
     * <p>If the current user is the system user, the other users are removed straight away.
     */
    public void testRemoveUsersOnSetForceEphemeralUsers() throws Exception {
        if (!mHasEphemeralUserFeature) {
            return;
        }

        // Create a user.
        int userId = createUser();
        assertTrue("User must have been created", listUsers().contains(userId));

        // Set force-ephemeral-users policy and verify it was set.
        executeDeviceTestMethod(".ForceEphemeralUsersTest", "testSetForceEphemeralUsers");

        // Users have to be removed when force-ephemeral-users is toggled on.
        assertFalse("User must have been removed", listUsers().contains(userId));
    }

    /**
     * All users (except of the system user) must be removed after toggling the
     * force-ephemeral-users policy to true.
     *
     * <p>If the current user is not the system user, switching to the system user should happen
     * before all other users are removed.
     */
    public void testRemoveUsersOnSetForceEphemeralUsersWithUserSwitch() throws Exception {
        if (!mHasEphemeralUserFeature) {
            return;
        }

        // Create a user.
        int userId = createUser();
        assertTrue("User must have been created", listUsers().contains(userId));

        // Switch to the new (non-system) user.
        switchUser(userId);

        // Set force-ephemeral-users policy and verify it was set.
        executeDeviceTestMethod(".ForceEphemeralUsersTest", "testSetForceEphemeralUsers");

        // Make sure the user has been removed. As it is not a synchronous operation - switching to
        // the system user must happen first - give the system a little bit of time for finishing
        // it.
        final int sleepMs = 500;
        final int maxSleepMs = 10000;
        for (int totalSleptMs = 0; totalSleptMs < maxSleepMs; totalSleptMs += sleepMs) {
            // Wait a little while for the user's removal.
            Thread.sleep(sleepMs);

            if (!listUsers().contains(userId)) {
                // Success - the user has been removed.
                return;
            }
        }

        // The user hasn't been removed within the given time.
        fail("User must have been removed");
    }

    /** The users created after setting force-ephemeral-users policy to true must be ephemeral. */
    public void testCreateUserAfterSetForceEphemeralUsers() throws Exception {
        if (!mHasEphemeralUserFeature) {
            return;
        }

        // Set force-ephemeral-users policy and verify it was set.
        executeDeviceTestMethod(".ForceEphemeralUsersTest", "testSetForceEphemeralUsers");

        int userId = createUser();
        assertTrue("User must be ephemeral", 0 != (getUserFlags(userId) & FLAG_EPHEMERAL));
    }

    /**
     * Test creating an epehemeral user using the DevicePolicyManager's createAndManageUser method.
     */
    public void testCreateAndManageEphemeralUser() throws Exception {
        if (!mHasEphemeralUserFeature) {
            return;
        }

        executeDeviceTestMethod(".CreateAndManageUserTest", "testCreateAndManageEphemeralUser");

        List<Integer> newUsers = getUsersCreatedByTests();
        assertEquals(1, newUsers.size());
        int newUserId = newUsers.get(0);

        // Get the flags of the new user and check the user is ephemeral.
        int flags = getUserFlags(newUserId);
        assertEquals("Ephemeral flag must be set", FLAG_EPHEMERAL, flags & FLAG_EPHEMERAL);
    }

    /**
     * Test that creating an epehemeral user using the DevicePolicyManager's createAndManageUser
     * method fails on systems without the split system user.
     */
    public void testCreateAndManageEphemeralUserFailsWithoutSplitSystemUser() throws Exception {
        if (mHasDisabledEphemeralUserFeature) {
            executeDeviceTestMethod(
                    ".CreateAndManageUserTest", "testCreateAndManageEphemeralUserFails");
        }
    }

// Disabled due to b/29072728
//    public void testCreateAndManageUser_SkipSetupWizard() throws Exception {
//        if (mHasCreateAndManageUserFeature) {
//            executeDeviceTestMethod(".CreateAndManageUserTest",
//                "testCreateAndManageUser_SkipSetupWizard");
//        }
//    }
//
//    public void testCreateAndManageUser_DontSkipSetupWizard() throws Exception {
//        if (mHasCreateAndManageUserFeature) {
//            executeDeviceTestMethod(".CreateAndManageUserTest",
//                "testCreateAndManageUser_DontSkipSetupWizard");
//        }
//    }

    public void testCreateAndManageUser_AddRestrictionSet() throws Exception {
        if (mHasFeature && canCreateAdditionalUsers(1)) {
            executeDeviceTestMethod(".CreateAndManageUserTest",
                "testCreateAndManageUser_AddRestrictionSet");
        }
    }

    public void testCreateAndManageUser_RemoveRestrictionSet() throws Exception {
        if (mHasFeature && canCreateAdditionalUsers(1)) {
            executeDeviceTestMethod(".CreateAndManageUserTest",
                "testCreateAndManageUser_RemoveRestrictionSet");
        }
    }

    public void testUserAddedOrRemovedBroadcasts() throws Exception {
        if (mHasFeature && canCreateAdditionalUsers(1)) {
            executeDeviceTestMethod(".CreateAndManageUserTest",
                "testUserAddedOrRemovedBroadcasts");
        }
    }

    public void testSecurityLoggingWithTwoUsers() throws Exception {
        if (!mHasFeature || !canCreateAdditionalUsers(1)) {
            return;
        }

        final int userId = createUser();
        try {
            // The feature can be enabled, but in a "paused" state. Attempting to retrieve logs
            // should throw security exception.
            executeDeviceTestMethod(".SecurityLoggingTest", "testEnablingSecurityLogging");
            executeDeviceTestMethod(".SecurityLoggingTest",
                    "testRetrievingSecurityLogsThrowsSecurityException");
            executeDeviceTestMethod(".SecurityLoggingTest",
                    "testRetrievingPreviousSecurityLogsThrowsSecurityException");
        } finally {
            removeUser(userId);
            executeDeviceTestMethod(".SecurityLoggingTest", "testDisablingSecurityLogging");
        }
    }

    public void testSecurityLoggingWithSingleUser() throws Exception {
        if (!mHasFeature) {
            return;
        }
        executeDeviceTestMethod(".SecurityLoggingTest",
                "testRetrievingSecurityLogsNotPossibleImmediatelyAfterPreviousSuccessfulRetrieval");
        try {
            executeDeviceTestMethod(".SecurityLoggingTest", "testEnablingSecurityLogging");
            rebootAndWaitUntilReady();
            // Sleep for ~1 minute so that SecurityLogMonitor fetches the security events. This is
            // an implementation detail we shouldn't rely on but there is no other way to do it
            // currently.
            Thread.sleep(TimeUnit.SECONDS.toMillis(70));
            executeDeviceTestMethod(".SecurityLoggingTest", "testGetSecurityLogs");
        } finally {
            // Always attempt to disable security logging to bring the device to initial state.
            executeDeviceTestMethod(".SecurityLoggingTest", "testDisablingSecurityLogging");
        }
    }

    public void testNetworkLoggingWithTwoUsers() throws Exception {
        if (!mHasFeature || !canCreateAdditionalUsers(1)) {
            return;
        }

        final int userId = createUser();
        try {
            // The feature can be enabled, but in a "paused" state. Attempting to retrieve logs
            // should throw security exception.
            executeDeviceTestMethod(".NetworkLoggingTest",
                    "testRetrievingNetworkLogsThrowsSecurityException");
        } finally {
            removeUser(userId);
        }
    }

    public void testNetworkLoggingWithSingleUser() throws Exception {
        if (!mHasFeature) {
            return;
        }

        executeDeviceTestMethod(".NetworkLoggingTest", "testProvidingWrongBatchTokenReturnsNull");
        executeDeviceTestMethod(".NetworkLoggingTest", "testNetworkLoggingAndRetrieval");
    }

    public void testSetAffiliationId_IllegalArgumentException() throws Exception {
        if (!mHasFeature) {
            return;
        }
        executeDeviceTestMethod(".AffiliationTest", "testSetAffiliationId_null");
        executeDeviceTestMethod(".AffiliationTest", "testSetAffiliationId_containsEmptyString");
    }

    public void testLockTask_deviceOwnerUser() throws Exception {
        if (!mHasFeature) {
            return;
        }
        try {
            installAppAsUser(INTENT_RECEIVER_APK, mPrimaryUserId);
            executeDeviceOwnerTest("LockTaskTest");
        } catch (AssertionError ex) {
            // STOPSHIP(b/32771855), remove this once we fixed the bug.
            executeShellCommand("dumpsys activity activities");
            executeShellCommand("dumpsys window -a");
            executeShellCommand("dumpsys activity service com.android.systemui");
            throw ex;
        } finally {
            getDevice().uninstallPackage(INTENT_RECEIVER_PKG);
        }
    }

    public void testLockTaskAfterReboot_deviceOwnerUser() throws Exception {
        if (!mHasFeature) {
            return;
        }

        try {
            // Just start kiosk mode
            runDeviceTestsAsUser(DEVICE_OWNER_PKG, ".LockTaskHostDrivenTest", "startLockTask",
                    mPrimaryUserId);

            // Reboot while in kiosk mode and then unlock the device
            getDevice().reboot();

            // Check that kiosk mode is working and can't be interrupted
            runDeviceTestsAsUser(DEVICE_OWNER_PKG, ".LockTaskHostDrivenTest",
                    "testLockTaskIsActiveAndCantBeInterrupted", mPrimaryUserId);

            // Try to open settings via adb
            executeShellCommand("am start -a android.settings.SETTINGS");

            // Check again
            runDeviceTestsAsUser(DEVICE_OWNER_PKG, ".LockTaskHostDrivenTest",
                    "testLockTaskIsActiveAndCantBeInterrupted", mPrimaryUserId);
        } finally {
            runDeviceTestsAsUser(DEVICE_OWNER_PKG, ".LockTaskHostDrivenTest",
                    "clearDefaultHomeIntentReceiver", mPrimaryUserId);
        }
    }

    public void testLockTask_unaffiliatedUser() throws Exception {
        if (!mHasFeature || !canCreateAdditionalUsers(1)) {
            return;
        }

        final int userId = createUser();
        installAppAsUser(DEVICE_OWNER_APK, userId);
        setProfileOwnerOrFail(DEVICE_OWNER_COMPONENT, userId);

        runDeviceTestsAsUser(
                DEVICE_OWNER_PKG,
                ".AffiliationTest",
                "testLockTaskMethodsThrowExceptionIfUnaffiliated",
                userId);

        runDeviceTestsAsUser(
                DEVICE_OWNER_PKG, ".AffiliationTest", "testSetAffiliationId1", mPrimaryUserId);
        runDeviceTestsAsUser(
                DEVICE_OWNER_PKG, ".AffiliationTest", "testSetAffiliationId1", userId);
        runDeviceTestsAsUser(
                DEVICE_OWNER_PKG,
                ".AffiliationTest",
                "testSetLockTaskPackagesClearedIfUserBecomesUnaffiliated",
                userId);
    }

    public void testLockTask_affiliatedSecondaryUser() throws Exception {
        if (!mHasFeature || !canCreateAdditionalUsers(1)) {
            return;
        }

        final int userId = createUser();
        installAppAsUser(INTENT_RECEIVER_APK, userId);
        installAppAsUser(DEVICE_OWNER_APK, userId);
        setProfileOwnerOrFail(DEVICE_OWNER_COMPONENT, userId);

        switchUser(userId);
        wakeupAndDismissKeyguard();

        // Setting the same affiliation ids on both users and running the lock task tests.
        runDeviceTestsAsUser(
                DEVICE_OWNER_PKG, ".AffiliationTest", "testSetAffiliationId1", mPrimaryUserId);
        runDeviceTestsAsUser(
                DEVICE_OWNER_PKG, ".AffiliationTest", "testSetAffiliationId1", userId);
        runDeviceTestsAsUser(DEVICE_OWNER_PKG, ".LockTaskTest", userId);
    }

    public void testSystemUpdatePolicy() throws Exception {
        if (!mHasFeature) {
            return;
        }
        executeDeviceOwnerTest("SystemUpdatePolicyTest");
    }

    public void testWifiConfigLockdown() throws Exception {
        final boolean hasWifi = hasDeviceFeature("android.hardware.wifi");
        if (hasWifi && mHasFeature) {
            try {
                installAppAsUser(WIFI_CONFIG_CREATOR_APK, mPrimaryUserId);
                executeDeviceOwnerTest("WifiConfigLockdownTest");
            } finally {
                getDevice().uninstallPackage(WIFI_CONFIG_CREATOR_PKG);
            }
        }
    }

    /**
     * Execute WifiSetHttpProxyTest as device owner.
     */
    public void testWifiSetHttpProxyTest() throws Exception {
        final boolean hasWifi = hasDeviceFeature("android.hardware.wifi");
        if (hasWifi && mHasFeature) {
            executeDeviceOwnerTest("WifiSetHttpProxyTest");
        }
    }

    public void testCannotSetDeviceOwnerAgain() throws Exception {
        if (!mHasFeature) {
            return;
        }
        // verify that we can't set the same admin receiver as device owner again
        assertFalse(setDeviceOwner(
                DEVICE_OWNER_PKG + "/" + ADMIN_RECEIVER_TEST_CLASS, mPrimaryUserId,
                /*expectFailure*/ true));

        // verify that we can't set a different admin receiver as device owner
        try {
            installAppAsUser(MANAGED_PROFILE_APK, mPrimaryUserId);
            assertFalse(setDeviceOwner(
                    MANAGED_PROFILE_PKG + "/" + MANAGED_PROFILE_ADMIN, mPrimaryUserId,
                    /*expectFailure*/ true));
        } finally {
            // Remove the device owner in case the test fails.
            removeAdmin(MANAGED_PROFILE_PKG + "/" + MANAGED_PROFILE_ADMIN, mPrimaryUserId);
            getDevice().uninstallPackage(MANAGED_PROFILE_PKG);
        }
    }

    // Execute HardwarePropertiesManagerTest as a device owner.
    public void testHardwarePropertiesManagerAsDeviceOwner() throws Exception {
        if (!mHasFeature)
            return;

        executeDeviceTestMethod(".HardwarePropertiesManagerTest", "testHardwarePropertiesManager");
    }

    // Execute VrTemperatureTest as a device owner.
    public void testVrTemperaturesAsDeviceOwner() throws Exception {
        if (!mHasFeature)
            return;

        executeDeviceTestMethod(".VrTemperatureTest", "testVrTemperatures");
    }

    public void testIsManagedDeviceProvisioningAllowed() throws Exception {
        if (!mHasFeature) {
            return;
        }
        // This case runs when DO is provisioned
        // mHasFeature == true and provisioned, can't provision DO again.
        executeDeviceTestMethod(".PreDeviceOwnerTest", "testIsProvisioningAllowedFalse");
    }

    /**
     * Can provision Managed Profile when DO is set by default if they are the same admin.
     */
    public void testIsManagedProfileProvisioningAllowed_deviceOwnerIsSet() throws Exception {
        if (!mHasFeature) {
            return;
        }
        if (!hasDeviceFeature("android.software.managed_users")) {
            return;
        }
        executeDeviceTestMethod(".PreDeviceOwnerTest",
                "testIsProvisioningAllowedTrueForManagedProfileAction");
    }

    public void testAdminActionBookkeeping() throws Exception {
        if (!mHasFeature) {
            return;
        }

        executeDeviceOwnerTest("AdminActionBookkeepingTest");
    }

    public void testBluetoothRestriction() throws Exception {
        if (!mHasFeature) {
            return;
        }
        executeDeviceOwnerTest("BluetoothRestrictionTest");
    }

    public void testDeviceOwnerProvisioning() throws Exception {
        if (!mHasFeature) {
            return;
        }
        executeDeviceOwnerTest("DeviceOwnerProvisioningTest");
    }

    public void testDisallowFactoryReset() throws Exception {
        if (!mHasFeature) {
            return;
        }
        int adminVersion = 24;
        changeUserRestrictionOrFail("no_factory_reset", true, mPrimaryUserId,
                DEVICE_OWNER_PKG);
        try {
            installAppAsUser(DeviceAdminHelper.getDeviceAdminApkFileName(adminVersion),
                    mPrimaryUserId);
            setDeviceAdmin(DeviceAdminHelper.getAdminReceiverComponent(adminVersion),
                    mPrimaryUserId);
            runDeviceTestsAsUser(
                    DeviceAdminHelper.getDeviceAdminApkPackage(adminVersion),
                    DeviceAdminHelper.getDeviceAdminJavaPackage() + ".WipeDataTest",
                    "testWipeDataThrowsSecurityException", mPrimaryUserId);
        } finally {
            removeAdmin(DeviceAdminHelper.getAdminReceiverComponent(adminVersion), mPrimaryUserId);
            getDevice().uninstallPackage(DeviceAdminHelper.getDeviceAdminApkPackage(adminVersion));
        }
    }

    public void testBackupServiceEnabling() throws Exception {
        final boolean hasBackupService = getDevice().hasFeature(FEATURE_BACKUP);
        // The backup service cannot be enabled if the backup feature is not supported.
        if (!mHasFeature || !hasBackupService) {
            return;
        }
        executeDeviceOwnerTest("BackupServiceEnabledTest");
    }

    private void executeDeviceOwnerTest(String testClassName) throws Exception {
        if (!mHasFeature) {
            return;
        }
        String testClass = DEVICE_OWNER_PKG + "." + testClassName;
        runDeviceTestsAsUser(DEVICE_OWNER_PKG, testClass, mPrimaryUserId);
    }

    private void executeDeviceTestMethod(String className, String testName) throws Exception {
        if (!mHasFeature) {
            return;
        }
        runDeviceTestsAsUser(DEVICE_OWNER_PKG, className, testName,
                /* deviceOwnerUserId */ mPrimaryUserId);
    }
}