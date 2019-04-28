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

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.Matchers.anyString;
import static org.mockito.Matchers.contains;
import static org.mockito.Mockito.anyInt;
import static org.mockito.Mockito.anyObject;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import android.app.test.MockAnswerUtil.AnswerWithArguments;
import android.content.Context;
import android.test.suitebuilder.annotation.SmallTest;

import com.android.internal.R;

import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.mockito.Spy;

import java.io.ByteArrayInputStream;
import java.io.FileDescriptor;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.regex.Pattern;

/**
 * Unit tests for {@link WifiDiagnostics}.
 */
@SmallTest
public class WifiDiagnosticsTest {
    @Mock WifiStateMachine mWsm;
    @Mock WifiNative mWifiNative;
    @Mock BuildProperties mBuildProperties;
    @Mock Context mContext;
    @Mock WifiInjector mWifiInjector;
    @Spy FakeWifiLog mLog;
    @Mock LastMileLogger mLastMileLogger;
    @Mock Runtime mJavaRuntime;
    @Mock Process mExternalProcess;
    WifiDiagnostics mWifiDiagnostics;

    private static final String FAKE_RING_BUFFER_NAME = "fake-ring-buffer";
    private static final int SMALL_RING_BUFFER_SIZE_KB = 32;
    private static final int LARGE_RING_BUFFER_SIZE_KB = 1024;
    private static final int BYTES_PER_KBYTE = 1024;
    private static final long FAKE_CONNECTION_ID = 1;

    private WifiNative.RingBufferStatus mFakeRbs;
    /**
     * Returns the data that we would dump in a bug report, for our ring buffer.
     * @return a 2-D byte array, where the first dimension is the record number, and the second
     * dimension is the byte index within that record.
     */
    private final byte[][] getLoggerRingBufferData() throws Exception {
        return mWifiDiagnostics.getBugReports().get(0).ringBuffers.get(FAKE_RING_BUFFER_NAME);
    }

    /**
     * Initializes common state (e.g. mocks) needed by test cases.
     */
    @Before
    public void setUp() throws Exception {
        MockitoAnnotations.initMocks(this);

        mFakeRbs = new WifiNative.RingBufferStatus();
        mFakeRbs.name = FAKE_RING_BUFFER_NAME;
        WifiNative.RingBufferStatus[] ringBufferStatuses = new WifiNative.RingBufferStatus[] {
                mFakeRbs
        };

        when(mWifiNative.getRingBufferStatus()).thenReturn(ringBufferStatuses);
        when(mWifiNative.readKernelLog()).thenReturn("");
        when(mBuildProperties.isEngBuild()).thenReturn(false);
        when(mBuildProperties.isUserdebugBuild()).thenReturn(false);
        when(mBuildProperties.isUserBuild()).thenReturn(true);
        when(mExternalProcess.getInputStream()).thenReturn(new ByteArrayInputStream(new byte[0]));
        when(mExternalProcess.getErrorStream()).thenReturn(new ByteArrayInputStream(new byte[0]));
        when(mJavaRuntime.exec(anyString())).thenReturn(mExternalProcess);

        MockResources resources = new MockResources();
        resources.setInteger(R.integer.config_wifi_logger_ring_buffer_default_size_limit_kb,
                SMALL_RING_BUFFER_SIZE_KB);
        resources.setInteger(R.integer.config_wifi_logger_ring_buffer_verbose_size_limit_kb,
                LARGE_RING_BUFFER_SIZE_KB);
        when(mContext.getResources()).thenReturn(resources);
        when(mWifiInjector.makeLog(anyString())).thenReturn(mLog);
        when(mWifiInjector.getJavaRuntime()).thenReturn(mJavaRuntime);

        mWifiDiagnostics = new WifiDiagnostics(
                mContext, mWifiInjector, mWsm, mWifiNative, mBuildProperties, mLastMileLogger);
        mWifiNative.enableVerboseLogging(0);
    }

    /** Verifies that startLogging() registers a logging event handler. */
    @Test
    public void startLoggingRegistersLogEventHandler() throws Exception {
        final boolean verbosityToggle = false;  // even default mode wants log events from HAL
        mWifiDiagnostics.startLogging(verbosityToggle);
        verify(mWifiNative).setLoggingEventHandler(anyObject());
    }

    /**
     * Verifies that a failure to set the logging event handler does not prevent a future
     * startLogging() from setting the logging event handler.
     */
    @Test
    public void startLoggingRegistersLogEventHandlerIfPriorAttemptFailed()
            throws Exception {
        final boolean verbosityToggle = false;  // even default mode wants log events from HAL

        when(mWifiNative.setLoggingEventHandler(anyObject())).thenReturn(false);
        mWifiDiagnostics.startLogging(verbosityToggle);
        verify(mWifiNative).setLoggingEventHandler(anyObject());
        reset(mWifiNative);

        when(mWifiNative.setLoggingEventHandler(anyObject())).thenReturn(true);
        mWifiDiagnostics.startLogging(verbosityToggle);
        verify(mWifiNative).setLoggingEventHandler(anyObject());
    }

    /** Verifies that startLogging() does not make redundant calls to setLoggingEventHandler(). */
    @Test
    public void startLoggingDoesNotRegisterLogEventHandlerIfPriorAttemptSucceeded()
            throws Exception {
        final boolean verbosityToggle = false;  // even default mode wants log events from HAL

        when(mWifiNative.setLoggingEventHandler(anyObject())).thenReturn(true);
        mWifiDiagnostics.startLogging(verbosityToggle);
        verify(mWifiNative).setLoggingEventHandler(anyObject());
        reset(mWifiNative);

        mWifiDiagnostics.startLogging(verbosityToggle);
        verify(mWifiNative, never()).setLoggingEventHandler(anyObject());
    }

    /**
     * Verifies that startLogging() restarts HAL ringbuffers.
     *
     * Specifically: verifies that startLogging()
     * a) stops any ring buffer logging that might be already running,
     * b) instructs WifiNative to enable ring buffers of the appropriate log level.
     */
    @Test
    public void startLoggingStopsAndRestartsRingBufferLogging() throws Exception {
        final boolean verbosityToggle = false;
        setBuildPropertiesToEnableRingBuffers();
        mWifiDiagnostics.startLogging(verbosityToggle);
        verify(mWifiNative).startLoggingRingBuffer(
                eq(WifiDiagnostics.VERBOSE_NO_LOG), anyInt(), anyInt(), anyInt(),
                eq(FAKE_RING_BUFFER_NAME));
        verify(mWifiNative).startLoggingRingBuffer(
                eq(WifiDiagnostics.VERBOSE_NORMAL_LOG), anyInt(), anyInt(), anyInt(),
                eq(FAKE_RING_BUFFER_NAME));
    }

    @Test
    public void startLoggingDoesNotStartRingBuffersOnUserBuilds() throws Exception {
        final boolean verbosityToggle = true;
        mWifiDiagnostics.startLogging(verbosityToggle);
        verify(mWifiNative, never()).startLoggingRingBuffer(
                anyInt(), anyInt(), anyInt(), anyInt(), anyString());
    }

    /** Verifies that, if a log handler was registered, then stopLogging() resets it. */
    @Test
    public void stopLoggingResetsLogHandlerIfHandlerWasRegistered() throws Exception {
        final boolean verbosityToggle = false;  // even default mode wants log events from HAL

        when(mWifiNative.setLoggingEventHandler(anyObject())).thenReturn(true);
        mWifiDiagnostics.startLogging(verbosityToggle);
        reset(mWifiNative);

        mWifiDiagnostics.stopLogging();
        verify(mWifiNative).resetLogHandler();
    }

    /** Verifies that, if a log handler is not registered, stopLogging() skips resetLogHandler(). */
    @Test
    public void stopLoggingOnlyResetsLogHandlerIfHandlerWasRegistered() throws Exception {
        final boolean verbosityToggle = false;  // even default mode wants log events from HAL
        mWifiDiagnostics.stopLogging();
        verify(mWifiNative, never()).resetLogHandler();
    }

    /** Verifies that stopLogging() remembers that we've reset the log handler. */
    @Test
    public void multipleStopLoggingCallsOnlyResetLogHandlerOnce() throws Exception {
        final boolean verbosityToggle = false;  // even default mode wants log events from HAL

        when(mWifiNative.setLoggingEventHandler(anyObject())).thenReturn(true);
        mWifiDiagnostics.startLogging(verbosityToggle);
        reset(mWifiNative);

        when(mWifiNative.resetLogHandler()).thenReturn(true);
        mWifiDiagnostics.stopLogging();
        verify(mWifiNative).resetLogHandler();
        reset(mWifiNative);

        mWifiDiagnostics.stopLogging();
        verify(mWifiNative, never()).resetLogHandler();
    }

    /**
     * Verifies that we capture ring-buffer data.
     */
    @Test
    public void canCaptureAndStoreRingBufferData() throws Exception {
        final boolean verbosityToggle = false;
        setBuildPropertiesToEnableRingBuffers();
        mWifiDiagnostics.startLogging(verbosityToggle);

        final byte[] data = new byte[SMALL_RING_BUFFER_SIZE_KB * BYTES_PER_KBYTE];
        mWifiDiagnostics.onRingBufferData(mFakeRbs, data);
        mWifiDiagnostics.captureBugReportData(WifiDiagnostics.REPORT_REASON_NONE);

        byte[][] ringBufferData = getLoggerRingBufferData();
        assertEquals(1, ringBufferData.length);
        assertArrayEquals(data, ringBufferData[0]);
    }

    /**
     * Verifies that we discard extraneous ring-buffer data.
     */
    // TODO(b/36811399): re-enabled this @Test
    public void loggerDiscardsExtraneousData() throws Exception {
        final boolean verbosityToggle = false;
        setBuildPropertiesToEnableRingBuffers();
        mWifiDiagnostics.startLogging(verbosityToggle);

        final byte[] data1 = new byte[SMALL_RING_BUFFER_SIZE_KB * BYTES_PER_KBYTE];
        final byte[] data2 = {1, 2, 3};
        mWifiDiagnostics.onRingBufferData(mFakeRbs, data1);
        mWifiDiagnostics.onRingBufferData(mFakeRbs, data2);
        mWifiDiagnostics.captureBugReportData(WifiDiagnostics.REPORT_REASON_NONE);

        byte[][] ringBufferData = getLoggerRingBufferData();
        assertEquals(1, ringBufferData.length);
        assertArrayEquals(data2, ringBufferData[0]);
    }

    /**
     * Verifies that, when verbose mode is not enabled, startLogging() calls
     * startPktFateMonitoring().
     */
    @Test
    public void startLoggingStartsPacketFateWithoutVerboseMode() {
        final boolean verbosityToggle = false;
        mWifiDiagnostics.startLogging(verbosityToggle);
        verify(mWifiNative).startPktFateMonitoring();
    }

    /**
     * Verifies that, when verbose mode is enabled, startLogging() calls
     * startPktFateMonitoring().
     */
    @Test
    public void startLoggingStartsPacketFateInVerboseMode() {
        final boolean verbosityToggle = true;
        mWifiDiagnostics.startLogging(verbosityToggle);
        verify(mWifiNative).startPktFateMonitoring();
    }

    // Verifies that startLogging() reports failure of startPktFateMonitoring().
    @Test
    public void startLoggingReportsFailureOfStartPktFateMonitoring() {
        final boolean verbosityToggle = true;
        when(mWifiNative.startPktFateMonitoring()).thenReturn(false);
        mWifiDiagnostics.startLogging(verbosityToggle);
        verify(mLog).wC(contains("Failed"));
    }

    /**
     * Verifies that, when verbose mode is not enabled,
     * reportConnectionEvent(WifiDiagnostics.CONNECTION_EVENT_FAILED) still fetches packet fates.
     */
    @Test
    public void reportConnectionFailureIsIgnoredWithoutVerboseMode() {
        final boolean verbosityToggle = false;
        mWifiDiagnostics.startLogging(verbosityToggle);
        mWifiDiagnostics.reportConnectionEvent(
                FAKE_CONNECTION_ID, WifiDiagnostics.CONNECTION_EVENT_FAILED);
        verify(mWifiNative).getTxPktFates(anyObject());
        verify(mWifiNative).getRxPktFates(anyObject());
    }

    /**
     * Verifies that, when verbose mode is enabled, reportConnectionFailure() fetches packet fates.
     */
    @Test
    public void reportConnectionFailureFetchesFatesInVerboseMode() {
        final boolean verbosityToggle = true;
        mWifiDiagnostics.startLogging(verbosityToggle);
        mWifiDiagnostics.reportConnectionEvent(
                FAKE_CONNECTION_ID, WifiDiagnostics.CONNECTION_EVENT_FAILED);
        verify(mWifiNative).getTxPktFates(anyObject());
        verify(mWifiNative).getRxPktFates(anyObject());
    }

    @Test
    public void reportConnectionEventPropagatesStartToLastMileLogger() {
        final boolean verbosityToggle = false;
        mWifiDiagnostics.startLogging(verbosityToggle);
        mWifiDiagnostics.reportConnectionEvent(
                FAKE_CONNECTION_ID, WifiDiagnostics.CONNECTION_EVENT_STARTED);
        verify(mLastMileLogger).reportConnectionEvent(
                FAKE_CONNECTION_ID, WifiDiagnostics.CONNECTION_EVENT_STARTED);
    }

    @Test
    public void reportConnectionEventPropagatesSuccessToLastMileLogger() {
        final boolean verbosityToggle = false;
        mWifiDiagnostics.startLogging(verbosityToggle);
        mWifiDiagnostics.reportConnectionEvent(
                FAKE_CONNECTION_ID, WifiDiagnostics.CONNECTION_EVENT_SUCCEEDED);
        verify(mLastMileLogger).reportConnectionEvent(
                FAKE_CONNECTION_ID, WifiDiagnostics.CONNECTION_EVENT_SUCCEEDED);
    }

    @Test
    public void reportConnectionEventPropagatesFailureToLastMileLogger() {
        final boolean verbosityToggle = false;
        mWifiDiagnostics.startLogging(verbosityToggle);
        mWifiDiagnostics.reportConnectionEvent(
                FAKE_CONNECTION_ID, WifiDiagnostics.CONNECTION_EVENT_FAILED);
        verify(mLastMileLogger).reportConnectionEvent(
                FAKE_CONNECTION_ID, WifiDiagnostics.CONNECTION_EVENT_FAILED);
    }

    /**
     * Verifies that we try to fetch TX fates, even if fetching RX fates failed.
     */
    @Test
    public void loggerFetchesTxFatesEvenIfFetchingRxFatesFails() {
        final boolean verbosityToggle = true;
        when(mWifiNative.getRxPktFates(anyObject())).thenReturn(false);
        mWifiDiagnostics.startLogging(verbosityToggle);
        mWifiDiagnostics.reportConnectionEvent(
                FAKE_CONNECTION_ID, WifiDiagnostics.CONNECTION_EVENT_FAILED);
        verify(mWifiNative).getTxPktFates(anyObject());
        verify(mWifiNative).getRxPktFates(anyObject());
    }

    /**
     * Verifies that we try to fetch RX fates, even if fetching TX fates failed.
     */
    @Test
    public void loggerFetchesRxFatesEvenIfFetchingTxFatesFails() {
        final boolean verbosityToggle = true;
        when(mWifiNative.getTxPktFates(anyObject())).thenReturn(false);
        mWifiDiagnostics.startLogging(verbosityToggle);
        mWifiDiagnostics.reportConnectionEvent(
                FAKE_CONNECTION_ID, WifiDiagnostics.CONNECTION_EVENT_FAILED);
        verify(mWifiNative).getTxPktFates(anyObject());
        verify(mWifiNative).getRxPktFates(anyObject());
    }

    /** Verifies that dump() fetches the latest fates. */
    @Test
    public void dumpFetchesFates() {
        final boolean verbosityToggle = false;
        StringWriter sw = new StringWriter();
        PrintWriter pw = new PrintWriter(sw);
        mWifiDiagnostics.startLogging(verbosityToggle);
        mWifiDiagnostics.dump(new FileDescriptor(), pw, new String[]{"bogus", "args"});
        verify(mWifiNative).getTxPktFates(anyObject());
        verify(mWifiNative).getRxPktFates(anyObject());
    }

    /**
     * Verifies that dump() doesn't crash, or generate garbage, in the case where we haven't fetched
     * any fates.
     */
    @Test
    public void dumpSucceedsWhenNoFatesHaveNotBeenFetched() {
        StringWriter sw = new StringWriter();
        PrintWriter pw = new PrintWriter(sw);
        mWifiDiagnostics.dump(new FileDescriptor(), pw, new String[]{"bogus", "args"});

        String fateDumpString = sw.toString();
        assertTrue(fateDumpString.contains("Last failed"));
        // Verify dump terminator is present
        assertTrue(fateDumpString.contains(
                "--------------------------------------------------------------------"));
    }

    /**
     * Verifies that dump() doesn't crash, or generate garbage, in the case where the fates that
     * the HAL-provided fates are empty.
     */
    @Test
    public void dumpSucceedsWhenFatesHaveBeenFetchedButAreEmpty() {
        final boolean verbosityToggle = true;
        mWifiDiagnostics.startLogging(verbosityToggle);
        mWifiDiagnostics.reportConnectionEvent(
                FAKE_CONNECTION_ID, WifiDiagnostics.CONNECTION_EVENT_FAILED);
        verify(mWifiNative).getTxPktFates(anyObject());
        verify(mWifiNative).getRxPktFates(anyObject());

        StringWriter sw = new StringWriter();
        PrintWriter pw = new PrintWriter(sw);
        mWifiDiagnostics.dump(new FileDescriptor(), pw, new String[]{"bogus", "args"});

        String fateDumpString = sw.toString();
        assertTrue(fateDumpString.contains("Last failed"));
        // Verify dump terminator is present
        assertTrue(fateDumpString.contains(
                "--------------------------------------------------------------------"));
    }

    private String getDumpString(boolean verbose) {
        mWifiDiagnostics.startLogging(verbose);
        mWifiNative.enableVerboseLogging(verbose ? 1 : 0);
        when(mWifiNative.getTxPktFates(anyObject())).then(new AnswerWithArguments() {
            public boolean answer(WifiNative.TxFateReport[] fates) {
                fates[0] = new WifiNative.TxFateReport(
                        WifiLoggerHal.TX_PKT_FATE_ACKED, 2, WifiLoggerHal.FRAME_TYPE_ETHERNET_II,
                        new byte[0]
                );
                fates[1] = new WifiNative.TxFateReport(
                        WifiLoggerHal.TX_PKT_FATE_ACKED, 0, WifiLoggerHal.FRAME_TYPE_ETHERNET_II,
                        new byte[0]
                );
                return true;
            }
        });
        when(mWifiNative.getRxPktFates(anyObject())).then(new AnswerWithArguments() {
            public boolean answer(WifiNative.RxFateReport[] fates) {
                fates[0] = new WifiNative.RxFateReport(
                        WifiLoggerHal.RX_PKT_FATE_SUCCESS, 3, WifiLoggerHal.FRAME_TYPE_ETHERNET_II,
                        new byte[0]
                );
                fates[1] = new WifiNative.RxFateReport(
                        WifiLoggerHal.RX_PKT_FATE_SUCCESS, 1, WifiLoggerHal.FRAME_TYPE_ETHERNET_II,
                        new byte[0]
                );
                return true;
            }
        });
        mWifiDiagnostics.reportConnectionEvent(
                FAKE_CONNECTION_ID, WifiDiagnostics.CONNECTION_EVENT_FAILED);

        StringWriter sw = new StringWriter();
        PrintWriter pw = new PrintWriter(sw);
        mWifiDiagnostics.dump(new FileDescriptor(), pw, new String[]{"bogus", "args"});
        return sw.toString();
    }

      /**
     * Verifies that dump() shows both TX, and RX fates in only table form, when verbose
     * logging is not enabled.
     */
    @Test
    public void dumpShowsTxAndRxFates() {
        final boolean verbosityToggle = false;
        String dumpString = getDumpString(verbosityToggle);
        assertTrue(dumpString.contains(WifiNative.FateReport.getTableHeader()));
        assertTrue(Pattern.compile("0 .* TX ").matcher(dumpString).find());
        assertTrue(Pattern.compile("1 .* RX ").matcher(dumpString).find());
        assertTrue(Pattern.compile("2 .* TX ").matcher(dumpString).find());
        assertTrue(Pattern.compile("3 .* RX ").matcher(dumpString).find());
        assertFalse(dumpString.contains("VERBOSE PACKET FATE DUMP"));
        assertFalse(dumpString.contains("Frame bytes"));
    }

    /**
     * Verifies that dump() shows both TX, and RX fates in table and verbose forms, when verbose
     * logging is enabled.
     */
    @Test
    public void dumpShowsTxAndRxFatesVerbose() {
        final boolean verbosityToggle = true;
        String dumpString = getDumpString(verbosityToggle);
        assertTrue(dumpString.contains(WifiNative.FateReport.getTableHeader()));
        assertTrue(Pattern.compile("0 .* TX ").matcher(dumpString).find());
        assertTrue(Pattern.compile("1 .* RX ").matcher(dumpString).find());
        assertTrue(Pattern.compile("2 .* TX ").matcher(dumpString).find());
        assertTrue(Pattern.compile("3 .* RX ").matcher(dumpString).find());
        assertTrue(dumpString.contains("VERBOSE PACKET FATE DUMP"));
        assertTrue(dumpString.contains("Frame bytes"));
    }

    /**
     * Verifies that dump() outputs frames in timestamp order, even though the HAL provided the
     * data out-of-order (order is specified in getDumpString()).
     */
    @Test
    public void dumpIsSortedByTimestamp() {
        final boolean verbosityToggle = true;
        String dumpString = getDumpString(verbosityToggle);
        assertTrue(dumpString.contains(WifiNative.FateReport.getTableHeader()));
        assertTrue(Pattern.compile(
                "0 .* TX .*\n" +
                "1 .* RX .*\n" +
                "2 .* TX .*\n" +
                "3 .* RX "
        ).matcher(dumpString).find());

        int expected_index_of_verbose_frame_0 = dumpString.indexOf(
                "Frame direction: TX\nFrame timestamp: 0\n");
        int expected_index_of_verbose_frame_1 = dumpString.indexOf(
                "Frame direction: RX\nFrame timestamp: 1\n");
        int expected_index_of_verbose_frame_2 = dumpString.indexOf(
                "Frame direction: TX\nFrame timestamp: 2\n");
        int expected_index_of_verbose_frame_3 = dumpString.indexOf(
                "Frame direction: RX\nFrame timestamp: 3\n");
        assertFalse(-1 == expected_index_of_verbose_frame_0);
        assertFalse(-1 == expected_index_of_verbose_frame_1);
        assertFalse(-1 == expected_index_of_verbose_frame_2);
        assertFalse(-1 == expected_index_of_verbose_frame_3);
        assertTrue(expected_index_of_verbose_frame_0 < expected_index_of_verbose_frame_1);
        assertTrue(expected_index_of_verbose_frame_1 < expected_index_of_verbose_frame_2);
        assertTrue(expected_index_of_verbose_frame_2 < expected_index_of_verbose_frame_3);
    }

    /** Verifies that eng builds do not show fate detail outside of verbose mode. */
    @Test
    public void dumpOmitsFateDetailInEngBuildsOutsideOfVerboseMode() throws Exception {
        final boolean verbosityToggle = false;
        when(mBuildProperties.isEngBuild()).thenReturn(true);
        when(mBuildProperties.isUserdebugBuild()).thenReturn(false);
        when(mBuildProperties.isUserBuild()).thenReturn(false);
        String dumpString = getDumpString(verbosityToggle);
        assertFalse(dumpString.contains("VERBOSE PACKET FATE DUMP"));
        assertFalse(dumpString.contains("Frame bytes"));
    }

    /** Verifies that userdebug builds do not show fate detail outside of verbose mode. */
    @Test
    public void dumpOmitsFateDetailInUserdebugBuildsOutsideOfVerboseMode() throws Exception {
        final boolean verbosityToggle = false;
        when(mBuildProperties.isUserdebugBuild()).thenReturn(true);
        when(mBuildProperties.isEngBuild()).thenReturn(false);
        when(mBuildProperties.isUserBuild()).thenReturn(false);
        String dumpString = getDumpString(verbosityToggle);
        assertFalse(dumpString.contains("VERBOSE PACKET FATE DUMP"));
        assertFalse(dumpString.contains("Frame bytes"));
    }

    /**
     * Verifies that, if verbose is disabled after fetching fates, the dump does not include
     * verbose fate logs.
     */
    @Test
    public void dumpOmitsFatesIfVerboseIsDisabledAfterFetch() {
        final boolean verbosityToggle = true;
        mWifiDiagnostics.startLogging(verbosityToggle);
        when(mWifiNative.getTxPktFates(anyObject())).then(new AnswerWithArguments() {
            public boolean answer(WifiNative.TxFateReport[] fates) {
                fates[0] = new WifiNative.TxFateReport(
                        WifiLoggerHal.TX_PKT_FATE_ACKED, 0, WifiLoggerHal.FRAME_TYPE_ETHERNET_II,
                        new byte[0]
                );
                return true;
            }
        });
        when(mWifiNative.getRxPktFates(anyObject())).then(new AnswerWithArguments() {
            public boolean answer(WifiNative.RxFateReport[] fates) {
                fates[0] = new WifiNative.RxFateReport(
                        WifiLoggerHal.RX_PKT_FATE_SUCCESS, 1, WifiLoggerHal.FRAME_TYPE_ETHERNET_II,
                        new byte[0]
                );
                return true;
            }
        });
        mWifiDiagnostics.reportConnectionEvent(
                FAKE_CONNECTION_ID, WifiDiagnostics.CONNECTION_EVENT_FAILED);
        verify(mWifiNative).getTxPktFates(anyObject());
        verify(mWifiNative).getRxPktFates(anyObject());

        final boolean newVerbosityToggle = false;
        mWifiDiagnostics.startLogging(newVerbosityToggle);

        StringWriter sw = new StringWriter();
        PrintWriter pw = new PrintWriter(sw);
        mWifiDiagnostics.dump(new FileDescriptor(), pw, new String[]{"bogus", "args"});

        String fateDumpString = sw.toString();
        assertFalse(fateDumpString.contains("VERBOSE PACKET FATE DUMP"));
        assertFalse(fateDumpString.contains("Frame bytes"));
    }

    @Test
    public void dumpSucceedsEvenIfRingBuffersAreDisabled() {
        final boolean verbosityToggle = true;
        mWifiDiagnostics.startLogging(verbosityToggle);
        verify(mWifiNative, never()).startLoggingRingBuffer(
                anyInt(), anyInt(), anyInt(), anyInt(), anyString());

        StringWriter sw = new StringWriter();
        PrintWriter pw = new PrintWriter(sw);
        mWifiDiagnostics.dump(new FileDescriptor(), pw, new String[]{"bogus", "args"});
    }

    /** Verifies that the default size of our ring buffers is small. */
    // TODO(b/36811399): re-enable this @Test
    public void ringBufferSizeIsSmallByDefault() throws Exception {
        final boolean verbosityToggle = false;
        mWifiDiagnostics.startLogging(verbosityToggle);
        mWifiDiagnostics.onRingBufferData(
                mFakeRbs, new byte[SMALL_RING_BUFFER_SIZE_KB * BYTES_PER_KBYTE + 1]);
        mWifiDiagnostics.captureBugReportData(WifiDiagnostics.REPORT_REASON_NONE);
        assertEquals(0, getLoggerRingBufferData().length);
    }

    /** Verifies that we use small ring buffers by default, on userdebug builds. */
    // TODO(b/36811399): re-enable this @Test
    public void ringBufferSizeIsSmallByDefaultOnUserdebugBuilds() throws Exception {
        final boolean verbosityToggle = false;
        when(mBuildProperties.isUserdebugBuild()).thenReturn(true);
        when(mBuildProperties.isEngBuild()).thenReturn(false);
        when(mBuildProperties.isUserBuild()).thenReturn(false);
        mWifiDiagnostics.startLogging(verbosityToggle);
        mWifiDiagnostics.onRingBufferData(
                mFakeRbs, new byte[SMALL_RING_BUFFER_SIZE_KB * BYTES_PER_KBYTE + 1]);
        mWifiDiagnostics.captureBugReportData(WifiDiagnostics.REPORT_REASON_NONE);
        assertEquals(0, getLoggerRingBufferData().length);
    }

    /** Verifies that we use small ring buffers by default, on eng builds. */
    // TODO(b/36811399): re-enable this @Test
    public void ringBufferSizeIsSmallByDefaultOnEngBuilds() throws Exception {
        final boolean verbosityToggle = false;
        when(mBuildProperties.isEngBuild()).thenReturn(true);
        when(mBuildProperties.isUserdebugBuild()).thenReturn(false);
        when(mBuildProperties.isUserBuild()).thenReturn(false);
        mWifiDiagnostics.startLogging(verbosityToggle);
        mWifiDiagnostics.onRingBufferData(
                mFakeRbs, new byte[SMALL_RING_BUFFER_SIZE_KB * BYTES_PER_KBYTE + 1]);
        mWifiDiagnostics.captureBugReportData(WifiDiagnostics.REPORT_REASON_NONE);
        assertEquals(0, getLoggerRingBufferData().length);
    }

    /** Verifies that we use large ring buffers when initially started in verbose mode. */
    @Test
    public void ringBufferSizeIsLargeInVerboseMode() throws Exception {
        final boolean verbosityToggle = true;
        setBuildPropertiesToEnableRingBuffers();

        mWifiDiagnostics.startLogging(verbosityToggle);
        mWifiDiagnostics.onRingBufferData(
                mFakeRbs, new byte[LARGE_RING_BUFFER_SIZE_KB * BYTES_PER_KBYTE]);
        mWifiDiagnostics.captureBugReportData(WifiDiagnostics.REPORT_REASON_NONE);
        assertEquals(1, getLoggerRingBufferData().length);
    }

    /** Verifies that we use large ring buffers when switched from normal to verbose mode. */
    @Test
    public void startLoggingGrowsRingBuffersIfNeeded() throws Exception {
        setBuildPropertiesToEnableRingBuffers();

        mWifiDiagnostics.startLogging(false  /* verbose disabled */);
        mWifiDiagnostics.startLogging(true  /* verbose enabled */);
        mWifiDiagnostics.onRingBufferData(
                mFakeRbs, new byte[LARGE_RING_BUFFER_SIZE_KB * BYTES_PER_KBYTE]);
        mWifiDiagnostics.captureBugReportData(WifiDiagnostics.REPORT_REASON_NONE);
        assertEquals(1, getLoggerRingBufferData().length);
    }

    /** Verifies that we use small ring buffers when switched from verbose to normal mode. */
    // TODO(b/36811399): re-enabled this @Test
    public void startLoggingShrinksRingBuffersIfNeeded() throws Exception {
        setBuildPropertiesToEnableRingBuffers();

        mWifiDiagnostics.startLogging(true  /* verbose enabled */);
        mWifiDiagnostics.onRingBufferData(
                mFakeRbs, new byte[SMALL_RING_BUFFER_SIZE_KB * BYTES_PER_KBYTE + 1]);

        // Existing data is nuked (too large).
        mWifiDiagnostics.startLogging(false  /* verbose disabled */);
        mWifiDiagnostics.captureBugReportData(WifiDiagnostics.REPORT_REASON_NONE);
        assertEquals(0, getLoggerRingBufferData().length);

        // New data must obey limit as well.
        mWifiDiagnostics.onRingBufferData(
                mFakeRbs, new byte[SMALL_RING_BUFFER_SIZE_KB * BYTES_PER_KBYTE + 1]);
        mWifiDiagnostics.captureBugReportData(WifiDiagnostics.REPORT_REASON_NONE);
        assertEquals(0, getLoggerRingBufferData().length);
    }

    /** Verifies that we skip the firmware and driver dumps if verbose is not enabled. */
    @Test
    public void captureBugReportSkipsFirmwareAndDriverDumpsByDefault() {
        mWifiDiagnostics.captureBugReportData(WifiDiagnostics.REPORT_REASON_NONE);
        verify(mWifiNative, never()).getFwMemoryDump();
        verify(mWifiNative, never()).getDriverStateDump();
    }

    /** Verifies that we capture the firmware and driver dumps if verbose is enabled. */
    @Test
    public void captureBugReportTakesFirmwareAndDriverDumpsInVerboseMode() {
        mWifiDiagnostics.startLogging(true  /* verbose enabled */);
        mWifiDiagnostics.captureBugReportData(WifiDiagnostics.REPORT_REASON_NONE);
        verify(mWifiNative).getFwMemoryDump();
        verify(mWifiNative).getDriverStateDump();
    }

    /** Verifies that the dump includes driver state, if driver state was provided by HAL. */
    @Test
    public void dumpIncludesDriverStateDumpIfAvailable() {
        when(mWifiNative.getDriverStateDump()).thenReturn(new byte[]{0, 1, 2});

        mWifiDiagnostics.startLogging(true  /* verbose enabled */);
        mWifiDiagnostics.captureBugReportData(WifiDiagnostics.REPORT_REASON_NONE);
        verify(mWifiNative).getDriverStateDump();

        StringWriter sw = new StringWriter();
        PrintWriter pw = new PrintWriter(sw);
        mWifiDiagnostics.dump(new FileDescriptor(), pw, new String[]{});
        assertTrue(sw.toString().contains(WifiDiagnostics.DRIVER_DUMP_SECTION_HEADER));
    }

    /** Verifies that the dump skips driver state, if driver state was not provided by HAL. */
    @Test
    public void dumpOmitsDriverStateDumpIfUnavailable() {
        mWifiDiagnostics.startLogging(true  /* verbose enabled */);
        mWifiDiagnostics.captureBugReportData(WifiDiagnostics.REPORT_REASON_NONE);
        verify(mWifiNative).getDriverStateDump();

        StringWriter sw = new StringWriter();
        PrintWriter pw = new PrintWriter(sw);
        mWifiDiagnostics.dump(new FileDescriptor(), pw, new String[]{});
        assertFalse(sw.toString().contains(WifiDiagnostics.DRIVER_DUMP_SECTION_HEADER));
    }

    /** Verifies that the dump omits driver state, if verbose was disabled after capture. */
    @Test
    public void dumpOmitsDriverStateDumpIfVerboseDisabledAfterCapture() {
        when(mWifiNative.getDriverStateDump()).thenReturn(new byte[]{0, 1, 2});

        mWifiDiagnostics.startLogging(true  /* verbose enabled */);
        mWifiDiagnostics.captureBugReportData(WifiDiagnostics.REPORT_REASON_NONE);
        verify(mWifiNative).getDriverStateDump();

        mWifiDiagnostics.startLogging(false  /* verbose no longer enabled */);

        StringWriter sw = new StringWriter();
        PrintWriter pw = new PrintWriter(sw);
        mWifiDiagnostics.dump(new FileDescriptor(), pw, new String[]{});
        assertFalse(sw.toString().contains(WifiDiagnostics.DRIVER_DUMP_SECTION_HEADER));
    }

    /** Verifies that the dump includes firmware dump, if firmware dump was provided by HAL. */
    @Test
    public void dumpIncludesFirmwareMemoryDumpIfAvailable() {
        when(mWifiNative.getFwMemoryDump()).thenReturn(new byte[]{0, 1, 2});

        mWifiDiagnostics.startLogging(true  /* verbose enabled */);
        mWifiDiagnostics.captureBugReportData(WifiDiagnostics.REPORT_REASON_NONE);
        verify(mWifiNative).getFwMemoryDump();

        StringWriter sw = new StringWriter();
        PrintWriter pw = new PrintWriter(sw);
        mWifiDiagnostics.dump(new FileDescriptor(), pw, new String[]{});
        assertTrue(sw.toString().contains(WifiDiagnostics.FIRMWARE_DUMP_SECTION_HEADER));
    }

    /** Verifies that the dump skips firmware memory, if firmware memory was not provided by HAL. */
    @Test
    public void dumpOmitsFirmwareMemoryDumpIfUnavailable() {
        mWifiDiagnostics.startLogging(true  /* verbose enabled */);
        mWifiDiagnostics.captureBugReportData(WifiDiagnostics.REPORT_REASON_NONE);
        verify(mWifiNative).getFwMemoryDump();

        StringWriter sw = new StringWriter();
        PrintWriter pw = new PrintWriter(sw);
        mWifiDiagnostics.dump(new FileDescriptor(), pw, new String[]{});
        assertFalse(sw.toString().contains(WifiDiagnostics.FIRMWARE_DUMP_SECTION_HEADER));
    }

    /** Verifies that the dump omits firmware memory, if verbose was disabled after capture. */
    @Test
    public void dumpOmitsFirmwareMemoryDumpIfVerboseDisabledAfterCapture() {
        when(mWifiNative.getFwMemoryDump()).thenReturn(new byte[]{0, 1, 2});

        mWifiDiagnostics.startLogging(true  /* verbose enabled */);
        mWifiDiagnostics.captureBugReportData(WifiDiagnostics.REPORT_REASON_NONE);
        verify(mWifiNative).getFwMemoryDump();

        mWifiDiagnostics.startLogging(false  /* verbose no longer enabled */);

        StringWriter sw = new StringWriter();
        PrintWriter pw = new PrintWriter(sw);
        mWifiDiagnostics.dump(new FileDescriptor(), pw, new String[]{});
        assertFalse(sw.toString().contains(WifiDiagnostics.FIRMWARE_DUMP_SECTION_HEADER));
    }

    @Test
    public void dumpRequestsLastMileLoggerDump() {
        mWifiDiagnostics.dump(
                new FileDescriptor(), new PrintWriter(new StringWriter()), new String[]{});
        verify(mLastMileLogger).dump(anyObject());
    }

    private void setBuildPropertiesToEnableRingBuffers() {
        when(mBuildProperties.isEngBuild()).thenReturn(false);
        when(mBuildProperties.isUserdebugBuild()).thenReturn(true);
        when(mBuildProperties.isUserBuild()).thenReturn(false);
    }
}
