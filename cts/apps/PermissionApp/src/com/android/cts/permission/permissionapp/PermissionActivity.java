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

package com.android.cts.permissionapp;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.util.Log;

import java.lang.Override;

/**
 * A simple activity that requests permissions and returns the result.
 */
public class PermissionActivity extends Activity {
    private static final String TAG = "PermissionActivity";

    private static final String ACTION_CHECK_HAS_PERMISSION
            = "com.android.cts.permission.action.CHECK_HAS_PERMISSION";
    private static final String ACTION_REQUEST_PERMISSION
            = "com.android.cts.permission.action.REQUEST_PERMISSION";
    private static final String ACTION_PERMISSION_RESULT
            = "com.android.cts.permission.action.PERMISSION_RESULT";
    private static final String EXTRA_PERMISSION
            = "com.android.cts.permission.extra.PERMISSION";
    private static final String EXTRA_GRANT_STATE
            = "com.android.cts.permission.extra.GRANT_STATE";
    private static final int PERMISSION_ERROR = -2;
    private static final int PERMISSIONS_REQUEST_CODE = 100;

    private String mPermission;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        final Intent received = getIntent();
        Log.d(TAG, "Started with " + received);

        final String action = received.getAction();
        mPermission = received.getStringExtra(EXTRA_PERMISSION);
        if (ACTION_REQUEST_PERMISSION.equals(action)) {
            Log.d(TAG, "Requesting permission " + mPermission);
            requestPermissions(new String[] {mPermission}, PERMISSIONS_REQUEST_CODE);
        } else if (ACTION_CHECK_HAS_PERMISSION.equals(action)) {
            Log.d(TAG, "Checking permission " + mPermission);
            sendResultBroadcast(checkSelfPermission(mPermission));
        } else {
            Log.w(TAG, "Unknown intent received: " + received);
            finish();
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions,
            int[] grantResults) {
        if (requestCode != PERMISSIONS_REQUEST_CODE ||
                permissions.length != 1 ||
                !permissions[0].equals(mPermission)) {
            Log.d(TAG, "Received wrong permissions result");
            sendResultBroadcast(PERMISSION_ERROR);
        } else {
            Log.d(TAG, "Received valid permission result: " + grantResults[0]);
            sendResultBroadcast(grantResults[0]);
        }
    }

    private void sendResultBroadcast(int result) {
        Log.d(TAG, "Sending result broadcast: " + result);
        Intent broadcast = new Intent(ACTION_PERMISSION_RESULT);
        broadcast.putExtra(EXTRA_GRANT_STATE, result);
        sendBroadcast(broadcast);
        finish();
    }
}
