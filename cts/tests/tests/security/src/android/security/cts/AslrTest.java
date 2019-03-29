/*
 * Copyright (C) 2011 The Android Open Source Project
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

import android.test.InstrumentationTestCase;
import junit.framework.TestCase;

import android.os.ParcelFileDescriptor;
import android.platform.test.annotations.SecurityTest;
import android.util.Log;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.FileInputStream;
import java.io.InputStreamReader;
import java.io.IOException;
import java.util.HashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.android.compatibility.common.util.ReadElf;

/**
 * Verify that ASLR is properly enabled on Android Compatible devices.
 */
@SecurityTest
public class AslrTest extends InstrumentationTestCase {

    private static final int aslrMinEntropyBits = 8;

    private static final String TAG = "AslrTest";

    private String readMappingAddress(String mappingName) throws Exception {
        ParcelFileDescriptor pfd = getInstrumentation().getUiAutomation()
                .executeShellCommand("/system/bin/cat /proc/self/maps");

        String result = null;
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(new ParcelFileDescriptor.AutoCloseInputStream(pfd)))) {
            Pattern p = Pattern.compile("^([a-f0-9]+).*" + mappingName + ".*");
            String line;

            while ((line = reader.readLine()) != null) {
                // Even after a match is found, read until the end to clean up the pipe.
                if (result != null) continue;

                Matcher m = p.matcher(line);

                if (m.matches()) {
                    result = m.group(1);
                }
            }
        }
        return result;
    }

    private int calculateEntropyBits(String mappingName) throws Exception {
        HashMap<String, Integer> addresses = new HashMap<String, Integer>();

        // Sufficient number of iterations to ensure we should see at least
        // aslrMinEntropyBits 
        for (int i = 0; i < 2 * (1 << aslrMinEntropyBits); i++) {
            addresses.put(readMappingAddress(mappingName), 1);
        }

        double entropy = Math.log(addresses.size()) / Math.log(2);

        Log.i(TAG, String.format("%.1f", entropy) +
            " bits of entropy for " + mappingName);

        return (int) Math.round(entropy);
    }

    private void testMappingEntropy(String mappingName) throws Exception {
        if (readMappingAddress(mappingName) == null) {
            Log.i(TAG, mappingName + " does not exist");
            return;
        }

        int entropy = calculateEntropyBits(mappingName);

        assertTrue("Insufficient " + mappingName + " randomization (" +
            entropy + " bits, >= " + aslrMinEntropyBits + " required)",
            entropy >= aslrMinEntropyBits);
    }

    public void testRandomization() throws Exception {
        testMappingEntropy("\\[stack\\]");
        testMappingEntropy("/system/bin/");
    }

    public void testOneExecutableIsPie() throws IOException {
        assertTrue(ReadElf.read(new File("/system/bin/cat")).isPIE());
    }

    public void testVaRandomize() throws IOException {
        BufferedReader in = null;
        try {
            in = new BufferedReader(new FileReader("/proc/sys/kernel/randomize_va_space"));
            int level = Integer.parseInt(in.readLine().trim());
            assertTrue("Expected /proc/sys/kernel/randomize_va_space to be "
                    + "greater than or equal to 2, got " + level,
                    level >= 2);
        } catch (FileNotFoundException e) {
            // Odd. The file doesn't exist... Assume ASLR is enabled.
        } finally {
            if (in != null) {
                in.close();
            }
        }
    }
}
