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

package com.android.cts.deviceandprofileowner;

import android.accounts.Account;
import android.accounts.AccountManager;
import android.accounts.AccountManagerFuture;
import android.accounts.AuthenticatorException;
import android.accounts.OperationCanceledException;
import android.app.admin.DevicePolicyManager;
import android.content.Context;
import android.os.Bundle;
import android.os.UserManager;

import java.io.IOException;

/**
 * These tests verify that the device / profile owner can use account management APIs to add
 * accounts even when policies are set. The policies tested are
 * {@link DevicePolicyManager#setAccountManagementDisabled} and
 * {@link UserManager#DISALLOW_MODIFY_ACCOUNTS}.
 *
 * This test depends on {@link com.android.cts.devicepolicy.accountmanagement.MockAccountService},
 * which provides authenticator for a mock account type.
 *
 * Note that we cannot test account removal, because only the authenticator can remove an account
 * and the Dpc is not the authenticator for the mock account type.
 */
public class DpcAllowedAccountManagementTest extends BaseDeviceAdminTest {

    // Account type for MockAccountAuthenticator
    private final static String ACCOUNT_TYPE_1
            = "com.android.cts.devicepolicy.accountmanagement.account.type";
    private final static String ACCOUNT_TYPE_2 = "com.dummy.account";
    private final static Account ACCOUNT_0 = new Account("user0", ACCOUNT_TYPE_1);
    private final static Account ACCOUNT_1 = new Account("user1", ACCOUNT_TYPE_1);

    private AccountManager mAccountManager;

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        mAccountManager = (AccountManager) mContext.getSystemService(Context.ACCOUNT_SERVICE);
        clearAllAccountManagementDisabled();
        mDevicePolicyManager.clearUserRestriction(ADMIN_RECEIVER_COMPONENT,
                UserManager.DISALLOW_MODIFY_ACCOUNTS);
    }

    @Override
    protected void tearDown() throws Exception {
        clearAllAccountManagementDisabled();
        mDevicePolicyManager.clearUserRestriction(ADMIN_RECEIVER_COMPONENT,
                UserManager.DISALLOW_MODIFY_ACCOUNTS);
        super.tearDown();
    }

    public void testAccountManagementDisabled_setterAndGetter() {
        // Some local tests: adding and removing disabled accounts and make sure
        // DevicePolicyManager keeps track of the disabled set correctly
        assertEquals(0, mDevicePolicyManager.getAccountTypesWithManagementDisabled().length);

        mDevicePolicyManager.setAccountManagementDisabled(ADMIN_RECEIVER_COMPONENT, ACCOUNT_TYPE_1,
                true);
        // Test if disabling ACCOUNT_TYPE_2 affects ACCOUNT_TYPE_1
        mDevicePolicyManager.setAccountManagementDisabled(ADMIN_RECEIVER_COMPONENT, ACCOUNT_TYPE_2,
                false);
        assertEquals(1, mDevicePolicyManager.getAccountTypesWithManagementDisabled().length);
        assertEquals(ACCOUNT_TYPE_1,
                mDevicePolicyManager.getAccountTypesWithManagementDisabled()[0]);

        mDevicePolicyManager.setAccountManagementDisabled(ADMIN_RECEIVER_COMPONENT, ACCOUNT_TYPE_1,
                false);
        assertEquals(0, mDevicePolicyManager.getAccountTypesWithManagementDisabled().length);
    }

    public void testAccountManagementDisabled_profileAndDeviceOwnerCanAddAccount()
            throws AuthenticatorException, IOException, OperationCanceledException {
        mDevicePolicyManager.setAccountManagementDisabled(ADMIN_RECEIVER_COMPONENT, ACCOUNT_TYPE_1,
                true);

        assertEquals(0, mAccountManager.getAccountsByType(ACCOUNT_TYPE_1).length);
        // Management is disabled, but the device / profile owner is still allowed to use the APIs
        Bundle result = mAccountManager.addAccount(ACCOUNT_TYPE_1,
                null, null, null, null, null, null).getResult();

        // Normally the expected result of addAccount() is AccountManager returning
        // an intent to start the authenticator activity for adding new accounts.
        // But MockAccountAuthenticator returns a new account straightway.
        assertEquals(ACCOUNT_TYPE_1, result.getString(AccountManager.KEY_ACCOUNT_TYPE));
    }

    public void testUserRestriction_profileAndDeviceOwnerCanAddAccount()
            throws AuthenticatorException, IOException, OperationCanceledException {
        mDevicePolicyManager.addUserRestriction(ADMIN_RECEIVER_COMPONENT,
                UserManager.DISALLOW_MODIFY_ACCOUNTS);

        assertEquals(0, mAccountManager.getAccountsByType(ACCOUNT_TYPE_1).length);
        // Management is disabled, but the device / profile owner is still allowed to use the APIs
        Bundle result = mAccountManager.addAccount(ACCOUNT_TYPE_1,
                null, null, null, null, null, null).getResult();

        // Normally the expected result of addAccount() is AccountManager returning
        // an intent to start the authenticator activity for adding new accounts.
        // But MockAccountAuthenticator returns a new account straightway.
        assertEquals(ACCOUNT_TYPE_1, result.getString(AccountManager.KEY_ACCOUNT_TYPE));
    }

    private void clearAllAccountManagementDisabled() {
        for (String accountType : mDevicePolicyManager.getAccountTypesWithManagementDisabled()) {
            mDevicePolicyManager.setAccountManagementDisabled(ADMIN_RECEIVER_COMPONENT, accountType,
                    false);
        }
        assertEquals(0, mDevicePolicyManager.getAccountTypesWithManagementDisabled().length);
    }
}
