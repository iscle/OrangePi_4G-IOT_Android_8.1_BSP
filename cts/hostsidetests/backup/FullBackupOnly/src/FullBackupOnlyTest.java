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

import static android.support.test.InstrumentationRegistry.getTargetContext;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

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
 * Device side routines to be invoked by the host side FullBackupOnlyHostSideTest. These
 * are not designed to be called in any other way, as they rely on state set up by the host side
 * test.
 */
@RunWith(AndroidJUnit4.class)
public class FullBackupOnlyTest {
    private static final String TAG = "FullBackupOnlyTest";

    private static final int FILE_SIZE_BYTES = 1024 * 1024;

    private File mKeyValueBackupFile;
    private File mDoBackupFile;
    private File mDoBackupFile2;

    private Context mContext;

    @Before
    public void setUp() {
        Log.i(TAG, "set up");
        mContext = getTargetContext();
        setupFiles();
    }

    private void setupFiles() {
        mKeyValueBackupFile = getKeyValueBackupFile(mContext);

        // Files to be backed up with Dolly
        File filesDir = mContext.getFilesDir();
        File normalFolder = new File(filesDir, "normal_folder");

        mDoBackupFile = new File(filesDir, "file_to_backup");
        mDoBackupFile2 = new File(normalFolder, "file_to_backup2");
    }

    @Test
    public void createFiles() throws Exception {
        // Make sure the data does not exist from before
        deleteAllFiles();
        assertNoFilesExist();

        // Create test data
        generateFiles();
        assertAllFilesExist();

        Log.d(TAG, "Test files created: \n"
                + mKeyValueBackupFile.getAbsolutePath() + "\n"
                + mDoBackupFile.getAbsolutePath() + "\n"
                + mDoBackupFile2.getAbsolutePath());
    }

    @Test
    public void checkKeyValueFileDoesntExist() throws Exception {
        assertKeyValueFileDoesntExist();
    }

    @Test
    public void checkKeyValueFileExists() throws Exception {
        assertKeyValueFileExists();
    }

    @Test
    public void checkDollyFilesExist() throws Exception {
        assertDollyFilesExist();
    }

    @Test
    public void checkDollyFilesDontExist() throws Exception {
        assertDollyFilesDontExist();
    }

    protected static File getKeyValueBackupFile(Context context) {
        // Files in the 'no_backup' directory are not backed up with Dolly.
        // We're going to use it to store a file the contents of which are backed up via key/value.
        File noBackupDir = context.getNoBackupFilesDir();
        return new File(noBackupDir, "key_value_backup_file");
    }

    private void generateFiles() {
        try {
            // Add data to all the files we created
            addData(mKeyValueBackupFile);
            addData(mDoBackupFile);
            addData(mDoBackupFile2);
            Log.d(TAG, "Files generated!");
        } catch (IOException e) {
            Log.e(TAG, "Unable to generate files", e);
        }
    }

    private void deleteAllFiles() {
        mKeyValueBackupFile.delete();
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

    private void assertAllFilesExist() {
        assertKeyValueFileExists();
        assertDollyFilesExist();
    }

    private void assertNoFilesExist() {
        assertKeyValueFileDoesntExist();
        assertDollyFilesDontExist();
    }

    private void assertKeyValueFileExists() {
        assertTrue("Key/value file did not exist!", mKeyValueBackupFile.exists());
    }

    private void assertKeyValueFileDoesntExist() {
        assertFalse("Key/value file did exist!", mKeyValueBackupFile.exists());
    }

    private void assertDollyFilesExist() {
        assertTrue("File in 'files' did not exist!", mDoBackupFile.exists());
        assertTrue("File in folder inside 'files' did not exist!", mDoBackupFile2.exists());
    }

    private void assertDollyFilesDontExist() {
        assertFalse("File in 'files' did exist!", mDoBackupFile.exists());
        assertFalse("File in folder inside 'files' did exist!", mDoBackupFile2.exists());
    }
}
