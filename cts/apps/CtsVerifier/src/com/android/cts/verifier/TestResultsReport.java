/*
 * Copyright (C) 2010 The Android Open Source Project
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

package com.android.cts.verifier;

import android.content.Context;
import android.os.Build;
import android.text.TextUtils;
import android.util.Xml;

import com.android.compatibility.common.util.DevicePropertyInfo;
import com.android.compatibility.common.util.ICaseResult;
import com.android.compatibility.common.util.IInvocationResult;
import com.android.compatibility.common.util.IModuleResult;
import com.android.compatibility.common.util.InvocationResult;
import com.android.compatibility.common.util.ITestResult;
import com.android.compatibility.common.util.MetricsXmlSerializer;
import com.android.compatibility.common.util.ReportLog;
import com.android.compatibility.common.util.TestStatus;
import com.android.cts.verifier.TestListAdapter.TestListItem;

import org.xmlpull.v1.XmlSerializer;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.Map.Entry;

/**
 * Helper class for creating an {@code InvocationResult} for CTS result generation.
 */
class TestResultsReport {

    /** Version of the test report. Increment whenever adding new tags and attributes. */
    private static final int REPORT_VERSION = 2;

    /** Format of the report's creation time. Maintain the same format at CTS. */
    private static DateFormat DATE_FORMAT =
            new SimpleDateFormat("EEE MMM dd HH:mm:ss z yyyy", Locale.ENGLISH);

    private static final String PREFIX_TAG = "build_";
    private static final String TEST_RESULTS_REPORT_TAG = "test-results-report";
    private static final String VERIFIER_INFO_TAG = "verifier-info";
    private static final String DEVICE_INFO_TAG = "device-info";
    private static final String BUILD_INFO_TAG = "build-info";
    private static final String TEST_RESULTS_TAG = "test-results";
    private static final String TEST_TAG = "test";
    private static final String TEST_DETAILS_TAG = "details";

    private static final String MODULE_ID = "noabi CtsVerifier";
    private static final String TEST_CASE_NAME = "manualTests";

    private final Context mContext;

    private final TestListAdapter mAdapter;

    TestResultsReport(Context context, TestListAdapter adapter) {
        this.mContext = context;
        this.mAdapter = adapter;
    }

    IInvocationResult generateResult() {
        String abis = null;
        String abis32 = null;
        String abis64 = null;
        String versionBaseOs = null;
        String versionSecurityPatch = null;
        IInvocationResult result = new InvocationResult();
        IModuleResult moduleResult = result.getOrCreateModule(MODULE_ID);

        // Collect build fields available in API level 21
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            abis = TextUtils.join(",", Build.SUPPORTED_ABIS);
            abis32 = TextUtils.join(",", Build.SUPPORTED_32_BIT_ABIS);
            abis64 = TextUtils.join(",", Build.SUPPORTED_64_BIT_ABIS);
        }

        // Collect build fields available in API level 23
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            versionBaseOs = Build.VERSION.BASE_OS;
            versionSecurityPatch = Build.VERSION.SECURITY_PATCH;
        }

        // at the time of writing, the build class has no REFERENCE_FINGERPRINT property
        String referenceFingerprint = null;

        DevicePropertyInfo devicePropertyInfo = new DevicePropertyInfo(Build.CPU_ABI,
                Build.CPU_ABI2, abis, abis32, abis64, Build.BOARD, Build.BRAND, Build.DEVICE,
                Build.FINGERPRINT, Build.ID, Build.MANUFACTURER, Build.MODEL, Build.PRODUCT,
                referenceFingerprint, Build.SERIAL, Build.TAGS, Build.TYPE, versionBaseOs,
                Build.VERSION.RELEASE, Integer.toString(Build.VERSION.SDK_INT),
                versionSecurityPatch, Build.VERSION.INCREMENTAL);

        // add device properties to the result with a prefix tag for each key
        for (Entry<String, String> entry :
                devicePropertyInfo.getPropertytMapWithPrefix(PREFIX_TAG).entrySet()) {
            String entryValue = entry.getValue();
            if (entryValue != null) {
                result.addInvocationInfo(entry.getKey(), entry.getValue());
            }
        }

        ICaseResult caseResult = moduleResult.getOrCreateResult(TEST_CASE_NAME);
        int count = mAdapter.getCount();
        int notExecutedCount = 0;
        for (int i = 0; i < count; i++) {
            TestListItem item = mAdapter.getItem(i);
            if (item.isTest()) {
                ITestResult currentTestResult = caseResult.getOrCreateResult(item.testName);
                TestStatus resultStatus = getTestResultStatus(mAdapter.getTestResult(i));
                if (resultStatus == null) {
                    ++notExecutedCount;
                }
                currentTestResult.setResultStatus(resultStatus);
                // TODO: report test details with Extended Device Info (EDI) or CTS metrics
                // String details = mAdapter.getTestDetails(i);

                ReportLog reportLog = mAdapter.getReportLog(i);
                if (reportLog != null) {
                    currentTestResult.setReportLog(reportLog);
                }
            }
        }
        moduleResult.setDone(true);
        moduleResult.setNotExecuted(notExecutedCount);

        return result;
    }

    String getContents() {
        // TODO: remove getContents and everything that depends on it
        return "Report viewing is deprecated. See contents on the SD Card.";
    }

    private TestStatus getTestResultStatus(int testResult) {
        switch (testResult) {
            case TestResult.TEST_RESULT_PASSED:
                return TestStatus.PASS;

            case TestResult.TEST_RESULT_FAILED:
                return TestStatus.FAIL;

            case TestResult.TEST_RESULT_NOT_EXECUTED:
                return null;

            default:
                throw new IllegalArgumentException("Unknown test result: " + testResult);
        }
    }
}
