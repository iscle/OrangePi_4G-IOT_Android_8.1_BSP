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

package com.android.documentsui.prefs;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.fail;

import android.content.SharedPreferences;
import android.content.SharedPreferences.Editor;
import android.support.test.InstrumentationRegistry;
import android.support.test.filters.SmallTest;
import android.support.test.runner.AndroidJUnit4;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.IOException;
import java.util.Map;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Set;

@RunWith(AndroidJUnit4.class)
@SmallTest
public class PrefsBackupHelperTest {

    private static final String LOCAL_PREFERENCE_1 = "rootViewMode-validPreference1";
    private static final String LOCAL_PREFERENCE_2 = "rootViewMode-validPreference2";
    private static final String SCOPED_PREFERENCE = "includeDeviceRoot";
    private static final String NON_BACKUP_PREFERENCE = "notBackup-invalidPreference";

    private SharedPreferences mDefaultPrefs;
    private SharedPreferences mBackupPrefs;
    private PrefsBackupHelper mPrefsBackupHelper;

    @Before
    public void setUp() {
        mDefaultPrefs = InstrumentationRegistry.getContext().getSharedPreferences("prefs1", 0);
        mBackupPrefs = InstrumentationRegistry.getContext().getSharedPreferences("prefs2", 0);
        mPrefsBackupHelper = new PrefsBackupHelper(mDefaultPrefs);
    }

    @Test
    public void testPrepareBackupFile_BackupLocalPreferences() {
        mDefaultPrefs.edit().putInt(LOCAL_PREFERENCE_1, 1).commit();

        mPrefsBackupHelper.getBackupPreferences(mBackupPrefs);

        assertEquals(mBackupPrefs.getInt(LOCAL_PREFERENCE_1, 0), 1);
    }

    @Test
    public void testPrepareBackupFile_BackupScopedPreferences() {
        mDefaultPrefs.edit().putBoolean(SCOPED_PREFERENCE, true).commit();

        mPrefsBackupHelper.getBackupPreferences(mBackupPrefs);

        assertEquals(mBackupPrefs.getBoolean(SCOPED_PREFERENCE, false), true);
    }

    @Test
    public void testPrepareBackupFile_BackupNotInterestedPreferences() {
        mDefaultPrefs.edit().putBoolean(NON_BACKUP_PREFERENCE, true).commit();

        mPrefsBackupHelper.getBackupPreferences(mBackupPrefs);

        assertFalse(mBackupPrefs.contains(NON_BACKUP_PREFERENCE));
    }

    @Test
    public void testPrepareBackupFile_BackupUnexpectedType() throws Exception {
        // Currently only Integer and Boolean type are supported.
        mDefaultPrefs.edit().putString(LOCAL_PREFERENCE_1, "String is not accepted").commit();

        try {
            mPrefsBackupHelper.getBackupPreferences(mBackupPrefs);
            fail();
        } catch(IllegalArgumentException e) {

        } finally {
            assertFalse(mBackupPrefs.contains(LOCAL_PREFERENCE_1));
        }
    }

    @Test
    public void testRestorePreferences_RestoreLocalPreferences() {
        mBackupPrefs.edit().putInt(LOCAL_PREFERENCE_1, 1).commit();

        mPrefsBackupHelper.putBackupPreferences(mBackupPrefs);

        assertEquals(mDefaultPrefs.getInt(LOCAL_PREFERENCE_1, 0), 1);
    }

    @Test
    public void testRestorePreferences_RestoreScopedPreferences() {
        mBackupPrefs.edit().putBoolean(SCOPED_PREFERENCE, true).commit();

        mPrefsBackupHelper.putBackupPreferences(mBackupPrefs);

        assertEquals(mDefaultPrefs.getBoolean(SCOPED_PREFERENCE, false), true);
    }

    @Test
    public void testEndToEnd() {
        // Simulating an end to end backup & restore process. At the begining, all preferences are
        // stored in the default shared preferences file, includes preferences that we don't want
        // to backup.
        //
        // On backup, we copy all preferences that we want to backup to the backup shared
        // preferences file, and then backup that single file.
        //
        // On restore, we restore the backup file first, and then copy all preferences in the backup
        // file to the app's default shared preferences file.

        SharedPreferences.Editor editor = mDefaultPrefs.edit();

        // Set preferences to the default file, includes preferences that are not backed up.
        editor.putInt(LOCAL_PREFERENCE_1, 1);
        editor.putInt(LOCAL_PREFERENCE_2, 2);
        editor.putBoolean(SCOPED_PREFERENCE, true);
        editor.putBoolean(NON_BACKUP_PREFERENCE, true);
        editor.commit();

        // Write all backed up preferences to backup shared preferences file.
        mPrefsBackupHelper.getBackupPreferences(mBackupPrefs);

        // Assume we are doing backup to the backup file.

        // Clear all preferences in default shared preferences file.
        editor.clear().commit();

        // Assume we are doing restore to the backup file.

        // Copy all backuped preferences to default shared preferences file.
        mPrefsBackupHelper.putBackupPreferences(mBackupPrefs);

        // Check all preferences are correctly restored.
        assertEquals(mDefaultPrefs.getInt(LOCAL_PREFERENCE_1, 0), 1);
        assertEquals(mDefaultPrefs.getInt(LOCAL_PREFERENCE_2, 0), 2);
        assertEquals(mDefaultPrefs.getBoolean(SCOPED_PREFERENCE, false), true);
        assertFalse(mDefaultPrefs.contains(NON_BACKUP_PREFERENCE));
    }

    @Test
    public void testPreferenceTypesSupport() {
        Map<String, Object> map = new HashMap<String, Object>();
        map.put("int", (Integer) 1);
        map.put("float", (Float) 0.1f);
        map.put("long", (Long) 10000000000l);
        map.put("boolean", true);
        map.put("String", "String");
        Set<String> stringSet = new HashSet<String>();
        stringSet.add("string1");
        stringSet.add("string2");
        map.put("StringSet", stringSet);

        // SharedPreferences accept Integer, Float, Long, Boolean, String, Set<String> types.
        // Currently in DocumentsUI, only Integer and Boolean preferences are backed up.
        for (Map.Entry<String, ?> entry : map.entrySet()) {
            String key = entry.getKey();
            Object value = entry.getValue();
            Editor editor = mDefaultPrefs.edit().clear();
            if (value instanceof Integer) {
                mPrefsBackupHelper.setPreference(editor, entry);
                editor.apply();
                assertEquals(mDefaultPrefs.getInt("int", 0), 1);
            } else if(value instanceof Boolean) {
                mPrefsBackupHelper.setPreference(editor, entry);
                editor.apply();
                assertEquals(mDefaultPrefs.getBoolean("boolean", false), true);
            } else {
                try {
                    mPrefsBackupHelper.setPreference(editor, entry);
                    fail();
                } catch(IllegalArgumentException e) {

                } finally {
                    editor.apply();
                    assertFalse(mDefaultPrefs.contains(key));
                }
            }
        }
    }
}
