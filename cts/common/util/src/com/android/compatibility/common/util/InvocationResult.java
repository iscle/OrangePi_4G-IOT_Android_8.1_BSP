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
package com.android.compatibility.common.util;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Data structure for the detailed Compatibility test results.
 */
public class InvocationResult implements IInvocationResult {

    private long mTimestamp;
    private Map<String, IModuleResult> mModuleResults = new LinkedHashMap<>();
    private Map<String, String> mInvocationInfo = new HashMap<>();
    private Set<String> mSerials = new HashSet<>();
    private String mBuildFingerprint;
    private String mTestPlan;
    private String mCommandLineArgs;
    private int mNotExecuted = 0;
    private RetryChecksumStatus mRetryChecksumStatus = RetryChecksumStatus.NotRetry;
    private File mRetryDirectory = null;

    /**
     * {@inheritDoc}
     */
    @Override
    public List<IModuleResult> getModules() {
        ArrayList<IModuleResult> modules = new ArrayList<>(mModuleResults.values());
        Collections.sort(modules);
        return modules;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public int countResults(TestStatus result) {
        int total = 0;
        for (IModuleResult m : mModuleResults.values()) {
            total += m.countResults(result);
        }
        return total;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public int getNotExecuted() {
        int numTests = 0;
        for (IModuleResult module : mModuleResults.values()) {
            numTests += module.getNotExecuted();
        }
        return numTests;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public IModuleResult getOrCreateModule(String id) {
        IModuleResult moduleResult = mModuleResults.get(id);
        if (moduleResult == null) {
            moduleResult = new ModuleResult(id);
            mModuleResults.put(id, moduleResult);
        }
        return moduleResult;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void mergeModuleResult(IModuleResult moduleResult) {
        // Merge the moduleResult with any existing module result
        IModuleResult existingModuleResult = getOrCreateModule(moduleResult.getId());
        existingModuleResult.mergeFrom(moduleResult);
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
        int completeModules = 0;
        for (IModuleResult module : mModuleResults.values()) {
            if (module.isDone()) {
                completeModules++;
            }
        }
        return completeModules;
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
