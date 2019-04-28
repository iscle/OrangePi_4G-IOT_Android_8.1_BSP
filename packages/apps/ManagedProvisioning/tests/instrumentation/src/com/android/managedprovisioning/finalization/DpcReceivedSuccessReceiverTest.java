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

package com.android.managedprovisioning.finalization;

import static android.app.admin.DeviceAdminReceiver.ACTION_PROFILE_PROVISIONING_COMPLETE;
import static android.app.admin.DevicePolicyManager.ACTION_MANAGED_PROFILE_PROVISIONED;
import static android.app.admin.DevicePolicyManager.EXTRA_PROVISIONING_ACCOUNT_TO_MIGRATE;
import static org.mockito.Matchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import android.accounts.Account;
import android.content.Context;
import android.content.Intent;
import android.os.UserHandle;
import android.test.AndroidTestCase;
import android.test.suitebuilder.annotation.SmallTest;

import com.android.managedprovisioning.common.Utils;

import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.mockito.invocation.InvocationOnMock;

import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;

/**
 * Unit tests for {@link DpcReceivedSuccessReceiver}.
 */
public class DpcReceivedSuccessReceiverTest extends AndroidTestCase {
    private static final int SEND_BROADCAST_TIMEOUT_SECONDS = 1;
    private static final String TEST_MDM_PACKAGE_NAME = "mdm.package.name";
    private static final Account TEST_ACCOUNT = new Account("test@account.com", "account.type");
    private static final Intent TEST_INTENT = new Intent(ACTION_PROFILE_PROVISIONING_COMPLETE);
    private static final UserHandle MANAGED_PROFILE_USER_HANDLE = UserHandle.of(123);

    @Mock private Context mContext;
    @Mock private Utils mUtils;

    @Override
    public void setUp() {
        // this is necessary for mockito to work
        System.setProperty("dexmaker.dexcache", getContext().getCacheDir().toString());
        MockitoAnnotations.initMocks(this);
    }

    @SmallTest
    public void testNoAccountMigration() {
        // GIVEN that no account migration occurred during provisioning
        final DpcReceivedSuccessReceiver receiver = new DpcReceivedSuccessReceiver(null, false,
                MANAGED_PROFILE_USER_HANDLE, TEST_MDM_PACKAGE_NAME, mUtils);

        // WHEN the profile provisioning complete intent was received by the DPC
        receiver.onReceive(mContext, TEST_INTENT);

        // THEN an intent should be sent to the primary user
        ArgumentCaptor<Intent> intentCaptor = ArgumentCaptor.forClass(Intent.class);
        verify(mContext).sendBroadcast(intentCaptor.capture());

        // THEN the broadcast action is ACTION_MANAGED_PROFILE_PROVISIONED
        assertEquals(ACTION_MANAGED_PROFILE_PROVISIONED, intentCaptor.getValue().getAction());

        // THEN the receiver package is the DPC
        assertEquals(TEST_MDM_PACKAGE_NAME, intentCaptor.getValue().getPackage());

        // THEN the extra user handle should be of managed profile
        assertEquals(MANAGED_PROFILE_USER_HANDLE,
                intentCaptor.getValue().getExtra(Intent.EXTRA_USER));
    }

    @SmallTest
    public void testAccountMigration() throws Exception {
        // GIVEN that account migration occurred during provisioning
        final DpcReceivedSuccessReceiver receiver = new DpcReceivedSuccessReceiver(TEST_ACCOUNT,
                false /* keepAccountMigrated */, MANAGED_PROFILE_USER_HANDLE, TEST_MDM_PACKAGE_NAME,
                mUtils);

        // WHEN receiver.onReceive is called
        invokeOnReceiveAndVerifyIntent(receiver);

        // THEN the account should have been removed from the primary user
        verify(mUtils).removeAccount(mContext, TEST_ACCOUNT);
    }

    @SmallTest
    public void testAccountCopy() throws Exception {
        // GIVEN that account copy occurred during provisioning
        final DpcReceivedSuccessReceiver receiver = new DpcReceivedSuccessReceiver(TEST_ACCOUNT,
                true /* keepAccountMigrated */, MANAGED_PROFILE_USER_HANDLE, TEST_MDM_PACKAGE_NAME,
                mUtils);

        // WHEN receiver.onReceive is called
        invokeOnReceiveAndVerifyIntent(receiver);

        // THEN the account is not removed from the primary user
        verify(mUtils, never()).removeAccount(mContext, TEST_ACCOUNT);
    }

    private void invokeOnReceiveAndVerifyIntent(final DpcReceivedSuccessReceiver receiver)
            throws InterruptedException {
        // prepare a semaphore to handle AsyncTask usage
        final Semaphore semaphore = new Semaphore(0);
        doAnswer((InvocationOnMock invocation) -> {
            semaphore.release(1);
            return null;
        }).when(mContext).sendBroadcast(any(Intent.class));

        // WHEN the profile provisioning complete intent was received by the DPC
        receiver.onReceive(mContext, TEST_INTENT);

        assertTrue(semaphore.tryAcquire(SEND_BROADCAST_TIMEOUT_SECONDS, TimeUnit.SECONDS));

        // THEN an intent should be sent to the primary user
        ArgumentCaptor<Intent> intentCaptor = ArgumentCaptor.forClass(Intent.class);
        verify(mContext).sendBroadcast(intentCaptor.capture());

        // THEN the broadcast action is ACTION_MANAGED_PROFILE_PROVISIONED
        assertEquals(ACTION_MANAGED_PROFILE_PROVISIONED, intentCaptor.getValue().getAction());

        // THEN the receiver package is the DPC
        assertEquals(TEST_MDM_PACKAGE_NAME, intentCaptor.getValue().getPackage());

        // THEN the extra user handle should be of managed profile
        assertEquals(MANAGED_PROFILE_USER_HANDLE,
                intentCaptor.getValue().getExtra(Intent.EXTRA_USER));

        // THEN the account was added to the broadcast
        assertEquals(TEST_ACCOUNT, intentCaptor.getValue().getParcelableExtra(
                EXTRA_PROVISIONING_ACCOUNT_TO_MIGRATE));
    }
}
