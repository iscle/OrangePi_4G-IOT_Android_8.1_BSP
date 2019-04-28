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

package com.android.server.wifi;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import android.net.wifi.ScanResult;
import android.util.ArraySet;

import org.junit.Before;
import org.junit.Test;
import org.mockito.MockitoAnnotations;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Tests for {@link OpenNetworkRecommender}.
 */
public class OpenNetworkRecommenderTest {

    private static final String TEST_SSID_1 = "Test SSID 1";
    private static final String TEST_SSID_2 = "Test SSID 2";
    private static final int MIN_RSSI_LEVEL = -127;

    private OpenNetworkRecommender mOpenNetworkRecommender;
    private Set<String> mBlacklistedSsids;

    @Before
    public void setUp() throws Exception {
        MockitoAnnotations.initMocks(this);
        mOpenNetworkRecommender = new OpenNetworkRecommender();
        mBlacklistedSsids = new ArraySet<>();
    }

    private List<ScanDetail> createOpenScanResults(String... ssids) {
        List<ScanDetail> scanResults = new ArrayList<>();
        for (String ssid : ssids) {
            ScanResult scanResult = new ScanResult();
            scanResult.SSID = ssid;
            scanResult.capabilities = "[ESS]";
            scanResults.add(new ScanDetail(scanResult, null /* networkDetail */));
        }
        return scanResults;
    }

    /** If list of open networks contain only one network, that network should be returned. */
    @Test
    public void onlyNetworkIsRecommended() {
        List<ScanDetail> scanResults = createOpenScanResults(TEST_SSID_1);
        scanResults.get(0).getScanResult().level = MIN_RSSI_LEVEL;

        ScanResult actual = mOpenNetworkRecommender.recommendNetwork(
                scanResults, mBlacklistedSsids);
        ScanResult expected = scanResults.get(0).getScanResult();
        assertEquals(expected, actual);
    }

    /** Verifies that the network with the highest rssi is recommended. */
    @Test
    public void networkWithHighestRssiIsRecommended() {
        List<ScanDetail> scanResults = createOpenScanResults(TEST_SSID_1, TEST_SSID_2);
        scanResults.get(0).getScanResult().level = MIN_RSSI_LEVEL;
        scanResults.get(1).getScanResult().level = MIN_RSSI_LEVEL + 1;

        ScanResult actual = mOpenNetworkRecommender.recommendNetwork(
                scanResults, mBlacklistedSsids);
        ScanResult expected = scanResults.get(1).getScanResult();
        assertEquals(expected, actual);
    }

    /**
     * If the best available open network is blacklisted, no networks should be recommended.
     */
    @Test
    public void blacklistBestNetworkSsid_shouldNeverRecommendNetwork() {
        List<ScanDetail> scanResults = createOpenScanResults(TEST_SSID_1, TEST_SSID_2);
        scanResults.get(0).getScanResult().level = MIN_RSSI_LEVEL + 1;
        scanResults.get(1).getScanResult().level = MIN_RSSI_LEVEL;
        mBlacklistedSsids.add(TEST_SSID_1);

        ScanResult actual = mOpenNetworkRecommender.recommendNetwork(
                scanResults, mBlacklistedSsids);
        assertNull(actual);
    }
}
