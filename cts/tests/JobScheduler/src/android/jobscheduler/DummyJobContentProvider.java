/*
 * Copyright (C) 2016 The Android Open Source Project.
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

package android.jobscheduler;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.content.Context;
import android.content.UriMatcher;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.net.Uri;

/**
 * Stub content provider used for generating content change reports
 */
public class DummyJobContentProvider extends ContentProvider {
    private static final String DATABASE_NAME = "dummy.db";
    private static final String NAME_VALUE_TABLE = "name_value";

    private DatabaseHelper mDbHelper;
    private static UriMatcher sMatcher = new UriMatcher(UriMatcher.NO_MATCH);

    private static final int MATCH_NAME_VALUE      = 1;

    public static final String AUTHORITY = "android.jobscheduler.dummyprovider";
    public static final Uri CONTENT_URI = Uri.parse("content://" + AUTHORITY);

    public static final String _ID   = "_id";
    public static final String NAME  = "name";
    public static final String VALUE = "value";

    static {
        sMatcher.addURI(AUTHORITY, null, MATCH_NAME_VALUE);
    }

    /*
     * (non-Javadoc)
     * @see android.content.ContentProvider#onCreate()
     */
    @Override
    public boolean onCreate() {
        mDbHelper = new DatabaseHelper(getContext());
        return true;
    }

    private class DatabaseHelper extends SQLiteOpenHelper {
        private static final int DATABASE_VERSION = 1;

        DatabaseHelper(Context context) {
            super(context, DATABASE_NAME, null, DATABASE_VERSION);
        }

        @Override
        public void onCreate(SQLiteDatabase db) {
            // create an empty name_value table
            db.execSQL("CREATE TABLE " + NAME_VALUE_TABLE + " (" + _ID + " INTEGER PRIMARY KEY,"
                    + NAME + " TEXT," + VALUE + " TEXT"+ ");");
        }

        @Override
        public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        }
    }

    /*
     * (non-Javadoc)
     * @see android.content.ContentProvider#insert(android.net.Uri,
     * android.content.ContentValues)
     */
    @Override
    public Uri insert(Uri uri, ContentValues values) {
        String tbName = getTableName(uri);
        if (tbName == null) {
            return null;
        }
        SQLiteDatabase db = mDbHelper.getWritableDatabase();
        db.insert(tbName, VALUE, values);
        getContext().getContentResolver().notifyChange(uri, null);
        return uri;
    }

    /*
     * (non-Javadoc)
     * @see android.content.ContentProvider#query(android.net.Uri,
     * java.lang.String[], java.lang.String, java.lang.String[],
     * java.lang.String)
     */
    @Override
    public Cursor query(Uri uri, String[] projection, String selection, String[] selectionArgs,
            String sortOrder) {
        String tbName = getTableName(uri);
        if (tbName == null) {
            return null;
        }
        SQLiteDatabase db = mDbHelper.getReadableDatabase();
        Cursor c = db.query(tbName, projection, selection, selectionArgs, null, null, sortOrder);
        c.setNotificationUri(getContext().getContentResolver(), uri);
        return c;
    }

    private String getTableName(Uri uri) {
        switch (sMatcher.match(uri)) {
            case MATCH_NAME_VALUE:
                return NAME_VALUE_TABLE;
            default:
                throw new UnsupportedOperationException();
        }
    }

    /*
     * (non-Javadoc)
     * @see android.content.ContentProvider#update(android.net.Uri,
     * android.content.ContentValues, java.lang.String, java.lang.String[])
     */
    @Override
    public int update(Uri uri, ContentValues values, String selection, String[] selectionArgs) {
        String tbName = getTableName(uri);
        if (tbName == null) {
            return 0;
        }
        SQLiteDatabase db = mDbHelper.getWritableDatabase();
        int count = db.update(tbName, values, selection, selectionArgs);
        getContext().getContentResolver().notifyChange(uri, null);
        return count;
    }

    /*
     * (non-Javadoc)
     * @see android.content.ContentProvider#delete(android.net.Uri,
     * java.lang.String, java.lang.String[])
     */
    @Override
    public int delete(Uri uri, String selection, String[] selectionArgs) {
        String tbName = getTableName(uri);
        if (tbName == null) {
            return 0;
        }
        SQLiteDatabase db = mDbHelper.getWritableDatabase();
        int count = db.delete(tbName, selection, selectionArgs);
        getContext().getContentResolver().notifyChange(uri, null);
        return count;
    }

    /*
     * (non-Javadoc)
     * @see android.content.ContentProvider#getType(android.net.Uri)
     */
    @Override
    public String getType(Uri uri) {
        return null;
    }
}
