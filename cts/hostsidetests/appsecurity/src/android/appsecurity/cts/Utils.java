/*
 * Copyright (C) 2014 The Android Open Source Project
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

package android.appsecurity.cts;

import com.android.ddmlib.testrunner.RemoteAndroidTestRunner;
import com.android.ddmlib.testrunner.TestIdentifier;
import com.android.ddmlib.testrunner.TestResult;
import com.android.ddmlib.testrunner.TestResult.TestStatus;
import com.android.ddmlib.testrunner.TestRunResult;
import com.android.tradefed.device.DeviceNotAvailableException;
import com.android.tradefed.device.ITestDevice;
import com.android.tradefed.result.CollectingTestListener;

import java.util.Arrays;
import java.util.Map;
import java.util.concurrent.TimeUnit;

public class Utils {
    public static final int USER_SYSTEM = 0;

    public static void runDeviceTests(ITestDevice device, String packageName)
            throws DeviceNotAvailableException {
        runDeviceTests(device, packageName, null, null, USER_SYSTEM, null);
    }

    public static void runDeviceTests(ITestDevice device, String packageName, int userId)
            throws DeviceNotAvailableException {
        runDeviceTests(device, packageName, null, null, userId, null);
    }

    public static void runDeviceTests(ITestDevice device, String packageName, String testClassName)
            throws DeviceNotAvailableException {
        runDeviceTests(device, packageName, testClassName, null, USER_SYSTEM, null);
    }

    public static void runDeviceTests(ITestDevice device, String packageName, String testClassName,
            int userId) throws DeviceNotAvailableException {
        runDeviceTests(device, packageName, testClassName, null, userId, null);
    }

    public static void runDeviceTests(ITestDevice device, String packageName, String testClassName,
            String testMethodName) throws DeviceNotAvailableException {
        runDeviceTests(device, packageName, testClassName, testMethodName, USER_SYSTEM, null);
    }

    public static void runDeviceTests(ITestDevice device, String packageName, String testClassName,
            String testMethodName, Map<String, String> testArgs)
                    throws DeviceNotAvailableException {
        runDeviceTests(device, packageName, testClassName, testMethodName, USER_SYSTEM, testArgs);
    }

    public static void runDeviceTests(ITestDevice device, String packageName, String testClassName,
            String testMethodName, int userId) throws DeviceNotAvailableException {
        runDeviceTests(device, packageName, testClassName, testMethodName, userId, null);
    }

    public static void runDeviceTests(ITestDevice device, String packageName, String testClassName,
            String testMethodName, int userId, Map<String, String> testArgs)
                    throws DeviceNotAvailableException {
        // 60 min timeout per test by default
        runDeviceTests(device, packageName, testClassName, testMethodName, userId, testArgs,
                60L, TimeUnit.MINUTES);
    }

    public static void runDeviceTests(ITestDevice device, String packageName, String testClassName,
            String testMethodName, int userId, Map<String, String> testArgs, long timeout,
            TimeUnit unit)
                    throws DeviceNotAvailableException {
        if (testClassName != null && testClassName.startsWith(".")) {
            testClassName = packageName + testClassName;
        }
        RemoteAndroidTestRunner testRunner = new RemoteAndroidTestRunner(packageName,
                "android.support.test.runner.AndroidJUnitRunner", device.getIDevice());
        // timeout_msec is the timeout per test for instrumentation
        testRunner.addInstrumentationArg("timeout_msec", Long.toString(unit.toMillis(timeout)));
        if (testClassName != null && testMethodName != null) {
            testRunner.setMethodName(testClassName, testMethodName);
        } else if (testClassName != null) {
            testRunner.setClassName(testClassName);
        }

        if (testArgs != null && testArgs.size() > 0) {
            for (String name : testArgs.keySet()) {
                final String value = testArgs.get(name);
                testRunner.addInstrumentationArg(name, value);
            }
        }
        final CollectingTestListener listener = new CollectingTestListener();
        device.runInstrumentationTestsAsUser(testRunner, userId, listener);

        final TestRunResult result = listener.getCurrentRunResults();
        if (result.isRunFailure()) {
            throw new AssertionError("Failed to successfully run device tests for "
                    + result.getName() + ": " + result.getRunFailureMessage());
        }
        if (result.getNumTests() == 0) {
            throw new AssertionError("No tests were run on the device");
        }
        if (result.hasFailedTests()) {
            // build a meaningful error message
            StringBuilder errorBuilder = new StringBuilder("on-device tests failed:\n");
            for (Map.Entry<TestIdentifier, TestResult> resultEntry :
                result.getTestResults().entrySet()) {
                if (!resultEntry.getValue().getStatus().equals(TestStatus.PASSED)) {
                    errorBuilder.append(resultEntry.getKey().toString());
                    errorBuilder.append(":\n");
                    errorBuilder.append(resultEntry.getValue().getStackTrace());
                }
            }
            throw new AssertionError(errorBuilder.toString());
        }
    }

    /**
     * Prepare and return a single user relevant for testing.
     */
    public static int[] prepareSingleUser(ITestDevice device)
            throws DeviceNotAvailableException {
        return prepareMultipleUsers(device, 1);
    }

    /**
     * Prepare and return two users relevant for testing.
     */
    public static int[] prepareMultipleUsers(ITestDevice device)
            throws DeviceNotAvailableException {
        return prepareMultipleUsers(device, 2);
    }

    /**
     * Prepare and return multiple users relevant for testing.
     */
    public static int[] prepareMultipleUsers(ITestDevice device, int maxUsers)
            throws DeviceNotAvailableException {
        final int[] userIds = getAllUsers(device);
        for (int i = 1; i < userIds.length; i++) {
            if (i < maxUsers) {
                device.startUser(userIds[i]);
            } else {
                device.stopUser(userIds[i]);
            }
        }
        if (userIds.length > maxUsers) {
            return Arrays.copyOf(userIds, maxUsers);
        } else {
            return userIds;
        }
    }

    public static int[] getAllUsers(ITestDevice device)
            throws DeviceNotAvailableException {
        Integer primary = device.getPrimaryUserId();
        if (primary == null) {
            primary = USER_SYSTEM;
        }
        int[] users = new int[] { primary };
        for (Integer user : device.listUsers()) {
            if ((user != USER_SYSTEM) && (user != primary)) {
                users = Arrays.copyOf(users, users.length + 1);
                users[users.length - 1] = user;
            }
        }
        return users;
    }
}
