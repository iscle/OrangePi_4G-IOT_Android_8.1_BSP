/*
 * Copyright (C) 2012 Google Inc.
 * Licensed to The Android Open Source Project.
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

package com.android.mail.providers;

import android.app.SearchManager;
import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.support.annotation.Nullable;

import com.android.mail.R;

import java.util.ArrayList;

public class SearchRecentSuggestionsProvider {
    /*
     * String used to delimit different parts of a query.
     */
    public static final String QUERY_TOKEN_SEPARATOR = " ";

    // general database configuration and tables
    private SQLiteOpenHelper mOpenHelper;
    private static final String DATABASE_NAME = "suggestions.db";
    private static final String SUGGESTIONS_TABLE = "suggestions";

    private static final String QUERY =
            " SELECT _id" +
            "   , display1 AS " + SearchManager.SUGGEST_COLUMN_TEXT_1 +
            "   , ? || query AS " + SearchManager.SUGGEST_COLUMN_QUERY +
            "   , ? AS " + SearchManager.SUGGEST_COLUMN_ICON_1 +
            " FROM " + SUGGESTIONS_TABLE +
            " WHERE display1 LIKE ?" +
            " ORDER BY date DESC";

    // Table of database versions.  Don't forget to update!
    // NOTE:  These version values are shifted left 8 bits (x 256) in order to create space for
    // a small set of mode bitflags in the version int.
    //
    // 1      original implementation with queries, and 1 or 2 display columns
    // 1->2   added UNIQUE constraint to display1 column
    // 2->3   <redacted> being dumb and accidentally upgraded, this should be ignored.
    private static final int DATABASE_VERSION = 3 * 256;

    private static final int DATABASE_VERSION_2 = 2 * 256;
    private static final int DATABASE_VERSION_3 = 3 * 256;

    private String mHistoricalIcon;

    protected final Context mContext;
    private ArrayList<String> mFullQueryTerms;

    private final Object mDbLock = new Object();
    private boolean mClosed;

    public SearchRecentSuggestionsProvider(Context context) {
        mContext = context;
        mOpenHelper = new DatabaseHelper(mContext, DATABASE_VERSION);

        // The URI of the icon that we will include on every suggestion here.
        mHistoricalIcon = ContentResolver.SCHEME_ANDROID_RESOURCE + "://"
                + mContext.getPackageName() + "/" + R.drawable.ic_history_24dp;
    }

    public void cleanup() {
        synchronized (mDbLock) {
            mOpenHelper.close();
            mClosed = true;
        }
    }

    /**
     * Builds the database.  This version has extra support for using the version field
     * as a mode flags field, and configures the database columns depending on the mode bits
     * (features) requested by the extending class.
     *
     * @hide
     */
    private static class DatabaseHelper extends SQLiteOpenHelper {
        public DatabaseHelper(Context context, int newVersion) {
            super(context, DATABASE_NAME, null, newVersion);
        }

        @Override
        public void onCreate(SQLiteDatabase db) {
            final String create = "CREATE TABLE suggestions (" +
                    "_id INTEGER PRIMARY KEY" +
                    ",display1 TEXT UNIQUE ON CONFLICT REPLACE" +
                    ",query TEXT" +
                    ",date LONG" +
                    ");";
            db.execSQL(create);
        }

        @Override
        public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
            // When checking the old version clear the last 8 bits
            oldVersion = oldVersion & ~0xff;
            newVersion = newVersion & ~0xff;
            if (oldVersion == DATABASE_VERSION_2 && newVersion == DATABASE_VERSION_3) {
                // Oops, didn't mean to upgrade this database. Ignore this upgrade.
                return;
            }
            db.execSQL("DROP TABLE IF EXISTS suggestions");
            onCreate(db);
        }
    }

    /**
     * Set the other query terms to be included in the user's query.
     * These are in addition to what is being looked up for suggestions.
     * @param terms
     */
    public void setFullQueryTerms(ArrayList<String> terms) {
        mFullQueryTerms = terms;
    }

    private @Nullable SQLiteDatabase getDatabase(boolean readOnly) {
        synchronized (mDbLock) {
            if (!mClosed) {
                return readOnly ? mOpenHelper.getReadableDatabase() :
                        mOpenHelper.getWritableDatabase();
            }
        }
        return null;
    }

    public Cursor query(String query) {
        final SQLiteDatabase db = getDatabase(true /* readOnly */);
        if (db != null) {
            final StringBuilder builder = new StringBuilder();
            if (mFullQueryTerms != null) {
                for (String token : mFullQueryTerms) {
                    builder.append(token).append(QUERY_TOKEN_SEPARATOR);
                }
            }

            final String[] args = new String[] {
                    builder.toString(), mHistoricalIcon, "%" + query + "%" };

            try {
                // db could have been closed due to cleanup, simply don't do anything.
                return db.rawQuery(QUERY, args);
            } catch (IllegalStateException e) {}
        }
        return null;
    }

    /**
     * We are going to keep track of recent suggestions ourselves and not depend on the framework.
     * Note that this writes to disk. DO NOT CALL FROM MAIN THREAD.
     */
    public void saveRecentQuery(String query) {
        final SQLiteDatabase db = getDatabase(false /* readOnly */);
        if (db != null) {
            ContentValues values = new ContentValues(3);
            values.put("display1", query);
            values.put("query", query);
            values.put("date", System.currentTimeMillis());
            // Note:  This table has on-conflict-replace semantics, so insert may actually replace
            try {
                // db could have been closed due to cleanup, simply don't do anything.
                db.insert(SUGGESTIONS_TABLE, null, values);
            } catch (IllegalStateException e) {}
        }
    }

    public void clearHistory() {
        final SQLiteDatabase db = getDatabase(false /* readOnly */);
        if (db != null) {
            try {
                // db could have been closed due to cleanup, simply don't do anything.
                db.delete(SUGGESTIONS_TABLE, null, null);
            } catch (IllegalStateException e) {}
        }
    }
}