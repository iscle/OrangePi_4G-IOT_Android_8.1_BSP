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
import java.util.Random;
import android.renderscript.*;

public class AtomicTest extends RSBaseCompute {
    int[] mSrcData;
    int[] mReturnData = new int[1];
    int[] mRefData = new int[1];
    ScriptC_AtomicTest mScript;
    Allocation mSrc;
    Allocation mReturn;

    @Override
    protected void tearDown() throws Exception {
        if (mSrc != null) {
            mSrc.destroy();
        }
        if (mReturn != null) {
            mReturn.destroy();
        }
        if (mScript != null) {
            mScript.destroy();
        }
        super.tearDown();
    }

    private void initS(boolean fillData, int sz) {
        mSrcData = new int[sz * sz];
        mScript = new ScriptC_AtomicTest(mRS);
        mSrc = Allocation.createTyped(mRS, Type.createXY(mRS, Element.I32(mRS), sz, sz));
        mReturn = Allocation.createSized(mRS, Element.I32(mRS), 1);
        if (fillData) {
            RSUtils.genRandomInts(0, mSrcData, true, 32);
            mSrc.copyFrom(mSrcData);
        }
    }

    private void initU(boolean fillData, int sz) {
        mSrcData = new int[sz * sz];
        mScript = new ScriptC_AtomicTest(mRS);
        mSrc = Allocation.createTyped(mRS, Type.createXY(mRS, Element.U32(mRS), sz, sz));
        mReturn = Allocation.createSized(mRS, Element.U32(mRS), 1);
        if (fillData) {
            RSUtils.genRandomInts(0, mSrcData, false, 32);
            mSrc.copyFrom(mSrcData);
        }
    }

    public void testCas() {
        initS(true, 1024);
        mScript.set_gISum(10);
        mScript.forEach_test_Cas(mSrc);
        mScript.invoke_getValueS(mReturn);
        mReturn.copyTo(mReturnData);
        assertEquals("Incorrect value for Cas ", (10 + 1024*1024), mReturnData[0]);
    }

    public void testUCas() {
        initU(true, 1024);
        mScript.set_gUSum(10);
        mScript.forEach_test_uCas(mSrc);
        mScript.invoke_getValueU(mReturn);
        mReturn.copyTo(mReturnData);
        assertEquals("Incorrect value for UCas ", (10 + 1024*1024), mReturnData[0]);
    }

    public void testInc() {
        initS(true, 1024);
        mScript.set_gISum(10);
        mScript.forEach_test_Inc(mSrc);
        mScript.invoke_getValueS(mReturn);
        mReturn.copyTo(mReturnData);
        assertEquals("Incorrect value for Inc ", (10 + 1024*1024), mReturnData[0]);
    }

    public void testUInc() {
        initU(true, 1024);
        mScript.set_gUSum(10);
        mScript.forEach_test_uInc(mSrc);
        mScript.invoke_getValueU(mReturn);
        mReturn.copyTo(mReturnData);
        assertEquals("Incorrect value for UInc ", (10 + 1024*1024), mReturnData[0]);
    }

    public void testDec() {
        initS(true, 1024);
        mScript.set_gISum(10 + 1024*1024);
        mScript.forEach_test_Dec(mSrc);
        mScript.invoke_getValueS(mReturn);
        mReturn.copyTo(mReturnData);
        assertEquals("Incorrect value for Dec ", 10, mReturnData[0]);
    }

    public void testUDec() {
        initU(true, 1024);
        mScript.set_gUSum(10 + 1024*1024);
        mScript.forEach_test_uDec(mSrc);
        mScript.invoke_getValueU(mReturn);
        mReturn.copyTo(mReturnData);
        assertEquals("Incorrect value for UDec ", 10, mReturnData[0]);
    }


    public void testAdd() {
        initS(true, 1024);
        mScript.set_gISum(10);
        mScript.forEach_test_Add(mSrc);
        mScript.invoke_getValueS(mReturn);
        mReturn.copyTo(mReturnData);

        int sExpected = 10;
        for (int i=0; i < mSrcData.length; i++) {
            sExpected += mSrcData[i];
        }
        assertEquals("Incorrect value for Add ", sExpected, mReturnData[0]);
    }

    public void testUAdd() {
        initU(true, 1024);
        mScript.set_gUSum(10);
        mScript.forEach_test_uAdd(mSrc);
        mScript.invoke_getValueU(mReturn);
        mReturn.copyTo(mReturnData);

        int sExpected = 10;
        for (int i=0; i < mSrcData.length; i++) {
            sExpected += mSrcData[i];
        }
        assertEquals("Incorrect value for UAdd ", sExpected, mReturnData[0]);
    }

    public void testSub() {
        initS(true, 1024);
        mScript.set_gISum(10);
        mScript.forEach_test_Sub(mSrc);
        mScript.invoke_getValueS(mReturn);
        mReturn.copyTo(mReturnData);

        int sExpected = 10;
        for (int i=0; i < mSrcData.length; i++) {
            sExpected -= mSrcData[i];
        }
        assertEquals("Incorrect value for Sub ", sExpected, mReturnData[0]);
    }

    public void testUSub() {
        initU(true, 1024);
        mScript.set_gUSum(10);
        mScript.forEach_test_uSub(mSrc);
        mScript.invoke_getValueU(mReturn);
        mReturn.copyTo(mReturnData);

        int sExpected = 10;
        for (int i=0; i < mSrcData.length; i++) {
            sExpected -= mSrcData[i];
        }
        assertEquals("Incorrect value for USub ", sExpected, mReturnData[0]);
    }

    public void testXor() {
        initS(true, 1024);
        mScript.set_gISum(10);
        mScript.forEach_test_Xor(mSrc);
        mScript.invoke_getValueS(mReturn);
        mReturn.copyTo(mReturnData);

        int sExpected = 10;
        for (int i=0; i < mSrcData.length; i++) {
            sExpected ^= mSrcData[i];
        }
        assertEquals("Incorrect value for Xor ", sExpected, mReturnData[0]);
    }

    public void testUXor() {
        initU(true, 1024);
        mScript.set_gUSum(10);
        mScript.forEach_test_uXor(mSrc);
        mScript.invoke_getValueU(mReturn);
        mReturn.copyTo(mReturnData);

        int sExpected = 10;
        for (int i=0; i < mSrcData.length; i++) {
            sExpected ^= mSrcData[i];
        }
        assertEquals("Incorrect value for UXor ", sExpected, mReturnData[0]);
    }

    public void testMin() {
        for (int i = 0; i < 16; i++) {
            initS(true, 256);

            mScript.set_gISum(0x7fffffff);
            mScript.forEach_test_Min(mSrc);
            mScript.invoke_getValueS(mReturn);
            mReturn.copyTo(mReturnData);

            mScript.set_gISum(0x7fffffff);
            mScript.invoke_computeReference_Min(mSrc, mReturn);
            mReturn.copyTo(mRefData);

            assertEquals("Incorrect value for Min ", mRefData[0], mReturnData[0]);
        }
    }

    public void testUMin() {
        for (int i = 0; i < 16; i++) {
            initU(true, 256);

            mScript.set_gUSum(0xffffffffL);
            mScript.forEach_test_uMin(mSrc);
            mScript.invoke_getValueU(mReturn);
            mReturn.copyTo(mReturnData);

            mScript.set_gUSum(0xffffffffL);
            mScript.invoke_computeReference_uMin(mSrc, mReturn);
            mReturn.copyTo(mRefData);

            assertEquals("Incorrect value for UMin ", mRefData[0], mReturnData[0]);
        }
    }

    public void testMax() {
        for (int i = 0; i < 16; i++) {
            initS(true, 256);

            mScript.set_gISum(0);
            mScript.forEach_test_Max(mSrc);
            mScript.invoke_getValueS(mReturn);
            mReturn.copyTo(mReturnData);

            mScript.set_gISum(0);
            mScript.invoke_computeReference_Max(mSrc, mReturn);
            mReturn.copyTo(mRefData);

            assertEquals("Incorrect value for Min ", mRefData[0], mReturnData[0]);
        }
    }

    public void testUMax() {
        for (int i = 0; i < 16; i++) {
            initU(true, 256);

            mScript.set_gISum(0);
            mScript.forEach_test_uMax(mSrc);
            mScript.invoke_getValueU(mReturn);
            mReturn.copyTo(mReturnData);

            mScript.set_gISum(0);
            mScript.invoke_computeReference_uMax(mSrc, mReturn);
            mReturn.copyTo(mRefData);

            assertEquals("Incorrect value for UMax ", mRefData[0], mReturnData[0]);
        }
    }

    public void testAnd() {
        Random r = new Random(78);

        for (int i = 0; i < 64; i++) {
            initS(false, 128);

            for (int j = 0; j < mSrcData.length; j++) {
                mSrcData[j] = ~0;
            }
            mSrcData[r.nextInt(mSrcData.length)] = ~0x40000000;
            mSrcData[r.nextInt(mSrcData.length)] = ~0x10000000;
            mSrcData[r.nextInt(mSrcData.length)] = ~0x02000000;
            mSrcData[r.nextInt(mSrcData.length)] = ~0x00c00000;
            mSrcData[r.nextInt(mSrcData.length)] = ~0x00010000;
            mSrcData[r.nextInt(mSrcData.length)] = ~0x00080000;
            mSrcData[r.nextInt(mSrcData.length)] = ~0x00001000;
            mSrcData[r.nextInt(mSrcData.length)] = ~0x00000200;
            mSrcData[r.nextInt(mSrcData.length)] = ~0x0000000f;
            mSrc.copyFrom(mSrcData);

            mScript.set_gISum(0xffffffff);
            mScript.forEach_test_And(mSrc);
            mScript.invoke_getValueS(mReturn);
            mReturn.copyTo(mReturnData);

            int sExpected = 0xffffffff;
            for (int j = 0; j < mSrcData.length; j++) {
                sExpected &= mSrcData[j];
            }
            assertEquals("Incorrect value for And ", sExpected, mReturnData[0]);
        }
    }

    public void testUAnd() {
        Random r = new Random(78);

        for (int i = 0; i < 64; i++) {
            initU(false, 128);

            for (int j = 0; j < mSrcData.length; j++) {
                mSrcData[j] = ~0;
            }
            mSrcData[r.nextInt(mSrcData.length)] = ~0x40000000;
            mSrcData[r.nextInt(mSrcData.length)] = ~0x10000000;
            mSrcData[r.nextInt(mSrcData.length)] = ~0x02000000;
            mSrcData[r.nextInt(mSrcData.length)] = ~0x00c00000;
            mSrcData[r.nextInt(mSrcData.length)] = ~0x00010000;
            mSrcData[r.nextInt(mSrcData.length)] = ~0x00080000;
            mSrcData[r.nextInt(mSrcData.length)] = ~0x00001000;
            mSrcData[r.nextInt(mSrcData.length)] = ~0x00000200;
            mSrcData[r.nextInt(mSrcData.length)] = ~0x0000000f;
            mSrc.copyFrom(mSrcData);

            mScript.set_gUSum(0xffffffffL);
            mScript.forEach_test_uAnd(mSrc);
            mScript.invoke_getValueU(mReturn);
            mReturn.copyTo(mReturnData);

            int sExpected = 0xffffffff;
            for (int j = 0; j < mSrcData.length; j++) {
                sExpected &= mSrcData[j];
            }
            assertEquals("Incorrect value for uAnd ", sExpected, mReturnData[0]);
        }
    }

    public void testOr() {
        Random r = new Random(78);

        for (int i = 0; i < 64; i++) {
            initS(false, 128);

            for (int j = 0; j < mSrcData.length; j++) {
                mSrcData[j] = 0;
            }
            mSrcData[r.nextInt(mSrcData.length)] = 0x40000000;
            mSrcData[r.nextInt(mSrcData.length)] = 0x10000000;
            mSrcData[r.nextInt(mSrcData.length)] = 0x02000000;
            mSrcData[r.nextInt(mSrcData.length)] = 0x00c00000;
            mSrcData[r.nextInt(mSrcData.length)] = 0x00010000;
            mSrcData[r.nextInt(mSrcData.length)] = 0x00080000;
            mSrcData[r.nextInt(mSrcData.length)] = 0x00001000;
            mSrcData[r.nextInt(mSrcData.length)] = 0x00000200;
            mSrcData[r.nextInt(mSrcData.length)] = 0x0000000f;
            mSrc.copyFrom(mSrcData);

            mScript.set_gISum(0);
            mScript.forEach_test_Or(mSrc);
            mScript.invoke_getValueS(mReturn);
            mReturn.copyTo(mReturnData);

            int sExpected = 0;
            for (int j = 0; j < mSrcData.length; j++) {
                sExpected |= mSrcData[j];
            }
            assertEquals("Incorrect value for Or ", sExpected, mReturnData[0]);
        }
    }

    public void testUOr() {
        Random r = new Random(78);

        for (int i = 0; i < 64; i++) {
            initU(false, 128);

            for (int j = 0; j < mSrcData.length; j++) {
                mSrcData[j] = 0;
            }
            mSrcData[r.nextInt(mSrcData.length)] = 0x40000000;
            mSrcData[r.nextInt(mSrcData.length)] = 0x10000000;
            mSrcData[r.nextInt(mSrcData.length)] = 0x02000000;
            mSrcData[r.nextInt(mSrcData.length)] = 0x00c00000;
            mSrcData[r.nextInt(mSrcData.length)] = 0x00010000;
            mSrcData[r.nextInt(mSrcData.length)] = 0x00080000;
            mSrcData[r.nextInt(mSrcData.length)] = 0x00001000;
            mSrcData[r.nextInt(mSrcData.length)] = 0x00000200;
            mSrcData[r.nextInt(mSrcData.length)] = 0x0000000f;
            mSrc.copyFrom(mSrcData);

            mScript.set_gUSum(0);
            mScript.forEach_test_uOr(mSrc);
            mScript.invoke_getValueU(mReturn);
            mReturn.copyTo(mReturnData);

            int sExpected = 0;
            for (int j = 0; j < mSrcData.length; j++) {
                sExpected |= mSrcData[j];
            }
            assertEquals("Incorrect value for UOr ", sExpected, mReturnData[0]);
        }
    }

}

