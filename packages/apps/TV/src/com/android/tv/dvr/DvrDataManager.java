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

import android.support.annotation.MainThread;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.util.Range;

import com.android.tv.dvr.data.RecordedProgram;
import com.android.tv.dvr.data.ScheduledRecording;
import com.android.tv.dvr.data.ScheduledRecording.RecordingState;
import com.android.tv.dvr.data.SeriesRecording;

import java.util.Collection;
import java.util.List;

/**
 * Read only data manager.
 */
@MainThread
public interface DvrDataManager {
    long NEXT_START_TIME_NOT_FOUND = -1;

    boolean isInitialized();

    /**
     * Returns {@code true} if the schedules were loaded, otherwise {@code false}.
     */
    boolean isDvrScheduleLoadFinished();

    /**
     * Returns {@code true} if the recorded programs were loaded, otherwise {@code false}.
     */
    boolean isRecordedProgramLoadFinished();

    /**
     * Returns past recordings.
     */
    List<RecordedProgram> getRecordedPrograms();

    /**
     * Returns past recorded programs in the given series.
     */
    List<RecordedProgram> getRecordedPrograms(long seriesRecordingId);

    /**
     * Returns all {@link ScheduledRecording} regardless of state.
     * <p>
     * The result doesn't contain the deleted schedules.
     */
    List<ScheduledRecording> getAllScheduledRecordings();

    /**
     * Returns all available {@link ScheduledRecording}, it contains started and non started
     * recordings.
     */
    List<ScheduledRecording> getAvailableScheduledRecordings();

    /**
     * Returns started recordings that expired.
     */
    List<ScheduledRecording> getStartedRecordings();

    /**
     * Returns scheduled but not started recordings that have not expired.
     */
    List<ScheduledRecording> getNonStartedScheduledRecordings();

    /**
     * Returns series recordings.
     */
    List<SeriesRecording> getSeriesRecordings();

    /**
     * Returns series recordings from the given input.
     */
    List<SeriesRecording> getSeriesRecordings(String inputId);

    /**
     * Returns the next start time after {@code time} or {@link #NEXT_START_TIME_NOT_FOUND}
     * if none is found.
     *
     * @param time time milliseconds
     */
    long getNextScheduledStartTimeAfter(long time);

    /**
     * Returns a list of the schedules with a overlap with the given time period inclusive and with
     * the given state.
     *
     * <p> A recording overlaps with a period when
     * {@code recording.getStartTime() <= period.getUpper() &&
     * recording.getEndTime() >= period.getLower()}.
     *
     * @param period a time period in milliseconds.
     * @param state the state of the schedule.
     */
    List<ScheduledRecording> getScheduledRecordings(Range<Long> period, @RecordingState int state);

    /**
     * Returns a list of the schedules in the given series.
     */
    List<ScheduledRecording> getScheduledRecordings(long seriesRecordingId);

    /**
     * Returns a list of the schedules from the given input.
     */
    List<ScheduledRecording> getScheduledRecordings(String inputId);

    /**
     * Add a {@link OnDvrScheduleLoadFinishedListener}.
     */
    void addDvrScheduleLoadFinishedListener(OnDvrScheduleLoadFinishedListener listener);

    /**
     * Remove a {@link OnDvrScheduleLoadFinishedListener}.
     */
    void removeDvrScheduleLoadFinishedListener(OnDvrScheduleLoadFinishedListener listener);

    /**
     * Add a {@link OnRecordedProgramLoadFinishedListener}.
     */
    void addRecordedProgramLoadFinishedListener(OnRecordedProgramLoadFinishedListener listener);

    /**
     * Remove a {@link OnRecordedProgramLoadFinishedListener}.
     */
    void removeRecordedProgramLoadFinishedListener(OnRecordedProgramLoadFinishedListener listener);

    /**
     * Add a {@link ScheduledRecordingListener}.
     */
    void addScheduledRecordingListener(ScheduledRecordingListener scheduledRecordingListener);

    /**
     * Remove a {@link ScheduledRecordingListener}.
     */
    void removeScheduledRecordingListener(ScheduledRecordingListener scheduledRecordingListener);

    /**
     * Add a {@link RecordedProgramListener}.
     */
    void addRecordedProgramListener(RecordedProgramListener listener);

    /**
     * Remove a {@link RecordedProgramListener}.
     */
    void removeRecordedProgramListener(RecordedProgramListener listener);

    /**
     * Add a {@link ScheduledRecordingListener}.
     */
    void addSeriesRecordingListener(SeriesRecordingListener seriesRecordingListener);

    /**
     * Remove a {@link ScheduledRecordingListener}.
     */
    void removeSeriesRecordingListener(SeriesRecordingListener seriesRecordingListener);

    /**
     * Returns the scheduled recording program with the given recordingId or null if is not found.
     */
    @Nullable
    ScheduledRecording getScheduledRecording(long recordingId);

    /**
     * Returns the scheduled recording program with the given programId or null if is not found.
     */
    @Nullable
    ScheduledRecording getScheduledRecordingForProgramId(long programId);

    /**
     * Returns the recorded program with the given recordingId or null if is not found.
     */
    @Nullable
    RecordedProgram getRecordedProgram(long recordingId);

    /**
     * Returns the series recording with the given seriesId or null if is not found.
     */
    @Nullable
    SeriesRecording getSeriesRecording(long seriesRecordingId);

    /**
     * Returns the series recording with the given series ID or {@code null} if not found.
     */
    @Nullable
    SeriesRecording getSeriesRecording(String seriesId);

    /**
     * Returns the schedules which are marked deleted.
     */
    Collection<ScheduledRecording> getDeletedSchedules();

    /**
     * Returns the program IDs which is not allowed to make a schedule automatically.
     */
    @NonNull
    Collection<Long> getDisallowedProgramIds();

    /**
     * Checks each of the give series recordings to see if it's empty, i.e., it doesn't contains
     * any available schedules or recorded programs, and it's status is
     * {@link SeriesRecording#STATE_SERIES_STOPPED}; and removes those empty series recordings.
     */
    void checkAndRemoveEmptySeriesRecording(long... seriesRecordingIds);

    /**
     * Listens for the DVR schedules loading finished.
     */
    interface OnDvrScheduleLoadFinishedListener {
        void onDvrScheduleLoadFinished();
    }

    /**
     * Listens for the recorded program loading finished.
     */
    interface OnRecordedProgramLoadFinishedListener {
        void onRecordedProgramLoadFinished();
    }

    /**
     * Listens for changes to {@link ScheduledRecording}s.
     */
    interface ScheduledRecordingListener {
        void onScheduledRecordingAdded(ScheduledRecording... scheduledRecordings);

        void onScheduledRecordingRemoved(ScheduledRecording... scheduledRecordings);

        /**
         * Called when the schedules are updated.
         *
         * <p>Note that the passed arguments are the new objects with the same ID as the old ones.
         */
        void onScheduledRecordingStatusChanged(ScheduledRecording... scheduledRecordings);
    }

    /**
     * Listens for changes to {@link SeriesRecording}s.
     */
    interface SeriesRecordingListener {
        void onSeriesRecordingAdded(SeriesRecording... seriesRecordings);

        void onSeriesRecordingRemoved(SeriesRecording... seriesRecordings);

        void onSeriesRecordingChanged(SeriesRecording... seriesRecordings);
    }

    /**
     * Listens for changes to {@link RecordedProgram}s.
     */
    interface RecordedProgramListener {
        void onRecordedProgramsAdded(RecordedProgram... recordedPrograms);

        void onRecordedProgramsChanged(RecordedProgram... recordedPrograms);

        void onRecordedProgramsRemoved(RecordedProgram... recordedPrograms);
    }
}
