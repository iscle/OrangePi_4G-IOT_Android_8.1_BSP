package com.android.pmc;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.net.wifi.WifiManager;
import android.os.AsyncTask;
import android.os.PowerManager;
import android.os.SystemClock;
import android.util.Log;

/**
 * Call wifi scan whenever an alarm is received.
 */
public class WifiConnScanReceiver extends BroadcastReceiver {
    int mScanCount = 0;
    ConnectvityScanTask mConnScanTask;
    PMCMainActivity mPMCMainActivity;
    private WifiManager mWifiManager;
    private Context mContext;
    private PowerManager.WakeLock mWakeLock;
    private int mAlarmInterval;
    private AlarmManager mAlarmManager;
    private PendingIntent mAlarmIntent;

    public WifiConnScanReceiver(PMCMainActivity activity, int interval, AlarmManager alarmManager,
                                PendingIntent alarmIntent) {
        mPMCMainActivity = activity;
        mScanCount = 0;
        mAlarmInterval = interval;
        mAlarmManager = alarmManager;
        mAlarmIntent = alarmIntent;
    }

    @Override
    public void onReceive(Context context, Intent intent) {
        if (mConnScanTask != null && mConnScanTask.getStatus() != AsyncTask.Status.FINISHED) {
            Log.e(PMCMainActivity.TAG, "Previous connection scan still running.");
            try {
                mConnScanTask.get();
            } catch (Exception e) {
                Log.e(PMCMainActivity.TAG, "Connection scan cancelled.");
            }
        } else {
            mContext = context;
            PowerManager pm = (PowerManager) context.getSystemService(Context.POWER_SERVICE);
            mWakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "WIFITEST");
            // Acquire the lock
            mWakeLock.acquire();
            mWifiManager = (WifiManager) context.getApplicationContext()
                    .getSystemService(Context.WIFI_SERVICE);
            Log.i(PMCMainActivity.TAG, "Starting Connectivity Scan Task");
            mConnScanTask = new ConnectvityScanTask();
            mConnScanTask.execute();
        }
        scheduleConnScan();
    }

    /**
     * Schedule the next connectivity scan.
     */
    public void scheduleConnScan() {
        Log.i(PMCMainActivity.TAG, "Scheduling the next conn scan after " + mAlarmInterval);
        mAlarmManager.setAndAllowWhileIdle(AlarmManager.ELAPSED_REALTIME_WAKEUP,
                SystemClock.elapsedRealtime() + mAlarmInterval, mAlarmIntent);
    }

    /**
     * Cancel the connectivity scans.
     */
    public void cancelConnScan() {
        mAlarmManager.cancel(mAlarmIntent);
        if (mConnScanTask != null) mConnScanTask.cancel(true);
    }

    class ConnectvityScanTask extends AsyncTask<Integer, Integer, String> {
        WifiScanReceiver mWifiScanReceiver;
        Boolean mScanCompleted = false;

        ConnectvityScanTask() {
            mWifiScanReceiver = new WifiScanReceiver();
            mContext.getApplicationContext().registerReceiver(mWifiScanReceiver,
                    new IntentFilter(WifiManager.SCAN_RESULTS_AVAILABLE_ACTION));
        }

        @Override
        protected String doInBackground(Integer... stime) {
            //android.os.Debug.waitForDebugger();
            int waitCount = 0;
            try {
                mScanCompleted = false;
                mWifiManager.startScan();
                while (!mScanCompleted) {
                    if (waitCount >= 100) {
                        return "Timeout, scan results avaiable action didn't triggered";
                    } else {
                        Thread.sleep(100);
                        waitCount += 1;
                    }
                }
                waitCount = 0;
                mScanCount += 1;
                Log.d(PMCMainActivity.TAG, "Number of scan completed " + mScanCount);
                publishProgress(mScanCount);
            } catch (Exception e) {
                Log.e(PMCMainActivity.TAG, e.toString());
                return e.toString();
            }
            return null;
        }

        @Override
        protected void onCancelled(String result) {
            mContext.getApplicationContext().unregisterReceiver(mWifiScanReceiver);
            mWakeLock.release();
        }

        @Override
        protected void onProgressUpdate(Integer... values) {
            Log.d(PMCMainActivity.TAG, "ConnectvityScanTask onProgressUpdate updating the UI");
            mPMCMainActivity.updateProgressStatus("Total Connectivity scan completed :: "
                    + Integer.toString(values[0].intValue()));
        }

        @Override
        protected void onPostExecute(String error) {
            if (error != null) {
                Log.e(PMCMainActivity.TAG, error);
                mPMCMainActivity.updateProgressStatus(error);
            }
            mContext.getApplicationContext().unregisterReceiver(mWifiScanReceiver);
            mWakeLock.release();
        }

        class WifiScanReceiver extends BroadcastReceiver {
            @Override
            public void onReceive(Context c, Intent intent) {
                String action = intent.getAction();
                if (action.equals(WifiManager.SCAN_RESULTS_AVAILABLE_ACTION)) {
                    Log.d(PMCMainActivity.TAG, "Wifi connection scan finished, results available.");
                    mScanCompleted = true;
                }
            }
        }
    }
}
