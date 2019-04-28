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

import android.net.IpConfiguration;
import android.net.wifi.WifiManager;
import android.os.Parcelable;

/**
 * Provide unified interface for network configuration that works for both Wi-Fi and Ethernet.
 */
public interface NetworkConfiguration {

    /**
     * Set IpConfiguration
     *
     * @param configuration IpConfiguration to set
     */
    public void setIpConfiguration(IpConfiguration configuration);

    /**
     * Get IpConfiguration
     *
     * @return IpConfiguration
     */
    public IpConfiguration getIpConfiguration();

    /**
     * Save current network configuration to system
     *
     * @param listener listener to notify the result
     */
    public void save(WifiManager.ActionListener listener);

    /**
     * Get printable name for this network.
     *
     * @return Printable name
     */
    public String getPrintableName();

    /**
     * Get parcelable for this configuration
     *
     * @return Parcelable
     */
    public Parcelable toParcelable();

    /**
     * Set values from a parcelable
     *
     * @param Parcelable
     */
    public void fromParcelable(Parcelable parcelable);

    /**
     * Get network type for this configuration defined in ConnectivityManager
     */
    public int getNetworkType();
}
