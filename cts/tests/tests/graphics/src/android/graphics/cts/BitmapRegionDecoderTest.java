/*
 * Copyright (C) 2010 The Android Open Source Project
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
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.Bitmap.Config;
import android.graphics.BitmapFactory;
import android.graphics.BitmapFactory.Options;
import android.graphics.BitmapRegionDecoder;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.ColorSpace;
import android.graphics.Rect;
import android.os.ParcelFileDescriptor;
import android.support.test.InstrumentationRegistry;
import android.support.test.filters.LargeTest;
import android.support.test.filters.SmallTest;
import android.support.test.runner.AndroidJUnit4;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileDescriptor;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;

@SmallTest
@RunWith(AndroidJUnit4.class)
public class BitmapRegionDecoderTest {
    // The test images, including baseline JPEGs and progressive JPEGs, a PNG,
    // a WEBP, a GIF and a BMP.
    private static final int[] RES_IDS = new int[] {
            R.drawable.baseline_jpeg, R.drawable.progressive_jpeg,
            R.drawable.baseline_restart_jpeg,
            R.drawable.progressive_restart_jpeg,
            R.drawable.png_test, R.drawable.webp_test,
            R.drawable.gif_test, R.drawable.bmp_test
    };
    private static final String[] NAMES_TEMP_FILES = new String[] {
            "baseline_temp.jpg", "progressive_temp.jpg", "baseline_restart_temp.jpg",
            "progressive_restart_temp.jpg", "png_temp.png", "webp_temp.webp",
            "gif_temp.gif", "bmp_temp.bmp"
    };

    // Do not change the order!
    private static final String[] ASSET_NAMES = {
            "prophoto-rgba16f.png",
            "green-p3.png",
            "red-adobergb.png",
            "green-srgb.png",
    };
    private static final ColorSpace.Named[][] ASSET_COLOR_SPACES = {
            // ARGB8888
            {
                    ColorSpace.Named.LINEAR_EXTENDED_SRGB,
                    ColorSpace.Named.DISPLAY_P3,
                    ColorSpace.Named.ADOBE_RGB,
                    ColorSpace.Named.SRGB
            },
            // RGB565
            {
                    ColorSpace.Named.SRGB,
                    ColorSpace.Named.SRGB,
                    ColorSpace.Named.SRGB,
                    ColorSpace.Named.SRGB
            }
    };

    // The width and height of the above image.
    // -1 denotes that the image format is not supported by BitmapRegionDecoder
    private static final int WIDTHS[] = new int[] {
            1280, 1280, 1280, 1280, 640, 640, -1, -1};
    private static final int HEIGHTS[] = new int[] {960, 960, 960, 960, 480, 480, -1, -1};

    // The number of test images, format of which is supported by BitmapRegionDecoder
    private static final int NUM_TEST_IMAGES = 6;

    private static final int TILE_SIZE = 256;
    private static final int SMALL_TILE_SIZE = 16;

    // Configurations for BitmapFactory.Options
    private static final Config[] COLOR_CONFIGS = new Config[] {Config.ARGB_8888,
            Config.RGB_565};
    private static final int[] SAMPLESIZES = new int[] {1, 4};

    // We allow a certain degree of discrepancy between the tile-based decoding
    // result and the regular decoding result, because the two decoders may have
    // different implementations. The allowable discrepancy is set to a mean
    // square error of 3 * (1 * 1) among the RGB values.
    private static final int MSE_MARGIN = 3 * (1 * 1);

    // MSE margin for WebP Region-Decoding for 'Config.RGB_565' is little bigger.
    private static final int MSE_MARGIN_WEB_P_CONFIG_RGB_565 = 8;

    private final int[] mExpectedColors = new int [TILE_SIZE * TILE_SIZE];
    private final int[] mActualColors = new int [TILE_SIZE * TILE_SIZE];

    private ArrayList<File> mFilesCreated = new ArrayList<>(NAMES_TEMP_FILES.length);

    private Resources mRes;

    @Before
    public void setup() {
        mRes = InstrumentationRegistry.getTargetContext().getResources();
    }

    @After
    public void teardown() {
        for (File file : mFilesCreated) {
            file.delete();
        }
    }

    @Test
    public void testNewInstanceInputStream() throws IOException {
        for (int i = 0; i < RES_IDS.length; ++i) {
            InputStream is = obtainInputStream(RES_IDS[i]);
            try {
                BitmapRegionDecoder decoder =
                        BitmapRegionDecoder.newInstance(is, false);
                assertEquals(WIDTHS[i], decoder.getWidth());
                assertEquals(HEIGHTS[i], decoder.getHeight());
            } catch (IOException e) {
                assertEquals(WIDTHS[i], -1);
                assertEquals(HEIGHTS[i], -1);
            } finally {
                if (is != null) {
                    is.close();
                }
            }
        }
    }

    @Test
    public void testNewInstanceByteArray() throws IOException {
        for (int i = 0; i < RES_IDS.length; ++i) {
            byte[] imageData = obtainByteArray(RES_IDS[i]);
            try {
                BitmapRegionDecoder decoder = BitmapRegionDecoder
                        .newInstance(imageData, 0, imageData.length, false);
                assertEquals(WIDTHS[i], decoder.getWidth());
                assertEquals(HEIGHTS[i], decoder.getHeight());
            } catch (IOException e) {
                assertEquals(WIDTHS[i], -1);
                assertEquals(HEIGHTS[i], -1);
            }
        }
    }

    @Test
    public void testNewInstanceStringAndFileDescriptor() throws IOException {
        for (int i = 0; i < RES_IDS.length; ++i) {
            String filepath = obtainPath(i);
            ParcelFileDescriptor pfd = obtainParcelDescriptor(filepath);
            FileDescriptor fd = pfd.getFileDescriptor();
            try {
                BitmapRegionDecoder decoder1 =
                        BitmapRegionDecoder.newInstance(filepath, false);
                assertEquals(WIDTHS[i], decoder1.getWidth());
                assertEquals(HEIGHTS[i], decoder1.getHeight());

                BitmapRegionDecoder decoder2 =
                        BitmapRegionDecoder.newInstance(fd, false);
                assertEquals(WIDTHS[i], decoder2.getWidth());
                assertEquals(HEIGHTS[i], decoder2.getHeight());
            } catch (IOException e) {
                assertEquals(WIDTHS[i], -1);
                assertEquals(HEIGHTS[i], -1);
            }
        }
    }

    @LargeTest
    @Test
    public void testDecodeRegionInputStream() throws IOException {
        Options opts = new BitmapFactory.Options();
        for (int i = 0; i < NUM_TEST_IMAGES; ++i) {
            for (int j = 0; j < SAMPLESIZES.length; ++j) {
                for (int k = 0; k < COLOR_CONFIGS.length; ++k) {
                    opts.inSampleSize = SAMPLESIZES[j];
                    opts.inPreferredConfig = COLOR_CONFIGS[k];

                    InputStream is1 = obtainInputStream(RES_IDS[i]);
                    BitmapRegionDecoder decoder = BitmapRegionDecoder.newInstance(is1, false);
                    InputStream is2 = obtainInputStream(RES_IDS[i]);
                    Bitmap wholeImage = BitmapFactory.decodeStream(is2, null, opts);

                    if (RES_IDS[i] == R.drawable.webp_test && COLOR_CONFIGS[k] == Config.RGB_565) {
                        compareRegionByRegion(decoder, opts, MSE_MARGIN_WEB_P_CONFIG_RGB_565,
                                              wholeImage);
                    } else {
                        compareRegionByRegion(decoder, opts, MSE_MARGIN, wholeImage);
                    }
                    wholeImage.recycle();
                }
            }
        }
    }

    @LargeTest
    @Test
    public void testDecodeRegionInputStreamInBitmap() throws IOException {
        Options opts = new BitmapFactory.Options();
        for (int i = 0; i < NUM_TEST_IMAGES; ++i) {
            for (int j = 0; j < SAMPLESIZES.length; ++j) {
                for (int k = 0; k < COLOR_CONFIGS.length; ++k) {
                    opts.inSampleSize = SAMPLESIZES[j];
                    opts.inPreferredConfig = COLOR_CONFIGS[k];
                    opts.inBitmap = null;

                    InputStream is1 = obtainInputStream(RES_IDS[i]);
                    BitmapRegionDecoder decoder = BitmapRegionDecoder.newInstance(is1, false);
                    InputStream is2 = obtainInputStream(RES_IDS[i]);
                    Bitmap wholeImage = BitmapFactory.decodeStream(is2, null, opts);

                    // setting inBitmap enables several checks within compareRegionByRegion
                    opts.inBitmap = Bitmap.createBitmap(
                            wholeImage.getWidth(), wholeImage.getHeight(), opts.inPreferredConfig);

                    if (RES_IDS[i] == R.drawable.webp_test && COLOR_CONFIGS[k] == Config.RGB_565) {
                        compareRegionByRegion(decoder, opts, MSE_MARGIN_WEB_P_CONFIG_RGB_565,
                                              wholeImage);
                    } else {
                        compareRegionByRegion(decoder, opts, MSE_MARGIN, wholeImage);
                    }
                    wholeImage.recycle();
                }
            }
        }
    }

    @LargeTest
    @Test
    public void testDecodeRegionByteArray() throws IOException {
        Options opts = new BitmapFactory.Options();
        for (int i = 0; i < NUM_TEST_IMAGES; ++i) {
            for (int j = 0; j < SAMPLESIZES.length; ++j) {
                for (int k = 0; k < COLOR_CONFIGS.length; ++k) {
                    opts.inSampleSize = SAMPLESIZES[j];
                    opts.inPreferredConfig = COLOR_CONFIGS[k];

                    byte[] imageData = obtainByteArray(RES_IDS[i]);
                    BitmapRegionDecoder decoder = BitmapRegionDecoder
                            .newInstance(imageData, 0, imageData.length, false);
                    Bitmap wholeImage = BitmapFactory.decodeByteArray(imageData,
                            0, imageData.length, opts);

                    if (RES_IDS[i] == R.drawable.webp_test && COLOR_CONFIGS[k] == Config.RGB_565) {
                        compareRegionByRegion(decoder, opts, MSE_MARGIN_WEB_P_CONFIG_RGB_565,
                                              wholeImage);
                    } else {
                        compareRegionByRegion(decoder, opts, MSE_MARGIN, wholeImage);
                    }
                    wholeImage.recycle();
                }
            }
        }
    }

    @LargeTest
    @Test
    public void testDecodeRegionStringAndFileDescriptor() throws IOException {
        Options opts = new BitmapFactory.Options();
        for (int i = 0; i < NUM_TEST_IMAGES; ++i) {
            String filepath = obtainPath(i);
            for (int j = 0; j < SAMPLESIZES.length; ++j) {
                for (int k = 0; k < COLOR_CONFIGS.length; ++k) {
                    opts.inSampleSize = SAMPLESIZES[j];
                    opts.inPreferredConfig = COLOR_CONFIGS[k];

                    BitmapRegionDecoder decoder =
                        BitmapRegionDecoder.newInstance(filepath, false);
                    Bitmap wholeImage = BitmapFactory.decodeFile(filepath, opts);
                    if (RES_IDS[i] == R.drawable.webp_test && COLOR_CONFIGS[k] == Config.RGB_565) {
                        compareRegionByRegion(decoder, opts, MSE_MARGIN_WEB_P_CONFIG_RGB_565,
                                              wholeImage);
                    } else {
                        compareRegionByRegion(decoder, opts, MSE_MARGIN, wholeImage);
                    }

                    ParcelFileDescriptor pfd1 = obtainParcelDescriptor(filepath);
                    FileDescriptor fd1 = pfd1.getFileDescriptor();
                    decoder = BitmapRegionDecoder.newInstance(fd1, false);
                    ParcelFileDescriptor pfd2 = obtainParcelDescriptor(filepath);
                    FileDescriptor fd2 = pfd2.getFileDescriptor();
                    if (RES_IDS[i] == R.drawable.webp_test && COLOR_CONFIGS[k] == Config.RGB_565) {
                        compareRegionByRegion(decoder, opts, MSE_MARGIN_WEB_P_CONFIG_RGB_565,
                                              wholeImage);
                    } else {
                        compareRegionByRegion(decoder, opts, MSE_MARGIN, wholeImage);
                    }
                    wholeImage.recycle();
                }
            }
        }
    }

    @Test
    public void testRecycle() throws IOException {
        InputStream is = obtainInputStream(RES_IDS[0]);
        BitmapRegionDecoder decoder = BitmapRegionDecoder.newInstance(is, false);
        decoder.recycle();
        assertTrue(decoder.isRecycled());
        try {
            decoder.getWidth();
            fail("Should throw an exception!");
        } catch (Exception e) {
        }

        try {
            decoder.getHeight();
            fail("Should throw an exception!");
        } catch (Exception e) {
        }

        Rect rect = new Rect(0, 0, WIDTHS[0], HEIGHTS[0]);
        BitmapFactory.Options opts = new BitmapFactory.Options();
        try {
            decoder.decodeRegion(rect, opts);
            fail("Should throw an exception!");
        } catch (Exception e) {
        }
    }

    // The documentation for BitmapRegionDecoder guarantees that, when reusing a
    // bitmap, "the provided Bitmap's width, height, and Bitmap.Config will not
    // be changed".  If the inBitmap is too small, decoded content will be
    // clipped into inBitmap.  Here we test that:
    //     (1) If inBitmap is specified, it is always used.
    //     (2) The width, height, and Config of inBitmap are never changed.
    //     (3) All of the pixels decoded into inBitmap exactly match the pixels
    //         of a decode where inBitmap is NULL.
    @LargeTest
    @Test
    public void testInBitmapReuse() throws IOException {
        Options defaultOpts = new BitmapFactory.Options();
        Options reuseOpts = new BitmapFactory.Options();
        Rect subset = new Rect(0, 0, TILE_SIZE, TILE_SIZE);

        for (int i = 0; i < NUM_TEST_IMAGES; i++) {
            InputStream is = obtainInputStream(RES_IDS[i]);
            BitmapRegionDecoder decoder = BitmapRegionDecoder.newInstance(is, false);
            for (int j = 0; j < SAMPLESIZES.length; j++) {
                int sampleSize = SAMPLESIZES[j];
                defaultOpts.inSampleSize = sampleSize;
                reuseOpts.inSampleSize = sampleSize;

                // We don't need to worry about rounding here because sampleSize
                // divides evenly into TILE_SIZE.
                assertEquals(0, TILE_SIZE % sampleSize);
                int scaledDim = TILE_SIZE / sampleSize;
                int chunkSize = scaledDim / 2;
                for (int k = 0; k < COLOR_CONFIGS.length; k++) {
                    Config config = COLOR_CONFIGS[k];
                    defaultOpts.inPreferredConfig = config;
                    reuseOpts.inPreferredConfig = config;

                    // For both the width and the height of inBitmap, we test three
                    // interesting cases:
                    // (1) inBitmap dimension is smaller than scaledDim.  The decoded
                    //     pixels that fit inside inBitmap should exactly match the
                    //     corresponding decoded pixels from the same region decode,
                    //     performed without an inBitmap.  The pixels that do not fit
                    //     inside inBitmap should be clipped.
                    // (2) inBitmap dimension matches scaledDim.  After the decode,
                    //     the pixels and dimensions of inBitmap should exactly match
                    //     those of the result bitmap of the same region decode,
                    //     performed without an inBitmap.
                    // (3) inBitmap dimension is larger than scaledDim.  After the
                    //     decode, inBitmap should contain decoded pixels for the
                    //     entire region, exactly matching the decoded pixels
                    //     produced when inBitmap is not specified.  The additional
                    //     pixels in inBitmap are left the same as before the decode.
                    for (int w = chunkSize; w <= 3 * chunkSize; w += chunkSize) {
                        for (int h = chunkSize; h <= 3 * chunkSize; h += chunkSize) {
                            // Decode reusing inBitmap.
                            reuseOpts.inBitmap = Bitmap.createBitmap(w, h, config);
                            Bitmap reuseResult = decoder.decodeRegion(subset, reuseOpts);
                            assertSame(reuseOpts.inBitmap, reuseResult);
                            assertEquals(reuseResult.getWidth(), w);
                            assertEquals(reuseResult.getHeight(), h);
                            assertEquals(reuseResult.getConfig(), config);

                            // Decode into a new bitmap.
                            Bitmap defaultResult = decoder.decodeRegion(subset, defaultOpts);
                            assertEquals(defaultResult.getWidth(), scaledDim);
                            assertEquals(defaultResult.getHeight(), scaledDim);

                            // Ensure that the decoded pixels of reuseResult and defaultResult
                            // are identical.
                            int cropWidth = Math.min(w, scaledDim);
                            int cropHeight = Math.min(h, scaledDim);
                            Rect crop = new Rect(0 ,0, cropWidth, cropHeight);
                            Bitmap reuseCropped = cropBitmap(reuseResult, crop);
                            Bitmap defaultCropped = cropBitmap(defaultResult, crop);
                            compareBitmaps(reuseCropped, defaultCropped, 0, true);
                        }
                    }
                }
            }
        }
    }

    @Test
    public void testDecodeHardwareBitmap() throws IOException {
        BitmapFactory.Options options = new BitmapFactory.Options();
        options.inPreferredConfig = Bitmap.Config.HARDWARE;
        InputStream is = obtainInputStream(RES_IDS[0]);
        BitmapRegionDecoder decoder = BitmapRegionDecoder.newInstance(is, false);
        Bitmap hardwareBitmap = decoder.decodeRegion(new Rect(0, 0, 10, 10), options);
        assertNotNull(hardwareBitmap);
        // Test that checks that correct bitmap was obtained is in uirendering/HardwareBitmapTests
        assertEquals(Config.HARDWARE, hardwareBitmap.getConfig());
    }

    @Test
    public void testOutColorType() throws IOException {
        Options opts = new BitmapFactory.Options();
        for (int i = 0; i < NUM_TEST_IMAGES; ++i) {
            for (int j = 0; j < SAMPLESIZES.length; ++j) {
                for (int k = 0; k < COLOR_CONFIGS.length; ++k) {
                    opts.inSampleSize = SAMPLESIZES[j];
                    opts.inPreferredConfig = COLOR_CONFIGS[k];

                    InputStream is1 = obtainInputStream(RES_IDS[i]);
                    BitmapRegionDecoder decoder = BitmapRegionDecoder.newInstance(is1, false);
                    Bitmap region = decoder.decodeRegion(
                            new Rect(0, 0, TILE_SIZE, TILE_SIZE), opts);
                    decoder.recycle();

                    assertSame(opts.inPreferredConfig, opts.outConfig);
                    assertSame(opts.outConfig, region.getConfig());
                    region.recycle();
                }
            }
        }
    }

    @Test
    public void testOutColorSpace() throws IOException {
        Options opts = new BitmapFactory.Options();
        for (int i = 0; i < ASSET_NAMES.length; i++) {
            for (int j = 0; j < SAMPLESIZES.length; ++j) {
                for (int k = 0; k < COLOR_CONFIGS.length; ++k) {
                    opts.inPreferredConfig = COLOR_CONFIGS[k];

                    String assetName = ASSET_NAMES[i];
                    InputStream is1 = obtainInputStream(assetName);
                    BitmapRegionDecoder decoder = BitmapRegionDecoder.newInstance(is1, false);
                    Bitmap region = decoder.decodeRegion(
                            new Rect(0, 0, SMALL_TILE_SIZE, SMALL_TILE_SIZE), opts);
                    decoder.recycle();

                    ColorSpace expected = ColorSpace.get(ASSET_COLOR_SPACES[k][i]);
                    assertSame(expected, opts.outColorSpace);
                    assertSame(expected, region.getColorSpace());
                    region.recycle();
                }
            }
        }
    }

    @Test
    public void testReusedColorSpace() throws IOException {
        Bitmap b = Bitmap.createBitmap(SMALL_TILE_SIZE, SMALL_TILE_SIZE, Config.ARGB_8888,
                false, ColorSpace.get(ColorSpace.Named.ADOBE_RGB));

        Options opts = new BitmapFactory.Options();
        opts.inBitmap = b;

        // sRGB
        BitmapRegionDecoder decoder = BitmapRegionDecoder.newInstance(
                obtainInputStream(ASSET_NAMES[3]), false);
        Bitmap region = decoder.decodeRegion(
                new Rect(0, 0, SMALL_TILE_SIZE, SMALL_TILE_SIZE), opts);
        decoder.recycle();

        assertEquals(ColorSpace.get(ColorSpace.Named.SRGB), region.getColorSpace());

        // DisplayP3
        decoder = BitmapRegionDecoder.newInstance(obtainInputStream(ASSET_NAMES[1]), false);
        region = decoder.decodeRegion(new Rect(0, 0, SMALL_TILE_SIZE, SMALL_TILE_SIZE), opts);
        decoder.recycle();

        assertEquals(ColorSpace.get(ColorSpace.Named.DISPLAY_P3), region.getColorSpace());
    }

    @Test
    public void testInColorSpace() throws IOException {
        Options opts = new BitmapFactory.Options();
        for (int i = 0; i < NUM_TEST_IMAGES; ++i) {
            for (int j = 0; j < SAMPLESIZES.length; ++j) {
                opts.inSampleSize = SAMPLESIZES[j];
                opts.inPreferredColorSpace = ColorSpace.get(ColorSpace.Named.DISPLAY_P3);

                InputStream is1 = obtainInputStream(RES_IDS[i]);
                BitmapRegionDecoder decoder = BitmapRegionDecoder.newInstance(is1, false);
                Bitmap region = decoder.decodeRegion(new Rect(0, 0, TILE_SIZE, TILE_SIZE), opts);
                decoder.recycle();

                assertSame(ColorSpace.get(ColorSpace.Named.DISPLAY_P3), opts.outColorSpace);
                assertSame(opts.outColorSpace, region.getColorSpace());
                region.recycle();
            }
        }
    }

    @Test
    public void testInColorSpaceRGBA16F() throws IOException {
        Options opts = new BitmapFactory.Options();
        opts.inPreferredColorSpace = ColorSpace.get(ColorSpace.Named.ADOBE_RGB);

        InputStream is1 = obtainInputStream(ASSET_NAMES[0]); // ProPhoto 16 bit
        BitmapRegionDecoder decoder = BitmapRegionDecoder.newInstance(is1, false);
        Bitmap region = decoder.decodeRegion(new Rect(0, 0, SMALL_TILE_SIZE, SMALL_TILE_SIZE), opts);
        decoder.recycle();

        assertSame(ColorSpace.get(ColorSpace.Named.LINEAR_EXTENDED_SRGB), region.getColorSpace());
        region.recycle();
    }

    @Test
    public void testInColorSpace565() throws IOException {
        Options opts = new BitmapFactory.Options();
        opts.inPreferredConfig = Config.RGB_565;
        opts.inPreferredColorSpace = ColorSpace.get(ColorSpace.Named.ADOBE_RGB);

        InputStream is1 = obtainInputStream(ASSET_NAMES[1]); // Display P3
        BitmapRegionDecoder decoder = BitmapRegionDecoder.newInstance(is1, false);
        Bitmap region = decoder.decodeRegion(new Rect(0, 0, SMALL_TILE_SIZE, SMALL_TILE_SIZE), opts);
        decoder.recycle();

        assertSame(ColorSpace.get(ColorSpace.Named.SRGB), region.getColorSpace());
        region.recycle();
    }

    @Test(expected = IllegalArgumentException.class)
    public void testInColorSpaceNotRgb() throws IOException {
        Options opts = new BitmapFactory.Options();
        opts.inPreferredColorSpace = ColorSpace.get(ColorSpace.Named.CIE_LAB);
        InputStream is1 = obtainInputStream(RES_IDS[0]);
        BitmapRegionDecoder decoder = BitmapRegionDecoder.newInstance(is1, false);
        Bitmap region = decoder.decodeRegion(new Rect(0, 0, TILE_SIZE, TILE_SIZE), opts);
    }

    @Test(expected = IllegalArgumentException.class)
    public void testInColorSpaceNoTransferParameters() throws IOException {
        Options opts = new BitmapFactory.Options();
        opts.inPreferredColorSpace = ColorSpace.get(ColorSpace.Named.EXTENDED_SRGB);
        InputStream is1 = obtainInputStream(RES_IDS[0]);
        BitmapRegionDecoder decoder = BitmapRegionDecoder.newInstance(is1, false);
        Bitmap region = decoder.decodeRegion(new Rect(0, 0, TILE_SIZE, TILE_SIZE), opts);
    }

    @Test(expected = IllegalArgumentException.class)
    public void testHardwareBitmapIn() throws IOException {
        Options opts = new BitmapFactory.Options();
        Bitmap bitmap = Bitmap.createBitmap(TILE_SIZE, TILE_SIZE, Config.ARGB_8888)
                .copy(Config.HARDWARE, false);
        opts.inBitmap = bitmap;
        InputStream is = obtainInputStream(RES_IDS[0]);
        BitmapRegionDecoder decoder = BitmapRegionDecoder.newInstance(is, false);
        decoder.decodeRegion(new Rect(0, 0, TILE_SIZE, TILE_SIZE), opts);
    }

    private void compareRegionByRegion(BitmapRegionDecoder decoder,
            Options opts, int mseMargin, Bitmap wholeImage) {
        int width = decoder.getWidth();
        int height = decoder.getHeight();
        Rect rect = new Rect(0, 0, width, height);
        int numCols = (width + TILE_SIZE - 1) / TILE_SIZE;
        int numRows = (height + TILE_SIZE - 1) / TILE_SIZE;
        Bitmap actual;
        Bitmap expected;

        for (int i = 0; i < numCols; ++i) {
            for (int j = 0; j < numRows; ++j) {
                Rect rect1 = new Rect(i * TILE_SIZE, j * TILE_SIZE,
                        (i + 1) * TILE_SIZE, (j + 1) * TILE_SIZE);
                rect1.intersect(rect);
                actual = decoder.decodeRegion(rect1, opts);
                int left = rect1.left / opts.inSampleSize;
                int top = rect1.top / opts.inSampleSize;
                if (opts.inBitmap != null) {
                    // bitmap reuse path - ensure reuse worked
                    assertSame(opts.inBitmap, actual);
                    int currentWidth = rect1.width() / opts.inSampleSize;
                    int currentHeight = rect1.height() / opts.inSampleSize;
                    Rect actualRect = new Rect(0, 0, currentWidth, currentHeight);
                    // crop 'actual' to the size to be tested (and avoid recycling inBitmap)
                    actual = cropBitmap(actual, actualRect);
                }
                Rect expectedRect = new Rect(left, top, left + actual.getWidth(),
                        top + actual.getHeight());
                expected = cropBitmap(wholeImage, expectedRect);
                compareBitmaps(expected, actual, mseMargin, true);
                actual.recycle();
                expected.recycle();
            }
        }
    }

    private static Bitmap cropBitmap(Bitmap wholeImage, Rect rect) {
        Bitmap cropped = Bitmap.createBitmap(rect.width(), rect.height(),
                wholeImage.getConfig());
        Canvas canvas = new Canvas(cropped);
        Rect dst = new Rect(0, 0, rect.width(), rect.height());
        canvas.drawBitmap(wholeImage, rect, dst, null);
        return cropped;
    }

    private InputStream obtainInputStream(int resId) {
        return mRes.openRawResource(resId);
    }

    private InputStream obtainInputStream(String assetName) throws IOException {
        return mRes.getAssets().open(assetName);
    }

    private byte[] obtainByteArray(int resId) throws IOException {
        InputStream is = obtainInputStream(resId);
        ByteArrayOutputStream os = new ByteArrayOutputStream();
        byte[] buffer = new byte[1024];
        int readLength;
        while ((readLength = is.read(buffer)) != -1) {
            os.write(buffer, 0, readLength);
        }
        byte[] data = os.toByteArray();
        is.close();
        os.close();
        return data;
    }

    private String obtainPath(int idx) throws IOException {
        File dir = InstrumentationRegistry.getTargetContext().getFilesDir();
        dir.mkdirs();
        File file = new File(dir, NAMES_TEMP_FILES[idx]);
        InputStream is = obtainInputStream(RES_IDS[idx]);
        FileOutputStream fOutput = new FileOutputStream(file);
        mFilesCreated.add(file);
        byte[] dataBuffer = new byte[1024];
        int readLength = 0;
        while ((readLength = is.read(dataBuffer)) != -1) {
            fOutput.write(dataBuffer, 0, readLength);
        }
        is.close();
        fOutput.close();
        return (file.getPath());
    }

    private static ParcelFileDescriptor obtainParcelDescriptor(String path)
            throws IOException {
        File file = new File(path);
        return ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY);
    }


    // Compare expected to actual to see if their diff is less then mseMargin.
    // lessThanMargin is to indicate whether we expect the diff to be
    // "less than" or "no less than".
    private void compareBitmaps(Bitmap expected, Bitmap actual,
            int mseMargin, boolean lessThanMargin) {
        assertEquals("mismatching widths", expected.getWidth(),
                actual.getWidth());
        assertEquals("mismatching heights", expected.getHeight(),
                actual.getHeight());

        double mse = 0;
        int width = expected.getWidth();
        int height = expected.getHeight();
        int[] expectedColors;
        int[] actualColors;
        if (width == TILE_SIZE && height == TILE_SIZE) {
            expectedColors = mExpectedColors;
            actualColors = mActualColors;
        } else {
            expectedColors = new int [width * height];
            actualColors = new int [width * height];
        }

        expected.getPixels(expectedColors, 0, width, 0, 0, width, height);
        actual.getPixels(actualColors, 0, width, 0, 0, width, height);

        for (int row = 0; row < height; ++row) {
            for (int col = 0; col < width; ++col) {
                int idx = row * width + col;
                mse += distance(expectedColors[idx], actualColors[idx]);
            }
        }
        mse /= width * height;

        if (lessThanMargin) {
            assertTrue("MSE too large for normal case: " + mse,
                    mse <= mseMargin);
        } else {
            assertFalse("MSE too small for abnormal case: " + mse,
                    mse <= mseMargin);
        }
    }

    private static double distance(int exp, int actual) {
        int r = Color.red(actual) - Color.red(exp);
        int g = Color.green(actual) - Color.green(exp);
        int b = Color.blue(actual) - Color.blue(exp);
        return r * r + g * g + b * b;
    }
}
