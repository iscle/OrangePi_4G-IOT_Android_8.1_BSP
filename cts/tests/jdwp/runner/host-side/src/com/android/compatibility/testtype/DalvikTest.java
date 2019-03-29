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

package com.android.compatibility.testtype;

import com.android.compatibility.common.tradefed.build.CompatibilityBuildHelper;
import com.android.ddmlib.IShellOutputReceiver;
import com.android.ddmlib.Log;
import com.android.ddmlib.Log.LogLevel;
import com.android.ddmlib.MultiLineReceiver;
import com.android.ddmlib.testrunner.TestIdentifier;
import com.android.tradefed.build.IBuildInfo;
import com.android.tradefed.config.Option;
import com.android.tradefed.config.OptionCopier;
import com.android.tradefed.device.DeviceNotAvailableException;
import com.android.tradefed.device.ITestDevice;
import com.android.tradefed.result.ITestInvocationListener;
import com.android.tradefed.testtype.IAbi;
import com.android.tradefed.testtype.IAbiReceiver;
import com.android.tradefed.testtype.IBuildReceiver;
import com.android.tradefed.testtype.IDeviceTest;
import com.android.tradefed.testtype.IRemoteTest;
import com.android.tradefed.testtype.IRuntimeHintProvider;
import com.android.tradefed.testtype.IShardableTest;
import com.android.tradefed.testtype.ITestCollector;
import com.android.tradefed.testtype.ITestFileFilterReceiver;
import com.android.tradefed.testtype.ITestFilterReceiver;
import com.android.tradefed.util.AbiUtils;
import com.android.tradefed.util.ArrayUtil;
import com.android.tradefed.util.FileUtil;
import com.android.tradefed.util.TimeVal;
import com.google.common.base.Splitter;

import vogar.ExpectationStore;
import vogar.ModeId;

import java.io.BufferedReader;
import java.io.File;
import java.io.FilenameFilter;
import java.io.FileReader;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;

/**
 * A wrapper to run tests against Dalvik.
 */
public class DalvikTest implements IAbiReceiver, IBuildReceiver, IDeviceTest, IRemoteTest,
        IRuntimeHintProvider, IShardableTest, ITestCollector, ITestFileFilterReceiver,
        ITestFilterReceiver {

    private static final String TAG = DalvikTest.class.getSimpleName();

    /**
     * TEST_PACKAGES is a Set containing the names of packages on the classpath known to contain
     * tests to be run under DalvikTest. The TEST_PACKAGES set is used to shard DalvikTest into
     * multiple DalvikTests, each responsible for running one of these packages' tests.
     */
    private static final Set<String> TEST_PACKAGES = new HashSet<>();
    private static final String JDWP_PACKAGE_BASE = "org.apache.harmony.jpda.tests.jdwp.%s";
    static {
        // Though uppercase, these are package names, not class names
        TEST_PACKAGES.add(String.format(JDWP_PACKAGE_BASE, "ArrayReference"));
        TEST_PACKAGES.add(String.format(JDWP_PACKAGE_BASE, "ArrayType"));
        TEST_PACKAGES.add(String.format(JDWP_PACKAGE_BASE, "ClassLoaderReference"));
        TEST_PACKAGES.add(String.format(JDWP_PACKAGE_BASE, "ClassObjectReference"));
        TEST_PACKAGES.add(String.format(JDWP_PACKAGE_BASE, "ClassType"));
        TEST_PACKAGES.add(String.format(JDWP_PACKAGE_BASE, "DebuggerOnDemand"));
        TEST_PACKAGES.add(String.format(JDWP_PACKAGE_BASE, "Deoptimization"));
        TEST_PACKAGES.add(String.format(JDWP_PACKAGE_BASE, "EventModifiers"));
        TEST_PACKAGES.add(String.format(JDWP_PACKAGE_BASE, "Events"));
        TEST_PACKAGES.add(String.format(JDWP_PACKAGE_BASE, "InterfaceType"));
        TEST_PACKAGES.add(String.format(JDWP_PACKAGE_BASE, "Method"));
        TEST_PACKAGES.add(String.format(JDWP_PACKAGE_BASE, "MultiSession"));
        TEST_PACKAGES.add(String.format(JDWP_PACKAGE_BASE, "ObjectReference"));
        TEST_PACKAGES.add(String.format(JDWP_PACKAGE_BASE, "ReferenceType"));
        TEST_PACKAGES.add(String.format(JDWP_PACKAGE_BASE, "StackFrame"));
        TEST_PACKAGES.add(String.format(JDWP_PACKAGE_BASE, "StringReference"));
        TEST_PACKAGES.add(String.format(JDWP_PACKAGE_BASE, "ThreadGroupReference"));
        TEST_PACKAGES.add(String.format(JDWP_PACKAGE_BASE, "ThreadReference"));
        TEST_PACKAGES.add(String.format(JDWP_PACKAGE_BASE, "VirtualMachine"));
    }

    private static final String EXPECTATIONS_EXT = ".expectations";
    // Command to run the VM, args are bitness, classpath, dalvik-args, abi, runner-args,
    // include and exclude filters, and exclude filters file.
    private static final String COMMAND = "dalvikvm%s -classpath %s %s "
            + "com.android.compatibility.dalvik.DalvikTestRunner --abi=%s %s %s %s %s %s %s";
    private static final String INCLUDE_FILE = "/data/local/tmp/dalvik/includes";
    private static final String EXCLUDE_FILE = "/data/local/tmp/dalvik/excludes";
    private static String START_RUN = "start-run";
    private static String END_RUN = "end-run";
    private static String START_TEST = "start-test";
    private static String END_TEST = "end-test";
    private static String FAILURE = "failure";

    @Option(name = "run-name", description = "The name to use when reporting results")
    private String mRunName;

    @Option(name = "classpath", description = "Holds the paths to search when loading tests")
    private List<String> mClasspath = new ArrayList<>();

    @Option(name = "dalvik-arg", description = "Holds arguments to pass to Dalvik")
    private List<String> mDalvikArgs = new ArrayList<>();

    @Option(name = "runner-arg",
            description = "Holds arguments to pass to the device-side test runner")
    private List<String> mRunnerArgs = new ArrayList<>();

    @Option(name = "include-filter",
            description = "The include filters of the test name to run.")
    private List<String> mIncludeFilters = new ArrayList<>();

    @Option(name = "exclude-filter",
            description = "The exclude filters of the test name to run.")
    private List<String> mExcludeFilters = new ArrayList<>();

    @Option(name = "test-file-include-filter",
            description="A file containing a list of line separated test classes and optionally"
            + " methods to include")
    private File mIncludeTestFile = null;

    @Option(name = "test-file-exclude-filter",
            description="A file containing a list of line separated test classes and optionally"
            + " methods to exclude")
    private File mExcludeTestFile = null;

    @Option(name = "runtime-hint",
            isTimeVal = true,
            description="The hint about the test's runtime.")
    private long mRuntimeHint = 60000;// 1 minute

    @Option(name = "known-failures",
            description = "Comma-separated list of files specifying known-failures to be skipped")
    private String mKnownFailures;

    @Option(name = "collect-tests-only",
            description = "Only invoke the instrumentation to collect list of applicable test "
                    + "cases. All test run callbacks will be triggered, but test execution will "
                    + "not be actually carried out.")
    private boolean mCollectTestsOnly = false;

    @Option(name = "per-test-timeout",
            description = "The maximum amount of time during which the DalvikTestRunner may "
                    + "yield no output. Because the runner outputs results for each test, this "
                    + "is essentially a per-test timeout")
    private long mPerTestTimeout = 10; // 10 minutes

    private IAbi mAbi;
    private CompatibilityBuildHelper mBuildHelper;
    private ITestDevice mDevice;

    /**
     * {@inheritDoc}
     */
    @Override
    public void setAbi(IAbi abi) {
        mAbi = abi;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public IAbi getAbi() {
        return mAbi;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void setBuild(IBuildInfo build) {
        mBuildHelper = new CompatibilityBuildHelper(build);
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
    public ITestDevice getDevice() {
        return mDevice;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void addIncludeFilter(String filter) {
        mIncludeFilters.add(filter);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void addAllIncludeFilters(Set<String> filters) {
        mIncludeFilters.addAll(filters);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void addExcludeFilter(String filter) {
        mExcludeFilters.add(filter);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void addAllExcludeFilters(Set<String> filters) {
        mExcludeFilters.addAll(filters);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void setIncludeTestFile(File testFile) {
        mIncludeTestFile = testFile;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void setExcludeTestFile(File testFile) {
        mExcludeTestFile = testFile;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public long getRuntimeHint() {
        return mRuntimeHint;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void setCollectTestsOnly(boolean shouldCollectTest) {
        mCollectTestsOnly = shouldCollectTest;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void run(final ITestInvocationListener listener) throws DeviceNotAvailableException {
        String abiName = mAbi.getName();
        String bitness = AbiUtils.getBitness(abiName);

        File tmpExcludeFile = null;
        try {
            // push one file of exclude filters to the device
            tmpExcludeFile = getExcludeFile();
            if (!mDevice.pushFile(tmpExcludeFile, EXCLUDE_FILE)) {
                Log.logAndDisplay(LogLevel.ERROR, TAG, "Couldn't push file: " + tmpExcludeFile);
            }
        } catch (IOException e) {
            throw new RuntimeException("Failed to parse expectations", e);
        } finally {
            FileUtil.deleteFile(tmpExcludeFile);
        }

        // push one file of include filters to the device, if file exists
        if (mIncludeTestFile != null) {
            String path = mIncludeTestFile.getAbsolutePath();
            if (!mIncludeTestFile.isFile() || !mIncludeTestFile.canRead()) {
                throw new RuntimeException(String.format("Failed to read include file %s", path));
            }
            if (!mDevice.pushFile(mIncludeTestFile, INCLUDE_FILE)) {
                Log.logAndDisplay(LogLevel.ERROR, TAG, "Couldn't push file: " + path);
            }
        }


        // Create command
        mDalvikArgs.add("-Duser.name=shell");
        mDalvikArgs.add("-Duser.language=en");
        mDalvikArgs.add("-Duser.region=US");
        mDalvikArgs.add("-Xcheck:jni");
        mDalvikArgs.add("-Xjnigreflimit:2000");

        String dalvikArgs = ArrayUtil.join(" ", mDalvikArgs);
        dalvikArgs = dalvikArgs.replace("|#ABI#|", bitness);

        String runnerArgs = ArrayUtil.join(" ", mRunnerArgs);
        // Filters
        StringBuilder includeFilters = new StringBuilder();
        if (!mIncludeFilters.isEmpty()) {
            includeFilters.append("--include-filter=");
            includeFilters.append(ArrayUtil.join(",", mIncludeFilters));
        }
        StringBuilder excludeFilters = new StringBuilder();
        if (!mExcludeFilters.isEmpty()) {
            excludeFilters.append("--exclude-filter=");
            excludeFilters.append(ArrayUtil.join(",", mExcludeFilters));
        }
        // Filter files
        String includeFile = String.format("--include-filter-file=%s", INCLUDE_FILE);
        String excludeFile = String.format("--exclude-filter-file=%s", EXCLUDE_FILE);
        // Communicate with DalvikTestRunner if tests should only be collected
        String collectTestsOnlyString = (mCollectTestsOnly) ? "--collect-tests-only" : "";
        final String command = String.format(COMMAND, bitness,
                ArrayUtil.join(File.pathSeparator, mClasspath),
                dalvikArgs, abiName, runnerArgs,
                includeFilters, excludeFilters, includeFile, excludeFile, collectTestsOnlyString);
        IShellOutputReceiver receiver = new MultiLineReceiver() {
            private TestIdentifier test;

            @Override
            public boolean isCancelled() {
                return false;
            }

            @Override
            public void processNewLines(String[] lines) {
                for (String line : lines) {
                    String[] parts = line.split(":", 2);
                    String tag = parts[0];
                    if (tag.equals(START_RUN)) {
                        listener.testRunStarted(mRunName, Integer.parseInt(parts[1]));
                        Log.logAndDisplay(LogLevel.INFO, TAG, command);
                        Log.logAndDisplay(LogLevel.INFO, TAG, line);
                    } else if (tag.equals(END_RUN)) {
                        listener.testRunEnded(Integer.parseInt(parts[1]),
                                Collections.<String, String>emptyMap());
                        Log.logAndDisplay(LogLevel.INFO, TAG, line);
                    } else if (tag.equals(START_TEST)) {
                        test = getTestIdentifier(parts[1]);
                        listener.testStarted(test);
                    } else if (tag.equals(FAILURE)) {
                        listener.testFailed(test, parts[1]);
                    } else if (tag.equals(END_TEST)) {
                        listener.testEnded(getTestIdentifier(parts[1]),
                                Collections.<String, String>emptyMap());
                    } else {
                        Log.logAndDisplay(LogLevel.INFO, TAG, line);
                    }
                }
            }

            private TestIdentifier getTestIdentifier(String name) {
                String[] parts = name.split("#");
                String className = parts[0];
                String testName = "";
                if (parts.length > 1) {
                    testName = parts[1];
                }
                return new TestIdentifier(className, testName);
            }

        };
        mDevice.executeShellCommand(command, receiver, mPerTestTimeout, TimeUnit.MINUTES, 1);
    }

    /*
     * Due to known failures, there are typically too many excludes to pass via command line.
     * Collect excludes from .expectation files in the testcases directory, from files in the
     * module's resources directory, and from mExcludeTestFile, if set.
     */
    private File getExcludeFile() throws IOException {
        File excludeFile = null;
        PrintWriter out = null;

        try {
            excludeFile = File.createTempFile("excludes", "txt");
            out = new PrintWriter(excludeFile);
            // create expectation store from set of expectation files found in testcases dir
            Set<File> expectationFiles = new HashSet<>();
            for (File f : mBuildHelper.getTestsDir().listFiles(
                    new ExpectationFileFilter(mRunName))) {
                expectationFiles.add(f);
            }
            ExpectationStore testsDirStore =
                    ExpectationStore.parse(expectationFiles, ModeId.DEVICE);
            // create expectation store from expectation files found in module resources dir
            ExpectationStore resourceStore = null;
            if (mKnownFailures != null) {
                Splitter splitter = Splitter.on(',').trimResults();
                Set<String> knownFailuresFiles =
                        new HashSet<>(splitter.splitToList(mKnownFailures));
                resourceStore = ExpectationStore.parseResources(
                        getClass(), knownFailuresFiles, ModeId.DEVICE);
            }
            // Add expectations from testcases dir
            for (String exclude : testsDirStore.getAllFailures().keySet()) {
                out.println(exclude);
            }
            for (String exclude : testsDirStore.getAllOutComes().keySet()) {
                out.println(exclude);
            }
            // Add expectations from resources dir
            if (resourceStore != null) {
                for (String exclude : resourceStore.getAllFailures().keySet()) {
                    out.println(exclude);
                }
                for (String exclude : resourceStore.getAllOutComes().keySet()) {
                    out.println(exclude);
                }
            }
            // Add excludes from test-file-exclude-filter option
            for (String exclude : getFiltersFromFile(mExcludeTestFile)) {
                out.println(exclude);
            }
            out.flush();
        } finally {
            if (out != null) {
                out.close();
            }
        }
        return excludeFile;
    }


    /*
     * Helper method that reads filters from a file into a set.
     * Returns an empty set given a null file
     */
    private static Set<String> getFiltersFromFile(File f) throws IOException {
        Set<String> filters = new HashSet<String>();
        if (f != null) {
            BufferedReader reader = new BufferedReader(new FileReader(f));
            String filter = null;
            while ((filter = reader.readLine()) != null) {
                filters.add(filter);
            }
            reader.close();
        }
        return filters;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Collection<IRemoteTest> split() {
        List<IRemoteTest> shards = new ArrayList<>();
        // A DalvikTest to run any tests not contained in packages from TEST_PACKAGES, may be empty
        DalvikTest catchAll = new DalvikTest();
        OptionCopier.copyOptionsNoThrow(this, catchAll);
        catchAll.mAbi = mAbi;
        shards.add(catchAll);
        // estimate catchAll's runtime to be that of a single package in TEST_PACKAGES
        long runtimeHint = mRuntimeHint / TEST_PACKAGES.size();
        catchAll.mRuntimeHint = runtimeHint;
        for (String packageName: TEST_PACKAGES) {
            catchAll.addExcludeFilter(packageName);
            // create one shard for package 'packageName'
            DalvikTest test = new DalvikTest();
            OptionCopier.copyOptionsNoThrow(this, test);
            test.addIncludeFilter(packageName);
            test.mRuntimeHint = runtimeHint / TEST_PACKAGES.size();
            test.mAbi = mAbi;
            shards.add(test);
        }
        // return a shard for each package in TEST_PACKAGE, plus a shard for any other tests
        return shards;
    }

    /**
     * A {@link FilenameFilter} to find all the expectation files in a directory.
     */
    public static class ExpectationFileFilter implements FilenameFilter {

        private String mName;

        public ExpectationFileFilter(String name) {
            mName = name;
        }
        /**
         * {@inheritDoc}
         */
        @Override
        public boolean accept(File dir, String name) {
            return name.startsWith(mName) && name.endsWith(EXPECTATIONS_EXT);
        }
    }
}
