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

import android.renderscript.Allocation;

import android.renderscript.Byte2;
import android.renderscript.Byte3;
import android.renderscript.Byte4;

import android.renderscript.Double2;
import android.renderscript.Double3;
import android.renderscript.Double4;

import android.renderscript.Element;

import android.renderscript.Float2;
import android.renderscript.Float3;
import android.renderscript.Float4;

import android.renderscript.Int2;
import android.renderscript.Int3;
import android.renderscript.Int4;

import android.renderscript.Long2;
import android.renderscript.Long3;
import android.renderscript.Long4;

import android.renderscript.Short2;
import android.renderscript.Short3;
import android.renderscript.Short4;

import android.renderscript.Element;

/*
 * This checks that modifications to input arguments done by a kernel
 * are never reflected back to the input Allocation.
 *
 * The test works by launching forEach kernels that take different
 * types of inputs. Each forEach kernel modifies its input in some way.
 * After running the forEach kernel, the input Allocation is checked
 * that it remains unmodified.
 */
public class KernelInputTest extends RSBaseCompute {

    void checkForErrorsInScript(ScriptC_kernel_input script) {
        mRS.finish();
        script.invoke_checkError();
        waitForMessage();
        checkForErrors();
    }

    public void testInputNotModified_char() {
        ScriptC_kernel_input script = new ScriptC_kernel_input(mRS);
        Allocation ain = Allocation.createSized(mRS, Element.I8(mRS), 1);
        Allocation tmp = Allocation.createSized(mRS, Element.I8(mRS), 1);

        script.set_initial_value_char((byte) 6);
        ain.copyFrom(new byte[]{ (byte) 6 });
        script.forEach_clear_input_char(ain, tmp);
        script.invoke_verify_input_char(ain);

        checkForErrorsInScript(script);

        ain.destroy();
        tmp.destroy();
        script.destroy();
    }

    public void testInputNotModified_char2() {
        ScriptC_kernel_input script = new ScriptC_kernel_input(mRS);
        Allocation ain = Allocation.createSized(mRS, Element.I8_2(mRS), 1);
        Allocation tmp = Allocation.createSized(mRS, Element.I8_2(mRS), 1);

        script.set_initial_value_char2(new Byte2((byte) 127, (byte) 3));
        ain.copyFrom(new byte[]{ (byte) 127, (byte) 3 });
        script.forEach_clear_input_char2(ain, tmp);
        script.invoke_verify_input_char2(ain);

        checkForErrorsInScript(script);

        ain.destroy();
        tmp.destroy();
        script.destroy();
    }

    public void testInputNotModified_char3() {
        ScriptC_kernel_input script = new ScriptC_kernel_input(mRS);
        Allocation ain = Allocation.createSized(mRS, Element.I8_3(mRS), 1);
        Allocation tmp = Allocation.createSized(mRS, Element.I8_3(mRS), 1);

        script.set_initial_value_char3(new Byte3((byte) 127, (byte) 3, (byte) 4));
        ain.copyFrom(new byte[]{ (byte) 127, (byte) 3, (byte) 4, 0 });
        script.forEach_clear_input_char3(ain, tmp);
        script.invoke_verify_input_char3(ain);

        checkForErrorsInScript(script);

        ain.destroy();
        tmp.destroy();
        script.destroy();
    }

    public void testInputNotModified_char4() {
        ScriptC_kernel_input script = new ScriptC_kernel_input(mRS);
        Allocation ain = Allocation.createSized(mRS, Element.I8_4(mRS), 1);
        Allocation tmp = Allocation.createSized(mRS, Element.I8_4(mRS), 1);

        script.set_initial_value_char4(new Byte4((byte) 127, (byte) 3, (byte) 4, (byte) 7));
        ain.copyFrom(new byte[]{ (byte) 127, (byte) 3, (byte) 4, (byte) 7 });
        script.forEach_clear_input_char4(ain, tmp);
        script.invoke_verify_input_char4(ain);

        checkForErrorsInScript(script);

        ain.destroy();
        tmp.destroy();
        script.destroy();
    }

    public void testInputNotModified_double() {
        ScriptC_kernel_input script = new ScriptC_kernel_input(mRS);
        Allocation ain = Allocation.createSized(mRS, Element.F64(mRS), 1);
        Allocation tmp = Allocation.createSized(mRS, Element.F64(mRS), 1);

        script.set_initial_value_double((double) 6);
        ain.copyFrom(new double[]{ (double) 6 });
        script.forEach_clear_input_double(ain, tmp);
        script.invoke_verify_input_double(ain);

        checkForErrorsInScript(script);

        ain.destroy();
        tmp.destroy();
        script.destroy();
    }

    public void testInputNotModified_double2() {
        ScriptC_kernel_input script = new ScriptC_kernel_input(mRS);
        Allocation ain = Allocation.createSized(mRS, Element.F64_2(mRS), 1);
        Allocation tmp = Allocation.createSized(mRS, Element.F64_2(mRS), 1);

        script.set_initial_value_double2(new Double2((double) 127, (double) 3));
        ain.copyFrom(new double[]{ (double) 127, (double) 3 });
        script.forEach_clear_input_double2(ain, tmp);
        script.invoke_verify_input_double2(ain);

        checkForErrorsInScript(script);

        ain.destroy();
        tmp.destroy();
        script.destroy();
    }

    public void testInputNotModified_double3() {
        ScriptC_kernel_input script = new ScriptC_kernel_input(mRS);
        Allocation ain = Allocation.createSized(mRS, Element.F64_3(mRS), 1);
        Allocation tmp = Allocation.createSized(mRS, Element.F64_3(mRS), 1);

        script.set_initial_value_double3(new Double3((double) 127, (double) 3, (double) 4));
        ain.copyFrom(new double[]{ (double) 127, (double) 3, (double) 4, 0 });
        script.forEach_clear_input_double3(ain, tmp);
        script.invoke_verify_input_double3(ain);

        checkForErrorsInScript(script);

        ain.destroy();
        tmp.destroy();
        script.destroy();
    }

    public void testInputNotModified_double4() {
        ScriptC_kernel_input script = new ScriptC_kernel_input(mRS);
        Allocation ain = Allocation.createSized(mRS, Element.F64_4(mRS), 1);
        Allocation tmp = Allocation.createSized(mRS, Element.F64_4(mRS), 1);

        script.set_initial_value_double4(new Double4((double) 127, (double) 3, (double) 4, (double) 7));
        ain.copyFrom(new double[]{ (double) 127, (double) 3, (double) 4, (double) 7 });
        script.forEach_clear_input_double4(ain, tmp);
        script.invoke_verify_input_double4(ain);

        checkForErrorsInScript(script);

        ain.destroy();
        tmp.destroy();
        script.destroy();
    }

    public void testInputNotModified_float() {
        ScriptC_kernel_input script = new ScriptC_kernel_input(mRS);
        Allocation ain = Allocation.createSized(mRS, Element.F32(mRS), 1);
        Allocation tmp = Allocation.createSized(mRS, Element.F32(mRS), 1);

        script.set_initial_value_float((float) 6);
        ain.copyFrom(new float[]{ (float) 6 });
        script.forEach_clear_input_float(ain, tmp);
        script.invoke_verify_input_float(ain);

        checkForErrorsInScript(script);

        ain.destroy();
        tmp.destroy();
        script.destroy();
    }

    public void testInputNotModified_float2() {
        ScriptC_kernel_input script = new ScriptC_kernel_input(mRS);
        Allocation ain = Allocation.createSized(mRS, Element.F32_2(mRS), 1);
        Allocation tmp = Allocation.createSized(mRS, Element.F32_2(mRS), 1);

        script.set_initial_value_float2(new Float2((float) 127, (float) 3));
        ain.copyFrom(new float[]{ (float) 127, (float) 3 });
        script.forEach_clear_input_float2(ain, tmp);
        script.invoke_verify_input_float2(ain);

        checkForErrorsInScript(script);

        ain.destroy();
        tmp.destroy();
        script.destroy();
    }

    public void testInputNotModified_float3() {
        ScriptC_kernel_input script = new ScriptC_kernel_input(mRS);
        Allocation ain = Allocation.createSized(mRS, Element.F32_3(mRS), 1);
        Allocation tmp = Allocation.createSized(mRS, Element.F32_3(mRS), 1);

        script.set_initial_value_float3(new Float3((float) 127, (float) 3, (float) 4));
        ain.copyFrom(new float[]{ (float) 127, (float) 3, (float) 4, 0 });
        script.forEach_clear_input_float3(ain, tmp);
        script.invoke_verify_input_float3(ain);

        checkForErrorsInScript(script);

        ain.destroy();
        tmp.destroy();
        script.destroy();
    }

    public void testInputNotModified_float4() {
        ScriptC_kernel_input script = new ScriptC_kernel_input(mRS);
        Allocation ain = Allocation.createSized(mRS, Element.F32_4(mRS), 1);
        Allocation tmp = Allocation.createSized(mRS, Element.F32_4(mRS), 1);

        script.set_initial_value_float4(new Float4((float) 127, (float) 3, (float) 4, (float) 7));
        ain.copyFrom(new float[]{ (float) 127, (float) 3, (float) 4, (float) 7 });
        script.forEach_clear_input_float4(ain, tmp);
        script.invoke_verify_input_float4(ain);

        checkForErrorsInScript(script);

        ain.destroy();
        tmp.destroy();
        script.destroy();
    }

    public void testInputNotModified_int() {
        ScriptC_kernel_input script = new ScriptC_kernel_input(mRS);
        Allocation ain = Allocation.createSized(mRS, Element.I32(mRS), 1);
        Allocation tmp = Allocation.createSized(mRS, Element.I32(mRS), 1);

        script.set_initial_value_int(6);
        ain.copyFrom(new int[]{ 6 });
        script.forEach_clear_input_int(ain, tmp);
        script.invoke_verify_input_int(ain);

        checkForErrorsInScript(script);

        ain.destroy();
        tmp.destroy();
        script.destroy();
    }

    public void testInputNotModified_int2() {
        ScriptC_kernel_input script = new ScriptC_kernel_input(mRS);
        Allocation ain = Allocation.createSized(mRS, Element.I32_2(mRS), 1);
        Allocation tmp = Allocation.createSized(mRS, Element.I32_2(mRS), 1);

        script.set_initial_value_int2(new Int2(127, 3));
        ain.copyFrom(new int[]{ 127, 3 });
        script.forEach_clear_input_int2(ain, tmp);
        script.invoke_verify_input_int2(ain);

        checkForErrorsInScript(script);

        ain.destroy();
        tmp.destroy();
        script.destroy();
    }

    public void testInputNotModified_int3() {
        ScriptC_kernel_input script = new ScriptC_kernel_input(mRS);
        Allocation ain = Allocation.createSized(mRS, Element.I32_3(mRS), 1);
        Allocation tmp = Allocation.createSized(mRS, Element.I32_3(mRS), 1);

        script.set_initial_value_int3(new Int3(127, 3, 4));
        ain.copyFrom(new int[]{ 127, 3, 4, 0 });
        script.forEach_clear_input_int3(ain, tmp);
        script.invoke_verify_input_int3(ain);

        checkForErrorsInScript(script);

        ain.destroy();
        tmp.destroy();
        script.destroy();
    }

    public void testInputNotModified_int4() {
        ScriptC_kernel_input script = new ScriptC_kernel_input(mRS);
        Allocation ain = Allocation.createSized(mRS, Element.I32_4(mRS), 1);
        Allocation tmp = Allocation.createSized(mRS, Element.I32_4(mRS), 1);

        script.set_initial_value_int4(new Int4(127, 3, 4, 7));
        ain.copyFrom(new int[]{ 127, 3, 4, 7 });
        script.forEach_clear_input_int4(ain, tmp);
        script.invoke_verify_input_int4(ain);

        checkForErrorsInScript(script);

        ain.destroy();
        tmp.destroy();
        script.destroy();
    }

    public void testInputNotModified_long() {
        ScriptC_kernel_input script = new ScriptC_kernel_input(mRS);
        Allocation ain = Allocation.createSized(mRS, Element.I64(mRS), 1);
        Allocation tmp = Allocation.createSized(mRS, Element.I64(mRS), 1);

        script.set_initial_value_long((long) 6);
        ain.copyFrom(new long[]{ (long) 6 });
        script.forEach_clear_input_long(ain, tmp);
        script.invoke_verify_input_long(ain);

        checkForErrorsInScript(script);

        ain.destroy();
        tmp.destroy();
        script.destroy();
    }

    public void testInputNotModified_long2() {
        ScriptC_kernel_input script = new ScriptC_kernel_input(mRS);
        Allocation ain = Allocation.createSized(mRS, Element.I64_2(mRS), 1);
        Allocation tmp = Allocation.createSized(mRS, Element.I64_2(mRS), 1);

        script.set_initial_value_long2(new Long2((long) 127, (long) 3));
        ain.copyFrom(new long[]{ (long) 127, (long) 3 });
        script.forEach_clear_input_long2(ain, tmp);
        script.invoke_verify_input_long2(ain);

        checkForErrorsInScript(script);

        ain.destroy();
        tmp.destroy();
        script.destroy();
    }

    public void testInputNotModified_long3() {
        ScriptC_kernel_input script = new ScriptC_kernel_input(mRS);
        Allocation ain = Allocation.createSized(mRS, Element.I64_3(mRS), 1);
        Allocation tmp = Allocation.createSized(mRS, Element.I64_3(mRS), 1);

        script.set_initial_value_long3(new Long3((long) 127, (long) 3, (long) 4));
        ain.copyFrom(new long[]{ (long) 127, (long) 3, (long) 4, 0 });
        script.forEach_clear_input_long3(ain, tmp);
        script.invoke_verify_input_long3(ain);

        checkForErrorsInScript(script);

        ain.destroy();
        tmp.destroy();
        script.destroy();
    }

    public void testInputNotModified_long4() {
        ScriptC_kernel_input script = new ScriptC_kernel_input(mRS);
        Allocation ain = Allocation.createSized(mRS, Element.I64_4(mRS), 1);
        Allocation tmp = Allocation.createSized(mRS, Element.I64_4(mRS), 1);

        script.set_initial_value_long4(new Long4((long) 127, (long) 3, (long) 4, (long) 7));
        ain.copyFrom(new long[]{ (long) 127, (long) 3, (long) 4, (long) 7 });
        script.forEach_clear_input_long4(ain, tmp);
        script.invoke_verify_input_long4(ain);

        checkForErrorsInScript(script);

        ain.destroy();
        tmp.destroy();
        script.destroy();
    }

    public void testInputNotModified_short() {
        ScriptC_kernel_input script = new ScriptC_kernel_input(mRS);
        Allocation ain = Allocation.createSized(mRS, Element.I16(mRS), 1);
        Allocation tmp = Allocation.createSized(mRS, Element.I16(mRS), 1);

        script.set_initial_value_short((short) 6);
        ain.copyFrom(new short[]{ (short) 6 });
        script.forEach_clear_input_short(ain, tmp);
        script.invoke_verify_input_short(ain);

        checkForErrorsInScript(script);

        ain.destroy();
        tmp.destroy();
        script.destroy();
    }

    public void testInputNotModified_short2() {
        ScriptC_kernel_input script = new ScriptC_kernel_input(mRS);
        Allocation ain = Allocation.createSized(mRS, Element.I16_2(mRS), 1);
        Allocation tmp = Allocation.createSized(mRS, Element.I16_2(mRS), 1);

        script.set_initial_value_short2(new Short2((short) 127, (short) 3));
        ain.copyFrom(new short[]{ (short) 127, (short) 3 });
        script.forEach_clear_input_short2(ain, tmp);
        script.invoke_verify_input_short2(ain);

        checkForErrorsInScript(script);

        ain.destroy();
        tmp.destroy();
        script.destroy();
    }

    public void testInputNotModified_short3() {
        ScriptC_kernel_input script = new ScriptC_kernel_input(mRS);
        Allocation ain = Allocation.createSized(mRS, Element.I16_3(mRS), 1);
        Allocation tmp = Allocation.createSized(mRS, Element.I16_3(mRS), 1);

        script.set_initial_value_short3(new Short3((short) 127, (short) 3, (short) 4));
        ain.copyFrom(new short[]{ (short) 127, (short) 3, (short) 4, 0 });
        script.forEach_clear_input_short3(ain, tmp);
        script.invoke_verify_input_short3(ain);

        checkForErrorsInScript(script);

        ain.destroy();
        tmp.destroy();
        script.destroy();
    }

    public void testInputNotModified_short4() {
        ScriptC_kernel_input script = new ScriptC_kernel_input(mRS);
        Allocation ain = Allocation.createSized(mRS, Element.I16_4(mRS), 1);
        Allocation tmp = Allocation.createSized(mRS, Element.I16_4(mRS), 1);

        script.set_initial_value_short4(new Short4((short) 127, (short) 3, (short) 4, (short) 7));
        ain.copyFrom(new short[]{ (short) 127, (short) 3, (short) 4, (short) 7 });
        script.forEach_clear_input_short4(ain, tmp);
        script.invoke_verify_input_short4(ain);

        checkForErrorsInScript(script);

        ain.destroy();
        tmp.destroy();
        script.destroy();
    }

    public void testInputNotModified_uchar() {
        ScriptC_kernel_input script = new ScriptC_kernel_input(mRS);
        Allocation ain = Allocation.createSized(mRS, Element.U8(mRS), 1);
        Allocation tmp = Allocation.createSized(mRS, Element.U8(mRS), 1);

        script.set_initial_value_uchar((short) 6);
        ain.copyFrom(new byte[]{ (byte) 6 });
        script.forEach_clear_input_uchar(ain, tmp);
        script.invoke_verify_input_uchar(ain);

        checkForErrorsInScript(script);

        ain.destroy();
        tmp.destroy();
        script.destroy();
    }

    public void testInputNotModified_uchar2() {
        ScriptC_kernel_input script = new ScriptC_kernel_input(mRS);
        Allocation ain = Allocation.createSized(mRS, Element.U8_2(mRS), 1);
        Allocation tmp = Allocation.createSized(mRS, Element.U8_2(mRS), 1);

        script.set_initial_value_uchar2(new Short2((short) 127, (short) 3));
        ain.copyFrom(new byte[]{ (byte) 127, (byte) 3 });
        script.forEach_clear_input_uchar2(ain, tmp);
        script.invoke_verify_input_uchar2(ain);

        checkForErrorsInScript(script);

        ain.destroy();
        tmp.destroy();
        script.destroy();
    }

    public void testInputNotModified_uchar3() {
        ScriptC_kernel_input script = new ScriptC_kernel_input(mRS);
        Allocation ain = Allocation.createSized(mRS, Element.U8_3(mRS), 1);
        Allocation tmp = Allocation.createSized(mRS, Element.U8_3(mRS), 1);

        script.set_initial_value_uchar3(new Short3((short) 127, (short) 3, (short) 4));
        ain.copyFrom(new byte[]{ (byte) 127, (byte) 3, (byte) 4, 0 });
        script.forEach_clear_input_uchar3(ain, tmp);
        script.invoke_verify_input_uchar3(ain);

        checkForErrorsInScript(script);

        ain.destroy();
        tmp.destroy();
        script.destroy();
    }

    public void testInputNotModified_uchar4() {
        ScriptC_kernel_input script = new ScriptC_kernel_input(mRS);
        Allocation ain = Allocation.createSized(mRS, Element.U8_4(mRS), 1);
        Allocation tmp = Allocation.createSized(mRS, Element.U8_4(mRS), 1);

        script.set_initial_value_uchar4(new Short4((short) 127, (short) 3, (short) 4, (short) 7));
        ain.copyFrom(new byte[]{ (byte) 127, (byte) 3, (byte) 4, (byte) 7 });
        script.forEach_clear_input_uchar4(ain, tmp);
        script.invoke_verify_input_uchar4(ain);

        checkForErrorsInScript(script);

        ain.destroy();
        tmp.destroy();
        script.destroy();
    }

    public void testInputNotModified_uint() {
        ScriptC_kernel_input script = new ScriptC_kernel_input(mRS);
        Allocation ain = Allocation.createSized(mRS, Element.U32(mRS), 1);
        Allocation tmp = Allocation.createSized(mRS, Element.U32(mRS), 1);

        script.set_initial_value_uint((long) 6);
        ain.copyFrom(new int[]{ 6 });
        script.forEach_clear_input_uint(ain, tmp);
        script.invoke_verify_input_uint(ain);

        checkForErrorsInScript(script);

        ain.destroy();
        tmp.destroy();
        script.destroy();
    }

    public void testInputNotModified_uint2() {
        ScriptC_kernel_input script = new ScriptC_kernel_input(mRS);
        Allocation ain = Allocation.createSized(mRS, Element.U32_2(mRS), 1);
        Allocation tmp = Allocation.createSized(mRS, Element.U32_2(mRS), 1);

        script.set_initial_value_uint2(new Long2((long) 127, (long) 3));
        ain.copyFrom(new int[]{ 127, 3 });
        script.forEach_clear_input_uint2(ain, tmp);
        script.invoke_verify_input_uint2(ain);

        checkForErrorsInScript(script);

        ain.destroy();
        tmp.destroy();
        script.destroy();
    }

    public void testInputNotModified_uint3() {
        ScriptC_kernel_input script = new ScriptC_kernel_input(mRS);
        Allocation ain = Allocation.createSized(mRS, Element.U32_3(mRS), 1);
        Allocation tmp = Allocation.createSized(mRS, Element.U32_3(mRS), 1);

        script.set_initial_value_uint3(new Long3((long) 127, (long) 3, (long) 4));
        ain.copyFrom(new int[]{ 127, 3, 4, 0 });
        script.forEach_clear_input_uint3(ain, tmp);
        script.invoke_verify_input_uint3(ain);

        checkForErrorsInScript(script);

        ain.destroy();
        tmp.destroy();
        script.destroy();
    }

    public void testInputNotModified_uint4() {
        ScriptC_kernel_input script = new ScriptC_kernel_input(mRS);
        Allocation ain = Allocation.createSized(mRS, Element.U32_4(mRS), 1);
        Allocation tmp = Allocation.createSized(mRS, Element.U32_4(mRS), 1);

        script.set_initial_value_uint4(new Long4((long) 127, (long) 3, (long) 4, (long) 7));
        ain.copyFrom(new int[]{ 127, 3, 4, 7 });
        script.forEach_clear_input_uint4(ain, tmp);
        script.invoke_verify_input_uint4(ain);

        checkForErrorsInScript(script);

        ain.destroy();
        tmp.destroy();
        script.destroy();
    }

    public void testInputNotModified_ulong() {
        ScriptC_kernel_input script = new ScriptC_kernel_input(mRS);
        Allocation ain = Allocation.createSized(mRS, Element.U64(mRS), 1);
        Allocation tmp = Allocation.createSized(mRS, Element.U64(mRS), 1);

        script.set_initial_value_ulong((long) 6);
        ain.copyFrom(new long[]{ (long) 6 });
        script.forEach_clear_input_ulong(ain, tmp);
        script.invoke_verify_input_ulong(ain);

        checkForErrorsInScript(script);

        ain.destroy();
        tmp.destroy();
        script.destroy();
    }

    public void testInputNotModified_ulong2() {
        ScriptC_kernel_input script = new ScriptC_kernel_input(mRS);
        Allocation ain = Allocation.createSized(mRS, Element.U64_2(mRS), 1);
        Allocation tmp = Allocation.createSized(mRS, Element.U64_2(mRS), 1);

        script.set_initial_value_ulong2(new Long2((long) 127, (long) 3));
        ain.copyFrom(new long[]{ (long) 127, (long) 3 });
        script.forEach_clear_input_ulong2(ain, tmp);
        script.invoke_verify_input_ulong2(ain);

        checkForErrorsInScript(script);

        ain.destroy();
        tmp.destroy();
        script.destroy();
    }

    public void testInputNotModified_ulong3() {
        ScriptC_kernel_input script = new ScriptC_kernel_input(mRS);
        Allocation ain = Allocation.createSized(mRS, Element.U64_3(mRS), 1);
        Allocation tmp = Allocation.createSized(mRS, Element.U64_3(mRS), 1);

        script.set_initial_value_ulong3(new Long3((long) 127, (long) 3, (long) 4));
        ain.copyFrom(new long[]{ (long) 127, (long) 3, (long) 4, 0 });
        script.forEach_clear_input_ulong3(ain, tmp);
        script.invoke_verify_input_ulong3(ain);

        checkForErrorsInScript(script);

        ain.destroy();
        tmp.destroy();
        script.destroy();
    }

    public void testInputNotModified_ulong4() {
        ScriptC_kernel_input script = new ScriptC_kernel_input(mRS);
        Allocation ain = Allocation.createSized(mRS, Element.U64_4(mRS), 1);
        Allocation tmp = Allocation.createSized(mRS, Element.U64_4(mRS), 1);

        script.set_initial_value_ulong4(new Long4((long) 127, (long) 3, (long) 4, (long) 7));
        ain.copyFrom(new long[]{ (long) 127, (long) 3, (long) 4, (long) 7 });
        script.forEach_clear_input_ulong4(ain, tmp);
        script.invoke_verify_input_ulong4(ain);

        checkForErrorsInScript(script);

        ain.destroy();
        tmp.destroy();
        script.destroy();
    }

    public void testInputNotModified_ushort() {
        ScriptC_kernel_input script = new ScriptC_kernel_input(mRS);
        Allocation ain = Allocation.createSized(mRS, Element.U16(mRS), 1);
        Allocation tmp = Allocation.createSized(mRS, Element.U16(mRS), 1);

        script.set_initial_value_ushort(6);
        ain.copyFrom(new short[]{ (short) 6 });
        script.forEach_clear_input_ushort(ain, tmp);
        script.invoke_verify_input_ushort(ain);

        checkForErrorsInScript(script);

        ain.destroy();
        tmp.destroy();
        script.destroy();
    }

    public void testInputNotModified_ushort2() {
        ScriptC_kernel_input script = new ScriptC_kernel_input(mRS);
        Allocation ain = Allocation.createSized(mRS, Element.U16_2(mRS), 1);
        Allocation tmp = Allocation.createSized(mRS, Element.U16_2(mRS), 1);

        script.set_initial_value_ushort2(new Int2(127, 3));
        ain.copyFrom(new short[]{ (short) 127, (short) 3 });
        script.forEach_clear_input_ushort2(ain, tmp);
        script.invoke_verify_input_ushort2(ain);

        checkForErrorsInScript(script);

        ain.destroy();
        tmp.destroy();
        script.destroy();
    }

    public void testInputNotModified_ushort3() {
        ScriptC_kernel_input script = new ScriptC_kernel_input(mRS);
        Allocation ain = Allocation.createSized(mRS, Element.U16_3(mRS), 1);
        Allocation tmp = Allocation.createSized(mRS, Element.U16_3(mRS), 1);

        script.set_initial_value_ushort3(new Int3(127, 3, 4));
        ain.copyFrom(new short[]{ (short) 127, (short) 3, (short) 4, 0 });
        script.forEach_clear_input_ushort3(ain, tmp);
        script.invoke_verify_input_ushort3(ain);

        checkForErrorsInScript(script);

        ain.destroy();
        tmp.destroy();
        script.destroy();
    }

    public void testInputNotModified_ushort4() {
        ScriptC_kernel_input script = new ScriptC_kernel_input(mRS);
        Allocation ain = Allocation.createSized(mRS, Element.U16_4(mRS), 1);
        Allocation tmp = Allocation.createSized(mRS, Element.U16_4(mRS), 1);

        script.set_initial_value_ushort4(new Int4(127, 3, 4, 7));
        ain.copyFrom(new short[]{ (short) 127, (short) 3, (short) 4, (short) 7 });
        script.forEach_clear_input_ushort4(ain, tmp);
        script.invoke_verify_input_ushort4(ain);

        checkForErrorsInScript(script);

        ain.destroy();
        tmp.destroy();
        script.destroy();
    }

    public void testInputsNotModified_small() {
        ScriptC_kernel_input script = new ScriptC_kernel_input(mRS);

        Allocation tmp = Allocation.createSized(mRS, ScriptField_small.createElement(mRS), 1);
        ScriptField_small item = new ScriptField_small(mRS, 1);

        item.set_x(0, new int[]{6}, true);
        script.set_initial_value_small(item.get(0));
        script.forEach_clear_input_small(item.getAllocation(), tmp);
        script.invoke_verify_input_small(item.getAllocation());

        checkForErrorsInScript(script);

        tmp.destroy();
        script.destroy();
    }

    public void testInputsNotModified_big() {
        ScriptC_kernel_input script = new ScriptC_kernel_input(mRS);

        Allocation tmp = Allocation.createSized(mRS, ScriptField_big.createElement(mRS), 1);
        ScriptField_big item = new ScriptField_big(mRS, 1);

        for (int i = 0; i < 100; i++) {
            int[] input = new int[100];

            input[i] = 6;
            item.set_x(0, input, true);
            script.set_initial_value_big(item.get(0));
            script.forEach_clear_input_big(item.getAllocation(), tmp);
            script.invoke_verify_input_big(item.getAllocation());
       }

      checkForErrorsInScript(script);

      tmp.destroy();
      script.destroy();
    }
}
