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
import android.util.Log;

public class Intrinsic3DLut extends IntrinsicBase {
    private Allocation mCube;
    private ScriptC_intrinsic_3dlut mScript;
    private ScriptIntrinsic3DLUT mIntrinsic;
    final int sx = 32;
    final int sy = 32;
    final int sz = 16;

    @Override
    protected void tearDown() throws Exception {
        if (mCube != null) {
            mCube.destroy();
        }
        if (mScript != null) {
            mScript.destroy();
        }
        if (mIntrinsic != null) {
            mIntrinsic.destroy();
        }
        super.tearDown();
    }

    private void genCubeIdent() {
        int dat[] = new int[sx * sy * sz];
        for (int z = 0; z < sz; z++) {
            for (int y = 0; y < sy; y++) {
                for (int x = 0; x < sx; x++ ) {
                    int v = 0xff000000;
                    v |= (0xff * x / (sx - 1));
                    v |= (0xff * y / (sy - 1)) << 8;
                    v |= (0xff * z / (sz - 1)) << 16;
                    dat[z*sy*sx + y*sx + x] = v;
                }
            }
        }

        mCube.copyFromUnchecked(dat);
    }

    private void genCubeRand() {
        java.util.Random r = new java.util.Random(100);
        int dat[] = new int[sx * sy * sz];
        for (int z = 0; z < sz; z++) {
            for (int y = 0; y < sy; y++) {
                for (int x = 0; x < sx; x++ ) {
                    int v = 0xff000000;
                    v |= r.nextInt(0x100);
                    v |= r.nextInt(0x100) << 8;
                    v |= r.nextInt(0x100) << 16;
                    dat[z*sy*sx + y*sx + x] = v;
                }
            }
        }

        mCube.copyFromUnchecked(dat);
    }

    private void initCube() {
        Type.Builder tb = new Type.Builder(mRS, Element.U8_4(mRS));
        tb.setX(sx);
        tb.setY(sy);
        tb.setZ(sz);
        Type t = tb.create();
        mCube = Allocation.createTyped(mRS, t);
        genCubeIdent();

        mScript = new ScriptC_intrinsic_3dlut(mRS);
        mScript.invoke_setCube(mCube);

        mIntrinsic = ScriptIntrinsic3DLUT.create(mRS, Element.U8_4(mRS));
        mIntrinsic.setLUT(mCube);
    }

    public void test1() {
        initCube();
        makeBuffers(97, 97, Element.U8_4(mRS));

        mIntrinsic.forEach(mAllocSrc, mAllocDst);
        mScript.forEach_root(mAllocSrc, mAllocRef);

        mVerify.set_gAllowedIntError(1);
        mVerify.invoke_verify(mAllocRef, mAllocDst, mAllocSrc);
        mRS.finish();
        checkError();
    }

    public void test2() {
        initCube();
        makeBuffers(97, 97, Element.U8_4(mRS));
        genCubeRand();

        mIntrinsic.forEach(mAllocSrc, mAllocDst);
        mScript.forEach_root(mAllocSrc, mAllocRef);

        mVerify.set_gAllowedIntError(2);
        mVerify.invoke_verify(mAllocRef, mAllocDst, mAllocSrc);
        mRS.finish();
        checkError();
    }

    public void test1C() {
        initCube();
        makeBuffers(97, 97, Element.U8_4(mRS));

        Script.LaunchOptions lo = makeClipper(11, 11, 87, 87);

        mIntrinsic.forEach(mAllocSrc, mAllocDst, lo);
        mScript.forEach_root(mAllocSrc, mAllocRef, lo);

        mVerify.set_gAllowedIntError(1);
        mVerify.invoke_verify(mAllocRef, mAllocDst, mAllocSrc);
        mRS.finish();
        checkError();
    }

    public void test2C() {
        initCube();
        makeBuffers(97, 97, Element.U8_4(mRS));
        genCubeRand();

        Script.LaunchOptions lo = makeClipper(11, 11, 87, 87);

        mIntrinsic.forEach(mAllocSrc, mAllocDst, lo);
        mScript.forEach_root(mAllocSrc, mAllocRef, lo);

        mVerify.set_gAllowedIntError(2);
        mVerify.invoke_verify(mAllocRef, mAllocDst, mAllocSrc);
        mRS.finish();
        checkError();
    }

    public void test_ID() {
        ScriptIntrinsic3DLUT s = ScriptIntrinsic3DLUT.create(mRS, Element.U8_4(mRS));
        Script.KernelID kid = s.getKernelID();
        if (kid == null) {
            throw new IllegalStateException("kid must be valid");
        }
        s.destroy();
    }

}
