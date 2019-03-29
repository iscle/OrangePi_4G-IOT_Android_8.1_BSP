/*
 * Copyright (C) 2012 The Android Open Source Project
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

package android.simplecpu.cts;

import android.util.Log;

import com.android.compatibility.common.util.CtsAndroidTestCase;
import com.android.compatibility.common.util.DeviceReportLog;
import com.android.compatibility.common.util.ResultType;
import com.android.compatibility.common.util.ResultUnit;
import com.android.compatibility.common.util.Stat;

/**
 * Very simple CPU benchmarking to check the basic capability of CPU.
 * Cases include
 *   qsort
 *   matrix multiplication (for floating point performance)
 */
public class SimpleCpuTest extends CtsAndroidTestCase {
    private static final String TAG = "BandwidthTest";
    private static final int KB = 1024;
    private static final int MB = 1024 * 1024;
    private static final int NUMBER_REPEAT = 20;
    // reject data outside +/- this value * median
    private static final double OUTLIER_THRESHOLD = 0.1;
    private static final String REPORT_LOG_NAME = "CtsSimpleCpuTestCases";

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        warmUpCpu();
    }

    public void testSort004KB() {
        doTestSort(NUMBER_REPEAT, 4 * KB);
    }

    public void testSort128KB() {
        doTestSort(NUMBER_REPEAT, 128 * KB);
    }

    public void testSort001MB() {
        doTestSort(NUMBER_REPEAT, 1 * MB);
    }

    // will fit into L1
    public void testMatrixMultiplication032() {
        doMatrixMultiplication(NUMBER_REPEAT, 32);
    }

    // mostly fit into L2
    public void testMatrixMultiplication128() {
        doMatrixMultiplication(NUMBER_REPEAT, 128);
    }

    // may fit into L2
    public void testMatrixMultiplication200() {
        doMatrixMultiplication(NUMBER_REPEAT, 200);
    }

    public void testMatrixMultiplication400() {
        doMatrixMultiplication(NUMBER_REPEAT, 400);
    }

    // will exceed L2
    public void testMatrixMultiplication600() {
        doMatrixMultiplication(NUMBER_REPEAT, 600);
    }

    /**
     * run some code to force full CPU freq.
     */
    private void warmUpCpu() {
        CpuNative.runSort(1 * MB, 10);
    }

    /**
     * qsort test
     * @param numberRepeat
     * @param arrayLength
     */
    private void doTestSort(int numberRepeat, int arrayLength) {
        final int numberRepeatInEachCall = 10;
        double[] result = new double[numberRepeat];
        for (int i = 0; i < numberRepeat; i++) {
            result[i] = CpuNative.runSort(arrayLength, numberRepeatInEachCall);
        }
        String streamName = "do_test_sort";
        DeviceReportLog report = new DeviceReportLog(REPORT_LOG_NAME, streamName);
        report.addValue("array_length", arrayLength, ResultType.NEUTRAL, ResultUnit.NONE);
        report.addValues("sorting_time", result, ResultType.LOWER_BETTER, ResultUnit.MS);
        Stat.StatResult stat = Stat.getStatWithOutlierRejection(result, OUTLIER_THRESHOLD);
        if (stat.mDataCount != result.length) {
            Log.w(TAG, "rejecting " + (result.length - stat.mDataCount) + " outliers");
        }
        report.setSummary("sorting_time_average", stat.mAverage, ResultType.LOWER_BETTER, ResultUnit.MS);
        report.submit(getInstrumentation());
    }

    /**
     * Matrix multiplication test, nxn matrix multiplication
     * @param numberRepeat
     * @param n should be multiple of 8
     */
    private void doMatrixMultiplication(int numberRepeat, int n) {
        assertTrue(n % 8 == 0);
        final int numberRepeatInEachCall = 10;
        double[] result = new double[numberRepeat];
        for (int i = 0; i < numberRepeat; i++) {
            result[i] = CpuNative.runMatrixMultiplication(n, numberRepeatInEachCall);
        }
        String streamName = "do_matrix_multiplication";
        DeviceReportLog report = new DeviceReportLog(REPORT_LOG_NAME, streamName);
        report.addValue("matrix_dimension", n, ResultType.NEUTRAL, ResultUnit.NONE);
        report.addValues("matrix_mutiplication_time", result, ResultType.LOWER_BETTER,
                ResultUnit.MS);
        Stat.StatResult stat = Stat.getStatWithOutlierRejection(result, OUTLIER_THRESHOLD);
        if (stat.mDataCount != result.length) {
            Log.w(TAG, "rejecting " + (result.length - stat.mDataCount) + " outliers");
        }
        report.setSummary("matrix_mutiplication_time_average", stat.mAverage,
                ResultType.LOWER_BETTER, ResultUnit.MS);
        report.submit(getInstrumentation());
    }

}
