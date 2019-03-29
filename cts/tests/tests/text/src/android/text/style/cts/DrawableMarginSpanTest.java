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

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint.FontMetricsInt;
import android.graphics.drawable.Drawable;
import android.support.test.InstrumentationRegistry;
import android.support.test.filters.SmallTest;
import android.support.test.runner.AndroidJUnit4;
import android.text.Html;
import android.text.Layout;
import android.text.Spanned;
import android.text.StaticLayout;
import android.text.TextPaint;
import android.text.cts.R;
import android.text.style.DrawableMarginSpan;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

@SmallTest
@RunWith(AndroidJUnit4.class)
public class DrawableMarginSpanTest {
    private Context mContext;
    private Drawable mDrawable;

    @Before
    public void setup() {
        mContext = InstrumentationRegistry.getTargetContext();
        mDrawable = mContext.getDrawable(R.drawable.scenery);
    }

    @Test
    public void testConstructor() {
        Drawable d = mContext.getDrawable(R.drawable.pass);

        new DrawableMarginSpan(d);
        new DrawableMarginSpan(d, 1);
        new DrawableMarginSpan(null, -1);
    }

    @Test
    public void testGetLeadingMargin() {
        DrawableMarginSpan drawableMarginSpan = new DrawableMarginSpan(mDrawable, 1);
        int leadingMargin1 = drawableMarginSpan.getLeadingMargin(true);

        drawableMarginSpan = new DrawableMarginSpan(mDrawable, 10);
        int leadingMargin2 = drawableMarginSpan.getLeadingMargin(true);

        assertTrue(leadingMargin2 > leadingMargin1);
    }

    @Test
    public void testDrawLeadingMargin() {
        DrawableMarginSpan drawableMarginSpan = new DrawableMarginSpan(mDrawable, 0);

        assertEquals(0, mDrawable.getBounds().top);
        assertEquals(0, mDrawable.getBounds().bottom);
        assertEquals(0, mDrawable.getBounds().left);
        assertEquals(0, mDrawable.getBounds().right);

        Canvas canvas = new Canvas();
        Spanned text = Html.fromHtml("<b>hello</b>");
        TextPaint paint = new TextPaint();
        Layout layout = new StaticLayout("cts test.", paint, 200,
                Layout.Alignment.ALIGN_NORMAL, 1, 0, true);

        int x = 10;
        drawableMarginSpan.drawLeadingMargin(canvas, null, x, 0, 0,
                0, 0, text, 0, 0, true, layout);

        // 0 means the top location
        assertEquals(0, mDrawable.getBounds().top);
        assertEquals(mDrawable.getIntrinsicHeight(), mDrawable.getBounds().bottom);
        assertEquals(x, mDrawable.getBounds().left);
        assertEquals(x + mDrawable.getIntrinsicWidth(), mDrawable.getBounds().right);
    }

    @Test(expected=NullPointerException.class)
    public void testDrawLeadingMarginNull() {
        DrawableMarginSpan drawableMarginSpan = new DrawableMarginSpan(mDrawable, 0);

        drawableMarginSpan.drawLeadingMargin(null, null, 0, 0, 0, 0, 0,
                null, 0, 0, false, null);
    }

    @Test(expected=ClassCastException.class)
    public void testDrawLeadingMarginString() {
        DrawableMarginSpan drawableMarginSpan = new DrawableMarginSpan(mDrawable, 0);

        // When try to use a String as the text, should throw ClassCastException
        drawableMarginSpan.drawLeadingMargin(null, null, 0, 0, 0, 0, 0,
                "cts test.", 0, 0, false, null);
    }

    @Test
    public void testChooseHeight() {
        DrawableMarginSpan drawableMarginSpan = new DrawableMarginSpan(mDrawable, 0);

        Spanned text = Html.fromHtml("cts test.");
        FontMetricsInt fm = new FontMetricsInt();

        assertEquals(0, fm.ascent);
        assertEquals(0, fm.bottom);
        assertEquals(0, fm.descent);
        assertEquals(0, fm.leading);
        assertEquals(0, fm.top);

        // should not change ascent, leading and top.
        drawableMarginSpan.chooseHeight(text, 0, text.getSpanEnd(drawableMarginSpan), 0, 0, fm);

        assertEquals(0, fm.ascent);
        // do not know what they should be, because missed javadoc. only check they are positive.
        assertTrue(fm.bottom > 0);
        assertTrue(fm.descent > 0);
        assertEquals(0, fm.leading);
        assertEquals(0, fm.top);
    }

    @Test(expected=NullPointerException.class)
    public void testChooseHeightNull() {
        DrawableMarginSpan drawableMarginSpan = new DrawableMarginSpan(mDrawable, 0);

        drawableMarginSpan.chooseHeight(null, 0, 0, 0, 0, null);
    }


    @Test(expected=ClassCastException.class)
    public void testChooseHeightString() {
        DrawableMarginSpan drawableMarginSpan = new DrawableMarginSpan(mDrawable, 0);

        // When try to use a String as the text, should throw ClassCastException
        drawableMarginSpan.chooseHeight("cts test.", 0, 0, 0, 0, null);
    }
}
