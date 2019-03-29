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

package android.trustedvoice.cts;

import com.android.tradefed.testtype.DeviceTestCase;

import java.io.File;
import java.lang.String;
import java.util.Scanner;

/**
 * Test to check the APK logs to Logcat.
 * This test first locks the device screen and then runs the associated app to run the test.
 *
 * When this test builds, it also builds {@see android.trustedvoice.app.TrustedVoiceActivity}
 * into an APK which it then installs at runtime. TrustedVoiceActivity sets the
 * FLAG_DISMISS_KEYGUARD, prints a message to Logcat and then gets uninstalled.
 */
public class TrustedVoiceHostTest extends DeviceTestCase {

    /**
     * The package name of the APK.
     */
    private static final String PACKAGE = "android.trustedvoice.app";

    /**
     * Lock screen key event code.
     */
    private static final int SLEEP_KEYEVENT = 223;

    /**
     * Lock screen key event code.
     */
    private static final int AWAKE_KEYEVENT = 224;

    /**
     * The file name of the APK.
     */
    private static final String APK = "CtsTrustedVoiceApp.apk";

    /**
     * The class name of the main activity in the APK.
     */
    private static final String CLASS = "TrustedVoiceActivity";

    /**
     * The command to launch the main activity.
     */
    private static final String START_COMMAND = String.format(
            "am start -W -a android.intent.action.MAIN -n %s/%s.%s", PACKAGE, PACKAGE, CLASS);

    /**
     * The command to put the device to sleep.
     */
    private static final String SLEEP_COMMAND = String.format(
            "input keyevent %d", SLEEP_KEYEVENT);

    /**
     * The command to wake the device up.
     */
    private static final String AWAKE_COMMAND = String.format(
            "input keyevent %d", AWAKE_KEYEVENT);

    /**
     * The command to dismiss the keyguard.
     */
    private static final String DISMISS_KEYGUARD_COMMAND = "wm dismiss-keyguard";

    /**
     * The test string to look for.
     */
    private static final String TEST_STRING = "TrustedVoiceTestString";

    /**
     * Tests the app successfully unlocked the device.
     *
     * @throws Exception
     */
    public void testUnlock() throws Exception {
        Scanner in = null;
        try {
            // Clear logcat.
            getDevice().executeAdbCommand("logcat", "-c");
            // Lock the device
            getDevice().executeShellCommand(SLEEP_COMMAND);
            // Start the APK and wait for it to complete.
            getDevice().executeShellCommand(START_COMMAND);
            // Adding delay for OEM specific features which could delay the time of printing the
            // test log. Please refer to b/62075150 for additional details.
            Thread.sleep(1000);
            // Dump logcat.
            String logs = getDevice().executeAdbCommand(
                    "logcat", "-v", "brief", "-d", CLASS + ":I", "*:S");
            // Search for string.
            in = new Scanner(logs);
            String testString = "";

            while (in.hasNextLine()) {
                String line = in.nextLine();
                if(line.contains(TEST_STRING)) {
                    // Retrieve the test string.
                    testString = line.split(":")[1].trim();
                    break;
                }
            }
            // Assert the logged string matches the test string.
            assertNotNull("Test string must not be null", testString);
            assertEquals("Test string does not match", TEST_STRING, testString);
        } finally {
            if (in != null) {
                in.close();
            }
            // Unlock the device
            getDevice().executeShellCommand(AWAKE_COMMAND);
            getDevice().executeShellCommand(DISMISS_KEYGUARD_COMMAND);
        }
    }
}
