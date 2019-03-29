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

import android.platform.test.annotations.SecurityTest;

import junit.framework.TestCase;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;

/**
 * Verify that the kernel is configured how we expect it to be
 * configured.
 */
@SecurityTest
public class KernelSettingsTest extends TestCase {

    static {
        System.loadLibrary("ctssecurity_jni");
    }

    /**
     * Protect against kernel based NULL pointer attacks by enforcing a
     * minimum (and maximum!) value of mmap_min_addr.
     *
     * http://lwn.net/Articles/342330/
     * http://lwn.net/Articles/342420/
     */
    public void testMmapMinAddr() throws IOException {
        try {
            assertEquals("32768", getFile("/proc/sys/vm/mmap_min_addr"));
        } catch (FileNotFoundException e) {
            // Odd. The file doesn't exist... Assume we're ok.
        }
    }

    /**
     * /proc/kallsyms will show the address of exported kernel symbols. This
     * information can be used to write a reliable kernel exploit that can run
     * on many platforms without using hardcoded pointers. To make this more
     * difficult for attackers, don't export kernel symbols.
     */
    public void testKptrRestrict() throws IOException {
        try {
            assertEquals("2", getFile("/proc/sys/kernel/kptr_restrict"));
        } catch (FileNotFoundException e) {
            // Odd. The file doesn't exist... Assume we're ok.
        }
    }

    /**
     * setuid programs should not be dumpable.
     */
    public void testSetuidDumpable() throws IOException {
        try {
            assertEquals("0", getFile("/proc/sys/fs/suid_dumpable"));
        } catch (FileNotFoundException e) {
            // Odd. The file doesn't exist... Assume we're ok.
        }
    }

    /**
     * Verify that ext4 extended attributes (xattrs) are enabled in the
     * Linux kernel.
     *
     * To fix this failure, you need to enable the following kernel options:
     * - CONFIG_EXT4_FS_XATTR
     * - CONFIG_EXT4_FS_SECURITY
     *
     * Failure to enable this option may result in upgrade problems when
     * trying to upgrade to Android 4.4.
     */
    public void testXattrInKernel() {
        assertTrue(supportsXattr());
    }

    private static native boolean supportsXattr();

    /**
     * ICMP redirects should be disabled.
     */
    public void testNoIcmpRedirects() throws IOException {
        try {
            assertEquals("ICMP redirects are enabled for IPv4.",
                    "0", getFile("/proc/sys/net/ipv4/conf/all/accept_redirects"));
        } catch (FileNotFoundException e) {
            // Odd. The file doesn't exist... Assume we're ok.
        }

        try {
            assertEquals("ICMP redirects are enabled for IPv6.",
                    "0", getFile("/proc/sys/net/ipv6/conf/all/accept_redirects"));
        } catch (FileNotFoundException e) {
            // Odd. The file doesn't exist... Assume we're ok.
        }
    }

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
}
