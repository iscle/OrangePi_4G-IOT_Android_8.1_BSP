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
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.ParcelUuid;
import android.util.Log;
import android.widget.Toast;

import com.android.cts.verifier.R;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Set;
import java.util.Timer;
import java.util.UUID;

public class BleServerService extends Service {

    public static final boolean DEBUG = true;
    public static final String TAG = "BleServerService";

    public static final int COMMAND_ADD_SERVICE = 0;
    public static final int COMMAND_WRITE_CHARACTERISTIC = 1;
    public static final int COMMAND_WRITE_DESCRIPTOR = 2;

    public static final String BLE_BLUETOOTH_MISMATCH_SECURE =
            "com.android.cts.verifier.bluetooth.BLE_BLUETOOTH_MISMATCH_SECURE";
    public static final String BLE_BLUETOOTH_MISMATCH_INSECURE =
            "com.android.cts.verifier.bluetooth.BLE_BLUETOOTH_MISMATCH_INSECURE";
    public static final String BLE_BLUETOOTH_DISABLED =
            "com.android.cts.verifier.bluetooth.BLE_BLUETOOTH_DISABLED";
    public static final String BLE_ACTION_SERVER_SECURE =
            "com.android.cts.verifier.bluetooth.BLE_ACTION_SERVER_SECURE";
    public static final String BLE_ACTION_SERVER_NON_SECURE =
            "com.android.cts.verifier.bluetooth.BLE_ACTION_SERVER_NON_SECURE";


    public static final String BLE_SERVER_CONNECTED =
            "com.android.cts.verifier.bluetooth.BLE_SERVER_CONNECTED";
    public static final String BLE_SERVER_DISCONNECTED =
            "com.android.cts.verifier.bluetooth.BLE_SERVER_DISCONNECTED";
    public static final String BLE_SERVICE_ADDED =
            "com.android.cts.verifier.bluetooth.BLE_SERVICE_ADDED";
    public static final String BLE_MTU_REQUEST_23BYTES =
            "com.android.cts.verifier.bluetooth.BLE_MTU_REQUEST_23BYTES";
    public static final String BLE_MTU_REQUEST_512BYTES =
            "com.android.cts.verifier.bluetooth.BLE_MTU_REQUEST_512BYTES";
    public static final String BLE_CHARACTERISTIC_READ_REQUEST =
            "com.android.cts.verifier.bluetooth.BLE_CHARACTERISTIC_READ_REQUEST";
    public static final String BLE_CHARACTERISTIC_WRITE_REQUEST =
            "com.android.cts.verifier.bluetooth.BLE_CHARACTERISTIC_WRITE_REQUEST";
    public static final String BLE_CHARACTERISTIC_READ_REQUEST_WITHOUT_PERMISSION =
            "com.android.cts.verifier.bluetooth.BLE_CHARACTERISTIC_READ_REQUEST_WITHOUT_PERMISSION";
    public static final String BLE_CHARACTERISTIC_WRITE_REQUEST_WITHOUT_PERMISSION =
            "com.android.cts.verifier.bluetooth.BLE_CHARACTERISTIC_WRITE_REQUEST_WITHOUT_PERMISSION";
    public static final String BLE_CHARACTERISTIC_READ_REQUEST_NEED_ENCRYPTED =
            "com.android.cts.verifier.bluetooth.BLE_CHARACTERISTIC_READ_REQUEST_NEED_ENCRYPTED";
    public static final String BLE_CHARACTERISTIC_WRITE_REQUEST_NEED_ENCRYPTED =
            "com.android.cts.verifier.bluetooth.BLE_CHARACTERISTIC_WRITE_REQUEST_NEED_ENCRYPTED";
    public static final String BLE_CHARACTERISTIC_NOTIFICATION_REQUEST =
            "com.android.cts.verifier.bluetooth.BLE_CHARACTERISTIC_NOTIFICATION_REQUEST";
    public static final String BLE_CHARACTERISTIC_INDICATE_REQUEST =
            "com.android.cts.verifier.bluetooth.BLE_CHARACTERISTIC_INDICATE_REQUEST";
    public static final String BLE_DESCRIPTOR_READ_REQUEST =
            "com.android.cts.verifier.bluetooth.BLE_DESCRIPTOR_READ_REQUEST";
    public static final String BLE_DESCRIPTOR_WRITE_REQUEST =
            "com.android.cts.verifier.bluetooth.BLE_DESCRIPTOR_WRITE_REQUEST";
    public static final String BLE_DESCRIPTOR_READ_REQUEST_WITHOUT_PERMISSION =
            "com.android.cts.verifier.bluetooth.BLE_DESCRIPTOR_READ_REQUEST_WITHOUT_PERMISSION";
    public static final String BLE_DESCRIPTOR_WRITE_REQUEST_WITHOUT_PERMISSION =
            "com.android.cts.verifier.bluetooth.BLE_DESCRIPTOR_WRITE_REQUEST_WITHOUT_PERMISSION";
    public static final String BLE_DESCRIPTOR_READ_REQUEST_NEED_ENCRYPTED =
            "com.android.cts.verifier.bluetooth.BLE_DESCRIPTOR_READ_REQUEST_NEED_ENCRYPTED";
    public static final String BLE_DESCRIPTOR_WRITE_REQUEST_NEED_ENCRYPTED =
            "com.android.cts.verifier.bluetooth.BLE_DESCRIPTOR_WRITE_REQUEST_NEED_ENCRYPTED";
    public static final String BLE_EXECUTE_WRITE =
            "com.android.cts.verifier.bluetooth.BLE_EXECUTE_WRITE";
    public static final String BLE_OPEN_FAIL =
            "com.android.cts.verifier.bluetooth.BLE_OPEN_FAIL";
    public static final String BLE_RELIABLE_WRITE_BAD_RESP =
            "com.android.cts.verifier.bluetooth.BLE_RELIABLE_WRITE_BAD_RESP";
    public static final String BLE_ADVERTISE_UNSUPPORTED =
            "com.android.cts.verifier.bluetooth.BLE_ADVERTISE_UNSUPPORTED";
    public static final String BLE_ADD_SERVICE_FAIL =
            "com.android.cts.verifier.bluetooth.BLE_ADD_SERVICE_FAIL";

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
    public static final UUID ADV_SERVICE_UUID=
            UUID.fromString("00003333-0000-1000-8000-00805f9b34fb");

    private static final UUID SERVICE_UUID_ADDITIONAL =
            UUID.fromString("00009995-0000-1000-8000-00805f9b34fb");
    private static final UUID SERVICE_UUID_INCLUDED =
            UUID.fromString("00009994-0000-1000-8000-00805f9b34fb");

    // Variable for registration permission of Characteristic
    private static final UUID CHARACTERISTIC_NO_READ_UUID =
            UUID.fromString("00009984-0000-1000-8000-00805f9b34fb");
    private static final UUID CHARACTERISTIC_NO_WRITE_UUID =
            UUID.fromString("00009983-0000-1000-8000-00805f9b34fb");
    private static final UUID CHARACTERISTIC_NEED_ENCRYPTED_READ_UUID =
            UUID.fromString("00009982-0000-1000-8000-00805f9b34fb");
    private static final UUID CHARACTERISTIC_NEED_ENCRYPTED_WRITE_UUID =
            UUID.fromString("00009981-0000-1000-8000-00805f9b34fb");

    // Variable for registration permission of Descriptor
    private static final UUID DESCRIPTOR_NO_READ_UUID =
            UUID.fromString("00009973-0000-1000-8000-00805f9b34fb");
    private static final UUID DESCRIPTOR_NO_WRITE_UUID =
            UUID.fromString("00009972-0000-1000-8000-00805f9b34fb");
    private static final UUID DESCRIPTOR_NEED_ENCRYPTED_READ_UUID =
            UUID.fromString("00009969-0000-1000-8000-00805f9b34fb");
    private static final UUID DESCRIPTOR_NEED_ENCRYPTED_WRITE_UUID =
            UUID.fromString("00009968-0000-1000-8000-00805f9b34fb");

    //  Variable for registration upper limit confirmation of Characteristic
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

    private static final int CONN_INTERVAL = 150;   // connection interval 150ms

    // Delay of notification when secure test failed to start.
    private static final long NOTIFICATION_DELAY_OF_SECURE_TEST_FAILURE = 5 * 1000;

    public static final String WRITE_VALUE = "SERVER_TEST";
    private static final String NOTIFY_VALUE = "NOTIFY_TEST";
    private static final String INDICATE_VALUE = "INDICATE_TEST";
    public static final String READ_NO_PERMISSION = "READ_NO_CHAR";
    public static final String WRITE_NO_PERMISSION = "WRITE_NO_CHAR";
    public static final String DESCRIPTOR_READ_NO_PERMISSION = "READ_NO_DESC";
    public static final String DESCRIPTOR_WRITE_NO_PERMISSION = "WRITE_NO_DESC";

    private BluetoothManager mBluetoothManager;
    private BluetoothGattServer mGattServer;
    private BluetoothGattService mService;
    private BluetoothDevice mDevice;
    private Timer mNotificationTimer;
    private Handler mHandler;
    private String mReliableWriteValue;
    private BluetoothLeAdvertiser mAdvertiser;
    private boolean mIndicated;
    private int mNotifyCount;
    private boolean mSecure;
    private int mCountMtuChange;
    private int mMtuSize = -1;
    private String mMtuTestReceivedData;
    private Runnable mResetValuesTask;
    private BluetoothGattService mAdditionalNotificationService;

    // Task to notify failure of starting secure test.
    //   Secure test calls BluetoothDevice#createBond() when devices were not paired.
    //   createBond() causes onConnectionStateChange() twice, and it works as strange sequence.
    //   At the first onConnectionStateChange(), target device is not paired(bond state is
    //   BluetoothDevice.BOND_NONE).
    //   At the second onConnectionStateChange(), target devices is paired(bond state is
    //   BluetoothDevice.BOND_BONDED).
    //   CTS Verifier will perform lazy check of bond state.Verifier checks bond state
    //   after NOTIFICATION_DELAY_OF_SECURE_TEST_FAILURE from the first onConnectionStateChange().
    private Runnable mNotificationTaskOfSecureTestStartFailure;

    @Override
    public void onCreate() {
        super.onCreate();

        mBluetoothManager = (BluetoothManager) getSystemService(Context.BLUETOOTH_SERVICE);
        mAdvertiser = mBluetoothManager.getAdapter().getBluetoothLeAdvertiser();
        mGattServer = mBluetoothManager.openGattServer(this, mCallbacks);

        mService = createService();
        mAdditionalNotificationService = createAdditionalNotificationService();

        mDevice = null;
        mReliableWriteValue = "";

        mHandler = new Handler();
        if (!mBluetoothManager.getAdapter().isEnabled()) {
            notifyBluetoothDisabled();
        } else if (mGattServer == null) {
            notifyOpenFail();
        } else if (mAdvertiser == null) {
            notifyAdvertiseUnsupported();
        } else {
            // start adding services
            mNotifyCount = 11;
            mSecure = false;
            mCountMtuChange = 0;
            if (!mGattServer.addService(mService)) {
                notifyAddServiceFail();
            }
        }
    }

    private void notifyBluetoothDisabled() {
        Intent intent = new Intent(BLE_BLUETOOTH_DISABLED);
        sendBroadcast(intent);
    }

    private void notifyMismatchSecure() {
        Intent intent = new Intent(BLE_BLUETOOTH_MISMATCH_SECURE);
        sendBroadcast(intent);
    }

    private void notifyMismatchInsecure() {
        /*
        Intent intent = new Intent(BLE_BLUETOOTH_MISMATCH_INSECURE);
        sendBroadcast(intent);
        */
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        String action = intent.getAction();
        if (action != null) {
            switch (action) {
            case BLE_ACTION_SERVER_SECURE:
                mSecure = true;
                if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.M) {
                    showMessage("Skip MTU test.");
                    mCountMtuChange = 1;
                    notifyMtuRequest();
                    mCountMtuChange = 2;
                    notifyMtuRequest();
                    mCountMtuChange = 0;
                }
                break;
            case BLE_ACTION_SERVER_NON_SECURE:
                mSecure = false;
                break;
            }
        }

        if (mBluetoothManager.getAdapter().isEnabled() && (mAdvertiser != null)) {
            startAdvertise();
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
        cancelNotificationTaskOfSecureTestStartFailure();
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
                getCharacteristic(CHARACTERISTIC_UUID).setValue(WRITE_VALUE.getBytes());
                getDescriptor().setValue(WRITE_VALUE.getBytes());
            }
        };
        mHandler.postDelayed(mResetValuesTask, CONN_INTERVAL);
    }

    private void notifyOpenFail() {
        if (DEBUG) {
            Log.d(TAG, "notifyOpenFail");
        }
        Intent intent = new Intent(BLE_OPEN_FAIL);
        sendBroadcast(intent);
    }

    private void notifyAddServiceFail() {
        if (DEBUG) {
            Log.d(TAG, "notifyAddServiceFail");
        }
        Intent intent = new Intent(BLE_ADD_SERVICE_FAIL);
        sendBroadcast(intent);
    }

    private void notifyAdvertiseUnsupported() {
        if (DEBUG) {
            Log.d(TAG, "notifyAdvertiseUnsupported");
        }
        Intent intent = new Intent(BLE_ADVERTISE_UNSUPPORTED);
        sendBroadcast(intent);
    }

    private void notifyConnected() {
        if (DEBUG) {
            Log.d(TAG, "notifyConnected");
        }
        Intent intent = new Intent(BLE_SERVER_CONNECTED);
        sendBroadcast(intent);

        resetValues();
    }

    private void notifyDisconnected() {
        if (DEBUG) {
            Log.d(TAG, "notifyDisconnected");
        }
        Intent intent = new Intent(BLE_SERVER_DISCONNECTED);
        sendBroadcast(intent);
    }

    private void notifyServiceAdded() {
        if (DEBUG) {
            Log.d(TAG, "notifyServiceAdded");
        }
        Intent intent = new Intent(BLE_SERVICE_ADDED);
        sendBroadcast(intent);
    }

    private void notifyMtuRequest() {
        if (DEBUG) {
            Log.d(TAG, "notifyMtuRequest");
        }
        Intent intent;
        if (mCountMtuChange == 1) {
            intent = new Intent(BLE_MTU_REQUEST_23BYTES);
        } else if (mCountMtuChange == 2) {
            intent = new Intent(BLE_MTU_REQUEST_512BYTES);
        } else {
            return; // never occurs
        }
        sendBroadcast(intent);
    }

    private void notifyCharacteristicReadRequest(boolean resetValues) {
        if (DEBUG) {
            Log.d(TAG, "notifyCharacteristicReadRequest");
        }
        Intent intent = new Intent(BLE_CHARACTERISTIC_READ_REQUEST);
        sendBroadcast(intent);

        if (resetValues) {
            resetValues();
        }
    }

    private void notifyCharacteristicWriteRequest() {
        if (DEBUG) {
            Log.d(TAG, "notifyCharacteristicWriteRequest");
        }
        Intent intent = new Intent(BLE_CHARACTERISTIC_WRITE_REQUEST);
        sendBroadcast(intent);

        resetValues();
    }

    private void notifyCharacteristicReadRequestWithoutPermission() {
        if (DEBUG) {
            Log.d(TAG, "notifyCharacteristicReadRequestWithoutPermission");
        }
        Intent intent = new Intent(BLE_CHARACTERISTIC_READ_REQUEST_WITHOUT_PERMISSION);
        sendBroadcast(intent);

        resetValues();
    }

    private void notifyCharacteristicWriteRequestWithoutPermission() {
        if (DEBUG) {
            Log.d(TAG, "notifyCharacteristicWriteRequestWithoutPermission");
        }
        Intent intent = new Intent(BLE_CHARACTERISTIC_WRITE_REQUEST_WITHOUT_PERMISSION);
        sendBroadcast(intent);

        resetValues();
    }

    private void notifyCharacteristicReadRequestNeedEncrypted() {
        if (DEBUG) {
            Log.d(TAG, "notifyCharacteristicReadRequestNeedEncrypted");
        }
        Intent intent = new Intent(BLE_CHARACTERISTIC_READ_REQUEST_NEED_ENCRYPTED);
        sendBroadcast(intent);

        resetValues();
    }

    private void notifyCharacteristicWriteRequestNeedEncrypted() {
        if (DEBUG) {
            Log.d(TAG, "notifyCharacteristicWriteRequestNeedEncrypted");
        }
        Intent intent = new Intent(BLE_CHARACTERISTIC_WRITE_REQUEST_NEED_ENCRYPTED);
        sendBroadcast(intent);

        resetValues();
    }

    private void notifyCharacteristicNotificationRequest() {
        if (DEBUG) {
            Log.d(TAG, "notifyCharacteristicNotificationRequest");
        }
        mNotifyCount = 11;
        Intent intent = new Intent(BLE_CHARACTERISTIC_NOTIFICATION_REQUEST);
        sendBroadcast(intent);

        resetValues();
    }

    private void notifyCharacteristicIndicationRequest() {
        if (DEBUG) {
            Log.d(TAG, "notifyCharacteristicIndicationRequest");
        }
        Intent intent = new Intent(BLE_CHARACTERISTIC_INDICATE_REQUEST);
        sendBroadcast(intent);

        resetValues();
    }

    private void notifyDescriptorReadRequest() {
        if (DEBUG) {
            Log.d(TAG, "notifyDescriptorReadRequest");
        }
        Intent intent = new Intent(BLE_DESCRIPTOR_READ_REQUEST);
        sendBroadcast(intent);

        resetValues();
    }

    private void notifyDescriptorWriteRequest() {
        if (DEBUG) {
            Log.d(TAG, "notifyDescriptorWriteRequest");
        }
        Intent intent = new Intent(BLE_DESCRIPTOR_WRITE_REQUEST);
        sendBroadcast(intent);

        resetValues();
    }

    private void notifyDescriptorReadRequestWithoutPermission() {
        if (DEBUG) {
            Log.d(TAG, "notifyDescriptorReadRequestWithoutPermission");
        }
        Intent intent = new Intent(BLE_DESCRIPTOR_READ_REQUEST_WITHOUT_PERMISSION);
        sendBroadcast(intent);

        resetValues();
    }

    private void notifyDescriptorWriteRequestWithoutPermission() {
        if (DEBUG) {
            Log.d(TAG, "notifyDescriptorWriteRequestWithoutPermission");
        }
        Intent intent = new Intent(BLE_DESCRIPTOR_WRITE_REQUEST_WITHOUT_PERMISSION);
        sendBroadcast(intent);

        resetValues();
    }

    private void notifyDescriptorReadRequestNeedEncrypted() {
        if (DEBUG) {
            Log.d(TAG, "notifyDescriptorReadRequestNeedEncrypted");
        }
        Intent intent = new Intent(BLE_DESCRIPTOR_READ_REQUEST_NEED_ENCRYPTED);
        sendBroadcast(intent);

        resetValues();
    }

    private void notifyDescriptorWriteRequestNeedEncrypted() {
        if (DEBUG) {
            Log.d(TAG, "notifyDescriptorWriteRequestNeedEncrypted");
        }
        Intent intent = new Intent(BLE_DESCRIPTOR_WRITE_REQUEST_NEED_ENCRYPTED);
        sendBroadcast(intent);

        resetValues();
    }

    private void notifyExecuteWrite() {
        if (DEBUG) {
            Log.d(TAG, "notifyExecuteWrite");
        }
        Intent intent = new Intent(BLE_EXECUTE_WRITE);
        sendBroadcast(intent);

        resetValues();
    }

    private void notifyReliableWriteBadResp() {
        if (DEBUG) {
            Log.d(TAG, "notifyReliableWriteBadResp");
        }
        Intent intent = new Intent(BLE_RELIABLE_WRITE_BAD_RESP);
        sendBroadcast(intent);

        resetValues();
    }

    private BluetoothGattCharacteristic getCharacteristic(UUID uuid) {
        BluetoothGattCharacteristic characteristic = mService.getCharacteristic(uuid);
        if (characteristic == null) {
            showMessage("Characteristic not found");
        }
        return characteristic;
    }

    private BluetoothGattDescriptor getDescriptor() {
        BluetoothGattDescriptor descriptor = null;

        BluetoothGattCharacteristic characteristic = getCharacteristic(CHARACTERISTIC_UUID);
        if (characteristic != null) {
            descriptor = characteristic.getDescriptor(DESCRIPTOR_UUID);
            if (descriptor == null) {
                showMessage("Descriptor not found");
            }
        }
        return descriptor;
    }

    /**
     * Create service for notification test
     * @return
     */
    private BluetoothGattService createAdditionalNotificationService() {
        BluetoothGattService service =
                new BluetoothGattService(SERVICE_UUID_ADDITIONAL, BluetoothGattService.SERVICE_TYPE_PRIMARY);

        BluetoothGattCharacteristic notiCharacteristic =
                new BluetoothGattCharacteristic(UPDATE_CHARACTERISTIC_UUID_1, 0x12, 0x1);
        BluetoothGattDescriptor descriptor = new BluetoothGattDescriptor(UPDATE_DESCRIPTOR_UUID, 0x11);
        descriptor.setValue(BluetoothGattDescriptor.DISABLE_NOTIFICATION_VALUE);
        notiCharacteristic.addDescriptor(descriptor);
        notiCharacteristic.setValue(NOTIFY_VALUE);
        service.addCharacteristic(notiCharacteristic);

        notiCharacteristic =
                new BluetoothGattCharacteristic(UPDATE_CHARACTERISTIC_UUID_2, 0x14, 0x11);
        descriptor = new BluetoothGattDescriptor(UPDATE_DESCRIPTOR_UUID, 0x11);
        descriptor.setValue(BluetoothGattDescriptor.DISABLE_NOTIFICATION_VALUE);
        notiCharacteristic.addDescriptor(descriptor);
        notiCharacteristic.setValue(NOTIFY_VALUE);
        service.addCharacteristic(notiCharacteristic);

        notiCharacteristic =
                new BluetoothGattCharacteristic(UPDATE_CHARACTERISTIC_UUID_3, 0x16, 0x11);
        descriptor = new BluetoothGattDescriptor(UPDATE_DESCRIPTOR_UUID, 0x11);
        descriptor.setValue(BluetoothGattDescriptor.DISABLE_NOTIFICATION_VALUE);
        notiCharacteristic.addDescriptor(descriptor);
        notiCharacteristic.setValue(NOTIFY_VALUE);
        service.addCharacteristic(notiCharacteristic);

        notiCharacteristic =
                new BluetoothGattCharacteristic(UPDATE_CHARACTERISTIC_UUID_4, 0x18, 0x10);
        descriptor = new BluetoothGattDescriptor(UPDATE_DESCRIPTOR_UUID, 0x11);
        descriptor.setValue(BluetoothGattDescriptor.DISABLE_NOTIFICATION_VALUE);
        notiCharacteristic.addDescriptor(descriptor);
        notiCharacteristic.setValue(NOTIFY_VALUE);
        service.addCharacteristic(notiCharacteristic);

        notiCharacteristic =
                new BluetoothGattCharacteristic(UPDATE_CHARACTERISTIC_UUID_5, 0x1C, 0x11);
        descriptor = new BluetoothGattDescriptor(UPDATE_DESCRIPTOR_UUID, 0x11);
        descriptor.setValue(BluetoothGattDescriptor.DISABLE_NOTIFICATION_VALUE);
        notiCharacteristic.addDescriptor(descriptor);
        notiCharacteristic.setValue(NOTIFY_VALUE);
        service.addCharacteristic(notiCharacteristic);

        notiCharacteristic =
                new BluetoothGattCharacteristic(UPDATE_CHARACTERISTIC_UUID_11, 0x3A, 0x11);
        descriptor = new BluetoothGattDescriptor(UPDATE_DESCRIPTOR_UUID, 0x11);
        descriptor.setValue(BluetoothGattDescriptor.DISABLE_NOTIFICATION_VALUE);
        notiCharacteristic.addDescriptor(descriptor);
        notiCharacteristic.setValue(NOTIFY_VALUE);
        service.addCharacteristic(notiCharacteristic);

        notiCharacteristic =
                new BluetoothGattCharacteristic(UPDATE_CHARACTERISTIC_UUID_12, 0x3C, 0x11);
        descriptor = new BluetoothGattDescriptor(UPDATE_DESCRIPTOR_UUID, 0x11);
        descriptor.setValue(BluetoothGattDescriptor.DISABLE_NOTIFICATION_VALUE);
        notiCharacteristic.addDescriptor(descriptor);
        notiCharacteristic.setValue(NOTIFY_VALUE);
        service.addCharacteristic(notiCharacteristic);

        notiCharacteristic =
                new BluetoothGattCharacteristic(UPDATE_CHARACTERISTIC_UUID_13, 0x3E, 0x11);
        descriptor = new BluetoothGattDescriptor(UPDATE_DESCRIPTOR_UUID, 0x11);
        descriptor.setValue(BluetoothGattDescriptor.DISABLE_NOTIFICATION_VALUE);
        notiCharacteristic.addDescriptor(descriptor);
        notiCharacteristic.setValue(NOTIFY_VALUE);
        service.addCharacteristic(notiCharacteristic);

        notiCharacteristic =
                new BluetoothGattCharacteristic(UPDATE_CHARACTERISTIC_UUID_14, 0x10, 0x0);
        descriptor = new BluetoothGattDescriptor(UPDATE_DESCRIPTOR_UUID, 0x11);
        descriptor.setValue(BluetoothGattDescriptor.DISABLE_NOTIFICATION_VALUE);
        notiCharacteristic.addDescriptor(descriptor);
        notiCharacteristic.setValue(NOTIFY_VALUE);
        service.addCharacteristic(notiCharacteristic);

        notiCharacteristic =
                new BluetoothGattCharacteristic(UPDATE_CHARACTERISTIC_UUID_15, 0x30, 0x0);
        descriptor = new BluetoothGattDescriptor(UPDATE_DESCRIPTOR_UUID, 0x11);
        descriptor.setValue(BluetoothGattDescriptor.DISABLE_NOTIFICATION_VALUE);
        notiCharacteristic.addDescriptor(descriptor);
        notiCharacteristic.setValue(NOTIFY_VALUE);
        service.addCharacteristic(notiCharacteristic);

        return service;
    }

    private BluetoothGattService createService() {
        BluetoothGattService service =
                new BluetoothGattService(SERVICE_UUID, BluetoothGattService.SERVICE_TYPE_PRIMARY);
        BluetoothGattCharacteristic characteristic =
                new BluetoothGattCharacteristic(CHARACTERISTIC_UUID, 0x0A, 0x11);
        characteristic.setValue(WRITE_VALUE.getBytes());

        BluetoothGattDescriptor descriptor = new BluetoothGattDescriptor(DESCRIPTOR_UUID, 0x11);
        descriptor.setValue(WRITE_VALUE.getBytes());
        characteristic.addDescriptor(descriptor);

        BluetoothGattDescriptor descriptor_permission = new BluetoothGattDescriptor(DESCRIPTOR_NO_READ_UUID, 0x10);
        characteristic.addDescriptor(descriptor_permission);

        descriptor_permission = new BluetoothGattDescriptor(DESCRIPTOR_NO_WRITE_UUID, 0x01);
        characteristic.addDescriptor(descriptor_permission);

        service.addCharacteristic(characteristic);

        characteristic =
                new BluetoothGattCharacteristic(CHARACTERISTIC_RESULT_UUID, 0x0A, 0x11);

        BluetoothGattDescriptor descriptor_encrypted = new BluetoothGattDescriptor(DESCRIPTOR_NEED_ENCRYPTED_READ_UUID, 0x02);
        characteristic.addDescriptor(descriptor_encrypted);

        descriptor_encrypted = new BluetoothGattDescriptor(DESCRIPTOR_NEED_ENCRYPTED_WRITE_UUID, 0x20);
        characteristic.addDescriptor(descriptor_encrypted);

        service.addCharacteristic(characteristic);

        // Add new Characteristics
        // Registered the characteristic of read permission for operation confirmation.
        characteristic =
                new BluetoothGattCharacteristic(CHARACTERISTIC_NO_READ_UUID, 0x0A, 0x10);
        service.addCharacteristic(characteristic);

        // Registered the characteristic of write permission for operation confirmation.
        characteristic =
                new BluetoothGattCharacteristic(CHARACTERISTIC_NO_WRITE_UUID, 0x0A, 0x01);
        service.addCharacteristic(characteristic);

        // Registered the characteristic of authenticate (Encrypted) for operation confirmation.
        characteristic =
                new BluetoothGattCharacteristic(CHARACTERISTIC_NEED_ENCRYPTED_READ_UUID, 0x0A, 0x02);
        service.addCharacteristic(characteristic);

        characteristic =
                new BluetoothGattCharacteristic(CHARACTERISTIC_NEED_ENCRYPTED_WRITE_UUID, 0x0A, 0x20);
        service.addCharacteristic(characteristic);

        // Add new Characteristics(Indicate)
        BluetoothGattCharacteristic indicateCharacteristic =
                new BluetoothGattCharacteristic(INDICATE_CHARACTERISTIC_UUID, 0x2A, 0x11);
        descriptor = new BluetoothGattDescriptor(UPDATE_DESCRIPTOR_UUID, 0x11);
        descriptor.setValue(BluetoothGattDescriptor.DISABLE_NOTIFICATION_VALUE);
        indicateCharacteristic.addDescriptor(descriptor);
        indicateCharacteristic.setValue(INDICATE_VALUE);
        service.addCharacteristic(indicateCharacteristic);

        // Add new Characteristics(Notify)
        BluetoothGattCharacteristic notiCharacteristic =
                new BluetoothGattCharacteristic(UPDATE_CHARACTERISTIC_UUID, 0x1A, 0x11);
        descriptor = new BluetoothGattDescriptor(UPDATE_DESCRIPTOR_UUID, 0x11);
        descriptor.setValue(BluetoothGattDescriptor.DISABLE_NOTIFICATION_VALUE);
        notiCharacteristic.addDescriptor(descriptor);
        notiCharacteristic.setValue(NOTIFY_VALUE);
        service.addCharacteristic(notiCharacteristic);

        notiCharacteristic =
                new BluetoothGattCharacteristic(UPDATE_CHARACTERISTIC_UUID_6, 0x1E, 0x11);
        descriptor = new BluetoothGattDescriptor(UPDATE_DESCRIPTOR_UUID, 0x11);
        descriptor.setValue(BluetoothGattDescriptor.DISABLE_NOTIFICATION_VALUE);
        notiCharacteristic.addDescriptor(descriptor);
        notiCharacteristic.setValue(NOTIFY_VALUE);
        service.addCharacteristic(notiCharacteristic);

        notiCharacteristic =
                new BluetoothGattCharacteristic(UPDATE_CHARACTERISTIC_UUID_7, 0x32, 0x1);
        descriptor = new BluetoothGattDescriptor(UPDATE_DESCRIPTOR_UUID, 0x11);
        descriptor.setValue(BluetoothGattDescriptor.DISABLE_NOTIFICATION_VALUE);
        notiCharacteristic.addDescriptor(descriptor);
        notiCharacteristic.setValue(NOTIFY_VALUE);
        service.addCharacteristic(notiCharacteristic);

        notiCharacteristic =
                new BluetoothGattCharacteristic(UPDATE_CHARACTERISTIC_UUID_8, 0x34, 0x11);
        descriptor = new BluetoothGattDescriptor(UPDATE_DESCRIPTOR_UUID, 0x11);
        descriptor.setValue(BluetoothGattDescriptor.DISABLE_NOTIFICATION_VALUE);
        notiCharacteristic.addDescriptor(descriptor);
        notiCharacteristic.setValue(NOTIFY_VALUE);
        service.addCharacteristic(notiCharacteristic);

        notiCharacteristic =
                new BluetoothGattCharacteristic(UPDATE_CHARACTERISTIC_UUID_9, 0x36, 0x11);
        descriptor = new BluetoothGattDescriptor(UPDATE_DESCRIPTOR_UUID, 0x11);
        descriptor.setValue(BluetoothGattDescriptor.DISABLE_NOTIFICATION_VALUE);
        notiCharacteristic.addDescriptor(descriptor);
        notiCharacteristic.setValue(NOTIFY_VALUE);
        service.addCharacteristic(notiCharacteristic);

        notiCharacteristic =
                new BluetoothGattCharacteristic(UPDATE_CHARACTERISTIC_UUID_10, 0x38, 0x10);
        descriptor = new BluetoothGattDescriptor(UPDATE_DESCRIPTOR_UUID, 0x11);
        descriptor.setValue(BluetoothGattDescriptor.DISABLE_NOTIFICATION_VALUE);
        notiCharacteristic.addDescriptor(descriptor);
        notiCharacteristic.setValue(NOTIFY_VALUE);
        service.addCharacteristic(notiCharacteristic);

        return service;
    }

    private void showMessage(final String msg) {
        mHandler.post(new Runnable() {
            public void run() {
                Toast.makeText(BleServerService.this, msg, Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void onMtuTestDataReceive() {

        Log.d(TAG, "onMtuTestDataReceive(" + mCountMtuChange + "):" + mMtuTestReceivedData);

        // verify
        if (mMtuTestReceivedData.equals(BleClientService.WRITE_VALUE_512BYTES_FOR_MTU)) {

            // write back data
            // MTU test verifies whether the write/read operations go well.
            BluetoothGattCharacteristic characteristic = getCharacteristic(CHARACTERISTIC_UUID);
            characteristic.setValue(mMtuTestReceivedData.getBytes());

            notifyMtuRequest();
        } else {
            showMessage(getString(R.string.ble_mtu_fail_message));
        }
        mMtuTestReceivedData = "";
        if (mCountMtuChange >= 2) {
            // All MTU change tests completed
            mCountMtuChange = 0;
        }
    }

    private synchronized void cancelNotificationTaskOfSecureTestStartFailure() {
        if (mNotificationTaskOfSecureTestStartFailure != null) {
            mHandler.removeCallbacks(mNotificationTaskOfSecureTestStartFailure);
            mNotificationTaskOfSecureTestStartFailure = null;
        }
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
                    boolean bonded = false;
                    Set<BluetoothDevice> pairedDevices = mBluetoothManager.getAdapter().getBondedDevices();
                    if (pairedDevices.size() > 0) {
                        for (BluetoothDevice target : pairedDevices) {
                            if (target.getAddress().equals(device.getAddress())) {
                                bonded = true;
                                break;
                            }
                        }
                    }

                    if (mSecure && ((device.getBondState() == BluetoothDevice.BOND_NONE) || !bonded)) {
                        // not pairing and execute Secure Test
                        cancelNotificationTaskOfSecureTestStartFailure();
                        /*
                        mNotificationTaskOfSecureTestStartFailure = new Runnable() {
                            @Override
                            public void run() {
                                mNotificationTaskOfSecureTestStartFailure = null;
                                if (mSecure && (mDevice.getBondState() != BluetoothDevice.BOND_BONDED)) {
                                    notifyMismatchSecure();
                                }
                            }
                        };
                        mHandler.postDelayed(mNotificationTaskOfSecureTestStartFailure,
                                NOTIFICATION_DELAY_OF_SECURE_TEST_FAILURE);
                        */
                    } else if (!mSecure && ((device.getBondState() != BluetoothDevice.BOND_NONE) || bonded)) {
                        // already pairing nad execute Insecure Test
                        /*
                        notifyMismatchInsecure();
                        */
                    } else {
                        cancelNotificationTaskOfSecureTestStartFailure();
                        notifyConnected();
                    }
                } else if (status == BluetoothProfile.STATE_DISCONNECTED) {
                    notifyDisconnected();
                    mDevice = null;
                }
            }
        }

        @Override
        public void onServiceAdded(int status, BluetoothGattService service) {
            if (DEBUG) {
                Log.d(TAG, "onServiceAdded(): " + service.getUuid());
                dumpService(service, 0);
            }
            if (status == BluetoothGatt.GATT_SUCCESS) {
                UUID uuid = service.getUuid();

                if (uuid.equals(mService.getUuid())) {
                    // create and add nested service
                    BluetoothGattService includedService =
                            new BluetoothGattService(SERVICE_UUID_INCLUDED, BluetoothGattService.SERVICE_TYPE_SECONDARY);
                    BluetoothGattCharacteristic characteristic =
                        new BluetoothGattCharacteristic(CHARACTERISTIC_UUID, 0x0A, 0x11);
                    characteristic.setValue(WRITE_VALUE.getBytes());
                    BluetoothGattDescriptor descriptor = new BluetoothGattDescriptor(DESCRIPTOR_UUID, 0x11);
                    descriptor.setValue(WRITE_VALUE.getBytes());
                    characteristic.addDescriptor(descriptor);
                    includedService.addCharacteristic(characteristic);
                    mGattServer.addService(includedService);
                } else if (uuid.equals(SERVICE_UUID_INCLUDED)) {
                    mService.addService(service);
                    mGattServer.addService(mAdditionalNotificationService);
                } else if (uuid.equals(mAdditionalNotificationService.getUuid())) {
                    // all services added
                    notifyServiceAdded();
                } else {
                    notifyAddServiceFail();
                }
            } else {
                notifyAddServiceFail();
            }
        }

        @Override
        public void onCharacteristicReadRequest(BluetoothDevice device, int requestId, int offset, BluetoothGattCharacteristic characteristic) {
            if (mGattServer == null) {
                if (DEBUG) {
                    Log.d(TAG, "GattServer is null, return");
                }
                return;
            }
            if (DEBUG) {
                Log.d(TAG, "onCharacteristicReadRequest()");
            }

            boolean finished = false;
            byte[] value = null;
            if (mMtuSize > 0) {
                byte[] buf = characteristic.getValue();
                if (buf != null) {
                    int len = Math.min((buf.length - offset), mMtuSize);
                    if (len > 0) {
                        value = Arrays.copyOfRange(buf, offset, (offset + len));
                    }
                    finished = ((offset + len) >= buf.length);
                    if (finished) {
                        Log.d(TAG, "sent whole data: " + (new String(characteristic.getValue())));
                    }
                }
            } else {
                value = characteristic.getValue();
                finished = true;
            }

            mGattServer.sendResponse(device, requestId, BluetoothGatt.GATT_SUCCESS, offset, value);

            UUID uid = characteristic.getUuid();
            if (uid.equals(CHARACTERISTIC_NEED_ENCRYPTED_READ_UUID)) {
                notifyCharacteristicReadRequestNeedEncrypted();
            } else {
                notifyCharacteristicReadRequest(finished);
            }
        }

        @Override
        public void onCharacteristicWriteRequest(BluetoothDevice device, int requestId,
                BluetoothGattCharacteristic characteristic,
                boolean preparedWrite, boolean responseNeeded,
                int offset, byte[] value) {
            if (mGattServer == null) {
                if (DEBUG) {
                    Log.d(TAG, "GattServer is null, return");
                }
                return;
            }
            if (DEBUG) {
                Log.d(TAG, "onCharacteristicWriteRequest: preparedWrite=" + preparedWrite + ", responseNeeded= " + responseNeeded);
            }

            if (characteristic.getUuid().equals(CHARACTERISTIC_RESULT_UUID)) {
                String resValue = new String(value);
                Log.d(TAG, "CHARACTERISTIC_RESULT_UUID: resValue=" + resValue);
                switch (resValue) {
                    case WRITE_NO_PERMISSION:
                        notifyCharacteristicWriteRequestWithoutPermission();
                        break;
                    case READ_NO_PERMISSION:
                        notifyCharacteristicReadRequestWithoutPermission();
                        break;
                    case DESCRIPTOR_WRITE_NO_PERMISSION:
                        notifyDescriptorWriteRequestWithoutPermission();
                        break;
                    case DESCRIPTOR_READ_NO_PERMISSION:
                        notifyDescriptorReadRequestWithoutPermission();
                        break;
                }
                if (responseNeeded) {
                    mGattServer.sendResponse(device, requestId, BluetoothGatt.GATT_SUCCESS, offset, value);
                }
                return;
            }

            // MTU test flow
            if (mCountMtuChange > 0) {
                if (preparedWrite) {
                    mMtuTestReceivedData += new String(value);
                } else {
                    String strValue = new String(value);
                    if (mCountMtuChange > 0) {
                        mMtuTestReceivedData = strValue;
                        onMtuTestDataReceive();
                    }
                }
                if (responseNeeded) {
                    mGattServer.sendResponse(device, requestId, BluetoothGatt.GATT_SUCCESS, offset, value);
                }

                return;
            }

            // Reliable write with bad response test flow
            String valueStr = new String(value);
            if (BleClientService.WRITE_VALUE_BAD_RESP.equals(valueStr)) {
                mGattServer.sendResponse(device, requestId, BluetoothGatt.GATT_SUCCESS, 0, null);
                notifyReliableWriteBadResp();
                return;
            }

            if (preparedWrite) {
                mReliableWriteValue += (new String(value));
            } else {
                characteristic.setValue(value);
                // verify
                if (Arrays.equals(BleClientService.WRITE_VALUE.getBytes(), characteristic.getValue())) {
                    UUID uid = characteristic.getUuid();
                    if (uid.equals(CHARACTERISTIC_NEED_ENCRYPTED_WRITE_UUID)) {
                        notifyCharacteristicWriteRequestNeedEncrypted();
                    } else {
                        notifyCharacteristicWriteRequest();
                    }
                } else {
                    showMessage("Written data is not correct");
                }
            }

            if (responseNeeded) {
                mGattServer.sendResponse(device, requestId, BluetoothGatt.GATT_SUCCESS, offset, value);
            }
        }

        @Override
        public void onDescriptorReadRequest(BluetoothDevice device, int requestId,
                int offset, BluetoothGattDescriptor descriptor) {
            if (mGattServer == null) {
                if (DEBUG) {
                    Log.d(TAG, "GattServer is null, return");
                }
                return;
            }
                if (DEBUG) {
                Log.d(TAG, "onDescriptorReadRequest(): (descriptor == getDescriptor())="
                        + (descriptor == getDescriptor()));
            }

            UUID uid = descriptor.getUuid();
            if (uid.equals(DESCRIPTOR_NEED_ENCRYPTED_READ_UUID)){
                notifyDescriptorReadRequestNeedEncrypted();
            } else {
                notifyDescriptorReadRequest();
            }

            byte[] value = descriptor.getValue();
            if (value == null) {
                throw new RuntimeException("descriptor data read is null");
            }

            mGattServer.sendResponse(device, requestId, BluetoothGatt.GATT_SUCCESS, offset, value);
        }

        @Override
        public void onDescriptorWriteRequest(BluetoothDevice device, int requestId,
                BluetoothGattDescriptor descriptor,
                boolean preparedWrite, boolean responseNeeded,
                int offset,  byte[] value) {
            if (mGattServer == null) {
                if (DEBUG) {
                    Log.d(TAG, "GattServer is null, return");
                }
                return;
            }
            BluetoothGattCharacteristic characteristic = descriptor.getCharacteristic();
            UUID uid = characteristic.getUuid();
            if (DEBUG) {
                Log.d(TAG, "onDescriptorWriteRequest: preparedWrite=" + preparedWrite + ", responseNeeded= " + responseNeeded);
                Log.d(TAG, "   characteristic uuid = " + uid);
            }

            descriptor.setValue(value);
            UUID duid = descriptor.getUuid();
            // If there is a written request to the CCCD for Notify.
            if (duid.equals(UPDATE_DESCRIPTOR_UUID)) {
                if (Arrays.equals(value, BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE)) {
                    mGattServer.notifyCharacteristicChanged(mDevice, descriptor.getCharacteristic(), false);
                    mIndicated = false;
                } else if (Arrays.equals(value, BluetoothGattDescriptor.ENABLE_INDICATION_VALUE)) {
                    mGattServer.notifyCharacteristicChanged(mDevice, descriptor.getCharacteristic(), true);
                    mIndicated = true;
                }
            } else if (duid.equals(DESCRIPTOR_NEED_ENCRYPTED_WRITE_UUID)) {
                // verify
                if (Arrays.equals(BleClientService.WRITE_VALUE.getBytes(), descriptor.getValue())) {
                    notifyDescriptorWriteRequestNeedEncrypted();
                } else {
                    showMessage("Written data is not correct");
                }
            } else {
                // verify
                if (Arrays.equals(BleClientService.WRITE_VALUE.getBytes(), descriptor.getValue())) {
                    notifyDescriptorWriteRequest();
                } else {
                    showMessage("Written data is not correct");
                }
            }
            if (responseNeeded) {
                mGattServer.sendResponse(device, requestId, BluetoothGatt.GATT_SUCCESS, offset, value);
            }
        }

        @Override
        public void onExecuteWrite(BluetoothDevice device, int requestId, boolean execute) {
            if (mGattServer == null) {
                if (DEBUG) {
                    Log.d(TAG, "GattServer is null, return");
                }
                return;
            }
            if (DEBUG) {
                Log.d(TAG, "onExecuteWrite");
            }

            if (execute) {
                if (mCountMtuChange > 0) {
                    onMtuTestDataReceive();
                } else {
                    // verify
                    String str = BleClientService.WRITE_VALUE_507BYTES_FOR_RELIABLE_WRITE
                            + BleClientService.WRITE_VALUE_507BYTES_FOR_RELIABLE_WRITE;
                    if (str.equals(mReliableWriteValue)) {
                        notifyExecuteWrite();
                    } else {
                        showMessage("Failed to receive data");
                        Log.d(TAG, "Failed to receive data:" + mReliableWriteValue);
                    }
                }
                mReliableWriteValue = "";
            }
            mGattServer.sendResponse(device, requestId, BluetoothGatt.GATT_SUCCESS, 0, null);
        }

        @Override
        public void onNotificationSent(BluetoothDevice device, int status) {
            if (mGattServer == null) {
                if (DEBUG) {
                    Log.d(TAG, "GattServer is null, return");
                }
                return;
            }
            if (DEBUG) {
                Log.d(TAG, "onNotificationSent");
            }

            if (status == BluetoothGatt.GATT_SUCCESS) {
                if (mIndicated) {
                    notifyCharacteristicIndicationRequest();
                } else {
                    mNotifyCount--;
                    if (mNotifyCount == 0) {
                        notifyCharacteristicNotificationRequest();
                    }
                }
            }
        }

        @Override
        public void onMtuChanged(BluetoothDevice device, int mtu) {
            if (mGattServer == null) {
                if (DEBUG) {
                    Log.d(TAG, "GattServer is null, return");
                }
                return;
            }
            if (DEBUG) {
                Log.d(TAG, "onMtuChanged");
            }

            mMtuSize = mtu;
            if (mCountMtuChange == 0) {
                if (mtu != 23) {
                    String msg = String.format(getString(R.string.ble_mtu_mismatch_message),
                            23, mtu);
                    showMessage(msg);
                }
            } else if (mCountMtuChange == 1) {
                if (mtu != 512) {
                    String msg = String.format(getString(R.string.ble_mtu_mismatch_message),
                            512, mtu);
                    showMessage(msg);
                }
            }
            mMtuTestReceivedData = "";
            ++mCountMtuChange;
        }
    };

    private void startAdvertise() {
        if (DEBUG) {
            Log.d(TAG, "startAdvertise");
        }
        AdvertiseData data = new AdvertiseData.Builder()
            .addServiceData(new ParcelUuid(ADV_SERVICE_UUID), new byte[]{1,2,3})
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

    private final AdvertiseCallback mAdvertiseCallback = new AdvertiseCallback(){
        @Override
        public void onStartFailure(int errorCode) {
            // Implementation for API Test.
            super.onStartFailure(errorCode);
            if (DEBUG) {
                Log.d(TAG, "onStartFailure");
            }

            if (errorCode == ADVERTISE_FAILED_FEATURE_UNSUPPORTED) {
                notifyAdvertiseUnsupported();
            } else {
                notifyOpenFail();
            }
        }

        @Override
        public void onStartSuccess(AdvertiseSettings settingsInEffect) {
            // Implementation for API Test.
            super.onStartSuccess(settingsInEffect);
            if (DEBUG) {
                Log.d(TAG, "onStartSuccess");
            }
        }
    };

    /*protected*/ static void dumpService(BluetoothGattService service, int level) {
        String indent = "";
        for (int i = 0; i < level; ++i) {
            indent += "  ";
        }

        Log.d(TAG, indent + "[service]");
        Log.d(TAG, indent + "UUID: " + service.getUuid());
        Log.d(TAG, indent + "  [characteristics]");
        for (BluetoothGattCharacteristic ch : service.getCharacteristics()) {
            Log.d(TAG, indent + "    UUID: " + ch.getUuid());
            Log.d(TAG, indent + "      properties: " + String.format("0x%02X", ch.getProperties()));
            Log.d(TAG, indent + "      permissions: " + String.format("0x%02X", ch.getPermissions()));
            Log.d(TAG, indent + "      [descriptors]");
            for (BluetoothGattDescriptor d : ch.getDescriptors()) {
                Log.d(TAG, indent + "        UUID: " + d.getUuid());
                Log.d(TAG, indent + "          permissions: " + String.format("0x%02X", d.getPermissions()));
            }
        }

        if (service.getIncludedServices() != null) {
            Log.d(TAG, indent + "  [included services]");
            for (BluetoothGattService s : service.getIncludedServices()) {
                dumpService(s, level+1);
            }
        }
    }

}

