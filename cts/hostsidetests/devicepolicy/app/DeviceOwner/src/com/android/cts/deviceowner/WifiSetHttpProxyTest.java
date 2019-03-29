/*
 * Copyright (C) 2017 The Android Open Source Project
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

import com.android.compatibility.common.util.WifiConfigCreator;

/**
 * Tests that DeviceOwner can add WifiConfigurations containing a HttpProxy
 */
public class WifiSetHttpProxyTest extends BaseDeviceOwnerTest {

    private static final String TAG = "WifiSetHttpProxyTest";
    private static final String TEST_PAC_URL = "http://www.example.com/proxy.pac";
    private static final String TEST_SSID = "SomeProxyApSsid";
    private static final int FAILURE_NETWORK_ID = -1;

    @Override
    protected void setUp() throws Exception {
        super.setUp();
    }

    /**
     * Test WifiManager.addNetwork() succeeds for a DeviceOwner adding a WifiConfiguraiton
     * containing a HttpProxy.
     * 2. Creates a new WifiConfiguration with ssid TEST_SSID
     * 3. Adds a PAC proxy file URL to the WifiConfiguraiton
     * 4. Adds the WifiConfiguration via WifiManager.addNetwork(), expects success
     * 5. Verifies the added WifiConfiguration has the same proxy
     */
    public void testSetHttpProxy() throws Exception {
        PackageManager packageManager = getContext().getPackageManager();
        if (!packageManager.hasSystemFeature(PackageManager.FEATURE_WIFI)) {
            // skip the test if WiFi is not supported
            return;
        }
        WifiConfigCreator configCreator = new WifiConfigCreator(getContext());
        String retreievedPacProxyUrl = configCreator.addHttpProxyNetworkVerifyAndRemove(
                TEST_SSID, TEST_PAC_URL);
        assertEquals(TEST_PAC_URL, retreievedPacProxyUrl);
    }
}
