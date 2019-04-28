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

import static org.junit.Assert.assertEquals;

import android.os.Build;
import android.support.test.filters.SdkSuppress;
import android.support.test.filters.SmallTest;

import com.android.tv.dvr.data.ScheduledRecording;
import com.android.tv.testing.dvr.RecordingTestUtils;

import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

/** Tests for {@link DvrDataManagerImpl} */
@SmallTest
@SdkSuppress(minSdkVersion = Build.VERSION_CODES.N)
public class DvrDataManagerImplTest {
    private static final String INPUT_ID = "input_id";
    private static final int CHANNEL_ID = 273;

    @Test
    public void testGetNextScheduledStartTimeAfter() {
        long id = 1;
        List<ScheduledRecording> scheduledRecordings = new ArrayList<>();
        assertNextStartTime(scheduledRecordings, 0L, DvrDataManager.NEXT_START_TIME_NOT_FOUND);
        scheduledRecordings.add(RecordingTestUtils
                .createTestRecordingWithIdAndPeriod(id++, INPUT_ID, CHANNEL_ID, 10L, 20L));
        assertNextStartTime(scheduledRecordings, 9L, 10L);
        assertNextStartTime(scheduledRecordings, 10L, DvrDataManager.NEXT_START_TIME_NOT_FOUND);
        scheduledRecordings.add(RecordingTestUtils
                .createTestRecordingWithIdAndPeriod(id++, INPUT_ID, CHANNEL_ID, 20L, 30L));
        assertNextStartTime(scheduledRecordings, 9L, 10L);
        assertNextStartTime(scheduledRecordings, 10L, 20L);
        assertNextStartTime(scheduledRecordings, 20L, DvrDataManager.NEXT_START_TIME_NOT_FOUND);
        scheduledRecordings.add(RecordingTestUtils
                .createTestRecordingWithIdAndPeriod(id++, INPUT_ID, CHANNEL_ID, 30L, 40L));
        assertNextStartTime(scheduledRecordings, 9L, 10L);
        assertNextStartTime(scheduledRecordings, 10L, 20L);
        assertNextStartTime(scheduledRecordings, 20L, 30L);
        assertNextStartTime(scheduledRecordings, 30L, DvrDataManager.NEXT_START_TIME_NOT_FOUND);
        scheduledRecordings.clear();
        scheduledRecordings.add(RecordingTestUtils
                .createTestRecordingWithIdAndPeriod(id++, INPUT_ID, CHANNEL_ID, 10L, 20L));
        scheduledRecordings.add(RecordingTestUtils
                .createTestRecordingWithIdAndPeriod(id++, INPUT_ID, CHANNEL_ID, 10L, 20L));
        scheduledRecordings.add(RecordingTestUtils
                .createTestRecordingWithIdAndPeriod(id++, INPUT_ID, CHANNEL_ID, 10L, 20L));
        assertNextStartTime(scheduledRecordings, 9L, 10L);
        assertNextStartTime(scheduledRecordings, 10L, DvrDataManager.NEXT_START_TIME_NOT_FOUND);
    }

    private void assertNextStartTime(List<ScheduledRecording> scheduledRecordings, long startTime,
            long expected) {
        assertEquals("getNextScheduledStartTimeAfter()", expected,
                DvrDataManagerImpl.getNextStartTimeAfter(scheduledRecordings, startTime));
    }
}