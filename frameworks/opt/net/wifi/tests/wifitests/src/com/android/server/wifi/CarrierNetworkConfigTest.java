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

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.net.wifi.EAPConstants;
import android.net.wifi.WifiEnterpriseConfig;
import android.os.PersistableBundle;
import android.telephony.CarrierConfigManager;
import android.telephony.SubscriptionInfo;
import android.telephony.SubscriptionManager;
import android.test.suitebuilder.annotation.SmallTest;
import android.util.Base64;

import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.Arrays;

/**
 * Unit tests for {@link com.android.server.wifi.CarrierNetworkConfig}.
 */
@SmallTest
public class CarrierNetworkConfigTest {
    private static final String TEST_SSID = "Test SSID";
    private static final int TEST_STANDARD_EAP_TYPE = EAPConstants.EAP_SIM;
    private static final int TEST_INTERNAL_EAP_TYPE = WifiEnterpriseConfig.Eap.SIM;
    private static final int TEST_SUBSCRIPTION_ID = 1;
    private static final String TEST_CARRIER_NAME = "Test Carrier";
    private static final SubscriptionInfo TEST_SUBSCRIPTION_INFO =
            new SubscriptionInfo(TEST_SUBSCRIPTION_ID, null, 0, TEST_CARRIER_NAME, null, 0, 0,
                    null, 0, null, 0, 0, null);

    @Mock Context mContext;
    @Mock CarrierConfigManager mCarrierConfigManager;
    @Mock SubscriptionManager mSubscriptionManager;
    BroadcastReceiver mBroadcastReceiver;
    CarrierNetworkConfig mCarrierNetworkConfig;

    /**
     * Generate and return a carrier config for testing
     *
     * @param ssid The SSID of the carrier network
     * @param eapType The EAP type of the carrier network
     * @return {@link PersistableBundle} containing carrier config
     */
    private PersistableBundle generateTestConfig(String ssid, int eapType) {
        PersistableBundle bundle = new PersistableBundle();
        String networkConfig =
                new String(Base64.encode(ssid.getBytes(), Base64.DEFAULT)) + "," + eapType;
        bundle.putStringArray(CarrierConfigManager.KEY_CARRIER_WIFI_STRING_ARRAY,
                new String[] {networkConfig});
        return bundle;
    }

    /**
     * Method to initialize mocks for tests.
     */
    @Before
    public void setUp() throws Exception {
        MockitoAnnotations.initMocks(this);
        when(mContext.getSystemService(Context.CARRIER_CONFIG_SERVICE))
                .thenReturn(mCarrierConfigManager);
        when(mContext.getSystemService(Context.TELEPHONY_SUBSCRIPTION_SERVICE))
                .thenReturn(mSubscriptionManager);
        when(mCarrierConfigManager.getConfigForSubId(TEST_SUBSCRIPTION_ID))
                .thenReturn(generateTestConfig(TEST_SSID, TEST_STANDARD_EAP_TYPE));
        when(mSubscriptionManager.getActiveSubscriptionInfoList())
                .thenReturn(Arrays.asList(new SubscriptionInfo[] {TEST_SUBSCRIPTION_INFO}));
        mCarrierNetworkConfig = new CarrierNetworkConfig(mContext);
        ArgumentCaptor<BroadcastReceiver> receiver =
                ArgumentCaptor.forClass(BroadcastReceiver.class);
        verify(mContext).registerReceiver(receiver.capture(), any(IntentFilter.class));
        mBroadcastReceiver = receiver.getValue();
        reset(mCarrierConfigManager);
    }

    /**
     * Verify that {@link CarrierNetworkConfig#isCarrierNetwork} will return true and
     * {@link CarrierNetworkConfig#getNetworkEapType} will return the corresponding EAP type
     * when the given SSID is associated with a carrier network.
     *
     * @throws Exception
     */
    @Test
    public void getExistingCarrierNetworkInfo() throws Exception {
        assertTrue(mCarrierNetworkConfig.isCarrierNetwork(TEST_SSID));
        assertEquals(TEST_INTERNAL_EAP_TYPE, mCarrierNetworkConfig.getNetworkEapType(TEST_SSID));
        assertEquals(TEST_CARRIER_NAME, mCarrierNetworkConfig.getCarrierName(TEST_SSID));
    }

    /**
     * Verify that {@link CarrierNetworkConfig#isCarrierNetwork} will return false and
     * {@link CarrierNetworkConfig#getNetworkEapType} will return -1 when the given SSID is not
     * associated with any carrier network.
     *
     * @throws Exception
     */
    @Test
    public void getNonCarrierNetworkInfo() throws Exception {
        String dummySsid = "Dummy SSID";
        assertFalse(mCarrierNetworkConfig.isCarrierNetwork(dummySsid));
        assertEquals(-1, mCarrierNetworkConfig.getNetworkEapType(dummySsid));
    }

    /**
     * Verify that the carrier network config is updated when
     * {@link CarrierConfigManager#ACTION_CARRIER_CONFIG_CHANGED} intent is received.
     *
     * @throws Exception
     */
    @Test
    public void receivedCarrierConfigChangedIntent() throws Exception {
        String updatedSsid = "Updated SSID";
        int updatedStandardEapType = EAPConstants.EAP_AKA;
        int updatedInternalEapType = WifiEnterpriseConfig.Eap.AKA;
        String updatedCarrierName = "Updated Carrier";
        SubscriptionInfo updatedSubscriptionInfo = new SubscriptionInfo(TEST_SUBSCRIPTION_ID,
                null, 0, updatedCarrierName, null, 0, 0, null, 0, null, 0, 0, null);
        when(mSubscriptionManager.getActiveSubscriptionInfoList())
                .thenReturn(Arrays.asList(new SubscriptionInfo[] {updatedSubscriptionInfo}));
        when(mCarrierConfigManager.getConfigForSubId(TEST_SUBSCRIPTION_ID))
                .thenReturn(generateTestConfig(updatedSsid, updatedStandardEapType));
        mBroadcastReceiver.onReceive(mContext,
                new Intent(CarrierConfigManager.ACTION_CARRIER_CONFIG_CHANGED));

        // Verify that original SSID no longer associated with a carrier network.
        assertFalse(mCarrierNetworkConfig.isCarrierNetwork(TEST_SSID));
        assertEquals(-1, mCarrierNetworkConfig.getNetworkEapType(TEST_SSID));
        assertEquals(null, mCarrierNetworkConfig.getCarrierName(TEST_SSID));

        // Verify that updated SSID is associated with a carrier network.
        assertTrue(mCarrierNetworkConfig.isCarrierNetwork(updatedSsid));
        assertEquals(updatedInternalEapType, mCarrierNetworkConfig.getNetworkEapType(updatedSsid));
        assertEquals(updatedCarrierName, mCarrierNetworkConfig.getCarrierName(updatedSsid));
    }

    /**
     * Verify that the carrier network config is not updated when non
     * {@link CarrierConfigManager#ACTION_CARRIER_CONFIG_CHANGED} intent is received.
     *
     * @throws Exception
     */
    @Test
    public void receivedNonCarrierConfigChangedIntent() throws Exception {
        mBroadcastReceiver.onReceive(mContext, new Intent("dummyIntent"));
        verify(mCarrierConfigManager, never()).getConfig();
    }
}
