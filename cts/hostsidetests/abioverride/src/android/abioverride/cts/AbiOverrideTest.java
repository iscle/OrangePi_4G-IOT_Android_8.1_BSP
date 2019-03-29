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

package android.abioverride.cts;

import com.android.compatibility.common.tradefed.build.CompatibilityBuildHelper;
import com.android.tradefed.build.IBuildInfo;
import com.android.tradefed.device.ITestDevice;
import com.android.tradefed.testtype.DeviceTestCase;
import com.android.tradefed.testtype.IBuildReceiver;

import java.io.File;
import java.util.Scanner;

/**
 * Test to check the APK runs in 32bit ABI.
 *
 * When this test builds, it also builds {@link android.abioverride.app.AbiOverrideActivity}
 * into an APK which it then installed at runtime and started. The activity simply prints
 * a message to Logcat and then gets uninstalled. The test verifies that logcat has the right
 * string.
 */
public class AbiOverrideTest extends DeviceTestCase implements IBuildReceiver {

    /**
     * The package name of the APK.
     */
    private static final String PACKAGE = "android.abioverride.app";

    /**
     * The class name of the main activity in the APK.
     */
    private static final String CLASS = "AbiOverrideActivity";

    /**
     * The class name of the main activity in the APK.
     */
    private static final String APK_NAME="CtsAbiOverrideTestApp.apk";

    /**
     * The command to launch the main activity.
     */
    private static final String START_COMMAND = String.format(
            "am start -W -a android.intent.action.MAIN -n %s/%s.%s", PACKAGE, PACKAGE, CLASS);


    private static final String TEST_STRING = "Is64bit ";
            // android.abioverride.app.AbiOverrideActivity.TEST_STRING;

    private IBuildInfo mBuild;

    @Override
    public void setBuild(IBuildInfo buildInfo) {
        mBuild = buildInfo;
    }

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        ITestDevice device = getDevice();
        device.uninstallPackage(PACKAGE);
        CompatibilityBuildHelper buildHelper = new CompatibilityBuildHelper(mBuild);
        File app = buildHelper.getTestFile(APK_NAME);
        String[] options = {};
        device.installPackage(app, false, options);
    }

    /**
     * Tests the abi is correctly set to 32bit when use32BitAbi is set to true.
     *
     * @throws Exception
     */
    public void testAbiIs32bit() throws Exception {
        ITestDevice device = getDevice();
        // Clear logcat.
        device.executeAdbCommand("logcat", "-c");
        // Start the APK and wait for it to complete.
        device.executeShellCommand(START_COMMAND);
        // Dump logcat
        String logs = device.executeAdbCommand("logcat", "-v", "brief", "-d", CLASS + ":I", "*:S");
        // Search for string.
        String testString = "";
        Scanner in = new Scanner(logs);
        while (in.hasNextLine()) {
            String line = in.nextLine();
            if(line.startsWith("I/"+CLASS)) {
                testString = line.split(":")[1].trim();
            }
        }
        in.close();
        // Verify that TEST_STRING is actually found in logs.
        assertTrue("No result found in logs", testString.startsWith(TEST_STRING));
        // Assert that the result is false
        assertEquals("Incorrect abi", TEST_STRING + "false", testString);
    }
}
