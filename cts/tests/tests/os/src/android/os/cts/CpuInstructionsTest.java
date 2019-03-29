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


package android.os.cts;

import android.os.cts.CpuInstructions;

import com.android.compatibility.common.util.CpuFeatures;

import junit.framework.TestCase;

public class CpuInstructionsTest extends TestCase {

    public void testArmVirtualCounter() {
        if (!CpuFeatures.isArm64Cpu()) {
            return;
        }

        assertTrue("Machine does not allow access to CNTVCT register",
                CpuInstructions.canReadCntvct());
    }

    public void testEmulatedArmCpuFeatures() {
        if (!CpuFeatures.isArm64CpuIn32BitMode()) {
            return;
        }

        assertTrue("Machine does not support ARM swp instruction emulation",
                CpuInstructions.hasSwp());
        assertTrue("Machine does not support ARM setend instruction emulation",
                CpuInstructions.hasSetend());
        assertTrue("Machine does not support ARM CP15 barrier emulation",
                CpuInstructions.hasCp15Barriers());
    }
}
