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

import android.app.AlertDialog;
import android.content.Intent;
import android.database.DataSetObserver;
import android.os.Bundle;
import android.provider.Settings;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.EditText;
import android.widget.RadioGroup;

import com.android.cts.verifier.ArrayTestListAdapter;
import com.android.cts.verifier.PassFailButtons;
import com.android.cts.verifier.R;
import com.android.cts.verifier.IntentDrivenTestActivity.ButtonInfo;

import com.android.compatibility.common.util.WifiConfigCreator;
import static com.android.compatibility.common.util.WifiConfigCreator.SECURITY_TYPE_NONE;
import static com.android.compatibility.common.util.WifiConfigCreator.SECURITY_TYPE_WPA;
import static com.android.compatibility.common.util.WifiConfigCreator.SECURITY_TYPE_WEP;

/**
 * Activity to test WiFi configuration lockdown functionality. A locked down WiFi config
 * must not be editable/forgettable in Settings.
 */
public class WifiLockdownTestActivity extends PassFailButtons.TestListActivity {
    private static final String TAG = "WifiLockdownTestActivity";

    private static final int NONE = R.id.device_owner_keymgmnt_none;
    private static final int WPA = R.id.device_owner_keymgmnt_wpa;
    private static final int WEP = R.id.device_owner_keymgmnt_wep;

    private static final String CONFIG_MODIFIABLE_WHEN_UNLOCKED_TEST_ID = "UNLOCKED_MODIFICATION";
    private static final String CONFIG_NOT_MODIFIABLE_WHEN_LOCKED_TEST_ID = "LOCKED_MODIFICATION";
    private static final String CONFIG_CONNECTABLE_WHEN_LOCKED_TEST_ID = "LOCKED_CONNECT";
    private static final String CONFIG_REMOVABLE_WHEN_UNLOCKED_TEST_ID = "UNLOCKED_REMOVE";

    private WifiConfigCreator mConfigCreator;
    private ButtonInfo[] mSwitchLockdownOffButtonInfos;
    private ButtonInfo[] mSwitchLockdownOnButtonInfos;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        mConfigCreator = new WifiConfigCreator(this);
        setContentView(R.layout.wifi_lockdown);
        setInfoResources(R.string.device_owner_wifi_lockdown_test,
                R.string.device_owner_wifi_lockdown_info, 0);
        setPassFailButtonClickListeners();

        final ArrayTestListAdapter adapter = new ArrayTestListAdapter(this);

        final ButtonInfo goToWifiSettings = new ButtonInfo(
                R.string.wifi_lockdown_go_settings_wifi_button,
                new Intent(Settings.ACTION_WIFI_SETTINGS));
        mSwitchLockdownOffButtonInfos = new ButtonInfo[] { new ButtonInfo(
                R.string.switch_wifi_lockdown_off_button,
                new Intent(this, CommandReceiverActivity.class)
                        .putExtra(CommandReceiverActivity.EXTRA_COMMAND,
                                CommandReceiverActivity.COMMAND_SET_GLOBAL_SETTING)
                        .putExtra(CommandReceiverActivity.EXTRA_SETTING,
                                Settings.Global.WIFI_DEVICE_OWNER_CONFIGS_LOCKDOWN)
                        .putExtra(CommandReceiverActivity.EXTRA_VALUE, "0"
                )), goToWifiSettings };
        mSwitchLockdownOnButtonInfos = new ButtonInfo[] { new ButtonInfo(
                R.string.switch_wifi_lockdown_on_button,
                new Intent(this, CommandReceiverActivity.class)
                        .putExtra(CommandReceiverActivity.EXTRA_COMMAND,
                                CommandReceiverActivity.COMMAND_SET_GLOBAL_SETTING)
                        .putExtra(CommandReceiverActivity.EXTRA_SETTING,
                                Settings.Global.WIFI_DEVICE_OWNER_CONFIGS_LOCKDOWN)
                        .putExtra(CommandReceiverActivity.EXTRA_VALUE, "1"
                )), goToWifiSettings };

        addTestsToAdapter(adapter);

        adapter.registerDataSetObserver(new DataSetObserver() {
            @Override
            public void onChanged() {
                updatePassButton();
            }
        });

        setTestListAdapter(adapter);

        View createConfigButton = findViewById(R.id.create_wifi_config_button);
        createConfigButton .setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                EditText ssidEditor = (EditText) findViewById(R.id.device_owner_wifi_ssid);
                RadioGroup authMethods = (RadioGroup) findViewById(
                        R.id.device_owner_keyManagementMethods);
                int checkedRadioId = authMethods.getCheckedRadioButtonId();
                if (checkedRadioId == -1) {
                    checkedRadioId = NONE;
                }
                int netId;
                try {
                    netId = mConfigCreator.addNetwork(ssidEditor.getText().toString(), false,
                            convertKeyManagement(checkedRadioId), "defaultpassword");
                } catch (InterruptedException e) {
                    netId = -1;
                }
                if (netId == -1) {
                    new AlertDialog.Builder(
                            WifiLockdownTestActivity.this)
                            .setIcon(android.R.drawable.ic_dialog_info)
                            .setTitle(R.string.wifi_lockdown_add_network_failed_dialog_title)
                            .setMessage(R.string.wifi_lockdown_add_network_failed_dialog_text)
                            .setPositiveButton(android.R.string.ok, null)
                            .show();
                }
            }
        });
    }

    private void addTestsToAdapter(final ArrayTestListAdapter adapter) {
        adapter.add(Utils.createInteractiveTestItem(this,
                CONFIG_MODIFIABLE_WHEN_UNLOCKED_TEST_ID,
                R.string.device_owner_wifi_config_unlocked_modification_test,
                R.string.device_owner_wifi_config_unlocked_modification_test_info,
                mSwitchLockdownOffButtonInfos));
        adapter.add(Utils.createInteractiveTestItem(this,
                CONFIG_NOT_MODIFIABLE_WHEN_LOCKED_TEST_ID,
                R.string.device_owner_wifi_config_locked_modification_test,
                R.string.device_owner_wifi_config_locked_modification_test_info,
                mSwitchLockdownOnButtonInfos));
        adapter.add(Utils.createInteractiveTestItem(this,
                CONFIG_CONNECTABLE_WHEN_LOCKED_TEST_ID,
                R.string.device_owner_wifi_config_locked_connection_test,
                R.string.device_owner_wifi_config_locked_connection_test_info,
                mSwitchLockdownOnButtonInfos));
        adapter.add(Utils.createInteractiveTestItem(this,
                CONFIG_REMOVABLE_WHEN_UNLOCKED_TEST_ID,
                R.string.device_owner_wifi_config_unlocked_removal_test,
                R.string.device_owner_wifi_config_unlocked_removal_test_info,
                mSwitchLockdownOffButtonInfos));
    }

    private int convertKeyManagement(int radioButtonId) {
        switch (radioButtonId) {
            case NONE: {
                return SECURITY_TYPE_NONE;
            }
            case WPA: {
                return SECURITY_TYPE_WPA;
            }
            case WEP: {
                return SECURITY_TYPE_WEP;
            }
        }
        return SECURITY_TYPE_NONE;
    }
}
