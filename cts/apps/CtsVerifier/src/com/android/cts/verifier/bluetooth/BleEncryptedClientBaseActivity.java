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
import android.app.ProgressDialog;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Bundle;
import android.os.Handler;
import android.util.Log;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ListView;

import com.android.cts.verifier.PassFailButtons;
import com.android.cts.verifier.R;

import java.util.ArrayList;
import java.util.List;

public class BleEncryptedClientBaseActivity extends PassFailButtons.Activity {

    private TestAdapter mTestAdapter;
    private int mAllPassed;
    private Dialog mDialog;
    private Handler mHandler;

    private final int BLE_READ_ENCRIPTED_CHARACTERISTIC = 0;
    private final int BLE_WRITE_ENCRIPTED_CHARACTERISTIC = 1;
    private final int BLE_READ_ENCRIPTED_DESCRIPTOR = 2;
    private final int BLE_WRITE_ENCRIPTED_DESCRIPTOR = 3;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.ble_encrypted_client_test);
        setPassFailButtonClickListeners();
        setInfoResources(R.string.ble_encrypted_client_name,
                R.string.ble_encrypted_client_info, -1);
        getPassButton().setEnabled(false);

        mHandler = new Handler();

        mTestAdapter = new TestAdapter(this, setupTestList());
        ListView listView = (ListView) findViewById(R.id.ble_client_enctypted_tests);
        listView.setAdapter(mTestAdapter);
        listView.setOnItemClickListener(new ListView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> adapterView, View view, int position, long id) {
                Intent intent = new Intent(BleEncryptedClientBaseActivity.this, BleEncryptedClientService.class);
                Log.v(getLocalClassName(), "onItemClick()");
                switch (position) {
                case BLE_WRITE_ENCRIPTED_CHARACTERISTIC:
                    intent.setAction(BleEncryptedClientService.ACTION_WRITE_ENCRYPTED_CHARACTERISTIC);
                    break;
                case BLE_READ_ENCRIPTED_CHARACTERISTIC:
                    intent.setAction(BleEncryptedClientService.ACTION_READ_ENCRYPTED_CHARACTERISTIC);
                    break;
                case BLE_WRITE_ENCRIPTED_DESCRIPTOR:
                    intent.setAction(BleEncryptedClientService.ACTION_WRITE_ENCRYPTED_DESCRIPTOR);
                    break;
                case BLE_READ_ENCRIPTED_DESCRIPTOR:
                    intent.setAction(BleEncryptedClientService.ACTION_READ_ENCRYPTED_DESCRIPTOR);
                    break;
                default:
                    return;
                }
                startService(intent);
                showProgressDialog();
            }
        });

        mAllPassed = 0;
    }

    @Override
    public void onResume() {
        super.onResume();

        IntentFilter filter = new IntentFilter();
        filter.addAction(BleEncryptedClientService.INTENT_BLE_BLUETOOTH_DISABLED);
        filter.addAction(BleEncryptedClientService.INTENT_BLE_WRITE_ENCRYPTED_CHARACTERISTIC);
        filter.addAction(BleEncryptedClientService.INTENT_BLE_READ_ENCRYPTED_CHARACTERISTIC);
        filter.addAction(BleEncryptedClientService.INTENT_BLE_WRITE_ENCRYPTED_DESCRIPTOR);
        filter.addAction(BleEncryptedClientService.INTENT_BLE_READ_ENCRYPTED_DESCRIPTOR);
        filter.addAction(BleEncryptedClientService.INTENT_BLE_WRITE_NOT_ENCRYPTED_CHARACTERISTIC);
        filter.addAction(BleEncryptedClientService.INTENT_BLE_READ_NOT_ENCRYPTED_CHARACTERISTIC);
        filter.addAction(BleEncryptedClientService.INTENT_BLE_WRITE_NOT_ENCRYPTED_DESCRIPTOR);
        filter.addAction(BleEncryptedClientService.INTENT_BLE_READ_NOT_ENCRYPTED_DESCRIPTOR);
        filter.addAction(BleEncryptedClientService.INTENT_BLE_WRITE_FAIL_ENCRYPTED_CHARACTERISTIC);
        filter.addAction(BleEncryptedClientService.INTENT_BLE_READ_FAIL_ENCRYPTED_CHARACTERISTIC);
        filter.addAction(BleEncryptedClientService.INTENT_BLE_WRITE_FAIL_ENCRYPTED_DESCRIPTOR);
        filter.addAction(BleEncryptedClientService.INTENT_BLE_READ_FAIL_ENCRYPTED_DESCRIPTOR);
        filter.addAction(BleEncryptedClientService.ACTION_DISCONNECTED);
        registerReceiver(mBroadcast, filter);
    }

    @Override
    public void onPause() {
        super.onPause();
        unregisterReceiver(mBroadcast);
        closeDialog();
    }

    private List<Integer> setupTestList() {
        ArrayList<Integer> testList = new ArrayList<Integer>();
        testList.add(R.string.ble_read_authenticated_characteristic_name);
        testList.add(R.string.ble_write_authenticated_characteristic_name);
        testList.add(R.string.ble_read_authenticated_descriptor_name);
        testList.add(R.string.ble_write_authenticated_descriptor_name);
        return testList;
    }

    private void showErrorDialog(String title, String message, boolean finish) {
        closeDialog();

        AlertDialog.Builder builder = new AlertDialog.Builder(this)
                .setTitle(title)
                .setMessage(message);
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

    private synchronized void closeDialog() {
        if (mDialog != null) {
            mDialog.dismiss();
            mDialog = null;
        }
    }

    private synchronized void showProgressDialog() {
        closeDialog();

        ProgressDialog dialog = new ProgressDialog(this);
        dialog.setTitle(R.string.ble_test_running);
        dialog.setProgressStyle(ProgressDialog.STYLE_SPINNER);
        dialog.setMessage(getString(R.string.ble_test_running_message));
        dialog.setCanceledOnTouchOutside(false);
        mDialog = dialog;
        mDialog.show();
    }

    public boolean shouldRebootBluetoothAfterTest() {
        return false;
    }

    public boolean isSecure() { return false; }

    private BroadcastReceiver mBroadcast = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            switch (action) {
            case BleEncryptedClientService.INTENT_BLE_BLUETOOTH_DISABLED:
                showErrorDialog(getString(R.string.ble_bluetooth_disable_title), getString(R.string.ble_bluetooth_disable_message), true);
                break;
            case BleEncryptedClientService.INTENT_BLE_WRITE_ENCRYPTED_CHARACTERISTIC:
                mTestAdapter.setTestPass(BLE_WRITE_ENCRIPTED_CHARACTERISTIC);
                mAllPassed |= 0x01;
                if (!isSecure()) {
                    closeDialog();
                }
                break;
            case BleEncryptedClientService.INTENT_BLE_READ_ENCRYPTED_CHARACTERISTIC:
                mTestAdapter.setTestPass(BLE_READ_ENCRIPTED_CHARACTERISTIC);
                mAllPassed |= 0x02;
                if (!isSecure()) {
                    closeDialog();
                }
                break;
            case BleEncryptedClientService.INTENT_BLE_WRITE_ENCRYPTED_DESCRIPTOR:
                mTestAdapter.setTestPass(BLE_WRITE_ENCRIPTED_DESCRIPTOR);
                mAllPassed |= 0x04;
                if (!isSecure()) {
                    closeDialog();
                }
                break;
            case BleEncryptedClientService.INTENT_BLE_READ_ENCRYPTED_DESCRIPTOR:
                mTestAdapter.setTestPass(BLE_READ_ENCRIPTED_DESCRIPTOR);
                mAllPassed |= 0x08;
                if (!isSecure()) {
                    closeDialog();
                }
                break;
            case BleEncryptedClientService.INTENT_BLE_WRITE_NOT_ENCRYPTED_CHARACTERISTIC:
                showErrorDialog(getString(R.string.ble_encrypted_client_name), getString(R.string.ble_encrypted_client_no_encrypted_characteristic), false);
                break;
            case BleEncryptedClientService.INTENT_BLE_READ_NOT_ENCRYPTED_CHARACTERISTIC:
                showErrorDialog(getString(R.string.ble_encrypted_client_name), getString(R.string.ble_encrypted_client_no_encrypted_characteristic), false);
                break;
            case BleEncryptedClientService.INTENT_BLE_WRITE_NOT_ENCRYPTED_DESCRIPTOR:
                showErrorDialog(getString(R.string.ble_encrypted_client_name), getString(R.string.ble_encrypted_client_no_encrypted_descriptor), false);
                break;
            case BleEncryptedClientService.INTENT_BLE_READ_NOT_ENCRYPTED_DESCRIPTOR:
                showErrorDialog(getString(R.string.ble_encrypted_client_name), getString(R.string.ble_encrypted_client_no_encrypted_descriptor), false);
                break;

            case BleEncryptedClientService.INTENT_BLE_WRITE_FAIL_ENCRYPTED_CHARACTERISTIC:
                showErrorDialog(getString(R.string.ble_encrypted_client_name), getString(R.string.ble_encrypted_client_fail_write_encrypted_characteristic), false);
                break;
            case BleEncryptedClientService.INTENT_BLE_READ_FAIL_ENCRYPTED_CHARACTERISTIC:
                showErrorDialog(getString(R.string.ble_encrypted_client_name), getString(R.string.ble_encrypted_client_fail_read_encrypted_characteristic), false);
                break;
            case BleEncryptedClientService.INTENT_BLE_WRITE_FAIL_ENCRYPTED_DESCRIPTOR:
                showErrorDialog(getString(R.string.ble_encrypted_client_name), getString(R.string.ble_encrypted_client_fail_write_encrypted_descriptor), false);
                break;
            case BleEncryptedClientService.INTENT_BLE_READ_FAIL_ENCRYPTED_DESCRIPTOR:
                showErrorDialog(getString(R.string.ble_encrypted_client_name), getString(R.string.ble_encrypted_client_fail_read_encrypted_descriptor), false);
                break;

            case BleEncryptedClientService.ACTION_DISCONNECTED:
                if (shouldRebootBluetoothAfterTest()) {
                    mBtPowerSwitcher.executeSwitching();
                } else {
                    closeDialog();
                }
                break;
            }

            mTestAdapter.notifyDataSetChanged();
            if (mAllPassed == 0x0F) {
                getPassButton().setEnabled(true);
            }
        }
    };

    private static final long BT_ON_DELAY = 10000;
    private final BluetoothPowerSwitcher mBtPowerSwitcher = new BluetoothPowerSwitcher();
    private class BluetoothPowerSwitcher extends BroadcastReceiver {

        private boolean mIsSwitching = false;
        private BluetoothAdapter mAdapter;

        public void executeSwitching() {
            if (mAdapter == null) {
                BluetoothManager btMgr = (BluetoothManager) getSystemService(Context.BLUETOOTH_SERVICE);
                mAdapter = btMgr.getAdapter();
            }

            if (!mIsSwitching) {
                IntentFilter filter = new IntentFilter(BluetoothAdapter.ACTION_STATE_CHANGED);
                registerReceiver(this, filter);
                mIsSwitching = true;
                mHandler.postDelayed(new Runnable() {
                    @Override
                    public void run() {
                        mAdapter.disable();
                    }
                }, 1000);
            }
        }

        @Override
        public void onReceive(Context context, Intent intent) {
            if (intent.getAction().equals(BluetoothAdapter.ACTION_STATE_CHANGED)) {
                int state = intent.getIntExtra(BluetoothAdapter.EXTRA_STATE, -1);
                if (state == BluetoothAdapter.STATE_OFF) {
                    mHandler.postDelayed(new Runnable() {
                        @Override
                        public void run() {
                            mAdapter.enable();
                        }
                    }, BT_ON_DELAY);
                } else if (state == BluetoothAdapter.STATE_ON) {
                    mIsSwitching = false;
                    unregisterReceiver(this);
                    getPassButton().setEnabled(true);
                    closeDialog();
                }
            }
        }
    }
}
