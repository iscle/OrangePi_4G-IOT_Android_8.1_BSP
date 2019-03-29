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
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Interface for a the result of a single Compatibility invocation.
 */
public interface IInvocationResult {

    /**
     * @return the starting timestamp.
     */
    long getStartTime();

    /**
     * @param time the starting timestamp
     */
    void setStartTime(long time);

    /**
     * Count the number of results with given status.
     */
    int countResults(TestStatus result);

    /**
     * @return the number of tests that have not been executed in this moduleResult.
     */
    int getNotExecuted();

    /**
     * @param plan the plan associated with this result.
     */
    void setTestPlan(String plan);

    /**
     * @return the test plan associated with this result.
     */
    String getTestPlan();

    /**
     * Adds the given device serial to the result.
     */
    void addDeviceSerial(String serial);

    /**
     * @return the device serials associated with result.
     */
    Set<String> getDeviceSerials();

    /**
     * @return the {@link IModuleResult} for the given id, creating one if it doesn't exist
     */
    IModuleResult getOrCreateModule(String id);

    /**
     * @return the {@link IModuleResult}s sorted by id.
     */
    List<IModuleResult> getModules();

    /**
     * Merges a module result to the invocation result.
     */
    void mergeModuleResult(IModuleResult moduleResult);

    /**
     * Adds the given invocation info to the result.
     */
    void addInvocationInfo(String key, String value);

    /**
     * Gets the {@link Map} of invocation info collected.
     */
    Map<String, String> getInvocationInfo();

    /**
     *  Set the string containing the command line arguments to the run command.
     */
    void setCommandLineArgs(String setCommandLineArgs);

    /**
     * Retrieve the command line arguments to the run command.
     */
    String getCommandLineArgs();

    /**
     * @param buildFingerprint the build fingerprint associated with this result.
     */
    void setBuildFingerprint(String buildFingerprint);

    /**
     * @return the device build fingerprint associated with result.
     */
    String getBuildFingerprint();

    /**
     * Return the number of completed test modules for this invocation.
     */
    int getModuleCompleteCount();

    /**
     * Return status of checksum from previous session
     */
    RetryChecksumStatus getRetryChecksumStatus();

    /**
     * Set status of checksum from previous session
     */
    void setRetryChecksumStatus(RetryChecksumStatus retryStatus);

    /**
     * Return the directory of the previous sessions results
     */
    File getRetryDirectory();

    /**
     * Set the directory of the previous sessions results
     */
    void setRetryDirectory(File resultDir);

}
