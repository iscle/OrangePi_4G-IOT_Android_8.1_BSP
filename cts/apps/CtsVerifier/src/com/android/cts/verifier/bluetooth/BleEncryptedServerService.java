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

import android.app.Service;
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
import android.content.Intent;
import android.os.Handler;
import android.os.IBinder;
import android.os.ParcelUuid;
import android.util.Log;

import java.util.Arrays;
import java.util.UUID;

public class BleEncryptedServerService extends Service {
    public BleEncryptedServerService() {
    }
    public static final boolean DEBUG = true;
    public static final String TAG = "BleEncryptedServer";

    public static final String INTENT_BLUETOOTH_DISABLED =
            "com.android.cts.verifier.bluetooth.encripted.intent.BLUETOOTH_DISABLED";

    public static final String ACTION_CONNECT_WITH_SECURE =
            "com.android.cts.verifier.bluetooth.encripted.action.ACTION_CONNECT_WITH_SECURE";
    public static final String ACTION_CONNECT_WITHOUT_SECURE =
            "com.android.cts.verifier.bluetooth.encripted.action.ACTION_CONNECT_WITHOUT_SECURE";

    public static final String INTENT_WAIT_WRITE_ENCRYPTED_CHARACTERISTIC =
            "com.android.cts.verifier.bluetooth.encripted.intent.WAIT_WRITE_ENCRYPTED_CHARACTERISTIC";
    public static final String INTENT_WAIT_READ_ENCRYPTED_CHARACTERISTIC =
            "com.android.cts.verifier.bluetooth.encripted.intent.WAIT_READ_ENCRYPTED_CHARACTERISTIC";
    public static final String INTENT_WAIT_WRITE_ENCRYPTED_DESCRIPTOR =
            "com.android.cts.verifier.bluetooth.encripted.intent.WAIT_WRITE_ENCRYPTED_DESCRIPTOR";
    public static final String INTENT_WAIT_READ_ENCRYPTED_DESCRIPTOR =
            "com.android.cts.verifier.bluetooth.encripted.intent.WAIT_READ_ENCRYPTED_DESCRIPTOR";

    private static final UUID SERVICE_UUID =
            UUID.fromString("00009999-0000-1000-8000-00805f9b34fb");
    private static final UUID CHARACTERISTIC_UUID =
            UUID.fromString("00009998-0000-1000-8000-00805f9b34fb");
    private static final UUID DESCRIPTOR_UUID =
            UUID.fromString("00009997-0000-1000-8000-00805f9b34fb");

    private static final UUID CHARACTERISTIC_ENCRYPTED_WRITE_UUID =
            UUID.fromString("00009996-0000-1000-8000-00805f9b34fb");
    private static final UUID CHARACTERISTIC_ENCRYPTED_READ_UUID =
            UUID.fromString("00009995-0000-1000-8000-00805f9b34fb");
    private static final UUID DESCRIPTOR_ENCRYPTED_WRITE_UUID =
            UUID.fromString("00009994-0000-1000-8000-00805f9b34fb");
    private static final UUID DESCRIPTOR_ENCRYPTED_READ_UUID =
            UUID.fromString("00009993-0000-1000-8000-00805f9b34fb");

    public static final UUID ADV_SERVICE_UUID=
            UUID.fromString("00002222-0000-1000-8000-00805f9b34fb");

    private static final int CONN_INTERVAL = 150;   // connection interval 150ms

    public static final String EXTRA_SECURE = "SECURE";
    public static final String WRITE_CHARACTERISTIC = "WRITE_CHAR";
    public static final String READ_CHARACTERISTIC = "READ_CHAR";
    public static final String WRITE_DESCRIPTOR = "WRITE_DESC";
    public static final String READ_DESCRIPTOR = "READ_DESC";

    public static final String WRITE_VALUE = "ENC_SERVER_TEST";

    private BluetoothManager mBluetoothManager;
    private BluetoothGattServer mGattServer;
    private BluetoothGattService mService;
    private BluetoothDevice mDevice;
    private BluetoothLeAdvertiser mAdvertiser;
    private boolean mSecure;
    private String mTarget;
    private Handler mHandler;
    private Runnable mResetValuesTask;

    @Override
    public void onCreate() {
        super.onCreate();

        mHandler = new Handler();
        mBluetoothManager = (BluetoothManager) getSystemService(Context.BLUETOOTH_SERVICE);
        mAdvertiser = mBluetoothManager.getAdapter().getBluetoothLeAdvertiser();
        mGattServer = mBluetoothManager.openGattServer(this, mCallbacks);
        mService = createService();
        if ((mGattServer != null) && (mAdvertiser != null)) {
            mGattServer.addService(mService);
        }
        mDevice = null;
        mSecure = false;
        if (!mBluetoothManager.getAdapter().isEnabled()) {
            notifyBluetoothDisabled();
        } else if (mGattServer == null) {
            notifyOpenFail();
        } else if (mAdvertiser == null) {
            notifyAdvertiseUnsupported();
        } else {
            startAdvertise();
        }

        resetValues();
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        stopAdvertise();
        if (mGattServer == null) {
            return;
        }
        if (mDevice != null) {
            mGattServer.cancelConnection(mDevice);
        }
        mGattServer.clearServices();
        mGattServer.close();
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        String action = intent.getAction();
        if (action != null) {
            switch (action) {
            case ACTION_CONNECT_WITH_SECURE:
                mSecure = true;
                break;
            case ACTION_CONNECT_WITHOUT_SECURE:
                mSecure = false;
                break;
            }
        }
        return START_NOT_STICKY;
    }

    /**
     * Sets default value to characteristic and descriptor.
     *
     * Set operation will be done after connection interval.
     * (If set values immediately, multiple read/write operations may fail.)
     */
    private synchronized void resetValues() {
        // cancel pending task
        if (mResetValuesTask != null) {
            mHandler.removeCallbacks(mResetValuesTask);
            mResetValuesTask = null;
        }

        // reserve task
        mResetValuesTask = new Runnable() {
            @Override
            public void run() {
                BluetoothGattCharacteristic characteristic = mService.getCharacteristic(CHARACTERISTIC_ENCRYPTED_READ_UUID);
                characteristic.setValue(WRITE_VALUE.getBytes());
                characteristic = mService.getCharacteristic(CHARACTERISTIC_UUID);
                characteristic.getDescriptor(DESCRIPTOR_ENCRYPTED_READ_UUID).setValue(WRITE_VALUE.getBytes());
            }
        };
        mHandler.postDelayed(mResetValuesTask, CONN_INTERVAL);
    }

    private void notifyBluetoothDisabled() {
        Intent intent = new Intent(INTENT_BLUETOOTH_DISABLED);
        sendBroadcast(intent);
    }

    private void notifyOpenFail() {
        if (DEBUG) {
            Log.d(TAG, "notifyOpenFail");
        }
        Intent intent = new Intent(BleServerService.BLE_OPEN_FAIL);
        sendBroadcast(intent);
    }

    private void notifyAdvertiseUnsupported() {
        if (DEBUG) {
            Log.d(TAG, "notifyAdvertiseUnsupported");
        }
        Intent intent = new Intent(BleServerService.BLE_ADVERTISE_UNSUPPORTED);
        sendBroadcast(intent);
    }

    private void notifyConnected() {
        if (DEBUG) {
            Log.d(TAG, "notifyConnected");
        }
        resetValues();
    }

    private void notifyDisconnected() {
        if (DEBUG) {
            Log.d(TAG, "notifyDisconnected");
        }
    }

    private void notifyServiceAdded() {
        if (DEBUG) {
            Log.d(TAG, "notifyServiceAdded");
        }
    }

    private void notifyCharacteristicWriteRequest() {
        if (DEBUG) {
            Log.d(TAG, "notifyCharacteristicWriteRequest");
        }
        Intent intent = new Intent(INTENT_WAIT_WRITE_ENCRYPTED_CHARACTERISTIC);
        sendBroadcast(intent);
        resetValues();
    }

    private void notifyCharacteristicReadRequest() {
        if (DEBUG) {
            Log.d(TAG, "notifyCharacteristicReadRequest");
        }
        Intent intent = new Intent(INTENT_WAIT_READ_ENCRYPTED_CHARACTERISTIC);
        sendBroadcast(intent);
        resetValues();
    }

    private void notifyDescriptorWriteRequest() {
        if (DEBUG) {
            Log.d(TAG, "notifyDescriptorWriteRequest");
        }
        Intent intent = new Intent(INTENT_WAIT_WRITE_ENCRYPTED_DESCRIPTOR);
        sendBroadcast(intent);
        resetValues();
    }

    private void notifyDescriptorReadRequest() {
        if (DEBUG) {
            Log.d(TAG, "notifyDescriptorReadRequest");
        }
        Intent intent = new Intent(INTENT_WAIT_READ_ENCRYPTED_DESCRIPTOR);
        sendBroadcast(intent);
        resetValues();
    }

    private BluetoothGattService createService() {
        BluetoothGattService service =
                new BluetoothGattService(SERVICE_UUID, BluetoothGattService.SERVICE_TYPE_PRIMARY);
        // add characteristic to service
        //   property: 0x0A (read, write)
        //   permission: 0x11 (read, write)
        BluetoothGattCharacteristic characteristic =
                new BluetoothGattCharacteristic(CHARACTERISTIC_UUID, 0x0A, 0x11);

        BluetoothGattDescriptor descriptor = new BluetoothGattDescriptor(DESCRIPTOR_UUID, 0x11);
        characteristic.addDescriptor(descriptor);

        // Encrypted Descriptor
        descriptor = new BluetoothGattDescriptor(DESCRIPTOR_ENCRYPTED_READ_UUID, 0x02);
        characteristic.addDescriptor(descriptor);
        descriptor = new BluetoothGattDescriptor(DESCRIPTOR_ENCRYPTED_WRITE_UUID, 0x20);
        characteristic.addDescriptor(descriptor);
        service.addCharacteristic(characteristic);

        // Encrypted Characteristic
        characteristic = new BluetoothGattCharacteristic(CHARACTERISTIC_ENCRYPTED_READ_UUID, 0x0A, 0x02);
        descriptor = new BluetoothGattDescriptor(DESCRIPTOR_UUID, 0x11);
        characteristic.addDescriptor(descriptor);
        service.addCharacteristic(characteristic);
        characteristic = new BluetoothGattCharacteristic(CHARACTERISTIC_ENCRYPTED_WRITE_UUID, 0x0A, 0x20);
        descriptor = new BluetoothGattDescriptor(DESCRIPTOR_UUID, 0x11);
        characteristic.addDescriptor(descriptor);
        service.addCharacteristic(characteristic);

        return service;
    }

    private final BluetoothGattServerCallback mCallbacks = new BluetoothGattServerCallback() {
        @Override
        public void onConnectionStateChange(BluetoothDevice device, int status, int newState) {
            if (DEBUG) {
                Log.d(TAG, "onConnectionStateChange: newState=" + newState);
            }
            if (status == BluetoothGatt.GATT_SUCCESS) {
                if (newState == BluetoothProfile.STATE_CONNECTED) {
                    mDevice = device;
                    notifyConnected();
                } else if (status == BluetoothProfile.STATE_DISCONNECTED) {
                    notifyDisconnected();
                    mDevice = null;
                    mTarget = null;
                }
            }
        }

        @Override
        public void onServiceAdded(int status, BluetoothGattService service) {
            if (DEBUG) {
                Log.d(TAG, "onServiceAdded()");
            }
            if (status == BluetoothGatt.GATT_SUCCESS) {
                notifyServiceAdded();
            }
        }

        String mPriority = null;

        @Override
        public void onCharacteristicWriteRequest(BluetoothDevice device, int requestId,
                                                 BluetoothGattCharacteristic characteristic,
                                                 boolean preparedWrite, boolean responseNeeded,
                                                 int offset, byte[] value) {
            int status = BluetoothGatt.GATT_SUCCESS;
            if (mGattServer == null) {
                if (DEBUG) {
                    Log.d(TAG, "GattServer is null, return");
                }
                return;
            }
            if (DEBUG) {
                Log.d(TAG, "onCharacteristicWriteRequest: preparedWrite=" + preparedWrite);
            }
            if (characteristic.getUuid().equals(CHARACTERISTIC_ENCRYPTED_WRITE_UUID)) {
                if (mSecure) {
                    characteristic.setValue(value);
                    if (Arrays.equals(BleEncryptedClientService.WRITE_VALUE.getBytes(), characteristic.getValue())) {
                        notifyCharacteristicWriteRequest();
                    } else {
                        status = BluetoothGatt.GATT_FAILURE;
                    }
                } else {
                    // will not occur
                    status = BluetoothGatt.GATT_FAILURE;
                }
            } else if (characteristic.getUuid().equals(CHARACTERISTIC_UUID)) {
                mTarget = new String(value);
                characteristic.setValue(value);
            }

            if (responseNeeded) {
                mGattServer.sendResponse(device, requestId, status, offset, value);
            }
        }

        @Override
        public void onCharacteristicReadRequest(BluetoothDevice device, int requestId, int offset, BluetoothGattCharacteristic characteristic) {
            int status = BluetoothGatt.GATT_SUCCESS;
            if (mGattServer == null) {
                if (DEBUG) {
                    Log.d(TAG, "GattServer is null, return");
                }
                return;
            }
            if (DEBUG) {
                Log.d(TAG, "onCharacteristicReadRequest()");
            }
            if (characteristic.getUuid().equals(CHARACTERISTIC_ENCRYPTED_READ_UUID)) {
                if (mSecure) {
                    notifyCharacteristicReadRequest();
                }
            }
            mGattServer.sendResponse(device, requestId, status, offset, characteristic.getValue());
        }

        @Override
        public void onDescriptorReadRequest(BluetoothDevice device, int requestId, int offset, BluetoothGattDescriptor descriptor) {
            int status = BluetoothGatt.GATT_SUCCESS;
            if (mGattServer == null) {
                if (DEBUG) {
                    Log.d(TAG, "GattServer is null, return");
                }
                return;
            }
            if (DEBUG) {
                Log.d(TAG, "onDescriptorReadRequest():");
            }

            if (descriptor.getUuid().equals(DESCRIPTOR_ENCRYPTED_READ_UUID)) {
                if (mSecure) {
                    notifyDescriptorReadRequest();
                }
            }
            Log.d(TAG, "  status = " + status);
            mGattServer.sendResponse(device, requestId, status, offset, descriptor.getValue());
        }

        @Override
        public void onDescriptorWriteRequest(BluetoothDevice device, int requestId, BluetoothGattDescriptor descriptor, boolean preparedWrite, boolean responseNeeded, int offset, byte[] value) {
            int status = BluetoothGatt.GATT_SUCCESS;
            if (mGattServer == null) {
                if (DEBUG) {
                    Log.d(TAG, "GattServer is null, return");
                }
                return;
            }

            if (DEBUG) {
                Log.d(TAG, "onDescriptorWriteRequest: preparedWrite=" + preparedWrite + ", responseNeeded= " + responseNeeded);
            }

            if (descriptor.getUuid().equals(DESCRIPTOR_ENCRYPTED_WRITE_UUID)) {
                if (mSecure) {
                    descriptor.setValue(value);
                    if (Arrays.equals(BleEncryptedClientService.WRITE_VALUE.getBytes(), descriptor.getValue())) {
                        notifyDescriptorWriteRequest();
                    } else {
                        status = BluetoothGatt.GATT_FAILURE;
                    }
                } else {
                    // will not occur
                    status = BluetoothGatt.GATT_FAILURE;
                }
            }

            if (responseNeeded) {
                mGattServer.sendResponse(device, requestId, status, offset, value);
            }
        }
    };

    private final AdvertiseCallback mAdvertiseCallback = new AdvertiseCallback() {
        @Override
        public void onStartFailure(int errorCode) {
            super.onStartFailure(errorCode);
            if (errorCode == ADVERTISE_FAILED_FEATURE_UNSUPPORTED) {
                notifyAdvertiseUnsupported();
            } else {
                notifyOpenFail();
            }
        }
    };

    private void startAdvertise() {
        if (DEBUG) {
            Log.d(TAG, "startAdvertise");
        }
        AdvertiseData data = new AdvertiseData.Builder()
                .addServiceData(new ParcelUuid(ADV_SERVICE_UUID), new byte[]{1, 2, 3})
                .addServiceUuid(new ParcelUuid(ADV_SERVICE_UUID))
                .build();
        AdvertiseSettings setting = new AdvertiseSettings.Builder()
                .setAdvertiseMode(AdvertiseSettings.ADVERTISE_MODE_LOW_LATENCY)
                .setTxPowerLevel(AdvertiseSettings.ADVERTISE_TX_POWER_MEDIUM)
                .setConnectable(true)
                .build();
        mAdvertiser.startAdvertising(setting, data, mAdvertiseCallback);
    }

    private void stopAdvertise() {
        if (DEBUG) {
            Log.d(TAG, "stopAdvertise");
        }
        if (mAdvertiser != null) {
            mAdvertiser.stopAdvertising(mAdvertiseCallback);
        }
    }
}
