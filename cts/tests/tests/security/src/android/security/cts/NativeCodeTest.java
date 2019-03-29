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

import junit.framework.TestCase;

@SecurityTest
public class NativeCodeTest extends TestCase {

    static {
        System.loadLibrary("ctssecurity_jni");
    }

    @SecurityTest
    public void testPerfEvent() throws Exception {
        assertFalse("Device is vulnerable to CVE-2013-2094. Please apply security patch "
                    + "at http://git.kernel.org/cgit/linux/kernel/git/torvalds/linux.git/"
                    + "commit/?id=8176cced706b5e5d15887584150764894e94e02f",
                    doPerfEventTest());
    }

    @SecurityTest
    public void testPerfEvent2() throws Exception {
        assertTrue(doPerfEventTest2());
    }

    @SecurityTest
    public void testFutex() throws Exception {
        assertTrue("Device is vulnerable to CVE-2014-3153, a vulnerability in the futex() system "
                   + "call. Please apply the security patch at "
                   + "https://git.kernel.org/cgit/linux/kernel/git/torvalds/linux.git/commit/"
                   + "?id=e9c243a5a6de0be8e584c604d353412584b592f8",
                   doFutexTest());
    }

    @SecurityTest
    public void testNvmapIocFromId() throws Exception {
        assertTrue("Device is vulnerable to CVE-2014-5332. "
                   + "NVIDIA has released code fixes to upstream repositories and device vendors. "
                   + "For more information, see "
                   + "https://nvidia.custhelp.com/app/answers/detail/a_id/3618",
                   doNvmapIocFromIdTest());
    }

    @SecurityTest
    public void testPingPongRoot() throws Exception {
        assertTrue("Device is vulnerable to CVE-2015-3636, a vulnerability in the ping "
                   + "socket implementation. Please apply the security patch at "
                   + "https://github.com/torvalds/linux/commit/a134f083e79f",
                   doPingPongRootTest());
    }

    @SecurityTest
    public void testPipeReadV() throws Exception {
        assertTrue("Device is vulnerable to CVE-2015-1805 and/or CVE-2016-0774,"
                   + " a vulnerability in the pipe_read() function."
                   + " Please apply the following patches:\n"
                   + "https://git.kernel.org/cgit/linux/kernel/git/stable/linux-stable.git/commit/?id=75cf667b7fac08a7b21694adca7dff07361be68a\n"
                   + "https://git.kernel.org/cgit/linux/kernel/git/stable/linux-stable.git/commit/?id=feae3ca2e5e1a8f44aa6290255d3d9709985d0b2\n",
                   doPipeReadVTest());
    }

    @SecurityTest
    public void testSysVipc() throws Exception {
        assertTrue("Android does not support Sys V IPC, it must "
                   + "be removed from the kernel. In the kernel config: "
                   + "Change \"CONFIG_SYSVIPC=y\" to \"# CONFIG_SYSVIPC is not set\" "
                   + "and rebuild.",
                   doSysVipcTest());
    }

    /**
     * Returns true iff this device is vulnerable to CVE-2013-2094.
     * A patch for CVE-2013-2094 can be found at
     * http://git.kernel.org/cgit/linux/kernel/git/torvalds/linux.git/commit/?id=8176cced706b5e5d15887584150764894e94e02f
     */
    private static native boolean doPerfEventTest();

    /**
     * CVE-2013-4254
     *
     * Verifies that
     * http://git.kernel.org/cgit/linux/kernel/git/torvalds/linux.git/commit/?id=c95eb3184ea1a3a2551df57190c81da695e2144b
     * is applied to the system. Returns true if the patch is applied,
     * and crashes the system otherwise.
     *
     * While you're at it, please also apply the following patch:
     * http://git.kernel.org/cgit/linux/kernel/git/torvalds/linux.git/commit/?id=b88a2595b6d8aedbd275c07dfa784657b4f757eb
     *
     * Credit: https://github.com/deater/perf_event_tests/blob/master/exploits/arm_perf_exploit.c
     */
    private static native boolean doPerfEventTest2();

    @SecurityTest
    public void testCVE20141710() throws Exception {
        assertTrue("Device is vulnerable to CVE-2014-1710", doCVE20141710Test());
    }

    /**
     * ANDROID-15455425 / CVE-2014-3153
     *
     * Returns true if the device is patched against the futex() system call vulnerability.
     *
     * More information on this vulnerability is at http://seclists.org/oss-sec/2014/q2/467 and
     * the patch is at:
     * https://git.kernel.org/cgit/linux/kernel/git/torvalds/linux.git/commit/?id=e9c243a5a6de0be8e584c604d353412584b592f8
     */
    private static native boolean doFutexTest();

    /**
     * ANDROID-17453812 / CVE-2014-5332
     *
     * Returns true if the device is patched against the NVMAP_IOC_FROM_ID ioctl call.
     *
     * More information on this vulnreability is available at
     * https://nvidia.custhelp.com/app/answers/detail/a_id/3618
     */
    private static native boolean doNvmapIocFromIdTest();

    /**
     * Returns true if the device is immune to CVE-2014-1710,
     * false if the device is vulnerable.
     */
    private static native boolean doCVE20141710Test();

    /**
     * CVE-2015-3636
     *
     * Returns true if the patch is applied, crashes the system otherwise.
     *
     * Detects if the following patch is present.
     * https://github.com/torvalds/linux/commit/a134f083e79f
     *
     * Credit: Wen Xu and wushi of KeenTeam.
     * http://seclists.org/oss-sec/2015/q2/333
     */
    private static native boolean doPingPongRootTest();

    /**
     * CVE-2015-1805 and CVE-2016-0774
     *
     * Returns true if the patches are applied, crashes the system otherwise.
     *
     * Detects if the following patches are present.
     * https://git.kernel.org/cgit/linux/kernel/git/stable/linux-stable.git/commit/?id=75cf667b7fac08a7b21694adca7dff07361be68a
     * https://git.kernel.org/cgit/linux/kernel/git/stable/linux-stable.git/commit/?id=feae3ca2e5e1a8f44aa6290255d3d9709985d0b2
     *
     * b/27275324 and b/27721803
     */
    private static native boolean doPipeReadVTest();

    /**
     * Test that SysV IPC has been removed from the kernel.
     *
     * Returns true if SysV IPC has been removed.
     *
     * System V IPCs are not compliant with Android's application lifecycle because allocated
     * resources are not freed by the low memory killer. This lead to global kernel resource leakage.
     *
     * For example, there is no way to automatically release a SysV semaphore
     * allocated in the kernel when:
     * - a buggy or malicious process exits
     * - a non-buggy and non-malicious process crashes or is explicitly killed.
     *
     * Killing processes automatically to make room for new ones is an
     * important part of Android's application lifecycle implementation. This means
     * that, even assuming only non-buggy and non-malicious code, it is very likely
     * that over time, the kernel global tables used to implement SysV IPCs will fill
     * up.
     */
    private static native boolean doSysVipcTest();

}
