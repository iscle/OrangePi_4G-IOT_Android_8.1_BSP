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
import static org.junit.Assert.assertNotNull;

import android.graphics.Typeface;
import android.os.Parcel;
import android.support.test.filters.SmallTest;
import android.support.test.runner.AndroidJUnit4;
import android.text.TextPaint;
import android.text.style.StyleSpan;

import org.junit.Test;
import org.junit.runner.RunWith;

@SmallTest
@RunWith(AndroidJUnit4.class)
public class StyleSpanTest {
    @Test
    public void testConstructor() {
        StyleSpan styleSpan = new StyleSpan(2);

        Parcel p = Parcel.obtain();
        try {
            styleSpan.writeToParcel(p, 0);
            p.setDataPosition(0);
            StyleSpan fromParcel = new StyleSpan(p);
            assertEquals(2, fromParcel.getStyle());
            new StyleSpan(-2);
        } finally {
            p.recycle();
        }
    }

    @Test
    public void testGetStyle() {
        StyleSpan styleSpan = new StyleSpan(2);
        assertEquals(2, styleSpan.getStyle());

        styleSpan = new StyleSpan(-2);
        assertEquals(-2, styleSpan.getStyle());
    }

    @Test
    public void testUpdateMeasureState() {
        StyleSpan styleSpan = new StyleSpan(Typeface.BOLD);

        TextPaint tp = new TextPaint();
        Typeface tf = Typeface.defaultFromStyle(Typeface.NORMAL);
        tp.setTypeface(tf);

        assertNotNull(tp.getTypeface());
        assertEquals(Typeface.NORMAL, tp.getTypeface().getStyle());

        styleSpan.updateMeasureState(tp);

        assertNotNull(tp.getTypeface());
        assertEquals(Typeface.BOLD, tp.getTypeface().getStyle());
    }

    @Test(expected=NullPointerException.class)
    public void testUpdateMeasureStateNull() {
        StyleSpan styleSpan = new StyleSpan(Typeface.BOLD);

        styleSpan.updateMeasureState(null);
    }

    @Test
    public void testUpdateDrawState() {
        StyleSpan styleSpan = new StyleSpan(Typeface.BOLD);

        TextPaint tp = new TextPaint();
        Typeface tf = Typeface.defaultFromStyle(Typeface.NORMAL);
        tp.setTypeface(tf);

        assertNotNull(tp.getTypeface());
        assertEquals(Typeface.NORMAL, tp.getTypeface().getStyle());

        styleSpan.updateDrawState(tp);

        assertNotNull(tp.getTypeface());
        assertEquals(Typeface.BOLD, tp.getTypeface().getStyle());
    }

    @Test(expected=NullPointerException.class)
    public void testUpdateDrawStateNull() {
        StyleSpan styleSpan = new StyleSpan(Typeface.BOLD);

        styleSpan.updateDrawState(null);
    }

    @Test
    public void testDescribeContents() {
        StyleSpan styleSpan = new StyleSpan(Typeface.BOLD);
        styleSpan.describeContents();
    }

    @Test
    public void testGetSpanTypeId() {
        StyleSpan styleSpan = new StyleSpan(Typeface.BOLD);
        styleSpan.getSpanTypeId();
    }

    @Test
    public void testWriteToParcel() {
        Parcel p = Parcel.obtain();
        try {
            StyleSpan styleSpan = new StyleSpan(Typeface.BOLD);
            styleSpan.writeToParcel(p, 0);
            p.setDataPosition(0);
            StyleSpan newSpan = new StyleSpan(p);
            assertEquals(Typeface.BOLD, newSpan.getStyle());
        } finally {
            p.recycle();
        }
    }
}
