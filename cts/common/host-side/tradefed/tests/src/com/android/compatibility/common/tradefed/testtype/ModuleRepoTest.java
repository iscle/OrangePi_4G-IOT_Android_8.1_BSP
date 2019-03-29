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
import com.android.compatibility.common.tradefed.testtype.ModuleRepo.ConfigFilter;
import com.android.tradefed.build.IBuildInfo;
import com.android.tradefed.config.Configuration;
import com.android.tradefed.config.ConfigurationDescriptor;
import com.android.tradefed.config.IConfiguration;
import com.android.tradefed.config.Option;
import com.android.tradefed.device.DeviceNotAvailableException;
import com.android.tradefed.result.ITestInvocationListener;
import com.android.tradefed.targetprep.ITargetPreparer;
import com.android.tradefed.testtype.Abi;
import com.android.tradefed.testtype.IAbi;
import com.android.tradefed.testtype.IAbiReceiver;
import com.android.tradefed.testtype.IRemoteTest;
import com.android.tradefed.testtype.IRuntimeHintProvider;
import com.android.tradefed.testtype.IStrictShardableTest;
import com.android.tradefed.testtype.ITestCollector;
import com.android.tradefed.testtype.ITestFilterReceiver;
import com.android.tradefed.util.AbiUtils;
import com.android.tradefed.util.FileUtil;
import com.android.tradefed.util.MultiMap;

import junit.framework.TestCase;

import org.easymock.EasyMock;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Unit Tests for {@link ModuleRepo}
 */
public class ModuleRepoTest extends TestCase {

    private static final String TOKEN =
            "<target_preparer class=\"com.android.compatibility.common.tradefed.targetprep.TokenRequirement\">\n"
            + "<option name=\"token\" value=\"%s\" />\n"
            + "</target_preparer>\n";
    private static final String CONFIG =
            "<configuration description=\"Auto Generated File\">\n" +
            "%s" +
            "<test class=\"com.android.compatibility.common.tradefed.testtype.%s\">\n" +
            "<option name=\"module\" value=\"%s\" />" +
            "</test>\n" +
            "</configuration>";
    private static final String FOOBAR_TOKEN = "foobar";
    private static final String SERIAL1 = "abc";
    private static final String SERIAL2 = "def";
    private static final String SERIAL3 = "ghi";
    private static final Set<String> SERIALS = new HashSet<>();
    private static final Set<IAbi> ABIS = new LinkedHashSet<>();
    private static final List<String> DEVICE_TOKENS = new ArrayList<>();
    private static final List<String> TEST_ARGS= new ArrayList<>();
    private static final List<String> MODULE_ARGS = new ArrayList<>();
    private static final Set<String> INCLUDES = new HashSet<>();
    private static final Set<String> EXCLUDES = new HashSet<>();
    private static final MultiMap<String, String> METADATA_INCLUDES = new MultiMap<>();
    private static final MultiMap<String, String> METADATA_EXCLUDES = new MultiMap<>();
    private static final Set<String> FILES = new HashSet<>();
    private static final String FILENAME = "%s.config";
    private static final String ROOT_DIR_ATTR = "ROOT_DIR";
    private static final String SUITE_NAME_ATTR = "SUITE_NAME";
    private static final String START_TIME_MS_ATTR = "START_TIME_MS";
    private static final String ABI_32 = "armeabi-v7a";
    private static final String ABI_64 = "arm64-v8a";
    private static final String MODULE_NAME_A = "FooModuleA";
    private static final String MODULE_NAME_B = "FooModuleB";
    private static final String MODULE_NAME_C = "FooModuleC";
    private static final String NON_EXISTS_MODULE_NAME = "NonExistModule";
    private static final String ID_A_32 = AbiUtils.createId(ABI_32, MODULE_NAME_A);
    private static final String ID_A_64 = AbiUtils.createId(ABI_64, MODULE_NAME_A);
    private static final String ID_B_32 = AbiUtils.createId(ABI_32, MODULE_NAME_B);
    private static final String ID_B_64 = AbiUtils.createId(ABI_64, MODULE_NAME_B);
    private static final String ID_C_32 = AbiUtils.createId(ABI_32, MODULE_NAME_C);
    private static final String ID_C_64 = AbiUtils.createId(ABI_64, MODULE_NAME_C);
    private static final String TEST_ARG = TestStub.class.getName() + ":foo:bar";
    private static final String MODULE_ARG = "%s:blah:foobar";
    private static final String TEST_STUB = "TestStub"; // Trivial test stub
    private static final String SHARDABLE_TEST_STUB = "ShardableTestStub"; // Shardable and IBuildReceiver
    private static final String [] EXPECTED_MODULE_IDS = new String[] {
        "arm64-v8a FooModuleB",
        "arm64-v8a FooModuleC",
        "armeabi-v7a FooModuleA",
        "arm64-v8a FooModuleA",
        "armeabi-v7a FooModuleC",
        "armeabi-v7a FooModuleB"
    };

    static {
        SERIALS.add(SERIAL1);
        SERIALS.add(SERIAL2);
        SERIALS.add(SERIAL3);
        ABIS.add(new Abi(ABI_32, "32"));
        ABIS.add(new Abi(ABI_64, "64"));
        DEVICE_TOKENS.add(String.format("%s:%s", SERIAL3, FOOBAR_TOKEN));
        TEST_ARGS.add(TEST_ARG);
        MODULE_ARGS.add(String.format(MODULE_ARG, MODULE_NAME_A));
        MODULE_ARGS.add(String.format(MODULE_ARG, MODULE_NAME_B));
        MODULE_ARGS.add(String.format(MODULE_ARG, MODULE_NAME_C));
        FILES.add(String.format(FILENAME, MODULE_NAME_A));
        FILES.add(String.format(FILENAME, MODULE_NAME_B));
        FILES.add(String.format(FILENAME, MODULE_NAME_C));
    }
    private ModuleRepo mRepo;
    private File mTestsDir;
    private File mRootDir;
    private IBuildInfo mMockBuildInfo;

    @Override
    public void setUp() throws Exception {
        mTestsDir = setUpConfigs();
        mRepo = new ModuleRepo();
        mMockBuildInfo = EasyMock.createMock(IBuildInfo.class);
        // Flesh out the result directory structure so ModuleRepo can write to the test runs file
        mRootDir = FileUtil.createTempDir("root");
        File subRootDir = new File(mRootDir, String.format("android-suite"));
        File resultsDir = new File(subRootDir, "results");
        File resultDir = new File(resultsDir, CompatibilityBuildHelper.getDirSuffix(0));
        resultDir.mkdirs();

        Map<String, String> mockBuildInfoMap = new HashMap<String, String>();
        mockBuildInfoMap.put(ROOT_DIR_ATTR, mRootDir.getAbsolutePath());
        mockBuildInfoMap.put(SUITE_NAME_ATTR, "suite");
        mockBuildInfoMap.put(START_TIME_MS_ATTR, Long.toString(0));
        EasyMock.expect(mMockBuildInfo.getBuildAttributes()).andReturn(mockBuildInfoMap).anyTimes();
        EasyMock.replay(mMockBuildInfo);
    }

    private File setUpConfigs() throws IOException {
        File testsDir = FileUtil.createTempDir("testcases");
        createConfig(testsDir, MODULE_NAME_A, null);
        createConfig(testsDir, MODULE_NAME_B, null);
        createConfig(testsDir, MODULE_NAME_C, FOOBAR_TOKEN);
        return testsDir;
    }

    private void createConfig(File testsDir, String name, String token) throws IOException {
        createConfig(testsDir, name, token, TEST_STUB);
    }

    private void createConfig(File testsDir, String name, String token, String moduleClass)
            throws IOException {
        File config = new File(testsDir, String.format(FILENAME, name));
        if (!config.createNewFile()) {
            throw new IOException(String.format("Failed to create '%s'", config.getAbsolutePath()));
        }
        String preparer = "";
        if (token != null) {
            preparer = String.format(TOKEN, token);
        }
        FileUtil.writeToFile(String.format(CONFIG, preparer, moduleClass, name), config);
    }

    @Override
    public void tearDown() throws Exception {
        FileUtil.recursiveDelete(mTestsDir);
        tearDownConfigs(mTestsDir);
        tearDownConfigs(mRootDir);
    }

    private void tearDownConfigs(File testsDir) {
        FileUtil.recursiveDelete(testsDir);
    }

    public void testInitialization() throws Exception {
        mRepo.initialize(3, null, mTestsDir, ABIS, DEVICE_TOKENS, TEST_ARGS, MODULE_ARGS, INCLUDES,
                EXCLUDES, METADATA_INCLUDES, METADATA_EXCLUDES, mMockBuildInfo);
        assertTrue("Should be initialized", mRepo.isInitialized());
        assertEquals("Wrong number of shards", 3, mRepo.getNumberOfShards());
        Map<String, Set<String>> deviceTokens = mRepo.getDeviceTokens();
        assertEquals("Wrong number of devices with tokens", 1, deviceTokens.size());
        Set<String> tokens = deviceTokens.get(SERIAL3);
        assertEquals("Wrong number of tokens", 1, tokens.size());
        assertTrue("Unexpected device token", tokens.contains(FOOBAR_TOKEN));
        assertEquals("Wrong number of modules", 4, mRepo.getNonTokenModules().size());
        List<IModuleDef> tokenModules = mRepo.getTokenModules();
        assertEquals("Wrong number of modules with tokens", 2, tokenModules.size());
    }

    public void testGetModules() throws Exception {
        mRepo.initialize(1, null, mTestsDir, ABIS, DEVICE_TOKENS, TEST_ARGS, MODULE_ARGS, INCLUDES,
                EXCLUDES, METADATA_INCLUDES, METADATA_EXCLUDES, mMockBuildInfo);
        assertTrue("Should be initialized", mRepo.isInitialized());
        assertEquals("Wrong number of tokens", 2, mRepo.getTokenModules().size());
        assertEquals("Wrong number of tokens", 4, mRepo.getNonTokenModules().size());
    }

    /**
     * Test sharding with 2 shards of the 4 non token modules.
     */
    public void testGetModulesSharded() throws Exception {
        mRepo.initialize(2, null, mTestsDir, ABIS, new ArrayList<String>(), TEST_ARGS, MODULE_ARGS,
                INCLUDES, EXCLUDES, METADATA_INCLUDES, METADATA_EXCLUDES, mMockBuildInfo);
        assertTrue("Should be initialized", mRepo.isInitialized());
        assertEquals("Wrong number of tokens", 2, mRepo.getTokenModules().size());
        assertEquals("Wrong number of tokens", 4, mRepo.getNonTokenModules().size());
        List<IModuleDef> shard1 = mRepo.getModules(SERIAL1, 0);
        assertEquals(2, shard1.size());
        assertEquals("armeabi-v7a FooModuleA", shard1.get(0).getId());
        assertEquals("arm64-v8a FooModuleA", shard1.get(1).getId());
        List<IModuleDef> shard2 = mRepo.getModules(SERIAL2, 1);
        // last shard gets the token modules too
        assertEquals(4, shard2.size());
        assertEquals("armeabi-v7a FooModuleB", shard2.get(0).getId());
        assertEquals("arm64-v8a FooModuleB", shard2.get(1).getId());
    }

    /**
     * Test running with only token modules.
     */
    public void testGetModules_onlyTokenModules() throws Exception {
        Set<String> includes = new HashSet<>();
        includes.add(MODULE_NAME_C);
        mRepo.initialize(1, null, mTestsDir, ABIS, new ArrayList<String>(), TEST_ARGS, MODULE_ARGS,
                includes, EXCLUDES, METADATA_INCLUDES, METADATA_EXCLUDES, mMockBuildInfo);
        assertTrue("Should be initialized", mRepo.isInitialized());
        assertEquals("Wrong number of tokens", 2, mRepo.getTokenModules().size());
        assertEquals("Wrong number of tokens", 0, mRepo.getNonTokenModules().size());
        List<IModuleDef> modules = mRepo.getModules(SERIAL1, 0);
        assertNotNull(modules);
        assertEquals(2, modules.size());
    }

    /**
     * Test running with only token modules, with sharded local run, we specify a token module
     * for each device, tests should go in the right place.
     */
    public void testGetModules_TokenModules_multiDevices() throws Exception {
        createConfig(mTestsDir, "FooModuleD", "foobar2");
        Set<String> includes = new HashSet<>();
        includes.add(MODULE_NAME_C);
        includes.add("FooModuleD");
        List<String> tokens = new ArrayList<>();
        tokens.add(String.format("%s:%s", SERIAL1, FOOBAR_TOKEN));
        tokens.add(String.format("%s:%s", SERIAL2, "foobar2"));
        mRepo.initialize(2, null, mTestsDir, ABIS, tokens, TEST_ARGS, MODULE_ARGS,
                includes, EXCLUDES, METADATA_INCLUDES, METADATA_EXCLUDES, mMockBuildInfo);
        assertTrue("Should be initialized", mRepo.isInitialized());
        assertEquals("Wrong number of tokens", 4, mRepo.getTokenModules().size());
        assertEquals("Wrong number of tokens", 0, mRepo.getNonTokenModules().size());
        List<IModuleDef> modules1 = mRepo.getModules(SERIAL1, 0);
        assertNotNull(modules1);
        assertEquals(2, modules1.size());
        // Only module C tokens with serial 1.
        assertTrue(modules1.get(0).getId().contains(MODULE_NAME_C));
        assertTrue(modules1.get(1).getId().contains(MODULE_NAME_C));
        List<IModuleDef> modules2 = mRepo.getModules(SERIAL2, 1);
        assertNotNull(modules2);
        assertEquals(2, modules2.size());
        assertTrue(modules2.get(0).getId().contains("FooModuleD"));
        assertTrue(modules2.get(1).getId().contains("FooModuleD"));
    }

    /**
     * Test sharding with 4 shards of the 6 non token modules + 2 token modules.
     */
    public void testGetModulesSharded_uneven() throws Exception {
        createConfig(mTestsDir, "FooModuleD", null);
        mRepo.initialize(4, null, mTestsDir, ABIS, new ArrayList<String>(), TEST_ARGS, MODULE_ARGS,
                INCLUDES, EXCLUDES, METADATA_INCLUDES, METADATA_EXCLUDES, mMockBuildInfo);
        assertTrue("Should be initialized", mRepo.isInitialized());
        assertEquals("Wrong number of tokens", 2, mRepo.getTokenModules().size());
        assertEquals("Wrong number of tokens", 6, mRepo.getNonTokenModules().size());

        List<IModuleDef> shard1 = mRepo.getModules(SERIAL1, 0);
        assertEquals(1, shard1.size());
        assertEquals("armeabi-v7a FooModuleA", shard1.get(0).getId());

        List<IModuleDef> shard2 = mRepo.getModules(SERIAL2, 1);
        assertEquals(1, shard2.size());
        assertEquals("arm64-v8a FooModuleA", shard2.get(0).getId());

        List<IModuleDef> shard3 = mRepo.getModules(SERIAL3, 2);
        assertEquals(2, shard3.size());
        assertEquals("armeabi-v7a FooModuleB", shard3.get(0).getId());
        assertEquals("arm64-v8a FooModuleB", shard3.get(1).getId());

        List<IModuleDef> shard4 = mRepo.getModules(SERIAL2, 3);
        assertEquals(4, shard4.size());
        assertEquals("armeabi-v7a FooModuleC", shard4.get(0).getId());
        assertEquals("arm64-v8a FooModuleC", shard4.get(1).getId());
        assertEquals("armeabi-v7a FooModuleD", shard4.get(2).getId());
        assertEquals("arm64-v8a FooModuleD", shard4.get(3).getId());
    }

    public void testConfigFilter() throws Exception {
        File[] configFiles = mTestsDir.listFiles(new ConfigFilter());
        assertEquals("Wrong number of config files found.", 3, configFiles.length);
        for (File file : configFiles) {
            assertTrue(String.format("Unrecognised file: %s", file.getAbsolutePath()),
                    FILES.contains(file.getName()));
        }
    }

    public void testFiltering() throws Exception {
        Set<String> includeFilters = new HashSet<>();
        includeFilters.add(MODULE_NAME_A);
        Set<String> excludeFilters = new HashSet<>();
        excludeFilters.add(ID_A_32);
        excludeFilters.add(MODULE_NAME_B);
        mRepo.initialize(1, null, mTestsDir, ABIS, DEVICE_TOKENS, TEST_ARGS, MODULE_ARGS,
                includeFilters, excludeFilters, METADATA_INCLUDES, METADATA_EXCLUDES,
                mMockBuildInfo);
        List<IModuleDef> modules = mRepo.getModules(SERIAL1, 0);
        assertEquals("Incorrect number of modules", 1, modules.size());
        IModuleDef module = modules.get(0);
        assertEquals("Incorrect ID", ID_A_64, module.getId());
        checkArgs(module);
    }

    /**
     * Test that excluded module shouldn't be loaded.
     */
    public void testInitialization_ExcludeModule_SkipLoadingConfig() {
        try {
            Set<String> excludeFilters = new HashSet<String>();
            excludeFilters.add(NON_EXISTS_MODULE_NAME);
            mRepo.initialize(1, null, mTestsDir, ABIS, DEVICE_TOKENS, TEST_ARGS,
                    MODULE_ARGS, Collections.emptySet(), excludeFilters,
                    METADATA_INCLUDES, METADATA_EXCLUDES,
                    mMockBuildInfo);
        } catch (Exception e) {
            fail("Initialization should not fail if non-existing module is excluded");
        }
    }

    /**
     * Test that {@link ModuleRepo#getModules(String, int)} handles well all module being filtered.
     */
    public void testFiltering_empty() throws Exception {
        Set<String> includeFilters = new HashSet<>();
        Set<String> excludeFilters = new HashSet<>();
        excludeFilters.add(MODULE_NAME_A);
        excludeFilters.add(MODULE_NAME_B);
        excludeFilters.add(MODULE_NAME_C);
        mRepo.initialize(1, null, mTestsDir, ABIS, DEVICE_TOKENS, TEST_ARGS, MODULE_ARGS,
                includeFilters, excludeFilters,
                METADATA_INCLUDES, METADATA_EXCLUDES, mMockBuildInfo);
        List<IModuleDef> modules = mRepo.getModules(SERIAL1, 0);
        assertEquals("Incorrect number of modules", 0, modules.size());
    }

    public void testParsing() throws Exception {
        mRepo.initialize(1, null, mTestsDir, ABIS, DEVICE_TOKENS, TEST_ARGS, MODULE_ARGS, INCLUDES,
                EXCLUDES, METADATA_INCLUDES, METADATA_EXCLUDES, mMockBuildInfo);
        List<IModuleDef> modules = mRepo.getModules(SERIAL3, 0);
        Set<String> idSet = new HashSet<>();
        for (IModuleDef module : modules) {
            idSet.add(module.getId());
        }
        assertEquals("Incorrect number of IDs", 6, idSet.size());
        assertTrue("Missing ID_A_32", idSet.contains(ID_A_32));
        assertTrue("Missing ID_A_64", idSet.contains(ID_A_64));
        assertTrue("Missing ID_B_32", idSet.contains(ID_B_32));
        assertTrue("Missing ID_B_64", idSet.contains(ID_B_64));
        assertTrue("Missing ID_C_32", idSet.contains(ID_C_32));
        assertTrue("Missing ID_C_64", idSet.contains(ID_C_64));
        for (IModuleDef module : modules) {
            checkArgs(module);
        }
    }

    private void checkArgs(IModuleDef module) {
        IRemoteTest test = module.getTest();
        assertTrue("Incorrect test type", test instanceof TestStub);
        TestStub stub = (TestStub) test;
        assertEquals("Incorrect test arg", "bar", stub.mFoo);
        assertEquals("Incorrect module arg", "foobar", stub.mBlah);
    }

    public void testSplit() throws Exception {
        createConfig(mTestsDir, "sharded_1", null, SHARDABLE_TEST_STUB);
        createConfig(mTestsDir, "sharded_2", null, SHARDABLE_TEST_STUB);
        createConfig(mTestsDir, "sharded_3", null, SHARDABLE_TEST_STUB);
        Set<IAbi> abis = new HashSet<>();
        abis.add(new Abi(ABI_64, "64"));
        ArrayList<String> emptyList = new ArrayList<>();

        mRepo.initialize(3, 0, mTestsDir, abis, DEVICE_TOKENS, emptyList, emptyList, INCLUDES,
                         EXCLUDES, METADATA_INCLUDES, METADATA_EXCLUDES, mMockBuildInfo);

        List<IModuleDef> modules = new ArrayList<>();
        modules.addAll(mRepo.getNonTokenModules());
        modules.addAll(mRepo.getTokenModules());

        int shardableCount = 0;
        for (IModuleDef def : modules) {
            IRemoteTest test = def.getTest();
            if (test instanceof IStrictShardableTest) {
                shardableCount++;
            }
        }
        assertEquals("Shards wrong", 9, shardableCount);
    }

    public void testGetModuleIds() {
        mRepo.initialize(3, null, mTestsDir, ABIS, DEVICE_TOKENS, TEST_ARGS, MODULE_ARGS, INCLUDES,
                EXCLUDES, METADATA_INCLUDES, METADATA_EXCLUDES, mMockBuildInfo);
        assertTrue("Should be initialized", mRepo.isInitialized());

        assertArrayEquals(EXPECTED_MODULE_IDS, mRepo.getModuleIds());
    }

    private void assertArrayEquals(Object[] expected, Object[] actual) {
        assertEquals(Arrays.asList(expected), Arrays.asList(actual));
    }

    /**
     * Test class to provide runtimeHint.
     */
    private class TestRuntime implements IRemoteTest, IRuntimeHintProvider, IAbiReceiver,
            ITestCollector, ITestFilterReceiver {
        public long runtimeHint = 0l;
        @Override
        public long getRuntimeHint() {
            return runtimeHint;
        }
        // ignore all the other calls
        @Override
        public void run(ITestInvocationListener arg0) throws DeviceNotAvailableException {}
        @Override
        public void addAllExcludeFilters(Set<String> arg0) {}
        @Override
        public void addAllIncludeFilters(Set<String> arg0) {}
        @Override
        public void addExcludeFilter(String arg0) {}
        @Override
        public void addIncludeFilter(String arg0) {}
        @Override
        public void setCollectTestsOnly(boolean arg0) {}
        @Override
        public void setAbi(IAbi arg0) {}
        @Override
        public IAbi getAbi() {return null;}
    }

    /**
     * Balance the load of runtime of the modules for the same runtimehint everywhere.
     */
    public void testGetshard_allSameRuntime() throws Exception {
        List<IModuleDef> testList = new ArrayList<>();
        TestRuntime test1 = new TestRuntime();
        test1.runtimeHint = 100l;
        IModuleDef mod1 = new ModuleDef("test1", new Abi("arm", "32"), test1,
                new ArrayList<ITargetPreparer>(), new ConfigurationDescriptor());
        testList.add(mod1);
        TestRuntime test2 = new TestRuntime();
        test2.runtimeHint = 100l;
        IModuleDef mod2 = new ModuleDef("test2", new Abi("arm", "32"), test2,
                new ArrayList<ITargetPreparer>(), new ConfigurationDescriptor());
        testList.add(mod2);
        TestRuntime test3 = new TestRuntime();
        test3.runtimeHint = 100l;
        IModuleDef mod3 = new ModuleDef("test3", new Abi("arm", "32"), test3,
                new ArrayList<ITargetPreparer>(), new ConfigurationDescriptor());
        testList.add(mod3);
        TestRuntime test4 = new TestRuntime();
        test4.runtimeHint = 100l;
        IModuleDef mod4 = new ModuleDef("test4", new Abi("arm", "32"), test4,
                new ArrayList<ITargetPreparer>(), new ConfigurationDescriptor());
        testList.add(mod4);
        // if we don't shard everything is in one shard.
        List<IModuleDef> res = mRepo.getShard(testList, 0, 1);
        assertEquals(4, res.size());
        res = mRepo.getShard(testList, 0, 2);
        assertEquals(2, res.size());
        assertEquals(mod1, res.get(0));
        assertEquals(mod2, res.get(1));
        res = mRepo.getShard(testList, 1, 2);
        assertEquals(2, res.size());
        assertEquals(mod3, res.get(0));
        assertEquals(mod4, res.get(1));
    }

    /**
     * When reaching splitting time, we need to ensure that even after best effort, if we cannot
     * split into the requested number of shardIndex, we simply return null to report an empty
     * shard.
     */
    public void testGetShard_cannotSplitMore() {
        List<IModuleDef> testList = new ArrayList<>();
        TestRuntime test1 = new TestRuntime();
        test1.runtimeHint = 100l;
        IModuleDef mod1 = new ModuleDef("test1", new Abi("arm", "32"), test1,
                new ArrayList<ITargetPreparer>(), new ConfigurationDescriptor());
        testList.add(mod1);
        List<IModuleDef> res = mRepo.getShard(testList, 1, 2);
        assertNull(res);
    }

    /**
     * When there are no metadata based filters specified, config should be included
     * @throws Exception
     */
    public void testMetadataFilter_emptyFilters() throws Exception {
        IConfiguration config = new Configuration("foo", "bar");
        assertTrue("config not included when metadata filters are empty",
                mRepo.filterByConfigMetadata(config, METADATA_INCLUDES, METADATA_EXCLUDES));
    }

    /**
     * When inclusion filter is specified, config matching the filter is included
     * @throws Exception
     */
    public void testMetadataFilter_matchInclude() throws Exception {
        IConfiguration config = new Configuration("foo", "bar");
        ConfigurationDescriptor desc = config.getConfigurationDescription();
        MultiMap<String, String> metadata = new MultiMap<>();
        metadata.put("component", "foo");
        desc.setMetaData(metadata);
        MultiMap<String, String> includeFilter = new MultiMap<>();
        includeFilter.put("component", "foo");
        assertTrue("config not included with matching inclusion filter",
                mRepo.filterByConfigMetadata(config, includeFilter, METADATA_EXCLUDES));
    }

    /**
     * When inclusion filter is specified, config not matching the filter is excluded
     * @throws Exception
     */
    public void testMetadataFilter_noMatchInclude_mismatchValue() throws Exception {
        IConfiguration config = new Configuration("foo", "bar");
        ConfigurationDescriptor desc = config.getConfigurationDescription();
        MultiMap<String, String> metadata = new MultiMap<>();
        metadata.put("component", "foo");
        desc.setMetaData(metadata);
        MultiMap<String, String> includeFilter = new MultiMap<>();
        includeFilter.put("component", "bar");
        assertFalse("config not excluded with mismatching inclusion filter",
                mRepo.filterByConfigMetadata(config, includeFilter, METADATA_EXCLUDES));
    }

    /**
     * When inclusion filter is specified, config not matching the filter is excluded
     * @throws Exception
     */
    public void testMetadataFilter_noMatchInclude_mismatchKey() throws Exception {
        IConfiguration config = new Configuration("foo", "bar");
        ConfigurationDescriptor desc = config.getConfigurationDescription();
        MultiMap<String, String> metadata = new MultiMap<>();
        metadata.put("component", "foo");
        desc.setMetaData(metadata);
        MultiMap<String, String> includeFilter = new MultiMap<>();
        includeFilter.put("group", "bar");
        assertFalse("config not excluded with mismatching inclusion filter",
                mRepo.filterByConfigMetadata(config, includeFilter, METADATA_EXCLUDES));
    }

    /**
     * When exclusion filter is specified, config matching the filter is excluded
     * @throws Exception
     */
    public void testMetadataFilter_matchExclude() throws Exception {
        IConfiguration config = new Configuration("foo", "bar");
        ConfigurationDescriptor desc = config.getConfigurationDescription();
        MultiMap<String, String> metadata = new MultiMap<>();
        metadata.put("component", "foo");
        desc.setMetaData(metadata);
        MultiMap<String, String> excludeFilter = new MultiMap<>();
        excludeFilter.put("component", "foo");
        assertFalse("config not excluded with matching exclusion filter",
                mRepo.filterByConfigMetadata(config, METADATA_INCLUDES, excludeFilter));
    }

    /**
     * When exclusion filter is specified, config not matching the filter is included
     * @throws Exception
     */
    public void testMetadataFilter_noMatchExclude_mismatchKey() throws Exception {
        IConfiguration config = new Configuration("foo", "bar");
        ConfigurationDescriptor desc = config.getConfigurationDescription();
        MultiMap<String, String> metadata = new MultiMap<>();
        metadata.put("component", "foo");
        desc.setMetaData(metadata);
        MultiMap<String, String> excludeFilter = new MultiMap<>();
        excludeFilter.put("component", "bar");
        assertTrue("config not included with mismatching exclusion filter",
                mRepo.filterByConfigMetadata(config, METADATA_INCLUDES, excludeFilter));
    }

    /**
     * When exclusion filter is specified, config not matching the filter is included
     * @throws Exception
     */
    public void testMetadataFilter_noMatchExclude_mismatchValue() throws Exception {
        IConfiguration config = new Configuration("foo", "bar");
        ConfigurationDescriptor desc = config.getConfigurationDescription();
        MultiMap<String, String> metadata = new MultiMap<>();
        metadata.put("component", "foo");
        desc.setMetaData(metadata);
        MultiMap<String, String> excludeFilter = new MultiMap<>();
        excludeFilter.put("group", "bar");
        assertTrue("config not included with mismatching exclusion filter",
                mRepo.filterByConfigMetadata(config, METADATA_INCLUDES, excludeFilter));
    }

    /**
     * When inclusion filter is specified, config with one of the metadata field matching the filter
     * is included
     * @throws Exception
     */
    public void testMetadataFilter_matchInclude_multipleMetadataField() throws Exception {
        IConfiguration config = new Configuration("foo", "bar");
        ConfigurationDescriptor desc = config.getConfigurationDescription();
        MultiMap<String, String> metadata = new MultiMap<>();
        metadata.put("component", "foo");
        metadata.put("component", "bar");
        desc.setMetaData(metadata);
        MultiMap<String, String> includeFilter = new MultiMap<>();
        includeFilter.put("component", "foo");
        assertTrue("config not included with matching inclusion filter",
                mRepo.filterByConfigMetadata(config, includeFilter, METADATA_EXCLUDES));
    }

    /**
     * When exclusion filter is specified, config with one of the metadata field matching the filter
     * is excluded
     * @throws Exception
     */
    public void testMetadataFilter_matchExclude_multipleMetadataField() throws Exception {
        IConfiguration config = new Configuration("foo", "bar");
        ConfigurationDescriptor desc = config.getConfigurationDescription();
        MultiMap<String, String> metadata = new MultiMap<>();
        metadata.put("component", "foo");
        metadata.put("component", "bar");
        desc.setMetaData(metadata);
        MultiMap<String, String> excludeFilter = new MultiMap<>();
        excludeFilter.put("component", "foo");
        assertFalse("config not excluded with matching exclusion filter",
                mRepo.filterByConfigMetadata(config, METADATA_INCLUDES, excludeFilter));
    }

    /**
     * When inclusion filters are specified, config with metadata field matching one of the filter
     * is included
     * @throws Exception
     */
    public void testMetadataFilter_matchInclude_multipleFilters() throws Exception {
        IConfiguration config = new Configuration("foo", "bar");
        ConfigurationDescriptor desc = config.getConfigurationDescription();
        MultiMap<String, String> metadata = new MultiMap<>();
        metadata.put("component", "foo");
        desc.setMetaData(metadata);
        MultiMap<String, String> includeFilter = new MultiMap<>();
        includeFilter.put("component", "foo");
        includeFilter.put("component", "bar");
        assertTrue("config not included with matching inclusion filter",
                mRepo.filterByConfigMetadata(config, includeFilter, METADATA_EXCLUDES));
    }

    /**
     * When exclusion filters are specified, config with metadata field matching one of the filter
     * is excluded
     * @throws Exception
     */
    public void testMetadataFilter_matchExclude_multipleFilters() throws Exception {
        IConfiguration config = new Configuration("foo", "bar");
        ConfigurationDescriptor desc = config.getConfigurationDescription();
        MultiMap<String, String> metadata = new MultiMap<>();
        metadata.put("component", "foo");
        desc.setMetaData(metadata);
        MultiMap<String, String> excludeFilter = new MultiMap<>();
        excludeFilter.put("component", "foo");
        excludeFilter.put("component", "bar");
        assertFalse("config not excluded with matching exclusion filter",
                mRepo.filterByConfigMetadata(config, METADATA_INCLUDES, excludeFilter));
    }

    /**
     * When inclusion filters are specified, config with metadata field matching one of the filter
     * is included
     * @throws Exception
     */
    public void testMetadataFilter_matchInclude_multipleMetadataAndFilters() throws Exception {
        IConfiguration config = new Configuration("foo", "bar");
        ConfigurationDescriptor desc = config.getConfigurationDescription();
        MultiMap<String, String> metadata = new MultiMap<>();
        metadata.put("component", "foo1");
        metadata.put("group", "bar1");
        desc.setMetaData(metadata);
        MultiMap<String, String> includeFilter = new MultiMap<>();
        includeFilter.put("component", "foo1");
        includeFilter.put("group", "bar2");
        assertTrue("config not included with matching inclusion filter",
                mRepo.filterByConfigMetadata(config, includeFilter, METADATA_EXCLUDES));
    }

    /**
     * When exclusion filters are specified, config with metadata field matching one of the filter
     * is excluded
     * @throws Exception
     */
    public void testMetadataFilter_matchExclude_multipleMetadataAndFilters() throws Exception {
        IConfiguration config = new Configuration("foo", "bar");
        ConfigurationDescriptor desc = config.getConfigurationDescription();
        MultiMap<String, String> metadata = new MultiMap<>();
        metadata.put("component", "foo1");
        metadata.put("group", "bar1");
        desc.setMetaData(metadata);
        MultiMap<String, String> excludeFilter = new MultiMap<>();
        excludeFilter.put("component", "foo1");
        excludeFilter.put("group", "bar2");
        assertFalse("config not excluded with matching exclusion filter",
                mRepo.filterByConfigMetadata(config, METADATA_INCLUDES, excludeFilter));
    }

    /**
     * When inclusion and exclusion filters are both specified, config can pass through the filters
     * as expected.
     * @throws Exception
     */
    public void testMetadataFilter_includeAndExclude() throws Exception {
        IConfiguration config = new Configuration("foo", "bar");
        ConfigurationDescriptor desc = config.getConfigurationDescription();
        MultiMap<String, String> metadata = new MultiMap<>();
        metadata.put("component", "foo");
        metadata.put("group", "bar1");
        desc.setMetaData(metadata);
        MultiMap<String, String> includeFilter = new MultiMap<>();
        includeFilter.put("component", "foo");
        MultiMap<String, String> excludeFilter = new MultiMap<>();
        excludeFilter.put("group", "bar2");
        assertTrue("config not included with matching inclusion and mismatching exclusion filters",
                mRepo.filterByConfigMetadata(config, includeFilter, excludeFilter));
    }

    /**
     * When inclusion and exclusion filters are both specified, config be excluded as specified
     * @throws Exception
     */
    public void testMetadataFilter_includeThenExclude() throws Exception {
        IConfiguration config = new Configuration("foo", "bar");
        ConfigurationDescriptor desc = config.getConfigurationDescription();
        MultiMap<String, String> metadata = new MultiMap<>();
        metadata.put("component", "foo");
        metadata.put("group", "bar");
        desc.setMetaData(metadata);
        MultiMap<String, String> includeFilter = new MultiMap<>();
        includeFilter.put("component", "foo");
        MultiMap<String, String> excludeFilter = new MultiMap<>();
        excludeFilter.put("group", "bar");
        assertFalse("config not excluded with matching inclusion and exclusion filters",
                mRepo.filterByConfigMetadata(config, includeFilter, excludeFilter));
    }

    public static class TestInject implements IRemoteTest {
        @Option(name = "simple-string")
        public String test = null;
        @Option(name = "list-string")
        public List<String> testList = new ArrayList<>();
        @Option(name = "map-string")
        public Map<String, String> testMap = new HashMap<>();

        @Override
        public void run(ITestInvocationListener listener) throws DeviceNotAvailableException {
        }
    }

    /**
     * Test that the different format for module-arg and test-arg can properly be passed to the
     * configuration.
     */
    public void testInjectConfig() throws Exception {
        IConfiguration config = new Configuration("foo", "bar");
        TestInject checker = new TestInject();
        config.setTest(checker);
        Map<String, List<String>> optionMap = new HashMap<String, List<String>>();
        List<String> option1 = new ArrayList<>();
        option1.add("value1");
        optionMap.put("simple-string", option1);

        List<String> option2 = new ArrayList<>();
        option2.add("value2");
        option2.add("value3");
        option2.add("set-option:moreoption");
        optionMap.put("list-string", option2);

        List<String> option3 = new ArrayList<>();
        option3.add("set-option:=moreoption");
        optionMap.put("map-string", option3);

        mRepo.injectOptionsToConfig(optionMap, config);

        assertEquals("value1", checker.test);
        assertEquals(option2, checker.testList);
        Map<String, String> resMap = new HashMap<>();
        resMap.put("set-option", "moreoption");
        assertEquals(resMap, checker.testMap);
    }
}
