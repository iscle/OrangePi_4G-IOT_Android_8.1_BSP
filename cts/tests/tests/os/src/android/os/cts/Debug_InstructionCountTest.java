/*
 * Copyright (C) 2010 The Android Open Source Project
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


import android.os.Debug;

import junit.framework.TestCase;

public class Debug_InstructionCountTest extends TestCase {

    public void testDebugInstructionCount() {
        Debug.InstructionCount instructionCount = new Debug.InstructionCount();

        assertFalse(instructionCount.resetAndStart());
        assertFalse(instructionCount.collect());
        assertEquals(0, instructionCount.globalTotal());
        assertEquals(0, instructionCount.globalMethodInvocations());
    }
}
