/*
 * Copyright (C) 2015 The Android Open Source Project
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

package com.android.tv.util;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import android.support.test.filters.SmallTest;

import org.junit.Test;

import java.util.Calendar;
import java.util.GregorianCalendar;
import java.util.TimeZone;

/**
 * Tests for {@link com.android.tv.util.Utils#isInGivenDay}.
 */
@SmallTest
public class UtilsTest_IsInGivenDay {
    @Test
    public void testIsInGivenDay() {
        assertTrue(Utils.isInGivenDay(
                new GregorianCalendar(2015, Calendar.JANUARY, 1).getTimeInMillis(),
                new GregorianCalendar(2015, Calendar.JANUARY, 1, 0, 30).getTimeInMillis()));
    }

    @Test
    public void testIsNotInGivenDay() {
        assertFalse(Utils.isInGivenDay(
                new GregorianCalendar(2015, Calendar.JANUARY, 1).getTimeInMillis(),
                new GregorianCalendar(2015, Calendar.JANUARY, 2).getTimeInMillis()));
    }

    @Test
    public void testIfTimeZoneApplied() {
        TimeZone timeZone = TimeZone.getDefault();

        TimeZone.setDefault(TimeZone.getTimeZone("Asia/Seoul"));

        // 2015.01.01 00:00 in KST = 2014.12.31 15:00 in UTC
        long date2015StartMs =
                new GregorianCalendar(2015, Calendar.JANUARY, 1).getTimeInMillis();

        // 2015.01.01 10:00 in KST = 2015.01.01 01:00 in UTC
        long date2015Start10AMMs =
                new GregorianCalendar(2015, Calendar.JANUARY, 1, 10, 0).getTimeInMillis();

        // Those two times aren't in the same day in UTC, but they are in KST.
        assertTrue(Utils.isInGivenDay(date2015StartMs, date2015Start10AMMs));

        TimeZone.setDefault(timeZone);
    }
}
