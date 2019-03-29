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
package com.android.compatibility.common.tradefed.result;

import com.android.compatibility.common.tradefed.build.CompatibilityBuildHelper;
import com.android.compatibility.common.tradefed.testtype.CompatibilityTest;
import com.android.compatibility.common.tradefed.util.RetryType;
import com.android.compatibility.common.util.ChecksumReporter;
import com.android.compatibility.common.util.ICaseResult;
import com.android.compatibility.common.util.IInvocationResult;
import com.android.compatibility.common.util.IModuleResult;
import com.android.compatibility.common.util.ITestResult;
import com.android.compatibility.common.util.InvocationResult;
import com.android.compatibility.common.util.MetricsStore;
import com.android.compatibility.common.util.ReportLog;
import com.android.compatibility.common.util.ResultHandler;
import com.android.compatibility.common.util.ResultUploader;
import com.android.compatibility.common.util.TestStatus;
import com.android.ddmlib.Log.LogLevel;
import com.android.ddmlib.testrunner.TestIdentifier;
import com.android.tradefed.build.IBuildInfo;
import com.android.tradefed.config.Option;
import com.android.tradefed.config.Option.Importance;
import com.android.tradefed.config.OptionClass;
import com.android.tradefed.config.OptionCopier;
import com.android.tradefed.invoker.IInvocationContext;
import com.android.tradefed.log.LogUtil.CLog;
import com.android.tradefed.result.ILogSaver;
import com.android.tradefed.result.ILogSaverListener;
import com.android.tradefed.result.IShardableListener;
import com.android.tradefed.result.ITestInvocationListener;
import com.android.tradefed.result.ITestSummaryListener;
import com.android.tradefed.result.InputStreamSource;
import com.android.tradefed.result.LogDataType;
import com.android.tradefed.result.LogFile;
import com.android.tradefed.result.LogFileSaver;
import com.android.tradefed.result.TestSummary;
import com.android.tradefed.util.FileUtil;
import com.android.tradefed.util.StreamUtil;
import com.android.tradefed.util.TimeUtil;
import com.android.tradefed.util.ZipUtil;

import com.google.common.annotations.VisibleForTesting;

import org.xmlpull.v1.XmlPullParserException;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/**
 * Collect test results for an entire invocation and output test results to disk.
 */
@OptionClass(alias="result-reporter")
public class ResultReporter implements ILogSaverListener, ITestInvocationListener,
       ITestSummaryListener, IShardableListener {

    private static final String UNKNOWN_DEVICE = "unknown_device";
    private static final String RESULT_KEY = "COMPATIBILITY_TEST_RESULT";
    private static final String CTS_PREFIX = "cts:";
    private static final String BUILD_INFO = CTS_PREFIX + "build_";

    public static final String BUILD_BRAND = "build_brand";
    public static final String BUILD_DEVICE = "build_device";
    public static final String BUILD_FINGERPRINT = "build_fingerprint";
    public static final String BUILD_ID = "build_id";
    public static final String BUILD_MANUFACTURER = "build_manufacturer";
    public static final String BUILD_MODEL = "build_model";
    public static final String BUILD_PRODUCT = "build_product";
    public static final String BUILD_VERSION_RELEASE = "build_version_release";

    private static final List<String> NOT_RETRY_FILES = Arrays.asList(
            ChecksumReporter.NAME,
            ChecksumReporter.PREV_NAME,
            ResultHandler.FAILURE_REPORT_NAME,
            "diffs");

    @Option(name = CompatibilityTest.RETRY_OPTION,
            shortName = 'r',
            description = "retry a previous session.",
            importance = Importance.IF_UNSET)
    private Integer mRetrySessionId = null;

    @Option(name = CompatibilityTest.RETRY_TYPE_OPTION,
            description = "used with " + CompatibilityTest.RETRY_OPTION
            + ", retry tests of a certain status. Possible values include \"failed\", "
            + "\"not_executed\", and \"custom\".",
            importance = Importance.IF_UNSET)
    private RetryType mRetryType = null;

    @Option(name = "result-server", description = "Server to publish test results.")
    private String mResultServer;

    @Option(name = "disable-result-posting", description = "Disable result posting into report server.")
    private boolean mDisableResultPosting = false;

    @Option(name = "include-test-log-tags", description = "Include test log tags in report.")
    private boolean mIncludeTestLogTags = false;

    @Option(name = "use-log-saver", description = "Also saves generated result with log saver")
    private boolean mUseLogSaver = false;

    @Option(name = "compress-logs", description = "Whether logs will be saved with compression")
    private boolean mCompressLogs = true;

    private CompatibilityBuildHelper mBuildHelper;
    private File mResultDir = null;
    private File mLogDir = null;
    private ResultUploader mUploader;
    private String mReferenceUrl;
    private ILogSaver mLogSaver;
    private int invocationEndedCount = 0;
    private CountDownLatch mFinalized = null;

    protected IInvocationResult mResult = new InvocationResult();
    private IModuleResult mCurrentModuleResult;
    private ICaseResult mCurrentCaseResult;
    private ITestResult mCurrentResult;
    private String mDeviceSerial = UNKNOWN_DEVICE;
    private Set<String> mMasterDeviceSerials = new HashSet<>();
    private Set<IBuildInfo> mMasterBuildInfos = new HashSet<>();

    // mCurrentTestNum and mTotalTestsInModule track the progress within the module
    // Note that this count is not necessarily equal to the count of tests contained
    // in mCurrentModuleResult because of how special cases like ignored tests are reported.
    private int mCurrentTestNum;
    private int mTotalTestsInModule;

    // Whether modules can be marked done for this invocation. Initialized in invocationStarted()
    // Visible for unit testing
    protected boolean mCanMarkDone;
    // Whether the current test run has failed. If true, we will not mark the current module done
    protected boolean mTestRunFailed;
    // Whether the current module has previously been marked done
    private boolean mModuleWasDone;

    // Nullable. If null, "this" is considered the master and must handle
    // result aggregation and reporting. When not null, it should forward events
    // to the master.
    private final ResultReporter mMasterResultReporter;

    private LogFileSaver mTestLogSaver;

    // Elapsed time from invocation started to ended.
    private long mElapsedTime;

    /**
     * Default constructor.
     */
    public ResultReporter() {
        this(null);
        mFinalized = new CountDownLatch(1);
    }

    /**
     * Construct a shard ResultReporter that forwards module results to the
     * masterResultReporter.
     */
    public ResultReporter(ResultReporter masterResultReporter) {
        mMasterResultReporter = masterResultReporter;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void invocationStarted(IInvocationContext context) {
        IBuildInfo primaryBuild = context.getBuildInfos().get(0);
        synchronized(this) {
            if (mBuildHelper == null) {
                mBuildHelper = new CompatibilityBuildHelper(primaryBuild);
            }
            if (mDeviceSerial == null && primaryBuild.getDeviceSerial() != null) {
                mDeviceSerial = primaryBuild.getDeviceSerial();
            }
            mCanMarkDone = canMarkDone(mBuildHelper.getRecentCommandLineArgs());
        }

        if (isShardResultReporter()) {
            // Shard ResultReporters forward invocationStarted to the mMasterResultReporter
            mMasterResultReporter.invocationStarted(context);
            return;
        }

        // NOTE: Everything after this line only applies to the master ResultReporter.

        synchronized(this) {
            if (primaryBuild.getDeviceSerial() != null) {
                // The master ResultReporter collects all device serials being used
                // for the current implementation.
                mMasterDeviceSerials.add(primaryBuild.getDeviceSerial());
            }

            // The master ResultReporter collects all buildInfos.
            mMasterBuildInfos.add(primaryBuild);

            if (mResultDir == null) {
                // For the non-sharding case, invocationStarted is only called once,
                // but for the sharding case, this might be called multiple times.
                // Logic used to initialize the result directory should not be
                // invoked twice during the same invocation.
                initializeResultDirectories();
            }
        }
    }

    /**
     * Create directory structure where results and logs will be written.
     */
    private void initializeResultDirectories() {
        debug("Initializing result directory");

        try {
            // Initialize the result directory. Either a new directory or reusing
            // an existing session.
            if (mRetrySessionId != null) {
                // Overwrite the mResult with the test results of the previous session
                mResult = ResultHandler.findResult(mBuildHelper.getResultsDir(), mRetrySessionId);
            }
            mResult.setStartTime(mBuildHelper.getStartTime());
            mResultDir = mBuildHelper.getResultDir();
            if (mResultDir != null) {
                mResultDir.mkdirs();
            }
        } catch (FileNotFoundException e) {
            throw new RuntimeException(e);
        }

        if (mResultDir == null) {
            throw new RuntimeException("Result Directory was not created");
        }
        if (!mResultDir.exists()) {
            throw new RuntimeException("Result Directory was not created: " +
                    mResultDir.getAbsolutePath());
        }

        debug("Results Directory: " + mResultDir.getAbsolutePath());

        mUploader = new ResultUploader(mResultServer, mBuildHelper.getSuiteName());
        try {
            mLogDir = new File(mBuildHelper.getLogsDir(),
                    CompatibilityBuildHelper.getDirSuffix(mBuildHelper.getStartTime()));
        } catch (FileNotFoundException e) {
            CLog.e(e);
        }
        if (mLogDir != null && mLogDir.mkdirs()) {
            debug("Created log dir %s", mLogDir.getAbsolutePath());
        }
        if (mLogDir == null || !mLogDir.exists()) {
            throw new IllegalArgumentException(String.format("Could not create log dir %s",
                    mLogDir.getAbsolutePath()));
        }
        if (mTestLogSaver == null) {
            mTestLogSaver = new LogFileSaver(mLogDir);
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void testRunStarted(String id, int numTests) {
        if (mCurrentModuleResult != null && mCurrentModuleResult.getId().equals(id)
                && mCurrentModuleResult.isDone()) {
            // Modules run with JarHostTest treat each test class as a separate module,
            // resulting in additional unexpected test runs.
            // This case exists only for N
            mTotalTestsInModule += numTests;
        } else {
            // Handle non-JarHostTest case
            mCurrentModuleResult = mResult.getOrCreateModule(id);
            mModuleWasDone = mCurrentModuleResult.isDone();
            mTestRunFailed = false;
            if (!mModuleWasDone) {
                // we only want to update testRun variables if the IModuleResult is not yet done
                // otherwise leave testRun variables alone so isDone evaluates to true.
                if (mCurrentModuleResult.getExpectedTestRuns() == 0) {
                    mCurrentModuleResult.setExpectedTestRuns(TestRunHandler.getTestRuns(
                            mBuildHelper, mCurrentModuleResult.getId()));
                }
                mCurrentModuleResult.addTestRun();
            }
            // Reset counters
            mTotalTestsInModule = numTests;
            mCurrentTestNum = 0;
        }
        mCurrentModuleResult.inProgress(true);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void testStarted(TestIdentifier test) {
        mCurrentCaseResult = mCurrentModuleResult.getOrCreateResult(test.getClassName());
        mCurrentResult = mCurrentCaseResult.getOrCreateResult(test.getTestName().trim());
        if (mCurrentResult.isRetry()) {
            mCurrentResult.reset(); // clear result status for this invocation
        }
        mCurrentTestNum++;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void testEnded(TestIdentifier test, Map<String, String> metrics) {
        if (mCurrentResult.getResultStatus() == TestStatus.FAIL) {
            // Test has previously failed.
            return;
        }
        // device test can have performance results in test metrics
        String perfResult = metrics.get(RESULT_KEY);
        ReportLog report = null;
        if (perfResult != null) {
            try {
                report = ReportLog.parse(perfResult);
            } catch (XmlPullParserException | IOException e) {
                e.printStackTrace();
            }
        } else {
            // host test should be checked into MetricsStore.
            report = MetricsStore.removeResult(mBuildHelper.getBuildInfo(),
                    mCurrentModuleResult.getAbi(), test.toString());
        }
        if (mCurrentResult.getResultStatus() == null) {
            // Only claim that we passed when we're certain our result was
            // not any other state.
            mCurrentResult.passed(report);
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void testIgnored(TestIdentifier test) {
        mCurrentResult.skipped();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void testFailed(TestIdentifier test, String trace) {
        mCurrentResult.failed(trace);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void testAssumptionFailure(TestIdentifier test, String trace) {
        mCurrentResult.skipped();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void testRunStopped(long elapsedTime) {
        // ignore
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void testRunEnded(long elapsedTime, Map<String, String> metrics) {
        mCurrentModuleResult.inProgress(false);
        mCurrentModuleResult.addRuntime(elapsedTime);
        if (!mModuleWasDone && mCanMarkDone && !mTestRunFailed) {
            // Only mark module done if:
            // - status of the invocation allows it (mCanMarkDone), and
            // - module has not already been marked done, and
            // - no test run failure has been detected
            mCurrentModuleResult.setDone(mCurrentTestNum >= mTotalTestsInModule);
        }
        if (isShardResultReporter()) {
            // Forward module results to the master.
            mMasterResultReporter.mergeModuleResult(mCurrentModuleResult);
            mCurrentModuleResult.resetTestRuns();
            mCurrentModuleResult.resetRuntime();
        }
    }

    /**
     * Directly add a module result. Note: this method is meant to be used by
     * a shard ResultReporter.
     */
    private void mergeModuleResult(IModuleResult moduleResult) {
        // This merges the results in moduleResult to any existing results already
        // contained in mResult. This is useful for retries and allows the final
        // report from a retry to contain all test results.
        synchronized(this) {
            mResult.mergeModuleResult(moduleResult);
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void testRunFailed(String errorMessage) {
        mTestRunFailed = true;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public TestSummary getSummary() {
        // ignore
        return null;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void putSummary(List<TestSummary> summaries) {
        // This is safe to be invoked on either the master or a shard ResultReporter,
        // but the value added to the report will be that of the master ResultReporter.
        if (summaries.size() > 0) {
            mReferenceUrl = summaries.get(0).getSummary().getString();
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void invocationEnded(long elapsedTime) {
        if (isShardResultReporter()) {
            // Shard ResultReporters report
            mMasterResultReporter.invocationEnded(elapsedTime);
            return;
        }

        // NOTE: Everything after this line only applies to the master ResultReporter.

        synchronized(this) {
            // The master ResultReporter tracks the progress of all invocations across
            // shard ResultReporters. Writing results should not proceed until all
            // ResultReporters have completed.
            if (++invocationEndedCount < mMasterBuildInfos.size()) {
                return;
            }
            mElapsedTime = elapsedTime;
            finalizeResults();
            mFinalized.countDown();
        }
    }

    private void finalizeResults() {
        // Add all device serials into the result to be serialized
        for (String deviceSerial : mMasterDeviceSerials) {
            mResult.addDeviceSerial(deviceSerial);
        }

        addDeviceBuildInfoToResult();

        Set<String> allExpectedModules = new HashSet<>();
        for (IBuildInfo buildInfo : mMasterBuildInfos) {
            for (Map.Entry<String, String> entry : buildInfo.getBuildAttributes().entrySet()) {
                String key = entry.getKey();
                String value = entry.getValue();
                if (key.equals(CompatibilityBuildHelper.MODULE_IDS) && value.length() > 0) {
                    Collections.addAll(allExpectedModules, value.split(","));
                }
            }
        }

        // Include a record in the report of all expected modules ids, even if they weren't
        // executed.
        for (String moduleId : allExpectedModules) {
            mResult.getOrCreateModule(moduleId);
        }

        String moduleProgress = String.format("%d of %d",
                mResult.getModuleCompleteCount(), mResult.getModules().size());


        try {
            // Zip the full test results directory.
            copyDynamicConfigFiles();
            copyFormattingFiles(mResultDir, mBuildHelper.getSuiteName());

            File resultFile = generateResultXmlFile();
            if (mRetrySessionId != null) {
                copyRetryFiles(ResultHandler.getResultDirectory(
                        mBuildHelper.getResultsDir(), mRetrySessionId), mResultDir);
            }
            File zippedResults = zipResults(mResultDir);
            // Create failure report after zip file so extra data is not uploaded
            File failureReport = ResultHandler.createFailureReport(resultFile);
            if (failureReport.exists()) {
                info("Test Result: %s", failureReport.getCanonicalPath());
            } else {
                info("Test Result: %s", resultFile.getCanonicalPath());
            }
            info("Test Logs: %s", mLogDir.getCanonicalPath());
            debug("Full Result: %s", zippedResults.getCanonicalPath());

            saveLog(resultFile, zippedResults);

            uploadResult(resultFile);

        } catch (IOException | XmlPullParserException e) {
            CLog.e("[%s] Exception while saving result XML.", mDeviceSerial);
            CLog.e(e);
        }
        // print the run results last.
        info("Invocation finished in %s. PASSED: %d, FAILED: %d, MODULES: %s",
                TimeUtil.formatElapsedTime(mElapsedTime),
                mResult.countResults(TestStatus.PASS),
                mResult.countResults(TestStatus.FAIL),
                moduleProgress);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void invocationFailed(Throwable cause) {
        warn("Invocation failed: %s", cause);
        InvocationFailureHandler.setFailed(mBuildHelper, cause);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void testLog(String name, LogDataType type, InputStreamSource stream) {
        // This is safe to be invoked on either the master or a shard ResultReporter
        if (isShardResultReporter()) {
            // Shard ResultReporters forward testLog to the mMasterResultReporter
            mMasterResultReporter.testLog(name, type, stream);
            return;
        }
        try {
            File logFile = null;
            if (mCompressLogs) {
                try (InputStream inputStream = stream.createInputStream()) {
                    logFile = mTestLogSaver.saveAndGZipLogData(name, type, inputStream);
                }
            } else {
                try (InputStream inputStream = stream.createInputStream()) {
                    logFile = mTestLogSaver.saveLogData(name, type, inputStream);
                }
            }
            debug("Saved logs for %s in %s", name, logFile.getAbsolutePath());
        } catch (IOException e) {
            warn("Failed to write log for %s", name);
            CLog.e(e);
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void testLogSaved(String dataName, LogDataType dataType, InputStreamSource dataStream,
            LogFile logFile) {
        // This is safe to be invoked on either the master or a shard ResultReporter
        if (mIncludeTestLogTags && mCurrentResult != null
                && dataName.startsWith(mCurrentResult.getFullName())) {

            if (dataType == LogDataType.BUGREPORT) {
                mCurrentResult.setBugReport(logFile.getUrl());
            } else if (dataType == LogDataType.LOGCAT) {
                mCurrentResult.setLog(logFile.getUrl());
            } else if (dataType == LogDataType.PNG) {
                mCurrentResult.setScreenshot(logFile.getUrl());
            }
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void setLogSaver(ILogSaver saver) {
        // This is safe to be invoked on either the master or a shard ResultReporter
        mLogSaver = saver;
    }

    /**
     * When enabled, save log data using log saver
     */
    private void saveLog(File resultFile, File zippedResults) throws IOException {
        if (!mUseLogSaver) {
            return;
        }

        FileInputStream fis = null;
        LogFile logFile = null;
        try {
            fis = new FileInputStream(resultFile);
            logFile = mLogSaver.saveLogData("log-result", LogDataType.XML, fis);
            debug("Result XML URL: %s", logFile.getUrl());
        } catch (IOException ioe) {
            CLog.e("[%s] error saving XML with log saver", mDeviceSerial);
            CLog.e(ioe);
        } finally {
            StreamUtil.close(fis);
        }
        // Save the full results folder.
        if (zippedResults != null) {
            FileInputStream zipResultStream = null;
            try {
                zipResultStream = new FileInputStream(zippedResults);
                logFile = mLogSaver.saveLogData("results", LogDataType.ZIP, zipResultStream);
                debug("Result zip URL: %s", logFile.getUrl());
            } finally {
                StreamUtil.close(zipResultStream);
            }
        }
    }

    /**
     * Return the path in which log saver persists log files or null if
     * logSaver is not enabled.
     */
    private String getLogUrl() {
        if (!mUseLogSaver || mLogSaver == null) {
            return null;
        }

        return mLogSaver.getLogReportDir().getUrl();
    }

    @Override
    public IShardableListener clone() {
        ResultReporter clone = new ResultReporter(this);
        OptionCopier.copyOptionsNoThrow(this, clone);
        return clone;
    }

    /**
     * Create results file compatible with CTSv2 (xml) report format.
     */
    protected File generateResultXmlFile()
            throws IOException, XmlPullParserException {
        return ResultHandler.writeResults(mBuildHelper.getSuiteName(),
                mBuildHelper.getSuiteVersion(), mBuildHelper.getSuitePlan(),
                mBuildHelper.getSuiteBuild(), mResult, mResultDir, mResult.getStartTime(),
                mElapsedTime + mResult.getStartTime(), mReferenceUrl, getLogUrl(),
                mBuildHelper.getCommandLineArgs());
    }

    /**
     * Add build info collected from the device attributes to the results.
     */
    protected void addDeviceBuildInfoToResult() {
        // Add all build info to the result to be serialized
        Map<String, String> buildProperties = mapBuildInfo();
        addBuildInfoToResult(buildProperties, mResult);
    }

    /**
     * Override specific build properties so the report will be associated with the
     * build fingerprint being certified.
     */
    protected void addDeviceBuildInfoToResult(String buildFingerprintOverride,
            String manufactureOverride, String modelOverride) {

        Map<String, String> buildProperties = mapBuildInfo();

        // Extract and override values from build fingerprint.
        // Build fingerprint format: brand/product/device:version/build_id/tags
        String fingerprintPrefix = buildFingerprintOverride.split(":")[0];
        String fingerprintTail = buildFingerprintOverride.split(":")[1];
        String buildIdOverride = fingerprintTail.split("/")[1];
        buildProperties.put(BUILD_ID, buildIdOverride);
        String brandOverride = fingerprintPrefix.split("/")[0];
        buildProperties.put(BUILD_BRAND, brandOverride);
        String deviceOverride = fingerprintPrefix.split("/")[2];
        buildProperties.put(BUILD_DEVICE, deviceOverride);
        String productOverride = fingerprintPrefix.split("/")[1];
        buildProperties.put(BUILD_PRODUCT, productOverride);
        String versionOverride = fingerprintTail.split("/")[0];
        buildProperties.put(BUILD_VERSION_RELEASE, versionOverride);
        buildProperties.put(BUILD_FINGERPRINT, buildFingerprintOverride);
        buildProperties.put(BUILD_MANUFACTURER, manufactureOverride);
        buildProperties.put(BUILD_MODEL, modelOverride);

        // Add modified values to results.
        addBuildInfoToResult(buildProperties, mResult);
        mResult.setBuildFingerprint(buildFingerprintOverride);
    }
    /** Aggregate build info from member device info. */
    protected Map<String, String> mapBuildInfo() {
        Map<String, String> buildProperties = new HashMap<>();
        for (IBuildInfo buildInfo : mMasterBuildInfos) {
            for (Map.Entry<String, String> entry : buildInfo.getBuildAttributes().entrySet()) {
                String key = entry.getKey();
                String value = entry.getValue();
                if (key.startsWith(BUILD_INFO)) {
                    buildProperties.put(key.substring(CTS_PREFIX.length()), value);
                }
            }
        }
        return buildProperties;
    }

    /**
     * Add build info to results.
     * @param buildProperties Build info to add.
     */
    protected static void addBuildInfoToResult(Map<String, String> buildProperties,
            IInvocationResult invocationResult) {
        buildProperties.entrySet().stream().forEach(entry ->
                invocationResult.addInvocationInfo(entry.getKey(), entry.getValue()));
    }

    /**
     * Return true if this instance is a shard ResultReporter and should propagate
     * certain events to the master.
     */
    private boolean isShardResultReporter() {
        return mMasterResultReporter != null;
    }

    /**
     * When enabled, upload the result to a server.
     */
    private void uploadResult(File resultFile) {
        if (mResultServer != null && !mResultServer.trim().isEmpty() && !mDisableResultPosting) {
            try {
                debug("Result Server: %d", mUploader.uploadResult(resultFile, mReferenceUrl));
            } catch (IOException ioe) {
                CLog.e("[%s] IOException while uploading result.", mDeviceSerial);
                CLog.e(ioe);
            }
        }
    }

    /**
     * Returns whether it is safe to mark modules as "done", given the invocation command-line
     * arguments. Returns true unless this is a retry and specific filtering techniques are applied
     * on the command-line, such as:
     *   --retry-type failed
     *   --include-filter
     *   --exclude-filter
     *   -t/--test
     *   --subplan
     */
    private boolean canMarkDone(String args) {
        if (mRetrySessionId == null) {
            return true; // always allow modules to be marked done if not retry
        }
        return !(RetryType.FAILED.equals(mRetryType)
                || RetryType.CUSTOM.equals(mRetryType)
                || args.contains(CompatibilityTest.INCLUDE_FILTER_OPTION)
                || args.contains(CompatibilityTest.EXCLUDE_FILTER_OPTION)
                || args.contains(CompatibilityTest.SUBPLAN_OPTION)
                || args.matches(String.format(".* (-%s|--%s) .*",
                CompatibilityTest.TEST_OPTION_SHORT_NAME, CompatibilityTest.TEST_OPTION)));
    }

    /**
     * Copy the xml formatting files stored in this jar to the results directory
     *
     * @param resultsDir
     */
    static void copyFormattingFiles(File resultsDir, String suiteName) {
        for (String resultFileName : ResultHandler.RESULT_RESOURCES) {
            InputStream configStream = ResultHandler.class.getResourceAsStream(
                    String.format("/report/%s-%s", suiteName, resultFileName));
            if (configStream == null) {
                // If suite specific files are not available, fallback to common.
                configStream = ResultHandler.class.getResourceAsStream(
                    String.format("/report/%s", resultFileName));
            }
            if (configStream != null) {
                File resultFile = new File(resultsDir, resultFileName);
                try {
                    FileUtil.writeToFile(configStream, resultFile);
                } catch (IOException e) {
                    warn("Failed to write %s to file", resultFileName);
                }
            } else {
                warn("Failed to load %s from jar", resultFileName);
            }
        }
    }

    /**
     * move the dynamic config files to the results directory
     */
    private void copyDynamicConfigFiles() {
        File configDir = new File(mResultDir, "config");
        if (!configDir.mkdir()) {
            warn("Failed to make dynamic config directory \"%s\" in the result",
                    configDir.getAbsolutePath());
        }

        Set<String> uniqueModules = new HashSet<>();
        for (IBuildInfo buildInfo : mMasterBuildInfos) {
            CompatibilityBuildHelper helper = new CompatibilityBuildHelper(buildInfo);
            Map<String, File> dcFiles = helper.getDynamicConfigFiles();
            for (String moduleName : dcFiles.keySet()) {
                File srcFile = dcFiles.get(moduleName);
                if (!uniqueModules.contains(moduleName)) {
                    // have not seen config for this module yet, copy into result
                    File destFile = new File(configDir, moduleName + ".dynamic");
                    try {
                        FileUtil.copyFile(srcFile, destFile);
                        uniqueModules.add(moduleName); // Add to uniqueModules if copy succeeds
                    } catch (IOException e) {
                        warn("Failure when copying config file \"%s\" to \"%s\" for module %s",
                                srcFile.getAbsolutePath(), destFile.getAbsolutePath(), moduleName);
                        CLog.e(e);
                    }
                }
                FileUtil.deleteFile(srcFile);
            }
        }
    }

    /**
     * Recursively copy any other files found in the previous session's result directory to the
     * new result directory, so long as they don't already exist. For example, a "screenshots"
     * directory generated in a previous session by a passing test will not be generated on retry
     * unless copied from the old result directory.
     *
     * @param oldDir
     * @param newDir
     */
    static void copyRetryFiles(File oldDir, File newDir) {
        File[] oldChildren = oldDir.listFiles();
        for (File oldChild : oldChildren) {
            if (NOT_RETRY_FILES.contains(oldChild.getName())) {
                continue; // do not copy this file/directory or its children
            }
            File newChild = new File(newDir, oldChild.getName());
            if (!newChild.exists()) {
                // If this old file or directory doesn't exist in new dir, simply copy it
                try {
                    if (oldChild.isDirectory()) {
                        FileUtil.recursiveCopy(oldChild, newChild);
                    } else {
                        FileUtil.copyFile(oldChild, newChild);
                    }
                } catch (IOException e) {
                    warn("Failed to copy file \"%s\" from previous session", oldChild.getName());
                }
            } else if (oldChild.isDirectory() && newChild.isDirectory()) {
                // If both children exist as directories, make sure the children of the old child
                // directory exist in the new child directory.
                copyRetryFiles(oldChild, newChild);
            }
        }
    }

    /**
     * Zip the contents of the given results directory.
     *
     * @param resultsDir
     */
    private static File zipResults(File resultsDir) {
        File zipResultFile = null;
        try {
            // create a file in parent directory, with same name as resultsDir
            zipResultFile = new File(resultsDir.getParent(), String.format("%s.zip",
                    resultsDir.getName()));
            ZipUtil.createZip(resultsDir, zipResultFile);
        } catch (IOException e) {
            warn("Failed to create zip for %s", resultsDir.getName());
        }
        return zipResultFile;
    }

    /**
     *  Log info to the console.
     */
    private static void info(String format, Object... args) {
        log(LogLevel.INFO, format, args);
    }

    /**
     *  Log debug to the console.
     */
    private static void debug(String format, Object... args) {
        log(LogLevel.DEBUG, format, args);
    }

    /**
     *  Log a warning to the console.
     */
    private static void warn(String format, Object... args) {
        log(LogLevel.WARN, format, args);
    }

    /**
     * Log a message to the console
     */
    private static void log(LogLevel level, String format, Object... args) {
        CLog.logAndDisplay(level, format, args);
    }

    /**
     * For testing purpose.
     */
    @VisibleForTesting
    public IInvocationResult getResult() {
        return mResult;
    }

    /**
     * Returns true if the reporter is finalized before the end of the timeout. False otherwise.
     */
    @VisibleForTesting
    public boolean waitForFinalized(long timeout, TimeUnit unit) throws InterruptedException {
        return mFinalized.await(timeout, unit);
    }
}
