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

import static org.mockito.Mockito.any;
import static org.mockito.Mockito.anyBoolean;
import static org.mockito.Mockito.anyInt;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import android.net.InterfaceConfiguration;
import android.net.wifi.IApInterface;
import android.net.wifi.WifiConfiguration;
import android.net.wifi.WifiManager;
import android.os.IBinder;
import android.os.IBinder.DeathRecipient;
import android.os.INetworkManagementService;
import android.os.test.TestLooper;
import android.test.suitebuilder.annotation.SmallTest;

import com.android.server.net.BaseNetworkObserver;

import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Locale;

/** Unit tests for {@link SoftApManager}. */
@SmallTest
public class SoftApManagerTest {

    private static final String TAG = "SoftApManagerTest";

    private static final String DEFAULT_SSID = "DefaultTestSSID";
    private static final String TEST_SSID = "TestSSID";
    private static final String TEST_COUNTRY_CODE = "TestCountry";
    private static final Integer[] ALLOWED_2G_CHANNELS = {1, 2, 3, 4};
    private static final String TEST_INTERFACE_NAME = "testif0";

    private final ArrayList<Integer> mAllowed2GChannels =
            new ArrayList<>(Arrays.asList(ALLOWED_2G_CHANNELS));

    private final WifiConfiguration mDefaultApConfig = createDefaultApConfig();

    TestLooper mLooper;
    @Mock WifiNative mWifiNative;
    @Mock SoftApManager.Listener mListener;
    @Mock InterfaceConfiguration mInterfaceConfiguration;
    @Mock IBinder mApInterfaceBinder;
    @Mock IApInterface mApInterface;
    @Mock INetworkManagementService mNmService;
    @Mock WifiApConfigStore mWifiApConfigStore;
    @Mock WifiMetrics mWifiMetrics;
    final ArgumentCaptor<DeathRecipient> mDeathListenerCaptor =
            ArgumentCaptor.forClass(DeathRecipient.class);
    final ArgumentCaptor<BaseNetworkObserver> mNetworkObserverCaptor =
            ArgumentCaptor.forClass(BaseNetworkObserver.class);

    SoftApManager mSoftApManager;

    /** Sets up test. */
    @Before
    public void setUp() throws Exception {
        MockitoAnnotations.initMocks(this);
        mLooper = new TestLooper();

        when(mApInterface.asBinder()).thenReturn(mApInterfaceBinder);
        when(mApInterface.startHostapd()).thenReturn(true);
        when(mApInterface.stopHostapd()).thenReturn(true);
        when(mApInterface.writeHostapdConfig(
                any(), anyBoolean(), anyInt(), anyInt(), any())).thenReturn(true);
        when(mApInterface.getInterfaceName()).thenReturn(TEST_INTERFACE_NAME);
    }

    private WifiConfiguration createDefaultApConfig() {
        WifiConfiguration defaultConfig = new WifiConfiguration();
        defaultConfig.SSID = DEFAULT_SSID;
        return defaultConfig;
    }

    private SoftApManager createSoftApManager(WifiConfiguration config) throws Exception {
        when(mApInterface.asBinder()).thenReturn(mApInterfaceBinder);
        when(mApInterface.startHostapd()).thenReturn(true);
        when(mApInterface.stopHostapd()).thenReturn(true);
        if (config == null) {
            when(mWifiApConfigStore.getApConfiguration()).thenReturn(mDefaultApConfig);
        }
        SoftApManager newSoftApManager = new SoftApManager(mLooper.getLooper(),
                                                           mWifiNative,
                                                           TEST_COUNTRY_CODE,
                                                           mListener,
                                                           mApInterface,
                                                           mNmService,
                                                           mWifiApConfigStore,
                                                           config,
                                                           mWifiMetrics);
        mLooper.dispatchAll();
        return newSoftApManager;
    }

    /** Verifies startSoftAp will use default config if AP configuration is not provided. */
    @Test
    public void startSoftApWithoutConfig() throws Exception {
        startSoftApAndVerifyEnabled(null);
    }

    /** Verifies startSoftAp will use provided config and start AP. */
    @Test
    public void startSoftApWithConfig() throws Exception {
        WifiConfiguration config = new WifiConfiguration();
        config.apBand = WifiConfiguration.AP_BAND_2GHZ;
        config.SSID = TEST_SSID;
        startSoftApAndVerifyEnabled(config);
    }


    /**
     * Verifies startSoftAp will start with the hiddenSSID param set when it is set to true in the
     * supplied config.
     */
    @Test
    public void startSoftApWithHiddenSsidTrueInConfig() throws Exception {
        WifiConfiguration config = new WifiConfiguration();
        config.apBand = WifiConfiguration.AP_BAND_2GHZ;
        config.SSID = TEST_SSID;
        config.hiddenSSID = true;
        startSoftApAndVerifyEnabled(config);
    }

    /** Tests softap startup if default config fails to load. **/
    @Test
    public void startSoftApDefaultConfigFailedToLoad() throws Exception {
        when(mApInterface.asBinder()).thenReturn(mApInterfaceBinder);
        when(mApInterface.startHostapd()).thenReturn(true);
        when(mApInterface.stopHostapd()).thenReturn(true);
        when(mWifiApConfigStore.getApConfiguration()).thenReturn(null);
        SoftApManager newSoftApManager = new SoftApManager(mLooper.getLooper(),
                                                           mWifiNative,
                                                           TEST_COUNTRY_CODE,
                                                           mListener,
                                                           mApInterface,
                                                           mNmService,
                                                           mWifiApConfigStore,
                                                           null,
                                                           mWifiMetrics);
        mLooper.dispatchAll();
        newSoftApManager.start();
        mLooper.dispatchAll();
        verify(mListener).onStateChanged(WifiManager.WIFI_AP_STATE_FAILED,
                WifiManager.SAP_START_FAILURE_GENERAL);
    }

    /** Tests the handling of stop command when soft AP is not started. */
    @Test
    public void stopWhenNotStarted() throws Exception {
        mSoftApManager = createSoftApManager(null);
        mSoftApManager.stop();
        mLooper.dispatchAll();
        /* Verify no state changes. */
        verify(mListener, never()).onStateChanged(anyInt(), anyInt());
    }

    /** Tests the handling of stop command when soft AP is started. */
    @Test
    public void stopWhenStarted() throws Exception {
        startSoftApAndVerifyEnabled(null);

        InOrder order = inOrder(mListener);

        mSoftApManager.stop();
        mLooper.dispatchAll();

        verify(mApInterface).stopHostapd();
        order.verify(mListener).onStateChanged(WifiManager.WIFI_AP_STATE_DISABLING, 0);
        order.verify(mListener).onStateChanged(WifiManager.WIFI_AP_STATE_DISABLED, 0);
    }

    @Test
    public void handlesWificondInterfaceDeath() throws Exception {
        startSoftApAndVerifyEnabled(null);

        mDeathListenerCaptor.getValue().binderDied();
        mLooper.dispatchAll();
        InOrder order = inOrder(mListener);
        order.verify(mListener).onStateChanged(WifiManager.WIFI_AP_STATE_DISABLING, 0);
        order.verify(mListener).onStateChanged(WifiManager.WIFI_AP_STATE_FAILED,
                WifiManager.SAP_START_FAILURE_GENERAL);
    }

    /** Starts soft AP and verifies that it is enabled successfully. */
    protected void startSoftApAndVerifyEnabled(WifiConfiguration config) throws Exception {
        String expectedSSID;
        boolean expectedHiddenSsid;
        InOrder order = inOrder(mListener, mApInterfaceBinder, mApInterface, mNmService);

        when(mWifiNative.isHalStarted()).thenReturn(false);
        when(mWifiNative.setCountryCodeHal(TEST_COUNTRY_CODE.toUpperCase(Locale.ROOT)))
                .thenReturn(true);

        mSoftApManager = createSoftApManager(config);
        if (config == null) {
            when(mWifiApConfigStore.getApConfiguration()).thenReturn(mDefaultApConfig);
            expectedSSID = mDefaultApConfig.SSID;
            expectedHiddenSsid = mDefaultApConfig.hiddenSSID;
        } else {
            expectedSSID = config.SSID;
            expectedHiddenSsid = config.hiddenSSID;
        }

        mSoftApManager.start();
        mLooper.dispatchAll();
        order.verify(mListener).onStateChanged(WifiManager.WIFI_AP_STATE_ENABLING, 0);
        order.verify(mApInterfaceBinder).linkToDeath(mDeathListenerCaptor.capture(), eq(0));
        order.verify(mNmService).registerObserver(mNetworkObserverCaptor.capture());
        order.verify(mApInterface).writeHostapdConfig(
                eq(expectedSSID.getBytes(StandardCharsets.UTF_8)), eq(expectedHiddenSsid),
                anyInt(), anyInt(), any());
        order.verify(mApInterface).startHostapd();
        mNetworkObserverCaptor.getValue().interfaceLinkStateChanged(TEST_INTERFACE_NAME, true);
        mLooper.dispatchAll();
        order.verify(mListener).onStateChanged(WifiManager.WIFI_AP_STATE_ENABLED, 0);
    }

    /** Verifies that soft AP was not disabled. */
    protected void verifySoftApNotDisabled() throws Exception {
        verify(mListener, never()).onStateChanged(WifiManager.WIFI_AP_STATE_DISABLING, 0);
        verify(mListener, never()).onStateChanged(WifiManager.WIFI_AP_STATE_DISABLED, 0);
    }
}
