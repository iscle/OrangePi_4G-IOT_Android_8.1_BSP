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

import com.android.tradefed.device.DeviceNotAvailableException;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

public class UserRestrictionsTest extends BaseDevicePolicyTest {
    private static final String DEVICE_ADMIN_PKG = "com.android.cts.deviceandprofileowner";
    private static final String DEVICE_ADMIN_APK = "CtsDeviceAndProfileOwnerApp.apk";
    private static final String ADMIN_RECEIVER_TEST_CLASS
            = ".BaseDeviceAdminTest$BasicAdminReceiver";

    private static final String GLOBAL_RESTRICTIONS_TEST_CLASS =
            "userrestrictions.ProfileGlobalRestrictionsTest";
    private static final String SET_GLOBAL_RESTRICTIONS_TEST =
            "testSetProfileGlobalRestrictions";
    private static final String CLEAR_GLOBAL_RESTRICTIONS_TEST =
            "testClearProfileGlobalRestrictions";
    private static final String ENSURE_GLOBAL_RESTRICTIONS_TEST =
            "testProfileGlobalRestrictionsEnforced";
    private static final String ENSURE_NO_GLOBAL_RESTRICTIONS_TEST =
            "testProfileGlobalRestrictionsNotEnforced";

    private boolean mRemoveOwnerInTearDown;
    private int mDeviceOwnerUserId;

    @Override
    protected void setUp() throws Exception {
        super.setUp();

        mRemoveOwnerInTearDown = false;
        mDeviceOwnerUserId = mPrimaryUserId;
    }

    @Override
    protected void tearDown() throws Exception {
        if (mHasFeature) {
            if (mRemoveOwnerInTearDown) {
                assertTrue("Failed to clear owner",
                        removeAdmin(DEVICE_ADMIN_PKG + "/" + ADMIN_RECEIVER_TEST_CLASS,
                                mDeviceOwnerUserId));
                runTests("userrestrictions.CheckNoOwnerRestrictionsTest", mDeviceOwnerUserId);
            }

            // DO/PO might have set DISALLOW_REMOVE_USER, so it needs to be done after removing
            // them.
            removeTestUsers();
            getDevice().uninstallPackage(DEVICE_ADMIN_PKG);
        }
        super.tearDown();
    }

    private void runTests(@Nonnull String className,
            @Nullable String method, int userId) throws DeviceNotAvailableException {
        runDeviceTestsAsUser(DEVICE_ADMIN_PKG, "." + className, method, userId);
    }

    private void runTests(@Nonnull String className, int userId)
            throws DeviceNotAvailableException {
        runTests(className, null, userId);
    }

    public void testUserRestrictions_deviceOwnerOnly() throws Exception {
        if (!mHasFeature) {
            return;
        }
        setDo();

        runTests("userrestrictions.DeviceOwnerUserRestrictionsTest",
                "testDefaultRestrictions", mDeviceOwnerUserId);
        runTests("userrestrictions.DeviceOwnerUserRestrictionsTest",
                "testSetAllRestrictions", mDeviceOwnerUserId);
        runTests("userrestrictions.DeviceOwnerUserRestrictionsTest",
                "testBroadcast", mDeviceOwnerUserId);
    }

    public void testUserRestrictions_primaryProfileOwnerOnly() throws Exception {
        if (!mHasFeature) {
            return;
        }
        if (hasUserSplit()) {
            // Can't set PO on user-0 in this mode.
            return;
        }

        setPoAsUser(mDeviceOwnerUserId);

        runTests("userrestrictions.PrimaryProfileOwnerUserRestrictionsTest",
                "testDefaultRestrictions", mDeviceOwnerUserId);
        runTests("userrestrictions.PrimaryProfileOwnerUserRestrictionsTest",
                "testSetAllRestrictions", mDeviceOwnerUserId);
        runTests("userrestrictions.PrimaryProfileOwnerUserRestrictionsTest",
                "testBroadcast", mDeviceOwnerUserId);
    }

    // Checks restrictions for managed user (NOT managed profile).
    public void testUserRestrictions_secondaryProfileOwnerOnly() throws Exception {
        if (!mHasFeature || !mSupportsMultiUser) {
            return;
        }
        final int secondaryUserId = createUser();
        setPoAsUser(secondaryUserId);

        runTests("userrestrictions.SecondaryProfileOwnerUserRestrictionsTest",
                "testDefaultRestrictions", secondaryUserId);
        runTests("userrestrictions.SecondaryProfileOwnerUserRestrictionsTest",
                "testSetAllRestrictions", secondaryUserId);
        runTests("userrestrictions.SecondaryProfileOwnerUserRestrictionsTest",
                "testBroadcast", secondaryUserId);
    }

    // Checks restrictions for managed profile.
    public void testUserRestrictions_managedProfileOwnerOnly() throws Exception {
        if (!mHasFeature || !mSupportsMultiUser) {
            return;
        }

        // Create managed profile.
        final int profileUserId = createManagedProfile(mDeviceOwnerUserId /* parentUserId */);
        // createManagedProfile doesn't start the user automatically.
        startUser(profileUserId);
        setPoAsUser(profileUserId);

        runTests("userrestrictions.ManagedProfileOwnerUserRestrictionsTest",
                "testDefaultRestrictions", profileUserId);
        runTests("userrestrictions.ManagedProfileOwnerUserRestrictionsTest",
                "testSetAllRestrictions", profileUserId);
        runTests("userrestrictions.ManagedProfileOwnerUserRestrictionsTest",
                "testBroadcast", profileUserId);
    }

    /**
     * DO + PO combination.  Make sure global DO restrictions are visible on secondary users.
     */
    public void testUserRestrictions_layering() throws Exception {
        if (!mHasFeature || !mSupportsMultiUser) {
            return;
        }
        setDo();

        // Create another user and set PO.
        final int secondaryUserId = createUser();
        setPoAsUser(secondaryUserId);

        // Let DO set all restrictions.
        runTests("userrestrictions.DeviceOwnerUserRestrictionsTest",
                "testSetAllRestrictions", mDeviceOwnerUserId);

        // Make sure the global restrictions are visible to secondary users.
        runTests("userrestrictions.SecondaryProfileOwnerUserRestrictionsTest",
                "testHasGlobalRestrictions", secondaryUserId);

        // Then let PO set all restrictions.
        runTests("userrestrictions.SecondaryProfileOwnerUserRestrictionsTest",
                "testSetAllRestrictions", secondaryUserId);

        // Make sure both local and global restrictions are visible on secondary users.
        runTests("userrestrictions.SecondaryProfileOwnerUserRestrictionsTest",
                "testHasBothGlobalAndLocalRestrictions", secondaryUserId);

        // Let DO clear all restrictions.
        runTests("userrestrictions.DeviceOwnerUserRestrictionsTest",
                "testClearAllRestrictions", mDeviceOwnerUserId);

        // Now only PO restrictions should be set on the secondary user.
        runTests("userrestrictions.SecondaryProfileOwnerUserRestrictionsTest",
                "testLocalRestrictionsOnly", secondaryUserId);
    }

    /**
     * PO on user-0.  It can set DO restrictions too, but they shouldn't leak to other users.
     */
    public void testUserRestrictions_layering_profileOwnerNoLeaking() throws Exception {
        if (!mHasFeature || !mSupportsMultiUser) {
            return;
        }
        if (hasUserSplit()) {
            // Can't set PO on user-0 in this mode.
            return;
        }
        // Set PO on user 0
        setPoAsUser(mDeviceOwnerUserId);

        // Create another user and set PO.
        final int secondaryUserId = createUser();
        setPoAsUser(secondaryUserId);

        // Let user-0 PO sets all restrictions.
        runTests("userrestrictions.PrimaryProfileOwnerUserRestrictionsTest",
                "testSetAllRestrictions", mDeviceOwnerUserId);

        // Secondary users shouldn't see any of them.
        runTests("userrestrictions.SecondaryProfileOwnerUserRestrictionsTest",
                "testDefaultRestrictionsOnly", secondaryUserId);
    }

    /**
     * DO sets profile global restrictions (only ENSURE_VERIFY_APPS), should affect all
     * users (not a particularly special case but to be sure).
     */
    public void testUserRestrictions_profileGlobalRestrictionsAsDo() throws Exception {
        if (!mHasFeature || !mSupportsMultiUser) {
            return;
        }
        setDo();

        // Create another user with PO.
        final int secondaryUserId = createUser();
        setPoAsUser(secondaryUserId);

        final int[] usersToCheck = {mDeviceOwnerUserId, secondaryUserId};

        // Do sets the restriction.
        setAndCheckProfileGlobalRestriction(mDeviceOwnerUserId, usersToCheck);
    }

    /**
     * Managed profile owner sets profile global restrictions (only ENSURE_VERIFY_APPS), should
     * affect all users.
     */
    public void testUserRestrictions_ProfileGlobalRestrictionsAsPo() throws Exception {
        if (!mHasFeature || !mSupportsMultiUser) {
            return;
        }
        // Set PO on user 0
        setPoAsUser(mDeviceOwnerUserId);

        // Create another user with PO.
        final int secondaryUserId = createManagedProfile(mDeviceOwnerUserId /* parentUserId */);
        setPoAsUser(secondaryUserId);

        final int[] usersToCheck = {mDeviceOwnerUserId, secondaryUserId};

        // Check the case when primary user's PO sets the restriction.
        setAndCheckProfileGlobalRestriction(mDeviceOwnerUserId, usersToCheck);

        // Check the case when managed profile owner sets the restriction.
        setAndCheckProfileGlobalRestriction(secondaryUserId, usersToCheck);
    }

    /** Installs admin package and makes it a profile owner for a given user. */
    private void setPoAsUser(int userId) throws Exception {
        installAppAsUser(DEVICE_ADMIN_APK, userId);
        assertTrue("Failed to set profile owner",
                setProfileOwner(DEVICE_ADMIN_PKG + "/" + ADMIN_RECEIVER_TEST_CLASS,
                        userId, /* expectFailure */ false));
        // If PO is not in primary user, it will be removed with the user.
        if (userId == mDeviceOwnerUserId) {
            mRemoveOwnerInTearDown = true;
        }
    }

    /** Installs admin package and makes it a device owner. */
    private void setDo() throws Exception {
        installAppAsUser(DEVICE_ADMIN_APK, mDeviceOwnerUserId);
        assertTrue("Failed to set device owner",
                setDeviceOwner(DEVICE_ADMIN_PKG + "/" + ADMIN_RECEIVER_TEST_CLASS,
                        mDeviceOwnerUserId, /*expectFailure*/ false));
        mRemoveOwnerInTearDown = true;
    }

    /**
     * Sets user restriction and checks that it applies to all users.
     * @param enforcingUserId user who should set/clear the restriction, should be either
     *        primary or secondary user id and should have device or profile owner active.
     * @param usersToCheck users that should have this restriction enforced.
     */
    private void setAndCheckProfileGlobalRestriction(int enforcingUserId, int usersToCheck[])
            throws Exception {
        // Always try to clear the restriction to avoid undesirable side effects.
        try {
            // Set the restriction.
            runGlobalRestrictionsTest(SET_GLOBAL_RESTRICTIONS_TEST, enforcingUserId);
            // Check that the restriction is in power.
            for (int userId : usersToCheck) {
                runGlobalRestrictionsTest(ENSURE_GLOBAL_RESTRICTIONS_TEST, userId);
            }
        } finally {
            // Clear the restriction.
            runGlobalRestrictionsTest(CLEAR_GLOBAL_RESTRICTIONS_TEST, enforcingUserId);
            // Check that the restriction is not in power anymore.
            for (int userId : usersToCheck) {
                runGlobalRestrictionsTest(ENSURE_NO_GLOBAL_RESTRICTIONS_TEST, userId);
            }
        }
    }

    /** Convenience method to run global user restrictions tests. */
    private void runGlobalRestrictionsTest(String testMethodName, int userId) throws Exception {
        runTests(GLOBAL_RESTRICTIONS_TEST_CLASS, testMethodName, userId);
    }
}
