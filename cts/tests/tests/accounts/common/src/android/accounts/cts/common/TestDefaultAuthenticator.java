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

package android.accounts.cts.common;

import android.accounts.AbstractAccountAuthenticator;
import android.accounts.Account;
import android.accounts.AccountAuthenticatorResponse;
import android.accounts.AccountManager;
import android.accounts.NetworkErrorException;
import android.accounts.cts.common.tx.AddAccountTx;
import android.accounts.cts.common.tx.UpdateCredentialsTx;
import android.content.Context;
import android.os.Bundle;


/**
 * This authenticator is to test the default implementation of
 * AbstractAccountAuthenticator.
 */
public class TestDefaultAuthenticator extends AbstractAccountAuthenticator {
    private final String mAccountType;
    private final Context mContext;

    public TestDefaultAuthenticator(Context context, String accountType) {
        super(context);
        mAccountType = accountType;
        mContext = context;
    }

    @Override
    public Bundle editProperties(AccountAuthenticatorResponse response, String accountType) {
        throw new UnsupportedOperationException(
                "editProperties should not be tested using the TestDefaultAuthenticator");
    }

    @Override
    public Bundle addAccount(
            AccountAuthenticatorResponse response,
            String accountType,
            String authTokenType,
            String[] requiredFeatures,
            Bundle options) throws NetworkErrorException {
        if (!mAccountType.equals(accountType)) {
            throw new IllegalArgumentException("Request to the wrong authenticator!");
        }

        String accountName = null;
        if (options != null) {
            accountName = options.getString(Fixtures.KEY_ACCOUNT_NAME);
        } else {
            accountName = Fixtures.PREFIX_NAME_SUCCESS + "@"
                    + Fixtures.SUFFIX_NAME_FIXTURE;
        }

        Bundle result = new Bundle();
        result.putString(AccountManager.KEY_ACCOUNT_NAME, accountName);
        result.putString(AccountManager.KEY_ACCOUNT_TYPE, mAccountType);
        AuthenticatorContentProvider.setTx(
                new AddAccountTx(accountType, authTokenType, requiredFeatures, options, result));
        return result;
    }

    @Override
    public Bundle confirmCredentials(
            AccountAuthenticatorResponse response,
            Account account,
            Bundle options) throws NetworkErrorException {
        throw new UnsupportedOperationException(
                "confirmCredentials should not be tested using the TestDefaultAuthenticator");
    }

    @Override
    public Bundle getAuthToken(
            AccountAuthenticatorResponse response,
            Account account,
            String authTokenType,
            Bundle options) throws NetworkErrorException {
        throw new UnsupportedOperationException(
                "getAuthToken should not be tested using the TestDefaultAuthenticator");
    }

    @Override
    public String getAuthTokenLabel(String authTokenType) {
        throw new UnsupportedOperationException(
                "getAuthTokenLabel should not be tested using the TestDefaultAuthenticator");
    }

    @Override
    public Bundle updateCredentials(
            AccountAuthenticatorResponse response,
            Account account,
            String authTokenType,
            Bundle options) throws NetworkErrorException {
        if (!mAccountType.equals(account.type)) {
            throw new IllegalArgumentException("Request to the wrong authenticator!");
        }
        Bundle result = new Bundle();
        result.putString(AccountManager.KEY_ACCOUNT_NAME, account.name);
        result.putString(AccountManager.KEY_ACCOUNT_TYPE, account.type);
        AuthenticatorContentProvider.setTx(
                new UpdateCredentialsTx(account, authTokenType, options, result));
        return result;
    }

    @Override
    public Bundle hasFeatures(
            AccountAuthenticatorResponse response,
            Account account,
            String[] features) throws NetworkErrorException {
        throw new UnsupportedOperationException(
                "hasFeatures should not be tested using the TestDefaultAuthenticator");
    }
}

