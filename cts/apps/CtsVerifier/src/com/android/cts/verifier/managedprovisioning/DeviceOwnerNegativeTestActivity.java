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

import android.app.Activity;
import android.app.admin.DevicePolicyManager;
import android.content.ComponentName;
import android.content.Intent;
import android.database.DataSetObserver;
import android.os.Bundle;
import android.provider.Settings;

import com.android.cts.verifier.ArrayTestListAdapter;
import com.android.cts.verifier.IntentDrivenTestActivity;
import com.android.cts.verifier.IntentDrivenTestActivity.ButtonInfo;
import com.android.cts.verifier.IntentDrivenTestActivity.TestInfo;
import com.android.cts.verifier.PassFailButtons;
import com.android.cts.verifier.R;
import com.android.cts.verifier.TestListAdapter.TestListItem;

import static com.android.cts.verifier.managedprovisioning.Utils.createInteractiveTestItem;

/**
 * Activity that lists all device owner negative tests.
 */
public class DeviceOwnerNegativeTestActivity extends PassFailButtons.TestListActivity {

    private static final String DEVICE_OWNER_PROVISIONING_NEGATIVE
            = "DEVICE_OWNER_PROVISIONING_NEGATIVE";
    private static final String ENTERPRISE_PRIVACY_QUICK_SETTINGS_NEGATIVE
            = "ENTERPRISE_PRIVACY_QUICK_SETTINGS_NEGATIVE";
    private static final String ENTERPRISE_PRIVACY_KEYGUARD_NEGATIVE
            = "ENTERPRISE_PRIVACY_KEYGUARD_NEGATIVE";
    private static final String ENTERPRISE_PRIVACY_ADD_ACCOUNT_NEGATIVE
            = "ENTERPRISE_PRIVACY_ADD_ACCOUNT_NEGATIVE";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.pass_fail_list);
        setPassFailButtonClickListeners();

        final ArrayTestListAdapter adapter = new ArrayTestListAdapter(this);
        adapter.add(TestListItem.newCategory(this, R.string.device_owner_negative_category));

        addTestsToAdapter(adapter);

        adapter.registerDataSetObserver(new DataSetObserver() {
            @Override
            public void onChanged() {
                updatePassButton();
            }
        });

        setTestListAdapter(adapter);
    }

    private void addTestsToAdapter(final ArrayTestListAdapter adapter) {
        final TestInfo provisioningNegativeTestInfo = new TestInfo(
                DEVICE_OWNER_PROVISIONING_NEGATIVE,
                R.string.device_owner_provisioning_negative,
                R.string.device_owner_provisioning_negative_info,
                new ButtonInfo(
                        R.string.start_device_owner_provisioning_button,
                        new Intent(this, TrampolineActivity.class)));
        final Intent startTestIntent = new Intent(this, IntentDrivenTestActivity.class)
                    .putExtra(IntentDrivenTestActivity.EXTRA_ID,
                            provisioningNegativeTestInfo.getTestId())
                    .putExtra(IntentDrivenTestActivity.EXTRA_TITLE,
                            provisioningNegativeTestInfo.getTitle())
                    .putExtra(IntentDrivenTestActivity.EXTRA_INFO,
                            provisioningNegativeTestInfo.getInfoText())
                    .putExtra(IntentDrivenTestActivity.EXTRA_BUTTONS,
                            provisioningNegativeTestInfo.getButtons());
        adapter.add(TestListItem.newTest(this, provisioningNegativeTestInfo.getTitle(),
                provisioningNegativeTestInfo.getTestId(), startTestIntent, null));
        adapter.add(TestListItem.newTest(this, R.string.enterprise_privacy_quick_settings_negative,
                ENTERPRISE_PRIVACY_QUICK_SETTINGS_NEGATIVE,
                new Intent(this, EnterprisePrivacyInfoOnlyTestActivity.class)
                        .putExtra(EnterprisePrivacyInfoOnlyTestActivity.EXTRA_ID,
                                ENTERPRISE_PRIVACY_QUICK_SETTINGS_NEGATIVE)
                        .putExtra(EnterprisePrivacyInfoOnlyTestActivity.EXTRA_TITLE,
                                R.string.enterprise_privacy_quick_settings_negative)
                        .putExtra(EnterprisePrivacyInfoOnlyTestActivity.EXTRA_INFO,
                                R.string.enterprise_privacy_quick_settings_negative_info),
                        null));
        adapter.add(createInteractiveTestItem(this, ENTERPRISE_PRIVACY_KEYGUARD_NEGATIVE,
                R.string.enterprise_privacy_keyguard_negative,
                R.string.enterprise_privacy_keyguard_negative_info,
                new ButtonInfo(R.string.go_button_text, new Intent(Settings.ACTION_SETTINGS))));
        adapter.add(createInteractiveTestItem(this, ENTERPRISE_PRIVACY_ADD_ACCOUNT_NEGATIVE,
                R.string.enterprise_privacy_add_account_negative,
                R.string.enterprise_privacy_add_account_negative_info,
                new ButtonInfo(R.string.go_button_text, new Intent(Settings.ACTION_ADD_ACCOUNT))));
    }

    /**
     * This is needed because IntentDrivenTestActivity fires the intent by startActivity when
     * a button is clicked, but ACTION_PROVISION_MANAGED_DEVICE requires to be fired by
     * startActivityForResult.
     */
    public static class TrampolineActivity extends Activity {
        @Override
        protected void onCreate(Bundle savedInstanceState) {
            super.onCreate(savedInstanceState);
            Intent provisionDeviceIntent = new Intent(
                    DevicePolicyManager.ACTION_PROVISION_MANAGED_DEVICE);
            provisionDeviceIntent.putExtra(
                    DevicePolicyManager.EXTRA_PROVISIONING_DEVICE_ADMIN_COMPONENT_NAME,
                    new ComponentName(this, DeviceAdminTestReceiver.class.getName()));
            startActivityForResult(provisionDeviceIntent, 0);
        }

        @Override
        protected void onActivityResult(int requestCode, int resultCode, Intent data) {
            super.onActivityResult(requestCode, resultCode, data);
            finish();
        }
    }
}

