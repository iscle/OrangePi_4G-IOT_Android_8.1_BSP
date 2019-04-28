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
import android.app.test.MockAnswerUtil.AnswerWithArguments;
import android.hardware.wifi.V1_0.IWifiApIface;
import android.hardware.wifi.V1_0.IWifiChip;
import android.hardware.wifi.V1_0.IWifiChipEventCallback;
import android.hardware.wifi.V1_0.IWifiIface;
import android.hardware.wifi.V1_0.IWifiRttController;
import android.hardware.wifi.V1_0.IWifiRttControllerEventCallback;
import android.hardware.wifi.V1_0.IWifiStaIface;
import android.hardware.wifi.V1_0.IWifiStaIfaceEventCallback;
import android.hardware.wifi.V1_0.IfaceType;
import android.hardware.wifi.V1_0.RttCapabilities;
import android.hardware.wifi.V1_0.RttConfig;
import android.hardware.wifi.V1_0.StaApfPacketFilterCapabilities;
import android.hardware.wifi.V1_0.StaBackgroundScanCapabilities;
import android.hardware.wifi.V1_0.StaBackgroundScanParameters;
import android.hardware.wifi.V1_0.StaLinkLayerIfacePacketStats;
import android.hardware.wifi.V1_0.StaLinkLayerRadioStats;
import android.hardware.wifi.V1_0.StaLinkLayerStats;
import android.hardware.wifi.V1_0.StaScanData;
import android.hardware.wifi.V1_0.StaScanDataFlagMask;
import android.hardware.wifi.V1_0.StaScanResult;
import android.hardware.wifi.V1_0.WifiDebugHostWakeReasonStats;
import android.hardware.wifi.V1_0.WifiDebugPacketFateFrameType;
import android.hardware.wifi.V1_0.WifiDebugRingBufferFlags;
import android.hardware.wifi.V1_0.WifiDebugRingBufferStatus;
import android.hardware.wifi.V1_0.WifiDebugRingBufferVerboseLevel;
import android.hardware.wifi.V1_0.WifiDebugRxPacketFate;
import android.hardware.wifi.V1_0.WifiDebugRxPacketFateReport;
import android.hardware.wifi.V1_0.WifiDebugTxPacketFate;
import android.hardware.wifi.V1_0.WifiDebugTxPacketFateReport;
import android.hardware.wifi.V1_0.WifiInformationElement;
import android.hardware.wifi.V1_0.WifiStatus;
import android.hardware.wifi.V1_0.WifiStatusCode;
import android.net.apf.ApfCapabilities;
import android.net.wifi.RttManager;
import android.net.wifi.ScanResult;
import android.net.wifi.WifiLinkLayerStats;
import android.net.wifi.WifiManager;
import android.net.wifi.WifiScanner;
import android.net.wifi.WifiSsid;
import android.net.wifi.WifiWakeReasonAndCounts;
import android.os.Looper;
import android.os.RemoteException;
import android.os.test.TestLooper;
import android.util.Pair;

import com.android.server.connectivity.KeepalivePacketData;
import com.android.server.wifi.util.NativeUtil;

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.mockito.stubbing.Answer;

import java.net.InetAddress;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;

/**
 * Unit tests for {@link com.android.server.wifi.WifiVendorHal}.
 */
public class WifiVendorHalTest {

    WifiVendorHal mWifiVendorHal;
    private WifiStatus mWifiStatusSuccess;
    private WifiStatus mWifiStatusFailure;
    WifiLog mWifiLog;
    @Mock
    private HalDeviceManager mHalDeviceManager;
    @Mock
    private TestLooper mLooper;
    @Mock
    private WifiVendorHal.HalDeviceManagerStatusListener mHalDeviceManagerStatusCallbacks;
    @Mock
    private IWifiApIface mIWifiApIface;
    @Mock
    private IWifiChip mIWifiChip;
    @Mock
    private android.hardware.wifi.V1_1.IWifiChip mIWifiChipV11;
    @Mock
    private IWifiStaIface mIWifiStaIface;
    @Mock
    private IWifiRttController mIWifiRttController;
    private IWifiStaIfaceEventCallback mIWifiStaIfaceEventCallback;
    private IWifiChipEventCallback mIWifiChipEventCallback;
    @Mock
    private WifiNative.VendorHalDeathEventHandler mVendorHalDeathHandler;

    /**
     * Spy used to return the V1_1 IWifiChip mock object to simulate the 1.1 HAL running on the
     * device.
     */
    private class WifiVendorHalSpyV1_1 extends WifiVendorHal {
        WifiVendorHalSpyV1_1(HalDeviceManager halDeviceManager, Looper looper) {
            super(halDeviceManager, looper);
        }

        @Override
        protected android.hardware.wifi.V1_1.IWifiChip getWifiChipForV1_1Mockable() {
            return mIWifiChipV11;
        }
    }

    /**
     * Identity function to supply a type to its argument, which is a lambda
     */
    static Answer<WifiStatus> answerWifiStatus(Answer<WifiStatus> statusLambda) {
        return (statusLambda);
    }

    /**
     * Sets up for unit test
     */
    @Before
    public void setUp() throws Exception {
        MockitoAnnotations.initMocks(this);
        mWifiLog = new FakeWifiLog();
        mLooper = new TestLooper();
        mWifiStatusSuccess = new WifiStatus();
        mWifiStatusSuccess.code = WifiStatusCode.SUCCESS;
        mWifiStatusFailure = new WifiStatus();
        mWifiStatusFailure.code = WifiStatusCode.ERROR_UNKNOWN;
        mWifiStatusFailure.description = "I don't even know what a Mock Turtle is.";
        when(mIWifiStaIface.enableLinkLayerStatsCollection(false)).thenReturn(mWifiStatusSuccess);

        // Setup the HalDeviceManager mock's start/stop behaviour. This can be overridden in
        // individual tests, if needed.
        doAnswer(new AnswerWithArguments() {
            public boolean answer() {
                when(mHalDeviceManager.isReady()).thenReturn(true);
                when(mHalDeviceManager.isStarted()).thenReturn(true);
                mHalDeviceManagerStatusCallbacks.onStatusChanged();
                return true;
            }
        }).when(mHalDeviceManager).start();

        doAnswer(new AnswerWithArguments() {
            public void answer() {
                when(mHalDeviceManager.isReady()).thenReturn(true);
                when(mHalDeviceManager.isStarted()).thenReturn(false);
                mHalDeviceManagerStatusCallbacks.onStatusChanged();
            }
        }).when(mHalDeviceManager).stop();
        when(mHalDeviceManager.createStaIface(eq(null), eq(null)))
                .thenReturn(mIWifiStaIface);
        when(mHalDeviceManager.createApIface(eq(null), eq(null)))
                .thenReturn(mIWifiApIface);
        when(mHalDeviceManager.getChip(any(IWifiIface.class)))
                .thenReturn(mIWifiChip);
        when(mHalDeviceManager.createRttController(any(IWifiIface.class)))
                .thenReturn(mIWifiRttController);
        when(mIWifiChip.registerEventCallback(any(IWifiChipEventCallback.class)))
                .thenReturn(mWifiStatusSuccess);
        mIWifiStaIfaceEventCallback = null;
        when(mIWifiStaIface.registerEventCallback(any(IWifiStaIfaceEventCallback.class)))
                .thenAnswer(answerWifiStatus((invocation) -> {
                    Object[] args = invocation.getArguments();
                    mIWifiStaIfaceEventCallback = (IWifiStaIfaceEventCallback) args[0];
                    return (mWifiStatusSuccess);
                }));
        mIWifiChipEventCallback = null;
        when(mIWifiChip.registerEventCallback(any(IWifiChipEventCallback.class)))
                .thenAnswer(answerWifiStatus((invocation) -> {
                    Object[] args = invocation.getArguments();
                    mIWifiChipEventCallback = (IWifiChipEventCallback) args[0];
                    return (mWifiStatusSuccess);
                }));

        when(mIWifiRttController.registerEventCallback(any(IWifiRttControllerEventCallback.class)))
                .thenReturn(mWifiStatusSuccess);

        // Create the vendor HAL object under test.
        mWifiVendorHal = new WifiVendorHal(mHalDeviceManager, mLooper.getLooper());

        // Initialize the vendor HAL to capture the registered callback.
        mWifiVendorHal.initialize(mVendorHalDeathHandler);
        ArgumentCaptor<WifiVendorHal.HalDeviceManagerStatusListener> hdmCallbackCaptor =
                ArgumentCaptor.forClass(WifiVendorHal.HalDeviceManagerStatusListener.class);
        verify(mHalDeviceManager).registerStatusListener(hdmCallbackCaptor.capture(), any());
        mHalDeviceManagerStatusCallbacks = hdmCallbackCaptor.getValue();

    }

    /**
     * Tests the successful starting of HAL in STA mode using
     * {@link WifiVendorHal#startVendorHal(boolean)}.
     */
    @Test
    public void testStartHalSuccessInStaMode() throws  Exception {
        assertTrue(mWifiVendorHal.startVendorHal(true));
        assertTrue(mWifiVendorHal.isHalStarted());

        verify(mHalDeviceManager).start();
        verify(mHalDeviceManager).createStaIface(eq(null), eq(null));
        verify(mHalDeviceManager).getChip(eq(mIWifiStaIface));
        verify(mHalDeviceManager).createRttController(eq(mIWifiStaIface));
        verify(mHalDeviceManager).isReady();
        verify(mHalDeviceManager).isStarted();
        verify(mIWifiStaIface).registerEventCallback(any(IWifiStaIfaceEventCallback.class));
        verify(mIWifiChip).registerEventCallback(any(IWifiChipEventCallback.class));

        verify(mHalDeviceManager, never()).createApIface(eq(null), eq(null));
    }

    /**
     * Tests the successful starting of HAL in AP mode using
     * {@link WifiVendorHal#startVendorHal(boolean)}.
     */
    @Test
    public void testStartHalSuccessInApMode() throws Exception {
        assertTrue(mWifiVendorHal.startVendorHal(false));
        assertTrue(mWifiVendorHal.isHalStarted());

        verify(mHalDeviceManager).start();
        verify(mHalDeviceManager).createApIface(eq(null), eq(null));
        verify(mHalDeviceManager).getChip(eq(mIWifiApIface));
        verify(mHalDeviceManager).isReady();
        verify(mHalDeviceManager).isStarted();

        verify(mHalDeviceManager, never()).createStaIface(eq(null), eq(null));
        verify(mHalDeviceManager, never()).createRttController(any(IWifiIface.class));
    }

    /**
     * Tests the failure to start HAL in STA mode using
     * {@link WifiVendorHal#startVendorHal(boolean)}.
     */
    @Test
    public void testStartHalFailureInStaMode() throws Exception {
        // No callbacks are invoked in this case since the start itself failed. So, override
        // default AnswerWithArguments that we setup.
        doAnswer(new AnswerWithArguments() {
            public boolean answer() throws Exception {
                return false;
            }
        }).when(mHalDeviceManager).start();
        assertFalse(mWifiVendorHal.startVendorHal(true));
        assertFalse(mWifiVendorHal.isHalStarted());

        verify(mHalDeviceManager).start();

        verify(mHalDeviceManager, never()).createStaIface(eq(null), eq(null));
        verify(mHalDeviceManager, never()).createApIface(eq(null), eq(null));
        verify(mHalDeviceManager, never()).getChip(any(IWifiIface.class));
        verify(mHalDeviceManager, never()).createRttController(any(IWifiIface.class));
        verify(mIWifiStaIface, never())
                .registerEventCallback(any(IWifiStaIfaceEventCallback.class));
    }

    /**
     * Tests the failure to start HAL in STA mode using
     * {@link WifiVendorHal#startVendorHal(boolean)}.
     */
    @Test
    public void testStartHalFailureInIfaceCreationInStaMode() throws Exception {
        when(mHalDeviceManager.createStaIface(eq(null), eq(null))).thenReturn(null);
        assertFalse(mWifiVendorHal.startVendorHal(true));
        assertFalse(mWifiVendorHal.isHalStarted());

        verify(mHalDeviceManager).start();
        verify(mHalDeviceManager).createStaIface(eq(null), eq(null));
        verify(mHalDeviceManager).stop();

        verify(mHalDeviceManager, never()).createApIface(eq(null), eq(null));
        verify(mHalDeviceManager, never()).getChip(any(IWifiIface.class));
        verify(mHalDeviceManager, never()).createRttController(any(IWifiIface.class));
        verify(mIWifiStaIface, never())
                .registerEventCallback(any(IWifiStaIfaceEventCallback.class));
    }

    /**
     * Tests the failure to start HAL in STA mode using
     * {@link WifiVendorHal#startVendorHal(boolean)}.
     */
    @Test
    public void testStartHalFailureInRttControllerCreationInStaMode() throws Exception {
        when(mHalDeviceManager.createRttController(any(IWifiIface.class))).thenReturn(null);
        assertFalse(mWifiVendorHal.startVendorHal(true));
        assertFalse(mWifiVendorHal.isHalStarted());

        verify(mHalDeviceManager).start();
        verify(mHalDeviceManager).createStaIface(eq(null), eq(null));
        verify(mHalDeviceManager).createRttController(eq(mIWifiStaIface));
        verify(mHalDeviceManager).stop();
        verify(mIWifiStaIface).registerEventCallback(any(IWifiStaIfaceEventCallback.class));

        verify(mHalDeviceManager, never()).createApIface(eq(null), eq(null));
        verify(mHalDeviceManager, never()).getChip(any(IWifiIface.class));
    }

    /**
     * Tests the failure to start HAL in STA mode using
     * {@link WifiVendorHal#startVendorHal(boolean)}.
     */
    @Test
    public void testStartHalFailureInChipGetInStaMode() throws Exception {
        when(mHalDeviceManager.getChip(any(IWifiIface.class))).thenReturn(null);
        assertFalse(mWifiVendorHal.startVendorHal(true));
        assertFalse(mWifiVendorHal.isHalStarted());

        verify(mHalDeviceManager).start();
        verify(mHalDeviceManager).createStaIface(eq(null), eq(null));
        verify(mHalDeviceManager).createRttController(eq(mIWifiStaIface));
        verify(mHalDeviceManager).getChip(any(IWifiIface.class));
        verify(mHalDeviceManager).stop();
        verify(mIWifiStaIface).registerEventCallback(any(IWifiStaIfaceEventCallback.class));

        verify(mHalDeviceManager, never()).createApIface(eq(null), eq(null));
    }

    /**
     * Tests the failure to start HAL in STA mode using
     * {@link WifiVendorHal#startVendorHal(boolean)}.
     */
    @Test
    public void testStartHalFailureInStaIfaceCallbackRegistration() throws Exception {
        when(mIWifiStaIface.registerEventCallback(any(IWifiStaIfaceEventCallback.class)))
                .thenReturn(mWifiStatusFailure);
        assertFalse(mWifiVendorHal.startVendorHal(true));
        assertFalse(mWifiVendorHal.isHalStarted());

        verify(mHalDeviceManager).start();
        verify(mHalDeviceManager).createStaIface(eq(null), eq(null));
        verify(mHalDeviceManager).stop();
        verify(mIWifiStaIface).registerEventCallback(any(IWifiStaIfaceEventCallback.class));

        verify(mHalDeviceManager, never()).createRttController(eq(mIWifiStaIface));
        verify(mHalDeviceManager, never()).getChip(any(IWifiIface.class));
        verify(mHalDeviceManager, never()).createApIface(eq(null), eq(null));
    }

    /**
     * Tests the failure to start HAL in STA mode using
     * {@link WifiVendorHal#startVendorHal(boolean)}.
     */
    @Test
    public void testStartHalFailureInChipCallbackRegistration() throws Exception {
        when(mIWifiChip.registerEventCallback(any(IWifiChipEventCallback.class)))
                .thenReturn(mWifiStatusFailure);
        assertFalse(mWifiVendorHal.startVendorHal(true));
        assertFalse(mWifiVendorHal.isHalStarted());

        verify(mHalDeviceManager).start();
        verify(mHalDeviceManager).createStaIface(eq(null), eq(null));
        verify(mHalDeviceManager).createRttController(eq(mIWifiStaIface));
        verify(mHalDeviceManager).getChip(any(IWifiIface.class));
        verify(mHalDeviceManager).stop();
        verify(mIWifiStaIface).registerEventCallback(any(IWifiStaIfaceEventCallback.class));
        verify(mIWifiChip).registerEventCallback(any(IWifiChipEventCallback.class));

        verify(mHalDeviceManager, never()).createApIface(eq(null), eq(null));
    }

    /**
     * Tests the failure to start HAL in STA mode using
     * {@link WifiVendorHal#startVendorHal(boolean)}.
     */
    @Test
    public void testStartHalFailureInApMode() throws Exception {
        when(mHalDeviceManager.createApIface(eq(null), eq(null))).thenReturn(null);
        assertFalse(mWifiVendorHal.startVendorHal(false));
        assertFalse(mWifiVendorHal.isHalStarted());

        verify(mHalDeviceManager).start();
        verify(mHalDeviceManager).createApIface(eq(null), eq(null));
        verify(mHalDeviceManager).stop();

        verify(mHalDeviceManager, never()).createStaIface(eq(null), eq(null));
        verify(mHalDeviceManager, never()).getChip(any(IWifiIface.class));
        verify(mHalDeviceManager, never()).createRttController(any(IWifiIface.class));
    }

    /**
     * Tests the stopping of HAL in STA mode using
     * {@link WifiVendorHal#stopVendorHal()}.
     */
    @Test
    public void testStopHalInStaMode() {
        assertTrue(mWifiVendorHal.startVendorHal(true));
        assertTrue(mWifiVendorHal.isHalStarted());

        mWifiVendorHal.stopVendorHal();
        assertFalse(mWifiVendorHal.isHalStarted());

        verify(mHalDeviceManager).start();
        verify(mHalDeviceManager).stop();
        verify(mHalDeviceManager).createStaIface(eq(null), eq(null));
        verify(mHalDeviceManager).getChip(eq(mIWifiStaIface));
        verify(mHalDeviceManager).createRttController(eq(mIWifiStaIface));
        verify(mHalDeviceManager, times(2)).isReady();
        verify(mHalDeviceManager, times(2)).isStarted();

        verify(mHalDeviceManager, never()).createApIface(eq(null), eq(null));
    }

    /**
     * Tests the stopping of HAL in AP mode using
     * {@link WifiVendorHal#stopVendorHal()}.
     */
    @Test
    public void testStopHalInApMode() {
        assertTrue(mWifiVendorHal.startVendorHal(false));
        assertTrue(mWifiVendorHal.isHalStarted());

        mWifiVendorHal.stopVendorHal();
        assertFalse(mWifiVendorHal.isHalStarted());

        verify(mHalDeviceManager).start();
        verify(mHalDeviceManager).stop();
        verify(mHalDeviceManager).createApIface(eq(null), eq(null));
        verify(mHalDeviceManager).getChip(eq(mIWifiApIface));
        verify(mHalDeviceManager, times(2)).isReady();
        verify(mHalDeviceManager, times(2)).isStarted();

        verify(mHalDeviceManager, never()).createStaIface(eq(null), eq(null));
        verify(mHalDeviceManager, never()).createRttController(any(IWifiIface.class));
    }

    /**
     * Test that enter logs when verbose logging is enabled
     */
    @Test
    public void testEnterLogging() {
        mWifiVendorHal.mLog = spy(mWifiLog);
        mWifiVendorHal.enableVerboseLogging(true);
        mWifiVendorHal.installPacketFilter(new byte[0]);
        verify(mWifiVendorHal.mLog).trace(eq("% filter length %"));
    }

    /**
     * Test that enter does not log when verbose logging is not enabled
     */
    @Test
    public void testEnterSilenceWhenNotEnabled() {
        mWifiVendorHal.mLog = spy(mWifiLog);
        mWifiVendorHal.installPacketFilter(new byte[0]);
        mWifiVendorHal.enableVerboseLogging(true);
        mWifiVendorHal.enableVerboseLogging(false);
        mWifiVendorHal.installPacketFilter(new byte[0]);
        verify(mWifiVendorHal.mLog, never()).trace(eq("% filter length %"));
    }

    /**
     * Test that boolResult logs a false result
     */
    @Test
    public void testBoolResultFalse() {
        mWifiLog = spy(mWifiLog);
        mWifiVendorHal.mLog = mWifiLog;
        mWifiVendorHal.mVerboseLog = mWifiLog;
        assertFalse(mWifiVendorHal.getBgScanCapabilities(new WifiNative.ScanCapabilities()));
        verify(mWifiLog).err("% returns %");
    }

    /**
     * Test that getBgScanCapabilities is hooked up to the HAL correctly
     *
     * A call before the vendor HAL is started should return a non-null result with version 0
     *
     * A call after the HAL is started should return the mocked values.
     */
    @Test
    public void testGetBgScanCapabilities() throws Exception {
        StaBackgroundScanCapabilities capabilities = new StaBackgroundScanCapabilities();
        capabilities.maxCacheSize = 12;
        capabilities.maxBuckets = 34;
        capabilities.maxApCachePerScan = 56;
        capabilities.maxReportingThreshold = 78;

        doAnswer(new AnswerWithArguments() {
            public void answer(IWifiStaIface.getBackgroundScanCapabilitiesCallback cb)
                    throws RemoteException {
                cb.onValues(mWifiStatusSuccess, capabilities);
            }
        }).when(mIWifiStaIface).getBackgroundScanCapabilities(any(
                IWifiStaIface.getBackgroundScanCapabilitiesCallback.class));

        WifiNative.ScanCapabilities result = new WifiNative.ScanCapabilities();

        assertFalse(mWifiVendorHal.getBgScanCapabilities(result));  // should fail - not started
        assertTrue(mWifiVendorHal.startVendorHalSta());           // Start the vendor hal
        assertTrue(mWifiVendorHal.getBgScanCapabilities(result));   // should succeed

        assertEquals(12, result.max_scan_cache_size);
        assertEquals(34, result.max_scan_buckets);
        assertEquals(56, result.max_ap_cache_per_scan);
        assertEquals(78, result.max_scan_reporting_threshold);
    }

    private void setupValidFrequenciesForBand(ArrayList<Integer> frequencies) throws Exception {

        doAnswer(new AnswerWithArguments() {
            public void answer(int band, IWifiStaIface.getValidFrequenciesForBandCallback cb)
                    throws RemoteException {
                cb.onValues(mWifiStatusSuccess, frequencies);
            }
        }).when(mIWifiStaIface).getValidFrequenciesForBand(anyInt(), any(
                IWifiStaIface.getValidFrequenciesForBandCallback.class));

        doAnswer(new AnswerWithArguments() {
            public void answer(int band, IWifiApIface.getValidFrequenciesForBandCallback cb)
                    throws RemoteException {
                cb.onValues(mWifiStatusSuccess, frequencies);
            }
        }).when(mIWifiApIface).getValidFrequenciesForBand(anyInt(), any(
                IWifiApIface.getValidFrequenciesForBandCallback.class));

    }

    private int[] intArrayFromArrayList(ArrayList<Integer> in) {
        int[] ans = new int[in.size()];
        int i = 0;
        for (Integer e : in) ans[i++] = e;
        return ans;
    }

    /**
     * Test that isGetChannelsForBandSupported works in STA mode
     */
    @Test
    public void testGetChannelsForBandSupportedSta() throws Exception {
        ArrayList<Integer> freq = new ArrayList<>();
        freq.add(2405);

        setupValidFrequenciesForBand(freq);

        assertFalse(mWifiVendorHal.isGetChannelsForBandSupported());

        assertTrue(mWifiVendorHal.startVendorHalSta());

        assertTrue(mWifiVendorHal.isGetChannelsForBandSupported());
    }

    /**
     * Test that isGetChannelsForBandSupported works in AP mode
     */
    @Test
    public void testGetChannelsForBandSupportedAp() throws Exception {
        ArrayList<Integer> freq = new ArrayList<>();
        freq.add(2405);

        setupValidFrequenciesForBand(freq);

        assertFalse(mWifiVendorHal.isGetChannelsForBandSupported());

        assertTrue(mWifiVendorHal.startVendorHalAp());

        assertTrue(mWifiVendorHal.isGetChannelsForBandSupported());
    }

    /**
     * Test translation to WifiManager.WIFI_FEATURE_*
     *
     * Just do a spot-check with a few feature bits here; since the code is table-
     * driven we don't have to work hard to exercise all of it.
     */
    @Test
    public void testStaIfaceFeatureMaskTranslation() {
        int caps = (
                IWifiStaIface.StaIfaceCapabilityMask.BACKGROUND_SCAN
                | IWifiStaIface.StaIfaceCapabilityMask.LINK_LAYER_STATS
            );
        int expected = (
                WifiManager.WIFI_FEATURE_SCANNER
                | WifiManager.WIFI_FEATURE_LINK_LAYER_STATS);
        assertEquals(expected, mWifiVendorHal.wifiFeatureMaskFromStaCapabilities(caps));
    }

    /**
     * Test translation to WifiManager.WIFI_FEATURE_*
     *
     * Just do a spot-check with a few feature bits here; since the code is table-
     * driven we don't have to work hard to exercise all of it.
     */
    @Test
    public void testChipFeatureMaskTranslation() {
        int caps = (
                android.hardware.wifi.V1_1.IWifiChip.ChipCapabilityMask.SET_TX_POWER_LIMIT
                        | android.hardware.wifi.V1_1.IWifiChip.ChipCapabilityMask.D2D_RTT
                        | android.hardware.wifi.V1_1.IWifiChip.ChipCapabilityMask.D2AP_RTT
        );
        int expected = (
                WifiManager.WIFI_FEATURE_TX_POWER_LIMIT
                        | WifiManager.WIFI_FEATURE_D2D_RTT
                        | WifiManager.WIFI_FEATURE_D2AP_RTT
        );
        assertEquals(expected, mWifiVendorHal.wifiFeatureMaskFromChipCapabilities(caps));
    }

    /**
     * Test get supported features. Tests whether we coalesce information from different sources
     * (IWifiStaIface, IWifiChip and HalDeviceManager) into the bitmask of supported features
     * correctly.
     */
    @Test
    public void testGetSupportedFeatures() throws Exception {
        assertTrue(mWifiVendorHal.startVendorHal(true));

        int staIfaceHidlCaps = (
                IWifiStaIface.StaIfaceCapabilityMask.BACKGROUND_SCAN
                        | IWifiStaIface.StaIfaceCapabilityMask.LINK_LAYER_STATS
        );
        int chipHidlCaps =
                android.hardware.wifi.V1_1.IWifiChip.ChipCapabilityMask.SET_TX_POWER_LIMIT;
        Set<Integer>  halDeviceManagerSupportedIfaces = new HashSet<Integer>() {{
                add(IfaceType.STA);
                add(IfaceType.P2P);
            }};
        int expectedFeatureSet = (
                WifiManager.WIFI_FEATURE_SCANNER
                        | WifiManager.WIFI_FEATURE_LINK_LAYER_STATS
                        | WifiManager.WIFI_FEATURE_TX_POWER_LIMIT
                        | WifiManager.WIFI_FEATURE_INFRA
                        | WifiManager.WIFI_FEATURE_P2P
        );

        doAnswer(new AnswerWithArguments() {
            public void answer(IWifiStaIface.getCapabilitiesCallback cb) throws RemoteException {
                cb.onValues(mWifiStatusSuccess, staIfaceHidlCaps);
            }
        }).when(mIWifiStaIface).getCapabilities(any(IWifiStaIface.getCapabilitiesCallback.class));
        doAnswer(new AnswerWithArguments() {
            public void answer(IWifiChip.getCapabilitiesCallback cb) throws RemoteException {
                cb.onValues(mWifiStatusSuccess, chipHidlCaps);
            }
        }).when(mIWifiChip).getCapabilities(any(IWifiChip.getCapabilitiesCallback.class));
        when(mHalDeviceManager.getSupportedIfaceTypes())
                .thenReturn(halDeviceManagerSupportedIfaces);

        assertEquals(expectedFeatureSet, mWifiVendorHal.getSupportedFeatureSet());
    }

    /**
     * Test enablement of link layer stats after startup
     *
     * Request link layer stats before HAL start
     * - should not make it to the HAL layer
     * Start the HAL in STA mode
     * Request link layer stats twice more
     * - enable request should make it to the HAL layer
     * - HAL layer should have been called to make the requests (i.e., two calls total)
     */
    @Test
    public void testLinkLayerStatsEnableAfterStartup() throws Exception {
        doNothing().when(mIWifiStaIface).getLinkLayerStats(any());

        assertNull(mWifiVendorHal.getWifiLinkLayerStats());
        assertTrue(mWifiVendorHal.startVendorHalSta());
        assertTrue(mWifiVendorHal.isHalStarted());

        verify(mHalDeviceManager).start();
        mWifiVendorHal.getWifiLinkLayerStats();
        mWifiVendorHal.getWifiLinkLayerStats();
        verify(mIWifiStaIface).enableLinkLayerStatsCollection(false); // mLinkLayerStatsDebug
        verify(mIWifiStaIface, times(2)).getLinkLayerStats(any());
    }

    /**
     * Test that link layer stats are not enabled and harmless in AP mode
     *
     * Start the HAL in AP mode
     * - stats should not be enabled
     * Request link layer stats
     * - HAL layer should have been called to make the request
     */
    @Test
    public void testLinkLayerStatsNotEnabledAndHarmlessInApMode() throws Exception {
        doNothing().when(mIWifiStaIface).getLinkLayerStats(any());

        assertTrue(mWifiVendorHal.startVendorHalAp());
        assertTrue(mWifiVendorHal.isHalStarted());
        assertNull(mWifiVendorHal.getWifiLinkLayerStats());

        verify(mHalDeviceManager).start();

        verify(mIWifiStaIface, never()).enableLinkLayerStatsCollection(false);
        verify(mIWifiStaIface, never()).getLinkLayerStats(any());
    }

    /**
     * Test that the link layer stats fields are populated correctly.
     *
     * This is done by filling with random values and then using toString on the
     * original and converted values, comparing just the numerics in the result.
     * This makes the assumption that the fields are in the same order in both string
     * representations, which is not quite true. So apply some fixups before the final
     * comparison.
     */
    @Test
    public void testLinkLayerStatsAssignment() throws Exception {
        Random r = new Random(1775968256);
        StaLinkLayerStats stats = new StaLinkLayerStats();
        randomizePacketStats(r, stats.iface.wmeBePktStats);
        randomizePacketStats(r, stats.iface.wmeBkPktStats);
        randomizePacketStats(r, stats.iface.wmeViPktStats);
        randomizePacketStats(r, stats.iface.wmeVoPktStats);
        randomizeRadioStats(r, stats.radios);

        stats.timeStampInMs = 42; // currently dropped in conversion

        String expected = numbersOnly(stats.toString());

        WifiLinkLayerStats converted = WifiVendorHal.frameworkFromHalLinkLayerStats(stats);

        String actual = numbersOnly(converted.toString());

        // Do the required fixups to the both expected and actual
        expected = rmValue(expected, stats.radios.get(0).rxTimeInMs);
        expected = rmValue(expected, stats.radios.get(0).onTimeInMsForScan);

        actual = rmValue(actual, stats.radios.get(0).rxTimeInMs);
        actual = rmValue(actual, stats.radios.get(0).onTimeInMsForScan);
        actual = actual + "42 ";

        // The remaining fields should agree
        assertEquals(expected, actual);
    }

    /** Just the digits with delimiting spaces, please */
    private static String numbersOnly(String s) {
        return s.replaceAll("[^0-9]+", " ");
    }

    /** Remove the given value from the space-delimited string, or die trying. */
    private static String rmValue(String s, long value) throws Exception {
        String ans = s.replaceAll(" " + value + " ", " ");
        assertNotEquals(s, ans);
        return ans;
    }

    /**
     * Populate packet stats with non-negative random values
     */
    private static void randomizePacketStats(Random r, StaLinkLayerIfacePacketStats pstats) {
        pstats.rxMpdu = r.nextLong() & 0xFFFFFFFFFFL; // more than 32 bits
        pstats.txMpdu = r.nextLong() & 0xFFFFFFFFFFL;
        pstats.lostMpdu = r.nextLong() & 0xFFFFFFFFFFL;
        pstats.retries = r.nextLong() & 0xFFFFFFFFFFL;
    }

   /**
     * Populate radio stats with non-negative random values
     */
    private static void randomizeRadioStats(Random r, ArrayList<StaLinkLayerRadioStats> rstats) {
        StaLinkLayerRadioStats rstat = new StaLinkLayerRadioStats();
        rstat.onTimeInMs = r.nextInt() & 0xFFFFFF;
        rstat.txTimeInMs = r.nextInt() & 0xFFFFFF;
        for (int i = 0; i < 4; i++) {
            Integer v = r.nextInt() & 0xFFFFFF;
            rstat.txTimeInMsPerLevel.add(v);
        }
        rstat.rxTimeInMs = r.nextInt() & 0xFFFFFF;
        rstat.onTimeInMsForScan = r.nextInt() & 0xFFFFFF;
        rstats.add(rstat);
    }

    /**
     * Test that getFirmwareVersion() and getDriverVersion() work
     *
     * Calls before the STA is started are expected to return null.
     */
    @Test
    public void testVersionGetters() throws Exception {
        String firmwareVersion = "fuzzy";
        String driverVersion = "dizzy";
        IWifiChip.ChipDebugInfo chipDebugInfo = new IWifiChip.ChipDebugInfo();
        chipDebugInfo.firmwareDescription = firmwareVersion;
        chipDebugInfo.driverDescription = driverVersion;

        doAnswer(new AnswerWithArguments() {
            public void answer(IWifiChip.requestChipDebugInfoCallback cb) throws RemoteException {
                cb.onValues(mWifiStatusSuccess, chipDebugInfo);
            }
        }).when(mIWifiChip).requestChipDebugInfo(any(IWifiChip.requestChipDebugInfoCallback.class));

        assertNull(mWifiVendorHal.getFirmwareVersion());
        assertNull(mWifiVendorHal.getDriverVersion());

        assertTrue(mWifiVendorHal.startVendorHalSta());

        assertEquals(firmwareVersion, mWifiVendorHal.getFirmwareVersion());
        assertEquals(driverVersion, mWifiVendorHal.getDriverVersion());
    }

    /**
     * For checkRoundTripIntTranslation lambdas
     */
    interface IntForInt {
        int translate(int value);
    }

    /**
     * Checks that translation from x to y and back again is the identity function
     *
     * @param xFromY reverse translator
     * @param yFromX forward translator
     * @param xLimit non-inclusive upper bound on x (lower bound is zero)
     */
    private void checkRoundTripIntTranslation(
            IntForInt xFromY, IntForInt yFromX, int xFirst, int xLimit) throws Exception {
        int ex = 0;
        for (int i = xFirst; i < xLimit; i++) {
            assertEquals(i, xFromY.translate(yFromX.translate(i)));
        }
        try {
            yFromX.translate(xLimit);
            assertTrue("expected an exception here", false);
        } catch (IllegalArgumentException e) {
            ex++;
        }
        try {
            xFromY.translate(yFromX.translate(xLimit - 1) + 1);
            assertTrue("expected an exception here", false);
        } catch (IllegalArgumentException e) {
            ex++;
        }
        assertEquals(2, ex);
    }


    /**
     * Test translations of RTT type
     */
    @Test
    public void testRttTypeTranslation() throws Exception {
        checkRoundTripIntTranslation(
                (y) -> WifiVendorHal.halRttTypeFromFrameworkRttType(y),
                (x) -> WifiVendorHal.frameworkRttTypeFromHalRttType(x),
                1, 3);
    }

    /**
     * Test translations of peer type
     */
    @Test
    public void testPeerTranslation() throws Exception {
        checkRoundTripIntTranslation(
                (y) -> WifiVendorHal.halPeerFromFrameworkPeer(y),
                (x) -> WifiVendorHal.frameworkPeerFromHalPeer(x),
                1, 6);
    }

    /**
     * Test translations of channel width
     */
    @Test
    public void testChannelWidth() throws Exception {
        checkRoundTripIntTranslation(
                (y) -> WifiVendorHal.halChannelWidthFromFrameworkChannelWidth(y),
                (x) -> WifiVendorHal.frameworkChannelWidthFromHalChannelWidth(x),
                0, 5);
    }

    /**
     * Test translations of preamble type mask
     */
    @Test
    public void testPreambleTranslation() throws Exception {
        checkRoundTripIntTranslation(
                (y) -> WifiVendorHal.halPreambleFromFrameworkPreamble(y),
                (x) -> WifiVendorHal.frameworkPreambleFromHalPreamble(x),
                0, 8);
    }

    /**
     * Test translations of bandwidth mask
     */
    @Test
    public void testBandwidthTranslations() throws Exception {
        checkRoundTripIntTranslation(
                (y) -> WifiVendorHal.halBwFromFrameworkBw(y),
                (x) -> WifiVendorHal.frameworkBwFromHalBw(x),
                0, 64);
    }

    @Test
    public void testGetRttStuff() throws Exception {
        RttManager.RttParams params = new RttManager.RttParams();
        //TODO(b/34901744) populate
        RttConfig config = WifiVendorHal.halRttConfigFromFrameworkRttParams(params);
        //TODO(b/34901744) check
    }

    @Test
    public void testGetRttCapabilities() throws Exception {
        RttCapabilities capabilities = new RttCapabilities();
        //TODO(b/34901744) populate

        doAnswer(new AnswerWithArguments() {
            public void answer(IWifiRttController.getCapabilitiesCallback cb)
                    throws RemoteException {
                cb.onValues(mWifiStatusSuccess, capabilities);
            }
        }).when(mIWifiRttController).getCapabilities(any(
                IWifiRttController.getCapabilitiesCallback.class));

        assertNull(mWifiVendorHal.getRttCapabilities());

        assertTrue(mWifiVendorHal.startVendorHalSta());

        RttManager.RttCapabilities actual = mWifiVendorHal.getRttCapabilities();
        //TODO(b/34901744) check

    }

    //TODO(b/34901744) negative RTT test cases as well.
    // e.g. invoke RTT without putting the HAL in the correct mode.

    /**
     * Test that setScanningMacOui is hooked up to the HAL correctly
     */
    @Test
    public void testSetScanningMacOui() throws Exception {
        byte[] oui = NativeUtil.macAddressOuiToByteArray("DA:A1:19");
        byte[] zzz = NativeUtil.macAddressOuiToByteArray("00:00:00");

        when(mIWifiStaIface.setScanningMacOui(any())).thenReturn(mWifiStatusSuccess);

        assertFalse(mWifiVendorHal.setScanningMacOui(oui)); // expect fail - STA not started
        assertTrue(mWifiVendorHal.startVendorHalSta());
        assertFalse(mWifiVendorHal.setScanningMacOui(null));  // expect fail - null
        assertFalse(mWifiVendorHal.setScanningMacOui(new byte[]{(byte) 1})); // expect fail - len
        assertTrue(mWifiVendorHal.setScanningMacOui(oui));
        assertTrue(mWifiVendorHal.setScanningMacOui(zzz));

        verify(mIWifiStaIface).setScanningMacOui(eq(oui));
        verify(mIWifiStaIface).setScanningMacOui(eq(zzz));
    }

    @Test
    public void testStartSendingOffloadedPacket() throws Exception {
        byte[] srcMac = NativeUtil.macAddressToByteArray("4007b2088c81");
        InetAddress src = InetAddress.parseNumericAddress("192.168.13.13");
        InetAddress dst = InetAddress.parseNumericAddress("93.184.216.34");
        int slot = 13;
        int millis = 16000;

        KeepalivePacketData kap = KeepalivePacketData.nattKeepalivePacket(src, 63000, dst, 4500);

        when(mIWifiStaIface.startSendingKeepAlivePackets(
                anyInt(), any(), anyShort(), any(), any(), anyInt()
        )).thenReturn(mWifiStatusSuccess);

        assertTrue(mWifiVendorHal.startVendorHalSta());
        assertTrue(0 == mWifiVendorHal.startSendingOffloadedPacket(slot, srcMac, kap, millis));

        verify(mIWifiStaIface).startSendingKeepAlivePackets(
                eq(slot), any(), anyShort(), any(), any(), eq(millis));
    }

    @Test
    public void testStopSendingOffloadedPacket() throws Exception {
        int slot = 13;

        when(mIWifiStaIface.stopSendingKeepAlivePackets(anyInt())).thenReturn(mWifiStatusSuccess);

        assertTrue(mWifiVendorHal.startVendorHalSta());
        assertTrue(0 == mWifiVendorHal.stopSendingOffloadedPacket(slot));

        verify(mIWifiStaIface).stopSendingKeepAlivePackets(eq(slot));
    }

    /**
     * Test the setup, invocation, and removal of a RSSI event handler
     *
     */
    @Test
    public void testRssiMonitoring() throws Exception {
        when(mIWifiStaIface.startRssiMonitoring(anyInt(), anyInt(), anyInt()))
                .thenReturn(mWifiStatusSuccess);
        when(mIWifiStaIface.stopRssiMonitoring(anyInt()))
                .thenReturn(mWifiStatusSuccess);

        ArrayList<Byte> breach = new ArrayList<>(10);
        byte hi = -21;
        byte med = -42;
        byte lo = -84;
        Byte lower = -88;
        WifiNative.WifiRssiEventHandler handler;
        handler = ((cur) -> {
            breach.add(cur);
        });
        assertEquals(-1, mWifiVendorHal.startRssiMonitoring(hi, lo, handler)); // not started
        assertEquals(-1, mWifiVendorHal.stopRssiMonitoring()); // not started
        assertTrue(mWifiVendorHal.startVendorHalSta());
        assertEquals(0, mWifiVendorHal.startRssiMonitoring(hi, lo, handler));
        int theCmdId = mWifiVendorHal.sRssiMonCmdId;
        breach.clear();
        mIWifiStaIfaceEventCallback.onRssiThresholdBreached(theCmdId, new byte[6], lower);
        assertEquals(breach.get(0), lower);
        assertEquals(0, mWifiVendorHal.stopRssiMonitoring());
        assertEquals(0, mWifiVendorHal.startRssiMonitoring(hi, lo, handler));
        assertEquals(0, mWifiVendorHal.startRssiMonitoring(med, lo, handler)); // replacing works
        assertEquals(-1, mWifiVendorHal.startRssiMonitoring(hi, lo, null)); // null handler fails
        assertEquals(0, mWifiVendorHal.startRssiMonitoring(hi, lo, handler));
        assertEquals(-1, mWifiVendorHal.startRssiMonitoring(lo, hi, handler)); // empty range
    }

    /**
     * Test that getApfCapabilities is hooked up to the HAL correctly
     *
     * A call before the vendor HAL is started should return a non-null result with version 0
     *
     * A call after the HAL is started should return the mocked values.
     */
    @Test
    public void testApfCapabilities() throws Exception {
        int myVersion = 33;
        int myMaxSize = 1234;

        StaApfPacketFilterCapabilities capabilities = new StaApfPacketFilterCapabilities();
        capabilities.version = myVersion;
        capabilities.maxLength = myMaxSize;

        doAnswer(new AnswerWithArguments() {
            public void answer(IWifiStaIface.getApfPacketFilterCapabilitiesCallback cb)
                    throws RemoteException {
                cb.onValues(mWifiStatusSuccess, capabilities);
            }
        }).when(mIWifiStaIface).getApfPacketFilterCapabilities(any(
                IWifiStaIface.getApfPacketFilterCapabilitiesCallback.class));


        assertEquals(0, mWifiVendorHal.getApfCapabilities().apfVersionSupported);

        assertTrue(mWifiVendorHal.startVendorHalSta());

        ApfCapabilities actual = mWifiVendorHal.getApfCapabilities();

        assertEquals(myVersion, actual.apfVersionSupported);
        assertEquals(myMaxSize, actual.maximumApfProgramSize);
        assertEquals(android.system.OsConstants.ARPHRD_ETHER, actual.apfPacketFormat);
        assertNotEquals(0, actual.apfPacketFormat);
    }

    /**
     * Test that an APF program can be installed.
     */
    @Test
    public void testInstallApf() throws Exception {
        byte[] filter = new byte[] {19, 53, 10};

        ArrayList<Byte> expected = new ArrayList<>(3);
        for (byte b : filter) expected.add(b);

        when(mIWifiStaIface.installApfPacketFilter(anyInt(), any(ArrayList.class)))
                .thenReturn(mWifiStatusSuccess);

        assertTrue(mWifiVendorHal.startVendorHalSta());
        assertTrue(mWifiVendorHal.installPacketFilter(filter));

        verify(mIWifiStaIface).installApfPacketFilter(eq(0), eq(expected));
    }

    /**
     * Test that the country code is set in AP mode (when it should be).
     */
    @Test
    public void testSetCountryCodeHal() throws Exception {
        byte[] expected = new byte[]{(byte) 'C', (byte) 'A'};

        when(mIWifiApIface.setCountryCode(any()))
                .thenReturn(mWifiStatusSuccess);

        assertTrue(mWifiVendorHal.startVendorHalAp());

        assertFalse(mWifiVendorHal.setCountryCodeHal(null));
        assertFalse(mWifiVendorHal.setCountryCodeHal(""));
        assertFalse(mWifiVendorHal.setCountryCodeHal("A"));
        assertTrue(mWifiVendorHal.setCountryCodeHal("CA")); // Only one expected to succeed
        assertFalse(mWifiVendorHal.setCountryCodeHal("ZZZ"));

        verify(mIWifiApIface).setCountryCode(eq(expected));
    }

    /**
     * Test that RemoteException is caught and logged.
     */
    @Test
    public void testRemoteExceptionIsHandled() throws Exception {
        mWifiLog = spy(mWifiLog);
        mWifiVendorHal.mVerboseLog = mWifiLog;
        when(mIWifiApIface.setCountryCode(any()))
                .thenThrow(new RemoteException("oops"));
        assertTrue(mWifiVendorHal.startVendorHalAp());
        assertFalse(mWifiVendorHal.setCountryCodeHal("CA"));
        assertFalse(mWifiVendorHal.isHalStarted());
        verify(mWifiLog).err(any());
    }

    /**
     * Test that startLoggingToDebugRingBuffer is plumbed to chip
     *
     * A call before the vendor hal is started should just return false.
     * After starting in STA mode, the call should succeed, and pass ther right things down.
     */
    @Test
    public void testStartLoggingRingBuffer() throws Exception {
        when(mIWifiChip.startLoggingToDebugRingBuffer(
                any(String.class), anyInt(), anyInt(), anyInt()
        )).thenReturn(mWifiStatusSuccess);

        assertFalse(mWifiVendorHal.startLoggingRingBuffer(1, 0x42, 0, 0, "One"));
        assertTrue(mWifiVendorHal.startVendorHalSta());
        assertTrue(mWifiVendorHal.startLoggingRingBuffer(1, 0x42, 11, 3000, "One"));

        verify(mIWifiChip).startLoggingToDebugRingBuffer("One", 1, 11, 3000);
    }

    /**
     * Same test as testStartLoggingRingBuffer, but in AP mode rather than STA.
     */
    @Test
    public void testStartLoggingRingBufferOnAp() throws Exception {
        when(mIWifiChip.startLoggingToDebugRingBuffer(
                any(String.class), anyInt(), anyInt(), anyInt()
        )).thenReturn(mWifiStatusSuccess);

        assertFalse(mWifiVendorHal.startLoggingRingBuffer(1, 0x42, 0, 0, "One"));
        assertTrue(mWifiVendorHal.startVendorHalAp());
        assertTrue(mWifiVendorHal.startLoggingRingBuffer(1, 0x42, 11, 3000, "One"));

        verify(mIWifiChip).startLoggingToDebugRingBuffer("One", 1, 11, 3000);
    }

    /**
     * Test that getRingBufferStatus gets and translates its stuff correctly
     */
    @Test
    public void testRingBufferStatus() throws Exception {
        WifiDebugRingBufferStatus one = new WifiDebugRingBufferStatus();
        one.ringName = "One";
        one.flags = WifiDebugRingBufferFlags.HAS_BINARY_ENTRIES;
        one.ringId = 5607371;
        one.sizeInBytes = 54321;
        one.freeSizeInBytes = 42;
        one.verboseLevel = WifiDebugRingBufferVerboseLevel.VERBOSE;
        String oneExpect = "name: One flag: 1 ringBufferId: 5607371 ringBufferByteSize: 54321"
                + " verboseLevel: 2 writtenBytes: 0 readBytes: 0 writtenRecords: 0";

        WifiDebugRingBufferStatus two = new WifiDebugRingBufferStatus();
        two.ringName = "Two";
        two.flags = WifiDebugRingBufferFlags.HAS_ASCII_ENTRIES
                | WifiDebugRingBufferFlags.HAS_PER_PACKET_ENTRIES;
        two.ringId = 4512470;
        two.sizeInBytes = 300;
        two.freeSizeInBytes = 42;
        two.verboseLevel = WifiDebugRingBufferVerboseLevel.DEFAULT;

        ArrayList<WifiDebugRingBufferStatus> halBufferStatus = new ArrayList<>(2);
        halBufferStatus.add(one);
        halBufferStatus.add(two);

        WifiNative.RingBufferStatus[] actual;

        doAnswer(new AnswerWithArguments() {
            public void answer(IWifiChip.getDebugRingBuffersStatusCallback cb)
                    throws RemoteException {
                cb.onValues(mWifiStatusSuccess, halBufferStatus);
            }
        }).when(mIWifiChip).getDebugRingBuffersStatus(any(
                IWifiChip.getDebugRingBuffersStatusCallback.class));

        assertTrue(mWifiVendorHal.startVendorHalSta());
        actual = mWifiVendorHal.getRingBufferStatus();

        assertEquals(halBufferStatus.size(), actual.length);
        assertEquals(oneExpect, actual[0].toString());
        assertEquals(two.ringId, actual[1].ringBufferId);

    }

    /**
     * Test that getRingBufferData calls forceDumpToDebugRingBuffer
     *
     * Try once before hal start, and twice after (one success, one failure).
     */
    @Test
    public void testForceRingBufferDump() throws Exception {
        when(mIWifiChip.forceDumpToDebugRingBuffer(eq("Gunk"))).thenReturn(mWifiStatusSuccess);
        when(mIWifiChip.forceDumpToDebugRingBuffer(eq("Glop"))).thenReturn(mWifiStatusFailure);

        assertFalse(mWifiVendorHal.getRingBufferData("Gunk")); // hal not started

        assertTrue(mWifiVendorHal.startVendorHalSta());

        assertTrue(mWifiVendorHal.getRingBufferData("Gunk")); // mocked call succeeds
        assertFalse(mWifiVendorHal.getRingBufferData("Glop")); // mocked call fails

        verify(mIWifiChip).forceDumpToDebugRingBuffer("Gunk");
        verify(mIWifiChip).forceDumpToDebugRingBuffer("Glop");
    }

    /**
     * Tests the start of packet fate monitoring.
     *
     * Try once before hal start, and once after (one success, one failure).
     */
    @Test
    public void testStartPktFateMonitoring() throws Exception {
        when(mIWifiStaIface.startDebugPacketFateMonitoring()).thenReturn(mWifiStatusSuccess);

        assertFalse(mWifiVendorHal.startPktFateMonitoring());
        verify(mIWifiStaIface, never()).startDebugPacketFateMonitoring();

        assertTrue(mWifiVendorHal.startVendorHalSta());
        assertTrue(mWifiVendorHal.startPktFateMonitoring());
        verify(mIWifiStaIface).startDebugPacketFateMonitoring();
    }

    /**
     * Tests the retrieval of tx packet fates.
     *
     * Try once before hal start, and once after.
     */
    @Test
    public void testGetTxPktFates() throws Exception {
        byte[] frameContentBytes = new byte[30];
        new Random().nextBytes(frameContentBytes);
        WifiDebugTxPacketFateReport fateReport = new WifiDebugTxPacketFateReport();
        fateReport.fate = WifiDebugTxPacketFate.DRV_QUEUED;
        fateReport.frameInfo.driverTimestampUsec = new Random().nextLong();
        fateReport.frameInfo.frameType = WifiDebugPacketFateFrameType.ETHERNET_II;
        fateReport.frameInfo.frameContent.addAll(
                NativeUtil.byteArrayToArrayList(frameContentBytes));

        doAnswer(new AnswerWithArguments() {
            public void answer(IWifiStaIface.getDebugTxPacketFatesCallback cb) {
                cb.onValues(mWifiStatusSuccess,
                        new ArrayList<WifiDebugTxPacketFateReport>(Arrays.asList(fateReport)));
            }
        }).when(mIWifiStaIface)
                .getDebugTxPacketFates(any(IWifiStaIface.getDebugTxPacketFatesCallback.class));

        WifiNative.TxFateReport[] retrievedFates = new WifiNative.TxFateReport[1];
        assertFalse(mWifiVendorHal.getTxPktFates(retrievedFates));
        verify(mIWifiStaIface, never())
                .getDebugTxPacketFates(any(IWifiStaIface.getDebugTxPacketFatesCallback.class));

        assertTrue(mWifiVendorHal.startVendorHalSta());

        assertTrue(mWifiVendorHal.getTxPktFates(retrievedFates));
        verify(mIWifiStaIface)
                .getDebugTxPacketFates(any(IWifiStaIface.getDebugTxPacketFatesCallback.class));
        assertEquals(WifiLoggerHal.TX_PKT_FATE_DRV_QUEUED, retrievedFates[0].mFate);
        assertEquals(fateReport.frameInfo.driverTimestampUsec,
                retrievedFates[0].mDriverTimestampUSec);
        assertEquals(WifiLoggerHal.FRAME_TYPE_ETHERNET_II, retrievedFates[0].mFrameType);
        assertArrayEquals(frameContentBytes, retrievedFates[0].mFrameBytes);
    }

    /**
     * Tests the retrieval of tx packet fates when the number of fates retrieved exceeds the
     * input array.
     *
     * Try once before hal start, and once after.
     */
    @Test
    public void testGetTxPktFatesExceedsInputArrayLength() throws Exception {
        byte[] frameContentBytes = new byte[30];
        new Random().nextBytes(frameContentBytes);
        WifiDebugTxPacketFateReport fateReport = new WifiDebugTxPacketFateReport();
        fateReport.fate = WifiDebugTxPacketFate.FW_DROP_OTHER;
        fateReport.frameInfo.driverTimestampUsec = new Random().nextLong();
        fateReport.frameInfo.frameType = WifiDebugPacketFateFrameType.MGMT_80211;
        fateReport.frameInfo.frameContent.addAll(
                NativeUtil.byteArrayToArrayList(frameContentBytes));

        doAnswer(new AnswerWithArguments() {
            public void answer(IWifiStaIface.getDebugTxPacketFatesCallback cb) {
                cb.onValues(mWifiStatusSuccess,
                        new ArrayList<WifiDebugTxPacketFateReport>(Arrays.asList(
                                fateReport, fateReport)));
            }
        }).when(mIWifiStaIface)
                .getDebugTxPacketFates(any(IWifiStaIface.getDebugTxPacketFatesCallback.class));

        WifiNative.TxFateReport[] retrievedFates = new WifiNative.TxFateReport[1];
        assertFalse(mWifiVendorHal.getTxPktFates(retrievedFates));
        verify(mIWifiStaIface, never())
                .getDebugTxPacketFates(any(IWifiStaIface.getDebugTxPacketFatesCallback.class));

        assertTrue(mWifiVendorHal.startVendorHalSta());

        assertTrue(mWifiVendorHal.getTxPktFates(retrievedFates));
        verify(mIWifiStaIface)
                .getDebugTxPacketFates(any(IWifiStaIface.getDebugTxPacketFatesCallback.class));
        assertEquals(WifiLoggerHal.TX_PKT_FATE_FW_DROP_OTHER, retrievedFates[0].mFate);
        assertEquals(fateReport.frameInfo.driverTimestampUsec,
                retrievedFates[0].mDriverTimestampUSec);
        assertEquals(WifiLoggerHal.FRAME_TYPE_80211_MGMT, retrievedFates[0].mFrameType);
        assertArrayEquals(frameContentBytes, retrievedFates[0].mFrameBytes);
    }

    /**
     * Tests the retrieval of rx packet fates.
     *
     * Try once before hal start, and once after.
     */
    @Test
    public void testGetRxPktFates() throws Exception {
        byte[] frameContentBytes = new byte[30];
        new Random().nextBytes(frameContentBytes);
        WifiDebugRxPacketFateReport fateReport = new WifiDebugRxPacketFateReport();
        fateReport.fate = WifiDebugRxPacketFate.SUCCESS;
        fateReport.frameInfo.driverTimestampUsec = new Random().nextLong();
        fateReport.frameInfo.frameType = WifiDebugPacketFateFrameType.ETHERNET_II;
        fateReport.frameInfo.frameContent.addAll(
                NativeUtil.byteArrayToArrayList(frameContentBytes));

        doAnswer(new AnswerWithArguments() {
            public void answer(IWifiStaIface.getDebugRxPacketFatesCallback cb) {
                cb.onValues(mWifiStatusSuccess,
                        new ArrayList<WifiDebugRxPacketFateReport>(Arrays.asList(fateReport)));
            }
        }).when(mIWifiStaIface)
                .getDebugRxPacketFates(any(IWifiStaIface.getDebugRxPacketFatesCallback.class));

        WifiNative.RxFateReport[] retrievedFates = new WifiNative.RxFateReport[1];
        assertFalse(mWifiVendorHal.getRxPktFates(retrievedFates));
        verify(mIWifiStaIface, never())
                .getDebugRxPacketFates(any(IWifiStaIface.getDebugRxPacketFatesCallback.class));

        assertTrue(mWifiVendorHal.startVendorHalSta());

        assertTrue(mWifiVendorHal.getRxPktFates(retrievedFates));
        verify(mIWifiStaIface)
                .getDebugRxPacketFates(any(IWifiStaIface.getDebugRxPacketFatesCallback.class));
        assertEquals(WifiLoggerHal.RX_PKT_FATE_SUCCESS, retrievedFates[0].mFate);
        assertEquals(fateReport.frameInfo.driverTimestampUsec,
                retrievedFates[0].mDriverTimestampUSec);
        assertEquals(WifiLoggerHal.FRAME_TYPE_ETHERNET_II, retrievedFates[0].mFrameType);
        assertArrayEquals(frameContentBytes, retrievedFates[0].mFrameBytes);
    }

    /**
     * Tests the retrieval of rx packet fates when the number of fates retrieved exceeds the
     * input array.
     *
     * Try once before hal start, and once after.
     */
    @Test
    public void testGetRxPktFatesExceedsInputArrayLength() throws Exception {
        byte[] frameContentBytes = new byte[30];
        new Random().nextBytes(frameContentBytes);
        WifiDebugRxPacketFateReport fateReport = new WifiDebugRxPacketFateReport();
        fateReport.fate = WifiDebugRxPacketFate.FW_DROP_FILTER;
        fateReport.frameInfo.driverTimestampUsec = new Random().nextLong();
        fateReport.frameInfo.frameType = WifiDebugPacketFateFrameType.MGMT_80211;
        fateReport.frameInfo.frameContent.addAll(
                NativeUtil.byteArrayToArrayList(frameContentBytes));

        doAnswer(new AnswerWithArguments() {
            public void answer(IWifiStaIface.getDebugRxPacketFatesCallback cb) {
                cb.onValues(mWifiStatusSuccess,
                        new ArrayList<WifiDebugRxPacketFateReport>(Arrays.asList(
                                fateReport, fateReport)));
            }
        }).when(mIWifiStaIface)
                .getDebugRxPacketFates(any(IWifiStaIface.getDebugRxPacketFatesCallback.class));

        WifiNative.RxFateReport[] retrievedFates = new WifiNative.RxFateReport[1];
        assertFalse(mWifiVendorHal.getRxPktFates(retrievedFates));
        verify(mIWifiStaIface, never())
                .getDebugRxPacketFates(any(IWifiStaIface.getDebugRxPacketFatesCallback.class));

        assertTrue(mWifiVendorHal.startVendorHalSta());

        assertTrue(mWifiVendorHal.getRxPktFates(retrievedFates));
        verify(mIWifiStaIface)
                .getDebugRxPacketFates(any(IWifiStaIface.getDebugRxPacketFatesCallback.class));
        assertEquals(WifiLoggerHal.RX_PKT_FATE_FW_DROP_FILTER, retrievedFates[0].mFate);
        assertEquals(fateReport.frameInfo.driverTimestampUsec,
                retrievedFates[0].mDriverTimestampUSec);
        assertEquals(WifiLoggerHal.FRAME_TYPE_80211_MGMT, retrievedFates[0].mFrameType);
        assertArrayEquals(frameContentBytes, retrievedFates[0].mFrameBytes);
    }

    /**
     * Tests the failure to retrieve tx packet fates when the input array is empty.
     */
    @Test
    public void testGetTxPktFatesEmptyInputArray() throws Exception {
        assertTrue(mWifiVendorHal.startVendorHalSta());
        assertFalse(mWifiVendorHal.getTxPktFates(new WifiNative.TxFateReport[0]));
        verify(mIWifiStaIface, never())
                .getDebugTxPacketFates(any(IWifiStaIface.getDebugTxPacketFatesCallback.class));
    }

    /**
     * Tests the failure to retrieve rx packet fates when the input array is empty.
     */
    @Test
    public void testGetRxPktFatesEmptyInputArray() throws Exception {
        assertTrue(mWifiVendorHal.startVendorHalSta());
        assertFalse(mWifiVendorHal.getRxPktFates(new WifiNative.RxFateReport[0]));
        verify(mIWifiStaIface, never())
                .getDebugRxPacketFates(any(IWifiStaIface.getDebugRxPacketFatesCallback.class));
    }

    /**
     * Tests the nd offload enable/disable.
     */
    @Test
    public void testEnableDisableNdOffload() throws Exception {
        when(mIWifiStaIface.enableNdOffload(anyBoolean())).thenReturn(mWifiStatusSuccess);

        assertFalse(mWifiVendorHal.configureNeighborDiscoveryOffload(true));
        verify(mIWifiStaIface, never()).enableNdOffload(anyBoolean());

        assertTrue(mWifiVendorHal.startVendorHalSta());

        assertTrue(mWifiVendorHal.configureNeighborDiscoveryOffload(true));
        verify(mIWifiStaIface).enableNdOffload(eq(true));
        assertTrue(mWifiVendorHal.configureNeighborDiscoveryOffload(false));
        verify(mIWifiStaIface).enableNdOffload(eq(false));
    }

    /**
     * Tests the nd offload enable failure.
     */
    @Test
    public void testEnableNdOffloadFailure() throws Exception {
        when(mIWifiStaIface.enableNdOffload(eq(true))).thenReturn(mWifiStatusFailure);

        assertTrue(mWifiVendorHal.startVendorHalSta());

        assertFalse(mWifiVendorHal.configureNeighborDiscoveryOffload(true));
        verify(mIWifiStaIface).enableNdOffload(eq(true));
    }

    /**
     * Tests the retrieval of wlan wake reason stats.
     */
    @Test
    public void testGetWlanWakeReasonCount() throws Exception {
        WifiDebugHostWakeReasonStats stats = new WifiDebugHostWakeReasonStats();
        Random rand = new Random();
        stats.totalCmdEventWakeCnt = rand.nextInt();
        stats.totalDriverFwLocalWakeCnt = rand.nextInt();
        stats.totalRxPacketWakeCnt = rand.nextInt();
        stats.rxPktWakeDetails.rxUnicastCnt = rand.nextInt();
        stats.rxPktWakeDetails.rxMulticastCnt = rand.nextInt();
        stats.rxIcmpPkWakeDetails.icmpPkt = rand.nextInt();
        stats.rxIcmpPkWakeDetails.icmp6Pkt = rand.nextInt();
        stats.rxMulticastPkWakeDetails.ipv4RxMulticastAddrCnt = rand.nextInt();
        stats.rxMulticastPkWakeDetails.ipv6RxMulticastAddrCnt = rand.nextInt();

        doAnswer(new AnswerWithArguments() {
            public void answer(IWifiChip.getDebugHostWakeReasonStatsCallback cb) {
                cb.onValues(mWifiStatusSuccess, stats);
            }
        }).when(mIWifiChip).getDebugHostWakeReasonStats(
                any(IWifiChip.getDebugHostWakeReasonStatsCallback.class));

        assertNull(mWifiVendorHal.getWlanWakeReasonCount());
        verify(mIWifiChip, never())
                .getDebugHostWakeReasonStats(
                        any(IWifiChip.getDebugHostWakeReasonStatsCallback.class));

        assertTrue(mWifiVendorHal.startVendorHalSta());

        WifiWakeReasonAndCounts retrievedStats = mWifiVendorHal.getWlanWakeReasonCount();
        verify(mIWifiChip).getDebugHostWakeReasonStats(
                any(IWifiChip.getDebugHostWakeReasonStatsCallback.class));
        assertNotNull(retrievedStats);
        assertEquals(stats.totalCmdEventWakeCnt, retrievedStats.totalCmdEventWake);
        assertEquals(stats.totalDriverFwLocalWakeCnt, retrievedStats.totalDriverFwLocalWake);
        assertEquals(stats.totalRxPacketWakeCnt, retrievedStats.totalRxDataWake);
        assertEquals(stats.rxPktWakeDetails.rxUnicastCnt, retrievedStats.rxUnicast);
        assertEquals(stats.rxPktWakeDetails.rxMulticastCnt, retrievedStats.rxMulticast);
        assertEquals(stats.rxIcmpPkWakeDetails.icmpPkt, retrievedStats.icmp);
        assertEquals(stats.rxIcmpPkWakeDetails.icmp6Pkt, retrievedStats.icmp6);
        assertEquals(stats.rxMulticastPkWakeDetails.ipv4RxMulticastAddrCnt,
                retrievedStats.ipv4RxMulticast);
        assertEquals(stats.rxMulticastPkWakeDetails.ipv6RxMulticastAddrCnt,
                retrievedStats.ipv6Multicast);
    }

    /**
     * Tests the failure in retrieval of wlan wake reason stats.
     */
    @Test
    public void testGetWlanWakeReasonCountFailure() throws Exception {
        doAnswer(new AnswerWithArguments() {
            public void answer(IWifiChip.getDebugHostWakeReasonStatsCallback cb) {
                cb.onValues(mWifiStatusFailure, new WifiDebugHostWakeReasonStats());
            }
        }).when(mIWifiChip).getDebugHostWakeReasonStats(
                any(IWifiChip.getDebugHostWakeReasonStatsCallback.class));

        // This should work in both AP & STA mode.
        assertTrue(mWifiVendorHal.startVendorHalAp());

        assertNull(mWifiVendorHal.getWlanWakeReasonCount());
        verify(mIWifiChip).getDebugHostWakeReasonStats(
                any(IWifiChip.getDebugHostWakeReasonStatsCallback.class));
    }

    /**
     * Test that getFwMemoryDump is properly plumbed
     */
    @Test
    public void testGetFwMemoryDump() throws Exception {
        byte [] sample = NativeUtil.hexStringToByteArray("268c7a3fbfa4661c0bdd6a36");
        ArrayList<Byte> halBlob = NativeUtil.byteArrayToArrayList(sample);

        doAnswer(new AnswerWithArguments() {
            public void answer(IWifiChip.requestFirmwareDebugDumpCallback cb)
                    throws RemoteException {
                cb.onValues(mWifiStatusSuccess, halBlob);
            }
        }).when(mIWifiChip).requestFirmwareDebugDump(any(
                IWifiChip.requestFirmwareDebugDumpCallback.class));

        assertTrue(mWifiVendorHal.startVendorHalSta());
        assertArrayEquals(sample, mWifiVendorHal.getFwMemoryDump());
    }

    /**
     * Test that getDriverStateDump is properly plumbed
     *
     * Just for variety, use AP mode here.
     */
    @Test
    public void testGetDriverStateDump() throws Exception {
        byte [] sample = NativeUtil.hexStringToByteArray("e83ff543cf80083e6459d20f");
        ArrayList<Byte> halBlob = NativeUtil.byteArrayToArrayList(sample);

        doAnswer(new AnswerWithArguments() {
            public void answer(IWifiChip.requestDriverDebugDumpCallback cb)
                    throws RemoteException {
                cb.onValues(mWifiStatusSuccess, halBlob);
            }
        }).when(mIWifiChip).requestDriverDebugDump(any(
                IWifiChip.requestDriverDebugDumpCallback.class));

        assertTrue(mWifiVendorHal.startVendorHalAp());
        assertArrayEquals(sample, mWifiVendorHal.getDriverStateDump());
    }

    /**
     * Test that background scan failure is handled correctly.
     */
    @Test
    public void testBgScanFailureCallback() throws Exception {
        assertTrue(mWifiVendorHal.startVendorHalSta());
        assertNotNull(mIWifiStaIfaceEventCallback);

        WifiNative.ScanEventHandler eventHandler = mock(WifiNative.ScanEventHandler.class);
        startBgScan(eventHandler);

        mIWifiStaIfaceEventCallback.onBackgroundScanFailure(mWifiVendorHal.mScan.cmdId);
        verify(eventHandler).onScanStatus(WifiNative.WIFI_SCAN_FAILED);
    }

    /**
     * Test that background scan failure with wrong id is not reported.
     */
    @Test
    public void testBgScanFailureCallbackWithInvalidCmdId() throws Exception {
        assertTrue(mWifiVendorHal.startVendorHalSta());
        assertNotNull(mIWifiStaIfaceEventCallback);

        WifiNative.ScanEventHandler eventHandler = mock(WifiNative.ScanEventHandler.class);
        startBgScan(eventHandler);

        mIWifiStaIfaceEventCallback.onBackgroundScanFailure(mWifiVendorHal.mScan.cmdId + 1);
        verify(eventHandler, never()).onScanStatus(WifiNative.WIFI_SCAN_FAILED);
    }

    /**
     * Test that background scan full results are handled correctly.
     */
    @Test
    public void testBgScanFullScanResults() throws Exception {
        assertTrue(mWifiVendorHal.startVendorHalSta());
        assertNotNull(mIWifiStaIfaceEventCallback);

        WifiNative.ScanEventHandler eventHandler = mock(WifiNative.ScanEventHandler.class);
        startBgScan(eventHandler);

        Pair<StaScanResult, ScanResult> result = createHidlAndFrameworkBgScanResult();
        mIWifiStaIfaceEventCallback.onBackgroundFullScanResult(
                mWifiVendorHal.mScan.cmdId, 5, result.first);

        ArgumentCaptor<ScanResult> scanResultCaptor = ArgumentCaptor.forClass(ScanResult.class);
        verify(eventHandler).onFullScanResult(scanResultCaptor.capture(), eq(5));

        assertScanResultEqual(result.second, scanResultCaptor.getValue());
    }

    /**
     * Test that background scan results are handled correctly.
     */
    @Test
    public void testBgScanScanResults() throws Exception {
        assertTrue(mWifiVendorHal.startVendorHalSta());
        assertNotNull(mIWifiStaIfaceEventCallback);

        WifiNative.ScanEventHandler eventHandler = mock(WifiNative.ScanEventHandler.class);
        startBgScan(eventHandler);

        Pair<ArrayList<StaScanData>, ArrayList<WifiScanner.ScanData>> data =
                createHidlAndFrameworkBgScanDatas();
        mIWifiStaIfaceEventCallback.onBackgroundScanResults(
                mWifiVendorHal.mScan.cmdId, data.first);

        verify(eventHandler).onScanStatus(WifiNative.WIFI_SCAN_RESULTS_AVAILABLE);
        assertScanDatasEqual(
                data.second, Arrays.asList(mWifiVendorHal.mScan.latestScanResults));
    }

    /**
     * Test that starting a new background scan when one is active will stop the previous one.
     */
    @Test
    public void testBgScanReplacement() throws Exception {
        when(mIWifiStaIface.stopBackgroundScan(anyInt())).thenReturn(mWifiStatusSuccess);
        assertTrue(mWifiVendorHal.startVendorHalSta());
        assertNotNull(mIWifiStaIfaceEventCallback);
        WifiNative.ScanEventHandler eventHandler = mock(WifiNative.ScanEventHandler.class);
        startBgScan(eventHandler);
        int cmdId1 = mWifiVendorHal.mScan.cmdId;
        startBgScan(eventHandler);
        assertNotEquals(mWifiVendorHal.mScan.cmdId, cmdId1);
        verify(mIWifiStaIface, times(2)).startBackgroundScan(anyInt(), any());
        verify(mIWifiStaIface).stopBackgroundScan(cmdId1);
    }

    /**
     * Test stopping a background scan.
     */
    @Test
    public void testBgScanStop() throws Exception {
        when(mIWifiStaIface.stopBackgroundScan(anyInt())).thenReturn(mWifiStatusSuccess);
        assertTrue(mWifiVendorHal.startVendorHalSta());
        assertNotNull(mIWifiStaIfaceEventCallback);
        WifiNative.ScanEventHandler eventHandler = mock(WifiNative.ScanEventHandler.class);
        startBgScan(eventHandler);

        int cmdId = mWifiVendorHal.mScan.cmdId;

        mWifiVendorHal.stopBgScan();
        mWifiVendorHal.stopBgScan(); // second call should not do anything
        verify(mIWifiStaIface).stopBackgroundScan(cmdId); // Should be called just once
    }

    /**
     * Test pausing and restarting a background scan.
     */
    @Test
    public void testBgScanPauseAndRestart() throws Exception {
        when(mIWifiStaIface.stopBackgroundScan(anyInt())).thenReturn(mWifiStatusSuccess);
        assertTrue(mWifiVendorHal.startVendorHalSta());
        assertNotNull(mIWifiStaIfaceEventCallback);
        WifiNative.ScanEventHandler eventHandler = mock(WifiNative.ScanEventHandler.class);
        startBgScan(eventHandler);

        int cmdId = mWifiVendorHal.mScan.cmdId;

        mWifiVendorHal.pauseBgScan();
        mWifiVendorHal.restartBgScan();
        verify(mIWifiStaIface).stopBackgroundScan(cmdId); // Should be called just once
        verify(mIWifiStaIface, times(2)).startBackgroundScan(eq(cmdId), any());
    }

    /**
     * Test the handling of log handler set.
     */
    @Test
    public void testSetLogHandler() throws Exception {
        when(mIWifiChip.enableDebugErrorAlerts(anyBoolean())).thenReturn(mWifiStatusSuccess);

        WifiNative.WifiLoggerEventHandler eventHandler =
                mock(WifiNative.WifiLoggerEventHandler.class);

        assertFalse(mWifiVendorHal.setLoggingEventHandler(eventHandler));
        verify(mIWifiChip, never()).enableDebugErrorAlerts(anyBoolean());

        assertTrue(mWifiVendorHal.startVendorHalSta());

        assertTrue(mWifiVendorHal.setLoggingEventHandler(eventHandler));
        verify(mIWifiChip).enableDebugErrorAlerts(eq(true));
        reset(mIWifiChip);

        // Second call should fail.
        assertFalse(mWifiVendorHal.setLoggingEventHandler(eventHandler));
        verify(mIWifiChip, never()).enableDebugErrorAlerts(anyBoolean());
    }

    /**
     * Test the handling of log handler reset.
     */
    @Test
    public void testResetLogHandler() throws Exception {
        when(mIWifiChip.enableDebugErrorAlerts(anyBoolean())).thenReturn(mWifiStatusSuccess);
        when(mIWifiChip.stopLoggingToDebugRingBuffer()).thenReturn(mWifiStatusSuccess);

        assertFalse(mWifiVendorHal.resetLogHandler());
        verify(mIWifiChip, never()).enableDebugErrorAlerts(anyBoolean());
        verify(mIWifiChip, never()).stopLoggingToDebugRingBuffer();

        assertTrue(mWifiVendorHal.startVendorHalSta());

        // Not set, so this should fail.
        assertFalse(mWifiVendorHal.resetLogHandler());
        verify(mIWifiChip, never()).enableDebugErrorAlerts(anyBoolean());
        verify(mIWifiChip, never()).stopLoggingToDebugRingBuffer();

        // Now set and then reset.
        assertTrue(mWifiVendorHal.setLoggingEventHandler(
                mock(WifiNative.WifiLoggerEventHandler.class)));
        assertTrue(mWifiVendorHal.resetLogHandler());
        verify(mIWifiChip).enableDebugErrorAlerts(eq(false));
        verify(mIWifiChip).stopLoggingToDebugRingBuffer();
        reset(mIWifiChip);

        // Second reset should fail.
        assertFalse(mWifiVendorHal.resetLogHandler());
        verify(mIWifiChip, never()).enableDebugErrorAlerts(anyBoolean());
        verify(mIWifiChip, never()).stopLoggingToDebugRingBuffer();
    }

    /**
     * Test the handling of alert callback.
     */
    @Test
    public void testAlertCallback() throws Exception {
        when(mIWifiChip.enableDebugErrorAlerts(anyBoolean())).thenReturn(mWifiStatusSuccess);
        when(mIWifiChip.stopLoggingToDebugRingBuffer()).thenReturn(mWifiStatusSuccess);

        assertTrue(mWifiVendorHal.startVendorHalSta());
        assertNotNull(mIWifiChipEventCallback);

        int errorCode = 5;
        byte[] errorData = new byte[45];
        new Random().nextBytes(errorData);

        // Randomly raise the HIDL callback before we register for the log callback.
        // This should be safely ignored. (Not trigger NPE.)
        mIWifiChipEventCallback.onDebugErrorAlert(
                errorCode, NativeUtil.byteArrayToArrayList(errorData));
        mLooper.dispatchAll();

        WifiNative.WifiLoggerEventHandler eventHandler =
                mock(WifiNative.WifiLoggerEventHandler.class);
        assertTrue(mWifiVendorHal.setLoggingEventHandler(eventHandler));
        verify(mIWifiChip).enableDebugErrorAlerts(eq(true));

        // Now raise the HIDL callback, this should be properly handled.
        mIWifiChipEventCallback.onDebugErrorAlert(
                errorCode, NativeUtil.byteArrayToArrayList(errorData));
        mLooper.dispatchAll();
        verify(eventHandler).onWifiAlert(eq(errorCode), eq(errorData));

        // Now stop the logging and invoke the callback. This should be ignored.
        reset(eventHandler);
        assertTrue(mWifiVendorHal.resetLogHandler());
        mIWifiChipEventCallback.onDebugErrorAlert(
                errorCode, NativeUtil.byteArrayToArrayList(errorData));
        mLooper.dispatchAll();
        verify(eventHandler, never()).onWifiAlert(anyInt(), anyObject());
    }

    /**
     * Test the handling of ring buffer callback.
     */
    @Test
    public void testRingBufferDataCallback() throws Exception {
        when(mIWifiChip.enableDebugErrorAlerts(anyBoolean())).thenReturn(mWifiStatusSuccess);
        when(mIWifiChip.stopLoggingToDebugRingBuffer()).thenReturn(mWifiStatusSuccess);

        assertTrue(mWifiVendorHal.startVendorHalSta());
        assertNotNull(mIWifiChipEventCallback);

        byte[] errorData = new byte[45];
        new Random().nextBytes(errorData);

        // Randomly raise the HIDL callback before we register for the log callback.
        // This should be safely ignored. (Not trigger NPE.)
        mIWifiChipEventCallback.onDebugRingBufferDataAvailable(
                new WifiDebugRingBufferStatus(), NativeUtil.byteArrayToArrayList(errorData));
        mLooper.dispatchAll();

        WifiNative.WifiLoggerEventHandler eventHandler =
                mock(WifiNative.WifiLoggerEventHandler.class);
        assertTrue(mWifiVendorHal.setLoggingEventHandler(eventHandler));
        verify(mIWifiChip).enableDebugErrorAlerts(eq(true));

        // Now raise the HIDL callback, this should be properly handled.
        mIWifiChipEventCallback.onDebugRingBufferDataAvailable(
                new WifiDebugRingBufferStatus(), NativeUtil.byteArrayToArrayList(errorData));
        mLooper.dispatchAll();
        verify(eventHandler).onRingBufferData(
                any(WifiNative.RingBufferStatus.class), eq(errorData));

        // Now stop the logging and invoke the callback. This should be ignored.
        reset(eventHandler);
        assertTrue(mWifiVendorHal.resetLogHandler());
        mIWifiChipEventCallback.onDebugRingBufferDataAvailable(
                new WifiDebugRingBufferStatus(), NativeUtil.byteArrayToArrayList(errorData));
        mLooper.dispatchAll();
        verify(eventHandler, never()).onRingBufferData(anyObject(), anyObject());
    }

    /**
     * Test the handling of Vendor HAL death.
     */
    @Test
    public void testVendorHalDeath() {
        // Invoke the HAL device manager status callback with ready set to false to indicate the
        // death of the HAL.
        when(mHalDeviceManager.isReady()).thenReturn(false);
        mHalDeviceManagerStatusCallbacks.onStatusChanged();

        verify(mVendorHalDeathHandler).onDeath();
    }

    /**
     * Test the new selectTxPowerScenario HIDL method invocation. This should return failure if the
     * HAL service is exposing the 1.0 interface.
     */
    @Test
    public void testSelectTxPowerScenario() throws RemoteException {
        assertTrue(mWifiVendorHal.startVendorHal(true));
        // Should fail because we exposed the 1.0 IWifiChip.
        assertFalse(
                mWifiVendorHal.selectTxPowerScenario(WifiNative.TX_POWER_SCENARIO_VOICE_CALL));
        verify(mIWifiChipV11, never()).selectTxPowerScenario(anyInt());
        mWifiVendorHal.stopVendorHal();

        // Now expose the 1.1 IWifiChip.
        mWifiVendorHal = new WifiVendorHalSpyV1_1(mHalDeviceManager, mLooper.getLooper());
        when(mIWifiChipV11.selectTxPowerScenario(anyInt())).thenReturn(mWifiStatusSuccess);

        assertTrue(mWifiVendorHal.startVendorHal(true));
        assertTrue(
                mWifiVendorHal.selectTxPowerScenario(WifiNative.TX_POWER_SCENARIO_VOICE_CALL));
        verify(mIWifiChipV11).selectTxPowerScenario(
                eq(android.hardware.wifi.V1_1.IWifiChip.TxPowerScenario.VOICE_CALL));
        verify(mIWifiChipV11, never()).resetTxPowerScenario();
        mWifiVendorHal.stopVendorHal();
    }

    /**
     * Test the new resetTxPowerScenario HIDL method invocation. This should return failure if the
     * HAL service is exposing the 1.0 interface.
     */
    @Test
    public void testResetTxPowerScenario() throws RemoteException {
        assertTrue(mWifiVendorHal.startVendorHal(true));
        // Should fail because we exposed the 1.0 IWifiChip.
        assertFalse(mWifiVendorHal.selectTxPowerScenario(WifiNative.TX_POWER_SCENARIO_NORMAL));
        verify(mIWifiChipV11, never()).resetTxPowerScenario();
        mWifiVendorHal.stopVendorHal();

        // Now expose the 1.1 IWifiChip.
        mWifiVendorHal = new WifiVendorHalSpyV1_1(mHalDeviceManager, mLooper.getLooper());
        when(mIWifiChipV11.resetTxPowerScenario()).thenReturn(mWifiStatusSuccess);

        assertTrue(mWifiVendorHal.startVendorHal(true));
        assertTrue(mWifiVendorHal.selectTxPowerScenario(WifiNative.TX_POWER_SCENARIO_NORMAL));
        verify(mIWifiChipV11).resetTxPowerScenario();
        verify(mIWifiChipV11, never()).selectTxPowerScenario(anyInt());
        mWifiVendorHal.stopVendorHal();
    }

    /**
     * Test the new selectTxPowerScenario HIDL method invocation with a bad scenario index.
     */
    @Test
    public void testInvalidSelectTxPowerScenario() throws RemoteException {
        // Expose the 1.1 IWifiChip.
        mWifiVendorHal = new WifiVendorHalSpyV1_1(mHalDeviceManager, mLooper.getLooper());
        when(mIWifiChipV11.selectTxPowerScenario(anyInt())).thenReturn(mWifiStatusSuccess);

        assertTrue(mWifiVendorHal.startVendorHal(true));
        assertFalse(mWifiVendorHal.selectTxPowerScenario(-6));
        verify(mIWifiChipV11, never()).selectTxPowerScenario(anyInt());
        verify(mIWifiChipV11, never()).resetTxPowerScenario();
        mWifiVendorHal.stopVendorHal();
    }

    private void startBgScan(WifiNative.ScanEventHandler eventHandler) throws Exception {
        when(mIWifiStaIface.startBackgroundScan(
                anyInt(), any(StaBackgroundScanParameters.class))).thenReturn(mWifiStatusSuccess);
        WifiNative.ScanSettings settings = new WifiNative.ScanSettings();
        settings.num_buckets = 1;
        WifiNative.BucketSettings bucketSettings = new WifiNative.BucketSettings();
        bucketSettings.bucket = 0;
        bucketSettings.period_ms = 16000;
        bucketSettings.report_events = WifiScanner.REPORT_EVENT_AFTER_EACH_SCAN;
        settings.buckets = new WifiNative.BucketSettings[] {bucketSettings};
        assertTrue(mWifiVendorHal.startBgScan(settings, eventHandler));
    }

    // Create a pair of HIDL scan result and its corresponding framework scan result for
    // comparison.
    private Pair<StaScanResult, ScanResult> createHidlAndFrameworkBgScanResult() {
        StaScanResult staScanResult = new StaScanResult();
        Random random = new Random();
        byte[] ssid = new byte[8];
        random.nextBytes(ssid);
        staScanResult.ssid.addAll(NativeUtil.byteArrayToArrayList(ssid));
        random.nextBytes(staScanResult.bssid);
        staScanResult.frequency = 2432;
        staScanResult.rssi = -45;
        staScanResult.timeStampInUs = 5;
        WifiInformationElement ie1 = new WifiInformationElement();
        byte[] ie1_data = new byte[56];
        random.nextBytes(ie1_data);
        ie1.id = 1;
        ie1.data.addAll(NativeUtil.byteArrayToArrayList(ie1_data));
        staScanResult.informationElements.add(ie1);

        // Now create the corresponding Scan result structure.
        ScanResult scanResult = new ScanResult();
        scanResult.SSID = NativeUtil.encodeSsid(staScanResult.ssid);
        scanResult.BSSID = NativeUtil.macAddressFromByteArray(staScanResult.bssid);
        scanResult.wifiSsid = WifiSsid.createFromByteArray(ssid);
        scanResult.frequency = staScanResult.frequency;
        scanResult.level = staScanResult.rssi;
        scanResult.timestamp = staScanResult.timeStampInUs;
        scanResult.bytes = new byte[57];
        scanResult.bytes[0] = ie1.id;
        System.arraycopy(ie1_data, 0, scanResult.bytes, 1, ie1_data.length);

        return Pair.create(staScanResult, scanResult);
    }

    // Create a pair of HIDL scan datas and its corresponding framework scan datas for
    // comparison.
    private Pair<ArrayList<StaScanData>, ArrayList<WifiScanner.ScanData>>
            createHidlAndFrameworkBgScanDatas() {
        ArrayList<StaScanData> staScanDatas = new ArrayList<>();
        StaScanData staScanData = new StaScanData();

        Pair<StaScanResult, ScanResult> result = createHidlAndFrameworkBgScanResult();
        staScanData.results.add(result.first);
        staScanData.bucketsScanned = 5;
        staScanData.flags = StaScanDataFlagMask.INTERRUPTED;
        staScanDatas.add(staScanData);

        ArrayList<WifiScanner.ScanData> scanDatas = new ArrayList<>();
        ScanResult[] scanResults = new ScanResult[1];
        scanResults[0] = result.second;
        WifiScanner.ScanData scanData =
                new WifiScanner.ScanData(mWifiVendorHal.mScan.cmdId, 1,
                        staScanData.bucketsScanned, false, scanResults);
        scanDatas.add(scanData);
        return Pair.create(staScanDatas, scanDatas);
    }

    private void assertScanResultEqual(ScanResult expected, ScanResult actual) {
        assertEquals(expected.SSID, actual.SSID);
        assertEquals(expected.wifiSsid.getHexString(), actual.wifiSsid.getHexString());
        assertEquals(expected.BSSID, actual.BSSID);
        assertEquals(expected.frequency, actual.frequency);
        assertEquals(expected.level, actual.level);
        assertEquals(expected.timestamp, actual.timestamp);
        assertArrayEquals(expected.bytes, actual.bytes);
    }

    private void assertScanResultsEqual(ScanResult[] expected, ScanResult[] actual) {
        assertEquals(expected.length, actual.length);
        for (int i = 0; i < expected.length; i++) {
            assertScanResultEqual(expected[i], actual[i]);
        }
    }

    private void assertScanDataEqual(WifiScanner.ScanData expected, WifiScanner.ScanData actual) {
        assertEquals(expected.getId(), actual.getId());
        assertEquals(expected.getFlags(), actual.getFlags());
        assertEquals(expected.getBucketsScanned(), actual.getBucketsScanned());
        assertScanResultsEqual(expected.getResults(), actual.getResults());
    }

    private void assertScanDatasEqual(
            List<WifiScanner.ScanData> expected, List<WifiScanner.ScanData> actual) {
        assertEquals(expected.size(), actual.size());
        for (int i = 0; i < expected.size(); i++) {
            assertScanDataEqual(expected.get(i), actual.get(i));
        }
    }
}
