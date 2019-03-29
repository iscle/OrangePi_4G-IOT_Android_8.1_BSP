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
import android.os.DeadObjectException;
import android.os.Handler;
import android.os.IBinder;
import android.os.ParcelUuid;
import android.util.Log;
import android.widget.Toast;

import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.Set;
import java.util.Timer;
import java.util.TimerTask;
import java.util.UUID;

public class BleConnectionPriorityClientService extends Service {
    public static final boolean DEBUG = true;
    public static final String TAG = "BlePriorityClient";

    public static final String ACTION_BLUETOOTH_DISABLED =
            "com.android.cts.verifier.bluetooth.action.BLUETOOTH_DISABLED";

    public static final String ACTION_CONNECTION_SERVICES_DISCOVERED =
            "com.android.cts.verifier.bluetooth.action.CONNECTION_SERVICES_DISCOVERED";

    public static final String ACTION_BLUETOOTH_MISMATCH_SECURE =
            "com.android.cts.verifier.bluetooth.action.ACTION_BLUETOOTH_MISMATCH_SECURE";
    public static final String ACTION_BLUETOOTH_MISMATCH_INSECURE =
            "com.android.cts.verifier.bluetooth.action.ACTION_BLUETOOTH_MISMATCH_INSECURE";

    public static final String ACTION_CONNECTION_PRIORITY_BALANCED =
            "com.android.cts.verifier.bluetooth.action.CONNECTION_PRIORITY_BALANCED";
    public static final String ACTION_CONNECTION_PRIORITY_HIGH =
            "com.android.cts.verifier.bluetooth.action.CONNECTION_PRIORITY_HIGH";
    public static final String ACTION_CONNECTION_PRIORITY_LOW_POWER =
            "com.android.cts.verifier.bluetooth.action.CONNECTION_PRIORITY_LOW_POWER";

    public static final String ACTION_FINISH_CONNECTION_PRIORITY_BALANCED =
            "com.android.cts.verifier.bluetooth.action.FINISH_CONNECTION_PRIORITY_BALANCED";
    public static final String ACTION_FINISH_CONNECTION_PRIORITY_HIGH =
            "com.android.cts.verifier.bluetooth.action.FINISH_CONNECTION_PRIORITY_HIGH";
    public static final String ACTION_FINISH_CONNECTION_PRIORITY_LOW_POWER =
            "com.android.cts.verifier.bluetooth.action.FINISH_CONNECTION_PRIORITY_LOW_POWER";

    public static final String ACTION_CLIENT_CONNECT_SECURE =
            "com.android.cts.verifier.bluetooth.action.CLIENT_CONNECT_SECURE";

    public static final String ACTION_DISCONNECT =
            "com.android.cts.verifier.bluetooth.action.DISCONNECT";
    public static final String ACTION_FINISH_DISCONNECT =
            "com.android.cts.verifier.bluetooth.action.FINISH_DISCONNECT";

    public static final long DEFAULT_INTERVAL = 100L;
    public static final long DEFAULT_PERIOD = 10000L;

    // this string will be used at writing test and connection priority test.
    private static final String WRITE_VALUE = "TEST";

    private static final UUID SERVICE_UUID =
            UUID.fromString("00009999-0000-1000-8000-00805f9b34fb");
    private static final UUID CHARACTERISTIC_UUID =
            UUID.fromString("00009998-0000-1000-8000-00805f9b34fb");
    private static final UUID START_CHARACTERISTIC_UUID =
            UUID.fromString("00009997-0000-1000-8000-00805f9b34fb");
    private static final UUID STOP_CHARACTERISTIC_UUID =
            UUID.fromString("00009995-0000-1000-8000-00805f9b34fb");

    private BluetoothManager mBluetoothManager;
    private BluetoothAdapter mBluetoothAdapter;
    private BluetoothGatt mBluetoothGatt;
    private BluetoothLeScanner mScanner;
    private Handler mHandler;
    private Timer mConnectionTimer;
    private Context mContext;

    private String mAction;
    private long mInterval;
    private long mPeriod;
    private Date mStartDate;
    private int mWriteCount;
    private boolean mSecure;

    private String mPriority;

    private TestTaskQueue mTaskQueue;

    @Override
    public void onCreate() {
        super.onCreate();

        mTaskQueue = new TestTaskQueue(getClass().getName() + "__taskHandlerThread");

        mBluetoothManager = (BluetoothManager) getSystemService(Context.BLUETOOTH_SERVICE);
        mBluetoothAdapter = mBluetoothManager.getAdapter();
        mScanner = mBluetoothAdapter.getBluetoothLeScanner();
        mHandler = new Handler();
        mContext = this;
        mInterval = DEFAULT_INTERVAL;
        mPeriod = DEFAULT_PERIOD;

        startScan();
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (mBluetoothGatt != null) {
            try {
                mBluetoothGatt.disconnect();
                mBluetoothGatt.close();
            } catch (Exception e) {}
            finally {
                mBluetoothGatt = null;
            }
        }
        stopScan();

        mTaskQueue.quit();
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    private void notifyBluetoothDisabled() {
        Intent intent = new Intent(ACTION_BLUETOOTH_DISABLED);
        sendBroadcast(intent);
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null) {
            mAction = intent.getAction();
            if (mAction != null) {
                switch (mAction) {
                case ACTION_CLIENT_CONNECT_SECURE:
                    mSecure = true;
                    break;
                case ACTION_CONNECTION_PRIORITY_BALANCED:
                case ACTION_CONNECTION_PRIORITY_HIGH:
                case ACTION_CONNECTION_PRIORITY_LOW_POWER:
                    mTaskQueue.addTask(new Runnable() {
                        @Override
                        public void run() {
                            startPeriodicTransmission();
                        }
                    });
                    break;
                case ACTION_DISCONNECT:
                    if (mBluetoothGatt != null) {
                        mBluetoothGatt.disconnect();
                    } else {
                        notifyDisconnect();
                    }
                    break;
                }
            }
        }
        return START_NOT_STICKY;
    }

    private void startPeriodicTransmission() {
        mWriteCount = 0;

        // Set connection priority
        switch (mAction) {
        case ACTION_CONNECTION_PRIORITY_BALANCED:
            mPriority = BleConnectionPriorityServerService.CONNECTION_PRIORITY_BALANCED;
            mBluetoothGatt.requestConnectionPriority(BluetoothGatt.CONNECTION_PRIORITY_BALANCED);
            break;
        case ACTION_CONNECTION_PRIORITY_HIGH:
            mPriority = BleConnectionPriorityServerService.CONNECTION_PRIORITY_HIGH;
            mBluetoothGatt.requestConnectionPriority(BluetoothGatt.CONNECTION_PRIORITY_HIGH);
            break;
        case ACTION_CONNECTION_PRIORITY_LOW_POWER:
            mPriority = BleConnectionPriorityServerService.CONNECTION_PRIORITY_LOW_POWER;
            mBluetoothGatt.requestConnectionPriority(BluetoothGatt.CONNECTION_PRIORITY_LOW_POWER);
            break;
        default:
            mPriority = BleConnectionPriorityServerService.CONNECTION_PRIORITY_BALANCED;
            mBluetoothGatt.requestConnectionPriority(BluetoothGatt.CONNECTION_PRIORITY_BALANCED);
            break;
        }

        // Create Timer for Periodic transmission
        mStartDate = new Date();
        TimerTask task = new TimerTask() {
            @Override
            public void run() {
                if (mBluetoothGatt == null) {
                    if (DEBUG) {
                        Log.d(TAG, "BluetoothGatt is null, return");
                    }
                    return;
                }

                Date currentData = new Date();
                if ((currentData.getTime() - mStartDate.getTime()) >= mPeriod) {
                    if (mConnectionTimer != null) {
                        mConnectionTimer.cancel();
                        mConnectionTimer = null;
                    }
                    // The STOP_CHARACTERISTIC_UUID is critical in syncing the client and server
                    // states.  Delay the write by 2 seconds to improve the chance of this
                    // characteristic going through.  Consider changing the code to use callbacks
                    // in the future to make it more robust.
                    sleep(2000);
                    // write termination data (contains current priority and number of messages wrote)
                    String msg = "" + mPriority + "," + mWriteCount;
                    writeCharacteristic(STOP_CHARACTERISTIC_UUID, msg);
                    sleep(1000);
                    Intent intent = new Intent();
                    switch (mPriority) {
                    case BleConnectionPriorityServerService.CONNECTION_PRIORITY_BALANCED:
                        intent.setAction(ACTION_FINISH_CONNECTION_PRIORITY_BALANCED);
                        break;
                    case BleConnectionPriorityServerService.CONNECTION_PRIORITY_HIGH:
                        intent.setAction(ACTION_FINISH_CONNECTION_PRIORITY_HIGH);
                        break;
                    case BleConnectionPriorityServerService.CONNECTION_PRIORITY_LOW_POWER:
                        intent.setAction(ACTION_FINISH_CONNECTION_PRIORITY_LOW_POWER);
                        break;
                    }
                    sendBroadcast(intent);
                }

                if (mConnectionTimer != null) {
                    // write testing data
                    ++mWriteCount;
                    writeCharacteristic(CHARACTERISTIC_UUID, WRITE_VALUE);
                }
            }
        };

        // write starting data
        writeCharacteristic(START_CHARACTERISTIC_UUID, mPriority);

        // start sending
        sleep(1000);
        mConnectionTimer = new Timer();
        mConnectionTimer.schedule(task, 0, mInterval);
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

    private void writeCharacteristic(UUID uid, String writeValue) {
        BluetoothGattCharacteristic characteristic = getCharacteristic(uid);
        if (characteristic != null){
            characteristic.setValue(writeValue);
            mBluetoothGatt.writeCharacteristic(characteristic);
        }
    }

    private void sleep(int millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Log.e(TAG, "Error in thread sleep", e);
        }
    }

    private void showMessage(final String msg) {
        mHandler.post(new Runnable() {
            public void run() {
                Toast.makeText(BleConnectionPriorityClientService.this, msg, Toast.LENGTH_SHORT).show();
            }
        });
    }

    private final BluetoothGattCallback mGattCallbacks = new BluetoothGattCallback() {
        @Override
        public void onConnectionStateChange(BluetoothGatt gatt, int status, int newState) {
            if (DEBUG) Log.d(TAG, "onConnectionStateChange");
            if (status == BluetoothGatt.GATT_SUCCESS) {
                if (newState == BluetoothProfile.STATE_CONNECTED) {
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
                        showMessage("Bluetooth LE connected");
                        mBluetoothGatt.discoverServices();
                    }
                } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                    showMessage("Bluetooth LE disconnected");

                    notifyDisconnect();
                }
            } else {
                showMessage("Failed to connect");
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
                showMessage("Service discovered");
                Intent intent = new Intent(ACTION_CONNECTION_SERVICES_DISCOVERED);
                sendBroadcast(intent);
            }
        }

        @Override
        public void onCharacteristicWrite(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic, int status) {
            String value = characteristic.getStringValue(0);
            UUID uid = characteristic.getUuid();
            if (DEBUG) {
                Log.d(TAG, "onCharacteristicWrite: characteristic.val=" + value + " status=" + status + " uid=" + uid);
            }
        }
    };

    private final ScanCallback mScanCallback = new ScanCallback() {
        @Override
        public void onScanResult(int callbackType, ScanResult result) {
            if (mBluetoothGatt == null) {
                stopScan();
                mBluetoothGatt = BleClientService.connectGatt(result.getDevice(), mContext, false, mSecure, mGattCallbacks);
            }
        }
    };

    private void startScan() {
        if (DEBUG) {
            Log.d(TAG, "startScan");
        }
        if (!mBluetoothAdapter.isEnabled()) {
            notifyBluetoothDisabled();
        } else {
            List<ScanFilter> filter = Arrays.asList(new ScanFilter.Builder().setServiceUuid(
                    new ParcelUuid(BleConnectionPriorityServerService.ADV_SERVICE_UUID)).build());
            ScanSettings setting = new ScanSettings.Builder()
                    .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY).build();
            mScanner.startScan(filter, setting, mScanCallback);
        }
    }

    private void stopScan() {
        if (DEBUG) {
            Log.d(TAG, "stopScan");
        }
        if (mScanner != null) {
            mScanner.stopScan(mScanCallback);
        }
    }

    private void notifyMismatchSecure() {
        Intent intent = new Intent(ACTION_BLUETOOTH_MISMATCH_SECURE);
        sendBroadcast(intent);
    }

    private void notifyMismatchInsecure() {
        Intent intent = new Intent(ACTION_BLUETOOTH_MISMATCH_INSECURE);
        sendBroadcast(intent);
    }

    private void notifyDisconnect() {
        Intent intent = new Intent(ACTION_FINISH_DISCONNECT);
        sendBroadcast(intent);
    }
}
