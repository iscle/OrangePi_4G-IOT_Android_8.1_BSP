/*
 * Copyright (C) 2009 The Android Open Source Project
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

import static android.text.Spanned.SPAN_EXCLUSIVE_INCLUSIVE;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assert.assertEquals;

import android.graphics.Typeface;
import android.support.test.filters.SmallTest;
import android.support.test.runner.AndroidJUnit4;
import android.text.Html;
import android.text.Layout;
import android.text.Spannable;
import android.text.SpannableString;
import android.text.Spanned;
import android.text.style.AlignmentSpan;
import android.text.style.BackgroundColorSpan;
import android.text.style.BulletSpan;
import android.text.style.ForegroundColorSpan;
import android.text.style.QuoteSpan;
import android.text.style.StrikethroughSpan;
import android.text.style.StyleSpan;
import android.text.style.SubscriptSpan;
import android.text.style.SuperscriptSpan;
import android.text.style.TypefaceSpan;
import android.text.style.URLSpan;
import android.text.style.UnderlineSpan;

import org.hamcrest.BaseMatcher;
import org.hamcrest.Description;
import org.junit.Test;
import org.junit.runner.RunWith;

@SmallTest
@RunWith(AndroidJUnit4.class)
public class HtmlTest {
    @Test
    public void testSingleTagOnWhileString() {
        final String source = "<b>hello</b>";

        Spanned spanned = Html.fromHtml(source);
        verifySingleTagOnWhileString(spanned);
        spanned = Html.fromHtml(source, null, null);
        verifySingleTagOnWhileString(spanned);
    }

    private void verifySingleTagOnWhileString(Spanned spanned) {
        final int expectStart = 0;
        final int expectEnd = 5;
        final int expectLen = 1;
        final int start = -1;
        final int end = 100;

        Object[] spans = spanned.getSpans(start, end, Object.class);
        assertEquals(expectLen, spans.length);
        assertEquals(expectStart, spanned.getSpanStart(spans[0]));
        assertEquals(expectEnd, spanned.getSpanEnd(spans[0]));
    }

    @Test
    public void testBadHtml() {
        final String source = "Hello <b>b<i>bi</b>i</i>";

        Spanned spanned = Html.fromHtml(source);
        verifyBadHtml(spanned);
        spanned = Html.fromHtml(source, null, null);
        verifyBadHtml(spanned);
    }

    private void verifyBadHtml(Spanned spanned) {
        final int start = 0;
        final int end = 100;
        final int spansLen = 3;

        Object[] spans = spanned.getSpans(start, end, Object.class);
        assertEquals(spansLen, spans.length);
    }

    @Test
    public void testSymbols() {
        final String source = "&copy; &gt; &lt";
        final String expected = "\u00a9 > <";

        String spanned = Html.fromHtml(source).toString();
        assertEquals(expected, spanned);
        spanned = Html.fromHtml(source, null, null).toString();
        assertEquals(expected, spanned);
    }

    @Test
    public void testColor() {
        final Class<ForegroundColorSpan> type = ForegroundColorSpan.class;

        Spanned s = Html.fromHtml("<font color=\"#00FF00\">something</font>");
        ForegroundColorSpan[] colors = s.getSpans(0, s.length(), type);
        assertEquals(0xFF00FF00, colors[0].getForegroundColor());

        s = Html.fromHtml("<font color=\"navy\">NAVY</font>");
        colors = s.getSpans(0, s.length(), type);
        assertEquals(0xFF000080, colors[0].getForegroundColor());

        s = Html.fromHtml("<font color=\"gibberish\">something</font>");
        colors = s.getSpans(0, s.length(), type);
        assertEquals(0, colors.length);

        // By default use the color values from android.graphics.Color instead of HTML/CSS
        s = Html.fromHtml("<font color=\"green\">GREEN</font>");
        colors = s.getSpans(0, s.length(), type);
        assertEquals(0xFF00FF00, colors[0].getForegroundColor());

        s = Html.fromHtml("<font color=\"gray\">GRAY</font>");
        colors = s.getSpans(0, s.length(), type);
        assertEquals(0xFF888888, colors[0].getForegroundColor());

        s = Html.fromHtml("<font color=\"grey\">GREY</font>");
        colors = s.getSpans(0, s.length(), type);
        assertEquals(0xFF888888, colors[0].getForegroundColor());

        s = Html.fromHtml("<font color=\"lightgray\">LIGHTGRAY</font>");
        colors = s.getSpans(0, s.length(), type);
        assertEquals(0xFFCCCCCC, colors[0].getForegroundColor());

        s = Html.fromHtml("<font color=\"lightgrey\">LIGHTGREY</font>");
        colors = s.getSpans(0, s.length(), type);
        assertEquals(0xFFCCCCCC, colors[0].getForegroundColor());

        s = Html.fromHtml("<font color=\"darkgray\">DARKGRAY</font>");
        colors = s.getSpans(0, s.length(), type);
        assertEquals(0xFF444444, colors[0].getForegroundColor());

        s = Html.fromHtml("<font color=\"darkgrey\">DARKGREY</font>");
        colors = s.getSpans(0, s.length(), type);
        assertEquals(0xFF444444, colors[0].getForegroundColor());
    }

    @Test
    public void testUseCssColor() {
        final Class<ForegroundColorSpan> type = ForegroundColorSpan.class;
        final int flags = Html.FROM_HTML_OPTION_USE_CSS_COLORS;

        Spanned s = Html.fromHtml("<font color=\"green\">GREEN</font>", flags);
        ForegroundColorSpan[] colors = s.getSpans(0, s.length(), type);
        assertEquals(0xFF008000, colors[0].getForegroundColor());

        s = Html.fromHtml("<font color=\"gray\">GRAY</font>", flags);
        colors = s.getSpans(0, s.length(), type);
        assertEquals(0xFF808080, colors[0].getForegroundColor());

        s = Html.fromHtml("<font color=\"grey\">GREY</font>", flags);
        colors = s.getSpans(0, s.length(), type);
        assertEquals(0xFF808080, colors[0].getForegroundColor());

        s = Html.fromHtml("<font color=\"lightgray\">LIGHTGRAY</font>", flags);
        colors = s.getSpans(0, s.length(), type);
        assertEquals(0xFFD3D3D3, colors[0].getForegroundColor());

        s = Html.fromHtml("<font color=\"lightgrey\">LIGHTGREY</font>", flags);
        colors = s.getSpans(0, s.length(), type);
        assertEquals(0xFFD3D3D3, colors[0].getForegroundColor());

        s = Html.fromHtml("<font color=\"darkgray\">DARKGRAY</font>", flags);
        colors = s.getSpans(0, s.length(), type);
        assertEquals(0xFFA9A9A9, colors[0].getForegroundColor());

        s = Html.fromHtml("<font color=\"darkgrey\">DARKGREY</font>", flags);
        colors = s.getSpans(0, s.length(), type);
        assertEquals(0xFFA9A9A9, colors[0].getForegroundColor());
    }

    @Test
    public void testStylesFromHtml() {
        Spanned s = Html.fromHtml("<span style=\"color:#FF0000; background-color:#00FF00; "
                + "text-decoration:line-through;\">style</span>");

        ForegroundColorSpan[] foreground = s.getSpans(0, s.length(), ForegroundColorSpan.class);
        assertEquals(1, foreground.length);
        assertEquals(0xFFFF0000, foreground[0].getForegroundColor());

        BackgroundColorSpan[] background = s.getSpans(0, s.length(), BackgroundColorSpan.class);
        assertEquals(1, background.length);
        assertEquals(0xFF00FF00, background[0].getBackgroundColor());

        StrikethroughSpan[] strike = s.getSpans(0, s.length(), StrikethroughSpan.class);
        assertEquals(1, strike.length);
    }

    @Test
    public void testParagraphs() {
        SpannableString s = new SpannableString("Hello world");
        assertThat(Html.toHtml(s), matchesIgnoringTrailingWhitespace(
                "<p dir=\"ltr\">Hello world</p>"));

        s = new SpannableString("Hello world\nor something");
        assertThat(Html.toHtml(s), matchesIgnoringTrailingWhitespace(
                "<p dir=\"ltr\">Hello world<br>\nor something</p>"));
        assertThat(Html.toHtml(s, Html.TO_HTML_PARAGRAPH_LINES_INDIVIDUAL),
                matchesIgnoringTrailingWhitespace(
                "<p dir=\"ltr\" style=\"margin-top:0; margin-bottom:0;\">Hello world</p>\n"
                + "<p dir=\"ltr\" style=\"margin-top:0; margin-bottom:0;\">or something</p>"));

        s = new SpannableString("Hello world\n\nor something");
        assertThat(Html.toHtml(s), matchesIgnoringTrailingWhitespace(
                "<p dir=\"ltr\">Hello world</p>\n<p dir=\"ltr\">or something</p>"));

        s = new SpannableString("Hello world\n\n\nor something");
        assertThat(Html.toHtml(s), matchesIgnoringTrailingWhitespace(
                "<p dir=\"ltr\">Hello world<br></p>\n<p dir=\"ltr\">or something</p>"));
        assertThat(Html.toHtml(s, Html.TO_HTML_PARAGRAPH_LINES_INDIVIDUAL),
                matchesIgnoringTrailingWhitespace(
                "<p dir=\"ltr\" style=\"margin-top:0; margin-bottom:0;\">Hello world</p>\n"
                + "<br>\n"
                + "<br>\n"
                + "<p dir=\"ltr\" style=\"margin-top:0; margin-bottom:0;\">or something</p>"));
    }

    @Test
    public void testBidi() {
        SpannableString s = new SpannableString("LTR Text");
        assertThat(Html.toHtml(s), matchesIgnoringTrailingWhitespace(
                "<p dir=\"ltr\">LTR Text</p>"));

        s = new SpannableString("\u0622"); // U+06222 ARABIC LETTER ALEF WITH MADDA ABOVE
        assertThat(Html.toHtml(s), matchesIgnoringTrailingWhitespace(
                "<p dir=\"rtl\">&#1570;</p>"));

        // Paragraphs with no strong characters should become LTR.
        s = new SpannableString("===");
        assertThat(Html.toHtml(s), matchesIgnoringTrailingWhitespace(
                "<p dir=\"ltr\">===</p>"));
    }

    @Test
    public void testParagraphStyles() {
        SpannableString s = new SpannableString("Hello world");
        s.setSpan(new AlignmentSpan.Standard(Layout.Alignment.ALIGN_CENTER),
                0, s.length(), Spanned.SPAN_PARAGRAPH);
        assertThat(Html.toHtml(s, Html.TO_HTML_PARAGRAPH_LINES_INDIVIDUAL),
                matchesIgnoringTrailingWhitespace(
                "<p dir=\"ltr\" style=\"margin-top:0; margin-bottom:0; text-align:center;\">"
                + "Hello world</p>"));

        // Set another AlignmentSpan of a different alignment. Only the last one should be encoded.
        s.setSpan(new AlignmentSpan.Standard(Layout.Alignment.ALIGN_OPPOSITE),
                0, s.length(), Spanned.SPAN_PARAGRAPH);
        assertThat(Html.toHtml(s, Html.TO_HTML_PARAGRAPH_LINES_INDIVIDUAL),
                matchesIgnoringTrailingWhitespace(
                "<p dir=\"ltr\" style=\"margin-top:0; margin-bottom:0; text-align:end;\">"
                + "Hello world</p>"));

        // Set another AlignmentSpan without SPAN_PARAGRAPH flag.
        // This will be ignored and the previous alignment should be encoded.
        s.setSpan(new AlignmentSpan.Standard(Layout.Alignment.ALIGN_NORMAL),
                0, s.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        assertThat(Html.toHtml(s, Html.TO_HTML_PARAGRAPH_LINES_INDIVIDUAL),
                matchesIgnoringTrailingWhitespace(
                "<p dir=\"ltr\" style=\"margin-top:0; margin-bottom:0; text-align:end;\">"
                + "Hello world</p>"));
    }

    @Test
    public void testBulletSpan() {
        SpannableString s = new SpannableString("Bullet1\nBullet2\nNormal paragraph");
        s.setSpan(new BulletSpan(), 0, 8, Spanned.SPAN_PARAGRAPH);
        s.setSpan(new BulletSpan(), 8, 16, Spanned.SPAN_PARAGRAPH);
        assertThat(Html.toHtml(s, Html.TO_HTML_PARAGRAPH_LINES_INDIVIDUAL),
                matchesIgnoringTrailingWhitespace(
                "<ul style=\"margin-top:0; margin-bottom:0;\">\n"
                + "<li dir=\"ltr\">Bullet1</li>\n"
                + "<li dir=\"ltr\">Bullet2</li>\n"
                + "</ul>\n"
                + "<p dir=\"ltr\" style=\"margin-top:0; margin-bottom:0;\">Normal paragraph</p>"));
    }

    @Test
    public void testBlockquote() {
        final int start = 0;

        SpannableString s = new SpannableString("Hello world");
        int end = s.length();
        s.setSpan(new QuoteSpan(), start, end, Spannable.SPAN_PARAGRAPH);
        assertThat(Html.toHtml(s), matchesIgnoringTrailingWhitespace(
                "<blockquote><p dir=\"ltr\">Hello world</p>\n</blockquote>"));

        s = new SpannableString("Hello\n\nworld");
        end = 7;
        s.setSpan(new QuoteSpan(), start, end, Spannable.SPAN_PARAGRAPH);
        assertThat(Html.toHtml(s), matchesIgnoringTrailingWhitespace(
                "<blockquote><p dir=\"ltr\">Hello</p>\n</blockquote>\n<p dir=\"ltr\">world</p>"));
    }

    @Test
    public void testEntities() {
        SpannableString s = new SpannableString("Hello <&> world");
        assertThat(Html.toHtml(s), matchesIgnoringTrailingWhitespace(
                "<p dir=\"ltr\">Hello &lt;&amp;&gt; world</p>"));

        s = new SpannableString("Hello \u03D5 world");
        assertThat(Html.toHtml(s), matchesIgnoringTrailingWhitespace(
                "<p dir=\"ltr\">Hello &#981; world</p>"));

        s = new SpannableString("Hello  world");
        assertThat(Html.toHtml(s), matchesIgnoringTrailingWhitespace(
                "<p dir=\"ltr\">Hello&nbsp; world</p>"));
    }

    @Test
    public void testMarkup() {
        final int start = 6;

        SpannableString s = new SpannableString("Hello bold world");
        int end = s.length() - start;
        s.setSpan(new StyleSpan(Typeface.BOLD), start, end, SPAN_EXCLUSIVE_INCLUSIVE);
        assertThat(Html.toHtml(s), matchesIgnoringTrailingWhitespace(
                "<p dir=\"ltr\">Hello <b>bold</b> world</p>"));

        s = new SpannableString("Hello italic world");
        end = s.length() - start;
        s.setSpan(new StyleSpan(Typeface.ITALIC), start, end, SPAN_EXCLUSIVE_INCLUSIVE);
        assertThat(Html.toHtml(s), matchesIgnoringTrailingWhitespace(
                "<p dir=\"ltr\">Hello <i>italic</i> world</p>"));

        s = new SpannableString("Hello monospace world");
        end = s.length() - start;
        s.setSpan(new TypefaceSpan("monospace"), start, end, SPAN_EXCLUSIVE_INCLUSIVE);
        assertThat(Html.toHtml(s), matchesIgnoringTrailingWhitespace(
                "<p dir=\"ltr\">Hello <tt>monospace</tt> world</p>"));

        s = new SpannableString("Hello superscript world");
        end = s.length() - start;
        s.setSpan(new SuperscriptSpan(), start, end, SPAN_EXCLUSIVE_INCLUSIVE);
        assertThat(Html.toHtml(s), matchesIgnoringTrailingWhitespace(
                "<p dir=\"ltr\">Hello <sup>superscript</sup> world</p>"));

        s = new SpannableString("Hello subscript world");
        end = s.length() - start;
        s.setSpan(new SubscriptSpan(), start, end, SPAN_EXCLUSIVE_INCLUSIVE);
        assertThat(Html.toHtml(s), matchesIgnoringTrailingWhitespace(
                "<p dir=\"ltr\">Hello <sub>subscript</sub> world</p>"));

        s = new SpannableString("Hello underline world");
        end = s.length() - start;
        s.setSpan(new UnderlineSpan(), start, end, SPAN_EXCLUSIVE_INCLUSIVE);
        assertThat(Html.toHtml(s), matchesIgnoringTrailingWhitespace(
                "<p dir=\"ltr\">Hello <u>underline</u> world</p>"));

        s = new SpannableString("Hello struck world");
        end = s.length() - start;
        s.setSpan(new StrikethroughSpan(), start, end, SPAN_EXCLUSIVE_INCLUSIVE);
        assertThat(Html.toHtml(s), matchesIgnoringTrailingWhitespace("<p dir=\"ltr\">Hello "
                + "<span style=\"text-decoration:line-through;\">struck</span> world</p>"));

        s = new SpannableString("Hello linky world");
        end = s.length() - start;
        s.setSpan(new URLSpan("http://www.google.com"), start, end, SPAN_EXCLUSIVE_INCLUSIVE);
        assertThat(Html.toHtml(s), matchesIgnoringTrailingWhitespace("<p dir=\"ltr\">Hello "
                + "<a href=\"http://www.google.com\">linky</a> world</p>"));

        s = new SpannableString("Hello foreground world");
        end = s.length() - start;
        s.setSpan(new ForegroundColorSpan(0x00FF00), start, end, SPAN_EXCLUSIVE_INCLUSIVE);
        assertThat(Html.toHtml(s), matchesIgnoringTrailingWhitespace("<p dir=\"ltr\">Hello "
                + "<span style=\"color:#00FF00;\">foreground</span> world</p>"));

        s = new SpannableString("Hello background world");
        end = s.length() - start;
        s.setSpan(new BackgroundColorSpan(0x00FF00), start, end, SPAN_EXCLUSIVE_INCLUSIVE);
        assertThat(Html.toHtml(s), matchesIgnoringTrailingWhitespace("<p dir=\"ltr\">Hello "
                + "<span style=\"background-color:#00FF00;\">background</span> world</p>"));
    }

    @Test
    public void testMarkupFromHtml() {
        final int expectedStart = 6;
        final int expectedEnd = expectedStart + 6;

        String tags[] = {"del", "s", "strike"};
        for (String tag : tags) {
            String source = String.format("Hello <%s>struck</%s> world", tag, tag);
            Spanned spanned = Html.fromHtml(source);
            Object[] spans = spanned.getSpans(0, spanned.length(), Object.class);
            assertEquals(1, spans.length);
            assertEquals(StrikethroughSpan.class, spans[0].getClass());
            assertEquals(expectedStart, spanned.getSpanStart(spans[0]));
            assertEquals(expectedEnd, spanned.getSpanEnd(spans[0]));
        }
    }

    /**
     * Tests if text alignments encoded as CSS TEXT-ALIGN property are correctly converted into
     * {@link AlignmentSpan}s. Note that the span will also cover the first newline character after
     * the text.
     */
    @Test
    public void testTextAlignCssFromHtml() {
        String tags[] = {"p", "h1", "h2", "h3", "h4", "h5", "h6", "div", "blockquote"};

        for (String tag : tags) {
            String source = String.format("<%s style=\"text-align:start\">TEXT</%s>"
                    + "<%s style=\"text-align:center\">TEXT</%s>"
                    + "<%s style=\"text-align:end\">TEXT</%s>",
                    tag, tag, tag, tag, tag, tag);
            Spanned spanned = Html.fromHtml(source);
            AlignmentSpan[] spans = spanned.getSpans(0, spanned.length(), AlignmentSpan.class);
            assertEquals(3, spans.length);

            assertEquals(Layout.Alignment.ALIGN_NORMAL, spans[0].getAlignment());
            assertEquals(0, spanned.getSpanStart(spans[0]));
            assertEquals(5, spanned.getSpanEnd(spans[0]));

            assertEquals(Layout.Alignment.ALIGN_CENTER, spans[1].getAlignment());
            assertEquals(6, spanned.getSpanStart(spans[1]));
            assertEquals(11, spanned.getSpanEnd(spans[1]));

            assertEquals(Layout.Alignment.ALIGN_OPPOSITE, spans[2].getAlignment());
            assertEquals(12, spanned.getSpanStart(spans[2]));
            assertEquals(17, spanned.getSpanEnd(spans[2]));

            // Other valid TEXT-ALIGN property encodings
            source = String.format("<%s style=\'text-align:center\''>TEXT</%s>"
                    + "<%s style=\"text-align:center;\">TEXT</%s>"
                    + "<%s style=\"text-align  :  center  ;  \">TEXT</%s>",
                    tag, tag, tag, tag, tag, tag);
            spanned = Html.fromHtml(source);
            spans = spanned.getSpans(0, spanned.length(), AlignmentSpan.class);
            assertEquals(3, spans.length);

            // Invalid TEXT-ALIGN property encodings
            source = String.format("<%s style=\"text-align:centre\">TEXT</%s>"
                    + "<%s style=\"text-alignment:center\">TEXT</%s>"
                    + "<%s style=\"align:center\">TEXT</%s>"
                    + "<%s style=\"text-align:gibberish\">TEXT</%s>",
                    tag, tag, tag, tag, tag, tag, tag, tag);
            spanned = Html.fromHtml(source);
            spans = spanned.getSpans(0, spanned.length(), AlignmentSpan.class);
            assertEquals(0, spans.length);
        }
    }

    @Test
    public void testBlockLevelElementsFromHtml() {
        String source = "<blockquote>BLOCKQUOTE</blockquote>"
                + "<div>DIV</div>"
                + "<p>P</p>"
                + "<h1>HEADING</h1>";

        int flags = Html.FROM_HTML_SEPARATOR_LINE_BREAK_BLOCKQUOTE
                | Html.FROM_HTML_SEPARATOR_LINE_BREAK_DIV;
        assertEquals("BLOCKQUOTE\nDIV\n\nP\n\nHEADING\n\n",
                Html.fromHtml(source, flags, null, null).toString());

        flags = Html.FROM_HTML_SEPARATOR_LINE_BREAK_DIV
                | Html.FROM_HTML_SEPARATOR_LINE_BREAK_PARAGRAPH;
        assertEquals("BLOCKQUOTE\n\nDIV\nP\n\nHEADING\n\n",
                Html.fromHtml(source, flags, null, null).toString());

        flags = Html.FROM_HTML_SEPARATOR_LINE_BREAK_PARAGRAPH
                | Html.FROM_HTML_SEPARATOR_LINE_BREAK_HEADING;
        assertEquals("BLOCKQUOTE\n\nDIV\n\nP\nHEADING\n",
                Html.fromHtml(source, flags, null, null).toString());
    }

    @Test
    public void testListFromHtml() {
        String source = "CITRUS FRUITS:<ul><li>LEMON</li><li>LIME</li><li>ORANGE</li></ul>";
        assertEquals("CITRUS FRUITS:\n\nLEMON\n\nLIME\n\nORANGE\n\n",
                Html.fromHtml(source).toString());

        int flags = Html.FROM_HTML_SEPARATOR_LINE_BREAK_LIST;
        // The <li> still has to be separated by two newline characters
        assertEquals("CITRUS FRUITS:\n\nLEMON\n\nLIME\n\nORANGE\n\n",
                Html.fromHtml(source, flags, null, null).toString());

        flags = Html.FROM_HTML_SEPARATOR_LINE_BREAK_LIST_ITEM;
        assertEquals("CITRUS FRUITS:\n\nLEMON\nLIME\nORANGE\n\n",
                Html.fromHtml(source, flags, null, null).toString());

        flags = Html.FROM_HTML_SEPARATOR_LINE_BREAK_LIST
                | Html.FROM_HTML_SEPARATOR_LINE_BREAK_LIST_ITEM;
        assertEquals("CITRUS FRUITS:\nLEMON\nLIME\nORANGE\n",
                Html.fromHtml(source, flags, null, null).toString());
    }

    @Test
    public void testParagraphFromHtml() {
        final int flags = Html.FROM_HTML_SEPARATOR_LINE_BREAK_PARAGRAPH;

        String source = "<p>Line 1</p><p>Line 2</p>";
        assertEquals("Line 1\nLine 2\n",
                Html.fromHtml(source, flags).toString());
        assertEquals("Line 1\n\nLine 2\n\n",
                Html.fromHtml(source).toString());

        source = "<br>Line 1<br>Line 2<br>";
        assertEquals("\nLine 1\nLine 2\n",
                Html.fromHtml(source, flags).toString());
        assertEquals("\nLine 1\nLine 2\n",
                Html.fromHtml(source).toString());

        source = "<br><p>Line 1</p><br><p>Line 2</p><br>";
        assertEquals("\nLine 1\n\nLine 2\n\n",
                Html.fromHtml(source, flags).toString());
        assertEquals("\n\nLine 1\n\n\nLine 2\n\n\n",
                Html.fromHtml(source).toString());

        source = "<p>Line 1<br>Line 2</p><p>Line 3</p>";
        assertEquals("Line 1\nLine 2\nLine 3\n",
                Html.fromHtml(source, flags).toString());
        assertEquals("Line 1\nLine 2\n\nLine 3\n\n",
                Html.fromHtml(source).toString());
    }

    @Test
    public void testHeadingFromHtml() {
        final int flags = Html.FROM_HTML_SEPARATOR_LINE_BREAK_HEADING;

        String source = "<h1>Heading 1</h1><h1>Heading 2</h1>";
        assertEquals("Heading 1\nHeading 2\n",
                Html.fromHtml(source, flags).toString());
        assertEquals("Heading 1\n\nHeading 2\n\n",
                Html.fromHtml(source).toString());

        source = "<br><h1>Heading 1</h1><br><h1>Heading 2</h1><br>";
        assertEquals("\nHeading 1\n\nHeading 2\n\n",
                Html.fromHtml(source, flags).toString());
        assertEquals("\n\nHeading 1\n\n\nHeading 2\n\n\n",
                Html.fromHtml(source).toString());

        source = "<h1>Heading 1<br>Heading 2</h1><h1>Heading 3</h1>";
        assertEquals("Heading 1\nHeading 2\nHeading 3\n",
                Html.fromHtml(source, flags).toString());
        assertEquals("Heading 1\nHeading 2\n\nHeading 3\n\n",
                Html.fromHtml(source).toString());
    }

    @Test
    public void testImg() {
        Spanned s = Html.fromHtml("yes<img src=\"http://example.com/foo.gif\">no");
        assertThat(Html.toHtml(s), matchesIgnoringTrailingWhitespace(
                "<p dir=\"ltr\">yes<img src=\"http://example.com/foo.gif\">no</p>"));
    }

    @Test
    public void testUtf8() {
        Spanned s = Html.fromHtml("<p>\u0124\u00eb\u0142\u0142o, world!</p>");
        assertThat(Html.toHtml(s), matchesIgnoringTrailingWhitespace(
                "<p dir=\"ltr\">&#292;&#235;&#322;&#322;o, world!</p>"));
    }

    @Test
    public void testSurrogates() {
        Spanned s = Html.fromHtml("\ud83d\udc31");
        assertThat(Html.toHtml(s), matchesIgnoringTrailingWhitespace(
                "<p dir=\"ltr\">&#128049;</p>"));
    }

    @Test
    public void testBadSurrogates() {
        Spanned s = Html.fromHtml("\udc31\ud83d");
        assertThat(Html.toHtml(s), matchesIgnoringTrailingWhitespace("<p dir=\"ltr\"></p>"));
    }

    private static StringIgnoringTrailingWhitespaceMatcher matchesIgnoringTrailingWhitespace(
            String expected) {
        return new StringIgnoringTrailingWhitespaceMatcher(expected);
    }

    private static final class StringIgnoringTrailingWhitespaceMatcher extends
            BaseMatcher<String> {
        private final String mStrippedString;

        public StringIgnoringTrailingWhitespaceMatcher(String string) {
            mStrippedString = stripTrailingWhitespace(string);
        }

        @Override
        public boolean matches(Object item) {
            final String string = (String) item;
            return mStrippedString.equals(stripTrailingWhitespace(string));
        }

        private String stripTrailingWhitespace(String text) {
            return text.replaceFirst("\\s+$", "");
        }

        @Override
        public void describeTo(Description description) {
            description.appendText("is equal to ")
                    .appendText(mStrippedString)
                    .appendText(" ignoring tailing whitespaces");
        }
    }
}
