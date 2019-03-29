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

package android.cts.backup.keyvaluerestoreapp;

import static android.content.Context.MODE_PRIVATE;
import static android.support.test.InstrumentationRegistry.getTargetContext;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import android.app.backup.BackupManager;
import android.app.backup.FileBackupHelper;
import android.app.backup.SharedPreferencesBackupHelper;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.support.test.runner.AndroidJUnit4;
import android.util.Log;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.PrintWriter;
import java.util.Scanner;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/**
 * Device side routines to be invoked by the host side KeyValueBackupRestoreHostSideTest. These
 * are not designed to be called in any other way, as they rely on state set up by the host side
 * test.
 *
 * Some tests invoked by KeyValueBackupRestoreHostSideTest#testSharedPreferencesRestore() are
 * interacting with SharedPrefsRestoreTestActivity from another package.
 *
 */
@RunWith(AndroidJUnit4.class)
public class KeyValueBackupRestoreTest {
    private static final String TAG = "KeyValueBackupRestore";

    // Names and values of the test files for
    // KeyValueBackupRestoreHostSideTest#testKeyValueBackupAndRestore()
    private static final String TEST_FILE_1 = "test-file-1";
    private static final String TEST_FILE_1_DATA = "test-file-1-data";

    private static final String TEST_FILE_2 = "test-file-2";
    private static final String TEST_FILE_2_DATA = "test-file-2-data";

    private static final String TEST_PREFS_1 = "test-prefs-1";
    private static final String INT_PREF = "int-pref";
    private static final int INT_PREF_VALUE = 111;
    private static final String BOOL_PREF = "bool-pref";
    private static final boolean BOOL_PREF_VALUE = true;

    private static final String TEST_PREFS_2 = "test-prefs-2";
    private static final String FLOAT_PREF = "float-pref";
    private static final float FLOAT_PREF_VALUE = 0.12345f;
    private static final String LONG_PREF = "long-pref";
    private static final long LONG_PREF_VALUE = 12345L;
    private static final String STRING_PREF = "string-pref";
    private static final String STRING_PREF_VALUE = "string-pref-value";

    private static final int DEFAULT_INT_VALUE = 0;
    private static final boolean DEFAULT_BOOL_VALUE = false;
    private static final float DEFAULT_FLOAT_VALUE = 0.0f;
    private static final long DEFAULT_LONG_VALUE = 0L;
    private static final String DEFAULT_STRING_VALUE = null;
    private static final String DEFAULT_FILE_CONTENT = "";


    /** Package name of the test app for
     * KeyValueBackupRestoreHostSideTest#testSharedPreferencesRestore() */
    private static final String SHARED_PREFERENCES_RESTORE_PACKAGE_NAME =
            "android.cts.backup.sharedprefrestoreapp";

    /** Test activity for KeyValueBackupRestoreHostSideTest#testSharedPreferencesRestore() */
    private static final String SHARED_PREFERENCES_RESTORE_ACTIVITY_NAME =
            SHARED_PREFERENCES_RESTORE_PACKAGE_NAME + ".SharedPrefsRestoreTestActivity";

    // Shared prefs test activity actions
    private static final String INIT_ACTION = "android.backup.cts.backuprestore.INIT";
    private static final String UPDATE_ACTION = "android.backup.cts.backuprestore.UPDATE";
    private static final String TEST_ACTION = "android.backup.cts.backuprestore.TEST";

    // Action for returning the result of shared preference activity's operations
    private static final String RESULT_ACTION = "android.backup.cts.backuprestore.RESULT";
    private static final String EXTRA_SUCCESS = "EXTRA_SUCCESS";

    private static final int ACTIVITY_TEST_TIMEOUT_MS = 5000;

    private Context mContext;

    private int mIntValue;
    private boolean mBoolValue;
    private float mFloatValue;
    private long mLongValue;
    private String mStringValue;
    private String mFileContent1;
    private String mFileContent2;

    private boolean mSharedPrefTestSuccess;
    private CountDownLatch mLatch;
    private Intent mSharedPrefActivityIntent;

    private final BroadcastReceiver mSharedPrefencesReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            Log.i(TAG, "Received shared preference activity result broadcast");
            mSharedPrefTestSuccess = intent.getBooleanExtra(EXTRA_SUCCESS, false);
            mLatch.countDown();
        }
    };

    @Before
    public void setUp() {
        Log.i(TAG, "set up");

        mContext = getTargetContext();
        mLatch = new CountDownLatch(1);
        mSharedPrefTestSuccess = false;
        mSharedPrefActivityIntent = new Intent()
                .setClassName(SHARED_PREFERENCES_RESTORE_PACKAGE_NAME,
                        SHARED_PREFERENCES_RESTORE_ACTIVITY_NAME);
        mContext.registerReceiver(mSharedPrefencesReceiver, new IntentFilter(RESULT_ACTION));
    }

    @After
    public void tearDown() {
        mContext.unregisterReceiver(mSharedPrefencesReceiver);
    }

    @Test
    public void saveSharedPreferencesAndNotifyBackupManager() throws Exception {
        saveSharedPreferencesValues();

        //Let BackupManager know that the data has changed
        BackupManager backupManager = new BackupManager(mContext);
        backupManager.dataChanged();
    }

    @Test
    public void checkSharedPrefIsEmpty() throws Exception {
        readSharedPrefValues();
        assertEquals(DEFAULT_INT_VALUE, mIntValue);
        assertEquals(DEFAULT_BOOL_VALUE, mBoolValue);
        assertEquals(DEFAULT_FLOAT_VALUE, mFloatValue, 0.001f);
        assertEquals(DEFAULT_LONG_VALUE, mLongValue);
        assertEquals(DEFAULT_STRING_VALUE, mStringValue);
        assertEquals(DEFAULT_FILE_CONTENT, mFileContent1);
        assertEquals(DEFAULT_FILE_CONTENT, mFileContent2);
    }

    @Test
    public void checkSharedPreferencesAreRestored() throws Exception {
        readSharedPrefValues();
        assertEquals(INT_PREF_VALUE, mIntValue);
        assertEquals(BOOL_PREF_VALUE, mBoolValue);
        assertEquals(FLOAT_PREF_VALUE, mFloatValue, 0.001f);
        assertEquals(LONG_PREF_VALUE, mLongValue);
        assertEquals(STRING_PREF_VALUE, mStringValue);
        assertEquals(TEST_FILE_1_DATA, mFileContent1);
        assertEquals(TEST_FILE_2_DATA, mFileContent2);
    }

    @Test
    public void launchSharedPrefActivity() throws Exception {
        mContext.startActivity(mSharedPrefActivityIntent.setAction(INIT_ACTION));

        assertTrue("Activity init timed out",
                mLatch.await(ACTIVITY_TEST_TIMEOUT_MS, TimeUnit.MILLISECONDS));
        assertTrue("Saving shared preferences didn't succeed", mSharedPrefTestSuccess);
    }

    @Test
    public void updateSharedPrefActivity() throws Exception {
        mContext.startActivity(mSharedPrefActivityIntent.setAction(UPDATE_ACTION));

        assertTrue("Activity launch timed out",
                mLatch.await(ACTIVITY_TEST_TIMEOUT_MS, TimeUnit.MILLISECONDS));
        assertTrue("Saving shared preferences didn't succeed", mSharedPrefTestSuccess);
    }

    @Test
    public void checkSharedPrefActivity() throws Exception {
        mContext.startActivity(mSharedPrefActivityIntent.setAction(TEST_ACTION));

        assertTrue("Activity test timed out",
                mLatch.await(ACTIVITY_TEST_TIMEOUT_MS, TimeUnit.MILLISECONDS));
        assertTrue("Shared preference value wasn't updated from the restore set",
                mSharedPrefTestSuccess);
    }

    /** Saves predefined constant values to shared preferences and files. */
    private void saveSharedPreferencesValues() throws FileNotFoundException {
        SharedPreferences prefs = mContext.getSharedPreferences(TEST_PREFS_1, MODE_PRIVATE);
        SharedPreferences.Editor editor = prefs.edit();
        editor.putInt(INT_PREF, INT_PREF_VALUE);
        editor.putBoolean(BOOL_PREF, BOOL_PREF_VALUE);
        assertTrue("Error committing shared preference 1 value", editor.commit());

        prefs = mContext.getSharedPreferences(TEST_PREFS_2, MODE_PRIVATE);
        editor = prefs.edit();
        editor.putFloat(FLOAT_PREF, FLOAT_PREF_VALUE);
        editor.putLong(LONG_PREF, LONG_PREF_VALUE);
        editor.putString(STRING_PREF, STRING_PREF_VALUE);
        assertTrue("Error committing shared preference 2 value", editor.commit());

        File file = new File(mContext.getFilesDir(), TEST_FILE_1);
        PrintWriter writer = new PrintWriter(file);
        writer.write(TEST_FILE_1_DATA);
        writer.close();
        assertTrue("Error writing file 1 data", file.exists());

        file = new File(mContext.getFilesDir(), TEST_FILE_2);
        writer = new PrintWriter(file);
        writer.write(TEST_FILE_2_DATA);
        writer.close();
        assertTrue("Error writing file 2 data", file.exists());
    }

    private void readSharedPrefValues() throws InterruptedException {
        SharedPreferences prefs = mContext.getSharedPreferences(TEST_PREFS_1, MODE_PRIVATE);
        mIntValue = prefs.getInt(INT_PREF, DEFAULT_INT_VALUE);
        mBoolValue = prefs.getBoolean(BOOL_PREF, DEFAULT_BOOL_VALUE);

        prefs = mContext.getSharedPreferences(TEST_PREFS_2, MODE_PRIVATE);
        mFloatValue = prefs.getFloat(FLOAT_PREF, DEFAULT_FLOAT_VALUE);
        mLongValue = prefs.getLong(LONG_PREF, DEFAULT_LONG_VALUE);
        mStringValue = prefs.getString(STRING_PREF, DEFAULT_STRING_VALUE);

        mFileContent1 = readFileContent(TEST_FILE_1);
        mFileContent2 = readFileContent(TEST_FILE_2);

        Log.i(TAG, INT_PREF + ":" + mIntValue + "\n"
                + BOOL_PREF + ":" + mBoolValue + "\n"
                + FLOAT_PREF + ":" + mFloatValue + "\n"
                + LONG_PREF + ":" + mLongValue + "\n"
                + STRING_PREF + ":" + mStringValue + "\n"
                + TEST_FILE_1 + ":" + mFileContent1 + "\n"
                + TEST_FILE_2 + ":" + mFileContent2 + "\n");
    }

    private String readFileContent(String fileName) {
        StringBuilder contents = new StringBuilder();
        Scanner scanner = null;
        try {
            scanner = new Scanner(new File(mContext.getFilesDir(), fileName));
            while (scanner.hasNext()) {
                contents.append(scanner.nextLine());
            }
            scanner.close();
        } catch (FileNotFoundException e) {
            Log.i(TAG, "Couldn't find test file but this may be fine...");
        } finally {
            if (scanner != null) {
                scanner.close();
            }
        }
        return contents.toString();
    }

    public static SharedPreferencesBackupHelper getSharedPreferencesBackupHelper(Context context) {
        return new SharedPreferencesBackupHelper(context, TEST_PREFS_1, TEST_PREFS_2);
    }

    public static FileBackupHelper getFileBackupHelper(Context context) {
        return new FileBackupHelper(context, TEST_FILE_1, TEST_FILE_2);
    }
}
