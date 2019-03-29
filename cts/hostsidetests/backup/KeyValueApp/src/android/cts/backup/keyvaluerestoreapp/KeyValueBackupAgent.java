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

import android.app.backup.BackupAgentHelper;

public class KeyValueBackupAgent extends BackupAgentHelper {

    private static final String KEY_BACKUP_TEST_PREFS_PREFIX = "backup-test-prefs";
    private static final String KEY_BACKUP_TEST_FILES_PREFIX = "backup-test-files";

    @Override
    public void onCreate() {
        super.onCreate();
        addHelper(KEY_BACKUP_TEST_PREFS_PREFIX,
                KeyValueBackupRestoreTest.getSharedPreferencesBackupHelper(this));
        addHelper(KEY_BACKUP_TEST_FILES_PREFIX,
                KeyValueBackupRestoreTest.getFileBackupHelper(this));
    }
}
