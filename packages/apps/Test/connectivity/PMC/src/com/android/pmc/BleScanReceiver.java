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
import android.bluetooth.le.BluetoothLeScanner;
import android.bluetooth.le.ScanCallback;
import android.bluetooth.le.ScanFilter;
import android.bluetooth.le.ScanResult;
import android.bluetooth.le.ScanSettings;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Bundle;
import android.os.SystemClock;
import android.util.Log;

import java.util.ArrayList;
import java.util.List;

/**
 * Bluetooth LE Receiver functions for power testing.
 */
public class BleScanReceiver extends BroadcastReceiver {
    public static final String TAG = "BLEPOWER";
    public static final String BLE_SCAN_INTENT = "com.android.pmc.BLESCAN";
    public static final int START_SCAN = 1;
    public static final int STOP_SCAN = 2;
    public static final int INIT_ALARM_NO = 1;
    private final Context mContext;
    private final AlarmManager mAlarmManager;
    private final BleScanListener mAlarmScanListener;
    private BluetoothLeScanner mBleScanner;
    private ScanSettings mScanSettings;
    private List<ScanFilter> mScanFilterList;
    // Use PMCStatusLogger to send status and start & end times back to Python client
    private PMCStatusLogger mPMCStatusLogger;
    // Test start time is set when receiving the broadcast message from Python client
    private long mStartTestTime;

    private ScanCallback mScanCallback = new ScanCallback() {
        @Override
        public void onScanResult(int callbackType, ScanResult result) {
            Log.e(TAG, "Bluetooth scan result: " + result.toString());
        }

        @Override
        public void onScanFailed(int errorCode) {
            Log.e(TAG, "Scan Failed: " + errorCode);
        }
    };

    /**
     * Class to provide callback for AlarmManager to start BLE scan alarms
     */
    public class BleScanListener extends BroadcastReceiver {

        public static final String BLESCAN =
                       "com.android.pmc.BLESCAN.ALARM";

        private int mScanTime;
        private int mNoScanTime;
        private int mNumAlarms;
        private int mFirstScanTime;
        private long mScanStartTime;
        private long mScanEndTime;

        /**
         * Constructor
         *
         */
        public BleScanListener() {
            Log.d(TAG, "Start BleScanListener()");
            BluetoothAdapter bleAdaptor = BluetoothAdapter.getDefaultAdapter();

            if (bleAdaptor == null) {
                Log.e(TAG, "BluetoothAdapter is Null");
                return;
            } else {
                if (!bleAdaptor.isEnabled()) {
                    Log.d(TAG, "BluetoothAdapter is NOT enabled, enable now");
                    bleAdaptor.enable();
                    if (!bleAdaptor.isEnabled()) {
                        Log.e(TAG, "Can't enable Bluetooth");
                        return;
                    }
                }
            }

            mBleScanner = bleAdaptor.getBluetoothLeScanner();
            mScanFilterList = new ArrayList<ScanFilter>();

            // Create ScanFilter object, to force scan even with screen OFF
            // using deviceName string of "dummy" for example
            ScanFilter scanFilterDeviceName = new ScanFilter.Builder().setDeviceName(
                       "dummy").build();
            // Add the object to FilterList
            mScanFilterList.add(scanFilterDeviceName);

            Log.d(TAG, "End BleScanListener()");
        }

        /**
         * Function to be called by BleScanReceiver to start
         * Initial Bluetooth scan alarm
         *
         * @param scanMode - scan mode
         * @param startTime - time when the first scan needs to be started
         * @param scanTime - time for the scan is lasted
         * @param noScanTime - time when the scan is stopped
         * @param numAlarms - number of alarms to start and to stop scan
         *
         */
        public void firstAlarm(int scanMode, int startTime, int scanTime,
                               int noScanTime, int numAlarms) {
            Log.d(TAG, "First Alarm for scan mode: " + scanMode);
            mScanTime = scanTime;
            mNoScanTime = noScanTime;
            mNumAlarms = numAlarms;
            mFirstScanTime = startTime;

            mScanSettings = new ScanSettings.Builder().setScanMode(
                                            scanMode).build();

            Intent alarmIntent = new Intent(BleScanListener.BLESCAN);
            alarmIntent.putExtra("com.android.pmc.BLESCAN.Action", START_SCAN);
            alarmIntent.putExtra("com.android.pmc.BLESCAN.CurrentAlarm", INIT_ALARM_NO);
            long triggerTime = SystemClock.elapsedRealtime() + startTime * 1000;
            mAlarmManager.setExactAndAllowWhileIdle(
                          AlarmManager.ELAPSED_REALTIME_WAKEUP, triggerTime,
                          PendingIntent.getBroadcast(mContext, 0,
                                        alarmIntent, PendingIntent.FLAG_UPDATE_CURRENT));
        }

        /**
         * Function to be called by onReceive() to start subsequent alarm
         *
         * @param intent - intent to get extra data
         * @param timeInterval - time for alarm to trigger next alarm
         * @param nextAction - next action for the alarm
         *
         */
        public void repeatAlarm(Intent intent, int timeInterval,
                                  int nextAction) {

            int currentAlarm = intent.getIntExtra("com.android.pmc.BLESCAN.CurrentAlarm", 0);
            Log.d(TAG, "repeatAlarm() currentAlarm: " + currentAlarm);
            if (currentAlarm == 0) {
                Log.d(TAG, "Received Alarm with no currentAlarm");
                return;
            }
            if (currentAlarm >= mNumAlarms) {
                mPMCStatusLogger.flash();  // To flash out timestamps into log file
                Log.d(TAG, "All alarms are done");
                return;
            }
            Log.d(TAG, "Next Action: " + nextAction);
            Intent alarmIntent = new Intent(BleScanListener.BLESCAN);
            alarmIntent.putExtra("com.android.pmc.BLESCAN.Action", nextAction);
            alarmIntent.putExtra("com.android.pmc.BLESCAN.CurrentAlarm", ++currentAlarm);
            long triggerTime = SystemClock.elapsedRealtime()
                                          + timeInterval * 1000;
            mAlarmManager.setExactAndAllowWhileIdle(
                          AlarmManager.ELAPSED_REALTIME_WAKEUP, triggerTime,
                          PendingIntent.getBroadcast(mContext, 0,
                          alarmIntent, PendingIntent.FLAG_UPDATE_CURRENT));
        }

        /**
         * Callback will be called for AlarmManager to start Bluetooth LE scan
         *
         * @param context - system will provide a context to this function
         * @param intent - system will provide an intent to this function
         */
        @Override
        public void onReceive(Context context, Intent intent) {
            if (!intent.getAction().equals(BLESCAN)) {
                return;
            }
            int action = intent.getIntExtra("com.android.pmc.BLESCAN.Action", 0);
            Log.d(TAG, "onReceive() Action: " + action);
            if (action == -1) {
                Log.e(TAG, "Received Alarm with no Action");
                return;
            }
            if (action == START_SCAN) {
                Log.v(TAG, "Before Start Scan");
                mScanStartTime = System.currentTimeMillis();
                mBleScanner.startScan(mScanFilterList, mScanSettings,
                                 mScanCallback);
                repeatAlarm(intent, mScanTime, STOP_SCAN);
            } else if (action == STOP_SCAN) {
                Log.v(TAG, "Before Stop scan");
                mScanEndTime = System.currentTimeMillis();
                mPMCStatusLogger.logAlarmTimes(mScanStartTime / 1000.0, mScanEndTime / 1000.0);
                mBleScanner.stopScan(mScanCallback);
                if ((mScanEndTime - mStartTestTime)
                        < ((mScanTime + mNoScanTime) * mNumAlarms / 2 + mFirstScanTime) * 1000) {
                    repeatAlarm(intent, mNoScanTime, START_SCAN);
                } else {
                    mPMCStatusLogger.flash();  // To flash out timestamps into log file
                    Log.d(TAG, "Time is up to end");
                }
            } else {
                Log.e(TAG, "Unknown Action");
            }
        }
    }

    /**
     * Constructor to be called by PMC
     *
     * @param context - PMC will provide a context
     * @param alarmManager - PMC will provide alarmManager
     */
    public BleScanReceiver(Context context, AlarmManager alarmManager) {
        // prepare for setting alarm service
        mContext = context;
        mAlarmManager = alarmManager;
        mAlarmScanListener = new BleScanListener();

        // RegisterAlarmReceiver for BleScanListener
        mContext.registerReceiver(mAlarmScanListener,
                new IntentFilter(BleScanListener.BLESCAN));

    }

    /**
     * Method to receive the broadcast from python client
     *
     * @param context - system will provide a context to this function
     * @param intent - system will provide an intent to this function
     */
    @Override
    public void onReceive(Context context, Intent intent) {
        if (intent.getAction().equals(BLE_SCAN_INTENT)) {
            Bundle extras = intent.getExtras();
            int scanMode = -1, startTime = 0, scanTime = 0, noScanTime = 0;
            int repetitions = 1;
            String str;

            mStartTestTime = System.currentTimeMillis();
            mPMCStatusLogger = new PMCStatusLogger(TAG + ".log", TAG);

            if (extras == null) {
                Log.e(TAG, "No parameters specified");
                return;
            }

            if (!extras.containsKey("ScanMode")) {
                Log.e(TAG, "No scan mode specified");
                return;
            }
            str = extras.getString("ScanMode");
            Log.d(TAG, "Scan Mode = " + str);
            scanMode = Integer.valueOf(str);

            if (!extras.containsKey("StartTime")) {
                Log.e(TAG, "No Start Time specified");
                return;
            }
            str = extras.getString("StartTime");
            Log.d(TAG, "Start Time = " + str);
            startTime = Integer.valueOf(str);

            if (!extras.containsKey("ScanTime")) {
                Log.e(TAG, "No Scan Time specified");
                return;
            }
            str = extras.getString("ScanTime");
            Log.d(TAG, "Scan Time = " + str);
            scanTime = Integer.valueOf(str);

            if (extras.containsKey("Repetitions")) {

                str = extras.getString("Repetitions");
                Log.d(TAG, "Repetitions = " + str);
                repetitions = Integer.valueOf(str);

                if (!extras.containsKey("NoScanTime")) {
                    Log.e(TAG, "No NoScan Time specified");
                    return;
                }
                str = extras.getString("NoScanTime");
                Log.d(TAG, "NoScan Time = " + str);
                noScanTime = Integer.valueOf(str);
            }
            if (scanTime == 0 || startTime == 0 || scanMode == -1) {
                Log.d(TAG, "Invalid paramters");
                return;
            }
            mAlarmScanListener.firstAlarm(scanMode, startTime,
                                       scanTime, noScanTime, repetitions * 2);
            if (mBleScanner != null && mScanFilterList != null && mScanSettings != null
                                 && mScanCallback != null) {
                mPMCStatusLogger.logStatus("READY");
            } else {
                Log.e(TAG, "BLE scanner is not ready to start test");
            }
        }
    }
}
