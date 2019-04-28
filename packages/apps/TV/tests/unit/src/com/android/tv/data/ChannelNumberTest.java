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
package com.android.tv.data;

import static com.android.tv.data.ChannelNumber.parseChannelNumber;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import android.support.test.filters.SmallTest;

import com.android.tv.testing.ComparableTester;

import org.junit.Test;

/**
 * Tests for {@link ChannelNumber}.
 */
@SmallTest
public class ChannelNumberTest {
    /**
     * Test method for {@link ChannelNumber#ChannelNumber()}.
     */
    @Test
    public void testChannelNumber() {
        assertChannelEquals(new ChannelNumber(), "", false, "");
    }

    /**
     * Test method for
     * {@link com.android.tv.data.ChannelNumber#parseChannelNumber(java.lang.String)}.
     */
    @Test
    public void testParseChannelNumber() {
        assertNull(parseChannelNumber(""));
        assertNull(parseChannelNumber("-"));
        assertNull(parseChannelNumber("abcd12"));
        assertNull(parseChannelNumber("12abcd"));
        assertNull(parseChannelNumber("-12"));
        assertChannelEquals(parseChannelNumber("1"), "1", false, "");
        assertChannelEquals(parseChannelNumber("1234-4321"), "1234", true, "4321");
        assertChannelEquals(parseChannelNumber("3-4"), "3", true, "4");
        assertChannelEquals(parseChannelNumber("5-6"), "5", true, "6");
    }

    /**
     * Test method for {@link ChannelNumber#compareTo(com.android.tv.data.ChannelNumber)}.
     */
    @Test
    public void testCompareTo() {
        new ComparableTester<ChannelNumber>()
                .addEquivalentGroup(parseChannelNumber("1"), parseChannelNumber("1"))
                .addEquivalentGroup(parseChannelNumber("2"))
                .addEquivalentGroup(parseChannelNumber("2-1"))
                .addEquivalentGroup(parseChannelNumber("2-2"))
                .addEquivalentGroup(parseChannelNumber("2-10"))
                .addEquivalentGroup(parseChannelNumber("3"))
                .addEquivalentGroup(parseChannelNumber("4"), parseChannelNumber("4-0"))
                .addEquivalentGroup(parseChannelNumber("10"))
                .addEquivalentGroup(parseChannelNumber("100"))
                .test();
    }

    /**
     * Test method for {@link ChannelNumber#compare(java.lang.String, java.lang.String)}.
     */
    @Test
    public void testCompare() {
        // Only need to test nulls, the reset is tested by testCompareTo
        assertEquals("compareTo(null,null)", 0, ChannelNumber.compare(null, null));
        assertEquals("compareTo(1,1)", 0, ChannelNumber.compare("1", "1"));
        assertEquals("compareTo(null,1)<0", true, ChannelNumber.compare(null, "1") < 0);
        assertEquals("compareTo(mal-formatted,1)<0", true, ChannelNumber.compare("abcd", "1") < 0);
        assertEquals("compareTo(mal-formatted,1)<0", true, ChannelNumber.compare(".4", "1") < 0);
        assertEquals("compareTo(1,null)>0", true, ChannelNumber.compare("1", null) > 0);
    }

    private void assertChannelEquals(ChannelNumber actual, String expectedMajor,
            boolean expectedHasDelimiter, String expectedMinor) {
        assertEquals(actual + " major", actual.majorNumber, expectedMajor);
        assertEquals(actual + " hasDelimiter", actual.hasDelimiter, expectedHasDelimiter);
        assertEquals(actual + " minor", actual.minorNumber, expectedMinor);
    }

}
