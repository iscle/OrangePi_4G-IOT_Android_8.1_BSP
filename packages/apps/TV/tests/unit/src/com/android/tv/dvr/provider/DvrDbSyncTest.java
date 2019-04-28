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
 * limitations under the License
 */

package com.android.tv.dvr.provider;

import static android.support.test.InstrumentationRegistry.getContext;
import static org.mockito.Matchers.anyInt;
import static org.mockito.Matchers.anyObject;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import android.os.Build;
import android.support.test.filters.SdkSuppress;
import android.support.test.filters.SmallTest;

import com.android.tv.data.ChannelDataManager;
import com.android.tv.data.Program;
import com.android.tv.dvr.DvrDataManagerImpl;
import com.android.tv.dvr.DvrManager;
import com.android.tv.dvr.data.ScheduledRecording;
import com.android.tv.dvr.data.SeriesRecording;
import com.android.tv.dvr.recorder.SeriesRecordingScheduler;

import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

/**
 * Tests for {@link com.android.tv.dvr.DvrScheduleManager}
 */
@SmallTest
@SdkSuppress(minSdkVersion = Build.VERSION_CODES.N)
public class DvrDbSyncTest {
    private static final String INPUT_ID = "input_id";
    private static final long BASE_PROGRAM_ID = 1;
    private static final long BASE_START_TIME_MS = 0;
    private static final long BASE_END_TIME_MS = 1;
    private static final String BASE_SEASON_NUMBER = "2";
    private static final String BASE_EPISODE_NUMBER = "3";
    private static final Program BASE_PROGRAM = new Program.Builder().setId(BASE_PROGRAM_ID)
            .setStartTimeUtcMillis(BASE_START_TIME_MS).setEndTimeUtcMillis(BASE_END_TIME_MS)
            .build();
    private static final Program BASE_SERIES_PROGRAM = new Program.Builder().setId(BASE_PROGRAM_ID)
            .setStartTimeUtcMillis(BASE_START_TIME_MS).setEndTimeUtcMillis(BASE_END_TIME_MS)
            .setSeasonNumber(BASE_SEASON_NUMBER).setEpisodeNumber(BASE_EPISODE_NUMBER).build();
    private static final ScheduledRecording BASE_SCHEDULE =
            ScheduledRecording.builder(INPUT_ID, BASE_PROGRAM).build();
    private static final ScheduledRecording BASE_SERIES_SCHEDULE =
            ScheduledRecording.builder(INPUT_ID, BASE_SERIES_PROGRAM).build();

    private DvrDbSync mDbSync;
    @Mock private DvrManager mDvrManager;
    @Mock private DvrDataManagerImpl mDataManager;
    @Mock private ChannelDataManager mChannelDataManager;
    @Mock private SeriesRecordingScheduler mSeriesRecordingScheduler;

    @Before
    public void setUp() {
        MockitoAnnotations.initMocks(this);
        when(mChannelDataManager.isDbLoadFinished()).thenReturn(true);
        when(mDvrManager.addSeriesRecording(anyObject(), anyObject(), anyInt()))
                .thenReturn(SeriesRecording.builder(INPUT_ID, BASE_PROGRAM).build());
        mDbSync = new DvrDbSync(getContext(), mDataManager, mChannelDataManager,
                mDvrManager, mSeriesRecordingScheduler);
    }

    @Test
    public void testHandleUpdateProgram_null() {
        addSchedule(BASE_PROGRAM_ID, BASE_SCHEDULE);
        mDbSync.handleUpdateProgram(null, BASE_PROGRAM_ID);
        verify(mDataManager).removeScheduledRecording(BASE_SCHEDULE);
    }

    @Test
    public void testHandleUpdateProgram_changeTimeNotStarted() {
        addSchedule(BASE_PROGRAM_ID, BASE_SCHEDULE);
        long startTimeMs = BASE_START_TIME_MS + 1;
        long endTimeMs = BASE_END_TIME_MS + 1;
        Program program = new Program.Builder(BASE_PROGRAM).setStartTimeUtcMillis(startTimeMs)
                .setEndTimeUtcMillis(endTimeMs).build();
        mDbSync.handleUpdateProgram(program, BASE_PROGRAM_ID);
        assertUpdateScheduleCalled(program);
    }

    @Test
    public void testHandleUpdateProgram_changeTimeInProgressNotCalled() {
        addSchedule(BASE_PROGRAM_ID, ScheduledRecording.buildFrom(BASE_SCHEDULE)
                .setState(ScheduledRecording.STATE_RECORDING_IN_PROGRESS).build());
        long startTimeMs = BASE_START_TIME_MS + 1;
        Program program = new Program.Builder(BASE_PROGRAM).setStartTimeUtcMillis(startTimeMs)
                .build();
        mDbSync.handleUpdateProgram(program, BASE_PROGRAM_ID);
        verify(mDataManager, never()).updateScheduledRecording(anyObject());
    }

    @Test
    public void testHandleUpdateProgram_changeSeason() {
        addSchedule(BASE_PROGRAM_ID, BASE_SERIES_SCHEDULE);
        String seasonNumber = BASE_SEASON_NUMBER + "1";
        String episodeNumber = BASE_EPISODE_NUMBER + "1";
        Program program = new Program.Builder(BASE_SERIES_PROGRAM).setSeasonNumber(seasonNumber)
                .setEpisodeNumber(episodeNumber).build();
        mDbSync.handleUpdateProgram(program, BASE_PROGRAM_ID);
        assertUpdateScheduleCalled(program);
    }

    @Test
    public void testHandleUpdateProgram_finished() {
        addSchedule(BASE_PROGRAM_ID, ScheduledRecording.buildFrom(BASE_SERIES_SCHEDULE)
                .setState(ScheduledRecording.STATE_RECORDING_FINISHED).build());
        String seasonNumber = BASE_SEASON_NUMBER + "1";
        String episodeNumber = BASE_EPISODE_NUMBER + "1";
        Program program = new Program.Builder(BASE_SERIES_PROGRAM).setSeasonNumber(seasonNumber)
                .setEpisodeNumber(episodeNumber).build();
        mDbSync.handleUpdateProgram(program, BASE_PROGRAM_ID);
        verify(mDataManager, never()).updateScheduledRecording(anyObject());
    }

    private void addSchedule(long programId, ScheduledRecording schedule) {
        when(mDataManager.getScheduledRecordingForProgramId(programId)).thenReturn(schedule);
    }

    private void assertUpdateScheduleCalled(Program program) {
        verify(mDataManager).updateScheduledRecording(
                eq(ScheduledRecording.builder(INPUT_ID, program).build()));
    }
}
