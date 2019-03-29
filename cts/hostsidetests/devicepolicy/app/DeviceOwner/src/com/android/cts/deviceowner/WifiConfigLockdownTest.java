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
 * limitations under the License.
 */

package com.android.cts.deviceowner;

import android.content.Context;
import android.content.Intent;
import android.net.wifi.WifiConfiguration;
import android.net.wifi.WifiManager;
import android.provider.Settings;

import com.android.compatibility.common.util.WifiConfigCreator;

import java.util.List;

import static com.android.compatibility.common.util.WifiConfigCreator.ACTION_CREATE_WIFI_CONFIG;
import static com.android.compatibility.common.util.WifiConfigCreator.EXTRA_NETID;
import static com.android.compatibility.common.util.WifiConfigCreator.EXTRA_PASSWORD;
import static com.android.compatibility.common.util.WifiConfigCreator.EXTRA_SECURITY_TYPE;
import static com.android.compatibility.common.util.WifiConfigCreator.EXTRA_SSID;
import static com.android.compatibility.common.util.WifiConfigCreator.ACTION_REMOVE_WIFI_CONFIG;
import static com.android.compatibility.common.util.WifiConfigCreator.SECURITY_TYPE_NONE;
import static com.android.compatibility.common.util.WifiConfigCreator.SECURITY_TYPE_WPA;
import static com.android.compatibility.common.util.WifiConfigCreator.ACTION_UPDATE_WIFI_CONFIG;

/**
 * Testing WiFi configuration lockdown by Device Owner
 */
public class WifiConfigLockdownTest extends BaseDeviceOwnerTest {
    private static final String TAG = "WifiConfigLockdownTest";
    private static final String ORIGINAL_DEVICE_OWNER_SSID = "DOCTSTest";
    private static final String CHANGED_DEVICE_OWNER_SSID = "DOChangedCTSTest";
    private static final String ORIGINAL_REGULAR_SSID = "RegularCTSTest";
    private static final String CHANGED_REGULAR_SSID = "RegularChangedCTSTest";
    private static final String ORIGINAL_PASSWORD = "originalpassword";
    private WifiManager mWifiManager;
    private WifiConfigCreator mConfigCreator;

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        mWifiManager = (WifiManager) mContext.getSystemService(Context.WIFI_SERVICE);
        mConfigCreator = new WifiConfigCreator(mContext);
        mDevicePolicyManager.setGlobalSetting(getWho(),
                Settings.Global.WIFI_DEVICE_OWNER_CONFIGS_LOCKDOWN, "1");
        mConfigCreator.addNetwork(ORIGINAL_DEVICE_OWNER_SSID, true, SECURITY_TYPE_WPA,
                ORIGINAL_PASSWORD);
        startRegularActivity(ACTION_CREATE_WIFI_CONFIG, -1, ORIGINAL_REGULAR_SSID,
                SECURITY_TYPE_WPA, ORIGINAL_PASSWORD);
    }

    @Override
    protected void tearDown() throws Exception {
        mDevicePolicyManager.setGlobalSetting(getWho(),
                Settings.Global.WIFI_DEVICE_OWNER_CONFIGS_LOCKDOWN, "0");
        List<WifiConfiguration> configs = mWifiManager.getConfiguredNetworks();
        for (WifiConfiguration config : configs) {
            if (areMatchingSsids(ORIGINAL_DEVICE_OWNER_SSID, config.SSID) ||
                    areMatchingSsids(CHANGED_DEVICE_OWNER_SSID, config.SSID) ||
                    areMatchingSsids(ORIGINAL_REGULAR_SSID, config.SSID) ||
                    areMatchingSsids(CHANGED_REGULAR_SSID, config.SSID)) {
                mWifiManager.removeNetwork(config.networkId);
            }
        }
        super.tearDown();
    }

    public void testDeviceOwnerCanUpdateConfig() throws Exception {
        List<WifiConfiguration> configs = mWifiManager.getConfiguredNetworks();
        int updateCount = 0;
        for (WifiConfiguration config : configs) {
            if (areMatchingSsids(ORIGINAL_DEVICE_OWNER_SSID, config.SSID)) {
                assertFalse(-1 == mConfigCreator.updateNetwork(config,
                        CHANGED_DEVICE_OWNER_SSID, true, SECURITY_TYPE_NONE, null));
                ++updateCount;
            }
            if (areMatchingSsids(ORIGINAL_REGULAR_SSID, config.SSID)) {
                assertFalse(-1 == mConfigCreator.updateNetwork(config,
                        CHANGED_REGULAR_SSID, true, SECURITY_TYPE_NONE, null));
                ++updateCount;
            }
        }
        assertEquals("Expected to update two configs: the DO created one and the regular one." +
                " Instead updated: " + updateCount, 2, updateCount);
    }

    public void testDeviceOwnerCanRemoveConfig() throws Exception {
        List<WifiConfiguration> configs = mWifiManager.getConfiguredNetworks();
        int removeCount = 0;
        for (WifiConfiguration config : configs) {
            if (areMatchingSsids(ORIGINAL_DEVICE_OWNER_SSID, config.SSID) ||
                    areMatchingSsids(ORIGINAL_REGULAR_SSID, config.SSID)) {
                assertTrue(mWifiManager.removeNetwork(config.networkId));
                ++removeCount;
            }
        }
        assertEquals("Expected to remove two configs: the DO created one and the regular one." +
                " Instead removed: " + removeCount, 2, removeCount);
    }

    public void testRegularAppCannotUpdateDeviceOwnerConfig() throws Exception {
        List<WifiConfiguration> configs = mWifiManager.getConfiguredNetworks();
        int updateCount = 0;
        for (WifiConfiguration config : configs) {
            if (areMatchingSsids(ORIGINAL_DEVICE_OWNER_SSID, config.SSID)) {
                startRegularActivity(ACTION_UPDATE_WIFI_CONFIG, config.networkId,
                        CHANGED_DEVICE_OWNER_SSID, SECURITY_TYPE_NONE, null);
                ++updateCount;
            }
        }
        assertEquals("Expected to have tried to update one config: the DO created one" +
                " Instead tried to update: " + updateCount, 1, updateCount);

        // Assert nothing has changed
        configs = mWifiManager.getConfiguredNetworks();
        int notChangedCount = 0;
        for (WifiConfiguration config : configs) {
            assertFalse(areMatchingSsids(CHANGED_DEVICE_OWNER_SSID, config.SSID));
            if (areMatchingSsids(ORIGINAL_DEVICE_OWNER_SSID, config.SSID)) {
                ++notChangedCount;
            }
        }
        assertEquals("Expected to see one unchanged config, saw instead: " + notChangedCount, 1,
                notChangedCount);
    }

    public void testRegularAppCannotRemoveDeviceOwnerConfig() throws Exception {
        List<WifiConfiguration> configs = mWifiManager.getConfiguredNetworks();
        int removeCount = 0;
        for (WifiConfiguration config : configs) {
            if (areMatchingSsids(ORIGINAL_DEVICE_OWNER_SSID, config.SSID)) {
                startRegularActivity(ACTION_REMOVE_WIFI_CONFIG, config.networkId,
                        null, SECURITY_TYPE_NONE, null);
                ++removeCount;
            }
        }
        assertEquals("Expected to try to remove one config: the DO created one." +
                " Instead tried to remove: " + removeCount, 1, removeCount);

        // Assert nothing has changed
        configs = mWifiManager.getConfiguredNetworks();
        int notChangedCount = 0;
        for (WifiConfiguration config : configs) {
            if (areMatchingSsids(ORIGINAL_DEVICE_OWNER_SSID, config.SSID)) {
                ++notChangedCount;
            }
        }
        assertEquals("Expected to see one unchanged config, saw instead: " + notChangedCount, 1,
                notChangedCount);
    }

    private void startRegularActivity(String action, int netId, String ssid, int securityType,
            String password) throws InterruptedException {
        Intent createRegularConfig = new Intent(action);
        createRegularConfig.putExtra(EXTRA_NETID, netId);
        createRegularConfig.putExtra(EXTRA_SSID, ssid);
        createRegularConfig.putExtra(EXTRA_SECURITY_TYPE, securityType);
        createRegularConfig.putExtra(EXTRA_PASSWORD, password);
        createRegularConfig.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        mContext.startActivity(createRegularConfig);

        // Give some time for the other app to finish the action
        Thread.sleep(2000);
    }

    private boolean areMatchingSsids(String s1, String s2) {
        if (s1 == null || s2 == null) {
            return false;
        }
        return s1.replace("\"", "").equals(s2.replace("\"", ""));
    }
}
