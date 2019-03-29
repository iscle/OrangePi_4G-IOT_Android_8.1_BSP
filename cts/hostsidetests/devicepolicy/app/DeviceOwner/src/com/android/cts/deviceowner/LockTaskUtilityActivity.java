/*
 * Copyright (C) 2014 The Android Open Source Project
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

package com.android.cts.deviceowner;

import android.app.Activity;
import android.content.Intent;
import android.util.Log;

public class LockTaskUtilityActivity extends Activity {
    private static final String TAG = "LockTaskUtilityActivity";

    public static final String START_LOCK_TASK = "startLockTask";
    public static final String STOP_LOCK_TASK = "stopLockTask";
    public static final String START_ACTIVITY = "startActivity";
    public static final String FINISH = "finish";

    public static final String CREATE_ACTION = "com.android.cts.deviceowner.LOCK_TASK_CREATE";
    public static final String DESTROY_ACTION = "com.android.cts.deviceowner.LOCK_TASK_DESTROY";
    public static final String PAUSE_ACTION = "com.android.cts.deviceowner.LOCK_TASK_PAUSE";
    public static final String RESUME_ACTION = "com.android.cts.deviceowner.LOCK_TASK_RESUME";
    public static final String INTENT_ACTION = "com.android.cts.deviceowner.LOCK_TASK_INTENT";

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        handleIntent(intent);
    }

    @Override
    protected void onCreate(android.os.Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        sendLocalBroadcast(new Intent(CREATE_ACTION));
        handleIntent(getIntent());
    }

    @Override
    protected void onDestroy() {
        sendLocalBroadcast(new Intent(DESTROY_ACTION));
        super.onDestroy();
    }

    @Override
    protected void onResume() {
        sendLocalBroadcast(new Intent(RESUME_ACTION));
        super.onResume();
    }

    @Override
    protected void onPause() {
        sendLocalBroadcast(new Intent(PAUSE_ACTION));
        super.onPause();
    }

    private void handleIntent(Intent intent) {
        if (intent.getBooleanExtra(START_LOCK_TASK, false)) {
            startLockTask();
        }
        if (intent.getBooleanExtra(STOP_LOCK_TASK, false)) {
            stopLockTask();
        }
        if (intent.hasExtra(START_ACTIVITY)) {
            Intent i = intent.getParcelableExtra(START_ACTIVITY);
            startActivity(i);
        }
        if (intent.getBooleanExtra(FINISH, false)) {
            finish();
        }
        sendLocalBroadcast(new Intent(INTENT_ACTION));
    }

    private void sendLocalBroadcast(Intent intent) {
        Log.d(TAG, "sendLocalBroadcast: " + intent.getAction());
        intent.setPackage(this.getPackageName());
        intent.setFlags(Intent.FLAG_RECEIVER_FOREGROUND);
        sendBroadcast(intent);
    }
}
