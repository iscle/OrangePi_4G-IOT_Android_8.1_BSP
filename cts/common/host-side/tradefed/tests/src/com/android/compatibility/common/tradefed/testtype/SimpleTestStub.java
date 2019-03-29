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
package com.android.compatibility.common.tradefed.testtype;

import com.android.ddmlib.testrunner.TestIdentifier;
import com.android.tradefed.config.Option;
import com.android.tradefed.device.DeviceNotAvailableException;
import com.android.tradefed.result.ITestInvocationListener;
import com.android.tradefed.testtype.IAbi;
import com.android.tradefed.testtype.IAbiReceiver;
import com.android.tradefed.testtype.IRemoteTest;
import com.android.tradefed.testtype.IRuntimeHintProvider;
import com.android.tradefed.testtype.ITestCollector;
import com.android.tradefed.testtype.ITestFilterReceiver;

import java.util.Collections;
import java.util.Set;

/**
 * A test Stub that can be used to fake some runs.
 */
public class SimpleTestStub implements IRemoteTest, IAbiReceiver, IRuntimeHintProvider,
        ITestCollector, ITestFilterReceiver {

    // options below are unused
    @Option(name = "report-test")
    protected boolean mReportTest = false;
    @Option(name = "run-complete")
    protected boolean mIsComplete = true;
    @Option(name = "test-fail")
    protected boolean mDoesOneTestFail = true;
    @Option(name = "internal-retry")
    protected boolean mRetry = false;

    /**
     * {@inheritDoc}
     */
    @Override
    public void run(ITestInvocationListener listener) throws DeviceNotAvailableException {
        // We report 1 passing tes
        listener.testRunStarted("module-run", 1);
        TestIdentifier tid = new TestIdentifier("TestStub", "test1");
        listener.testStarted(tid);
        listener.testEnded(tid, Collections.emptyMap());
        listener.testRunEnded(0, Collections.emptyMap());
    }

    @Override
    public void setAbi(IAbi abi) {
        // Do nothing
    }

    @Override
    public long getRuntimeHint() {
        return 1L;
    }

    @Override
    public void setCollectTestsOnly(boolean shouldCollectTest) {
        // Do nothing
    }

    @Override
    public void addIncludeFilter(String filter) {

    }

    @Override
    public void addAllIncludeFilters(Set<String> filters) {

    }

    @Override
    public void addExcludeFilter(String filter) {

    }

    @Override
    public void addAllExcludeFilters(Set<String> filters) {

    }
}
