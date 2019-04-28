/*
 * Copyright (C) 2014 The Android Open Source Project
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

package com.android.tv.settings.connectivity;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.net.ConnectivityManager;
import android.net.EthernetManager;
import android.net.LinkAddress;
import android.net.LinkProperties;
import android.net.Network;
import android.net.NetworkInfo;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.os.HandlerThread;
import android.os.Process;
import android.support.annotation.UiThread;
import android.telephony.PhoneStateListener;
import android.telephony.SignalStrength;
import android.telephony.TelephonyManager;
import android.text.TextUtils;

import com.android.settingslib.wifi.AccessPoint;
import com.android.settingslib.wifi.WifiTracker;

import java.util.List;

/**
 * Listens for changes to the current connectivity status.
 */
public class ConnectivityListener implements WifiTracker.WifiListener {

    private static final String TAG = "ConnectivityListener";

    private final Context mContext;
    private final Listener mListener;
    private boolean mStarted;

    private WifiTracker mWifiTracker;
    private final HandlerThread mBgThread;

    private final ConnectivityManager mConnectivityManager;
    private final WifiManager mWifiManager;
    private final EthernetManager mEthernetManager;
    private WifiNetworkListener mWifiListener;
    private final BroadcastReceiver mNetworkReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            mListener.onConnectivityChange();
        }
    };
    private final EthernetManager.Listener mEthernetListener = new EthernetManager.Listener() {
        @Override
        public void onAvailabilityChanged(boolean isAvailable) {
            mListener.onConnectivityChange();
        }
    };
    private final PhoneStateListener mPhoneStateListener = new PhoneStateListener() {
        @Override
        public void onSignalStrengthsChanged(SignalStrength signalStrength) {
            mCellSignalStrength = signalStrength;
            mListener.onConnectivityChange();
        }
    };

    private SignalStrength mCellSignalStrength;
    private int mNetworkType;
    private String mWifiSsid;
    private int mWifiSignalStrength;

    public ConnectivityListener(Context context, Listener listener) {
        mContext = context;
        mConnectivityManager = (ConnectivityManager) mContext.getSystemService(
                Context.CONNECTIVITY_SERVICE);
        mWifiManager = mContext.getSystemService(WifiManager.class);
        mEthernetManager = mContext.getSystemService(EthernetManager.class);
        mListener = listener;
        mBgThread = new HandlerThread(TAG, Process.THREAD_PRIORITY_BACKGROUND);
        mBgThread.start();
        mWifiTracker = new WifiTracker(context, this, mBgThread.getLooper(), true, true);
    }

    /**
     * Starts {@link ConnectivityListener}.
     * This should be called only from main thread.
     */
    @UiThread
    public void start() {
        if (!mStarted) {
            mStarted = true;
            updateConnectivityStatus();
            mWifiTracker.startTracking();
            IntentFilter networkIntentFilter = new IntentFilter();
            networkIntentFilter.addAction(WifiManager.NETWORK_STATE_CHANGED_ACTION);
            networkIntentFilter.addAction(WifiManager.RSSI_CHANGED_ACTION);
            networkIntentFilter.addAction(WifiManager.WIFI_STATE_CHANGED_ACTION);

            mContext.registerReceiver(mNetworkReceiver, networkIntentFilter);
            mEthernetManager.addListener(mEthernetListener);
            final TelephonyManager telephonyManager = mContext
                    .getSystemService(TelephonyManager.class);
            if (telephonyManager != null) {
                telephonyManager.listen(mPhoneStateListener,
                        PhoneStateListener.LISTEN_SIGNAL_STRENGTHS);
            }
        }
    }

    /**
     * Stops {@link ConnectivityListener}.
     * This should be called only from main thread.
     */
    @UiThread
    public void stop() {
        if (mStarted) {
            mStarted = false;
            mWifiTracker.stopTracking();
            mContext.unregisterReceiver(mNetworkReceiver);
            mWifiListener = null;
            mEthernetManager.removeListener(mEthernetListener);
            final TelephonyManager telephonyManager = mContext
                    .getSystemService(TelephonyManager.class);
            if (telephonyManager != null) {
                telephonyManager.listen(mPhoneStateListener, PhoneStateListener.LISTEN_NONE);
            }
        }
    }

    /**
     * Causes the background thread to quit.
     */
    public void destroy() {
        mBgThread.quit();
    }

    public void setWifiListener(WifiNetworkListener wifiListener) {
        mWifiListener = wifiListener;
    }

    public String getWifiIpAddress() {
        if (isWifiConnected()) {
            WifiInfo wifiInfo = mWifiManager.getConnectionInfo();
            int ip = wifiInfo.getIpAddress();
            return String.format("%d.%d.%d.%d", (ip & 0xff), (ip >> 8 & 0xff),
                    (ip >> 16 & 0xff), (ip >> 24 & 0xff));
        } else {
            return "";
        }
    }

    /**
     * Return the MAC address of the currently connected Wifi AP.
     */
    public String getWifiMacAddress() {
        if (isWifiConnected()) {
            WifiInfo wifiInfo = mWifiManager.getConnectionInfo();
            return wifiInfo.getMacAddress();
        } else {
            return "";
        }
    }

    public boolean isEthernetConnected() {
        return mNetworkType == ConnectivityManager.TYPE_ETHERNET;
    }

    public boolean isWifiConnected() {
        return mNetworkType == ConnectivityManager.TYPE_WIFI;
    }

    public boolean isCellConnected() {
        return mNetworkType == ConnectivityManager.TYPE_MOBILE;
    }

    /**
     * Return whether Ethernet port is available.
     */
    public boolean isEthernetAvailable() {
        return mConnectivityManager.isNetworkSupported(ConnectivityManager.TYPE_ETHERNET)
                && mEthernetManager.isAvailable();
    }

    private Network getFirstEthernet() {
        final Network[] networks = mConnectivityManager.getAllNetworks();
        for (final Network network : networks) {
            NetworkInfo networkInfo = mConnectivityManager.getNetworkInfo(network);
            if (networkInfo != null && networkInfo.getType() == ConnectivityManager.TYPE_ETHERNET) {
                return network;
            }
        }
        return null;
    }

    public String getEthernetIpAddress() {
        final Network network = getFirstEthernet();
        if (network == null) {
            return null;
        }
        final StringBuilder sb = new StringBuilder();
        boolean gotAddress = false;
        final LinkProperties linkProperties = mConnectivityManager.getLinkProperties(network);
        for (LinkAddress linkAddress : linkProperties.getLinkAddresses()) {
            if (gotAddress) {
                sb.append("\n");
            }
            sb.append(linkAddress.getAddress().getHostAddress());
            gotAddress = true;
        }
        if (gotAddress) {
            return sb.toString();
        } else {
            return null;
        }
    }

    public int getWifiSignalStrength(int maxLevel) {
        WifiInfo wifiInfo = mWifiManager.getConnectionInfo();
        return WifiManager.calculateSignalLevel(wifiInfo.getRssi(), maxLevel);
    }

    public int getCellSignalStrength() {
        if (isCellConnected() && mCellSignalStrength != null) {
            return mCellSignalStrength.getLevel();
        } else {
            return 0;
        }
    }

    /**
     * Return a list of wifi networks. Ensure that if a wifi network is connected that it appears
     * as the first item on the list.
     */
    public List<AccessPoint> getAvailableNetworks() {
        return mWifiTracker.getAccessPoints();
    }

    public boolean isWifiEnabledOrEnabling() {
        return mWifiManager.getWifiState() == WifiManager.WIFI_STATE_ENABLED
                || mWifiManager.getWifiState() == WifiManager.WIFI_STATE_ENABLING;
    }

    public void setWifiEnabled(boolean enable) {
        mWifiManager.setWifiEnabled(enable);
    }

    private boolean setNetworkType(int networkType) {
        boolean hasChanged = mNetworkType != networkType;
        mNetworkType = networkType;
        return hasChanged;
    }

    private boolean updateConnectivityStatus() {
        NetworkInfo networkInfo = mConnectivityManager.getActiveNetworkInfo();
        if (networkInfo == null) {
            return setNetworkType(ConnectivityManager.TYPE_NONE);
        } else {
            switch (networkInfo.getType()) {
                case ConnectivityManager.TYPE_WIFI: {
                    boolean hasChanged;

                    // Determine if this is an open or secure wifi connection.
                    WifiInfo wifiInfo = mWifiManager.getConnectionInfo();
                    hasChanged = setNetworkType(ConnectivityManager.TYPE_WIFI);

                    // Find the SSID of network.
                    String ssid = null;
                    if (wifiInfo != null) {
                        ssid = wifiInfo.getSSID();
                        if (ssid != null) {
                            ssid = WifiInfo.removeDoubleQuotes(ssid);
                        }
                    }
                    if (!TextUtils.equals(mWifiSsid, ssid)) {
                        hasChanged = true;
                        mWifiSsid = ssid;
                    }

                    // Calculate the signal strength.
                    int signalStrength;
                    if (wifiInfo != null) {
                        // Calculate the signal strength between 0 and 3.
                        signalStrength = WifiManager.calculateSignalLevel(wifiInfo.getRssi(), 4);
                    } else {
                        signalStrength = 0;
                    }
                    if (mWifiSignalStrength != signalStrength) {
                        hasChanged = true;
                        mWifiSignalStrength = signalStrength;
                    }
                    return hasChanged;
                }

                case ConnectivityManager.TYPE_ETHERNET:
                    return setNetworkType(ConnectivityManager.TYPE_ETHERNET);

                case ConnectivityManager.TYPE_MOBILE:
                    return setNetworkType(ConnectivityManager.TYPE_MOBILE);

                default:
                    return setNetworkType(ConnectivityManager.TYPE_NONE);
            }
        }
    }

    @Override
    public void onWifiStateChanged(int state) {
        mListener.onConnectivityChange();
    }

    @Override
    public void onConnectedChanged() {

    }

    @Override
    public void onAccessPointsChanged() {
        if (mWifiListener != null) {
            mWifiListener.onWifiListChanged();
        }
    }

    public interface Listener {
        void onConnectivityChange();
    }

    public interface WifiNetworkListener {
        void onWifiListChanged();
    }
}
