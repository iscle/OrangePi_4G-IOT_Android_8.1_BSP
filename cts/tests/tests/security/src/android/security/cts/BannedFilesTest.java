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

import android.platform.test.annotations.RestrictedBuildTest;
import android.platform.test.annotations.SecurityTest;

import com.android.compatibility.common.util.FileUtils;

import junit.framework.TestCase;

import java.io.DataInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.UnsupportedEncodingException;

@SecurityTest
public class BannedFilesTest extends TestCase {

    /**
     * Detect devices vulnerable to the cmdclient privilege escalation bug.
     *
     * References:
     *
     * http://vulnfactory.org/blog/2012/02/18/xoom-fe-stupid-bugs-and-more-plagiarism/
     * http://forum.xda-developers.com/showthread.php?t=1213014
     */
    public void testNoCmdClient() {
        assertNotSetugid("/system/bin/cmdclient");
    }

    public void testNoSyncAgent() {
        assertNotSetugid("/system/bin/sync_agent");
    }

    /**
     * Detect devices allowing shell commands to be executed as root
     * through sockets.
     *
     * References:
     *
     * https://plus.google.com/+JustinCaseAndroid/posts/e1r6c9Z9jgg
     * https://plus.google.com/+JustinCaseAndroid/posts/5ofgPNrSu3J
     */
    public void testNoRootCmdSocket() {
        assertFalse("/dev/socket/init_runit", new File("/dev/socket/init_runit").exists());
        assertFalse("/dev/socket/fotabinder", new File("/dev/socket/fotabinder").exists());
    }

    /**
     * Detect devices allowing shell commands to be executed as system
     * through sockets.
     *
     * ANDROID-19679287
     * CVE-2015-2231
     */
    public void testNoSystemCmdSocket() {
        assertFalse("/dev/socket/fota", new File("/dev/socket/fota").exists());
    }

    @RestrictedBuildTest
    public void testNoSu() {
        assertFalse("/sbin/su",        new File("/sbin/su").exists());
        assertFalse("/system/bin/su",  new File("/system/bin/su").exists());
        assertFalse("/system/sbin/su", new File("/system/sbin/su").exists());
        assertFalse("/system/xbin/su", new File("/system/xbin/su").exists());
        assertFalse("/vendor/bin/su",  new File("/vendor/bin/su").exists());
    }

    @RestrictedBuildTest
    public void testNoSuInPath() {
        String path = System.getenv("PATH");
        if (path == null) {
            return;
        }
        String[] elems = path.split(":");
        for (String i : elems) {
            File f = new File(i, "su");
            assertFalse(f.getAbsolutePath() + " exists", f.exists());
        }
    }

    public void testNoEnableRoot() throws UnsupportedEncodingException {
        byte[] badPattern = "enable_root".getBytes("US-ASCII");
        assertFileDoesNotContain("/system/bin/adb", badPattern);
    }

    private static void assertFileDoesNotContain(String filename, byte[] pattern) {
        try {
            File f = new File(filename);
            byte[] fileData = new byte[(int) f.length()];
            DataInputStream dis = new DataInputStream(new FileInputStream(f));
            dis.readFully(fileData);
            dis.close();

            outer:
            for (int i = 0; i < (fileData.length - pattern.length); i++) {
                for (int j = 0; j < pattern.length; j++) {
                    if (fileData[i+j] != pattern[j]) {
                        continue outer;
                    }
                }
                fail("Found banned pattern in " + filename);
            }

        } catch (IOException e) {
            // ignore - no such file, or IO error. Assume OK.
        }
    }

    /**
     * setuid or setgid "ip" command can be used to modify the
     * routing tables of a device, potentially allowing a malicious
     * program to intercept all network traffic to and from
     * the device.
     */
    public void testNoSetuidIp() {
        assertNotSetugid("/system/bin/ip");
        assertNotSetugid("/system/xbin/ip");
        assertNotSetugid("/vendor/bin/ip");
    }

    /**
     * setuid or setgid tcpdump can be used maliciously to monitor
     * all traffic in and out of the device.
     */
    public void testNoSetuidTcpdump() {
        assertNotSetugid("/system/bin/tcpdump");
        assertNotSetugid("/system/bin/tcpdump-arm");
        assertNotSetugid("/system/xbin/tcpdump");
        assertNotSetugid("/system/xbin/tcpdump-arm");
        assertNotSetugid("/vendor/bin/tcpdump");
        assertNotSetugid("/vendor/bin/tcpdump-arm");
    }

    private static void assertNotSetugid(String file) {
        FileUtils.FileStatus fs = new FileUtils.FileStatus();
        if (!FileUtils.getFileStatus(file, fs, false)) {
            return;
        }
        assertTrue("File \"" + file + "\" is setUID", (fs.mode & FileUtils.S_ISUID) == 0);
        assertTrue("File \"" + file + "\" is setGID", (fs.mode & FileUtils.S_ISGID) == 0);
    }

    /**
     * Detect "rootmydevice" vulnerability
     *
     * References:
     *
     * http://www.theregister.co.uk/2016/05/09/allwinners_allloser_custom_kernel_has_a_nasty_root_backdoor/
     */
    public void testNoSunxiDebug() {
        assertFalse("/proc/sunxi_debug/sunxi_debug", new File("/proc/sunxi_debug/sunxi_debug").exists());
    }
}
