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

package android.text.method.cts;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNotSame;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;

import android.support.test.filters.MediumTest;
import android.support.test.runner.AndroidJUnit4;
import android.text.InputType;
import android.text.Spannable;
import android.text.SpannableString;
import android.text.Spanned;
import android.text.method.DigitsKeyListener;
import android.view.KeyEvent;

import com.android.compatibility.common.util.CtsKeyEventUtil;

import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.Locale;

/**
 * Test {@link DigitsKeyListener}.
 */
@MediumTest
@RunWith(AndroidJUnit4.class)
public class DigitsKeyListenerTest extends KeyListenerTestCase {
    @Test
    public void testConstructor() {
        new DigitsKeyListener();
        new DigitsKeyListener(true, true);
        new DigitsKeyListener(true, false);
        new DigitsKeyListener(false, true);
        new DigitsKeyListener(false, false);

        new DigitsKeyListener(Locale.US);
        new DigitsKeyListener(Locale.US, true, true);
        new DigitsKeyListener(Locale.US, true, false);
        new DigitsKeyListener(Locale.US, false, true);
        new DigitsKeyListener(Locale.US, false, false);

        final Locale ir = Locale.forLanguageTag("fa-IR");
        new DigitsKeyListener(ir);
        new DigitsKeyListener(ir, true, true);
        new DigitsKeyListener(ir, true, false);
        new DigitsKeyListener(ir, false, true);
        new DigitsKeyListener(ir, false, false);
    }

    /*
     * Check point:
     * Current accepted characters are '0', '1', '2', '3', '4', '5', '6', '7', '8', '9'.
     * 1. filter "123456", return null.
     * 2. filter "a1b2c3d", return "123"
     * 3. filter "-a1.b2c3d", return "123"
     * 4. filter "+a1.b2c3d", return "123"
     * 5. filter Spanned("-a1.b2c3d"), return Spanned("123") and copy spans.
     * 6. filter "", return null
     */
    @Test
    public void testFilter1() {
        String source = "123456";
        String destString = "dest string";

        DigitsKeyListener digitsKeyListener = DigitsKeyListener.getInstance();
        SpannableString dest = new SpannableString(destString);
        assertNull(digitsKeyListener.filter(source, 0, source.length(),
                dest, 0, dest.length()));
        assertEquals(destString, dest.toString());

        source = "a1b2c3d";
        assertEquals("123", (digitsKeyListener.filter(source, 0, source.length(),
                dest, 0, dest.length())).toString());
        assertEquals(destString, dest.toString());

        source = "-a1.b2c3d";
        assertEquals("123", (digitsKeyListener.filter(source, 0, source.length(),
                dest, 0, dest.length())).toString());
        assertEquals(destString, dest.toString());

        source = "+a1.b2c3d";
        assertEquals("123", (digitsKeyListener.filter(source, 0, source.length(),
                dest, 0, dest.length())).toString());
        assertEquals(destString, dest.toString());

        Object what = new Object();
        Spannable spannableSource = new SpannableString(source);
        spannableSource.setSpan(what, 0, spannableSource.length(), Spanned.SPAN_POINT_POINT);
        Spanned filtered = (Spanned) digitsKeyListener.filter(spannableSource,
                0, spannableSource.length(), dest, 0, dest.length());
        assertEquals("123", filtered.toString());
        assertEquals(Spanned.SPAN_POINT_POINT, filtered.getSpanFlags(what));
        assertEquals(0, filtered.getSpanStart(what));
        assertEquals("123".length(), filtered.getSpanEnd(what));

        assertNull(digitsKeyListener.filter("", 0, 0, dest, 0, dest.length()));
        assertEquals(destString, dest.toString());
    }

    /*
     * Check point:
     * Current accepted characters are '0', '1', '2', '3', '4', '5', '6', '7', '8', '9', '-', '+'.
     * 1. filter "-123456", return null
     * 2. filter "+123456", return null
     * 3. filter "-a1.b2c3d", return "-123"
     * 4. filter "-a1-b2c3d", return "-123"
     * 5. filter "+a1-b2c3d", return "+123"
     * 6. filter "5-a1-b2c3d", return "5123"
     * 7. filter "5-a1+b2c3d", return "5123"
     * 8. filter "+5-a1+b2c3d", return "+5123"
     * 9. filter Spanned("5-a1-b2c3d"), return Spanned("5123") and copy spans.
     * 10. filter "", return null
     * 11. filter "-123456" but dest has '-' after dend, return ""
     * 12. filter "-123456" but dest has '+' after dend, return ""
     * 13. filter "-123456" but dest has '-' before dstart, return "123456"
     * 14. filter "+123456" but dest has '-' before dstart, return "123456"
     */
    @Test
    public void testFilter2() {
        String source = "-123456";
        String destString = "dest string without sign and decimal";

        DigitsKeyListener digitsKeyListener = DigitsKeyListener.getInstance(true, false);
        SpannableString dest = new SpannableString(destString);
        assertNull(digitsKeyListener.filter(source, 0, source.length(),
                dest, 0, dest.length()));
        assertEquals(destString, dest.toString());

        source = "+123456";
        assertNull(digitsKeyListener.filter(source, 0, source.length(),
                dest, 0, dest.length()));
        assertEquals(destString, dest.toString());

        source = "-a1.b2c3d";
        assertEquals("-123", (digitsKeyListener.filter(source, 0, source.length(),
                dest, 0, dest.length())).toString());
        assertEquals(destString, dest.toString());

        source = "-a1-b2c3d";
        assertEquals("-123", (digitsKeyListener.filter(source, 0, source.length(),
                dest, 0, dest.length())).toString());
        assertEquals(destString, dest.toString());

        source = "+a1-b2c3d";
        assertEquals("+123", (digitsKeyListener.filter(source, 0, source.length(),
                dest, 0, dest.length())).toString());
        assertEquals(destString, dest.toString());

        source = "5-a1-b2c3d";
        assertEquals("5123", (digitsKeyListener.filter(source, 0, source.length(),
                dest, 0, dest.length())).toString());
        assertEquals(destString, dest.toString());

        source = "5-a1+b2c3d";
        assertEquals("5123", (digitsKeyListener.filter(source, 0, source.length(),
                dest, 0, dest.length())).toString());
        assertEquals(destString, dest.toString());

        source = "+5-a1+b2c3d";
        assertEquals("+5123", (digitsKeyListener.filter(source, 0, source.length(),
                dest, 0, dest.length())).toString());
        assertEquals(destString, dest.toString());

        source = "5-a1+b2c3d";
        Object what = new Object();
        Spannable spannableSource = new SpannableString(source);
        spannableSource.setSpan(what, 0, spannableSource.length(), Spanned.SPAN_POINT_POINT);
        Spanned filtered = (Spanned) digitsKeyListener.filter(spannableSource,
                0, spannableSource.length(), dest, 0, dest.length());
        assertEquals("5123", filtered.toString());
        assertEquals(Spanned.SPAN_POINT_POINT, filtered.getSpanFlags(what));
        assertEquals(0, filtered.getSpanStart(what));
        assertEquals("5123".length(), filtered.getSpanEnd(what));

        assertNull(digitsKeyListener.filter("", 0, 0, dest, 0, dest.length()));
        assertEquals(destString, dest.toString());

        source = "-123456";
        String endSign = "789-";
        dest = new SpannableString(endSign);
        assertEquals("", (digitsKeyListener.filter(source, 0, source.length(),
                dest, 0, dest.length() - 1)).toString());
        assertEquals(endSign, dest.toString());

        endSign = "789+";
        dest = new SpannableString(endSign);
        assertEquals("", (digitsKeyListener.filter(source, 0, source.length(),
                dest, 0, dest.length() - 1)).toString());
        assertEquals(endSign, dest.toString());

        String startSign = "-789";
        dest = new SpannableString(startSign);
        assertEquals("123456", (digitsKeyListener.filter(source, 0, source.length(),
                dest, 1, dest.length())).toString());
        assertEquals(startSign, dest.toString());

        source = "+123456";
        dest = new SpannableString(startSign);
        assertEquals("123456", (digitsKeyListener.filter(source, 0, source.length(),
                dest, 1, dest.length())).toString());
        assertEquals(startSign, dest.toString());
    }

    /*
     * Check point:
     * Current accepted characters are '0', '1', '2', '3', '4', '5', '6', '7', '8', '9', '.'.
     * 1. filter "123.456", return null
     * 2. filter "-a1.b2c3d", return "1.23"
     * 3. filter "+a1.b2c3d", return "1.23"
     * 4. filter "a1.b2c3d.", return "123."
     * 5. filter "5.a1.b2c3d", return "51.23"
     * 6. filter Spanned("5.a1.b2c3d"), return Spanned("51.23") and copy spans.
     * 7. filter "", return null
     * 8. filter "123.456" but dest has '.' after dend, return "123456"
     * 9. filter "123.456" but dest has '.' before dstart, return "123456"
     */
    @Test
    public void testFilter3() {
        String source = "123.456";
        String destString = "dest string without sign and decimal";

        DigitsKeyListener digitsKeyListener = DigitsKeyListener.getInstance(false, true);
        SpannableString dest = new SpannableString(destString);
        assertNull(digitsKeyListener.filter(source, 0, source.length(),
                dest, 0, dest.length()));
        assertEquals(destString, dest.toString());

        source = "-a1.b2c3d";
        assertEquals("1.23", (digitsKeyListener.filter(source, 0, source.length(),
                dest, 0, dest.length())).toString());
        assertEquals(destString, dest.toString());

        source = "+a1.b2c3d";
        assertEquals("1.23", (digitsKeyListener.filter(source, 0, source.length(),
                dest, 0, dest.length())).toString());
        assertEquals(destString, dest.toString());

        source = "a1.b2c3d.";
        assertEquals("123.", (digitsKeyListener.filter(source, 0, source.length(),
                dest, 0, dest.length())).toString());
        assertEquals(destString, dest.toString());

        source = "5.a1.b2c3d";
        assertEquals("51.23", (digitsKeyListener.filter(source, 0, source.length(),
                dest, 0, dest.length())).toString());
        assertEquals(destString, dest.toString());

        Object what = new Object();
        Spannable spannableSource = new SpannableString(source);
        spannableSource.setSpan(what, 0, spannableSource.length(), Spanned.SPAN_POINT_POINT);
        Spanned filtered = (Spanned) digitsKeyListener.filter(spannableSource,
                0, spannableSource.length(), dest, 0, dest.length());
        assertEquals("51.23", filtered.toString());
        assertEquals(Spanned.SPAN_POINT_POINT, filtered.getSpanFlags(what));
        assertEquals(0, filtered.getSpanStart(what));
        assertEquals("51.23".length(), filtered.getSpanEnd(what));

        assertNull(digitsKeyListener.filter("", 0, 0, dest, 0, dest.length()));
        assertEquals(destString, dest.toString());

        source = "123.456";
        String endDecimal = "789.";
        dest = new SpannableString(endDecimal);
        assertEquals("123456", (digitsKeyListener.filter(source, 0, source.length(),
                dest, 0, dest.length() - 1)).toString());
        assertEquals(endDecimal, dest.toString());

        String startDecimal = ".789";
        dest = new SpannableString(startDecimal);
        assertEquals("123456", (digitsKeyListener.filter(source, 0, source.length(),
                dest, 1, dest.length())).toString());
        assertEquals(startDecimal, dest.toString());
    }

    /*
     * Check point:
     * Current accepted characters are '0', '1', '2', '3', '4', '5', '6', '7', '8', '9', '.', '-',
     * '+'.
     * 1. filter "-123.456", return null
     * 2. filter "+123.456", return null
     * 3. filter "-a1.b2c3d", return "-1.23"
     * 4. filter "+a1.b2c3d", return "+1.23"
     * 5. filter "a1.b-2c+3d.", return "123."
     * 6. filter "-5.a1.b2c+3d", return "-51.23"
     * 7. filter "+5.a1.b2c-3d", return "+51.23"
     * 8. filter Spanned("-5.a1.b2c3d"), return Spanned("-51.23") and copy spans.
     * 9. filter "", return null
     * 10. filter "-123.456" but dest has '.' after dend, return "-123456"
     * 11. filter "-123.456" but dest has '.' before dstart, return "123456"
     * 12. filter "+123.456" but dest has '.' after dend, return "+123456"
     * 13. filter "+123.456" but dest has '.' before dstart, return "123456"
     * 14. filter "-123.456" but dest has '-' after dend, return ""
     * 15. filter "-123.456" but dest has '+' after dend, return ""
     * 16. filter "-123.456" but dest has '-' before dstart, return "123.456"
     * 17. filter "+123.456" but dest has '-' before dstart, return "123.456"
     */
    @Test
    public void testFilter4() {
        String source = "-123.456";
        String destString = "dest string without sign and decimal";

        DigitsKeyListener digitsKeyListener = DigitsKeyListener.getInstance(true, true);
        SpannableString dest = new SpannableString(destString);
        assertNull(digitsKeyListener.filter(source, 0, source.length(),
                dest, 0, dest.length()));
        assertEquals(destString, dest.toString());

        source = "+123.456";
        assertNull(digitsKeyListener.filter(source, 0, source.length(),
                dest, 0, dest.length()));
        assertEquals(destString, dest.toString());

        source = "-a1.b2c3d";
        assertEquals("-1.23", (digitsKeyListener.filter(source, 0, source.length(),
                dest, 0, dest.length())).toString());
        assertEquals(destString, dest.toString());

        source = "a1.b-2c+3d.";
        assertEquals("123.", (digitsKeyListener.filter(source, 0, source.length(),
                dest, 0, dest.length())).toString());
        assertEquals(destString, dest.toString());

        source = "-5.a1.b2c+3d";
        assertEquals("-51.23", (digitsKeyListener.filter(source, 0, source.length(),
                dest, 0, dest.length())).toString());
        assertEquals(destString, dest.toString());

        source = "+5.a1.b2c-3d";
        assertEquals("+51.23", (digitsKeyListener.filter(source, 0, source.length(),
                dest, 0, dest.length())).toString());
        assertEquals(destString, dest.toString());

        source = "-5.a1.b2c+3d";
        Object what = new Object();
        Spannable spannableSource = new SpannableString(source);
        spannableSource.setSpan(what, 0, spannableSource.length(), Spanned.SPAN_POINT_POINT);
        Spanned filtered = (Spanned) digitsKeyListener.filter(spannableSource,
                0, spannableSource.length(), dest, 0, dest.length());
        assertEquals("-51.23", filtered.toString());
        assertEquals(Spanned.SPAN_POINT_POINT, filtered.getSpanFlags(what));
        assertEquals(0, filtered.getSpanStart(what));
        assertEquals("-51.23".length(), filtered.getSpanEnd(what));

        assertNull(digitsKeyListener.filter("", 0, 0, dest, 0, dest.length()));
        assertEquals(destString, dest.toString());

        source = "-123.456";
        String endDecimal = "789.";
        dest = new SpannableString(endDecimal);
        assertEquals("-123456", (digitsKeyListener.filter(source, 0, source.length(),
                dest, 0, dest.length() - 1)).toString());
        assertEquals(endDecimal, dest.toString());

        String startDecimal = ".789";
        dest = new SpannableString(startDecimal);
        assertEquals("123456", (digitsKeyListener.filter(source, 0, source.length(),
                dest, 1, dest.length())).toString());
        assertEquals(startDecimal, dest.toString());

        source = "+123.456";
        endDecimal = "789.";
        dest = new SpannableString(endDecimal);
        assertEquals("+123456", (digitsKeyListener.filter(source, 0, source.length(),
                dest, 0, dest.length() - 1)).toString());
        assertEquals(endDecimal, dest.toString());

        startDecimal = ".789";
        dest = new SpannableString(startDecimal);
        assertEquals("123456", (digitsKeyListener.filter(source, 0, source.length(),
                dest, 1, dest.length())).toString());
        assertEquals(startDecimal, dest.toString());

        source = "-123.456";
        String endSign = "789-";
        dest = new SpannableString(endSign);
        assertEquals("", (digitsKeyListener.filter(source, 0, source.length(),
                dest, 0, dest.length() - 1)).toString());
        assertEquals(endSign, dest.toString());

        endSign = "789+";
        dest = new SpannableString(endSign);
        assertEquals("", (digitsKeyListener.filter(source, 0, source.length(),
                dest, 0, dest.length() - 1)).toString());
        assertEquals(endSign, dest.toString());

        String startSign = "-789";
        dest = new SpannableString(startSign);
        assertEquals("123.456", (digitsKeyListener.filter(source, 0, source.length(),
                dest, 1, dest.length())).toString());
        assertEquals(startSign, dest.toString());

        source = "+123.456";
        dest = new SpannableString(startSign);
        assertEquals("123.456", (digitsKeyListener.filter(source, 0, source.length(),
                dest, 1, dest.length())).toString());
        assertEquals(startSign, dest.toString());
    }

    /*
     * Check point:
     * Current accepted characters are U+06F0..U+06F9 for digits, U+066B as decimal separator, '-',
     * '+'.
     *
     * Tests are otherwise identical to the tests in testFilter4().
     */
    @Test
    public void testFilter4_internationalized() {
        String source = "-\u06F1\u06F2\u06F3\u066B\u06F4\u06F5\u06F6";
        String destString = "dest string without sign and decimal";

        DigitsKeyListener digitsKeyListener =
                DigitsKeyListener.getInstance(Locale.forLanguageTag("fa-IR"), true, true);
        SpannableString dest = new SpannableString(destString);
        assertNull(digitsKeyListener.filter(source, 0, source.length(),
                dest, 0, dest.length()));
        assertEquals(destString, dest.toString());

        source = "+\u06F1\u06F2\u06F3\u066B\u06F4\u06F5\u06F6";
        assertNull(digitsKeyListener.filter(source, 0, source.length(),
                dest, 0, dest.length()));
        assertEquals(destString, dest.toString());

        source = "-a\u06F1\u066Bb\u06F2c\u06F3d";
        assertEquals("-\u06F1\u066B\u06F2\u06F3", (digitsKeyListener.filter(
                source, 0, source.length(),
                dest, 0, dest.length())).toString());
        assertEquals(destString, dest.toString());

        source = "a\u06F1\u066Bb-\u06F2c+\u06F3d\u066B";
        assertEquals("\u06F1\u06F2\u06F3\u066B", (digitsKeyListener.filter(
                source, 0, source.length(),
                dest, 0, dest.length())).toString());
        assertEquals(destString, dest.toString());

        source = "-\u06F5\u066Ba\u06F1\u066Bb\u06F2c+\u06F3d";
        assertEquals("-\u06F5\u06F1\u066B\u06F2\u06F3", (digitsKeyListener.filter(
                source, 0, source.length(),
                dest, 0, dest.length())).toString());
        assertEquals(destString, dest.toString());

        source = "+\u06F5\u066Ba\u06F1\u066Bb\u06F2c-\u06F3d";
        assertEquals("+\u06F5\u06F1\u066B\u06F2\u06F3", (digitsKeyListener.filter(
                source, 0, source.length(),
                dest, 0, dest.length())).toString());
        assertEquals(destString, dest.toString());

        source = "-\u06F5\u066Ba\u06F1\u066Bb\u06F2c+\u06F3d";
        Object what = new Object();
        Spannable spannableSource = new SpannableString(source);
        spannableSource.setSpan(what, 0, spannableSource.length(), Spanned.SPAN_POINT_POINT);
        Spanned filtered = (Spanned) digitsKeyListener.filter(spannableSource,
                0, spannableSource.length(), dest, 0, dest.length());
        assertEquals("-\u06F5\u06F1\u066B\u06F2\u06F3", filtered.toString());
        assertEquals(Spanned.SPAN_POINT_POINT, filtered.getSpanFlags(what));
        assertEquals(0, filtered.getSpanStart(what));
        assertEquals("-\u06F5\u06F1\u066B\u06F2\u06F3".length(), filtered.getSpanEnd(what));

        assertNull(digitsKeyListener.filter("", 0, 0, dest, 0, dest.length()));
        assertEquals(destString, dest.toString());

        source = "-\u06F1\u06F2\u06F3\u066B\u06F4\u06F5\u06F6";
        String endDecimal = "\u06F7\u06F8\u06F9\u066B";
        dest = new SpannableString(endDecimal);
        assertEquals("-\u06F1\u06F2\u06F3\u06F4\u06F5\u06F6", (digitsKeyListener.filter(
                source, 0, source.length(),
                dest, 0, dest.length() - 1)).toString());
        assertEquals(endDecimal, dest.toString());

        String startDecimal = "\u066B\u06F7\u06F8\u06F9";
        dest = new SpannableString(startDecimal);
        assertEquals("\u06F1\u06F2\u06F3\u06F4\u06F5\u06F6", (digitsKeyListener.filter(
                source, 0, source.length(),
                dest, 1, dest.length())).toString());
        assertEquals(startDecimal, dest.toString());

        source = "+\u06F1\u06F2\u06F3\u066B\u06F4\u06F5\u06F6";
        endDecimal = "\u06F7\u06F8\u06F9\u066B";
        dest = new SpannableString(endDecimal);
        assertEquals("+\u06F1\u06F2\u06F3\u06F4\u06F5\u06F6", (digitsKeyListener.filter(
                source, 0, source.length(),
                dest, 0, dest.length() - 1)).toString());
        assertEquals(endDecimal, dest.toString());

        startDecimal = "\u066B\u06F7\u06F8\u06F9";
        dest = new SpannableString(startDecimal);
        assertEquals("\u06F1\u06F2\u06F3\u06F4\u06F5\u06F6", (digitsKeyListener.filter(
                source, 0, source.length(),
                dest, 1, dest.length())).toString());
        assertEquals(startDecimal, dest.toString());

        source = "-\u06F1\u06F2\u06F3\u066B\u06F4\u06F5\u06F6";
        String endSign = "\u06F7\u06F8\u06F9-";
        dest = new SpannableString(endSign);
        assertEquals("", (digitsKeyListener.filter(source, 0, source.length(),
                dest, 0, dest.length() - 1)).toString());
        assertEquals(endSign, dest.toString());

        endSign = "\u06F7\u06F8\u06F9+";
        dest = new SpannableString(endSign);
        assertEquals("", (digitsKeyListener.filter(source, 0, source.length(),
                dest, 0, dest.length() - 1)).toString());
        assertEquals(endSign, dest.toString());

        String startSign = "-\u06F7\u06F8\u06F9";
        dest = new SpannableString(startSign);
        assertEquals("\u06F1\u06F2\u06F3\u066B\u06F4\u06F5\u06F6", (digitsKeyListener.filter(
                source, 0, source.length(),
                dest, 1, dest.length())).toString());
        assertEquals(startSign, dest.toString());

        source = "+\u06F1\u06F2\u06F3\u066B\u06F4\u06F5\u06F6";
        dest = new SpannableString(startSign);
        assertEquals("\u06F1\u06F2\u06F3\u066B\u06F4\u06F5\u06F6", (digitsKeyListener.filter(
                source, 0, source.length(),
                dest, 1, dest.length())).toString());
        assertEquals(startSign, dest.toString());
    }

    /*
     * Scenario description:
     * Current accepted characters are '0', '1', '2', '3', '4', '5', '6', '7', '8', '9'.
     *  1. Press '-' key and this key could not be accepted.
     *  2. Press '1' key and check if the content of TextView becomes "1"
     *  3. Press '.' key and this key could not be accepted.
     *  4. Press '2' key and check if the content of TextView becomes "12"
     */
    @Test
    public void testDigitsKeyListener1() {
        final DigitsKeyListener digitsKeyListener = DigitsKeyListener.getInstance();

        setKeyListenerSync(digitsKeyListener);
        assertEquals("", mTextView.getText().toString());

        // press '-' key.
        CtsKeyEventUtil.sendKeys(mInstrumentation, mTextView, KeyEvent.KEYCODE_MINUS);
        assertEquals("", mTextView.getText().toString());

        // press '1' key.
        CtsKeyEventUtil.sendKeys(mInstrumentation, mTextView, KeyEvent.KEYCODE_1);
        assertEquals("1", mTextView.getText().toString());

        // press '.' key.
        CtsKeyEventUtil.sendKeys(mInstrumentation, mTextView, KeyEvent.KEYCODE_PERIOD);
        assertEquals("1", mTextView.getText().toString());

        // press '2' key.
        CtsKeyEventUtil.sendKeys(mInstrumentation, mTextView, KeyEvent.KEYCODE_2);
        assertEquals("12", mTextView.getText().toString());
    }

    /*
     * Scenario description:
     * Current accepted characters are '0', '1', '2', '3', '4', '5', '6', '7', '8', '9', '-', '+'.
     *  1. Press '-' key and check if the content of TextView becomes "-"
     *  2. Press '1' key and check if the content of TextView becomes "-1"
     *  3. Press '.' key and this key could not be accepted.
     *  4. Press '+' key and this key could not be accepted.
     *  5. Press '2' key and check if the content of TextView becomes "-12"
     *  6. Press '-' key and this key could not be accepted,
     *     because text view accepts minus sign iff it at the beginning.
     */
    @Test
    public void testDigitsKeyListener2() {
        final DigitsKeyListener digitsKeyListener = DigitsKeyListener.getInstance(true, false);

        setKeyListenerSync(digitsKeyListener);
        assertEquals("", mTextView.getText().toString());

        // press '-' key.
        CtsKeyEventUtil.sendKeys(mInstrumentation, mTextView, KeyEvent.KEYCODE_MINUS);
        assertEquals("-", mTextView.getText().toString());

        // press '1' key.
        CtsKeyEventUtil.sendKeys(mInstrumentation, mTextView, KeyEvent.KEYCODE_1);
        assertEquals("-1", mTextView.getText().toString());

        // press '.' key.
        CtsKeyEventUtil.sendKeys(mInstrumentation, mTextView, KeyEvent.KEYCODE_PERIOD);
        assertEquals("-1", mTextView.getText().toString());

        // press '+' key.
        CtsKeyEventUtil.sendKeys(mInstrumentation, mTextView, KeyEvent.KEYCODE_PLUS);
        assertEquals("-1", mTextView.getText().toString());

        // press '2' key.
        CtsKeyEventUtil.sendKeys(mInstrumentation, mTextView, KeyEvent.KEYCODE_2);
        assertEquals("-12", mTextView.getText().toString());

        // press '-' key.
        CtsKeyEventUtil.sendKeys(mInstrumentation, mTextView, KeyEvent.KEYCODE_MINUS);
        assertEquals("-12", mTextView.getText().toString());
    }

    /*
     * Scenario description:
     * Current accepted characters are '0', '1', '2', '3', '4', '5', '6', '7', '8', '9', '.'.
     *  1. Press '-' key and check if the content of TextView becomes ""
     *  2. Press '+' key and check if the content of TextView becomes ""
     *  3. Press '1' key and check if the content of TextView becomes "1"
     *  4. Press '.' key and check if the content of TextView becomes "1."
     *  5. Press '2' key and check if the content of TextView becomes "1.2"
     *  6. Press '.' key and this key could not be accepted,
     *     because text view accepts only one decimal point per field.
     */
    @Test
    public void testDigitsKeyListener3() {
        final DigitsKeyListener digitsKeyListener = DigitsKeyListener.getInstance(false, true);

        setKeyListenerSync(digitsKeyListener);
        assertEquals("", mTextView.getText().toString());

        // press '-' key.
        CtsKeyEventUtil.sendKeys(mInstrumentation, mTextView, KeyEvent.KEYCODE_MINUS);
        assertEquals("", mTextView.getText().toString());

        // press '+' key.
        CtsKeyEventUtil.sendKeys(mInstrumentation, mTextView, KeyEvent.KEYCODE_PLUS);
        assertEquals("", mTextView.getText().toString());

        // press '1' key.
        CtsKeyEventUtil.sendKeys(mInstrumentation, mTextView, KeyEvent.KEYCODE_1);
        assertEquals("1", mTextView.getText().toString());

        // press '.' key.
        CtsKeyEventUtil.sendKeys(mInstrumentation, mTextView, KeyEvent.KEYCODE_PERIOD);
        assertEquals("1.", mTextView.getText().toString());

        // press '2' key.
        CtsKeyEventUtil.sendKeys(mInstrumentation, mTextView, KeyEvent.KEYCODE_2);
        assertEquals("1.2", mTextView.getText().toString());

        // press '.' key.
        CtsKeyEventUtil.sendKeys(mInstrumentation, mTextView, KeyEvent.KEYCODE_PERIOD);
        assertEquals("1.2", mTextView.getText().toString());
    }

    /*
     * Scenario description:
     * Current accepted characters are '0', '1', '2', '3', '4', '5', '6', '7', '8', '9', '-', '+',
     * '.'.
     *  1. Press '+' key and check if the content of TextView becomes "+"
     *  2. Press '1' key and check if the content of TextView becomes "+1"
     *  3. Press '.' key and this key could not be accepted.
     *  4. Press '2' key and check if the content of TextView becomes "+12"
     *  5. Press '-' key and this key could not be accepted,
     *     because text view accepts minus sign iff it at the beginning.
     *  6. Press '.' key and this key could not be accepted,
     *     because text view accepts only one decimal point per field.
     */
    @Test
    public void testDigitsKeyListener4() {
        final DigitsKeyListener digitsKeyListener = DigitsKeyListener.getInstance(true, true);

        setKeyListenerSync(digitsKeyListener);
        assertEquals("", mTextView.getText().toString());

        // press '+' key.
        CtsKeyEventUtil.sendKeys(mInstrumentation, mTextView, KeyEvent.KEYCODE_PLUS);
        assertEquals("+", mTextView.getText().toString());

        // press '1' key.
        CtsKeyEventUtil.sendKeys(mInstrumentation, mTextView, KeyEvent.KEYCODE_1);
        assertEquals("+1", mTextView.getText().toString());

        // press '.' key.
        CtsKeyEventUtil.sendKeys(mInstrumentation, mTextView, KeyEvent.KEYCODE_PERIOD);
        assertEquals("+1.", mTextView.getText().toString());

        // press '2' key.
        CtsKeyEventUtil.sendKeys(mInstrumentation, mTextView, KeyEvent.KEYCODE_2);
        assertEquals("+1.2", mTextView.getText().toString());

        // press '-' key.
        CtsKeyEventUtil.sendKeys(mInstrumentation, mTextView, KeyEvent.KEYCODE_MINUS);
        assertEquals("+1.2", mTextView.getText().toString());

        // press '.' key.
        CtsKeyEventUtil.sendKeys(mInstrumentation, mTextView, KeyEvent.KEYCODE_PERIOD);
        assertEquals("+1.2", mTextView.getText().toString());
    }

    /*
     * Scenario description:
     * Current accepted characters are '5', '6', '7', '8', '9'.
     *  1. Press '1' key and this key could not be accepted.
     *  2. Press '5' key and check if the content of TextView becomes "5"
     *  3. Press '.' key and this key could not be accepted.
     *  4. Press '-' key and this key could not be accepted.
     *  5. remove DigitsKeyListener and Press '5' key, this key will not be accepted
     */
    @Test
    public void testDigitsKeyListener5() throws Throwable {
        final String accepted = "56789";
        final DigitsKeyListener digitsKeyListener = DigitsKeyListener.getInstance(accepted);

        setKeyListenerSync(digitsKeyListener);
        assertEquals("", mTextView.getText().toString());

        // press '1' key.
        CtsKeyEventUtil.sendKeys(mInstrumentation, mTextView, KeyEvent.KEYCODE_1);
        assertEquals("", mTextView.getText().toString());

        // press '5' key.
        CtsKeyEventUtil.sendKeys(mInstrumentation, mTextView, KeyEvent.KEYCODE_5);
        assertEquals("5", mTextView.getText().toString());

        // press '.' key.
        CtsKeyEventUtil.sendKeys(mInstrumentation, mTextView, KeyEvent.KEYCODE_PERIOD);
        assertEquals("5", mTextView.getText().toString());

        // press '-' key.
        CtsKeyEventUtil.sendKeys(mInstrumentation, mTextView, KeyEvent.KEYCODE_MINUS);
        assertEquals("5", mTextView.getText().toString());

        // remove DigitsKeyListener
        mActivityRule.runOnUiThread(() -> {
            mTextView.setKeyListener(null);
            mTextView.requestFocus();
        });
        mInstrumentation.waitForIdleSync();
        assertEquals("5", mTextView.getText().toString());

        // press '5' key.
        CtsKeyEventUtil.sendKeys(mInstrumentation, mTextView, KeyEvent.KEYCODE_5);
        assertEquals("5", mTextView.getText().toString());
    }

    @Test
    public void testGetInstance1() {
        DigitsKeyListener listener1 = DigitsKeyListener.getInstance();
        DigitsKeyListener listener2 = DigitsKeyListener.getInstance();
        DigitsKeyListener listener3 = DigitsKeyListener.getInstance((Locale) null);

        assertNotNull(listener1);
        assertNotNull(listener2);
        assertNotNull(listener3);
        assertSame(listener1, listener2);
        assertSame(listener1, listener3);
    }

    @Test
    public void testGetInstance2() {
        DigitsKeyListener listener1 = DigitsKeyListener.getInstance(true, true);
        DigitsKeyListener listener2 = DigitsKeyListener.getInstance(true, true);

        assertNotNull(listener1);
        assertNotNull(listener2);
        assertSame(listener1, listener2);

        DigitsKeyListener listener3 = DigitsKeyListener.getInstance(true, false);
        DigitsKeyListener listener4 = DigitsKeyListener.getInstance(true, false);

        assertNotNull(listener3);
        assertNotNull(listener4);
        assertSame(listener3, listener4);

        assertNotSame(listener1, listener3);
    }

    @Test
    public void testGetInstance3() {
        DigitsKeyListener digitsKeyListener = DigitsKeyListener.getInstance("abcdefg");
        assertNotNull(digitsKeyListener);

        digitsKeyListener = DigitsKeyListener.getInstance("Android Test");
        assertNotNull(digitsKeyListener);
    }

    @Test
    public void testGetInstance4() {
        DigitsKeyListener listener1 = DigitsKeyListener.getInstance(Locale.US);
        DigitsKeyListener listener2 = DigitsKeyListener.getInstance(Locale.US, false, false);
        assertNotNull(listener1);
        assertNotNull(listener2);
        assertSame(listener1, listener2);
    }

    @Test
    public void testGetInstance5() {
        DigitsKeyListener listener1 = DigitsKeyListener.getInstance(Locale.US, false, false);
        DigitsKeyListener listener2 = DigitsKeyListener.getInstance(Locale.US, true, false);
        DigitsKeyListener listener3 = DigitsKeyListener.getInstance(Locale.US, false, true);
        DigitsKeyListener listener4 = DigitsKeyListener.getInstance(Locale.US, true, true);
        assertNotNull(listener1);
        assertNotNull(listener2);
        assertNotNull(listener3);
        assertNotNull(listener4);
        assertNotSame(listener1, listener2);
        assertNotSame(listener1, listener3);
        assertNotSame(listener1, listener4);
        assertNotSame(listener2, listener3);
        assertNotSame(listener2, listener4);
        assertNotSame(listener3, listener4);
    }

    @Test
    public void testGetAcceptedChars1() {
        MockDigitsKeyListener mockDigitsKeyListener = new MockDigitsKeyListener();

        final char[][] expected = new char[][] {
            new char[] { '0', '1', '2', '3', '4', '5', '6', '7', '8', '9' },
            new char[] { '0', '1', '2', '3', '4', '5', '6', '7', '8', '9', '-', '+' },
            new char[] { '0', '1', '2', '3', '4', '5', '6', '7', '8', '9', '.' },
            new char[] { '0', '1', '2', '3', '4', '5', '6', '7', '8', '9', '-', '+', '.' },
        };

        assertArrayEquals(expected[0], mockDigitsKeyListener.getAcceptedChars());

        mockDigitsKeyListener = new MockDigitsKeyListener(true, false);
        assertArrayEquals(expected[1], mockDigitsKeyListener.getAcceptedChars());

        mockDigitsKeyListener = new MockDigitsKeyListener(false, true);
        assertArrayEquals(expected[2], mockDigitsKeyListener.getAcceptedChars());

        mockDigitsKeyListener = new MockDigitsKeyListener(true, true);
        assertArrayEquals(expected[3], mockDigitsKeyListener.getAcceptedChars());
    }

    @Test
    public void testGetAcceptedChars2() {
        final Locale irLocale = Locale.forLanguageTag("fa-IR");
        final char irDecimalSeparator = '\u066B';
        final char usDecimalSeparator = '.';
        final char[] irDigits = {
            '\u06F0', '\u06F1', '\u06F2', '\u06F3', '\u06F4',
            '\u06F5', '\u06F6', '\u06F7', '\u06F8', '\u06F9'
        };
        final char[] irSigns = {
            '+', '-', '\u2212',
        };
        final char[] asciiDigits = {
            '0', '1', '2', '3', '4',
            '5', '6', '7', '8', '9'
        };

        MockDigitsKeyListener mockDigitsKeyListener = new MockDigitsKeyListener(irLocale);
        String acceptedChars = new String(mockDigitsKeyListener.getAcceptedChars());
        for (int i = 0; i < irDigits.length; i++) {
            assertNotEquals(-1, acceptedChars.indexOf(irDigits[i]));
        }
        for (int i = 0; i < irSigns.length; i++) {
            assertEquals(-1, acceptedChars.indexOf(irSigns[i]));
        }
        assertEquals(-1, acceptedChars.indexOf(irDecimalSeparator));
        for (int i = 0; i < asciiDigits.length; i++) {
            assertEquals(-1, acceptedChars.indexOf(asciiDigits[i]));
        }
        assertEquals(-1, acceptedChars.indexOf(usDecimalSeparator));

        mockDigitsKeyListener = new MockDigitsKeyListener(
                irLocale, false /* sign */, true /* decimal */);
        acceptedChars = new String(mockDigitsKeyListener.getAcceptedChars());
        for (int i = 0; i < irDigits.length; i++) {
            assertNotEquals(-1, acceptedChars.indexOf(irDigits[i]));
        }
        for (int i = 0; i < irSigns.length; i++) {
            assertEquals(-1, acceptedChars.indexOf(irSigns[i]));
        }
        assertNotEquals(-1, acceptedChars.indexOf(irDecimalSeparator));
        for (int i = 0; i < asciiDigits.length; i++) {
            assertEquals(-1, acceptedChars.indexOf(asciiDigits[i]));
        }
        assertEquals(-1, acceptedChars.indexOf(usDecimalSeparator));

        mockDigitsKeyListener = new MockDigitsKeyListener(
                irLocale, true /* sign */, true /* decimal */);
        acceptedChars = new String(mockDigitsKeyListener.getAcceptedChars());
        for (int i = 0; i < irDigits.length; i++) {
            assertNotEquals(acceptedChars, -1, acceptedChars.indexOf(irDigits[i]));
        }
        for (int i = 0; i < irSigns.length; i++) {
            assertNotEquals(-1, acceptedChars.indexOf(irSigns[i]));
        }
        assertNotEquals(-1, acceptedChars.indexOf(irDecimalSeparator));
        for (int i = 0; i < asciiDigits.length; i++) {
            assertEquals(-1, acceptedChars.indexOf(asciiDigits[i]));
        }
        assertEquals(-1, acceptedChars.indexOf(usDecimalSeparator));
    }

    // Deprecated constructors that need to preserve pre-existing behavior.
    @Test
    public void testGetInputType_deprecatedConstructors() {
        DigitsKeyListener digitsKeyListener = DigitsKeyListener.getInstance(false, false);
        int expected = InputType.TYPE_CLASS_NUMBER;
        assertEquals(expected, digitsKeyListener.getInputType());

        digitsKeyListener = DigitsKeyListener.getInstance(true, false);
        expected = InputType.TYPE_CLASS_NUMBER
                | InputType.TYPE_NUMBER_FLAG_SIGNED;
        assertEquals(expected, digitsKeyListener.getInputType());

        digitsKeyListener = DigitsKeyListener.getInstance(false, true);
        expected = InputType.TYPE_CLASS_NUMBER
                | InputType.TYPE_NUMBER_FLAG_DECIMAL;
        assertEquals(expected, digitsKeyListener.getInputType());

        digitsKeyListener = DigitsKeyListener.getInstance(true, true);
        expected = InputType.TYPE_CLASS_NUMBER
                | InputType.TYPE_NUMBER_FLAG_SIGNED
                | InputType.TYPE_NUMBER_FLAG_DECIMAL;
        assertEquals(expected, digitsKeyListener.getInputType());
    }

    // Deprecated constructors that need to preserve pre-existing behavior.
    @Test
    public void testGetInputType_English() {
        int expected = InputType.TYPE_CLASS_NUMBER;
        DigitsKeyListener digitsKeyListener = DigitsKeyListener.getInstance(
                Locale.US, false, false);
        assertEquals(expected, digitsKeyListener.getInputType());
        digitsKeyListener = DigitsKeyListener.getInstance(
                Locale.UK, false, false);
        assertEquals(expected, digitsKeyListener.getInputType());

        expected = InputType.TYPE_CLASS_NUMBER
                | InputType.TYPE_NUMBER_FLAG_SIGNED;
        digitsKeyListener = DigitsKeyListener.getInstance(Locale.US, true, false);
        assertEquals(expected, digitsKeyListener.getInputType());
        digitsKeyListener = DigitsKeyListener.getInstance(Locale.UK, true, false);
        assertEquals(expected, digitsKeyListener.getInputType());

        expected = InputType.TYPE_CLASS_NUMBER
                | InputType.TYPE_NUMBER_FLAG_DECIMAL;
        digitsKeyListener = DigitsKeyListener.getInstance(Locale.US, false, true);
        assertEquals(expected, digitsKeyListener.getInputType());
        digitsKeyListener = DigitsKeyListener.getInstance(Locale.UK, false, true);
        assertEquals(expected, digitsKeyListener.getInputType());

        expected = InputType.TYPE_CLASS_NUMBER
                | InputType.TYPE_NUMBER_FLAG_SIGNED
                | InputType.TYPE_NUMBER_FLAG_DECIMAL;
        digitsKeyListener = DigitsKeyListener.getInstance(Locale.US, true, true);
        assertEquals(expected, digitsKeyListener.getInputType());
        digitsKeyListener = DigitsKeyListener.getInstance(Locale.UK, true, true);
        assertEquals(expected, digitsKeyListener.getInputType());
    }

    // Persian needs more characters then typically provided by datetime inputs, so it falls
    // back on normal text.
    @Test
    public void testGetInputType_Persian() {
        final Locale irLocale = Locale.forLanguageTag("fa-IR");
        final int expected = InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_NORMAL;

        DigitsKeyListener digitsKeyListener = DigitsKeyListener.getInstance(irLocale, false, false);
        assertEquals(expected, digitsKeyListener.getInputType());

        digitsKeyListener = DigitsKeyListener.getInstance(irLocale, true, false);
        assertEquals(expected, digitsKeyListener.getInputType());

        digitsKeyListener = DigitsKeyListener.getInstance(irLocale, false, true);
        assertEquals(expected, digitsKeyListener.getInputType());

        digitsKeyListener = DigitsKeyListener.getInstance(irLocale, true, true);
        assertEquals(expected, digitsKeyListener.getInputType());
    }

    /**
     * A mocked {@link android.text.method.DigitsKeyListener} for testing purposes.
     *
     * Allows {@link DigitsKeyListenerTest} to call
     * {@link android.text.method.DigitsKeyListener#getAcceptedChars()}.
     */
    private class MockDigitsKeyListener extends DigitsKeyListener {
        MockDigitsKeyListener() {
            super();
        }

        MockDigitsKeyListener(boolean sign, boolean decimal) {
            super(sign, decimal);
        }

        MockDigitsKeyListener(Locale locale) {
            super(locale);
        }

        MockDigitsKeyListener(Locale locale, boolean sign, boolean decimal) {
            super(locale, sign, decimal);
        }

        @Override
        protected char[] getAcceptedChars() {
            return super.getAcceptedChars();
        }
    }
}
