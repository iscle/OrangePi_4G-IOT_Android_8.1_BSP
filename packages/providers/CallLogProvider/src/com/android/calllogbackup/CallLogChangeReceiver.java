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
 * limitations under the License
 */

package com.android.calllogbackup;

import android.app.backup.BackupManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.provider.Settings;
import android.util.Log;

/**
 * Call Log Change Broadcast Receiver. Receives an intent when the call log provider changes
 * so that it triggers backup accordingly.
 */
public class CallLogChangeReceiver extends BroadcastReceiver {
    private static final String TAG = "CallLogChangeReceiver";
    private static final boolean VDBG = Log.isLoggable(TAG, Log.VERBOSE);
    private static final String ACTION_CALL_LOG_CHANGE =
            "com.android.internal.action.CALL_LOG_CHANGE";

    /** ${inheritDoc} */
    @Override
    public void onReceive(Context context, Intent intent) {
        if (ACTION_CALL_LOG_CHANGE.equals(intent.getAction())) {

            if (CallLogBackupAgent.shouldPreventBackup(context)) {
                // User is not full-backup-data aware so we skip calllog backup until they are.
                if (VDBG) {
                    Log.v(TAG, "Skipping call log backup due to lack of full-data check.");
                }
            } else {
                BackupManager bm = new BackupManager(context);
                bm.dataChanged();
            }
        }
    }

}
