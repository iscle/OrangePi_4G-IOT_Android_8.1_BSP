/*
 * Copyright (C) 2017 The Android Open Source Project
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
package com.android.cts.comp;

import android.app.admin.DevicePolicyManager;
import android.content.ComponentName;
import android.content.Context;
import android.os.UserHandle;
import android.test.AndroidTestCase;
import android.test.MoreAsserts;
import java.util.List;

public class ManagementTest extends AndroidTestCase {

    private DevicePolicyManager mDevicePolicyManager;

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        mDevicePolicyManager = (DevicePolicyManager)
                mContext.getSystemService(Context.DEVICE_POLICY_SERVICE);
    }

    public void testIsManagedProfile() {
        assertNotNull(mDevicePolicyManager);
        assertTrue(mDevicePolicyManager.isAdminActive(
                AdminReceiver.getComponentName(getContext())));
        assertTrue(mDevicePolicyManager.isProfileOwnerApp(getContext().getPackageName()));
        assertTrue(mDevicePolicyManager.isManagedProfile(
                AdminReceiver.getComponentName(getContext())));
    }

    public void testIsDeviceOwner() {
        assertNotNull(mDevicePolicyManager);
        assertTrue(mDevicePolicyManager.isAdminActive(
                AdminReceiver.getComponentName(getContext())));
        assertTrue(mDevicePolicyManager.isDeviceOwnerApp(getContext().getPackageName()));
        assertFalse(mDevicePolicyManager.isManagedProfile(
                AdminReceiver.getComponentName(getContext())));
    }

    /**
     * Assumes that the managed profile is enabled.
     * Otherwise, {@link Utils#getOtherProfile} won't return a profile.
     */
    public void testOtherProfilesEqualsBindTargetUsers() {
        UserHandle otherProfile = Utils.getOtherProfile(mContext);
        assertNotNull(otherProfile);

        List<UserHandle> allowedTargetUsers = mDevicePolicyManager.getBindDeviceAdminTargetUsers(
                AdminReceiver.getComponentName(mContext));
        assertEquals(1, allowedTargetUsers.size());
        assertEquals(otherProfile, allowedTargetUsers.get(0));
    }

    public void testProvisionManagedProfileAllowed() {
        assertTrue(mDevicePolicyManager.isProvisioningAllowed(
                DevicePolicyManager.ACTION_PROVISION_MANAGED_PROFILE));
    }

    public void testProvisionManagedProfileNotAllowed() {
        assertFalse(mDevicePolicyManager.isProvisioningAllowed(
                DevicePolicyManager.ACTION_PROVISION_MANAGED_PROFILE));
    }

    public void testWipeData() {
        mDevicePolicyManager.wipeData(0);
    }

    public void testCanRemoveManagedProfile() {
        UserHandle profileUserHandle = Utils.getOtherProfile(mContext);
        assertNotNull(profileUserHandle);
        assertTrue(mDevicePolicyManager.removeUser(AdminReceiver.getComponentName(mContext),
                profileUserHandle));
    }

    public void testCreateSecondaryUser() throws Exception {
        ComponentName admin = AdminReceiver.getComponentName(mContext);
        assertNotNull(mDevicePolicyManager.createAndManageUser(admin, "secondary-user",
                admin, null, DevicePolicyManager.SKIP_SETUP_WIZARD));
    }

    public void testNoBindDeviceAdminTargetUsers() {
        MoreAsserts.assertEmpty(mDevicePolicyManager.getBindDeviceAdminTargetUsers(
                AdminReceiver.getComponentName(mContext)));
    }
}
