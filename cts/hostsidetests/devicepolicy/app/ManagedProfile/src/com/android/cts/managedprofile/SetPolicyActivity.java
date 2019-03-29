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
package com.android.cts.managedprofile;

import android.app.Activity;
import android.app.admin.DevicePolicyManager;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.os.Process;
import android.util.Log;

import com.android.cts.managedprofile.BaseManagedProfileTest;

/**
 * Simple activity that adds or clears a user restriction depending on the value of the extras.
 */
public class SetPolicyActivity extends Activity {

    private static final String TAG = SetPolicyActivity.class.getName();

    private static final String EXTRA_RESTRICTION_KEY = "extra-restriction-key";
    private static final String EXTRA_PACKAGE_NAME = "extra-package-name";
    private static final String EXTRA_COMMAND = "extra-command";

    private static final String ADD_RESTRICTION_COMMAND = "add-restriction";
    private static final String CLEAR_RESTRICTION_COMMAND = "clear-restriction";
    private static final String ADD_CROSS_PROFILE_WIDGET_COMMAND = "add-cross-profile-widget";
    private static final String REMOVE_CROSS_PROFILE_WIDGET_COMMAND = "remove-cross-profile-widget";

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        handleIntent(getIntent());
    }

    // Overriding this method in case another intent is sent to this activity before finish()
    @Override
    public void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        handleIntent(intent);
    }

    @Override
    public void onPause() {
        super.onPause();
        // Calling finish() here because doing it in onCreate(), onStart() or onResume() makes
        // "adb shell am start" timeout if using the -W option.
        finish();
    }

    private void handleIntent(Intent intent) {
        DevicePolicyManager dpm = (DevicePolicyManager)
                getSystemService(Context.DEVICE_POLICY_SERVICE);
        String command = intent.getStringExtra(EXTRA_COMMAND);
        Log.i(TAG, "Command: \"" + command);

        if (ADD_RESTRICTION_COMMAND.equals(command)) {
            String restrictionKey = intent.getStringExtra(EXTRA_RESTRICTION_KEY);
            dpm.addUserRestriction(BaseManagedProfileTest.ADMIN_RECEIVER_COMPONENT, restrictionKey);
            Log.i(TAG, "Added user restriction " + restrictionKey
                    + " for user " + Process.myUserHandle());
        } else if (CLEAR_RESTRICTION_COMMAND.equals(command)) {
            String restrictionKey = intent.getStringExtra(EXTRA_RESTRICTION_KEY);
            dpm.clearUserRestriction(
                    BaseManagedProfileTest.ADMIN_RECEIVER_COMPONENT, restrictionKey);
            Log.i(TAG, "Cleared user restriction " + restrictionKey
                    + " for user " + Process.myUserHandle());
        } else if (ADD_CROSS_PROFILE_WIDGET_COMMAND.equals(command)) {
            String packageName = intent.getStringExtra(EXTRA_PACKAGE_NAME);
            dpm.addCrossProfileWidgetProvider(BaseManagedProfileTest.ADMIN_RECEIVER_COMPONENT,
                    packageName);
            Log.i(TAG, "Added cross-profile widget provider for package:" + packageName
                    + " for user " + Process.myUserHandle());
        } else if (REMOVE_CROSS_PROFILE_WIDGET_COMMAND.equals(command)) {
            String packageName = intent.getStringExtra(EXTRA_PACKAGE_NAME);
            dpm.removeCrossProfileWidgetProvider(BaseManagedProfileTest.ADMIN_RECEIVER_COMPONENT,
                    packageName);
            Log.i(TAG, "Removed cross-profile widget provider for package:" + packageName
                    + " for user " + Process.myUserHandle());
        } else {
            Log.e(TAG, "Invalid command: " + command);
        }
    }

}