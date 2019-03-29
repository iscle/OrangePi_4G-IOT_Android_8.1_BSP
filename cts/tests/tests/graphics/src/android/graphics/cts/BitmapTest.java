/*
 * Copyright (C) 2008 The Android Open Source Project
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
package android.graphics.cts;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.Bitmap.CompressFormat;
import android.graphics.Bitmap.Config;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.ColorSpace;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.os.Debug;
import android.os.Parcel;
import android.os.StrictMode;
import android.support.test.InstrumentationRegistry;
import android.support.test.filters.LargeTest;
import android.support.test.filters.SmallTest;
import android.support.test.runner.AndroidJUnit4;
import android.util.DisplayMetrics;
import android.view.Surface;

import com.android.compatibility.common.util.ColorUtils;
import com.android.compatibility.common.util.WidgetTestUtils;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.IntBuffer;
import java.nio.ShortBuffer;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

@SmallTest
@RunWith(AndroidJUnit4.class)
public class BitmapTest {
    // small alpha values cause color values to be pre-multiplied down, losing accuracy
    private static final int PREMUL_COLOR = Color.argb(2, 255, 254, 253);
    private static final int PREMUL_ROUNDED_COLOR = Color.argb(2, 255, 255, 255);
    private static final int PREMUL_STORED_COLOR = Color.argb(2, 2, 2, 2);

    private static final BitmapFactory.Options HARDWARE_OPTIONS = createHardwareBitmapOptions();

    static {
        System.loadLibrary("ctsgraphics_jni");
    }

    private Resources mRes;
    private Bitmap mBitmap;
    private BitmapFactory.Options mOptions;

    @Before
    public void setup() {
        mRes = InstrumentationRegistry.getTargetContext().getResources();
        mOptions = new BitmapFactory.Options();
        mOptions.inScaled = false;
        mBitmap = BitmapFactory.decodeResource(mRes, R.drawable.start, mOptions);
    }

    @Test(expected=IllegalStateException.class)
    public void testCompressRecycled() {
        mBitmap.recycle();
        mBitmap.compress(CompressFormat.JPEG, 0, null);
    }

    @Test(expected=NullPointerException.class)
    public void testCompressNullStream() {
        mBitmap.compress(CompressFormat.JPEG, 0, null);
    }

    @Test(expected=IllegalArgumentException.class)
    public void testCompressQualityTooLow() {
        mBitmap.compress(CompressFormat.JPEG, -1, new ByteArrayOutputStream());
    }

    @Test(expected=IllegalArgumentException.class)
    public void testCompressQualityTooHigh() {
        mBitmap.compress(CompressFormat.JPEG, 101, new ByteArrayOutputStream());
    }

    @Test
    public void testCompress() {
        assertTrue(mBitmap.compress(CompressFormat.JPEG, 50, new ByteArrayOutputStream()));
    }

    @Test(expected=IllegalStateException.class)
    public void testCopyRecycled() {
        mBitmap.recycle();
        mBitmap.copy(Config.RGB_565, false);
    }

    @Test
    public void testCopy() {
        mBitmap = Bitmap.createBitmap(100, 100, Config.ARGB_8888);
        Bitmap bitmap = mBitmap.copy(Config.ARGB_8888, false);
        WidgetTestUtils.assertEquals(mBitmap, bitmap);
    }

    @Test
    public void testCopyConfigs() {
        Config[] supportedConfigs = new Config[] {
                Config.ALPHA_8, Config.RGB_565, Config.ARGB_8888, Config.RGBA_F16,
        };
        for (Config src : supportedConfigs) {
            for (Config dst : supportedConfigs) {
                Bitmap srcBitmap = Bitmap.createBitmap(1, 1, src);
                srcBitmap.eraseColor(Color.WHITE);
                Bitmap dstBitmap = srcBitmap.copy(dst, false);
                assertNotNull("Should support copying from " + src + " to " + dst,
                        dstBitmap);
                if (Config.ALPHA_8 == dst || Config.ALPHA_8 == src) {
                    // Color will be opaque but color information will be lost.
                    assertEquals("Color should be black when copying from " + src + " to "
                            + dst, Color.BLACK, dstBitmap.getPixel(0, 0));
                } else {
                    assertEquals("Color should be preserved when copying from " + src + " to "
                            + dst, Color.WHITE, dstBitmap.getPixel(0, 0));
                }
            }
        }
    }

    @Test(expected=IllegalArgumentException.class)
    public void testCopyMutableHwBitmap() {
        mBitmap = Bitmap.createBitmap(100, 100, Config.ARGB_8888);
        mBitmap.copy(Config.HARDWARE, true);
    }

    @Test(expected=RuntimeException.class)
    public void testCopyPixelsToBufferUnsupportedBufferClass() {
        final int pixSize = mBitmap.getRowBytes() * mBitmap.getHeight();

        mBitmap.copyPixelsToBuffer(CharBuffer.allocate(pixSize));
    }

    @Test(expected=RuntimeException.class)
    public void testCopyPixelsToBufferBufferTooSmall() {
        final int pixSize = mBitmap.getRowBytes() * mBitmap.getHeight();
        final int tooSmall = pixSize / 2;

        mBitmap.copyPixelsToBuffer(ByteBuffer.allocate(tooSmall));
    }

    @Test
    public void testCopyPixelsToBuffer() {
        final int pixSize = mBitmap.getRowBytes() * mBitmap.getHeight();

        ByteBuffer byteBuf = ByteBuffer.allocate(pixSize);
        assertEquals(0, byteBuf.position());
        mBitmap.copyPixelsToBuffer(byteBuf);
        assertEquals(pixSize, byteBuf.position());

        ShortBuffer shortBuf = ShortBuffer.allocate(pixSize);
        assertEquals(0, shortBuf.position());
        mBitmap.copyPixelsToBuffer(shortBuf);
        assertEquals(pixSize >> 1, shortBuf.position());

        IntBuffer intBuf1 = IntBuffer.allocate(pixSize);
        assertEquals(0, intBuf1.position());
        mBitmap.copyPixelsToBuffer(intBuf1);
        assertEquals(pixSize >> 2, intBuf1.position());

        Bitmap bitmap = Bitmap.createBitmap(mBitmap.getWidth(), mBitmap.getHeight(),
                mBitmap.getConfig());
        intBuf1.position(0); // copyPixelsToBuffer adjusted the position, so rewind to start
        bitmap.copyPixelsFromBuffer(intBuf1);
        IntBuffer intBuf2 = IntBuffer.allocate(pixSize);
        bitmap.copyPixelsToBuffer(intBuf2);

        assertEquals(pixSize >> 2, intBuf2.position());
        assertEquals(intBuf1.position(), intBuf2.position());
        int size = intBuf1.position();
        intBuf1.position(0);
        intBuf2.position(0);
        for (int i = 0; i < size; i++) {
            assertEquals("mismatching pixels at position " + i, intBuf1.get(), intBuf2.get());
        }
    }

    @Test
    public void testCreateBitmap1() {
        int[] colors = createColors(100);
        Bitmap bitmap = Bitmap.createBitmap(colors, 10, 10, Config.RGB_565);
        Bitmap ret = Bitmap.createBitmap(bitmap);
        assertNotNull(ret);
        assertEquals(10, ret.getWidth());
        assertEquals(10, ret.getHeight());
        assertEquals(Config.RGB_565, ret.getConfig());
    }

    @Test(expected=IllegalArgumentException.class)
    public void testCreateBitmapNegativeX() {
        Bitmap.createBitmap(mBitmap, -100, 50, 50, 200);
    }

    @Test
    public void testCreateBitmap2() {
        // special case: output bitmap is equal to the input bitmap
        mBitmap = Bitmap.createBitmap(new int[100 * 100], 100, 100, Config.ARGB_8888);
        Bitmap ret = Bitmap.createBitmap(mBitmap, 0, 0, 100, 100);
        assertNotNull(ret);
        assertTrue(mBitmap.equals(ret));

        //normal case
        mBitmap = Bitmap.createBitmap(100, 100, Config.ARGB_8888);
        ret = Bitmap.createBitmap(mBitmap, 10, 10, 50, 50);
        assertNotNull(ret);
        assertFalse(mBitmap.equals(ret));
    }

    @Test(expected=IllegalArgumentException.class)
    public void testCreateBitmapNegativeXY() {
        mBitmap = Bitmap.createBitmap(100, 100, Config.ARGB_8888);

        // abnormal case: x and/or y less than 0
        Bitmap.createBitmap(mBitmap, -1, -1, 10, 10, null, false);
    }

    @Test(expected=IllegalArgumentException.class)
    public void testCreateBitmapNegativeWidthHeight() {
        mBitmap = Bitmap.createBitmap(100, 100, Config.ARGB_8888);

        // abnormal case: width and/or height less than 0
        Bitmap.createBitmap(mBitmap, 1, 1, -10, -10, null, false);
    }

    @Test(expected=IllegalArgumentException.class)
    public void testCreateBitmapXRegionTooWide() {
        mBitmap = Bitmap.createBitmap(100, 100, Config.ARGB_8888);

        // abnormal case: (x + width) bigger than source bitmap's width
        Bitmap.createBitmap(mBitmap, 10, 10, 95, 50, null, false);
    }

    @Test(expected=IllegalArgumentException.class)
    public void testCreateBitmapYRegionTooTall() {
        mBitmap = Bitmap.createBitmap(100, 100, Config.ARGB_8888);

        // abnormal case: (y + height) bigger than source bitmap's height
        Bitmap.createBitmap(mBitmap, 10, 10, 50, 95, null, false);
    }

    @Test(expected=IllegalArgumentException.class)
    public void testCreateMutableBitmapWithHardwareConfig() {
        Bitmap.createBitmap(100, 100, Config.HARDWARE);
    }

    @Test
    public void testCreateBitmap3() {
        // special case: output bitmap is equal to the input bitmap
        mBitmap = Bitmap.createBitmap(new int[100 * 100], 100, 100, Config.ARGB_8888);
        Bitmap ret = Bitmap.createBitmap(mBitmap, 0, 0, 100, 100, null, false);
        assertNotNull(ret);
        assertTrue(mBitmap.equals(ret));

        // normal case
        mBitmap = Bitmap.createBitmap(100, 100, Config.ARGB_8888);
        ret = Bitmap.createBitmap(mBitmap, 10, 10, 50, 50, new Matrix(), true);
        assertNotNull(ret);
        assertFalse(mBitmap.equals(ret));
    }

    @Test
    public void testCreateBitmap4() {
        Bitmap ret = Bitmap.createBitmap(100, 200, Config.RGB_565);
        assertNotNull(ret);
        assertEquals(100, ret.getWidth());
        assertEquals(200, ret.getHeight());
        assertEquals(Config.RGB_565, ret.getConfig());
    }

    private static void verify2x2BitmapContents(int[] expected, Bitmap observed) {
        ColorUtils.verifyColor(expected[0], observed.getPixel(0, 0));
        ColorUtils.verifyColor(expected[1], observed.getPixel(1, 0));
        ColorUtils.verifyColor(expected[2], observed.getPixel(0, 1));
        ColorUtils.verifyColor(expected[3], observed.getPixel(1, 1));
    }

    @Test
    public void testCreateBitmap_matrix() {
        int[] colorArray = new int[] { Color.RED, Color.GREEN, Color.BLUE, Color.BLACK };
        Bitmap src = Bitmap.createBitmap(2, 2, Config.ARGB_8888);
        src.setPixels(colorArray,0, 2, 0, 0, 2, 2);

        // baseline
        verify2x2BitmapContents(colorArray, src);

        // null
        Bitmap dst = Bitmap.createBitmap(src, 0, 0, 2, 2, null, false);
        verify2x2BitmapContents(colorArray, dst);

        // identity matrix
        Matrix matrix = new Matrix();
        dst = Bitmap.createBitmap(src, 0, 0, 2, 2, matrix, false);
        verify2x2BitmapContents(colorArray, dst);

        // big scale - only red visible
        matrix.setScale(10, 10);
        dst = Bitmap.createBitmap(src, 0, 0, 2, 2, matrix, false);
        verify2x2BitmapContents(new int[] { Color.RED, Color.RED, Color.RED, Color.RED }, dst);

        // rotation
        matrix.setRotate(90);
        dst = Bitmap.createBitmap(src, 0, 0, 2, 2, matrix, false);
        verify2x2BitmapContents(
                new int[] { Color.BLUE, Color.RED, Color.BLACK, Color.GREEN }, dst);
    }

    @Test(expected=IllegalArgumentException.class)
    public void testCreateBitmapFromColorsNegativeWidthHeight() {
        int[] colors = createColors(100);

        // abnormal case: width and/or height less than 0
        Bitmap.createBitmap(colors, 0, 100, -1, 100, Config.RGB_565);
    }

    @Test(expected=IllegalArgumentException.class)
    public void testCreateBitmapFromColorsIllegalStride() {
        int[] colors = createColors(100);

        // abnormal case: stride less than width and bigger than -width
        Bitmap.createBitmap(colors, 10, 10, 100, 100, Config.RGB_565);
    }

    @Test(expected=ArrayIndexOutOfBoundsException.class)
    public void testCreateBitmapFromColorsNegativeOffset() {
        int[] colors = createColors(100);

        // abnormal case: offset less than 0
        Bitmap.createBitmap(colors, -10, 100, 100, 100, Config.RGB_565);
    }

    @Test(expected=ArrayIndexOutOfBoundsException.class)
    public void testCreateBitmapFromColorsOffsetTooLarge() {
        int[] colors = createColors(100);

        // abnormal case: (offset + width) bigger than colors' length
        Bitmap.createBitmap(colors, 10, 100, 100, 100, Config.RGB_565);
    }

    @Test(expected=ArrayIndexOutOfBoundsException.class)
    public void testCreateBitmapFromColorsScalnlineTooLarge() {
        int[] colors = createColors(100);

        // abnormal case: (lastScanline + width) bigger than colors' length
        Bitmap.createBitmap(colors, 10, 100, 50, 100, Config.RGB_565);
    }

    @Test
    public void testCreateBitmap6() {
        int[] colors = createColors(100);

        // normal case
        Bitmap ret = Bitmap.createBitmap(colors, 5, 10, 10, 5, Config.RGB_565);
        assertNotNull(ret);
        assertEquals(10, ret.getWidth());
        assertEquals(5, ret.getHeight());
        assertEquals(Config.RGB_565, ret.getConfig());
    }

    @Test
    public void testCreateBitmap_displayMetrics_mutable() {
        DisplayMetrics metrics =
                InstrumentationRegistry.getTargetContext().getResources().getDisplayMetrics();

        Bitmap bitmap;
        bitmap = Bitmap.createBitmap(metrics, 10, 10, Config.ARGB_8888);
        assertTrue(bitmap.isMutable());
        assertEquals(metrics.densityDpi, bitmap.getDensity());

        bitmap = Bitmap.createBitmap(metrics, 10, 10, Config.ARGB_8888);
        assertTrue(bitmap.isMutable());
        assertEquals(metrics.densityDpi, bitmap.getDensity());

        bitmap = Bitmap.createBitmap(metrics, 10, 10, Config.ARGB_8888, true);
        assertTrue(bitmap.isMutable());
        assertEquals(metrics.densityDpi, bitmap.getDensity());

        bitmap = Bitmap.createBitmap(metrics, 10, 10, Config.ARGB_8888, true, ColorSpace.get(
                ColorSpace.Named.SRGB));

        assertTrue(bitmap.isMutable());
        assertEquals(metrics.densityDpi, bitmap.getDensity());

        int[] colors = createColors(100);
        assertNotNull(Bitmap.createBitmap(metrics, colors, 0, 10, 10, 10, Config.ARGB_8888));
        assertNotNull(Bitmap.createBitmap(metrics, colors, 10, 10, Config.ARGB_8888));
    }

    @Test
    public void testCreateBitmap_displayMetrics_immutable() {
        DisplayMetrics metrics =
                InstrumentationRegistry.getTargetContext().getResources().getDisplayMetrics();
        int[] colors = createColors(100);

        Bitmap bitmap;
        bitmap = Bitmap.createBitmap(metrics, colors, 0, 10, 10, 10, Config.ARGB_8888);
        assertFalse(bitmap.isMutable());
        assertEquals(metrics.densityDpi, bitmap.getDensity());

        bitmap = Bitmap.createBitmap(metrics, colors, 10, 10, Config.ARGB_8888);
        assertFalse(bitmap.isMutable());
        assertEquals(metrics.densityDpi, bitmap.getDensity());
    }

    @Test
    public void testCreateScaledBitmap() {
        mBitmap = Bitmap.createBitmap(100, 200, Config.RGB_565);
        Bitmap ret = Bitmap.createScaledBitmap(mBitmap, 50, 100, false);
        assertNotNull(ret);
        assertEquals(50, ret.getWidth());
        assertEquals(100, ret.getHeight());
    }

    @Test
    public void testGenerationId() {
        Bitmap bitmap = Bitmap.createBitmap(10, 10, Config.ARGB_8888);
        int genId = bitmap.getGenerationId();
        assertEquals("not expected to change", genId, bitmap.getGenerationId());
        bitmap.setDensity(bitmap.getDensity() + 4);
        assertEquals("not expected to change", genId, bitmap.getGenerationId());
        bitmap.getPixel(0, 0);
        assertEquals("not expected to change", genId, bitmap.getGenerationId());

        int beforeGenId = bitmap.getGenerationId();
        bitmap.eraseColor(Color.WHITE);
        int afterGenId = bitmap.getGenerationId();
        assertTrue("expected to increase", afterGenId > beforeGenId);

        beforeGenId = bitmap.getGenerationId();
        bitmap.setPixel(4, 4, Color.BLUE);
        afterGenId = bitmap.getGenerationId();
        assertTrue("expected to increase again", afterGenId > beforeGenId);
    }

    @Test
    public void testDescribeContents() {
        assertEquals(0, mBitmap.describeContents());
    }

    @Test(expected=IllegalStateException.class)
    public void testEraseColorOnRecycled() {
        mBitmap.recycle();

        mBitmap.eraseColor(0);
    }

    @Test(expected=IllegalStateException.class)
    public void testEraseColorOnImmutable() {
        mBitmap = BitmapFactory.decodeResource(mRes, R.drawable.start, mOptions);

        //abnormal case: bitmap is immutable
        mBitmap.eraseColor(0);
    }

    @Test
    public void testEraseColor() {
        // normal case
        mBitmap = Bitmap.createBitmap(100, 100, Config.ARGB_8888);
        mBitmap.eraseColor(0xffff0000);
        assertEquals(0xffff0000, mBitmap.getPixel(10, 10));
        assertEquals(0xffff0000, mBitmap.getPixel(50, 50));
    }

    @Test(expected=IllegalStateException.class)
    public void testExtractAlphaFromRecycled() {
        mBitmap.recycle();

        mBitmap.extractAlpha();
    }

    @Test
    public void testExtractAlpha() {
        // normal case
        mBitmap = BitmapFactory.decodeResource(mRes, R.drawable.start, mOptions);
        Bitmap ret = mBitmap.extractAlpha();
        assertNotNull(ret);
        int source = mBitmap.getPixel(10, 20);
        int result = ret.getPixel(10, 20);
        assertEquals(Color.alpha(source), Color.alpha(result));
        assertEquals(0xFF, Color.alpha(result));
    }

    @Test(expected=IllegalStateException.class)
    public void testExtractAlphaWithPaintAndOffsetFromRecycled() {
        mBitmap.recycle();

        mBitmap.extractAlpha(new Paint(), new int[]{0, 1});
    }

    @Test
    public void testExtractAlphaWithPaintAndOffset() {
        // normal case
        mBitmap = BitmapFactory.decodeResource(mRes, R.drawable.start, mOptions);
        Bitmap ret = mBitmap.extractAlpha(new Paint(), new int[]{0, 1});
        assertNotNull(ret);
        int source = mBitmap.getPixel(10, 20);
        int result = ret.getPixel(10, 20);
        assertEquals(Color.alpha(source), Color.alpha(result));
        assertEquals(0xFF, Color.alpha(result));
    }

    @Test
    public void testGetAllocationByteCount() {
        mBitmap = Bitmap.createBitmap(100, 200, Bitmap.Config.ALPHA_8);
        int alloc = mBitmap.getAllocationByteCount();
        assertEquals(mBitmap.getByteCount(), alloc);

        // reconfigure same size
        mBitmap.reconfigure(50, 100, Bitmap.Config.ARGB_8888);
        assertEquals(mBitmap.getByteCount(), alloc);
        assertEquals(mBitmap.getAllocationByteCount(), alloc);

        // reconfigure different size
        mBitmap.reconfigure(10, 10, Bitmap.Config.ALPHA_8);
        assertEquals(mBitmap.getByteCount(), 100);
        assertEquals(mBitmap.getAllocationByteCount(), alloc);
    }

    @Test
    public void testGetConfig() {
        Bitmap bm0 = Bitmap.createBitmap(100, 200, Bitmap.Config.ALPHA_8);
        Bitmap bm1 = Bitmap.createBitmap(100, 200, Bitmap.Config.ARGB_8888);
        Bitmap bm2 = Bitmap.createBitmap(100, 200, Bitmap.Config.RGB_565);
        Bitmap bm3 = Bitmap.createBitmap(100, 200, Bitmap.Config.ARGB_4444);

        assertEquals(Bitmap.Config.ALPHA_8, bm0.getConfig());
        assertEquals(Bitmap.Config.ARGB_8888, bm1.getConfig());
        assertEquals(Bitmap.Config.RGB_565, bm2.getConfig());
        // Attempting to create a 4444 bitmap actually creates an 8888 bitmap.
        assertEquals(Bitmap.Config.ARGB_8888, bm3.getConfig());

        // Can't call Bitmap.createBitmap with Bitmap.Config.HARDWARE,
        // because createBitmap creates mutable bitmap and hardware bitmaps are always immutable,
        // so such call will throw an exception.
        Bitmap hardwareBitmap = BitmapFactory.decodeResource(mRes, R.drawable.robot,
                HARDWARE_OPTIONS);
        assertEquals(Bitmap.Config.HARDWARE, hardwareBitmap.getConfig());
    }

    @Test
    public void testGetHeight() {
        assertEquals(31, mBitmap.getHeight());
        mBitmap = Bitmap.createBitmap(100, 200, Bitmap.Config.ARGB_8888);
        assertEquals(200, mBitmap.getHeight());
    }

    @Test
    public void testGetNinePatchChunk() {
        assertNull(mBitmap.getNinePatchChunk());
    }

    @Test(expected=IllegalStateException.class)
    public void testGetPixelFromRecycled() {
        mBitmap.recycle();

        mBitmap.getPixel(10, 16);
    }

    @Test(expected=IllegalArgumentException.class)
    public void testGetPixelXTooLarge() {
        mBitmap = Bitmap.createBitmap(100, 200, Bitmap.Config.RGB_565);

        // abnormal case: x bigger than the source bitmap's width
        mBitmap.getPixel(200, 16);
    }

    @Test(expected=IllegalArgumentException.class)
    public void testGetPixelYTooLarge() {
        mBitmap = Bitmap.createBitmap(100, 200, Bitmap.Config.RGB_565);

        // abnormal case: y bigger than the source bitmap's height
        mBitmap.getPixel(10, 300);
    }

    @Test
    public void testGetPixel() {
        mBitmap = Bitmap.createBitmap(100, 200, Bitmap.Config.RGB_565);

        // normal case 565
        mBitmap.setPixel(10, 16, 0xFF << 24);
        assertEquals(0xFF << 24, mBitmap.getPixel(10, 16));

        // normal case A_8
        mBitmap = Bitmap.createBitmap(10, 10, Config.ALPHA_8);
        mBitmap.setPixel(5, 5, 0xFFFFFFFF);
        assertEquals(0xFF000000, mBitmap.getPixel(5, 5));
        mBitmap.setPixel(5, 5, 0xA8A8A8A8);
        assertEquals(0xA8000000, mBitmap.getPixel(5, 5));
        mBitmap.setPixel(5, 5, 0x00000000);
        assertEquals(0x00000000, mBitmap.getPixel(5, 5));
        mBitmap.setPixel(5, 5, 0x1F000000);
        assertEquals(0x1F000000, mBitmap.getPixel(5, 5));
    }

    @Test
    public void testGetRowBytes() {
        Bitmap bm0 = Bitmap.createBitmap(100, 200, Bitmap.Config.ALPHA_8);
        Bitmap bm1 = Bitmap.createBitmap(100, 200, Bitmap.Config.ARGB_8888);
        Bitmap bm2 = Bitmap.createBitmap(100, 200, Bitmap.Config.RGB_565);
        Bitmap bm3 = Bitmap.createBitmap(100, 200, Bitmap.Config.ARGB_4444);

        assertEquals(100, bm0.getRowBytes());
        assertEquals(400, bm1.getRowBytes());
        assertEquals(200, bm2.getRowBytes());
        // Attempting to create a 4444 bitmap actually creates an 8888 bitmap.
        assertEquals(400, bm3.getRowBytes());
    }

    @Test
    public void testGetWidth() {
        assertEquals(31, mBitmap.getWidth());
        mBitmap = Bitmap.createBitmap(100, 200, Bitmap.Config.ARGB_8888);
        assertEquals(100, mBitmap.getWidth());
    }

    @Test
    public void testHasAlpha() {
        assertFalse(mBitmap.hasAlpha());
        mBitmap = Bitmap.createBitmap(100, 200, Bitmap.Config.ARGB_8888);
        assertTrue(mBitmap.hasAlpha());
    }

    @Test
    public void testIsMutable() {
        assertFalse(mBitmap.isMutable());
        mBitmap = Bitmap.createBitmap(100, 100, Config.ARGB_8888);
        assertTrue(mBitmap.isMutable());
    }

    @Test
    public void testIsRecycled() {
        assertFalse(mBitmap.isRecycled());
        mBitmap.recycle();
        assertTrue(mBitmap.isRecycled());
    }

    @Test
    public void testReconfigure() {
        mBitmap = Bitmap.createBitmap(100, 200, Bitmap.Config.RGB_565);
        int alloc = mBitmap.getAllocationByteCount();

        // test shrinking
        mBitmap.reconfigure(50, 100, Bitmap.Config.ALPHA_8);
        assertEquals(mBitmap.getAllocationByteCount(), alloc);
        assertEquals(mBitmap.getByteCount() * 8, alloc);
    }

    @Test(expected=IllegalArgumentException.class)
    public void testReconfigureExpanding() {
        mBitmap = Bitmap.createBitmap(100, 200, Bitmap.Config.RGB_565);
        mBitmap.reconfigure(101, 201, Bitmap.Config.ARGB_8888);
    }

    @Test(expected=IllegalStateException.class)
    public void testReconfigureMutable() {
        mBitmap = BitmapFactory.decodeResource(mRes, R.drawable.start, mOptions);
        mBitmap.reconfigure(1, 1, Bitmap.Config.ALPHA_8);
    }

    // Used by testAlphaAndPremul. FIXME: Should we also test Index8? That would require decoding a
    // Bitmap, since one cannot be created directly. It will also have a Config of null, since it
    // has no Java equivalent.
    private static Config[] CONFIGS = new Config[] { Config.ALPHA_8, Config.ARGB_4444,
            Config.ARGB_8888, Config.RGB_565 };

    // test that reconfigure, setHasAlpha, and setPremultiplied behave as expected with
    // respect to alpha and premultiplied.
    @Test
    public void testAlphaAndPremul() {
        boolean falseTrue[] = new boolean[] { false, true };
        for (Config fromConfig : CONFIGS) {
            for (Config toConfig : CONFIGS) {
                for (boolean hasAlpha : falseTrue) {
                    for (boolean isPremul : falseTrue) {
                        Bitmap bitmap = Bitmap.createBitmap(10, 10, fromConfig);

                        // 4444 is deprecated, and will convert to 8888. No need to
                        // attempt a reconfigure, which will be tested when fromConfig
                        // is 8888.
                        if (fromConfig == Config.ARGB_4444) {
                            assertEquals(bitmap.getConfig(), Config.ARGB_8888);
                            break;
                        }

                        bitmap.setHasAlpha(hasAlpha);
                        bitmap.setPremultiplied(isPremul);

                        verifyAlphaAndPremul(bitmap, hasAlpha, isPremul, false);

                        // reconfigure to a smaller size so the function will still succeed when
                        // going to a Config that requires more bits.
                        bitmap.reconfigure(1, 1, toConfig);
                        if (toConfig == Config.ARGB_4444) {
                            assertEquals(bitmap.getConfig(), Config.ARGB_8888);
                        } else {
                            assertEquals(bitmap.getConfig(), toConfig);
                        }

                        // Check that the alpha and premultiplied state has not changed (unless
                        // we expected it to).
                        verifyAlphaAndPremul(bitmap, hasAlpha, isPremul, fromConfig == Config.RGB_565);
                    }
                }
            }
        }
    }

    /**
     *  Assert that bitmap returns the appropriate values for hasAlpha() and isPremultiplied().
     *  @param bitmap Bitmap to check.
     *  @param expectedAlpha Expected return value from bitmap.hasAlpha(). Note that this is based
     *          on what was set, but may be different from the actual return value depending on the
     *          Config and convertedFrom565.
     *  @param expectedPremul Expected return value from bitmap.isPremultiplied(). Similar to
     *          expectedAlpha, this is based on what was set, but may be different from the actual
     *          return value depending on the Config.
     *  @param convertedFrom565 Whether bitmap was converted to its current Config by being
     *          reconfigured from RGB_565. If true, and bitmap is now a Config that supports alpha,
     *          hasAlpha() is expected to be true even if expectedAlpha is false.
     */
    private void verifyAlphaAndPremul(Bitmap bitmap, boolean expectedAlpha, boolean expectedPremul,
            boolean convertedFrom565) {
        switch (bitmap.getConfig()) {
            case ARGB_4444:
                // This shouldn't happen, since we don't allow creating or converting
                // to 4444.
                assertFalse(true);
                break;
            case RGB_565:
                assertFalse(bitmap.hasAlpha());
                assertFalse(bitmap.isPremultiplied());
                break;
            case ALPHA_8:
                // ALPHA_8 behaves mostly the same as 8888, except for premultiplied. Fall through.
            case ARGB_8888:
                // Since 565 is necessarily opaque, we revert to hasAlpha when switching to a type
                // that can have alpha.
                if (convertedFrom565) {
                    assertTrue(bitmap.hasAlpha());
                } else {
                    assertEquals(bitmap.hasAlpha(), expectedAlpha);
                }

                if (bitmap.hasAlpha()) {
                    // ALPHA_8's premultiplied status is undefined.
                    if (bitmap.getConfig() != Config.ALPHA_8) {
                        assertEquals(bitmap.isPremultiplied(), expectedPremul);
                    }
                } else {
                    // Opaque bitmap is never considered premultiplied.
                    assertFalse(bitmap.isPremultiplied());
                }
                break;
        }
    }

    @Test
    public void testSetConfig() {
        mBitmap = Bitmap.createBitmap(100, 200, Bitmap.Config.RGB_565);
        int alloc = mBitmap.getAllocationByteCount();

        // test shrinking
        mBitmap.setConfig(Bitmap.Config.ALPHA_8);
        assertEquals(mBitmap.getAllocationByteCount(), alloc);
        assertEquals(mBitmap.getByteCount() * 2, alloc);
    }

    @Test(expected=IllegalArgumentException.class)
    public void testSetConfigExpanding() {
        mBitmap = Bitmap.createBitmap(100, 200, Bitmap.Config.RGB_565);
        // test expanding
        mBitmap.setConfig(Bitmap.Config.ARGB_8888);
    }

    @Test(expected=IllegalStateException.class)
    public void testSetConfigMutable() {
        // test mutable
        mBitmap = BitmapFactory.decodeResource(mRes, R.drawable.start, mOptions);
        mBitmap.setConfig(Bitmap.Config.ALPHA_8);
    }

    @Test
    public void testSetHeight() {
        mBitmap = Bitmap.createBitmap(100, 200, Bitmap.Config.ARGB_8888);
        int alloc = mBitmap.getAllocationByteCount();

        // test shrinking
        mBitmap.setHeight(100);
        assertEquals(mBitmap.getAllocationByteCount(), alloc);
        assertEquals(mBitmap.getByteCount() * 2, alloc);
    }

    @Test(expected=IllegalArgumentException.class)
    public void testSetHeightExpanding() {
        // test expanding
        mBitmap = Bitmap.createBitmap(100, 200, Bitmap.Config.ARGB_8888);
        mBitmap.setHeight(201);
    }

    @Test(expected=IllegalStateException.class)
    public void testSetHeightMutable() {
        // test mutable
        mBitmap = BitmapFactory.decodeResource(mRes, R.drawable.start, mOptions);
        mBitmap.setHeight(1);
    }

    @Test(expected=IllegalStateException.class)
    public void testSetPixelOnRecycled() {
        int color = 0xff << 24;

        mBitmap.recycle();
        mBitmap.setPixel(10, 16, color);
    }

    @Test(expected=IllegalStateException.class)
    public void testSetPixelOnImmutable() {
        int color = 0xff << 24;
        mBitmap = BitmapFactory.decodeResource(mRes, R.drawable.start, mOptions);

        mBitmap.setPixel(10, 16, color);
    }

    @Test(expected=IllegalArgumentException.class)
    public void testSetPixelXIsTooLarge() {
        int color = 0xff << 24;
        mBitmap = Bitmap.createBitmap(100, 200, Bitmap.Config.RGB_565);

        // abnormal case: x bigger than the source bitmap's width
        mBitmap.setPixel(200, 16, color);
    }

    @Test(expected=IllegalArgumentException.class)
    public void testSetPixelYIsTooLarge() {
        int color = 0xff << 24;
        mBitmap = Bitmap.createBitmap(100, 200, Bitmap.Config.RGB_565);

        // abnormal case: y bigger than the source bitmap's height
        mBitmap.setPixel(10, 300, color);
    }

    @Test
    public void testSetPixel() {
        int color = 0xff << 24;
        mBitmap = Bitmap.createBitmap(100, 200, Bitmap.Config.RGB_565);

        // normal case
        mBitmap.setPixel(10, 16, color);
        assertEquals(color, mBitmap.getPixel(10, 16));
    }

    @Test(expected=IllegalStateException.class)
    public void testSetPixelsOnRecycled() {
        int[] colors = createColors(100);

        mBitmap.recycle();
        mBitmap.setPixels(colors, 0, 0, 0, 0, 0, 0);
    }

    @Test(expected=IllegalStateException.class)
    public void testSetPixelsOnImmutable() {
        int[] colors = createColors(100);
        mBitmap = BitmapFactory.decodeResource(mRes, R.drawable.start, mOptions);

        mBitmap.setPixels(colors, 0, 0, 0, 0, 0, 0);
    }

    @Test(expected=IllegalArgumentException.class)
    public void testSetPixelsXYNegative() {
        int[] colors = createColors(100);
        mBitmap = Bitmap.createBitmap(100, 100, Bitmap.Config.ARGB_8888);

        // abnormal case: x and/or y less than 0
        mBitmap.setPixels(colors, 0, 0, -1, -1, 200, 16);
    }

    @Test(expected=IllegalArgumentException.class)
    public void testSetPixelsWidthHeightNegative() {
        int[] colors = createColors(100);
        mBitmap = Bitmap.createBitmap(100, 100, Bitmap.Config.ARGB_8888);

        // abnormal case: width and/or height less than 0
        mBitmap.setPixels(colors, 0, 0, 0, 0, -1, -1);
    }

    @Test(expected=IllegalArgumentException.class)
    public void testSetPixelsXTooHigh() {
        int[] colors = createColors(100);
        mBitmap = Bitmap.createBitmap(100, 100, Bitmap.Config.ARGB_8888);

        // abnormal case: (x + width) bigger than the source bitmap's width
        mBitmap.setPixels(colors, 0, 0, 10, 10, 95, 50);
    }

    @Test(expected=IllegalArgumentException.class)
    public void testSetPixelsYTooHigh() {
        int[] colors = createColors(100);
        mBitmap = Bitmap.createBitmap(100, 100, Bitmap.Config.ARGB_8888);

        // abnormal case: (y + height) bigger than the source bitmap's height
        mBitmap.setPixels(colors, 0, 0, 10, 10, 50, 95);
    }

    @Test(expected=IllegalArgumentException.class)
    public void testSetPixelsStrideIllegal() {
        int[] colors = createColors(100);
        mBitmap = Bitmap.createBitmap(100, 100, Bitmap.Config.ARGB_8888);

        // abnormal case: stride less than width and bigger than -width
        mBitmap.setPixels(colors, 0, 10, 10, 10, 50, 50);
    }

    @Test(expected=ArrayIndexOutOfBoundsException.class)
    public void testSetPixelsOffsetNegative() {
        int[] colors = createColors(100);
        mBitmap = Bitmap.createBitmap(100, 100, Bitmap.Config.ARGB_8888);

        // abnormal case: offset less than 0
        mBitmap.setPixels(colors, -1, 50, 10, 10, 50, 50);
    }

    @Test(expected=ArrayIndexOutOfBoundsException.class)
    public void testSetPixelsOffsetTooBig() {
        int[] colors = createColors(100);
        mBitmap = Bitmap.createBitmap(100, 100, Bitmap.Config.ARGB_8888);

        // abnormal case: (offset + width) bigger than the length of colors
        mBitmap.setPixels(colors, 60, 50, 10, 10, 50, 50);
    }

    @Test(expected=ArrayIndexOutOfBoundsException.class)
    public void testSetPixelsLastScanlineNegative() {
        int[] colors = createColors(100);
        mBitmap = Bitmap.createBitmap(100, 100, Bitmap.Config.ARGB_8888);

        // abnormal case: lastScanline less than 0
        mBitmap.setPixels(colors, 10, -50, 10, 10, 50, 50);
    }

    @Test(expected=ArrayIndexOutOfBoundsException.class)
    public void testSetPixelsLastScanlineTooBig() {
        int[] colors = createColors(100);
        mBitmap = Bitmap.createBitmap(100, 100, Bitmap.Config.ARGB_8888);

        // abnormal case: (lastScanline + width) bigger than the length of colors
        mBitmap.setPixels(colors, 10, 50, 10, 10, 50, 50);
    }

    @Test
    public void testSetPixels() {
        int[] colors = createColors(100 * 100);
        mBitmap = Bitmap.createBitmap(100, 100, Bitmap.Config.ARGB_8888);
        mBitmap.setPixels(colors, 0, 100, 0, 0, 100, 100);
        int[] ret = new int[100 * 100];
        mBitmap.getPixels(ret, 0, 100, 0, 0, 100, 100);

        for(int i = 0; i < 10000; i++){
            assertEquals(ret[i], colors[i]);
        }
    }

    private void verifyPremultipliedBitmapConfig(Config config, boolean expectedPremul) {
        Bitmap bitmap = Bitmap.createBitmap(1, 1, config);
        bitmap.setPremultiplied(true);
        bitmap.setPixel(0, 0, Color.TRANSPARENT);
        assertTrue(bitmap.isPremultiplied() == expectedPremul);

        bitmap.setHasAlpha(false);
        assertFalse(bitmap.isPremultiplied());
    }

    @Test
    public void testSetPremultipliedSimple() {
        verifyPremultipliedBitmapConfig(Bitmap.Config.ALPHA_8, true);
        verifyPremultipliedBitmapConfig(Bitmap.Config.RGB_565, false);
        verifyPremultipliedBitmapConfig(Bitmap.Config.ARGB_4444, true);
        verifyPremultipliedBitmapConfig(Bitmap.Config.ARGB_8888, true);
    }

    @Test
    public void testSetPremultipliedData() {
        // with premul, will store 2,2,2,2, so it doesn't get value correct
        Bitmap bitmap = Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888);
        bitmap.setPixel(0, 0, PREMUL_COLOR);
        assertEquals(bitmap.getPixel(0, 0), PREMUL_ROUNDED_COLOR);

        // read premultiplied value directly
        bitmap.setPremultiplied(false);
        assertEquals(bitmap.getPixel(0, 0), PREMUL_STORED_COLOR);

        // value can now be stored/read correctly
        bitmap.setPixel(0, 0, PREMUL_COLOR);
        assertEquals(bitmap.getPixel(0, 0), PREMUL_COLOR);

        // verify with array methods
        int testArray[] = new int[] { PREMUL_COLOR };
        bitmap.setPixels(testArray, 0, 1, 0, 0, 1, 1);
        bitmap.getPixels(testArray, 0, 1, 0, 0, 1, 1);
        assertEquals(bitmap.getPixel(0, 0), PREMUL_COLOR);
    }

    @Test
    public void testPremultipliedCanvas() {
        Bitmap bitmap = Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888);
        bitmap.setHasAlpha(true);
        bitmap.setPremultiplied(false);
        assertFalse(bitmap.isPremultiplied());

        Canvas c = new Canvas();
        try {
            c.drawBitmap(bitmap, 0, 0, null);
            fail("canvas should fail with exception");
        } catch (RuntimeException e) {
        }
    }

    private int getBitmapRawInt(Bitmap bitmap) {
        IntBuffer buffer = IntBuffer.allocate(1);
        bitmap.copyPixelsToBuffer(buffer);
        return buffer.get(0);
    }

    private void bitmapStoreRawInt(Bitmap bitmap, int value) {
        IntBuffer buffer = IntBuffer.allocate(1);
        buffer.put(0, value);
        bitmap.copyPixelsFromBuffer(buffer);
    }

    @Test
    public void testSetPremultipliedToBuffer() {
        Bitmap bitmap = Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888);
        bitmap.setPixel(0, 0, PREMUL_COLOR);
        int storedPremul = getBitmapRawInt(bitmap);

        bitmap = Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888);
        bitmap.setPremultiplied(false);
        bitmap.setPixel(0, 0, PREMUL_STORED_COLOR);

        assertEquals(getBitmapRawInt(bitmap), storedPremul);
    }

    @Test
    public void testSetPremultipliedFromBuffer() {
        Bitmap bitmap = Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888);
        bitmap.setPremultiplied(false);
        bitmap.setPixel(0, 0, PREMUL_COLOR);
        int rawTestColor = getBitmapRawInt(bitmap);

        bitmap = Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888);
        bitmap.setPremultiplied(false);
        bitmapStoreRawInt(bitmap, rawTestColor);
        assertEquals(bitmap.getPixel(0, 0), PREMUL_COLOR);
    }

    @Test
    public void testSetWidth() {
        mBitmap = Bitmap.createBitmap(100, 200, Bitmap.Config.ARGB_8888);
        int alloc = mBitmap.getAllocationByteCount();

        // test shrinking
        mBitmap.setWidth(50);
        assertEquals(mBitmap.getAllocationByteCount(), alloc);
        assertEquals(mBitmap.getByteCount() * 2, alloc);
    }

    @Test(expected=IllegalArgumentException.class)
    public void testSetWidthExpanding() {
        // test expanding
        mBitmap = Bitmap.createBitmap(100, 200, Bitmap.Config.ARGB_8888);

        mBitmap.setWidth(101);
    }

    @Test(expected=IllegalStateException.class)
    public void testSetWidthMutable() {
        // test mutable
        mBitmap = BitmapFactory.decodeResource(mRes, R.drawable.start, mOptions);

        mBitmap.setWidth(1);
    }

    @Test(expected=IllegalStateException.class)
    public void testWriteToParcelRecycled() {
        mBitmap.recycle();

        mBitmap.writeToParcel(null, 0);
    }

    @Test
    public void testWriteToParcel() {
        // abnormal case: failed to unparcel Bitmap
        mBitmap = BitmapFactory.decodeResource(mRes, R.drawable.start, mOptions);
        Parcel p = Parcel.obtain();
        mBitmap.writeToParcel(p, 0);

        try {
            Bitmap.CREATOR.createFromParcel(p);
            fail("shouldn't come to here");
        } catch(RuntimeException e){
        }

        // normal case
        p = Parcel.obtain();
        mBitmap = Bitmap.createBitmap(100, 100, Config.ARGB_8888);
        mBitmap.writeToParcel(p, 0);
        p.setDataPosition(0);
        assertTrue(mBitmap.sameAs(Bitmap.CREATOR.createFromParcel(p)));
    }

    @Test
    public void testWriteHwBitmapToParcel() {
        mBitmap = BitmapFactory.decodeResource(mRes, R.drawable.robot, HARDWARE_OPTIONS);
        Parcel p = Parcel.obtain();
        mBitmap.writeToParcel(p, 0);
        p.setDataPosition(0);
        Bitmap expectedBitmap = BitmapFactory.decodeResource(mRes, R.drawable.robot);
        assertTrue(expectedBitmap.sameAs(Bitmap.CREATOR.createFromParcel(p)));
    }

    @Test
    public void testGetScaledHeight1() {
        int dummyDensity = 5;
        Bitmap ret = Bitmap.createBitmap(100, 200, Config.RGB_565);
        int scaledHeight = scaleFromDensity(ret.getHeight(), ret.getDensity(), dummyDensity);
        assertNotNull(ret);
        assertEquals(scaledHeight, ret.getScaledHeight(dummyDensity));
    }

    @Test
    public void testGetScaledHeight2() {
        Bitmap ret = Bitmap.createBitmap(100, 200, Config.RGB_565);
        DisplayMetrics metrics =
                InstrumentationRegistry.getTargetContext().getResources().getDisplayMetrics();
        int scaledHeight = scaleFromDensity(ret.getHeight(), ret.getDensity(), metrics.densityDpi);
        assertEquals(scaledHeight, ret.getScaledHeight(metrics));
    }

    @Test
    public void testGetScaledHeight3() {
        Bitmap ret = Bitmap.createBitmap(100, 200, Config.RGB_565);
        Bitmap mMutableBitmap = Bitmap.createBitmap(100, 200, Config.ARGB_8888);
        Canvas mCanvas = new Canvas(mMutableBitmap);
        // set Density
        mCanvas.setDensity(DisplayMetrics.DENSITY_HIGH);
        int scaledHeight = scaleFromDensity(
                ret.getHeight(), ret.getDensity(), mCanvas.getDensity());
        assertEquals(scaledHeight, ret.getScaledHeight(mCanvas));
    }

    @Test
    public void testGetScaledWidth1() {
        int dummyDensity = 5;
        Bitmap ret = Bitmap.createBitmap(100, 200, Config.RGB_565);
        int scaledWidth = scaleFromDensity(ret.getWidth(), ret.getDensity(), dummyDensity);
        assertNotNull(ret);
        assertEquals(scaledWidth, ret.getScaledWidth(dummyDensity));
    }

    @Test
    public void testGetScaledWidth2() {
        Bitmap ret = Bitmap.createBitmap(100, 200, Config.RGB_565);
        DisplayMetrics metrics =
                InstrumentationRegistry.getTargetContext().getResources().getDisplayMetrics();
        int scaledWidth = scaleFromDensity(ret.getWidth(), ret.getDensity(), metrics.densityDpi);
        assertEquals(scaledWidth, ret.getScaledWidth(metrics));
    }

    @Test
    public void testGetScaledWidth3() {
        Bitmap ret = Bitmap.createBitmap(100, 200, Config.RGB_565);
        Bitmap mMutableBitmap = Bitmap.createBitmap(100, 200, Config.ARGB_8888);
        Canvas mCanvas = new Canvas(mMutableBitmap);
        // set Density
        mCanvas.setDensity(DisplayMetrics.DENSITY_HIGH);
        int scaledWidth = scaleFromDensity(ret.getWidth(), ret.getDensity(),  mCanvas.getDensity());
        assertEquals(scaledWidth, ret.getScaledWidth(mCanvas));
    }

    @Test
    public void testSameAs_simpleSuccess() {
        Bitmap bitmap1 = Bitmap.createBitmap(100, 100, Config.ARGB_8888);
        Bitmap bitmap2 = Bitmap.createBitmap(100, 100, Config.ARGB_8888);
        bitmap1.eraseColor(Color.BLACK);
        bitmap2.eraseColor(Color.BLACK);
        assertTrue(bitmap1.sameAs(bitmap2));
        assertTrue(bitmap2.sameAs(bitmap1));
    }

    @Test
    public void testSameAs_simpleFail() {
        Bitmap bitmap1 = Bitmap.createBitmap(100, 100, Config.ARGB_8888);
        Bitmap bitmap2 = Bitmap.createBitmap(100, 100, Config.ARGB_8888);
        bitmap1.eraseColor(Color.BLACK);
        bitmap2.eraseColor(Color.BLACK);
        bitmap2.setPixel(20, 10, Color.WHITE);
        assertFalse(bitmap1.sameAs(bitmap2));
        assertFalse(bitmap2.sameAs(bitmap1));
    }

    @Test
    public void testSameAs_reconfigure() {
        Bitmap bitmap1 = Bitmap.createBitmap(100, 100, Config.ARGB_8888);
        Bitmap bitmap2 = Bitmap.createBitmap(150, 150, Config.ARGB_8888);
        bitmap2.reconfigure(100, 100, Config.ARGB_8888); // now same size, so should be same
        bitmap1.eraseColor(Color.BLACK);
        bitmap2.eraseColor(Color.BLACK);
        assertTrue(bitmap1.sameAs(bitmap2));
        assertTrue(bitmap2.sameAs(bitmap1));
    }

    @Test
    public void testSameAs_config() {
        Bitmap bitmap1 = Bitmap.createBitmap(100, 200, Config.RGB_565);
        Bitmap bitmap2 = Bitmap.createBitmap(100, 200, Config.ARGB_8888);

        // both bitmaps can represent black perfectly
        bitmap1.eraseColor(Color.BLACK);
        bitmap2.eraseColor(Color.BLACK);

        // but not same due to config
        assertFalse(bitmap1.sameAs(bitmap2));
        assertFalse(bitmap2.sameAs(bitmap1));
    }

    @Test
    public void testSameAs_width() {
        Bitmap bitmap1 = Bitmap.createBitmap(100, 100, Config.ARGB_8888);
        Bitmap bitmap2 = Bitmap.createBitmap(101, 100, Config.ARGB_8888);
        bitmap1.eraseColor(Color.BLACK);
        bitmap2.eraseColor(Color.BLACK);
        assertFalse(bitmap1.sameAs(bitmap2));
        assertFalse(bitmap2.sameAs(bitmap1));
    }

    @Test
    public void testSameAs_height() {
        Bitmap bitmap1 = Bitmap.createBitmap(100, 100, Config.ARGB_8888);
        Bitmap bitmap2 = Bitmap.createBitmap(102, 100, Config.ARGB_8888);
        bitmap1.eraseColor(Color.BLACK);
        bitmap2.eraseColor(Color.BLACK);
        assertFalse(bitmap1.sameAs(bitmap2));
        assertFalse(bitmap2.sameAs(bitmap1));
    }

    @Test
    public void testSameAs_opaque() {
        Bitmap bitmap1 = Bitmap.createBitmap(100, 100, Config.ARGB_8888);
        Bitmap bitmap2 = Bitmap.createBitmap(100, 100, Config.ARGB_8888);
        bitmap1.eraseColor(Color.BLACK);
        bitmap2.eraseColor(Color.BLACK);
        bitmap1.setHasAlpha(true);
        bitmap2.setHasAlpha(false);
        assertFalse(bitmap1.sameAs(bitmap2));
        assertFalse(bitmap2.sameAs(bitmap1));
    }

    @Test
    public void testSameAs_hardware() {
        Bitmap bitmap1 = BitmapFactory.decodeResource(mRes, R.drawable.robot, HARDWARE_OPTIONS);
        Bitmap bitmap2 = BitmapFactory.decodeResource(mRes, R.drawable.robot, HARDWARE_OPTIONS);
        Bitmap bitmap3 = BitmapFactory.decodeResource(mRes, R.drawable.robot);
        Bitmap bitmap4 = BitmapFactory.decodeResource(mRes, R.drawable.start, HARDWARE_OPTIONS);
        assertTrue(bitmap1.sameAs(bitmap2));
        assertTrue(bitmap2.sameAs(bitmap1));
        assertFalse(bitmap1.sameAs(bitmap3));
        assertFalse(bitmap1.sameAs(bitmap4));
    }

    @Test(expected=IllegalStateException.class)
    public void testHardwareGetPixel() {
        Bitmap bitmap = BitmapFactory.decodeResource(mRes, R.drawable.robot, HARDWARE_OPTIONS);
        bitmap.getPixel(0, 0);
    }

    @Test(expected=IllegalStateException.class)
    public void testHardwareGetPixels() {
        Bitmap bitmap = BitmapFactory.decodeResource(mRes, R.drawable.robot, HARDWARE_OPTIONS);
        bitmap.getPixels(new int[5], 0, 5, 0, 0, 5, 1);
    }

    @Test
    public void testGetConfigOnRecycled() {
        Bitmap bitmap1 = BitmapFactory.decodeResource(mRes, R.drawable.robot, HARDWARE_OPTIONS);
        bitmap1.recycle();
        assertEquals(Config.HARDWARE, bitmap1.getConfig());
        Bitmap bitmap2 = Bitmap.createBitmap(100, 100, Config.ARGB_8888);
        bitmap2.recycle();
        assertEquals(Config.ARGB_8888, bitmap2.getConfig());
    }

    @Test(expected = IllegalStateException.class)
    public void testHardwareSetWidth() {
        Bitmap bitmap = BitmapFactory.decodeResource(mRes, R.drawable.robot, HARDWARE_OPTIONS);
        bitmap.setWidth(30);
    }

    @Test(expected = IllegalStateException.class)
    public void testHardwareSetHeight() {
        Bitmap bitmap = BitmapFactory.decodeResource(mRes, R.drawable.robot, HARDWARE_OPTIONS);
        bitmap.setHeight(30);
    }

    @Test(expected = IllegalStateException.class)
    public void testHardwareSetConfig() {
        Bitmap bitmap = BitmapFactory.decodeResource(mRes, R.drawable.robot, HARDWARE_OPTIONS);
        bitmap.setConfig(Config.ARGB_8888);
    }

    @Test(expected = IllegalStateException.class)
    public void testHardwareReconfigure() {
        Bitmap bitmap = BitmapFactory.decodeResource(mRes, R.drawable.robot, HARDWARE_OPTIONS);
        bitmap.reconfigure(30, 30, Config.ARGB_8888);
    }

    @Test(expected = IllegalStateException.class)
    public void testHardwareSetPixels() {
        Bitmap bitmap = BitmapFactory.decodeResource(mRes, R.drawable.robot, HARDWARE_OPTIONS);
        bitmap.setPixels(new int[10], 0, 1, 0, 0, 1, 1);
    }

    @Test(expected = IllegalStateException.class)
    public void testHardwareSetPixel() {
        Bitmap bitmap = BitmapFactory.decodeResource(mRes, R.drawable.robot, HARDWARE_OPTIONS);
        bitmap.setPixel(1, 1, 0);
    }

    @Test(expected = IllegalStateException.class)
    public void testHardwareEraseColor() {
        Bitmap bitmap = BitmapFactory.decodeResource(mRes, R.drawable.robot, HARDWARE_OPTIONS);
        bitmap.eraseColor(0);
    }

    @Test(expected = IllegalStateException.class)
    public void testHardwareCopyPixelsToBuffer() {
        Bitmap bitmap = BitmapFactory.decodeResource(mRes, R.drawable.start, HARDWARE_OPTIONS);
        ByteBuffer byteBuf = ByteBuffer.allocate(bitmap.getRowBytes() * bitmap.getHeight());
        bitmap.copyPixelsToBuffer(byteBuf);
    }

    @Test(expected = IllegalStateException.class)
    public void testHardwareCopyPixelsFromBuffer() {
        IntBuffer intBuf1 = IntBuffer.allocate(mBitmap.getRowBytes() * mBitmap.getHeight());
        assertEquals(0, intBuf1.position());
        mBitmap.copyPixelsToBuffer(intBuf1);
        Bitmap hwBitmap = BitmapFactory.decodeResource(mRes, R.drawable.start, HARDWARE_OPTIONS);
        hwBitmap.copyPixelsFromBuffer(intBuf1);
    }

    @Test
    public void testUseMetadataAfterRecycle() {
        Bitmap bitmap = Bitmap.createBitmap(10, 20, Config.RGB_565);
        bitmap.recycle();
        assertEquals(10, bitmap.getWidth());
        assertEquals(20, bitmap.getHeight());
        assertEquals(Config.RGB_565, bitmap.getConfig());
    }

    @Test
    public void testCopyHWBitmapInStrictMode() {
        strictModeTest(()->{
            Bitmap bitmap = Bitmap.createBitmap(100, 100, Config.ARGB_8888);
            Bitmap hwBitmap = bitmap.copy(Config.HARDWARE, false);
            hwBitmap.copy(Config.ARGB_8888, false);
        });
    }

    @Test
    public void testCreateScaledFromHWInStrictMode() {
        strictModeTest(()->{
            Bitmap bitmap = Bitmap.createBitmap(100, 100, Config.ARGB_8888);
            Bitmap hwBitmap = bitmap.copy(Config.HARDWARE, false);
            Bitmap.createScaledBitmap(hwBitmap, 200, 200, false);
        });
    }

    @Test
    public void testExtractAlphaFromHWInStrictMode() {
        strictModeTest(()->{
            Bitmap bitmap = Bitmap.createBitmap(100, 100, Config.ARGB_8888);
            Bitmap hwBitmap = bitmap.copy(Config.HARDWARE, false);
            hwBitmap.extractAlpha();
        });
    }

    @Test
    public void testCompressInStrictMode() {
        strictModeTest(()->{
            Bitmap bitmap = Bitmap.createBitmap(100, 100, Config.ARGB_8888);
            bitmap.compress(CompressFormat.JPEG, 90, new ByteArrayOutputStream());
        });
    }

    @Test
    public void testParcelHWInStrictMode() {
        strictModeTest(()->{
            mBitmap = Bitmap.createBitmap(100, 100, Config.ARGB_8888);
            Bitmap hwBitmap = mBitmap.copy(Config.HARDWARE, false);
            hwBitmap.writeToParcel(Parcel.obtain(), 0);
        });
    }

    @Test
    public void testSameAsFirstHWInStrictMode() {
        strictModeTest(()->{
            Bitmap bitmap = Bitmap.createBitmap(100, 100, Config.ARGB_8888);
            Bitmap hwBitmap = bitmap.copy(Config.HARDWARE, false);
            hwBitmap.sameAs(bitmap);
        });
    }

    @Test
    public void testSameAsSecondHWInStrictMode() {
        strictModeTest(()->{
            Bitmap bitmap = Bitmap.createBitmap(100, 100, Config.ARGB_8888);
            Bitmap hwBitmap = bitmap.copy(Config.HARDWARE, false);
            bitmap.sameAs(hwBitmap);
        });
    }

    @Test
    public void testNdkAccessAfterRecycle() {
        Bitmap bitmap = Bitmap.createBitmap(10, 20, Config.RGB_565);
        nValidateBitmapInfo(bitmap, 10, 20, true);
        bitmap.recycle();
        nValidateBitmapInfo(bitmap, 10, 20, true);
        nValidateNdkAccessAfterRecycle(bitmap);
    }

    private void runGcAndFinalizersSync() {
        final CountDownLatch fence = new CountDownLatch(1);
        new Object() {
            @Override
            protected void finalize() throws Throwable {
                try {
                    fence.countDown();
                } finally {
                    super.finalize();
                }
            }
        };
        try {
            do {
                Runtime.getRuntime().gc();
                Runtime.getRuntime().runFinalization();
            } while (!fence.await(100, TimeUnit.MILLISECONDS));
        } catch (InterruptedException ex) {
            throw new RuntimeException(ex);
        }
        Runtime.getRuntime().gc();
    }

    private void assertNotLeaking(int iteration, Debug.MemoryInfo start, Debug.MemoryInfo end) {
        Debug.getMemoryInfo(end);
        if (end.getTotalPss() - start.getTotalPss() > 2000 /* kB */) {
            runGcAndFinalizersSync();
            Debug.getMemoryInfo(end);
            if (end.getTotalPss() - start.getTotalPss() > 2000 /* kB */) {
                // Guarded by if so we don't continually generate garbage for the
                // assertion string.
                assertEquals("Memory leaked, iteration=" + iteration,
                        start.getTotalPss(), end.getTotalPss(),
                        2000 /* kb */);
            }
        }
    }

    @Test
    @LargeTest
    public void testHardwareBitmapNotLeaking() {
        Debug.MemoryInfo meminfoStart = new Debug.MemoryInfo();
        Debug.MemoryInfo meminfoEnd = new Debug.MemoryInfo();
        BitmapFactory.Options opts = new BitmapFactory.Options();
        opts.inPreferredConfig = Config.HARDWARE;
        opts.inScaled = false;

        for (int i = 0; i < 2000; i++) {
            if (i == 2) {
                // Not really the "start" but by having done a couple
                // we've fully initialized any state that may be required,
                // so memory usage should be stable now
                runGcAndFinalizersSync();
                Debug.getMemoryInfo(meminfoStart);
            }
            if (i % 100 == 5) {
                assertNotLeaking(i, meminfoStart, meminfoEnd);
            }
            Bitmap bitmap = BitmapFactory.decodeResource(mRes, R.drawable.robot, opts);
            assertNotNull(bitmap);
            // Make sure nothing messed with the bitmap
            assertEquals(128, bitmap.getWidth());
            assertEquals(128, bitmap.getHeight());
            assertEquals(Config.HARDWARE, bitmap.getConfig());
            bitmap.recycle();
        }

        assertNotLeaking(2000, meminfoStart, meminfoEnd);
    }

    @Test
    @LargeTest
    public void testDrawingHardwareBitmapNotLeaking() {
        Debug.MemoryInfo meminfoStart = new Debug.MemoryInfo();
        Debug.MemoryInfo meminfoEnd = new Debug.MemoryInfo();
        BitmapFactory.Options opts = new BitmapFactory.Options();
        opts.inPreferredConfig = Config.HARDWARE;
        opts.inScaled = false;
        RenderTarget renderTarget = RenderTarget.create();
        renderTarget.setDefaultSize(128, 128);
        final Surface surface = renderTarget.getSurface();

        for (int i = 0; i < 2000; i++) {
            if (i == 2) {
                // Not really the "start" but by having done a couple
                // we've fully initialized any state that may be required,
                // so memory usage should be stable now
                runGcAndFinalizersSync();
                Debug.getMemoryInfo(meminfoStart);
            }
            if (i % 100 == 5) {
                assertNotLeaking(i, meminfoStart, meminfoEnd);
            }
            Bitmap bitmap = BitmapFactory.decodeResource(mRes, R.drawable.robot, opts);
            assertNotNull(bitmap);
            // Make sure nothing messed with the bitmap
            assertEquals(128, bitmap.getWidth());
            assertEquals(128, bitmap.getHeight());
            assertEquals(Config.HARDWARE, bitmap.getConfig());
            Canvas canvas = surface.lockHardwareCanvas();
            canvas.drawBitmap(bitmap, 0, 0, null);
            surface.unlockCanvasAndPost(canvas);
            bitmap.recycle();
        }

        assertNotLeaking(2000, meminfoStart, meminfoEnd);
    }

    private void strictModeTest(Runnable runnable) {
        StrictMode.ThreadPolicy originalPolicy = StrictMode.getThreadPolicy();
        StrictMode.setThreadPolicy(new StrictMode.ThreadPolicy.Builder()
                .detectCustomSlowCalls().penaltyDeath().build());
        try {
            runnable.run();
            fail("Shouldn't reach it");
        } catch (RuntimeException expected){
            // expect to receive StrictModeViolation
        } finally {
            StrictMode.setThreadPolicy(originalPolicy);
        }
    }

    private static native void nValidateBitmapInfo(Bitmap bitmap, int width, int height,
            boolean is565);
    private static native void nValidateNdkAccessAfterRecycle(Bitmap bitmap);

    private static int scaleFromDensity(int size, int sdensity, int tdensity) {
        if (sdensity == Bitmap.DENSITY_NONE || sdensity == tdensity) {
            return size;
        }

        // Scale by tdensity / sdensity, rounding up.
        return ((size * tdensity) + (sdensity >> 1)) / sdensity;
    }

    private static int[] createColors(int size) {
        int[] colors = new int[size];

        for (int i = 0; i < size; i++) {
            colors[i] = (0xFF << 24) | (i << 16) | (i << 8) | i;
        }

        return colors;
    }

    private static BitmapFactory.Options createHardwareBitmapOptions() {
        BitmapFactory.Options options = new BitmapFactory.Options();
        options.inPreferredConfig = Config.HARDWARE;
        return options;
    }
}
