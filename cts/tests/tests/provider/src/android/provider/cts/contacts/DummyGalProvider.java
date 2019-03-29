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
 * limitations under the License
 */

package android.provider.cts.contacts;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.content.UriMatcher;
import android.database.Cursor;
import android.database.MatrixCursor;
import android.net.Uri;
import android.provider.ContactsContract;
import android.provider.ContactsContract.Contacts;
import android.provider.ContactsContract.Directory;
import android.provider.ContactsContract.RawContacts;
import android.provider.cts.contacts.account.StaticAccountAuthenticator;
import android.text.TextUtils;
import android.util.Log;

import org.json.JSONException;
import org.json.JSONObject;

/**
 * GAL provider for CTS.
 */
public class DummyGalProvider extends ContentProvider {
    private static final String TAG = "DummyGalProvider";

    public static final String AUTHORITY = "android.provider.cts.contacts.dgp";

    public static final String ACCOUNT_NAME = "dummygal";
    public static final String ACCOUNT_TYPE = StaticAccountAuthenticator.TYPE;

    public static final String DISPLAY_NAME = "dummy-gal";

    public static final String ERROR_MESSAGE_KEY = "error";
    public static final String QUERY_KEY = "query";
    public static final String CALLER_PACKAGE_NAME_KEY = "package_name";
    public static final String LIMIT_KEY = "limit";


    private static final int GAL_DIRECTORIES = 0;
    private static final int GAL_FILTER = 1;
    private static final int GAL_CONTACT = 2;
    private static final int GAL_CONTACT_WITH_ID = 3;
    private static final int GAL_EMAIL_FILTER = 4;
    private static final int GAL_PHONE_FILTER = 5;
    private static final int GAL_PHONE_LOOKUP = 6;

    private static final UriMatcher sURIMatcher = new UriMatcher(UriMatcher.NO_MATCH);

    static {
        sURIMatcher.addURI(AUTHORITY, "directories", GAL_DIRECTORIES);
        sURIMatcher.addURI(AUTHORITY, "contacts/filter/*", GAL_FILTER);
        // The following URIs are not supported by this class.
//        sURIMatcher.addURI(AUTHORITY, "contacts/lookup/*/entities", GAL_CONTACT);
//        sURIMatcher.addURI(AUTHORITY, "contacts/lookup/*/#/entities", GAL_CONTACT_WITH_ID);
//        sURIMatcher.addURI(AUTHORITY, "data/emails/filter/*", GAL_EMAIL_FILTER);
//        sURIMatcher.addURI(AUTHORITY, "data/phones/filter/*", GAL_PHONE_FILTER);
//        sURIMatcher.addURI(AUTHORITY, "phone_lookup/*", GAL_PHONE_LOOKUP);
    }

    @Override
    public boolean onCreate() {
        return true;
    }

    @Override
    public Cursor query(Uri uri, String[] projection, String selection, String[] selectionArgs,
            String sortOrder) {
        Log.d(TAG, "uri: " + uri);
        final int match = sURIMatcher.match(uri);

        switch (match) {
            case GAL_DIRECTORIES: {
                return handleDirectories(projection);
            }
            case GAL_FILTER: {
                try {
                    return handleFilter(uri, projection);
                } catch (JSONException e) {
                    Log.e(TAG, "Caught exception", e);
                    return null;
                }
            }
        }
        return null;
    }

    @Override
    public String getType(Uri uri) {
        return null;
    }

    @Override
    public Uri insert(Uri uri, ContentValues values) {
        return null;
    }

    @Override
    public int delete(Uri uri, String selection, String[] selectionArgs) {
        return 0;
    }

    @Override
    public int update(Uri uri, ContentValues values, String selection, String[] selectionArgs) {
        return 0;
    }

    /**
     * Build a cursor containing the directory information.
     *
     * See com.android.providers.contacts.ContactDirectoryManager.
     */
    private Cursor handleDirectories(String[] projection) {
        MatrixCursor cursor;
        Object[] row;
        cursor = new MatrixCursor(projection);

        row = new Object[projection.length];

        for (int i = 0; i < projection.length; i++) {
            String column = projection[i];
            if (column.equals(Directory.ACCOUNT_NAME)) {
                row[i] = ACCOUNT_NAME;
            } else if (column.equals(Directory.ACCOUNT_TYPE)) {
                row[i] = ACCOUNT_TYPE;
            } else if (column.equals(Directory.TYPE_RESOURCE_ID)) {
                row[i] = "";
            } else if (column.equals(Directory.DISPLAY_NAME)) {
                row[i] = DISPLAY_NAME;
            } else if (column.equals(Directory.EXPORT_SUPPORT)) {
                row[i] = Directory.EXPORT_SUPPORT_NONE;
            } else if (column.equals(Directory.SHORTCUT_SUPPORT)) {
                row[i] = Directory.SHORTCUT_SUPPORT_NONE;
            } else if (column.equals(Directory.PHOTO_SUPPORT)) {
                row[i] = Directory.PHOTO_SUPPORT_NONE;
            }
        }
        cursor.addRow(row);
        return cursor;
    }

    /**
     * Build a cursor to return from the {@link #GAL_FILTER} URI, which returns the following
     * information in the display_name column as json.
     *
     * - It checks whether the incoming account name/type match the values returned by {@link
     * #handleDirectories(String[])}, and if not, set a error message to the result.
     * - If above check succeeds, then sets the query parameters in the result.  The caller
     * checks the returned values.
     */
    private Cursor handleFilter(Uri uri, String[] projection) throws JSONException {
        final String accountName = uri.getQueryParameter(RawContacts.ACCOUNT_NAME);
        final String accountType = uri.getQueryParameter(RawContacts.ACCOUNT_TYPE);
        final String limit = uri.getQueryParameter(ContactsContract.LIMIT_PARAM_KEY);
        final String callerPackage = uri.getQueryParameter(Directory.CALLER_PACKAGE_PARAM_KEY);

        final JSONObject result = new JSONObject();

        final StringBuilder error = new StringBuilder();
        if (!ACCOUNT_NAME.equals(accountName)) {
            error.append(String.format("Account name expected=%s but was %s\n",
                    ACCOUNT_NAME, accountName));
        }
        if (!ACCOUNT_TYPE.equals(accountType)) {
            error.append(String.format("Account type expected=%s but was %s\n",
                    ACCOUNT_TYPE, accountType));
        }

        if (error.length() > 0) {
            result.put(ERROR_MESSAGE_KEY, error.toString());
        } else {
            if (!TextUtils.isEmpty(limit)) {
                result.put(LIMIT_KEY, limit);
            }
            if (!TextUtils.isEmpty(callerPackage)) {
                result.put(CALLER_PACKAGE_NAME_KEY, callerPackage);
            }
            result.put(QUERY_KEY, uri.getLastPathSegment());
        }
        if (projection == null) {
            projection = new String[]{Contacts.DISPLAY_NAME};
        }

        final MatrixCursor c = new MatrixCursor(projection);

        Object[] row = new Object[projection.length];
        for (int i = 0; i < projection.length; i++) {
            String column = projection[i];
            if (Contacts.DISPLAY_NAME.equals(column)) {
                row[i] = result.toString();
                continue;
            }
            // All other fields are null.
        }

        c.addRow(row);
        return c;
    }
}
