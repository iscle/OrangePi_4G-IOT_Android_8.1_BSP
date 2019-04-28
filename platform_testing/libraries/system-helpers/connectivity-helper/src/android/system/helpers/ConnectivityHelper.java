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

package android.system.helpers;

import android.app.DownloadManager;
import android.bluetooth.BluetoothAdapter;
import android.content.Context;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkInfo;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.support.test.InstrumentationRegistry;
import android.system.helpers.CommandsHelper;
import android.telecom.TelecomManager;
import android.telephony.TelephonyManager;
import android.util.Log;
import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URL;
import junit.framework.Assert;

/**
 * Implement common helper methods for connectivity.
 */
public class ConnectivityHelper {
    private static final String TAG = ConnectivityHelper.class.getSimpleName();
    private final static String DEFAULT_PING_SITE = "http://www.google.com";

    public static final int TIMEOUT = 1000;
    private static ConnectivityHelper sInstance = null;
    private Context mContext = null;
    private CommandsHelper mCommandsHelper = null;

    public ConnectivityHelper() {
        mContext = InstrumentationRegistry.getTargetContext();
        mCommandsHelper = CommandsHelper.getInstance();
    }

    public static ConnectivityHelper getInstance() {
        if (sInstance == null) {
            sInstance = new ConnectivityHelper();
        }
        return sInstance;
    }

    public TelecomManager getTelecomManager() {
        return (TelecomManager) mContext.getSystemService(Context.TELECOM_SERVICE);
    }

    public WifiManager getWifiManager() {
        return (WifiManager) mContext.getSystemService(Context.WIFI_SERVICE);
    }

    public ConnectivityManager getConnectivityManager() {
        return (ConnectivityManager) (ConnectivityManager) mContext
                .getSystemService(Context.CONNECTIVITY_SERVICE);
    }

    public DownloadManager getDownloadManager() {
        return (DownloadManager) (DownloadManager) mContext
                .getSystemService(Context.DOWNLOAD_SERVICE);
    }

    public BluetoothAdapter getBluetoothAdapter() {
        return BluetoothAdapter.getDefaultAdapter();
    }

   /**
     * Checks if device connection is active either through wifi or mobile data by sending an HTTP
     * request, check for HTTP_OK
     */
    public boolean isConnected() throws InterruptedException {
        int counter = 7;
        long TIMEOUT_MS = TIMEOUT;
        HttpURLConnection conn = null;
        while (--counter > 0) {
            try{
                URL url = new URL(DEFAULT_PING_SITE);
                conn = (HttpURLConnection) url.openConnection();
                conn.setRequestMethod("GET");
                conn.setConnectTimeout(TIMEOUT * 60); // 1 minute
                conn.setReadTimeout(TIMEOUT * 60); // 1 minute
                Log.i(TAG, "connection response code is " + conn.getResponseCode());
                Log.i(TAG, " counter = " + counter);
                return true;
            } catch (IOException ex) {
                // Wifi being flaky in the lab, test retries 10 times to connect to google.com
                // as IOException is throws connection isn't made and response stream is null
                // so for retrying purpose, exception hasn't been rethrown
                Log.i(TAG, ex.getMessage());
            } finally {
                if (conn != null) {
                    conn.disconnect();
                }
            }
            Thread.sleep(TIMEOUT_MS);
            TIMEOUT_MS = 2 * TIMEOUT_MS;
        }
        Log.i(TAG, " counter = " + counter);
        return false;
    }

    /**
     * Disconnects and disables network
     * @return true/false
     */
    public int disconnectWifi() {
        Assert.assertTrue("Wifi not disconnected", getWifiManager().disconnect());
        int netId = getWifiManager().getConnectionInfo().getNetworkId();
        getWifiManager().disableNetwork(netId);
        getWifiManager().saveConfiguration();
        return netId;
    }

    /**
     * Ensures wifi is enabled in device
     * @throws InterruptedException
     */
    public void ensureWifiEnabled() throws InterruptedException {
        // Device already connected to wifi as part of tradefed setup
        if (!getWifiManager().isWifiEnabled()) {
            getWifiManager().enableNetwork(getWifiManager().getConnectionInfo().getNetworkId(),
                    true);
            int counter = 5;
            while (--counter > 0 && !getWifiManager().isWifiEnabled()) {
                Thread.sleep(TIMEOUT * 5);
            }
        }
        Assert.assertTrue("Wifi should be enabled by now", getWifiManager().isWifiEnabled());
    }

    /**
     * Checks whether device has wifi connection for data service
     * @return true/false
     */
    public boolean hasWifiData() {
        NetworkInfo netInfo = getConnectivityManager().getActiveNetworkInfo();
        Assert.assertNotNull(netInfo);
        return (netInfo.getType() == ConnectivityManager.TYPE_WIFI);
    }

    /**
     * Checks whether device has mobile connection for data service
     * @return true/false
     */
    public boolean hasMobileData() {
        NetworkInfo netInfo = getConnectivityManager().getActiveNetworkInfo();
        Assert.assertNotNull(netInfo);
        return (netInfo.getType() == ConnectivityManager.TYPE_MOBILE);
    }

    /**
     * Checks whether device has sim
     * @return true/false
     */
    public boolean hasDeviceSim() {
        TelephonyManager telMgr = (TelephonyManager) mContext
                .getSystemService(mContext.TELEPHONY_SERVICE);
        return (telMgr.getSimState() == TelephonyManager.SIM_STATE_READY);
    }

    /**
     * Get connected wifi SSID.
     * @return connected wifi SSID
     */
    public String getCurrentWifiSSID() {
        WifiInfo connectionInfo = getWifiManager().getConnectionInfo();
        if (connectionInfo != null) {
            return connectionInfo.getSSID();
        }
        return null;
    }

    /**
     * Ensure bluetooth is enabled on device.
     * @throws InterruptedException
     */
    public void ensureBluetoothEnabled() throws InterruptedException {
        if (!getBluetoothAdapter().isEnabled()) {
            getBluetoothAdapter().enable();
        }
        int counter = 5;
        while (--counter > 0 && !getBluetoothAdapter().isEnabled()) {
            Thread.sleep(TIMEOUT * 2);
        }
    }

    /**
     * Check whether device has mobile data available.
     * @return true/false
     */
    public boolean mobileDataAvailable() {
        Network[] networkArray = getConnectivityManager().getAllNetworks();
        for (Network net: networkArray) {
            if (getConnectivityManager().getNetworkInfo(net).getType()
                    == ConnectivityManager.TYPE_MOBILE) {
                return true;
            }
        }
        return false;
    }
}
