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

package com.android.cts.net;

import com.android.compatibility.common.tradefed.build.CompatibilityBuildHelper;
import com.android.ddmlib.Log;
import com.android.ddmlib.testrunner.RemoteAndroidTestRunner;
import com.android.ddmlib.testrunner.TestIdentifier;
import com.android.ddmlib.testrunner.TestResult;
import com.android.ddmlib.testrunner.TestResult.TestStatus;
import com.android.ddmlib.testrunner.TestRunResult;
import com.android.tradefed.build.IBuildInfo;
import com.android.tradefed.device.DeviceNotAvailableException;
import com.android.tradefed.result.CollectingTestListener;
import com.android.tradefed.testtype.DeviceTestCase;
import com.android.tradefed.testtype.IAbi;
import com.android.tradefed.testtype.IAbiReceiver;
import com.android.tradefed.testtype.IBuildReceiver;

import java.io.FileNotFoundException;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

abstract class HostsideNetworkTestCase extends DeviceTestCase implements IAbiReceiver,
        IBuildReceiver {
    protected static final boolean DEBUG = false;
    protected static final String TAG = "HostsideNetworkTests";
    protected static final String TEST_PKG = "com.android.cts.net.hostside";
    protected static final String TEST_APK = "CtsHostsideNetworkTestsApp.apk";
    protected static final String TEST_APP2_PKG = "com.android.cts.net.hostside.app2";
    protected static final String TEST_APP2_APK = "CtsHostsideNetworkTestsApp2.apk";

    private IAbi mAbi;
    private IBuildInfo mCtsBuild;

    @Override
    public void setAbi(IAbi abi) {
        mAbi = abi;
    }

    @Override
    public void setBuild(IBuildInfo buildInfo) {
        mCtsBuild = buildInfo;
    }

    @Override
    protected void setUp() throws Exception {
        super.setUp();

        assertNotNull(mAbi);
        assertNotNull(mCtsBuild);

        uninstallPackage(TEST_PKG, false);
        installPackage(TEST_APK);
    }

    @Override
    protected void tearDown() throws Exception {
        super.tearDown();

        uninstallPackage(TEST_PKG, true);
    }

    protected void installPackage(String apk) throws FileNotFoundException,
            DeviceNotAvailableException {
        CompatibilityBuildHelper buildHelper = new CompatibilityBuildHelper(mCtsBuild);
        assertNull(getDevice().installPackage(buildHelper.getTestFile(apk), false));
    }

    protected void uninstallPackage(String packageName, boolean shouldSucceed)
            throws DeviceNotAvailableException {
        final String result = getDevice().uninstallPackage(packageName);
        if (shouldSucceed) {
            assertNull("uninstallPackage(" + packageName + ") failed: " + result, result);
        }
    }

    protected void assertPackageUninstalled(String packageName) throws DeviceNotAvailableException,
            InterruptedException {
        final String command = "cmd package list packages " + packageName;
        final int max_tries = 5;
        for (int i = 1; i <= max_tries; i++) {
            final String result = runCommand(command);
            if (result.trim().isEmpty()) {
                return;
            }
            // 'list packages' filters by substring, so we need to iterate with the results
            // and check one by one, otherwise 'com.android.cts.net.hostside' could return
            // 'com.android.cts.net.hostside.app2'
            boolean found = false;
            for (String line : result.split("[\\r\\n]+")) {
                if (line.endsWith(packageName)) {
                    found = true;
                    break;
                }
            }
            if (!found) {
                return;
            }
            i++;
            Log.v(TAG, "Package " + packageName + " not uninstalled yet (" + result
                    + "); sleeping 1s before polling again");
            Thread.sleep(1000);
        }
        fail("Package '" + packageName + "' not uinstalled after " + max_tries + " seconds");
    }

    protected void runDeviceTests(String packageName, String testClassName)
            throws DeviceNotAvailableException {
        runDeviceTests(packageName, testClassName, null);
    }

    protected void runDeviceTests(String packageName, String testClassName, String methodName)
            throws DeviceNotAvailableException {
        RemoteAndroidTestRunner testRunner = new RemoteAndroidTestRunner(packageName,
                "android.support.test.runner.AndroidJUnitRunner", getDevice().getIDevice());

        if (testClassName != null) {
            if (methodName != null) {
                testRunner.setMethodName(testClassName, methodName);
            } else {
                testRunner.setClassName(testClassName);
            }
        }

        final CollectingTestListener listener = new CollectingTestListener();
        getDevice().runInstrumentationTests(testRunner, listener);

        final TestRunResult result = listener.getCurrentRunResults();
        if (result.isRunFailure()) {
            throw new AssertionError("Failed to successfully run device tests for "
                    + result.getName() + ": " + result.getRunFailureMessage());
        }

        if (result.hasFailedTests()) {
            // build a meaningful error message
            StringBuilder errorBuilder = new StringBuilder("on-device tests failed:\n");
            for (Map.Entry<TestIdentifier, TestResult> resultEntry :
                result.getTestResults().entrySet()) {
                if (!resultEntry.getValue().getStatus().equals(TestStatus.PASSED)) {
                    errorBuilder.append(resultEntry.getKey().toString());
                    errorBuilder.append(":\n");
                    errorBuilder.append(resultEntry.getValue().getStackTrace());
                }
            }
            throw new AssertionError(errorBuilder.toString());
        }
    }

    private static final Pattern UID_PATTERN =
            Pattern.compile(".*userId=([0-9]+)$", Pattern.MULTILINE);

    protected int getUid(String packageName) throws DeviceNotAvailableException {
        final String output = runCommand("dumpsys package " + packageName);
        final Matcher matcher = UID_PATTERN.matcher(output);
        while (matcher.find()) {
            final String match = matcher.group(1);
            return Integer.parseInt(match);
        }
        throw new RuntimeException("Did not find regexp '" + UID_PATTERN + "' on adb output\n"
                + output);
    }

    protected String runCommand(String command) throws DeviceNotAvailableException {
        Log.d(TAG, "Command: '" + command + "'");
        final String output = getDevice().executeShellCommand(command);
        if (DEBUG) Log.v(TAG, "Output: " + output.trim());
        return output;
    }
}
