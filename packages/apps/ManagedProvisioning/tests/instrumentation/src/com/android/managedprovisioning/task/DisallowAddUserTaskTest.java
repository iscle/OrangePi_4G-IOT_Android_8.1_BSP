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

import static org.mockito.Mockito.any;
import static org.mockito.Mockito.anyBoolean;
import static org.mockito.Mockito.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import android.content.Context;
import android.content.pm.UserInfo;
import android.os.UserHandle;
import android.os.UserManager;
import android.test.AndroidTestCase;
import android.test.suitebuilder.annotation.SmallTest;

import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.Arrays;
import java.util.Collections;

/**
 * Unit-tests for {@link DisallowAddUserTask}.
 */
public class DisallowAddUserTaskTest extends AndroidTestCase {
    @Mock private Context mockContext;
    @Mock private UserManager mockUserManager;
    @Mock private AbstractProvisioningTask.Callback mCallback;

    // Normal cases.
    private UserInfo primaryUser = new UserInfo(0, "Primary",
            UserInfo.FLAG_PRIMARY | UserInfo.FLAG_ADMIN);

    // Split-system-user cases.
    private UserInfo systemUser = new UserInfo(UserHandle.USER_SYSTEM, "System", 0 /* flags */);
    private UserInfo meatUser = new UserInfo(10, "Primary",
            UserInfo.FLAG_PRIMARY | UserInfo.FLAG_ADMIN);

    @Override
    public void setUp() {
        // this is necessary for mockito to work
        System.setProperty("dexmaker.dexcache", getContext().getCacheDir().toString());

        MockitoAnnotations.initMocks(this);

        when(mockContext.getSystemService(Context.USER_SERVICE)).thenReturn(mockUserManager);
        // Setup sensible default responses.
        when(mockUserManager.hasUserRestriction(anyString(), any(UserHandle.class)))
                .thenReturn(false);
    }

    @SmallTest
    public void testMaybeDisallowAddUsers_normalSystem() {
        // GIVEN that only one user exists on the device and the system doesn't have a split system
        // user
        when(mockUserManager.getUsers()).thenReturn(Collections.singletonList(primaryUser));
        final DisallowAddUserTask task =
                new DisallowAddUserTask(false, mockContext, null, mCallback);

        // WHEN running the DisallowAddUserTask on the single user
        task.run(primaryUser.id);

        // THEN the user restriction should be set
        verify(mockUserManager).setUserRestriction(UserManager.DISALLOW_ADD_USER, true,
                primaryUser.getUserHandle());
        verify(mCallback).onSuccess(task);
    }

    @SmallTest
    public void testMaybeDisallowAddUsers_normalSystem_restrictionAlreadySetupForOneUser() {
        // GIVEN that only one user exists on the device and the system doesn't have a split system
        // user
        when(mockUserManager.getUsers()).thenReturn(Collections.singletonList(primaryUser));
        final DisallowAddUserTask task =
                new DisallowAddUserTask(false, mockContext, null, mCallback);

        // GIVEN that the user restriction has already been set
        when(mockUserManager.hasUserRestriction(UserManager.DISALLOW_ADD_USER,
                primaryUser.getUserHandle()))
                .thenReturn(true);

        // WHEN running the DisallowAddUserTask on the single user
        task.run(primaryUser.id);

        // THEN the user restriction should not be set
        verify(mockUserManager, never()).setUserRestriction(anyString(), anyBoolean(),
                any(UserHandle.class));
        verify(mCallback).onSuccess(task);
    }

    @SmallTest
    public void testMaybeDisallowAddUsers_splitUserSystem_meatUserDeviceOwner() {
        // GIVEN that we have a split system user and a single meat user on the device
        when(mockUserManager.getUsers()).thenReturn(Arrays.asList(new UserInfo[]{
                systemUser, meatUser}));
        final DisallowAddUserTask task =
                new DisallowAddUserTask(true, mockContext, null, mCallback);

        // WHEN running the DisallowAddUserTask on the meat user
        task.run(meatUser.id);

        // THEN the user restriction should be added on both users
        verify(mockUserManager).setUserRestriction(UserManager.DISALLOW_ADD_USER, true,
                systemUser.getUserHandle());
        verify(mockUserManager).setUserRestriction(UserManager.DISALLOW_ADD_USER, true,
                meatUser.getUserHandle());
        verify(mCallback).onSuccess(task);
    }

    @SmallTest
    public void testMaybeDisallowAddUsers_splitUserSystem_systemDeviceOwner() {
        // GIVEN that we have a split system user and only the system user on the device
        when(mockUserManager.getUsers()).thenReturn(Collections.singletonList(systemUser));
        final DisallowAddUserTask task =
                new DisallowAddUserTask(true, mockContext, null, mCallback);

        // WHEN running the DisallowAddUserTask on the system user
        task.run(systemUser.id);

        // THEN the user restriction should not be set
        verify(mockUserManager, never()).setUserRestriction(anyString(), anyBoolean(),
                any(UserHandle.class));
        verify(mCallback).onSuccess(task);
    }
}
