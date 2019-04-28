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
package com.android.providers.blockednumber;

import android.content.Context;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.provider.BlockedNumberContract.BlockedNumbers;

import com.android.internal.annotations.VisibleForTesting;
import com.android.internal.util.Preconditions;

public class BlockedNumberDatabaseHelper {
    private static final int DATABASE_VERSION = 2;

    private static final String DATABASE_NAME = "blockednumbers.db";

    private static final int IDLE_CONNECTION_TIMEOUT_MS = 30000;

    private static BlockedNumberDatabaseHelper sInstance;

    private final Context mContext;

    private final OpenHelper mOpenHelper;

    public interface Tables {
        String BLOCKED_NUMBERS = "blocked";
    }

    private static final class OpenHelper extends SQLiteOpenHelper {
        public OpenHelper(Context context, String name, SQLiteDatabase.CursorFactory factory,
                          int version) {
            super(context, name, factory, version);
            // Memory optimization - close idle connections after 30s of inactivity
            setIdleConnectionTimeout(IDLE_CONNECTION_TIMEOUT_MS);
        }

        @Override
        public void onCreate(SQLiteDatabase db) {
            createTables(db);
        }

        @Override
        public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
            if (oldVersion < 2) {
                db.execSQL("DROP TABLE IF EXISTS blocked");
                createTables(db);
            }
        }

        private void createTables(SQLiteDatabase db) {
            db.execSQL("CREATE TABLE " + Tables.BLOCKED_NUMBERS + " (" +
                    BlockedNumbers.COLUMN_ID + " INTEGER PRIMARY KEY AUTOINCREMENT," +
                    BlockedNumbers.COLUMN_ORIGINAL_NUMBER + " TEXT NOT NULL UNIQUE," +
                    BlockedNumbers.COLUMN_E164_NUMBER + " TEXT" +
                    ")");

            db.execSQL("CREATE INDEX blocked_number_idx_original ON " + Tables.BLOCKED_NUMBERS +
                    " (" + BlockedNumbers.COLUMN_ORIGINAL_NUMBER + ");");
            db.execSQL("CREATE INDEX blocked_number_idx_e164 ON " + Tables.BLOCKED_NUMBERS + " (" +
                    BlockedNumbers.COLUMN_E164_NUMBER +
                    ");");
        }
    }

    @VisibleForTesting
    public static BlockedNumberDatabaseHelper newInstanceForTest(Context context) {
        return new BlockedNumberDatabaseHelper(context, /* instanceIsForTesting =*/ true);
    }

    private BlockedNumberDatabaseHelper(Context context, boolean instanceIsForTesting) {
        Preconditions.checkNotNull(context);
        mContext = context;
        mOpenHelper = new OpenHelper(mContext,
                instanceIsForTesting ? null : DATABASE_NAME, null, DATABASE_VERSION);
    }

    public static synchronized BlockedNumberDatabaseHelper getInstance(Context context) {
        if (sInstance == null) {
            sInstance = new BlockedNumberDatabaseHelper(
                    context,
                    /* instanceIsForTesting = */ false);
        }
        return sInstance;
    }

    public SQLiteDatabase getReadableDatabase() {
        return mOpenHelper.getReadableDatabase();
    }

    public SQLiteDatabase getWritableDatabase() {
        return mOpenHelper.getWritableDatabase();
    }

    public void wipeForTest() {
        getWritableDatabase().execSQL("DELETE FROM " + Tables.BLOCKED_NUMBERS);
    }
}
