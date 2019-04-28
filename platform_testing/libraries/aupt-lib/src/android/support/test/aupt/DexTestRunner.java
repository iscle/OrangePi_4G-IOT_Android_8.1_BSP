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

package android.support.test.aupt;

import android.app.Instrumentation;
import android.test.AndroidTestRunner;
import android.util.Log;

import dalvik.system.DexClassLoader;

import junit.framework.AssertionFailedError;
import junit.framework.Test;
import junit.framework.TestCase;
import junit.framework.TestListener;
import junit.framework.TestResult;
import junit.framework.TestSuite;

import java.io.File;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * A DexTestRunner runs tests by name from a given list of JARs,
 * with the following additional magic:
 *
 * - Custom ClassLoading from given dexed Jars
 * - Custom test scheduling (via Scheduler)
 *
 * In addition to the parameters in the constructor, be sure to run setTest or setTestClassName
 * before attempting to runTest.
 */
class DexTestRunner extends AndroidTestRunner {
    private static final String LOG_TAG = DexTestRunner.class.getSimpleName();

    /* Constants */
    static final String DEFAULT_JAR_PATH = "/data/local/tmp/";
    static final String DEX_OPT_PATH = "dex-test-opt";

    /* Private fields */
    private final List<TestListener> mTestListeners = new ArrayList<>();
    private final DexClassLoader mLoader;
    private final long mTestTimeoutMillis;
    private final long mSuiteTimeoutMillis;

    /* TestRunner State */
    protected TestResult mTestResult = new TestResult();
    protected List<TestCase> mTestCases = new ArrayList<>();
    protected String mTestClassName;
    protected Instrumentation mInstrumentation;
    protected Scheduler mScheduler;
    protected long mSuiteEndTime;

    /** A temporary ExecutorService to manage running the current test. */
    private ExecutorService mExecutorService;

    /** The current test. */
    private TestCase mTestCase;

    /* Field initialization */
    DexTestRunner(
            Instrumentation instrumentation,
            Scheduler scheduler,
            List<String> jars,
            long testTimeoutMillis,
            long suiteTimeoutMillis) {
        super();

        mInstrumentation = instrumentation;
        mScheduler = scheduler;
        mLoader = makeLoader(jars);
        mTestTimeoutMillis = testTimeoutMillis;
        mSuiteTimeoutMillis = suiteTimeoutMillis;
    }

    /* Main methods */

    @Override
    public void runTest() {
        runTest(newResult());
    }

    @Override
    public synchronized void runTest(final TestResult testResult) {
        mTestResult = testResult;
        mSuiteEndTime = System.currentTimeMillis() + mSuiteTimeoutMillis;

        for (final TestCase testCase : mScheduler.apply(mTestCases)) {
            // Timeout the suite if we've passed the end time.
            if (mSuiteTimeoutMillis != 0 && System.currentTimeMillis() > mSuiteEndTime) {
                Log.w(LOG_TAG, String.format("Ending suite after %d mins running.",
                        TimeUnit.MILLISECONDS.toMinutes(mSuiteTimeoutMillis)));
                break;
            }

            mExecutorService = Executors.newSingleThreadExecutor();
            mTestCase = testCase;

            // A Future that calls testCase::run. The reasoning behind using a thread here
            // is that AuptTestRunner should be able to interrupt it (via killTest) if it runs
            // too long; and interrupting the main thread here without actually exiting is tricky.
            Future<TestResult> result =
                    mExecutorService.submit(
                            new Callable<TestResult>() {
                                @Override
                                public TestResult call() throws Exception {
                                    testCase.run(testResult);
                                    return testResult;
                                }
                            });

            try {
                // Run our test-running thread and wait on it.
                result.get(mTestTimeoutMillis, TimeUnit.MILLISECONDS);
            } catch (TimeoutException e) {
                killTest(e);
            } catch (ExecutionException e) {
                onError(testCase, e.getCause());
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            } finally {
                mExecutorService.shutdownNow();
                mTestCase = null;
            }
        }
    }

    /** Interrupt the current test with the given exception. */
    void killTest(Exception e) {
        if (mTestCase != null) {
            // First, tell our listeners.
            onError(mTestCase, e);

            // Kill the test.
            mExecutorService.shutdownNow();
        }
    }

    /* TestCase Initialization */

    @Override
    public void setTestClassName(String className, String methodName) {
        mTestCases.clear();
        addTestClassByName(className, methodName);
    }

    void addTestClassByName(final String className, final String methodName) {
        try {
            final Class<?> testClass = mLoader.loadClass(className);

            if (Test.class.isAssignableFrom(testClass)) {
                Test test = null;

                try {
                    // Make sure it works
                    test = (Test) testClass.getConstructor().newInstance();
                } catch (Exception e1) { /* If we fail, test will just stay null */ }

                try {
                    test = (Test) testClass.getConstructor(String.class).newInstance(methodName);
                } catch (Exception e2) { /* If we fail, test will just stay null */ }

                addTest(test);
            } else {
                throw new RuntimeException("Test class not found: " + className);
            }
        } catch (ClassNotFoundException ex) {
            throw new RuntimeException("Class not found: " + ex.getMessage());
        }

        if (mTestCases.isEmpty()) {
            throw new RuntimeException("No tests found in " + className + "#" + methodName);
        }
    }

    @Override
    public void setTest(Test test) {
        mTestCases.clear();
        addTest(test);

        // Update our test class name.
        if (TestSuite.class.isAssignableFrom(test.getClass())) {
            mTestClassName = ((TestSuite) test).getName();
        } else if (TestCase.class.isAssignableFrom(test.getClass())) {
            mTestClassName = ((TestCase) test).getName();
        } else {
            mTestClassName = test.getClass().getSimpleName();
        }
    }

    public void addTest(Test test) {
        if (test instanceof TestCase) {

            mTestCases.add((TestCase) test);

        } else if (test instanceof TestSuite) {
            Enumeration<Test> tests = ((TestSuite) test).tests();

            while (tests.hasMoreElements()) {
                addTest(tests.nextElement());
            }
        } else {
            throw new RuntimeException("Tried to add invalid test: " + test.toString());
        }
    }

    /* State Manipulation Methods */

    @Override
    public void clearTestListeners() {
        mTestListeners.clear();
    }

    @Override
    public void addTestListener(TestListener testListener) {
        if (testListener != null) {
            mTestListeners.add(testListener);
            mTestResult.addListener(testListener);
        }
    }

    void addTestListenerIf(Boolean cond, TestListener testListener) {
        if (cond && testListener != null) {
            mTestListeners.add(testListener);
        }
    }

    @Override
    public List<TestCase> getTestCases() {
        return mTestCases;
    }

    @Override
    public void setInstrumentation(Instrumentation instrumentation) {
        mInstrumentation = instrumentation;
    }

    @Override
    public TestResult getTestResult() {
        return mTestResult;
    }

    @Override
    protected TestResult createTestResult() {
        return new TestResult();
    }

    @Override
    public String getTestClassName() {
        return mTestClassName;
    }

    /* Listener Exception Callback. */

    void onError(Test test, Throwable t) {
        if (t instanceof AssertionFailedError) {
            for (TestListener listener : mTestListeners) {
                listener.addFailure(test, (AssertionFailedError) t);
            }
        } else {
            for (TestListener listener : mTestListeners) {
                listener.addError(test, t);
            }
        }
    }

    /* Package-private Utilities */

    TestResult newResult() {
        TestResult result = new TestResult();

        for (TestListener listener: mTestListeners) {
            result.addListener(listener);
        }

        return result;
    }

    static List<String> parseDexedJarPaths(String jarString) {
        List<String> jars = new ArrayList<>();

        for (String jar : jarString.split(":")) {
            // Check that jar isn't empty, but don't fail because String::split will yield
            // spurious empty results if, for example, we don't specify any jars, accidentally
            // start with a leading colon, etc.
            if (!jar.trim().isEmpty()) {
                File jarFile = jar.startsWith("/")
                        ? new File(jar)
                        : new File(DEFAULT_JAR_PATH + jar);

                if (jarFile.exists()) {
                    jars.add(jarFile.getAbsolutePath());
                } else {
                    throw new RuntimeException("Can't find jar file " + jarFile);
                }
            }
        }

        return jars;
    }

    DexClassLoader getDexClassLoader() {
        return mLoader;
    }

    DexClassLoader makeLoader(List<String> jars) {
        StringBuilder jarFiles = new StringBuilder();

        for (String jar : jars) {
            if (new File(jar).exists() && new File(jar).canRead()) {
                if (jarFiles.length() != 0) {
                    jarFiles.append(File.pathSeparator);
                }

                jarFiles.append(jar);
            } else {
                throw new IllegalArgumentException(
                        "Jar file does not exist or not accessible: "  + jar);
            }
        }

        File optDir = new File(mInstrumentation.getTargetContext().getCacheDir(), DEX_OPT_PATH);

        if (optDir.exists() || optDir.mkdirs()) {
            return new DexClassLoader(
                    jarFiles.toString(),
                    optDir.getAbsolutePath(),
                    null,
                    DexTestRunner.class.getClassLoader());
        } else {
            throw new RuntimeException(
                    "Failed to create dex optimization directory: " + optDir.getAbsolutePath());
        }
    }
}
