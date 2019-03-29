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

import android.renderscript.Allocation;
import android.renderscript.Element;
import android.renderscript.Script;
import android.renderscript.Type;
import android.renderscript.Type.Builder;

public class SingleSourceForEachTest extends RSBaseCompute {

    private static final int X = 1024;
    private static final int Y = 768;

    private Allocation testInputAlloc;
    private Allocation testInputAlloc2;
    private Allocation testOutputAlloc;
    private Allocation baselineOutputAlloc;
    private int testInputArray[];
    private int testInputArray2[];
    private int testOutputArray[];
    private int baselineOutputArray[];
    private ScriptC_single_source_script s;

    @Override
    protected void setUp() throws Exception {
        super.setUp();

        Type.Builder i32TypeBuilder = new Type.Builder(mRS, Element.I32(mRS));
        i32TypeBuilder.setX(X).setY(Y);
        testInputAlloc = Allocation.createTyped(mRS, i32TypeBuilder.create());
        testInputAlloc2 = Allocation.createTyped(mRS, i32TypeBuilder.create());
        testOutputAlloc = Allocation.createTyped(mRS, i32TypeBuilder.create());
        baselineOutputAlloc = Allocation.createTyped(mRS, i32TypeBuilder.create());

        testInputArray = new int[X * Y];
        testInputArray2 = new int[X * Y];
        testOutputArray = new int[X * Y];
        baselineOutputArray = new int[X * Y];

        s = new ScriptC_single_source_script(mRS);

        RSUtils.genRandomInts(0x900d5eed, testInputArray, true, 32);
        testInputAlloc.copyFrom(testInputArray);

        for (int i = 0; i < testInputArray2.length; i++) {
            testInputArray2[i] = i + 1;
        }
        testInputAlloc2.copyFrom(testInputArray2);
    }

    @Override
    protected void tearDown() throws Exception {
        testInputAlloc.destroy();
        testInputAlloc2.destroy();
        testOutputAlloc.destroy();
        baselineOutputAlloc.destroy();
        s.destroy();
        super.tearDown();
    }

    public void testSingleInputKernelLaunch() {
        s.forEach_foo(testInputAlloc, baselineOutputAlloc);
        s.invoke_testSingleInput(testInputAlloc, testOutputAlloc);
        testOutputAlloc.copyTo(testOutputArray);
        baselineOutputAlloc.copyTo(baselineOutputArray);
        checkArray(baselineOutputArray, testOutputArray, Y, X, X);
    }

    public void testMultiInputKernelLaunch() {
        s.forEach_goo(testInputAlloc, testInputAlloc2,
                      baselineOutputAlloc);
        s.invoke_testMultiInput(testInputAlloc, testInputAlloc2,
                                testOutputAlloc);
        testOutputAlloc.copyTo(testOutputArray);
        baselineOutputAlloc.copyTo(baselineOutputArray);
        checkArray(baselineOutputArray, testOutputArray, Y, X, X);
    }

    public void testKernelLaunchWithOptions() {
        // Initialize the testOutputAlloc and baselineOutputAlloc to be the same
        // as the input. When we the check the entire output later, the lower half
        // of the output Allocations should also be the same.
        baselineOutputAlloc.copyFrom(testInputArray);
        testOutputAlloc.copyFrom(testInputArray);

        Script.LaunchOptions sc = new Script.LaunchOptions();
        sc.setX(0, X);
        sc.setY(0, Y / 2);
        s.forEach_foo(testInputAlloc, baselineOutputAlloc, sc);
        s.invoke_testLaunchOptions(testInputAlloc, testOutputAlloc, X, Y);
        testOutputAlloc.copyTo(testOutputArray);
        baselineOutputAlloc.copyTo(baselineOutputArray);
        checkArray(baselineOutputArray, testOutputArray, Y, X, X);
    }

    public void testAllocationlessKernelLaunch() {
        baselineOutputAlloc.copyFrom(testInputArray);
        testOutputAlloc.copyFrom(testInputArray);

        Script.LaunchOptions sc = new Script.LaunchOptions();
        sc.setX(0, X);
        sc.setY(0, Y);
        s.set_gAllocOut(baselineOutputAlloc);
        s.forEach_bar(sc);

        s.invoke_testAllocationlessLaunch(testOutputAlloc, X, Y);

        testOutputAlloc.copyTo(testOutputArray);
        baselineOutputAlloc.copyTo(baselineOutputArray);
        checkArray(baselineOutputArray, testOutputArray, Y, X, X);
    }

    public void testConsistency() {
        s.invoke_testConsistency(testInputAlloc, testOutputAlloc);
        mRS.finish();
        waitForMessage();
        checkForErrors();
    }
}
