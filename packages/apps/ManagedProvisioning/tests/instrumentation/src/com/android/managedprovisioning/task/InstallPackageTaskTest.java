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
import static com.android.managedprovisioning.task.InstallPackageTask.ERROR_INSTALLATION_FAILED;
import static com.android.managedprovisioning.task.InstallPackageTask.ERROR_PACKAGE_INVALID;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.anyInt;
import static org.mockito.Matchers.anyString;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import android.content.Context;
import android.content.pm.IPackageInstallObserver;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.test.AndroidTestCase;
import android.test.suitebuilder.annotation.SmallTest;

import com.android.managedprovisioning.common.SettingsFacade;
import com.android.managedprovisioning.model.ProvisioningParams;

import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

public class InstallPackageTaskTest extends AndroidTestCase {
    private static final String TEST_PACKAGE_NAME = "com.android.test";
    private static final String OTHER_PACKAGE_NAME = "com.android.other";
    private static final String TEST_PACKAGE_LOCATION = "/sdcard/TestPackage.apk";
    private static final ProvisioningParams TEST_PARAMS = new ProvisioningParams.Builder()
            .setDeviceAdminPackageName(TEST_PACKAGE_NAME)
            .setProvisioningAction(ACTION_PROVISION_MANAGED_DEVICE)
            .build();
    private static final int TEST_USER_ID = 123;

    @Mock private Context mContext;
    @Mock private PackageManager mPackageManager;
    @Mock private AbstractProvisioningTask.Callback mCallback;
    @Mock private DownloadPackageTask mDownloadPackageTask;
    private InstallPackageTask mTask;
    private final SettingsFacade mSettingsFacade = new SettingsFacadeStub();

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        // this is necessary for mockito to work
        System.setProperty("dexmaker.dexcache", getContext().getCacheDir().toString());
        MockitoAnnotations.initMocks(this);

        when(mContext.getPackageManager()).thenReturn(mPackageManager);
        when(mContext.getPackageName()).thenReturn(getContext().getPackageName());

        mTask = new InstallPackageTask(mSettingsFacade, mDownloadPackageTask, mContext, TEST_PARAMS,
                mCallback);
    }

    @SmallTest
    public void testNoDownloadLocation() {
        // GIVEN no package was downloaded
        when(mDownloadPackageTask.getDownloadedPackageLocation()).thenReturn(null);

        // WHEN running the InstallPackageTask without specifying an install location
        mTask.run(TEST_USER_ID);
        // THEN no package is installed, but we get a success callback
        verify(mPackageManager, never()).installPackage(
                any(Uri.class),
                any(IPackageInstallObserver.class),
                anyInt(),
                anyString());
        verify(mCallback).onSuccess(mTask);
        verifyNoMoreInteractions(mCallback);
        assertTrue(mSettingsFacade.isPackageVerifierEnabled(mContext));
    }

    @SmallTest
    public void testSuccess() throws Exception {
        // GIVEN a package was downloaded to TEST_LOCATION
        when(mDownloadPackageTask.getDownloadedPackageLocation()).thenReturn(TEST_PACKAGE_LOCATION);

        // WHEN running the InstallPackageTask specifying an install location
        mTask.run(TEST_USER_ID);

        // THEN package installed is invoked with an install observer
        IPackageInstallObserver observer = verifyPackageInstalled();

        // WHEN the package installed callback is invoked with success
        observer.packageInstalled(TEST_PACKAGE_NAME, PackageManager.INSTALL_SUCCEEDED);

        // THEN we receive a success callback
        verify(mCallback).onSuccess(mTask);
        verifyNoMoreInteractions(mCallback);
        assertTrue(mSettingsFacade.isPackageVerifierEnabled(mContext));
    }

    @SmallTest
    public void testInstallFailedVersionDowngrade() throws Exception {
        // GIVEN a package was downloaded to TEST_LOCATION
        when(mDownloadPackageTask.getDownloadedPackageLocation()).thenReturn(TEST_PACKAGE_LOCATION);

        // WHEN running the InstallPackageTask with a package already at a higher version
        mTask.run(TEST_USER_ID);

        // THEN package installed is invoked with an install observer
        IPackageInstallObserver observer = verifyPackageInstalled();

        // WHEN the package installed callback is invoked with version downgrade error
        observer.packageInstalled(null, PackageManager.INSTALL_FAILED_VERSION_DOWNGRADE);

        // THEN we get a success callback, because an existing version of the DPC is present
        verify(mCallback).onSuccess(mTask);
        verifyNoMoreInteractions(mCallback);
        assertTrue(mSettingsFacade.isPackageVerifierEnabled(mContext));
    }

    @SmallTest
    public void testInstallFailedOtherError() throws Exception {
        // GIVEN a package was downloaded to TEST_LOCATION
        when(mDownloadPackageTask.getDownloadedPackageLocation()).thenReturn(TEST_PACKAGE_LOCATION);

        // WHEN running the InstallPackageTask with a package already at a higher version
        mTask.run(TEST_USER_ID);

        // THEN package installed is invoked with an install observer
        IPackageInstallObserver observer = verifyPackageInstalled();

        // WHEN the package installed callback is invoked with version downgrade error
        observer.packageInstalled(null, PackageManager.INSTALL_FAILED_INVALID_APK);

        // THEN we get a success callback, because an existing version of the DPC is present
        verify(mCallback).onError(mTask, ERROR_INSTALLATION_FAILED);
        verifyNoMoreInteractions(mCallback);
        assertTrue(mSettingsFacade.isPackageVerifierEnabled(mContext));
    }

    @SmallTest
    public void testDifferentPackageName() throws Exception {
        // GIVEN a package was downloaded to TEST_LOCATION
        when(mDownloadPackageTask.getDownloadedPackageLocation()).thenReturn(TEST_PACKAGE_LOCATION);

        // WHEN running the InstallPackageTask with a package already at a higher version
        mTask.run(TEST_USER_ID);

        // THEN package installed is invoked with an install observer
        IPackageInstallObserver observer = verifyPackageInstalled();

        // WHEN the package installed callback is invoked with version downgrade error
        observer.packageInstalled(OTHER_PACKAGE_NAME, PackageManager.INSTALL_SUCCEEDED);

        // THEN we get a success callback, because an existing version of the DPC is present
        verify(mCallback).onError(mTask, ERROR_PACKAGE_INVALID);
        verifyNoMoreInteractions(mCallback);
        assertTrue(mSettingsFacade.isPackageVerifierEnabled(mContext));
    }

    private IPackageInstallObserver verifyPackageInstalled() {
        ArgumentCaptor<IPackageInstallObserver> observerCaptor
                = ArgumentCaptor.forClass(IPackageInstallObserver.class);
        ArgumentCaptor<Integer> flagsCaptor = ArgumentCaptor.forClass(Integer.class);
        // THEN the package is installed and we get a success callback
        verify(mPackageManager).installPackage(
                eq(Uri.parse("file://" + TEST_PACKAGE_LOCATION)),
                observerCaptor.capture(),
                flagsCaptor.capture(),
                eq(getContext().getPackageName()));
        // make sure that the flags value has been set
        assertTrue(0 != (flagsCaptor.getValue() & PackageManager.INSTALL_REPLACE_EXISTING));
        return observerCaptor.getValue();
    }

    private static class SettingsFacadeStub extends SettingsFacade {
        private boolean mPackageVerifierEnabled = true;

        @Override
        public boolean isPackageVerifierEnabled(Context c) {
            return mPackageVerifierEnabled;
        }

        @Override
        public void setPackageVerifierEnabled(Context c, boolean packageVerifierEnabled) {
            mPackageVerifierEnabled = packageVerifierEnabled;
        }
    }
}
