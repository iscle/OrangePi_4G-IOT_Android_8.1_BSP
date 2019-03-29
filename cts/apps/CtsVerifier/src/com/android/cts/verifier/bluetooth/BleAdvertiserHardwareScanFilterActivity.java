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

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Bundle;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.Button;
import android.widget.Toast;

public class BleAdvertiserHardwareScanFilterActivity extends PassFailButtons.Activity {

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.ble_advertiser_hardware_scan_filter);
        setPassFailButtonClickListeners();
        setInfoResources(R.string.ble_advertiser_scan_filter_name,
                         R.string.ble_advertiser_scan_filter_info, -1);

        ((Button) findViewById(R.id.ble_advertiser_scannable_start))
            .setOnClickListener(new OnClickListener() {
                @Override
                public void onClick(View v) {
                    Intent intent = new Intent(BleAdvertiserHardwareScanFilterActivity.this,
                                               BleAdvertiserService.class);
                    intent.putExtra(BleAdvertiserService.EXTRA_COMMAND,
                                    BleAdvertiserService.COMMAND_START_SCANNABLE);
                    startService(intent);
                }
            });
        ((Button)findViewById(R.id.ble_advertiser_scannable_stop))
            .setOnClickListener(new OnClickListener() {
                @Override
                public void onClick(View v) {
                    stopAdvertising();
                }
            });
        ((Button)findViewById(R.id.ble_advertiser_unscannable_start))
            .setOnClickListener(new OnClickListener() {
                @Override
                public void onClick(View v) {
                    Intent intent = new Intent(BleAdvertiserHardwareScanFilterActivity.this,
                                               BleAdvertiserService.class);
                    intent.putExtra(BleAdvertiserService.EXTRA_COMMAND,
                                    BleAdvertiserService.COMMAND_START_UNSCANNABLE);
                    startService(intent);
                }
        });
        ((Button)findViewById(R.id.ble_advertiser_unscannable_stop))
            .setOnClickListener(new OnClickListener() {
                @Override
                public void onClick(View v) {
                    stopAdvertising();
                }
        });
    }

    @Override
    public void onResume() {
        super.onResume();
        IntentFilter filter = new IntentFilter();
        filter.addAction(BleAdvertiserService.BLE_ADV_NOT_SUPPORT);
        filter.addAction(BleAdvertiserService.BLE_START_SCANNABLE);
        filter.addAction(BleAdvertiserService.BLE_START_UNSCANNABLE);
        filter.addAction(BleAdvertiserService.BLE_STOP_SCANNABLE);
        filter.addAction(BleAdvertiserService.BLE_STOP_UNSCANNABLE);
        registerReceiver(onBroadcast, filter);
    }

    @Override
    public void onPause() {
        super.onPause();
        unregisterReceiver(onBroadcast);
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        stopAdvertising();
    }

    private void showMessage(String msg) {
        Toast.makeText(this, msg, Toast.LENGTH_SHORT).show();
    }

    private void pass() {
        this.setTestResultAndFinish(true);
    }

    private void stopAdvertising() {
        Intent intent = new Intent(BleAdvertiserHardwareScanFilterActivity.this,
                                   BleAdvertiserService.class);
        intent.putExtra(BleAdvertiserService.EXTRA_COMMAND,
                        BleAdvertiserService.COMMAND_STOP_SCANNABLE);
        intent.putExtra(BleAdvertiserService.EXTRA_COMMAND,
                        BleAdvertiserService.COMMAND_STOP_UNSCANNABLE);
        startService(intent);
    }

    private BroadcastReceiver onBroadcast = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            switch (intent.getAction()) {
                case BleAdvertiserService.BLE_START_SCANNABLE:
                    showMessage("Start advertising, this should be scanned");
                    break;
                case BleAdvertiserService.BLE_START_UNSCANNABLE:
                    showMessage("Start advertising, this should not be scanned");
                    break;
                case BleAdvertiserService.BLE_STOP_SCANNABLE:
                case BleAdvertiserService.BLE_STOP_UNSCANNABLE:
                    showMessage("Stop advertising");
                    break;
                case BleAdvertiserService.BLE_ADV_NOT_SUPPORT:
                    pass();
                    break;
            }
        }
    };
}
