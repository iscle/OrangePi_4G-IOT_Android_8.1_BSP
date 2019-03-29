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

package com.android.compatibility.common.tradefed.testtype;

import com.android.compatibility.common.tradefed.build.CompatibilityBuildHelper;
import com.android.compatibility.common.tradefed.result.InvocationFailureHandler;
import com.android.compatibility.common.tradefed.result.SubPlanHelper;
import com.android.compatibility.common.tradefed.targetprep.NetworkConnectivityChecker;
import com.android.compatibility.common.tradefed.util.RetryFilterHelper;
import com.android.compatibility.common.tradefed.util.RetryType;
import com.android.compatibility.common.tradefed.util.UniqueModuleCountUtil;
import com.android.compatibility.common.util.IInvocationResult;
import com.android.compatibility.common.util.ResultHandler;
import com.android.compatibility.common.util.TestFilter;
import com.android.ddmlib.Log.LogLevel;
import com.android.tradefed.build.IBuildInfo;
import com.android.tradefed.config.ConfigurationException;
import com.android.tradefed.config.Option;
import com.android.tradefed.config.Option.Importance;
import com.android.tradefed.config.OptionClass;
import com.android.tradefed.config.OptionCopier;
import com.android.tradefed.device.DeviceNotAvailableException;
import com.android.tradefed.device.DeviceUnresponsiveException;
import com.android.tradefed.device.ITestDevice;
import com.android.tradefed.invoker.IInvocationContext;
import com.android.tradefed.invoker.InvocationContext;
import com.android.tradefed.log.ITestLogger;
import com.android.tradefed.log.LogUtil.CLog;
import com.android.tradefed.result.ITestInvocationListener;
import com.android.tradefed.result.InputStreamSource;
import com.android.tradefed.result.LogDataType;
import com.android.tradefed.suite.checker.ISystemStatusChecker;
import com.android.tradefed.suite.checker.ISystemStatusCheckerReceiver;
import com.android.tradefed.testtype.Abi;
import com.android.tradefed.testtype.IAbi;
import com.android.tradefed.testtype.IBuildReceiver;
import com.android.tradefed.testtype.IDeviceTest;
import com.android.tradefed.testtype.IInvocationContextReceiver;
import com.android.tradefed.testtype.IRemoteTest;
import com.android.tradefed.testtype.IShardableTest;
import com.android.tradefed.testtype.IStrictShardableTest;
import com.android.tradefed.testtype.ITestCollector;
import com.android.tradefed.testtype.suite.TestSuiteInfo;
import com.android.tradefed.util.AbiFormatter;
import com.android.tradefed.util.AbiUtils;
import com.android.tradefed.util.ArrayUtil;
import com.android.tradefed.util.MultiMap;
import com.android.tradefed.util.StreamUtil;
import com.android.tradefed.util.TimeUtil;

import com.google.common.annotations.VisibleForTesting;

import java.io.ByteArrayOutputStream;
import java.io.FileNotFoundException;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/**
 * A Test for running Compatibility Suites
 */
@OptionClass(alias = "compatibility")
public class CompatibilityTest implements IDeviceTest, IShardableTest, IBuildReceiver,
        IStrictShardableTest, ISystemStatusCheckerReceiver, ITestCollector,
        IInvocationContextReceiver {

    public static final String INCLUDE_FILTER_OPTION = "include-filter";
    public static final String EXCLUDE_FILTER_OPTION = "exclude-filter";
    public static final String SUBPLAN_OPTION = "subplan";
    public static final String MODULE_OPTION = "module";
    public static final String TEST_OPTION = "test";
    public static final String PRECONDITION_ARG_OPTION = "precondition-arg";
    public static final String MODULE_ARG_OPTION = "module-arg";
    public static final String TEST_ARG_OPTION = "test-arg";
    public static final char TEST_OPTION_SHORT_NAME = 't';
    public static final String RETRY_OPTION = "retry";
    public static final String RETRY_TYPE_OPTION = "retry-type";
    public static final String ABI_OPTION = "abi";
    public static final String SHARD_OPTION = "shards";
    public static final String SKIP_DEVICE_INFO_OPTION = "skip-device-info";
    public static final String SKIP_PRECONDITIONS_OPTION = "skip-preconditions";
    public static final String SKIP_HOST_ARCH_CHECK = "skip-host-arch-check";
    public static final String PRIMARY_ABI_RUN = "primary-abi-only";
    public static final String DEVICE_TOKEN_OPTION = "device-token";
    public static final String LOGCAT_ON_FAILURE_SIZE_OPTION = "logcat-on-failure-size";

    // Constants for checking invocation or preconditions preparation failure
    private static final int NUM_PREP_ATTEMPTS = 10;
    private static final int MINUTES_PER_PREP_ATTEMPT = 2;

    @Option(name = SUBPLAN_OPTION,
            description = "the subplan to run",
            importance = Importance.IF_UNSET)
    private String mSubPlan;

    @Option(name = INCLUDE_FILTER_OPTION,
            description = "the include module filters to apply.",
            importance = Importance.ALWAYS)
    private Set<String> mIncludeFilters = new HashSet<>();

    @Option(name = EXCLUDE_FILTER_OPTION,
            description = "the exclude module filters to apply.",
            importance = Importance.ALWAYS)
    private Set<String> mExcludeFilters = new HashSet<>();

    @Option(name = MODULE_OPTION,
            shortName = 'm',
            description = "the test module to run.",
            importance = Importance.IF_UNSET)
    private String mModuleName = null;

    @Option(name = TEST_OPTION,
            shortName = TEST_OPTION_SHORT_NAME,
            description = "the test run.",
            importance = Importance.IF_UNSET)
    private String mTestName = null;

    @Option(name = PRECONDITION_ARG_OPTION,
            description = "the arguments to pass to a precondition. The expected format is"
                    + "\"<arg-name>:<arg-value>\"",
            importance = Importance.ALWAYS)
    private List<String> mPreconditionArgs = new ArrayList<>();

    @Option(name = MODULE_ARG_OPTION,
            description = "the arguments to pass to a module. The expected format is"
                    + "\"<module-name>:<arg-name>:[<arg-key>:=]<arg-value>\"",
            importance = Importance.ALWAYS)
    private List<String> mModuleArgs = new ArrayList<>();

    @Option(name = TEST_ARG_OPTION,
            description = "the arguments to pass to a test. The expected format is"
                    + "\"<test-class>:<arg-name>:[<arg-key>:=]<arg-value>\"",
            importance = Importance.ALWAYS)
    private List<String> mTestArgs = new ArrayList<>();

    @Option(name = RETRY_OPTION,
            shortName = 'r',
            description = "retry a previous session's failed and not executed tests.",
            importance = Importance.IF_UNSET)
    private Integer mRetrySessionId = null;

    @Option(name = RETRY_TYPE_OPTION,
            description = "used with " + RETRY_OPTION + ", retry tests of a certain status. "
            + "Possible values include \"failed\", \"not_executed\", and \"custom\".",
            importance = Importance.IF_UNSET)
    private RetryType mRetryType = null;

    @Option(name = ABI_OPTION,
            shortName = 'a',
            description = "the abi to test.",
            importance = Importance.IF_UNSET)
    private String mAbiName = null;

    @Option(name = SHARD_OPTION,
            description = "split the modules up to run on multiple devices concurrently. "
                    + "Deprecated, use --shard-count instead.")
    @Deprecated
    private int mShards = 1;

    @Option(name = SKIP_DEVICE_INFO_OPTION,
            shortName = 'd',
            description = "Whether device info collection should be skipped")
    private boolean mSkipDeviceInfo = false;

    @Option(name = SKIP_HOST_ARCH_CHECK,
            description = "Whether host architecture check should be skipped")
    private boolean mSkipHostArchCheck = false;

    @Option(name = SKIP_PRECONDITIONS_OPTION,
            shortName = 'o',
            description = "Whether preconditions should be skipped")
    private boolean mSkipPreconditions = false;

    @Option(name = PRIMARY_ABI_RUN,
            description = "Whether to run tests with only the device primary abi. "
                    + "This override the --abi option.")
    private boolean mPrimaryAbiRun = false;

    @Option(name = DEVICE_TOKEN_OPTION,
            description = "Holds the devices' tokens, used when scheduling tests that have"
                    + "prerequisites such as requiring a SIM card. Format is <serial>:<token>",
            importance = Importance.ALWAYS)
    private List<String> mDeviceTokens = new ArrayList<>();

    @Option(name = "bugreport-on-failure",
            description = "Take a bugreport on every test failure. " +
                    "Warning: can potentially use a lot of disk space.")
    private boolean mBugReportOnFailure = false;

    @Option(name = "logcat-on-failure",
            description = "Take a logcat snapshot on every test failure.")
    private boolean mLogcatOnFailure = false;

    @Option(name = LOGCAT_ON_FAILURE_SIZE_OPTION,
            description = "The max number of logcat data in bytes to capture when "
            + "--logcat-on-failure is on. Should be an amount that can comfortably fit in memory.")
    private int mMaxLogcatBytes = 500 * 1024; // 500K

    @Option(name = "screenshot-on-failure",
            description = "Take a screenshot on every test failure.")
    private boolean mScreenshotOnFailure = false;

    @Option(name = "reboot-before-test",
            description = "Reboot the device before the test suite starts.")
    private boolean mRebootBeforeTest = false;

    @Option(name = "reboot-on-failure",
            description = "Reboot the device after every test failure.")
    private boolean mRebootOnFailure = false;

    @Option(name = "reboot-per-module",
            description = "Reboot the device before every module run.")
    private boolean mRebootPerModule = false;

    @Option(name = "skip-connectivity-check",
            description = "Don't verify device connectivity between module execution.")
    private boolean mSkipConnectivityCheck = false;

    @Option(name = "preparer-whitelist",
            description = "Only run specific preparers."
            + "Specify zero or more ITargetPreparers as canonical class names. "
            + "e.g. \"com.android.compatibility.common.tradefed.targetprep.ApkInstaller\" "
            + "If not specified, all configured preparers are run.")
    private Set<String> mPreparerWhitelist = new HashSet<>();

    @Option(name = "skip-all-system-status-check",
            description = "Whether all system status check between modules should be skipped")
    private boolean mSkipAllSystemStatusCheck = false;

    @Option(name = "skip-system-status-check",
            description = "Disable specific system status checkers."
            + "Specify zero or more SystemStatusChecker as canonical class names. e.g. "
            + "\"com.android.compatibility.common.tradefed.targetprep.NetworkConnectivityChecker\" "
            + "If not specified, all configured or whitelisted system status checkers are run.")
    private Set<String> mSystemStatusCheckBlacklist = new HashSet<>();

    @Option(name = "system-status-check-whitelist",
            description = "Only run specific system status checkers."
            + "Specify zero or more SystemStatusChecker as canonical class names. e.g. "
            + "\"com.android.compatibility.common.tradefed.targetprep.NetworkConnectivityChecker\" "
            + "If not specified, all configured system status checkers are run.")
    private Set<String> mSystemStatusCheckWhitelist = new HashSet<>();

    private List<ISystemStatusChecker> mListCheckers = new ArrayList<>();

    @Option(name = "collect-tests-only",
            description = "Only invoke the suite to collect list of applicable test cases. All "
                    + "test run callbacks will be triggered, but test execution will not be "
                    + "actually carried out.")
    private Boolean mCollectTestsOnly = null;

    @Option(name = "module-metadata-include-filter",
            description = "Include modules for execution based on matching of metadata fields: "
                    + "for any of the specified filter name and value, if a module has a metadata "
                    + "field with the same name and value, it will be included. When both module "
                    + "inclusion and exclusion rules are applied, inclusion rules will be "
                    + "evaluated first. Using this together with test filter inclusion rules may "
                    + "result in no tests to execute if the rules don't overlap.")
    private MultiMap<String, String> mModuleMetadataIncludeFilter = new MultiMap<>();

    @Option(name = "module-metadata-exclude-filter",
            description = "Exclude modules for execution based on matching of metadata fields: "
                    + "for any of the specified filter name and value, if a module has a metadata "
                    + "field with the same name and value, it will be excluded. When both module "
                    + "inclusion and exclusion rules are applied, inclusion rules will be "
                    + "evaluated first.")
    private MultiMap<String, String> mModuleMetadataExcludeFilter = new MultiMap<>();

    private int mTotalShards;
    private Integer mShardIndex = null;
    private IModuleRepo mModuleRepo;
    private ITestDevice mDevice;
    private CompatibilityBuildHelper mBuildHelper;

    // variables used for local sharding scenario
    private static CountDownLatch sPreparedLatch;
    private boolean mIsLocalSharding = false;
    private boolean mIsSharded = false;

    private IInvocationContext mInvocationContext;

    /**
     * Create a new {@link CompatibilityTest} that will run the default list of
     * modules.
     */
    public CompatibilityTest() {
        this(1 /* totalShards */, new ModuleRepo(), 0);
    }

    /**
     * Create a new {@link CompatibilityTest} that will run a sublist of
     * modules.
     */
    public CompatibilityTest(int totalShards, IModuleRepo moduleRepo, Integer shardIndex) {
        if (totalShards < 1) {
            throw new IllegalArgumentException(
                    "Must be at least 1 shard. Given:" + totalShards);
        }
        mTotalShards = totalShards;
        mModuleRepo = moduleRepo;
        mShardIndex = shardIndex;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public ITestDevice getDevice() {
        return mDevice;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void setDevice(ITestDevice device) {
        mDevice = device;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void setBuild(IBuildInfo buildInfo) {
        mBuildHelper = new CompatibilityBuildHelper(buildInfo);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void run(ITestInvocationListener listener) throws DeviceNotAvailableException {
        try {
            List<ISystemStatusChecker> checkers = new ArrayList<>();
            // Get system status checkers
            if (mSkipAllSystemStatusCheck) {
                CLog.d("Skipping system status checkers");
            } else {
                checkSystemStatusBlackAndWhiteList();
                for (ISystemStatusChecker checker : mListCheckers) {
                    if(shouldIncludeSystemStatusChecker(checker)) {
                        checkers.add(checker);
                    }
                }
            }

            LinkedList<IModuleDef> modules = initializeModuleRepo();

            mExcludeFilters.clear();
            mIncludeFilters.clear();
            // Update BuildInfo in each shard to store the original command-line arguments from
            // the session to be retried. These arguments will be serialized in the report later.
            if (mRetrySessionId != null) {
                loadRetryCommandLineArgs(mRetrySessionId);
            }

            listener = new FailureListener(listener, getDevice(), mBugReportOnFailure,
                    mLogcatOnFailure, mScreenshotOnFailure, mRebootOnFailure, mMaxLogcatBytes);
            int moduleCount = modules.size();
            if (moduleCount == 0) {
                CLog.logAndDisplay(LogLevel.INFO, "No module to run on %s.",
                        mDevice.getSerialNumber());
                // Make sure we unlock other shards.
                if (sPreparedLatch != null) {
                    sPreparedLatch.countDown();
                }
                return;
            } else {
                int uniqueModuleCount = UniqueModuleCountUtil.countUniqueModules(modules);
                CLog.logAndDisplay(LogLevel.INFO, "Starting %d test sub-module%s on %s",
                        uniqueModuleCount, (uniqueModuleCount > 1) ? "s" : "",
                                mDevice.getSerialNumber());
            }

            if (mRebootBeforeTest) {
                CLog.d("Rebooting device before test starts as requested.");
                mDevice.reboot();
            }

            if (mSkipConnectivityCheck) {
                String clazz = NetworkConnectivityChecker.class.getCanonicalName();
                CLog.logAndDisplay(LogLevel.INFO, "\"--skip-connectivity-check\" is deprecated, "
                        + "please use \"--skip-system-status-check %s\" instead", clazz);
                mSystemStatusCheckBlacklist.add(clazz);
            }

            // Set values and run preconditions
            boolean isPrepared = true; // whether the device has been successfully prepared
            for (int i = 0; i < moduleCount; i++) {
                IModuleDef module = modules.get(i);
                module.setBuild(mBuildHelper.getBuildInfo());
                module.setDevice(mDevice);
                module.setPreparerWhitelist(mPreparerWhitelist);
                // don't set a value if unspecified
                if (mCollectTestsOnly != null) {
                    module.setCollectTestsOnly(mCollectTestsOnly);
                }
                isPrepared &= (module.prepare(mSkipPreconditions, mPreconditionArgs));
            }
            if (!isPrepared) {
                throw new RuntimeException(String.format("Failed preconditions on %s",
                        mDevice.getSerialNumber()));
            }
            if (mIsLocalSharding) {
                try {
                    sPreparedLatch.countDown();
                    int attempt = 1;
                    while(!sPreparedLatch.await(MINUTES_PER_PREP_ATTEMPT, TimeUnit.MINUTES)) {
                        if (attempt > NUM_PREP_ATTEMPTS ||
                                InvocationFailureHandler.hasFailed(mBuildHelper)) {
                            CLog.logAndDisplay(LogLevel.ERROR,
                                    "Incorrect preparation detected, exiting test run from %s",
                                    mDevice.getSerialNumber());
                            return;
                        }
                        CLog.logAndDisplay(LogLevel.WARN, "waiting on preconditions");
                        attempt++;
                    }
                } catch (InterruptedException e) {
                    throw new RuntimeException(e);
                }
            }
            // Module Repo is not useful anymore
            mModuleRepo.tearDown();
            mModuleRepo = null;
            // Run the tests
            while (!modules.isEmpty()) {
                // Make sure we remove the modules from the reference list when we are done with
                // them.
                IModuleDef module = modules.poll();
                long start = System.currentTimeMillis();

                if (mRebootPerModule) {
                    if ("user".equals(mDevice.getProperty("ro.build.type"))) {
                        CLog.e("reboot-per-module should only be used during development, "
                            + "this is a\" user\" build device");
                    } else {
                        CLog.logAndDisplay(LogLevel.INFO, "Rebooting device before starting next "
                            + "module");
                        mDevice.reboot();
                    }
                }

                // execute pre module execution checker
                if (checkers != null && !checkers.isEmpty()) {
                    runPreModuleCheck(module.getName(), checkers, mDevice, listener);
                }
                IInvocationContext moduleContext = new InvocationContext();
                moduleContext.setConfigurationDescriptor(module.getConfigurationDescriptor());
                moduleContext.addInvocationAttribute(IModuleDef.MODULE_NAME, module.getName());
                moduleContext.addInvocationAttribute(IModuleDef.MODULE_ABI,
                        module.getAbi().getName());
                mInvocationContext.setModuleInvocationContext(moduleContext);
                try {
                    module.run(listener);
                } catch (DeviceUnresponsiveException due) {
                    // being able to catch a DeviceUnresponsiveException here implies that recovery
                    // was successful, and test execution should proceed to next module
                    ByteArrayOutputStream stack = new ByteArrayOutputStream();
                    due.printStackTrace(new PrintWriter(stack, true));
                    StreamUtil.close(stack);
                    CLog.w("Ignored DeviceUnresponsiveException because recovery was successful, "
                            + "proceeding with next module. Stack trace: %s",
                            stack.toString());
                    CLog.w("This may be due to incorrect timeout setting on module %s",
                            module.getName());
                } finally {
                    // clear out module invocation context since we are now done with module
                    // execution
                    mInvocationContext.setModuleInvocationContext(null);
                }
                long duration = System.currentTimeMillis() - start;
                long expected = module.getRuntimeHint();
                long delta = Math.abs(duration - expected);
                // Show warning if delta is more than 10% of expected
                if (expected > 0 && ((float)delta / (float)expected) > 0.1f) {
                    CLog.logAndDisplay(LogLevel.WARN,
                            "Inaccurate runtime hint for %s, expected %s was %s",
                            module.getId(),
                            TimeUtil.formatElapsedTime(expected),
                            TimeUtil.formatElapsedTime(duration));
                }
                if (checkers != null && !checkers.isEmpty()) {
                    runPostModuleCheck(module.getName(), checkers, mDevice, listener);
                }
                module = null;
            }
        } catch (FileNotFoundException fnfe) {
            throw new RuntimeException("Failed to initialize modules", fnfe);
        }
    }

    /**
     * Initialize module repo.
     *
     * @return A list of module definition
     * @throws DeviceNotAvailableException
     * @throws FileNotFoundException
     */
    protected LinkedList<IModuleDef> initializeModuleRepo()
            throws DeviceNotAvailableException, FileNotFoundException {
        // FIXME: Each shard will do a full initialization which is not optimal. Need a way
        // to be more specific on what to initialize.
        synchronized (mModuleRepo) {
            if (!mModuleRepo.isInitialized()) {
                setupFilters();
                // Initialize the repository, {@link CompatibilityBuildHelper#getTestsDir} can
                // throw a {@link FileNotFoundException}
                mModuleRepo.initialize(mTotalShards, mShardIndex, mBuildHelper.getTestsDir(),
                        getAbis(), mDeviceTokens, mTestArgs, mModuleArgs, mIncludeFilters,
                        mExcludeFilters, mModuleMetadataIncludeFilter, mModuleMetadataExcludeFilter,
                        mBuildHelper.getBuildInfo());

                // Add the entire list of modules to the CompatibilityBuildHelper for reporting
                mBuildHelper.setModuleIds(mModuleRepo.getModuleIds());

                int count = UniqueModuleCountUtil.countUniqueModules(mModuleRepo.getTokenModules())
                        + UniqueModuleCountUtil.countUniqueModules(
                                  mModuleRepo.getNonTokenModules());
                CLog.logAndDisplay(LogLevel.INFO, "========================================");
                CLog.logAndDisplay(LogLevel.INFO, "Starting a run with %s unique modules.", count);
                CLog.logAndDisplay(LogLevel.INFO, "========================================");
            } else {
                CLog.d("ModuleRepo already initialized.");
            }
            // Get the tests to run in this shard
            return mModuleRepo.getModules(getDevice().getSerialNumber(), mShardIndex);
        }
    }

    /**
     * Gets the set of ABIs supported by both Compatibility and the device under test
     *
     * @return The set of ABIs to run the tests on
     * @throws DeviceNotAvailableException
     */
    Set<IAbi> getAbis() throws DeviceNotAvailableException {
        Set<IAbi> abis = new LinkedHashSet<>();
        Set<String> archAbis = getAbisForBuildTargetArch();
        if (mPrimaryAbiRun) {
            if (mAbiName == null) {
                // Get the primary from the device and make it the --abi to run.
                mAbiName = mDevice.getProperty("ro.product.cpu.abi").trim();
            } else {
                CLog.d("Option --%s supersedes the option --%s, using abi: %s", ABI_OPTION,
                        PRIMARY_ABI_RUN, mAbiName);
            }
        }
        if (mAbiName != null) {
            // A particular abi was requested, it still need to be supported by the build.
            if ((!mSkipHostArchCheck && !archAbis.contains(mAbiName)) ||
                    !AbiUtils.isAbiSupportedByCompatibility(mAbiName)) {
                throw new IllegalArgumentException(String.format("Your CTS hasn't been built with "
                        + "abi '%s' support, this CTS currently supports '%s'.",
                        mAbiName, archAbis));
            } else {
                abis.add(new Abi(mAbiName, AbiUtils.getBitness(mAbiName)));
                return abis;
            }
        } else {
            // Run on all abi in common between the device and CTS.
            List<String> deviceAbis = Arrays.asList(AbiFormatter.getSupportedAbis(mDevice, ""));
            for (String abi : deviceAbis) {
                if ((mSkipHostArchCheck || archAbis.contains(abi)) &&
                        AbiUtils.isAbiSupportedByCompatibility(abi)) {
                    abis.add(new Abi(abi, AbiUtils.getBitness(abi)));
                } else {
                    CLog.d("abi '%s' is supported by device but not by this CTS build (%s), tests "
                            + "will not run against it.", abi, archAbis);
                }
            }
            if (abis.isEmpty()) {
                throw new IllegalArgumentException(String.format("None of the abi supported by this"
                       + " CTS build ('%s') are supported by the device ('%s').",
                       archAbis, deviceAbis));
            }
            return abis;
        }
    }

    /**
     * Return the abis supported by the Host build target architecture.
     * Exposed for testing.
     */
    protected Set<String> getAbisForBuildTargetArch() {
        return AbiUtils.getAbisForArch(TestSuiteInfo.getInstance().getTargetArch());
    }

    /**
     * Check that the system status checker specified by option are valid.
     */
    protected void checkSystemStatusBlackAndWhiteList() {
        for (String checker : mSystemStatusCheckWhitelist) {
            try {
                Class.forName(checker);
            } catch (ClassNotFoundException e) {
                ConfigurationException ex = new ConfigurationException(
                        String.format("--system-status-check-whitelist must contains valid class, "
                                + "%s was not found", checker), e);
                throw new RuntimeException(ex);
            }
        }
        for (String checker : mSystemStatusCheckBlacklist) {
            try {
                Class.forName(checker);
            } catch (ClassNotFoundException e) {
                ConfigurationException ex = new ConfigurationException(
                        String.format("--skip-system-status-check must contains valid class, "
                                + "%s was not found", checker), e);
                throw new RuntimeException(ex);
            }
        }
    }

    /**
     * Resolve the inclusion and exclusion logic of system status checkers
     *
     * @param s the {@link ISystemStatusChecker} to perform filtering logic on
     * @return True if the {@link ISystemStatusChecker} should be included, false otherwise.
     */
    private boolean shouldIncludeSystemStatusChecker(ISystemStatusChecker s) {
        String clazz = s.getClass().getCanonicalName();
        boolean shouldInclude = mSystemStatusCheckWhitelist.isEmpty()
                || mSystemStatusCheckWhitelist.contains(clazz);
        boolean shouldExclude = !mSystemStatusCheckBlacklist.isEmpty()
                && mSystemStatusCheckBlacklist.contains(clazz);
        return shouldInclude && !shouldExclude;
    }

    @VisibleForTesting
    void runPreModuleCheck(String moduleName, List<ISystemStatusChecker> checkers,
            ITestDevice device, ITestLogger logger) throws DeviceNotAvailableException {
        CLog.i("Running system status checker before module execution: %s", moduleName);
        List<String> failures = new ArrayList<>();
        for (ISystemStatusChecker checker : checkers) {
            boolean result = checker.preExecutionCheck(device);
            if (!result) {
                failures.add(checker.getClass().getCanonicalName());
                CLog.w("System status checker [%s] failed", checker.getClass().getCanonicalName());
            }
        }
        if (!failures.isEmpty()) {
            CLog.w("There are failed system status checkers: %s capturing a bugreport",
                    failures.toString());
            try (InputStreamSource bugSource = device.getBugreport()) {
                logger.testLog(String.format("bugreport-checker-pre-module-%s", moduleName),
                        LogDataType.BUGREPORT, bugSource);
            }
        }
    }

    @VisibleForTesting
    void runPostModuleCheck(String moduleName, List<ISystemStatusChecker> checkers,
            ITestDevice device, ITestLogger logger) throws DeviceNotAvailableException {
        CLog.i("Running system status checker after module execution: %s", moduleName);
        List<String> failures = new ArrayList<>();
        for (ISystemStatusChecker checker : checkers) {
            boolean result = checker.postExecutionCheck(device);
            if (!result) {
                failures.add(checker.getClass().getCanonicalName());
                CLog.w("System status checker [%s] failed", checker.getClass().getCanonicalName());
            }
        }
        if (!failures.isEmpty()) {
            CLog.w("There are failed system status checkers: %s capturing a bugreport",
                    failures.toString());
            try (InputStreamSource bugSource = device.getBugreport()) {
                logger.testLog(String.format("bugreport-checker-post-module-%s", moduleName),
                        LogDataType.BUGREPORT, bugSource);
            }
        }
    }

    /**
     * Sets the retry command-line args to be stored in the BuildInfo and serialized into the
     * report upon completion of the invocation.
     */
    void loadRetryCommandLineArgs(Integer sessionId) {
        IInvocationResult result = null;
        try {
            result = ResultHandler.findResult(mBuildHelper.getResultsDir(), sessionId);
        } catch (FileNotFoundException e) {
            // We should never reach this point, because this method should only be called
            // after setupFilters(), so result exists if we've gotten this far
            throw new RuntimeException(e);
        }
        if (result == null) {
            // Again, this should never happen
            throw new IllegalArgumentException(String.format(
                    "Could not find session with id %d", sessionId));
        }
        String retryCommandLineArgs = result.getCommandLineArgs();
        if (retryCommandLineArgs != null) {
            mBuildHelper.setRetryCommandLineArgs(retryCommandLineArgs);
        }
    }

    /**
     * Sets the include/exclude filters up based on if a module name was given or whether this is a
     * retry run.
     */
    void setupFilters() throws DeviceNotAvailableException {
        if (mRetrySessionId != null) {
            // Load the invocation result
            RetryFilterHelper helper = new RetryFilterHelper(mBuildHelper, mRetrySessionId,
                    mSubPlan, mIncludeFilters, mExcludeFilters, mAbiName, mModuleName, mTestName,
                    mRetryType);
            helper.validateBuildFingerprint(mDevice);
            helper.setCommandLineOptionsFor(this);
            helper.populateRetryFilters();
            mIncludeFilters = helper.getIncludeFilters();
            mExcludeFilters = helper.getExcludeFilters();
            helper.tearDown();
        } else {
            if (mSubPlan != null) {
                ISubPlan subPlan = SubPlanHelper.getSubPlanByName(mBuildHelper, mSubPlan);
                mIncludeFilters.addAll(subPlan.getIncludeFilters());
                mExcludeFilters.addAll(subPlan.getExcludeFilters());
            }
            if (mModuleName != null) {
                try {
                    List<String> modules = ModuleRepo.getModuleNamesMatching(
                            mBuildHelper.getTestsDir(), mModuleName);
                    if (modules.size() == 0) {
                        throw new IllegalArgumentException(
                                String.format("No modules found matching %s", mModuleName));
                    } else if (modules.size() > 1) {
                        throw new IllegalArgumentException(String.format("Multiple modules found"
                                + " matching %s:\n%s\nWhich one did you mean?\n",
                                mModuleName, ArrayUtil.join("\n", modules)));
                    } else {
                        String module = modules.get(0);
                        cleanFilters(mIncludeFilters, module);
                        cleanFilters(mExcludeFilters, module);
                        mIncludeFilters.add(
                                new TestFilter(mAbiName, module, mTestName).toString());
                    }
                } catch (FileNotFoundException e) {
                    throw new RuntimeException(e);
                }
            } else if (mTestName != null) {
                throw new IllegalArgumentException(
                        "Test name given without module name. Add --module <module-name>");
            }
        }
    }

    /* Helper method designed to remove filters in a list not applicable to the given module */
    private static void cleanFilters(Set<String> filters, String module) {
        Set<String> cleanedFilters = new HashSet<String>();
        for (String filter : filters) {
            if (module.equals(TestFilter.createFrom(filter).getName())) {
                cleanedFilters.add(filter); // Module name matches, filter passes
            }
        }
        filters.clear();
        filters.addAll(cleanedFilters);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Collection<IRemoteTest> split() {
        if (mShards <= 1) {
            return null;
        }
        mIsLocalSharding = true;
        List<IRemoteTest> shardQueue = new LinkedList<>();
        for (int i = 0; i < mShards; i++) {
            CompatibilityTest test = (CompatibilityTest) getTestShard(mShards, i);
            test.mIsLocalSharding = true;
            shardQueue.add(test);
        }
        sPreparedLatch = new CountDownLatch(shardQueue.size());
        return shardQueue;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Collection<IRemoteTest> split(int shardCount) {
        if (shardCount <= 1 || mIsSharded) {
            return null;
        }
        mIsSharded = true;
        List<IRemoteTest> shardQueue = new LinkedList<>();
        for (int i = 0; i < shardCount; i++) {
            CompatibilityTest test = (CompatibilityTest) getTestShard(shardCount, i);
            shardQueue.add(test);
            test.mIsSharded = true;
        }
        return shardQueue;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public IRemoteTest getTestShard(int shardCount, int shardIndex) {
        CompatibilityTest test = new CompatibilityTest(shardCount, mModuleRepo, shardIndex);
        OptionCopier.copyOptionsNoThrow(this, test);
        // Set the shard count because the copy option on the previous line
        // copies over the mShard value
        test.mShards = 0;
        return test;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void setSystemStatusChecker(List<ISystemStatusChecker> systemCheckers) {
        mListCheckers = systemCheckers;
    }

    @Override
    public void setCollectTestsOnly(boolean collectTestsOnly) {
        mCollectTestsOnly = collectTestsOnly;
    }

    /**
     * Sets include-filters for the compatibility test
     */
    public void setIncludeFilter(Set<String> includeFilters) {
        mIncludeFilters.addAll(includeFilters);
    }

    /**
     * Sets exclude-filters for the compatibility test
     */
    public void setExcludeFilter(Set<String> excludeFilters) {
        mExcludeFilters.addAll(excludeFilters);
    }

    @Override
    public void setInvocationContext(IInvocationContext invocationContext) {
        mInvocationContext = invocationContext;
    }
    /**
     * @return the mIncludeFilters
     */
    protected Set<String> getIncludeFilters() {
        return mIncludeFilters;
    }

    /**
     * @return the mExcludeFilters
     */
    protected Set<String> getExcludeFilters() {
        return mExcludeFilters;
    }

    /**
     * @return the mModuleArgs
     */
    protected List<String> getModuleArgs() {
        return mModuleArgs;
    }

    /**
     * @return the mTestArgs
     */
    protected List<String> getTestArgs() {
        return mTestArgs;
    }

    /**
     * @return the mDeviceTokens
     */
    protected List<String> getDeviceTokens() {
        return mDeviceTokens;
    }

    /**
     * @return the mModuleMetadataIncludeFilter
     */
    protected MultiMap<String, String> getModuleMetadataIncludeFilter() {
        return mModuleMetadataIncludeFilter;
    }

    /**
     * @return the mModuleMetadataExcludeFilter
     */
    protected MultiMap<String, String> getModuleMetadataExcludeFilter() {
        return mModuleMetadataExcludeFilter;
    }

    /**
     * @return the mTotalShards
     */
    protected int getTotalShards() {
        return mTotalShards;
    }

    /**
     * @return the mShardIndex
     */
    protected Integer getShardIndex() {
        return mShardIndex;
    }

    /**
     * @return the mBuildHelper
     */
    protected CompatibilityBuildHelper getBuildHelper() {
        return mBuildHelper;
    }

    /**
     * @return the mInvocationContext
     */
    protected IInvocationContext getInvocationContext() {
        return mInvocationContext;
    }

    /**
     * @return the mModuleRepo
     */
    protected IModuleRepo getModuleRepo() {
        return mModuleRepo;
    }
}
