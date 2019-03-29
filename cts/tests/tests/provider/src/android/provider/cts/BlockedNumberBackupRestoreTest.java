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

package android.provider.cts;

import android.app.UiAutomation;
import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.content.pm.PackageManager;
import android.provider.BlockedNumberContract;
import android.telecom.Log;

/**
 * CTS tests for backup and restore of blocked numbers using local transport.
 */
// To run the tests in this file w/o running all the cts tests:
// make cts
// cts-tradefed
// run cts -m CtsProviderTestCases --test android.provider.cts.BlockedNumberBackupRestoreTest
public class BlockedNumberBackupRestoreTest extends TestCaseThatRunsIfTelephonyIsEnabled {
    private static final String TAG = "BlockedNumberBackupRestoreTest";
    private static final String LOCAL_BACKUP_COMPONENT =
            "android/com.android.internal.backup.LocalTransport";
    private static final String BLOCKED_NUMBERS_PROVIDER_PACKAGE =
            "com.android.providers.blockednumber";

    private ContentResolver mContentResolver;
    private Context mContext;
    private UiAutomation mUiAutomation;
    private String mOldTransport;
    private boolean mOldBackupEnabled;
    private boolean mHasFeature;

    @Override
    protected void setUp() throws Exception {
        super.setUp();

        mContext = getInstrumentation().getContext();
        mContentResolver = mContext.getContentResolver();
        mUiAutomation = getInstrumentation().getUiAutomation();

        mHasFeature = isFeatureSupported();

        if (mHasFeature) {
            ProviderTestUtils.setDefaultSmsApp(true, mContext.getPackageName(), mUiAutomation);

            mOldTransport = ProviderTestUtils.setBackupTransport(
                    LOCAL_BACKUP_COMPONENT, mUiAutomation);
            mOldBackupEnabled = ProviderTestUtils.setBackupEnabled(true, mUiAutomation);
            clearBlockedNumbers();
            wipeBackup();
        }
    }

    @Override
    protected void tearDown() throws Exception {
        if (mHasFeature) {
            wipeBackup();
            clearBlockedNumbers();
            ProviderTestUtils.setBackupEnabled(mOldBackupEnabled, mUiAutomation);
            ProviderTestUtils.setBackupTransport(mOldTransport, mUiAutomation);
            ProviderTestUtils.setDefaultSmsApp(false, mContext.getPackageName(), mUiAutomation);
        }

        super.tearDown();
    }

    public void testBackupAndRestoreForSingleNumber() throws Exception {
        if (!mHasFeature) {
            Log.i(TAG, "skipping BlockedNumberBackupRestoreTest");
            return;
        }

        Log.i(TAG, "Adding blocked numbers.");
        insertBlockedNumber("123456789");

        Log.i(TAG, "Running backup.");
        runBackup();

        Log.i(TAG, "Clearing blocked numbers.");
        clearBlockedNumbers();
        verifyBlockedNumbers();

        Log.i(TAG, "Restoring blocked numbers.");
        runRestore();
        verifyBlockedNumbers("123456789");
    }

    public void testBackupAndRestoreWithDeletion() throws Exception {
        if (!mHasFeature) {
            Log.i(TAG, "skipping BlockedNumberBackupRestoreTest");
            return;
        }

        Log.i(TAG, "Adding blocked numbers.");
        insertBlockedNumber("123456789");
        insertBlockedNumber("223456789");
        insertBlockedNumber("323456789");

        Log.i(TAG, "Running backup.");
        runBackup();

        Log.i(TAG, "Deleting blocked number.");
        deleteNumber("123456789");
        verifyBlockedNumbers("223456789", "323456789");

        Log.i(TAG, "Running backup.");
        runBackup();

        Log.i(TAG, "Clearing blocked numbers.");
        clearBlockedNumbers();
        verifyBlockedNumbers();

        Log.i(TAG, "Restoring blocked numbers.");
        runRestore();
        verifyBlockedNumbers("223456789", "323456789");
    }

    private boolean isFeatureSupported() throws Exception {
        return ProviderTestUtils.hasBackupTransport(LOCAL_BACKUP_COMPONENT, mUiAutomation)
                && mContext.getPackageManager().hasSystemFeature(PackageManager.FEATURE_TELEPHONY);
    }

    private void insertBlockedNumber(String number) {
        ContentValues cv = new ContentValues();
        cv.put(BlockedNumberContract.BlockedNumbers.COLUMN_ORIGINAL_NUMBER, number);
        mContentResolver.insert(BlockedNumberContract.BlockedNumbers.CONTENT_URI, cv);
    }

    private void deleteNumber(String number) {
        assertEquals(1,
                mContentResolver.delete(
                        BlockedNumberContract.BlockedNumbers.CONTENT_URI,
                        BlockedNumberContract.BlockedNumbers.COLUMN_ORIGINAL_NUMBER + "= ?",
                        new String[] {number}));
    }

    private void verifyBlockedNumbers(String ... blockedNumbers) {
        assertEquals(blockedNumbers.length,
                mContentResolver.query(
                        BlockedNumberContract.BlockedNumbers.CONTENT_URI, null, null, null, null)
                        .getCount());
        for (String blockedNumber : blockedNumbers) {
            assertTrue(BlockedNumberContract.isBlocked(mContext, blockedNumber));
        }
    }

    private void clearBlockedNumbers() {
        mContentResolver.delete(BlockedNumberContract.BlockedNumbers.CONTENT_URI, null, null);
    }

    private void runBackup() throws Exception {
        ProviderTestUtils.runBackup(BLOCKED_NUMBERS_PROVIDER_PACKAGE, mUiAutomation);
    }

    private void runRestore() throws Exception {
        ProviderTestUtils.runRestore(BLOCKED_NUMBERS_PROVIDER_PACKAGE, mUiAutomation);
    }

    private void wipeBackup() throws Exception {
        ProviderTestUtils.wipeBackup(LOCAL_BACKUP_COMPONENT, BLOCKED_NUMBERS_PROVIDER_PACKAGE,
                mUiAutomation);
    }
}
