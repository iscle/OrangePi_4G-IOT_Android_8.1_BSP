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

package android.util.cts;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import android.support.test.filters.SmallTest;
import android.support.test.runner.AndroidJUnit4;
import android.util.MonthDisplayHelper;

import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.Calendar;

@SmallTest
@RunWith(AndroidJUnit4.class)
public class MonthDisplayHelperTest {
    @Test
    public void testConstructor() {
        new MonthDisplayHelper(2008, Calendar.DECEMBER, Calendar.MONDAY);
        new MonthDisplayHelper(2008, Calendar.DECEMBER);
    }

    @Test(expected=IllegalArgumentException.class)
    public void testConstructorInvalidDay1() {
        new MonthDisplayHelper(2008, Calendar.DECEMBER, Calendar.SUNDAY - 1);
    }

    @Test(expected=IllegalArgumentException.class)
    public void testConstructorInvalidDay2() {
        new MonthDisplayHelper(2008, Calendar.DECEMBER, Calendar.SATURDAY + 1);
    }

    @Test(expected=IllegalArgumentException.class)
    public void testConstructorInvalidYearAndDay() {
        new MonthDisplayHelper(-1, Calendar.DECEMBER, Calendar.SATURDAY + 1);
    }

    @Test(expected=IllegalArgumentException.class)
    public void testConstructorInvalidYearAndMonthAndDay() {
        new MonthDisplayHelper(-1, Calendar.DECEMBER + 1, Calendar.SATURDAY + 1);
    }

    @Test
    public void testNumberOfDaysInCurrentMonth() {
        assertEquals(30,
                new MonthDisplayHelper(2007, Calendar.SEPTEMBER).getNumberOfDaysInMonth());
        assertEquals(28,
                new MonthDisplayHelper(2007, Calendar.FEBRUARY).getNumberOfDaysInMonth());
        assertEquals(29,
                new MonthDisplayHelper(2008, Calendar.FEBRUARY).getNumberOfDaysInMonth());
    }

    @Test
    public void testNextMonth() {
        MonthDisplayHelper helper = new MonthDisplayHelper(2007, Calendar.AUGUST, Calendar.SUNDAY);

        assertArrayEquals(new int[] { 29, 30, 31, 1, 2, 3, 4 }, helper.getDigitsForRow(0));

        helper.nextMonth();

        assertEquals(Calendar.SEPTEMBER, helper.getMonth());
        assertArrayEquals(new int[] { 26, 27, 28, 29, 30, 31, 1 }, helper.getDigitsForRow(0));
    }

    @Test
    public void testGetRowOf() {
        MonthDisplayHelper helper = new MonthDisplayHelper(2007, Calendar.AUGUST, Calendar.SUNDAY);

        assertEquals(0, helper.getRowOf(2));
        assertEquals(0, helper.getRowOf(4));
        assertEquals(2, helper.getRowOf(12));
        assertEquals(2, helper.getRowOf(18));
        assertEquals(3, helper.getRowOf(19));
    }

    @Test
    public void testHelperProperties() {
        MonthDisplayHelper helper = new MonthDisplayHelper(2007, Calendar.AUGUST, Calendar.SUNDAY);

        assertEquals(1, helper.getWeekStartDay());
        assertEquals(3, helper.getOffset());
        helper = new MonthDisplayHelper(2007, Calendar.AUGUST);
        assertEquals(1, helper.getWeekStartDay());
        assertEquals(3, helper.getOffset());
    }

    @Test
    public void testMonthRows() {
        MonthDisplayHelper helper = new MonthDisplayHelper(2007, Calendar.SEPTEMBER);

        assertArrayEquals(new int[] { 26, 27, 28, 29, 30, 31, 1 }, helper
                .getDigitsForRow(0));
        assertArrayEquals(new int[] { 2, 3, 4, 5, 6, 7, 8 }, helper
                .getDigitsForRow(1));
        assertArrayEquals(new int[] { 30, 1, 2, 3, 4, 5, 6 }, helper
                .getDigitsForRow(5));

        helper = new MonthDisplayHelper(2007, Calendar.SEPTEMBER, Calendar.MONDAY);

        assertArrayEquals(new int[] { 27, 28, 29, 30, 31, 1, 2 }, helper
                .getDigitsForRow(0));
        assertArrayEquals(new int[] { 3, 4, 5, 6, 7, 8, 9 }, helper
                .getDigitsForRow(1));
        assertArrayEquals(new int[] { 24, 25, 26, 27, 28, 29, 30 }, helper
                .getDigitsForRow(4));
        assertArrayEquals(new int[] { 1, 2, 3, 4, 5, 6, 7 }, helper
                .getDigitsForRow(5));
    }

    @Test
    public void testFirstDayOfMonth() {
        assertEquals("august 2007", Calendar.WEDNESDAY, new MonthDisplayHelper(
                2007, Calendar.AUGUST).getFirstDayOfMonth());

        assertEquals("september, 2007", Calendar.SATURDAY,
                new MonthDisplayHelper(2007, Calendar.SEPTEMBER)
                        .getFirstDayOfMonth());
    }

    @Test
    public void testGetColumnOf() {
        MonthDisplayHelper helper = new MonthDisplayHelper(2007, Calendar.AUGUST, Calendar.SUNDAY);

        assertEquals(3, helper.getColumnOf(1));
        assertEquals(4, helper.getColumnOf(9));
        assertEquals(5, helper.getColumnOf(17));
        assertEquals(6, helper.getColumnOf(25));
        assertEquals(0, helper.getColumnOf(26));
    }

    @Test
    public void testGetDayAt() {
        MonthDisplayHelper helper = new MonthDisplayHelper(2007, Calendar.AUGUST, Calendar.SUNDAY);

        assertEquals(30, helper.getDayAt(0, 1));
    }

    @Test
    public void testPrevMonth() {
        MonthDisplayHelper mHelper = new MonthDisplayHelper(2007, Calendar.SEPTEMBER,
                Calendar.SUNDAY);

        assertArrayEquals(new int[] { 26, 27, 28, 29, 30, 31, 1 }, mHelper.getDigitsForRow(0));

        mHelper.previousMonth();

        assertEquals(Calendar.AUGUST, mHelper.getMonth());
        assertArrayEquals(new int[] { 29, 30, 31, 1, 2, 3, 4 }, mHelper.getDigitsForRow(0));

        mHelper = new MonthDisplayHelper(2007, Calendar.JANUARY);

        mHelper.previousMonth();

        assertEquals(2006, mHelper.getYear());
        assertEquals(Calendar.DECEMBER, mHelper.getMonth());
    }

    @Test
    public void testIsWithinCurrentMonth() {
        MonthDisplayHelper mHelper = new MonthDisplayHelper(2007, Calendar.SEPTEMBER,
                Calendar.SUNDAY);

        // out of bounds
        assertFalse(mHelper.isWithinCurrentMonth(-1, 3));
        assertFalse(mHelper.isWithinCurrentMonth(6, 3));
        assertFalse(mHelper.isWithinCurrentMonth(2, -1));
        assertFalse(mHelper.isWithinCurrentMonth(2, 7));

        // last day of previous month
        assertFalse(mHelper.isWithinCurrentMonth(0, 5));

        // first day of next month
        assertFalse(mHelper.isWithinCurrentMonth(5, 1));

        // first day in month
        assertTrue(mHelper.isWithinCurrentMonth(0, 6));

        // last day in month
        assertTrue(mHelper.isWithinCurrentMonth(5, 0));
    }
}

