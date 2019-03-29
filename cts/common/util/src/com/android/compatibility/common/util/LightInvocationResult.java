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
package com.android.compatibility.common.util;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Data structure for storing finalized Compatibility test results with minimum memory.
 * This implementation stores only enough ModuleResult information to return empty modules
 * of the correct ids (names and abis) upon {@link IInvocationResult}'s getModules() method.
 */
public class LightInvocationResult implements IInvocationResult {

    private long mTimestamp;
    private Map<String, String> mInvocationInfo;
    private Set<String> mSerials;
    private String mBuildFingerprint;
    private String mTestPlan;
    private String mCommandLineArgs;
    private int mNotExecuted;
    private int mModuleCompleteCount;
    private RetryChecksumStatus mRetryChecksumStatus;
    private File mRetryDirectory;
    private Set<String> mModuleIds;
    private Map<TestStatus, Integer> mResultCounts;

    /**
     * Constructor that takes a reference to an existing result (light or complete) and
     * initializes instance variables accordingly. This class must NOT save any reference to the
     * result param to remain lightweight.
     */
    public LightInvocationResult(IInvocationResult result) {
        mTimestamp = result.getStartTime();
        mInvocationInfo = new HashMap<String, String>(result.getInvocationInfo());
        mSerials = new HashSet<String>(result.getDeviceSerials());
        mBuildFingerprint = result.getBuildFingerprint();
        mTestPlan = result.getTestPlan();
        mCommandLineArgs = result.getCommandLineArgs();
        mNotExecuted = result.getNotExecuted();
        mModuleCompleteCount = result.getModuleCompleteCount();
        mRetryChecksumStatus = RetryChecksumStatus.NotRetry;
        mRetryDirectory = result.getRetryDirectory();
        mModuleIds = new HashSet<String>();
        for (IModuleResult module : result.getModules()) {
            mModuleIds.add(module.getId());
        }
        mResultCounts = new HashMap<TestStatus, Integer>();
        for (TestStatus status : TestStatus.values()) {
            mResultCounts.put(status, result.countResults(status));
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public List<IModuleResult> getModules() {
        List<IModuleResult> modules = new ArrayList<IModuleResult>();
        for (String id : mModuleIds) {
            modules.add(new ModuleResult(id));
        }
        return modules; // return empty modules
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public int countResults(TestStatus result) {
        return mResultCounts.get(result);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public int getNotExecuted() {
        return mNotExecuted;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public IModuleResult getOrCreateModule(String id) {
        mModuleIds.add(id);
        return new ModuleResult(id);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void mergeModuleResult(IModuleResult moduleResult) {
        mModuleIds.add(moduleResult.getId());
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void addInvocationInfo(String key, String value) {
        mInvocationInfo.put(key, value);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Map<String, String> getInvocationInfo() {
        return mInvocationInfo;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void setStartTime(long time) {
        mTimestamp = time;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public long getStartTime() {
        return mTimestamp;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void setTestPlan(String plan) {
        mTestPlan = plan;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String getTestPlan() {
        return mTestPlan;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void addDeviceSerial(String serial) {
        mSerials.add(serial);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Set<String> getDeviceSerials() {
        return mSerials;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void setCommandLineArgs(String commandLineArgs) {
        mCommandLineArgs = commandLineArgs;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String getCommandLineArgs() {
        return mCommandLineArgs;
    }

    @Override
    public void setBuildFingerprint(String buildFingerprint) {
        mBuildFingerprint = buildFingerprint;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String getBuildFingerprint() {
        return mBuildFingerprint;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public int getModuleCompleteCount() {
        return mModuleCompleteCount;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public RetryChecksumStatus getRetryChecksumStatus() {
        return mRetryChecksumStatus;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void setRetryChecksumStatus(RetryChecksumStatus retryStatus) {
        mRetryChecksumStatus = retryStatus;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public File getRetryDirectory() {
        return mRetryDirectory;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void setRetryDirectory(File resultDir) {
        mRetryDirectory = resultDir;
    }
}
