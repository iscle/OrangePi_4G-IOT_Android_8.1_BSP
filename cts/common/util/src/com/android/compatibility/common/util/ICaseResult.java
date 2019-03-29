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

import java.util.List;

/**
 * Data structure for a Compatibility test case result.
 */
public interface ICaseResult extends Comparable<ICaseResult> {

    String getName();

    /**
     * Gets a {@link ITestResult} for the given test, creating it if it doesn't exist.
     *
     * @param testName the name of the test eg &lt;method-name&gt;
     * @return the {@link ITestResult} or <code>null</code>
     */
    ITestResult getOrCreateResult(String testName);

    /**
     * Gets the {@link ITestResult} for given test.
     *
     * @param testName the name of the test eg &lt;method-name&gt;
     * @return the {@link ITestResult} or <code>null</code>
     */
    ITestResult getResult(String testName);

    /**
     * Gets all results sorted by name.
     */
    List<ITestResult> getResults();

    /**
     * Gets all results which have the given status.
     */
    List<ITestResult> getResults(TestStatus status);

    /**
     * Counts the number of results which have the given status.
     */
    int countResults(TestStatus status);

    /**
     * Merge the case results from otherCaseResult into this caseResult.
     */
    void mergeFrom(ICaseResult otherCaseResult);
}
