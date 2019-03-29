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
import java.util.Random;

public class RSYuvTest extends RSCppTest {
    static {
        System.loadLibrary("rscpptest_jni");
    }

    int width;
    int height;
    byte [] by;
    byte [] bu;
    byte [] bv;
    Allocation ay;
    Allocation au;
    Allocation av;

    int getCWidth() {
        return (width + 1) / 2;
    }
    int getCHeight() {
        return (height + 1) / 2;
    }

    protected void makeYuvBuffer(int w, int h) {
        Random r = new Random();
        width = w;
        height = h;

        by = new byte[w*h];
        bu = new byte[getCWidth() * getCHeight()];
        bv = new byte[getCWidth() * getCHeight()];

        for (int i=0; i < by.length; i++) {
            by[i] = (byte)r.nextInt(256);
        }
        for (int i=0; i < bu.length; i++) {
            bu[i] = (byte)r.nextInt(256);
        }
        for (int i=0; i < bv.length; i++) {
            bv[i] = (byte)r.nextInt(256);
        }

        ay = Allocation.createTyped(mRS, Type.createXY(mRS, Element.U8(mRS), w, h));
        final Type tuv = Type.createXY(mRS, Element.U8(mRS), w >> 1, h >> 1);
        au = Allocation.createTyped(mRS, tuv);
        av = Allocation.createTyped(mRS, tuv);

        ay.copyFrom(by);
        au.copyFrom(bu);
        av.copyFrom(bv);
    }

    public Allocation makeOutput() {
        return Allocation.createTyped(mRS, Type.createXY(mRS, Element.RGBA_8888(mRS), width, height));
    }

    native boolean yuvTest(String path, int X, int Y, byte[] input, byte[] output, int yuvFormat);
    // Test for the API 17 conversion path
    // This used a uchar buffer assuming nv21
    public void testV17() {
        makeYuvBuffer(120, 96);
        Allocation aout = makeOutput();
        Allocation aref = makeOutput();

        byte tmp[] = new byte[(width * height) + (getCWidth() * getCHeight() * 2)];
        int i = 0;
        for (int j = 0; j < (width * height); j++) {
            tmp[i++] = by[j];
        }
        for (int j = 0; j < (getCWidth() * getCHeight()); j++) {
            tmp[i++] = bv[j];
            tmp[i++] = bu[j];
        }

        Allocation ta = Allocation.createSized(mRS, Element.U8(mRS), tmp.length);
        ta.copyFrom(tmp);

        ScriptIntrinsicYuvToRGB syuv = ScriptIntrinsicYuvToRGB.create(mRS, Element.U8(mRS));
        syuv.setInput(ta);
        syuv.forEach(aout);

        byte[] nativeByteAlloc = new byte[width * height * 4];
        yuvTest(this.getContext().getCacheDir().toString(), width, height, tmp, nativeByteAlloc, 0);
        aref.copyFromUnchecked(nativeByteAlloc);

        mVerify.invoke_verify(aref, aout, ay);
        checkForErrors();
    }

    // Test for the API 18 conversion path with yv12
    public void test_YV12() {
        ScriptIntrinsicYuvToRGB syuv = ScriptIntrinsicYuvToRGB.create(mRS, Element.YUV(mRS));

        makeYuvBuffer(512, 512);
        Allocation aout = makeOutput();
        Allocation aref = makeOutput();

        Type.Builder tb = new Type.Builder(mRS, Element.YUV(mRS));
        tb.setX(width);
        tb.setY(height);
        tb.setYuvFormat(android.graphics.ImageFormat.YV12);
        Allocation ta = Allocation.createTyped(mRS, tb.create(), Allocation.USAGE_SCRIPT);

        byte tmp[] = new byte[(width * height) + (getCWidth() * getCHeight() * 2)];
        int i = 0;
        for (int j = 0; j < (width * height); j++) {
            tmp[i++] = by[j];
        }
        for (int j = 0; j < (getCWidth() * getCHeight()); j++) {
            tmp[i++] = bu[j];
        }
        for (int j = 0; j < (getCWidth() * getCHeight()); j++) {
            tmp[i++] = bv[j];
        }
        ta.copyFrom(tmp);

        syuv.setInput(ta);
        syuv.forEach(aout);

        byte[] nativeByteAlloc = new byte[width * height * 4];
        yuvTest(this.getContext().getCacheDir().toString(), width, height, tmp, nativeByteAlloc,
                android.graphics.ImageFormat.YV12);
        aref.copyFromUnchecked(nativeByteAlloc);

        mVerify.invoke_verify(aref, aout, ay);
        checkForErrors();
    }

    // Test for the API 18 conversion path with nv21
    public void test_NV21() {
        ScriptIntrinsicYuvToRGB syuv = ScriptIntrinsicYuvToRGB.create(mRS, Element.YUV(mRS));

        makeYuvBuffer(512, 512);
        Allocation aout = makeOutput();
        Allocation aref = makeOutput();

        Type.Builder tb = new Type.Builder(mRS, Element.YUV(mRS));
        tb.setX(width);
        tb.setY(height);
        tb.setYuvFormat(android.graphics.ImageFormat.NV21);
        Allocation ta = Allocation.createTyped(mRS, tb.create(), Allocation.USAGE_SCRIPT);

        byte tmp[] = new byte[(width * height) + (getCWidth() * getCHeight() * 2)];
        int i = 0;
        for (int j = 0; j < (width * height); j++) {
            tmp[i++] = by[j];
        }
        for (int j = 0; j < (getCWidth() * getCHeight()); j++) {
            tmp[i++] = bv[j];
            tmp[i++] = bu[j];
        }
        ta.copyFrom(tmp);

        syuv.setInput(ta);
        syuv.forEach(aout);

        byte[] nativeByteAlloc = new byte[width * height * 4];
        yuvTest(this.getContext().getCacheDir().toString(), width, height, tmp, nativeByteAlloc,
                android.graphics.ImageFormat.NV21);
        aref.copyFromUnchecked(nativeByteAlloc);

        mVerify.invoke_verify(aref, aout, ay);
        checkForErrors();
    }
}
