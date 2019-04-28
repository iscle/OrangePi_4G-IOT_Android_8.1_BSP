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

import static org.mockito.Matchers.any;
import static org.mockito.Matchers.anyLong;
import static org.mockito.Matchers.eq;
import static org.mockito.Matchers.nullable;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.verifyZeroInteractions;
import static org.mockito.Mockito.when;

import android.accounts.Account;
import android.accounts.AccountManager;
import android.accounts.AccountManagerCallback;
import android.accounts.AccountManagerFuture;
import android.accounts.OperationCanceledException;
import android.content.Context;
import android.os.Handler;
import android.os.UserHandle;
import android.support.test.filters.FlakyTest;
import android.test.AndroidTestCase;
import android.test.suitebuilder.annotation.SmallTest;

import com.android.managedprovisioning.model.ProvisioningParams;

import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.concurrent.TimeUnit;

/**
 * Unit tests for {@link CopyAccountToUserTask}.
 */
@FlakyTest // TODO: http://b/34117742
public class CopyAccountToUserTaskTest extends AndroidTestCase {
    private static final String TEST_MDM_PACKAGE_NAME = "mdm.package.name";
    private static final Account TEST_ACCOUNT = new Account("test@afw-test.com", "com.google");
    private static final int TEST_SOURCE_USER_ID = 1;
    private static final int TEST_TARGET_USER_ID = 2;

    @Mock private Context mContext;
    @Mock private AccountManager mAccountManager;
    @Mock private AccountManagerFuture mAccountManagerFuture;
    @Mock private AbstractProvisioningTask.Callback mCallback;
    private CopyAccountToUserTask mTask;

    public void setUp() {
        // this is necessary for mockito to work
        System.setProperty("dexmaker.dexcache", getContext().getCacheDir().toString());
        MockitoAnnotations.initMocks(this);

        when(mContext.getSystemService(Context.ACCOUNT_SERVICE)).thenReturn(mAccountManager);
        when(mAccountManager.copyAccountToUser(
                eq(TEST_ACCOUNT),
                eq(UserHandle.of(TEST_SOURCE_USER_ID)),
                eq(UserHandle.of(TEST_TARGET_USER_ID)),
                nullable(AccountManagerCallback.class),
                nullable(Handler.class))).thenReturn(mAccountManagerFuture);
    }

    @SmallTest
    public void testRun() throws Exception {
        // GIVEN an account on the source user
        createTask(TEST_SOURCE_USER_ID, TEST_ACCOUNT);

        // GIVEN no timeout or error occurred during migration
        when(mAccountManagerFuture.getResult(anyLong(), any(TimeUnit.class))).thenReturn(true);

        // THEN when the task is run
        mTask.run(TEST_TARGET_USER_ID);

        // THEN the account migration was triggered
        verify(mAccountManager).copyAccountToUser(
                eq(TEST_ACCOUNT),
                eq(UserHandle.of(TEST_SOURCE_USER_ID)),
                eq(UserHandle.of(TEST_TARGET_USER_ID)),
                nullable(AccountManagerCallback.class),
                nullable(Handler.class));

        // THEN the success callback should be given
        verify(mCallback).onSuccess(mTask);
        verifyNoMoreInteractions(mCallback);
    }

    @SmallTest
    public void testRun_error() throws Exception {
        // GIVEN an account on the source user
        createTask(TEST_SOURCE_USER_ID, TEST_ACCOUNT);

        // GIVEN no timeout or error occurred during migration
        when(mAccountManagerFuture.getResult(anyLong(), any(TimeUnit.class))).thenReturn(false);

        // THEN when the task is run
        mTask.run(TEST_TARGET_USER_ID);

        // THEN the account migration was triggered
        verify(mAccountManager).copyAccountToUser(
                eq(TEST_ACCOUNT),
                eq(UserHandle.of(TEST_SOURCE_USER_ID)),
                eq(UserHandle.of(TEST_TARGET_USER_ID)),
                nullable(AccountManagerCallback.class),
                nullable(Handler.class));

        // THEN the success callback should be given
        verify(mCallback).onSuccess(mTask);
        verifyNoMoreInteractions(mCallback);
    }

    @SmallTest
    public void testRun_nullAccount() {
        // GIVEN no account is passed
        createTask(TEST_SOURCE_USER_ID, null);

        // WHEN running the task
        mTask.run(TEST_TARGET_USER_ID);

        // THEN nothing should happen
        verifyZeroInteractions(mAccountManager);

        // THEN the success callback should still occur
        verify(mCallback).onSuccess(mTask);
        verifyNoMoreInteractions(mCallback);
    }

    @SmallTest
    public void testRun_sameUser() {
        // GIVEN an account on a user
        createTask(TEST_SOURCE_USER_ID, TEST_ACCOUNT);

        // WHEN running the task for the same user
        mTask.run(TEST_SOURCE_USER_ID);

        // THEN nothing should happen
        verifyZeroInteractions(mAccountManager);

        // THEN the success callback should still occur
        verify(mCallback).onSuccess(mTask);
        verifyNoMoreInteractions(mCallback);
    }

    @SmallTest
    public void testMaybeCopyAccount_success() throws Exception {
        // GIVEN an account on the source user
        createTask(TEST_SOURCE_USER_ID, TEST_ACCOUNT);

        // GIVEN no timeout or error occurred during migration
        when(mAccountManagerFuture.getResult(anyLong(), any(TimeUnit.class))).thenReturn(true);

        // WHEN copying the account from the source user to the target user
        // THEN the account migration succeeds
        assertTrue(mTask.maybeCopyAccount(TEST_TARGET_USER_ID));
    }

    @SmallTest
    public void testMaybeCopyAccount_error() throws Exception {
        // GIVEN an account on the source user
        createTask(TEST_SOURCE_USER_ID, TEST_ACCOUNT);

        // GIVEN an error occurred during migration
        when(mAccountManagerFuture.getResult(anyLong(), any(TimeUnit.class))).thenReturn(false);

        // WHEN copying the account from the source user to the target user
        // THEN the account migration fails
        assertFalse(mTask.maybeCopyAccount(TEST_TARGET_USER_ID));
    }

    @SmallTest
    public void testMaybeCopyAccount_timeout() throws Exception {
        // GIVEN an account on the source user
        createTask(TEST_SOURCE_USER_ID, TEST_ACCOUNT);

        // GIVEN a timeout occurred during migration, which is indicated by an
        // OperationCanceledException
        when(mAccountManagerFuture.getResult(anyLong(), any(TimeUnit.class)))
                .thenThrow(new OperationCanceledException());

        // WHEN copying the account from the source user to the target user
        // THEN the account migration fails
        assertFalse(mTask.maybeCopyAccount(TEST_TARGET_USER_ID));
    }

    private void createTask(int sourceUserId, Account account) {
        ProvisioningParams params = new ProvisioningParams.Builder()
                .setProvisioningAction(ACTION_PROVISION_MANAGED_PROFILE)
                .setDeviceAdminPackageName(TEST_MDM_PACKAGE_NAME)
                .setAccountToMigrate(account)
                .build();
        mTask = new CopyAccountToUserTask(sourceUserId, mContext, params, mCallback);
    }
}
