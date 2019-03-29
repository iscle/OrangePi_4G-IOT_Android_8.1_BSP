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

package com.android.cts.devicepolicy.accountmanagement;

import android.accounts.Account;
import android.accounts.AccountManager;
import android.accounts.AccountManagerFuture;
import android.accounts.AuthenticatorException;
import android.accounts.OperationCanceledException;
import android.content.Context;
import android.os.Bundle;
import android.test.AndroidTestCase;

import java.io.IOException;

/**
 * Functionality tests for
 * {@link android.app.admin.DevicePolicyManager#setAccountManagementDisabled}
 * and (@link android.os.UserManager#DISALLOW_MODIFY_ACCOUNTS}
 *
 * This test depends on {@link MockAccountService}, which provides authenticator of type
 * {@link MockAccountService#ACCOUNT_TYPE}.
 */
public class AccountUtilsTest extends AndroidTestCase {

    // Account type for MockAccountAuthenticator
    private final static Account ACCOUNT = new Account("user0",
            MockAccountAuthenticator.ACCOUNT_TYPE);

    private AccountManager mAccountManager;

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        mAccountManager = (AccountManager) mContext.getSystemService(Context.ACCOUNT_SERVICE);
    }

    public void testAddAccountExplicitly() throws Exception {
        assertEquals(0, mAccountManager.getAccountsByType(MockAccountAuthenticator.ACCOUNT_TYPE)
                .length);
        assertTrue(mAccountManager.addAccountExplicitly(ACCOUNT, "password", null));
        assertEquals(1, mAccountManager.getAccountsByType(MockAccountAuthenticator.ACCOUNT_TYPE)
                .length);
    }

    public void testRemoveAccountExplicitly() throws Exception {
        assertEquals(1, mAccountManager.getAccountsByType(MockAccountAuthenticator.ACCOUNT_TYPE)
                .length);
        mAccountManager.removeAccountExplicitly(ACCOUNT);
        assertEquals(0, mAccountManager.getAccountsByType(MockAccountAuthenticator.ACCOUNT_TYPE)
                .length);
    }
}

