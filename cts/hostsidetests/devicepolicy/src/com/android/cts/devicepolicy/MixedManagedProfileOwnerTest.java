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

/**
 * Set of tests for managed profile owner use cases that also apply to device owners.
 * Tests that should be run identically in both cases are added in DeviceAndProfileOwnerTest.
 */
public class MixedManagedProfileOwnerTest extends DeviceAndProfileOwnerTest {

    protected static final String CLEAR_PROFILE_OWNER_NEGATIVE_TEST_CLASS =
            DEVICE_ADMIN_PKG + ".ClearProfileOwnerNegativeTest";

    private int mParentUserId = -1;

    @Override
    protected void setUp() throws Exception {
        super.setUp();

        // We need managed users to be supported in order to create a profile of the user owner.
        mHasFeature &= hasDeviceFeature("android.software.managed_users");

        if (mHasFeature) {
            removeTestUsers();
            mParentUserId = mPrimaryUserId;
            createManagedProfile();
        }
    }

    private void createManagedProfile() throws Exception {
        mUserId = createManagedProfile(mParentUserId);
        switchUser(mParentUserId);
        startUser(mUserId);

        installAppAsUser(DEVICE_ADMIN_APK, mUserId);
        setProfileOwnerOrFail(DEVICE_ADMIN_PKG + "/" + ADMIN_RECEIVER_TEST_CLASS, mUserId);
        startUser(mUserId);
    }

    @Override
    protected void tearDown() throws Exception {
        if (mHasFeature) {
            removeUser(mUserId);
        }
        super.tearDown();
    }

    // Most tests for this class are defined in DeviceAndProfileOwnerTest

    /**
     * Verify that screenshots are still possible for activities in the primary user when the policy
     * is set on the profile owner.
     */
    public void testScreenCaptureDisabled_allowedPrimaryUser() throws Exception {
        if (!mHasFeature) {
            return;
        }
        // disable screen capture in profile
        setScreenCaptureDisabled(mUserId, true);

        // start the ScreenCaptureDisabledActivity in the parent
        installAppAsUser(DEVICE_ADMIN_APK, mParentUserId);
        startSimpleActivityAsUser(mParentUserId);
        executeDeviceTestMethod(".ScreenCaptureDisabledTest", "testScreenCapturePossible");
    }

    public void testScreenCaptureDisabled_assist_allowedPrimaryUser() throws Exception {
        if (!mHasFeature) {
            return;
        }
        // disable screen capture in profile
        executeDeviceTestMethod(".ScreenCaptureDisabledTest", "testSetScreenCaptureDisabled_true");
        try {
            // Install and enable assistant in personal side.
            installAppAsUser(ASSIST_APP_APK, mParentUserId);
            setVoiceInteractionService(ASSIST_INTERACTION_SERVICE);

            // Start an activity in parent user.
            installAppAsUser(DEVICE_ADMIN_APK, mParentUserId);
            startSimpleActivityAsUser(mParentUserId);

            // Verify assistant app can't take a screenshot.
            runDeviceTestsAsUser(
                    DEVICE_ADMIN_PKG,
                    ".AssistScreenCaptureDisabledTest",
                    "testScreenCapturePossible_assist",
                    mPrimaryUserId);
        } finally {
            // enable screen capture in profile
            executeDeviceTestMethod(
                    ".ScreenCaptureDisabledTest",
                    "testSetScreenCaptureDisabled_false");
            clearVoiceInteractionService();
        }
    }

    @Override
    public void testDisallowSetWallpaper_allowed() throws Exception {
        // Managed profile doesn't have wallpaper.
    }

    @Override
    public void testAudioRestriction() throws Exception {
        // DISALLOW_UNMUTE_MICROPHONE and DISALLOW_ADJUST_VOLUME can only be set by device owners
        // and profile owners on the primary user.
    }

    /** VPN tests don't require physical device for managed profile, thus overriding. */
    @Override
    public void testAlwaysOnVpn() throws Exception {
        super.testAlwaysOnVpn();
    }

    /** VPN tests don't require physical device for managed profile, thus overriding. */
    @Override
    public void testAlwaysOnVpnLockDown() throws Exception {
        super.testAlwaysOnVpnLockDown();
    }

    /** VPN tests don't require physical device for managed profile, thus overriding. */
    @Override
    public void testAlwaysOnVpnAcrossReboot() throws Exception {
        super.testAlwaysOnVpnAcrossReboot();
    }

    /** VPN tests don't require physical device for managed profile, thus overriding. */
    @Override
    public void testAlwaysOnVpnPackageUninstalled() throws Exception {
        super.testAlwaysOnVpnPackageUninstalled();
    }

    /** VPN tests don't require physical device for managed profile, thus overriding. */
    @Override
    public void testAlwaysOnVpnUnsupportedPackage() throws Exception {
        super.testAlwaysOnVpnUnsupportedPackage();
    }

    /** VPN tests don't require physical device for managed profile, thus overriding. */
    @Override
    public void testAlwaysOnVpnUnsupportedPackageReplaced() throws Exception {
        super.testAlwaysOnVpnUnsupportedPackageReplaced();
    }

    @Override
    public void testResetPasswordWithToken() throws Exception {
        if (!mHasFeature) {
            return;
        }
        // Execute the test method that's guaranteed to succeed. See also test in base class
        // which are tolerant to failure and executed by MixedDeviceOwnerTest and
        // MixedProfileOwnerTest
        executeResetPasswordWithTokenTests(false);
    }
}
