/*
 * Copyright (C) 2014 The Android Open Source Project
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

package android.uirendering.cts.testclasses;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Typeface;
import android.support.test.filters.MediumTest;
import android.support.test.runner.AndroidJUnit4;
import android.uirendering.cts.R;
import android.uirendering.cts.bitmapcomparers.BitmapComparer;
import android.uirendering.cts.bitmapcomparers.MSSIMComparer;
import android.uirendering.cts.bitmapverifiers.GoldenImageVerifier;
import android.uirendering.cts.testinfrastructure.ActivityTestBase;

import org.junit.Test;
import org.junit.runner.RunWith;

@MediumTest
@RunWith(AndroidJUnit4.class)
public class FontRenderingTests extends ActivityTestBase {
    // Thresholds are barely loose enough for differences between sw and hw renderers.
    private static final double REGULAR_THRESHOLD = 0.92;
    private static final double THIN_THRESHOLD = 0.87;

    // Representative characters including some from Unicode 7
    private static final String sTestString1 = "Hambu";
    private static final String sTestString2 = "rg \u20bd";
    private static final String sTestString3 = "\u20b9\u0186\u0254\u1e24\u1e43";

    private void fontTestBody(String family, int style, int id) {
        Bitmap goldenBitmap = BitmapFactory.decodeResource(getActivity().getResources(), id);

        // adjust threshold based on thinness - more variance is expected in thin cases
        boolean thinTestCase = family.endsWith("-thin") && ((style & Typeface.BOLD) == 0);
        BitmapComparer comparer = new MSSIMComparer(
                thinTestCase ? THIN_THRESHOLD : REGULAR_THRESHOLD);

        final Typeface typeface = Typeface.create(family, style);
        createTest()
                .addCanvasClient((canvas, width, height) -> {
                    Paint p = new Paint();
                    p.setAntiAlias(true);
                    p.setColor(Color.BLACK);
                    p.setTextSize(26);
                    p.setTypeface(typeface);
                    canvas.drawText(sTestString1, 1, 20, p);
                    canvas.drawText(sTestString2, 1, 50, p);
                    canvas.drawText(sTestString3, 1, 80, p);
                })
                .runWithVerifier(new GoldenImageVerifier(goldenBitmap, comparer));
    }

    @Test
    public void testDefaultFont() {
        fontTestBody("sans-serif",
                Typeface.NORMAL,
                R.drawable.hello1);
    }

    @Test
    public void testBoldFont() {
        fontTestBody("sans-serif",
                Typeface.BOLD,
                R.drawable.bold1);
    }

    @Test
    public void testItalicFont() {
        fontTestBody("sans-serif",
                Typeface.ITALIC,
                R.drawable.italic1);
    }

    @Test
    public void testBoldItalicFont() {
        fontTestBody("sans-serif",
                Typeface.BOLD | Typeface.ITALIC,
                R.drawable.bolditalic1);
    }

    @Test
    public void testMediumFont() {
        fontTestBody("sans-serif-medium",
                Typeface.NORMAL,
                R.drawable.medium1);
    }

    @Test
    public void testMediumBoldFont() {
        // bold attribute on medium base font = black
        fontTestBody("sans-serif-medium",
                Typeface.BOLD,
                R.drawable.black1);
    }

    @Test
    public void testMediumItalicFont() {
        fontTestBody("sans-serif-medium",
                Typeface.ITALIC,
                R.drawable.mediumitalic1);
    }

    @Test
    public void testMediumBoldItalicFont() {
        fontTestBody("sans-serif-medium",
                Typeface.BOLD | Typeface.ITALIC,
                R.drawable.blackitalic1);
    }

    @Test
    public void testLightFont() {
        fontTestBody("sans-serif-light",
                Typeface.NORMAL,
                R.drawable.light1);
    }

    @Test
    public void testLightBoldFont() {
        // bold attribute on light base font = medium
        fontTestBody("sans-serif-light",
                Typeface.BOLD,
                R.drawable.medium1);
    }

    @Test
    public void testLightItalicFont() {
        fontTestBody("sans-serif-light",
                Typeface.ITALIC,
                R.drawable.lightitalic1);
    }

    @Test
    public void testLightBoldItalicFont() {
        fontTestBody("sans-serif-light",
                Typeface.BOLD | Typeface.ITALIC,
                R.drawable.mediumitalic1);
    }

    @Test
    public void testThinFont() {
        fontTestBody("sans-serif-thin",
                Typeface.NORMAL,
                R.drawable.thin1);
    }

    @Test
    public void testThinBoldFont() {
        // bold attribute on thin base font = normal
        fontTestBody("sans-serif-thin",
                Typeface.BOLD,
                R.drawable.hello1);
    }

    @Test
    public void testThinItalicFont() {
        fontTestBody("sans-serif-thin",
                Typeface.ITALIC,
                R.drawable.thinitalic1);
    }

    @Test
    public void testThinBoldItalicFont() {
        fontTestBody("sans-serif-thin",
                Typeface.BOLD | Typeface.ITALIC,
                R.drawable.italic1);
    }

    @Test
    public void testBlackFont() {
        fontTestBody("sans-serif-black",
                Typeface.NORMAL,
                R.drawable.black1);
    }

    @Test
    public void testBlackBoldFont() {
        // bold attribute on black base font = black
        fontTestBody("sans-serif-black",
                Typeface.BOLD,
                R.drawable.black1);
    }

    @Test
    public void testBlackItalicFont() {
        fontTestBody("sans-serif-black",
                Typeface.ITALIC,
                R.drawable.blackitalic1);
    }

    @Test
    public void testBlackBoldItalicFont() {
        fontTestBody("sans-serif-black",
                Typeface.BOLD | Typeface.ITALIC,
                R.drawable.blackitalic1);
    }

    /* condensed fonts */

    @Test
    public void testCondensedFont() {
        fontTestBody("sans-serif-condensed",
                Typeface.NORMAL,
                R.drawable.condensed1);
    }

    @Test
    public void testCondensedBoldFont() {
        fontTestBody("sans-serif-condensed",
                Typeface.BOLD,
                R.drawable.condensedbold1);
    }

    @Test
    public void testCondensedItalicFont() {
        fontTestBody("sans-serif-condensed",
                Typeface.ITALIC,
                R.drawable.condenseditalic1);
    }

    @Test
    public void testCondensedBoldItalicFont() {
        fontTestBody("sans-serif-condensed",
                Typeface.BOLD | Typeface.ITALIC,
                R.drawable.condensedbolditalic1);
    }

    @Test
    public void testCondensedLightFont() {
        fontTestBody("sans-serif-condensed-light",
                Typeface.NORMAL,
                R.drawable.condensedlight1);
    }

    @Test
    public void testCondensedLightItalicFont() {
        fontTestBody("sans-serif-condensed-light",
                Typeface.ITALIC,
                R.drawable.condensedlightitalic1);
    }
}
