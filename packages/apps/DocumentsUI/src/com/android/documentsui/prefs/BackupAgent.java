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

package com.android.documentsui.prefs;

import android.app.backup.BackupAgentHelper;
import android.app.backup.BackupDataInput;
import android.app.backup.BackupDataOutput;
import android.app.backup.SharedPreferencesBackupHelper;
import android.content.SharedPreferences;
import android.os.ParcelFileDescriptor;
import android.support.annotation.VisibleForTesting;

import java.io.IOException;

/**
 * Provides glue between backup infrastructure and PrefsBackupHelper (which contains the core logic
 * for retrieving and restoring settings).
 *
 * When doing backup & restore, we create and add a {@link SharedPreferencesBackupHelper} for our
 * backup preferences file in {@link #onCreate}, and populate the backup preferences file in
 * {@link #onBackup} and {@link #onRestore}. Then {@link BackupAgentHelper#onBackup} and
 * {@link BackupAgentHelper#onRestore} will take care of the rest of the work. See external
 * documentation below.
 *
 * https://developer.android.com/guide/topics/data/keyvaluebackup.html#BackupAgentHelper
 */
public class BackupAgent extends BackupAgentHelper {

    /**
     * Name of the shared preferences file used by BackupAgent. Should only be used in
     * this class.
     *
     * @see #onBackup(ParcelFileDescriptor, BackupDataOutput, ParcelFileDescriptor)
     * @see #onRestore(BackupDataInput, int, ParcelFileDescriptor)
     */
    private static final String BACKUP_PREFS = "documentsui_backup_prefs";

    /**
     * An arbitrary string used by the BackupHelper.
     *
     * BackupAgentHelper works with BackupHelper. When adding a BackupHelper in #onCreate,
     * it requires a "key". This string is that "key".
     *
     * https://developer.android.com/guide/topics/data/keyvaluebackup.html#BackupAgentHelper
     * See "Backing up SharedPreference" for the purpose of this string.
     *
     * @see #onCreate()
     */
    private static final String BACKUP_HELPER_KEY = "DOCUMENTSUI_BACKUP_HELPER_KEY";

    private PrefsBackupHelper mPrefsBackupHelper;
    private SharedPreferences mBackupPreferences;

    @Override
    public void onCreate() {
        addHelper(BACKUP_HELPER_KEY, new SharedPreferencesBackupHelper(this, BACKUP_PREFS));
        mPrefsBackupHelper = new PrefsBackupHelper(this);
        mBackupPreferences = getSharedPreferences(BACKUP_PREFS, MODE_PRIVATE);
    }

    @Override
    public void onBackup(ParcelFileDescriptor oldState, BackupDataOutput data,
            ParcelFileDescriptor newState) throws IOException {
        mPrefsBackupHelper.getBackupPreferences(mBackupPreferences);
        super.onBackup(oldState, data, newState);
        mBackupPreferences.edit().clear().apply();
    }

    @Override
    public void onRestore(BackupDataInput data, int appVersionCode, ParcelFileDescriptor newState)
            throws IOException {
        // TODO: refresh the UI after restore finished. Currently the restore for system apps only
        // happens during SUW, at which time user haven't open the app. However the restore time may
        // change in ODR. Once it happens, we may need to refresh the UI after restore finished.
        super.onRestore(data, appVersionCode, newState);
        mPrefsBackupHelper.putBackupPreferences(mBackupPreferences);
        mBackupPreferences.edit().clear().apply();
    }

}
