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
import com.android.compatibility.common.util.MeasureRun;
import com.android.compatibility.common.util.MeasureTime;
import com.android.compatibility.common.util.ResultType;
import com.android.compatibility.common.util.ResultUnit;
import com.android.compatibility.common.util.Stat;

import java.util.Arrays;
import java.util.Random;

/**
 * A simple compatibility test which includes results in the report.
 *
 * This test measures the time taken to run a workload and adds in the report.
 */
public class SampleDeviceResultTest extends ActivityInstrumentationTestCase2<SampleDeviceActivity> {

    /**
     * Name of the report log to store test metrics.
     */
    private static final String REPORT_LOG_NAME = "CtsSampleDeviceTestCases";

    /**
     * The number of times to repeat the test.
     */
    private static final int REPEAT = 5;

    /**
     * A {@link Random} to generate random integers to test the sort.
     */
    private static final Random random = new Random(12345);

    /**
     * Constructor which passes the class of the activity to be instrumented.
     */
    public SampleDeviceResultTest() {
        super(SampleDeviceActivity.class);
    }

    /**
     * Measures the time taken to sort an array.
     */
    public void testSort() throws Exception {
        // MeasureTime runs the workload N times and records the time taken by each run.
        double[] result = MeasureTime.measure(REPEAT, new MeasureRun() {
            /**
             * The size of the array to sort.
             */
            private static final int ARRAY_SIZE = 100000;
            private int[] array;
            @Override
            public void prepare(int i) throws Exception {
                array = createArray(ARRAY_SIZE);
            }
            @Override
            public void run(int i) throws Exception {
                Arrays.sort(array);
                assertTrue("Array not sorted", isSorted(array));
            }
        });
        // Compute the stats.
        Stat.StatResult stat = Stat.getStat(result);
        // Create a new report to hold the metrics.
        String streamName = "test_sort";
        DeviceReportLog reportLog = new DeviceReportLog(REPORT_LOG_NAME, streamName);
        // Add the results to the report.
        reportLog.addValues("times", result, ResultType.LOWER_BETTER, ResultUnit.MS);
        reportLog.addValue("min", stat.mMin, ResultType.LOWER_BETTER, ResultUnit.MS);
        reportLog.addValue("max", stat.mMax, ResultType.LOWER_BETTER, ResultUnit.MS);
        // Set a summary.
        reportLog.setSummary("average", stat.mAverage, ResultType.LOWER_BETTER, ResultUnit.MS);
        // Submit the report to the given instrumentation.
        reportLog.submit(getInstrumentation());
    }

    /**
     * Creates an array filled with random numbers of the given size.
     */
    private static int[] createArray(int size) {
        int[] array = new int[size];
        for (int i = 0; i < size; i++) {
            array[i] = random.nextInt();
        }
        return array;
    }

    /**
     * Tests an array is sorted.
     */
    private static boolean isSorted(int[] array) {
        int len = array.length;
        for (int i = 0, j = 1; j < len; i++, j++) {
            if (array[i] > array[j]) {
                return false;
            }
        }
        return true;
    }
}
