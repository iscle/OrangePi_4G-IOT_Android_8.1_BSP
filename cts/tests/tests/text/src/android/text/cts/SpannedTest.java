/*
 * Copyright (C) 2016 The Android Open Source Project
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
import static org.junit.Assert.fail;

import android.support.test.filters.SmallTest;
import android.support.test.runner.AndroidJUnit4;
import android.text.Spannable;
import android.text.SpannableString;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.SpannedString;
import android.text.style.LocaleSpan;
import android.text.style.QuoteSpan;
import android.text.style.UnderlineSpan;

import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.Locale;

@SmallTest
@RunWith(AndroidJUnit4.class)
public class SpannedTest {
    // Returns an array of three Spanned objects, of three different classes:
    // SpannableString, SpannableStringBuilder, and SpannedString.
    private static Spanned[] makeSpanned(CharSequence s) {
        return new Spanned[]{
                new SpannableString(s),
                new SpannableStringBuilder(s),
                new SpannedString(s)};
    }

    @Test
    public void testCharAt() {
        final Spanned[] spannedCases = makeSpanned("\uD83D\uDE00");  // U+1F600 GRINNING FACE
        for (Spanned spanned : spannedCases) {
            assertEquals('\uD83D', spanned.charAt(0));
            assertEquals('\uDE00', spanned.charAt(1));

            try {
                spanned.charAt(-1);
                fail("should throw IndexOutOfBoundsException here");
            } catch (IndexOutOfBoundsException e) {
            }

            try {
                spanned.charAt(spanned.length());
                fail("should throw IndexOutOfBoundsException here");
            } catch (IndexOutOfBoundsException e) {
            }
        }
    }

    @Test
    public void testNextSpanTransition() {
        final int flags = Spannable.SPAN_INCLUSIVE_INCLUSIVE;
        final SpannableString text = new SpannableString("0123 5678");
        text.setSpan(new QuoteSpan(), 0, 4, flags);
        text.setSpan(new LocaleSpan((Locale) null), 2, 7, flags);
        text.setSpan(new UnderlineSpan(), 5, text.length(), flags);
        // Now there are span transitions at 0, 2, 4, 5, 7, and the end of string.

        final Spanned[] spannedCases = makeSpanned(text);
        for (Spanned spanned : spannedCases) {

            assertEquals(4, spanned.nextSpanTransition(1, spanned.length(), QuoteSpan.class));
            assertEquals(spanned.length(),
                    spanned.nextSpanTransition(4, spanned.length(), QuoteSpan.class));
            assertEquals(5, spanned.nextSpanTransition(4, spanned.length(), UnderlineSpan.class));

            assertEquals(2, spanned.nextSpanTransition(0, spanned.length(), Object.class));
            assertEquals(4, spanned.nextSpanTransition(2, spanned.length(), Object.class));
            assertEquals(5, spanned.nextSpanTransition(4, spanned.length(), Object.class));
            assertEquals(7, spanned.nextSpanTransition(5, spanned.length(), Object.class));
            assertEquals(spanned.length(),
                    spanned.nextSpanTransition(7, spanned.length(), Object.class));

            // Test that 'null' catches all spans.
            assertEquals(2, spanned.nextSpanTransition(0, spanned.length(), null));
            assertEquals(4, spanned.nextSpanTransition(2, spanned.length(), null));
            assertEquals(5, spanned.nextSpanTransition(4, spanned.length(), null));
            assertEquals(7, spanned.nextSpanTransition(5, spanned.length(), null));
            assertEquals(spanned.length(), spanned.nextSpanTransition(7, spanned.length(), null));

            // 'start' can be negative.
            assertEquals(0, spanned.nextSpanTransition(-1, spanned.length(), QuoteSpan.class));

            // 'limit' can be high.
            final int highLimit = spanned.length() + 1;
            assertEquals(highLimit, spanned.nextSpanTransition(5, highLimit, QuoteSpan.class));

            // 'limit' can be lower than 'start'. In such a case, limit should be returned.
            assertEquals(1, spanned.nextSpanTransition(5, 1, QuoteSpan.class));
        }
    }
}
