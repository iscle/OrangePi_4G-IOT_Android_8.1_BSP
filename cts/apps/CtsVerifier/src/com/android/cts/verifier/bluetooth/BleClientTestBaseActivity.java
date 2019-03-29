/*
 * Copyright (C) 2014 The Android Open Source Project
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
import android.widget.ListView;

import com.android.cts.verifier.PassFailButtons;
import com.android.cts.verifier.R;

import java.util.ArrayList;
import java.util.List;

public class BleClientTestBaseActivity extends PassFailButtons.Activity {
    public static final String TAG = "BleClientTestBase";

    private static final boolean STEP_EXECUTION = false;

    private static final int PASS_FLAG_CONNECT = 0x1;
    private static final int PASS_FLAG_DISCOVER = 0x2;
    private static final int PASS_FLAG_READ_CHARACTERISTIC = 0x4;
    private static final int PASS_FLAG_WRITE_CHARACTERISTIC = 0x8;
    private static final int PASS_FLAG_RELIABLE_WRITE = 0x10;
    private static final int PASS_FLAG_NOTIFY_CHARACTERISTIC = 0x20;
    private static final int PASS_FLAG_READ_DESCRIPTOR = 0x40;
    private static final int PASS_FLAG_WRITE_DESCRIPTOR = 0x80;
    private static final int PASS_FLAG_READ_RSSI = 0x100;
    private static final int PASS_FLAG_DISCONNECT = 0x200;
    private static final int PASS_FLAG_READ_CHARACTERISTIC_NO_PERMISSION = 0x400;
    private static final int PASS_FLAG_WRITE_CHARACTERISTIC_NO_PERMISSION = 0x800;
    private static final int PASS_FLAG_READ_DESCRIPTOR_NO_PERMISSION = 0x1000;
    private static final int PASS_FLAG_WRITE_DESCRIPTOR_NO_PERMISSION = 0x2000;
    private static final int PASS_FLAG_MTU_CHANGE_23BYTES = 0x4000;
    private static final int PASS_FLAG_INDICATE_CHARACTERISTIC = 0x8000;
    private static final int PASS_FLAG_MTU_CHANGE_512BYTES = 0x10000;
    private static final int PASS_FLAG_RELIABLE_WRITE_BAD_RESP = 0x20000;
    private static final int PASS_FLAG_ALL = 0x3FFFF;

    private final int BLE_CLIENT_CONNECT = 0;
    private final int BLE_BLE_DISVOCER_SERVICE = 1;
    private final int BLE_READ_CHARACTERISTIC = 2;
    private final int BLE_WRITE_CHARACTERISTIC = 3;
    private final int BLE_REQUEST_MTU_23BYTES = 4;
    private final int BLE_REQUEST_MTU_512BYTES = 5;
    private final int BLE_READ_CHARACTERISTIC_NO_PERMISSION = 6;
    private final int BLE_WRITE_CHARACTERISTIC_NO_PERMISSION = 7;
    private final int BLE_RELIABLE_WRITE = 8;
    private final int BLE_RELIABLE_WRITE_BAD_RESP = 9;
    private final int BLE_NOTIFY_CHARACTERISTIC = 9;    //10;
    private final int BLE_INDICATE_CHARACTERISTIC = 10;  //11;
    private final int BLE_READ_DESCRIPTOR = 11; //12;
    private final int BLE_WRITE_DESCRIPTOR = 12;    //13;
    private final int BLE_READ_DESCRIPTOR_NO_PERMISSION = 13;   //14;
    private final int BLE_WRITE_DESCRIPTOR_NO_PERMISSION = 14;  //15;
    private final int BLE_READ_RSSI = 15;   //16;
    private final int BLE_CLIENT_DISCONNECT = 15;   //16;   //17;

    private TestAdapter mTestAdapter;
    private long mPassed;
    private Dialog mDialog;
    private Handler mHandler;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.ble_server_start);
        setPassFailButtonClickListeners();
        setInfoResources(R.string.ble_client_test_name,
                R.string.ble_client_test_info, -1);
        getPassButton().setEnabled(false);

        mTestAdapter = new TestAdapter(this, setupTestList());
        ListView listView = (ListView) findViewById(R.id.ble_server_tests);
        listView.setAdapter(mTestAdapter);

        mPassed = 0;
        mHandler = new Handler();
    }

    @Override
    public void onResume() {
        super.onResume();

        IntentFilter filter = new IntentFilter();
        filter.addAction(BleClientService.BLE_BLUETOOTH_CONNECTED);
        filter.addAction(BleClientService.BLE_BLUETOOTH_DISCONNECTED);
        filter.addAction(BleClientService.BLE_SERVICES_DISCOVERED);
        filter.addAction(BleClientService.BLE_MTU_CHANGED_23BYTES);
        filter.addAction(BleClientService.BLE_MTU_CHANGED_512BYTES);
        filter.addAction(BleClientService.BLE_CHARACTERISTIC_READ);
        filter.addAction(BleClientService.BLE_CHARACTERISTIC_WRITE);
        filter.addAction(BleClientService.BLE_CHARACTERISTIC_CHANGED);
        filter.addAction(BleClientService.BLE_DESCRIPTOR_READ);
        filter.addAction(BleClientService.BLE_DESCRIPTOR_WRITE);
        filter.addAction(BleClientService.BLE_RELIABLE_WRITE_COMPLETED);
        filter.addAction(BleClientService.BLE_RELIABLE_WRITE_BAD_RESP_COMPLETED);
        filter.addAction(BleClientService.BLE_READ_REMOTE_RSSI);
        filter.addAction(BleClientService.BLE_CHARACTERISTIC_READ_NOPERMISSION);
        filter.addAction(BleClientService.BLE_CHARACTERISTIC_WRITE_NOPERMISSION);
        filter.addAction(BleClientService.BLE_DESCRIPTOR_READ_NOPERMISSION);
        filter.addAction(BleClientService.BLE_DESCRIPTOR_WRITE_NOPERMISSION);
        filter.addAction(BleClientService.BLE_CHARACTERISTIC_INDICATED);
        filter.addAction(BleClientService.BLE_BLUETOOTH_DISABLED);
        filter.addAction(BleClientService.BLE_BLUETOOTH_MISMATCH_SECURE);
        filter.addAction(BleClientService.BLE_BLUETOOTH_MISMATCH_INSECURE);
        filter.addAction(BleClientService.BLE_CLIENT_ERROR);

        registerReceiver(mBroadcast, filter);
    }

    @Override
    public void onPause() {
        super.onPause();
        unregisterReceiver(mBroadcast);
        closeDialog();
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

    private List<Integer> setupTestList() {
        ArrayList<Integer> testList = new ArrayList<Integer>();
        testList.add(R.string.ble_client_connect_name);
        testList.add(R.string.ble_discover_service_name);
        testList.add(R.string.ble_read_characteristic_name);
        testList.add(R.string.ble_write_characteristic_name);
        testList.add(R.string.ble_mtu_23_name);
        testList.add(R.string.ble_mtu_512_name);
        testList.add(R.string.ble_read_characteristic_nopermission_name);
        testList.add(R.string.ble_write_characteristic_nopermission_name);
        testList.add(R.string.ble_reliable_write_name);
//        testList.add(R.string.ble_reliable_write_bad_resp_name);
        testList.add(R.string.ble_notify_characteristic_name);
        testList.add(R.string.ble_indicate_characteristic_name);
        testList.add(R.string.ble_read_descriptor_name);
        testList.add(R.string.ble_write_descriptor_name);
        testList.add(R.string.ble_read_descriptor_nopermission_name);
        testList.add(R.string.ble_write_descriptor_nopermission_name);
// TODO: too flaky b/34951749
//        testList.add(R.string.ble_read_rssi_name);
        testList.add(R.string.ble_client_disconnect_name);

        return testList;
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

    public boolean shouldRebootBluetoothAfterTest() {
        return false;
    }

    private BroadcastReceiver mBroadcast = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            boolean showProgressDialog = false;
            closeDialog();

            String action = intent.getAction();
            String newAction = null;
            String actionName = null;
            final Intent startIntent = new Intent(BleClientTestBaseActivity.this, BleClientService.class);
            switch (action) {
            case BleClientService.BLE_BLUETOOTH_DISABLED:
                showErrorDialog(R.string.ble_bluetooth_disable_title, R.string.ble_bluetooth_disable_message, true);
                break;
            case BleClientService.BLE_BLUETOOTH_CONNECTED:
                actionName = getString(R.string.ble_client_connect_name);
                mTestAdapter.setTestPass(BLE_CLIENT_CONNECT);
                mPassed |= PASS_FLAG_CONNECT;
                // execute service discovery test
                newAction = BleClientService.BLE_CLIENT_ACTION_BLE_DISVOCER_SERVICE;
                break;
            case BleClientService.BLE_SERVICES_DISCOVERED:
                actionName = getString(R.string.ble_discover_service_name);
                mTestAdapter.setTestPass(BLE_BLE_DISVOCER_SERVICE);
                mPassed |= PASS_FLAG_DISCOVER;
                // execute MTU requesting test (23bytes)
                newAction = BleClientService.BLE_CLIENT_ACTION_READ_CHARACTERISTIC;
                break;
            case BleClientService.BLE_MTU_CHANGED_23BYTES:
                actionName = getString(R.string.ble_mtu_23_name);
                mTestAdapter.setTestPass(BLE_REQUEST_MTU_23BYTES);
                mPassed |= PASS_FLAG_MTU_CHANGE_23BYTES;
                // execute MTU requesting test (512bytes)
                newAction = BleClientService.BLE_CLIENT_ACTION_REQUEST_MTU_512;
                showProgressDialog = true;
                break;
            case BleClientService.BLE_MTU_CHANGED_512BYTES:
                actionName = getString(R.string.ble_mtu_512_name);
                mTestAdapter.setTestPass(BLE_REQUEST_MTU_512BYTES);
                mPassed |= PASS_FLAG_MTU_CHANGE_512BYTES;
                // execute characteristic reading test
                newAction = BleClientService.BLE_CLIENT_ACTION_READ_CHARACTERISTIC_NO_PERMISSION;
                break;
            case BleClientService.BLE_CHARACTERISTIC_READ:
                actionName = getString(R.string.ble_read_characteristic_name);
                mTestAdapter.setTestPass(BLE_READ_CHARACTERISTIC);
                mPassed |= PASS_FLAG_READ_CHARACTERISTIC;
                // execute characteristic writing test
                newAction = BleClientService.BLE_CLIENT_ACTION_WRITE_CHARACTERISTIC;
                break;
            case BleClientService.BLE_CHARACTERISTIC_WRITE:
                actionName = getString(R.string.ble_write_characteristic_name);
                mTestAdapter.setTestPass(BLE_WRITE_CHARACTERISTIC);
                mPassed |= PASS_FLAG_WRITE_CHARACTERISTIC;
                newAction = BleClientService.BLE_CLIENT_ACTION_REQUEST_MTU_23;
                showProgressDialog = true;
                break;
            case BleClientService.BLE_CHARACTERISTIC_READ_NOPERMISSION:
                actionName = getString(R.string.ble_read_characteristic_nopermission_name);
                mTestAdapter.setTestPass(BLE_READ_CHARACTERISTIC_NO_PERMISSION);
                mPassed |= PASS_FLAG_READ_CHARACTERISTIC_NO_PERMISSION;
                // execute unpermitted characteristic writing test
                newAction = BleClientService.BLE_CLIENT_ACTION_WRITE_CHARACTERISTIC_NO_PERMISSION;
                break;
            case BleClientService.BLE_CHARACTERISTIC_WRITE_NOPERMISSION:
                actionName = getString(R.string.ble_write_characteristic_nopermission_name);
                mTestAdapter.setTestPass(BLE_WRITE_CHARACTERISTIC_NO_PERMISSION);
                mPassed |= PASS_FLAG_WRITE_CHARACTERISTIC_NO_PERMISSION;
                // execute reliable write test
                newAction = BleClientService.BLE_CLIENT_ACTION_RELIABLE_WRITE;
                showProgressDialog = true;
                break;
            case BleClientService.BLE_RELIABLE_WRITE_COMPLETED:
                actionName = getString(R.string.ble_reliable_write_name);
                mTestAdapter.setTestPass(BLE_RELIABLE_WRITE);
                mPassed |= PASS_FLAG_RELIABLE_WRITE;
//                newAction = BleClientService.BLE_CLIENT_ACTION_RELIABLE_WRITE_BAD_RESP;

                // skip Reliable write (bad response) test
                mPassed |= PASS_FLAG_RELIABLE_WRITE_BAD_RESP;
                newAction = BleClientService.BLE_CLIENT_ACTION_NOTIFY_CHARACTERISTIC;
                showProgressDialog = true;
                break;
            case BleClientService.BLE_RELIABLE_WRITE_BAD_RESP_COMPLETED: {
                actionName = getString(R.string.ble_reliable_write_bad_resp_name);
                if(!intent.hasExtra(BleClientService.EXTRA_ERROR_MESSAGE)) {
                    mPassed |= PASS_FLAG_RELIABLE_WRITE_BAD_RESP;
                    mTestAdapter.setTestPass(BLE_RELIABLE_WRITE_BAD_RESP);
                }
                // execute notification test
                newAction = BleClientService.BLE_CLIENT_ACTION_NOTIFY_CHARACTERISTIC;
                showProgressDialog = true;
            }
                break;
            case BleClientService.BLE_CHARACTERISTIC_CHANGED:
                actionName = getString(R.string.ble_notify_characteristic_name);
                mTestAdapter.setTestPass(BLE_NOTIFY_CHARACTERISTIC);
                mPassed |= PASS_FLAG_NOTIFY_CHARACTERISTIC;
                // execute indication test
                newAction = BleClientService.BLE_CLIENT_ACTION_INDICATE_CHARACTERISTIC;
                showProgressDialog = true;
                break;
            case BleClientService.BLE_CHARACTERISTIC_INDICATED:
                actionName = getString(R.string.ble_indicate_characteristic_name);
                mTestAdapter.setTestPass(BLE_INDICATE_CHARACTERISTIC);
                mPassed |= PASS_FLAG_INDICATE_CHARACTERISTIC;
                // execute descriptor reading test
                newAction = BleClientService.BLE_CLIENT_ACTION_READ_DESCRIPTOR;
                break;
            case BleClientService.BLE_DESCRIPTOR_READ:
                actionName = getString(R.string.ble_read_descriptor_name);
                mTestAdapter.setTestPass(BLE_READ_DESCRIPTOR);
                mPassed |= PASS_FLAG_READ_DESCRIPTOR;
                // execute descriptor writing test
                newAction = BleClientService.BLE_CLIENT_ACTION_WRITE_DESCRIPTOR;
                break;
            case BleClientService.BLE_DESCRIPTOR_WRITE:
                actionName = getString(R.string.ble_write_descriptor_name);
                mTestAdapter.setTestPass(BLE_WRITE_DESCRIPTOR);
                mPassed |= PASS_FLAG_WRITE_DESCRIPTOR;
                // execute unpermitted descriptor reading test
                newAction = BleClientService.BLE_CLIENT_ACTION_READ_DESCRIPTOR_NO_PERMISSION;
                break;
            case BleClientService.BLE_DESCRIPTOR_READ_NOPERMISSION:
                actionName = getString(R.string.ble_read_descriptor_nopermission_name);
                mTestAdapter.setTestPass(BLE_READ_DESCRIPTOR_NO_PERMISSION);
                mPassed |= PASS_FLAG_READ_DESCRIPTOR_NO_PERMISSION;
                // execute unpermitted descriptor writing test
                newAction = BleClientService.BLE_CLIENT_ACTION_WRITE_DESCRIPTOR_NO_PERMISSION;
                break;
            case BleClientService.BLE_DESCRIPTOR_WRITE_NOPERMISSION:
                actionName = getString(R.string.ble_write_descriptor_nopermission_name);
                mTestAdapter.setTestPass(BLE_WRITE_DESCRIPTOR_NO_PERMISSION);
                mPassed |= PASS_FLAG_WRITE_DESCRIPTOR_NO_PERMISSION;
// TODO: too flaky b/34951749
                // execute RSSI requesting test
                // newAction = BleClientService.BLE_CLIENT_ACTION_READ_RSSI;
                // execute disconnection test
                mPassed |= PASS_FLAG_READ_RSSI;
                newAction = BleClientService.BLE_CLIENT_ACTION_CLIENT_DISCONNECT;
                break;
            case BleClientService.BLE_READ_REMOTE_RSSI:
                actionName = getString(R.string.ble_read_rssi_name);
                mTestAdapter.setTestPass(BLE_READ_RSSI);
                mPassed |= PASS_FLAG_READ_RSSI;
                // execute disconnection test
                newAction = BleClientService.BLE_CLIENT_ACTION_CLIENT_DISCONNECT;
                break;
            case BleClientService.BLE_BLUETOOTH_DISCONNECTED:
                mTestAdapter.setTestPass(BLE_CLIENT_DISCONNECT);
                mPassed |= PASS_FLAG_DISCONNECT;
                // all test done
                newAction = null;
                break;
            case BleClientService.BLE_BLUETOOTH_MISMATCH_SECURE:
                showErrorDialog(R.string.ble_bluetooth_mismatch_title, R.string.ble_bluetooth_mismatch_secure_message, true);
                break;
            case BleClientService.BLE_BLUETOOTH_MISMATCH_INSECURE:
                showErrorDialog(R.string.ble_bluetooth_mismatch_title, R.string.ble_bluetooth_mismatch_insecure_message, true);
                break;
            }

            mTestAdapter.notifyDataSetChanged();

            if (newAction != null) {
                startIntent.setAction(newAction);
                if (STEP_EXECUTION) {
                    closeDialog();
                    final boolean showProgressDialogValue = showProgressDialog;
                    mDialog = new AlertDialog.Builder(BleClientTestBaseActivity.this)
                            .setTitle(actionName)
                            .setMessage(R.string.ble_test_finished)
                            .setCancelable(false)
                            .setPositiveButton(R.string.ble_test_next,
                                    new DialogInterface.OnClickListener() {
                                        @Override
                                        public void onClick(DialogInterface dialog, int which) {
                                            closeDialog();
                                            if (showProgressDialogValue) {
                                                showProgressDialog();
                                            }
                                            startService(startIntent);
                                        }
                                    })
                            .show();
                } else {
                    if (showProgressDialog) {
                        showProgressDialog();
                    }
                    startService(startIntent);
                }
            } else {
                closeDialog();
            }

            if (mPassed == PASS_FLAG_ALL) {
                if (shouldRebootBluetoothAfterTest()) {
                    mBtPowerSwitcher.executeSwitching();
                } else {
                    getPassButton().setEnabled(true);
                }
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
                showProgressDialog();
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
