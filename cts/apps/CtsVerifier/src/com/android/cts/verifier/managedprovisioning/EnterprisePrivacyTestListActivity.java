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

import android.Manifest;
import android.app.admin.DevicePolicyManager;
import android.content.ComponentName;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.database.DataSetObserver;
import android.os.Bundle;
import android.provider.Settings;

import com.android.cts.verifier.ArrayTestListAdapter;
import com.android.cts.verifier.IntentDrivenTestActivity.ButtonInfo;
import com.android.cts.verifier.PassFailButtons;
import com.android.cts.verifier.R;
import com.android.cts.verifier.TestListAdapter.TestListItem;

import static com.android.cts.verifier.managedprovisioning.Utils.createInteractiveTestItem;

/**
 * Test class to verify privacy information is shown for devices managed by a Device Owner.
 */
public class EnterprisePrivacyTestListActivity extends PassFailButtons.TestListActivity {

    private static final String ENTERPRISE_PRIVACY_PAGE = "ENTERPRISE_PRIVACY_PAGE";
    private static final String ENTERPRISE_PRIVACY_NETWORK_LOGGING
            = "ENTERPRISE_PRIVACY_NETWORK_LOGGING";
    private static final String ENTERPRISE_PRIVACY_BUG_REPORT = "ENTERPRISE_PRIVACY_BUG_REPORT";
    private static final String ENTERPRISE_PRIVACY_SECURITY_LOGGING
            = "ENTERPRISE_PRIVACY_SECURITY_LOGGING";
    private static final String ENTERPRISE_PRIVACY_ENTERPRISE_INSTALLED_APPS
            = "ENTERPRISE_PRIVACY_ENTERPRISE_INSTALLED_APPS";
    private static final String ENTERPRISE_PRIVACY_LOCATION_ACCESS
            = "ENTERPRISE_PRIVACY_LOCATION_ACCESS";
    private static final String ENTERPRISE_PRIVACY_MICROPHONE_ACCESS
            = "ENTERPRISE_PRIVACY_MICROPHONE_ACCESS";
    private static final String ENTERPRISE_PRIVACY_CAMERA_ACCESS
            = "ENTERPRISE_PRIVACY_CAMERA_ACCESS";
    private static final String ENTERPRISE_PRIVACY_DEFAULT_APPS
            = "ENTERPRISE_PRIVACY_DEFAULT_APPS";
    private static final String ENTERPRISE_PRIVACY_DEFAULT_IME
            = "ENTERPRISE_PRIVACY_DEFAULT_IME";
    private static final String ENTERPRISE_PRIVACY_ALWAYS_ON_VPN
            = "ENTERPRISE_PRIVACY_ALWAYS_ON_VPN";
    private static final String ENTERPRISE_PRIVACY_COMP_ALWAYS_ON_VPN
            = "ENTERPRISE_PRIVACY_COMP_ALWAYS_ON_VPN";
    private static final String ENTERPRISE_PRIVACY_GLOBAL_HTTP_PROXY
            = "ENTERPRISE_PRIVACY_GLOBAL_HTTP_PROXY";
    private static final String ENTERPRISE_PRIVACY_CA_CERTS = "ENTERPRISE_PRIVACY_CA_CERTS";
    private static final String ENTERPRISE_PRIVACY_COMP_CA_CERTS
            = "ENTERPRISE_PRIVACY_COMP_CA_CERTS";
    private static final String ENTERPRISE_PRIVACY_FAILED_PASSWORD_WIPE
            = "ENTERPRISE_PRIVACY_FAILED_PASSWORD_WIPE";
    private static final String ENTERPRISE_PRIVACY_COMP_FAILED_PASSWORD_WIPE
            = "ENTERPRISE_PRIVACY_COMP_FAILED_PASSWORD_WIPE";
    private static final String ENTERPRISE_PRIVACY_QUICK_SETTINGS
            = "ENTERPRISE_PRIVACY_QUICK_SETTINGS";
    private static final String ENTERPRISE_PRIVACY_KEYGUARD = "ENTERPRISE_PRIVACY_KEYGUARD";
    private static final String ENTERPRISE_PRIVACY_ADD_ACCOUNT = "ENTERPRISE_PRIVACY_ADD_ACCOUNT";

    public static final String EXTRA_TEST_ID =
            "com.android.cts.verifier.managedprovisioning.extra.TEST_ID";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.pass_fail_list);
        setPassFailButtonClickListeners();
        final ArrayTestListAdapter adapter = new ArrayTestListAdapter(this);
        addTestsToAdapter(adapter);
        adapter.registerDataSetObserver(new DataSetObserver() {
            @Override
            public void onChanged() {
                updatePassButton();
            }
        });
        setTestListAdapter(adapter);
        getPackageManager().setComponentEnabledSetting(
                new ComponentName(this, CompHelperActivity.class),
                PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
                PackageManager.DONT_KILL_APP);
    }

    private Intent buildCommandIntent(String command) {
        return new Intent(CommandReceiverActivity.ACTION_EXECUTE_COMMAND)
                .putExtra(CommandReceiverActivity.EXTRA_COMMAND, command);
    }

    private TestListItem buildCommandTest(String id, int titleRes, int infoRes,
            int commandButtonRes, String command) {
        return createInteractiveTestItem(this, id, titleRes, infoRes,
                new ButtonInfo[] {
                        new ButtonInfo(commandButtonRes, buildCommandIntent(command)),
                        new ButtonInfo(R.string.enterprise_privacy_open_settings,
                               new Intent(Settings.ACTION_ENTERPRISE_PRIVACY_SETTINGS))});
    }

    private TestListItem buildAdminGrantedPermissionTest(String id, int titleRes, int infoRes,
            String permission) {
        return createInteractiveTestItem(this, id, titleRes, infoRes,
                new ButtonInfo[] {
                        new ButtonInfo(R.string.enterprise_privacy_reset, buildCommandIntent(
                                CommandReceiverActivity.COMMAND_SET_PERMISSION_GRANT_STATE)
                                .putExtra(CommandReceiverActivity.EXTRA_PERMISSION, permission)
                                .putExtra(CommandReceiverActivity.EXTRA_GRANT_STATE,
                                        DevicePolicyManager.PERMISSION_GRANT_STATE_DEFAULT)),
                        new ButtonInfo(R.string.enterprise_privacy_grant, buildCommandIntent(
                                CommandReceiverActivity.COMMAND_SET_PERMISSION_GRANT_STATE)
                                .putExtra(CommandReceiverActivity.EXTRA_PERMISSION, permission)
                                .putExtra(CommandReceiverActivity.EXTRA_GRANT_STATE,
                                        DevicePolicyManager.PERMISSION_GRANT_STATE_GRANTED)),
                        new ButtonInfo(R.string.enterprise_privacy_open_settings,
                                new Intent(Settings.ACTION_ENTERPRISE_PRIVACY_SETTINGS))});
    }

    private void addTestsToAdapter(final ArrayTestListAdapter adapter) {
        adapter.add(createInteractiveTestItem(this, ENTERPRISE_PRIVACY_PAGE,
                R.string.enterprise_privacy_page,
                R.string.enterprise_privacy_page_info,
                new ButtonInfo(R.string.go_button_text, new Intent(Settings.ACTION_SETTINGS))));
        adapter.add(buildCommandTest(ENTERPRISE_PRIVACY_NETWORK_LOGGING,
                R.string.enterprise_privacy_network_logging,
                R.string.enterprise_privacy_network_logging_info,
                R.string.enterprise_privacy_retrieve_network_logs,
                CommandReceiverActivity.COMMAND_RETRIEVE_NETWORK_LOGS));
        adapter.add(buildCommandTest(ENTERPRISE_PRIVACY_BUG_REPORT,
                R.string.enterprise_privacy_bug_report,
                R.string.enterprise_privacy_bug_report_info,
                R.string.enterprise_privacy_request_bug_report,
                CommandReceiverActivity.COMMAND_REQUEST_BUGREPORT));
        adapter.add(buildCommandTest(ENTERPRISE_PRIVACY_SECURITY_LOGGING,
                R.string.enterprise_privacy_security_logging,
                R.string.enterprise_privacy_security_logging_info,
                R.string.enterprise_privacy_retrieve_security_logs,
                CommandReceiverActivity.COMMAND_RETRIEVE_SECURITY_LOGS));
        adapter.add(createInteractiveTestItem(this, ENTERPRISE_PRIVACY_ENTERPRISE_INSTALLED_APPS,
                R.string.enterprise_privacy_enterprise_installed_apps,
                R.string.enterprise_privacy_enterprise_installed_apps_info,
                new ButtonInfo[] {
                        new ButtonInfo(R.string.enterprise_privacy_install,
                                buildCommandIntent(
                                        CommandReceiverActivity.COMMAND_INSTALL_HELPER_PACKAGE)),
                        new ButtonInfo(R.string.enterprise_privacy_uninstall,
                                buildCommandIntent(CommandReceiverActivity
                                        .COMMAND_UNINSTALL_HELPER_PACKAGE)),
                        new ButtonInfo(R.string.enterprise_privacy_open_settings,
                                new Intent(Settings.ACTION_ENTERPRISE_PRIVACY_SETTINGS))}));
        adapter.add(buildAdminGrantedPermissionTest(ENTERPRISE_PRIVACY_LOCATION_ACCESS,
                R.string.enterprise_privacy_admin_granted_location_access,
                R.string.enterprise_privacy_admin_granted_location_access_info,
                Manifest.permission.ACCESS_FINE_LOCATION));
        adapter.add(buildAdminGrantedPermissionTest(ENTERPRISE_PRIVACY_MICROPHONE_ACCESS,
                R.string.enterprise_privacy_admin_granted_microphone_access,
                R.string.enterprise_privacy_admin_granted_microphone_access_info,
                Manifest.permission.RECORD_AUDIO));
        adapter.add(buildAdminGrantedPermissionTest(ENTERPRISE_PRIVACY_CAMERA_ACCESS,
                R.string.enterprise_privacy_admin_granted_camera_access,
                R.string.enterprise_privacy_admin_granted_camera_access_info,
                Manifest.permission.CAMERA));
        adapter.add(createInteractiveTestItem(this, ENTERPRISE_PRIVACY_DEFAULT_APPS,
                R.string.enterprise_privacy_default_apps,
                R.string.enterprise_privacy_default_apps_info,
                new ButtonInfo[] {
                        new ButtonInfo(R.string.enterprise_privacy_open_settings,
                                new Intent(Settings.ACTION_ENTERPRISE_PRIVACY_SETTINGS)),
                        new ButtonInfo(R.string.enterprise_privacy_reset, buildCommandIntent(
                                CommandReceiverActivity
                                        .COMMAND_CLEAR_PERSISTENT_PREFERRED_ACTIVITIES)),
                        new ButtonInfo(R.string.enterprise_privacy_set_default_apps,
                                buildCommandIntent(CommandReceiverActivity
                                        .COMMAND_ADD_PERSISTENT_PREFERRED_ACTIVITIES))}));
        adapter.add(createInteractiveTestItem(this, ENTERPRISE_PRIVACY_DEFAULT_IME,
                R.string.enterprise_privacy_default_ime,
                R.string.enterprise_privacy_default_ime_info,
                new ButtonInfo[] {
                        new ButtonInfo(R.string.enterprise_privacy_open_settings,
                                new Intent(Settings.ACTION_ENTERPRISE_PRIVACY_SETTINGS)),
                        new ButtonInfo(R.string.enterprise_privacy_set_keyboard,
                                buildCommandIntent(CommandReceiverActivity
                                        .COMMAND_SET_DEFAULT_IME)),
                        new ButtonInfo(R.string.enterprise_privacy_finish,
                                buildCommandIntent(CommandReceiverActivity
                                        .COMMAND_CLEAR_DEFAULT_IME))}));
        adapter.add(createInteractiveTestItem(this, ENTERPRISE_PRIVACY_ALWAYS_ON_VPN,
                R.string.enterprise_privacy_always_on_vpn,
                R.string.enterprise_privacy_always_on_vpn_info,
                new ButtonInfo[] {
                        new ButtonInfo(R.string.enterprise_privacy_open_settings,
                                new Intent(Settings.ACTION_ENTERPRISE_PRIVACY_SETTINGS)),
                        new ButtonInfo(R.string.enterprise_privacy_set_always_on_vpn,
                                buildCommandIntent(
                                        CommandReceiverActivity.COMMAND_SET_ALWAYS_ON_VPN)),
                        new ButtonInfo(R.string.enterprise_privacy_finish,
                                buildCommandIntent(
                                        CommandReceiverActivity.COMMAND_CLEAR_ALWAYS_ON_VPN))}));
        if (getPackageManager().hasSystemFeature(PackageManager.FEATURE_MANAGED_USERS)) {
            adapter.add(createInteractiveTestItem(this, ENTERPRISE_PRIVACY_COMP_ALWAYS_ON_VPN,
                    R.string.enterprise_privacy_comp_always_on_vpn,
                    R.string.enterprise_privacy_comp_always_on_vpn_info,
                    new ButtonInfo[] {
                            new ButtonInfo(R.string.enterprise_privacy_start,
                                    buildCommandIntent(
                                            CommandReceiverActivity.
                                            COMMAND_CREATE_MANAGED_PROFILE)),
                            new ButtonInfo(R.string.enterprise_privacy_open_settings,
                                    new Intent(Settings.ACTION_ENTERPRISE_PRIVACY_SETTINGS)),
                            new ButtonInfo(R.string.enterprise_privacy_set_always_on_vpn,
                                    new Intent(CompHelperActivity.ACTION_SET_ALWAYS_ON_VPN)),
                            new ButtonInfo(R.string.enterprise_privacy_finish,
                                    buildCommandIntent(
                                            CommandReceiverActivity.
                                            COMMAND_REMOVE_MANAGED_PROFILE))}));
        }
        adapter.add(createInteractiveTestItem(this, ENTERPRISE_PRIVACY_GLOBAL_HTTP_PROXY,
                R.string.enterprise_privacy_global_http_proxy,
                R.string.enterprise_privacy_global_http_proxy_info,
                new ButtonInfo[] {
                        new ButtonInfo(R.string.enterprise_privacy_open_settings,
                                new Intent(Settings.ACTION_ENTERPRISE_PRIVACY_SETTINGS)),
                        new ButtonInfo(R.string.enterprise_privacy_set_proxy,
                                buildCommandIntent(
                                        CommandReceiverActivity.COMMAND_SET_GLOBAL_HTTP_PROXY)),
                        new ButtonInfo(R.string.enterprise_privacy_clear_proxy,
                                buildCommandIntent(CommandReceiverActivity
                                        .COMMAND_CLEAR_GLOBAL_HTTP_PROXY))}));
        adapter.add(createInteractiveTestItem(this, ENTERPRISE_PRIVACY_CA_CERTS,
                R.string.enterprise_privacy_ca_certs,
                R.string.enterprise_privacy_ca_certs_info,
                new ButtonInfo[] {
                        new ButtonInfo(R.string.enterprise_privacy_open_settings,
                                new Intent(Settings.ACTION_ENTERPRISE_PRIVACY_SETTINGS)),
                        new ButtonInfo(R.string.enterprise_privacy_install_cert,
                                buildCommandIntent(
                                        CommandReceiverActivity.COMMAND_INSTALL_CA_CERT)),
                        new ButtonInfo(R.string.enterprise_privacy_finish,
                                buildCommandIntent(
                                        CommandReceiverActivity.COMMAND_CLEAR_CA_CERT))}));
        if (getPackageManager().hasSystemFeature(PackageManager.FEATURE_MANAGED_USERS)) {
            adapter.add(createInteractiveTestItem(this, ENTERPRISE_PRIVACY_COMP_CA_CERTS,
                    R.string.enterprise_privacy_comp_ca_certs,
                    R.string.enterprise_privacy_comp_ca_certs_info,
                    new ButtonInfo[] {
                            new ButtonInfo(R.string.enterprise_privacy_start,
                                    buildCommandIntent(
                                            CommandReceiverActivity.
                                            COMMAND_CREATE_MANAGED_PROFILE)),
                            new ButtonInfo(R.string.enterprise_privacy_settings,
                                    new Intent(Settings.ACTION_ENTERPRISE_PRIVACY_SETTINGS)),
                            new ButtonInfo(R.string.enterprise_privacy_install_cert,
                                    new Intent(CompHelperActivity.ACTION_INSTALL_CA_CERT)),
                            new ButtonInfo(R.string.enterprise_privacy_finish,
                                    buildCommandIntent(
                                            CommandReceiverActivity.
                                            COMMAND_REMOVE_MANAGED_PROFILE))}));
        }
        // Disabled for API 26 due to b/63696536.
        // adapter.add(createInteractiveTestItem(this, ENTERPRISE_PRIVACY_FAILED_PASSWORD_WIPE,
        //         R.string.enterprise_privacy_failed_password_wipe,
        //         R.string.enterprise_privacy_failed_password_wipe_info,
        //         new ButtonInfo[] {
        //                 new ButtonInfo(R.string.enterprise_privacy_open_settings,
        //                         new Intent(Settings.ACTION_ENTERPRISE_PRIVACY_SETTINGS)),
        //                 new ButtonInfo(R.string.enterprise_privacy_set_limit,
        //                         buildCommandIntent(CommandReceiverActivity
        //                                 .COMMAND_SET_MAXIMUM_PASSWORD_ATTEMPTS)),
        //                 new ButtonInfo(R.string.enterprise_privacy_finish,
        //                         buildCommandIntent(CommandReceiverActivity
        //                                 .COMMAND_CLEAR_MAXIMUM_PASSWORD_ATTEMPTS))}));
        if (getPackageManager().hasSystemFeature(PackageManager.FEATURE_MANAGED_USERS)) {
            adapter.add(createInteractiveTestItem(this,
                    ENTERPRISE_PRIVACY_COMP_FAILED_PASSWORD_WIPE,
                    R.string.enterprise_privacy_comp_failed_password_wipe,
                    R.string.enterprise_privacy_comp_failed_password_wipe_info,
                    new ButtonInfo[]{
                            new ButtonInfo(R.string.enterprise_privacy_start,
                                    buildCommandIntent(
                                            CommandReceiverActivity.
                                                    COMMAND_CREATE_MANAGED_PROFILE)),
                            new ButtonInfo(R.string.enterprise_privacy_open_settings,
                                    new Intent(Settings.ACTION_ENTERPRISE_PRIVACY_SETTINGS)),
                            new ButtonInfo(R.string.enterprise_privacy_set_limit, new Intent(
                                    CompHelperActivity.ACTION_SET_MAXIMUM_PASSWORD_ATTEMPTS)),
                            new ButtonInfo(R.string.enterprise_privacy_finish,
                                    buildCommandIntent(
                                            CommandReceiverActivity.
                                                    COMMAND_REMOVE_MANAGED_PROFILE))}));
        }
        adapter.add(createInteractiveTestItem(this, ENTERPRISE_PRIVACY_QUICK_SETTINGS,
                R.string.enterprise_privacy_quick_settings,
                R.string.enterprise_privacy_quick_settings_info,
                new ButtonInfo[] {
                        new ButtonInfo(R.string.enterprise_privacy_clear_organization,
                                buildCommandIntent(
                                        CommandReceiverActivity.COMMAND_SET_ORGANIZATION_NAME)),
                        new ButtonInfo(R.string.enterprise_privacy_set_organization,
                                buildCommandIntent(
                                        CommandReceiverActivity.COMMAND_SET_ORGANIZATION_NAME)
                                        .putExtra(CommandReceiverActivity.EXTRA_ORGANIZATION_NAME,
                                                "Foo, Inc."))}));
        adapter.add(createInteractiveTestItem(this, ENTERPRISE_PRIVACY_KEYGUARD,
                R.string.enterprise_privacy_keyguard,
                R.string.enterprise_privacy_keyguard_info,
                new ButtonInfo[] {
                        new ButtonInfo(R.string.enterprise_privacy_open_settings,
                                new Intent(Settings.ACTION_SETTINGS)),
                        new ButtonInfo(R.string.enterprise_privacy_clear_organization,
                                buildCommandIntent(
                                        CommandReceiverActivity.COMMAND_SET_ORGANIZATION_NAME)),
                        new ButtonInfo(R.string.enterprise_privacy_set_organization,
                                buildCommandIntent(
                                        CommandReceiverActivity.COMMAND_SET_ORGANIZATION_NAME)
                                        .putExtra(CommandReceiverActivity.EXTRA_ORGANIZATION_NAME,
                                                "Foo, Inc."))}));
        adapter.add(createInteractiveTestItem(this, ENTERPRISE_PRIVACY_ADD_ACCOUNT,
                R.string.enterprise_privacy_add_account,
                R.string.enterprise_privacy_add_account_info,
                new ButtonInfo[] {
                        new ButtonInfo(R.string.enterprise_privacy_open_settings,
                                new Intent(Settings.ACTION_ADD_ACCOUNT)),
                        new ButtonInfo(R.string.enterprise_privacy_clear_organization,
                                buildCommandIntent(
                                        CommandReceiverActivity.COMMAND_SET_ORGANIZATION_NAME)),
                        new ButtonInfo(R.string.enterprise_privacy_set_organization,
                                buildCommandIntent(
                                        CommandReceiverActivity.COMMAND_SET_ORGANIZATION_NAME)
                                        .putExtra(CommandReceiverActivity.EXTRA_ORGANIZATION_NAME,
                                                "Foo, Inc."))}));
    }

    @Override
    public String getTestId() {
        return getIntent().getStringExtra(EXTRA_TEST_ID);
    }

    @Override
    public void finish() {
        super.finish();
        Intent intent = buildCommandIntent(CommandReceiverActivity.COMMAND_CLEAR_POLICIES)
                .putExtra(PolicyTransparencyTestListActivity.EXTRA_MODE,
                        PolicyTransparencyTestListActivity.MODE_DEVICE_OWNER);
        startActivity(intent);
        startActivity(buildCommandIntent(CommandReceiverActivity.COMMAND_REMOVE_MANAGED_PROFILE));
    }
}
