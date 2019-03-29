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
package com.android.compatibility.common.tradefed.presubmit;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import com.android.compatibility.common.tradefed.build.CompatibilityBuildHelper;
import com.android.compatibility.common.tradefed.result.ResultReporter;
import com.android.compatibility.common.tradefed.testtype.CompatibilityTest;
import com.android.compatibility.common.tradefed.testtype.IModuleDef;
import com.android.compatibility.common.util.IInvocationResult;
import com.android.compatibility.common.util.TestStatus;
import com.android.ddmlib.testrunner.TestIdentifier;
import com.android.tradefed.build.IBuildInfo;
import com.android.tradefed.config.ConfigurationFactory;
import com.android.tradefed.config.OptionSetter;
import com.android.tradefed.device.DeviceNotAvailableException;
import com.android.tradefed.device.ITestDevice;
import com.android.tradefed.invoker.IInvocationContext;
import com.android.tradefed.invoker.InvocationContext;
import com.android.tradefed.invoker.ShardListener;
import com.android.tradefed.result.ITestInvocationListener;
import com.android.tradefed.result.ResultForwarder;
import com.android.tradefed.testtype.IBuildReceiver;
import com.android.tradefed.testtype.IDeviceTest;
import com.android.tradefed.testtype.IInvocationContextReceiver;
import com.android.tradefed.testtype.IRemoteTest;
import com.android.tradefed.testtype.suite.TestSuiteInfo;
import com.android.tradefed.util.AbiUtils;
import com.android.tradefed.util.FileUtil;

import org.easymock.EasyMock;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;

/**
 * Integration tests between {@link CompatibilityTest} and {@link ResultReporter} to ensure proper
 * flow of run and results.
 */
@RunWith(JUnit4.class)
public class IntegrationTest {

    private static final String CONFIG =
            "<configuration description=\"Auto Generated File\">\n" +
            "<option name=\"config-descriptor:metadata\" key=\"component\" value=\"%s\" />\n" +
            "<test class=\"com.android.compatibility.common.tradefed.testtype.%s\">\n" +
            "    <option name=\"report-test\" value=\"%s\" />\n" +
            "    <option name=\"run-complete\" value=\"%s\" />\n" +
            "    <option name=\"test-fail\" value=\"%s\" />\n" +
            "    <option name=\"internal-retry\" value=\"%s\" />\n" +
            "</test>\n" +
            "</configuration>";
    private static final String FILENAME = "%s.config";
    private static final String TEST_STUB = "TestStub"; // Test stub
    private static final String SIMPLE_TEST_STUB = "SimpleTestStub"; // Simple test stub
    private static final String TEST_STUB_SHARDABLE = "TestStubShardable";
    private static final String COMMAND_LINE = "run cts";

    private CompatibilityTest mTest;
    private ResultReporter mReporter;
    private ITestDevice mMockDevice;
    private IBuildInfo mMockBuildInfo;
    private IInvocationContext mContext;

    private File mRootDir;
    private File mAndroidFolder;
    private File mTestDir;
    private Map<String, String> mAttributes;

    @Before
    public void setUp() throws IOException {
        mAttributes = new HashMap<>();
        mTest = new CompatibilityTest() {
            @Override
            protected Set<String> getAbisForBuildTargetArch() {
                Set<String> abis = new HashSet<>();
                abis.add("arm64-v8a");
                abis.add("armeabi-v7a");
                return abis;
            }
        };
        mReporter = new ResultReporter();
        mMockDevice = EasyMock.createMock(ITestDevice.class);
        mMockBuildInfo = EasyMock.createMock(IBuildInfo.class);
        mTest.setBuild(mMockBuildInfo);
        mTest.setDevice(mMockDevice);
        mRootDir = FileUtil.createTempDir("fake-cts-root-dir");
        mAndroidFolder = FileUtil.createTempDir("android-", mRootDir);
        mTestDir = new File(mAndroidFolder, "testcases");
        mTestDir.mkdirs();
        String suiteName = mAndroidFolder.getName().split("-")[1];
        // Create fake build attributes
        mAttributes.put(CompatibilityBuildHelper.ROOT_DIR, mRootDir.getAbsolutePath());
        mAttributes.put(CompatibilityBuildHelper.SUITE_NAME, suiteName);
        mAttributes.put(CompatibilityBuildHelper.START_TIME_MS, "0");
        mAttributes.put(CompatibilityBuildHelper.SUITE_VERSION, "10");
        mAttributes.put(CompatibilityBuildHelper.SUITE_PLAN, "cts");
        mAttributes.put(CompatibilityBuildHelper.SUITE_BUILD, "good-build");
        mAttributes.put(CompatibilityBuildHelper.COMMAND_LINE_ARGS, COMMAND_LINE);

        // these attributes seems necessary for re-run, not for run
        mAttributes.put("cts:build_fingerprint", "fingerprint");
        mAttributes.put("cts:build_product", "product");
        mAttributes.put("cts:build_id", "bid");

        EasyMock.expect(mMockBuildInfo.getBuildAttributes()).andStubReturn(mAttributes);

        EasyMock.expect(mMockDevice.getSerialNumber()).andStubReturn("SERIAL");
        EasyMock.expect(mMockBuildInfo.getDeviceSerial()).andStubReturn("SERIAL");

        EasyMock.expect(mMockBuildInfo.getFiles()).andStubReturn(Collections.emptyList());

        mContext = new InvocationContext();
        mContext.addAllocatedDevice("default", mMockDevice);
        mContext.addDeviceBuildInfo("default", mMockBuildInfo);
        mTest.setInvocationContext(mContext);
    }

    @After
    public void tearDown() {
        FileUtil.recursiveDelete(mRootDir);
    }

    /**
     * Create a CTS configuration with a fake tests to exercise all cases.
     *
     * @param testsDir The testcases/ dir where to put the module
     * @param name the name of the module.
     * @param moduleClass the fake test class to use.
     * @param reportTest True if the test report some tests
     * @param runComplete True if the test run is complete
     * @param doesOneTestFail True if one of the test is going to fail
     * @param internalRetry True if the test will retry the module itself once
     */
    private void createConfig(File testsDir, String name, String moduleClass, boolean reportTest,
            boolean runComplete, boolean doesOneTestFail, boolean internalRetry)
                    throws IOException {
        createConfig(testsDir, name, moduleClass, reportTest, runComplete, doesOneTestFail,
                internalRetry, "foo");

    }

    /**
     * Create a CTS configuration with a fake tests to exercise all cases.
     *
     * @param testsDir The testcases/ dir where to put the module
     * @param name the name of the module.
     * @param moduleClass the fake test class to use.
     * @param reportTest True if the test report some tests
     * @param runComplete True if the test run is complete
     * @param doesOneTestFail True if one of the test is going to fail
     * @param internalRetry True if the test will retry the module itself once
     * @param component the platform component name that the module can be categorized under
     */
    private void createConfig(File testsDir, String name, String moduleClass, boolean reportTest,
            boolean runComplete, boolean doesOneTestFail, boolean internalRetry, String component)
                    throws IOException {
        File config = new File(testsDir, String.format(FILENAME, name));
        FileUtil.deleteFile(config);
        if (!config.createNewFile()) {
            throw new IOException(String.format("Failed to create '%s'", config.getAbsolutePath()));
        }

        FileUtil.writeToFile(String.format(CONFIG, component, moduleClass, reportTest, runComplete,
                doesOneTestFail, internalRetry), config);
    }

    /**
     * Simple tests running in one module that should be marked complete.
     */
    @Test
    public void testSingleModuleRun() throws Exception {
        final String moduleName = "module_run";
        final String mAbi = "arm64-v8a";
        createConfig(mTestDir, moduleName, TEST_STUB, true, true, true, false);
        EasyMock.expect(mMockDevice.getProperty("ro.product.cpu.abilist")).andReturn(mAbi);

        mMockBuildInfo.addBuildAttribute(EasyMock.eq(CompatibilityBuildHelper.MODULE_IDS),
                EasyMock.eq(AbiUtils.createId(mAbi, moduleName)));
        EasyMock.expectLastCall();

        EasyMock.replay(mMockDevice, mMockBuildInfo);
        mReporter.invocationStarted(mContext);
        mTest.run(mReporter);
        mReporter.invocationEnded(500);
        EasyMock.verify(mMockDevice, mMockBuildInfo);
        IInvocationResult result = mReporter.getResult();
        assertEquals(2, result.countResults(TestStatus.PASS));
        assertEquals(1, result.countResults(TestStatus.FAIL));
        assertEquals(1, result.getModules().size());
        assertEquals(1, result.getModuleCompleteCount());
    }

    /**
     * Verify that result reporters test run ended callback can receive component name as configured
     * in module config metadata field.
     */
    @Test
    public void testSingleModuleRun_checkMetadata() throws Exception {
        final String moduleName = "AwsomeModule";
        final String mAbi = "arm64-v8a";
        final String component = "CriticalComponent";
        final List<String> receivedComponentsTestEnded = new ArrayList<>();
        final List<String> receivedModuleNameTestEnded = new ArrayList<>();
        final List<String> receivedAbiTestEnded = new ArrayList<>();
        final List<String> receivedComponentsTestRunEnded = new ArrayList<>();
        final List<String> receivedModuleNameTestRunEnded = new ArrayList<>();
        final List<String> receivedAbiTestRunEnded = new ArrayList<>();
        createConfig(mTestDir, moduleName, SIMPLE_TEST_STUB, true, true, true, false, component);
        EasyMock.expect(mMockDevice.getProperty("ro.product.cpu.abilist")).andReturn(mAbi);

        mMockBuildInfo.addBuildAttribute(EasyMock.eq(CompatibilityBuildHelper.MODULE_IDS),
                EasyMock.eq(AbiUtils.createId(mAbi, moduleName)));
        EasyMock.expectLastCall();

        EasyMock.replay(mMockDevice, mMockBuildInfo);
        ITestInvocationListener myListener = new ITestInvocationListener() {
            private IInvocationContext myContext;
            @Override
            public void invocationStarted(IInvocationContext context) {
                myContext = context;
            }
            @Override
            public void testRunEnded(long elapsedTimeMillis, Map<String, String> runMetrics) {
                receivedComponentsTestRunEnded.addAll(myContext.getModuleInvocationContext()
                        .getConfigurationDescriptor().getMetaData("component"));
                receivedModuleNameTestRunEnded.addAll(myContext.getModuleInvocationContext()
                        .getAttributes().get(IModuleDef.MODULE_NAME));
                receivedAbiTestRunEnded.addAll(myContext.getModuleInvocationContext()
                        .getAttributes().get(IModuleDef.MODULE_ABI));
            }
            @Override
            public void testEnded(TestIdentifier test, long endTime,
                    Map<String, String> testMetrics) {
                receivedComponentsTestEnded.addAll(myContext.getModuleInvocationContext()
                        .getConfigurationDescriptor().getMetaData("component"));
                receivedModuleNameTestEnded.addAll(myContext.getModuleInvocationContext()
                        .getAttributes().get(IModuleDef.MODULE_NAME));
                receivedAbiTestEnded.addAll(myContext.getModuleInvocationContext()
                        .getAttributes().get(IModuleDef.MODULE_ABI));
            }
        };
        myListener.invocationStarted(mContext);
        mTest.run(myListener);
        myListener.invocationEnded(500);
        EasyMock.verify(mMockDevice, mMockBuildInfo);
        // verify metadata was retrieved during testRunEnded callbacks
        assertEquals("[testRunEnded] wrong number of metadata collected",
                1, receivedComponentsTestRunEnded.size());
        assertEquals("[testRunEnded] wrong component metadata field received",
                component, receivedComponentsTestRunEnded.get(0));
        assertEquals("[testRunEnded] wrong number of module name collected",
                1, receivedModuleNameTestRunEnded.size());
        assertEquals(moduleName, receivedModuleNameTestRunEnded.get(0));
        assertEquals("[testEnded] wrong number of module abi collected",
                1, receivedAbiTestRunEnded.size());
        assertEquals(mAbi, receivedAbiTestRunEnded.get(0));
        // verify metadata was retrieved during testEnded callbacks
        assertEquals("[testEnded] wrong number of metadata collected",
                1, receivedComponentsTestEnded.size());
        assertEquals("[testEnded] wrong component metadata field received",
                component, receivedComponentsTestEnded.get(0));
        assertEquals("[testEnded] wrong number of module name collected",
                1, receivedModuleNameTestEnded.size());
        assertEquals(moduleName, receivedModuleNameTestEnded.get(0));
        assertEquals("[testEnded] wrong number of module abi collected",
                1, receivedAbiTestEnded.size());
        assertEquals(mAbi, receivedAbiTestEnded.get(0));
    }

    /**
     * Simple tests running in one module that run some tests but not all of them.
     */
    @Test
    public void testSingleModuleRun_incomplete() throws Exception {
        final String moduleName = "module_run_incomplete";
        final String mAbi = "arm64-v8a";
        createConfig(mTestDir, moduleName, TEST_STUB, true, false, true, false);
        EasyMock.expect(mMockDevice.getProperty("ro.product.cpu.abilist")).andReturn(mAbi);

        mMockBuildInfo.addBuildAttribute(EasyMock.eq(CompatibilityBuildHelper.MODULE_IDS),
                EasyMock.eq(AbiUtils.createId(mAbi, moduleName)));
        EasyMock.expectLastCall();

        EasyMock.replay(mMockDevice, mMockBuildInfo);
        mReporter.invocationStarted(mContext);
        mTest.run(mReporter);
        mReporter.invocationEnded(500);
        EasyMock.verify(mMockDevice, mMockBuildInfo);
        IInvocationResult result = mReporter.getResult();
        assertEquals(1, result.countResults(TestStatus.PASS));
        assertEquals(1, result.countResults(TestStatus.FAIL));
        // Module should not be seen as complete.
        assertEquals(1, result.getModules().size());
        assertEquals(0, result.getModuleCompleteCount());
    }

    /**
     * Simple tests running in one module that should be marked complete since it runs all its
     * tests after an internal retry (like InstrumentationTest).
     * FIXME: Fix the expectation of this test
     */
    @Test
    public void testSingleModuleRun_completeAfterInternalRetry() throws Exception {
        final String moduleName = "module_completeAfterRetry";
        final String mAbi = "arm64-v8a";
        createConfig(mTestDir, moduleName, TEST_STUB, true, true, true, true);
        EasyMock.expect(mMockDevice.getProperty("ro.product.cpu.abilist")).andReturn(mAbi);

        mMockBuildInfo.addBuildAttribute(EasyMock.eq(CompatibilityBuildHelper.MODULE_IDS),
                EasyMock.eq(AbiUtils.createId(mAbi, moduleName)));
        EasyMock.expectLastCall();

        EasyMock.replay(mMockDevice, mMockBuildInfo);
        mReporter.invocationStarted(mContext);
        mTest.run(mReporter);
        mReporter.invocationEnded(500);
        EasyMock.verify(mMockDevice, mMockBuildInfo);
        IInvocationResult result = mReporter.getResult();
        // FIXME: All tests should be marked as executed and not aggregating the count.
        assertEquals(2, result.countResults(TestStatus.PASS));
        assertEquals(1, result.countResults(TestStatus.FAIL));
        // FIXME: Module should be complete since all its test have run.
        assertEquals(1, result.getModules().size());
        assertEquals(0, result.getModuleCompleteCount());
    }

    /**
     * Simple tests running in one module that run some tests but not all of them, then we
     * attempt to run it again and they still didn't run.
     * FIXME: This test expectation needs to be fixed
     */
    @Test
    public void testSingleModuleRun_incomplete_rerun_incomplete() throws Exception {
        final String moduleName = "module_incomplete_rerun";
        final String mAbi = "arm64-v8a";
        createConfig(mTestDir, moduleName, TEST_STUB, true, false, true, false);
        EasyMock.expect(mMockDevice.getProperty("ro.product.cpu.abilist")).andReturn(mAbi);

        mMockBuildInfo.addBuildAttribute(EasyMock.eq(CompatibilityBuildHelper.MODULE_IDS),
                EasyMock.eq(AbiUtils.createId(mAbi, moduleName)));
        EasyMock.expectLastCall();

        // extra calls for retry
        EasyMock.expect(mMockDevice.getProperty("ro.build.fingerprint")).andReturn("fingerprint");
        EasyMock.expect(mMockDevice.getProperty("ro.product.cpu.abilist")).andReturn(mAbi);
        mMockBuildInfo.addBuildAttribute(EasyMock.eq(CompatibilityBuildHelper.MODULE_IDS),
                EasyMock.eq(AbiUtils.createId(mAbi, moduleName)));
        EasyMock.expectLastCall();
        mMockBuildInfo.addBuildAttribute(EasyMock.eq("retry_command_line_args"),
                EasyMock.eq(COMMAND_LINE));
        EasyMock.expectLastCall();

        EasyMock.replay(mMockDevice, mMockBuildInfo);
        mReporter.invocationStarted(mContext);
        mTest.run(mReporter);
        mReporter.invocationEnded(500);

        IInvocationResult result = mReporter.getResult();
        assertEquals(1, result.countResults(TestStatus.PASS));
        assertEquals(1, result.countResults(TestStatus.FAIL));
        // Module should not be seen as complete.
        assertEquals(0, result.getModuleCompleteCount());

        // We re-run it
        mReporter = new ResultReporter();
        mTest = new CompatibilityTest() {
            @Override
            protected Set<String> getAbisForBuildTargetArch() {
                Set<String> abis = new HashSet<>();
                abis.add("arm64-v8a");
                return abis;
            }
        };
        mTest.setDevice(mMockDevice);
        mTest.setBuild(mMockBuildInfo);
        mTest.setInvocationContext(mContext);
        OptionSetter setter = new OptionSetter(mTest, mReporter);
        setter.setOptionValue("retry", "0");

        mReporter.invocationStarted(mContext);
        mTest.run(mReporter);
        mReporter.invocationEnded(500);
        EasyMock.verify(mMockDevice, mMockBuildInfo);

        // Check retry results
        result = mReporter.getResult();
        // FIXME: We should only have 1 not_executed in the retry too. They should not aggregate
        // from one run to another.
        assertEquals(1, result.countResults(TestStatus.PASS));
        assertEquals(1, result.countResults(TestStatus.FAIL));
        // Module should not be seen as complete.
        assertEquals(1, result.getModules().size());
        assertEquals(0, result.getModuleCompleteCount());
    }

    /**
     * Simple tests running in one module that run some tests but not all of them, we then attempt
     * to retry run them and this time the not_executed is executed.
     */
    @Test
    public void testSingleModuleRun_incomplete_rerun_complete() throws Exception {
        final String moduleName = "module_incom_rerun_complete";
        final String mAbi = "arm64-v8a";
        createConfig(mTestDir, moduleName, TEST_STUB, true, false, true, false);
        EasyMock.expect(mMockDevice.getProperty("ro.product.cpu.abilist")).andReturn(mAbi);

        mMockBuildInfo.addBuildAttribute(EasyMock.eq(CompatibilityBuildHelper.MODULE_IDS),
                EasyMock.eq(AbiUtils.createId(mAbi, moduleName)));
        EasyMock.expectLastCall();

        // extra calls for retry
        EasyMock.expect(mMockDevice.getProperty("ro.build.fingerprint")).andReturn("fingerprint");
        EasyMock.expect(mMockDevice.getProperty("ro.product.cpu.abilist")).andReturn(mAbi);
        mMockBuildInfo.addBuildAttribute(EasyMock.eq(CompatibilityBuildHelper.MODULE_IDS),
                EasyMock.eq(AbiUtils.createId(mAbi, moduleName)));
        EasyMock.expectLastCall();
        mMockBuildInfo.addBuildAttribute(EasyMock.eq("retry_command_line_args"),
                EasyMock.eq(COMMAND_LINE));
        EasyMock.expectLastCall();

        EasyMock.replay(mMockDevice, mMockBuildInfo);
        mReporter.invocationStarted(mContext);
        mTest.run(mReporter);
        mReporter.invocationEnded(500);

        IInvocationResult result = mReporter.getResult();
        assertEquals(1, result.countResults(TestStatus.PASS));
        assertEquals(1, result.countResults(TestStatus.FAIL));
        // Module should not be seen as complete.
        assertEquals(0, result.getModuleCompleteCount());

        // We replace the config by one that runs all tests without failures.
        createConfig(mTestDir, moduleName, TEST_STUB, true, true, false, false);
        // Usually configs do not change during the same session so we clear the map to have
        // the new version of the config.
        ((ConfigurationFactory)ConfigurationFactory.getInstance()).clearMapConfig();

        // We re-run it
        mReporter = new ResultReporter();
        mTest = new CompatibilityTest() {
            @Override
            protected Set<String> getAbisForBuildTargetArch() {
                Set<String> abis = new HashSet<>();
                abis.add("arm64-v8a");
                return abis;
            }
        };
        mTest.setDevice(mMockDevice);
        mTest.setBuild(mMockBuildInfo);
        mTest.setInvocationContext(mContext);
        OptionSetter setter = new OptionSetter(mTest, mReporter);
        setter.setOptionValue("retry", "0");

        mReporter.invocationStarted(mContext);
        mTest.run(mReporter);
        mReporter.invocationEnded(500);
        EasyMock.verify(mMockDevice, mMockBuildInfo);

        // Check retry results
        result = mReporter.getResult();
        assertEquals(3, result.countResults(TestStatus.PASS));
        assertEquals(0, result.countResults(TestStatus.FAIL));
        // Module should be marked as complete after retry.
        assertEquals(1, result.getModules().size());
        assertEquals(1, result.getModuleCompleteCount());
    }

    // ***** Case for sharding interaction *****

    /**
     * Helper to create a shard listener with the original reporter as master.
     */
    private ITestInvocationListener getShardListener(ResultReporter masterReporter) {
        List<ITestInvocationListener> shardListeners = new ArrayList<ITestInvocationListener>();
        ShardListener origConfigListener = new ShardListener(masterReporter);
        ResultReporter reporterClone = (ResultReporter) masterReporter.clone();
        shardListeners.add(reporterClone);
        shardListeners.add(origConfigListener);
        ResultForwarder shard = new ResultForwarder(shardListeners);
        return shard;
    }

    /**
     * Helper Thread to run the IShardableTest.
     */
    private class ShardThread extends Thread {
        private IRemoteTest mShardTest;
        private ResultReporter mMasterReporter;
        private IBuildInfo mBuild;
        private ITestDevice mDevice;
        private IInvocationContext mShardContext;

        public ShardThread(IRemoteTest test, ResultReporter masterReporter, IBuildInfo build,
                ITestDevice device, IInvocationContext context) {
            mShardTest = test;
            mMasterReporter = masterReporter;
            mBuild = build;
            mDevice = device;
            mShardContext = context;
        }

        @Override
        public void run() {
            ITestInvocationListener listener = getShardListener(mMasterReporter);
            ((IBuildReceiver)mShardTest).setBuild(mBuild);
            ((IDeviceTest)mShardTest).setDevice(mDevice);
            ((IInvocationContextReceiver)mShardTest).setInvocationContext(mContext);
            listener.invocationStarted(mShardContext);
            try {
                mShardTest.run(listener);
            } catch (DeviceNotAvailableException e) {
                throw new RuntimeException(e);
            } finally {
                listener.invocationEnded(500);
            }
        }
    }

    /**
     * Simple tests running in one module that should be marked complete when each shard run a test
     * from the module. Each Module is going to run 1 pass 1 fail. 2 modules and 2 shards.
     * Using the {@link CompatibilityTest#split()}.
     */
    @Test
    public void testSingleModuleRun_sharded() throws Exception {
        final String moduleName = "module_sharded";
        Set<String> abis = AbiUtils.getAbisForArch(TestSuiteInfo.getInstance().getTargetArch());
        Iterator<String> ite = abis.iterator();
        final String abi1 = ite.next();
        final String abi2 = ite.next();
        createConfig(mTestDir, moduleName, TEST_STUB_SHARDABLE, true, true, true, false);
        EasyMock.expect(mMockDevice.getProperty("ro.product.cpu.abilist")).andReturn(
                String.format("%s,%s", abi1, abi2));
        mMockBuildInfo.addBuildAttribute(EasyMock.eq(CompatibilityBuildHelper.MODULE_IDS),
                EasyMock.anyObject());
        EasyMock.expectLastCall();

        EasyMock.replay(mMockDevice, mMockBuildInfo);

        OptionSetter setter = new OptionSetter(mTest);
        setter.setOptionValue("shards", "2");
        List<IRemoteTest> tests = (List<IRemoteTest>) mTest.split();
        // We expect 2 shards
        assertEquals(2, tests.size());

        List<ShardThread> threads = new ArrayList<>();
        // Run all shards
        for (IRemoteTest test : tests) {
            ShardThread st = new ShardThread(test, mReporter, mMockBuildInfo, mMockDevice,
                    mContext);
            threads.add(st);
            st.start();
        }
        for (ShardThread thread : threads) {
            thread.join(5000);
        }
        // Allow some time for ResultReport to finalize the results coming from the threads.
        boolean finalized = mReporter.waitForFinalized(2, TimeUnit.MINUTES);
        assertTrue(finalized);
        EasyMock.verify(mMockDevice, mMockBuildInfo);
        // Check aggregated results to make sure it's consistent.
        IInvocationResult result = mReporter.getResult();
        assertEquals(4, result.countResults(TestStatus.PASS));
        assertEquals(4, result.countResults(TestStatus.FAIL));
        assertEquals(2, result.getModules().size());
        assertEquals(2, result.getModuleCompleteCount());
    }

    /**
     * Simple tests running in one module that should be marked incomplete when shards do not
     * complete. Each shard is going to run 1 pass 1 fail 1 not_executed.
     * Using the {@link CompatibilityTest#split()}.
     */
    @Test
    public void testSingleModuleRun_sharded_incomplete() throws Exception {
        final String moduleName = "module_sharded_incomplete";
        Set<String> abis = AbiUtils.getAbisForArch(TestSuiteInfo.getInstance().getTargetArch());
        Iterator<String> ite = abis.iterator();
        final String abi1 = ite.next();
        final String abi2 = ite.next();
        createConfig(mTestDir, moduleName, TEST_STUB_SHARDABLE, true, false, true, false);
        EasyMock.expect(mMockDevice.getProperty("ro.product.cpu.abilist")).andReturn(
                String.format("%s,%s", abi1, abi2));
        mMockBuildInfo.addBuildAttribute(EasyMock.eq(CompatibilityBuildHelper.MODULE_IDS),
                EasyMock.anyObject());
        EasyMock.expectLastCall();

        EasyMock.replay(mMockDevice, mMockBuildInfo);
        OptionSetter setter = new OptionSetter(mTest);
        setter.setOptionValue("shards", "2");
        List<IRemoteTest> tests = (List<IRemoteTest>) mTest.split();
        // We expect 2 shards
        assertEquals(2, tests.size());

        List<ShardThread> threads = new ArrayList<>();
        // Run all shards
        for (IRemoteTest test : tests) {
            ShardThread st = new ShardThread(test, mReporter, mMockBuildInfo, mMockDevice,
                    mContext);
            threads.add(st);
            st.start();
        }
        for (ShardThread thread : threads) {
            thread.join(5000);
        }
        // Allow some time for ResultReport to finalize the results coming from the threads.
        boolean finalized = mReporter.waitForFinalized(2, TimeUnit.MINUTES);
        assertTrue(finalized);
        EasyMock.verify(mMockDevice, mMockBuildInfo);
        // Check aggregated results to make sure it's consistent.
        IInvocationResult result = mReporter.getResult();
        assertEquals(4, result.countResults(TestStatus.PASS));
        assertEquals(4, result.countResults(TestStatus.FAIL));
        assertEquals(2, result.getModules().size());
        assertEquals(0, result.getModuleCompleteCount());
    }

    /**
     * Simple tests running in one module that should be marked complete when each shard run a test
     * from the module.
     * We are going to run only one of the shard since IStrictShardable allows it.
     * Using the {@link CompatibilityTest#getTestShard(int, int)}.
     * FIXME: Fix expectation of this test.
     */
    @Test
    public void testSingleModuleRun_sharded_getTestShard() throws Exception {
        final String moduleName = "module_sharded_getTestShard";
        Set<String> abis = AbiUtils.getAbisForArch(TestSuiteInfo.getInstance().getTargetArch());
        Iterator<String> ite = abis.iterator();
        final String abi1 = ite.next();
        final String abi2 = ite.next();
        createConfig(mTestDir, moduleName, TEST_STUB_SHARDABLE, true, true, true, false);
        EasyMock.expect(mMockDevice.getProperty("ro.product.cpu.abilist")).andReturn(
                String.format("%s,%s", abi1, abi2));

        String expectedAdd = AbiUtils.createId(abi1, moduleName) + ","
                + AbiUtils.createId(abi2, moduleName);
        mMockBuildInfo.addBuildAttribute(EasyMock.eq(CompatibilityBuildHelper.MODULE_IDS),
                EasyMock.anyObject());
        EasyMock.expectLastCall();
        mAttributes.put(CompatibilityBuildHelper.MODULE_IDS, expectedAdd);

        EasyMock.replay(mMockDevice, mMockBuildInfo);

        List<IRemoteTest> tests = new ArrayList<>();
        tests.add(mTest.getTestShard(3, 0));
        // We are only running one of the shards since they should be independent.
        assertEquals(1, tests.size());

        ((IBuildReceiver)tests.get(0)).setBuild(mMockBuildInfo);
        ((IDeviceTest)tests.get(0)).setDevice(mMockDevice);
        ((IInvocationContextReceiver)tests.get(0)).setInvocationContext(mContext);
        mReporter.invocationStarted(mContext);
        try {
            tests.get(0).run(mReporter);
        } catch (DeviceNotAvailableException e) {
            throw new RuntimeException(e);
        } finally {
            mReporter.invocationEnded(500);
        }
        EasyMock.verify(mMockDevice, mMockBuildInfo);

        IInvocationResult result = mReporter.getResult();
        assertEquals(2, result.countResults(TestStatus.PASS));
        assertEquals(2, result.countResults(TestStatus.FAIL));
        // FIXME: Only one module should be expected since within the one shard requested to run
        // only one module existed.
        assertEquals(2, result.getModules().size());
        // FIXME: The module for the shard should be completed since all tests run.
        // TestRunHandler in this case create an expectation of 3 testRunStarted just because of
        // the number of shards.
        assertEquals(0, result.getModuleCompleteCount());
    }
}
