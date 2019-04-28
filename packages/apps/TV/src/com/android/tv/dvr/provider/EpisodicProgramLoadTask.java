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

import android.annotation.TargetApi;
import android.content.Context;
import android.database.Cursor;
import android.media.tv.TvContract;
import android.media.tv.TvContract.Programs;
import android.net.Uri;
import android.os.Build;
import android.support.annotation.Nullable;
import android.support.annotation.WorkerThread;

import com.android.tv.TvApplication;
import com.android.tv.common.SoftPreconditions;
import com.android.tv.data.Program;
import com.android.tv.dvr.DvrDataManager;
import com.android.tv.dvr.data.SeasonEpisodeNumber;
import com.android.tv.dvr.data.ScheduledRecording;
import com.android.tv.dvr.data.SeriesRecording;
import com.android.tv.util.AsyncDbTask.AsyncProgramQueryTask;
import com.android.tv.util.AsyncDbTask.CursorFilter;
import com.android.tv.util.PermissionUtils;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * A wrapper of AsyncProgramQueryTask to load the episodic programs for the series recordings.
 */
@TargetApi(Build.VERSION_CODES.N)
abstract public class EpisodicProgramLoadTask {
    private static final String TAG = "EpisodicProgramLoadTask";

    private static final int PROGRAM_ID_INDEX = Program.getColumnIndex(Programs._ID);
    private static final int START_TIME_INDEX =
            Program.getColumnIndex(Programs.COLUMN_START_TIME_UTC_MILLIS);
    private static final int RECORDING_PROHIBITED_INDEX =
            Program.getColumnIndex(Programs.COLUMN_RECORDING_PROHIBITED);

    private static final String PARAM_START_TIME = "start_time";
    private static final String PARAM_END_TIME = "end_time";

    private static final String PROGRAM_PREDICATE =
            Programs.COLUMN_START_TIME_UTC_MILLIS + ">? AND "
                    + Programs.COLUMN_RECORDING_PROHIBITED + "=0";
    private static final String PROGRAM_PREDICATE_WITH_CURRENT_PROGRAM =
            Programs.COLUMN_END_TIME_UTC_MILLIS + ">? AND "
                    + Programs.COLUMN_RECORDING_PROHIBITED + "=0";
    private static final String CHANNEL_ID_PREDICATE = Programs.COLUMN_CHANNEL_ID + "=?";
    private static final String PROGRAM_TITLE_PREDICATE = Programs.COLUMN_TITLE + "=?";

    private final Context mContext;
    private final DvrDataManager mDataManager;
    private boolean mQueryAllChannels;
    private boolean mLoadCurrentProgram;
    private boolean mLoadScheduledEpisode;
    private boolean mLoadDisallowedProgram;
    // If true, match programs with OPTION_CHANNEL_ALL.
    private boolean mIgnoreChannelOption;
    private final ArrayList<SeriesRecording> mSeriesRecordings = new ArrayList<>();
    private AsyncProgramQueryTask mProgramQueryTask;

    /**
     *
     * Constructor used to load programs for one series recording with the given channel option.
     */
    public EpisodicProgramLoadTask(Context context, SeriesRecording seriesRecording) {
        this(context, Collections.singletonList(seriesRecording));
    }

    /**
     * Constructor used to load programs for multiple series recordings. The channel option is
     * {@link SeriesRecording#OPTION_CHANNEL_ALL}.
     */
    public EpisodicProgramLoadTask(Context context, Collection<SeriesRecording> seriesRecordings) {
        mContext = context.getApplicationContext();
        mDataManager = TvApplication.getSingletons(context).getDvrDataManager();
        mSeriesRecordings.addAll(seriesRecordings);
    }

    /**
     * Returns the series recordings.
     */
    public List<SeriesRecording> getSeriesRecordings() {
        return mSeriesRecordings;
    }

    /**
     * Returns the program query task. It is {@code null} until it is executed.
     */
    @Nullable
    public AsyncProgramQueryTask getTask() {
        return mProgramQueryTask;
    }

    /**
     * Enables loading current programs. The default value is {@code false}.
     */
    public EpisodicProgramLoadTask setLoadCurrentProgram(boolean loadCurrentProgram) {
        SoftPreconditions.checkState(mProgramQueryTask == null, TAG,
                "Can't change setting after execution.");
        mLoadCurrentProgram = loadCurrentProgram;
        return this;
    }

    /**
     * Enables already schedules episodes. The default value is {@code false}.
     */
    public EpisodicProgramLoadTask setLoadScheduledEpisode(boolean loadScheduledEpisode) {
        SoftPreconditions.checkState(mProgramQueryTask == null, TAG,
                "Can't change setting after execution.");
        mLoadScheduledEpisode = loadScheduledEpisode;
        return this;
    }

    /**
     * Enables loading disallowed programs whose schedules were removed manually by the user.
     * The default value is {@code false}.
     */
    public EpisodicProgramLoadTask setLoadDisallowedProgram(boolean loadDisallowedProgram) {
        SoftPreconditions.checkState(mProgramQueryTask == null, TAG,
                "Can't change setting after execution.");
        mLoadDisallowedProgram = loadDisallowedProgram;
        return this;
    }

    /**
     * Gives the option whether to ignore the channel option when matching programs.
     * If {@code ignoreChannelOption} is {@code true}, the program will be matched with
     * {@link SeriesRecording#OPTION_CHANNEL_ALL} option.
     */
    public EpisodicProgramLoadTask setIgnoreChannelOption(boolean ignoreChannelOption) {
        SoftPreconditions.checkState(mProgramQueryTask == null, TAG,
                "Can't change setting after execution.");
        mIgnoreChannelOption = ignoreChannelOption;
        return this;
    }

    /**
     * Executes the task.
     *
     * @see com.android.tv.util.AsyncDbTask#executeOnDbThread
     */
    public void execute() {
        if (SoftPreconditions.checkState(mProgramQueryTask == null, TAG,
                "Can't execute task: the task is already running.")) {
            mQueryAllChannels = mSeriesRecordings.size() > 1
                    || mSeriesRecordings.get(0).getChannelOption()
                            == SeriesRecording.OPTION_CHANNEL_ALL
                    || mIgnoreChannelOption;
            mProgramQueryTask = createTask();
            mProgramQueryTask.executeOnDbThread();
        }
    }

    /**
     * Cancels the task.
     *
     * @see android.os.AsyncTask#cancel
     */
    public void cancel(boolean mayInterruptIfRunning) {
        if (mProgramQueryTask != null) {
            mProgramQueryTask.cancel(mayInterruptIfRunning);
        }
    }

    /**
     * Runs on the UI thread after the program loading finishes successfully.
     */
    protected void onPostExecute(List<Program> programs) {
    }

    /**
     * Runs on the UI thread after the program loading was canceled.
     */
    protected void onCancelled(List<Program> programs) {
    }

    private AsyncProgramQueryTask createTask() {
        SqlParams sqlParams = createSqlParams();
        return new AsyncProgramQueryTask(mContext.getContentResolver(), sqlParams.uri,
                sqlParams.selection, sqlParams.selectionArgs, null, sqlParams.filter) {
            @Override
            protected void onPostExecute(List<Program> programs) {
                EpisodicProgramLoadTask.this.onPostExecute(programs);
            }

            @Override
            protected void onCancelled(List<Program> programs) {
                EpisodicProgramLoadTask.this.onCancelled(programs);
            }
        };
    }

    private SqlParams createSqlParams() {
        SqlParams sqlParams = new SqlParams();
        if (PermissionUtils.hasAccessAllEpg(mContext)) {
            sqlParams.uri = Programs.CONTENT_URI;
            // Base
            StringBuilder selection = new StringBuilder(mLoadCurrentProgram
                    ? PROGRAM_PREDICATE_WITH_CURRENT_PROGRAM : PROGRAM_PREDICATE);
            List<String> args = new ArrayList<>();
            args.add(Long.toString(System.currentTimeMillis()));
            // Channel option
            if (!mQueryAllChannels) {
                selection.append(" AND ").append(CHANNEL_ID_PREDICATE);
                args.add(Long.toString(mSeriesRecordings.get(0).getChannelId()));
            }
            // Title
            if (mSeriesRecordings.size() == 1) {
                selection.append(" AND ").append(PROGRAM_TITLE_PREDICATE);
                args.add(mSeriesRecordings.get(0).getTitle());
            }
            sqlParams.selection = selection.toString();
            sqlParams.selectionArgs = args.toArray(new String[args.size()]);
            sqlParams.filter = new SeriesRecordingCursorFilter(mSeriesRecordings);
        } else {
            // The query includes the current program. Will be filtered if needed.
            if (mQueryAllChannels) {
                sqlParams.uri = Programs.CONTENT_URI.buildUpon()
                        .appendQueryParameter(PARAM_START_TIME,
                                String.valueOf(System.currentTimeMillis()))
                        .appendQueryParameter(PARAM_END_TIME, String.valueOf(Long.MAX_VALUE))
                        .build();
            } else {
                sqlParams.uri = TvContract.buildProgramsUriForChannel(
                        mSeriesRecordings.get(0).getChannelId(),
                        System.currentTimeMillis(), Long.MAX_VALUE);
            }
            sqlParams.selection = null;
            sqlParams.selectionArgs = null;
            sqlParams.filter = new SeriesRecordingCursorFilterForNonSystem(mSeriesRecordings);
        }
        return sqlParams;
    }

    /**
     * Filter the programs which match the series recording. The episodes which the schedules are
     * already created for are filtered out too.
     */
    private class SeriesRecordingCursorFilter implements CursorFilter {
        private final Set<Long> mDisallowedProgramIds = new HashSet<>();
        private final Set<SeasonEpisodeNumber> mSeasonEpisodeNumbers = new HashSet<>();

        SeriesRecordingCursorFilter(List<SeriesRecording> seriesRecordings) {
            if (!mLoadDisallowedProgram) {
                mDisallowedProgramIds.addAll(mDataManager.getDisallowedProgramIds());
            }
            if (!mLoadScheduledEpisode) {
                Set<Long> seriesRecordingIds = new HashSet<>();
                for (SeriesRecording r : seriesRecordings) {
                    seriesRecordingIds.add(r.getId());
                }
                for (ScheduledRecording r : mDataManager.getAllScheduledRecordings()) {
                    if (seriesRecordingIds.contains(r.getSeriesRecordingId())
                            && r.getState() != ScheduledRecording.STATE_RECORDING_FAILED
                            && r.getState() != ScheduledRecording.STATE_RECORDING_CLIPPED) {
                        mSeasonEpisodeNumbers.add(new SeasonEpisodeNumber(r));
                    }
                }
            }
        }

        @Override
        @WorkerThread
        public boolean filter(Cursor c) {
            if (!mLoadDisallowedProgram
                    && mDisallowedProgramIds.contains(c.getLong(PROGRAM_ID_INDEX))) {
                return false;
            }
            Program program = Program.fromCursor(c);
            for (SeriesRecording seriesRecording : mSeriesRecordings) {
                boolean programMatches;
                if (mIgnoreChannelOption) {
                    programMatches = seriesRecording.matchProgram(program,
                            SeriesRecording.OPTION_CHANNEL_ALL);
                } else {
                    programMatches = seriesRecording.matchProgram(program);
                }
                if (programMatches) {
                    return mLoadScheduledEpisode
                            || !mSeasonEpisodeNumbers.contains(new SeasonEpisodeNumber(
                            seriesRecording.getId(), program.getSeasonNumber(),
                            program.getEpisodeNumber()));
                }
            }
            return false;
        }
    }

    private class SeriesRecordingCursorFilterForNonSystem extends SeriesRecordingCursorFilter {
        SeriesRecordingCursorFilterForNonSystem(List<SeriesRecording> seriesRecordings) {
            super(seriesRecordings);
        }

        @Override
        public boolean filter(Cursor c) {
            return (mLoadCurrentProgram || c.getLong(START_TIME_INDEX) > System.currentTimeMillis())
                    && c.getInt(RECORDING_PROHIBITED_INDEX) != 0 && super.filter(c);
        }
    }

    private static class SqlParams {
        public Uri uri;
        public String selection;
        public String[] selectionArgs;
        public CursorFilter filter;
    }
}
