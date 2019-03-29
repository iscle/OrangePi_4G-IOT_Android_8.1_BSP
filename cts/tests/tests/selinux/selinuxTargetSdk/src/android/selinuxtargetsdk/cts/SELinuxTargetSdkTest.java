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

package android.selinuxtargetsdk.cts;

import android.test.AndroidTestCase;
import android.test.suitebuilder.annotation.SmallTest;
import java.io.BufferedReader;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;

/**
 * Verify the selinux domain for apps running with targetSdkVersion<=25
 */
public class SELinuxTargetSdkTest extends AndroidTestCase
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

    /**
     * Verify that selinux context is the expected domain based on
     * targetSdkVersion,
     */
    @SmallTest
    public void testTargetSdkValue() throws IOException {
        String context = getFile("/proc/self/attr/current");
        String expected = "u:r:untrusted_app_25:s0";
        assertEquals("Untrusted apps with targetSdkVersion<=25 " +
                "must run in the untrusted_app_25 selinux domain.",
                context.substring(0, expected.length()),
                expected);
    }

}
