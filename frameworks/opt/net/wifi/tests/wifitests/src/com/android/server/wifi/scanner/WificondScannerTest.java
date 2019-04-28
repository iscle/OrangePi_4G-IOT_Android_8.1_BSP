/*
 * Copyright (C) 2015 The Android Open Source Project
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

package com.android.server.wifi.scanner;

import static com.android.server.wifi.ScanTestUtil.NativeScanSettingsBuilder;
import static com.android.server.wifi.ScanTestUtil.assertScanDatasEquals;
import static com.android.server.wifi.ScanTestUtil.createFreqSet;

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

import android.net.wifi.ScanResult;
import android.net.wifi.WifiScanner;
import android.net.wifi.WifiSsid;
import android.test.suitebuilder.annotation.SmallTest;

import com.android.server.wifi.ScanDetail;
import com.android.server.wifi.ScanResults;
import com.android.server.wifi.WifiMonitor;
import com.android.server.wifi.WifiNative;
import com.android.server.wifi.scanner.ChannelHelper.ChannelCollection;

import org.junit.Before;
import org.junit.Test;
import org.mockito.InOrder;

import java.io.FileDescriptor;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.ArrayList;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Unit tests for {@link com.android.server.wifi.scanner.WificondScannerImpl}.
 */
@SmallTest
public class WificondScannerTest extends BaseWifiScannerImplTest {

    @Before
    public void setup() throws Exception {
        mScanner = new WificondScannerImpl(mContext, mWifiNative, mWifiMonitor,
                mLooper.getLooper(), mClock);
    }

    /**
     * Test that WificondScannerImpl will not issue a scan and report scan failure
     * when there is no channel to scan for.
     */
    @Test
    public void singleScanNotIssuedIfNoAvailableChannels() {
        // Use mocked ChannelHelper and ChannelCollection to simulate the scenario
        // that no channel is available for this request.
        ChannelHelper channelHelper = mock(ChannelHelper.class);
        ChannelCollection channelCollection = mock(ChannelCollection.class);
        when(channelHelper.createChannelCollection()).thenReturn(channelCollection);
        when(channelCollection.isEmpty()).thenReturn(true);

        mScanner = new WificondScannerImpl(mContext, mWifiNative, mWifiMonitor,
                channelHelper, mLooper.getLooper(), mClock);

        WifiNative.ScanSettings settings = new NativeScanSettingsBuilder()
                .withBasePeriod(10000) // ms
                .withMaxApPerScan(10)
                .addBucketWithBand(10000 /* ms */, WifiScanner.REPORT_EVENT_AFTER_EACH_SCAN,
                        WifiScanner.WIFI_BAND_5_GHZ)
                .build();
        WifiNative.ScanEventHandler eventHandler = mock(WifiNative.ScanEventHandler.class);
        mScanner.startSingleScan(settings, eventHandler);

        mLooper.dispatchAll();

        // No scan is issued to WifiNative.
        verify(mWifiNative, never()).scan(any(), any(Set.class));
        // A scan failed event must be reported.
        verify(eventHandler).onScanStatus(WifiNative.WIFI_SCAN_FAILED);
    }

    @Test
    public void backgroundScanSuccessSingleBucket() {
        WifiNative.ScanSettings settings = new NativeScanSettingsBuilder()
                .withBasePeriod(10000)
                .withMaxApPerScan(10)
                .addBucketWithBand(10000, WifiScanner.REPORT_EVENT_AFTER_EACH_SCAN,
                        WifiScanner.WIFI_BAND_24_GHZ)
                .build();

        ScanPeriod[] expectedPeriods = new ScanPeriod[] {
            new ScanPeriod(ScanPeriod.ReportType.RESULT,
                    ScanResults.create(0, 2400),
                    expectedBandScanFreqs(WifiScanner.WIFI_BAND_24_GHZ)),
            new ScanPeriod(ScanPeriod.ReportType.RESULT,
                    ScanResults.create(1, 2450),
                    expectedBandScanFreqs(WifiScanner.WIFI_BAND_24_GHZ))
        };

        doSuccessfulTest(settings, expectedPeriods);
    }

    @Test
    public void backgroundScanMaxApExceeded() {
        WifiNative.ScanSettings settings = new NativeScanSettingsBuilder()
                .withBasePeriod(10000)
                .withMaxApPerScan(2)
                .addBucketWithBand(10000,
                        WifiScanner.REPORT_EVENT_AFTER_EACH_SCAN
                        | WifiScanner.REPORT_EVENT_FULL_SCAN_RESULT,
                        WifiScanner.WIFI_BAND_24_GHZ)
                .build();

        ScanPeriod[] expectedPeriods = new ScanPeriod[] {
            new ScanPeriod(ScanPeriod.ReportType.FULL_AND_RESULT,
                    ScanResults.createOverflowing(0, 2,
                            new ScanDetail(WifiSsid.createFromAsciiEncoded("TEST AP 1"),
                                    "00:00:00:00:00:00", "", -70, 2450, Long.MAX_VALUE, 0),
                            new ScanDetail(WifiSsid.createFromAsciiEncoded("TEST AP 2"),
                                    "AA:BB:CC:DD:EE:FF", "", -66, 2400, Long.MAX_VALUE, 0),
                            new ScanDetail(WifiSsid.createFromAsciiEncoded("TEST AP 3"),
                                    "00:00:00:00:00:00", "", -80, 2450, Long.MAX_VALUE, 0),
                            new ScanDetail(WifiSsid.createFromAsciiEncoded("TEST AP 4"),
                                    "AA:BB:CC:11:22:33", "", -65, 2450, Long.MAX_VALUE, 0)),
                    expectedBandScanFreqs(WifiScanner.WIFI_BAND_24_GHZ))
        };

        doSuccessfulTest(settings, expectedPeriods);
    }

    @Test
    public void backgroundScanSuccessWithFullScanResults() {
        WifiNative.ScanSettings settings = new NativeScanSettingsBuilder()
                .withBasePeriod(10000)
                .withMaxApPerScan(10)
                .addBucketWithBand(10000,
                        WifiScanner.REPORT_EVENT_AFTER_EACH_SCAN
                        | WifiScanner.REPORT_EVENT_FULL_SCAN_RESULT,
                        WifiScanner.WIFI_BAND_24_GHZ)
                .build();

        ScanPeriod[] expectedPeriods = new ScanPeriod[] {
            new ScanPeriod(ScanPeriod.ReportType.FULL_AND_RESULT,
                    ScanResults.create(0, 2400, 2450, 2400, 2400),
                    expectedBandScanFreqs(WifiScanner.WIFI_BAND_24_GHZ)),
            new ScanPeriod(ScanPeriod.ReportType.FULL_AND_RESULT,
                    ScanResults.create(1, 2450, 2400, 2450, 2400),
                    expectedBandScanFreqs(WifiScanner.WIFI_BAND_24_GHZ))
        };

        doSuccessfulTest(settings, expectedPeriods);
    }

    @Test
    public void backgroundScanSuccessWithMixedFullResultsAndNot() {
        WifiNative.ScanSettings settings = new NativeScanSettingsBuilder()
                .withBasePeriod(10000)
                .withMaxApPerScan(10)
                .addBucketWithBand(10000,
                        WifiScanner.REPORT_EVENT_AFTER_EACH_SCAN,
                        WifiScanner.WIFI_BAND_24_GHZ)
                .addBucketWithBand(20000,
                        WifiScanner.REPORT_EVENT_AFTER_EACH_SCAN
                        | WifiScanner.REPORT_EVENT_FULL_SCAN_RESULT,
                        WifiScanner.WIFI_BAND_5_GHZ)
                .build();

        ScanPeriod[] expectedPeriods = new ScanPeriod[] {
            new ScanPeriod(ScanPeriod.ReportType.FULL_AND_RESULT,
                    ScanResults.create(0, 2400, 2450, 2400, 5175),
                    expectedBandScanFreqs(WifiScanner.WIFI_BAND_BOTH)),
            new ScanPeriod(ScanPeriod.ReportType.RESULT,
                    ScanResults.create(1, 2450, 2400, 2450, 2400),
                    expectedBandScanFreqs(WifiScanner.WIFI_BAND_24_GHZ)),
            new ScanPeriod(ScanPeriod.ReportType.FULL_AND_RESULT,
                    ScanResults.create(2, 2450, 2400, 2450, 5150),
                    expectedBandScanFreqs(WifiScanner.WIFI_BAND_BOTH))
        };

        doSuccessfulTest(settings, expectedPeriods);
    }

    @Test
    public void backgroundScanNoBatch() {
        WifiNative.ScanSettings settings = new NativeScanSettingsBuilder()
                .withBasePeriod(10000)
                .withMaxApPerScan(10)
                .addBucketWithBand(10000,
                        WifiScanner.REPORT_EVENT_NO_BATCH,
                        WifiScanner.WIFI_BAND_24_GHZ)
                .build();

        ScanPeriod[] expectedPeriods = new ScanPeriod[] {
            new ScanPeriod(ScanPeriod.ReportType.NONE,
                    ScanResults.create(0, 2400, 2400, 2400),
                    expectedBandScanFreqs(WifiScanner.WIFI_BAND_24_GHZ)),
            new ScanPeriod(ScanPeriod.ReportType.NONE,
                    ScanResults.create(1, 2400, 2400, 2450),
                    expectedBandScanFreqs(WifiScanner.WIFI_BAND_24_GHZ)),
            new ScanPeriod(ScanPeriod.ReportType.NONE,
                    ScanResults.create(2, 2400, 2450, 2400),
                    expectedBandScanFreqs(WifiScanner.WIFI_BAND_24_GHZ))
        };

        doSuccessfulTest(settings, expectedPeriods);
    }

    @Test
    public void backgroundScanBatch() {
        WifiNative.ScanSettings settings = new NativeScanSettingsBuilder()
                .withBasePeriod(10000)
                .withMaxApPerScan(10)
                .withMaxScansToCache(3)
                .addBucketWithBand(10000,
                        WifiScanner.REPORT_EVENT_AFTER_BUFFER_FULL,
                        WifiScanner.WIFI_BAND_24_GHZ)
                .build();

        ScanResults[] periodResults = new ScanResults[] {
            ScanResults.create(0, 2400, 2400, 2400),
            ScanResults.create(1, 2400, 2400, 2400, 2400),
            ScanResults.create(2, 2450),
            ScanResults.create(3, 2400, 2400),
            ScanResults.create(4, 2400, 2450),
            ScanResults.create(5, 2450)
        };

        ScanPeriod[] expectedPeriods = new ScanPeriod[] {
            new ScanPeriod(ScanPeriod.ReportType.NONE,
                    periodResults[0],
                    expectedBandScanFreqs(WifiScanner.WIFI_BAND_24_GHZ)),
            new ScanPeriod(ScanPeriod.ReportType.NONE,
                    periodResults[1],
                    expectedBandScanFreqs(WifiScanner.WIFI_BAND_24_GHZ)),
            new ScanPeriod(ScanPeriod.ReportType.RESULT,
                    new ScanResults[] {
                        periodResults[0],
                        periodResults[1],
                        periodResults[2]
                    },
                    expectedBandScanFreqs(WifiScanner.WIFI_BAND_24_GHZ)),
            new ScanPeriod(ScanPeriod.ReportType.NONE,
                    periodResults[3],
                    expectedBandScanFreqs(WifiScanner.WIFI_BAND_24_GHZ)),
            new ScanPeriod(ScanPeriod.ReportType.NONE,
                    periodResults[4],
                    expectedBandScanFreqs(WifiScanner.WIFI_BAND_24_GHZ)),
            new ScanPeriod(ScanPeriod.ReportType.RESULT,
                    new ScanResults[] {
                        periodResults[3],
                        periodResults[4],
                        periodResults[5]
                    },
                    expectedBandScanFreqs(WifiScanner.WIFI_BAND_24_GHZ))
        };

        doSuccessfulTest(settings, expectedPeriods);
    }

    @Test
    public void backgroundScanSuccessWithMultipleBuckets() {
        WifiNative.ScanSettings settings = new NativeScanSettingsBuilder()
                .withBasePeriod(10000)
                .withMaxApPerScan(10)
                .addBucketWithBand(10000, WifiScanner.REPORT_EVENT_AFTER_EACH_SCAN,
                        WifiScanner.WIFI_BAND_24_GHZ)
                .addBucketWithBand(30000, WifiScanner.REPORT_EVENT_AFTER_EACH_SCAN,
                        WifiScanner.WIFI_BAND_BOTH)
                .addBucketWithChannels(20000, WifiScanner.REPORT_EVENT_AFTER_EACH_SCAN,
                        5650)
                .build();

        ScanPeriod[] expectedPeriods = new ScanPeriod[] {
            new ScanPeriod(ScanPeriod.ReportType.RESULT,
                    ScanResults.create(0, 2400, 5175),
                    expectedBandAndChannelScanFreqs(WifiScanner.WIFI_BAND_BOTH, 5650)),
            new ScanPeriod(ScanPeriod.ReportType.RESULT,
                    ScanResults.create(1, 2400),
                    expectedBandScanFreqs(WifiScanner.WIFI_BAND_24_GHZ)),
            new ScanPeriod(ScanPeriod.ReportType.RESULT,
                    ScanResults.create(2, 2450, 5650),
                    expectedBandAndChannelScanFreqs(WifiScanner.WIFI_BAND_24_GHZ, 5650)),
            new ScanPeriod(ScanPeriod.ReportType.RESULT,
                    ScanResults.create(3, 2450, 5175),
                    expectedBandScanFreqs(WifiScanner.WIFI_BAND_BOTH)),
            new ScanPeriod(ScanPeriod.ReportType.RESULT,
                    ScanResults.create(4, new int[0]),
                    expectedBandAndChannelScanFreqs(WifiScanner.WIFI_BAND_24_GHZ, 5650)),
            new ScanPeriod(ScanPeriod.ReportType.RESULT,
                    ScanResults.create(5, 2400, 2400, 2400, 2450),
                    expectedBandScanFreqs(WifiScanner.WIFI_BAND_24_GHZ)),
            new ScanPeriod(ScanPeriod.ReportType.RESULT,
                    ScanResults.create(6, 5150, 5650, 5650),
                    expectedBandAndChannelScanFreqs(WifiScanner.WIFI_BAND_BOTH, 5650))
        };

        doSuccessfulTest(settings, expectedPeriods);
    }

    @Test
    public void backgroundScanSuccessWithMultipleBucketsWhereAPeriodDoesNotRequireAScan() {
        WifiNative.ScanSettings settings = new NativeScanSettingsBuilder()
                .withBasePeriod(10000)
                .withMaxApPerScan(10)
                .addBucketWithBand(30000, WifiScanner.REPORT_EVENT_AFTER_EACH_SCAN,
                        WifiScanner.WIFI_BAND_BOTH)
                .addBucketWithChannels(20000, WifiScanner.REPORT_EVENT_AFTER_EACH_SCAN,
                        5650)
                .build();

        // expected scan frequencies
        ScanPeriod[] expectedPeriods = new ScanPeriod[] {
            new ScanPeriod(ScanPeriod.ReportType.RESULT,
                    ScanResults.create(0, 2400, 5175),
                    expectedBandAndChannelScanFreqs(WifiScanner.WIFI_BAND_BOTH, 5650)),
            null,
            new ScanPeriod(ScanPeriod.ReportType.RESULT,
                    ScanResults.create(1, 5650),
                    createFreqSet(5650)),
            new ScanPeriod(ScanPeriod.ReportType.RESULT,
                    ScanResults.create(2, 2450, 5175),
                    expectedBandScanFreqs(WifiScanner.WIFI_BAND_BOTH)),
            new ScanPeriod(ScanPeriod.ReportType.RESULT,
                    ScanResults.create(3, 5650, 5650, 5650),
                    createFreqSet(5650)),
            null,
            new ScanPeriod(ScanPeriod.ReportType.RESULT,
                    ScanResults.create(4, 2400, 2400, 2400, 2450),
                    expectedBandAndChannelScanFreqs(WifiScanner.WIFI_BAND_BOTH, 5650))
        };

        doSuccessfulTest(settings, expectedPeriods);
    }

    @Test
    public void backgroundScanStartFailed() {
        WifiNative.ScanEventHandler eventHandler = mock(WifiNative.ScanEventHandler.class);
        WifiNative.ScanSettings settings = new NativeScanSettingsBuilder()
                .withBasePeriod(10000)
                .withMaxApPerScan(10)
                .addBucketWithBand(10000, WifiScanner.REPORT_EVENT_AFTER_EACH_SCAN,
                        WifiScanner.WIFI_BAND_24_GHZ)
                .build();

        InOrder order = inOrder(eventHandler, mWifiNative);

        // All scans fail
        when(mWifiNative.scan(any(), any(Set.class))).thenReturn(false);

        // Start scan
        mScanner.startBatchedScan(settings, eventHandler);

        assertBackgroundPeriodAlarmPending();

        expectFailedScanStart(order, eventHandler,
                expectedBandScanFreqs(WifiScanner.WIFI_BAND_24_GHZ));

        // Fire alarm to start next scan
        dispatchBackgroundPeriodAlarm();

        assertBackgroundPeriodAlarmPending();

        expectFailedScanStart(order, eventHandler,
                expectedBandScanFreqs(WifiScanner.WIFI_BAND_24_GHZ));

        verifyNoMoreInteractions(eventHandler);
    }


    @Test
    public void backgroundScanEventFailed() {
        WifiNative.ScanEventHandler eventHandler = mock(WifiNative.ScanEventHandler.class);
        WifiNative.ScanSettings settings = new NativeScanSettingsBuilder()
                .withBasePeriod(10000)
                .withMaxApPerScan(10)
                .addBucketWithBand(10000, WifiScanner.REPORT_EVENT_AFTER_EACH_SCAN,
                        WifiScanner.WIFI_BAND_24_GHZ)
                .build();

        InOrder order = inOrder(eventHandler, mWifiNative);

        // All scan starts succeed
        when(mWifiNative.scan(any(), any(Set.class))).thenReturn(true);

        // Start scan
        mScanner.startBatchedScan(settings, eventHandler);

        assertBackgroundPeriodAlarmPending();

        expectFailedEventScan(order, eventHandler,
                expectedBandScanFreqs(WifiScanner.WIFI_BAND_24_GHZ));

        // Fire alarm to start next scan
        dispatchBackgroundPeriodAlarm();

        assertBackgroundPeriodAlarmPending();

        expectFailedEventScan(order, eventHandler,
                expectedBandScanFreqs(WifiScanner.WIFI_BAND_24_GHZ));

        verifyNoMoreInteractions(eventHandler);
    }

    /**
     * Run a scan and then pause after the first scan completes, but before the next one starts
     * Then resume the scan
     */
    @Test
    public void pauseWhileWaitingToStartNextScanAndResumeScan() {
        WifiNative.ScanEventHandler eventHandler = mock(WifiNative.ScanEventHandler.class);
        WifiNative.ScanSettings settings = new NativeScanSettingsBuilder()
                .withBasePeriod(10000)
                .withMaxApPerScan(10)
                .addBucketWithBand(10000, WifiScanner.REPORT_EVENT_AFTER_EACH_SCAN,
                        WifiScanner.WIFI_BAND_24_GHZ)
                .build();

        ScanPeriod[] expectedPeriods = new ScanPeriod[] {
            new ScanPeriod(ScanPeriod.ReportType.RESULT,
                    ScanResults.create(0, 2400, 2450, 2450),
                    expectedBandScanFreqs(WifiScanner.WIFI_BAND_24_GHZ)),
            new ScanPeriod(ScanPeriod.ReportType.RESULT,
                    ScanResults.create(1, 2400),
                    expectedBandScanFreqs(WifiScanner.WIFI_BAND_24_GHZ))
        };

        InOrder order = inOrder(eventHandler, mWifiNative);

        // All scan starts succeed
        when(mWifiNative.scan(any(), any(Set.class))).thenReturn(true);

        // Start scan
        mScanner.startBatchedScan(settings, eventHandler);

        assertBackgroundPeriodAlarmPending();

        expectSuccessfulBackgroundScan(order, eventHandler, expectedPeriods[0], 0);

        assertBackgroundPeriodAlarmPending();

        mScanner.pauseBatchedScan();

        // onPause callback (previous results were flushed)
        order.verify(eventHandler).onScanPaused(new WifiScanner.ScanData[0]);

        assertBackgroundPeriodAlarmNotPending();

        mScanner.restartBatchedScan();

        // onRestarted callback
        order.verify(eventHandler).onScanRestarted();

        expectSuccessfulBackgroundScan(order, eventHandler, expectedPeriods[1], 1);

        verifyNoMoreInteractions(eventHandler);
    }

    /**
     * Run a scan and then pause while the first scan is running
     * Then resume the scan
     */
    @Test
    public void pauseWhileScanningAndResumeScan() {
        WifiNative.ScanEventHandler eventHandler = mock(WifiNative.ScanEventHandler.class);
        WifiNative.ScanSettings settings = new NativeScanSettingsBuilder()
                .withBasePeriod(10000)
                .withMaxApPerScan(10)
                .addBucketWithBand(10000, WifiScanner.REPORT_EVENT_AFTER_EACH_SCAN,
                        WifiScanner.WIFI_BAND_24_GHZ)
                .build();

        ScanPeriod[] expectedPeriods = new ScanPeriod[] {
            new ScanPeriod(ScanPeriod.ReportType.RESULT,
                    ScanResults.create(0, 2400, 2450, 2450),
                    expectedBandScanFreqs(WifiScanner.WIFI_BAND_24_GHZ)),
            new ScanPeriod(ScanPeriod.ReportType.RESULT,
                    ScanResults.create(1, 2400),
                    expectedBandScanFreqs(WifiScanner.WIFI_BAND_24_GHZ))
        };

        InOrder order = inOrder(eventHandler, mWifiNative);

        // All scan starts succeed
        when(mWifiNative.scan(any(), any(Set.class))).thenReturn(true);

        // Start scan
        mScanner.startBatchedScan(settings, eventHandler);

        assertBackgroundPeriodAlarmPending();

        order.verify(mWifiNative).scan(eq(expectedPeriods[0].getScanFreqs()), any(Set.class));

        mScanner.pauseBatchedScan();

        // onPause callback (no pending results)
        order.verify(eventHandler).onScanPaused(new WifiScanner.ScanData[0]);

        assertBackgroundPeriodAlarmNotPending();

        // Setup scan results
        when(mWifiNative.getScanResults()).thenReturn(expectedPeriods[0]
                .getResultsToBeDelivered()[0].getScanDetailArrayList());

        // Notify scan has finished
        mWifiMonitor.sendMessage(mWifiNative.getInterfaceName(), WifiMonitor.SCAN_RESULTS_EVENT);
        assertEquals("dispatch message after results event", 1, mLooper.dispatchAll());

        // listener should not be notified

        mScanner.restartBatchedScan();

        // onRestarted callback
        order.verify(eventHandler).onScanRestarted();

        expectSuccessfulBackgroundScan(order, eventHandler, expectedPeriods[1], 1);

        verifyNoMoreInteractions(eventHandler);
    }


    /**
     * Run a scan and then pause after the first scan completes, but before the next one starts
     * Then schedule a new scan while still paused
     */
    @Test
    public void pauseWhileWaitingToStartNextScanAndStartNewScan() {
        WifiNative.ScanEventHandler eventHandler = mock(WifiNative.ScanEventHandler.class);
        WifiNative.ScanSettings settings = new NativeScanSettingsBuilder()
                .withBasePeriod(10000)
                .withMaxApPerScan(10)
                .addBucketWithBand(10000, WifiScanner.REPORT_EVENT_AFTER_EACH_SCAN,
                        WifiScanner.WIFI_BAND_24_GHZ)
                .build();

        WifiNative.ScanSettings settings2 = new NativeScanSettingsBuilder()
                .withBasePeriod(10000)
                .withMaxApPerScan(10)
                .addBucketWithBand(10000, WifiScanner.REPORT_EVENT_AFTER_EACH_SCAN,
                        WifiScanner.WIFI_BAND_5_GHZ)
                .build();

        ScanPeriod[] expectedPeriods = new ScanPeriod[] {
            new ScanPeriod(ScanPeriod.ReportType.RESULT,
                    ScanResults.create(0, 2400, 2450, 2450),
                    expectedBandScanFreqs(WifiScanner.WIFI_BAND_24_GHZ))
        };

        ScanPeriod[] expectedPeriods2 = new ScanPeriod[] {
            new ScanPeriod(ScanPeriod.ReportType.RESULT,
                    ScanResults.create(1, 5150, 5175, 5175),
                    expectedBandScanFreqs(WifiScanner.WIFI_BAND_5_GHZ)),
        };

        InOrder order = inOrder(eventHandler, mWifiNative);

        // All scan starts succeed
        when(mWifiNative.scan(any(), any(Set.class))).thenReturn(true);

        // Start scan
        mScanner.startBatchedScan(settings, eventHandler);

        assertBackgroundPeriodAlarmPending();

        expectSuccessfulBackgroundScan(order, eventHandler, expectedPeriods[0], 0);

        assertBackgroundPeriodAlarmPending();

        mScanner.pauseBatchedScan();

        // onPause callback (previous results were flushed)
        order.verify(eventHandler).onScanPaused(new WifiScanner.ScanData[0]);

        assertBackgroundPeriodAlarmNotPending();

        // Start new scan
        mScanner.startBatchedScan(settings2, eventHandler);

        expectSuccessfulBackgroundScan(order, eventHandler, expectedPeriods2[0], 0);

        verifyNoMoreInteractions(eventHandler);
    }

    /**
     * Test that dump() of WificondScannerImpl dumps native scan results.
     */
    @Test
    public void dumpContainsNativeScanResults() {
        assertDumpContainsRequestLog("Latest native scan results:");
    }

    private void assertDumpContainsRequestLog(String log) {
        String objectDump = dumpObject();
        Pattern logLineRegex = Pattern.compile(".*" + log + ".*");
        assertTrue("dump did not contain log = " + log + "\n " + objectDump + "\n",
                logLineRegex.matcher(objectDump).find());
    }

    private String dumpObject() {
        StringWriter stringWriter = new StringWriter();
        mScanner.dump(new FileDescriptor(), new PrintWriter(stringWriter),
                new String[0]);
        return stringWriter.toString();
    }

    /**
     * Run a test with the given settings where all native scans succeed
     * This will execute expectedPeriods.length scan periods by first
     * starting the scan settings and then dispatching the scan period alarm to start the
     * next scan.
     */
    private void doSuccessfulTest(WifiNative.ScanSettings settings, ScanPeriod[] expectedPeriods) {
        WifiNative.ScanEventHandler eventHandler = mock(WifiNative.ScanEventHandler.class);

        InOrder order = inOrder(eventHandler, mWifiNative);

        // All scans succeed
        when(mWifiNative.scan(any(), any(Set.class))).thenReturn(true);

        // Start scan
        mScanner.startBatchedScan(settings, eventHandler);

        for (int i = 0; i < expectedPeriods.length; ++i) {
            ScanPeriod period = expectedPeriods[i];
            assertBackgroundPeriodAlarmPending();
            if (period != null) { // scan should be scheduled
                expectSuccessfulBackgroundScan(order, eventHandler, expectedPeriods[i], i);
            }
            if (i < expectedPeriods.length - 1) {
                dispatchBackgroundPeriodAlarm();
            }
        }

        verifyNoMoreInteractions(eventHandler);
    }

    /**
     * Verify the state after a scan was started either through startBatchedScan or
     * dispatching the period alarm.
     */
    private void expectSuccessfulBackgroundScan(InOrder order,
            WifiNative.ScanEventHandler eventHandler, ScanPeriod period, int periodId) {
        WifiScanner.ScanData[] scanDatas = null;
        ArrayList<ScanDetail> nativeResults = null;
        ScanResult[] fullResults = null;
        if (period.getResultsToBeDelivered() != null) {
            ScanResults lastPeriodResults = period.getResultsToBeDelivered()
                    [period.getResultsToBeDelivered().length - 1];
            nativeResults = lastPeriodResults.getScanDetailArrayList();
            if (period.expectResults()) {
                scanDatas =
                        new WifiScanner.ScanData[period.getResultsToBeDelivered().length];
                for (int j = 0; j < scanDatas.length; ++j) {
                    scanDatas[j] = period.getResultsToBeDelivered()[j].getScanData();
                }
            }
            if (period.expectFullResults()) {
                fullResults = lastPeriodResults.getRawScanResults();
            }
        }
        expectSuccessfulBackgroundScan(order, eventHandler, period.getScanFreqs(),
                nativeResults, scanDatas, fullResults, periodId);
    }

    /**
     * Verify the state after a scan was started either through startBatchedScan or
     * dispatching the period alarm.
     */
    private void expectSuccessfulBackgroundScan(InOrder order,
            WifiNative.ScanEventHandler eventHandler, Set<Integer> scanFreqs,
            ArrayList<ScanDetail> nativeResults,
            WifiScanner.ScanData[] expectedScanResults,
            ScanResult[] fullResults, int periodId) {
        // Verify scan started
        order.verify(mWifiNative).scan(eq(scanFreqs), any(Set.class));

        // Setup scan results
        when(mWifiNative.getScanResults()).thenReturn(nativeResults);

        // Notify scan has finished
        mWifiMonitor.sendMessage(mWifiNative.getInterfaceName(), WifiMonitor.SCAN_RESULTS_EVENT);
        assertEquals("dispatch message after results event", 1, mLooper.dispatchAll());

        if (fullResults != null) {
            for (ScanResult result : fullResults) {
                order.verify(eventHandler).onFullScanResult(eq(result), eq(0));
            }
        }

        if (expectedScanResults != null) {
            // Verify scan results delivered
            order.verify(eventHandler).onScanStatus(WifiNative.WIFI_SCAN_RESULTS_AVAILABLE);
            assertScanDatasEquals("period[" + periodId + "].", expectedScanResults,
                    mScanner.getLatestBatchedScanResults(true));
        }
    }

    private void expectFailedScanStart(InOrder order, WifiNative.ScanEventHandler eventHandler,
            Set<Integer> scanFreqs) {
        // Verify scan started
        order.verify(mWifiNative).scan(eq(scanFreqs), any(Set.class));
    }

    private void expectFailedEventScan(InOrder order, WifiNative.ScanEventHandler eventHandler,
            Set<Integer> scanFreqs) {
        // Verify scan started
        order.verify(mWifiNative).scan(eq(scanFreqs), any(Set.class));

        // Notify scan has failed
        mWifiMonitor.sendMessage(mWifiNative.getInterfaceName(), WifiMonitor.SCAN_FAILED_EVENT);
        assertEquals("dispatch message after results event", 1, mLooper.dispatchAll());

        // TODO: verify failure event
    }

    private void assertBackgroundPeriodAlarmPending() {
        assertTrue("background period alarm not pending",
                mAlarmManager.isPending(WificondScannerImpl.BACKGROUND_PERIOD_ALARM_TAG));
    }

    private void assertBackgroundPeriodAlarmNotPending() {
        assertFalse("background period alarm is pending",
                mAlarmManager.isPending(WificondScannerImpl.BACKGROUND_PERIOD_ALARM_TAG));
    }

    private void dispatchBackgroundPeriodAlarm() {
        assertTrue("dispatch background period alarm",
                mAlarmManager.dispatch(WificondScannerImpl.BACKGROUND_PERIOD_ALARM_TAG));
        mLooper.dispatchAll();
    }

    private static class ScanPeriod {
        enum ReportType {
            NONE(false, false),
            RESULT(true, false),
            FULL_AND_RESULT(true, true),
            FULL(false, true);

            public final boolean result;
            public final boolean full;
            ReportType(boolean result, boolean full) {
                this.result = result;
                this.full = full;
            }
        };
        private final ReportType mReportType;
        private final ScanResults[] mDeliveredResults;
        private final Set<Integer> mRequestedFreqs;

        ScanPeriod(ReportType reportType, ScanResults deliveredResult,
                Set<Integer> requestedFreqs) {
            this(reportType, new ScanResults[] {deliveredResult}, requestedFreqs);
        }

        ScanPeriod(ReportType reportType, ScanResults[] deliveredResults,
                Set<Integer> requestedFreqs) {
            mReportType = reportType;
            mDeliveredResults = deliveredResults;
            mRequestedFreqs = requestedFreqs;
        }

        public boolean expectResults() {
            return mReportType.result;
        }
        public boolean expectFullResults() {
            return mReportType.full;
        }
        public final ScanResults[] getResultsToBeDelivered() {
            return mDeliveredResults;
        }
        public Set<Integer> getScanFreqs() {
            return mRequestedFreqs;
        }
    }
}
