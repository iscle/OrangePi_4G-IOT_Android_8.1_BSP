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


package android.os.cts;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.android.compatibility.common.util.CpuFeatures;

import junit.framework.TestCase;

public class CpuFeaturesTest extends TestCase {

    private static void assertHwCap(String name, int hwcaps, int flag) {
        assertEquals("Machine does not advertise " + name + " support", flag,
                hwcaps & flag);
    }

    public void testArm64RequiredHwCaps() {
        if (!CpuFeatures.isArm64CpuIn32BitMode()) {
            return;
        }

        int hwcaps = CpuFeatures.getHwCaps();

        assertFalse("Machine does not support getauxval(AT_HWCAP)",
                hwcaps == 0);

        assertHwCap("VFP", hwcaps, CpuFeatures.HWCAP_VFP);
        assertHwCap("NEON", hwcaps, CpuFeatures.HWCAP_NEON);
        assertHwCap("VFPv3", hwcaps, CpuFeatures.HWCAP_VFPv3);
        assertHwCap("VFPv4", hwcaps, CpuFeatures.HWCAP_VFPv4);
        assertHwCap("IDIVA", hwcaps, CpuFeatures.HWCAP_IDIVA);
        assertHwCap("IDIVT", hwcaps, CpuFeatures.HWCAP_IDIVT);
    }

    private static String getFieldFromCpuinfo(String field) throws IOException {
        BufferedReader br = new BufferedReader(new FileReader("/proc/cpuinfo"));
        Pattern p = Pattern.compile(field + "\\s*:\\s*(.*)");

        try {
            String line;
            while ((line = br.readLine()) != null) {
                Matcher m = p.matcher(line);
                if (m.matches()) {
                    return m.group(1);
                }
            }
       } finally {
           br.close();
       }

       return null;
    }

    private static List<String> getFeaturesFromCpuinfo() throws IOException {
        String features = getFieldFromCpuinfo("Features");
        if (features == null)
            return null;

        return Arrays.asList(features.split("\\s"));
    }

    private static final String[] armv8RequiredFeatures = {
            "half", "thumb", "fastmult", "vfp", "edsp", "neon",
            "vfpv3", "vfpv4", "idiva", "idivt" };

    private static void assertInCpuinfo(List<String> features,
            String feature) {
        assertTrue("/proc/cpuinfo does not advertise required feature " + feature + " to 32-bit ARM processes",
                features.contains(feature));
    }

    private static void assertNotInCpuinfo(List<String> features,
            String feature) {
        assertFalse("/proc/cpuinfo advertises required feature " + feature + " to 64-bit ARM processes",
                features.contains(feature));
    }

    public void testArmCpuinfo() throws IOException {
        if (!CpuFeatures.isArmCpu())
            return;

        String cpuArch = getFieldFromCpuinfo("CPU architecture");
        /* When /proc/cpuinfo is read by 32-bit ARM processes, the CPU
         * architecture field must be present and contain an integer.
         */
        assertNotNull("Failed to read CPU architecture field from /proc/cpuinfo",
                cpuArch);

        int cpuArchInt = 0;
        try {
            cpuArchInt = Integer.parseInt(cpuArch);
        } catch (NumberFormatException e) {
            fail("/proc/cpuinfo reported non-integer CPU architecture " + cpuArch);
        }

        if (CpuFeatures.isArm64CpuIn32BitMode()) {
            assertTrue("/proc/cpuinfo reported 32-bit only CPU architecture " + cpuArchInt + " on ARM64 CPU",
                    cpuArchInt >= 8);
        }

        List<String> features = getFeaturesFromCpuinfo();
        /* When /proc/cpuinfo is read by 32-bit ARM processes, the Features
         * field must be present.  On ARMv8+ devices specifically, it must
         * include ARMv7-optional features that are now required by ARMv8.
         */
        assertNotNull("Failed to read Features field from /proc/cpuinfo",
                features);

        if (CpuFeatures.isArm64CpuIn32BitMode()) {
            for (String feature : armv8RequiredFeatures) {
                assertInCpuinfo(features, feature);
            }
        }
    }

    public void testArm64Cpuinfo() throws IOException {
        if (!CpuFeatures.isArm64Cpu()) {
            return;
        }

        List<String> features = getFeaturesFromCpuinfo();
        /* When /proc/cpuinfo is read by 64-bit ARM processes, the Features
         * field in /proc/cpuinfo must not include ARMv8-required features.
         * This can be satisified either by not listing required features, or by
         * not having a Features field at all.
         */
        if (features == null) {
            return;
        }

        for (String feature : armv8RequiredFeatures) {
            assertNotInCpuinfo(features, feature);
        }
    }
}
