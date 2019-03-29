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
package com.android.cts.webkit;

import com.android.ddmlib.testrunner.RemoteAndroidTestRunner;
import com.android.ddmlib.testrunner.TestResult.TestStatus;
import com.android.ddmlib.testrunner.TestRunResult;
import com.android.tradefed.device.DeviceNotAvailableException;
import com.android.tradefed.result.CollectingTestListener;
import com.android.tradefed.testtype.DeviceTestCase;

import java.util.Map;

public class WebViewHostSideStartupTest extends DeviceTestCase {
    private static final String RUNNER = "android.support.test.runner.AndroidJUnitRunner";

    private static final String DEVICE_WEBVIEW_STARTUP_PKG = "com.android.cts.webkit";
    private static final String DEVICE_WEBVIEW_STARTUP_TEST_CLASS = "WebViewDeviceSideStartupTest";
    @Override
    protected void setUp() throws Exception {
        super.setUp();
    }

    public void testCookieManager() throws DeviceNotAvailableException {
        assertTrue(runDeviceTest(DEVICE_WEBVIEW_STARTUP_PKG, DEVICE_WEBVIEW_STARTUP_TEST_CLASS,
                    "testCookieManagerBlockingUiThread"));
    }

    public void testWebViewVersionApiOnUiThread() throws DeviceNotAvailableException {
        assertTrue(runDeviceTest(DEVICE_WEBVIEW_STARTUP_PKG, DEVICE_WEBVIEW_STARTUP_TEST_CLASS,
                    "testGetCurrentWebViewPackageOnUiThread"));
    }

    public void testWebViewVersionApi() throws DeviceNotAvailableException {
        assertTrue(runDeviceTest(DEVICE_WEBVIEW_STARTUP_PKG, DEVICE_WEBVIEW_STARTUP_TEST_CLASS,
                    "testGetCurrentWebViewPackage"));
    }

    public void testStrictMode() throws DeviceNotAvailableException {
        assertTrue(runDeviceTest(DEVICE_WEBVIEW_STARTUP_PKG, DEVICE_WEBVIEW_STARTUP_TEST_CLASS,
                    "testStrictModeNotViolatedOnStartup"));
    }

    private boolean runDeviceTest(String packageName, String testClassName,
            String testMethodName) throws DeviceNotAvailableException {
        testClassName = packageName + "." + testClassName;

        RemoteAndroidTestRunner testRunner = new RemoteAndroidTestRunner(
                packageName, RUNNER, getDevice().getIDevice());
        testRunner.setMethodName(testClassName, testMethodName);

        CollectingTestListener listener = new CollectingTestListener();
        assertTrue(getDevice().runInstrumentationTests(testRunner, listener));

        TestRunResult runResult = listener.getCurrentRunResults();
        return !runResult.hasFailedTests() && runResult.getNumTestsInState(TestStatus.PASSED) > 0;
    }
}
