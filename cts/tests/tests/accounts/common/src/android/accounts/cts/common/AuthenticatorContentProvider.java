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

import android.accounts.Account;
import android.accounts.AccountManager;
import android.accounts.AuthenticatorDescription;
import android.content.ContentProvider;
import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.os.Parcelable;

import java.util.concurrent.atomic.AtomicReference;

public class AuthenticatorContentProvider extends ContentProvider {

    public static final String AUTHORITY =
            "android.accounts.cts.unaffiliated.authenticators.provider";

    public static final int RESULT_SUCCESS = 1;
    public static final int RESULT_FAIL = 2;

    public static final String METHOD_GET = "get";
    public static final String METHOD_SETUP = "setup";
    public static final String METHOD_TEARDOWN = "setup";

    public static final int ACTION_GET = 1;
    public static final int ACTION_SETUP = 2;
    public static final int ACTION_TEARDOWN = 3;

    public static final int ARG_UNAFFILIATED = 10;
    public static final int ARG_AFFILIATED = 11;

    public static final String KEY_CALLBACK = "callback";
    public static final String KEY_TX = "tx";

    public static final AtomicReference<Parcelable> sLastTx = new AtomicReference<>();

    public static void setTx(Parcelable tx) {
        sLastTx.set(tx);
    }

    @Override
    // public void handleMessage(Message msg) {
    public Bundle call(String method, String arg, Bundle extras) {
        super.call(method, arg, extras);
        Bundle result = new Bundle();
        if (METHOD_GET.equals(method)) {
            result.putParcelable(KEY_TX, sLastTx.get());
            return result;
        } else if (METHOD_SETUP.equals(method)) {
            setup();
            return result;
        } else if (METHOD_TEARDOWN.equals(method)) {
            teardown();
            return result;
        } else {
            throw new IllegalArgumentException("Unrecognized method!");
        }
    }

    public void setup() {
        Context context = getContext();
        AccountManager am = AccountManager.get(context);
        AuthenticatorDescription[] authenticators = am.getAuthenticatorTypes();
        for (AuthenticatorDescription a : authenticators) {
            /*
             * Populate relevant test information for authenticators in the
             * same package as the TestAuthenticatorSupportHandler.
             */
            if (a.packageName.equals(context.getPackageName())) {
                for (String name : Fixtures.getFixtureAccountNames()) {
                    Account account = new Account(name, a.type);
                    am.addAccountExplicitly(account, Fixtures.PREFIX_PASSWORD + name, null);
                }
            }
        }
    }

    public void teardown() {
        Context context = getContext();
        AccountManager am = AccountManager.get(context);
        AuthenticatorDescription[] authenticators = am.getAuthenticatorTypes();
        for (AuthenticatorDescription a : authenticators) {
            /*
             * Populate relevant test information for authenticators in the
             * same package as the TestAuthenticatorSupportHandler.
             */
            if (a.packageName.equals(context.getPackageName())) {
                Account[] accountsToRemove = am.getAccountsByType(a.type);
                for (Account account : accountsToRemove) {
                    am.removeAccountExplicitly(account);
                }
            }
        }
    }

    @Override
    public boolean onCreate() {
        return true;   
    }

    @Override
    public Cursor query(
            Uri uri, 
            String[] projection,
            String selection,
            String[] selectionArgs,
            String sortOrder) {
        throw new UnsupportedOperationException();
    }

    @Override
    public String getType(Uri uri) {
        throw new UnsupportedOperationException();
    }

    @Override
    public Uri insert(Uri uri, ContentValues values) {
        throw new UnsupportedOperationException();
    }

    @Override
    public int delete(Uri uri, String selection, String[] selectionArgs) {
        throw new UnsupportedOperationException();
    }

    @Override
    public int update(Uri uri, ContentValues values, String selection, String[] selectionArgs) {
        throw new UnsupportedOperationException();
    }
}

