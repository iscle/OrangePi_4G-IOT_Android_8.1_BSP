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

package android.print.cts;

import android.print.PrintAttributes;
import android.print.PrintAttributes.MediaSize;
import android.print.PrintAttributes.Resolution;
import android.print.PrintDocumentInfo;
import android.support.test.runner.AndroidJUnit4;
import org.junit.Test;
import org.junit.runner.RunWith;

import static android.print.cts.Utils.*;
import static org.junit.Assert.*;

/**
 * Test that the print attributes can be constructed correctly. This does not test that the
 * attributes have the desired effect when send to the print framework.
 */
@RunWith(AndroidJUnit4.class)
public class ClassParametersTest {
    /**
     * Test that we cannot create PrintAttributes.colorModes with illegal parameters.
     *
     * @throws Exception If anything is unexpected
     */
    @Test
    public void illegalPrintAttributesColorMode() throws Throwable {
        assertException(() -> (new PrintAttributes.Builder()).setColorMode(-1),
                IllegalArgumentException.class);
        assertException(() -> (new PrintAttributes.Builder()).setColorMode(0),
                IllegalArgumentException.class);
        assertException(() -> (new PrintAttributes.Builder()).setColorMode(
                PrintAttributes.COLOR_MODE_COLOR | PrintAttributes.COLOR_MODE_MONOCHROME),
                IllegalArgumentException.class);
    }

    /**
     * Test that we cannot create PrintAttributes.duplexMode with illegal parameters.
     *
     * @throws Exception If anything is unexpected
     */
    @Test
    public void illegalPrintAttributesDuplexMode() throws Throwable {
        assertException(() -> (new PrintAttributes.Builder()).setDuplexMode(-1),
                IllegalArgumentException.class);
        assertException(() -> (new PrintAttributes.Builder()).setDuplexMode(0),
                IllegalArgumentException.class);
        assertException(() -> (new PrintAttributes.Builder()).setDuplexMode(
                PrintAttributes.DUPLEX_MODE_LONG_EDGE | PrintAttributes.DUPLEX_MODE_NONE),
                IllegalArgumentException.class);
    }

    /**
     * Test that we cannot create PrintAttributes.resolution with illegal parameters.
     *
     * @throws Exception If anything is unexpected
     */
    @Test
    public void illegalPrintAttributesResolution() throws Throwable {
        assertException(() -> new Resolution(null, "label", 10, 10),
                IllegalArgumentException.class);
        assertException(() -> new Resolution("", "label", 10, 10),
                IllegalArgumentException.class);
        assertException(() -> new Resolution("id", null, 10, 10),
                IllegalArgumentException.class);
        assertException(() -> new Resolution("id", "", 10, 10),
                IllegalArgumentException.class);
        assertException(() -> new Resolution("id", "label", -10, 10),
                IllegalArgumentException.class);
        assertException(() -> new Resolution("id", "label", 0, 10),
                IllegalArgumentException.class);
        assertException(() -> new Resolution("id", "label", 10, -10),
                IllegalArgumentException.class);
        assertException(() -> new Resolution("id", "label", 10, 0),
                IllegalArgumentException.class);
    }

    /**
     * Test that we can create PrintAttributes.resolution with legal parameters.
     *
     * @throws Exception If anything is unexpected
     */
    @Test
    public void legalPrintAttributesResolution() throws Exception {
        // Small resolution
        Resolution testResolution = new Resolution("testId", "testLabel", 1, 2);
        assertEquals("testId", testResolution.getId());
        assertEquals("testLabel", testResolution.getLabel());
        assertEquals(1, testResolution.getHorizontalDpi());
        assertEquals(2, testResolution.getVerticalDpi());

        // Small even resolution
        Resolution testResolution2 = new Resolution("testId2", "testLabel2", 1, 1);
        assertEquals("testId2", testResolution2.getId());
        assertEquals("testLabel2", testResolution2.getLabel());
        assertEquals(1, testResolution2.getHorizontalDpi());
        assertEquals(1, testResolution2.getVerticalDpi());

        // Large even resolution
        Resolution testResolution3 = new Resolution("testId3", "testLabel3", Integer.MAX_VALUE,
                Integer.MAX_VALUE);
        assertEquals("testId3", testResolution3.getId());
        assertEquals("testLabel3", testResolution3.getLabel());
        assertEquals(Integer.MAX_VALUE, testResolution3.getHorizontalDpi());
        assertEquals(Integer.MAX_VALUE, testResolution3.getVerticalDpi());
    }

    /**
     * Test that we cannot create PrintAttributes.mediaSize with illegal parameters.
     *
     * @throws Exception If anything is unexpected
     */
    @Test
    public void illegalPrintAttributesMediaSize() throws Throwable {
        assertException(() -> new MediaSize(null, "label", 10, 10),
                IllegalArgumentException.class);
        assertException(() -> new MediaSize("", "label", 10, 10),
                IllegalArgumentException.class);
        assertException(() -> new MediaSize("id", null, 10, 10),
                IllegalArgumentException.class);
        assertException(() -> new MediaSize("id", "", 10, 10),
                IllegalArgumentException.class);
        assertException(() -> new MediaSize("id", "label", -10, 10),
                IllegalArgumentException.class);
        assertException(() -> new MediaSize("id", "label", 0, 10),
                IllegalArgumentException.class);
        assertException(() -> new MediaSize("id", "label", 10, -10),
                IllegalArgumentException.class);
        assertException(() -> new MediaSize("id", "label", 10, 0),
                IllegalArgumentException.class);
    }

    /**
     * Test that we can create PrintAttributes.mediaSize with legal parameters.
     *
     * @throws Exception If anything is unexpected
     */
    @Test
    public void legalPrintAttributesMediaSize() throws Exception {
        // Small portrait paper
        MediaSize testMediaSize1 = new MediaSize("testId", "testLabel", 1, 2);
        assertEquals("testId", testMediaSize1.getId());
        assertEquals("testLabel", testMediaSize1.getLabel(null));
        assertEquals(1, testMediaSize1.getWidthMils());
        assertEquals(2, testMediaSize1.getHeightMils());
        assertTrue(testMediaSize1.isPortrait());

        MediaSize testMediaSize1L = testMediaSize1.asLandscape();
        assertEquals("testId", testMediaSize1L.getId());
        assertEquals("testLabel", testMediaSize1L.getLabel(null));
        assertEquals(2, testMediaSize1L.getWidthMils());
        assertEquals(1, testMediaSize1L.getHeightMils());
        assertFalse(testMediaSize1L.isPortrait());

        MediaSize testMediaSize1P = testMediaSize1.asPortrait();
        assertEquals("testId", testMediaSize1P.getId());
        assertEquals("testLabel", testMediaSize1P.getLabel(null));
        assertEquals(1, testMediaSize1P.getWidthMils());
        assertEquals(2, testMediaSize1P.getHeightMils());
        assertTrue(testMediaSize1P.isPortrait());

        // Small square paper
        MediaSize testMediaSize2 = new MediaSize("testId2", "testLabel2", 1, 1);
        assertEquals("testId2", testMediaSize2.getId());
        assertEquals("testLabel2", testMediaSize2.getLabel(null));
        assertEquals(1, testMediaSize2.getWidthMils());
        assertEquals(1, testMediaSize2.getHeightMils());
        assertTrue(testMediaSize2.isPortrait());

        MediaSize testMediaSize2L = testMediaSize2.asLandscape();
        assertEquals("testId2", testMediaSize2L.getId());
        assertEquals("testLabel2", testMediaSize2L.getLabel(null));
        assertEquals(1, testMediaSize2L.getWidthMils());
        assertEquals(1, testMediaSize2L.getHeightMils());
        assertTrue(testMediaSize2L.isPortrait());

        MediaSize testMediaSize2P = testMediaSize2.asPortrait();
        assertEquals("testId2", testMediaSize2P.getId());
        assertEquals("testLabel2", testMediaSize2P.getLabel(null));
        assertEquals(1, testMediaSize2P.getWidthMils());
        assertEquals(1, testMediaSize2P.getHeightMils());
        assertTrue(testMediaSize2P.isPortrait());

        // Large landscape paper
        MediaSize testMediaSize3 = new MediaSize("testId3", "testLabel3", Integer.MAX_VALUE,
                Integer.MAX_VALUE - 1);
        assertEquals("testId3", testMediaSize3.getId());
        assertEquals("testLabel3", testMediaSize3.getLabel(null));
        assertEquals(Integer.MAX_VALUE, testMediaSize3.getWidthMils());
        assertEquals(Integer.MAX_VALUE - 1, testMediaSize3.getHeightMils());
        assertFalse(testMediaSize3.isPortrait());

        MediaSize testMediaSize3L = testMediaSize3.asLandscape();
        assertEquals("testId3", testMediaSize3L.getId());
        assertEquals("testLabel3", testMediaSize3L.getLabel(null));
        assertEquals(Integer.MAX_VALUE, testMediaSize3L.getWidthMils());
        assertEquals(Integer.MAX_VALUE - 1, testMediaSize3L.getHeightMils());
        assertFalse(testMediaSize3L.isPortrait());

        MediaSize testMediaSize3P = testMediaSize3.asPortrait();
        assertEquals("testId3", testMediaSize3P.getId());
        assertEquals("testLabel3", testMediaSize3P.getLabel(null));
        assertEquals(Integer.MAX_VALUE - 1, testMediaSize3P.getWidthMils());
        assertEquals(Integer.MAX_VALUE, testMediaSize3P.getHeightMils());
        assertTrue(testMediaSize3P.isPortrait());
    }

    /**
     * Test that we cannot create PrintDocumentInfo with illegal parameters.
     *
     * @throws Exception If anything is unexpected
     */
    @Test
    public void illegalPrintDocumentInfo() throws Throwable {
        assertException(() -> new PrintDocumentInfo.Builder(null),
                IllegalArgumentException.class);
        assertException(() -> new PrintDocumentInfo.Builder(""),
                IllegalArgumentException.class);

        assertException(() -> new PrintDocumentInfo.Builder("doc").setPageCount(-2),
                IllegalArgumentException.class);
    }

    /**
     * Test that we can create PrintDocumentInfo with legal parameters.
     *
     * @throws Exception If anything is unexpected
     */
    @Test
    public void legalPrintDocumentInfo() throws Exception {
        PrintDocumentInfo defaultInfo = new PrintDocumentInfo.Builder("doc").build();
        assertEquals(PrintDocumentInfo.CONTENT_TYPE_DOCUMENT, defaultInfo.getContentType());
        assertEquals(PrintDocumentInfo.PAGE_COUNT_UNKNOWN, defaultInfo.getPageCount());
        assertEquals(0, defaultInfo.getDataSize());
        assertEquals("doc", defaultInfo.getName());

        PrintDocumentInfo info = new PrintDocumentInfo.Builder("doc")
                .setContentType(PrintDocumentInfo.CONTENT_TYPE_UNKNOWN).build();
        assertEquals(PrintDocumentInfo.CONTENT_TYPE_UNKNOWN, info.getContentType());

        PrintDocumentInfo info2 = new PrintDocumentInfo.Builder("doc")
                .setContentType(PrintDocumentInfo.CONTENT_TYPE_DOCUMENT).build();
        assertEquals(PrintDocumentInfo.CONTENT_TYPE_DOCUMENT, info2.getContentType());

        PrintDocumentInfo info3 = new PrintDocumentInfo.Builder("doc")
                .setContentType(PrintDocumentInfo.CONTENT_TYPE_PHOTO).build();
        assertEquals(PrintDocumentInfo.CONTENT_TYPE_PHOTO, info3.getContentType());

        PrintDocumentInfo info4 = new PrintDocumentInfo.Builder("doc").setContentType(-23).build();
        assertEquals(-23, info4.getContentType());

        PrintDocumentInfo info5 = new PrintDocumentInfo.Builder("doc").setPageCount(0).build();
        assertEquals(PrintDocumentInfo.PAGE_COUNT_UNKNOWN, info5.getPageCount());

        PrintDocumentInfo info6 = new PrintDocumentInfo.Builder("doc")
                .setPageCount(PrintDocumentInfo.PAGE_COUNT_UNKNOWN).build();
        assertEquals(PrintDocumentInfo.PAGE_COUNT_UNKNOWN, info6.getPageCount());

        PrintDocumentInfo info7 = new PrintDocumentInfo.Builder("doc")
                .setPageCount(Integer.MAX_VALUE).build();
        assertEquals(Integer.MAX_VALUE, info7.getPageCount());
    }
}
