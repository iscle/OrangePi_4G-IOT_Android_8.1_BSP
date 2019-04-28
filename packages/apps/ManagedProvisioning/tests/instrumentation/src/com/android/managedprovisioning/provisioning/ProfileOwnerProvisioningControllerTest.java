/*
 * Copyright 2016, The Android Open Source Project
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

package com.android.managedprovisioning.provisioning;

import static android.app.admin.DevicePolicyManager.ACTION_PROVISION_MANAGED_PROFILE;

import static org.mockito.Matchers.anyInt;
import static org.mockito.Matchers.anyString;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import android.content.ComponentName;
import android.content.Context;
import android.content.ContextWrapper;
import android.content.pm.UserInfo;
import android.os.UserManager;
import android.support.test.filters.MediumTest;
import android.support.test.filters.SmallTest;

import com.android.managedprovisioning.R;
import com.android.managedprovisioning.finalization.FinalizationController;
import com.android.managedprovisioning.model.ProvisioningParams;
import com.android.managedprovisioning.task.AbstractProvisioningTask;
import com.android.managedprovisioning.task.CopyAccountToUserTask;
import com.android.managedprovisioning.task.CreateManagedProfileTask;
import com.android.managedprovisioning.task.DisableInstallShortcutListenersTask;
import com.android.managedprovisioning.task.InstallExistingPackageTask;
import com.android.managedprovisioning.task.ManagedProfileSettingsTask;
import com.android.managedprovisioning.task.SetDevicePolicyTask;
import com.android.managedprovisioning.task.StartManagedProfileTask;

import org.mockito.Mock;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.stubbing.Answer;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/**
 * Unit tests for {@link ProfileOwnerProvisioningController}.
 */

public class ProfileOwnerProvisioningControllerTest extends ProvisioningControllerBaseTest {

    private static final int TEST_PARENT_USER_ID = 1;
    private static final int TEST_PROFILE_USER_ID = 2;
    private static final ComponentName TEST_ADMIN = new ComponentName("com.test.admin",
            "com.test.admin.AdminReceiver");

    @Mock private ProvisioningControllerCallback mCallback;
    @Mock private FinalizationController mFinalizationController;
    @Mock private UserManager mUserManager;
    private Context mContext;
    private ProvisioningParams mParams;

    @Override
    public void setUp() throws Exception {
        super.setUp();

        mContext = new ContextWrapper(getContext()) {
            @Override
            public Object getSystemService(String name) {
                if (Context.USER_SERVICE.equals(name)) {
                    return mUserManager;
                }
                return super.getSystemService(name);
            }
        };

        when(mUserManager.createProfileForUser(anyString(), anyInt(), eq(TEST_PARENT_USER_ID)))
                .thenReturn(new UserInfo(TEST_PROFILE_USER_ID, null, 0));
    }

    @SmallTest
    public void testRunAllTasks() throws Exception {
        // GIVEN device profile owner provisioning was invoked
        createController();

        // WHEN starting the test run
        mController.start(mHandler);

        // THEN the create managed profile task is run first
        verifyTaskRun(CreateManagedProfileTask.class);

        // WHEN the task completes successfully
        CreateManagedProfileTask createManagedProfileTask = mock(CreateManagedProfileTask.class);
        when(createManagedProfileTask.getProfileUserId()).thenReturn(TEST_PROFILE_USER_ID);
        mController.onSuccess(createManagedProfileTask);

        // THEN the install existing package task is run
        taskSucceeded(InstallExistingPackageTask.class);

        // THEN the set device policy task is run
        taskSucceeded(SetDevicePolicyTask.class);

        // THEN the managed profile settings task is run
        taskSucceeded(ManagedProfileSettingsTask.class);

        // THEN the disable install shortcut listeners task is run
        taskSucceeded(DisableInstallShortcutListenersTask.class);

        // THEN the start managed profile task is run
        taskSucceeded(StartManagedProfileTask.class);

        // THEN the copy account to user task is run
        taskSucceeded(CopyAccountToUserTask.class);

        // THEN the provisioning complete callback should have happened
        verify(mCallback).provisioningTasksCompleted();
    }

    @MediumTest
    public void testCancel() throws Exception {
        // GIVEN device profile owner provisioning was invoked
        createController();

        // WHEN starting the test run
        mController.start(mHandler);

        // THEN the create managed profile task is run first
        verifyTaskRun(CreateManagedProfileTask.class);

        // WHEN the task completes successfully
        CreateManagedProfileTask createManagedProfileTask = mock(CreateManagedProfileTask.class);
        when(createManagedProfileTask.getProfileUserId()).thenReturn(TEST_PROFILE_USER_ID);
        mController.onSuccess(createManagedProfileTask);

        // THEN the install existing package task is run
        AbstractProvisioningTask task = verifyTaskRun(InstallExistingPackageTask.class);

        // latch used to wait for onCancelled callback
        final CountDownLatch latch = new CountDownLatch(1);
        doAnswer(new Answer<Void>() {
            @Override
            public Void answer(InvocationOnMock invocationOnMock) throws Throwable {
                latch.countDown();
                return null;
            }
        }).when(mCallback).cleanUpCompleted();

        // WHEN the user cancels the provisioning progress
        mController.cancel();

        // THEN the activity is informed that progress has been cancelled
        assertTrue(latch.await(1, TimeUnit.SECONDS));

        // THEN the managed profile is deleted
        verify(mUserManager).removeUserEvenWhenDisallowed(TEST_PROFILE_USER_ID);

        // WHEN the install existing package task eventually finishes
        mController.onSuccess(task);

        // THEN no more tasks should be run
        assertNull(mHandler.getLastTask());
    }

    @SmallTest
    public void testError() throws Exception {
        // GIVEN device profile owner provisioning was invoked
        createController();

        // WHEN starting the test run
        mController.start(mHandler);

        // THEN the create managed profile task is run first
        verifyTaskRun(CreateManagedProfileTask.class);

        // WHEN the task completes successfully
        CreateManagedProfileTask createManagedProfileTask = mock(CreateManagedProfileTask.class);
        when(createManagedProfileTask.getProfileUserId()).thenReturn(TEST_PROFILE_USER_ID);
        mController.onSuccess(createManagedProfileTask);

        // THEN the install existing package task is run
        AbstractProvisioningTask task = verifyTaskRun(InstallExistingPackageTask.class);

        // WHEN the task encountered an error
        mController.onError(task, 0);

        // THEN the activity should be informed about the error
        verify(mCallback).error(R.string.cant_set_up_profile,
                R.string.managed_provisioning_error_text, false);
    }

    private void createController() {
        mParams = new ProvisioningParams.Builder()
                .setDeviceAdminComponentName(TEST_ADMIN)
                .setProvisioningAction(ACTION_PROVISION_MANAGED_PROFILE)
                .build();

        mController = new ProfileOwnerProvisioningController(
                mContext,
                mParams,
                TEST_PARENT_USER_ID,
                mCallback,
                mFinalizationController);
    }
}
