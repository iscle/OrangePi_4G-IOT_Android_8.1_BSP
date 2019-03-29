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

package com.android.cts.core.runner;

import android.app.Activity;
import android.app.Instrumentation;
import android.os.Bundle;
import android.os.Debug;
import android.support.test.internal.runner.listener.InstrumentationResultPrinter;
import android.support.test.internal.runner.listener.InstrumentationRunListener;
import android.support.test.internal.util.AndroidRunnerParams;
import android.util.Log;
import com.android.cts.core.runner.support.ExtendedAndroidRunnerBuilder;
import com.google.common.base.Splitter;
import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.FileReader;
import java.io.IOException;
import java.io.PrintStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.junit.runner.Computer;
import org.junit.runner.JUnitCore;
import org.junit.runner.Request;
import org.junit.runner.Result;
import org.junit.runner.Runner;
import org.junit.runner.manipulation.Filter;
import org.junit.runner.manipulation.Filterable;
import org.junit.runner.manipulation.NoTestsRemainException;
import org.junit.runner.notification.RunListener;
import org.junit.runners.model.InitializationError;
import org.junit.runners.model.RunnerBuilder;

import static com.android.cts.core.runner.AndroidJUnitRunnerConstants.ARGUMENT_COUNT;
import static com.android.cts.core.runner.AndroidJUnitRunnerConstants.ARGUMENT_DEBUG;
import static com.android.cts.core.runner.AndroidJUnitRunnerConstants.ARGUMENT_LOG_ONLY;
import static com.android.cts.core.runner.AndroidJUnitRunnerConstants.ARGUMENT_NOT_TEST_CLASS;
import static com.android.cts.core.runner.AndroidJUnitRunnerConstants.ARGUMENT_NOT_TEST_FILE;
import static com.android.cts.core.runner.AndroidJUnitRunnerConstants.ARGUMENT_NOT_TEST_PACKAGE;
import static com.android.cts.core.runner.AndroidJUnitRunnerConstants.ARGUMENT_TEST_CLASS;
import static com.android.cts.core.runner.AndroidJUnitRunnerConstants.ARGUMENT_TEST_FILE;
import static com.android.cts.core.runner.AndroidJUnitRunnerConstants.ARGUMENT_TEST_PACKAGE;
import static com.android.cts.core.runner.AndroidJUnitRunnerConstants.ARGUMENT_TIMEOUT;

/**
 * A drop-in replacement for AndroidJUnitTestRunner, which understands the same arguments, and has
 * similar functionality, but can filter by expectations and allows a custom runner-builder to be
 * provided.
 */
public class CoreTestRunner extends Instrumentation {

    static final String TAG = "LibcoreTestRunner";

    private static final java.lang.String ARGUMENT_ROOT_CLASSES = "core-root-classes";

    private static final String ARGUMENT_CORE_LISTENER = "core-listener";

    private static final Splitter CLASS_LIST_SPLITTER = Splitter.on(',').trimResults();

    /** The args for the runner. */
    private Bundle args;

    /** Only log the number and names of tests, and not run them. */
    private boolean logOnly;

    /** The amount of time in millis to wait for a single test to complete. */
    private long testTimeout;

    /**
     * The list of tests to run.
     */
    private TestList testList;

    /**
     * The list of {@link RunListener} classes to create.
     */
    private List<Class<? extends RunListener>> listenerClasses;
    private Filter expectationFilter;

    @Override
    public void onCreate(final Bundle args) {
        super.onCreate(args);
        this.args = args;

        boolean debug = "true".equalsIgnoreCase(args.getString(ARGUMENT_DEBUG));
        if (debug) {
            Log.i(TAG, "Waiting for debugger to connect...");
            Debug.waitForDebugger();
            Log.i(TAG, "Debugger connected.");
        }

        // Log the message only after getting a value from the args so that the args are
        // unparceled.
        Log.d(TAG, "In OnCreate: " + args);

        // Treat logOnly and count as the same. This is not quite true as count should only send
        // the host the number of tests but logOnly should send the name and number. However,
        // this is how this has always behaved and it does not appear to have caused any problems.
        // Changing it seems unnecessary given that count is CTSv1 only and CTSv1 will be removed
        // soon now that CTSv2 is ready.
        boolean testCountOnly = args.getBoolean(ARGUMENT_COUNT);
        this.logOnly = "true".equalsIgnoreCase(args.getString(ARGUMENT_LOG_ONLY)) || testCountOnly;
        this.testTimeout = parseUnsignedLong(args.getString(ARGUMENT_TIMEOUT), ARGUMENT_TIMEOUT);

        expectationFilter = new ExpectationBasedFilter(args);

        // The test can be run specifying a list of tests to run, or as cts-tradefed does it,
        // by passing a fileName with a test to run on each line.
        Set<String> testNameSet = new HashSet<>();
        String arg;
        if ((arg = args.getString(ARGUMENT_TEST_FILE)) != null) {
            // The tests are specified in a file.
            try {
                testNameSet.addAll(readTestsFromFile(arg));
            } catch (IOException err) {
                finish(Activity.RESULT_CANCELED, new Bundle());
                return;
            }
        } else if ((arg = args.getString(ARGUMENT_TEST_CLASS)) != null) {
            // The tests are specified in a String passed in the bundle.
            String[] tests = arg.split(",");
            testNameSet.addAll(Arrays.asList(tests));
        }

        // Tests may be excluded from the run by passing a list of tests not to run,
        // or by passing a fileName with a test not to run on each line.
        Set<String> notTestNameSet = new HashSet<>();
        if ((arg = args.getString(ARGUMENT_NOT_TEST_FILE)) != null) {
            // The tests are specified in a file.
            try {
                notTestNameSet.addAll(readTestsFromFile(arg));
            } catch (IOException err) {
                finish(Activity.RESULT_CANCELED, new Bundle());
                return;
            }
        } else if ((arg = args.getString(ARGUMENT_NOT_TEST_CLASS)) != null) {
            // The classes are specified in a String passed in the bundle
            String[] tests = arg.split(",");
            notTestNameSet.addAll(Arrays.asList(tests));
        }

        Set<String> packageNameSet = new HashSet<>();
        if ((arg = args.getString(ARGUMENT_TEST_PACKAGE)) != null) {
            // The packages are specified in a String passed in the bundle
            String[] packages = arg.split(",");
            packageNameSet.addAll(Arrays.asList(packages));
        }

        Set<String> notPackageNameSet = new HashSet<>();
        if ((arg = args.getString(ARGUMENT_NOT_TEST_PACKAGE)) != null) {
            // The packages are specified in a String passed in the bundle
            String[] packages = arg.split(",");
            notPackageNameSet.addAll(Arrays.asList(packages));
        }

        List<String> roots = getRootClassNames(args);
        if (roots == null) {
            // Find all test classes
            Collection<Class<?>> classes = TestClassFinder.getClasses(
                Collections.singletonList(getContext().getPackageCodePath()),
                getClass().getClassLoader());
            testList = new TestList(classes);
        } else {
            testList = TestList.rootList(roots);
        }

        testList.addIncludeTestPackages(packageNameSet);
        testList.addExcludeTestPackages(notPackageNameSet);
        testList.addIncludeTests(testNameSet);
        testList.addExcludeTests(notTestNameSet);

        listenerClasses = new ArrayList<>();
        String listenerArg = args.getString(ARGUMENT_CORE_LISTENER);
        if (listenerArg != null) {
            List<String> listenerClassNames = CLASS_LIST_SPLITTER.splitToList(listenerArg);
            for (String listenerClassName : listenerClassNames) {
                try {
                    Class<? extends RunListener> listenerClass = Class.forName(listenerClassName)
                            .asSubclass(RunListener.class);
                    listenerClasses.add(listenerClass);
                } catch (ClassNotFoundException e) {
                    Log.e(TAG, "Could not load listener class: " + listenerClassName, e);
                }
            }
        }

        start();
    }

    private List<String> getRootClassNames(Bundle args) {
        String rootClasses = args.getString(ARGUMENT_ROOT_CLASSES);
        List<String> roots;
        if (rootClasses == null) {
            roots = null;
        } else {
            roots = CLASS_LIST_SPLITTER.splitToList(rootClasses);
        }
        return roots;
    }

    @Override
    public void onStart() {
        if (logOnly) {
            Log.d(TAG, "Counting/logging tests only");
        } else {
            Log.d(TAG, "Running tests");
        }

        AndroidRunnerParams runnerParams = new AndroidRunnerParams(this, args,
                false, testTimeout, false /*ignoreSuiteMethods*/);

        Runner runner;
        try {
            RunnerBuilder runnerBuilder = new ExtendedAndroidRunnerBuilder(runnerParams);
            Class[] classes = testList.getClassesToRun();
            for (Class cls : classes) {
              Log.d(TAG, "Found class to run: " + cls.getName());
            }
            runner = new Computer().getSuite(runnerBuilder, classes);

            if (runner instanceof Filterable) {
                Log.d(TAG, "Applying filters");
                Filterable filterable = (Filterable) runner;

                // Filter out all the tests that are expected to fail.
                try {
                    filterable.filter(expectationFilter);
                } catch (NoTestsRemainException e) {
                    // Sometimes filtering will remove all tests but we do not care about that.
                }
                Log.d(TAG, "Applied filters");
            }

            // If the tests are only supposed to be logged and not actually run then replace the
            // runner with a runner that will fire notifications for all the tests that would have
            // been run. This is needed because CTSv2 does a log only run through a CTS module in
            // order to generate a list of tests that will be run so that it can monitor them.
            // Encapsulating that in a Runner implementation makes it easier to leverage the
            // existing code for running tests.
            if (logOnly) {
                runner = new DescriptionHierarchyNotifier(runner.getDescription());
            }

        } catch (InitializationError e) {
            throw new RuntimeException("Could not create a suite", e);
        }

        InstrumentationResultPrinter instrumentationResultPrinter =
                new InstrumentationResultPrinter();
        instrumentationResultPrinter.setInstrumentation(this);

        JUnitCore core = new JUnitCore();
        core.addListener(instrumentationResultPrinter);

        // If not logging the list of tests then add any additional configured listeners. These
        // must be added before firing any events.
        if (!logOnly) {
            // Add additional configured listeners.
            for (Class<? extends RunListener> listenerClass : listenerClasses) {
                try {
                    RunListener runListener = listenerClass.newInstance();
                    if (runListener instanceof InstrumentationRunListener) {
                        ((InstrumentationRunListener) runListener).setInstrumentation(this);
                    }
                    core.addListener(runListener);
                } catch (InstantiationException | IllegalAccessException e) {
                    Log.e(TAG,
                            "Could not create instance of listener: " + listenerClass, e);
                }
            }
        }

        Log.d(TAG, "Finished preparations, running/listing tests");

        Bundle results = new Bundle();
        Result junitResults = new Result();
        try {
            junitResults = core.run(Request.runner(runner));
        } catch (RuntimeException e) {
            final String msg = "Fatal exception when running tests";
            Log.e(TAG, msg, e);
            // report the exception to instrumentation out
            results.putString(Instrumentation.REPORT_KEY_STREAMRESULT,
                    msg + "\n" + Log.getStackTraceString(e));
        } finally {
            ByteArrayOutputStream summaryStream = new ByteArrayOutputStream();
            // create the stream used to output summary data to the user
            PrintStream summaryWriter = new PrintStream(summaryStream);
            instrumentationResultPrinter.instrumentationRunFinished(summaryWriter,
                    results, junitResults);
            summaryWriter.close();
            results.putString(Instrumentation.REPORT_KEY_STREAMRESULT,
                    String.format("\n%s", summaryStream.toString()));
        }


        Log.d(TAG, "Finished");
        finish(Activity.RESULT_OK, results);
    }

    /**
     * Read tests from a specified file.
     *
     * @return class names of tests. If there was an error reading the file, null is returned.
     */
    private static List<String> readTestsFromFile(String fileName) throws IOException {
        try (BufferedReader br = new BufferedReader(new FileReader(fileName))) {
            List<String> tests = new ArrayList<>();
            String line;
            while ((line = br.readLine()) != null) {
                tests.add(line);
            }
            return tests;
        } catch (IOException err) {
            Log.e(TAG, "There was an error reading the test class list: " + err.getMessage());
            throw err;
        }
    }

    /**
     * Parse long from given value - except either Long or String.
     *
     * @return the value, -1 if not found
     * @throws NumberFormatException if value is negative or not a number
     */
    private static long parseUnsignedLong(Object value, String name) {
        if (value != null) {
            long longValue = Long.parseLong(value.toString());
            if (longValue < 0) {
                throw new NumberFormatException(name + " can not be negative");
            }
            return longValue;
        }
        return -1;
    }
}
