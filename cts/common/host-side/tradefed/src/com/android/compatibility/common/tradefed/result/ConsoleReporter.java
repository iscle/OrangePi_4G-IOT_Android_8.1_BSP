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

package com.android.compatibility.common.tradefed.result;

import com.android.ddmlib.Log.LogLevel;
import com.android.ddmlib.testrunner.TestIdentifier;
import com.android.tradefed.build.IBuildInfo;
import com.android.tradefed.config.Option;
import com.android.tradefed.config.OptionCopier;
import com.android.tradefed.invoker.IInvocationContext;
import com.android.tradefed.log.LogUtil.CLog;
import com.android.tradefed.result.IShardableListener;
import com.android.tradefed.util.TimeUtil;

import java.util.Map;

/**
 * Write test progress to the test console.
 */
public class ConsoleReporter implements IShardableListener {

    private static final String UNKNOWN_DEVICE = "unknown_device";

    @Option(name = "quiet-output", description = "Mute display of test results.")
    private boolean mQuietOutput = false;

    private String mDeviceSerial = UNKNOWN_DEVICE;
    private boolean mTestFailed;
    private String mModuleId;
    private int mCurrentTestNum;
    private int mTotalTestsInModule;
    private int mPassedTests;
    private int mFailedTests;
    private int mNotExecutedTests;

    /**
     * {@inheritDoc}
     */
    @Override
    public void invocationStarted(IInvocationContext context) {
        if (context == null) {
            CLog.w("InvocationContext should not be null");
            return;
        }
        IBuildInfo primaryBuild = context.getBuildInfos().get(0);

        // Escape any "%" signs in the device serial.
        mDeviceSerial = primaryBuild.getDeviceSerial().replace("%", "%%");
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void testRunStarted(String id, int numTests) {
        boolean isRepeatModule = (mModuleId != null && mModuleId.equals(id));
        mModuleId = id;
        mTotalTestsInModule = numTests;
        // Reset counters
        mCurrentTestNum = 0;
        mPassedTests = 0;
        mFailedTests = 0;
        mNotExecutedTests = 0;
        mTestFailed = false;
        logMessage("%s %s with %d test%s", (isRepeatModule) ? "Continuing" : "Starting", id,
                mTotalTestsInModule, (mTotalTestsInModule > 1) ? "s" : "");
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void testStarted(TestIdentifier test) {
        mTestFailed = false;
        mCurrentTestNum++;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void testFailed(TestIdentifier test, String trace) {
        logProgress("%s fail: %s", test, trace);
        mTestFailed = true;
        mFailedTests++;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void testIgnored(TestIdentifier test) {
        mCurrentTestNum--;
        logProgress("%s ignore", test);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void testAssumptionFailure(TestIdentifier test, String trace) {
        logProgress("%s skip", test);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void testEnded(TestIdentifier test, Map<String, String> testMetrics) {
        if (!mTestFailed) {
            logProgress("%s pass", test);
            mPassedTests++;
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void testRunFailed(String errorMessage) {
        logMessage(errorMessage);
    }


    /**
     * {@inheritDoc}
     */
    @Override
    public void testRunEnded(long elapsedTime, Map<String, String> metrics) {
        mNotExecutedTests = Math.max(mTotalTestsInModule - mCurrentTestNum, 0);
        String status = mNotExecutedTests > 0 ? "failed" : "completed";
        logMessage("%s %s in %s. %d passed, %d failed, %d not executed",
            mModuleId,
            status,
            TimeUtil.formatElapsedTime(elapsedTime),
            mPassedTests,
            mFailedTests,
            mNotExecutedTests);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void testRunStopped(long elapsedTime) {
        logMessage("%s stopped (%s)", mModuleId, TimeUtil.formatElapsedTime(elapsedTime));
    }

    /**
     * Print out message with test execution status.
     */
    private void logProgress(String format, Object... args) {
        format = String.format("[%s %s %s] %s", progress(), mModuleId, mDeviceSerial, format);
        log(format, args);
    }

    /**
     * Print out message to the console
     */
    private void logMessage(String format, Object... args) {
        format = String.format("[%s] %s", mDeviceSerial, format);
        log(format, args);
    }

    /**
     * Print out to the console or log silently when mQuietOutput is true.
     */
    private void log(String format, Object... args) {
        if (mQuietOutput) {
            CLog.i(format, args);
        } else {
            CLog.logAndDisplay(LogLevel.INFO, format, args);
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public IShardableListener clone() {
        ConsoleReporter clone = new ConsoleReporter();
        OptionCopier.copyOptionsNoThrow(this, clone);
        return clone;
    }

    /**
     * Return a string containing the percentage complete of module test execution.
     */
    private String progress() {
        return String.format("%d/%d", mCurrentTestNum, mTotalTestsInModule);
    }

    String getDeviceSerial() {
        return mDeviceSerial;
    }

    boolean getTestFailed() {
        return mTestFailed;
    }

    String getModuleId() {
        return mModuleId;
    }

    int getCurrentTestNum() {
        return mCurrentTestNum;
    }

    int getTotalTestsInModule() {
        return mTotalTestsInModule;
    }

    int getPassedTests() {
        return mPassedTests;
    }

    int getFailedTests() {
        return mFailedTests;
    }
}
