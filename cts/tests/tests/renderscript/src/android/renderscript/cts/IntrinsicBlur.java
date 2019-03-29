/*
 * Copyright (C) 2013 The Android Open Source Project
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

public class IntrinsicBlur extends IntrinsicBase {
    private ScriptIntrinsicBlur mIntrinsic;
    private int MAX_RADIUS = 25;
    private ScriptC_intrinsic_blur mScript;
    private float mRadius = MAX_RADIUS;
    private float mSaturation = 1.0f;
    private Allocation mScratchPixelsAllocation1;
    private Allocation mScratchPixelsAllocation2;

    @Override
    protected void tearDown() throws Exception {
        if (mIntrinsic != null) {
            mIntrinsic.destroy();
        }
        if (mScript != null) {
            mScript.destroy();
        }
        if (mScratchPixelsAllocation1 != null) {
            mScratchPixelsAllocation1.destroy();
        }
        if (mScratchPixelsAllocation2 != null) {
            mScratchPixelsAllocation2.destroy();
        }
        super.tearDown();
    }

    private void initTest(int w, int h, Element e, Script.LaunchOptions lo) {
        makeBuffers(w, h, e);

        Type.Builder tb = new Type.Builder(mRS, Element.F32_4(mRS));
        tb.setX(w);
        tb.setY(h);
        mScratchPixelsAllocation1 = Allocation.createTyped(mRS, tb.create());
        mScratchPixelsAllocation2 = Allocation.createTyped(mRS, tb.create());

        mIntrinsic = ScriptIntrinsicBlur.create(mRS, e);
        mIntrinsic.setRadius(MAX_RADIUS);
        mIntrinsic.setInput(mAllocSrc);

        mScript = new ScriptC_intrinsic_blur(mRS);
        mScript.set_width(w);
        mScript.set_height(h);
        mScript.invoke_setRadius(MAX_RADIUS);

        mScript.set_ScratchPixel1(mScratchPixelsAllocation1);
        mScript.set_ScratchPixel2(mScratchPixelsAllocation2);

        // Make reference
        copyInput();
        mScript.forEach_horz(mScratchPixelsAllocation2);
        mScript.forEach_vert(mScratchPixelsAllocation1);
        copyOutput(lo);
    }

    private void copyInput() {
        if (mAllocSrc.getType().getElement().isCompatible(Element.U8(mRS))) {
            mScript.forEach_convert1_uToF(mAllocSrc, mScratchPixelsAllocation1);
            return;
        }
        if (mAllocSrc.getType().getElement().isCompatible(Element.U8_4(mRS))) {
            mScript.forEach_convert4_uToF(mAllocSrc, mScratchPixelsAllocation1);
            return;
        }
        throw new IllegalArgumentException("bad type");
    }

    private void copyOutput(Script.LaunchOptions lo) {
        if (mAllocSrc.getType().getElement().isCompatible(Element.U8(mRS))) {
            mScript.forEach_convert1_fToU(mScratchPixelsAllocation1, mAllocRef, lo);
            return;
        }
        if (mAllocSrc.getType().getElement().isCompatible(Element.U8_4(mRS))) {
            mScript.forEach_convert4_fToU(mScratchPixelsAllocation1, mAllocRef, lo);
            return;
        }
        throw new IllegalArgumentException("bad type");
    }

    public void testU8_1() {
        final int w = 97;
        final int h = 97;
        Element e = Element.U8(mRS);
        initTest(w, h, e, null);

        mIntrinsic.forEach(mAllocDst);

        mVerify.set_gAllowedIntError(1);
        mVerify.invoke_verify(mAllocRef, mAllocDst, mAllocSrc);
        mRS.finish();
        checkError();
    }

    public void testU8_4() {
        final int w = 97;
        final int h = 97;
        Element e = Element.U8_4(mRS);
        initTest(w, h, e, null);

        mIntrinsic.forEach(mAllocDst);

        mVerify.set_gAllowedIntError(1);
        mVerify.invoke_verify(mAllocRef, mAllocDst, mAllocSrc);
        mRS.finish();
        checkError();
    }


    public void testU8_1C() {
        final int w = 97;
        final int h = 97;
        Element e = Element.U8(mRS);
        Script.LaunchOptions lo = makeClipper(11, 11, w - 11, h - 11);

        initTest(w, h, e, lo);
        mIntrinsic.forEach(mAllocDst, lo);

        mVerify.set_gAllowedIntError(1);
        mVerify.invoke_verify(mAllocRef, mAllocDst, mAllocSrc);
        mRS.finish();
        checkError();
    }

    public void testU8_4C() {
        final int w = 97;
        final int h = 97;
        Element e = Element.U8_4(mRS);
        Script.LaunchOptions lo = makeClipper(11, 11, w - 11, h - 11);

        initTest(w, h, e, lo);
        mIntrinsic.forEach(mAllocDst, lo);

        mVerify.set_gAllowedIntError(1);
        mVerify.invoke_verify(mAllocRef, mAllocDst, mAllocSrc);
        mRS.finish();
        checkError();
    }

    public void test_ID() {
        ScriptIntrinsicBlur s = ScriptIntrinsicBlur.create(mRS, Element.U8_4(mRS));
        Script.KernelID kid = s.getKernelID();
        if (kid == null) {
            throw new IllegalStateException("kid must be valid");
        }

        Script.FieldID fid = s.getFieldID_Input();
        if (fid == null) {
            throw new IllegalStateException("fid must be valid");
        }

        s.destroy();
    }

}
