/*
 * Copyright (C) 2015-2016 The Android Open Source Project
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

package android.renderscript.cts;

import android.util.Log;
import android.renderscript.Allocation;
import android.renderscript.Element;
import android.renderscript.Script;
import android.renderscript.Type;
import android.renderscript.Type.Builder;

public class SingleSourceAllocationTest extends RSBaseCompute {

    private static final String TAG = "SingleSourceAllocationTest";

    private ScriptC_single_source_alloc s;
    private static final int dimX = 3;
    private static final int dimY = 4;
    private static final int dimZ = 5;
    private static final int start = 23;

    @Override
    protected void setUp() throws Exception {
        super.setUp();

        s = new ScriptC_single_source_alloc(mRS);
        s.set_gDimX(dimX);
        s.set_gDimY(dimY);
        s.set_gDimZ(dimZ);
        s.set_gStart(start);
    }

    @Override
    protected void tearDown() throws Exception {
        s.destroy();
        super.tearDown();
    }

    public void testElementCreation() {
        s.invoke_TestElementCreation();
        waitForMessage();
        checkForErrors();
    }

    public void testTypeCreation() {
        s.invoke_TestTypeCreation();
        waitForMessage();
        checkForErrors();
    }

    public void testAllocationCreationWithUsage() {
        s.invoke_TestAllocationCreationWithUsage();
        waitForMessage();
        checkForErrors();
    }

    public void testAllocationCreationHelperFunctions() {
        s.invoke_TestHelperFunctions();
        waitForMessage();
        checkForErrors();
    }

    public void testAllocationCreationAndAccess() {
        s.invoke_TestAllocationCreationAndAccess();
        waitForMessage();
        checkForErrors();
    }
}
