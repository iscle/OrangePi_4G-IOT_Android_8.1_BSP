package com.android.pmc;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.AsyncTask;
import android.os.PowerManager;
import android.os.SystemClock;
import android.util.Log;

import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
/**
 * Call wifi Download data whenever an alarm is received.
 */
public class WifiDownloadReceiver extends BroadcastReceiver {
    private static final int DOWNLOAD_BUFFER_SIZE = 1024 * 4;

    DownloadTask mDownloadTask;
    PMCMainActivity mPMCMainActivity;
    int mFileCount;
    int mBytesCount;
    long mDownloadStartTime;
    String mDownloadURL;
    private Context mContext;
    private PowerManager.WakeLock mWakeLock;
    private int mAlarmInterval;
    private AlarmManager mAlarmManager;
    private PendingIntent mAlarmIntent;

    public WifiDownloadReceiver(PMCMainActivity activity, String url, int interval,
                                AlarmManager alarmManager, PendingIntent alarmIntent) {
        mPMCMainActivity = activity;
        mDownloadURL = url;
        mFileCount = 0;
        mBytesCount = 0;
        mDownloadStartTime = -1;
        mAlarmInterval = interval;
        mAlarmManager = alarmManager;
        mAlarmIntent = alarmIntent;
    }

    @Override
    public void onReceive(Context context, Intent intent) {
        if (mDownloadTask != null && mDownloadTask.getStatus() != AsyncTask.Status.FINISHED) {
            Log.e(PMCMainActivity.TAG, "Previous download still running.");
            try {
                mDownloadTask.get();
            } catch (Exception e) {
                Log.e(PMCMainActivity.TAG, "Download cancelled.");
            }
        } else {
            mContext = context;
            PowerManager pm = (PowerManager) context.getSystemService(Context.POWER_SERVICE);
            mWakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "WIFITEST");
            // Acquire the lock
            mWakeLock.acquire();
            Log.i(PMCMainActivity.TAG, "Starting Download Task");
            mDownloadTask = new DownloadTask();
            mDownloadTask.execute(mDownloadURL);
        }
        scheduleDownload();
    }

    /**
     * Schedule the next download.
     */
    public void scheduleDownload() {
        if (mDownloadStartTime == -1) {
            // Note down the start of all download activity
            mDownloadStartTime = System.currentTimeMillis();
        }
        Log.i(PMCMainActivity.TAG, "Scheduling the next download after " + mAlarmInterval);
        mAlarmManager.setAndAllowWhileIdle(AlarmManager.ELAPSED_REALTIME_WAKEUP,
                SystemClock.elapsedRealtime() + mAlarmInterval, mAlarmIntent);
    }

    /**
     * Cancel the downloads.
     */
    public void cancelDownload() {
        mAlarmManager.cancel(mAlarmIntent);
        if (mDownloadTask != null) mDownloadTask.cancel(true);
    }

    /**
     * Returns an approximate data rate at which we're downloading the files.
     * @return
     */
    public int getDownloadRate() {
        long durationInMilliSeconds = (System.currentTimeMillis() - mDownloadStartTime);
        int durationInSeconds = (int) (durationInMilliSeconds / 1000);
        return (mBytesCount / durationInSeconds);
    }

    class DownloadTask extends AsyncTask<String, Integer, String> {
        @Override
        protected String doInBackground(String... sUrl) {
            //android.os.Debug.waitForDebugger();
            Log.d(PMCMainActivity.TAG, "Starting background task for downloading file");
            HttpURLConnection connection = null;
            try {
                URL url = new URL(sUrl[0]);
                connection = (HttpURLConnection) url.openConnection();
                connection.connect();
                // expect HTTP 200 OK, so we don't mistakenly save error report
                // instead of the file
                if (connection.getResponseCode() != HttpURLConnection.HTTP_OK) {
                    return "Server returned HTTP " + connection.getResponseCode()
                            + " " + connection.getResponseMessage();
                }
                // this will be useful to display download percentage
                // might be -1: server did not report the length
                int fileLength = connection.getContentLength();
                int bytesRead = downloadFile(connection);
                if (fileLength != bytesRead) {
                    return "Expected file of size " + fileLength + " but only received "
                            + bytesRead;
                }
                Log.d(PMCMainActivity.TAG, "Downloaded file size " + fileLength);
                mFileCount += 1;
                mBytesCount += fileLength;
                publishProgress(mFileCount, getDownloadRate());
                Thread.sleep(10000);
            } catch (Exception e) {
                Log.e(PMCMainActivity.TAG, e.toString());
                return e.toString();
            } finally {
                if (connection != null) {
                    connection.disconnect();
                }
            }
            return null;
        }

        @Override
        protected void onCancelled(String result) {
            mWakeLock.release();
        }

        @Override
        protected void onProgressUpdate(Integer... values) {
            Log.d(PMCMainActivity.TAG, "DownloadTask onProgressUpdate updating the UI");
            mPMCMainActivity.updateProgressStatus("Total file downloaded :: "
                    + values[0].toString() + ", Data rate :: "
                    + values[1].toString() + " bytes/sec");
        }

        @Override
        protected void onPostExecute(String error) {
            if (error != null) {
                Log.e(PMCMainActivity.TAG, error);
                mPMCMainActivity.updateProgressStatus(error);
            }
            mWakeLock.release();
        }

        private int downloadFile(HttpURLConnection connection) {
            if (connection == null) return -1;
            int totalBytesRead = 0;
            InputStream inputStream = null;
            // Just read out the input file, not saving it anywhere in the device
            try {
                inputStream = connection.getInputStream();
                int bytesRead = -1;
                byte[] buffer = new byte[DOWNLOAD_BUFFER_SIZE];
                while ((bytesRead = inputStream.read(buffer)) != -1) {
                    totalBytesRead += bytesRead;
                }
            } catch (Exception e) {
                Log.e(PMCMainActivity.TAG, "Downloaded failed");
            } finally {
                try {
                    if (inputStream != null) inputStream.close();
                } catch (Exception e) {
                    Log.e(PMCMainActivity.TAG, "Downloaded close failed");
                }
            }
            return totalBytesRead;
        }
    }
}
