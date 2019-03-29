/*
 * Copyright (C) 2017 The Android Open Source Project
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

package android.inputmethodservice.cts.db;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.support.annotation.NonNull;

import java.util.List;

/**
 * Abstraction of SQLite database.
 */
public abstract class Database {

    private final SQLiteOpenHelper mHelper;

    public Database(final Context context, final String name, final int version) {
        mHelper = new SQLiteOpenHelper(context, name, null /* factory */, version) {
            @Override
            public void onCreate(final SQLiteDatabase db) {
                db.beginTransaction();
                try {
                    for (final Table table : getTables()) {
                        db.execSQL(table.createTableSql());
                    }
                    db.setTransactionSuccessful();
                } finally {
                    db.endTransaction();
                }
            }

            @Override
            public void onUpgrade(final SQLiteDatabase db, final int oldVersion,
                    final int newVersion) {
                // nothing to do so far.
            }
        };
    }

    @NonNull
    protected abstract List<Table> getTables();

    public Cursor query(final String table, final String[] projection, final String selection,
            final String[] selectionArgs, final String orderBy) {
        return mHelper.getReadableDatabase()
                .query(table, projection, selection, selectionArgs, null /* groupBy */,
                        null /* having */, orderBy);
    }

    public long insert(final String table, final ContentValues values) {
        return mHelper.getWritableDatabase().insert(table, null /* nullColumnHack */, values);
    }

    public int delete(final String table, final String selection, final String[] selectionArgs) {
        return mHelper.getWritableDatabase().delete(table, selection, selectionArgs);
    }

    public int update(final String table, final ContentValues values, final String selection,
            final String[] selectionArgs) {
        return mHelper.getWritableDatabase().update(table, values, selection, selectionArgs);
    }

    public void close() {
        mHelper.close();
    }
}
