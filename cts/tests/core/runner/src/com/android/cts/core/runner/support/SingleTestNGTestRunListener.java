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

package com.android.cts.core.runner.support;

import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Listener for TestNG runs that provides gtest-like console output.
 *
 * Prints a message like [RUN], [OK], [ERROR], [SKIP] to stdout
 * as tests are being executed with their status.
 *
 * This output is also saved as the device logs (logcat) when the test is run through
 * cts-tradefed.
 */
public class SingleTestNGTestRunListener implements org.testng.ITestListener {
    private int mTestStarted = 0;

    private Map<String, Throwable> failures = new LinkedHashMap<>();

    private static class Prefixes {
        @SuppressWarnings("unused")
        private static final String INFORMATIONAL_MARKER =  "[----------]";
        private static final String START_TEST_MARKER =     "[ RUN      ]";
        private static final String OK_TEST_MARKER =        "[       OK ]";
        private static final String ERROR_TEST_RUN_MARKER = "[    ERROR ]";
        private static final String SKIPPED_TEST_MARKER =   "[     SKIP ]";
        private static final String TEST_RUN_MARKER =       "[==========]";
    }

    // How many tests did TestNG *actually* try to run?
    public int getNumTestStarted() {
      return mTestStarted;
    }

    public Map<String, Throwable> getFailures() {
        return Collections.unmodifiableMap(failures);
    }

    @Override
    public void onFinish(org.testng.ITestContext context) {
        System.out.println(String.format("%s", Prefixes.TEST_RUN_MARKER));
    }

    @Override
    public void onStart(org.testng.ITestContext context) {
        System.out.println(String.format("%s", Prefixes.INFORMATIONAL_MARKER));
    }

    @Override
    public void onTestFailedButWithinSuccessPercentage(org.testng.ITestResult result) {
        onTestFailure(result);
    }

    @Override
    public void onTestFailure(org.testng.ITestResult result) {
        // All failures are coalesced into one '[ FAILED ]' message at the end
        // This is because a single test method can run multiple times with different parameters.
        // Since we only test a single method, it's safe to combine all failures into one
        // failure at the end.
        //
        // The big pass/fail is printed from SingleTestNGTestRunner, not from the listener.
        String id = getId(result);
        Throwable throwable = result.getThrowable();
        System.out.println(String.format("%s %s ::: %s", Prefixes.ERROR_TEST_RUN_MARKER,
                id, stringify(throwable)));
        failures.put(id, throwable);
    }

    @Override
    public void onTestSkipped(org.testng.ITestResult result) {
        System.out.println(String.format("%s %s", Prefixes.SKIPPED_TEST_MARKER,
              getId(result)));
    }

    @Override
    public void onTestStart(org.testng.ITestResult result) {
        mTestStarted++;
        System.out.println(String.format("%s %s", Prefixes.START_TEST_MARKER,
              getId(result)));
    }

    @Override
    public void onTestSuccess(org.testng.ITestResult result) {
        System.out.println(String.format("%s", Prefixes.OK_TEST_MARKER));
    }

    private String getId(org.testng.ITestResult test) {
        // TestNG is quite complicated since tests can have arbitrary parameters.
        // Use its code to stringify a result name instead of doing it ourselves.

        org.testng.remote.strprotocol.TestResultMessage msg =
                new org.testng.remote.strprotocol.TestResultMessage(
                    null, /*suite name*/
                    null, /*test name -- display the test method name instead */
                    test);

        String className = test.getTestClass().getName();
        //String name = test.getMethod().getMethodName();
        return String.format("%s#%s", className, msg.toDisplayString());

    }

    private String stringify(Throwable error) {
        return Arrays.toString(error.getStackTrace()).replaceAll("\n", " ");
    }
}
