/*
 * Copyright (C) 2014 The Android Open Source Project
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

import junit.framework.TestCase;

import org.xmlpull.v1.XmlPullParserFactory;
import org.xmlpull.v1.XmlSerializer;

import java.io.ByteArrayOutputStream;
import java.io.IOException;

//TODO(stuartscott): Delete file for v2, ReportLog can serialize itself.
/**
 * Unit tests for {@link MetricsXmlSerializer}
 */
public class MetricsXmlSerializerTest extends TestCase {

    static class LocalReportLog extends ReportLog {}
    private static final double[] VALUES = new double[] {1, 11, 21, 1211, 111221};
    private static final String HEADER = "<?xml version='1.0' encoding='utf-8' standalone='yes' ?>";
    private static final String EXPECTED_XML = HEADER
            + "<Summary message=\"Sample\" scoreType=\"higher_better\" unit=\"byte\">1.0</Summary>";

    private LocalReportLog mLocalReportLog;
    private MetricsXmlSerializer mMetricsXmlSerializer;
    private ByteArrayOutputStream mByteArrayOutputStream;
    private XmlSerializer xmlSerializer;

    @Override
    public void setUp() throws Exception {
        mLocalReportLog = new LocalReportLog();
        mByteArrayOutputStream = new ByteArrayOutputStream();
        XmlPullParserFactory factory = XmlPullParserFactory.newInstance(null, null);
        xmlSerializer = factory.newSerializer();
        xmlSerializer.setOutput(mByteArrayOutputStream, "utf-8");

        this.mMetricsXmlSerializer = new MetricsXmlSerializer(xmlSerializer);
    }

    public void testSerialize_null() throws IOException {
        xmlSerializer.startDocument("utf-8", true);
        mMetricsXmlSerializer.serialize(null);
        xmlSerializer.endDocument();

        assertEquals(HEADER.length(), mByteArrayOutputStream.toByteArray().length);
    }

    public void testSerialize_noData() throws IOException {
        xmlSerializer.startDocument("utf-8", true);
        mMetricsXmlSerializer.serialize(mLocalReportLog);
        xmlSerializer.endDocument();

        assertEquals(HEADER.length(), mByteArrayOutputStream.toByteArray().length);
    }

    public void testSerialize() throws IOException {
        mLocalReportLog.setSummary("Sample", 1.0, ResultType.HIGHER_BETTER, ResultUnit.BYTE);
        mLocalReportLog.addValues("Details", VALUES, ResultType.NEUTRAL, ResultUnit.FPS);

        xmlSerializer.startDocument("utf-8", true);
        mMetricsXmlSerializer.serialize(mLocalReportLog);
        xmlSerializer.endDocument();

        assertEquals(EXPECTED_XML, mByteArrayOutputStream.toString("utf-8"));
    }
}