/*
 * Copyright (C) 2016 The Android Open Source Project
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
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Point;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.graphics.PorterDuffXfermode;
import android.graphics.drawable.ColorDrawable;
import android.support.test.filters.LargeTest;
import android.uirendering.cts.bitmapverifiers.SamplePointVerifier;
import android.uirendering.cts.testinfrastructure.ActivityTestBase;
import android.uirendering.cts.testinfrastructure.CanvasClient;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

import java.util.List;

@LargeTest // Temporarily hidden from presubmit
@RunWith(Parameterized.class)
public class ColorFilterAlphaTest extends ActivityTestBase {
    // We care about one point in each of the four rectangles of different alpha values, as well as
    // the area outside the rectangles

    public static final int FILTER_COLOR = 0xFFBB0000;

    private static final Point[] TEST_POINTS = new Point[] {
            new Point(9, 45),
            new Point(27, 45),
            new Point(45, 45),
            new Point(63, 45),
            new Point(81, 45)
    };

    private static Object[][] MODES_AND_EXPECTED_COLORS = new Object[][] {
        { PorterDuff.Mode.DST, new int[] {
                0xFFE6E6E6, 0xFFCCCCCC, 0xFFB3B3B3, 0xFF999999, 0xFFFFFFFF } },

        { PorterDuff.Mode.SRC_OVER, new int[] {
                0xFFBB0000, 0xFFBB0000, 0xFFBB0000, 0xFFBB0000, 0xFFBB0000 } },

        { PorterDuff.Mode.DST_OVER, new int[] {
                0xFFAF1A1A, 0xFFA33333, 0xFF984D4D, 0xFF8B6666, 0xFFBB0000 } },

        { PorterDuff.Mode.SRC_IN, new int[] {
                0xFFF1CCCC, 0xFFE49999, 0xFFD66666, 0xFFC83333, 0xFFFFFFFF } },

        { PorterDuff.Mode.DST_IN, new int[] {
                0xFFE6E6E6, 0xFFCCCCCC, 0xFFB3B3B3, 0xFF999999, 0xFFFFFFFF } },

        { PorterDuff.Mode.SRC_OUT, new int[] {
                0xFFC83333, 0xFFD66666, 0xFFE49999, 0xFFF1CCCC, 0xFFBB0000 } },

        { PorterDuff.Mode.DST_OUT, new int[] {
                0xFFFFFFFF, 0xFFFFFFFF, 0xFFFFFFFF, 0xFFFFFFFF, 0xFFFFFFFF } },

        { PorterDuff.Mode.SRC_ATOP, new int[] {
                0xFFF1CCCC, 0xFFE49999, 0xFFD66666, 0xFFC93333, 0xFFFFFFFF } },

        { PorterDuff.Mode.DST_ATOP, new int[] {
                0xFFB01A1A, 0xFFA33333, 0xFF984D4D, 0xFF8B6666, 0xFFBB0000 } },

        { PorterDuff.Mode.XOR, new int[] {
                0xFFC93333, 0xFFD66666, 0xFFE49999, 0xFFF1CCCC, 0xFFBB0000 } },

        { PorterDuff.Mode.MULTIPLY, new int[] {
                0xFFDFCCCC, 0xFFBE9999, 0xFF9E6666, 0xFF7E3333, 0xFFFFFFFF } },

        { PorterDuff.Mode.SCREEN, new int[] {
                0xFFC21A1A, 0xFFC93333, 0xFFD04D4D, 0xFFD66666, 0xFFBB0000 } },
    };

    @Parameterized.Parameters(name = "{0}")
    public static List<XfermodeTest.Config> configs() {
        return XfermodeTest.configs(MODES_AND_EXPECTED_COLORS);
    }

    private final XfermodeTest.Config mConfig;

    public ColorFilterAlphaTest(XfermodeTest.Config config) {
        mConfig = config;
    }

    private static final int[] BLOCK_COLORS = new int[] {
            0x33808080,
            0x66808080,
            0x99808080,
            0xCC808080,
            0x00000000
    };

    private static Bitmap createMultiRectBitmap() {
        Bitmap bitmap = Bitmap.createBitmap(TEST_WIDTH, TEST_HEIGHT, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bitmap);
        Paint paint = new Paint();
        paint.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.SRC));
        final int blockCount = BLOCK_COLORS.length;
        final int blockWidth = TEST_WIDTH / blockCount;
        for (int i = 0 ; i < blockCount; i++) {
            paint.setColor(BLOCK_COLORS[i]);
            canvas.drawRect(i * blockWidth, 0, (i + 1) * blockWidth, TEST_HEIGHT, paint);
        }
        return bitmap;
    }


    @Override
    public void setUp() {
        super.setUp();

        // temporary - ensure test isn't capturing window bg only
        getInstrumentation().runOnMainSync(() -> getActivity().getWindow().setBackgroundDrawable(
                        new ColorDrawable(Color.GREEN)));

    }

    private CanvasClient mCanvasClient = new CanvasClient() {
        final Paint mPaint = new Paint();
        private final Bitmap mBitmap = createMultiRectBitmap();

        @Override
        public void draw(Canvas canvas, int width, int height) {
            canvas.drawColor(Color.WHITE); // temporary - ensure test isn't capturing window bg only

            mPaint.setColorFilter(new PorterDuffColorFilter(FILTER_COLOR, mConfig.mode));
            canvas.drawBitmap(mBitmap, 0, 0, mPaint);
        }
    };

    @Test
    public void test() {
        createTest()
                .addCanvasClient(mCanvasClient, mConfig.hardwareAccelerated)
                .runWithVerifier(new SamplePointVerifier(TEST_POINTS, mConfig.expectedColors));
    }
}


