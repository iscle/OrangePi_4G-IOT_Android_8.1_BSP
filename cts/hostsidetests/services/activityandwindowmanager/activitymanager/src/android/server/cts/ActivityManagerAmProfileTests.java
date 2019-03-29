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

package android.server.cts;

import com.google.common.io.Files;

import com.android.tradefed.device.DeviceNotAvailableException;
import com.android.tradefed.device.ITestDevice;
import com.android.tradefed.util.FileUtil;

import java.io.File;
import java.lang.StringBuilder;

/**
 * Build: mmma -j32 cts/hostsidetests/services
 * Run: cts/hostsidetests/services/activityandwindowmanager/util/run-test CtsServicesHostTestCases android.server.cts.ActivityManagerAmProfileTests
 *
 * Please talk to Android Studio team first if you want to modify or delete these tests.
 */
public class ActivityManagerAmProfileTests extends ActivityManagerTestBase {

    private static final String TEST_PACKAGE_NAME = "android.server.cts.debuggable";
    private static final String TEST_ACTIVITY_NAME = "DebuggableAppActivity";
    private static final String OUTPUT_FILE_PATH = "/data/local/tmp/profile.trace";
    private static final String FIRST_WORD_NO_STREAMING = "*version\n";
    private static final String FIRST_WORD_STREAMING = "SLOW";  // Magic word set by runtime.

    private ITestDevice mDevice;

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        mDevice = getDevice();
        setComponentName(TEST_PACKAGE_NAME);
    }

    /**
     * Test am profile functionality with the following 3 configurable options:
     *    starting the activity before start profiling? yes;
     *    sampling-based profiling? no;
     *    using streaming output mode? no.
     */
    public void testAmProfileStartNoSamplingNoStreaming() throws Exception {
        // am profile start ... , and the same to the following 3 test methods.
        testProfile(true, false, false);
    }

    /**
     * The following tests are similar to testAmProfileStartNoSamplingNoStreaming(),
     * only different in the three configuration options.
     */
    public void testAmProfileStartNoSamplingStreaming() throws Exception {
        testProfile(true, false, true);
    }
    public void testAmProfileStartSamplingNoStreaming() throws Exception {
        testProfile(true, true, false);
    }
    public void testAmProfileStartSamplingStreaming() throws Exception {
        testProfile(true, true, true);
    }
    public void testAmStartStartProfilerNoSamplingNoStreaming() throws Exception {
        // am start --start-profiler ..., and the same to the following 3 test methods.
        testProfile(false, false, false);
    }
    public void testAmStartStartProfilerNoSamplingStreaming() throws Exception {
        testProfile(false, false, true);
    }
    public void testAmStartStartProfilerSamplingNoStreaming() throws Exception {
        testProfile(false, true, false);
    }
    public void testAmStartStartProfilerSamplingStreaming() throws Exception {
        testProfile(false, true, true);
    }

    private void testProfile(boolean startActivityFirst,
                             boolean sampling, boolean streaming) throws Exception {
        if (startActivityFirst) {
            launchActivity(TEST_ACTIVITY_NAME);
        }

        String cmd = getStartCmd(TEST_ACTIVITY_NAME, startActivityFirst, sampling, streaming);
        executeShellCommand(cmd);
        // Go to home screen and then warm start the activity to generate some interesting trace.
        pressHomeButton();
        launchActivity(TEST_ACTIVITY_NAME);

        cmd = "am profile stop " + componentName;
        executeShellCommand(cmd);
        // Sleep for 0.1 second (100 milliseconds) so the generation of the profiling
        // file is complete.
        try {
            Thread.sleep(100);
        } catch (InterruptedException e) {
            //ignored
        }
        verifyOutputFileFormat(streaming);
    }

    private String getStartCmd(String activityName, boolean activityAlreadyStarted,
                                        boolean sampling, boolean streaming) {
        StringBuilder builder = new StringBuilder("am");
        if (activityAlreadyStarted) {
            builder.append(" profile start");
        } else {
            builder.append(String.format(" start -n %s/.%s -W -S --start-profiler %s",
                                         componentName, activityName, OUTPUT_FILE_PATH));
        }
        if (sampling) {
            builder.append(" --sampling 1000");
        }
        if (streaming) {
            builder.append(" --streaming");
        }
        if (activityAlreadyStarted) {
            builder.append(String.format(" %s %s", componentName, OUTPUT_FILE_PATH));
        } else {

        }
        return builder.toString();
    }

    private void verifyOutputFileFormat(boolean streaming) throws Exception {
        String expectedFirstWord = streaming ? FIRST_WORD_STREAMING : FIRST_WORD_NO_STREAMING;
        byte[] data = readFileOnClient(OUTPUT_FILE_PATH);
        assertTrue("data size=" + data.length, data.length >= expectedFirstWord.length());
        String actualFirstWord = new String(data, 0, expectedFirstWord.length());
        assertTrue("Unexpected first word: '" + actualFirstWord + "'",
                   actualFirstWord.equals(expectedFirstWord));
        // Clean up.
        executeShellCommand("rm -f " + OUTPUT_FILE_PATH);
    }

    private byte[] readFileOnClient(String clientPath) throws Exception {
        assertTrue("File not found on client: " + clientPath,
                mDevice.doesFileExist(clientPath));
        File copyOnHost = File.createTempFile("host", "copy");
        try {
            executeAdbCommand("pull", clientPath, copyOnHost.getPath());
            return Files.toByteArray(copyOnHost);
        } finally {
            FileUtil.deleteFile(copyOnHost);
        }
    }

    private String[] executeAdbCommand(String... command) throws DeviceNotAvailableException {
        String output = mDevice.executeAdbCommand(command);
        // "".split() returns { "" }, but we want an empty array
        String[] lines = output.equals("") ? new String[0] : output.split("\n");
        return lines;
    }

}
