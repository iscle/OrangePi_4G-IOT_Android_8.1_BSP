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

package android.print.pdf.cts;

import android.content.Context;
import android.graphics.Rect;
import android.graphics.pdf.PdfDocument;
import android.print.PrintAttributes;
import android.print.pdf.PrintedPdfDocument;
import android.support.test.InstrumentationRegistry;
import android.support.test.runner.AndroidJUnit4;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;

import static android.print.cts.Utils.assertException;
import static org.junit.Assert.assertEquals;

/**
 * Tests {@link PrintedPdfDocument}. This class is a subclass of {@link PdfDocument}, hence only the
 * overridden methods are tested.
 */
@RunWith(AndroidJUnit4.class)
public class PrintedPdfDocumentTest {
    private static final PrintAttributes.Margins ZERO_MARGINS = new PrintAttributes.Margins(0, 0, 0,
            0);
    private static Context sContext;

    @BeforeClass
    public static void setUp() {
        sContext = InstrumentationRegistry.getInstrumentation().getTargetContext();
    }

    @Test
    public void createWithNullAttributes() throws Throwable {
        assertException(() -> new PrintedPdfDocument(sContext, null), NullPointerException.class);
    }

    @Test
    public void createWithNullMediaSize() throws Throwable {
        PrintAttributes attr = new PrintAttributes.Builder().setMinMargins(ZERO_MARGINS).build();
        assertException(() -> new PrintedPdfDocument(sContext, attr), NullPointerException.class);
    }

    @Test
    public void createWithNullMargins() throws Throwable {
        PrintAttributes attr = new PrintAttributes.Builder()
                .setMediaSize(PrintAttributes.MediaSize.ISO_A4).build();
        assertException(() -> new PrintedPdfDocument(sContext, attr),
                NullPointerException.class);
    }

    @Test
    public void createWithNullContext() throws Exception {
        PrintAttributes attr = new PrintAttributes.Builder().setMinMargins(ZERO_MARGINS)
                .setMediaSize(PrintAttributes.MediaSize.ISO_A4).build();

        // Legacy: Context is not used and not checked for null-ness
        PrintedPdfDocument doc = new PrintedPdfDocument(null, attr);
        doc.close();
    }

    @Test
    public void startPage() throws Exception {
        PrintAttributes attr = new PrintAttributes.Builder().setMinMargins(ZERO_MARGINS)
                .setMediaSize(PrintAttributes.MediaSize.ISO_A4).build();

        PrintedPdfDocument doc = new PrintedPdfDocument(sContext, attr);
        PdfDocument.Page page = doc.startPage(0);
        doc.finishPage(page);
        doc.close();
    }

    @Test
    public void oneMilPageSize() throws Throwable {
        PrintAttributes attr = new PrintAttributes.Builder().setMinMargins(ZERO_MARGINS)
                .setMediaSize(new PrintAttributes.MediaSize("oneMil", "oneMil", 1, 1)).build();

        PrintedPdfDocument doc = new PrintedPdfDocument(sContext, attr);

        // We get an illegal argument exception here as a single mil of page size is converted to 0
        // pts.
        assertEquals(0, milsToPts(attr.getMediaSize().getHeightMils()));
        assertException(() -> doc.startPage(0), IllegalArgumentException.class);

        doc.close();
    }

    /**
     * Converts mils (1000th of an inch) to postscript points (72th of an inch).
     *
     * @param mils The distance in mils
     *
     * @return The distance in Postscript points
     */
    private int milsToPts(int mils) {
        return (int) (((float) mils / 1000) * 72);
    }

    @Test
    public void getPageWidth() throws Exception {
        PrintAttributes attr = new PrintAttributes.Builder().setMinMargins(ZERO_MARGINS)
                .setMediaSize(PrintAttributes.MediaSize.ISO_A4).build();

        PrintedPdfDocument doc = new PrintedPdfDocument(sContext, attr);
        assertEquals(milsToPts(attr.getMediaSize().getWidthMils()), doc.getPageWidth());
        doc.close();
    }

    @Test
    public void getPageHeight() throws Exception {
        PrintAttributes attr = new PrintAttributes.Builder().setMinMargins(ZERO_MARGINS)
                .setMediaSize(PrintAttributes.MediaSize.ISO_A4).build();

        PrintedPdfDocument doc = new PrintedPdfDocument(sContext, attr);
        assertEquals(milsToPts(attr.getMediaSize().getHeightMils()), doc.getPageHeight());
        doc.close();
    }

    @Test
    public void getContentRect() throws Exception {
        PrintAttributes attr = new PrintAttributes.Builder().setMinMargins(ZERO_MARGINS)
                .setMediaSize(PrintAttributes.MediaSize.ISO_A4).build();

        PrintedPdfDocument doc = new PrintedPdfDocument(sContext, attr);
        assertEquals(new Rect(0, 0, milsToPts(attr.getMediaSize().getWidthMils()),
                milsToPts(attr.getMediaSize().getHeightMils())), doc.getPageContentRect());
        doc.close();
    }

    @Test
    public void getContentRectBigMargins() throws Exception {
        PrintAttributes.Margins margins = new PrintAttributes.Margins(50, 60, 70, 80);
        PrintAttributes attr = new PrintAttributes.Builder().setMinMargins(margins)
                .setMediaSize(PrintAttributes.MediaSize.ISO_A4).build();

        PrintedPdfDocument doc = new PrintedPdfDocument(sContext, attr);
        assertEquals(new Rect(milsToPts(margins.getLeftMils()), milsToPts(margins.getTopMils()),
                milsToPts(attr.getMediaSize().getWidthMils()) - milsToPts(margins.getRightMils()),
                milsToPts(attr.getMediaSize().getHeightMils()) -
                        milsToPts(margins.getBottomMils())), doc.getPageContentRect());
        doc.close();
    }

    @Test
    public void getPageHeightAfterClose() throws Exception {
        PrintAttributes attr = new PrintAttributes.Builder().setMinMargins(ZERO_MARGINS)
                .setMediaSize(PrintAttributes.MediaSize.ISO_A4).build();

        PrintedPdfDocument doc = new PrintedPdfDocument(sContext, attr);
        doc.close();
        assertEquals(milsToPts(attr.getMediaSize().getHeightMils()), doc.getPageHeight());
    }

    @Test
    public void getPageWidthAfterClose() throws Exception {
        PrintAttributes attr = new PrintAttributes.Builder().setMinMargins(ZERO_MARGINS)
                .setMediaSize(PrintAttributes.MediaSize.ISO_A4).build();

        PrintedPdfDocument doc = new PrintedPdfDocument(sContext, attr);
        doc.close();
        assertEquals(milsToPts(attr.getMediaSize().getWidthMils()), doc.getPageWidth());
    }

    @Test
    public void getContentRectAfterClose() throws Exception {
        PrintAttributes attr = new PrintAttributes.Builder().setMinMargins(ZERO_MARGINS)
                .setMediaSize(PrintAttributes.MediaSize.ISO_A4).build();

        PrintedPdfDocument doc = new PrintedPdfDocument(sContext, attr);
        doc.close();
        assertEquals(new Rect(0, 0, milsToPts(attr.getMediaSize().getWidthMils()),
                milsToPts(attr.getMediaSize().getHeightMils())), doc.getPageContentRect());
    }
}
