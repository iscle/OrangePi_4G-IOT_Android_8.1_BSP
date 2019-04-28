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

package com.android.tv.dvr.ui.list;

import android.content.Context;

import com.android.tv.data.Program;
import com.android.tv.dvr.data.ScheduledRecording;
import com.android.tv.dvr.data.ScheduledRecording.Builder;
import com.android.tv.dvr.ui.DvrUiHelper;

/**
 * A class for the episodic program.
 */
class EpisodicProgramRow extends ScheduleRow {
    private final String mInputId;
    private final Program mProgram;

    public EpisodicProgramRow(String inputId, Program program, ScheduledRecording recording,
            SchedulesHeaderRow headerRow) {
        super(recording, headerRow);
        mInputId = inputId;
        mProgram = program;
    }

    /**
     * Returns the program.
     */
    public Program getProgram() {
        return mProgram;
    }

    @Override
    public long getChannelId() {
        return mProgram.getChannelId();
    }

    @Override
    public long getStartTimeMs() {
        return mProgram.getStartTimeUtcMillis();
    }

    @Override
    public long getEndTimeMs() {
        return mProgram.getEndTimeUtcMillis();
    }

    @Override
    public Builder createNewScheduleBuilder() {
        return ScheduledRecording.builder(mInputId, mProgram);
    }

    @Override
    public String getProgramTitleWithEpisodeNumber(Context context) {
        return DvrUiHelper.getStyledTitleWithEpisodeNumber(context, mProgram, 0).toString();
    }

    @Override
    public String getEpisodeDisplayTitle(Context context) {
        return mProgram.getEpisodeDisplayTitle(context);
    }

    @Override
    public boolean matchSchedule(ScheduledRecording schedule) {
        return schedule.getType() == ScheduledRecording.TYPE_PROGRAM
                && mProgram.getId() == schedule.getProgramId();
    }

    @Override
    public String toString() {
        return super.toString()
                + "(inputId=" + mInputId
                + ",program=" + mProgram
                + ")";
    }
}
