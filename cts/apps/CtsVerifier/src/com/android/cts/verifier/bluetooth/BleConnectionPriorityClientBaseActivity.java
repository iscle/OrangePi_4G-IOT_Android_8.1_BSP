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
import android.widget.ListView;
import android.widget.Toast;

import com.android.cts.verifier.PassFailButtons;
import com.android.cts.verifier.R;

import java.util.ArrayList;
import java.util.List;

public class BleConnectionPriorityClientBaseActivity extends PassFailButtons.Activity {

    private TestAdapter mTestAdapter;
    private int mPassed = 0;
    private Dialog mDialog;

    private static final int BLE_CONNECTION_HIGH = 0;
    private static final int BLE_CONNECTION_BALANCED = 1;
    private static final int BLE_CONNECTION_LOW = 2;

    private static final int PASSED_HIGH = 0x1;
    private static final int PASSED_BALANCED = 0x2;
    private static final int PASSED_LOW = 0x4;
    private static final int ALL_PASSED = 0x7;

    private boolean mSecure;

    private Handler mHandler;
    private int mCurrentTest = -1;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.ble_connection_priority_client_test);
        setPassFailButtonClickListeners();
        setInfoResources(R.string.ble_connection_priority_client_name,
                R.string.ble_connection_priority_client_info, -1);
        getPassButton().setEnabled(false);

        mHandler = new Handler();

        mTestAdapter = new TestAdapter(this, setupTestList());
        ListView listView = (ListView) findViewById(R.id.ble_client_connection_tests);
        listView.setAdapter(mTestAdapter);
        listView.setEnabled(false);
        listView.setClickable(false);
    }

    @Override
    public void onResume() {
        super.onResume();

        IntentFilter filter = new IntentFilter();
        filter.addAction(BleConnectionPriorityClientService.ACTION_BLUETOOTH_DISABLED);
        filter.addAction(BleConnectionPriorityClientService.ACTION_CONNECTION_SERVICES_DISCOVERED);
        filter.addAction(BleConnectionPriorityClientService.ACTION_FINISH_CONNECTION_PRIORITY_HIGH);
        filter.addAction(BleConnectionPriorityClientService.ACTION_FINISH_CONNECTION_PRIORITY_BALANCED);
        filter.addAction(BleConnectionPriorityClientService.ACTION_FINISH_CONNECTION_PRIORITY_LOW_POWER);
        filter.addAction(BleConnectionPriorityClientService.ACTION_BLUETOOTH_MISMATCH_SECURE);
        filter.addAction(BleConnectionPriorityClientService.ACTION_BLUETOOTH_MISMATCH_INSECURE);
        filter.addAction(BleConnectionPriorityClientService.ACTION_FINISH_DISCONNECT);
        registerReceiver(mBroadcast, filter);
    }

    @Override
    public void onPause() {
        super.onPause();
        unregisterReceiver(mBroadcast);
        closeDialog();
    }

    protected void setSecure(boolean secure) {
        mSecure = secure;
    }

    public boolean isSecure() {
        return mSecure;
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

    private List<Integer> setupTestList() {
        ArrayList<Integer> testList = new ArrayList<Integer>();
        testList.add(R.string.ble_connection_priority_client_high);
        testList.add(R.string.ble_connection_priority_client_balanced);
        testList.add(R.string.ble_connection_priority_client_low);
        return testList;
    }

    private void executeNextTest(long delay) {
        mHandler.postDelayed(new Runnable() {
            @Override
            public void run() {
                executeNextTestImpl();
            }
        }, delay);
    }
    private void executeNextTestImpl() {
        switch (mCurrentTest) {
            case -1: {
                mCurrentTest = BLE_CONNECTION_HIGH;
                Intent intent = new Intent(this, BleConnectionPriorityClientService.class);
                intent.setAction(BleConnectionPriorityClientService.ACTION_CONNECTION_PRIORITY_HIGH);
                startService(intent);
                String msg = getString(R.string.ble_client_connection_priority)
                        + getString(R.string.ble_connection_priority_high);
                Toast.makeText(getApplicationContext(), msg, Toast.LENGTH_LONG).show();
            }
                break;
            case BLE_CONNECTION_BALANCED: {
                mCurrentTest = BLE_CONNECTION_LOW;
                Intent intent = new Intent(this, BleConnectionPriorityClientService.class);
                intent.setAction(BleConnectionPriorityClientService.ACTION_CONNECTION_PRIORITY_LOW_POWER);
                startService(intent);
                String msg = getString(R.string.ble_client_connection_priority)
                        + getString(R.string.ble_connection_priority_low);
                Toast.makeText(getApplicationContext(), msg, Toast.LENGTH_LONG).show();
            }
                break;
            case BLE_CONNECTION_HIGH: {
                mCurrentTest = BLE_CONNECTION_BALANCED;
                Intent intent = new Intent(this, BleConnectionPriorityClientService.class);
                intent.setAction(BleConnectionPriorityClientService.ACTION_CONNECTION_PRIORITY_BALANCED);
                startService(intent);
                String msg = getString(R.string.ble_client_connection_priority)
                        + getString(R.string.ble_connection_priority_balanced);
                Toast.makeText(getApplicationContext(), msg, Toast.LENGTH_LONG).show();
            }
                break;
            case BLE_CONNECTION_LOW:
                // all test done
                closeDialog();
                if (mPassed == ALL_PASSED) {
                    Intent intent = new Intent(this, BleConnectionPriorityClientService.class);
                    intent.setAction(BleConnectionPriorityClientService.ACTION_DISCONNECT);
                    startService(intent);
                }
                break;
            default:
                // something went wrong
                closeDialog();
                break;
        }
    }

    public boolean shouldRebootBluetoothAfterTest() {
        return false;
    }

    private BroadcastReceiver mBroadcast = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            switch (action) {
            case BleConnectionPriorityClientService.ACTION_BLUETOOTH_DISABLED:
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
            case BleConnectionPriorityClientService.ACTION_CONNECTION_SERVICES_DISCOVERED:
                showProgressDialog();
                executeNextTest(3000);
                break;
            case BleConnectionPriorityClientService.ACTION_FINISH_CONNECTION_PRIORITY_HIGH:
                mTestAdapter.setTestPass(BLE_CONNECTION_HIGH);
                mPassed |= PASSED_HIGH;
                executeNextTest(1000);
                break;
            case BleConnectionPriorityClientService.ACTION_FINISH_CONNECTION_PRIORITY_BALANCED:
                mTestAdapter.setTestPass(BLE_CONNECTION_BALANCED);
                mPassed |= PASSED_BALANCED;
                executeNextTest(1000);
                break;
            case BleConnectionPriorityClientService.ACTION_FINISH_CONNECTION_PRIORITY_LOW_POWER:
                mTestAdapter.setTestPass(BLE_CONNECTION_LOW);
                mPassed |= PASSED_LOW;
                executeNextTest(100);
                break;
            case BleConnectionPriorityClientService.ACTION_BLUETOOTH_MISMATCH_SECURE:
                showErrorDialog(R.string.ble_bluetooth_mismatch_title, R.string.ble_bluetooth_mismatch_secure_message, true);
                break;
            case BleConnectionPriorityClientService.ACTION_BLUETOOTH_MISMATCH_INSECURE:
                showErrorDialog(R.string.ble_bluetooth_mismatch_title, R.string.ble_bluetooth_mismatch_insecure_message, true);
                break;
            case BleConnectionPriorityClientService.ACTION_FINISH_DISCONNECT:
                if (shouldRebootBluetoothAfterTest()) {
                    mBtPowerSwitcher.executeSwitching();
                } else {
                    getPassButton().setEnabled(true);
                }
                break;
            }
            mTestAdapter.notifyDataSetChanged();
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
