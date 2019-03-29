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
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import android.app.UiAutomation;
import android.content.Context;
import android.os.ParcelFileDescriptor;
import android.provider.Settings;
import android.support.annotation.NonNull;
import android.support.test.InstrumentationRegistry;
import android.support.test.filters.LargeTest;
import android.support.test.runner.AndroidJUnit4;
import android.text.format.DateFormat;

import com.android.compatibility.common.util.SystemUtil;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Date;
import java.util.GregorianCalendar;
import java.util.Locale;
import java.util.Scanner;
import java.util.TimeZone;

@LargeTest
@RunWith(AndroidJUnit4.class)
public class DateFormatTest {
    private static final String TIME_FORMAT_12 = "12";
    private static final String TIME_FORMAT_24 = "24";

    // Date: 2008-12-18 05:30
    private static final int YEAR_FROM_1900 = 108;
    private static final int YEAR = 2008;
    private static final int MONTH = Calendar.DECEMBER; // java.util.Calendar months are 0-based.
    private static final int DAY = 18;
    private static final int HOUR = 5;
    private static final int MINUTE = 30;

    private Context mContext;

    private String mDefaultTimeFormat;
    private Locale mDefaultLocale;

    @Before
    public void setup() throws Exception {
        mContext = InstrumentationRegistry.getTargetContext();
        mDefaultTimeFormat = getTimeFormat();
        mDefaultLocale = Locale.getDefault();

        enableAppOps();
    }

    @After
    public void teardown() throws Exception {
        if (!getTimeFormat().equals(mDefaultTimeFormat)) {
            setTimeFormat(mDefaultTimeFormat);
        }
        if ((mDefaultLocale != null) && !Locale.getDefault().equals(mDefaultLocale)) {
            Locale.setDefault(mDefaultLocale);
        }
    }

    private void enableAppOps() {
        UiAutomation uiAutomation = InstrumentationRegistry.getInstrumentation().getUiAutomation();

        StringBuilder cmd = new StringBuilder();
        cmd.append("appops set ");
        cmd.append(mContext.getPackageName());
        cmd.append(" android:write_settings allow");
        uiAutomation.executeShellCommand(cmd.toString());

        StringBuilder query = new StringBuilder();
        query.append("appops get ");
        query.append(mContext.getPackageName());
        query.append(" android:write_settings");
        String queryStr = query.toString();

        String result = "No operations.";
        while (result.contains("No operations")) {
            ParcelFileDescriptor pfd = uiAutomation.executeShellCommand(queryStr);
            InputStream inputStream = new FileInputStream(pfd.getFileDescriptor());
            result = convertStreamToString(inputStream);
        }
    }

    private String convertStreamToString(InputStream is) {
        try (Scanner scanner = new Scanner(is).useDelimiter("\\A")) {
            return scanner.hasNext() ? scanner.next() : "";
        }
    }

    @Test
    public void test_is24HourFormat() throws Exception {
        setTimeFormat(TIME_FORMAT_24);
        assertTrue(DateFormat.is24HourFormat(mContext));
        setTimeFormat(TIME_FORMAT_12);
        assertFalse(DateFormat.is24HourFormat(mContext));
    }

    @Test
    public void test_format_M() {
        Calendar c = new GregorianCalendar(2008, Calendar.DECEMBER, 18);
        assertEquals("D", DateFormat.format("MMMMM", c));
        assertEquals("December", DateFormat.format("MMMM", c));
        assertEquals("Dec", DateFormat.format("MMM", c));
        assertEquals("12", DateFormat.format("MM", c));
        assertEquals("12", DateFormat.format("M", c));
    }

    @Test
    public void test_format_L() {
        // TODO: we can't test other locales with this API so we can't test 'L' properly!
        Calendar c = new GregorianCalendar(2008, Calendar.DECEMBER, 18);
        assertEquals("D", DateFormat.format("LLLLL", c));
        assertEquals("December", DateFormat.format("LLLL", c));
        assertEquals("Dec", DateFormat.format("LLL", c));
        assertEquals("12", DateFormat.format("LL", c));
        assertEquals("12", DateFormat.format("L", c));
    }

    @Test
    public void test_format_E() {
        Calendar c = new GregorianCalendar(2008, Calendar.DECEMBER, 18);
        assertEquals("T", DateFormat.format("EEEEE", c));
        assertEquals("Thursday", DateFormat.format("EEEE", c));
        assertEquals("Thu", DateFormat.format("EEE", c));
        assertEquals("Thu", DateFormat.format("EE", c));
        assertEquals("Thu", DateFormat.format("E", c));
    }

    @Test
    public void test_format_c() {
        // TODO: we can't test other locales with this API, so we can't test 'c' properly!
        Calendar c = new GregorianCalendar(2008, Calendar.DECEMBER, 18);
        assertEquals("T", DateFormat.format("ccccc", c));
        assertEquals("Thursday", DateFormat.format("cccc", c));
        assertEquals("Thu", DateFormat.format("ccc", c));
        assertEquals("Thu", DateFormat.format("cc", c));
        assertEquals("Thu", DateFormat.format("c", c));
    }

    @SuppressWarnings("deprecation")
    @Test
    public void testFormatMethods() throws ParseException {
        if (!mDefaultLocale.equals(Locale.US)) {
            Locale.setDefault(Locale.US);
        }

        java.text.DateFormat dateFormat = DateFormat.getDateFormat(mContext);
        assertNotNull(dateFormat);
        Date date = new Date(YEAR_FROM_1900, MONTH, DAY, HOUR, MINUTE);
        String source = dateFormat.format(date);
        Date parseDate = dateFormat.parse(source);
        assertEquals(date.getYear(), parseDate.getYear());
        assertEquals(date.getMonth(), parseDate.getMonth());
        assertEquals(date.getDay(), date.getDay());

        dateFormat = DateFormat.getLongDateFormat(mContext);
        assertNotNull(dateFormat);
        source = dateFormat.format(date);
        assertTrue(source.indexOf("December") >= 0);
        dateFormat = DateFormat.getMediumDateFormat(mContext);
        assertNotNull(dateFormat);
        source = dateFormat.format(date);
        assertTrue(source.indexOf("Dec") >= 0);
        assertTrue(source.indexOf("December") < 0);
        dateFormat = DateFormat.getTimeFormat(mContext);
        source = dateFormat.format(date);
        assertTrue(source.indexOf("5") >= 0);
        assertTrue(source.indexOf("30") >= 0);

        String format = "MM/dd/yy";
        String expectedString = "12/18/08";
        Calendar calendar = new GregorianCalendar(YEAR, MONTH, DAY);
        CharSequence actual = DateFormat.format(format, calendar);
        assertEquals(expectedString, actual.toString());
        Date formatDate = new Date(YEAR_FROM_1900, MONTH, DAY);
        actual = DateFormat.format(format, formatDate);
        assertEquals(expectedString, actual.toString());
        actual = DateFormat.format(format, formatDate.getTime());
        assertEquals(expectedString, actual.toString());
    }

    @Test
    public void test2038() {
        Calendar calendar = new GregorianCalendar(TimeZone.getTimeZone("GMT+00:00"));

        calendar.setTimeInMillis(((long) Integer.MIN_VALUE + Integer.MIN_VALUE) * 1000L);
        assertEquals("Sun Nov 24 17:31:44 GMT+00:00 1833",
                DateFormat.format("EEE MMM dd HH:mm:ss zzz yyyy", calendar));

        calendar.setTimeInMillis(Integer.MIN_VALUE * 1000L);
        assertEquals("Fri Dec 13 20:45:52 GMT+00:00 1901",
                DateFormat.format("EEE MMM dd HH:mm:ss zzz yyyy", calendar));

        calendar.setTimeInMillis(0L);
        assertEquals("Thu Jan 01 00:00:00 GMT+00:00 1970",
                DateFormat.format("EEE MMM dd HH:mm:ss zzz yyyy", calendar));

        calendar.setTimeInMillis(Integer.MAX_VALUE * 1000L);
        assertEquals("Tue Jan 19 03:14:07 GMT+00:00 2038",
                DateFormat.format("EEE MMM dd HH:mm:ss zzz yyyy", calendar));

        calendar.setTimeInMillis((2L + Integer.MAX_VALUE + Integer.MAX_VALUE) * 1000L);
        assertEquals("Sun Feb 07 06:28:16 GMT+00:00 2106",
                DateFormat.format("EEE MMM dd HH:mm:ss zzz yyyy", calendar));
    }

    private static void checkFormat(String expected, String pattern, int hour) {
        TimeZone utc = TimeZone.getTimeZone("UTC");

        Calendar c = new GregorianCalendar(utc);
        c.set(2013, Calendar.JANUARY, 1, hour, 00);

        SimpleDateFormat sdf = new SimpleDateFormat(pattern);
        sdf.setTimeZone(utc);

        if (pattern.equals("k") && (hour == 0 || hour == 24)) {
          // http://b/8359981: 'k' has always been broken on Android, and we keep it broken
          // for compatibility. Maybe one day we'll be able to fix this...
          assertEquals("0", DateFormat.format(pattern, c));
        } else {
          assertEquals(expected, DateFormat.format(pattern, c));
        }
        assertEquals(expected, sdf.format(c.getTime()));
    }

    @Test
    public void test_bug_8359981() {
        checkFormat("24", "k", 00);
        checkFormat( "0", "K", 00);
        checkFormat("12", "h", 00);
        checkFormat( "0", "H", 00);

        checkFormat( "1", "k", 01);
        checkFormat( "1", "K", 01);
        checkFormat( "1", "h", 01);
        checkFormat( "1", "H", 01);

        checkFormat("12", "k", 12);
        checkFormat( "0", "K", 12);
        checkFormat("12", "h", 12);
        checkFormat("12", "H", 12);

        checkFormat("13", "k", 13);
        checkFormat( "1", "K", 13);
        checkFormat( "1", "h", 13);
        checkFormat("13", "H", 13);

        checkFormat("24", "k", 24);
        checkFormat( "0", "K", 24);
        checkFormat("12", "h", 24);
        checkFormat( "0", "H", 24);
    }

    @Test
    public void test_bug_82144() {
        for (Locale locale : Locale.getAvailableLocales()) {
            Locale.setDefault(locale);
            char[] order = DateFormat.getDateFormatOrder(mContext);
            boolean seenDay = false, seenMonth = false, seenYear = false;
            for (char c : order) {
                switch (c) {
                    case 'd':
                        seenDay = true;
                        break;
                    case 'M':
                        seenMonth = true;
                        break;
                    case 'y':
                        seenYear = true;
                        break;
                    default:
                        fail("Unknown character: " + c + " in " + Arrays.toString(order)
                                + " for " + locale);
                        break;
                }
            }
            assertTrue(locale.toString() + " day not found", seenDay);
            assertTrue(locale.toString() + " month not found", seenMonth);
            assertTrue(locale.toString() + " year not found", seenYear);
        }
    }

    @NonNull
    private String getTimeFormat() throws IOException {
        return SystemUtil.runShellCommand(InstrumentationRegistry.getInstrumentation(),
                "settings get system " + Settings.System.TIME_12_24).trim();
    }

    private void setTimeFormat(@NonNull String timeFormat) throws IOException {
        if ("null".equals(timeFormat)) {
            SystemUtil.runShellCommand(InstrumentationRegistry.getInstrumentation(),
                    "settings delete system " + Settings.System.TIME_12_24);
        } else {
            SystemUtil.runShellCommand(InstrumentationRegistry.getInstrumentation(),
                    "settings put system " + Settings.System.TIME_12_24 + " " + timeFormat);
        }
    }
}
