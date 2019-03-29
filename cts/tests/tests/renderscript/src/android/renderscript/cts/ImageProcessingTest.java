/*
 * Copyright (C) 2012 The Android Open Source Project
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

import android.renderscript.Byte2;
import android.renderscript.Byte3;
import android.renderscript.Byte4;

import android.renderscript.Double2;
import android.renderscript.Double3;
import android.renderscript.Double4;

import android.renderscript.Element;

import android.renderscript.Float2;
import android.renderscript.Float3;
import android.renderscript.Float4;

import android.renderscript.Int2;
import android.renderscript.Int3;
import android.renderscript.Int4;

import android.renderscript.Long2;
import android.renderscript.Long3;
import android.renderscript.Long4;

import android.renderscript.RSRuntimeException;

import android.renderscript.Short2;
import android.renderscript.Short3;
import android.renderscript.Short4;

import android.renderscript.Matrix4f;
import android.renderscript.Script;

import android.renderscript.Type;

import android.renderscript.ScriptGroup;

import android.renderscript.ScriptIntrinsicBlend;
import android.renderscript.ScriptIntrinsicBlur;
import android.renderscript.ScriptIntrinsicColorMatrix;
import android.renderscript.ScriptIntrinsicConvolve3x3;
import android.renderscript.ScriptIntrinsicConvolve5x5;
import android.renderscript.ScriptIntrinsicLUT;
import android.util.Log;

public class ImageProcessingTest extends RSBaseCompute {
    private Allocation a1, a2;

    private final int MAX_RADIUS = 25;
    private final int dimX = 256;

    @Override
    protected void setUp() throws Exception {
        super.setUp();

        Type t = new Type.Builder(mRS, Element.U8_4(mRS)).setX(dimX).setY(dimX).create();
        a1 = Allocation.createTyped(mRS, t);
        a2 = Allocation.createTyped(mRS, t);
    }

    @Override
    protected void tearDown() throws Exception {
        a1.destroy();
        a2.destroy();
        super.tearDown();
    }

    public void testBlur() {
        ScriptIntrinsicBlur mBlur;
        mBlur = ScriptIntrinsicBlur.create(mRS, Element.U8_4(mRS));

        Allocation a1_copy, a2_copy;
        a1_copy = Allocation.createTyped(mRS, a1.getType());
        a2_copy = Allocation.createTyped(mRS, a2.getType());

        for (int i = 1; i < MAX_RADIUS; i++) {

            a1_copy.copy2DRangeFrom(0, 0, a1.getType().getX(), a1.getType().getY(), a1, 0, 0);

            mBlur.setRadius(i);
            mBlur.setInput(a1_copy);

            mBlur.forEach(a2_copy);

            // validate

        }

        a1_copy.destroy();
        a2_copy.destroy();
        mBlur.destroy();
    }

    public void testBlend() {
        ScriptIntrinsicBlend mBlend;
        mBlend = ScriptIntrinsicBlend.create(mRS, Element.U8_4(mRS));
        int w = 256;
        int h = 256;
        Allocation src = creatAllocation(w, h);
        Allocation dst = creatAllocation(w, h);
        byte[] srcData = new byte[w * h * 4];
        byte[] dstData = new byte[w * h * 4];
        byte[] resultData = new byte[w * h * 4];
        Script.LaunchOptions opt = new Script.LaunchOptions();
        // unclipped but with options
        for (int i = 0; i < 28; i++) {
            buildSrc(srcData, w, h);
            buildDst(dstData, w, h);
            src.copyFromUnchecked(srcData);
            dst.copyFromUnchecked(dstData);

            switch (i) {
                case 0:
                    mBlend.forEachSrc(src, dst);
                    break;
                case 1:
                    mBlend.forEachDst(src, dst);
                    break;
                case 2:
                    mBlend.forEachSrcOver(src, dst);
                    break;
                case 3:
                    mBlend.forEachDstOver(src, dst);
                    break;
                case 4:
                    mBlend.forEachSrcIn(src, dst);
                    break;
                case 5:
                    mBlend.forEachDstIn(src, dst);
                    break;
                case 6:
                    mBlend.forEachSrcOut(src, dst);
                    break;
                case 7:
                    mBlend.forEachDstOut(src, dst);
                    break;
                case 8:
                    mBlend.forEachSrcAtop(src, dst);
                    break;
                case 9:
                    mBlend.forEachDstAtop(src, dst);
                    break;
                case 10:
                    mBlend.forEachXor(src, dst);
                    break;
                case 11:
                    mBlend.forEachAdd(src, dst);
                    break;
                case 12:
                    mBlend.forEachSubtract(src, dst);
                    break;
                case 13:
                    mBlend.forEachMultiply(src, dst);
                    break;
                case 14:
                    mBlend.forEachSrc(src, dst, opt);
                    break;
                case 15:
                    mBlend.forEachDst(src, dst, opt);
                    break;
                case 16:
                    mBlend.forEachSrcOver(src, dst, opt);
                    break;
                case 17:
                    mBlend.forEachDstOver(src, dst, opt);
                    break;
                case 18:
                    mBlend.forEachSrcIn(src, dst, opt);
                    break;
                case 19:
                    mBlend.forEachDstIn(src, dst, opt);
                    break;
                case 20:
                    mBlend.forEachSrcOut(src, dst, opt);
                    break;
                case 21:
                    mBlend.forEachDstOut(src, dst, opt);
                    break;
                case 22:
                    mBlend.forEachSrcAtop(src, dst, opt);
                    break;
                case 23:
                    mBlend.forEachDstAtop(src, dst, opt);
                    break;
                case 24:
                    mBlend.forEachXor(src, dst, opt);
                    break;
                case 25:
                    mBlend.forEachAdd(src, dst, opt);
                    break;
                case 26:
                    mBlend.forEachSubtract(src, dst, opt);
                    break;
                case 27:
                    mBlend.forEachMultiply(src, dst, opt);
                    break;
            }
            dst.copyTo(resultData);
            String name = javaBlend(i%14, srcData, dstData);
            assertTrue(name, similar(resultData,dstData));
            Log.v("BlendUnit", name + " " + similar(resultData, dstData));

        }

        src.destroy();
        dst.destroy();
        mBlend.destroy();
    }

    // utility to create and allocation of a given dimension
    protected Allocation creatAllocation(int w, int h) {
        Type.Builder b = new Type.Builder(mRS, Element.RGBA_8888(mRS));
        b.setX(w);
        b.setY(h);
        return  Allocation.createTyped(mRS, b.create(), Allocation.USAGE_SCRIPT);
    }

   // Compare two images ensuring returning false if error is greater than 2
   // so that it can  support integer and float non identical versions
    public boolean similar(byte[] a, byte[] b) {
        for (int i = 0; i < a.length; i++) {
            int v1 = 0xFF & a[i];
            int v2 = 0xFF & b[i];
            int error = Math.abs(v1 - v2);
            if (error > 2) {
                return false;
            }
        }
        return true;
    }
    // Build a test pattern to be the source pattern designed to provide a wide range of values
    public void buildSrc(byte[] srcData, int width, int height) {
        for (int i = 0; i < srcData.length / 4; i++) {
            int x = i % width;
            int y = i / width;
            int d = (x - width / 2) * (x - width / 2) + (y - height / 2) * (y - height / 2);
            d = (255 * d) / ((width / 2) * (width / 2));
            d = (d > 255) ? 0 : d;

            srcData[i * 4 + 0] = (byte) d; // red
            srcData[i * 4 + 1] = (byte) d; // green
            srcData[i * 4 + 2] = (byte) 0; // blue
            srcData[i * 4 + 3] = (byte) y; // alpha
        }
    }

    // Build a test pattern to be the destination pattern designed to provide a wide range of values
    public void buildDst(byte[] dstData, int width, int height) {
        for (int i = 0; i < dstData.length / 4; i++) {
            int x = i % width;
            int y = i / width;

            dstData[i * 4 + 0] = (byte) 0; // red
            dstData[i * 4 + 1] = (byte) 0; // green
            dstData[i * 4 + 2] = (byte) y; // blue
            dstData[i * 4 + 3] = (byte) x; // alpha
        }

    }

    public String javaBlend(int type, byte[] src, byte[] dst) {

        for (int i = 0; i < dst.length; i += 4) {
            byte[] rgba = func[type].filter(src[i], src[i + 1], src[i + 2], src[i + 3],
                    dst[i], dst[i + 1], dst[i + 2], dst[i + 3]);
            dst[i] = rgba[0];
            dst[i + 1] = rgba[1];
            dst[i + 2] = rgba[2];
            dst[i + 3] = rgba[3];
        }
        return func[type].name;
    }

    // Base class for Java blend implementation supporting float and int implementations

    abstract class BlendFunc {
        float srcR, srcG, srcB, srcA;
        float dstR, dstG, dstB, dstA;
        int s_srcR, s_srcG, s_srcB, s_srcA;
        int s_dstR, s_dstG, s_dstB, s_dstA;
        byte[] rgba = new byte[4];
        String name;

        final int clamp(int c) {
            final int N = 255;
            c &= ~(c >> 31);
            c -= N;
            c &= (c >> 31);
            c += N;
            return c;
        }

        int pack(float a, float r, float g, float b) {
            int ia = clamp((int) (255 * a));
            int ir = clamp((int) (255 * r));
            int ig = clamp((int) (255 * g));
            int ib = clamp((int) (255 * b));
            rgba[0] = (byte) ir;
            rgba[1] = (byte) ig;
            rgba[2] = (byte) ib;
            rgba[3] = (byte) ia;
            return (ia << 24) | (ir << 16) | (ig << 8) | ib;
        }

        int pack(int a, int r, int g, int b) {

            rgba[0] = (byte) clamp(r);
            rgba[1] = (byte) clamp(g);
            rgba[2] = (byte) clamp(b);
            rgba[3] = (byte) clamp(a);
            return 0;
        }

        void unpackSrc(int src) {
            s_srcR = (0xFF & (src >> 16));
            s_srcG = (0xFF & (src >> 8));
            s_srcB = (0xFF & (src >> 0));
            s_srcA = (0xFF & (src >> 24));
            float scale = 1 / 255f;

            srcR = (0xFF & (src >> 16)) * scale;
            srcG = (0xFF & (src >> 8)) * scale;
            srcB = (0xFF & (src >> 0)) * scale;
            srcA = (0xFF & (src >> 24)) * scale;
        }

        void unpackDst(int dst) {
            float scale = 1 / 255f;

            s_dstR = (0xFF & (dst >> 16));
            s_dstG = (0xFF & (dst >> 8));
            s_dstB = (0xFF & (dst >> 0));
            s_dstA = (0xFF & (dst >> 24));

            dstR = (0xFF & (dst >> 16)) * scale;
            dstG = (0xFF & (dst >> 8)) * scale;
            dstB = (0xFF & (dst >> 0)) * scale;
            dstA = (0xFF & (dst >> 24)) * scale;
        }

        int filter(int scr, int dst) {
            unpackSrc(scr);
            unpackDst(dst);
            return blend();
        }

        byte[] filter(byte srcR, byte srcG, byte srcB, byte srcA,
                      byte dstR, byte dstG, byte dstB, byte dstA) {
            float scale = 1 / 255f;
            this.srcR = (0xFF & (srcR)) * scale;
            this.srcG = (0xFF & (srcG)) * scale;
            this.srcB = (0xFF & (srcB)) * scale;
            this.srcA = (0xFF & (srcA)) * scale;

            this.dstR = (0xFF & (dstR)) * scale;
            this.dstG = (0xFF & (dstG)) * scale;
            this.dstB = (0xFF & (dstB)) * scale;
            this.dstA = (0xFF & (dstA)) * scale;
            s_dstR = (0xFF & (dstR));
            s_dstG = (0xFF & (dstG));
            s_dstB = (0xFF & (dstB));
            s_dstA = (0xFF & (dstA));

            s_srcR = (0xFF & (srcR));
            s_srcG = (0xFF & (srcG));
            s_srcB = (0xFF & (srcB));
            s_srcA = (0xFF & (srcA));

            blend();
            return rgba;
        }

        abstract int blend();
    }

    BlendFunc blend_dstAtop = new BlendFunc() {
        // dst = dst.rgb * src.a + (1.0 - dst.a) * src.rgb
        // dst.a = src.a
        {
            name = "blend_dstAtop";
        }

        @Override
        int blend() {
            float r = (dstR * srcA + (1 - dstA) * srcR);
            float g = (dstG * srcA + (1 - dstA) * srcG);
            float b = (dstB * srcA + (1 - dstA) * srcB);
            float a = srcA;

            return pack(a, r, g, b);
        }
    };
    BlendFunc blend_dstIn = new BlendFunc() {
        // Sets dst = dst * src.a
        {
            name = "blend_dstIn";
        }

        @Override
        int blend() {
            float r = (dstR * srcA);
            float g = (dstG * srcA);
            float b = (dstB * srcA);
            float a = (dstA * srcA);
            return pack(a, r, g, b);
        }
    };

    BlendFunc blend_add = new BlendFunc() {
        // dst = dst + src
        {
            name = "blend_add";
        }

        @Override
        int blend() {

            int r = Math.min(s_dstR + s_srcR, 255);
            int g = Math.min(s_dstG + s_srcG, 255);
            int b = Math.min(s_dstB + s_srcB, 255);
            int a = Math.min(s_dstA + s_srcA, 255);
            return pack(a, r, g, b);

        }
    };

    BlendFunc blend_clear = new BlendFunc() {
        // Sets dst = {0, 0, 0, 0}
        {
            name = "blend_clear";
        }

        @Override
        int blend() {
            return pack(0, 0, 0, 0);
        }
    };

    BlendFunc blend_dst = new BlendFunc() {
        // Sets dst = dst
        {
            name = "blend_dst";
        }

        @Override
        int blend() {
            return pack(dstA, dstR, dstG, dstB);
        }
    };

    BlendFunc blend_dstOut = new BlendFunc() {
        // Sets dst = dst * (1.0 - src.a)
        {
            name = "blend_dstOut";
        }

        @Override
        int blend() {
            float r = (dstR * (1 - srcA));
            float g = (dstG * (1 - srcA));
            float b = (dstB * (1 - srcA));
            float a = (dstA * (1 - srcA));
            return pack(a, r, g, b);
        }
    };
    BlendFunc blend_dstOver = new BlendFunc() {
        // Sets dst = dst + src * (1.0 - dst.a)
        {
            name = "blend_dstOver";
        }

        @Override
        int blend() {
            float r = dstR + (srcR * (1 - dstA));
            float g = dstG + (srcG * (1 - dstA));
            float b = dstB + (srcB * (1 - dstA));
            float a = dstA + (srcA * (1 - dstA));
            return pack(a, r, g, b);
        }
    };

    BlendFunc blend_multiply = new BlendFunc() {
        // dst = dst * src
        {
            name = "blend_multiply";
        }

        @Override
        int blend() {
            float r = (srcR * dstR);
            float g = (srcG * dstG);
            float b = (srcB * dstB);
            float a = (srcA * dstA);
            return pack(a, r, g, b);
        }
    };

    BlendFunc blend_src = new BlendFunc() {
        // Sets dst =  src
        {
            name = "blend_src";
        }

        int blend() {
            return pack(srcA, srcR, srcG, srcB);

        }
    };

    BlendFunc blend_srcAtop = new BlendFunc() {
        // dst.rgb = src.rgb * dst.a + (1.0 - src.a) * dst.rgb
        // dst.a = dst.a
        {
            name = "blend_srcAtop";
        }

        @Override
        int blend() {
            float r = (srcR * dstA + (1 - srcA) * dstR);
            float g = (srcG * dstA + (1 - srcA) * dstG);
            float b = (srcB * dstA + (1 - srcA) * dstB);
            float a = (srcA * dstA + (1 - srcA) * dstA);
            return pack(a, r, g, b);
        }
    };

    BlendFunc blend_srcIn = new BlendFunc() {
        // dst = src * dst.a
        {
            name = "blend_srcIn";
        }

        @Override
        int blend() {
            float r = (srcR * dstA);
            float g = (srcG * dstA);
            float b = (srcB * dstA);
            float a = (srcA * dstA);
            ;
            return pack(a, r, g, b);
        }
    };

    BlendFunc blend_srcOut = new BlendFunc() {
        // Sets dst = src * (1.0 - dst.a)
        {
            name = "blend_srcOut";
        }

        @Override
        int blend() {
            float r = (srcR * (1 - dstA));
            float g = (srcG * (1 - dstA));
            float b = (srcB * (1 - dstA));
            float a = (srcA * (1 - dstA));
            ;
            return pack(a, r, g, b);
        }
    };

    BlendFunc blend_srcOver = new BlendFunc() {
        // Sets dst = src + dst * (1.0 - src.a)
        {
            name = "blend_srcOver";
        }

        @Override
        int blend() {
            float r = srcR + (dstR * (1 - srcA));
            float g = srcG + (dstG * (1 - srcA));
            float b = srcB + (dstB * (1 - srcA));
            float a = srcA + (dstA * (1 - srcA));
            return pack(a, r, g, b);
        }
    };

    BlendFunc blend_subtract = new BlendFunc() {
        // Sets dst =  dst - src
        {
            name = "blend_subtract";
        }

        @Override
        int blend() {
            float r = Math.max(dstR - srcR, 0);
            float g = Math.max(dstG - srcG, 0);
            float b = Math.max(dstB - srcB, 0);
            float a = Math.max(dstA - srcA, 0);
            return pack(a, r, g, b);
        }
    };

    // Porter/Duff xor compositing
    BlendFunc blend_pdxor = new BlendFunc() {
        // dst.rgb = src.rgb*(1-dst.a)+(1-src.a)*dst.rgb;
        // dst.a = src.a+dst.a - 2*src.a*dst.a
        {
            name = "blend_pdxor";
        }

        @Override
        int blend() {
            float r = srcR * (1 - dstA) + (dstR * (1 - srcA));
            float g = srcG * (1 - dstA) + (dstG * (1 - srcA));
            float b = srcB * (1 - dstA) + (dstB * (1 - srcA));
            float a = srcA + dstA - (2 * srcA * dstA);
            return pack(a, r, g, b);
        }
    };

    // NOT Porter/Duff xor compositing simple XOR
    BlendFunc blend_xor = new BlendFunc() {
        // Sets dst = {src.r ^ dst.r, src.g ^ dst.g, src.b ^ dst.b, src.a ^ dst.a}
        {
            name = "blend_xor";
        }

        @Override
        int blend() {
            float scale = 1 / 255f;
            float r = (((int) (dstR * 255)) ^ ((int) (srcR * 255))) * scale;
            float g = (((int) (dstG * 255)) ^ ((int) (srcG * 255))) * scale;
            float b = (((int) (dstB * 255)) ^ ((int) (srcB * 255))) * scale;
            float a = (((int) (dstA * 255)) ^ ((int) (srcA * 255))) * scale;
            return pack(a, r, g, b);
        }
    };

    BlendFunc[] func = {
            blend_src,
            blend_dst,
            blend_srcOver,
            blend_dstOver,
            blend_srcIn,
            blend_dstIn,
            blend_srcOut,
            blend_dstOut,
            blend_srcAtop,
            blend_dstAtop,
            blend_xor,
            blend_add,
            blend_subtract,
            blend_multiply,
    };

    public void testColorMatrix() {
        ScriptIntrinsicColorMatrix mColorMatrix;
        mColorMatrix = ScriptIntrinsicColorMatrix.create(mRS, Element.U8_4(mRS));

        Allocation a1_copy, a2_copy;
        a1_copy = Allocation.createTyped(mRS, a1.getType());
        a2_copy = Allocation.createTyped(mRS, a2.getType());

        Matrix4f m = new Matrix4f();
        m.set(1, 0, 0.2f);
        m.set(1, 1, 0.9f);
        m.set(1, 2, 0.2f);

        //test greyscale
        mColorMatrix.setGreyscale();

        a1_copy.copy2DRangeFrom(0, 0, a1.getType().getX(), a1.getType().getY(), a1, 0, 0);
        a2_copy.copy2DRangeFrom(0, 0, a2.getType().getX(), a2.getType().getY(), a2, 0, 0);

        mColorMatrix.forEach(a1_copy, a2_copy);

        //validate greyscale


        //test color matrix
        mColorMatrix.setColorMatrix(m);

        a1_copy.copy2DRangeFrom(0, 0, a1.getType().getX(), a1.getType().getY(), a1, 0, 0);
        a2_copy.copy2DRangeFrom(0, 0, a2.getType().getX(), a2.getType().getY(), a2, 0, 0);

        mColorMatrix.forEach(a1_copy, a2_copy);

        a1_copy.destroy();
        a2_copy.destroy();
        mColorMatrix.destroy();
    }

    public void testConvolve3x3() {
        ScriptIntrinsicConvolve3x3 mConvolve3x3;
        mConvolve3x3 = ScriptIntrinsicConvolve3x3.create(mRS, Element.U8_4(mRS));

        Allocation a1_copy, a2_copy;
        a1_copy = Allocation.createTyped(mRS, a1.getType());
        a2_copy = Allocation.createTyped(mRS, a2.getType());

        a1_copy.copy2DRangeFrom(0, 0, a1.getType().getX(), a1.getType().getY(), a1, 0, 0);

        float f[] = new float[9];
        f[0] =  0.f;    f[1] = -1.f;    f[2] =  0.f;
        f[3] = -1.f;    f[4] =  5.f;    f[5] = -1.f;
        f[6] =  0.f;    f[7] = -1.f;    f[8] =  0.f;

        mConvolve3x3.setCoefficients(f);
        mConvolve3x3.setInput(a1_copy);
        mConvolve3x3.forEach(a2_copy);

        a1_copy.destroy();
        a2_copy.destroy();
        mConvolve3x3.destroy();
    }

    public void testConvolve5x5() {
        ScriptIntrinsicConvolve5x5 mConvolve5x5;
        mConvolve5x5 = ScriptIntrinsicConvolve5x5.create(mRS, Element.U8_4(mRS));

        Allocation a1_copy, a2_copy;
        a1_copy = Allocation.createTyped(mRS, a1.getType());
        a2_copy = Allocation.createTyped(mRS, a2.getType());

        a1_copy.copy2DRangeFrom(0, 0, a1.getType().getX(), a1.getType().getY(), a1, 0, 0);

        float f[] = new float[25];
        f[0] = -1.f; f[1] = -3.f; f[2] = -4.f; f[3] = -3.f; f[4] = -1.f;
        f[5] = -3.f; f[6] =  0.f; f[7] =  6.f; f[8] =  0.f; f[9] = -3.f;
        f[10]= -4.f; f[11]=  6.f; f[12]= 20.f; f[13]=  6.f; f[14]= -4.f;
        f[15]= -3.f; f[16]=  0.f; f[17]=  6.f; f[18]=  0.f; f[19]= -3.f;
        f[20]= -1.f; f[21]= -3.f; f[22]= -4.f; f[23]= -3.f; f[24]= -1.f;

        mConvolve5x5.setCoefficients(f);
        mConvolve5x5.setInput(a1_copy);
        mConvolve5x5.forEach(a2_copy);

        a1_copy.destroy();
        a2_copy.destroy();
        mConvolve5x5.destroy();
    }

    public void testLUT() {
        ScriptIntrinsicLUT mLUT;
        mLUT = ScriptIntrinsicLUT.create(mRS, Element.U8_4(mRS));

        Allocation a1_copy, a2_copy;
        a1_copy = Allocation.createTyped(mRS, a1.getType());
        a2_copy = Allocation.createTyped(mRS, a2.getType());

        a1_copy.copy2DRangeFrom(0, 0, a1.getType().getX(), a1.getType().getY(), a1, 0, 0);

        for (int ct=0; ct < 256; ct++) {
            float f = ((float)ct) / 255.f;

            float r = f;
            if (r < 0.5f) {
                r = 4.0f * r * r * r;
            } else {
                r = 1.0f - r;
                r = 1.0f - (4.0f * r * r * r);
            }
            mLUT.setRed(ct, (int)(r * 255.f + 0.5f));

            float g = f;
            if (g < 0.5f) {
                g = 2.0f * g * g;
            } else {
                g = 1.0f - g;
                g = 1.0f - (2.0f * g * g);
            }
            mLUT.setGreen(ct, (int)(g * 255.f + 0.5f));

            float b = f * 0.5f + 0.25f;
            mLUT.setBlue(ct, (int)(b * 255.f + 0.5f));
        }

        mLUT.forEach(a1_copy, a2_copy);

        a1_copy.destroy();
        a2_copy.destroy();
        mLUT.destroy();
    }

    public void testScriptGroup() {
        ScriptGroup group;

        ScriptIntrinsicConvolve3x3 mConvolve3x3;
        ScriptIntrinsicColorMatrix mColorMatrix;

        mConvolve3x3 = ScriptIntrinsicConvolve3x3.create(mRS, Element.U8_4(mRS));
        mColorMatrix = ScriptIntrinsicColorMatrix.create(mRS, Element.U8_4(mRS));

        Allocation a1_copy, a2_copy;
        a1_copy = Allocation.createTyped(mRS, a1.getType());
        a2_copy = Allocation.createTyped(mRS, a2.getType());

        a1_copy.copy2DRangeFrom(0, 0, a1.getType().getX(), a1.getType().getY(), a1, 0, 0);

        float f[] = new float[9];
        f[0] =  0.f;    f[1] = -1.f;    f[2] =  0.f;
        f[3] = -1.f;    f[4] =  5.f;    f[5] = -1.f;
        f[6] =  0.f;    f[7] = -1.f;    f[8] =  0.f;

        mConvolve3x3.setCoefficients(f);

        Matrix4f m = new Matrix4f();
        m.set(1, 0, 0.2f);
        m.set(1, 1, 0.9f);
        m.set(1, 2, 0.2f);
        mColorMatrix.setColorMatrix(m);

        Type connect = new Type.Builder(mRS, Element.U8_4(mRS)).setX(dimX).setY(dimX).create();

        ScriptGroup.Builder b = new ScriptGroup.Builder(mRS);
        b.addKernel(mConvolve3x3.getKernelID());
        b.addKernel(mColorMatrix.getKernelID());
        b.addConnection(connect, mConvolve3x3.getKernelID(), mColorMatrix.getKernelID());
        group = b.create();

        mConvolve3x3.setInput(a1_copy);
        group.setOutput(mColorMatrix.getKernelID(), a2_copy);
        group.execute();

        // validate

        a1_copy.destroy();
        a2_copy.destroy();
        mConvolve3x3.destroy();
        mColorMatrix.destroy();
    }


}
