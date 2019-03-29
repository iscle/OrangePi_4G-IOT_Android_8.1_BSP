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

package android.text.style.cts;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint.FontMetricsInt;
import android.support.test.filters.SmallTest;
import android.support.test.runner.AndroidJUnit4;
import android.text.Html;
import android.text.Layout;
import android.text.Spanned;
import android.text.StaticLayout;
import android.text.TextPaint;
import android.text.style.IconMarginSpan;

import org.junit.Test;
import org.junit.runner.RunWith;

@SmallTest
@RunWith(AndroidJUnit4.class)
public class IconMarginSpanTest {
    private static final int WIDTH = 80;
    private static final int HEIGHT = 120;
    private static final int[] COLOR = new int[WIDTH * HEIGHT];
    private static final Bitmap BITMAP_80X120 =
        Bitmap.createBitmap(COLOR, WIDTH, HEIGHT, Bitmap.Config.RGB_565);

    @Test
    public void testConstructor() {
        new IconMarginSpan(BITMAP_80X120);
        new IconMarginSpan(BITMAP_80X120, 1);
        new IconMarginSpan(null, -1);
    }

    @Test
    public void testGetLeadingMargin() {
        IconMarginSpan iconMarginSpan = new IconMarginSpan(BITMAP_80X120, 1);
        int leadingMargin1 = iconMarginSpan.getLeadingMargin(true);

        iconMarginSpan = new IconMarginSpan(BITMAP_80X120, 2);
        int leadingMargin2 = iconMarginSpan.getLeadingMargin(true);

        assertTrue(leadingMargin2 > leadingMargin1);
    }

    @Test
    public void testDrawLeadingMargin() {
        IconMarginSpan iconMarginSpan = new IconMarginSpan(BITMAP_80X120, 0);
        Canvas c = new Canvas();
        Spanned text = Html.fromHtml("<b>hello</b>");
        TextPaint p = new TextPaint();
        Layout layout = new StaticLayout("cts test.", p, 200, Layout.Alignment.ALIGN_NORMAL,
                1, 0, true);
        iconMarginSpan.drawLeadingMargin(c, p, 0, 0, 0, 0, 0, text, 0, 0, true, layout);
    }

    @Test(expected=NullPointerException.class)
    public void testDrawLeadingMarginNull() {
        IconMarginSpan iconMarginSpan = new IconMarginSpan(BITMAP_80X120, 0);

        iconMarginSpan.chooseHeight(null, 0, 0, 0, 0, null);
    }

    @Test(expected=ClassCastException.class)
    public void testDrawLeadingMarginString() {
        IconMarginSpan iconMarginSpan = new IconMarginSpan(BITMAP_80X120, 0);

        // When try to use a String as the text, should throw ClassCastException
        iconMarginSpan.chooseHeight("cts test.", 0, 0, 0, 0, null);
    }

    @Test
    public void testChooseHeight() {
        IconMarginSpan iconMarginSpan = new IconMarginSpan(BITMAP_80X120, 0);

        Spanned text = Html.fromHtml("cts test.");
        FontMetricsInt fm = new FontMetricsInt();

        assertEquals(0, fm.ascent);
        assertEquals(0, fm.bottom);
        assertEquals(0, fm.descent);
        assertEquals(0, fm.leading);
        assertEquals(0, fm.top);

        iconMarginSpan.chooseHeight(text, 0, -1, 0, 0, fm);

        assertEquals(0, fm.ascent);
        assertEquals(HEIGHT, fm.bottom);
        assertEquals(HEIGHT, fm.descent);
        assertEquals(0, fm.leading);
        assertEquals(0, fm.top);
    }

    @Test(expected=NullPointerException.class)
    public void testChooseHeightNull() {
        IconMarginSpan iconMarginSpan = new IconMarginSpan(BITMAP_80X120, 0);

        iconMarginSpan.chooseHeight(null, 0, 0, 0, 0, null);
    }

    @Test(expected=ClassCastException.class)
    public void testChooseHeightString() {
        IconMarginSpan iconMarginSpan = new IconMarginSpan(BITMAP_80X120, 0);

        // When try to use a String as the text, should throw ClassCastException
        iconMarginSpan.chooseHeight("cts test.", 0, 0, 0, 0, null);
    }
}
