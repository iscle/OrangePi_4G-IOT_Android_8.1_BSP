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
import android.util.Log;

public class rsAllocationCopyTest extends RSBaseCompute {

    public void test_rsAllocationCopy1D_Byte() {
        Random random = new Random(0x172d8ab9);
        int width = random.nextInt(512);
        int arr_len = width;
        int offset = random.nextInt(arr_len);
        int count = random.nextInt(arr_len - offset);

        byte[] inArray = new byte[arr_len];
        byte[] outArray = new byte[arr_len];
        random.nextBytes(inArray);

        Type.Builder typeBuilder = new Type.Builder(mRS, Element.I8(mRS));
        typeBuilder.setX(width);
        Allocation aIn = Allocation.createTyped(mRS, typeBuilder.create());
        Allocation aOut = Allocation.createTyped(mRS, typeBuilder.create());
        aIn.copyFrom(inArray);
        aOut.copyFrom(outArray);

        ScriptC_rsallocationcopy s = new ScriptC_rsallocationcopy(mRS);
        s.set_aIn1D(aIn);
        s.set_aOut1D(aOut);
        s.set_xOff(offset);
        s.set_xCount(count);
        s.invoke_test1D();
        mRS.finish();
        aOut.copyTo(outArray);

        boolean result = true;
        for (int i = 0; i < arr_len; i++) {
            if (offset <= i && i < offset + count) {
                if (inArray[i] != outArray[i]) {
                    result = false;
                    break;
                }
            } else {
                if (outArray[i] != 0) {
                    result = false;
                    break;
                }
            }
        }
        assertTrue("test_rsAllocationCopy1D_Byte failed, output array does not match input",
                   result);
    }

    public void test_rsAllocationCopy1D_Short() {
        Random random = new Random(0x172d8ab9);
        int width = random.nextInt(512);
        int arr_len = width;
        int offset = random.nextInt(arr_len);
        int count = random.nextInt(arr_len - offset);

        short[] inArray = new short[arr_len];
        short[] outArray = new short[arr_len];
        for (int i = 0; i < arr_len; i++) {
            inArray[i] = (short)random.nextInt();
        }

        Type.Builder typeBuilder = new Type.Builder(mRS, Element.I16(mRS));
        typeBuilder.setX(width);
        Allocation aIn = Allocation.createTyped(mRS, typeBuilder.create());
        Allocation aOut = Allocation.createTyped(mRS, typeBuilder.create());
        aIn.copyFrom(inArray);
        aOut.copyFrom(outArray);

        ScriptC_rsallocationcopy s = new ScriptC_rsallocationcopy(mRS);
        s.set_aIn1D(aIn);
        s.set_aOut1D(aOut);
        s.set_xOff(offset);
        s.set_xCount(count);
        s.invoke_test1D();
        mRS.finish();
        aOut.copyTo(outArray);

        boolean result = true;
        for (int i = 0; i < arr_len; i++) {
            if (offset <= i && i < offset + count) {
                if (inArray[i] != outArray[i]) {
                    result = false;
                    break;
                }
            } else {
                if (outArray[i] != 0) {
                    result = false;
                    break;
                }
            }
        }
        assertTrue("test_rsAllocationCopy1D_Short failed, output array does not match input",
                   result);
    }

    public void test_rsAllocationCopy1D_Int() {
        Random random = new Random(0x172d8ab9);
        int width = random.nextInt(512);
        int arr_len = width;
        int offset = random.nextInt(arr_len);
        int count = random.nextInt(arr_len - offset);

        int[] inArray = new int[arr_len];
        int[] outArray = new int[arr_len];
        for (int i = 0; i < arr_len; i++) {
            inArray[i] = random.nextInt();
        }

        Type.Builder typeBuilder = new Type.Builder(mRS, Element.I32(mRS));
        typeBuilder.setX(width);
        Allocation aIn = Allocation.createTyped(mRS, typeBuilder.create());
        Allocation aOut = Allocation.createTyped(mRS, typeBuilder.create());
        aIn.copyFrom(inArray);
        aOut.copyFrom(outArray);

        ScriptC_rsallocationcopy s = new ScriptC_rsallocationcopy(mRS);
        s.set_aIn1D(aIn);
        s.set_aOut1D(aOut);
        s.set_xOff(offset);
        s.set_xCount(count);
        s.invoke_test1D();
        mRS.finish();
        aOut.copyTo(outArray);

        boolean result = true;
        for (int i = 0; i < arr_len; i++) {
            if (offset <= i && i < offset + count) {
                if (inArray[i] != outArray[i]) {
                    result = false;
                    break;
                }
            } else {
                if (outArray[i] != 0) {
                    result = false;
                    break;
                }
            }
        }
        assertTrue("test_rsAllocationCopy1D_Int failed, output array does not match input",
                   result);
    }

    public void test_rsAllocationCopy1D_Float() {
        Random random = new Random(0x172d8ab9);
        int width = random.nextInt(512);
        int arr_len = width;
        int offset = random.nextInt(arr_len);
        int count = random.nextInt(arr_len - offset);

        float[] inArray = new float[arr_len];
        float[] outArray = new float[arr_len];
        for (int i = 0; i < arr_len; i++) {
            inArray[i] = random.nextFloat();
        }

        Type.Builder typeBuilder = new Type.Builder(mRS, Element.F32(mRS));
        typeBuilder.setX(width);
        Allocation aIn = Allocation.createTyped(mRS, typeBuilder.create());
        Allocation aOut = Allocation.createTyped(mRS, typeBuilder.create());
        aIn.copyFrom(inArray);
        aOut.copyFrom(outArray);

        ScriptC_rsallocationcopy s = new ScriptC_rsallocationcopy(mRS);
        s.set_aIn1D(aIn);
        s.set_aOut1D(aOut);
        s.set_xOff(offset);
        s.set_xCount(count);
        s.invoke_test1D();
        mRS.finish();
        aOut.copyTo(outArray);


        boolean result = true;
        for (int i = 0; i < arr_len; i++) {
            if (offset <= i && i < offset + count) {
                if (inArray[i] != outArray[i]) {
                    result = false;
                    break;
                }
            } else {
                if (outArray[i] != 0) {
                    result = false;
                    break;
                }
            }
        }
        assertTrue("test_rsAllocationCopy1D_Float failed, output array does not match input",
                   result);
    }

    public void test_rsAllocationCopy1D_Long() {
        Random random = new Random(0x172d8ab9);
        int width = random.nextInt(512);
        int arr_len = width;
        int offset = random.nextInt(arr_len);
        int count = random.nextInt(arr_len - offset);

        long[] inArray = new long[arr_len];
        long[] outArray = new long[arr_len];
        for (int i = 0; i < arr_len; i++) {
            inArray[i] = random.nextLong();
        }

        Type.Builder typeBuilder = new Type.Builder(mRS, Element.I64(mRS));
        typeBuilder.setX(width);
        Allocation aIn = Allocation.createTyped(mRS, typeBuilder.create());
        Allocation aOut = Allocation.createTyped(mRS, typeBuilder.create());
        aIn.copyFrom(inArray);
        aOut.copyFrom(outArray);

        ScriptC_rsallocationcopy s = new ScriptC_rsallocationcopy(mRS);
        s.set_aIn1D(aIn);
        s.set_aOut1D(aOut);
        s.set_xOff(offset);
        s.set_xCount(count);
        s.invoke_test1D();
        mRS.finish();
        aOut.copyTo(outArray);

        boolean result = true;
        for (int i = 0; i < arr_len; i++) {
            if (offset <= i && i < offset + count) {
                if (inArray[i] != outArray[i]) {
                    result = false;
                    break;
                }
            } else {
                if (outArray[i] != 0) {
                    result = false;
                    break;
                }
            }
        }
        assertTrue("test_rsAllocationCopy1D_Long failed, output array does not match input",
                   result);
    }


    public void test_rsAllocationCopy2D_Byte() {
        Random random = new Random(0x172d8ab9);
        int width = random.nextInt(128);
        int height = random.nextInt(128);
        int xOff = random.nextInt(width);
        int yOff = random.nextInt(height);
        int xCount = random.nextInt(width - xOff);
        int yCount = random.nextInt(height - yOff);
        int arr_len = width * height;

        byte[] inArray = new byte[arr_len];
        byte[] outArray = new byte[arr_len];
        random.nextBytes(inArray);

        Type.Builder typeBuilder = new Type.Builder(mRS, Element.I8(mRS));
        typeBuilder.setX(width).setY(height);
        Allocation aIn = Allocation.createTyped(mRS, typeBuilder.create());
        Allocation aOut = Allocation.createTyped(mRS, typeBuilder.create());
        aIn.copyFrom(inArray);
        aOut.copyFrom(outArray);

        ScriptC_rsallocationcopy s = new ScriptC_rsallocationcopy(mRS);
        s.set_aIn2D(aIn);
        s.set_aOut2D(aOut);
        s.set_xOff(xOff);
        s.set_yOff(yOff);
        s.set_xCount(xCount);
        s.set_yCount(yCount);
        s.invoke_test2D();
        mRS.finish();
        aOut.copyTo(outArray);

        boolean result = true;
        for (int i = 0; i < height; i++) {
            for (int j = 0; j < width; j++) {
                int pos = i * width + j;
                if (yOff <= i && i < yOff + yCount &&
                    xOff <= j && j < xOff + xCount) {
                    if (inArray[pos] != outArray[pos]) {
                        result = false;
                        break;
                    }
                } else {
                    if (outArray[pos] != 0) {
                        result = false;
                        break;
                    }
                }
            }
        }
        assertTrue("test_rsAllocationCopy2D_Byte failed, output array does not match input",
                   result);
    }

    public void test_rsAllocationCopy2D_Short() {
        Random random = new Random(0x172d8ab9);
        int width = random.nextInt(128);
        int height = random.nextInt(128);
        int xOff = random.nextInt(width);
        int yOff = random.nextInt(height);
        int xCount = random.nextInt(width - xOff);
        int yCount = random.nextInt(height - yOff);
        int arr_len = width * height;

        short[] inArray = new short[arr_len];
        short[] outArray = new short[arr_len];
        for (int i = 0; i < arr_len; i++) {
            inArray[i] = (short)random.nextInt();
        }

        Type.Builder typeBuilder = new Type.Builder(mRS, Element.I16(mRS));
        typeBuilder.setX(width).setY(height);
        Allocation aIn = Allocation.createTyped(mRS, typeBuilder.create());
        Allocation aOut = Allocation.createTyped(mRS, typeBuilder.create());
        aIn.copyFrom(inArray);
        aOut.copyFrom(outArray);

        ScriptC_rsallocationcopy s = new ScriptC_rsallocationcopy(mRS);
        s.set_aIn2D(aIn);
        s.set_aOut2D(aOut);
        s.set_xOff(xOff);
        s.set_yOff(yOff);
        s.set_xCount(xCount);
        s.set_yCount(yCount);
        s.invoke_test2D();
        mRS.finish();
        aOut.copyTo(outArray);

        boolean result = true;
        for (int i = 0; i < height; i++) {
            for (int j = 0; j < width; j++) {
                int pos = i * width + j;
                if (yOff <= i && i < yOff + yCount &&
                    xOff <= j && j < xOff + xCount) {
                    if (inArray[pos] != outArray[pos]) {
                        result = false;
                        break;
                    }
                } else {
                    if (outArray[pos] != 0) {
                        result = false;
                        break;
                    }
                }
            }
        }
        assertTrue("test_rsAllocationCopy2D_Short failed, output array does not match input",
                   result);
    }

    public void test_rsAllocationCopy2D_Int() {
        Random random = new Random(0x172d8ab9);
        int width = random.nextInt(128);
        int height = random.nextInt(128);
        int xOff = random.nextInt(width);
        int yOff = random.nextInt(height);
        int xCount = random.nextInt(width - xOff);
        int yCount = random.nextInt(height - yOff);
        int arr_len = width * height;

        int[] inArray = new int[arr_len];
        int[] outArray = new int[arr_len];
        for (int i = 0; i < arr_len; i++) {
            inArray[i] = random.nextInt();
        }

        Type.Builder typeBuilder = new Type.Builder(mRS, Element.I32(mRS));
        typeBuilder.setX(width).setY(height);
        Allocation aIn = Allocation.createTyped(mRS, typeBuilder.create());
        Allocation aOut = Allocation.createTyped(mRS, typeBuilder.create());
        aIn.copyFrom(inArray);
        aOut.copyFrom(outArray);

        ScriptC_rsallocationcopy s = new ScriptC_rsallocationcopy(mRS);
        s.set_aIn2D(aIn);
        s.set_aOut2D(aOut);
        s.set_xOff(xOff);
        s.set_yOff(yOff);
        s.set_xCount(xCount);
        s.set_yCount(yCount);
        s.invoke_test2D();
        mRS.finish();
        aOut.copyTo(outArray);

        boolean result = true;
        for (int i = 0; i < height; i++) {
            for (int j = 0; j < width; j++) {
                int pos = i * width + j;
                if (yOff <= i && i < yOff + yCount &&
                    xOff <= j && j < xOff + xCount) {
                    if (inArray[pos] != outArray[pos]) {
                        result = false;
                        break;
                    }
                } else {
                    if (outArray[pos] != 0) {
                        result = false;
                        break;
                    }
                }
            }
        }
        assertTrue("test_rsAllocationCopy2D_Int failed, output array does not match input",
                   result);
    }

    public void test_rsAllocationCopy2D_Float() {
        Random random = new Random(0x172d8ab9);
        int width = random.nextInt(128);
        int height = random.nextInt(128);
        int xOff = random.nextInt(width);
        int yOff = random.nextInt(height);
        int xCount = random.nextInt(width - xOff);
        int yCount = random.nextInt(height - yOff);
        int arr_len = width * height;

        float[] inArray = new float[arr_len];
        float[] outArray = new float[arr_len];
        for (int i = 0; i < arr_len; i++) {
            inArray[i] = random.nextFloat();
        }

        Type.Builder typeBuilder = new Type.Builder(mRS, Element.F32(mRS));
        typeBuilder.setX(width).setY(height);
        Allocation aIn = Allocation.createTyped(mRS, typeBuilder.create());
        Allocation aOut = Allocation.createTyped(mRS, typeBuilder.create());
        aIn.copyFrom(inArray);
        aOut.copyFrom(outArray);

        ScriptC_rsallocationcopy s = new ScriptC_rsallocationcopy(mRS);
        s.set_aIn2D(aIn);
        s.set_aOut2D(aOut);
        s.set_xOff(xOff);
        s.set_yOff(yOff);
        s.set_xCount(xCount);
        s.set_yCount(yCount);
        s.invoke_test2D();
        mRS.finish();
        aOut.copyTo(outArray);

        boolean result = true;
        for (int i = 0; i < height; i++) {
            for (int j = 0; j < width; j++) {
                int pos = i * width + j;
                if (yOff <= i && i < yOff + yCount &&
                    xOff <= j && j < xOff + xCount) {
                    if (inArray[pos] != outArray[pos]) {
                        result = false;
                        break;
                    }
                } else {
                    if (outArray[pos] != 0) {
                        result = false;
                        break;
                    }
                }
            }
        }
        assertTrue("test_rsAllocationCopy2D_Float failed, output array does not match input",
                   result);
    }

    public void test_rsAllocationCopy2D_Long() {
        Random random = new Random(0x172d8ab9);
        int width = random.nextInt(128);
        int height = random.nextInt(128);
        int xOff = random.nextInt(width);
        int yOff = random.nextInt(height);
        int xCount = random.nextInt(width - xOff);
        int yCount = random.nextInt(height - yOff);
        int arr_len = width * height;

        long[] inArray = new long[arr_len];
        long[] outArray = new long[arr_len];
        for (int i = 0; i < arr_len; i++) {
            inArray[i] = random.nextLong();
        }

        Type.Builder typeBuilder = new Type.Builder(mRS, Element.I64(mRS));
        typeBuilder.setX(width).setY(height);
        Allocation aIn = Allocation.createTyped(mRS, typeBuilder.create());
        Allocation aOut = Allocation.createTyped(mRS, typeBuilder.create());
        aIn.copyFrom(inArray);
        aOut.copyFrom(outArray);

        ScriptC_rsallocationcopy s = new ScriptC_rsallocationcopy(mRS);
        s.set_aIn2D(aIn);
        s.set_aOut2D(aOut);
        s.set_xOff(xOff);
        s.set_yOff(yOff);
        s.set_xCount(xCount);
        s.set_yCount(yCount);
        s.invoke_test2D();
        mRS.finish();
        aOut.copyTo(outArray);

        boolean result = true;
        for (int i = 0; i < height; i++) {
            for (int j = 0; j < width; j++) {
                int pos = i * width + j;
                if (yOff <= i && i < yOff + yCount &&
                    xOff <= j && j < xOff + xCount) {
                    if (inArray[pos] != outArray[pos]) {
                        result = false;
                        break;
                    }
                } else {
                    if (outArray[pos] != 0) {
                        result = false;
                        break;
                    }
                }
            }
        }
        assertTrue("test_rsAllocationCopy2D_Long failed, output array does not match input",
                   result);
    }
}
