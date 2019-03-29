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

package android.cts.backup.sharedprefrestoreapp;

import android.app.backup.BackupAgentHelper;
import android.app.backup.SharedPreferencesBackupHelper;

public class SharedPreferencesBackupAgent extends BackupAgentHelper {

    private static final String KEY_BACKUP_TEST_PREFS_PREFIX = "backup-test-prefs";
    private static final String TEST_PREFS_1 = "test-prefs-1";

    @Override
    public void onCreate() {
        super.onCreate();
        addHelper(KEY_BACKUP_TEST_PREFS_PREFIX,
                new SharedPreferencesBackupHelper(this, TEST_PREFS_1));
    }
}
