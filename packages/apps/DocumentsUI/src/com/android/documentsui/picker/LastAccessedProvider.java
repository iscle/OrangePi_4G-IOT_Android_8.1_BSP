/*
 * Copyright (C) 2013 The Android Open Source Project
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

package com.android.documentsui.picker;

import static com.android.documentsui.base.DocumentInfo.getCursorString;

import android.app.Activity;
import android.content.ContentProvider;
import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.content.UriMatcher;
import android.content.pm.ResolveInfo;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.net.Uri;
import android.os.Bundle;
import android.provider.DocumentsContract;
import android.util.Log;

import com.android.documentsui.base.DocumentStack;
import com.android.documentsui.base.DurableUtils;

import libcore.io.IoUtils;

import com.google.android.collect.Sets;

import java.io.IOException;
import java.util.Set;
import java.util.function.Predicate;

/*
 * Provider used to keep track of the last known directory navigation trail done by the user
 */
public class LastAccessedProvider extends ContentProvider {
    private static final String TAG = "LastAccessedProvider";

    private static final String AUTHORITY = "com.android.documentsui.lastAccessed";

    private static final UriMatcher sMatcher = new UriMatcher(UriMatcher.NO_MATCH);

    private static final int URI_LAST_ACCESSED = 1;

    public static final String METHOD_PURGE = "purge";
    public static final String METHOD_PURGE_PACKAGE = "purgePackage";

    static {
        sMatcher.addURI(AUTHORITY, "lastAccessed/*", URI_LAST_ACCESSED);
    }

    public static final String TABLE_LAST_ACCESSED = "lastAccessed";

    public static class Columns {
        public static final String PACKAGE_NAME = "package_name";
        public static final String STACK = "stack";
        public static final String TIMESTAMP = "timestamp";
        // Indicates handler was an external app, like photos.
        public static final String EXTERNAL = "external";
    }

    public static Uri buildLastAccessed(String packageName) {
        return new Uri.Builder().scheme(ContentResolver.SCHEME_CONTENT)
                .authority(AUTHORITY).appendPath("lastAccessed").appendPath(packageName).build();
    }

    private DatabaseHelper mHelper;

    private static class DatabaseHelper extends SQLiteOpenHelper {
        private static final String DB_NAME = "lastAccess.db";

        // Used for backwards compatibility
        private static final int VERSION_INIT = 1;
        private static final int VERSION_AS_BLOB = 3;
        private static final int VERSION_ADD_EXTERNAL = 4;
        private static final int VERSION_ADD_RECENT_KEY = 5;

        private static final int VERSION_LAST_ACCESS_REFACTOR = 6;

        public DatabaseHelper(Context context) {
            super(context, DB_NAME, null, VERSION_LAST_ACCESS_REFACTOR);
        }

        @Override
        public void onCreate(SQLiteDatabase db) {

            db.execSQL("CREATE TABLE " + TABLE_LAST_ACCESSED + " (" +
                    Columns.PACKAGE_NAME + " TEXT NOT NULL PRIMARY KEY," +
                    Columns.STACK + " BLOB DEFAULT NULL," +
                    Columns.TIMESTAMP + " INTEGER," +
                    Columns.EXTERNAL + " INTEGER NOT NULL DEFAULT 0" +
                    ")");
        }

        @Override
        public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
            Log.w(TAG, "Upgrading database; wiping app data");
            db.execSQL("DROP TABLE IF EXISTS " + TABLE_LAST_ACCESSED);
            onCreate(db);
        }
    }

    /**
     * Rather than concretely depending on LastAccessedProvider, consider using
     * {@link LastAccessedStorage#setLastAccessed(Activity, DocumentStack)}.
     */
    @Deprecated
    static void setLastAccessed(
            ContentResolver resolver, String packageName, DocumentStack stack) {
        final ContentValues values = new ContentValues();
        final byte[] rawStack = DurableUtils.writeToArrayOrNull(stack);
        values.clear();
        values.put(Columns.STACK, rawStack);
        values.put(Columns.EXTERNAL, 0);
        resolver.insert(buildLastAccessed(packageName), values);
    }

    @Override
    public boolean onCreate() {
        mHelper = new DatabaseHelper(getContext());
        return true;
    }

    @Override
    public Cursor query(Uri uri, String[] projection, String selection, String[] selectionArgs,
            String sortOrder) {
        if (sMatcher.match(uri) != URI_LAST_ACCESSED) {
            throw new UnsupportedOperationException("Unsupported Uri " + uri);
        }

        final SQLiteDatabase db = mHelper.getReadableDatabase();
        final String packageName = uri.getPathSegments().get(1);
        return db.query(TABLE_LAST_ACCESSED, projection, Columns.PACKAGE_NAME + "=?",
                        new String[] { packageName }, null, null, sortOrder);
    }

    @Override
    public String getType(Uri uri) {
        return null;
    }

    @Override
    public Uri insert(Uri uri, ContentValues values) {
        if (sMatcher.match(uri) != URI_LAST_ACCESSED) {
            throw new UnsupportedOperationException("Unsupported Uri " + uri);
        }

        final SQLiteDatabase db = mHelper.getWritableDatabase();
        final ContentValues key = new ContentValues();

        values.put(Columns.TIMESTAMP, System.currentTimeMillis());

        final String packageName = uri.getPathSegments().get(1);
        key.put(Columns.PACKAGE_NAME, packageName);

        // Ensure that row exists, then update with changed values
        db.insertWithOnConflict(TABLE_LAST_ACCESSED, null, key, SQLiteDatabase.CONFLICT_IGNORE);
        db.update(TABLE_LAST_ACCESSED, values, Columns.PACKAGE_NAME + "=?",
                        new String[] { packageName });
        return uri;
    }

    @Override
    public int update(Uri uri, ContentValues values, String selection, String[] selectionArgs) {
        throw new UnsupportedOperationException("Unsupported Uri " + uri);
    }

    @Override
    public int delete(Uri uri, String selection, String[] selectionArgs) {
        throw new UnsupportedOperationException("Unsupported Uri " + uri);
    }

    @Override
    public Bundle call(String method, String arg, Bundle extras) {
        if (METHOD_PURGE.equals(method)) {
            // Purge references to unknown authorities
            final Intent intent = new Intent(DocumentsContract.PROVIDER_INTERFACE);
            final Set<String> knownAuth = Sets.newHashSet();
            for (ResolveInfo info : getContext()
                    .getPackageManager().queryIntentContentProviders(intent, 0)) {
                knownAuth.add(info.providerInfo.authority);
            }

            purgeByAuthority(new Predicate<String>() {
                @Override
                public boolean test(String authority) {
                    // Purge unknown authorities
                    return !knownAuth.contains(authority);
                }
            });

            return null;

        } else if (METHOD_PURGE_PACKAGE.equals(method)) {
            // Purge references to authorities in given package
            final Intent intent = new Intent(DocumentsContract.PROVIDER_INTERFACE);
            intent.setPackage(arg);
            final Set<String> packageAuth = Sets.newHashSet();
            for (ResolveInfo info : getContext()
                    .getPackageManager().queryIntentContentProviders(intent, 0)) {
                packageAuth.add(info.providerInfo.authority);
            }

            if (!packageAuth.isEmpty()) {
                purgeByAuthority(new Predicate<String>() {
                    @Override
                    public boolean test(String authority) {
                        // Purge authority matches
                        return packageAuth.contains(authority);
                    }
                });
            }

            return null;

        } else {
            return super.call(method, arg, extras);
        }
    }

    /**
     * Purge all internal data whose authority matches the given
     * {@link Predicate}.
     */
    private void purgeByAuthority(Predicate<String> predicate) {
        final SQLiteDatabase db = mHelper.getWritableDatabase();
        final DocumentStack stack = new DocumentStack();

        Cursor cursor = db.query(TABLE_LAST_ACCESSED, null, null, null, null, null, null);
        try {
            while (cursor.moveToNext()) {
                try {
                    final byte[] rawStack = cursor.getBlob(
                            cursor.getColumnIndex(Columns.STACK));
                    DurableUtils.readFromArray(rawStack, stack);

                    if (stack.getRoot() != null && predicate.test(stack.getRoot().authority)) {
                        final String packageName = getCursorString(
                                cursor, Columns.PACKAGE_NAME);
                        db.delete(TABLE_LAST_ACCESSED, Columns.PACKAGE_NAME + "=?",
                                new String[] { packageName });
                    }
                } catch (IOException ignored) {
                }
            }
        } finally {
            IoUtils.closeQuietly(cursor);
        }
    }
}
