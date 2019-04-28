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

package com.android.tv.dvr.provider;

import android.annotation.SuppressLint;
import android.annotation.TargetApi;
import android.content.ContentUris;
import android.content.Context;
import android.database.ContentObserver;
import android.media.tv.TvContract.Programs;
import android.net.Uri;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.support.annotation.MainThread;
import android.support.annotation.VisibleForTesting;
import android.util.Log;

import com.android.tv.TvApplication;
import com.android.tv.data.ChannelDataManager;
import com.android.tv.data.Program;
import com.android.tv.dvr.DvrDataManager.ScheduledRecordingListener;
import com.android.tv.dvr.DvrDataManagerImpl;
import com.android.tv.dvr.DvrManager;
import com.android.tv.dvr.data.ScheduledRecording;
import com.android.tv.dvr.data.SeriesRecording;
import com.android.tv.dvr.recorder.SeriesRecordingScheduler;
import com.android.tv.util.AsyncDbTask.AsyncQueryProgramTask;
import com.android.tv.util.TvUriMatcher;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Objects;
import java.util.Queue;
import java.util.Set;

/**
 * A class to synchronizes DVR DB with TvProvider.
 *
 * <p>The current implementation of AsyncDbTask allows only one task to run at a time, and all the
 * other tasks are blocked until the current one finishes. As this class performs the low priority
 * jobs which take long time, it should not block others if possible. For this reason, only one
 * program is queried at a time and others are queued and will be executed on the other
 * AsyncDbTask's after the current one finishes to minimize the execution time of one AsyncDbTask.
 */
@MainThread
@TargetApi(Build.VERSION_CODES.N)
public class DvrDbSync {
    private static final String TAG = "DvrDbSync";
    private static final boolean DEBUG = false;

    private final Context mContext;
    private final DvrManager mDvrManager;
    private final DvrDataManagerImpl mDataManager;
    private final ChannelDataManager mChannelDataManager;
    private final Queue<Long> mProgramIdQueue = new LinkedList<>();
    private QueryProgramTask mQueryProgramTask;
    private final SeriesRecordingScheduler mSeriesRecordingScheduler;
    private final ContentObserver mContentObserver = new ContentObserver(new Handler(
            Looper.getMainLooper())) {
        @SuppressLint("SwitchIntDef")
        @Override
        public void onChange(boolean selfChange, Uri uri) {
            switch (TvUriMatcher.match(uri)) {
                case TvUriMatcher.MATCH_PROGRAM:
                    if (DEBUG) Log.d(TAG, "onProgramsUpdated");
                    onProgramsUpdated();
                    break;
                case TvUriMatcher.MATCH_PROGRAM_ID:
                    if (DEBUG) {
                        Log.d(TAG, "onProgramUpdated: programId=" + ContentUris.parseId(uri));
                    }
                    onProgramUpdated(ContentUris.parseId(uri));
                    break;
            }
        }
    };

    private final ChannelDataManager.Listener mChannelDataManagerListener =
            new ChannelDataManager.Listener() {
                @Override
                public void onLoadFinished() {
                    start();
                }

                @Override
                public void onChannelListUpdated() {
                    onChannelsUpdated();
                }

                @Override
                public void onChannelBrowsableChanged() { }
            };

    private final ScheduledRecordingListener mScheduleListener = new ScheduledRecordingListener() {
        @Override
        public void onScheduledRecordingAdded(ScheduledRecording... schedules) {
            for (ScheduledRecording schedule : schedules) {
                addProgramIdToCheckIfNeeded(schedule);
            }
            startNextUpdateIfNeeded();
        }

        @Override
        public void onScheduledRecordingRemoved(ScheduledRecording... schedules) {
            for (ScheduledRecording schedule : schedules) {
                mProgramIdQueue.remove(schedule.getProgramId());
            }
        }

        @Override
        public void onScheduledRecordingStatusChanged(ScheduledRecording... schedules) {
            for (ScheduledRecording schedule : schedules) {
                mProgramIdQueue.remove(schedule.getProgramId());
                addProgramIdToCheckIfNeeded(schedule);
            }
            startNextUpdateIfNeeded();
        }
    };

    public DvrDbSync(Context context, DvrDataManagerImpl dataManager) {
        this(context, dataManager, TvApplication.getSingletons(context).getChannelDataManager(),
                TvApplication.getSingletons(context).getDvrManager(),
                SeriesRecordingScheduler.getInstance(context));
    }

    @VisibleForTesting
    DvrDbSync(Context context, DvrDataManagerImpl dataManager,
            ChannelDataManager channelDataManager, DvrManager dvrManager,
            SeriesRecordingScheduler seriesRecordingScheduler) {
        mContext = context;
        mDvrManager = dvrManager;
        mDataManager = dataManager;
        mChannelDataManager = channelDataManager;
        mSeriesRecordingScheduler = seriesRecordingScheduler;
    }

    /**
     * Starts the DB sync.
     */
    public void start() {
        if (!mChannelDataManager.isDbLoadFinished()) {
            mChannelDataManager.addListener(mChannelDataManagerListener);
            return;
        }
        mContext.getContentResolver().registerContentObserver(Programs.CONTENT_URI, true,
                mContentObserver);
        mDataManager.addScheduledRecordingListener(mScheduleListener);
        onChannelsUpdated();
        onProgramsUpdated();
    }

    /**
     * Stops the DB sync.
     */
    public void stop() {
        mProgramIdQueue.clear();
        if (mQueryProgramTask != null) {
            mQueryProgramTask.cancel(true);
        }
        mChannelDataManager.removeListener(mChannelDataManagerListener);
        mDataManager.removeScheduledRecordingListener(mScheduleListener);
        mContext.getContentResolver().unregisterContentObserver(mContentObserver);
    }

    private void onChannelsUpdated() {
        List<SeriesRecording> seriesRecordingsToUpdate = new ArrayList<>();
        for (SeriesRecording r : mDataManager.getSeriesRecordings()) {
            if (r.getChannelOption() == SeriesRecording.OPTION_CHANNEL_ONE
                    && !mChannelDataManager.doesChannelExistInDb(r.getChannelId())) {
                seriesRecordingsToUpdate.add(SeriesRecording.buildFrom(r)
                        .setChannelOption(SeriesRecording.OPTION_CHANNEL_ALL)
                        .setState(SeriesRecording.STATE_SERIES_STOPPED).build());
            }
        }
        if (!seriesRecordingsToUpdate.isEmpty()) {
            mDataManager.updateSeriesRecording(
                    SeriesRecording.toArray(seriesRecordingsToUpdate));
        }
        List<ScheduledRecording> schedulesToRemove = new ArrayList<>();
        for (ScheduledRecording r : mDataManager.getAvailableScheduledRecordings()) {
            if (!mChannelDataManager.doesChannelExistInDb(r.getChannelId())) {
                schedulesToRemove.add(r);
                mProgramIdQueue.remove(r.getProgramId());
            }
        }
        if (!schedulesToRemove.isEmpty()) {
            mDataManager.removeScheduledRecording(
                    ScheduledRecording.toArray(schedulesToRemove));
        }
    }

    private void onProgramsUpdated() {
        for (ScheduledRecording schedule : mDataManager.getAvailableScheduledRecordings()) {
            addProgramIdToCheckIfNeeded(schedule);
        }
        startNextUpdateIfNeeded();
    }

    private void onProgramUpdated(long programId) {
        addProgramIdToCheckIfNeeded(mDataManager.getScheduledRecordingForProgramId(programId));
        startNextUpdateIfNeeded();
    }

    private void addProgramIdToCheckIfNeeded(ScheduledRecording schedule) {
        if (schedule == null) {
            return;
        }
        long programId = schedule.getProgramId();
        if (programId != ScheduledRecording.ID_NOT_SET
                && !mProgramIdQueue.contains(programId)
                && (schedule.getState() == ScheduledRecording.STATE_RECORDING_NOT_STARTED
                || schedule.getState() == ScheduledRecording.STATE_RECORDING_IN_PROGRESS)) {
            if (DEBUG) Log.d(TAG, "Program ID enqueued: " + programId);
            mProgramIdQueue.offer(programId);
            // There are schedules to be updated. Pause the SeriesRecordingScheduler until all the
            // schedule updates finish.
            // Note that the SeriesRecordingScheduler should be paused even though the program to
            // check is not episodic because it can be changed to the episodic program after the
            // update, which affect the SeriesRecordingScheduler.
            mSeriesRecordingScheduler.pauseUpdate();
        }
    }

    private void startNextUpdateIfNeeded() {
        if (mQueryProgramTask != null && !mQueryProgramTask.isCancelled()) {
            return;
        }
        if (!mProgramIdQueue.isEmpty()) {
            if (DEBUG) Log.d(TAG, "Program ID dequeued: " + mProgramIdQueue.peek());
            mQueryProgramTask = new QueryProgramTask(mProgramIdQueue.poll());
            mQueryProgramTask.executeOnDbThread();
        } else {
            mSeriesRecordingScheduler.resumeUpdate();
        }
    }

    @VisibleForTesting
    void handleUpdateProgram(Program program, long programId) {
        Set<SeriesRecording> seriesRecordingsToUpdate = new HashSet<>();
        ScheduledRecording schedule = mDataManager.getScheduledRecordingForProgramId(programId);
        if (schedule != null
                && (schedule.getState() == ScheduledRecording.STATE_RECORDING_NOT_STARTED
                || schedule.getState() == ScheduledRecording.STATE_RECORDING_IN_PROGRESS)) {
            if (program == null) {
                mDataManager.removeScheduledRecording(schedule);
                if (schedule.getSeriesRecordingId() != SeriesRecording.ID_NOT_SET) {
                    SeriesRecording seriesRecording =
                            mDataManager.getSeriesRecording(schedule.getSeriesRecordingId());
                    if (seriesRecording != null) {
                        seriesRecordingsToUpdate.add(seriesRecording);
                    }
                }
            } else {
                long currentTimeMs = System.currentTimeMillis();
                ScheduledRecording.Builder builder = ScheduledRecording.buildFrom(schedule)
                        .setEndTimeMs(program.getEndTimeUtcMillis())
                        .setSeasonNumber(program.getSeasonNumber())
                        .setEpisodeNumber(program.getEpisodeNumber())
                        .setEpisodeTitle(program.getEpisodeTitle())
                        .setProgramDescription(program.getDescription())
                        .setProgramLongDescription(program.getLongDescription())
                        .setProgramPosterArtUri(program.getPosterArtUri())
                        .setProgramThumbnailUri(program.getThumbnailUri());
                boolean needUpdate = false;
                // Check the series recording.
                SeriesRecording seriesRecordingForOldSchedule =
                        mDataManager.getSeriesRecording(schedule.getSeriesRecordingId());
                if (program.isEpisodic()) {
                    // New program belongs to a series.
                    SeriesRecording seriesRecording =
                            mDataManager.getSeriesRecording(program.getSeriesId());
                    if (seriesRecording == null) {
                        // The new program is episodic while the previous one isn't.
                        SeriesRecording newSeriesRecording = mDvrManager.addSeriesRecording(
                                program, Collections.singletonList(program),
                                SeriesRecording.STATE_SERIES_STOPPED);
                        builder.setSeriesRecordingId(newSeriesRecording.getId());
                        needUpdate = true;
                    } else if (seriesRecording.getId() != schedule.getSeriesRecordingId()) {
                        // The new program belongs to the other series.
                        builder.setSeriesRecordingId(seriesRecording.getId());
                        needUpdate = true;
                        seriesRecordingsToUpdate.add(seriesRecording);
                        if (seriesRecordingForOldSchedule != null) {
                            seriesRecordingsToUpdate.add(seriesRecordingForOldSchedule);
                        }
                    } else if (!Objects.equals(schedule.getSeasonNumber(),
                                    program.getSeasonNumber())
                            || !Objects.equals(schedule.getEpisodeNumber(),
                                    program.getEpisodeNumber())) {
                        // The episode number has been changed.
                        if (seriesRecordingForOldSchedule != null) {
                            seriesRecordingsToUpdate.add(seriesRecordingForOldSchedule);
                        }
                    }
                } else if (seriesRecordingForOldSchedule != null) {
                    // Old program belongs to a series but the new one doesn't.
                    seriesRecordingsToUpdate.add(seriesRecordingForOldSchedule);
                }
                // Change start time only when the recording is not started yet.
                boolean needToChangeStartTime =
                        schedule.getState() != ScheduledRecording.STATE_RECORDING_IN_PROGRESS
                        && program.getStartTimeUtcMillis() != schedule.getStartTimeMs();
                if (needToChangeStartTime) {
                    builder.setStartTimeMs(program.getStartTimeUtcMillis());
                    needUpdate = true;
                }
                if (needUpdate || schedule.getEndTimeMs() != program.getEndTimeUtcMillis()
                        || !Objects.equals(schedule.getSeasonNumber(), program.getSeasonNumber())
                        || !Objects.equals(schedule.getEpisodeNumber(), program.getEpisodeNumber())
                        || !Objects.equals(schedule.getEpisodeTitle(), program.getEpisodeTitle())
                        || !Objects.equals(schedule.getProgramDescription(),
                        program.getDescription())
                        || !Objects.equals(schedule.getProgramLongDescription(),
                        program.getLongDescription())
                        || !Objects.equals(schedule.getProgramPosterArtUri(),
                        program.getPosterArtUri())
                        || !Objects.equals(schedule.getProgramThumbnailUri(),
                        program.getThumbnailUri())) {
                    mDataManager.updateScheduledRecording(builder.build());
                }
                if (!seriesRecordingsToUpdate.isEmpty()) {
                    // The series recordings will be updated after it's resumed.
                    mSeriesRecordingScheduler.updateSchedules(seriesRecordingsToUpdate);
                }
            }
        }
    }

    private class QueryProgramTask extends AsyncQueryProgramTask {
        private final long mProgramId;

        QueryProgramTask(long programId) {
            super(mContext.getContentResolver(), programId);
            mProgramId = programId;
        }

        @Override
        protected void onCancelled(Program program) {
            if (mQueryProgramTask == this) {
                mQueryProgramTask = null;
            }
            startNextUpdateIfNeeded();
        }

        @Override
        protected void onPostExecute(Program program) {
            if (mQueryProgramTask == this) {
                mQueryProgramTask = null;
            }
            handleUpdateProgram(program, mProgramId);
            startNextUpdateIfNeeded();
        }
    }
}
