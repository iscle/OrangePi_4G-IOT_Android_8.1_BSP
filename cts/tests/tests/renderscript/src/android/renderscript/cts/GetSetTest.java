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

public class GetSetTest extends RSBaseCompute {

    private ScriptC_getset script;
    private ScriptC_getset_relaxed scriptRelaxed;
    Allocation walkAlloc;
    Allocation in1DAlloc;
    Allocation out1DAlloc;
    Allocation in2DAlloc;
    Allocation out2DAlloc;
    Allocation in3DAlloc;
    Allocation out3DAlloc;
    private static java.util.Random random = new java.util.Random();

    final int gWidth = 252;
    final int gHeight = 31;
    final int gDepth = 4;
    final int gCount = gWidth * gHeight * gDepth;

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        random.setSeed(10);
        script = new ScriptC_getset(mRS);
        scriptRelaxed = new ScriptC_getset_relaxed(mRS);
        script.set_gWidth(gWidth);
        script.set_gHeight(gHeight);
        scriptRelaxed.set_gWidth(gWidth);
        scriptRelaxed.set_gHeight(gHeight);
    }

    @Override
    protected void tearDown() throws Exception {
        if (walkAlloc != null) {
            walkAlloc.destroy();
        }
        if (in1DAlloc != null) {
            in1DAlloc.destroy();
        }
        if (out1DAlloc != null) {
            out1DAlloc.destroy();
        }
        if (in2DAlloc != null) {
            in2DAlloc.destroy();
        }
        if (out2DAlloc != null) {
            out2DAlloc.destroy();
        }
        if (in3DAlloc != null) {
            in3DAlloc.destroy();
        }
        if (out3DAlloc != null) {
            out3DAlloc.destroy();
        }
        script.destroy();
        scriptRelaxed.destroy();
        super.tearDown();
    }

    protected void createWalk(int vsize) {
        // We do a random copy order to attempt to get multiple threads
        // reading and writing the same cache line
        // We could do this as a simple walk but that would likely miss
        // some caching issues.
        final int tw = gCount / vsize;
        int tmp[] = new int[tw];
        boolean b[] = new boolean[tw];
        int toCopy = tw;
        int i = 0;

        while (toCopy > 0) {
            int x = random.nextInt(tw);

            while ((x < tw) && b[x]) {
                x++;
                if (x >= tw) {
                    x = 0;
                }
            }

            b[x] = true;
            toCopy --;

            //android.util.Log.v("rs", "walk  " + i + ", " + x);
            tmp[i++] = x;
        }

        walkAlloc = Allocation.createSized(mRS, Element.I32(mRS), tw);
        walkAlloc.copy1DRangeFrom(0, tw, tmp);
    }

    private void testSetup(Element e) {
        int vs = e.getVectorSize();
        if (vs == 3) {
            vs = 4;
        }
        createWalk(vs);

        Type t1 = Type.createX(mRS, e, gWidth * gHeight * gDepth / vs);
        in1DAlloc = Allocation.createTyped(mRS, t1);
        out1DAlloc = Allocation.createTyped(mRS, t1);
        script.set_gAlloc1DIn(in1DAlloc);
        script.set_gAlloc1DOut(out1DAlloc);
        scriptRelaxed.set_gAlloc1DIn(in1DAlloc);
        scriptRelaxed.set_gAlloc1DOut(out1DAlloc);

        Type t2 = Type.createXY(mRS, e, gWidth / vs, gHeight * gDepth);
        in2DAlloc = Allocation.createTyped(mRS, t2);
        out2DAlloc = Allocation.createTyped(mRS, t2);
        script.set_gAlloc2DIn(in2DAlloc);
        script.set_gAlloc2DOut(out2DAlloc);
        scriptRelaxed.set_gAlloc2DIn(in2DAlloc);
        scriptRelaxed.set_gAlloc2DOut(out2DAlloc);

        Type t3 = Type.createXYZ(mRS, e, gWidth / vs, gHeight, gDepth);
        in3DAlloc = Allocation.createTyped(mRS, t3);
        out3DAlloc = Allocation.createTyped(mRS, t3);
        script.set_gAlloc3DIn(in3DAlloc);
        script.set_gAlloc3DOut(out3DAlloc);
        scriptRelaxed.set_gAlloc3DIn(in3DAlloc);
        scriptRelaxed.set_gAlloc3DOut(out3DAlloc);
    }

    private void verify(byte[] a1, byte[] a2, Allocation alloc, String s, int vsize) {
        alloc.copyTo(a2);
        for (int i=0; i < gCount; i++) {
            if (a1[i] != a2[i]) {
                if ((vsize == 3) && ((i % 4) == 3)) {
                    continue;
                }
                throw new RSRuntimeException(s + a1[i] + ", " + a2[i] + ", at " + i);
            }
            a2[i] = 0;
        }
        alloc.copyFrom(a2);
    }

    private void verify(short[] a1, short[] a2, Allocation alloc, String s, int vsize) {
        alloc.copyTo(a2);
        for (int i=0; i < gCount; i++) {
            if (a1[i] != a2[i]) {
                if ((vsize == 3) && ((i % 4) == 3)) {
                    continue;
                }
                throw new RSRuntimeException(s + a1[i] + ", " + a2[i] + ", at " + i);
            }
            a2[i] = 0;
        }
        alloc.copyFrom(a2);
    }

    private void verify(int[] a1, int[] a2, Allocation alloc, String s, int vsize) {
        alloc.copyTo(a2);
        for (int i=0; i < gCount; i++) {
            if (a1[i] != a2[i]) {
                if ((vsize == 3) && ((i % 4) == 3)) {
                    continue;
                }
                throw new RSRuntimeException(s + a1[i] + ", " + a2[i] + ", at " + i);
            }
            a2[i] = 0;
        }
        alloc.copyFrom(a2);
    }

    private void verify(long[] a1, long[] a2, Allocation alloc, String s, int vsize) {
        alloc.copyTo(a2);
        for (int i=0; i < gCount; i++) {
            if (a1[i] != a2[i]) {
                if ((vsize == 3) && ((i % 4) == 3)) {
                    continue;
                }
                throw new RSRuntimeException(s + a1[i] + ", " + a2[i] + ", at " + i);
            }
            a2[i] = 0;
        }
        alloc.copyFrom(a2);
    }

    private void verify(float[] a1, float[] a2, Allocation alloc, String s, int vsize) {
        alloc.copyTo(a2);
        for (int i=0; i < gCount; i++) {
            if (a1[i] != a2[i]) {
                if ((vsize == 3) && ((i % 4) == 3)) {
                    continue;
                }
                throw new RSRuntimeException(s + a1[i] + ", " + a2[i] + ", at " + i);
            }
            a2[i] = 0;
        }
        alloc.copyFrom(a2);
    }

    private void verify(double[] a1, double[] a2, Allocation alloc, String s, int vsize) {
        alloc.copyTo(a2);
        for (int i=0; i < gCount; i++) {
            if (a1[i] != a2[i]) {
                if ((vsize == 3) && ((i % 4) == 3)) {
                    continue;
                }
                throw new RSRuntimeException(s + a1[i] + ", " + a2[i] + ", at " + i);
            }
            a2[i] = 0;
        }
        alloc.copyFrom(a2);
    }

    private byte[] randomByteArray(int len) {
        byte t[] = new byte[len];
        random.nextBytes(t);
        in1DAlloc.copyFrom(t);
        in2DAlloc.copyFrom(t);
        in3DAlloc.copyFrom(t);
        return t;
    }

    private short[] randomShortArray(int len) {
        short t[] = new short[len];
        for (int i = 0; i < t.length; i++) {
            t[i] = (short)(random.nextInt() & 0xffff);
        }
        in1DAlloc.copyFrom(t);
        in2DAlloc.copyFrom(t);
        in3DAlloc.copyFrom(t);
        return t;
    }

    private int[] randomIntArray(int len) {
        int t[] = new int[len];
        for (int i = 0; i < t.length; i++) {
            t[i] = random.nextInt();
        }
        in1DAlloc.copyFrom(t);
        in2DAlloc.copyFrom(t);
        in3DAlloc.copyFrom(t);
        return t;
    }

    private long[] randomLongArray(int len) {
        long t[] = new long[len];
        for (int i = 0; i < t.length; i++) {
            t[i] = random.nextLong();
        }
        in1DAlloc.copyFrom(t);
        in2DAlloc.copyFrom(t);
        in3DAlloc.copyFrom(t);
        return t;
    }

    private float[] randomFloatArray(int len) {
        float t[] = new float[len];
        for (int i = 0; i < t.length; i++) {
            t[i] = random.nextFloat();
        }
        in1DAlloc.copyFrom(t);
        in2DAlloc.copyFrom(t);
        in3DAlloc.copyFrom(t);
        return t;
    }

    private double[] randomDoubleArray(int len) {
        double t[] = new double[len];
        for (int i = 0; i < t.length; i++) {
            t[i] = random.nextDouble();
        }
        in1DAlloc.copyFrom(t);
        in2DAlloc.copyFrom(t);
        in3DAlloc.copyFrom(t);
        return t;
    }

    public void testGetSet_char() {
        testSetup(Element.I8(mRS));
        byte tmp[] = randomByteArray(gCount);
        byte tmp2[] = new byte[gCount];

        script.forEach_copy1D_char(walkAlloc);
        verify(tmp, tmp2, out1DAlloc, "Data mismatch char: ", 1);
        scriptRelaxed.forEach_copy1D_char(walkAlloc);
        verify(tmp, tmp2, out1DAlloc, "Data mismatch relaxed char: ", 1);

        script.forEach_copy2D_char(walkAlloc);
        verify(tmp, tmp2, out2DAlloc, "Data mismatch char: ", 1);
        scriptRelaxed.forEach_copy2D_char(walkAlloc);
        verify(tmp, tmp2, out2DAlloc, "Data mismatch relaxed char: ", 1);

        script.forEach_copy3D_char(walkAlloc);
        verify(tmp, tmp2, out3DAlloc, "Data mismatch char: ", 1);
        scriptRelaxed.forEach_copy3D_char(walkAlloc);
        verify(tmp, tmp2, out3DAlloc, "Data mismatch relaxed char: ", 1);
    }

    public void testGetSet_char2() {
        testSetup(Element.I8_2(mRS));
        byte tmp[] = randomByteArray(gCount);
        byte tmp2[] = new byte[gCount];
        script.forEach_copy1D_char2(walkAlloc);
        verify(tmp, tmp2, out1DAlloc, "Data mismatch char2: ", 2);
        scriptRelaxed.forEach_copy1D_char2(walkAlloc);
        verify(tmp, tmp2, out1DAlloc, "Data mismatch relaxed char2: ", 2);

        script.forEach_copy2D_char2(walkAlloc);
        verify(tmp, tmp2, out2DAlloc, "Data mismatch char2: ", 2);
        scriptRelaxed.forEach_copy2D_char2(walkAlloc);
        verify(tmp, tmp2, out2DAlloc, "Data mismatch relaxed char2: ", 2);

        script.forEach_copy3D_char2(walkAlloc);
        verify(tmp, tmp2, out3DAlloc, "Data mismatch char2: ", 2);
        scriptRelaxed.forEach_copy3D_char2(walkAlloc);
        verify(tmp, tmp2, out3DAlloc, "Data mismatch relaxed char2: ", 2);
    }

    public void testGetSet_char3() {
        testSetup(Element.I8_3(mRS));
        byte tmp[] = randomByteArray(gCount);
        byte tmp2[] = new byte[gCount];
        script.forEach_copy1D_char3(walkAlloc);
        verify(tmp, tmp2, out1DAlloc, "Data mismatch char3: ", 3);
        scriptRelaxed.forEach_copy1D_char3(walkAlloc);
        verify(tmp, tmp2, out1DAlloc, "Data mismatch relaxed char3: ", 3);

        script.forEach_copy2D_char3(walkAlloc);
        verify(tmp, tmp2, out2DAlloc, "Data mismatch char3: ", 3);
        scriptRelaxed.forEach_copy2D_char3(walkAlloc);
        verify(tmp, tmp2, out2DAlloc, "Data mismatch relaxed char3: ", 3);

        script.forEach_copy3D_char3(walkAlloc);
        verify(tmp, tmp2, out3DAlloc, "Data mismatch char3: ", 3);
        scriptRelaxed.forEach_copy3D_char3(walkAlloc);
        verify(tmp, tmp2, out3DAlloc, "Data mismatch relaxed char3: ", 3);
    }

    public void testGetSet_char4() {
        testSetup(Element.I8_4(mRS));
        byte tmp[] = randomByteArray(gCount);
        byte tmp2[] = new byte[gCount];
        script.forEach_copy1D_char4(walkAlloc);
        verify(tmp, tmp2, out1DAlloc, "Data mismatch char4: ", 4);
        scriptRelaxed.forEach_copy1D_char4(walkAlloc);
        verify(tmp, tmp2, out1DAlloc, "Data mismatch relaxed char4: ", 4);

        script.forEach_copy2D_char4(walkAlloc);
        verify(tmp, tmp2, out2DAlloc, "Data mismatch char4: ", 4);
        scriptRelaxed.forEach_copy2D_char4(walkAlloc);
        verify(tmp, tmp2, out2DAlloc, "Data mismatch relaxed char4: ", 4);

        script.forEach_copy3D_char4(walkAlloc);
        verify(tmp, tmp2, out3DAlloc, "Data mismatch char4: ", 4);
        scriptRelaxed.forEach_copy3D_char4(walkAlloc);
        verify(tmp, tmp2, out3DAlloc, "Data mismatch relaxed char4: ", 4);
    }

    public void testGetSet_uchar() {
        testSetup(Element.U8(mRS));
        byte tmp[] = randomByteArray(gCount);
        byte tmp2[] = new byte[gCount];
        script.forEach_copy1D_uchar(walkAlloc);
        verify(tmp, tmp2, out1DAlloc, "Data mismatch uchar: ", 1);
        scriptRelaxed.forEach_copy1D_uchar(walkAlloc);
        verify(tmp, tmp2, out1DAlloc, "Data mismatch relaxed uchar: ", 1);

        script.forEach_copy2D_uchar(walkAlloc);
        verify(tmp, tmp2, out2DAlloc, "Data mismatch uchar: ", 1);
        scriptRelaxed.forEach_copy2D_uchar(walkAlloc);
        verify(tmp, tmp2, out2DAlloc, "Data mismatch relaxed uchar: ", 1);

        script.forEach_copy3D_uchar(walkAlloc);
        verify(tmp, tmp2, out3DAlloc, "Data mismatch uchar: ", 1);
        scriptRelaxed.forEach_copy3D_uchar(walkAlloc);
        verify(tmp, tmp2, out3DAlloc, "Data mismatch relaxed uchar: ", 1);
    }

    public void testGetSet_uchar2() {
        testSetup(Element.U8_2(mRS));
        byte tmp[] = randomByteArray(gCount);
        byte tmp2[] = new byte[gCount];
        script.forEach_copy1D_uchar2(walkAlloc);
        verify(tmp, tmp2, out1DAlloc, "Data mismatch uchar2: ", 2);
        scriptRelaxed.forEach_copy1D_uchar2(walkAlloc);
        verify(tmp, tmp2, out1DAlloc, "Data mismatch relaxed uchar2: ", 2);

        script.forEach_copy2D_uchar2(walkAlloc);
        verify(tmp, tmp2, out2DAlloc, "Data mismatch uchar2: ", 2);
        scriptRelaxed.forEach_copy2D_uchar2(walkAlloc);
        verify(tmp, tmp2, out2DAlloc, "Data mismatch relaxed uchar2: ", 2);

        script.forEach_copy3D_uchar2(walkAlloc);
        verify(tmp, tmp2, out3DAlloc, "Data mismatch uchar2: ", 2);
        scriptRelaxed.forEach_copy3D_uchar2(walkAlloc);
        verify(tmp, tmp2, out3DAlloc, "Data mismatch relaxed uchar2: ", 2);
    }

    public void testGetSet_uchar3() {
        testSetup(Element.U8_3(mRS));
        byte tmp[] = randomByteArray(gCount);
        byte tmp2[] = new byte[gCount];
        script.forEach_copy1D_uchar3(walkAlloc);
        verify(tmp, tmp2, out1DAlloc, "Data mismatch uchar3: ", 3);
        scriptRelaxed.forEach_copy1D_uchar3(walkAlloc);
        verify(tmp, tmp2, out1DAlloc, "Data mismatch relaxed uchar3: ", 3);

        script.forEach_copy2D_uchar3(walkAlloc);
        verify(tmp, tmp2, out2DAlloc, "Data mismatch uchar3: ", 3);
        scriptRelaxed.forEach_copy2D_uchar3(walkAlloc);
        verify(tmp, tmp2, out2DAlloc, "Data mismatch relaxed uchar3: ", 3);

        script.forEach_copy3D_uchar3(walkAlloc);
        verify(tmp, tmp2, out3DAlloc, "Data mismatch uchar3: ", 3);
        scriptRelaxed.forEach_copy3D_uchar3(walkAlloc);
        verify(tmp, tmp2, out3DAlloc, "Data mismatch relaxed uchar3: ", 3);
    }

    public void testGetSet_uchar4() {
        testSetup(Element.U8_4(mRS));
        byte tmp[] = randomByteArray(gCount);
        byte tmp2[] = new byte[gCount];
        script.forEach_copy1D_uchar4(walkAlloc);
        verify(tmp, tmp2, out1DAlloc, "Data mismatch uchar4: ", 4);
        scriptRelaxed.forEach_copy1D_uchar4(walkAlloc);
        verify(tmp, tmp2, out1DAlloc, "Data mismatch relaxed uchar4: ", 4);

        script.forEach_copy2D_uchar4(walkAlloc);
        verify(tmp, tmp2, out2DAlloc, "Data mismatch uchar4: ", 4);
        scriptRelaxed.forEach_copy2D_uchar4(walkAlloc);
        verify(tmp, tmp2, out2DAlloc, "Data mismatch relaxed uchar4: ", 4);

        script.forEach_copy3D_uchar4(walkAlloc);
        verify(tmp, tmp2, out3DAlloc, "Data mismatch uchar4: ", 4);
        scriptRelaxed.forEach_copy3D_uchar4(walkAlloc);
        verify(tmp, tmp2, out3DAlloc, "Data mismatch relaxed uchar4: ", 4);
    }






    public void testGetSet_short() {
        testSetup(Element.I16(mRS));
        short tmp[] = randomShortArray(gCount);
        short tmp2[] = new short[gCount];
        script.forEach_copy1D_short(walkAlloc);
        verify(tmp, tmp2, out1DAlloc, "Data mismatch short: ", 1);
        scriptRelaxed.forEach_copy1D_short(walkAlloc);
        verify(tmp, tmp2, out1DAlloc, "Data mismatch relaxed short: ", 1);

        script.forEach_copy2D_short(walkAlloc);
        verify(tmp, tmp2, out2DAlloc, "Data mismatch short: ", 1);
        scriptRelaxed.forEach_copy2D_short(walkAlloc);
        verify(tmp, tmp2, out2DAlloc, "Data mismatch relaxed short: ", 1);

        script.forEach_copy3D_short(walkAlloc);
        verify(tmp, tmp2, out3DAlloc, "Data mismatch short: ", 1);
        scriptRelaxed.forEach_copy3D_short(walkAlloc);
        verify(tmp, tmp2, out3DAlloc, "Data mismatch relaxed short: ", 1);
    }

    public void testGetSet_short2() {
        testSetup(Element.I16_2(mRS));
        short tmp[] = randomShortArray(gCount);
        short tmp2[] = new short[gCount];
        script.forEach_copy1D_short2(walkAlloc);
        verify(tmp, tmp2, out1DAlloc, "Data mismatch short2: ", 2);
        scriptRelaxed.forEach_copy1D_short2(walkAlloc);
        verify(tmp, tmp2, out1DAlloc, "Data mismatch relaxed short2: ", 2);

        script.forEach_copy2D_short2(walkAlloc);
        verify(tmp, tmp2, out2DAlloc, "Data mismatch short2: ", 2);
        scriptRelaxed.forEach_copy2D_short2(walkAlloc);
        verify(tmp, tmp2, out2DAlloc, "Data mismatch relaxed short2: ", 2);

        script.forEach_copy3D_short2(walkAlloc);
        verify(tmp, tmp2, out3DAlloc, "Data mismatch short2: ", 2);
        scriptRelaxed.forEach_copy3D_short2(walkAlloc);
        verify(tmp, tmp2, out3DAlloc, "Data mismatch relaxed short2: ", 2);
    }

    public void testGetSet_short3() {
        testSetup(Element.I16_3(mRS));
        short tmp[] = randomShortArray(gCount);
        short tmp2[] = new short[gCount];
        script.forEach_copy1D_short3(walkAlloc);
        verify(tmp, tmp2, out1DAlloc, "Data mismatch short3: ", 3);
        scriptRelaxed.forEach_copy1D_short3(walkAlloc);
        verify(tmp, tmp2, out1DAlloc, "Data mismatch relaxed short3: ", 3);

        script.forEach_copy2D_short3(walkAlloc);
        verify(tmp, tmp2, out2DAlloc, "Data mismatch short3: ", 3);
        scriptRelaxed.forEach_copy2D_short3(walkAlloc);
        verify(tmp, tmp2, out2DAlloc, "Data mismatch relaxed short3: ", 3);

        script.forEach_copy3D_short3(walkAlloc);
        verify(tmp, tmp2, out3DAlloc, "Data mismatch short3: ", 3);
        scriptRelaxed.forEach_copy3D_short3(walkAlloc);
        verify(tmp, tmp2, out3DAlloc, "Data mismatch relaxed short3: ", 3);
    }

    public void testGetSet_short4() {
        testSetup(Element.I16_4(mRS));
        short tmp[] = randomShortArray(gCount);
        short tmp2[] = new short[gCount];
        script.forEach_copy1D_short4(walkAlloc);
        verify(tmp, tmp2, out1DAlloc, "Data mismatch short4: ", 4);
        scriptRelaxed.forEach_copy1D_short4(walkAlloc);
        verify(tmp, tmp2, out1DAlloc, "Data mismatch relaxed short4: ", 4);

        script.forEach_copy2D_short4(walkAlloc);
        verify(tmp, tmp2, out2DAlloc, "Data mismatch short4: ", 4);
        scriptRelaxed.forEach_copy2D_short4(walkAlloc);
        verify(tmp, tmp2, out2DAlloc, "Data mismatch relaxed short4: ", 4);

        script.forEach_copy3D_short4(walkAlloc);
        verify(tmp, tmp2, out3DAlloc, "Data mismatch short4: ", 4);
        scriptRelaxed.forEach_copy3D_short4(walkAlloc);
        verify(tmp, tmp2, out3DAlloc, "Data mismatch relaxed short4: ", 4);
    }

    public void testGetSet_ushort() {
        testSetup(Element.U16(mRS));
        short tmp[] = randomShortArray(gCount);
        short tmp2[] = new short[gCount];
        script.forEach_copy1D_ushort(walkAlloc);
        verify(tmp, tmp2, out1DAlloc, "Data mismatch ushort: ", 1);
        scriptRelaxed.forEach_copy1D_ushort(walkAlloc);
        verify(tmp, tmp2, out1DAlloc, "Data mismatch relaxed ushort: ", 1);

        script.forEach_copy2D_ushort(walkAlloc);
        verify(tmp, tmp2, out2DAlloc, "Data mismatch ushort: ", 1);
        scriptRelaxed.forEach_copy2D_ushort(walkAlloc);
        verify(tmp, tmp2, out2DAlloc, "Data mismatch relaxed ushort: ", 1);

        script.forEach_copy3D_ushort(walkAlloc);
        verify(tmp, tmp2, out3DAlloc, "Data mismatch ushort: ", 1);
        scriptRelaxed.forEach_copy3D_ushort(walkAlloc);
        verify(tmp, tmp2, out3DAlloc, "Data mismatch relaxed ushort: ", 1);
    }

    public void testGetSet_ushort2() {
        testSetup(Element.U16_2(mRS));
        short tmp[] = randomShortArray(gCount);
        short tmp2[] = new short[gCount];
        script.forEach_copy1D_ushort2(walkAlloc);
        verify(tmp, tmp2, out1DAlloc, "Data mismatch ushort2: ", 2);
        scriptRelaxed.forEach_copy1D_ushort2(walkAlloc);
        verify(tmp, tmp2, out1DAlloc, "Data mismatch relaxed ushort2: ", 2);

        script.forEach_copy2D_ushort2(walkAlloc);
        verify(tmp, tmp2, out2DAlloc, "Data mismatch ushort2: ", 2);
        scriptRelaxed.forEach_copy2D_ushort2(walkAlloc);
        verify(tmp, tmp2, out2DAlloc, "Data mismatch relaxed ushort2: ", 2);

        script.forEach_copy3D_ushort2(walkAlloc);
        verify(tmp, tmp2, out3DAlloc, "Data mismatch ushort2: ", 2);
        scriptRelaxed.forEach_copy3D_ushort2(walkAlloc);
        verify(tmp, tmp2, out3DAlloc, "Data mismatch relaxed ushort2: ", 2);
    }

    public void testGetSet_ushort3() {
        testSetup(Element.U16_3(mRS));
        short tmp[] = randomShortArray(gCount);
        short tmp2[] = new short[gCount];
        script.forEach_copy1D_ushort3(walkAlloc);
        verify(tmp, tmp2, out1DAlloc, "Data mismatch ushort3: ", 3);
        scriptRelaxed.forEach_copy1D_ushort3(walkAlloc);
        verify(tmp, tmp2, out1DAlloc, "Data mismatch relaxed ushort3: ", 3);

        script.forEach_copy2D_ushort3(walkAlloc);
        verify(tmp, tmp2, out2DAlloc, "Data mismatch ushort3: ", 3);
        scriptRelaxed.forEach_copy2D_ushort3(walkAlloc);
        verify(tmp, tmp2, out2DAlloc, "Data mismatch relaxed ushort3: ", 3);

        script.forEach_copy3D_ushort3(walkAlloc);
        verify(tmp, tmp2, out3DAlloc, "Data mismatch ushort3: ", 3);
        scriptRelaxed.forEach_copy3D_ushort3(walkAlloc);
        verify(tmp, tmp2, out3DAlloc, "Data mismatch relaxed ushort3: ", 3);
    }

    public void testGetSet_ushort4() {
        testSetup(Element.U16_4(mRS));
        short tmp[] = randomShortArray(gCount);
        short tmp2[] = new short[gCount];
        script.forEach_copy1D_ushort4(walkAlloc);
        verify(tmp, tmp2, out1DAlloc, "Data mismatch ushort4: ", 4);
        scriptRelaxed.forEach_copy1D_ushort4(walkAlloc);
        verify(tmp, tmp2, out1DAlloc, "Data mismatch relaxed ushort4: ", 4);

        script.forEach_copy2D_ushort4(walkAlloc);
        verify(tmp, tmp2, out2DAlloc, "Data mismatch ushort4: ", 4);
        scriptRelaxed.forEach_copy2D_ushort4(walkAlloc);
        verify(tmp, tmp2, out2DAlloc, "Data mismatch relaxed ushort4: ", 4);

        script.forEach_copy3D_ushort4(walkAlloc);
        verify(tmp, tmp2, out3DAlloc, "Data mismatch ushort4: ", 4);
        scriptRelaxed.forEach_copy3D_ushort4(walkAlloc);
        verify(tmp, tmp2, out3DAlloc, "Data mismatch relaxed ushort4: ", 4);
    }




    public void testGetSet_int() {
        testSetup(Element.I32(mRS));
        int tmp[] = randomIntArray(gCount);
        int tmp2[] = new int[gCount];
        script.forEach_copy1D_int(walkAlloc);
        verify(tmp, tmp2, out1DAlloc, "Data mismatch int: ", 1);
        scriptRelaxed.forEach_copy1D_int(walkAlloc);
        verify(tmp, tmp2, out1DAlloc, "Data mismatch relaxed int: ", 1);

        script.forEach_copy2D_int(walkAlloc);
        verify(tmp, tmp2, out2DAlloc, "Data mismatch int: ", 1);
        scriptRelaxed.forEach_copy2D_int(walkAlloc);
        verify(tmp, tmp2, out2DAlloc, "Data mismatch relaxed int: ", 1);

        script.forEach_copy3D_int(walkAlloc);
        verify(tmp, tmp2, out3DAlloc, "Data mismatch int: ", 1);
        scriptRelaxed.forEach_copy3D_int(walkAlloc);
        verify(tmp, tmp2, out3DAlloc, "Data mismatch relaxed int: ", 1);
    }

    public void testGetSet_int2() {
        testSetup(Element.I32_2(mRS));
        int tmp[] = randomIntArray(gCount);
        int tmp2[] = new int[gCount];
        script.forEach_copy1D_int2(walkAlloc);
        verify(tmp, tmp2, out1DAlloc, "Data mismatch int2: ", 2);
        scriptRelaxed.forEach_copy1D_int2(walkAlloc);
        verify(tmp, tmp2, out1DAlloc, "Data mismatch relaxed int2: ", 2);

        script.forEach_copy2D_int2(walkAlloc);
        verify(tmp, tmp2, out2DAlloc, "Data mismatch int2: ", 2);
        scriptRelaxed.forEach_copy2D_int2(walkAlloc);
        verify(tmp, tmp2, out2DAlloc, "Data mismatch relaxed int2: ", 2);

        script.forEach_copy3D_int2(walkAlloc);
        verify(tmp, tmp2, out3DAlloc, "Data mismatch int2: ", 2);
        scriptRelaxed.forEach_copy3D_int2(walkAlloc);
        verify(tmp, tmp2, out3DAlloc, "Data mismatch relaxed int2: ", 2);
    }

    public void testGetSet_int3() {
        testSetup(Element.I32_3(mRS));
        int tmp[] = randomIntArray(gCount);
        int tmp2[] = new int[gCount];
        script.forEach_copy1D_int3(walkAlloc);
        verify(tmp, tmp2, out1DAlloc, "Data mismatch int3: ", 3);
        scriptRelaxed.forEach_copy1D_int3(walkAlloc);
        verify(tmp, tmp2, out1DAlloc, "Data mismatch relaxed int3: ", 3);

        script.forEach_copy2D_int3(walkAlloc);
        verify(tmp, tmp2, out2DAlloc, "Data mismatch int3: ", 3);
        scriptRelaxed.forEach_copy2D_int3(walkAlloc);
        verify(tmp, tmp2, out2DAlloc, "Data mismatch relaxed int3: ", 3);

        script.forEach_copy3D_int3(walkAlloc);
        verify(tmp, tmp2, out3DAlloc, "Data mismatch int3: ", 3);
        scriptRelaxed.forEach_copy3D_int3(walkAlloc);
        verify(tmp, tmp2, out3DAlloc, "Data mismatch relaxed int3: ", 3);
    }

    public void testGetSet_int4() {
        testSetup(Element.I32_4(mRS));
        int tmp[] = randomIntArray(gCount);
        int tmp2[] = new int[gCount];
        script.forEach_copy1D_int4(walkAlloc);
        verify(tmp, tmp2, out1DAlloc, "Data mismatch int4: ", 4);
        scriptRelaxed.forEach_copy1D_int4(walkAlloc);
        verify(tmp, tmp2, out1DAlloc, "Data mismatch relaxed int4: ", 4);

        script.forEach_copy2D_int4(walkAlloc);
        verify(tmp, tmp2, out2DAlloc, "Data mismatch int4: ", 4);
        scriptRelaxed.forEach_copy2D_int4(walkAlloc);
        verify(tmp, tmp2, out2DAlloc, "Data mismatch relaxed int4: ", 4);

        script.forEach_copy3D_int4(walkAlloc);
        verify(tmp, tmp2, out3DAlloc, "Data mismatch int4: ", 4);
        scriptRelaxed.forEach_copy3D_int4(walkAlloc);
        verify(tmp, tmp2, out3DAlloc, "Data mismatch relaxed int4: ", 4);
    }

    public void testGetSet_uint() {
        testSetup(Element.U32(mRS));
        int tmp[] = randomIntArray(gCount);
        int tmp2[] = new int[gCount];
        script.forEach_copy1D_uint(walkAlloc);
        verify(tmp, tmp2, out1DAlloc, "Data mismatch uint: ", 1);
        scriptRelaxed.forEach_copy1D_uint(walkAlloc);
        verify(tmp, tmp2, out1DAlloc, "Data mismatch relaxed uint: ", 1);

        script.forEach_copy2D_uint(walkAlloc);
        verify(tmp, tmp2, out2DAlloc, "Data mismatch uint: ", 1);
        scriptRelaxed.forEach_copy2D_uint(walkAlloc);
        verify(tmp, tmp2, out2DAlloc, "Data mismatch relaxed uint: ", 1);

        script.forEach_copy3D_uint(walkAlloc);
        verify(tmp, tmp2, out3DAlloc, "Data mismatch uint: ", 1);
        scriptRelaxed.forEach_copy3D_uint(walkAlloc);
        verify(tmp, tmp2, out3DAlloc, "Data mismatch relaxed uint: ", 1);
    }

    public void testGetSet_uint2() {
        testSetup(Element.U32_2(mRS));
        int tmp[] = randomIntArray(gCount);
        int tmp2[] = new int[gCount];
        script.forEach_copy1D_uint2(walkAlloc);
        verify(tmp, tmp2, out1DAlloc, "Data mismatch uint2: ", 2);
        scriptRelaxed.forEach_copy1D_uint2(walkAlloc);
        verify(tmp, tmp2, out1DAlloc, "Data mismatch relaxed uint2: ", 2);

        script.forEach_copy2D_uint2(walkAlloc);
        verify(tmp, tmp2, out2DAlloc, "Data mismatch uint2: ", 2);
        scriptRelaxed.forEach_copy2D_uint2(walkAlloc);
        verify(tmp, tmp2, out2DAlloc, "Data mismatch relaxed uint2: ", 2);

        script.forEach_copy3D_uint2(walkAlloc);
        verify(tmp, tmp2, out3DAlloc, "Data mismatch uint2: ", 2);
        scriptRelaxed.forEach_copy3D_uint2(walkAlloc);
        verify(tmp, tmp2, out3DAlloc, "Data mismatch relaxed uint2: ", 2);
    }

    public void testGetSet_uint3() {
        testSetup(Element.U32_3(mRS));
        int tmp[] = randomIntArray(gCount);
        int tmp2[] = new int[gCount];
        script.forEach_copy1D_uint3(walkAlloc);
        verify(tmp, tmp2, out1DAlloc, "Data mismatch uint3: ", 3);
        scriptRelaxed.forEach_copy1D_uint3(walkAlloc);
        verify(tmp, tmp2, out1DAlloc, "Data mismatch relaxed uint3: ", 3);

        script.forEach_copy2D_uint3(walkAlloc);
        verify(tmp, tmp2, out2DAlloc, "Data mismatch uint3: ", 3);
        scriptRelaxed.forEach_copy2D_uint3(walkAlloc);
        verify(tmp, tmp2, out2DAlloc, "Data mismatch relaxed uint3: ", 3);

        script.forEach_copy3D_uint3(walkAlloc);
        verify(tmp, tmp2, out3DAlloc, "Data mismatch uint3: ", 3);
        scriptRelaxed.forEach_copy3D_uint3(walkAlloc);
        verify(tmp, tmp2, out3DAlloc, "Data mismatch relaxed uint3: ", 3);
    }

    public void testGetSet_uint4() {
        testSetup(Element.U32_4(mRS));
        int tmp[] = randomIntArray(gCount);
        int tmp2[] = new int[gCount];
        script.forEach_copy1D_uint4(walkAlloc);
        verify(tmp, tmp2, out1DAlloc, "Data mismatch uint4: ", 4);
        scriptRelaxed.forEach_copy1D_uint4(walkAlloc);
        verify(tmp, tmp2, out1DAlloc, "Data mismatch relaxed uint4: ", 4);

        script.forEach_copy2D_uint4(walkAlloc);
        verify(tmp, tmp2, out2DAlloc, "Data mismatch uint4: ", 4);
        scriptRelaxed.forEach_copy2D_uint4(walkAlloc);
        verify(tmp, tmp2, out2DAlloc, "Data mismatch relaxed uint4: ", 4);

        script.forEach_copy3D_uint4(walkAlloc);
        verify(tmp, tmp2, out3DAlloc, "Data mismatch uint4: ", 4);
        scriptRelaxed.forEach_copy3D_uint4(walkAlloc);
        verify(tmp, tmp2, out3DAlloc, "Data mismatch relaxed uint4: ", 4);
    }




    public void testGetSet_long() {
        testSetup(Element.I64(mRS));
        long tmp[] = randomLongArray(gCount);
        long tmp2[] = new long[gCount];
        script.forEach_copy1D_long(walkAlloc);
        verify(tmp, tmp2, out1DAlloc, "Data mismatch long: ", 1);
        scriptRelaxed.forEach_copy1D_long(walkAlloc);
        verify(tmp, tmp2, out1DAlloc, "Data mismatch relaxed long: ", 1);

        script.forEach_copy2D_long(walkAlloc);
        verify(tmp, tmp2, out2DAlloc, "Data mismatch long: ", 1);
        scriptRelaxed.forEach_copy2D_long(walkAlloc);
        verify(tmp, tmp2, out2DAlloc, "Data mismatch relaxed long: ", 1);

        script.forEach_copy3D_long(walkAlloc);
        verify(tmp, tmp2, out3DAlloc, "Data mismatch long: ", 1);
        scriptRelaxed.forEach_copy3D_long(walkAlloc);
        verify(tmp, tmp2, out3DAlloc, "Data mismatch relaxed long: ", 1);
    }

    public void testGetSet_long2() {
        testSetup(Element.I64_2(mRS));
        long tmp[] = randomLongArray(gCount);
        long tmp2[] = new long[gCount];
        script.forEach_copy1D_long2(walkAlloc);
        verify(tmp, tmp2, out1DAlloc, "Data mismatch long2: ", 2);
        scriptRelaxed.forEach_copy1D_long2(walkAlloc);
        verify(tmp, tmp2, out1DAlloc, "Data mismatch relaxed long2: ", 2);

        script.forEach_copy2D_long2(walkAlloc);
        verify(tmp, tmp2, out2DAlloc, "Data mismatch long2: ", 2);
        scriptRelaxed.forEach_copy2D_long2(walkAlloc);
        verify(tmp, tmp2, out2DAlloc, "Data mismatch relaxed long2: ", 2);

        script.forEach_copy3D_long2(walkAlloc);
        verify(tmp, tmp2, out3DAlloc, "Data mismatch long2: ", 2);
        scriptRelaxed.forEach_copy3D_long2(walkAlloc);
        verify(tmp, tmp2, out3DAlloc, "Data mismatch relaxed long2: ", 2);
    }

    public void testGetSet_long3() {
        testSetup(Element.I64_3(mRS));
        long tmp[] = randomLongArray(gCount);
        long tmp2[] = new long[gCount];
        script.forEach_copy1D_long3(walkAlloc);
        verify(tmp, tmp2, out1DAlloc, "Data mismatch long3: ", 3);
        scriptRelaxed.forEach_copy1D_long3(walkAlloc);
        verify(tmp, tmp2, out1DAlloc, "Data mismatch relaxed long3: ", 3);

        script.forEach_copy2D_long3(walkAlloc);
        verify(tmp, tmp2, out2DAlloc, "Data mismatch long3: ", 3);
        scriptRelaxed.forEach_copy2D_long3(walkAlloc);
        verify(tmp, tmp2, out2DAlloc, "Data mismatch relaxed long3: ", 3);

        script.forEach_copy3D_long3(walkAlloc);
        verify(tmp, tmp2, out3DAlloc, "Data mismatch long3: ", 3);
        scriptRelaxed.forEach_copy3D_long3(walkAlloc);
        verify(tmp, tmp2, out3DAlloc, "Data mismatch relaxed long3: ", 3);
    }

    public void testGetSet_long4() {
        testSetup(Element.I64_4(mRS));
        long tmp[] = randomLongArray(gCount);
        long tmp2[] = new long[gCount];
        script.forEach_copy1D_long4(walkAlloc);
        verify(tmp, tmp2, out1DAlloc, "Data mismatch long4: ", 4);
        scriptRelaxed.forEach_copy1D_long4(walkAlloc);
        verify(tmp, tmp2, out1DAlloc, "Data mismatch relaxed long4: ", 4);

        script.forEach_copy2D_long4(walkAlloc);
        verify(tmp, tmp2, out2DAlloc, "Data mismatch long4: ", 4);
        scriptRelaxed.forEach_copy2D_long4(walkAlloc);
        verify(tmp, tmp2, out2DAlloc, "Data mismatch relaxed long4: ", 4);

        script.forEach_copy3D_long4(walkAlloc);
        verify(tmp, tmp2, out3DAlloc, "Data mismatch long4: ", 4);
        scriptRelaxed.forEach_copy3D_long4(walkAlloc);
        verify(tmp, tmp2, out3DAlloc, "Data mismatch relaxed long4: ", 4);
    }

    public void testGetSet_ulong() {
        testSetup(Element.U64(mRS));
        long tmp[] = randomLongArray(gCount);
        long tmp2[] = new long[gCount];
        script.forEach_copy1D_ulong(walkAlloc);
        verify(tmp, tmp2, out1DAlloc, "Data mismatch ulong: ", 1);
        scriptRelaxed.forEach_copy1D_ulong(walkAlloc);
        verify(tmp, tmp2, out1DAlloc, "Data mismatch relaxed ulong: ", 1);

        script.forEach_copy2D_ulong(walkAlloc);
        verify(tmp, tmp2, out2DAlloc, "Data mismatch ulong: ", 1);
        scriptRelaxed.forEach_copy2D_ulong(walkAlloc);
        verify(tmp, tmp2, out2DAlloc, "Data mismatch relaxed ulong: ", 1);

        script.forEach_copy3D_ulong(walkAlloc);
        verify(tmp, tmp2, out3DAlloc, "Data mismatch ulong: ", 1);
        scriptRelaxed.forEach_copy3D_ulong(walkAlloc);
        verify(tmp, tmp2, out3DAlloc, "Data mismatch relaxed ulong: ", 1);
    }

    public void testGetSet_ulong2() {
        testSetup(Element.U64_2(mRS));
        long tmp[] = randomLongArray(gCount);
        long tmp2[] = new long[gCount];
        script.forEach_copy1D_ulong2(walkAlloc);
        verify(tmp, tmp2, out1DAlloc, "Data mismatch ulong2: ", 2);
        scriptRelaxed.forEach_copy1D_ulong2(walkAlloc);
        verify(tmp, tmp2, out1DAlloc, "Data mismatch relaxed ulong2: ", 2);

        script.forEach_copy2D_ulong2(walkAlloc);
        verify(tmp, tmp2, out2DAlloc, "Data mismatch ulong2: ", 2);
        scriptRelaxed.forEach_copy2D_ulong2(walkAlloc);
        verify(tmp, tmp2, out2DAlloc, "Data mismatch relaxed ulong2: ", 2);

        script.forEach_copy3D_ulong2(walkAlloc);
        verify(tmp, tmp2, out3DAlloc, "Data mismatch ulong2: ", 2);
        scriptRelaxed.forEach_copy3D_ulong2(walkAlloc);
        verify(tmp, tmp2, out3DAlloc, "Data mismatch relaxed ulong2: ", 2);
    }

    public void testGetSet_ulong3() {
        testSetup(Element.U64_3(mRS));
        long tmp[] = randomLongArray(gCount);
        long tmp2[] = new long[gCount];
        script.forEach_copy1D_ulong3(walkAlloc);
        verify(tmp, tmp2, out1DAlloc, "Data mismatch ulong3: ", 3);
        scriptRelaxed.forEach_copy1D_ulong3(walkAlloc);
        verify(tmp, tmp2, out1DAlloc, "Data mismatch relaxed ulong3: ", 3);

        script.forEach_copy2D_ulong3(walkAlloc);
        verify(tmp, tmp2, out2DAlloc, "Data mismatch ulong3: ", 3);
        scriptRelaxed.forEach_copy2D_ulong3(walkAlloc);
        verify(tmp, tmp2, out2DAlloc, "Data mismatch relaxed ulong3: ", 3);

        script.forEach_copy3D_ulong3(walkAlloc);
        verify(tmp, tmp2, out3DAlloc, "Data mismatch ulong3: ", 3);
        scriptRelaxed.forEach_copy3D_ulong3(walkAlloc);
        verify(tmp, tmp2, out3DAlloc, "Data mismatch relaxed ulong3: ", 3);
    }

    public void testGetSet_ulong4() {
        testSetup(Element.U64_4(mRS));
        long tmp[] = randomLongArray(gCount);
        long tmp2[] = new long[gCount];
        script.forEach_copy1D_ulong4(walkAlloc);
        verify(tmp, tmp2, out1DAlloc, "Data mismatch ulong4: ", 4);
        scriptRelaxed.forEach_copy1D_ulong4(walkAlloc);
        verify(tmp, tmp2, out1DAlloc, "Data mismatch relaxed ulong4: ", 4);

        script.forEach_copy2D_ulong4(walkAlloc);
        verify(tmp, tmp2, out2DAlloc, "Data mismatch ulong4: ", 4);
        scriptRelaxed.forEach_copy2D_ulong4(walkAlloc);
        verify(tmp, tmp2, out2DAlloc, "Data mismatch relaxed ulong4: ", 4);

        script.forEach_copy3D_ulong4(walkAlloc);
        verify(tmp, tmp2, out3DAlloc, "Data mismatch ulong4: ", 4);
        scriptRelaxed.forEach_copy3D_ulong4(walkAlloc);
        verify(tmp, tmp2, out3DAlloc, "Data mismatch relaxed ulong4: ", 4);
    }




    public void testGetSet_float() {
        testSetup(Element.F32(mRS));
        float tmp[] = randomFloatArray(gCount);
        float tmp2[] = new float[gCount];
        script.forEach_copy1D_float(walkAlloc);
        verify(tmp, tmp2, out1DAlloc, "Data mismatch float: ", 1);
        scriptRelaxed.forEach_copy1D_float(walkAlloc);
        verify(tmp, tmp2, out1DAlloc, "Data mismatch relaxed float: ", 1);

        script.forEach_copy2D_float(walkAlloc);
        verify(tmp, tmp2, out2DAlloc, "Data mismatch float: ", 1);
        scriptRelaxed.forEach_copy2D_float(walkAlloc);
        verify(tmp, tmp2, out2DAlloc, "Data mismatch relaxed float: ", 1);

        script.forEach_copy3D_float(walkAlloc);
        verify(tmp, tmp2, out3DAlloc, "Data mismatch float: ", 1);
        scriptRelaxed.forEach_copy3D_float(walkAlloc);
        verify(tmp, tmp2, out3DAlloc, "Data mismatch relaxed float: ", 1);
    }

    public void testGetSet_float2() {
        testSetup(Element.F32_2(mRS));
        float tmp[] = randomFloatArray(gCount);
        float tmp2[] = new float[gCount];
        script.forEach_copy1D_float2(walkAlloc);
        verify(tmp, tmp2, out1DAlloc, "Data mismatch float2: ", 2);
        scriptRelaxed.forEach_copy1D_float2(walkAlloc);
        verify(tmp, tmp2, out1DAlloc, "Data mismatch relaxed float2: ", 2);

        script.forEach_copy2D_float2(walkAlloc);
        verify(tmp, tmp2, out2DAlloc, "Data mismatch float2: ", 2);
        scriptRelaxed.forEach_copy2D_float2(walkAlloc);
        verify(tmp, tmp2, out2DAlloc, "Data mismatch relaxed float2: ", 2);

        script.forEach_copy3D_float2(walkAlloc);
        verify(tmp, tmp2, out3DAlloc, "Data mismatch float2: ", 2);
        scriptRelaxed.forEach_copy3D_float2(walkAlloc);
        verify(tmp, tmp2, out3DAlloc, "Data mismatch relaxed float2: ", 2);
    }

    public void testGetSet_float3() {
        testSetup(Element.F32_3(mRS));
        float tmp[] = randomFloatArray(gCount);
        float tmp2[] = new float[gCount];
        script.forEach_copy1D_float3(walkAlloc);
        verify(tmp, tmp2, out1DAlloc, "Data mismatch float3: ", 3);
        scriptRelaxed.forEach_copy1D_float3(walkAlloc);
        verify(tmp, tmp2, out1DAlloc, "Data mismatch relaxed float3: ", 3);

        script.forEach_copy2D_float3(walkAlloc);
        verify(tmp, tmp2, out2DAlloc, "Data mismatch float3: ", 3);
        scriptRelaxed.forEach_copy2D_float3(walkAlloc);
        verify(tmp, tmp2, out2DAlloc, "Data mismatch relaxed float3: ", 3);

        script.forEach_copy3D_float3(walkAlloc);
        verify(tmp, tmp2, out3DAlloc, "Data mismatch float3: ", 3);
        scriptRelaxed.forEach_copy3D_float3(walkAlloc);
        verify(tmp, tmp2, out3DAlloc, "Data mismatch relaxed float3: ", 3);
    }

    public void testGetSet_float4() {
        testSetup(Element.F32_4(mRS));
        float tmp[] = randomFloatArray(gCount);
        float tmp2[] = new float[gCount];
        script.forEach_copy1D_float4(walkAlloc);
        verify(tmp, tmp2, out1DAlloc, "Data mismatch float4: ", 4);
        scriptRelaxed.forEach_copy1D_float4(walkAlloc);
        verify(tmp, tmp2, out1DAlloc, "Data mismatch relaxed float4: ", 4);

        script.forEach_copy2D_float4(walkAlloc);
        verify(tmp, tmp2, out2DAlloc, "Data mismatch float4: ", 4);
        scriptRelaxed.forEach_copy2D_float4(walkAlloc);
        verify(tmp, tmp2, out2DAlloc, "Data mismatch relaxed float4: ", 4);

        script.forEach_copy3D_float4(walkAlloc);
        verify(tmp, tmp2, out3DAlloc, "Data mismatch float4: ", 4);
        scriptRelaxed.forEach_copy3D_float4(walkAlloc);
        verify(tmp, tmp2, out3DAlloc, "Data mismatch relaxed float4: ", 4);
    }


    public void testGetSet_double() {
        testSetup(Element.F64(mRS));
        double tmp[] = randomDoubleArray(gCount);
        double tmp2[] = new double[gCount];
        script.forEach_copy1D_double(walkAlloc);
        verify(tmp, tmp2, out1DAlloc, "Data mismatch double: ", 1);
        scriptRelaxed.forEach_copy1D_double(walkAlloc);
        verify(tmp, tmp2, out1DAlloc, "Data mismatch relaxed double: ", 1);

        script.forEach_copy2D_double(walkAlloc);
        verify(tmp, tmp2, out2DAlloc, "Data mismatch double: ", 1);
        scriptRelaxed.forEach_copy2D_double(walkAlloc);
        verify(tmp, tmp2, out2DAlloc, "Data mismatch relaxed double: ", 1);

        script.forEach_copy3D_double(walkAlloc);
        verify(tmp, tmp2, out3DAlloc, "Data mismatch double: ", 1);
        scriptRelaxed.forEach_copy3D_double(walkAlloc);
        verify(tmp, tmp2, out3DAlloc, "Data mismatch relaxed double: ", 1);
    }

    public void testGetSet_double2() {
        testSetup(Element.F64_2(mRS));
        double tmp[] = randomDoubleArray(gCount);
        double tmp2[] = new double[gCount];
        script.forEach_copy1D_double2(walkAlloc);
        verify(tmp, tmp2, out1DAlloc, "Data mismatch double2: ", 2);
        scriptRelaxed.forEach_copy1D_double2(walkAlloc);
        verify(tmp, tmp2, out1DAlloc, "Data mismatch relaxed double2: ", 2);

        script.forEach_copy2D_double2(walkAlloc);
        verify(tmp, tmp2, out2DAlloc, "Data mismatch double2: ", 2);
        scriptRelaxed.forEach_copy2D_double2(walkAlloc);
        verify(tmp, tmp2, out2DAlloc, "Data mismatch relaxed double2: ", 2);

        script.forEach_copy3D_double2(walkAlloc);
        verify(tmp, tmp2, out3DAlloc, "Data mismatch double2: ", 2);
        scriptRelaxed.forEach_copy3D_double2(walkAlloc);
        verify(tmp, tmp2, out3DAlloc, "Data mismatch relaxed double2: ", 2);
    }

    public void testGetSet_double3() {
        testSetup(Element.F64_3(mRS));
        double tmp[] = randomDoubleArray(gCount);
        double tmp2[] = new double[gCount];
        script.forEach_copy1D_double3(walkAlloc);
        verify(tmp, tmp2, out1DAlloc, "Data mismatch double3: ", 3);
        scriptRelaxed.forEach_copy1D_double3(walkAlloc);
        verify(tmp, tmp2, out1DAlloc, "Data mismatch relaxed double3: ", 3);

        script.forEach_copy2D_double3(walkAlloc);
        verify(tmp, tmp2, out2DAlloc, "Data mismatch double3: ", 3);
        scriptRelaxed.forEach_copy2D_double3(walkAlloc);
        verify(tmp, tmp2, out2DAlloc, "Data mismatch relaxed double3: ", 3);

        script.forEach_copy3D_double3(walkAlloc);
        verify(tmp, tmp2, out3DAlloc, "Data mismatch double3: ", 3);
        scriptRelaxed.forEach_copy3D_double3(walkAlloc);
        verify(tmp, tmp2, out3DAlloc, "Data mismatch relaxed double3: ", 3);
    }

    public void testGetSet_double4() {
        testSetup(Element.F64_4(mRS));
        double tmp[] = randomDoubleArray(gCount);
        double tmp2[] = new double[gCount];
        script.forEach_copy1D_double4(walkAlloc);
        verify(tmp, tmp2, out1DAlloc, "Data mismatch double4: ", 4);
        scriptRelaxed.forEach_copy1D_double4(walkAlloc);
        verify(tmp, tmp2, out1DAlloc, "Data mismatch relaxed double4: ", 4);

        script.forEach_copy2D_double4(walkAlloc);
        verify(tmp, tmp2, out2DAlloc, "Data mismatch double4: ", 4);
        scriptRelaxed.forEach_copy2D_double4(walkAlloc);
        verify(tmp, tmp2, out2DAlloc, "Data mismatch relaxed double4: ", 4);

        script.forEach_copy3D_double4(walkAlloc);
        verify(tmp, tmp2, out3DAlloc, "Data mismatch double4: ", 4);
        scriptRelaxed.forEach_copy3D_double4(walkAlloc);
        verify(tmp, tmp2, out3DAlloc, "Data mismatch relaxed double4: ", 4);
    }


}
