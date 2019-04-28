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
 * limitations under the License
 */

package com.android.tv.dvr;

import static com.android.tv.testing.dvr.RecordingTestUtils.createTestRecordingWithIdAndPeriod;
import static com.android.tv.testing.dvr.RecordingTestUtils.normalizePriority;
import static junit.framework.TestCase.assertEquals;

import android.os.Build;
import android.support.test.filters.SdkSuppress;
import android.support.test.filters.SmallTest;
import android.test.MoreAsserts;
import android.util.Range;

import com.android.tv.data.Channel;
import com.android.tv.data.Program;
import com.android.tv.dvr.data.ScheduledRecording;
import com.android.tv.testing.dvr.RecordingTestUtils;

import org.junit.Test;

import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

/** Tests for {@link ScheduledRecordingTest} */
@SmallTest
@SdkSuppress(minSdkVersion = Build.VERSION_CODES.N)
public class ScheduledRecordingTest {
    private static final String INPUT_ID = "input_id";
    private static final int CHANNEL_ID = 273;

    @Test
    public void testIsOverLapping() {
        ScheduledRecording r = createTestRecordingWithIdAndPeriod(1, INPUT_ID, CHANNEL_ID,
                10L, 20L);
        assertOverLapping(false, 1L, 9L, r);

        assertOverLapping(true, 1L, 20L, r);
        assertOverLapping(false, 1L, 10L, r);
        assertOverLapping(true, 10L, 19L, r);
        assertOverLapping(true, 10L, 20L, r);
        assertOverLapping(true, 11L, 20L, r);
        assertOverLapping(true, 11L, 21L, r);
        assertOverLapping(false, 20L, 21L, r);

        assertOverLapping(false, 21L, 29L, r);
    }

    @Test
    public void testBuildProgram() {
        Channel c = new Channel.Builder().build();
        Program p = new Program.Builder().build();
        ScheduledRecording actual = ScheduledRecording.builder(INPUT_ID, p)
                .setChannelId(c.getId()).build();
        assertEquals("type", ScheduledRecording.TYPE_PROGRAM, actual.getType());
    }

    @Test
    public void testBuildTime() {
        ScheduledRecording actual = createTestRecordingWithIdAndPeriod(1, INPUT_ID, CHANNEL_ID,
                10L, 20L);
        assertEquals("type", ScheduledRecording.TYPE_TIMED, actual.getType());
    }

    @Test
    public void testBuildFrom() {
        ScheduledRecording expected = createTestRecordingWithIdAndPeriod(1, INPUT_ID, CHANNEL_ID,
                10L, 20L);
        ScheduledRecording actual = ScheduledRecording.buildFrom(expected).build();
        RecordingTestUtils.assertRecordingEquals(expected, actual);
    }

    @Test
    public void testBuild_priority() {
        ScheduledRecording a = normalizePriority(
                createTestRecordingWithIdAndPeriod(1, INPUT_ID, CHANNEL_ID, 10L, 20L));
        ScheduledRecording b = normalizePriority(
                createTestRecordingWithIdAndPeriod(2, INPUT_ID, CHANNEL_ID, 10L, 20L));
        ScheduledRecording c = normalizePriority(
                createTestRecordingWithIdAndPeriod(3, INPUT_ID, CHANNEL_ID, 10L, 20L));

        // default priority
        MoreAsserts.assertContentsInOrder(sortByPriority(c, b, a), a, b, c);

        // make A preferred over B
        a = ScheduledRecording.buildFrom(a).setPriority(b.getPriority() + 2).build();
        MoreAsserts.assertContentsInOrder(sortByPriority(a, b, c), b, c, a);
    }

    public Collection<ScheduledRecording> sortByPriority(ScheduledRecording a, ScheduledRecording b,
            ScheduledRecording c) {
        List<ScheduledRecording> list = Arrays.asList(a, b, c);
        Collections.sort(list, ScheduledRecording.PRIORITY_COMPARATOR);
        return list;
    }

    private void assertOverLapping(boolean expected, long lower, long upper, ScheduledRecording r) {
        assertEquals("isOverlapping(Range(" + lower + "," + upper + "), recording " + r, expected,
                r.isOverLapping(new Range<>(lower, upper)));
    }
}
