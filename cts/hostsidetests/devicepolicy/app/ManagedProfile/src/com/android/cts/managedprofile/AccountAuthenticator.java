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

package com.android.cts.managedprofile;

import android.accounts.AbstractAccountAuthenticator;
import android.accounts.Account;
import android.accounts.AccountAuthenticatorResponse;
import android.accounts.AccountManager;
import android.accounts.NetworkErrorException;
import android.content.Context;
import android.os.Bundle;
import android.util.Log;

/* package */ class AccountAuthenticator extends AbstractAccountAuthenticator {
    private static final String TAG = "AccountAuthenticator";
    private static AccountAuthenticator sAuthenticator = null;
    private static final String KEY_ACCOUNT_SECRET = "key_secret";
    private static final String ACCOUNT_SECRET = "super_secret";
    public static final String ACCOUNT_NAME = "CTS";
    public static final String ACCOUNT_TYPE = "com.android.cts.test";
    public static final Account TEST_ACCOUNT = new Account(ACCOUNT_NAME, ACCOUNT_TYPE);
    private static final String AUTH_TOKEN = "authToken";
    private static final String AUTH_TOKEN_LABEL = "authTokenLabel";

    private final Context mContext;

    private AccountAuthenticator(Context context) {
        super(context);
        mContext = context;
    }

    private Bundle createAuthTokenBundle() {
        Bundle result = new Bundle();
        result.putString(AccountManager.KEY_ACCOUNT_NAME, ACCOUNT_NAME);
        result.putString(AccountManager.KEY_ACCOUNT_TYPE, ACCOUNT_TYPE);
        result.putString(AccountManager.KEY_AUTHTOKEN, AUTH_TOKEN);

        return result;
    }

    private Bundle createAccountSecretBundle() {
        Bundle result = createAuthTokenBundle();
        result.putBoolean(AccountManager.KEY_BOOLEAN_RESULT, true);
        result.putString(KEY_ACCOUNT_SECRET, ACCOUNT_SECRET);

        return result;
    }

    private Bundle createResultBundle(boolean value) {
        Bundle result = new Bundle();
        result.putBoolean(AccountManager.KEY_BOOLEAN_RESULT, value);

        return result;
    }

    @Override
    public Bundle addAccount(AccountAuthenticatorResponse response, String accountType,
            String authTokenType, String[] requiredFeatures, Bundle options)
            throws NetworkErrorException {
        return createAuthTokenBundle();
    }

    @Override
    public Bundle editProperties(AccountAuthenticatorResponse response, String accountType) {
        return createAuthTokenBundle();
    }

    @Override
    public Bundle updateCredentials(AccountAuthenticatorResponse response, Account account,
            String authTokenType, Bundle options) throws NetworkErrorException {
        return createAuthTokenBundle();
    }

    @Override
    public Bundle confirmCredentials(AccountAuthenticatorResponse response, Account account,
            Bundle options) throws NetworkErrorException {

        Bundle result = new Bundle();
        result.putBoolean(AccountManager.KEY_BOOLEAN_RESULT, true);
        return result;
    }

    @Override
    public Bundle getAuthToken(AccountAuthenticatorResponse response, Account account,
            String authTokenType, Bundle options) throws NetworkErrorException {
        return createAuthTokenBundle();
    }

    @Override
    public String getAuthTokenLabel(String authTokenType) {
        return AUTH_TOKEN_LABEL;
    }

    @Override
    public Bundle hasFeatures(AccountAuthenticatorResponse response, Account account,
            String[] features) throws NetworkErrorException {

        Bundle result = new Bundle();
        result.putBoolean(AccountManager.KEY_BOOLEAN_RESULT, true);
        return result;
    }

    @Override
    public Bundle getAccountCredentialsForCloning(AccountAuthenticatorResponse response, Account account) {
        if (TEST_ACCOUNT.equals(account)) {
            return createAccountSecretBundle();
        } else {
            Log.e(TAG, "failed in getAccountCredentialsForCloning. account: " + account);
            return createResultBundle(false);
        }
    }

    @Override
    public Bundle addAccountFromCredentials(AccountAuthenticatorResponse response, Account account,
            Bundle accountCredentials) {
        if (accountCredentials != null && TEST_ACCOUNT.equals(account)
                && ACCOUNT_SECRET.equals(accountCredentials.getString(KEY_ACCOUNT_SECRET))) {
            AccountManager.get(mContext).addAccountExplicitly(TEST_ACCOUNT, null, null);
            return createResultBundle(true);
        } else {
            Log.e(TAG, "failed in addAccountFromCredentials. Bundle values: " + accountCredentials
                    + " account: " + account);
            return createResultBundle(false);
        }
    }

    public static synchronized AccountAuthenticator getAuthenticator(Context context) {
        if (sAuthenticator == null) {
            sAuthenticator = new AccountAuthenticator(context.getApplicationContext());
        }
        return sAuthenticator;
    }
}
