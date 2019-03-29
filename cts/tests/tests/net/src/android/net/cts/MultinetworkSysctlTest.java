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

package android.net.cts;

import android.system.ErrnoException;
import android.system.Os;
import android.system.OsConstants;
import android.system.StructStat;
import android.test.AndroidTestCase;

import java.io.File;
import java.io.FileDescriptor;
import java.io.IOException;

/**
 * Tests for multinetwork sysctl functionality.
 */
public class MultinetworkSysctlTest extends AndroidTestCase {

    // Global sysctls. Must be present and set to 1.
    private static final String[] GLOBAL_SYSCTLS = {
        "/proc/sys/net/ipv4/fwmark_reflect",
        "/proc/sys/net/ipv6/fwmark_reflect",
        "/proc/sys/net/ipv4/tcp_fwmark_accept",
    };

    // Per-interface IPv6 autoconf sysctls.
    private static final String IPV6_SYSCTL_DIR = "/proc/sys/net/ipv6/conf";
    private static final String AUTOCONF_SYSCTL = "accept_ra_rt_table";

    // Expected mode, UID, and GID of sysctl files.
    private static final int SYSCTL_MODE = 0100644;
    private static final int SYSCTL_UID = 0;
    private static final int SYSCTL_GID = 0;

    private void checkSysctlPermissions(String fileName) throws ErrnoException {
        StructStat stat = Os.stat(fileName);
        assertEquals("mode of " + fileName + ":", SYSCTL_MODE, stat.st_mode);
        assertEquals("UID of " + fileName + ":", SYSCTL_UID, stat.st_uid);
        assertEquals("GID of " + fileName + ":", SYSCTL_GID, stat.st_gid);
    }

    private void assertLess(String what, int a, int b) {
        assertTrue(what + " expected < " + b + " but was: " + a, a < b);
    }

    private String readFile(String fileName) throws ErrnoException, IOException {
        byte[] buf = new byte[1024];
        FileDescriptor fd = Os.open(fileName, 0, OsConstants.O_RDONLY);
        int bytesRead = Os.read(fd, buf, 0, buf.length);
        assertLess("length of " + fileName + ":", bytesRead, buf.length);
        return new String(buf);
    }

    /**
     * Checks that the sysctls for multinetwork kernel features are present and
     * enabled. The necessary kernel commits are:
     *
     * Mainline Linux:
     *   e110861 net: add a sysctl to reflect the fwmark on replies
     *   1b3c61d net: Use fwmark reflection in PMTU discovery.
     *   84f39b0 net: support marking accepting TCP sockets
     *
     * Common Android tree (e.g., 3.10):
     *   a03f539 net: ipv6: autoconf routes into per-device tables
     */
     public void testProcFiles() throws ErrnoException, IOException, NumberFormatException {
         for (String sysctl : GLOBAL_SYSCTLS) {
             checkSysctlPermissions(sysctl);
             int value = Integer.parseInt(readFile(sysctl).trim());
             assertEquals("value of " + sysctl + ":", 1, value);
         }

         File[] interfaceDirs = new File(IPV6_SYSCTL_DIR).listFiles();
         for (File interfaceDir : interfaceDirs) {
             if (interfaceDir.getName().equals("all") || interfaceDir.getName().equals("lo")) {
                 continue;
             }
             String sysctl = new File(interfaceDir, AUTOCONF_SYSCTL).getAbsolutePath();
             checkSysctlPermissions(sysctl);
             int value = Integer.parseInt(readFile(sysctl).trim());
             assertLess("value of " + sysctl + ":", value, 0);
         }
     }
}
