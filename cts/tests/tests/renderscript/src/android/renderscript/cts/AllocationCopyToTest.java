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

public class AllocationCopyToTest extends RSBaseCompute {
    private Allocation alloc;

    @Override
    protected void tearDown() throws Exception {
        if (alloc != null) {
            alloc.destroy();
        }
        super.tearDown();
    }

    public void test_Allocationcopy1DRangeTo_Byte() {
        Random random = new Random(0x172d8ab9);
        int width = random.nextInt(512);
        int arr_len = width;

        byte[] inArray = new byte[arr_len];
        byte[] outArray = new byte[arr_len];
        random.nextBytes(inArray);

        Type.Builder typeBuilder = new Type.Builder(mRS, Element.I8(mRS));
        typeBuilder.setX(width);
        alloc = Allocation.createTyped(mRS, typeBuilder.create());
        int offset = random.nextInt(arr_len);
        int count = arr_len - offset;
        alloc.copy1DRangeFrom(offset, count, inArray);
        alloc.copy1DRangeTo(offset, count, outArray);

        boolean result = true;
        for (int i = 0; i < count; i++) {
            if (inArray[i] != outArray[i]) {
                result = false;
                break;
            }
        }
        for (int i = count; i < arr_len; i++) {
            if (outArray[i] != 0) {
                result = false;
                break;
            }
        }
        assertTrue("test_Allocationcopy1DRangeTo_Byte failed, output array does not match input",
                   result);
    }

    void test_Allocationcopy1DRangeTo_Short_Helper(Element element, boolean testTyped) {
        Random random = new Random(0x172d8ab9);
        int width = random.nextInt(512);
        int arr_len = width;

        short[] inArray = new short[arr_len];
        short[] outArray = new short[arr_len];

        for (int i = 0; i < arr_len; i++) {
            inArray[i] = (short)random.nextInt();
        }

        Type.Builder typeBuilder = new Type.Builder(mRS, element);
        typeBuilder.setX(width);
        alloc = Allocation.createTyped(mRS, typeBuilder.create());
        int offset = random.nextInt(arr_len);
        int count = arr_len - offset;
        if (testTyped) {
            alloc.copy1DRangeFrom(offset, count, inArray);
            alloc.copy1DRangeTo(offset, count, outArray);
        } else {
            alloc.copy1DRangeFrom(offset, count, (Object) inArray);
            alloc.copy1DRangeTo(offset, count, (Object) outArray);
        }

        boolean result = true;
        for (int i = 0; i < count; i++) {
            if (inArray[i] != outArray[i]) {
                result = false;
                break;
            }
        }
        for (int i = count; i < arr_len; i++) {
            if (outArray[i] != 0) {
                result = false;
                break;
            }
        }
        assertTrue("test_Allocationcopy1DRangeTo_Short_Helper failed, output array does not match input",
                   result);
    }

    public void test_Allocationcopy1DRangeTo_Short() {
        test_Allocationcopy1DRangeTo_Short_Helper(Element.I16(mRS), true);
        test_Allocationcopy1DRangeTo_Short_Helper(Element.F16(mRS), true);
        test_Allocationcopy1DRangeTo_Short_Helper(Element.F16(mRS), false);
    }

    public void test_Allocationcopy1DRangeTo_Int() {
        Random random = new Random(0x172d8ab9);
        int width = random.nextInt(512);
        int arr_len = width;

        int[] inArray = new int[arr_len];
        int[] outArray = new int[arr_len];

        for (int i = 0; i < arr_len; i++) {
            inArray[i] = random.nextInt();
        }

        Type.Builder typeBuilder = new Type.Builder(mRS, Element.I32(mRS));
        typeBuilder.setX(width);
        alloc = Allocation.createTyped(mRS, typeBuilder.create());
        int offset = random.nextInt(arr_len);
        int count = arr_len - offset;
        alloc.copy1DRangeFrom(offset, count, inArray);
        alloc.copy1DRangeTo(offset, count, outArray);

        boolean result = true;
        for (int i = 0; i < count; i++) {
            if (inArray[i] != outArray[i]) {
                result = false;
                break;
            }
        }
        for (int i = count; i < arr_len; i++) {
            if (outArray[i] != 0) {
                result = false;
                break;
            }
        }
        assertTrue("test_Allocationcopy1DRangeTo_Int failed, output array does not match input",
                   result);
    }

    public void test_Allocationcopy1DRangeTo_Float() {
        Random random = new Random(0x172d8ab9);
        int width = random.nextInt(512);
        int arr_len = width;

        float[] inArray = new float[arr_len];
        float[] outArray = new float[arr_len];

        for (int i = 0; i < arr_len; i++) {
            inArray[i] = random.nextFloat();
        }

        Type.Builder typeBuilder = new Type.Builder(mRS, Element.F32(mRS));
        typeBuilder.setX(width);
        alloc = Allocation.createTyped(mRS, typeBuilder.create());
        int offset = random.nextInt(arr_len);
        int count = arr_len - offset;
        alloc.copy1DRangeFrom(offset, count, inArray);
        alloc.copy1DRangeTo(offset, count, outArray);

        boolean result = true;
        for (int i = 0; i < count; i++) {
            if (inArray[i] != outArray[i]) {
                result = false;
                break;
            }
        }
        for (int i = count; i < arr_len; i++) {
            if (outArray[i] != 0f) {
                result = false;
                break;
            }
        }
        assertTrue("test_Allocationcopy1DRangeTo_Float failed, output array does not match input",
                   result);
    }

    public void test_Allocationcopy1DRangeTo_Long() {
        Random random = new Random(0x172d8ab9);
        int width = random.nextInt(512);
        int arr_len = width;

        long[] inArray = new long[arr_len];
        long[] outArray = new long[arr_len];

        for (int i = 0; i < arr_len; i++) {
            inArray[i] = random.nextLong();
        }

        Type.Builder typeBuilder = new Type.Builder(mRS, Element.I64(mRS));
        typeBuilder.setX(width);
        alloc = Allocation.createTyped(mRS, typeBuilder.create());
        int offset = random.nextInt(arr_len);
        int count = arr_len - offset;
        alloc.copy1DRangeFrom(offset, count, inArray);
        alloc.copy1DRangeTo(offset, count, outArray);

        boolean result = true;
        for (int i = 0; i < count; i++) {
            if (inArray[i] != outArray[i]) {
                result = false;
                break;
            }
        }
        for (int i = count; i < arr_len; i++) {
            if (outArray[i] != 0) {
                result = false;
                break;
            }
        }
        assertTrue("test_Allocationcopy1DRangeTo_Long failed, output array does not match input",
                   result);
    }

    public void test_Allocationcopy2DRangeTo_Byte() {
        Random random = new Random(0x172d8ab9);
        int width = random.nextInt(128);
        int height = random.nextInt(128);
        int xoff = random.nextInt(width);
        int yoff = random.nextInt(height);
        int xcount = width - xoff;
        int ycount = height - yoff;
        int arr_len = xcount * ycount;

        byte[] inArray = new byte[arr_len];
        byte[] outArray = new byte[arr_len];
        random.nextBytes(inArray);

        Type.Builder typeBuilder = new Type.Builder(mRS, Element.I8(mRS));
        typeBuilder.setX(width).setY(height);
        alloc = Allocation.createTyped(mRS, typeBuilder.create());
        alloc.copy2DRangeFrom(xoff, yoff, xcount, ycount, inArray);
        alloc.copy2DRangeTo(xoff, yoff, xcount, ycount, outArray);

        boolean result = true;
        for (int i = 0; i < arr_len; i++) {
            if (inArray[i] != outArray[i]) {
                result = false;
                break;
            }
        }
        assertTrue("test_Allocationcopy2DRangeTo_Byte failed, output array does not match input",
                   result);
    }

    void test_Allocationcopy2DRangeTo_Short_Helper(Element element, boolean testTyped) {
        Random random = new Random(0x172d8ab9);
        int width = random.nextInt(128);
        int height = random.nextInt(128);
        int xoff = random.nextInt(width);
        int yoff = random.nextInt(height);
        int xcount = width - xoff;
        int ycount = height - yoff;
        int arr_len = xcount * ycount;

        short[] inArray = new short[arr_len];
        short[] outArray = new short[arr_len];

        for (int i = 0; i < arr_len; i++) {
            inArray[i] = (short)random.nextInt();
        }

        Type.Builder typeBuilder = new Type.Builder(mRS, Element.I16(mRS));
        typeBuilder.setX(width).setY(height);
        alloc = Allocation.createTyped(mRS, typeBuilder.create());
        if (testTyped) {
            alloc.copy2DRangeFrom(xoff, yoff, xcount, ycount, inArray);
            alloc.copy2DRangeTo(xoff, yoff, xcount, ycount, outArray);
        } else {
            alloc.copy2DRangeFrom(xoff, yoff, xcount, ycount, (Object) inArray);
            alloc.copy2DRangeTo(xoff, yoff, xcount, ycount, (Object) outArray);
        }

        boolean result = true;
        for (int i = 0; i < arr_len; i++) {
            if (inArray[i] != outArray[i]) {
                result = false;
                break;
            }
        }
        assertTrue("test_Allocationcopy2DRangeTo_Short_Helper failed, output array does not match input",
                   result);
    }

    public void test_Allocationcopy2DRangeTo_Short() {
        test_Allocationcopy2DRangeTo_Short_Helper(Element.I16(mRS), true);
        test_Allocationcopy2DRangeTo_Short_Helper(Element.F16(mRS), true);
        test_Allocationcopy2DRangeTo_Short_Helper(Element.F16(mRS), false);
    }

    public void test_Allocationcopy2DRangeTo_Int() {
        Random random = new Random(0x172d8ab9);
        int width = random.nextInt(128);
        int height = random.nextInt(128);
        int xoff = random.nextInt(width);
        int yoff = random.nextInt(height);
        int xcount = width - xoff;
        int ycount = height - yoff;
        int arr_len = xcount * ycount;

        int[] inArray = new int[arr_len];
        int[] outArray = new int[arr_len];

        for (int i = 0; i < arr_len; i++) {
            inArray[i] = random.nextInt();
        }

        Type.Builder typeBuilder = new Type.Builder(mRS, Element.I32(mRS));
        typeBuilder.setX(width).setY(height);
        alloc = Allocation.createTyped(mRS, typeBuilder.create());
        alloc.copy2DRangeFrom(xoff, yoff, xcount, ycount, inArray);
        alloc.copy2DRangeTo(xoff, yoff, xcount, ycount, outArray);

        boolean result = true;
        for (int i = 0; i < arr_len; i++) {
            if (inArray[i] != outArray[i]) {
                result = false;
                break;
            }
        }
        assertTrue("test_Allocationcopy2DRangeTo_Int failed, output array does not match input",
                   result);
    }

    public void test_Allocationcopy2DRangeTo_Float() {
        Random random = new Random(0x172d8ab9);
        int width = random.nextInt(128);
        int height = random.nextInt(128);
        int xoff = random.nextInt(width);
        int yoff = random.nextInt(height);
        int xcount = width - xoff;
        int ycount = height - yoff;
        int arr_len = xcount * ycount;

        float[] inArray = new float[arr_len];
        float[] outArray = new float[arr_len];

        for (int i = 0; i < arr_len; i++) {
            inArray[i] = random.nextFloat();
        }

        Type.Builder typeBuilder = new Type.Builder(mRS, Element.F32(mRS));
        typeBuilder.setX(width).setY(height);
        alloc = Allocation.createTyped(mRS, typeBuilder.create());
        alloc.copy2DRangeFrom(xoff, yoff, xcount, ycount, inArray);
        alloc.copy2DRangeTo(xoff, yoff, xcount, ycount, outArray);

        boolean result = true;
        for (int i = 0; i < arr_len; i++) {
            if (inArray[i] != outArray[i]) {
                result = false;
                break;
            }
        }
        assertTrue("test_Allocationcopy2DRangeTo_Float failed, output array does not match input",
                   result);
    }

    public void test_Allocationcopy2DRangeTo_Long() {
        Random random = new Random(0x172d8ab9);
        int width = random.nextInt(128);
        int height = random.nextInt(128);
        int xoff = random.nextInt(width);
        int yoff = random.nextInt(height);
        int xcount = width - xoff;
        int ycount = height - yoff;
        int arr_len = xcount * ycount;

        long[] inArray = new long[arr_len];
        long[] outArray = new long[arr_len];

        for (int i = 0; i < arr_len; i++) {
            inArray[i] = random.nextLong();
        }

        Type.Builder typeBuilder = new Type.Builder(mRS, Element.I64(mRS));
        typeBuilder.setX(width).setY(height);
        alloc = Allocation.createTyped(mRS, typeBuilder.create());
        alloc.copy2DRangeFrom(xoff, yoff, xcount, ycount, inArray);
        alloc.copy2DRangeTo(xoff, yoff, xcount, ycount, outArray);

        boolean result = true;
        for (int i = 0; i < arr_len; i++) {
            if (inArray[i] != outArray[i]) {
                result = false;
                break;
            }
        }
        assertTrue("test_Allocationcopy2DRangeTo_Long failed, output array does not match input",
                   result);
    }

    public void test_Allocationcopy3DRangeTo_Byte() {
        Random random = new Random(0x172d8ab9);
        int width = random.nextInt(64);
        int height = random.nextInt(64);
        int depth = random.nextInt(64);

        int xoff = random.nextInt(width);
        int yoff = random.nextInt(height);
        int zoff = random.nextInt(height);

        int xcount = width - xoff;
        int ycount = height - yoff;
        int zcount = depth - zoff;
        int arr_len = xcount * ycount * zcount;

        byte[] inArray = new byte[arr_len];
        byte[] outArray = new byte[arr_len];
        random.nextBytes(inArray);

        Type.Builder typeBuilder = new Type.Builder(mRS, Element.I8(mRS));
        typeBuilder.setX(width).setY(height).setZ(depth);
        alloc = Allocation.createTyped(mRS, typeBuilder.create());
        alloc.copy3DRangeFrom(xoff, yoff, zoff, xcount, ycount, zcount, (Object)inArray);
        alloc.copy3DRangeTo(xoff, yoff, zoff, xcount, ycount, zcount, (Object)outArray);

        boolean result = true;
        for (int i = 0; i < arr_len; i++) {
            if (inArray[i] != outArray[i]) {
                result = false;
                android.util.Log.v("Allocation CopyTo Test", "Failed: " + i + " " + inArray[i] + " " + outArray[i]);
                break;
            }
        }
        assertTrue("test_Allocationcopy3DRangeTo_Byte failed, output array does not match input",
                   result);
    }

    void test_Allocationcopy3DRangeTo_Short_Helper(Element element) {
        Random random = new Random(0x172d8ab9);
        int width = random.nextInt(64);
        int height = random.nextInt(64);
        int depth = random.nextInt(64);

        int xoff = random.nextInt(width);
        int yoff = random.nextInt(height);
        int zoff = random.nextInt(height);

        int xcount = width - xoff;
        int ycount = height - yoff;
        int zcount = depth - zoff;
        int arr_len = xcount * ycount * zcount;

        short[] inArray = new short[arr_len];
        short[] outArray = new short[arr_len];

        for (int i = 0; i < arr_len; i++) {
            inArray[i] = (short)random.nextInt();
        }

        Type.Builder typeBuilder = new Type.Builder(mRS, element);
        typeBuilder.setX(width).setY(height).setZ(depth);
        alloc = Allocation.createTyped(mRS, typeBuilder.create());
        alloc.copy3DRangeFrom(xoff, yoff, zoff, xcount, ycount, zcount, (Object)inArray);
        alloc.copy3DRangeTo(xoff, yoff, zoff, xcount, ycount, zcount, (Object)outArray);

        boolean result = true;
        for (int i = 0; i < arr_len; i++) {
            if (inArray[i] != outArray[i]) {
                result = false;
                android.util.Log.v("Allocation CopyTo Test", "Failed: " + i + " " + inArray[i] + " " + outArray[i]);
                break;
            }
        }
        assertTrue("test_Allocationcopy3DRangeTo_Short_Helper failed, output array does not match input",
                   result);
    }

    public void test_Allocationcopy3DRangeTo_Short() {
        test_Allocationcopy3DRangeTo_Short_Helper(Element.I16(mRS));
        test_Allocationcopy3DRangeTo_Short_Helper(Element.F16(mRS));
    }

    public void test_Allocationcopy3DRangeTo_Int() {
        Random random = new Random(0x172d8ab9);
        int width = random.nextInt(64);
        int height = random.nextInt(64);
        int depth = random.nextInt(64);

        int xoff = random.nextInt(width);
        int yoff = random.nextInt(height);
        int zoff = random.nextInt(height);

        int xcount = width - xoff;
        int ycount = height - yoff;
        int zcount = depth - zoff;
        int arr_len = xcount * ycount * zcount;

        int[] inArray = new int[arr_len];
        int[] outArray = new int[arr_len];

        for (int i = 0; i < arr_len; i++) {
            inArray[i] = random.nextInt();
        }

        Type.Builder typeBuilder = new Type.Builder(mRS, Element.I32(mRS));
        typeBuilder.setX(width).setY(height).setZ(depth);
        alloc = Allocation.createTyped(mRS, typeBuilder.create());
        alloc.copy3DRangeFrom(xoff, yoff, zoff, xcount, ycount, zcount, (Object)inArray);
        alloc.copy3DRangeTo(xoff, yoff, zoff, xcount, ycount, zcount, (Object)outArray);

        boolean result = true;
        for (int i = 0; i < arr_len; i++) {
            if (inArray[i] != outArray[i]) {
                result = false;
                android.util.Log.v("Allocation CopyTo Test", "Failed: " + i + " " + inArray[i] + " " + outArray[i]);
                break;
            }
        }
        assertTrue("test_Allocationcopy3DRangeTo_Int failed, output array does not match input",
                   result);
    }

    public void test_Allocationcopy3DRangeTo_Float() {
        Random random = new Random(0x172d8ab9);
        int width = random.nextInt(64);
        int height = random.nextInt(64);
        int depth = random.nextInt(64);

        int xoff = random.nextInt(width);
        int yoff = random.nextInt(height);
        int zoff = random.nextInt(height);

        int xcount = width - xoff;
        int ycount = height - yoff;
        int zcount = depth - zoff;
        int arr_len = xcount * ycount * zcount;

        float[] inArray = new float[arr_len];
        float[] outArray = new float[arr_len];

        for (int i = 0; i < arr_len; i++) {
            inArray[i] = random.nextFloat();
        }

        Type.Builder typeBuilder = new Type.Builder(mRS, Element.F32(mRS));
        typeBuilder.setX(width).setY(height).setZ(depth);
        alloc = Allocation.createTyped(mRS, typeBuilder.create());
        alloc.copy3DRangeFrom(xoff, yoff, zoff, xcount, ycount, zcount, (Object)inArray);
        alloc.copy3DRangeTo(xoff, yoff, zoff, xcount, ycount, zcount, (Object)outArray);

        boolean result = true;
        for (int i = 0; i < arr_len; i++) {
            if (inArray[i] != outArray[i]) {
                result = false;
                android.util.Log.v("Allocation CopyTo Test", "Failed: " + i + " " + inArray[i] + " " + outArray[i]);
                break;
            }
        }
        assertTrue("test_Allocationcopy3DRangeTo_Float failed, output array does not match input",
                   result);
    }

    public void test_Allocationcopy3DRangeTo_Long() {
        Random random = new Random(0x172d8ab9);
        int width = random.nextInt(64);
        int height = random.nextInt(64);
        int depth = random.nextInt(64);

        int xoff = random.nextInt(width);
        int yoff = random.nextInt(height);
        int zoff = random.nextInt(height);

        int xcount = width - xoff;
        int ycount = height - yoff;
        int zcount = depth - zoff;
        int arr_len = xcount * ycount * zcount;

        long[] inArray = new long[arr_len];
        long[] outArray = new long[arr_len];

        for (int i = 0; i < arr_len; i++) {
            inArray[i] = random.nextLong();
        }

        Type.Builder typeBuilder = new Type.Builder(mRS, Element.I64(mRS));
        typeBuilder.setX(width).setY(height).setZ(depth);
        alloc = Allocation.createTyped(mRS, typeBuilder.create());
        alloc.copy3DRangeFrom(xoff, yoff, zoff, xcount, ycount, zcount, (Object)inArray);
        alloc.copy3DRangeTo(xoff, yoff, zoff, xcount, ycount, zcount, (Object)outArray);

        boolean result = true;
        for (int i = 0; i < arr_len; i++) {
            if (inArray[i] != outArray[i]) {
                result = false;
                android.util.Log.v("Allocation CopyTo Test", "Failed: " + i + " " + inArray[i] + " " + outArray[i]);
                break;
            }
        }
        assertTrue("test_Allocationcopy3DRangeTo_Long failed, output array does not match input",
                   result);
    }

    public void test_AllocationCopy3DRangeFrom_Alloc() {
        Random random = new Random(0x172d8ab9);
        int width = random.nextInt(64);
        int height = random.nextInt(64);
        int depth = random.nextInt(64);

        int xoff = random.nextInt(width);
        int yoff = random.nextInt(height);
        int zoff = random.nextInt(height);

        int xcount = width - xoff;
        int ycount = height - yoff;
        int zcount = depth - zoff;
        int arr_len = xcount * ycount * zcount;

        long[] inArray = new long[arr_len];
        long[] outArray = new long[arr_len];

        for (int i = 0; i < arr_len; i++) {
            inArray[i] = random.nextLong();
        }

        Type.Builder typeBuilder = new Type.Builder(mRS, Element.I64(mRS));
        typeBuilder.setX(width).setY(height).setZ(depth);
        alloc = Allocation.createTyped(mRS, typeBuilder.create());
        Allocation allocRef = Allocation.createTyped(mRS, typeBuilder.create());

        allocRef.copy3DRangeFrom(xoff, yoff, zoff, xcount, ycount, zcount, (Object)inArray);
        alloc.copy3DRangeFrom(xoff, yoff, zoff, xcount, ycount, zcount, allocRef, xoff, yoff, zoff);
        alloc.copy3DRangeTo(xoff, yoff, zoff, xcount, ycount, zcount, (Object)outArray);

        boolean result = true;
        for (int i = 0; i < arr_len; i++) {
            if (inArray[i] != outArray[i]) {
                result = false;
                android.util.Log.v("Allocation Copy3DRangeFrom (alloc) Test", "Failed: " + i + " " + inArray[i] + " " + outArray[i]);
                break;
            }
        }
        assertTrue("test_AllocationCopy3DRangeFrom_Alloc failed, output array does not match input",
                   result);
    }

    public void test_Allocationcopy1DRangeToUnchecked_Byte() {
        Random random = new Random(0x172d8ab9);
        int width = random.nextInt(512);
        int arr_len = width;

        byte[] inArray = new byte[arr_len];
        byte[] outArray = new byte[arr_len];
        random.nextBytes(inArray);

        Type.Builder typeBuilder = new Type.Builder(mRS, Element.I8(mRS));
        typeBuilder.setX(width);
        alloc = Allocation.createTyped(mRS, typeBuilder.create());
        int offset = random.nextInt(arr_len);
        int count = arr_len - offset;
        alloc.copy1DRangeFrom(offset, count, inArray);
        alloc.copy1DRangeToUnchecked(offset, count, outArray);

        boolean result = true;
        for (int i = 0; i < count; i++) {
            if (inArray[i] != outArray[i]) {
                result = false;
                break;
            }
        }
        for (int i = count; i < arr_len; i++) {
            if (outArray[i] != 0) {
                result = false;
                break;
            }
        }
        assertTrue("test_Allocationcopy1DRangeToUnchecked_Byte failed, output array does not match input",
                   result);
    }

    void test_Allocationcopy1DRangeToUnchecked_Short_Helper(Element element) {
        Random random = new Random(0x172d8ab9);
        int width = random.nextInt(512);
        int arr_len = width;

        short[] inArray = new short[arr_len];
        short[] outArray = new short[arr_len];

        for (int i = 0; i < arr_len; i++) {
            inArray[i] = (short)random.nextInt();
        }

        Type.Builder typeBuilder = new Type.Builder(mRS, element);
        typeBuilder.setX(width);
        alloc = Allocation.createTyped(mRS, typeBuilder.create());
        int offset = random.nextInt(arr_len);
        int count = arr_len - offset;
        alloc.copy1DRangeFrom(offset, count, inArray);
        alloc.copy1DRangeToUnchecked(offset, count, outArray);

        boolean result = true;
        for (int i = 0; i < count; i++) {
            if (inArray[i] != outArray[i]) {
                result = false;
                break;
            }
        }
        for (int i = count; i < arr_len; i++) {
            if (outArray[i] != 0) {
                result = false;
                break;
            }
        }
        assertTrue("test_Allocationcopy1DRangeToUnchecked_Short_Helper failed, output array does not match input",
                   result);
    }

    public void test_Allocationcopy1DRangeToUnchecked_Short() {
        test_Allocationcopy1DRangeToUnchecked_Short_Helper(Element.I16(mRS));
        test_Allocationcopy1DRangeToUnchecked_Short_Helper(Element.F16(mRS));
    }

    public void test_Allocationcopy1DRangeToUnchecked_Int() {
        Random random = new Random(0x172d8ab9);
        int width = random.nextInt(512);
        int arr_len = width;

        int[] inArray = new int[arr_len];
        int[] outArray = new int[arr_len];

        for (int i = 0; i < arr_len; i++) {
            inArray[i] = random.nextInt();
        }

        Type.Builder typeBuilder = new Type.Builder(mRS, Element.I32(mRS));
        typeBuilder.setX(width);
        alloc = Allocation.createTyped(mRS, typeBuilder.create());
        int offset = random.nextInt(arr_len);
        int count = arr_len - offset;
        alloc.copy1DRangeFrom(offset, count, inArray);
        alloc.copy1DRangeToUnchecked(offset, count, outArray);

        boolean result = true;
        for (int i = 0; i < count; i++) {
            if (inArray[i] != outArray[i]) {
                result = false;
                break;
            }
        }
        for (int i = count; i < arr_len; i++) {
            if (outArray[i] != 0) {
                result = false;
                break;
            }
        }
        assertTrue("test_Allocationcopy1DRangeToUnchecked_Int Failed, output array does not match input",
                   result);
    }

    public void test_Allocationcopy1DRangeToUnchecked_Float() {
        Random random = new Random(0x172d8ab9);
        int width = random.nextInt(512);
        int arr_len = width;

        float[] inArray = new float[arr_len];
        float[] outArray = new float[arr_len];

        for (int i = 0; i < arr_len; i++) {
            inArray[i] = random.nextFloat();
        }

        Type.Builder typeBuilder = new Type.Builder(mRS, Element.F32(mRS));
        typeBuilder.setX(width);
        alloc = Allocation.createTyped(mRS, typeBuilder.create());
        int offset = random.nextInt(arr_len);
        int count = arr_len - offset;
        alloc.copy1DRangeFrom(offset, count, inArray);
        alloc.copy1DRangeToUnchecked(offset, count, outArray);

        boolean result = true;
        for (int i = 0; i < count; i++) {
            if (inArray[i] != outArray[i]) {
                result = false;
                break;
            }
        }
        for (int i = count; i < arr_len; i++) {
            if (outArray[i] != 0f) {
                result = false;
                break;
            }
        }
        assertTrue("test_Allocationcopy1DRangeToUnchecked_Float Failed, output array does not match input",
                   result);
    }

    public void test_Allocationcopy1DRangeToUnchecked_Long() {
        Random random = new Random(0x172d8ab9);
        int width = random.nextInt(512);
        int arr_len = width;

        long[] inArray = new long[arr_len];
        long[] outArray = new long[arr_len];

        for (int i = 0; i < arr_len; i++) {
            inArray[i] = random.nextLong();
        }

        Type.Builder typeBuilder = new Type.Builder(mRS, Element.I64(mRS));
        typeBuilder.setX(width);
        alloc = Allocation.createTyped(mRS, typeBuilder.create());
        int offset = random.nextInt(arr_len);
        int count = arr_len - offset;
        alloc.copy1DRangeFrom(offset, count, inArray);
        alloc.copy1DRangeToUnchecked(offset, count, outArray);

        boolean result = true;
        for (int i = 0; i < count; i++) {
            if (inArray[i] != outArray[i]) {
                result = false;
                break;
            }
        }
        for (int i = count; i < arr_len; i++) {
            if (outArray[i] != 0) {
                result = false;
                break;
            }
        }
        assertTrue("test_Allocationcopy1DRangeToUnchecked_Long Failed, output array does not match input",
                   result);
    }
}
