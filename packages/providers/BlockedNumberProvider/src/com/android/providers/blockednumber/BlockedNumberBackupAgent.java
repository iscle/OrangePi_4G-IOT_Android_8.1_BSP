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

import android.annotation.Nullable;
import android.app.backup.BackupAgent;
import android.app.backup.BackupDataInput;
import android.app.backup.BackupDataOutput;
import android.content.ContentResolver;
import android.content.ContentValues;
import android.database.Cursor;
import android.os.ParcelFileDescriptor;
import android.provider.BlockedNumberContract;
import android.util.Log;

import libcore.io.IoUtils;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.SortedSet;
import java.util.TreeSet;

/**
 * A backup agent to enable backup and restore of blocked numbers.
 */
public class BlockedNumberBackupAgent extends BackupAgent {
    private static final String[] BLOCKED_NUMBERS_PROJECTION = new String[] {
            BlockedNumberContract.BlockedNumbers.COLUMN_ID,
            BlockedNumberContract.BlockedNumbers.COLUMN_ORIGINAL_NUMBER,
            BlockedNumberContract.BlockedNumbers.COLUMN_E164_NUMBER,
    };
    private static final String TAG = "BlockedNumberBackup";
    private static final int VERSION = 1;
    private static final boolean DEBUG = false; // DO NOT SUBMIT WITH TRUE.

    @Override
    public void onBackup(ParcelFileDescriptor oldState, BackupDataOutput backupDataOutput,
                         ParcelFileDescriptor newState) throws IOException {
        logV("Backing up blocked numbers.");

        DataInputStream dataInputStream =
                new DataInputStream(new FileInputStream(oldState.getFileDescriptor()));
        final BackupState state;
        try {
            state = readState(dataInputStream);
        } finally {
            IoUtils.closeQuietly(dataInputStream);
        }

        runBackup(state, backupDataOutput, getAllBlockedNumbers());

        DataOutputStream dataOutputStream =
                new DataOutputStream(new FileOutputStream(newState.getFileDescriptor()));
        try {
            writeNewState(dataOutputStream, state);
        } finally {
            dataOutputStream.close();
        }
    }

    @Override
    public void onRestore(BackupDataInput data, int appVersionCode,
                          ParcelFileDescriptor newState) throws IOException {
        logV("Restoring blocked numbers.");

        while (data.readNextHeader()) {
            BackedUpBlockedNumber blockedNumber = readBlockedNumberFromData(data);
            if (blockedNumber != null) {
                writeToProvider(blockedNumber);
            }
        }
    }

    private BackupState readState(DataInputStream dataInputStream) throws IOException {
        int version = VERSION;
        if (dataInputStream.available() > 0) {
            version = dataInputStream.readInt();
        }
        BackupState state = new BackupState(version, new TreeSet<Integer>());
        while (dataInputStream.available() > 0) {
            state.ids.add(dataInputStream.readInt());
        }
        return state;
    }

    private void runBackup(BackupState state, BackupDataOutput backupDataOutput,
                           Iterable<BackedUpBlockedNumber> allBlockedNumbers) throws IOException {
        SortedSet<Integer> deletedBlockedNumbers = new TreeSet<>(state.ids);

        for (BackedUpBlockedNumber blockedNumber : allBlockedNumbers) {
            if (state.ids.contains(blockedNumber.id)) {
                // Existing blocked number: do not delete.
                deletedBlockedNumbers.remove(blockedNumber.id);
            } else {
                logV("Adding blocked number to backup: " + blockedNumber);
                // New blocked number
                addToBackup(backupDataOutput, blockedNumber);
                state.ids.add(blockedNumber.id);
            }
        }

        for (int id : deletedBlockedNumbers) {
            logV("Removing blocked number from backup: " + id);
            removeFromBackup(backupDataOutput, id);
            state.ids.remove(id);
        }
    }

    private void addToBackup(BackupDataOutput output, BackedUpBlockedNumber blockedNumber)
            throws IOException {
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        DataOutputStream dataOutputStream = new DataOutputStream(outputStream);
        dataOutputStream.writeInt(VERSION);
        writeString(dataOutputStream, blockedNumber.originalNumber);
        writeString(dataOutputStream, blockedNumber.e164Number);
        dataOutputStream.flush();

        output.writeEntityHeader(Integer.toString(blockedNumber.id), outputStream.size());
        output.writeEntityData(outputStream.toByteArray(), outputStream.size());
    }

    private void writeString(DataOutputStream dataOutputStream, @Nullable String value)
            throws IOException {
        if (value == null) {
            dataOutputStream.writeBoolean(false);
        } else {
            dataOutputStream.writeBoolean(true);
            dataOutputStream.writeUTF(value);
        }
    }

    @Nullable
    private String readString(DataInputStream dataInputStream)
            throws IOException {
        if (dataInputStream.readBoolean()) {
            return dataInputStream.readUTF();
        } else {
            return null;
        }
    }

    private void removeFromBackup(BackupDataOutput output, int id) throws IOException {
        output.writeEntityHeader(Integer.toString(id), -1);
    }

    private Iterable<BackedUpBlockedNumber> getAllBlockedNumbers() {
        List<BackedUpBlockedNumber> blockedNumbers = new ArrayList<>();
        ContentResolver resolver = getContentResolver();
        Cursor cursor = resolver.query(
                BlockedNumberContract.BlockedNumbers.CONTENT_URI, BLOCKED_NUMBERS_PROJECTION, null,
                null, null);
        if (cursor != null) {
            try {
                while (cursor.moveToNext()) {
                    blockedNumbers.add(createBlockedNumberFromCursor(cursor));
                }
            } finally {
                cursor.close();
            }
        }
        return blockedNumbers;
    }

    private BackedUpBlockedNumber createBlockedNumberFromCursor(Cursor cursor) {
        return new BackedUpBlockedNumber(
                cursor.getInt(0), cursor.getString(1), cursor.getString(2));
    }

    private void writeNewState(DataOutputStream dataOutputStream, BackupState state)
            throws IOException {
        dataOutputStream.writeInt(VERSION);
        for (int i : state.ids) {
            dataOutputStream.writeInt(i);
        }
    }

    @Nullable
    private BackedUpBlockedNumber readBlockedNumberFromData(BackupDataInput data) {
        int id;
        try {
            id = Integer.parseInt(data.getKey());
        } catch (NumberFormatException e) {
            Log.e(TAG, "Unexpected key found in restore: " + data.getKey());
            return null;
        }

        try {
            byte[] byteArray = new byte[data.getDataSize()];
            data.readEntityData(byteArray, 0, byteArray.length);
            DataInputStream dataInput = new DataInputStream(new ByteArrayInputStream(byteArray));
            dataInput.readInt(); // Ignore version.
            BackedUpBlockedNumber blockedNumber =
                    new BackedUpBlockedNumber(id, readString(dataInput), readString(dataInput));
            logV("Restoring blocked number: " + blockedNumber);
            return blockedNumber;
        } catch (IOException e) {
            Log.e(TAG, "Error reading blocked number for: " + id + ": " + e.getMessage());
            return null;
        }
    }

    private void writeToProvider(BackedUpBlockedNumber blockedNumber) {
        ContentValues contentValues = new ContentValues();
        contentValues.put(BlockedNumberContract.BlockedNumbers.COLUMN_ORIGINAL_NUMBER,
                blockedNumber.originalNumber);
        contentValues.put(BlockedNumberContract.BlockedNumbers.COLUMN_E164_NUMBER,
                blockedNumber.e164Number);
        try {
            getContentResolver().insert(
                    BlockedNumberContract.BlockedNumbers.CONTENT_URI, contentValues);
        } catch (Exception e) {
            Log.e(TAG, "Unable to insert blocked number " + blockedNumber + " :" + e.getMessage());
        }
    }

    private static boolean isDebug() {
        return Log.isLoggable(TAG, Log.DEBUG);
    }

    private static void logV(String msg) {
        if (DEBUG) {
            Log.v(TAG, msg);
        }
    }

    private static class BackupState {
        final int version;
        final SortedSet<Integer> ids;

        BackupState(int version, SortedSet<Integer> ids) {
            this.version = version;
            this.ids = ids;
        }
    }

    private static class BackedUpBlockedNumber {
        final int id;
        final String originalNumber;
        final String e164Number;

        BackedUpBlockedNumber(int id, String originalNumber, String e164Number) {
            this.id = id;
            this.originalNumber = originalNumber;
            this.e164Number = e164Number;
        }

        @Override
        public String toString() {
            if (isDebug()) {
                return String.format("[%d, original number: %s, e164 number: %s]",
                        id, originalNumber, e164Number);
            } else {
                return String.format("[%d]", id);
            }
        }
    }
}
