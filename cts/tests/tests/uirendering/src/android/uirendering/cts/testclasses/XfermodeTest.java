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
import android.graphics.PorterDuffXfermode;
import android.graphics.RectF;
import android.graphics.drawable.ColorDrawable;
import android.support.test.filters.LargeTest;
import android.uirendering.cts.bitmapverifiers.SamplePointVerifier;
import android.uirendering.cts.testinfrastructure.ActivityTestBase;
import android.uirendering.cts.testinfrastructure.CanvasClient;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

import java.util.ArrayList;
import java.util.List;

@LargeTest // Temporarily hidden from presubmit
@RunWith(Parameterized.class)
public class XfermodeTest extends ActivityTestBase {
    /**
     * There are 4 locations we care about in testing each filter:
     *
     * 1) Both empty
     * 2) Only src, dst empty
     * 3) Both src + dst
     * 4) Only dst, src empty
     */
    private final static Point[] TEST_POINTS = new Point[] {
            new Point(1, 80),
            new Point(25, 25),
            new Point(35, 35),
            new Point(70, 70)
    };

    public static class Config {
        final boolean hardwareAccelerated;
        final PorterDuff.Mode mode;
        final int[] expectedColors;

        Config(boolean hardwareAccelerated, Object[] modeAndExpectedColors) {
            this.hardwareAccelerated = hardwareAccelerated;
            mode = (PorterDuff.Mode) modeAndExpectedColors[0];
            expectedColors = (int[]) modeAndExpectedColors[1];
        }

        @Override
        public String toString() {
            return mode.name() + ", hardwareAccelerated=" + hardwareAccelerated;
        }
    };

    public static List<XfermodeTest.Config> configs(Object[][] modesAndExpectedColors) {
        List<XfermodeTest.Config> configs = new ArrayList<>();
        for (boolean hardwareAccelerated : new boolean[] {false, true}) {
            for (Object[] modeAndExpectedColors : modesAndExpectedColors) {
                configs.add(new XfermodeTest.Config(hardwareAccelerated, modeAndExpectedColors));
            }
        }
        return configs;
    }

    private static final int BG_COLOR = 0xFFFFFFFF;
    private static final int DST_COLOR = 0xFFFFCC44;
    private static final int SRC_COLOR = 0xFF66AAFF;
    private static final int MULTIPLY_COLOR = 0xFF668844;
    private static final int SCREEN_COLOR = 0xFFFFEEFF;

    private static Object[][] MODES_AND_EXPECTED_COLORS = new Object[][] {
        { PorterDuff.Mode.SRC, new int[] {
                BG_COLOR, BG_COLOR, SRC_COLOR, SRC_COLOR } },

        { PorterDuff.Mode.DST, new int[] {
                BG_COLOR, DST_COLOR, DST_COLOR, BG_COLOR } },

        { PorterDuff.Mode.SRC_OVER, new int[] {
                BG_COLOR, DST_COLOR, SRC_COLOR, SRC_COLOR } },

        { PorterDuff.Mode.DST_OVER, new int[] {
                BG_COLOR, DST_COLOR, DST_COLOR, SRC_COLOR } },

        { PorterDuff.Mode.SRC_IN, new int[] {
                BG_COLOR, BG_COLOR, SRC_COLOR, BG_COLOR } },

        { PorterDuff.Mode.DST_IN, new int[] {
                BG_COLOR, BG_COLOR, DST_COLOR, BG_COLOR } },

        { PorterDuff.Mode.SRC_OUT, new int[] {
                BG_COLOR, BG_COLOR, BG_COLOR, SRC_COLOR } },

        { PorterDuff.Mode.DST_OUT, new int[] {
                BG_COLOR, DST_COLOR, BG_COLOR, BG_COLOR } },

        { PorterDuff.Mode.SRC_ATOP, new int[] {
                BG_COLOR, DST_COLOR, SRC_COLOR, BG_COLOR } },

        { PorterDuff.Mode.DST_ATOP, new int[] {
                BG_COLOR, BG_COLOR, DST_COLOR, SRC_COLOR } },

        { PorterDuff.Mode.XOR, new int[] {
                BG_COLOR, DST_COLOR, BG_COLOR, SRC_COLOR } },

        { PorterDuff.Mode.MULTIPLY, new int[] {
                BG_COLOR, BG_COLOR, MULTIPLY_COLOR, BG_COLOR } },

        { PorterDuff.Mode.SCREEN, new int[] {
                BG_COLOR, DST_COLOR, SCREEN_COLOR, SRC_COLOR } },
    };

    @Parameterized.Parameters(name = "{0}")
    public static List<Config> configs() {
        return configs(MODES_AND_EXPECTED_COLORS);
    }

    private final Config mConfig;

    public XfermodeTest(Config config) {
        mConfig = config;
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
        private final RectF mSrcRect = new RectF(30, 30, 80, 80);
        private final RectF mDstRect = new RectF(10, 10, 60, 60);
        private final Bitmap mSrcBitmap = createSrc();
        private final Bitmap mDstBitmap = createDst();

        @Override
        public void draw(Canvas canvas, int width, int height) {
            canvas.drawColor(Color.WHITE); // temporary - ensure test isn't capturing window bg only

            int sc = canvas.saveLayer(0, 0, TEST_WIDTH, TEST_HEIGHT, null);

            canvas.drawBitmap(mDstBitmap, 0, 0, null);
            mPaint.setXfermode(new PorterDuffXfermode(mConfig.mode));
            canvas.drawBitmap(mSrcBitmap, 0, 0, mPaint);

            canvas.restoreToCount(sc);
        }

        private Bitmap createSrc() {
            Bitmap srcB = Bitmap.createBitmap(TEST_WIDTH, TEST_HEIGHT, Bitmap.Config.ARGB_8888);
            Canvas srcCanvas = new Canvas(srcB);
            Paint srcPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
            srcPaint.setColor(SRC_COLOR);
            srcCanvas.drawRect(mSrcRect, srcPaint);
            return srcB;
        }

        private Bitmap createDst() {
            Bitmap dstB = Bitmap.createBitmap(TEST_WIDTH, TEST_HEIGHT, Bitmap.Config.ARGB_8888);
            Canvas dstCanvas = new Canvas(dstB);
            Paint dstPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
            dstPaint.setColor(DST_COLOR);
            dstCanvas.drawOval(mDstRect, dstPaint);
            return dstB;
        }
    };

    @Test
    public void test() {
        createTest()
                .addCanvasClient(mCanvasClient, mConfig.hardwareAccelerated)
                .runWithVerifier(new SamplePointVerifier(TEST_POINTS, mConfig.expectedColors));
    }
}
