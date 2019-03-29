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

package android.graphics.pdf.cts;

import static android.graphics.pdf.cts.Utils.A4_HEIGHT_PTS;
import static android.graphics.pdf.cts.Utils.A4_PORTRAIT;
import static android.graphics.pdf.cts.Utils.A4_WIDTH_PTS;
import static android.graphics.pdf.cts.Utils.A5_PORTRAIT;
import static android.graphics.pdf.cts.Utils.createRenderer;
import static android.graphics.pdf.cts.Utils.renderAndCompare;
import static android.graphics.pdf.cts.Utils.renderWithTransform;
import static android.graphics.pdf.cts.Utils.verifyException;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.Rect;
import android.graphics.pdf.cts.R;
import android.graphics.pdf.PdfRenderer;
import android.graphics.pdf.PdfRenderer.Page;
import android.os.ParcelFileDescriptor;
import android.support.annotation.NonNull;
import android.support.test.InstrumentationRegistry;
import android.support.test.filters.SmallTest;
import android.support.test.runner.AndroidJUnit4;

import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.IOException;

/**
 * All test for {@link PdfRenderer} beside the valid transformation parameter tests of {@link
 * PdfRenderer.Page#render}.
 */
@SmallTest
@RunWith(AndroidJUnit4.class)
public class PdfRendererTest {
    private static final int A5_PORTRAIT_PRINTSCALING_DEFAULT =
            R.raw.a5_portrait_rgbb_1_6_printscaling_default;
    private static final int A5_PORTRAIT_PRINTSCALING_NONE =
            R.raw.a5_portrait_rgbb_1_6_printscaling_none;
    private static final int TWO_PAGES = R.raw.two_pages;

    private Context mContext;

    @Before
    public void setup() {
        mContext = InstrumentationRegistry.getTargetContext();
    }

    @Test
    public void constructRendererNull() throws Exception {
        verifyException(() -> new PdfRenderer(null), NullPointerException.class);
    }

    @Test
    @Ignore("Makes all subsequent tests fail")
    public void constructRendererFromNonPDF() throws Exception {
        // Open jpg as if it was a PDF
        ParcelFileDescriptor fd = mContext.getResources().openRawResourceFd(R.raw.testimage)
                .getParcelFileDescriptor();
        verifyException(() -> new PdfRenderer(fd), IOException.class);
    }

    @Test
    public void useRendererAfterClose() throws Exception {
        PdfRenderer renderer = createRenderer(A4_PORTRAIT, mContext);
        renderer.close();

        verifyException(renderer::close, IllegalStateException.class);
        verifyException(renderer::getPageCount, IllegalStateException.class);
        verifyException(renderer::shouldScaleForPrinting, IllegalStateException.class);
        verifyException(() -> renderer.openPage(0), IllegalStateException.class);
    }

    @Test
    public void usePageAfterClose() throws Exception {
        PdfRenderer renderer = createRenderer(A4_PORTRAIT, mContext);
        Page page = renderer.openPage(0);
        page.close();

        // Legacy behavior: The properties are cached, hence they are still available after the page
        //                  is closed
        page.getHeight();
        page.getWidth();
        page.getIndex();
        verifyException(page::close, IllegalStateException.class);

        // Legacy support. An IllegalStateException would be nice by unfortunately the legacy
        // implementation returned NullPointerException
        verifyException(() -> page.render(null, null, null, Page.RENDER_MODE_FOR_DISPLAY),
                NullPointerException.class);

        renderer.close();
    }

    @Test
    public void closeWithOpenPage() throws Exception {
        PdfRenderer renderer = createRenderer(A4_PORTRAIT, mContext);
        Page page = renderer.openPage(0);

        verifyException(renderer::close, IllegalStateException.class);

        page.close();
        renderer.close();
    }

    @Test
    public void openTwoPages() throws Exception {
        try (PdfRenderer renderer = createRenderer(TWO_PAGES, mContext)) {
            // Cannot open two pages at once
            Page page = renderer.openPage(0);
            verifyException(() -> renderer.openPage(1), IllegalStateException.class);

            page.close();
        }
    }

    @Test
    public void testPageCount() throws Exception {
        try (PdfRenderer renderer = createRenderer(TWO_PAGES, mContext)) {
            assertEquals(2, renderer.getPageCount());
        }
    }

    @Test
    public void testOpenPage() throws Exception {
        try (PdfRenderer renderer = createRenderer(TWO_PAGES, mContext)) {
            verifyException(() -> renderer.openPage(-1), IllegalArgumentException.class);
            Page page0 = renderer.openPage(0);
            page0.close();
            Page page1 = renderer.openPage(1);
            page1.close();
            verifyException(() -> renderer.openPage(2), IllegalArgumentException.class);
        }
    }

    @Test
    public void testPageSize() throws Exception {
        try (PdfRenderer renderer = createRenderer(A4_PORTRAIT, mContext);
             Page page = renderer.openPage(0)) {
            assertEquals(A4_HEIGHT_PTS, page.getHeight());
            assertEquals(A4_WIDTH_PTS, page.getWidth());
        }
    }

    @Test
    public void testPrintScaleDefault() throws Exception {
        try (PdfRenderer renderer = createRenderer(A5_PORTRAIT, mContext)) {
            assertTrue(renderer.shouldScaleForPrinting());
        }
    }

    @Test
    public void testPrintScalePDF16Default() throws Exception {
        try (PdfRenderer renderer = createRenderer(A5_PORTRAIT_PRINTSCALING_DEFAULT, mContext)) {
            assertTrue(renderer.shouldScaleForPrinting());
        }
    }

    @Test
    public void testPrintScalePDF16None() throws Exception {
        try (PdfRenderer renderer = createRenderer(A5_PORTRAIT_PRINTSCALING_NONE, mContext)) {
            assertFalse(renderer.shouldScaleForPrinting());
        }
    }

    /**
     * Take 16 color probes in the middle of the 16 segments of the page in the following pattern:
     * <pre>
     * +----+----+----+----+
     * |  0 :  1 :  2 :  3 |
     * +....:....:....:....+
     * |  4 :  5 :  6 :  7 |
     * +....:....:....:....+
     * |  8 :  9 : 10 : 11 |
     * +....:....:....:....+
     * | 12 : 13 : 14 : 15 |
     * +----+----+----+----+
     * </pre>
     *
     * @param bm The bitmap to probe
     *
     * @return The color at the probes
     */
    private @NonNull int[] getColorProbes(@NonNull Bitmap bm) {
        int[] probes = new int[16];

        for (int row = 0; row < 4; row++) {
            for (int column = 0; column < 4; column++) {
                probes[row * 4 + column] = bm.getPixel((int) (bm.getWidth() * (column + 0.5) / 4),
                        (int) (bm.getHeight() * (row + 0.5) / 4));
            }
        }

        return probes;
    }

    /**
     * Implementation for {@link #renderNoTransformationAndComparePointsForScreen} and {@link
     * #renderNoTransformationAndComparePointsForPrint}.
     *
     * @param renderMode The render mode to use
     *
     * @throws Exception If anything was unexpected
     */
    private void renderNoTransformationAndComparePoints(int renderMode) throws Exception {
        Bitmap bm = renderWithTransform(A4_WIDTH_PTS, A4_HEIGHT_PTS, A4_PORTRAIT, null, null,
                renderMode, mContext);
        int[] probes = getColorProbes(bm);

        // Compare rendering to expected result. This ensures that all other tests in this class do
        // not accidentally all compare empty bitmaps.
        assertEquals(Color.RED, probes[0]);
        assertEquals(Color.RED, probes[1]);
        assertEquals(Color.GREEN, probes[2]);
        assertEquals(Color.GREEN, probes[3]);
        assertEquals(Color.RED, probes[4]);
        assertEquals(Color.RED, probes[5]);
        assertEquals(Color.GREEN, probes[6]);
        assertEquals(Color.GREEN, probes[7]);
        assertEquals(Color.BLUE, probes[8]);
        assertEquals(Color.BLUE, probes[9]);
        assertEquals(Color.BLACK, probes[10]);
        assertEquals(Color.BLACK, probes[11]);
        assertEquals(Color.BLUE, probes[12]);
        assertEquals(Color.BLUE, probes[13]);
        assertEquals(Color.BLACK, probes[14]);
        assertEquals(Color.BLACK, probes[15]);
    }

    @Test
    public void renderNoTransformationAndComparePointsForScreen() throws Exception {
        renderNoTransformationAndComparePoints(Page.RENDER_MODE_FOR_DISPLAY);
    }

    @Test
    public void renderNoTransformationAndComparePointsForPrint() throws Exception {
        renderNoTransformationAndComparePoints(Page.RENDER_MODE_FOR_PRINT);
    }

    @Test
    public void renderPerspective() throws Exception {
        Matrix transform = new Matrix();

        transform.setValues(new float[] { 1, 1, 1, 1, 1, 1, 1, 1, 1 });

        verifyException(
                () -> renderWithTransform(A4_WIDTH_PTS, A4_HEIGHT_PTS, A4_PORTRAIT, null, transform,
                        Page.RENDER_MODE_FOR_DISPLAY, mContext), IllegalArgumentException.class);
    }

    @Test
    public void render45degreeRotationTranslationAndScaleAndClip() throws Exception {
        Matrix transform = new Matrix();
        // Rotate on top left corner
        transform.postRotate(45);
        // Move
        transform.postTranslate(A4_WIDTH_PTS / 4, A4_HEIGHT_PTS / 4);
        // Scale to 75%
        transform.postScale(0.75f, 0.75f);
        // Clip
        Rect clip = new Rect(20, 20, A4_WIDTH_PTS - 20, A4_HEIGHT_PTS - 20);

        renderAndCompare(A4_WIDTH_PTS, A4_HEIGHT_PTS, A4_PORTRAIT, clip, transform,
                Page.RENDER_MODE_FOR_DISPLAY, mContext);
    }

    @Test
    public void renderStreched() throws Exception {
        renderAndCompare(A4_WIDTH_PTS * 4 / 3, A4_HEIGHT_PTS * 3 / 4, A4_PORTRAIT, null, null,
                Page.RENDER_MODE_FOR_DISPLAY, mContext);
    }

    @Test
    public void renderWithClip() throws Exception {
        Rect clip = new Rect(20, 20, A4_WIDTH_PTS - 50, A4_HEIGHT_PTS - 50);
        renderAndCompare(A4_WIDTH_PTS, A4_HEIGHT_PTS, A4_PORTRAIT, clip, null,
                Page.RENDER_MODE_FOR_DISPLAY, mContext);
    }

    @Test
    public void renderWithAllClipped() throws Exception {
        Rect clip = new Rect(A4_WIDTH_PTS / 2, A4_HEIGHT_PTS / 2, A4_WIDTH_PTS / 2,
                A4_HEIGHT_PTS / 2);
        renderAndCompare(A4_WIDTH_PTS, A4_HEIGHT_PTS, A4_PORTRAIT, clip, null,
                Page.RENDER_MODE_FOR_DISPLAY, mContext);
    }

    @Test
    public void renderWithBadLowerCornerOfClip() throws Exception {
        Rect clip = new Rect(0, 0, A4_WIDTH_PTS + 20, A4_HEIGHT_PTS + 20);
        verifyException(
                () -> renderWithTransform(A4_WIDTH_PTS, A4_HEIGHT_PTS, A4_PORTRAIT, clip, null,
                        Page.RENDER_MODE_FOR_DISPLAY, mContext), IllegalArgumentException.class);
    }

    @Test
    public void renderWithBadUpperCornerOfClip() throws Exception {
        Rect clip = new Rect(-20, -20, A4_WIDTH_PTS, A4_HEIGHT_PTS);
        verifyException(
                () -> renderWithTransform(A4_WIDTH_PTS, A4_HEIGHT_PTS, A4_PORTRAIT, clip, null,
                        Page.RENDER_MODE_FOR_DISPLAY, mContext), IllegalArgumentException.class);
    }

    @Test
    public void renderTwoModes() throws Exception {
        verifyException(
                () -> renderWithTransform(A4_WIDTH_PTS, A4_HEIGHT_PTS, A4_PORTRAIT, null, null,
                        Page.RENDER_MODE_FOR_DISPLAY | Page.RENDER_MODE_FOR_PRINT, mContext),
                IllegalArgumentException.class);
    }

    @Test
    public void renderBadMode() throws Exception {
        verifyException(
                () -> renderWithTransform(A4_WIDTH_PTS, A4_HEIGHT_PTS, A4_PORTRAIT, null, null,
                        1 << 30, mContext), IllegalArgumentException.class);
    }

    @Test
    public void renderAllModes() throws Exception {
        verifyException(
                () -> renderWithTransform(A4_WIDTH_PTS, A4_HEIGHT_PTS, A4_PORTRAIT, null, null, -1,
                        mContext), IllegalArgumentException.class);
    }

    @Test
    public void renderNoMode() throws Exception {
        verifyException(
                () -> renderWithTransform(A4_WIDTH_PTS, A4_HEIGHT_PTS, A4_PORTRAIT, null, null, 0,
                        mContext), IllegalArgumentException.class);
    }

    @Test
    public void renderOnNullBitmap() throws Exception {
        try (PdfRenderer renderer = createRenderer(A4_PORTRAIT, mContext);
             Page page = renderer.openPage(0)) {
            verifyException(() -> page.render(null, null, null, Page.RENDER_MODE_FOR_DISPLAY),
                    NullPointerException.class);
        }
    }

}
