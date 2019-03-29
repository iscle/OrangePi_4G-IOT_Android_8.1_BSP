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
 * limitations under the License.
 */
package com.android.cts.verifier.managedprovisioning;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.os.Bundle;
import android.util.Log;

import com.android.cts.verifier.R;

/**
 * Creates a managed profile on a device owner device, and checks that the user is not able to
 * remove the profile if {@link android.os.UserManager#DISALLOW_REMOVE_MANAGED_PROFILE} is set.
 */
public class CompTestActivity extends Activity {

    private static final String TAG = "CompTestActivity";

    private static final int PROVISION_MANAGED_PROFILE_REQUEST_CODE = 1;
    private static final int POLICY_TRANSPARENCY_REQUEST_CODE = 2;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        new AlertDialog.Builder(
                CompTestActivity.this)
                .setIcon(android.R.drawable.ic_dialog_info)
                .setTitle(R.string.comp_provision_profile_dialog_title)
                .setMessage(R.string.comp_provision_profile_dialog_text)
                .setPositiveButton(android.R.string.ok,
                        (dialog, whichButton) -> {
                            Utils.provisionManagedProfile(CompTestActivity.this,
                                    CompDeviceAdminTestReceiver.getReceiverComponentName(),
                                    PROVISION_MANAGED_PROFILE_REQUEST_CODE);
                        }).show();
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode == PROVISION_MANAGED_PROFILE_REQUEST_CODE) {
            if (resultCode != Activity.RESULT_OK) {
                Log.i(TAG, "Provisioning failed or was cancelled by the user.");
                setResult(Activity.RESULT_CANCELED);
                finish();
                return;
            }

            final Intent policyTransparencyTestIntent = new Intent(this,
                    PolicyTransparencyTestListActivity.class);
            policyTransparencyTestIntent.putExtra(
                    PolicyTransparencyTestListActivity.EXTRA_MODE,
                    PolicyTransparencyTestListActivity.MODE_COMP);
            String testId = getIntent().getStringExtra(
                    PolicyTransparencyTestActivity.EXTRA_TEST_ID);
            policyTransparencyTestIntent.putExtra(
                    PolicyTransparencyTestActivity.EXTRA_TEST_ID,
                    testId);
            startActivityForResult(policyTransparencyTestIntent, POLICY_TRANSPARENCY_REQUEST_CODE);
        } else if (requestCode == POLICY_TRANSPARENCY_REQUEST_CODE) {
            startActivity(new Intent(CommandReceiverActivity.ACTION_EXECUTE_COMMAND)
                    .putExtra(CommandReceiverActivity.EXTRA_COMMAND,
                            CommandReceiverActivity.COMMAND_REMOVE_MANAGED_PROFILE));
            // forward the result to the caller activity so that it can update the test result
            setResult(resultCode, data);
            finish();
        } else {
            Log.e(TAG, "Unknown request code received " + requestCode);
        }
    }
}
