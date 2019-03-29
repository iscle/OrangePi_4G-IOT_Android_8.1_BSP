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

import android.accounts.AbstractAccountAuthenticator;
import android.accounts.Account;
import android.accounts.AccountAuthenticatorResponse;
import android.accounts.AccountManager;
import android.accounts.NetworkErrorException;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.os.IBinder;
import android.os.SystemClock;
import android.text.TextUtils;
import android.util.Log;

import java.util.Arrays;
import java.util.List;

public class TestAuthenticator extends Service {
    private static final String TAG = "TestAuthenticator";

    private static Authenticator sInstance;

    @Override
    public IBinder onBind(Intent intent) {
        if (sInstance == null) {
            sInstance = new Authenticator(getApplicationContext());

        }
        return sInstance.getIBinder();
    }

    public static class Authenticator extends AbstractAccountAuthenticator {

        private final Context mContxet;

        public Authenticator(Context context) {
            super(context);
            mContxet = context;
        }

        @Override
        public Bundle addAccount(AccountAuthenticatorResponse response, String accountType,
                String authTokenType, String[] requiredFeatures, Bundle options)
                throws NetworkErrorException {

            // Create an account whose name is:
            //   [current time] + ":" + [all requested features concatenated with , ]

            if (requiredFeatures == null) {
                requiredFeatures = new String[0];
            }

            final String name = SystemClock.elapsedRealtimeNanos()
                    + ":" + TextUtils.join(",", requiredFeatures);

            Log.v(TAG, "Adding account '" + name + "' for " + accountType
                    + "... " + Arrays.asList(requiredFeatures));

            Bundle result = new Bundle();
            result.putString(AccountManager.KEY_ACCOUNT_TYPE, accountType);
            result.putString(AccountManager.KEY_ACCOUNT_NAME, name);

            mContxet.getSystemService(AccountManager.class).addAccountExplicitly(
                    new Account(name, accountType), "password", new Bundle());

            return result;
        }

        @Override
        public Bundle editProperties(AccountAuthenticatorResponse response, String accountType) {
            return new Bundle();
        }

        @Override
        public Bundle updateCredentials(AccountAuthenticatorResponse response, Account account,
                String authTokenType, Bundle options) throws NetworkErrorException {
            return new Bundle();
        }

        @Override
        public Bundle confirmCredentials(AccountAuthenticatorResponse response, Account account,
                Bundle options) throws NetworkErrorException {
            return new Bundle();
        }

        @Override
        public Bundle getAuthToken(AccountAuthenticatorResponse response, Account account,
                String authTokenType, Bundle options) throws NetworkErrorException {
            return new Bundle();
        }

        @Override
        public String getAuthTokenLabel(String authTokenType) {
            return "token_label";
        }

        @Override
        public Bundle hasFeatures(AccountAuthenticatorResponse response, Account account,
                String[] features) throws NetworkErrorException {

            final int p = account.name.indexOf(':');

            boolean hasAll = true;
            final List<String> hasFeatures =
                    Arrays.asList(TextUtils.split(account.name.substring(p + 1), ","));
            for (String requested : features) {
                if (!hasFeatures.contains(requested)) {
                    hasAll = false;
                    break;
                }
            }

            Bundle result = new Bundle();
            result.putBoolean(AccountManager.KEY_BOOLEAN_RESULT, hasAll);
            return result;
        }
    }
}
