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

package com.android.cts.verifier.wifiaware;

import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.database.DataSetObserver;
import android.net.wifi.aware.WifiAwareManager;
import android.os.Bundle;
import android.provider.Settings;
import android.util.Log;
import android.view.View;
import android.widget.ListView;

import com.android.cts.verifier.ArrayTestListAdapter;
import com.android.cts.verifier.PassFailButtons;
import com.android.cts.verifier.R;
import com.android.cts.verifier.TestListAdapter;

/**
 * Activity listing all Wi-Fi Aware tests.
 */
public class TestListActivity extends PassFailButtons.TestListActivity {
    private static final String TAG = "TestListActivity";

    private WifiAwareManager mWifiAwareManager;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        mWifiAwareManager = (WifiAwareManager) getSystemService(Context.WIFI_AWARE_SERVICE);
        if (mWifiAwareManager == null) {
            Log.wtf(TAG,
                    "Can't get WIFI_AWARE_SERVICE. Should be gated by 'test_required_features'!?");
            return;
        }

        setContentView(R.layout.pass_fail_list);
        setInfoResources(R.string.aware_test, R.string.aware_test_info, 0);
        setPassFailButtonClickListeners();
        getPassButton().setEnabled(false);

        // Add the sub-test/categories
        ArrayTestListAdapter adapter = new ArrayTestListAdapter(this);

        adapter.add(TestListAdapter.TestListItem.newCategory(this,
                R.string.aware_dp_ib_open_unsolicited));
        adapter.add(TestListAdapter.TestListItem.newTest(this,
                R.string.aware_publish,
                DataPathOpenUnsolicitedPublishTestActivity.class.getName(),
                new Intent(this, DataPathOpenUnsolicitedPublishTestActivity.class), null));
        adapter.add(TestListAdapter.TestListItem.newTest(this,
                R.string.aware_subscribe,
                DataPathOpenPassiveSubscribeTestActivity.class.getName(),
                new Intent(this, DataPathOpenPassiveSubscribeTestActivity.class), null));
        adapter.add(TestListAdapter.TestListItem.newCategory(this,
                R.string.aware_dp_ib_passphrase_unsolicited));
        adapter.add(TestListAdapter.TestListItem.newTest(this,
                R.string.aware_publish,
                DataPathPassphraseUnsolicitedPublishTestActivity.class.getName(),
                new Intent(this, DataPathPassphraseUnsolicitedPublishTestActivity.class), null));
        adapter.add(TestListAdapter.TestListItem.newTest(this,
                R.string.aware_subscribe,
                DataPathPassphrasePassiveSubscribeTestActivity.class.getName(),
                new Intent(this, DataPathPassphrasePassiveSubscribeTestActivity.class), null));
        adapter.add(TestListAdapter.TestListItem.newCategory(this,
                R.string.aware_dp_ib_open_solicited));
        adapter.add(TestListAdapter.TestListItem.newTest(this,
                R.string.aware_publish,
                DataPathOpenSolicitedPublishTestActivity.class.getName(),
                new Intent(this, DataPathOpenSolicitedPublishTestActivity.class), null));
        adapter.add(TestListAdapter.TestListItem.newTest(this,
                R.string.aware_subscribe,
                DataPathOpenActiveSubscribeTestActivity.class.getName(),
                new Intent(this, DataPathOpenActiveSubscribeTestActivity.class), null));
        adapter.add(TestListAdapter.TestListItem.newCategory(this,
                R.string.aware_dp_ib_passphrase_solicited));
        adapter.add(TestListAdapter.TestListItem.newTest(this,
                R.string.aware_publish,
                DataPathPassphraseSolicitedPublishTestActivity.class.getName(),
                new Intent(this, DataPathPassphraseSolicitedPublishTestActivity.class), null));
        adapter.add(TestListAdapter.TestListItem.newTest(this,
                R.string.aware_subscribe,
                DataPathPassphraseActiveSubscribeTestActivity.class.getName(),
                new Intent(this, DataPathPassphraseActiveSubscribeTestActivity.class), null));
        adapter.add(TestListAdapter.TestListItem.newCategory(this,
                R.string.aware_dp_oob_open));
        adapter.add(TestListAdapter.TestListItem.newTest(this,
                R.string.aware_responder,
                DataPathOobOpenResponderTestActivity.class.getName(),
                new Intent(this, DataPathOobOpenResponderTestActivity.class), null));
        adapter.add(TestListAdapter.TestListItem.newTest(this,
                R.string.aware_initiator,
                DataPathOobOpenInitiatorTestActivity.class.getName(),
                new Intent(this, DataPathOobOpenInitiatorTestActivity.class), null));
        adapter.add(TestListAdapter.TestListItem.newCategory(this,
                R.string.aware_dp_oob_passphrase));
        adapter.add(TestListAdapter.TestListItem.newTest(this,
                R.string.aware_responder,
                DataPathOobPassphraseResponderTestActivity.class.getName(),
                new Intent(this, DataPathOobPassphraseResponderTestActivity.class), null));
        adapter.add(TestListAdapter.TestListItem.newTest(this,
                R.string.aware_initiator,
                DataPathOobPassphraseInitiatorTestActivity.class.getName(),
                new Intent(this, DataPathOobPassphraseInitiatorTestActivity.class), null));

        adapter.registerDataSetObserver(new DataSetObserver() {
            @Override
            public void onChanged() {
                updatePassButton();
            }

            @Override
            public void onInvalidated() {
                updatePassButton();
            }
        });

        setTestListAdapter(adapter);
    }

    @Override
    protected void handleItemClick(ListView listView, View view, int position, long id) {
        if (!mWifiAwareManager.isAvailable()) {
            showAwareEnableDialog();
            return;
        }

        super.handleItemClick(listView, view, position, id);
    }

    /**
     * Show the dialog to jump to system settings in order to enable
     * WiFi (and by extension WiFi Aware).
     */
    private void showAwareEnableDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setIcon(android.R.drawable.ic_dialog_alert);
        builder.setTitle(R.string.aware_not_enabled);
        builder.setMessage(R.string.aware_not_enabled_message);
        builder.setPositiveButton(R.string.aware_settings,
                new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        startActivity(new Intent(Settings.ACTION_WIFI_SETTINGS));
                    }
                });
        builder.create().show();
    }
}
