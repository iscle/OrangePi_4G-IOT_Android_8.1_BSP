/*
 * Copyright (C) 2014 The Android Open Source Project
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

package com.android.bluetooth.gatt;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.le.ScanCallback;
import android.bluetooth.le.ScanFilter;
import android.bluetooth.le.ScanSettings;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.hardware.display.DisplayManager;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;
import android.os.Message;
import android.os.RemoteException;
import android.os.ServiceManager;
import android.os.SystemClock;
import android.util.Log;
import android.view.Display;

import com.android.bluetooth.Utils;
import com.android.bluetooth.btservice.AdapterService;

import java.util.ArrayDeque;
import java.util.Collections;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/**
 * Class that handles Bluetooth LE scan related operations.
 *
 * @hide
 */
public class ScanManager {
    private static final boolean DBG = GattServiceConfig.DBG;
    private static final String TAG = GattServiceConfig.TAG_PREFIX + "ScanManager";

    // Result type defined in bt stack. Need to be accessed by GattService.
    static final int SCAN_RESULT_TYPE_TRUNCATED = 1;
    static final int SCAN_RESULT_TYPE_FULL = 2;
    static final int SCAN_RESULT_TYPE_BOTH = 3;

    // Internal messages for handling BLE scan operations.
    private static final int MSG_START_BLE_SCAN = 0;
    private static final int MSG_STOP_BLE_SCAN = 1;
    private static final int MSG_FLUSH_BATCH_RESULTS = 2;
    private static final int MSG_SCAN_TIMEOUT = 3;
    private static final int MSG_SUSPEND_SCANS = 4;
    private static final int MSG_RESUME_SCANS = 5;
    private static final String ACTION_REFRESH_BATCHED_SCAN =
            "com.android.bluetooth.gatt.REFRESH_BATCHED_SCAN";

    // Timeout for each controller operation.
    private static final int OPERATION_TIME_OUT_MILLIS = 500;

    private int mLastConfiguredScanSetting = Integer.MIN_VALUE;
    // Scan parameters for batch scan.
    private BatchScanParams mBatchScanParms;

    private Integer curUsedTrackableAdvertisements;
    private GattService mService;
    private BroadcastReceiver mBatchAlarmReceiver;
    private boolean mBatchAlarmReceiverRegistered;
    private ScanNative mScanNative;
    private ClientHandler mHandler;

    private Set<ScanClient> mRegularScanClients;
    private Set<ScanClient> mBatchClients;
    private Set<ScanClient> mSuspendedScanClients;

    private CountDownLatch mLatch;

    private DisplayManager mDm;

    ScanManager(GattService service) {
        mRegularScanClients = Collections.newSetFromMap(new ConcurrentHashMap<ScanClient, Boolean>());
        mBatchClients = Collections.newSetFromMap(new ConcurrentHashMap<ScanClient, Boolean>());
        mSuspendedScanClients =
                Collections.newSetFromMap(new ConcurrentHashMap<ScanClient, Boolean>());
        mService = service;
        mScanNative = new ScanNative();
        curUsedTrackableAdvertisements = 0;
        mDm = (DisplayManager) mService.getSystemService(Context.DISPLAY_SERVICE);
    }

    void start() {
        HandlerThread thread = new HandlerThread("BluetoothScanManager");
        thread.start();
        mHandler = new ClientHandler(thread.getLooper());
        if (mDm != null) {
            mDm.registerDisplayListener(mDisplayListener, null);
        }
    }

    void cleanup() {
        mRegularScanClients.clear();
        mBatchClients.clear();
        mSuspendedScanClients.clear();
        mScanNative.cleanup();

        if (mDm != null) {
            mDm.unregisterDisplayListener(mDisplayListener);
        }

        if (mHandler != null) {
            // Shut down the thread
            mHandler.removeCallbacksAndMessages(null);
            Looper looper = mHandler.getLooper();
            if (looper != null) {
                looper.quit();
            }
            mHandler = null;
        }
    }

    void registerScanner(UUID uuid) {
        mScanNative.registerScannerNative(
            uuid.getLeastSignificantBits(), uuid.getMostSignificantBits());
    }

    void unregisterScanner(int scannerId) {
        mScanNative.unregisterScannerNative(scannerId);
    }

    /**
     * Returns the regular scan queue.
     */
    Set<ScanClient> getRegularScanQueue() {
        return mRegularScanClients;
    }

    /**
     * Returns batch scan queue.
     */
    Set<ScanClient> getBatchScanQueue() {
        return mBatchClients;
    }

    /**
     * Returns a set of full batch scan clients.
     */
    Set<ScanClient> getFullBatchScanQueue() {
        // TODO: split full batch scan clients and truncated batch clients so we don't need to
        // construct this every time.
        Set<ScanClient> fullBatchClients = new HashSet<ScanClient>();
        for (ScanClient client : mBatchClients) {
            if (client.settings.getScanResultType() == ScanSettings.SCAN_RESULT_TYPE_FULL) {
                fullBatchClients.add(client);
            }
        }
        return fullBatchClients;
    }

    void startScan(ScanClient client) {
        sendMessage(MSG_START_BLE_SCAN, client);
    }

    void stopScan(ScanClient client) {
        sendMessage(MSG_STOP_BLE_SCAN, client);
    }

    void flushBatchScanResults(ScanClient client) {
        sendMessage(MSG_FLUSH_BATCH_RESULTS, client);
    }

    void callbackDone(int scannerId, int status) {
        if (DBG) Log.d(TAG, "callback done for scannerId - " + scannerId + " status - " + status);
        if (status == 0) {
            mLatch.countDown();
        }
        // TODO: add a callback for scan failure.
    }

    private void sendMessage(int what, ScanClient client) {
        Message message = new Message();
        message.what = what;
        message.obj = client;
        mHandler.sendMessage(message);
    }

    private boolean isFilteringSupported() {
        BluetoothAdapter adapter = BluetoothAdapter.getDefaultAdapter();
        return adapter.isOffloadedFilteringSupported();
    }

    // Handler class that handles BLE scan operations.
    private class ClientHandler extends Handler {

        ClientHandler(Looper looper) {
            super(looper);
        }

        @Override
        public void handleMessage(Message msg) {
            ScanClient client = (ScanClient) msg.obj;
            switch (msg.what) {
                case MSG_START_BLE_SCAN:
                    handleStartScan(client);
                    break;
                case MSG_STOP_BLE_SCAN:
                    handleStopScan(client);
                    break;
                case MSG_FLUSH_BATCH_RESULTS:
                    handleFlushBatchResults(client);
                    break;
                case MSG_SCAN_TIMEOUT:
                    mScanNative.regularScanTimeout(client);
                    break;
                case MSG_SUSPEND_SCANS:
                    handleSuspendScans();
                    break;
                case MSG_RESUME_SCANS:
                    handleResumeScans();
                    break;
                default:
                    // Shouldn't happen.
                    Log.e(TAG, "received an unkown message : " + msg.what);
            }
        }

        void handleStartScan(ScanClient client) {
            Utils.enforceAdminPermission(mService);
            boolean isFiltered = (client.filters != null) && !client.filters.isEmpty();
            if (DBG) Log.d(TAG, "handling starting scan");

            if (!isScanSupported(client)) {
                Log.e(TAG, "Scan settings not supported");
                return;
            }

            if (mRegularScanClients.contains(client) || mBatchClients.contains(client)) {
                Log.e(TAG, "Scan already started");
                return;
            }

            if (!mScanNative.isOpportunisticScanClient(client) && !isScreenOn() && !isFiltered) {
                Log.e(TAG,
                        "Cannot start unfiltered scan in screen-off. This scan will be resumed later: "
                                + client.scannerId);
                mSuspendedScanClients.add(client);
                return;
            }
            /** M: Bug fix. Remote app may die for reasons such as low memory.
                   So check if the app is still alive before start scan @{ */
            if (mService.mScannerMap.getById(client.scannerId) == null)
                return;
            /** @} */

            // Begin scan operations.
            if (isBatchClient(client)) {
                mBatchClients.add(client);
                mScanNative.startBatchScan(client);
            } else {
                mRegularScanClients.add(client);
                mScanNative.startRegularScan(client);
                if (!mScanNative.isOpportunisticScanClient(client)) {
                    mScanNative.configureRegularScanParams();

                    if (!mScanNative.isExemptFromScanDowngrade(client)) {
                        Message msg = mHandler.obtainMessage(MSG_SCAN_TIMEOUT);
                        msg.obj = client;
                        // Only one timeout message should exist at any time
                        mHandler.sendMessageDelayed(msg, AppScanStats.SCAN_TIMEOUT_MS);
                    }
                }
            }
        }

        void handleStopScan(ScanClient client) {
            Utils.enforceAdminPermission(mService);
            if (client == null) return;

            if (mSuspendedScanClients.contains(client)) {
                mSuspendedScanClients.remove(client);
            }

            if (mRegularScanClients.contains(client)) {
                /** M: Bug fix.  The ScanClient passed in may just holds the scannerId.
                       We retrieve the real client, which have correct scan parameters.  @{ */
                ScanClient workClient = mScanNative.getRegularScanClient(client.scannerId);
                /** @} */
                mScanNative.stopRegularScan(workClient);

                if (mScanNative.numRegularScanClients() == 0) {
                    mHandler.removeMessages(MSG_SCAN_TIMEOUT);
                }

                if (!mScanNative.isOpportunisticScanClient(workClient)) {
                    mScanNative.configureRegularScanParams();
                }
            } else {
                /** M: Bug fix.  The ScanClient passed in may just holds the scannerId.
                       We retrieve the real client, which have correct scan parameters.  @{ */
                if (mBatchClients.contains(client)) {
                    ScanClient workClient = mScanNative.getBatchScanClient(client.scannerId);
                    mScanNative.stopBatchScan(workClient);
                }
                /** @} */
            }
            if (client.appDied) {
                if (DBG) Log.d(TAG, "app died, unregister scanner - " + client.scannerId);
                mService.unregisterScanner(client.scannerId);
            }
        }

        void handleFlushBatchResults(ScanClient client) {
            Utils.enforceAdminPermission(mService);
            if (!mBatchClients.contains(client)) {
                return;
            }
            mScanNative.flushBatchResults(client.scannerId);
        }

        private boolean isBatchClient(ScanClient client) {
            if (client == null || client.settings == null) {
                return false;
            }
            ScanSettings settings = client.settings;
            return settings.getCallbackType() == ScanSettings.CALLBACK_TYPE_ALL_MATCHES &&
                    settings.getReportDelayMillis() != 0;
        }

        private boolean isScanSupported(ScanClient client) {
            if (client == null || client.settings == null) {
                return true;
            }
            ScanSettings settings = client.settings;
            if (isFilteringSupported()) {
                return true;
            }
            return settings.getCallbackType() == ScanSettings.CALLBACK_TYPE_ALL_MATCHES &&
                    settings.getReportDelayMillis() == 0;
        }

        void handleSuspendScans() {
            for (ScanClient client : mRegularScanClients) {
                if (!mScanNative.isOpportunisticScanClient(client)
                        && (client.filters == null || client.filters.isEmpty())) {
                    /*Suspend unfiltered scans*/
                    if (client.stats != null) {
                        client.stats.recordScanSuspend(client.scannerId);
                    }
                    handleStopScan(client);
                    mSuspendedScanClients.add(client);
                }
            }
        }

        void handleResumeScans() {
            for (ScanClient client : mSuspendedScanClients) {
                if (client.stats != null) {
                    client.stats.recordScanResume(client.scannerId);
                }
                handleStartScan(client);
            }
            mSuspendedScanClients.clear();
        }
    }

    /**
     * Parameters for batch scans.
     */
    class BatchScanParams {
        int scanMode;
        int fullScanscannerId;
        int truncatedScanscannerId;

        BatchScanParams() {
            scanMode = -1;
            fullScanscannerId = -1;
            truncatedScanscannerId = -1;
        }

        @Override
        public boolean equals(Object obj) {
            if (this == obj) {
                return true;
            }
            if (obj == null || getClass() != obj.getClass()) {
                return false;
            }
            BatchScanParams other = (BatchScanParams) obj;
            return scanMode == other.scanMode && fullScanscannerId == other.fullScanscannerId
                    && truncatedScanscannerId == other.truncatedScanscannerId;

        }
    }

    public int getCurrentUsedTrackingAdvertisement() {
        return curUsedTrackableAdvertisements;
    }

    private class ScanNative {

        // Delivery mode defined in bt stack.
        private static final int DELIVERY_MODE_IMMEDIATE = 0;
        private static final int DELIVERY_MODE_ON_FOUND_LOST = 1;
        private static final int DELIVERY_MODE_BATCH = 2;

        private static final int ONFOUND_SIGHTINGS_AGGRESSIVE = 1;
        private static final int ONFOUND_SIGHTINGS_STICKY = 4;

        private static final int ALL_PASS_FILTER_INDEX_REGULAR_SCAN = 1;
        private static final int ALL_PASS_FILTER_INDEX_BATCH_SCAN = 2;
        private static final int ALL_PASS_FILTER_SELECTION = 0;

        private static final int DISCARD_OLDEST_WHEN_BUFFER_FULL = 0;

        /**
         * Scan params corresponding to regular scan setting
         */
        private static final int SCAN_MODE_LOW_POWER_WINDOW_MS = 500;
        private static final int SCAN_MODE_LOW_POWER_INTERVAL_MS = 5000;
        private static final int SCAN_MODE_BALANCED_WINDOW_MS = 2000;
        private static final int SCAN_MODE_BALANCED_INTERVAL_MS = 5000;
        private static final int SCAN_MODE_LOW_LATENCY_WINDOW_MS = 5000;
        private static final int SCAN_MODE_LOW_LATENCY_INTERVAL_MS = 5000;

        /**
         * Onfound/onlost for scan settings
         */
        private static final int MATCH_MODE_AGGRESSIVE_TIMEOUT_FACTOR = (1);
        private static final int MATCH_MODE_STICKY_TIMEOUT_FACTOR = (3);
        private static final int ONLOST_FACTOR = 2;
        private static final int ONLOST_ONFOUND_BASE_TIMEOUT_MS = 500;

        /**
         * Scan params corresponding to batch scan setting
         */
        private static final int SCAN_MODE_BATCH_LOW_POWER_WINDOW_MS = 1500;
        private static final int SCAN_MODE_BATCH_LOW_POWER_INTERVAL_MS = 150000;
        private static final int SCAN_MODE_BATCH_BALANCED_WINDOW_MS = 1500;
        private static final int SCAN_MODE_BATCH_BALANCED_INTERVAL_MS = 15000;
        private static final int SCAN_MODE_BATCH_LOW_LATENCY_WINDOW_MS = 1500;
        private static final int SCAN_MODE_BATCH_LOW_LATENCY_INTERVAL_MS = 5000;

        // The logic is AND for each filter field.
        private static final int LIST_LOGIC_TYPE = 0x1111111;
        private static final int FILTER_LOGIC_TYPE = 1;
        // Filter indices that are available to user. It's sad we need to maintain filter index.
        private final Deque<Integer> mFilterIndexStack;
        // Map of scannerId and Filter indices used by client.
        private final Map<Integer, Deque<Integer>> mClientFilterIndexMap;
        // Keep track of the clients that uses ALL_PASS filters.
        private final Set<Integer> mAllPassRegularClients = new HashSet<>();
        private final Set<Integer> mAllPassBatchClients = new HashSet<>();

        private AlarmManager mAlarmManager;
        private PendingIntent mBatchScanIntervalIntent;

        ScanNative() {
            mFilterIndexStack = new ArrayDeque<Integer>();
            mClientFilterIndexMap = new HashMap<Integer, Deque<Integer>>();

            mAlarmManager = (AlarmManager) mService.getSystemService(Context.ALARM_SERVICE);
            Intent batchIntent = new Intent(ACTION_REFRESH_BATCHED_SCAN, null);
            mBatchScanIntervalIntent = PendingIntent.getBroadcast(mService, 0, batchIntent, 0);
            IntentFilter filter = new IntentFilter();
            filter.addAction(ACTION_REFRESH_BATCHED_SCAN);
            mBatchAlarmReceiver = new BroadcastReceiver() {
                    @Override
                public void onReceive(Context context, Intent intent) {
                    Log.d(TAG, "awakened up at time " + SystemClock.elapsedRealtime());
                    String action = intent.getAction();

                    if (action.equals(ACTION_REFRESH_BATCHED_SCAN)) {
                        if (mBatchClients.isEmpty()) {
                            return;
                        }
                        // Note this actually flushes all pending batch data.
                        flushBatchScanResults(mBatchClients.iterator().next());
                    }
                }
            };
            mService.registerReceiver(mBatchAlarmReceiver, filter);
            mBatchAlarmReceiverRegistered = true;
        }

        private void resetCountDownLatch() {
            mLatch = new CountDownLatch(1);
        }

        // Returns true if mLatch reaches 0, false if timeout or interrupted.
        private boolean waitForCallback() {
            try {
                return mLatch.await(OPERATION_TIME_OUT_MILLIS, TimeUnit.MILLISECONDS);
            } catch (InterruptedException e) {
                return false;
            }
        }

        void configureRegularScanParams() {
            if (DBG) {
                Log.d(TAG, "configureRegularScanParams() - queue=" + mRegularScanClients.size());
            }
            int curScanSetting = Integer.MIN_VALUE;
            ScanClient client = getAggressiveClient(mRegularScanClients);
            if (client != null) {
                curScanSetting = client.settings.getScanMode();
            }

            if (DBG) {
                Log.d(TAG, "configureRegularScanParams() - ScanSetting Scan mode=" + curScanSetting
                                + " mLastConfiguredScanSetting=" + mLastConfiguredScanSetting);
            }

            if (curScanSetting != Integer.MIN_VALUE &&
                    curScanSetting != ScanSettings.SCAN_MODE_OPPORTUNISTIC) {
                if (curScanSetting != mLastConfiguredScanSetting) {
                    int scanWindow = getScanWindowMillis(client.settings);
                    int scanInterval = getScanIntervalMillis(client.settings);
                    // convert scanWindow and scanInterval from ms to LE scan units(0.625ms)
                    scanWindow = Utils.millsToUnit(scanWindow);
                    scanInterval = Utils.millsToUnit(scanInterval);
                    gattClientScanNative(false);
                    if (DBG) {
                        Log.d(TAG, "configureRegularScanParams - scanInterval = " + scanInterval
                                        + "configureRegularScanParams - scanWindow = "
                                        + scanWindow);
                    }
                    gattSetScanParametersNative(client.scannerId, scanInterval, scanWindow);
                    gattClientScanNative(true);
                    mLastConfiguredScanSetting = curScanSetting;
                }
            } else {
                mLastConfiguredScanSetting = curScanSetting;
                if (DBG) Log.d(TAG, "configureRegularScanParams() - queue emtpy, scan stopped");
            }
        }

        ScanClient getAggressiveClient(Set<ScanClient> cList) {
            ScanClient result = null;
            int curScanSetting = Integer.MIN_VALUE;
            for (ScanClient client : cList) {
                // ScanClient scan settings are assumed to be monotonically increasing in value for
                // more power hungry(higher duty cycle) operation.
                if (client.settings.getScanMode() > curScanSetting) {
                    result = client;
                    curScanSetting = client.settings.getScanMode();
                }
            }
            return result;
        }

        void startRegularScan(ScanClient client) {
            if (isFilteringSupported() && mFilterIndexStack.isEmpty()
                    && mClientFilterIndexMap.isEmpty()) {
                initFilterIndexStack();
            }
            if (isFilteringSupported()) {
                configureScanFilters(client);
            }
            // Start scan native only for the first client.
            if (numRegularScanClients() == 1) {
                gattClientScanNative(true);
            }
        }

        private int numRegularScanClients() {
            int num = 0;
            for (ScanClient client: mRegularScanClients) {
                if (client.settings.getScanMode() != ScanSettings.SCAN_MODE_OPPORTUNISTIC) {
                    num++;
                }
            }
            return num;
        }

        void startBatchScan(ScanClient client) {
            if (mFilterIndexStack.isEmpty() && isFilteringSupported()) {
                initFilterIndexStack();
            }
            configureScanFilters(client);
            if (!isOpportunisticScanClient(client)) {
                // Reset batch scan. May need to stop the existing batch scan and update scan params.
                resetBatchScan(client);
            }
        }

        private boolean isExemptFromScanDowngrade(ScanClient client) {
          return isOpportunisticScanClient(client)
              || isFirstMatchScanClient(client)
              || !shouldUseAllPassFilter(client);
        }

        private boolean isOpportunisticScanClient(ScanClient client) {
            return client.settings.getScanMode() == ScanSettings.SCAN_MODE_OPPORTUNISTIC;
        }

        private boolean isFirstMatchScanClient(ScanClient client) {
            return (client.settings.getCallbackType() & ScanSettings.CALLBACK_TYPE_FIRST_MATCH) != 0;
        }

        private void resetBatchScan(ScanClient client) {
            int scannerId = client.scannerId;
            BatchScanParams batchScanParams = getBatchScanParams();
            // Stop batch if batch scan params changed and previous params is not null.
            if (mBatchScanParms != null && (!mBatchScanParms.equals(batchScanParams))) {
                if (DBG) Log.d(TAG, "stopping BLe Batch");
                resetCountDownLatch();
                gattClientStopBatchScanNative(scannerId);
                waitForCallback();
                // Clear pending results as it's illegal to config storage if there are still
                // pending results.
                flushBatchResults(scannerId);
            }
            // Start batch if batchScanParams changed and current params is not null.
            if (batchScanParams != null && (!batchScanParams.equals(mBatchScanParms))) {
                int notifyThreshold = 95;
                if (DBG) Log.d(TAG, "Starting BLE batch scan");
                int resultType = getResultType(batchScanParams);
                int fullScanPercent = getFullScanStoragePercent(resultType);
                resetCountDownLatch();
                if (DBG) Log.d(TAG, "configuring batch scan storage, appIf " + client.scannerId);
                gattClientConfigBatchScanStorageNative(client.scannerId, fullScanPercent,
                        100 - fullScanPercent, notifyThreshold);
                waitForCallback();
                resetCountDownLatch();
                int scanInterval =
                        Utils.millsToUnit(getBatchScanIntervalMillis(batchScanParams.scanMode));
                int scanWindow =
                        Utils.millsToUnit(getBatchScanWindowMillis(batchScanParams.scanMode));
                gattClientStartBatchScanNative(scannerId, resultType, scanInterval,
                        scanWindow, 0, DISCARD_OLDEST_WHEN_BUFFER_FULL);
                waitForCallback();
            }
            mBatchScanParms = batchScanParams;
            setBatchAlarm();
        }

        private int getFullScanStoragePercent(int resultType) {
            switch (resultType) {
                case SCAN_RESULT_TYPE_FULL:
                    return 100;
                case SCAN_RESULT_TYPE_TRUNCATED:
                    return 0;
                case SCAN_RESULT_TYPE_BOTH:
                    return 50;
                default:
                    return 50;
            }
        }

        private BatchScanParams getBatchScanParams() {
            if (mBatchClients.isEmpty()) {
                return null;
            }
            BatchScanParams params = new BatchScanParams();
            // TODO: split full batch scan results and truncated batch scan results to different
            // collections.
            for (ScanClient client : mBatchClients) {
                params.scanMode = Math.max(params.scanMode, client.settings.getScanMode());
                if (client.settings.getScanResultType() == ScanSettings.SCAN_RESULT_TYPE_FULL) {
                    params.fullScanscannerId = client.scannerId;
                } else {
                    params.truncatedScanscannerId = client.scannerId;
                }
            }
            return params;
        }

        private int getBatchScanWindowMillis(int scanMode) {
            switch (scanMode) {
                case ScanSettings.SCAN_MODE_LOW_LATENCY:
                    return SCAN_MODE_BATCH_LOW_LATENCY_WINDOW_MS;
                case ScanSettings.SCAN_MODE_BALANCED:
                    return SCAN_MODE_BATCH_BALANCED_WINDOW_MS;
                case ScanSettings.SCAN_MODE_LOW_POWER:
                    return SCAN_MODE_BATCH_LOW_POWER_WINDOW_MS;
                default:
                    return SCAN_MODE_BATCH_LOW_POWER_WINDOW_MS;
            }
        }

        private int getBatchScanIntervalMillis(int scanMode) {
            switch (scanMode) {
                case ScanSettings.SCAN_MODE_LOW_LATENCY:
                    return SCAN_MODE_BATCH_LOW_LATENCY_INTERVAL_MS;
                case ScanSettings.SCAN_MODE_BALANCED:
                    return SCAN_MODE_BATCH_BALANCED_INTERVAL_MS;
                case ScanSettings.SCAN_MODE_LOW_POWER:
                    return SCAN_MODE_BATCH_LOW_POWER_INTERVAL_MS;
                default:
                    return SCAN_MODE_BATCH_LOW_POWER_INTERVAL_MS;
            }
        }

        // Set the batch alarm to be triggered within a short window after batch interval. This
        // allows system to optimize wake up time while still allows a degree of precise control.
        private void setBatchAlarm() {
            // Cancel any pending alarm just in case.
            mAlarmManager.cancel(mBatchScanIntervalIntent);
            if (mBatchClients.isEmpty()) {
                return;
            }
            long batchTriggerIntervalMillis = getBatchTriggerIntervalMillis();
            // Allows the alarm to be triggered within
            // [batchTriggerIntervalMillis, 1.1 * batchTriggerIntervalMillis]
            long windowLengthMillis = batchTriggerIntervalMillis / 10;
            long windowStartMillis = SystemClock.elapsedRealtime() + batchTriggerIntervalMillis;
            mAlarmManager.setWindow(AlarmManager.ELAPSED_REALTIME_WAKEUP,
                    windowStartMillis, windowLengthMillis,
                    mBatchScanIntervalIntent);
        }

        void stopRegularScan(ScanClient client) {
            // Remove scan filters and recycle filter indices.
            if (client == null) return;
            int deliveryMode = getDeliveryMode(client);
            if (deliveryMode == DELIVERY_MODE_ON_FOUND_LOST) {
                for (ScanFilter filter : client.filters) {
                    int entriesToFree = getNumOfTrackingAdvertisements(client.settings);
                    if (!manageAllocationOfTrackingAdvertisement(entriesToFree, false)) {
                        Log.e(TAG, "Error freeing for onfound/onlost filter resources "
                                    + entriesToFree);
                        try {
                            mService.onScanManagerErrorCallback(client.scannerId,
                                            ScanCallback.SCAN_FAILED_INTERNAL_ERROR);
                        } catch (RemoteException e) {
                            Log.e(TAG, "failed on onScanManagerCallback at freeing", e);
                        }
                    }
                }
            }
            mRegularScanClients.remove(client);
            if (numRegularScanClients() == 0) {
                if (DBG) Log.d(TAG, "stop scan");
                gattClientScanNative(false);
            }
            removeScanFilters(client.scannerId);
        }

        void regularScanTimeout(ScanClient client) {
            if (!isExemptFromScanDowngrade(client) && client.stats.isScanningTooLong()) {
                Log.w(TAG,
                        "Moving scan client to opportunistic (scannerId " + client.scannerId + ")");
                setOpportunisticScanClient(client);
                removeScanFilters(client.scannerId);
                client.stats.setScanTimeout(client.scannerId);
            }

            // The scan should continue for background scans
            configureRegularScanParams();
            if (numRegularScanClients() == 0) {
                if (DBG) Log.d(TAG, "stop scan");
                gattClientScanNative(false);
            }
        }

        void setOpportunisticScanClient(ScanClient client) {
            // TODO: Add constructor to ScanSettings.Builder
            // that can copy values from an existing ScanSettings object
            ScanSettings.Builder builder = new ScanSettings.Builder();
            ScanSettings settings = client.settings;
            builder.setScanMode(ScanSettings.SCAN_MODE_OPPORTUNISTIC);
            builder.setCallbackType(settings.getCallbackType());
            builder.setScanResultType(settings.getScanResultType());
            builder.setReportDelay(settings.getReportDelayMillis());
            builder.setNumOfMatches(settings.getNumOfMatches());
            client.settings = builder.build();
        }

        // Find the regular scan client information.
        ScanClient getRegularScanClient(int scannerId) {
            for (ScanClient client : mRegularScanClients) {
              if (client.scannerId == scannerId) return client;
            }
            return null;
        }

        void stopBatchScan(ScanClient client) {
            mBatchClients.remove(client);
            removeScanFilters(client.scannerId);
            if (!isOpportunisticScanClient(client)) {
                resetBatchScan(client);
            }
        }

        void flushBatchResults(int scannerId) {
            if (DBG) Log.d(TAG, "flushPendingBatchResults - scannerId = " + scannerId);
            if (mBatchScanParms.fullScanscannerId != -1) {
                resetCountDownLatch();
                gattClientReadScanReportsNative(mBatchScanParms.fullScanscannerId,
                        SCAN_RESULT_TYPE_FULL);
                waitForCallback();
            }
            if (mBatchScanParms.truncatedScanscannerId != -1) {
                resetCountDownLatch();
                gattClientReadScanReportsNative(mBatchScanParms.truncatedScanscannerId,
                        SCAN_RESULT_TYPE_TRUNCATED);
                waitForCallback();
            }
            setBatchAlarm();
        }

        void cleanup() {
            mAlarmManager.cancel(mBatchScanIntervalIntent);
            // Protect against multiple calls of cleanup.
            if (mBatchAlarmReceiverRegistered) {
                mService.unregisterReceiver(mBatchAlarmReceiver);
            }
            mBatchAlarmReceiverRegistered = false;
        }

        private long getBatchTriggerIntervalMillis() {
            long intervalMillis = Long.MAX_VALUE;
            for (ScanClient client : mBatchClients) {
                if (client.settings != null && client.settings.getReportDelayMillis() > 0) {
                    intervalMillis = Math.min(intervalMillis,
                            client.settings.getReportDelayMillis());
                }
            }
            return intervalMillis;
        }

        // Add scan filters. The logic is:
        // If no offload filter can/needs to be set, set ALL_PASS filter.
        // Otherwise offload all filters to hardware and enable all filters.
        private void configureScanFilters(ScanClient client) {
            int scannerId = client.scannerId;
            int deliveryMode = getDeliveryMode(client);
            int trackEntries = 0;

            // Do not add any filters set by opportunistic scan clients
            if (isOpportunisticScanClient(client)) {
                return;
            }

            if (!shouldAddAllPassFilterToController(client, deliveryMode)) {
                return;
            }

            resetCountDownLatch();
            gattClientScanFilterEnableNative(scannerId, true);
            waitForCallback();

            if (shouldUseAllPassFilter(client)) {
                int filterIndex = (deliveryMode == DELIVERY_MODE_BATCH) ?
                        ALL_PASS_FILTER_INDEX_BATCH_SCAN : ALL_PASS_FILTER_INDEX_REGULAR_SCAN;
                resetCountDownLatch();
                // Don't allow Onfound/onlost with all pass
                configureFilterParamter(scannerId, client, ALL_PASS_FILTER_SELECTION,
                                filterIndex, 0);
                waitForCallback();
            } else {
                Deque<Integer> clientFilterIndices = new ArrayDeque<Integer>();
                for (ScanFilter filter : client.filters) {
                    ScanFilterQueue queue = new ScanFilterQueue();
                    queue.addScanFilter(filter);
                    int featureSelection = queue.getFeatureSelection();
                    int filterIndex = mFilterIndexStack.pop();
                    while (!queue.isEmpty()) {
                        resetCountDownLatch();
                        addFilterToController(scannerId, queue.pop(), filterIndex);
                        waitForCallback();
                    }
                    resetCountDownLatch();
                    if (deliveryMode == DELIVERY_MODE_ON_FOUND_LOST) {
                        trackEntries = getNumOfTrackingAdvertisements(client.settings);
                        if (!manageAllocationOfTrackingAdvertisement(trackEntries, true)) {
                            Log.e(TAG, "No hardware resources for onfound/onlost filter " +
                                    trackEntries);
                            try {
                                mService.onScanManagerErrorCallback(scannerId,
                                            ScanCallback.SCAN_FAILED_INTERNAL_ERROR);
                            } catch (RemoteException e) {
                                Log.e(TAG, "failed on onScanManagerCallback", e);
                            }
                        }
                    }
                    configureFilterParamter(scannerId, client, featureSelection, filterIndex,
                                            trackEntries);
                    waitForCallback();
                    clientFilterIndices.add(filterIndex);
                }
                mClientFilterIndexMap.put(scannerId, clientFilterIndices);
            }
        }

        // Check whether the filter should be added to controller.
        // Note only on ALL_PASS filter should be added.
        private boolean shouldAddAllPassFilterToController(ScanClient client, int deliveryMode) {
            // Not an ALL_PASS client, need to add filter.
            if (!shouldUseAllPassFilter(client)) {
                return true;
            }

            if (deliveryMode == DELIVERY_MODE_BATCH) {
                mAllPassBatchClients.add(client.scannerId);
                return mAllPassBatchClients.size() == 1;
            } else {
                mAllPassRegularClients.add(client.scannerId);
                return mAllPassRegularClients.size() == 1;
            }
        }

        private void removeScanFilters(int scannerId) {
            Deque<Integer> filterIndices = mClientFilterIndexMap.remove(scannerId);
            if (filterIndices != null) {
                mFilterIndexStack.addAll(filterIndices);
                for (Integer filterIndex : filterIndices) {
                    resetCountDownLatch();
                    gattClientScanFilterParamDeleteNative(scannerId, filterIndex);
                    waitForCallback();
                }
            }
            // Remove if ALL_PASS filters are used.
            removeFilterIfExisits(mAllPassRegularClients, scannerId,
                    ALL_PASS_FILTER_INDEX_REGULAR_SCAN);
            removeFilterIfExisits(mAllPassBatchClients, scannerId,
                    ALL_PASS_FILTER_INDEX_BATCH_SCAN);
        }

        private void removeFilterIfExisits(Set<Integer> clients, int scannerId, int filterIndex) {
            if (!clients.contains(scannerId)) {
                return;
            }
            clients.remove(scannerId);
            // Remove ALL_PASS filter iff no app is using it.
            if (clients.isEmpty()) {
                resetCountDownLatch();
                gattClientScanFilterParamDeleteNative(scannerId, filterIndex);
                waitForCallback();
            }
        }

        private ScanClient getBatchScanClient(int scannerId) {
            for (ScanClient client : mBatchClients) {
                if (client.scannerId == scannerId) {
                    return client;
                }
            }
            return null;
        }

        /**
         * Return batch scan result type value defined in bt stack.
         */
        private int getResultType(BatchScanParams params) {
            if (params.fullScanscannerId != -1 && params.truncatedScanscannerId != -1) {
                return SCAN_RESULT_TYPE_BOTH;
            }
            if (params.truncatedScanscannerId != -1) {
                return SCAN_RESULT_TYPE_TRUNCATED;
            }
            if (params.fullScanscannerId != -1) {
                return SCAN_RESULT_TYPE_FULL;
            }
            return -1;
        }

        // Check if ALL_PASS filter should be used for the client.
        private boolean shouldUseAllPassFilter(ScanClient client) {
            if (client == null) {
                return true;
            }
            if (client.filters == null || client.filters.isEmpty()) {
                return true;
            }
            return client.filters.size() > mFilterIndexStack.size();
        }

        private void addFilterToController(int scannerId, ScanFilterQueue.Entry entry,
                int filterIndex) {
            if (DBG) Log.d(TAG, "addFilterToController: " + entry.type);
            switch (entry.type) {
                case ScanFilterQueue.TYPE_DEVICE_ADDRESS:
                    if (DBG) Log.d(TAG, "add address " + entry.address);
                    gattClientScanFilterAddNative(scannerId, entry.type, filterIndex, 0, 0, 0, 0, 0,
                            0,
                            "", entry.address, (byte) entry.addr_type, new byte[0], new byte[0]);
                    break;

                case ScanFilterQueue.TYPE_SERVICE_DATA:
                    gattClientScanFilterAddNative(scannerId, entry.type, filterIndex, 0, 0, 0, 0, 0,
                            0,
                            "", "", (byte) 0, entry.data, entry.data_mask);
                    break;

                case ScanFilterQueue.TYPE_SERVICE_UUID:
                case ScanFilterQueue.TYPE_SOLICIT_UUID:
                    gattClientScanFilterAddNative(scannerId, entry.type, filterIndex, 0, 0,
                            entry.uuid.getLeastSignificantBits(),
                            entry.uuid.getMostSignificantBits(),
                            entry.uuid_mask.getLeastSignificantBits(),
                            entry.uuid_mask.getMostSignificantBits(),
                            "", "", (byte) 0, new byte[0], new byte[0]);
                    break;

                case ScanFilterQueue.TYPE_LOCAL_NAME:
                    if (DBG) Log.d(TAG, "adding filters: " + entry.name);
                    gattClientScanFilterAddNative(scannerId, entry.type, filterIndex, 0, 0, 0, 0, 0,
                            0,
                            entry.name, "", (byte) 0, new byte[0], new byte[0]);
                    break;

                case ScanFilterQueue.TYPE_MANUFACTURER_DATA:
                    int len = entry.data.length;
                    if (entry.data_mask.length != len)
                        return;
                    gattClientScanFilterAddNative(scannerId, entry.type, filterIndex, entry.company,
                            entry.company_mask, 0, 0, 0, 0, "", "", (byte) 0,
                            entry.data, entry.data_mask);
                    break;
            }
        }

        private void initFilterIndexStack() {
            int maxFiltersSupported =
                    AdapterService.getAdapterService().getNumOfOffloadedScanFilterSupported();
            // Start from index 3 as:
            // index 0 is reserved for ALL_PASS filter in Settings app.
            // index 1 is reserved for ALL_PASS filter for regular scan apps.
            // index 2 is reserved for ALL_PASS filter for batch scan apps.
            for (int i = 3; i < maxFiltersSupported; ++i) {
                mFilterIndexStack.add(i);
            }
        }

        // Configure filter parameters.
        private void configureFilterParamter(int scannerId, ScanClient client, int featureSelection,
                int filterIndex, int numOfTrackingEntries) {
            int deliveryMode = getDeliveryMode(client);
            int rssiThreshold = Byte.MIN_VALUE;
            ScanSettings settings = client.settings;
            int onFoundTimeout = getOnFoundOnLostTimeoutMillis(settings, true);
            int onLostTimeout = getOnFoundOnLostTimeoutMillis(settings, false);
            int onFoundCount = getOnFoundOnLostSightings(settings);
            onLostTimeout = 10000;
            if (DBG) {
                Log.d(TAG, "configureFilterParamter " + onFoundTimeout + " " + onLostTimeout + " "
                                + onFoundCount + " " + numOfTrackingEntries);
            }
            FilterParams FiltValue = new FilterParams(scannerId, filterIndex, featureSelection,
                    LIST_LOGIC_TYPE, FILTER_LOGIC_TYPE, rssiThreshold, rssiThreshold, deliveryMode,
                    onFoundTimeout, onLostTimeout, onFoundCount, numOfTrackingEntries);
            gattClientScanFilterParamAddNative(FiltValue);
        }

        // Get delivery mode based on scan settings.
        private int getDeliveryMode(ScanClient client) {
            if (client == null) {
                return DELIVERY_MODE_IMMEDIATE;
            }
            ScanSettings settings = client.settings;
            if (settings == null) {
                return DELIVERY_MODE_IMMEDIATE;
            }
            if ((settings.getCallbackType() & ScanSettings.CALLBACK_TYPE_FIRST_MATCH) != 0
                    || (settings.getCallbackType() & ScanSettings.CALLBACK_TYPE_MATCH_LOST) != 0) {
                return DELIVERY_MODE_ON_FOUND_LOST;
            }
            return settings.getReportDelayMillis() == 0 ? DELIVERY_MODE_IMMEDIATE
                    : DELIVERY_MODE_BATCH;
        }

        private int getScanWindowMillis(ScanSettings settings) {
            if (settings == null) {
                return SCAN_MODE_LOW_POWER_WINDOW_MS;
            }
            switch (settings.getScanMode()) {
                case ScanSettings.SCAN_MODE_LOW_LATENCY:
                    return SCAN_MODE_LOW_LATENCY_WINDOW_MS;
                case ScanSettings.SCAN_MODE_BALANCED:
                    return SCAN_MODE_BALANCED_WINDOW_MS;
                case ScanSettings.SCAN_MODE_LOW_POWER:
                    return SCAN_MODE_LOW_POWER_WINDOW_MS;
                default:
                    return SCAN_MODE_LOW_POWER_WINDOW_MS;
            }
        }

        private int getScanIntervalMillis(ScanSettings settings) {
            if (settings == null)
                return SCAN_MODE_LOW_POWER_INTERVAL_MS;
            switch (settings.getScanMode()) {
                case ScanSettings.SCAN_MODE_LOW_LATENCY:
                    return SCAN_MODE_LOW_LATENCY_INTERVAL_MS;
                case ScanSettings.SCAN_MODE_BALANCED:
                    return SCAN_MODE_BALANCED_INTERVAL_MS;
                case ScanSettings.SCAN_MODE_LOW_POWER:
                    return SCAN_MODE_LOW_POWER_INTERVAL_MS;
                default:
                    return SCAN_MODE_LOW_POWER_INTERVAL_MS;
            }
        }

        private int getOnFoundOnLostTimeoutMillis(ScanSettings settings, boolean onFound) {
            int factor;
            int timeout = ONLOST_ONFOUND_BASE_TIMEOUT_MS;

            if (settings.getMatchMode() == ScanSettings.MATCH_MODE_AGGRESSIVE) {
                factor = MATCH_MODE_AGGRESSIVE_TIMEOUT_FACTOR;
            } else {
                factor = MATCH_MODE_STICKY_TIMEOUT_FACTOR;
            }
            if (!onFound) factor = factor * ONLOST_FACTOR;
            return (timeout*factor);
        }

        private int getOnFoundOnLostSightings(ScanSettings settings) {
            if (settings == null)
                return ONFOUND_SIGHTINGS_AGGRESSIVE;
            if (settings.getMatchMode() == ScanSettings.MATCH_MODE_AGGRESSIVE) {
                return ONFOUND_SIGHTINGS_AGGRESSIVE;
            } else {
                return ONFOUND_SIGHTINGS_STICKY;
            }
        }

        private int getNumOfTrackingAdvertisements(ScanSettings settings) {
            if (settings == null)
                return 0;
            int val=0;
            int maxTotalTrackableAdvertisements =
                    AdapterService.getAdapterService().getTotalNumOfTrackableAdvertisements();
            // controller based onfound onlost resources are scarce commodity; the
            // assignment of filters to num of beacons to track is configurable based
            // on hw capabilities. Apps give an intent and allocation of onfound
            // resources or failure there of is done based on availibility - FCFS model
            switch (settings.getNumOfMatches()) {
                case ScanSettings.MATCH_NUM_ONE_ADVERTISEMENT:
                    val = 1;
                    break;
                case ScanSettings.MATCH_NUM_FEW_ADVERTISEMENT:
                    val = 2;
                    break;
                case ScanSettings.MATCH_NUM_MAX_ADVERTISEMENT:
                    val = maxTotalTrackableAdvertisements/2;
                    break;
                default:
                    val = 1;
                    if (DBG) {
                        Log.d(TAG, "Invalid setting for getNumOfMatches() "
                                        + settings.getNumOfMatches());
                    }
            }
            return val;
        }

        private boolean manageAllocationOfTrackingAdvertisement(int numOfTrackableAdvertisement,
                            boolean allocate) {
            int maxTotalTrackableAdvertisements =
                    AdapterService.getAdapterService().getTotalNumOfTrackableAdvertisements();
            synchronized(curUsedTrackableAdvertisements) {
                int availableEntries = maxTotalTrackableAdvertisements
                                            - curUsedTrackableAdvertisements;
                if (allocate) {
                    if (availableEntries >= numOfTrackableAdvertisement) {
                        curUsedTrackableAdvertisements += numOfTrackableAdvertisement;
                        return true;
                    } else {
                        return false;
                    }
                } else {
                    if (numOfTrackableAdvertisement > curUsedTrackableAdvertisements) {
                        return false;
                    } else {
                         curUsedTrackableAdvertisements -= numOfTrackableAdvertisement;
                         return true;
                    }
                }
            }
        }


        /************************** Regular scan related native methods **************************/
        private native void registerScannerNative(long app_uuid_lsb, long app_uuid_msb);
        private native void unregisterScannerNative(int scannerId);

        private native void gattClientScanNative(boolean start);

        private native void gattSetScanParametersNative(int client_if, int scan_interval,
                int scan_window);

        /************************** Filter related native methods ********************************/
        private native void gattClientScanFilterAddNative(int client_if,
                int filter_type, int filter_index, int company_id,
                int company_id_mask, long uuid_lsb, long uuid_msb,
                long uuid_mask_lsb, long uuid_mask_msb, String name,
                String address, byte addr_type, byte[] data, byte[] mask);

        private native void gattClientScanFilterDeleteNative(int client_if,
                int filter_type, int filter_index, int company_id,
                int company_id_mask, long uuid_lsb, long uuid_msb,
                long uuid_mask_lsb, long uuid_mask_msb, String name,
                String address, byte addr_type, byte[] data, byte[] mask);

        private native void gattClientScanFilterParamAddNative(FilterParams FiltValue);

        // Note this effectively remove scan filters for ALL clients.
        private native void gattClientScanFilterParamClearAllNative(
                int client_if);

        private native void gattClientScanFilterParamDeleteNative(
                int client_if, int filt_index);

        private native void gattClientScanFilterClearNative(int client_if,
                int filter_index);

        private native void gattClientScanFilterEnableNative(int client_if,
                boolean enable);

        /************************** Batch related native methods *********************************/
        private native void gattClientConfigBatchScanStorageNative(int client_if,
                int max_full_reports_percent, int max_truncated_reports_percent,
                int notify_threshold_percent);

        private native void gattClientStartBatchScanNative(int client_if, int scan_mode,
                int scan_interval_unit, int scan_window_unit, int address_type, int discard_rule);

        private native void gattClientStopBatchScanNative(int client_if);

        private native void gattClientReadScanReportsNative(int client_if, int scan_type);
    }

    private boolean isScreenOn() {
        Display[] displays = mDm.getDisplays();

        if (displays == null) {
            return false;
        }

        for (Display display : displays) {
            if (display.getState() == Display.STATE_ON) {
                return true;
            }
        }

        return false;
    }

    private final DisplayManager.DisplayListener mDisplayListener =
            new DisplayManager.DisplayListener() {
                @Override
                public void onDisplayAdded(int displayId) {}

                @Override
                public void onDisplayRemoved(int displayId) {}

                @Override
                public void onDisplayChanged(int displayId) {
                    if (isScreenOn()) {
                        sendMessage(MSG_RESUME_SCANS, null);
                    } else {
                        sendMessage(MSG_SUSPEND_SCANS, null);
                    }
                }
            };
}
