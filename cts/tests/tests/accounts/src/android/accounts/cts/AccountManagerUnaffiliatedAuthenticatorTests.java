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

package android.accounts.cts;

import android.accounts.Account;
import android.accounts.AccountManager;
import android.accounts.AccountManagerFuture;
import android.accounts.AuthenticatorException;
import android.accounts.OperationCanceledException;
import android.accounts.cts.common.AuthenticatorContentProvider;
import android.accounts.cts.common.Fixtures;
import android.accounts.cts.common.tx.StartAddAccountSessionTx;
import android.accounts.cts.common.tx.StartUpdateCredentialsSessionTx;
import android.content.ContentProviderClient;
import android.content.ContentResolver;
import android.os.Bundle;
import android.os.RemoteException;
import android.test.AndroidTestCase;

import java.io.IOException;
import java.util.HashMap;

/**
 * Tests for AccountManager and AbstractAccountAuthenticator related behavior using {@link
 * android.accounts.cts.common.TestAccountAuthenticator} instances signed with different keys than
 * the caller. This is important to test that portion of the {@link AccountManager} API intended
 * for {@link android.accounts.AbstractAccountAuthenticator} implementers.
 * <p>
 * You can run those unit tests with the following command line:
 * <p>
 *  adb shell am instrument
 *   -e debug false -w
 *   -e class android.accounts.cts.AccountManagerUnaffiliatedAuthenticatorTests
 * android.accounts.cts/android.support.test.runner.AndroidJUnitRunner
 */
public class AccountManagerUnaffiliatedAuthenticatorTests extends AndroidTestCase {

    public static final Bundle SESSION_BUNDLE = new Bundle();
    public static final String SESSION_DATA_NAME_1 = "session.data.name.1";
    public static final String SESSION_DATA_VALUE_1 = "session.data.value.1";

    private AccountManager mAccountManager;
    private ContentProviderClient mProviderClient;

    @Override
    public void setUp() throws Exception {
        SESSION_BUNDLE.putString(SESSION_DATA_NAME_1, SESSION_DATA_VALUE_1);

        // bind to the diagnostic service and set it up.
        mAccountManager = AccountManager.get(getContext());
        ContentResolver resolver = getContext().getContentResolver();
        mProviderClient = resolver.acquireContentProviderClient(
                AuthenticatorContentProvider.AUTHORITY);
        /*
         * This will install a bunch of accounts on the device
         * (see Fixtures.getFixtureAccountNames()).
         */
        mProviderClient.call(AuthenticatorContentProvider.METHOD_SETUP, null, null);
    }

    @Override
    public void tearDown() throws RemoteException {
        try {
            mProviderClient.call(AuthenticatorContentProvider.METHOD_TEARDOWN, null, null);
        } finally {
            mProviderClient.release();
        }
    }

    public void testNotifyAccountAuthenticated() {
        try {
            mAccountManager.notifyAccountAuthenticated(
                    Fixtures.ACCOUNT_UNAFFILIATED_FIXTURE_SUCCESS);
            fail("Expected to just barf if the caller doesn't share a signature.");
        } catch (SecurityException expected) {}
    }

    public void testEditProperties()  {
        try {
            mAccountManager.editProperties(
                    Fixtures.TYPE_STANDARD_UNAFFILIATED,
                    null, // activity
                    null, // callback
                    null); // handler
            fail("Expecting a OperationCanceledException.");
        } catch (SecurityException expected) {

        }
    }

    public void testAddAccountExplicitly() {
        try {
            mAccountManager.addAccountExplicitly(
                    Fixtures.ACCOUNT_UNAFFILIATED_FIXTURE_SUCCESS,
                    "shouldn't matter", // password
                    null); // bundle
            fail("addAccountExplicitly should just barf if the caller isn't permitted.");
        } catch (SecurityException expected) {}
    }

    public void testRemoveAccount_withBooleanResult() {
        try {
            mAccountManager.removeAccount(
                    Fixtures.ACCOUNT_UNAFFILIATED_FIXTURE_SUCCESS,
                    null,
                    null);
            fail("removeAccount should just barf if the caller isn't permitted.");
        } catch (SecurityException expected) {}
    }

    public void testRemoveAccount_withBundleResult() {
        try {
            mAccountManager.removeAccount(
                    Fixtures.ACCOUNT_UNAFFILIATED_FIXTURE_SUCCESS,
                    null, // Activity
                    null,
                    null);
            fail("removeAccount should just barf if the caller isn't permitted.");
        } catch (SecurityException expected) {}
    }

    public void testRemoveAccountExplicitly() {
        try {
            mAccountManager.removeAccountExplicitly(
                    Fixtures.ACCOUNT_UNAFFILIATED_FIXTURE_SUCCESS);
            fail("removeAccountExplicitly should just barf if the caller isn't permitted.");
        } catch (SecurityException expected) {}
    }

    public void testGetPassword() {
        try {
            mAccountManager.getPassword(
                    Fixtures.ACCOUNT_UNAFFILIATED_FIXTURE_SUCCESS);
            fail("getPassword should just barf if the caller isn't permitted.");
        } catch (SecurityException expected) {}
    }

    public void testSetPassword() {
        try {
            mAccountManager.setPassword(
                    Fixtures.ACCOUNT_UNAFFILIATED_FIXTURE_SUCCESS,
                    "Doesn't matter");
            fail("setPassword should just barf if the caller isn't permitted.");
        } catch (SecurityException expected) {}
    }

    public void testClearPassword() {
        try {
            mAccountManager.clearPassword(
                    Fixtures.ACCOUNT_UNAFFILIATED_FIXTURE_SUCCESS);
            fail("clearPassword should just barf if the caller isn't permitted.");
        } catch (SecurityException expected) {}
    }

    public void testGetUserData() {
        try {
            mAccountManager.getUserData(
                    Fixtures.ACCOUNT_UNAFFILIATED_FIXTURE_SUCCESS,
                    "key");
            fail("getUserData should just barf if the caller isn't permitted.");
        } catch (SecurityException expected) {}
    }

    public void testSetUserData() {
        try {
            mAccountManager.setUserData(
                    Fixtures.ACCOUNT_UNAFFILIATED_FIXTURE_SUCCESS,
                    "key",
                    "value");
            fail("setUserData should just barf if the caller isn't permitted.");
        } catch (SecurityException expected) {}
    }

    public void setAuthToken() {
        try {
            mAccountManager.setAuthToken(Fixtures.ACCOUNT_UNAFFILIATED_FIXTURE_SUCCESS, "tokenType",
                    "token");
            fail("setAuthToken should just barf if the caller isn't permitted.");
        } catch (SecurityException expected) {
        }
    }

    public void testPeekAuthToken() {
        try {
            mAccountManager.peekAuthToken(Fixtures.ACCOUNT_UNAFFILIATED_FIXTURE_SUCCESS,
                    "tokenType");
            fail("peekAuthToken should just barf if the caller isn't permitted.");
        } catch (SecurityException expected) {
        }
    }

    public void testSetAccountVisibility()
            throws IOException, AuthenticatorException, OperationCanceledException {
        try {
            mAccountManager.setAccountVisibility(Fixtures.ACCOUNT_UNAFFILIATED_FIXTURE_SUCCESS,
                    "some", AccountManager.VISIBILITY_VISIBLE);
            fail("setAccountVisibility should just barf if the caller isn't permitted.");
        } catch (SecurityException expected) {
        }
    }

    public void testGetAccountVisibility()
            throws IOException, AuthenticatorException, OperationCanceledException {
        try {
            mAccountManager.getAccountVisibility(Fixtures.ACCOUNT_UNAFFILIATED_FIXTURE_SUCCESS,
                    "some.example");
            fail("getAccountVisibility should just barf if the caller isn't permitted.");
        } catch (SecurityException expected) {
        }
    }

    public void testGetAccountsAndVisibilityForPackage()
            throws IOException, AuthenticatorException, OperationCanceledException {
        try {
            mAccountManager.getAccountsAndVisibilityForPackage("some.package",
                    Fixtures.TYPE_STANDARD_UNAFFILIATED);
            fail("getAccountsAndVisibilityForPackage should just barf if the caller isn't permitted.");
        } catch (SecurityException expected) {
        }
    }

    public void testGetPackagesAndVisibilityForAccount()
            throws IOException, AuthenticatorException, OperationCanceledException {
        try {
            mAccountManager.getPackagesAndVisibilityForAccount(
                    Fixtures.ACCOUNT_UNAFFILIATED_FIXTURE_SUCCESS);
            fail("getRequestingUidsForType should just barf if the caller isn't permitted.");
        } catch (SecurityException expected) {
        }
    }

    public void testAddAccountExplicitlyVisthVisibilityMap()
            throws IOException, AuthenticatorException, OperationCanceledException {
        try {
            mAccountManager.addAccountExplicitly(Fixtures.ACCOUNT_UNAFFILIATED_FIXTURE_SUCCESS,
                    "shouldn't matter", // password
                    null, // bundle
                    new HashMap<String, Integer>()); // visibility;
            fail("addAccountExplicitly should just barf if the caller isn't permitted.");
        } catch (SecurityException expected) {
        }
    }

    public void testGetAccounts() {
        Account[] accounts = mAccountManager.getAccounts();
        assertEquals(0, accounts.length);
    }

    public void testGetAccountsByType() {
        Account[] accounts = mAccountManager.getAccountsByType(Fixtures.TYPE_STANDARD_UNAFFILIATED);
        assertEquals(0, accounts.length);
    }

    public void testGetAccountsByTypeAndFeatures()
            throws OperationCanceledException, AuthenticatorException, IOException {
        AccountManagerFuture<Account[]> future = mAccountManager.getAccountsByTypeAndFeatures(
                Fixtures.TYPE_STANDARD_UNAFFILIATED,
                new String[] { "doesn't matter" },
                null,  // Callback
                null);  // Handler
        Account[] accounts = future.getResult();
        assertEquals(0, accounts.length);
    }

    public void testGetAccountsByTypeForPackage() {
        Account[] accounts = mAccountManager.getAccountsByTypeForPackage(
                Fixtures.TYPE_STANDARD_UNAFFILIATED,
                getContext().getPackageName());
        assertEquals(0, accounts.length);
    }

    /**
     * Tests startAddAccountSession when calling package doesn't have the same sig as the
     * authenticator.
     * An encrypted session bundle should always be returned without password.
     */
    public void testStartAddAccountSession() throws
            OperationCanceledException, AuthenticatorException, IOException, RemoteException {
        String accountName = Fixtures.PREFIX_NAME_SUCCESS + "@" + Fixtures.SUFFIX_NAME_FIXTURE;
        Bundle options = createOptionsWithAccountName(accountName);

        AccountManagerFuture<Bundle> future = mAccountManager.startAddAccountSession(
                Fixtures.TYPE_STANDARD_UNAFFILIATED,
                null /* authTokenType */,
                null /* requiredFeatures */,
                options,
                null /* activity */,
                null /* callback */,
                null /* handler */);

        Bundle result = future.getResult();
        assertTrue(future.isDone());
        assertNotNull(result);

        validateStartAddAccountSessionParameters(options);

        // Validate that auth token was stripped from result.
        assertNull(result.get(AccountManager.KEY_AUTHTOKEN));

        // Validate returned data
        validateSessionBundleAndPasswordAndStatusTokenResult(result);
    }

    /**
     * Tests startUpdateCredentialsSession when calling package doesn't have the same sig as
     * the authenticator.
     * An encrypted session bundle should always be returned without password.
     */
    public void testStartUpdateCredentialsSession() throws
            OperationCanceledException, AuthenticatorException, IOException, RemoteException {
        String accountName = Fixtures.PREFIX_NAME_SUCCESS + "@" + Fixtures.SUFFIX_NAME_FIXTURE;
        Bundle options = createOptionsWithAccountName(accountName);

        AccountManagerFuture<Bundle> future = mAccountManager.startUpdateCredentialsSession(
                Fixtures.ACCOUNT_UNAFFILIATED_FIXTURE_SUCCESS,
                null /* authTokenType */,
                options,
                null /* activity */,
                null /* callback */,
                null /* handler */);

        Bundle result = future.getResult();
        assertTrue(future.isDone());
        assertNotNull(result);

        validateStartUpdateCredentialsSessionParameters(options);

        // Validate no auth token in result.
        assertNull(result.get(AccountManager.KEY_AUTHTOKEN));

        // Validate returned data
        validateSessionBundleAndPasswordAndStatusTokenResult(result);
    }

    /**
     * Tests finishSession default implementation with overridden startAddAccountSession
     * implementation. AuthenticatorException is expected because default AbstractAuthenticator
     * implementation cannot understand customized session bundle.
     */
    public void testDefaultFinishSessiontWithStartAddAccountSessionImpl()
            throws OperationCanceledException, AuthenticatorException, IOException {
        String accountName = Fixtures.PREFIX_NAME_SUCCESS + "@" + Fixtures.SUFFIX_NAME_FIXTURE;
        // Creates session bundle to be returned by custom implementation of
        // startAddAccountSession of authenticator.
        Bundle sessionBundle = new Bundle();
        sessionBundle.putString(Fixtures.KEY_ACCOUNT_NAME, accountName);
        sessionBundle.putString(AccountManager.KEY_ACCOUNT_TYPE,
                Fixtures.TYPE_STANDARD_UNAFFILIATED);
        Bundle options = new Bundle();
        options.putString(Fixtures.KEY_ACCOUNT_NAME, accountName);
        options.putBundle(Fixtures.KEY_ACCOUNT_SESSION_BUNDLE, sessionBundle);

        // First get an encrypted session bundle from custom startAddAccountSession implementation.
        AccountManagerFuture<Bundle> future = mAccountManager.startAddAccountSession(
                Fixtures.TYPE_STANDARD_UNAFFILIATED,
                null /* authTokenType */,
                null /* requiredFeatures */,
                options,
                null /* activity */,
                null /* callback */,
                null /* handler */);

        Bundle result = future.getResult();
        assertTrue(future.isDone());
        assertNotNull(result);

        Bundle decryptedBundle = result.getBundle(AccountManager.KEY_ACCOUNT_SESSION_BUNDLE);
        assertNotNull(decryptedBundle);

        try {
            // Call default implementation of finishSession of authenticator
            // with encrypted session bundle.
            future = mAccountManager.finishSession(
                    decryptedBundle,
                    null /* activity */,
                    null /* callback */,
                    null /* handler */);
            future.getResult();

            fail("Should have thrown AuthenticatorException if finishSession is not overridden.");
        } catch (AuthenticatorException e) {
        }
    }

    /**
     * Tests finishSession default implementation with overridden startUpdateCredentialsSession
     * implementation. AuthenticatorException is expected because default implementation cannot
     * understand custom session bundle.
     */
    public void testDefaultFinishSessionWithCustomStartUpdateCredentialsSessionImpl()
            throws OperationCanceledException, AuthenticatorException, IOException {
        String accountName = Fixtures.PREFIX_NAME_SUCCESS + "@" + Fixtures.SUFFIX_NAME_FIXTURE;
        // Creates session bundle to be returned by custom implementation of
        // startUpdateCredentialsSession of authenticator.
        Bundle sessionBundle = new Bundle();
        sessionBundle.putString(Fixtures.KEY_ACCOUNT_NAME, accountName);
        sessionBundle.putString(AccountManager.KEY_ACCOUNT_TYPE,
                Fixtures.TYPE_STANDARD_UNAFFILIATED);
        Bundle options = new Bundle();
        options.putString(Fixtures.KEY_ACCOUNT_NAME, accountName);
        options.putBundle(Fixtures.KEY_ACCOUNT_SESSION_BUNDLE, sessionBundle);

        // First get an encrypted session bundle from custom
        // startUpdateCredentialsSession implementation.
        AccountManagerFuture<Bundle> future = mAccountManager.startUpdateCredentialsSession(
                Fixtures.ACCOUNT_UNAFFILIATED_FIXTURE_SUCCESS,
                null /* authTokenType */,
                options,
                null /* activity */,
                null /* callback */,
                null /* handler */);

        Bundle result = future.getResult();
        assertTrue(future.isDone());
        assertNotNull(result);

        Bundle decryptedBundle = result.getBundle(AccountManager.KEY_ACCOUNT_SESSION_BUNDLE);
        assertNotNull(decryptedBundle);

        try {
            // Call default implementation of finishSession of authenticator
            // with encrypted session bundle.
            future = mAccountManager.finishSession(
                    decryptedBundle,
                    null /* activity */,
                    null /* callback */,
                    null /* handler */);
            future.getResult();

            fail("Should have thrown AuthenticatorException if finishSession is not overridden.");
        } catch (AuthenticatorException e) {
        }
    }

    private void validateStartAddAccountSessionParameters(Bundle inOpt)
            throws RemoteException {
        Bundle params = mProviderClient.call(AuthenticatorContentProvider.METHOD_GET, null, null);
        params.setClassLoader(StartAddAccountSessionTx.class.getClassLoader());
        StartAddAccountSessionTx tx = params.<StartAddAccountSessionTx>getParcelable(
                AuthenticatorContentProvider.KEY_TX);
        assertEquals(tx.accountType, Fixtures.TYPE_STANDARD_UNAFFILIATED);
        assertEquals(tx.options.getString(Fixtures.KEY_ACCOUNT_NAME),
                inOpt.getString(Fixtures.KEY_ACCOUNT_NAME));
    }

    private void validateStartUpdateCredentialsSessionParameters(Bundle inOpt)
            throws RemoteException {
        Bundle params = mProviderClient.call(AuthenticatorContentProvider.METHOD_GET, null, null);
        params.setClassLoader(StartUpdateCredentialsSessionTx.class.getClassLoader());
        StartUpdateCredentialsSessionTx tx =
                params.<StartUpdateCredentialsSessionTx>getParcelable(
                        AuthenticatorContentProvider.KEY_TX);
        assertEquals(tx.account, Fixtures.ACCOUNT_UNAFFILIATED_FIXTURE_SUCCESS);
        assertEquals(tx.options.getString(Fixtures.KEY_ACCOUNT_NAME),
                inOpt.getString(Fixtures.KEY_ACCOUNT_NAME));
    }

    private Bundle createOptionsWithAccountName(final String accountName) {
        Bundle options = new Bundle();
        options.putString(Fixtures.KEY_ACCOUNT_NAME, accountName);
        options.putBundle(Fixtures.KEY_ACCOUNT_SESSION_BUNDLE, SESSION_BUNDLE);
        return options;
    }

    private void validateSessionBundleAndPasswordAndStatusTokenResult(Bundle result)
        throws RemoteException {
        Bundle sessionBundle = result.getBundle(AccountManager.KEY_ACCOUNT_SESSION_BUNDLE);
        assertNotNull(sessionBundle);
        // Assert that session bundle is encrypted and hence data not visible.
        assertNull(sessionBundle.getString(SESSION_DATA_NAME_1));
        // Validate that no password is returned in the result for unaffiliated package.
        assertNull(result.getString(AccountManager.KEY_PASSWORD));
        assertEquals(Fixtures.ACCOUNT_STATUS_TOKEN_UNAFFILIATED,
                result.getString(AccountManager.KEY_ACCOUNT_STATUS_TOKEN));
    }
}

