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

public class IntrinsicLut extends IntrinsicBase {
    private ScriptIntrinsicLUT mIntrinsic;
    private ScriptC_intrinsic_lut mScript;

    short mRed[] = new short[256];
    short mGreen[] = new short[256];
    short mBlue[] = new short[256];
    short mAlpha[] = new short[256];

    @Override
    protected void tearDown() throws Exception {
        if (mIntrinsic != null) {
            mIntrinsic.destroy();
        }
        if (mScript != null) {
            mScript.destroy();
        }
        super.tearDown();
    }

    public void createTest() {
        java.util.Random r = new java.util.Random(100);

        mIntrinsic = ScriptIntrinsicLUT.create(mRS, Element.U8_4(mRS));
        mScript = new ScriptC_intrinsic_lut(mRS);

        for (int ct=0; ct < 256; ct++) {
            mRed[ct] = (short)r.nextInt(256);
            mGreen[ct] = (short)r.nextInt(256);
            mBlue[ct] = (short)r.nextInt(256);
            mAlpha[ct] = (short)r.nextInt(256);
            mIntrinsic.setRed(ct, mRed[ct]);
            mIntrinsic.setGreen(ct, mGreen[ct]);
            mIntrinsic.setBlue(ct, mBlue[ct]);
            mIntrinsic.setAlpha(ct, mAlpha[ct]);
        }
        mScript.set_red(mRed);
        mScript.set_green(mGreen);
        mScript.set_blue(mBlue);
        mScript.set_alpha(mAlpha);
    }



    public void test() {
        createTest();
        makeBuffers(97, 97, Element.U8_4(mRS));

        mIntrinsic.forEach(mAllocSrc, mAllocDst);
        mScript.forEach_root(mAllocSrc, mAllocRef);

        mVerify.set_gAllowedIntError(0);
        mVerify.invoke_verify(mAllocRef, mAllocDst, mAllocSrc);
        mRS.finish();
        checkError();
    }

    public void test1C() {
        createTest();
        makeBuffers(97, 97, Element.U8_4(mRS));

        Script.LaunchOptions lo = makeClipper(11, 11, 87, 87);

        mIntrinsic.forEach(mAllocSrc, mAllocDst, lo);
        mScript.forEach_root(mAllocSrc, mAllocRef, lo);

        mVerify.set_gAllowedIntError(0);
        mVerify.invoke_verify(mAllocRef, mAllocDst, mAllocSrc);
        mRS.finish();
        checkError();
    }


    public void test_ID() {
        ScriptIntrinsicLUT s = ScriptIntrinsicLUT.create(mRS, Element.U8_4(mRS));
        Script.KernelID kid = s.getKernelID();
        if (kid == null) {
            throw new IllegalStateException("kid must be valid");
        }
        s.destroy();
    }

}
