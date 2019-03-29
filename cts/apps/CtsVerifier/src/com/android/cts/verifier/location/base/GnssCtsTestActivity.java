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
 * limitations under the License
 */

package com.android.cts.verifier.location.base;

import android.location.cts.GnssTestCase;
import android.location.cts.MultiConstellationNotSupportedException;
import android.view.WindowManager;

import com.android.cts.verifier.R;
import com.android.cts.verifier.location.reporting.GnssTestDetails;

import junit.framework.Test;
import junit.framework.TestSuite;

import org.junit.internal.runners.JUnit38ClassRunner;
import org.junit.internal.runners.SuiteMethod;
import org.junit.runner.Computer;
import org.junit.runner.Description;
import org.junit.runner.JUnitCore;
import org.junit.runner.Request;
import org.junit.runner.Result;
import org.junit.runner.Runner;
import org.junit.runner.notification.Failure;
import org.junit.runner.notification.RunListener;
import org.junit.runners.model.RunnerBuilder;

import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * An Activity that allows Gnss CTS tests to be executed inside CtsVerifier.
 *
 * Sub-classes pass the test class as part of construction.
 * One JUnit test class is executed per Activity, the test class can still be executed outside
 * CtsVerifier.
 */
public class GnssCtsTestActivity extends BaseGnssTestActivity {

    /**
     * Constructor for a CTS test executor. It will execute a standalone CTS test class.
     *
     * @param testClass The test class to execute, it must be a subclass of {@link AndroidTestCase}.
     */
    protected GnssCtsTestActivity(Class<? extends GnssTestCase> testClass) {
        super(testClass);
    }

    @Override
    protected void activitySetUp() throws InterruptedException {
        waitForUserToBegin();
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                mTextView.setText("");
            }
        });
    }

    @Override
    protected void activityCleanUp() {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                getWindow().addFlags(WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD);
            }
        });
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
    }

    /**
     * For reference on the implementation of this test executor see:
     *      android.support.test.runner.AndroidJUnitRunner
     */
    @Override
    protected GnssTestDetails executeTests() {
        JUnitCore testRunner = new JUnitCore();
        testRunner.addListener(new GnssRunListener());

        Computer computer = new Computer();
        RunnerBuilder runnerBuilder = new GnssRunnerBuilder();

        Runner runner;
        try {
            runner = computer.getSuite(runnerBuilder, new Class[]{ mTestClass });
        } catch (Exception e) {
            return new GnssTestDetails(
                    getTestClassName(),
                    GnssTestDetails.ResultCode.FAIL,
                    "[JUnit Initialization]" + e.getMessage());
        }

        Request request = Request.runner(runner);
        Result result = testRunner.run(request);
        // Handle MultiConstellationNotSupportedException warning: If there is a
        // "MultiConstellationNotSupportedException" then it will just print the warning and
        // mark test as pass.
        int failureCount = result.getFailureCount();
        List<Failure> failures = result.getFailures();
        for (Failure f: failures) {
            // TODO: Refactor this to use a more general exception instead of 
            // MultiConstellationNotSupportedException.
            if (f.getException() instanceof MultiConstellationNotSupportedException) {
                failureCount = failureCount - 1;
                int passCount = result.getRunCount() - failureCount - result.getIgnoreCount();
                return new GnssTestDetails(
                        getApplicationContext(), getClass().getName(), passCount,
                        result.getIgnoreCount(), failureCount);
            }
        }

        return new GnssTestDetails(getApplicationContext(), getClass().getName(), result);
    }

    /**
     * A {@link RunnerBuilder} that is used to inject during execution a {@link GnssCtsTestSuite}.
     */
    private class GnssRunnerBuilder extends RunnerBuilder {
        @Override
        public Runner runnerForClass(Class<?> testClass) throws Throwable {
            TestSuite testSuite;
            if (hasSuiteMethod(testClass)) {
                Test test = SuiteMethod.testFromSuiteMethod(testClass);
                if (test instanceof TestSuite) {
                    testSuite = (TestSuite) test;
                } else {
                    throw new IllegalArgumentException(
                            testClass.getName() + "#suite() did not return a TestSuite.");
                }
            } else {
                testSuite = new TestSuite(testClass);
            }
            GnssCtsTestSuite gnssTestSuite =
                    new GnssCtsTestSuite(getApplicationContext(), testSuite);
            return new JUnit38ClassRunner(gnssTestSuite);
        }

        private boolean hasSuiteMethod(Class<?> testClass) {
            try {
                testClass.getMethod("suite");
                return true;
            } catch (NoSuchMethodException e) {
                return false;
            }
        }
    }

    /**
     * Dummy {@link RunListener}.
     * It is only used to handle logging into the UI.
     */
    private class GnssRunListener extends RunListener {
        private volatile boolean mCurrentTestReported;
        private StringBuilder mTestsResults = new StringBuilder("Test summary:\n");
        private int mPassTestCase = 0;
        private int mFailTestCase = 0;

        public void testRunStarted(Description description) throws Exception {
            // nothing to log
        }

        public void testRunFinished(Result result) throws Exception {
            // nothing to log
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    int totalTestCase = mPassTestCase + mFailTestCase;
                    mTestsResults.append(String.format("\n\n %d/%d verification passed.",
                            mPassTestCase, totalTestCase));
                    if (mFailTestCase == 0) {
                        mTestsResults.append(" All test pass!");
                    } else {
                        mTestsResults.append("\n\n" + mTextView.getResources().getString(
                                R.string.location_gnss_test_retry_info) + "\n");
                    }
                    mTextView.setText(mTestsResults);
                }
            });
            vibrate((int)TimeUnit.SECONDS.toMillis(2));
            playSound();
        }

        public void testStarted(Description description) throws Exception {
            mCurrentTestReported = false;
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    mTextView.append("\n Running test: " + description.getMethodName());
                }
            });
        }

        public void testFinished(Description description) throws Exception {
            if (!mCurrentTestReported) {
                mPassTestCase++;
                appendTestDetail("\n Test passed: " + description.getMethodName());
                mTestsResults.append("\n Test passed: " + description.getMethodName());
            }
        }

        public void testFailure(Failure failure) throws Exception {
            mCurrentTestReported = true;
            if (failure.getException() instanceof MultiConstellationNotSupportedException) {
                // append warning for MultiConstellationNotSupportedException's.
                mTestsResults.append(failure.getException());
            } else {
                mFailTestCase++;
                mTestsResults.append("\n Test failed: "
                        + failure.getDescription().getMethodName()
                        + "\n\n Error: " + failure.toString() + "\n");
            }
        }

        public void testAssumptionFailure(Failure failure) {
            mCurrentTestReported = true;
        }

        public void testIgnored(Description description) throws Exception {
            mCurrentTestReported = true;
        }

        private void appendTestDetail(final String testDetail) {
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    mTextView.append(testDetail);
                }
            });
        }
    }
}
