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

package com.android.managedprovisioning.task;

import static android.app.admin.DevicePolicyManager.ACTION_PROVISION_MANAGED_PROFILE;
import static com.android.managedprovisioning.task.ManagedProfileSettingsTask.DEFAULT_CONTACT_REMOTE_SEARCH;
import static org.mockito.Matchers.anyInt;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import android.app.admin.DevicePolicyManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.os.UserManager;
import android.support.test.filters.SmallTest;

import com.android.managedprovisioning.common.SettingsFacade;
import com.android.managedprovisioning.model.ProvisioningParams;

import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

/**
 * Unit-tests for {@link ManagedProfileSettingsTask}.
 */
@SmallTest
public class ManagedProfileSettingsTaskTest {

    private static final int TEST_USER_ID = 123;
    private static final ComponentName ADMIN = new ComponentName("com.test.admin", ".Receiver");
    private static final ProvisioningParams NO_COLOR_PARAMS = new ProvisioningParams.Builder()
            .setProvisioningAction(ACTION_PROVISION_MANAGED_PROFILE)
            .setDeviceAdminComponentName(ADMIN)
            .build();
    private static final ProvisioningParams COLOR_PARAMS = new ProvisioningParams.Builder()
            .setProvisioningAction(ACTION_PROVISION_MANAGED_PROFILE)
            .setDeviceAdminComponentName(ADMIN)
            .setMainColor(Color.GREEN)
            .build();


    @Mock private Context mContext;
    @Mock private UserManager mUserManager;
    @Mock private DevicePolicyManager mDevicePolicyManager;
    @Mock private PackageManager mPackageManager;
    @Mock private AbstractProvisioningTask.Callback mCallback;
    @Mock private SettingsFacade mSettingsFacade;
    @Mock private CrossProfileIntentFiltersSetter mCrossProfileIntentFiltersSetter;
    private ManagedProfileSettingsTask mTask;

    @Before
    public void setUp() {
        MockitoAnnotations.initMocks(this);

        when(mContext.getSystemService(Context.USER_SERVICE)).thenReturn(mUserManager);
        when(mContext.getSystemService(Context.DEVICE_POLICY_SERVICE))
                .thenReturn(mDevicePolicyManager);
        when(mContext.getPackageManager()).thenReturn(mPackageManager);

    }

    @Test
    public void testNoMainColor() {
        // GIVEN that no main color was passed in the parameter
        mTask = new ManagedProfileSettingsTask(mSettingsFacade, mCrossProfileIntentFiltersSetter,
                mContext, NO_COLOR_PARAMS, mCallback);

        // WHEN running the task
        mTask.run(TEST_USER_ID);

        // THEN success should be called
        verify(mCallback).onSuccess(mTask);
        verifyNoMoreInteractions(mCallback);

        // THEN no color should be stored in dpm
        verify(mDevicePolicyManager, never())
                .setOrganizationColorForUser(anyInt(), eq(TEST_USER_ID));

        // THEN user setup complete and contacts remote search should be set
        verify(mSettingsFacade).setUserSetupCompleted(mContext, TEST_USER_ID);
        verify(mSettingsFacade).setProfileContactRemoteSearch(mContext,
                DEFAULT_CONTACT_REMOTE_SEARCH, TEST_USER_ID);

        // THEN cross profile intent filters are set
        verify(mCrossProfileIntentFiltersSetter).setFilters(anyInt(), eq(TEST_USER_ID));
    }

    @Test
    public void testMainColor() {
        // GIVEN that a main color was passed in the parameter
        mTask = new ManagedProfileSettingsTask(mSettingsFacade, mCrossProfileIntentFiltersSetter,
                mContext, COLOR_PARAMS, mCallback);

        // WHEN running the task
        mTask.run(TEST_USER_ID);

        // THEN success should be called
        verify(mCallback).onSuccess(mTask);
        verifyNoMoreInteractions(mCallback);

        // THEN the main color should be stored in dpm
        verify(mDevicePolicyManager).setOrganizationColorForUser(Color.GREEN, TEST_USER_ID);

        // THEN user setup complete and contacts remote search should be set
        verify(mSettingsFacade).setUserSetupCompleted(mContext, TEST_USER_ID);
        verify(mSettingsFacade).setProfileContactRemoteSearch(mContext,
                DEFAULT_CONTACT_REMOTE_SEARCH, TEST_USER_ID);

        // THEN cross profile intent filters are set
        verify(mCrossProfileIntentFiltersSetter).setFilters(anyInt(), eq(TEST_USER_ID));
    }
}
