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
 * limitations under the License.
 */

package com.android.cts.verifier.managedprovisioning;

import android.app.admin.DevicePolicyManager;
import android.content.ActivityNotFoundException;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.hardware.fingerprint.FingerprintManager;
import android.os.Bundle;
import android.os.UserManager;
import android.provider.Settings;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.Toast;

import com.android.cts.verifier.ArrayTestListAdapter;
import com.android.cts.verifier.DialogTestListActivity;
import com.android.cts.verifier.R;

public class DisallowAppsControlActivity extends DialogTestListActivity {

    protected DevicePolicyManager mDpm;

    public DisallowAppsControlActivity() {
        super(R.layout.provisioning_byod,
                R.string.provisioning_byod_disallow_apps_control,
                R.string.provisioning_byod_disallow_apps_control_info,
                R.string.provisioning_byod_disallow_apps_control_instruction);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        mDpm = (DevicePolicyManager) getSystemService(Context.DEVICE_POLICY_SERVICE);

        mPrepareTestButton.setText(
                R.string.provisioning_byod_disallow_apps_control_prepare_button);
        mPrepareTestButton.setOnClickListener(new OnClickListener() {
                @Override
                public void onClick(View v) {
                    disallowAppsControl();
                }
            });
    }

    protected ComponentName getAdminComponent() {
        return DeviceAdminTestReceiver.getReceiverComponentName();
    }

    @Override
    public void finish() {
        // Pass and fail buttons are known to call finish() when clicked, and this is when we want to
        // clear the restriction.
        allowAppsControl();
        super.finish();
    }

    private void allowAppsControl() {
        Intent allowAppsControlIntent = new Intent(
                ByodHelperActivity.ACTION_CLEAR_USER_RESTRICTION);
        allowAppsControlIntent.putExtra(
                ByodHelperActivity.EXTRA_PARAMETER_1, UserManager.DISALLOW_APPS_CONTROL);
        startActivity(allowAppsControlIntent);
    }

    private void disallowAppsControl() {
        Intent disallowAppsControlIntent = new Intent(
                ByodHelperActivity.ACTION_SET_USER_RESTRICTION).putExtra(
                ByodHelperActivity.EXTRA_PARAMETER_1, UserManager.DISALLOW_APPS_CONTROL);
        startActivity(disallowAppsControlIntent);
    }

    private void setupCheckDisabledUninstallButtonTest(ArrayTestListAdapter adapter) {
        adapter.add(new DialogTestListItem(this,
                R.string.provisioning_byod_disabled_uninstall_button,
                "BYOD_DISABLED_UNINSTALL_BUTTON",
                R.string.provisioning_byod_disabled_uninstall_button_instruction,
                new Intent(Settings.ACTION_APPLICATION_SETTINGS)));
    }

    private void setupCheckDisabledForceStopTest(ArrayTestListAdapter adapter) {
        adapter.add(new DialogTestListItem(this,
                R.string.provisioning_byod_disabled_force_stop_button,
                "BYOD_DISABLED_FORCE_STOP_BUTTON",
                R.string.provisioning_byod_disabled_force_stop_button_instruction,
                new Intent(Settings.ACTION_APPLICATION_SETTINGS)));
    }

    private void setupCheckDisabledAppStorageButtonsTest(ArrayTestListAdapter adapter) {
        adapter.add(new DialogTestListItem(this,
                R.string.provisioning_byod_disabled_app_storage_buttons,
                "BYOD_DISABLED_APP_STORAGE_BUTTONS",
                R.string.provisioning_byod_disabled_app_storage_buttons_instruction,
                new Intent(Settings.ACTION_APPLICATION_SETTINGS)));
    }

    @Override
    protected void setupTests(ArrayTestListAdapter adapter) {
        setupCheckDisabledUninstallButtonTest(adapter);
        setupCheckDisabledForceStopTest(adapter);
        setupCheckDisabledAppStorageButtonsTest(adapter);
    }
}
