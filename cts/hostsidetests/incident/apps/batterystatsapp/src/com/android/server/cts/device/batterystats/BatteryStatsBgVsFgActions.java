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

package com.android.server.cts.device.batterystats;

import android.accounts.Account;
import android.app.Activity;
import android.app.ActivityManager;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.le.BluetoothLeScanner;
import android.bluetooth.le.ScanCallback;
import android.bluetooth.le.ScanResult;
import android.bluetooth.le.ScanSettings;
import android.content.BroadcastReceiver;
import android.app.job.JobInfo;
import android.app.job.JobScheduler;
import android.content.ComponentName;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.Color;
import android.graphics.Point;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.net.wifi.WifiManager;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;
import android.util.Log;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;


import org.junit.Assert;

import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

public class BatteryStatsBgVsFgActions {
    private static final String TAG = BatteryStatsBgVsFgActions.class.getSimpleName();

    private static final int DO_NOTHING_TIMEOUT = 2000;

    public static final String KEY_ACTION = "action";
    public static final String ACTION_BLE_SCAN_OPTIMIZED = "action.ble_scan_optimized";
    public static final String ACTION_BLE_SCAN_UNOPTIMIZED = "action.ble_scan_unoptimized";
    public static final String ACTION_GPS = "action.gps";
    public static final String ACTION_JOB_SCHEDULE = "action.jobs";
    public static final String ACTION_SYNC = "action.sync";
    public static final String ACTION_WIFI_SCAN = "action.wifi_scan";
    public static final String ACTION_WIFI_DOWNLOAD = "action.wifi_download";
    public static final String ACTION_WIFI_UPLOAD = "action.wifi_upload";
    public static final String ACTION_SLEEP_WHILE_BACKGROUND = "action.sleep_background";
    public static final String ACTION_SLEEP_WHILE_TOP = "action.sleep_top";
    public static final String ACTION_SHOW_APPLICATION_OVERLAY = "action.show_application_overlay";

    public static final String KEY_REQUEST_CODE = "request_code";

    /** Number of times to check that app is in correct state before giving up. */
    public static final int PROC_STATE_CHECK_ATTEMPTS = 10;

    /** Perform the action specified by the given action code (see constants above). */
    public static void doAction(Context ctx, String actionCode, String requestCode) {
        if (actionCode == null) {
            Log.e(TAG, "Intent was missing action.");
            return;
        }
        sleep(100);
        switch (actionCode) {
            case ACTION_BLE_SCAN_OPTIMIZED:
                doOptimizedBleScan(ctx, requestCode);
                break;
            case ACTION_BLE_SCAN_UNOPTIMIZED:
                doUnoptimizedBleScan(ctx, requestCode);
                break;
            case ACTION_GPS:
                doGpsUpdate(ctx, requestCode);
                break;
            case ACTION_JOB_SCHEDULE:
                doScheduleJob(ctx, requestCode);
                break;
            case ACTION_SYNC:
                doSync(ctx, requestCode);
                break;
            case ACTION_WIFI_SCAN:
                doWifiScan(ctx, requestCode);
                break;
            case ACTION_WIFI_DOWNLOAD:
                doWifiDownload(ctx, requestCode);
                break;
            case ACTION_WIFI_UPLOAD:
                doWifiUpload(ctx, requestCode);
                break;
            case ACTION_SLEEP_WHILE_BACKGROUND:
                sleep(DO_NOTHING_TIMEOUT);
                tellHostActionFinished(ACTION_SLEEP_WHILE_BACKGROUND, requestCode);
                break;
            case ACTION_SLEEP_WHILE_TOP:
                doNothingAsync(ctx, ACTION_SLEEP_WHILE_TOP, requestCode);
                break;
            case ACTION_SHOW_APPLICATION_OVERLAY:
                showApplicationOverlay(ctx, requestCode);
                break;
            default:
                Log.e(TAG, "Intent had invalid action");
        }
        sleep(100);
    }

    private static void showApplicationOverlay(Context ctx, String requestCode) {
        final WindowManager wm = ctx.getSystemService(WindowManager.class);
        Point size = new Point();
        wm.getDefaultDisplay().getSize(size);

        WindowManager.LayoutParams wmlp = new WindowManager.LayoutParams(
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                        | WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON
                        | WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE);
        wmlp.width = size.x / 4;
        wmlp.height = size.y / 4;
        wmlp.gravity = Gravity.CENTER | Gravity.LEFT;
        wmlp.setTitle(ctx.getPackageName());

        ViewGroup.LayoutParams vglp = new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT);

        View v = new View(ctx);
        v.setBackgroundColor(Color.GREEN);
        v.setLayoutParams(vglp);
        wm.addView(v, wmlp);

        tellHostActionFinished(ACTION_SHOW_APPLICATION_OVERLAY, requestCode);
    }

    private static void doOptimizedBleScan(Context ctx, String requestCode) {
        ScanSettings scanSettings = new ScanSettings.Builder()
                .setScanMode(ScanSettings.SCAN_MODE_OPPORTUNISTIC).build();
        performBleScan(scanSettings);
        tellHostActionFinished(ACTION_BLE_SCAN_OPTIMIZED, requestCode);
    }

    private static void doUnoptimizedBleScan(Context ctx, String requestCode) {
        ScanSettings scanSettings = new ScanSettings.Builder()
                .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY).build();
        performBleScan(scanSettings);
        tellHostActionFinished(ACTION_BLE_SCAN_UNOPTIMIZED, requestCode);
    }

    private static void performBleScan(ScanSettings scanSettings) {
        BluetoothAdapter bluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
        if (bluetoothAdapter == null) {
            Log.e(TAG, "Device does not support Bluetooth");
            return;
        }
        boolean bluetoothEnabledByTest = false;
        if (!bluetoothAdapter.isEnabled()) {
            if (!bluetoothAdapter.enable()) {
                Log.e(TAG, "Bluetooth is not enabled");
                return;
            }
            sleep(8_000);
            bluetoothEnabledByTest = true;
        }

        BluetoothLeScanner bleScanner = bluetoothAdapter.getBluetoothLeScanner();
        if (bleScanner == null) {
            Log.e(TAG, "Cannot access BLE scanner");
            return;
        }

        ScanCallback scanCallback = new ScanCallback() {
            @Override
            public void onScanResult(int callbackType, ScanResult result) {
                Log.v(TAG, "called onScanResult");
            }

            @Override
            public void onScanFailed(int errorCode) {
                Log.v(TAG, "called onScanFailed");
            }

            @Override
            public void onBatchScanResults(List<ScanResult> results) {
                Log.v(TAG, "called onBatchScanResults");
            }
        };

        bleScanner.startScan(null, scanSettings, scanCallback);
        sleep(2_000);
        bleScanner.stopScan(scanCallback);

        // Restore adapter state at end of test
        if (bluetoothEnabledByTest) {
            bluetoothAdapter.disable();
        }
    }

    private static void doGpsUpdate(Context ctx, String requestCode) {
        final LocationManager locManager = ctx.getSystemService(LocationManager.class);
        if (!locManager.isProviderEnabled(LocationManager.GPS_PROVIDER)) {
            Log.e(TAG, "GPS provider is not enabled");
            tellHostActionFinished(ACTION_GPS, requestCode);
            return;
        }
        CountDownLatch latch = new CountDownLatch(1);

        final LocationListener locListener = new LocationListener() {
            public void onLocationChanged(Location location) {
                Log.v(TAG, "onLocationChanged: location has been obtained");
            }

            public void onProviderDisabled(String provider) {
                Log.w(TAG, "onProviderDisabled " + provider);
            }

            public void onProviderEnabled(String provider) {
                Log.w(TAG, "onProviderEnabled " + provider);
            }

            public void onStatusChanged(String provider, int status, Bundle extras) {
                Log.w(TAG, "onStatusChanged " + provider + " " + status);
            }
        };

        new AsyncTask<Void, Void, Void>() {
            @Override
            protected Void doInBackground(Void... params) {
                Looper.prepare();
                locManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, 990, 0,
                        locListener);
                sleep(1_000);
                locManager.removeUpdates(locListener);
                latch.countDown();
                return null;
            }
        }.execute();

        waitForReceiver(ctx, 59_000, latch, null);
        tellHostActionFinished(ACTION_GPS, requestCode);
    }

    private static void doScheduleJob(Context ctx, String requestCode) {
        final ComponentName JOB_COMPONENT_NAME =
                new ComponentName("com.android.server.cts.device.batterystats",
                        SimpleJobService.class.getName());
        JobScheduler js = ctx.getSystemService(JobScheduler.class);
        if (js == null) {
            Log.e(TAG, "JobScheduler service not available");
            tellHostActionFinished(ACTION_JOB_SCHEDULE, requestCode);
            return;
        }
        final JobInfo job = (new JobInfo.Builder(1, JOB_COMPONENT_NAME))
                .setOverrideDeadline(0)
                .build();
        CountDownLatch latch = SimpleJobService.resetCountDownLatch();
        js.schedule(job);
        // Job starts in main thread so wait in another thread to see if job finishes.
        new AsyncTask<Void, Void, Void>() {
            @Override
            protected Void doInBackground(Void... params) {
                waitForReceiver(null, 60_000, latch, null);
                tellHostActionFinished(ACTION_JOB_SCHEDULE, requestCode);
                return null;
            }
        }.execute();
    }

    private static void doNothingAsync(Context ctx, String requestCode, String actionCode) {
        new AsyncTask<Void, Void, Void>() {
            @Override
            protected Void doInBackground(Void... params) {
                sleep(DO_NOTHING_TIMEOUT);
                return null;
            }

            @Override
            protected void onPostExecute(Void nothing) {
                if (ctx instanceof Activity) {
                    ((Activity) ctx).finish();
                    tellHostActionFinished(actionCode, requestCode);
                }
            }
        }.execute();
    }

    private static void doSync(Context ctx, String requestCode) {
        BatteryStatsAuthenticator.removeAllAccounts(ctx);
        final Account account = BatteryStatsAuthenticator.getTestAccount();
        // Create the test account.
        BatteryStatsAuthenticator.ensureTestAccount(ctx);
        // Force set is syncable.
        ContentResolver.setMasterSyncAutomatically(true);
        ContentResolver.setIsSyncable(account, BatteryStatsProvider.AUTHORITY, 1);

        new AsyncTask<Void, Void, Void>() {
            @Override
            protected Void doInBackground(Void... params) {
                try {
                    Log.v(TAG, "Starting sync");
                    BatteryStatsSyncAdapter.requestSync(account);
                    sleep(500);
                } catch (Exception e) {
                    Log.e(TAG, "Exception trying to sync", e);
                }
                BatteryStatsAuthenticator.removeAllAccounts(ctx);
                return null;
            }

            @Override
            protected void onPostExecute(Void aVoid) {
                super.onPostExecute(aVoid);
                Log.v(TAG, "Finished sync method");
                // If ctx is an Activity, finish it when sync is done. If it's a service, don't.
                if (ctx instanceof Activity) {
                    ((Activity) ctx).finish();
                }
                tellHostActionFinished(ACTION_SYNC, requestCode);
            }
        }.execute();
    }

    private static void doWifiScan(Context ctx, String requestCode) {
        // Sometimes a scan was already running (from a different uid), so the first scan doesn't
        // start when requested. Therefore, additionally wait for whatever scan is currently running
        // to finish, then request a scan again - at least one of these two scans should be
        // attributed to this app.
        doWifiScanOnce(ctx);
        doWifiScanOnce(ctx);
        tellHostActionFinished(ACTION_WIFI_SCAN, requestCode);
    }

    private static void doWifiScanOnce(Context ctx) {
        IntentFilter intentFilter = new IntentFilter(WifiManager.SCAN_RESULTS_AVAILABLE_ACTION);
        CountDownLatch onReceiveLatch = new CountDownLatch(1);
        BroadcastReceiver receiver = registerReceiver(ctx, onReceiveLatch, intentFilter);
        ctx.getSystemService(WifiManager.class).startScan();
        waitForReceiver(ctx, 60_000, onReceiveLatch, receiver);
    }

    private static void doWifiDownload(Context ctx, String requestCode) {
        CountDownLatch latch = new CountDownLatch(1);

        new AsyncTask<Void, Void, Void>() {
            @Override
            protected Void doInBackground(Void... params) {
                BatteryStatsWifiTransferTests.download(requestCode);
                latch.countDown();
                return null;
            }
        }.execute();

        waitForReceiver(null, 60_000, latch, null);
        tellHostActionFinished(ACTION_WIFI_DOWNLOAD, requestCode);
    }

    private static void doWifiUpload(Context ctx, String requestCode) {
        CountDownLatch latch = new CountDownLatch(1);

        new AsyncTask<Void, Void, Void>() {
            @Override
            protected Void doInBackground(Void... params) {
                BatteryStatsWifiTransferTests.upload();
                latch.countDown();
                return null;
            }
        }.execute();

        waitForReceiver(null, 60_000, latch, null);
        tellHostActionFinished(ACTION_WIFI_UPLOAD, requestCode);
    }

    /** Register receiver to determine when given action is complete. */
    private static BroadcastReceiver registerReceiver(
            Context ctx, CountDownLatch onReceiveLatch, IntentFilter intentFilter) {
        BroadcastReceiver receiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                onReceiveLatch.countDown();
            }
        };
        // run Broadcast receiver in a different thread since the foreground activity will wait.
        HandlerThread handlerThread = new HandlerThread("br_handler_thread");
        handlerThread.start();
        Looper looper = handlerThread.getLooper();
        Handler handler = new Handler(looper);
        ctx.registerReceiver(receiver, intentFilter, null, handler);
        return receiver;
    }

    /**
     * Uses the receiver to wait until the action is complete. ctx and receiver may be null if no
     * receiver is needed to be unregistered.
     */
    private static void waitForReceiver(Context ctx,
            int maxWaitTimeMs, CountDownLatch latch, BroadcastReceiver receiver) {
        try {
            boolean didFinish = latch.await(maxWaitTimeMs, TimeUnit.MILLISECONDS);
            if (didFinish) {
                Log.v(TAG, "Finished performing action");
            } else {
                // This is not necessarily a problem. If we just want to make sure a count was
                // recorded for the request, it doesn't matter if the action actually finished.
                Log.w(TAG, "Did not finish in specified time.");
            }
        } catch (InterruptedException e) {
            Log.e(TAG, "Interrupted exception while awaiting action to finish", e);
        }
        if (ctx != null && receiver != null) {
            ctx.unregisterReceiver(receiver);
        }
    }

    /** Communicates to hostside (via logcat) that action has completed (regardless of success). */
    private static void tellHostActionFinished(String actionCode, String requestCode) {
        String s = String.format("Completed performing %s for request %s", actionCode, requestCode);
        Log.i(TAG, s);
    }

    /** Determines whether the package is running as a background process. */
    private static boolean isAppInBackground(Context context) throws ReflectiveOperationException {
        String pkgName = context.getPackageName();
        ActivityManager am = (ActivityManager) context.getSystemService(Context.ACTIVITY_SERVICE);
        List<ActivityManager.RunningAppProcessInfo> processes = am.getRunningAppProcesses();
        if (processes == null) {
            return false;
        }
        for (ActivityManager.RunningAppProcessInfo r : processes) {
            // BatteryStatsImpl treats as background if procState is >=
            // Activitymanager.PROCESS_STATE_IMPORTANT_BACKGROUND (corresponding
            // to BatteryStats.PROCESS_STATE_BACKGROUND).
            // Due to lack of permissions, only the current app should show up in the list of
            // processes, which is desired in this case; but in case this changes later, we check
            // that the package name matches anyway.
            int processState = -1;
            int backgroundCode = -1;
            try {
                processState = ActivityManager.RunningAppProcessInfo.class
                        .getField("processState").getInt(r);
                backgroundCode = (Integer) ActivityManager.class
                        .getDeclaredField("PROCESS_STATE_IMPORTANT_BACKGROUND").get(null);
            } catch (ReflectiveOperationException ex) {
                Log.e(TAG, "Failed to get proc state info via reflection", ex);
                throw ex;
            }
            if (processState < backgroundCode) { // if foreground process
                for (String rpkg : r.pkgList) {
                    if (pkgName.equals(rpkg)) {
                        return false;
                    }
                }
            }
        }
        return true;
    }

    /**
     * Makes sure app is in desired state, either background (if shouldBeBg = true) or foreground
     * (if shouldBeBg = false).
     * Tries for up to PROC_STATE_CHECK_ATTEMPTS seconds. If app is still not in the correct state,
     * throws an AssertionError failure to crash the app.
     */
    public static void checkAppState(
            Context context, boolean shouldBeBg, String actionCode, String requestCode) {
        final String errMsg = "App is " + (shouldBeBg ? "not " : "") + "a background process!";
        try {
            for (int attempt = 0; attempt < PROC_STATE_CHECK_ATTEMPTS; attempt++) {
                if (shouldBeBg == isAppInBackground(context)) {
                    return; // No problems.
                } else {
                    if (attempt < PROC_STATE_CHECK_ATTEMPTS - 1) {
                        Log.w(TAG, errMsg + " Trying again in 1s.");
                        sleep(1_000);
                    } else {
                        Log.e(TAG, errMsg + " Quiting app.");
                        BatteryStatsBgVsFgActions.tellHostActionFinished(actionCode, requestCode);
                        Assert.fail(errMsg + " Test requires app to be in the correct state.");
                    }
                }
            }
        } catch(ReflectiveOperationException ex) {
            Log.w(TAG, "Couldn't determine if app is in background. Proceeding with test anyway.");
        }
    }

    /** Puts the current thread to sleep. */
    private static void sleep(int millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Log.e(TAG, "Interrupted exception while sleeping", e);
        }
    }
}
