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
package com.android.compatibility.common.tradefed.testtype.retry;

import com.android.compatibility.common.tradefed.build.CompatibilityBuildHelper;
import com.android.compatibility.common.tradefed.testtype.CompatibilityTest;
import com.android.compatibility.common.tradefed.testtype.suite.CompatibilityTestSuite;
import com.android.compatibility.common.tradefed.util.RetryFilterHelper;
import com.android.compatibility.common.tradefed.util.RetryType;
import com.android.tradefed.build.IBuildInfo;
import com.android.tradefed.config.ConfigurationException;
import com.android.tradefed.config.Option;
import com.android.tradefed.config.Option.Importance;
import com.android.tradefed.config.OptionClass;
import com.android.tradefed.config.OptionSetter;
import com.android.tradefed.device.DeviceNotAvailableException;
import com.android.tradefed.device.ITestDevice;
import com.android.tradefed.invoker.IInvocationContext;
import com.android.tradefed.log.LogUtil.CLog;
import com.android.tradefed.result.ITestInvocationListener;
import com.android.tradefed.suite.checker.ISystemStatusChecker;
import com.android.tradefed.suite.checker.ISystemStatusCheckerReceiver;
import com.android.tradefed.testtype.IBuildReceiver;
import com.android.tradefed.testtype.IDeviceTest;
import com.android.tradefed.testtype.IInvocationContextReceiver;
import com.android.tradefed.testtype.IRemoteTest;
import com.android.tradefed.testtype.IShardableTest;

import com.google.common.annotations.VisibleForTesting;

import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Runner that creates a {@link CompatibilityTest} to re-run some previous results.
 * Only the 'cts' plan is supported.
 * TODO: explore other new way to build the retry (instead of relying on one massive pair of
 * include/exclude filters)
 */
@OptionClass(alias = "compatibility")
public class RetryFactoryTest implements IRemoteTest, IDeviceTest, IBuildReceiver,
        ISystemStatusCheckerReceiver, IInvocationContextReceiver, IShardableTest {

    /**
     * Mirror the {@link CompatibilityTest} options in order to create it.
     */
    public static final String RETRY_OPTION = "retry";
    @Option(name = RETRY_OPTION,
            shortName = 'r',
            description = "retry a previous session's failed and not executed tests.",
            mandatory = true)
    private Integer mRetrySessionId = null;

    @Option(name = CompatibilityTest.SUBPLAN_OPTION,
            description = "the subplan to run",
            importance = Importance.IF_UNSET)
    protected String mSubPlan;

    @Option(name = CompatibilityTest.INCLUDE_FILTER_OPTION,
            description = "the include module filters to apply.",
            importance = Importance.ALWAYS)
    protected Set<String> mIncludeFilters = new HashSet<>();

    @Option(name = CompatibilityTest.EXCLUDE_FILTER_OPTION,
            description = "the exclude module filters to apply.",
            importance = Importance.ALWAYS)
    protected Set<String> mExcludeFilters = new HashSet<>();

    @Option(name = CompatibilityTest.ABI_OPTION,
            shortName = 'a',
            description = "the abi to test.",
            importance = Importance.IF_UNSET)
    protected String mAbiName = null;

    @Option(name = CompatibilityTest.MODULE_OPTION,
            shortName = 'm',
            description = "the test module to run.",
            importance = Importance.IF_UNSET)
    protected String mModuleName = null;

    @Option(name = CompatibilityTest.TEST_OPTION,
            shortName = CompatibilityTest.TEST_OPTION_SHORT_NAME,
            description = "the test run.",
            importance = Importance.IF_UNSET)
    protected String mTestName = null;

    @Option(name = CompatibilityTest.RETRY_TYPE_OPTION,
            description = "used with " + CompatibilityTest.RETRY_OPTION + ", retry tests"
            + " of a certain status. Possible values include \"failed\" and \"not_executed\".",
            importance = Importance.IF_UNSET)
    protected RetryType mRetryType = null;

    private List<ISystemStatusChecker> mStatusCheckers;
    private IBuildInfo mBuildInfo;
    private ITestDevice mDevice;
    private IInvocationContext mContext;

    @Override
    public void setSystemStatusChecker(List<ISystemStatusChecker> systemCheckers) {
        mStatusCheckers = systemCheckers;
    }

    @Override
    public void setBuild(IBuildInfo buildInfo) {
        mBuildInfo = buildInfo;
    }

    @Override
    public void setDevice(ITestDevice device) {
        mDevice = device;
    }

    @Override
    public ITestDevice getDevice() {
        return mDevice;
    }

    @Override
    public void setInvocationContext(IInvocationContext invocationContext) {
        mContext = invocationContext;
    }

    /**
     * Build a CompatibilityTest with appropriate filters to run only the tests of interests.
     */
    @Override
    public void run(ITestInvocationListener listener) throws DeviceNotAvailableException {
        CompatibilityTestSuite test = loadSuite();
        // run the retry run.
        test.run(listener);
    }

    @Override
    public Collection<IRemoteTest> split(int shardCountHint) {
        try {
            CompatibilityTestSuite test = loadSuite();
            return test.split(shardCountHint);
        } catch (DeviceNotAvailableException e) {
            CLog.e("Failed to shard the retry run.");
            CLog.e(e);
        }
        return null;
    }

    /**
     * Helper to create a {@link CompatibilityTestSuite} from previous results.
     */
    private CompatibilityTestSuite loadSuite() throws DeviceNotAvailableException {
        // Create a compatibility test and set it to run only what we want.
        CompatibilityTestSuite test = createTest();

        CompatibilityBuildHelper buildHelper = new CompatibilityBuildHelper(mBuildInfo);
        // Create the helper with all the options needed.
        RetryFilterHelper helper = createFilterHelper(buildHelper);
        // TODO: we have access to the original command line, we should accommodate more re-run
        // scenario like when the original cts.xml config was not used.
        helper.validateBuildFingerprint(mDevice);
        helper.setCommandLineOptionsFor(test);
        helper.setCommandLineOptionsFor(this);
        helper.populateRetryFilters();

        try {
            OptionSetter setter = new OptionSetter(test);
            setter.setOptionValue("compatibility:test-arg",
                    "com.android.tradefed.testtype.AndroidJUnitTest:rerun-from-file:true");
            setter.setOptionValue("compatibility:test-arg",
                    "com.android.tradefed.testtype.AndroidJUnitTest:fallback-to-serial-rerun:"
                    + "false");
        } catch (ConfigurationException e) {
            throw new RuntimeException(e);
        }

        test.setIncludeFilter(helper.getIncludeFilters());
        test.setExcludeFilter(helper.getExcludeFilters());
        test.setDevice(mDevice);
        test.setBuild(mBuildInfo);
        test.setSystemStatusChecker(mStatusCheckers);
        test.setInvocationContext(mContext);
        // clean the helper
        helper.tearDown();
        return test;
    }

    @VisibleForTesting
    RetryFilterHelper createFilterHelper(CompatibilityBuildHelper buildHelper) {
        return new RetryFilterHelper(buildHelper, mRetrySessionId, mSubPlan, mIncludeFilters,
                mExcludeFilters, mAbiName, mModuleName, mTestName, mRetryType);
    }

    @VisibleForTesting
    CompatibilityTestSuite createTest() {
        return new CompatibilityTestSuite();
    }
}
