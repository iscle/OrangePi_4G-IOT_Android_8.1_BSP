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

package android.dumpsys.cts;

import com.android.compatibility.common.tradefed.build.CompatibilityBuildHelper;
import com.android.ddmlib.testrunner.RemoteAndroidTestRunner;
import com.android.ddmlib.testrunner.TestIdentifier;
import com.android.ddmlib.testrunner.TestResult;
import com.android.ddmlib.testrunner.TestResult.TestStatus;
import com.android.ddmlib.testrunner.TestRunResult;
import com.android.tradefed.device.DeviceNotAvailableException;
import com.android.tradefed.result.CollectingTestListener;

import java.io.BufferedReader;
import java.io.StringReader;
import java.util.Map;

/**
 * Test to check the format of the dumps of the processstats test.
 */
public class StoragedDumpsysTest extends BaseDumpsysTest {
    private static final String DEVICE_SIDE_TEST_APK = "CtsStoragedTestApp.apk";
    private static final String DEVICE_SIDE_TEST_PACKAGE = "com.android.server.cts.storaged";

    @Override
    protected void tearDown() throws Exception {
        super.tearDown();
        getDevice().uninstallPackage(DEVICE_SIDE_TEST_PACKAGE);
    }

    /**
     * Tests the output of "dumpsys storaged --force --hours 0.01".
     *
     * @throws Exception
     */
    public void testStoragedOutput() throws Exception {
        String result = mDevice.executeShellCommand("stat /proc/uid_io/stats");
        if(result.contains("No such file or directory")) {
            return;
        }

        if (mDevice.getAppPackageInfo(DEVICE_SIDE_TEST_APK) != null) {
            getDevice().uninstallPackage(DEVICE_SIDE_TEST_PACKAGE);
        }

        CompatibilityBuildHelper buildHelper = new CompatibilityBuildHelper(mCtsBuild);
        mDevice.installPackage(buildHelper.getTestFile(DEVICE_SIDE_TEST_APK), true);

        mDevice.executeShellCommand("dumpsys storaged --force");

        runDeviceTests(DEVICE_SIDE_TEST_PACKAGE,
                "com.android.server.cts.storaged.StoragedTest",
                "testBackgroundIO");

        runDeviceTests(DEVICE_SIDE_TEST_PACKAGE,
                "com.android.server.cts.storaged.StoragedTest",
                "testForegroundIO");

        String output = mDevice.executeShellCommand("dumpsys storaged --force --hours 0.01");
        assertNotNull(output);
        assertTrue(output.length() > 0);

        boolean hasTestIO = false;
        try (BufferedReader reader = new BufferedReader(
                new StringReader(output))) {

            String line;
            String[] parts;
            while ((line = reader.readLine()) != null) {
                if (line.isEmpty()) {
                    continue;
                }

                if (line.contains(",")) {
                    parts = line.split(",");
                    assertTrue(parts.length == 2);
                    if (!parts[0].isEmpty()) {
                        assertInteger(parts[0]);
                    }
                    assertInteger(parts[1]);
                    continue;
                }

                parts = line.split(" ");
                assertTrue(parts.length == 9);
                for (int i = 1; i < parts.length; i++) {
                    assertInteger(parts[i]);
                }

                if (parts[0].equals(DEVICE_SIDE_TEST_PACKAGE)) {
                    assertTrue((Integer.parseInt(parts[6]) >= 4096 && Integer.parseInt(parts[8]) >= 4096) ||
                                Integer.parseInt(parts[8]) >= 8192);
                    hasTestIO = true;
                }
            }

            assertTrue(hasTestIO);
        }
    }
}
