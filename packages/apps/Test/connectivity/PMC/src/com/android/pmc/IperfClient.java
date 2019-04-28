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

import java.io.File;
import java.util.ArrayList;
import java.util.List;

/**
 * Starts an iperf client with the provided params for data transmission tests.
 * The client starts a UDP data transfer with the provided server with the max possible timeout.t a
 */
public class IperfClient {

    private static final String IPERF_COMMAND = "iperf3";
    private static final String IPERF_OPTION_SERVER_FLAG = "-c";
    private static final String IPERF_OPTION_PORT_FLAG = "-p";
    private static final String IPERF_OPTION_BANDWIDTH_FLAG = "-b";
    private static final String IPERF_OPTION_INTERVAL_FLAG = "-i";
    private static final String IPERF_OPTION_VERBOSE_FLAG = "-V";
    private static final String IPERF_OPTION_UDP_FLAG = "-u";
    private static final String IPERF_OPTION_JSON_OUTPUT_FLAG = "-J";
    private static final String IPERF_OPTION_TIMEOUT_FLAG = "-t";
    private static final String IPERF_OPTION_LOGFILE_FLAG = "--logfile";
    private static final String IPERF_OPTION_TMPDIR_FLAG = "--tmpdir";
    private static final int IPERF_OPTION_INTERVAL = 2;
    // This is the max value supported by iperf3.
    private static final int IPERF_OPTION_TIMEOUT = 86400;

    private final PMCMainActivity mPMCMainActivity;
    private final ProcessBuilder mProcessBuilder;
    private PowerManager.WakeLock mWakeLock;
    private Process mProcess;
    private File mLogFile;

    public IperfClient(PMCMainActivity activity, String serverAddress,
            String serverPort, String bandWidthInMbps, String logFile) {
        mPMCMainActivity = activity;
        List<String> cmdList = new ArrayList<>();
        cmdList.add(IPERF_COMMAND);
        cmdList.add(IPERF_OPTION_VERBOSE_FLAG);
        cmdList.add(IPERF_OPTION_UDP_FLAG);
        cmdList.add(IPERF_OPTION_JSON_OUTPUT_FLAG);
        cmdList.add(IPERF_OPTION_INTERVAL_FLAG);
        cmdList.add(Integer.toString(IPERF_OPTION_INTERVAL));
        cmdList.add(IPERF_OPTION_TIMEOUT_FLAG);
        cmdList.add(Integer.toString(IPERF_OPTION_TIMEOUT));
        cmdList.add(IPERF_OPTION_TMPDIR_FLAG);
        cmdList.add(activity.getCacheDir().getPath());
        if (serverAddress != null && serverAddress.length() > 0) {
            cmdList.add(IPERF_OPTION_SERVER_FLAG);
            cmdList.add(serverAddress);
        }
        if (serverPort != null && serverPort.length() > 0) {
            cmdList.add(IPERF_OPTION_PORT_FLAG);
            cmdList.add(serverPort);
        }
        if (bandWidthInMbps != null && bandWidthInMbps.length() > 0) {
            cmdList.add(IPERF_OPTION_BANDWIDTH_FLAG);
            cmdList.add(bandWidthInMbps);
        }
        if (logFile != null && logFile.length() > 0) {
            mLogFile = new File(logFile);
        }
        mProcessBuilder = new ProcessBuilder(cmdList);
    }

    /**
     * Start the iperf client
     */
    public void startClient() {
        Log.i(PMCMainActivity.TAG, "Starting iperf client: " + mProcessBuilder.command());
        PowerManager pm = (PowerManager) mPMCMainActivity.getSystemService(Context.POWER_SERVICE);
        mWakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "WIFITEST");
        // Acquire the lock
        mWakeLock.acquire();
        try {
            mProcessBuilder.redirectOutput(mLogFile);
            mProcessBuilder.redirectError(mLogFile);
            mProcess = mProcessBuilder.start();
        } catch (Exception e) {
            Log.e(PMCMainActivity.TAG, "Starting iperf client failed: " + e);
            mPMCMainActivity.updateProgressStatus("Starting iperf client failed");
        }
    }

    /**
     * Stop the iperf client
     */
    public void stopClient() {
        if (mProcess != null) {
            Log.i(PMCMainActivity.TAG, "Stopping iperf client: " + mProcessBuilder.command());
            try {
                mProcess.destroy();
                mProcess.waitFor();
            } catch (Exception e) {
                Log.e(PMCMainActivity.TAG, "Stopping iperf client failed: " + e);
            }
            mWakeLock.release();
            mProcess = null;
        }
    }
}
