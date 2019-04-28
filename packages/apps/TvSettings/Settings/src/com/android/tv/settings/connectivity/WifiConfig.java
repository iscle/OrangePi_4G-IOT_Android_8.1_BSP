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

import android.content.Context;
import android.net.IpConfiguration;
import android.net.wifi.WifiConfiguration;
import android.net.wifi.WifiManager;
import android.os.Parcelable;

/**
 * Wi-Fi configuration that implements NetworkConfiguration.
 */
class WifiConfig implements NetworkConfiguration {
    private final WifiManager mWifiManager;
    private WifiConfiguration mWifiConfiguration;

    WifiConfig(Context context) {
        mWifiManager = (WifiManager) context.getSystemService(Context.WIFI_SERVICE);
        mWifiConfiguration = new WifiConfiguration();
    }

    @Override
    public void setIpConfiguration(IpConfiguration configuration) {
        mWifiConfiguration.setIpConfiguration(configuration);
    }

    @Override
    public IpConfiguration getIpConfiguration() {
        return mWifiConfiguration.getIpConfiguration();
    }

    @Override
    public void save(WifiManager.ActionListener listener) {
        mWifiManager.save(mWifiConfiguration, listener);
    }

    /**
     * Load IpConfiguration from system with the given networkId.
     */
    public void load(int networkId) {
        mWifiConfiguration = WifiConfigHelper.getWifiConfiguration(mWifiManager, networkId);
    }

    @Override
    public String getPrintableName() {
        return mWifiConfiguration.getPrintableSsid();
    }

    @Override
    public Parcelable toParcelable() {
        return mWifiConfiguration;
    }

    @Override
    public void fromParcelable(Parcelable parcelable) {
        if (parcelable instanceof WifiConfiguration) {
            mWifiConfiguration = (WifiConfiguration) parcelable;
        } else {
            throw new IllegalArgumentException("Invalid parcelable");
        }
    }

    @Override
    public int getNetworkType() {
        return NetworkConfigurationFactory.TYPE_WIFI;
    }
}
