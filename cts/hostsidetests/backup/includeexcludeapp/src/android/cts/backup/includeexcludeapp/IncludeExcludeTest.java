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

package android.cts.backup.includeexcludeapp;

import static android.support.test.InstrumentationRegistry.getTargetContext;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.fail;

import android.content.Context;
import android.content.SharedPreferences;
import android.support.test.runner.AndroidJUnit4;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * Device side routines to be invoked by the host side FullbackupRulesHostSideTest. These are not
 * designed to be called in any other way, as they rely on state set up by the host side test.
 */
@RunWith(AndroidJUnit4.class)
public class IncludeExcludeTest {
    public static final String TAG = "IncludeExcludeCTSApp";
    private static final int FILE_SIZE_BYTES = 1024 * 1024;
    private static final String SHARED_PREF_KEY1 = "dummy_key1";
    private static final String SHARED_PREF_KEY2 = "dummy_key2";
    private static final int SHARED_PREF_VALUE1 = 1337;
    private static final int SHARED_PREF_VALUE2 = 1338;

    private Context mContext;

    private List<File> mIncludeFiles;
    private List<File> mExcludeFiles;

    private SharedPreferences mIncludeSharedPref;
    private SharedPreferences mExcludeSharedPref;

    @Before
    public void setUp() {
        mContext = getTargetContext();
        setupFiles();
    }

    private void setupFiles() {

        // We add all the files we expect to be backed up accoring to res/my_backup_rules.xml to
        // the mIncludeFiles list, and all files expected not to be to mExcludeFiles.
        mIncludeFiles = new ArrayList<File>();
        mExcludeFiles = new ArrayList<File>();

        // Files in the normal files directory.
        File filesDir = mContext.getFilesDir();
        File excludeFolder = new File(filesDir, "exclude_folder");
        mIncludeFiles.add(new File(filesDir, "file_to_include"));
        mExcludeFiles.add(new File(excludeFolder, "file_to_exclude"));

        // Files in database directory.
        File databaseDir = mContext.getDatabasePath("db_name");
        mIncludeFiles.add(new File(databaseDir, "file_to_include"));
        mExcludeFiles.add(new File(databaseDir, "file_to_exclude"));

        // Files in external files directory.
        File externalDir = mContext.getExternalFilesDir(null);
        File excludeExternalFolder = new File(externalDir, "exclude_folder");
        mIncludeFiles.add(new File(externalDir, "file_to_include"));
        mExcludeFiles.add(new File(excludeExternalFolder, "file_to_exclude"));

        // Files in root directory
        File rootDir = mContext.getDataDir();
        mIncludeFiles.add(new File(rootDir, "file_to_include"));
        mExcludeFiles.add(new File(rootDir, "file_to_exclude"));

        // Set up SharedPreferences
        mIncludeSharedPref =
                mContext.getSharedPreferences("include_shared_pref", Context.MODE_PRIVATE);
        mExcludeSharedPref =
                mContext.getSharedPreferences("exclude_shared_pref", Context.MODE_PRIVATE);
    }

    @Test
    public void createFiles() throws Exception {
        // Make sure the data does not exist from before
        deleteAllFiles();
        deleteSharedPref();
        checkNoFilesExist();
        checkSharedPrefDontExist();

        // Create test data
        generateFiles();
        generateSharedPrefs();

        checkAllFilesExist();
        checkSharedPrefExist();
    }

    @Test
    public void deleteFilesAfterBackup() throws Exception {
        // Make sure the test data exists first
        checkAllFilesExist();
        checkSharedPrefExist();

        // Delete test data
        deleteAllFiles();
        deleteSharedPref();

        // Now there should be no files left
        checkNoFilesExist();
        checkSharedPrefDontExist();
    }

    @Test
    public void checkRestoredFiles() throws Exception {
        // After a restore, only files in the mIncludeFiles list should exist.
        checkIncludeFilesDoExist();
        checkExcludeFilesDoNotExist();

        // Also check that the SharedPreferences were restored, except the excluded ones.
        checkIncludeSharedPrefExist();
        checkExcludeSharedPrefDoNotExist();
    }

    private void generateFiles() {
        try {
            // Add data to all the files we created
            for (File file : mIncludeFiles) {
                addData(file);
            }
            for (File file : mExcludeFiles) {
                addData(file);
            }
        } catch (IOException e) {
            fail("Unable to generate files: " + e);
        }
    }

    private void deleteAllFiles() {
        for (File file : mIncludeFiles) {
            file.delete();
        }
        for (File file : mExcludeFiles) {
            file.delete();
        }
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
        for (File file: mIncludeFiles) {
            assertTrue("File did unexpectedly not exist: " + file.getAbsolutePath(), file.exists());
        }
        for (File file: mExcludeFiles) {
            assertTrue("File did unexpectedly not exist: " + file.getAbsolutePath(), file.exists());
        }
    }

    private void checkNoFilesExist() {
        for (File file: mIncludeFiles) {
            assertFalse("File did unexpectedly exist: " + file.getAbsolutePath(), file.exists());
        }
        for (File file: mExcludeFiles) {
            assertFalse("File did unexpectedly exist: " + file.getAbsolutePath(), file.exists());
        }
    }

    private void checkExcludeFilesDoNotExist() {
        for (File file: mExcludeFiles) {
            assertFalse("File expected not to be restored did exist: " + file.getAbsolutePath(),
                    file.exists());
        }
    }

    private void checkIncludeFilesDoExist() {
        for (File file: mIncludeFiles) {
            assertTrue("File expected to be restored did not exist: " + file.getAbsolutePath(),
                    file.exists());
        }
    }

    private void generateSharedPrefs() {
        SharedPreferences.Editor editor = mIncludeSharedPref.edit();
        editor.putInt(SHARED_PREF_KEY1, SHARED_PREF_VALUE1);
        editor.commit();


        editor = mExcludeSharedPref.edit();
        editor.putInt(SHARED_PREF_KEY2, SHARED_PREF_VALUE2);
        editor.commit();
    }

    private void checkSharedPrefExist() {
        int value = mIncludeSharedPref.getInt(SHARED_PREF_KEY1, 0);
        assertEquals("Shared preference did not exist", SHARED_PREF_VALUE1, value);

        value = mExcludeSharedPref.getInt(SHARED_PREF_KEY2, 0);
        assertEquals("Shared preference did not exist", SHARED_PREF_VALUE2, value);
    }

    private void deleteSharedPref() {
        SharedPreferences.Editor editor = mIncludeSharedPref.edit();
        editor.clear();
        editor.commit();

        editor = mExcludeSharedPref.edit();
        editor.clear();
        editor.commit();
    }

    private void checkSharedPrefDontExist() {
        int value = mIncludeSharedPref.getInt(SHARED_PREF_KEY1, 0);
        assertEquals("Shared preference did exist", 0, value);

        value = mExcludeSharedPref.getInt(SHARED_PREF_KEY2, 0);
        assertEquals("Shared preference did exist", 0, value);
    }

    private void checkIncludeSharedPrefExist() {
        int value = mIncludeSharedPref.getInt(SHARED_PREF_KEY1, 0);
        assertEquals("Shared preference did not exist", SHARED_PREF_VALUE1, value);
    }

    private void checkExcludeSharedPrefDoNotExist() {
        int value = mExcludeSharedPref.getInt(SHARED_PREF_KEY2, 0);
        assertEquals("Shared preference did exist", 0, value);
    }
}
