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
package com.android.bluetooth.gatt;

import android.bluetooth.le.ScanSettings;
import android.os.Binder;
import android.os.WorkSource;
import android.os.ServiceManager;
import android.os.SystemClock;
import android.os.RemoteException;
import com.android.internal.app.IBatteryStats;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.Iterator;
import java.util.List;
import java.util.HashMap;

import com.android.bluetooth.btservice.BluetoothProto;
/**
 * ScanStats class helps keep track of information about scans
 * on a per application basis.
 * @hide
 */
/*package*/ class AppScanStats {
    static final DateFormat dateFormat = new SimpleDateFormat("yyyy/MM/dd HH:mm:ss");

    /* ContextMap here is needed to grab Apps and Connections */
    ContextMap contextMap;

    /* GattService is needed to add scan event protos to be dumped later */
    GattService gattService;

    /* Battery stats is used to keep track of scans and result stats */
    IBatteryStats batteryStats;

    class LastScan {
        long duration;
        long suspendDuration;
        long suspendStartTime;
        boolean isSuspended;
        long timestamp;
        boolean opportunistic;
        boolean timeout;
        boolean background;
        boolean filtered;
        int results;
        int scannerId;

        public LastScan(long timestamp, long duration, boolean opportunistic, boolean background,
                boolean filtered, int scannerId) {
            this.duration = duration;
            this.timestamp = timestamp;
            this.opportunistic = opportunistic;
            this.background = background;
            this.filtered = filtered;
            this.results = 0;
            this.scannerId = scannerId;
            this.suspendDuration = 0;
            this.suspendStartTime = 0;
            this.isSuspended = false;
        }
    }

    static final int NUM_SCAN_DURATIONS_KEPT = 5;

    // This constant defines the time window an app can scan multiple times.
    // Any single app can scan up to |NUM_SCAN_DURATIONS_KEPT| times during
    // this window. Once they reach this limit, they must wait until their
    // earliest recorded scan exits this window.
    static final long EXCESSIVE_SCANNING_PERIOD_MS = 30 * 1000;

    // Maximum msec before scan gets downgraded to opportunistic
    static final int SCAN_TIMEOUT_MS = 30 * 60 * 1000;

    String appName;
    WorkSource workSource; // Used for BatteryStats
    int scansStarted = 0;
    int scansStopped = 0;
    boolean isRegistered = false;
    long minScanTime = Long.MAX_VALUE;
    long maxScanTime = 0;
    long mScanStartTime = 0;
    long mTotalScanTime = 0;
    long mTotalSuspendTime = 0;
    List<LastScan> lastScans = new ArrayList<LastScan>(NUM_SCAN_DURATIONS_KEPT);
    HashMap<Integer, LastScan> ongoingScans = new HashMap<Integer, LastScan>();
    long startTime = 0;
    long stopTime = 0;
    int results = 0;

    public AppScanStats(String name, WorkSource source, ContextMap map, GattService service) {
        appName = name;
        contextMap = map;
        gattService = service;
        batteryStats = IBatteryStats.Stub.asInterface(ServiceManager.getService("batterystats"));

        if (source == null) {
            // Bill the caller if the work source isn't passed through
            source = new WorkSource(Binder.getCallingUid(), appName);
        }
        workSource = source;
    }

    synchronized void addResult(int scannerId) {
        LastScan scan = getScanFromScannerId(scannerId);
        if (scan != null) {
            int batteryStatsResults = ++scan.results;

            // Only update battery stats after receiving 100 new results in order
            // to lower the cost of the binder transaction
            if (batteryStatsResults % 100 == 0) {
                try {
                    batteryStats.noteBleScanResults(workSource, 100);
                } catch (RemoteException e) {
                    /* ignore */
                }
            }
        }

        results++;
    }

    boolean isScanning() {
        return !ongoingScans.isEmpty();
    }

    LastScan getScanFromScannerId(int scannerId) {
        return ongoingScans.get(scannerId);
    }

    synchronized void recordScanStart(ScanSettings settings, boolean filtered, int scannerId) {
        LastScan existingScan = getScanFromScannerId(scannerId);
        if (existingScan != null) {
            return;
        }
        this.scansStarted++;
        startTime = SystemClock.elapsedRealtime();

        LastScan scan = new LastScan(startTime, 0, false, false, filtered, scannerId);
        if (settings != null) {
          scan.opportunistic = settings.getScanMode() == ScanSettings.SCAN_MODE_OPPORTUNISTIC;
          scan.background = (settings.getCallbackType() & ScanSettings.CALLBACK_TYPE_FIRST_MATCH) != 0;
        }

        BluetoothProto.ScanEvent scanEvent = new BluetoothProto.ScanEvent();
        scanEvent.setScanEventType(BluetoothProto.ScanEvent.SCAN_EVENT_START);
        scanEvent.setScanTechnologyType(BluetoothProto.ScanEvent.SCAN_TECH_TYPE_LE);
        scanEvent.setEventTimeMillis(System.currentTimeMillis());
        scanEvent.setInitiator(truncateAppName(appName));
        gattService.addScanEvent(scanEvent);

        if (!isScanning()) mScanStartTime = startTime;
        try {
            boolean isUnoptimized = !(scan.filtered || scan.background || scan.opportunistic);
            batteryStats.noteBleScanStarted(workSource, isUnoptimized);
        } catch (RemoteException e) {
            /* ignore */
        }

        ongoingScans.put(scannerId, scan);
    }

    synchronized void recordScanStop(int scannerId) {
        LastScan scan = getScanFromScannerId(scannerId);
        if (scan == null) {
            return;
        }
        this.scansStopped++;
        stopTime = SystemClock.elapsedRealtime();
        long scanDuration = stopTime - scan.timestamp;
        scan.duration = scanDuration;
        if (scan.isSuspended) {
            scan.suspendDuration += stopTime - scan.suspendStartTime;
            mTotalSuspendTime += scan.suspendDuration;
        }
        ongoingScans.remove(scannerId);
        if (lastScans.size() >= NUM_SCAN_DURATIONS_KEPT) {
            lastScans.remove(0);
        }
        lastScans.add(scan);

        BluetoothProto.ScanEvent scanEvent = new BluetoothProto.ScanEvent();
        scanEvent.setScanEventType(BluetoothProto.ScanEvent.SCAN_EVENT_STOP);
        scanEvent.setScanTechnologyType(BluetoothProto.ScanEvent.SCAN_TECH_TYPE_LE);
        scanEvent.setEventTimeMillis(System.currentTimeMillis());
        scanEvent.setInitiator(truncateAppName(appName));
        gattService.addScanEvent(scanEvent);

        if (!isScanning()) {
            long totalDuration = stopTime - mScanStartTime;
            mTotalScanTime += totalDuration;
            minScanTime = Math.min(totalDuration, minScanTime);
            maxScanTime = Math.max(totalDuration, maxScanTime);
        }

        try {
            // Inform battery stats of any results it might be missing on
            // scan stop
            boolean isUnoptimized = !(scan.filtered || scan.background || scan.opportunistic);
            batteryStats.noteBleScanResults(workSource, scan.results % 100);
            batteryStats.noteBleScanStopped(workSource, isUnoptimized);
        } catch (RemoteException e) {
            /* ignore */
        }
    }

    synchronized void recordScanSuspend(int scannerId) {
        LastScan scan = getScanFromScannerId(scannerId);
        if (scan == null || scan.isSuspended) {
            return;
        }
        scan.suspendStartTime = SystemClock.elapsedRealtime();
        scan.isSuspended = true;
    }

    synchronized void recordScanResume(int scannerId) {
        LastScan scan = getScanFromScannerId(scannerId);
        if (scan == null || !scan.isSuspended) {
            return;
        }
        scan.isSuspended = false;
        stopTime = SystemClock.elapsedRealtime();
        scan.suspendDuration += stopTime - scan.suspendStartTime;
        mTotalSuspendTime += scan.suspendDuration;
    }

    synchronized void setScanTimeout(int scannerId) {
        if (!isScanning()) return;

        LastScan scan = getScanFromScannerId(scannerId);
        if (scan != null) {
            scan.timeout = true;
        }
    }

    synchronized boolean isScanningTooFrequently() {
        if (lastScans.size() < NUM_SCAN_DURATIONS_KEPT) {
            return false;
        }

        return (SystemClock.elapsedRealtime() - lastScans.get(0).timestamp)
                < EXCESSIVE_SCANNING_PERIOD_MS;
    }

    synchronized boolean isScanningTooLong() {
        if (!isScanning()) {
            return false;
        }
        return (SystemClock.elapsedRealtime() - mScanStartTime) > SCAN_TIMEOUT_MS;
    }

    // This function truncates the app name for privacy reasons. Apps with
    // four part package names or more get truncated to three parts, and apps
    // with three part package names names get truncated to two. Apps with two
    // or less package names names are untouched.
    // Examples: one.two.three.four => one.two.three
    //           one.two.three => one.two
    private String truncateAppName(String name) {
        String initiator = name;
        String[] nameSplit = initiator.split("\\.");
        if (nameSplit.length > 3) {
            initiator = nameSplit[0] + "." +
                        nameSplit[1] + "." +
                        nameSplit[2];
        } else if (nameSplit.length == 3) {
            initiator = nameSplit[0] + "." + nameSplit[1];
        }

        return initiator;
    }

    synchronized void dumpToString(StringBuilder sb) {
        long currTime = SystemClock.elapsedRealtime();
        long maxScan = maxScanTime;
        long minScan = minScanTime;
        long scanDuration = 0;

        if (isScanning()) {
            scanDuration = currTime - mScanStartTime;
        }
        minScan = Math.min(scanDuration, minScan);
        maxScan = Math.max(scanDuration, maxScan);

        if (minScan == Long.MAX_VALUE) {
            minScan = 0;
        }

        /*TODO: Average scan time can be skewed for
         * multiple scan clients. It will show less than
         * actual value.
         * */
        long avgScan = 0;
        long totalScanTime = mTotalScanTime + scanDuration;
        if (scansStarted > 0) {
            avgScan = totalScanTime / scansStarted;
        }

        sb.append("  " + appName);
        if (isRegistered) sb.append(" (Registered)");

        if (!lastScans.isEmpty()) {
            LastScan lastScan = lastScans.get(lastScans.size() - 1);
            if (lastScan.opportunistic) sb.append(" (Opportunistic)");
            if (lastScan.background) sb.append(" (Background)");
            if (lastScan.timeout) sb.append(" (Forced-Opportunistic)");
            if (lastScan.filtered) sb.append(" (Filtered)");
        }
        sb.append("\n");

        sb.append("  LE scans (started/stopped)         : " +
                  scansStarted + " / " +
                  scansStopped + "\n");
        sb.append("  Scan time in ms (min/max/avg/total): " +
                  minScan + " / " +
                  maxScan + " / " +
                  avgScan + " / " +
                  totalScanTime + "\n");
        if (mTotalSuspendTime != 0) {
            sb.append("  Total time suspended             : " + mTotalSuspendTime + "ms\n");
        }
        sb.append("  Total number of results            : " +
                  results + "\n");

        long currentTime = System.currentTimeMillis();
        long elapsedRt = SystemClock.elapsedRealtime();
        if (!lastScans.isEmpty()) {
            sb.append("  Last " + lastScans.size() + " scans                       :\n");

            for (int i = 0; i < lastScans.size(); i++) {
                LastScan scan = lastScans.get(i);
                Date timestamp = new Date(currentTime - elapsedRt + scan.timestamp);
                sb.append("    " + dateFormat.format(timestamp) + " - ");
                sb.append(scan.duration + "ms ");
                if (scan.opportunistic) sb.append("Opp ");
                if (scan.background) sb.append("Back ");
                if (scan.timeout) sb.append("Forced ");
                if (scan.filtered) sb.append("Filter ");
                sb.append(scan.results + " results");
                sb.append(" (" + scan.scannerId + ")");
                sb.append("\n");
                if (scan.suspendDuration != 0) {
                    sb.append("      └"
                            + " Suspended Time: " + scan.suspendDuration + "ms\n");
                }
            }
        }

        if (!ongoingScans.isEmpty()) {
            sb.append("  Ongoing scans                      :\n");
            for (Integer key : ongoingScans.keySet()) {
                LastScan scan = ongoingScans.get(key);
                Date timestamp = new Date(currentTime - elapsedRt + scan.timestamp);
                sb.append("    " + dateFormat.format(timestamp) + " - ");
                sb.append((elapsedRt - scan.timestamp) + "ms ");
                if (scan.opportunistic) sb.append("Opp ");
                if (scan.background) sb.append("Back ");
                if (scan.timeout) sb.append("Forced ");
                if (scan.filtered) sb.append("Filter ");
                if (scan.isSuspended) sb.append("Suspended ");
                sb.append(scan.results + " results");
                sb.append(" (" + scan.scannerId + ")");
                sb.append("\n");
                if (scan.suspendStartTime != 0) {
                    long duration = scan.suspendDuration
                            + (scan.isSuspended ? (elapsedRt - scan.suspendStartTime) : 0);
                    sb.append("      └"
                            + " Suspended Time: " + duration + "ms\n");
                }
            }
        }

        ContextMap.App appEntry = contextMap.getByName(appName);
        if (appEntry != null && isRegistered) {
            sb.append("  Application ID                     : " +
                      appEntry.id + "\n");
            sb.append("  UUID                               : " +
                      appEntry.uuid + "\n");

            List<ContextMap.Connection> connections =
              contextMap.getConnectionByApp(appEntry.id);

            sb.append("  Connections: " + connections.size() + "\n");

            Iterator<ContextMap.Connection> ii = connections.iterator();
            while(ii.hasNext()) {
                ContextMap.Connection connection = ii.next();
                long connectionTime = SystemClock.elapsedRealtime() - connection.startTime;
                sb.append("    " + connection.connId + ": " +
                          connection.address + " " + connectionTime + "ms\n");
            }
        }
        sb.append("\n");
    }
}
