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

import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.anyInt;
import static org.mockito.Mockito.atLeast;
import static org.mockito.Mockito.atMost;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import android.content.Context;
import android.content.res.Resources;
import android.net.NetworkAgent;
import android.net.wifi.WifiConfiguration;
import android.net.wifi.WifiInfo;

import com.android.internal.R;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.io.PrintWriter;
import java.util.Arrays;

/**
 * Unit tests for {@link com.android.server.wifi.WifiScoreReport}.
 */
public class WifiScoreReportTest {

    private static final int CELLULAR_THRESHOLD_SCORE = 50;

    class FakeClock extends Clock {
        long mWallClockMillis = 1500000000000L;
        int mStepMillis = 1001;

        @Override
        public long getWallClockMillis() {
            mWallClockMillis += mStepMillis;
            return mWallClockMillis;
        }
    }

    FakeClock mClock;
    WifiConfiguration mWifiConfiguration;
    WifiScoreReport mWifiScoreReport;
    ScanDetailCache mScanDetailCache;
    WifiInfo mWifiInfo;
    int mAggr; // Aggressive handover
    @Mock Context mContext;
    @Mock NetworkAgent mNetworkAgent;
    @Mock Resources mResources;
    @Mock WifiConfigManager mWifiConfigManager;
    @Mock WifiMetrics mWifiMetrics;
    @Mock PrintWriter mPrintWriter;

    /**
     * Sets up resource values for testing
     *
     * See frameworks/base/core/res/res/values/config.xml
     */
    private void setUpResources(Resources resources) {
        when(resources.getInteger(
                R.integer.config_wifi_framework_wifi_score_bad_rssi_threshold_5GHz))
            .thenReturn(-82);
        when(resources.getInteger(
                R.integer.config_wifi_framework_wifi_score_low_rssi_threshold_5GHz))
            .thenReturn(-70);
        when(resources.getInteger(
                R.integer.config_wifi_framework_wifi_score_good_rssi_threshold_5GHz))
            .thenReturn(-57);
        when(resources.getInteger(
                R.integer.config_wifi_framework_wifi_score_bad_rssi_threshold_24GHz))
            .thenReturn(-85);
        when(resources.getInteger(
                R.integer.config_wifi_framework_wifi_score_low_rssi_threshold_24GHz))
            .thenReturn(-73);
        when(resources.getInteger(
                R.integer.config_wifi_framework_wifi_score_good_rssi_threshold_24GHz))
            .thenReturn(-60);
        when(resources.getInteger(
                R.integer.config_wifi_framework_wifi_score_bad_link_speed_24))
            .thenReturn(6); // Mbps
        when(resources.getInteger(
                R.integer.config_wifi_framework_wifi_score_bad_link_speed_5))
            .thenReturn(12);
        when(resources.getInteger(
                R.integer.config_wifi_framework_wifi_score_good_link_speed_24))
            .thenReturn(24);
        when(resources.getInteger(
                R.integer.config_wifi_framework_wifi_score_good_link_speed_5))
            .thenReturn(36);
    }

    /**
     * Sets up for unit test
     */
    @Before
    public void setUp() throws Exception {
        MockitoAnnotations.initMocks(this);
        setUpResources(mResources);
        WifiConfiguration config = new WifiConfiguration();
        config.SSID = "nooooooooooo";
        config.allowedKeyManagement.set(WifiConfiguration.KeyMgmt.NONE);
        config.hiddenSSID = false;
        mWifiInfo = new WifiInfo();
        mWifiInfo.setFrequency(2412);
        mAggr = 0;
        when(mWifiConfigManager.getSavedNetworks()).thenReturn(Arrays.asList(config));
        when(mWifiConfigManager.getConfiguredNetwork(anyInt())).thenReturn(config);
        mWifiConfiguration = config;
        int maxSize = 10;
        int trimSize = 5;
        mScanDetailCache = new ScanDetailCache(config, maxSize, trimSize);
        // TODO: populate the cache, but probably in the test cases, not here.
        when(mWifiConfigManager.getScanDetailCacheForNetwork(anyInt()))
                .thenReturn(mScanDetailCache);
        when(mContext.getResources()).thenReturn(mResources);
        mClock = new FakeClock();
        mWifiScoreReport = new WifiScoreReport(mContext, mWifiConfigManager, mClock);
    }

    /**
     * Cleans up after test
     */
    @After
    public void tearDown() throws Exception {
        mResources = null;
        mWifiScoreReport = null;
        mWifiConfigManager = null;
        mWifiMetrics = null;
    }

    /**
     * Test for score reporting
     *
     * The score should be sent to both the NetworkAgent and the
     * WifiMetrics
     */
    @Test
    public void calculateAndReportScoreSucceeds() throws Exception {
        mWifiInfo.setRssi(-77);
        mWifiScoreReport.calculateAndReportScore(mWifiInfo,
                mNetworkAgent, mAggr, mWifiMetrics);
        verify(mNetworkAgent).sendNetworkScore(anyInt());
        verify(mWifiMetrics).incrementWifiScoreCount(anyInt());
    }

    /**
     * Test for operation with null NetworkAgent
     *
     * Expect to not die, and to calculate the score and report to metrics.
     */
    @Test
    public void networkAgentMayBeNull() throws Exception {
        mWifiInfo.setRssi(-33);
        mWifiScoreReport.enableVerboseLogging(true);
        mWifiScoreReport.calculateAndReportScore(mWifiInfo, null, mAggr, mWifiMetrics);
        verify(mWifiMetrics).incrementWifiScoreCount(anyInt());
    }

    /**
     * Exercise the rates with low RSSI
     *
     * The setup has a low (not bad) RSSI, and data movement (txSuccessRate) above
     * the threshold.
     *
     * Expect a score above threshold.
     */
    @Test
    public void allowLowRssiIfDataIsMoving() throws Exception {
        mWifiInfo.setRssi(-80);
        mWifiInfo.setLinkSpeed(6); // Mbps
        mWifiInfo.txSuccessRate = 5.1; // proportional to pps
        mWifiInfo.rxSuccessRate = 5.1;
        for (int i = 0; i < 10; i++) {
            mWifiScoreReport.calculateAndReportScore(mWifiInfo, mNetworkAgent, mAggr, mWifiMetrics);
        }
        int score = mWifiInfo.score;
        assertTrue(score > CELLULAR_THRESHOLD_SCORE);
    }

    /**
     * Bad RSSI without data moving should allow handoff
     *
     * The setup has a bad RSSI, and the txSuccessRate is below threshold; several
     * scoring iterations are performed.
     *
     * Expect the score to drop below the handoff threshold.
     */
    @Test
    public void giveUpOnBadRssiWhenDataIsNotMoving() throws Exception {
        mWifiInfo.setRssi(-100);
        mWifiInfo.setLinkSpeed(6); // Mbps
        mWifiInfo.setFrequency(5220);
        mWifiScoreReport.enableVerboseLogging(true);
        mWifiInfo.txSuccessRate = 0.1;
        mWifiInfo.rxSuccessRate = 0.1;
        for (int i = 0; i < 10; i++) {
            mWifiScoreReport.calculateAndReportScore(mWifiInfo, mNetworkAgent, mAggr, mWifiMetrics);
        }
        int score = mWifiInfo.score;
        assertTrue(score < CELLULAR_THRESHOLD_SCORE);
        verify(mNetworkAgent, atLeast(1)).sendNetworkScore(score);
    }

    /**
     * Test reporting with aggressive handover
     */
    @Test
    public void calculateAndReportScoreSucceedsAggressively() throws Exception {
        mAggr = 1;
        mWifiInfo.setRssi(-77);
        mWifiScoreReport.calculateAndReportScore(mWifiInfo, mNetworkAgent, mAggr, mWifiMetrics);
        verify(mNetworkAgent).sendNetworkScore(anyInt());
        verify(mWifiMetrics).incrementWifiScoreCount(anyInt());
    }

    /**
     * Test low rssi with aggressive handover
     */
    @Test
    public void giveUpOnBadRssiAggressively() throws Exception {
        mAggr = 1;
        mWifiInfo.setRssi(-83);
        mWifiScoreReport.calculateAndReportScore(mWifiInfo, mNetworkAgent, mAggr, mWifiMetrics);
        int score = mWifiInfo.score;
        verify(mNetworkAgent, atLeast(1)).sendNetworkScore(score);
        assertTrue(score < CELLULAR_THRESHOLD_SCORE);
    }

    /**
     * Test high rssi with aggressive handover
     */
    @Test
    public void allowGoodRssiAggressively() throws Exception {
        mAggr = 1;
        mWifiInfo.setRssi(-65);
        mWifiScoreReport.calculateAndReportScore(mWifiInfo, mNetworkAgent, mAggr, mWifiMetrics);
        int score = mWifiInfo.score;
        verify(mNetworkAgent, atLeast(1)).sendNetworkScore(score);
        assertTrue(score > CELLULAR_THRESHOLD_SCORE);
    }

    /**
     * Test data logging
     */
    @Test
    public void testDataLogging() throws Exception {
        mAggr = 1;
        for (int i = 0; i < 10; i++) {
            mWifiInfo.setRssi(-65 + i);
            mWifiInfo.setLinkSpeed(300);
            mWifiInfo.setFrequency(5220);
            mWifiInfo.txSuccessRate = 0.1 + i;
            mWifiInfo.txRetriesRate = 0.2 + i;
            mWifiInfo.txBadRate = 0.01 * i;
            mWifiInfo.rxSuccessRate = 0.3 + i;
            mWifiScoreReport.calculateAndReportScore(mWifiInfo, mNetworkAgent, mAggr, mWifiMetrics);
        }
        mWifiScoreReport.dump(null, mPrintWriter, null);
        verify(mPrintWriter, atLeast(11)).println(anyString());
    }

    /**
     *  Test data logging limit
     *  <p>
     *  Check that only a bounded amount of data is collected for dumpsys report
     */
    @Test
    public void testDataLoggingLimit() throws Exception {
        for (int i = 0; i < 14500; i++) {
            mWifiInfo.setRssi(-65 + i % 20);
            mWifiInfo.setLinkSpeed(300);
            mWifiInfo.setFrequency(5220);
            mWifiInfo.txSuccessRate = 0.1 + i % 100;
            mWifiInfo.txRetriesRate = 0.2 + i % 100;
            mWifiInfo.txBadRate = 0.0001 * i;
            mWifiInfo.rxSuccessRate = 0.3 + i % 200;
            mWifiScoreReport.calculateAndReportScore(mWifiInfo, mNetworkAgent, mAggr, mWifiMetrics);
        }
        mWifiScoreReport.dump(null, mPrintWriter, null);
        verify(mPrintWriter, atMost(14401)).println(anyString());
    }
}
