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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import android.net.IpConfiguration;
import android.net.wifi.WifiConfiguration;
import android.support.test.filters.SmallTest;

import com.android.managedprovisioning.model.WifiInfo;

import org.junit.Test;

/**
 * Unit test for {@link WifiConfigurationProvider}.
 */
@SmallTest
public class WifiConfigurationProviderTest {
    private static final String TEST_SSID = "test_ssid";
    private static final boolean TEST_HIDDEN = true;
    private static final String TEST_PAC_URL = "test.pac.url";
    private static final String TEST_PROXY_BYPASS_HOST = "testProxyBypassHost";
    private static final String TEST_PROXY_HOST = "TestProxyHost";
    private static final int TEST_PROXY_PORT = 1234;
    private static final String TEST_PASSWORD = "testPassword";
    private static final String TEST_PASSWORD_WEP = "0123456789"; // length needs to be 10

    private static final WifiInfo.Builder BASE_BUILDER = new WifiInfo.Builder()
            .setSsid(TEST_SSID)
            .setHidden(TEST_HIDDEN);

    private static final WifiInfo WIFI_INFO_WPA = BASE_BUILDER
            .setSecurityType(WifiConfigurationProvider.WPA)
            .setPassword(TEST_PASSWORD)
            .build();

    private static final WifiInfo WIFI_INFO_WEP = BASE_BUILDER
            .setSecurityType(WifiConfigurationProvider.WEP)
            .setPassword(TEST_PASSWORD)
            .build();

    private static final WifiInfo WIFI_INFO_WEP_2 = BASE_BUILDER
            .setSecurityType(WifiConfigurationProvider.WEP)
            .setPassword(TEST_PASSWORD_WEP)
            .build();

    private static final WifiInfo WIFI_INFO_NONE = BASE_BUILDER
            .setSecurityType(WifiConfigurationProvider.NONE)
            .build();

    private static final WifiInfo WIFI_INFO_NULL = BASE_BUILDER
            .build();

    private static final WifiInfo WIFI_INFO_PAC = BASE_BUILDER
            .setPacUrl(TEST_PAC_URL)
            .build();

    private static final WifiInfo WIFI_INFO_PROXY = BASE_BUILDER
            .setProxyBypassHosts(TEST_PROXY_BYPASS_HOST)
            .setProxyHost(TEST_PROXY_HOST)
            .setProxyPort(TEST_PROXY_PORT)
            .build();


    private final WifiConfigurationProvider mProvider = new WifiConfigurationProvider();

    @Test
    public void testWpa() {
        WifiConfiguration wifiConf = mProvider.generateWifiConfiguration(WIFI_INFO_WPA);

        assertBase(wifiConf);
        assertTrue(wifiConf.allowedKeyManagement.get(WifiConfiguration.KeyMgmt.WPA_PSK));
        assertTrue(wifiConf.allowedProtocols.get(WifiConfiguration.Protocol.WPA));
        assertEquals("\"" + TEST_PASSWORD + "\"", wifiConf.preSharedKey);
        assertEquals(IpConfiguration.ProxySettings.UNASSIGNED, wifiConf.getProxySettings());
    }

    @Test
    public void testWep() {
        WifiConfiguration wifiConf = mProvider.generateWifiConfiguration(WIFI_INFO_WEP);

        assertBase(wifiConf);
        assertTrue(wifiConf.allowedKeyManagement.get(WifiConfiguration.KeyMgmt.NONE));
        assertEquals("\"" + TEST_PASSWORD + "\"", wifiConf.wepKeys[0]);
        assertEquals(IpConfiguration.ProxySettings.UNASSIGNED, wifiConf.getProxySettings());
    }

    @Test
    public void testWep2() {
        WifiConfiguration wifiConf = mProvider.generateWifiConfiguration(WIFI_INFO_WEP_2);

        assertBase(wifiConf);
        assertTrue(wifiConf.allowedKeyManagement.get(WifiConfiguration.KeyMgmt.NONE));
        assertEquals(TEST_PASSWORD_WEP, wifiConf.wepKeys[0]);
        assertEquals(IpConfiguration.ProxySettings.UNASSIGNED, wifiConf.getProxySettings());
    }

    @Test
    public void testNone() {
        WifiConfiguration wifiConf = mProvider.generateWifiConfiguration(WIFI_INFO_NONE);

        assertBase(wifiConf);
        assertTrue(wifiConf.allowedKeyManagement.get(WifiConfiguration.KeyMgmt.NONE));
        assertTrue(wifiConf.allowedAuthAlgorithms.get(WifiConfiguration.AuthAlgorithm.OPEN));
        assertEquals(IpConfiguration.ProxySettings.UNASSIGNED, wifiConf.getProxySettings());
    }

    @Test
    public void testNull() {
        WifiConfiguration wifiConf = mProvider.generateWifiConfiguration(WIFI_INFO_NULL);

        assertBase(wifiConf);
        assertTrue(wifiConf.allowedKeyManagement.get(WifiConfiguration.KeyMgmt.NONE));
        assertTrue(wifiConf.allowedAuthAlgorithms.get(WifiConfiguration.AuthAlgorithm.OPEN));
        assertEquals(IpConfiguration.ProxySettings.UNASSIGNED, wifiConf.getProxySettings());
    }

    @Test
    public void testPac() {
        WifiConfiguration wifiConf = mProvider.generateWifiConfiguration(WIFI_INFO_PAC);

        assertBase(wifiConf);
        assertEquals(IpConfiguration.ProxySettings.PAC, wifiConf.getProxySettings());
    }

    @Test
    public void testStaticProxy() {
        WifiConfiguration wifiConf = mProvider.generateWifiConfiguration(WIFI_INFO_PROXY);

        assertBase(wifiConf);
        assertEquals(IpConfiguration.ProxySettings.STATIC, wifiConf.getProxySettings());
    }

    private void assertBase(WifiConfiguration wifiConf) {
        assertEquals(TEST_SSID, wifiConf.SSID);
        assertEquals(TEST_HIDDEN, wifiConf.hiddenSSID);
        assertEquals(WifiConfiguration.Status.ENABLED, wifiConf.status);
        assertEquals(WifiConfiguration.USER_APPROVED, wifiConf.userApproved);
    }
}
