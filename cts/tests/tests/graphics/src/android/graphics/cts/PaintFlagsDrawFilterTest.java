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
import static org.junit.Assert.assertTrue;

import android.graphics.Bitmap;
import android.graphics.Bitmap.Config;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Paint.Align;
import android.graphics.PaintFlagsDrawFilter;
import android.graphics.Rect;
import android.support.test.filters.SmallTest;
import android.support.test.runner.AndroidJUnit4;

import org.junit.Test;
import org.junit.runner.RunWith;

@SmallTest
@RunWith(AndroidJUnit4.class)
public class PaintFlagsDrawFilterTest {
    private static final float TEXT_SIZE = 20;
    private static final float TEXT_X = 50;
    private static final float TEXT_Y = 50;
    private static final String TEXT = "Test";
    private static final int BITMAP_WIDTH = 100;
    private static final int BITMAP_HEIGHT = 100;

    private float mTextWidth;

    @Test
    public void testPaintFlagsDrawFilter() {
        Bitmap bitmapWithoutFilter = drawText(null);

        PaintFlagsDrawFilter filter = new PaintFlagsDrawFilter(Paint.UNDERLINE_TEXT_FLAG, 0);
        Bitmap bitmapWithFilter = drawText(filter);

        Bitmap combined = delta(bitmapWithoutFilter, bitmapWithFilter);
        verifyUnderline(combined);
    }

    private Bitmap drawText(PaintFlagsDrawFilter filter) {
        Paint p = new Paint(Paint.UNDERLINE_TEXT_FLAG);
        p.setColor(Color.RED);
        p.setTextSize(TEXT_SIZE);
        p.setTextAlign(Align.CENTER);
        mTextWidth = p.measureText(TEXT);
        Bitmap b = Bitmap.createBitmap(BITMAP_WIDTH, BITMAP_HEIGHT, Config.ARGB_8888);
        Canvas c = new Canvas(b);
        c.setDrawFilter(filter);
        c.drawColor(Color.BLACK);
        c.drawText(TEXT, TEXT_X, TEXT_Y, p);
        return b;
    }

    private Bitmap delta(Bitmap bitmapWithoutFilter, Bitmap bitmapWithFilter) {
        Bitmap combinedBitmap = Bitmap.createBitmap(BITMAP_WIDTH, BITMAP_HEIGHT, Config.ARGB_8888);
        combinedBitmap.eraseColor(Color.BLACK);
        int pixelWithoutFilter;
        int pixelWithFilter;
        for (int i = 0; i < BITMAP_WIDTH; i++) {
            for (int j = 0; j < BITMAP_HEIGHT; j++) {
                pixelWithoutFilter = bitmapWithoutFilter.getPixel(i, j);
                pixelWithFilter = bitmapWithFilter.getPixel(i, j);
                if (pixelWithoutFilter != pixelWithFilter) {
                    assertEquals(Color.RED, pixelWithoutFilter);
                    assertEquals(Color.BLACK, pixelWithFilter);
                    combinedBitmap.setPixel(i, j, Color.RED);
                }
            }
        }
        return combinedBitmap;
    }

    private void verifyUnderline(Bitmap bitmap) {
        // Find smallest rectangle containing all RED pixels
        Rect rect = new Rect(BITMAP_WIDTH, BITMAP_HEIGHT, 0, 0);
        for (int y = 0; y < BITMAP_HEIGHT; y++) {
            for (int x = 0; x < BITMAP_WIDTH; x++) {
                int pixel = bitmap.getPixel(x, y);
                if (pixel == Color.RED) {
                    rect.left = Math.min(rect.left, x);
                    rect.right = Math.max(rect.right, x);
                    rect.top = Math.min(rect.top, y);
                    rect.bottom = Math.max(rect.bottom, y);
                }
            }
        }
        // underline is at least one pixel high
        assertTrue(rect.top <= rect.bottom);
        // underline is roughly the same length at the text (5% tolerance)
        assertEquals(mTextWidth, rect.right - rect.left, mTextWidth * 0.053);
        // underline is under the text or at least at the bottom of it
        assertTrue(rect.top >= TEXT_Y);
    }

    // Tests that FILTER_BITMAP_FLAG is handled properly.
    @Test
    public void testPaintFlagsDrawFilter2() {
        // Create a bitmap with alternating black and white pixels.
        int kWidth = 5;
        int kHeight = 5;
        int colors[] = new int [] { Color.WHITE, Color.BLACK };
        int k = 0;
        Bitmap grid = Bitmap.createBitmap(kWidth, kHeight, Config.ARGB_8888);
        for (int i = 0; i < kWidth; ++i) {
            for (int j = 0; j < kHeight; ++j) {
                grid.setPixel(i, j, colors[k]);
                k = (k + 1) % 2;
            }
        }

        // Setup a scaled canvas for drawing the bitmap, with and without FILTER_BITMAP_FLAG set.
        // When the flag is set, there will be gray pixels. When the flag is not set, all pixels
        // will be either black or white.
        int kScale = 5;
        Bitmap dst = Bitmap.createBitmap(kWidth * kScale, kHeight * kScale, Config.ARGB_8888);
        Canvas canvas = new Canvas(dst);
        canvas.scale(kScale, kScale);

        // Drawn without FILTER_BITMAP_FLAG, all pixels will be black or white.
        Paint simplePaint = new Paint();
        canvas.drawBitmap(grid, 0, 0, simplePaint);

        verifyContainsOnlyBlackAndWhite(dst);

        // Drawn with FILTER_BITMAP_FLAG, some pixels will be somewhere in between.
        Paint filterBitmapPaint = new Paint(Paint.FILTER_BITMAP_FLAG);
        canvas.drawBitmap(grid, 0, 0, filterBitmapPaint);

        verifyContainsNonBW(dst);

        // Drawing with a paint that FILTER_BITMAP_FLAG set and a DrawFilter that removes
        // FILTER_BITMAP_FLAG should remove the effect of the flag, resulting in all pixels being
        // either black or white.
        canvas.setDrawFilter(new PaintFlagsDrawFilter(Paint.FILTER_BITMAP_FLAG, 0));
        canvas.drawBitmap(grid, 0, 0, filterBitmapPaint);

        verifyContainsOnlyBlackAndWhite(dst);

        // Likewise, drawing with a DrawFilter that sets FILTER_BITMAP_FLAG should filter,
        // resulting in gray pixels.
        canvas.setDrawFilter(new PaintFlagsDrawFilter(0, Paint.FILTER_BITMAP_FLAG));
        canvas.drawBitmap(grid, 0, 0, simplePaint);

        verifyContainsNonBW(dst);
    }

    // Assert that at least one pixel is neither black nor white. This is used to verify that
    // filtering was done, since the original bitmap only contained black and white pixels.
    private void verifyContainsNonBW(Bitmap bitmap) {
        for (int i = 0; i < bitmap.getWidth(); ++i) {
            for (int j = 0; j < bitmap.getHeight(); ++j) {
                int color = bitmap.getPixel(i, j);
                if (color != Color.BLACK && color != Color.WHITE) {
                    // Filtering must have been done.
                    return;
                }
            }
        }
        // Filtering did not happen.
        assertTrue(false);
    }

    // Assert that every pixel is either black or white. Used to verify that no filtering was
    // done, since the original bitmap contained only black and white pixels.
    private void verifyContainsOnlyBlackAndWhite(Bitmap bitmap) {
        for (int i = 0; i < bitmap.getWidth(); ++i) {
            for (int j = 0; j < bitmap.getHeight(); ++j) {
                int color = bitmap.getPixel(i, j);
                assertTrue(color == Color.BLACK || color == Color.WHITE);
            }
        }
    }
}
