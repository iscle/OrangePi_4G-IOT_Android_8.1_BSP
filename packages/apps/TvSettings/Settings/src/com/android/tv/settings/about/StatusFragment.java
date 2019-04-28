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
 * limitations under the License
 */

package com.android.tv.settings.about;

import android.bluetooth.BluetoothAdapter;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.net.ConnectivityManager;
import android.net.LinkProperties;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.os.SystemClock;
import android.os.SystemProperties;
import android.os.UserManager;
import android.support.annotation.Nullable;
import android.support.v17.preference.LeanbackPreferenceFragment;
import android.support.v7.preference.Preference;
import android.text.TextUtils;
import android.text.format.DateUtils;

import com.android.internal.util.ArrayUtils;
import com.android.tv.settings.R;

import java.lang.ref.WeakReference;
import java.net.InetAddress;
import java.util.Iterator;

public class StatusFragment extends LeanbackPreferenceFragment {

    private static final String KEY_BATTERY_STATUS = "battery_status";
    private static final String KEY_BATTERY_LEVEL = "battery_level";
    private static final String KEY_IP_ADDRESS = "wifi_ip_address";
    private static final String KEY_WIFI_MAC_ADDRESS = "wifi_mac_address";
    private static final String KEY_BT_ADDRESS = "bt_address";
    private static final String KEY_SERIAL_NUMBER = "serial_number";
    private static final String KEY_WIMAX_MAC_ADDRESS = "wimax_mac_address";
    private static final String KEY_SIM_STATUS = "sim_status";
    private static final String KEY_IMEI_INFO = "imei_info";

    // Broadcasts to listen to for connectivity changes.
    private static final String[] CONNECTIVITY_INTENTS = {
            BluetoothAdapter.ACTION_STATE_CHANGED,
            ConnectivityManager.CONNECTIVITY_ACTION,
            WifiManager.LINK_CONFIGURATION_CHANGED_ACTION,
            WifiManager.NETWORK_STATE_CHANGED_ACTION,
    };

    private static final int EVENT_UPDATE_STATS = 500;

    private static final int EVENT_UPDATE_CONNECTIVITY = 600;

    private ConnectivityManager mCM;
    private WifiManager mWifiManager;

    private Preference mUptime;
    private Preference mBtAddress;
    private Preference mIpAddress;
    private Preference mWifiMacAddress;
    private Preference mWimaxMacAddress;

    private IntentFilter mConnectivityIntentFilter;
    private final BroadcastReceiver mConnectivityReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            if (ArrayUtils.contains(CONNECTIVITY_INTENTS, action)) {
                mHandler.sendEmptyMessage(EVENT_UPDATE_CONNECTIVITY);
            }
        }
    };

    private Handler mHandler;

    private static class MyHandler extends Handler {
        private WeakReference<StatusFragment> mStatus;

        public MyHandler(StatusFragment activity) {
            mStatus = new WeakReference<>(activity);
        }

        @Override
        public void handleMessage(Message msg) {
            StatusFragment status = mStatus.get();
            if (status == null) {
                return;
            }

            switch (msg.what) {
                case EVENT_UPDATE_STATS:
                    status.updateTimes();
                    sendEmptyMessageDelayed(EVENT_UPDATE_STATS, 1000);
                    break;

                case EVENT_UPDATE_CONNECTIVITY:
                    status.updateConnectivity();
                    break;
            }
        }
    }

    public static StatusFragment newInstance() {
        return new StatusFragment();
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        mHandler = new MyHandler(this);

        mCM = (ConnectivityManager) getActivity().getSystemService(Context.CONNECTIVITY_SERVICE);
        mWifiManager = (WifiManager) getActivity().getSystemService(Context.WIFI_SERVICE);

        super.onCreate(savedInstanceState);
    }

    @Override
    public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
        setPreferencesFromResource(R.xml.device_info_status, null);

        // TODO: detect if we have a battery or not
        removePreference(findPreference(KEY_BATTERY_LEVEL));
        removePreference(findPreference(KEY_BATTERY_STATUS));
        mBtAddress = findPreference(KEY_BT_ADDRESS);
        mWifiMacAddress = findPreference(KEY_WIFI_MAC_ADDRESS);
        mWimaxMacAddress = findPreference(KEY_WIMAX_MAC_ADDRESS);
        mIpAddress = findPreference(KEY_IP_ADDRESS);
        mUptime = findPreference("up_time");

        if (!hasBluetooth()) {
            getPreferenceScreen().removePreference(mBtAddress);
            mBtAddress = null;
        }

        if (!hasWimax()) {
            getPreferenceScreen().removePreference(mWimaxMacAddress);
            mWimaxMacAddress = null;
        }

        mConnectivityIntentFilter = new IntentFilter();
        for (String intent: CONNECTIVITY_INTENTS) {
            mConnectivityIntentFilter.addAction(intent);
        }

        updateConnectivity();

        final Preference serialPref = findPreference(KEY_SERIAL_NUMBER);
        String serial = Build.SERIAL;
        if (!TextUtils.isEmpty(serial)) {
            serialPref.setSummary(serial);
        } else {
            removePreference(serialPref);
        }

        // Remove SimStatus and Imei for Secondary user as it access Phone b/19165700
        // Also remove on Wi-Fi only devices.
        //TODO: the bug above will surface in split system user mode.
        if (!UserManager.get(getActivity()).isAdminUser()
                || AboutFragment.isWifiOnly(getActivity())) {
            removePreference(findPreference(KEY_SIM_STATUS));
            removePreference(findPreference(KEY_IMEI_INFO));
        }
    }

    private void removePreference(@Nullable Preference preference) {
        if (preference != null) {
            getPreferenceScreen().removePreference(preference);
        }
    }

    private boolean hasBluetooth() {
        return BluetoothAdapter.getDefaultAdapter() != null;
    }

    private boolean hasWimax() {
        return  mCM.getNetworkInfo(ConnectivityManager.TYPE_WIMAX) != null;
    }

    @Override
    public void onStart() {
        super.onStart();
        getActivity().registerReceiver(mConnectivityReceiver, mConnectivityIntentFilter,
                android.Manifest.permission.CHANGE_NETWORK_STATE, null);
        mHandler.sendEmptyMessage(EVENT_UPDATE_STATS);
    }

    @Override
    public void onStop() {
        super.onStop();
        getActivity().unregisterReceiver(mConnectivityReceiver);
        mHandler.removeMessages(EVENT_UPDATE_STATS);
    }

    private void setWimaxStatus() {
        if (mWimaxMacAddress != null) {
            String macAddress = SystemProperties.get("net.wimax.mac.address",
                    getString(R.string.status_unavailable));
            mWimaxMacAddress.setSummary(macAddress);
        }
    }

    private void setWifiStatus() {
        WifiInfo wifiInfo = mWifiManager.getConnectionInfo();
        String macAddress = wifiInfo == null ? null : wifiInfo.getMacAddress();
        mWifiMacAddress.setSummary(!TextUtils.isEmpty(macAddress) ?
                macAddress : getString(R.string.status_unavailable));
    }

    private void setIpAddressStatus() {
        String ipAddress = getDefaultIpAddresses(mCM);
        if (ipAddress != null) {
            mIpAddress.setSummary(ipAddress);
        } else {
            mIpAddress.setSummary(R.string.status_unavailable);
        }
    }

    private void setBtStatus() {
        BluetoothAdapter bluetooth = BluetoothAdapter.getDefaultAdapter();
        if (bluetooth != null && mBtAddress != null) {
            String address = bluetooth.isEnabled() ? bluetooth.getAddress() : null;
            if (!TextUtils.isEmpty(address)) {
                // Convert the address to lowercase for consistency with the wifi MAC address.
                mBtAddress.setSummary(address.toLowerCase());
            } else {
                mBtAddress.setSummary(R.string.status_unavailable);
            }
        }
    }

    void updateConnectivity() {
        setWimaxStatus();
        setWifiStatus();
        setBtStatus();
        setIpAddressStatus();
    }

    /**
     * Returns the default link's IP addresses, if any, taking into account IPv4 and IPv6 style
     * addresses.
     * @param cm ConnectivityManager
     * @return the formatted and newline-separated IP addresses, or null if none.
     */
    private static String getDefaultIpAddresses(ConnectivityManager cm) {
        LinkProperties prop = cm.getActiveLinkProperties();
        return formatIpAddresses(prop);
    }

    private static String formatIpAddresses(LinkProperties prop) {
        if (prop == null) return null;
        Iterator<InetAddress> iter = prop.getAllAddresses().iterator();
        // If there are no entries, return null
        if (!iter.hasNext()) return null;
        // Concatenate all available addresses, comma separated
        String addresses = "";
        while (iter.hasNext()) {
            addresses += iter.next().getHostAddress();
            if (iter.hasNext()) addresses += "\n";
        }
        return addresses;
    }

    void updateTimes() {
        mUptime.setSummary(DateUtils.formatDuration(SystemClock.elapsedRealtime()));
    }

}
