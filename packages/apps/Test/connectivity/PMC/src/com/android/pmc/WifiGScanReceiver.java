package com.android.pmc;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.net.wifi.ScanResult;
import android.net.wifi.WifiManager;
import android.net.wifi.WifiScanner;
import android.net.wifi.WifiScanner.ScanData;
import android.net.wifi.WifiScanner.ScanSettings;
import android.os.AsyncTask;
import android.os.PowerManager;
import android.os.SystemClock;
import android.util.Log;

/**
 * Call wifi Gscan whenever an alarm is received.
 */
public class WifiGScanReceiver extends BroadcastReceiver {
    int mScanCount = 0;
    GScanTask mGScanTask;
    PMCMainActivity mPMCMainActivity;
    private WifiManager mWifiManager;
    private Context mContext;
    private PowerManager.WakeLock mWakeLock;
    private WifiScanner mScan;
    private ScanSettings mScanSettings;
    private int mAlarmInterval;
    private AlarmManager mAlarmManager;
    private PendingIntent mAlarmIntent;


    public WifiGScanReceiver(PMCMainActivity activity, ScanSettings settings, int interval,
                             AlarmManager alarmManager, PendingIntent alarmIntent) {
        mPMCMainActivity = activity;
        mScanSettings = settings;
        mScanCount = 0;
        mAlarmInterval = interval;
        mAlarmManager = alarmManager;
        mAlarmIntent = alarmIntent;
    }

    @Override
    public void onReceive(Context context, Intent intent) {
        if (mGScanTask != null && mGScanTask.getStatus() != AsyncTask.Status.FINISHED) {
            Log.e(PMCMainActivity.TAG, "Previous Gscan still running.");
            try {
                mGScanTask.get();
            } catch (Exception e) {
                Log.e(PMCMainActivity.TAG, "Gscan cancelled.");
            }
        } else {
            mContext = context;
            PowerManager pm = (PowerManager) context.getSystemService(Context.POWER_SERVICE);
            mWakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "WIFITEST");
            // Acquire the lock
            mWakeLock.acquire();
            mScan = (WifiScanner) context.getSystemService(Context.WIFI_SCANNING_SERVICE);
            Log.i(PMCMainActivity.TAG, "Starting GScan Task");
            mGScanTask = new GScanTask();
            mGScanTask.execute(mScanSettings);
        }
        scheduleGscan();
    }

    /**
     * Schedule the next Gscan.
     */
    public void scheduleGscan() {
        Log.i(PMCMainActivity.TAG, "Scheduling the next gscan after " + mAlarmInterval);
        mAlarmManager.setAndAllowWhileIdle(AlarmManager.ELAPSED_REALTIME_WAKEUP,
                SystemClock.elapsedRealtime() + mAlarmInterval, mAlarmIntent);
    }

    /**
     * Cancel the Gscans.
     */
    public void cancelGScan() {
        mAlarmManager.cancel(mAlarmIntent);
        if (mGScanTask != null) mGScanTask.cancel(true);
    }

    class GScanTask extends AsyncTask<ScanSettings, Integer, String> {
        WifiScanListener mWifiScanListener;
        Boolean mScanCompleted = false;

        GScanTask() {
            mWifiScanListener = new WifiScanListener();
        }

        @Override
        protected String doInBackground(ScanSettings... settings) {
            //android.os.Debug.waitForDebugger();
            Log.d(PMCMainActivity.TAG, "Starting background task for gscan with channel");
            int waitCount = 0;
            try {
                mScanCompleted = false;
                mScan.startBackgroundScan(settings[0], mWifiScanListener);
                while (!mScanCompleted) {
                    if (waitCount >= 100) {
                        return "Timeout, scan results avaiable action didn't triggered";
                    } else {
                        Thread.sleep(100);
                        waitCount += 1;
                    }
                }
                mScanCount += 1;
                waitCount = 0;
                Log.d(PMCMainActivity.TAG, "Number of scan completed " + mScanCount);
                publishProgress(mScanCount);
            } catch (Exception e) {
                Log.e(PMCMainActivity.TAG, e.toString());
                return e.toString();
            } finally {
                mScan.stopBackgroundScan(mWifiScanListener);
            }
            return null;
        }

        @Override
        protected void onCancelled(String result) {
            mWakeLock.release();
        }

        @Override
        protected void onProgressUpdate(Integer... values) {
            Log.d(PMCMainActivity.TAG, "GScanTask onProgressUpdate updating the UI");
            mPMCMainActivity.updateProgressStatus("Total Gscan completed :: "
                    + Integer.toString(values[0].intValue()));
        }

        @Override
        protected void onPostExecute(String error) {
            if (error != null) {
                Log.e(PMCMainActivity.TAG, error);
                mPMCMainActivity.updateProgressStatus(error);
            }
            mWakeLock.release();
        }

        private class WifiScanListener implements WifiScanner.ScanListener {
            WifiScanListener() {

            }

            @Override
            public void onSuccess() {
                Log.d(PMCMainActivity.TAG, "onSuccess called");
            }

            @Override
            public void onFailure(int reason, String description) {
                Log.d(PMCMainActivity.TAG, "onFailure called");
            }

            @Override
            public void onPeriodChanged(int periodInMs) {
                Log.d(PMCMainActivity.TAG, "onPeriodChanged called");
            }

            @Override
            public void onFullResult(ScanResult fullScanResult) {
                Log.d(PMCMainActivity.TAG, "onFullResult called");
            }

            @Override
            public void onResults(ScanData[] results) {
                Log.d(PMCMainActivity.TAG, "onResult WifiScanListener called");
                mScanCompleted = true;
            }
        }
    }
}
