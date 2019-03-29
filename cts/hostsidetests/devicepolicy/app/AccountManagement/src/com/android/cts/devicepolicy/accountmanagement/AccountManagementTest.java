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
 * This test depend on {@link MockAccountService}, which provides authenticator of type
 * {@link MockAccountService#ACCOUNT_TYPE}.
 */
public class AccountManagementTest extends AndroidTestCase {

    // Account type for MockAccountAuthenticator
    private final static Account ACCOUNT = new Account("user0",
            MockAccountAuthenticator.ACCOUNT_TYPE);

    private AccountManager mAccountManager;

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        mAccountManager = (AccountManager) mContext.getSystemService(Context.ACCOUNT_SERVICE);
    }

    @Override
    protected void tearDown() throws Exception {
        mAccountManager.removeAccountExplicitly(ACCOUNT);
        assertEquals(0, mAccountManager.getAccountsByType(MockAccountAuthenticator.ACCOUNT_TYPE)
                .length);
        super.tearDown();
    }

    public void testAddAccount_blocked() throws AuthenticatorException,
            IOException, OperationCanceledException {
        assertEquals(0, mAccountManager.getAccountsByType(MockAccountAuthenticator.ACCOUNT_TYPE)
                .length);
        // Management is disabled, adding account should fail.
        try {
            mAccountManager.addAccount(MockAccountAuthenticator.ACCOUNT_TYPE,
                null, null, null, null, null, null).getResult();
            fail("Expected OperationCanceledException is not thrown.");
        } catch (OperationCanceledException e) {
            // Expected
        }
        assertEquals(0, mAccountManager.getAccountsByType(MockAccountAuthenticator.ACCOUNT_TYPE)
                .length);
    }

    public void testAddAccount_allowed() throws Exception {
        Bundle result = mAccountManager.addAccount(MockAccountAuthenticator.ACCOUNT_TYPE,
                null, null, null, null, null, null).getResult();

        // Normally the expected result of addAccount() is AccountManager returning
        // an intent to start the authenticator activity for adding new accounts.
        // But MockAccountAuthenticator returns a new account straightway.
        String accountType = result.getString(AccountManager.KEY_ACCOUNT_TYPE);
        String accountName = result.getString(AccountManager.KEY_ACCOUNT_NAME);
        assertEquals(MockAccountAuthenticator.ACCOUNT_TYPE, accountType);

        // Further verify that getAuthToken is working.
        Account account = new Account(accountName, accountType);
        String authToken = mAccountManager.blockingGetAuthToken(account, "authTokenType", false);
        assertEquals(MockAccountAuthenticator.AUTH_TOKEN, authToken);
    }

    public void testRemoveAccount_blocked() throws AuthenticatorException,
            IOException, OperationCanceledException {
        assertEquals(0, mAccountManager.getAccountsByType(MockAccountAuthenticator.ACCOUNT_TYPE)
                .length);
        // First prepare some accounts by manually adding them,
        // setAccountManagementDisabled(true) should not stop addAccountExplicitly().
        assertTrue(mAccountManager.addAccountExplicitly(ACCOUNT, "password", null));
        assertEquals(1, mAccountManager.getAccountsByType(MockAccountAuthenticator.ACCOUNT_TYPE)
                .length);

        // Removing account should fail, as we just disabled it.
        try {
            mAccountManager.removeAccount(ACCOUNT, null, null).getResult();
            fail("Expected OperationCanceledException is not thrown.");
        } catch (OperationCanceledException e) {
            // Expected
        }
        // Make sure the removal actually fails.
        Account[] accounts = mAccountManager.getAccountsByType(
                MockAccountAuthenticator.ACCOUNT_TYPE);
        assertEquals(1, accounts.length);
        assertEquals(ACCOUNT, accounts[0]);
    }

    public void testRemoveAccount_allowed() throws Exception {
        assertEquals(0, mAccountManager.getAccountsByType(MockAccountAuthenticator.ACCOUNT_TYPE)
                .length);
        // First prepare some accounts by manually adding them,
        // setAccountManagementDisabled(true) should not stop addAccountExplicitly().
        assertTrue(mAccountManager.addAccountExplicitly(ACCOUNT, "password", null));
        assertEquals(1, mAccountManager.getAccountsByType(MockAccountAuthenticator.ACCOUNT_TYPE)
                .length);
        assertTrue(mAccountManager.removeAccount(ACCOUNT, null, null).getResult());

        // Make sure the removal actually succeeded.
        assertEquals(0, mAccountManager.getAccountsByType(MockAccountAuthenticator.ACCOUNT_TYPE)
                .length);
    }
}
