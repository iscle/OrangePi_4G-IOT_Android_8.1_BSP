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

package com.android.test.util.wifistrengthscanner;

import android.app.Activity;
import android.app.Instrumentation;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.net.wifi.ScanResult;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.os.Bundle;
import android.os.Environment;
import android.os.ParcelFileDescriptor;
import android.util.Log;

import java.io.FileInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

public class WifiStrengthScannerInstrumentation extends Instrumentation {
    private static final String TAG = WifiStrengthScannerInstrumentation.class.getCanonicalName();
    private final static String SD_CARD_PATH =
            Environment.getExternalStorageDirectory().getAbsolutePath() + "/";
    private final int NUMBER_OF_WIFI_LEVELS = 101;
    private final int INVALID_RSSI = -127;
    private Bundle mArguments;
    private CountDownLatch mLatch;
    private boolean scanReceived;

    @Override
    public void onCreate(Bundle arguments) {
        super.onCreate(arguments);
        mArguments = arguments;
        start();
    }

    @Override
    public void onStart() {
        super.onStart();
        try {
            mLatch = new CountDownLatch(1);
            getContext().registerReceiver(new WifiScanReceiver(),
                    new IntentFilter(WifiManager.SCAN_RESULTS_AVAILABLE_ACTION));
            WifiManager wifiManager =
                    (WifiManager) getContext().getSystemService(Context.WIFI_SERVICE);
            scanReceived = false;
            wifiManager.startScan();
            mLatch.await(10000, TimeUnit.MILLISECONDS);

            if (!scanReceived) {
                sendFailureStatus("no_scan_received");
                finish(Activity.RESULT_CANCELED, new Bundle());
                return;
            }

            WifiInfo wifiInfo = wifiManager.getConnectionInfo();
            Bundle bundle = new Bundle();
            bundle.putString("suplicant_state", wifiInfo.getSupplicantState().name());

            String bssid = wifiInfo.getBSSID();
            bundle.putString("bssid", bssid);
            // zero counts as a level, so the max level is one less that the number of levels.
            bundle.putInt("wifi_max_level", NUMBER_OF_WIFI_LEVELS - 1);

            bundle.putInt("wifi_info_wifi_level",
                    WifiManager.calculateSignalLevel(wifiInfo.getRssi(), NUMBER_OF_WIFI_LEVELS));
            bundle.putInt("wifi_info_rssi", wifiInfo.getRssi());
            bundle.putInt("wifi_info_frequency", wifiInfo.getFrequency());

            ScanResult result = getScanResult(wifiManager, bssid);
            if (result != null) {
                bundle.putInt("scan_result_wifi_level", wifiManager.calculateSignalLevel(result
                        .level, NUMBER_OF_WIFI_LEVELS));
                bundle.putInt("scan_result_rssi", result.level);
                bundle.putInt("scan_result_frequency", result.frequency);
            }

            int dumpsysRssi = getRssiFromDumpsys(bssid);
            bundle.putInt("dumpsys_rssi", dumpsysRssi);
            bundle.putInt("dumpsys_wifi_level",
                    WifiManager.calculateSignalLevel(dumpsysRssi, NUMBER_OF_WIFI_LEVELS));
            sendStatus(Activity.RESULT_OK, bundle);
            finish(Activity.RESULT_OK, new Bundle());
        } catch (IOException e) {
            Log.e(TAG, Log.getStackTraceString(e));
            sendFailureStatus("io_exception");
            finish(Activity.RESULT_CANCELED, new Bundle());
        } catch (InterruptedException e) {
            Log.e(TAG, Log.getStackTraceString(e));
            sendFailureStatus("interrupted_exception");
            finish(Activity.RESULT_CANCELED, new Bundle());
        }
    }

    private ScanResult getScanResult(WifiManager wifiManager, String bssid) {
        List<ScanResult> scanResults = wifiManager.getScanResults();
        for (ScanResult scanResult : scanResults) {
            if (scanResult.BSSID.equals(bssid)) {
                return scanResult;
            }
        }

        return null;
    }

    private void sendFailureStatus(String update) {
        Bundle result = new Bundle();
        result.putString("wifi_strength_scanner_failure", update);
        sendStatus(Activity.RESULT_CANCELED, result);
    }

    private Integer getRssiFromDumpsys(String bssid) throws IOException, InterruptedException {
        List<String> dumpsysLines = getDumpsysWifiLastScanResults();

        for (int i = 2; i < dumpsysLines.size(); i++) {
            String line = dumpsysLines.get(i);
            if (line != null && line.contains(bssid)) {
                String[] tokens = line.trim().split("\\s\\s+");
                return Integer.parseInt(tokens[2]);
            }
        }

        return INVALID_RSSI;
    }

    private List<String> getDumpsysWifiLastScanResults() throws IOException, InterruptedException {
        String dumpsysWifi = executeCommand("dumpsys wifi");
        String[] lines = dumpsysWifi.split("\n");
        List<String> scanResults = new ArrayList<>();

        boolean scansStarted = false;
        for (String line : lines) {
            if (line.startsWith("Latest scan results:")) {
                scansStarted = true;
            }

            if (scansStarted) {
                if ("".equals(line.trim())) {
                    break;
                }

                scanResults.add(line);
            }
        }

        return scanResults;
    }

    private String executeCommand(String cmd) throws IOException, InterruptedException {
        StringBuilder result = new StringBuilder();
        try {
            ParcelFileDescriptor pfd = getUiAutomation().executeShellCommand(cmd);
            byte[] buf = new byte[1024];
            int bytesRead;
            FileInputStream fis = new ParcelFileDescriptor.AutoCloseInputStream(pfd);
            while ((bytesRead = fis.read(buf)) != -1) {
                result.append(new String(buf, 0, bytesRead));
            }
            fis.close();
        } catch (IOException e) {
            throw new IOException(String.format("Fails to execute command: %s ", cmd), e);
        }

        return result.toString();
    }

    private class WifiScanReceiver extends BroadcastReceiver {
        @Override
        public void onReceive(Context context, Intent intent) {
            Log.d(TAG, "scan results received.");
            scanReceived = true;
            mLatch.countDown();
        }
    }
}
