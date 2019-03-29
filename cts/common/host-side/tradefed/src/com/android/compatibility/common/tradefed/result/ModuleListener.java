/*
 * Copyright (C) 2015 The Android Open Source Project
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

import com.android.compatibility.common.tradefed.testtype.IModuleDef;
import com.android.ddmlib.testrunner.TestIdentifier;
import com.android.tradefed.invoker.IInvocationContext;
import com.android.tradefed.log.LogUtil.CLog;
import com.android.tradefed.result.ITestInvocationListener;
import com.android.tradefed.result.InputStreamSource;
import com.android.tradefed.result.LogDataType;
import com.android.tradefed.result.TestSummary;

import java.util.Map;

/**
 * Listener for Compatibility test info.
 * <p>
 * This listener wraps around the normal listener to convert from module name to module id.
 */
public class ModuleListener implements IModuleListener {

    private IModuleDef mModule;
    private ITestInvocationListener mListener;

    /**
     * @param module
     * @param listener
     */
    public ModuleListener(IModuleDef module, ITestInvocationListener listener) {
        mModule = module;
        mListener = listener;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void invocationStarted(IInvocationContext context) {
        CLog.d("ModuleListener.invocationStarted(%s)", context.getBuildInfos());
        mListener.invocationStarted(context);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void testRunStarted(String name, int numTests) {
        CLog.d("ModuleListener.testRunStarted(%s, %d)", name, numTests);
        mListener.testRunStarted(mModule.getId(), numTests);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void testStarted(TestIdentifier test) {
        CLog.d("ModuleListener.testStarted(%s)", test.toString());
        mListener.testStarted(test);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void testEnded(TestIdentifier test, Map<String, String> metrics) {
        CLog.d("ModuleListener.testEnded(%s, %s)", test.toString(), metrics.toString());
        mListener.testEnded(test, metrics);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void testIgnored(TestIdentifier test) {
        CLog.d("ModuleListener.testIgnored(%s)", test.toString());
        mListener.testIgnored(test);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void testFailed(TestIdentifier test, String trace) {
        CLog.d("ModuleListener.testFailed(%s, %s)", test.toString(), trace);
        mListener.testFailed(test, trace);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void testAssumptionFailure(TestIdentifier test, String trace) {
        CLog.d("ModuleListener.testAssumptionFailure(%s, %s)", test.toString(), trace);
        mListener.testAssumptionFailure(test, trace);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void testRunStopped(long elapsedTime) {
        CLog.d("ModuleListener.testRunStopped(%d)", elapsedTime);
        mListener.testRunStopped(elapsedTime);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void testRunEnded(long elapsedTime, Map<String, String> metrics) {
        CLog.d("ModuleListener.testRunEnded(%d, %s)", elapsedTime, metrics.toString());
        mListener.testRunEnded(elapsedTime, metrics);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void testRunFailed(String errorMessage) {
        CLog.d("ModuleListener.testRunFailed(%s)", errorMessage);
        mListener.testRunFailed(errorMessage);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public TestSummary getSummary() {
        return mListener.getSummary();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void invocationEnded(long elapsedTime) {
        CLog.d("ModuleListener.invocationEnded(%d)", elapsedTime);
        mListener.invocationEnded(elapsedTime);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void invocationFailed(Throwable cause) {
        CLog.d("ModuleListener.invocationFailed(%s)", cause.toString());
        mListener.invocationFailed(cause);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void testLog(String name, LogDataType type, InputStreamSource stream) {
        CLog.d("ModuleListener.testLog(%s, %s, %s)", name, type.toString(), stream.toString());
        mListener.testLog(name, type, stream);
    }

}
