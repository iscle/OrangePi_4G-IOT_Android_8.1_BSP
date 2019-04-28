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

package com.android.tv.dvr.recorder;

import android.annotation.SuppressLint;
import android.annotation.TargetApi;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.AsyncTask;
import android.os.Build;
import android.support.annotation.MainThread;
import android.text.TextUtils;
import android.util.ArraySet;
import android.util.Log;
import android.util.LongSparseArray;

import com.android.tv.ApplicationSingletons;
import com.android.tv.TvApplication;
import com.android.tv.common.CollectionUtils;
import com.android.tv.common.SharedPreferencesUtils;
import com.android.tv.common.SoftPreconditions;
import com.android.tv.data.Program;
import com.android.tv.data.epg.EpgFetcher;
import com.android.tv.dvr.DvrDataManager;
import com.android.tv.dvr.DvrDataManager.ScheduledRecordingListener;
import com.android.tv.dvr.DvrDataManager.SeriesRecordingListener;
import com.android.tv.dvr.DvrManager;
import com.android.tv.dvr.WritableDvrDataManager;
import com.android.tv.dvr.data.SeasonEpisodeNumber;
import com.android.tv.dvr.data.ScheduledRecording;
import com.android.tv.dvr.data.SeriesInfo;
import com.android.tv.dvr.data.SeriesRecording;
import com.android.tv.dvr.provider.EpisodicProgramLoadTask;
import com.android.tv.experiments.Experiments;

import com.android.tv.util.LocationUtils;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

/**
 * Creates the {@link com.android.tv.dvr.data.ScheduledRecording}s for
 * the {@link com.android.tv.dvr.data.SeriesRecording}.
 * <p>
 * The current implementation assumes that the series recordings are scheduled only for one channel.
 */
@TargetApi(Build.VERSION_CODES.N)
public class SeriesRecordingScheduler {
    private static final String TAG = "SeriesRecordingSchd";
    private static final boolean DEBUG = false;

    private static final String KEY_FETCHED_SERIES_IDS =
            "SeriesRecordingScheduler.fetched_series_ids";

    @SuppressLint("StaticFieldLeak")
    private static SeriesRecordingScheduler sInstance;

    /**
     * Creates and returns the {@link SeriesRecordingScheduler}.
     */
    public static synchronized SeriesRecordingScheduler getInstance(Context context) {
        if (sInstance == null) {
            sInstance = new SeriesRecordingScheduler(context);
        }
        return sInstance;
    }

    private final Context mContext;
    private final DvrManager mDvrManager;
    private final WritableDvrDataManager mDataManager;
    private final List<SeriesRecordingUpdateTask> mScheduleTasks = new ArrayList<>();
    private final LongSparseArray<FetchSeriesInfoTask> mFetchSeriesInfoTasks =
            new LongSparseArray<>();
    private final Set<String> mFetchedSeriesIds = new ArraySet<>();
    private final SharedPreferences mSharedPreferences;
    private boolean mStarted;
    private boolean mPaused;
    private final Set<Long> mPendingSeriesRecordings = new ArraySet<>();

    private final SeriesRecordingListener mSeriesRecordingListener = new SeriesRecordingListener() {
        @Override
        public void onSeriesRecordingAdded(SeriesRecording... seriesRecordings) {
            for (SeriesRecording seriesRecording : seriesRecordings) {
                executeFetchSeriesInfoTask(seriesRecording);
            }
        }

        @Override
        public void onSeriesRecordingRemoved(SeriesRecording... seriesRecordings) {
            // Cancel the update.
            for (Iterator<SeriesRecordingUpdateTask> iter = mScheduleTasks.iterator();
                    iter.hasNext(); ) {
                SeriesRecordingUpdateTask task = iter.next();
                if (CollectionUtils.subtract(task.getSeriesRecordings(), seriesRecordings,
                        SeriesRecording.ID_COMPARATOR).isEmpty()) {
                    task.cancel(true);
                    iter.remove();
                }
            }
            for (SeriesRecording seriesRecording : seriesRecordings) {
                FetchSeriesInfoTask task = mFetchSeriesInfoTasks.get(seriesRecording.getId());
                if (task != null) {
                    task.cancel(true);
                    mFetchSeriesInfoTasks.remove(seriesRecording.getId());
                }
            }
        }

        @Override
        public void onSeriesRecordingChanged(SeriesRecording... seriesRecordings) {
            List<SeriesRecording> stopped = new ArrayList<>();
            List<SeriesRecording> normal = new ArrayList<>();
            for (SeriesRecording r : seriesRecordings) {
                if (r.isStopped()) {
                    stopped.add(r);
                } else {
                    normal.add(r);
                }
            }
            if (!stopped.isEmpty()) {
                onSeriesRecordingRemoved(SeriesRecording.toArray(stopped));
            }
            if (!normal.isEmpty()) {
                updateSchedules(normal);
            }
        }
    };

    private final ScheduledRecordingListener mScheduledRecordingListener =
            new ScheduledRecordingListener() {
                @Override
                public void onScheduledRecordingAdded(ScheduledRecording... schedules) {
                    // No need to update series recordings when the new schedule is added.
                }

                @Override
                public void onScheduledRecordingRemoved(ScheduledRecording... schedules) {
                    handleScheduledRecordingChange(Arrays.asList(schedules));
                }

                @Override
                public void onScheduledRecordingStatusChanged(ScheduledRecording... schedules) {
                    List<ScheduledRecording> schedulesForUpdate = new ArrayList<>();
                    for (ScheduledRecording r : schedules) {
                        if ((r.getState() == ScheduledRecording.STATE_RECORDING_FAILED
                                || r.getState() == ScheduledRecording.STATE_RECORDING_CLIPPED)
                                && r.getSeriesRecordingId() != SeriesRecording.ID_NOT_SET
                                && !TextUtils.isEmpty(r.getSeasonNumber())
                                && !TextUtils.isEmpty(r.getEpisodeNumber())) {
                            schedulesForUpdate.add(r);
                        }
                    }
                    if (!schedulesForUpdate.isEmpty()) {
                        handleScheduledRecordingChange(schedulesForUpdate);
                    }
                }

                private void handleScheduledRecordingChange(List<ScheduledRecording> schedules) {
                    if (schedules.isEmpty()) {
                        return;
                    }
                    Set<Long> seriesRecordingIds = new HashSet<>();
                    for (ScheduledRecording r : schedules) {
                        if (r.getSeriesRecordingId() != SeriesRecording.ID_NOT_SET) {
                            seriesRecordingIds.add(r.getSeriesRecordingId());
                        }
                    }
                    if (!seriesRecordingIds.isEmpty()) {
                        List<SeriesRecording> seriesRecordings = new ArrayList<>();
                        for (Long id : seriesRecordingIds) {
                            SeriesRecording seriesRecording = mDataManager.getSeriesRecording(id);
                            if (seriesRecording != null) {
                                seriesRecordings.add(seriesRecording);
                            }
                        }
                        if (!seriesRecordings.isEmpty()) {
                            updateSchedules(seriesRecordings);
                        }
                    }
                }
            };

    private SeriesRecordingScheduler(Context context) {
        mContext = context.getApplicationContext();
        ApplicationSingletons appSingletons = TvApplication.getSingletons(context);
        mDvrManager = appSingletons.getDvrManager();
        mDataManager = (WritableDvrDataManager) appSingletons.getDvrDataManager();
        mSharedPreferences = context.getSharedPreferences(
                SharedPreferencesUtils.SHARED_PREF_SERIES_RECORDINGS, Context.MODE_PRIVATE);
        mFetchedSeriesIds.addAll(mSharedPreferences.getStringSet(KEY_FETCHED_SERIES_IDS,
                Collections.emptySet()));
    }

    /**
     * Starts the scheduler.
     */
    @MainThread
    public void start() {
        SoftPreconditions.checkState(mDataManager.isInitialized());
        if (mStarted) {
            return;
        }
        if (DEBUG) Log.d(TAG, "start");
        mStarted = true;
        mDataManager.addSeriesRecordingListener(mSeriesRecordingListener);
        mDataManager.addScheduledRecordingListener(mScheduledRecordingListener);
        startFetchingSeriesInfo();
        updateSchedules(mDataManager.getSeriesRecordings());
    }

    @MainThread
    public void stop() {
        if (!mStarted) {
            return;
        }
        if (DEBUG) Log.d(TAG, "stop");
        mStarted = false;
        for (int i = 0; i < mFetchSeriesInfoTasks.size(); i++) {
            FetchSeriesInfoTask task = mFetchSeriesInfoTasks.get(mFetchSeriesInfoTasks.keyAt(i));
            task.cancel(true);
        }
        mFetchSeriesInfoTasks.clear();
        for (SeriesRecordingUpdateTask task : mScheduleTasks) {
            task.cancel(true);
        }
        mScheduleTasks.clear();
        mDataManager.removeScheduledRecordingListener(mScheduledRecordingListener);
        mDataManager.removeSeriesRecordingListener(mSeriesRecordingListener);
    }

    private void startFetchingSeriesInfo() {
        for (SeriesRecording seriesRecording : mDataManager.getSeriesRecordings()) {
            if (!mFetchedSeriesIds.contains(seriesRecording.getSeriesId())) {
                executeFetchSeriesInfoTask(seriesRecording);
            }
        }
    }

    private void executeFetchSeriesInfoTask(SeriesRecording seriesRecording) {
        if (Experiments.CLOUD_EPG.get()) {
            FetchSeriesInfoTask task = new FetchSeriesInfoTask(seriesRecording);
            task.execute();
            mFetchSeriesInfoTasks.put(seriesRecording.getId(), task);
        }
    }

    /**
     * Pauses the updates of the series recordings.
     */
    public void pauseUpdate() {
        if (DEBUG) Log.d(TAG, "Schedule paused");
        if (mPaused) {
            return;
        }
        mPaused = true;
        if (!mStarted) {
            return;
        }
        for (SeriesRecordingUpdateTask task : mScheduleTasks) {
            for (SeriesRecording r : task.getSeriesRecordings()) {
                mPendingSeriesRecordings.add(r.getId());
            }
            task.cancel(true);
        }
    }

    /**
     * Resumes the updates of the series recordings.
     */
    public void resumeUpdate() {
        if (DEBUG) Log.d(TAG, "Schedule resumed");
        if (!mPaused) {
            return;
        }
        mPaused = false;
        if (!mStarted) {
            return;
        }
        if (!mPendingSeriesRecordings.isEmpty()) {
            List<SeriesRecording> seriesRecordings = new ArrayList<>();
            for (long seriesRecordingId : mPendingSeriesRecordings) {
                SeriesRecording seriesRecording =
                        mDataManager.getSeriesRecording(seriesRecordingId);
                if (seriesRecording != null) {
                    seriesRecordings.add(seriesRecording);
                }
            }
            if (!seriesRecordings.isEmpty()) {
                updateSchedules(seriesRecordings);
            }
        }
    }

    /**
     * Update schedules for the given series recordings. If it's paused, the update will be done
     * after it's resumed.
     */
    public void updateSchedules(Collection<SeriesRecording> seriesRecordings) {
        if (DEBUG) Log.d(TAG, "updateSchedules:" + seriesRecordings);
        if (!mStarted) {
            if (DEBUG) Log.d(TAG, "Not started yet.");
            return;
        }
        if (mPaused) {
            for (SeriesRecording r : seriesRecordings) {
                mPendingSeriesRecordings.add(r.getId());
            }
            if (DEBUG) {
                Log.d(TAG, "The scheduler has been paused. Adding to the pending list. size="
                        + mPendingSeriesRecordings.size());
            }
            return;
        }
        Set<SeriesRecording> previousSeriesRecordings = new HashSet<>();
        for (Iterator<SeriesRecordingUpdateTask> iter = mScheduleTasks.iterator();
             iter.hasNext(); ) {
            SeriesRecordingUpdateTask task = iter.next();
            if (CollectionUtils.containsAny(task.getSeriesRecordings(), seriesRecordings,
                    SeriesRecording.ID_COMPARATOR)) {
                // The task is affected by the seriesRecordings
                task.cancel(true);
                previousSeriesRecordings.addAll(task.getSeriesRecordings());
                iter.remove();
            }
        }
        List<SeriesRecording> seriesRecordingsToUpdate = CollectionUtils.union(seriesRecordings,
                previousSeriesRecordings, SeriesRecording.ID_COMPARATOR);
        for (Iterator<SeriesRecording> iter = seriesRecordingsToUpdate.iterator();
                iter.hasNext(); ) {
            SeriesRecording seriesRecording = mDataManager.getSeriesRecording(iter.next().getId());
            if (seriesRecording == null || seriesRecording.isStopped()) {
                // Series recording has been removed or stopped.
                iter.remove();
            }
        }
        if (seriesRecordingsToUpdate.isEmpty()) {
            return;
        }
        if (needToReadAllChannels(seriesRecordingsToUpdate)) {
            SeriesRecordingUpdateTask task =
                    new SeriesRecordingUpdateTask(seriesRecordingsToUpdate);
            mScheduleTasks.add(task);
            if (DEBUG) Log.d(TAG, "Added schedule task: " + task);
            task.execute();
        } else {
            for (SeriesRecording seriesRecording : seriesRecordingsToUpdate) {
                SeriesRecordingUpdateTask task = new SeriesRecordingUpdateTask(
                        Collections.singletonList(seriesRecording));
                mScheduleTasks.add(task);
                if (DEBUG) Log.d(TAG, "Added schedule task: " + task);
                task.execute();
            }
        }
    }

    private boolean needToReadAllChannels(List<SeriesRecording> seriesRecordingsToUpdate) {
        for (SeriesRecording seriesRecording : seriesRecordingsToUpdate) {
            if (seriesRecording.getChannelOption() == SeriesRecording.OPTION_CHANNEL_ALL) {
                return true;
            }
        }
        return false;
    }

    /**
     * Pick one program per an episode.
     *
     * <p>Note that the programs which has been already scheduled have the highest priority, and all
     * of them are added even though they are the same episodes. That's because the schedules
     * should be added to the series recording.
     * <p>If there are no existing schedules for an episode, one program which starts earlier is
     * picked.
     */
    private LongSparseArray<List<Program>> pickOneProgramPerEpisode(
            List<SeriesRecording> seriesRecordings, List<Program> programs) {
        return pickOneProgramPerEpisode(mDataManager, seriesRecordings, programs);
    }

    /**
     * @see #pickOneProgramPerEpisode(List, List)
     */
    public static LongSparseArray<List<Program>> pickOneProgramPerEpisode(
            DvrDataManager dataManager, List<SeriesRecording> seriesRecordings,
            List<Program> programs) {
        // Initialize.
        LongSparseArray<List<Program>> result = new LongSparseArray<>();
        Map<String, Long> seriesRecordingIds = new HashMap<>();
        for (SeriesRecording seriesRecording : seriesRecordings) {
            result.put(seriesRecording.getId(), new ArrayList<>());
            seriesRecordingIds.put(seriesRecording.getSeriesId(), seriesRecording.getId());
        }
        // Group programs by the episode.
        Map<SeasonEpisodeNumber, List<Program>> programsForEpisodeMap = new HashMap<>();
        for (Program program : programs) {
            long seriesRecordingId = seriesRecordingIds.get(program.getSeriesId());
            if (TextUtils.isEmpty(program.getSeasonNumber())
                    || TextUtils.isEmpty(program.getEpisodeNumber())) {
                // Add all the programs if it doesn't have season number or episode number.
                result.get(seriesRecordingId).add(program);
                continue;
            }
            SeasonEpisodeNumber seasonEpisodeNumber = new SeasonEpisodeNumber(seriesRecordingId,
                    program.getSeasonNumber(), program.getEpisodeNumber());
            List<Program> programsForEpisode = programsForEpisodeMap.get(seasonEpisodeNumber);
            if (programsForEpisode == null) {
                programsForEpisode = new ArrayList<>();
                programsForEpisodeMap.put(seasonEpisodeNumber, programsForEpisode);
            }
            programsForEpisode.add(program);
        }
        // Pick one program.
        for (Entry<SeasonEpisodeNumber, List<Program>> entry : programsForEpisodeMap.entrySet()) {
            List<Program> programsForEpisode = entry.getValue();
            Collections.sort(programsForEpisode, new Comparator<Program>() {
                @Override
                public int compare(Program lhs, Program rhs) {
                    // Place the existing schedule first.
                    boolean lhsScheduled = isProgramScheduled(dataManager, lhs);
                    boolean rhsScheduled = isProgramScheduled(dataManager, rhs);
                    if (lhsScheduled && !rhsScheduled) {
                        return -1;
                    }
                    if (!lhsScheduled && rhsScheduled) {
                        return 1;
                    }
                    // Sort by the start time in ascending order.
                    return lhs.compareTo(rhs);
                }
            });
            boolean added = false;
            // Add all the scheduled programs
            List<Program> programsForSeries = result.get(entry.getKey().seriesRecordingId);
            for (Program program : programsForEpisode) {
                if (isProgramScheduled(dataManager, program)) {
                    programsForSeries.add(program);
                    added = true;
                } else if (!added) {
                    programsForSeries.add(program);
                    break;
                }
            }
        }
        return result;
    }

    private static boolean isProgramScheduled(DvrDataManager dataManager, Program program) {
        ScheduledRecording schedule =
                dataManager.getScheduledRecordingForProgramId(program.getId());
        return schedule != null && schedule.getState()
                == ScheduledRecording.STATE_RECORDING_NOT_STARTED;
    }

    private void updateFetchedSeries() {
        mSharedPreferences.edit().putStringSet(KEY_FETCHED_SERIES_IDS, mFetchedSeriesIds).apply();
    }

    /**
     * This works only for the existing series recordings. Do not use this task for the
     * "adding series recording" UI.
     */
    private class SeriesRecordingUpdateTask extends EpisodicProgramLoadTask {
        SeriesRecordingUpdateTask(List<SeriesRecording> seriesRecordings) {
            super(mContext, seriesRecordings);
        }

        @Override
        protected void onPostExecute(List<Program> programs) {
            if (DEBUG) Log.d(TAG, "onPostExecute: updating schedules with programs:" + programs);
            mScheduleTasks.remove(this);
            if (programs == null) {
                Log.e(TAG, "Creating schedules for series recording failed: "
                        + getSeriesRecordings());
                return;
            }
            LongSparseArray<List<Program>> seriesProgramMap = pickOneProgramPerEpisode(
                    getSeriesRecordings(), programs);
            for (SeriesRecording seriesRecording : getSeriesRecordings()) {
                // Check the series recording is still valid.
                SeriesRecording actualSeriesRecording = mDataManager.getSeriesRecording(
                        seriesRecording.getId());
                if (actualSeriesRecording == null || actualSeriesRecording.isStopped()) {
                    continue;
                }
                List<Program> programsToSchedule = seriesProgramMap.get(seriesRecording.getId());
                if (mDataManager.getSeriesRecording(seriesRecording.getId()) != null
                        && !programsToSchedule.isEmpty()) {
                    mDvrManager.addScheduleToSeriesRecording(seriesRecording, programsToSchedule);
                }
            }
        }

        @Override
        protected void onCancelled(List<Program> programs) {
            mScheduleTasks.remove(this);
        }

        @Override
        public String toString() {
            return "SeriesRecordingUpdateTask:{"
                    + "series_recordings=" + getSeriesRecordings()
                    + "}";
        }
    }

    private class FetchSeriesInfoTask extends AsyncTask<Void, Void, SeriesInfo> {
        private SeriesRecording mSeriesRecording;

        FetchSeriesInfoTask(SeriesRecording seriesRecording) {
            mSeriesRecording = seriesRecording;
        }

        @Override
        protected SeriesInfo doInBackground(Void... voids) {
            return EpgFetcher.createEpgReader(mContext, LocationUtils.getCurrentCountry(mContext))
                    .getSeriesInfo(mSeriesRecording.getSeriesId());
        }

        @Override
        protected void onPostExecute(SeriesInfo seriesInfo) {
            if (seriesInfo != null) {
                mDataManager.updateSeriesRecording(SeriesRecording.buildFrom(mSeriesRecording)
                        .setTitle(seriesInfo.getTitle())
                        .setDescription(seriesInfo.getDescription())
                        .setLongDescription(seriesInfo.getLongDescription())
                        .setCanonicalGenreIds(seriesInfo.getCanonicalGenreIds())
                        .setPosterUri(seriesInfo.getPosterUri())
                        .setPhotoUri(seriesInfo.getPhotoUri())
                        .build());
                mFetchedSeriesIds.add(seriesInfo.getId());
                updateFetchedSeries();
            }
            mFetchSeriesInfoTasks.remove(mSeriesRecording.getId());
        }

        @Override
        protected void onCancelled(SeriesInfo seriesInfo) {
            mFetchSeriesInfoTasks.remove(mSeriesRecording.getId());
        }
    }
}
