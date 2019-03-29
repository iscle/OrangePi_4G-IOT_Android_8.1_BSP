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
package android.uirendering.cts.testclasses;

import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.PaintFlagsDrawFilter;
import android.support.test.filters.MediumTest;
import android.support.test.runner.AndroidJUnit4;
import android.uirendering.cts.bitmapverifiers.BitmapVerifier;
import android.uirendering.cts.bitmapverifiers.ColorVerifier;
import android.uirendering.cts.bitmapverifiers.PerPixelBitmapVerifier;
import android.uirendering.cts.testinfrastructure.ActivityTestBase;
import android.uirendering.cts.testinfrastructure.CanvasClient;

import org.junit.Test;
import org.junit.runner.RunWith;

@MediumTest
@RunWith(AndroidJUnit4.class)
public class BitmapFilterTests extends ActivityTestBase {
    private static final int WHITE_WEIGHT = 255 * 3;
    private enum FilterEnum {
        // Creates Paint object that will have bitmap filtering
        PAINT_FILTER,
        // First uses a Paint object with bitmap filtering, then uses canvas.setDrawFilter to remove
        // the bitmap filtering
        REMOVE_FILTER,
        // Sets DrawFilter to use Paint.FILTER_BITMAP_FLAG
        ADD_FILTER
    }

    private static final BitmapVerifier BLACK_WHITE_ONLY_VERIFIER
            = new PerPixelBitmapVerifier(PerPixelBitmapVerifier.DEFAULT_THRESHOLD, 0.99f) {
        @Override
        protected boolean verifyPixel(int x, int y, int color) {
            int weight = Color.red(color) + Color.blue(color) + Color.green(color);
            return weight < DEFAULT_THRESHOLD // is approx Color.BLACK
                    || weight > WHITE_WEIGHT - DEFAULT_THRESHOLD; // is approx Color.WHITE
        }
    };
    private static final BitmapVerifier GREY_ONLY_VERIFIER
            = new ColorVerifier(Color.argb(255, 127, 127, 127),
            150); // content will be entirely grey, for a fairly wide range of grey
    private static final BitmapVerifier GREY_PARTIAL_VERIFIER
            = new ColorVerifier(Color.argb(255, 127, 127, 127),
            300, 0.8f); // content will be mostly grey, for a wide range of grey


    private static Bitmap createGridBitmap(int width, int height) {
        Bitmap bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
        for (int i = 0 ; i < width ; i++) {
            for (int j = 0 ; j < height ; j++) {
                boolean isWhite = (i + j * width) % 2 == 0;
                bitmap.setPixel(i, j, isWhite ? Color.WHITE : Color.BLACK);
            }
        }
        return bitmap;
    }

    private static final int SMALL_GRID_SIZE = 5;
    private Bitmap mSmallGridBitmap = createGridBitmap(SMALL_GRID_SIZE, SMALL_GRID_SIZE);

    // samples will occur in the middle of 4 pixels, 2 white
    // and 2 black, and thus each be close to x7F7F7F grey
    private static final int BIG_GRID_SIZE = TEST_WIDTH * 2;
    private Bitmap mBigGridBitmap = createGridBitmap(BIG_GRID_SIZE, BIG_GRID_SIZE);

    @Test
    public void testPaintFilterScaleUp() {
        runScaleTest(FilterEnum.PAINT_FILTER, true);
    }

    @Test
    public void testPaintFilterScaleDown() {
        runScaleTest(FilterEnum.PAINT_FILTER, false);
    }

    @Test
    public void testDrawFilterRemoveFilterScaleUp() {
        runScaleTest(FilterEnum.REMOVE_FILTER, true);
    }

    @Test
    public void testDrawFilterRemoveFilterScaleDown() {
        runScaleTest(FilterEnum.REMOVE_FILTER, false);
    }

    @Test
    public void testDrawFilterScaleUp() {
        runScaleTest(FilterEnum.ADD_FILTER, true);
    }

    @Test
    public void testDrawFilterScaleDown() {
        runScaleTest(FilterEnum.ADD_FILTER, false);
    }

    private void runScaleTest(final FilterEnum filterEnum, final boolean scaleUp) {
        final int gridWidth = scaleUp ? SMALL_GRID_SIZE : BIG_GRID_SIZE;
        final Paint paint = new Paint(filterEnum.equals(FilterEnum.ADD_FILTER) ?
                0 : Paint.FILTER_BITMAP_FLAG);

        CanvasClient canvasClient = (canvas, width, height) -> {
            canvas.scale(1.0f * width / gridWidth, 1.0f * height / gridWidth);
            if (filterEnum.equals(FilterEnum.ADD_FILTER)) {
                canvas.setDrawFilter(new PaintFlagsDrawFilter(0, Paint.FILTER_BITMAP_FLAG));
            } else if (filterEnum.equals(FilterEnum.REMOVE_FILTER)) {
                canvas.setDrawFilter(new PaintFlagsDrawFilter(Paint.FILTER_BITMAP_FLAG, 0));
            }
            canvas.drawBitmap(scaleUp ? mSmallGridBitmap : mBigGridBitmap, 0, 0, paint);
            canvas.setDrawFilter(null);
        };
        createTest()
                // Picture does not support PaintFlagsDrawFilter
                .addCanvasClientWithoutUsingPicture(canvasClient)
                .runWithVerifier(getVerifierForTest(filterEnum, scaleUp));
    }

    private static BitmapVerifier getVerifierForTest(FilterEnum filterEnum, boolean scaleUp) {
        if (filterEnum.equals(FilterEnum.REMOVE_FILTER)) {
            // filtering disabled, so only black and white pixels will come through
            return BLACK_WHITE_ONLY_VERIFIER;
        }
        // if scaling up, output pixels may have single source to sample from,
        // will only be *mostly* grey.
        return scaleUp ? GREY_PARTIAL_VERIFIER : GREY_ONLY_VERIFIER;
    }
}
