/*
 * Copyright (C) 2015 The Android Open Source Project
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

package com.android.compatibility.common.util;

import static android.net.wifi.WifiManager.EXTRA_WIFI_STATE;
import static android.net.wifi.WifiManager.WIFI_STATE_ENABLED;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.net.ProxyInfo;
import android.net.Uri;
import android.net.wifi.WifiConfiguration;
import android.net.wifi.WifiManager;
import android.text.TextUtils;
import android.util.Log;

import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/**
 * A simple activity to create and manage wifi configurations.
 */
public class WifiConfigCreator {
    public static final String ACTION_CREATE_WIFI_CONFIG =
            "com.android.compatibility.common.util.CREATE_WIFI_CONFIG";
    public static final String ACTION_UPDATE_WIFI_CONFIG =
            "com.android.compatibility.common.util.UPDATE_WIFI_CONFIG";
    public static final String ACTION_REMOVE_WIFI_CONFIG =
            "com.android.compatibility.common.util.REMOVE_WIFI_CONFIG";
    public static final String EXTRA_NETID = "extra-netid";
    public static final String EXTRA_SSID = "extra-ssid";
    public static final String EXTRA_SECURITY_TYPE = "extra-security-type";
    public static final String EXTRA_PASSWORD = "extra-password";

    public static final int SECURITY_TYPE_NONE = 1;
    public static final int SECURITY_TYPE_WPA = 2;
    public static final int SECURITY_TYPE_WEP = 3;

    private static final String TAG = "WifiConfigCreator";

    private static final long ENABLE_WIFI_WAIT_SEC = 10L;

    private final Context mContext;
    private final WifiManager mWifiManager;

    public WifiConfigCreator(Context context) {
        mContext = context;
        mWifiManager = (WifiManager) context.getSystemService(Context.WIFI_SERVICE);
    }

    /**
     * Adds a new WiFi network.
     * @return network id or -1 in case of error
     */
    public int addNetwork(String ssid, boolean hidden, int securityType,
            String password) throws InterruptedException, SecurityException {
        checkAndEnableWifi();

        WifiConfiguration wifiConf = createConfig(ssid, hidden, securityType, password);

        int netId = mWifiManager.addNetwork(wifiConf);

        if (netId != -1) {
            mWifiManager.enableNetwork(netId, true);
        } else {
            Log.w(TAG, "Unable to add SSID '" + ssid + "': netId = " + netId);
        }
        return netId;
    }

    /**
     * Adds a new wifiConfiguration with OPEN security type, and the given pacProxy
     * verifies that the proxy is added by getting the configuration back, and checking it.
     * @return returns the PAC proxy URL after adding the network and getting it from WifiManager
     * @throws IllegalStateException if any of the WifiManager operations fail
     */
    public String addHttpProxyNetworkVerifyAndRemove(String ssid, String pacProxyUrl)
            throws IllegalStateException {
        String retrievedPacProxyUrl = null;
        int netId = -1;
        try {
            WifiConfiguration conf = createConfig(ssid, false, SECURITY_TYPE_NONE, null);
            if (pacProxyUrl != null) {
                conf.setHttpProxy(ProxyInfo.buildPacProxy(Uri.parse(pacProxyUrl)));
            }
            netId = mWifiManager.addNetwork(conf);
            if (netId == -1) {
                throw new IllegalStateException("Failed to addNetwork: " + ssid);
            }
            for (final WifiConfiguration w : mWifiManager.getConfiguredNetworks()) {
                if (w.SSID.equals(ssid)) {
                    conf = w;
                    break;
                }
            }
            if (conf == null) {
                throw new IllegalStateException("Failed to get WifiConfiguration for: " + ssid);
            }
            Uri pacProxyFileUri = null;
            ProxyInfo httpProxy = conf.getHttpProxy();
            if (httpProxy != null) pacProxyFileUri = httpProxy.getPacFileUrl();
            if (pacProxyFileUri != null) {
                retrievedPacProxyUrl = conf.getHttpProxy().getPacFileUrl().toString();
            }
            if (!mWifiManager.removeNetwork(netId)) {
                throw new IllegalStateException("Failed to remove WifiConfiguration: " + ssid);
            }
        } finally {
            mWifiManager.removeNetwork(netId);
        }
        return retrievedPacProxyUrl;
    }

    /**
     * Updates a new WiFi network.
     * @return network id (may differ from original) or -1 in case of error
     */
    public int updateNetwork(WifiConfiguration wifiConf, String ssid, boolean hidden,
            int securityType, String password) throws InterruptedException, SecurityException {
        checkAndEnableWifi();
        if (wifiConf == null) {
            return -1;
        }

        WifiConfiguration conf = createConfig(ssid, hidden, securityType, password);
        conf.networkId = wifiConf.networkId;

        int newNetId = mWifiManager.updateNetwork(conf);

        if (newNetId != -1) {
            mWifiManager.saveConfiguration();
            mWifiManager.enableNetwork(newNetId, true);
        } else {
            Log.w(TAG, "Unable to update SSID '" + ssid + "': netId = " + newNetId);
        }
        return newNetId;
    }

    /**
     * Updates a new WiFi network.
     * @return network id (may differ from original) or -1 in case of error
     */
    public int updateNetwork(int netId, String ssid, boolean hidden,
            int securityType, String password) throws InterruptedException, SecurityException {
        checkAndEnableWifi();

        WifiConfiguration wifiConf = null;
        List<WifiConfiguration> configs = mWifiManager.getConfiguredNetworks();
        for (WifiConfiguration config : configs) {
            if (config.networkId == netId) {
                wifiConf = config;
                break;
            }
        }
        return updateNetwork(wifiConf, ssid, hidden, securityType, password);
    }

    public boolean removeNetwork(int netId) {
        return mWifiManager.removeNetwork(netId);
    }

    /**
     * Creates a WifiConfiguration set up according to given parameters
     * @param ssid SSID of the network
     * @param hidden Is SSID not broadcast?
     * @param securityType One of {@link #SECURITY_TYPE_NONE}, {@link #SECURITY_TYPE_WPA} or
     *                     {@link #SECURITY_TYPE_WEP}
     * @param password Password for WPA or WEP
     * @return Created configuration object
     */
    private WifiConfiguration createConfig(String ssid, boolean hidden, int securityType,
            String password) {
        WifiConfiguration wifiConf = new WifiConfiguration();
        if (!TextUtils.isEmpty(ssid)) {
            wifiConf.SSID = '"' + ssid + '"';
        }
        wifiConf.status = WifiConfiguration.Status.ENABLED;
        wifiConf.hiddenSSID = hidden;
        switch (securityType) {
            case SECURITY_TYPE_NONE:
                wifiConf.allowedKeyManagement.set(WifiConfiguration.KeyMgmt.NONE);
                break;
            case SECURITY_TYPE_WPA:
                updateForWPAConfiguration(wifiConf, password);
                break;
            case SECURITY_TYPE_WEP:
                updateForWEPConfiguration(wifiConf, password);
                break;
        }
        return wifiConf;
    }

    private void updateForWPAConfiguration(WifiConfiguration wifiConf, String wifiPassword) {
        wifiConf.allowedKeyManagement.set(WifiConfiguration.KeyMgmt.WPA_PSK);
        if (!TextUtils.isEmpty(wifiPassword)) {
            wifiConf.preSharedKey = '"' + wifiPassword + '"';
        }
    }

    private void updateForWEPConfiguration(WifiConfiguration wifiConf, String password) {
        wifiConf.allowedKeyManagement.set(WifiConfiguration.KeyMgmt.NONE);
        wifiConf.allowedAuthAlgorithms.set(WifiConfiguration.AuthAlgorithm.OPEN);
        wifiConf.allowedAuthAlgorithms.set(WifiConfiguration.AuthAlgorithm.SHARED);
        if (!TextUtils.isEmpty(password)) {
            int length = password.length();
            if ((length == 10 || length == 26
                    || length == 58) && password.matches("[0-9A-Fa-f]*")) {
                wifiConf.wepKeys[0] = password;
            } else {
                wifiConf.wepKeys[0] = '"' + password + '"';
            }
            wifiConf.wepTxKeyIndex = 0;
        }
    }

    private void checkAndEnableWifi() throws InterruptedException {
        final CountDownLatch enabledLatch = new CountDownLatch(1);

        // Register a change receiver first to pick up events between isEnabled and setEnabled
        final BroadcastReceiver watcher = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                if (intent.getIntExtra(EXTRA_WIFI_STATE, -1) == WIFI_STATE_ENABLED) {
                    enabledLatch.countDown();
                }
            }
        };

        mContext.registerReceiver(watcher, new IntentFilter(WifiManager.WIFI_STATE_CHANGED_ACTION));
        try {
            // In case wifi is not already enabled, wait for it to come up
            if (!mWifiManager.isWifiEnabled()) {
                mWifiManager.setWifiEnabled(true);
                enabledLatch.await(ENABLE_WIFI_WAIT_SEC, TimeUnit.SECONDS);
            }
        } finally {
            mContext.unregisterReceiver(watcher);
        }
    }
}

