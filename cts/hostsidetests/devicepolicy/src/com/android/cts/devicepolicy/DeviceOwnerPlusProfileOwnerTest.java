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

import java.util.List;

/**
 * Tests for having both device owner and profile owner. Device owner is setup for you in
 * {@link #setUp()} and it is always the {@link #COMP_DPC_PKG}. You are required to call
 * {@link #setupManagedProfile} or {@link #setupManagedSecondaryUser} yourself to create another
 * profile/user on each test case.
 */
public class DeviceOwnerPlusProfileOwnerTest extends BaseDevicePolicyTest {
    private static final String BIND_DEVICE_ADMIN_SERVICE_GOOD_SETUP_TEST =
            "com.android.cts.comp.BindDeviceAdminServiceGoodSetupTest";
    private static final String MANAGED_PROFILE_PROVISIONING_TEST =
            "com.android.cts.comp.provisioning.ManagedProfileProvisioningTest";
    private static final String BIND_DEVICE_ADMIN_SERVICE_FAILS_TEST =
            "com.android.cts.comp.BindDeviceAdminServiceFailsTest";
    private static final String DEVICE_WIDE_LOGGING_TEST =
            "com.android.cts.comp.DeviceWideLoggingFeaturesTest";
    private static final String AFFILIATION_TEST =
            "com.android.cts.comp.provisioning.AffiliationTest";
    private static final String USER_RESTRICTION_TEST =
            "com.android.cts.comp.provisioning.UserRestrictionTest";
    private static final String MANAGEMENT_TEST =
            "com.android.cts.comp.ManagementTest";

    private static final String COMP_DPC_PKG = "com.android.cts.comp";
    private static final String COMP_DPC_APK = "CtsCorpOwnedManagedProfile.apk";
    private static final String COMP_DPC_ADMIN =
            COMP_DPC_PKG + "/com.android.cts.comp.AdminReceiver";
    private static final String COMP_DPC_PKG2 = "com.android.cts.comp2";
    private static final String COMP_DPC_APK2 = "CtsCorpOwnedManagedProfile2.apk";
    private static final String COMP_DPC_ADMIN2 =
            COMP_DPC_PKG2 + "/com.android.cts.comp.AdminReceiver";

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        // We need managed user to be supported in order to create a profile of the user owner.
        mHasFeature = mHasFeature && hasDeviceFeature("android.software.managed_users");
        if (mHasFeature) {
            // Set device owner.
            installAppAsUser(COMP_DPC_APK, mPrimaryUserId);
            if (!setDeviceOwner(COMP_DPC_ADMIN, mPrimaryUserId, /*expectFailure*/ false)) {
                removeAdmin(COMP_DPC_ADMIN, mPrimaryUserId);
                fail("Failed to set device owner");
            }
            runDeviceTestsAsUser(
                    COMP_DPC_PKG,
                    MANAGEMENT_TEST,
                    "testIsDeviceOwner",
                    mPrimaryUserId);
        }
    }

    @Override
    protected void tearDown() throws Exception {
        if (mHasFeature) {
            assertTrue("Failed to remove device owner.",
                    removeAdmin(COMP_DPC_ADMIN, mPrimaryUserId));
        }

        super.tearDown();
    }

    /**
     * Both device owner and profile are the same package ({@link #COMP_DPC_PKG}).
     */
    public void testBindDeviceAdminServiceAsUser_corpOwnedManagedProfile() throws Exception {
        if (!mHasFeature) {
            return;
        }
        int profileUserId = setupManagedProfile(COMP_DPC_APK, COMP_DPC_PKG, COMP_DPC_ADMIN);

        // Not setting affiliation ids, should not be possible to bind.
        verifyBindDeviceAdminServiceAsUserFails(profileUserId);

        // Now setting the same affiliation ids, binding is allowed.
        setSameAffiliationId(profileUserId);
        assertOtherProfilesEqualsBindTargetUsers(profileUserId);
        verifyBindDeviceAdminServiceAsUser(profileUserId);

        // Setting different affiliation ids makes binding unavailable.
        setDifferentAffiliationId(profileUserId);
        verifyBindDeviceAdminServiceAsUserFails(profileUserId);
    }

    /**
     * Same as {@link #testBindDeviceAdminServiceAsUser_corpOwnedManagedProfile} except
     * creating managed profile through ManagedProvisioning like normal flow
     */
    public void testBindDeviceAdminServiceAsUser_corpOwnedManagedProfileWithManagedProvisioning()
            throws Exception {
        if (!mHasFeature) {
            return;
        }
        int profileUserId = provisionCorpOwnedManagedProfile();
        setSameAffiliationId(profileUserId);
        runDeviceTestsAsUser(
                COMP_DPC_PKG,
                MANAGED_PROFILE_PROVISIONING_TEST,
                "testEnableProfile",
                profileUserId);
        assertOtherProfilesEqualsBindTargetUsers(profileUserId);
        verifyBindDeviceAdminServiceAsUser(profileUserId);
    }

    /**
     * Same as
     * {@link #testBindDeviceAdminServiceAsUser_corpOwnedManagedProfileWithManagedProvisioning}
     * except we don't enable the profile.
     */
    public void testBindDeviceAdminServiceAsUser_canBindEvenIfProfileNotEnabled() throws Exception {
        if (!mHasFeature) {
            return;
        }
        int profileUserId = provisionCorpOwnedManagedProfile();
        setSameAffiliationId(profileUserId);
        verifyBindDeviceAdminServiceAsUser(profileUserId);
    }

    /**
     * Device owner is {@link #COMP_DPC_PKG} while profile owner is {@link #COMP_DPC_PKG2}.
     * Therefore it isn't allowed to bind to each other.
     */
    public void testBindDeviceAdminServiceAsUser_byodPlusDeviceOwnerCannotBind() throws Exception {
        if (!mHasFeature) {
            return;
        }
        int profileUserId = setupManagedProfile(COMP_DPC_APK2, COMP_DPC_PKG2, COMP_DPC_ADMIN2);

        // Setting same affiliation ids shouldn't make a difference. Binding still not allowed.
        setSameAffiliationId(profileUserId, COMP_DPC_PKG2);
        // Testing device owner -> profile owner.
        runDeviceTestsAsUser(
                COMP_DPC_PKG,
                BIND_DEVICE_ADMIN_SERVICE_FAILS_TEST,
                mPrimaryUserId);
        // Testing profile owner -> device owner.
        runDeviceTestsAsUser(
                COMP_DPC_PKG2,
                BIND_DEVICE_ADMIN_SERVICE_FAILS_TEST,
                profileUserId);
    }

    /**
     * Both device owner and profile are the same package ({@link #COMP_DPC_PKG}), as setup
     * by createAndManagedUser.
     */
    public void testBindDeviceAdminServiceAsUser_secondaryUser() throws Exception {
        if (!mHasFeature || !canCreateAdditionalUsers(1)) {
            return;
        }
        int secondaryUserId = setupManagedSecondaryUser();

        installAppAsUser(COMP_DPC_APK2, mPrimaryUserId);
        installAppAsUser(COMP_DPC_APK2, secondaryUserId);

        // Shouldn't be possible to bind to each other, as they are not affiliated.
        verifyBindDeviceAdminServiceAsUserFails(secondaryUserId);

        // Set the same affiliation ids, and check that DO and PO can now bind to each other.
        setSameAffiliationId(secondaryUserId);
        verifyBindDeviceAdminServiceAsUser(secondaryUserId);
    }

    /**
     * Test that the DO can talk to both a managed profile and managed secondary user at the same
     * time.
     */
    public void testBindDeviceAdminServiceAsUser_compPlusSecondaryUser() throws Exception {
        if (!mHasFeature || !canCreateAdditionalUsers(1)) {
            return;
        }
        int secondaryUserId = setupManagedSecondaryUser();
        int profileUserId = setupManagedProfile(COMP_DPC_APK, COMP_DPC_PKG, COMP_DPC_ADMIN);

        // Affiliate only the secondary user. The DO and the PO from that user can talk, but not
        // the DO and the PO of the un-affiliated managed profile.
        setSameAffiliationId(secondaryUserId);
        verifyBindDeviceAdminServiceAsUser(secondaryUserId);
        runDeviceTestsAsUser(
                COMP_DPC_PKG,
                BIND_DEVICE_ADMIN_SERVICE_FAILS_TEST,
                profileUserId);

        // Now affiliate the work profile - the DO is able to talk to both.
        setSameAffiliationId(profileUserId);
        verifyBindDeviceAdminServiceAsUser(profileUserId);
        verifyBindDeviceAdminServiceAsUser(secondaryUserId);
    }

    public void testCannotRemoveProfileIfRestrictionSet() throws Exception {
        if (!mHasFeature) {
            return;
        }
        int profileUserId = setupManagedProfile(COMP_DPC_APK2, COMP_DPC_PKG2, COMP_DPC_ADMIN2);
        addDisallowRemoveManagedProfileRestriction();
        assertFalse(getDevice().removeUser(profileUserId));

        clearDisallowRemoveManagedProfileRestriction();
        assertTrue(getDevice().removeUser(profileUserId));
    }

    public void testCannotRemoveUserIfRestrictionSet() throws Exception {
        if (!mHasFeature || !canCreateAdditionalUsers(1)) {
            return;
        }
        int secondaryUserId = setupManagedSecondaryUser();
        addDisallowRemoveUserRestriction();
        assertFalse(getDevice().removeUser(secondaryUserId));

        clearDisallowRemoveUserRestriction();
        assertTrue(getDevice().removeUser(secondaryUserId));
    }

    public void testCanRemoveProfileEvenIfDisallowRemoveUserSet() throws Exception {
        if (!mHasFeature) {
            return;
        }
        int profileUserId = setupManagedProfile(COMP_DPC_APK2, COMP_DPC_PKG2, COMP_DPC_ADMIN2);
        addDisallowRemoveUserRestriction();
        // DISALLOW_REMOVE_USER only affects users, not profiles.
        assertTrue(getDevice().removeUser(profileUserId));
        assertUserGetsRemoved(profileUserId);
    }

    public void testDoCanRemoveProfileEvenIfUserRestrictionSet() throws Exception {
        if (!mHasFeature) {
            return;
        }
        int profileUserId = setupManagedProfile(COMP_DPC_APK, COMP_DPC_PKG, COMP_DPC_ADMIN);
        addDisallowRemoveUserRestriction();
        addDisallowRemoveManagedProfileRestriction();

        // The DO should be allowed to remove the managed profile, even though disallow remove user
        // and disallow remove managed profile restrictions are set.
        runDeviceTestsAsUser(
                COMP_DPC_PKG,
                MANAGEMENT_TEST,
                "testCanRemoveManagedProfile",
                mPrimaryUserId);
        assertUserGetsRemoved(profileUserId);
    }

    public void testCannotAddProfileIfRestrictionSet() throws Exception {
        if (!mHasFeature) {
            return;
        }
        // by default, disallow add managed profile users restriction is set.
        assertCannotCreateManagedProfile(mPrimaryUserId);
    }

    /**
     * Both device owner and profile are the same package ({@link #COMP_DPC_PKG}).
     */
    public void testIsProvisioningAllowed() throws Exception {
        if (!mHasFeature) {
            return;
        }
        installAppAsUser(COMP_DPC_APK2, mPrimaryUserId);
        // By default, disallow add managed profile is set, so provisioning a managed profile is
        // not allowed for DPCs other than the device owner.
        assertProvisionManagedProfileNotAllowed(COMP_DPC_PKG2);
        // But the device owner can still provision a managed profile because it owns the
        // restriction.
        assertProvisionManagedProfileAllowed(COMP_DPC_PKG);

        setupManagedProfile(COMP_DPC_APK, COMP_DPC_PKG, COMP_DPC_ADMIN);

        clearDisallowAddManagedProfileRestriction();
        // We've created a managed profile, but it's still possible to delete it to create a new
        // one.
        assertProvisionManagedProfileAllowed(COMP_DPC_PKG2);
        assertProvisionManagedProfileAllowed(COMP_DPC_PKG);

        addDisallowRemoveManagedProfileRestriction();
        // Now we can't delete the managed profile any more to create a new one.
        assertProvisionManagedProfileNotAllowed(COMP_DPC_PKG2);
        // But if it is initiated by the device owner, it is still possible, because the device
        // owner itself has set the restriction
        assertProvisionManagedProfileAllowed(COMP_DPC_PKG);
    }

    public void testWipeData_managedProfile() throws Exception {
        if (!mHasFeature) {
            return;
        }
        int profileUserId = setupManagedProfile(COMP_DPC_APK, COMP_DPC_PKG, COMP_DPC_ADMIN);
        addDisallowRemoveManagedProfileRestriction();
        // The PO of the managed profile should be allowed to delete the managed profile, even
        // though the disallow remove profile restriction is set.
        runDeviceTestsAsUser(
                COMP_DPC_PKG,
                MANAGEMENT_TEST,
                "testWipeData",
                profileUserId);
        assertUserGetsRemoved(profileUserId);
    }

    public void testWipeData_secondaryUser() throws Exception {
        if (!mHasFeature || !canCreateAdditionalUsers(1)) {
            return;
        }
        int secondaryUserId = setupManagedSecondaryUser();
        addDisallowRemoveUserRestriction();
        // The PO of the managed user should be allowed to delete it, even though the disallow
        // remove user restriction is set.
        runDeviceTestsAsUser(
                COMP_DPC_PKG,
                MANAGEMENT_TEST,
                "testWipeData",
                secondaryUserId);
        assertUserGetsRemoved(secondaryUserId);
    }

    public void testNetworkAndSecurityLoggingAvailableIfAffiliated() throws Exception {
        if (!mHasFeature) {
            return;
        }

        if (canCreateAdditionalUsers(1)) {
            // If secondary users are allowed, create an affiliated one, to check that this still
            // works if having both an affiliated user and an affiliated managed profile.
            int secondaryUserId = setupManagedSecondaryUser();
            setSameAffiliationId(secondaryUserId);
        }

        // Create a managed profile for a different DPC package name, to test that the features are
        // still available as long as the users are affiliated
        int profileUserId = setupManagedProfile(COMP_DPC_APK2, COMP_DPC_PKG2, COMP_DPC_ADMIN2);

        runDeviceTestsAsUser(
                COMP_DPC_PKG,
                DEVICE_WIDE_LOGGING_TEST,
                "testEnablingNetworkAndSecurityLogging",
                mPrimaryUserId);
        try {
            // No affiliation ids have been set on the profile, the features shouldn't be available.
            runDeviceTestsAsUser(
                    COMP_DPC_PKG,
                    DEVICE_WIDE_LOGGING_TEST,
                    "testRetrievingLogsThrowsSecurityException",
                    mPrimaryUserId);

            // Affiliate the DO and the managed profile (the secondary user is already affiliated,
            // if it was added).
            setSameAffiliationId(profileUserId, COMP_DPC_PKG2);
            runDeviceTestsAsUser(
                    COMP_DPC_PKG,
                    DEVICE_WIDE_LOGGING_TEST,
                    "testRetrievingLogsDoesNotThrowException",
                    mPrimaryUserId);

            setDifferentAffiliationId(profileUserId, COMP_DPC_PKG2);
            runDeviceTestsAsUser(
                    COMP_DPC_PKG,
                    DEVICE_WIDE_LOGGING_TEST,
                    "testRetrievingLogsThrowsSecurityException",
                    mPrimaryUserId);
        } finally {
            runDeviceTestsAsUser(
                COMP_DPC_PKG,
                DEVICE_WIDE_LOGGING_TEST,
                "testDisablingNetworkAndSecurityLogging",
                mPrimaryUserId);
        }
    }

    public void testRequestBugreportAvailableIfAffiliated() throws Exception {
        if (!mHasFeature) {
            return;
        }

        if (canCreateAdditionalUsers(1)) {
            // If secondary users are allowed, create an affiliated one, to check that this still
            // works if having both an affiliated user and an affiliated managed profile.
            int secondaryUserId = setupManagedSecondaryUser();
            setSameAffiliationId(secondaryUserId);
        }

        // Create a managed profile for a different DPC package name, to test that the feature is
        // still available as long as the users are affiliated
        int profileUserId = setupManagedProfile(COMP_DPC_APK2, COMP_DPC_PKG2, COMP_DPC_ADMIN2);

        // No affiliation ids have been set on the profile, the feature shouldn't be available.
        runDeviceTestsAsUser(
                COMP_DPC_PKG,
                DEVICE_WIDE_LOGGING_TEST,
                "testRequestBugreportThrowsSecurityException",
                mPrimaryUserId);

        // Affiliate the DO and the managed profile (the secondary user is already affiliated,
        // if it was added).
        setSameAffiliationId(profileUserId, COMP_DPC_PKG2);
        runDeviceTestsAsUser(
                COMP_DPC_PKG,
                DEVICE_WIDE_LOGGING_TEST,
                "testRequestBugreportDoesNotThrowException",
                mPrimaryUserId);

        setDifferentAffiliationId(profileUserId, COMP_DPC_PKG2);
        runDeviceTestsAsUser(
                COMP_DPC_PKG,
                DEVICE_WIDE_LOGGING_TEST,
                "testRequestBugreportThrowsSecurityException",
                mPrimaryUserId);
    }

    private void verifyBindDeviceAdminServiceAsUser(int profileOwnerUserId) throws Exception {
        // Installing a non managing app (neither device owner nor profile owner).
        installAppAsUser(COMP_DPC_APK2, mPrimaryUserId);
        installAppAsUser(COMP_DPC_APK2, profileOwnerUserId);

        // Testing device owner -> profile owner.
        runDeviceTestsAsUser(
                COMP_DPC_PKG,
                BIND_DEVICE_ADMIN_SERVICE_GOOD_SETUP_TEST,
                mPrimaryUserId);
        // Testing profile owner -> device owner.
        runDeviceTestsAsUser(
                COMP_DPC_PKG,
                BIND_DEVICE_ADMIN_SERVICE_GOOD_SETUP_TEST,
                profileOwnerUserId);
    }

    private void verifyBindDeviceAdminServiceAsUserFails(int profileOwnerUserId) throws Exception {
        // Installing a non managing app (neither device owner nor profile owner).
        installAppAsUser(COMP_DPC_APK2, mPrimaryUserId);
        installAppAsUser(COMP_DPC_APK2, profileOwnerUserId);

        // Testing device owner -> profile owner.
        runDeviceTestsAsUser(
                COMP_DPC_PKG,
                BIND_DEVICE_ADMIN_SERVICE_FAILS_TEST,
                mPrimaryUserId);
        // Testing profile owner -> device owner.
        runDeviceTestsAsUser(
                COMP_DPC_PKG,
                BIND_DEVICE_ADMIN_SERVICE_FAILS_TEST,
                profileOwnerUserId);
    }

    private void setSameAffiliationId(
            int profileOwnerUserId, String profileOwnerPackage) throws Exception {
        runDeviceTestsAsUser(
                COMP_DPC_PKG,
                AFFILIATION_TEST,
                "testSetAffiliationId1",
                mPrimaryUserId);
        runDeviceTestsAsUser(
                profileOwnerPackage,
                AFFILIATION_TEST,
                "testSetAffiliationId1",
                profileOwnerUserId);
    }

    private void setSameAffiliationId(int profileOwnerUserId) throws Exception {
        setSameAffiliationId(profileOwnerUserId, COMP_DPC_PKG);
    }

    private void setDifferentAffiliationId(
            int profileOwnerUserId, String profileOwnerPackage) throws Exception {
        runDeviceTestsAsUser(
                COMP_DPC_PKG,
                AFFILIATION_TEST,
                "testSetAffiliationId1",
                mPrimaryUserId);
        runDeviceTestsAsUser(
                profileOwnerPackage,
                AFFILIATION_TEST,
                "testSetAffiliationId2",
                profileOwnerUserId);
    }

    private void setDifferentAffiliationId(int profileOwnerUserId) throws Exception {
        setDifferentAffiliationId(profileOwnerUserId, COMP_DPC_PKG);
    }

    private void assertProvisionManagedProfileAllowed(String packageName) throws Exception {
        runDeviceTestsAsUser(
                packageName,
                MANAGEMENT_TEST,
                "testProvisionManagedProfileAllowed",
                mPrimaryUserId);
    }

    private void assertProvisionManagedProfileNotAllowed(String packageName) throws Exception {
        runDeviceTestsAsUser(
                packageName,
                MANAGEMENT_TEST,
                "testProvisionManagedProfileNotAllowed",
                mPrimaryUserId);
    }

    /** Returns the user id of the newly created managed profile */
    private int setupManagedProfile(String apkName, String packageName,
            String adminReceiverClassName) throws Exception {
        // Temporary disable the DISALLOW_ADD_MANAGED_PROFILE, so that we can create profile
        // using adb command.
        clearDisallowAddManagedProfileRestriction();
        try {
            final int userId = createManagedProfile(mPrimaryUserId);
            installAppAsUser(apkName, userId);
            setProfileOwnerOrFail(adminReceiverClassName, userId);
            startUser(userId);
            runDeviceTestsAsUser(
                    packageName,
                    MANAGEMENT_TEST,
                    "testIsManagedProfile",
                    userId);
            return userId;
        } finally {
            // Adding back DISALLOW_ADD_MANAGED_PROFILE.
            addDisallowAddManagedProfileRestriction();
        }
    }

    /** Returns the user id of the newly created secondary user */
    private int setupManagedSecondaryUser() throws Exception {
        assertTrue(canCreateAdditionalUsers(1));

        runDeviceTestsAsUser(
                COMP_DPC_PKG,
                MANAGEMENT_TEST,
                "testCreateSecondaryUser",
                mPrimaryUserId);
        List<Integer> newUsers = getUsersCreatedByTests();
        assertEquals(1, newUsers.size());
        int secondaryUserId = newUsers.get(0);
        getDevice().startUser(secondaryUserId);
        return secondaryUserId;
    }

    /** Returns the user id of the newly created secondary user */
    private int provisionCorpOwnedManagedProfile() throws Exception {
        runDeviceTestsAsUser(
                COMP_DPC_PKG,
                MANAGED_PROFILE_PROVISIONING_TEST,
                "testProvisioningCorpOwnedManagedProfile",
                mPrimaryUserId);
        return getFirstManagedProfileUserId();
    }

    /**
     * Clear {@link android.os.UserManager#DISALLOW_ADD_MANAGED_PROFILE}.
     */
    private void clearDisallowAddManagedProfileRestriction() throws Exception {
        runDeviceTestsAsUser(
                COMP_DPC_PKG,
                USER_RESTRICTION_TEST,
                "testClearDisallowAddManagedProfileRestriction",
                mPrimaryUserId);
    }

    /**
     * Add {@link android.os.UserManager#DISALLOW_ADD_MANAGED_PROFILE}.
     */
    private void addDisallowAddManagedProfileRestriction() throws Exception {
        runDeviceTestsAsUser(
                COMP_DPC_PKG,
                USER_RESTRICTION_TEST,
                "testAddDisallowAddManagedProfileRestriction",
                mPrimaryUserId);
    }

    /**
     * Clear {@link android.os.UserManager#DISALLOW_REMOVE_MANAGED_PROFILE}.
     */
    private void clearDisallowRemoveManagedProfileRestriction() throws Exception {
        runDeviceTestsAsUser(
                COMP_DPC_PKG,
                USER_RESTRICTION_TEST,
                "testClearDisallowRemoveManagedProfileRestriction",
                mPrimaryUserId);
    }

    /**
     * Add {@link android.os.UserManager#DISALLOW_REMOVE_MANAGED_PROFILE}.
     */
    private void addDisallowRemoveManagedProfileRestriction() throws Exception {
        runDeviceTestsAsUser(
                COMP_DPC_PKG,
                USER_RESTRICTION_TEST,
                "testAddDisallowRemoveManagedProfileRestriction",
                mPrimaryUserId);
    }

    /**
     * Add {@link android.os.UserManager#DISALLOW_REMOVE_USER}.
     */
    private void addDisallowRemoveUserRestriction() throws Exception {
        runDeviceTestsAsUser(
                COMP_DPC_PKG,
                USER_RESTRICTION_TEST,
                "testAddDisallowRemoveUserRestriction",
                mPrimaryUserId);
    }

    /**
     * Clear {@link android.os.UserManager#DISALLOW_REMOVE_USER}.
     */
    private void clearDisallowRemoveUserRestriction() throws Exception {
        runDeviceTestsAsUser(
                COMP_DPC_PKG,
                USER_RESTRICTION_TEST,
                "testClearDisallowRemoveUserRestriction",
                mPrimaryUserId);
    }

    private void assertOtherProfilesEqualsBindTargetUsers(int otherProfileUserId) throws Exception {
        runDeviceTestsAsUser(
                COMP_DPC_PKG,
                MANAGEMENT_TEST,
                "testOtherProfilesEqualsBindTargetUsers",
                mPrimaryUserId);
        runDeviceTestsAsUser(
                COMP_DPC_PKG,
                MANAGEMENT_TEST,
                "testOtherProfilesEqualsBindTargetUsers",
                otherProfileUserId);
    }
}
