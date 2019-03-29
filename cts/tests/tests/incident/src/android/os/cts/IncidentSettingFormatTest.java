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

package android.os.incident.cts;

import android.os.IncidentReportArgs;

import junit.framework.TestCase;
import org.junit.Assert;

/**
 * Test the parsing of the string IncidentReportArgs format.
 */
public class IncidentSettingFormatTest extends TestCase {
    private static final String TAG = "IncidentSettingFormatTest";

    public void testNull() throws Exception {
        final IncidentReportArgs args = IncidentReportArgs.parseSetting(null);
        assertNull(args);
    }

    public void testEmpty() throws Exception {
        final IncidentReportArgs args = IncidentReportArgs.parseSetting("");
        assertNull(args);
    }

    public void testSpaces() throws Exception {
        final IncidentReportArgs args = IncidentReportArgs.parseSetting(" \r\n\t");
        assertNull(args);
    }

    public void testDisabled() throws Exception {
        final IncidentReportArgs args = IncidentReportArgs.parseSetting(" disabled ");
        assertNull(args);
    }

    public void testAll() throws Exception {
        final IncidentReportArgs args = IncidentReportArgs.parseSetting(" all ");
        assertTrue(args.isAll());
    }

    public void testNone() throws Exception {
        final IncidentReportArgs args = IncidentReportArgs.parseSetting(" none ");
        assertFalse(args.isAll());
        assertEquals(0, args.sectionCount());
    }

    public void testOne() throws Exception {
        final IncidentReportArgs args = IncidentReportArgs.parseSetting(" 1 ");
        assertFalse(args.isAll());
        assertEquals(1, args.sectionCount());
        assertTrue(args.containsSection(1));
    }

    public void testSeveral() throws Exception {
        final IncidentReportArgs args = IncidentReportArgs.parseSetting(" 1, 2, , 3,4,5, 6, 78, ");
        assertFalse(args.isAll());
        assertEquals(7, args.sectionCount());
        assertTrue(args.containsSection(1));
        assertTrue(args.containsSection(2));
        assertTrue(args.containsSection(3));
        assertTrue(args.containsSection(4));
        assertTrue(args.containsSection(5));
        assertTrue(args.containsSection(6));
        assertTrue(args.containsSection(78));
    }

    public void testZero() throws Exception {
        try {
            final IncidentReportArgs args = IncidentReportArgs.parseSetting(" 0 ");
            throw new RuntimeException("parseSetting(\" 0 \") should fail with"
                    + " IllegalArgumentException");
        } catch (IllegalArgumentException ex) {
        }
    }

    public void testNegative() throws Exception {
        try {
            final IncidentReportArgs args = IncidentReportArgs.parseSetting(" -1 ");
            throw new RuntimeException("parseSetting(\" -1 \") should fail with"
                    + " IllegalArgumentException");
        } catch (IllegalArgumentException ex) {
        }
    }

}
