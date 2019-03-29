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
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import android.os.Parcel;
import android.support.test.filters.SmallTest;
import android.support.test.runner.AndroidJUnit4;
import android.text.SpannableString;
import android.text.Spanned;
import android.text.StaticLayout;
import android.text.TextPaint;
import android.text.style.UnderlineSpan;

import org.junit.Test;
import org.junit.runner.RunWith;

@SmallTest
@RunWith(AndroidJUnit4.class)
public class UnderlineSpanTest {
    @Test
    public void testConstructor() {
        new UnderlineSpan();

        final Parcel p = Parcel.obtain();
        try {
            new UnderlineSpan(p);
        } finally {
            p.recycle();
        }
    }

    @Test
    public void testUpdateDrawState() {
        UnderlineSpan underlineSpan = new UnderlineSpan();

        TextPaint tp = new TextPaint();
        tp.setUnderlineText(false);
        assertFalse(tp.isUnderlineText());

        underlineSpan.updateDrawState(tp);
        assertTrue(tp.isUnderlineText());
    }

    @Test(expected=NullPointerException.class)
    public void testUpdateDrawStateNull() {
        UnderlineSpan underlineSpan = new UnderlineSpan();

        underlineSpan.updateDrawState(null);
    }

    @Test
    public void testDescribeContents() {
        UnderlineSpan underlineSpan = new UnderlineSpan();
        underlineSpan.describeContents();
    }

    @Test
    public void testGetSpanTypeId() {
        UnderlineSpan underlineSpan = new UnderlineSpan();
        underlineSpan.getSpanTypeId();
    }

    @Test
    public void testWriteToParcel() {
        Parcel p = Parcel.obtain();
        try {
            UnderlineSpan underlineSpan = new UnderlineSpan();
            underlineSpan.writeToParcel(p, 0);
            new UnderlineSpan(p);
        } finally {
            p.recycle();
        }
    }

    // Measures the width of some potentially-spanned text, assuming it's not too wide.
    private float textWidth(CharSequence text) {
        final TextPaint tp = new TextPaint();
        tp.setTextSize(100.0f); // Large enough so that the difference in kerning is visible.
        final int largeWidth = 10000; // Enough width so the whole text fits in one line.
        final StaticLayout layout = StaticLayout.Builder.obtain(
                text, 0, text.length(), tp, largeWidth).build();
        return layout.getLineWidth(0);
    }

    @Test
    public void testDoesntAffectWidth() {
        {
            // Roboto kerns between "P" and "."
            final SpannableString text = new SpannableString("P.");
            final float origLineWidth = textWidth(text);
            // Underline just the "P".
            text.setSpan(new UnderlineSpan(), 0, 1, Spanned.SPAN_INCLUSIVE_INCLUSIVE);
            final float underlinedLineWidth = textWidth(text);
            assertEquals(origLineWidth, underlinedLineWidth, 0.0f);
        }
        {
            final SpannableString text = new SpannableString("P.");
            final float origLineWidth = textWidth(text);
            // Underline just the period.
            text.setSpan(new UnderlineSpan(), 1, 2, Spanned.SPAN_INCLUSIVE_INCLUSIVE);
            final float underlinedLineWidth = textWidth(text);
            assertEquals(origLineWidth, underlinedLineWidth, 0.0f);
        }
        {
            final SpannableString text = new SpannableString("P. P.");
            final float origLineWidth = textWidth(text);
            // Underline just the second "P".
            text.setSpan(new UnderlineSpan(), 3, 4, Spanned.SPAN_INCLUSIVE_INCLUSIVE);
            final float underlinedLineWidth = textWidth(text);
            assertEquals(origLineWidth, underlinedLineWidth, 0.0f);
        }
        {
            final SpannableString text = new SpannableString("P. P.");
            final float origLineWidth = textWidth(text);
            // Underline both "P"s.
            text.setSpan(new UnderlineSpan(), 0, 1, Spanned.SPAN_INCLUSIVE_INCLUSIVE);
            text.setSpan(new UnderlineSpan(), 3, 4, Spanned.SPAN_INCLUSIVE_INCLUSIVE);
            final float underlinedLineWidth = textWidth(text);
            assertEquals(origLineWidth, underlinedLineWidth, 0.0f);
        }
    }

    private class SafeUnderlineSpan extends UnderlineSpan {
    }

    // Identical to the normal UnderlineSpan test, except that a subclass of UnderlineSpan is used.
    // The subclass is identical to UnderlineSpan in visual behavior, so it shouldn't affect width
    // either.
    @Test
    public void testDoesntAffectWidth_safeSubclass() {
        // Roboto kerns between "P" and "."
        final SpannableString text = new SpannableString("P.");
        final float origLineWidth = textWidth(text);
        // Underline just the "P".
        text.setSpan(new SafeUnderlineSpan(), 0, 1, Spanned.SPAN_INCLUSIVE_INCLUSIVE);
        final float underlinedLineWidth = textWidth(text);
        assertEquals(origLineWidth, underlinedLineWidth, 0.0f);
    }

    private class NoUnderlineSpan extends UnderlineSpan {
        @Override
        public void updateDrawState(TextPaint ds) {
            // do not draw an underline
        }
    }

    // Identical to the normal UnderlineSpan test, except that a subclass of UnderlineSpan is used
    // that doesn't draw an underline. This shouldn't affect width either.
    @Test
    public void testDoesntAffectWidth_noUnderlineSubclass() {
        // Roboto kerns between "P" and "."
        final SpannableString text = new SpannableString("P.");
        final float origLineWidth = textWidth(text);
        // Underline just the "P".
        text.setSpan(new NoUnderlineSpan(), 0, 1, Spanned.SPAN_INCLUSIVE_INCLUSIVE);
        final float underlinedLineWidth = textWidth(text);
        assertEquals(origLineWidth, underlinedLineWidth, 0.0f);
    }

    private class ElegantUnderlineSpan extends UnderlineSpan {
        @Override
        public void updateDrawState(TextPaint ds) {
            super.updateDrawState(ds);
            ds.setElegantTextHeight(true);
        }
    }

    // Identical to the normal UnderlineSpan test, except that a subclass of UnderlineSpan is used
    // that draws an underline and sets the font to elegant style. Since we may actually be
    // changing fonts at the span boundary, this should increase width. Note that this subclass is
    // not declared entirely correctly, and since it may affect metrics it should also extend
    // MetricAffectingSpan, but we need to keep it behaving correctly for backward compatibility.
    @Test
    public void testAffectsWidth_ElegantSubclass() {
        // Roboto kerns between "P" and "."
        final SpannableString text = new SpannableString("P.");
        final float origLineWidth = textWidth(text);
        // Underline just the "P".
        text.setSpan(new ElegantUnderlineSpan(), 0, 1, Spanned.SPAN_INCLUSIVE_INCLUSIVE);
        final float underlinedLineWidth = textWidth(text);
        assertTrue(underlinedLineWidth > origLineWidth + 1.0f);
    }
}
