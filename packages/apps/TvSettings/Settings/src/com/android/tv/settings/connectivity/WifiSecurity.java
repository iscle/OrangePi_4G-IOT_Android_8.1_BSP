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
import android.net.wifi.ScanResult;
import android.net.wifi.WifiConfiguration;

import com.android.settingslib.wifi.AccessPoint;
import com.android.tv.settings.R;

/**
 * Used to identify different wifi security types
 * TODO: delete this
 */
public enum WifiSecurity {
    WEP(R.string.wifi_security_type_wep),
    PSK(R.string.wifi_security_type_wpa),
    EAP(R.string.wifi_security_type_eap),
    NONE(R.string.wifi_security_type_none);

    public static WifiSecurity getSecurity(ScanResult result) {
        if (result.capabilities.contains("WEP")) {
            return WEP;
        } else if (result.capabilities.contains("PSK")) {
            return PSK;
        } else if (result.capabilities.contains("EAP")) {
            return EAP;
        }
        return NONE;
    }

    public static WifiSecurity getSecurity(WifiConfiguration config) {
        if (config.allowedKeyManagement.get(WifiConfiguration.KeyMgmt.WPA_PSK)) {
            return PSK;
        }
        if (config.allowedKeyManagement.get(WifiConfiguration.KeyMgmt.WPA_EAP)
                || config.allowedKeyManagement.get(WifiConfiguration.KeyMgmt.IEEE8021X)) {
            return EAP;
        }
        return (config.wepKeys[0] != null) ? WEP : NONE;
    }

    public static WifiSecurity getSecurity(AccessPoint accessPoint) {
        switch (accessPoint.getSecurity()) {
            case AccessPoint.SECURITY_WEP: return WEP;
            case AccessPoint.SECURITY_PSK: return PSK;
            case AccessPoint.SECURITY_EAP: return EAP;
            case AccessPoint.SECURITY_NONE: return NONE;
        }

        throw new IllegalArgumentException("Unknown security type");
    }

    private final int mNameResourceId;

    private WifiSecurity(int nameResourceId) {
        mNameResourceId = nameResourceId;
    }

    public String getName(Context context) {
        return context.getString(mNameResourceId);
    }

    public boolean isOpen() {
        return this == NONE;
    }
}
