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

package android.accounts.cts.common;

import android.accounts.AbstractAccountAuthenticator;
import android.accounts.Account;
import android.accounts.AccountAuthenticatorResponse;
import android.accounts.AccountManager;
import android.accounts.NetworkErrorException;
import android.accounts.cts.common.tx.AddAccountTx;
import android.accounts.cts.common.tx.ConfirmCredentialsTx;
import android.accounts.cts.common.tx.GetAuthTokenLabelTx;
import android.accounts.cts.common.tx.GetAuthTokenTx;
import android.accounts.cts.common.tx.HasFeaturesTx;
import android.accounts.cts.common.tx.StartAddAccountSessionTx;
import android.accounts.cts.common.tx.StartUpdateCredentialsSessionTx;
import android.accounts.cts.common.tx.UpdateCredentialsTx;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;

import java.util.Arrays;
import java.util.concurrent.atomic.AtomicInteger;

public class TestAccountAuthenticator extends AbstractAccountAuthenticator {

    private final String mAccountType;
    private final Context mContext;
    private volatile int mCounter = 0;
    private final AtomicInteger mTokenCounter  = new AtomicInteger(0);

    public TestAccountAuthenticator(Context context, String accountType) {
        super(context);
        mContext = context;
        mAccountType = accountType;
    }

    @Override
    public Bundle editProperties(AccountAuthenticatorResponse response, String accountType) {
        throw new UnsupportedOperationException(
                "editProperties should be tested using the MockAuthenticator");
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
        boolean isCallbackRequired = false;
        if (options != null) {
            accountName = options.getString(Fixtures.KEY_ACCOUNT_NAME);
            isCallbackRequired = options.getBoolean(Fixtures.KEY_CALLBACK_REQUIRED, false);
        }
        Bundle result = new Bundle();
        AuthenticatorContentProvider.setTx(
                new AddAccountTx(accountType, authTokenType, requiredFeatures, options, result));
        if (accountName.startsWith(Fixtures.PREFIX_NAME_SUCCESS)) {
            // fill bundle with a success result.
            result.putString(AccountManager.KEY_ACCOUNT_NAME, accountName);
            result.putString(AccountManager.KEY_ACCOUNT_TYPE, mAccountType);
        } else if (accountName.startsWith(Fixtures.PREFIX_NAME_INTERVENE)) {
            // Specify data to be returned by the eventual activity.
            Intent eventualActivityResultData = new Intent();
            eventualActivityResultData.putExtra(AccountManager.KEY_ACCOUNT_NAME, accountName);
            eventualActivityResultData.putExtra(AccountManager.KEY_ACCOUNT_TYPE, accountType);
            // Fill result with Intent.
            Intent intent = new Intent(mContext, TestAuthenticatorActivity.class);
            intent.putExtra(Fixtures.KEY_RESULT, eventualActivityResultData);
            intent.putExtra(Fixtures.KEY_CALLBACK, response);

            result.putParcelable(AccountManager.KEY_INTENT, intent);
        } else {
            // fill with error
            fillDefaultError(result, options);
        }

        try {
            return (isCallbackRequired) ? null : result;
        } finally {
            if (isCallbackRequired) {
                response.onResult(result);
            }
        }
    }

    @Override
    public Bundle confirmCredentials(
            AccountAuthenticatorResponse response,
            Account account,
            Bundle options) throws NetworkErrorException {
        if (!mAccountType.equals(account.type)) {
            throw new IllegalArgumentException("Request to the wrong authenticator!");
        }
        Bundle result = new Bundle();
        AuthenticatorContentProvider.setTx(
                new ConfirmCredentialsTx(account, options, result));

        boolean isCallbackRequired =
                options != null && options.getBoolean(Fixtures.KEY_CALLBACK_REQUIRED);
        if (account.name.startsWith(Fixtures.PREFIX_NAME_SUCCESS)) {
            // fill bundle with a success result.
            result.putBoolean(AccountManager.KEY_BOOLEAN_RESULT, true);
            result.putString(AccountManager.KEY_ACCOUNT_NAME, account.name);
            result.putString(AccountManager.KEY_ACCOUNT_TYPE, account.type);
        } else if (account.name.startsWith(Fixtures.PREFIX_NAME_INTERVENE)) {
            // Specify data to be returned by the eventual activity.
            Intent eventualActivityResultData = new Intent();
            eventualActivityResultData.putExtra(AccountManager.KEY_BOOLEAN_RESULT, true);
            eventualActivityResultData.putExtra(AccountManager.KEY_ACCOUNT_NAME, account.name);
            eventualActivityResultData.putExtra(AccountManager.KEY_ACCOUNT_TYPE, account.type);

            // Fill result with Intent.
            Intent intent = new Intent(mContext, TestAuthenticatorActivity.class);
            intent.putExtra(Fixtures.KEY_RESULT, eventualActivityResultData);
            intent.putExtra(Fixtures.KEY_CALLBACK, response);

            result.putParcelable(AccountManager.KEY_INTENT, intent);
        } else {
            // fill with error
            fillDefaultError(result, options);
        }

        try {
            return (isCallbackRequired) ? null : result;
        } finally {
            if (isCallbackRequired) {
                response.onResult(result);
            }
        }
    }

    @Override
    public Bundle getAuthToken(
            AccountAuthenticatorResponse response,
            Account account,
            String authTokenType,
            Bundle options) throws NetworkErrorException {
        if (!mAccountType.equals(account.type)) {
            throw new IllegalArgumentException("Request to the wrong authenticator!");
        }
        Bundle result = new Bundle();
        AuthenticatorContentProvider.setTx(
                new GetAuthTokenTx(account, authTokenType, options, result));
        boolean isCallbackRequired =
                options != null && options.getBoolean(Fixtures.KEY_CALLBACK_REQUIRED);
        long expiryMillis = (options == null) ? 0 : options.getLong(Fixtures.KEY_TOKEN_EXPIRY);
        if (account.name.startsWith(Fixtures.PREFIX_NAME_SUCCESS)) {
            // fill bundle with a success result.
            result.putString(
                    AccountManager.KEY_AUTHTOKEN, Fixtures.PREFIX_TOKEN + mCounter++);
            result.putLong(
                    AbstractAccountAuthenticator.KEY_CUSTOM_TOKEN_EXPIRY,
                    expiryMillis);
            result.putString(AccountManager.KEY_ACCOUNT_NAME, account.name);
            result.putString(AccountManager.KEY_ACCOUNT_TYPE, account.type);
        } else if (account.name.startsWith(Fixtures.PREFIX_NAME_INTERVENE)) {
            // Specify data to be returned by the eventual activity.
            Intent eventualActivityResultData = new Intent();
            eventualActivityResultData.putExtra(
                    AccountManager.KEY_AUTHTOKEN, Fixtures.PREFIX_TOKEN + mCounter++);
            eventualActivityResultData.putExtra(
                    AbstractAccountAuthenticator.KEY_CUSTOM_TOKEN_EXPIRY,
                    expiryMillis);
            eventualActivityResultData.putExtra(AccountManager.KEY_ACCOUNT_NAME, account.name);
            eventualActivityResultData.putExtra(AccountManager.KEY_ACCOUNT_TYPE, account.type);

            // Fill result with Intent.
            Intent intent = new Intent(mContext, TestAuthenticatorActivity.class);
            intent.putExtra(Fixtures.KEY_RESULT, eventualActivityResultData);
            intent.putExtra(Fixtures.KEY_CALLBACK, response);

            result.putParcelable(AccountManager.KEY_INTENT, intent);

        } else {
            // fill with error
            fillDefaultError(result, options);
        }

        try {
            return (isCallbackRequired) ? null : result;
        } finally {
            if (isCallbackRequired) {
                response.onResult(result);
            }
        }
    }

    @Override
    public String getAuthTokenLabel(String authTokenType) {
        String result = "Label:" + authTokenType;
        AuthenticatorContentProvider.setTx(
                new GetAuthTokenLabelTx(authTokenType, result));
        return result;
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
        AuthenticatorContentProvider.setTx(
                new UpdateCredentialsTx(account, authTokenType, options, result));

        boolean isCallbackRequired =
                options != null && options.getBoolean(Fixtures.KEY_CALLBACK_REQUIRED);
        if (account.name.startsWith(Fixtures.PREFIX_NAME_SUCCESS)) {
            // fill bundle with a success result.
            result.putString(AccountManager.KEY_ACCOUNT_NAME, account.name);
            result.putString(AccountManager.KEY_ACCOUNT_TYPE, account.type);
        } else if (account.name.startsWith(Fixtures.PREFIX_NAME_INTERVENE)) {
            // Specify data to be returned by the eventual activity.
            Intent eventualActivityResultData = new Intent();
            eventualActivityResultData.putExtra(AccountManager.KEY_ACCOUNT_NAME, account.name);
            eventualActivityResultData.putExtra(AccountManager.KEY_ACCOUNT_TYPE, account.type);

            // Fill result with Intent.
            Intent intent = new Intent(mContext, TestAuthenticatorActivity.class);
            intent.putExtra(Fixtures.KEY_RESULT, eventualActivityResultData);
            intent.putExtra(Fixtures.KEY_CALLBACK, response);

            result.putParcelable(AccountManager.KEY_INTENT, intent);
        } else {
            // fill with error
            fillDefaultError(result, options);
        }

        try {
            return (isCallbackRequired) ? null : result;
        } finally {
            if (isCallbackRequired) {
                response.onResult(result);
            }
        }
    }

    @Override
    public Bundle hasFeatures(
            AccountAuthenticatorResponse response,
            Account account,
            String[] features) throws NetworkErrorException {
        if (!mAccountType.equals(account.type)) {
            throw new IllegalArgumentException("Request to the wrong authenticator!");
        }
        Bundle result = new Bundle();
        AuthenticatorContentProvider.setTx(
                new HasFeaturesTx(account, features, result));
        boolean isCallbackRequired =
                Arrays.asList(features).contains(Fixtures.KEY_CALLBACK_REQUIRED);
        if (account.name.startsWith(Fixtures.PREFIX_NAME_SUCCESS)) {
            // fill bundle with a success result.
            result.putBoolean(AccountManager.KEY_BOOLEAN_RESULT, true);
        } else if (account.name.startsWith(Fixtures.PREFIX_NAME_INTERVENE)) {
            // Specify data to be returned by the eventual activity.
            Intent eventualActivityResultData = new Intent();
            eventualActivityResultData.putExtra(AccountManager.KEY_BOOLEAN_RESULT, true);

            Intent intent = new Intent(mContext, TestAuthenticatorActivity.class);
            intent.putExtra(Fixtures.KEY_RESULT, eventualActivityResultData);
            intent.putExtra(Fixtures.KEY_CALLBACK, response);

            result.putParcelable(AccountManager.KEY_INTENT, intent);
        } else {
            // fill with error
        }

        try {
            return (isCallbackRequired) ? null : result;
        } finally {
            if (isCallbackRequired) {
                response.onResult(result);
            }
        }
    }

    /**
     * Start add account flow of the specified accountType to authenticate user.
     * This implementation works with AccountManagerUnaffiliatedAuthenticatorTests
     * to test that portion of the default implementation of the
     * {@link AccountManager#finishSession} API when implementers of
     * {@link android.accounts.AbstractAccountAuthenticator} override only
     * {@link AccountManager#startAddAccountSession} but not
     * {@link AccountManager#finishSession}.
     */
    @Override
    public Bundle startAddAccountSession(
            AccountAuthenticatorResponse response,
            String accountType,
            String authTokenType,
            String[] requiredFeatures,
            Bundle options) throws NetworkErrorException {
        if (!mAccountType.equals(accountType)) {
            throw new IllegalArgumentException("Request to the wrong authenticator!");
        }

        AuthenticatorContentProvider.setTx(new StartAddAccountSessionTx(
                accountType, authTokenType, requiredFeatures, options));

        String accountName = null;
        Bundle sessionBundle = null;
        if (options != null) {
            accountName = options.getString(Fixtures.KEY_ACCOUNT_NAME);
            sessionBundle = options.getBundle(Fixtures.KEY_ACCOUNT_SESSION_BUNDLE);
        }

        Bundle result = new Bundle();
        if (accountName.startsWith(Fixtures.PREFIX_NAME_SUCCESS)) {
            // fill bundle with a success result.
            result.putBundle(AccountManager.KEY_ACCOUNT_SESSION_BUNDLE, sessionBundle);
            result.putString(AccountManager.KEY_ACCOUNT_STATUS_TOKEN,
                    Fixtures.ACCOUNT_STATUS_TOKEN_UNAFFILIATED);
            result.putString(AccountManager.KEY_PASSWORD, "doesn't matter");
            result.putString(AccountManager.KEY_AUTHTOKEN,
                    Integer.toString(mTokenCounter.incrementAndGet()));
        } else {
            // fill with error
            fillDefaultError(result, options);
        }

        return result;
    }

    /**
     * Start update credentials flow to re-auth user without updating locally stored
     * credentials for an account.
     * This implementation works with AccountManagerUnaffiliatedAuthenticatorTests
     * to test that portion of the default implementation of the
     * {@link AccountManager#finishSession} API when implementers of
     * {@link android.accounts.AbstractAccountAuthenticator} override only
     * {@link AccountManager#startUpdateCredentialsSession} but not
     * {@link AccountManager#finishSession}.
     */
    @Override
    public Bundle startUpdateCredentialsSession(
            AccountAuthenticatorResponse response,
            Account account,
            String authTokenType,
            Bundle options)
            throws NetworkErrorException {

        if (!mAccountType.equals(account.type)) {
            throw new IllegalArgumentException("Request to the wrong authenticator!");
        }

        AuthenticatorContentProvider.setTx(new StartUpdateCredentialsSessionTx(
                account, authTokenType, options));

        String accountName = null;
        Bundle sessionBundle = null;
        if (options != null) {
            accountName = options.getString(Fixtures.KEY_ACCOUNT_NAME);
            sessionBundle = options.getBundle(Fixtures.KEY_ACCOUNT_SESSION_BUNDLE);
        }

        Bundle result = new Bundle();
        if (accountName.startsWith(Fixtures.PREFIX_NAME_SUCCESS)) {
            // fill bundle with a success result.
            result.putBundle(AccountManager.KEY_ACCOUNT_SESSION_BUNDLE, sessionBundle);
            result.putString(AccountManager.KEY_ACCOUNT_STATUS_TOKEN,
                    Fixtures.ACCOUNT_STATUS_TOKEN_UNAFFILIATED);
            result.putString(AccountManager.KEY_PASSWORD, "doesn't matter");
            result.putString(AccountManager.KEY_AUTHTOKEN,
                    Integer.toString(mTokenCounter.incrementAndGet()));
        } else {
            // fill with error
            fillDefaultError(result, options);
        }
        return result;
    }

    private void fillDefaultError(Bundle result, Bundle options) {
        int errorCode = AccountManager.ERROR_CODE_INVALID_RESPONSE;
        String errorMsg = "Default Error Message";
        if (options != null) {
            errorCode = options.getInt(AccountManager.KEY_ERROR_CODE);
            errorMsg = options.getString(AccountManager.KEY_ERROR_MESSAGE);
        }
        result.putInt(AccountManager.KEY_ERROR_CODE, errorCode);
        result.putString(AccountManager.KEY_ERROR_MESSAGE, errorMsg);
    }
}

