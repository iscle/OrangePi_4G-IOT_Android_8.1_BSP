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
package android.content.pm.cts.shortcuthost;

import com.android.compatibility.common.tradefed.build.CompatibilityBuildHelper;
import com.android.ddmlib.testrunner.RemoteAndroidTestRunner;
import com.android.ddmlib.testrunner.TestIdentifier;
import com.android.ddmlib.testrunner.TestResult;
import com.android.ddmlib.testrunner.TestResult.TestStatus;
import com.android.ddmlib.testrunner.TestRunResult;
import com.android.tradefed.build.IBuildInfo;
import com.android.tradefed.device.DeviceNotAvailableException;
import com.android.tradefed.log.LogUtil.CLog;
import com.android.tradefed.result.CollectingTestListener;
import com.android.tradefed.testtype.DeviceTestCase;
import com.android.tradefed.testtype.IBuildReceiver;

import java.io.FileNotFoundException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Map;
import java.util.regex.MatchResult;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.annotation.Nullable;

abstract public class BaseShortcutManagerHostTest extends DeviceTestCase implements IBuildReceiver {
    protected static final boolean DUMPSYS_IN_TEARDOWN = false; // DO NOT SUBMIT WITH TRUE

    private static final String RUNNER = "android.support.test.runner.AndroidJUnitRunner";

    private IBuildInfo mCtsBuild;

    protected boolean mIsMultiuserSupported;
    protected boolean mIsManagedUserSupported;

    private ArrayList<Integer> mOriginalUsers;

    @Override
    public void setBuild(IBuildInfo buildInfo) {
        mCtsBuild = buildInfo;
    }

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        assertNotNull(mCtsBuild);  // ensure build has been set before test is run.

        mIsMultiuserSupported = getDevice().isMultiUserSupported();
        if (!mIsMultiuserSupported) {
            CLog.w("Multi user not supporeted");
        }
        mIsManagedUserSupported = getDevice().hasFeature("android.software.managed_users");
        if (!mIsManagedUserSupported) {
            CLog.w("Managed users not supporeted");
        }

        if (mIsMultiuserSupported) {
            mOriginalUsers = new ArrayList<>(getDevice().listUsers());
        }
    }

    @Override
    protected void tearDown() throws Exception {
        removeTestUsers();
        super.tearDown();
    }

    protected void dumpsys(String label) throws DeviceNotAvailableException {
        CLog.w("dumpsys shortcuts #" + label);

        CLog.w(getDevice().executeShellCommand("dumpsys shortcut"));
    }

    protected String executeShellCommandWithLog(String command) throws DeviceNotAvailableException {
        CLog.i("Executing command: " + command);
        final String output = getDevice().executeShellCommand(command);
        CLog.i(output);
        return output;
    }

    protected void clearShortcuts(String packageName, int userId) throws Exception {
        assertContainsRegex("Success",
                getDevice().executeShellCommand("cmd shortcut clear-shortcuts --user " + userId
                        + " " + packageName));
    }

    protected void installAppAsUser(String appFileName, int userId) throws FileNotFoundException,
            DeviceNotAvailableException {
        CLog.i("Installing app " + appFileName + " for user " + userId);
        CompatibilityBuildHelper buildHelper = new CompatibilityBuildHelper(mCtsBuild);
        String result = getDevice().installPackageForUser(
                buildHelper.getTestFile(appFileName), true, true, userId, "-t");
        assertNull("Failed to install " + appFileName + " for user " + userId + ": " + result,
                result);
    }

    protected int getPrimaryUserId() throws DeviceNotAvailableException {
        return getDevice().getPrimaryUserId();
    }

    /** Returns true if the specified tests passed. Tests are run as given user. */
    protected void runDeviceTestsAsUser(
            String pkgName, @Nullable String testClassName, int userId)
            throws DeviceNotAvailableException {
        runDeviceTestsAsUser(pkgName, testClassName, null /*testMethodName*/, userId);
    }

    /** Returns true if the specified tests passed. Tests are run as given user. */
    protected void runDeviceTestsAsUser(
            String pkgName, @Nullable String testClassName, String testMethodName, int userId)
            throws DeviceNotAvailableException {
        Map<String, String> params = Collections.emptyMap();
        runDeviceTestsAsUser(pkgName, testClassName, testMethodName, userId, params);
    }

    protected void runDeviceTestsAsUser(String pkgName, @Nullable String testClassName,
            @Nullable String testMethodName, int userId,
            Map<String, String> params) throws DeviceNotAvailableException {
        if (testClassName != null && testClassName.startsWith(".")) {
            testClassName = pkgName + testClassName;
        }

        RemoteAndroidTestRunner testRunner = new RemoteAndroidTestRunner(
                pkgName, RUNNER, getDevice().getIDevice());
        if (testClassName != null && testMethodName != null) {
            testRunner.setMethodName(testClassName, testMethodName);
        } else if (testClassName != null) {
            testRunner.setClassName(testClassName);
        }

        for (Map.Entry<String, String> param : params.entrySet()) {
            testRunner.addInstrumentationArg(param.getKey(), param.getValue());
        }

        CollectingTestListener listener = new CollectingTestListener();
        assertTrue(getDevice().runInstrumentationTestsAsUser(testRunner, userId, listener));

        TestRunResult runResult = listener.getCurrentRunResults();
        if (runResult.getTestResults().size() == 0) {
            fail("No tests have been executed.");
            return;
        }

        printTestResult(runResult);
        if (runResult.hasFailedTests() || runResult.getNumTestsInState(TestStatus.PASSED) == 0) {
            fail("Some tests have been failed.");
        }
    }

    private void printTestResult(TestRunResult runResult) {
        for (Map.Entry<TestIdentifier, TestResult> testEntry :
                runResult.getTestResults().entrySet()) {
            TestResult testResult = testEntry.getValue();

            final String message = "Test " + testEntry.getKey() + ": " + testResult.getStatus();
            if (testResult.getStatus() == TestStatus.PASSED) {
                CLog.i(message);
            } else {
                CLog.e(message);
                CLog.e(testResult.getStackTrace());
            }
        }
    }

    private void removeTestUsers() throws Exception {
        if (!mIsMultiuserSupported) {
            return;
        }
        getDevice().switchUser(getPrimaryUserId());
        for (int userId : getDevice().listUsers()) {
            if (!mOriginalUsers.contains(userId)) {
                getDevice().removeUser(userId);
            }
        }
    }

    protected int createUser() throws Exception{
        return getDevice().createUser("TestUser_" + System.currentTimeMillis());
    }

    protected int createProfile(int parentUserId) throws Exception{
        final String command = "pm create-user --profileOf " + parentUserId
                + " --managed TestUser_" + System.currentTimeMillis();
        CLog.d("Starting command: " + command);
        final String output = getDevice().executeShellCommand(command);
        CLog.d("Output for command " + command + ": " + output);

        if (output.startsWith("Success")) {
            try {
                return Integer.parseInt(output.substring(output.lastIndexOf(" ")).trim());
            } catch (NumberFormatException e) {
                CLog.e("Failed to parse result: %s", output);
            }
        } else {
            CLog.e("Failed to create user: %s", output);
        }
        throw new IllegalStateException();
    }

    /**
     * Variant of {@link #assertContainsRegex(String,String,String)} using a
     * generic message.
     */
    public MatchResult assertContainsRegex(
            String expectedRegex, String actual) {
        return assertContainsRegex(null, expectedRegex, actual);
    }

    /**
     * Asserts that {@code expectedRegex} matches any substring of {@code actual}
     * and fails with {@code message} if it does not.  The Matcher is returned in
     * case the test needs access to any captured groups.  Note that you can also
     * use this for a literal string, by wrapping your expected string in
     * {@link Pattern#quote}.
     */
    public MatchResult assertContainsRegex(
            String message, String expectedRegex, String actual) {
        if (actual == null) {
            failNotContains(message, expectedRegex, actual);
        }
        Matcher matcher = getMatcher(expectedRegex, actual);
        if (!matcher.find()) {
            failNotContains(message, expectedRegex, actual);
        }
        return matcher;
    }

    /**
     * Asserts that {@code expectedRegex} does not exactly match {@code actual},
     * and fails with {@code message} if it does. Note that you can also use
     * this for a literal string, by wrapping your expected string in
     * {@link Pattern#quote}.
     */
    public void assertNotMatchesRegex(
            String message, String expectedRegex, String actual) {
        Matcher matcher = getMatcher(expectedRegex, actual);
        if (matcher.matches()) {
            failMatch(message, expectedRegex, actual);
        }
    }

    private Matcher getMatcher(String expectedRegex, String actual) {
        Pattern pattern = Pattern.compile(expectedRegex);
        return pattern.matcher(actual);
    }

    private void failMatch(
            String message, String expectedRegex, String actual) {
        failWithMessage(message, "expected not to match regex:<" + expectedRegex
                + "> but was:<" + actual + '>');
    }

    private void failWithMessage(String userMessage, String ourMessage) {
        fail((userMessage == null)
                ? ourMessage
                : userMessage + ' ' + ourMessage);
    }

    private void failNotContains(
            String message, String expectedRegex, String actual) {
        String actualDesc = (actual == null) ? "null" : ('<' + actual + '>');
        failWithMessage(message, "expected to contain regex:<" + expectedRegex
                + "> but was:" + actualDesc);
    }
}
