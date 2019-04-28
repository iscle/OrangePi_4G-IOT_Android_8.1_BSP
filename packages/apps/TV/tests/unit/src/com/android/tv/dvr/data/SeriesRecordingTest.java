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

package com.android.tv.dvr.data;

import static org.junit.Assert.assertEquals;

import android.os.Build;
import android.os.Parcel;
import android.support.test.filters.SdkSuppress;
import android.support.test.filters.SmallTest;

import com.android.tv.data.Program;

import org.junit.Test;

/**
 * Tests for {@link SeriesRecording}.
 */
@SmallTest
@SdkSuppress(minSdkVersion = Build.VERSION_CODES.N)
public class SeriesRecordingTest {
    private static final String PROGRAM_TITLE = "MyProgram";
    private static final long CHANNEL_ID = 123;
    private static final long OTHER_CHANNEL_ID = 321;
    private static final String SERIES_ID = "SERIES_ID";
    private static final String OTHER_SERIES_ID = "OTHER_SERIES_ID";

    private final SeriesRecording mBaseSeriesRecording = new SeriesRecording.Builder()
            .setTitle(PROGRAM_TITLE).setChannelId(CHANNEL_ID).setSeriesId(SERIES_ID).build();
    private final SeriesRecording mSeriesRecordingSeason2 = SeriesRecording
            .buildFrom(mBaseSeriesRecording).setStartFromSeason(2).build();
    private final SeriesRecording mSeriesRecordingSeason2Episode5 = SeriesRecording
            .buildFrom(mSeriesRecordingSeason2).setStartFromEpisode(5).build();
    private final Program mBaseProgram = new Program.Builder().setTitle(PROGRAM_TITLE)
            .setChannelId(CHANNEL_ID).setSeriesId(SERIES_ID).build();

    @Test
    public void testParcelable() {
        SeriesRecording r1 = new SeriesRecording.Builder()
                .setId(1)
                .setChannelId(2)
                .setPriority(3)
                .setTitle("4")
                .setDescription("5")
                .setLongDescription("5-long")
                .setSeriesId("6")
                .setStartFromEpisode(7)
                .setStartFromSeason(8)
                .setChannelOption(SeriesRecording.OPTION_CHANNEL_ALL)
                .setCanonicalGenreIds(new int[] {9, 10})
                .setPosterUri("11")
                .setPhotoUri("12")
                .build();
        Parcel p1 = Parcel.obtain();
        Parcel p2 = Parcel.obtain();
        try {
            r1.writeToParcel(p1, 0);
            byte[] bytes = p1.marshall();
            p2.unmarshall(bytes, 0, bytes.length);
            p2.setDataPosition(0);
            SeriesRecording r2 = SeriesRecording.fromParcel(p2);
            assertEquals(r1, r2);
        } finally {
            p1.recycle();
            p2.recycle();
        }
    }

    @Test
    public void testDoesProgramMatch_simpleMatch() {
        assertDoesProgramMatch(mBaseProgram, mBaseSeriesRecording, true);
    }

    @Test
    public void testDoesProgramMatch_differentSeriesId() {
        Program program = new Program.Builder(mBaseProgram).setSeriesId(OTHER_SERIES_ID).build();
        assertDoesProgramMatch(program, mBaseSeriesRecording, false);
    }

    @Test
    public void testDoesProgramMatch_differentChannel() {
        Program program = new Program.Builder(mBaseProgram).setChannelId(OTHER_CHANNEL_ID).build();
        assertDoesProgramMatch(program, mBaseSeriesRecording, false);
    }

    @Test
    public void testDoesProgramMatch_startFromSeason2() {
        Program program = mBaseProgram;
        assertDoesProgramMatch(program, mSeriesRecordingSeason2, true);
        program = new Program.Builder(program).setSeasonNumber("1").build();
        assertDoesProgramMatch(program, mSeriesRecordingSeason2, false);
        program = new Program.Builder(program).setSeasonNumber("2").build();
        assertDoesProgramMatch(program, mSeriesRecordingSeason2, true);
        program = new Program.Builder(program).setSeasonNumber("3").build();
        assertDoesProgramMatch(program, mSeriesRecordingSeason2, true);
    }

    @Test
    public void testDoesProgramMatch_startFromSeason2episode5() {
        Program program = mBaseProgram;
        assertDoesProgramMatch(program, mSeriesRecordingSeason2Episode5, true);
        program = new Program.Builder(program).setSeasonNumber("2").build();
        assertDoesProgramMatch(program, mSeriesRecordingSeason2Episode5, true);
        program = new Program.Builder(program).setEpisodeNumber("4").build();
        assertDoesProgramMatch(program, mSeriesRecordingSeason2Episode5, false);
        program = new Program.Builder(program).setEpisodeNumber("5").build();
        assertDoesProgramMatch(program, mSeriesRecordingSeason2Episode5, true);
        program = new Program.Builder(program).setEpisodeNumber("6").build();
        assertDoesProgramMatch(program, mSeriesRecordingSeason2Episode5, true);
        program = new Program.Builder(program).setSeasonNumber("3").setEpisodeNumber("1").build();
        assertDoesProgramMatch(program, mSeriesRecordingSeason2Episode5, true);
    }

    private void assertDoesProgramMatch(Program p, SeriesRecording seriesRecording,
            boolean expected) {
        assertEquals(seriesRecording + " doesProgramMatch " + p, expected,
                seriesRecording.matchProgram(p));
    }
}
