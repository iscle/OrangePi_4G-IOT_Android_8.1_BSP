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

package com.android.compatibility.common.tradefed.testtype;

import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import com.android.annotations.Nullable;
import com.android.annotations.VisibleForTesting;
import com.android.compatibility.common.tradefed.build.CompatibilityBuildHelper;
import com.android.ddmlib.Log.LogLevel;
import com.android.ddmlib.testrunner.RemoteAndroidTestRunner;
import com.android.ddmlib.testrunner.TestIdentifier;
import com.android.ddmlib.testrunner.TestResult;
import com.android.ddmlib.testrunner.TestResult.TestStatus;
import com.android.ddmlib.testrunner.TestRunResult;
import com.android.tradefed.build.IBuildInfo;
import com.android.tradefed.device.DeviceNotAvailableException;
import com.android.tradefed.device.ITestDevice;
import com.android.tradefed.log.LogUtil.CLog;
import com.android.tradefed.result.CollectingTestListener;
import com.android.tradefed.testtype.IAbi;
import com.android.tradefed.testtype.IAbiReceiver;
import com.android.tradefed.testtype.IBuildReceiver;
import com.android.tradefed.testtype.IDeviceTest;
import com.android.tradefed.util.AbiUtils;

import org.junit.Assume;
import org.junit.Before;
import org.junit.internal.AssumptionViolatedException;

import java.io.File;
import java.io.FileNotFoundException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

/**
 * Compatibility host test base class for JUnit4 tests. Enables host-side tests written in JUnit4
 * to access build and ABI information, as well as a reference to the testing device. The class
 * includes methods to install and uninstall test packages, as well as methods to run device-side
 * tests and retrieve their results.
 */
public class CompatibilityHostTestBase implements IAbiReceiver, IBuildReceiver, IDeviceTest {

    protected static final String AJUR = "android.support.test.runner.AndroidJUnitRunner";

    /** The build will be used. */
    protected IBuildInfo mBuild;

    /** The ABI to use. */
    protected IAbi mAbi;

    /** A reference to the device under test. */
    protected ITestDevice mDevice;

    /** The test runner used for test apps */
    private String mRunner;

    @Override
    public void setAbi(IAbi abi) {
        mAbi = abi;
    }

    @Override
    public IAbi getAbi() {
        return mAbi;
    }

    @Override
    public void setBuild(IBuildInfo buildInfo) {
        // Get the build, this is used to access the APK.
        mBuild = buildInfo;
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
    public void setDevice(ITestDevice device) {
        mDevice = device;
    }

    @Before
    public void baseSetUp() throws Exception {
        mRunner = AJUR;
    }

    /**
     * Set the runner name
     * @param runner of the device test runner
     */
    protected void setRunner(String runner) {
        mRunner = runner;
    }

    /**
     * Get the runner name
     * @return name of the device test runner
     */
    protected String getRunner() {
        return mRunner;
    }

    /**
     * Installs a package on the device
     * @param fileName the name of the file to install
     * @param options optional extra arguments to pass. See 'adb shell pm install --help' for
     * available options
     * @throws FileNotFoundException if file with filename cannot be found
     * @throws DeviceNotAvailableException
     */
    protected void installPackage(String fileName, String... options)
            throws FileNotFoundException, DeviceNotAvailableException {

        final List<String> optList = new ArrayList<>(Arrays.asList(options));
        optList.add(AbiUtils.createAbiFlag(mAbi.getName()));
        options = optList.toArray(new String[optList.size()]);

        CompatibilityBuildHelper buildHelper = new CompatibilityBuildHelper(mBuild);
        File testFile = buildHelper.getTestFile(fileName);
        // Install the APK on the device.
        String installResult = mDevice.installPackage(testFile, true, options);

        assertNull(String.format("Failed to install %s, Reason: %s", fileName, installResult),
                installResult);
    }

    /**
     * Uninstalls a package on the device
     * @param pkgName the Android package to uninstall
     * @return a {@link String} with an error code, or <code>null</code> if success
     * @throws DeviceNotAvailableException
     */
    protected String uninstallPackage(String pkgName) throws DeviceNotAvailableException {
        return mDevice.uninstallPackage(pkgName);
    }

    /**
     * Checks if a package of a given name is installed on the device
     * @param pkg the name of the package
     * @return true if the package is found on the device
     * @throws DeviceNotAvailableException
     */
    protected boolean isPackageInstalled(String pkg) throws DeviceNotAvailableException {
        for (String installedPackage : mDevice.getInstalledPackageNames()) {
            if (pkg.equals(installedPackage)) {
                return true;
            }
        }
        return false;
    }

    private void printTestResult(TestRunResult runResult) {
        for (Map.Entry<TestIdentifier, TestResult> testEntry :
                     runResult.getTestResults().entrySet()) {
            TestResult testResult = testEntry.getValue();
            TestStatus testStatus = testResult.getStatus();
            CLog.logAndDisplay(LogLevel.INFO,
                    "Test " + testEntry.getKey() + ": " + testStatus);
            if (testStatus != TestStatus.PASSED && testStatus != TestStatus.ASSUMPTION_FAILURE) {
                CLog.logAndDisplay(LogLevel.WARN, testResult.getStackTrace());
            }
        }
    }

    /**
     * Runs tests of a given package on the device and reports success.
     * @param pkgName the name of the package containing tests
     * @param testClassName the class from which tests should be collected. Tests are collected
     * from all test classes in the package if null
     * @return true if at least once test runs and there are no failures
     * @throws AssertionError if device fails to run instrumentation tests
     * @throws AssumptionViolatedException if each device test fails an assumption
     * @throws DeviceNotAvailableException
     */
    protected boolean runDeviceTests(String pkgName, @Nullable String testClassName)
            throws DeviceNotAvailableException {
        return runDeviceTests(pkgName, testClassName, null /*testMethodName*/);
    }

    /**
     * Runs tests of a given package on the device and reports success.
     * @param pkgName the name of the package containing tests
     * @param testClassName the class from which tests should be collected. Tests are collected
     * from all test classes in the package if null
     * @param testMethodName the test method to run. All tests from the class or package are run
     * if null
     * @return true if at least once test runs and there are no failures
     * @throws AssertionError if device fails to run instrumentation tests
     * @throws AssumptionViolatedException if each device test fails an assumption
     * @throws DeviceNotAvailableException
     */
    protected boolean runDeviceTests(String pkgName, @Nullable String testClassName,
            @Nullable String testMethodName)
            throws DeviceNotAvailableException {
        TestRunResult runResult = doRunTests(pkgName, testClassName, testMethodName);
        printTestResult(runResult);
        // assume not all tests have skipped (and rethrow AssumptionViolatedException if so)
        Assume.assumeTrue(runResult.getNumTests() != runResult.getNumTestsInState(
                TestStatus.ASSUMPTION_FAILURE));
        return !runResult.hasFailedTests() && runResult.getNumTests() > 0;
    }

    /** Helper method to run tests and return the listener that collected the results. */
    private TestRunResult doRunTests(
        String pkgName, String testClassName,
        String testMethodName) throws DeviceNotAvailableException {
        RemoteAndroidTestRunner testRunner = new RemoteAndroidTestRunner(
            pkgName, mRunner, mDevice.getIDevice());
        if (testClassName != null && testMethodName != null) {
            testRunner.setMethodName(testClassName, testMethodName);
        } else if (testClassName != null) {
            testRunner.setClassName(testClassName);
        }

        CollectingTestListener listener = createCollectingListener();
        assertTrue(mDevice.runInstrumentationTests(testRunner, listener));
        return listener.getCurrentRunResults();
    }

    @VisibleForTesting
    protected CollectingTestListener createCollectingListener() {
        return new CollectingTestListener();
    }
}
