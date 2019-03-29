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
package com.android.cts.verifier.sensors.sixdof.Utils;

import android.content.Context;
import android.os.Build;
import android.util.Xml;

import org.xmlpull.v1.XmlSerializer;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.Locale;

/**
 * Handles all the XML to print to the user.
 */
public class TestReport {

    public enum TestStatus {
        NOT_EXECUTED,
        EXECUTED,
        PASS,
        FAIL,
    }

    private static final int REPORT_VERSION = 1;
    private static DateFormat DATE_FORMAT = new SimpleDateFormat(
            "EEE MMM dd HH:mm:ss z yyyy", Locale.ENGLISH);
    private static final String TEST_RESULTS_REPORT_TAG = "test-results-report";
    private static final String VERIFIER_INFO_TAG = "verifier-info";
    private static final String DEVICE_INFO_TAG = "device-info";
    private static final String BUILD_INFO_TAG = "build-info";
    private static final String TEST_RESULTS_TAG = "test-results";
    private static final String TEST_TAG = "test";
    private static final String TEST_DETAILS_TAG = "details";
    private String mTestStatus = "not-executed";
    private Context mContext;
    private ArrayList<String> mTestDetails = new ArrayList<>();

    /**
     * Sets the context of this test.
     *
     * @param context reference to the activity this test is in.
     */
    public TestReport(Context context) {
        mContext = context;
    }

    /**
     * Produces the XML for the test.
     *
     * @return the XML of the test to display.
     */
    public String getContents()
            throws IllegalArgumentException, IllegalStateException, IOException {

        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();

        XmlSerializer xml = Xml.newSerializer();

        xml.setOutput(outputStream, "utf-8");
        xml.setFeature("http://xmlpull.org/v1/doc/features.html#indent-output", true);
        xml.startDocument("utf-8", true);

        xml.startTag(null, TEST_RESULTS_REPORT_TAG);
        xml.attribute(null, "report-version", Integer.toString(REPORT_VERSION));
        xml.attribute(null, "creation-time", DATE_FORMAT.format(new Date()));

        xml.startTag(null, VERIFIER_INFO_TAG);
        xml.attribute(null, "version-name", Version.getVersionName(mContext));
        xml.attribute(null, "version-code", Integer.toString(Version.getVersionCode(mContext)));
        xml.endTag(null, VERIFIER_INFO_TAG);

        xml.startTag(null, DEVICE_INFO_TAG);
        xml.startTag(null, BUILD_INFO_TAG);
        xml.attribute(null, "board", Build.BOARD);
        xml.attribute(null, "brand", Build.BRAND);
        xml.attribute(null, "device", Build.DEVICE);
        xml.attribute(null, "display", Build.DISPLAY);
        xml.attribute(null, "fingerprint", Build.FINGERPRINT);
        xml.attribute(null, "id", Build.ID);
        xml.attribute(null, "model", Build.MODEL);
        xml.attribute(null, "product", Build.PRODUCT);
        xml.attribute(null, "release", Build.VERSION.RELEASE);
        xml.attribute(null, "sdk", Integer.toString(Build.VERSION.SDK_INT));
        xml.endTag(null, BUILD_INFO_TAG);
        xml.endTag(null, DEVICE_INFO_TAG);

        xml.startTag(null, TEST_RESULTS_TAG);
        xml.startTag(null, TEST_TAG);
        xml.attribute(null, "title", "6dof accuracy test");
        xml.attribute(null, "class-name", "com.android.cts.verifier.sixdof.Activities.TestActivity");

        if (mTestDetails.isEmpty()) {
            xml.attribute(null, "result", mTestStatus);
        } else {
            setTestState(TestStatus.FAIL);
            xml.attribute(null, "result", mTestStatus);
            xml.startTag(null, TEST_DETAILS_TAG);

            for (int i = 0; i < mTestDetails.size(); i++) {
                xml.text(mTestDetails.get(i));
            }

            xml.endTag(null, TEST_DETAILS_TAG);
        }

        xml.endTag(null, TEST_TAG);
        xml.endTag(null, TEST_RESULTS_TAG);

        xml.endTag(null, TEST_RESULTS_REPORT_TAG);
        xml.endDocument();

        return outputStream.toString("utf-8");
    }

    /**
     * Adds the failed results to the details.
     *
     * @param failedPart the failed test result.
     */
    public void setFailDetails(String failedPart) {
        mTestDetails.add(failedPart);
    }

    /**
     * Sets the status the test is currently in.
     *
     * @param state the status the test is in.
     */
    public void setTestState(TestStatus state) {
        switch (state) {
            case EXECUTED:
                mTestStatus = "executed";
                break;
            case PASS:
                mTestStatus = "passed";
                break;
            case FAIL:
                mTestStatus = "failed";
                break;
            case NOT_EXECUTED:
                mTestStatus = "not-executed";
                break;
            default:
                throw new AssertionError("TestExecuted default we should not be in", null);
        }
    }
}
