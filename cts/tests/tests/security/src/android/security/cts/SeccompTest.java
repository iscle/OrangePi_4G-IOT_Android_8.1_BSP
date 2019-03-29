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

package android.security.cts;

import android.test.AndroidTestCase;

import com.android.compatibility.common.util.CpuFeatures;

import junit.framework.TestCase;

/**
 * Verify that the seccomp policy is enforced
 */
public class SeccompTest extends AndroidTestCase {

    static {
        System.loadLibrary("ctssecurity_jni");
    }

    public void testCTSSyscallBlocked() {
        if (CpuFeatures.isArm64Cpu()) {
            testBlocked(217); // __NR_add_key
            testBlocked(219); // __NR_keyctl
            testAllowed(56); // __NR_openat

            // b/35034743 - do not remove test without reading bug
            testAllowed(267); // __NR_fstatfs64
        } else if (CpuFeatures.isArmCpu()) {
            testBlocked(309); // __NR_add_key
            testBlocked(311); // __NR_keyctl
            testAllowed(322); // __NR_openat

            // b/35906875 - do not remove test without reading bug
            testAllowed(316); // __NR_inotify_init
        } else if (CpuFeatures.isX86_64Cpu()) {
            testBlocked(248); // __NR_add_key
            testBlocked(250); // __NR_keyctl
            testAllowed(257); // __NR_openat
        } else if (CpuFeatures.isX86Cpu()) {
            testBlocked(286); // __NR_add_key
            testBlocked(288); // __NR_keyctl
            testAllowed(295); // __NR_openat
        } else if (CpuFeatures.isMips64Cpu()) {
            testBlocked(5239); // __NR_add_key
            testBlocked(5241); // __NR_keyctl
            testAllowed(5247); // __NR_openat
        } else if (CpuFeatures.isMipsCpu()) {
            testBlocked(4280); // __NR_add_key
            testBlocked(4282); // __NR_keyctl
            testAllowed(4288); // __NR_openat
        } else {
            fail("Unsupported OS");
        }
    }

    public void testCTSSwapOnOffBlocked() {
        if (CpuFeatures.isArm64Cpu()) {
            testBlocked(224); // __NR_swapon
            testBlocked(225); // __NR_swapoff
        } else if (CpuFeatures.isArmCpu()) {
            testBlocked(87);  // __NR_swapon
            testBlocked(115); // __NR_swapoff
        } else if (CpuFeatures.isX86_64Cpu()) {
            testBlocked(167); // __NR_swapon
            testBlocked(168); // __NR_swapoff
        } else if (CpuFeatures.isX86Cpu()) {
            testBlocked(87);  // __NR_swapon
            testBlocked(115); // __NR_swapoff
        } else if (CpuFeatures.isMips64Cpu()) {
            testBlocked(5162); // __NR_swapon
            testBlocked(5163); // __NR_swapoff
        } else if (CpuFeatures.isMipsCpu()) {
            testBlocked(4087); // __NR_swapon
            testBlocked(4115); // __NR_swapoff
        } else {
            fail("Unsupported OS");
        }
    }

    private void testBlocked(int nr) {
        assertTrue("Syscall " + nr + " allowed", testSyscallBlocked(nr));
    }

    private void testAllowed(int nr) {
        assertFalse("Syscall " + nr + " blocked", testSyscallBlocked(nr));
    }

    private static final native boolean testSyscallBlocked(int nr);
}
