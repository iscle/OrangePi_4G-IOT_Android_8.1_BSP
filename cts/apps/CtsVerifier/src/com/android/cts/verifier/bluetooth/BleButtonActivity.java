/*
 * Copyright (C) 2013 The Android Open Source Project
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

package com.android.cts.verifier.bluetooth;

import com.android.cts.verifier.PassFailButtons;
import com.android.cts.verifier.R;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Bundle;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.Button;
import android.widget.Toast;

public class BleButtonActivity extends PassFailButtons.Activity {

    static final int DISCOVER_SERVICE = 0;
    static final int DISCONNECT = 1;

    private int mName;
    private int mInfo;
    private int mButtonText;
    private int mCommand;
    private String mFilter;
    private String mMessage;

    BleButtonActivity(int target) {
        if (target == DISCOVER_SERVICE) {
            mName = R.string.ble_discover_service_name;
            mInfo = R.string.ble_discover_service_info;
            mButtonText = R.string.ble_discover_service;
            mCommand = BleClientService.COMMAND_DISCOVER_SERVICE;
            mFilter = BleClientService.BLE_SERVICES_DISCOVERED;
            mMessage = "Service discovered.";
        } else if (target == DISCONNECT) {
            mName = R.string.ble_client_disconnect_name;
            mInfo = R.string.ble_client_disconnect_name;
            mButtonText = R.string.ble_disconnect;
            mCommand = BleClientService.COMMAND_DISCONNECT;
            mFilter = BleClientService.BLE_BLUETOOTH_DISCONNECTED;
            mMessage = "Bluetooth LE disconnected.";
        }
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.ble_button);
        setPassFailButtonClickListeners();
        setInfoResources(mName, mInfo, -1);
        getPassButton().setEnabled(false);

        ((Button) findViewById(R.id.ble_button)).setText(mButtonText);
        ((Button) findViewById(R.id.ble_button)).setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent intent = new Intent(BleButtonActivity.this, BleClientService.class);
                intent.putExtra(BleClientService.EXTRA_COMMAND, mCommand);
                startService(intent);
            }
        });
    }

    @Override
    public void onResume() {
        super.onResume();
        IntentFilter filter = new IntentFilter();
        filter.addAction(mFilter);
        registerReceiver(onBroadcast, filter);
    }

    @Override
    public void onPause() {
        super.onPause();
        unregisterReceiver(onBroadcast);
    }

    private void showMessage(String msg) {
        Toast.makeText(this, msg, Toast.LENGTH_SHORT).show();
    }

    private BroadcastReceiver onBroadcast = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            showMessage(mMessage);
            getPassButton().setEnabled(true);
        }
    };
}