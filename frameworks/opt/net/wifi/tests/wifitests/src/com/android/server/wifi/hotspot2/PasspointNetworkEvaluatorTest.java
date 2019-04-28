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

package com.android.server.wifi.hotspot2;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.anyInt;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.MockitoAnnotations.initMocks;

import android.net.wifi.ScanResult;
import android.net.wifi.WifiConfiguration;
import android.net.wifi.hotspot2.PasspointConfiguration;
import android.net.wifi.hotspot2.pps.HomeSp;
import android.test.suitebuilder.annotation.SmallTest;
import android.util.LocalLog;
import android.util.Pair;

import com.android.server.wifi.NetworkUpdateResult;
import com.android.server.wifi.ScanDetail;
import com.android.server.wifi.WifiConfigManager;
import com.android.server.wifi.util.ScanResultUtil;

import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Unit tests for {@link com.android.server.wifi.hotspot2.PasspointNetworkEvaluator}.
 */
@SmallTest
public class PasspointNetworkEvaluatorTest {
    private static final int TEST_NETWORK_ID = 1;
    private static final String TEST_SSID1 = "ssid1";
    private static final String TEST_SSID2 = "ssid2";
    private static final String TEST_BSSID1 = "01:23:45:56:78:9a";
    private static final String TEST_BSSID2 = "23:12:34:90:81:12";
    private static final String TEST_FQDN1 = "test1.com";
    private static final String TEST_FQDN2 = "test2.com";
    private static final WifiConfiguration TEST_CONFIG1 = generateWifiConfig(TEST_FQDN1);
    private static final WifiConfiguration TEST_CONFIG2 = generateWifiConfig(TEST_FQDN2);
    private static final PasspointProvider TEST_PROVIDER1 = generateProvider(TEST_CONFIG1);
    private static final PasspointProvider TEST_PROVIDER2 = generateProvider(TEST_CONFIG2);

    @Mock PasspointManager mPasspointManager;
    @Mock WifiConfigManager mWifiConfigManager;
    LocalLog mLocalLog;
    PasspointNetworkEvaluator mEvaluator;

    /**
     * Helper function for generating {@link WifiConfiguration} for testing.
     *
     * @param fqdn The FQDN associated with the configuration
     * @return {@link WifiConfiguration}
     */
    private static WifiConfiguration generateWifiConfig(String fqdn) {
        WifiConfiguration config = new WifiConfiguration();
        config.FQDN = fqdn;
        return config;
    }

    /**
     * Helper function for generating {@link PasspointProvider} for testing.
     *
     * @param config The WifiConfiguration associated with the provider
     * @return {@link PasspointProvider}
     */
    private static PasspointProvider generateProvider(WifiConfiguration config) {
        PasspointProvider provider = mock(PasspointProvider.class);
        PasspointConfiguration passpointConfig = new PasspointConfiguration();
        HomeSp homeSp = new HomeSp();
        homeSp.setFqdn(config.FQDN);
        passpointConfig.setHomeSp(homeSp);
        when(provider.getConfig()).thenReturn(passpointConfig);
        when(provider.getWifiConfig()).thenReturn(config);
        return provider;
    }

    /**
     * Helper function for generating {@link ScanDetail} for testing.
     *
     * @param ssid The SSID associated with the scan
     * @param rssiLevel The RSSI level associated with the scan
     * @return {@link ScanDetail}
     */
    private static ScanDetail generateScanDetail(String ssid, String bssid) {
        NetworkDetail networkDetail = mock(NetworkDetail.class);
        when(networkDetail.isInterworking()).thenReturn(true);
        when(networkDetail.getAnt()).thenReturn(NetworkDetail.Ant.FreePublic);

        ScanDetail scanDetail = mock(ScanDetail.class);
        ScanResult scanResult = new ScanResult();
        scanResult.SSID = ssid;
        scanResult.BSSID = bssid;
        when(scanDetail.getSSID()).thenReturn(ssid);
        when(scanDetail.getBSSIDString()).thenReturn(bssid);
        when(scanDetail.getScanResult()).thenReturn(scanResult);
        when(scanDetail.getNetworkDetail()).thenReturn(networkDetail);
        return scanDetail;
    }

    /**
     * Test setup.
     */
    @Before
    public void setUp() throws Exception {
        initMocks(this);
        mLocalLog = new LocalLog(512);
        mEvaluator = new PasspointNetworkEvaluator(mPasspointManager, mWifiConfigManager,
                mLocalLog);
    }

    /**
     * Verify that null will be returned when evaluating scans without any matching providers.
     *
     * @throws Exception
     */
    @Test
    public void evaluateScansWithNoMatch() throws Exception {
        List<ScanDetail> scanDetails = Arrays.asList(new ScanDetail[] {
                generateScanDetail(TEST_SSID1, TEST_BSSID1),
                generateScanDetail(TEST_SSID2, TEST_BSSID2)});
        List<Pair<ScanDetail, WifiConfiguration>> connectableNetworks = new ArrayList<>();
        when(mPasspointManager.matchProvider(any(ScanResult.class))).thenReturn(null);
        assertEquals(null, mEvaluator.evaluateNetworks(
                scanDetails, null, null, false, false, connectableNetworks));
        assertTrue(connectableNetworks.isEmpty());
    }

    /**
     * Verify that provider matching will not be performed when evaluating scans with no
     * interworking support, and null will be returned.
     *
     * @throws Exception
     */
    @Test
    public void evaulateScansWithNoInterworkingAP() throws Exception {
        NetworkDetail networkDetail = mock(NetworkDetail.class);
        when(networkDetail.isInterworking()).thenReturn(false);
        ScanDetail scanDetail = mock(ScanDetail.class);
        when(scanDetail.getNetworkDetail()).thenReturn(networkDetail);

        List<ScanDetail> scanDetails = Arrays.asList(new ScanDetail[] {scanDetail});
        List<Pair<ScanDetail, WifiConfiguration>> connectableNetworks = new ArrayList<>();
        assertEquals(null, mEvaluator.evaluateNetworks(
                scanDetails, null, null, false, false, connectableNetworks));
        assertTrue(connectableNetworks.isEmpty());
        // Verify that no provider matching is performed.
        verify(mPasspointManager, never()).matchProvider(any(ScanResult.class));
    }

    /**
     * Verify that when a network matches a home provider is found, the correct network
     * information (WifiConfiguration) is setup and returned.
     *
     * @throws Exception
     */
    @Test
    public void evaluateScansWithNetworkMatchingHomeProvider() throws Exception {
        List<ScanDetail> scanDetails = Arrays.asList(new ScanDetail[] {
                generateScanDetail(TEST_SSID1, TEST_BSSID1),
                generateScanDetail(TEST_SSID2, TEST_BSSID2)});

        // Setup matching providers for ScanDetail with TEST_SSID1.
        Pair<PasspointProvider, PasspointMatch> homeProvider = Pair.create(
                TEST_PROVIDER1, PasspointMatch.HomeProvider);

        List<Pair<ScanDetail, WifiConfiguration>> connectableNetworks = new ArrayList<>();

        // Return homeProvider for the first ScanDetail (TEST_SSID1) and a null (no match) for
        // for the second (TEST_SSID2);
        when(mPasspointManager.matchProvider(any(ScanResult.class))).thenReturn(homeProvider)
                .thenReturn(null);
        when(mWifiConfigManager.addOrUpdateNetwork(any(WifiConfiguration.class), anyInt()))
                .thenReturn(new NetworkUpdateResult(TEST_NETWORK_ID));
        when(mWifiConfigManager.getConfiguredNetwork(TEST_NETWORK_ID)).thenReturn(TEST_CONFIG1);
        assertNotNull(mEvaluator.evaluateNetworks(scanDetails, null, null, false,
                false, connectableNetworks));
        assertEquals(1, connectableNetworks.size());

        // Verify the content of the WifiConfiguration that was added to WifiConfigManager.
        ArgumentCaptor<WifiConfiguration> addedConfig =
                ArgumentCaptor.forClass(WifiConfiguration.class);
        verify(mWifiConfigManager).addOrUpdateNetwork(addedConfig.capture(), anyInt());
        assertEquals(ScanResultUtil.createQuotedSSID(TEST_SSID1), addedConfig.getValue().SSID);
        assertEquals(TEST_FQDN1, addedConfig.getValue().FQDN);
        assertTrue(addedConfig.getValue().isHomeProviderNetwork);
        verify(mWifiConfigManager).enableNetwork(eq(TEST_NETWORK_ID), eq(false), anyInt());
        verify(mWifiConfigManager).setNetworkCandidateScanResult(
                eq(TEST_NETWORK_ID), any(ScanResult.class), anyInt());
        verify(mWifiConfigManager).updateScanDetailForNetwork(
                eq(TEST_NETWORK_ID), any(ScanDetail.class));
    }

    /**
     * Verify that when a network matches a roaming provider is found, the correct network
     * information (WifiConfiguration) is setup and returned.
     *
     * @throws Exception
     */
    @Test
    public void evaluateScansWithNetworkMatchingRoamingProvider() throws Exception {
        List<ScanDetail> scanDetails = Arrays.asList(new ScanDetail[] {
                generateScanDetail(TEST_SSID1, TEST_BSSID1),
                generateScanDetail(TEST_SSID2, TEST_BSSID2)});

        // Setup matching providers for ScanDetail with TEST_SSID1.
        Pair<PasspointProvider, PasspointMatch> roamingProvider = Pair.create(
                TEST_PROVIDER1, PasspointMatch.RoamingProvider);

        List<Pair<ScanDetail, WifiConfiguration>> connectableNetworks = new ArrayList<>();

        // Return roamingProvider for the first ScanDetail (TEST_SSID1) and a null (no match) for
        // for the second (TEST_SSID2);
        when(mPasspointManager.matchProvider(any(ScanResult.class))).thenReturn(roamingProvider)
                .thenReturn(null);
        when(mWifiConfigManager.addOrUpdateNetwork(any(WifiConfiguration.class), anyInt()))
                .thenReturn(new NetworkUpdateResult(TEST_NETWORK_ID));
        when(mWifiConfigManager.getConfiguredNetwork(TEST_NETWORK_ID)).thenReturn(TEST_CONFIG1);
        assertNotNull(mEvaluator.evaluateNetworks(scanDetails, null, null, false,
                false, connectableNetworks));
        assertEquals(1, connectableNetworks.size());

        // Verify the content of the WifiConfiguration that was added to WifiConfigManager.
        ArgumentCaptor<WifiConfiguration> addedConfig =
                ArgumentCaptor.forClass(WifiConfiguration.class);
        verify(mWifiConfigManager).addOrUpdateNetwork(addedConfig.capture(), anyInt());
        assertEquals(ScanResultUtil.createQuotedSSID(TEST_SSID1), addedConfig.getValue().SSID);
        assertEquals(TEST_FQDN1, addedConfig.getValue().FQDN);
        assertFalse(addedConfig.getValue().isHomeProviderNetwork);
        verify(mWifiConfigManager).enableNetwork(eq(TEST_NETWORK_ID), eq(false), anyInt());
        verify(mWifiConfigManager).setNetworkCandidateScanResult(
                eq(TEST_NETWORK_ID), any(ScanResult.class), anyInt());
        verify(mWifiConfigManager).updateScanDetailForNetwork(
                eq(TEST_NETWORK_ID), any(ScanDetail.class));
    }

    /**
     * Verify that when a network matches a home provider and another network matches a roaming
     * provider are found, the network that matched to a home provider is preferred.
     *
     * @throws Exception
     */
    @Test
    public void evaluateScansWithHomeProviderNewtorkAndRoamingProviderNetwork() throws Exception {
        List<ScanDetail> scanDetails = Arrays.asList(new ScanDetail[] {
                generateScanDetail(TEST_SSID1, TEST_BSSID1),
                generateScanDetail(TEST_SSID2, TEST_BSSID2)});

        // Setup matching providers for ScanDetail with TEST_SSID1.
        Pair<PasspointProvider, PasspointMatch> homeProvider = Pair.create(
                TEST_PROVIDER1, PasspointMatch.HomeProvider);
        Pair<PasspointProvider, PasspointMatch> roamingProvider = Pair.create(
                TEST_PROVIDER2, PasspointMatch.RoamingProvider);

        List<Pair<ScanDetail, WifiConfiguration>> connectableNetworks = new ArrayList<>();

        // Return homeProvider for the first ScanDetail (TEST_SSID1) and
        // roamingProvider for the second (TEST_SSID2);
        when(mPasspointManager.matchProvider(any(ScanResult.class)))
                .thenReturn(homeProvider).thenReturn(roamingProvider);
        when(mWifiConfigManager.addOrUpdateNetwork(any(WifiConfiguration.class), anyInt()))
                .thenReturn(new NetworkUpdateResult(TEST_NETWORK_ID));
        when(mWifiConfigManager.getConfiguredNetwork(TEST_NETWORK_ID)).thenReturn(TEST_CONFIG1);
        assertNotNull(mEvaluator.evaluateNetworks(scanDetails, null, null, false,
                false, connectableNetworks));
        assertEquals(1, connectableNetworks.size());

        // Verify the content of the WifiConfiguration that was added to WifiConfigManager.
        ArgumentCaptor<WifiConfiguration> addedConfig =
                ArgumentCaptor.forClass(WifiConfiguration.class);
        verify(mWifiConfigManager).addOrUpdateNetwork(addedConfig.capture(), anyInt());
        assertEquals(ScanResultUtil.createQuotedSSID(TEST_SSID1), addedConfig.getValue().SSID);
        assertEquals(TEST_FQDN1, addedConfig.getValue().FQDN);
        assertTrue(addedConfig.getValue().isHomeProviderNetwork);
        verify(mWifiConfigManager).enableNetwork(eq(TEST_NETWORK_ID), eq(false), anyInt());
        verify(mWifiConfigManager).setNetworkCandidateScanResult(
                eq(TEST_NETWORK_ID), any(ScanResult.class), anyInt());
        verify(mWifiConfigManager).updateScanDetailForNetwork(
                eq(TEST_NETWORK_ID), any(ScanDetail.class));
    }

    /**
     * Verify that when two networks both matches a home provider, with one of them being the
     * active network, the active network is preferred.
     *
     * @throws Exception
     */
    @Test
    public void evaluateScansWithActiveNetworkMatchingHomeProvider() throws Exception {
        List<ScanDetail> scanDetails = Arrays.asList(new ScanDetail[] {
                generateScanDetail(TEST_SSID1, TEST_BSSID1),
                generateScanDetail(TEST_SSID2, TEST_BSSID2)});

        // Setup matching providers for both ScanDetail.
        Pair<PasspointProvider, PasspointMatch> homeProvider = Pair.create(
                TEST_PROVIDER1, PasspointMatch.HomeProvider);

        // Setup currently connected network
        WifiConfiguration currentNetwork = new WifiConfiguration();
        currentNetwork.networkId = TEST_NETWORK_ID;
        currentNetwork.SSID = ScanResultUtil.createQuotedSSID(TEST_SSID2);
        String currentBssid = TEST_BSSID2;

        // Returning the same matching provider for both ScanDetail.
        List<Pair<ScanDetail, WifiConfiguration>> connectableNetworks = new ArrayList<>();
        when(mPasspointManager.matchProvider(any(ScanResult.class)))
                .thenReturn(homeProvider).thenReturn(homeProvider);
        WifiConfiguration config = mEvaluator.evaluateNetworks(scanDetails, currentNetwork,
                currentBssid, true, false, connectableNetworks);
        assertEquals(1, connectableNetworks.size());

        // Verify no new network is added to WifiConfigManager.
        verify(mWifiConfigManager, never()).addOrUpdateNetwork(
                any(WifiConfiguration.class), anyInt());

        // Verify current active network is returned.
        assertEquals(ScanResultUtil.createQuotedSSID(TEST_SSID2), config.SSID);
        assertEquals(TEST_NETWORK_ID, config.networkId);
    }

    /**
     * Verify that null will be returned when matching a SIM credential provider without SIM
     * card installed.
     *
     * @throws Exception
     */
    @Test
    public void evaluateScanMatchingSIMProviderWithoutSIMCard() throws Exception {
        // Setup ScanDetail and match providers.
        List<ScanDetail> scanDetails = Arrays.asList(new ScanDetail[] {
                generateScanDetail(TEST_SSID1, TEST_BSSID1)});
        PasspointProvider testProvider = mock(PasspointProvider.class);
        Pair<PasspointProvider, PasspointMatch> homeProvider = Pair.create(
                testProvider, PasspointMatch.HomeProvider);

        List<Pair<ScanDetail, WifiConfiguration>> connectableNetworks = new ArrayList<>();
        when(mPasspointManager.matchProvider(any(ScanResult.class))).thenReturn(homeProvider);
        when(testProvider.isSimCredential()).thenReturn(true);
        when(mWifiConfigManager.isSimPresent()).thenReturn(false);
        assertEquals(null, mEvaluator.evaluateNetworks(
                scanDetails, null, null, false, false, connectableNetworks));
        assertTrue(connectableNetworks.isEmpty());
        verify(testProvider, never()).getWifiConfig();
    }

    /**
     * Verify that when the current active network is matched, the scan info associated with
     * the network is updated.
     *
     * @throws Exception
     */
    @Test
    public void evaluateScansMatchingActiveNetworkWithDifferentBSS() throws Exception {
        List<ScanDetail> scanDetails = Arrays.asList(new ScanDetail[] {
                generateScanDetail(TEST_SSID1, TEST_BSSID2)});
        // Setup matching provider.
        Pair<PasspointProvider, PasspointMatch> homeProvider = Pair.create(
                TEST_PROVIDER1, PasspointMatch.HomeProvider);

        // Setup currently connected network.
        WifiConfiguration currentNetwork = new WifiConfiguration();
        currentNetwork.networkId = TEST_NETWORK_ID;
        currentNetwork.SSID = ScanResultUtil.createQuotedSSID(TEST_SSID1);
        String currentBssid = TEST_BSSID1;

        // Match the current connected network to a home provider.
        List<Pair<ScanDetail, WifiConfiguration>> connectableNetworks = new ArrayList<>();
        when(mPasspointManager.matchProvider(any(ScanResult.class))).thenReturn(homeProvider);
        WifiConfiguration config = mEvaluator.evaluateNetworks(scanDetails, currentNetwork,
                currentBssid, true, false, connectableNetworks);
        assertEquals(1, connectableNetworks.size());

        // Verify network candidate information is updated.
        ArgumentCaptor<ScanResult> updatedCandidateScanResult =
                ArgumentCaptor.forClass(ScanResult.class);
        verify(mWifiConfigManager).setNetworkCandidateScanResult(eq(TEST_NETWORK_ID),
                updatedCandidateScanResult.capture(), anyInt());
        assertEquals(TEST_BSSID2, updatedCandidateScanResult.getValue().BSSID);
        ArgumentCaptor<ScanDetail> updatedCandidateScanDetail =
                ArgumentCaptor.forClass(ScanDetail.class);
        verify(mWifiConfigManager).updateScanDetailForNetwork(eq(TEST_NETWORK_ID),
                updatedCandidateScanDetail.capture());
        assertEquals(TEST_BSSID2, updatedCandidateScanDetail.getValue().getBSSIDString());
    }
}
