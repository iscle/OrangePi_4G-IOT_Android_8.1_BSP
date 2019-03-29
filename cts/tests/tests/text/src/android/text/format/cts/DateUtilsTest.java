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

package android.text.format.cts;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import android.content.Context;
import android.support.test.InstrumentationRegistry;
import android.support.test.filters.SmallTest;
import android.support.test.runner.AndroidJUnit4;
import android.text.format.DateUtils;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.Calendar;
import java.util.Date;
import java.util.Formatter;
import java.util.GregorianCalendar;
import java.util.Locale;
import java.util.TimeZone;

@SmallTest
@RunWith(AndroidJUnit4.class)
public class DateUtilsTest {
    private long mBaseTime;
    private Context mContext;

    @Before
    public void setup() {
        mContext = InstrumentationRegistry.getTargetContext();
        TimeZone.setDefault(TimeZone.getTimeZone("GMT"));
        mBaseTime = System.currentTimeMillis();
    }

    @Test
    public void testGetDayOfWeekString() {
        if (!LocaleUtils.isCurrentLocale(mContext, Locale.US)) {
            return;
        }

        assertEquals("Sunday",
                DateUtils.getDayOfWeekString(Calendar.SUNDAY, DateUtils.LENGTH_LONG));
        assertEquals("Sun",
                DateUtils.getDayOfWeekString(Calendar.SUNDAY, DateUtils.LENGTH_MEDIUM));
        assertEquals("Sun",
                DateUtils.getDayOfWeekString(Calendar.SUNDAY, DateUtils.LENGTH_SHORT));
        assertEquals("Sun",
                DateUtils.getDayOfWeekString(Calendar.SUNDAY, DateUtils.LENGTH_SHORTER));
        assertEquals("S",
                DateUtils.getDayOfWeekString(Calendar.SUNDAY, DateUtils.LENGTH_SHORTEST));
        // Other abbrev
        assertEquals("Sun",
                DateUtils.getDayOfWeekString(Calendar.SUNDAY, 60));
    }

    @Test
    public void testGetMonthString() {
        if (!LocaleUtils.isCurrentLocale(mContext, Locale.US)) {
            return;
        }
        assertEquals("January", DateUtils.getMonthString(Calendar.JANUARY, DateUtils.LENGTH_LONG));
        assertEquals("Jan",
                DateUtils.getMonthString(Calendar.JANUARY, DateUtils.LENGTH_MEDIUM));
        assertEquals("Jan", DateUtils.getMonthString(Calendar.JANUARY, DateUtils.LENGTH_SHORT));
        assertEquals("Jan",
                DateUtils.getMonthString(Calendar.JANUARY, DateUtils.LENGTH_SHORTER));
        assertEquals("J",
                DateUtils.getMonthString(Calendar.JANUARY, DateUtils.LENGTH_SHORTEST));
        // Other abbrev
        assertEquals("Jan", DateUtils.getMonthString(Calendar.JANUARY, 60));
    }

    @Test
    public void testGetAMPMString() {
        if (!LocaleUtils.isCurrentLocale(mContext, Locale.US)) {
            return;
        }
        assertEquals("AM", DateUtils.getAMPMString(Calendar.AM));
        assertEquals("PM", DateUtils.getAMPMString(Calendar.PM));
    }

    // This is to test the mapping between DateUtils' public API and
    // libcore/icu4c's implementation. More tests, in different locales, are
    // in libcore's CTS tests.
    @Test
    public void test_getRelativeTimeSpanString() {
        if (!LocaleUtils.isCurrentLocale(mContext, Locale.US)) {
            return;
        }

        final long ONE_SECOND_IN_MS = 1000;
        assertEquals("0 minutes ago",
                DateUtils.getRelativeTimeSpanString(mBaseTime - ONE_SECOND_IN_MS));
        assertEquals("In 0 minutes",
                DateUtils.getRelativeTimeSpanString(mBaseTime + ONE_SECOND_IN_MS));

        final long ONE_MINUTE_IN_MS = 60 * ONE_SECOND_IN_MS;
        assertEquals("1 minute ago", DateUtils.getRelativeTimeSpanString(0, ONE_MINUTE_IN_MS,
                DateUtils.MINUTE_IN_MILLIS));
        assertEquals("In 1 minute", DateUtils.getRelativeTimeSpanString(ONE_MINUTE_IN_MS, 0,
                DateUtils.MINUTE_IN_MILLIS));

        final long ONE_HOUR_IN_MS = 60 * 60 * 1000;
        final long TWO_HOURS_IN_MS = 2 * ONE_HOUR_IN_MS;
        assertEquals("2 hours ago", DateUtils.getRelativeTimeSpanString(mBaseTime - TWO_HOURS_IN_MS,
                mBaseTime, DateUtils.MINUTE_IN_MILLIS, DateUtils.FORMAT_NUMERIC_DATE));
        assertEquals("In 2 hours", DateUtils.getRelativeTimeSpanString(mBaseTime + TWO_HOURS_IN_MS,
                mBaseTime, DateUtils.MINUTE_IN_MILLIS, DateUtils.FORMAT_NUMERIC_DATE));
    }

    @Test
    public void test_getRelativeTimeSpanString_withContext() {
        if (!LocaleUtils.isCurrentLocale(mContext, Locale.US)) {
            return;
        }

        final GregorianCalendar cal = new GregorianCalendar();
        cal.setTimeInMillis(mBaseTime);
        cal.set(Calendar.HOUR_OF_DAY, 10);
        cal.set(Calendar.MINUTE, 0);
        final long today10am = cal.getTimeInMillis();

        final CharSequence withPrep = DateUtils.getRelativeTimeSpanString(mContext, today10am,
                true /* with preposition */);
        final CharSequence noPrep = DateUtils.getRelativeTimeSpanString(mContext, today10am,
                false /* no preposition */);
        assertEquals(noPrep, DateUtils.getRelativeTimeSpanString(mContext, today10am));

        if (android.text.format.DateFormat.is24HourFormat(mContext)) {
            assertEquals("at 10:00", withPrep);
            assertEquals("10:00", noPrep);
        } else {
            assertEquals("at 10:00 AM", withPrep);
            assertEquals("10:00 AM", noPrep);
        }
    }

    // Similar to test_getRelativeTimeSpanString(). The function here is to
    // test the mapping between DateUtils's public API and libcore/icu4c's
    // implementation. More tests, in different locales, are in libcore's
    // CTS tests.
    @Test
    public void test_getRelativeDateTimeString() {
        final long DAY_DURATION = 5 * 24 * 60 * 60 * 1000;
        assertNotNull(DateUtils.getRelativeDateTimeString(mContext, mBaseTime - DAY_DURATION,
                DateUtils.MINUTE_IN_MILLIS, DateUtils.DAY_IN_MILLIS, DateUtils.FORMAT_NUMERIC_DATE));
    }

    @Test
    public void test_formatElapsedTime() {
        if (!LocaleUtils.isCurrentLocale(mContext, Locale.US)) {
            return;
        }

        long MINUTES = 60;
        long HOURS = 60 * MINUTES;
        verifyFormatElapsedTime("02:01", 2 * MINUTES + 1);
        verifyFormatElapsedTime("3:02:01", 3 * HOURS + 2 * MINUTES + 1);
        // http://code.google.com/p/android/issues/detail?id=41401
        verifyFormatElapsedTime("123:02:01", 123 * HOURS + 2 * MINUTES + 1);
    }

    private void verifyFormatElapsedTime(String expected, long elapsedTime) {
        assertEquals(expected, DateUtils.formatElapsedTime(elapsedTime));
        StringBuilder sb = new StringBuilder();
        assertEquals(expected, DateUtils.formatElapsedTime(sb, elapsedTime));
        assertEquals(expected, sb.toString());
    }

    // This is just to exercise the wrapper that calls the libcore/icu4c implementation.
    // Full testing, in multiple locales, is in libcore's CTS tests.
    @Test
    public void testFormatDateRange() {
        if (!LocaleUtils.isCurrentLocale(mContext, Locale.US)) {
            return;
        }

        final Date date = new Date(109, 0, 19, 3, 30, 15);
        final long fixedTime = date.getTime();
        final long hourDuration = 2 * 60 * 60 * 1000;
        assertEquals("Monday", DateUtils.formatDateRange(mContext, fixedTime,
                fixedTime + hourDuration, DateUtils.FORMAT_SHOW_WEEKDAY));
    }

    @Test
    public void testFormatDateRange_withFormatter() {
        if (!LocaleUtils.isCurrentLocale(mContext, Locale.US)) {
            return;
        }

        final Date date = new Date(109, 0, 19, 3, 30, 15);
        final long fixedTime = date.getTime();
        final long hourDuration = 2 * 60 * 60 * 1000;
        final Formatter formatter = new Formatter();
        final Formatter result = DateUtils.formatDateRange(mContext, formatter, fixedTime,
                fixedTime + hourDuration, DateUtils.FORMAT_SHOW_WEEKDAY);
        assertEquals("Monday", result.toString());
        assertSame(result, formatter);
    }

    @Test
    public void testFormatDateRange_withFormatterAndTimezone() {
        if (!LocaleUtils.isCurrentLocale(mContext, Locale.US)) {
            return;
        }

        final Date date = new Date(109, 0, 19, 3, 30, 15);
        final long fixedTime = date.getTime();
        final long hourDuration = 2 * 60 * 60 * 1000;
        final Formatter formatter = new Formatter();
        final Formatter result = DateUtils.formatDateRange(mContext, formatter, fixedTime,
                fixedTime + hourDuration, DateUtils.FORMAT_SHOW_WEEKDAY, null /* local */);
        assertEquals("Monday", result.toString());
        assertSame(result, formatter);
    }

    @Test
    public void testFormatDateTime() {
        if (!LocaleUtils.isCurrentLocale(mContext, Locale.US)) {
            return;
        }

        final Date date = new Date(109, 0, 19, 3, 30, 15);
        final long fixedTime = date.getTime();
        assertEquals("Monday", DateUtils.formatDateTime(mContext, fixedTime,
                DateUtils.FORMAT_SHOW_WEEKDAY));
    }

    @Test
    public void testIsToday() {
        final long ONE_DAY_IN_MS = 24 * 60 * 60 * 1000;
        assertTrue(DateUtils.isToday(mBaseTime));
        assertFalse(DateUtils.isToday(mBaseTime - ONE_DAY_IN_MS));
    }

    @Test
    public void test_bug_7548161() {
        if (!LocaleUtils.isCurrentLocale(mContext, Locale.US)) {
            return;
        }

        long now = System.currentTimeMillis();
        long today = now;
        long tomorrow = now + DateUtils.DAY_IN_MILLIS;
        long yesterday = now - DateUtils.DAY_IN_MILLIS;
        assertEquals("Tomorrow", DateUtils.getRelativeTimeSpanString(tomorrow, now,
                DateUtils.DAY_IN_MILLIS, 0));
        assertEquals("Yesterday", DateUtils.getRelativeTimeSpanString(yesterday, now,
                DateUtils.DAY_IN_MILLIS, 0));
        assertEquals("Today", DateUtils.getRelativeTimeSpanString(today, now,
                DateUtils.DAY_IN_MILLIS, 0));
    }
}
