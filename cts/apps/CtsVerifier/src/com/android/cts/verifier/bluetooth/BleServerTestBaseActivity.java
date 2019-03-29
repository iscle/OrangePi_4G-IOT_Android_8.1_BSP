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

public class BleServerTestBaseActivity extends PassFailButtons.Activity {

    private static final int PASS_FLAG_ADD_SERVICE = 0x1;
    private static final int PASS_FLAG_CONNECT = 0x2;
    private static final int PASS_FLAG_READ_CHARACTERISTIC = 0x4;
    private static final int PASS_FLAG_WRITE_CHARACTERISTIC = 0x8;
    private static final int PASS_FLAG_READ_DESCRIPTOR = 0x10;
    private static final int PASS_FLAG_WRITE_DESCRIPTOR = 0x20;
    private static final int PASS_FLAG_WRITE = 0x40;
    private static final int PASS_FLAG_DISCONNECT = 0x80;
    private static final int PASS_FLAG_NOTIFY_CHARACTERISTIC = 0x100;
    private static final int PASS_FLAG_READ_CHARACTERISTIC_NO_PERMISSION = 0x200;
    private static final int PASS_FLAG_WRITE_CHARACTERISTIC_NO_PERMISSION = 0x400;
    private static final int PASS_FLAG_READ_DESCRIPTOR_NO_PERMISSION = 0x800;
    private static final int PASS_FLAG_WRITE_DESCRIPTOR_NO_PERMISSION = 0x1000;
    private static final int PASS_FLAG_INDICATE_CHARACTERISTIC = 0x2000;
    private static final int PASS_FLAG_MTU_CHANGE_23BYTES = 0x4000;
    private static final int PASS_FLAG_MTU_CHANGE_512BYTES = 0x8000;
    private static final int PASS_FLAG_RELIABLE_WRITE_BAD_RESP = 0x10000;
    private static final int PASS_FLAG_ALL = 0x1FFFF;

    private final int BLE_SERVICE_ADDED = 0;
    private final int BLE_SERVER_CONNECTED = 1;
    private final int BLE_CHARACTERISTIC_READ_REQUEST = 2;
    private final int BLE_CHARACTERISTIC_WRITE_REQUEST = 3;
    private final int BLE_SERVER_MTU_23BYTES = 4;
    private final int BLE_SERVER_MTU_512BYTES = 5;
    private final int BLE_CHARACTERISTIC_READ_REQUEST_WITHOUT_PERMISSION = 6;
    private final int BLE_CHARACTERISTIC_WRITE_REQUEST_WITHOUT_PERMISSION = 7;
    private final int BLE_EXECUTE_WRITE = 8;
    private final int BLE_EXECUTE_WRITE_BAD_RESP = 9;
    private final int BLE_CHARACTERISTIC_NOTIFICATION_REQUEST = 9;  //10;
    private final int BLE_CHARACTERISTIC_INDICATE_REQUEST = 10; //11;
    private final int BLE_DESCRIPTOR_READ_REQUEST = 11; //12;
    private final int BLE_DESCRIPTOR_WRITE_REQUEST = 12;    //13;
    private final int BLE_DESCRIPTOR_READ_REQUEST_WITHOUT_PERMISSION = 13;  //14;
    private final int BLE_DESCRIPTOR_WRITE_REQUEST_WITHOUT_PERMISSION = 14; //15;
    private final int BLE_SERVER_DISCONNECTED = 15; //16;
    private final int BLE_OPEN_FAIL = 16;   //17;

    private TestAdapter mTestAdapter;
    private long mAllPassed;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.ble_server_start);
        setPassFailButtonClickListeners();
        setInfoResources(R.string.ble_server_start_name,
                         R.string.ble_server_start_info, -1);
        getPassButton().setEnabled(false);

        mTestAdapter = new TestAdapter(this, setupTestList());
        ListView listView = (ListView) findViewById(R.id.ble_server_tests);
        listView.setAdapter(mTestAdapter);

//        mAllPassed = 0;
        // skip Reliable write (bad response) test
        mAllPassed = PASS_FLAG_RELIABLE_WRITE_BAD_RESP;
    }

    @Override
    public void onResume() {
        super.onResume();

        IntentFilter filter = new IntentFilter();
        filter.addAction(BleServerService.BLE_BLUETOOTH_DISABLED);
        filter.addAction(BleServerService.BLE_SERVICE_ADDED);
        filter.addAction(BleServerService.BLE_SERVER_CONNECTED);
        filter.addAction(BleServerService.BLE_MTU_REQUEST_23BYTES);
        filter.addAction(BleServerService.BLE_MTU_REQUEST_512BYTES);
        filter.addAction(BleServerService.BLE_CHARACTERISTIC_READ_REQUEST);
        filter.addAction(BleServerService.BLE_CHARACTERISTIC_WRITE_REQUEST);
        filter.addAction(BleServerService.BLE_CHARACTERISTIC_READ_REQUEST_WITHOUT_PERMISSION);
        filter.addAction(BleServerService.BLE_CHARACTERISTIC_WRITE_REQUEST_WITHOUT_PERMISSION);
        filter.addAction(BleServerService.BLE_CHARACTERISTIC_NOTIFICATION_REQUEST);
        filter.addAction(BleServerService.BLE_CHARACTERISTIC_INDICATE_REQUEST);
        filter.addAction(BleServerService.BLE_DESCRIPTOR_READ_REQUEST);
        filter.addAction(BleServerService.BLE_DESCRIPTOR_WRITE_REQUEST);
        filter.addAction(BleServerService.BLE_DESCRIPTOR_READ_REQUEST_WITHOUT_PERMISSION);
        filter.addAction(BleServerService.BLE_DESCRIPTOR_WRITE_REQUEST_WITHOUT_PERMISSION);
        filter.addAction(BleServerService.BLE_EXECUTE_WRITE);
        filter.addAction(BleServerService.BLE_RELIABLE_WRITE_BAD_RESP);
        filter.addAction(BleServerService.BLE_SERVER_DISCONNECTED);
        filter.addAction(BleServerService.BLE_OPEN_FAIL);
        filter.addAction(BleServerService.BLE_BLUETOOTH_MISMATCH_SECURE);
        filter.addAction(BleServerService.BLE_BLUETOOTH_MISMATCH_INSECURE);
        filter.addAction(BleServerService.BLE_ADVERTISE_UNSUPPORTED);
        filter.addAction(BleServerService.BLE_ADD_SERVICE_FAIL);

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
    }

    private List<Integer> setupTestList() {
        ArrayList<Integer> testList = new ArrayList<Integer>();
        testList.add(R.string.ble_server_add_service);
        testList.add(R.string.ble_server_receiving_connect);
        testList.add(R.string.ble_server_read_characteristic);
        testList.add(R.string.ble_server_write_characteristic);
        testList.add(R.string.ble_server_mtu_23bytes);
        testList.add(R.string.ble_server_mtu_512bytes);
        testList.add(R.string.ble_server_read_characteristic_without_permission);
        testList.add(R.string.ble_server_write_characteristic_without_permission);
        testList.add(R.string.ble_server_reliable_write);
//        testList.add(R.string.ble_server_reliable_write_bad_resp);
        testList.add(R.string.ble_server_notify_characteristic);
        testList.add(R.string.ble_server_indicate_characteristic);
        testList.add(R.string.ble_server_read_descriptor);
        testList.add(R.string.ble_server_write_descriptor);
        testList.add(R.string.ble_server_read_descriptor_without_permission);
        testList.add(R.string.ble_server_write_descriptor_without_permission);
        testList.add(R.string.ble_server_receiving_disconnect);
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

    private BroadcastReceiver mBroadcast = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            switch (action) {
            case BleServerService.BLE_BLUETOOTH_DISABLED:
                showErrorDialog(R.string.ble_bluetooth_disable_title, R.string.ble_bluetooth_disable_message, true);
                break;
            case BleServerService.BLE_SERVICE_ADDED:
                mTestAdapter.setTestPass(BLE_SERVICE_ADDED);
                mAllPassed |= PASS_FLAG_ADD_SERVICE;
                break;
            case BleServerService.BLE_SERVER_CONNECTED:
                mTestAdapter.setTestPass(BLE_SERVER_CONNECTED);
                mAllPassed |= PASS_FLAG_CONNECT;
                break;
            case BleServerService.BLE_CHARACTERISTIC_READ_REQUEST:
                // Sometimes server returns incorrect pairing status.
                // And it causes the mismatch of pairing status and connection status.
                // So consider the connection went well if reading characteristic went well.
                mTestAdapter.setTestPass(BLE_SERVER_CONNECTED);
                mAllPassed |= PASS_FLAG_CONNECT;

                mTestAdapter.setTestPass(BLE_CHARACTERISTIC_READ_REQUEST);
                mAllPassed |= PASS_FLAG_READ_CHARACTERISTIC;
                break;
            case BleServerService.BLE_CHARACTERISTIC_WRITE_REQUEST:
                mTestAdapter.setTestPass(BLE_CHARACTERISTIC_WRITE_REQUEST);
                mAllPassed |= PASS_FLAG_WRITE_CHARACTERISTIC;
                break;
            case BleServerService.BLE_DESCRIPTOR_READ_REQUEST:
                mTestAdapter.setTestPass(BLE_DESCRIPTOR_READ_REQUEST);
                mAllPassed |= PASS_FLAG_READ_DESCRIPTOR;
                break;
            case BleServerService.BLE_DESCRIPTOR_WRITE_REQUEST:
                mTestAdapter.setTestPass(BLE_DESCRIPTOR_WRITE_REQUEST);
                mAllPassed |= PASS_FLAG_WRITE_DESCRIPTOR;
                break;
            case BleServerService.BLE_EXECUTE_WRITE:
                mTestAdapter.setTestPass(BLE_EXECUTE_WRITE);
                mAllPassed |= PASS_FLAG_WRITE;
                break;
            case BleServerService.BLE_RELIABLE_WRITE_BAD_RESP:
                mTestAdapter.setTestPass(BLE_EXECUTE_WRITE_BAD_RESP);
                mAllPassed |= PASS_FLAG_RELIABLE_WRITE_BAD_RESP;
                break;
            case BleServerService.BLE_SERVER_DISCONNECTED:
                mTestAdapter.setTestPass(BLE_SERVER_DISCONNECTED);
                mAllPassed |= PASS_FLAG_DISCONNECT;
                break;
            case BleServerService.BLE_CHARACTERISTIC_NOTIFICATION_REQUEST:
                mTestAdapter.setTestPass(BLE_CHARACTERISTIC_NOTIFICATION_REQUEST);
                mAllPassed |= PASS_FLAG_NOTIFY_CHARACTERISTIC;
                break;
            case BleServerService.BLE_CHARACTERISTIC_READ_REQUEST_WITHOUT_PERMISSION:
                mTestAdapter.setTestPass(BLE_CHARACTERISTIC_READ_REQUEST_WITHOUT_PERMISSION);
                mAllPassed |= PASS_FLAG_READ_CHARACTERISTIC_NO_PERMISSION;
                break;
            case BleServerService.BLE_CHARACTERISTIC_WRITE_REQUEST_WITHOUT_PERMISSION:
                mTestAdapter.setTestPass(BLE_CHARACTERISTIC_WRITE_REQUEST_WITHOUT_PERMISSION);
                mAllPassed |= PASS_FLAG_WRITE_CHARACTERISTIC_NO_PERMISSION;
                break;
            case BleServerService.BLE_DESCRIPTOR_READ_REQUEST_WITHOUT_PERMISSION:
                mTestAdapter.setTestPass(BLE_DESCRIPTOR_READ_REQUEST_WITHOUT_PERMISSION);
                mAllPassed |= PASS_FLAG_READ_DESCRIPTOR_NO_PERMISSION;
                break;
            case BleServerService.BLE_DESCRIPTOR_WRITE_REQUEST_WITHOUT_PERMISSION:
                mTestAdapter.setTestPass(BLE_DESCRIPTOR_WRITE_REQUEST_WITHOUT_PERMISSION);
                mAllPassed |= PASS_FLAG_WRITE_DESCRIPTOR_NO_PERMISSION;
                break;
            case BleServerService.BLE_CHARACTERISTIC_INDICATE_REQUEST:
                mTestAdapter.setTestPass(BLE_CHARACTERISTIC_INDICATE_REQUEST);
                mAllPassed |= PASS_FLAG_INDICATE_CHARACTERISTIC;
                break;
            case BleServerService.BLE_MTU_REQUEST_23BYTES:
                mTestAdapter.setTestPass(BLE_SERVER_MTU_23BYTES);
                mAllPassed |= PASS_FLAG_MTU_CHANGE_23BYTES;
                break;
            case BleServerService.BLE_MTU_REQUEST_512BYTES:
                mTestAdapter.setTestPass(BLE_SERVER_MTU_512BYTES);
                mAllPassed |= PASS_FLAG_MTU_CHANGE_512BYTES;
                break;
            case BleServerService.BLE_BLUETOOTH_MISMATCH_SECURE:
                showErrorDialog(R.string.ble_bluetooth_mismatch_title, R.string.ble_bluetooth_mismatch_secure_message, true);
                break;
            case BleServerService.BLE_BLUETOOTH_MISMATCH_INSECURE:
                showErrorDialog(R.string.ble_bluetooth_mismatch_title, R.string.ble_bluetooth_mismatch_insecure_message, true);
                break;
            case BleServerService.BLE_ADVERTISE_UNSUPPORTED:
                showErrorDialog(R.string.bt_advertise_unsupported_title, R.string.bt_advertise_unsupported_message, true);
                break;
            case BleServerService.BLE_OPEN_FAIL:
                setTestResultAndFinish(false);
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        Toast.makeText(BleServerTestBaseActivity.this, R.string.bt_open_failed_message, Toast.LENGTH_SHORT).show();
                    }
                });
                break;
            case BleServerService.BLE_ADD_SERVICE_FAIL:
                showErrorDialog(R.string.bt_add_service_failed_title, R.string.bt_add_service_failed_message, true);
                break;
            }

            mTestAdapter.notifyDataSetChanged();
            if (mAllPassed == PASS_FLAG_ALL) getPassButton().setEnabled(true);
        }
    };
}
