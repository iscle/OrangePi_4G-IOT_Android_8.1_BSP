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
package com.android.cts.deviceandprofileowner;

import android.app.Activity;
import android.app.admin.DevicePolicyManager;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager.NameNotFoundException;
import android.os.Bundle;
import android.os.Process;
import android.util.Log;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Simple activity that sets or unsets a policy depending on the value of the extras.
 */
public class SetPolicyActivity extends Activity {

    private static final String TAG = SetPolicyActivity.class.getName();

    private static final String EXTRA_RESTRICTION_KEY = "extra-restriction-key";
    private static final String EXTRA_COMMAND = "extra-command";
    private static final String EXTRA_ACCOUNT_TYPE = "extra-account-type";
    private static final String EXTRA_PACKAGE_NAME = "extra-package-name";
    private static final String EXTRA_SCOPES_LIST = "extra-scopes-list";

    private static final String COMMAND_ADD_USER_RESTRICTION = "add-restriction";
    private static final String COMMAND_CLEAR_USER_RESTRICTION = "clear-restriction";
    private static final String COMMAND_BLOCK_ACCOUNT_TYPE = "block-accounttype";
    private static final String COMMAND_UNBLOCK_ACCOUNT_TYPE = "unblock-accounttype";
    private static final String COMMAND_SET_APP_RESTRICTIONS_MANAGER =
            "set-app-restrictions-manager";
    private static final String COMMAND_SET_DELEGATED_SCOPES = "set-delegated-scopes";

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
        Log.i(TAG, "Command: " + command);

        if (COMMAND_ADD_USER_RESTRICTION.equals(command)) {
            String restrictionKey = intent.getStringExtra(EXTRA_RESTRICTION_KEY);
            dpm.addUserRestriction(BaseDeviceAdminTest.ADMIN_RECEIVER_COMPONENT, restrictionKey);
            Log.i(TAG, "Added user restriction " + restrictionKey
                    + " for user " + Process.myUserHandle());
        } else if (COMMAND_CLEAR_USER_RESTRICTION.equals(command)) {
            String restrictionKey = intent.getStringExtra(EXTRA_RESTRICTION_KEY);
            dpm.clearUserRestriction(
                    BaseDeviceAdminTest.ADMIN_RECEIVER_COMPONENT, restrictionKey);
            Log.i(TAG, "Cleared user restriction " + restrictionKey
                    + " for user " + Process.myUserHandle());
        } else if (COMMAND_BLOCK_ACCOUNT_TYPE.equals(command)) {
            String accountType = intent.getStringExtra(EXTRA_ACCOUNT_TYPE);
            dpm.setAccountManagementDisabled(BaseDeviceAdminTest.ADMIN_RECEIVER_COMPONENT,
                    accountType, true);
            Log.i(TAG, "Blocking account management for account type: " + accountType
                    + " for user " + Process.myUserHandle());
        } else if (COMMAND_UNBLOCK_ACCOUNT_TYPE.equals(command)) {
            String accountType = intent.getStringExtra(EXTRA_ACCOUNT_TYPE);
            dpm.setAccountManagementDisabled(BaseDeviceAdminTest.ADMIN_RECEIVER_COMPONENT,
                    accountType, false);
            Log.i(TAG, "Unblocking account management for account type: " + accountType
                    + " for user " + Process.myUserHandle());
        } else if (COMMAND_SET_APP_RESTRICTIONS_MANAGER.equals(command)) {
            String packageName = intent.getStringExtra(EXTRA_PACKAGE_NAME);
            try {
                dpm.setApplicationRestrictionsManagingPackage(
                        BaseDeviceAdminTest.ADMIN_RECEIVER_COMPONENT, packageName);
            } catch (NameNotFoundException e) {
                throw new IllegalArgumentException(e);
            }
            Log.i(TAG, "Setting the application restrictions managing package to " + packageName);
        } else if (COMMAND_SET_DELEGATED_SCOPES.equals(command)) {
            String packageName = intent.getStringExtra(EXTRA_PACKAGE_NAME);
            String scopeArray[] = intent.getStringArrayExtra(EXTRA_SCOPES_LIST);
            List<String> scopes = scopeArray == null ? new ArrayList() : Arrays.asList(scopeArray);
            dpm.setDelegatedScopes(BaseDeviceAdminTest.ADMIN_RECEIVER_COMPONENT,
                    packageName, scopes);
            Log.i(TAG, "Setting delegated scopes for package: " + packageName + " "
                    + Arrays.toString(scopeArray));
        } else {
            Log.e(TAG, "Invalid command: " + command);
        }
    }
}

