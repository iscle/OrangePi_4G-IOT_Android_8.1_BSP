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

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCallback;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattDescriptor;
import android.bluetooth.BluetoothGattService;
import android.bluetooth.BluetoothProfile;
import android.bluetooth.le.BluetoothLeScanner;
import android.bluetooth.le.ScanCallback;
import android.bluetooth.le.ScanFilter;
import android.bluetooth.le.ScanResult;
import android.bluetooth.le.ScanSettings;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.SystemClock;
import android.util.Log;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Class to provide Receiver for AlarmManager to start Gatt Client alarms
 */
public class GattClientListener extends BroadcastReceiver {

    public static final String TAG = "GATTC";
    public static final String GATTCLIENT_ALARM =
                           "com.android.pmc.GATTClient.ALARM";
    private static final int MILLSEC = 1000;
    private static final int INIT_VALUE = 0;
    private Context mContext;
    private final AlarmManager mAlarmManager;

    private BluetoothAdapter mBluetoothAdapter;

    private BluetoothGatt mBluetoothGatt;
    private GattCallback mGattCallback;

    private MyBleScanner mMyBleScanner;
    private String mMacAddress;
    private BluetoothDevice mDevice;
    private int mWriteTime;
    private int mIdleTime;
    private int mCycles;

    /**
     * Constructor
     * @param context - system will provide a context to this function
     * @param alarmManager - system will provide a AlarmManager to this function
     */
    public GattClientListener(Context context, AlarmManager alarmManager) {
        Log.d(TAG, "Start GattClientListener()");
        mContext = context;
        mAlarmManager = alarmManager;
        mBluetoothAdapter = BluetoothAdapter.getDefaultAdapter();

        if (mBluetoothAdapter == null) {
            Log.e(TAG, "BluetoothAdapter is Null");
            return;
        } else {
            if (!mBluetoothAdapter.isEnabled()) {
                Log.d(TAG, "BluetoothAdapter is NOT enabled, enable now");
                mBluetoothAdapter.enable();
                if (!mBluetoothAdapter.isEnabled()) {
                    Log.e(TAG, "Can't enable Bluetooth");
                    return;
                }
            }
        }

        mMyBleScanner = new MyBleScanner(mBluetoothAdapter);
        mGattCallback = new GattCallback();
        mBluetoothGatt = null;
        mMacAddress = null;
        mDevice = null;
        Log.d(TAG, "End GattClientListener");
    }

    /**
     * Function to be called to start alarm by PMC
     *
     * @param startTime - time (sec) when next GATT writing needs to be started
     * @param writeTime - how long (sec) to write GATT characteristic
     * @param idleTime - how long (sec) it doesn't need to wait
     * @param numCycles - how many of cycles of writing with idle time
     */
    public void startAlarm(int startTime, int writeTime, int idleTime, int numCycles,
                    Intent intent) {

        int currentAlarm = 0;

        if (intent == null) {
            // Start Scan here when this func is called for the first time
            mMyBleScanner.startScan();
            mWriteTime = writeTime;
            mIdleTime = idleTime;
            mCycles = numCycles;
        } else {
            // Get alarm number inside the intent
            currentAlarm = intent.getIntExtra("com.android.pmc.GATTClient.CurrentAlarm", 0);
        }
        Log.d(TAG, "Current Cycle Num: " + currentAlarm);
        if (currentAlarm >= mCycles) {
            Log.d(TAG, "All alarms are done");
            return;
        }

        Intent alarmIntent = new Intent(GattClientListener.GATTCLIENT_ALARM);
        alarmIntent.putExtra("com.android.pmc.GATTClient.CurrentAlarm", ++currentAlarm);

        long triggerTime = SystemClock.elapsedRealtime() + startTime * MILLSEC;
        mAlarmManager.setExactAndAllowWhileIdle(
                              AlarmManager.ELAPSED_REALTIME_WAKEUP, triggerTime,
                              PendingIntent.getBroadcast(mContext, 0,
                                            alarmIntent, PendingIntent.FLAG_UPDATE_CURRENT));
    }

    /**
     * Receive function will be called for AlarmManager to connect GATT
     *    and then to write characteristic
     *
     * @param context - system will provide a context to this function
     * @param intent - system will provide an intent to this function
     */
    @Override
    public void onReceive(Context context, Intent intent) {
        Log.d(TAG, "onReceiver: " + intent.getAction());
        if (!intent.getAction().equals(GATTCLIENT_ALARM)) {
            return;
        }

        if (mMacAddress == null) mMacAddress = mMyBleScanner.getAdvMacAddress();
        if (mMacAddress == null || mMacAddress.isEmpty()) {
            Log.e(TAG, "Remote device Mac Address is not set");
            return;
        }
        if (mDevice == null) mDevice = mBluetoothAdapter.getRemoteDevice(mMacAddress);

        if (mBluetoothGatt == null) {
            mBluetoothGatt = mDevice.connectGatt(mContext,
                        false, mGattCallback, BluetoothDevice.TRANSPORT_LE);
        } else {
            mBluetoothGatt.discoverServices();
        }
        // Start next alarm to connect again then to write
        startAlarm((mWriteTime + mIdleTime), mWriteTime, mIdleTime, mCycles, intent);
    }

    /**
     * Callback for GATT Writing
     */
    class GattCallback extends BluetoothGattCallback {

        public static final int MAX_MTU = 511;
        public static final int MAX_BYTES = 508;
        private long mStartWriteTime;

        GattCallback() {}

        @Override
        public void onConnectionStateChange(BluetoothGatt gatt, int status,
                    int newState) {
            Log.d(TAG, "onConnectionStateChange " + status);
            if (newState == BluetoothProfile.STATE_CONNECTED) {
                Log.d(TAG, "State Connected to mac address "
                            + gatt.getDevice().getAddress() + " status " + status);
                // Discover services in advertiser, callback will be called
                mBluetoothGatt.discoverServices();

            } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                Log.d(TAG, "State Disconnected from mac address "
                            + gatt.getDevice().getAddress() + " status " + status);
                try {
                    mBluetoothGatt.close();
                } catch (Exception e) {
                    Log.e(TAG, "Close Gatt: " + e);
                }
                mBluetoothGatt = null;

            } else if (newState == BluetoothProfile.STATE_CONNECTING) {
                Log.d(TAG, "State Connecting to mac address "
                            + gatt.getDevice().getAddress() + " status " + status);
            } else if (newState == BluetoothProfile.STATE_DISCONNECTING) {
                Log.d(TAG, "State Disconnecting from mac address "
                            + gatt.getDevice().getAddress() + " status " + status);
            }
        }

        @Override
        public void onServicesDiscovered(BluetoothGatt gatt, int status) {
            Log.d(TAG, "onServicesDiscovered Status " + status);
            mBluetoothGatt.requestMtu(MAX_MTU);
        }

        @Override
        public void onCharacteristicRead(BluetoothGatt gatt,
                BluetoothGattCharacteristic characteristic, int status) {
            Log.d(TAG, "onCharacteristicRead: " + status);
        }

        @Override
        public void onCharacteristicWrite(BluetoothGatt gatt,
                    BluetoothGattCharacteristic characteristic, int status) {
            Log.d(TAG, "onCharacteristicWrite: " + status);
            long timeElapse = SystemClock.elapsedRealtime() - mStartWriteTime;
            if (timeElapse < (mWriteTime * MILLSEC)) {
                writeCharacteristic(gatt, (int) (timeElapse / MILLSEC));
            }
        }

        @Override
        public void onCharacteristicChanged(BluetoothGatt gatt,
                    BluetoothGattCharacteristic characteristic) {
            Log.d(TAG, "onCharacteristicChanged");
        }

        @Override
        public void onDescriptorRead(BluetoothGatt gatt, BluetoothGattDescriptor descriptor,
                    int status) {
            Log.d(TAG, "onServicesDiscovered: " + status);
        }

        @Override
        public void onDescriptorWrite(BluetoothGatt gatt, BluetoothGattDescriptor descriptor,
                int status) {
            Log.d(TAG, "onDescriptorWrite: " + status);
        }

        @Override
        public void onReliableWriteCompleted(BluetoothGatt gatt, int status) {
            Log.d(TAG, "onReliableWriteCompleted: " + status);
        }

        @Override
        public void onReadRemoteRssi(BluetoothGatt gatt, int rssi, int status) {
            Log.d(TAG, "onReadRemoteRssi: " + rssi + " status: " + status);
        }

        @Override
        public void onMtuChanged(BluetoothGatt gatt, int mtu, int status) {
            // Every time it disconnects/reconnects it needs to re-set MTU
            Log.d(TAG, "onMtuChanged " + mtu + " status: " + status);
            // First time to write a characteristic to GATT server
            mStartWriteTime = SystemClock.elapsedRealtime();
            writeCharacteristic(gatt, INIT_VALUE);
        }

        /**
         * Function to be called to write a new GATT characteristic
         *
         * @param gatt - BluetoothGatt object to write
         * @param value - value to be set inside GATT characteristic
         */
        private void writeCharacteristic(BluetoothGatt gatt, int value) {
            UUID sUuid = UUID.fromString(GattServer.TEST_SERVICE_UUID);
            BluetoothGattService service = gatt.getService(sUuid);
            if (service == null) {
                Log.e(TAG, "service not found!");
                return;
            }
            UUID cUuid = UUID.fromString(GattServer.WRITABLE_CHAR_UUID);
            BluetoothGattCharacteristic characteristic = service.getCharacteristic(cUuid);

            if (characteristic == null) {
                Log.e(TAG, "Characteristic not found!");
                return;
            }

            byte[] byteValue = new byte[MAX_BYTES];

            for (int i = 0; i < MAX_BYTES; i++) {
                byteValue[i] = (byte) value;
            }

            characteristic.setValue(byteValue);
            characteristic.setWriteType(BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT);
            gatt.writeCharacteristic(characteristic);
        }
    }

    /**
     * Class to provide Ble Scanner functionalities
     */
    class MyBleScanner {

        private BluetoothLeScanner mBLEScanner;
        private ScanSettings mScanSettings;
        private List<ScanFilter> mScanFilterList;
        private MyScanCallback mScanCallback;
        private String mAdvMacAddress = null;

        /**
         * Constructor
         * @param context - system will provide a context to this function
         */
        MyBleScanner(BluetoothAdapter bluetoothAdapter) {

            mBLEScanner = bluetoothAdapter.getBluetoothLeScanner();
            mScanFilterList = new ArrayList<ScanFilter>();
            mScanSettings = new ScanSettings.Builder().setScanMode(
                                            ScanSettings.SCAN_MODE_LOW_LATENCY).build();
            mScanCallback = new MyScanCallback();
        }

        /**
         * Wrapper function to start BLE Scanning
         */
        public void startScan() {
            // Start Scan here when this func is called for the first time
            if (mBLEScanner != null) {
                mBLEScanner.startScan(mScanFilterList, mScanSettings, mScanCallback);
            } else {
                Log.e(TAG, "BLEScanner is null");
            }

        }

        /**
         * Wrapper function to stop BLE Scanning
         */
        public void stopScan() {
            if (mBLEScanner != null) {
                mBLEScanner.stopScan(mScanCallback);
            } else {
                Log.e(TAG, "BLEScanner is null");
            }
        }

        /**
         * function to get Mac Address for BLE Advertiser device
         */
        public String getAdvMacAddress() {
            // Return Mac address for Advertiser device
            return mAdvMacAddress;
        }

        /**
         * Class to provide callback for BLE Scanning
         */
        class MyScanCallback extends ScanCallback {

            @Override
            public void onScanResult(int callbackType, ScanResult result) {
                Log.d(TAG, "Bluetooth scan result: " + result.toString());
                BluetoothDevice device = result.getDevice();
                if (mAdvMacAddress == null) {
                    mAdvMacAddress = device.getAddress();
                    Log.d(TAG, "Bluetooth Address: " + mAdvMacAddress);
                }
                mBLEScanner.stopScan(mScanCallback);
                Log.d(TAG, "Bluetooth scan result: end ");
            }

            @Override
            public void onScanFailed(int errorCode) {
                Log.e(TAG, "Scan Failed: " + errorCode);
            }
        }
    }
}

