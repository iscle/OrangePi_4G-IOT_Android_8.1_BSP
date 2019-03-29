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
import android.widget.ListView;
import android.widget.Toast;

import com.android.cts.verifier.PassFailButtons;
import com.android.cts.verifier.R;

import java.util.ArrayList;
import java.util.List;

public class BleEncryptedServerBaseActivity extends PassFailButtons.Activity {

    private TestAdapter mTestAdapter;
    private int mAllPassed;

    private final int WAIT_WRITE_ENCRIPTED_CHARACTERISTIC = 0;
    private final int WAIT_READ_ENCRIPTED_CHARACTERISTIC = 1;
    private final int WAIT_WRITE_ENCRIPTED_DESCRIPTOR = 2;
    private final int WAIT_READ_ENCRIPTED_DESCRIPTOR = 3;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.ble_encrypted_server_test);
        setPassFailButtonClickListeners();
        setInfoResources(R.string.ble_encrypted_server_name,
                R.string.ble_encrypted_server_info, -1);

        getPassButton().setEnabled(false);

        mTestAdapter = new TestAdapter(this, setupTestList());
        ListView listView = (ListView) findViewById(R.id.ble_server_enctypted_tests);
        listView.setAdapter(mTestAdapter);

        startService(new Intent(this, BleEncryptedServerService.class));
    }

    @Override
    public void onResume() {
        super.onResume();

        IntentFilter filter = new IntentFilter();
        filter.addAction(BleEncryptedServerService.INTENT_BLUETOOTH_DISABLED);
        filter.addAction(BleEncryptedServerService.INTENT_WAIT_WRITE_ENCRYPTED_CHARACTERISTIC);
        filter.addAction(BleEncryptedServerService.INTENT_WAIT_READ_ENCRYPTED_CHARACTERISTIC);
        filter.addAction(BleEncryptedServerService.INTENT_WAIT_WRITE_ENCRYPTED_DESCRIPTOR);
        filter.addAction(BleEncryptedServerService.INTENT_WAIT_READ_ENCRYPTED_DESCRIPTOR);
        filter.addAction(BleServerService.BLE_ADVERTISE_UNSUPPORTED);
        filter.addAction(BleServerService.BLE_OPEN_FAIL);
        registerReceiver(mBroadcast, filter);
    }

    @Override
    public void onPause() {
        super.onPause();
        unregisterReceiver(mBroadcast);
    }

    private List<Integer> setupTestList() {
        ArrayList<Integer> testList = new ArrayList<Integer>();
        testList.add(R.string.ble_server_write_characteristic_need_encrypted);
        testList.add(R.string.ble_server_read_characteristic_need_encrypted);
        testList.add(R.string.ble_server_write_descriptor_need_encrypted);
        testList.add(R.string.ble_server_read_descriptor_need_encrypted);
        return testList;
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        stopService(new Intent(this, BleConnectionPriorityServerService.class));
    }

    private BroadcastReceiver mBroadcast = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            switch (action) {
            case BleEncryptedServerService.INTENT_BLUETOOTH_DISABLED:
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
            case BleEncryptedServerService.INTENT_WAIT_WRITE_ENCRYPTED_CHARACTERISTIC:
                mTestAdapter.setTestPass(WAIT_WRITE_ENCRIPTED_CHARACTERISTIC);
                mAllPassed |= 0x01;
                break;
            case BleEncryptedServerService.INTENT_WAIT_READ_ENCRYPTED_CHARACTERISTIC:
                mTestAdapter.setTestPass(WAIT_READ_ENCRIPTED_CHARACTERISTIC);
                mAllPassed |= 0x02;
                break;
            case BleEncryptedServerService.INTENT_WAIT_WRITE_ENCRYPTED_DESCRIPTOR:
                mTestAdapter.setTestPass(WAIT_WRITE_ENCRIPTED_DESCRIPTOR);
                mAllPassed |= 0x04;
                break;
            case BleEncryptedServerService.INTENT_WAIT_READ_ENCRYPTED_DESCRIPTOR:
                mTestAdapter.setTestPass(WAIT_READ_ENCRIPTED_DESCRIPTOR);
                mAllPassed |= 0x08;
                break;
            case BleServerService.BLE_ADVERTISE_UNSUPPORTED:
                showErrorDialog(R.string.bt_advertise_unsupported_title, R.string.bt_advertise_unsupported_message, true);
                break;
            case BleServerService.BLE_OPEN_FAIL:
                setTestResultAndFinish(false);
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        Toast.makeText(BleEncryptedServerBaseActivity.this, R.string.bt_open_failed_message, Toast.LENGTH_SHORT).show();
                    }
                });
                break;
            }
            mTestAdapter.notifyDataSetChanged();
            if (mAllPassed == 0x0F) {
                getPassButton().setEnabled(true);
            }
        }
    };

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
}
