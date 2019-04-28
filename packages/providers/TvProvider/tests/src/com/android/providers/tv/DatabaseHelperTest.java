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

package com.android.providers.tv;

import android.content.Context;
import android.content.pm.ProviderInfo;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.media.tv.TvContract;
import android.os.Bundle;
import android.provider.Settings;
import android.test.AndroidTestCase;
import android.test.mock.MockContentProvider;
import android.test.mock.MockContentResolver;

public class DatabaseHelperTest extends AndroidTestCase {
    private static final int BASE_DATABASE_VERSION = 23;

    private DatabaseHelperForTesting mDatabaseHelper;
    private MockContentResolver mResolver;
    private TvProviderForTesting mProvider;

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        mResolver = new MockContentResolver();
        mResolver.addProvider(Settings.AUTHORITY, new MockContentProvider() {
            @Override
            public Bundle call(String method, String request, Bundle args) {
                return new Bundle();
            }
        });

        mProvider = new TvProviderForTesting();
        mResolver.addProvider(TvContract.AUTHORITY, mProvider);

        setContext(new MockTvProviderContext(mResolver, getContext()));

        final ProviderInfo info = new ProviderInfo();
        info.authority = TvContract.AUTHORITY;
        mProvider.attachInfoForTesting(getContext(), info);
        mDatabaseHelper = new DatabaseHelperForTesting(getContext(), BASE_DATABASE_VERSION);
        mProvider.setOpenHelper(mDatabaseHelper);
    }

    @Override
    protected void tearDown() throws Exception {
        mProvider.shutdown();
        super.tearDown();
    }

    public void testUpgradeDatabase() {
        SQLiteDatabase db = mDatabaseHelper.getReadableDatabase();
        assertEquals(BASE_DATABASE_VERSION, db.getVersion());
        mDatabaseHelper.close();

        mProvider.setOpenHelper(
                new DatabaseHelperForTesting(getContext(), TvProvider.DATABASE_VERSION));

        try (Cursor cursor = mResolver.query(
                TvContract.Channels.CONTENT_URI, null, null, null, null)) {
            assertNotNull(cursor);
        }
        try (Cursor cursor = mResolver.query(
                TvContract.Programs.CONTENT_URI, null, null, null, null)) {
            assertNotNull(cursor);
        }
        try (Cursor cursor = mResolver.query(
                TvContract.WatchedPrograms.CONTENT_URI, null, null, null, null)) {
            assertNotNull(cursor);
        }
        try (Cursor cursor = mResolver.query(
                TvContract.RecordedPrograms.CONTENT_URI, null, null, null, null)) {
            assertNotNull(cursor);
        }
        try (Cursor cursor = mResolver.query(
                TvContract.PreviewPrograms.CONTENT_URI, null, null, null, null)) {
            assertNotNull(cursor);
        }
        try (Cursor cursor = mResolver.query(
                TvContract.WatchNextPrograms.CONTENT_URI, null, null, null, null)) {
            assertNotNull(cursor);
        }
    }

    private static class DatabaseHelperForTesting extends TvProvider.DatabaseHelper {
        private static final String DATABASE_NAME ="tvtest.db";

        private DatabaseHelperForTesting(Context context, int version) {
            super(context, DATABASE_NAME, version);
        }

        @Override
        public void onCreate(SQLiteDatabase db) {
            // Set up the database schema for version 23.
            db.execSQL("CREATE TABLE " + TvProvider.CHANNELS_TABLE + " ("
                    + TvContract.Channels._ID + " INTEGER PRIMARY KEY AUTOINCREMENT,"
                    + TvContract.Channels.COLUMN_PACKAGE_NAME + " TEXT NOT NULL,"
                    + TvContract.Channels.COLUMN_INPUT_ID + " TEXT NOT NULL,"
                    + TvContract.Channels.COLUMN_TYPE + " TEXT NOT NULL DEFAULT '" + TvContract
                    .Channels.TYPE_OTHER + "',"
                    + TvContract.Channels.COLUMN_SERVICE_TYPE + " TEXT NOT NULL DEFAULT '"
                    + TvContract.Channels.SERVICE_TYPE_AUDIO_VIDEO + "',"
                    + TvContract.Channels.COLUMN_ORIGINAL_NETWORK_ID
                    + " INTEGER NOT NULL DEFAULT 0,"
                    + TvContract.Channels.COLUMN_TRANSPORT_STREAM_ID
                    + " INTEGER NOT NULL DEFAULT 0,"
                    + TvContract.Channels.COLUMN_SERVICE_ID + " INTEGER NOT NULL DEFAULT 0,"
                    + TvContract.Channels.COLUMN_DISPLAY_NUMBER + " TEXT,"
                    + TvContract.Channels.COLUMN_DISPLAY_NAME + " TEXT,"
                    + TvContract.Channels.COLUMN_NETWORK_AFFILIATION + " TEXT,"
                    + TvContract.Channels.COLUMN_DESCRIPTION + " TEXT,"
                    + TvContract.Channels.COLUMN_VIDEO_FORMAT + " TEXT,"
                    + TvContract.Channels.COLUMN_BROWSABLE + " INTEGER NOT NULL DEFAULT 0,"
                    + TvContract.Channels.COLUMN_SEARCHABLE + " INTEGER NOT NULL DEFAULT 1,"
                    + TvContract.Channels.COLUMN_LOCKED + " INTEGER NOT NULL DEFAULT 0,"
                    + TvContract.Channels.COLUMN_INTERNAL_PROVIDER_DATA + " BLOB,"
                    + TvProvider.CHANNELS_COLUMN_LOGO + " BLOB,"
                    + TvContract.Channels.COLUMN_VERSION_NUMBER + " INTEGER,"
                    // Needed for foreign keys in other tables.
                    + "UNIQUE(" + TvContract.Channels._ID + ","
                    + TvContract.Channels.COLUMN_PACKAGE_NAME + ")"
                    + ");");
            db.execSQL("CREATE TABLE " + TvProvider.PROGRAMS_TABLE + " ("
                    + TvContract.Programs._ID + " INTEGER PRIMARY KEY AUTOINCREMENT,"
                    + TvContract.Programs.COLUMN_PACKAGE_NAME + " TEXT NOT NULL,"
                    + TvContract.Programs.COLUMN_CHANNEL_ID + " INTEGER,"
                    + TvContract.Programs.COLUMN_TITLE + " TEXT,"
                    + TvContract.Programs.COLUMN_SEASON_NUMBER + " TEXT,"
                    + TvContract.Programs.COLUMN_EPISODE_NUMBER + " TEXT,"
                    + TvContract.Programs.COLUMN_EPISODE_TITLE + " TEXT,"
                    + TvContract.Programs.COLUMN_START_TIME_UTC_MILLIS + " INTEGER,"
                    + TvContract.Programs.COLUMN_END_TIME_UTC_MILLIS + " INTEGER,"
                    + TvContract.Programs.COLUMN_BROADCAST_GENRE + " TEXT,"
                    + TvContract.Programs.COLUMN_CANONICAL_GENRE + " TEXT,"
                    + TvContract.Programs.COLUMN_SHORT_DESCRIPTION + " TEXT,"
                    + TvContract.Programs.COLUMN_LONG_DESCRIPTION + " TEXT,"
                    + TvContract.Programs.COLUMN_VIDEO_WIDTH + " INTEGER,"
                    + TvContract.Programs.COLUMN_VIDEO_HEIGHT + " INTEGER,"
                    + TvContract.Programs.COLUMN_AUDIO_LANGUAGE + " TEXT,"
                    + TvContract.Programs.COLUMN_CONTENT_RATING + " TEXT,"
                    + TvContract.Programs.COLUMN_POSTER_ART_URI + " TEXT,"
                    + TvContract.Programs.COLUMN_THUMBNAIL_URI + " TEXT,"
                    + TvContract.Programs.COLUMN_INTERNAL_PROVIDER_DATA + " BLOB,"
                    + TvContract.Programs.COLUMN_VERSION_NUMBER + " INTEGER,"
                    + "FOREIGN KEY("
                    + TvContract.Programs.COLUMN_CHANNEL_ID + ","
                    + TvContract.Programs.COLUMN_PACKAGE_NAME
                    + ") REFERENCES " + TvProvider.CHANNELS_TABLE + "("
                    + TvContract.Channels._ID + "," + TvContract.Channels.COLUMN_PACKAGE_NAME
                    + ") ON UPDATE CASCADE ON DELETE CASCADE"
                    + ");");
            db.execSQL("CREATE INDEX " + TvProvider.PROGRAMS_TABLE_PACKAGE_NAME_INDEX + " ON "
                    + TvProvider.PROGRAMS_TABLE
                    + "(" + TvContract.Programs.COLUMN_PACKAGE_NAME + ");");
            db.execSQL("CREATE INDEX " + TvProvider.PROGRAMS_TABLE_CHANNEL_ID_INDEX + " ON "
                    + TvProvider.PROGRAMS_TABLE
                    + "(" + TvContract.Programs.COLUMN_CHANNEL_ID + ");");
            db.execSQL("CREATE INDEX " + TvProvider.PROGRAMS_TABLE_START_TIME_INDEX + " ON "
                    + TvProvider.PROGRAMS_TABLE
                    + "(" + TvContract.Programs.COLUMN_START_TIME_UTC_MILLIS + ");");
            db.execSQL("CREATE INDEX " + TvProvider.PROGRAMS_TABLE_END_TIME_INDEX + " ON "
                    + TvProvider.PROGRAMS_TABLE
                    + "(" + TvContract.Programs.COLUMN_END_TIME_UTC_MILLIS + ");");
            db.execSQL("CREATE TABLE " + TvProvider.WATCHED_PROGRAMS_TABLE + " ("
                    + TvContract.WatchedPrograms._ID + " INTEGER PRIMARY KEY AUTOINCREMENT,"
                    + TvContract.WatchedPrograms.COLUMN_PACKAGE_NAME + " TEXT NOT NULL,"
                    + TvContract.WatchedPrograms.COLUMN_WATCH_START_TIME_UTC_MILLIS
                    + " INTEGER NOT NULL DEFAULT 0,"
                    + TvContract.WatchedPrograms.COLUMN_WATCH_END_TIME_UTC_MILLIS
                    + " INTEGER NOT NULL DEFAULT 0,"
                    + TvContract.WatchedPrograms.COLUMN_CHANNEL_ID + " INTEGER,"
                    + TvContract.WatchedPrograms.COLUMN_TITLE + " TEXT,"
                    + TvContract.WatchedPrograms.COLUMN_START_TIME_UTC_MILLIS + " INTEGER,"
                    + TvContract.WatchedPrograms.COLUMN_END_TIME_UTC_MILLIS + " INTEGER,"
                    + TvContract.WatchedPrograms.COLUMN_DESCRIPTION + " TEXT,"
                    + TvContract.WatchedPrograms.COLUMN_INTERNAL_TUNE_PARAMS + " TEXT,"
                    + TvContract.WatchedPrograms.COLUMN_INTERNAL_SESSION_TOKEN + " TEXT NOT NULL,"
                    + TvProvider.WATCHED_PROGRAMS_COLUMN_CONSOLIDATED
                    + " INTEGER NOT NULL DEFAULT 0,"
                    + "FOREIGN KEY("
                    + TvContract.WatchedPrograms.COLUMN_CHANNEL_ID + ","
                    + TvContract.WatchedPrograms.COLUMN_PACKAGE_NAME
                    + ") REFERENCES " + TvProvider.CHANNELS_TABLE + "("
                    + TvContract.Channels._ID + "," + TvContract.Channels.COLUMN_PACKAGE_NAME
                    + ") ON UPDATE CASCADE ON DELETE CASCADE"
                    + ");");
            db.execSQL("CREATE INDEX " + TvProvider.WATCHED_PROGRAMS_TABLE_CHANNEL_ID_INDEX + " ON "
                    + TvProvider.WATCHED_PROGRAMS_TABLE
                    + "(" + TvContract.WatchedPrograms.COLUMN_CHANNEL_ID + ");");
        }

        @Override
        public void onDowngrade(SQLiteDatabase db, int oldVersion, int newVersion) {
            assertEquals(BASE_DATABASE_VERSION, newVersion);
            db.execSQL("DROP TABLE IF EXISTS " + TvProvider.WATCH_NEXT_PROGRAMS_TABLE);
            db.execSQL("DROP TABLE IF EXISTS " + TvProvider.PREVIEW_PROGRAMS_TABLE);
            db.execSQL("DROP TABLE IF EXISTS " + TvProvider.RECORDED_PROGRAMS_TABLE);
            db.execSQL("DROP TABLE IF EXISTS " + TvProvider.WATCHED_PROGRAMS_TABLE);
            db.execSQL("DROP TABLE IF EXISTS " + TvProvider.PROGRAMS_TABLE);
            db.execSQL("DROP TABLE IF EXISTS " + TvProvider.CHANNELS_TABLE);
            onCreate(db);
        }
    }
}
