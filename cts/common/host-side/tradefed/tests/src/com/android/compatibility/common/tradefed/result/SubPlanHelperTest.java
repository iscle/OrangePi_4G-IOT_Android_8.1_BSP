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
import com.android.compatibility.common.tradefed.testtype.ISubPlan;
import com.android.compatibility.common.tradefed.testtype.SubPlan;
import com.android.compatibility.common.util.ICaseResult;
import com.android.compatibility.common.util.IInvocationResult;
import com.android.compatibility.common.util.IModuleResult;
import com.android.compatibility.common.util.ITestResult;
import com.android.compatibility.common.util.InvocationResult;
import com.android.compatibility.common.util.ResultHandler;
import com.android.compatibility.common.util.TestFilter;
import com.android.compatibility.common.util.TestStatus;
import com.android.tradefed.build.BuildInfo;
import com.android.tradefed.build.IBuildInfo;
import com.android.tradefed.config.ArgsOptionParser;
import com.android.tradefed.util.AbiUtils;
import com.android.tradefed.util.FileUtil;

import junit.framework.TestCase;

import java.io.File;
import java.io.FileNotFoundException;
import java.util.Arrays;
import java.util.Set;

public class SubPlanHelperTest extends TestCase {

    // Values used to populate mock results
    private static final String SUITE_NAME = "CTS";
    private static final String SUITE_VERSION = "5.0";
    private static final String SUITE_PLAN = "cts";
    private static final String SUITE_BUILD = "12345";
    private static final String NAME_A = "ModuleA";
    private static final String NAME_B = "ModuleB";
    private static final String ABI = "mips64";
    private static final String ID_A = AbiUtils.createId(ABI, NAME_A);
    private static final String ID_B = AbiUtils.createId(ABI, NAME_B);
    private static final String BUILD_ID = "build_id";
    private static final String BUILD_PRODUCT = "build_product";
    private static final String EXAMPLE_BUILD_ID = "XYZ";
    private static final String EXAMPLE_BUILD_PRODUCT = "wolverine";
    private static final String DEVICE_A = "device123";
    private static final String DEVICE_B = "device456";
    private static final String CLASS_A = "android.test.Foor";
    private static final String CLASS_B = "android.test.Bar";
    private static final String METHOD_1 = "testBlah1";
    private static final String METHOD_2 = "testBlah2";
    private static final String METHOD_3 = "testBlah3";
    private static final String METHOD_4 = "testBlah4";
    private static final long START_MS = 1431586801000L;
    private static final long END_MS = 1431673199000L;
    private static final String REFERENCE_URL="http://android.com";
    private static final String LOG_URL ="file:///path/to/logs";
    private static final String COMMAND_LINE_ARGS = "cts -m CtsMyModuleTestCases";

    private static final String SP_NAME = "testsubplan";
    private static final String SP_SESSION = "0";
    private static final String SP_RESULT_TYPE_FAILED = "failed";
    private static final String SP_RESULT_TYPE_NOT_EXECUTED = "not_executed";

    private static final String PARAM_SUFFIX = "[0]";

    private CompatibilityBuildHelper mBuildHelper;
    private SubPlanHelper mSubPlanHelper;

    private File mResultsDir = null;
    private File mResultDir = null;
    private File mSubPlansDir = null;

    @Override
    public void setUp() throws Exception {
        mResultsDir = FileUtil.createTempDir("results");
        mResultDir = FileUtil.createTempDir("12345", mResultsDir);
        mSubPlansDir = FileUtil.createTempDir("subplans");
        mBuildHelper = new SpctMockCompatibilityBuildHelper(new BuildInfo("0", "", ""));
        populateResults();

        mSubPlanHelper = new SubPlanHelper();
        ArgsOptionParser optionParser = new ArgsOptionParser(mSubPlanHelper);
        optionParser.parse(Arrays.asList(
            "-n", SP_NAME,
            "--session", SP_SESSION,
            "--result-type", SP_RESULT_TYPE_FAILED,
            "--result-type", SP_RESULT_TYPE_NOT_EXECUTED));
    }

    @Override
    public void tearDown() throws Exception {
        if (mResultsDir != null) {
            FileUtil.recursiveDelete(mResultsDir);
        }
        if (mSubPlansDir != null) {
            FileUtil.recursiveDelete(mSubPlansDir);
        }
    }

    public void testCreateSubPlan() throws Exception {
        ISubPlan plan = mSubPlanHelper.createSubPlan(mBuildHelper);
        Set<String> planIncludes = plan.getIncludeFilters();
        Set<String> planExcludes = plan.getExcludeFilters();
        TestFilter mf1 = new TestFilter(ABI, NAME_A, null);
        TestFilter tf1 = new TestFilter(ABI, NAME_A, String.format("%s#%s", CLASS_A, METHOD_1));
        TestFilter tf3 = new TestFilter(ABI, NAME_B, String.format("%s#%s", CLASS_B, METHOD_3));
        assertTrue(planIncludes.contains("CtsMyModuleTestCases")); // command-line '-m' arg
        assertTrue(planIncludes.contains(mf1.toString())); // include module with not-executed test
        assertTrue(planExcludes.contains(tf1.toString())); // exclude passing test in that module
        assertTrue(planIncludes.contains(tf3.toString())); // include failure in executed module
    }

    public void testAddInclude() throws Exception {
        ISubPlan subPlan = new SubPlan();
        TestFilter tf = new TestFilter(ABI, NAME_A, String.format("%s#%s", CLASS_A, METHOD_1));
        SubPlanHelper.addIncludeToSubPlan(subPlan, tf);
        Set<String> includes = subPlan.getIncludeFilters();
        assertTrue(includes.contains(tf.toString()));
    }

    public void testAddExclude() throws Exception {
        ISubPlan subPlan = new SubPlan();
        TestFilter tf = new TestFilter(ABI, NAME_A, String.format("%s#%s", CLASS_A, METHOD_1));
        SubPlanHelper.addExcludeToSubPlan(subPlan, tf);
        Set<String> excludes = subPlan.getExcludeFilters();
        assertTrue(excludes.contains(tf.toString()));
    }

    public void testAddParameterizedInclude() throws Exception {
        ISubPlan subPlan = new SubPlan();
        TestFilter filterWithSuffix = new TestFilter(ABI, NAME_A,
                String.format("%s#%s%s", CLASS_A, METHOD_1, PARAM_SUFFIX));
        TestFilter filterWithoutSuffix = new TestFilter(ABI, NAME_A,
                String.format("%s#%s", CLASS_A, METHOD_1));
        SubPlanHelper.addIncludeToSubPlan(subPlan, filterWithSuffix);
        Set<String> includes = subPlan.getIncludeFilters();
        assertTrue(includes.contains(filterWithoutSuffix.toString()));
    }

    public void testAddParameterizedExclude() throws Exception {
        ISubPlan subPlan = new SubPlan();
        TestFilter filterWithSuffix = new TestFilter(ABI, NAME_A,
                String.format("%s#%s%s", CLASS_A, METHOD_1, PARAM_SUFFIX));
        SubPlanHelper.addExcludeToSubPlan(subPlan, filterWithSuffix);
        Set<String> excludes = subPlan.getExcludeFilters();
        assertTrue(excludes.isEmpty());
    }

    private void populateResults() throws Exception {
        // copied from ResultHandlerTest
        IInvocationResult result = new InvocationResult();
        result.setStartTime(START_MS);
        result.setTestPlan(SUITE_PLAN);
        result.addDeviceSerial(DEVICE_A);
        result.addDeviceSerial(DEVICE_B);
        result.addInvocationInfo(BUILD_ID, EXAMPLE_BUILD_ID);
        result.addInvocationInfo(BUILD_PRODUCT, EXAMPLE_BUILD_PRODUCT);
        IModuleResult moduleA = result.getOrCreateModule(ID_A);
        moduleA.setDone(false);
        ICaseResult moduleACase = moduleA.getOrCreateResult(CLASS_A);
        ITestResult moduleATest1 = moduleACase.getOrCreateResult(METHOD_1);
        moduleATest1.setResultStatus(TestStatus.PASS);
        ITestResult moduleATest2 = moduleACase.getOrCreateResult(METHOD_2);
        moduleATest2.setResultStatus(null); // not executed test

        IModuleResult moduleB = result.getOrCreateModule(ID_B);
        moduleB.setDone(true);
        ICaseResult moduleBCase = moduleB.getOrCreateResult(CLASS_B);
        ITestResult moduleBTest3 = moduleBCase.getOrCreateResult(METHOD_3);
        moduleBTest3.setResultStatus(TestStatus.FAIL);
        ITestResult moduleBTest4 = moduleBCase.getOrCreateResult(METHOD_4);
        moduleBTest4.setResultStatus(TestStatus.PASS);

        // Serialize to file
        ResultHandler.writeResults(SUITE_NAME, SUITE_VERSION, SUITE_PLAN, SUITE_BUILD,
                result, mResultDir, START_MS, END_MS, REFERENCE_URL, LOG_URL,
                COMMAND_LINE_ARGS);
    }

    private class SpctMockCompatibilityBuildHelper extends CompatibilityBuildHelper {

        public SpctMockCompatibilityBuildHelper(IBuildInfo buildInfo) {
            super(buildInfo);
        }

        @Override
        public File getResultsDir() throws FileNotFoundException {
            return mResultsDir;
        }

        @Override
        public File getSubPlansDir() throws FileNotFoundException {
            return mSubPlansDir;
        }
    }
}
