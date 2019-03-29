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
 * limitations under the License.
 */
package android.sample.cts;

import android.sample.SampleDeviceActivity;
import android.test.ActivityInstrumentationTestCase2;

import com.android.compatibility.common.util.DeviceReportLog;
import com.android.compatibility.common.util.ResultType;
import com.android.compatibility.common.util.ResultUnit;

/**
 * A simple compatibility test which includes results in the report.
 *
 * This class has 3 dummy tests that create report logs and log dummy metrics.
 */
public class SampleDeviceReportLogTest
        extends ActivityInstrumentationTestCase2<SampleDeviceActivity> {

    /**
     * Name of the report log. Test metrics will be written out to ths report. The name must match
     * the test module name.
     */
    private static final String REPORT_LOG_NAME = "CtsSampleDeviceTestCases";

    /**
     * Sample numbers used by the sample tests.
     */
    private static final int MULTIPLICATION_NUMBER_1 = 23;
    private static final int MULTIPLICATION_NUMBER_2 = 97;
    private static final int MULTIPLICATION_RESULT = 2231;
    private static final int COUNT_START = 1;
    private static final int COUNT_END = 1000;

    private static final String EXPECTED_PRODUCT_TAG = "expected_product";
    private static final String ACTUAL_PRODUCT_TAG = "actual_product";
    private static final String START_TAG = "count_start";
    private static final String END_TAG = "actual_end";

    /**
     * Constructor which passes the class of the activity to be instrumented.
     */
    public SampleDeviceReportLogTest() {
        super(SampleDeviceActivity.class);
    }

    /**
     * Sample test that creates and logs test metrics into a report log.
     */
    public void testMultiplication() {
        // Perform test.
        int product = MULTIPLICATION_NUMBER_1 * MULTIPLICATION_NUMBER_2;
        assertTrue("Multiplication result do not match", product == MULTIPLICATION_RESULT);

        // Log metrics from the test.
        String streamName = "test_multiplication";
        DeviceReportLog reportLog = new DeviceReportLog(REPORT_LOG_NAME, streamName);
        reportLog.addValue(EXPECTED_PRODUCT_TAG, 1.0 * MULTIPLICATION_RESULT, ResultType.NEUTRAL,
                ResultUnit.NONE);
        reportLog.addValue(ACTUAL_PRODUCT_TAG, 1.0 * product, ResultType.NEUTRAL, ResultUnit.NONE);
        reportLog.setSummary(ACTUAL_PRODUCT_TAG, 1.0 * product, ResultType.NEUTRAL, ResultUnit.NONE);
        reportLog.submit(getInstrumentation());
    }

    /**
     * Sample test to check counting up.
     */
    public void testCountUp() {
        String streamName = "test_count_up";
        countHelper(1, streamName);
    }

    /**
     * Sample test to check counting down.
     */
    public void testCountDown() {
        String streamName = "test_count_down";
        countHelper(2, streamName);
    }

    /**
     * Sample test function that counts up or down based on test parameter. It creates and logs test
     * metrics into a report log.
     * @param testParameter {@link String} parameter passed by caller test function.
     * @param streamName {@link String} name of the report log stream retrieved from dynamic config.
     */
    private void countHelper(int testParameter, String streamName) {
        // Perform test.
        int start;
        int end;
        if (testParameter == 1) {
            start = COUNT_START;
            end = COUNT_END;
            for (int i = start; i <= end;) {
                i++;
            }
        } else {
            start = COUNT_END;
            end = COUNT_START;
            for (int i = start; i >= end;) {
                i--;
            }
        }

        // Log metrics.
        DeviceReportLog reportLog = new DeviceReportLog(REPORT_LOG_NAME, streamName);
        reportLog.addValue(START_TAG, 1.0 * start, ResultType.NEUTRAL, ResultUnit.NONE);
        reportLog.addValue(END_TAG, 1.0 * end, ResultType.NEUTRAL, ResultUnit.NONE);
        reportLog.setSummary(END_TAG, 1.0 * end, ResultType.NEUTRAL, ResultUnit.NONE);
        reportLog.submit(getInstrumentation());
    }
}
