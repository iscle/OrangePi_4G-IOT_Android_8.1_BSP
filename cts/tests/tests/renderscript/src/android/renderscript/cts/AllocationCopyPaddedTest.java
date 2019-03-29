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
import android.renderscript.Element;
import android.renderscript.Type;
import java.util.Random;

public class AllocationCopyPaddedTest extends RSBaseCompute {
    public void test_AllocationPadded_Byte3_1D() {
        Random random = new Random(0x172d8ab9);
        int width = random.nextInt(128);;
        int arr_len = width * 3;

        byte[] inArray = new byte[arr_len];
        byte[] outArray = new byte[arr_len];
        random.nextBytes(inArray);

        Type.Builder typeBuilder = new Type.Builder(mRS, Element.I8_3(mRS));
        typeBuilder.setX(width);
        Allocation alloc = Allocation.createTyped(mRS, typeBuilder.create());
        alloc.setAutoPadding(true);
        alloc.copyFrom(inArray);
        alloc.copyTo(outArray);

        boolean result = true;
        for (int i = 0; i < arr_len; i++) {
            if (inArray[i] != outArray[i]) {
                result = false;
                break;
            }
        }
        assertTrue("test_AllocationPadded_Byte3_1D Failed, output array does not match input",
                   result);
        alloc.destroy();
    }

    public void test_AllocationPadded_Byte3_2D() {
        Random random = new Random(0x172d8ab9);
        int width = random.nextInt(128);
        int height = random.nextInt(128);
        int arr_len = width * height * 3;

        byte[] inArray = new byte[arr_len];
        byte[] outArray = new byte[arr_len];
        random.nextBytes(inArray);

        Type.Builder typeBuilder = new Type.Builder(mRS, Element.I8_3(mRS));
        typeBuilder.setX(width).setY(height);
        Allocation alloc = Allocation.createTyped(mRS, typeBuilder.create());
        alloc.setAutoPadding(true);
        alloc.copyFrom(inArray);
        alloc.copyTo(outArray);

        boolean result = true;
        for (int i = 0; i < arr_len; i++) {
            if (inArray[i] != outArray[i]) {
                result = false;
                break;
            }
        }
        assertTrue("test_AllocationPadded_Byte3_2D Failed, output array does not match input",
                   result);
        alloc.destroy();
    }

    public void test_AllocationPadded_Byte3_3D() {
        Random random = new Random(0x172d8ab9);
        int w = random.nextInt(32);
        int h = random.nextInt(32);
        int d = random.nextInt(32);
        int arr_len = w * d * h * 3;

        byte[] inArray = new byte[arr_len];
        byte[] outArray = new byte[arr_len];
        random.nextBytes(inArray);

        Type.Builder typeBuilder = new Type.Builder(mRS, Element.I8_3(mRS));
        typeBuilder.setX(w).setY(h).setZ(d);
        Allocation alloc = Allocation.createTyped(mRS, typeBuilder.create());
        alloc.setAutoPadding(true);
        alloc.copyFrom(inArray);
        alloc.copyTo(outArray);

        boolean result = true;
        for (int i = 0; i < arr_len; i++) {
            if (inArray[i] != outArray[i]) {
                result = false;
                break;
            }
        }
        assertTrue("test_AllocationPadded_Byte3_3D Failed, output array does not match input",
                   result);
        alloc.destroy();
    }

    void test_AllocationPadded_Short3_1D_Helper(Element element) {
        Random random = new Random(0x172d8ab9);
        int width = random.nextInt(512);
        int arr_len = width * 3;

        short[] inArray = new short[arr_len];
        short[] outArray = new short[arr_len];

        for (int i = 0; i < arr_len; i++) {
            inArray[i] = (short)random.nextInt();
        }

        Type.Builder typeBuilder = new Type.Builder(mRS, element);
        typeBuilder.setX(width);
        Allocation alloc = Allocation.createTyped(mRS, typeBuilder.create());
        alloc.setAutoPadding(true);
        alloc.copyFrom(inArray);
        alloc.copyTo(outArray);

        boolean result = true;
        for (int i = 0; i < arr_len; i++) {
            if (inArray[i] != outArray[i]) {
                result = false;
                break;
            }
        }
        assertTrue("test_AllocationPadded_Short3_1D_Helper Failed, output array does not match input",
                   result);
        alloc.destroy();
    }

    public void test_AllocationPadded_Short3_1D() {
        test_AllocationPadded_Short3_1D_Helper(Element.I16_3(mRS));
        test_AllocationPadded_Short3_1D_Helper(Element.F16_3(mRS));
    }

    void test_AllocationPadded_Short3_2D_Helper(Element element) {
        Random random = new Random(0x172d8ab9);
        int width = random.nextInt(128);
        int height = random.nextInt(128);
        int arr_len = width * height * 3;

        short[] inArray = new short[arr_len];
        short[] outArray = new short[arr_len];

        for (int i = 0; i < arr_len; i++) {
            inArray[i] = (short)random.nextInt();
        }

        Type.Builder typeBuilder = new Type.Builder(mRS, element);
        typeBuilder.setX(width).setY(height);
        Allocation alloc = Allocation.createTyped(mRS, typeBuilder.create());
        alloc.setAutoPadding(true);
        alloc.copyFrom(inArray);
        alloc.copyTo(outArray);

        boolean result = true;
        for (int i = 0; i < arr_len; i++) {
            if (inArray[i] != outArray[i]) {
                result = false;
                break;
            }
        }
        assertTrue("test_AllocationPadded_Short3_2D_Helper Failed, output array does not match input",
                   result);
        alloc.destroy();
    }

    public void test_AllocationPadded_Short3_2D() {
        test_AllocationPadded_Short3_2D_Helper(Element.I16_3(mRS));
        test_AllocationPadded_Short3_2D_Helper(Element.F16_3(mRS));
    }

    void test_AllocationPadded_Short3_3D_Helper(Element element) {
        Random random = new Random(0x172d8ab9);
        int w = random.nextInt(32);
        int h = random.nextInt(32);
        int d = random.nextInt(32);
        int arr_len = w * d * h * 3;

        short[] inArray = new short[arr_len];
        short[] outArray = new short[arr_len];

        for (int i = 0; i < arr_len; i++) {
            inArray[i] = (short)random.nextInt();
        }

        Type.Builder typeBuilder = new Type.Builder(mRS, element);
        typeBuilder.setX(w).setY(h).setZ(d);
        Allocation alloc = Allocation.createTyped(mRS, typeBuilder.create());
        alloc.setAutoPadding(true);
        alloc.copyFrom(inArray);
        alloc.copyTo(outArray);

        boolean result = true;
        for (int i = 0; i < arr_len; i++) {
            if (inArray[i] != outArray[i]) {
                result = false;
                break;
            }
        }
        assertTrue("test_AllocationPadded_Short3_3D_Helper Failed, output array does not match input",
                   result);
        alloc.destroy();
    }

    public void test_AllocationPadded_Short3_3D() {
        test_AllocationPadded_Short3_3D_Helper(Element.I16_3(mRS));
        test_AllocationPadded_Short3_3D_Helper(Element.F16_3(mRS));
    }

    public void test_AllocationPadded_Int3_1D() {
        Random random = new Random(0x172d8ab9);
        int width = random.nextInt(512);
        int arr_len = width * 3;

        int[] inArray = new int[arr_len];
        int[] outArray = new int[arr_len];

        for (int i = 0; i < arr_len; i++) {
            inArray[i] = random.nextInt();
        }

        Type.Builder typeBuilder = new Type.Builder(mRS, Element.I32_3(mRS));
        typeBuilder.setX(width);
        Allocation alloc = Allocation.createTyped(mRS, typeBuilder.create());
        alloc.setAutoPadding(true);
        alloc.copyFrom(inArray);
        alloc.copyTo(outArray);

        boolean result = true;
        for (int i = 0; i < arr_len; i++) {
            if (inArray[i] != outArray[i]) {
                result = false;
                break;
            }
        }
        assertTrue("test_AllocationPadded_Int3_1D Failed, output array does not match input",
                   result);
        alloc.destroy();
    }

    public void test_AllocationPadded_Int3_2D() {
        Random random = new Random(0x172d8ab9);
        int width = random.nextInt(128);
        int height = random.nextInt(128);
        int arr_len = width * height * 3;

        int[] inArray = new int[arr_len];
        int[] outArray = new int[arr_len];

        for (int i = 0; i < arr_len; i++) {
            inArray[i] = random.nextInt();
        }

        Type.Builder typeBuilder = new Type.Builder(mRS, Element.I32_3(mRS));
        typeBuilder.setX(width).setY(height);
        Allocation alloc = Allocation.createTyped(mRS, typeBuilder.create());
        alloc.setAutoPadding(true);
        alloc.copyFrom(inArray);
        alloc.copyTo(outArray);

        boolean result = true;
        for (int i = 0; i < arr_len; i++) {
            if (inArray[i] != outArray[i]) {
                result = false;
                break;
            }
        }
        assertTrue("test_AllocationPadded_Int3_2D Failed, output array does not match input",
                   result);
        alloc.destroy();
    }

    public void test_AllocationPadded_Int3_3D() {
        Random random = new Random(0x172d8ab9);
        int w = random.nextInt(32);
        int h = random.nextInt(32);
        int d = random.nextInt(32);
        int arr_len = w * d * h * 3;

        int[] inArray = new int[arr_len];
        int[] outArray = new int[arr_len];

        for (int i = 0; i < arr_len; i++) {
            inArray[i] = random.nextInt();
        }

        Type.Builder typeBuilder = new Type.Builder(mRS, Element.I32_3(mRS));
        typeBuilder.setX(w).setY(h).setZ(d);
        Allocation alloc = Allocation.createTyped(mRS, typeBuilder.create());
        alloc.setAutoPadding(true);
        alloc.copyFrom(inArray);
        alloc.copyTo(outArray);

        boolean result = true;
        for (int i = 0; i < arr_len; i++) {
            if (inArray[i] != outArray[i]) {
                result = false;
                break;
            }
        }
        assertTrue("test_AllocationPadded_Int3_3D Failed, output array does not match input",
                   result);
        alloc.destroy();
    }

    public void test_AllocationPadded_Float3_1D() {
        Random random = new Random(0x172d8ab9);
        int width = random.nextInt(512);
        int arr_len = width * 3;

        float[] inArray = new float[arr_len];
        float[] outArray = new float[arr_len];

        for (int i = 0; i < arr_len; i++) {
            inArray[i] = random.nextFloat();
        }

        Type.Builder typeBuilder = new Type.Builder(mRS, Element.F32_3(mRS));
        typeBuilder.setX(width);
        Allocation alloc = Allocation.createTyped(mRS, typeBuilder.create());
        alloc.setAutoPadding(true);
        alloc.copyFrom(inArray);
        alloc.copyTo(outArray);

        boolean result = true;
        for (int i = 0; i < arr_len; i++) {
            if (inArray[i] != outArray[i]) {
                result = false;
                break;
            }
        }
        assertTrue("test_AllocationPadded_Float3_1D Failed, output array does not match input",
                   result);
        alloc.destroy();
    }
    public void test_AllocationPadded_Float3_2D() {
        Random random = new Random(0x172d8ab9);
        int width = random.nextInt(128);
        int height = random.nextInt(128);
        int arr_len = width * height * 3;

        float[] inArray = new float[arr_len];
        float[] outArray = new float[arr_len];

        for (int i = 0; i < arr_len; i++) {
            inArray[i] = random.nextFloat();
        }

        Type.Builder typeBuilder = new Type.Builder(mRS, Element.F32_3(mRS));
        typeBuilder.setX(width).setY(height);
        Allocation alloc = Allocation.createTyped(mRS, typeBuilder.create());
        alloc.setAutoPadding(true);
        alloc.copyFrom(inArray);
        alloc.copyTo(outArray);

        boolean result = true;
        for (int i = 0; i < arr_len; i++) {
            if (inArray[i] != outArray[i]) {
                result = false;
                break;
            }
        }
        assertTrue("test_AllocationPadded_Float3_2D Failed, output array does not match input",
                   result);
        alloc.destroy();
    }
    public void test_AllocationPadded_Float3_3D() {
        Random random = new Random(0x172d8ab9);
        int w = random.nextInt(32);
        int h = random.nextInt(32);
        int d = random.nextInt(32);
        int arr_len = w * d * h * 3;

        float[] inArray = new float[arr_len];
        float[] outArray = new float[arr_len];

        for (int i = 0; i < arr_len; i++) {
            inArray[i] = random.nextFloat();
        }

        Type.Builder typeBuilder = new Type.Builder(mRS, Element.F32_3(mRS));
        typeBuilder.setX(w).setY(h).setZ(d);
        Allocation alloc = Allocation.createTyped(mRS, typeBuilder.create());
        alloc.setAutoPadding(true);
        alloc.copyFrom(inArray);
        alloc.copyTo(outArray);

        boolean result = true;
        for (int i = 0; i < arr_len; i++) {
            if (inArray[i] != outArray[i]) {
                result = false;
                break;
            }
        }
        assertTrue("test_AllocationPadded_Float3_3D Failed, output array does not match input",
                   result);
        alloc.destroy();
    }

    public void test_AllocationPadded_Double3_1D() {
        Random random = new Random(0x172d8ab9);
        int width = random.nextInt(512);
        int arr_len = width * 3;

        double[] inArray = new double[arr_len];
        double[] outArray = new double[arr_len];

        for (int i = 0; i < arr_len; i++) {
            inArray[i] = (double)random.nextFloat();
        }

        Type.Builder typeBuilder = new Type.Builder(mRS, Element.F64_3(mRS));
        typeBuilder.setX(width);
        Allocation alloc = Allocation.createTyped(mRS, typeBuilder.create());
        alloc.setAutoPadding(true);
        alloc.copyFrom(inArray);
        alloc.copyTo(outArray);

        boolean result = true;
        for (int i = 0; i < arr_len; i++) {
            if (inArray[i] != outArray[i]) {
                result = false;
                break;
            }
        }
        assertTrue("test_AllocationPadded_Double3_1D Failed, output array does not match input",
                   result);
        alloc.destroy();
    }
    public void test_AllocationPadded_Double3_2D() {
        Random random = new Random(0x172d8ab9);
        int width = random.nextInt(128);
        int height = random.nextInt(128);
        int arr_len = width * height * 3;

        double[] inArray = new double[arr_len];
        double[] outArray = new double[arr_len];

        for (int i = 0; i < arr_len; i++) {
            inArray[i] = (double)random.nextFloat();
        }

        Type.Builder typeBuilder = new Type.Builder(mRS, Element.F64_3(mRS));
        typeBuilder.setX(width).setY(height);
        Allocation alloc = Allocation.createTyped(mRS, typeBuilder.create());
        alloc.setAutoPadding(true);
        alloc.copyFrom(inArray);
        alloc.copyTo(outArray);

        boolean result = true;
        for (int i = 0; i < arr_len; i++) {
            if (inArray[i] != outArray[i]) {
                result = false;
                break;
            }
        }
        assertTrue("test_AllocationPadded_Double3_2D Failed, output array does not match input",
                   result);
        alloc.destroy();
    }
    public void test_AllocationPadded_Double3_3D() {
        Random random = new Random(0x172d8ab9);
        int w = random.nextInt(32);
        int h = random.nextInt(32);
        int d = random.nextInt(32);
        int arr_len = w * d * h * 3;

        double[] inArray = new double[arr_len];
        double[] outArray = new double[arr_len];

        for (int i = 0; i < arr_len; i++) {
            inArray[i] = (double)random.nextFloat();
        }

        Type.Builder typeBuilder = new Type.Builder(mRS, Element.F64_3(mRS));
        typeBuilder.setX(w).setY(h).setZ(d);
        Allocation alloc = Allocation.createTyped(mRS, typeBuilder.create());
        alloc.setAutoPadding(true);
        alloc.copyFrom(inArray);
        alloc.copyTo(outArray);

        boolean result = true;
        for (int i = 0; i < arr_len; i++) {
            if (inArray[i] != outArray[i]) {
                result = false;
                break;
            }
        }
        assertTrue("test_AllocationPadded_Double3_3D Failed, output array does not match input",
                   result);
        alloc.destroy();
    }

    public void test_AllocationPadded_Long3_1D() {
        Random random = new Random(0x172d8ab9);
        int width = random.nextInt(512);
        int arr_len = width * 3;

        long[] inArray = new long[arr_len];
        long[] outArray = new long[arr_len];

        for (int i = 0; i < arr_len; i++) {
            inArray[i] = random.nextLong();
        }

        Type.Builder typeBuilder = new Type.Builder(mRS, Element.I64_3(mRS));
        typeBuilder.setX(width);
        Allocation alloc = Allocation.createTyped(mRS, typeBuilder.create());
        alloc.setAutoPadding(true);
        alloc.copyFrom(inArray);
        alloc.copyTo(outArray);

        boolean result = true;
        for (int i = 0; i < arr_len; i++) {
            if (inArray[i] != outArray[i]) {
                result = false;
                break;
            }
        }
        assertTrue("test_AllocationPadded_Long3_1D Failed, output array does not match input",
                   result);
        alloc.destroy();
    }

    public void test_AllocationPadded_Long3_2D() {
        Random random = new Random(0x172d8ab9);
        int width = random.nextInt(128);
        int height = random.nextInt(128);
        int arr_len = width * height * 3;

        long[] inArray = new long[arr_len];
        long[] outArray = new long[arr_len];

        for (int i = 0; i < arr_len; i++) {
            inArray[i] = random.nextLong();
        }

        Type.Builder typeBuilder = new Type.Builder(mRS, Element.I64_3(mRS));
        typeBuilder.setX(width).setY(height);
        Allocation alloc = Allocation.createTyped(mRS, typeBuilder.create());
        alloc.setAutoPadding(true);
        alloc.copyFrom(inArray);
        alloc.copyTo(outArray);

        boolean result = true;
        for (int i = 0; i < arr_len; i++) {
            if (inArray[i] != outArray[i]) {
                result = false;
                break;
            }
        }
        assertTrue("test_AllocationPadded_Long3_2D Failed, output array does not match input",
                   result);
        alloc.destroy();
    }

    public void test_AllocationPadded_Long3_3D() {
        Random random = new Random(0x172d8ab9);
        int w = random.nextInt(32);
        int h = random.nextInt(32);
        int d = random.nextInt(32);
        int arr_len = w * d * h * 3;

        long[] inArray = new long[arr_len];
        long[] outArray = new long[arr_len];

        for (int i = 0; i < arr_len; i++) {
            inArray[i] = random.nextLong();
        }

        Type.Builder typeBuilder = new Type.Builder(mRS, Element.I64_3(mRS));
        typeBuilder.setX(w).setY(h).setZ(d);
        Allocation alloc = Allocation.createTyped(mRS, typeBuilder.create());
        alloc.setAutoPadding(true);
        alloc.copyFrom(inArray);
        alloc.copyTo(outArray);

        boolean result = true;
        for (int i = 0; i < arr_len; i++) {
            if (inArray[i] != outArray[i]) {
                result = false;
                break;
            }
        }
        assertTrue("test_AllocationPadded_Long3_3D Failed, output array does not match input",
                   result);
        alloc.destroy();
    }


    public void test_AllocationPadded_copy1DRangeTo_Byte3() {
        Random random = new Random(0x172d8ab9);
        int width = random.nextInt(512);
        int arr_len = width * 3;

        byte[] inArray = new byte[arr_len];
        byte[] outArray = new byte[arr_len];
        random.nextBytes(inArray);

        Type.Builder typeBuilder = new Type.Builder(mRS, Element.I8_3(mRS));
        typeBuilder.setX(width);
        Allocation alloc = Allocation.createTyped(mRS, typeBuilder.create());
        alloc.setAutoPadding(true);
        int offset = random.nextInt(width);
        int count = width - offset;
        alloc.copy1DRangeFrom(offset, count, inArray);
        alloc.copy1DRangeTo(offset, count, outArray);

        boolean result = true;
        for (int i = 0; i < count * 3; i++) {
            if (inArray[i] != outArray[i]) {
                result = false;
                break;
            }
        }
        for (int i = count * 3; i < arr_len; i++) {
            if (outArray[i] != 0) {
                result = false;
                break;
            }
        }
        assertTrue("test_AllocationPadded_copy1DRangeTo_Byte3, output array does not match input",
                   result);
        alloc.destroy();
    }

    void test_AllocationPadded_copy1DRangeTo_Short3_Helper(Element element) {
        Random random = new Random(0x172d8ab9);
        int width = random.nextInt(512);
        int arr_len = width * 3;

        short[] inArray = new short[arr_len];
        short[] outArray = new short[arr_len];

        for (int i = 0; i < arr_len; i++) {
            inArray[i] = (short)random.nextInt();
        }

        Type.Builder typeBuilder = new Type.Builder(mRS, element);
        typeBuilder.setX(width);
        Allocation alloc = Allocation.createTyped(mRS, typeBuilder.create());
        alloc.setAutoPadding(true);
        int offset = random.nextInt(width);
        int count = width - offset;
        alloc.copy1DRangeFrom(offset, count, inArray);
        alloc.copy1DRangeTo(offset, count, outArray);

        boolean result = true;
        for (int i = 0; i < count * 3; i++) {
            if (inArray[i] != outArray[i]) {
                result = false;
                break;
            }
        }
        for (int i = count * 3; i < arr_len; i++) {
            if (outArray[i] != 0) {
                result = false;
                break;
            }
        }
        assertTrue("test_AllocationPadded_copy1DRangeTo_Short3_Helper Failed, output array does not match input",
                   result);
        alloc.destroy();
    }

    public void test_AllocationPadded_copy1DRangeTo_Short3() {
        test_AllocationPadded_copy1DRangeTo_Short3_Helper(Element.I16_3(mRS));
        test_AllocationPadded_copy1DRangeTo_Short3_Helper(Element.F16_3(mRS));
    }

    public void test_AllocationPadded_copy1DRangeTo_Int3() {
        Random random = new Random(0x172d8ab9);
        int width = random.nextInt(512);
        int arr_len = width * 3;

        int[] inArray = new int[arr_len];
        int[] outArray = new int[arr_len];

        for (int i = 0; i < arr_len; i++) {
            inArray[i] = random.nextInt();
        }

        Type.Builder typeBuilder = new Type.Builder(mRS, Element.I32_3(mRS));
        typeBuilder.setX(width);
        Allocation alloc = Allocation.createTyped(mRS, typeBuilder.create());
        alloc.setAutoPadding(true);
        int offset = random.nextInt(width);
        int count = width - offset;
        alloc.copy1DRangeFrom(offset, count, inArray);
        alloc.copy1DRangeTo(offset, count, outArray);

        boolean result = true;
        for (int i = 0; i < count * 3; i++) {
            if (inArray[i] != outArray[i]) {
                result = false;
                break;
            }
        }
        for (int i = count * 3; i < arr_len; i++) {
            if (outArray[i] != 0) {
                result = false;
                break;
            }
        }
        assertTrue("test_AllocationPadded_copy1DRangeTo_Int3 Failed, output array does not match input",
                   result);
        alloc.destroy();
    }

    public void test_AllocationPadded_copy1DRangeTo_Float3() {
        Random random = new Random(0x172d8ab9);
        int width = random.nextInt(512);
        int arr_len = width * 3;

        float[] inArray = new float[arr_len];
        float[] outArray = new float[arr_len];

        for (int i = 0; i < arr_len; i++) {
            inArray[i] = random.nextFloat();
        }

        Type.Builder typeBuilder = new Type.Builder(mRS, Element.F32_3(mRS));
        typeBuilder.setX(width);
        Allocation alloc = Allocation.createTyped(mRS, typeBuilder.create());
        alloc.setAutoPadding(true);
        int offset = random.nextInt(width);
        int count = width - offset;
        alloc.copy1DRangeFrom(offset, count, inArray);
        alloc.copy1DRangeTo(offset, count, outArray);

        boolean result = true;
        for (int i = 0; i < count * 3; i++) {
            if (inArray[i] != outArray[i]) {
                result = false;
                break;
            }
        }
        for (int i = count * 3; i < arr_len; i++) {
            if (outArray[i] != 0f) {
                result = false;
                break;
            }
        }
        assertTrue("test_AllocationPadded_copy1DRangeTo_Float3 Failed, output array does not match input",
                   result);
        alloc.destroy();
    }

    public void test_AllocationPadded_copy1DRangeTo_Long3() {
        Random random = new Random(0x172d8ab9);
        int width = random.nextInt(512);
        int arr_len = width * 3;

        long[] inArray = new long[arr_len];
        long[] outArray = new long[arr_len];

        for (int i = 0; i < arr_len; i++) {
            inArray[i] = random.nextLong();
        }

        Type.Builder typeBuilder = new Type.Builder(mRS, Element.I64_3(mRS));
        typeBuilder.setX(width);
        Allocation alloc = Allocation.createTyped(mRS, typeBuilder.create());
        alloc.setAutoPadding(true);
        int offset = random.nextInt(width);
        int count = width - offset;
        alloc.copy1DRangeFrom(offset, count, inArray);
        alloc.copy1DRangeTo(offset, count, outArray);

        boolean result = true;
        for (int i = 0; i < count * 3; i++) {
            if (inArray[i] != outArray[i]) {
                result = false;
                break;
            }
        }
        for (int i = count * 3; i < arr_len; i++) {
            if (outArray[i] != 0) {
                result = false;
                break;
            }
        }
        assertTrue("test_AllocationPadded_copy1DRangeTo_Long3 Failed, output array does not match input",
                   result);
        alloc.destroy();
    }

    public void test_AllocationPadded_copy2DRangeTo_Byte3() {
        Random random = new Random(0x172d8ab9);
        int width = random.nextInt(128);
        int height = random.nextInt(128);
        int xoff = random.nextInt(width);
        int yoff = random.nextInt(height);
        int xcount = width - xoff;
        int ycount = height - yoff;
        int arr_len = xcount * ycount * 3;

        byte[] inArray = new byte[arr_len];
        byte[] outArray = new byte[arr_len];
        random.nextBytes(inArray);

        Type.Builder typeBuilder = new Type.Builder(mRS, Element.I8_3(mRS));
        typeBuilder.setX(width).setY(height);
        Allocation alloc = Allocation.createTyped(mRS, typeBuilder.create());
        alloc.setAutoPadding(true);
        alloc.copy2DRangeFrom(xoff, yoff, xcount, ycount, inArray);
        alloc.copy2DRangeTo(xoff, yoff, xcount, ycount, outArray);

        boolean result = true;
        for (int i = 0; i < arr_len; i++) {
            if (inArray[i] != outArray[i]) {
                result = false;
                break;
            }
        }
        assertTrue("test_AllocationPadded_copy2DRangeTo_Byte3 Failed, output array does not match input",
                   result);
        alloc.destroy();
    }

    void test_AllocationPadded_copy2DRangeTo_Short3_Helper(Element element) {
        Random random = new Random(0x172d8ab9);
        int width = random.nextInt(128);
        int height = random.nextInt(128);
        int xoff = random.nextInt(width);
        int yoff = random.nextInt(height);
        int xcount = width - xoff;
        int ycount = height - yoff;
        int arr_len = xcount * ycount * 3;

        short[] inArray = new short[arr_len];
        short[] outArray = new short[arr_len];

        for (int i = 0; i < arr_len; i++) {
            inArray[i] = (short)random.nextInt();
        }

        Type.Builder typeBuilder = new Type.Builder(mRS, element);
        typeBuilder.setX(width).setY(height);
        Allocation alloc = Allocation.createTyped(mRS, typeBuilder.create());
        alloc.setAutoPadding(true);
        alloc.copy2DRangeFrom(xoff, yoff, xcount, ycount, inArray);
        alloc.copy2DRangeTo(xoff, yoff, xcount, ycount, outArray);

        boolean result = true;
        for (int i = 0; i < arr_len; i++) {
            if (inArray[i] != outArray[i]) {
                result = false;
                break;
            }
        }
        assertTrue("test_AllocationPadded_copy2DRangeTo_Short3_Helper Failed, output array does not match input",
                   result);
        alloc.destroy();
    }

    public void test_AllocationPadded_copy2DRangeTo_Short3() {
        test_AllocationPadded_copy2DRangeTo_Short3_Helper(Element.I16_3(mRS));
        test_AllocationPadded_copy2DRangeTo_Short3_Helper(Element.F16_3(mRS));
    }

    public void test_AllocationPadded_copy2DRangeTo_Int3() {
        Random random = new Random(0x172d8ab9);
        int width = random.nextInt(128);
        int height = random.nextInt(128);
        int xoff = random.nextInt(width);
        int yoff = random.nextInt(height);
        int xcount = width - xoff;
        int ycount = height - yoff;
        int arr_len = xcount * ycount * 3;

        int[] inArray = new int[arr_len];
        int[] outArray = new int[arr_len];

        for (int i = 0; i < arr_len; i++) {
            inArray[i] = random.nextInt();
        }

        Type.Builder typeBuilder = new Type.Builder(mRS, Element.I32_3(mRS));
        typeBuilder.setX(width).setY(height);
        Allocation alloc = Allocation.createTyped(mRS, typeBuilder.create());
        alloc.setAutoPadding(true);
        alloc.copy2DRangeFrom(xoff, yoff, xcount, ycount, inArray);
        alloc.copy2DRangeTo(xoff, yoff, xcount, ycount, outArray);

        boolean result = true;
        for (int i = 0; i < arr_len; i++) {
            if (inArray[i] != outArray[i]) {
                result = false;
                break;
            }
        }
        assertTrue("test_AllocationPadded_copy2DRangeTo_Int3 Failed, output array does not match input",
                   result);
        alloc.destroy();
    }

    public void test_AllocationPadded_copy2DRangeTo_Float3() {
        Random random = new Random(0x172d8ab9);
        int width = random.nextInt(128);
        int height = random.nextInt(128);
        int xoff = random.nextInt(width);
        int yoff = random.nextInt(height);
        int xcount = width - xoff;
        int ycount = height - yoff;
        int arr_len = xcount * ycount * 3;

        float[] inArray = new float[arr_len];
        float[] outArray = new float[arr_len];

        for (int i = 0; i < arr_len; i++) {
            inArray[i] = random.nextFloat();
        }

        Type.Builder typeBuilder = new Type.Builder(mRS, Element.F32_3(mRS));
        typeBuilder.setX(width).setY(height);
        Allocation alloc = Allocation.createTyped(mRS, typeBuilder.create());
        alloc.setAutoPadding(true);
        alloc.copy2DRangeFrom(xoff, yoff, xcount, ycount, inArray);
        alloc.copy2DRangeTo(xoff, yoff, xcount, ycount, outArray);

        boolean result = true;
        for (int i = 0; i < arr_len; i++) {
            if (inArray[i] != outArray[i]) {
                result = false;
                break;
            }
        }
        assertTrue("test_AllocationPadded_copy2DRangeTo_Float3 Failed, output array does not match input",
                   result);
        alloc.destroy();
    }

    public void test_AllocationPadded_copy2DRangeTo_Long3() {
        Random random = new Random(0x172d8ab9);
        int width = random.nextInt(128);
        int height = random.nextInt(128);
        int xoff = random.nextInt(width);
        int yoff = random.nextInt(height);
        int xcount = width - xoff;
        int ycount = height - yoff;
        int arr_len = xcount * ycount * 3;

        long[] inArray = new long[arr_len];
        long[] outArray = new long[arr_len];

        for (int i = 0; i < arr_len; i++) {
            inArray[i] = random.nextLong();
        }

        Type.Builder typeBuilder = new Type.Builder(mRS, Element.I64_3(mRS));
        typeBuilder.setX(width).setY(height);
        Allocation alloc = Allocation.createTyped(mRS, typeBuilder.create());
        alloc.setAutoPadding(true);
        alloc.copy2DRangeFrom(xoff, yoff, xcount, ycount, inArray);
        alloc.copy2DRangeTo(xoff, yoff, xcount, ycount, outArray);

        boolean result = true;
        for (int i = 0; i < arr_len; i++) {
            if (inArray[i] != outArray[i]) {
                result = false;
                break;
            }
        }
        assertTrue("test_AllocationPadded_copy2DRangeTo_Long3 Failed, output array does not match input",
                   result);
        alloc.destroy();
    }


    public void test_AllocationPadded_copy1DRangeToUnchecked_Byte3() {
        Random random = new Random(0x172d8ab9);
        int width = random.nextInt(512);
        int arr_len = width * 3;

        byte[] inArray = new byte[arr_len];
        byte[] outArray = new byte[arr_len];
        random.nextBytes(inArray);

        Type.Builder typeBuilder = new Type.Builder(mRS, Element.I8_3(mRS));
        typeBuilder.setX(width);
        Allocation alloc = Allocation.createTyped(mRS, typeBuilder.create());
        alloc.setAutoPadding(true);
        int offset = random.nextInt(width);
        int count = width - offset;
        alloc.copy1DRangeFrom(offset, count, inArray);
        alloc.copy1DRangeToUnchecked(offset, count, outArray);

        boolean result = true;
        for (int i = 0; i < count * 3; i++) {
            if (inArray[i] != outArray[i]) {
                result = false;
                break;
            }
        }
        for (int i = count * 3; i < arr_len; i++) {
            if (outArray[i] != 0) {
                result = false;
                break;
            }
        }
        assertTrue("test_AllocationPadded_copy1DRangeToUnchecked_Byte3 Failed, output array does not match input",
                   result);
        alloc.destroy();
    }

    void test_AllocationPadded_copy1DRangeToUnchecked_Short3_Helper(Element element) {
        Random random = new Random(0x172d8ab9);
        int width = random.nextInt(512);
        int arr_len = width * 3;

        short[] inArray = new short[arr_len];
        short[] outArray = new short[arr_len];

        for (int i = 0; i < arr_len; i++) {
            inArray[i] = (short)random.nextInt();
        }

        Type.Builder typeBuilder = new Type.Builder(mRS, element);
        typeBuilder.setX(width);
        Allocation alloc = Allocation.createTyped(mRS, typeBuilder.create());
        alloc.setAutoPadding(true);
        int offset = random.nextInt(width);
        int count = width - offset;
        alloc.copy1DRangeFrom(offset, count, inArray);
        alloc.copy1DRangeToUnchecked(offset, count, outArray);

        boolean result = true;
        for (int i = 0; i < count * 3; i++) {
            if (inArray[i] != outArray[i]) {
                result = false;
                break;
            }
        }
        for (int i = count * 3; i < arr_len; i++) {
            if (outArray[i] != 0) {
                result = false;
                break;
            }
        }
        assertTrue("test_AllocationPadded_copy1DRangeToUnchecked_Short3_Helper Failed, output array does not match input",
                   result);
        alloc.destroy();
    }

    public void test_AllocationPadded_copy1DRangeToUnchecked_Short3() {
        test_AllocationPadded_copy1DRangeToUnchecked_Short3_Helper(Element.I16_3(mRS));
        test_AllocationPadded_copy1DRangeToUnchecked_Short3_Helper(Element.F16_3(mRS));
    }

    public void test_AllocationPadded_copy1DRangeToUnchecked_Int3() {
        Random random = new Random(0x172d8ab9);
        int width = random.nextInt(512);
        int arr_len = width * 3;

        int[] inArray = new int[arr_len];
        int[] outArray = new int[arr_len];

        for (int i = 0; i < arr_len; i++) {
            inArray[i] = random.nextInt();
        }

        Type.Builder typeBuilder = new Type.Builder(mRS, Element.I32_3(mRS));
        typeBuilder.setX(width);
        Allocation alloc = Allocation.createTyped(mRS, typeBuilder.create());
        alloc.setAutoPadding(true);
        int offset = random.nextInt(width);
        int count = width - offset;
        alloc.copy1DRangeFrom(offset, count, inArray);
        alloc.copy1DRangeToUnchecked(offset, count, outArray);

        boolean result = true;
        for (int i = 0; i < count * 3; i++) {
            if (inArray[i] != outArray[i]) {
                result = false;
                break;
            }
        }
        for (int i = count * 3; i < arr_len; i++) {
            if (outArray[i] != 0) {
                result = false;
                break;
            }
        }
        assertTrue("test_AllocationPadded_copy1DRangeToUnchecked_Int3 Failed, output array does not match input",
                   result);
        alloc.destroy();
    }

    public void test_AllocationPadded_copy1DRangeToUnchecked_Float3() {
        Random random = new Random(0x172d8ab9);
        int width = random.nextInt(512);
        int arr_len = width * 3;

        float[] inArray = new float[arr_len];
        float[] outArray = new float[arr_len];

        for (int i = 0; i < arr_len; i++) {
            inArray[i] = random.nextFloat();
        }

        Type.Builder typeBuilder = new Type.Builder(mRS, Element.F32_3(mRS));
        typeBuilder.setX(width);
        Allocation alloc = Allocation.createTyped(mRS, typeBuilder.create());
        alloc.setAutoPadding(true);
        int offset = random.nextInt(width);
        int count = width - offset;
        alloc.copy1DRangeFrom(offset, count, inArray);
        alloc.copy1DRangeToUnchecked(offset, count, outArray);

        boolean result = true;
        for (int i = 0; i < count * 3; i++) {
            if (inArray[i] != outArray[i]) {
                result = false;
                break;
            }
        }
        for (int i = count * 3; i < arr_len; i++) {
            if (outArray[i] != 0f) {
                result = false;
                break;
            }
        }
        assertTrue("test_AllocationPadded_copy1DRangeToUnchecked_Float3 Failed, output array does not match input",
                   result);
        alloc.destroy();
    }

    public void test_AllocationPadded_copy1DRangeToUnchecked_Long3() {
        Random random = new Random(0x172d8ab9);
        int width = random.nextInt(512);
        int arr_len = width * 3;

        long[] inArray = new long[arr_len];
        long[] outArray = new long[arr_len];

        for (int i = 0; i < arr_len; i++) {
            inArray[i] = random.nextLong();
        }

        Type.Builder typeBuilder = new Type.Builder(mRS, Element.I64_3(mRS));
        typeBuilder.setX(width);
        Allocation alloc = Allocation.createTyped(mRS, typeBuilder.create());
        alloc.setAutoPadding(true);
        int offset = random.nextInt(width);
        int count = width - offset;
        alloc.copy1DRangeFrom(offset, count, inArray);
        alloc.copy1DRangeToUnchecked(offset, count, outArray);

        boolean result = true;
        for (int i = 0; i < count * 3; i++) {
            if (inArray[i] != outArray[i]) {
                result = false;
                break;
            }
        }
        for (int i = count * 3; i < arr_len; i++) {
            if (outArray[i] != 0) {
                result = false;
                break;
            }
        }
        assertTrue("test_AllocationPadded_copy1DRangeToUnchecked_Long3 Failed, output array does not match input",
                   result);
        alloc.destroy();
    }
}
