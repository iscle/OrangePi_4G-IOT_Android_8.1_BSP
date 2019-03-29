/*
 * Copyright (C) 2017 The Android Open Source Project
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

package android.content.sync.cts;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import android.accounts.Account;
import android.accounts.AccountManager;
import android.content.ContentResolver;
import android.content.Context;
import android.support.test.InstrumentationRegistry;
import android.support.test.runner.AndroidJUnit4;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

/**
 * Device side code for {@link android.content.cts.InvalidSyncAuthoritiesHostTest}
 */
@RunWith(AndroidJUnit4.class)
public class InvalidSyncAuthoritiesDeviceTest {

    private static final String VALID_TEST_AUTHORITY = "android.content.sync.cts.authority";
    private static final String INVALID_TEST_AUTHORITY = "invalid.authority";
    private static final String VALID_TEST_ACCOUNT_TYPE = "android.content.sync.cts.accounttype";

    private Account mInvalidAccount;
    private Account mValidAccount;
    private AccountManager mAccountManager;

    @Before
    public void setUp() {
        final Context context = InstrumentationRegistry.getTargetContext();
        mAccountManager = context.getSystemService(AccountManager.class);
        mInvalidAccount = new Account("invalid_test_name", "invalid_test_type");
        final Account[] accounts = mAccountManager.getAccountsByType(VALID_TEST_ACCOUNT_TYPE);
        mValidAccount = (accounts.length == 0) ? createTestAccount() : accounts[0];
    }

    private Account createTestAccount() {
        mValidAccount = new Account("testAccount", VALID_TEST_ACCOUNT_TYPE);
        assertTrue("Failed to create a valid test account",
                mAccountManager.addAccountExplicitly(mValidAccount, "password", null));
        return mValidAccount;
    }

    @Test
    public void populateAndTestSyncAutomaticallyBeforeReboot() {
        ContentResolver.setSyncAutomatically(mValidAccount, VALID_TEST_AUTHORITY, true);
        ContentResolver.setSyncAutomatically(mValidAccount, INVALID_TEST_AUTHORITY, true);
        ContentResolver.setSyncAutomatically(mInvalidAccount, INVALID_TEST_AUTHORITY, true);
        ContentResolver.setSyncAutomatically(mInvalidAccount, VALID_TEST_AUTHORITY, true);

        assertTrue(ContentResolver.getSyncAutomatically(mValidAccount, VALID_TEST_AUTHORITY));
        assertTrue(ContentResolver.getSyncAutomatically(mValidAccount, INVALID_TEST_AUTHORITY));
        // checking for invalid accounts may already return false depending on when the broadcast
        // LOGIN_ACCOUNTS_CHANGED_ACTION was received by SyncManager
    }

    @Test
    public void testSyncAutomaticallyAfterReboot() {
        assertTrue(ContentResolver.getSyncAutomatically(mValidAccount, VALID_TEST_AUTHORITY));
        assertFalse(ContentResolver.getSyncAutomatically(mValidAccount, INVALID_TEST_AUTHORITY));
        assertFalse(ContentResolver.getSyncAutomatically(mInvalidAccount, VALID_TEST_AUTHORITY));
        assertFalse(ContentResolver.getSyncAutomatically(mInvalidAccount, INVALID_TEST_AUTHORITY));
    }

    @Test
    public void removeTestAccount() {
        // To use as a teardown step from the hostside test
        mAccountManager.removeAccountExplicitly(mValidAccount);
    }
}
