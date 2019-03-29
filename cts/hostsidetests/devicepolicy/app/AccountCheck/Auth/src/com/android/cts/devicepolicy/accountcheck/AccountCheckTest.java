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
package com.android.cts.devicepolicy.accountcheck;

import android.accounts.Account;
import android.accounts.AccountManager;
import android.app.admin.DevicePolicyManager;
import android.content.ComponentName;
import android.os.Bundle;
import android.os.SystemClock;
import android.test.AndroidTestCase;
import android.util.Log;

public class AccountCheckTest extends AndroidTestCase {
    private static final String TAG = "AccountCheckTest";

    private static final String ACCOUNT_TYPE = "com.android.cts.devicepolicy.accountcheck";
    private static final String ACCOUNT_FEATURE_ALLOWED =
            DevicePolicyManager.ACCOUNT_FEATURE_DEVICE_OR_PROFILE_OWNER_ALLOWED;
    private static final String ACCOUNT_FEATURE_DISALLOWED =
            DevicePolicyManager.ACCOUNT_FEATURE_DEVICE_OR_PROFILE_OWNER_DISALLOWED;

    private DevicePolicyManager mDevicePolicyManager;
    private AccountManager mAccountManager;

    @Override
    protected void setUp() throws Exception {
        super.setUp();

        mDevicePolicyManager = getContext().getSystemService(DevicePolicyManager.class);
        mAccountManager = getContext().getSystemService(AccountManager.class);
    }

    /**
     * Remove all test accounts.
     */
    public void testRemoveAllAccounts() throws Exception {
        for (Account account : mAccountManager.getAccountsByType(ACCOUNT_TYPE)) {
            Log.i(TAG, "Removing account: " + account);
            mAccountManager.removeAccountExplicitly(account);
        }
    }

    private void addAccount(String... features) throws Exception {
        final Bundle result = mAccountManager.addAccount(
                ACCOUNT_TYPE,
                null, // tokentype
                features,
                null, // options
                null, // activity
                null, // callback
                null // handler
        ).getResult();
        assertEquals(ACCOUNT_TYPE, result.getString(AccountManager.KEY_ACCOUNT_TYPE));
    }

    /**
     * Add an incompatible account, type A, no features.
     */
    public void testAddIncompatibleA() throws Exception {
        addAccount();
    }

    /**
     * Add an incompatible account, type B.  Disallow feature only.
     */
    public void testAddIncompatibleB() throws Exception {
        addAccount(ACCOUNT_FEATURE_DISALLOWED);
    }

    /**
     * Add an incompatible account, type C.  Has the disallow feature.
     */
    public void testAddIncompatibleC() throws Exception {
        addAccount(ACCOUNT_FEATURE_ALLOWED, ACCOUNT_FEATURE_DISALLOWED);
    }

    /**
     * Add a compatible account.
     */
    public void testAddCompatible() throws Exception {
        addAccount(ACCOUNT_FEATURE_ALLOWED);
    }

    /**
     * Remove the non-test-only (profile|device) owner.  Note this package and the test-only owner
     * have the same UID, so we can call clearXxX() from this package.
     */
    public void testCleanUpNonTestOwner() throws Exception {
        final ComponentName admin = new ComponentName(
                "com.android.cts.devicepolicy.accountcheck.nontestonly",
                "com.android.cts.devicepolicy.accountcheck.owner.AdminReceiver");

        if (mDevicePolicyManager.isDeviceOwnerApp(admin.getPackageName())) {
            Log.i(TAG, "testCleanUpNonTestOwner: Removing as DO");
            mDevicePolicyManager.clearDeviceOwnerApp(admin.getPackageName());
        }

        if (mDevicePolicyManager.isProfileOwnerApp(admin.getPackageName())) {
            Log.i(TAG, "testCleanUpNonTestOwner: Removing as PO");
            mDevicePolicyManager.clearProfileOwner(admin);
        }

        if (mDevicePolicyManager.isAdminActive(admin)) {
            Log.i(TAG, "testCleanUpNonTestOwner: Removing as DA");
            mDevicePolicyManager.removeActiveAdmin(admin);

            final long timeout = SystemClock.elapsedRealtime() + 60 * 1000;
            while (SystemClock.elapsedRealtime() < timeout
                    && mDevicePolicyManager.isAdminActive(admin)) {
                Thread.sleep(100);
            }
        }
        // Give the system a breath.
        Thread.sleep(5000);
    }

    /**
     * Test there are no preconfigured accounts that don't accept DO/PO.
     */
    public void testCheckPreconfiguredAccountFeatures() {
        final AccountManager am = AccountManager.get(mContext);
        final Account accounts[] = am.getAccounts();
        if (accounts.length == 0) {
            Log.v(TAG, "No preconfigured accounts found.");
            return; // pass.
        }
        final String[] feature_allow =
                {"android.account.DEVICE_OR_PROFILE_OWNER_ALLOWED"};
        final String[] feature_disallow =
                {"android.account.DEVICE_OR_PROFILE_OWNER_DISALLOWED"};

        // Even if we find incompatible accounts along the way, we still check all accounts
        // for logging.
        final StringBuilder error = new StringBuilder();
        for (Account account : accounts) {
            Log.v(TAG, "Checking " + account);
            if (hasAccountFeatures(am, account, feature_disallow)) {
                error.append(account + " has " + feature_disallow[0] + "\n");
            }
            if (!hasAccountFeatures(am, account, feature_allow)) {
                error.append(account + " doesn't have " + feature_allow[0] + "\n");
            }
        }
        if (error.length() > 0) {
            fail(error.toString());
        }
    }

    private boolean hasAccountFeatures(AccountManager am, Account account, String[] features) {
        try {
            return am.hasFeatures(account, features, null, null).getResult();
        } catch (Exception e) {
            Log.w(TAG, "Failed to get account feature", e);
            return false;
        }
    }
}
