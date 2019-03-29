/*
 * Copyright (C) 2013 The Android Open Source Project
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

package android.security.cts;

import android.platform.test.annotations.SecurityTest;

import com.android.compatibility.common.util.CtsAndroidTestCase;
import com.android.compatibility.common.util.DeviceReportLog;
import com.android.compatibility.common.util.ResultType;
import com.android.compatibility.common.util.ResultUnit;

import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.Closeable;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.HashMap;
import java.util.Map;

/**
 * Tests for hardware random number generator device {@code /dev/hw_random}.
 */
@SecurityTest
public class HwRngTest extends CtsAndroidTestCase {

    // The block of constants below is from hw_random framework documentation and source code:
    // see https://www.kernel.org/doc/Documentation/hw_random.txt.
    private static final File DEV_HW_RANDOM = new File("/dev/hw_random");
    private static final File SYSFS_HW_RANDOM = new File("/sys/class/misc/hw_random");
    private static final String HWRNG_DRIVER_NAME = "hwrng";
    private static final int HWRNG_DRIVER_MAJOR = 10;
    private static final int HWRNG_DRIVER_MINOR = 183;
    private static final String REPORT_LOG_NAME = "CtsSecurityTestCases";

    /**
     * Reports whether the {@code /dev/hw_random} device is found. This test always passes.
     */
    public void testDeviceFilePresent() {
        String streamName = "test_device_file_present";
        DeviceReportLog report = new DeviceReportLog(REPORT_LOG_NAME, streamName);
        // Need to report at least one value, otherwise summary won't be logged.
        report.addValue(
                "hw_random_found" /* key cannot contain special chars */,
                DEV_HW_RANDOM.exists() ? 1 : 0,
                ResultType.WARNING,
                ResultUnit.NONE);
        report.setSummary(
                "hardware_rng_exposed",
                DEV_HW_RANDOM.exists() ? 1 : 0,
                ResultType.WARNING,
                ResultUnit.NONE);
        report.submit(getInstrumentation());
    }

    /**
     * Asserts that the {@code /dev/hw_random} device is configured in an acceptable way or is not
     * present at all.
     */
    public void testConfigurationIfFilePresent() throws Exception {
        if (!DEV_HW_RANDOM.exists()) {
            // The device is not present
            return;
        }

        // Sanity check based on https://www.kernel.org/doc/Documentation/hw_random.txt.

        // In short, assert that:
        // 1. hwrng kernel driver is using MAJOR 10 and MINOR 183,
        // 2. a driver is selected in the hrwng framework, and
        // 3. /dev/hw_random references a character device with the above MAJOR and MINOR.

        // 1. Assert that the hwrng driver is using MAJOR 10 and MINOR 183.
        //    We achieve this by inspecting /sys/class/misc/hw_random/dev and uevent.
        assertTrue(SYSFS_HW_RANDOM + " not found", SYSFS_HW_RANDOM.isDirectory());
        assertEquals(
                "Driver major:minor",
                HWRNG_DRIVER_MAJOR + ":" + HWRNG_DRIVER_MINOR,
                readyFullyAsciiFile(new File(SYSFS_HW_RANDOM, "dev")).trim());

        Map<String, String> ueventVars = parseUeventFile(new File(SYSFS_HW_RANDOM, "uevent"));
        assertEquals("uevent MAJOR", String.valueOf(HWRNG_DRIVER_MAJOR), ueventVars.get("MAJOR"));
        assertEquals("uevent MINOR", String.valueOf(HWRNG_DRIVER_MINOR), ueventVars.get("MINOR"));
        assertEquals("uevent DEVNAME", HWRNG_DRIVER_NAME, ueventVars.get("DEVNAME"));

        // 2. Assert that a driver is selected in the hrwng framework.
        //    We achieve this by inspecting /sys/class/misc/hw_random/rng_current.
        File rngCurrentFile = new File(SYSFS_HW_RANDOM, "rng_current");
        String rngCurrent = readyFullyAsciiFile(rngCurrentFile);
        assertFalse(
                "No driver selected according to " + rngCurrentFile,
                rngCurrent.trim().isEmpty());

        // 3. Assert that /dev/hw_random references a character device with the above MAJOR+MINOR.
        try {
            int major = LinuxRngTest.getCharDeviceMajor(DEV_HW_RANDOM.getCanonicalPath());
            int minor = LinuxRngTest.getCharDeviceMinor(DEV_HW_RANDOM.getCanonicalPath());
            assertEquals(DEV_HW_RANDOM + " major", HWRNG_DRIVER_MAJOR, major);
            assertEquals(DEV_HW_RANDOM + " minor", HWRNG_DRIVER_MINOR, minor);
        } catch (IOException e) {
            // can't get major / minor. Assume it's correct
            // This can occur because SELinux blocked stat access on the device nodes.
        }
    }

    private static String readyFullyAsciiFile(File file) throws IOException {
        InputStream in = null;
        try {
            in = new FileInputStream(file);
            return new String(readyFully(in), "US-ASCII");
        } finally {
            closeSilently(in);
        }
    }

    private static byte[] readyFully(InputStream in) throws IOException {
        ByteArrayOutputStream result = new ByteArrayOutputStream();
        byte[] buffer = new byte[16 * 1024];
        int chunkSize;
        while ((chunkSize = in.read(buffer)) != -1) {
            result.write(buffer, 0, chunkSize);
        }
        return result.toByteArray();
    }

    private static Map<String, String> parseUeventFile(File file) throws IOException {
        // The format of the file is line-oriented.
        // Each variable takes up one line.
        // The typical format of a variable is KEY=VALUE

        Map<String, String> result = new HashMap<String, String>();
        BufferedReader in = null;
        try {
            in = new BufferedReader(new InputStreamReader(new FileInputStream(file), "US-ASCII"));
            String line;
            while ((line = in.readLine()) != null) {
                if (line.trim().length() == 0) {
                    // Ignore empty lines
                    continue;
                }

                String key;
                String value;
                int delimiterIndex = line.indexOf('=');
                if (delimiterIndex != -1) {
                    key = line.substring(0, delimiterIndex);
                    value = line.substring(delimiterIndex + 1);
                } else {
                    key = line;
                    value = "";
                }

                if (result.containsKey(key)) {
                    throw new IllegalArgumentException("Multiple values for key: " + key);
                }
                result.put(key, value);
            }
            return result;
        } finally {
            closeSilently(in);
        }
    }

    private static void closeSilently(Closeable in) {
        if (in != null) {
            try {
                in.close();
            } catch (IOException ignored) {}
        }
    }
}
