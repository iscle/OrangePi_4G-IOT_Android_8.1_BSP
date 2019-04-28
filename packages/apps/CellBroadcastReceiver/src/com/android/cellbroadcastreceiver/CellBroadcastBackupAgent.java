/*
 * Copyright (C) 2015 The Android Open Source Project
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

package com.android.cellbroadcastreceiver;

import android.app.backup.BackupAgentHelper;
import android.app.backup.SharedPreferencesBackupHelper;
import android.content.Intent;
import android.os.UserHandle;
import android.util.Log;

/**
 * The CellBroadcast backup agent backs up the shared
 * preferences settings of the CellBroadcastReceiver App. Right
 * now it backs up the whole shared preference file. This can be
 * modified in the future to accommodate partial backup.
 */
public class CellBroadcastBackupAgent extends BackupAgentHelper
{
    private static final String TAG = "CBBackupAgent";

    private static final String SHARED_KEY = "shared_pref";

    private static final String SHARED_PREFS_NAME = "com.android.cellbroadcastreceiver_preferences";

    @Override
    public void onCreate() {
        Log.d(TAG, "onCreate");
        addHelper(SHARED_KEY, new SharedPreferencesBackupHelper(this, SHARED_PREFS_NAME));
    }

    @Override
    public void onRestoreFinished() {
        Log.d(TAG, "Restore finished.");
        Intent intent = new Intent(CellBroadcastReceiver.CELLBROADCAST_START_CONFIG_ACTION);

        // Cell broadcast was configured during boot up before the shared preference is restored,
        // we need to re-configure it.
        sendBroadcastAsUser(intent, UserHandle.SYSTEM);
    }
}

