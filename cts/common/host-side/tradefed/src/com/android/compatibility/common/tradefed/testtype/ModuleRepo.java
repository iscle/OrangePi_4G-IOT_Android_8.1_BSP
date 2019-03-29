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
import com.android.compatibility.common.tradefed.result.TestRunHandler;
import com.android.compatibility.common.tradefed.util.LinearPartition;
import com.android.compatibility.common.tradefed.util.UniqueModuleCountUtil;
import com.android.compatibility.common.util.TestFilter;
import com.android.ddmlib.Log.LogLevel;
import com.android.tradefed.build.IBuildInfo;
import com.android.tradefed.config.ConfigurationException;
import com.android.tradefed.config.ConfigurationFactory;
import com.android.tradefed.config.IConfiguration;
import com.android.tradefed.config.IConfigurationFactory;
import com.android.tradefed.log.LogUtil.CLog;
import com.android.tradefed.testtype.IAbi;
import com.android.tradefed.testtype.IBuildReceiver;
import com.android.tradefed.testtype.IRemoteTest;
import com.android.tradefed.testtype.IStrictShardableTest;
import com.android.tradefed.testtype.ITestFileFilterReceiver;
import com.android.tradefed.testtype.ITestFilterReceiver;
import com.android.tradefed.util.AbiUtils;
import com.android.tradefed.util.FileUtil;
import com.android.tradefed.util.MultiMap;
import com.android.tradefed.util.TimeUtil;

import com.google.common.annotations.VisibleForTesting;

import java.io.File;
import java.io.FilenameFilter;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

/**
 * Retrieves Compatibility test module definitions from the repository.
 */
public class ModuleRepo implements IModuleRepo {

    private static final String CONFIG_EXT = ".config";
    private static final Map<String, Integer> ENDING_MODULES = new HashMap<>();
    static {
      // b/62732298 put testFullDisk in the end to accommodate CTSMediaStressTest temporally
      ENDING_MODULES.put("CtsAppSecurityHostTestCases", 1);
      ENDING_MODULES.put("CtsMonkeyTestCases", 2);
    }
    // Synchronization objects for Token Modules.
    private int mInitCount = 0;
    private Set<IModuleDef> mTokenModuleScheduled;
    private static Object lock = new Object();

    private int mTotalShards;
    private Integer mShardIndex;

    private Map<String, Set<String>> mDeviceTokens = new HashMap<>();
    private Map<String, Map<String, List<String>>> mTestArgs = new HashMap<>();
    private Map<String, Map<String, List<String>>> mModuleArgs = new HashMap<>();
    private boolean mIncludeAll;
    private Map<String, List<TestFilter>> mIncludeFilters = new HashMap<>();
    private Map<String, List<TestFilter>> mExcludeFilters = new HashMap<>();
    private IConfigurationFactory mConfigFactory = ConfigurationFactory.getInstance();

    private volatile boolean mInitialized = false;

    // Holds all the tests with tokens waiting to be run. Meaning the DUT must have a specific token.
    private List<IModuleDef> mTokenModules = new ArrayList<>();
    private List<IModuleDef> mNonTokenModules = new ArrayList<>();

    /**
     * {@inheritDoc}
     */
    @Override
    public int getNumberOfShards() {
        return mTotalShards;
    }

    /**
     * Returns the device tokens of this module repo. Exposed for testing.
     */
    protected Map<String, Set<String>> getDeviceTokens() {
        return mDeviceTokens;
    }

    /**
     * A {@link FilenameFilter} to find all modules in a directory who match the given pattern.
     */
    public static class NameFilter implements FilenameFilter {

        private String mPattern;

        public NameFilter(String pattern) {
            mPattern = pattern;
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public boolean accept(File dir, String name) {
            return name.contains(mPattern) && name.endsWith(CONFIG_EXT);
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public List<IModuleDef> getNonTokenModules() {
        return mNonTokenModules;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public List<IModuleDef> getTokenModules() {
        return mTokenModules;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String[] getModuleIds() {
        Set<String> moduleIdSet = new HashSet<>();
        for (IModuleDef moduleDef : mNonTokenModules) {
            moduleIdSet.add(moduleDef.getId());
        }
        for (IModuleDef moduleDef : mTokenModules) {
            moduleIdSet.add(moduleDef.getId());
        }
        return moduleIdSet.toArray(new String[moduleIdSet.size()]);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean isInitialized() {
        return mInitialized;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void initialize(int totalShards, Integer shardIndex, File testsDir, Set<IAbi> abis,
            List<String> deviceTokens, List<String> testArgs, List<String> moduleArgs,
            Set<String> includeFilters, Set<String> excludeFilters,
            MultiMap<String, String> metadataIncludeFilters,
            MultiMap<String, String> metadataExcludeFilters,
            IBuildInfo buildInfo) {
        CLog.d("Initializing ModuleRepo\nShards:%d\nTests Dir:%s\nABIs:%s\nDevice Tokens:%s\n" +
                "Test Args:%s\nModule Args:%s\nIncludes:%s\nExcludes:%s",
                totalShards, testsDir.getAbsolutePath(), abis, deviceTokens, testArgs, moduleArgs,
                includeFilters, excludeFilters);
        mInitialized = true;
        mTotalShards = totalShards;
        mShardIndex = shardIndex;
        synchronized (lock) {
            if (mTokenModuleScheduled == null) {
                mTokenModuleScheduled = new HashSet<>();
            }
        }

        for (String line : deviceTokens) {
            String[] parts = line.split(":");
            if (parts.length == 2) {
                String key = parts[0];
                String value = parts[1];
                Set<String> list = mDeviceTokens.get(key);
                if (list == null) {
                    list = new HashSet<>();
                    mDeviceTokens.put(key, list);
                }
                list.add(value);
            } else {
                throw new IllegalArgumentException(
                        String.format("Could not parse device token: %s", line));
            }
        }
        putArgs(testArgs, mTestArgs);
        putArgs(moduleArgs, mModuleArgs);
        mIncludeAll = includeFilters.isEmpty();
        // Include all the inclusions
        addFilters(includeFilters, mIncludeFilters, abis);
        // Exclude all the exclusions
        addFilters(excludeFilters, mExcludeFilters, abis);

        File[] configFiles = testsDir.listFiles(new ConfigFilter());
        if (configFiles.length == 0) {
            throw new IllegalArgumentException(
                    String.format("No config files found in %s", testsDir.getAbsolutePath()));
        }
        Map<String, Integer> shardedTestCounts = new HashMap<>();
        for (File configFile : configFiles) {
            final String name = configFile.getName().replace(CONFIG_EXT, "");
            final String[] pathArg = new String[] { configFile.getAbsolutePath() };
            try {
                // Invokes parser to process the test module config file
                // Need to generate a different config for each ABI as we cannot guarantee the
                // configs are idempotent. This however means we parse the same file multiple times
                for (IAbi abi : abis) {
                    String id = AbiUtils.createId(abi.getName(), name);
                    if (!shouldRunModule(id)) {
                        // If the module should not run tests based on the state of filters,
                        // skip this name/abi combination.
                        continue;
                    }

                    IConfiguration config = mConfigFactory.createConfigurationFromArgs(pathArg);
                    if (!filterByConfigMetadata(config,
                            metadataIncludeFilters, metadataExcludeFilters)) {
                        // if the module config did not pass the metadata filters, it's excluded
                        // from execution
                        continue;
                    }
                    Map<String, List<String>> args = new HashMap<>();
                    if (mModuleArgs.containsKey(name)) {
                        args.putAll(mModuleArgs.get(name));
                    }
                    if (mModuleArgs.containsKey(id)) {
                        args.putAll(mModuleArgs.get(id));
                    }
                    injectOptionsToConfig(args, config);

                    List<IRemoteTest> tests = config.getTests();
                    for (IRemoteTest test : tests) {
                        prepareTestClass(name, abi, config, test);
                    }
                    List<IRemoteTest> shardedTests = tests;
                    if (mTotalShards > 1) {
                         shardedTests = splitShardableTests(tests, buildInfo);
                    }
                    if (shardedTests.size() > 1) {
                        shardedTestCounts.put(id, shardedTests.size());
                    }
                    for (IRemoteTest test : shardedTests) {
                        addModuleDef(name, abi, test, pathArg);
                    }
                }
            } catch (ConfigurationException e) {
                throw new RuntimeException(String.format("error parsing config file: %s",
                        configFile.getName()), e);
            }
        }
        mExcludeFilters.clear();
        TestRunHandler.setTestRuns(new CompatibilityBuildHelper(buildInfo), shardedTestCounts);
    }

    /**
     * Prepare to run test classes.
     *
     * @param name module name
     * @param abi IAbi object that contains abi information
     * @param config IConfiguration object created from config file
     * @param test test class
     * @throws ConfigurationException
     */
    protected void prepareTestClass(final String name, IAbi abi, IConfiguration config,
            IRemoteTest test) throws ConfigurationException {
        String className = test.getClass().getName();
        Map<String, List<String>> testArgsMap = new HashMap<>();
        if (mTestArgs.containsKey(className)) {
            testArgsMap.putAll(mTestArgs.get(className));
        }
        injectOptionsToConfig(testArgsMap, config);
        addFiltersToTest(test, abi, name);
    }

    /**
     * Helper to inject options to a config.
     */
    @VisibleForTesting
    void injectOptionsToConfig(Map<String, List<String>> optionMap, IConfiguration config)
            throws ConfigurationException{
        for (Entry<String, List<String>> entry : optionMap.entrySet()) {
            for (String entryValue : entry.getValue()) {
                String entryName = entry.getKey();
                if (entryValue.contains(":=")) {
                    // entryValue is key-value pair
                    String key = entryValue.substring(0, entryValue.indexOf(":="));
                    String value = entryValue.substring(entryValue.indexOf(":=") + 2);
                    config.injectOptionValue(entryName, key, value);
                } else {
                    // entryValue is just the argument value
                    config.injectOptionValue(entryName, entryValue);
                }
            }
        }
    }

    private List<IRemoteTest> splitShardableTests(List<IRemoteTest> tests, IBuildInfo buildInfo) {
        ArrayList<IRemoteTest> shardedList = new ArrayList<>(tests.size());
        for (IRemoteTest test : tests) {
            if (test instanceof IBuildReceiver) {
                ((IBuildReceiver)test).setBuild(buildInfo);
            }
            if (mShardIndex != null && test instanceof IStrictShardableTest) {
                for (int i = 0; i < mTotalShards; i++) {
                    shardedList.add(((IStrictShardableTest)test).getTestShard(mTotalShards, i));
                }
            } else {
                shardedList.add(test);
            }
        }
        return shardedList;
    }

    private void addFilters(Set<String> stringFilters,
            Map<String, List<TestFilter>> filters, Set<IAbi> abis) {
        for (String filterString : stringFilters) {
            TestFilter filter = TestFilter.createFrom(filterString);
            String abi = filter.getAbi();
            if (abi == null) {
                for (IAbi a : abis) {
                    addFilter(a.getName(), filter, filters);
                }
            } else {
                addFilter(abi, filter, filters);
            }
        }
    }

    private void addFilter(String abi, TestFilter filter,
            Map<String, List<TestFilter>> filters) {
        getFilter(filters, AbiUtils.createId(abi, filter.getName())).add(filter);
    }

    private List<TestFilter> getFilter(Map<String, List<TestFilter>> filters, String id) {
        List<TestFilter> fs = filters.get(id);
        if (fs == null) {
            fs = new ArrayList<>();
            filters.put(id, fs);
        }
        return fs;
    }

    protected void addModuleDef(String name, IAbi abi, IRemoteTest test, String[] configPaths)
            throws ConfigurationException {
        // Invokes parser to process the test module config file
        IConfiguration config = mConfigFactory.createConfigurationFromArgs(configPaths);
        addModuleDef(new ModuleDef(name, abi, test, config.getTargetPreparers(),
                config.getConfigurationDescription()));
    }

    protected void addModuleDef(IModuleDef moduleDef) {
        Set<String> tokens = moduleDef.getTokens();
        if (tokens != null && !tokens.isEmpty()) {
            mTokenModules.add(moduleDef);
        } else {
            mNonTokenModules.add(moduleDef);
        }
    }

    private void addFiltersToTest(IRemoteTest test, IAbi abi, String name) {
        String moduleId = AbiUtils.createId(abi.getName(), name);
        if (!(test instanceof ITestFilterReceiver)) {
            throw new IllegalArgumentException(String.format(
                    "Test in module %s must implement ITestFilterReceiver.", moduleId));
        }
        List<TestFilter> mdIncludes = getFilter(mIncludeFilters, moduleId);
        List<TestFilter> mdExcludes = getFilter(mExcludeFilters, moduleId);
        if (!mdIncludes.isEmpty()) {
            addTestIncludes((ITestFilterReceiver) test, mdIncludes, name);
        }
        if (!mdExcludes.isEmpty()) {
            addTestExcludes((ITestFilterReceiver) test, mdExcludes, name);
        }
    }

    @VisibleForTesting
    protected boolean filterByConfigMetadata(IConfiguration config,
            MultiMap<String, String> include, MultiMap<String, String> exclude) {
        MultiMap<String, String> metadata = config.getConfigurationDescription().getAllMetaData();
        boolean shouldInclude = false;
        for (String key : include.keySet()) {
            Set<String> filters = new HashSet<>(include.get(key));
            if (metadata.containsKey(key)) {
                filters.retainAll(metadata.get(key));
                if (!filters.isEmpty()) {
                    // inclusion filter is not empty and there's at least one matching inclusion
                    // rule so there's no need to match other inclusion rules
                    shouldInclude = true;
                    break;
                }
            }
        }
        if (!include.isEmpty() && !shouldInclude) {
            // if inclusion filter is not empty and we didn't find a match, the module will not be
            // included
            return false;
        }
        // Now evaluate exclusion rules, this ordering also means that exclusion rules may override
        // inclusion rules: a config already matched for inclusion may still be excluded if matching
        // rules exist
        for (String key : exclude.keySet()) {
            Set<String> filters = new HashSet<>(exclude.get(key));
            if (metadata.containsKey(key)) {
                filters.retainAll(metadata.get(key));
                if (!filters.isEmpty()) {
                    // we found at least one matching exclusion rules, so we are excluding this
                    // this module
                    return false;
                }
            }
        }
        // we've matched at least one inclusion rule (if there's any) AND we didn't match any of the
        // exclusion rules (if there's any)
        return true;
    }

    private boolean shouldRunModule(String moduleId) {
        List<TestFilter> mdIncludes = getFilter(mIncludeFilters, moduleId);
        List<TestFilter> mdExcludes = getFilter(mExcludeFilters, moduleId);
        // if including all modules or includes exist for this module, and there are not excludes
        // for the entire module, this module should be run.
        return (mIncludeAll || !mdIncludes.isEmpty()) && !containsModuleExclude(mdExcludes);
    }

    private void addTestIncludes(ITestFilterReceiver test, List<TestFilter> includes,
            String name) {
        if (test instanceof ITestFileFilterReceiver) {
            File includeFile = createFilterFile(name, ".include", includes);
            ((ITestFileFilterReceiver)test).setIncludeTestFile(includeFile);
        } else {
            // add test includes one at a time
            for (TestFilter include : includes) {
                String filterTestName = include.getTest();
                if (filterTestName != null) {
                    test.addIncludeFilter(filterTestName);
                }
            }
        }
    }

    private void addTestExcludes(ITestFilterReceiver test, List<TestFilter> excludes,
            String name) {
        if (test instanceof ITestFileFilterReceiver) {
            File excludeFile = createFilterFile(name, ".exclude", excludes);
            ((ITestFileFilterReceiver)test).setExcludeTestFile(excludeFile);
        } else {
            // add test excludes one at a time
            for (TestFilter exclude : excludes) {
                test.addExcludeFilter(exclude.getTest());
            }
        }
    }

    private File createFilterFile(String prefix, String suffix, List<TestFilter> filters) {
        File filterFile = null;
        PrintWriter out = null;
        try {
            filterFile = FileUtil.createTempFile(prefix, suffix);
            out = new PrintWriter(filterFile);
            for (TestFilter filter : filters) {
                String filterTest = filter.getTest();
                if (filterTest != null) {
                    out.println(filterTest);
                }
            }
            out.flush();
        } catch (IOException e) {
            throw new RuntimeException("Failed to create filter file");
        } finally {
            if (out != null) {
                out.close();
            }
        }
        filterFile.deleteOnExit();
        return filterFile;
    }

    /*
     * Returns true iff one or more test filters in excludes apply to the entire module.
     */
    private boolean containsModuleExclude(Collection<TestFilter> excludes) {
        for (TestFilter exclude : excludes) {
            if (exclude.getTest() == null) {
                return true;
            }
        }
        return false;
    }

    /**
     * A {@link FilenameFilter} to find all the config files in a directory.
     */
    public static class ConfigFilter implements FilenameFilter {

        /**
         * {@inheritDoc}
         */
        @Override
        public boolean accept(File dir, String name) {
            CLog.d("%s/%s", dir.getAbsolutePath(), name);
            return name.endsWith(CONFIG_EXT);
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public LinkedList<IModuleDef> getModules(String serial, int shardIndex) {
        Collections.sort(mNonTokenModules, new ExecutionOrderComparator());
        List<IModuleDef> modules = getShard(mNonTokenModules, shardIndex, mTotalShards);
        if (modules == null) {
            modules = new LinkedList<IModuleDef>();
        }
        long estimatedTime = 0;
        for (IModuleDef def : modules) {
            estimatedTime += def.getRuntimeHint();
        }

        // FIXME: Token Modules are the only last part that is not deterministic.
        synchronized (lock) {
            // Get tokens from the device
            Set<String> tokens = mDeviceTokens.get(serial);
            if (tokens != null && !tokens.isEmpty()) {
                // if it matches any of the token modules, add them
                for (IModuleDef def : mTokenModules) {
                    if (!mTokenModuleScheduled.contains(def)) {
                        if (tokens.equals(def.getTokens())) {
                            modules.add(def);
                            CLog.d("Adding %s to scheduled token", def);
                            mTokenModuleScheduled.add(def);
                        }
                    }
                }
            }
            // the last shard going through may add everything remaining.
            if (mInitCount == (mTotalShards - 1) &&
                    mTokenModuleScheduled.size() != mTokenModules.size()) {
                mTokenModules.removeAll(mTokenModuleScheduled);
                if (mTotalShards != 1) {
                    // Only print the warnings if we are sharding.
                    CLog.e("Could not find any token for %s. Adding to last shard.", mTokenModules);
                }
                modules.addAll(mTokenModules);
            }
            mInitCount++;
        }
        Collections.sort(modules, new ExecutionOrderComparator());
        int uniqueCount = UniqueModuleCountUtil.countUniqueModules(modules);
        CLog.logAndDisplay(LogLevel.INFO, "%s running %s test sub-modules, expected to complete "
                + "in %s.", serial, uniqueCount, TimeUtil.formatElapsedTime(estimatedTime));
        CLog.d("module list for this shard: %s", modules);
        LinkedList<IModuleDef> tests = new LinkedList<>();
        tests.addAll(modules);
        return tests;
    }

    /**
     * Helper to linearly split the list into shards with balanced runtimeHint.
     * Exposed for testing.
     */
    protected List<IModuleDef> getShard(List<IModuleDef> fullList, int shardIndex, int totalShard) {
        List<List<IModuleDef>> res = LinearPartition.split(fullList, totalShard);
        if (res.isEmpty()) {
            return null;
        }
        if (shardIndex >= res.size()) {
            // If we could not shard up to expectation
            return null;
        }
        return res.get(shardIndex);
    }

    /**
     * @return the {@link List} of modules whose name contains the given pattern.
     */
    public static List<String> getModuleNamesMatching(File directory, String pattern) {
        String[] names = directory.list(new NameFilter(pattern));
        List<String> modules = new ArrayList<String>(names.length);
        for (String name : names) {
            int index = name.indexOf(CONFIG_EXT);
            if (index > 0) {
                String module = name.substring(0, index);
                if (module.equals(pattern)) {
                    // Pattern represents a single module, just return a single-item list
                    modules = new ArrayList<>(1);
                    modules.add(module);
                    return modules;
                }
                modules.add(module);
            }
        }
        return modules;
    }

    private static void putArgs(List<String> args,
            Map<String, Map<String, List<String>>> argsMap) {
        for (String arg : args) {
            String[] parts = arg.split(":");
            String target = parts[0];
            String name = parts[1];
            String value;
            if (parts.length == 4) {
                // key and value given, keep the pair delimited by ':' and stored as value
                value = String.format("%s:%s", parts[2], parts[3]);
            } else {
                value = parts[2];
            }
            Map<String, List<String>> map = argsMap.get(target);
            if (map == null) {
                map = new HashMap<>();
                argsMap.put(target, map);
            }
            List<String> valueList = map.get(name);
            if (valueList == null) {
                valueList = new ArrayList<>();
                map.put(name, valueList);
            }
            valueList.add(value);
        }
    }

    /**
     * Sort by name and use runtimeHint for separation, shortest test first.
     */
    private static class ExecutionOrderComparator implements Comparator<IModuleDef> {
        @Override
        public int compare(IModuleDef def1, IModuleDef def2) {
            int value1 = 0;
            int value2 = 0;
            if (ENDING_MODULES.containsKey(def1.getName())) {
                value1 = ENDING_MODULES.get(def1.getName());
            }
            if (ENDING_MODULES.containsKey(def2.getName())) {
                value2 = ENDING_MODULES.get(def2.getName());
            }
            if (value1 == 0 && value2 == 0) {
                int time = (int) Math.signum(def1.getRuntimeHint() - def2.getRuntimeHint());
                if (time == 0) {
                    return def1.getName().compareTo(def2.getName());
                }
                return time;
            }
            return (int) Math.signum(value1 - value2);
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void tearDown() {
        mNonTokenModules.clear();
        mTokenModules.clear();
        mIncludeFilters.clear();
        mExcludeFilters.clear();
        mTestArgs.clear();
        mModuleArgs.clear();
    }

    /**
     * @return the mConfigFactory
     */
    protected IConfigurationFactory getConfigFactory() {
        return mConfigFactory;
    }
}
