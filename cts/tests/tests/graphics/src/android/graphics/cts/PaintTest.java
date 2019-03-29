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

package android.graphics.cts;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapShader;
import android.graphics.ColorFilter;
import android.graphics.MaskFilter;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Paint.Align;
import android.graphics.Paint.Cap;
import android.graphics.Paint.Join;
import android.graphics.Paint.Style;
import android.graphics.Path;
import android.graphics.PathEffect;
import android.graphics.Rect;
import android.graphics.Shader;
import android.graphics.Typeface;
import android.graphics.Xfermode;
import android.graphics.fonts.FontVariationAxis;
import android.os.LocaleList;
import android.support.test.InstrumentationRegistry;
import android.support.test.filters.SmallTest;
import android.support.test.runner.AndroidJUnit4;
import android.text.SpannedString;

import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.Locale;

@SmallTest
@RunWith(AndroidJUnit4.class)
public class PaintTest {
    private static final Typeface[] TYPEFACES = new Typeface[] {
            Typeface.DEFAULT,
            Typeface.DEFAULT_BOLD,
            Typeface.MONOSPACE,
            Typeface.SANS_SERIF,
            Typeface.SERIF,
    };

    @Test
    public void testConstructor() {
        new Paint();

        new Paint(1);

        Paint p = new Paint();
        new Paint(p);
    }

    @Test
    public void testBreakText() {
        String text = "HIJKLMN";
        char[] textChars = text.toCharArray();
        SpannedString textSpan = new SpannedString(text);

        Paint p = new Paint();

        // We need to turn off kerning in order to get accurate comparisons
        p.setFlags(p.getFlags() & ~Paint.DEV_KERN_TEXT_FLAG);

        float[] widths = new float[text.length()];
        assertEquals(text.length(), p.getTextWidths(text, widths));

        float totalWidth = 0.0f;
        for (int i = 0; i < text.length(); i++) {
            totalWidth += widths[i];
        }

        for (int i = 0; i < text.length(); i++) {
            verifyBreakText(text, textChars, textSpan, i, i + 1, true, totalWidth, 1, widths[i]);
        }

        // Measure empty string
        verifyBreakText(text, textChars, textSpan, 0, 0, true, totalWidth, 0, 0);

        // Measure substring from front: "HIJ"
        verifyBreakText(text, textChars, textSpan, 0, 3, true, totalWidth,
                3, widths[0] + widths[1] + widths[2]);

        // Reverse measure substring from front: "HIJ"
        verifyBreakText(text, textChars, textSpan, 0, 3, false, totalWidth,
                3, widths[0] + widths[1] + widths[2]);

        // Measure substring from back: "MN"
        verifyBreakText(text, textChars, textSpan, 5, 7, true, totalWidth,
                2, widths[5] + widths[6]);

        // Reverse measure substring from back: "MN"
        verifyBreakText(text, textChars, textSpan, 5, 7, false, totalWidth,
                2, widths[5] + widths[6]);

        // Measure substring in the middle: "JKL"
        verifyBreakText(text, textChars, textSpan, 2, 5, true, totalWidth,
                3, widths[2] + widths[3] + widths[4]);

        // Reverse measure substring in the middle: "JKL"
        verifyBreakText(text, textChars, textSpan, 2, 5, false, totalWidth,
                3, widths[2] + widths[3] + widths[4]);

        // Measure substring in the middle and restrict width to the first 2 characters.
        verifyBreakText(text, textChars, textSpan, 2, 5, true, widths[2] + widths[3],
                2, widths[2] + widths[3]);

        // Reverse measure substring in the middle and restrict width to the last 2 characters.
        verifyBreakText(text, textChars, textSpan, 2, 5, false, widths[3] + widths[4],
                2, widths[3] + widths[4]);

        // a single Emoji (U+1f601)
        String emoji = "\ud83d\ude01";
        char[] emojiChars = emoji.toCharArray();
        SpannedString emojiSpan = new SpannedString(emoji);

        float[] emojiWidths = new float[emoji.length()];
        assertEquals(emoji.length(), p.getTextWidths(emoji, emojiWidths));

        // Measure substring with a cluster
        verifyBreakText(emoji, emojiChars, emojiSpan, 0, 2, true, 0,
                0, 0);

        // Measure substring with a cluster
        verifyBreakText(emoji, emojiChars, emojiSpan, 0, 2, true, emojiWidths[0],
                2, emojiWidths[0]);

        // Reverse measure substring with a cluster
        verifyBreakText(emoji, emojiChars, emojiSpan, 0, 2, false, 0,
                0, 0);

        // Measure substring with a cluster
        verifyBreakText(emoji, emojiChars, emojiSpan, 0, 2, false, emojiWidths[0],
                2, emojiWidths[0]);
    }

    private void verifyBreakText(String text, char[] textChars, SpannedString textSpan,
            int start, int end, boolean measureForwards, float maxWidth, int expectedCount,
            float expectedWidth) {
        Paint p = new Paint();

        // We need to turn off kerning in order to get accurate comparisons
        p.setFlags(p.getFlags() & ~Paint.DEV_KERN_TEXT_FLAG);

        int count = end - start;
        if (!measureForwards) {
            count = -count;
        }

        float[][] measured = new float[][] {
            new float[1],
            new float[1],
            new float[1]
        };
        String textSlice = text.substring(start, end);
        assertEquals(expectedCount, p.breakText(textSlice, measureForwards, maxWidth, measured[0]));
        assertEquals(expectedCount, p.breakText(textChars, start, count, maxWidth, measured[1]));
        assertEquals(expectedCount, p.breakText(textSpan, start, end, measureForwards, maxWidth,
                measured[2]));

        for (int i = 0; i < measured.length; i++) {
            assertEquals("i: " + i, expectedWidth, measured[i][0], 0.0f);
        }
    }

    @Test
    public void testSet() {
        Paint p  = new Paint();
        Paint p2 = new Paint();
        ColorFilter c = new ColorFilter();
        MaskFilter m  = new MaskFilter();
        PathEffect e  = new PathEffect();
        Shader s      = new Shader();
        Typeface t    = Typeface.DEFAULT;
        Xfermode x = new Xfermode();

        p.setColorFilter(c);
        p.setMaskFilter(m);
        p.setPathEffect(e);
        p.setShader(s);
        p.setTypeface(t);
        p.setXfermode(x);
        p2.set(p);
        assertEquals(c, p2.getColorFilter());
        assertEquals(m, p2.getMaskFilter());
        assertEquals(e, p2.getPathEffect());
        assertEquals(s, p2.getShader());
        assertEquals(t, p2.getTypeface());
        assertEquals(x, p2.getXfermode());

        p2.set(p2);
        assertEquals(c, p2.getColorFilter());
        assertEquals(m, p2.getMaskFilter());
        assertEquals(e, p2.getPathEffect());
        assertEquals(s, p2.getShader());
        assertEquals(t, p2.getTypeface());
        assertEquals(x, p2.getXfermode());

        p.setColorFilter(null);
        p.setMaskFilter(null);
        p.setPathEffect(null);
        p.setShader(null);
        p.setTypeface(null);
        p.setXfermode(null);
        p2.set(p);
        assertNull(p2.getColorFilter());
        assertNull(p2.getMaskFilter());
        assertNull(p2.getPathEffect());
        assertNull(p2.getShader());
        assertNull(p2.getTypeface());
        assertNull(p2.getXfermode());

        p2.set(p2);
        assertNull(p2.getColorFilter());
        assertNull(p2.getMaskFilter());
        assertNull(p2.getPathEffect());
        assertNull(p2.getShader());
        assertNull(p2.getTypeface());
        assertNull(p2.getXfermode());
    }

    @Test
    public void testAccessStrokeCap() {
        Paint p = new Paint();

        p.setStrokeCap(Cap.BUTT);
        assertEquals(Cap.BUTT, p.getStrokeCap());

        p.setStrokeCap(Cap.ROUND);
        assertEquals(Cap.ROUND, p.getStrokeCap());

        p.setStrokeCap(Cap.SQUARE);
        assertEquals(Cap.SQUARE, p.getStrokeCap());
    }

    @Test(expected=RuntimeException.class)
    public void testSetStrokeCapNull() {
        Paint p = new Paint();

        p.setStrokeCap(null);
    }

    @Test
    public void testAccessXfermode() {
        Paint p = new Paint();
        Xfermode x = new Xfermode();

        assertEquals(x, p.setXfermode(x));
        assertEquals(x, p.getXfermode());

        assertNull(p.setXfermode(null));
        assertNull(p.getXfermode());
    }

    @Test
    public void testAccessShader() {
        Paint p = new Paint();
        Shader s = new Shader();

        assertEquals(s, p.setShader(s));
        assertEquals(s, p.getShader());

        assertNull(p.setShader(null));
        assertNull(p.getShader());
    }

    @Test
    public void testShaderLocalMatrix() {
        int width = 80;
        int height = 120;
        int[] color = new int[width * height];
        Bitmap bitmap = Bitmap.createBitmap(color, width, height, Bitmap.Config.RGB_565);

        Paint p = new Paint();
        Matrix m = new Matrix();
        Shader s = new BitmapShader(bitmap, Shader.TileMode.REPEAT, Shader.TileMode.REPEAT);

        // set the shaders matrix to a non identity value and attach to paint
        m.setScale(10, 0);
        s.setLocalMatrix(m);
        p.setShader(s);

        Matrix m2 = new Matrix();
        assertTrue(p.getShader().getLocalMatrix(m2));
        assertEquals(m, m2);

        // updated the matrix again and set it on the shader but NOT the paint
        m.setScale(0, 10);
        s.setLocalMatrix(m);

        // assert that the matrix on the paint's shader also changed
        Matrix m3 = new Matrix();
        assertTrue(p.getShader().getLocalMatrix(m3));
        assertEquals(m, m3);
    }

    @Test
    public void testSetAntiAlias() {
        Paint p = new Paint();

        p.setAntiAlias(true);
        assertTrue(p.isAntiAlias());

        p.setAntiAlias(false);
        assertFalse(p.isAntiAlias());
    }

    @Test
    public void testAccessTypeface() {
        Paint p = new Paint();

        assertEquals(Typeface.DEFAULT, p.setTypeface(Typeface.DEFAULT));
        assertEquals(Typeface.DEFAULT, p.getTypeface());

        assertEquals(Typeface.DEFAULT_BOLD, p.setTypeface(Typeface.DEFAULT_BOLD));
        assertEquals(Typeface.DEFAULT_BOLD, p.getTypeface());

        assertEquals(Typeface.MONOSPACE, p.setTypeface(Typeface.MONOSPACE));
        assertEquals(Typeface.MONOSPACE, p.getTypeface());

        assertNull(p.setTypeface(null));
        assertNull(p.getTypeface());
    }

    @Test
    public void testAccessPathEffect() {
        Paint p = new Paint();
        PathEffect e = new PathEffect();

        assertEquals(e, p.setPathEffect(e));
        assertEquals(e, p.getPathEffect());

        assertNull(p.setPathEffect(null));
        assertNull(p.getPathEffect());
    }

    @Test
    public void testSetFakeBoldText() {
        Paint p = new Paint();

        p.setFakeBoldText(true);
        assertTrue(p.isFakeBoldText());

        p.setFakeBoldText(false);
        assertFalse(p.isFakeBoldText());
    }

    @Test
    public void testAccessStrokeJoin() {
        Paint p = new Paint();

        p.setStrokeJoin(Join.BEVEL);
        assertEquals(Join.BEVEL, p.getStrokeJoin());

        p.setStrokeJoin(Join.MITER);
        assertEquals(Join.MITER, p.getStrokeJoin());

        p.setStrokeJoin(Join.ROUND);
        assertEquals(Join.ROUND, p.getStrokeJoin());
    }

    @Test(expected=RuntimeException.class)
    public void testSetStrokeJoinNull() {
        Paint p = new Paint();

        p.setStrokeJoin(null);
    }

    @Test
    public void testAccessStyle() {
        Paint p = new Paint();

        p.setStyle(Style.FILL);
        assertEquals(Style.FILL, p.getStyle());

        p.setStyle(Style.FILL_AND_STROKE);
        assertEquals(Style.FILL_AND_STROKE, p.getStyle());

        p.setStyle(Style.STROKE);
        assertEquals(Style.STROKE, p.getStyle());
    }

    @Test(expected=RuntimeException.class)
    public void testSetStyleNull() {
        Paint p = new Paint();

        p.setStyle(null);
    }

    @Test
    public void testGetFontSpacing() {
        Paint p = new Paint();

        for (Typeface typeface : TYPEFACES) {
            p.setTypeface(typeface);

            p.setTextSize(10);
            float spacing10 = p.getFontSpacing();
            assertTrue(spacing10 > 0);

            p.setTextSize(20);
            float spacing20 = p.getFontSpacing();
            assertTrue(spacing20 > spacing10);
        }
    }

    @Test
    public void testSetSubpixelText() {
        Paint p = new Paint();

        p.setSubpixelText(true);
        assertTrue(p.isSubpixelText());

        p.setSubpixelText(false);
        assertFalse(p.isSubpixelText());
    }

    @Test
    public void testAccessTextScaleX() {
        Paint p = new Paint();

        p.setTextScaleX(2.0f);
        assertEquals(2.0f, p.getTextScaleX(), 0.0f);

        p.setTextScaleX(1.0f);
        assertEquals(1.0f, p.getTextScaleX(), 0.0f);

        p.setTextScaleX(0.0f);
        assertEquals(0.0f, p.getTextScaleX(), 0.0f);

    }

    @Test
    public void testAccessMaskFilter() {
        Paint p = new Paint();
        MaskFilter m = new MaskFilter();

        assertEquals(m, p.setMaskFilter(m));
        assertEquals(m, p.getMaskFilter());

        assertNull(p.setMaskFilter(null));
        assertNull(p.getMaskFilter());
    }

    @Test
    public void testAccessColorFilter() {
        Paint p = new Paint();
        ColorFilter c = new ColorFilter();

        assertEquals(c, p.setColorFilter(c));
        assertEquals(c, p.getColorFilter());

        assertNull(p.setColorFilter(null));
        assertNull(p.getColorFilter());
    }

    @Test
    public void testSetARGB() {
        Paint p = new Paint();

        p.setARGB(0, 0, 0, 0);
        assertEquals(0, p.getColor());

        p.setARGB(3, 3, 3, 3);
        assertEquals((3 << 24) | (3 << 16) | (3 << 8) | 3, p.getColor());
    }

    @Test
    public void testAscent() {
        Paint p = new Paint();

        for (Typeface typeface : TYPEFACES) {
            p.setTypeface(typeface);

            p.setTextSize(10);
            float ascent10 = p.ascent();
            assertTrue(ascent10 < 0);

            p.setTextSize(20);
            float ascent20 = p.ascent();
            assertTrue(ascent20 < ascent10);
        }
    }

    @Test
    public void testAccessTextSkewX() {
        Paint p = new Paint();

        p.setTextSkewX(1.0f);
        assertEquals(1.0f, p.getTextSkewX(), 0.0f);

        p.setTextSkewX(0.0f);
        assertEquals(0.0f, p.getTextSkewX(), 0.0f);

        p.setTextSkewX(-0.25f);
        assertEquals(-0.25f, p.getTextSkewX(), 0.0f);
    }

    @Test
    public void testAccessTextSize() {
        Paint p = new Paint();

        p.setTextSize(1.0f);
        assertEquals(1.0f, p.getTextSize(), 0.0f);

        p.setTextSize(2.0f);
        assertEquals(2.0f, p.getTextSize(), 0.0f);

        // text size should be greater than 0, so set -1 has no effect
        p.setTextSize(-1.0f);
        assertEquals(2.0f, p.getTextSize(), 0.0f);

        // text size should be greater than or equals to 0
        p.setTextSize(0.0f);
        assertEquals(0.0f, p.getTextSize(), 0.0f);
    }

    @Test
    public void testGetTextWidths() throws Exception {
        String text = "HIJKLMN";
        char[] textChars = text.toCharArray();
        SpannedString textSpan = new SpannedString(text);

        // Test measuring the widths of the entire text
        verifyGetTextWidths(text, textChars, textSpan, 0, 7);

        // Test measuring a substring of the text
        verifyGetTextWidths(text, textChars, textSpan, 1, 3);

        // Test measuring a substring of zero length.
        verifyGetTextWidths(text, textChars, textSpan, 3, 3);

        // Test measuring substrings from the front and back
        verifyGetTextWidths(text, textChars, textSpan, 0, 2);
        verifyGetTextWidths(text, textChars, textSpan, 4, 7);
    }

    /** Tests all four overloads of getTextWidths are the same. */
    private void verifyGetTextWidths(String text, char[] textChars, SpannedString textSpan,
            int start, int end) {
        Paint p = new Paint();
        int count = end - start;
        float[][] widths = new float[][] {
            new float[count],
            new float[count],
            new float[count],
            new float[count]
        };

        String textSlice = text.substring(start, end);
        assertEquals(count, p.getTextWidths(textSlice, widths[0]));
        assertEquals(count, p.getTextWidths(textChars, start, count, widths[1]));
        assertEquals(count, p.getTextWidths(textSpan, start, end, widths[2]));
        assertEquals(count, p.getTextWidths(text, start, end, widths[3]));

        // Check that the widths returned by the overloads are the same.
        for (int i = 0; i < count; i++) {
            assertEquals(widths[0][i], widths[1][i], 0.0f);
            assertEquals(widths[1][i], widths[2][i], 0.0f);
            assertEquals(widths[2][i], widths[3][i], 0.0f);
        }
    }

    @Test
    public void testSetStrikeThruText() {
        Paint p = new Paint();

        p.setStrikeThruText(true);
        assertTrue(p.isStrikeThruText());

        p.setStrikeThruText(false);
        assertFalse(p.isStrikeThruText());
    }

    @Test
    public void testAccessTextAlign() {
        Paint p = new Paint();

        p.setTextAlign(Align.CENTER);
        assertEquals(Align.CENTER, p.getTextAlign());

        p.setTextAlign(Align.LEFT);
        assertEquals(Align.LEFT, p.getTextAlign());

        p.setTextAlign(Align.RIGHT);
        assertEquals(Align.RIGHT, p.getTextAlign());
    }

    @Test
    public void testAccessTextLocale() {
        Paint p = new Paint();

        final Locale defaultLocale = Locale.getDefault();

        // Check default
        assertEquals(defaultLocale, p.getTextLocale());

        // Check setter / getters
        p.setTextLocale(Locale.US);
        assertEquals(Locale.US, p.getTextLocale());
        assertEquals(new LocaleList(Locale.US), p.getTextLocales());

        p.setTextLocale(Locale.CHINESE);
        assertEquals(Locale.CHINESE, p.getTextLocale());
        assertEquals(new LocaleList(Locale.CHINESE), p.getTextLocales());

        p.setTextLocale(Locale.JAPANESE);
        assertEquals(Locale.JAPANESE, p.getTextLocale());
        assertEquals(new LocaleList(Locale.JAPANESE), p.getTextLocales());

        p.setTextLocale(Locale.KOREAN);
        assertEquals(Locale.KOREAN, p.getTextLocale());
        assertEquals(new LocaleList(Locale.KOREAN), p.getTextLocales());

        // Check reverting back to default
        p.setTextLocale(defaultLocale);
        assertEquals(defaultLocale, p.getTextLocale());
        assertEquals(new LocaleList(defaultLocale), p.getTextLocales());
    }

    @Test(expected=IllegalArgumentException.class)
    public void testSetTextLocaleNull() {
        Paint p = new Paint();

        p.setTextLocale(null);
    }

    @Test
    public void testAccessTextLocales() {
        Paint p = new Paint();

        final LocaleList defaultLocales = LocaleList.getDefault();

        // Check default
        assertEquals(defaultLocales, p.getTextLocales());

        // Check setter / getters for a one-member locale list
        p.setTextLocales(new LocaleList(Locale.CHINESE));
        assertEquals(Locale.CHINESE, p.getTextLocale());
        assertEquals(new LocaleList(Locale.CHINESE), p.getTextLocales());

        // Check setter / getters for a two-member locale list
        p.setTextLocales(LocaleList.forLanguageTags("fr,de"));
        assertEquals(Locale.forLanguageTag("fr"), p.getTextLocale());
        assertEquals(LocaleList.forLanguageTags("fr,de"), p.getTextLocales());

        // Check reverting back to default
        p.setTextLocales(defaultLocales);
        assertEquals(defaultLocales, p.getTextLocales());
    }

    @Test(expected=IllegalArgumentException.class)
    public void testAccessTextLocalesNull() {
        Paint p = new Paint();

        // Check that we cannot pass a null locale list
        p.setTextLocales(null);
    }

    @Test(expected=IllegalArgumentException.class)
    public void testAccessTextLocalesEmpty() {
        Paint p = new Paint();

        // Check that we cannot pass an empty locale list
        p.setTextLocales(new LocaleList());
    }

    @Test
    public void testGetFillPath() {
        Paint p = new Paint();
        Path path1 = new Path();
        Path path2 = new Path();

        assertTrue(path1.isEmpty());
        assertTrue(path2.isEmpty());
        p.getFillPath(path1, path2);
        assertTrue(path1.isEmpty());
        assertTrue(path2.isEmpty());

        // No setter
    }

    @Test
    public void testAccessAlpha() {
        Paint p = new Paint();

        p.setAlpha(0);
        assertEquals(0, p.getAlpha());

        p.setAlpha(255);
        assertEquals(255, p.getAlpha());

        // set value should between 0 and 255, so 266 is rounded to 10
        p.setAlpha(266);
        assertEquals(10, p.getAlpha());

        // set value should between 0 and 255, so -20 is rounded to 236
        p.setAlpha(-20);
        assertEquals(236, p.getAlpha());
    }

    @Test
    public void testSetFilterBitmap() {
        Paint p = new Paint();

        p.setFilterBitmap(true);
        assertTrue(p.isFilterBitmap());

        p.setFilterBitmap(false);
        assertFalse(p.isFilterBitmap());
    }

    @Test
    public void testAccessColor() {
        Paint p = new Paint();

        p.setColor(1);
        assertEquals(1, p.getColor());

        p.setColor(0);
        assertEquals(0, p.getColor());

        p.setColor(255);
        assertEquals(255, p.getColor());

        p.setColor(-1);
        assertEquals(-1, p.getColor());

        p.setColor(256);
        assertEquals(256, p.getColor());
    }

    @Test
    public void testSetShadowLayer() {
        new Paint().setShadowLayer(10, 1, 1, 0);
    }

    @Test
    public void testGetFontMetrics1() {
        Paint p = new Paint();
        Paint.FontMetrics fm = new Paint.FontMetrics();

        for (Typeface typeface : TYPEFACES) {
            p.setTypeface(typeface);

            p.setTextSize(10);
            p.getFontMetrics(fm);
            assertEquals(p.ascent(), fm.ascent, 0.0f);
            assertEquals(p.descent(), fm.descent, 0.0f);

            p.setTextSize(20);
            p.getFontMetrics(fm);
            assertEquals(p.ascent(), fm.ascent, 0.0f);
            assertEquals(p.descent(), fm.descent, 0.0f);
        }
    }

    @Test
    public void testGetFontMetrics2() {
        Paint p = new Paint();

        for (Typeface typeface : TYPEFACES) {
            p.setTypeface(typeface);

            p.setTextSize(10);
            Paint.FontMetrics fm = p.getFontMetrics();
            assertEquals(p.ascent(), fm.ascent, 0.0f);
            assertEquals(p.descent(), fm.descent, 0.0f);

            p.setTextSize(20);
            fm = p.getFontMetrics();
            assertEquals(p.ascent(), fm.ascent, 0.0f);
            assertEquals(p.descent(), fm.descent, 0.0f);
        }
    }

    @Test
    public void testAccessStrokeMiter() {
        Paint p = new Paint();

        p.setStrokeMiter(0.0f);
        assertEquals(0.0f, p.getStrokeMiter(), 0.0f);

        p.setStrokeMiter(10.0f);
        assertEquals(10.0f, p.getStrokeMiter(), 0.0f);

        // set value should be greater or equal to 0, set to -10.0f has no effect
        p.setStrokeMiter(-10.0f);
        assertEquals(10.0f, p.getStrokeMiter(), 0.0f);
    }

    @Test
    public void testClearShadowLayer() {
        new Paint().clearShadowLayer();
    }

    @Test
    public void testSetUnderlineText() {
        Paint p = new Paint();

        p.setUnderlineText(true);
        assertTrue(p.isUnderlineText());

        p.setUnderlineText(false);
        assertFalse(p.isUnderlineText());
    }

    @Test
    public void testSetDither() {
        Paint p = new Paint();

        p.setDither(true);
        assertTrue(p.isDither());

        p.setDither(false);
        assertFalse(p.isDither());
    }

    @Test
    public void testDescent() {
        Paint p = new Paint();

        for (Typeface typeface : TYPEFACES) {
            p.setTypeface(typeface);

            p.setTextSize(10);
            float descent10 = p.descent();
            assertTrue(descent10 > 0);

            p.setTextSize(20);
            float descent20 = p.descent();
            assertTrue(descent20 > descent10);
        }
    }

    @Test
    public void testAccessFlags() {
        Paint p = new Paint();

        p.setFlags(Paint.ANTI_ALIAS_FLAG);
        assertEquals(Paint.ANTI_ALIAS_FLAG, p.getFlags());

        p.setFlags(Paint.DEV_KERN_TEXT_FLAG);
        assertEquals(Paint.DEV_KERN_TEXT_FLAG, p.getFlags());
    }

    @Test
    public void testAccessStrokeWidth() {
        Paint p = new Paint();

        p.setStrokeWidth(0.0f);
        assertEquals(0.0f, p.getStrokeWidth(), 0.0f);

        p.setStrokeWidth(10.0f);
        assertEquals(10.0f, p.getStrokeWidth(), 0.0f);

        // set value must greater or equal to 0, set -10.0f has no effect
        p.setStrokeWidth(-10.0f);
        assertEquals(10.0f, p.getStrokeWidth(), 0.0f);
    }

    @Test
    public void testSetFontFeatureSettings() {
        Paint p = new Paint();
        // Roboto font (system default) has "fi" ligature
        String text = "fi";
        float[] widths = new float[text.length()];
        p.getTextWidths(text, widths);
        assertTrue(widths[0] > 0.0f);
        assertEquals(0.0f, widths[1], 0.0f);

        // Disable ligature using OpenType feature
        p.setFontFeatureSettings("'liga' off");
        p.getTextWidths(text, widths);
        assertTrue(widths[0] > 0.0f);
        assertTrue(widths[1] > 0.0f);

        // Re-enable ligature
        p.setFontFeatureSettings("'liga' on");
        p.getTextWidths(text, widths);
        assertTrue(widths[0] > 0.0f);
        assertEquals(0.0f, widths[1], 0.0f);
    }

    @Test
    public void testSetFontVariationSettings_defaultTypeface() {
        new Paint().setFontVariationSettings("'wght' 400");
    }

    @Test
    public void testSetGetFontVariationSettings() {
        final Paint defaultPaint = new Paint();

        Paint p = new Paint();
        Context context = InstrumentationRegistry.getTargetContext();
        Typeface typeface = Typeface.createFromAsset(context.getAssets(), "multiaxis.ttf");
        p.setTypeface(typeface);

        // multiaxis.ttf supports "wght", "PRIV", "PR12" axes.

        // The default variation settings should be null.
        assertNull(p.getFontVariationSettings());

        final String[] nonEffectiveSettings = {
                "'slnt' 30",  // unsupported tag
                "'BBBB' 1.0",  // unsupported tag
                "'A   ' 1.0",  // unsupported tag
                "'PR0 ' 1.3",  // unsupported tag
                "'WGHT' 0.7",  // unsupported tag (case sensitive)
                "'BBBB' 1.0, 'CCCC' 2.0",  // none of them are supported.
        };

        for (String notEffectiveSetting : nonEffectiveSettings) {
            if (!defaultPaint.setFontVariationSettings(notEffectiveSetting)) {
                // Test only when the system font don't support the above axes. OEMs may add
                // their fonts and these font may support above axes.
                assertFalse("Must return false for " + notEffectiveSetting,
                        p.setFontVariationSettings(notEffectiveSetting));
                assertNull("Must not change settings for " + notEffectiveSetting,
                        p.getFontVariationSettings());
            }
        }

        String retainSettings = "'wght' 400";
        assertTrue(p.setFontVariationSettings(retainSettings));
        for (String notEffectiveSetting : nonEffectiveSettings) {
            assertFalse(p.setFontVariationSettings(notEffectiveSetting));
            assertEquals("Must not change settings for " + notEffectiveSetting,
                    retainSettings, p.getFontVariationSettings());
        }

        // At least one axis is supported, the settings should be applied.
        final String[] effectiveSettings = {
                "'wght' 300",  // supported tag
                "'wght' 300, 'PRIV' 0.5",  // both are supported
                "'PRIV' 1.0, 'BBBB' 0.4",  // 'BBBB' is unsupported
        };

        for (String effectiveSetting : effectiveSettings) {
            assertTrue("Must return true for " + effectiveSetting,
                    p.setFontVariationSettings(effectiveSetting));
            assertEquals(effectiveSetting, p.getFontVariationSettings());
        }

        p.setFontVariationSettings("");
        assertNull(p.getFontVariationSettings());
    }

    @Test
    public void testGetTextBounds() {
        Paint p = new Paint();
        p.setTextSize(10);
        String text1 = "hello";
        Rect bounds1 = new Rect();
        Rect bounds2 = new Rect();
        p.getTextBounds(text1, 0, text1.length(), bounds1);
        char[] textChars1 = text1.toCharArray();
        p.getTextBounds(textChars1, 0, textChars1.length, bounds2);
        // verify that string and char array methods produce consistent results
        assertEquals(bounds1, bounds2);
        String text2 = "hello world";

        // verify substring produces consistent results
        p.getTextBounds(text2, 0, text1.length(), bounds2);
        assertEquals(bounds1, bounds2);

        // longer string is expected to have same left edge but be wider
        p.getTextBounds(text2, 0, text2.length(), bounds2);
        assertEquals(bounds1.left, bounds2.left);
        assertTrue(bounds2.right > bounds1.right);

        // bigger size implies bigger bounding rect
        p.setTextSize(20);
        p.getTextBounds(text1, 0, text1.length(), bounds2);
        assertTrue(bounds2.right > bounds1.right);
        assertTrue(bounds2.bottom - bounds2.top > bounds1.bottom - bounds1.top);
    }

    @Test
    public void testReset() {
        Paint p  = new Paint();
        ColorFilter c = new ColorFilter();
        MaskFilter m  = new MaskFilter();
        PathEffect e  = new PathEffect();
        Shader s      = new Shader();
        Typeface t    = Typeface.DEFAULT;
        Xfermode x = new Xfermode();

        p.setColorFilter(c);
        p.setMaskFilter(m);
        p.setPathEffect(e);
        p.setShader(s);
        p.setTypeface(t);
        p.setXfermode(x);
        p.setFlags(Paint.ANTI_ALIAS_FLAG);
        assertEquals(c, p.getColorFilter());
        assertEquals(m, p.getMaskFilter());
        assertEquals(e, p.getPathEffect());
        assertEquals(s, p.getShader());
        assertEquals(t, p.getTypeface());
        assertEquals(x, p.getXfermode());
        assertEquals(Paint.ANTI_ALIAS_FLAG, p.getFlags());

        p.reset();
        assertEquals(Paint.DEV_KERN_TEXT_FLAG | Paint.EMBEDDED_BITMAP_TEXT_FLAG, p.getFlags());
        assertEquals(null, p.getColorFilter());
        assertEquals(null, p.getMaskFilter());
        assertEquals(null, p.getPathEffect());
        assertEquals(null, p.getShader());
        assertEquals(null, p.getTypeface());
        assertEquals(null, p.getXfermode());
    }

    @Test
    public void testSetLinearText() {
        Paint p = new Paint();

        p.setLinearText(true);
        assertTrue(p.isLinearText());

        p.setLinearText(false);
        assertFalse(p.isLinearText());
    }

    @Test
    public void testGetFontMetricsInt1() {
        Paint p = new Paint();
        Paint.FontMetricsInt fmi = new Paint.FontMetricsInt();

        for (Typeface typeface : TYPEFACES) {
            p.setTypeface(typeface);

            p.setTextSize(10);
            p.getFontMetricsInt(fmi);
            assertEquals(Math.round(p.ascent()), fmi.ascent);
            assertEquals(Math.round(p.descent()), fmi.descent);

            p.setTextSize(20);
            p.getFontMetricsInt(fmi);
            assertEquals(Math.round(p.ascent()), fmi.ascent);
            assertEquals(Math.round(p.descent()), fmi.descent);
        }
    }

    @Test
    public void testGetFontMetricsInt2() {
        Paint p = new Paint();
        Paint.FontMetricsInt fmi;

        for (Typeface typeface : TYPEFACES) {
            p.setTypeface(typeface);

            p.setTextSize(10);
            fmi = p.getFontMetricsInt();
            assertEquals(Math.round(p.ascent()), fmi.ascent);
            assertEquals(Math.round(p.descent()), fmi.descent);

            p.setTextSize(20);
            fmi = p.getFontMetricsInt();
            assertEquals(Math.round(p.ascent()), fmi.ascent);
            assertEquals(Math.round(p.descent()), fmi.descent);
        }
    }

    @Test
    public void testMeasureText() {
        String text = "HIJKLMN";
        char[] textChars = text.toCharArray();
        SpannedString textSpan = new SpannedString(text);

        Paint p = new Paint();

        // We need to turn off kerning in order to get accurate comparisons
        p.setFlags(p.getFlags() & ~Paint.DEV_KERN_TEXT_FLAG);

        float[] widths = new float[text.length()];
        for (int i = 0; i < widths.length; i++) {
            widths[i] = p.measureText(text, i, i + 1);
        }

        float totalWidth = 0;
        for (int i = 0; i < widths.length; i++) {
            totalWidth += widths[i];
        }

        // Test measuring the widths of the entire text
        verifyMeasureText(text, textChars, textSpan, 0, 7, totalWidth);

        // Test measuring a substring of the text
        verifyMeasureText(text, textChars, textSpan, 1, 3, widths[1] + widths[2]);

        // Test measuring a substring of zero length.
        verifyMeasureText(text, textChars, textSpan, 3, 3, 0);

        // Test measuring substrings from the front and back
        verifyMeasureText(text, textChars, textSpan, 0, 2, widths[0] + widths[1]);
        verifyMeasureText(text, textChars, textSpan, 4, 7, widths[4] + widths[5] + widths[6]);
    }

    @Test
    public void testMeasureTextContext() {
       Paint p = new Paint();
       // Arabic LAM, which is different width depending on context
       String shortString = "\u0644";
       String longString = "\u0644\u0644\u0644";
       char[] longChars = longString.toCharArray();
       SpannedString longSpanned = new SpannedString(longString);
       float width = p.measureText(shortString);
       // Verify that measurement of substring is consistent no matter what surrounds it.
       verifyMeasureText(longString, longChars, longSpanned, 0, 1, width);
       verifyMeasureText(longString, longChars, longSpanned, 1, 2, width);
       verifyMeasureText(longString, longChars, longSpanned, 2, 3, width);
    }

    @Test
    public void testMeasureTextWithLongText() {
        final int MAX_COUNT = 65535;
        char[] longText = new char[MAX_COUNT];
        for (int n = 0; n < MAX_COUNT; n++) {
            longText[n] = 'm';
        }

        Paint p = new Paint();
        float width = p.measureText(longText, 0, 1);
        assertEquals(true, width > 0);
    }

    /** Tests that all four overloads of measureText are the same and match some value. */
    private void verifyMeasureText(String text, char[] textChars, SpannedString textSpan,
            int start, int end, float expectedWidth) {
        Paint p = new Paint();

        // We need to turn off kerning in order to get accurate comparisons
        p.setFlags(p.getFlags() & ~Paint.DEV_KERN_TEXT_FLAG);

        int count = end - start;
        float[] widths = new float[] {-1, -1, -1, -1};

        String textSlice = text.substring(start, end);
        widths[0] = p.measureText(textSlice);
        widths[1] = p.measureText(textChars, start, count);
        widths[2] = p.measureText(textSpan, start, end);
        widths[3] = p.measureText(text, start, end);

        // Check that the widths returned by the overloads are the same.
        assertEquals(widths[0], widths[1], 0.0f);
        assertEquals(widths[1], widths[2], 0.0f);
        assertEquals(widths[2], widths[3], 0.0f);
        assertEquals(widths[3], expectedWidth, 0.0f);
    }

    @Test
    public void testGetTextPathCharArray() {
        Path path = new Path();

        assertTrue(path.isEmpty());
        new Paint().getTextPath(new char[] {'H', 'I', 'J', 'K', 'L', 'M', 'N'}, 0, 7, 0, 0, path);
        assertFalse(path.isEmpty());
    }

    @Test(expected=RuntimeException.class)
    public void testGetTextPathCharArrayNegativeIndex() {
        new Paint().getTextPath(new char[] {'H', 'I', 'J', 'K', 'L', 'M', 'N'}, -2, 7, 0, 0,
                new Path());
    }

    @Test(expected=RuntimeException.class)
    public void testGetTextPathCharArrayNegativeCount() {
        new Paint().getTextPath(new char[] {'H', 'I', 'J', 'K', 'L', 'M', 'N'}, 0, -3, 0, 0,
                new Path());
    }

    @Test(expected=RuntimeException.class)
    public void testGetTextPathCharArrayCountTooHigh() {
        new Paint().getTextPath(new char[] {'H', 'I', 'J', 'K', 'L', 'M', 'N'}, 3, 7, 0, 0,
                new Path());
    }

    @Test
    public void testGetTextPathString() {
        Path path = new Path();

        assertTrue(path.isEmpty());
        new Paint().getTextPath("HIJKLMN", 0, 7, 0, 0, path);
        assertFalse(path.isEmpty());
    }

    @Test(expected=RuntimeException.class)
    public void testGetTextPathStringNegativeIndex() {
        new Paint().getTextPath("HIJKLMN", -2, 7, 0, 0, new Path());
    }

    @Test(expected=RuntimeException.class)
    public void testGetTextPathStringNegativeCount() {
        new Paint().getTextPath("HIJKLMN", 0, -3, 0, 0, new Path());
    }

    @Test(expected=RuntimeException.class)
    public void testGetTextPathStringStartTooHigh() {
        new Paint().getTextPath("HIJKLMN", 7, 3, 0, 0, new Path());
    }

    @Test(expected=RuntimeException.class)
    public void testGetTextPathStringCountTooHigh() {
        new Paint().getTextPath("HIJKLMN", 3, 9, 0, 0, new Path());
    }

    @Test
    public void testHasGlyph() {
        Paint p = new Paint();

        // This method tests both the logic of hasGlyph and the sanity of fonts present
        // on the device.
        assertTrue(p.hasGlyph("A"));
        assertFalse(p.hasGlyph("\uFFFE"));  // U+FFFE is guaranteed to be a noncharacter

        // Roboto 2 (the default typeface) does have an "fi" glyph and is mandated by CDD
        assertTrue(p.hasGlyph("fi"));
        assertFalse(p.hasGlyph("ab"));  // but it does not contain an "ab" glyph
        assertTrue(p.hasGlyph("\u02E5\u02E9"));  // IPA tone mark ligature

        // variation selectors
        assertFalse(p.hasGlyph("a\uFE0F"));
        assertFalse(p.hasGlyph("a\uDB40\uDDEF"));  // UTF-16 encoding of U+E01EF
        assertFalse(p.hasGlyph("\u2229\uFE0F"));  // base character is in mathematical symbol font
        // Note: U+FE0F is variation selection, unofficially reserved for emoji

        // regional indicator symbols
        assertTrue(p.hasGlyph("\uD83C\uDDEF\uD83C\uDDF5"));   // "JP" U+1F1EF U+1F1F5
        assertFalse(p.hasGlyph("\uD83C\uDDFF\uD83C\uDDFF"));  // "ZZ" U+1F1FF U+1F1FF

        // Mongolian, which is an optional font, but if present, should support FVS
        if (p.hasGlyph("\u182D")) {
            assertTrue(p.hasGlyph("\u182D\u180B"));
        }

        // Emoji with variation selector support for both text and emoji presentation
        assertTrue(p.hasGlyph("\u231A\uFE0E"));  // WATCH + VS15
        assertTrue(p.hasGlyph("\u231A\uFE0F"));  // WATCH + VS16

        // Unicode 7.0, 8.0, and 9.0 emoji should be supported.
        assertTrue(p.hasGlyph("\uD83D\uDD75"));  // SLEUTH OR SPY is introduced in Unicode 7.0
        assertTrue(p.hasGlyph("\uD83C\uDF2E"));  // TACO is introduced in Unicode 8.0
        assertTrue(p.hasGlyph("\uD83E\uDD33"));  // SELFIE is introduced in Unicode 9.0

        // We don't require gender-neutral emoji, but if present, results must be consistent
        // whether VS is present or not.
        assertTrue(p.hasGlyph("\uD83D\uDC69\u200D\u2695") ==  // WOMAN, ZWJ, STAFF OF AESCULAPIUS
                p.hasGlyph("\uD83D\uDC69\u200D\u2695\uFE0F"));  // above + VS16
    }

    @Test
    public void testGetRunAdvance() {
        Paint p = new Paint();
        {
            // LTR
            String string = "abcdef";
            {
                final float width = p.getRunAdvance(string, 0, string.length(), 0,
                        string.length(), false, 0);
                assertEquals(0.0f, width, 0.0f);
            }
            {
                for (int i = 0; i < string.length(); i++) {
                    final float width = p.getRunAdvance(string, i, i + 1, 0, string.length(),
                            false, i);
                    assertEquals(0.0f, width, 0.0f);
                }
            }
            {
                final float widthToMid = p.getRunAdvance(string, 0, string.length(), 0,
                        string.length(), false, string.length() / 2);
                final float widthToTail = p.getRunAdvance(string, 0, string.length(), 0,
                        string.length(), false, string.length());
                assertTrue(widthToMid > 0.0f);
                assertTrue(widthToTail > widthToMid);
            }
            {
                final float widthFromHead = p.getRunAdvance(string, 0, string.length(), 0,
                        string.length(), false, string.length());
                final float widthFromSecond = p.getRunAdvance(string, 1, string.length(), 0,
                        string.length(), false, string.length());
                assertTrue(widthFromHead > widthFromSecond);
            }
            {
                float width = 0.0f;
                for (int i = 0; i < string.length(); i++) {
                    width += p.getRunAdvance(string, i, i + 1, 0, string.length(), false, i + 1);
                }
                final float totalWidth = p.getRunAdvance(string, 0, string.length(), 0,
                        string.length(), false, string.length());
                assertEquals(totalWidth, width, 1.0f);
            }
        }
        {
            // RTL
            String string = "\u0644\u063A\u0629 \u0639\u0631\u0628\u064A\u0629"; // Arabic
            {
                final float width = p.getRunAdvance(string, 0, string.length(), 0,
                        string.length(), true, 0);
                assertEquals(0.0f, width, 0.0f);
            }
            {
                for (int i = 0; i < string.length(); i++) {
                    final float width = p.getRunAdvance(string, i, i + 1, 0, string.length(),
                            true, i);
                    assertEquals(0.0f, width, 0.0f);
                }
            }
            {
                final float widthToMid = p.getRunAdvance(string, 0, string.length(), 0,
                        string.length(), true, string.length() / 2);
                final float widthToTail = p.getRunAdvance(string, 0, string.length(), 0,
                        string.length(), true, string.length());
                assertTrue(widthToMid > 0.0f);
                assertTrue(widthToTail > widthToMid);
            }
            {
                final float widthFromHead = p.getRunAdvance(string, 0, string.length(), 0,
                        string.length(), true, string.length());
                final float widthFromSecond = p.getRunAdvance(string, 1, string.length(), 0,
                        string.length(), true, string.length());
                assertTrue(widthFromHead > widthFromSecond);
            }
        }
    }

    @Test(expected=IllegalArgumentException.class)
    public void testGetRunAdvanceNullCharSequence() {
        new Paint().getRunAdvance((CharSequence) null, 0, 0, 0, 0, false, 0);
    }

    @Test(expected=IllegalArgumentException.class)
    public void testGetRunAdvanceNullCharArray() {
        new Paint().getRunAdvance((char[]) null, 0, 0, 0, 0, false, 0);
    }

    @Test(expected=IndexOutOfBoundsException.class)
    public void testGetRunAdvanceTextLengthLessThenContextEnd() {
        final String string = "abcde";

        // text length < context end
        new Paint().getRunAdvance(string, 0, string.length(), 0, string.length() + 1, false,
                string.length());
    }

    @Test(expected=IndexOutOfBoundsException.class)
    public void testGetRunAdvanceContextEndLessThanEnd() {
        final String string = "abcde";

        // context end < end
        new Paint().getRunAdvance(string, 0, string.length(), 0, string.length() - 1, false, 0);
    }

    @Test(expected=IndexOutOfBoundsException.class)
    public void testGetRunAdvanceEndLessThanOffset() {
        final String string = "abcde";

        // end < offset
        new Paint().getRunAdvance(string, 0, string.length() - 1, 0, string.length() - 1, false,
                string.length());
    }

    @Test(expected=IndexOutOfBoundsException.class)
    public void testGetRunAdvanceOffsetLessThanStart() {
        final String string = "abcde";

        // offset < start
        new Paint().getRunAdvance(string, 1, string.length(), 1, string.length(), false, 0);
    }

    @Test(expected=IndexOutOfBoundsException.class)
    public void testGetRunAdvanceStartLessThanContextStart() {
        final String string = "abcde";

        // start < context start
        new Paint().getRunAdvance(string, 0, string.length(), 1, string.length(), false, 1);
    }

    @Test(expected=IndexOutOfBoundsException.class)
    public void testGetRunAdvanceContextStartNegative() {
        final String string = "abcde";

        // context start < 0
        new Paint().getRunAdvance(string, 0, string.length(), -1, string.length(), false, 0);
    }

    @Test
    public void testGetRunAdvance_nonzeroIndex() {
        Paint p = new Paint();
        final String text = "Android powers hundreds of millions of mobile " +
                "devices in more than 190 countries around the world. It's" +
                "the largest installed base of any mobile platform and" +
                "growing fast—every day another million users power up their" +
                "Android devices for the first time and start looking for" +
                "apps, games, and other digital content.";
        // Test offset index does not affect width.
        final float widthAndroidFirst = p.getRunAdvance(
                text, 0, 7, 0, text.length(), false, 7);
        final float widthAndroidSecond = p.getRunAdvance(
                text, 215, 222, 0, text.length(), false, 222);
        assertTrue(Math.abs(widthAndroidFirst - widthAndroidSecond) < 1);
    }

    @Test
    public void testGetRunAdvance_glyphDependingContext() {
        Paint p = new Paint();
        // Test the context change the character shape.
        // First character should be isolated form because the context ends at index 1.
        final float isolatedFormWidth = p.getRunAdvance("\u0644\u0644", 0, 1, 0, 1, true, 1);
        // First character should be initial form because the context ends at index 2.
        final float initialFormWidth = p.getRunAdvance("\u0644\u0644", 0, 1, 0, 2, true, 1);
        assertTrue(isolatedFormWidth > initialFormWidth);
    }

    @Test
    public void testGetRunAdvance_arabic() {
        Paint p = new Paint();
        // Test total width is equals to sum of each character's width.
        // "What is Unicode?" in Arabic.
        final String text =
                "\u0645\u0627\u0020\u0647\u064A\u0020\u0627\u0644\u0634" +
                "\u0641\u0631\u0629\u0020\u0627\u0644\u0645\u0648\u062D" +
                "\u062F\u0629\u0020\u064A\u0648\u0646\u064A\u0643\u0648" +
                "\u062F\u061F";
        final float totalWidth = p.getRunAdvance(
                text, 0, text.length(), 0, text.length(), true, text.length());
        float sumOfCharactersWidth = 0;
        for (int i = 0; i < text.length(); i++) {
            sumOfCharactersWidth += p.getRunAdvance(
                    text, i, i + 1, 0, text.length(), true, i + 1);
        }
        assertTrue(Math.abs(totalWidth - sumOfCharactersWidth) < 1);
    }

    @Test
    public void testGetOffsetForAdvance() {
        Paint p = new Paint();
        {
            // LTR
            String string = "abcdef";
            {
                for (int offset = 0; offset <= string.length(); ++offset) {
                    final float widthToOffset = p.getRunAdvance(string, 0,
                            string.length(), 0, string.length(), false, offset);
                    final int restoredOffset = p.getOffsetForAdvance(string, 0,
                            string.length(), 0, string.length(), false, widthToOffset);
                    assertEquals(offset, restoredOffset);
                }
            }
            {
                final int offset = p.getOffsetForAdvance(string, 0, string.length(), 0,
                        string.length(), false, -10.0f);
                assertEquals(0, offset);
            }
            {
                final float widthToEnd = p.getRunAdvance(string, 0, string.length(), 0,
                        string.length(), true, string.length());
                final int offset = p.getOffsetForAdvance(string, 0, string.length(), 0,
                        string.length(), true, widthToEnd + 10.0f);
                assertEquals(string.length(), offset);
            }
        }
        {
            // RTL
            String string = "\u0639\u0631\u0628\u0649"; // Arabic
            {
                for (int offset = 0; offset <= string.length(); ++offset) {
                    final float widthToOffset = p.getRunAdvance(string, 0,
                            string.length(), 0, string.length(), true, offset);
                    final int restoredOffset = p.getOffsetForAdvance(string, 0,
                            string.length(), 0, string.length(), true, widthToOffset);
                    assertEquals(offset, restoredOffset);
                }
            }
            {
                final int offset = p.getOffsetForAdvance(string, 0, string.length(), 0,
                        string.length(), true, -10.0f);
                assertEquals(0, offset);
            }
            {
                final float widthToEnd = p.getRunAdvance(string, 0, string.length(), 0,
                        string.length(), true, string.length());
                final int offset = p.getOffsetForAdvance(string, 0, string.length(), 0,
                        string.length(), true, widthToEnd + 10.0f);
                assertEquals(string.length(), offset);
            }
        }
    }

    @Test(expected=IllegalArgumentException.class)
    public void testGetOffsetForAdvanceNullCharSequence() {
        new Paint().getOffsetForAdvance((CharSequence) null, 0, 0, 0, 0, false, 0.0f);
    }

    @Test(expected=IllegalArgumentException.class)
    public void testGetOffsetForAdvanceNullCharArray() {
        new Paint().getOffsetForAdvance((char[]) null, 0, 0, 0, 0, false, 0.0f);
    }

    @Test(expected=IndexOutOfBoundsException.class)
    public void testGetOffsetForAdvanceContextStartNegative() {
        final String string = "abcde";

        // context start < 0
        new Paint().getOffsetForAdvance(string, -1, string.length(), 0, string.length(), false,
                0.0f);
    }

    @Test(expected=IndexOutOfBoundsException.class)
    public void testGetOffsetForAdvanceStartLessThanContextStart() {
        final String string = "abcde";

        // start < context start
        new Paint().getOffsetForAdvance(string, 0, string.length(), 1, string.length(), false,
                0.0f);
    }

    @Test(expected=IndexOutOfBoundsException.class)
    public void testGetOffsetForAdvanceEndLessThanStart() {
        final String string = "abcde";

        // end < start
        new Paint().getOffsetForAdvance(string, 1, 0, 0, 0, false, 0);
    }

    @Test(expected=IndexOutOfBoundsException.class)
    public void testGetOffsetForAdvanceContextEndLessThanEnd() {
        final String string = "abcde";

        // context end < end
        new Paint().getOffsetForAdvance(string, 0, string.length(), 0, string.length() - 1, false,
                0.0f);
    }

    @Test(expected=IndexOutOfBoundsException.class)
    public void testGetOffsetForAdvanceTextLengthLessThanContextEnd() {
        final String string = "abcde";

        // text length < context end
        new Paint().getOffsetForAdvance(string, 0, string.length(), 0, string.length() + 1, false,
                0.0f);
    }

    @Test
    public void testGetOffsetForAdvance_graphemeCluster() {
        Paint p = new Paint();
        {
            String string = "\uD83C\uDF37"; // U+1F337: TULIP
            {
                final float widthToOffset = p.getRunAdvance(string, 0,
                        string.length(), 0, string.length(), false, 1);
                final int offset = p.getOffsetForAdvance(string, 0, string.length(), 0,
                        string.length(), false, widthToOffset);
                assertFalse(1 == offset);
                assertTrue(0 == offset || string.length() == offset);
            }
        }
        {
            String string = "\uD83C\uDDFA\uD83C\uDDF8"; // US flag
            {
                final float widthToOffset = p.getRunAdvance(string, 0,
                        string.length(), 0, string.length(), false, 2);
                final int offset = p.getOffsetForAdvance(string, 0, string.length(), 0,
                        string.length(), false, widthToOffset);
                assertFalse(2 == offset);
                assertTrue(0 == offset || string.length() == offset);
            }
            {
                final float widthToOffset = p.getRunAdvance(string, 0, 2, 0, 2, false, 2);
                final int offset = p.getOffsetForAdvance(string, 0, 2,
                        0, 2, false, widthToOffset);
                assertEquals(2, offset);
            }
        }
        {
            // HANGUL CHOSEONG KIYEOK, HANGUL JUNGSEONG A, HANDUL JONGSEONG KIYEOK
            String string = "\u1100\u1161\u11A8";
            {
                for (int offset = 0; offset <= string.length(); ++offset) {
                    final float widthToOffset = p.getRunAdvance(string, 0,
                            string.length(), 0, string.length(), false, offset);
                    final int offsetForAdvance = p.getOffsetForAdvance(string, 0, string.length(),
                            0, string.length(), false, widthToOffset);
                    assertTrue(0 == offsetForAdvance || string.length() == offsetForAdvance);
                }
                for (int offset = 0; offset <= string.length(); ++offset) {
                    final float widthToOffset = p.getRunAdvance(string, 0, offset, 0, offset,
                            false, offset);
                    final int offsetForAdvance = p.getOffsetForAdvance(string, 0, string.length(),
                            0, string.length(), false, widthToOffset);
                    assertTrue(0 == offsetForAdvance || string.length() == offsetForAdvance);
                }
                for (int offset = 0; offset <= string.length(); ++offset) {
                    final float widthToOffset = p.getRunAdvance(string, 0, offset, 0, offset,
                            false, offset);
                    final int offsetForAdvance = p.getOffsetForAdvance(string, 0, offset, 0,
                            offset, false, widthToOffset);
                    assertEquals(offset, offsetForAdvance);
                }
            }
        }
    }

    @Test
    public void testElegantText() {
        final Paint p = new Paint();
        p.setTextSize(10);
        assertFalse(p.isElegantTextHeight());
        final float nonElegantTop = p.getFontMetrics().top;
        final float nonElegantBottom = p.getFontMetrics().bottom;

        p.setElegantTextHeight(true);
        assertTrue(p.isElegantTextHeight());
        final float elegantTop = p.getFontMetrics().top;
        final float elegantBottom = p.getFontMetrics().bottom;

        assertTrue(elegantTop < nonElegantTop);
        assertTrue(elegantBottom > nonElegantBottom);

        p.setElegantTextHeight(false);
        assertFalse(p.isElegantTextHeight());
    }
}
