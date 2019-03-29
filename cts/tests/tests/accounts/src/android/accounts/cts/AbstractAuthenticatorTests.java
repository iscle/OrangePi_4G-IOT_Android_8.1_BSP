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

package android.accounts.cts;

import android.accounts.Account;
import android.accounts.AccountManager;
import android.accounts.AccountManagerFuture;
import android.accounts.AuthenticatorException;
import android.accounts.OperationCanceledException;
import android.accounts.cts.common.AuthenticatorContentProvider;
import android.accounts.cts.common.Fixtures;
import android.accounts.cts.common.tx.AddAccountTx;
import android.accounts.cts.common.tx.UpdateCredentialsTx;
import android.content.ContentProviderClient;
import android.content.ContentResolver;
import android.os.Bundle;
import android.os.RemoteException;
import android.test.AndroidTestCase;

import java.io.IOException;

/**
 * Tests for AccountManager and AbstractAccountAuthenticator. This is to test
 * default implementation of account session api in
 * {@link android.accounts.AbstractAccountAuthenticator}.
 * <p>
 * You can run those unit tests with the following command line:
 * <p>
 *  adb shell am instrument
 *   -e debug false -w
 *   -e class android.accounts.cts.AbstractAuthenticatorTests
 * android.accounts.cts/android.support.test.runner.AndroidJUnitRunner
 */
public class AbstractAuthenticatorTests extends AndroidTestCase {

    private AccountManager mAccountManager;
    private ContentProviderClient mProviderClient;

    @Override
    public void setUp() throws Exception {
        // bind to the diagnostic service and set it up.
        mAccountManager = AccountManager.get(getContext());
        ContentResolver resolver = getContext().getContentResolver();
        mProviderClient = resolver.acquireContentProviderClient(
                AuthenticatorContentProvider.AUTHORITY);
    }

    public void tearDown() throws RemoteException {
        mProviderClient.release();
    }

    /**
     * Tests startAddAccountSession default implementation. An encrypted session
     * bundle should always be returned without password or status token.
     */
    public void testStartAddAccountSessionDefaultImpl()
            throws OperationCanceledException, AuthenticatorException, IOException {
        Bundle options = new Bundle();
        String accountName = Fixtures.PREFIX_NAME_SUCCESS + "@" + Fixtures.SUFFIX_NAME_FIXTURE;
        options.putString(Fixtures.KEY_ACCOUNT_NAME, accountName);

        AccountManagerFuture<Bundle> future = mAccountManager.startAddAccountSession(
                Fixtures.TYPE_DEFAULT,
                null /* authTokenType */,
                null /* requiredFeatures */,
                options,
                null /* activity */,
                null /* callback */,
                null /* handler */);

        Bundle result = future.getResult();

        // Validate that auth token was stripped from result.
        assertNull(result.get(AccountManager.KEY_AUTHTOKEN));

        // Validate that no password nor status token is returned in the result
        // for default implementation.
        validateNullPasswordAndStatusToken(result);

        Bundle sessionBundle = result.getBundle(AccountManager.KEY_ACCOUNT_SESSION_BUNDLE);
        // Validate session bundle is returned but data in the bundle is
        // encrypted and hence not visible.
        assertNotNull(sessionBundle);
        assertNull(sessionBundle.getString(AccountManager.KEY_ACCOUNT_TYPE));
    }


    /**
     * Tests startUpdateCredentialsSession default implementation. An encrypted session
     * bundle should always be returned without password or status token.
     */
    public void testStartUpdateCredentialsSessionDefaultImpl()
            throws OperationCanceledException, AuthenticatorException, IOException {
        Bundle options = new Bundle();
        String accountName = Fixtures.PREFIX_NAME_SUCCESS + "@" + Fixtures.SUFFIX_NAME_FIXTURE;
        options.putString(Fixtures.KEY_ACCOUNT_NAME, accountName);

        AccountManagerFuture<Bundle> future = mAccountManager.startUpdateCredentialsSession(
                Fixtures.ACCOUNT_DEFAULT,
                null /* authTokenType */,
                options,
                null /* activity */,
                null /* callback */,
                null /* handler */);

        Bundle result = future.getResult();
        assertTrue(future.isDone());
        assertNotNull(result);

        // Validate no auth token in result.
        assertNull(result.get(AccountManager.KEY_AUTHTOKEN));

        // Validate that no password nor status token is returned in the result
        // for default implementation.
        validateNullPasswordAndStatusToken(result);

        Bundle sessionBundle = result.getBundle(AccountManager.KEY_ACCOUNT_SESSION_BUNDLE);
        // Validate session bundle is returned but data in the bundle is
        // encrypted and hence not visible.
        assertNotNull(sessionBundle);
        assertNull(sessionBundle.getString(Fixtures.KEY_ACCOUNT_NAME));
    }

    /**
     * Tests finishSession default implementation with default startAddAccountSession.
     * Only account name and account type should be returned as a bundle.
     */
    public void testFinishSessionAndStartAddAccountSessionDefaultImpl()
            throws OperationCanceledException, AuthenticatorException, IOException,
            RemoteException {
        Bundle options = new Bundle();
        String accountName = Fixtures.PREFIX_NAME_SUCCESS + "@" + Fixtures.SUFFIX_NAME_FIXTURE;
        options.putString(Fixtures.KEY_ACCOUNT_NAME, accountName);

        // First obtain an encrypted session bundle from startAddAccountSession(...) default
        // implementation.
        AccountManagerFuture<Bundle> future = mAccountManager.startAddAccountSession(
                Fixtures.TYPE_DEFAULT,
                null /* authTokenType */,
                null /* requiredFeatures */,
                options,
                null /* activity */,
                null /* callback */,
                null /* handler */);

        Bundle result = future.getResult();
        assertTrue(future.isDone());
        assertNotNull(result);

        // Assert that result contains a non-null session bundle.
        Bundle escrowBundle = result.getBundle(AccountManager.KEY_ACCOUNT_SESSION_BUNDLE);
        assertNotNull(escrowBundle);

        // Now call finishSession(...) with the session bundle we just obtained.
        future = mAccountManager.finishSession(
                escrowBundle,
                null /* activity */,
                null /* callback */,
                null /* handler */);

        result = future.getResult();
        assertTrue(future.isDone());
        assertNotNull(result);

        // Validate that parameters are passed to addAccount(...) correctly in default finishSession
        // implementation.
        Bundle providerBundle = mProviderClient.call(
                AuthenticatorContentProvider.METHOD_GET,
                null /* arg */,
                null /* extras */);
        providerBundle.setClassLoader(AddAccountTx.class.getClassLoader());
        AddAccountTx addAccountTx = providerBundle
                .getParcelable(AuthenticatorContentProvider.KEY_TX);
        assertNotNull(addAccountTx);

        // Assert parameters has been passed to addAccount(...) correctly
        assertEquals(Fixtures.TYPE_DEFAULT, addAccountTx.accountType);
        assertNull(addAccountTx.authTokenType);

        validateSystemOptions(addAccountTx.options);
        // Validate options
        assertNotNull(addAccountTx.options);
        assertEquals(accountName, addAccountTx.options.getString(Fixtures.KEY_ACCOUNT_NAME));
        // Validate features.
        assertEquals(0, addAccountTx.requiredFeatures.size());

        // Assert returned result contains correct account name, account type and null auth token.
        assertEquals(accountName, result.get(AccountManager.KEY_ACCOUNT_NAME));
        assertEquals(Fixtures.TYPE_DEFAULT, result.get(AccountManager.KEY_ACCOUNT_TYPE));
        assertNull(result.get(AccountManager.KEY_AUTHTOKEN));
    }

    /**
     * Tests finishSession default implementation with default startUpdateCredentialsSession.
     * Only account name and account type should be returned as a bundle.
     */
    public void testFinishSessionAndStartUpdateCredentialsSessionDefaultImpl()
            throws OperationCanceledException, AuthenticatorException, IOException,
            RemoteException {
        Bundle options = new Bundle();
        String accountName = Fixtures.PREFIX_NAME_SUCCESS + "@" + Fixtures.SUFFIX_NAME_FIXTURE;
        options.putString(Fixtures.KEY_ACCOUNT_NAME, accountName);

        // First obtain an encrypted session bundle from startUpdateCredentialsSession(...) default
        // implementation.
        AccountManagerFuture<Bundle> future = mAccountManager.startUpdateCredentialsSession(
                Fixtures.ACCOUNT_DEFAULT,
                null /* authTokenTYpe */,
                options,
                null /* activity */,
                null /* callback */,
                null /* handler */);

        Bundle result = future.getResult();
        assertTrue(future.isDone());
        assertNotNull(result);

        // Assert that result contains a non-null session bundle.
        Bundle escrowBundle = result.getBundle(AccountManager.KEY_ACCOUNT_SESSION_BUNDLE);
        assertNotNull(escrowBundle);

        // Now call finishSession(...) with the session bundle we just obtained.
        future = mAccountManager.finishSession(
                escrowBundle,
                null /* activity */,
                null /* callback */,
                null /* handler */);

        result = future.getResult();
        assertTrue(future.isDone());
        assertNotNull(result);

        // Validate that parameters are passed to updateCredentials(...) correctly in default
        // finishSession implementation.
        Bundle providerBundle = mProviderClient.call(
                AuthenticatorContentProvider.METHOD_GET,
                null /* arg */,
                null /* extras */);
        providerBundle.setClassLoader(UpdateCredentialsTx.class.getClassLoader());
        UpdateCredentialsTx updateCredentialsTx = providerBundle
                .getParcelable(AuthenticatorContentProvider.KEY_TX);
        assertNotNull(updateCredentialsTx);

        // Assert parameters has been passed to updateCredentials(...) correctly
        assertEquals(Fixtures.ACCOUNT_DEFAULT, updateCredentialsTx.account);
        assertNull(updateCredentialsTx.authTokenType);

        validateSystemOptions(updateCredentialsTx.options);
        // Validate options
        assertNotNull(updateCredentialsTx.options);
        assertEquals(accountName, updateCredentialsTx.options.getString(Fixtures.KEY_ACCOUNT_NAME));

        // Assert returned result contains correct account name, account type and null auth token.
        assertEquals(accountName, result.get(AccountManager.KEY_ACCOUNT_NAME));
        assertEquals(Fixtures.TYPE_DEFAULT, result.get(AccountManager.KEY_ACCOUNT_TYPE));
        assertNull(result.get(AccountManager.KEY_AUTHTOKEN));
    }

    private void validateSystemOptions(Bundle options) {
        assertNotNull(options.getString(AccountManager.KEY_ANDROID_PACKAGE_NAME));
        assertTrue(options.containsKey(AccountManager.KEY_CALLER_UID));
        assertTrue(options.containsKey(AccountManager.KEY_CALLER_PID));
    }

    private void validateNullPasswordAndStatusToken(Bundle result) {
        assertNull(result.getString(AccountManager.KEY_PASSWORD));
        assertNull(result.getString(AccountManager.KEY_ACCOUNT_STATUS_TOKEN));
    }

    /**
     * Tests isCredentialsUpdateSuggested default implementation.
     * A bundle with boolean false should be returned.
     */
    public void testIsCredentialsUpdateSuggestedDefaultImpl()
            throws OperationCanceledException, AuthenticatorException, IOException,
            RemoteException {
        String accountName = Fixtures.PREFIX_NAME_SUCCESS + "@" + Fixtures.SUFFIX_NAME_FIXTURE;
        Account account = new Account(accountName, Fixtures.TYPE_DEFAULT);
        String statusToken = Fixtures.PREFIX_STATUS_TOKEN + accountName;

        AccountManagerFuture<Boolean> future = mAccountManager.isCredentialsUpdateSuggested(
                account,
                statusToken,
                null /* callback */,
                null /* handler */);

        assertFalse(future.getResult());
        assertTrue(future.isDone());
    }
}
