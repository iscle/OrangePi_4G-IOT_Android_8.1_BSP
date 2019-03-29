/*
 * Copyright (C) 2017 The Android Open Source Project
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

package android.media.cts.bitstreams;

import com.android.ddmlib.testrunner.TestIdentifier;
import com.android.tradefed.device.DeviceNotAvailableException;
import com.android.tradefed.device.ITestDevice;
import com.android.tradefed.log.LogUtil.CLog;
import com.android.tradefed.result.ITestInvocationListener;
import com.android.tradefed.testtype.AndroidJUnitTest;
import com.android.tradefed.util.FileUtil;
import java.io.File;
import java.io.IOException;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;

/**
 * A {@link ReportProcessor} installs {@code CtsMediaBitstreamsDeviceSideTestApp.apk}
 * onto a test device, invokes a report-generating test, then pulls and processes
 * the generated report.
 */
abstract class ReportProcessor {

    private final Map<String, String> mMetrics = new HashMap<>();
    private String mFailureStackTrace = null;

    private static final String APP_CLS_NAME = "MediaBitstreamsDeviceSideTest";
    private static final String APP_PKG_NAME = "android.media.cts.bitstreams.app";

    /**
     * Setup {@code device} before test.
     *
     * @param device device under test
     * @throws DeviceNotAvailableException
     * @throws IOException
     */
    void setUp(ITestDevice device) throws DeviceNotAvailableException, IOException {}

    Map<String, String> getArgs() {
        return Collections.emptyMap();
    }

    /**
     * Process test report.
     *
     * @param device device under test
     * @param reportPath path to test report on {@code device}
     * @throws DeviceNotAvailableException
     * @throws IOException
     */
    void process(ITestDevice device, String reportPath)
            throws DeviceNotAvailableException, IOException {}

    /**
     * Attempt to recover from a crash during test on {@code device}.
     *
     * @param device device under test
     * @param reportPath path to test report on {@code device}
     * @throws DeviceNotAvailableException
     * @throws IOException
     * @return true if successfully recovered from test crash, false otherwise
     */
    boolean recover(ITestDevice device, String reportPath)
            throws DeviceNotAvailableException, IOException {
        return false;
    }

    /**
     * Cleanup {@code device} after test
     * @param device device under test
     * @param reportPath path to test report on {@code device}
     */
    void cleanup(ITestDevice device, String reportPath) {
        try {
            device.executeShellCommand(String.format("rm %s", reportPath));
        } catch (DeviceNotAvailableException e) {
            CLog.e(e);
        }
    }

    /**
     * @param device device under test
     * @param reportPath path to test report on {@code device}
     * @return array of lines in report, sans newline
     * @throws DeviceNotAvailableException
     */
    static String[] getReportLines(ITestDevice device, String reportPath)
            throws DeviceNotAvailableException {
        File reportFile = device.pullFile(reportPath);
        try {
            return FileUtil.readStringFromFile(reportFile).split("\n");
        } catch (IOException e) {
            CLog.w(e);
            return new String[0];
        } finally {
            reportFile.delete();
        }
    }

    /* Special listener for setting MediaPreparer instance variable values */
    private class MediaBitstreamsListener implements ITestInvocationListener {

        @Override
        public void testEnded(TestIdentifier test, Map<String, String> metrics) {
            mMetrics.putAll(metrics);
        }

        @Override
        public void testFailed(TestIdentifier test, String trace) {
            mFailureStackTrace = trace;
        }

    }

    private boolean runDeviceTest(
            ITestDevice device, String method, String reportKey, int testTimeout,
            long shellTimeout)
            throws DeviceNotAvailableException {

        String fullTestName = String.format("%s.%s#%s", APP_PKG_NAME, APP_CLS_NAME, method);
        AndroidJUnitTest instrTest = new AndroidJUnitTest();
        instrTest.setDevice(device);
        instrTest.setPackageName(APP_PKG_NAME);
        instrTest.addIncludeFilter(fullTestName);
        instrTest.setTestTimeout(testTimeout);
        instrTest.setShellTimeout(shellTimeout);
        for (Entry<String, String> e : getArgs().entrySet()) {
            instrTest.addInstrumentationArg(e.getKey(), e.getValue());
        }
        instrTest.run(new MediaBitstreamsListener());

        return checkFile(reportKey);

    }

    private boolean checkFile(String reportKey) {
        if (mFailureStackTrace != null) {
            CLog.w("Retrieving bitstreams formats failed with trace:\n%s", mFailureStackTrace);
            mFailureStackTrace = null;
            return false;
        } else if (!mMetrics.containsKey(reportKey)) {
            CLog.w("Failed to generate file key=%s on device", reportKey);
            return false;
        }
        return true;
    }

    void processDeviceReport(
            ITestDevice device, String method, String reportKey)
            throws DeviceNotAvailableException, IOException {
        try {
            setUp(device);
            while (!runDeviceTest(device, method, reportKey, 0, 0)) {
                if (!recover(device, mMetrics.get(reportKey))) {
                    return;
                }
            }
            process(device, mMetrics.get(reportKey));
        } finally {
            cleanup(device, mMetrics.get(reportKey));
        }
    }

}
