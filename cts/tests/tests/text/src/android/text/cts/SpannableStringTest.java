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

package android.text.cts;

import static junit.framework.Assert.assertEquals;
import static junit.framework.Assert.assertNotNull;
import static junit.framework.Assert.assertSame;
import static junit.framework.Assert.fail;

import android.support.test.filters.SmallTest;
import android.support.test.runner.AndroidJUnit4;
import android.text.Layout;
import android.text.SpannableString;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.method.SingleLineTransformationMethod;
import android.text.method.TransformationMethod;
import android.text.style.AlignmentSpan;
import android.text.style.LocaleSpan;
import android.text.style.QuoteSpan;
import android.text.style.UnderlineSpan;

import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.Locale;

@SmallTest
@RunWith(AndroidJUnit4.class)
public class SpannableStringTest {

    @Test
    public void testConstructor() {
        new SpannableString("test");

        try {
            new SpannableString(null);
            fail("should throw NullPointerException here");
        } catch (NullPointerException e) {
        }
    }

    @Test
    public void testValueOf() {
        String text = "test valueOf";
        SpannableString spannable = SpannableString.valueOf(text);
        assertNotNull(spannable);
        assertEquals(text, spannable.toString());

        spannable = new SpannableString(text);
        assertSame(spannable, SpannableString.valueOf(spannable));

        try {
            SpannableString.valueOf(null);
            fail("should throw NullPointerException here");
        } catch (NullPointerException e) {
        }
    }

    @Test
    public void testSetSpan() {
        String text = "hello, world";
        SpannableString spannable = new SpannableString(text);

        spannable.setSpan(null, 1, 4, SpannableString.SPAN_POINT_POINT);
        assertEquals(1, spannable.getSpanStart(null));
        assertEquals(4, spannable.getSpanEnd(null));
        assertEquals(SpannableString.SPAN_POINT_POINT, spannable.getSpanFlags(null));

        UnderlineSpan underlineSpan = new UnderlineSpan();
        spannable.setSpan(underlineSpan, 0, 2, SpannableString.SPAN_EXCLUSIVE_EXCLUSIVE);
        assertEquals(0, spannable.getSpanStart(underlineSpan));
        assertEquals(2, spannable.getSpanEnd(underlineSpan));
        assertEquals(SpannableString.SPAN_EXCLUSIVE_EXCLUSIVE,
                spannable.getSpanFlags(underlineSpan));

        try {
            spannable.setSpan(null, 4, 1, SpannableString.SPAN_POINT_POINT);
            fail("should throw IndexOutOfBoundsException here");
        } catch (IndexOutOfBoundsException e) {
        }

        try {
            spannable.setSpan(underlineSpan, -1, text.length() + 1,
                    SpannableString.SPAN_EXCLUSIVE_EXCLUSIVE);
            fail("should throw IndexOutOfBoundsException here");
        } catch (IndexOutOfBoundsException e) {
        }
    }

    @Test
    public void testRemoveSpan() {
        SpannableString spannable = new SpannableString("hello, world");

        spannable.removeSpan(null);

        UnderlineSpan underlineSpan = new UnderlineSpan();
        spannable.setSpan(underlineSpan, 0, 2, 2);
        assertEquals(0, spannable.getSpanStart(underlineSpan));
        assertEquals(2, spannable.getSpanEnd(underlineSpan));
        assertEquals(2, spannable.getSpanFlags(underlineSpan));

        spannable.removeSpan(underlineSpan);
        assertEquals(-1, spannable.getSpanStart(underlineSpan));
        assertEquals(-1, spannable.getSpanEnd(underlineSpan));
        assertEquals(0, spannable.getSpanFlags(underlineSpan));

        // try to test removeSpan when there are no spans
        spannable.removeSpan(underlineSpan);
        assertEquals(-1, spannable.getSpanStart(underlineSpan));
        assertEquals(-1, spannable.getSpanEnd(underlineSpan));
        assertEquals(0, spannable.getSpanFlags(underlineSpan));
    }

    @Test
    public void testSubSequence() {
        String text = "hello, world";
        SpannableString spannable = new SpannableString(text);

        CharSequence subSequence = spannable.subSequence(0, 2);
        assertEquals("he", subSequence.toString());

        subSequence = spannable.subSequence(0, text.length());
        assertEquals(text, subSequence.toString());

        try {
            spannable.subSequence(-1, text.length() + 1);
            fail("subSequence failed when index is out of bounds");
        } catch (StringIndexOutOfBoundsException e) {
        }

        try {
            spannable.subSequence(2, 0);
            fail("subSequence failed on invalid index");
        } catch (StringIndexOutOfBoundsException e) {
        }
    }

    @Test
    public void testSubsequence_copiesSpans() {
        SpannableString first = new SpannableString("t\nest data");
        QuoteSpan quoteSpan = new QuoteSpan();
        LocaleSpan localeSpan = new LocaleSpan(Locale.US);
        UnderlineSpan underlineSpan = new UnderlineSpan();

        first.setSpan(quoteSpan, 0, 2, Spanned.SPAN_PARAGRAPH);
        first.setSpan(localeSpan, 2, 4, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        first.setSpan(underlineSpan, 0, first.length(), Spanned.SPAN_PRIORITY);

        Spanned second = (Spanned) first.subSequence(2,4);
        Object[] secondSpans = second.getSpans(0, second.length(), Object.class);
        assertNotNull(secondSpans);
        assertEquals(2, secondSpans.length);

        //priority flag is first in the results
        Object[][] expected = new Object[][] {
                {underlineSpan, 0, second.length(), Spanned.SPAN_PRIORITY},
                {localeSpan, 0, 2, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE}};

        for (int i = 0; i < secondSpans.length; i++) {
            Object secondSpan = secondSpans[i];
            assertEquals(expected[i][0], secondSpan);
            assertEquals(expected[i][1], second.getSpanStart(secondSpan));
            assertEquals(expected[i][2], second.getSpanEnd(secondSpan));
            assertEquals(expected[i][3], second.getSpanFlags(secondSpan));
        }
    }

    @Test
    public void testCopyConstructor_copiesAllSpans() {
        SpannableString first = new SpannableString("t\nest data");
        first.setSpan(new QuoteSpan(), 0, 2, Spanned.SPAN_PARAGRAPH);
        first.setSpan(new LocaleSpan(Locale.US), 2, 4, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        first.setSpan(new UnderlineSpan(), 0, first.length(), Spanned.SPAN_PRIORITY);
        Object[] firstSpans = first.getSpans(0, first.length(), Object.class);
        assertNotNull(firstSpans);

        SpannableString second = new SpannableString(first);
        Object[] secondSpans = second.getSpans(0, second.length(), Object.class);
        assertNotNull(secondSpans);
        assertEquals("Spans.length should be equal", firstSpans.length, secondSpans.length);
        for (int i = 0; i < firstSpans.length; i++) {
            Object firstSpan = firstSpans[i];
            Object secondSpan = secondSpans[i];
            assertSame(firstSpan, secondSpan);
            assertEquals(first.getSpanStart(firstSpan), second.getSpanStart(secondSpan));
            assertEquals(first.getSpanEnd(firstSpan), second.getSpanEnd(secondSpan));
            assertEquals(first.getSpanFlags(firstSpan), second.getSpanFlags(secondSpan));
        }
    }

    @Test
    public void testCopyGrowable() {
        SpannableString first = new SpannableString("t\nest data");
        final int N_SPANS = 127;
        for (int i = 0; i < N_SPANS; i++) {
            first.setSpan(new QuoteSpan(), 0, 2, Spanned.SPAN_PARAGRAPH);
        }
        SpannableString second = new SpannableString(first.subSequence(0, first.length() - 1));
        second.setSpan(new LocaleSpan(Locale.US), 2, 4, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        Object[] secondSpans = second.getSpans(0, second.length(), Object.class);
        assertEquals(secondSpans.length, N_SPANS + 1);
    }

    @Test
    public void testCopyConstructorDoesNotEnforceParagraphStyleConstraint() {
        final SpannableStringBuilder original = new SpannableStringBuilder("\ntest data\nb");
        final AlignmentSpan.Standard span = new AlignmentSpan.Standard(
                Layout.Alignment.ALIGN_NORMAL);
        original.setSpan(span, 1, original.length() - 1, Spanned.SPAN_PARAGRAPH);

        // test that paragraph style is in the copied when it is valid
        SpannableString copied = new SpannableString(original);
        AlignmentSpan.Standard[] copiedSpans = copied.getSpans(0, copied.length(),
                AlignmentSpan.Standard.class);

        assertEquals(1, copiedSpans.length);

        // test that paragraph style is in not in the copied when it is invalid
        final TransformationMethod transformation = SingleLineTransformationMethod.getInstance();
        final CharSequence transformed = transformation.getTransformation(original, null);
        copied = new SpannableString(transformed);
        copiedSpans = copied.getSpans(0, copied.length(), AlignmentSpan.Standard.class);

        assertEquals(0, copiedSpans.length);
    }
}
