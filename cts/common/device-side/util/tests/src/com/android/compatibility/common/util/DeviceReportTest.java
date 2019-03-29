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

import android.app.Instrumentation;
import android.os.Bundle;
import android.os.Environment;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.lang.StringBuilder;

import junit.framework.TestCase;

import org.json.JSONObject;

/**
 * Tests for {@line DeviceReportLog}.
 */
public class DeviceReportTest extends TestCase {

    /**
     * A stub of {@link Instrumentation}
     */
    public class TestInstrumentation extends Instrumentation {

        private int mResultCode = -1;
        private Bundle mResults = null;

        @Override
        public void sendStatus(int resultCode, Bundle results) {
            mResultCode = resultCode;
            mResults = results;
        }
    }

    private static final int RESULT_CODE = 2;
    private static final String RESULT_KEY = "COMPATIBILITY_TEST_RESULT";
    private static final String TEST_MESSAGE_1 = "Foo";
    private static final double TEST_VALUE_1 = 3;
    private static final ResultType TEST_TYPE_1 = ResultType.HIGHER_BETTER;
    private static final ResultUnit TEST_UNIT_1 = ResultUnit.SCORE;
    private static final String TEST_MESSAGE_2 = "Bar";
    private static final double TEST_VALUE_2 = 5;
    private static final ResultType TEST_TYPE_2 = ResultType.LOWER_BETTER;
    private static final ResultUnit TEST_UNIT_2 = ResultUnit.COUNT;
    private static final String TEST_MESSAGE_3 = "Sample";
    private static final double TEST_VALUE_3 = 7;
    private static final ResultType TEST_TYPE_3 = ResultType.LOWER_BETTER;
    private static final ResultUnit TEST_UNIT_3 = ResultUnit.COUNT;
    private static final String TEST_MESSAGE_4 = "Message";
    private static final double TEST_VALUE_4 = 9;
    private static final ResultType TEST_TYPE_4 = ResultType.LOWER_BETTER;
    private static final ResultUnit TEST_UNIT_4 = ResultUnit.COUNT;
    private static final String REPORT_NAME_1 = "TestReport1";
    private static final String REPORT_NAME_2 = "TestReport2";
    private static final String STREAM_NAME_1 = "SampleStream1";
    private static final String STREAM_NAME_2 = "SampleStream2";
    private static final String STREAM_NAME_3 = "SampleStream3";
    private static final String STREAM_NAME_4 = "SampleStream4";

    public void testSubmit() throws Exception {
        DeviceReportLog log = new DeviceReportLog(REPORT_NAME_1, STREAM_NAME_1);
        log.addValue(TEST_MESSAGE_1, TEST_VALUE_1, TEST_TYPE_1, TEST_UNIT_1);
        log.setSummary(TEST_MESSAGE_2, TEST_VALUE_2, TEST_TYPE_2, TEST_UNIT_2);
        TestInstrumentation inst = new TestInstrumentation();
        log.submit(inst);
        assertEquals("Incorrect result code", RESULT_CODE, inst.mResultCode);
        assertNotNull("Bundle missing", inst.mResults);
        String metrics = inst.mResults.getString(RESULT_KEY);
        assertNotNull("Metrics missing", metrics);
        ReportLog result = ReportLog.parse(metrics);
        assertNotNull("Metrics could not be decoded", result);
    }

    public void testFile() throws Exception {
        assertTrue("External storage is not mounted",
                Environment.getExternalStorageState().equals(Environment.MEDIA_MOUNTED));
        final File dir = new File(Environment.getExternalStorageDirectory(), "report-log-files");
        assertTrue("Report Log directory missing", dir.isDirectory() || dir.mkdirs());

        // Remove files from earlier possible runs.
        File[] files = dir.listFiles();
        for (File file : files) {
            file.delete();
        }

        TestInstrumentation inst = new TestInstrumentation();

        DeviceReportLog log1 = new DeviceReportLog(REPORT_NAME_1, STREAM_NAME_1);
        log1.addValue(TEST_MESSAGE_1, TEST_VALUE_1, TEST_TYPE_1, TEST_UNIT_1);
        log1.setSummary(TEST_MESSAGE_1, TEST_VALUE_1, TEST_TYPE_1, TEST_UNIT_1);
        log1.submit(inst);

        DeviceReportLog log2 = new DeviceReportLog(REPORT_NAME_1, STREAM_NAME_2);
        log2.addValue(TEST_MESSAGE_2, TEST_VALUE_2, TEST_TYPE_2, TEST_UNIT_2);
        log2.setSummary(TEST_MESSAGE_2, TEST_VALUE_2, TEST_TYPE_2, TEST_UNIT_2);
        log2.submit(inst);

        DeviceReportLog log3 = new DeviceReportLog(REPORT_NAME_2, STREAM_NAME_3);
        log3.addValue(TEST_MESSAGE_3, TEST_VALUE_3, TEST_TYPE_3, TEST_UNIT_3);
        log3.setSummary(TEST_MESSAGE_3, TEST_VALUE_3, TEST_TYPE_3, TEST_UNIT_3);
        log3.submit(inst);

        DeviceReportLog log4 = new DeviceReportLog(REPORT_NAME_2, STREAM_NAME_4);
        log4.addValue(TEST_MESSAGE_4, TEST_VALUE_4, TEST_TYPE_4, TEST_UNIT_4);
        log4.setSummary(TEST_MESSAGE_4, TEST_VALUE_4, TEST_TYPE_4, TEST_UNIT_4);
        log4.submit(inst);

        File jsonFile1 = new File(dir, REPORT_NAME_1 + ".reportlog.json");
        File jsonFile2 = new File(dir, REPORT_NAME_2 + ".reportlog.json");
        assertTrue("Report Log missing", jsonFile1.exists());
        assertTrue("Report Log missing", jsonFile2.exists());

        BufferedReader jsonReader = new BufferedReader(new FileReader(jsonFile1));
        StringBuilder metricsBuilder = new StringBuilder();
        String line;
        while ((line = jsonReader.readLine()) != null) {
            metricsBuilder.append(line);
        }
        String metrics = metricsBuilder.toString().trim();
        JSONObject jsonObject = new JSONObject(metrics);
        assertTrue("Incorrect metrics",
                jsonObject.getJSONObject(STREAM_NAME_1).getDouble(TEST_MESSAGE_1) == TEST_VALUE_1);
        assertTrue("Incorrect metrics",
                jsonObject.getJSONObject(STREAM_NAME_2).getDouble(TEST_MESSAGE_2) == TEST_VALUE_2);

        jsonReader = new BufferedReader(new FileReader(jsonFile2));
        metricsBuilder = new StringBuilder();
        while ((line = jsonReader.readLine()) != null) {
            metricsBuilder.append(line);
        }
        metrics = metricsBuilder.toString().trim();
        jsonObject = new JSONObject(metrics);
        assertTrue("Incorrect metrics",
                jsonObject.getJSONObject(STREAM_NAME_3).getDouble(TEST_MESSAGE_3) == TEST_VALUE_3);
        assertTrue("Incorrect metrics",
                jsonObject.getJSONObject(STREAM_NAME_4).getDouble(TEST_MESSAGE_4) == TEST_VALUE_4);
    }
}
