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

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.Matchers.argThat;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import android.net.wifi.IApInterface;
import android.net.wifi.IClientInterface;
import android.net.wifi.IPnoScanEvent;
import android.net.wifi.IScanEvent;
import android.net.wifi.IWifiScannerImpl;
import android.net.wifi.IWificond;
import android.net.wifi.ScanResult;
import android.net.wifi.WifiEnterpriseConfig;
import android.net.wifi.WifiScanner;
import android.test.suitebuilder.annotation.SmallTest;

import com.android.server.wifi.util.NativeUtil;
import com.android.server.wifi.wificond.ChannelSettings;
import com.android.server.wifi.wificond.HiddenNetwork;
import com.android.server.wifi.wificond.NativeScanResult;
import com.android.server.wifi.wificond.PnoSettings;
import com.android.server.wifi.wificond.SingleScanSettings;

import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.ArgumentMatcher;

import java.io.ByteArrayOutputStream;
import java.util.ArrayList;
import java.util.BitSet;
import java.util.HashSet;
import java.util.Set;

/**
 * Unit tests for {@link com.android.server.wifi.WificondControl}.
 */
@SmallTest
public class WificondControlTest {
    private WifiInjector mWifiInjector;
    private WifiMonitor mWifiMonitor;
    private WifiMetrics mWifiMetrics;
    private CarrierNetworkConfig mCarrierNetworkConfig;
    private WificondControl mWificondControl;
    private static final String TEST_INTERFACE_NAME = "test_wlan_if";
    private static final byte[] TEST_SSID =
            new byte[] {'G', 'o', 'o', 'g', 'l', 'e', 'G', 'u', 'e', 's', 't'};
    private static final byte[] TEST_BSSID =
            new byte[] {(byte) 0x12, (byte) 0xef, (byte) 0xa1,
                        (byte) 0x2c, (byte) 0x97, (byte) 0x8b};
    // This the IE buffer which is consistent with TEST_SSID.
    private static final byte[] TEST_INFO_ELEMENT_SSID =
            new byte[] {
                    // Element ID for SSID.
                    (byte) 0x00,
                    // Length of the SSID: 0x0b or 11.
                    (byte) 0x0b,
                    // This is string "GoogleGuest"
                    'G', 'o', 'o', 'g', 'l', 'e', 'G', 'u', 'e', 's', 't'};
    // RSN IE data indicating EAP key management.
    private static final byte[] TEST_INFO_ELEMENT_RSN =
            new byte[] {
                    // Element ID for RSN.
                    (byte) 0x30,
                    // Length of the element data.
                    (byte) 0x18,
                    (byte) 0x01, (byte) 0x00, (byte) 0x00, (byte) 0x0F, (byte) 0xAC, (byte) 0x02,
                    (byte) 0x02, (byte) 0x00, (byte) 0x00, (byte) 0x0F, (byte) 0xAC, (byte) 0x04,
                    (byte) 0x00, (byte) 0x0F, (byte) 0xAC, (byte) 0x02, (byte) 0x01, (byte) 0x00,
                    (byte) 0x00, (byte) 0x0F, (byte) 0xAC, (byte) 0x01, (byte) 0x00, (byte) 0x00 };

    private static final int TEST_FREQUENCY = 2456;
    private static final int TEST_SIGNAL_MBM = -4500;
    private static final long TEST_TSF = 34455441;
    private static final BitSet TEST_CAPABILITY = new BitSet(16) {{ set(2); set(5); }};
    private static final boolean TEST_ASSOCIATED = true;
    private static final NativeScanResult MOCK_NATIVE_SCAN_RESULT =
            new NativeScanResult() {{
                ssid = TEST_SSID;
                bssid = TEST_BSSID;
                infoElement = TEST_INFO_ELEMENT_SSID;
                frequency = TEST_FREQUENCY;
                signalMbm = TEST_SIGNAL_MBM;
                capability = TEST_CAPABILITY;
                associated = TEST_ASSOCIATED;
            }};

    private static final Set<Integer> SCAN_FREQ_SET =
            new HashSet<Integer>() {{
                add(2410);
                add(2450);
                add(5050);
                add(5200);
            }};
    private static final String TEST_QUOTED_SSID_1 = "\"testSsid1\"";
    private static final String TEST_QUOTED_SSID_2 = "\"testSsid2\"";

    private static final Set<String> SCAN_HIDDEN_NETWORK_SSID_SET =
            new HashSet<String>() {{
                add(TEST_QUOTED_SSID_1);
                add(TEST_QUOTED_SSID_2);
            }};


    private static final WifiNative.PnoSettings TEST_PNO_SETTINGS =
            new WifiNative.PnoSettings() {{
                isConnected = false;
                periodInMs = 6000;
                networkList = new WifiNative.PnoNetwork[2];
                networkList[0] = new WifiNative.PnoNetwork();
                networkList[1] = new WifiNative.PnoNetwork();
                networkList[0].ssid = TEST_QUOTED_SSID_1;
                networkList[0].flags = WifiScanner.PnoSettings.PnoNetwork.FLAG_DIRECTED_SCAN;
                networkList[1].ssid = TEST_QUOTED_SSID_2;
                networkList[1].flags = 0;
            }};

    @Before
    public void setUp() throws Exception {
        mWifiInjector = mock(WifiInjector.class);
        mWifiMonitor = mock(WifiMonitor.class);
        mWifiMetrics = mock(WifiMetrics.class);
        when(mWifiInjector.getWifiMetrics()).thenReturn(mWifiMetrics);
        mCarrierNetworkConfig = mock(CarrierNetworkConfig.class);
        mWificondControl = new WificondControl(mWifiInjector, mWifiMonitor, mCarrierNetworkConfig);
    }

    /**
     * Verifies that setupDriverForClientMode() calls Wificond.
     */
    @Test
    public void testSetupDriverForClientMode() throws Exception {
        IWificond wificond = mock(IWificond.class);
        IClientInterface clientInterface = getMockClientInterface();

        when(mWifiInjector.makeWificond()).thenReturn(wificond);
        when(wificond.createClientInterface()).thenReturn(clientInterface);

        IClientInterface returnedClientInterface = mWificondControl.setupDriverForClientMode();
        assertEquals(clientInterface, returnedClientInterface);
        verify(wificond).createClientInterface();
    }

    /**
     * Verifies that setupDriverForClientMode() calls subscribeScanEvents().
     */
    @Test
    public void testSetupDriverForClientModeCallsScanEventSubscripiton() throws Exception {
        IWifiScannerImpl scanner = setupClientInterfaceAndCreateMockWificondScanner();

        verify(scanner).subscribeScanEvents(any(IScanEvent.class));
    }

    /**
     * Verifies that setupDriverForClientMode() returns null when wificond is not started.
     */
    @Test
    public void testSetupDriverForClientModeErrorWhenWificondIsNotStarted() throws Exception {
        when(mWifiInjector.makeWificond()).thenReturn(null);

        IClientInterface returnedClientInterface = mWificondControl.setupDriverForClientMode();
        assertEquals(null, returnedClientInterface);
    }

    /**
     * Verifies that setupDriverForClientMode() returns null when wificond failed to setup client
     * interface.
     */
    @Test
    public void testSetupDriverForClientModeErrorWhenWificondFailedToSetupInterface()
            throws Exception {
        IWificond wificond = mock(IWificond.class);

        when(mWifiInjector.makeWificond()).thenReturn(wificond);
        when(wificond.createClientInterface()).thenReturn(null);

        IClientInterface returnedClientInterface = mWificondControl.setupDriverForClientMode();
        assertEquals(null, returnedClientInterface);
    }

    /**
     * Verifies that setupDriverForSoftApMode() calls wificond.
     */
    @Test
    public void testSetupDriverForSoftApMode() throws Exception {
        IWificond wificond = mock(IWificond.class);
        IApInterface apInterface = mock(IApInterface.class);

        when(mWifiInjector.makeWificond()).thenReturn(wificond);
        when(wificond.createApInterface()).thenReturn(apInterface);

        IApInterface returnedApInterface = mWificondControl.setupDriverForSoftApMode();
        assertEquals(apInterface, returnedApInterface);
        verify(wificond).createApInterface();
    }

    /**
     * Verifies that setupDriverForSoftAp() returns null when wificond is not started.
     */
    @Test
    public void testSetupDriverForSoftApModeErrorWhenWificondIsNotStarted() throws Exception {
        when(mWifiInjector.makeWificond()).thenReturn(null);

        IApInterface returnedApInterface = mWificondControl.setupDriverForSoftApMode();

        assertEquals(null, returnedApInterface);
    }

    /**
     * Verifies that setupDriverForSoftApMode() returns null when wificond failed to setup
     * AP interface.
     */
    @Test
    public void testSetupDriverForSoftApModeErrorWhenWificondFailedToSetupInterface()
            throws Exception {
        IWificond wificond = mock(IWificond.class);

        when(mWifiInjector.makeWificond()).thenReturn(wificond);
        when(wificond.createApInterface()).thenReturn(null);

        IApInterface returnedApInterface = mWificondControl.setupDriverForSoftApMode();
        assertEquals(null, returnedApInterface);
    }

    /**
     * Verifies that enableSupplicant() calls wificond.
     */
    @Test
    public void testEnableSupplicant() throws Exception {
        IWificond wificond = mock(IWificond.class);
        IClientInterface clientInterface = getMockClientInterface();

        when(mWifiInjector.makeWificond()).thenReturn(wificond);
        when(wificond.createClientInterface()).thenReturn(clientInterface);
        when(clientInterface.enableSupplicant()).thenReturn(true);

        mWificondControl.setupDriverForClientMode();
        assertTrue(mWificondControl.enableSupplicant());
        verify(clientInterface).enableSupplicant();
    }

    /**
     * Verifies that enableSupplicant() returns false when there is no configured
     * client interface.
     */
    @Test
    public void testEnableSupplicantErrorWhenNoClientInterfaceConfigured() throws Exception {
        IWificond wificond = mock(IWificond.class);
        IClientInterface clientInterface = getMockClientInterface();

        when(mWifiInjector.makeWificond()).thenReturn(wificond);
        when(wificond.createClientInterface()).thenReturn(clientInterface);

        // Configure client interface.
        IClientInterface returnedClientInterface = mWificondControl.setupDriverForClientMode();
        assertEquals(clientInterface, returnedClientInterface);

        // Tear down interfaces.
        assertTrue(mWificondControl.tearDownInterfaces());

        // Enabling supplicant should fail.
        assertFalse(mWificondControl.enableSupplicant());
    }

    /**
     * Verifies that disableSupplicant() calls wificond.
     */
    @Test
    public void testDisableSupplicant() throws Exception {
        IWificond wificond = mock(IWificond.class);
        IClientInterface clientInterface = getMockClientInterface();

        when(mWifiInjector.makeWificond()).thenReturn(wificond);
        when(wificond.createClientInterface()).thenReturn(clientInterface);
        when(clientInterface.disableSupplicant()).thenReturn(true);

        mWificondControl.setupDriverForClientMode();
        assertTrue(mWificondControl.disableSupplicant());
        verify(clientInterface).disableSupplicant();
    }

    /**
     * Verifies that disableSupplicant() returns false when there is no configured
     * client interface.
     */
    @Test
    public void testDisableSupplicantErrorWhenNoClientInterfaceConfigured() throws Exception {
        IWificond wificond = mock(IWificond.class);
        IClientInterface clientInterface = getMockClientInterface();

        when(mWifiInjector.makeWificond()).thenReturn(wificond);
        when(wificond.createClientInterface()).thenReturn(clientInterface);

        // Configure client interface.
        IClientInterface returnedClientInterface = mWificondControl.setupDriverForClientMode();
        assertEquals(clientInterface, returnedClientInterface);

        // Tear down interfaces.
        assertTrue(mWificondControl.tearDownInterfaces());

        // Disabling supplicant should fail.
        assertFalse(mWificondControl.disableSupplicant());
    }

    /**
     * Verifies that tearDownInterfaces() calls wificond.
     */
    @Test
    public void testTearDownInterfaces() throws Exception {
        IWificond wificond = mock(IWificond.class);

        when(mWifiInjector.makeWificond()).thenReturn(wificond);
        assertTrue(mWificondControl.tearDownInterfaces());

        verify(wificond).tearDownInterfaces();
    }

    /**
     * Verifies that tearDownInterfaces() calls unsubscribeScanEvents() when there was
     * a configured client interface.
     */
    @Test
    public void testTearDownInterfacesRemovesScanEventSubscription() throws Exception {
        IWifiScannerImpl scanner = setupClientInterfaceAndCreateMockWificondScanner();

        assertTrue(mWificondControl.tearDownInterfaces());

        verify(scanner).unsubscribeScanEvents();
    }


    /**
     * Verifies that tearDownInterfaces() returns false when wificond is not started.
     */
    @Test
    public void testTearDownInterfacesErrorWhenWificondIsNotStarterd() throws Exception {
        when(mWifiInjector.makeWificond()).thenReturn(null);

        assertFalse(mWificondControl.tearDownInterfaces());
    }

    /**
     * Verifies that signalPoll() calls wificond.
     */
    @Test
    public void testSignalPoll() throws Exception {
        IWificond wificond = mock(IWificond.class);
        IClientInterface clientInterface = getMockClientInterface();

        when(mWifiInjector.makeWificond()).thenReturn(wificond);
        when(wificond.createClientInterface()).thenReturn(clientInterface);

        mWificondControl.setupDriverForClientMode();
        mWificondControl.signalPoll();
        verify(clientInterface).signalPoll();
    }

    /**
     * Verifies that signalPoll() returns null when there is no configured client interface.
     */
    @Test
    public void testSignalPollErrorWhenNoClientInterfaceConfigured() throws Exception {
        IWificond wificond = mock(IWificond.class);
        IClientInterface clientInterface = getMockClientInterface();

        when(mWifiInjector.makeWificond()).thenReturn(wificond);
        when(wificond.createClientInterface()).thenReturn(clientInterface);

        // Configure client interface.
        IClientInterface returnedClientInterface = mWificondControl.setupDriverForClientMode();
        assertEquals(clientInterface, returnedClientInterface);

        // Tear down interfaces.
        assertTrue(mWificondControl.tearDownInterfaces());

        // Signal poll should fail.
        assertEquals(null, mWificondControl.signalPoll());
    }

    /**
     * Verifies that getTxPacketCounters() calls wificond.
     */
    @Test
    public void testGetTxPacketCounters() throws Exception {
        IWificond wificond = mock(IWificond.class);
        IClientInterface clientInterface = getMockClientInterface();

        when(mWifiInjector.makeWificond()).thenReturn(wificond);
        when(wificond.createClientInterface()).thenReturn(clientInterface);

        mWificondControl.setupDriverForClientMode();
        mWificondControl.getTxPacketCounters();
        verify(clientInterface).getPacketCounters();
    }

    /**
     * Verifies that getTxPacketCounters() returns null when there is no configured client
     * interface.
     */
    @Test
    public void testGetTxPacketCountersErrorWhenNoClientInterfaceConfigured() throws Exception {
        IWificond wificond = mock(IWificond.class);
        IClientInterface clientInterface = getMockClientInterface();

        when(mWifiInjector.makeWificond()).thenReturn(wificond);
        when(wificond.createClientInterface()).thenReturn(clientInterface);

        // Configure client interface.
        IClientInterface returnedClientInterface = mWificondControl.setupDriverForClientMode();
        assertEquals(clientInterface, returnedClientInterface);

        // Tear down interfaces.
        assertTrue(mWificondControl.tearDownInterfaces());

        // Signal poll should fail.
        assertEquals(null, mWificondControl.getTxPacketCounters());
    }

    /**
     * Verifies that getScanResults() returns null when there is no configured client
     * interface.
     */
    @Test
    public void testGetScanResultsErrorWhenNoClientInterfaceConfigured() throws Exception {
        IWificond wificond = mock(IWificond.class);
        IClientInterface clientInterface = getMockClientInterface();

        when(mWifiInjector.makeWificond()).thenReturn(wificond);
        when(wificond.createClientInterface()).thenReturn(clientInterface);

        // Configure client interface.
        IClientInterface returnedClientInterface = mWificondControl.setupDriverForClientMode();
        assertEquals(clientInterface, returnedClientInterface);

        // Tear down interfaces.
        assertTrue(mWificondControl.tearDownInterfaces());

        // getScanResults should fail.
        assertEquals(0,
                mWificondControl.getScanResults(WificondControl.SCAN_TYPE_SINGLE_SCAN).size());
    }

    /**
     * Verifies that getScanResults() can parse NativeScanResult from wificond correctly,
     */
    @Test
    public void testGetScanResults() throws Exception {
        IWifiScannerImpl scanner = setupClientInterfaceAndCreateMockWificondScanner();
        assertNotNull(scanner);

        // Mock the returned array of NativeScanResult.
        NativeScanResult[] mockScanResults = {MOCK_NATIVE_SCAN_RESULT};
        when(scanner.getScanResults()).thenReturn(mockScanResults);

        ArrayList<ScanDetail> returnedScanResults = mWificondControl.getScanResults(
                WificondControl.SCAN_TYPE_SINGLE_SCAN);
        // The test IEs {@link #TEST_INFO_ELEMENT} doesn't contained RSN IE, which means non-EAP
        // AP. So verify carrier network is not checked, since EAP is currently required for a
        // carrier network.
        verify(mCarrierNetworkConfig, never()).isCarrierNetwork(anyString());
        assertEquals(mockScanResults.length, returnedScanResults.size());
        // Since NativeScanResult is organized differently from ScanResult, this only checks
        // a few fields.
        for (int i = 0; i < mockScanResults.length; i++) {
            assertArrayEquals(mockScanResults[i].ssid,
                              returnedScanResults.get(i).getScanResult().SSID.getBytes());
            assertEquals(mockScanResults[i].frequency,
                         returnedScanResults.get(i).getScanResult().frequency);
            assertEquals(mockScanResults[i].tsf,
                         returnedScanResults.get(i).getScanResult().timestamp);
        }
    }

    /**
     * Verifies that scan result's carrier network info {@link ScanResult#isCarrierAp} and
     * {@link ScanResult#getCarrierApEapType} is set appropriated based on the carrier network
     * config.
     *
     * @throws Exception
     */
    @Test
    public void testGetScanResultsForCarrierAp() throws Exception {
        IWifiScannerImpl scanner = setupClientInterfaceAndCreateMockWificondScanner();
        assertNotNull(scanner);

        // Include RSN IE to indicate EAP key management.
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        out.write(TEST_INFO_ELEMENT_SSID);
        out.write(TEST_INFO_ELEMENT_RSN);
        NativeScanResult nativeScanResult = new NativeScanResult(MOCK_NATIVE_SCAN_RESULT);
        nativeScanResult.infoElement = out.toByteArray();
        when(scanner.getScanResults()).thenReturn(new NativeScanResult[] {nativeScanResult});

        // AP associated with a carrier network.
        int eapType = WifiEnterpriseConfig.Eap.SIM;
        String carrierName = "Test Carrier";
        when(mCarrierNetworkConfig.isCarrierNetwork(new String(nativeScanResult.ssid)))
                .thenReturn(true);
        when(mCarrierNetworkConfig.getNetworkEapType(new String(nativeScanResult.ssid)))
                .thenReturn(eapType);
        when(mCarrierNetworkConfig.getCarrierName(new String(nativeScanResult.ssid)))
                .thenReturn(carrierName);
        ArrayList<ScanDetail> returnedScanResults = mWificondControl.getScanResults(
                WificondControl.SCAN_TYPE_SINGLE_SCAN);
        assertEquals(1, returnedScanResults.size());
        // Verify returned scan result.
        ScanResult scanResult = returnedScanResults.get(0).getScanResult();
        assertArrayEquals(nativeScanResult.ssid, scanResult.SSID.getBytes());
        assertTrue(scanResult.isCarrierAp);
        assertEquals(eapType, scanResult.carrierApEapType);
        assertEquals(carrierName, scanResult.carrierName);
        reset(mCarrierNetworkConfig);

        // AP not associated with a carrier network.
        when(mCarrierNetworkConfig.isCarrierNetwork(new String(nativeScanResult.ssid)))
                .thenReturn(false);
        returnedScanResults = mWificondControl.getScanResults(
                WificondControl.SCAN_TYPE_SINGLE_SCAN);
        assertEquals(1, returnedScanResults.size());
        // Verify returned scan result.
        scanResult = returnedScanResults.get(0).getScanResult();
        assertArrayEquals(nativeScanResult.ssid, scanResult.SSID.getBytes());
        assertFalse(scanResult.isCarrierAp);
        assertEquals(ScanResult.UNSPECIFIED, scanResult.carrierApEapType);
        assertEquals(null, scanResult.carrierName);
    }

    /**
     * Verifies that Scan() can convert input parameters to SingleScanSettings correctly.
     */
    @Test
    public void testScan() throws Exception {
        IWifiScannerImpl scanner = setupClientInterfaceAndCreateMockWificondScanner();

        when(scanner.scan(any(SingleScanSettings.class))).thenReturn(true);

        assertTrue(mWificondControl.scan(SCAN_FREQ_SET, SCAN_HIDDEN_NETWORK_SSID_SET));
        verify(scanner).scan(argThat(new ScanMatcher(
                SCAN_FREQ_SET, SCAN_HIDDEN_NETWORK_SSID_SET)));
    }

    /**
     * Verifies that Scan() can handle null input parameters correctly.
     */
    @Test
    public void testScanNullParameters() throws Exception {
        IWifiScannerImpl scanner = setupClientInterfaceAndCreateMockWificondScanner();

        when(scanner.scan(any(SingleScanSettings.class))).thenReturn(true);

        assertTrue(mWificondControl.scan(null, null));
        verify(scanner).scan(argThat(new ScanMatcher(null, null)));
    }

    /**
     * Verifies that Scan() can handle wificond scan failure.
     */
    @Test
    public void testScanFailure() throws Exception {
        IWifiScannerImpl scanner = setupClientInterfaceAndCreateMockWificondScanner();

        when(scanner.scan(any(SingleScanSettings.class))).thenReturn(false);
        assertFalse(mWificondControl.scan(SCAN_FREQ_SET, SCAN_HIDDEN_NETWORK_SSID_SET));
        verify(scanner).scan(any(SingleScanSettings.class));
    }

    /**
     * Verifies that startPnoScan() can convert input parameters to PnoSettings correctly.
     */
    @Test
    public void testStartPnoScan() throws Exception {
        IWifiScannerImpl scanner = setupClientInterfaceAndCreateMockWificondScanner();

        when(scanner.startPnoScan(any(PnoSettings.class))).thenReturn(true);
        assertTrue(mWificondControl.startPnoScan(TEST_PNO_SETTINGS));
        verify(scanner).startPnoScan(argThat(new PnoScanMatcher(TEST_PNO_SETTINGS)));
    }

    /**
     * Verifies that stopPnoScan() calls underlying wificond.
     */
    @Test
    public void testStopPnoScan() throws Exception {
        IWifiScannerImpl scanner = setupClientInterfaceAndCreateMockWificondScanner();

        when(scanner.stopPnoScan()).thenReturn(true);
        assertTrue(mWificondControl.stopPnoScan());
        verify(scanner).stopPnoScan();
    }

    /**
     * Verifies that stopPnoScan() can handle wificond failure.
     */
    @Test
    public void testStopPnoScanFailure() throws Exception {
        IWifiScannerImpl scanner = setupClientInterfaceAndCreateMockWificondScanner();

        when(scanner.stopPnoScan()).thenReturn(false);
        assertFalse(mWificondControl.stopPnoScan());
        verify(scanner).stopPnoScan();
    }

    /**
     * Verifies that WificondControl can invoke WifiMonitor broadcast methods upon scan
     * reuslt event.
     */
    @Test
    public void testScanResultEvent() throws Exception {
        IWifiScannerImpl scanner = setupClientInterfaceAndCreateMockWificondScanner();

        ArgumentCaptor<IScanEvent> messageCaptor = ArgumentCaptor.forClass(IScanEvent.class);
        verify(scanner).subscribeScanEvents(messageCaptor.capture());
        IScanEvent scanEvent = messageCaptor.getValue();
        assertNotNull(scanEvent);
        scanEvent.OnScanResultReady();

        verify(mWifiMonitor).broadcastScanResultEvent(any(String.class));
    }

    /**
     * Verifies that WificondControl can invoke WifiMonitor broadcast methods upon scan
     * failed event.
     */
    @Test
    public void testScanFailedEvent() throws Exception {
        IWifiScannerImpl scanner = setupClientInterfaceAndCreateMockWificondScanner();

        ArgumentCaptor<IScanEvent> messageCaptor = ArgumentCaptor.forClass(IScanEvent.class);
        verify(scanner).subscribeScanEvents(messageCaptor.capture());
        IScanEvent scanEvent = messageCaptor.getValue();
        assertNotNull(scanEvent);
        scanEvent.OnScanFailed();

        verify(mWifiMonitor).broadcastScanFailedEvent(any(String.class));
    }

    /**
     * Verifies that WificondControl can invoke WifiMonitor broadcast methods upon pno scan
     * result event.
     */
    @Test
    public void testPnoScanResultEvent() throws Exception {
        IWifiScannerImpl scanner = setupClientInterfaceAndCreateMockWificondScanner();

        ArgumentCaptor<IPnoScanEvent> messageCaptor = ArgumentCaptor.forClass(IPnoScanEvent.class);
        verify(scanner).subscribePnoScanEvents(messageCaptor.capture());
        IPnoScanEvent pnoScanEvent = messageCaptor.getValue();
        assertNotNull(pnoScanEvent);
        pnoScanEvent.OnPnoNetworkFound();
        verify(mWifiMonitor).broadcastPnoScanResultEvent(any(String.class));
    }

    /**
     * Verifies that WificondControl can invoke WifiMetrics pno scan count methods upon pno event.
     */
    @Test
    public void testPnoScanEventsForMetrics() throws Exception {
        IWifiScannerImpl scanner = setupClientInterfaceAndCreateMockWificondScanner();

        ArgumentCaptor<IPnoScanEvent> messageCaptor = ArgumentCaptor.forClass(IPnoScanEvent.class);
        verify(scanner).subscribePnoScanEvents(messageCaptor.capture());
        IPnoScanEvent pnoScanEvent = messageCaptor.getValue();
        assertNotNull(pnoScanEvent);

        pnoScanEvent.OnPnoNetworkFound();
        verify(mWifiMetrics).incrementPnoFoundNetworkEventCount();

        pnoScanEvent.OnPnoScanFailed();
        verify(mWifiMetrics).incrementPnoScanFailedCount();

        pnoScanEvent.OnPnoScanOverOffloadStarted();
        verify(mWifiMetrics).incrementPnoScanStartedOverOffloadCount();

        pnoScanEvent.OnPnoScanOverOffloadFailed(0);
        verify(mWifiMetrics).incrementPnoScanFailedOverOffloadCount();
    }

    /**
     * Verifies that startPnoScan() can invoke WifiMetrics pno scan count methods correctly.
     */
    @Test
    public void testStartPnoScanForMetrics() throws Exception {
        IWifiScannerImpl scanner = setupClientInterfaceAndCreateMockWificondScanner();

        when(scanner.startPnoScan(any(PnoSettings.class))).thenReturn(false);
        assertFalse(mWificondControl.startPnoScan(TEST_PNO_SETTINGS));
        verify(mWifiMetrics).incrementPnoScanStartAttempCount();
        verify(mWifiMetrics).incrementPnoScanFailedCount();
    }

    /**
     * Verifies that abortScan() calls underlying wificond.
     */
    @Test
    public void testAbortScan() throws Exception {
        IWifiScannerImpl scanner = setupClientInterfaceAndCreateMockWificondScanner();

        mWificondControl.abortScan();
        verify(scanner).abortScan();
    }

    /**
     * Helper method: create a mock IClientInterface which mocks all neccessary operations.
     * Returns a mock IClientInterface.
     */
    private IClientInterface getMockClientInterface() throws Exception {
        IClientInterface clientInterface = mock(IClientInterface.class);
        IWifiScannerImpl scanner = mock(IWifiScannerImpl.class);

        when(clientInterface.getWifiScannerImpl()).thenReturn(scanner);

        return clientInterface;
    }

    /**
     * Helper method: Setup interface to client mode for mWificondControl.
     * Returns a mock IWifiScannerImpl.
     */
    private IWifiScannerImpl setupClientInterfaceAndCreateMockWificondScanner() throws Exception {
        IWificond wificond = mock(IWificond.class);
        IClientInterface clientInterface = mock(IClientInterface.class);
        IWifiScannerImpl scanner = mock(IWifiScannerImpl.class);

        when(mWifiInjector.makeWificond()).thenReturn(wificond);
        when(wificond.createClientInterface()).thenReturn(clientInterface);
        when(clientInterface.getWifiScannerImpl()).thenReturn(scanner);
        when(clientInterface.getInterfaceName()).thenReturn(TEST_INTERFACE_NAME);

        assertEquals(clientInterface, mWificondControl.setupDriverForClientMode());

        return scanner;
    }

    // Create a ArgumentMatcher which captures a SingleScanSettings parameter and checks if it
    // matches the provided frequency set and ssid set.
    private class ScanMatcher implements ArgumentMatcher<SingleScanSettings> {
        private final Set<Integer> mExpectedFreqs;
        private final Set<String> mExpectedSsids;
        ScanMatcher(Set<Integer> expectedFreqs, Set<String> expectedSsids) {
            this.mExpectedFreqs = expectedFreqs;
            this.mExpectedSsids = expectedSsids;
        }

        @Override
        public boolean matches(SingleScanSettings settings) {
            ArrayList<ChannelSettings> channelSettings = settings.channelSettings;
            ArrayList<HiddenNetwork> hiddenNetworks = settings.hiddenNetworks;
            if (mExpectedFreqs != null) {
                Set<Integer> freqSet = new HashSet<Integer>();
                for (ChannelSettings channel : channelSettings) {
                    freqSet.add(channel.frequency);
                }
                if (!mExpectedFreqs.equals(freqSet)) {
                    return false;
                }
            } else {
                if (channelSettings != null && channelSettings.size() > 0) {
                    return false;
                }
            }

            if (mExpectedSsids != null) {
                Set<String> ssidSet = new HashSet<String>();
                for (HiddenNetwork network : hiddenNetworks) {
                    ssidSet.add(NativeUtil.encodeSsid(
                            NativeUtil.byteArrayToArrayList(network.ssid)));
                }
                if (!mExpectedSsids.equals(ssidSet)) {
                    return false;
                }

            } else {
                if (hiddenNetworks != null && hiddenNetworks.size() > 0) {
                    return false;
                }
            }
            return true;
        }

        @Override
        public String toString() {
            return "ScanMatcher{mExpectedFreqs=" + mExpectedFreqs
                    + ", mExpectedSsids=" + mExpectedSsids + '}';
        }
    }

    // Create a ArgumentMatcher which captures a PnoSettings parameter and checks if it
    // matches the WifiNative.PnoSettings;
    private class PnoScanMatcher implements ArgumentMatcher<PnoSettings> {
        private final WifiNative.PnoSettings mExpectedPnoSettings;
        PnoScanMatcher(WifiNative.PnoSettings expectedPnoSettings) {
            this.mExpectedPnoSettings = expectedPnoSettings;
        }
        @Override
        public boolean matches(PnoSettings settings) {
            if (mExpectedPnoSettings == null) {
                return false;
            }
            if (settings.intervalMs != mExpectedPnoSettings.periodInMs
                    || settings.min2gRssi != mExpectedPnoSettings.min24GHzRssi
                    || settings.min5gRssi != mExpectedPnoSettings.min5GHzRssi) {
                return false;
            }
            if (settings.pnoNetworks == null || mExpectedPnoSettings.networkList == null) {
                return false;
            }
            if (settings.pnoNetworks.size() != mExpectedPnoSettings.networkList.length) {
                return false;
            }

            for (int i = 0; i < settings.pnoNetworks.size(); i++) {
                if (!mExpectedPnoSettings.networkList[i].ssid.equals(NativeUtil.encodeSsid(
                         NativeUtil.byteArrayToArrayList(settings.pnoNetworks.get(i).ssid)))) {
                    return false;
                }
                boolean isNetworkHidden = (mExpectedPnoSettings.networkList[i].flags
                        & WifiScanner.PnoSettings.PnoNetwork.FLAG_DIRECTED_SCAN) != 0;
                if (isNetworkHidden != settings.pnoNetworks.get(i).isHidden) {
                    return false;
                }

            }
            return true;
        }

        @Override
        public String toString() {
            return "PnoScanMatcher{" + "mExpectedPnoSettings=" + mExpectedPnoSettings + '}';
        }
    }
}
