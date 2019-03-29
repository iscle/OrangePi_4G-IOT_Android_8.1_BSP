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
package com.android.compatibility.common.tradefed.presubmit;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import com.android.tradefed.testtype.suite.TestSuiteInfo;
import com.android.tradefed.util.AaptParser;
import com.android.tradefed.util.AbiUtils;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

import java.io.File;
import java.io.FilenameFilter;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

/**
 * Tests to validate that the build is containing usable test artifact.
 */
@RunWith(JUnit4.class)
public class ValidateTestsAbi {

    /**
     *  This particular module is shipping all it's dependencies in all abis with prebuilt stuff.
     *  Excluding it for now to have the test setup.
     */
    private static final String MODULE_EXCEPTION = "CtsSplitApp";

    private static final Set<String> BINARY_EXCEPTIONS = new HashSet<>();
    static {
        /**
         * This binary is a host side helper, so we do not need to check it.
         */
        BINARY_EXCEPTIONS.add("sepolicy-analyze");
    }

    /**
     * Test that all apks have the same supported abis.
     * Sometimes, if a module is missing LOCAL_MULTILIB := both, we will end up with only one of
     * the two abis required and the second one will fail.
     */
    @Test
    public void testApksAbis() {
        String ctsRoot = System.getProperty("CTS_ROOT");
        File testcases = new File(ctsRoot, "/android-cts/testcases/");
        if (!testcases.exists()) {
            fail(String.format("%s does not exists", testcases));
            return;
        }
        File[] listApks = testcases.listFiles(new FilenameFilter() {
            @Override
            public boolean accept(File dir, String name) {
                if (name.startsWith(MODULE_EXCEPTION)) {
                    return false;
                }
                if (name.endsWith(".apk")) {
                    return true;
                }
                return false;
            }
        });
        assertTrue(listApks.length > 0);
        int maxAbi = 0;
        Map<String, Integer> apkToAbi = new HashMap<>();

        for (File testApk : listApks) {
            AaptParser result = AaptParser.parse(testApk);
            // Retry as we have seen flake with aapt sometimes.
            if (result == null) {
                for (int i = 0; i < 2; i++) {
                    result = AaptParser.parse(testApk);
                    if (result != null) {
                        break;
                    }
                }
                // If still couldn't parse the apk
                if (result == null) {
                    fail(String.format("Fail to run 'aapt dump badging %s'",
                            testApk.getAbsolutePath()));
                }
            }
            // We only check the apk that have native code
            if (!result.getNativeCode().isEmpty()) {
                List<String> supportedAbiApk = result.getNativeCode();
                Set<String> buildTarget = AbiUtils.getAbisForArch(
                        TestSuiteInfo.getInstance().getTargetArch());
                // first check, all the abis are supported
                for (String abi : supportedAbiApk) {
                    if (!buildTarget.contains(abi)) {
                        fail(String.format("apk %s %s does not support our abis [%s]",
                                testApk.getName(), supportedAbiApk, buildTarget));
                    }
                }
                apkToAbi.put(testApk.getName(), supportedAbiApk.size());
                maxAbi = Math.max(maxAbi, supportedAbiApk.size());
            }
        }

        // We do a second pass to make sure nobody is short on abi
        for (Entry<String, Integer> apk : apkToAbi.entrySet()) {
            if (apk.getValue() < maxAbi) {
                fail(String.format("apk %s only has %s abi when it should have %s", apk.getKey(),
                        apk.getValue(), maxAbi));
            }
        }
    }

    /**
     * Test that when CTS has multiple abis, we have binary for each ABI. In this case the abi will
     * be the same with different bitness (only case supported by build system).
     * <p/>
     * If there is only one bitness, then we check that it's the right one.
     */
    @Test
    public void testBinariesAbis() {
        String ctsRoot = System.getProperty("CTS_ROOT");
        File testcases = new File(ctsRoot, "/android-cts/testcases/");
        if (!testcases.exists()) {
            fail(String.format("%s does not exist", testcases));
            return;
        }
        String[] listBinaries = testcases.list(new FilenameFilter() {
            @Override
            public boolean accept(File dir, String name) {
                if (name.contains(".")) {
                    return false;
                }
                if (BINARY_EXCEPTIONS.contains(name)) {
                    return false;
                }
                File file = new File(dir, name);
                if (file.isDirectory()) {
                    return false;
                }
                if (!file.canExecute()) {
                    return false;
                }
                return true;
            }
        });
        assertTrue(listBinaries.length > 0);
        List<String> orderedList = Arrays.asList(listBinaries);
        // we sort to have binary starting with same name, next to each other. The last two
        // characters of their name with be the bitness (32 or 64).
        Collections.sort(orderedList);
        Set<String> buildTarget = AbiUtils.getAbisForArch(
                TestSuiteInfo.getInstance().getTargetArch());
        // We expect one binary per abi of CTS, they should be appended with 32 or 64
        for (int i = 0; i < orderedList.size(); i=i + buildTarget.size()) {
            List<String> subSet = orderedList.subList(i, i + buildTarget.size());
            if (subSet.size() > 1) {
                String base = subSet.get(0).substring(0, subSet.get(0).length() - 2);
                for (int j = 0; j < subSet.size(); j++) {
                    assertEquals(base, subSet.get(j).substring(0, subSet.get(j).length() - 2));
                }
            } else {
                String bitness = AbiUtils.getBitness(buildTarget.iterator().next());
                assertTrue(subSet.get(i).endsWith(bitness));
            }
        }
    }
}
