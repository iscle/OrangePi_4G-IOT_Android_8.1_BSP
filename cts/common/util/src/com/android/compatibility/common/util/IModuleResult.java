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
 * Data structure for a Compatibility test module result.
 */
public interface IModuleResult extends Comparable<IModuleResult> {

    String getId();

    String getName();

    String getAbi();

    void addRuntime(long elapsedTime);

    void resetRuntime();

    long getRuntime();

    /**
     * Get the estimate of not-executed tests for this module. This estimate is a maximum
     * not-executed count, assuming all test runs have been started.
     * @return estimate of not-executed tests
     */
    int getNotExecuted();

    /**
     * Set the estimate of not-executed tests for this module. This estimate is a maximum
     * not-executed count, assuming all test runs have been started.
     * @param estimate of not-executed tests
     */
    void setNotExecuted(int numTests);

    /**
     * Whether all expected tests have been executed and all expected test runs have been seen
     * and completed.
     *
     * @return the comprehensive completeness status of the module
     */
    boolean isDone();

    /**
     * Whether all expected tests have been executed for the test runs seen so far.
     *
     * @return the completeness status of the module so far
     */
    boolean isDoneSoFar();

    /**
     * Explicitly sets the "done" status for this module. To be used when constructing this
     * instance from an XML report. The done status for an {@link IModuleResult} can be changed
     * indiscriminately by method setDone(boolean) immediately after a call to initializeDone,
     * whereas the status may only be switched to false immediately after a call to setDone.
     *
     * @param done the initial completeness status of the module
     */
    void initializeDone(boolean done);

    /**
     * Sets the "done" status for this module. To be used after each test run for the module.
     * After setDone is used once, subsequent calls to setDone will AND the given value with the
     * existing done status value. Thus a module with "done" already set to false cannot be marked
     * done unless re-initialized (see initializeDone).
     *
     * @param done the completeness status of the module for a test run
     */
    void setDone(boolean done);

    /**
     * Sets the "in-progress" status for this module. Useful for tracking completion of the module
     * in the case that a test run begins but never ends.
     *
     * @param inProgress whether the module is currently in progress
     */
    void inProgress(boolean inProgress);

    /**
     * @return the number of expected test runs for this module in this invocation
     */
    int getExpectedTestRuns();

    /**
     * @param the number of expected test runs for this module in this invocation
     */
    void setExpectedTestRuns(int numRuns);

    /**
     * @return the number of test runs seen for this module in this invocation
     */
    int getTestRuns();

    /**
     * Adds to the count of test runs seen for this module in this invocation
     */
    void addTestRun();

    /**
     * Reset the count of test runs seen for this module in this invocation. Should be performed
     * after merging the module into another module, so that future merges do not double-count the
     * same test runs.
     */
    void resetTestRuns();

    /**
     * Gets a {@link ICaseResult} for the given testcase, creating it if it doesn't exist.
     *
     * @param caseName the name of the testcase eg &lt;package-name&gt;&lt;class-name&gt;
     * @return the {@link ICaseResult} or <code>null</code>
     */
    ICaseResult getOrCreateResult(String caseName);

    /**
     * Gets the {@link ICaseResult} result for given testcase.
     *
     * @param caseName the name of the testcase eg &lt;package-name&gt;&lt;class-name&gt;
     * @return the {@link ITestResult} or <code>null</code>
     */
    ICaseResult getResult(String caseName);

    /**
     * Gets all results sorted by name.
     */
    List<ICaseResult> getResults();

    /**
     * Counts the number of results which have the given status.
     */
    int countResults(TestStatus status);

    /**
     * Merge the module results from otherModuleResult into this moduleResult.
     */
    void mergeFrom(IModuleResult otherModuleResult);
}
