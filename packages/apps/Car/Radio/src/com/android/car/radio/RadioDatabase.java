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

package com.android.car.radio;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.os.Looper;
import android.support.annotation.NonNull;
import android.support.annotation.WorkerThread;
import android.text.TextUtils;
import android.util.Log;
import com.android.car.radio.service.RadioRds;
import com.android.car.radio.service.RadioStation;

import java.util.ArrayList;
import java.util.List;

/**
 * A helper class that manages all operations relating to the database. This class should not
 * be accessed directly. Instead, {@link RadioStorage} interfaces directly with it.
 */
public final class RadioDatabase extends SQLiteOpenHelper {
    private static final String TAG = "Em.RadioDatabase";

    private static final String DATABASE_NAME = "RadioDatabase";
    private static final int DATABASE_VERSION = 1;

    /**
     * The table that holds all the user's currently stored presets.
     */
    private static final class RadioPresetsTable {
        public static final String NAME = "presets_table";

        private static final class Columns {
            public static final String CHANNEL_NUMBER = "channel_number";
            public static final String SUB_CHANNEL = "sub_channel";
            public static final String BAND = "band";
            public static final String PROGRAM_SERVICE = "program_service";
        }
    }

    /**
     * Creates the radio presets table. A channel number together with its subchannel number
     * represents the primary key
     */
    private static final String CREATE_PRESETS_TABLE =
            "CREATE TABLE " + RadioPresetsTable.NAME + " ("
                    + RadioPresetsTable.Columns.CHANNEL_NUMBER + " INTEGER NOT NULL, "
                    + RadioPresetsTable.Columns.SUB_CHANNEL + " INTEGER NOT NULL, "
                    + RadioPresetsTable.Columns.BAND + " INTEGER NOT NULL, "
                    + RadioPresetsTable.Columns.PROGRAM_SERVICE + " TEXT, "
                    + "PRIMARY KEY ("
                    + RadioPresetsTable.Columns.CHANNEL_NUMBER + ", "
                    + RadioPresetsTable.Columns.SUB_CHANNEL + "));";

    private static final String DELETE_PRESETS_TABLE =
            "DROP TABLE IF EXISTS " + RadioPresetsTable.NAME;

    /**
     * Query to return the entire {@link RadioPresetsTable}.
     */
    private static final String GET_ALL_PRESETS =
            "SELECT * FROM " + RadioPresetsTable.NAME
                    + " ORDER BY " + RadioPresetsTable.Columns.CHANNEL_NUMBER
                    + ", " + RadioPresetsTable.Columns.SUB_CHANNEL;

    /**
     * The WHERE clause for a delete preset operation. A preset is identified uniquely by its
     * channel and subchannel number.
     */
    private static final String DELETE_PRESETS_WHERE_CLAUSE =
            RadioPresetsTable.Columns.CHANNEL_NUMBER + " = ? AND "
            + RadioPresetsTable.Columns.SUB_CHANNEL + " = ?";

    /**
     * The table that holds all radio stations have have been pre-scanned by a secondary tuner.
     */
    private static final class PreScannedStationsTable {
        public static final String NAME = "pre_scanned_table";

        private static final class Columns {
            public static final String CHANNEL_NUMBER = "channel_number";
            public static final String SUB_CHANNEL = "sub_channel";
            public static final String BAND = "band";
            public static final String PROGRAM_SERVICE = "program_service";
        }
    }

    /**
     * Creates the radio pre-scanned table. A channel number together with its subchannel number
     * represents the primary key
     */
    private static final String CREATE_PRE_SCAN_TABLE =
            "CREATE TABLE " + PreScannedStationsTable.NAME + " ("
                    + PreScannedStationsTable.Columns.CHANNEL_NUMBER + " INTEGER NOT NULL, "
                    + PreScannedStationsTable.Columns.SUB_CHANNEL + " INTEGER NOT NULL, "
                    + PreScannedStationsTable.Columns.BAND + " INTEGER NOT NULL, "
                    + PreScannedStationsTable.Columns.PROGRAM_SERVICE + " TEXT, "
                    + "PRIMARY KEY ("
                    + PreScannedStationsTable.Columns.CHANNEL_NUMBER + ", "
                    + PreScannedStationsTable.Columns.SUB_CHANNEL + "));";

    private static final String DELETE_PRE_SCAN_TABLE =
            "DROP TABLE IF EXISTS " + PreScannedStationsTable.NAME;

    /**
     * Query to return all the pre-scanned stations for a particular radio band.
     */
    private static final String GET_ALL_PRE_SCAN_FOR_BAND =
            "SELECT * FROM " + PreScannedStationsTable.NAME
                    + " WHERE " + PreScannedStationsTable.Columns.BAND  + " = ? "
                    + " ORDER BY " + PreScannedStationsTable.Columns.CHANNEL_NUMBER
                    + ", " + PreScannedStationsTable.Columns.SUB_CHANNEL;

    /**
     * The WHERE clause for a delete operation that will remove all pre-scanned stations for a
     * paritcular radio band.
     */
    private static final String DELETE_PRE_SCAN_WHERE_CLAUSE =
            PreScannedStationsTable.Columns.BAND + " = ?";

    public RadioDatabase(Context context) {
        super(context, DATABASE_NAME, null /* factory */, DATABASE_VERSION);
    }

    /**
     * Returns a list of all user defined radio presets sorted by channel number and then sub
     * channel number. If there are no presets, then an empty {@link List} is returned.
     */
    @WorkerThread
    public List<RadioStation> getAllPresets() {
        assertNotMainThread();

        if (Log.isLoggable(TAG, Log.DEBUG)) {
            Log.d(TAG, "getAllPresets()");
        }

        SQLiteDatabase db = getReadableDatabase();
        List<RadioStation> presets = new ArrayList<>();
        Cursor cursor = null;

        db.beginTransaction();
        try {
            cursor = db.rawQuery(GET_ALL_PRESETS, null /* selectionArgs */);
            while (cursor.moveToNext()) {
                int channel = cursor.getInt(
                        cursor.getColumnIndexOrThrow(RadioPresetsTable.Columns.CHANNEL_NUMBER));
                int subChannel = cursor.getInt(
                        cursor.getColumnIndexOrThrow(RadioPresetsTable.Columns.SUB_CHANNEL));
                int band = cursor.getInt(
                        cursor.getColumnIndexOrThrow(RadioPresetsTable.Columns.BAND));
                String programService = cursor.getString(
                        cursor.getColumnIndexOrThrow(RadioPresetsTable.Columns.PROGRAM_SERVICE));

                RadioRds rds = null;
                if (!TextUtils.isEmpty(programService)) {
                    rds = new RadioRds(programService, null /* songArtist */, null /* songTitle */);
                }

                presets.add(new RadioStation(channel, subChannel, band, rds));
            }

            db.setTransactionSuccessful();
        } finally {
            if (cursor != null) {
                cursor.close();
            }

            db.endTransaction();
            db.close();
        }

        return presets;
    }

    /**
     * Inserts that given {@link RadioStation} as a preset into the database. The given station
     * will replace any existing station in the database if there is a conflict.
     *
     * @return {@code true} if the operation succeeded.
     */
    @WorkerThread
    public boolean insertPreset(RadioStation preset) {
        assertNotMainThread();

        ContentValues values = new ContentValues();
        values.put(RadioPresetsTable.Columns.CHANNEL_NUMBER, preset.getChannelNumber());
        values.put(RadioPresetsTable.Columns.SUB_CHANNEL, preset.getSubChannelNumber());
        values.put(RadioPresetsTable.Columns.BAND, preset.getRadioBand());

        if (preset.getRds() != null) {
            values.put(RadioPresetsTable.Columns.PROGRAM_SERVICE,
                    preset.getRds().getProgramService());
        }

        SQLiteDatabase db = getWritableDatabase();
        long status;

        db.beginTransaction();
        try {
            status = db.insertWithOnConflict(RadioPresetsTable.NAME, null /* nullColumnHack */,
                    values, SQLiteDatabase.CONFLICT_REPLACE);

            db.setTransactionSuccessful();
        } finally {
            db.endTransaction();
            db.close();
        }

        return status != -1;
    }

    /**
     * Removes the preset represented by the given {@link RadioStation}.
     *
     * @return {@code true} if the operation succeeded.
     */
    @WorkerThread
    public boolean deletePreset(RadioStation preset) {
        assertNotMainThread();

        SQLiteDatabase db = getWritableDatabase();
        long rowsDeleted;

        db.beginTransaction();
        try {
            String channelNumber = Integer.toString(preset.getChannelNumber());
            String subChannelNumber = Integer.toString(preset.getSubChannelNumber());

            rowsDeleted = db.delete(RadioPresetsTable.NAME, DELETE_PRESETS_WHERE_CLAUSE,
                    new String[] { channelNumber, subChannelNumber });

            db.setTransactionSuccessful();
        } finally {
            db.endTransaction();
            db.close();
        }

        return rowsDeleted != 0;
    }

    /**
     * Returns all the pre-scanned stations for the given radio band.
     *
     * @param radioBand One of the band values in {@link android.hardware.radio.RadioManager}.
     * @return A list of pre-scanned stations or an empty array if no stations found.
     */
    @NonNull
    @WorkerThread
    public List<RadioStation> getAllPreScannedStationsForBand(int radioBand) {
        assertNotMainThread();

        if (Log.isLoggable(TAG, Log.DEBUG)) {
            Log.d(TAG, "getAllPreScannedStationsForBand()");
        }

        SQLiteDatabase db = getReadableDatabase();
        List<RadioStation> stations = new ArrayList<>();
        Cursor cursor = null;

        db.beginTransaction();
        try {
            cursor = db.rawQuery(GET_ALL_PRE_SCAN_FOR_BAND,
                    new String[] { Integer.toString(radioBand) });

            while (cursor.moveToNext()) {
                int channel = cursor.getInt(cursor.getColumnIndexOrThrow(
                        PreScannedStationsTable.Columns.CHANNEL_NUMBER));
                int subChannel = cursor.getInt(
                        cursor.getColumnIndexOrThrow(PreScannedStationsTable.Columns.SUB_CHANNEL));
                int band = cursor.getInt(
                        cursor.getColumnIndexOrThrow(PreScannedStationsTable.Columns.BAND));
                String programService = cursor.getString(cursor.getColumnIndexOrThrow(
                        PreScannedStationsTable.Columns.PROGRAM_SERVICE));

                RadioRds rds = null;
                if (!TextUtils.isEmpty(programService)) {
                    rds = new RadioRds(programService, null /* songArtist */, null /* songTitle */);
                }

                stations.add(new RadioStation(channel, subChannel, band, rds));
            }

            db.setTransactionSuccessful();
        } finally {
            if (cursor != null) {
                cursor.close();
            }

            db.endTransaction();
            db.close();
        }

        return stations;
    }

    /**
     * Inserts the given list of {@link RadioStation}s as the list of pre-scanned stations for the
     * given band. This operation will clear all currently stored stations for the given band
     * and replace them with the given list.
     *
     * @param radioBand One of the band values in {@link android.hardware.radio.RadioManager}.
     * @param stations A list of {@link RadioStation}s representing the pre-scanned stations.
     * @return {@code true} if the operation was successful.
     */
    @WorkerThread
    public boolean insertPreScannedStations(int radioBand, List<RadioStation> stations) {
        assertNotMainThread();

        SQLiteDatabase db = getWritableDatabase();
        db.beginTransaction();

        long status = -1;

        try {
            // First clear all pre-scanned stations for the given radio band so that they can be
            // replaced by the list of stations.
            db.delete(PreScannedStationsTable.NAME, DELETE_PRE_SCAN_WHERE_CLAUSE,
                    new String[] { Integer.toString(radioBand) });

            for (RadioStation station : stations) {
                ContentValues values = new ContentValues();
                values.put(PreScannedStationsTable.Columns.CHANNEL_NUMBER,
                        station.getChannelNumber());
                values.put(PreScannedStationsTable.Columns.SUB_CHANNEL,
                        station.getSubChannelNumber());
                values.put(PreScannedStationsTable.Columns.BAND, station.getRadioBand());

                if (station.getRds() != null) {
                    values.put(PreScannedStationsTable.Columns.PROGRAM_SERVICE,
                            station.getRds().getProgramService());
                }

                status = db.insertWithOnConflict(PreScannedStationsTable.NAME,
                        null /* nullColumnHack */, values, SQLiteDatabase.CONFLICT_REPLACE);
            }

            db.setTransactionSuccessful();
        } finally {
            db.endTransaction();
            db.close();
        }

        return status != -1;
    }

    /**
     * Checks that the current thread is not the main thread. If it is, then an
     * {@link IllegalStateException} is thrown. This assert should be called before all database
     * operations.
     */
    private void assertNotMainThread() {
        if (Looper.myLooper() == Looper.getMainLooper()) {
            throw new IllegalStateException("Attempting to call database methods on main thread.");
        }
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        db.execSQL(CREATE_PRESETS_TABLE);
        db.execSQL(CREATE_PRE_SCAN_TABLE);
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        db.beginTransaction();

        try {
            // Currently no upgrade steps as this is the first version of the database. Simply drop
            // all tables and re-create.
            db.execSQL(DELETE_PRESETS_TABLE);
            db.execSQL(DELETE_PRE_SCAN_TABLE);
            onCreate(db);

            db.setTransactionSuccessful();
        } finally {
            db.endTransaction();
        }
    }

    @Override
    public void onDowngrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        onUpgrade(db, oldVersion, newVersion);
    }
}
