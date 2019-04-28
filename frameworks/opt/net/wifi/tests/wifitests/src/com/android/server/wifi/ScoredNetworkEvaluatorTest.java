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

package com.android.server.wifi;


import static com.android.server.wifi.WifiConfigurationTestUtil.SECURITY_NONE;
import static com.android.server.wifi.WifiConfigurationTestUtil.SECURITY_PSK;

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

import android.content.Context;
import android.database.ContentObserver;
import android.net.NetworkKey;
import android.net.NetworkScoreManager;
import android.net.Uri;
import android.net.wifi.ScanResult;
import android.net.wifi.WifiConfiguration;
import android.net.wifi.WifiNetworkScoreCache;
import android.os.Looper;
import android.os.SystemClock;
import android.provider.Settings;
import android.test.suitebuilder.annotation.SmallTest;
import android.util.LocalLog;

import com.android.server.wifi.WifiNetworkSelectorTestUtil.ScanDetailsAndWifiConfigs;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.ArrayList;
import java.util.List;

/**
 * Unit tests for {@link ScoredNetworkEvaluator}.
 */
@SmallTest
public class ScoredNetworkEvaluatorTest {
    private ContentObserver mContentObserver;
    private int mThresholdQualifiedRssi2G;
    private int mThresholdQualifiedRssi5G;

    @Mock private Context mContext;
    @Mock private Clock mClock;
    @Mock private FrameworkFacade mFrameworkFacade;
    @Mock private NetworkScoreManager mNetworkScoreManager;
    @Mock private WifiConfigManager mWifiConfigManager;

    @Captor private ArgumentCaptor<NetworkKey[]> mNetworkKeyArrayCaptor;

    private WifiNetworkScoreCache mScoreCache;
    private ScoredNetworkEvaluator mScoredNetworkEvaluator;

    @Before
    public void setUp() throws Exception {
        mThresholdQualifiedRssi2G = -73;
        mThresholdQualifiedRssi5G = -70;

        MockitoAnnotations.initMocks(this);

        when(mFrameworkFacade.getIntegerSetting(mContext,
                Settings.Global.NETWORK_RECOMMENDATIONS_ENABLED, 0))
                .thenReturn(1);

        ArgumentCaptor<ContentObserver> observerCaptor =
                ArgumentCaptor.forClass(ContentObserver.class);
        mScoreCache = new WifiNetworkScoreCache(mContext);
        mScoredNetworkEvaluator = new ScoredNetworkEvaluator(mContext,
                Looper.getMainLooper(), mFrameworkFacade, mNetworkScoreManager,
                mWifiConfigManager, new LocalLog(0), mScoreCache);
        verify(mFrameworkFacade).registerContentObserver(eq(mContext), any(Uri.class), eq(false),
                observerCaptor.capture());
        mContentObserver = observerCaptor.getValue();

        reset(mNetworkScoreManager);

        when(mClock.getElapsedSinceBootMillis()).thenReturn(SystemClock.elapsedRealtime());
    }

    @After
    public void tearDown() {
        validateMockitoUsage();
    }

    @Test
    public void testUpdate_recommendationsDisabled() {
        String[] ssids = {"\"test1\""};
        String[] bssids = {"6c:f3:7f:ae:8c:f3"};
        int[] freqs = {2470};
        String[] caps = {"[WPA2-EAP-CCMP][ESS]"};
        int[] levels = {mThresholdQualifiedRssi2G + 8};
        int[] securities = {SECURITY_PSK};

        ScanDetailsAndWifiConfigs scanDetailsAndConfigs = WifiNetworkSelectorTestUtil
                .setupScanDetailsAndConfigStore(
                ssids, bssids, freqs, caps, levels, securities, mWifiConfigManager, mClock);

        when(mFrameworkFacade.getIntegerSetting(mContext,
                Settings.Global.NETWORK_RECOMMENDATIONS_ENABLED, 0))
                .thenReturn(0);

        mContentObserver.onChange(false /* unused */);

        mScoredNetworkEvaluator.update(scanDetailsAndConfigs.getScanDetails());

        verifyZeroInteractions(mNetworkScoreManager);
    }

    @Test
    public void testUpdate_emptyScanList() {
        String[] ssids = {"\"test1\""};
        String[] bssids = {"6c:f3:7f:ae:8c:f3"};
        int[] freqs = {2470};
        String[] caps = {"[WPA2-EAP-CCMP][ESS]"};
        int[] levels = {mThresholdQualifiedRssi2G + 8};
        int[] securities = {SECURITY_PSK};

        ScanDetailsAndWifiConfigs scanDetailsAndConfigs = WifiNetworkSelectorTestUtil
                .setupScanDetailsAndConfigStore(
                        ssids, bssids, freqs, caps, levels, securities, mWifiConfigManager, mClock);

        mScoredNetworkEvaluator.update(new ArrayList<ScanDetail>());

        verifyZeroInteractions(mNetworkScoreManager);
    }

    @Test
    public void testUpdate_allNetworksUnscored() {
        String[] ssids = {"\"test1\"", "\"test2\""};
        String[] bssids = {"6c:f3:7f:ae:8c:f3", "6c:f3:7f:ae:8c:f4"};
        int[] freqs = {2470, 2437};
        String[] caps = {"[WPA2-EAP-CCMP][ESS]", "[ESS]"};
        int[] securities = {SECURITY_PSK, SECURITY_NONE};
        int[] levels = {mThresholdQualifiedRssi2G + 8, mThresholdQualifiedRssi2G + 10};

        ScanDetailsAndWifiConfigs scanDetailsAndConfigs = WifiNetworkSelectorTestUtil
                .setupScanDetailsAndConfigStore(
                        ssids, bssids, freqs, caps, levels, securities, mWifiConfigManager, mClock);

        mScoredNetworkEvaluator.update(scanDetailsAndConfigs.getScanDetails());

        verify(mNetworkScoreManager).requestScores(mNetworkKeyArrayCaptor.capture());
        assertEquals(2, mNetworkKeyArrayCaptor.getValue().length);
        NetworkKey expectedNetworkKey = NetworkKey.createFromScanResult(
                scanDetailsAndConfigs.getScanDetails().get(0).getScanResult());
        assertEquals(expectedNetworkKey, mNetworkKeyArrayCaptor.getValue()[0]);
        expectedNetworkKey = NetworkKey.createFromScanResult(
                scanDetailsAndConfigs.getScanDetails().get(1).getScanResult());
        assertEquals(expectedNetworkKey, mNetworkKeyArrayCaptor.getValue()[1]);
    }

    @Test
    public void testUpdate_oneScored_oneUnscored() {
        String[] ssids = {"\"test1\"", "\"test2\""};
        String[] bssids = {"6c:f3:7f:ae:8c:f3", "6c:f3:7f:ae:8c:f4"};
        int[] freqs = {2470, 2437};
        String[] caps = {"[WPA2-EAP-CCMP][ESS]", "[ESS]"};
        int[] securities = {SECURITY_PSK, SECURITY_NONE};
        int[] levels = {mThresholdQualifiedRssi2G + 8, mThresholdQualifiedRssi2G + 10};

        ScanDetailsAndWifiConfigs scanDetailsAndConfigs = WifiNetworkSelectorTestUtil
                .setupScanDetailsAndConfigStore(
                        ssids, bssids, freqs, caps, levels, securities, mWifiConfigManager, mClock);

        List<ScanDetail> scoredScanDetails = scanDetailsAndConfigs.getScanDetails().subList(0, 1);
        Integer[] scores = {120};
        boolean[] meteredHints = {true};
        WifiNetworkSelectorTestUtil.configureScoreCache(
                mScoreCache, scoredScanDetails, scores, meteredHints);

        mScoredNetworkEvaluator.update(scanDetailsAndConfigs.getScanDetails());

        verify(mNetworkScoreManager).requestScores(mNetworkKeyArrayCaptor.capture());

        NetworkKey[] requestedScores = mNetworkKeyArrayCaptor.getValue();
        assertEquals(1, requestedScores.length);
        NetworkKey expectedNetworkKey = NetworkKey.createFromScanResult(
                scanDetailsAndConfigs.getScanDetails().get(1).getScanResult());
        assertEquals(expectedNetworkKey, requestedScores[0]);
    }

    @Test
    public void testEvaluateNetworks_recommendationsDisabled() {
        when(mFrameworkFacade.getIntegerSetting(mContext,
                Settings.Global.NETWORK_RECOMMENDATIONS_ENABLED, 0))
                .thenReturn(0);

        mContentObserver.onChange(false /* unused */);

        mScoredNetworkEvaluator.evaluateNetworks(null, null, null, false, false, null);

        verifyZeroInteractions(mWifiConfigManager, mNetworkScoreManager);
    }

    /**
     * When no saved networks available, choose the available ephemeral networks
     * if untrusted networks are allowed.
     */
    @Test
    public void testEvaluateNetworks_chooseEphemeralNetworkBecauseOfNoSavedNetwork() {
        String[] ssids = {"\"test1\"", "\"test2\""};
        String[] bssids = {"6c:f3:7f:ae:8c:f3", "6c:f3:7f:ae:8c:f4"};
        int[] freqs = {2470, 2437};
        String[] caps = {"[WPA2-EAP-CCMP][ESS]", "[ESS]"};
        int[] levels = {mThresholdQualifiedRssi2G + 8, mThresholdQualifiedRssi2G + 10};
        Integer[] scores = {null, 120};
        boolean[] meteredHints = {false, true};

        List<ScanDetail> scanDetails = WifiNetworkSelectorTestUtil.buildScanDetails(
                ssids, bssids, freqs, caps, levels, mClock);
        WifiNetworkSelectorTestUtil.configureScoreCache(mScoreCache,
                scanDetails, scores, meteredHints);

        // No saved networks.
        when(mWifiConfigManager.getConfiguredNetworkForScanDetailAndCache(any(ScanDetail.class)))
                .thenReturn(null);

        ScanResult scanResult = scanDetails.get(1).getScanResult();
        WifiConfiguration ephemeralNetworkConfig = WifiNetworkSelectorTestUtil
                .setupEphemeralNetwork(mWifiConfigManager, 1, scanDetails.get(1), meteredHints[1]);

        // Untrusted networks allowed.
        WifiConfiguration candidate = mScoredNetworkEvaluator.evaluateNetworks(scanDetails,
                null, null, false, true, null);

        WifiConfigurationTestUtil.assertConfigurationEqual(ephemeralNetworkConfig, candidate);
        WifiNetworkSelectorTestUtil.verifySelectedScanResult(mWifiConfigManager,
                scanResult, candidate);
        assertEquals(meteredHints[1], candidate.meteredHint);
    }

    /**
     * When no saved networks available, choose the highest scored ephemeral networks
     * if untrusted networks are allowed.
     */
    @Test
    public void testEvaluateNetworks_chooseHigherScoredEphemeralNetwork() {
        String[] ssids = {"\"test1\"", "\"test2\""};
        String[] bssids = {"6c:f3:7f:ae:8c:f3", "6c:f3:7f:ae:8c:f4"};
        int[] freqs = {2470, 2437};
        String[] caps = {"[ESS]", "[ESS]"};
        int[] levels = {mThresholdQualifiedRssi2G + 8, mThresholdQualifiedRssi2G + 8};
        Integer[] scores = {100, 120};
        boolean[] meteredHints = {true, true};
        ScanResult[] scanResults = new ScanResult[2];
        WifiConfiguration[] ephemeralNetworkConfigs = new WifiConfiguration[2];

        List<ScanDetail> scanDetails = WifiNetworkSelectorTestUtil.buildScanDetails(
                ssids, bssids, freqs, caps, levels, mClock);
        WifiNetworkSelectorTestUtil.configureScoreCache(mScoreCache,
                scanDetails, scores, meteredHints);

        // No saved networks.
        when(mWifiConfigManager.getConfiguredNetworkForScanDetailAndCache(any(ScanDetail.class)))
                .thenReturn(null);

        for (int i = 0; i < 2; i++) {
            scanResults[i] = scanDetails.get(i).getScanResult();
            ephemeralNetworkConfigs[i] = WifiNetworkSelectorTestUtil.setupEphemeralNetwork(
                    mWifiConfigManager, i, scanDetails.get(i), meteredHints[i]);
        }

        WifiConfiguration candidate = mScoredNetworkEvaluator.evaluateNetworks(scanDetails,
                null, null, false, true, null);

        WifiConfigurationTestUtil.assertConfigurationEqual(ephemeralNetworkConfigs[1], candidate);
        WifiNetworkSelectorTestUtil.verifySelectedScanResult(mWifiConfigManager,
                scanResults[1], candidate);
        assertEquals(meteredHints[1], candidate.meteredHint);
    }

    /**
     * Don't choose available ephemeral networks if no saved networks and untrusted networks
     * are not allowed.
     */
    @Test
    public void testEvaluateNetworks_noEphemeralNetworkWhenUntrustedNetworksNotAllowed() {
        String[] ssids = {"\"test1\"", "\"test2\""};
        String[] bssids = {"6c:f3:7f:ae:8c:f3", "6c:f3:7f:ae:8c:f4"};
        int[] freqs = {2470, 2437};
        String[] caps = {"[WPA2-EAP-CCMP][ESS]", "[ESS]"};
        int[] levels = {mThresholdQualifiedRssi2G + 8, mThresholdQualifiedRssi2G + 10};
        Integer[] scores = {null, 120};
        boolean[] meteredHints = {false, true};

        List<ScanDetail> scanDetails = WifiNetworkSelectorTestUtil.buildScanDetails(
                ssids, bssids, freqs, caps, levels, mClock);
        WifiNetworkSelectorTestUtil.configureScoreCache(mScoreCache,
                scanDetails, scores, meteredHints);

        // No saved networks.
        when(mWifiConfigManager.getConfiguredNetworkForScanDetailAndCache(any(ScanDetail.class)))
                .thenReturn(null);

        WifiNetworkSelectorTestUtil.setupEphemeralNetwork(
                mWifiConfigManager, 1, scanDetails.get(1), meteredHints[1]);

        // Untrusted networks not allowed.
        WifiConfiguration candidate = mScoredNetworkEvaluator.evaluateNetworks(scanDetails,
                null, null, false, false, null);

        assertEquals("Expect null configuration", null, candidate);
    }

    /**
     * Choose externally scored saved network.
     */
    @Test
    public void testEvaluateNetworks_chooseSavedNetworkWithExternalScore() {
        String[] ssids = {"\"test1\""};
        String[] bssids = {"6c:f3:7f:ae:8c:f3"};
        int[] freqs = {5200};
        String[] caps = {"[WPA2-EAP-CCMP][ESS]"};
        int[] securities = {SECURITY_PSK};
        int[] levels = {mThresholdQualifiedRssi5G + 8};
        Integer[] scores = {120};
        boolean[] meteredHints = {false};

        WifiNetworkSelectorTestUtil.ScanDetailsAndWifiConfigs scanDetailsAndConfigs =
                WifiNetworkSelectorTestUtil.setupScanDetailsAndConfigStore(ssids, bssids,
                        freqs, caps, levels, securities, mWifiConfigManager, mClock);
        List<ScanDetail> scanDetails = scanDetailsAndConfigs.getScanDetails();
        WifiConfiguration[] savedConfigs = scanDetailsAndConfigs.getWifiConfigs();
        savedConfigs[0].useExternalScores = true;

        WifiNetworkSelectorTestUtil.configureScoreCache(mScoreCache,
                scanDetails, scores, meteredHints);

        WifiConfiguration candidate = mScoredNetworkEvaluator.evaluateNetworks(scanDetails,
                null, null, false, true, null);

        WifiConfigurationTestUtil.assertConfigurationEqual(savedConfigs[0], candidate);
        WifiNetworkSelectorTestUtil.verifySelectedScanResult(mWifiConfigManager,
                scanDetails.get(0).getScanResult(), candidate);
    }

    /**
     * Choose externally scored saved network with higher score.
     */
    @Test
    public void testEvaluateNetworks_chooseSavedNetworkWithHigherExternalScore() {
        String[] ssids = {"\"test1\"", "\"test2\""};
        String[] bssids = {"6c:f3:7f:ae:8c:f3", "6c:f3:7f:ae:8c:f4"};
        int[] freqs = {2470, 2437};
        String[] caps = {"[WPA2-EAP-CCMP][ESS]", "[WPA2-EAP-CCMP][ESS]"};
        int[] securities = {SECURITY_PSK, SECURITY_PSK};
        int[] levels = {mThresholdQualifiedRssi2G + 8, mThresholdQualifiedRssi2G + 8};
        Integer[] scores = {100, 120};
        boolean[] meteredHints = {false, false};

        WifiNetworkSelectorTestUtil.ScanDetailsAndWifiConfigs scanDetailsAndConfigs =
                WifiNetworkSelectorTestUtil.setupScanDetailsAndConfigStore(ssids, bssids,
                        freqs, caps, levels, securities, mWifiConfigManager, mClock);
        List<ScanDetail> scanDetails = scanDetailsAndConfigs.getScanDetails();
        WifiConfiguration[] savedConfigs = scanDetailsAndConfigs.getWifiConfigs();
        savedConfigs[0].useExternalScores = savedConfigs[1].useExternalScores = true;

        WifiNetworkSelectorTestUtil.configureScoreCache(mScoreCache,
                scanDetails, scores, meteredHints);

        WifiConfiguration candidate = mScoredNetworkEvaluator.evaluateNetworks(scanDetails,
                null, null, false, true, null);

        WifiConfigurationTestUtil.assertConfigurationEqual(savedConfigs[1], candidate);
        WifiNetworkSelectorTestUtil.verifySelectedScanResult(mWifiConfigManager,
                scanDetails.get(1).getScanResult(), candidate);
    }

    /**
     * Prefer externally scored saved network over untrusted network when they have
     * the same score.
     */
    @Test
    public void testEvaluateNetworks_chooseExternallyScoredOverUntrustedNetworksWithSameScore() {
        String[] ssids = {"\"test1\"", "\"test2\""};
        String[] bssids = {"6c:f3:7f:ae:8c:f3", "6c:f3:7f:ae:8c:f4"};
        int[] freqs = {2470, 2437};
        String[] caps = {"[WPA2-EAP-CCMP][ESS]", "[ESS]"};
        int[] securities = {SECURITY_PSK, SECURITY_NONE};
        int[] levels = {mThresholdQualifiedRssi2G + 8, mThresholdQualifiedRssi2G + 8};
        Integer[] scores = {120, 120};
        boolean[] meteredHints = {false, true};

        WifiNetworkSelectorTestUtil.ScanDetailsAndWifiConfigs scanDetailsAndConfigs =
                WifiNetworkSelectorTestUtil.setupScanDetailsAndConfigStore(ssids, bssids,
                        freqs, caps, levels, securities, mWifiConfigManager, mClock);
        List<ScanDetail> scanDetails = scanDetailsAndConfigs.getScanDetails();
        WifiConfiguration[] savedConfigs = scanDetailsAndConfigs.getWifiConfigs();
        savedConfigs[0].useExternalScores = true;

        WifiNetworkSelectorTestUtil.configureScoreCache(mScoreCache,
                scanDetails, scores, meteredHints);

        WifiConfiguration candidate = mScoredNetworkEvaluator.evaluateNetworks(scanDetails,
                null, null, false, true, null);

        WifiConfigurationTestUtil.assertConfigurationEqual(savedConfigs[0], candidate);
        WifiNetworkSelectorTestUtil.verifySelectedScanResult(mWifiConfigManager,
                scanDetails.get(0).getScanResult(), candidate);
    }

    /**
     * Choose untrusted network when it has higher score than the externally scored
     * saved network.
     */
    @Test
    public void testEvaluateNetworks_chooseUntrustedWithHigherScoreThanExternallyScoredNetwork() {
        // Saved network.
        String[] savedSsids = {"\"test1\""};
        String[] savedBssids = {"6c:f3:7f:ae:8c:f3"};
        int[] savedFreqs = {2470};
        String[] savedCaps = {"[WPA2-EAP-CCMP][ESS]"};
        int[] savedSecurities = {SECURITY_PSK};
        int[] savedLevels = {mThresholdQualifiedRssi2G + 8};
        // Ephemeral network.
        String[] ephemeralSsids = {"\"test2\""};
        String[] ephemeralBssids = {"6c:f3:7f:ae:8c:f4"};
        int[] ephemeralFreqs = {2437};
        String[] ephemeralCaps = {"[ESS]"};
        int[] ephemeralLevels = {mThresholdQualifiedRssi2G + 8};
        // Ephemeral network has higher score than the saved network.
        Integer[] scores = {100, 120};
        boolean[] meteredHints = {false, true};

        // Set up the saved network.
        WifiNetworkSelectorTestUtil.ScanDetailsAndWifiConfigs scanDetailsAndConfigs =
                WifiNetworkSelectorTestUtil.setupScanDetailsAndConfigStore(savedSsids,
                        savedBssids, savedFreqs, savedCaps, savedLevels, savedSecurities,
                        mWifiConfigManager, mClock);
        List<ScanDetail> scanDetails = scanDetailsAndConfigs.getScanDetails();
        WifiConfiguration[] savedConfigs = scanDetailsAndConfigs.getWifiConfigs();
        savedConfigs[0].useExternalScores = true;

        // Set up the ephemeral network.
        scanDetails.addAll(WifiNetworkSelectorTestUtil.buildScanDetails(
                ephemeralSsids, ephemeralBssids, ephemeralFreqs,
                ephemeralCaps, ephemeralLevels, mClock));
        ScanResult ephemeralScanResult = scanDetails.get(1).getScanResult();
        WifiConfiguration ephemeralNetworkConfig = WifiNetworkSelectorTestUtil
                .setupEphemeralNetwork(mWifiConfigManager, 1, scanDetails.get(1),
                        meteredHints[1]);

        // Set up score cache for both the saved network and the ephemeral network.
        WifiNetworkSelectorTestUtil.configureScoreCache(mScoreCache,
                scanDetails, scores, meteredHints);

        WifiConfiguration candidate = mScoredNetworkEvaluator.evaluateNetworks(scanDetails,
                null, null, false, true, null);

        WifiConfigurationTestUtil.assertConfigurationEqual(ephemeralNetworkConfig, candidate);
        WifiNetworkSelectorTestUtil.verifySelectedScanResult(mWifiConfigManager,
                ephemeralScanResult, candidate);
    }

    /**
     * Prefer externally scored saved network over untrusted network when they have
     * the same score.
     */
    @Test
    public void testEvaluateNetworks_nullScoredNetworks() {
        String[] ssids = {"\"test1\"", "\"test2\""};
        String[] bssids = {"6c:f3:7f:ae:8c:f3", "6c:f3:7f:ae:8c:f4"};
        int[] freqs = {2470, 2437};
        String[] caps = {"[WPA2-EAP-CCMP][ESS]", "[ESS]"};
        int[] securities = {SECURITY_PSK, SECURITY_NONE};
        int[] levels = {mThresholdQualifiedRssi2G + 8, mThresholdQualifiedRssi2G + 8};
        Integer[] scores = {null, null};
        boolean[] meteredHints = {false, true};

        WifiNetworkSelectorTestUtil.ScanDetailsAndWifiConfigs scanDetailsAndConfigs =
                WifiNetworkSelectorTestUtil.setupScanDetailsAndConfigStore(ssids, bssids,
                        freqs, caps, levels, securities, mWifiConfigManager, mClock);
        List<ScanDetail> scanDetails = scanDetailsAndConfigs.getScanDetails();
        WifiConfiguration[] savedConfigs = scanDetailsAndConfigs.getWifiConfigs();
        savedConfigs[0].useExternalScores = true;

        WifiNetworkSelectorTestUtil.configureScoreCache(mScoreCache,
                scanDetails, scores, meteredHints);

        WifiConfiguration candidate = mScoredNetworkEvaluator.evaluateNetworks(scanDetails,
                null, null, false, true, null);

        assertEquals("Expect null configuration", null, candidate);
    }

    /**
     * Between two ephemeral networks with the same RSSI, choose
     * the currently connected one.
     */
    @Test
    public void testEvaluateNetworks_chooseActiveEphemeralNetwork() {
        String[] ssids = {"\"test1\"", "\"test2\""};
        String[] bssids = {"6c:f3:7f:ae:8c:f3", "6c:f3:7f:ae:8c:f4"};
        int[] freqs = {2470, 2437};
        String[] caps = {"[ESS]", "[ESS]"};
        int[] levels = {mThresholdQualifiedRssi2G + 28, mThresholdQualifiedRssi2G + 28};
        boolean[] meteredHints = {true, true};
        ScanResult[] scanResults = new ScanResult[2];
        WifiConfiguration[] ephemeralNetworkConfigs = new WifiConfiguration[2];

        List<ScanDetail> scanDetails = WifiNetworkSelectorTestUtil
                .buildScanDetails(ssids, bssids, freqs, caps, levels, mClock);

        WifiNetworkSelectorTestUtil.configureScoreCache(
                mScoreCache, scanDetails, null, meteredHints);

        // No saved networks.
        when(mWifiConfigManager.getConfiguredNetworkForScanDetailAndCache(any(ScanDetail.class)))
                .thenReturn(null);

        for (int i = 0; i < 2; i++) {
            scanResults[i] = scanDetails.get(i).getScanResult();
            ephemeralNetworkConfigs[i] = WifiNetworkSelectorTestUtil.setupEphemeralNetwork(
                    mWifiConfigManager, i, scanDetails.get(i), meteredHints[i]);
        }

        WifiConfiguration candidate = mScoredNetworkEvaluator.evaluateNetworks(
                scanDetails, ephemeralNetworkConfigs[1],
                bssids[1], true, true, null);

        WifiConfigurationTestUtil.assertConfigurationEqual(ephemeralNetworkConfigs[1], candidate);
        WifiNetworkSelectorTestUtil.verifySelectedScanResult(mWifiConfigManager,
                scanResults[1], candidate);
        assertEquals(meteredHints[1], candidate.meteredHint);
    }

    /**
     *  Between two externally scored saved networks with the same RSSI, choose
     *  the currently connected one.
     */
    @Test
    public void testEvaluateNetworks_chooseActiveSavedNetwork() {
        String[] ssids = {"\"test1\"", "\"test2\""};
        String[] bssids = {"6c:f3:7f:ae:8c:f3", "6c:f3:7f:ae:8c:f4"};
        int[] freqs = {2470, 2437};
        String[] caps = {"[WPA2-EAP-CCMP][ESS]", "[WPA2-EAP-CCMP][ESS]"};
        int[] securities = {SECURITY_PSK, SECURITY_PSK};
        int[] levels = {mThresholdQualifiedRssi2G + 28, mThresholdQualifiedRssi2G + 28};
        boolean[] meteredHints = {false, false};

        WifiNetworkSelectorTestUtil.ScanDetailsAndWifiConfigs scanDetailsAndConfigs =
                WifiNetworkSelectorTestUtil.setupScanDetailsAndConfigStore(ssids, bssids,
                        freqs, caps, levels, securities, mWifiConfigManager, mClock);
        List<ScanDetail> scanDetails = scanDetailsAndConfigs.getScanDetails();
        WifiConfiguration[] savedConfigs = scanDetailsAndConfigs.getWifiConfigs();
        savedConfigs[0].useExternalScores = savedConfigs[1].useExternalScores = true;

        WifiNetworkSelectorTestUtil.configureScoreCache(mScoreCache,
                scanDetails, null, meteredHints);

        WifiConfiguration candidate = mScoredNetworkEvaluator.evaluateNetworks(scanDetails,
                savedConfigs[1], bssids[1], true, true, null);

        WifiConfigurationTestUtil.assertConfigurationEqual(savedConfigs[1], candidate);
        WifiNetworkSelectorTestUtil.verifySelectedScanResult(mWifiConfigManager,
                scanDetails.get(1).getScanResult(), candidate);
    }
}
