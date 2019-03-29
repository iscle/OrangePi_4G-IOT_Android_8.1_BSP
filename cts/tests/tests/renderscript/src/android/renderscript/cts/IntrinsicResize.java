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

public class IntrinsicResize extends IntrinsicBase {

    static final int inX = 307;
    static final int inY = 157;

    private void testResize(int w, int h, Element.DataType dt, int vecSize, float scaleX, float scaleY) {

        Element e = makeElement(dt, vecSize);

        System.gc();
        makeSource(w, h, e);

        int outW = (int) (w*scaleX);
        int outH = (int) (h*scaleY);
        if (mAllocRef != null) {
            mAllocRef.destroy();
        }
        if (mAllocDst != null) {
            mAllocDst.destroy();
        }
        mAllocRef = makeAllocation(outW, outH, e);
        mAllocDst = makeAllocation(outW, outH, e);

        ScriptIntrinsicResize si = ScriptIntrinsicResize.create(mRS);
        si.setInput(mAllocSrc);
        si.forEach_bicubic(mAllocRef);

        ScriptC_intrinsic_resize sr = new ScriptC_intrinsic_resize(mRS);
        sr.set_scaleX((float)w/outW);
        sr.set_scaleY((float)h/outH);
        sr.set_gIn(mAllocSrc);
        sr.set_gWidthIn(w);
        sr.set_gHeightIn(h);
        if (dt == Element.DataType.UNSIGNED_8) {
            switch(vecSize) {
            case 4:
                sr.forEach_bicubic_U4(mAllocDst);
                break;
            case 3:
                sr.forEach_bicubic_U3(mAllocDst);
                break;
            case 2:
                sr.forEach_bicubic_U2(mAllocDst);
                break;
            case 1:
                sr.forEach_bicubic_U1(mAllocDst);
                break;
            }
        } else {
            switch(vecSize) {
            case 4:
                sr.forEach_bicubic_F4(mAllocDst);
                break;
            case 3:
                sr.forEach_bicubic_F3(mAllocDst);
                break;
            case 2:
                sr.forEach_bicubic_F2(mAllocDst);
                break;
            case 1:
                sr.forEach_bicubic_F1(mAllocDst);
                break;
            }
        }


        mVerify.set_gAllowedIntError(1);
        mVerify.invoke_verify(mAllocRef, mAllocDst, mAllocSrc);
        if (outW == w && outH == h) {
            mVerify.set_gAllowedIntError(0);
            //when scale = 1, check with the original.
            mVerify.invoke_verify(mAllocRef, mAllocSrc, mAllocSrc);
            mVerify.invoke_verify(mAllocDst, mAllocSrc, mAllocSrc);
        }
        mRS.finish();

        si.destroy();
        sr.destroy();
    }


    public void test_U8_4_SCALE10_10_inSquare() {
        testResize(inX, inX, Element.DataType.UNSIGNED_8, 4, 1.f, 1.f);
        checkError();
    }
    public void test_U8_3_SCALE10_10_inSquare() {
        testResize(inX, inX, Element.DataType.UNSIGNED_8, 3, 1.f, 1.f);
        checkError();
    }
    public void test_U8_2_SCALE10_10_inSquare() {
        testResize(inX, inX, Element.DataType.UNSIGNED_8, 2, 1.f, 1.f);
        checkError();
    }
    public void test_U8_1_SCALE10_10_inSquare() {
        testResize(inX, inX, Element.DataType.UNSIGNED_8, 1, 1.f, 1.f);
        checkError();
    }

    public void test_U8_4_SCALE20_20_inSquare() {
        testResize(inX, inX, Element.DataType.UNSIGNED_8, 4, 2.f, 2.f);
        checkError();
    }
    public void test_U8_3_SCALE20_20_inSquare() {
        testResize(inX, inX, Element.DataType.UNSIGNED_8, 3, 2.f, 2.f);
        checkError();
    }
    public void test_U8_2_SCALE20_20_inSquare() {
        testResize(inX, inX, Element.DataType.UNSIGNED_8, 2, 2.f, 2.f);
        checkError();
    }
    public void test_U8_1_SCALE20_20_inSquare() {
        testResize(inX, inX, Element.DataType.UNSIGNED_8, 1, 2.f, 2.f);
        checkError();
    }

    public void test_U8_4_SCALE05_20_inSquare() {
        testResize(inX, inX, Element.DataType.UNSIGNED_8, 4, 0.5f, 2.f);
        checkError();
    }
    public void test_U8_3_SCALE05_20_inSquare() {
        testResize(inX, inX, Element.DataType.UNSIGNED_8, 3, 0.5f, 2.f);
        checkError();
    }
    public void test_U8_2_SCALE05_20_inSquare() {
        testResize(inX, inX, Element.DataType.UNSIGNED_8, 2, 0.5f, 2.f);
        checkError();
    }
    public void test_U8_1_SCALE05_20_inSquare() {
        testResize(inX, inX, Element.DataType.UNSIGNED_8, 1, 0.5f, 2.f);
        checkError();
    }

    public void test_U8_4_SCALE20_05_inSquare() {
        testResize(inX, inX, Element.DataType.UNSIGNED_8, 4, 2.f, 0.5f);
        checkError();
    }
    public void test_U8_3_SCALE20_05_inSquare() {
        testResize(inX, inX, Element.DataType.UNSIGNED_8, 3, 2.f, 0.5f);
        checkError();
    }
    public void test_U8_2_SCALE20_05_inSquare() {
        testResize(inX, inX, Element.DataType.UNSIGNED_8, 2, 2.f, 0.5f);
        checkError();
    }
    public void test_U8_1_SCALE20_05_inSquare() {
        testResize(inX, inX, Element.DataType.UNSIGNED_8, 1, 2.f, 0.5f);
        checkError();
    }

    public void test_U8_4_SCALE05_05_inSquare() {
        testResize(inX, inX, Element.DataType.UNSIGNED_8, 4, 0.5f, 0.5f);
        checkError();
    }
    public void test_U8_3_SCALE05_05_inSquare() {
        testResize(inX, inX, Element.DataType.UNSIGNED_8, 3, 0.5f, 0.5f);
        checkError();
    }
    public void test_U8_2_SCALE05_05_inSquare() {
        testResize(inX, inX, Element.DataType.UNSIGNED_8, 2, 0.5f, 0.5f);
        checkError();
    }
    public void test_U8_1_SCALE05_05_inSquare() {
        testResize(inX, inX, Element.DataType.UNSIGNED_8, 1, 0.5f, 0.5f);
        checkError();
    }

    public void test_U8_4_SCALE10_10_inRectangle() {
        testResize(inX, inY, Element.DataType.UNSIGNED_8, 4, 1.f, 1.f);
        checkError();
    }
    public void test_U8_3_SCALE10_10_inRectangle() {
        testResize(inX, inY, Element.DataType.UNSIGNED_8, 3, 1.f, 1.f);
        checkError();
    }
    public void test_U8_2_SCALE10_10_inRectangle() {
        testResize(inX, inY, Element.DataType.UNSIGNED_8, 2, 1.f, 1.f);
        checkError();
    }
    public void test_U8_1_SCALE10_10_inRectangle() {
        testResize(inX, inY, Element.DataType.UNSIGNED_8, 1, 1.f, 1.f);
        checkError();
    }

    public void test_U8_4_SCALE20_20_inRectangle() {
        testResize(inX, inY, Element.DataType.UNSIGNED_8, 4, 2.f, 2.f);
        checkError();
    }
    public void test_U8_3_SCALE20_20_inRectangle() {
        testResize(inX, inY, Element.DataType.UNSIGNED_8, 3, 2.f, 2.f);
        checkError();
    }
    public void test_U8_2_SCALE20_20_inRectangle() {
        testResize(inX, inY, Element.DataType.UNSIGNED_8, 2, 2.f, 2.f);
        checkError();
    }
    public void test_U8_1_SCALE20_20_inRectangle() {
        testResize(inX, inY, Element.DataType.UNSIGNED_8, 1, 2.f, 2.f);
        checkError();
    }

    public void test_U8_4_SCALE05_20_inRectangle() {
        testResize(inX, inY, Element.DataType.UNSIGNED_8, 4, 0.5f, 2.f);
        checkError();
    }
    public void test_U8_3_SCALE05_20_inRectangle() {
        testResize(inX, inY, Element.DataType.UNSIGNED_8, 3, 0.5f, 2.f);
        checkError();
    }
    public void test_U8_2_SCALE05_20_inRectangle() {
        testResize(inX, inY, Element.DataType.UNSIGNED_8, 2, 0.5f, 2.f);
        checkError();
    }
    public void test_U8_1_SCALE05_20_inRectangle() {
        testResize(inX, inY, Element.DataType.UNSIGNED_8, 1, 0.5f, 2.f);
        checkError();
    }

    public void test_U8_4_SCALE20_05_inRectangle() {
        testResize(inX, inY, Element.DataType.UNSIGNED_8, 4, 2.f, 0.5f);
        checkError();
    }
    public void test_U8_3_SCALE20_05_inRectangle() {
        testResize(inX, inY, Element.DataType.UNSIGNED_8, 3, 2.f, 0.5f);
        checkError();
    }
    public void test_U8_2_SCALE20_05_inRectangle() {
        testResize(inX, inY, Element.DataType.UNSIGNED_8, 2, 2.f, 0.5f);
        checkError();
    }
    public void test_U8_1_SCALE20_05_inRectangle() {
        testResize(inX, inY, Element.DataType.UNSIGNED_8, 1, 2.f, 0.5f);
        checkError();
    }

    public void test_U8_4_SCALE05_05_inRectangle() {
        testResize(inX, inY, Element.DataType.UNSIGNED_8, 4, 0.5f, 0.5f);
        checkError();
    }
    public void test_U8_3_SCALE05_05_inRectangle() {
        testResize(inX, inY, Element.DataType.UNSIGNED_8, 3, 0.5f, 0.5f);
        checkError();
    }
    public void test_U8_2_SCALE05_05_inRectangle() {
        testResize(inX, inY, Element.DataType.UNSIGNED_8, 2, 0.5f, 0.5f);
        checkError();
    }
    public void test_U8_1_SCALE05_05_inRectangle() {
        testResize(inX, inY, Element.DataType.UNSIGNED_8, 1, 0.5f, 0.5f);
        checkError();
    }


    public void test_F32_4_SCALE10_10_inSquare() {
        testResize(inX, inX, Element.DataType.FLOAT_32, 4, 1.f, 1.f);
        checkError();
    }
    public void test_F32_3_SCALE10_10_inSquare() {
        testResize(inX, inX, Element.DataType.FLOAT_32, 3, 1.f, 1.f);
        checkError();
    }
    public void test_F32_2_SCALE10_10_inSquare() {
        testResize(inX, inX, Element.DataType.FLOAT_32, 2, 1.f, 1.f);
        checkError();
    }
    public void test_F32_1_SCALE10_10_inSquare() {
        testResize(inX, inX, Element.DataType.FLOAT_32, 1, 1.f, 1.f);
        checkError();
    }

    public void test_F32_4_SCALE20_20_inSquare() {
        testResize(inX, inX, Element.DataType.FLOAT_32, 4, 2.f, 2.f);
        checkError();
    }
    public void test_F32_3_SCALE20_20_inSquare() {
        testResize(inX, inX, Element.DataType.FLOAT_32, 3, 2.f, 2.f);
        checkError();
    }
    public void test_F32_2_SCALE20_20_inSquare() {
        testResize(inX, inX, Element.DataType.FLOAT_32, 2, 2.f, 2.f);
        checkError();
    }
    public void test_F32_1_SCALE20_20_inSquare() {
        testResize(inX, inX, Element.DataType.FLOAT_32, 1, 2.f, 2.f);
        checkError();
    }

    public void test_F32_4_SCALE05_20_inSquare() {
        testResize(inX, inX, Element.DataType.FLOAT_32, 4, 0.5f, 2.f);
        checkError();
    }
    public void test_F32_3_SCALE05_20_inSquare() {
        testResize(inX, inX, Element.DataType.FLOAT_32, 3, 0.5f, 2.f);
        checkError();
    }
    public void test_F32_2_SCALE05_20_inSquare() {
        testResize(inX, inX, Element.DataType.FLOAT_32, 2, 0.5f, 2.f);
        checkError();
    }
    public void test_F32_1_SCALE05_20_inSquare() {
        testResize(inX, inX, Element.DataType.FLOAT_32, 1, 0.5f, 2.f);
        checkError();
    }

    public void test_F32_4_SCALE20_05_inSquare() {
        testResize(inX, inX, Element.DataType.FLOAT_32, 4, 2.f, 0.5f);
        checkError();
    }
    public void test_F32_3_SCALE20_05_inSquare() {
        testResize(inX, inX, Element.DataType.FLOAT_32, 3, 2.f, 0.5f);
        checkError();
    }
    public void test_F32_2_SCALE20_05_inSquare() {
        testResize(inX, inX, Element.DataType.FLOAT_32, 2, 2.f, 0.5f);
        checkError();
    }
    public void test_F32_1_SCALE20_05_inSquare() {
        testResize(inX, inX, Element.DataType.FLOAT_32, 1, 2.f, 0.5f);
        checkError();
    }

    public void test_F32_4_SCALE05_05_inSquare() {
        testResize(inX, inX, Element.DataType.FLOAT_32, 4, 0.5f, 0.5f);
        checkError();
    }
    public void test_F32_3_SCALE05_05_inSquare() {
        testResize(inX, inX, Element.DataType.FLOAT_32, 3, 0.5f, 0.5f);
        checkError();
    }
    public void test_F32_2_SCALE05_05_inSquare() {
        testResize(inX, inX, Element.DataType.FLOAT_32, 2, 0.5f, 0.5f);
        checkError();
    }
    public void test_F32_1_SCALE05_05_inSquare() {
        testResize(inX, inX, Element.DataType.FLOAT_32, 1, 0.5f, 0.5f);
        checkError();
    }

    public void test_F32_4_SCALE10_10_inRectangle() {
        testResize(inX, inY, Element.DataType.FLOAT_32, 4, 1.f, 1.f);
        checkError();
    }
    public void test_F32_3_SCALE10_10_inRectangle() {
        testResize(inX, inY, Element.DataType.FLOAT_32, 3, 1.f, 1.f);
        checkError();
    }
    public void test_F32_2_SCALE10_10_inRectangle() {
        testResize(inX, inY, Element.DataType.FLOAT_32, 2, 1.f, 1.f);
        checkError();
    }
    public void test_F32_1_SCALE10_10_inRectangle() {
        testResize(inX, inY, Element.DataType.FLOAT_32, 1, 1.f, 1.f);
        checkError();
    }

    public void test_F32_4_SCALE20_20_inRectangle() {
        testResize(inX, inY, Element.DataType.FLOAT_32, 4, 2.f, 2.f);
        checkError();
    }
    public void test_F32_3_SCALE20_20_inRectangle() {
        testResize(inX, inY, Element.DataType.FLOAT_32, 3, 2.f, 2.f);
        checkError();
    }
    public void test_F32_2_SCALE20_20_inRectangle() {
        testResize(inX, inY, Element.DataType.FLOAT_32, 2, 2.f, 2.f);
        checkError();
    }
    public void test_F32_1_SCALE20_20_inRectangle() {
        testResize(inX, inY, Element.DataType.FLOAT_32, 1, 2.f, 2.f);
        checkError();
    }

    public void test_F32_4_SCALE05_20_inRectangle() {
        testResize(inX, inY, Element.DataType.FLOAT_32, 4, 0.5f, 2.f);
        checkError();
    }
    public void test_F32_3_SCALE05_20_inRectangle() {
        testResize(inX, inY, Element.DataType.FLOAT_32, 3, 0.5f, 2.f);
        checkError();
    }
    public void test_F32_2_SCALE05_20_inRectangle() {
        testResize(inX, inY, Element.DataType.FLOAT_32, 2, 0.5f, 2.f);
        checkError();
    }
    public void test_F32_1_SCALE05_20_inRectangle() {
        testResize(inX, inY, Element.DataType.FLOAT_32, 1, 0.5f, 2.f);
        checkError();
    }

    public void test_F32_4_SCALE20_05_inRectangle() {
        testResize(inX, inY, Element.DataType.FLOAT_32, 4, 2.f, 0.5f);
        checkError();
    }
    public void test_F32_3_SCALE20_05_inRectangle() {
        testResize(inX, inY, Element.DataType.FLOAT_32, 3, 2.f, 0.5f);
        checkError();
    }
    public void test_F32_2_SCALE20_05_inRectangle() {
        testResize(inX, inY, Element.DataType.FLOAT_32, 2, 2.f, 0.5f);
        checkError();
    }
    public void test_F32_1_SCALE20_05_inRectangle() {
        testResize(inX, inY, Element.DataType.FLOAT_32, 1, 2.f, 0.5f);
        checkError();
    }

    public void test_F32_4_SCALE05_05_inRectangle() {
        testResize(inX, inY, Element.DataType.FLOAT_32, 4, 0.5f, 0.5f);
        checkError();
    }
    public void test_F32_3_SCALE05_05_inRectangle() {
        testResize(inX, inY, Element.DataType.FLOAT_32, 3, 0.5f, 0.5f);
        checkError();
    }
    public void test_F32_2_SCALE05_05_inRectangle() {
        testResize(inX, inY, Element.DataType.FLOAT_32, 2, 0.5f, 0.5f);
        checkError();
    }
    public void test_F32_1_SCALE05_05_inRectangle() {
        testResize(inX, inY, Element.DataType.FLOAT_32, 1, 0.5f, 0.5f);
        checkError();
    }

}
