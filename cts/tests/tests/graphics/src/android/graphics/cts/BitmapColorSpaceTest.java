/*
 * Copyright (C) 2017 The Android Open Source Project
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
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.ColorSpace;
import android.os.Parcel;
import android.support.annotation.ColorInt;
import android.support.annotation.NonNull;
import android.support.test.InstrumentationRegistry;
import android.support.test.filters.SmallTest;
import android.support.test.runner.AndroidJUnit4;
import android.util.Log;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.IntBuffer;
import java.util.Arrays;

@SmallTest
@RunWith(AndroidJUnit4.class)
public class BitmapColorSpaceTest {
    private static final String LOG_TAG = "BitmapColorSpaceTest";

    private Resources mResources;

    @Before
    public void setup() {
        mResources = InstrumentationRegistry.getTargetContext().getResources();
    }

    @SuppressWarnings("deprecation")
    @Test
    public void createWithColorSpace() {
        Bitmap b;
        ColorSpace cs;
        ColorSpace sRGB = ColorSpace.get(ColorSpace.Named.SRGB);

        // We don't test HARDWARE configs because they are not compatible with mutable bitmaps

        b = Bitmap.createBitmap(32, 32, Bitmap.Config.ARGB_8888, true, sRGB);
        cs = b.getColorSpace();
        assertNotNull(cs);
        assertSame(sRGB, cs);

        b = Bitmap.createBitmap(32, 32, Bitmap.Config.ARGB_8888, true,
                ColorSpace.get(ColorSpace.Named.ADOBE_RGB));
        cs = b.getColorSpace();
        assertNotNull(cs);
        assertSame(ColorSpace.get(ColorSpace.Named.ADOBE_RGB), cs);

        b = Bitmap.createBitmap(32, 32, Bitmap.Config.RGBA_F16, true, sRGB);
        cs = b.getColorSpace();
        assertNotNull(cs);
        assertSame(ColorSpace.get(ColorSpace.Named.LINEAR_EXTENDED_SRGB), cs);

        b = Bitmap.createBitmap(32, 32, Bitmap.Config.RGBA_F16, true,
                ColorSpace.get(ColorSpace.Named.ADOBE_RGB));
        cs = b.getColorSpace();
        assertNotNull(cs);
        assertSame(ColorSpace.get(ColorSpace.Named.LINEAR_EXTENDED_SRGB), cs);

        b = Bitmap.createBitmap(32, 32, Bitmap.Config.RGB_565, true, sRGB);
        cs = b.getColorSpace();
        assertNotNull(cs);
        assertSame(sRGB, cs);

        b = Bitmap.createBitmap(32, 32, Bitmap.Config.RGB_565, true,
                ColorSpace.get(ColorSpace.Named.ADOBE_RGB));
        cs = b.getColorSpace();
        assertNotNull(cs);
        assertSame(sRGB, cs);

        b = Bitmap.createBitmap(32, 32, Bitmap.Config.ALPHA_8, true, sRGB);
        cs = b.getColorSpace();
        assertNotNull(cs);
        assertSame(sRGB, cs);

        b = Bitmap.createBitmap(32, 32, Bitmap.Config.ALPHA_8, true,
                ColorSpace.get(ColorSpace.Named.ADOBE_RGB));
        cs = b.getColorSpace();
        assertNotNull(cs);
        assertSame(sRGB, cs);

        b = Bitmap.createBitmap(32, 32, Bitmap.Config.ARGB_4444, true, sRGB);
        cs = b.getColorSpace();
        assertNotNull(cs);
        assertSame(sRGB, cs);

        b = Bitmap.createBitmap(32, 32, Bitmap.Config.ARGB_4444, true,
                ColorSpace.get(ColorSpace.Named.ADOBE_RGB));
        cs = b.getColorSpace();
        assertNotNull(cs);
        assertSame(sRGB, cs);
    }

    @Test
    public void createDefaultColorSpace() {
        ColorSpace sRGB = ColorSpace.get(ColorSpace.Named.SRGB);
        Bitmap.Config[] configs = new Bitmap.Config[] {
                Bitmap.Config.ALPHA_8, Bitmap.Config.RGB_565, Bitmap.Config.ARGB_8888
        };
        for (Bitmap.Config config : configs) {
            Bitmap bitmap = Bitmap.createBitmap(32, 32, config, true);
            assertSame(sRGB, bitmap.getColorSpace());
        }
    }

    @Test(expected = IllegalArgumentException.class)
    public void createWithoutColorSpace() {
        Bitmap.createBitmap(32, 32, Bitmap.Config.ARGB_8888, true, null);
    }

    @Test(expected = IllegalArgumentException.class)
    public void createWithNonRgbColorSpace() {
        Bitmap.createBitmap(32, 32, Bitmap.Config.ARGB_8888, true,
                ColorSpace.get(ColorSpace.Named.CIE_LAB));
    }

    @Test
    public void sRGB() {
        Bitmap b = BitmapFactory.decodeResource(mResources, R.drawable.robot);
        ColorSpace cs = b.getColorSpace();
        assertNotNull(cs);
        assertSame(ColorSpace.get(ColorSpace.Named.SRGB), cs);

        b = Bitmap.createBitmap(b, 0, 0, b.getWidth() / 2, b.getHeight() / 2);
        cs = b.getColorSpace();
        assertNotNull(cs);
        assertSame(ColorSpace.get(ColorSpace.Named.SRGB), cs);

        b = Bitmap.createScaledBitmap(b, b.getWidth() / 2, b.getHeight() / 2, true);
        cs = b.getColorSpace();
        assertNotNull(cs);
        assertSame(ColorSpace.get(ColorSpace.Named.SRGB), cs);
    }

    @Test
    public void p3() {
        try (InputStream in = mResources.getAssets().open("green-p3.png")) {
            Bitmap b = BitmapFactory.decodeStream(in);
            ColorSpace cs = b.getColorSpace();
            assertNotNull(cs);
            assertSame(ColorSpace.get(ColorSpace.Named.DISPLAY_P3), cs);

            b = Bitmap.createBitmap(b, 0, 0, b.getWidth() / 2, b.getHeight() / 2);
            cs = b.getColorSpace();
            assertNotNull(cs);
            assertSame(ColorSpace.get(ColorSpace.Named.DISPLAY_P3), cs);

            b = Bitmap.createScaledBitmap(b, b.getWidth() / 2, b.getHeight() / 2, true);
            cs = b.getColorSpace();
            assertNotNull(cs);
            assertSame(ColorSpace.get(ColorSpace.Named.DISPLAY_P3), cs);
        } catch (IOException e) {
            fail();
        }
    }

    @Test
    public void extendedSRGB() {
        try (InputStream in = mResources.getAssets().open("prophoto-rgba16f.png")) {
            Bitmap b = BitmapFactory.decodeStream(in);
            ColorSpace cs = b.getColorSpace();
            assertNotNull(cs);
            assertSame(ColorSpace.get(ColorSpace.Named.LINEAR_EXTENDED_SRGB), cs);

            b = Bitmap.createBitmap(b, 0, 0, b.getWidth() / 2, b.getHeight() / 2);
            cs = b.getColorSpace();
            assertNotNull(cs);
            assertSame(ColorSpace.get(ColorSpace.Named.LINEAR_EXTENDED_SRGB), cs);

            b = Bitmap.createScaledBitmap(b, b.getWidth() / 2, b.getHeight() / 2, true);
            cs = b.getColorSpace();
            assertNotNull(cs);
            assertSame(ColorSpace.get(ColorSpace.Named.LINEAR_EXTENDED_SRGB), cs);
        } catch (IOException e) {
            fail();
        }
    }

    @Test
    public void reconfigure() {
        try (InputStream in = mResources.getAssets().open("green-p3.png")) {
            BitmapFactory.Options opts = new BitmapFactory.Options();
            opts.inMutable = true;

            Bitmap b = BitmapFactory.decodeStream(in, null, opts);
            ColorSpace cs = b.getColorSpace();
            assertNotNull(cs);
            assertSame(ColorSpace.get(ColorSpace.Named.DISPLAY_P3), cs);

            b.reconfigure(b.getWidth() / 2, b.getHeight() / 2, Bitmap.Config.RGBA_F16);
            cs = b.getColorSpace();
            assertNotNull(cs);
            assertSame(ColorSpace.get(ColorSpace.Named.LINEAR_EXTENDED_SRGB), cs);

            b.reconfigure(b.getWidth(), b.getHeight(), Bitmap.Config.ARGB_8888);
            cs = b.getColorSpace();
            assertNotNull(cs);
            assertSame(ColorSpace.get(ColorSpace.Named.DISPLAY_P3), cs);
        } catch (IOException e) {
            fail();
        }
    }

    @Test
    public void reuse() {
        BitmapFactory.Options opts = new BitmapFactory.Options();
        opts.inMutable = true;

        Bitmap bitmap1 = null;
        try (InputStream in = mResources.getAssets().open("green-srgb.png")) {
            bitmap1 = BitmapFactory.decodeStream(in, null, opts);
            ColorSpace cs = bitmap1.getColorSpace();
            assertNotNull(cs);
            assertSame(ColorSpace.get(ColorSpace.Named.SRGB), cs);
        } catch (IOException e) {
            fail();
        }

        try (InputStream in = mResources.getAssets().open("green-p3.png")) {
            opts.inBitmap = bitmap1;

            Bitmap bitmap2 = BitmapFactory.decodeStream(in, null, opts);
            assertSame(bitmap1, bitmap2);
            ColorSpace cs = bitmap2.getColorSpace();
            assertNotNull(cs);
            assertSame(ColorSpace.get(ColorSpace.Named.DISPLAY_P3), cs);
        } catch (IOException e) {
            fail();
        }
    }

    @Test
    public void getPixel() {
        verifyGetPixel("green-p3.png", 0x75fb4cff, 0xff00ff00);
        verifyGetPixel("translucent-green-p3.png", 0x3a7d267f, 0x7f00ff00); // 50% translucent
    }

    private void verifyGetPixel(@NonNull String fileName,
            @ColorInt int rawColor, @ColorInt int srgbColor) {
        try (InputStream in = mResources.getAssets().open(fileName)) {
            Bitmap b = BitmapFactory.decodeStream(in);
            ColorSpace cs = b.getColorSpace();
            assertNotNull(cs);
            assertSame(ColorSpace.get(ColorSpace.Named.DISPLAY_P3), cs);

            verifyGetPixel(b, rawColor, srgbColor);

            b = Bitmap.createBitmap(b, 0, 0, b.getWidth() / 2, b.getHeight() / 2);
            verifyGetPixel(b, rawColor, srgbColor);

            b = Bitmap.createScaledBitmap(b, b.getWidth() / 2, b.getHeight() / 2, true);
            verifyGetPixel(b, rawColor, srgbColor);
        } catch (IOException e) {
            fail();
        }
    }

    private static void verifyGetPixel(@NonNull Bitmap b,
            @ColorInt int rawColor, @ColorInt int srgbColor) {
        ByteBuffer dst = ByteBuffer.allocate(b.getByteCount());
        b.copyPixelsToBuffer(dst);
        dst.rewind();

        // Stored as RGBA
        assertEquals(rawColor, dst.asIntBuffer().get());

        int srgb = b.getPixel(15, 15);
        almostEqual(srgbColor, srgb, 3, 15 * b.getWidth() + 15);
    }

    @Test
    public void getPixels() {
        verifyGetPixels("green-p3.png", 0xff00ff00);
        verifyGetPixels("translucent-green-p3.png", 0x7f00ff00); // 50% translucent
    }

    private void verifyGetPixels(@NonNull String fileName, @ColorInt int expected) {
        try (InputStream in = mResources.getAssets().open(fileName)) {
            Bitmap b = BitmapFactory.decodeStream(in);
            ColorSpace cs = b.getColorSpace();
            assertNotNull(cs);
            assertSame(ColorSpace.get(ColorSpace.Named.DISPLAY_P3), cs);

            verifyGetPixels(b, expected);

            b = Bitmap.createBitmap(b, 0, 0, b.getWidth() / 2, b.getHeight() / 2);
            verifyGetPixels(b, expected);

            b = Bitmap.createScaledBitmap(b, b.getWidth() / 2, b.getHeight() / 2, true);
            verifyGetPixels(b, expected);
        } catch (IOException e) {
            fail();
        }
    }

    private static void verifyGetPixels(@NonNull Bitmap b, @ColorInt int expected) {
        int[] pixels = new int[b.getWidth() * b.getHeight()];
        b.getPixels(pixels, 0, b.getWidth(), 0, 0, b.getWidth(), b.getHeight());

        for (int i = 0; i < pixels.length; i++) {
            int pixel = pixels[i];
            almostEqual(expected, pixel, 3, i);
        }
    }

    @Test
    public void setPixel() {
        verifySetPixel("green-p3.png", 0xffff0000, 0xea3323ff);
        verifySetPixel("translucent-green-p3.png", 0x7fff0000, 0x7519117f);
    }

    private void verifySetPixel(@NonNull String fileName,
            @ColorInt int newColor, @ColorInt int expectedColor) {
        try (InputStream in = mResources.getAssets().open(fileName)) {
            BitmapFactory.Options opts = new BitmapFactory.Options();
            opts.inMutable = true;

            Bitmap b = BitmapFactory.decodeStream(in, null, opts);
            ColorSpace cs = b.getColorSpace();
            assertNotNull(cs);
            assertSame(ColorSpace.get(ColorSpace.Named.DISPLAY_P3), cs);

            verifySetPixel(b, newColor, expectedColor);

            b = Bitmap.createBitmap(b, 0, 0, b.getWidth() / 2, b.getHeight() / 2);
            verifySetPixel(b, newColor, expectedColor);

            b = Bitmap.createScaledBitmap(b, b.getWidth() / 2, b.getHeight() / 2, true);
            verifySetPixel(b, newColor, expectedColor);
        } catch (IOException e) {
            fail();
        }
    }

    private static void verifySetPixel(@NonNull Bitmap b,
            @ColorInt int newColor, @ColorInt int expectedColor) {
        b.setPixel(0, 0, newColor);

        ByteBuffer dst = ByteBuffer.allocate(b.getByteCount());
        b.copyPixelsToBuffer(dst);
        dst.rewind();
        // Stored as RGBA
        assertEquals(expectedColor, dst.asIntBuffer().get());
    }

    @Test
    public void setPixels() {
        verifySetPixels("green-p3.png", 0xffff0000, 0xea3323ff);
        verifySetPixels("translucent-green-p3.png", 0x7fff0000, 0x7519117f);
    }

    private void verifySetPixels(@NonNull String fileName,
            @ColorInt int newColor, @ColorInt int expectedColor) {
        try (InputStream in = mResources.getAssets().open(fileName)) {
            BitmapFactory.Options opts = new BitmapFactory.Options();
            opts.inMutable = true;

            Bitmap b = BitmapFactory.decodeStream(in, null, opts);
            ColorSpace cs = b.getColorSpace();
            assertNotNull(cs);
            assertSame(ColorSpace.get(ColorSpace.Named.DISPLAY_P3), cs);

            verifySetPixels(b, newColor, expectedColor);

            b = Bitmap.createBitmap(b, 0, 0, b.getWidth() / 2, b.getHeight() / 2);
            verifySetPixels(b, newColor, expectedColor);

            b = Bitmap.createScaledBitmap(b, b.getWidth() / 2, b.getHeight() / 2, true);
            verifySetPixels(b, newColor, expectedColor);
        } catch (IOException e) {
            fail();
        }
    }

    private static void verifySetPixels(@NonNull Bitmap b,
            @ColorInt int newColor, @ColorInt int expectedColor) {
        int[] pixels = new int[b.getWidth() * b.getHeight()];
        Arrays.fill(pixels, newColor);
        b.setPixels(pixels, 0, b.getWidth(), 0, 0, b.getWidth(), b.getHeight());

        ByteBuffer dst = ByteBuffer.allocate(b.getByteCount());
        b.copyPixelsToBuffer(dst);
        dst.rewind();

        IntBuffer buffer = dst.asIntBuffer();
        //noinspection ForLoopReplaceableByForEach
        for (int i = 0; i < pixels.length; i++) {
            // Stored as RGBA
            assertEquals(expectedColor, buffer.get());
        }
    }

    @Test
    public void writeColorSpace() {
        verifyColorSpaceMarshalling("green-srgb.png", ColorSpace.get(ColorSpace.Named.SRGB));
        verifyColorSpaceMarshalling("green-p3.png", ColorSpace.get(ColorSpace.Named.DISPLAY_P3));
        verifyColorSpaceMarshalling("prophoto-rgba16f.png",
                ColorSpace.get(ColorSpace.Named.LINEAR_EXTENDED_SRGB));

        // Special case where the color space will be null in native
        Bitmap bitmapIn = BitmapFactory.decodeResource(mResources, R.drawable.robot);
        verifyParcelUnparcel(bitmapIn, ColorSpace.get(ColorSpace.Named.SRGB));
    }

    private void verifyColorSpaceMarshalling(
            @NonNull String fileName, @NonNull ColorSpace colorSpace) {
        try (InputStream in = mResources.getAssets().open(fileName)) {
            Bitmap bitmapIn = BitmapFactory.decodeStream(in);
            verifyParcelUnparcel(bitmapIn, colorSpace);
        } catch (IOException e) {
            fail();
        }
    }

    private void verifyParcelUnparcel(Bitmap bitmapIn, ColorSpace expected) {
        ColorSpace cs = bitmapIn.getColorSpace();
        assertNotNull(cs);
        assertSame(expected, cs);

        Parcel p = Parcel.obtain();
        bitmapIn.writeToParcel(p, 0);
        p.setDataPosition(0);

        Bitmap bitmapOut = Bitmap.CREATOR.createFromParcel(p);
        cs = bitmapOut.getColorSpace();
        assertNotNull(cs);
        assertSame(expected, cs);

        p.recycle();
    }

    @Test
    public void p3rgb565() {
        BitmapFactory.Options opts = new BitmapFactory.Options();
        opts.inPreferredConfig = Bitmap.Config.RGB_565;

        try (InputStream in = mResources.getAssets().open("green-p3.png")) {
            Bitmap b = BitmapFactory.decodeStream(in, null, opts);
            ColorSpace cs = b.getColorSpace();
            assertNotNull(cs);
            assertSame(ColorSpace.get(ColorSpace.Named.SRGB), cs);
        } catch (IOException e) {
            fail();
        }
    }

    @Test
    public void p3hardware() {
        BitmapFactory.Options opts = new BitmapFactory.Options();
        opts.inPreferredConfig = Bitmap.Config.HARDWARE;

        try (InputStream in = mResources.getAssets().open("green-p3.png")) {
            Bitmap b = BitmapFactory.decodeStream(in, null, opts);
            ColorSpace cs = b.getColorSpace();
            assertNotNull(cs);
            assertSame(ColorSpace.get(ColorSpace.Named.DISPLAY_P3), cs);
        } catch (IOException e) {
            fail();
        }
    }

    @Test
    public void guessSRGB() {
        BitmapFactory.Options opts = new BitmapFactory.Options();
        opts.inJustDecodeBounds = true;

        try (InputStream in = mResources.getAssets().open("green-srgb.png")) {
            Bitmap b = BitmapFactory.decodeStream(in, null, opts);
            ColorSpace cs = opts.outColorSpace;
            assertNull(b);
            assertNotNull(cs);
            assertSame(ColorSpace.get(ColorSpace.Named.SRGB), cs);
        } catch (IOException e) {
            fail();
        }
    }

    @Test
    public void guessProPhotoRGB() {
        BitmapFactory.Options opts = new BitmapFactory.Options();
        opts.inJustDecodeBounds = true;

        try (InputStream in = mResources.getAssets().open("prophoto-rgba16f.png")) {
            Bitmap b = BitmapFactory.decodeStream(in, null, opts);
            ColorSpace cs = opts.outColorSpace;
            assertNull(b);
            assertNotNull(cs);
            assertSame(ColorSpace.get(ColorSpace.Named.LINEAR_EXTENDED_SRGB), cs);
        } catch (IOException e) {
            fail();
        }
    }

    @Test
    public void guessP3() {
        BitmapFactory.Options opts = new BitmapFactory.Options();
        opts.inJustDecodeBounds = true;

        try (InputStream in = mResources.getAssets().open("green-p3.png")) {
            Bitmap b = BitmapFactory.decodeStream(in, null, opts);
            ColorSpace cs = opts.outColorSpace;
            assertNull(b);
            assertNotNull(cs);
            assertSame(ColorSpace.get(ColorSpace.Named.DISPLAY_P3), cs);
        } catch (IOException e) {
            fail();
        }
    }

    @Test
    public void guessAdobeRGB() {
        BitmapFactory.Options opts = new BitmapFactory.Options();
        opts.inJustDecodeBounds = true;

        try (InputStream in = mResources.getAssets().open("red-adobergb.png")) {
            Bitmap b = BitmapFactory.decodeStream(in, null, opts);
            ColorSpace cs = opts.outColorSpace;
            assertNull(b);
            assertNotNull(cs);
            assertSame(ColorSpace.get(ColorSpace.Named.ADOBE_RGB), cs);
        } catch (IOException e) {
            fail();
        }
    }

    @Test
    public void guessUnknown() {
        BitmapFactory.Options opts = new BitmapFactory.Options();
        opts.inJustDecodeBounds = true;

        try (InputStream in = mResources.getAssets().open("purple-displayprofile.png")) {
            Bitmap b = BitmapFactory.decodeStream(in, null, opts);
            ColorSpace cs = opts.outColorSpace;
            assertNull(b);
            assertNotNull(cs);
            assertEquals("Unknown", cs.getName());
        } catch (IOException e) {
            fail();
        }
    }

    @Test
    public void guessCMYK() {
        BitmapFactory.Options opts = new BitmapFactory.Options();
        opts.inJustDecodeBounds = true;

        try (InputStream in = mResources.getAssets().open("purple-cmyk.png")) {
            Bitmap b = BitmapFactory.decodeStream(in, null, opts);
            ColorSpace cs = opts.outColorSpace;
            assertNull(b);
            assertNotNull(cs);
            assertSame(ColorSpace.get(ColorSpace.Named.SRGB), cs);
        } catch (IOException e) {
            fail();
        }
    }

    @Test
    public void inColorSpaceP3ToSRGB() {
        BitmapFactory.Options opts = new BitmapFactory.Options();
        opts.inPreferredColorSpace = ColorSpace.get(ColorSpace.Named.SRGB);

        try (InputStream in = mResources.getAssets().open("green-p3.png")) {
            Bitmap b = BitmapFactory.decodeStream(in, null, opts);
            ColorSpace cs = b.getColorSpace();
            assertNotNull(cs);
            assertSame(ColorSpace.get(ColorSpace.Named.SRGB), cs);
            assertEquals(opts.inPreferredColorSpace, opts.outColorSpace);

            verifyGetPixel(b, 0x3ff00ff, 0xff00ff00);
        } catch (IOException e) {
            fail();
        }
    }

    @Test
    public void inColorSpaceSRGBToP3() {
        BitmapFactory.Options opts = new BitmapFactory.Options();
        opts.inPreferredColorSpace = ColorSpace.get(ColorSpace.Named.DISPLAY_P3);

        try (InputStream in = mResources.getAssets().open("green-srgb.png")) {
            Bitmap b = BitmapFactory.decodeStream(in, null, opts);
            ColorSpace cs = b.getColorSpace();
            assertNotNull(cs);
            assertSame(ColorSpace.get(ColorSpace.Named.DISPLAY_P3), cs);
            assertEquals(opts.inPreferredColorSpace, opts.outColorSpace);

            verifyGetPixel(b, 0x75fb4cff, 0xff00ff00);
        } catch (IOException e) {
            fail();
        }
    }

    @Test
    public void inColorSpaceRGBA16F() {
        BitmapFactory.Options opts = new BitmapFactory.Options();
        opts.inPreferredColorSpace = ColorSpace.get(ColorSpace.Named.ADOBE_RGB);

        try (InputStream in = mResources.getAssets().open("prophoto-rgba16f.png")) {
            Bitmap b = BitmapFactory.decodeStream(in, null, opts);
            ColorSpace cs = b.getColorSpace();
            assertNotNull(cs);
            assertSame(ColorSpace.get(ColorSpace.Named.LINEAR_EXTENDED_SRGB), cs);
            assertNotEquals(opts.inPreferredColorSpace, opts.outColorSpace);
        } catch (IOException e) {
            fail();
        }
    }

    @Test
    public void inColorSpace565() {
        BitmapFactory.Options opts = new BitmapFactory.Options();
        opts.inPreferredColorSpace = ColorSpace.get(ColorSpace.Named.ADOBE_RGB);
        opts.inPreferredConfig = Bitmap.Config.RGB_565;

        try (InputStream in = mResources.getAssets().open("green-p3.png")) {
            Bitmap b = BitmapFactory.decodeStream(in, null, opts);
            ColorSpace cs = b.getColorSpace();
            assertNotNull(cs);
            assertSame(ColorSpace.get(ColorSpace.Named.SRGB), cs);
            assertNotEquals(opts.inPreferredColorSpace, opts.outColorSpace);
        } catch (IOException e) {
            fail();
        }
    }

    @Test(expected = IllegalArgumentException.class)
    public void inColorSpaceNotRGB() {
        BitmapFactory.Options opts = new BitmapFactory.Options();
        opts.inPreferredColorSpace = ColorSpace.get(ColorSpace.Named.CIE_LAB);

        try (InputStream in = mResources.getAssets().open("green-p3.png")) {
            BitmapFactory.decodeStream(in, null, opts);
        } catch (IOException e) {
            fail();
        }
    }

    @Test(expected = IllegalArgumentException.class)
    public void inColorSpaceNoTransferParameters() {
        BitmapFactory.Options opts = new BitmapFactory.Options();
        opts.inPreferredColorSpace = ColorSpace.get(ColorSpace.Named.EXTENDED_SRGB);

        try (InputStream in = mResources.getAssets().open("green-p3.png")) {
            BitmapFactory.decodeStream(in, null, opts);
        } catch (IOException e) {
            fail();
        }
    }

    @Test
    public void copy() {
        Bitmap b = BitmapFactory.decodeResource(mResources, R.drawable.robot);
        Bitmap c = b.copy(Bitmap.Config.ARGB_8888, false);
        ColorSpace cs = c.getColorSpace();
        assertNotNull(cs);
        assertSame(ColorSpace.get(ColorSpace.Named.SRGB), cs);

        c = b.copy(Bitmap.Config.ARGB_8888, true);
        cs = c.getColorSpace();
        assertNotNull(cs);
        assertSame(ColorSpace.get(ColorSpace.Named.SRGB), cs);

        try (InputStream in = mResources.getAssets().open("green-p3.png")) {
            b = BitmapFactory.decodeStream(in);
            c = b.copy(Bitmap.Config.ARGB_8888, false);
            cs = c.getColorSpace();
            assertNotNull(cs);
            assertSame(ColorSpace.get(ColorSpace.Named.DISPLAY_P3), cs);

            c = b.copy(Bitmap.Config.ARGB_8888, true);
            cs = c.getColorSpace();
            assertNotNull(cs);
            assertSame(ColorSpace.get(ColorSpace.Named.DISPLAY_P3), cs);
        } catch (IOException e) {
            fail();
        }

        try (InputStream in = mResources.getAssets().open("prophoto-rgba16f.png")) {
            b = BitmapFactory.decodeStream(in);
            c = b.copy(Bitmap.Config.RGBA_F16, false);
            cs = c.getColorSpace();
            assertNotNull(cs);
            assertSame(ColorSpace.get(ColorSpace.Named.LINEAR_EXTENDED_SRGB), cs);

            c = b.copy(Bitmap.Config.RGBA_F16, true);
            cs = c.getColorSpace();
            assertNotNull(cs);
            assertSame(ColorSpace.get(ColorSpace.Named.LINEAR_EXTENDED_SRGB), cs);
        } catch (IOException e) {
            fail();
        }
    }

    @SuppressWarnings("SameParameterValue")
    private static void almostEqual(@ColorInt int expected,
            @ColorInt int pixel, int threshold, int index) {
        int diffA = Math.abs(expected >>> 24 - pixel >>> 24);
        int diffR = Math.abs((expected >> 16) & 0xff - (pixel >> 16) & 0xff);
        int diffG = Math.abs((expected >>  8) & 0xff - (pixel >>  8) & 0xff);
        int diffB = Math.abs((expected      ) & 0xff - (pixel      ) & 0xff);

        boolean pass = diffA + diffR + diffG + diffB < threshold;
        if (!pass) {
            Log.d(LOG_TAG, "Expected 0x" + Integer.toHexString(expected) +
                    " but was 0x" + Integer.toHexString(pixel) + " with index " + index);
        }

        assertTrue(pass);
    }
}
