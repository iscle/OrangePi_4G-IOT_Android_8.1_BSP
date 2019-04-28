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
import android.support.annotation.Nullable;

import com.android.tv.common.SoftPreconditions;
import com.android.tv.dvr.data.ScheduledRecording;
import com.android.tv.dvr.ui.DvrUiHelper;

/**
 * A class for schedule recording row.
 */
class ScheduleRow {
    private final SchedulesHeaderRow mHeaderRow;
    @Nullable private ScheduledRecording mSchedule;
    private boolean mStopRecordingRequested;
    private boolean mStartRecordingRequested;

    public ScheduleRow(@Nullable ScheduledRecording recording, SchedulesHeaderRow headerRow) {
        mSchedule = recording;
        mHeaderRow = headerRow;
    }

    /**
     * Gets which {@link SchedulesHeaderRow} this schedule row belongs to.
     */
    public SchedulesHeaderRow getHeaderRow() {
        return mHeaderRow;
    }

    /**
     * Returns the recording schedule.
     */
    @Nullable
    public ScheduledRecording getSchedule() {
        return mSchedule;
    }

    /**
     * Checks if the stop recording has been requested or not.
     */
    public boolean isStopRecordingRequested() {
        return mStopRecordingRequested;
    }

    /**
     * Sets the flag of stop recording request.
     */
    public void setStopRecordingRequested(boolean stopRecordingRequested) {
        SoftPreconditions.checkState(!mStartRecordingRequested);
        mStopRecordingRequested = stopRecordingRequested;
    }

    /**
     * Checks if the start recording has been requested or not.
     */
    public boolean isStartRecordingRequested() {
        return mStartRecordingRequested;
    }

    /**
     * Sets the flag of start recording request.
     */
    public void setStartRecordingRequested(boolean startRecordingRequested) {
        SoftPreconditions.checkState(!mStopRecordingRequested);
        mStartRecordingRequested = startRecordingRequested;
    }

    /**
     * Sets the recording schedule.
     */
    public void setSchedule(@Nullable ScheduledRecording schedule) {
        mSchedule = schedule;
    }

    /**
     * Returns the channel ID.
     */
    public long getChannelId() {
        return mSchedule != null ? mSchedule.getChannelId() : -1;
    }

    /**
     * Returns the start time.
     */
    public long getStartTimeMs() {
        return mSchedule != null ? mSchedule.getStartTimeMs() : -1;
    }

    /**
     * Returns the end time.
     */
    public long getEndTimeMs() {
        return mSchedule != null ? mSchedule.getEndTimeMs() : -1;
    }

    /**
     * Returns the duration.
     */
    public final long getDuration() {
        return getEndTimeMs() - getStartTimeMs();
    }

    /**
     * Checks if the program is on air.
     */
    public final boolean isOnAir() {
        long currentTimeMs = System.currentTimeMillis();
        return getStartTimeMs() <= currentTimeMs && getEndTimeMs() > currentTimeMs;
    }

    /**
     * Checks if the schedule is not started.
     */
    public final boolean isRecordingNotStarted() {
        return mSchedule != null
                && mSchedule.getState() == ScheduledRecording.STATE_RECORDING_NOT_STARTED;
    }

    /**
     * Checks if the schedule is in progress.
     */
    public final boolean isRecordingInProgress() {
        return mSchedule != null
                && mSchedule.getState() == ScheduledRecording.STATE_RECORDING_IN_PROGRESS;
    }

    /**
     * Checks if the schedule has been canceled or not.
     */
    public final boolean isScheduleCanceled() {
        return mSchedule != null
                && mSchedule.getState() == ScheduledRecording.STATE_RECORDING_CANCELED;
    }

    public boolean isRecordingFinished() {
        return mSchedule != null
                && (mSchedule.getState() == ScheduledRecording.STATE_RECORDING_FAILED
                || mSchedule.getState() == ScheduledRecording.STATE_RECORDING_CLIPPED
                || mSchedule.getState() == ScheduledRecording.STATE_RECORDING_FINISHED);
    }

    /**
     * Creates and returns the new schedule with the existing information.
     */
    public ScheduledRecording.Builder createNewScheduleBuilder() {
        return mSchedule != null ? ScheduledRecording.buildFrom(mSchedule) : null;
    }

    /**
     * Returns the program title with episode number.
     */
    public String getProgramTitleWithEpisodeNumber(Context context) {
        return mSchedule != null ? DvrUiHelper.getStyledTitleWithEpisodeNumber(context,
                mSchedule, 0).toString() : null;
    }

    /**
     * Returns the program title including the season/episode number.
     */
    public String getEpisodeDisplayTitle(Context context) {
        return mSchedule != null ? mSchedule.getEpisodeDisplayTitle(context) : null;
    }

    @Override
    public String toString() {
        return getClass().getSimpleName()
                + "(schedule=" + mSchedule
                + ",stopRecordingRequested=" + mStopRecordingRequested
                + ",startRecordingRequested=" + mStartRecordingRequested
                + ")";
    }

    /**
     * Checks if the {@code schedule} is for the program or channel.
     */
    public boolean matchSchedule(ScheduledRecording schedule) {
        if (mSchedule == null) {
            return false;
        }
        if (mSchedule.getType() == ScheduledRecording.TYPE_TIMED) {
            return mSchedule.getChannelId() == schedule.getChannelId()
                    && mSchedule.getStartTimeMs() == schedule.getStartTimeMs()
                    && mSchedule.getEndTimeMs() == schedule.getEndTimeMs();
        } else {
            return mSchedule.getProgramId() == schedule.getProgramId();
        }
    }
}
