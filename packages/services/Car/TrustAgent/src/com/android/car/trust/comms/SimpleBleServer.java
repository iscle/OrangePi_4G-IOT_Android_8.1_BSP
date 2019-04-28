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
 * limitations under the License
 */
package com.android.car.trust.comms;

import android.app.Service;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCharacteristic;
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
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.IBinder;
import android.os.ParcelUuid;
import android.util.Log;

import java.util.HashSet;

/**
 * A generic service to start a BLE
 */
public abstract class SimpleBleServer extends Service {

    /**
     * Listener that is notified when the status of the BLE server changes.
     */
    public interface ConnectionListener {
        /**
         * Called when the GATT server is started and BLE is successfully advertising.
         */
        void onServerStarted();

        /**
         * Called when the BLE advertisement fails to start.
         *
         * @param errorCode Error code (see {@link AdvertiseCallback}#ADVERTISE_FAILED_* constants)
         */
        void onServerStartFailed(int errorCode);

        /**
         * Called when a device is connected.
         * @param device
         */
        void onDeviceConnected(BluetoothDevice device);
    }

    private static final String TAG = "SimpleBleServer";

    private BluetoothLeAdvertiser mAdvertiser;
    protected BluetoothGattServer mGattServer;

    private HashSet<ConnectionListener> mListeners = new HashSet<>();

    @Override
    public IBinder onBind(Intent intent) {
        // Override in child classes.
        return null;
    }

    /**
     * Starts the GATT server with the given {@link BluetoothGattService} and begins
     * advertising with the {@link ParcelUuid}.
     * @param advertiseUuid Service Uuid used in the {@link AdvertiseData}
     * @param service {@link BluetoothGattService} that will be discovered by clients
     */
    protected void start(ParcelUuid advertiseUuid, BluetoothGattService service) {
        if (!getPackageManager().hasSystemFeature(PackageManager.FEATURE_BLUETOOTH_LE)) {
            Log.e(TAG, "System does not support BLE");
            return;
        }

        BluetoothManager btManager =
                (BluetoothManager) getSystemService(Context.BLUETOOTH_SERVICE);

        mGattServer = btManager.openGattServer(this, mGattServerCallback);
        if (mGattServer == null) {
            Log.e(TAG, "Gatt Server not created");
            return;
        }

        // We only allow adding one service in this implementation. If multiple services need
        // to be added, then they need to be queued up and added only after
        // BluetoothGattServerCallback.onServiceAdded is called.
        mGattServer.addService(service);

        AdvertiseSettings settings = new AdvertiseSettings.Builder()
                .setAdvertiseMode(AdvertiseSettings.ADVERTISE_MODE_LOW_LATENCY)
                .setTxPowerLevel(AdvertiseSettings.ADVERTISE_TX_POWER_HIGH)
                .setConnectable(true)
                .build();

        AdvertiseData data = new AdvertiseData.Builder()
                .setIncludeDeviceName(true)
                .addServiceUuid(advertiseUuid)
                .build();

        mAdvertiser
                = BluetoothAdapter.getDefaultAdapter().getBluetoothLeAdvertiser();

        mAdvertiser.startAdvertising(settings, data, mAdvertisingCallback);
    }

    /**
     * Stops the advertiser and GATT server. This needs to be done to avoid leaks
     */
    protected void stop() {
        if (mAdvertiser != null) {
            mAdvertiser.stopAdvertising(mAdvertisingCallback);
            mAdvertiser.cleanup();
        }

        if (mGattServer != null) {
            mGattServer.clearServices();
            try {
                for (BluetoothDevice d : mGattServer.getConnectedDevices()) {
                    mGattServer.cancelConnection(d);
                }
            } catch (UnsupportedOperationException e) {
                Log.e(TAG, "Error getting connected devices", e);
            } finally {
                mGattServer.close();
            }
        }

        mListeners.clear();
    }

    @Override
    public void onDestroy() {
        stop();
        super.onDestroy();
    }

    public void addConnectionListener(ConnectionListener listener) {
        if (Log.isLoggable(TAG, Log.DEBUG)) {
            Log.d(TAG, "Adding connection listener");
        }
        mListeners.add(listener);
    }

    /**
     * Triggered when this BleService receives a write request from a remote
     * device. Sub-classes should implement how to handle requests.
     */
    public abstract void onCharacteristicWrite(final BluetoothDevice device, int requestId,
            BluetoothGattCharacteristic characteristic, boolean preparedWrite, boolean
            responseNeeded, int offset, byte[] value);

    /**
     * Triggered when this BleService receives a read request from a remote device.
     */
    public abstract void onCharacteristicRead(BluetoothDevice device,
            int requestId, int offset, final BluetoothGattCharacteristic characteristic);

    private AdvertiseCallback mAdvertisingCallback = new AdvertiseCallback() {
        @Override
        public void onStartSuccess(AdvertiseSettings settingsInEffect) {
            super.onStartSuccess(settingsInEffect);
            if (Log.isLoggable(TAG, Log.DEBUG)) {
                Log.d(TAG, "Successfully started advertising service");
            }
            for (ConnectionListener listener : mListeners) {
                listener.onServerStarted();
            }
        }

        @Override
        public void onStartFailure(int errorCode) {
            super.onStartFailure(errorCode);
            if (Log.isLoggable(TAG, Log.DEBUG)) {
                Log.d(TAG, "Failed to advertise, errorCode: " + errorCode);
            }
            for (ConnectionListener listener : mListeners) {
                listener.onServerStartFailed(errorCode);
            }
        }
    };

    private BluetoothGattServerCallback mGattServerCallback = new BluetoothGattServerCallback() {
        @Override
        public void onConnectionStateChange(BluetoothDevice device,
                final int status, final int newState) {
            if (Log.isLoggable(TAG, Log.DEBUG)) {
                Log.d(TAG, "GattServer connection change status: "
                        + newState + " newState: "
                        + newState + " device name: " + device.getName());
            }
            if (newState == BluetoothProfile.STATE_CONNECTED) {
                for (ConnectionListener listener : mListeners) {
                    listener.onDeviceConnected(device);
                }
            }
        }

        @Override
        public void onServiceAdded(final int status, BluetoothGattService service) {
            if (Log.isLoggable(TAG, Log.DEBUG)) {
                Log.d(TAG, "Service added status: " + status + " uuid: " + service.getUuid());
            }
        }

        @Override
        public void onCharacteristicReadRequest(BluetoothDevice device,
                int requestId, int offset, final BluetoothGattCharacteristic characteristic) {
            if (Log.isLoggable(TAG, Log.DEBUG)) {
                Log.d(TAG, "Read request for characteristic: " + characteristic.getUuid());
            }
            mGattServer.sendResponse(device, requestId,
                    BluetoothGatt.GATT_SUCCESS, offset, characteristic.getValue());
            SimpleBleServer.
                    this.onCharacteristicRead(device, requestId, offset, characteristic);
        }

        @Override
        public void onCharacteristicWriteRequest(final BluetoothDevice device, int requestId,
                BluetoothGattCharacteristic characteristic, boolean preparedWrite, boolean
                responseNeeded, int offset, byte[] value) {
            if (Log.isLoggable(TAG, Log.DEBUG)) {
                Log.d(TAG, "Write request for characteristic: " + characteristic.getUuid());
            }
            mGattServer.sendResponse(device, requestId, BluetoothGatt.GATT_SUCCESS,
                    offset, value);

            SimpleBleServer.
                    this.onCharacteristicWrite(device, requestId, characteristic,
                    preparedWrite, responseNeeded, offset, value);
        }
    };
}
