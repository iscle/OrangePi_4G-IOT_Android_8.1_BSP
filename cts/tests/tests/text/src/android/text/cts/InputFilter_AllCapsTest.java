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
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import android.support.test.filters.SmallTest;
import android.support.test.runner.AndroidJUnit4;
import android.text.InputFilter;
import android.text.InputFilter.AllCaps;
import android.text.SpannableString;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.SpannedString;

import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.Locale;

@SmallTest
@RunWith(AndroidJUnit4.class)
public class InputFilter_AllCapsTest {
    @Test
    public void testFilter() {
        // Implicitly invoked
        CharSequence source = "Caps";
        SpannableStringBuilder dest = new SpannableStringBuilder("AllTest");
        AllCaps allCaps = new AllCaps();
        InputFilter[] filters = {allCaps};
        dest.setFilters(filters);

        String expectedString1 = "AllCAPSTest";
        dest.insert(3, source);
        assertEquals(expectedString1, dest.toString());

        String expectedString2 = "AllCAPSCAPS";
        dest.replace(7, 11, source);
        assertEquals(expectedString2, dest.toString());

        dest.delete(0, 4);
        String expectedString3 = "APSCAPS";
        assertEquals(expectedString3, dest.toString());

        // Explicitly invoked
        CharSequence beforeFilterSource = "TestFilter";
        String expectedAfterFilter = "STFIL";
        CharSequence actualAfterFilter =
            allCaps.filter(beforeFilterSource, 2, 7, dest, 0, beforeFilterSource.length());
        assertEquals(expectedAfterFilter, actualAfterFilter);
    }

    @Test
    public void testFilter_nonBMP() {
        // The source string, lowerBee, is two code units that contains a single lowercase letter.
        // DESERET SMALL LETTER BEE
        final String lowerBee = new String(Character.toChars(0x1043A));
        // DESERET CAPITAL LETTER BEE
        final String upperBee = new String(Character.toChars(0x10412));

        final AllCaps allCaps = new AllCaps();
        final SpannedString dest = new SpannedString("");

        // If given the whole string, the filter should transform it to uppercase.
        assertEquals(upperBee, allCaps.filter(lowerBee, 0, lowerBee.length(), dest, 0, 0));

        // If given just part of the character, it should be treated as an isolated surrogate
        // and not get transformed, so null should be returned.
        assertNull(allCaps.filter(lowerBee, 0, 1, dest, 0, 0));
    }

    @Test
    public void testFilter_turkish() {
        final String source = "i";
        final AllCaps usAllCaps = new AllCaps(Locale.US);
        final AllCaps turkishAllCaps = new AllCaps(new Locale("tr", "TR"));
        final SpannedString dest = new SpannedString("");

        assertEquals("I", usAllCaps.filter(source, 0, source.length(), dest, 0, 0));
        assertEquals("İ", turkishAllCaps.filter(source, 0, source.length(), dest, 0, 0));
    }

    @Test
    public void testFilter_titlecase() {
        final String source = "ǈ"; // U+01C8 LATIN CAPITAL LETTER L WITH SMALL LETTER J
        final AllCaps allCaps = new AllCaps();
        final SpannedString dest = new SpannedString("");

        assertEquals("Ǉ", // LATIN CAPITAL LETTER LJ
                allCaps.filter(source, 0, source.length(), dest, 0, 0));
    }

    @Test
    public void testFilter_greekWithSpans() {
        final Locale greek = new Locale("el", "GR");
        final String lowerString = "ι\u0301ριδα";  // ίριδα with first letter decomposed
        final String upperString = "ΙΡΙΔΑ";  // uppercased

        final SpannableString source = new SpannableString(lowerString);
        final Object span = new Object();
        source.setSpan(span, 0, 2, Spanned.SPAN_INCLUSIVE_INCLUSIVE); // around "ί"

        final AllCaps greekAllCaps = new AllCaps(greek);
        final SpannedString dest = new SpannedString("");
        final CharSequence result = greekAllCaps.filter(source, 0, source.length(), dest, 0, 0);

        assertEquals(upperString, result.toString());
        assertTrue(result instanceof Spanned);
        final Spanned spannedResult = (Spanned) result;
        final Object[] resultSpans = spannedResult.getSpans(
                0, spannedResult.length(), Object.class);
        assertEquals(1, resultSpans.length);
        assertSame(span, resultSpans[0]);
        assertEquals(0, spannedResult.getSpanStart(span));
        // The two characters in source have been transformed to one character in the result.
        assertEquals(1, spannedResult.getSpanEnd(span));
        assertEquals(Spanned.SPAN_INCLUSIVE_INCLUSIVE, spannedResult.getSpanFlags(span));
    }

    @Test(expected = NullPointerException.class)
    public void testNullConstructor() {
        new AllCaps(null);
    }
}
