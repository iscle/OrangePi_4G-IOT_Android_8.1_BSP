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

package com.android.tv.tuner;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.content.Context;
import android.content.UriMatcher;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteException;
import android.database.sqlite.SQLiteOpenHelper;
import android.net.Uri;

/**
 * A content provider for storing the preferences. It's used across TV app and USB tuner TV input.
 */
public class TunerPreferenceProvider extends ContentProvider {
    /** The authority of the provider */
    public static final String AUTHORITY = "com.android.tv.tuner.preferences";

    private static final String PATH_PREFERENCES = "preferences";

    private static final int DATABASE_VERSION = 1;
    private static final String DATABASE_NAME = "usbtuner_preferences.db";
    private static final String PREFERENCES_TABLE = "preferences";

    private static final int MATCH_PREFERENCE = 1;
    private static final int MATCH_PREFERENCE_KEY = 2;

    private static final UriMatcher sUriMatcher;

    private DatabaseOpenHelper mDatabaseOpenHelper;

    static {
        sUriMatcher = new UriMatcher(UriMatcher.NO_MATCH);
        sUriMatcher.addURI(AUTHORITY, "preferences", MATCH_PREFERENCE);
        sUriMatcher.addURI(AUTHORITY, "preferences/*", MATCH_PREFERENCE_KEY);
    }

    /**
     * Builds a Uri that points to a specific preference.

     * @param key a key of the preference to point to
     */
    public static Uri buildPreferenceUri(String key) {
        return Preferences.CONTENT_URI.buildUpon().appendPath(key).build();
    }

    /**
     * Columns definitions for the preferences table.
     */
    public interface Preferences {

        /**
         * The content:// style for the preferences table.
         */
        Uri CONTENT_URI = Uri.parse("content://" + AUTHORITY + "/" + PATH_PREFERENCES);

        /**
         * The MIME type of a directory of preferences.
         */
        String CONTENT_TYPE = "vnd.android.cursor.dir/preferences";

        /**
         * The MIME type of a single preference.
         */
        String CONTENT_ITEM_TYPE = "vnd.android.cursor.item/preferences";

        /**
         * The ID of this preference.
         *
         * <p>This is auto-incremented.
         *
         * <p>Type: INTEGER
         */
        String _ID = "_id";

        /**
         * The key of this preference.
         *
         * <p>Should be unique.
         *
         * <p>Type: TEXT
         */
        String COLUMN_KEY = "key";

        /**
         * The value of this preference.
         *
         * <p>Type: TEXT
         */
        String COLUMN_VALUE = "value";
    }

    private static class DatabaseOpenHelper extends SQLiteOpenHelper {
        public DatabaseOpenHelper(Context context) {
            super(context, DATABASE_NAME, null, DATABASE_VERSION);
        }

        @Override
        public void onCreate(SQLiteDatabase db) {
            db.execSQL("CREATE TABLE " + PREFERENCES_TABLE + " ("
                    + Preferences._ID + " INTEGER PRIMARY KEY AUTOINCREMENT,"
                    + Preferences.COLUMN_KEY + " TEXT NOT NULL,"
                    + Preferences.COLUMN_VALUE + " TEXT);");
        }

        @Override
        public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
            // No-op
        }
    }

    @Override
    public boolean onCreate() {
        mDatabaseOpenHelper = new DatabaseOpenHelper(getContext());
        return true;
    }

    @Override
    public Cursor query(Uri uri, String[] projection, String selection, String[] selectionArgs,
            String sortOrder) {
        int match = sUriMatcher.match(uri);
        if (match != MATCH_PREFERENCE && match != MATCH_PREFERENCE_KEY) {
            throw new UnsupportedOperationException();
        }
        SQLiteDatabase db = mDatabaseOpenHelper.getReadableDatabase();
        Cursor cursor = db.query(PREFERENCES_TABLE, projection, selection, selectionArgs,
                null, null, sortOrder);
        cursor.setNotificationUri(getContext().getContentResolver(), uri);
        return cursor;
    }

    @Override
    public String getType(Uri uri) {
        switch (sUriMatcher.match(uri)) {
            case MATCH_PREFERENCE:
                return Preferences.CONTENT_TYPE;
            case MATCH_PREFERENCE_KEY:
                return Preferences.CONTENT_ITEM_TYPE;
        }
        throw new IllegalArgumentException("Unknown URI " + uri);
    }

    /**
     * Inserts a preference row into the preference table.
     *
     * If a key is already exists in the table, it removes the old row and inserts a new row.
     *
     * @param uri the URL of the table to insert into
     * @param values the initial values for the newly inserted row
     * @return the URL of the newly created row
     */
    @Override
    public Uri insert(Uri uri, ContentValues values) {
        if (sUriMatcher.match(uri) != MATCH_PREFERENCE) {
            throw new UnsupportedOperationException();
        }
        return insertRow(uri, values);
    }

    private Uri insertRow(Uri uri, ContentValues values) {
        SQLiteDatabase db = mDatabaseOpenHelper.getWritableDatabase();

        // Remove the old row.
        db.delete(PREFERENCES_TABLE, Preferences.COLUMN_KEY + " like ?",
                new String[]{values.getAsString(Preferences.COLUMN_KEY)});

        long rowId = db.insert(PREFERENCES_TABLE, null, values);
        if (rowId > 0) {
            Uri rowUri = buildPreferenceUri(values.getAsString(Preferences.COLUMN_KEY));
            getContext().getContentResolver().notifyChange(rowUri, null);
            return rowUri;
        }

        throw new SQLiteException("Failed to insert row into " + uri);
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
