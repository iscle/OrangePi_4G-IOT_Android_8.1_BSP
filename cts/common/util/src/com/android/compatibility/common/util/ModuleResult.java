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

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Data structure for a Compatibility test module result.
 */
public class ModuleResult implements IModuleResult {

    private String mId;
    private long mRuntime = 0;

    /* Variables related to completion of the module */
    private boolean mDone = false;
    private boolean mHaveSetDone = false;
    private boolean mInProgress = false;
    private int mExpectedTestRuns = 0;
    private int mActualTestRuns = 0;
    private int mNotExecuted = 0;

    private Map<String, ICaseResult> mResults = new HashMap<>();

    /**
     * Creates a {@link ModuleResult} for the given id, created with
     * {@link AbiUtils#createId(String, String)}
     */
    public ModuleResult(String id) {
        mId = id;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean isDone() {
        return mDone && !mInProgress && (mActualTestRuns >= mExpectedTestRuns);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean isDoneSoFar() {
        return mDone && !mInProgress;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void initializeDone(boolean done) {
        mDone = done;
        mHaveSetDone = false;
        if (mDone) {
            mNotExecuted = 0;
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void setDone(boolean done) {
        if (mHaveSetDone) {
            mDone &= done; // If we've already set done for this instance, AND the received value
        } else {
            mDone = done; // If done has only been initialized, overwrite the existing value
        }
        mHaveSetDone = true;
        if (mDone) {
            mNotExecuted = 0;
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void inProgress(boolean inProgress) {
        mInProgress = inProgress;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public int getExpectedTestRuns() {
        return mExpectedTestRuns;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void setExpectedTestRuns(int numRuns) {
        mExpectedTestRuns = numRuns;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public int getTestRuns() {
        return mActualTestRuns;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void addTestRun() {
        mActualTestRuns++;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void resetTestRuns() {
        mActualTestRuns = 0;
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
    public void setNotExecuted(int numTests) {
        mNotExecuted = numTests;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String getId() {
        return mId;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String getName() {
        return AbiUtils.parseTestName(mId);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String getAbi() {
        return AbiUtils.parseAbi(mId);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void addRuntime(long elapsedTime) {
        mRuntime += elapsedTime;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void resetRuntime() {
        mRuntime = 0;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public long getRuntime() {
        return mRuntime;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public ICaseResult getOrCreateResult(String caseName) {
        ICaseResult result = mResults.get(caseName);
        if (result == null) {
            result = new CaseResult(caseName);
            mResults.put(caseName, result);
        }
        return result;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public ICaseResult getResult(String caseName) {
        return mResults.get(caseName);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public List<ICaseResult> getResults() {
        ArrayList<ICaseResult> results = new ArrayList<>(mResults.values());
        Collections.sort(results);
        return results;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public int countResults(TestStatus status) {
        int total = 0;
        for (ICaseResult result : mResults.values()) {
            total += result.countResults(status);
        }
        return total;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public int compareTo(IModuleResult another) {
        return getId().compareTo(another.getId());
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void mergeFrom(IModuleResult otherModuleResult) {
        if (!otherModuleResult.getId().equals(getId())) {
            throw new IllegalArgumentException(String.format(
                "Cannot merge module result with mismatched id. Expected %s, Found %s",
                        otherModuleResult.getId(), getId()));
        }

        this.mRuntime += otherModuleResult.getRuntime();
        this.mNotExecuted += otherModuleResult.getNotExecuted();
        // only touch variables related to 'done' status if this module is not already done
        if (!isDone()) {
            this.setDone(otherModuleResult.isDoneSoFar());
            this.mActualTestRuns += otherModuleResult.getTestRuns();
            // expected test runs are the same across shards, except for shards that do not run
            // this module at least once (for which the value is not yet set).
            this.mExpectedTestRuns = otherModuleResult.getExpectedTestRuns();
        }
        for (ICaseResult otherCaseResult : otherModuleResult.getResults()) {
            ICaseResult caseResult = getOrCreateResult(otherCaseResult.getName());
            caseResult.mergeFrom(otherCaseResult);
        }
    }
}
