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
import static org.junit.Assert.assertEquals;
import static org.mockito.AdditionalMatchers.aryEq;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.anyInt;
import static org.mockito.Matchers.anyString;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import android.content.pm.UserInfo;
import android.os.UserManager;
import android.support.test.InstrumentationRegistry;
import android.support.test.filters.SmallTest;

import com.android.managedprovisioning.model.ProvisioningParams;
import com.android.managedprovisioning.task.nonrequiredapps.NonRequiredAppsLogic;

import java.util.Arrays;
import java.util.HashSet;
import java.util.LinkedHashSet;

import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

@SmallTest
public class CreateManagedProfileTaskTest {
    private static final int TEST_PARENT_USER_ID = 111;
    private static final int TEST_USER_ID = 123;
    private static final String TEST_DPC_PACKAGE_NAME = "com.test.dpc";
    private static final ProvisioningParams TEST_PARAMS = new ProvisioningParams.Builder()
            .setDeviceAdminPackageName(TEST_DPC_PACKAGE_NAME)
            .setProvisioningAction(ACTION_PROVISION_MANAGED_PROFILE)
            .build();
    private static final String[] SYSTEM_APPS_TO_DELETE = {"app.a", "app.b"};

    private @Mock UserManager mUserManager;
    private @Mock NonRequiredAppsLogic mLogic;
    private @Mock AbstractProvisioningTask.Callback mCallback;

    private CreateManagedProfileTask mTask;

    @Before
    public void setUp() {
        MockitoAnnotations.initMocks(this);
        mTask = new CreateManagedProfileTask(InstrumentationRegistry.getTargetContext(),
                TEST_PARAMS, mCallback, mUserManager, mLogic);
        // GIVEN that a set of system apps should not be installed on the new user
        when(mLogic.getSystemAppsToRemove(TEST_PARENT_USER_ID))
            .thenReturn(new LinkedHashSet<String>(Arrays.asList(SYSTEM_APPS_TO_DELETE)));
    }

    @Test
    public void testSuccess() {
        // GIVEN that a new profile can be created
        when(mUserManager.createProfileForUserEvenWhenDisallowed(
                        anyString(), anyInt(), eq(TEST_PARENT_USER_ID),
                        aryEq(SYSTEM_APPS_TO_DELETE)))
                .thenReturn(new UserInfo(TEST_USER_ID, null, 0));

        // WHEN the CreateManagedProfileTask is run
        mTask.run(TEST_PARENT_USER_ID);
        // THEN success callback should have happened
        verify(mCallback).onSuccess(mTask);
        // THEN any other callback should not happen
        verifyNoMoreInteractions(mCallback);
        // THEN list of system apps in the new user should be saved
        verify(mLogic).maybeTakeSystemAppsSnapshot(TEST_USER_ID);
        // THEN the id of the new profile should be returned by getProfileUserId
        assertEquals(TEST_USER_ID, mTask.getProfileUserId());
    }

    @Test
    public void testError() {
        // GIVEN that a new profile can't be created
        when(mUserManager.createProfileForUserEvenWhenDisallowed(
                        anyString(), anyInt(), eq(TEST_PARENT_USER_ID),
                        aryEq(SYSTEM_APPS_TO_DELETE)))
                .thenReturn(null);

        // WHEN the CreateManagedProfileTask is run
        mTask.run(TEST_PARENT_USER_ID);
        // THEN error callback should have happened
        verify(mCallback).onError(mTask, 0);
        // THEN any other callback should not happen
        verifyNoMoreInteractions(mCallback);
    }
}
