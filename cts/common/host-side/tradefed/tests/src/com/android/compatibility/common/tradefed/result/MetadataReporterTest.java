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

package com.android.compatibility.common.tradefed.result;

import com.android.compatibility.common.tradefed.build.CompatibilityBuildHelper;
import com.android.compatibility.common.util.AbiUtils;
import com.android.ddmlib.testrunner.TestIdentifier;
import com.android.tradefed.build.BuildInfo;
import com.android.tradefed.build.IBuildInfo;
import com.android.tradefed.config.OptionSetter;
import com.android.tradefed.invoker.IInvocationContext;
import com.android.tradefed.invoker.InvocationContext;
import com.android.tradefed.util.FileUtil;
import com.android.tradefed.util.RunUtil;

import junit.framework.TestCase;

import java.io.File;
import java.util.Collection;
import java.util.HashMap;

/**
 * Unit Tests for {@link MetadataReporter}
 */
public class MetadataReporterTest extends TestCase {

    private static final String MIN_TEST_DURATION = "10";
    private static final String BUILD_NUMBER = "2";
    private static final String ROOT_DIR_NAME = "root";
    private static final String BASE_DIR_NAME = "android-tests";
    private static final String TESTCASES = "testcases";
    private static final String NAME = "ModuleName";
    private static final String ABI = "mips64";
    private static final String ID = AbiUtils.createId(ABI, NAME);
    private static final String CLASS = "android.test.FoorBar";
    private static final String METHOD_1 = "testBlah1";
    private static final String METHOD_2 = "testBlah2";
    private static final String METHOD_3 = "testBlah3";
    private static final String STACK_TRACE = "Something small is not alright\n " +
            "at four.big.insects.Marley.sing(Marley.java:10)";

    private MetadataReporter mReporter;
    private IBuildInfo mBuildInfo;
    private IInvocationContext mContext;

    private File mRoot = null;
    private File mBase = null;
    private File mTests = null;

    @Override
    public void setUp() throws Exception {
        mReporter = new MetadataReporter();
        OptionSetter setter = new OptionSetter(mReporter);
        setter.setOptionValue("min-test-duration", MIN_TEST_DURATION);
        mRoot = FileUtil.createTempDir(ROOT_DIR_NAME);
        mBase = new File(mRoot, BASE_DIR_NAME);
        mBase.mkdirs();
        mTests = new File(mBase, TESTCASES);
        mTests.mkdirs();
        mBuildInfo = new BuildInfo(BUILD_NUMBER, "");
        mBuildInfo.addBuildAttribute(CompatibilityBuildHelper.ROOT_DIR, mRoot.getAbsolutePath());
        mBuildInfo.addBuildAttribute(CompatibilityBuildHelper.SUITE_NAME, "tests");
        mBuildInfo.addBuildAttribute(CompatibilityBuildHelper.START_TIME_MS, "0");
        mContext = new InvocationContext();
        mContext.addDeviceBuildInfo("fakeDevice", mBuildInfo);
    }

    @Override
    public void tearDown() throws Exception {
        mReporter = null;
        FileUtil.recursiveDelete(mRoot);
    }

    /**
     * Test that when tests execute faster than the threshold we do not report then.
     */
    public void testResultReportingFastTests() throws Exception {
        mReporter.invocationStarted(mContext);
        mReporter.testRunStarted(ID, 3);
        runTests(0l);
        Collection<MetadataReporter.TestMetadata> metadata = mReporter.getTestMetadata();
        assertTrue(metadata.isEmpty());
        mReporter.testRunEnded(10, new HashMap<String, String>());
        mReporter.invocationEnded(10);
    }

    /**
     * Test that when tests execute slower than the limit we report them if they passed.
     */
    public void testResultReportingSlowTests() throws Exception {
        mReporter.invocationStarted(mContext);
        mReporter.testRunStarted(ID, 3);
        runTests(50l);

        Collection<MetadataReporter.TestMetadata> metadata = mReporter.getTestMetadata();
        assertEquals(metadata.size(), 2); // Two passing slow tests.

        mReporter.testRunEnded(10, new HashMap<String, String>());
        mReporter.invocationEnded(10);
    }

    /** Run 4 test. */
    private void runTests(long waitTime) {
        TestIdentifier test1 = new TestIdentifier(CLASS, METHOD_1);
        mReporter.testStarted(test1);
        RunUtil.getDefault().sleep(waitTime);
        mReporter.testEnded(test1, new HashMap<String, String>());

        TestIdentifier test2 = new TestIdentifier(CLASS, METHOD_2);
        mReporter.testStarted(test2);
        RunUtil.getDefault().sleep(waitTime);
        mReporter.testEnded(test1, new HashMap<String, String>());

        TestIdentifier test3 = new TestIdentifier(CLASS, METHOD_3);
        mReporter.testStarted(test3);
        RunUtil.getDefault().sleep(waitTime);
        mReporter.testFailed(test3, STACK_TRACE);
        mReporter.testEnded(test3, new HashMap<String, String>());

        TestIdentifier test4 = new TestIdentifier(CLASS, METHOD_3);
        mReporter.testStarted(test4);
        RunUtil.getDefault().sleep(waitTime);
        mReporter.testIgnored(test4);
        mReporter.testEnded(test4, new HashMap<String, String>());
    }
}
