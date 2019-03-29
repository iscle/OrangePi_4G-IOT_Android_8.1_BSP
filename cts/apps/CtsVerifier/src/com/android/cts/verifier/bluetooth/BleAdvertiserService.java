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

import java.util.UUID;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import android.app.Service;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothGattServer;
import android.bluetooth.BluetoothGattServerCallback;
import android.bluetooth.BluetoothManager;
import android.bluetooth.le.BluetoothLeAdvertiser;
import android.bluetooth.le.AdvertiseCallback;
import android.bluetooth.le.AdvertiseData;
import android.bluetooth.le.AdvertiseSettings;
import android.content.Context;
import android.content.Intent;
import android.os.Handler;
import android.os.IBinder;
import android.os.ParcelUuid;
import android.util.Log;
import android.widget.Toast;

public class BleAdvertiserService extends Service {

    public static final boolean DEBUG = true;
    public static final String TAG = "BleAdvertiserService";

    public static final int COMMAND_START_ADVERTISE = 0;
    public static final int COMMAND_STOP_ADVERTISE = 1;
    public static final int COMMAND_START_POWER_LEVEL = 2;
    public static final int COMMAND_STOP_POWER_LEVEL = 3;
    public static final int COMMAND_START_SCANNABLE = 4;
    public static final int COMMAND_STOP_SCANNABLE = 5;
    public static final int COMMAND_START_UNSCANNABLE = 6;
    public static final int COMMAND_STOP_UNSCANNABLE = 7;

    public static final String BLE_ADV_NOT_SUPPORT =
            "com.android.cts.verifier.bluetooth.BLE_ADV_NOT_SUPPORT";
    public static final String BLE_START_ADVERTISE =
            "com.android.cts.verifier.bluetooth.BLE_START_ADVERTISE";
    public static final String BLE_STOP_ADVERTISE =
            "com.android.cts.verifier.bluetooth.BLE_STOP_ADVERTISE";
    public static final String BLE_START_POWER_LEVEL =
            "com.android.cts.verifier.bluetooth.BLE_START_POWER_LEVEL";
    public static final String BLE_STOP_POWER_LEVEL =
            "com.android.cts.verifier.bluetooth.BLE_STOP_POWER_LEVEL";
    public static final String BLE_START_SCANNABLE =
            "com.android.cts.verifier.bluetooth.BLE_START_SCANNABLE";
    public static final String BLE_START_UNSCANNABLE =
            "com.android.cts.verifier.bluetooth.BLE_START_UNSCANNABLE";
    public static final String BLE_STOP_SCANNABLE =
            "com.android.cts.verifier.bluetooth.BLE_STOP_SCANNABLE";
    public static final String BLE_STOP_UNSCANNABLE =
            "com.android.cts.verifier.bluetooth.BLE_STOP_UNSCANNABLE";

    public static final String EXTRA_COMMAND =
            "com.android.cts.verifier.bluetooth.EXTRA_COMMAND";

    protected static final UUID PRIVACY_MAC_UUID =
            UUID.fromString("00009999-0000-1000-8000-00805f9b34fb");
    protected static final UUID POWER_LEVEL_UUID =
            UUID.fromString("00008888-0000-1000-8000-00805f9b34fb");
    protected static final UUID SCAN_RESP_UUID =
            UUID.fromString("00007777-0000-1000-8000-00805f9b34fb");
    protected static final UUID SCANNABLE_UUID =
            UUID.fromString("00006666-0000-1000-8000-00805f9b34fb");
    protected static final UUID UNSCANNABLE_UUID =
            UUID.fromString("00005555-0000-1000-8000-00805f9b34fb");

    public static final byte MANUFACTURER_TEST_ID = (byte)0x07;
    public static final byte[] PRIVACY_MAC_DATA = new byte[]{3, 1, 4};
    public static final byte[] PRIVACY_RESPONSE = new byte[]{9, 2, 6};
    public static final byte[] POWER_LEVEL_DATA = new byte[]{1, 5, 0, 0, 0,
        0, 0, 0, 0, 0, 0, 0, 0, 0, 0};  // 15 bytes
    public static final byte[] POWER_LEVEL_MASK = new byte[]{1, 1, 0, 0, 0,
        0, 0, 0, 0, 0, 0, 0, 0, 0, 0};  // 15 bytes
    public static final int POWER_LEVEL_DATA_LENGTH = 15;
    public static final byte[] SCANNABLE_DATA = new byte[]{5, 3, 5};
    public static final byte[] UNSCANNABLE_DATA = new byte[]{8, 9, 7};

    private BluetoothManager mBluetoothManager;
    private BluetoothAdapter mBluetoothAdapter;
    private BluetoothLeAdvertiser mAdvertiser;
    private BluetoothGattServer mGattServer;
    private AdvertiseCallback mCallback;
    private Handler mHandler;

    private int[] mPowerLevel;
    private Map<Integer, AdvertiseCallback> mPowerCallback;
    private int mAdvertiserStatus;

    private AdvertiseCallback mScannableCallback;
    private AdvertiseCallback mUnscannableCallback;

    @Override
    public void onCreate() {
        super.onCreate();

        mBluetoothManager = (BluetoothManager) getSystemService(Context.BLUETOOTH_SERVICE);
        mBluetoothAdapter = mBluetoothManager.getAdapter();
        mAdvertiser = mBluetoothAdapter.getBluetoothLeAdvertiser();
        mGattServer = mBluetoothManager.openGattServer(getApplicationContext(),
            new BluetoothGattServerCallback() {});
        mHandler = new Handler();
        mAdvertiserStatus = 0;

        mCallback = new BLEAdvertiseCallback();
        mScannableCallback = new BLEAdvertiseCallback();
        mUnscannableCallback = new BLEAdvertiseCallback();
        mPowerLevel = new int[]{
            AdvertiseSettings.ADVERTISE_TX_POWER_ULTRA_LOW,
            AdvertiseSettings.ADVERTISE_TX_POWER_LOW,
            AdvertiseSettings.ADVERTISE_TX_POWER_MEDIUM,
            AdvertiseSettings.ADVERTISE_TX_POWER_HIGH};
        mPowerCallback = new HashMap<Integer, AdvertiseCallback>();
        for (int x : mPowerLevel) {
            mPowerCallback.put(x, new BLEAdvertiseCallback());
        }
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null) handleIntent(intent);
        return START_NOT_STICKY;
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (mAdvertiser != null) {
            stopAdvertiser();
        }
    }

    private void stopAdvertiser() {
        if (mAdvertiser == null) {
            mAdvertiserStatus = 0;
            return;
        }
        if ((mAdvertiserStatus & (1 << COMMAND_START_ADVERTISE)) > 0) {
            mAdvertiser.stopAdvertising(mCallback);
        }
        if ((mAdvertiserStatus & (1 << COMMAND_START_POWER_LEVEL)) > 0) {
            for (int t : mPowerLevel) {
                mAdvertiser.stopAdvertising(mPowerCallback.get(t));
            }
        }
        if ((mAdvertiserStatus & (1 << COMMAND_START_SCANNABLE)) > 0) {
            mAdvertiser.stopAdvertising(mScannableCallback);
        }
        if ((mAdvertiserStatus & (1 << COMMAND_START_UNSCANNABLE)) > 0) {
            mAdvertiser.stopAdvertising(mUnscannableCallback);
        }
        mAdvertiserStatus = 0;
    }

    private AdvertiseData generateAdvertiseData(UUID uuid, byte[] data) {
        return new AdvertiseData.Builder()
            .addManufacturerData(MANUFACTURER_TEST_ID, new byte[]{MANUFACTURER_TEST_ID, 0})
            .addServiceData(new ParcelUuid(uuid), data)
            .setIncludeTxPowerLevel(true)
            .build();
    }

    private AdvertiseSettings generateSetting(int power) {
        return new AdvertiseSettings.Builder()
            .setAdvertiseMode(AdvertiseSettings.ADVERTISE_MODE_LOW_LATENCY)
            .setTxPowerLevel(power)
            .setConnectable(false)
            .build();
    }

    private void handleIntent(Intent intent) {
        if (mBluetoothAdapter != null && !mBluetoothAdapter.isMultipleAdvertisementSupported()) {
            showMessage("Multiple advertisement is not supported.");
            sendBroadcast(new Intent(BLE_ADV_NOT_SUPPORT));
            return;
        } else if (mAdvertiser == null) {
            showMessage("Cannot start advertising on this device.");
            return;
        }
        int command = intent.getIntExtra(EXTRA_COMMAND, -1);
        if (command >= 0) {
            stopAdvertiser();
            mAdvertiserStatus |= (1 << command);
        }

        switch (command) {
            case COMMAND_START_ADVERTISE:
                AdvertiseData data = generateAdvertiseData(PRIVACY_MAC_UUID, PRIVACY_MAC_DATA);
                AdvertiseData response = generateAdvertiseData(SCAN_RESP_UUID, PRIVACY_RESPONSE);
                AdvertiseSettings setting =
                        generateSetting(AdvertiseSettings.ADVERTISE_TX_POWER_MEDIUM);

                mAdvertiser.startAdvertising(setting, data, response, mCallback);
                sendBroadcast(new Intent(BLE_START_ADVERTISE));
                break;
            case COMMAND_STOP_ADVERTISE:
                sendBroadcast(new Intent(BLE_STOP_ADVERTISE));
                break;
            case COMMAND_START_POWER_LEVEL:
                for (int t : mPowerLevel) {
                    // Service data:
                    //    field overhead = 2 bytes
                    //    uuid = 2 bytes
                    //    data = 15 bytes
                    // Manufacturer data:
                    //    field overhead = 2 bytes
                    //    Specific data length = 2 bytes
                    //    data length = 2 bytes
                    // Include power level:
                    //    field overhead = 2 bytes
                    //    1 byte
                    // Connectable flag: 3 bytes (0 byte for Android 5.1+)
                    // SUM = 31 bytes
                    byte[] dataBytes = new byte[POWER_LEVEL_DATA_LENGTH];
                    dataBytes[0] = 0x01;
                    dataBytes[1] = 0x05;
                    for (int i = 2; i < POWER_LEVEL_DATA_LENGTH; i++) {
                        dataBytes[i] = (byte)t;
                    }
                    AdvertiseData d = generateAdvertiseData(POWER_LEVEL_UUID, dataBytes);
                    AdvertiseSettings settings = generateSetting(t);
                    mAdvertiser.startAdvertising(settings, d, mPowerCallback.get(t));
                }
                sendBroadcast(new Intent(BLE_START_POWER_LEVEL));
                break;
            case COMMAND_STOP_POWER_LEVEL:
                sendBroadcast(new Intent(BLE_STOP_POWER_LEVEL));
                break;
            case COMMAND_START_SCANNABLE:
                data = generateAdvertiseData(SCANNABLE_UUID, SCANNABLE_DATA);
                setting = generateSetting(AdvertiseSettings.ADVERTISE_TX_POWER_MEDIUM);

                mAdvertiser.startAdvertising(setting, data, mScannableCallback);
                sendBroadcast(new Intent(BLE_START_SCANNABLE));
                break;
            case COMMAND_START_UNSCANNABLE:
                data = generateAdvertiseData(UNSCANNABLE_UUID, UNSCANNABLE_DATA);
                setting = generateSetting(AdvertiseSettings.ADVERTISE_TX_POWER_MEDIUM);

                mAdvertiser.startAdvertising(setting, data, mUnscannableCallback);
                sendBroadcast(new Intent(BLE_START_UNSCANNABLE));
                break;
            case COMMAND_STOP_SCANNABLE:
                sendBroadcast(new Intent(BLE_STOP_SCANNABLE));
                break;
            case COMMAND_STOP_UNSCANNABLE:
                sendBroadcast(new Intent(BLE_STOP_UNSCANNABLE));
                break;
            default:
                showMessage("Unrecognized command: " + command);
                break;
        }
    }

    private void showMessage(final String msg) {
        mHandler.post(new Runnable() {
            public void run() {
                Toast.makeText(BleAdvertiserService.this, msg, Toast.LENGTH_SHORT).show();
            }
        });
    }

    private class BLEAdvertiseCallback extends AdvertiseCallback {
        @Override
        public void onStartFailure(int errorCode) {
            Log.e(TAG, "fail. Error code: " + errorCode);
        }

        @Override
        public void onStartSuccess(AdvertiseSettings setting) {
            if (DEBUG) Log.d(TAG, "success.");
        }
    }
}
