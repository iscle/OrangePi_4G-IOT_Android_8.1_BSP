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
 * limitations under the License
 */

package com.android.compatibility.common.util;

import com.android.compatibility.common.util.ReportLog.Metric;

import junit.framework.TestCase;

import java.util.Arrays;

/**
 * Unit tests for {@link ReportLog}
 */
public class ReportLogTest extends TestCase {

    private static final String SOURCE = "Source";
    private static final String MESSAGE = "Message";
    private static final double[] VALUES = new double[] {.1, 124, 4736, 835.683, 98, 395};
    private static final String HEADER_XML =
            "<?xml version='1.0' encoding='UTF-8' standalone='yes' ?>";
    private static final String SUMMARY_XML =
            HEADER_XML + "\r\n" +
            "<Summary>\r\n" +
            "  <Metric source=\"com.android.compatibility.common.util.ReportLogTest#%s\" "
            + "message=\"Sample\" score_type=\"higher_better\" score_unit=\"byte\">\r\n" +
            "    <Value>1.0</Value>\r\n" +
            "  </Metric>\r\n" +
            "</Summary>";
    private static final String FULL_XML = SUMMARY_XML;

    private ReportLog mReportLog;

    @Override
    protected void setUp() throws Exception {
        mReportLog = new ReportLog();
    }

    public void testSerialize_null() throws Exception {
        try {
            ReportLog.serialize(null);
            fail("Expected IllegalArgumentException when serializing an empty report");
        } catch (IllegalArgumentException e) {
            // Expected
        }
    }

    public void testSerialize_noData() throws Exception {
        ReportLog.serialize(mReportLog);
    }

    public void testSerialize_summaryOnly() throws Exception {
        mReportLog.setSummary("Sample", 1.0, ResultType.HIGHER_BETTER, ResultUnit.BYTE);
        assertEquals(String.format(SUMMARY_XML, "testSerialize_summaryOnly:66"),
                ReportLog.serialize(mReportLog));
    }

    public void testSerialize_detailOnly() throws Exception {
        mReportLog.addValues("Details", VALUES, ResultType.NEUTRAL, ResultUnit.FPS);
        assertEquals(HEADER_XML, ReportLog.serialize(mReportLog));
    }

    public void testSerialize_full() throws Exception {
        mReportLog.setSummary("Sample", 1.0, ResultType.HIGHER_BETTER, ResultUnit.BYTE);
        mReportLog.addValues("Details", VALUES, ResultType.NEUTRAL, ResultUnit.FPS);
        assertEquals(String.format(FULL_XML, "testSerialize_full:77"),
                ReportLog.serialize(mReportLog));
    }

    public void testParse_null() throws Exception {
        try {
            ReportLog.parse((String) null);
            fail("Expected IllegalArgumentException when passing a null report");
        } catch(IllegalArgumentException e) {
            // Expected
        }
    }

    public void testParse_noData() throws Exception {
        ReportLog report = ReportLog.parse(HEADER_XML);
        assertNull(report.getSummary());
    }

    public void testParse_summaryOnly() throws Exception {
        ReportLog report = ReportLog.parse(String.format(SUMMARY_XML, "testParse_summaryOnly:125"));
        assertNotNull(report);
        assertEquals("Sample", report.getSummary().getMessage());
    }

    public void testParse_full() throws Exception {
        ReportLog report = ReportLog.parse(String.format(FULL_XML, "testParse_full:140"));
        assertNotNull(report);
        assertEquals("Sample", report.getSummary().getMessage());
    }

    public void testLimits_source() throws Exception {
        // Should pass with a short source.
        Metric metric = new Metric(SOURCE, MESSAGE, 1.0, ResultType.HIGHER_BETTER, ResultUnit.BYTE);
        assertEquals("Expected message to be ok", SOURCE, metric.getSource());
        // Make a long source.
        StringBuilder sb = new StringBuilder();
        // 40 x "Source" = 240 character string
        for (int i = 0; i < 40; i++) sb.append(SOURCE);
        String source = sb.toString();
        // Should be trimmed because source is too long.
        metric = new Metric(source, MESSAGE, 1.0, ResultType.HIGHER_BETTER, ResultUnit.BYTE);
        assertEquals("Expected source to be trimmed", source.substring(source.length() - 200),
                metric.getSource());
    }

    public void testLimits_message() throws Exception {
        // Should pass with a short message.
        Metric metric = new Metric(SOURCE, MESSAGE, 1.0, ResultType.HIGHER_BETTER, ResultUnit.BYTE);
        assertEquals("Expected message to be ok", MESSAGE, metric.getMessage());
        // Make a long message.
        StringBuilder sb = new StringBuilder();
        // 40 x "Message" = 280 character string
        for (int i = 0; i < 40; i++) sb.append(MESSAGE);
        String message = sb.toString();
        // Should be trimmed because message is too long.
        metric = new Metric(SOURCE, message, 1.0, ResultType.HIGHER_BETTER, ResultUnit.BYTE);
        assertEquals("Expected message to be trimmed", message.substring(0, 200),
                metric.getMessage());
    }

    public void testLimits_values() throws Exception {
        // Should pass with a small array.
        Metric metric = new Metric(SOURCE, MESSAGE, VALUES, ResultType.HIGHER_BETTER,
                ResultUnit.BYTE);
        assertTrue("Expected values to be ok", Arrays.equals(VALUES, metric.getValues()));
        // Make a big array.
        double[] values = new double[1001];
        for (int i = 0; i < values.length; i++) values[i] = i;
        // Should be trimmed because array is too big.
        metric = new Metric(SOURCE, MESSAGE, values, ResultType.HIGHER_BETTER, ResultUnit.BYTE);
        assertTrue("Expected values to be trimmed", Arrays.equals(Arrays.copyOf(values, 1000),
                metric.getValues()));
    }

}
