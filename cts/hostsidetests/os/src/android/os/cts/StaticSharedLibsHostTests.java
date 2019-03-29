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

package android.os.cts;

import com.android.compatibility.common.tradefed.build.CompatibilityBuildHelper;
import com.android.ddmlib.testrunner.RemoteAndroidTestRunner;
import com.android.ddmlib.testrunner.TestIdentifier;
import com.android.ddmlib.testrunner.TestResult;
import com.android.ddmlib.testrunner.TestRunResult;
import com.android.tradefed.build.IBuildInfo;
import com.android.tradefed.device.DeviceNotAvailableException;
import com.android.tradefed.result.CollectingTestListener;
import com.android.tradefed.testtype.DeviceTestCase;
import com.android.tradefed.testtype.IBuildReceiver;

import java.util.Map;

public class StaticSharedLibsHostTests extends DeviceTestCase implements IBuildReceiver {
    private static final String ANDROID_JUNIT_RUNNER_CLASS =
            "android.support.test.runner.AndroidJUnitRunner";

    private static final String STATIC_LIB_PROVIDER_RECURSIVE_APK =
            "CtsStaticSharedLibProviderRecursive.apk";
    private static final String STATIC_LIB_PROVIDER_RECURSIVE_PKG =
            "android.os.lib.provider.recursive";

    private static final String STATIC_LIB_PROVIDER1_APK = "CtsStaticSharedLibProviderApp1.apk";
    private static final String STATIC_LIB_PROVIDER1_PKG = "android.os.lib.provider";

    private static final String STATIC_LIB_PROVIDER2_APK = "CtsStaticSharedLibProviderApp2.apk";
    private static final String STATIC_LIB_PROVIDER2_PKG = "android.os.lib.provider";

    private static final String STATIC_LIB_PROVIDER3_APK = "CtsStaticSharedLibProviderApp3.apk";
    private static final String STATIC_LIB_PROVIDER3_PKG = "android.os.lib.provider";

    private static final String STATIC_LIB_PROVIDER4_APK = "CtsStaticSharedLibProviderApp4.apk";
    private static final String STATIC_LIB_PROVIDER4_PKG = "android.os.lib.provider";

    private static final String STATIC_LIB_PROVIDER5_APK = "CtsStaticSharedLibProviderApp5.apk";
    private static final String STATIC_LIB_PROVIDER5_PKG = "android.os.lib.provider";

    private static final String STATIC_LIB_PROVIDER6_APK = "CtsStaticSharedLibProviderApp6.apk";
    private static final String STATIC_LIB_PROVIDER6_PKG = "android.os.lib.provider";

    private static final String STATIC_LIB_PROVIDER7_APK = "CtsStaticSharedLibProviderApp7.apk";
    private static final String STATIC_LIB_PROVIDER7_PKG = "android.os.lib.provider";

    private static final String STATIC_LIB_NATIVE_PROVIDER_APK =
            "CtsStaticSharedNativeLibProvider.apk";
    private static final String STATIC_LIB_NATIVE_PROVIDER_PKG =
            "android.os.lib.provider";

    private static final String STATIC_LIB_NATIVE_PROVIDER_APK1 =
            "CtsStaticSharedNativeLibProvider1.apk";
    private static final String STATIC_LIB_NATIVE_PROVIDER_PKG1 =
            "android.os.lib.provider";

    private static final String STATIC_LIB_CONSUMER1_APK = "CtsStaticSharedLibConsumerApp1.apk";
    private static final String STATIC_LIB_CONSUMER1_PKG = "android.os.lib.consumer1";

    private static final String STATIC_LIB_CONSUMER2_APK = "CtsStaticSharedLibConsumerApp2.apk";
    private static final String STATIC_LIB_CONSUMER2_PKG = "android.os.lib.consumer2";

    private static final String STATIC_LIB_CONSUMER3_APK = "CtsStaticSharedLibConsumerApp3.apk";
    private static final String STATIC_LIB_CONSUMER3_PKG = "android.os.lib.consumer3";

    private static final String STATIC_LIB_NATIVE_CONSUMER_APK
            = "CtsStaticSharedNativeLibConsumer.apk";
    private static final String STATIC_LIB_NATIVE_CONSUMER_PKG
            = "android.os.lib.consumer";

    private CompatibilityBuildHelper mBuildHelper;

    @Override
    public void setBuild(IBuildInfo buildInfo) {
        mBuildHelper = new CompatibilityBuildHelper(buildInfo);
    }

    public void testInstallSharedLibrary() throws Exception {
        getDevice().uninstallPackage(STATIC_LIB_PROVIDER1_PKG);
        getDevice().uninstallPackage(STATIC_LIB_PROVIDER2_PKG);
        getDevice().uninstallPackage(STATIC_LIB_PROVIDER_RECURSIVE_PKG);
        try {
            // Install library dependency
            assertNull(getDevice().installPackage(mBuildHelper.getTestFile(
                    STATIC_LIB_PROVIDER_RECURSIVE_APK), false, false));
            // Install version 1
            assertNull(getDevice().installPackage(mBuildHelper.getTestFile(
                    STATIC_LIB_PROVIDER1_APK), false, false));
            // Install version 2
            assertNull(getDevice().installPackage(mBuildHelper.getTestFile(
                    STATIC_LIB_PROVIDER2_APK), false, false));
            // Uninstall version 1
            assertNull(getDevice().uninstallPackage(STATIC_LIB_PROVIDER1_PKG));
            // Uninstall version 2
            assertNull(getDevice().uninstallPackage(STATIC_LIB_PROVIDER2_PKG));
            // Uninstall dependency
            assertNull(getDevice().uninstallPackage(STATIC_LIB_PROVIDER_RECURSIVE_PKG));
        } finally {
            getDevice().uninstallPackage(STATIC_LIB_PROVIDER1_PKG);
            getDevice().uninstallPackage(STATIC_LIB_PROVIDER2_PKG);
            getDevice().uninstallPackage(STATIC_LIB_PROVIDER_RECURSIVE_PKG);
        }
    }

    public void testCannotInstallSharedLibraryWithMissingDependency() throws Exception {
        getDevice().uninstallPackage(STATIC_LIB_PROVIDER1_PKG);
        getDevice().uninstallPackage(STATIC_LIB_PROVIDER2_PKG);
        try {
            // Install version 1 - should fail - no dependency
            assertNotNull(getDevice().installPackage(mBuildHelper.getTestFile(
                    STATIC_LIB_PROVIDER1_APK), false, false));
        } finally {
            getDevice().uninstallPackage(STATIC_LIB_PROVIDER1_PKG);
            getDevice().uninstallPackage(STATIC_LIB_PROVIDER2_PKG);
        }
    }

    public void testLoadCodeAndResourcesFromSharedLibraryRecursively() throws Exception {
        getDevice().uninstallPackage(STATIC_LIB_CONSUMER1_PKG);
        getDevice().uninstallPackage(STATIC_LIB_PROVIDER1_PKG);
        getDevice().uninstallPackage(STATIC_LIB_PROVIDER_RECURSIVE_PKG);
        try {
            // Install library dependency
            assertNull(getDevice().installPackage(mBuildHelper.getTestFile(
                    STATIC_LIB_PROVIDER_RECURSIVE_APK), false, false));
            // Install the library
            assertNull(getDevice().installPackage(mBuildHelper.getTestFile(
                    STATIC_LIB_PROVIDER1_APK), false, false));
            // Install the client
            assertNull(getDevice().installPackage(mBuildHelper.getTestFile(
                    STATIC_LIB_CONSUMER1_APK), false, false));
            // Try to load code and resources
            runDeviceTests(STATIC_LIB_CONSUMER1_PKG,
                    "android.os.lib.consumer1.UseSharedLibraryTest",
                    "testLoadCodeAndResources");
        } finally {
            getDevice().uninstallPackage(STATIC_LIB_CONSUMER1_PKG);
            getDevice().uninstallPackage(STATIC_LIB_PROVIDER1_PKG);
            getDevice().uninstallPackage(STATIC_LIB_PROVIDER_RECURSIVE_PKG);
        }
    }

    public void testCannotUninstallUsedSharedLibrary1() throws Exception {
        getDevice().uninstallPackage(STATIC_LIB_PROVIDER1_PKG);
        getDevice().uninstallPackage(STATIC_LIB_PROVIDER_RECURSIVE_PKG);
        try {
            // Install library dependency
            assertNull(getDevice().installPackage(mBuildHelper.getTestFile(
                    STATIC_LIB_PROVIDER_RECURSIVE_APK), false, false));
            // Install the library
            assertNull(getDevice().installPackage(mBuildHelper.getTestFile(
                    STATIC_LIB_PROVIDER1_APK), false, false));
            // The library dependency cannot be uninstalled
            assertNotNull(getDevice().uninstallPackage(STATIC_LIB_PROVIDER_RECURSIVE_PKG));
            // Now the library dependency can be uninstalled
            assertNull(getDevice().uninstallPackage(STATIC_LIB_PROVIDER1_PKG));
            // Uninstall dependency
            assertNull(getDevice().uninstallPackage(STATIC_LIB_PROVIDER_RECURSIVE_PKG));
        } finally {
            getDevice().uninstallPackage(STATIC_LIB_PROVIDER1_PKG);
            getDevice().uninstallPackage(STATIC_LIB_PROVIDER_RECURSIVE_PKG);
        }
    }

    public void testCannotUninstallUsedSharedLibrary2() throws Exception {
        getDevice().uninstallPackage(STATIC_LIB_CONSUMER1_PKG);
        getDevice().uninstallPackage(STATIC_LIB_PROVIDER1_PKG);
        getDevice().uninstallPackage(STATIC_LIB_PROVIDER_RECURSIVE_PKG);
        try {
            // Install library dependency
            assertNull(getDevice().installPackage(mBuildHelper.getTestFile(
                    STATIC_LIB_PROVIDER_RECURSIVE_APK), false, false));
            // Install the library
            assertNull(getDevice().installPackage(mBuildHelper.getTestFile(
                    STATIC_LIB_PROVIDER1_APK), false, false));
            // Install the client
            assertNull(getDevice().installPackage(mBuildHelper.getTestFile(
                    STATIC_LIB_CONSUMER1_APK), false, false));
            // The library cannot be uninstalled
            assertNotNull(getDevice().uninstallPackage(STATIC_LIB_PROVIDER1_PKG));
            // Uninstall the client
            assertNull(getDevice().uninstallPackage(STATIC_LIB_CONSUMER1_PKG));
            // Now the library can be uninstalled
            assertNull(getDevice().uninstallPackage(STATIC_LIB_PROVIDER1_PKG));
            // Uninstall dependency
            assertNull(getDevice().uninstallPackage(STATIC_LIB_PROVIDER_RECURSIVE_PKG));
        } finally {
            getDevice().uninstallPackage(STATIC_LIB_CONSUMER1_PKG);
            getDevice().uninstallPackage(STATIC_LIB_PROVIDER1_PKG);
            getDevice().uninstallPackage(STATIC_LIB_PROVIDER_RECURSIVE_PKG);
        }
    }

    public void testLibraryVersionsAndVersionCodesSameOrder() throws Exception {
        getDevice().uninstallPackage(STATIC_LIB_PROVIDER1_PKG);
        getDevice().uninstallPackage(STATIC_LIB_PROVIDER2_PKG);
        getDevice().uninstallPackage(STATIC_LIB_PROVIDER3_PKG);
        getDevice().uninstallPackage(STATIC_LIB_PROVIDER_RECURSIVE_PKG);
        try {
            // Install library dependency
            assertNull(getDevice().installPackage(mBuildHelper.getTestFile(
                    STATIC_LIB_PROVIDER_RECURSIVE_APK), false, false));
            // Install library version 1 with version code 1
            assertNull(getDevice().installPackage(mBuildHelper.getTestFile(
                    STATIC_LIB_PROVIDER1_APK), false, false));
            // Install library version 2 with version code 4
            assertNull(getDevice().installPackage(mBuildHelper.getTestFile(
                    STATIC_LIB_PROVIDER2_APK), false, false));
            // Shouldn't be able to install library version 3 with version code 3
            assertNotNull(getDevice().installPackage(mBuildHelper.getTestFile(
                    STATIC_LIB_PROVIDER3_APK), false, false));
        } finally {
            getDevice().uninstallPackage(STATIC_LIB_PROVIDER1_PKG);
            getDevice().uninstallPackage(STATIC_LIB_PROVIDER2_PKG);
            getDevice().uninstallPackage(STATIC_LIB_PROVIDER3_PKG);
            getDevice().uninstallPackage(STATIC_LIB_PROVIDER_RECURSIVE_PKG);
        }
    }

    public void testCannotInstallAppWithMissingLibrary() throws Exception {
        getDevice().uninstallPackage(STATIC_LIB_CONSUMER1_PKG);
        try {
            // Shouldn't be able to install an app if a dependency lib is missing
            assertNotNull(getDevice().installPackage(mBuildHelper.getTestFile(
                    STATIC_LIB_CONSUMER1_APK), false, false));
        } finally {
            getDevice().uninstallPackage(STATIC_LIB_CONSUMER1_PKG);
        }
    }

    public void testCanReplaceLibraryIfVersionAndVersionCodeSame() throws Exception {
        getDevice().uninstallPackage(STATIC_LIB_PROVIDER1_PKG);
        getDevice().uninstallPackage(STATIC_LIB_PROVIDER_RECURSIVE_PKG);
        try {
            // Install library dependency
            assertNull(getDevice().installPackage(mBuildHelper.getTestFile(
                    STATIC_LIB_PROVIDER_RECURSIVE_APK), false, false));
            // Install a library
            assertNull(getDevice().installPackage(mBuildHelper.getTestFile(
                    STATIC_LIB_PROVIDER1_APK), false, false));
            // Cannot install the library (need to reinstall)
            assertNotNull(getDevice().installPackage(mBuildHelper.getTestFile(
                    STATIC_LIB_PROVIDER1_APK), false, false));
            // Can reinstall the library if version and version code same
            assertNull(getDevice().installPackage(mBuildHelper.getTestFile(
                    STATIC_LIB_PROVIDER1_APK), true, false));
        } finally {
            getDevice().uninstallPackage(STATIC_LIB_PROVIDER1_PKG);
            getDevice().uninstallPackage(STATIC_LIB_PROVIDER_RECURSIVE_PKG);
        }
    }

    public void testUninstallSpecificLibraryVersion() throws Exception {
        getDevice().uninstallPackage(STATIC_LIB_PROVIDER1_PKG);
        getDevice().uninstallPackage(STATIC_LIB_PROVIDER_RECURSIVE_PKG);
        try {
            // Install library dependency
            assertNull(getDevice().installPackage(mBuildHelper.getTestFile(
                    STATIC_LIB_PROVIDER_RECURSIVE_APK), false, false));
            // Install library version 1 with version code 1
            assertNull(getDevice().installPackage(mBuildHelper.getTestFile(
                    STATIC_LIB_PROVIDER1_APK), false, false));
            // Install library version 2 with version code 4
            assertNull(getDevice().installPackage(mBuildHelper.getTestFile(
                    STATIC_LIB_PROVIDER2_APK), false, false));
            // Uninstall the library package with version code 4 (version 2)
            assertTrue(getDevice().executeShellCommand("pm uninstall --versionCode 4 "
                    + STATIC_LIB_PROVIDER1_PKG).startsWith("Success"));
            // Uninstall the library package with version code 1 (version 1)
            assertTrue(getDevice().executeShellCommand("pm uninstall "
                    + STATIC_LIB_PROVIDER1_PKG).startsWith("Success"));
        } finally {
            getDevice().uninstallPackage(STATIC_LIB_PROVIDER1_PKG);
            getDevice().uninstallPackage(STATIC_LIB_PROVIDER_RECURSIVE_PKG);
        }
    }

    public void testKeyRotation() throws Exception {
        getDevice().uninstallPackage(STATIC_LIB_CONSUMER2_PKG);
        getDevice().uninstallPackage(STATIC_LIB_PROVIDER2_PKG);
        getDevice().uninstallPackage(STATIC_LIB_PROVIDER4_PKG);
        try {
            // Install a library version specifying an upgrade key set
            assertNull(getDevice().installPackage(mBuildHelper.getTestFile(
                    STATIC_LIB_PROVIDER2_APK), false, false));
            // Install a newer library signed with the upgrade key set
            assertNull(getDevice().installPackage(mBuildHelper.getTestFile(
                    STATIC_LIB_PROVIDER4_APK), false, false));
            // Install a client that depends on the upgraded key set
            assertNull(getDevice().installPackage(mBuildHelper.getTestFile(
                    STATIC_LIB_CONSUMER2_APK), false, false));
            // Ensure code and resources can be loaded
            runDeviceTests(STATIC_LIB_CONSUMER2_PKG,
                    "android.os.lib.consumer2.UseSharedLibraryTest",
                    "testLoadCodeAndResources");
        } finally {
            getDevice().uninstallPackage(STATIC_LIB_CONSUMER2_PKG);
            getDevice().uninstallPackage(STATIC_LIB_PROVIDER2_PKG);
            getDevice().uninstallPackage(STATIC_LIB_PROVIDER4_PKG);
        }
    }

    public void testCannotInstallIncorrectlySignedLibrary() throws Exception {
        getDevice().uninstallPackage(STATIC_LIB_PROVIDER1_PKG);
        getDevice().uninstallPackage(STATIC_LIB_PROVIDER4_PKG);
        getDevice().uninstallPackage(STATIC_LIB_PROVIDER_RECURSIVE_PKG);
        try {
            // Install library dependency
            assertNull(getDevice().installPackage(mBuildHelper.getTestFile(
                    STATIC_LIB_PROVIDER_RECURSIVE_APK), false, false));
            // Install a library version not specifying an upgrade key set
            assertNull(getDevice().installPackage(mBuildHelper.getTestFile(
                    STATIC_LIB_PROVIDER1_APK), false, false));
            // Shouldn't be able to install a newer version signed differently
            assertNotNull(getDevice().installPackage(mBuildHelper.getTestFile(
                    STATIC_LIB_PROVIDER4_APK), false, false));
        } finally {
            getDevice().uninstallPackage(STATIC_LIB_PROVIDER1_PKG);
            getDevice().uninstallPackage(STATIC_LIB_PROVIDER4_PKG);
            getDevice().uninstallPackage(STATIC_LIB_PROVIDER_RECURSIVE_PKG);
        }
    }

    public void testLibraryAndPackageNameCanMatch() throws Exception {
        getDevice().uninstallPackage(STATIC_LIB_PROVIDER5_PKG);
        getDevice().uninstallPackage(STATIC_LIB_PROVIDER6_PKG);
        try {
            // Install a library with same name as package should work.
            assertNull(getDevice().installPackage(mBuildHelper.getTestFile(
                    STATIC_LIB_PROVIDER5_APK), false, false));
            // Install a library with same name as package should work.
            assertNull(getDevice().installPackage(mBuildHelper.getTestFile(
                    STATIC_LIB_PROVIDER6_APK), true, false));
        } finally {
            getDevice().uninstallPackage(STATIC_LIB_PROVIDER5_PKG);
            getDevice().uninstallPackage(STATIC_LIB_PROVIDER6_PKG);
        }
    }

    public void testGetSharedLibraries() throws Exception {
        getDevice().uninstallPackage(STATIC_LIB_CONSUMER1_PKG);
        getDevice().uninstallPackage(STATIC_LIB_CONSUMER2_PKG);
        getDevice().uninstallPackage(STATIC_LIB_PROVIDER1_PKG);
        getDevice().uninstallPackage(STATIC_LIB_PROVIDER2_PKG);
        getDevice().uninstallPackage(STATIC_LIB_PROVIDER4_PKG);
        getDevice().uninstallPackage(STATIC_LIB_PROVIDER_RECURSIVE_PKG);
        try {
            // Install library dependency
            assertNull(getDevice().installPackage(mBuildHelper.getTestFile(
                    STATIC_LIB_PROVIDER_RECURSIVE_APK), false, false));
            // Install the first library
            assertNull(getDevice().installPackage(mBuildHelper.getTestFile(
                    STATIC_LIB_PROVIDER1_APK), false, false));
            // Install the second library
            assertNull(getDevice().installPackage(mBuildHelper.getTestFile(
                    STATIC_LIB_PROVIDER2_APK), false, false));
            // Install the third library
            assertNull(getDevice().installPackage(mBuildHelper.getTestFile(
                    STATIC_LIB_PROVIDER4_APK), false, false));
            // Install the first client
            assertNull(getDevice().installPackage(mBuildHelper.getTestFile(
                    STATIC_LIB_CONSUMER1_APK), false, false));
            // Install the second client
            assertNull(getDevice().installPackage(mBuildHelper.getTestFile(
                    STATIC_LIB_CONSUMER2_APK), false, false));
            // Ensure libraries are properly reported
            runDeviceTests(STATIC_LIB_CONSUMER1_PKG,
                    "android.os.lib.consumer1.UseSharedLibraryTest",
                    "testSharedLibrariesProperlyReported");
        } finally {
            getDevice().uninstallPackage(STATIC_LIB_CONSUMER1_PKG);
            getDevice().uninstallPackage(STATIC_LIB_CONSUMER2_PKG);
            getDevice().uninstallPackage(STATIC_LIB_PROVIDER1_PKG);
            getDevice().uninstallPackage(STATIC_LIB_PROVIDER2_PKG);
            getDevice().uninstallPackage(STATIC_LIB_PROVIDER4_PKG);
            getDevice().uninstallPackage(STATIC_LIB_PROVIDER_RECURSIVE_PKG);
        }
    }

    public void testAppCanSeeOnlyLibrariesItDependOn() throws Exception {
        getDevice().uninstallPackage(STATIC_LIB_CONSUMER1_PKG);
        getDevice().uninstallPackage(STATIC_LIB_PROVIDER1_PKG);
        getDevice().uninstallPackage(STATIC_LIB_PROVIDER2_PKG);
        getDevice().uninstallPackage(STATIC_LIB_PROVIDER_RECURSIVE_PKG);
        try {
            // Install library dependency
            assertNull(getDevice().installPackage(mBuildHelper.getTestFile(
                    STATIC_LIB_PROVIDER_RECURSIVE_APK), false, false));
            // Install the first library
            assertNull(getDevice().installPackage(mBuildHelper.getTestFile(
                    STATIC_LIB_PROVIDER1_APK), false, false));
            // Install the second library
            assertNull(getDevice().installPackage(mBuildHelper.getTestFile(
                    STATIC_LIB_PROVIDER2_APK), false, false));
            // Install the client
            assertNull(getDevice().installPackage(mBuildHelper.getTestFile(
                    STATIC_LIB_CONSUMER1_APK), false, false));
            // Ensure the client can see only the lib it depends on
            runDeviceTests(STATIC_LIB_CONSUMER1_PKG,
                    "android.os.lib.consumer1.UseSharedLibraryTest",
                    "testAppCanSeeOnlyLibrariesItDependOn");
        } finally {
            getDevice().uninstallPackage(STATIC_LIB_CONSUMER1_PKG);
            getDevice().uninstallPackage(STATIC_LIB_PROVIDER1_PKG);
            getDevice().uninstallPackage(STATIC_LIB_PROVIDER2_PKG);
            getDevice().uninstallPackage(STATIC_LIB_PROVIDER_RECURSIVE_PKG);
        }
    }

    public void testLoadCodeFromNativeLib() throws Exception {
        getDevice().uninstallPackage(STATIC_LIB_NATIVE_CONSUMER_PKG);
        getDevice().uninstallPackage(STATIC_LIB_NATIVE_PROVIDER_PKG);
        try {
            // Install library
            assertNull(getDevice().installPackage(mBuildHelper.getTestFile(
                    STATIC_LIB_NATIVE_PROVIDER_APK), false, false));
            // Install the library client
            assertNull(getDevice().installPackage(mBuildHelper.getTestFile(
                    STATIC_LIB_NATIVE_CONSUMER_APK), false, false));
            // Ensure the client can load native code from the library
            runDeviceTests(STATIC_LIB_NATIVE_CONSUMER_PKG,
                    "android.os.lib.consumer.UseSharedLibraryTest",
                    "testLoadNativeCode");
        } finally {
            getDevice().uninstallPackage(STATIC_LIB_NATIVE_CONSUMER_PKG);
            getDevice().uninstallPackage(STATIC_LIB_NATIVE_PROVIDER_PKG);
        }
    }

    public void testLoadCodeFromNativeLibMultiArchViolation() throws Exception {
        getDevice().uninstallPackage(STATIC_LIB_NATIVE_PROVIDER_PKG1);
        try {
            // Cannot install the library with native code if not multi-arch
            assertNotNull(getDevice().installPackage(mBuildHelper.getTestFile(
                    STATIC_LIB_NATIVE_PROVIDER_APK1), false, false));
        } finally {
            getDevice().uninstallPackage(STATIC_LIB_NATIVE_PROVIDER_PKG1);
        }
    }

    public void testLoadCodeAndResourcesFromSharedLibrarySignedWithTwoCerts()
            throws Exception {
        getDevice().uninstallPackage(STATIC_LIB_CONSUMER3_PKG);
        getDevice().uninstallPackage(STATIC_LIB_PROVIDER7_PKG);
        try {
            // Install the library
            assertNull(getDevice().installPackage(mBuildHelper.getTestFile(
                    STATIC_LIB_PROVIDER7_APK), false, false));
            // Install the client
            assertNull(getDevice().installPackage(mBuildHelper.getTestFile(
                    STATIC_LIB_CONSUMER3_APK), false, false));
            // Try to load code and resources
            runDeviceTests(STATIC_LIB_CONSUMER3_PKG,
                    "android.os.lib.consumer3.UseSharedLibraryTest",
                    "testLoadCodeAndResources");
        } finally {
            getDevice().uninstallPackage(STATIC_LIB_CONSUMER3_PKG);
            getDevice().uninstallPackage(STATIC_LIB_PROVIDER7_PKG);
        }
    }

    private void runDeviceTests(String packageName, String testClassName,
            String testMethodName) throws DeviceNotAvailableException {
        RemoteAndroidTestRunner testRunner = new RemoteAndroidTestRunner(packageName,
                ANDROID_JUNIT_RUNNER_CLASS, getDevice().getIDevice());
        testRunner.setMethodName(testClassName, testMethodName);
        CollectingTestListener listener = new CollectingTestListener();

        getDevice().runInstrumentationTests(testRunner, listener);

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
                if (!resultEntry.getValue().getStatus().equals(TestResult.TestStatus.PASSED)) {
                    errorBuilder.append(resultEntry.getKey().toString());
                    errorBuilder.append(":\n");
                    errorBuilder.append(resultEntry.getValue().getStackTrace());
                }
            }
            throw new AssertionError(errorBuilder.toString());
        }
    }
}
