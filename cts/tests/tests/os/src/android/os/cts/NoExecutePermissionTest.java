/*
 * Copyright (C) 2011 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package android.os.cts;

import com.android.compatibility.common.util.CpuFeatures;

import java.io.File;
import java.io.FileNotFoundException;
import java.util.Scanner;

import junit.framework.TestCase;

/**
 * {@link TestCase} that checks that the NX (No Execute) feature is enabled. This feature makes it
 * harder to perform attacks against Android by marking certain data blocks as non-executable.
 */
public class NoExecutePermissionTest extends TestCase {

    static {
        System.loadLibrary("ctsos_jni");
    }

    public void testNoExecuteStack() {
        if (!cpuHasNxSupport()) {
            return;
        }
        assertFalse(isStackExecutable());
    }

    public void testNoExecuteHeap() {
        if (!cpuHasNxSupport()) {
            return;
        }
        assertFalse(isHeapExecutable());
    }

    public void testExecuteCode() {
        assertTrue(isMyCodeExecutable());
    }

    private static boolean cpuHasNxSupport() {
        if (CpuFeatures.isArmCpu() && !CpuFeatures.isArm7Compatible()) {
            // ARM processors before v7 do not have NX support.
            // http://code.google.com/p/android/issues/detail?id=17328
            return false;
        }
        if (CpuFeatures.isMipsCpu()) {
            // MIPS processors do not have NX support.
            return false;
        }

        // TODO: handle other processors.  For now, assume those processors
        // have NX support.
        return true;
    }

    private static native boolean isStackExecutable();
    private static native boolean isHeapExecutable();
    private static native boolean isMyCodeExecutable();
}
