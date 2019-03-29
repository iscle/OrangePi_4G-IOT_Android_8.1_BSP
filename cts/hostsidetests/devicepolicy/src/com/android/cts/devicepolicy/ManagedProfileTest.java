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

import junit.framework.AssertionFailedError;

import java.util.Collections;
import java.util.concurrent.Callable;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Set of tests for Managed Profile use cases.
 */
public class ManagedProfileTest extends BaseDevicePolicyTest {

    private static final String MANAGED_PROFILE_PKG = "com.android.cts.managedprofile";
    private static final String MANAGED_PROFILE_APK = "CtsManagedProfileApp.apk";

    private static final String DEVICE_OWNER_PKG = "com.android.cts.deviceowner";
    private static final String DEVICE_OWNER_APK = "CtsDeviceOwnerApp.apk";
    private static final String DEVICE_OWNER_ADMIN =
            DEVICE_OWNER_PKG + ".BaseDeviceOwnerTest$BasicAdminReceiver";

    private static final String INTENT_SENDER_PKG = "com.android.cts.intent.sender";
    private static final String INTENT_SENDER_APK = "CtsIntentSenderApp.apk";

    private static final String INTENT_RECEIVER_PKG = "com.android.cts.intent.receiver";
    private static final String INTENT_RECEIVER_APK = "CtsIntentReceiverApp.apk";

    private static final String WIFI_CONFIG_CREATOR_PKG = "com.android.cts.wificonfigcreator";
    private static final String WIFI_CONFIG_CREATOR_APK = "CtsWifiConfigCreator.apk";

    private static final String WIDGET_PROVIDER_APK = "CtsWidgetProviderApp.apk";
    private static final String WIDGET_PROVIDER_PKG = "com.android.cts.widgetprovider";

    private static final String DIRECTORY_PROVIDER_APK = "CtsContactDirectoryProvider.apk";
    private static final String DIRECTORY_PROVIDER_PKG
            = "com.android.cts.contactdirectoryprovider";
    private static final String PRIMARY_DIRECTORY_PREFIX = "Primary";
    private static final String MANAGED_DIRECTORY_PREFIX = "Managed";
    private static final String DIRECTORY_PRIVOIDER_URI
            = "content://com.android.cts.contact.directory.provider/";
    private static final String SET_CUSTOM_DIRECTORY_PREFIX_METHOD = "set_prefix";

    private static final String NOTIFICATION_APK = "CtsNotificationSenderApp.apk";
    private static final String NOTIFICATION_PKG =
            "com.android.cts.managedprofiletests.notificationsender";
    private static final String NOTIFICATION_ACTIVITY =
            NOTIFICATION_PKG + ".SendNotification";

    private static final String ADMIN_RECEIVER_TEST_CLASS =
            MANAGED_PROFILE_PKG + ".BaseManagedProfileTest$BasicAdminReceiver";

    private static final String FEATURE_BLUETOOTH = "android.hardware.bluetooth";
    private static final String FEATURE_CAMERA = "android.hardware.camera";
    private static final String FEATURE_WIFI = "android.hardware.wifi";
    private static final String FEATURE_TELEPHONY = "android.hardware.telephony";
    private static final String FEATURE_CONNECTION_SERVICE = "android.software.connectionservice";

    private static final String SIMPLE_APP_APK = "CtsSimpleApp.apk";
    private static final String SIMPLE_APP_PKG = "com.android.cts.launcherapps.simpleapp";

    private static final long TIMEOUT_USER_LOCKED_MILLIS = TimeUnit.SECONDS.toMillis(30);

    private static final String PARAM_PROFILE_ID = "profile-id";

    // Password needs to be in sync with ResetPasswordWithTokenTest.PASSWORD1
    private static final String RESET_PASSWORD_TEST_DEFAULT_PASSWORD = "123456";

    private int mParentUserId;

    // ID of the profile we'll create. This will always be a profile of the parent.
    private int mProfileUserId;
    private String mPackageVerifier;

    private boolean mHasNfcFeature;

    @Override
    protected void setUp() throws Exception {
        super.setUp();

        // We need multi user to be supported in order to create a profile of the user owner.
        mHasFeature = mHasFeature && hasDeviceFeature(
                "android.software.managed_users");
        mHasNfcFeature = hasDeviceFeature("android.hardware.nfc");

        if (mHasFeature) {
            removeTestUsers();
            mParentUserId = mPrimaryUserId;
            mProfileUserId = createManagedProfile(mParentUserId);

            installAppAsUser(MANAGED_PROFILE_APK, mParentUserId);
            installAppAsUser(MANAGED_PROFILE_APK, mProfileUserId);
            setProfileOwnerOrFail(MANAGED_PROFILE_PKG + "/" + ADMIN_RECEIVER_TEST_CLASS,
                    mProfileUserId);
            startUser(mProfileUserId);
        }
    }

    @Override
    protected void tearDown() throws Exception {
        if (mHasFeature) {
            removeUser(mProfileUserId);
            getDevice().uninstallPackage(MANAGED_PROFILE_PKG);
            getDevice().uninstallPackage(INTENT_SENDER_PKG);
            getDevice().uninstallPackage(INTENT_RECEIVER_PKG);
            getDevice().uninstallPackage(NOTIFICATION_PKG);
        }
        super.tearDown();
    }

    public void testManagedProfileSetup() throws Exception {
        if (!mHasFeature) {
            return;
        }
        runDeviceTestsAsUser(
                MANAGED_PROFILE_PKG, MANAGED_PROFILE_PKG + ".ManagedProfileSetupTest",
                mProfileUserId);
    }

    /**
     *  wipeData() test removes the managed profile, so it needs to separated from other tests.
     */
    public void testWipeData() throws Exception {
        if (!mHasFeature) {
            return;
        }
        assertTrue(listUsers().contains(mProfileUserId));
        runDeviceTestsAsUser(
                MANAGED_PROFILE_PKG, MANAGED_PROFILE_PKG + ".WipeDataTest", mProfileUserId);
        // Note: the managed profile is removed by this test, which will make removeUserCommand in
        // tearDown() to complain, but that should be OK since its result is not asserted.
        assertUserGetsRemoved(mProfileUserId);
    }

    public void testLockNowWithKeyEviction() throws Exception {
        if (!mHasFeature || !mSupportsFbe) {
            return;
        }
        changeUserCredential("1234", null, mProfileUserId);
        runDeviceTestsAsUser(MANAGED_PROFILE_PKG, MANAGED_PROFILE_PKG + ".LockNowTest",
                "testLockNowWithKeyEviction", mProfileUserId);
        waitUntilProfileLocked();
    }

    private void waitUntilProfileLocked() throws Exception {
        final String cmd = "dumpsys activity | grep 'User #" + mProfileUserId + ": state='";
        final Pattern p = Pattern.compile("state=([\\p{Upper}_]+)$");
        SuccessCondition userLocked = () -> {
            final String activityDump = getDevice().executeShellCommand(cmd);
            final Matcher m = p.matcher(activityDump);
            return m.find() && m.group(1).equals("RUNNING_LOCKED");
        };
        tryWaitForSuccess(
                userLocked,
                "The managed profile has not been locked after calling "
                        + "lockNow(FLAG_SECURE_USER_DATA)",
                TIMEOUT_USER_LOCKED_MILLIS);
    }

    public void testMaxOneManagedProfile() throws Exception {
        int newUserId = -1;
        try {
            newUserId = createManagedProfile(mParentUserId);
        } catch (AssertionFailedError expected) {
        }
        if (newUserId > 0) {
            removeUser(newUserId);
            fail(mHasFeature ? "Device must allow creating only one managed profile"
                    : "Device must not allow creating a managed profile");
        }
    }

    /**
     * Verify that removing a managed profile will remove all networks owned by that profile.
     */
    public void testProfileWifiCleanup() throws Exception {
        if (!mHasFeature || !hasDeviceFeature(FEATURE_WIFI)) {
            return;
        }
        installAppAsUser(WIFI_CONFIG_CREATOR_APK, mProfileUserId);

        runDeviceTestsAsUser(
                MANAGED_PROFILE_PKG, ".WifiTest", "testRemoveWifiNetworkIfExists", mParentUserId);

        runDeviceTestsAsUser(
                MANAGED_PROFILE_PKG, ".WifiTest", "testAddWifiNetwork", mProfileUserId);

        // Now delete the user - should undo the effect of testAddWifiNetwork.
        removeUser(mProfileUserId);
        runDeviceTestsAsUser(
                MANAGED_PROFILE_PKG, ".WifiTest", "testWifiNetworkDoesNotExist",
                mParentUserId);
    }

    public void testWifiMacAddress() throws Exception {
        if (!mHasFeature || !hasDeviceFeature(FEATURE_WIFI)) {
            return;
        }
        runDeviceTestsAsUser(
                MANAGED_PROFILE_PKG, ".WifiTest", "testCannotGetWifiMacAddress", mProfileUserId);
    }

    public void testCrossProfileIntentFilters() throws Exception {
        if (!mHasFeature) {
            return;
        }
        // Set up activities: ManagedProfileActivity will only be enabled in the managed profile and
        // PrimaryUserActivity only in the primary one
        disableActivityForUser("ManagedProfileActivity", mParentUserId);
        disableActivityForUser("PrimaryUserActivity", mProfileUserId);

        runDeviceTestsAsUser(MANAGED_PROFILE_PKG,
                MANAGED_PROFILE_PKG + ".ManagedProfileTest", mProfileUserId);

        // Set up filters from primary to managed profile
        String command = "am start -W --user " + mProfileUserId  + " " + MANAGED_PROFILE_PKG
                + "/.PrimaryUserFilterSetterActivity";
        CLog.d("Output for command " + command + ": "
              + getDevice().executeShellCommand(command));
        runDeviceTestsAsUser(
                MANAGED_PROFILE_PKG, MANAGED_PROFILE_PKG + ".PrimaryUserTest", mParentUserId);
        // TODO: Test with startActivity
    }

    public void testAppLinks_verificationStatus() throws Exception {
        if (!mHasFeature) {
            return;
        }
        // Disable all pre-existing browsers in the managed profile so they don't interfere with
        // intents resolution.
        runDeviceTestsAsUser(MANAGED_PROFILE_PKG, ".CrossProfileUtils",
                "testDisableAllBrowsers", mProfileUserId);
        installAppAsUser(INTENT_RECEIVER_APK, mParentUserId);
        installAppAsUser(INTENT_SENDER_APK, mParentUserId);
        installAppAsUser(INTENT_RECEIVER_APK, mProfileUserId);
        installAppAsUser(INTENT_SENDER_APK, mProfileUserId);

        changeVerificationStatus(mParentUserId, INTENT_RECEIVER_PKG, "ask");
        changeVerificationStatus(mProfileUserId, INTENT_RECEIVER_PKG, "ask");
        // We should have two receivers: IntentReceiverActivity and BrowserActivity in the
        // managed profile
        assertAppLinkResult("testTwoReceivers");

        changeUserRestrictionOrFail("allow_parent_profile_app_linking", true, mProfileUserId);
        // Now we should also have one receiver in the primary user, so three receivers in total.
        assertAppLinkResult("testThreeReceivers");

        changeVerificationStatus(mParentUserId, INTENT_RECEIVER_PKG, "never");
        // The primary user one has been set to never: we should only have the managed profile ones.
        assertAppLinkResult("testTwoReceivers");

        changeVerificationStatus(mProfileUserId, INTENT_RECEIVER_PKG, "never");
        // Now there's only the browser in the managed profile left
        assertAppLinkResult("testReceivedByBrowserActivityInManaged");

        changeVerificationStatus(mProfileUserId, INTENT_RECEIVER_PKG, "always");
        changeVerificationStatus(mParentUserId, INTENT_RECEIVER_PKG, "always");
        // We have one always in the primary user and one always in the managed profile: the managed
        // profile one should have precedence.
        assertAppLinkResult("testReceivedByAppLinkActivityInManaged");
    }

    public void testAppLinks_enabledStatus() throws Exception {
        if (!mHasFeature) {
            return;
        }
        // Disable all pre-existing browsers in the managed profile so they don't interfere with
        // intents resolution.
        runDeviceTestsAsUser(MANAGED_PROFILE_PKG, ".CrossProfileUtils",
                "testDisableAllBrowsers", mProfileUserId);
        installAppAsUser(INTENT_RECEIVER_APK, mParentUserId);
        installAppAsUser(INTENT_SENDER_APK, mParentUserId);
        installAppAsUser(INTENT_RECEIVER_APK, mProfileUserId);
        installAppAsUser(INTENT_SENDER_APK, mProfileUserId);

        final String APP_HANDLER_COMPONENT = "com.android.cts.intent.receiver/.AppLinkActivity";

        // allow_parent_profile_app_linking is not set, try different enabled state combinations.
        // We should not have app link handler in parent user no matter whether it is enabled.

        disableComponentOrPackage(mParentUserId, APP_HANDLER_COMPONENT);
        disableComponentOrPackage(mProfileUserId, APP_HANDLER_COMPONENT);
        assertAppLinkResult("testReceivedByBrowserActivityInManaged");

        enableComponentOrPackage(mParentUserId, APP_HANDLER_COMPONENT);
        disableComponentOrPackage(mProfileUserId, APP_HANDLER_COMPONENT);
        assertAppLinkResult("testReceivedByBrowserActivityInManaged");

        disableComponentOrPackage(mParentUserId, APP_HANDLER_COMPONENT);
        enableComponentOrPackage(mProfileUserId, APP_HANDLER_COMPONENT);
        assertAppLinkResult("testTwoReceivers");

        enableComponentOrPackage(mParentUserId, APP_HANDLER_COMPONENT);
        enableComponentOrPackage(mProfileUserId, APP_HANDLER_COMPONENT);
        assertAppLinkResult("testTwoReceivers");

        // We now set allow_parent_profile_app_linking, and hence we should have the app handler
        // in parent user if it is enabled.
        changeUserRestrictionOrFail("allow_parent_profile_app_linking", true, mProfileUserId);

        disableComponentOrPackage(mParentUserId, APP_HANDLER_COMPONENT);
        disableComponentOrPackage(mProfileUserId, APP_HANDLER_COMPONENT);
        assertAppLinkResult("testReceivedByBrowserActivityInManaged");

        enableComponentOrPackage(mParentUserId, APP_HANDLER_COMPONENT);
        disableComponentOrPackage(mProfileUserId, APP_HANDLER_COMPONENT);
        assertAppLinkResult("testTwoReceivers");

        disableComponentOrPackage(mParentUserId, APP_HANDLER_COMPONENT);
        enableComponentOrPackage(mProfileUserId, APP_HANDLER_COMPONENT);
        assertAppLinkResult("testTwoReceivers");

        enableComponentOrPackage(mParentUserId, APP_HANDLER_COMPONENT);
        enableComponentOrPackage(mProfileUserId, APP_HANDLER_COMPONENT);
        assertAppLinkResult("testThreeReceivers");
    }

    public void testSettingsIntents() throws Exception {
        if (!mHasFeature) {
            return;
        }

        runDeviceTestsAsUser(MANAGED_PROFILE_PKG, ".SettingsIntentsTest",
                mProfileUserId);
    }

    public void testCrossProfileContent() throws Exception {
        if (!mHasFeature) {
            return;
        }
        installAppAsUser(INTENT_RECEIVER_APK, mParentUserId);
        installAppAsUser(INTENT_SENDER_APK, mParentUserId);
        installAppAsUser(INTENT_RECEIVER_APK, mProfileUserId);
        installAppAsUser(INTENT_SENDER_APK, mProfileUserId);

        // Test from parent to managed
        runDeviceTestsAsUser(MANAGED_PROFILE_PKG, ".CrossProfileUtils",
                "testRemoveAllFilters", mProfileUserId);
        runDeviceTestsAsUser(MANAGED_PROFILE_PKG, ".CrossProfileUtils",
                "testAddManagedCanAccessParentFilters", mProfileUserId);
        runDeviceTestsAsUser(INTENT_SENDER_PKG, ".ContentTest", mParentUserId);

        // Test from managed to parent
        runDeviceTestsAsUser(MANAGED_PROFILE_PKG, ".CrossProfileUtils",
                "testRemoveAllFilters", mProfileUserId);
        runDeviceTestsAsUser(MANAGED_PROFILE_PKG, ".CrossProfileUtils",
                "testAddParentCanAccessManagedFilters", mProfileUserId);
        runDeviceTestsAsUser(INTENT_SENDER_PKG, ".ContentTest", mProfileUserId);

    }

    public void testCrossProfileNotificationListeners_EmptyWhitelist() throws Exception {
        if (!mHasFeature) {
            return;
        }

        installAppAsUser(NOTIFICATION_APK, mProfileUserId);
        installAppAsUser(NOTIFICATION_APK, mParentUserId);

        // Profile owner in the profile sets an empty whitelist
        runDeviceTestsAsUser(MANAGED_PROFILE_PKG, ".NotificationListenerTest",
                "testSetEmptyWhitelist", mProfileUserId,
                Collections.singletonMap(PARAM_PROFILE_ID, Integer.toString(mProfileUserId)));
        // Listener outside the profile can only see personal notifications.
        runDeviceTestsAsUser(MANAGED_PROFILE_PKG, ".NotificationListenerTest",
                "testCannotReceiveProfileNotifications", mParentUserId,
                Collections.singletonMap(PARAM_PROFILE_ID, Integer.toString(mProfileUserId)));
    }

    public void testCrossProfileNotificationListeners_NullWhitelist() throws Exception {
        if (!mHasFeature) {
            return;
        }

        installAppAsUser(NOTIFICATION_APK, mProfileUserId);
        installAppAsUser(NOTIFICATION_APK, mParentUserId);

        // Profile owner in the profile sets a null whitelist
        runDeviceTestsAsUser(MANAGED_PROFILE_PKG, ".NotificationListenerTest",
                "testSetNullWhitelist", mProfileUserId,
                Collections.singletonMap(PARAM_PROFILE_ID, Integer.toString(mProfileUserId)));
        // Listener outside the profile can see profile and personal notifications
        runDeviceTestsAsUser(MANAGED_PROFILE_PKG, ".NotificationListenerTest",
                "testCanReceiveNotifications", mParentUserId,
                Collections.singletonMap(PARAM_PROFILE_ID, Integer.toString(mProfileUserId)));
    }

    public void testCrossProfileNotificationListeners_InWhitelist() throws Exception {
        if (!mHasFeature) {
            return;
        }

        installAppAsUser(NOTIFICATION_APK, mProfileUserId);
        installAppAsUser(NOTIFICATION_APK, mParentUserId);

        // Profile owner in the profile adds listener to the whitelist
        runDeviceTestsAsUser(MANAGED_PROFILE_PKG, ".NotificationListenerTest",
                "testAddListenerToWhitelist", mProfileUserId,
                Collections.singletonMap(PARAM_PROFILE_ID, Integer.toString(mProfileUserId)));
        // Listener outside the profile can see profile and personal notifications
        runDeviceTestsAsUser(MANAGED_PROFILE_PKG, ".NotificationListenerTest",
                "testCanReceiveNotifications", mParentUserId,
                Collections.singletonMap(PARAM_PROFILE_ID, Integer.toString(mProfileUserId)));
    }

    public void testCrossProfileCopyPaste() throws Exception {
        if (!mHasFeature) {
            return;
        }
        installAppAsUser(INTENT_RECEIVER_APK, mParentUserId);
        installAppAsUser(INTENT_SENDER_APK, mParentUserId);
        installAppAsUser(INTENT_RECEIVER_APK, mProfileUserId);
        installAppAsUser(INTENT_SENDER_APK, mProfileUserId);

        runDeviceTestsAsUser(MANAGED_PROFILE_PKG, ".CrossProfileUtils",
                "testAllowCrossProfileCopyPaste", mProfileUserId);
        // Test that managed can see what is copied in the parent.
        testCrossProfileCopyPasteInternal(mProfileUserId, true);
        // Test that the parent can see what is copied in managed.
        testCrossProfileCopyPasteInternal(mParentUserId, true);

        runDeviceTestsAsUser(MANAGED_PROFILE_PKG, ".CrossProfileUtils",
                "testDisallowCrossProfileCopyPaste", mProfileUserId);
        // Test that managed can still see what is copied in the parent.
        testCrossProfileCopyPasteInternal(mProfileUserId, true);
        // Test that the parent cannot see what is copied in managed.
        testCrossProfileCopyPasteInternal(mParentUserId, false);
    }

    private void testCrossProfileCopyPasteInternal(int userId, boolean shouldSucceed)
            throws DeviceNotAvailableException {
        final String direction = (userId == mParentUserId)
                ? "testAddManagedCanAccessParentFilters"
                : "testAddParentCanAccessManagedFilters";
        runDeviceTestsAsUser(MANAGED_PROFILE_PKG, ".CrossProfileUtils",
                "testRemoveAllFilters", mProfileUserId);
        runDeviceTestsAsUser(MANAGED_PROFILE_PKG, ".CrossProfileUtils",
                direction, mProfileUserId);
        if (shouldSucceed) {
            runDeviceTestsAsUser(INTENT_SENDER_PKG, ".CopyPasteTest",
                    "testCanReadAcrossProfiles", userId);
            runDeviceTestsAsUser(INTENT_SENDER_PKG, ".CopyPasteTest",
                    "testIsNotified", userId);
        } else {
            runDeviceTestsAsUser(INTENT_SENDER_PKG, ".CopyPasteTest",
                    "testCannotReadAcrossProfiles", userId);
        }
    }

    /** Tests for the API helper class. */
    public void testCurrentApiHelper() throws Exception {
        if (!mHasFeature) {
            return;
        }
        runDeviceTestsAsUser(MANAGED_PROFILE_PKG, ".CurrentApiHelperTest",
                mProfileUserId);
    }

    /** Test: unsupported public APIs are disabled on a parent profile. */
    public void testParentProfileApiDisabled() throws Exception {
        if (!mHasFeature) {
            return;
        }
        runDeviceTestsAsUser(MANAGED_PROFILE_PKG, ".ParentProfileTest",
                "testParentProfileApiDisabled", mProfileUserId);
    }

    // TODO: This test is not specific to managed profiles, but applies to multi-user in general.
    // Move it to a MultiUserTest class when there is one. Should probably move
    // SetPolicyActivity to a more generic apk too as it might be useful for different kinds
    // of tests (same applies to ComponentDisablingActivity).
    public void testNoDebuggingFeaturesRestriction() throws Exception {
        if (!mHasFeature) {
            return;
        }
        // If adb is running as root, then the adb uid is 0 instead of SHELL_UID,
        // so the DISALLOW_DEBUGGING_FEATURES restriction does not work and this test
        // fails.
        if (getDevice().isAdbRoot()) {
            CLog.logAndDisplay(LogLevel.WARN,
                    "Cannot test testNoDebuggingFeaturesRestriction() in eng/userdebug build");
            return;
        }
        String restriction = "no_debugging_features";  // UserManager.DISALLOW_DEBUGGING_FEATURES

        changeUserRestrictionOrFail(restriction, true, mProfileUserId);


        // This should now fail, as the shell is not available to start activities under a different
        // user once the restriction is in place.
        String addRestrictionCommandOutput =
                changeUserRestriction(restriction, true, mProfileUserId);
        assertTrue(
                "Expected SecurityException when starting the activity "
                        + addRestrictionCommandOutput,
                addRestrictionCommandOutput.contains("SecurityException"));
    }

    // Test the bluetooth API from a managed profile.
    public void testBluetooth() throws Exception {
        boolean hasBluetooth = hasDeviceFeature(FEATURE_BLUETOOTH);
        if (!mHasFeature || !hasBluetooth) {
            return ;
        }

        runDeviceTestsAsUser(MANAGED_PROFILE_PKG, ".BluetoothTest",
                "testEnableDisable", mProfileUserId);
        runDeviceTestsAsUser(MANAGED_PROFILE_PKG, ".BluetoothTest",
                "testGetAddress", mProfileUserId);
        runDeviceTestsAsUser(MANAGED_PROFILE_PKG, ".BluetoothTest",
                "testListenUsingRfcommWithServiceRecord", mProfileUserId);
        runDeviceTestsAsUser(MANAGED_PROFILE_PKG, ".BluetoothTest",
                "testGetRemoteDevice", mProfileUserId);
    }

    public void testCameraPolicy() throws Exception {
        boolean hasCamera = hasDeviceFeature(FEATURE_CAMERA);
        if (!mHasFeature || !hasCamera) {
            return;
        }
        try {
            setDeviceAdmin(MANAGED_PROFILE_PKG + "/.PrimaryUserDeviceAdmin", mParentUserId);

            // Disable managed profile camera.
            runDeviceTestsAsUser(MANAGED_PROFILE_PKG, ".CameraPolicyTest",
                    "testDisableCameraInManagedProfile",
                    mProfileUserId);
            runDeviceTestsAsUser(MANAGED_PROFILE_PKG, ".CameraPolicyTest",
                    "testIsCameraEnabledInPrimaryProfile",
                    mParentUserId);

            // Enable managed profile camera.
            runDeviceTestsAsUser(MANAGED_PROFILE_PKG, ".CameraPolicyTest",
                    "testEnableCameraInManagedProfile",
                    mProfileUserId);
            runDeviceTestsAsUser(MANAGED_PROFILE_PKG, ".CameraPolicyTest",
                    "testIsCameraEnabledInPrimaryProfile",
                    mParentUserId);

            // Disable primary profile camera.
            runDeviceTestsAsUser(MANAGED_PROFILE_PKG, ".CameraPolicyTest",
                    "testDisableCameraInPrimaryProfile",
                    mParentUserId);
            runDeviceTestsAsUser(MANAGED_PROFILE_PKG, ".CameraPolicyTest",
                    "testIsCameraEnabledInManagedProfile",
                    mProfileUserId);

            // Enable primary profile camera.
            runDeviceTestsAsUser(MANAGED_PROFILE_PKG, ".CameraPolicyTest",
                    "testEnableCameraInPrimaryProfile",
                    mParentUserId);
            runDeviceTestsAsUser(MANAGED_PROFILE_PKG, ".CameraPolicyTest",
                    "testIsCameraEnabledInManagedProfile",
                    mProfileUserId);
        } finally {
            final String adminHelperClass = ".PrimaryUserAdminHelper";
            runDeviceTestsAsUser(MANAGED_PROFILE_PKG,
                    adminHelperClass, "testClearDeviceAdmin", mParentUserId);
        }
    }


    public void testManagedContactsUris() throws Exception {
        runManagedContactsTest(new Callable<Void>() {
            @Override
            public Void call() throws Exception {
                ContactsTestSet contactsTestSet = new ContactsTestSet(ManagedProfileTest.this,
                        MANAGED_PROFILE_PKG, mParentUserId, mProfileUserId);

                contactsTestSet.setCallerIdEnabled(true);
                contactsTestSet.setContactsSearchEnabled(true);
                contactsTestSet.checkIfCanLookupEnterpriseContacts(true);
                contactsTestSet.checkIfCanFilterEnterpriseContacts(true);
                contactsTestSet.checkIfCanFilterSelfContacts();
                return null;
            }
        });
    }

    public void testManagedQuickContacts() throws Exception {
        runManagedContactsTest(new Callable<Void>() {
            @Override
            public Void call() throws Exception {
                runDeviceTestsAsUser(MANAGED_PROFILE_PKG, ".ContactsTest",
                        "testQuickContact", mParentUserId);
                return null;
            }
        });
    }

    public void testManagedContactsPolicies() throws Exception {
        runManagedContactsTest(new Callable<Void>() {
            @Override
            public Void call() throws Exception {
                ContactsTestSet contactsTestSet = new ContactsTestSet(ManagedProfileTest.this,
                        MANAGED_PROFILE_PKG, mParentUserId, mProfileUserId);
                try {
                    contactsTestSet.setCallerIdEnabled(true);
                    contactsTestSet.setContactsSearchEnabled(false);
                    contactsTestSet.checkIfCanLookupEnterpriseContacts(true);
                    contactsTestSet.checkIfCanFilterEnterpriseContacts(false);
                    contactsTestSet.checkIfCanFilterSelfContacts();
                    contactsTestSet.setCallerIdEnabled(false);
                    contactsTestSet.setContactsSearchEnabled(true);
                    contactsTestSet.checkIfCanLookupEnterpriseContacts(false);
                    contactsTestSet.checkIfCanFilterEnterpriseContacts(true);
                    contactsTestSet.checkIfCanFilterSelfContacts();
                    contactsTestSet.setCallerIdEnabled(false);
                    contactsTestSet.setContactsSearchEnabled(false);
                    contactsTestSet.checkIfCanLookupEnterpriseContacts(false);
                    contactsTestSet.checkIfCanFilterEnterpriseContacts(false);
                    contactsTestSet.checkIfCanFilterSelfContacts();
                    contactsTestSet.checkIfNoEnterpriseDirectoryFound();
                    return null;
                } finally {
                    // reset policies
                    contactsTestSet.setCallerIdEnabled(true);
                    contactsTestSet.setContactsSearchEnabled(true);
                }
            }
        });
    }

    public void testOrganizationInfo() throws Exception {
        if (!mHasFeature) {
            return;
        }
        runDeviceTestsAsUser(MANAGED_PROFILE_PKG, ".OrganizationInfoTest",
                "testDefaultOrganizationColor", mProfileUserId);
        runDeviceTestsAsUser(MANAGED_PROFILE_PKG, ".OrganizationInfoTest",
                "testDefaultOrganizationNameIsNull", mProfileUserId);
        runDeviceTestsAsUser(MANAGED_PROFILE_PKG, ".OrganizationInfoTest",
                mProfileUserId);
    }

    public void testPasswordMinimumRestrictions() throws Exception {
        if (!mHasFeature) {
            return;
        }
        runDeviceTestsAsUser(MANAGED_PROFILE_PKG, ".PasswordMinimumRestrictionsTest",
                mProfileUserId);
    }

    public void testBluetoothContactSharingDisabled() throws Exception {
        if (!mHasFeature) {
            return;
        }
        runDeviceTestsAsUser(MANAGED_PROFILE_PKG, ".ContactsTest",
                "testSetBluetoothContactSharingDisabled_setterAndGetter", mProfileUserId);
    }

    public void testCannotSetProfileOwnerAgain() throws Exception {
        if (!mHasFeature) {
            return;
        }
        // verify that we can't set the same admin receiver as profile owner again
        assertFalse(setProfileOwner(
                MANAGED_PROFILE_PKG + "/" + ADMIN_RECEIVER_TEST_CLASS, mProfileUserId,
                /*expectFailure*/ true));

        // verify that we can't set a different admin receiver as profile owner
        installAppAsUser(DEVICE_OWNER_APK, mProfileUserId);
        assertFalse(setProfileOwner(DEVICE_OWNER_PKG + "/" + DEVICE_OWNER_ADMIN, mProfileUserId,
                /*expectFailure*/ true));
    }

    public void testCannotSetDeviceOwnerWhenProfilePresent() throws Exception {
        if (!mHasFeature) {
            return;
        }

        try {
            installAppAsUser(DEVICE_OWNER_APK, mParentUserId);
            assertFalse(setDeviceOwner(DEVICE_OWNER_PKG + "/" + DEVICE_OWNER_ADMIN, mParentUserId,
                    /*expectFailure*/ true));
        } finally {
            // make sure we clean up in case we succeeded in setting the device owner
            removeAdmin(DEVICE_OWNER_PKG + "/" + DEVICE_OWNER_ADMIN, mParentUserId);
            getDevice().uninstallPackage(DEVICE_OWNER_PKG);
        }
    }

    public void testNfcRestriction() throws Exception {
        if (!mHasFeature || !mHasNfcFeature) {
            return;
        }

        runDeviceTestsAsUser(MANAGED_PROFILE_PKG, ".NfcTest",
                "testNfcShareEnabled", mProfileUserId);
        runDeviceTestsAsUser(MANAGED_PROFILE_PKG, ".NfcTest",
                "testNfcShareEnabled", mParentUserId);

        changeUserRestrictionOrFail("no_outgoing_beam" /* UserManager.DISALLOW_OUTGOING_BEAM */,
                true, mProfileUserId);

        runDeviceTestsAsUser(MANAGED_PROFILE_PKG, ".NfcTest",
                "testNfcShareDisabled", mProfileUserId);
        runDeviceTestsAsUser(MANAGED_PROFILE_PKG, ".NfcTest",
                "testNfcShareEnabled", mParentUserId);
    }

    public void testCrossProfileWidgets() throws Exception {
        if (!mHasFeature) {
            return;
        }

        try {
            installAppAsUser(WIDGET_PROVIDER_APK, mProfileUserId);
            installAppAsUser(WIDGET_PROVIDER_APK, mParentUserId);
            getDevice().executeShellCommand("appwidget grantbind --user " + mParentUserId
                    + " --package " + WIDGET_PROVIDER_PKG);
            setIdleWhitelist(WIDGET_PROVIDER_PKG, true);
            startWidgetHostService();

            String commandOutput = changeCrossProfileWidgetForUser(WIDGET_PROVIDER_PKG,
                    "add-cross-profile-widget", mProfileUserId);
            assertTrue("Command was expected to succeed " + commandOutput,
                    commandOutput.contains("Status: ok"));

            runDeviceTestsAsUser(MANAGED_PROFILE_PKG, ".CrossProfileWidgetTest",
                    "testCrossProfileWidgetProviderAdded", mProfileUserId);
            runDeviceTestsAsUser(MANAGED_PROFILE_PKG,
                    ".CrossProfileWidgetPrimaryUserTest",
                    "testHasCrossProfileWidgetProvider_true", mParentUserId);
            runDeviceTestsAsUser(MANAGED_PROFILE_PKG,
                    ".CrossProfileWidgetPrimaryUserTest",
                    "testHostReceivesWidgetUpdates_true", mParentUserId);

            commandOutput = changeCrossProfileWidgetForUser(WIDGET_PROVIDER_PKG,
                    "remove-cross-profile-widget", mProfileUserId);
            assertTrue("Command was expected to succeed " + commandOutput,
                    commandOutput.contains("Status: ok"));

            runDeviceTestsAsUser(MANAGED_PROFILE_PKG, ".CrossProfileWidgetTest",
                    "testCrossProfileWidgetProviderRemoved", mProfileUserId);
            runDeviceTestsAsUser(MANAGED_PROFILE_PKG,
                    ".CrossProfileWidgetPrimaryUserTest",
                    "testHasCrossProfileWidgetProvider_false", mParentUserId);
            runDeviceTestsAsUser(MANAGED_PROFILE_PKG,
                    ".CrossProfileWidgetPrimaryUserTest",
                    "testHostReceivesWidgetUpdates_false", mParentUserId);
        } finally {
            changeCrossProfileWidgetForUser(WIDGET_PROVIDER_PKG, "remove-cross-profile-widget",
                    mProfileUserId);
            getDevice().uninstallPackage(WIDGET_PROVIDER_PKG);
        }
    }

    public void testIsProvisioningAllowed() throws DeviceNotAvailableException {
        if (!mHasFeature) {
            return;
        }
        // In Managed profile user when managed profile is provisioned
        runDeviceTestsAsUser(MANAGED_PROFILE_PKG, ".PreManagedProfileTest",
                "testIsProvisioningAllowedFalse", mProfileUserId);

        // In parent user when managed profile is provisioned
        // It's allowed to provision again by removing the previous profile
        runDeviceTestsAsUser(MANAGED_PROFILE_PKG, ".PreManagedProfileTest",
                "testIsProvisioningAllowedTrue", mParentUserId);
    }

    private void setDirectoryPrefix(String directoryName, int userId)
            throws DeviceNotAvailableException {
        String command = "content call --uri " + DIRECTORY_PRIVOIDER_URI
                + " --user " + userId
                + " --method " + SET_CUSTOM_DIRECTORY_PREFIX_METHOD
                + " --arg " + directoryName;
        CLog.d("Output for command " + command + ": "
                + getDevice().executeShellCommand(command));
    }

    public void testPhoneAccountVisibility() throws Exception  {
        if (!mHasFeature) {
            return;
        }
        if (!shouldRunTelecomTest()) {
            return;
        }
        try {
            // Register phone account in parent user.
            runDeviceTestsAsUser(MANAGED_PROFILE_PKG, ".PhoneAccountTest",
                    "testRegisterPhoneAccount",
                    mParentUserId);
            // The phone account should not be visible in managed user.
            runDeviceTestsAsUser(MANAGED_PROFILE_PKG, ".PhoneAccountTest",
                    "testPhoneAccountNotRegistered",
                    mProfileUserId);
        } finally {
            // Unregister the phone account.
            runDeviceTestsAsUser(MANAGED_PROFILE_PKG, ".PhoneAccountTest",
                    "testUnregisterPhoneAccount",
                    mParentUserId);
        }

        try {
            // Register phone account in profile user.
            runDeviceTestsAsUser(MANAGED_PROFILE_PKG, ".PhoneAccountTest",
                    "testRegisterPhoneAccount",
                    mProfileUserId);
            // The phone account should not be visible in parent user.
            runDeviceTestsAsUser(MANAGED_PROFILE_PKG, ".PhoneAccountTest",
                    "testPhoneAccountNotRegistered",
                    mParentUserId);
        } finally {
            // Unregister the phone account.
            runDeviceTestsAsUser(MANAGED_PROFILE_PKG, ".PhoneAccountTest",
                    "testUnregisterPhoneAccount",
                    mProfileUserId);
        }
    }

    public void testManagedCall() throws Exception {
        if (!mHasFeature) {
            return;
        }
        if (!shouldRunTelecomTest()) {
            return;
        }
        getDevice().setSetting(
            mProfileUserId, "secure", "dialer_default_application", MANAGED_PROFILE_PKG);

        // Place a outgoing call through work phone account using TelecomManager and verify the
        // call is inserted properly.
        runDeviceTestsAsUser(MANAGED_PROFILE_PKG, ".PhoneAccountTest",
                "testOutgoingCallUsingTelecomManager",
                mProfileUserId);
        // Make sure the call is not inserted into parent user.
        runDeviceTestsAsUser(MANAGED_PROFILE_PKG, ".PhoneAccountTest",
                "testEnsureCallNotInserted",
                mParentUserId);

        // Place a outgoing call through work phone account using ACTION_CALL and verify the call
        // is inserted properly.
        runDeviceTestsAsUser(MANAGED_PROFILE_PKG, ".PhoneAccountTest",
                "testOutgoingCallUsingActionCall",
                mProfileUserId);
        // Make sure the call is not inserted into parent user.
        runDeviceTestsAsUser(MANAGED_PROFILE_PKG, ".PhoneAccountTest",
                "testEnsureCallNotInserted",
                mParentUserId);

        // Add an incoming call with parent user's phone account and verify the call is inserted
        // properly.
        runDeviceTestsAsUser(MANAGED_PROFILE_PKG, ".PhoneAccountTest",
                "testIncomingCall",
                mProfileUserId);
        // Make sure the call is not inserted into parent user.
        runDeviceTestsAsUser(MANAGED_PROFILE_PKG, ".PhoneAccountTest",
                "testEnsureCallNotInserted",
                mParentUserId);

        // Add an incoming missed call with parent user's phone account and verify the call is
        // inserted properly.
        runDeviceTestsAsUser(MANAGED_PROFILE_PKG, ".PhoneAccountTest",
            "testIncomingMissedCall",
            mProfileUserId);
        // Make sure the call is not inserted into parent user.
        runDeviceTestsAsUser(MANAGED_PROFILE_PKG, ".PhoneAccountTest",
            "testEnsureCallNotInserted",
            mParentUserId);
    }

    private void givePackageWriteSettingsPermission(int userId, String pkg) throws Exception {
        // Allow app to write to settings (for RingtoneManager.setActualDefaultUri to work)
        String command = "appops set --user " + userId + " " + pkg
                + " android:write_settings allow";
        CLog.d("Output for command " + command + ": " + getDevice().executeShellCommand(command));
    }

    public void testRingtoneSync() throws Exception {
        if (!mHasFeature) {
            return;
        }
        givePackageWriteSettingsPermission(mProfileUserId, MANAGED_PROFILE_PKG);
        runDeviceTestsAsUser(MANAGED_PROFILE_PKG, ".RingtoneSyncTest",
                "testRingtoneSync", mProfileUserId);
    }

    // Test if setting RINGTONE disables sync
    public void testRingtoneSyncAutoDisableRingtone() throws Exception {
        if (!mHasFeature) {
            return;
        }
        givePackageWriteSettingsPermission(mProfileUserId, MANAGED_PROFILE_PKG);
        runDeviceTestsAsUser(MANAGED_PROFILE_PKG, ".RingtoneSyncTest",
                "testRingtoneDisableSync", mProfileUserId);
    }

    // Test if setting NOTIFICATION disables sync
    public void testRingtoneSyncAutoDisableNotification() throws Exception {
        if (!mHasFeature) {
            return;
        }
        givePackageWriteSettingsPermission(mProfileUserId, MANAGED_PROFILE_PKG);
        runDeviceTestsAsUser(MANAGED_PROFILE_PKG, ".RingtoneSyncTest",
                "testNotificationDisableSync", mProfileUserId);
    }

    // Test if setting ALARM disables sync
    public void testRingtoneSyncAutoDisableAlarm() throws Exception {
        if (!mHasFeature) {
            return;
        }
        givePackageWriteSettingsPermission(mProfileUserId, MANAGED_PROFILE_PKG);
        runDeviceTestsAsUser(MANAGED_PROFILE_PKG, ".RingtoneSyncTest",
                "testAlarmDisableSync", mProfileUserId);
    }

    public void testTrustAgentInfo() throws Exception {
        if (!mHasFeature) {
            return;
        }
        // Set and get trust agent config using child dpm instance.
        runDeviceTestsAsUser(MANAGED_PROFILE_PKG, ".TrustAgentInfoTest",
                "testSetAndGetTrustAgentConfiguration_child",
                mProfileUserId);
        // Set and get trust agent config using parent dpm instance.
        runDeviceTestsAsUser(MANAGED_PROFILE_PKG, ".TrustAgentInfoTest",
                "testSetAndGetTrustAgentConfiguration_parent",
                mProfileUserId);
        // Unified case
        runDeviceTestsAsUser(MANAGED_PROFILE_PKG, ".TrustAgentInfoTest",
                "testSetTrustAgentConfiguration_bothHaveTrustAgentConfigAndUnified",
                mProfileUserId);
        // Non-unified case
        try {
            changeUserCredential("1234", null, mProfileUserId);
            runDeviceTestsAsUser(MANAGED_PROFILE_PKG, ".TrustAgentInfoTest",
                    "testSetTrustAgentConfiguration_bothHaveTrustAgentConfigAndNonUnified",
                    mProfileUserId);
        } finally {
            changeUserCredential(null, "1234", mProfileUserId);
        }
    }

    public void testSanityCheck() throws Exception {
        if (!mHasFeature) {
            return;
        }
        // Install SimpleApp in work profile only and check activity in it can be launched.
        installAppAsUser(SIMPLE_APP_APK, mProfileUserId);
        runDeviceTestsAsUser(MANAGED_PROFILE_PKG, ".SanityTest", mProfileUserId);
    }

    public void testBluetoothSharingRestriction() throws Exception {
        final boolean hasBluetooth = hasDeviceFeature(FEATURE_BLUETOOTH);
        if (!mHasFeature || !hasBluetooth) {
            return;
        }

        // Primary profile should be able to use bluetooth sharing.
        runDeviceTestsAsUser(MANAGED_PROFILE_PKG, ".BluetoothSharingRestrictionPrimaryProfileTest",
                "testBluetoothSharingAvailable", mPrimaryUserId);

        // Managed profile owner should be able to control it via DISALLOW_BLUETOOTH_SHARING.
        runDeviceTestsAsUser(MANAGED_PROFILE_PKG, ".BluetoothSharingRestrictionTest",
                "testOppDisabledWhenRestrictionSet", mProfileUserId);
    }

    public void testResetPasswordWithTokenBeforeUnlock() throws Exception {
        if (!mHasFeature || !mSupportsFbe) {
            return;
        }

        runDeviceTestsAsUser(MANAGED_PROFILE_PKG, ".ResetPasswordWithTokenTest",
                "testSetupWorkProfileAndLock", mProfileUserId);
        waitUntilProfileLocked();
        runDeviceTestsAsUser(MANAGED_PROFILE_PKG, ".ResetPasswordWithTokenTest",
                "testResetPasswordBeforeUnlock", mProfileUserId);
        // Password needs to be in sync with ResetPasswordWithTokenTest.PASSWORD1
        verifyUserCredential(RESET_PASSWORD_TEST_DEFAULT_PASSWORD, mProfileUserId);
    }

    /**
     * Test password reset token is still functional after the primary user clears and
     * re-adds back its device lock. This is to detect a regression where the work profile
     * undergoes an untrusted credential reset (causing synthetic password to change, invalidating
     * existing password reset token) if it has unified work challenge and the primary user clears
     * the device lock.
     */
    public void testResetPasswordTokenUsableAfterClearingLock() throws Exception {
        if (!mHasFeature || !mSupportsFbe) {
            return;
        }
        final String devicePassword = "1234";

        runDeviceTestsAsUser(MANAGED_PROFILE_PKG, ".ResetPasswordWithTokenTest",
                "testSetResetPasswordToken", mProfileUserId);
        try {
            changeUserCredential(devicePassword, null, mParentUserId);
            changeUserCredential(null, devicePassword, mParentUserId);
            changeUserCredential(devicePassword, null, mParentUserId);

            runDeviceTestsAsUser(MANAGED_PROFILE_PKG, ".ResetPasswordWithTokenTest",
                    "testLockWorkProfile", mProfileUserId);
            waitUntilProfileLocked();
            runDeviceTestsAsUser(MANAGED_PROFILE_PKG, ".ResetPasswordWithTokenTest",
                    "testResetPasswordBeforeUnlock", mProfileUserId);
            verifyUserCredential(RESET_PASSWORD_TEST_DEFAULT_PASSWORD, mProfileUserId);
        } finally {
            changeUserCredential(null, devicePassword, mParentUserId);
            // Cycle the device screen to flush stale password information from keyguard,
            // otherwise it will still ask for the non-existent password.
            executeShellCommand("input keyevent KEYCODE_WAKEUP");
            executeShellCommand("input keyevent KEYCODE_SLEEP");
        }
    }

    private void disableActivityForUser(String activityName, int userId)
            throws DeviceNotAvailableException {
        String command = "am start -W --user " + userId
                + " --es extra-package " + MANAGED_PROFILE_PKG
                + " --es extra-class-name " + MANAGED_PROFILE_PKG + "." + activityName
                + " " + MANAGED_PROFILE_PKG + "/.ComponentDisablingActivity ";
        CLog.d("Output for command " + command + ": "
                + getDevice().executeShellCommand(command));
    }

    private void changeUserRestrictionOrFail(String key, boolean value, int userId)
            throws DeviceNotAvailableException {
        changeUserRestrictionOrFail(key, value, userId, MANAGED_PROFILE_PKG);
    }

    private String changeUserRestriction(String key, boolean value, int userId)
            throws DeviceNotAvailableException {
        return changeUserRestriction(key, value, userId, MANAGED_PROFILE_PKG);
    }

    private void setIdleWhitelist(String packageName, boolean enabled)
            throws DeviceNotAvailableException {
        String command = "cmd deviceidle whitelist " + (enabled ? "+" : "-") + packageName;
        CLog.d("Output for command " + command + ": "
                + getDevice().executeShellCommand(command));
    }

    private String changeCrossProfileWidgetForUser(String packageName, String command, int userId)
            throws DeviceNotAvailableException {
        String adbCommand = "am start -W --user " + userId
                + " -c android.intent.category.DEFAULT "
                + " --es extra-command " + command
                + " --es extra-package-name " + packageName
                + " " + MANAGED_PROFILE_PKG + "/.SetPolicyActivity";
        String commandOutput = getDevice().executeShellCommand(adbCommand);
        CLog.d("Output for command " + adbCommand + ": " + commandOutput);
        return commandOutput;
    }

    // status should be one of never, undefined, ask, always
    private void changeVerificationStatus(int userId, String packageName, String status)
            throws DeviceNotAvailableException {
        String command = "pm set-app-link --user " + userId + " " + packageName + " " + status;
        CLog.d("Output for command " + command + ": "
                + getDevice().executeShellCommand(command));
    }

    protected void startWidgetHostService() throws Exception {
        String command = "am startservice --user " + mParentUserId
                + " -a " + WIDGET_PROVIDER_PKG + ".REGISTER_CALLBACK "
                + "--ei user-extra " + getUserSerialNumber(mProfileUserId)
                + " " + WIDGET_PROVIDER_PKG + "/.SimpleAppWidgetHostService";
        CLog.d("Output for command " + command + ": "
              + getDevice().executeShellCommand(command));
    }

    private void assertAppLinkResult(String methodName) throws DeviceNotAvailableException {
        runDeviceTestsAsUser(INTENT_SENDER_PKG, ".AppLinkTest", methodName,
                mProfileUserId);
    }

    private boolean shouldRunTelecomTest() throws DeviceNotAvailableException {
        return hasDeviceFeature(FEATURE_TELEPHONY) && hasDeviceFeature(FEATURE_CONNECTION_SERVICE);
    }

    private void runManagedContactsTest(Callable<Void> callable) throws Exception {
        if (!mHasFeature) {
            return;
        }

        try {
            // Allow cross profile contacts search.
            // TODO test both on and off.
            getDevice().executeShellCommand(
                    "settings put --user " + mProfileUserId
                    + " secure managed_profile_contact_remote_search 1");

            // Add test account
            runDeviceTestsAsUser(MANAGED_PROFILE_PKG, ".ContactsTest",
                    "testAddTestAccount", mParentUserId);
            runDeviceTestsAsUser(MANAGED_PROFILE_PKG, ".ContactsTest",
                    "testAddTestAccount", mProfileUserId);

            // Install directory provider to both primary and managed profile
            installAppAsUser(DIRECTORY_PROVIDER_APK, mProfileUserId);
            installAppAsUser(DIRECTORY_PROVIDER_APK, mParentUserId);
            setDirectoryPrefix(PRIMARY_DIRECTORY_PREFIX, mParentUserId);
            setDirectoryPrefix(MANAGED_DIRECTORY_PREFIX, mProfileUserId);

            // Check enterprise directory API works
            runDeviceTestsAsUser(MANAGED_PROFILE_PKG, ".ContactsTest",
                    "testGetDirectoryListInPrimaryProfile", mParentUserId);

            // Insert Primary profile Contacts
            runDeviceTestsAsUser(MANAGED_PROFILE_PKG, ".ContactsTest",
                    "testPrimaryProfilePhoneAndEmailLookup_insertedAndfound", mParentUserId);
            // Insert Managed profile Contacts
            runDeviceTestsAsUser(MANAGED_PROFILE_PKG, ".ContactsTest",
                    "testManagedProfilePhoneAndEmailLookup_insertedAndfound", mProfileUserId);
            // Insert a primary contact with same phone & email as other
            // enterprise contacts
            runDeviceTestsAsUser(MANAGED_PROFILE_PKG, ".ContactsTest",
                    "testPrimaryProfileDuplicatedPhoneEmailContact_insertedAndfound",
                    mParentUserId);
            // Insert a enterprise contact with same phone & email as other
            // primary contacts
            runDeviceTestsAsUser(MANAGED_PROFILE_PKG, ".ContactsTest",
                    "testManagedProfileDuplicatedPhoneEmailContact_insertedAndfound",
                    mProfileUserId);

            callable.call();

        } finally {
            // Clean up in managed profile and primary profile
            runDeviceTestsAsUser(MANAGED_PROFILE_PKG, ".ContactsTest",
                    "testCurrentProfileContacts_removeContacts", mProfileUserId);
            runDeviceTestsAsUser(MANAGED_PROFILE_PKG, ".ContactsTest",
                    "testCurrentProfileContacts_removeContacts", mParentUserId);
            getDevice().uninstallPackage(DIRECTORY_PROVIDER_PKG);
        }
    }


    /*
     * Container for running ContactsTest under multi-user environment
     */
    private static class ContactsTestSet {

        private ManagedProfileTest mManagedProfileTest;
        private String mManagedProfilePackage;
        private int mParentUserId;
        private int mProfileUserId;

        public ContactsTestSet(ManagedProfileTest managedProfileTest, String managedProfilePackage,
                int parentUserId, int profileUserId) {
            mManagedProfileTest = managedProfileTest;
            mManagedProfilePackage = managedProfilePackage;
            mParentUserId = parentUserId;
            mProfileUserId = profileUserId;
        }

        private void runDeviceTestsAsUser(String pkgName, String testClassName,
                String testMethodName, Integer userId) throws DeviceNotAvailableException {
            mManagedProfileTest.runDeviceTestsAsUser(pkgName, testClassName, testMethodName,
                    userId);
        }

        // Enable / Disable cross profile caller id
        public void setCallerIdEnabled(boolean enabled) throws DeviceNotAvailableException {
            if (enabled) {
                runDeviceTestsAsUser(mManagedProfilePackage, ".ContactsTest",
                        "testSetCrossProfileCallerIdDisabled_false", mProfileUserId);
            } else {
                runDeviceTestsAsUser(mManagedProfilePackage, ".ContactsTest",
                        "testSetCrossProfileCallerIdDisabled_true", mProfileUserId);
            }
        }

        // Enable / Disable cross profile contacts search
        public void setContactsSearchEnabled(boolean enabled) throws DeviceNotAvailableException {
            if (enabled) {
                runDeviceTestsAsUser(mManagedProfilePackage, ".ContactsTest",
                        "testSetCrossProfileContactsSearchDisabled_false", mProfileUserId);
            } else {
                runDeviceTestsAsUser(mManagedProfilePackage, ".ContactsTest",
                        "testSetCrossProfileContactsSearchDisabled_true", mProfileUserId);
            }
        }

        public void checkIfCanLookupEnterpriseContacts(boolean expected)
                throws DeviceNotAvailableException {
            // Primary user cannot use ordinary phone/email lookup api to access
            // managed contacts
            runDeviceTestsAsUser(mManagedProfilePackage, ".ContactsTest",
                    "testPrimaryProfilePhoneLookup_canNotAccessEnterpriseContact", mParentUserId);
            runDeviceTestsAsUser(mManagedProfilePackage, ".ContactsTest",
                    "testPrimaryProfileEmailLookup_canNotAccessEnterpriseContact", mParentUserId);
            // Primary user can use ENTERPRISE_CONTENT_FILTER_URI to access
            // primary contacts
            runDeviceTestsAsUser(mManagedProfilePackage, ".ContactsTest",
                    "testPrimaryProfileEnterprisePhoneLookup_canAccessPrimaryContact",
                    mParentUserId);
            runDeviceTestsAsUser(mManagedProfilePackage, ".ContactsTest",
                    "testPrimaryProfileEnterpriseEmailLookup_canAccessPrimaryContact",
                    mParentUserId);
            // When there exist contacts with the same phone/email in primary &
            // enterprise,
            // primary user can use ENTERPRISE_CONTENT_FILTER_URI to access the
            // primary contact.
            runDeviceTestsAsUser(mManagedProfilePackage, ".ContactsTest",
                    "testPrimaryProfileEnterpriseEmailLookupDuplicated_canAccessPrimaryContact",
                    mParentUserId);
            runDeviceTestsAsUser(mManagedProfilePackage, ".ContactsTest",
                    "testPrimaryProfileEnterprisePhoneLookupDuplicated_canAccessPrimaryContact",
                    mParentUserId);

            // Managed user cannot use ordinary phone/email lookup api to access
            // primary contacts
            runDeviceTestsAsUser(mManagedProfilePackage, ".ContactsTest",
                    "testManagedProfilePhoneLookup_canNotAccessPrimaryContact", mProfileUserId);
            runDeviceTestsAsUser(mManagedProfilePackage, ".ContactsTest",
                    "testManagedProfileEmailLookup_canNotAccessPrimaryContact", mProfileUserId);
            // Managed user can use ENTERPRISE_CONTENT_FILTER_URI to access
            // enterprise contacts
            runDeviceTestsAsUser(mManagedProfilePackage, ".ContactsTest",
                    "testManagedProfileEnterprisePhoneLookup_canAccessEnterpriseContact",
                    mProfileUserId);
            runDeviceTestsAsUser(mManagedProfilePackage, ".ContactsTest",
                    "testManagedProfileEnterpriseEmailLookup_canAccessEnterpriseContact",
                    mProfileUserId);
            // Managed user cannot use ENTERPRISE_CONTENT_FILTER_URI to access
            // primary contacts
            runDeviceTestsAsUser(mManagedProfilePackage, ".ContactsTest",
                    "testManagedProfileEnterprisePhoneLookup_canNotAccessPrimaryContact",
                    mProfileUserId);
            runDeviceTestsAsUser(mManagedProfilePackage, ".ContactsTest",
                    "testManagedProfileEnterpriseEmailLookup_canNotAccessPrimaryContact",
                    mProfileUserId);
            // When there exist contacts with the same phone/email in primary &
            // enterprise,
            // managed user can use ENTERPRISE_CONTENT_FILTER_URI to access the
            // enterprise contact.
            runDeviceTestsAsUser(mManagedProfilePackage, ".ContactsTest",
                    "testManagedProfileEnterpriseEmailLookupDuplicated_canAccessEnterpriseContact",
                    mProfileUserId);
            runDeviceTestsAsUser(mManagedProfilePackage, ".ContactsTest",
                    "testManagedProfileEnterprisePhoneLookupDuplicated_canAccessEnterpriseContact",
                    mProfileUserId);

            // Check if phone lookup can access primary directories
            runDeviceTestsAsUser(mManagedProfilePackage, ".ContactsTest",
                    "testPrimaryProfileEnterprisePhoneLookup_canAccessPrimaryDirectories",
                    mParentUserId);

            // Check if email lookup can access primary directories
            runDeviceTestsAsUser(mManagedProfilePackage, ".ContactsTest",
                    "testPrimaryProfileEnterpriseEmailLookup_canAccessPrimaryDirectories",
                    mParentUserId);

            if (expected) {
                // Primary user can use ENTERPRISE_CONTENT_FILTER_URI to access
                // managed profile contacts
                runDeviceTestsAsUser(mManagedProfilePackage, ".ContactsTest",
                        "testPrimaryProfileEnterprisePhoneLookup_canAccessEnterpriseContact",
                        mParentUserId);
                runDeviceTestsAsUser(mManagedProfilePackage, ".ContactsTest",
                        "testPrimaryProfileEnterpriseEmailLookup_canAccessEnterpriseContact",
                        mParentUserId);

                // Make sure SIP enterprise lookup works too.
                runDeviceTestsAsUser(mManagedProfilePackage, ".ContactsTest",
                        "testPrimaryProfileEnterpriseSipLookup_canAccessEnterpriseContact",
                        mParentUserId);

                // Check if phone lookup can access enterprise directories
                runDeviceTestsAsUser(mManagedProfilePackage, ".ContactsTest",
                        "testPrimaryProfileEnterprisePhoneLookup_canAccessManagedDirectories",
                        mParentUserId);

                // Check if email lookup can access enterprise directories
                runDeviceTestsAsUser(mManagedProfilePackage, ".ContactsTest",
                        "testPrimaryProfileEnterpriseEmailLookup_canAccessManagedDirectories",
                        mParentUserId);
            } else {
                // Primary user cannot use ENTERPRISE_CONTENT_FILTER_URI to
                // access managed contacts
                runDeviceTestsAsUser(mManagedProfilePackage, ".ContactsTest",
                        "testPrimaryProfileEnterprisePhoneLookup_canNotAccessEnterpriseContact",
                        mParentUserId);
                runDeviceTestsAsUser(mManagedProfilePackage, ".ContactsTest",
                        "testPrimaryProfileEnterprisePhoneLookup_canNotAccessManagedDirectories",
                        mParentUserId);

                runDeviceTestsAsUser(mManagedProfilePackage, ".ContactsTest",
                        "testPrimaryProfileEnterpriseEmailLookup_canNotAccessManagedDirectories",
                        mParentUserId);
                runDeviceTestsAsUser(mManagedProfilePackage, ".ContactsTest",
                        "testPrimaryProfileEnterprisePhoneLookup_canNotAccessManagedDirectories",
                        mParentUserId);
            }
        }

        public void checkIfCanFilterSelfContacts() throws DeviceNotAvailableException {
            runDeviceTestsAsUser(mManagedProfilePackage, ".ContactsTest",
                    "testPrimaryProfileEnterpriseCallableFilter_canAccessPrimaryDirectories",
                    mParentUserId);
            runDeviceTestsAsUser(mManagedProfilePackage, ".ContactsTest",
                    "testManagedProfileEnterpriseCallableFilter_canAccessManagedDirectories",
                    mProfileUserId);

            runDeviceTestsAsUser(mManagedProfilePackage, ".ContactsTest",
                    "testPrimaryProfileEnterpriseEmailFilter_canAccessPrimaryDirectories",
                    mParentUserId);
            runDeviceTestsAsUser(mManagedProfilePackage, ".ContactsTest",
                    "testEnterpriseProfileEnterpriseEmailFilter_canAccessManagedDirectories",
                    mProfileUserId);

            runDeviceTestsAsUser(mManagedProfilePackage, ".ContactsTest",
                    "testPrimaryProfileEnterpriseContactFilter_canAccessPrimaryDirectories",
                    mParentUserId);
            runDeviceTestsAsUser(mManagedProfilePackage, ".ContactsTest",
                    "testManagedProfileEnterpriseContactFilter_canAccessManagedDirectories",
                    mProfileUserId);

            runDeviceTestsAsUser(mManagedProfilePackage, ".ContactsTest",
                    "testPrimaryProfileEnterprisePhoneFilter_canAccessPrimaryDirectories",
                    mParentUserId);
            runDeviceTestsAsUser(mManagedProfilePackage, ".ContactsTest",
                    "testManagedProfileEnterprisePhoneFilter_canAccessManagedDirectories",
                    mProfileUserId);
        }

        public void checkIfCanFilterEnterpriseContacts(boolean expected)
                throws DeviceNotAvailableException {
            runDeviceTestsAsUser(mManagedProfilePackage, ".ContactsTest",
                    "testFilterUriWhenDirectoryParamMissing", mParentUserId);
            if (expected) {
                runDeviceTestsAsUser(mManagedProfilePackage, ".ContactsTest",
                        "testPrimaryProfileEnterpriseCallableFilter_canAccessManagedDirectories",
                        mParentUserId);
                runDeviceTestsAsUser(mManagedProfilePackage, ".ContactsTest",
                        "testPrimaryProfileEnterpriseEmailFilter_canAccessManagedDirectories",
                        mParentUserId);
                runDeviceTestsAsUser(mManagedProfilePackage, ".ContactsTest",
                        "testPrimaryProfileEnterpriseContactFilter_canAccessManagedDirectories",
                        mParentUserId);
                runDeviceTestsAsUser(mManagedProfilePackage, ".ContactsTest",
                        "testPrimaryProfileEnterprisePhoneFilter_canAccessManagedDirectories",
                        mParentUserId);
            } else {
                runDeviceTestsAsUser(mManagedProfilePackage, ".ContactsTest",
                        "testPrimaryProfileEnterpriseCallableFilter_canNotAccessManagedDirectories",
                        mParentUserId);
                runDeviceTestsAsUser(mManagedProfilePackage, ".ContactsTest",
                        "testPrimaryProfileEnterpriseEmailFilter_canNotAccessManagedDirectories",
                        mParentUserId);
                runDeviceTestsAsUser(mManagedProfilePackage, ".ContactsTest",
                        "testPrimaryProfileEnterpriseContactFilter_canNotAccessManagedDirectories",
                        mParentUserId);
                runDeviceTestsAsUser(mManagedProfilePackage, ".ContactsTest",
                        "testPrimaryProfileEnterprisePhoneFilter_canNotAccessManagedDirectories",
                        mParentUserId);
            }
        }

        public void checkIfNoEnterpriseDirectoryFound() throws DeviceNotAvailableException {
            runDeviceTestsAsUser(mManagedProfilePackage, ".ContactsTest",
                    "testPrimaryProfileEnterpriseDirectories_canNotAccessManagedDirectories",
                    mParentUserId);
        }
    }
}
