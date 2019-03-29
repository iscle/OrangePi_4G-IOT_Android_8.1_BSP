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

package com.android.cts.verifier.managedprovisioning;

import android.app.admin.DevicePolicyManager;
import android.content.ActivityNotFoundException;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Bundle;
import android.provider.Settings;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.RadioGroup;
import android.widget.TextView;
import android.widget.Toast;

import com.android.cts.verifier.PassFailButtons;
import com.android.cts.verifier.R;

public class PermissionLockdownTestActivity extends PassFailButtons.Activity
        implements RadioGroup.OnCheckedChangeListener {
    private static final String PERMISSION_APP_PACKAGE = "com.android.cts.permissionapp";

    // Alias used for starting the activity from ByodFlowTestActivity (Managed profile tests).
    static final String ACTIVITY_ALIAS
            = "com.android.cts.verifier.managedprovisioning" +
                    ".ManagedProfilePermissionLockdownTestActivity";

    private static final String MANAGED_PROVISIONING_ACTION_PREFIX
            = "com.android.cts.verifier.managedprovisioning.action.";
    // Indicates that activity is started for device owner case.
    static final String ACTION_CHECK_PERMISSION_LOCKDOWN
            = MANAGED_PROVISIONING_ACTION_PREFIX + "CHECK_PERMISSION_LOCKDOWN";
    // Indicates that activity is started for profile owner case.
    static final String ACTION_MANAGED_PROFILE_CHECK_PERMISSION_LOCKDOWN
            = MANAGED_PROVISIONING_ACTION_PREFIX + "MANAGED_PROFILE_CHECK_PERMISSION_LOCKDOWN";

    // Permission grant states will be set on this permission.
    private static final String CONTACTS_PERMISSION = android.Manifest.permission.READ_CONTACTS;

    private boolean mDeviceOwnerTest;
    private DevicePolicyManager mDevicePolicyManager;
    private ComponentName mAdmin;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.permission_lockdown);

        mDevicePolicyManager =
                (DevicePolicyManager) getSystemService(Context.DEVICE_POLICY_SERVICE);
        mAdmin = DeviceAdminTestReceiver.getReceiverComponentName();

        mDeviceOwnerTest =
                ACTION_CHECK_PERMISSION_LOCKDOWN.equals(getIntent().getAction()) ? true : false;
        // Return immediately if we are neither profile nor device owner.
        if (!isProfileOrDeviceOwner()) {
            setTestResultAndFinish(false);
            return;
        }

        buildLayout();
    }

    private void buildLayout() {
        PackageManager packageManager = getPackageManager();
        ApplicationInfo applicationInfo = null;
        try {
            // We need to make sure that the CtsPermissionApp is installed on the device or
            // work profile.
            applicationInfo = packageManager.getApplicationInfo(
                    PERMISSION_APP_PACKAGE, 0 /* flags */);
        } catch (PackageManager.NameNotFoundException e) {
            showToast(getString(R.string.package_not_found, PERMISSION_APP_PACKAGE));
            setTestResultAndFinish(false);
            return;
        }

        ImageView packageIconImageView = (ImageView) findViewById(R.id.package_icon);
        packageIconImageView.setImageDrawable(packageManager.getApplicationIcon(applicationInfo));
        TextView packageNameTextView = (TextView) findViewById(R.id.package_name);
        packageNameTextView.setText(packageManager.getApplicationLabel(applicationInfo));

        TextView permissionNameTextView = (TextView) findViewById(R.id.permission_name);
        permissionNameTextView.setText(CONTACTS_PERMISSION);

        // Get the current permission grant state for initializing the RadioGroup.
        int currentPermissionState = mDevicePolicyManager.getPermissionGrantState(mAdmin,
                    PERMISSION_APP_PACKAGE, CONTACTS_PERMISSION);
        RadioGroup permissionRadioGroup = (RadioGroup) findViewById(R.id.permission_group);
        switch (currentPermissionState) {
            case DevicePolicyManager.PERMISSION_GRANT_STATE_GRANTED: {
                permissionRadioGroup.check(R.id.permission_allow);
            } break;
            case DevicePolicyManager.PERMISSION_GRANT_STATE_DEFAULT: {
                permissionRadioGroup.check(R.id.permission_default);
            } break;
            case  DevicePolicyManager.PERMISSION_GRANT_STATE_DENIED: {
                permissionRadioGroup.check(R.id.permission_deny);
            } break;
        }
        permissionRadioGroup.setOnCheckedChangeListener(this);

        addFinishOrPassFailButtons();
    }

    private void addFinishOrPassFailButtons() {
        // In case of device owner, we include the pass-fail buttons where as in case of profile
        // owner, we add a finish button.
        ViewGroup parentView = (ViewGroup) findViewById(R.id.permission_lockdown_activity);
        if (mDeviceOwnerTest) {
            parentView.addView(
                    getLayoutInflater().inflate(R.layout.pass_fail_buttons, parentView, false));
            setInfoResources(R.string.device_profile_owner_permission_lockdown_test,
                    R.string.device_owner_permission_lockdown_test_info, 0);
            setPassFailButtonClickListeners();
        } else {
            Button finishButton = new Button(this);
            finishButton.setText(R.string.finish_button_label);
            finishButton.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    PermissionLockdownTestActivity.this.setTestResultAndFinish(false);
                }
            });
            parentView.addView(finishButton);
        }
    }

    // Dispatches an intent to open the Settings screen for CtsPermissionApp.
    public void openSettings(View v) {
        Intent intent = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                .setData(Uri.fromParts("package", PERMISSION_APP_PACKAGE, null))
                .setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        try {
            startActivity(intent);
        } catch (ActivityNotFoundException e) {
            showToast(getString(R.string.activity_not_found, intent));
        }
    }

    @Override
    public void onCheckedChanged(RadioGroup radioGroup, int checkedId) {
        int permissionGrantState = -1;
        switch (checkedId) {
            case R.id.permission_allow: {
                permissionGrantState = DevicePolicyManager.PERMISSION_GRANT_STATE_GRANTED;
            } break;
            case R.id.permission_default: {
                permissionGrantState = DevicePolicyManager.PERMISSION_GRANT_STATE_DEFAULT;
            } break;
            case R.id.permission_deny: {
                permissionGrantState = DevicePolicyManager.PERMISSION_GRANT_STATE_DENIED;
            } break;
        }
        mDevicePolicyManager.setPermissionGrantState(mAdmin, PERMISSION_APP_PACKAGE,
                CONTACTS_PERMISSION, permissionGrantState);
    }

    private boolean isProfileOrDeviceOwner() {
        String adminPackage = mAdmin.getPackageName();
        if (mDeviceOwnerTest && !mDevicePolicyManager.isDeviceOwnerApp(adminPackage)) {
            showToast(getString(R.string.not_device_owner, adminPackage));
            return false;
        } else if (!mDeviceOwnerTest && !mDevicePolicyManager.isProfileOwnerApp(adminPackage)) {
            showToast(getString(R.string.not_profile_owner, adminPackage));
            return false;
        }
        return true;
    }

    private void showToast(String toast) {
        Toast.makeText(this, toast, Toast.LENGTH_LONG).show();
    }
}