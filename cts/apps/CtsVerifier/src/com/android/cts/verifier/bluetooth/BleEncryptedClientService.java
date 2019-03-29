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
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCallback;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattDescriptor;
import android.bluetooth.BluetoothGattService;
import android.bluetooth.BluetoothManager;
import android.bluetooth.BluetoothProfile;
import android.bluetooth.le.BluetoothLeScanner;
import android.bluetooth.le.ScanCallback;
import android.bluetooth.le.ScanFilter;
import android.bluetooth.le.ScanResult;
import android.bluetooth.le.ScanSettings;
import android.content.Context;
import android.content.Intent;
import android.os.Handler;
import android.os.IBinder;
import android.os.ParcelUuid;
import android.util.Log;
import android.widget.Toast;

import java.util.Arrays;
import java.util.List;
import java.util.UUID;

public class BleEncryptedClientService extends Service {
    public static final boolean DEBUG = true;
    public static final String TAG = "BleEncryptedClient";

    public static final String ACTION_CONNECT_WITH_SECURE =
            "com.android.cts.verifier.bluetooth.encripted.action.ACTION_CONNECT_WITH_SECURE";
    public static final String ACTION_CONNECT_WITHOUT_SECURE =
            "com.android.cts.verifier.bluetooth.encripted.action.ACTION_CONNECT_WITHOUT_SECURE";

    public static final String INTENT_BLE_BLUETOOTH_DISABLED =
            "com.android.cts.verifier.bluetooth.encripted.intent.BLE_BLUETOOTH_DISABLED";
    public static final String INTENT_BLE_WRITE_ENCRYPTED_CHARACTERISTIC =
            "com.android.cts.verifier.bluetooth.encripted.intent.BLE_WRITE_ENCRYPTED_CHARACTERISTIC";
    public static final String INTENT_BLE_WRITE_NOT_ENCRYPTED_CHARACTERISTIC =
            "com.android.cts.verifier.bluetooth.encripted.intent.BLE_WRITE_NOT_ENCRYPTED_CHARACTERISTIC";
    public static final String INTENT_BLE_READ_ENCRYPTED_CHARACTERISTIC =
            "com.android.cts.verifier.bluetooth.encripted.intent.BLE_READ_ENCRYPTED_CHARACTERISTIC";
    public static final String INTENT_BLE_READ_NOT_ENCRYPTED_CHARACTERISTIC =
            "com.android.cts.verifier.bluetooth.encripted.intent.BLE_READ_NOT_ENCRYPTED_CHARACTERISTIC";
    public static final String INTENT_BLE_WRITE_ENCRYPTED_DESCRIPTOR =
            "com.android.cts.verifier.bluetooth.encripted.intent.BLE_WRITE_ENCRYPTED_DESCRIPTOR";
    public static final String INTENT_BLE_WRITE_NOT_ENCRYPTED_DESCRIPTOR =
            "com.android.cts.verifier.bluetooth.encripted.intent.BLE_WRITE_NOT_ENCRYPTED_DESCRIPTOR";
    public static final String INTENT_BLE_READ_ENCRYPTED_DESCRIPTOR =
            "com.android.cts.verifier.bluetooth.encripted.intent.BLE_READ_ENCRYPTED_DESCRIPTOR";
    public static final String INTENT_BLE_READ_NOT_ENCRYPTED_DESCRIPTOR =
            "com.android.cts.verifier.bluetooth.encripted.intent.BLE_READ_NOT_ENCRYPTED_DESCRIPTOR";
    public static final String INTENT_BLE_WRITE_FAIL_ENCRYPTED_CHARACTERISTIC =
            "com.android.cts.verifier.bluetooth.encripted.intent.INTENT_BLE_WRITE_FAIL_ENCRYPTED_CHARACTERISTIC";
    public static final String INTENT_BLE_READ_FAIL_ENCRYPTED_CHARACTERISTIC =
            "com.android.cts.verifier.bluetooth.encripted.intent.INTENT_BLE_READ_FAIL_ENCRYPTED_CHARACTERISTIC";
    public static final String INTENT_BLE_WRITE_FAIL_ENCRYPTED_DESCRIPTOR =
            "com.android.cts.verifier.bluetooth.encripted.intent.INTENT_BLE_WRITE_FAIL_ENCRYPTED_DESCRIPTOR";
    public static final String INTENT_BLE_READ_FAIL_ENCRYPTED_DESCRIPTOR =
            "com.android.cts.verifier.bluetooth.encripted.intent.INTENT_BLE_READ_FAIL_ENCRYPTED_DESCRIPTOR";

    public static final String ACTION_WRITE_ENCRYPTED_CHARACTERISTIC =
            "com.android.cts.verifier.bluetooth.encripted.action.WRITE_ENCRYPTED_CHARACTERISTIC";
    public static final String ACTION_READ_ENCRYPTED_CHARACTERISTIC =
            "com.android.cts.verifier.bluetooth.encripted.action.READ_ENCRYPTED_CHARACTERISTIC";
    public static final String ACTION_WRITE_ENCRYPTED_DESCRIPTOR =
            "com.android.cts.verifier.bluetooth.encripted.action.WRITE_ENCRYPTED_DESCRIPTOR";
    public static final String ACTION_READ_ENCRYPTED_DESCRIPTOR =
            "com.android.cts.verifier.bluetooth.encripted.action.READ_ENCRYPTED_DESCRIPTOR";

    public static final String ACTION_DISCONNECTED =
            "com.android.cts.verifier.bluetooth.encripted.action.DISCONNECTED";

    public static final String WRITE_VALUE = "ENC_CLIENT_TEST";

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

    private BluetoothManager mBluetoothManager;
    private BluetoothAdapter mBluetoothAdapter;
    private BluetoothGatt mBluetoothGatt;
    private BluetoothLeScanner mScanner;
    private BluetoothDevice mDevice;
    private Handler mHandler;
    private Context mContext;
    private String mAction;
    private boolean mSecure;
    private String mTarget;

    private String mLastScanError;
    private TestTaskQueue mTaskQueue;

    public BleEncryptedClientService() {
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public void onCreate() {
        super.onCreate();

        mTaskQueue = new TestTaskQueue(getClass().getName() + "_enc_cli_taskHandlerThread");

        mBluetoothManager = (BluetoothManager) getSystemService(Context.BLUETOOTH_SERVICE);
        mBluetoothAdapter = mBluetoothManager.getAdapter();

        mScanner = mBluetoothAdapter.getBluetoothLeScanner();
        mHandler = new Handler();
        mContext = this;
        mSecure = false;
    }

    @Override
    public void onDestroy() {
        super.onDestroy();

        mTaskQueue.quit();

        if (mBluetoothGatt != null) {
            mBluetoothGatt.disconnect();
            mBluetoothGatt.close();
            mBluetoothGatt = null;
            mDevice = null;
        }
        stopScan();
    }

    private void notifyBluetoothDisabled() {
        Intent intent = new Intent(INTENT_BLE_BLUETOOTH_DISABLED);
        sendBroadcast(intent);
    }

    private void notifyDisconnected() {
        Intent intent = new Intent(ACTION_DISCONNECTED);
        sendBroadcast(intent);
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (!mBluetoothAdapter.isEnabled()) {
            notifyBluetoothDisabled();
        } else {
            if (intent != null) {
                mAction = intent.getAction();
                if (mAction == null) {
                    mSecure = intent.getBooleanExtra(BleEncryptedServerService.EXTRA_SECURE, false);
                } else {
                    switch (mAction) {
                    case ACTION_CONNECT_WITH_SECURE:
                        mSecure = true;
                        break;
                    case ACTION_CONNECT_WITHOUT_SECURE:
                        mSecure = false;
                        break;
                    case ACTION_WRITE_ENCRYPTED_CHARACTERISTIC:
                        mTarget = BleEncryptedServerService.WRITE_CHARACTERISTIC;
                        startScan();
                        break;
                    case ACTION_READ_ENCRYPTED_CHARACTERISTIC:
                        mTarget = BleEncryptedServerService.READ_CHARACTERISTIC;
                        startScan();
                        break;
                    case ACTION_WRITE_ENCRYPTED_DESCRIPTOR:
                        mTarget = BleEncryptedServerService.WRITE_DESCRIPTOR;
                        startScan();
                        break;
                    case ACTION_READ_ENCRYPTED_DESCRIPTOR:
                        mTarget = BleEncryptedServerService.READ_DESCRIPTOR;
                        startScan();
                        break;
                    default:
                        return START_NOT_STICKY;
                    }
                }
            }
        }
        return START_NOT_STICKY;
    }

    private BluetoothGattService getService() {
        BluetoothGattService service = null;

        if (mBluetoothGatt != null) {
            service = mBluetoothGatt.getService(SERVICE_UUID);
            if (service == null) {
                showMessage("Service not found");
            }
        }
        return service;
    }

    private BluetoothGattCharacteristic getCharacteristic(UUID uuid) {
        BluetoothGattCharacteristic characteristic = null;

        BluetoothGattService service = getService();
        if (service != null) {
            characteristic = service.getCharacteristic(uuid);
            if (characteristic == null) {
                showMessage("Characteristic not found");
            }
        }
        return characteristic;
    }

    private BluetoothGattDescriptor getDescriptor(UUID uid) {
        BluetoothGattDescriptor descriptor = null;

        BluetoothGattCharacteristic characteristic = getCharacteristic(CHARACTERISTIC_UUID);
        if (characteristic != null) {
            descriptor = characteristic.getDescriptor(uid);
            if (descriptor == null) {
                showMessage("Descriptor not found");
            }
        }
        return descriptor;
    }

    private void sleep(int millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Log.e(TAG, "Error in thread sleep", e);
        }
    }

    private void startEncryptedAction() {
        BluetoothGattCharacteristic characteristic;
        BluetoothGattCharacteristic caseCharacteristic;
        BluetoothGattDescriptor descriptor;
        switch (mTarget) {
        case BleEncryptedServerService.WRITE_CHARACTERISTIC:
            Log.v(TAG, "WRITE_CHARACTERISTIC");
            characteristic = getCharacteristic(CHARACTERISTIC_ENCRYPTED_WRITE_UUID);
            characteristic.setValue(WRITE_VALUE);
            mBluetoothGatt.writeCharacteristic(characteristic);
            break;
        case BleEncryptedServerService.READ_CHARACTERISTIC:
            Log.v(TAG, "READ_CHARACTERISTIC");
            characteristic = getCharacteristic(CHARACTERISTIC_ENCRYPTED_READ_UUID);
            mBluetoothGatt.readCharacteristic(characteristic);
            break;
        case BleEncryptedServerService.WRITE_DESCRIPTOR:
            Log.v(TAG, "WRITE_DESCRIPTOR");
            descriptor = getDescriptor(DESCRIPTOR_ENCRYPTED_WRITE_UUID);
            descriptor.setValue(WRITE_VALUE.getBytes());
            mBluetoothGatt.writeDescriptor(descriptor);
            break;
        case BleEncryptedServerService.READ_DESCRIPTOR:
            Log.v(TAG, "READ_DESCRIPTOR");
            descriptor = getDescriptor(DESCRIPTOR_ENCRYPTED_READ_UUID);
            mBluetoothGatt.readDescriptor(descriptor);
            break;
        }
    }

    private void showMessage(final String msg) {
        mHandler.post(new Runnable() {
            public void run() {
                Toast.makeText(BleEncryptedClientService.this, msg, Toast.LENGTH_SHORT).show();
            }
        });
    }

    private final BluetoothGattCallback mGattCallbacks = new BluetoothGattCallback() {
        @Override
        public void onConnectionStateChange(BluetoothGatt gatt, int status, int newState) {
            if (DEBUG) Log.d(TAG, "onConnectionStateChange: status = " + status + ", newState = " + newState);
            if (status == BluetoothGatt.GATT_SUCCESS) {
                if (newState == BluetoothProfile.STATE_CONNECTED) {
                    showMessage("Bluetooth LE connected");
                    mTaskQueue.addTask(new Runnable() {
                        @Override
                        public void run() {
                            mBluetoothGatt.discoverServices();
                        }
                    }, 1000);
                } else if (status == BluetoothProfile.STATE_DISCONNECTED) {
                    showMessage("Bluetooth LE disconnected");
                    mTaskQueue.addTask(new Runnable() {
                        @Override
                        public void run() {
                            if (mBluetoothGatt != null) {
                                mBluetoothGatt.close();
                                mBluetoothGatt = null;
                                mTarget = null;
                                mDevice = null;
                                notifyDisconnected();
                            }
                        }
                    }, 1000);
                }
            } else {
                showMessage("Connection Not Success.");
                if (mTarget != null) {
                    Intent intent;
                    switch (mTarget) {
                    case BleEncryptedServerService.READ_CHARACTERISTIC:
                        intent = new Intent(INTENT_BLE_READ_FAIL_ENCRYPTED_CHARACTERISTIC);
                        break;
                    case BleEncryptedServerService.WRITE_CHARACTERISTIC:
                        if (mSecure) {
                            intent = new Intent(INTENT_BLE_WRITE_FAIL_ENCRYPTED_CHARACTERISTIC);
                        } else {
                            intent = new Intent(INTENT_BLE_WRITE_ENCRYPTED_CHARACTERISTIC);
                        }
                        break;
                    case BleEncryptedServerService.READ_DESCRIPTOR:
                        intent = new Intent(INTENT_BLE_READ_FAIL_ENCRYPTED_DESCRIPTOR);
                        break;
                    case BleEncryptedServerService.WRITE_DESCRIPTOR:
                        if (mSecure) {
                            intent = new Intent(INTENT_BLE_WRITE_FAIL_ENCRYPTED_DESCRIPTOR);
                        } else {
                            intent = new Intent(INTENT_BLE_WRITE_ENCRYPTED_DESCRIPTOR);
                        }
                        break;
                    default:
                        return;
                    }
                    if (mBluetoothGatt != null) {
                        mBluetoothGatt.close();
                        mBluetoothGatt = null;
                        mDevice = null;
                        mTarget = null;
                    }
                    sendBroadcast(intent);
                }
            }
        }

        @Override
        public void onServicesDiscovered(BluetoothGatt gatt, int status) {
            if (DEBUG){
                Log.d(TAG, "onServiceDiscovered");
            }
            if ((status == BluetoothGatt.GATT_SUCCESS) && (mBluetoothGatt.getService(SERVICE_UUID) != null)) {
                showMessage("Service discovered");
                startEncryptedAction();
            }
        }

        @Override
        public void onCharacteristicWrite(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic, final int status) {
            final String value = characteristic.getStringValue(0);
            UUID uid = characteristic.getUuid();
            if (DEBUG) {
                Log.d(TAG, "onCharacteristicWrite: characteristic.val=" + value + " status=" + status + " uid=" + uid);
            }

            if (uid.equals(CHARACTERISTIC_ENCRYPTED_WRITE_UUID)) {
                mTaskQueue.addTask(new Runnable() {
                    @Override
                    public void run() {
                        if (status == BluetoothGatt.GATT_SUCCESS) {
                            if (mSecure) {
                                mBluetoothGatt.disconnect();
                                if (WRITE_VALUE.equals(value)) {
                                    Intent intent = new Intent(INTENT_BLE_WRITE_ENCRYPTED_CHARACTERISTIC);
                                    sendBroadcast(intent);
                                } else {
                                    showMessage("Written data is not correct");
                                }
                            }
                        } else {
                            if (!mSecure) {
                                mBluetoothGatt.disconnect();
                                Intent intent = new Intent(INTENT_BLE_WRITE_NOT_ENCRYPTED_CHARACTERISTIC);
                                sendBroadcast(intent);
                            } else {
                                mBluetoothGatt.disconnect();
                                Intent intent = new Intent(INTENT_BLE_WRITE_FAIL_ENCRYPTED_CHARACTERISTIC);
                                sendBroadcast(intent);
                            }
                        }
                    }
                }, 1000);
            }
        }

        @Override
        public void onCharacteristicRead(BluetoothGatt gatt, final BluetoothGattCharacteristic characteristic, final int status) {
            UUID uid = characteristic.getUuid();
            if (DEBUG) {
                Log.d(TAG, "onCharacteristicRead: status=" + status);
            }
            if (uid.equals(CHARACTERISTIC_ENCRYPTED_READ_UUID)) {
                mTaskQueue.addTask(new Runnable() {
                    @Override
                    public void run() {
                        if (status == BluetoothGatt.GATT_SUCCESS) {
                            if (mSecure) {
                                mBluetoothGatt.disconnect();
                                if (Arrays.equals(BleEncryptedServerService.WRITE_VALUE.getBytes(), characteristic.getValue())) {
                                    Intent intent = new Intent(INTENT_BLE_READ_ENCRYPTED_CHARACTERISTIC);
                                    sendBroadcast(intent);
                                } else {
                                    showMessage("Read data is not correct");
                                }
                            } else {
                                mBluetoothGatt.disconnect();
                                Intent intent = new Intent(INTENT_BLE_READ_NOT_ENCRYPTED_CHARACTERISTIC);
                                sendBroadcast(intent);
                            }
                        } else {
                            if (!mSecure) {
                                mBluetoothGatt.disconnect();
                                Intent intent = new Intent(INTENT_BLE_READ_ENCRYPTED_CHARACTERISTIC);
                                sendBroadcast(intent);
                            } else {
                                mBluetoothGatt.disconnect();
                                Intent intent = new Intent(INTENT_BLE_READ_FAIL_ENCRYPTED_CHARACTERISTIC);
                                sendBroadcast(intent);
                            }
                        }
                    }
                }, 1000);
            }
        }

        @Override
        public void onDescriptorRead(BluetoothGatt gatt, final BluetoothGattDescriptor descriptor, final int status) {
            if (DEBUG) {
                Log.d(TAG, "onDescriptorRead: status=" + status);
            }

            mTaskQueue.addTask(new Runnable() {
                @Override
                public void run() {
                    UUID uid = descriptor.getUuid();
                    if ((status == BluetoothGatt.GATT_SUCCESS)) {
                        if (uid.equals(DESCRIPTOR_ENCRYPTED_READ_UUID)) {
                            if (mSecure) {
                                mBluetoothGatt.disconnect();
                                if (Arrays.equals(BleEncryptedServerService.WRITE_VALUE.getBytes(), descriptor.getValue())) {
                                    Intent intent = new Intent(INTENT_BLE_READ_ENCRYPTED_DESCRIPTOR);
                                    sendBroadcast(intent);
                                } else {
                                    showMessage("Read data is not correct");
                                }
                            } else {
                                mBluetoothGatt.disconnect();
                                Intent intent = new Intent(INTENT_BLE_READ_NOT_ENCRYPTED_DESCRIPTOR);
                                sendBroadcast(intent);
                            }
                        }
                    } else {
                        if (!mSecure) {
                            mBluetoothGatt.disconnect();
                            Intent intent = new Intent(INTENT_BLE_READ_ENCRYPTED_DESCRIPTOR);
                            sendBroadcast(intent);
                        } else {
                            if (uid.equals(DESCRIPTOR_ENCRYPTED_READ_UUID)) {
                                mBluetoothGatt.disconnect();
                                Intent intent = new Intent(INTENT_BLE_READ_FAIL_ENCRYPTED_DESCRIPTOR);
                                sendBroadcast(intent);
                            }
                        }
                    }
                }
            }, 1000);
        }

        @Override
        public void onDescriptorWrite(BluetoothGatt gatt, final BluetoothGattDescriptor descriptor, final int status) {
            if (DEBUG) {
                Log.d(TAG, "onDescriptorWrite: status=" + status);
            }

            mTaskQueue.addTask(new Runnable() {
                @Override
                public void run() {
                    UUID uid = descriptor.getUuid();
                    if (uid.equals(DESCRIPTOR_ENCRYPTED_WRITE_UUID)) {
                        if ((status == BluetoothGatt.GATT_SUCCESS)) {
                            if (mSecure) {
                                mBluetoothGatt.disconnect();
                                if (Arrays.equals(WRITE_VALUE.getBytes(), descriptor.getValue())) {
                                    Intent intent = new Intent(INTENT_BLE_WRITE_ENCRYPTED_DESCRIPTOR);
                                    sendBroadcast(intent);
                                } else {
                                    showMessage("Written data is not correct");
                                }
                            }
                        } else {
                            if (!mSecure) {
                                mBluetoothGatt.disconnect();
                                Intent intent = new Intent(INTENT_BLE_WRITE_NOT_ENCRYPTED_DESCRIPTOR);
                                sendBroadcast(intent);
                            } else {
                                mBluetoothGatt.disconnect();
                                Intent intent = new Intent(INTENT_BLE_WRITE_FAIL_ENCRYPTED_DESCRIPTOR);
                                sendBroadcast(intent);
                            }
                        }
                    }
                }
            }, 1000);
        }
    };

    private final ScanCallback mScanCallback = new ScanCallback() {
        @Override
        public void onScanResult(int callbackType, final ScanResult result) {
            if (mBluetoothGatt== null) {
                mDevice = result.getDevice();
                int bond_state = mDevice.getBondState();
                if (mSecure && bond_state != BluetoothDevice.BOND_BONDED) {
                    mLastScanError = "This test is a test of Secure.\n Before running the test, please do the pairing.";
                    return;
                } else if (!mSecure && bond_state != BluetoothDevice.BOND_NONE) {
                    mLastScanError = "This test is a test of Insecure\n Before running the test, please release the pairing.";
                    return;
                }
                mLastScanError = null;
                stopScan();
                mBluetoothGatt = BleClientService.connectGatt(mDevice, mContext, false, mSecure, mGattCallbacks);
            }
        }
    };

    private void startScan() {
        if (DEBUG) Log.d(TAG, "startScan");
        List<ScanFilter> filter = Arrays.asList(new ScanFilter.Builder().setServiceUuid(
                new ParcelUuid(BleEncryptedServerService.ADV_SERVICE_UUID)).build());
        ScanSettings setting = new ScanSettings.Builder()
                .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY).build();
        mScanner.startScan(filter, setting, mScanCallback);

        mTaskQueue.addTask(new Runnable() {
            @Override
            public void run() {
                if (mLastScanError != null) {
                    stopScan();
                    Toast.makeText(BleEncryptedClientService.this, mLastScanError, Toast.LENGTH_LONG).show();
                    mLastScanError = null;
                }
            }
        }, 10000);
    }

    private void stopScan() {
        if (DEBUG) Log.d(TAG, "stopScan");
        if (mScanner != null) {
            mScanner.stopScan(mScanCallback);
        }
    }
}
