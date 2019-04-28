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

import android.app.Activity;
import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.net.ConnectivityManager;
import android.net.wifi.WifiScanner;
import android.net.wifi.WifiScanner.ChannelSpec;
import android.net.wifi.WifiScanner.ScanSettings;
import android.os.Bundle;
import android.os.PowerManager;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;
import android.widget.RadioGroup;
import android.widget.TextView;
import android.widget.Toast;

/**
 * Main class for PMC.
 */
public class PMCMainActivity extends Activity {

    public static final String TAG = "PMC";
    public static final String SETTING_SERVER_IP_KEY = "ServerIP";
    public static final String SETTING_SERVER_PORT_KEY = "ServerPort";
    public static final String SETTING_INTERVAL_KEY = "Interval";
    public static final String SETTING_IPERF_BANDWIDTH_KEY = "IperfBandwidth";
    public static final String SETTING_IPERF_LOGFILE_KEY = "IperfLogfile";
    private static final String sConnScanAction = "ConnectionScan";
    private static final String sGScanAction = "GScan";
    private static final String sDownloadAction = "DownloadData";
    private static final String SETPARAMS_INTENT_STRING = "com.android.pmc.action.SETPARAMS";
    private static final String AUTOPOWER_INTENT_STRING = "com.android.pmc.action.AUTOPOWER";

    TextView mTextView;
    Intent mSettingIntent;
    private PendingIntent mPIGScan;
    private PendingIntent mPIDownload;
    private PendingIntent mPIConnScan;
    private String mServerIP = "10.10.10.1";
    private String mServerPort = "8080";
    private int mIntervalMillis = 60 * 1000;
    private String mIperfBandwidth = "1M";
    private String mIperfLogFile = "/sdcard/iperf.txt";
    private WifiConnScanReceiver mConnSR = null;
    private WifiGScanReceiver mGScanR = null;
    private WifiDownloadReceiver mDR = null;
    private IperfClient mIperfClient = null;
    private boolean mTethered = false;
    private RadioGroup mRadioGroup;
    private Button mBtnStart;
    private Button mBtnStop;
    private PMCReceiver mPMCReceiver;
    private BleScanReceiver mBleScanReceiver;
    private GattPMCReceiver mGattPMCReceiver;
    private A2dpReceiver mA2dpReceiver;
    private AlarmManager mAlarmManager;
    private PowerManager.WakeLock mWakeLock;
    private ConnectivityManager mConnManager;
    private int mProvisionCheckSleep = 1250;

    class OnStartTetheringCallback extends ConnectivityManager.OnStartTetheringCallback {
        @Override
        public void onTetheringStarted() {
            mTethered = true;
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        //Initiate wifi service manger
        mAlarmManager = (AlarmManager) getSystemService(Context.ALARM_SERVICE);
        mConnManager = (ConnectivityManager)
                this.getApplicationContext().getSystemService(Context.CONNECTIVITY_SERVICE);
        mPIGScan = PendingIntent.getBroadcast(this, 0, new Intent(sGScanAction), 0);
        mPIDownload = PendingIntent.getBroadcast(this, 0, new Intent(sDownloadAction), 0);
        mPIConnScan = PendingIntent.getBroadcast(this, 0, new Intent(sConnScanAction), 0);
        mPMCReceiver = new PMCReceiver();
        mBleScanReceiver = new BleScanReceiver(this, mAlarmManager);
        mGattPMCReceiver = new GattPMCReceiver(this, mAlarmManager);
        mA2dpReceiver = new A2dpReceiver(this, mAlarmManager);
        setContentView(R.layout.activity_linear);
        mTextView = (TextView) findViewById(R.id.text_content);
        mRadioGroup = (RadioGroup) findViewById(R.id.rb_dataselect);
        mBtnStart = (Button) findViewById(R.id.btnstart);
        mBtnStop = (Button) findViewById(R.id.btnstop);
        addListenerOnButton();
        registerReceiver(mPMCReceiver, new IntentFilter(AUTOPOWER_INTENT_STRING));
        registerReceiver(mPMCReceiver, new IntentFilter(SETPARAMS_INTENT_STRING));
        registerReceiver(mBleScanReceiver, new IntentFilter(BleScanReceiver.BLE_SCAN_INTENT));
        registerReceiver(mGattPMCReceiver, new IntentFilter(GattPMCReceiver.GATTPMC_INTENT));
        registerReceiver(mA2dpReceiver, new IntentFilter(A2dpReceiver.A2DP_INTENT));
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        unregisterReceiver(mPMCReceiver);
    }

    /**
     * Add Listener On Button
     */
    public void addListenerOnButton() {
        mBtnStart.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                // get selected radio button from radioGroup
                int selectedId = mRadioGroup.getCheckedRadioButtonId();
                switch (selectedId) {
                    case R.id.rb_hundredkb:
                        startDownloadFile("100kb.txt");
                        break;
                    case R.id.rb_kb:
                        startDownloadFile("1kb.txt");
                        break;
                    case R.id.rb_tenkb:
                        startDownloadFile("10kb.txt");
                        break;
                    case R.id.rb_mb:
                        startDownloadFile("1mb.txt");
                        break;
                    case R.id.rb_connscan:
                        startConnectivityScan();
                        break;
                    case R.id.rb_gscan2g:
                        Integer[] channelList = {2412, 2437, 2462};
                        startGscan(WifiScanner.WIFI_BAND_UNSPECIFIED, channelList);
                        break;
                    case R.id.rb_gscan_without_dfs:
                        startGscan(WifiScanner.WIFI_BAND_BOTH, null);
                        break;
                    case R.id.rb_iperf_client:
                        startIperfClient();
                        break;
                    case R.id.rb_usb_tethering:
                        startUSBTethering();
                        break;
                    default:
                        return;
                }
            }
        });

        mBtnStop.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                stopConnectivityScan();
                stopDownloadFile();
                stopGScan();
                stopIperfClient();
                stopUSBTethering();
                mBtnStart.setEnabled(true);
            }
        });
    }

    /**
     * Updates progress on the UI.
     * @param status
     */
    public void updateProgressStatus(String status) {
        mTextView.setText(status);
    }

    private void startDownloadFile(String filename) {
        // Stop any ongoing download sessions before starting a new instance.
        stopDownloadFile();
        Log.d(TAG, "serverIP ::" + mServerIP + " Port ::" + mServerPort
                + ". Interval: " + mIntervalMillis);
        if (mServerIP.length() == 0 || mServerPort.length() == 0) {
            String msg = "Provide server IP and Port information in Setting";
            Toast errorMsg = Toast.makeText(getBaseContext(), msg, Toast.LENGTH_LONG);
            errorMsg.show();
            startSettingActivity();
        } else {
            mDR = new WifiDownloadReceiver(PMCMainActivity.this,
                    "http://" + mServerIP + ":" + mServerPort + "/" + filename, mIntervalMillis,
                    mAlarmManager, mPIDownload);
            registerReceiver(mDR, new IntentFilter(sDownloadAction));
            Log.d(TAG, "Setting download data alarm. Interval: " + mIntervalMillis);
            mDR.scheduleDownload();
            mBtnStart.setEnabled(false);
            mRadioGroup.setFocusable(false);
            mTextView.setText("Started downloadng " + filename);
        }
    }

    private void stopDownloadFile() {
        if (mDR != null) {
            unregisterReceiver(mDR);
            mDR.cancelDownload();
            mDR = null;
            mBtnStart.setEnabled(true);
            mRadioGroup.setFocusable(true);
            mTextView.setText("Stopped download");
        }
    }

    private void startConnectivityScan() {
        // Stop any ongoing scans before starting a new instance.
        stopConnectivityScan();
        mConnSR = new WifiConnScanReceiver(this, mIntervalMillis, mAlarmManager, mPIConnScan);
        registerReceiver(mConnSR, new IntentFilter(sConnScanAction));
        Log.d(TAG, "Setting connectivity scan alarm. Interval: " + mIntervalMillis);
        mConnSR.scheduleConnScan();
        mBtnStart.setEnabled(false);
        mRadioGroup.setFocusable(false);
        mTextView.setText("Started connectivity scan");
    }

    private void stopConnectivityScan() {
        if (mConnSR != null) {
            unregisterReceiver(mConnSR);
            mConnSR.cancelConnScan();
            mConnSR = null;
            mBtnStart.setEnabled(true);
            mRadioGroup.setFocusable(true);
            mTextView.setText("Stopped connectivity scan");
        }
    }

    private void startGscan(int band, Integer[] channelList) {
        // Stop any ongoing scans before starting a new instance.
        stopGScan();
        ScanSettings scanSettings = new ScanSettings();
        String message;
        if (band == WifiScanner.WIFI_BAND_UNSPECIFIED) {
            ChannelSpec[] channels = new ChannelSpec[channelList.length];
            for (int i = 0; i < channelList.length; i++) {
                channels[i] = new ChannelSpec(channelList[i]);
            }
            scanSettings.channels = channels;
            message = "Started GScan for social channels";
        } else {
            scanSettings.band = band;
            message = "Started Gscan for both band without DFS channel";
        }
        mGScanR = new WifiGScanReceiver(
                this, scanSettings, mIntervalMillis, mAlarmManager, mPIGScan);
        registerReceiver(mGScanR, new IntentFilter(sGScanAction));
        Log.d(TAG, "Setting Gscan alarm. Interval: " + mIntervalMillis);
        mGScanR.scheduleGscan();
        mBtnStart.setEnabled(false);
        mRadioGroup.setFocusable(false);
        mTextView.setText(message);
    }

    private void stopGScan() {
        if (mGScanR != null) {
            unregisterReceiver(mGScanR);
            mGScanR.cancelGScan();
            mGScanR = null;
            mBtnStart.setEnabled(true);
            mRadioGroup.setFocusable(true);
            mTextView.setText("Stopped Gscan");
        }
    }

    private void startIperfClient() {
        // Stop any ongoing iperf sessions before starting a new instance.
        stopIperfClient();
        mIperfClient =
                new IperfClient(this, mServerIP, mServerPort, mIperfBandwidth, mIperfLogFile);
        mIperfClient.startClient();
        mBtnStart.setEnabled(false);
        mRadioGroup.setFocusable(false);
        mTextView.setText("Started iperf client");
    }

    private void stopIperfClient() {
        if (mIperfClient != null) {
            mIperfClient.stopClient();
            mIperfClient = null;
            mBtnStart.setEnabled(true);
            mRadioGroup.setFocusable(true);
            mTextView.setText("Stopped iperf client");
        }
    }

    private void startUSBTethering() {
        OnStartTetheringCallback tetherCallback = new OnStartTetheringCallback();
        mConnManager.startTethering(ConnectivityManager.TETHERING_USB, true, tetherCallback);
        // sleep until provisioning check for tethering is done
        try {
            Thread.sleep(mProvisionCheckSleep);
        } catch (InterruptedException e) {
            Log.d(TAG, "Sleep exception after enabling USB tethering");
        }
        if (mTethered) {
            mBtnStart.setEnabled(false);
            mRadioGroup.setFocusable(false);
            mTextView.setText("Started usb tethering");
        }
    }

    private void stopUSBTethering() {
        if (mTethered) {
            mConnManager.stopTethering(ConnectivityManager.TETHERING_USB);
            mTethered = false;
            mBtnStart.setEnabled(true);
            mRadioGroup.setFocusable(true);
            mTextView.setText("Stopped usb tethering");
        }
    }

    private void turnScreenOn(Context context) {
        if (mWakeLock == null) {
            PowerManager pm = (PowerManager) context.getSystemService(Context.POWER_SERVICE);
            mWakeLock = pm.newWakeLock(
                    PowerManager.SCREEN_DIM_WAKE_LOCK | PowerManager.ACQUIRE_CAUSES_WAKEUP, TAG);
        }
        if (mWakeLock != null && !mWakeLock.isHeld()) {
            Log.i(TAG, "Turning screen on");
            mWakeLock.acquire();
        }
    }

    private void turnScreenOff() {
        if (mWakeLock != null && mWakeLock.isHeld()) {
            Log.i(TAG, "Turning screen off");
            mWakeLock.release();
        }
    }

    private void startSettingActivity() {
        mSettingIntent = new Intent(PMCMainActivity.this, SettingActivity.class);
        mSettingIntent.putExtra(SETTING_SERVER_IP_KEY, mServerIP);
        mSettingIntent.putExtra(SETTING_SERVER_PORT_KEY, mServerPort);
        mSettingIntent.putExtra(SETTING_INTERVAL_KEY, String.valueOf(mIntervalMillis / 1000));
        mSettingIntent.putExtra(SETTING_IPERF_BANDWIDTH_KEY, mIperfBandwidth);
        mSettingIntent.putExtra(SETTING_IPERF_LOGFILE_KEY, mIperfLogFile);
        this.startActivityForResult(mSettingIntent, 0);
    }

    private void setIntervalFromUser(String newValueInSeconds) {
        if (newValueInSeconds.length() != 0 && Integer.parseInt(newValueInSeconds) >= 0) {
            mIntervalMillis = Integer.parseInt(newValueInSeconds) * 1000;
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.main, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case R.id.action_setting:
                startSettingActivity();
                return true;
            default:
                return super.onOptionsItemSelected(item);
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        //Retrieve data in the intent
        if (resultCode == 0) {
            mServerIP = data.getStringExtra(SETTING_SERVER_IP_KEY);
            mServerPort = data.getStringExtra(SETTING_SERVER_PORT_KEY);
            setIntervalFromUser(data.getStringExtra(SETTING_INTERVAL_KEY));
            mIperfBandwidth = data.getStringExtra(SETTING_IPERF_BANDWIDTH_KEY);
            mIperfLogFile = data.getStringExtra(SETTING_IPERF_LOGFILE_KEY);
        }
    }

    class PMCReceiver extends BroadcastReceiver {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (intent.getAction().equals(AUTOPOWER_INTENT_STRING)) {
                Bundle extras = intent.getExtras();
                String key = "PowerAction";
                if (extras != null) {
                    if (extras.containsKey(key)) {
                        String actionstring = extras.getString(key);
                        Log.d(TAG, "PowerAction = " + actionstring);
                        if (actionstring.equalsIgnoreCase("StartConnectivityScan")) {
                            startConnectivityScan();
                        } else if (actionstring.equalsIgnoreCase("StopConnectivityScan")) {
                            stopConnectivityScan();
                        } else if (actionstring.equalsIgnoreCase("Download1KB")) {
                            startDownloadFile("1kb.txt");
                        } else if (actionstring.equalsIgnoreCase("Download10KB")) {
                            startDownloadFile("10kb.txt");
                        } else if (actionstring.equalsIgnoreCase("Download100KB")) {
                            startDownloadFile("100kb.txt");
                        } else if (actionstring.equalsIgnoreCase("Download1MB")) {
                            startDownloadFile("1mb.txt");
                        } else if (actionstring.equalsIgnoreCase("StopDownload")) {
                            stopDownloadFile();
                        } else if (actionstring.equalsIgnoreCase("StartGScanChannel")) {
                            Integer[] channelList = {2412, 2437, 2462};
                            startGscan(WifiScanner.WIFI_BAND_UNSPECIFIED, channelList);
                        } else if (actionstring.equalsIgnoreCase("StartGScanBand")) {
                            startGscan(WifiScanner.WIFI_BAND_BOTH, null);
                        } else if (actionstring.equalsIgnoreCase("StopGScan")) {
                            stopGScan();
                        } else if (actionstring.equalsIgnoreCase("GetDownloadRate")) {
                            if (mDR != null) {
                                String dataRateString = "Data Rate: "
                                        + Integer.toString(mDR.getDownloadRate()) + " bytes/sec";
                                this.setResultData(dataRateString);
                            } else {
                                this.setResultData("No download running");
                            }
                        } else if (actionstring.equalsIgnoreCase("StartIperfClient")) {
                            startIperfClient();
                        } else if (actionstring.equalsIgnoreCase("StopIperfClient")) {
                            stopIperfClient();
                        } else if (actionstring.equalsIgnoreCase("StartUSBTethering")) {
                            startUSBTethering();
                        } else if (actionstring.equalsIgnoreCase("StopUSBTethering")) {
                            stopUSBTethering();
                        } else if (actionstring.equalsIgnoreCase("TurnScreenOn")) {
                            turnScreenOn(context);
                        } else if (actionstring.equalsIgnoreCase("TurnScreenOff")) {
                            turnScreenOff();
                        }
                        intent.removeExtra(key);
                    }
                }
            } else if (intent.getAction().equals(SETPARAMS_INTENT_STRING)) {
                Bundle extras = intent.getExtras();
                if (extras != null) {
                    if (extras.containsKey(SETTING_INTERVAL_KEY)) {
                        setIntervalFromUser(extras.getString(SETTING_INTERVAL_KEY));
                    }
                    if (extras.containsKey(SETTING_SERVER_IP_KEY)) {
                        mServerIP = extras.getString(SETTING_SERVER_IP_KEY);
                    }
                    if (extras.containsKey(SETTING_SERVER_PORT_KEY)) {
                        mServerPort = extras.getString(SETTING_SERVER_PORT_KEY);
                    }
                    if (extras.containsKey(SETTING_IPERF_BANDWIDTH_KEY)) {
                        mIperfBandwidth = extras.getString(SETTING_IPERF_BANDWIDTH_KEY);
                    }
                    if (extras.containsKey(SETTING_IPERF_LOGFILE_KEY)) {
                        mIperfLogFile = extras.getString(SETTING_IPERF_LOGFILE_KEY);
                    }
                }
            }
        }
    }
}
