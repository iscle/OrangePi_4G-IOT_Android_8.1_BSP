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

import java.util.Comparator;

import android.net.wifi.ScanResult;

/**
 * Comparator that sorts Wifi scan results by signal strength and network name.
 */
public class ScanResultComparator implements Comparator<ScanResult> {

    private final String mConnectedSSID;
    private final WifiSecurity mConnectedSecurity;

    public ScanResultComparator(String connectedSSID, WifiSecurity connectedSecurity) {
        mConnectedSSID = connectedSSID;
        mConnectedSecurity = connectedSecurity;
    }

    public ScanResultComparator() {
        mConnectedSSID = null;
        mConnectedSecurity = WifiSecurity.NONE;
    }

    @Override
    public int compare(ScanResult result1, ScanResult result2) {
        if (result1 == null) {
            if (result2 == null) {
                return 0;
            } else {
                return 1;
            }
        } else {
            if (result2 == null) {
                return -1;
            } else {
                WifiSecurity security1 = WifiSecurity.getSecurity(result1);
                WifiSecurity security2 = WifiSecurity.getSecurity(result2);
                if (mConnectedSSID != null) {
                    if (result1.SSID.equals(mConnectedSSID)
                            && security1.equals(mConnectedSecurity)) {
                        return -1;
                    }
                    if (result2.SSID.equals(mConnectedSSID)
                            && security2.equals(mConnectedSecurity)) {
                        return 1;
                    }
                }
                int levelDiff = result2.level - result1.level;
                if (levelDiff != 0) {
                    return levelDiff;
                }
                if (result1.SSID.equals(result2.SSID)) {
                    return security1.compareTo(security2);
                }
                return result1.SSID.compareTo(result2.SSID);
            }
        }
    }
}
