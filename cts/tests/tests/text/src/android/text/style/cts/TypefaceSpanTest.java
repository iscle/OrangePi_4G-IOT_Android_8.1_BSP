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
import static org.junit.Assert.assertNull;

import android.graphics.Typeface;
import android.os.Parcel;
import android.support.test.filters.SmallTest;
import android.support.test.runner.AndroidJUnit4;
import android.text.TextPaint;
import android.text.style.TypefaceSpan;

import org.junit.Test;
import org.junit.runner.RunWith;

@SmallTest
@RunWith(AndroidJUnit4.class)
public class TypefaceSpanTest {
    private static final String FAMILY = "monospace";

    @Test
    public void testConstructor() {
        TypefaceSpan t = new TypefaceSpan(FAMILY);

        final Parcel p = Parcel.obtain();
        try {
            t.writeToParcel(p, 0);
            p.setDataPosition(0);
            new TypefaceSpan(p);
        } finally {
            p.recycle();
        }
    }

    @Test
    public void testGetFamily() {
        TypefaceSpan typefaceSpan = new TypefaceSpan(FAMILY);
        assertEquals(FAMILY, typefaceSpan.getFamily());
    }

    @Test
    public void testUpdateMeasureState() {
        TypefaceSpan typefaceSpan = new TypefaceSpan(FAMILY);

        TextPaint tp = new TextPaint();
        assertNull(tp.getTypeface());

        typefaceSpan.updateMeasureState(tp);

        assertNotNull(tp.getTypeface());
        // the style should be default style.
        assertEquals(Typeface.NORMAL, tp.getTypeface().getStyle());
    }

    @Test(expected=NullPointerException.class)
    public void testUpdateMeasureStateNull() {
        TypefaceSpan typefaceSpan = new TypefaceSpan(FAMILY);

        typefaceSpan.updateMeasureState(null);
    }

    @Test
    public void testUpdateDrawState() {
        TypefaceSpan typefaceSpan = new TypefaceSpan(FAMILY);

        TextPaint tp = new TextPaint();
        assertNull(tp.getTypeface());

        typefaceSpan.updateDrawState(tp);

        assertNotNull(tp.getTypeface());
        // the style should be default style.
        assertEquals(Typeface.NORMAL, tp.getTypeface().getStyle());
    }

    @Test(expected=NullPointerException.class)
    public void testUpdateDrawStateNull() {
        TypefaceSpan typefaceSpan = new TypefaceSpan(FAMILY);

        typefaceSpan.updateDrawState(null);
    }

    @Test
    public void testDescribeContents() {
        TypefaceSpan typefaceSpan = new TypefaceSpan(FAMILY);
        typefaceSpan.describeContents();
    }

    @Test
    public void testGetSpanTypeId() {
        TypefaceSpan typefaceSpan = new TypefaceSpan(FAMILY);
        typefaceSpan.getSpanTypeId();
    }

    @Test
    public void testWriteToParcel() {
        Parcel p = Parcel.obtain();
        try {
            TypefaceSpan typefaceSpan = new TypefaceSpan(FAMILY);
            typefaceSpan.writeToParcel(p, 0);
            p.setDataPosition(0);
            TypefaceSpan t = new TypefaceSpan(p);
            assertEquals(FAMILY, t.getFamily());
        } finally {
            p.recycle();
        }
    }
}
