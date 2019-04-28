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
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.verifyZeroInteractions;
import static org.mockito.Mockito.when;

import android.app.IActivityManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.RemoteException;
import android.os.UserHandle;
import android.test.AndroidTestCase;
import android.test.suitebuilder.annotation.SmallTest;

import com.android.managedprovisioning.model.ProvisioningParams;

import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.mockito.invocation.InvocationOnMock;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/**
 * Unit tests for {@link StartManagedProfileTask}.
 */
public class StartManagedProfileTaskTest extends AndroidTestCase {
    private static final int TEST_USER_ID = 123;
    private static final String TEST_MDM_PACKAGE_NAME = "com.test.mdm";
    private static final ProvisioningParams TEST_PARAMS = new ProvisioningParams.Builder()
            .setDeviceAdminPackageName(TEST_MDM_PACKAGE_NAME)
            .setProvisioningAction(ACTION_PROVISION_MANAGED_PROFILE)
            .build();
    private static final Intent UNLOCK_INTENT = new Intent(Intent.ACTION_USER_UNLOCKED)
            .putExtra(Intent.EXTRA_USER_HANDLE, TEST_USER_ID);

    @Mock private IActivityManager mIActivityManager;
    @Mock private Context mContext;
    @Mock private AbstractProvisioningTask.Callback mCallback;
    private ArgumentCaptor<BroadcastReceiver> mReceiverCaptor =
            ArgumentCaptor.forClass(BroadcastReceiver.class);

    private StartManagedProfileTask mTask;
    private HandlerThread mHandlerThread;
    private final CountDownLatch mStartInBackgroundLatch = new CountDownLatch(1);
    private final CountDownLatch mSuccessLatch = new CountDownLatch(1);

    public void setUp() {
        // this is necessary for mockito to work
        System.setProperty("dexmaker.dexcache", getContext().getCacheDir().toString());
        MockitoAnnotations.initMocks(this);

        mHandlerThread = new HandlerThread("Test thread");
        mHandlerThread.start();

        mTask = new StartManagedProfileTask(mIActivityManager, mContext, TEST_PARAMS, mCallback);

        // register a countdown latch for the success callback
        doAnswer((InvocationOnMock invocationOnMock) -> {
                mSuccessLatch.countDown();
                return null;
            }).when(mCallback).onSuccess(mTask);
    }

    public void tearDown() {
        mHandlerThread.quitSafely();
    }

    @SmallTest
    public void testSuccess() throws Exception {
        // GIVEN that starting the user succeeds
        doAnswer((InvocationOnMock invocationOnMock) -> {
                mStartInBackgroundLatch.countDown();
                return true;
            }).when(mIActivityManager).startUserInBackground(TEST_USER_ID);

        // WHEN the task is run (on a handler thread to avoid deadlocks)
        new Handler(mHandlerThread.getLooper()).post(() -> mTask.run(TEST_USER_ID));

        // THEN user unlock should have been called
        assertTrue(mStartInBackgroundLatch.await(1, TimeUnit.SECONDS));

        // THEN an unlock receiver should be registered
        verify(mContext).registerReceiverAsUser(
                mReceiverCaptor.capture(),
                eq(UserHandle.of(TEST_USER_ID)),
                eq(StartManagedProfileTask.UNLOCK_FILTER),
                eq(null), eq(null));

        // THEN the success callback should not have been called
        verifyZeroInteractions(mCallback);

        // WHEN the unlock broadcast is sent
        mReceiverCaptor.getValue().onReceive(mContext, UNLOCK_INTENT);

        // THEN the success callback should be called
        assertTrue(mSuccessLatch.await(1, TimeUnit.SECONDS));
        verify(mCallback).onSuccess(mTask);
        verifyNoMoreInteractions(mCallback);

        verify(mContext).unregisterReceiver(mReceiverCaptor.getValue());
    }

    @SmallTest
    public void testError() throws Exception {
        // GIVEN that starting the user in background fails
        when(mIActivityManager.startUserInBackground(TEST_USER_ID)).thenReturn(false);

        // WHEN the task is run
        mTask.run(TEST_USER_ID);

        // THEN an unlock receiver should be registered
        verify(mContext).registerReceiverAsUser(
                mReceiverCaptor.capture(),
                eq(UserHandle.of(TEST_USER_ID)),
                eq(StartManagedProfileTask.UNLOCK_FILTER),
                eq(null), eq(null));

        // THEN the error callback should have been called
        verify(mCallback).onError(mTask, 0);
        verifyNoMoreInteractions(mCallback);

        verify(mContext).unregisterReceiver(mReceiverCaptor.getValue());
    }

    @SmallTest
    public void testRemoteException() throws Exception {
        // GIVEN that starting the user in background throws a remote exception
        when(mIActivityManager.startUserInBackground(TEST_USER_ID))
                .thenThrow(new RemoteException());

        // WHEN the task is run
        mTask.run(TEST_USER_ID);

        // THEN an unlock receiver should be registered
        verify(mContext).registerReceiverAsUser(
                mReceiverCaptor.capture(),
                eq(UserHandle.of(TEST_USER_ID)),
                eq(StartManagedProfileTask.UNLOCK_FILTER),
                eq(null), eq(null));

        // THEN the error callback should have been called
        verify(mCallback).onError(mTask, 0);
        verifyNoMoreInteractions(mCallback);

        verify(mContext).unregisterReceiver(mReceiverCaptor.getValue());
    }
}
