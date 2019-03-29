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

package android.cts.rscpp;

import android.content.Context;
import android.content.res.Resources;
import android.test.AndroidTestCase;
import android.renderscript.*;
import android.util.Log;
import java.lang.Integer;

public class RSResizeTest extends RSCppTest {
    static {
        System.loadLibrary("rscpptest_jni");
    }

    private final int inX = 307;
    private final int inY = 157;

    native boolean resizeTest(String path, int w, int h, float scaleX, float scaleY,
                              boolean useByte, int vecSize, byte[] inB, byte[] outB,
                              float[] inF, float[] outF);
    private void testResize(int w, int h, Element.DataType dt, int vecSize, float scaleX, float scaleY) {

        boolean useByte = false;
        if (dt == Element.DataType.UNSIGNED_8) {
            useByte = true;
        }

        Element e = makeElement(dt, vecSize);
        Allocation rsInput = makeAllocation(w, h, e);

        int arrSize = w * h * (vecSize == 3 ? 4 : vecSize);
        int[] baseAlloc = new int[arrSize];
        byte[] byteAlloc = null;
        float[] floatAlloc = null;

        RSUtils.genRandom(0x72727272, 255, 1, -128, baseAlloc);
        if (useByte) {
            byteAlloc = new byte[arrSize];
            for (int i = 0; i < arrSize; i++) {
                byteAlloc[i] = (byte)baseAlloc[i];
            }
            rsInput.copyFromUnchecked(byteAlloc);
        } else {
            //Float case
            floatAlloc = new float[arrSize];
            for (int i = 0; i < arrSize; i++) {
                floatAlloc[i] = (float)baseAlloc[i];
            }
            rsInput.copyFromUnchecked(floatAlloc);
        }

        int outW = (int) (w*scaleX);
        int outH = (int) (h*scaleY);

        Allocation rsOutput = makeAllocation(outW, outH, e);
        Allocation rsCppOutput = makeAllocation(outW, outH, e);

        ScriptIntrinsicResize resize = ScriptIntrinsicResize.create(mRS);
        resize.setInput(rsInput);
        resize.forEach_bicubic(rsOutput);

        int outArrSize = outW * outH * (vecSize == 3 ? 4 : vecSize);
        byte[] nativeByteAlloc = new byte[outArrSize];
        float[] nativeFloatAlloc = new float[outArrSize];
        resizeTest(this.getContext().getCacheDir().toString().toString(), w, h, scaleX, scaleY,
                   useByte, vecSize, byteAlloc, nativeByteAlloc, floatAlloc, nativeFloatAlloc);

        if (useByte) {
            rsCppOutput.copyFromUnchecked(nativeByteAlloc);
        } else {
            rsCppOutput.copyFromUnchecked(nativeFloatAlloc);
        }
        mVerify.set_image_tolerance(0.04f); // Kept loose till a better test designed
        mVerify.invoke_verify(rsOutput, rsCppOutput, rsInput);
        checkForErrors();
    }

    public void test_U8_4_SCALE10_10_inSqure() {
        testResize(inX, inX, Element.DataType.UNSIGNED_8, 4, 1.f, 1.f);
    }
    public void test_U8_3_SCALE10_10_inSqure() {
        testResize(inX, inX, Element.DataType.UNSIGNED_8, 3, 1.f, 1.f);
    }
    public void test_U8_2_SCALE10_10_inSqure() {
        testResize(inX, inX, Element.DataType.UNSIGNED_8, 2, 1.f, 1.f);
    }
    public void test_U8_1_SCALE10_10_inSqure() {
        testResize(inX, inX, Element.DataType.UNSIGNED_8, 1, 1.f, 1.f);
    }

    public void test_U8_4_SCALE20_20_inSqure() {
        testResize(inX, inX, Element.DataType.UNSIGNED_8, 4, 2.f, 2.f);
    }
    public void test_U8_3_SCALE20_20_inSqure() {
        testResize(inX, inX, Element.DataType.UNSIGNED_8, 3, 2.f, 2.f);
    }
    public void test_U8_2_SCALE20_20_inSqure() {
        testResize(inX, inX, Element.DataType.UNSIGNED_8, 2, 2.f, 2.f);
    }
    public void test_U8_1_SCALE20_20_inSqure() {
        testResize(inX, inX, Element.DataType.UNSIGNED_8, 1, 2.f, 2.f);
    }

    public void test_U8_4_SCALE05_20_inSqure() {
        testResize(inX, inX, Element.DataType.UNSIGNED_8, 4, 0.5f, 2.f);
    }
    public void test_U8_3_SCALE05_20_inSqure() {
        testResize(inX, inX, Element.DataType.UNSIGNED_8, 3, 0.5f, 2.f);
    }
    public void test_U8_2_SCALE05_20_inSqure() {
        testResize(inX, inX, Element.DataType.UNSIGNED_8, 2, 0.5f, 2.f);
    }
    public void test_U8_1_SCALE05_20_inSqure() {
        testResize(inX, inX, Element.DataType.UNSIGNED_8, 1, 0.5f, 2.f);
    }

    public void test_U8_4_SCALE20_05_inSqure() {
        testResize(inX, inX, Element.DataType.UNSIGNED_8, 4, 2.f, 0.5f);
    }
    public void test_U8_3_SCALE20_05_inSqure() {
        testResize(inX, inX, Element.DataType.UNSIGNED_8, 3, 2.f, 0.5f);
    }
    public void test_U8_2_SCALE20_05_inSqure() {
        testResize(inX, inX, Element.DataType.UNSIGNED_8, 2, 2.f, 0.5f);
    }
    public void test_U8_1_SCALE20_05_inSqure() {
        testResize(inX, inX, Element.DataType.UNSIGNED_8, 1, 2.f, 0.5f);
    }

    public void test_U8_4_SCALE05_05_inSqure() {
        testResize(inX, inX, Element.DataType.UNSIGNED_8, 4, 0.5f, 0.5f);
    }
    public void test_U8_3_SCALE05_05_inSqure() {
        testResize(inX, inX, Element.DataType.UNSIGNED_8, 3, 0.5f, 0.5f);
    }
    public void test_U8_2_SCALE05_05_inSqure() {
        testResize(inX, inX, Element.DataType.UNSIGNED_8, 2, 0.5f, 0.5f);
    }
    public void test_U8_1_SCALE05_05_inSqure() {
        testResize(inX, inX, Element.DataType.UNSIGNED_8, 1, 0.5f, 0.5f);
    }

    public void test_U8_4_SCALE10_10_inRectangle() {
        testResize(inX, inY, Element.DataType.UNSIGNED_8, 4, 1.f, 1.f);
    }
    public void test_U8_3_SCALE10_10_inRectangle() {
        testResize(inX, inY, Element.DataType.UNSIGNED_8, 3, 1.f, 1.f);
    }
    public void test_U8_2_SCALE10_10_inRectangle() {
        testResize(inX, inY, Element.DataType.UNSIGNED_8, 2, 1.f, 1.f);
    }
    public void test_U8_1_SCALE10_10_inRectangle() {
        testResize(inX, inY, Element.DataType.UNSIGNED_8, 1, 1.f, 1.f);
    }

    public void test_U8_4_SCALE20_20_inRectangle() {
        testResize(inX, inY, Element.DataType.UNSIGNED_8, 4, 2.f, 2.f);
    }
    public void test_U8_3_SCALE20_20_inRectangle() {
        testResize(inX, inY, Element.DataType.UNSIGNED_8, 3, 2.f, 2.f);
    }
    public void test_U8_2_SCALE20_20_inRectangle() {
        testResize(inX, inY, Element.DataType.UNSIGNED_8, 2, 2.f, 2.f);
    }
    public void test_U8_1_SCALE20_20_inRectangle() {
        testResize(inX, inY, Element.DataType.UNSIGNED_8, 1, 2.f, 2.f);
    }

    public void test_U8_4_SCALE05_20_inRectangle() {
        testResize(inX, inY, Element.DataType.UNSIGNED_8, 4, 0.5f, 2.f);
    }
    public void test_U8_3_SCALE05_20_inRectangle() {
        testResize(inX, inY, Element.DataType.UNSIGNED_8, 3, 0.5f, 2.f);
    }
    public void test_U8_2_SCALE05_20_inRectangle() {
        testResize(inX, inY, Element.DataType.UNSIGNED_8, 2, 0.5f, 2.f);
    }
    public void test_U8_1_SCALE05_20_inRectangle() {
        testResize(inX, inY, Element.DataType.UNSIGNED_8, 1, 0.5f, 2.f);
    }

    public void test_U8_4_SCALE20_05_inRectangle() {
        testResize(inX, inY, Element.DataType.UNSIGNED_8, 4, 2.f, 0.5f);
    }
    public void test_U8_3_SCALE20_05_inRectangle() {
        testResize(inX, inY, Element.DataType.UNSIGNED_8, 3, 2.f, 0.5f);
    }
    public void test_U8_2_SCALE20_05_inRectangle() {
        testResize(inX, inY, Element.DataType.UNSIGNED_8, 2, 2.f, 0.5f);
    }
    public void test_U8_1_SCALE20_05_inRectangle() {
        testResize(inX, inY, Element.DataType.UNSIGNED_8, 1, 2.f, 0.5f);
    }

    public void test_U8_4_SCALE05_05_inRectangle() {
        testResize(inX, inY, Element.DataType.UNSIGNED_8, 4, 0.5f, 0.5f);
    }
    public void test_U8_3_SCALE05_05_inRectangle() {
        testResize(inX, inY, Element.DataType.UNSIGNED_8, 3, 0.5f, 0.5f);
    }
    public void test_U8_2_SCALE05_05_inRectangle() {
        testResize(inX, inY, Element.DataType.UNSIGNED_8, 2, 0.5f, 0.5f);
    }
    public void test_U8_1_SCALE05_05_inRectangle() {
        testResize(inX, inY, Element.DataType.UNSIGNED_8, 1, 0.5f, 0.5f);
    }


    public void test_F32_4_SCALE10_10_inSqure() {
        testResize(inX, inX, Element.DataType.FLOAT_32, 4, 1.f, 1.f);
    }
    public void test_F32_3_SCALE10_10_inSqure() {
        testResize(inX, inX, Element.DataType.FLOAT_32, 3, 1.f, 1.f);
    }
    public void test_F32_2_SCALE10_10_inSqure() {
        testResize(inX, inX, Element.DataType.FLOAT_32, 2, 1.f, 1.f);
    }
    public void test_F32_1_SCALE10_10_inSqure() {
        testResize(inX, inX, Element.DataType.FLOAT_32, 1, 1.f, 1.f);
    }

    public void test_F32_4_SCALE20_20_inSqure() {
        testResize(inX, inX, Element.DataType.FLOAT_32, 4, 2.f, 2.f);
    }
    public void test_F32_3_SCALE20_20_inSqure() {
        testResize(inX, inX, Element.DataType.FLOAT_32, 3, 2.f, 2.f);
    }
    public void test_F32_2_SCALE20_20_inSqure() {
        testResize(inX, inX, Element.DataType.FLOAT_32, 2, 2.f, 2.f);
    }
    public void test_F32_1_SCALE20_20_inSqure() {
        testResize(inX, inX, Element.DataType.FLOAT_32, 1, 2.f, 2.f);
    }

    public void test_F32_4_SCALE05_20_inSqure() {
        testResize(inX, inX, Element.DataType.FLOAT_32, 4, 0.5f, 2.f);
    }
    public void test_F32_3_SCALE05_20_inSqure() {
        testResize(inX, inX, Element.DataType.FLOAT_32, 3, 0.5f, 2.f);
    }
    public void test_F32_2_SCALE05_20_inSqure() {
        testResize(inX, inX, Element.DataType.FLOAT_32, 2, 0.5f, 2.f);
    }
    public void test_F32_1_SCALE05_20_inSqure() {
        testResize(inX, inX, Element.DataType.FLOAT_32, 1, 0.5f, 2.f);
    }

    public void test_F32_4_SCALE20_05_inSqure() {
        testResize(inX, inX, Element.DataType.FLOAT_32, 4, 2.f, 0.5f);
    }
    public void test_F32_3_SCALE20_05_inSqure() {
        testResize(inX, inX, Element.DataType.FLOAT_32, 3, 2.f, 0.5f);
    }
    public void test_F32_2_SCALE20_05_inSqure() {
        testResize(inX, inX, Element.DataType.FLOAT_32, 2, 2.f, 0.5f);
    }
    public void test_F32_1_SCALE20_05_inSqure() {
        testResize(inX, inX, Element.DataType.FLOAT_32, 1, 2.f, 0.5f);
    }

    public void test_F32_4_SCALE05_05_inSqure() {
        testResize(inX, inX, Element.DataType.FLOAT_32, 4, 0.5f, 0.5f);
    }
    public void test_F32_3_SCALE05_05_inSqure() {
        testResize(inX, inX, Element.DataType.FLOAT_32, 3, 0.5f, 0.5f);
    }
    public void test_F32_2_SCALE05_05_inSqure() {
        testResize(inX, inX, Element.DataType.FLOAT_32, 2, 0.5f, 0.5f);
    }
    public void test_F32_1_SCALE05_05_inSqure() {
        testResize(inX, inX, Element.DataType.FLOAT_32, 1, 0.5f, 0.5f);
    }

    public void test_F32_4_SCALE10_10_inRectangle() {
        testResize(inX, inY, Element.DataType.FLOAT_32, 4, 1.f, 1.f);
    }
    public void test_F32_3_SCALE10_10_inRectangle() {
        testResize(inX, inY, Element.DataType.FLOAT_32, 3, 1.f, 1.f);
    }
    public void test_F32_2_SCALE10_10_inRectangle() {
        testResize(inX, inY, Element.DataType.FLOAT_32, 2, 1.f, 1.f);
    }
    public void test_F32_1_SCALE10_10_inRectangle() {
        testResize(inX, inY, Element.DataType.FLOAT_32, 1, 1.f, 1.f);
    }

    public void test_F32_4_SCALE20_20_inRectangle() {
        testResize(inX, inY, Element.DataType.FLOAT_32, 4, 2.f, 2.f);
    }
    public void test_F32_3_SCALE20_20_inRectangle() {
        testResize(inX, inY, Element.DataType.FLOAT_32, 3, 2.f, 2.f);
    }
    public void test_F32_2_SCALE20_20_inRectangle() {
        testResize(inX, inY, Element.DataType.FLOAT_32, 2, 2.f, 2.f);
    }
    public void test_F32_1_SCALE20_20_inRectangle() {
        testResize(inX, inY, Element.DataType.FLOAT_32, 1, 2.f, 2.f);
    }

    public void test_F32_4_SCALE05_20_inRectangle() {
        testResize(inX, inY, Element.DataType.FLOAT_32, 4, 0.5f, 2.f);
    }
    public void test_F32_3_SCALE05_20_inRectangle() {
        testResize(inX, inY, Element.DataType.FLOAT_32, 3, 0.5f, 2.f);
    }
    public void test_F32_2_SCALE05_20_inRectangle() {
        testResize(inX, inY, Element.DataType.FLOAT_32, 2, 0.5f, 2.f);
    }
    public void test_F32_1_SCALE05_20_inRectangle() {
        testResize(inX, inY, Element.DataType.FLOAT_32, 1, 0.5f, 2.f);
    }

    public void test_F32_4_SCALE20_05_inRectangle() {
        testResize(inX, inY, Element.DataType.FLOAT_32, 4, 2.f, 0.5f);
    }
    public void test_F32_3_SCALE20_05_inRectangle() {
        testResize(inX, inY, Element.DataType.FLOAT_32, 3, 2.f, 0.5f);
    }
    public void test_F32_2_SCALE20_05_inRectangle() {
        testResize(inX, inY, Element.DataType.FLOAT_32, 2, 2.f, 0.5f);
    }
    public void test_F32_1_SCALE20_05_inRectangle() {
        testResize(inX, inY, Element.DataType.FLOAT_32, 1, 2.f, 0.5f);
    }

    public void test_F32_4_SCALE05_05_inRectangle() {
        testResize(inX, inY, Element.DataType.FLOAT_32, 4, 0.5f, 0.5f);
    }
    public void test_F32_3_SCALE05_05_inRectangle() {
        testResize(inX, inY, Element.DataType.FLOAT_32, 3, 0.5f, 0.5f);
    }
    public void test_F32_2_SCALE05_05_inRectangle() {
        testResize(inX, inY, Element.DataType.FLOAT_32, 2, 0.5f, 0.5f);
    }
    public void test_F32_1_SCALE05_05_inRectangle() {
        testResize(inX, inY, Element.DataType.FLOAT_32, 1, 0.5f, 0.5f);
    }
}
