 /*
 * Copyright (C) 2014 The Android Open Source Project
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

public class VLoadTest extends RSBaseCompute {

    private ScriptC_vload script;
    private ScriptC_vload_relaxed scriptRelaxed;
    Allocation walkAlloc;
    Allocation inAlloc;
    Allocation outAlloc;
    private static java.util.Random random = new java.util.Random();

    final int w = 253;

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        random.setSeed(10);
        script = new ScriptC_vload(mRS);
        scriptRelaxed = new ScriptC_vload_relaxed(mRS);
    }

    @Override
    protected void tearDown() throws Exception {
        if (walkAlloc != null) {
            walkAlloc.destroy();
        }
        if (inAlloc != null) {
            inAlloc.destroy();
        }
        if (outAlloc != null) {
            outAlloc.destroy();
        }
        script.destroy();
        scriptRelaxed.destroy();
        super.tearDown();
    }

    protected void createWalk() {
        int tmp[] = new int[w];
        boolean b[] = new boolean[w];
        int toCopy = w;
        int i = 0;

        while (toCopy > 0) {
            int x = random.nextInt(w);

            //android.util.Log.v("rs", "x " + x + ", y " + y + ", toCopy " + toCopy);
            while ((x < w) && b[x]) {
                x++;
                if (x >= w) {
                    x = 0;
                }
            }

            int maxsize = 1;
            b[x] = true;
            if ((x+1 < w) && !b[x+1]) {
                maxsize ++;
                b[x+1] = true;
                if ((x+2 < w) && !b[x+2]) {
                    maxsize ++;
                    b[x+2] = true;
                    if ((x+3 < w) && !b[x+3]) {
                        maxsize ++;
                        b[x+3] = true;
                    }
                }
            }

            toCopy -= maxsize;
            tmp[i] = x | (maxsize << 16);
            android.util.Log.v("rs", "x " + x + ", vec " + maxsize);
            i++;
        }

        walkAlloc = Allocation.createSized(mRS, Element.I32(mRS), i);
        walkAlloc.copy1DRangeFrom(0, i, tmp);
    }

    private void testSetup(Type t) {
        createWalk();

        inAlloc = Allocation.createTyped(mRS, t);
        outAlloc = Allocation.createTyped(mRS, t);
        script.set_gAllocIn(inAlloc);
        script.set_gAllocOut(outAlloc);
        scriptRelaxed.set_gAllocIn(inAlloc);
        scriptRelaxed.set_gAllocOut(outAlloc);
    }

    private void verify(byte[] a1, byte[] a2, String s) {
        outAlloc.copyTo(a2);
        for (int i=0; i < w; i++) {
            if (a1[i] != a2[i]) {
                throw new RSRuntimeException(s + a1[i] + ", " + a2[i] + ", at " + i);
            }
            a2[i] = 0;
        }
        outAlloc.copyFrom(a2);
    }

    private void verify(short[] a1, short[] a2, String s) {
        outAlloc.copyTo(a2);
        for (int i=0; i < w; i++) {
            if (a1[i] != a2[i]) {
                throw new RSRuntimeException(s + a1[i] + ", " + a2[i] + ", at " + i);
            }
            a2[i] = 0;
        }
        outAlloc.copyFrom(a2);
    }

    private void verify(int[] a1, int[] a2, String s) {
        outAlloc.copyTo(a2);
        for (int i=0; i < w; i++) {
            if (a1[i] != a2[i]) {
                throw new RSRuntimeException(s + a1[i] + ", " + a2[i] + ", at " + i);
            }
            a2[i] = 0;
        }
        outAlloc.copyFrom(a2);
    }

    private void verify(long[] a1, long[] a2, String s) {
        outAlloc.copyTo(a2);
        for (int i=0; i < w; i++) {
            if (a1[i] != a2[i]) {
                throw new RSRuntimeException(s + a1[i] + ", " + a2[i] + ", at " + i);
            }
            a2[i] = 0;
        }
        outAlloc.copyFrom(a2);
    }

    private void verify(float[] a1, float[] a2, String s) {
        outAlloc.copyTo(a2);
        for (int i=0; i < w; i++) {
            if (a1[i] != a2[i]) {
                throw new RSRuntimeException(s + a1[i] + ", " + a2[i] + ", at " + i);
            }
            a2[i] = 0;
        }
        outAlloc.copyFrom(a2);
    }

    private void verify(double[] a1, double[] a2, String s) {
        outAlloc.copyTo(a2);
        for (int i=0; i < w; i++) {
            if (a1[i] != a2[i]) {
                throw new RSRuntimeException(s + a1[i] + ", " + a2[i] + ", at " + i);
            }
            a2[i] = 0;
        }
        outAlloc.copyFrom(a2);
    }

    private byte[] randomByteArray(int len) {
        byte t[] = new byte[len];
        random.nextBytes(t);
        inAlloc.copyFrom(t);
        return t;
    }

    private short[] randomShortArray(int len) {
        short t[] = new short[len];
        for (int i = 0; i < t.length; i++) {
            t[i] = (short)(random.nextInt() & 0xffff);
        }
        inAlloc.copyFrom(t);
        return t;
    }

    private int[] randomIntArray(int len) {
        int t[] = new int[len];
        for (int i = 0; i < t.length; i++) {
            t[i] = random.nextInt();
        }
        inAlloc.copyFrom(t);
        return t;
    }

    private long[] randomLongArray(int len) {
        long t[] = new long[len];
        for (int i = 0; i < t.length; i++) {
            t[i] = random.nextLong();
        }
        inAlloc.copyFrom(t);
        return t;
    }

    public void testVload_char() {
        testSetup(Type.createX(mRS, Element.I8(mRS), w));
        byte tmp[] = randomByteArray(w);
        byte tmp2[] = new byte[w];
        script.forEach_copy2d_char(walkAlloc);
        verify(tmp, tmp2, "Data mismatch char: ");
    }

    public void testVload_uchar() {
        testSetup(Type.createX(mRS, Element.U8(mRS), w));
        byte tmp[] = randomByteArray(w);
        byte tmp2[] = new byte[w];
        script.forEach_copy2d_uchar(walkAlloc);
        verify(tmp, tmp2, "Data mismatch uchar: ");
    }

    public void testVload_char_relaxed() {
        testSetup(Type.createX(mRS, Element.I8(mRS), w));
        byte tmp[] = randomByteArray(w);
        byte tmp2[] = new byte[w];
        scriptRelaxed.forEach_copy2d_char(walkAlloc);
        verify(tmp, tmp2, "Data mismatch relaxed char: ");
    }

    public void testVload_uchar_relaxed() {
        testSetup(Type.createX(mRS, Element.U8(mRS), w));
        byte tmp[] = randomByteArray(w);
        byte tmp2[] = new byte[w];
        scriptRelaxed.forEach_copy2d_uchar(walkAlloc);
        verify(tmp, tmp2, "Data mismatch relaxed uchar: ");
    }

    public void testVload_short() {
        testSetup(Type.createX(mRS, Element.I16(mRS), w));
        short tmp[] = randomShortArray(w);
        short tmp2[] = new short[w];
        script.forEach_copy2d_short(walkAlloc);
        verify(tmp, tmp2, "Data mismatch short: ");
    }

    public void testVload_ushort() {
        testSetup(Type.createX(mRS, Element.U16(mRS), w));
        short tmp[] = randomShortArray(w);
        short tmp2[] = new short[w];
        script.forEach_copy2d_ushort(walkAlloc);
        verify(tmp, tmp2, "Data mismatch ushort: ");
    }

    public void testVload_short_relaxed() {
        testSetup(Type.createX(mRS, Element.I16(mRS), w));
        short tmp[] = randomShortArray(w);
        short tmp2[] = new short[w];
        scriptRelaxed.forEach_copy2d_short(walkAlloc);
        verify(tmp, tmp2, "Data mismatch relaxed short: ");
    }

    public void testVload_ushort_relaxed() {
        testSetup(Type.createX(mRS, Element.U16(mRS), w));
        short tmp[] = randomShortArray(w);
        short tmp2[] = new short[w];
        scriptRelaxed.forEach_copy2d_ushort(walkAlloc);
        verify(tmp, tmp2, "Data mismatch ushort: ");
    }

    public void testVload_int() {
        testSetup(Type.createX(mRS, Element.I32(mRS), w));
        int tmp[] = randomIntArray(w);
        int tmp2[] = new int[w];
        script.forEach_copy2d_int(walkAlloc);
        verify(tmp, tmp2, "Data mismatch int: ");
    }

    public void testVload_uint() {
        testSetup(Type.createX(mRS, Element.U32(mRS), w));
        int tmp[] = randomIntArray(w);
        int tmp2[] = new int[w];
        script.forEach_copy2d_uint(walkAlloc);
        verify(tmp, tmp2, "Data mismatch uint: ");
    }

    public void testVload_int_relaxed() {
        testSetup(Type.createX(mRS, Element.I32(mRS), w));
        int tmp[] = randomIntArray(w);
        int tmp2[] = new int[w];
        scriptRelaxed.forEach_copy2d_int(walkAlloc);
        verify(tmp, tmp2, "Data mismatch relaxed int: ");
    }

    public void testVload_uint_relaxed() {
        testSetup(Type.createX(mRS, Element.U32(mRS), w));
        int tmp[] = randomIntArray(w);
        int tmp2[] = new int[w];
        scriptRelaxed.forEach_copy2d_uint(walkAlloc);
        verify(tmp, tmp2, "Data mismatch uint: ");
    }

    public void testVload_long() {
        testSetup(Type.createX(mRS, Element.I64(mRS), w));
        long tmp[] = randomLongArray(w);
        long tmp2[] = new long[w];
        script.forEach_copy2d_long(walkAlloc);
        verify(tmp, tmp2, "Data mismatch long: ");
    }

    public void testVload_ulong() {
        testSetup(Type.createX(mRS, Element.U64(mRS), w));
        long tmp[] = randomLongArray(w);
        long tmp2[] = new long[w];
        script.forEach_copy2d_ulong(walkAlloc);
        verify(tmp, tmp2, "Data mismatch ulong: ");
    }
    public void testVload_long_relaxed() {
        testSetup(Type.createX(mRS, Element.I64(mRS), w));
        long tmp[] = randomLongArray(w);
        long tmp2[] = new long[w];
        scriptRelaxed.forEach_copy2d_long(walkAlloc);
        verify(tmp, tmp2, "Data mismatch relaxed long: ");
    }
    public void testVload_ulong_relaxed() {
        testSetup(Type.createX(mRS, Element.U64(mRS), w));
        long tmp[] = randomLongArray(w);
        long tmp2[] = new long[w];
        scriptRelaxed.forEach_copy2d_ulong(walkAlloc);
        verify(tmp, tmp2, "Data mismatch ulong: ");
    }

    public void testVload_float() {
        testSetup(Type.createX(mRS, Element.F32(mRS), w));
        float tmp[] = new float[w];
        float tmp2[] = new float[w];
        for (int i=0; i < w; i++) {
            tmp[i] = random.nextFloat();
        }
        inAlloc.copyFrom(tmp);
        script.forEach_copy2d_float(walkAlloc);
        verify(tmp, tmp2, "Data mismatch float: ");
    }

    public void testVload_float_relaxed() {
        testSetup(Type.createX(mRS, Element.F32(mRS), w));
        float tmp[] = new float[w];
        float tmp2[] = new float[w];
        for (int i=0; i < w; i++) {
            tmp[i] = random.nextFloat();
        }
        inAlloc.copyFrom(tmp);
        scriptRelaxed.forEach_copy2d_float(walkAlloc);
        verify(tmp, tmp2, "Data mismatch relaxed float: ");
    }

    public void testVload_double() {
        testSetup(Type.createX(mRS, Element.F64(mRS), w));
        double tmp[] = new double[w];
        double tmp2[] = new double[w];
        for (int i=0; i < w; i++) {
            tmp[i] = random.nextDouble();
        }
        inAlloc.copyFrom(tmp);
        script.forEach_copy2d_double(walkAlloc);
        verify(tmp, tmp2, "Data mismatch double: ");
    }

    public void testVload_double_relaxed() {
        testSetup(Type.createX(mRS, Element.F64(mRS), w));
        double tmp[] = new double[w];
        double tmp2[] = new double[w];
        for (int i=0; i < w; i++) {
            tmp[i] = random.nextDouble();
        }
        inAlloc.copyFrom(tmp);
        scriptRelaxed.forEach_copy2d_double(walkAlloc);
        verify(tmp, tmp2, "Data mismatch relaxed double: ");
    }

}
