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

package android.cts.backup.fullbackupapp;

import static android.support.test.InstrumentationRegistry.getTargetContext;

import static org.junit.Assert.assertTrue;
import static org.junit.Assert.assertFalse;

import android.content.Context;
import android.support.test.runner.AndroidJUnit4;
import android.util.Log;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.Random;

/**
 * Device side routines to be invoked by the host side FullbackupRulesHostSideTest. These are not
 * designed to be called in any other way, as they rely on state set up by the host side test.
 */
@RunWith(AndroidJUnit4.class)
public class FullbackupTest {
    public static final String TAG = "FullbackupCTSApp";
    private static final int FILE_SIZE_BYTES = 1024 * 1024;

    private Context mContext;

    private File mNoBackupFile;
    private File mNoBackupFile2;
    private File mDoBackupFile;
    private File mDoBackupFile2;

    @Before
    public void setUp() {
        mContext = getTargetContext();
        setupFiles();
    }

    private void setupFiles() {
        // Files in the 'no_backup' directory. We expect these to not be backed up.
        File noBackupDir = mContext.getNoBackupFilesDir();
        File folderInNoBackup = new File(noBackupDir, "folder_not_to_backup");

        mNoBackupFile = new File(noBackupDir, "file_not_backed_up");
        mNoBackupFile2 = new File(folderInNoBackup, "file_not_backed_up2");

        // Files in the normal files directory. These should be backed up and restored.
        File filesDir = mContext.getFilesDir();
        File normalFolder = new File(filesDir, "normal_folder");

        mDoBackupFile = new File(filesDir, "file_to_backup");
        mDoBackupFile2 = new File(normalFolder, "file_to_backup2");
    }

    @Test
    public void createFiles() throws Exception {
        // Make sure the data does not exist from before
        deleteAllFiles();
        checkNoFilesExist();

        // Create test data
        generateFiles();
        checkAllFilesExist();

        Log.d(TAG, "Test files created:");
        Log.d(TAG, mNoBackupFile.getAbsolutePath());
        Log.d(TAG, mNoBackupFile2.getAbsolutePath());
        Log.d(TAG, mDoBackupFile.getAbsolutePath());
        Log.d(TAG, mDoBackupFile2.getAbsolutePath());
    }

    @Test
    public void deleteFilesAfterBackup() throws Exception {
        // Make sure the test data exists first
        checkAllFilesExist();

        // Delete test data
        deleteAllFiles();

        // Now there should be no files left
        checkNoFilesExist();
    }

    @Test
    public void checkRestoredFiles() throws Exception {
        // After a restore, only files outside the 'no_backup' folder should exist
        checkNoBackupFilesDoNotExist();
        checkDoBackupFilesDoExist();
    }

    private void generateFiles() {
        try {
            // Add data to all the files we created
            addData(mNoBackupFile);
            addData(mNoBackupFile2);
            addData(mDoBackupFile);
            addData(mDoBackupFile2);
            Log.d(TAG, "Files generated!");
        } catch (IOException e) {
            Log.e(TAG, "Unable to generate files", e);
        }
    }

    private void deleteAllFiles() {
        mNoBackupFile.delete();
        mNoBackupFile2.delete();
        mDoBackupFile.delete();
        mDoBackupFile2.delete();
        Log.d(TAG, "Files deleted!");
    }

    private void addData(File file) throws IOException {
        file.getParentFile().mkdirs();
        byte[] bytes = new byte[FILE_SIZE_BYTES];
        new Random().nextBytes(bytes);

        try (BufferedOutputStream bos = new BufferedOutputStream(new FileOutputStream(file))) {
            bos.write(bytes, 0, bytes.length);
        }
    }

    private void checkAllFilesExist() {
        assertTrue("File in 'no_backup' did not exist!", mNoBackupFile.exists());
        assertTrue("File in folder inside 'no_backup' did not exist!", mNoBackupFile2.exists());
        assertTrue("File in 'files' did not exist!", mDoBackupFile.exists());
        assertTrue("File in folder inside 'files' did not exist!", mDoBackupFile2.exists());
    }

    private void checkNoFilesExist() {
        assertFalse("File in 'no_backup' did exist!", mNoBackupFile.exists());
        assertFalse("File in folder inside 'no_backup' did exist!", mNoBackupFile2.exists());
        assertFalse("File in 'files' did exist!", mDoBackupFile.exists());
        assertFalse("File in folder inside 'files' did exist!", mDoBackupFile2.exists());
    }

    private void checkNoBackupFilesDoNotExist() {
        assertFalse("File in 'no_backup' did exist!", mNoBackupFile.exists());
        assertFalse("File in folder inside 'no_backup' did exist!", mNoBackupFile2.exists());
    }

    private void checkDoBackupFilesDoExist() {
        assertTrue("File in 'files' did not exist!", mDoBackupFile.exists());
        assertTrue("File in folder inside 'files' did not exist!", mDoBackupFile2.exists());
    }
}
