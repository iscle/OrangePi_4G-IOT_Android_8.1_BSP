/*
 * Copyright (C) 2016 The Android Open Source Project
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
import com.android.compatibility.common.tradefed.testtype.ISubPlan;
import com.android.compatibility.common.tradefed.testtype.SubPlan;
import com.android.compatibility.common.tradefed.util.OptionHelper;
import com.android.compatibility.common.util.ICaseResult;
import com.android.compatibility.common.util.IInvocationResult;
import com.android.compatibility.common.util.IModuleResult;
import com.android.compatibility.common.util.ITestResult;
import com.android.compatibility.common.util.ResultHandler;
import com.android.compatibility.common.util.TestFilter;
import com.android.compatibility.common.util.TestStatus;
import com.android.ddmlib.Log.LogLevel;
import com.android.tradefed.config.ArgsOptionParser;
import com.android.tradefed.config.ConfigurationException;
import com.android.tradefed.config.Option;
import com.android.tradefed.config.Option.Importance;
import com.android.tradefed.log.LogUtil.CLog;
import com.android.tradefed.util.xml.AbstractXmlParser.ParseException;
import com.android.tradefed.util.StreamUtil;

import com.google.common.annotations.VisibleForTesting;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.IOException;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

/**
 * Class for creating subplans from compatibility result XML.
 */
public class SubPlanHelper {

    private static final String XML_EXT = ".xml";

    // string signalling the beginning of the parameter in a test name
    private static final String PARAM_START = "[";

    // result types
    public static final String PASSED = "passed";
    public static final String FAILED = "failed";
    public static final String NOT_EXECUTED = "not_executed";
    // static mapping of result types to TestStatuses
    private static final Map<String, TestStatus> STATUS_MAP;
    static {
        Map<String, TestStatus> statusMap = new HashMap<String, TestStatus>();
        statusMap.put(PASSED, TestStatus.PASS);
        statusMap.put(FAILED, TestStatus.FAIL);
        STATUS_MAP = Collections.unmodifiableMap(statusMap);
    }

    @Option (name = "name", shortName = 'n', description = "the name of the subplan to create",
            importance=Importance.IF_UNSET)
    private String mSubPlanName = null;

    @Option (name = "session", description = "the session id to derive from",
            importance=Importance.IF_UNSET)
    private Integer mSessionId = null;

    @Option (name = "result-type",
            description = "the result type to include. One of passed, failed, not_executed."
            + " Option may be repeated",
            importance=Importance.IF_UNSET)
    private Set<String> mResultTypes = new HashSet<String>();

    @Option(name = CompatibilityTest.INCLUDE_FILTER_OPTION,
            description = "the include module filters to apply.",
            importance = Importance.NEVER)
    private Set<String> mIncludeFilters = new HashSet<String>();

    @Option(name = CompatibilityTest.EXCLUDE_FILTER_OPTION,
            description = "the exclude module filters to apply.",
            importance = Importance.NEVER)
    private Set<String> mExcludeFilters = new HashSet<String>();

    @Option(name = CompatibilityTest.MODULE_OPTION, shortName = 'm',
            description = "the test module to run.",
            importance = Importance.NEVER)
    private String mModuleName = null;

    @Option(name = CompatibilityTest.TEST_OPTION, shortName = 't',
            description = "the test to run.",
            importance = Importance.NEVER)
    private String mTestName = null;

    @Option(name = CompatibilityTest.ABI_OPTION, shortName = 'a',
            description = "the abi to test.",
            importance = Importance.NEVER)
    private String mAbiName = null;

    File mSubPlanFile = null;
    IInvocationResult mResult = null;

    /**
     * Create an empty {@link SubPlanHelper}.
     * <p/>
     * All {@link Option} fields must be populated via
     * {@link com.android.tradefed.config.ArgsOptionParser}
     */
    public SubPlanHelper() {}

    /**
     * Create a {@link SubPlanHelper} using the specified option values.
     */
    public SubPlanHelper(String name, int session, Collection<String> resultTypes) {
        mSubPlanName = name;
        mSessionId = session;
        mResultTypes.addAll(resultTypes);
    }

    public static ISubPlan getSubPlanByName(CompatibilityBuildHelper buildHelper, String name) {
        if (!name.endsWith(XML_EXT)) {
            name = name + XML_EXT; // only append XML extension to name if not already there
        }
        InputStream subPlanInputStream = null;
        try {
            File subPlanFile = new File(buildHelper.getSubPlansDir(), name);
            if (!subPlanFile.exists()) {
                throw new IllegalArgumentException(
                        String.format("Could not retrieve subplan \"%s\"", name));
            }
            subPlanInputStream = new FileInputStream(subPlanFile);
            ISubPlan subPlan = new SubPlan();
            subPlan.parse(subPlanInputStream);
            return subPlan;
        } catch (FileNotFoundException | ParseException e) {
            throw new RuntimeException(
                    String.format("Unable to find or parse subplan %s", name), e);
        } finally {
            StreamUtil.closeStream(subPlanInputStream);
        }
    }

    /**
     * Set the result from which to derive the subplan.
     * @param result
     */
    public void setResult(IInvocationResult result) {
        mResult = result;
    }

    /**
     * Add a result type from which to derive the subplan. PASSED, FAILED, or NOT_EXECUTED
     * @param resultType
     */
    public void addResultType(String resultType) {
        mResultTypes.add(resultType);
    }

    /**
     * Create and serialize a subplan derived from a result.
     * <p/>
     * {@link Option} values must all be set before this is called.
     * @return serialized subplan file.
     * @throws ConfigurationException
     */
    public File createAndSerializeSubPlan(CompatibilityBuildHelper buildHelper)
            throws ConfigurationException {
        ISubPlan subPlan = createSubPlan(buildHelper);
        if (subPlan != null) {
            try {
                subPlan.serialize(new BufferedOutputStream(new FileOutputStream(mSubPlanFile)));
                CLog.logAndDisplay(LogLevel.INFO, "Created subplan \"%s\" at %s",
                        mSubPlanName, mSubPlanFile.getAbsolutePath());
                return mSubPlanFile;
            } catch (IOException e) {
                CLog.e("Failed to create plan file %s", mSubPlanFile.getAbsolutePath());
                CLog.e(e);
            }
        }
        return null;
    }

    /**
     * Create a subplan derived from a result.
     * <p/>
     * {@link Option} values must be set before this is called.
     * @param build
     * @return subplan
     * @throws ConfigurationException
     */
    public ISubPlan createSubPlan(CompatibilityBuildHelper buildHelper)
            throws ConfigurationException {
        setupFields(buildHelper);
        ISubPlan subPlan = new SubPlan();

        // add filters from previous session to track which tests must run
        subPlan.addAllIncludeFilters(mIncludeFilters);
        subPlan.addAllExcludeFilters(mExcludeFilters);
        if (mModuleName != null) {
            addIncludeToSubPlan(subPlan, new TestFilter(mAbiName, mModuleName, mTestName));
        }
        Set<TestStatus> statusesToRun = getStatusesToRun();
        for (IModuleResult module : mResult.getModules()) {
            if (shouldRunModule(module)) {
                TestFilter moduleInclude =
                            new TestFilter(module.getAbi(), module.getName(), null /*test*/);
                if (shouldRunEntireModule(module)) {
                    // include entire module
                    addIncludeToSubPlan(subPlan, moduleInclude);
                } else if (mResultTypes.contains(NOT_EXECUTED) && !module.isDone()) {
                    // add module include and test excludes
                    addIncludeToSubPlan(subPlan, moduleInclude);
                    for (ICaseResult caseResult : module.getResults()) {
                        for (ITestResult testResult : caseResult.getResults()) {
                            if (!statusesToRun.contains(testResult.getResultStatus())) {
                                TestFilter testExclude = new TestFilter(module.getAbi(),
                                        module.getName(), testResult.getFullName());
                                addExcludeToSubPlan(subPlan, testExclude);
                            }
                        }
                    }
                } else {
                    // Not-executed tests should not be rerun and/or this module is completed
                    // In any such case, it suffices to add includes for each test to rerun
                    for (ICaseResult caseResult : module.getResults()) {
                        for (ITestResult testResult : caseResult.getResults()) {
                            if (statusesToRun.contains(testResult.getResultStatus())) {
                                TestFilter testInclude = new TestFilter(module.getAbi(),
                                        module.getName(), testResult.getFullName());
                                addIncludeToSubPlan(subPlan, testInclude);
                            }
                        }
                    }
                }
            } else {
                // module should not run, exclude entire module
                TestFilter moduleExclude =
                        new TestFilter(module.getAbi(), module.getName(), null /*test*/);
                addExcludeToSubPlan(subPlan, moduleExclude);
            }
        }
        return subPlan;
    }

    /**
     * Add the include test filter to the subplan. For filters that specify the parameters of a
     * test, strip the parameter suffix and add the include, which will run the test with all
     * parameters. If JUnit test runners are extended to handle filtering by parameter, this
     * special case may be removed.
     */
    @VisibleForTesting
    static void addIncludeToSubPlan(ISubPlan subPlan, TestFilter include) {
        String test = include.getTest();
        String str = include.toString();
        if (test == null || !test.contains(PARAM_START)) {
            subPlan.addIncludeFilter(str);
        } else if (test.contains(PARAM_START)) {
            // filter applies to parameterized test, include test without parameter.
            subPlan.addIncludeFilter(str.substring(0, str.lastIndexOf(PARAM_START)));
        }
    }

    /**
     * Add the exclude test filter to the subplan. For filters that specify the parameters of a
     * test, do not add the exclude filter. This will prompt the test to run again with all
     * parameters. If JUnit test runners are extended to handle filtering by parameter, this
     * special case may be removed.
     */
    @VisibleForTesting
    static void addExcludeToSubPlan(ISubPlan subPlan, TestFilter exclude) {
        String test = exclude.getTest();
        String str = exclude.toString();
        if (test == null || !test.contains(PARAM_START)) {
            subPlan.addExcludeFilter(str);
        }
        // don't add exclude for parameterized test, as runners do not support this.
    }

    /**
     * Whether any tests within the given {@link IModuleResult} should be run, based on
     * the content of mResultTypes.
     * @param module
     * @return true if at least one test in the module should run
     */
    private boolean shouldRunModule(IModuleResult module) {
        if (mResultTypes.contains(NOT_EXECUTED) && !module.isDone()) {
            // module has not executed tests that the subplan should run
            return true;
        }
        for (TestStatus status : getStatusesToRun()) {
            if (module.countResults(status) > 0) {
                return true;
            }
        }
        return false;
    }

    /**
     * Whether all tests within the given {@link IModuleResult} should be run, based on
     * the content of mResultTypes.
     * @param module
     * @return true if every test in the module should run
     */
    private boolean shouldRunEntireModule(IModuleResult module) {
        if (!mResultTypes.contains(NOT_EXECUTED) && !module.isDone()) {
            // module has not executed tests that the subplan should not run
            return false;
        }
        Set<TestStatus> statusesToRun = getStatusesToRun();
        for (TestStatus status : TestStatus.values()) {
            if (!statusesToRun.contains(status)) {
                // status is a TestStatus we don't want to run
                if (module.countResults(status) > 0) {
                    return false;
                }
            }
        }
        return true;
    }

    /**
     * Retrieves a {@link Set} of {@link TestStatus}es to run, based on the content of
     * mResultTypes. Does not account for result type NOT_EXECUTED, since no such TestStatus
     * exists.
     * @return set of TestStatuses to run
     */
    private Set<TestStatus> getStatusesToRun() {
        Set<TestStatus> statusesToRun = new HashSet<TestStatus>();
        for (String resultType : mResultTypes) {
            // no test status exists for not-executed tests
            if (!NOT_EXECUTED.equals(resultType)) {
                statusesToRun.add(STATUS_MAP.get(resultType));
            }
        }
        return statusesToRun;
    }

    /**
     * Ensure that all {@Option}s and fields are populated with valid values.
     * @param buildHelper
     * @throws ConfigurationException if any option has an invalid value
     */
    private void setupFields(CompatibilityBuildHelper buildHelper) throws ConfigurationException {
        if (mResult == null) {
            if (mSessionId == null) {
                throw new ConfigurationException("Missing --session argument");
            }
            try {
                mResult = ResultHandler.findResult(buildHelper.getResultsDir(), mSessionId);
            } catch (FileNotFoundException e) {
                throw new RuntimeException(e);
            }
            if (mResult == null) {
                throw new IllegalArgumentException(String.format(
                        "Could not find session with id %d", mSessionId));
            }
        }

        String retryCommandLineArgs = mResult.getCommandLineArgs();
        if (retryCommandLineArgs != null) {
            try {
                // parse the command-line string from the result file and set options
                ArgsOptionParser parser = new ArgsOptionParser(this);
                parser.parse(OptionHelper.getValidCliArgs(retryCommandLineArgs, this));
            } catch (ConfigurationException e) {
                throw new RuntimeException(e);
            }
        }

        if (mResultTypes.isEmpty()) {
            // add all valid values, include all tests of all statuses
            mResultTypes.addAll(
                    new HashSet<String>(Arrays.asList(PASSED, FAILED, NOT_EXECUTED)));
        }
        // validate all test status values
        for (String type : mResultTypes) {
            if (!type.equals(PASSED) && !type.equals(FAILED) && !type.equals(NOT_EXECUTED)) {
                throw new ConfigurationException(String.format("result type %s invalid", type));
            }
        }

        if (mSubPlanName == null) {
            mSubPlanName = createPlanName();
        }
        try {
            mSubPlanFile = new File(buildHelper.getSubPlansDir(), mSubPlanName + XML_EXT);
            if (mSubPlanFile.exists()) {
                throw new ConfigurationException(String.format("Subplan %s already exists",
                        mSubPlanName));
            }
        } catch (IOException e) {
            throw new RuntimeException("Could not find subplans directory");
        }
    }

    /**
     * Helper to create a plan name if none is explicitly set
     */
    private String createPlanName() {
        StringBuilder sb = new StringBuilder();
        sb.append(String.join("_", mResultTypes));
        sb.append("_");
        if (mSessionId != null) {
            sb.append(Integer.toString(mSessionId));
            sb.append("_");
        }
        // use unique start time for name
        sb.append(CompatibilityBuildHelper.getDirSuffix(mResult.getStartTime()));
        return sb.toString();
    }
}
