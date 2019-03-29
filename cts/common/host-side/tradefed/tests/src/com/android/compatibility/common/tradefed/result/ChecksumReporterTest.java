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

import com.android.compatibility.common.tradefed.build.CompatibilityBuildProvider;
import com.android.compatibility.common.util.ChecksumReporter;
import com.android.compatibility.common.util.ChecksumReporter.ChecksumValidationException;
import com.android.compatibility.common.util.ICaseResult;
import com.android.compatibility.common.util.IInvocationResult;
import com.android.compatibility.common.util.IModuleResult;
import com.android.compatibility.common.util.ITestResult;
import com.android.compatibility.common.util.ReportLog;
import com.android.compatibility.common.util.TestStatus;
import com.android.tradefed.build.IBuildInfo;
import com.android.tradefed.config.OptionSetter;
import com.android.tradefed.invoker.IInvocationContext;
import com.android.tradefed.invoker.InvocationContext;
import com.android.tradefed.util.FileUtil;

import junit.framework.TestCase;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;

/**
 * Unit tests for {@link ChecksumReporter}
 */
public class ChecksumReporterTest extends TestCase {

    private static final String ROOT_PROPERTY = "TESTS_ROOT";
    private static final String ROOT_DIR_NAME = "root";
    private static final String SUITE_NAME = "TESTS";
    private static final String BUILD_NUMBER = "2";
    private static final String SUITE_PLAN = "cts";
    private static final String BASE_DIR_NAME = "android-tests";
    private static final String TESTCASES = "testcases";

    private ChecksumReporter mReporter;
    private File mRoot = null;
    private IBuildInfo mBuildInfo;
    private ReportLog mReportLog = null;
    private IInvocationResult mInvocationResult;
    private IModuleResult mModuleResult;
    private ITestResult mFailedTest;

    @Override
    public void setUp() throws Exception {
        mReporter = new ChecksumReporter(100, .001, (short)1);
        mRoot = FileUtil.createTempDir(ROOT_DIR_NAME);
        File baseDir = new File(mRoot, BASE_DIR_NAME);
        baseDir.mkdirs();
        File testDir = new File(baseDir, TESTCASES);
        testDir.mkdirs();
        System.setProperty(ROOT_PROPERTY, mRoot.getAbsolutePath());

        ResultReporter resultReporter = new ResultReporter();
        CompatibilityBuildProvider provider = new CompatibilityBuildProvider() {
            @Override
            protected String getSuiteInfoName() {
                return SUITE_NAME;
            }
            @Override
            protected String getSuiteInfoBuildNumber() {
                return BUILD_NUMBER;
            }
            @Override
            protected String getSuiteInfoVersion() {
                return BUILD_NUMBER;
            }
        };
        OptionSetter setter = new OptionSetter(provider);
        setter.setOptionValue("plan", SUITE_PLAN);
        setter.setOptionValue("dynamic-config-url", "");
        mBuildInfo = provider.getBuild();
        IInvocationContext context = new InvocationContext();
        context.addDeviceBuildInfo("fakeDevice", mBuildInfo);

        resultReporter.invocationStarted(context);
        mInvocationResult = resultReporter.getResult();
        mModuleResult = mInvocationResult.getOrCreateModule("Module-1");
        mModuleResult.setDone(true);
        ICaseResult caseResult = mModuleResult.getOrCreateResult("Case-1");
        ITestResult test1 = caseResult.getOrCreateResult("Test1");
        test1.passed(mReportLog);
        mFailedTest = caseResult.getOrCreateResult("Test2");
        mFailedTest.failed("stack-trace - error happened");

        IModuleResult moduleResult2 = mInvocationResult.getOrCreateModule("Module-2");
        ICaseResult caseResult2 = moduleResult2.getOrCreateResult("Case-2");
        mModuleResult.setDone(false);
        ITestResult test3 = caseResult2.getOrCreateResult("Test3");
        test3.passed(mReportLog);

    }

    @Override
    public void tearDown() throws Exception {
        mReporter = null;
        FileUtil.recursiveDelete(mRoot);
    }

    public void testStoreAndRetrieveTestResults() {
        mReporter.addInvocation(mInvocationResult);
        VerifyInvocationResults(mInvocationResult, mReporter);
    }

    /***
     * By definition this test is flaky since the checksum has a false positive probability of .1%
     */
    public void testInvalidChecksums() {
        mReporter.addInvocation(mInvocationResult);
        IModuleResult module = mInvocationResult.getModules().get(1);
        module.setDone(!module.isDone());
        String fingerprint = mInvocationResult.getBuildFingerprint();
        assertFalse("Checksum should contain module: " + module.getName(),
                mReporter.containsModuleResult(module, fingerprint));

        mFailedTest.setResultStatus(TestStatus.PASS);
        assertFalse("Checksum should not contain test: " + mFailedTest.getName(),
                mReporter.containsTestResult(mFailedTest, mModuleResult, fingerprint));
        assertFalse("Module checksum should verify number of tests",
                mReporter.containsModuleResult(mModuleResult, fingerprint));
    }

    public void testFileSerialization() throws IOException, ChecksumValidationException {
        mReporter.addInvocation(mInvocationResult);

        File file1 = new File(mRoot, "file1.txt");
        try (FileWriter fileWriter = new FileWriter(file1, false)) {
            fileWriter.append("This is a test file");
        }

        mReporter.addDirectory(mRoot);
        mReporter.saveToFile(mRoot);

        ChecksumReporter storedChecksum = ChecksumReporter.load(mRoot);
        VerifyInvocationResults(mInvocationResult, storedChecksum);
        assertTrue("Serializing checksum maintains file hash",
                storedChecksum.containsFile(file1, mRoot.getName()));
    }

    public void testFileCRCOperations() throws IOException {
        File subDirectory = new File(mRoot, "child");
        subDirectory.mkdir();
        File file1 = new File(mRoot, "file1.txt");
        try (FileWriter fileWriter = new FileWriter(file1, false)) {
            fileWriter.append("This is a test file");
        }

        File file2 = new File(subDirectory, "file2.txt");
        try (FileWriter fileWriter = new FileWriter(file2, false)) {
            fileWriter.append("This is another test file with a different crc");
        }

        mReporter.addDirectory(mRoot);
        String folderName = mRoot.getName();
        assertTrue(mReporter.containsFile(file1, folderName));
        assertTrue(mReporter.containsFile(file2, folderName + "/child"));
        assertFalse("Should not contain non-existent file",
                mReporter.containsFile(new File(mRoot, "fake.txt"), folderName));

        File file3 = new File(mRoot, "file3.txt");
        try (FileWriter fileWriter = new FileWriter(file3, false)) {
            fileWriter.append("This is a test file added after crc calculated");
        }
        assertFalse("Should not contain file created after crc calculated",
                mReporter.containsFile(file3, mRoot + "/"));

    }

    private void VerifyInvocationResults(IInvocationResult invocation, ChecksumReporter reporter) {
        for (IModuleResult module : invocation.getModules()) {
            String buildFingerprint = invocation.getBuildFingerprint();
            assertTrue("Checksum should contain module: " + module.getName(),
                    reporter.containsModuleResult(module, buildFingerprint));
            for (ICaseResult caseResult : module.getResults()) {
                for (ITestResult result : caseResult.getResults()) {
                    assertTrue("Checksum should contain test: " + result.getName(),
                            reporter.containsTestResult(result, module, buildFingerprint));
                }
            }
        }
    }
}
