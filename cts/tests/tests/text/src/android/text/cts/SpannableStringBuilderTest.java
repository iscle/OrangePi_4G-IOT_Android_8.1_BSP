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


import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import android.support.test.filters.SmallTest;
import android.support.test.runner.AndroidJUnit4;
import android.text.Editable;
import android.text.InputFilter;
import android.text.Selection;
import android.text.SpannableString;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.TextWatcher;
import android.text.style.QuoteSpan;
import android.text.style.StrikethroughSpan;
import android.text.style.SubscriptSpan;
import android.text.style.TabStopSpan;
import android.text.style.UnderlineSpan;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.Arrays;

/**
 * Test {@link SpannableStringBuilder}.
 */
@SmallTest
@RunWith(AndroidJUnit4.class)
public class SpannableStringBuilderTest {
    private StrikethroughSpan mStrikethroughSpan;
    private UnderlineSpan mUnderlineSpan;

    @Before
    public void setup() {
        mUnderlineSpan = new UnderlineSpan();
        mStrikethroughSpan = new StrikethroughSpan();
    }

    @Test
    public void testConstructor() {
        new SpannableStringBuilder();
        new SpannableStringBuilder("test");
    }

    @Test(expected=NullPointerException.class)
    public void testConstructorNullString() {
         new SpannableStringBuilder(null);
    }

    @Test
    public void testConstructorStartEnd() {
        new SpannableStringBuilder("Text", 0, "Text".length());
        new SpannableStringBuilder(new SpannableString("test"), 0, "Text".length());
    }

    @Test(expected=StringIndexOutOfBoundsException.class)
    public void testConstructorStartEndEndTooLarge() {
        new SpannableStringBuilder("Text", 0, 10);
    }

    @Test(expected=StringIndexOutOfBoundsException.class)
    public void testConstructorStartEndStartTooLow() {
        new SpannableStringBuilder("Text", -3, 3);
    }

    @Test(expected=StringIndexOutOfBoundsException.class)
    public void testConstructorStartEndEndTooLow() {
        new SpannableStringBuilder("Text", 3, 0);
    }

    @Test
    public void testGetSpanFlags() {
        SpannableStringBuilder builder = new SpannableStringBuilder("spannable string");
        assertEquals(0, builder.getSpanFlags(mUnderlineSpan));

        builder.setSpan(mUnderlineSpan, 2, 4, Spanned.SPAN_INCLUSIVE_INCLUSIVE);
        builder.setSpan(mUnderlineSpan, 0, 1, Spanned.SPAN_EXCLUSIVE_INCLUSIVE);
        builder.setSpan(mStrikethroughSpan, 5, 7, Spanned.SPAN_INCLUSIVE_EXCLUSIVE);

        assertEquals(Spanned.SPAN_EXCLUSIVE_INCLUSIVE, builder.getSpanFlags(mUnderlineSpan));
        assertEquals(Spanned.SPAN_INCLUSIVE_EXCLUSIVE, builder.getSpanFlags(mStrikethroughSpan));
        assertEquals(0, builder.getSpanFlags(new Object()));
    }

    @Test
    public void testNextSpanTransition() {
        SpannableStringBuilder builder = new SpannableStringBuilder("spannable string");

        builder.setSpan(mUnderlineSpan, 1, 2, Spanned.SPAN_INCLUSIVE_INCLUSIVE);
        builder.setSpan(mUnderlineSpan, 3, 4, Spanned.SPAN_INCLUSIVE_INCLUSIVE);
        builder.setSpan(mStrikethroughSpan, 5, 6, Spanned.SPAN_INCLUSIVE_INCLUSIVE);
        builder.setSpan(mStrikethroughSpan, 8, 9, Spanned.SPAN_INCLUSIVE_INCLUSIVE);

        assertEquals(8, builder.nextSpanTransition(0, 10, StrikethroughSpan.class));
        assertEquals(10, builder.nextSpanTransition(0, 10, TabStopSpan.class));
        assertEquals(3, builder.nextSpanTransition(0, 5, null));
        assertEquals(100, builder.nextSpanTransition(-5, 100, TabStopSpan.class));
        assertEquals(1, builder.nextSpanTransition(3, 1, UnderlineSpan.class));
    }

    @Test
    public void testSetSpan() {
        SpannableStringBuilder builder = new SpannableStringBuilder("hello, world");
        try {
            builder.setSpan(mUnderlineSpan, 4, 1, Spanned.SPAN_INCLUSIVE_INCLUSIVE);
            fail("should throw IndexOutOfBoundsException here");
        } catch (IndexOutOfBoundsException e) {
            // expected exception
        }

        try {
            builder.setSpan(mUnderlineSpan, -1, 100, Spanned.SPAN_POINT_POINT);
            fail("should throw ..IndexOutOfBoundsException here");
        } catch (IndexOutOfBoundsException e) {
            // expected exception
        }

        builder.setSpan(null, 1, 4, Spanned.SPAN_POINT_POINT);
        assertEquals(1, builder.getSpanStart(null));
        assertEquals(4, builder.getSpanEnd(null));
        assertEquals(Spanned.SPAN_POINT_POINT, builder.getSpanFlags(null));

        builder.setSpan(mUnderlineSpan, 0, 2, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        assertEquals(0, builder.getSpanStart(mUnderlineSpan));
        assertEquals(2, builder.getSpanEnd(mUnderlineSpan));
        assertEquals(Spanned.SPAN_EXCLUSIVE_EXCLUSIVE, builder.getSpanFlags(mUnderlineSpan));
    }

    @Test(expected=NullPointerException.class)
    public void testValueOfNull() {
        SpannableStringBuilder.valueOf(null);
    }

    @Test(expected=NullPointerException.class)
    public void testValueOfNullBuilder() {
        SpannableStringBuilder.valueOf((SpannableStringBuilder) null);
    }

    @Test
    public void testValueOf() {
        assertNotNull(SpannableStringBuilder.valueOf("hello, string"));

        SpannableStringBuilder builder = new SpannableStringBuilder("hello, world");
        assertSame(builder, SpannableStringBuilder.valueOf(builder));
    }

    @Test
    public void testReplace1() {
        SpannableStringBuilder builder = new SpannableStringBuilder("hello, world!");
        CharSequence text = "hi";
        builder.replace(0, 5, text);
        assertEquals("hi, world!", builder.toString());

        builder = new SpannableStringBuilder("hello, world!");
        builder.replace(7, 12, "google");
        assertEquals("hello, google!", builder.toString());

        try {
            builder.replace(4, 2, text);
            fail("should throw IndexOutOfBoundsException here");
        } catch (IndexOutOfBoundsException e) {
            // expected exception
        }

        try {
            builder.replace(-4, 100, text);
            fail("should throw IndexOutOfBoundsException here");
        } catch (IndexOutOfBoundsException e) {
            // expected exception
        }

        try {
            builder.replace(0, 1, null);
            fail("should throw NullPointerException here");
        } catch (NullPointerException e) {
            // expected exception
        }
    }

    @Test
    public void testReplace2() {
        SpannableStringBuilder builder = new SpannableStringBuilder("hello, world");
        CharSequence text = "ahiabc";
        builder.replace(0, 5, text, 3, text.length());
        assertEquals("abc, world", builder.toString());

        builder = new SpannableStringBuilder("hello, world");
        builder.replace(3, 5, text, 3, text.length());
        assertEquals("helabc, world", builder.toString());

        builder = new SpannableStringBuilder("hello, world");
        builder.replace(0, 5, text, 0, text.length());
        assertEquals("ahiabc, world", builder.toString());

        // Replacing by an empty string (identical target indexes)
        builder = new SpannableStringBuilder("hello, world");
        builder.replace(4, 6, "", 0, 0);
        assertEquals("hell world", builder.toString());

        builder = new SpannableStringBuilder("hello, world");
        builder.replace(4, 6, "any string", 5, 5);
        assertEquals("hell world", builder.toString());

        // Inserting in place (no deletion)
        builder = new SpannableStringBuilder("hello, world");
        builder.replace(3, 3, "any string", 0, 0);
        assertEquals("hello, world", builder.toString());

        builder = new SpannableStringBuilder("hello, world");
        builder.replace(7, 7, "nice ", 0, 5);
        assertEquals("hello, nice world", builder.toString());

        builder = new SpannableStringBuilder("hello, world");
        builder.replace(0, 0, "say ", 1, 4);
        assertEquals("ay hello, world", builder.toString());

        try {
            builder.replace(0, 5, text, 10, 3);
            fail("should throw IndexOutOfBoundsException here");
        } catch (IndexOutOfBoundsException e) {
            // expected exception
        }

        try {
            builder.replace(0, 5, text, -1, 100);
            fail("should throw IndexOutOfBoundsException here");
        } catch (IndexOutOfBoundsException e) {
            // expected exception
        }

        try {
            builder.replace(-1, 100, text, 10, 3);
            fail("should throw IndexOutOfBoundsException here");
        } catch (IndexOutOfBoundsException e) {
            // expected exception
        }

        try {
            builder.replace(3, 1, text, -1, 100);
            fail("should throw IndexOutOfBoundsException here");
        } catch (IndexOutOfBoundsException e) {
            // expected exception
        }

        // unexpected IndexOutOfBoundsException
        try {
            builder.replace(0, 5, null, 1, 2);
            fail("should throw IndexOutOfBoundsException here");
        } catch (IndexOutOfBoundsException e) {
            // expected exception
        } catch (NullPointerException e) {
            // expected exception
        }
    }

    @Test
    public void testSubSequence() {
        SpannableStringBuilder builder = new SpannableStringBuilder("hello, world");
        CharSequence text = builder.subSequence(0, 2);
        assertNotNull(text);
        assertTrue(text instanceof SpannableStringBuilder);
        assertEquals("he", text.toString());
        try {
            builder.subSequence(2, 0);
            fail("should throw IndexOutOfBoundsException here");
        } catch (IndexOutOfBoundsException e) {
            // expected exception
        }
    }

    @Test
    public void testGetChars() {
        SpannableStringBuilder builder = new SpannableStringBuilder("hello");
        char[] buf = new char[4];
        buf[0] = 'x';

        builder.getChars(0, 3, buf, 1);
        assertEquals("xhel", String.valueOf(buf));

        builder.getChars(1, 5, buf, 0);
        assertEquals("ello", String.valueOf(buf));

        try {
            builder.getChars(-1, 10, buf, 1);
            fail("should throw IndexOutOfBoundsException here");
        } catch (IndexOutOfBoundsException e) {
            // expected exception
        }

        try {
            builder.getChars(3, 2, buf, 0);
            fail("should throw IndexOutOfBoundsException here");
        } catch (IndexOutOfBoundsException e) {
            // expected exception
        }

        try {
            builder.getChars(1, 2, null, 0);
            fail("should throw NullPointerException here");
        } catch (NullPointerException e) {
            // expected exception
        }
    }

    @Test
    public void testAppend1() {
        SpannableStringBuilder builder = new SpannableStringBuilder("hello");
        builder.append(",world");
        assertEquals("hello,world", builder.toString());
        try {
            builder.append(null);
            fail("should throw NullPointerException here");
        } catch (NullPointerException e) {
            // expected exception
        }
    }

    @Test
    public void testAppend2() {
        SpannableStringBuilder builder = new SpannableStringBuilder("hello");
        builder.append(",world", 1, 3);
        assertEquals("hellowo", builder.toString());

        builder = new SpannableStringBuilder("hello");
        builder.append(",world", 0, 4);
        assertEquals("hello,wor", builder.toString());

        try {
            builder.append(null, 0, 1);
            fail("should throw NullPointerException here");
        } catch (NullPointerException e) {
            // expected exception
        }

        try {
            builder.append(",world", -1, 10);
            fail("should throw StringIndexOutOfBoundsException here");
        } catch (StringIndexOutOfBoundsException e) {
            // expected exception
        }

        try {
            builder.append(",world", 4, 1);
            fail("should throw StringIndexOutOfBoundsException here");
        } catch (StringIndexOutOfBoundsException e) {
            // expected exception
        }
    }

    @Test
    public void testAppend3() {
        SpannableStringBuilder builder = new SpannableStringBuilder("hello");
        builder.append('a');
        builder.append('b');
        builder.append('c');

        assertEquals("helloabc", builder.toString());
        try {
            builder.append(null);
            fail("should throw NullPointerException here");
        } catch (NullPointerException e) {
            // expected exception
        }
    }

    @Test
    public void testAppend_textWithSpan() {
        final QuoteSpan span = new QuoteSpan();
        final SpannableStringBuilder builder = new SpannableStringBuilder("hello ");
        final int spanStart = builder.length();
        builder.append("planet", span, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        final int spanEnd = builder.length();
        builder.append(" earth");

        assertEquals("SpannableStringBuilder.append should append text to existing whole text",
                "hello planet earth", builder.toString());

        final Object[] spans = builder.getSpans(0, builder.length(), Object.class);
        assertNotNull("Appended text included a Quote span", spans);
        assertEquals("Appended text included a Quote span", 1, spans.length);
        assertSame("Should be the same span instance", span, spans[0]);
        assertEquals("Appended span should start at appended text start",
                spanStart, builder.getSpanStart(spans[0]));
        assertEquals("Appended span should end at appended text end",
                spanEnd, builder.getSpanEnd(spans[0]));
    }

    @Test
    public void testClearSpans() {
        SpannableStringBuilder builder = new SpannableStringBuilder("hello, world");

        builder.setSpan(mUnderlineSpan, 0, 2, 2);
        assertEquals(0, builder.getSpanStart(mUnderlineSpan));
        assertEquals(2, builder.getSpanEnd(mUnderlineSpan));
        assertEquals(2, builder.getSpanFlags(mUnderlineSpan));

        builder.clearSpans();
        assertEquals(-1, builder.getSpanStart(mUnderlineSpan));
        assertEquals(-1, builder.getSpanEnd(mUnderlineSpan));
        assertEquals(0, builder.getSpanFlags(mUnderlineSpan));
    }

    @Test
    public void testGetSpanStart() {
        SpannableStringBuilder builder = new SpannableStringBuilder("hello");
        builder.setSpan(mUnderlineSpan, 1, 3, 0);
        assertEquals(1, builder.getSpanStart(mUnderlineSpan));
        assertEquals(-1, builder.getSpanStart(mStrikethroughSpan));
        assertEquals(-1, builder.getSpanStart(null));
    }

    @Test
    public void testAccessFilters() {
        InputFilter[] filters = new InputFilter[100];
        SpannableStringBuilder builder = new SpannableStringBuilder();
        builder.setFilters(filters);
        assertSame(filters, builder.getFilters());

        try {
            builder.setFilters(null);
            fail("should throw IllegalArgumentException here");
        } catch (IllegalArgumentException e) {
            // expected exception
        }
    }

    @Test
    public void testRemoveSpan() {
        SpannableStringBuilder builder = new SpannableStringBuilder("hello, world");

        builder.setSpan(mUnderlineSpan, 0, 2, 2);
        assertEquals(0, builder.getSpanStart(mUnderlineSpan));
        assertEquals(2, builder.getSpanEnd(mUnderlineSpan));
        assertEquals(2, builder.getSpanFlags(mUnderlineSpan));

        builder.removeSpan(new Object());
        assertEquals(0, builder.getSpanStart(mUnderlineSpan));
        assertEquals(2, builder.getSpanEnd(mUnderlineSpan));
        assertEquals(2, builder.getSpanFlags(mUnderlineSpan));

        builder.removeSpan(mUnderlineSpan);
        assertEquals(-1, builder.getSpanStart(mUnderlineSpan));
        assertEquals(-1, builder.getSpanEnd(mUnderlineSpan));
        assertEquals(0, builder.getSpanFlags(mUnderlineSpan));

        builder.removeSpan(mUnderlineSpan);
        builder.removeSpan(null);
    }

    @Test
    public void testToString() {
        SpannableStringBuilder builder = new SpannableStringBuilder("hello");
        assertEquals("hello", builder.toString());

        builder = new SpannableStringBuilder();
        assertEquals("", builder.toString());
    }

    @Test
    public void testGetSpanEnd() {
        SpannableStringBuilder builder = new SpannableStringBuilder("hello");
        builder.setSpan(mUnderlineSpan, 1, 3, 0);
        assertEquals(3, builder.getSpanEnd(mUnderlineSpan));
        assertEquals(-1, builder.getSpanEnd(mStrikethroughSpan));
        assertEquals(-1, builder.getSpanEnd(null));
    }

    @Test
    public void testCharAt() {
        SpannableStringBuilder builder = new SpannableStringBuilder("hello");
        assertEquals('h', builder.charAt(0));
        assertEquals('e', builder.charAt(1));
        try {
            builder.charAt(10);
            fail("should throw IndexOutOfBoundsException");
        } catch (IndexOutOfBoundsException e) {
            // expected exception
        }

        try {
            builder.charAt(-1);
            fail("should throw IndexOutOfBoundsException");
        } catch (IndexOutOfBoundsException e) {
            // expected exception
        }
    }

    @Test
    public void testInsert1() {
        SpannableStringBuilder builder = new SpannableStringBuilder("hello");
        builder.insert(1, "abcd", 1, 3);
        assertEquals("hbcello", builder.toString());

        builder = new SpannableStringBuilder("hello");
        builder.insert(2, "abcd", 0, 4);
        assertEquals("heabcdllo", builder.toString());

        try {
            builder.insert(-1, "abcd", 1, 3);
            fail("should throw IndexOutOfBoundsException");
        } catch (IndexOutOfBoundsException e) {
            // expected exception
        }

        try {
            builder.insert(100, "abcd", 1, 3);
            fail("should throw IndexOutOfBoundsException");
        } catch (IndexOutOfBoundsException e) {
            // expected exception
        }

        try {
            builder.insert(1, "abcd", 3, 2);
            fail("should throw IndexOutOfBoundsException");
        } catch (IndexOutOfBoundsException e) {
            // expected exception
        }

        try {
            builder.insert(1, "abcd", -3, 2);
            fail("should throw IndexOutOfBoundsException");
        } catch (IndexOutOfBoundsException e) {
            // expected exception
        }

        try {
            builder.insert(0, null, 0, 1);
            fail("should throw NullPointerException");
        } catch (NullPointerException e) {
            // expected exception
        }
    }

    @Test
    public void testInsert2() {
        SpannableStringBuilder builder = new SpannableStringBuilder("hello");
        builder.insert(1, "abcd");
        assertEquals("habcdello", builder.toString());

        builder = new SpannableStringBuilder("hello");
        builder.insert(5, "abcd");
        assertEquals("helloabcd", builder.toString());

        try {
            builder.insert(-1, "abcd");
            fail("should throw IndexOutOfBoundsException");
        } catch (IndexOutOfBoundsException e) {
            // expected exception
        }

        try {
            builder.insert(100, "abcd");
            fail("should throw IndexOutOfBoundsException");
        } catch (IndexOutOfBoundsException e) {
            // expected exception
        }

        try {
            builder.insert(0, null);
            fail("should throw NullPointerException");
        } catch (NullPointerException e) {
            // expected exception
        }
    }

    @Test
    public void testClear() {
        SpannableStringBuilder builder = new SpannableStringBuilder("hello");
        assertEquals("hello", builder.toString());
        builder.clear();
        assertEquals("", builder.toString());
    }

    @Test
    public void testGetSpans() {
        SpannableStringBuilder builder = new SpannableStringBuilder("hello, world");
        UnderlineSpan span1 = new UnderlineSpan();
        UnderlineSpan span2 = new UnderlineSpan();
        builder.setSpan(span1, 1, 2, Spanned.SPAN_POINT_POINT);
        builder.setSpan(span2, 4, 8, Spanned.SPAN_MARK_POINT);

        Object[] emptySpans = builder.getSpans(0, 10, null);
        assertNotNull(emptySpans);
        assertEquals(0, emptySpans.length);

        UnderlineSpan[] underlineSpans = builder.getSpans(0, 10, UnderlineSpan.class);
        assertEquals(2, underlineSpans.length);
        assertSame(span1, underlineSpans[0]);
        assertSame(span2, underlineSpans[1]);

        StrikethroughSpan[] strikeSpans = builder.getSpans(0, 10, StrikethroughSpan.class);
        assertEquals(0, strikeSpans.length);

        builder.getSpans(-1, 100, UnderlineSpan.class);

        builder.getSpans(4, 1, UnderlineSpan.class);
    }

    @Test
    public void testGetSpans_returnsEmptyIfSetSpanIsNotCalled() {
        String text = "p_in_s";
        SpannableStringBuilder builder = new SpannableStringBuilder(text);
        Object[] spans = builder.getSpans(0, text.length(), Object.class);
        assertEquals(0, spans.length);
    }

    @Test
    public void testGetSpans_returnsInInsertionOrder_regular() {
        assertGetSpans_returnsInInsertionOrder(Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
    }

    @Test
    public void testGetSpans_returnsInInsertionOrder_priority() {
        assertGetSpans_returnsInInsertionOrder(
                Spanned.SPAN_EXCLUSIVE_EXCLUSIVE | Spanned.SPAN_PRIORITY);
    }

    private static void assertGetSpans_returnsInInsertionOrder(int flags) {
        final SpannableStringBuilder builder = new SpannableStringBuilder();
        final SubscriptSpan[] expected = new SubscriptSpan[5];
        for (int i = 0; i < expected.length; i++) {
            final int currentLength = builder.length();
            builder.append("12\n");
            expected[i] = new SubscriptSpan();
            builder.setSpan(expected[i], currentLength + 1, currentLength + 2, flags);
        }

        final SubscriptSpan[] spans = builder.getSpans(0, builder.length(), SubscriptSpan.class);

        assertNotNull(spans);
        assertEquals(expected.length, spans.length);
        for (int i = 0; i < expected.length; i++) {
            assertSame(expected[i], spans[i]);
        }
    }

    @Test
    public void testGetSpans_returnsInInsertionOrder_priorityAndRegular() {
        // insert spans from end to start of the string, interleaved. for each alternation:
        // * regular span start is less than the priority span;
        // * setSpan for regular span is called before the priority span
        // expected result is: priority spans in the insertion order, then regular spans in
        // insertion order
        final int arrayLength = 5;
        final SpannableStringBuilder builder = new SpannableStringBuilder();
        final SubscriptSpan[] regularSpans = new SubscriptSpan[arrayLength];
        final SubscriptSpan[] prioritySpans = new SubscriptSpan[arrayLength];
        for (int i = 0; i < arrayLength; i++) {
            builder.append("12");
            regularSpans[i] = new SubscriptSpan();
            prioritySpans[i] = new SubscriptSpan();
        }

        // set spans on builder with pattern [regular, priority, regular, priority,...]
        // in reverse order (regularSpan[0] is at the end of the builder, regularSpan[5] is at the
        // begining.
        for (int i = 1; i <= arrayLength; i++) {
            final int spanStart = builder.length() - (i * 2);
            builder.setSpan(regularSpans[i - 1], spanStart, spanStart + 1,
                    Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            builder.setSpan(prioritySpans[i - 1], spanStart + 1, spanStart + 2,
                    Spanned.SPAN_EXCLUSIVE_EXCLUSIVE | Spanned.SPAN_PRIORITY);
        }

        final SubscriptSpan[] spans = builder.getSpans(0, builder.length(), SubscriptSpan.class);
        assertNotNull(spans);
        assertEquals(arrayLength * 2, spans.length);

        // assert priority spans are returned before the regular spans, in the insertion order
        for (int i = 0; i < arrayLength; i++) {
            assertSame(prioritySpans[i], spans[i]);
        }

        // assert regular spans are returned after priority spans, in the insertion order
        for (int i = 0; i < arrayLength; i++) {
            assertSame(regularSpans[i], spans[i + arrayLength]);
        }
    }

    @Test
    public void testGetSpans_returnsSpansInInsertionOrderWhenTheLaterCoversTheFirst() {
        String text = "p_in_s";
        SpannableStringBuilder builder = new SpannableStringBuilder(text);
        Object first = new SubscriptSpan();
        Object second = new SubscriptSpan();
        int flags = Spanned.SPAN_EXCLUSIVE_EXCLUSIVE;
        builder.setSpan(first, 2, 4, flags);
        builder.setSpan(second, 0, text.length(), flags);

        Object[] spans = builder.getSpans(0, text.length(), Object.class);

        assertNotNull(spans);
        assertEquals(2, spans.length);
        assertEquals(first, spans[0]);
        assertEquals(second, spans[1]);
    }

    @Test
    public void testGetSpans_returnsSpansSortedFirstByPriorityThenByInsertionOrder() {
        String text = "p_in_s";
        SpannableStringBuilder builder = new SpannableStringBuilder(text);
        Object first = new SubscriptSpan();
        Object second = new SubscriptSpan();
        Object third = new SubscriptSpan();
        Object fourth = new SubscriptSpan();

        int flags = Spanned.SPAN_EXCLUSIVE_EXCLUSIVE;
        int flagsPriority = Spanned.SPAN_EXCLUSIVE_EXCLUSIVE | Spanned.SPAN_PRIORITY;

        builder.setSpan(first, 2, 4, flags);
        builder.setSpan(second, 2, 4, flagsPriority);
        builder.setSpan(third, 0, text.length(), flags);
        builder.setSpan(fourth, 0, text.length(), flagsPriority);

        Object[] spans = builder.getSpans(0, text.length(), Object.class);

        assertNotNull(spans);
        assertEquals(4, spans.length);
        assertEquals(second, spans[0]);
        assertEquals(fourth, spans[1]);
        assertEquals(first, spans[2]);
        assertEquals(third, spans[3]);
    }

    @Test
    public void testGetSpans_returnsSpansInInsertionOrderAfterRemoveSpanCalls() {
        String text = "p_in_s";
        SpannableStringBuilder builder = new SpannableStringBuilder(text);
        Object first = new SubscriptSpan();
        Object second = new SubscriptSpan();
        Object third = new SubscriptSpan();
        Object fourth = new SubscriptSpan();

        int flags = Spanned.SPAN_EXCLUSIVE_EXCLUSIVE;
        builder.setSpan(first, 2, 4, flags);
        builder.setSpan(second, 0, text.length(), flags);
        builder.setSpan(third, 2, 4, flags);
        builder.removeSpan(first);
        builder.removeSpan(second);
        builder.setSpan(fourth, 0, text.length(), flags);

        Object[] spans = builder.getSpans(0, text.length(), Object.class);

        assertNotNull(spans);
        assertEquals(2, spans.length);
        assertEquals(third, spans[0]);
        assertEquals(fourth, spans[1]);
    }

    @Test
    public void testLength() {
        SpannableStringBuilder builder = new SpannableStringBuilder("hello");
        assertEquals(5, builder.length());
        builder.clear();
        assertEquals(0, builder.length());
    }

    @Test
    public void testReplace_shouldNotThrowIndexOutOfBoundsExceptionForLongText() {
        final char[] charArray = new char[75000];
        Arrays.fill(charArray, 'a');
        final String text = new String(charArray, 0, 50000);
        final String copiedText = new String(charArray);
        final SpannableStringBuilder spannable = new SpannableStringBuilder(text);
        Selection.setSelection(spannable, text.length());

        spannable.replace(0, text.length(), copiedText);

        assertEquals(copiedText.length(), spannable.length());
    }

    @Test
    public void testDelete() {
        SpannableStringBuilder builder = new SpannableStringBuilder("hello,world");
        assertEquals("hello,world", builder.toString());
        builder.delete(0, 5);
        assertEquals(",world", builder.toString());

        builder = new SpannableStringBuilder("hello,world");
        assertEquals("hello,world", builder.toString());
        builder.delete(2, 4);
        assertEquals("heo,world", builder.toString());

        try {
            builder.delete(-1, 100);
            fail("should throw IndexOutOfBoundsException");
        } catch (IndexOutOfBoundsException e) {
            // expected exception
        }

        try {
            builder.delete(4, 1);
            fail("should throw IndexOutOfBoundsException");
        } catch (IndexOutOfBoundsException e) {
            // expected exception
        }
    }

    private static class MockTextWatcher implements TextWatcher {
        private int mDepth = 0;

        @Override
        public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            SpannableStringBuilder builder = (SpannableStringBuilder)s;
            mDepth++;
            assertEquals(mDepth, builder.getTextWatcherDepth());
            mDepth--;
        }

        @Override
        public void onTextChanged(CharSequence s, int start, int before, int count) {
            SpannableStringBuilder builder = (SpannableStringBuilder)s;
            mDepth++;
            assertEquals(mDepth, builder.getTextWatcherDepth());
            mDepth--;
        }

        @Override
        public void afterTextChanged(Editable s) {
            SpannableStringBuilder builder = (SpannableStringBuilder)s;
            mDepth++;
            assertEquals(mDepth, builder.getTextWatcherDepth());
            if (mDepth <= builder.length()) {
                // This will recursively call afterTextChanged.
                builder.replace(mDepth - 1, mDepth, "a");
            }
            mDepth--;
        }
    }

    @Test
    public void testGetTextWatcherDepth() {
        SpannableStringBuilder builder = new SpannableStringBuilder("hello");
        builder.setSpan(new MockTextWatcher(), 0, builder.length(), 0);
        assertEquals(0, builder.getTextWatcherDepth());
        builder.replace(0, 1, "H");
        assertEquals(0, builder.getTextWatcherDepth());
        // MockTextWatcher replaces each character with 'a'.
        assertEquals("aaaaa", builder.toString());
    }
}
