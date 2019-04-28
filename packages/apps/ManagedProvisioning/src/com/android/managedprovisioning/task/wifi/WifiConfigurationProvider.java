/*
 * Copyright 2016, The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.managedprovisioning.task.wifi;

import android.net.IpConfiguration.ProxySettings;
import android.net.ProxyInfo;
import android.net.wifi.WifiConfiguration;
import android.text.TextUtils;

import com.android.internal.annotations.VisibleForTesting;
import com.android.managedprovisioning.model.WifiInfo;

/**
 * Utility class for configuring a new {@link WifiConfiguration} object from the provisioning
 * parameters represented via {@link WifiInfo}.
 */
public class WifiConfigurationProvider {

    @VisibleForTesting
    static final String WPA = "WPA";
    @VisibleForTesting
    static final String WEP = "WEP";
    @VisibleForTesting
    static final String NONE = "NONE";

    /**
     * Create a {@link WifiConfiguration} object from the internal representation given via
     * {@link WifiInfo}.
     */
    public WifiConfiguration generateWifiConfiguration(WifiInfo wifiInfo) {
        WifiConfiguration wifiConf = new WifiConfiguration();
        wifiConf.SSID = wifiInfo.ssid;
        wifiConf.status = WifiConfiguration.Status.ENABLED;
        wifiConf.hiddenSSID = wifiInfo.hidden;
        wifiConf.userApproved = WifiConfiguration.USER_APPROVED;
        String securityType = wifiInfo.securityType != null ? wifiInfo.securityType : NONE;
        switch (securityType) {
            case WPA:
                updateForWPAConfiguration(wifiConf, wifiInfo.password);
                break;
            case WEP:
                updateForWEPConfiguration(wifiConf, wifiInfo.password);
                break;
            default: // NONE
                wifiConf.allowedKeyManagement.set(WifiConfiguration.KeyMgmt.NONE);
                wifiConf.allowedAuthAlgorithms.set(WifiConfiguration.AuthAlgorithm.OPEN);
                break;
        }

        updateForProxy(
                wifiConf,
                wifiInfo.proxyHost,
                wifiInfo.proxyPort,
                wifiInfo.proxyBypassHosts,
                wifiInfo.pacUrl);
        return wifiConf;
    }

    private void updateForWPAConfiguration(WifiConfiguration wifiConf, String wifiPassword) {
        wifiConf.allowedKeyManagement.set(WifiConfiguration.KeyMgmt.WPA_PSK);
        wifiConf.allowedAuthAlgorithms.set(WifiConfiguration.AuthAlgorithm.OPEN);
        wifiConf.allowedProtocols.set(WifiConfiguration.Protocol.WPA); // For WPA
        wifiConf.allowedProtocols.set(WifiConfiguration.Protocol.RSN); // For WPA2
        wifiConf.allowedPairwiseCiphers.set(WifiConfiguration.PairwiseCipher.TKIP);
        wifiConf.allowedPairwiseCiphers.set(WifiConfiguration.PairwiseCipher.CCMP);
        wifiConf.allowedGroupCiphers.set(WifiConfiguration.GroupCipher.TKIP);
        wifiConf.allowedGroupCiphers.set(WifiConfiguration.GroupCipher.CCMP);
        if (!TextUtils.isEmpty(wifiPassword)) {
            wifiConf.preSharedKey = "\"" + wifiPassword + "\"";
        }
    }

    private void updateForWEPConfiguration(WifiConfiguration wifiConf, String password) {
        wifiConf.allowedKeyManagement.set(WifiConfiguration.KeyMgmt.NONE);
        wifiConf.allowedAuthAlgorithms.set(WifiConfiguration.AuthAlgorithm.OPEN);
        wifiConf.allowedAuthAlgorithms.set(WifiConfiguration.AuthAlgorithm.SHARED);
        wifiConf.allowedGroupCiphers.set(WifiConfiguration.GroupCipher.WEP40);
        wifiConf.allowedGroupCiphers.set(WifiConfiguration.GroupCipher.WEP104);
        wifiConf.allowedGroupCiphers.set(WifiConfiguration.GroupCipher.TKIP);
        wifiConf.allowedGroupCiphers.set(WifiConfiguration.GroupCipher.CCMP);
        int length = password.length();
        if ((length == 10 || length == 26 || length == 58) && password.matches("[0-9A-Fa-f]*")) {
            wifiConf.wepKeys[0] = password;
        } else {
            wifiConf.wepKeys[0] = '"' + password + '"';
        }
        wifiConf.wepTxKeyIndex = 0;
    }

    private void updateForProxy(WifiConfiguration wifiConf, String proxyHost, int proxyPort,
            String proxyBypassHosts, String pacUrl) {
        if (TextUtils.isEmpty(proxyHost) && TextUtils.isEmpty(pacUrl)) {
            return;
        }
        if (!TextUtils.isEmpty(proxyHost)) {
            ProxyInfo proxy = new ProxyInfo(proxyHost, proxyPort, proxyBypassHosts);
            wifiConf.setProxy(ProxySettings.STATIC, proxy);
        } else {
            ProxyInfo proxy = new ProxyInfo(pacUrl);
            wifiConf.setProxy(ProxySettings.PAC, proxy);
        }
    }
}
