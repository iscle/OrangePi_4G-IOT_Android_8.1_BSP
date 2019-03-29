/*
 * Copyright (C) 2011-2012 The Android Open Source Project
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

import android.graphics.Bitmap;
import android.renderscript.Allocation;
import android.renderscript.AllocationAdapter;
import android.renderscript.Allocation.MipmapControl;
import android.renderscript.Element;
import android.renderscript.RSIllegalArgumentException;
import android.renderscript.RSInvalidStateException;
import android.renderscript.Type;
import android.renderscript.Type.Builder;

import android.renderscript.ScriptIntrinsicColorMatrix;
import android.renderscript.ScriptIntrinsicConvolve3x3;
import android.renderscript.ScriptGroup;
import android.renderscript.Matrix4f;
import android.util.Log;

public class ScriptGroupTest extends RSBaseCompute {

    private static final String TAG = "ScriptGroupTest";
    private static final int ARRAY_SIZE = 256;
    static int bDimX = 48;
    static int bDimY = 8;

    public void testScriptGroupSingleKernel() {
        ScriptGroup group;

        Type connect = new Type.Builder(mRS, Element.U8_4(mRS)).setX(bDimX).setY(bDimY).create();

        ScriptIntrinsicColorMatrix mColorMatrix;

        mColorMatrix = ScriptIntrinsicColorMatrix.create(mRS, Element.U8_4(mRS));

        Allocation a1_copy, a2_copy;
        a1_copy = Allocation.createTyped(mRS, connect);
        a2_copy = Allocation.createTyped(mRS, connect);

        Matrix4f m = new Matrix4f();
        m.set(1, 0, 0.2f);
        m.set(1, 1, 0.9f);
        m.set(1, 2, 0.2f);
        mColorMatrix.setColorMatrix(m);

        ScriptGroup.Builder b = new ScriptGroup.Builder(mRS);
        b.addKernel(mColorMatrix.getKernelID());
        group = b.create();

        group.setInput(mColorMatrix.getKernelID(), a1_copy);
        group.setOutput(mColorMatrix.getKernelID(), a2_copy);

        group.execute();

        mColorMatrix.destroy();
        a1_copy.destroy();
        a2_copy.destroy();
        group.destroy();
    }

    public void testScriptGroupDisconnectedKernel() {
        ScriptGroup group = null;

        Type connect = new Type.Builder(mRS, Element.U8_4(mRS)).setX(bDimX).setY(bDimY).create();

        ScriptIntrinsicColorMatrix mColorMatrix, mColorMatrix2;

        mColorMatrix = ScriptIntrinsicColorMatrix.create(mRS, Element.U8_4(mRS));
        mColorMatrix2 = ScriptIntrinsicColorMatrix.create(mRS, Element.U8_4(mRS));

        Allocation a1_copy, a2_copy;

        a1_copy = Allocation.createTyped(mRS, connect);
        a2_copy = Allocation.createTyped(mRS, connect);

        Matrix4f m = new Matrix4f();
        m.set(1, 0, 0.2f);
        m.set(1, 1, 0.9f);
        m.set(1, 2, 0.2f);
        mColorMatrix.setColorMatrix(m);
        mColorMatrix2.setColorMatrix(m);

        ScriptGroup.Builder b = new ScriptGroup.Builder(mRS);
        b.addKernel(mColorMatrix.getKernelID());
        b.addKernel(mColorMatrix2.getKernelID());
        try {
            group = b.create();
            fail("should throw RSInvalidStateException.");
        } catch (RSInvalidStateException e) {

        }

        mColorMatrix.destroy();
        mColorMatrix2.destroy();
        a1_copy.destroy();
        a2_copy.destroy();
        if (group != null) {
            group.destroy();
        }
    }


    public void testScriptGroupFieldConnection() {
        ScriptGroup group;

        Type connect = new Type.Builder(mRS, Element.U8_4(mRS)).setX(bDimX).setY(bDimY).create();

        ScriptIntrinsicConvolve3x3 mConvolve3x3;
        ScriptIntrinsicColorMatrix mColorMatrix;

        mConvolve3x3 = ScriptIntrinsicConvolve3x3.create(mRS, Element.U8_4(mRS));
        mColorMatrix = ScriptIntrinsicColorMatrix.create(mRS, Element.U8_4(mRS));

        Allocation a1_copy, a2_copy;
        a1_copy = Allocation.createTyped(mRS, connect);
        a2_copy = Allocation.createTyped(mRS, connect);

        float f[] = new float[9];
        f[0] =  0.f;    f[1] = -1.f;    f[2] =  0.f;
        f[3] = -1.f;    f[4] =  5.f;    f[5] = -1.f;
        f[6] =  0.f;    f[7] = -1.f;    f[8] =  0.f;

        mConvolve3x3.setCoefficients(f);

        Matrix4f m = new Matrix4f();
        m.set(1, 0, 0.2f);
        m.set(1, 1, 0.9f);
        m.set(1, 2, 0.2f);
        mColorMatrix.setColorMatrix(m);

        ScriptGroup.Builder b = new ScriptGroup.Builder(mRS);
        b.addKernel(mColorMatrix.getKernelID());
        b.addKernel(mConvolve3x3.getKernelID());
        b.addConnection(connect, mColorMatrix.getKernelID(), mConvolve3x3.getFieldID_Input());
        group = b.create();

        group.setInput(mColorMatrix.getKernelID(), a1_copy);
        group.setOutput(mConvolve3x3.getKernelID(), a2_copy);

        group.execute();

        mConvolve3x3.destroy();
        mColorMatrix.destroy();
        a1_copy.destroy();
        a2_copy.destroy();
        group.destroy();
    }

    public void testScriptGroupDisconnectedDAG() {
        ScriptGroup group = null;

        Type connect = new Type.Builder(mRS, Element.U8_4(mRS)).setX(bDimX).setY(bDimY).create();

        ScriptIntrinsicConvolve3x3 mConvolve3x3, mConvolve3x32;
        ScriptIntrinsicColorMatrix mColorMatrix, mColorMatrix2;

        mConvolve3x3 = ScriptIntrinsicConvolve3x3.create(mRS, Element.U8_4(mRS));
        mConvolve3x32 = ScriptIntrinsicConvolve3x3.create(mRS, Element.U8_4(mRS));
        mColorMatrix = ScriptIntrinsicColorMatrix.create(mRS, Element.U8_4(mRS));
        mColorMatrix2 = ScriptIntrinsicColorMatrix.create(mRS, Element.U8_4(mRS));

        Allocation a1_copy, a2_copy;
        a1_copy = Allocation.createTyped(mRS, connect);
        a2_copy = Allocation.createTyped(mRS, connect);

        float f[] = new float[9];
        f[0] =  0.f;    f[1] = -1.f;    f[2] =  0.f;
        f[3] = -1.f;    f[4] =  5.f;    f[5] = -1.f;
        f[6] =  0.f;    f[7] = -1.f;    f[8] =  0.f;

        mConvolve3x3.setCoefficients(f);
        mConvolve3x32.setCoefficients(f);

        Matrix4f m = new Matrix4f();
        m.set(1, 0, 0.2f);
        m.set(1, 1, 0.9f);
        m.set(1, 2, 0.2f);
        mColorMatrix.setColorMatrix(m);
        mColorMatrix2.setColorMatrix(m);

        ScriptGroup.Builder b = new ScriptGroup.Builder(mRS);
        b.addKernel(mColorMatrix.getKernelID());
        b.addKernel(mColorMatrix2.getKernelID());
        b.addKernel(mConvolve3x3.getKernelID());
        b.addKernel(mConvolve3x32.getKernelID());
        b.addConnection(connect, mColorMatrix.getKernelID(), mConvolve3x3.getFieldID_Input());
        b.addConnection(connect, mColorMatrix2.getKernelID(), mConvolve3x32.getFieldID_Input());
        try {
            group = b.create();
            fail("RSInvalidStateException expected");
        } catch (RSInvalidStateException e) {

        }

        mConvolve3x3.destroy();
        mConvolve3x32.destroy();
        mColorMatrix.destroy();
        mColorMatrix2.destroy();
        a1_copy.destroy();
        a2_copy.destroy();
        if (group != null) {
            group.destroy();
        }
    }

    public void testScriptGroupTorture() {
        ScriptGroup group;

        int[] result = new int[1];

        bDimX = 1;

        Type connect = new Type.Builder(mRS, Element.I32(mRS)).setX(bDimX).create();
        Type compareType = new Type.Builder(mRS, Element.I32(mRS)).create();

        ScriptC_scriptgroup node1, node2, node3, node4, node5, compare;
        node1 = new ScriptC_scriptgroup(mRS);
        node2 = new ScriptC_scriptgroup(mRS);
        node3 = new ScriptC_scriptgroup(mRS);
        node4 = new ScriptC_scriptgroup(mRS);
        node5 = new ScriptC_scriptgroup(mRS);

        compare = new ScriptC_scriptgroup(mRS);

        Allocation in1, in2, out, resultAlloc;
        in1 = Allocation.createTyped(mRS, connect);
        in2 = Allocation.createTyped(mRS, connect);

        out = Allocation.createTyped(mRS, connect);
        resultAlloc = Allocation.createTyped(mRS, compareType);

        node1.set_memset_toValue(1);
        node1.forEach_memset(in1);
        node1.set_memset_toValue(2);
        node1.forEach_memset(in2);

        node1.set_arith_operation(2);
        node2.set_arith_operation(1);
        node3.set_arith_operation(0);
        node4.set_arith_operation(0);
        node5.set_arith_operation(1);

        node3.set_arith_use_rs_allocation(1);
        node4.set_arith_use_rs_allocation(1);

        node1.set_arith_value(5);
        node2.set_arith_value(3);
        node5.set_arith_value(7);

        ScriptGroup.Builder b = new ScriptGroup.Builder(mRS);
        b.addKernel(node1.getKernelID_arith());
        b.addKernel(node2.getKernelID_arith());
        b.addKernel(node3.getKernelID_arith());
        b.addKernel(node4.getKernelID_arith());
        b.addKernel(node5.getKernelID_arith());

        b.addConnection(connect, node1.getKernelID_arith(), node2.getKernelID_arith());
        b.addConnection(connect, node1.getKernelID_arith(), node3.getFieldID_arith_rs_input());
        b.addConnection(connect, node2.getKernelID_arith(), node4.getFieldID_arith_rs_input());
        b.addConnection(connect, node3.getKernelID_arith(), node4.getKernelID_arith());
        b.addConnection(connect, node4.getKernelID_arith(), node5.getKernelID_arith());

        group = b.create();
        group.setInput(node1.getKernelID_arith(), in1);
        group.setInput(node3.getKernelID_arith(), in2);

        group.setOutput(node5.getKernelID_arith(), out);

        group.execute();

        mRS.finish();

        compare.set_compare_value(2);
        compare.forEach_compare(out);
        compare.forEach_getCompareResult(resultAlloc);
        resultAlloc.copyTo(result);

        node1.destroy();
        node2.destroy();
        node3.destroy();
        node4.destroy();
        node5.destroy();
        in1.destroy();
        in2.destroy();
        out.destroy();
        resultAlloc.destroy();

        assertTrue(result[0] == 2);
    }

    /**
     * Tests a case where a shared global variable is updated by the first kernel in a group,
     * but then read by a subsequent kernel.
     *
     * The test ensures that we don't accidentally apply any fusion optimizations to the kernel
     * pair, since there is a potential dependency that crosses the kernel cell boundary.
     */
    public void testScriptGroupSharedGlobal() {
        Type i32 = new Type.Builder(mRS, Element.I32(mRS)).setX(1).create();
        Type u32 = new Type.Builder(mRS, Element.U32(mRS)).setX(2).create();

        Allocation aFailed = Allocation.createTyped(mRS, i32);
        Allocation aSharedInt = Allocation.createTyped(mRS, i32);

        ScriptC_group1 mG1 = new ScriptC_group1(mRS);
        ScriptC_group2 mG2 = new ScriptC_group2(mRS);

        mG1.set_aSharedInt(aSharedInt);
        mG2.set_aSharedInt(aSharedInt);
        mG2.set_aFailed(aFailed);

        int [] Failed = new int [1];
        Failed[0] = 0;
        aFailed.copyFrom(Failed);

        ScriptGroup.Builder b = new ScriptGroup.Builder(mRS);

        // Writes to aSharedInt[x] in the kernel.
        b.addKernel(mG1.getKernelID_setSharedInt());
        // Reads aSharedInt[1] to verify it is -5.
        b.addKernel(mG2.getKernelID_getSharedInt());
        // If we fuse mG1/mG2, we won't see the update to the aSharedInt[1] during mG2 for x == 0.
        // The update is only visible if we correctly identify the dependency and execute all of
        // mG1 before starting on mG2.
        b.addConnection(u32, mG1.getKernelID_setSharedInt(), mG2.getKernelID_getSharedInt());
        ScriptGroup group = b.create();
        group.execute();

        mG2.invoke_verify();
        aFailed.copyTo(Failed);
        if (Failed[0] != 0) {
            FoundError = true;
        }

        aFailed.destroy();
        aSharedInt.destroy();
        mG1.destroy();
        mG2.destroy();
        group.destroy();

        checkForErrors();
    }

    /**
     * Tests that kernel-to-kernel dependency via input/output is handled correctly
     */
    public void testBuilder2PointWiseKernelToKernelDependency() {
        ScriptC_increment s_inc = new ScriptC_increment(mRS);
        ScriptC_double s_double = new ScriptC_double(mRS);
        mRS.setMessageHandler(mRsMessage);

        int[] array = new int[ARRAY_SIZE * 4];

        for (int i = 0; i < ARRAY_SIZE * 4; i++) {
            array[i] = i;
        }

        Allocation input = Allocation.createSized(mRS, Element.I32_4(mRS), ARRAY_SIZE);
        input.copyFrom(array);

        ScriptGroup.Builder2 builder = new ScriptGroup.Builder2(mRS);

        ScriptGroup.Input unbound = builder.addInput();

        Type connectType = Type.createX(mRS, Element.I32_4(mRS), ARRAY_SIZE);

        ScriptGroup.Closure c0 =
                builder.addKernel(s_inc.getKernelID_increment(),
                                  connectType,
                                  unbound);

        ScriptGroup.Closure c1 =
                builder.addKernel(s_double.getKernelID_doubleKernel(),
                                  connectType,
                                  c0.getReturn());

        ScriptGroup group = builder.create("IncAndDbl", c1.getReturn());

        int[] a = new int[ARRAY_SIZE * 4];
        ((Allocation)group.execute(input)[0]).copyTo(a);

        mRS.finish();

        input.destroy();
        group.destroy();

        boolean failed = false;
        for (int i = 0; i < ARRAY_SIZE * 4; i++) {
            if (a[i] != (i+1) * 2) {
                Log.e(TAG, "a["+i+"]="+a[i]+", should be "+ ((i+1) * 2));
                failed = true;
            }
        }

        assertTrue(!failed);
    }

    /**
     * Tests that kernel-to-kernel dependency via global allocations is handled correctly
     */
    public void testBuilder2GatherScatterAcrossKernelsViaGlobals() {
        ScriptC_reduction s = new ScriptC_reduction(mRS);

        int[] array = new int[ARRAY_SIZE * 4];

        for (int i = 0; i < ARRAY_SIZE; i++) {
            array[i*4] = i * 7;
            array[i*4 + 1] = i * 7;
            array[i*4 + 2] = i * 7;
            array[i*4 + 3] = i * 7;
        }

        Allocation input = Allocation.createSized(mRS, Element.I32_4(mRS), ARRAY_SIZE);
        input.copyFrom(array);

        ScriptGroup.Builder2 builder = new ScriptGroup.Builder2(mRS);

        ScriptGroup.Input unbound = builder.addInput();

        ScriptGroup.Closure c = null;
        ScriptGroup.Binding b2 = new ScriptGroup.Binding(s.getFieldID_a(), unbound);
        for (int stride = ARRAY_SIZE / 2; stride >= 1; stride >>= 1) {
            ScriptGroup.Binding b1 = new ScriptGroup.Binding(s.getFieldID_reduction_stride(),
                                                             stride);
            c = builder.addKernel(s.getKernelID_add(),
                                  Type.createX(mRS, Element.I32_4(mRS), stride),
                                  b1, b2);
            b2 = new ScriptGroup.Binding(s.getFieldID_a(), c.getReturn());
        }

        if (c == null) {
            return;
        }

        ScriptGroup group = builder.create("Summation", c.getReturn());

        int[] a = new int[4];
        ((Allocation)group.execute(input)[0]).copyTo(a);

        mRS.finish();

        input.destroy();
        group.destroy();

        boolean failed = false;
        for (int i = 0; i < 4; i++) {
            if (failed == false && a[i] != ARRAY_SIZE * (ARRAY_SIZE - 1) * 7 / 2) {
                Log.e(TAG,
                      "a["+i+"]="+a[i]+", should be "+ (ARRAY_SIZE * (ARRAY_SIZE - 1) * 7 / 2));
                failed = true;
            }
        }

        assertTrue(!failed);
    }

    /**
     * Tests that the kernel output to a global can be used as a future
     */
    public void testBuilder2KernelOutputToGlobal() {
        ScriptC_reduction s = new ScriptC_reduction(mRS);

        int[] array = new int[ARRAY_SIZE * 4];

        for (int i = 0; i < ARRAY_SIZE; i++) {
            array[i*4] = i;
            array[i*4 + 1] = i;
            array[i*4 + 2] = i;
            array[i*4 + 3] = i;
        }

        Allocation input = Allocation.createSized(mRS, Element.I32_4(mRS), ARRAY_SIZE);
        input.copyFrom(array);
        Allocation input1 = Allocation.createSized(mRS, Element.I32_4(mRS), ARRAY_SIZE);

        ScriptGroup.Builder2 builder = new ScriptGroup.Builder2(mRS);

        ScriptGroup.Input unbound = builder.addInput();

        ScriptGroup.Closure c = null;
        ScriptGroup.Binding b2 = new ScriptGroup.Binding(s.getFieldID_a(), unbound);
        for (int stride = ARRAY_SIZE / 2; stride >= 1; stride >>= 1) {
            ScriptGroup.Binding b1 = new ScriptGroup.Binding(s.getFieldID_reduction_stride(),
                                                             stride);
            c = builder.addKernel(s.getKernelID_add2(),
                                  Type.createX(mRS, Element.I32_4(mRS), stride),
                                  b1, b2);
            b2 = new ScriptGroup.Binding(s.getFieldID_a(),
                                         c.getGlobal(s.getFieldID_a()));
        }

        if (c == null) {
            return;
        }

        ScriptGroup group = builder.create("SummationGlobal", c.getGlobal(s.getFieldID_a()));

        int[] a = new int[4 * ARRAY_SIZE];
        ((Allocation)group.execute(input, input1)[0]).copyTo(a);

        mRS.finish();

        input.destroy();
        input1.destroy();
        group.destroy();

        boolean failed = false;
        for (int i = 0; i < 4; i++) {
            if (failed == false && a[i] != ARRAY_SIZE * (ARRAY_SIZE - 1) / 2) {
                Log.e(TAG,
                      "a["+i+"]="+a[i]+", should be "+ (ARRAY_SIZE * (ARRAY_SIZE - 1) / 2));
                failed = true;
            }
        }

        assertTrue(!failed);
    }

    /**
     * Tests that invoke-to-kernel dependency is handled correctly
     */
    public void testBuilder2InvokeToKernelDependency() {
        ScriptC_matrix s = new ScriptC_matrix(mRS);

        float[] array = new float[ARRAY_SIZE * 4];

        for (int i = 0; i < ARRAY_SIZE; i++) {
            array[i * 4] = i * 4 * 7;
            array[i * 4 + 1] = (i * 4 + 1) * 7;
            array[i * 4 + 2] = (i * 4 + 2) * 7;
            array[i * 4 + 3] = (i * 4 + 3) * 7;
        }

        Allocation input = Allocation.createSized(mRS, Element.F32_4(mRS), ARRAY_SIZE);
        input.copyFrom(array);

        ScriptGroup.Builder2 builder = new ScriptGroup.Builder2(mRS);

        ScriptGroup.Input unbound = builder.addInput();

        Matrix4f mat = new Matrix4f();

        mat.set(0, 0, 0.0f);
        mat.set(0, 1, 0.0f);
        mat.set(0, 2, 0.0f);
        mat.set(0, 3, 1.0f);

        mat.set(1, 0, 1.0f);
        mat.set(1, 1, 0.0f);
        mat.set(1, 2, 0.0f);
        mat.set(1, 3, 0.0f);

        mat.set(2, 0, 0.0f);
        mat.set(2, 1, 1.0f);
        mat.set(2, 2, 0.0f);
        mat.set(2, 3, 0.0f);

        mat.set(3, 0, 0.0f);
        mat.set(3, 1, 0.0f);
        mat.set(3, 2, 1.0f);
        mat.set(3, 3, 0.0f);

        ScriptGroup.Closure c1 =
                builder.addInvoke(s.getInvokeID_setMatrix(), mat);

        ScriptGroup.Closure c2 =
                builder.addKernel(s.getKernelID_multiply(),
                                  Type.createX(mRS, Element.F32_4(mRS), ARRAY_SIZE),
                                  unbound);

        ScriptGroup group = builder.create("Multiply", c2.getReturn());

        float[] a = new float[ARRAY_SIZE * 4];
        ((Allocation)group.execute(input)[0]).copyTo(a);

        mRS.finish();

        input.destroy();
        group.destroy();

        boolean failed = false;
        for (int i = 0; i < ARRAY_SIZE; i++) {
            for (int j = 0; j < 4; j++) {
                float expected = (i*4+((j+1)%4))*7;
                if (failed == false && a[i * 4 + j] != expected) {
                    Log.e(TAG, "a["+i+"]="+a[i]+", should be "+ expected);
                    failed = true;
                }
            }
        }

        assertTrue(!failed);
    }
}
