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

package android.selinuxtargetsdk2.cts;

import android.test.AndroidTestCase;
import android.test.suitebuilder.annotation.SmallTest;
import java.io.BufferedReader;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.util.Scanner;

/**
 * Verify the selinux domain for apps running with current targetSdkVersion
 */
public class SELinuxTargetSdk2Test extends AndroidTestCase
{
    static String getFile(String filename) throws IOException {
        BufferedReader in = null;
        try {
            in = new BufferedReader(new FileReader(filename));
            return in.readLine().trim();
        } finally {
            if (in != null) {
                in.close();
            }
        }
    }

    private static String getProperty(String property)
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
     * Verify that net.dns properties may not be read
     */
    @SmallTest
    public void testNoDns() throws IOException {
        String[] dnsProps = {"net.dns1", "net.dns2", "net.dns3", "net.dns4"};
        for(int i = 0; i < dnsProps.length; i++) {
            String dns = getProperty(dnsProps[i]);
            assertEquals("DNS properties may not be readable by apps past " +
                    "targetSdkVersion 26", dns, "");
        }
    }

    /**
     * Verify that selinux context is the expected domain based on
     * targetSdkVersion,
     */
    @SmallTest
    public void testTargetSdkValue() throws IOException {
        String context = getFile("/proc/self/attr/current");
        String expected = "u:r:untrusted_app:s0";
        assertEquals("Untrusted apps with current targetSdkVersion " +
                "must run in the untrusted_app selinux domain.",
                context.substring(0, expected.length()),
                expected);
    }
}
