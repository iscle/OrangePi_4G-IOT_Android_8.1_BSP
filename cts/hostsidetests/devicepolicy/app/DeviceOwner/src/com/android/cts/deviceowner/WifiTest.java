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
package com.android.cts.deviceowner;

import android.content.pm.PackageManager;
import android.text.TextUtils;

/**
 * Tests that require the WiFi feature.
 */
public class WifiTest extends BaseDeviceOwnerTest {
    public void testGetWifiMacAddress() {
        if (!mContext.getPackageManager().hasSystemFeature(PackageManager.FEATURE_WIFI)) {
            // wifi not supported.
            return;
        }
        final String macAddress = mDevicePolicyManager.getWifiMacAddress(getWho());

        assertFalse("Device owner should be able to get the real MAC address",
                "02:00:00:00:00:00".equals(macAddress));
        assertFalse("getWifiMacAddress() returned an empty string.  WiFi not enabled?",
                TextUtils.isEmpty(macAddress));
    }
}
