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
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import android.content.res.Configuration;
import android.content.res.Resources;
import android.support.test.filters.SmallTest;
import android.support.test.runner.AndroidJUnit4;
import android.text.format.Time;
import android.util.Log;
import android.util.TimeFormatException;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.TimeZone;

@SmallTest
@RunWith(AndroidJUnit4.class)
public class TimeTest {
    private static final String TAG = "TimeTest";
    private static List<Locale> sSystemLocales;

    private Locale originalLocale;

    @Before
    public void setup() {
        originalLocale = Locale.getDefault();

        maybeInitializeSystemLocales();
    }

    @After
    public void teardown() {
        // The Locale may be changed by tests. Revert to the original.
        changeJavaAndAndroidLocale(originalLocale, true /* force */);
    }

    @Test
    public void testConstructor() {
        Time time = new Time();
        new Time(Time.getCurrentTimezone());
        time.set(System.currentTimeMillis());
        Time anotherTime = new Time(time);
        verifyTime(time, anotherTime);
    }

    @Test
    public void testNormalize() {
        final int expectedMonth = 3;
        final int expectedDate = 1;
        Time time = new Time();
        // set date to March 32.
        time.year = 2008;
        time.month = 2;
        time.monthDay = 32;
        long timeMilliseconds = time.normalize(false);
        Calendar cal = Calendar.getInstance();
        cal.setTimeInMillis(timeMilliseconds);
        assertEquals(2008, cal.get(Calendar.YEAR));
        assertEquals(3, cal.get(Calendar.MONTH));
        assertEquals(1, cal.get(Calendar.DAY_OF_MONTH));
        assertEquals(expectedMonth, time.month);
        assertEquals(expectedDate, time.monthDay);

        // reset date to March 32.
        time.month = 2;
        time.monthDay = 32;
        assertEquals(timeMilliseconds, time.normalize(true));
        assertEquals(expectedMonth, time.month);
        assertEquals(expectedDate, time.monthDay);
    }

    @Test
    public void testSwitchTimezone() {
        String timeZone = "US/Pacific";
        String anotherTimeZone = "Asia/Chongqing";
        Time time = new Time(timeZone);
        assertEquals(timeZone, time.timezone);
        time.switchTimezone(anotherTimeZone);
        assertEquals(anotherTimeZone, time.timezone);
    }

    @Test
    public void testSet() {
        final int year = 2008;
        final int month = 5;
        final int date = 10;
        Time time = new Time();
        time.set(date, month, year);
        assertEquals(year, time.year);
        assertEquals(month, time.month);
        assertEquals(date, time.monthDay);

        Time anotherTime = new Time();
        anotherTime.set(time);
        verifyTime(time, anotherTime);
    }

    private void verifyTime(Time time, Time anotherTime) {
        assertEquals(time.timezone, anotherTime.timezone);
        assertEquals(time.allDay, anotherTime.allDay);
        assertEquals(time.second, anotherTime.second);
        assertEquals(time.minute, anotherTime.minute);
        assertEquals(time.hour, anotherTime.hour);
        assertEquals(time.monthDay, anotherTime.monthDay);
        assertEquals(time.month, anotherTime.month);
        assertEquals(time.year, anotherTime.year);
        assertEquals(time.weekDay, anotherTime.weekDay);
        assertEquals(time.yearDay, anotherTime.yearDay);
        assertEquals(time.isDst, anotherTime.isDst);
        assertEquals(time.gmtoff, anotherTime.gmtoff);
    }

    @Test
    public void testGetWeekNumber() {
        Time time = new Time();
        time.normalize(false);
        time.set(12, 10, 2008);
        assertEquals(45, time.getWeekNumber());

        assertEquals("20081112", time.format2445());

        assertEquals("2008-11-12", time.format3339(true));
        assertEquals("2008-11-12T00:00:00.000+00:00", time.format3339(false));

        Time anotherTime = new Time();
        assertTrue(anotherTime.parse3339("2008-11-12T00:00:00.000+00:00"));
        assertEquals(time.year, anotherTime.year);
        assertEquals(time.month, anotherTime.month);
        assertEquals(time.monthDay, anotherTime.monthDay);
        assertEquals(Time.TIMEZONE_UTC, anotherTime.timezone);

        try {
            anotherTime.parse3339("2008-111-12T00:00:00.000+00:00");
            fail("should throw exception");
        } catch (TimeFormatException e) {
            // expected
        }
    }

    @Test(expected=NullPointerException.class)
    public void testParseNull() {
        Time t = new Time();
        t.parse(null);
    }

    @Test(expected=NullPointerException.class)
    public void testParse3339Null() {
        Time t = new Time();
        t.parse3339(null);
    }

    // http://code.google.com/p/android/issues/detail?id=16002
    // We'd leak one JNI global reference each time parsing failed.
    // This would cause a crash when we filled the global reference table.
    @Test
    public void testBug16002() {
        Time t = new Time();
        for (int i = 0; i < 8192; ++i) {
            try {
                t.parse3339("xxx");
                fail();
            } catch (TimeFormatException expected) {
            }
        }
    }

    // http://code.google.com/p/android/issues/detail?id=22225
    // We'd leak one JNI global reference each time parsing failed.
    // This would cause a crash when we filled the global reference table.
    @Test
    public void testBug22225() {
        Time t = new Time();
        for (int i = 0; i < 8192; ++i) {
            try {
                t.parse("xxx");
                fail();
            } catch (TimeFormatException expected) {
            }
        }
    }

    @Test
    public void testIsEpoch() {
        Time time = new Time();
        assertTrue(Time.isEpoch(time));
        time.set(1, 2, 1970);
        time.normalize(false);
        assertFalse(Time.isEpoch(time));
    }

    @Test
    public void testAfterBefore() {
        Time a = new Time(Time.TIMEZONE_UTC);
        Time b = new Time("America/Los_Angeles");
        assertFalse(a.after(b));
        assertTrue(b.after(a));
        assertFalse(b.before(a));
        assertTrue(a.before(b));
    }

    // Below are tests merged from Google
    private static class DateTest {
        public int year1;
        public int month1;
        public int day1;
        public int hour1;
        public int minute1;
        public int dst1;

        public int delta;

        public int year2;
        public int month2;
        public int day2;
        public int hour2;
        public int minute2;
        public int dst2;

        public DateTest(int year1, int month1, int day1, int hour1, int minute1, int dst1,
                int delta, int year2, int month2, int day2, int hour2, int minute2,
                int dst2) {
            this.year1 = year1;
            this.month1 = month1;
            this.day1 = day1;
            this.hour1 = hour1;
            this.minute1 = minute1;
            this.dst1 = dst1;
            this.delta = delta;
            this.year2 = year2;
            this.month2 = month2;
            this.day2 = day2;
            this.hour2 = hour2;
            this.minute2 = minute2;
            this.dst2 = dst2;
        }

        public DateTest(int year1, int month1, int day1, int hour1, int minute1,
                int delta, int year2, int month2, int day2, int hour2, int minute2) {
            this.year1 = year1;
            this.month1 = month1;
            this.day1 = day1;
            this.hour1 = hour1;
            this.minute1 = minute1;
            this.dst1 = -1;
            this.delta = delta;
            this.year2 = year2;
            this.month2 = month2;
            this.day2 = day2;
            this.hour2 = hour2;
            this.minute2 = minute2;
            this.dst2 = -1;
        }
    }

    // These tests assume that DST changes on Nov 4, 2007 at 2am (to 1am).

    // The "offset" field in "dayTests" represents days.
    // Use normalize(true) with these tests to change the date by 1 day.
    private DateTest[] dayTests = {
            // The month numbers are 0-relative, so Jan=0, Feb=1,...Dec=11

            // Nov 4, 12am + 0 day = Nov 4, 12am
            // Nov 5, 12am + 0 day = Nov 5, 12am
            new DateTest(2007, 10, 4, 0, 0, 0, 2007, 10, 4, 0, 0),
            new DateTest(2007, 10, 5, 0, 0, 0, 2007, 10, 5, 0, 0),

            // Nov 3, 12am + 1 day = Nov 4, 12am
            // Nov 4, 12am + 1 day = Nov 5, 12am
            // Nov 5, 12am + 1 day = Nov 6, 12am
            new DateTest(2007, 10, 3, 0, 0, 1, 2007, 10, 4, 0, 0),
            new DateTest(2007, 10, 4, 0, 0, 1, 2007, 10, 5, 0, 0),
            new DateTest(2007, 10, 5, 0, 0, 1, 2007, 10, 6, 0, 0),

            // Nov 3, 1am + 1 day = Nov 4, 1am
            // Nov 4, 1am + 1 day = Nov 5, 1am
            // Nov 5, 1am + 1 day = Nov 6, 1am
            new DateTest(2007, 10, 3, 1, 0, 1, 2007, 10, 4, 1, 0),
            new DateTest(2007, 10, 4, 1, 0, 1, 2007, 10, 5, 1, 0),
            new DateTest(2007, 10, 5, 1, 0, 1, 2007, 10, 6, 1, 0),

            // Nov 3, 2am + 1 day = Nov 4, 2am
            // Nov 4, 2am + 1 day = Nov 5, 2am
            // Nov 5, 2am + 1 day = Nov 6, 2am
            new DateTest(2007, 10, 3, 2, 0, 1, 2007, 10, 4, 2, 0),
            new DateTest(2007, 10, 4, 2, 0, 1, 2007, 10, 5, 2, 0),
            new DateTest(2007, 10, 5, 2, 0, 1, 2007, 10, 6, 2, 0),
    };

    // The "offset" field in "minuteTests" represents minutes.
    // Use normalize(false) with these tests.
    private DateTest[] minuteTests = {
            // The month numbers are 0-relative, so Jan=0, Feb=1,...Dec=11

            // Nov 4, 12am + 0 minutes = Nov 4, 12am
            // Nov 5, 12am + 0 minutes = Nov 5, 12am
            new DateTest(2007, 10, 4, 0, 0, 0, 2007, 10, 4, 0, 0),
            new DateTest(2007, 10, 5, 0, 0, 0, 2007, 10, 5, 0, 0),

            // Nov 3, 12am + 60 minutes = Nov 3, 1am
            // Nov 4, 12am + 60 minutes = Nov 4, 1am
            // Nov 5, 12am + 60 minutes = Nov 5, 1am
            new DateTest(2007, 10, 3, 0, 0, 60, 2007, 10, 3, 1, 0),
            new DateTest(2007, 10, 4, 0, 0, 60, 2007, 10, 4, 1, 0),
            new DateTest(2007, 10, 5, 0, 0, 60, 2007, 10, 5, 1, 0),

            // Nov 3, 1am + 60 minutes = Nov 3, 2am
            // Nov 4, 1am (PDT) + 30 minutes = Nov 4, 1:30am (PDT)
            // Nov 4, 1am (PDT) + 60 minutes = Nov 4, 1am (PST)
            new DateTest(2007, 10, 3, 1, 0, 60, 2007, 10, 3, 2, 0),
            new DateTest(2007, 10, 4, 1, 0, 1, 30, 2007, 10, 4, 1, 30, 1),
            new DateTest(2007, 10, 4, 1, 0, 1, 60, 2007, 10, 4, 1, 0, 0),

            // Nov 4, 1:30am (PDT) + 15 minutes = Nov 4, 1:45am (PDT)
            // Nov 4, 1:30am (PDT) + 30 minutes = Nov 4, 1:00am (PST)
            // Nov 4, 1:30am (PDT) + 60 minutes = Nov 4, 1:30am (PST)
            new DateTest(2007, 10, 4, 1, 30, 1, 15, 2007, 10, 4, 1, 45, 1),
            new DateTest(2007, 10, 4, 1, 30, 1, 30, 2007, 10, 4, 1, 0, 0),
            new DateTest(2007, 10, 4, 1, 30, 1, 60, 2007, 10, 4, 1, 30, 0),

            // Nov 4, 1:30am (PST) + 15 minutes = Nov 4, 1:45am (PST)
            // Nov 4, 1:30am (PST) + 30 minutes = Nov 4, 2:00am (PST)
            // Nov 5, 1am + 60 minutes = Nov 5, 2am
            new DateTest(2007, 10, 4, 1, 30, 0, 15, 2007, 10, 4, 1, 45, 0),
            new DateTest(2007, 10, 4, 1, 30, 0, 30, 2007, 10, 4, 2, 0, 0),
            new DateTest(2007, 10, 5, 1, 0, 60, 2007, 10, 5, 2, 0),

            // Nov 3, 2am + 60 minutes = Nov 3, 3am
            // Nov 4, 2am + 30 minutes = Nov 4, 2:30am
            // Nov 4, 2am + 60 minutes = Nov 4, 3am
            // Nov 5, 2am + 60 minutes = Nov 5, 3am
            new DateTest(2007, 10, 3, 2, 0, 60, 2007, 10, 3, 3, 0),
            new DateTest(2007, 10, 4, 2, 0, 30, 2007, 10, 4, 2, 30),
            new DateTest(2007, 10, 4, 2, 0, 60, 2007, 10, 4, 3, 0),
            new DateTest(2007, 10, 5, 2, 0, 60, 2007, 10, 5, 3, 0),
    };

    @Test
    public void testNormalize1() {
        String tz = "America/Los_Angeles";
        Time local = new Time(tz);

        int len = dayTests.length;
        for (int index = 0; index < len; index++) {
            DateTest test = dayTests[index];
            local.set(0, test.minute1, test.hour1, test.day1, test.month1, test.year1);
            // call normalize() to make sure that isDst is set
            local.normalize(false /* use isDst */);
            local.monthDay += test.delta;
            local.normalize(true /* ignore isDst */);

            Time expected = new Time(tz);
            Fields.setDateTime(expected, test.year2, test.month2, test.day2, test.hour2,
                    test.minute2, 0);
            Fields.verifyTimeEquals("day test index " + index + ", normalize():",
                    Fields.MAIN_DATE_TIME, expected, local);

            local.set(0, test.minute1, test.hour1, test.day1, test.month1, test.year1);
            // call normalize() to make sure that isDst is set
            local.normalize(false /* use isDst */);
            local.monthDay += test.delta;
            long millis = local.toMillis(true /* ignore isDst */);
            local.set(millis);

            expected = new Time(tz);
            Fields.setDateTime(expected, test.year2, test.month2, test.day2, test.hour2,
                    test.minute2, 0);
            Fields.verifyTimeEquals("day test index " + index + ", toMillis():",
                    Fields.MAIN_DATE_TIME, expected, local);
        }

        len = minuteTests.length;
        for (int index = 0; index < len; index++) {
            DateTest test = minuteTests[index];
            local.set(0, test.minute1, test.hour1, test.day1, test.month1, test.year1);
            local.isDst = test.dst1;
            // call normalize() to make sure that isDst is set
            local.normalize(false /* use isDst */);
            if (test.dst2 == -1) test.dst2 = local.isDst;
            local.minute += test.delta;
            local.normalize(false /* use isDst */);

            Time expected = new Time(tz);
            Fields.setDateTime(expected, test.year2, test.month2, test.day2, test.hour2,
                    test.minute2, 0);
            Fields.setDst(expected, test.dst2 /* isDst */, PstPdt.getUtcOffsetSeconds(test.dst2));
            Fields.verifyTimeEquals("minute test index " + index + ", normalize():",
                    Fields.MAIN_DATE_TIME | Fields.DST_FIELDS, expected, local);

            local.set(0, test.minute1, test.hour1, test.day1, test.month1, test.year1);
            local.isDst = test.dst1;
            // call normalize() to make sure that isDst is set
            local.normalize(false /* use isDst */);
            if (test.dst2 == -1) test.dst2 = local.isDst;
            local.minute += test.delta;
            long millis = local.toMillis(false /* use isDst */);
            local.set(millis);

            expected = new Time(tz);
            Fields.setDateTime(expected, test.year2, test.month2, test.day2, test.hour2,
                    test.minute2, 0);
            Fields.setDst(expected, test.dst2 /* isDst */, PstPdt.getUtcOffsetSeconds(test.dst2));
            Fields.verifyTimeEquals("minute test index " + index + ", toMillis():",
                    Fields.MAIN_DATE_TIME | Fields.DST_FIELDS, expected, local);
        }
    }

    @Test
    public void testSwitchTimezone_simpleUtc() {
        String originalTz = Time.TIMEZONE_UTC;
        Time t = new Time(originalTz);
        Fields.set(t, 2006, 9, 5, 12, 0, 0, -1 /* isDst */, 0, 0, 0);

        String newTz = "America/Los_Angeles";
        t.switchTimezone(newTz);

        Time expected1 = new Time(newTz);
        Fields.set(expected1, 2006, 9, 5, 5, 0, 0, 1 /* isDst */, -25200, 277, 4);
        Fields.verifyTimeEquals(expected1, t);

        t.switchTimezone(originalTz);

        Time expected2 = new Time(originalTz);
        Fields.set(expected2, 2006, 9, 5, 12, 0, 0, 0 /* isDst */, 0, 277, 4);
        Fields.verifyTimeEquals(expected2, t);
    }

    @Test
    public void testSwitchTimezone_standardToStandardTime() {
        String zone1 = "Europe/London";
        String zone2 = "America/Los_Angeles";

        // A time unambiguously in standard time in both zones.
        Time t = new Time(zone1);
        Fields.set(t, 2007, 2, 10, 12, 0, 0, -1 /* isDst */, 0, 0, 0);

        t.switchTimezone(zone2);

        Time expected1 = new Time(zone2);
        Fields.set(expected1, 2007, 2, 10, 4, 0, 0, 0 /* isDst */, -28800, 68, 6);
        Fields.verifyTimeEquals(expected1, t);

        t.switchTimezone(zone1);

        Time expected2 = new Time(zone1);
        Fields.set(expected2, 2007, 2, 10, 12, 0, 0, 0 /* isDst */, 0, 68, 6);
        Fields.verifyTimeEquals(expected2, t);
    }

    @Test
    public void testSwitchTimezone_dstToDstTime() {
        String zone1 = "Europe/London";
        String zone2 = "America/Los_Angeles";

        // A time unambiguously in DST in both zones.
        Time t = new Time(zone1);
        Fields.set(t, 2007, 2, 26, 12, 0, 0, -1 /* isDst */, 0, 0, 0);

        t.switchTimezone(zone2);

        Time expected1 = new Time(zone2);
        Fields.set(expected1, 2007, 2, 26, 4, 0, 0, 1 /* isDst */, -25200, 84, 1);
        Fields.verifyTimeEquals(expected1, t);

        t.switchTimezone(zone1);

        Time expected2 = new Time(zone1);
        Fields.set(expected2, 2007, 2, 26, 12, 0, 0, 1 /* isDst */, 3600, 84, 1);
        Fields.verifyTimeEquals(expected2, t);
    }

    @Test
    public void testSwitchTimezone_standardToDstTime() {
        String zone1 = "Europe/London";
        String zone2 = "America/Los_Angeles";

        // A time that is in standard time in zone1, but in DST in zone2.
        Time t = new Time(zone1);
        Fields.set(t, 2007, 2, 24, 12, 0, 0, -1 /* isDst */, 0, 0, 0);

        t.switchTimezone(zone2);

        Time expected1 = new Time(zone2);
        Fields.set(expected1, 2007, 2, 24, 5, 0, 0, 1 /* isDst */, -25200, 82, 6);
        Fields.verifyTimeEquals(expected1, t);

        t.switchTimezone(zone1);

        Time expected2 = new Time(zone1);
        Fields.set(expected2, 2007, 2, 24, 12, 0, 0, 0 /* isDst */, 0, 82, 6);
        Fields.verifyTimeEquals(expected2, t);
    }

    @Test
    public void testSwitchTimezone_sourceDateInvalid() {
        String zone1 = "Europe/London";
        String zone2 = "America/Los_Angeles";

        // A source wall time known not to normalize because it doesn't "exist" locally.
        Time t = new Time(zone1);
        Fields.set(t, 2007, 2, 25, 1, 30, 0, -1 /* isDst */, 0, 0, 0);
        assertEquals(-1, t.toMillis(false));

        t.switchTimezone(zone2);

        // It is assumed a sad trombone noise is also emitted from the device at this point.
        // This illustrates why using -1 to indicate a problem, when -1 is in range, is a poor idea.
        Time expected1 = new Time(zone2);
        Fields.set(expected1, 1969, 11, 31, 15, 59, 59, 0 /* isDst */, -28800, 364, 3);
        Fields.verifyTimeEquals(expected1, t);
    }

    @Test
    public void testSwitchTimezone_dstToStandardTime() {
        String zone1 = "America/Los_Angeles";
        String zone2 = "Europe/London";

        // A time that is in DST in zone1, but in standard in zone2.
        Time t = new Time(zone1);
        Fields.set(t, 2007, 2, 12, 12, 0, 0, -1 /* isDst */, 0, 0, 0);

        t.switchTimezone(zone2);

        Time expected1 = new Time(zone2);
        Fields.set(expected1, 2007, 2, 12, 19, 0, 0, 0 /* isDst */, 0, 70, 1);
        Fields.verifyTimeEquals(expected1, t);

        t.switchTimezone(zone1);

        Time expected2 = new Time(zone1);
        Fields.set(expected2, 2007, 2, 12, 12, 0, 0, 1 /* isDst */, -25200, 70, 1);
        Fields.verifyTimeEquals(expected2, t);
    }

    @Test
    public void testCtor() {
        String tz = Time.TIMEZONE_UTC;
        Time t = new Time(tz);
        assertEquals(tz, t.timezone);

        Time expected = new Time(tz);
        Fields.set(expected, 1970, 0, 1, 0, 0, 0, -1 /* isDst */, 0, 0, 0);
        Fields.verifyTimeEquals(expected, t);
    }

    @Test
    public void testGetActualMaximum() {
        Time t = new Time(Time.TIMEZONE_UTC);
        assertEquals(59, t.getActualMaximum(Time.SECOND));
        assertEquals(59, t.getActualMaximum(Time.MINUTE));
        assertEquals(23, t.getActualMaximum(Time.HOUR));
        assertEquals(11, t.getActualMaximum(Time.MONTH));
        assertEquals(2037, t.getActualMaximum(Time.YEAR));
        assertEquals(6, t.getActualMaximum(Time.WEEK_DAY));
        assertEquals(364, t.getActualMaximum(Time.YEAR_DAY));
        t.year = 2000;
        assertEquals(365, t.getActualMaximum(Time.YEAR_DAY));

        try {
            t.getActualMaximum(Time.WEEK_NUM);
            fail("should throw runtime exception");
        } catch (Exception e) {
            // expected
        }
        final int noExistField = -1;
        try {
            t.getActualMaximum(noExistField);
        } catch (Exception e) {
            // expected
        }

        t.year = 2001;
        final int[] DAYS_PER_MONTH = {
                31, 28, 31, 30, 31, 30, 31, 31, 30, 31, 30, 31
        };
        verifyMonth(t, DAYS_PER_MONTH);

        t.year = 2000;
        DAYS_PER_MONTH[1] = 29;
        verifyMonth(t, DAYS_PER_MONTH);
    }

    private void verifyMonth(Time t, final int[] DAYS_PER_MONTH) {
        for (int i = 0; i < t.getActualMaximum(Time.MONTH); i++) {
            t.month = i;
            assertEquals(DAYS_PER_MONTH[i], t.getActualMaximum(Time.MONTH_DAY));
        }
    }

    @Test
    public void testClear0() {
        Time t = new Time(Time.getCurrentTimezone());
        t.clear(Time.TIMEZONE_UTC);
        assertEquals(Time.TIMEZONE_UTC, t.timezone);
        assertFalse(t.allDay);
        assertEquals(0, t.second);
        assertEquals(0, t.minute);
        assertEquals(0, t.hour);
        assertEquals(0, t.monthDay);
        assertEquals(0, t.month);
        assertEquals(0, t.year);
        assertEquals(0, t.weekDay);
        assertEquals(0, t.yearDay);
        assertEquals(0, t.gmtoff);
        assertEquals(-1, t.isDst);
    }

    @Test
    public void testClear() {
        Time t = new Time("America/Los_Angeles");
        Fields.set(t, 1, 2, 3, 4, 5, 6, 7 /* isDst */, 8, 9, 10);

        t.clear(Time.TIMEZONE_UTC);

        Time expected = new Time(Time.TIMEZONE_UTC);
        Fields.set(expected, 0, 0, 0, 0, 0, 0, -1 /* isDst */, 0, 0, 0);
        Fields.verifyTimeEquals(expected, t);
    }

    @Test
    public void testCompare() {
        String timezone = "America/New_York";
        int[] aDateTimeFields = new int[] { 2005, 2, 3, 4, 5, 6 };

        Time a = new Time(timezone);
        Fields.setDateTime(a, aDateTimeFields);
        Fields.setDst(a, 7, 8);
        Fields.setDerivedDateTime(a, 9, 10);

        int[] bDateTimeFields = new int[aDateTimeFields.length];
        System.arraycopy(aDateTimeFields, 0, bDateTimeFields, 0, aDateTimeFields.length);
        Time b = new Time(timezone);
        Fields.setDateTime(b, bDateTimeFields);
        Fields.setDst(b, 7, 8);
        Fields.setDerivedDateTime(b, 9, 10);

        // Confirm timezone behavior: When timezones differ the result depends on the millis time.
        // This means that all date/time and the isDst field can impact compare() when timezones
        // are different. The result is always -1, 0 or 1.

        // East of New York. Millis goes down for a given wall time.
        b.timezone = "Europe/London";
        assertEquals(1, Time.compare(a, b));
        assertEquals(-1, Time.compare(b, a));

        // West of New York. Millis goes up for a given wall time.
        b.timezone = "America/Los_Angeles";
        assertEquals(-1, Time.compare(a, b));
        assertEquals(1, Time.compare(b, a));

        b.timezone = timezone;
        assertEquals(0, Time.compare(a, b));
        assertEquals(0, Time.compare(b, a));

        // Now confirm behavior when timezones are the same: Only the date/time fields are checked
        // and the result depends on the difference between the fields and the order in which they
        // are checked.

        // Check date/time fields
        for (int i = 0; i < aDateTimeFields.length; i++) {
            bDateTimeFields[i] = 99999;
            Fields.setDateTime(b, bDateTimeFields);

            assertEquals(aDateTimeFields[i] - bDateTimeFields[i], Time.compare(a, b));
            assertEquals(bDateTimeFields[i] - aDateTimeFields[i], Time.compare(b, a));

            bDateTimeFields[i] = -99999;
            Fields.setDateTime(b, bDateTimeFields);

            assertEquals(aDateTimeFields[i] - bDateTimeFields[i], Time.compare(a, b));
            assertEquals(bDateTimeFields[i] - aDateTimeFields[i], Time.compare(b, a));

            bDateTimeFields[i] = aDateTimeFields[i];
            Fields.setDateTime(b, bDateTimeFields);

            assertEquals(0, Time.compare(a, b));
            assertEquals(0, Time.compare(b, a));
        }

        // Show that the derived fields have no effect on compare when timezones are the same.
        Fields.setDst(b, 999, 999);
        Fields.setDerivedDateTime(b, 999, 999);
        assertEquals(0, Time.compare(a, b));
        assertEquals(0, Time.compare(b, a));
    }

    @Test(expected=NullPointerException.class)
    public void testCompareNullSecond() {
        Time a = new Time(Time.TIMEZONE_UTC);
        Time.compare(a, null);
    }

    @Test(expected=NullPointerException.class)
    public void testCompareNullFirst() {
        Time a = new Time(Time.TIMEZONE_UTC);
        Time.compare(null, a);
    }

    @Test(expected=NullPointerException.class)
    public void testCompareNullBoth() {
        Time.compare(null, null);
    }

    @Test
    public void testCompare_invalidDatesAreEqualIfTimezoneDiffers() {
        String timezone = "America/New_York";
        // This date is outside of the valid set of dates that can be calculated so toMillis()
        // returns -1.
        int[] dateTimeFields = new int[] { 1, 2, 3, 4, 5, 6 };

        Time a = new Time(timezone);
        Fields.setDateTime(a, dateTimeFields);
        Fields.setDst(a, 7, 8);
        Fields.setDerivedDateTime(a, 9, 10);
        assertEquals(-1, a.toMillis(false));

        Time b = new Time(timezone);
        Fields.setDateTime(b, dateTimeFields);
        Fields.setDst(b, 11, 12);
        Fields.setDerivedDateTime(b, 13, 14);
        assertEquals(-1, b.toMillis(false));

        // DST and derived-date time fields are always ignored.
        assertEquals(0, Time.compare(a, b));

        // Set a different invalid set of date fields.
        Fields.setDateTime(b, new int[] { 6, 5, 4, 3, 2, 1 });
        assertEquals(-5, Time.compare(a, b));

        // Now change the timezone
        b.timezone = Time.TIMEZONE_UTC;

        // >:-(
        assertEquals(0, Time.compare(a, b));
    }

    @Test
    public void testFormat() {
        Time t = new Time(Time.TIMEZONE_UTC);
        String r = t.format("%Y%m%dT%H%M%S");
        assertEquals("19700101T000000", r);
    }

    @Test
    public void testFormat_null() {
        Time t = new Time(Time.TIMEZONE_UTC);
        assertEquals(t.format("%c"), t.format(null));
    }

    @Test
    public void testFormat_badPatterns() {
        Time t = new Time(Time.TIMEZONE_UTC);
        verifyFormatEquals(t, "%~Y", "~Y");
        verifyFormatEquals(t, "%", "%");
    }

    @Test
    public void testFormat_doesNotNormalize() {
        Time t = new Time(Time.TIMEZONE_UTC);
        Fields.set(t, 2005, 13, 32, -1, -1, -1, -2, -2, -2, -2);

        Time tCopy = new Time(t);
        Fields.verifyTimeEquals(t, tCopy);

        verifyFormatEquals(t, "%Y%m%dT%H%M%S", "20051432T-1-1-1");

        Fields.verifyTimeEquals(t, tCopy);
    }

    private static void verifyFormatEquals(Time t, String formatArg, String expected) {
        assertEquals(expected, t.format(formatArg));
    }

    @Test
    public void testFormat_tokensUkLocale() {
        if (!changeJavaAndAndroidLocale(Locale.UK, false /* force */)) {
            Log.w(TAG, "Skipping testFormat_tokensUkLocale: no assets found");
            return;
        }

        Time t = new Time("Europe/London");
        Fields.setDateTime(t, 2005, 5, 1, 12, 30, 15);

        // Prove the un-normalized fields are used.
        verifyFormatEquals(t, "%A", "Sunday");

        // Set fields like weekday.
        t.normalize(true);

        verifyFormatEquals(t, "%A", "Wednesday");
        verifyFormatEquals(t, "%a", "Wed");
        verifyFormatEquals(t, "%B", "June");
        verifyFormatEquals(t, "%b", "Jun");
        verifyFormatEquals(t, "%C", "20");
        verifyFormatEquals(t, "%c", "1 Jun 2005, 12:30:15");
        verifyFormatEquals(t, "%D", "06/01/05");
        verifyFormatEquals(t, "%d", "01");
        verifyFormatEquals(t, "%E", "E");
        verifyFormatEquals(t, "%e", " 1");
        verifyFormatEquals(t, "%F", "2005-06-01");
        verifyFormatEquals(t, "%G", "2005");
        verifyFormatEquals(t, "%g", "05");
        verifyFormatEquals(t, "%H", "12");
        verifyFormatEquals(t, "%h", "Jun");
        verifyFormatEquals(t, "%I", "12");
        verifyFormatEquals(t, "%j", "152");
        verifyFormatEquals(t, "%K", "K");
        verifyFormatEquals(t, "%k", "12");
        verifyFormatEquals(t, "%l", "12");
        verifyFormatEquals(t, "%M", "30");
        verifyFormatEquals(t, "%m", "06");
        verifyFormatEquals(t, "%n", "\n");
        verifyFormatEquals(t, "%O", "O");
        verifyFormatEquals(t, "%p", "pm");
        verifyFormatEquals(t, "%P", "pm");
        verifyFormatEquals(t, "%R", "12:30");
        verifyFormatEquals(t, "%r", "12:30:15 pm");
        verifyFormatEquals(t, "%S", "15");
        // The original C implementation uses the (native) system default TZ, not the timezone of
        // the Time to calculate this and was therefore not stable. This changed to use the Time's
        // timezone when the Time class was re-written in Java.
        verifyFormatEquals(t, "%s", "1117625415");
        verifyFormatEquals(t, "%T", "12:30:15");
        verifyFormatEquals(t, "%t", "\t");
        verifyFormatEquals(t, "%U", "22");
        verifyFormatEquals(t, "%u", "3");
        verifyFormatEquals(t, "%V", "22");
        verifyFormatEquals(t, "%v", " 1-Jun-2005");
        verifyFormatEquals(t, "%W", "22");
        verifyFormatEquals(t, "%w", "3");
        verifyFormatEquals(t, "%X", "12:30:15");
        verifyFormatEquals(t, "%x", "1 June 2005");
        verifyFormatEquals(t, "%y", "05");
        verifyFormatEquals(t, "%Y", "2005");
        verifyFormatEquals(t, "%Z", "BST");
        verifyFormatEquals(t, "%z", "+0100");
        verifyFormatEquals(t, "%+", "Wed Jun  1 12:30:15 BST 2005");
        verifyFormatEquals(t, "%%", "%");

        // Modifiers

        verifyFormatEquals(t, "%EC", "20");
        verifyFormatEquals(t, "%OC", "20");

        verifyFormatEquals(t, "%_+", "Wed Jun  1 12:30:15 BST 2005");
        verifyFormatEquals(t, "%-+", "Wed Jun  1 12:30:15 BST 2005");
        verifyFormatEquals(t, "%0+", "Wed Jun  1 12:30:15 BST 2005");
        verifyFormatEquals(t, "%^+", "Wed Jun  1 12:30:15 BST 2005");
        verifyFormatEquals(t, "%#+", "Wed Jun  1 12:30:15 BST 2005");

        verifyFormatEquals(t, "%_A", "Wednesday");
        verifyFormatEquals(t, "%-A", "Wednesday");
        verifyFormatEquals(t, "%0A", "Wednesday");
        verifyFormatEquals(t, "%^A", "WEDNESDAY");
        verifyFormatEquals(t, "%#A", "wEDNESDAY");

        verifyFormatEquals(t, "%_Y", "20 5");
        verifyFormatEquals(t, "%-Y", "205");
        verifyFormatEquals(t, "%0Y", "2005");
        verifyFormatEquals(t, "%^Y", "2005");
        verifyFormatEquals(t, "%#Y", "2005");

        verifyFormatEquals(t, "%_d", " 1");
        verifyFormatEquals(t, "%-d", "1");
        verifyFormatEquals(t, "%0d", "01");
        verifyFormatEquals(t, "%^d", "01");
        verifyFormatEquals(t, "%#d", "01");
    }

    @Test
    public void testFormat_tokensUsLocale() {
        if (!changeJavaAndAndroidLocale(Locale.US, false /* force */)) {
            Log.w(TAG, "Skipping testFormat_tokensUSLocale: no assets found");
            return;
        }

        Time t = new Time("America/New_York");
        Fields.setDateTime(t, 2005, 5, 1, 12, 30, 15);

        // Prove the un-normalized fields are used.
        verifyFormatEquals(t, "%A", "Sunday");

        // Set fields like weekday.
        t.normalize(true);

        verifyFormatEquals(t, "%A", "Wednesday");
        verifyFormatEquals(t, "%a", "Wed");
        verifyFormatEquals(t, "%B", "June");
        verifyFormatEquals(t, "%b", "Jun");
        verifyFormatEquals(t, "%C", "20");
        verifyFormatEquals(t, "%c", "Jun 1, 2005, 12:30:15 PM");
        verifyFormatEquals(t, "%D", "06/01/05");
        verifyFormatEquals(t, "%d", "01");
        verifyFormatEquals(t, "%E", "E");
        verifyFormatEquals(t, "%e", " 1");
        verifyFormatEquals(t, "%F", "2005-06-01");
        verifyFormatEquals(t, "%G", "2005");
        verifyFormatEquals(t, "%g", "05");
        verifyFormatEquals(t, "%H", "12");
        verifyFormatEquals(t, "%h", "Jun");
        verifyFormatEquals(t, "%I", "12");
        verifyFormatEquals(t, "%j", "152");
        verifyFormatEquals(t, "%K", "K");
        verifyFormatEquals(t, "%k", "12");
        verifyFormatEquals(t, "%l", "12");
        verifyFormatEquals(t, "%M", "30");
        verifyFormatEquals(t, "%m", "06");
        verifyFormatEquals(t, "%n", "\n");
        verifyFormatEquals(t, "%O", "O");
        verifyFormatEquals(t, "%p", "PM");
        verifyFormatEquals(t, "%P", "pm");
        verifyFormatEquals(t, "%R", "12:30");
        verifyFormatEquals(t, "%r", "12:30:15 PM");
        verifyFormatEquals(t, "%S", "15");
        // The original C implementation uses the (native) system default TZ, not the timezone of
        // the Time to calculate this and was therefore not stable. This changed to use the Time's
        // timezone when the Time class was re-written in Java.
        verifyFormatEquals(t, "%s", "1117643415");
        verifyFormatEquals(t, "%T", "12:30:15");
        verifyFormatEquals(t, "%t", "\t");
        verifyFormatEquals(t, "%U", "22");
        verifyFormatEquals(t, "%u", "3");
        verifyFormatEquals(t, "%V", "22");
        verifyFormatEquals(t, "%v", " 1-Jun-2005");
        verifyFormatEquals(t, "%W", "22");
        verifyFormatEquals(t, "%w", "3");
        verifyFormatEquals(t, "%X", "12:30:15 PM");
        verifyFormatEquals(t, "%x", "June 1, 2005");
        verifyFormatEquals(t, "%y", "05");
        verifyFormatEquals(t, "%Y", "2005");
        verifyFormatEquals(t, "%Z", "EDT");
        verifyFormatEquals(t, "%z", "-0400");
        verifyFormatEquals(t, "%+", "Wed Jun  1 12:30:15 EDT 2005");
        verifyFormatEquals(t, "%%", "%");

        // Modifiers

        verifyFormatEquals(t, "%EC", "20");
        verifyFormatEquals(t, "%OC", "20");

        verifyFormatEquals(t, "%_+", "Wed Jun  1 12:30:15 EDT 2005");
        verifyFormatEquals(t, "%-+", "Wed Jun  1 12:30:15 EDT 2005");
        verifyFormatEquals(t, "%0+", "Wed Jun  1 12:30:15 EDT 2005");
        verifyFormatEquals(t, "%^+", "Wed Jun  1 12:30:15 EDT 2005");
        verifyFormatEquals(t, "%#+", "Wed Jun  1 12:30:15 EDT 2005");

        verifyFormatEquals(t, "%_A", "Wednesday");
        verifyFormatEquals(t, "%-A", "Wednesday");
        verifyFormatEquals(t, "%0A", "Wednesday");
        verifyFormatEquals(t, "%^A", "WEDNESDAY");
        verifyFormatEquals(t, "%#A", "wEDNESDAY");

        verifyFormatEquals(t, "%_Y", "20 5");
        verifyFormatEquals(t, "%-Y", "205");
        verifyFormatEquals(t, "%0Y", "2005");
        verifyFormatEquals(t, "%^Y", "2005");
        verifyFormatEquals(t, "%#Y", "2005");

        verifyFormatEquals(t, "%_d", " 1");
        verifyFormatEquals(t, "%-d", "1");
        verifyFormatEquals(t, "%0d", "01");
        verifyFormatEquals(t, "%^d", "01");
        verifyFormatEquals(t, "%#d", "01");
    }

    @Test
    public void testFormat_tokensFranceLocale() {
        if (!changeJavaAndAndroidLocale(Locale.FRANCE, false /* force */)) {
            Log.w(TAG, "Skipping testFormat_tokensFranceLocale: no assets found");
            return;
        }

        Time t = new Time("Europe/Paris");
        Fields.setDateTime(t, 2005, 5, 1, 12, 30, 15);

        // Prove the un-normalized fields are used.
        verifyFormatEquals(t, "%A", "dimanche");

        // Set fields like weekday.
        t.normalize(true);

        verifyFormatEquals(t, "%A", "mercredi");
        verifyFormatEquals(t, "%a", "mer.");
        verifyFormatEquals(t, "%B", "juin");
        verifyFormatEquals(t, "%b", "juin");
        verifyFormatEquals(t, "%C", "20");
        verifyFormatEquals(t, "%c", "1 juin 2005 à 12:30:15");
        verifyFormatEquals(t, "%D", "06/01/05");
        verifyFormatEquals(t, "%d", "01");
        verifyFormatEquals(t, "%E", "E");
        verifyFormatEquals(t, "%e", " 1");
        verifyFormatEquals(t, "%F", "2005-06-01");
        verifyFormatEquals(t, "%G", "2005");
        verifyFormatEquals(t, "%g", "05");
        verifyFormatEquals(t, "%H", "12");
        verifyFormatEquals(t, "%h", "juin");
        verifyFormatEquals(t, "%I", "12");
        verifyFormatEquals(t, "%j", "152");
        verifyFormatEquals(t, "%K", "K");
        verifyFormatEquals(t, "%k", "12");
        verifyFormatEquals(t, "%l", "12");
        verifyFormatEquals(t, "%M", "30");
        verifyFormatEquals(t, "%m", "06");
        verifyFormatEquals(t, "%n", "\n");
        verifyFormatEquals(t, "%O", "O");
        verifyFormatEquals(t, "%p", "PM");
        verifyFormatEquals(t, "%P", "pm");
        verifyFormatEquals(t, "%R", "12:30");
        verifyFormatEquals(t, "%r", "12:30:15 PM");
        verifyFormatEquals(t, "%S", "15");
        // The original C implementation uses the (native) system default TZ, not the timezone of
        // the Time to calculate this and was therefore not stable. This changed to use the Time's
        // timezone when the Time class was re-written in Java.
        verifyFormatEquals(t, "%s", "1117621815");
        verifyFormatEquals(t, "%T", "12:30:15");
        verifyFormatEquals(t, "%t", "\t");
        verifyFormatEquals(t, "%U", "22");
        verifyFormatEquals(t, "%u", "3");
        verifyFormatEquals(t, "%V", "22");
        verifyFormatEquals(t, "%v", " 1-juin-2005");
        verifyFormatEquals(t, "%W", "22");
        verifyFormatEquals(t, "%w", "3");
        verifyFormatEquals(t, "%X", "12:30:15");
        verifyFormatEquals(t, "%x", "1 juin 2005");
        verifyFormatEquals(t, "%y", "05");
        verifyFormatEquals(t, "%Y", "2005");
        verifyFormatEquals(t, "%Z", "GMT+02:00");
        verifyFormatEquals(t, "%z", "+0200");
        verifyFormatEquals(t, "%+", "mer. juin  1 12:30:15 GMT+02:00 2005");
        verifyFormatEquals(t, "%%", "%");

        // Modifiers

        verifyFormatEquals(t, "%EC", "20");
        verifyFormatEquals(t, "%OC", "20");

        verifyFormatEquals(t, "%_+", "mer. juin  1 12:30:15 GMT+02:00 2005");
        verifyFormatEquals(t, "%-+", "mer. juin  1 12:30:15 GMT+02:00 2005");
        verifyFormatEquals(t, "%0+", "mer. juin  1 12:30:15 GMT+02:00 2005");
        verifyFormatEquals(t, "%^+", "mer. juin  1 12:30:15 GMT+02:00 2005");
        verifyFormatEquals(t, "%#+", "mer. juin  1 12:30:15 GMT+02:00 2005");

        verifyFormatEquals(t, "%_A", "mercredi");
        verifyFormatEquals(t, "%-A", "mercredi");
        verifyFormatEquals(t, "%0A", "mercredi");
        verifyFormatEquals(t, "%^A", "MERCREDI");
        verifyFormatEquals(t, "%#A", "MERCREDI");

        verifyFormatEquals(t, "%_Y", "20 5");
        verifyFormatEquals(t, "%-Y", "205");
        verifyFormatEquals(t, "%0Y", "2005");
        verifyFormatEquals(t, "%^Y", "2005");
        verifyFormatEquals(t, "%#Y", "2005");

        verifyFormatEquals(t, "%_d", " 1");
        verifyFormatEquals(t, "%-d", "1");
        verifyFormatEquals(t, "%0d", "01");
        verifyFormatEquals(t, "%^d", "01");
        verifyFormatEquals(t, "%#d", "01");
    }

    @Test
    public void testFormat_tokensJapanLocale() {
        if (!changeJavaAndAndroidLocale(Locale.JAPAN, false /* force */)) {
            Log.w(TAG, "Skipping testFormat_tokensJapanLocale: no assets found");
            return;
        }

        Time t = new Time("Asia/Tokyo");
        Fields.setDateTime(t, 2005, 5, 1, 12, 30, 15);

        // Prove the un-normalized fields are used.
        verifyFormatEquals(t, "%A", "日曜日");

        // Set fields like weekday.
        t.normalize(true);

        verifyFormatEquals(t, "%A", "水曜日");
        verifyFormatEquals(t, "%a", "水");
        verifyFormatEquals(t, "%B", "6月");
        verifyFormatEquals(t, "%b", "6月");
        verifyFormatEquals(t, "%C", "20");
        verifyFormatEquals(t, "%c", "2005/06/01 12:30:15");
        verifyFormatEquals(t, "%D", "06/01/05");
        verifyFormatEquals(t, "%d", "01");
        verifyFormatEquals(t, "%E", "E");
        verifyFormatEquals(t, "%e", " 1");
        verifyFormatEquals(t, "%F", "2005-06-01");
        verifyFormatEquals(t, "%G", "2005");
        verifyFormatEquals(t, "%g", "05");
        verifyFormatEquals(t, "%H", "12");
        verifyFormatEquals(t, "%h", "6月");
        verifyFormatEquals(t, "%I", "12");
        verifyFormatEquals(t, "%j", "152");
        verifyFormatEquals(t, "%k", "12");
        verifyFormatEquals(t, "%l", "12");
        verifyFormatEquals(t, "%M", "30");
        verifyFormatEquals(t, "%m", "06");
        verifyFormatEquals(t, "%n", "\n");
        verifyFormatEquals(t, "%O", "O");
        verifyFormatEquals(t, "%p", "午後");
        verifyFormatEquals(t, "%P", "午後");
        verifyFormatEquals(t, "%R", "12:30");
        verifyFormatEquals(t, "%r", "12:30:15 午後");
        verifyFormatEquals(t, "%S", "15");
        // The original C implementation uses the (native) system default TZ, not the timezone of
        // the Time to calculate this and was therefore not stable. This changed to use the Time's
        // timezone when the Time class was re-written in Java.
        verifyFormatEquals(t, "%s", "1117596615");
        verifyFormatEquals(t, "%T", "12:30:15");
        verifyFormatEquals(t, "%t", "\t");
        verifyFormatEquals(t, "%U", "22");
        verifyFormatEquals(t, "%u", "3");
        verifyFormatEquals(t, "%V", "22");
        verifyFormatEquals(t, "%v", " 1-6月-2005");
        verifyFormatEquals(t, "%W", "22");
        verifyFormatEquals(t, "%w", "3");
        verifyFormatEquals(t, "%X", "12:30:15");
        verifyFormatEquals(t, "%x", "2005年6月1日");
        verifyFormatEquals(t, "%y", "05");
        verifyFormatEquals(t, "%Y", "2005");
        verifyFormatEquals(t, "%Z", "JST");
        verifyFormatEquals(t, "%z", "+0900");
        verifyFormatEquals(t, "%+", "水 6月  1 12:30:15 JST 2005");
        verifyFormatEquals(t, "%%", "%");

        // Modifiers

        verifyFormatEquals(t, "%EC", "20");
        verifyFormatEquals(t, "%OC", "20");

        verifyFormatEquals(t, "%_+", "水 6月  1 12:30:15 JST 2005");
        verifyFormatEquals(t, "%-+", "水 6月  1 12:30:15 JST 2005");
        verifyFormatEquals(t, "%0+", "水 6月  1 12:30:15 JST 2005");
        verifyFormatEquals(t, "%^+", "水 6月  1 12:30:15 JST 2005");
        verifyFormatEquals(t, "%#+", "水 6月  1 12:30:15 JST 2005");

        verifyFormatEquals(t, "%_A", "水曜日");
        verifyFormatEquals(t, "%-A", "水曜日");
        verifyFormatEquals(t, "%0A", "水曜日");
        verifyFormatEquals(t, "%^A", "水曜日");
        verifyFormatEquals(t, "%#A", "水曜日");

        verifyFormatEquals(t, "%_Y", "20 5");
        verifyFormatEquals(t, "%-Y", "205");
        verifyFormatEquals(t, "%0Y", "2005");
        verifyFormatEquals(t, "%^Y", "2005");
        verifyFormatEquals(t, "%#Y", "2005");

        verifyFormatEquals(t, "%_d", " 1");
        verifyFormatEquals(t, "%-d", "1");
        verifyFormatEquals(t, "%0d", "01");
        verifyFormatEquals(t, "%^d", "01");
        verifyFormatEquals(t, "%#d", "01");
    }

    @Test
    public void testFormat2445() {
        Time t = new Time(Time.TIMEZONE_UTC);
        Fields.setDateTime(t, 2005, 5, 1, 12, 30, 15);

        // UTC behavior is to add a trailing Z.
        String expected = t.format("%Y%m%dT%H%M%SZ");
        assertEquals(expected, t.format2445());

        // Only UTC includes the Z.
        t.timezone = "America/Los_Angeles";
        expected = t.format("%Y%m%dT%H%M%S");
        assertEquals(expected, t.format2445());

        // There is odd behavior around negative values.
        Fields.setDateTime(t, 2005, -1, -1, -1, -1, -1);
        assertEquals("2005000 T0 0 0 ", t.format2445());
    }

    @Test
    public void testFormat2445_doesNotNormalize() {
        Time t = new Time(Time.TIMEZONE_UTC);
        Fields.set(t, 2005, 13, 32, 25, 61, 61, -2, -2, -2, -2);

        Time tCopy = new Time(t);
        Fields.verifyTimeEquals(t, tCopy);

        assertEquals("20051432T256161Z", t.format2445());
        Fields.verifyTimeEquals(t, tCopy);

        t.timezone = tCopy.timezone = "America/Los_Angeles";
        assertEquals("20051432T256161", t.format2445());
        Fields.verifyTimeEquals(t, tCopy);
    }

    @Test
    public void testToString() {
        Time t = new Time(Time.TIMEZONE_UTC);
        assertEquals("19700101T000000UTC(0,0,0,-1,0)", t.toString());

        t.timezone = "America/Los_Angeles";
        assertEquals("19700101T000000America/Los_Angeles(0,0,0,-1,28800)", t.toString());
    }

    @Test
    public void testToString_doesNotNormalize() {
        Time t = new Time(Time.TIMEZONE_UTC);
        Fields.set(t, 2005, 13, 32, -1, -1, -1, -2, -2, -2, -2);

        Time tCopy = new Time(t);
        Fields.verifyTimeEquals(t, tCopy);

        String r = t.toString();
        assertEquals("20051432T-1-1-1UTC(-2,-2,-2,-2,1141426739)", r);

        Fields.verifyTimeEquals(t, tCopy);
    }

    @Test
    public void testGetCurrentTimezone() {
        String r = Time.getCurrentTimezone();
        assertEquals(TimeZone.getDefault().getID(), r);
    }

    @Test
    public void testSetToNow() {
        Time t = new Time(Time.TIMEZONE_UTC);

        // Time works in seconds so all millis values have to be divided by 1000, otherwise
        // the greater accuracy of System.currentTimeMillis() causes the test to fail.

        long lowerBound = System.currentTimeMillis() / 1000;

        t.setToNow();

        long upperBound = System.currentTimeMillis() / 1000;

        long actual = t.toMillis(true /* ignore isDst */) / 1000;
        assertTrue(lowerBound <= actual && actual <= upperBound);
    }

    @Test
    public void testToMillis_utc() {
        Time t = new Time(Time.TIMEZONE_UTC);

        long winterTimeUtcMillis = 1167613323000L;

        Fields.set(t, 2007, 0, 1, 1, 2, 3, 0 /* isDst */, 1, 9, 9);
        long r = t.toMillis(true /* ignore isDst */);
        assertEquals(winterTimeUtcMillis, r);

        Fields.set(t, 2007, 0, 1, 1, 2, 3, 1 /* isDst */, 1, 9, 9);
        r = t.toMillis(true /* ignore isDst */);
        assertEquals(winterTimeUtcMillis, r);

        Fields.set(t, 2007, 0, 1, 1, 2, 3, -1 /* isDst */, 1, 9, 9);
        r = t.toMillis(true /* ignore isDst */);
        assertEquals(winterTimeUtcMillis, r);

        Fields.set(t, 2007, 0, 1, 1, 2, 3, 0 /* isDst */, 1, 9, 9);
        r = t.toMillis(false /* ignore isDst */);
        assertEquals(winterTimeUtcMillis, r);

        Fields.set(t, 2007, 0, 1, 1, 2, 3, 1 /* isDst */, 1, 9, 9);
        r = t.toMillis(false /* ignore isDst */);
        assertEquals(-1, r);

        Fields.set(t, 2007, 0, 1, 1, 2, 3, -1 /* isDst */, 1, 9, 9);
        r = t.toMillis(false /* ignore isDst */);
        assertEquals(winterTimeUtcMillis, r);

        long summerTimeUtcMillis = 1180659723000L;

        Fields.set(t, 2007, 5, 1, 1, 2, 3, 0 /* isDst */, 1, 9, 9);
        r = t.toMillis(true /* ignore isDst */);
        assertEquals(summerTimeUtcMillis, r);

        Fields.set(t, 2007, 5, 1, 1, 2, 3, 1 /* isDst */, 1, 9, 9);
        r = t.toMillis(true /* ignore isDst */);
        assertEquals(summerTimeUtcMillis, r);

        Fields.set(t, 2007, 5, 1, 1, 2, 3, -1 /* isDst */, 1, 9, 9);
        r = t.toMillis(true /* ignore isDst */);
        assertEquals(summerTimeUtcMillis, r);

        Fields.set(t, 2007, 5, 1, 1, 2, 3, 0 /* isDst */, 1, 9, 9);
        r = t.toMillis(false /* ignore isDst */);
        assertEquals(summerTimeUtcMillis, r);

        Fields.set(t, 2007, 5, 1, 1, 2, 3, 1 /* isDst */, 1, 9, 9);
        r = t.toMillis(false /* ignore isDst */);
        assertEquals(-1, r);

        Fields.set(t, 2007, 5, 1, 1, 2, 3, -1 /* isDst */, 1, 9, 9);
        r = t.toMillis(false /* ignore isDst */);
        assertEquals(summerTimeUtcMillis, r);
    }

    @Test
    public void testToMillis_dstTz() {
        Time t = new Time(PstPdt.ID);

        // A STD time
        long stdTimeUtcMillis = 1167613323000L;
        long stdTimeMillis = stdTimeUtcMillis - PstPdt.getUtcOffsetMillis(false);

        Fields.set(t, 2007, 0, 1, 1, 2, 3, 0 /* isDst */, 9, 9, 9);
        long r = t.toMillis(true /* ignore isDst */);
        assertEquals(stdTimeMillis, r);

        Fields.set(t, 2007, 0, 1, 1, 2, 3, 1 /* isDst */, 9, 9, 9);
        verifyToMillisResult(true, t, stdTimeMillis);

        Fields.set(t, 2007, 0, 1, 1, 2, 3, -1 /* isDst */, 9, 9, 9);
        verifyToMillisResult(true, t, stdTimeMillis);

        long dstToStdCorrectionMillis =
                PstPdt.getUtcOffsetMillis(false) - PstPdt.getUtcOffsetMillis(true);

        Fields.set(t, 2007, 0, 1, 1, 2, 3, 0 /* isDst */, 9, 9, 9);
        verifyToMillisResult(false, t, stdTimeMillis);

        Fields.set(t, 2007, 0, 1, 1, 2, 3, 1 /* isDst */, 9, 9, 9);
        verifyToMillisResult(false, t, stdTimeMillis + dstToStdCorrectionMillis);

        Fields.set(t, 2007, 0, 1, 1, 2, 3, -1 /* isDst */, 9, 9, 9);
        verifyToMillisResult(false, t, stdTimeMillis);

        // A DST time
        long dstTimeUtcMillis = 1180659723000L;
        long dstTimeMillis = dstTimeUtcMillis - PstPdt.getUtcOffsetMillis(true);

        Fields.set(t, 2007, 5, 1, 1, 2, 3, 0 /* isDst */, 9, 9, 9);
        verifyToMillisResult(true, t, dstTimeMillis);

        Fields.set(t, 2007, 5, 1, 1, 2, 3, 1 /* isDst */, 9, 9, 9);
        verifyToMillisResult(true, t, dstTimeMillis);

        Fields.set(t, 2007, 5, 1, 1, 2, 3, -1 /* isDst */, 9, 9, 9);
        verifyToMillisResult(true, t, dstTimeMillis);

        long stdToDstCorrectionMillis = -dstToStdCorrectionMillis;

        Fields.set(t, 2007, 5, 1, 1, 2, 3, 0 /* isDst */, 9, 9, 9);
        verifyToMillisResult(false, t, dstTimeMillis + stdToDstCorrectionMillis);

        Fields.set(t, 2007, 5, 1, 1, 2, 3, 1 /* isDst */, 9, 9, 9);
        verifyToMillisResult(false, t, dstTimeMillis);

        Fields.set(t, 2007, 5, 1, 1, 2, 3, -1 /* isDst */, 9, 9, 9);
        verifyToMillisResult(false, t, dstTimeMillis);
    }

    private static void verifyToMillisResult(boolean toMillisArgument, Time t,
            long expectedResult) {
        long r = t.toMillis(toMillisArgument /* ignore isDst */);
        assertEquals(expectedResult, r);
    }

    @Test
    public void testToMillis_doesNotNormalize() {
        Time t = new Time(Time.TIMEZONE_UTC);

        Fields.set(t, 2007, 13, 32, 25, 60, 60, -2 /* isDst */, Integer.MAX_VALUE, 367, 7);

        Time originalTime = new Time(t);
        Fields.verifyTimeEquals(t, originalTime);

        t.toMillis(true);
        Fields.verifyTimeEquals(originalTime, t);

        t.toMillis(false);
        Fields.verifyTimeEquals(originalTime, t);
    }

    @Test
    public void testToMillis_skippedTime() {
        // Tests behavior around a transition from STD to DST that introduces an hour of "skipped"
        // time from 01:00 to 01:59.
        String timezone = PstPdt.ID;
        long stdBaseTimeMillis = 1173607200000L;
        long dstBaseTimeMillis = 1173603600000L;

        // Try each minute from one minute before the skipped hour until one after.
        for (int i = -1; i <= 60; i++) {
            int minutesInMillis = i * 60000;
            int[] timeFields = new int[] { 2007, 2, 11, 2, i, 0, -999 /* not used */, 9, 9, 9 };

            Time time = new Time(timezone);

            // isDst = 0, toMillis(true)
            Fields.set(time, timeFields);
            time.isDst = 0;
            long expectedTimeMillis;
            if (i == -1) {
                expectedTimeMillis = stdBaseTimeMillis + minutesInMillis;
            } else if (i == 60) {
                expectedTimeMillis = dstBaseTimeMillis + minutesInMillis;
            } else {
                expectedTimeMillis = -1;
            }
            verifyToMillisResult(true, time, expectedTimeMillis);

            // isDst = 0, toMillis(false)
            Fields.set(time, timeFields);
            time.isDst = 0;
            expectedTimeMillis = stdBaseTimeMillis + minutesInMillis;
            verifyToMillisResult(false, time, expectedTimeMillis);

            // isDst = 1, toMillis(true)
            Fields.set(time, timeFields);
            time.isDst = 1;
            if (i == -1) {
                expectedTimeMillis = stdBaseTimeMillis + minutesInMillis;
            } else if (i == 60) {
                expectedTimeMillis = dstBaseTimeMillis + minutesInMillis;
            } else {
                expectedTimeMillis = -1;
            }
            verifyToMillisResult(true, time, expectedTimeMillis);

            // isDst = 1, toMillis(false)
            Fields.set(time, timeFields);
            time.isDst = 1;
            expectedTimeMillis = dstBaseTimeMillis + minutesInMillis;
            verifyToMillisResult(false, time, expectedTimeMillis);

            // isDst = -1, toMillis(true)
            Fields.set(time, timeFields);
            time.isDst = -1;

            if (i == -1) {
                expectedTimeMillis = stdBaseTimeMillis + minutesInMillis;
            } else if (i == 60) {
                expectedTimeMillis = dstBaseTimeMillis + minutesInMillis;
            } else {
                expectedTimeMillis = -1;
            }
            verifyToMillisResult(false, time, expectedTimeMillis);

            // isDst = -1, toMillis(false)
            Fields.set(time, timeFields);
            time.isDst = -1;
            verifyToMillisResult(false, time, expectedTimeMillis);
        }
    }

    @Test
    public void testToMillis_duplicateWallTime() {
        // 1:00 in standard / 2:00 in DST
        long timeBaseMillis = 1194163200000L;
        long dstCorrectionMillis = 3600000;

        // Try each minute from one minute before the duplicated hour until one after.
        for (int i = -1; i <= 60; i++) {
            int minutesInMillis = i * 60000;

            Time time = new Time(PstPdt.ID);
            int[] timeFields = new int[] { 2007, 10, 4, 1, i, 0, -999 /* not used */, 9, 9, 9};

            // isDst = 0, toMillis(true)
            Fields.set(time, timeFields);
            time.isDst = 0;
            long timeMillis = time.toMillis(true);
            if (i == -1) {
                assertEquals("i = " + i, timeBaseMillis + minutesInMillis, timeMillis);
            } else if (i == 60) {
                assertEquals("i = " + i, timeBaseMillis + minutesInMillis + dstCorrectionMillis,
                        timeMillis);
            } else {
                // When ignoreDst the choice between DST and STD is arbitrary when both are
                // possible.
                assertTrue("i = " + i, timeMillis == timeBaseMillis + minutesInMillis
                        || timeMillis == timeBaseMillis + minutesInMillis + dstCorrectionMillis);
            }

            // isDst = 0, toMillis(false)
            Fields.set(time, timeFields);
            time.isDst = 0;
            verifyToMillisResult(false, time,
                    timeBaseMillis + minutesInMillis + dstCorrectionMillis);

            // isDst = 1, toMillis(true)
            Fields.set(time, timeFields);
            time.isDst = 1;
            if (i == -1) {
                assertEquals("i = " + i, timeBaseMillis + minutesInMillis, timeMillis);
            } else if (i == 60) {
                assertEquals("i = " + i, timeBaseMillis + minutesInMillis + dstCorrectionMillis,
                        timeMillis);
            } else {
                // When ignoreDst or isDst == -1 the choice between DST and STD is arbitrary when
                // both are possible.
                assertTrue("i = " + i, timeMillis == timeBaseMillis + minutesInMillis
                        || timeMillis == timeBaseMillis + minutesInMillis + dstCorrectionMillis);
            }

            // isDst = 1, toMillis(false)
            Fields.set(time, timeFields);
            time.isDst = 1;
            verifyToMillisResult(false, time, timeBaseMillis + minutesInMillis);

            // isDst = -1, toMillis(true)
            Fields.set(time, timeFields);
            time.isDst = -1;
            timeMillis = time.toMillis(true);
            if (i == -1) {
                assertEquals("i = " + i, timeBaseMillis + minutesInMillis, timeMillis);
            } else if (i == 60) {
                assertEquals("i = " + i, timeBaseMillis + minutesInMillis + dstCorrectionMillis,
                        timeMillis);
            } else {
                // When ignoreDst or isDst == -1 the choice between DST and STD is arbitrary when
                // both are possible.
                assertTrue("i = " + i, timeMillis == timeBaseMillis + minutesInMillis
                        || timeMillis == timeBaseMillis + minutesInMillis + dstCorrectionMillis);
            }

            // isDst = -1, toMillis(false)
            Fields.set(time, timeFields);
            time.isDst = -1;
            timeMillis = time.toMillis(false);
            if (i == -1) {
                assertEquals("i = " + i, timeBaseMillis + minutesInMillis, timeMillis);
            } else if (i == 60) {
                assertEquals("i = " + i, timeBaseMillis + minutesInMillis + dstCorrectionMillis,
                        timeMillis);
            } else {
                // When ignoreDst or isDst == -1 the choice between DST and STD is arbitrary when
                // both are possible.
                assertTrue("i = " + i, timeMillis == timeBaseMillis + minutesInMillis
                        || timeMillis == timeBaseMillis + minutesInMillis + dstCorrectionMillis);
            }
        }
    }

    @Test
    public void testToMillis_beforeTzRecords() {
        int[] timeFields = new int[] { 1900, 0, 1, 2, 3, 4, -999 /* not used */, 9, 9, 9 };
        verifyToMillisInvalid(timeFields, PstPdt.ID);
        verifyToMillisInvalid(timeFields, Time.TIMEZONE_UTC);
    }

    private static void verifyToMillisInvalid(int[] timeFields, String timezone) {
        Time time = new Time(timezone);

        // isDst = 0, toMillis(true)
        Fields.set(time, timeFields);
        time.isDst = 0;
        verifyToMillisResult(true, time, -1);

        // isDst = 0, toMillis(false)
        Fields.set(time, timeFields);
        time.isDst = 0;
        verifyToMillisResult(false, time, -1);

        // isDst = 1, toMillis(true)
        Fields.set(time, timeFields);
        time.isDst = 1;
        verifyToMillisResult(true, time, -1);

        // isDst = 1, toMillis(false)
        Fields.set(time, timeFields);
        time.isDst = 1;
        verifyToMillisResult(false, time, -1);

        // isDst = -1, toMillis(true)
        Fields.set(time, timeFields);
        time.isDst = -1;
        verifyToMillisResult(true, time, -1);

        // isDst = -1, toMillis(false)
        Fields.set(time, timeFields);
        time.isDst = -1;
        verifyToMillisResult(false, time, -1);
    }

    @Test
    public void testToMillis_afterTzRecords() {
        int[] timeFields = new int[] { 2039, 0, 1, 2, 3, 4, -999 /* not used */, 9, 9, 9 };
        verifyToMillisInvalid(timeFields, PstPdt.ID);
        verifyToMillisInvalid(timeFields, Time.TIMEZONE_UTC);
    }

    @Test
    public void testToMillis_invalid() {
        int[] timeFields = new int[] { 0, 0, 0, 0, 0, 0, -999 /* not used */, 9, 9, 9 };
        verifyToMillisInvalid(timeFields, PstPdt.ID);
        verifyToMillisInvalid(timeFields, Time.TIMEZONE_UTC);
    }

    @Test
    public void testParse_date() {
        String nonUtcTz = PstPdt.ID;
        Time t = new Time(nonUtcTz);
        assertFalse(t.parse("12345678"));
        Time expected = new Time(nonUtcTz);
        Fields.setAllDayDate(expected, 1234, 55, 78);
        Fields.setDst(expected, -1 /* isDst */, 0);
        Fields.setDerivedDateTime(expected, 0, 0);
        Fields.verifyTimeEquals(expected, t);
    }

    @Test(expected=NullPointerException.class)
    public void testParse_null() {
        Time t = new Time(Time.TIMEZONE_UTC);
        t.parse(null);
    }

    @Test
    public void testParse() {
        Time t = new Time(Time.TIMEZONE_UTC);
        t.parse("20061005T120000");

        Time expected = new Time(Time.TIMEZONE_UTC);
        Fields.set(expected, 2006, 9, 5, 12, 0, 0, -1 /* isDst */, 0, 0, 0);
        Fields.verifyTimeEquals(expected, t);
    }

    @Test
    public void testParse_dateTime() {
        String nonUtcTz = PstPdt.ID;
        Time t = new Time(nonUtcTz);
        assertFalse(t.parse("12345678T901234"));
        Time expected = new Time(nonUtcTz);
        Fields.set(expected, 1234, 55, 78, 90, 12, 34, -1 /* isDst */, 0, 0, 0);
        Fields.verifyTimeEquals(expected, t);

        Time t2 = new Time(nonUtcTz);
        assertTrue(t2.parse("12345678T901234Z"));
        Time utcExpected = new Time(Time.TIMEZONE_UTC);
        Fields.set(utcExpected, 1234, 55, 78, 90, 12, 34, -1 /* isDst */, 0, 0, 0);
        Fields.verifyTimeEquals(utcExpected, t2);
    }

    @Test(expected=NullPointerException.class)
    public void testParse_pstPdtNull() {
        Time t = new Time(PstPdt.ID);
        t.parse(null);
    }

    @Test
    public void testParse_errors() {
        // Too short
        verifyParseError("");
        verifyParseError("1");
        verifyParseError("12");
        verifyParseError("123");
        verifyParseError("1234");
        verifyParseError("12345");
        verifyParseError("123456");
        verifyParseError("1234567");

        // No "T" in the expected place
        verifyParseError("12345678S");

        // Invalid character in the first 8 characters.
        verifyParseError("12X45678");

        // Too short for a date/time (15 or 16 characters allowed)
        verifyParseError("12345678T");
        verifyParseError("12345678T0");
        verifyParseError("12345678T01");
        verifyParseError("12345678T012");
        verifyParseError("12345678T0123");
        verifyParseError("12345678T01234");

        // Invalid character
        verifyParseError("12345678T0X2345");
    }

    private static void verifyParseError(String s) {
        Time t = new Time(Time.TIMEZONE_UTC);
        try {
            t.parse(s);
            fail();
        } catch (TimeFormatException expected) {
        }
    }

    @Test
    public void testParse3339() {
        String tz = Time.TIMEZONE_UTC;
        Time expected = new Time(tz);
        Fields.setAllDayDate(expected, 1980, 4, 23);
        Fields.setDst(expected, -1 /* isDst */, 0);
        Fields.setDerivedDateTime(expected, 0, 0);
        verifyParse3339Succeeds(tz, "1980-05-23", expected);

        Fields.setDateTime(expected, 1980, 4, 23, 9, 50, 50);
        Fields.setDst(expected, -1 /* isDst */, 0);
        Fields.setDerivedDateTime(expected, 0, 0);
        verifyParse3339Succeeds(tz, "1980-05-23T09:50:50", expected);

        Fields.setDateTime(expected, 1980, 4, 23, 9, 50, 50);
        Fields.setDst(expected, -1 /* isDst */, 0);
        Fields.setDerivedDateTime(expected, 0, 0);
        verifyParse3339Succeeds(tz, "1980-05-23T09:50:50Z", expected);

        Fields.setDateTime(expected, 1980, 4, 23, 9, 50, 50);
        Fields.setDst(expected, -1 /* isDst */, 0);
        Fields.setDerivedDateTime(expected, 0, 0);
        verifyParse3339Succeeds(tz, "1980-05-23T09:50:50.0Z", expected);

        Fields.setDateTime(expected, 1980, 4, 23, 9, 50, 50);
        Fields.setDst(expected, -1 /* isDst */, 0);
        Fields.setDerivedDateTime(expected, 0, 0);
        verifyParse3339Succeeds(tz, "1980-05-23T09:50:50.12Z", expected);

        Fields.setDateTime(expected, 1980, 4, 23, 9, 50, 50);
        Fields.setDst(expected, -1 /* isDst */, 0);
        Fields.setDerivedDateTime(expected, 0, 0);
        verifyParse3339Succeeds(tz, "1980-05-23T09:50:50.123Z", expected);

        // The time should be normalized to UTC
        Fields.setDateTime(expected, 1980, 4, 23, 10, 55, 50);
        Fields.setDst(expected, -1 /* isDst */, 0);
        Fields.setDerivedDateTime(expected, 0, 0);
        verifyParse3339Succeeds(tz, "1980-05-23T09:50:50-01:05", expected);

        // The time should be normalized to UTC
        Fields.setDateTime(expected, 1980, 4, 23, 10, 55, 50);
        Fields.setDst(expected, -1 /* isDst */, 0);
        Fields.setDerivedDateTime(expected, 0, 0);
        verifyParse3339Succeeds(tz, "1980-05-23T09:50:50.123-01:05", expected);
    }

    private static void verifyParse3339Succeeds(String timeZone, String toParse, Time expected) {
        Time t = new Time(timeZone);
        t.parse3339(toParse);
        Fields.verifyTimeEquals(expected, t);
    }

    @Test
    public void testParse3339_parseErrors() {
        // Too short
        verifyParse3339Error("1980");

        // Timezone too short
        verifyParse3339Error("1980-05-23T09:50:50.123+");
        verifyParse3339Error("1980-05-23T09:50:50.123+05:0");
    }

    @Test(expected=NullPointerException.class)
    public void testParse3339_null() {
        Time t = new Time(Time.TIMEZONE_UTC);
        t.parse3339(null);
    }

    private void verifyParse3339Error(String s) {
        String tz = Time.TIMEZONE_UTC;
        Time t = new Time(tz);
        try {
            t.parse3339(s);
            fail();
        } catch (TimeFormatException expected) {
        }
    }

    @Test
    public void testSetMillis_utc() {
        String tz = Time.TIMEZONE_UTC;
        Time t = new Time(tz);
        t.set(1000L);

        Time expected = new Time(tz);
        Fields.set(expected, 1970, 0, 1, 0, 0, 1, 0 /* isDst */, 0, 0, 4);
        Fields.verifyTimeEquals(expected, t);

        t.set(2000L);
        Fields.set(expected, 1970, 0, 1, 0, 0, 2, 0 /* isDst */, 0, 0, 4);
        Fields.verifyTimeEquals(expected, t);

        t.set(1000L * 60);
        Fields.set(expected, 1970, 0, 1, 0, 1, 0, 0 /* isDst */, 0, 0, 4);
        Fields.verifyTimeEquals(expected, t);

        t.set((1000L * 60 * 60 * 24) + 1000L);
        Fields.set(expected, 1970, 0, 2, 0, 0, 1, 0 /* isDst */, 0, 1, 5);
        Fields.verifyTimeEquals(expected, t);
    }

    @Test
    public void testSetMillis_utc_edgeCases() {
        String tz = Time.TIMEZONE_UTC;
        Time t = new Time(tz);
        t.set(Integer.MAX_VALUE + 1L);

        Time expected = new Time(tz);
        // This a 32-bit int overflow bug.
        Fields.set(expected, 1970, 0, 25, 20, 31, 23, 0 /* isDst */, 0, 24, 0);
        Fields.verifyTimeEquals(expected, t);

        t = new Time(tz);
        t.set(Integer.MIN_VALUE - 1L);
        // This a 32-bit int underflow bug.
        Fields.set(expected, 1969, 11, 7, 3, 28, 37, 0 /* isDst */, 0, 340, 0);
        Fields.verifyTimeEquals(expected, t);
    }

    @Test
    public void testSetFields() {
        String tz = Time.TIMEZONE_UTC;
        Time t = new Time(tz);
        Fields.set(t, 9, 9, 9, 9, 9, 9, 9 /* isDst */, 9, 9, 9);

        t.set(1, 2, 3, 4, 5, 6);

        Time expected = new Time(tz);
        Fields.set(expected, 6, 5, 4, 3, 2, 1, -1 /* isDst */, 0, 0, 0);
        Fields.verifyTimeEquals(expected, t);
    }

    // Timezones that cover the world.  Some GMT offsets occur more than
    // once in case some cities decide to change their GMT offset.
    private static final String[] mTimeZones = {
        "UTC",
        "Pacific/Kiritimati",
        "Pacific/Enderbury",
        "Pacific/Fiji",
        "Antarctica/South_Pole",
        "Pacific/Norfolk",
        "Pacific/Ponape",
        "Asia/Magadan",
        "Australia/Lord_Howe",
        "Australia/Sydney",
        "Australia/Adelaide",
        "Asia/Tokyo",
        "Asia/Seoul",
        "Asia/Taipei",
        "Asia/Singapore",
        "Asia/Hong_Kong",
        "Asia/Saigon",
        "Asia/Bangkok",
        "Indian/Cocos",
        "Asia/Rangoon",
        "Asia/Omsk",
        "Antarctica/Mawson",
        "Asia/Colombo",
        "Asia/Calcutta",
        "Asia/Oral",
        "Asia/Kabul",
        "Asia/Dubai",
        "Asia/Tehran",
        "Europe/Moscow",
        "Asia/Baghdad",
        "Africa/Mogadishu",
        "Europe/Athens",
        "Africa/Cairo",
        "Europe/Rome",
        "Europe/Berlin",
        "Europe/Amsterdam",
        "Africa/Tunis",
        "Europe/London",
        "Europe/Dublin",
        "Atlantic/St_Helena",
        "Africa/Monrovia",
        "Africa/Accra",
        "Atlantic/Azores",
        "Atlantic/South_Georgia",
        "America/Noronha",
        "America/Sao_Paulo",
        "America/Cayenne",
        "America/St_Johns",
        "America/Puerto_Rico",
        "America/Aruba",
        "America/New_York",
        "America/Chicago",
        "America/Denver",
        "America/Los_Angeles",
        "America/Anchorage",
        "Pacific/Marquesas",
        "America/Adak",
        "Pacific/Honolulu",
        "Pacific/Midway",
    };

    @Test
    public void testGetJulianDay() {
        Time time = new Time();

        // For every 15th day of 2008, and for each of the timezones listed above,
        // get the Julian day for 12am and then check that if we change the time we get the
        // same Julian day. Note that one of the many problems with the Time class
        // is its lack of error handling. If we accidentally hit a time that doesn't
        // exist (because it was skipped by a daylight savings transition), rather than
        // an error, you'll silently get 1970-01-01. We should @deprecate Time.
        for (int monthDay = 1; monthDay <= 366; monthDay += 15) {
            for (int zoneIndex = 0; zoneIndex < mTimeZones.length; zoneIndex++) {
                // We leave the "month" as zero because we are changing the
                // "monthDay" from 1 to 366. The call to normalize() will
                // then change the "month" (but we don't really care).
                time.set(0, 0, 12, monthDay, 0, 2008);
                time.timezone = mTimeZones[zoneIndex];
                long millis = time.normalize(true);
                if (zoneIndex == 0) {
                    Log.i(TAG, time.format("%B %d, %Y"));
                }

                // This is the Julian day for 12pm for this day of the year
                int julianDay = Time.getJulianDay(millis, time.gmtoff);

                // Change the time during the day and check that we get the same
                // Julian day.
                for (int hour = 0; hour < 24; hour++) {
                    for (int minute = 0; minute < 60; minute += 15) {
                        time.set(0, minute, hour, monthDay, 0, 2008);
                        millis = time.normalize(true);
                        if (millis == -1) {
                            // millis == -1 means the wall time does not exist in the chosen
                            // timezone due to a DST change. We cannot calculate a JulianDay for -1.
                            continue;
                        }

                        int day = Time.getJulianDay(millis, time.gmtoff);
                        assertEquals("Julian day: " + day + " at time "
                                + time.hour + ":" + time.minute
                                + " != today's Julian day: " + julianDay
                                + " timezone: " + time.timezone, day, julianDay);
                    }
                }
            }
        }
    }

    @Test
    public void testSetJulianDay() {
        Time time = new Time();

        // For each day of the year in 2008, and for each timezone,
        // test that we can set the Julian day correctly.
        for (int monthDay = 1; monthDay <= 366; monthDay += 20) {
            for (int zoneIndex = 0; zoneIndex < mTimeZones.length; zoneIndex++) {
                // We leave the "month" as zero because we are changing the
                // "monthDay" from 1 to 366. The call to normalize() will
                // then change the "month" (but we don't really care).
                time.set(0, 0, 12, monthDay, 0, 2008);
                time.timezone = mTimeZones[zoneIndex];
                long millis = time.normalize(true);
                if (zoneIndex == 0) {
                    Log.i(TAG, time.format("%B %d, %Y"));
                }
                // This is the Julian day for 12pm for this day of the year
                int julianDay = Time.getJulianDay(millis, time.gmtoff);

                time.setJulianDay(julianDay);

                // Some places change daylight saving time at 12am and so there
                // is no 12am on some days in some timezones. In those cases,
                // the time is set to 1am.
                // Examples: Africa/Cairo on April 25, 2008
                // America/Sao_Paulo on October 12, 2008
                // Atlantic/Azores on March 30, 2008
                assertTrue(time.hour == 0 || time.hour == 1);
                assertEquals(0, time.minute);
                assertEquals(0, time.second);

                millis = time.toMillis(false);
                if (millis == -1) {
                    // millis == -1 means the wall time does not exist in the chosen
                    // timezone due to a DST change. We cannot calculate a JulianDay for -1.
                    continue;
                }
                int day = Time.getJulianDay(millis, time.gmtoff);
                assertEquals("Error: gmtoff " + (time.gmtoff / 3600.0) + " day " + julianDay
                                + " millis " + millis+ " " + time.format("%B %d, %Y")
                                + " " + time.timezone,
                        day, julianDay);
            }
        }
    }

    @Test
    public void testGetJulianMondayFromWeeksSinceEpoch() {
        final int mondayBeforeEpoch = Time.MONDAY_BEFORE_JULIAN_EPOCH;
        assertEquals(mondayBeforeEpoch, Time.getJulianMondayFromWeeksSinceEpoch(0));
        assertEquals(mondayBeforeEpoch + 7, Time.getJulianMondayFromWeeksSinceEpoch(1));
        assertEquals(mondayBeforeEpoch + 14, Time.getJulianMondayFromWeeksSinceEpoch(2));
        assertEquals(mondayBeforeEpoch - 7, Time.getJulianMondayFromWeeksSinceEpoch(-1));
    }

    @Test
    public void testGetWeeksSinceEpochFromJulianDay() {
        final int epoch = Time.EPOCH_JULIAN_DAY;  // a Thursday
        assertEquals(0, Time.getWeeksSinceEpochFromJulianDay(epoch, Time.SUNDAY));
        assertEquals(0, Time.getWeeksSinceEpochFromJulianDay(epoch, Time.MONDAY));
        assertEquals(0, Time.getWeeksSinceEpochFromJulianDay(epoch, Time.TUESDAY));
        assertEquals(0, Time.getWeeksSinceEpochFromJulianDay(epoch, Time.WEDNESDAY));
        assertEquals(0, Time.getWeeksSinceEpochFromJulianDay(epoch, Time.THURSDAY));
        assertEquals(0, Time.getWeeksSinceEpochFromJulianDay(epoch, Time.FRIDAY));
        assertEquals(0, Time.getWeeksSinceEpochFromJulianDay(epoch, Time.SATURDAY));

        final int epochFriday = epoch + 1;
        assertEquals(0, Time.getWeeksSinceEpochFromJulianDay(epochFriday, Time.SUNDAY));
        assertEquals(0, Time.getWeeksSinceEpochFromJulianDay(epochFriday, Time.MONDAY));
        assertEquals(0, Time.getWeeksSinceEpochFromJulianDay(epochFriday, Time.TUESDAY));
        assertEquals(0, Time.getWeeksSinceEpochFromJulianDay(epochFriday, Time.WEDNESDAY));
        assertEquals(0, Time.getWeeksSinceEpochFromJulianDay(epochFriday, Time.THURSDAY));
        assertEquals(1, Time.getWeeksSinceEpochFromJulianDay(epochFriday, Time.FRIDAY));
        assertEquals(0, Time.getWeeksSinceEpochFromJulianDay(epochFriday, Time.SATURDAY));

        final int epochSaturday = epoch + 2;
        assertEquals(0, Time.getWeeksSinceEpochFromJulianDay(epochSaturday, Time.SUNDAY));
        assertEquals(0, Time.getWeeksSinceEpochFromJulianDay(epochSaturday, Time.MONDAY));
        assertEquals(0, Time.getWeeksSinceEpochFromJulianDay(epochSaturday, Time.TUESDAY));
        assertEquals(0, Time.getWeeksSinceEpochFromJulianDay(epochSaturday, Time.WEDNESDAY));
        assertEquals(0, Time.getWeeksSinceEpochFromJulianDay(epochSaturday, Time.THURSDAY));
        assertEquals(1, Time.getWeeksSinceEpochFromJulianDay(epochSaturday, Time.FRIDAY));
        assertEquals(1, Time.getWeeksSinceEpochFromJulianDay(epochSaturday, Time.SATURDAY));

        final int tenWeeksLater = epochSaturday + 10 * 7;
        assertEquals(10, Time.getWeeksSinceEpochFromJulianDay(tenWeeksLater, Time.SUNDAY));
        assertEquals(10, Time.getWeeksSinceEpochFromJulianDay(tenWeeksLater, Time.MONDAY));
        assertEquals(10, Time.getWeeksSinceEpochFromJulianDay(tenWeeksLater, Time.TUESDAY));
        assertEquals(10, Time.getWeeksSinceEpochFromJulianDay(tenWeeksLater, Time.WEDNESDAY));
        assertEquals(10, Time.getWeeksSinceEpochFromJulianDay(tenWeeksLater, Time.THURSDAY));
        assertEquals(11, Time.getWeeksSinceEpochFromJulianDay(tenWeeksLater, Time.FRIDAY));
        assertEquals(11, Time.getWeeksSinceEpochFromJulianDay(tenWeeksLater, Time.SATURDAY));
    }

    @Test
    public void testNormalize_utc() {
        Time t = new Time(Time.TIMEZONE_UTC);
        Time expected = new Time(Time.TIMEZONE_UTC);

        long winterTimeUtcMillis = 1167613323000L;

        Fields.set(t, 2007, 0, 1, 1, 2, 3, 0 /* isDst */, 9, 9, 9);
        Fields.set(expected, 2007, 0, 1, 1, 2, 3, 0 /* isDst */, 0, 0, 1);
        verifyNormalizeResult(true, t, expected, winterTimeUtcMillis);

        Fields.set(t, 2007, 0, 1, 1, 2, 3, 1 /* isDst */, 9, 9, 9);
        Fields.set(expected, 2007, 0, 1, 1, 2, 3, 0 /* isDst */, 0, 0, 1);
        verifyNormalizeResult(true, t, expected, winterTimeUtcMillis);

        Fields.set(t, 2007, 0, 1, 1, 2, 3, -1 /* isDst */, 9, 9, 9);
        Fields.set(expected, 2007, 0, 1, 1, 2, 3, 0 /* isDst */, 0, 0, 1);
        verifyNormalizeResult(true, t, expected, winterTimeUtcMillis);

        Fields.set(t, 2007, 0, 1, 1, 2, 3, 0 /* isDst */, 9, 9, 9);
        Fields.set(expected, 2007, 0, 1, 1, 2, 3, 0 /* isDst */, 0, 0, 1);
        verifyNormalizeResult(false, t, expected, winterTimeUtcMillis);

        Fields.set(t, 2007, 0, 1, 1, 2, 3, 1 /* isDst */, 9, 9, 9);
        Fields.set(expected, 2007, 0, 1, 1, 2, 3, 1 /* isDst */, 9, 9, 9);
        verifyNormalizeResult(false, t, expected, -1);

        Fields.set(t, 2007, 0, 1, 1, 2, 3, -1 /* isDst */, 9, 9, 9);
        Fields.set(expected, 2007, 0, 1, 1, 2, 3, 0 /* isDst */, 0, 0, 1);
        verifyNormalizeResult(false, t, expected, winterTimeUtcMillis);

        long summerTimeUtcMillis = 1180659723000L;

        Fields.set(t, 2007, 5, 1, 1, 2, 3, 0 /* isDst */, 9, 9, 9);
        Fields.set(expected, 2007, 5, 1, 1, 2, 3, 0 /* isDst */, 0, 151, 5);
        verifyNormalizeResult(true, t, expected, summerTimeUtcMillis);

        Fields.set(t, 2007, 5, 1, 1, 2, 3, 1 /* isDst */, 9, 9, 9);
        Fields.set(expected, 2007, 5, 1, 1, 2, 3, 0 /* isDst */, 0, 151, 5);
        verifyNormalizeResult(true, t, expected, summerTimeUtcMillis);

        Fields.set(t, 2007, 5, 1, 1, 2, 3, -1 /* isDst */, 9, 9, 9);
        Fields.set(expected, 2007, 5, 1, 1, 2, 3, 0 /* isDst */, 0, 151, 5);
        verifyNormalizeResult(true, t, expected, summerTimeUtcMillis);

        Fields.set(t, 2007, 5, 1, 1, 2, 3, 0 /* isDst */, 9, 9, 9);
        Fields.set(expected, 2007, 5, 1, 1, 2, 3, 0 /* isDst */, 0, 151, 5);
        verifyNormalizeResult(false, t, expected, summerTimeUtcMillis);

        Fields.set(t, 2007, 5, 1, 1, 2, 3, 1 /* isDst */, 9, 9, 9);
        Fields.set(expected, 2007, 5, 1, 1, 2, 3, 1 /* isDst */, 9, 9, 9);
        verifyNormalizeResult(false, t, expected, -1);

        Fields.set(t, 2007, 5, 1, 1, 2, 3, -1 /* isDst */, 1, 9, 9);
        Fields.set(expected, 2007, 5, 1, 1, 2, 3, 0 /* isDst */, 0, 151, 5);
        verifyNormalizeResult(false, t, expected, summerTimeUtcMillis);
    }

    @Test
    public void testNormalize_dstTz() {
        Time t = new Time(PstPdt.ID);
        Time expected = new Time(PstPdt.ID);

        // A STD time
        long stdTimeUtcMillis = 1167613323000L;
        long stdTimeMillis = stdTimeUtcMillis - PstPdt.getUtcOffsetMillis(false);

        Fields.set(t, 2007, 0, 1, 1, 2, 3, 0 /* isDst */, 9, 9, 9);
        Fields.set(expected, 2007, 0, 1, 1, 2, 3, 0 /* isDst */, -28800, 0, 1);
        verifyNormalizeResult(true, t, expected, stdTimeMillis);

        Fields.set(t, 2007, 0, 1, 1, 2, 3, 1 /* isDst */, 9, 9, 9);
        Fields.set(expected, 2007, 0, 1, 1, 2, 3, 0 /* isDst */, -28800, 0, 1);
        verifyNormalizeResult(true, t, expected, stdTimeMillis);

        Fields.set(t, 2007, 0, 1, 1, 2, 3, -1 /* isDst */, 9, 9, 9);
        Fields.set(expected, 2007, 0, 1, 1, 2, 3, 0 /* isDst */, -28800, 0, 1);
        verifyNormalizeResult(true, t, expected, stdTimeMillis);

        long dstToStdCorrectionMillis =
                PstPdt.getUtcOffsetMillis(false) - PstPdt.getUtcOffsetMillis(true);

        Fields.set(t, 2007, 0, 1, 1, 2, 3, 0 /* isDst */, 9, 9, 9);
        Fields.set(expected, 2007, 0, 1, 1, 2, 3, 0 /* isDst */, -28800, 0, 1);
        verifyNormalizeResult(false, t, expected, stdTimeMillis);

        Fields.set(t, 2007, 0, 1, 1, 2, 3, 1 /* isDst */, 9, 9, 9);
        Fields.set(expected, 2007, 0, 1, 0, 2, 3, 0 /* isDst */, -28800, 0, 1);
        verifyNormalizeResult(false, t, expected, stdTimeMillis + dstToStdCorrectionMillis);

        Fields.set(t, 2007, 0, 1, 1, 2, 3, -1 /* isDst */, 9, 9, 9);
        Fields.set(expected, 2007, 0, 1, 1, 2, 3, 0 /* isDst */, -28800, 0, 1);
        verifyNormalizeResult(false, t, expected, stdTimeMillis);

        // A DST time
        long dstTimeUtcMillis = 1180659723000L;
        long dstTimeMillis = dstTimeUtcMillis - PstPdt.getUtcOffsetMillis(true);

        Fields.set(t, 2007, 5, 1, 1, 2, 3, 0 /* isDst */, 9, 9, 9);
        Fields.set(expected, 2007, 5, 1, 1, 2, 3, 1 /* isDst */, -25200, 151, 5);
        verifyNormalizeResult(true, t, expected, dstTimeMillis);

        Fields.set(t, 2007, 5, 1, 1, 2, 3, 1 /* isDst */, 9, 9, 9);
        Fields.set(expected, 2007, 5, 1, 1, 2, 3, 1 /* isDst */, -25200, 151, 5);
        verifyNormalizeResult(true, t, expected, dstTimeMillis);

        Fields.set(t, 2007, 5, 1, 1, 2, 3, -1 /* isDst */, 9, 9, 9);
        Fields.set(expected, 2007, 5, 1, 1, 2, 3, 1 /* isDst */, -25200, 151, 5);
        verifyNormalizeResult(true, t, expected, dstTimeMillis);

        long stdToDstCorrectionMillis = -dstToStdCorrectionMillis;

        Fields.set(t, 2007, 5, 1, 1, 2, 3, 0 /* isDst */, 9, 9, 9);
        Fields.set(expected, 2007, 5, 1, 2, 2, 3, 1 /* isDst */, -25200, 151, 5);
        verifyNormalizeResult(false, t, expected, dstTimeMillis + stdToDstCorrectionMillis);

        Fields.set(t, 2007, 5, 1, 1, 2, 3, 1 /* isDst */, -25200, 151, 5);
        Fields.set(expected, 2007, 5, 1, 1, 2, 3, 1 /* isDst */, -25200, 151, 5);
        verifyNormalizeResult(false, t, expected, dstTimeMillis);

        Fields.set(t, 2007, 5, 1, 1, 2, 3, -1 /* isDst */, -25200, 151, 5);
        Fields.set(expected, 2007, 5, 1, 1, 2, 3, 1 /* isDst */, -25200, 151, 5);
        verifyNormalizeResult(false, t, expected, dstTimeMillis);
    }

    @Test
    public void testNormalize_skippedTime() {
        // Tests behavior around a transition from STD to DST that introduces an hour of "skipped"
        // time from 01:00 to 01:59.
        String timezone = PstPdt.ID;
        long stdBaseTimeMillis = 1173607200000L;
        long dstBaseTimeMillis = 1173603600000L;

        // Try each minute from one minute before the skipped hour until one after.
        for (int i = -1; i <= 60; i++) {
            int minutesInMillis = i * 60000;
            int[] dateTimeArgs = new int[] { 2007, 2, 11, 2, i, 0 };

            int[] normalizedAdjustedBackwardDateTimeArgs;
            int[] normalizedDateTimeArgs;
            int[] normalizedAdjustedForwardDateTimeArgs;
            if (i == -1) {
                normalizedAdjustedBackwardDateTimeArgs = new int[] { 2007, 2, 11, 0, 59, 0 };
                normalizedDateTimeArgs = new int[] { 2007, 2, 11, 1, 59, 0 };
                normalizedAdjustedForwardDateTimeArgs = null;
            } else if (i == 60) {
                normalizedAdjustedBackwardDateTimeArgs = null;
                normalizedDateTimeArgs = new int[] { 2007, 2, 11, 3, 0, 0 };
                normalizedAdjustedForwardDateTimeArgs = new int[] { 2007, 2, 11, 4, 0, 0 };
            } else {
                normalizedAdjustedBackwardDateTimeArgs = new int[] { 2007, 2, 11, 1, i, 0 };
                normalizedDateTimeArgs = null;
                normalizedAdjustedForwardDateTimeArgs =  new int[] { 2007, 2, 11, 3, i, 0 };
            }

            Time time = new Time(timezone);
            Time expected = new Time(timezone);

            // isDst = 0, normalize(true)
            Fields.setDateTime(time, dateTimeArgs);
            Fields.setDst(time, 0 /* isDst */, 9);
            Fields.setDerivedDateTime(time, 9, 9);

            long timeMillis = time.normalize(true);
            long expectedTimeMillis;
            if (i == -1) {
                expectedTimeMillis = stdBaseTimeMillis + minutesInMillis;
                Fields.setDateTime(expected, normalizedDateTimeArgs);
                Fields.setDst(expected, 0, PstPdt.getUtcOffsetSeconds(0));
                Fields.setDerivedDateTime(expected, 69, 0);
            } else if (i == 60) {
                expectedTimeMillis = dstBaseTimeMillis + minutesInMillis;
                Fields.setDateTime(expected, normalizedDateTimeArgs);
                Fields.setDst(expected, 1, PstPdt.getUtcOffsetSeconds(1));
                Fields.setDerivedDateTime(expected, 69, 0);
            } else {
                expectedTimeMillis = -1;
                Fields.setDateTime(expected, dateTimeArgs);
                Fields.setDst(expected, -1, 9);
                Fields.setDerivedDateTime(expected, 9, 9);
            }
            assertEquals("i = " + i, expectedTimeMillis, timeMillis);
            Fields.verifyTimeEquals("i = " + i, expected, time);

            // isDst = 0, normalize(false)
            Fields.setDateTime(time, dateTimeArgs);
            Fields.setDst(time, 0 /* isDst */, 9);
            Fields.setDerivedDateTime(time, 9, 9);

            timeMillis = time.normalize(false);
            expectedTimeMillis = stdBaseTimeMillis + minutesInMillis;
            assertEquals("i = " + i, expectedTimeMillis, timeMillis);
            if (i == -1) {
                Fields.setDateTime(expected, normalizedDateTimeArgs);
                Fields.setDst(expected, 0, PstPdt.getUtcOffsetSeconds(0));
            } else {
                Fields.setDateTime(expected, normalizedAdjustedForwardDateTimeArgs);
                Fields.setDst(expected, 1, PstPdt.getUtcOffsetSeconds(1));
            }
            Fields.setDerivedDateTime(expected, 69, 0);
            Fields.verifyTimeEquals("i = " + i, expected, time);

            // isDst = 1, normalize(true)
            Fields.setDateTime(time, dateTimeArgs);
            Fields.setDst(time, 1 /* isDst */, 9);
            Fields.setDerivedDateTime(time, 9, 9);

            timeMillis = time.normalize(true);
            if (i == -1) {
                expectedTimeMillis = stdBaseTimeMillis + minutesInMillis;
                Fields.setDateTime(expected, normalizedDateTimeArgs);
                Fields.setDst(expected, 0, PstPdt.getUtcOffsetSeconds(0));
                Fields.setDerivedDateTime(expected, 69, 0);
            } else if (i == 60) {
                expectedTimeMillis = dstBaseTimeMillis + minutesInMillis;
                Fields.setDateTime(expected, normalizedDateTimeArgs);
                Fields.setDst(expected, 1, PstPdt.getUtcOffsetSeconds(1));
                Fields.setDerivedDateTime(expected, 69, 0);
            } else {
                expectedTimeMillis = -1;
                Fields.setDateTime(expected, dateTimeArgs);
                Fields.setDst(expected, -1, 9);
                Fields.setDerivedDateTime(expected, 9, 9);
            }
            assertEquals("i = " + i, expectedTimeMillis, timeMillis);
            Fields.verifyTimeEquals("i = " + i, expected, time);

            // isDst = 1, normalize(false)
            Fields.setDateTime(time, dateTimeArgs);
            Fields.setDst(time, 1 /* isDst */, 9);
            Fields.setDerivedDateTime(time, 9, 9);

            timeMillis = time.normalize(false);
            expectedTimeMillis = dstBaseTimeMillis + minutesInMillis;
            assertEquals("i = " + i, expectedTimeMillis, timeMillis);
            if (i == 60) {
                Fields.setDateTime(expected, normalizedDateTimeArgs);
                Fields.setDst(expected, 1, PstPdt.getUtcOffsetSeconds(1));
            } else {
                Fields.setDateTime(expected, normalizedAdjustedBackwardDateTimeArgs);
                Fields.setDst(expected, 0, PstPdt.getUtcOffsetSeconds(0));
            }
            Fields.setDerivedDateTime(expected, 69, 0);
            Fields.verifyTimeEquals("i = " + i, expected, time);

            // isDst = -1, normalize(true)
            Fields.setDateTime(time, dateTimeArgs);
            Fields.setDst(time, -1 /* isDst */, 9);
            Fields.setDerivedDateTime(time, 9, 9);

            timeMillis = time.normalize(true);
            if (i == -1) {
                expectedTimeMillis = stdBaseTimeMillis + minutesInMillis;
                Fields.setDateTime(expected, normalizedDateTimeArgs);
                Fields.setDst(expected, 0, PstPdt.getUtcOffsetSeconds(0));
                Fields.setDerivedDateTime(expected, 69, 0);
            } else if (i == 60) {
                expectedTimeMillis = dstBaseTimeMillis + minutesInMillis;
                Fields.setDateTime(expected, normalizedDateTimeArgs);
                Fields.setDst(expected, 1, PstPdt.getUtcOffsetSeconds(1));
                Fields.setDerivedDateTime(expected, 69, 0);
            } else {
                expectedTimeMillis = -1;
                Fields.setDateTime(expected, dateTimeArgs);
                Fields.setDst(expected, -1, 9);
                Fields.setDerivedDateTime(expected, 9, 9);
            }
            assertEquals("i = " + i, expectedTimeMillis, timeMillis);
            Fields.verifyTimeEquals("i = " + i, expected, time);

            // isDst = -1, normalize(false)
            Fields.setDateTime(time, dateTimeArgs);
            Fields.setDst(time, -1 /* isDst */, 9);
            Fields.setDerivedDateTime(time, 9, 9);

            timeMillis = time.normalize(false);
            if (i == -1) {
                expectedTimeMillis = stdBaseTimeMillis + minutesInMillis;
                Fields.setDateTime(expected, normalizedDateTimeArgs);
                Fields.setDst(expected, 0, PstPdt.getUtcOffsetSeconds(0));
                Fields.setDerivedDateTime(expected, 69, 0);
            } else if (i == 60) {
                expectedTimeMillis = dstBaseTimeMillis + minutesInMillis;
                Fields.setDateTime(expected, normalizedDateTimeArgs);
                Fields.setDst(expected, 1, PstPdt.getUtcOffsetSeconds(1));
                Fields.setDerivedDateTime(expected, 69, 0);
            } else {
                expectedTimeMillis = -1;
                Fields.setDateTime(expected, dateTimeArgs);
                Fields.setDst(expected, -1, 9);
                Fields.setDerivedDateTime(expected, 9, 9);
            }
            assertEquals("i = " + i, expectedTimeMillis, timeMillis);
            Fields.verifyTimeEquals("i = " + i, expected, time);
        }
    }

    @Test
    public void testNormalize_duplicateWallTime() {
        // 1:00 in standard / 2:00 in DST
        long timeBaseMillis = 1194163200000L;
        long dstCorrectionMillis = 3600000;

        // Try each minute from one minute before the duplicated hour until one after.
        for (int i = -1; i <= 60; i++) {
            Time time = new Time(PstPdt.ID);
            Time expected = new Time(PstPdt.ID);

            int[] dateTimeArgs = new int[] { 2007, 10, 4, 1, i, 0 };
            int[] normalizedAdjustedBackwardDateTimeArgs;
            int[] normalizedDateTimeArgs;
            int[] normalizedAdjustedForwardDateTimeArgs;
            if (i == -1) {
                normalizedAdjustedBackwardDateTimeArgs = null;
                normalizedDateTimeArgs = new int[] { 2007, 10, 4, 0, 59, 0 };
                normalizedAdjustedForwardDateTimeArgs = new int[] { 2007, 10, 4, 1, 59, 0 };
            } else if (i == 60) {
                normalizedAdjustedBackwardDateTimeArgs = new int[] { 2007, 10, 4, 1, 0, 0 };
                normalizedDateTimeArgs = new int[] { 2007, 10, 4, 2, 0, 0 };
                normalizedAdjustedForwardDateTimeArgs = null;
            } else {
                normalizedAdjustedBackwardDateTimeArgs = null;
                normalizedDateTimeArgs = dateTimeArgs;
                normalizedAdjustedForwardDateTimeArgs =  null;
            }

            int minutesInMillis = i * 60000;

            // isDst = 0, normalize(true)
            Fields.setDateTime(time, dateTimeArgs);
            Fields.setDst(time, 0 /* isDst */, 9);
            Fields.setDerivedDateTime(time, 9, 9);

            long timeMillis = time.normalize(true);

            Fields.setDateTime(expected, normalizedDateTimeArgs);
            // When ignoreDst == true the choice between DST and STD is arbitrary when both answers
            // are possible.
            if (timeMillis == timeBaseMillis + minutesInMillis) {
                // i == 60 is unambiguous
                assertTrue("i =" + i, i < 60);
                Fields.setDst(expected, 1 /* isDst */, PstPdt.getUtcOffsetSeconds(1));
            } else if (timeMillis == timeBaseMillis + minutesInMillis + dstCorrectionMillis) {
                // i == -1 is unambiguous
                assertTrue("i =" + i, i > -1);
                Fields.setDst(expected, 0 /* isDst */, PstPdt.getUtcOffsetSeconds(0));
            } else {
                fail("i =" + i);
            }
            Fields.setDerivedDateTime(expected, 307, 0);
            Fields.verifyTimeEquals("i = " + i, expected, time);

            // isDst = 0, normalize(false)
            Fields.setDateTime(time, dateTimeArgs);
            Fields.setDst(time, 0 /* isDst */, 9);
            Fields.setDerivedDateTime(time, 9, 9);

            timeMillis = time.normalize(false);
            long expectedTimeMillis = timeBaseMillis + minutesInMillis + dstCorrectionMillis;
            assertEquals("i = " + i, expectedTimeMillis, timeMillis);
            if (i == -1) {
                Fields.setDateTime(expected, normalizedAdjustedForwardDateTimeArgs);
                Fields.setDst(expected, 1 /* isDst */, PstPdt.getUtcOffsetSeconds(1));
            } else {
                Fields.setDateTime(expected, normalizedDateTimeArgs);
                Fields.setDst(expected, 0 /* isDst */, PstPdt.getUtcOffsetSeconds(0));
            }
            Fields.setDerivedDateTime(expected, 307, 0);
            Fields.verifyTimeEquals("i = " + i, expected, time);

            // isDst = 1, normalize(true)
            Fields.setDateTime(time, dateTimeArgs);
            Fields.setDst(time, 1 /* isDst */, 9);
            Fields.setDerivedDateTime(time, 9, 9);

            timeMillis = time.normalize(true);
            Fields.setDateTime(expected, normalizedDateTimeArgs);
            // When ignoreDst == true the choice between DST and STD is arbitrary when both answers
            // are possible.
            if (timeMillis == timeBaseMillis + minutesInMillis) {
                // i == 60 is unambiguous
                assertTrue("i =" + i, i < 60);
                Fields.setDst(expected, 1 /* isDst */, PstPdt.getUtcOffsetSeconds(1));
            } else if (timeMillis == timeBaseMillis + minutesInMillis + dstCorrectionMillis) {
                // i == -1 is unambiguous
                assertTrue("i =" + i, i > -1);
                Fields.setDst(expected, 0 /* isDst */, PstPdt.getUtcOffsetSeconds(0));
            } else {
                fail("i =" + i);
            }
            Fields.setDerivedDateTime(expected, 307, 0);
            Fields.verifyTimeEquals("i = " + i, expected, time);

            // isDst = 1, normalize(false)
            Fields.setDateTime(time, dateTimeArgs);
            Fields.setDst(time, 1 /* isDst */, 9);
            Fields.setDerivedDateTime(time, 9, 9);

            timeMillis = time.normalize(false);
            expectedTimeMillis = timeBaseMillis + minutesInMillis;
            if (i == 60) {
                Fields.setDateTime(expected, normalizedAdjustedBackwardDateTimeArgs);
                Fields.setDst(expected, 0 /* isDst */, PstPdt.getUtcOffsetSeconds(0));
            } else {
                Fields.setDateTime(expected, normalizedDateTimeArgs);
                Fields.setDst(expected, 1 /* isDst */, PstPdt.getUtcOffsetSeconds(1));
            }
            Fields.setDerivedDateTime(expected, 307, 0);
            assertEquals("i = " + i, expectedTimeMillis, timeMillis);
            Fields.verifyTimeEquals("i = " + i, expected, time);

            // isDst = -1, normalize(true)
            Fields.setDateTime(time, dateTimeArgs);
            Fields.setDst(time, -1 /* isDst */, 9);
            Fields.setDerivedDateTime(time, 9, 9);

            timeMillis = time.normalize(true);
            Fields.setDateTime(expected, normalizedDateTimeArgs);
            // When isDst == -1 the choice between DST and STD is arbitrary when both answers
            // are possible.
            if (timeMillis == timeBaseMillis + minutesInMillis) {
                // i == 60 is unambiguous
                assertTrue("i =" + i, i < 60);
                Fields.setDst(expected, 1 /* isDst */, PstPdt.getUtcOffsetSeconds(1));
            } else if (timeMillis == timeBaseMillis + minutesInMillis + dstCorrectionMillis) {
                // i == -1 is unambiguous
                assertTrue("i =" + i, i > -1);
                Fields.setDst(expected, 0 /* isDst */, PstPdt.getUtcOffsetSeconds(0));
            } else {
                fail("i =" + i);
            }
            Fields.setDerivedDateTime(expected, 307, 0);
            Fields.verifyTimeEquals("i = " + i, expected, time);

            // isDst = -1, normalize(false)
            Fields.setDateTime(time, dateTimeArgs);
            Fields.setDst(time, -1 /* isDst */, 9);
            Fields.setDerivedDateTime(time, 9, 9);

            timeMillis = time.normalize(false);
            // When isDst == -1 the choice between DST and STD is arbitrary when both answers
            // are possible.
            if (timeMillis == timeBaseMillis + minutesInMillis) {
                // i == 60 is unambiguous
                assertTrue("i =" + i, i < 60);
                Fields.setDst(expected, 1 /* isDst */, PstPdt.getUtcOffsetSeconds(1));
            } else if (timeMillis == timeBaseMillis + minutesInMillis + dstCorrectionMillis) {
                // i == -1 is unambiguous
                assertTrue("i =" + i, i > -1);
                Fields.setDst(expected, 0 /* isDst */, PstPdt.getUtcOffsetSeconds(0));
            } else {
                fail("i =" + i);
            }
            Fields.setDerivedDateTime(expected, 307, 0);
            Fields.verifyTimeEquals("i = " + i, expected, time);
        }
    }

    @Test
    public void testNormalize_beforeTzRecords() {
        int[] timeFields = new int[] { 1900, 0, 1, 2, 3, 4, -999 /* not used */, 9, 9, 9 };
        verifyNormalizeInvalid(timeFields, PstPdt.ID);
        verifyNormalizeInvalid(timeFields, Time.TIMEZONE_UTC);
    }

    private static void verifyNormalizeInvalid(int[] timeFields, String timezone) {
        Time time = new Time(timezone);
        Time expected = new Time(timezone);

        // isDst = 0, normalize(true)
        Fields.set(time, timeFields);
        time.isDst = 0;
        Fields.set(expected, timeFields);
        expected.isDst = -1;
        verifyNormalizeResult(true, time, expected, -1);

        // isDst = 0, normalize(false)
        Fields.set(time, timeFields);
        time.isDst = 0;
        Fields.set(expected, timeFields);
        expected.isDst = 0;
        verifyNormalizeResult(false, time, expected, -1);

        // isDst = 1, normalize(true)
        Fields.set(time, timeFields);
        time.isDst = 1;
        Fields.set(expected, timeFields);
        expected.isDst = -1;
        verifyNormalizeResult(true, time, expected, -1);

        // isDst = 1, normalize(false)
        Fields.set(time, timeFields);
        time.isDst = 1;
        Fields.set(expected, timeFields);
        expected.isDst = 1;
        verifyNormalizeResult(false, time, expected, -1);

        // isDst = -1, normalize(true)
        Fields.set(time, timeFields);
        time.isDst = -1;
        Fields.set(expected, timeFields);
        expected.isDst = -1;
        verifyNormalizeResult(true, time, expected, -1);

        // isDst = -1, normalize(false)
        Fields.set(time, timeFields);
        time.isDst = -1;
        Fields.set(expected, timeFields);
        expected.isDst = -1;
        verifyNormalizeResult(false, time, expected, -1);
    }

    @Test
    public void testNormalize_afterTzRecords() {
        int[] timeFields = new int[] { 2039, 0, 1, 2, 3, 4, -999 /* not used */, 9, 9, 9 };
        verifyNormalizeInvalid(timeFields, PstPdt.ID);
        verifyNormalizeInvalid(timeFields, Time.TIMEZONE_UTC);
    }

    @Test
    public void testNormalize_invalid() {
        int[] timeFields = new int[] { 0, 0, 0, 0, 0, 0, -999 /* not used */, 9, 9, 9 };
        verifyNormalizeInvalid(timeFields, PstPdt.ID);
        verifyNormalizeInvalid(timeFields, Time.TIMEZONE_UTC);
    }

    @Test
    public void testNormalize_dstToDstSkip() {
        // In London, 4th May 1941 02:00 - 03:00 was a skip from DST -> DST (+1 hour -> +2 hours)
        String timezone = "Europe/London";
        Time t = new Time(timezone);
        Time expected = new Time(timezone);

        // Demonstrate the data we expect either side of the skipped interval: 01:59
        Fields.set(t, 1941, 4, 4, 1, 59, 0, -1 /* isDst */, 9, 9, 9);
        Fields.set(expected, 1941, 4, 4, 1, 59, 0, 1 /* isDst */, 3600, 123, 0);
        verifyNormalizeResult(true, t, expected, -904518060000L);

        // Demonstrate the data we expect either side of the skipped interval: 03:00
        Fields.set(t, 1941, 4, 4, 3, 0, 0, -1 /* isDst */, 9, 9, 9);
        Fields.set(expected, 1941, 4, 4, 3, 0, 0, 1 /* isDst */, 7200, 123, 0);
        verifyNormalizeResult(true, t, expected, -904518000000L);

        // isDst = 1, normalize(false)
        Fields.set(t, 1941, 4, 4, 2, 30, 0, 1 /* isDst */, 9, 9, 9);
        Fields.set(expected, 1941, 4, 4, 2, 30, 0, 1 /* isDst */, 9, 9, 9);
        verifyNormalizeResult(false, t, expected, -1);

        // isDst = -1, normalize(false)
        Fields.set(t, 1941, 4, 4, 2, 30, 0, -1 /* isDst */, 9, 9, 9);
        Fields.set(expected, 1941, 4, 4, 2, 30, 0, -1 /* isDst */, 9, 9, 9);
        verifyNormalizeResult(false, t, expected, -1);

        // The results below are potentially arbitrary: 01:30 and 02:30 are not a valid standard
        // times so normalize() must apply one of the possible STD -> DST adjustments to arrive at a
        // date / time.

        // isDst = 0, normalize(false) @ 01:30
        Fields.set(t, 1941, 4, 4, 1, 30, 0, 0 /* isDst */, 9, 9, 9);
        Fields.set(expected, 1941, 4, 4, 3, 30, 0, 1 /* isDst */, 7200, 123, 0);
        verifyNormalizeResult(false, t, expected, -904516200000L);

        // isDst = 0, normalize(false) @ 02:30
        Fields.set(t, 1941, 4, 4, 2, 30, 0, 0 /* isDst */, 9, 9, 9);
        long timeMillis = t.normalize(false);

        if (timeMillis == -904516200000L) {
            // The original C implementation chooses this one.
            Fields.set(expected, 1941, 4, 4, 3, 30, 0, 1 /* isDst */, 7200, 123, 0);
        } else if (timeMillis == -904512600000L) {
            Fields.set(expected, 1941, 4, 4, 4, 30, 0, 1 /* isDst */, 7200, 123, 0);
        } else {
            fail();
        }
        Fields.verifyTimeEquals(expected, t);
    }

    @Test
    public void testNormalize_dstToDstRepeat() {
        // In London, 10th August 1941 02:00 - 03:00 was a repeat from DST -> DST
        // (+2 hour -> +1 hour)
        String timezone = "Europe/London";
        Time t = new Time(timezone);
        Time expected = new Time(timezone);

        // Demonstrate the data we expect during the repeated interval: 02:30 (first)
        t.set(-896052600000L);
        Fields.set(expected, 1941, 7, 10, 2, 30, 0, 1 /* isDst */, 7200, 221, 0);
        Fields.verifyTimeEquals(expected, t);

        // Demonstrate the data we expect during the repeated interval: 02:30 (second)
        t.set(-896049000000L);
        Fields.set(expected, 1941, 7, 10, 2, 30, 0, 1 /* isDst */, 3600, 221, 0);
        Fields.verifyTimeEquals(expected, t);

        // Now check times in the repeated hour with different isDst assertions...

        // isDst = 1, normalize(false) @ 02:30
        Fields.set(t, 1941, 7, 10, 2, 30, 0, 1 /* isDst */, 9, 9, 9);
        Fields.set(expected, 1941, 7, 10, 2, 30, 0, 1 /* isDst */, 3600, 221, 0);
        verifyNormalizeResult(false, t, expected, -896049000000L);

        // isDst = -1, normalize(false) @ 02:30
        Fields.set(t, 1941, 7, 10, 2, 30, 0, -1 /* isDst */, 9, 9, 9);
        Fields.set(expected, 1941, 7, 10, 2, 30, 0, 1 /* isDst */, 3600, 221, 0);
        verifyNormalizeResult(false, t, expected, -896049000000L);

        // The results below are potentially arbitrary: 01:30 and 02:30 are not a valid standard
        // times so normalize() must apply one of the possible STD -> DST adjustments to arrive at a
        // date / time.

        // isDst = 0, normalize(false) @ 01:30
        Fields.set(t, 1941, 7, 10, 1, 30, 0, 0 /* isDst */, 9, 9, 9);
        long timeMillis = t.normalize(false);
        if (timeMillis == -896052600000L) {
            // The original C implementation chooses this one.
            Fields.set(expected, 1941, 7, 10, 2, 30, 0, 1 /* isDst */, 7200, 221, 0);
        } else if (timeMillis == -896049000000L) {
            Fields.set(expected, 1941, 7, 10, 2, 30, 0, 1 /* isDst */, 3600, 221, 0);
        } else {
            fail();
        }
        Fields.verifyTimeEquals(expected, t);

        // isDst = 0, normalize(false) @ 02:30
        Fields.set(t, 1941, 7, 10, 2, 30, 0, 0 /* isDst */, 9, 9, 9);
        Fields.set(expected, 1941, 7, 10, 3, 30, 0, 1 /* isDst */, 3600, 221, 0);
        verifyNormalizeResult(false, t, expected, -896045400000L);
    }

    @Test
    public void testNormalize_stdToStdRepeat() {
        // In London, 31st October 1971 02:00 - 03:00 was a repeat from STD -> STD
        String timezone = "Europe/London";
        Time t = new Time(timezone);
        Time expected = new Time(timezone);

        // Demonstrate the data we expect during the repeated interval: 02:30 (first)
        t.set(57720600000L);
        Fields.set(expected, 1971, 9, 31, 2, 30, 0, 0 /* isDst */, 3600, 303, 0);
        Fields.verifyTimeEquals(expected, t);

        // Demonstrate the data we expect during the repeated interval: 02:30 (second)
        t.set(57724200000L);
        Fields.set(expected, 1971, 9, 31, 2, 30, 0, 0 /* isDst */, 0, 303, 0);
        Fields.verifyTimeEquals(expected, t);

        // isDst = 0, normalize(false) @ 02:30
        Fields.set(t, 1971, 9, 31, 2, 30, 0, 0 /* isDst */, 9, 9, 9);

        long timeMillis = t.normalize(false);

        // Either answer is valid: the choice is arbitrary.
        if (57720600000L == timeMillis) {
            // The original C implementation chooses this one.
            Fields.set(expected, 1971, 9, 31, 2, 30, 0, 0 /* isDst */, 3600, 303, 0);
        } else if (57724200000L == timeMillis) {
            Fields.set(expected, 1971, 9, 31, 2, 30, 0, 0 /* isDst */, 0, 303, 0);
        } else {
            fail();
        }
        Fields.verifyTimeEquals(expected, t);

        // isDst = -1, normalize(false) @ 02:30
        Fields.set(t, 1971, 9, 31, 2, 30, 0, -1 /* isDst */, 9, 9, 9);

        timeMillis = t.normalize(false);

        Fields.setDateTime(expected, 1971, 9, 31, 2, 30, 0);
        // Either answer is valid: the choice is arbitrary.
        if (57720600000L == timeMillis) {
            // The original C implementation chooses this one.
            Fields.set(expected, 1971, 9, 31, 2, 30, 0, 0 /* isDst */, 3600, 303, 0);
        } else if (57724200000L == timeMillis) {
            Fields.set(expected, 1971, 9, 31, 2, 30, 0, 0 /* isDst */, 0, 303, 0);
        } else {
            fail();
        }
        Fields.verifyTimeEquals(expected, t);

        // The results below are potentially arbitrary: 01:30 and 02:30 are not a valid DST
        // so normalize() must apply one of the possible STD -> DST adjustments to arrive at a
        // date / time.

        // isDst = 1, normalize(false) @ 01:30
        Fields.set(t, 1971, 9, 31, 1, 30, 0, 1 /* isDst */, 9, 9, 9);

        timeMillis = t.normalize(false);

        if (timeMillis == 57713400000L) {
            // Original C implementation chooses this one.
            Fields.set(expected, 1971, 9, 31, 0, 30, 0, 0 /* isDst */, 3600, 303, 0);
        } else if (timeMillis == 57717000000L) {
            Fields.set(expected, 1971, 9, 31, 1, 30, 0, 0 /* isDst */, 3600, 303, 0);
        } else {
            fail();
        }
        Fields.verifyTimeEquals(expected, t);

        // isDst = 1, normalize(false) @ 02:30
        Fields.set(t, 1971, 9, 31, 2, 30, 0, 1 /* isDst */, 9, 9, 9);
        timeMillis = t.normalize(false);
        if (timeMillis == 57717000000L) {
            // The original C implementation chooses this one.
            Fields.set(expected, 1971, 9, 31, 1, 30, 0, 0 /* isDst */, 3600, 303, 0);
        } else if (timeMillis == 57720600000L) {
            Fields.set(expected, 1971, 9, 31, 2, 30, 0, 0 /* isDst */, 3600, 303, 0);
        } else {
            fail();
        }
        Fields.verifyTimeEquals(expected, t);

        // isDst = 1, normalize(false) @ 03:30
        Fields.set(t, 1971, 9, 31, 3, 30, 0, 1 /* isDst */, 9, 9, 9);
        Fields.set(expected, 1971, 9, 31, 2, 30, 0, 0 /* isDst */, 0, 303, 0);
        verifyNormalizeResult(false, t, expected, 57724200000L);
    }

    @Test
    public void testNormalize_stdToStdSkip() {
        // In Kiritimati, 1st Jan 1995 10:00 - 10:40 was a skip from STD -> STD (plus they do not
        // observe DST).
        String timezone = "Pacific/Kiritimati";
        Time t = new Time(timezone);
        Time expected = new Time(timezone);

        // isDst = 0, normalize(false)
        Fields.set(t, 1995, 0, 1, 10, 20, 0, 0 /* isDst */, 9, 9, 9);
        Fields.set(expected, 1995, 0, 1, 10, 20, 0, 0 /* isDst */, 9, 9, 9);
        verifyNormalizeResult(false, t, expected, -1);

        // isDst = 1, normalize(false)
        Fields.set(t, 1995, 0, 1, 10, 20, 0, 1 /* isDst */, 9, 9, 9);
        Fields.set(expected, 1995, 0, 1, 10, 20, 0, 1 /* isDst */, 9, 9, 9);
        verifyNormalizeResult(false, t, expected, -1);

        // isDst = -1, normalize(false)
        Fields.set(t, 1995, 0, 1, 10, 20, 0, -1 /* isDst */, 9, 9, 9);
        Fields.set(expected, 1995, 0, 1, 10, 20, 0, -1 /* isDst */, 9, 9, 9);
        verifyNormalizeResult(false, t, expected, -1);
    }

    @Test
    public void testNormalize_utcWithDst() {
        // In UTC (or other zone without DST), what happens when a DST time is specified and there
        // is no DST offset available in the timezone data.
        Time t = new Time(Time.TIMEZONE_UTC);
        Time expected = new Time(Time.TIMEZONE_UTC);

        // isDst = 1, normalize(false)
        Fields.set(t, 2005, 6, 22, 1, 30, 0, 1 /* isDst */, 9, 9, 9);
        Fields.set(expected, 2005, 6, 22, 1, 30, 0, 1 /* isDst */, 9, 9, 9);
        verifyNormalizeResult(false, t, expected, -1);

        // isDst = -1, normalize(false)
        Fields.set(t, 2005, 6, 22, 1, 30, 0, -1 /* isDst */, 9, 9, 9);
        Fields.set(expected, 2005, 6, 22, 1, 30, 0, 0 /* isDst */, 0, 202, 5);
        verifyNormalizeResult(false, t, expected, 1121995800000L);
    }

    @Test
    public void testUnknownTz() {
        // Historically the code used UTC if the timezone is unrecognized.

        String unknownTimezoneId = "THIS_ID_IS_NOT_A_VALID_TZ";
        Time t = new Time(unknownTimezoneId);
        assertEquals(unknownTimezoneId, t.timezone);
        Fields.set(t, 2007, 5, 1, 1, 2, 3, -1 /* isDst */, 9, 9, 9);

        // We can't know for sure which time zone is being used, but we assume it is UTC if the date
        // normalizes to isDst == 0 and with an offset of 0 during the summer months.

        long timeMillis = t.normalize(true);
        assertEquals(unknownTimezoneId, t.timezone);
        assertEquals(1180659723000L, timeMillis);

        Time expected = new Time(unknownTimezoneId);
        Fields.set(expected, 2007, 5, 1, 1, 2, 3, 0 /* isDst */, 0, 151, 5);
        Fields.verifyTimeEquals(expected, t);
    }

    private static void verifyNormalizeResult(boolean normalizeArgument, Time toNormalize,
            Time expectedTime, long expectedTimeMillis) {
        long actualTimeMillis = toNormalize.normalize(normalizeArgument /* ignore isDst */);
        assertEquals(expectedTimeMillis, actualTimeMillis);
        Fields.verifyTimeEquals(expectedTime, toNormalize);
    }

    /** A helper class for manipulating / testing fields on Time objects. */
    private static class Fields {
        final static int MAIN_DATE_TIME = 1;
        final static int DST_FIELDS = 2;
        final static int DERIVED_DATE_TIME = 4;

        final static int ALL = MAIN_DATE_TIME | DST_FIELDS | DERIVED_DATE_TIME;

        public static void verifyTimeEquals(Time expected, Time actual) {
            verifyTimeEquals("", ALL, expected, actual);
        }

        public static void verifyTimeEquals(String message, Time expected, Time actual) {
            verifyTimeEquals(message, Fields.ALL, expected, actual);
        }

        public static void verifyTimeEquals(String message, int fields, Time expected,
                Time actual) {
            boolean mainDateTimeOk = (fields & Fields.MAIN_DATE_TIME) == 0
                    || (Objects.equals(expected.timezone, actual.timezone)
                    && expected.year == actual.year
                    && expected.month == actual.month
                    && expected.monthDay == actual.monthDay
                    && expected.hour == actual.hour
                    && expected.minute == actual.minute
                    && expected.second == actual.second
                    && expected.allDay == actual.allDay);

            boolean dstFieldsOk = (fields & Fields.DST_FIELDS) == 0
                    || (expected.isDst == actual.isDst && expected.gmtoff == actual.gmtoff);

            boolean derivedDateTimeOk = (fields & Fields.DERIVED_DATE_TIME) == 0
                    || (expected.yearDay == actual.yearDay && expected.weekDay == actual.weekDay);

            if (!mainDateTimeOk || !dstFieldsOk || !derivedDateTimeOk) {
                String expectedTime = timeToString(fields, expected);
                String actualTime = timeToString(fields, actual);
                fail(message + " [Time fields differed. Expected: " + expectedTime + "Actual: "
                        + actualTime + "]");
            }
        }

        private static String timeToString(int fields, Time time) {
            List<Object> values = new ArrayList<>();
            StringBuilder format = new StringBuilder();
            if ((fields & Fields.MAIN_DATE_TIME) > 0) {
                format.append("%d-%02d-%02d %02d:%02d:%02d allDay=%b timezone=%s ");
                values.addAll(
                        Arrays.asList(time.year, time.month, time.monthDay, time.hour, time.minute,
                                time.second, time.allDay, time.timezone));
            }
            if ((fields & Fields.DST_FIELDS) > 0) {
                format.append("isDst=%d, gmtoff=%d ");
                values.add(time.isDst);
                values.add(time.gmtoff);
            }
            if ((fields & Fields.DERIVED_DATE_TIME) > 0) {
                format.append("yearDay=%d, weekDay=%d ");
                values.add(time.yearDay);
                values.add(time.weekDay);

            }
            return String.format(format.toString(), values.toArray());
        }

        public static void setDateTime(Time t, int year, int month, int monthDay, int hour,
                int minute, int second) {
            t.year = year;
            t.month = month;
            t.monthDay = monthDay;
            t.hour = hour;
            t.minute = minute;
            t.second = second;
            t.allDay = false;
        }

        public static void setDateTime(Time t, int[] args) {
            assertEquals(6, args.length);
            setDateTime(t, args[0], args[1], args[2], args[3], args[4], args[5]);
        }

        public static void setDst(Time t, int isDst, int gmtoff) {
            t.isDst = isDst;
            t.gmtoff = gmtoff;
        }

        public static void setDerivedDateTime(Time t, int yearDay, int weekDay) {
            t.yearDay = yearDay;
            t.weekDay = weekDay;
        }

        public static void setAllDayDate(Time t, int year, int month, int monthDay) {
            t.year = year;
            t.month = month;
            t.monthDay = monthDay;
            t.allDay = true;
        }

        public static void set(Time t, int[] args) {
            assertEquals(10, args.length);
            set(t, args[0], args[1], args[2], args[3], args[4], args[5], args[6], args[7], args[8],
                    args[9]);
        }

        public static void set(Time t, int year, int month, int monthDay, int hour, int minute,
                int second, int isDst, int gmtoff, int yearDay, int weekDay) {
            setDateTime(t, year, month, monthDay, hour, minute, second);
            setDst(t, isDst, gmtoff);
            setDerivedDateTime(t, yearDay, weekDay);
        }
    }

    private static class PstPdt {
        public static final String ID = "America/Los_Angeles";

        public static int getUtcOffsetSeconds(int isDst) {
            if (isDst == 0) {
                return -28800;
            } else if (isDst == 1) {
                return -25200;
            }
            throw new IllegalArgumentException();
        }

        public static int getUtcOffsetSeconds(boolean isDst) {
            return getUtcOffsetSeconds(isDst ? 1 : 0);
        }

        public static int getUtcOffsetMillis(boolean isDst) {
            return getUtcOffsetSeconds(isDst) * 1000;
        }
    }

    private static void maybeInitializeSystemLocales() {
        if (sSystemLocales == null) {
            String[] locales = Resources.getSystem().getAssets().getLocales();
            List<Locale> systemLocales = new ArrayList<Locale>(locales.length);
            for (String localeStr : locales) {
                systemLocales.add(Locale.forLanguageTag(localeStr));
            }

            sSystemLocales = systemLocales;
        }
    }

    private static boolean changeJavaAndAndroidLocale(Locale locale, boolean force) {
        // The Time class uses the Android-managed locale for string resources containing format
        // patterns and the Java-managed locale for other things (e.g. month names, week days names)
        // that are placed into those patterns. For example the format "%c" expands to include
        // a pattern that includes month names.
        // Changing the Java-managed Locale does not affect the Android-managed locale.
        // Changing the Android-managed locale does not affect the Java-managed locale.
        //
        // In order to ensure consistent behavior in the tests the device Locale must not be
        // assumed. To simulate the most common behavior (i.e. when the Java and the Android-managed
        // locales agree), when the Java-managed locale is changed during this test the locale in
        // the runtime-local copy of the system resources is modified as well.

        if (!force && !sSystemLocales.contains(locale)) {
            return false;
        }

        // Change the Java-managed locale.
        Locale.setDefault(locale);

        // Change the local copy of the Android system configuration: this simulates the device
        // being set to the locale and forces a reload of the string resources.
        Configuration configuration = Resources.getSystem().getConfiguration();
        configuration.locale = locale;
        Resources.getSystem().updateConfiguration(configuration, null);
        return true;
    }
}
