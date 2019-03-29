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
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.Bitmap.CompressFormat;
import android.graphics.Bitmap.Config;
import android.graphics.BitmapFactory;
import android.graphics.BitmapFactory.Options;
import android.graphics.Color;
import android.graphics.Rect;
import android.os.Parcel;
import android.os.ParcelFileDescriptor;
import android.support.test.InstrumentationRegistry;
import android.support.test.filters.SmallTest;
import android.support.test.runner.AndroidJUnit4;
import android.system.ErrnoException;
import android.system.Os;
import android.system.OsConstants;
import android.util.DisplayMetrics;
import android.util.TypedValue;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileDescriptor;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.RandomAccessFile;
import java.util.concurrent.CountDownLatch;

@SmallTest
@RunWith(AndroidJUnit4.class)
public class BitmapFactoryTest {
    // height and width of start.jpg
    private static final int START_HEIGHT = 31;
    private static final int START_WIDTH = 31;

    // The test images, including baseline JPEG, a PNG, a GIF, a BMP AND a WEBP.
    private static final int[] RES_IDS = new int[] {
            R.drawable.baseline_jpeg, R.drawable.png_test, R.drawable.gif_test,
            R.drawable.bmp_test, R.drawable.webp_test
    };

    // The width and height of the above image.
    private static final int WIDTHS[] = new int[] { 1280, 640, 320, 320, 640 };
    private static final int HEIGHTS[] = new int[] { 960, 480, 240, 240, 480 };

    // Configurations for BitmapFactory.Options
    private static final Config[] COLOR_CONFIGS = new Config[] {Config.ARGB_8888, Config.RGB_565};
    private static final int[] COLOR_TOLS = new int[] {16, 49, 576};

    private static final Config[] COLOR_CONFIGS_RGBA = new Config[] {Config.ARGB_8888};
    private static final int[] COLOR_TOLS_RGBA = new int[] {72, 124};

    private static final int[] RAW_COLORS = new int[] {
        // raw data from R.drawable.premul_data
        Color.argb(255, 0, 0, 0),
        Color.argb(128, 255, 0, 0),
        Color.argb(128, 25, 26, 27),
        Color.argb(2, 255, 254, 253),
    };

    private static final int[] DEPREMUL_COLORS = new int[] {
        // data from R.drawable.premul_data, after premultiplied store + un-premultiplied load
        Color.argb(255, 0, 0, 0),
        Color.argb(128, 255, 0, 0),
        Color.argb(128, 26, 26, 28),
        Color.argb(2, 255, 255, 255),
    };

    private Resources mRes;
    // opt for non-null
    private BitmapFactory.Options mOpt1;
    // opt for null
    private BitmapFactory.Options mOpt2;
    private int mDefaultDensity;
    private int mTargetDensity;

    @Before
    public void setup() {
        mRes = InstrumentationRegistry.getTargetContext().getResources();
        mDefaultDensity = DisplayMetrics.DENSITY_DEFAULT;
        mTargetDensity = mRes.getDisplayMetrics().densityDpi;

        mOpt1 = new BitmapFactory.Options();
        mOpt1.inScaled = false;
        mOpt2 = new BitmapFactory.Options();
        mOpt2.inScaled = false;
        mOpt2.inJustDecodeBounds = true;
    }

    @Test
    public void testConstructor() {
        new BitmapFactory();
    }

    @Test
    public void testDecodeResource1() {
        Bitmap b = BitmapFactory.decodeResource(mRes, R.drawable.start,
                mOpt1);
        assertNotNull(b);
        // Test the bitmap size
        assertEquals(START_HEIGHT, b.getHeight());
        assertEquals(START_WIDTH, b.getWidth());
        // Test if no bitmap
        assertNull(BitmapFactory.decodeResource(mRes, R.drawable.start, mOpt2));
    }

    @Test
    public void testDecodeResource2() {
        Bitmap b = BitmapFactory.decodeResource(mRes, R.drawable.start);
        assertNotNull(b);
        // Test the bitmap size
        assertEquals(START_HEIGHT * mTargetDensity / mDefaultDensity, b.getHeight(), 1.1);
        assertEquals(START_WIDTH * mTargetDensity / mDefaultDensity, b.getWidth(), 1.1);
    }

    @Test
    public void testDecodeResourceStream() {
        InputStream is = obtainInputStream();
        Rect r = new Rect(1, 1, 1, 1);
        TypedValue value = new TypedValue();
        Bitmap b = BitmapFactory.decodeResourceStream(mRes, value, is, r, mOpt1);
        assertNotNull(b);
        // Test the bitmap size
        assertEquals(START_HEIGHT, b.getHeight());
        assertEquals(START_WIDTH, b.getWidth());
    }

    @Test
    public void testDecodeByteArray1() {
        byte[] array = obtainArray();
        Bitmap b = BitmapFactory.decodeByteArray(array, 0, array.length, mOpt1);
        assertNotNull(b);
        // Test the bitmap size
        assertEquals(START_HEIGHT, b.getHeight());
        assertEquals(START_WIDTH, b.getWidth());
        // Test if no bitmap
        assertNull(BitmapFactory.decodeByteArray(array, 0, array.length, mOpt2));
    }

    @Test
    public void testDecodeByteArray2() {
        byte[] array = obtainArray();
        Bitmap b = BitmapFactory.decodeByteArray(array, 0, array.length);
        assertNotNull(b);
        // Test the bitmap size
        assertEquals(START_HEIGHT, b.getHeight());
        assertEquals(START_WIDTH, b.getWidth());
    }

    @Test
    public void testDecodeStream1() {
        InputStream is = obtainInputStream();
        Rect r = new Rect(1, 1, 1, 1);
        Bitmap b = BitmapFactory.decodeStream(is, r, mOpt1);
        assertNotNull(b);
        // Test the bitmap size
        assertEquals(START_HEIGHT, b.getHeight());
        assertEquals(START_WIDTH, b.getWidth());
        // Test if no bitmap
        assertNull(BitmapFactory.decodeStream(is, r, mOpt2));
    }

    @Test
    public void testDecodeStream2() {
        InputStream is = obtainInputStream();
        Bitmap b = BitmapFactory.decodeStream(is);
        assertNotNull(b);
        // Test the bitmap size
        assertEquals(START_HEIGHT, b.getHeight());
        assertEquals(START_WIDTH, b.getWidth());
    }

    @Test
    public void testDecodeStream3() {
        for (int i = 0; i < RES_IDS.length; ++i) {
            InputStream is = obtainInputStream(RES_IDS[i]);
            Bitmap b = BitmapFactory.decodeStream(is);
            assertNotNull(b);
            // Test the bitmap size
            assertEquals(WIDTHS[i], b.getWidth());
            assertEquals(HEIGHTS[i], b.getHeight());
        }
    }

    @Test
    public void testDecodeStream4() {
        BitmapFactory.Options options = new BitmapFactory.Options();
        for (int k = 0; k < COLOR_CONFIGS.length; ++k) {
            options.inPreferredConfig = COLOR_CONFIGS[k];

            // Decode the PNG & WebP test images. The WebP test image has been encoded from PNG test
            // image and should have same similar (within some error-tolerance) Bitmap data.
            InputStream iStreamPng = obtainInputStream(R.drawable.png_test);
            Bitmap bPng = BitmapFactory.decodeStream(iStreamPng, null, options);
            assertNotNull(bPng);
            assertEquals(bPng.getConfig(), COLOR_CONFIGS[k]);
            assertFalse(bPng.isPremultiplied());
            assertFalse(bPng.hasAlpha());

            InputStream iStreamWebp1 = obtainInputStream(R.drawable.webp_test);
            Bitmap bWebp1 = BitmapFactory.decodeStream(iStreamWebp1, null, options);
            assertNotNull(bWebp1);
            assertFalse(bWebp1.isPremultiplied());
            assertFalse(bWebp1.hasAlpha());
            compareBitmaps(bPng, bWebp1, COLOR_TOLS[k], true, bPng.isPremultiplied());

            // Compress the PNG image to WebP format (Quality=90) and decode it back.
            // This will test end-to-end WebP encoding and decoding.
            ByteArrayOutputStream oStreamWebp = new ByteArrayOutputStream();
            assertTrue(bPng.compress(CompressFormat.WEBP, 90, oStreamWebp));
            InputStream iStreamWebp2 = new ByteArrayInputStream(oStreamWebp.toByteArray());
            Bitmap bWebp2 = BitmapFactory.decodeStream(iStreamWebp2, null, options);
            assertNotNull(bWebp2);
            assertFalse(bWebp2.isPremultiplied());
            assertFalse(bWebp2.hasAlpha());
            compareBitmaps(bPng, bWebp2, COLOR_TOLS[k], true, bPng.isPremultiplied());
        }
    }

    @Test
    public void testDecodeStream5() {
        BitmapFactory.Options options = new BitmapFactory.Options();
        for (int k = 0; k < COLOR_CONFIGS_RGBA.length; ++k) {
            options.inPreferredConfig = COLOR_CONFIGS_RGBA[k];

            // Decode the PNG & WebP (google_logo) images. The WebP image has
            // been encoded from PNG image.
            InputStream iStreamPng = obtainInputStream(R.drawable.google_logo_1);
            Bitmap bPng = BitmapFactory.decodeStream(iStreamPng, null, options);
            assertNotNull(bPng);
            assertEquals(bPng.getConfig(), COLOR_CONFIGS_RGBA[k]);
            assertTrue(bPng.isPremultiplied());
            assertTrue(bPng.hasAlpha());

            // Decode the corresponding WebP (transparent) image (google_logo_2.webp).
            InputStream iStreamWebP1 = obtainInputStream(R.drawable.google_logo_2);
            Bitmap bWebP1 = BitmapFactory.decodeStream(iStreamWebP1, null, options);
            assertNotNull(bWebP1);
            assertEquals(bWebP1.getConfig(), COLOR_CONFIGS_RGBA[k]);
            assertTrue(bWebP1.isPremultiplied());
            assertTrue(bWebP1.hasAlpha());
            compareBitmaps(bPng, bWebP1, COLOR_TOLS_RGBA[k], true, bPng.isPremultiplied());

            // Compress the PNG image to WebP format (Quality=90) and decode it back.
            // This will test end-to-end WebP encoding and decoding.
            ByteArrayOutputStream oStreamWebp = new ByteArrayOutputStream();
            assertTrue(bPng.compress(CompressFormat.WEBP, 90, oStreamWebp));
            InputStream iStreamWebp2 = new ByteArrayInputStream(oStreamWebp.toByteArray());
            Bitmap bWebP2 = BitmapFactory.decodeStream(iStreamWebp2, null, options);
            assertNotNull(bWebP2);
            assertEquals(bWebP2.getConfig(), COLOR_CONFIGS_RGBA[k]);
            assertTrue(bWebP2.isPremultiplied());
            assertTrue(bWebP2.hasAlpha());
            compareBitmaps(bPng, bWebP2, COLOR_TOLS_RGBA[k], true, bPng.isPremultiplied());
        }
    }

    @Test
    public void testDecodeFileDescriptor1() throws IOException {
        ParcelFileDescriptor pfd = obtainParcelDescriptor(obtainPath());
        FileDescriptor input = pfd.getFileDescriptor();
        Rect r = new Rect(1, 1, 1, 1);
        Bitmap b = BitmapFactory.decodeFileDescriptor(input, r, mOpt1);
        assertNotNull(b);
        // Test the bitmap size
        assertEquals(START_HEIGHT, b.getHeight());
        assertEquals(START_WIDTH, b.getWidth());
        // Test if no bitmap
        assertNull(BitmapFactory.decodeFileDescriptor(input, r, mOpt2));
    }

    @Test
    public void testDecodeFileDescriptor2() throws IOException {
        ParcelFileDescriptor pfd = obtainParcelDescriptor(obtainPath());
        FileDescriptor input = pfd.getFileDescriptor();
        Bitmap b = BitmapFactory.decodeFileDescriptor(input);
        assertNotNull(b);
        // Test the bitmap size
        assertEquals(START_HEIGHT, b.getHeight());
        assertEquals(START_WIDTH, b.getWidth());
    }

    @Test
    public void testDecodeFileDescriptor3() throws IOException {
        // Arbitrary offsets to use. If the offset of the FD matches the offset of the image,
        // decoding should succeed, but if they do not match, decoding should fail.
        long ACTUAL_OFFSETS[] = new long[] { 0, 17 };
        for (int RES_ID : RES_IDS) {
            for (int j = 0; j < ACTUAL_OFFSETS.length; ++j) {
                // FIXME: The purgeable test should attempt to purge the memory
                // to force a re-decode.
                for (boolean TEST_PURGEABLE : new boolean[] { false, true }) {
                    BitmapFactory.Options opts = new BitmapFactory.Options();
                    opts.inPurgeable = TEST_PURGEABLE;
                    opts.inInputShareable = TEST_PURGEABLE;

                    long actualOffset = ACTUAL_OFFSETS[j];
                    String path = obtainPath(RES_ID, actualOffset);
                    RandomAccessFile file = new RandomAccessFile(path, "r");
                    FileDescriptor fd = file.getFD();
                    assertTrue(fd.valid());

                    // Set the offset to ACTUAL_OFFSET
                    file.seek(actualOffset);
                    assertEquals(file.getFilePointer(), actualOffset);

                    // Now decode. This should be successful and leave the offset
                    // unchanged.
                    Bitmap b = BitmapFactory.decodeFileDescriptor(fd, null, opts);
                    assertNotNull(b);
                    assertEquals(file.getFilePointer(), actualOffset);

                    // Now use the other offset. It should fail to decode, and
                    // the offset should remain unchanged.
                    long otherOffset = ACTUAL_OFFSETS[(j + 1) % ACTUAL_OFFSETS.length];
                    assertFalse(otherOffset == actualOffset);
                    file.seek(otherOffset);
                    assertEquals(file.getFilePointer(), otherOffset);

                    b = BitmapFactory.decodeFileDescriptor(fd, null, opts);
                    assertNull(b);
                    assertEquals(file.getFilePointer(), otherOffset);
                }
            }
        }
    }

    @Test
    public void testDecodeFile1() throws IOException {
        Bitmap b = BitmapFactory.decodeFile(obtainPath(), mOpt1);
        assertNotNull(b);
        // Test the bitmap size
        assertEquals(START_HEIGHT, b.getHeight());
        assertEquals(START_WIDTH, b.getWidth());
        // Test if no bitmap
        assertNull(BitmapFactory.decodeFile(obtainPath(), mOpt2));
    }

    @Test
    public void testDecodeFile2() throws IOException {
        Bitmap b = BitmapFactory.decodeFile(obtainPath());
        assertNotNull(b);
        // Test the bitmap size
        assertEquals(START_HEIGHT, b.getHeight());
        assertEquals(START_WIDTH, b.getWidth());
    }

    @Test
    public void testDecodeReuseBasic() {
        BitmapFactory.Options options = new BitmapFactory.Options();
        options.inMutable = true;
        options.inSampleSize = 0; // treated as 1
        options.inScaled = false;
        Bitmap start = BitmapFactory.decodeResource(mRes, R.drawable.start, options);
        int originalSize = start.getByteCount();
        assertEquals(originalSize, start.getAllocationByteCount());

        options.inBitmap = start;
        options.inMutable = false; // will be overridden by non-null inBitmap
        options.inSampleSize = -42; // treated as 1
        Bitmap pass = BitmapFactory.decodeResource(mRes, R.drawable.pass, options);

        assertEquals(originalSize, pass.getByteCount());
        assertEquals(originalSize, pass.getAllocationByteCount());
        assertSame(start, pass);
        assertTrue(pass.isMutable());
    }

    /**
     * Create bitmap sized to load unscaled resources: start, pass, and alpha
     */
    private Bitmap createBitmapForReuse(int pixelCount) {
        Bitmap bitmap = Bitmap.createBitmap(pixelCount, 1, Config.ARGB_8888);
        bitmap.eraseColor(Color.BLACK);
        bitmap.setHasAlpha(false);
        return bitmap;
    }

    /**
     * Decode resource with ResId into reuse bitmap without scaling, verifying expected hasAlpha
     */
    private void decodeResourceWithReuse(Bitmap reuse, int resId, boolean hasAlpha) {
        BitmapFactory.Options options = new BitmapFactory.Options();
        options.inMutable = true;
        options.inSampleSize = 1;
        options.inScaled = false;
        options.inBitmap = reuse;
        Bitmap output = BitmapFactory.decodeResource(mRes, resId, options);
        assertSame(reuse, output);
        assertEquals(output.hasAlpha(), hasAlpha);
    }

    @Test
    public void testDecodeReuseHasAlpha() {
        final int bitmapSize = 31; // size in pixels of start, pass, and alpha resources
        final int pixelCount = bitmapSize * bitmapSize;

        // Test reuse, hasAlpha false and true
        Bitmap bitmap = createBitmapForReuse(pixelCount);
        decodeResourceWithReuse(bitmap, R.drawable.start, false);
        decodeResourceWithReuse(bitmap, R.drawable.alpha, true);

        // Test pre-reconfigure, hasAlpha false and true
        bitmap = createBitmapForReuse(pixelCount);
        bitmap.reconfigure(bitmapSize, bitmapSize, Config.ARGB_8888);
        bitmap.setHasAlpha(true);
        decodeResourceWithReuse(bitmap, R.drawable.start, false);

        bitmap = createBitmapForReuse(pixelCount);
        bitmap.reconfigure(bitmapSize, bitmapSize, Config.ARGB_8888);
        decodeResourceWithReuse(bitmap, R.drawable.alpha, true);
    }

    @Test
    public void testDecodeReuseFormats() {
        // reuse should support all image formats
        for (int i = 0; i < RES_IDS.length; ++i) {
            Bitmap reuseBuffer = Bitmap.createBitmap(1000000, 1, Bitmap.Config.ALPHA_8);

            BitmapFactory.Options options = new BitmapFactory.Options();
            options.inBitmap = reuseBuffer;
            options.inSampleSize = 4;
            options.inScaled = false;
            Bitmap decoded = BitmapFactory.decodeResource(mRes, RES_IDS[i], options);
            assertSame(reuseBuffer, decoded);
        }
    }

    @Test
    public void testDecodeReuseFailure() {
        BitmapFactory.Options options = new BitmapFactory.Options();
        options.inMutable = true;
        options.inScaled = false;
        options.inSampleSize = 4;
        Bitmap reduced = BitmapFactory.decodeResource(mRes, R.drawable.robot, options);

        options.inBitmap = reduced;
        options.inSampleSize = 1;
        try {
            BitmapFactory.decodeResource(mRes, R.drawable.robot, options);
            fail("should throw exception due to lack of space");
        } catch (IllegalArgumentException e) {
        }
    }

    @Test
    public void testDecodeReuseScaling() {
        BitmapFactory.Options options = new BitmapFactory.Options();
        options.inMutable = true;
        options.inScaled = false;
        Bitmap original = BitmapFactory.decodeResource(mRes, R.drawable.robot, options);
        int originalSize = original.getByteCount();
        assertEquals(originalSize, original.getAllocationByteCount());

        options.inBitmap = original;
        options.inSampleSize = 4;
        Bitmap reduced = BitmapFactory.decodeResource(mRes, R.drawable.robot, options);

        assertSame(original, reduced);
        assertEquals(originalSize, reduced.getAllocationByteCount());
        assertEquals(originalSize, reduced.getByteCount() * 16);
    }

    @Test
    public void testDecodeReuseDoubleScaling() {
        BitmapFactory.Options options = new BitmapFactory.Options();
        options.inMutable = true;
        options.inScaled = false;
        options.inSampleSize = 1;
        Bitmap original = BitmapFactory.decodeResource(mRes, R.drawable.robot, options);
        int originalSize = original.getByteCount();

        // Verify that inSampleSize and density scaling both work with reuse concurrently
        options.inBitmap = original;
        options.inScaled = true;
        options.inSampleSize = 2;
        options.inDensity = 2;
        options.inTargetDensity = 4;
        Bitmap doubleScaled = BitmapFactory.decodeResource(mRes, R.drawable.robot, options);

        assertSame(original, doubleScaled);
        assertEquals(4, doubleScaled.getDensity());
        assertEquals(originalSize, doubleScaled.getByteCount());
    }

    @Test
    public void testDecodeReuseEquivalentScaling() {
        BitmapFactory.Options options = new BitmapFactory.Options();
        options.inMutable = true;
        options.inScaled = true;
        options.inDensity = 4;
        options.inTargetDensity = 2;
        Bitmap densityReduced = BitmapFactory.decodeResource(mRes, R.drawable.robot, options);
        assertEquals(2, densityReduced.getDensity());
        options.inBitmap = densityReduced;
        options.inDensity = 0;
        options.inScaled = false;
        options.inSampleSize = 2;
        Bitmap scaleReduced = BitmapFactory.decodeResource(mRes, R.drawable.robot, options);
        // verify that density isn't incorrectly carried over during bitmap reuse
        assertFalse(densityReduced.getDensity() == 2);
        assertFalse(densityReduced.getDensity() == 0);
        assertSame(densityReduced, scaleReduced);
    }

    @Test
    public void testDecodePremultipliedDefault() {
        Bitmap simplePremul = BitmapFactory.decodeResource(mRes, R.drawable.premul_data);
        assertTrue(simplePremul.isPremultiplied());
    }

    @Test
    public void testDecodePremultipliedData() {
        BitmapFactory.Options options = new BitmapFactory.Options();
        options.inScaled = false;
        Bitmap premul = BitmapFactory.decodeResource(mRes, R.drawable.premul_data, options);
        options.inPremultiplied = false;
        Bitmap unpremul = BitmapFactory.decodeResource(mRes, R.drawable.premul_data, options);
        assertEquals(premul.getConfig(), Bitmap.Config.ARGB_8888);
        assertEquals(unpremul.getConfig(), Bitmap.Config.ARGB_8888);
        assertTrue(premul.getHeight() == 1 && unpremul.getHeight() == 1);
        assertTrue(premul.getWidth() == unpremul.getWidth() &&
                   DEPREMUL_COLORS.length == RAW_COLORS.length &&
                   premul.getWidth() == DEPREMUL_COLORS.length);

        // verify pixel data - unpremul should have raw values, premul will have rounding errors
        for (int i = 0; i < premul.getWidth(); i++) {
            assertEquals(premul.getPixel(i, 0), DEPREMUL_COLORS[i]);
            assertEquals(unpremul.getPixel(i, 0), RAW_COLORS[i]);
        }
    }

    @Test
    public void testDecodeInPurgeableAllocationCount() {
        BitmapFactory.Options options = new BitmapFactory.Options();
        options.inSampleSize = 1;
        options.inJustDecodeBounds = false;
        options.inPurgeable = true;
        options.inInputShareable = false;
        byte[] array = obtainArray();
        Bitmap purgeableBitmap = BitmapFactory.decodeByteArray(array, 0, array.length, options);
        assertFalse(purgeableBitmap.getAllocationByteCount() == 0);
    }

    private int mDefaultCreationDensity;
    private void verifyScaled(Bitmap b) {
        assertEquals(b.getWidth(), START_WIDTH * 2);
        assertEquals(b.getDensity(), 2);
    }

    private void verifyUnscaled(Bitmap b) {
        assertEquals(b.getWidth(), START_WIDTH);
        assertEquals(b.getDensity(), mDefaultCreationDensity);
    }

    @Test
    public void testDecodeScaling() {
        BitmapFactory.Options defaultOpt = new BitmapFactory.Options();

        BitmapFactory.Options unscaledOpt = new BitmapFactory.Options();
        unscaledOpt.inScaled = false;

        BitmapFactory.Options scaledOpt = new BitmapFactory.Options();
        scaledOpt.inScaled = true;
        scaledOpt.inDensity = 1;
        scaledOpt.inTargetDensity = 2;

        mDefaultCreationDensity = Bitmap.createBitmap(1, 1, Config.ARGB_8888).getDensity();

        byte[] bytes = obtainArray();

        verifyUnscaled(BitmapFactory.decodeByteArray(bytes, 0, bytes.length));
        verifyUnscaled(BitmapFactory.decodeByteArray(bytes, 0, bytes.length, null));
        verifyUnscaled(BitmapFactory.decodeByteArray(bytes, 0, bytes.length, unscaledOpt));
        verifyUnscaled(BitmapFactory.decodeByteArray(bytes, 0, bytes.length, defaultOpt));

        verifyUnscaled(BitmapFactory.decodeStream(obtainInputStream()));
        verifyUnscaled(BitmapFactory.decodeStream(obtainInputStream(), null, null));
        verifyUnscaled(BitmapFactory.decodeStream(obtainInputStream(), null, unscaledOpt));
        verifyUnscaled(BitmapFactory.decodeStream(obtainInputStream(), null, defaultOpt));

        // scaling should only occur if Options are passed with inScaled=true
        verifyScaled(BitmapFactory.decodeByteArray(bytes, 0, bytes.length, scaledOpt));
        verifyScaled(BitmapFactory.decodeStream(obtainInputStream(), null, scaledOpt));
    }

    @Test
    public void testParcel() {
        BitmapFactory.Options opts = new BitmapFactory.Options();
        opts.inScaled = false;
        Bitmap b = BitmapFactory.decodeResource(mRes, R.drawable.gif_test, opts);
        assertNotNull(b);

        Parcel p = Parcel.obtain();
        b.writeToParcel(p, 0);

        p.setDataPosition(0);
        Bitmap b2 = Bitmap.CREATOR.createFromParcel(p);
        compareBitmaps(b, b2, 0, true, true);

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        assertTrue(b2.compress(Bitmap.CompressFormat.JPEG, 50, baos));
    }

    @Test
    public void testConfigs() {
        // The output Config of a BitmapFactory decode depends on the request from the
        // client and the properties of the image to be decoded.
        //
        // Options.inPreferredConfig = Config.ARGB_8888
        //     This is the default value of inPreferredConfig.  In this case, the image
        //     will always be decoded to Config.ARGB_8888.
        // Options.inPreferredConfig = Config.RGB_565
        //     If the encoded image is opaque, we will decode to Config.RGB_565,
        //     otherwise we will decode to whichever color type is the most natural match
        //     for the encoded data.
        // Options.inPreferredConfig = Config.ARGB_4444
        //     This is deprecated and will always decode to Config.ARGB_8888.
        // Options.inPreferredConfig = Config.ALPHA_8
        //     If the encoded image is gray, we will decode to 8-bit grayscale values
        //     and indicate that the output bitmap is Config.ALPHA_8.  This is somewhat
        //     misleading because the image is really opaque and grayscale, but we are
        //     labeling each pixel as if it is a translucency (alpha) value.  If the
        //     encoded image is not gray, we will decode to whichever color type is the
        //     most natural match for the encoded data.
        // Options.inPreferredConfig = null
        //     We will decode to whichever Config is the most natural match with the
        //     encoded data.  This could be ALPHA_8 (gray) or ARGB_8888.
        //
        // This test ensures that images are decoded to the intended Config and that the
        // decodes match regardless of the Config.
        decodeConfigs(R.drawable.alpha, 31, 31, true, false);
        decodeConfigs(R.drawable.baseline_jpeg, 1280, 960, false, false);
        decodeConfigs(R.drawable.bmp_test, 320, 240, false, false);
        decodeConfigs(R.drawable.scaled2, 6, 8, false, false);
        decodeConfigs(R.drawable.grayscale_jpg, 128, 128, false, true);
        decodeConfigs(R.drawable.grayscale_png, 128, 128, false, true);
    }

    @Test(expected=IllegalArgumentException.class)
    public void testMutableHardwareInDecodeResource() {
        Options options = new Options();
        options.inMutable = true;
        options.inPreferredConfig = Config.HARDWARE;
        BitmapFactory.decodeResource(mRes, R.drawable.alpha, options);
    }

    @Test(expected=IllegalArgumentException.class)
    public void testMutableHardwareInDecodeByteArray() {
        Options options = new Options();
        options.inMutable = true;
        options.inPreferredConfig = Config.HARDWARE;
        BitmapFactory.decodeByteArray(new byte[100], 1, 20, options);
    }

    @Test(expected=IllegalArgumentException.class)
    public void testMutableHardwareInDecodeFile() {
        Options options = new Options();
        options.inMutable = true;
        options.inPreferredConfig = Config.HARDWARE;
        BitmapFactory.decodeFile("barely/care.jpg", options);
    }

    @Test(expected=IllegalArgumentException.class)
    public void testMutableHardwareInDecodeFileDescriptor() {
        Options options = new Options();
        options.inMutable = true;
        options.inPreferredConfig = Config.HARDWARE;
        BitmapFactory.decodeFileDescriptor(null, new Rect(), options);
    }

    @Test(expected=IllegalArgumentException.class)
    public void testMutableHardwareInDecodeResourceStream() {
        Options options = new Options();
        options.inMutable = true;
        options.inPreferredConfig = Config.HARDWARE;
        TypedValue value = new TypedValue();
        BitmapFactory.decodeResourceStream(mRes, value,
                new ByteArrayInputStream(new byte[20]), new Rect(), options);
    }

    @Test
    public void testDecodeHardwareBitmap() {
        BitmapFactory.Options options = new BitmapFactory.Options();
        options.inPreferredConfig = Bitmap.Config.HARDWARE;
        Bitmap hardwareBitmap = BitmapFactory.decodeResource(mRes, R.drawable.robot, options);
        assertNotNull(hardwareBitmap);
        // Test that checks that correct bitmap was obtained is in uirendering/HardwareBitmapTests
        assertEquals(Config.HARDWARE, hardwareBitmap.getConfig());
    }

    @Test
    public void testDecodePngFromPipe() {
        // This test verifies that we can send a PNG over a pipe and
        // successfully decode it. This behavior worked in N, so this
        // verifies that do not break it for backwards compatibility.
        // This was already not supported for the other Bitmap.CompressFormats
        // (JPEG and WEBP), so we do not test those.
        Bitmap source = Bitmap.createBitmap(100, 100, Config.ARGB_8888);
        source.eraseColor(Color.RED);
        try {
            Bitmap result = sendOverPipe(source, CompressFormat.PNG);
            assertTrue(source.sameAs(result));
        } catch (Exception e) {
            fail(e.toString());
        }
    }

    private Bitmap sendOverPipe(Bitmap source, CompressFormat format)
            throws IOException, ErrnoException, InterruptedException {
        FileDescriptor[] pipeFds = Os.pipe();
        final FileDescriptor readFd = pipeFds[0];
        final FileDescriptor writeFd = pipeFds[1];
        final Throwable[] compressErrors = new Throwable[1];
        final CountDownLatch writeFinished = new CountDownLatch(1);
        final CountDownLatch readFinished = new CountDownLatch(1);
        final Bitmap[] decodedResult = new Bitmap[1];
        Thread writeThread = new Thread() {
            @Override
            public void run() {
                try {
                    FileOutputStream output = new FileOutputStream(writeFd);
                    source.compress(format, 100, output);
                    output.close();
                } catch (Throwable t) {
                    compressErrors[0] = t;
                    // Try closing the FD to unblock the test thread
                    try {
                        Os.close(writeFd);
                    } catch (Throwable ignore) {}
                } finally {
                    writeFinished.countDown();
                }
            }
        };
        Thread readThread = new Thread() {
            @Override
            public void run() {
                decodedResult[0] = BitmapFactory.decodeFileDescriptor(readFd);
            }
        };
        writeThread.start();
        readThread.start();
        writeThread.join(1000);
        readThread.join(1000);
        assertFalse(writeThread.isAlive());
        if (compressErrors[0] != null) {
            fail(compressErrors[0].toString());
        }
        if (readThread.isAlive()) {
            // Test failure, try to clean up
            Os.close(writeFd);
            readThread.join(500);
            fail("Read timed out");
        }
        assertValidFd("readFd", readFd);
        assertValidFd("writeFd", writeFd);
        Os.close(readFd);
        Os.close(writeFd);
        return decodedResult[0];
    }

    private static void assertValidFd(String name, FileDescriptor fd) {
        try {
            assertTrue(fd.valid());
            // Hacky check to test that the underlying FD is still valid without using
            // the private fcntlVoid to do F_GETFD
            Os.close(Os.dup(fd));
        } catch (ErrnoException ex) {
            fail(name + " is invalid: " + ex.getMessage());
        }
    }

    private void decodeConfigs(int id, int width, int height, boolean hasAlpha, boolean isGray) {
        Options opts = new BitmapFactory.Options();
        opts.inScaled = false;
        assertEquals(Config.ARGB_8888, opts.inPreferredConfig);
        Bitmap reference = BitmapFactory.decodeResource(mRes, id, opts);
        assertNotNull(reference);
        assertEquals(width, reference.getWidth());
        assertEquals(height, reference.getHeight());
        assertEquals(Config.ARGB_8888, reference.getConfig());

        opts.inPreferredConfig = Config.ARGB_4444;
        Bitmap argb4444 = BitmapFactory.decodeResource(mRes, id, opts);
        assertNotNull(argb4444);
        assertEquals(width, argb4444.getWidth());
        assertEquals(height, argb4444.getHeight());
        // ARGB_4444 is deprecated and we should decode to ARGB_8888.
        assertEquals(Config.ARGB_8888, argb4444.getConfig());
        compareBitmaps(reference, argb4444, 0, true, true);

        opts.inPreferredConfig = Config.RGB_565;
        Bitmap rgb565 = BitmapFactory.decodeResource(mRes, id, opts);
        assertNotNull(rgb565);
        assertEquals(width, rgb565.getWidth());
        assertEquals(height, rgb565.getHeight());
        if (!hasAlpha) {
            assertEquals(Config.RGB_565, rgb565.getConfig());
            // Convert the RGB_565 bitmap to ARGB_8888 and test that it is similar to
            // the reference.  We lose information when decoding to 565, so there must
            // be some tolerance.  The tolerance is intentionally loose to allow us some
            // flexibility regarding if we dither and how we color convert.
            compareBitmaps(reference, rgb565.copy(Config.ARGB_8888, false), 30, true, true);
        }

        opts.inPreferredConfig = Config.ALPHA_8;
        Bitmap alpha8 = BitmapFactory.decodeResource(mRes, id, opts);
        assertNotNull(alpha8);
        assertEquals(width, reference.getWidth());
        assertEquals(height, reference.getHeight());
        if (isGray) {
            assertEquals(Config.ALPHA_8, alpha8.getConfig());
            // Convert the ALPHA_8 bitmap to ARGB_8888 and test that it is identical to
            // the reference.  We must do this manually because we are abusing ALPHA_8
            // in order to represent grayscale.
            compareBitmaps(reference, grayToARGB(alpha8), 0, true, true);
        }

        // Setting inPreferredConfig to nullptr will cause the default Config to be
        // selected, which in this case is ARGB_8888.
        opts.inPreferredConfig = null;
        Bitmap defaultBitmap = BitmapFactory.decodeResource(mRes, id, opts);
        assertNotNull(defaultBitmap);
        assertEquals(width, defaultBitmap.getWidth());
        assertEquals(height, defaultBitmap.getHeight());
        assertEquals(Config.ARGB_8888, defaultBitmap.getConfig());
        compareBitmaps(reference, defaultBitmap, 0, true, true);
    }

    private static Bitmap grayToARGB(Bitmap gray) {
        Bitmap argb = Bitmap.createBitmap(gray.getWidth(), gray.getHeight(), Config.ARGB_8888);
        for (int y = 0; y < argb.getHeight(); y++) {
            for (int x = 0; x < argb.getWidth(); x++) {
                int grayByte = Color.alpha(gray.getPixel(x, y));
                argb.setPixel(x, y, Color.rgb(grayByte, grayByte, grayByte));
            }
        }
        return argb;
    }

    private byte[] obtainArray() {
        ByteArrayOutputStream stm = new ByteArrayOutputStream();
        Options opt = new BitmapFactory.Options();
        opt.inScaled = false;
        Bitmap bitmap = BitmapFactory.decodeResource(mRes, R.drawable.start, opt);
        bitmap.compress(Bitmap.CompressFormat.JPEG, 0, stm);
        return(stm.toByteArray());
    }

    private InputStream obtainInputStream() {
        return mRes.openRawResource(R.drawable.start);
    }

    private InputStream obtainInputStream(int resId) {
        return mRes.openRawResource(resId);
    }

    private static ParcelFileDescriptor obtainParcelDescriptor(String path) throws IOException {
        File file = new File(path);
        return ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY);
    }

    private String obtainPath() throws IOException {
        return obtainPath(R.drawable.start, 0);
    }

    /*
     * Create a new file and return a path to it.
     * @param resId Original file. It will be copied into the new file.
     * @param offset Number of zeroes to write to the new file before the
     *               copied file. This allows testing decodeFileDescriptor
     *               with an offset. Must be less than or equal to 1024
     */
    private String obtainPath(int resId, long offset) throws IOException {
        File dir = InstrumentationRegistry.getTargetContext().getFilesDir();
        dir.mkdirs();
        // The suffix does not necessarily represent theactual file type.
        File file = new File(dir, "test.jpg");
        if (!file.createNewFile()) {
            if (!file.exists()) {
                fail("Failed to create new File!");
            }
        }
        InputStream is = obtainInputStream(resId);
        FileOutputStream fOutput = new FileOutputStream(file);
        byte[] dataBuffer = new byte[1024];
        // Write a bunch of zeroes before the image.
        assertTrue(offset <= 1024);
        fOutput.write(dataBuffer, 0, (int) offset);
        int readLength = 0;
        while ((readLength = is.read(dataBuffer)) != -1) {
            fOutput.write(dataBuffer, 0, readLength);
        }
        is.close();
        fOutput.close();
        return (file.getPath());
    }

    // Compare expected to actual to see if their diff is less then mseMargin.
    // lessThanMargin is to indicate whether we expect the mean square error
    // to be "less than" or "no less than".
    private static void compareBitmaps(Bitmap expected, Bitmap actual,
            int mseMargin, boolean lessThanMargin, boolean isPremultiplied) {
        final int width = expected.getWidth();
        final int height = expected.getHeight();

        assertEquals("mismatching widths", width, actual.getWidth());
        assertEquals("mismatching heights", height, actual.getHeight());
        assertEquals("mismatching configs", expected.getConfig(),
                actual.getConfig());

        double mse = 0;
        int[] expectedColors = new int [width * height];
        int[] actualColors = new int [width * height];

        // Bitmap.getPixels() returns colors with non-premultiplied ARGB values.
        expected.getPixels(expectedColors, 0, width, 0, 0, width, height);
        actual.getPixels(actualColors, 0, width, 0, 0, width, height);

        for (int row = 0; row < height; ++row) {
            for (int col = 0; col < width; ++col) {
                int idx = row * width + col;
                mse += distance(expectedColors[idx], actualColors[idx], isPremultiplied);
            }
        }
        mse /= width * height;

        if (lessThanMargin) {
            assertTrue("MSE " + mse +  "larger than the threshold: " + mseMargin,
                    mse <= mseMargin);
        } else {
            assertFalse("MSE " + mse +  "smaller than the threshold: " + mseMargin,
                    mse <= mseMargin);
        }
    }

    private static int multiplyAlpha(int color, int alpha) {
        return (color * alpha + 127) / 255;
    }

    // For the Bitmap with Alpha, multiply the Alpha values to get the effective
    // RGB colors and then compute the color-distance.
    private static double distance(int expect, int actual, boolean isPremultiplied) {
        if (isPremultiplied) {
            final int a1 = Color.alpha(actual);
            final int a2 = Color.alpha(expect);
            final int r = multiplyAlpha(Color.red(actual), a1) -
                    multiplyAlpha(Color.red(expect), a2);
            final int g = multiplyAlpha(Color.green(actual), a1) -
                    multiplyAlpha(Color.green(expect), a2);
            final int b = multiplyAlpha(Color.blue(actual), a1) -
                    multiplyAlpha(Color.blue(expect), a2);
            return r * r + g * g + b * b;
        } else {
            final int r = Color.red(actual) - Color.red(expect);
            final int g = Color.green(actual) - Color.green(expect);
            final int b = Color.blue(actual) - Color.blue(expect);
            return r * r + g * g + b * b;
        }
    }
}
