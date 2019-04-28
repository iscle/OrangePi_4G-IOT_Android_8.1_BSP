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
package com.android.managedprovisioning.model;

import android.os.Bundle;
import android.os.Parcel;
import android.test.AndroidTestCase;
import android.test.MoreAsserts;
import android.test.suitebuilder.annotation.SmallTest;

import com.android.managedprovisioning.model.WifiInfo;

import java.lang.Exception;

/** Tests for {@link WifiInfo} */
public class WifiInfoTest extends AndroidTestCase {
    private static final String TEST_SSID = "TestWifi";
    private static final boolean TEST_HIDDEN = true;
    private static final String TEST_HIDDEN_STR = Boolean.toString(TEST_HIDDEN);
    private static final String TEST_SECURITY_TYPE = "WPA2";
    private static final String TEST_PASSWORD = "TestPassword";
    private static final String TEST_PROXY_HOST = "proxy.example.com";
    private static final int TEST_PROXY_PORT = 7689;
    private static final String TEST_PROXY_PORT_STR = Integer.toString(TEST_PROXY_PORT);
    private static final String TEST_PROXY_BYPASS_HOSTS = "http://host1.com;https://host2.com";
    private static final String TEST_PAC_URL = "pac.example.com";

    @SmallTest
    public void testBuilderWriteAndReadBack() {
        // WHEN a WifiInfo object is constructed by a set of parameters.
        WifiInfo wifiInfo = WifiInfo.Builder.builder()
                .setSsid(TEST_SSID)
                .setHidden(TEST_HIDDEN)
                .setSecurityType(TEST_SECURITY_TYPE)
                .setPassword(TEST_PASSWORD)
                .setProxyHost(TEST_PROXY_HOST)
                .setProxyPort(TEST_PROXY_PORT)
                .setProxyBypassHosts(TEST_PROXY_BYPASS_HOSTS)
                .setPacUrl(TEST_PAC_URL)
                .build();
        // THEN the same set of values are set to the object.
        assertEquals(TEST_SSID, wifiInfo.ssid);
        assertEquals(TEST_HIDDEN, wifiInfo.hidden);
        assertEquals(TEST_SECURITY_TYPE, wifiInfo.securityType);
        assertEquals(TEST_PASSWORD, wifiInfo.password);
        assertEquals(TEST_PROXY_HOST, wifiInfo.proxyHost);
        assertEquals(TEST_PROXY_PORT, wifiInfo.proxyPort);
        assertEquals(TEST_PROXY_BYPASS_HOSTS, wifiInfo.proxyBypassHosts);
        assertEquals(TEST_PAC_URL, wifiInfo.pacUrl);
    }

    @SmallTest
    public void testFailToConstructWifiInfoWithoutSsid() {
        try {
            // WHEN a WifiInfo object is constructed without Ssid.
            WifiInfo wifiInfo = WifiInfo.Builder.builder()
                    .setHidden(TEST_HIDDEN)
                    .setSecurityType(TEST_SECURITY_TYPE)
                    .setPassword(TEST_PASSWORD)
                    .setProxyHost(TEST_PROXY_HOST)
                    .setProxyPort(TEST_PROXY_PORT)
                    .setProxyBypassHosts(TEST_PROXY_BYPASS_HOSTS)
                    .setPacUrl(TEST_PAC_URL)
                    .build();
            fail("Ssid is mandatory.");
        } catch (IllegalArgumentException e) {
            // THEN the WifiInfo object fails to construct due to missing ssid.
        }
    }

    @SmallTest
    public void testEquals() {
        // GIVEN 2 WifiInfo objects are constructed with the same set of parameters.
        WifiInfo wifiInfo1 = WifiInfo.Builder.builder()
                .setSsid(TEST_SSID)
                .setHidden(TEST_HIDDEN)
                .setSecurityType(TEST_SECURITY_TYPE)
                .setPassword(TEST_PASSWORD)
                .setProxyHost(TEST_PROXY_HOST)
                .setProxyPort(TEST_PROXY_PORT)
                .setProxyBypassHosts(TEST_PROXY_BYPASS_HOSTS)
                .setPacUrl(TEST_PAC_URL)
                .build();
        WifiInfo wifiInfo2 = WifiInfo.Builder.builder()
                .setSsid(TEST_SSID)
                .setHidden(TEST_HIDDEN)
                .setSecurityType(TEST_SECURITY_TYPE)
                .setPassword(TEST_PASSWORD)
                .setProxyHost(TEST_PROXY_HOST)
                .setProxyPort(TEST_PROXY_PORT)
                .setProxyBypassHosts(TEST_PROXY_BYPASS_HOSTS)
                .setPacUrl(TEST_PAC_URL)
                .build();
        // WHEN comparing these two objects.
        // THEN they are equal.
        assertEquals(wifiInfo1, wifiInfo2);
    }

    @SmallTest
    public void testNotEquals() {
        // GIVEN 2 WifiInfo objects are constructed with the same set of parameters.
        WifiInfo wifiInfo1 = WifiInfo.Builder.builder()
                .setSsid(TEST_SSID)
                .setHidden(TEST_HIDDEN)
                .setSecurityType(TEST_SECURITY_TYPE)
                .setPassword(TEST_PASSWORD)
                .setProxyHost(TEST_PROXY_HOST)
                .setProxyPort(TEST_PROXY_PORT)
                .setProxyBypassHosts(TEST_PROXY_BYPASS_HOSTS)
                .setPacUrl(TEST_PAC_URL)
                .build();
        WifiInfo wifiInfo2 = WifiInfo.Builder.builder()
                .setSsid("TestWifi2")
                .setHidden(TEST_HIDDEN)
                .setSecurityType(TEST_SECURITY_TYPE)
                .setPassword(TEST_PASSWORD)
                .setProxyHost(TEST_PROXY_HOST)
                .setProxyPort(TEST_PROXY_PORT)
                .setProxyBypassHosts(TEST_PROXY_BYPASS_HOSTS)
                .setPacUrl(TEST_PAC_URL)
                .build();
        // WHEN comparing these two objects.
        // THEN they are not equal.
        MoreAsserts.assertNotEqual(wifiInfo1, wifiInfo2);
    }

    @SmallTest
    public void testParceable() {
        // GIVEN a WifiInfo object.
        WifiInfo expectedWifiInfo = WifiInfo.Builder.builder()
                .setSsid(TEST_SSID)
                .setHidden(TEST_HIDDEN)
                .setSecurityType(TEST_SECURITY_TYPE)
                .setPassword(TEST_PASSWORD)
                .setProxyHost(TEST_PROXY_HOST)
                .setProxyPort(TEST_PROXY_PORT)
                .setProxyBypassHosts(TEST_PROXY_BYPASS_HOSTS)
                .setPacUrl(TEST_PAC_URL)
                .build();

        // WHEN the WifiInfo is written to parcel and then read back.
        Parcel parcel = Parcel.obtain();
        expectedWifiInfo.writeToParcel(parcel, 0);
        parcel.setDataPosition(0);
        WifiInfo actualWifiInfo = WifiInfo.CREATOR.createFromParcel(parcel);

        // THEN the same WifiInfo is obtained.
        assertEquals(expectedWifiInfo, actualWifiInfo);
    }
}
