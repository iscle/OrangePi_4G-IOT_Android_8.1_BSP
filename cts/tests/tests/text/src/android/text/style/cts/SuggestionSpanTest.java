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

package android.text.style.cts;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import android.content.Context;
import android.content.res.Configuration;
import android.os.LocaleList;
import android.os.Parcel;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.test.InstrumentationRegistry;
import android.support.test.filters.SmallTest;
import android.support.test.runner.AndroidJUnit4;
import android.text.SpannableString;
import android.text.Spanned;
import android.text.StaticLayout;
import android.text.TextPaint;
import android.text.style.SuggestionSpan;

import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.Locale;

/**
 * Test {@link SuggestionSpan}.
 */
@SmallTest
@RunWith(AndroidJUnit4.class)
public class SuggestionSpanTest {
    @Test
    public void testConstructorWithContext() {
        final String[] suggestions = new String[] {"suggestion1", "suggestion2"};
        final Configuration overrideConfig = new Configuration();
        final Locale locale = Locale.forLanguageTag("az-Arab");
        overrideConfig.setLocales(new LocaleList(locale));
        final Context context = InstrumentationRegistry.getTargetContext().
                createConfigurationContext(overrideConfig);

        final SuggestionSpan span = new SuggestionSpan(context, suggestions,
                SuggestionSpan.FLAG_AUTO_CORRECTION);

        assertEquals(locale, span.getLocaleObject());
        assertArrayEquals(suggestions, span.getSuggestions());
        assertEquals(SuggestionSpan.FLAG_AUTO_CORRECTION, span.getFlags());
    }

    @Test
    public void testGetSuggestionSpans() {
        final String[] suggestions = new String[]{"suggestion1", "suggestion2"};
        final SuggestionSpan span = new SuggestionSpan(Locale.forLanguageTag("en"), suggestions,
                SuggestionSpan.FLAG_AUTO_CORRECTION);
        assertArrayEquals("Should return the correct suggestions array",
                suggestions, span.getSuggestions());

        final SuggestionSpan clonedSpan = cloneViaParcel(span);
        assertArrayEquals("Should (de)serialize suggestions",
                suggestions, clonedSpan.getSuggestions());
    }

    @Test
    public void testGetSuggestionSpans_emptySuggestions() {
        final String[] suggestions = new String[0];
        final SuggestionSpan span = new SuggestionSpan(Locale.forLanguageTag("en"), suggestions,
                SuggestionSpan.FLAG_AUTO_CORRECTION);
        assertArrayEquals("Span should return empty suggestion array",
                suggestions, span.getSuggestions());

        // also test parceling
        final SuggestionSpan clonedSpan = cloneViaParcel(span);
        assertArrayEquals("Should (de)serialize empty suggestions array",
                suggestions, clonedSpan.getSuggestions());
    }

    @Test
    public void testGetSuggestionSpans_suggestionsWithNullValue() {
        final String[] suggestions = new String[]{"suggestion", null};
        final SuggestionSpan span = new SuggestionSpan(Locale.forLanguageTag("en"), suggestions,
                SuggestionSpan.FLAG_AUTO_CORRECTION);
        assertArrayEquals("Should accept and return null suggestions",
                suggestions, span.getSuggestions());

        final SuggestionSpan clonedSpan = cloneViaParcel(span);
        assertArrayEquals("Should (de)serialize null in suggestions array",
                suggestions, clonedSpan.getSuggestions());
    }

    @Test
    public void testGetFlags() {
        final String[] anySuggestions = new String[0];
        final int flag = SuggestionSpan.FLAG_AUTO_CORRECTION;
        SuggestionSpan span = new SuggestionSpan(Locale.forLanguageTag("en"), anySuggestions, flag);

        assertEquals("Should return the flag passed in constructor",
                flag, span.getFlags());

        final SuggestionSpan clonedSpan = cloneViaParcel(span);
        assertEquals("Should (de)serialize flags", flag, clonedSpan.getFlags());
    }

    @Test
    public void testEquals_returnsTrueForDeserializedInstances() {
        final SuggestionSpan span1 = new SuggestionSpan(null, Locale.forLanguageTag("en"),
                new String[0], SuggestionSpan.FLAG_AUTO_CORRECTION, SuggestionSpan.class);
        final SuggestionSpan span2 = cloneViaParcel(span1);

        assertTrue("(De)serialized instances should be equal", span1.equals(span2));
    }

    @Test
    public void testEquals_returnsTrueIfTheFlagsAreDifferent() {
        final SuggestionSpan span1 = new SuggestionSpan(null, Locale.forLanguageTag("en"),
                new String[0], SuggestionSpan.FLAG_AUTO_CORRECTION, SuggestionSpan.class);
        final SuggestionSpan span2 = cloneViaParcel(span1);
        span2.setFlags(SuggestionSpan.FLAG_EASY_CORRECT);

        assertEquals("Should return the flag passed in set function",
                SuggestionSpan.FLAG_EASY_CORRECT, span2.getFlags());

        assertTrue("Instances with different flags should be equal", span1.equals(span2));
    }

    @Test
    public void testEquals_returnsFalseIfCreationTimeIsNotSame() {
        final Locale anyLocale = Locale.forLanguageTag("en");
        final String[] anySuggestions = new String[0];
        final int anyFlags = SuggestionSpan.FLAG_AUTO_CORRECTION;
        final Class anyClass = SuggestionSpan.class;

        final SuggestionSpan span1 = new SuggestionSpan(null, anyLocale, anySuggestions, anyFlags,
                anyClass);
        try {
            // let some time pass before constructing the other span
            Thread.sleep(2);
        } catch (InterruptedException e) {
            // ignore
        }
        final SuggestionSpan span2 = new SuggestionSpan(null, anyLocale, anySuggestions, anyFlags,
                anyClass);

        assertFalse("Instances created at different time should not be equal", span2.equals(span1));
    }

    /**
     * @param locale a {@link Locale} object.
     * @return A well-formed BCP 47 language tag representation.
     */
    @Nullable
    private Locale toWellFormedLocale(@Nullable final Locale locale) {
        if (locale == null) {
            return null;
        }
        // Drop all the malformed data.
        return Locale.forLanguageTag(locale.toLanguageTag());
    }

    @NonNull
    private String getNonNullLocaleString(@Nullable final Locale original) {
        if (original == null) {
            return "";
        }
        return original.toString();
    }

    private void verifyGetLocaleObject(final Locale locale) {
        final SuggestionSpan span = new SuggestionSpan(locale, new String[0],
                SuggestionSpan.FLAG_AUTO_CORRECTION);
        // In the context of SuggestionSpan#getLocaleObject(), we do care only about subtags that
        // can be interpreted as LanguageTag.
        assertEquals(toWellFormedLocale(locale), span.getLocaleObject());
        assertEquals(getNonNullLocaleString(locale), span.getLocale());

        final SuggestionSpan cloned = cloneViaParcel(span);
        assertEquals(span, cloned);
        assertEquals(toWellFormedLocale(locale), cloned.getLocaleObject());
        assertEquals(getNonNullLocaleString(locale), cloned.getLocale());
    }

    @Test
    public void testGetLocaleObject() {
        verifyGetLocaleObject(Locale.forLanguageTag("en"));
        verifyGetLocaleObject(Locale.forLanguageTag("en-GB"));
        verifyGetLocaleObject(Locale.forLanguageTag("EN-GB"));
        verifyGetLocaleObject(Locale.forLanguageTag("en-gb"));
        verifyGetLocaleObject(Locale.forLanguageTag("En-gB"));
        verifyGetLocaleObject(Locale.forLanguageTag("und"));
        verifyGetLocaleObject(Locale.forLanguageTag("de-DE-u-co-phonebk"));
        verifyGetLocaleObject(Locale.forLanguageTag(""));
        verifyGetLocaleObject(null);
        verifyGetLocaleObject(new Locale(" an  ", " i n v a l i d ", "data"));
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
        // Roboto kerns between "P" and "."
        final SpannableString text = new SpannableString("P.");
        final float origLineWidth = textWidth(text);

        final String[] suggestions = new String[]{"suggestion1", "suggestion2"};
        final SuggestionSpan span = new SuggestionSpan(Locale.US, suggestions,
                SuggestionSpan.FLAG_AUTO_CORRECTION);
        // Put just the "P" in a suggestion span.
        text.setSpan(span, 0, 1, Spanned.SPAN_INCLUSIVE_INCLUSIVE);
        final float underlinedLineWidth = textWidth(text);
        assertEquals(origLineWidth, underlinedLineWidth, 0.0f);
    }

    @NonNull
    SuggestionSpan cloneViaParcel(@NonNull final SuggestionSpan original) {
        Parcel parcel = null;
        try {
            parcel = Parcel.obtain();
            original.writeToParcel(parcel, 0);
            parcel.setDataPosition(0);
            return new SuggestionSpan(parcel);
        } finally {
            if (parcel != null) {
                parcel.recycle();
            }
        }
    }
}
