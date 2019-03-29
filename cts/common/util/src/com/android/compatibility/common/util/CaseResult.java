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
 * Data structure for a Compatibility test case result.
 */
public class CaseResult implements ICaseResult {

    private String mName;

    private Map<String, ITestResult> mResults = new HashMap<>();

    /**
     * Creates a {@link CaseResult} for the given name, eg &lt;package-name&gt;.&lt;class-name&gt;
     */
    public CaseResult(String name) {
        mName = name;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String getName() {
        return mName;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public ITestResult getOrCreateResult(String testName) {
        ITestResult result = mResults.get(testName);
        if (result == null) {
            result = new TestResult(this, testName);
            mResults.put(testName, result);
        }
        return result;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public ITestResult getResult(String testName) {
        return mResults.get(testName);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public List<ITestResult> getResults(TestStatus status) {
        List<ITestResult> results = new ArrayList<>();
        for (ITestResult result : mResults.values()) {
            if (result.getResultStatus() == status) {
                results.add(result);
            }
        }
        return results;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public List<ITestResult> getResults() {
        ArrayList<ITestResult> results = new ArrayList<>(mResults.values());
        Collections.sort(results);
        return results;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public int countResults(TestStatus status) {
        int total = 0;
        for (ITestResult result : mResults.values()) {
            if (result.getResultStatus() == status) {
                total++;
            }
        }
        return total;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public int compareTo(ICaseResult another) {
        return getName().compareTo(another.getName());
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void mergeFrom(ICaseResult otherCaseResult) {
        if (!otherCaseResult.getName().equals(getName())) {
            throw new IllegalArgumentException(String.format(
                "Cannot merge case result with mismatched name. Expected %s, Found %s",
                        otherCaseResult.getName(), getName()));
        }

        for (ITestResult otherTestResult : otherCaseResult.getResults()) {
            mResults.put(otherTestResult.getName(), otherTestResult);
        }
    }

}
