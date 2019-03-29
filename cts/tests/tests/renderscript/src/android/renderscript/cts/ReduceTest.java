/*
 * Copyright (C) 2016 The Android Open Source Project
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

import android.renderscript.*;
import java.lang.Float;
import java.lang.Math;
import java.util.Arrays;
import java.util.Random;

public class ReduceTest extends RSBaseCompute {
    private ScriptC_reduce mScript;

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        mScript = new ScriptC_reduce(mRS);
        mScript.set_negInf(Float.NEGATIVE_INFINITY);
        mScript.set_posInf(Float.POSITIVE_INFINITY);
        mScript.invoke_setInfsHalf(RSUtils.FLOAT16_NEGATIVE_INFINITY, RSUtils.FLOAT16_POSITIVE_INFINITY);
    }

    @Override
    protected void tearDown() throws Exception {
        mScript.destroy();
        super.tearDown();
    }

    ///////////////////////////////////////////////////////////////////

    private void assertEquals(final float[] javaRslt, final float[] rsRslt) {
        assertEquals("length", javaRslt.length, rsRslt.length);
        for (int i = 0; i < javaRslt.length; ++i)
            assertEquals(String.valueOf(i), javaRslt[i], rsRslt[i]);
    }

    private void assertEquals(final short[] javaRslt, final short[] rsRslt) {
        assertEquals("length", javaRslt.length, rsRslt.length);
        for (int i = 0; i < javaRslt.length; ++i)
            assertEquals(String.valueOf(i), javaRslt[i], rsRslt[i]);
    }

    private void assertEquals(final Short2[] javaRslt, final Short2[] rsRslt) {
        assertEquals("length", javaRslt.length, rsRslt.length);
        for (int i = 0; i < javaRslt.length; ++i)
            assertEquals(String.valueOf(i), javaRslt[i], rsRslt[i]);
    }

    private void assertEquals(final String msg, final Float2 javaRslt, final Float2 rsRslt) {
        assertEquals(msg + "(x)", javaRslt.x, rsRslt.x);
        assertEquals(msg + "(y)", javaRslt.y, rsRslt.y);
    }

    private void assertEquals(final Float2 javaRslt, final Float2 rsRslt) {
        assertEquals("", javaRslt, rsRslt);
    }

    private void assertEquals(final String msg, final Int2 javaRslt, final Int2 rsRslt) {
        assertEquals(msg + "(x)", javaRslt.x, rsRslt.x);
        assertEquals(msg + "(y)", javaRslt.y, rsRslt.y);
    }

    private void assertEquals(final Int2 javaRslt, final Int2 rsRslt) {
        assertEquals("", javaRslt, rsRslt);
    }

    private void assertEquals(final String msg, final Short2 javaRslt, final Short2 rsRslt) {
        assertEquals(msg + "(x)", javaRslt.x, rsRslt.x);
        assertEquals(msg + "(y)", javaRslt.y, rsRslt.y);
    }

    private void assertEquals(final Short2 javaRslt, final Short2 rsRslt) {
        assertEquals("", javaRslt, rsRslt);
    }

    // Create a zero-initialized Allocation.
    // 1D: ylen == 0, zlen == 0
    // 2D: ylen != 0, zlen == 0
    // 3D: ylen != 0, zlen != 0
    private Allocation createInputAllocation(Element elt, int xlen, int ylen, int zlen) {
        assertTrue(xlen >= 1);
        assertTrue((zlen==0) || (ylen >= 1));

        Allocation alloc;

        if (zlen != 0)
            alloc = Allocation.createTyped(mRS, Type.createXYZ(mRS, elt, xlen, ylen, zlen));
        else if (ylen != 0)
            alloc = Allocation.createTyped(mRS, Type.createXY(mRS, elt, xlen, ylen));
        else
            alloc = Allocation.createSized(mRS, elt, xlen);
        if (elt.getVectorSize() == 3)
            alloc.setAutoPadding(true);

        byte[] init = new byte[alloc.getBytesSize()];
        Arrays.fill(init, (byte)0);
        alloc.copyFromUnchecked(init);
        return alloc;
    }

    // Create an arry of zero-initialized Allocations of various dimensions --
    // all possible 1D, 2D, and 3D Allocations where no dimension exceeds max.
    private Allocation[] createInputAllocations(Element elt, int max) {
        // 1D Allocations: { 1..max }
        // 2D Allocations: { 1..max }^2
        // 3D Allocations: { 1..max }^3
        final int numAllocs = max + max*max + max*max*max;
        Allocation alloc[] = new Allocation[numAllocs];
        int count = 0;
        for (int xlen = 1; xlen <= max; ++xlen) {
            for (int ylen = 0; ylen <= max; ++ylen) {
                final int zlim = ((ylen!=0) ? max : 0);
                for (int zlen = 0; zlen <= zlim; ++zlen)
                    alloc[count++] = createInputAllocation(elt, xlen, ylen, zlen);
            }
        }
        assertTrue(count == numAllocs);
        return alloc;
    }

    private static byte[] createInputArrayByte(int len, int seed) {
        byte[] array = new byte[len];
        (new Random(seed)).nextBytes(array);
        return array;
    }

    private static short[] createInputArrayHalf(int len, int seed) {
        short[] array = new short[len];
        RSUtils.genRandomFloat16s(seed, Double.NEGATIVE_INFINITY, Double.POSITIVE_INFINITY, array,
            false);
        return array;
    }

    private static float[] createInputArrayFloat(int len, int seed) {
        Random rand = new Random(seed);
        float[] array = new float[len];
        for (int i = 0; i < len; ++i)
            array[i] = rand.nextFloat();
        return array;
    }

    private static int[] createInputArrayInt(int len, int seed) {
        Random rand = new Random(seed);
        int[] array = new int[len];
        for (int i = 0; i < len; ++i)
            array[i] = rand.nextInt();
        return array;
    }

    private static int[] createInputArrayInt(int len, int seed, int eltRange) {
        Random rand = new Random(seed);
        int[] array = new int[len];
        for (int i = 0; i < len; ++i)
            array[i] = rand.nextInt(eltRange);
        return array;
    }

    ///////////////////////////////////////////////////////////////////

    private int addint(final int[] input) {
        int rslt = 0;
        for (int idx = 0; idx < input.length; ++idx)
            rslt += input[idx];
        return rslt;
    }

    public void testAddInt1D() {
        final int[] input = createInputArrayInt(100000, 0, 1 << 13);

        final int javaRslt = addint(input);
        final int rsRslt = mScript.reduce_addint(input).get();

        assertEquals(javaRslt, rsRslt);
    }

    public void testAddInt2D() {
        final int dimX = 450, dimY = 225;

        final int[] inputArray = createInputArrayInt(dimX * dimY, 1, 1 << 13);
        Type.Builder typeBuilder = new Type.Builder(mRS, Element.I32(mRS));
        typeBuilder.setX(dimX).setY(dimY);
        Allocation inputAllocation = Allocation.createTyped(mRS, typeBuilder.create());
        inputAllocation.copy2DRangeFrom(0, 0, dimX, dimY, inputArray);

        final int javaRslt = addint(inputArray);
        final int rsRslt = mScript.reduce_addint(inputAllocation).get();

        assertEquals(javaRslt, rsRslt);

        inputAllocation.destroy();
    }

    ///////////////////////////////////////////////////////////////////

    private Int2 findMinAndMax(final float[] input) {
        float minVal = Float.POSITIVE_INFINITY;
        int minIdx = -1;
        float maxVal = Float.NEGATIVE_INFINITY;
        int maxIdx = -1;

        for (int idx = 0; idx < input.length; ++idx) {
            if (input[idx] < minVal) {
                minVal = input[idx];
                minIdx = idx;
            }
            if (input[idx] > maxVal) {
                maxVal = input[idx];
                maxIdx = idx;
            }
        }

        return new Int2(minIdx, maxIdx);
    }

    public void testFindMinAndMax() {
        final float[] input = createInputArrayFloat(100000, 4);

        final Int2 javaRslt = findMinAndMax(input);
        final Int2 rsRslt = mScript.reduce_findMinAndMax(input).get();

        // Note that the Java and RenderScript algorithms are not
        // guaranteed to find the same cells -- but they should
        // find cells of the same value.
        final Float2 javaVal = new Float2(input[javaRslt.x], input[javaRslt.y]);
        final Float2 rsVal = new Float2(input[rsRslt.x], input[rsRslt.y]);

        assertEquals(javaVal, rsVal);
    }

    ///////////////////////////////////////////////////////////////////

    private Short2 findMinAndMaxHalf(final short[] inputArray) {
        Allocation inputAllocation = Allocation.createSized(mRS, Element.F16(mRS), inputArray.length);
        inputAllocation.copyFrom(inputArray);

        Allocation outputAllocation = Allocation.createSized(mRS, Element.F16_2(mRS), 1);

        mScript.invoke_findMinAndMaxHalf(outputAllocation, inputAllocation);

        short[] outputArray = new short[2];
        outputAllocation.copyTo(outputArray);
        return new Short2(outputArray[0], outputArray[1]);
    }

    private short[] findMinAndMaxHalfIntoArray(final short[] inputArray) {
        final Short2 vectorResult = findMinAndMaxHalf(inputArray);
        final short[] arrayResult = new short[] { vectorResult.x, vectorResult.y };
        return arrayResult;
    }

    public void testFindMinAndMaxHalf() {
        // fewer members in the array than there are distinct half values
        final short[] input = createInputArrayHalf(1000, 23);

        // test Short2 result
        final Short2 javaRslt = findMinAndMaxHalf(input);
        final Short2 rsRslt = mScript.reduce_findMinAndMaxHalf(input).get();
        assertEquals(javaRslt, rsRslt);

        // test short[2] result
        final short[] javaRsltIntoArray = findMinAndMaxHalfIntoArray(input);
        final short[] rsRsltIntoArray = mScript.reduce_findMinAndMaxHalfIntoArray(input).get();
        assertEquals(javaRsltIntoArray, rsRsltIntoArray);
    }

    ///////////////////////////////////////////////////////////////////

    // The input is a flattened representation of an array of 2-vector
    private Short2[] findMinAndMaxHalf2(final short[] inputArray) {
        assertEquals(inputArray.length % 2, 0);

        Allocation inputAllocation = Allocation.createSized(mRS, Element.F16_2(mRS), inputArray.length / 2);
        inputAllocation.copyFrom(inputArray);

        Allocation outputAllocation = Allocation.createSized(mRS, Element.F16_2(mRS), 2);

        mScript.invoke_findMinAndMaxHalf2(outputAllocation, inputAllocation);

        short[] outputArray = new short[4];
        outputAllocation.copyTo(outputArray);

        inputAllocation.destroy();
        outputAllocation.destroy();
        return new Short2[] { new Short2(outputArray[0], outputArray[1]),
                              new Short2(outputArray[2], outputArray[3]) };
    }

    public void testFindMinAndMaxHalf2() {
        // fewer members in the array than there are distinct half values
        final short[] input = createInputArrayHalf(1000, 25);

        final Short2[] javaRslt = findMinAndMaxHalf2(input);
        final Short2[] rsRslt = mScript.reduce_findMinAndMaxHalf2(input).get();

        assertEquals(javaRslt, rsRslt);
    }

    ///////////////////////////////////////////////////////////////////

    // Both the input and the result are linearized representations of 2x2 matrices.
    private float[] findMinMat(final float[] inputArray) {
        float[] result = new float[4];
        for (int i = 0; i < 4; ++i)
            result[i] = Float.POSITIVE_INFINITY;

        for (int i = 0; i < inputArray.length; ++i)
            result[i % 4] = Math.min(result[i % 4], inputArray[i]);

        return result;
    }

    public void testFindMinMat() {
        final int length = 100000;

        final float[] inputArray = createInputArrayFloat(4 * length, 24);
        Allocation inputAllocation = Allocation.createSized(mRS, Element.MATRIX_2X2(mRS), length);
        inputAllocation.copyFromUnchecked(inputArray);

        final float[] javaRslt = findMinMat(inputArray);
        final float[] rsRslt = mScript.reduce_findMinMat(inputAllocation).get();

        inputAllocation.destroy();
        assertEquals(javaRslt, rsRslt);
    }

    ///////////////////////////////////////////////////////////////////

    // Both the input and the result are linearized representations of 2x2 matrices.
    private float[] findMinAndMaxMat(final float[] inputArray) {
        float[] result = new float[8];
        for (int i = 0; i < 4; ++i) {
            result[i+0] = Float.POSITIVE_INFINITY;
            result[i+4] = Float.NEGATIVE_INFINITY;
        }

        for (int i = 0; i < inputArray.length; ++i) {
            result[0 + i % 4] = Math.min(result[0 + i % 4], inputArray[i]);
            result[4 + i % 4] = Math.max(result[4 + i % 4], inputArray[i]);
        }

        return result;
    }

    public void testFindMinAndMaxMat() {
        final int length = 100000;

        final float[] inputArray = createInputArrayFloat(4 * length, 26);
        Allocation inputAllocation = Allocation.createSized(mRS, Element.MATRIX_2X2(mRS), length);
        inputAllocation.copyFromUnchecked(inputArray);

        final float[] javaRslt = findMinAndMaxMat(inputArray);
        final float[] rsRslt = mScript.reduce_findMinAndMaxMat(inputAllocation).get();

        inputAllocation.destroy();
        assertEquals(javaRslt, rsRslt);
    }

    ///////////////////////////////////////////////////////////////////

    public void testFz() {
        final int inputLen = 100000;
        int[] input = createInputArrayInt(inputLen, 5);
        // just in case we got unlucky
        input[(new Random(6)).nextInt(inputLen)] = 0;

        final int rsRslt = mScript.reduce_fz(input).get();

        assertEquals("input[" + rsRslt + "]", 0, input[rsRslt]);
    }

    ///////////////////////////////////////////////////////////////////

    public void testFz2() {
        final int dimX = 225, dimY = 450;
        final int inputLen = dimX * dimY;

        int[] inputArray = createInputArrayInt(inputLen, 7);
        // just in case we got unlucky
        inputArray[(new Random(8)).nextInt(inputLen)] = 0;

        Type.Builder typeBuilder = new Type.Builder(mRS, Element.I32(mRS));
        typeBuilder.setX(dimX).setY(dimY);
        Allocation inputAllocation = Allocation.createTyped(mRS, typeBuilder.create());
        inputAllocation.copy2DRangeFrom(0, 0, dimX, dimY, inputArray);

        final Int2 rsRslt = mScript.reduce_fz2(inputAllocation).get();

        final int cellVal = inputArray[rsRslt.x + dimX * rsRslt.y];

        inputAllocation.destroy();
        assertEquals("input[" + rsRslt.x + ", " + rsRslt.y + "]", 0, cellVal);
    }

    ///////////////////////////////////////////////////////////////////

    public void testFz3() {
        final int dimX = 59, dimY = 48, dimZ = 37;
        final int inputLen = dimX * dimY * dimZ;

        int[] inputArray = createInputArrayInt(inputLen, 9);
        // just in case we got unlucky
        inputArray[(new Random(10)).nextInt(inputLen)] = 0;

        Type.Builder typeBuilder = new Type.Builder(mRS, Element.I32(mRS));
        typeBuilder.setX(dimX).setY(dimY).setZ(dimZ);
        Allocation inputAllocation = Allocation.createTyped(mRS, typeBuilder.create());
        inputAllocation.copy3DRangeFrom(0, 0, 0, dimX, dimY, dimZ, inputArray);

        final Int3 rsRslt = mScript.reduce_fz3(inputAllocation).get();

        final int cellVal = inputArray[rsRslt.x + dimX * rsRslt.y + dimX * dimY * rsRslt.z];

        inputAllocation.destroy();
        assertEquals("input[" + rsRslt.x + ", " + rsRslt.y + ", " + rsRslt.z + "]", 0, cellVal);
    }

    ///////////////////////////////////////////////////////////////////

    private static final int histogramBucketCount = 256;

    private long[] histogram(final byte[] inputArray) {
        Allocation inputAllocation = Allocation.createSized(mRS, Element.U8(mRS), inputArray.length);
        inputAllocation.copyFrom(inputArray);

        Allocation outputAllocation = Allocation.createSized(mRS, Element.U32(mRS), histogramBucketCount);

        ScriptIntrinsicHistogram scriptHsg = ScriptIntrinsicHistogram.create(mRS, Element.U8(mRS));
        scriptHsg.setOutput(outputAllocation);
        scriptHsg.forEach(inputAllocation);

        int[] outputArrayMistyped = new int[histogramBucketCount];
        outputAllocation.copyTo(outputArrayMistyped);

        long[] outputArray = new long[histogramBucketCount];
        for (int i = 0; i < histogramBucketCount; ++i)
            outputArray[i] = outputArrayMistyped[i] & (long)0xffffffff;

        inputAllocation.destroy();
        outputAllocation.destroy();
        scriptHsg.destroy();
        return outputArray;
    }

    public void testHistogram() {
        final byte[] inputArray = createInputArrayByte(100000, 11);

        final long[] javaRslt = histogram(inputArray);
        assertEquals("javaRslt unexpected length", histogramBucketCount, javaRslt.length);
        final long[] rsRslt = mScript.reduce_histogram(inputArray).get();
        assertEquals("rsRslt unexpected length", histogramBucketCount, rsRslt.length);

        for (int i = 0; i < histogramBucketCount; ++i) {
            assertEquals("histogram[" + i + "]", javaRslt[i], rsRslt[i]);
        }
    }

    //-----------------------------------------------------------------

    private Int2 mode(final byte[] inputArray) {
        long[] hsg = histogram(inputArray);

        int modeIdx = 0;
        for (int i = 1; i < hsg.length; ++i)
            if (hsg[i] > hsg[modeIdx]) modeIdx =i;
        return new Int2(modeIdx, (int)hsg[modeIdx]);
    }

    public void testMode() {
        final byte[] inputArray = createInputArrayByte(100000, 12);

        final Int2 javaRslt = mode(inputArray);
        final Int2 rsRslt = mScript.reduce_mode(inputArray).get();

        assertEquals(javaRslt, rsRslt);
    }

    ///////////////////////////////////////////////////////////////////

    private int sumXor(final int[] input1, final int[] input2) {
        int sum = 0;
        for (int idx = 0; idx < input1.length; ++idx)
            sum += (input1[idx] ^ input2[idx]);
        return sum;
    }

    public void testSumXor() {
        final int[] input1 = createInputArrayInt(100000, 13, 1 << 13);
        final int[] input2 = createInputArrayInt(100000, 14, 1 << 13);

        final int javaRslt = sumXor(input1, input2);
        final int rsRslt = mScript.reduce_sumxor(input1, input2).get();

        assertEquals(javaRslt, rsRslt);
    }

    public void testBadSumXorInputDimensionMismatch() {
        Allocation[] inputs = createInputAllocations(Element.I32(mRS), 3);

        // try all pairwise combinations of Allocations; we don't care
        // about the result, only whether we correctly recognize
        // whether or not the input Allocations have the same
        // dimensions.
        for (int i = 0; i < inputs.length; ++i) {
            for (int j = 0; j < inputs.length; ++j) {
                try {
                    mScript.reduce_sumxor(inputs[i], inputs[j]);
                    if (i != j)
                        fail("expected RSRuntimeException for dimension mismatch: inputs " + i + " and " + j);
                } catch (RSRuntimeException e) {
                    if (i == j)
                        fail("did not expect RSRuntimeException for dimension match: inputs " + i);
                }
            }
        }

        for (Allocation a : inputs) {
            a.destroy();
        }
    }

    public void testBadSumXorInputLengthMismatch() {
        final int[] input1 = createInputArrayInt(90000, 16, 1 << 13);
        final int[] input2 = createInputArrayInt(100000, 17, 1 << 13);

        // we don't care about the result, only whether we correctly recognize
        // that the input arrays have different dimensions.
        try {
            mScript.reduce_sumxor(input1, input2);
            fail("expected RSRuntimeException for mismatched array input lengths");
        } catch (RSRuntimeException e) {
        }
    }

    public void testBadSumXorInputNull() {
        final int[] input = createInputArrayInt(100000, 15, 1 << 13);

        // we don't care about the result, only whether we correctly recognize
        // that the input array is null.

        try {
            mScript.reduce_sumxor(input, null);
            fail("expected RSIllegalArgumentException for null array input");
        } catch (RSIllegalArgumentException e) {
        }

        try {
            mScript.reduce_sumxor(null, input);
            fail("expected RSIllegalArgumentException for null array input");
        } catch (RSIllegalArgumentException e) {
        }
    }

    public void testBadSumXorInputWrongType() {
        Allocation inputI32 = Allocation.createSized(mRS, Element.I32(mRS), 1);

        Allocation badInput[] = new Allocation[]{
            Allocation.createSized(mRS, Element.I16(mRS), 1),
            Allocation.createSized(mRS, Element.I16_2(mRS), 1),
            Allocation.createSized(mRS, Element.I32_2(mRS), 1),
            Allocation.createSized(mRS, Element.U32(mRS), 1)
        };

        // we don't care about the result, only whether we correctly recognize
        // that the input Allocation has the wrong type.

        for (int i = 0; i < badInput.length; ++i) {
            try {
                mScript.reduce_sumxor(inputI32, badInput[i]);
                fail("badInput[" + i + "]: expected RSRuntimeException for wrong input data type");
            } catch (RSRuntimeException e) {
            }

            try {
                mScript.reduce_sumxor(badInput[i], inputI32);
                fail("badInput[" + i + "]: expected RSRuntimeException for wrong input data type");
            } catch (RSRuntimeException e) {
            }
        }

        inputI32.destroy();
        for (Allocation a : badInput) {
            a.destroy();
        }
    }

    ///////////////////////////////////////////////////////////////////

    private long sillySum(final byte[] input1, final float[] input2, final int[] input3) {
        // input3 is a flattened 3-vector
        assertEquals(input1.length, input2.length);
        assertEquals(input1.length * 3, input3.length);

        long sum = 0;
        for (int i = 0; i < input1.length; ++i)
            sum += ((((input1[i] + (long)Math.ceil(Math.log(input2[i]))) + input3[3*i + 0]) + input3[3*i + 1]) + input3[3*i + 2]);
        return sum;
    }

    public void testSillySum() {
        final int length = 100000;

        final byte[] input1 = createInputArrayByte(length, 16);
        final float[] input2 = createInputArrayFloat(length, 17);
        // input3 is a flattened 3-vector
        final int[] input3 = createInputArrayInt(3 * length, 18);

        final long javaRslt = sillySum(input1, input2, input3);
        final long rsRslt = mScript.reduce_sillysum(input1, input2, input3).get();

        assertEquals(javaRslt, rsRslt);
    }

    public void testBadSillySumInputDimensionMismatch() {
        Allocation[] allocs1 = createInputAllocations(Element.I8(mRS), 3);
        Allocation[] allocs2 = createInputAllocations(Element.F32(mRS), 3);
        Allocation[] allocs3 = createInputAllocations(Element.I32_3(mRS), 3);

        // try all tuples of Allocations; we don't care about the
        // result, only whether we correctly recognize whether or not
        // the input Allocations have the same dimensions.
        for (int i = 0; i < allocs1.length; ++i) {
            for (int j = 0; j < allocs2.length; ++j) {
                for (int k = 0; k < allocs3.length; ++k) {
                    final boolean expectException = !((i == j) && (j == k));
                    try {
                        mScript.reduce_sillysum(allocs1[i], allocs2[j], allocs3[k]);
                        if (expectException)
                            fail("expected RSRuntimeException for dimension mismatch: inputs " + i + ", " + j + ", " + k);
                    } catch (RSRuntimeException e) {
                        if (!expectException) {
                            fail("did not expect RSRuntimeException for dimension match: inputs " + i);
                        }
                    }
                }
            }
        }

        for (Allocation a : allocs1) {
            a.destroy();
        }
        for (Allocation a : allocs2) {
            a.destroy();
        }
        for (Allocation a : allocs3) {
            a.destroy();
        }
    }

    public void testBadSillySumInputLengthMismatch() {
        final int[] lengths = new int[]{ 10, 100, 1000 };

        // try all pairwise combinations of lengths; we don't care
        // about the result, only whether we correctly recognize
        // whether or not the input Allocations have the same lengths.
        for (int len1idx = 0; len1idx < lengths.length; ++len1idx) {
            for (int len2idx = 0; len2idx < lengths.length; ++len2idx) {
                for (int len3idx = 0; len3idx < lengths.length; ++len3idx) {

                    final byte[] input1 = createInputArrayByte(lengths[len1idx], 19);
                    final float[] input2 = createInputArrayFloat(lengths[len2idx], 20);
                    // input3 is a flattened 3-vector
                    final int[] input3 = createInputArrayInt(3 * lengths[len3idx], 21);

                    try {
                        mScript.reduce_sillysum(input1, input2, input3);
                        if ((len1idx != len2idx) || (len1idx != len3idx))
                            fail("expected RSRuntimeException for dimension mismatch: inputs " +
                                    len1idx + ", " + len2idx + ", " + len3idx);
                    } catch (RSRuntimeException e) {
                        if ((len1idx == len2idx) && (len1idx == len3idx))
                            fail("did not expect RSRuntimeException for dimension match: inputs " + len1idx);
                    }
                }
            }
        }
    }

    ///////////////////////////////////////////////////////////////////

    private static final long[] oorrGoodResults = new long[]{0L, 1L, 0x7fff_ffff_ffff_ffffL};
    private static final long[] oorrBadResultHalfs = new long[]{0x4000_0000_0000_0000L, 0x4567_89ab_cdef_0123L};

    private static final int[] oorInput = createInputArrayInt(1, 22);

    public void testBadOorrSca() {
        final int[] oorrBadPositions = new int[]{-1, 0};

        for (long goodResult : oorrGoodResults) {
            mScript.set_oorrGoodResult(goodResult);
            for (long badResultHalf : oorrBadResultHalfs) {
                mScript.set_oorrBadResultHalf(badResultHalf);
                for (int badPosition : oorrBadPositions) {
                    mScript.set_oorrBadPos(badPosition);

                    // we don't care about the result, only whether
                    // it's representible.  note that no exception is
                    // thrown until "get()".
                    try {
                        mScript.reduce_oorrSca(oorInput).get();
                        if (badPosition >= 0)
                            fail("expected RSRuntimeException for non-representible result; expected 2*" + badResultHalf);
                    } catch (RSRuntimeException e) {
                        if (badPosition < 0)
                            fail("did not expect RSRuntimeException for representible result; expected " + goodResult);
                    }
                }
            }
        }
    }

    public void testBadOorrVec4() {
        final int[] oorrBadPositions = new int[]{-1, 0, 1, 2, 3};

        for (long goodResult : oorrGoodResults) {
            mScript.set_oorrGoodResult(goodResult);
            for (long badResultHalf : oorrBadResultHalfs) {
                mScript.set_oorrBadResultHalf(badResultHalf);
                for (int badPosition : oorrBadPositions) {
                    mScript.set_oorrBadPos(badPosition);

                    // we don't care about the result, only whether
                    // it's representible.  note that no exception is
                    // thrown until "get()".
                    try {
                        mScript.reduce_oorrVec4(oorInput).get();
                        if (badPosition >= 0)
                            fail("expected RSRuntimeException for non-representible result; expected 2*" + badResultHalf
                                    + " at position " + badPosition);
                    } catch (RSRuntimeException e) {
                        if (badPosition < 0)
                            fail("did not expect RSRuntimeException for representible result; expected " + goodResult);
                    }
                }
            }
        }
    }

    public void testBadOorrArr9() {
        final int[] oorrBadPositions = new int[]{-1, 0, 1, 2, 3, 4, 5, 6, 7, 8};

        for (long goodResult : oorrGoodResults) {
            mScript.set_oorrGoodResult(goodResult);
            for (long badResultHalf : oorrBadResultHalfs) {
                mScript.set_oorrBadResultHalf(badResultHalf);
                for (int badPosition : oorrBadPositions) {
                    mScript.set_oorrBadPos(badPosition);

                    // we don't care about the result, only whether
                    // it's representible.  note that no exception is
                    // thrown until "get()".
                    try {
                        mScript.reduce_oorrArr9(oorInput).get();
                        if (badPosition >= 0)
                            fail("expected RSRuntimeException for non-representible result; expected 2*" + badResultHalf
                                    + " at position " + badPosition);
                    } catch (RSRuntimeException e) {
                        if (badPosition < 0)
                            fail("did not expect RSRuntimeException for representible result; expected " + goodResult);
                    }
                }
            }
        }
    }

    public void testBadOorrArr9Vec4() {
        for (long goodResult : oorrGoodResults) {
            mScript.set_oorrGoodResult(goodResult);
            for (long badResultHalf : oorrBadResultHalfs) {
                mScript.set_oorrBadResultHalf(badResultHalf);
                for (int badPosition = -1; badPosition < 36; ++badPosition) {
                    mScript.set_oorrBadPos(badPosition);

                    // we don't care about the result, only whether
                    // it's representible.  note that no exception is
                    // thrown until "get()".
                    try {
                        mScript.reduce_oorrArr9Vec4(oorInput).get();
                        if (badPosition >= 0)
                            fail("expected RSRuntimeException for non-representible result; expected 2*" + badResultHalf
                                    + " at position " + badPosition);
                    } catch (RSRuntimeException e) {
                        if (badPosition < 0)
                            fail("did not expect RSRuntimeException for representible result; expected " + goodResult);
                    }
                }
            }
        }
    }
}
