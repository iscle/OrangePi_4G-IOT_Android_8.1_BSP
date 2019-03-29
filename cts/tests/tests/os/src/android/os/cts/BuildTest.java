/*
 * Copyright (C) 2010 The Android Open Source Project
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


import android.os.Build;
import android.os.SystemProperties;
import android.platform.test.annotations.RestrictedBuildTest;

import dalvik.system.VMRuntime;

import java.io.IOException;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.Arrays;
import java.util.List;
import java.util.Scanner;
import java.util.regex.Pattern;

import junit.framework.TestCase;

import static android.os.Build.VERSION.CODENAME;
import static android.os.Build.VERSION_CODES.CUR_DEVELOPMENT;

public class BuildTest extends TestCase {

    private static final String RO_PRODUCT_CPU_ABILIST = "ro.product.cpu.abilist";
    private static final String RO_PRODUCT_CPU_ABILIST32 = "ro.product.cpu.abilist32";
    private static final String RO_PRODUCT_CPU_ABILIST64 = "ro.product.cpu.abilist64";

    /** Tests that check the values of {@link Build#CPU_ABI} and {@link Build#CPU_ABI2}. */
    public void testCpuAbi() throws Exception {
        runTestCpuAbiCommon();
        if (VMRuntime.getRuntime().is64Bit()) {
            runTestCpuAbi64();
        } else {
            runTestCpuAbi32();
        }
    }

    private void runTestCpuAbiCommon() throws Exception {
        // The build property must match Build.SUPPORTED_ABIS exactly.
        final String[] abiListProperty = getStringList(RO_PRODUCT_CPU_ABILIST);
        assertEquals(Arrays.toString(abiListProperty), Arrays.toString(Build.SUPPORTED_ABIS));

        List<String> abiList = Arrays.asList(abiListProperty);

        // Every device must support at least one 32 bit ABI.
        assertTrue(Build.SUPPORTED_32_BIT_ABIS.length > 0);

        // Every supported 32 bit ABI must be present in Build.SUPPORTED_ABIS.
        for (String abi : Build.SUPPORTED_32_BIT_ABIS) {
            assertTrue(abiList.contains(abi));
            assertFalse(VMRuntime.is64BitAbi(abi));
        }

        // Every supported 64 bit ABI must be present in Build.SUPPORTED_ABIS.
        for (String abi : Build.SUPPORTED_64_BIT_ABIS) {
            assertTrue(abiList.contains(abi));
            assertTrue(VMRuntime.is64BitAbi(abi));
        }

        // Build.CPU_ABI and Build.CPU_ABI2 must be present in Build.SUPPORTED_ABIS.
        assertTrue(abiList.contains(Build.CPU_ABI));
        if (!Build.CPU_ABI2.isEmpty()) {
            assertTrue(abiList.contains(Build.CPU_ABI2));
        }
    }

    private void runTestCpuAbi32() throws Exception {
        List<String> abi32 = Arrays.asList(Build.SUPPORTED_32_BIT_ABIS);
        assertTrue(abi32.contains(Build.CPU_ABI));

        if (!Build.CPU_ABI2.isEmpty()) {
            assertTrue(abi32.contains(Build.CPU_ABI2));
        }
    }

    private void runTestCpuAbi64() {
        List<String> abi64 = Arrays.asList(Build.SUPPORTED_64_BIT_ABIS);
        assertTrue(abi64.contains(Build.CPU_ABI));

        if (!Build.CPU_ABI2.isEmpty()) {
            assertTrue(abi64.contains(Build.CPU_ABI2));
        }
    }

    private String[] getStringList(String property) throws IOException {
        String value = getProperty(property);
        if (value.isEmpty()) {
            return new String[0];
        } else {
            return value.split(",");
        }
    }

    /**
     * @param property name passed to getprop
     */
    static String getProperty(String property)
            throws IOException {
        Process process = new ProcessBuilder("getprop", property).start();
        Scanner scanner = null;
        String line = "";
        try {
            scanner = new Scanner(process.getInputStream());
            line = scanner.nextLine();
        } finally {
            if (scanner != null) {
                scanner.close();
            }
        }
        return line;
    }
    /**
     * @param message shown when the test fails
     * @param property name passed to getprop
     * @param expected value of the property
     */
    private void assertProperty(String message, String property, String expected)
            throws IOException {
        Process process = new ProcessBuilder("getprop", property).start();
        Scanner scanner = null;
        try {
            scanner = new Scanner(process.getInputStream());
            String line = scanner.nextLine();
            assertEquals(message + " Value found: " + line , expected, line);
            assertFalse(scanner.hasNext());
        } finally {
            if (scanner != null) {
                scanner.close();
            }
        }
    }

    /**
     * Check that a property is not set by scanning through the list of properties returned by
     * getprop, since calling getprop on an property set to "" and on a non-existent property
     * yields the same output.
     *
     * @param message shown when the test fails
     * @param property name passed to getprop
     */
    private void assertNoPropertySet(String message, String property) throws IOException {
        Process process = new ProcessBuilder("getprop").start();
        Scanner scanner = null;
        try {
            scanner = new Scanner(process.getInputStream());
            while (scanner.hasNextLine()) {
                String line = scanner.nextLine();
                assertFalse(message + "Property found: " + line,
                        line.startsWith("[" + property + "]"));
            }
        } finally {
            if (scanner != null) {
                scanner.close();
            }
        }
    }

    private static final Pattern BOARD_PATTERN =
        Pattern.compile("^([0-9A-Za-z._-]+)$");
    private static final Pattern BRAND_PATTERN =
        Pattern.compile("^([0-9A-Za-z._-]+)$");
    private static final Pattern DEVICE_PATTERN =
        Pattern.compile("^([0-9A-Za-z._-]+)$");
    private static final Pattern ID_PATTERN =
        Pattern.compile("^([0-9A-Za-z._-]+)$");
    private static final Pattern HARDWARE_PATTERN =
        Pattern.compile("^([0-9A-Za-z.,_-]+)$");
    private static final Pattern PRODUCT_PATTERN =
        Pattern.compile("^([0-9A-Za-z._-]+)$");
    private static final Pattern SERIAL_NUMBER_PATTERN =
        Pattern.compile("^([0-9A-Za-z]{6,20})$");
    private static final Pattern TAGS_PATTERN =
        Pattern.compile("^([0-9A-Za-z.,_-]+)$");
    private static final Pattern TYPE_PATTERN =
        Pattern.compile("^([0-9A-Za-z._-]+)$");

    /** Tests that check for valid values of constants in Build. */
    public void testBuildConstants() {
        // Build.VERSION.* constants tested by BuildVersionTest

        assertTrue(BOARD_PATTERN.matcher(Build.BOARD).matches());

        assertTrue(BRAND_PATTERN.matcher(Build.BRAND).matches());

        assertTrue(DEVICE_PATTERN.matcher(Build.DEVICE).matches());

        // Build.FINGERPRINT tested by BuildVersionTest

        assertTrue(HARDWARE_PATTERN.matcher(Build.HARDWARE).matches());

        assertNotEmpty(Build.HOST);

        assertTrue(ID_PATTERN.matcher(Build.ID).matches());

        assertNotEmpty(Build.MANUFACTURER);

        assertNotEmpty(Build.MODEL);

        assertTrue(PRODUCT_PATTERN.matcher(Build.PRODUCT).matches());

        assertTrue(SERIAL_NUMBER_PATTERN.matcher(Build.SERIAL).matches());

        assertTrue(TAGS_PATTERN.matcher(Build.TAGS).matches());

        // No format requirements stated in CDD for Build.TIME

        assertTrue(TYPE_PATTERN.matcher(Build.TYPE).matches());

        assertNotEmpty(Build.USER);

        // CUR_DEVELOPMENT must be larger than any released version.
        Field[] fields = Build.VERSION_CODES.class.getDeclaredFields();
        for (Field field : fields) {
            if (field.getType().equals(int.class) && Modifier.isStatic(field.getModifiers())) {
                String fieldName = field.getName();
                final int fieldValue;
                try {
                    fieldValue = field.getInt(null);
                } catch (IllegalAccessException e) {
                    throw new AssertionError(e.getMessage());
                }
                if (fieldName.equals("CUR_DEVELOPMENT")) {
                    // It should be okay to change the value of this constant in future, but it
                    // should at least be a conscious decision.
                    assertEquals(10000, fieldValue);
                } else if (fieldName.equals(CODENAME) && !CODENAME.equals("REL")) {
                    // This is the current development version. Note that fieldName can
                    // become < CUR_DEVELOPMENT before CODENAME becomes "REL", so we
                    // can't assertEquals(CUR_DEVELOPMENT, fieldValue) here.
                    assertTrue("Expected " + fieldName + " value to be <= " + CUR_DEVELOPMENT
                            + ", got " + fieldValue, fieldValue <= CUR_DEVELOPMENT);
                } else {
                    assertTrue("Expected " + fieldName + " value to be < " + CUR_DEVELOPMENT
                            + ", got " + fieldValue, fieldValue < CUR_DEVELOPMENT);
                }
            }
        }
    }

    static final String RO_DEBUGGABLE = "ro.debuggable";
    private static final String RO_SECURE = "ro.secure";

    /**
     * Assert that the device is a secure, not debuggable user build.
     *
     * Debuggable devices allow adb root and have the su command, allowing
     * escalations to root and unauthorized access to application data.
     *
     * Note: This test will fail on userdebug / eng devices, but should pass
     * on production (user) builds.
     */
    @RestrictedBuildTest
    public void testIsSecureUserBuild() throws IOException {
        assertEquals("Must be a user build", "user", Build.TYPE);
        assertProperty("Must be a non-debuggable build", RO_DEBUGGABLE, "0");
        assertProperty("Must be a secure build", RO_SECURE, "1");
    }

    private void assertNotEmpty(String value) {
        assertNotNull(value);
        assertFalse(value.isEmpty());
    }
}
