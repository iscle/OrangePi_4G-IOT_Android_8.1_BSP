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

package com.android.tv.dvr.provider;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.database.sqlite.SQLiteQueryBuilder;
import android.database.sqlite.SQLiteStatement;
import android.provider.BaseColumns;
import android.text.TextUtils;
import android.util.Log;

import com.android.tv.dvr.data.ScheduledRecording;
import com.android.tv.dvr.data.SeriesRecording;
import com.android.tv.dvr.provider.DvrContract.Schedules;
import com.android.tv.dvr.provider.DvrContract.SeriesRecordings;

/**
 * A data class for one recorded contents.
 */
public class DvrDatabaseHelper extends SQLiteOpenHelper {
    private static final String TAG = "DvrDatabaseHelper";
    private static final boolean DEBUG = true;

    private static final int DATABASE_VERSION = 17;
    private static final String DB_NAME = "dvr.db";

    private static final String SQL_CREATE_SCHEDULES =
            "CREATE TABLE " + Schedules.TABLE_NAME + "("
                    + Schedules._ID + " INTEGER PRIMARY KEY AUTOINCREMENT,"
                    + Schedules.COLUMN_PRIORITY + " INTEGER DEFAULT "
                            + ScheduledRecording.DEFAULT_PRIORITY + ","
                    + Schedules.COLUMN_TYPE + " TEXT NOT NULL,"
                    + Schedules.COLUMN_INPUT_ID + " TEXT NOT NULL,"
                    + Schedules.COLUMN_CHANNEL_ID + " INTEGER NOT NULL,"
                    + Schedules.COLUMN_PROGRAM_ID + " INTEGER,"
                    + Schedules.COLUMN_PROGRAM_TITLE + " TEXT,"
                    + Schedules.COLUMN_START_TIME_UTC_MILLIS + " INTEGER NOT NULL,"
                    + Schedules.COLUMN_END_TIME_UTC_MILLIS + " INTEGER NOT NULL,"
                    + Schedules.COLUMN_SEASON_NUMBER + " TEXT,"
                    + Schedules.COLUMN_EPISODE_NUMBER + " TEXT,"
                    + Schedules.COLUMN_EPISODE_TITLE + " TEXT,"
                    + Schedules.COLUMN_PROGRAM_DESCRIPTION + " TEXT,"
                    + Schedules.COLUMN_PROGRAM_LONG_DESCRIPTION + " TEXT,"
                    + Schedules.COLUMN_PROGRAM_POST_ART_URI + " TEXT,"
                    + Schedules.COLUMN_PROGRAM_THUMBNAIL_URI + " TEXT,"
                    + Schedules.COLUMN_STATE + " TEXT NOT NULL,"
                    + Schedules.COLUMN_SERIES_RECORDING_ID + " INTEGER,"
                    + "FOREIGN KEY(" + Schedules.COLUMN_SERIES_RECORDING_ID + ") "
                    + "REFERENCES " + SeriesRecordings.TABLE_NAME
                            + "(" + SeriesRecordings._ID + ") "
                    + "ON UPDATE CASCADE ON DELETE SET NULL);";

    private static final String SQL_DROP_SCHEDULES = "DROP TABLE IF EXISTS " + Schedules.TABLE_NAME;

    private static final String SQL_CREATE_SERIES_RECORDINGS =
            "CREATE TABLE " + SeriesRecordings.TABLE_NAME + "("
                    + SeriesRecordings._ID + " INTEGER PRIMARY KEY AUTOINCREMENT,"
                    + SeriesRecordings.COLUMN_PRIORITY + " INTEGER DEFAULT "
                            + SeriesRecording.DEFAULT_PRIORITY + ","
                    + SeriesRecordings.COLUMN_TITLE + " TEXT NOT NULL,"
                    + SeriesRecordings.COLUMN_SHORT_DESCRIPTION + " TEXT,"
                    + SeriesRecordings.COLUMN_LONG_DESCRIPTION + " TEXT,"
                    + SeriesRecordings.COLUMN_INPUT_ID + " TEXT NOT NULL,"
                    + SeriesRecordings.COLUMN_CHANNEL_ID + " INTEGER NOT NULL,"
                    + SeriesRecordings.COLUMN_SERIES_ID + " TEXT NOT NULL,"
                    + SeriesRecordings.COLUMN_START_FROM_SEASON + " INTEGER DEFAULT "
                            + SeriesRecordings.THE_BEGINNING + ","
                    + SeriesRecordings.COLUMN_START_FROM_EPISODE + " INTEGER DEFAULT "
                            + SeriesRecordings.THE_BEGINNING + ","
                    + SeriesRecordings.COLUMN_CHANNEL_OPTION + " TEXT DEFAULT "
                            + SeriesRecordings.OPTION_CHANNEL_ONE + ","
                    + SeriesRecordings.COLUMN_CANONICAL_GENRE + " TEXT,"
                    + SeriesRecordings.COLUMN_POSTER_URI + " TEXT,"
                    + SeriesRecordings.COLUMN_PHOTO_URI + " TEXT,"
                    + SeriesRecordings.COLUMN_STATE + " TEXT)";

    private static final String SQL_DROP_SERIES_RECORDINGS = "DROP TABLE IF EXISTS " +
            SeriesRecordings.TABLE_NAME;

    private static final int SQL_DATA_TYPE_LONG = 0;
    private static final int SQL_DATA_TYPE_INT = 1;
    private static final int SQL_DATA_TYPE_STRING = 2;

    private static final ColumnInfo[] COLUMNS_SCHEDULES = new ColumnInfo[] {
            new ColumnInfo(Schedules._ID, SQL_DATA_TYPE_LONG),
            new ColumnInfo(Schedules.COLUMN_PRIORITY, SQL_DATA_TYPE_LONG),
            new ColumnInfo(Schedules.COLUMN_TYPE, SQL_DATA_TYPE_STRING),
            new ColumnInfo(Schedules.COLUMN_INPUT_ID, SQL_DATA_TYPE_STRING),
            new ColumnInfo(Schedules.COLUMN_CHANNEL_ID, SQL_DATA_TYPE_LONG),
            new ColumnInfo(Schedules.COLUMN_PROGRAM_ID, SQL_DATA_TYPE_LONG),
            new ColumnInfo(Schedules.COLUMN_PROGRAM_TITLE, SQL_DATA_TYPE_STRING),
            new ColumnInfo(Schedules.COLUMN_START_TIME_UTC_MILLIS, SQL_DATA_TYPE_LONG),
            new ColumnInfo(Schedules.COLUMN_END_TIME_UTC_MILLIS, SQL_DATA_TYPE_LONG),
            new ColumnInfo(Schedules.COLUMN_SEASON_NUMBER, SQL_DATA_TYPE_STRING),
            new ColumnInfo(Schedules.COLUMN_EPISODE_NUMBER, SQL_DATA_TYPE_STRING),
            new ColumnInfo(Schedules.COLUMN_EPISODE_TITLE, SQL_DATA_TYPE_STRING),
            new ColumnInfo(Schedules.COLUMN_PROGRAM_DESCRIPTION, SQL_DATA_TYPE_STRING),
            new ColumnInfo(Schedules.COLUMN_PROGRAM_LONG_DESCRIPTION, SQL_DATA_TYPE_STRING),
            new ColumnInfo(Schedules.COLUMN_PROGRAM_POST_ART_URI, SQL_DATA_TYPE_STRING),
            new ColumnInfo(Schedules.COLUMN_PROGRAM_THUMBNAIL_URI, SQL_DATA_TYPE_STRING),
            new ColumnInfo(Schedules.COLUMN_STATE, SQL_DATA_TYPE_STRING),
            new ColumnInfo(Schedules.COLUMN_SERIES_RECORDING_ID, SQL_DATA_TYPE_LONG)};

    private static final String SQL_INSERT_SCHEDULES =
            buildInsertSql(Schedules.TABLE_NAME, COLUMNS_SCHEDULES);
    private static final String SQL_UPDATE_SCHEDULES =
            buildUpdateSql(Schedules.TABLE_NAME, COLUMNS_SCHEDULES);
    private static final String SQL_DELETE_SCHEDULES = buildDeleteSql(Schedules.TABLE_NAME);

    private static final ColumnInfo[] COLUMNS_SERIES_RECORDINGS = new ColumnInfo[] {
            new ColumnInfo(SeriesRecordings._ID, SQL_DATA_TYPE_LONG),
            new ColumnInfo(SeriesRecordings.COLUMN_PRIORITY, SQL_DATA_TYPE_LONG),
            new ColumnInfo(SeriesRecordings.COLUMN_INPUT_ID, SQL_DATA_TYPE_STRING),
            new ColumnInfo(SeriesRecordings.COLUMN_CHANNEL_ID, SQL_DATA_TYPE_LONG),
            new ColumnInfo(SeriesRecordings.COLUMN_SERIES_ID, SQL_DATA_TYPE_STRING),
            new ColumnInfo(SeriesRecordings.COLUMN_TITLE, SQL_DATA_TYPE_STRING),
            new ColumnInfo(SeriesRecordings.COLUMN_SHORT_DESCRIPTION, SQL_DATA_TYPE_STRING),
            new ColumnInfo(SeriesRecordings.COLUMN_LONG_DESCRIPTION, SQL_DATA_TYPE_STRING),
            new ColumnInfo(SeriesRecordings.COLUMN_START_FROM_SEASON, SQL_DATA_TYPE_INT),
            new ColumnInfo(SeriesRecordings.COLUMN_START_FROM_EPISODE, SQL_DATA_TYPE_INT),
            new ColumnInfo(SeriesRecordings.COLUMN_CHANNEL_OPTION, SQL_DATA_TYPE_STRING),
            new ColumnInfo(SeriesRecordings.COLUMN_CANONICAL_GENRE, SQL_DATA_TYPE_STRING),
            new ColumnInfo(SeriesRecordings.COLUMN_POSTER_URI, SQL_DATA_TYPE_STRING),
            new ColumnInfo(SeriesRecordings.COLUMN_PHOTO_URI, SQL_DATA_TYPE_STRING),
            new ColumnInfo(SeriesRecordings.COLUMN_STATE, SQL_DATA_TYPE_STRING)};

    private static final String SQL_INSERT_SERIES_RECORDINGS =
            buildInsertSql(SeriesRecordings.TABLE_NAME, COLUMNS_SERIES_RECORDINGS);
    private static final String SQL_UPDATE_SERIES_RECORDINGS =
            buildUpdateSql(SeriesRecordings.TABLE_NAME, COLUMNS_SERIES_RECORDINGS);
    private static final String SQL_DELETE_SERIES_RECORDINGS =
            buildDeleteSql(SeriesRecordings.TABLE_NAME);

    private static String buildInsertSql(String tableName, ColumnInfo[] columns) {
        StringBuilder sb = new StringBuilder();
        sb.append("INSERT INTO ").append(tableName).append(" (");
        boolean appendComma = false;
        for (ColumnInfo columnInfo : columns) {
            if (appendComma) {
                sb.append(",");
            }
            appendComma = true;
            sb.append(columnInfo.name);
        }
        sb.append(") VALUES (?");
        for (int i = 1; i < columns.length; ++i) {
            sb.append(",?");
        }
        sb.append(")");
        return sb.toString();
    }

    private static String buildUpdateSql(String tableName, ColumnInfo[] columns) {
        StringBuilder sb = new StringBuilder();
        sb.append("UPDATE ").append(tableName).append(" SET ");
        boolean appendComma = false;
        for (ColumnInfo columnInfo : columns) {
            if (appendComma) {
                sb.append(",");
            }
            appendComma = true;
            sb.append(columnInfo.name).append("=?");
        }
        sb.append(" WHERE ").append(BaseColumns._ID).append("=?");
        return sb.toString();
    }

    private static String buildDeleteSql(String tableName) {
        return "DELETE FROM " + tableName + " WHERE " + BaseColumns._ID + "=?";
    }
    public DvrDatabaseHelper(Context context) {
        super(context.getApplicationContext(), DB_NAME, null, DATABASE_VERSION);
    }

    @Override
    public void onConfigure(SQLiteDatabase db) {
        db.setForeignKeyConstraintsEnabled(true);
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        if (DEBUG) Log.d(TAG, "Executing SQL: " + SQL_CREATE_SCHEDULES);
        db.execSQL(SQL_CREATE_SCHEDULES);
        if (DEBUG) Log.d(TAG, "Executing SQL: " + SQL_CREATE_SERIES_RECORDINGS);
        db.execSQL(SQL_CREATE_SERIES_RECORDINGS);
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        if (DEBUG) Log.d(TAG, "Executing SQL: " + SQL_DROP_SCHEDULES);
        db.execSQL(SQL_DROP_SCHEDULES);
        if (DEBUG) Log.d(TAG, "Executing SQL: " + SQL_DROP_SERIES_RECORDINGS);
        db.execSQL(SQL_DROP_SERIES_RECORDINGS);
        onCreate(db);
    }

    /**
     * Handles the query request and returns a {@link Cursor}.
     */
    public Cursor query(String tableName, String[] projections) {
        SQLiteDatabase db = getReadableDatabase();
        SQLiteQueryBuilder builder = new SQLiteQueryBuilder();
        builder.setTables(tableName);
        return builder.query(db, projections, null, null, null, null, null);
    }

    /**
     * Inserts schedules.
     */
    public void insertSchedules(ScheduledRecording... scheduledRecordings) {
        SQLiteDatabase db = getWritableDatabase();
        SQLiteStatement statement = db.compileStatement(SQL_INSERT_SCHEDULES);
        db.beginTransaction();
        try {
            for (ScheduledRecording r : scheduledRecordings) {
                statement.clearBindings();
                ContentValues values = ScheduledRecording.toContentValues(r);
                bindColumns(statement, COLUMNS_SCHEDULES, values);
                statement.execute();
            }
            db.setTransactionSuccessful();
        } finally {
            db.endTransaction();
        }
    }

    /**
     * Update schedules.
     */
    public void updateSchedules(ScheduledRecording... scheduledRecordings) {
        SQLiteDatabase db = getWritableDatabase();
        SQLiteStatement statement = db.compileStatement(SQL_UPDATE_SCHEDULES);
        db.beginTransaction();
        try {
            for (ScheduledRecording r : scheduledRecordings) {
                statement.clearBindings();
                ContentValues values = ScheduledRecording.toContentValues(r);
                bindColumns(statement, COLUMNS_SCHEDULES, values);
                statement.bindLong(COLUMNS_SCHEDULES.length + 1, r.getId());
                statement.execute();
            }
            db.setTransactionSuccessful();
        } finally {
            db.endTransaction();
        }
    }

    /**
     * Delete schedules.
     */
    public void deleteSchedules(ScheduledRecording... scheduledRecordings) {
        SQLiteDatabase db = getWritableDatabase();
        SQLiteStatement statement = db.compileStatement(SQL_DELETE_SCHEDULES);
        db.beginTransaction();
        try {
            for (ScheduledRecording r : scheduledRecordings) {
                statement.clearBindings();
                statement.bindLong(1, r.getId());
                statement.execute();
            }
            db.setTransactionSuccessful();
        } finally {
            db.endTransaction();
        }
    }

    /**
     * Inserts series recordings.
     */
    public void insertSeriesRecordings(SeriesRecording... seriesRecordings) {
        SQLiteDatabase db = getWritableDatabase();
        SQLiteStatement statement = db.compileStatement(SQL_INSERT_SERIES_RECORDINGS);
        db.beginTransaction();
        try {
            for (SeriesRecording r : seriesRecordings) {
                statement.clearBindings();
                ContentValues values = SeriesRecording.toContentValues(r);
                bindColumns(statement, COLUMNS_SERIES_RECORDINGS, values);
                statement.execute();
            }
            db.setTransactionSuccessful();
        } finally {
            db.endTransaction();
        }
    }

    /**
     * Update series recordings.
     */
    public void updateSeriesRecordings(SeriesRecording... seriesRecordings) {
        SQLiteDatabase db = getWritableDatabase();
        SQLiteStatement statement = db.compileStatement(SQL_UPDATE_SERIES_RECORDINGS);
        db.beginTransaction();
        try {
            for (SeriesRecording r : seriesRecordings) {
                statement.clearBindings();
                ContentValues values = SeriesRecording.toContentValues(r);
                bindColumns(statement, COLUMNS_SERIES_RECORDINGS, values);
                statement.bindLong(COLUMNS_SERIES_RECORDINGS.length + 1, r.getId());
                statement.execute();
            }
            db.setTransactionSuccessful();
        } finally {
            db.endTransaction();
        }
    }

    /**
     * Delete series recordings.
     */
    public void deleteSeriesRecordings(SeriesRecording... seriesRecordings) {
        SQLiteDatabase db = getWritableDatabase();
        SQLiteStatement statement = db.compileStatement(SQL_DELETE_SERIES_RECORDINGS);
        db.beginTransaction();
        try {
            for (SeriesRecording r : seriesRecordings) {
                statement.clearBindings();
                statement.bindLong(1, r.getId());
                statement.execute();
            }
            db.setTransactionSuccessful();
        } finally {
            db.endTransaction();
        }
    }

    private void bindColumns(SQLiteStatement statement, ColumnInfo[] columns,
            ContentValues values) {
        for (int i = 0; i < columns.length; ++i) {
            ColumnInfo columnInfo = columns[i];
            Object value = values.get(columnInfo.name);
            switch (columnInfo.type) {
                case SQL_DATA_TYPE_LONG:
                    if (value == null) {
                        statement.bindNull(i + 1);
                    } else {
                        statement.bindLong(i + 1, (Long) value);
                    }
                    break;
                case SQL_DATA_TYPE_INT:
                    if (value == null) {
                        statement.bindNull(i + 1);
                    } else {
                        statement.bindLong(i + 1, (Integer) value);
                    }
                    break;
                case SQL_DATA_TYPE_STRING: {
                    if (TextUtils.isEmpty((String) value)) {
                        statement.bindNull(i + 1);
                    } else {
                        statement.bindString(i + 1, (String) value);
                    }
                    break;
                }
            }
        }
    }

    private static class ColumnInfo {
        final String name;
        final int type;

        ColumnInfo(String name, int type) {
            this.name = name;
            this.type = type;
        }
    }
}
