/*
 * Copyright (C) 2009 The Android Open Source Project
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

package android.accounts.cts;

import android.accounts.AbstractAccountAuthenticator;
import android.accounts.Account;
import android.accounts.AccountAuthenticatorResponse;
import android.accounts.AccountManager;
import android.accounts.NetworkErrorException;
import android.accounts.cts.common.Fixtures;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.util.Log;

import java.util.ArrayList;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * A simple Mock Account Authenticator
 */
public class MockAccountAuthenticator extends AbstractAccountAuthenticator {
    private static String TAG = "AccountManagerTest";

    public static String KEY_ACCOUNT_INFO = "key_account_info";
    public static String KEY_ACCOUNT_AUTHENTICATOR_RESPONSE = "key_account_authenticator_response";
    public static String ACCOUNT_NAME_FOR_NEW_REMOVE_API = "call new removeAccount api";
    public static String ACCOUNT_NAME_FOR_DEFAULT_IMPL = "call super api";
    // Key for triggering return intent flow
    public static String KEY_RETURN_INTENT = "return an intent";
    public static String ACCOUNT_NAME_FOR_NEW_REMOVE_API1 = "call new removeAccount api";

    private final Context mContext;
    private final AtomicInteger mTokenCounter  = new AtomicInteger(0);
    private final AtomicBoolean mIsRecentlyCalled = new AtomicBoolean(false);

    AccountAuthenticatorResponse mResponse;
    String mAccountType;
    String mAuthTokenType;
    String[] mRequiredFeatures;
    public Bundle mOptionsUpdateCredentials;
    public Bundle mOptionsConfirmCredentials;
    public Bundle mOptionsAddAccount;
    public Bundle mOptionsGetAuthToken;
    public Bundle mOptionsStartAddAccountSession;
    public Bundle mOptionsStartUpdateCredentialsSession;
    public Bundle mOptionsFinishSession;
    Account mAccount;
    String[] mFeatures;
    String mStatusToken;

    final ArrayList<String> mockFeatureList = new ArrayList<String>();
    private final long mTokenDurationMillis = 1000; // 1 second

    public MockAccountAuthenticator(Context context) {
        super(context);
        mContext = context;

        // Create some mock features
        mockFeatureList.add(AccountManagerTest.FEATURE_1);
        mockFeatureList.add(AccountManagerTest.FEATURE_2);
    }

    public long getTokenDurationMillis() {
        return mTokenDurationMillis;
    }

    public boolean isRecentlyCalled() {
        return mIsRecentlyCalled.getAndSet(false);
    }

    public String getLastTokenServed() {
        return Integer.toString(mTokenCounter.get());
    }

    public AccountAuthenticatorResponse getResponse() {
        return mResponse;
    }

    public String getAccountType() {
        return mAccountType;
    }

    public String getAuthTokenType() {
        return mAuthTokenType;
    }

    public String[] getRequiredFeatures() {
        return mRequiredFeatures;
    }

    public Account getAccount() {
        return mAccount;
    }

    public String[] getFeatures() {
        return mFeatures;
    }

    public String getStatusToken() {
        return mStatusToken;
    }

    public void clearData() {
        mResponse = null;
        mAccountType = null;
        mAuthTokenType = null;
        mRequiredFeatures = null;
        mOptionsUpdateCredentials = null;
        mOptionsAddAccount = null;
        mOptionsGetAuthToken = null;
        mOptionsConfirmCredentials = null;
        mOptionsStartAddAccountSession = null;
        mOptionsStartUpdateCredentialsSession = null;
        mOptionsFinishSession = null;
        mAccount = null;
        mFeatures = null;
        mStatusToken = null;
    }

    public void callAccountAuthenticated() {
        AccountManager am = AccountManager.get(mContext);
        am.notifyAccountAuthenticated(mAccount);
    }

    public void callSetPassword() {
        AccountManager am = AccountManager.get(mContext);
        am.setPassword(mAccount, "password");
    }

    private Bundle createResultBundle() {
        Bundle result = new Bundle();
        result.putString(AccountManager.KEY_ACCOUNT_NAME, AccountManagerTest.ACCOUNT_NAME);
        result.putString(AccountManager.KEY_ACCOUNT_TYPE, AccountManagerTest.ACCOUNT_TYPE);
        result.putString(
                AccountManager.KEY_AUTHTOKEN,
                Integer.toString(mTokenCounter.incrementAndGet()));
        return result;
    }

    /**
     * Adds an account of the specified accountType.
     */
    @Override
    public Bundle addAccount(AccountAuthenticatorResponse response, String accountType,
            String authTokenType, String[] requiredFeatures, Bundle options)
            throws NetworkErrorException {
        this.mResponse = response;
        this.mAccountType = accountType;
        this.mAuthTokenType = authTokenType;
        this.mRequiredFeatures = requiredFeatures;
        this.mOptionsAddAccount = options;
        AccountManager am = AccountManager.get(mContext);
        am.addAccountExplicitly(AccountManagerTest.ACCOUNT, "fakePassword", null);
        return createResultBundle();
    }

    /**
     * Update the locally stored credentials for an account.
     */
    @Override
    public Bundle updateCredentials(AccountAuthenticatorResponse response, Account account,
            String authTokenType, Bundle options) throws NetworkErrorException {
        this.mResponse = response;
        this.mAccount = account;
        this.mAuthTokenType = authTokenType;
        this.mOptionsUpdateCredentials = options;
        return createResultBundle();
    }

    /**
     * Returns a Bundle that contains the Intent of the activity that can be used to edit the
     * properties. In order to indicate success the activity should call response.setResult()
     * with a non-null Bundle.
     */
    @Override
    public Bundle editProperties(AccountAuthenticatorResponse response, String accountType) {
        this.mResponse = response;
        this.mAccountType = accountType;
        return createResultBundle();
    }

    /**
     * Checks that the user knows the credentials of an account.
     */
    @Override
    public Bundle confirmCredentials(AccountAuthenticatorResponse response, Account account,
            Bundle options) throws NetworkErrorException {
        this.mResponse = response;
        this.mAccount = account;
        this.mOptionsConfirmCredentials = options;
        Bundle result = new Bundle();
        if (options.containsKey(KEY_RETURN_INTENT)) {
            Intent intent = new Intent();
            intent.setClassName("android.accounts.cts", "android.accounts.cts.AccountDummyActivity");
            result.putParcelable(AccountManager.KEY_INTENT, intent);
        } else {
            result.putBoolean(AccountManager.KEY_BOOLEAN_RESULT, true);
        }

        return result;
    }

    /**
     * Gets the authtoken for an account.
     */
    @Override
    public Bundle getAuthToken(
            AccountAuthenticatorResponse response,
            Account account,
            String authTokenType,
            Bundle options) throws NetworkErrorException {
        Log.w(TAG, "MockAuth - getAuthToken@" + System.currentTimeMillis());
        mIsRecentlyCalled.set(true);
        this.mResponse = response;
        this.mAccount = account;
        this.mAuthTokenType = authTokenType;
        this.mOptionsGetAuthToken = options;
        Bundle result = new Bundle();

        result.putString(AccountManager.KEY_ACCOUNT_NAME, account.name);
        result.putString(AccountManager.KEY_ACCOUNT_TYPE, account.type);
        String token;
        if (AccountManagerTest.AUTH_EXPIRING_TOKEN_TYPE.equals(authTokenType)) {
            /*
             * The resultant token should simply be the expiration timestamp. E.g. the time after
             * which getting a new AUTH_EXPIRING_TOKEN_TYPE typed token will return a different
             * value.
             */
            long expiry = System.currentTimeMillis() + mTokenDurationMillis;
            result.putLong(AbstractAccountAuthenticator.KEY_CUSTOM_TOKEN_EXPIRY, expiry);
        }
        result.putString(
                AccountManager.KEY_AUTHTOKEN,
                Integer.toString(mTokenCounter.incrementAndGet()));
        return result;
    }

    /**
     * Ask the authenticator for a localized label for the given authTokenType.
     */
    @Override
    public String getAuthTokenLabel(String authTokenType) {
        this.mAuthTokenType = authTokenType;
        return AccountManagerTest.AUTH_TOKEN_LABEL;
    }

    /**
     * Checks if the account supports all the specified authenticator specific features.
     */
    @Override
    public Bundle hasFeatures(AccountAuthenticatorResponse response, Account account,
            String[] features) throws NetworkErrorException {

        this.mResponse = response;
        this.mAccount = account;
        this.mFeatures = features;

        Bundle result = new Bundle();
        if (null == features) {
            result.putBoolean(AccountManager.KEY_BOOLEAN_RESULT, true);
        }
        else {
            boolean booleanResult = true;
            for (String feature: features) {
                if (!mockFeatureList.contains(feature)) {
                    booleanResult = false;
                    break;
                }
            }
            result.putBoolean(AccountManager.KEY_BOOLEAN_RESULT, booleanResult);
        }
        return result;
    }

    @Override
    public Bundle getAccountRemovalAllowed(AccountAuthenticatorResponse response,
            Account account) throws NetworkErrorException {
        final Bundle result = new Bundle();
        if (ACCOUNT_NAME_FOR_NEW_REMOVE_API.equals(account.name)) {
            Intent intent = AccountRemovalDummyActivity.createIntent(mContext);
            // Pass in the authenticator response, so that account removal can
            // be
            // completed
            intent.putExtra(KEY_ACCOUNT_AUTHENTICATOR_RESPONSE, response);
            intent.putExtra(KEY_ACCOUNT_INFO, account);
            result.putParcelable(AccountManager.KEY_INTENT, intent);
            // Adding this following line to reject account installation
            // requests
            // coming from old removeAccount API.
            result.putBoolean(AccountManager.KEY_BOOLEAN_RESULT, false);
        } else if (ACCOUNT_NAME_FOR_DEFAULT_IMPL.equals(account.name)) {
            return super.getAccountRemovalAllowed(response, account);
        } else {
            result.putBoolean(AccountManager.KEY_BOOLEAN_RESULT, true);
        }
        return result;
    }

    @Override
    public Bundle addAccountFromCredentials(final AccountAuthenticatorResponse response,
            Account account,
            Bundle accountCredentials) throws NetworkErrorException {
        return super.addAccountFromCredentials(response, account, accountCredentials);
    }

    @Override
    public Bundle getAccountCredentialsForCloning(final AccountAuthenticatorResponse response,
            final Account account) throws NetworkErrorException {
        return super.getAccountCredentialsForCloning(response, account);
    }


    /**
     * Start add account flow of the specified accountType to authenticate user.
     */
    @Override
    public Bundle startAddAccountSession(AccountAuthenticatorResponse response,
            String accountType,
            String authTokenType,
            String[] requiredFeatures,
            Bundle options) throws NetworkErrorException {
        this.mResponse = response;
        this.mAccountType = accountType;
        this.mAuthTokenType = authTokenType;
        this.mRequiredFeatures = requiredFeatures;
        this.mOptionsStartAddAccountSession = options;

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
                    AccountManagerTest.ACCOUNT_STATUS_TOKEN);
            result.putString(AccountManager.KEY_PASSWORD, AccountManagerTest.ACCOUNT_PASSWORD);
            result.putString(AccountManager.KEY_AUTHTOKEN,
                    Integer.toString(mTokenCounter.incrementAndGet()));
        } else if (accountName.startsWith(Fixtures.PREFIX_NAME_INTERVENE)) {
            // Specify data to be returned by the eventual activity.
            Intent eventualActivityResultData = new Intent();
            eventualActivityResultData.putExtra(AccountManager.KEY_AUTHTOKEN,
                    Integer.toString(mTokenCounter.incrementAndGet()));
            eventualActivityResultData.putExtra(AccountManager.KEY_ACCOUNT_STATUS_TOKEN,
                    AccountManagerTest.ACCOUNT_STATUS_TOKEN);
            eventualActivityResultData.putExtra(AccountManager.KEY_PASSWORD,
                    AccountManagerTest.ACCOUNT_PASSWORD);
            eventualActivityResultData.putExtra(AccountManager.KEY_ACCOUNT_SESSION_BUNDLE,
                    sessionBundle);
            // Fill result with Intent.
            Intent intent = new Intent(mContext, AccountAuthenticatorDummyActivity.class);
            intent.putExtra(Fixtures.KEY_RESULT, eventualActivityResultData);
            intent.putExtra(Fixtures.KEY_CALLBACK, response);

            result.putParcelable(AccountManager.KEY_INTENT, intent);
        } else {
            // fill with error
            fillResultWithError(result, options);
        }
        return result;
    }

    /**
     * Start update credentials flow to re-auth user without updating locally stored credentials
     * for an account.
     */
    @Override
    public Bundle startUpdateCredentialsSession(AccountAuthenticatorResponse response,
            Account account,
            String authTokenType,
            Bundle options) throws NetworkErrorException {
        mResponse = response;
        mAccount = account;
        mAuthTokenType = authTokenType;
        mOptionsStartUpdateCredentialsSession = options;

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
                    AccountManagerTest.ACCOUNT_STATUS_TOKEN);
            result.putString(AccountManager.KEY_PASSWORD, AccountManagerTest.ACCOUNT_PASSWORD);
            result.putString(AccountManager.KEY_AUTHTOKEN,
                    Integer.toString(mTokenCounter.incrementAndGet()));
        } else if (accountName.startsWith(Fixtures.PREFIX_NAME_INTERVENE)) {
            // Specify data to be returned by the eventual activity.
            Intent eventualActivityResultData = new Intent();
            eventualActivityResultData.putExtra(AccountManager.KEY_AUTHTOKEN,
                    Integer.toString(mTokenCounter.incrementAndGet()));
            eventualActivityResultData.putExtra(AccountManager.KEY_ACCOUNT_STATUS_TOKEN,
                    AccountManagerTest.ACCOUNT_STATUS_TOKEN);
            eventualActivityResultData.putExtra(AccountManager.KEY_PASSWORD,
                    AccountManagerTest.ACCOUNT_PASSWORD);
            eventualActivityResultData.putExtra(AccountManager.KEY_ACCOUNT_SESSION_BUNDLE,
                    sessionBundle);
            // Fill result with Intent.
            Intent intent = new Intent(mContext, AccountAuthenticatorDummyActivity.class);
            intent.putExtra(Fixtures.KEY_RESULT, eventualActivityResultData);
            intent.putExtra(Fixtures.KEY_CALLBACK, response);

            result.putParcelable(AccountManager.KEY_INTENT, intent);
        } else {
            // fill with error
            fillResultWithError(result, options);
        }
        return result;
    }

    /**
     * Finishes account session started by adding the account to device or updating the local
     * credentials.
     */
    @Override
    public Bundle finishSession(AccountAuthenticatorResponse response,
            String accountType,
            Bundle sessionBundle) throws NetworkErrorException {
        this.mResponse = response;
        this.mAccountType = accountType;
        this.mOptionsFinishSession = sessionBundle;

        String accountName = null;
        if (sessionBundle != null) {
            accountName = sessionBundle.getString(Fixtures.KEY_ACCOUNT_NAME);
        }

        Bundle result = new Bundle();
        if (accountName.startsWith(Fixtures.PREFIX_NAME_SUCCESS)) {
            // fill bundle with a success result.
            result.putString(AccountManager.KEY_ACCOUNT_NAME, AccountManagerTest.ACCOUNT_NAME);
            result.putString(AccountManager.KEY_ACCOUNT_TYPE, AccountManagerTest.ACCOUNT_TYPE);
            result.putString(AccountManager.KEY_AUTHTOKEN,
                    Integer.toString(mTokenCounter.incrementAndGet()));
        } else if (accountName.startsWith(Fixtures.PREFIX_NAME_INTERVENE)) {
            // Specify data to be returned by the eventual activity.
            Intent eventualActivityResultData = new Intent();
            eventualActivityResultData.putExtra(AccountManager.KEY_ACCOUNT_NAME,
                    AccountManagerTest.ACCOUNT_NAME);
            eventualActivityResultData.putExtra(AccountManager.KEY_ACCOUNT_TYPE,
                    AccountManagerTest.ACCOUNT_TYPE);
            eventualActivityResultData.putExtra(AccountManager.KEY_AUTHTOKEN,
                    Integer.toString(mTokenCounter.incrementAndGet()));

            // Fill result with Intent.
            Intent intent = new Intent(mContext, AccountAuthenticatorDummyActivity.class);
            intent.putExtra(Fixtures.KEY_RESULT, eventualActivityResultData);
            intent.putExtra(Fixtures.KEY_CALLBACK, response);

            result.putParcelable(AccountManager.KEY_INTENT, intent);
        } else {
            // fill with error
            fillResultWithError(result, sessionBundle);
        }
        return result;
    }

    private void fillResultWithError(Bundle result, Bundle options) {
        int errorCode = AccountManager.ERROR_CODE_INVALID_RESPONSE;
        String errorMsg = "Default Error Message";
        if (options != null) {
            errorCode = options.getInt(AccountManager.KEY_ERROR_CODE);
            errorMsg = options.getString(AccountManager.KEY_ERROR_MESSAGE);
        }
        result.putInt(AccountManager.KEY_ERROR_CODE, errorCode);
        result.putString(AccountManager.KEY_ERROR_MESSAGE, errorMsg);
    }

    /**
     * Checks if the credentials of the account should be updated.
     */
    @Override
    public Bundle isCredentialsUpdateSuggested(
            final AccountAuthenticatorResponse response,
            Account account,
            String statusToken) throws NetworkErrorException {
        this.mResponse = response;
        this.mAccount = account;
        this.mStatusToken = statusToken;

        Bundle result = new Bundle();
        if (account.name.startsWith(Fixtures.PREFIX_NAME_SUCCESS)) {
            // fill bundle with a success result.
            result.putBoolean(AccountManager.KEY_BOOLEAN_RESULT, true);
        } else {
            // fill with error
            int errorCode = AccountManager.ERROR_CODE_INVALID_RESPONSE;
            String errorMsg = "Default Error Message";
            result.putInt(AccountManager.KEY_ERROR_CODE, errorCode);
            result.putString(AccountManager.KEY_ERROR_MESSAGE, errorMsg);
        }

        response.onResult(result);
        return null;
    }
}
