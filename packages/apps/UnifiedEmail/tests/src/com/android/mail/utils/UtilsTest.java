/**
 * Copyright (c) 2014, Google Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.android.mail.utils;

import android.test.AndroidTestCase;
import android.test.suitebuilder.annotation.SmallTest;
import android.text.SpannableString;
import android.text.Spanned;
import android.text.style.TextAppearanceSpan;

/**
 * Tests for {@link Utils}.
 */
@SmallTest
public class UtilsTest extends AndroidTestCase {

    public void testInsertStringWithStyle() {
        final String entire = "Hello World!";
        final String sub = "World";
        final int appearance = android.R.style.TextAppearance_Holo_Small;
        final Spanned actual = Utils.insertStringWithStyle(
                getContext(), entire, sub, appearance);
        final SpannableString expected = new SpannableString(entire);
        expected.setSpan(new TextAppearanceSpan(getContext(), appearance), 6, 11, 0);

        assertSpannedEquals(expected, actual);
    }

    public void testInsertStringWithStyle_substringNotInEntire() {
        final String entire = "Hello World!";
        final String sub = "foo";
        final int appearance = android.R.style.TextAppearance_Holo_Small;
        final Spanned actual = Utils.insertStringWithStyle(
                getContext(), entire, sub, appearance);
        final SpannableString expected = new SpannableString(entire);

        assertSpannedEquals(expected, actual);
    }

    public static void assertSpannedEquals(Spanned expected, Spanned actual) {
        assertEquals(expected.length(), actual.length());
        assertEquals(expected.toString(), actual.toString());
        if (expected.length() > 0) {
            TextAppearanceSpan[] expectedSpans =
                    expected.getSpans(0, expected.length(), TextAppearanceSpan.class);
            TextAppearanceSpan[] actualSpans =
                    actual.getSpans(0, actual.length(), TextAppearanceSpan.class);
            assertEquals(expectedSpans.length, actualSpans.length);
            for (int i = 0 ; i < expectedSpans.length ; i++) {
                assertTextAppearanceSpanEquals(expectedSpans[i], actualSpans[i]);
            }
        }
    }

    public static void assertTextAppearanceSpanEquals(
            TextAppearanceSpan expected, TextAppearanceSpan actual) {
        assertEquals(expected.describeContents(), actual.describeContents());
        assertEquals(expected.getTextStyle(), actual.getTextStyle());
        assertEquals(expected.getTextColor(), actual.getTextColor());
        assertEquals(expected.getLinkTextColor(), actual.getLinkTextColor());
        assertEquals(expected.getFamily(), actual.getFamily());
        assertEquals(expected.getTextSize(), actual.getTextSize());
    }
}
