/*
 * Copyright (C) 2017 The Android Open Source Project
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

package com.android.pmc;


import android.app.Service;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattDescriptor;
import android.bluetooth.BluetoothGattServer;
import android.bluetooth.BluetoothGattServerCallback;
import android.bluetooth.BluetoothGattService;
import android.bluetooth.BluetoothManager;
import android.bluetooth.BluetoothProfile;
import android.bluetooth.le.AdvertiseCallback;
import android.bluetooth.le.AdvertiseData;
import android.bluetooth.le.AdvertiseSettings;
import android.bluetooth.le.BluetoothLeAdvertiser;
import android.content.Context;
import android.util.Log;

import java.util.UUID;

/**
 * Class to implement Gatt Server functionalities
 */
public class GattServer {
    public static final String TAG = "GATTS";

    private MyBleAdvertiser mBleAdvertiser;
    private Context mContext;
    private BluetoothManager mBluetoothManager;
    private BluetoothGattServer mGattServer;
    private MyGattServerCallback mGattServerCallBack;
    private BluetoothGattService mGattService;
    private static final String READABLE_DESC_UUID = "76d5ed92-ca81-4edb-bb6b-9f019665fb32";
    public static final String WRITABLE_CHAR_UUID = "aa7edd5a-4d1d-4f0e-883a-d145616a1630";
    public static final String TEST_SERVICE_UUID = "3846D7A0-69C8-11E4-BA00-0002A5D5C51B";

    /**
     * Constructor
     *
     * @param context - System will provide a context
     */
    public GattServer(Context context) {
        Log.d(TAG, "Start GattServer()");
        mContext = context;
        // Check if Bluetooth is enabled
        BluetoothAdapter bluetoothAdapter = BluetoothAdapter.getDefaultAdapter();

        if (bluetoothAdapter == null) {
            Log.e(TAG, "BluetoothAdapter is Null");
            return;
        } else {
            if (!bluetoothAdapter.isEnabled()) {
                Log.d(TAG, "BluetoothAdapter is NOT enabled, enable now");
                bluetoothAdapter.enable();
                if (!bluetoothAdapter.isEnabled()) {
                    Log.e(TAG, "Can't enable Bluetooth");
                    return;
                }
            }
        }

        // Prepare data for GATT service
        mBluetoothManager = (BluetoothManager) context.getSystemService(
                                Service.BLUETOOTH_SERVICE);

        mGattServerCallBack = new MyGattServerCallback();

        BluetoothGattCharacteristic characteristic =
                    new BluetoothGattCharacteristic(UUID.fromString(WRITABLE_CHAR_UUID),
                    BluetoothGattCharacteristic.PROPERTY_WRITE
                    | BluetoothGattCharacteristic.PROPERTY_WRITE_NO_RESPONSE,
                    BluetoothGattCharacteristic.PERMISSION_WRITE);

        BluetoothGattDescriptor descriptor =
                    new BluetoothGattDescriptor(UUID.fromString(READABLE_DESC_UUID),
                    BluetoothGattDescriptor.PERMISSION_READ
                    | BluetoothGattDescriptor.PERMISSION_WRITE);

        characteristic.addDescriptor(descriptor);

        mGattService = new BluetoothGattService(UUID.fromString(TEST_SERVICE_UUID),
                    BluetoothGattService.SERVICE_TYPE_PRIMARY);
        mGattService.addCharacteristic(characteristic);

        // Create BLE Advertiser object
        mBleAdvertiser = new MyBleAdvertiser(bluetoothAdapter);
        Log.d(TAG, "End GattServer()");
    }

    /**
     * Function to be called to start Gatt Server
     */
    public void startGattServer() {
        // Connect to Gatt Server
        mGattServer = mBluetoothManager.openGattServer(mContext, mGattServerCallBack);
        // Add GATT Service to Gatt Server
        mGattServer.addService(mGattService);
        // Start BLE Advertising here
        mBleAdvertiser.startAdvertising();
        Log.d(TAG, "startGattServer finished");
    }

    /**
     * Class to provide callback for GATT server to handle GATT requests
     */
    class MyGattServerCallback extends BluetoothGattServerCallback {

        MyGattServerCallback() {}

        @Override
        public void onServiceAdded(int status, BluetoothGattService service) {
            Log.d(TAG, "onServiceAdded: " + status);
        }

        @Override
        public void onCharacteristicReadRequest(BluetoothDevice device, int requestId, int offset,
                BluetoothGattCharacteristic characteristic) {
            Log.d(TAG, "onCharacteristicReadRequest: " + characteristic);
        }

        @Override
        public void onCharacteristicWriteRequest(BluetoothDevice device, int requestId,
                BluetoothGattCharacteristic characteristic, boolean preparedWrite,
                boolean responseNeeded, int offset, byte[] value) {
            Log.d(TAG, "onCharacteristicWriteRequest requestId: " + requestId
                        + " preparedWrite: " + preparedWrite + " sendRespons back");

            mGattServer.sendResponse(device, requestId, BluetoothGatt.GATT_SUCCESS, offset, value);
        }

        @Override
        public void onDescriptorReadRequest(BluetoothDevice device, int requestId, int offset,
                BluetoothGattDescriptor descriptor) {
            Log.d(TAG, "onDescriptorReadRequest requestId: " + requestId);
        }

        @Override
        public void onDescriptorWriteRequest(BluetoothDevice device, int requestId,
                BluetoothGattDescriptor descriptor, boolean preparedWrite, boolean responseNeeded,
                int offset, byte[] value) {
            Log.d(TAG, "onDescriptorWriteRequest requestId: " + requestId + " preparedWrite: "
                    + preparedWrite);
        }

        @Override
        public void onExecuteWrite(BluetoothDevice device, int requestId, boolean execute) {
            Log.d(TAG, "onExecuteWrite requestId: " + requestId + " execute: " + execute);
            mGattServer.sendResponse(device, requestId, BluetoothGatt.GATT_SUCCESS, 0, null);
            Log.d(TAG, "onExecuteWrite sendResponse back to GATT Client");
        }

        @Override
        public void onNotificationSent(BluetoothDevice device, int status) {
            Log.d(TAG, "onNotificationSent " + status);
        }

        @Override
        public void onConnectionStateChange(BluetoothDevice device, int status, int newState) {
            Log.d(TAG, "onConnectionStateChange status: " + status + " new state: " + newState);
            if (newState == BluetoothProfile.STATE_CONNECTED) {
                Log.d(TAG, "Connected to mac address " + device.getAddress() + " status " + status);

            } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                Log.d(TAG, "Disconnected from mac address " + device.getAddress() + " status "
                        + status);
            }
        }

        @Override
        public void onMtuChanged(BluetoothDevice device, int mtu) {
            Log.d(TAG, "onMtuChanged: " + mtu);
        }
    }

    /**
     * Class to provide BLE Advertising functionalities
     */
    class MyBleAdvertiser {

        private BluetoothLeAdvertiser mAdvertiser;
        private AdvertiseSettings mAdvertiseSettings;
        private AdvertiseData mAdvertiseData;
        private MyAdvertiseCallback mAdvertiseCallback;

        /**
         * Constructor
         * @param bluetoothAdapter - Default BluetoothAdapter
         */
        MyBleAdvertiser(BluetoothAdapter bluetoothAdapter) {
            // Prepare for BLE Advertisement
            mAdvertiser = bluetoothAdapter.getBluetoothLeAdvertiser();
            mAdvertiseData = new AdvertiseData.Builder().setIncludeDeviceName(true).build();
            mAdvertiseCallback = new MyAdvertiseCallback();
            mAdvertiseSettings = new AdvertiseSettings.Builder()
                                 .setAdvertiseMode(AdvertiseSettings.ADVERTISE_MODE_BALANCED)
                                 .setTxPowerLevel(AdvertiseSettings.ADVERTISE_TX_POWER_HIGH)
                                 .setConnectable(true)
                                 .setTimeout(0).build();
        }

        /**
         * Wrapper function to start BLE Advertising
         */
        public void startAdvertising() {
            mAdvertiser.startAdvertising(mAdvertiseSettings, mAdvertiseData,
                        mAdvertiseCallback);
        }

        /**
         * Wrapper function to stop BLE Advertising
         */
        public void stopAdvertising() {
            mAdvertiser.stopAdvertising(mAdvertiseCallback);
        }

        /**
         * Class to provide callback to handle BLE Advertisement
         */
        class MyAdvertiseCallback extends AdvertiseCallback {
            private boolean mMaxReached;
            // The lock object is used to synchronize mMaxReached
            private final Object mLock = new Object();

            MyAdvertiseCallback() {
                mMaxReached = false;
            }

            public void setMaxReached(boolean setMax) {
                synchronized (mLock) {
                    mMaxReached = setMax;
                }
            }
            public boolean getMaxReached() {
                synchronized (mLock) {
                    return mMaxReached;
                }
            }

            @Override
            public void onStartSuccess(AdvertiseSettings settingsInEffect) {
                Log.d(TAG, "bluetooth_le_advertisement onSuccess ");
                if (getMaxReached()) {
                    Log.d(TAG, "Stop Advertising");
                    mBleAdvertiser.stopAdvertising();
                } else {
                    Log.d(TAG, "Start Advertising");
                    mBleAdvertiser.startAdvertising();
                }
            }

            @Override
            public void onStartFailure(int errorCode) {
                String errorString = "UNKNOWN_ERROR_CODE";
                if (errorCode == AdvertiseCallback.ADVERTISE_FAILED_ALREADY_STARTED) {
                    errorString = "ADVERTISE_FAILED_ALREADY_STARTED";
                } else if (errorCode == AdvertiseCallback.ADVERTISE_FAILED_DATA_TOO_LARGE) {
                    errorString = "ADVERTISE_FAILED_DATA_TOO_LARGE";
                } else if (errorCode == AdvertiseCallback.ADVERTISE_FAILED_FEATURE_UNSUPPORTED) {
                    errorString = "ADVERTISE_FAILED_FEATURE_UNSUPPORTED";
                } else if (errorCode == AdvertiseCallback.ADVERTISE_FAILED_INTERNAL_ERROR) {
                    errorString = "ADVERTISE_FAILED_INTERNAL_ERROR";
                } else if (errorCode == AdvertiseCallback.ADVERTISE_FAILED_TOO_MANY_ADVERTISERS) {
                    errorString = "ADVERTISE_FAILED_TOO_MANY_ADVERTISERS";
                    setMaxReached(true);
                }
                Log.d(TAG, "bluetooth_le_advertisement onFailure: " + errorString);
            }
        }
    }
}
