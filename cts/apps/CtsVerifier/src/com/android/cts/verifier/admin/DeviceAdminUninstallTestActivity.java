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

package com.android.cts.verifier.admin;

import android.app.admin.DevicePolicyManager;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Bundle;
import android.provider.Settings;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;

import com.android.cts.verifier.PassFailButtons;
import com.android.cts.verifier.R;

/**
 * Test that checks that active device admins can be easily uninstalled via the app details screen
 */
public class DeviceAdminUninstallTestActivity extends PassFailButtons.Activity implements
        View.OnClickListener {

    private static final String TAG = DeviceAdminUninstallTestActivity.class.getSimpleName();
    private static final String ADMIN_PACKAGE_NAME = "com.android.cts.emptydeviceadmin";
    private static final String ADMIN_RECEIVER_CLASS_NAME =
            "com.android.cts.emptydeviceadmin.EmptyDeviceAdmin";
    private static final String ADMIN_INSTALLED_BUNDLE_KEY = "admin_installed";
    private static final String ADMIN_ACTIVATED_BUNDLE_KEY = "admin_activated";
    private static final String ADMIN_REMOVED_BUNDLE_KEY = "admin_removed";

    private static final int REQUEST_ENABLE_ADMIN = 1;
    private static final int REQUEST_UNINSTALL_ADMIN = 2;

    private DevicePolicyManager mDevicePolicyManager;

    private ImageView mInstallStatus;
    private TextView mInstallAdminText;
    private Button mAddDeviceAdminButton;
    private ImageView mEnableStatus;
    private Button mUninstallAdminButton;
    private ImageView mUninstallStatus;
    private boolean mAdminInstalled;
    private boolean mAdminActivated;
    private boolean mAdminRemoved;
    private ComponentName mAdmin;
    private final BroadcastReceiver mPackageAddedListener = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            final Uri uri = intent.getData();
            if (uri != null && ADMIN_PACKAGE_NAME.equals(uri.getSchemeSpecificPart())) {
                onAdminPackageInstalled();
            }
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.da_uninstall_test_main);
        setInfoResources(R.string.da_uninstall_test, R.string.da_uninstall_test_info, -1);
        setPassFailButtonClickListeners();
        mAdmin = new ComponentName(ADMIN_PACKAGE_NAME, ADMIN_RECEIVER_CLASS_NAME);
        mDevicePolicyManager = (DevicePolicyManager) getSystemService(DEVICE_POLICY_SERVICE);

        if (savedInstanceState != null) {
            mAdminInstalled = savedInstanceState.getBoolean(ADMIN_INSTALLED_BUNDLE_KEY, false);
            mAdminActivated = savedInstanceState.getBoolean(ADMIN_ACTIVATED_BUNDLE_KEY, false);
            mAdminRemoved = savedInstanceState.getBoolean(ADMIN_REMOVED_BUNDLE_KEY, false);
        } else {
            mAdminInstalled = isPackageInstalled(ADMIN_PACKAGE_NAME);
            mAdminActivated = mDevicePolicyManager.isAdminActive(mAdmin);
        }
        mInstallStatus = findViewById(R.id.install_admin_status);
        mInstallAdminText = findViewById(R.id.install_admin_instructions);
        if (!mAdminInstalled) {
            final IntentFilter packageAddedFilter = new IntentFilter(Intent.ACTION_PACKAGE_ADDED);
            packageAddedFilter.addDataScheme("package");
            registerReceiver(mPackageAddedListener, packageAddedFilter);
        }

        mEnableStatus = findViewById(R.id.enable_admin_status);
        mAddDeviceAdminButton = findViewById(R.id.enable_device_admin_button);
        mAddDeviceAdminButton.setOnClickListener(this);

        mUninstallStatus = findViewById(R.id.uninstall_admin_status);
        mUninstallAdminButton = findViewById(R.id.open_app_details_button);
        mUninstallAdminButton.setOnClickListener(this);
    }

    private void onAdminPackageInstalled() {
        mAdminInstalled = true;
        updateWidgets();
        unregisterReceiver(mPackageAddedListener);
    }

    private boolean isPackageInstalled(String packageName) {
        PackageInfo packageInfo = null;
        try {
            packageInfo = getPackageManager().getPackageInfo(packageName, 0);
        } catch (PackageManager.NameNotFoundException exc) {
            // Expected.
        }
        return packageInfo != null;
    }

    @Override
    public void onClick(View v) {
        if (v == mAddDeviceAdminButton) {
            Intent securitySettingsIntent = new Intent(DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN);
            securitySettingsIntent.putExtra(DevicePolicyManager.EXTRA_DEVICE_ADMIN, mAdmin);
            startActivityForResult(securitySettingsIntent, REQUEST_ENABLE_ADMIN);
        } else if (v == mUninstallAdminButton) {
            Intent appDetails = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
            appDetails.setData(Uri.parse("package:" + ADMIN_PACKAGE_NAME));
            startActivityForResult(appDetails, REQUEST_UNINSTALL_ADMIN);
        }
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode == REQUEST_ENABLE_ADMIN) {
            mAdminActivated = mDevicePolicyManager.isAdminActive(mAdmin);
        } else if (requestCode == REQUEST_UNINSTALL_ADMIN) {
            mAdminRemoved = !isPackageInstalled(ADMIN_PACKAGE_NAME);
        }
    }

    @Override
    public void onResume() {
        super.onResume();
        updateWidgets();
    }

    @Override
    public void onSaveInstanceState(Bundle icicle) {
        icicle.putBoolean(ADMIN_INSTALLED_BUNDLE_KEY, mAdminInstalled);
        icicle.putBoolean(ADMIN_ACTIVATED_BUNDLE_KEY, mAdminActivated);
        icicle.putBoolean(ADMIN_REMOVED_BUNDLE_KEY, mAdminRemoved);
    }

    private void updateWidgets() {
        mInstallStatus.setImageResource(
                mAdminInstalled ? R.drawable.fs_good : R.drawable.fs_indeterminate);
        mInstallAdminText.setText(mAdminInstalled ? R.string.da_admin_installed_status_text
                : R.string.da_install_admin_instructions);
        mInstallStatus.invalidate();
        mAddDeviceAdminButton.setEnabled(mAdminInstalled && !mAdminActivated);
        mEnableStatus.setImageResource(
                mAdminActivated ? R.drawable.fs_good : R.drawable.fs_indeterminate);
        mEnableStatus.invalidate();

        mUninstallAdminButton.setEnabled(mAdminActivated && !mAdminRemoved);
        mUninstallStatus.setImageResource(
                mAdminRemoved ? R.drawable.fs_good : R.drawable.fs_indeterminate);
        mUninstallStatus.invalidate();

        getPassButton().setEnabled(mAdminRemoved);
    }
}
