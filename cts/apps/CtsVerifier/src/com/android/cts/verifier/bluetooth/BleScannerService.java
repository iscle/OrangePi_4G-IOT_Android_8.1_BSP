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

import android.app.Service;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothManager;
import android.bluetooth.le.AdvertiseSettings;
import android.bluetooth.le.BluetoothLeScanner;
import android.bluetooth.le.ScanCallback;
import android.bluetooth.le.ScanFilter;
import android.bluetooth.le.ScanRecord;
import android.bluetooth.le.ScanResult;
import android.bluetooth.le.ScanSettings;
import android.content.Context;
import android.content.Intent;
import android.os.Handler;
import android.os.IBinder;
import android.os.ParcelUuid;
import android.util.Log;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class BleScannerService extends Service {

    public static final boolean DEBUG = true;
    public static final String TAG = "BleScannerService";

    public static final int COMMAND_POWER_LEVEL = 1;
    public static final int COMMAND_SCAN_WITH_FILTER = 2;
    public static final int COMMAND_SCAN_WITHOUT_FILTER = 3;

    public static final String BLE_PRIVACY_NEW_MAC_RECEIVE =
            "com.android.cts.verifier.bluetooth.BLE_PRIVACY_NEW_MAC_RECEIVE";
    public static final String BLE_MAC_ADDRESS =
            "com.android.cts.verifier.bluetooth.BLE_MAC_ADDRESS";
    public static final String BLE_POWER_LEVEL =
            "com.android.cts.verifier.bluetooth.BLE_POWER_LEVEL";
    public static final String BLE_SCAN_RESP =
            "com.android.cts.verifier.bluetooth.BLE_SCAN_RESP";
    public static final String BLE_SCAN_RESULT =
            "com.android.cts.verifier.bluetooth.BLE_SCAN_RESULT";

    public static final String EXTRA_COMMAND =
            "com.google.cts.verifier.bluetooth.EXTRA_COMMAND";
    public static final String EXTRA_MAC_ADDRESS =
            "com.google.cts.verifier.bluetooth.EXTRA_MAC_ADDRESS";
    public static final String EXTRA_RSSI =
            "com.google.cts.verifier.bluetooth.EXTRA_RSSI";
    public static final String EXTRA_POWER_LEVEL =
            "com.google.cts.verifier.bluetooth.EXTRA_POWER_LEVEL";
    public static final String EXTRA_POWER_LEVEL_BIT =
            "com.google.cts.verifier.bluetooth.EXTRA_POWER_LEVEL_BIT";
    public static final String EXTRA_UUID =
            "com.google.cts.verifier.bluetooth.EXTRA_UUID";
    public static final String EXTRA_DATA =
            "com.google.cts.verifier.bluetooth.EXTRA_DATA";

    private static final byte MANUFACTURER_TEST_ID = (byte)0x07;

    private BluetoothManager mBluetoothManager;
    private BluetoothAdapter mAdapter;
    private BluetoothLeScanner mScanner;
    private ScanCallback mCallback;
    private Handler mHandler;
    private String mOldMac;

    @Override
    public void onCreate() {
        super.onCreate();

        mCallback = new BLEScanCallback();
        mHandler = new Handler();
        mOldMac = null;

        mBluetoothManager = (BluetoothManager) getSystemService(Context.BLUETOOTH_SERVICE);
        mAdapter = mBluetoothManager.getAdapter();
        mScanner = mAdapter.getBluetoothLeScanner();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (mScanner != null) {
            List<ScanFilter> filters = new ArrayList<ScanFilter>();
            ScanSettings.Builder settingBuilder = new ScanSettings.Builder();

            int command = intent.getIntExtra(EXTRA_COMMAND, -1);
            switch (command) {
                case COMMAND_POWER_LEVEL:
                    filters.add(new ScanFilter.Builder()
                        .setManufacturerData(MANUFACTURER_TEST_ID,
                            new byte[]{MANUFACTURER_TEST_ID, 0})
                        .setServiceData(new ParcelUuid(BleAdvertiserService.POWER_LEVEL_UUID),
                            BleAdvertiserService.POWER_LEVEL_DATA,
                            BleAdvertiserService.POWER_LEVEL_MASK)
                        .build());
                    settingBuilder.setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY);
                    break;
                case COMMAND_SCAN_WITH_FILTER:
                    mScanner.stopScan(mCallback);
                    filters.add(new ScanFilter.Builder()
                        .setManufacturerData(MANUFACTURER_TEST_ID,
                            new byte[]{MANUFACTURER_TEST_ID, 0})
                        .setServiceData(new ParcelUuid(BleAdvertiserService.SCANNABLE_UUID),
                            BleAdvertiserService.SCANNABLE_DATA)
                        .build());
                    settingBuilder.setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY);
                    break;
                case COMMAND_SCAN_WITHOUT_FILTER:
                    mScanner.stopScan(mCallback);
                    settingBuilder.setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY);
                    break;
            }
            mOldMac = null;
            mScanner.startScan(filters, settingBuilder.build(), mCallback);
        }
        return START_NOT_STICKY;
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (mScanner != null) {
            mScanner.stopScan(mCallback);
        }
    }

    private void showMessage(final String msg) {
        mHandler.post(new Runnable() {
            public void run() {
                Toast.makeText(BleScannerService.this, msg, Toast.LENGTH_SHORT).show();
            }
        });
    }

    private class BLEScanCallback extends ScanCallback {
        @Override
        public void onScanResult(int callBackType, ScanResult result) {
            if (callBackType != ScanSettings.CALLBACK_TYPE_ALL_MATCHES) {
                Log.e(TAG, "onScanResult fail. callBackType is not CALLBACK_TYPE_ALL_MATCHES");
                return;
            }

            ScanRecord record = result.getScanRecord();
            String mac = result.getDevice().getAddress();
            Map<ParcelUuid, byte[]> serviceData = record.getServiceData();

            if (serviceData.get(new ParcelUuid(BleAdvertiserService.POWER_LEVEL_UUID)) != null) {
                byte[] data =
                        serviceData.get(new ParcelUuid(BleAdvertiserService.POWER_LEVEL_UUID));
                if (data.length == BleAdvertiserService.POWER_LEVEL_DATA.length) {
                    Intent powerIntent = new Intent(BLE_POWER_LEVEL);
                    powerIntent.putExtra(EXTRA_MAC_ADDRESS, result.getDevice().getAddress());
                    powerIntent.putExtra(EXTRA_POWER_LEVEL, record.getTxPowerLevel());
                    powerIntent.putExtra(EXTRA_RSSI, new Integer(result.getRssi()).toString());
                    powerIntent.putExtra(EXTRA_POWER_LEVEL_BIT, (int)data[2]);
                    sendBroadcast(powerIntent);

                    // Check privacy mac.
                    if (data[2] == AdvertiseSettings.ADVERTISE_TX_POWER_HIGH) {
                        String newMac = result.getDevice().getAddress();
                        if (mOldMac == null) {
                            mOldMac = newMac;
                        } else if (!mOldMac.equals(mac)) {
                            mOldMac = newMac;
                            Intent newIntent = new Intent(BLE_PRIVACY_NEW_MAC_RECEIVE);
                            sendBroadcast(newIntent);
                        }
                    }
                }
            }

            if (serviceData.get(new ParcelUuid(BleAdvertiserService.SCAN_RESP_UUID)) != null) {
                Intent responseIntent = new Intent(BLE_SCAN_RESP);
                sendBroadcast(responseIntent);
            }

            byte[] data = null;
            String uuid = "";
            if (serviceData.containsKey(new ParcelUuid(BleAdvertiserService.SCANNABLE_UUID))) {
                uuid = BleAdvertiserService.SCANNABLE_UUID.toString();
                data = serviceData.get(new ParcelUuid(BleAdvertiserService.SCANNABLE_UUID));
            }
            if (serviceData.containsKey(new ParcelUuid(BleAdvertiserService.UNSCANNABLE_UUID))) {
                uuid = BleAdvertiserService.UNSCANNABLE_UUID.toString();
                data = serviceData.get(new ParcelUuid(BleAdvertiserService.UNSCANNABLE_UUID));
            }
            if (uuid.length() > 0) {
                Intent scanIntent = new Intent(BLE_SCAN_RESULT);
                scanIntent.putExtra(EXTRA_UUID, uuid);
                String dataStr = "{";
                for (byte x : data) {
                    dataStr = dataStr + " " + x;
                }
                dataStr = dataStr + "}";
                scanIntent.putExtra(EXTRA_DATA, dataStr);
                sendBroadcast(scanIntent);
            }
        }

        public void onScanFailed(int errorCode) {
            Log.e(TAG, "Scan fail. Error code: " + new Integer(errorCode).toString());
        }

    }
}
