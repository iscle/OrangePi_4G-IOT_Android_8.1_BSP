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
import android.widget.Toast;

import java.util.Timer;
import java.util.TimerTask;
import java.util.UUID;

public class BleConnectionPriorityServerService extends Service {
    public static final boolean DEBUG = true;
    public static final String TAG = "BlePriorityServer";
    private static final String RESET_COUNT_VALUE = "RESET";
    private static final String START_VALUE = "START";
    private static final String STOP_VALUE = "STOP";
    public static final String CONNECTION_PRIORITY_HIGH = "PR_H";
    public static final String CONNECTION_PRIORITY_BALANCED = "PR_B";
    public static final String CONNECTION_PRIORITY_LOW_POWER = "PR_L";

    public static final String ACTION_BLUETOOTH_DISABLED =
            "com.android.cts.verifier.bluetooth.action.BLUETOOTH_DISABLED";

    public static final String ACTION_CONNECTION_WRITE_REQUEST =
            "com.android.cts.verifier.bluetooth.action.CONNECTION_WRITE_REQUEST";
    public static final String EXTRA_REQUEST_COUNT =
            "com.android.cts.verifier.bluetooth.intent.EXTRA_REQUEST_COUNT";
    public static final String ACTION_FINICH_CONNECTION_PRIORITY_HIGHT =
            "com.android.cts.verifier.bluetooth.action.ACTION_FINICH_CONNECTION_PRIORITY_HIGHT";
    public static final String ACTION_FINICH_CONNECTION_PRIORITY_BALANCED =
            "com.android.cts.verifier.bluetooth.action.ACTION_FINICH_CONNECTION_PRIORITY_BALANCED";
    public static final String ACTION_FINICH_CONNECTION_PRIORITY_LOW =
            "com.android.cts.verifier.bluetooth.action.ACTION_FINICH_CONNECTION_PRIORITY_LOW";

    public static final String ACTION_START_CONNECTION_PRIORITY_TEST =
            "com.android.cts.verifier.bluetooth.action.ACTION_START_CONNECTION_PRIORITY_TEST";

    public static final String EXTRA_AVERAGE =
            "com.android.cts.verifier.bluetooth.intent.EXTRA_AVERAGE";

    private static final UUID SERVICE_UUID =
            UUID.fromString("00009999-0000-1000-8000-00805f9b34fb");
    private static final UUID CHARACTERISTIC_UUID =
            UUID.fromString("00009998-0000-1000-8000-00805f9b34fb");
    private static final UUID START_CHARACTERISTIC_UUID =
            UUID.fromString("00009997-0000-1000-8000-00805f9b34fb");
    private static final UUID STOP_CHARACTERISTIC_UUID =
            UUID.fromString("00009995-0000-1000-8000-00805f9b34fb");
    private static final UUID DESCRIPTOR_UUID =
            UUID.fromString("00009996-0000-1000-8000-00805f9b34fb");
    public static final UUID ADV_SERVICE_UUID=
            UUID.fromString("00002222-0000-1000-8000-00805f9b34fb");

    private BluetoothManager mBluetoothManager;
    private BluetoothGattServer mGattServer;
    private BluetoothGattService mService;
    private BluetoothDevice mDevice;
    private Handler mHandler;
    private BluetoothLeAdvertiser mAdvertiser;
    private long mReceiveWriteCount;
    private Timer mTimeoutTimer;
    private TimerTask mTimeoutTimerTask;

    @Override
    public void onCreate() {
        super.onCreate();

        mBluetoothManager = (BluetoothManager) getSystemService(Context.BLUETOOTH_SERVICE);
        mAdvertiser = mBluetoothManager.getAdapter().getBluetoothLeAdvertiser();
        mGattServer = mBluetoothManager.openGattServer(this, mCallbacks);
        mService = createService();
        if ((mGattServer != null) && (mAdvertiser != null)) {
            mGattServer.addService(mService);
        }
        mDevice = null;
        mHandler = new Handler();

        if (!mBluetoothManager.getAdapter().isEnabled()) {
            notifyBluetoothDisabled();
        } else if (mGattServer == null) {
            notifyOpenFail();
        } else if (mAdvertiser == null) {
            notifyAdvertiseUnsupported();
        } else {
            startAdvertise();
        }
    }

    @Override
    public void onDestroy() {
        super.onDestroy();

        cancelTimeoutTimer(false);

        if (mTimeoutTimer != null) {
            mTimeoutTimer.cancel();
            mTimeoutTimer = null;
        }
        mTimeoutTimerTask = null;

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
        return START_NOT_STICKY;
    }

    private void notifyBluetoothDisabled() {
        if (DEBUG) {
            Log.d(TAG, "notifyBluetoothDisabled");
        }
        Intent intent = new Intent(ACTION_BLUETOOTH_DISABLED);
        sendBroadcast(intent);
    }

    private void notifyTestStart() {
        Intent intent = new Intent(BleConnectionPriorityServerService.ACTION_START_CONNECTION_PRIORITY_TEST);
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
        Intent intent = new Intent(ACTION_CONNECTION_WRITE_REQUEST);
        intent.putExtra(EXTRA_REQUEST_COUNT, String.valueOf(mReceiveWriteCount));
        sendBroadcast(intent);
    }

    private void showMessage(final String msg) {
        mHandler.post(new Runnable() {
            @Override
            public void run() {
                Toast.makeText(getApplicationContext(), msg, Toast.LENGTH_SHORT).show();
            }
        });
    }

    private synchronized void cancelTimeoutTimer(boolean runTimeout) {
        if (mTimeoutTimerTask != null) {
            mTimeoutTimer.cancel();
            if (runTimeout) {
                mTimeoutTimerTask.run();
            }
            mTimeoutTimerTask = null;
            mTimeoutTimer = null;
        }
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
        service.addCharacteristic(characteristic);
        characteristic = new BluetoothGattCharacteristic(START_CHARACTERISTIC_UUID, 0x0A, 0x11);
        characteristic.addDescriptor(descriptor);
        service.addCharacteristic(characteristic);
        characteristic = new BluetoothGattCharacteristic(STOP_CHARACTERISTIC_UUID, 0x0A, 0x11);
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
                    cancelTimeoutTimer(true);
                    notifyDisconnected();
                    mDevice = null;
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
            if (mGattServer == null) {
                if (DEBUG) {
                    Log.d(TAG, "GattServer is null, return");
                }
                return;
            }
            if (DEBUG) {
                Log.d(TAG, "onCharacteristicWriteRequest: preparedWrite=" + preparedWrite);
            }

            if (characteristic.getUuid().equals(START_CHARACTERISTIC_UUID)) {
                // time out if previous measurement is running
                cancelTimeoutTimer(true);

                mPriority = new String(value);
                Log.d(TAG, "Start Count Up. Priority is " + mPriority);
                if (BleConnectionPriorityServerService.CONNECTION_PRIORITY_HIGH.equals(mPriority)) {
                    notifyTestStart();
                }

                // start timeout timer
                mTimeoutTimer = new Timer(getClass().getName() + "_TimeoutTimer");
                mTimeoutTimerTask = new TimerTask() {
                    @Override
                    public void run() {
                        // measurement timed out
                        mTimeoutTimerTask = null;
                        mTimeoutTimer = null;
                        mReceiveWriteCount = 0;
                        notifyMeasurementFinished(mPriority, Long.MAX_VALUE);
                    }
                };
                mTimeoutTimer.schedule(mTimeoutTimerTask, (BleConnectionPriorityClientService.DEFAULT_PERIOD * 2));

                mReceiveWriteCount = 0;
            } else if (characteristic.getUuid().equals(STOP_CHARACTERISTIC_UUID)) {
                boolean isRunning = (mTimeoutTimerTask != null);
                cancelTimeoutTimer(false);

                String valeStr = new String(value);
                String priority = null;
                int writeCount = -1;
                int sep = valeStr.indexOf(",");
                if (sep > 0) {
                    priority = valeStr.substring(0, sep);
                    writeCount = Integer.valueOf(valeStr.substring(sep + 1));
                }

                if ((mPriority != null) && isRunning) {
                    if (mPriority.equals(priority)) {
                        long averageTime = BleConnectionPriorityClientService.DEFAULT_PERIOD / mReceiveWriteCount;
                        notifyMeasurementFinished(mPriority, averageTime);
                        Log.d(TAG, "Received " + mReceiveWriteCount + " of " + writeCount + " messages");
                    } else {
                        Log.d(TAG, "Connection priority does not match");
                        showMessage("Connection priority does not match");
                    }
                } else {
                    Log.d(TAG, "Not Start Count UP.");
                }
                mReceiveWriteCount = 0;
            } else {
                if (mTimeoutTimerTask != null) {
                    ++mReceiveWriteCount;
                }
                if (!preparedWrite) {
                    characteristic.setValue(value);
                }
            }

            if (responseNeeded) {
                mGattServer.sendResponse(device, requestId, BluetoothGatt.GATT_SUCCESS, 0, null);
            }
        }
    };

    private void notifyMeasurementFinished(String priority, long averageTime) {
        Intent intent = new Intent();
        intent.putExtra(EXTRA_AVERAGE, averageTime);
        switch (priority) {
            case CONNECTION_PRIORITY_HIGH:
                intent.setAction(ACTION_FINICH_CONNECTION_PRIORITY_HIGHT);
                break;
            case CONNECTION_PRIORITY_BALANCED:
                intent.setAction(ACTION_FINICH_CONNECTION_PRIORITY_BALANCED);
                break;
            case CONNECTION_PRIORITY_LOW_POWER:
                intent.setAction(ACTION_FINICH_CONNECTION_PRIORITY_LOW);
                break;
        }
        sendBroadcast(intent);
    }

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
