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

import static com.android.cts.verifier.managedprovisioning.Utils.createInteractiveTestItem;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.admin.DevicePolicyManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.database.DataSetObserver;
import android.os.Bundle;
import android.os.UserManager;
import android.provider.Settings;
import android.util.Log;
import android.view.View;
import android.view.View.OnClickListener;

import com.android.cts.verifier.ArrayTestListAdapter;
import com.android.cts.verifier.IntentDrivenTestActivity;
import com.android.cts.verifier.IntentDrivenTestActivity.ButtonInfo;
import com.android.cts.verifier.PassFailButtons;
import com.android.cts.verifier.R;
import com.android.cts.verifier.TestListAdapter.TestListItem;
import com.android.cts.verifier.TestResult;

/**
 * Activity that lists all positive device owner tests. Requires the following adb command be issued
 * by the user prior to starting the tests:
 *
 * adb shell dpm set-device-owner
 *  'com.android.cts.verifier/com.android.cts.verifier.managedprovisioning.DeviceAdminTestReceiver'
 */
public class DeviceOwnerPositiveTestActivity extends PassFailButtons.TestListActivity {
    private static final String TAG = "DeviceOwnerPositiveTestActivity";

    private static final String ACTION_CHECK_DEVICE_OWNER =
            "com.android.cts.verifier.managedprovisioning.action.CHECK_DEVICE_OWNER";
    static final String EXTRA_TEST_ID = "extra-test-id";

    private static final String CHECK_DEVICE_OWNER_TEST_ID = "CHECK_DEVICE_OWNER";
    private static final String DEVICE_ADMIN_SETTINGS_ID = "DEVICE_ADMIN_SETTINGS";
    private static final String WIFI_LOCKDOWN_TEST_ID = WifiLockdownTestActivity.class.getName();
    private static final String DISABLE_STATUS_BAR_TEST_ID = "DISABLE_STATUS_BAR";
    private static final String DISABLE_KEYGUARD_TEST_ID = "DISABLE_KEYGUARD";
    private static final String CHECK_PERMISSION_LOCKDOWN_TEST_ID =
            PermissionLockdownTestActivity.class.getName();
    private static final String DISALLOW_CONFIG_BT_ID = "DISALLOW_CONFIG_BT";
    private static final String DISALLOW_CONFIG_WIFI_ID = "DISALLOW_CONFIG_WIFI";
    private static final String DISALLOW_CONFIG_VPN_ID = "DISALLOW_CONFIG_VPN";
    private static final String DISALLOW_USB_FILE_TRANSFER_ID = "DISALLOW_USB_FILE_TRANSFER";
    private static final String SET_USER_ICON_TEST_ID = "SET_USER_ICON";
    private static final String DISALLOW_DATA_ROAMING_ID = "DISALLOW_DATA_ROAMING";
    private static final String DISALLOW_FACTORY_RESET_ID = "DISALLOW_FACTORY_RESET";
    private static final String POLICY_TRANSPARENCY_TEST_ID = "POLICY_TRANSPARENCY";
    private static final String ENTERPRISE_PRIVACY_TEST_ID = "ENTERPRISE_PRIVACY";
    private static final String NETWORK_LOGGING_UI_TEST_ID = "NETWORK_LOGGING_UI";
    public static final String COMP_TEST_ID = "COMP_UI";
    private static final String REMOVE_DEVICE_OWNER_TEST_ID = "REMOVE_DEVICE_OWNER";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Tidy up in case previous run crashed.
        new ByodFlowTestHelper(this).tearDown();

        if (ACTION_CHECK_DEVICE_OWNER.equals(getIntent().getAction())) {
            DevicePolicyManager dpm = (DevicePolicyManager) getSystemService(
                    Context.DEVICE_POLICY_SERVICE);
            if (dpm.isDeviceOwnerApp(getPackageName())) {
                TestResult.setPassedResult(this, getIntent().getStringExtra(EXTRA_TEST_ID),
                        null, null);
            } else {
                TestResult.setFailedResult(this, getIntent().getStringExtra(EXTRA_TEST_ID),
                        getString(R.string.device_owner_incorrect_device_owner), null);
            }
            finish();
            return;
        }

        setContentView(R.layout.positive_device_owner);
        setInfoResources(R.string.device_owner_positive_tests,
                R.string.device_owner_positive_tests_info, 0);
        setPassFailButtonClickListeners();

        final ArrayTestListAdapter adapter = new ArrayTestListAdapter(this);
        adapter.add(TestListItem.newCategory(this, R.string.device_owner_positive_category));

        addTestsToAdapter(adapter);

        adapter.registerDataSetObserver(new DataSetObserver() {
            @Override
            public void onChanged() {
                updatePassButton();
            }
        });

        setTestListAdapter(adapter);

        View setDeviceOwnerButton = findViewById(R.id.set_device_owner_button);
        setDeviceOwnerButton.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                new AlertDialog.Builder(
                        DeviceOwnerPositiveTestActivity.this)
                        .setIcon(android.R.drawable.ic_dialog_info)
                        .setTitle(R.string.set_device_owner_dialog_title)
                        .setMessage(R.string.set_device_owner_dialog_text)
                        .setPositiveButton(android.R.string.ok, null)
                        .show();
            }
        });

    }

    @Override
    public void finish() {
        // If this activity was started for checking device owner status, then no need to do any
        // tear down.
        if (!ACTION_CHECK_DEVICE_OWNER.equals(getIntent().getAction())) {
            // Pass and fail buttons are known to call finish() when clicked,
            // and this is when we want to remove the device owner.
            startActivity(createTearDownIntent());
        }
        super.finish();
    }

    private void addTestsToAdapter(final ArrayTestListAdapter adapter) {
        adapter.add(createTestItem(this, CHECK_DEVICE_OWNER_TEST_ID,
                R.string.device_owner_check_device_owner_test,
                new Intent(ACTION_CHECK_DEVICE_OWNER)
                        .putExtra(EXTRA_TEST_ID, getIntent().getStringExtra(EXTRA_TEST_ID))));

        // device admin settings
        adapter.add(createInteractiveTestItem(this, DEVICE_ADMIN_SETTINGS_ID,
                R.string.device_owner_device_admin_visible,
                R.string.device_owner_device_admin_visible_info,
                new ButtonInfo(
                        R.string.device_owner_settings_go,
                        new Intent(Settings.ACTION_SECURITY_SETTINGS))));

        PackageManager packageManager = getPackageManager();
        if (packageManager.hasSystemFeature(PackageManager.FEATURE_WIFI)) {
            // WiFi Lock down tests
            adapter.add(createTestItem(this, WIFI_LOCKDOWN_TEST_ID,
                    R.string.device_owner_wifi_lockdown_test,
                    new Intent(this, WifiLockdownTestActivity.class)));

            // DISALLOW_CONFIG_WIFI
            adapter.add(createInteractiveTestItem(this, DISALLOW_CONFIG_WIFI_ID,
                    R.string.device_owner_disallow_config_wifi,
                    R.string.device_owner_disallow_config_wifi_info,
                    new ButtonInfo[] {
                            new ButtonInfo(
                                    R.string.device_owner_user_restriction_set,
                                    createSetUserRestrictionIntent(
                                            UserManager.DISALLOW_CONFIG_WIFI)),
                            new ButtonInfo(
                                    R.string.device_owner_settings_go,
                                    new Intent(Settings.ACTION_WIFI_SETTINGS))}));
        }

        // DISALLOW_CONFIG_VPN
        adapter.add(createInteractiveTestItem(this, DISALLOW_CONFIG_VPN_ID,
                R.string.device_owner_disallow_config_vpn,
                R.string.device_owner_disallow_config_vpn_info,
                new ButtonInfo[] {
                        new ButtonInfo(
                                R.string.device_owner_user_vpn_restriction_set,
                                createSetUserRestrictionIntent(
                                        UserManager.DISALLOW_CONFIG_VPN)),
                        new ButtonInfo(
                                R.string.device_owner_settings_go,
                                new Intent(Settings.ACTION_VPN_SETTINGS)),
                        new ButtonInfo(
                                R.string.device_owner_vpn_test,
                                new Intent(this, VpnTestActivity.class))}));

        // DISALLOW_DATA_ROAMING
        if(packageManager.hasSystemFeature(PackageManager.FEATURE_TELEPHONY)) {
            adapter.add(createInteractiveTestItem(this, DISALLOW_DATA_ROAMING_ID,
                    R.string.device_owner_disallow_data_roaming,
                    R.string.device_owner_disallow_data_roaming_info,
                    new ButtonInfo[] {
                            new ButtonInfo(
                                    R.string.device_owner_user_restriction_set,
                                    createSetUserRestrictionIntent(
                                            UserManager.DISALLOW_DATA_ROAMING)),
                            new ButtonInfo(
                                    R.string.device_owner_settings_go,
                                    new Intent(Settings.ACTION_DATA_ROAMING_SETTINGS))}));
        }

        // DISALLOW_FACTORY_RESET
        adapter.add(createInteractiveTestItem(this, DISALLOW_FACTORY_RESET_ID,
                R.string.device_owner_disallow_factory_reset,
                R.string.device_owner_disallow_factory_reset_info,
                new ButtonInfo[] {
                        new ButtonInfo(
                                R.string.device_owner_user_restriction_set,
                                createSetUserRestrictionIntent(
                                        UserManager.DISALLOW_FACTORY_RESET))}));

        // DISALLOW_CONFIG_BLUETOOTH
        if (packageManager.hasSystemFeature(PackageManager.FEATURE_BLUETOOTH)) {
            adapter.add(createInteractiveTestItem(this, DISALLOW_CONFIG_BT_ID,
                    R.string.device_owner_disallow_config_bt,
                    R.string.device_owner_disallow_config_bt_info,
                    new ButtonInfo[] {
                            new ButtonInfo(
                                    R.string.device_owner_user_restriction_set,
                                    createSetUserRestrictionIntent(
                                            UserManager.DISALLOW_CONFIG_BLUETOOTH)),
                            new ButtonInfo(
                                    R.string.device_owner_settings_go,
                                    new Intent(Settings.ACTION_BLUETOOTH_SETTINGS))}));
        }

        // DISALLOW_USB_FILE_TRANSFER
        adapter.add(createInteractiveTestItem(this, DISALLOW_USB_FILE_TRANSFER_ID,
                R.string.device_owner_disallow_usb_file_transfer_test,
                R.string.device_owner_disallow_usb_file_transfer_test_info,
                new ButtonInfo[] {
                        new ButtonInfo(
                                R.string.device_owner_user_restriction_set,
                                createSetUserRestrictionIntent(
                                        UserManager.DISALLOW_USB_FILE_TRANSFER)),
                }));

        // DISABLE_STATUS_BAR_TEST
        if (isStatusBarEnabled()) {
            adapter.add(createInteractiveTestItem(this, DISABLE_STATUS_BAR_TEST_ID,
                    R.string.device_owner_disable_statusbar_test,
                    R.string.device_owner_disable_statusbar_test_info,
                    new ButtonInfo[] {
                            new ButtonInfo(
                                    R.string.device_owner_disable_statusbar_button,
                                    createDeviceOwnerIntentWithBooleanParameter(
                                            CommandReceiverActivity.COMMAND_SET_STATUSBAR_DISABLED,
                                                    true)),
                            new ButtonInfo(
                                    R.string.device_owner_reenable_statusbar_button,
                                    createDeviceOwnerIntentWithBooleanParameter(
                                            CommandReceiverActivity.COMMAND_SET_STATUSBAR_DISABLED,
                                                    false))}));
        }

        // setKeyguardDisabled
        adapter.add(createInteractiveTestItem(this, DISABLE_KEYGUARD_TEST_ID,
                R.string.device_owner_disable_keyguard_test,
                R.string.device_owner_disable_keyguard_test_info,
                new ButtonInfo[] {
                        new ButtonInfo(
                                R.string.device_owner_disable_keyguard_button,
                                createDeviceOwnerIntentWithBooleanParameter(
                                        CommandReceiverActivity.COMMAND_SET_KEYGUARD_DISABLED,
                                                true)),
                        new ButtonInfo(
                                R.string.device_owner_reenable_keyguard_button,
                                createDeviceOwnerIntentWithBooleanParameter(
                                        CommandReceiverActivity.COMMAND_SET_KEYGUARD_DISABLED,
                                                false))}));

        adapter.add(createInteractiveTestItem(this, SET_USER_ICON_TEST_ID,
                R.string.device_owner_set_user_icon,
                R.string.device_owner_set_user_icon_instruction,
                new ButtonInfo[] {
                        new ButtonInfo(
                                R.string.device_owner_set_user_icon_button,
                                createSetUserIconIntent()),
                        new ButtonInfo(
                                R.string.device_owner_settings_go,
                                new Intent(Settings.ACTION_SETTINGS))}));

        // setPermissionGrantState
        adapter.add(createTestItem(this, CHECK_PERMISSION_LOCKDOWN_TEST_ID,
                R.string.device_profile_owner_permission_lockdown_test,
                new Intent(PermissionLockdownTestActivity.ACTION_CHECK_PERMISSION_LOCKDOWN)));

        // Policy Transparency
        final Intent policyTransparencyTestIntent = new Intent(this,
                PolicyTransparencyTestListActivity.class);
        policyTransparencyTestIntent.putExtra(
                PolicyTransparencyTestListActivity.EXTRA_MODE,
                PolicyTransparencyTestListActivity.MODE_DEVICE_OWNER);
        // So that PolicyTransparencyTestListActivity knows which test to update with the result:
        policyTransparencyTestIntent.putExtra(
                PolicyTransparencyTestActivity.EXTRA_TEST_ID, POLICY_TRANSPARENCY_TEST_ID);
        adapter.add(createTestItem(this, POLICY_TRANSPARENCY_TEST_ID,
                R.string.device_profile_owner_policy_transparency_test,
                policyTransparencyTestIntent));

        // Enterprise Privacy
        final Intent enterprisePolicyTestIntent = new Intent(this,
                EnterprisePrivacyTestListActivity.class);
        enterprisePolicyTestIntent.putExtra(
                EnterprisePrivacyTestListActivity.EXTRA_TEST_ID, ENTERPRISE_PRIVACY_TEST_ID);
        adapter.add(createTestItem(this, ENTERPRISE_PRIVACY_TEST_ID,
                R.string.enterprise_privacy_test,
                enterprisePolicyTestIntent));

        if (packageManager.hasSystemFeature(PackageManager.FEATURE_MANAGED_USERS)) {
            Intent compIntent = new Intent(this, CompTestActivity.class)
                    .putExtra(PolicyTransparencyTestActivity.EXTRA_TEST_ID, COMP_TEST_ID);
            adapter.add(createTestItem(this, COMP_TEST_ID,
                    R.string.comp_test,
                    compIntent));
        }

        // Network logging UI
        adapter.add(createInteractiveTestItem(this, NETWORK_LOGGING_UI_TEST_ID,
                R.string.device_owner_network_logging_ui,
                R.string.device_owner_network_logging_ui_info,
                new ButtonInfo[] {
                        new ButtonInfo(
                                R.string.device_owner_enable_network_logging_button,
                                createEnableNetworkLoggingIntent()),
                        new ButtonInfo(
                                R.string.device_owner_disable_network_logging_button,
                                createDisableNetworkLoggingIntent())}));

        // removeDeviceOwner
        adapter.add(createInteractiveTestItem(this, REMOVE_DEVICE_OWNER_TEST_ID,
                R.string.device_owner_remove_device_owner_test,
                R.string.device_owner_remove_device_owner_test_info,
                new ButtonInfo(
                        R.string.remove_device_owner_button,
                        createTearDownIntent())));
    }


    static TestListItem createTestItem(Activity activity, String id, int titleRes,
            Intent intent) {
        intent.putExtra(EXTRA_TEST_ID, id);
        return TestListItem.newTest(activity, titleRes, id, intent, null);
    }

    private Intent createTearDownIntent() {
        return new Intent(this, CommandReceiverActivity.class)
                .putExtra(CommandReceiverActivity.EXTRA_COMMAND,
                        CommandReceiverActivity.COMMAND_REMOVE_DEVICE_OWNER);
    }

    private Intent createDeviceOwnerIntentWithBooleanParameter(String command, boolean value) {
        return new Intent(this, CommandReceiverActivity.class)
                .putExtra(CommandReceiverActivity.EXTRA_COMMAND, command)
                .putExtra(CommandReceiverActivity.EXTRA_ENFORCED, value);
    }

    private Intent createSetUserRestrictionIntent(String restriction) {
        return new Intent(this, CommandReceiverActivity.class)
                .putExtra(CommandReceiverActivity.EXTRA_COMMAND,
                        CommandReceiverActivity.COMMAND_SET_USER_RESTRICTION)
                .putExtra(CommandReceiverActivity.EXTRA_USER_RESTRICTION, restriction)
                .putExtra(CommandReceiverActivity.EXTRA_ENFORCED, true);
    }

    private Intent createSetUserIconIntent() {
        return new Intent(this, CommandReceiverActivity.class)
                .putExtra(CommandReceiverActivity.EXTRA_COMMAND,
                        CommandReceiverActivity.COMMAND_SET_USER_ICON);
    }

    private Intent createEnableNetworkLoggingIntent() {
        return new Intent(this, CommandReceiverActivity.class)
                .putExtra(CommandReceiverActivity.EXTRA_COMMAND,
                        CommandReceiverActivity.COMMAND_ENABLE_NETWORK_LOGGING);
    }

    private Intent createDisableNetworkLoggingIntent() {
        return new Intent(this, CommandReceiverActivity.class)
                .putExtra(CommandReceiverActivity.EXTRA_COMMAND,
                        CommandReceiverActivity.COMMAND_DISABLE_NETWORK_LOGGING);
    }

    private boolean isStatusBarEnabled() {
      // Watches don't support the status bar so this is an ok proxy, but this is not the most
      // general test for that. TODO: add a test API to do a real check for status bar support.
      return !getPackageManager().hasSystemFeature(PackageManager.FEATURE_WATCH);
    }
}
