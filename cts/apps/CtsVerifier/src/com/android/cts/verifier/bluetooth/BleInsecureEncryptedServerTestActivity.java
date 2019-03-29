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

package com.android.cts.verifier.bluetooth;

import android.app.AlertDialog;
import android.app.Dialog;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Bundle;
import android.widget.Toast;

import com.android.cts.verifier.PassFailButtons;
import com.android.cts.verifier.R;

public class BleInsecureEncryptedServerTestActivity extends PassFailButtons.Activity {
    private Intent mIntent;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.ble_insecure_encrypted_server_test);
        setPassFailButtonClickListeners();
        setInfoResources(R.string.ble_encrypted_server_name,
                R.string.ble_encrypted_server_info, -1);

        getPassButton().setEnabled(true);

        mIntent =  new Intent(this, BleEncryptedServerService.class);
        mIntent.setAction(BleEncryptedServerService.ACTION_CONNECT_WITHOUT_SECURE);

        startService(mIntent);
    }

    @Override
    public void onResume() {
        super.onResume();

        IntentFilter filter = new IntentFilter();
        filter.addAction(BleEncryptedServerService.INTENT_BLUETOOTH_DISABLED);
        filter.addAction(BleServerService.BLE_OPEN_FAIL);
        filter.addAction(BleServerService.BLE_ADVERTISE_UNSUPPORTED);
        registerReceiver(mBroadcast, filter);
    }

    @Override
    public void onPause() {
        super.onPause();
        unregisterReceiver(mBroadcast);
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        stopService(mIntent);
    }

    private void showErrorDialog(int titleId, int messageId, boolean finish) {
        AlertDialog.Builder builder = new AlertDialog.Builder(this)
                .setTitle(titleId)
                .setMessage(messageId);
        if (finish) {
            builder.setOnCancelListener(new Dialog.OnCancelListener() {
                @Override
                public void onCancel(DialogInterface dialog) {
                    finish();
                }
            });
        }
        builder.create().show();
    }

    private BroadcastReceiver mBroadcast = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            switch (action) {
            case BleEncryptedServerService.INTENT_BLUETOOTH_DISABLED:
                // show message to turn on Bluetooth
                new AlertDialog.Builder(context)
                        .setTitle(R.string.ble_bluetooth_disable_title)
                        .setMessage(R.string.ble_bluetooth_disable_message)
                        .setOnCancelListener(new Dialog.OnCancelListener() {
                            @Override
                            public void onCancel(DialogInterface dialog) {
                                finish();
                            }
                        })
                        .create().show();
                break;
            case BleServerService.BLE_ADVERTISE_UNSUPPORTED:
                showErrorDialog(R.string.bt_advertise_unsupported_title,
                        R.string.bt_advertise_unsupported_message,
                        true);
                break;
            case BleServerService.BLE_OPEN_FAIL:
                setTestResultAndFinish(false);
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        Toast.makeText(BleInsecureEncryptedServerTestActivity.this,
                                R.string.bt_open_failed_message,
                                Toast.LENGTH_SHORT).show();
                    }
                });
                break;
            }
        }
    };
}