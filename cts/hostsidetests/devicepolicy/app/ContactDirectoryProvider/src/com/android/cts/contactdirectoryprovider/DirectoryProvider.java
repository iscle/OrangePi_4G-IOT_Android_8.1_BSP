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

package com.android.cts.contactdirectoryprovider;

import android.accounts.Account;
import android.accounts.AccountManager;
import android.content.ContentProvider;
import android.content.ContentValues;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.UriMatcher;
import android.content.res.AssetFileDescriptor;
import android.content.res.Resources;
import android.database.Cursor;
import android.database.MatrixCursor;
import android.net.Uri;
import android.os.Binder;
import android.os.Bundle;
import android.os.ParcelFileDescriptor;
import android.provider.ContactsContract.Contacts;
import android.provider.ContactsContract.Directory;
import android.util.Log;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;
import java.util.concurrent.Callable;
import java.util.concurrent.TimeUnit;

public class DirectoryProvider extends ContentProvider {
    private static final String TAG = "DirectoryProvider";
    private static final String CONFIG_NAME = "config";
    private static final String SET_CUSTOM_PREFIX = "set_prefix";
    private static final String AUTHORITY = "com.android.cts.contact.directory.provider";
    // Same as com.android.cts.managedprofile.AccountAuthenticator.ACCOUNT_TYPE
    private static final String TEST_ACCOUNT_TYPE = "com.android.cts.test";
    private static final String DEFAULT_DISPLAY_NAME = "Directory";
    private static final String DEFAULT_CONTACT_NAME = "DirectoryContact";

    private static final Uri AUTHORITY_URI = Uri.parse("content://" + AUTHORITY);
    private static final String PRIMARY_THUMBNAIL = "photo/primary_thumbnail";
    private static final String PRIMARY_PHOTO = "photo/primary_photo";
    private static final String MANAGED_THUMBNAIL = "photo/managed_thumbnail";
    private static final String MANAGED_PHOTO = "photo/managed_photo";
    private static final Uri PRIMARY_THUMBNAIL_URI = Uri.withAppendedPath(AUTHORITY_URI,
            PRIMARY_THUMBNAIL);
    private static final Uri PRIMARY_PHOTO_URI = Uri.withAppendedPath(AUTHORITY_URI,
            PRIMARY_PHOTO);
    private static final Uri MANAGED_THUMBNAIL_URI = Uri.withAppendedPath(AUTHORITY_URI,
            MANAGED_THUMBNAIL);
    private static final Uri MANAGED_PHOTO_URI = Uri.withAppendedPath(AUTHORITY_URI,
            MANAGED_PHOTO);

    private static final int GAL_BASE = 0;
    private static final int GAL_DIRECTORIES = GAL_BASE;
    private static final int GAL_FILTER = GAL_BASE + 1;
    private static final int GAL_CONTACT = GAL_BASE + 2;
    private static final int GAL_CONTACT_WITH_ID = GAL_BASE + 3;
    private static final int GAL_EMAIL_FILTER = GAL_BASE + 4;
    private static final int GAL_PHONE_FILTER = GAL_BASE + 5;
    private static final int GAL_PHONE_LOOKUP = GAL_BASE + 6;
    private static final int GAL_CALLABLES_FILTER = GAL_BASE + 7;
    private static final int GAL_EMAIL_LOOKUP = GAL_BASE + 8;
    private static final int GAL_PRIMARY_THUMBNAIL = GAL_BASE + 9;
    private static final int GAL_PRIMARY_PHOTO = GAL_BASE + 10;
    private static final int GAL_MANAGED_THUMBNAIL = GAL_BASE + 11;
    private static final int GAL_MANAGED_PHOTO = GAL_BASE + 12;

    private final UriMatcher mURIMatcher = new UriMatcher(UriMatcher.NO_MATCH);

    private SharedPreferences mSharedPrefs;

    @Override
    public boolean onCreate() {
        mURIMatcher.addURI(AUTHORITY, "directories", GAL_DIRECTORIES);
        mURIMatcher.addURI(AUTHORITY, "contacts/filter/*", GAL_FILTER);
        mURIMatcher.addURI(AUTHORITY, "contacts/lookup/*/entities", GAL_CONTACT);
        mURIMatcher.addURI(AUTHORITY, "contacts/lookup/*/#/entities", GAL_CONTACT_WITH_ID);
        mURIMatcher.addURI(AUTHORITY, "data/emails/filter/*", GAL_EMAIL_FILTER);
        mURIMatcher.addURI(AUTHORITY, "data/phones/filter/*", GAL_PHONE_FILTER);
        mURIMatcher.addURI(AUTHORITY, "phone_lookup/*", GAL_PHONE_LOOKUP);
        mURIMatcher.addURI(AUTHORITY, "data/callables/filter/*", GAL_CALLABLES_FILTER);
        mURIMatcher.addURI(AUTHORITY, "data/emails/lookup/*", GAL_EMAIL_LOOKUP);
        mURIMatcher.addURI(AUTHORITY, PRIMARY_THUMBNAIL, GAL_PRIMARY_THUMBNAIL);
        mURIMatcher.addURI(AUTHORITY, PRIMARY_PHOTO, GAL_PRIMARY_PHOTO);
        mURIMatcher.addURI(AUTHORITY, MANAGED_THUMBNAIL, GAL_MANAGED_THUMBNAIL);
        mURIMatcher.addURI(AUTHORITY, MANAGED_PHOTO, GAL_MANAGED_PHOTO);
        mSharedPrefs = getContext().getSharedPreferences(CONFIG_NAME, Context.MODE_PRIVATE);
        return true;
    }

    @Override
    public Cursor query(Uri uri, String[] projection, String selection, String[] selectionArgs,
            String sortOrder) {

        final String prefix = getPrefix();
        final int match = mURIMatcher.match(uri);
        switch (match) {
            case GAL_DIRECTORIES: {
                final MatrixCursor cursor = new MatrixCursor(projection);
                final AccountManager am = getContext().getSystemService(AccountManager.class);
                Account[] accounts = am.getAccountsByType(TEST_ACCOUNT_TYPE);
                Log.i(TAG, "Query GAL directories account size: " + accounts.length);
                if (accounts != null) {
                    for (Account account : accounts) {
                        final Object[] row = new Object[projection.length];
                        for (int i = 0; i < projection.length; i++) {
                            final String column = projection[i];
                            if (column.equals(Directory.ACCOUNT_NAME)) {
                                row[i] = account.name;
                            } else if (column.equals(Directory.ACCOUNT_TYPE)) {
                                row[i] = TEST_ACCOUNT_TYPE;
                            } else if (column.equals(Directory.TYPE_RESOURCE_ID)) {
                                row[i] = R.string.directory_resource_id;
                            } else if (column.equals(Directory.DISPLAY_NAME)) {
                                row[i] = prefix + DEFAULT_DISPLAY_NAME;
                            } else if (column.equals(Directory.EXPORT_SUPPORT)) {
                                row[i] = Directory.EXPORT_SUPPORT_SAME_ACCOUNT_ONLY;
                            } else if (column.equals(Directory.SHORTCUT_SUPPORT)) {
                                row[i] = Directory.SHORTCUT_SUPPORT_NONE;
                            }
                        }
                        cursor.addRow(row);
                    }
                }
                return cursor;
            }
            case GAL_FILTER:
            case GAL_CONTACT:
            case GAL_CONTACT_WITH_ID:
            case GAL_EMAIL_FILTER:
            case GAL_PHONE_FILTER:
            case GAL_PHONE_LOOKUP:
            case GAL_CALLABLES_FILTER:
            case GAL_EMAIL_LOOKUP: {
                // This parameter must always exist, and set to the device side app package name.
                final String callerPackage = uri.getQueryParameter(
                        Directory.CALLER_PACKAGE_PARAM_KEY);
                if (!"com.android.cts.managedprofile".equals(callerPackage)) {
                    throw new RuntimeException("Expected caller package name not set: uri="
                            + uri);
                }

                // TODO: Add all CTS tests for these APIs
                final MatrixCursor cursor = new MatrixCursor(projection);
                final Object[] row = new Object[projection.length];
                for (int i = 0; i < projection.length; i++) {
                    String column = projection[i];
                    if (column.equals(Contacts._ID)) {
                        row[i] = -1;
                    } else if (column.equals(Contacts.DISPLAY_NAME)) {
                        row[i] = prefix + DEFAULT_CONTACT_NAME;

                    } else if (column.equals(Contacts.PHOTO_THUMBNAIL_URI)) {
                        row[i] = isManagedProfile() ? MANAGED_THUMBNAIL_URI.toString()
                                : PRIMARY_THUMBNAIL_URI.toString();
                    } else if (column.equals(Contacts.PHOTO_URI)) {
                        row[i] = isManagedProfile() ? MANAGED_PHOTO_URI.toString()
                                : PRIMARY_PHOTO_URI.toString();
                    } else {
                        row[i] = null;
                    }
                }
                cursor.addRow(row);
                return cursor;
            }
        }
        return null;
    }

    @Override
    public String getType(Uri uri) {
        return null;
    }

    @Override
    public int delete(Uri uri, String selection, String[] selectionArgs) {
        throw new UnsupportedOperationException();
    }

    @Override
    public Uri insert(Uri uri, ContentValues values) {
        throw new UnsupportedOperationException();
    }

    @Override
    public int update(Uri uri, ContentValues values, String selection, String[] selectionArgs) {
        throw new UnsupportedOperationException();
    }

    @Override
    public Bundle call(String method, String arg, Bundle extras) {
        // Set custom display name, so primary directory and corp directory will have different
        // display name
        if (method.equals(SET_CUSTOM_PREFIX)) {
            Log.i(TAG, "Set directory name prefix: " + arg);
            mSharedPrefs.edit().putString(SET_CUSTOM_PREFIX, arg).apply();
            // Force update the content in CP2
            final long token = Binder.clearCallingIdentity();
            getContext().getContentResolver().update(Directory.CONTENT_URI, new ContentValues(),
                    null, null);
            Binder.restoreCallingIdentity(token);
        }
        return new Bundle();
    }

    @Override
    public AssetFileDescriptor openAssetFile(final Uri uri, String mode) {
        if (!mode.equals("r")) {
            throw new IllegalArgumentException("mode must be \"r\"");
        }

        final int match = mURIMatcher.match(uri);
        final int resId;
        switch (match) {
            case GAL_PRIMARY_THUMBNAIL:
                resId = isManagedProfile() ? 0 : R.raw.primary_thumbnail;
                break;
            case GAL_PRIMARY_PHOTO:
                resId = isManagedProfile() ? 0 : R.raw.primary_photo;
                break;
            case GAL_MANAGED_THUMBNAIL:
                resId = isManagedProfile() ? R.raw.managed_thumbnail : 0;
                break;
            case GAL_MANAGED_PHOTO:
                resId = isManagedProfile() ? R.raw.managed_photo : 0;
                break;
            default:
                resId = 0;
                break;
        }

        return resId == 0 ? null : getContext().getResources().openRawResourceFd(resId);
    }

    private String getPrefix() {
        return mSharedPrefs.getString(SET_CUSTOM_PREFIX, "");
    }

    private boolean isManagedProfile() {
        return "Managed".equals(getPrefix());
    }
}
