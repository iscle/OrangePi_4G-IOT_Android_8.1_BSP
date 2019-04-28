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
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import android.content.Context;
import android.content.pm.IPackageDeleteObserver;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.os.RemoteException;
import android.support.test.InstrumentationRegistry;
import android.support.test.filters.SmallTest;
import android.test.mock.MockPackageManager;

import com.android.managedprovisioning.model.ProvisioningParams;
import com.android.managedprovisioning.task.nonrequiredapps.NonRequiredAppsLogic;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

@SmallTest
public class DeleteNonRequiredAppsTaskTest {
    private static final String TEST_DPC_PACKAGE_NAME = "dpc.package.name";
    private static final int TEST_USER_ID = 123;
    private static final ProvisioningParams TEST_PARAMS = new ProvisioningParams.Builder()
            .setProvisioningAction(ACTION_PROVISION_MANAGED_DEVICE)
            .setDeviceAdminPackageName(TEST_DPC_PACKAGE_NAME)
            .build();

    private @Mock AbstractProvisioningTask.Callback mCallback;
    private @Mock Context mTestContext;
    private @Mock NonRequiredAppsLogic mLogic;

    private FakePackageManager mPackageManager;
    private Set<String> mDeletedApps;
    private DeleteNonRequiredAppsTask mTask;

    @Before
    public void setUp() throws Exception {
        MockitoAnnotations.initMocks(this);

        mPackageManager = new FakePackageManager();

        when(mTestContext.getPackageManager()).thenReturn(mPackageManager);
        when(mTestContext.getFilesDir()).thenReturn(InstrumentationRegistry.getTargetContext()
                .getFilesDir());

        mDeletedApps = new HashSet<>();

        mTask = new DeleteNonRequiredAppsTask(mTestContext, TEST_PARAMS, mCallback, mLogic);
    }

    @Test
    public void testNoAppsToDelete() {
        // GIVEN that no apps should be deleted
        when(mLogic.getSystemAppsToRemove(TEST_USER_ID)).thenReturn(Collections.emptySet());
        mPackageManager.setInstalledApps(setFromArray("app.a"));

        // WHEN running the task
        mTask.run(TEST_USER_ID);

        // THEN maybe take snapshot should have been called
        verify(mLogic).maybeTakeSystemAppsSnapshot(TEST_USER_ID);

        // THEN success should be called
        verify(mCallback).onSuccess(mTask);
        verifyNoMoreInteractions(mCallback);

        // THEN no apps should have been deleted
        assertDeletedApps();
    }

    @Test
    public void testAppsToDelete() {
        // GIVEN that some apps should be deleted
        when(mLogic.getSystemAppsToRemove(TEST_USER_ID))
                .thenReturn(setFromArray("app.a", "app.b"));
        // GIVEN that only app a is currently installed
        mPackageManager.setInstalledApps(setFromArray("app.a", "app.c"));

        // WHEN running the task
        mTask.run(TEST_USER_ID);

        // THEN maybe take snapshot should have been called
        verify(mLogic).maybeTakeSystemAppsSnapshot(TEST_USER_ID);

        // THEN success should be called
        verify(mCallback).onSuccess(mTask);
        verifyNoMoreInteractions(mCallback);

        // THEN those apps should have been deleted
        assertDeletedApps("app.a");
    }

    @Test
    public void testAllAppsAlreadyDeleted() {
        // GIVEN that some apps should be deleted
        when(mLogic.getSystemAppsToRemove(TEST_USER_ID))
            .thenReturn(setFromArray("app.a", "app.b"));

        // WHEN running the task
        mTask.run(TEST_USER_ID);

        // THEN maybe take snapshot should have been called
        verify(mLogic).maybeTakeSystemAppsSnapshot(TEST_USER_ID);

        // THEN success should be called
        verify(mCallback).onSuccess(mTask);
        verifyNoMoreInteractions(mCallback);

        // THEN those apps should have been deleted
        assertDeletedApps();
    }

    @Test
    public void testDeletionFailed() {
        // GIVEN that one app should be deleted
        when(mLogic.getSystemAppsToRemove(TEST_USER_ID))
            .thenReturn(setFromArray("app.a"));
        mPackageManager.setInstalledApps(setFromArray("app.a"));

        // GIVEN that deletion fails
        mPackageManager.setDeletionSucceeds(false);

        // WHEN running the task
        mTask.run(TEST_USER_ID);

        // THEN maybe take snapshot should have been called
        verify(mLogic).maybeTakeSystemAppsSnapshot(TEST_USER_ID);

        // THEN error should be returned
        verify(mCallback).onError(mTask, 0);
        verifyNoMoreInteractions(mCallback);
    }

    private <T> Set<T> setFromArray(T... array) {
        if (array == null) {
            return null;
        }
        return new HashSet<>(Arrays.asList(array));
    }

    private void assertDeletedApps(String... appArray) {
        assertEquals(setFromArray(appArray), mDeletedApps);
    }


    class FakePackageManager extends MockPackageManager {
        private boolean mDeletionSucceeds = true;
        private Set<String> mInstalledApps = new HashSet<>();

        void setDeletionSucceeds(boolean deletionSucceeds) {
            mDeletionSucceeds = deletionSucceeds;
        }

        void setInstalledApps(Set<String> set) {
            mInstalledApps = set;
        }

        @Override
        public void deletePackageAsUser(String packageName, IPackageDeleteObserver observer,
                int flags, int userId) {
            if (mDeletionSucceeds) {
                mDeletedApps.add(packageName);
            }
            assertTrue((flags & PackageManager.DELETE_SYSTEM_APP) != 0);
            assertEquals(TEST_USER_ID, userId);

            int resultCode;
            if (mDeletionSucceeds) {
                resultCode = PackageManager.DELETE_SUCCEEDED;
            } else {
                resultCode = PackageManager.DELETE_FAILED_INTERNAL_ERROR;
            }
            assertTrue(mInstalledApps.remove(packageName));

            try {
                observer.packageDeleted(packageName, resultCode);
            } catch (RemoteException e) {
                fail(e.toString());
            }
        }

        @Override
        public PackageInfo getPackageInfoAsUser(String pkg, int flag, int userId)
                throws NameNotFoundException {
            if (mInstalledApps.contains(pkg) && userId == TEST_USER_ID) {
                return new PackageInfo();
            }
            throw new NameNotFoundException();
        }
    }
}
