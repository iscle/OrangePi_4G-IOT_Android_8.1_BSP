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
 * limitations under the License
 */

package com.android.tv.settings.connectivity;

import android.content.Context;
import android.net.IpConfiguration;
import android.net.wifi.WifiConfiguration;
import android.net.wifi.WifiManager;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.v17.leanback.app.GuidedStepFragment;
import android.support.v17.leanback.widget.GuidanceStylist;
import android.support.v17.leanback.widget.GuidedAction;
import android.support.v17.preference.LeanbackPreferenceFragment;
import android.support.v7.preference.Preference;
import android.text.TextUtils;

import com.android.settingslib.wifi.AccessPoint;
import com.android.tv.settings.R;

import java.util.List;

public class WifiDetailsFragment extends LeanbackPreferenceFragment
        implements ConnectivityListener.Listener, ConnectivityListener.WifiNetworkListener {

    private static final String ARG_ACCESS_POINT_STATE = "apBundle";

    private static final String KEY_CONNECTION_STATUS = "connection_status";
    private static final String KEY_IP_ADDRESS = "ip_address";
    private static final String KEY_MAC_ADDRESS = "mac_address";
    private static final String KEY_SIGNAL_STRENGTH = "signal_strength";
    private static final String KEY_PROXY_SETTINGS = "proxy_settings";
    private static final String KEY_IP_SETTINGS = "ip_settings";
    private static final String KEY_FORGET_NETWORK = "forget_network";

    private Preference mConnectionStatusPref;
    private Preference mIpAddressPref;
    private Preference mMacAddressPref;
    private Preference mSignalStrengthPref;
    private Preference mProxySettingsPref;
    private Preference mIpSettingsPref;
    private Preference mForgetNetworkPref;

    private ConnectivityListener mConnectivityListener;
    private AccessPoint mAccessPoint;

    public static void prepareArgs(@NonNull Bundle args, AccessPoint accessPoint) {
        final Bundle apBundle = new Bundle();
        accessPoint.saveWifiState(apBundle);
        args.putParcelable(ARG_ACCESS_POINT_STATE, apBundle);
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        mConnectivityListener = new ConnectivityListener(getContext(), this);

        mAccessPoint = new AccessPoint(getContext(),
                getArguments().getBundle(ARG_ACCESS_POINT_STATE));
        super.onCreate(savedInstanceState);
    }

    @Override
    public void onStart() {
        super.onStart();
        mConnectivityListener.start();
        mConnectivityListener.setWifiListener(this);
    }

    @Override
    public void onResume() {
        super.onResume();
        update();
    }

    @Override
    public void onStop() {
        super.onStop();
        mConnectivityListener.stop();
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (mConnectivityListener != null) {
            mConnectivityListener.destroy();
        }
    }

    @Override
    public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
        setPreferencesFromResource(R.xml.wifi_details, null);

        getPreferenceScreen().setTitle(mAccessPoint.getSsid());

        mConnectionStatusPref = findPreference(KEY_CONNECTION_STATUS);
        mIpAddressPref = findPreference(KEY_IP_ADDRESS);
        mMacAddressPref = findPreference(KEY_MAC_ADDRESS);
        mSignalStrengthPref = findPreference(KEY_SIGNAL_STRENGTH);
        mProxySettingsPref = findPreference(KEY_PROXY_SETTINGS);
        mIpSettingsPref = findPreference(KEY_IP_SETTINGS);
        mForgetNetworkPref = findPreference(KEY_FORGET_NETWORK);
    }

    @Override
    public boolean onPreferenceTreeClick(Preference preference) {
        return super.onPreferenceTreeClick(preference);
    }

    @Override
    public void onConnectivityChange() {
        update();
    }

    @Override
    public void onWifiListChanged() {
        final List<AccessPoint> accessPoints = mConnectivityListener.getAvailableNetworks();
        for (final AccessPoint accessPoint : accessPoints) {
            if (TextUtils.equals(mAccessPoint.getSsidStr(), accessPoint.getSsidStr())
                    && mAccessPoint.getSecurity() == accessPoint.getSecurity()) {
                // Make sure we're not holding on to the one we inflated from the bundle, because
                // it won't be updated
                mAccessPoint = accessPoint;
                break;
            }
        }
        update();
    }

    private void update() {
        if (!isAdded()) {
            return;
        }

        final boolean active = mAccessPoint.isActive();

        mConnectionStatusPref.setSummary(active ? R.string.connected : R.string.not_connected);
        mIpAddressPref.setVisible(active);
        mMacAddressPref.setVisible(active);
        mSignalStrengthPref.setVisible(active);

        if (active) {
            mIpAddressPref.setSummary(mConnectivityListener.getWifiIpAddress());
            mMacAddressPref.setSummary(mConnectivityListener.getWifiMacAddress());
            mSignalStrengthPref.setSummary(getSignalStrength());
        }

        WifiConfiguration wifiConfiguration = mAccessPoint.getConfig();
        if (wifiConfiguration != null) {
            final int networkId = wifiConfiguration.networkId;
            mProxySettingsPref.setSummary(
                    wifiConfiguration.getProxySettings() == IpConfiguration.ProxySettings.NONE
                            ? R.string.wifi_action_proxy_none : R.string.wifi_action_proxy_manual);
            mProxySettingsPref.setIntent(EditProxySettingsActivity.createIntent(getContext(),
                    networkId));

            mIpSettingsPref.setSummary(
                    wifiConfiguration.getIpAssignment() == IpConfiguration.IpAssignment.STATIC
                            ? R.string.wifi_action_static : R.string.wifi_action_dhcp);
            mIpSettingsPref.setIntent(EditIpSettingsActivity.createIntent(getContext(), networkId));

            mForgetNetworkPref.setFragment(ForgetNetworkConfirmFragment.class.getName());
            ForgetNetworkConfirmFragment.prepareArgs(mForgetNetworkPref.getExtras(), mAccessPoint);
        }

        mProxySettingsPref.setVisible(wifiConfiguration != null);
        mIpSettingsPref.setVisible(wifiConfiguration != null);
        mForgetNetworkPref.setVisible(wifiConfiguration != null);
    }

    private String getSignalStrength() {
        String[] signalLevels = getResources().getStringArray(R.array.wifi_signal_strength);
        int strength = mConnectivityListener.getWifiSignalStrength(signalLevels.length);
        return signalLevels[strength];
    }

    public static class ForgetNetworkConfirmFragment extends GuidedStepFragment {

        private AccessPoint mAccessPoint;

        public static void prepareArgs(@NonNull Bundle args, AccessPoint accessPoint) {
            final Bundle apBundle = new Bundle();
            accessPoint.saveWifiState(apBundle);
            args.putParcelable(ARG_ACCESS_POINT_STATE, apBundle);
        }

        @Override
        public void onCreate(Bundle savedInstanceState) {
            mAccessPoint = new AccessPoint(getContext(),
                    getArguments().getBundle(ARG_ACCESS_POINT_STATE));
            super.onCreate(savedInstanceState);
        }

        @NonNull
        @Override
        public GuidanceStylist.Guidance onCreateGuidance(Bundle savedInstanceState) {
            return new GuidanceStylist.Guidance(
                    getString(R.string.wifi_forget_network),
                    getString(R.string.wifi_forget_network_description),
                    mAccessPoint.getSsidStr(),
                    getContext().getDrawable(R.drawable.ic_wifi_signal_4_white_132dp));
        }

        @Override
        public void onCreateActions(@NonNull List<GuidedAction> actions,
                Bundle savedInstanceState) {
            final Context context = getContext();
            actions.add(new GuidedAction.Builder(context)
                    .clickAction(GuidedAction.ACTION_ID_OK)
                    .build());
            actions.add(new GuidedAction.Builder(context)
                    .clickAction(GuidedAction.ACTION_ID_CANCEL)
                    .build());
        }

        @Override
        public void onGuidedActionClicked(GuidedAction action) {
            if (action.getId() == GuidedAction.ACTION_ID_OK) {
                WifiManager wifiManager =
                        (WifiManager) getContext().getSystemService(Context.WIFI_SERVICE);
                wifiManager.forget(mAccessPoint.getConfig().networkId, null);
            }
            getFragmentManager().popBackStack();
        }
    }
}
