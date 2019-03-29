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
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.ParcelUuid;
import android.text.TextUtils;
import android.util.Log;
import android.widget.Toast;

import com.android.cts.verifier.R;

import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.UUID;

public class BleClientService extends Service {

    public static final boolean DEBUG = true;
    public static final String TAG = "BleClientService";

    // Android N (2016 July 15, currently) BluetoothGatt#disconnect() does not work correct.
    // (termination signal will not be sent)
    // This flag switches to turn Bluetooth off instead of BluetoothGatt#disconnect().
    // If true, test will turn Bluetooth off. Otherwise, will call BluetoothGatt#disconnect().
    public static final boolean DISCONNECT_BY_TURN_BT_OFF_ON = (Build.VERSION.SDK_INT > Build.VERSION_CODES.M);

    // for Version 1 test
//    private static final int TRANSPORT_MODE_FOR_SECURE_CONNECTION = BluetoothDevice.TRANSPORT_AUTO;
    // for Version 2 test
    private static final int TRANSPORT_MODE_FOR_SECURE_CONNECTION = BluetoothDevice.TRANSPORT_LE;

    public static final int COMMAND_CONNECT = 0;
    public static final int COMMAND_DISCONNECT = 1;
    public static final int COMMAND_DISCOVER_SERVICE = 2;
    public static final int COMMAND_READ_RSSI = 3;
    public static final int COMMAND_WRITE_CHARACTERISTIC = 4;
    public static final int COMMAND_WRITE_CHARACTERISTIC_BAD_RESP = 5;
    public static final int COMMAND_READ_CHARACTERISTIC = 6;
    public static final int COMMAND_WRITE_DESCRIPTOR = 7;
    public static final int COMMAND_READ_DESCRIPTOR = 8;
    public static final int COMMAND_SET_NOTIFICATION = 9;
    public static final int COMMAND_BEGIN_WRITE = 10;
    public static final int COMMAND_EXECUTE_WRITE = 11;
    public static final int COMMAND_ABORT_RELIABLE = 12;
    public static final int COMMAND_SCAN_START = 13;
    public static final int COMMAND_SCAN_STOP = 14;

    public static final String BLE_BLUETOOTH_MISMATCH_SECURE =
            "com.android.cts.verifier.bluetooth.BLE_BLUETOOTH_MISMATCH_SECURE";
    public static final String BLE_BLUETOOTH_MISMATCH_INSECURE =
            "com.android.cts.verifier.bluetooth.BLE_BLUETOOTH_MISMATCH_INSECURE";
    public static final String BLE_BLUETOOTH_DISABLED =
            "com.android.cts.verifier.bluetooth.BLE_BLUETOOTH_DISABLED";
    public static final String BLE_BLUETOOTH_CONNECTED =
            "com.android.cts.verifier.bluetooth.BLE_BLUETOOTH_CONNECTED";
    public static final String BLE_BLUETOOTH_DISCONNECTED =
            "com.android.cts.verifier.bluetooth.BLE_BLUETOOTH_DISCONNECTED";
    public static final String BLE_SERVICES_DISCOVERED =
            "com.android.cts.verifier.bluetooth.BLE_SERVICES_DISCOVERED";
    public static final String BLE_MTU_CHANGED_23BYTES =
            "com.android.cts.verifier.bluetooth.BLE_MTU_CHANGED_23BYTES";
    public static final String BLE_MTU_CHANGED_512BYTES =
            "com.android.cts.verifier.bluetooth.BLE_MTU_CHANGED_512BYTES";
    public static final String BLE_CHARACTERISTIC_READ =
            "com.android.cts.verifier.bluetooth.BLE_CHARACTERISTIC_READ";
    public static final String BLE_CHARACTERISTIC_WRITE =
            "com.android.cts.verifier.bluetooth.BLE_CHARACTERISTIC_WRITE";
    public static final String BLE_CHARACTERISTIC_CHANGED =
            "com.android.cts.verifier.bluetooth.BLE_CHARACTERISTIC_CHANGED";
    public static final String BLE_CHARACTERISTIC_INDICATED =
            "com.android.cts.verifier.bluetooth.BLE_CHARACTERISTIC_INDICATED";
    public static final String BLE_DESCRIPTOR_READ =
            "com.android.cts.verifier.bluetooth.BLE_DESCRIPTOR_READ";
    public static final String BLE_DESCRIPTOR_WRITE =
            "com.android.cts.verifier.bluetooth.BLE_DESCRIPTOR_WRITE";
    public static final String BLE_RELIABLE_WRITE_COMPLETED =
            "com.android.cts.verifier.bluetooth.BLE_RELIABLE_WRITE_COMPLETED";
    public static final String BLE_RELIABLE_WRITE_BAD_RESP_COMPLETED =
            "com.android.cts.verifier.bluetooth.BLE_RELIABLE_WRITE_BAD_RESP_COMPLETED";
    public static final String BLE_READ_REMOTE_RSSI =
            "com.android.cts.verifier.bluetooth.BLE_READ_REMOTE_RSSI";
    public static final String BLE_CHARACTERISTIC_READ_NOPERMISSION =
            "com.android.cts.verifier.bluetooth.BLE_CHARACTERISTIC_READ_NOPERMISSION";
    public static final String BLE_CHARACTERISTIC_WRITE_NOPERMISSION =
            "com.android.cts.verifier.bluetooth.BLE_CHARACTERISTIC_WRITE_NOPERMISSION";
    public static final String BLE_DESCRIPTOR_READ_NOPERMISSION =
            "com.android.cts.verifier.bluetooth.BLE_DESCRIPTOR_READ_NOPERMISSION";
    public static final String BLE_DESCRIPTOR_WRITE_NOPERMISSION =
            "com.android.cts.verifier.bluetooth.BLE_DESCRIPTOR_WRITE_NOPERMISSION";
    public static final String BLE_CHARACTERISTIC_READ_NEED_ENCRYPTED =
            "com.android.cts.verifier.bluetooth.BLE_CHARACTERISTIC_READ_NEED_ENCRYPTED";
    public static final String BLE_CHARACTERISTIC_WRITE_NEED_ENCRYPTED =
            "com.android.cts.verifier.bluetooth.BLE_CHARACTERISTIC_WRITE_NEED_ENCRYPTED";
    public static final String BLE_DESCRIPTOR_READ_NEED_ENCRYPTED =
            "com.android.cts.verifier.bluetooth.BLE_DESCRIPTOR_READ_NEED_ENCRYPTED";
    public static final String BLE_DESCRIPTOR_WRITE_NEED_ENCRYPTED =
            "com.android.cts.verifier.bluetooth.BLE_DESCRIPTOR_WRITE_NEED_ENCRYPTED";
    public static final String BLE_CLIENT_ERROR =
            "com.android.cts.verifier.bluetooth.BLE_CLIENT_ERROR";

    public static final String EXTRA_COMMAND =
            "com.android.cts.verifier.bluetooth.EXTRA_COMMAND";
    public static final String EXTRA_WRITE_VALUE =
            "com.android.cts.verifier.bluetooth.EXTRA_WRITE_VALUE";
    public static final String EXTRA_BOOL =
            "com.android.cts.verifier.bluetooth.EXTRA_BOOL";


    // Literal for Client Action
    public static final String BLE_CLIENT_ACTION_CLIENT_CONNECT =
            "com.android.cts.verifier.bluetooth.BLE_CLIENT_ACTION_CLIENT_CONNECT";
    public static final String BLE_CLIENT_ACTION_CLIENT_CONNECT_SECURE =
            "com.android.cts.verifier.bluetooth.BLE_CLIENT_ACTION_CLIENT_CONNECT_SECURE";
    public static final String BLE_CLIENT_ACTION_BLE_DISVOCER_SERVICE =
            "com.android.cts.verifier.bluetooth.BLE_CLIENT_ACTION_BLE_DISVOCER_SERVICE";
    public static final String BLE_CLIENT_ACTION_REQUEST_MTU_23 =
            "com.android.cts.verifier.bluetooth.BLE_CLIENT_ACTION_REQUEST_MTU_23";
    public static final String BLE_CLIENT_ACTION_REQUEST_MTU_512 =
            "com.android.cts.verifier.bluetooth.BLE_CLIENT_ACTION_REQUEST_MTU_512";
    public static final String BLE_CLIENT_ACTION_READ_CHARACTERISTIC =
            "com.android.cts.verifier.bluetooth.BLE_CLIENT_ACTION_READ_CHARACTERISTIC";
    public static final String BLE_CLIENT_ACTION_WRITE_CHARACTERISTIC =
            "com.android.cts.verifier.bluetooth.BLE_CLIENT_ACTION_WRITE_CHARACTERISTIC";
    public static final String BLE_CLIENT_ACTION_RELIABLE_WRITE =
            "com.android.cts.verifier.bluetooth.BLE_CLIENT_ACTION_RELIABLE_WRITE";
    public static final String BLE_CLIENT_ACTION_RELIABLE_WRITE_BAD_RESP =
            "com.android.cts.verifier.bluetooth.BLE_CLIENT_ACTION_RELIABLE_WRITE_BAD_RESP";
    public static final String BLE_CLIENT_ACTION_NOTIFY_CHARACTERISTIC =
            "com.android.cts.verifier.bluetooth.BLE_CLIENT_ACTION_NOTIFY_CHARACTERISTIC";
    public static final String BLE_CLIENT_ACTION_INDICATE_CHARACTERISTIC =
            "com.android.cts.verifier.bluetooth.BLE_CLIENT_ACTION_INDICATE_CHARACTERISTIC";
    public static final String BLE_CLIENT_ACTION_READ_DESCRIPTOR =
            "com.android.cts.verifier.bluetooth.BLE_CLIENT_ACTION_READ_DESCRIPTOR";
    public static final String BLE_CLIENT_ACTION_WRITE_DESCRIPTOR =
            "com.android.cts.verifier.bluetooth.BLE_CLIENT_ACTION_WRITE_DESCRIPTOR";
    public static final String BLE_CLIENT_ACTION_READ_RSSI =
            "com.android.cts.verifier.bluetooth.BLE_CLIENT_ACTION_READ_RSSI";
    public static final String BLE_CLIENT_ACTION_CLIENT_DISCONNECT =
            "com.android.cts.verifier.bluetooth.BLE_CLIENT_ACTION_CLIENT_DISCONNECT";
    public static final String BLE_CLIENT_ACTION_READ_CHARACTERISTIC_NO_PERMISSION =
            "com.android.cts.verifier.bluetooth.BLE_CLIENT_ACTION_READ_CHARACTERISTIC_NO_PERMISSION";
    public static final String BLE_CLIENT_ACTION_WRITE_CHARACTERISTIC_NO_PERMISSION =
            "com.android.cts.verifier.bluetooth.BLE_CLIENT_ACTION_WRITE_CHARACTERISTIC_NO_PERMISSION";
    public static final String BLE_CLIENT_ACTION_READ_DESCRIPTOR_NO_PERMISSION =
            "com.android.cts.verifier.bluetooth.BLE_CLIENT_ACTION_READ_DESCRIPTOR_NO_PERMISSION";
    public static final String BLE_CLIENT_ACTION_WRITE_DESCRIPTOR_NO_PERMISSION =
            "com.android.cts.verifier.bluetooth.BLE_CLIENT_ACTION_WRITE_DESCRIPTOR_NO_PERMISSION";
    public static final String BLE_CLIENT_ACTION_READ_AUTHENTICATED_CHARACTERISTIC =
            "com.android.cts.verifier.bluetooth.BLE_CLIENT_ACTION_READ_AUTHENTICATED_CHARACTERISTIC";
    public static final String BLE_CLIENT_ACTION_WRITE_AUTHENTICATED_CHARACTERISTIC =
            "com.android.cts.verifier.bluetooth.BLE_CLIENT_ACTION_WRITE_AUTHENTICATED_CHARACTERISTIC";
    public static final String BLE_CLIENT_ACTION_READ_AUTHENTICATED_DESCRIPTOR =
            "com.android.cts.verifier.bluetooth.BLE_CLIENT_ACTION_READ_AUTHENTICATED_DESCRIPTOR";
    public static final String BLE_CLIENT_ACTION_WRITE_AUTHENTICATED_DESCRIPTOR =
            "com.android.cts.verifier.bluetooth.BLE_CLIENT_ACTION_WRITE_AUTHENTICATED_DESCRIPTOR";

    public static final String EXTRA_CHARACTERISTIC_VALUE =
            "com.android.cts.verifier.bluetooth.EXTRA_CHARACTERISTIC_VALUE";
    public static final String EXTRA_DESCRIPTOR_VALUE =
            "com.android.cts.verifier.bluetooth.EXTRA_DESCRIPTOR_VALUE";
    public static final String EXTRA_RSSI_VALUE =
            "com.android.cts.verifier.bluetooth.EXTRA_RSSI_VALUE";
    public static final String EXTRA_ERROR_MESSAGE =
            "com.android.cts.verifier.bluetooth.EXTRA_ERROR_MESSAGE";

    public static final String WRITE_VALUE_512BYTES_FOR_MTU = createTestData(512);
    public static final String WRITE_VALUE_507BYTES_FOR_RELIABLE_WRITE = createTestData(507);

    private static final UUID SERVICE_UUID =
            UUID.fromString("00009999-0000-1000-8000-00805f9b34fb");
    private static final UUID CHARACTERISTIC_UUID =
            UUID.fromString("00009998-0000-1000-8000-00805f9b34fb");
    private static final UUID CHARACTERISTIC_RESULT_UUID =
            UUID.fromString("00009974-0000-1000-8000-00805f9b34fb");
    private static final UUID UPDATE_CHARACTERISTIC_UUID =
            UUID.fromString("00009997-0000-1000-8000-00805f9b34fb");
    private static final UUID DESCRIPTOR_UUID =
            UUID.fromString("00009996-0000-1000-8000-00805f9b34fb");

    private static final UUID SERVICE_UUID_ADDITIONAL =
            UUID.fromString("00009995-0000-1000-8000-00805f9b34fb");

    // Literal for registration permission of Characteristic
    private static final UUID CHARACTERISTIC_NO_READ_UUID =
            UUID.fromString("00009984-0000-1000-8000-00805f9b34fb");
    private static final UUID CHARACTERISTIC_NO_WRITE_UUID =
            UUID.fromString("00009983-0000-1000-8000-00805f9b34fb");
    private static final UUID CHARACTERISTIC_NEED_ENCRYPTED_READ_UUID =
            UUID.fromString("00009982-0000-1000-8000-00805f9b34fb");
    private static final UUID CHARACTERISTIC_NEED_ENCRYPTED_WRITE_UUID =
            UUID.fromString("00009981-0000-1000-8000-00805f9b34fb");

    // Literal for registration permission of Descriptor
    private static final UUID DESCRIPTOR_NO_READ_UUID =
            UUID.fromString("00009973-0000-1000-8000-00805f9b34fb");
    private static final UUID DESCRIPTOR_NO_WRITE_UUID =
            UUID.fromString("00009972-0000-1000-8000-00805f9b34fb");
    private static final UUID DESCRIPTOR_NEED_ENCRYPTED_READ_UUID =
            UUID.fromString("00009969-0000-1000-8000-00805f9b34fb");
    private static final UUID DESCRIPTOR_NEED_ENCRYPTED_WRITE_UUID =
            UUID.fromString("00009968-0000-1000-8000-00805f9b34fb");

    //  Literal for registration upper limit confirmation of Characteristic
    private static final UUID UPDATE_CHARACTERISTIC_UUID_1 =
            UUID.fromString("00009989-0000-1000-8000-00805f9b34fb");
    private static final UUID UPDATE_CHARACTERISTIC_UUID_2 =
            UUID.fromString("00009988-0000-1000-8000-00805f9b34fb");
    private static final UUID UPDATE_CHARACTERISTIC_UUID_3 =
            UUID.fromString("00009987-0000-1000-8000-00805f9b34fb");
    private static final UUID UPDATE_CHARACTERISTIC_UUID_4 =
            UUID.fromString("00009986-0000-1000-8000-00805f9b34fb");
    private static final UUID UPDATE_CHARACTERISTIC_UUID_5 =
            UUID.fromString("00009985-0000-1000-8000-00805f9b34fb");
    private static final UUID UPDATE_CHARACTERISTIC_UUID_6 =
            UUID.fromString("00009979-0000-1000-8000-00805f9b34fb");
    private static final UUID UPDATE_CHARACTERISTIC_UUID_7 =
            UUID.fromString("00009978-0000-1000-8000-00805f9b34fb");
    private static final UUID UPDATE_CHARACTERISTIC_UUID_8 =
            UUID.fromString("00009977-0000-1000-8000-00805f9b34fb");
    private static final UUID UPDATE_CHARACTERISTIC_UUID_9 =
            UUID.fromString("00009976-0000-1000-8000-00805f9b34fb");
    private static final UUID UPDATE_CHARACTERISTIC_UUID_10 =
            UUID.fromString("00009975-0000-1000-8000-00805f9b34fb");
    private static final UUID UPDATE_CHARACTERISTIC_UUID_11 =
            UUID.fromString("00009959-0000-1000-8000-00805f9b34fb");
    private static final UUID UPDATE_CHARACTERISTIC_UUID_12 =
            UUID.fromString("00009958-0000-1000-8000-00805f9b34fb");
    private static final UUID UPDATE_CHARACTERISTIC_UUID_13 =
            UUID.fromString("00009957-0000-1000-8000-00805f9b34fb");
    private static final UUID UPDATE_CHARACTERISTIC_UUID_14 =
            UUID.fromString("00009956-0000-1000-8000-00805f9b34fb");
    private static final UUID UPDATE_CHARACTERISTIC_UUID_15 =
            UUID.fromString("00009955-0000-1000-8000-00805f9b34fb");

    private static final UUID UPDATE_DESCRIPTOR_UUID =
            UUID.fromString("00002902-0000-1000-8000-00805f9b34fb");

    private static final UUID INDICATE_CHARACTERISTIC_UUID =
            UUID.fromString("00009971-0000-1000-8000-00805f9b34fb");

    public static final String WRITE_VALUE = "CLIENT_TEST";
    private static final String NOTIFY_VALUE = "NOTIFY_TEST";
    public static final String WRITE_VALUE_BAD_RESP = "BAD_RESP_TEST";

    // current test category
    private String mCurrentAction;

    private BluetoothManager mBluetoothManager;
    private BluetoothAdapter mBluetoothAdapter;
    private BluetoothDevice mDevice;
    private BluetoothGatt mBluetoothGatt;
    private BluetoothLeScanner mScanner;
    private Handler mHandler;
    private Context mContext;
    private boolean mSecure;
    private int mNotifyCount;
    private boolean mValidityService;
    private ReliableWriteState mExecReliableWrite;
    private byte[] mBuffer;

    // Handler for communicating task with peer.
    private TestTaskQueue mTaskQueue;

    // Lock for synchronization during notification request test.
    private final Object mRequestNotificationLock = new Object();

    private enum ReliableWriteState {
        RELIABLE_WRITE_NONE,
        RELIABLE_WRITE_WRITE_1ST_DATA,
        RELIABLE_WRITE_WRITE_2ND_DATA,
        RELIABLE_WRITE_EXECUTE,
        RELIABLE_WRITE_BAD_RESP
    }

    @Override
    public void onCreate() {
        super.onCreate();

        registerReceiver(mBondStatusReceiver, new IntentFilter(BluetoothDevice.ACTION_BOND_STATE_CHANGED));

        mBluetoothManager = (BluetoothManager) getSystemService(Context.BLUETOOTH_SERVICE);
        mBluetoothAdapter = mBluetoothManager.getAdapter();
        mScanner = mBluetoothAdapter.getBluetoothLeScanner();
        mHandler = new Handler();
        mContext = this;
        mNotifyCount = 0;

        mTaskQueue = new TestTaskQueue(getClass().getName() + "_taskHandlerThread");
    }

    @Override
    public int onStartCommand(final Intent intent, int flags, int startId) {
        if (!mBluetoothAdapter.isEnabled()) {
            notifyBluetoothDisabled();
        } else {
            mTaskQueue.addTask(new Runnable() {
                @Override
                public void run() {
                    onTestFinish(intent.getAction());
                }
            }, 1500);
        }
        return START_NOT_STICKY;
    }

    private void onTestFinish(String action) {
        mCurrentAction = action;
        if (mCurrentAction != null) {
            switch (mCurrentAction) {
                case BLE_CLIENT_ACTION_CLIENT_CONNECT_SECURE:
                    mSecure = true;
                    mExecReliableWrite = ReliableWriteState.RELIABLE_WRITE_NONE;
                    startScan();
                    break;
                case BLE_CLIENT_ACTION_CLIENT_CONNECT:
                    mExecReliableWrite = ReliableWriteState.RELIABLE_WRITE_NONE;
                    startScan();
                    break;
                case BLE_CLIENT_ACTION_BLE_DISVOCER_SERVICE:
                    if (mBluetoothGatt != null && mBleState == BluetoothProfile.STATE_CONNECTED) {
                        mBluetoothGatt.discoverServices();
                    } else {
                        showMessage("Bluetooth LE not cnnected.");
                    }
                    break;
                case BLE_CLIENT_ACTION_REQUEST_MTU_23:
                case BLE_CLIENT_ACTION_REQUEST_MTU_512: // fall through
                    requestMtu();
                    break;
                case BLE_CLIENT_ACTION_READ_CHARACTERISTIC:
                    readCharacteristic(CHARACTERISTIC_UUID);
                    break;
                case BLE_CLIENT_ACTION_WRITE_CHARACTERISTIC:
                    writeCharacteristic(CHARACTERISTIC_UUID, WRITE_VALUE);
                    break;
                case BLE_CLIENT_ACTION_RELIABLE_WRITE:
                case BLE_CLIENT_ACTION_RELIABLE_WRITE_BAD_RESP: // fall through
                    mTaskQueue.addTask(new Runnable() {
                        @Override
                        public void run() {
                            reliableWrite();
                        }
                    });
                break;
                case BLE_CLIENT_ACTION_INDICATE_CHARACTERISTIC:
                    setNotification(INDICATE_CHARACTERISTIC_UUID, true);
                    break;
                case BLE_CLIENT_ACTION_NOTIFY_CHARACTERISTIC:
                    // Registered the notify to characteristics in the service
                    mTaskQueue.addTask(new Runnable() {
                        @Override
                        public void run() {
                            mNotifyCount = 16;
                            setNotification(UPDATE_CHARACTERISTIC_UUID, true);
                            waitForDisableNotificationCompletion();
                            setNotification(SERVICE_UUID_ADDITIONAL, UPDATE_CHARACTERISTIC_UUID_1, true);
                            waitForDisableNotificationCompletion();
                            setNotification(SERVICE_UUID_ADDITIONAL, UPDATE_CHARACTERISTIC_UUID_2, true);
                            waitForDisableNotificationCompletion();
                            setNotification(SERVICE_UUID_ADDITIONAL, UPDATE_CHARACTERISTIC_UUID_3, true);
                            waitForDisableNotificationCompletion();
                            setNotification(SERVICE_UUID_ADDITIONAL, UPDATE_CHARACTERISTIC_UUID_4, true);
                            waitForDisableNotificationCompletion();
                            setNotification(SERVICE_UUID_ADDITIONAL, UPDATE_CHARACTERISTIC_UUID_5, true);
                            waitForDisableNotificationCompletion();
                            setNotification(UPDATE_CHARACTERISTIC_UUID_6, true);
                            waitForDisableNotificationCompletion();
                            setNotification(UPDATE_CHARACTERISTIC_UUID_7, true);
                            waitForDisableNotificationCompletion();
                            setNotification(UPDATE_CHARACTERISTIC_UUID_8, true);
                            waitForDisableNotificationCompletion();
                            setNotification(UPDATE_CHARACTERISTIC_UUID_9, true);
                            waitForDisableNotificationCompletion();
                            setNotification(UPDATE_CHARACTERISTIC_UUID_10, true);
                            waitForDisableNotificationCompletion();
                            setNotification(SERVICE_UUID_ADDITIONAL, UPDATE_CHARACTERISTIC_UUID_11, true);
                            waitForDisableNotificationCompletion();
                            setNotification(SERVICE_UUID_ADDITIONAL, UPDATE_CHARACTERISTIC_UUID_12, true);
                            waitForDisableNotificationCompletion();
                            setNotification(SERVICE_UUID_ADDITIONAL, UPDATE_CHARACTERISTIC_UUID_13, true);
                            waitForDisableNotificationCompletion();
                            setNotification(SERVICE_UUID_ADDITIONAL, UPDATE_CHARACTERISTIC_UUID_14, true);
                            waitForDisableNotificationCompletion();
                            setNotification(SERVICE_UUID_ADDITIONAL, UPDATE_CHARACTERISTIC_UUID_15, true);
                            waitForDisableNotificationCompletion();
                        }
                    });
                break;
                case BLE_CLIENT_ACTION_READ_DESCRIPTOR:
                    readDescriptor(DESCRIPTOR_UUID);
                    break;
                case BLE_CLIENT_ACTION_WRITE_DESCRIPTOR:
                    writeDescriptor(DESCRIPTOR_UUID, WRITE_VALUE);
                    break;
                case BLE_CLIENT_ACTION_READ_RSSI:
                    if (mBluetoothGatt != null) {
                        mBluetoothGatt.readRemoteRssi();
                    }
                    break;
                case BLE_CLIENT_ACTION_CLIENT_DISCONNECT:
                    if (mBluetoothGatt != null) {
                        mBluetoothGatt.disconnect();
                    }
                    break;
                case BLE_CLIENT_ACTION_READ_CHARACTERISTIC_NO_PERMISSION:
                    readCharacteristic(CHARACTERISTIC_NO_READ_UUID);
                    break;
                case BLE_CLIENT_ACTION_WRITE_CHARACTERISTIC_NO_PERMISSION:
                    writeCharacteristic(CHARACTERISTIC_NO_WRITE_UUID, WRITE_VALUE);
                    break;
                case BLE_CLIENT_ACTION_READ_DESCRIPTOR_NO_PERMISSION:
                    readDescriptor(DESCRIPTOR_NO_READ_UUID);
                    break;
                case BLE_CLIENT_ACTION_WRITE_DESCRIPTOR_NO_PERMISSION:
                    writeDescriptor(DESCRIPTOR_NO_WRITE_UUID, WRITE_VALUE);
                    break;
                case BLE_CLIENT_ACTION_READ_AUTHENTICATED_CHARACTERISTIC:
                    readCharacteristic(CHARACTERISTIC_NEED_ENCRYPTED_READ_UUID);
                    break;
                case BLE_CLIENT_ACTION_WRITE_AUTHENTICATED_CHARACTERISTIC:
                    writeCharacteristic(CHARACTERISTIC_NEED_ENCRYPTED_WRITE_UUID, WRITE_VALUE);
                    break;
                case BLE_CLIENT_ACTION_READ_AUTHENTICATED_DESCRIPTOR:
                    readDescriptor(CHARACTERISTIC_RESULT_UUID, DESCRIPTOR_NEED_ENCRYPTED_READ_UUID);
                    break;
                case BLE_CLIENT_ACTION_WRITE_AUTHENTICATED_DESCRIPTOR:
                    writeDescriptor(CHARACTERISTIC_RESULT_UUID, DESCRIPTOR_NEED_ENCRYPTED_WRITE_UUID, WRITE_VALUE);
                    break;
            }
        }
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (mBluetoothGatt != null) {
            mBluetoothGatt.disconnect();
            mBluetoothGatt.close();
            mBluetoothGatt = null;
        }
        stopScan();
        unregisterReceiver(mBondStatusReceiver);

        mTaskQueue.quit();
    }

    public static BluetoothGatt connectGatt(BluetoothDevice device, Context context, boolean autoConnect, boolean isSecure, BluetoothGattCallback callback) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if (isSecure) {
                if (TRANSPORT_MODE_FOR_SECURE_CONNECTION == BluetoothDevice.TRANSPORT_AUTO) {
                    Toast.makeText(context, "connectGatt(transport=AUTO)", Toast.LENGTH_SHORT).show();
                } else {
                    Toast.makeText(context, "connectGatt(transport=LE)", Toast.LENGTH_SHORT).show();
                }
                return device.connectGatt(context, autoConnect, callback, TRANSPORT_MODE_FOR_SECURE_CONNECTION);
            } else {
                Toast.makeText(context, "connectGatt(transport=LE)", Toast.LENGTH_SHORT).show();
                return device.connectGatt(context, autoConnect, callback, BluetoothDevice.TRANSPORT_LE);
            }
        } else {
            Toast.makeText(context, "connectGatt", Toast.LENGTH_SHORT).show();
            return device.connectGatt(context, autoConnect, callback);
        }
    }

    private void requestMtu() {
        if (mBluetoothGatt != null) {
            // MTU request test does not work on Android 6.0 in secure mode.
            // (BluetoothDevice#createBond() does not work on Android 6.0.
            //  So devices can't establish Bluetooth LE pairing.)
            boolean mtuTestExecutable = true;
            if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.M) {
                mtuTestExecutable = !mSecure;
            }

            if (mtuTestExecutable) {
                int mtu = 0;
                if (BLE_CLIENT_ACTION_REQUEST_MTU_23.equals(mCurrentAction)) {
                    mtu = 23;
                } else if (BLE_CLIENT_ACTION_REQUEST_MTU_512.equals(mCurrentAction)) {
                    mtu = 512;
                } else {
                    throw new IllegalStateException("unexpected action: " + mCurrentAction);
                }
                mBluetoothGatt.requestMtu(mtu);
            } else {
                showMessage("Skip MTU test.");
                notifyMtuChanged();
            }
        }
    }

    private void writeCharacteristic(BluetoothGattCharacteristic characteristic, String writeValue) {
        if (characteristic != null) {
            characteristic.setValue(writeValue);
            mBluetoothGatt.writeCharacteristic(characteristic);
        }
    }

    private void writeCharacteristic(UUID uid, String writeValue) {
        BluetoothGattCharacteristic characteristic = getCharacteristic(uid);
        if (characteristic != null){
            writeCharacteristic(characteristic, writeValue);
        }
    }

    private void readCharacteristic(UUID uuid) {
        BluetoothGattCharacteristic characteristic = getCharacteristic(uuid);
        if (characteristic != null) {
            mBluetoothGatt.readCharacteristic(characteristic);
        }
    }

    private void writeDescriptor(UUID uid, String writeValue) {
        BluetoothGattDescriptor descriptor = getDescriptor(uid);
        if (descriptor != null) {
            descriptor.setValue(writeValue.getBytes());
            mBluetoothGatt.writeDescriptor(descriptor);
        }
    }

    private void readDescriptor(UUID uuid) {
        BluetoothGattDescriptor descriptor = getDescriptor(uuid);
        if (descriptor != null) {
            mBluetoothGatt.readDescriptor(descriptor);
        }
    }

    private void writeDescriptor(UUID cuid, UUID duid, String writeValue) {
        BluetoothGattDescriptor descriptor = getDescriptor(cuid, duid);
        if (descriptor != null) {
            descriptor.setValue(writeValue.getBytes());
            mBluetoothGatt.writeDescriptor(descriptor);
        }
    }

    private void readDescriptor(UUID cuid, UUID duid) {
        BluetoothGattDescriptor descriptor = getDescriptor(cuid, duid);
        if (descriptor != null) {
            mBluetoothGatt.readDescriptor(descriptor);
        }
    }

    private void notifyDisableNotificationCompletion() {
        synchronized (mRequestNotificationLock) {
            mRequestNotificationLock.notify();
        }
    }

    private void waitForDisableNotificationCompletion() {
        synchronized (mRequestNotificationLock) {
            try {
                mRequestNotificationLock.wait();
            } catch (InterruptedException e) {
                Log.e(TAG, "Error in waitForDisableNotificationCompletion" + e);
            }
        }
    }

    private void setNotification(BluetoothGattCharacteristic characteristic, boolean enable) {
        if (characteristic != null) {
            mBluetoothGatt.setCharacteristicNotification(characteristic, enable);
            BluetoothGattDescriptor descriptor = characteristic.getDescriptor(UPDATE_DESCRIPTOR_UUID);
            if (enable) {
                if (characteristic.getUuid().equals(INDICATE_CHARACTERISTIC_UUID)) {
                    descriptor.setValue(BluetoothGattDescriptor.ENABLE_INDICATION_VALUE);
                } else {
                    descriptor.setValue(BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE);
                }
            } else {
                descriptor.setValue(BluetoothGattDescriptor.DISABLE_NOTIFICATION_VALUE);
            }
            mBluetoothGatt.writeDescriptor(descriptor);
        }
    }

    private void setNotification(UUID serviceUid, UUID characteristicUid,  boolean enable) {
        BluetoothGattCharacteristic characteristic = getCharacteristic(serviceUid, characteristicUid);
        if (characteristic != null) {
            setNotification(characteristic, enable);
        }
    }

    private void setNotification(UUID uid, boolean enable) {
        BluetoothGattCharacteristic characteristic = getCharacteristic(uid);
        if (characteristic != null) {
           setNotification(characteristic, enable);
        }
    }

    private void notifyError(String message) {
        showMessage(message);
        Log.i(TAG, message);

        Intent intent = new Intent(BLE_CLIENT_ERROR);
        sendBroadcast(intent);
    }

    private void notifyMismatchSecure() {
        Intent intent = new Intent(BLE_BLUETOOTH_MISMATCH_SECURE);
        sendBroadcast(intent);
    }

    private void notifyMismatchInsecure() {
        Intent intent = new Intent(BLE_BLUETOOTH_MISMATCH_INSECURE);
        sendBroadcast(intent);
    }

    private void notifyBluetoothDisabled() {
        Intent intent = new Intent(BLE_BLUETOOTH_DISABLED);
        sendBroadcast(intent);
    }

    private void notifyConnected() {
        showMessage("Bluetooth LE connected");
        Intent intent = new Intent(BLE_BLUETOOTH_CONNECTED);
        sendBroadcast(intent);
    }

    private void notifyDisconnected() {
        showMessage("Bluetooth LE disconnected");
        Intent intent = new Intent(BLE_BLUETOOTH_DISCONNECTED);
        sendBroadcast(intent);
    }

    private void notifyServicesDiscovered() {
        showMessage("Service discovered");
        Intent intent = new Intent(BLE_SERVICES_DISCOVERED);
        sendBroadcast(intent);
    }

    private void notifyMtuChanged() {
        Intent intent;
        if (BLE_CLIENT_ACTION_REQUEST_MTU_23.equals(mCurrentAction)) {
            intent = new Intent(BLE_MTU_CHANGED_23BYTES);
        } else if (BLE_CLIENT_ACTION_REQUEST_MTU_512.equals(mCurrentAction)) {
            intent = new Intent(BLE_MTU_CHANGED_512BYTES);
        } else {
            throw new IllegalStateException("unexpected action: " + mCurrentAction);
        }
        sendBroadcast(intent);
    }

    private void notifyCharacteristicRead(String value) {
        showMessage("Characteristic read: " + value);
        Intent intent = new Intent(BLE_CHARACTERISTIC_READ);
        intent.putExtra(EXTRA_CHARACTERISTIC_VALUE, value);
        sendBroadcast(intent);
    }

    private void notifyCharacteristicWrite(String value) {
        showMessage("Characteristic write: " + value);
        Intent intent = new Intent(BLE_CHARACTERISTIC_WRITE);
        sendBroadcast(intent);
    }

    private void notifyCharacteristicReadNoPermission() {
        showMessage("Characteristic not read: No Permission");
        Intent intent = new Intent(BLE_CHARACTERISTIC_READ_NOPERMISSION);
        sendBroadcast(intent);
    }

    private void notifyCharacteristicWriteNoPermission(String value) {
        showMessage("Characteristic write: No Permission");
        Intent intent = new Intent(BLE_CHARACTERISTIC_WRITE_NOPERMISSION);
        sendBroadcast(intent);
    }

    private void notifyCharacteristicReadNeedEncrypted(String value) {
        showMessage("Characteristic read with encrypted: " + value);
        Intent intent = new Intent(BLE_CHARACTERISTIC_READ_NEED_ENCRYPTED);
        sendBroadcast(intent);
    }

    private void notifyCharacteristicWriteNeedEncrypted(String value) {
        showMessage("Characteristic write with encrypted: " + value);
        Intent intent = new Intent(BLE_CHARACTERISTIC_WRITE_NEED_ENCRYPTED);
        sendBroadcast(intent);
    }

    private void notifyCharacteristicChanged() {
        showMessage("Characteristic changed");
        Intent intent = new Intent(BLE_CHARACTERISTIC_CHANGED);
        sendBroadcast(intent);
    }

    private void notifyCharacteristicIndicated() {
        showMessage("Characteristic Indicated");
        Intent intent = new Intent(BLE_CHARACTERISTIC_INDICATED);
        sendBroadcast(intent);
    }

    private void notifyDescriptorRead(String value) {
        showMessage("Descriptor read: " + value);
        Intent intent = new Intent(BLE_DESCRIPTOR_READ);
        intent.putExtra(EXTRA_DESCRIPTOR_VALUE, value);
        sendBroadcast(intent);
    }

    private void notifyDescriptorWrite(String value) {
        showMessage("Descriptor write: " + value);
        Intent intent = new Intent(BLE_DESCRIPTOR_WRITE);
        sendBroadcast(intent);
    }

    private void notifyDescriptorReadNoPermission() {
        showMessage("Descriptor read: No Permission");
        Intent intent = new Intent(BLE_DESCRIPTOR_READ_NOPERMISSION);
        sendBroadcast(intent);
    }

    private void notifyDescriptorWriteNoPermission() {
        showMessage("Descriptor write: NoPermission");
        Intent intent = new Intent(BLE_DESCRIPTOR_WRITE_NOPERMISSION);
        sendBroadcast(intent);
    }

    private void notifyDescriptorReadNeedEncrypted(String value) {
        showMessage("Descriptor read with encrypted: " + value);
        Intent intent = new Intent(BLE_DESCRIPTOR_READ_NEED_ENCRYPTED);
        sendBroadcast(intent);
    }

    private void notifyDescriptorWriteNeedEncrypted(String value) {
        showMessage("Descriptor write with encrypted: " + value);
        Intent intent = new Intent(BLE_DESCRIPTOR_WRITE_NEED_ENCRYPTED);
        sendBroadcast(intent);
    }

    private void notifyReliableWriteCompleted() {
        showMessage("Reliable write compelte");
        Intent intent = new Intent(BLE_RELIABLE_WRITE_COMPLETED);
        sendBroadcast(intent);
    }

    private void notifyReliableWriteBadRespCompleted(String err) {
        showMessage("Reliable write(bad response) compelte");
        Intent intent = new Intent(BLE_RELIABLE_WRITE_BAD_RESP_COMPLETED);
        if (err != null) {
            intent.putExtra(EXTRA_ERROR_MESSAGE, err);
        }
        sendBroadcast(intent);
    }

    private void notifyReadRemoteRssi(int rssi) {
        showMessage("Remote rssi read: " + rssi);
        Intent intent = new Intent(BLE_READ_REMOTE_RSSI);
        intent.putExtra(EXTRA_RSSI_VALUE, rssi);
        sendBroadcast(intent);
    }

    private BluetoothGattService getService(UUID serverUid) {
        BluetoothGattService service = null;

        if (mBluetoothGatt != null) {
            service = mBluetoothGatt.getService(serverUid);
            if (service == null) {
                showMessage("Service not found");
            }
        }
        return service;
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

    private BluetoothGattCharacteristic getCharacteristic(UUID serverUid, UUID characteristicUid) {
        BluetoothGattCharacteristic characteristic = null;

        BluetoothGattService service = getService(serverUid);
        if (service != null) {
            characteristic = service.getCharacteristic(characteristicUid);
            if (characteristic == null) {
                showMessage("Characteristic not found");
            }
        }
        return characteristic;
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

    private BluetoothGattDescriptor getDescriptor(UUID cuid, UUID duid) {
        BluetoothGattDescriptor descriptor = null;

        BluetoothGattCharacteristic characteristic = getCharacteristic(cuid);
        if (characteristic != null) {
            descriptor = characteristic.getDescriptor(duid);
            if (descriptor == null) {
                showMessage("Descriptor not found");
            }
        }
        return descriptor;
    }

    private void showMessage(final String msg) {
        mHandler.post(new Runnable() {
            public void run() {
                Toast.makeText(BleClientService.this, msg, Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void sleep(int millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Log.e(TAG, "Error in thread sleep", e);
        }
    }

    private void reliableWrite() {
        // aborting test
        mBluetoothGatt.beginReliableWrite();
        sleep(1000);
        mBluetoothGatt.abortReliableWrite();

        // writing test
        sleep(2000);
        mBluetoothGatt.beginReliableWrite();
        sleep(1000);

        if (BLE_CLIENT_ACTION_RELIABLE_WRITE.equals(mCurrentAction)) {
            mExecReliableWrite = ReliableWriteState.RELIABLE_WRITE_WRITE_1ST_DATA;
            writeCharacteristic(CHARACTERISTIC_UUID, WRITE_VALUE_507BYTES_FOR_RELIABLE_WRITE);
        } else {
            mExecReliableWrite = ReliableWriteState.RELIABLE_WRITE_BAD_RESP;
            writeCharacteristic(CHARACTERISTIC_UUID, WRITE_VALUE_BAD_RESP);
        }
    }

    private int mBleState = BluetoothProfile.STATE_DISCONNECTED;
    private final BluetoothGattCallback mGattCallbacks = new BluetoothGattCallback() {
        @Override
        public void onConnectionStateChange(BluetoothGatt gatt, int status, int newState) {
            if (DEBUG) Log.d(TAG, "onConnectionStateChange: status= " + status + ", newState= " + newState);
            if (status == BluetoothGatt.GATT_SUCCESS) {
                if (newState == BluetoothProfile.STATE_CONNECTED) {
                    mBleState = newState;
                    int bond = gatt.getDevice().getBondState();
                    boolean bonded = false;
                    BluetoothDevice target = gatt.getDevice();
                    Set<BluetoothDevice> pairedDevices = mBluetoothAdapter.getBondedDevices();
                    if (pairedDevices.size() > 0) {
                        for (BluetoothDevice device : pairedDevices) {
                            if (device.getAddress().equals(target.getAddress())) {
                                bonded = true;
                                break;
                            }
                        }
                    }
                    if (mSecure && ((bond == BluetoothDevice.BOND_NONE) || !bonded)) {
                        // not pairing and execute Secure Test
                        mBluetoothGatt.disconnect();
                        notifyMismatchSecure();
                    } else if (!mSecure && ((bond != BluetoothDevice.BOND_NONE) || bonded)) {
                        // already pairing nad execute Insecure Test
                        mBluetoothGatt.disconnect();
                        notifyMismatchInsecure();
                    } else {
                        notifyConnected();
                    }
                } else if (status == BluetoothProfile.STATE_DISCONNECTED) {
                    mBleState = newState;
                    mSecure = false;
                    mBluetoothGatt.close();
                    notifyDisconnected();
                }
            } else {
                showMessage("Failed to connect: " + status + " , newState = " + newState);
                mBluetoothGatt.close();
                mBluetoothGatt = null;
            }
        }

        @Override
        public void onServicesDiscovered(BluetoothGatt gatt, int status) {
            if (DEBUG){
                Log.d(TAG, "onServiceDiscovered");
            }
            if ((status == BluetoothGatt.GATT_SUCCESS) && (mBluetoothGatt.getService(SERVICE_UUID) != null)) {
                notifyServicesDiscovered();
            }
        }

        @Override
        public void onMtuChanged(BluetoothGatt gatt, int mtu, int status) {
            super.onMtuChanged(gatt, mtu, status);
            if (DEBUG){
                Log.d(TAG, "onMtuChanged");
            }
            if (status == BluetoothGatt.GATT_SUCCESS) {
                // verify MTU size
                int requestedMtu;
                if (BLE_CLIENT_ACTION_REQUEST_MTU_23.equals(mCurrentAction)) {
                    requestedMtu = 23;
                } else if (BLE_CLIENT_ACTION_REQUEST_MTU_512.equals(mCurrentAction)) {
                    requestedMtu = 512;
                } else {
                    throw new IllegalStateException("unexpected action: " + mCurrentAction);
                }
                if (mtu != requestedMtu) {
                    String msg = String.format(getString(R.string.ble_mtu_mismatch_message),
                            requestedMtu, mtu);
                    showMessage(msg);
                }

                // test writing characteristic
                writeCharacteristic(CHARACTERISTIC_UUID, WRITE_VALUE_512BYTES_FOR_MTU);
            } else {
                notifyError("Failed to request mtu: " + status);
            }
        }

        @Override
        public void onCharacteristicWrite(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic, final int status) {
            String value = characteristic.getStringValue(0);
            final UUID uid = characteristic.getUuid();
            if (DEBUG) {
                Log.d(TAG, "onCharacteristicWrite: characteristic.val=" + value + " status=" + status);
            }

            if (BLE_CLIENT_ACTION_REQUEST_MTU_512.equals(mCurrentAction) ||
                    BLE_CLIENT_ACTION_REQUEST_MTU_23.equals(mCurrentAction)) {
                if (status == BluetoothGatt.GATT_SUCCESS) {
                    notifyMtuChanged();
                } else {
                    notifyError("Failed to write characteristic: " + status + " : " + uid);
                }
            } else {
                switch (mExecReliableWrite) {
                    case RELIABLE_WRITE_NONE:
                        if (status == BluetoothGatt.GATT_SUCCESS) {
                            if (characteristic.getUuid().equals(CHARACTERISTIC_NEED_ENCRYPTED_WRITE_UUID)) {
                                notifyCharacteristicWriteNeedEncrypted(value);
                            } else if (!characteristic.getUuid().equals(CHARACTERISTIC_RESULT_UUID)) {
                                // verify
                                if (Arrays.equals(BleClientService.WRITE_VALUE.getBytes(), characteristic.getValue())) {
                                    notifyCharacteristicWrite(value);
                                } else {
                                    notifyError("Written data is not correct");
                                }
                            }
                        } else if (status == BluetoothGatt.GATT_WRITE_NOT_PERMITTED) {
                            if (uid.equals(CHARACTERISTIC_NO_WRITE_UUID)) {
                                writeCharacteristic(getCharacteristic(CHARACTERISTIC_RESULT_UUID), BleServerService.WRITE_NO_PERMISSION);
                                notifyCharacteristicWriteNoPermission(value);
                            } else {
                                notifyError("Not Permission Write: " + status + " : " + uid);
                            }
                        } else if (status == BluetoothGatt.GATT_INSUFFICIENT_AUTHENTICATION) {
                            notifyError("Not Authentication Write: " + status + " : " + uid);
                        } else {
                            notifyError("Failed to write characteristic: " + status + " : " + uid);
                        }
                        break;
                    case RELIABLE_WRITE_WRITE_1ST_DATA:
                        // verify
                        if (WRITE_VALUE_507BYTES_FOR_RELIABLE_WRITE.equals(value)) {
                            // write next data
                            mExecReliableWrite = ReliableWriteState.RELIABLE_WRITE_WRITE_2ND_DATA;
                            writeCharacteristic(CHARACTERISTIC_UUID, WRITE_VALUE_507BYTES_FOR_RELIABLE_WRITE);
                        } else {
                            notifyError("Failed to write characteristic: " + status + " : " + uid);
                        }
                        break;
                    case RELIABLE_WRITE_WRITE_2ND_DATA:
                        // verify
                        if (WRITE_VALUE_507BYTES_FOR_RELIABLE_WRITE.equals(value)) {
                            // execute write
                            mTaskQueue.addTask(new Runnable() {
                                @Override
                                public void run() {
                                    mExecReliableWrite = ReliableWriteState.RELIABLE_WRITE_EXECUTE;
                                    if (!mBluetoothGatt.executeReliableWrite()) {
                                        notifyError("reliable write failed");
                                    }
                                }
                            }, 1000);
                        } else {
                            notifyError("Failed to write characteristic: " + status + " : " + uid);
                        }
                        break;
                    case RELIABLE_WRITE_BAD_RESP:
                        mExecReliableWrite = ReliableWriteState.RELIABLE_WRITE_NONE;
                        // verify response
                        //   Server sends empty response for this test.
                        //   Response must be empty.
                        String err = null;
                        if (!TextUtils.isEmpty(value)) {
                            err = "response is not empty";
                            showMessage(err);
                        }
                        // finish reliable write
                        final String errValue = err;
                        mTaskQueue.addTask(new Runnable() {
                            @Override
                            public void run() {
                                mBluetoothGatt.abortReliableWrite();
                                notifyReliableWriteBadRespCompleted(errValue);
                            }
                        }, 1000);
                        break;
                }
            }
        }

        @Override
        public void onCharacteristicRead(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic, int status) {
            UUID uid = characteristic.getUuid();
            if (DEBUG) {
                Log.d(TAG, "onCharacteristicRead: status=" + status);
            }
            if (status == BluetoothGatt.GATT_SUCCESS) {
                String value = characteristic.getStringValue(0);
                if (characteristic.getUuid().equals(CHARACTERISTIC_NEED_ENCRYPTED_READ_UUID)) {
                    notifyCharacteristicReadNeedEncrypted(value);
                } else {
                    // verify
                    if (BleServerService.WRITE_VALUE.equals(value)) {
                        notifyCharacteristicRead(value);
                    } else {
                        notifyError("Read data is not correct");
                    }
                }
            } else if (status == BluetoothGatt.GATT_READ_NOT_PERMITTED) {
                if (uid.equals(CHARACTERISTIC_NO_READ_UUID)) {
                    writeCharacteristic(getCharacteristic(CHARACTERISTIC_RESULT_UUID), BleServerService.READ_NO_PERMISSION);
                    notifyCharacteristicReadNoPermission();
                } else {
                    notifyError("Not Permission Read: " + status + " : " + uid);
                }
            } else if(status == BluetoothGatt.GATT_INSUFFICIENT_AUTHENTICATION) {
                notifyError("Not Authentication Read: " + status + " : " + uid);
            } else {
                notifyError("Failed to read characteristic: " + status + " : " + uid);
            }
        }

        @Override
        public void onDescriptorWrite(BluetoothGatt gatt, BluetoothGattDescriptor descriptor, int status) {
            if (DEBUG) {
                Log.d(TAG, "onDescriptorWrite");
            }
            UUID uid = descriptor.getUuid();
            if ((status == BluetoothGatt.GATT_SUCCESS)) {
                if (uid.equals(UPDATE_DESCRIPTOR_UUID)) {
                    Log.d(TAG, "write in update descriptor.");
                    if (descriptor.getValue() == BluetoothGattDescriptor.DISABLE_NOTIFICATION_VALUE) {
                        notifyDisableNotificationCompletion();
                    }
                } else if (uid.equals(DESCRIPTOR_UUID)) {
                    // verify
                    if (Arrays.equals(WRITE_VALUE.getBytes(), descriptor.getValue())) {
                        notifyDescriptorWrite(new String(descriptor.getValue()));
                    } else {
                        notifyError("Written data is not correct");
                    }
                } else if (uid.equals(DESCRIPTOR_NEED_ENCRYPTED_WRITE_UUID)) {
                    notifyDescriptorWriteNeedEncrypted(new String(descriptor.getValue()));
                }
            } else if (status == BluetoothGatt.GATT_WRITE_NOT_PERMITTED) {
                if (uid.equals(DESCRIPTOR_NO_WRITE_UUID)) {
                    writeCharacteristic(getCharacteristic(CHARACTERISTIC_RESULT_UUID), BleServerService.DESCRIPTOR_WRITE_NO_PERMISSION);
                    notifyDescriptorWriteNoPermission();
                } else {
                    notifyError("Not Permission Write: " + status + " : " + descriptor.getUuid());
                }
            } else {
                notifyError("Failed to write descriptor");
            }
        }

        @Override
        public void onDescriptorRead(BluetoothGatt gatt, BluetoothGattDescriptor descriptor, int status) {
            if (DEBUG) {
                Log.d(TAG, "onDescriptorRead");
            }

            UUID uid = descriptor.getUuid();
            if ((status == BluetoothGatt.GATT_SUCCESS)) {
                if ((uid != null) && (uid.equals(DESCRIPTOR_UUID))) {
                    // verify
                    if (Arrays.equals(BleServerService.WRITE_VALUE.getBytes(), descriptor.getValue())) {
                        notifyDescriptorRead(new String(descriptor.getValue()));
                    } else {
                        notifyError("Read data is not correct");
                    }
                } else if (uid.equals(DESCRIPTOR_NEED_ENCRYPTED_READ_UUID)) {
                    notifyDescriptorReadNeedEncrypted(new String(descriptor.getValue()));
                }
            } else if (status == BluetoothGatt.GATT_READ_NOT_PERMITTED) {
                if (uid.equals(DESCRIPTOR_NO_READ_UUID)) {
                    writeCharacteristic(getCharacteristic(CHARACTERISTIC_RESULT_UUID), BleServerService.DESCRIPTOR_READ_NO_PERMISSION);
                    notifyDescriptorReadNoPermission();
                } else {
                    notifyError("Not Permission Read: " + status + " : " + descriptor.getUuid());
                }
            } else {
                notifyError("Failed to read descriptor: " + status);
            }
        }

        @Override
        public void onCharacteristicChanged(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic) {
            UUID uid = characteristic.getUuid();
            if (DEBUG) {
                Log.d(TAG, "onCharacteristicChanged: " + uid);
            }
            if (uid != null) {
                if (uid.equals(INDICATE_CHARACTERISTIC_UUID)) {
                    setNotification(characteristic, false);
                    notifyCharacteristicIndicated();
                } else {
                    mNotifyCount--;
                    setNotification(characteristic, false);
                    if (mNotifyCount == 0) {
                        notifyCharacteristicChanged();
                    }
                }
            }
        }

        @Override
        public void onReliableWriteCompleted(BluetoothGatt gatt, int status) {
            if (DEBUG) {
                Log.d(TAG, "onReliableWriteComplete: " + status);
            }

            if (mExecReliableWrite != ReliableWriteState.RELIABLE_WRITE_NONE) {
                if (status == BluetoothGatt.GATT_SUCCESS) {
                    notifyReliableWriteCompleted();
                } else {
                    notifyError("Failed to complete reliable write: " + status);
                }
                mExecReliableWrite = ReliableWriteState.RELIABLE_WRITE_NONE;
            }
        }

        @Override
        public void onReadRemoteRssi(BluetoothGatt gatt, int rssi, int status) {
            if (DEBUG) {
                Log.d(TAG, "onReadRemoteRssi");
            }
            if (status == BluetoothGatt.GATT_SUCCESS) {
                notifyReadRemoteRssi(rssi);
            } else {
                notifyError("Failed to read remote rssi");
            }
        }
    };

    private final ScanCallback mScanCallback = new ScanCallback() {
        @Override
        public void onScanResult(int callbackType, ScanResult result) {
            if (mBluetoothGatt == null) {
                // verify the validity of the advertisement packet.
                mValidityService = false;
                List<ParcelUuid> uuids = result.getScanRecord().getServiceUuids();
                for (ParcelUuid uuid : uuids) {
                    if (uuid.getUuid().equals(BleServerService.ADV_SERVICE_UUID)) {
                        mValidityService = true;
                        break;
                    }
                }
                if (mValidityService) {
                    stopScan();

                    BluetoothDevice device = result.getDevice();
                    if (mSecure) {
                        if (device.getBondState() != BluetoothDevice.BOND_BONDED) {
                            if (!device.createBond()) {
                                notifyError("Failed to call create bond");
                            }
                        } else {
                            mBluetoothGatt = connectGatt(result.getDevice(), mContext, false, mSecure, mGattCallbacks);
                        }
                    } else {
                        mBluetoothGatt = connectGatt(result.getDevice(), mContext, false, mSecure, mGattCallbacks);
                    }
                } else {
                    notifyError("There is no validity to Advertise servie.");
                }
            }
        }
    };

    private void startScan() {
        if (DEBUG) Log.d(TAG, "startScan");
        List<ScanFilter> filter = Arrays.asList(new ScanFilter.Builder().setServiceUuid(
                new ParcelUuid(BleServerService.ADV_SERVICE_UUID)).build());
        ScanSettings setting = new ScanSettings.Builder()
                .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY).build();
        mScanner.startScan(filter, setting, mScanCallback);
    }

    private void stopScan() {
        if (DEBUG) Log.d(TAG, "stopScan");
        if (mScanner != null) {
            mScanner.stopScan(mScanCallback);
        }
    }

    private static String createTestData(int length) {
        StringBuilder builder = new StringBuilder();
        builder.append("REQUEST_MTU");
        int len = length - builder.length();
        for (int i = 0; i < len; ++i) {
            builder.append(""+(i%10));
        }
        return builder.toString();
    }

    private final BroadcastReceiver mBondStatusReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (intent.getAction().equals(BluetoothDevice.ACTION_BOND_STATE_CHANGED)) {
                BluetoothDevice device = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);
                int state = intent.getIntExtra(BluetoothDevice.EXTRA_BOND_STATE, BluetoothDevice.BOND_NONE);
                switch (state) {
                    case BluetoothDevice.BOND_BONDED:
                        mBluetoothGatt = connectGatt(device, mContext, false, mSecure, mGattCallbacks);
                        break;
                    case BluetoothDevice.BOND_NONE:
                        notifyError("Failed to create bond.");
                        break;
                    case BluetoothDevice.BOND_BONDING:
                    default:    // fall through
                        // wait for next state
                        break;
                }
            }
        }
    };
}
