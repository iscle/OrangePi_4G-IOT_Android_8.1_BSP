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

import android.renderscript.Allocation;
import android.renderscript.Element;
import android.renderscript.RenderScript;
import android.renderscript.RenderScript.RSErrorHandler;
import android.renderscript.RSRuntimeException;
import android.renderscript.Type;
import android.util.Log;
import java.util.Random;
import java.util.function.*;

public class DebugContext extends RSBaseCompute {
    Allocation AUnused;
    Allocation AInt;
    ScriptC_oob Soob;

    boolean mRanErrorHandler = false;
    RSErrorHandler mRsError = new RSErrorHandler() {
        public void run() {
            mRanErrorHandler = true;
            Log.e("RenderscriptCTS", mErrorMessage);
        }
    };

    protected void setupDebugContext() {
        mRS.destroy();
        mRS = RenderScript.create(mCtx, RenderScript.ContextType.DEBUG);
        mRS.setMessageHandler(mRsMessage);
        mRS.setErrorHandler(mRsError);

        Soob = new ScriptC_oob(mRS);

        Type.Builder typeBuilder = new Type.Builder(mRS, Element.I32(mRS));
        Type t = typeBuilder.setX(1).create();

        AInt = Allocation.createTyped(mRS, t);
        Soob.set_aInt(AInt);

        AUnused = Allocation.createTyped(mRS, t);
    }

    /**
     * Test whether we are detect out-of-bounds allocation accesses
     * from an invokable.
     */
    public void testDebugContextI() {
        setupDebugContext();
        Soob.invoke_write_i(7, 1);  // Write to invalid location.

        // Flush messages through the pipeline.
        mRS.sendMessage(RS_MSG_TEST_FLUSH, null);
        waitForMessage();

        Soob.destroy();
        assertTrue(mRanErrorHandler);

        // The context is dead at this point so make sure it's not reused
        RenderScript.releaseAllContexts();
    }

    /**
     * Test whether we are detect out-of-bounds allocation accesses
     * from a kernel.
     */
    public void testDebugContextK() {
        setupDebugContext();
        Soob.forEach_write_k(AUnused);  // Write to invalid location.

        // Flush messages through the pipeline.
        mRS.sendMessage(RS_MSG_TEST_FLUSH, null);
        waitForMessage();

        Soob.destroy();
        assertTrue(mRanErrorHandler);

        // The context is dead at this point so make sure it's not reused
        RenderScript.releaseAllContexts();
    }

    /**
     * Helper for rsAllocationCopy tests
     */

    protected class SetupAllocationCopyTests {
        int Width;
        int Height;
        int ArrLen;
        Allocation aIn;
        Allocation aOut;
        Random RN;

        SetupAllocationCopyTests(int dimension,
            int size, Function<RenderScript, Type.Builder> TB, long seed) {
            // Type.Builder constructor needs a RenderScript instance,
            // and that's created by setupDebugContext(). Hence a
            // Function<RenderScript, Type.Builder> is needed here

            assertTrue(dimension == 1 || dimension == 2);

            setupDebugContext();
            RN = new Random(seed);
            // So that we can have offsets that will be invalid in another
            // dimension
            Width = RN.nextInt(size/2)+size/2;
            Height = RN.nextInt(size/2);
            if (dimension == 1) {
                ArrLen = Width;
            } else {
                ArrLen = Width * Height;
            }

            Type.Builder typeBuilder = TB.apply(mRS);

            if (dimension == 1)
                typeBuilder.setX(Width);
            else
                typeBuilder.setX(Width).setY(Height);

            aIn = Allocation.createTyped(mRS, typeBuilder.create());
            aOut = Allocation.createTyped(mRS, typeBuilder.create());

            // Initialize test Allocations
            if (aIn.getElement().getDataType() == Element.DataType.SIGNED_8) {
                byte[] inArray = new byte[ArrLen];
                RN.nextBytes(inArray);
                byte[] outArray = new byte[ArrLen];
                aIn.copyFrom(inArray);
                aOut.copyFrom(outArray);
            } else {
                assertTrue(aIn.getElement().getDataType() ==
                    Element.DataType.SIGNED_16);
                short[] inArray = new short[ArrLen];
                for (int i = 0; i < ArrLen; i++)
                   inArray[i] = (short)RN.nextInt();
                short[] outArray = new short[ArrLen];
                aIn.copyFrom(inArray);
                aOut.copyFrom(outArray);
            }

            // Setup script environment
            if (dimension == 1) {
                Soob.set_aIn1D(aIn);
                Soob.set_aOut1D(aOut);
            } else {
                Soob.set_aIn2D(aIn);
                Soob.set_aOut2D(aOut);
            }
        }

        protected void finishAllocationCopyTests() {
            mRS.finish();

            // Flush messages through the pipeline.
            mRS.sendMessage(RS_MSG_TEST_FLUSH, null);
            waitForMessage();
            Soob.destroy();
        }
    }

    /**
     * 1D copy - things should work under DebugContext if given
     * legitimate arguments
     */
    public void testDebugContextRsAllocationCopy1D_Byte_Normal() {
        SetupAllocationCopyTests AC =
            new SetupAllocationCopyTests(1, 512,
                (RenderScript rs)-> new Type.Builder(rs, Element.I8(rs)),
                0x172d8ab9);
        int Offset = AC.RN.nextInt(AC.Width);
        int Count = AC.RN.nextInt(AC.Width - Offset);
        Soob.set_dstXOff(Offset);
        Soob.set_srcXOff(Offset);
        Soob.set_xCount(Count);
        Soob.set_srcMip(0);
        Soob.set_dstMip(0);
        Soob.invoke_test1D();

        AC.finishAllocationCopyTests();

        // Validate results
        boolean result = true;
        byte[] inArray = new byte[AC.ArrLen];
        byte[] outArray = new byte[AC.ArrLen];
        AC.aIn.copyTo(inArray);
        AC.aOut.copyTo(outArray);
        for (int i = 0; i < AC.Width; i++) {
            if (Offset <= i && i < Offset + Count) {
                if (inArray[i] != outArray[i]) {
                    result = false;
                    break;
                }
            } else {
                if (outArray[i] != 0) {
                    result = false;
                    break;
                }
            }
        }
        assertTrue(result);

        RenderScript.releaseAllContexts();
    }

    /**
     * 2D copy - with legitimate arguments
     */
    public void testDebugContextRsAllocationCopy2D_Short_Normal() {
        SetupAllocationCopyTests AC =
            new SetupAllocationCopyTests(2, 128,
                (RenderScript rs)-> new Type.Builder(rs, Element.I16(rs)),
                0x172d8aba);
        // to make sure xOff is not a valid yOff.
        int xOff = AC.RN.nextInt(AC.Width-AC.Height) + AC.Height;
        int yOff = AC.RN.nextInt(AC.Height);
        int xCount = AC.RN.nextInt(AC.Width - xOff);
        int yCount = AC.RN.nextInt(AC.Height - yOff);

        Soob.set_dstXOff(xOff);
        Soob.set_srcXOff(xOff);
        Soob.set_yOff(yOff);
        Soob.set_xCount(xCount);
        Soob.set_yCount(yCount);
        Soob.set_srcMip(0);
        Soob.set_dstMip(0);
        Soob.invoke_test2D();

        AC.finishAllocationCopyTests();

        // Validate results
        boolean result = true;
        short[] inArray = new short[AC.ArrLen];
        short[] outArray = new short[AC.ArrLen];
        AC.aIn.copyTo(inArray);
        AC.aOut.copyTo(outArray);
        for (int i = 0; i < AC.Height; i++) {
            for (int j = 0; j < AC.Width; j++) {
                int pos = i * AC.Width + j;
                if (yOff <= i && i < yOff + yCount &&
                    xOff <= j && j < xOff + xCount) {
                    if (inArray[pos] != outArray[pos]) {
                        result = false;
                        break;
                    }
                } else {
                    if (outArray[pos] != 0) {
                       result = false;
                       break;
                    }
                }
            }
        }
        assertTrue(result);

        // The context is dead at this point so make sure it's not reused
        RenderScript.releaseAllContexts();
    }

    /**
     * Test invalid arguments to rsAllocationCopy1D - bad source LOD
     */
    public void testDebugContextRsAllocationCopy1D_Byte_BadSrcLOD() {
        SetupAllocationCopyTests AC =
            new SetupAllocationCopyTests(1, 512,
                (RenderScript rs)-> new Type.Builder(rs, Element.I8(rs)),
                0x172d8abb);
        int Offset = AC.RN.nextInt(AC.Width);
        int Count = AC.RN.nextInt(AC.Width - Offset);
        Soob.set_dstXOff(Offset);
        Soob.set_srcXOff(Offset);
        Soob.set_xCount(Count);
        Soob.set_srcMip(1);  // bad source LOD
        Soob.set_dstMip(0);
        Soob.invoke_test1D();

        AC.finishAllocationCopyTests();
        assertTrue(mRanErrorHandler);

        // The context is dead at this point so make sure it's not reused
        RenderScript.releaseAllContexts();
    }

    /**
     * Test invalid arguments to rsAllocationCopy1D - bad destination LOD
     */
    public void testDebugContextRsAllocationCopy1D_Byte_BadDstLOD() {
        SetupAllocationCopyTests AC =
            new SetupAllocationCopyTests(1, 512,
                (RenderScript rs)-> new Type.Builder(rs, Element.I8(rs)),
                0x172d8abc);
        int Offset = AC.RN.nextInt(AC.Width);
        int Count = AC.RN.nextInt(AC.Width - Offset);
        Soob.set_dstXOff(Offset);
        Soob.set_srcXOff(Offset);
        Soob.set_xCount(Count);
        Soob.set_srcMip(0);
        Soob.set_dstMip(1);  // bad destination LOD
        Soob.invoke_test1D();

        AC.finishAllocationCopyTests();
        assertTrue(mRanErrorHandler);

        // The context is dead at this point so make sure it's not reused
        RenderScript.releaseAllContexts();
    }


    /**
     * Test invalid arguments to rsAllocationCopy1D - invalid count
     */
    public void testDebugContextRsAllocationCopy1D_Byte_BadCount() {
        SetupAllocationCopyTests AC =
            new SetupAllocationCopyTests(1, 512,
                (RenderScript rs)-> new Type.Builder(rs, Element.I8(rs)),
                0x172d8abd);
        int Offset = AC.RN.nextInt(AC.Width);
        Soob.set_dstXOff(Offset);
        Soob.set_srcXOff(Offset);
        Soob.set_xCount(AC.Width - Offset + 1);  // Invalid count by 1
        Soob.set_srcMip(0);
        Soob.set_dstMip(0);
        Soob.invoke_test1D();

        AC.finishAllocationCopyTests();
        assertTrue(mRanErrorHandler);

        // The context is dead at this point so make sure it's not reused
        RenderScript.releaseAllContexts();
    }

    /**
     * Test invalid arguments to rsAllocationCopy1D - invalid source offset
     */
    public void testDebugContextRsAllocationCopy1D_Byte_BadSrcOffset() {
        SetupAllocationCopyTests AC =
            new SetupAllocationCopyTests(1, 512,
                (RenderScript rs)-> new Type.Builder(rs, Element.I8(rs)),
                0x172d8abe);

        int Offset = AC.RN.nextInt(AC.Width);
        Soob.set_dstXOff(Offset);
        Soob.set_srcXOff(AC.Width);  // Invalid src offset
        Soob.set_xCount(1);
        Soob.set_srcMip(0);
        Soob.set_dstMip(0);
        Soob.invoke_test1D();

        AC.finishAllocationCopyTests();
        assertTrue(mRanErrorHandler);

        // The context is dead at this point so make sure it's not reused
        RenderScript.releaseAllContexts();
    }

    /**
     * Test invalid arguments to rsAllocationCopy1D - invalid destination offset
     */
    public void testDebugContextRsAllocationCopy1D_Byte_BadDstOffset() {
        SetupAllocationCopyTests AC =
            new SetupAllocationCopyTests(1, 512,
                (RenderScript rs)-> new Type.Builder(rs, Element.I8(rs)),
                0x172d8abf);

        int Offset = AC.RN.nextInt(AC.Width);
        Soob.set_dstXOff(AC.ArrLen);  // Invalid dst offset
        Soob.set_srcXOff(Offset);  // Invalid src offset
        Soob.set_xCount(1);
        Soob.set_srcMip(0);
        Soob.set_dstMip(0);
        Soob.invoke_test1D();

        AC.finishAllocationCopyTests();
        assertTrue(mRanErrorHandler);

        // The context is dead at this point so make sure it's not reused
        RenderScript.releaseAllContexts();
    }


    /**
     * Test invalid arguments to rsAllocationCopy2D - invalid y count
     */
    public void testDebugContextRsAllocationCopy2D_Short_BadYCount() {
        SetupAllocationCopyTests AC =
            new SetupAllocationCopyTests(2, 128,
                (RenderScript rs)-> new Type.Builder(rs, Element.I16(rs)),
                0x172d8ac0);
        int xOff = AC.RN.nextInt(AC.Width);
        int yOff = AC.RN.nextInt(AC.Height);
        int xCount = AC.RN.nextInt(AC.Width - xOff);

        Soob.set_dstXOff(xOff);
        Soob.set_srcXOff(xOff);
        Soob.set_yOff(yOff);
        Soob.set_xCount(xCount);  // Legitimate X count
        Soob.set_yCount(AC.Height - yOff + 1);  // Invalid Y count by 1
        Soob.set_srcMip(0);
        Soob.set_dstMip(0);
        Soob.invoke_test2D();

        AC.finishAllocationCopyTests();
        assertTrue(mRanErrorHandler);

        // The context is dead at this point so make sure it's not reused
        RenderScript.releaseAllContexts();
    }

    /**
     * Test invalid arguments to rsAllocationCopy2D - 2D operation on
     * an 1D Allocation
     */
    public void testDebugContextRsAllocationCopy2D_Short_WrongD() {
        setupDebugContext();

        Random random = new Random(0x172d8ac1);
        int width = random.nextInt(128);
        int height = random.nextInt(128);
        int xOff = random.nextInt(width);
        int yOff = random.nextInt(height);
        int xCount = random.nextInt(width - xOff);
        int yCount = random.nextInt(height - yOff);
        int arr_len = width * height;


        Type.Builder typeBuilder1 = new Type.Builder(mRS, Element.I16(mRS));
        typeBuilder1.setX(width);
        // aIn is one-dimensional
        Allocation aIn = Allocation.createTyped(mRS, typeBuilder1.create());
        Type.Builder typeBuilder2 = new Type.Builder(mRS, Element.I16(mRS));
        typeBuilder2.setX(width).setY(height);
        Allocation aOut = Allocation.createTyped(mRS, typeBuilder2.create());

        Soob.set_aIn2D(aIn);
        Soob.set_aOut2D(aOut);
        Soob.set_srcXOff(xOff);
        Soob.set_dstXOff(xOff);
        Soob.set_yOff(yOff);
        Soob.set_xCount(xCount);  // Legitimate X count
        Soob.set_yCount(yCount);  // Legitimate Y count (w.r.t aOut)
        Soob.set_srcMip(0);
        Soob.set_dstMip(0);
        Soob.invoke_test2D();
        mRS.finish();

        // Flush messages through the pipeline.
        mRS.sendMessage(RS_MSG_TEST_FLUSH, null);
        waitForMessage();

        Soob.destroy();
        assertTrue(mRanErrorHandler);

        // The context is dead at this point so make sure it's not reused
        RenderScript.releaseAllContexts();
    }


}
