/*
 * Copyright (C) 2015 The Android Open Source Project
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

import java.util.Random;
import java.lang.Math;
import java.lang.Float;
import java.lang.Integer;
import java.lang.Short;

import android.content.Context;
import android.content.res.Resources;
import android.renderscript.Allocation;
import android.renderscript.cts.Float16Utils;
import android.renderscript.Element;
import android.renderscript.RenderScript;
import android.renderscript.RSRuntimeException;
import android.renderscript.Script;
import android.renderscript.Type;
import android.util.Log;

public class Float16ArithmeticTest extends RSBaseCompute {
    private int numInputs = Float16TestData.input.length;

    private ScriptC_float16_arithmetic script;

    // Allocations to hold float16 input and output
    private Allocation mInput;
    private Allocation mF16Matrix;
    private Allocation mU16Matrix;

    // A numInputs * numInputs length 1-D array with data copied from
    // mU16Matrix
    private short[] output = new short[numInputs * numInputs];

    // Create input, intermediate, and output allocations.  Copy input data to
    // the input allocation
    @Override
    protected void setUp() throws Exception {
        super.setUp();

        Element f16 = Element.F16(mRS);
        Element u16 = Element.U16(mRS);
        Type f16Matrix = Type.createXY(mRS, f16, numInputs, numInputs);
        Type u16Matrix = Type.createXY(mRS, u16, numInputs, numInputs);

        mInput = Allocation.createSized(mRS, f16, numInputs);
        mF16Matrix = Allocation.createTyped(mRS, f16Matrix);
        mU16Matrix = Allocation.createTyped(mRS, u16Matrix);

        mInput.copyFromUnchecked(Float16TestData.input);

        script = new ScriptC_float16_arithmetic(mRS);
    }

    @Override
    protected void tearDown() throws Exception {
        mInput.destroy();
        mF16Matrix.destroy();
        mU16Matrix.destroy();
        script.destroy();
        super.tearDown();
    }

    // Check the output of performing 'operation' on inputs x and y against the
    // reference output in refValues.  For special cases like Infinity, NaN and
    // zero, use exact comparison.  Otherwise, check if the output is within
    // the bounds in 'refValues' by converting all values to float.
    private boolean checkFloat16Output(int x, int y, short[][][] refValues,
                                       String operation)
    {
        // Find the input, output and reference values based on the indices
        short in1 = Float16TestData.input[x];
        short in2 = Float16TestData.input[y];
        short out = output[x + y * numInputs];
        short lb = refValues[x][y][0];
        short ub = refValues[x][y][1];

        // Do exact match if the reference value is a special case (Nan, zero
        // infinity or their negative equivalents).
        if (Float16Utils.isFloat16Infinite(lb))
            return lb == out;
        // NaN can have any non-zero mantissa.  Do not use equality check
        if (Float16Utils.isFloat16NaN(lb))
            return Float16Utils.isFloat16NaN(out);
        // If reference output is zero, test for exact equivalence if at least
        // one of the input values is a special-case FP16 value.
        if (Float16Utils.isFloat16Zero(lb)) {
            if (!Float16Utils.isFloat16FiniteNonZero(in1) ||
                !Float16Utils.isFloat16FiniteNonZero(in2)) {
                return lb == out;
            }
        }

        float floatLB = Float16Utils.convertFloat16ToFloat(lb);
        float floatUB = Float16Utils.convertFloat16ToFloat(ub);
        float floatOut = Float16Utils.convertFloat16ToFloat(out);

        if (floatOut < floatLB || floatOut > floatUB) {
            StringBuilder message = new StringBuilder();
            message.append("Incorrect output for float16 " + operation + ":");
            message.append("\nInput 1: " + Short.toString(in1));
            message.append("\nInput 2: " + Short.toString(in2));
            message.append("\nExpected output between: " + Short.toString(lb) +
                           " and " + Short.toString(ub));
            message.append("\nActual   output: " + Short.toString(out));
            message.append("\nExpected output (in float) between: " +
                           Float.toString(floatLB) + " and " + Float.toString(floatUB));
            message.append("\nActual   output: " + Float.toString(floatOut));
            assertTrue(message.toString(), false);
        }
        return true;
    }

    private boolean checkFloat16Add(int x, int y) {
        return checkFloat16Output(x, y, Float16TestData.ReferenceOutputForAdd,
                                  "addition");
    }

    public void testFloat16Add() {
        script.set_gInput(mInput);
        script.forEach_add(mF16Matrix);
        script.forEach_bitcast(mF16Matrix, mU16Matrix);
        mU16Matrix.copyTo(output);

        for (int x = 0; x < numInputs; x ++) {
            for (int y = 0; y < numInputs; y ++) {
                checkFloat16Add(x, y);
            }
        }
    }

    private boolean checkFloat16Sub(int x, int y) {
        return checkFloat16Output(x, y, Float16TestData.ReferenceOutputForSub,
                                  "subtraction");
    }

    public void testFloat16Sub() {
        script.set_gInput(mInput);
        script.forEach_sub(mF16Matrix);
        script.forEach_bitcast(mF16Matrix, mU16Matrix);
        mU16Matrix.copyTo(output);

        for (int x = 0; x < numInputs; x ++) {
            for (int y = 0; y < numInputs; y ++) {
                checkFloat16Sub(x, y);
            }
        }
    }

    private boolean checkFloat16Mul(int x, int y) {
        return checkFloat16Output(x, y, Float16TestData.ReferenceOutputForMul,
                                  "multiplication");
    }

    public void testFloat16Mul() {
        script.set_gInput(mInput);
        script.forEach_mul(mF16Matrix);
        script.forEach_bitcast(mF16Matrix, mU16Matrix);
        mU16Matrix.copyTo(output);

        for (int x = 0; x < numInputs; x ++) {
            for (int y = 0; y < numInputs; y ++) {
                checkFloat16Mul(x, y);
            }
        }
    }

    private boolean checkFloat16Div(int x, int y) {
        return checkFloat16Output(x, y, Float16TestData.ReferenceOutputForDiv,
                                  "division");
    }

    public void testFloat16Div() {
        script.set_gInput(mInput);
        script.forEach_div(mF16Matrix);
        script.forEach_bitcast(mF16Matrix, mU16Matrix);
        mU16Matrix.copyTo(output);

        for (int x = 0; x < numInputs; x ++) {
            for (int y = 0; y < numInputs; y ++) {
                checkFloat16Div(x, y);
            }
        }
    }
}
