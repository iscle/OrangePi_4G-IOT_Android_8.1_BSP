/*
 * Copyright 2016, The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.managedprovisioning.finalization;

import static android.app.admin.DevicePolicyManager.ACTION_PROVISION_MANAGED_DEVICE;
import static android.app.admin.DevicePolicyManager.ACTION_PROVISION_MANAGED_PROFILE;
import static android.app.admin.DevicePolicyManager.STATE_USER_PROFILE_COMPLETE;
import static android.app.admin.DevicePolicyManager.STATE_USER_SETUP_COMPLETE;
import static android.app.admin.DevicePolicyManager.STATE_USER_SETUP_FINALIZED;
import static android.app.admin.DevicePolicyManager.STATE_USER_SETUP_INCOMPLETE;
import static android.app.admin.DevicePolicyManager.STATE_USER_UNMANAGED;
import static android.content.Context.DEVICE_POLICY_SERVICE;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import android.app.admin.DevicePolicyManager;
import android.content.Context;
import android.os.UserHandle;
import android.test.AndroidTestCase;
import android.test.suitebuilder.annotation.SmallTest;

import com.android.managedprovisioning.common.SettingsFacade;
import com.android.managedprovisioning.common.Utils;
import com.android.managedprovisioning.model.ProvisioningParams;

import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

/**
 * Unit tests for {@link UserProvisioningStateHelper}.
 */
public class UserProvisioningStateHelperTest extends AndroidTestCase {
    private static final int PRIMARY_USER_ID = 1;
    private static final int MANAGED_PROFILE_USER_ID = 2;
    private static final String TEST_MDM_PACKAGE_NAME = "mdm.package.name";

    @Mock private Context mContext;
    @Mock private DevicePolicyManager mDevicePolicyManager;
    @Mock private Utils mUtils;
    @Mock private SettingsFacade mSettingsFacade;

    private UserProvisioningStateHelper mHelper;

    public void setUp() {
        // this is necessary for mockito to work
        System.setProperty("dexmaker.dexcache", getContext().getCacheDir().toString());
        MockitoAnnotations.initMocks(this);

        when(mContext.getSystemService(DEVICE_POLICY_SERVICE)).thenReturn(mDevicePolicyManager);

        mHelper = new UserProvisioningStateHelper(
                mContext,
                mUtils,
                mSettingsFacade,
                PRIMARY_USER_ID);
    }

    @SmallTest
    public void testInitiallyDone_ProfileAfterSuw() {
        // GIVEN that we've provisioned a managed profile after SUW
        final ProvisioningParams params = createProvisioningParams(ACTION_PROVISION_MANAGED_PROFILE,
                false);
        when(mSettingsFacade.isUserSetupCompleted(mContext)).thenReturn(true);
        when(mUtils.getManagedProfile(mContext)).thenReturn(UserHandle.of(MANAGED_PROFILE_USER_ID));

        // WHEN calling markUserProvisioningStateInitiallyDone
        mHelper.markUserProvisioningStateInitiallyDone(params);

        // THEN the managed profile's state should be set to FINALIZED
        verify(mDevicePolicyManager).setUserProvisioningState(STATE_USER_SETUP_FINALIZED,
                MANAGED_PROFILE_USER_ID);
    }

    @SmallTest
    public void testInitiallyDone_ProfileDuringSuw() {
        // GIVEN that we've provisioned a managed profile during SUW
        final ProvisioningParams params = createProvisioningParams(ACTION_PROVISION_MANAGED_PROFILE,
                false);
        when(mSettingsFacade.isUserSetupCompleted(mContext)).thenReturn(false);
        when(mUtils.getManagedProfile(mContext)).thenReturn(UserHandle.of(MANAGED_PROFILE_USER_ID));

        // WHEN calling markUserProvisioningStateInitiallyDone
        mHelper.markUserProvisioningStateInitiallyDone(params);

        // THEN the managed profile's state should be set to COMPLETE
        verify(mDevicePolicyManager).setUserProvisioningState(STATE_USER_SETUP_COMPLETE,
                MANAGED_PROFILE_USER_ID);
        // THEN the primary user's state should be set to PROFILE_COMPLETE
        verify(mDevicePolicyManager).setUserProvisioningState(STATE_USER_PROFILE_COMPLETE,
                PRIMARY_USER_ID);
    }

    @SmallTest
    public void testInitiallyDone_DeviceOwnerSkipUserSetup() {
        // GIVEN that we've provisioned a device owner with skip user setup true
        final ProvisioningParams params = createProvisioningParams(ACTION_PROVISION_MANAGED_DEVICE,
                true);
        when(mSettingsFacade.isUserSetupCompleted(mContext)).thenReturn(false);

        // WHEN calling markUserProvisioningStateInitiallyDone
        mHelper.markUserProvisioningStateInitiallyDone(params);

        // THEN the primary user's state should be set to COMPLETE
        verify(mDevicePolicyManager).setUserProvisioningState(STATE_USER_SETUP_COMPLETE,
                PRIMARY_USER_ID);
    }

    @SmallTest
    public void testInitiallyDone_DeviceOwnerDontSkipUserSetup() {
        // GIVEN that we've provisioned a device owner with skip user setup false
        final ProvisioningParams params = createProvisioningParams(ACTION_PROVISION_MANAGED_DEVICE,
                false);
        when(mSettingsFacade.isUserSetupCompleted(mContext)).thenReturn(false);

        // WHEN calling markUserProvisioningStateInitiallyDone
        mHelper.markUserProvisioningStateInitiallyDone(params);

        // THEN the primary user's state should be set to INCOMPLETE
        verify(mDevicePolicyManager).setUserProvisioningState(STATE_USER_SETUP_INCOMPLETE,
                PRIMARY_USER_ID);
    }

    @SmallTest
    public void testFinalized_ManagedProfile() {
        // GIVEN that we've provisioned a managed profile
        final ProvisioningParams params = createProvisioningParams(ACTION_PROVISION_MANAGED_PROFILE,
                false);
        when(mUtils.getManagedProfile(mContext)).thenReturn(UserHandle.of(MANAGED_PROFILE_USER_ID));

        // WHEN calling markUserProvisioningStateFinalized
        mHelper.markUserProvisioningStateFinalized(params);

        // THEN the managed profile's state should be set to FINALIZED
        verify(mDevicePolicyManager).setUserProvisioningState(STATE_USER_SETUP_FINALIZED,
                MANAGED_PROFILE_USER_ID);
        // THEN the primary user's state should be set to UNMANAGED
        verify(mDevicePolicyManager).setUserProvisioningState(STATE_USER_UNMANAGED,
                PRIMARY_USER_ID);
    }

    @SmallTest
    public void testFinalized_DeviceOwner() {
        // GIVEN that we've provisioned a device owner with skip user setup false
        final ProvisioningParams params = createProvisioningParams(ACTION_PROVISION_MANAGED_DEVICE,
                false);

        // WHEN calling markUserProvisioningStateFinalized
        mHelper.markUserProvisioningStateFinalized(params);

        // THEN the primary user's state should be set to FINALIZED
        verify(mDevicePolicyManager).setUserProvisioningState(STATE_USER_SETUP_FINALIZED,
                PRIMARY_USER_ID);
    }

    @SmallTest
    public void testIsStateUnmanagedOrFinalized() {
        assertTrue(isStateUnmanagedOrFinalizedWithCurrentState(STATE_USER_UNMANAGED));
        assertTrue(isStateUnmanagedOrFinalizedWithCurrentState(STATE_USER_SETUP_FINALIZED));
        assertFalse(isStateUnmanagedOrFinalizedWithCurrentState(STATE_USER_PROFILE_COMPLETE));
        assertFalse(isStateUnmanagedOrFinalizedWithCurrentState(STATE_USER_SETUP_INCOMPLETE));
        assertFalse(isStateUnmanagedOrFinalizedWithCurrentState(STATE_USER_PROFILE_COMPLETE));
    }

    private boolean isStateUnmanagedOrFinalizedWithCurrentState(int currentState) {
        when(mDevicePolicyManager.getUserProvisioningState()).thenReturn(currentState);
        return mHelper.isStateUnmanagedOrFinalized();
    }

    private ProvisioningParams createProvisioningParams(String action, boolean skipUserSetup) {
        return new ProvisioningParams.Builder()
                .setDeviceAdminPackageName(TEST_MDM_PACKAGE_NAME)
                .setProvisioningAction(action)
                .setSkipUserSetup(skipUserSetup)
                .build();
    }
}
