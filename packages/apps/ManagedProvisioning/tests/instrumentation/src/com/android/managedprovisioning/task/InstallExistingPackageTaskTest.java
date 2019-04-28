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

package com.android.managedprovisioning.task;

import static android.app.admin.DevicePolicyManager.ACTION_PROVISION_MANAGED_PROFILE;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import android.content.ComponentName;
import android.content.Context;
import android.content.pm.PackageManager;
import android.support.test.filters.SmallTest;

import com.android.managedprovisioning.model.ProvisioningParams;

import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

/**
 * Unit tests for {@link InstallExistingPackageTask}.
 */
@SmallTest
public class InstallExistingPackageTaskTest {
    private static final String ADMIN_PACKAGE_NAME = "com.admin.test";
    private static final String ADMIN_RECEIVER_NAME = ADMIN_PACKAGE_NAME + ".AdminReceiver";
    private static final ComponentName ADMIN_COMPONENT_NAME = new ComponentName(ADMIN_PACKAGE_NAME,
            ADMIN_RECEIVER_NAME);
    private static final String INSTALL_PACKAGE_NAME = "com.install.package";
    private static final int TEST_USER_ID = 123;
    private final ProvisioningParams TEST_PARAMS = new ProvisioningParams.Builder()
            .setProvisioningAction(ACTION_PROVISION_MANAGED_PROFILE)
            .setDeviceAdminComponentName(ADMIN_COMPONENT_NAME)
            .build();

    @Mock private Context mContext;
    @Mock private PackageManager mPackageManager;
    @Mock private AbstractProvisioningTask.Callback mCallback;
    private InstallExistingPackageTask mTask;

    @Before
    public void setUp() throws Exception {
        MockitoAnnotations.initMocks(this);

        when(mContext.getPackageManager()).thenReturn(mPackageManager);

        mTask = new InstallExistingPackageTask(INSTALL_PACKAGE_NAME, mContext, TEST_PARAMS,
                mCallback);
    }

    @Test
    public void testSuccess() throws Exception {
        // GIVEN that installing the existing package succeeds
        when(mPackageManager.installExistingPackageAsUser(INSTALL_PACKAGE_NAME, TEST_USER_ID))
                .thenReturn(PackageManager.INSTALL_SUCCEEDED);

        // WHEN running the task
        mTask.run(TEST_USER_ID);

        // THEN the existing package should have been installed
        verify(mPackageManager).installExistingPackageAsUser(INSTALL_PACKAGE_NAME, TEST_USER_ID);
        verify(mCallback).onSuccess(mTask);
        verifyNoMoreInteractions(mCallback);
    }

    @Test
    public void testPackageNotFound() throws Exception {
        // GIVEN that the package is not present on the device
        when(mPackageManager.installExistingPackageAsUser(INSTALL_PACKAGE_NAME, TEST_USER_ID))
                .thenThrow(new PackageManager.NameNotFoundException());

        // WHEN running the task
        mTask.run(TEST_USER_ID);

        // THEN an error should be returned
        verify(mCallback).onError(mTask, 0);
        verifyNoMoreInteractions(mCallback);
    }

    @Test
    public void testInstallFailed() throws Exception {
        // GIVEN that the package is not present on the device
        when(mPackageManager.installExistingPackageAsUser(INSTALL_PACKAGE_NAME, TEST_USER_ID))
                .thenReturn(PackageManager.INSTALL_FAILED_INVALID_APK);

        // WHEN running the task
        mTask.run(TEST_USER_ID);

        // THEN an error should be returned
        verify(mCallback).onError(mTask, 0);
        verifyNoMoreInteractions(mCallback);
    }
}
