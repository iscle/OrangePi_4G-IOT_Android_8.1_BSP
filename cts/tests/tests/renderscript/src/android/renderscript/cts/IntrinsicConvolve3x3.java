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

public class IntrinsicConvolve3x3 extends IntrinsicBase {
    private void testConvolve3(int w, int h, Element.DataType dt, int vecSize, Script.LaunchOptions sc) {
        float cf1[] = {0.f, 0.f, 0.f,  0.f, 1.f, 0.f,  0.f, 0.f, 0.f};
        float cf2[] = {0.f, -1.f, 0.f,  -1.f, 5.f, -1.f,  0.f, -1.f, 0.f};

        float irCoeff1 = 3.1415927f;
        float irCoeff2 = -irCoeff1;
        float cf3[] = {0.f, irCoeff1, 0.f,  irCoeff2, 1.f, irCoeff2,  0.f, irCoeff1, 0.f};

        Element e = makeElement(dt, vecSize);

        System.gc();
        makeBuffers(w, h, e);

        mVerify.set_gAllowedIntError(1);

        ScriptIntrinsicConvolve3x3 si = ScriptIntrinsicConvolve3x3.create(mRS, e);
        si.setCoefficients(cf1);
        si.setInput(mAllocSrc);
        si.forEach(mAllocRef, sc);

        ScriptC_intrinsic_convolve3x3 sr = new ScriptC_intrinsic_convolve3x3(mRS);
        sr.set_gCoeffs(cf1);
        sr.set_gIn(mAllocSrc);
        sr.set_gWidth(w);
        sr.set_gHeight(h);

        if (sc != null) {
            mAllocRef.copyFrom(mAllocSrc);
            mAllocDst.copyFrom(mAllocSrc);
        }

        if (dt == Element.DataType.UNSIGNED_8) {
            switch(vecSize) {
            case 4:
                sr.forEach_convolve_U4(mAllocDst, sc);
                break;
            case 3:
                sr.forEach_convolve_U3(mAllocDst, sc);
                break;
            case 2:
                sr.forEach_convolve_U2(mAllocDst, sc);
                break;
            case 1:
                sr.forEach_convolve_U1(mAllocDst, sc);
                break;
            }
        } else {
            switch(vecSize) {
            case 4:
                sr.forEach_convolve_F4(mAllocDst, sc);
                break;
            case 3:
                sr.forEach_convolve_F3(mAllocDst, sc);
                break;
            case 2:
                sr.forEach_convolve_F2(mAllocDst, sc);
                break;
            case 1:
                sr.forEach_convolve_F1(mAllocDst, sc);
                break;
            }
        }

        //android.util.Log.e("RSI test", "test convolve U8_" + vecSize + " 1 " + w + ", " + h);
        mVerify.invoke_verify(mAllocRef, mAllocDst, mAllocSrc);

        si.setCoefficients(cf2);
        sr.set_gCoeffs(cf2);
        si.forEach(mAllocRef, sc);
        if (dt == Element.DataType.UNSIGNED_8) {
            switch(vecSize) {
            case 4:
                sr.forEach_convolve_U4(mAllocDst, sc);
                break;
            case 3:
                sr.forEach_convolve_U3(mAllocDst, sc);
                break;
            case 2:
                sr.forEach_convolve_U2(mAllocDst, sc);
                break;
            case 1:
                sr.forEach_convolve_U1(mAllocDst, sc);
                break;
            }
        } else {
            switch(vecSize) {
            case 4:
                sr.forEach_convolve_F4(mAllocDst, sc);
                break;
            case 3:
                sr.forEach_convolve_F3(mAllocDst, sc);
                break;
            case 2:
                sr.forEach_convolve_F2(mAllocDst, sc);
                break;
            case 1:
                sr.forEach_convolve_F1(mAllocDst, sc);
                break;
            }
        }
        //android.util.Log.e("RSI test", "test convolve U8_" + vecSize + " 2 " + w + ", " + h);
        mVerify.invoke_verify(mAllocRef, mAllocDst, mAllocSrc);

        si.setCoefficients(cf3);
        sr.set_gCoeffs(cf3);
        si.forEach(mAllocRef, sc);
        if (dt == Element.DataType.UNSIGNED_8) {
            switch(vecSize) {
            case 4:
                sr.forEach_convolve_U4(mAllocDst, sc);
                break;
            case 3:
                sr.forEach_convolve_U3(mAllocDst, sc);
                break;
            case 2:
                sr.forEach_convolve_U2(mAllocDst, sc);
                break;
            case 1:
                sr.forEach_convolve_U1(mAllocDst, sc);
                break;
            }
        } else {
            switch(vecSize) {
            case 4:
                sr.forEach_convolve_F4(mAllocDst, sc);
                break;
            case 3:
                sr.forEach_convolve_F3(mAllocDst, sc);
                break;
            case 2:
                sr.forEach_convolve_F2(mAllocDst, sc);
                break;
            case 1:
                sr.forEach_convolve_F1(mAllocDst, sc);
                break;
            }
        }
        //android.util.Log.e("RSI test", "test convolve U8_" + vecSize + " 2 " + w + ", " + h);
        mVerify.invoke_verify(mAllocRef, mAllocDst, mAllocSrc);

        mRS.finish();

        si.destroy();
        sr.destroy();
    }


    public void test_U8_4() {
        testConvolve3(100, 100, Element.DataType.UNSIGNED_8, 4, null);
        checkError();
    }
    public void test_U8_3() {
        testConvolve3(100, 100, Element.DataType.UNSIGNED_8, 3, null);
        checkError();
    }
    public void test_U8_2() {
        testConvolve3(100, 100, Element.DataType.UNSIGNED_8, 2, null);
        checkError();
    }
    public void test_U8_1() {
        testConvolve3(100, 100, Element.DataType.UNSIGNED_8, 1, null);
        checkError();
    }

    public void test_F32_4() {
        testConvolve3(100, 100, Element.DataType.FLOAT_32, 4, null);
        checkError();
    }

    public void test_F32_3() {
        testConvolve3(100, 100, Element.DataType.FLOAT_32, 3, null);
        checkError();
    }

    public void test_F32_2() {
        testConvolve3(100, 100, Element.DataType.FLOAT_32, 2, null);
        checkError();
    }

    public void test_F32_1() {
        testConvolve3(100, 100, Element.DataType.FLOAT_32, 1, null);
        checkError();
    }


    public void test_U8_4C() {
        testConvolve3(100, 100, Element.DataType.UNSIGNED_8, 4,
                      makeClipper(11, 11, 90, 90));
        checkError();
    }
    public void test_U8_3C() {
        testConvolve3(100, 100, Element.DataType.UNSIGNED_8, 3,
                      makeClipper(11, 11, 90, 90));
        checkError();
    }
    public void test_U8_2C() {
        testConvolve3(100, 100, Element.DataType.UNSIGNED_8, 2,
                      makeClipper(11, 11, 90, 90));
        checkError();
    }
    public void test_U8_1C() {
        testConvolve3(100, 100, Element.DataType.UNSIGNED_8, 1,
                      makeClipper(11, 11, 90, 90));
        checkError();
    }

    public void test_F32_4C() {
        testConvolve3(100, 100, Element.DataType.FLOAT_32, 4,
                      makeClipper(11, 11, 90, 90));
        checkError();
    }

    public void test_F32_3C() {
        testConvolve3(100, 100, Element.DataType.FLOAT_32, 3,
                      makeClipper(11, 11, 90, 90));
        checkError();
    }

    public void test_F32_2C() {
        testConvolve3(100, 100, Element.DataType.FLOAT_32, 2,
                      makeClipper(11, 11, 90, 90));
        checkError();
    }

    public void test_F32_1C() {
        testConvolve3(100, 100, Element.DataType.FLOAT_32, 1,
                      makeClipper(11, 11, 90, 90));
        checkError();
    }

    public void test_ID() {
        ScriptIntrinsicConvolve3x3 s = ScriptIntrinsicConvolve3x3.create(mRS, Element.U8_4(mRS));
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
