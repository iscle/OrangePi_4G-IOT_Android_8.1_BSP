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
 * limitations under the License
 */

package android.cts.backup.fullbackuponlyapp;

import android.app.backup.BackupAgent;
import android.app.backup.BackupDataInput;
import android.app.backup.BackupDataOutput;

import android.os.ParcelFileDescriptor;
import android.util.Log;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;

/**
 * BackupAgent for test apps in {@link android.cts.backup.fullbackuponlyapp} package.
 * Performs backup/restore of the file provided by {@link FullBackupOnlyTest}.
 *
 * Implementation of key/value backup methods is based on the examples from
 * https://developer.android.com/guide/topics/data/keyvaluebackup.html
 */
public class FullBackupOnlyBackupAgent extends BackupAgent {
    private static final String TAG = "FullBackupOnlyBA";

    private static final String BACKUP_KEY = "full_backup_only_key";
    private static final int DEFAULT_VALUE = -1;

    /** Get the value saved in the shared preference and back it up. */
    @Override
    public void onBackup(ParcelFileDescriptor oldState, BackupDataOutput data,
            ParcelFileDescriptor newState) throws IOException {
        Log.i(TAG, "onBackup");

        File fileToBackup = FullBackupOnlyTest.getKeyValueBackupFile(this);
        byte[] buffer = readFileToBytes(fileToBackup);

        // Send the data to the Backup Manager via the BackupDataOutput
        int len = buffer.length;
        data.writeEntityHeader(BACKUP_KEY, len);
        data.writeEntityData(buffer, len);
    }

    /** Get the value from backup and save it in the shared preference. */
    @Override
    public void onRestore(BackupDataInput data, int appVersionCode, ParcelFileDescriptor newState)
            throws IOException {
        Log.i(TAG, "onRestore");
        int value = DEFAULT_VALUE;

        // There should be only one entity, but the safest
        // way to consume it is using a while loop
        while (data.readNextHeader()) {
            String key = data.getKey();
            int dataSize = data.getDataSize();

            // If the key is ours (for saving top score). Note this key was used when
            // we wrote the backup entity header
            if (BACKUP_KEY.equals(key)) {
                // Copy data from BackupDataInput and write it into the file.
                byte[] dataBuf = new byte[dataSize];
                data.readEntityData(dataBuf, 0, dataSize);

                File fileToRestore = FullBackupOnlyTest.getKeyValueBackupFile(this);

                try (BufferedOutputStream bos = new BufferedOutputStream(
                        new FileOutputStream(fileToRestore))) {
                    bos.write(dataBuf, 0, dataBuf.length);
                }
            } else {
                // We don't know this entity key. Shouldn't happen.
                throw new RuntimeException("Unexpected key in the backup data");
            }
        }

        // Finally, write to the state blob (newState) that describes the restored data
        FileOutputStream outstream = new FileOutputStream(newState.getFileDescriptor());
        DataOutputStream out = new DataOutputStream(outstream);
        out.writeInt(value);
    }


    private byte[] readFileToBytes(File file) {
        int size = (int) file.length();
        byte[] bytes = new byte[size];
        try {
            BufferedInputStream buf = new BufferedInputStream(new FileInputStream(file));
            buf.read(bytes, 0, bytes.length);
            buf.close();
        } catch (FileNotFoundException e) {
            throw new RuntimeException("Key/value file not found", e);
        } catch (IOException e) {
            throw new RuntimeException("Error reading key/value file", e);
        }
        return bytes;
    }
}
