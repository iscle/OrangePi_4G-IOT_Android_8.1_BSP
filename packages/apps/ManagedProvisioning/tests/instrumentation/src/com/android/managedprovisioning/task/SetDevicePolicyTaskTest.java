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

package com.android.managedprovisioning.task;

import static android.app.admin.DevicePolicyManager.ACTION_PROVISION_MANAGED_DEVICE;
import static android.app.admin.DevicePolicyManager.ACTION_PROVISION_MANAGED_PROFILE;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import android.app.admin.DevicePolicyManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.pm.PackageManager;
import android.test.AndroidTestCase;
import android.test.suitebuilder.annotation.SmallTest;

import com.android.managedprovisioning.R;
import com.android.managedprovisioning.common.Utils;
import com.android.managedprovisioning.model.ProvisioningParams;

import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

public class SetDevicePolicyTaskTest extends AndroidTestCase {
    private static final String ADMIN_PACKAGE_NAME = "com.admin.test";
    private static final String ADMIN_RECEIVER_NAME = ADMIN_PACKAGE_NAME + ".AdminReceiver";
    private static final ComponentName ADMIN_COMPONENT_NAME = new ComponentName(ADMIN_PACKAGE_NAME,
            ADMIN_RECEIVER_NAME);
    private static final int TEST_USER_ID = 123;

    @Mock private Context mContext;
    @Mock private PackageManager mPackageManager;
    @Mock private DevicePolicyManager mDevicePolicyManager;
    @Mock private AbstractProvisioningTask.Callback mCallback;
    @Mock private Utils mUtils;

    private String mDefaultOwnerName;
    private SetDevicePolicyTask mTask;

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        // This is necessary for mockito to work
        System.setProperty("dexmaker.dexcache", getContext().getCacheDir().toString());
        MockitoAnnotations.initMocks(this);

        mDefaultOwnerName = getContext().getResources()
                .getString(R.string.default_owned_device_username);

        when(mContext.getPackageManager()).thenReturn(mPackageManager);
        when(mContext.getSystemService(Context.DEVICE_POLICY_SERVICE))
                .thenReturn(mDevicePolicyManager);
        when(mContext.getResources()).thenReturn(getContext().getResources());

        when(mPackageManager.getApplicationEnabledSetting(ADMIN_PACKAGE_NAME))
                .thenReturn(PackageManager.COMPONENT_ENABLED_STATE_DEFAULT);
        when(mDevicePolicyManager.getDeviceOwnerComponentOnCallingUser()).thenReturn(null);
        when(mDevicePolicyManager.setDeviceOwner(ADMIN_COMPONENT_NAME, mDefaultOwnerName,
                TEST_USER_ID)).thenReturn(true);
        when(mUtils.findDeviceAdmin(null, ADMIN_COMPONENT_NAME, mContext))
                .thenReturn(ADMIN_COMPONENT_NAME);
    }

    @SmallTest
    public void testEnableDevicePolicyApp_DefaultToDefault() {
        // GIVEN that we are provisioning device owner
        createTask(ACTION_PROVISION_MANAGED_DEVICE);
        // GIVEN that the management app is currently the manifest default
        when(mPackageManager.getApplicationEnabledSetting(ADMIN_PACKAGE_NAME))
                .thenReturn(PackageManager.COMPONENT_ENABLED_STATE_DEFAULT);

        // WHEN running the task
        mTask.run(TEST_USER_ID);

        // THEN the management app should still be default
        verify(mPackageManager, never()).setApplicationEnabledSetting(eq(ADMIN_PACKAGE_NAME),
                anyInt(), anyInt());
        verify(mCallback).onSuccess(mTask);
        verifyNoMoreInteractions(mCallback);
    }

    @SmallTest
    public void testEnableDevicePolicyApp_DisabledToDefault() {
        // GIVEN that we are provisioning device owner
        createTask(ACTION_PROVISION_MANAGED_DEVICE);
        // GIVEN that the management app is currently disabled
        when(mPackageManager.getApplicationEnabledSetting(ADMIN_PACKAGE_NAME))
                .thenReturn(PackageManager.COMPONENT_ENABLED_STATE_DISABLED);

        // WHEN running the task
        mTask.run(TEST_USER_ID);

        // THEN the management app should have been enabled
        verify(mPackageManager).setApplicationEnabledSetting(ADMIN_PACKAGE_NAME,
                PackageManager.COMPONENT_ENABLED_STATE_DEFAULT,
                PackageManager.DONT_KILL_APP);
        verify(mCallback).onSuccess(mTask);
        verifyNoMoreInteractions(mCallback);
    }

    @SmallTest
    public void testEnableDevicePolicyApp_EnabledToEnabled() {
        // GIVEN that we are provisioning device owner
        createTask(ACTION_PROVISION_MANAGED_DEVICE);
        // GIVEN that the management app is currently enabled
        when(mPackageManager.getApplicationEnabledSetting(ADMIN_PACKAGE_NAME))
                .thenReturn(PackageManager.COMPONENT_ENABLED_STATE_ENABLED);

        // WHEN running the task
        mTask.run(TEST_USER_ID);

        // THEN the management app should have been untouched
        verify(mPackageManager, never()).setApplicationEnabledSetting(eq(ADMIN_PACKAGE_NAME),
                anyInt(), anyInt());
        verify(mCallback).onSuccess(mTask);
        verifyNoMoreInteractions(mCallback);
    }

    @SmallTest
    public void testEnableDevicePolicyApp_PackageNotFound() {
        // GIVEN that we are provisioning device owner
        createTask(ACTION_PROVISION_MANAGED_DEVICE);
        // GIVEN that the management app is not present on the device
        when(mPackageManager.getApplicationEnabledSetting(ADMIN_PACKAGE_NAME))
                .thenThrow(new IllegalArgumentException());

        // WHEN running the task
        mTask.run(TEST_USER_ID);

        // THEN an error should be returned
        verify(mCallback).onError(mTask, 0);
        verifyNoMoreInteractions(mCallback);
    }

    @SmallTest
    public void testSetActiveAdmin() {
        // GIVEN that we are provisioning device owner
        createTask(ACTION_PROVISION_MANAGED_DEVICE);

        // WHEN running the task
        mTask.run(TEST_USER_ID);

        // THEN the management app should have been set as active admin
        verify(mDevicePolicyManager).setActiveAdmin(ADMIN_COMPONENT_NAME, true, TEST_USER_ID);
        verify(mCallback).onSuccess(mTask);
        verifyNoMoreInteractions(mCallback);
    }

    @SmallTest
    public void testSetDeviceOwner() {
        // GIVEN that we are provisioning device owner
        createTask(ACTION_PROVISION_MANAGED_DEVICE);

        // WHEN running the task
        mTask.run(TEST_USER_ID);

        // THEN the management app should have been set as device owner
        verify(mDevicePolicyManager).setDeviceOwner(ADMIN_COMPONENT_NAME, mDefaultOwnerName,
                TEST_USER_ID);
        verify(mCallback).onSuccess(mTask);
        verifyNoMoreInteractions(mCallback);
    }

    @SmallTest
    public void testSetDeviceOwner_PreconditionsNotMet() {
        // GIVEN that we are provisioning device owner
        createTask(ACTION_PROVISION_MANAGED_DEVICE);

        // GIVEN that setting device owner is not currently allowed
        when(mDevicePolicyManager.setDeviceOwner(ADMIN_COMPONENT_NAME, mDefaultOwnerName,
                TEST_USER_ID)).thenThrow(new IllegalStateException());

        // WHEN running the task
        mTask.run(TEST_USER_ID);

        // THEN an error should be returned
        verify(mCallback).onError(mTask, 0);
        verifyNoMoreInteractions(mCallback);
    }

    @SmallTest
    public void testSetDeviceOwner_ReturnFalse() {
        // GIVEN that we are provisioning device owner
        createTask(ACTION_PROVISION_MANAGED_DEVICE);

        // GIVEN that setting device owner fails
        when(mDevicePolicyManager.setDeviceOwner(ADMIN_COMPONENT_NAME, mDefaultOwnerName,
                TEST_USER_ID)).thenReturn(false);

        // WHEN running the task
        mTask.run(TEST_USER_ID);

        // THEN an error should be returned
        verify(mCallback).onError(mTask, 0);
        verifyNoMoreInteractions(mCallback);
    }

    @SmallTest
    public void testSetProfileOwner() {
        // GIVEN that we are provisioning a managed profile
        createTask(ACTION_PROVISION_MANAGED_PROFILE);
        // GIVEN that setting the profile owner succeeds
        when(mDevicePolicyManager.setProfileOwner(ADMIN_COMPONENT_NAME, ADMIN_PACKAGE_NAME,
                TEST_USER_ID)).thenReturn(true);

        // WHEN running the task
        mTask.run(TEST_USER_ID);

        // THEN the management app should have been set as profile owner
        verify(mDevicePolicyManager).setProfileOwner(ADMIN_COMPONENT_NAME, ADMIN_PACKAGE_NAME,
                TEST_USER_ID);
        verify(mCallback).onSuccess(mTask);
        verifyNoMoreInteractions(mCallback);
    }

    private void createTask(String action) {
        ProvisioningParams params = new ProvisioningParams.Builder()
                .setDeviceAdminComponentName(ADMIN_COMPONENT_NAME)
                .setProvisioningAction(action)
                .build();
        mTask = new SetDevicePolicyTask(mUtils, mContext, params, mCallback);
    }
}
