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
package com.android.car.apps.common;

import android.annotation.RequiresPermission;
import android.accounts.Account;
import android.accounts.AccountManager;
import android.content.ContentUris;
import android.content.Context;
import android.database.ContentObserver;
import android.database.Cursor;
import android.net.Uri;
import android.provider.ContactsContract;
import android.provider.ContactsContract.Contacts;
import android.text.TextUtils;

import java.util.HashMap;
import java.util.LinkedHashSet;

import static android.Manifest.permission.GET_ACCOUNTS;

/**
 * @hide
 */
public class AccountImageChangeObserver {
    private static final String GOOGLE_ACCOUNT_TYPE = "com.google";

    private static final Object sObserverInstanceLock = new Object();
    private static AccountImageChangeObserver sObserver;

    private class ContactChangeContentObserver extends ContentObserver {
        private final Account mWatchedAccount;
        private final LinkedHashSet<Uri> mUrisToNotify;
        private final Object mLock = new Object();
        private final Context mContext;
        private String mCurrentImageUri;

        public ContactChangeContentObserver(Context context, Account watchedAccount) {
            super(null);
            mWatchedAccount = watchedAccount;
            mUrisToNotify = new LinkedHashSet<>();
            mContext = context;
            mCurrentImageUri = AccountImageHelper.getAccountPictureUri(mContext, mWatchedAccount);
        }

        @Override
        public boolean deliverSelfNotifications() {
            return true;
        }

        public void addUriToNotifyList(Uri uri) {
            synchronized (mLock) {
                mUrisToNotify.add(uri);
            }
        }

        @Override
        public void onChange(boolean selfChange) {
            String newUri = AccountImageHelper.getAccountPictureUri(mContext, mWatchedAccount);

            if (TextUtils.equals(mCurrentImageUri, newUri)) {
                // no change, no need to notify
                return;
            }

            synchronized (mLock) {
                for (Uri uri : mUrisToNotify) {
                    mContext.getContentResolver().notifyChange(uri, null);
                }

                mCurrentImageUri = newUri;
            }
        }
    }

    private final HashMap<String, ContactChangeContentObserver> mObserverMap;


    /**
     * get the singleton AccountImageChangeObserver for the application
     */
    public final static AccountImageChangeObserver getInstance() {
        if (sObserver == null) {
            synchronized (sObserverInstanceLock) {
                if (sObserver == null) {
                    sObserver = new AccountImageChangeObserver();
                }
            }
        }
        return sObserver;
    }

    public AccountImageChangeObserver() {
        mObserverMap = new HashMap<>();
    }

    @RequiresPermission(GET_ACCOUNTS)
    public synchronized void registerChangeUriIfPresent(BitmapWorkerOptions options) {
        Uri imageUri = options.getResourceUri();
        // Only register URIs that match the Account Image URI schema, and
        // have a change notify URI specified.
        if (imageUri != null && UriUtils.isAccountImageUri(imageUri)) {
            Uri changeNotifUri = UriUtils.getAccountImageChangeNotifyUri(imageUri);
            imageUri = imageUri.buildUpon().clearQuery().build();

            if (changeNotifUri == null) {
                // No change Notiy URI specified
                return;
            }

            String accountName = UriUtils.getAccountName(imageUri);
            Context context = options.getContext();

            if (accountName != null && context != null) {
                Account thisAccount = null;
                for (Account account : AccountManager.get(context).
                        getAccountsByType(GOOGLE_ACCOUNT_TYPE)) {
                    if (account.name.equals(accountName)) {
                        thisAccount = account;
                        break;
                    }
                }
                if (thisAccount != null) {
                    ContactChangeContentObserver observer;

                    if (mObserverMap.containsKey(thisAccount.name)) {
                        observer = mObserverMap.get(thisAccount.name);
                        if (observer != null) {
                            observer.addUriToNotifyList(changeNotifUri);
                        }
                    } else {
                        long contactId = getContactIdForAccount(context, thisAccount);
                        if (contactId != -1) {
                            Uri contactUri = ContentUris.withAppendedId(Contacts.CONTENT_URI,
                                    contactId);
                            observer = new ContactChangeContentObserver(context, thisAccount);
                            mObserverMap.put(thisAccount.name, observer);
                            observer.addUriToNotifyList(changeNotifUri);
                            context.getContentResolver().registerContentObserver(contactUri, false,
                                    observer);
                        }
                    }
                }
            }
        }
    }

    private long getContactIdForAccount(Context context, Account account) {
        // Look up this account in the contacts database.
        String[] projection = new String[] {
                ContactsContract.Data._ID,
                ContactsContract.Data.CONTACT_ID,
                ContactsContract.Data.LOOKUP_KEY
        };
        String selection =
                ContactsContract.CommonDataKinds.Email.ADDRESS + " LIKE ?";
        String[] selectionArgs = new String[] { account.name };
        Cursor c = null;
        long contactId = -1;
        String lookupKey = null;
        try {
            c = context.getContentResolver().query(ContactsContract.Data.CONTENT_URI,
                    projection, selection, selectionArgs, null);
            if (c.moveToNext()) {
                contactId = c.getLong(1);
                lookupKey = c.getString(2);
            }
        } finally {
            if (c != null) {
                c.close();
            }
        }

        if (contactId != -1 && !TextUtils.isEmpty(lookupKey)) {
            return contactId;
        }

        return -1;
    }
}
