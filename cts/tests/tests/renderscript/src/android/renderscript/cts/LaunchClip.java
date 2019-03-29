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

import android.renderscript.*;



public class LaunchClip extends RSBaseCompute {
    Allocation mAPassFail;
    Allocation mAin;
    Allocation mAout;
    ScriptC_launchclip mScript;

    int[] mIn;
    int[] mOut;
    int[] mPassFail;

    int mDimX = 0;
    int mDimY = 0;
    int mDimZ = 0;
    boolean mHasFaces = false;
    boolean mHasLods = false;
    int mDimA0 = 0;
    int mDimA1 = 0;
    int mDimA2 = 0;
    int mDimA3 = 0;
    int mCellCount = 0;

    void setup(boolean makeIn, boolean makeOut, int x, int y, int z, boolean face, boolean lods,
               int a0, int a1, int a2, int a3) {

        mDimX = x;
        mDimY = y;
        mDimZ = z;
        mHasFaces = face;
        mHasLods = lods;
        mDimA0 = a0;
        mDimA1 = a1;
        mDimA2 = a2;
        mDimA3 = a3;

        mScript = new ScriptC_launchclip(mRS);
        mScript.set_dimX(mDimX);
        mScript.set_dimY(mDimY);
        mScript.set_dimZ(mDimZ);
        mScript.set_hasFaces(mHasFaces);
        mScript.set_hasLod(mHasLods);
        mScript.set_dimA0(mDimA0);
        mScript.set_dimA1(mDimA1);
        mScript.set_dimA2(mDimA2);
        mScript.set_dimA3(mDimA3);

        Type.Builder tb = new Type.Builder(mRS, Element.I32(mRS));
        tb.setX(mDimX);
        if (mDimY > 0) tb.setY(mDimY);
        if (mDimZ > 0) tb.setZ(mDimZ);
        if (mHasFaces) tb.setFaces(true);
        if (mHasLods) tb.setMipmaps(true);
        //if (mDimA0 != 0) tb.setArray(0, mDimA0);
        //if (mDimA1 != 0) tb.setArray(1, mDimA1);
        //if (mDimA2 != 0) tb.setArray(2, mDimA2);
        //if (mDimA3 != 0) tb.setArray(3, mDimA3);
        Type t = tb.create();

        if (makeIn) {
            mIn = new int[t.getCount()];
            mAin = Allocation.createTyped(mRS, t);
            mScript.forEach_zero(mAin);
        }
        if (makeOut) {
            mOut = new int[t.getCount()];
            mAout = Allocation.createTyped(mRS, t);
            mScript.forEach_zero(mAout);
        }

        mPassFail = new int[1];
        mAPassFail = Allocation.createSized(mRS, Element.U32(mRS), 1);
        mAPassFail.copyFrom(mPassFail);
        mScript.set_passfail(mAPassFail);
    }

    @Override
    protected void tearDown() throws Exception {
        if (mAPassFail != null) {
            mAPassFail.destroy();
        }
        if (mAin != null) {
            mAin.destroy();
        }
        if (mAout != null) {
            mAout.destroy();
        }
        if (mScript != null) {
            mScript.destroy();
        }
        super.tearDown();
    }

    private void verifyCell(int x, int y, int z, int[] a, Script.LaunchOptions sc) {
        int expected = 0x80000000;
        boolean inRange = true;

        if (mDimX != 0) {
            if (x >= sc.getXStart() && x < sc.getXEnd()) {
                expected |= x;
            } else {
                inRange = false;
            }
        }

        if (mDimY != 0) {
            if (y >= sc.getYStart() && y < sc.getYEnd()) {
                expected |= y << 8;
            } else {
                inRange = false;
            }
        }

        if (mDimZ != 0) {
            if (z >= sc.getZStart() && z < sc.getZEnd()) {
                expected |= z << 16;
            } else {
                inRange = false;
            }
        }

        if (!inRange) {
            expected = 0;
        }

        int val = a[x + y * mDimX + z * mDimX * mDimY];
        if (val != expected) {
            String s = new String("verify error @ " + x + ", " + y + ", " + z +
                                  ", expected " + expected + ", got " + val);
            ///android.util.Log.e("rs", s);
            throw new IllegalStateException(s);
        }
    }

    void verifyRange(Script.LaunchOptions sc, int[] a) {
        int itY = (mDimY > 0) ? mDimY : 1;
        int itZ = (mDimZ > 0) ? mDimZ : 1;

        for (int x = 0; x < mDimX; x++) {
            for (int y = 0; y < itY; y++) {
                for (int z = 0; z < itZ; z++) {
                    verifyCell(x, y, z, a, sc);
                }
            }
        }
    }

    AllocationAdapter makeAdapter(Allocation base, int ax, int ay, int az, int ox, int oy, int oz) {
        Type.Builder tb = new Type.Builder(mRS, base.getType().getElement());
        tb.setX(ax);
        if (ay > 0) {
            tb.setY(ay);
        }
        if (az > 0) {
            tb.setZ(az);
        }
        Type t = tb.create();

        AllocationAdapter a = AllocationAdapter.createTyped(mRS, base, t);
        a.setX(ox);
        if (base.getType().getY() > 0) {
            a.setY(oy);
        }
        if (base.getType().getZ() > 0) {
            a.setZ(oz);
        }

        mScript.set_biasX(ox);
        mScript.set_biasY(oy);
        mScript.set_biasZ(oz);
        return a;
    }

    public void testWrite1D() {
        setup(false, true, 256, 0, 0, false, false, 0, 0, 0, 0);
        Script.LaunchOptions sc = new Script.LaunchOptions();
        sc.setX(9, 77);

        mScript.forEach_write1d(mAout, sc);
        mAout.copyTo(mOut);

        verifyRange(sc, mOut);
    }

    public void testWrite1DAdapter1D() {
        setup(false, true, 256, 0, 0, false, false, 0, 0, 0, 0);
        Script.LaunchOptions sc = new Script.LaunchOptions();
        sc.setX(9, 77);

        AllocationAdapter a = makeAdapter(mAout, 68, 0, 0,  9, 0, 0);
        mScript.forEach_write1d(a);
        mAout.copyTo(mOut);

        verifyRange(sc, mOut);
    }


    public void testWrite2D() {
        setup(false, true, 256, 256, 0, false, false, 0, 0, 0, 0);
        Script.LaunchOptions sc = new Script.LaunchOptions();
        sc.setX(9, 77);
        sc.setY(17, 177);

        mScript.forEach_write2d(mAout, sc);
        mAout.copyTo(mOut);

        verifyRange(sc, mOut);
    }

    public void testWrite2DAdapter1D() {
        setup(false, true, 256, 256, 0, false, false, 0, 0, 0, 0);
        Script.LaunchOptions sc = new Script.LaunchOptions();
        sc.setX(9, 77);
        sc.setY(17, 18);

        AllocationAdapter a = makeAdapter(mAout, 68, 0, 0,  9, 17, 0);
        mScript.forEach_write1d(a);
        mAout.copyTo(mOut);

        verifyRange(sc, mOut);
    }

    public void testWrite2DAdapter2D() {
        setup(false, true, 256, 256, 0, false, false, 0, 0, 0, 0);
        Script.LaunchOptions sc = new Script.LaunchOptions();
        sc.setX(9, 77);
        sc.setY(17, 177);

        AllocationAdapter a = makeAdapter(mAout, 68, 160, 0,  9, 17, 0);
        mScript.forEach_write2d(a);
        mAout.copyTo(mOut);

        verifyRange(sc, mOut);
    }

    public void testWrite3D() {
        setup(false, true, 64, 64, 64, false, false, 0, 0, 0, 0);

        Script.LaunchOptions sc = new Script.LaunchOptions();
        sc.setX(9, 37);
        sc.setY(17, 27);
        sc.setZ(7, 21);
        mScript.forEach_write3d(mAout, sc);
        mAout.copyTo(mOut);

        verifyRange(sc, mOut);
    }

    public void testWrite3DAdapter1D() {
        setup(false, true, 64, 64, 64, false, false, 0, 0, 0, 0);

        Script.LaunchOptions sc = new Script.LaunchOptions();
        sc.setX(9, 37);
        sc.setY(17, 18);
        sc.setZ(7, 8);

        AllocationAdapter a = makeAdapter(mAout, 28, 0, 0,  9, 17, 7);
        mScript.forEach_write1d(a);
        mAout.copyTo(mOut);

        verifyRange(sc, mOut);
    }

    public void testWrite3DAdapter2D() {
        setup(false, true, 64, 64, 64, false, false, 0, 0, 0, 0);

        Script.LaunchOptions sc = new Script.LaunchOptions();
        sc.setX(9, 37);
        sc.setY(17, 27);
        sc.setZ(7, 8);

        AllocationAdapter a = makeAdapter(mAout, 28, 10, 0,  9, 17, 7);
        mScript.forEach_write2d(a);
        mAout.copyTo(mOut);

        verifyRange(sc, mOut);
    }

    public void testWrite3DAdapter3D() {
        setup(false, true, 64, 64, 64, false, false, 0, 0, 0, 0);

        Script.LaunchOptions sc = new Script.LaunchOptions();
        sc.setX(9, 37);
        sc.setY(17, 27);
        sc.setZ(7, 21);

        AllocationAdapter a = makeAdapter(mAout, 28, 10, 14,  9, 17, 7);
        mScript.forEach_write3d(a);
        mAout.copyTo(mOut);

        verifyRange(sc, mOut);
    }

}
