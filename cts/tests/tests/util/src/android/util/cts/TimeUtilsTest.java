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
package android.util.cts;

import static org.junit.Assert.assertEquals;

import android.support.test.filters.SmallTest;
import android.support.test.runner.AndroidJUnit4;
import android.util.TimeUtils;

import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.Calendar;
import java.util.TimeZone;

@SmallTest
@RunWith(AndroidJUnit4.class)
public class TimeUtilsTest {
    @Test
    public void testUnitedStates() throws Exception {
        String[] mainstream = new String[] {
            "America/New_York", // Eastern
            "America/Chicago", // Central
            "America/Denver", // Mountain
            "America/Los_Angeles", // Pacific
            "America/Anchorage", // Alaska
            "Pacific/Honolulu", // Hawaii, no DST
        };

        for (String name : mainstream) {
            TimeZone tz = TimeZone.getTimeZone(name);
            Calendar c = Calendar.getInstance(tz);
            TimeZone guess;

            c.set(2016, Calendar.OCTOBER, 20, 12, 0, 0);
            guess = guessTimeZone(c, "us");
            assertEquals(name, guess.getID());

            c.set(2017, Calendar.JANUARY, 20, 12, 0, 0);
            guess = guessTimeZone(c, "us");
            assertEquals(name, guess.getID());
        }
    }

    @Test
    public void testWeirdUnitedStates() throws Exception {
        String[] weird = new String[] {
            "America/Phoenix", // Mountain, no DST
            "America/Adak", // Same as Hawaii, but with DST
        };

        for (String name : weird) {
            TimeZone tz = TimeZone.getTimeZone(name);
            Calendar c = Calendar.getInstance(tz);
            TimeZone guess;

            c.set(2016, Calendar.OCTOBER, 20, 12, 0, 0);
            guess = guessTimeZone(c, "us");
            assertEquals(name, guess.getID());
        }
    }

    @Test
    public void testOld() throws Exception {
        String[] old = new String[] {
            "America/Indiana/Indianapolis", // Eastern, formerly no DST
        };

        for (String name : old) {
            TimeZone tz = TimeZone.getTimeZone(name);
            Calendar c = Calendar.getInstance(tz);
            TimeZone guess;

            c.set(2005, Calendar.OCTOBER, 20, 12, 0, 0);
            guess = guessTimeZone(c, "us");
            assertEquals(name, guess.getID());
        }
    }

    @Test
    public void testWorldWeird() throws Exception {
        String[] world = new String[] {
            // Distinguisable from Sydney only when DST not in effect
            "au", "Australia/Lord_Howe",
        };

        for (int i = 0; i < world.length; i += 2) {
            String country = world[i];
            String name = world[i + 1];

            TimeZone tz = TimeZone.getTimeZone(name);
            Calendar c = Calendar.getInstance(tz);
            TimeZone guess;

            c.set(2016, Calendar.JULY, 20, 12, 0, 0);
            guess = guessTimeZone(c, country);
            assertEquals(name, guess.getID());
        }
    }

    private static TimeZone guessTimeZone(Calendar c, String country) {
        return TimeUtils.getTimeZone(c.get(Calendar.ZONE_OFFSET) + c.get(Calendar.DST_OFFSET),
                                     c.get(Calendar.DST_OFFSET) != 0,
                                     c.getTimeInMillis(),
                                     country);
    }

    @Test
    public void testFormatDuration() {
        assertFormatDuration("0", 0);
        assertFormatDuration("-1ms", -1);
        assertFormatDuration("+1ms", 1);
        assertFormatDuration("+10ms", 10);
        assertFormatDuration("+100ms", 100);
        assertFormatDuration("+101ms", 101);
        assertFormatDuration("+330ms", 330);
        assertFormatDuration("+1s0ms", 1000);
        assertFormatDuration("+1s330ms", 1330);
        assertFormatDuration("+10s24ms", 10024);
        assertFormatDuration("+1m0s30ms", 60030);
        assertFormatDuration("+1h0m0s30ms", 3600030);
        assertFormatDuration("+1d0h0m0s30ms", 86400030);
    }

    @Test
    public void testFormatHugeDuration() {
        assertFormatDuration("+15542d1h11m11s555ms", 1342833071555L);
        assertFormatDuration("-15542d1h11m11s555ms", -1342833071555L);
    }

    private void assertFormatDuration(String expected, long duration) {
        StringBuilder sb = new StringBuilder();
        TimeUtils.formatDuration(duration, sb);
        assertEquals("formatDuration(" + duration + ")", expected, sb.toString());
    }
}
