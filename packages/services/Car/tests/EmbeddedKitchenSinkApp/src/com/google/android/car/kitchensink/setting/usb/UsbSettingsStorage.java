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
package com.google.android.car.kitchensink.setting.usb;

import android.annotation.Nullable;
import android.content.ComponentName;
import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.util.Log;

import java.util.ArrayList;
import java.util.List;

/**
 * Provides API to persist USB device settings.
 */
public final class UsbSettingsStorage {
    private static final String TAG = UsbSettingsStorage.class.getSimpleName();

    private static final String TABLE_USB_SETTINGS = "usb_devices";
    private static final String COLUMN_SERIAL = "serial";
    private static final String COLUMN_VID = "vid";
    private static final String COLUMN_PID = "pid";
    private static final String COLUMN_NAME = "name";
    private static final String COLUMN_HANDLER = "handler";
    private static final String COLUMN_AOAP = "aoap";

    private final UsbSettingsDbHelper mDbHelper;

    public UsbSettingsStorage(Context context) {
        mDbHelper = new UsbSettingsDbHelper(context);
    }

    /**
     * Returns settings for {@serialNumber} or null if it doesn't exist.
     */
    @Nullable
    public UsbDeviceSettings getSettings(String serialNumber, int vid, int pid) {
        try (SQLiteDatabase db = mDbHelper.getReadableDatabase();
             Cursor resultCursor = db.query(
                     TABLE_USB_SETTINGS,
                     null,
                     COLUMN_SERIAL + " = ? AND " + COLUMN_VID + " = ? AND " + COLUMN_PID + " = ?",
                     new String[]{serialNumber, Integer.toString(vid), Integer.toString(pid)},
                     null,
                     null,
                     null)) {
            if (resultCursor.getCount() > 1) {
                throw new RuntimeException("Quering for serial number: " + serialNumber
                        + " vid: " + vid + " pid: " + pid + " returned "
                        + resultCursor.getCount() + " results");
            }
            if (resultCursor.getCount() == 0) {
                Log.w(TAG, "Usb setting missing for device serial: " + serialNumber
                        + " vid: " + vid + " pid: " + pid);
                return null;
            }
            List<UsbDeviceSettings> settings = constructSettings(resultCursor);
            return settings.get(0);
        }
    }

    /**
     * Saves or updates settings for USB device.
     */
    public void saveSettings(UsbDeviceSettings settings) {
        try (SQLiteDatabase db = mDbHelper.getWritableDatabase()) {
            long result = db.replace(
                    TABLE_USB_SETTINGS,
                    null,
                    settingsToContentValues(settings));
            if (result == -1) {
                Log.e(TAG, "Failed to save settings: " + settings);
            }
        }
    }

    /**
     * Delete settings for USB device.
     */
    public void deleteSettings(String serialNumber, int vid, int pid) {
        try (SQLiteDatabase db = mDbHelper.getWritableDatabase()) {
            int result = db.delete(
                    TABLE_USB_SETTINGS,
                    COLUMN_SERIAL + " = ? AND " + COLUMN_VID + " = ? AND " + COLUMN_PID
                    + " = ?",
                    new String[]{serialNumber, Integer.toString(vid), Integer.toString(pid)});
            if (result == 0) {
                Log.w(TAG, "No settings with serialNumber: " + serialNumber
                        + " vid: " + vid + " pid: " + pid);
            }
            if (result > 1) {
                Log.e(TAG, "Deleted multiple rows (" + result + ") for serialNumber: "
                        + serialNumber + " vid: " + vid + " pid: " + pid);
            }
        }
    }

    /**
     * Returns all saved settings.
     */
    public List<UsbDeviceSettings> getAllSettings() {
        try (SQLiteDatabase db = mDbHelper.getReadableDatabase();
             Cursor resultCursor = db.query(
                     TABLE_USB_SETTINGS,
                     null,
                     null,
                     null,
                     null,
                     null,
                     null)) {
            return constructSettings(resultCursor);
        }
    }

    private List<UsbDeviceSettings> constructSettings(Cursor cursor) {
        if (!cursor.isBeforeFirst()) {
            throw new RuntimeException("Cursor is not reset to before first element");
        }
        int serialNumberColumnId = cursor.getColumnIndex(COLUMN_SERIAL);
        int vidColumnId = cursor.getColumnIndex(COLUMN_VID);
        int pidColumnId = cursor.getColumnIndex(COLUMN_PID);
        int deviceNameColumnId = cursor.getColumnIndex(COLUMN_NAME);
        int handlerColumnId = cursor.getColumnIndex(COLUMN_HANDLER);
        int aoapColumnId = cursor.getColumnIndex(COLUMN_AOAP);
        List<UsbDeviceSettings> results = new ArrayList<>(cursor.getCount());
        while (cursor.moveToNext()) {
            results.add(UsbDeviceSettings.constructSettings(
                                cursor.getString(serialNumberColumnId),
                                cursor.getInt(vidColumnId),
                                cursor.getInt(pidColumnId),
                                cursor.getString(deviceNameColumnId),
                                ComponentName.unflattenFromString(
                                        cursor.getString(handlerColumnId)),
                                cursor.getInt(aoapColumnId) != 0));
        }
        return results;
    }

    /**
     * Converts {@code UsbDeviceSettings} to {@code ContentValues}.
     */
    public ContentValues settingsToContentValues(UsbDeviceSettings settings) {
        ContentValues contentValues = new ContentValues();
        contentValues.put(COLUMN_SERIAL, settings.getSerialNumber());
        contentValues.put(COLUMN_VID, settings.getVid());
        contentValues.put(COLUMN_PID, settings.getPid());
        contentValues.put(COLUMN_NAME, settings.getDeviceName());
        contentValues.put(COLUMN_HANDLER, settings.getHandler().flattenToShortString());
        contentValues.put(COLUMN_AOAP, settings.getAoap() ? 1 : 0);
        return contentValues;
    }


    private static class UsbSettingsDbHelper extends SQLiteOpenHelper {
        private static final int DATABASE_VERSION = 2;
        private static final String DATABASE_NAME = "usb_devices.db";

        UsbSettingsDbHelper(Context context) {
            super(context, DATABASE_NAME, null, DATABASE_VERSION);
        }

        public void onCreate(SQLiteDatabase db) {
            db.execSQL("CREATE TABLE " + TABLE_USB_SETTINGS + " ("
                    + COLUMN_SERIAL + " TEXT,"
                    + COLUMN_VID + " INTEGER,"
                    + COLUMN_PID + " INTEGER,"
                    + COLUMN_NAME + " TEXT, "
                    + COLUMN_HANDLER + " TEXT,"
                    + COLUMN_AOAP + " INTEGER,"
                    + "PRIMARY KEY (" + COLUMN_SERIAL + ", " + COLUMN_VID + ", "
                    + COLUMN_PID + "))");
        }

        public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
            if (oldVersion < 2) {
                db.execSQL("ALTER TABLE " + TABLE_USB_SETTINGS + " ADD " + COLUMN_AOAP
                        + " INTEGER");
            }
            // Do nothing at this point. Not required for v1 database.
        }
    }
}
