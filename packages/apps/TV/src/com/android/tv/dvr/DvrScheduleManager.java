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
 * limitations under the License.
 */

package com.android.tv.dvr;

import android.annotation.TargetApi;
import android.content.Context;
import android.media.tv.TvInputInfo;
import android.os.Build;
import android.support.annotation.MainThread;
import android.support.annotation.NonNull;
import android.support.annotation.VisibleForTesting;
import android.util.ArraySet;
import android.util.Range;

import com.android.tv.ApplicationSingletons;
import com.android.tv.TvApplication;
import com.android.tv.common.SoftPreconditions;
import com.android.tv.data.Channel;
import com.android.tv.data.ChannelDataManager;
import com.android.tv.data.Program;
import com.android.tv.dvr.DvrDataManager.OnDvrScheduleLoadFinishedListener;
import com.android.tv.dvr.DvrDataManager.ScheduledRecordingListener;
import com.android.tv.dvr.recorder.InputTaskScheduler;
import com.android.tv.util.CompositeComparator;
import com.android.tv.dvr.data.ScheduledRecording;
import com.android.tv.dvr.data.SeriesRecording;
import com.android.tv.util.Utils;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArraySet;

/**
 * A class to manage the schedules.
 */
@TargetApi(Build.VERSION_CODES.N)
@MainThread
public class DvrScheduleManager {
    private static final String TAG = "DvrScheduleManager";

    /**
     * The default priority of scheduled recording.
     */
    public static final long DEFAULT_PRIORITY = Long.MAX_VALUE >> 1;
    /**
     * The default priority of series recording.
     */
    public static final long DEFAULT_SERIES_PRIORITY = DEFAULT_PRIORITY >> 1;
    // The new priority will have the offset from the existing one.
    private static final long PRIORITY_OFFSET = 1024;

    private static final Comparator<ScheduledRecording> RESULT_COMPARATOR =
            new CompositeComparator<>(
                    ScheduledRecording.PRIORITY_COMPARATOR.reversed(),
                    ScheduledRecording.START_TIME_COMPARATOR,
                    ScheduledRecording.ID_COMPARATOR.reversed());

    // The candidate comparator should be the consistent with
    // InputTaskScheduler#CANDIDATE_COMPARATOR.
    private static final Comparator<ScheduledRecording> CANDIDATE_COMPARATOR =
            new CompositeComparator<>(
                    ScheduledRecording.PRIORITY_COMPARATOR,
                    ScheduledRecording.END_TIME_COMPARATOR,
                    ScheduledRecording.ID_COMPARATOR);

    private final Context mContext;
    private final DvrDataManagerImpl mDataManager;
    private final ChannelDataManager mChannelDataManager;

    private final Map<String, List<ScheduledRecording>> mInputScheduleMap = new HashMap<>();
    // The inner map is a hash map from scheduled recording to its conflicting status, i.e.,
    // the boolean value true denotes the schedule is just partially conflicting, which means
    // although there's conflict, it might still be recorded partially.
    private final Map<String, Map<Long, ConflictInfo>> mInputConflictInfoMap = new HashMap<>();

    private boolean mInitialized;

    private final Set<OnInitializeListener> mOnInitializeListeners = new CopyOnWriteArraySet<>();
    private final Set<ScheduledRecordingListener> mScheduledRecordingListeners = new ArraySet<>();
    private final Set<OnConflictStateChangeListener> mOnConflictStateChangeListeners =
            new ArraySet<>();

    public DvrScheduleManager(Context context) {
        mContext = context;
        ApplicationSingletons appSingletons = TvApplication.getSingletons(context);
        mDataManager = (DvrDataManagerImpl) appSingletons.getDvrDataManager();
        mChannelDataManager = appSingletons.getChannelDataManager();
        if (mDataManager.isDvrScheduleLoadFinished() && mChannelDataManager.isDbLoadFinished()) {
            buildData();
        } else {
            mDataManager.addDvrScheduleLoadFinishedListener(
                    new OnDvrScheduleLoadFinishedListener() {
                        @Override
                        public void onDvrScheduleLoadFinished() {
                            mDataManager.removeDvrScheduleLoadFinishedListener(this);
                            if (mChannelDataManager.isDbLoadFinished() && !mInitialized) {
                                buildData();
                            }
                        }
                    });
        }
        ScheduledRecordingListener scheduledRecordingListener = new ScheduledRecordingListener() {
            @Override
            public void onScheduledRecordingAdded(ScheduledRecording... scheduledRecordings) {
                if (!mInitialized) {
                    return;
                }
                for (ScheduledRecording schedule : scheduledRecordings) {
                    if (!schedule.isNotStarted() && !schedule.isInProgress()) {
                        continue;
                    }
                    TvInputInfo input = Utils
                            .getTvInputInfoForInputId(mContext, schedule.getInputId());
                    if (!SoftPreconditions.checkArgument(input != null, TAG,
                            "Input was removed for : " + schedule)) {
                        // Input removed.
                        mInputScheduleMap.remove(schedule.getInputId());
                        mInputConflictInfoMap.remove(schedule.getInputId());
                        continue;
                    }
                    String inputId = input.getId();
                    List<ScheduledRecording> schedules = mInputScheduleMap.get(inputId);
                    if (schedules == null) {
                        schedules = new ArrayList<>();
                        mInputScheduleMap.put(inputId, schedules);
                    }
                    schedules.add(schedule);
                }
                onSchedulesChanged();
                notifyScheduledRecordingAdded(scheduledRecordings);
            }

            @Override
            public void onScheduledRecordingRemoved(ScheduledRecording... scheduledRecordings) {
                if (!mInitialized) {
                    return;
                }
                for (ScheduledRecording schedule : scheduledRecordings) {
                    TvInputInfo input = Utils
                            .getTvInputInfoForInputId(mContext, schedule.getInputId());
                    if (input == null) {
                        // Input removed.
                        mInputScheduleMap.remove(schedule.getInputId());
                        mInputConflictInfoMap.remove(schedule.getInputId());
                        continue;
                    }
                    String inputId = input.getId();
                    List<ScheduledRecording> schedules = mInputScheduleMap.get(inputId);
                    if (schedules != null) {
                        schedules.remove(schedule);
                        if (schedules.isEmpty()) {
                            mInputScheduleMap.remove(inputId);
                        }
                    }
                    Map<Long, ConflictInfo> conflictInfo = mInputConflictInfoMap.get(inputId);
                    if (conflictInfo != null) {
                        conflictInfo.remove(schedule.getId());
                        if (conflictInfo.isEmpty()) {
                            mInputConflictInfoMap.remove(inputId);
                        }
                    }
                }
                onSchedulesChanged();
                notifyScheduledRecordingRemoved(scheduledRecordings);
            }

            @Override
            public void onScheduledRecordingStatusChanged(
                    ScheduledRecording... scheduledRecordings) {
                if (!mInitialized) {
                    return;
                }
                for (ScheduledRecording schedule : scheduledRecordings) {
                    TvInputInfo input = Utils
                            .getTvInputInfoForInputId(mContext, schedule.getInputId());
                    if (!SoftPreconditions.checkArgument(input != null, TAG,
                            "Input was removed for : " + schedule)) {
                        // Input removed.
                        mInputScheduleMap.remove(schedule.getInputId());
                        mInputConflictInfoMap.remove(schedule.getInputId());
                        continue;
                    }
                    String inputId = input.getId();
                    List<ScheduledRecording> schedules = mInputScheduleMap.get(inputId);
                    if (schedules == null) {
                        schedules = new ArrayList<>();
                        mInputScheduleMap.put(inputId, schedules);
                    }
                    // Compare ID because ScheduledRecording.equals() doesn't work if the state
                    // is changed.
                    for (Iterator<ScheduledRecording> i = schedules.iterator(); i.hasNext(); ) {
                        if (i.next().getId() == schedule.getId()) {
                            i.remove();
                            break;
                        }
                    }
                    if (schedule.isNotStarted() || schedule.isInProgress()) {
                        schedules.add(schedule);
                    }
                    if (schedules.isEmpty()) {
                        mInputScheduleMap.remove(inputId);
                    }
                    // Update conflict list as well
                    Map<Long, ConflictInfo> conflictInfo = mInputConflictInfoMap.get(inputId);
                    if (conflictInfo != null) {
                        ConflictInfo oldConflictInfo = conflictInfo.get(schedule.getId());
                        if (oldConflictInfo != null) {
                            oldConflictInfo.schedule = schedule;
                        }
                    }
                }
                onSchedulesChanged();
                notifyScheduledRecordingStatusChanged(scheduledRecordings);
            }
        };
        mDataManager.addScheduledRecordingListener(scheduledRecordingListener);
        ChannelDataManager.Listener channelDataManagerListener = new ChannelDataManager.Listener() {
            @Override
            public void onLoadFinished() {
                if (mDataManager.isDvrScheduleLoadFinished() && !mInitialized) {
                    buildData();
                }
            }

            @Override
            public void onChannelListUpdated() {
                if (mDataManager.isDvrScheduleLoadFinished()) {
                    buildData();
                }
            }

            @Override
            public void onChannelBrowsableChanged() {
            }
        };
        mChannelDataManager.addListener(channelDataManagerListener);
    }

    /**
     * Returns the started recordings for the given input.
     */
    private List<ScheduledRecording> getStartedRecordings(String inputId) {
        if (!SoftPreconditions.checkState(mInitialized, TAG, "Not initialized yet")) {
            return Collections.emptyList();
        }
        List<ScheduledRecording> result = new ArrayList<>();
        List<ScheduledRecording> schedules = mInputScheduleMap.get(inputId);
        if (schedules != null) {
            for (ScheduledRecording schedule : schedules) {
                if (schedule.getState() == ScheduledRecording.STATE_RECORDING_IN_PROGRESS) {
                    result.add(schedule);
                }
            }
        }
        return result;
    }

    private void buildData() {
        mInputScheduleMap.clear();
        for (ScheduledRecording schedule : mDataManager.getAllScheduledRecordings()) {
            if (!schedule.isNotStarted() && !schedule.isInProgress()) {
                continue;
            }
            Channel channel = mChannelDataManager.getChannel(schedule.getChannelId());
            if (channel != null) {
                String inputId = channel.getInputId();
                // Do not check whether the input is valid or not. The input might be temporarily
                // invalid.
                List<ScheduledRecording> schedules = mInputScheduleMap.get(inputId);
                if (schedules == null) {
                    schedules = new ArrayList<>();
                    mInputScheduleMap.put(inputId, schedules);
                }
                schedules.add(schedule);
            }
        }
        if (!mInitialized) {
            mInitialized = true;
            notifyInitialize();
        }
        onSchedulesChanged();
    }

    private void onSchedulesChanged() {
        // TODO: notify conflict state change when some conflicting recording becomes partially
        //       conflicting, vice versa.
        List<ScheduledRecording> addedConflicts = new ArrayList<>();
        List<ScheduledRecording> removedConflicts = new ArrayList<>();
        for (String inputId : mInputScheduleMap.keySet()) {
            Map<Long, ConflictInfo> oldConflictInfo = mInputConflictInfoMap.get(inputId);
            Map<Long, ScheduledRecording> oldConflictMap = new HashMap<>();
            if (oldConflictInfo != null) {
                for (ConflictInfo conflictInfo : oldConflictInfo.values()) {
                    oldConflictMap.put(conflictInfo.schedule.getId(), conflictInfo.schedule);
                }
            }
            List<ConflictInfo> conflicts = getConflictingSchedulesInfo(inputId);
            if (conflicts.isEmpty()) {
                mInputConflictInfoMap.remove(inputId);
            } else {
                Map<Long, ConflictInfo> conflictInfos = new HashMap<>();
                for (ConflictInfo conflictInfo : conflicts) {
                    conflictInfos.put(conflictInfo.schedule.getId(), conflictInfo);
                    if (oldConflictMap.remove(conflictInfo.schedule.getId()) == null) {
                        addedConflicts.add(conflictInfo.schedule);
                    }
                }
                mInputConflictInfoMap.put(inputId, conflictInfos);
            }
            removedConflicts.addAll(oldConflictMap.values());
        }
        if (!removedConflicts.isEmpty()) {
            notifyConflictStateChange(false, ScheduledRecording.toArray(removedConflicts));
        }
        if (!addedConflicts.isEmpty()) {
            notifyConflictStateChange(true, ScheduledRecording.toArray(addedConflicts));
        }
    }

    /**
     * Returns {@code true} if this class has been initialized.
     */
    public boolean isInitialized() {
        return mInitialized;
    }

    /**
     * Adds a {@link ScheduledRecordingListener}.
     */
    public final void addScheduledRecordingListener(ScheduledRecordingListener listener) {
        mScheduledRecordingListeners.add(listener);
    }

    /**
     * Removes a {@link ScheduledRecordingListener}.
     */
    public final void removeScheduledRecordingListener(ScheduledRecordingListener listener) {
        mScheduledRecordingListeners.remove(listener);
    }

    /**
     * Calls {@link ScheduledRecordingListener#onScheduledRecordingAdded} for each listener.
     */
    private void notifyScheduledRecordingAdded(ScheduledRecording... scheduledRecordings) {
        for (ScheduledRecordingListener l : mScheduledRecordingListeners) {
            l.onScheduledRecordingAdded(scheduledRecordings);
        }
    }

    /**
     * Calls {@link ScheduledRecordingListener#onScheduledRecordingRemoved} for each listener.
     */
    private void notifyScheduledRecordingRemoved(ScheduledRecording... scheduledRecordings) {
        for (ScheduledRecordingListener l : mScheduledRecordingListeners) {
            l.onScheduledRecordingRemoved(scheduledRecordings);
        }
    }

    /**
     * Calls {@link ScheduledRecordingListener#onScheduledRecordingStatusChanged} for each listener.
     */
    private void notifyScheduledRecordingStatusChanged(ScheduledRecording... scheduledRecordings) {
        for (ScheduledRecordingListener l : mScheduledRecordingListeners) {
            l.onScheduledRecordingStatusChanged(scheduledRecordings);
        }
    }

    /**
     * Adds a {@link OnInitializeListener}.
     */
    public final void addOnInitializeListener(OnInitializeListener listener) {
        mOnInitializeListeners.add(listener);
    }

    /**
     * Removes a {@link OnInitializeListener}.
     */
    public final void removeOnInitializeListener(OnInitializeListener listener) {
        mOnInitializeListeners.remove(listener);
    }

    /**
     * Calls {@link OnInitializeListener#onInitialize} for each listener.
     */
    private void notifyInitialize() {
        for (OnInitializeListener l : mOnInitializeListeners) {
            l.onInitialize();
        }
    }

    /**
     * Adds a {@link OnConflictStateChangeListener}.
     */
    public final void addOnConflictStateChangeListener(OnConflictStateChangeListener listener) {
        mOnConflictStateChangeListeners.add(listener);
    }

    /**
     * Removes a {@link OnConflictStateChangeListener}.
     */
    public final void removeOnConflictStateChangeListener(OnConflictStateChangeListener listener) {
        mOnConflictStateChangeListeners.remove(listener);
    }

    /**
     * Calls {@link OnConflictStateChangeListener#onConflictStateChange} for each listener.
     */
    private void notifyConflictStateChange(boolean conflict,
            ScheduledRecording... scheduledRecordings) {
        for (OnConflictStateChangeListener l : mOnConflictStateChangeListeners) {
            l.onConflictStateChange(conflict, scheduledRecordings);
        }
    }

    /**
     * Returns the priority for the program if it is recorded.
     * <p>
     * The recording will have the higher priority than the existing ones.
     */
    public long suggestNewPriority() {
        if (!SoftPreconditions.checkState(mInitialized, TAG, "Not initialized yet")) {
            return DEFAULT_PRIORITY;
        }
        return suggestHighestPriority();
    }

    private long suggestHighestPriority() {
        long highestPriority = DEFAULT_PRIORITY - PRIORITY_OFFSET;
        for (ScheduledRecording schedule : mDataManager.getAllScheduledRecordings()) {
            if (schedule.getPriority() > highestPriority) {
                highestPriority = schedule.getPriority();
            }
        }
        return highestPriority + PRIORITY_OFFSET;
    }

    /**
     * Suggests the higher priority than the schedules which overlap with {@code schedule}.
     */
    public long suggestHighestPriority(ScheduledRecording schedule) {
        List<ScheduledRecording> schedules = mInputScheduleMap.get(schedule.getInputId());
        if (schedules == null) {
            return DEFAULT_PRIORITY;
        }
        long highestPriority = Long.MIN_VALUE;
        for (ScheduledRecording r : schedules) {
            if (!r.equals(schedule) && r.isOverLapping(schedule)
                    && r.getPriority() > highestPriority) {
                highestPriority = r.getPriority();
            }
        }
        if (highestPriority == Long.MIN_VALUE || highestPriority < schedule.getPriority()) {
            return schedule.getPriority();
        }
        return highestPriority + PRIORITY_OFFSET;
    }

    /**
     * Suggests the higher priority than the schedules which overlap with {@code schedule}.
     */
    public long suggestHighestPriority(String inputId, Range<Long> peroid, long basePriority) {
        List<ScheduledRecording> schedules = mInputScheduleMap.get(inputId);
        if (schedules == null) {
            return DEFAULT_PRIORITY;
        }
        long highestPriority = Long.MIN_VALUE;
        for (ScheduledRecording r : schedules) {
            if (r.isOverLapping(peroid) && r.getPriority() > highestPriority) {
                highestPriority = r.getPriority();
            }
        }
        if (highestPriority == Long.MIN_VALUE || highestPriority < basePriority) {
            return basePriority;
        }
        return highestPriority + PRIORITY_OFFSET;
    }

    /**
     * Returns the priority for a series recording.
     * <p>
     * The recording will have the higher priority than the existing series.
     */
    public long suggestNewSeriesPriority() {
        if (!SoftPreconditions.checkState(mInitialized, TAG, "Not initialized yet")) {
            return DEFAULT_SERIES_PRIORITY;
        }
        return suggestHighestSeriesPriority();
    }

    /**
     * Returns the priority for a series recording by order of series recording priority.
     *
     * Higher order will have higher priority.
     */
    public static long suggestSeriesPriority(int order) {
        return DEFAULT_SERIES_PRIORITY + order * PRIORITY_OFFSET;
    }

    private long suggestHighestSeriesPriority() {
        long highestPriority = DEFAULT_SERIES_PRIORITY - PRIORITY_OFFSET;
        for (SeriesRecording schedule : mDataManager.getSeriesRecordings()) {
            if (schedule.getPriority() > highestPriority) {
                highestPriority = schedule.getPriority();
            }
        }
        return highestPriority + PRIORITY_OFFSET;
    }

    /**
     * Returns a sorted list of all scheduled recordings that will not be recorded if
     * this program is going to be recorded, with their priorities in decending order.
     * <p>
     * An empty list means there is no conflicts. If there is conflict, a priority higher than
     * the first recording in the returned list should be assigned to the new schedule of this
     * program to guarantee the program would be completely recorded.
     */
    public List<ScheduledRecording> getConflictingSchedules(Program program) {
        SoftPreconditions.checkState(mInitialized, TAG, "Not initialized yet");
        SoftPreconditions.checkState(Program.isValid(program), TAG,
                "Program is invalid: " + program);
        SoftPreconditions.checkState(
                program.getStartTimeUtcMillis() < program.getEndTimeUtcMillis(), TAG,
                "Program duration is empty: " + program);
        if (!mInitialized || !Program.isValid(program)
                || program.getStartTimeUtcMillis() >= program.getEndTimeUtcMillis()) {
            return Collections.emptyList();
        }
        TvInputInfo input = Utils.getTvInputInfoForProgram(mContext, program);
        if (input == null || !input.canRecord() || input.getTunerCount() <= 0) {
            return Collections.emptyList();
        }
        return getConflictingSchedules(input, Collections.singletonList(
                ScheduledRecording.builder(input.getId(), program)
                        .setPriority(suggestHighestPriority())
                        .build()));
    }

    /**
     * Returns list of all conflicting scheduled recordings for the given {@code seriesRecording}
     * recording.
     * <p>
     * Any empty list means there is no conflicts.
     */
    public List<ScheduledRecording> getConflictingSchedules(SeriesRecording seriesRecording) {
        SoftPreconditions.checkState(mInitialized, TAG, "Not initialized yet");
        SoftPreconditions.checkState(seriesRecording != null, TAG, "series recording is null");
        if (!mInitialized || seriesRecording == null) {
            return Collections.emptyList();
        }
        TvInputInfo input = Utils.getTvInputInfoForInputId(mContext, seriesRecording.getInputId());
        if (input == null || !input.canRecord() || input.getTunerCount() <= 0) {
            return Collections.emptyList();
        }
        List<ScheduledRecording> scheduledRecordingForSeries = mDataManager.getScheduledRecordings(
                seriesRecording.getId());
        List<ScheduledRecording> availableScheduledRecordingForSeries = new ArrayList<>();
        for (ScheduledRecording scheduledRecording : scheduledRecordingForSeries) {
            if (scheduledRecording.isNotStarted() || scheduledRecording.isInProgress()) {
                availableScheduledRecordingForSeries.add(scheduledRecording);
            }
        }
        if (availableScheduledRecordingForSeries.isEmpty()) {
            return Collections.emptyList();
        }
        return getConflictingSchedules(input, availableScheduledRecordingForSeries);
    }

    /**
     * Returns a sorted list of all scheduled recordings that will not be recorded if
     * this channel is going to be recorded, with their priority in decending order.
     * <p>
     * An empty list means there is no conflicts. If there is conflict, a priority higher than
     * the first recording in the returned list should be assigned to the new schedule of this
     * channel to guarantee the channel would be completely recorded in the designated time range.
     */
    public List<ScheduledRecording> getConflictingSchedules(long channelId, long startTimeMs,
            long endTimeMs) {
        SoftPreconditions.checkState(mInitialized, TAG, "Not initialized yet");
        SoftPreconditions.checkState(channelId != Channel.INVALID_ID, TAG, "Invalid channel ID");
        SoftPreconditions.checkState(startTimeMs < endTimeMs, TAG, "Recording duration is empty.");
        if (!mInitialized || channelId == Channel.INVALID_ID || startTimeMs >= endTimeMs) {
            return Collections.emptyList();
        }
        TvInputInfo input = Utils.getTvInputInfoForChannelId(mContext, channelId);
        if (input == null || !input.canRecord() || input.getTunerCount() <= 0) {
            return Collections.emptyList();
        }
        return getConflictingSchedules(input, Collections.singletonList(
                ScheduledRecording.builder(input.getId(), channelId, startTimeMs, endTimeMs)
                        .setPriority(suggestHighestPriority())
                        .build()));
    }

    /**
     * Returns all the scheduled recordings that conflicts and will not be recorded or clipped for
     * the given input.
     */
    @NonNull
    private List<ConflictInfo> getConflictingSchedulesInfo(String inputId) {
        SoftPreconditions.checkState(mInitialized, TAG, "Not initialized yet");
        TvInputInfo input = Utils.getTvInputInfoForInputId(mContext, inputId);
        SoftPreconditions.checkState(input != null, TAG, "Can't find input for : " + inputId);
        if (!mInitialized || input == null) {
            return Collections.emptyList();
        }
        List<ScheduledRecording> schedules = mInputScheduleMap.get(input.getId());
        if (schedules == null || schedules.isEmpty()) {
            return Collections.emptyList();
        }
        return getConflictingSchedulesInfo(schedules, input.getTunerCount());
    }

    /**
     * Checks if the schedule is conflicting.
     *
     * <p>Note that the {@code schedule} should be the existing one. If not, this returns
     * {@code false}.
     */
    public boolean isConflicting(ScheduledRecording schedule) {
        SoftPreconditions.checkState(mInitialized, TAG, "Not initialized yet");
        TvInputInfo input = Utils.getTvInputInfoForInputId(mContext, schedule.getInputId());
        SoftPreconditions.checkState(input != null, TAG, "Can't find input for channel ID : "
                + schedule.getChannelId());
        if (!mInitialized || input == null) {
            return false;
        }
        Map<Long, ConflictInfo> conflicts = mInputConflictInfoMap.get(input.getId());
        return conflicts != null && conflicts.containsKey(schedule.getId());
    }

    /**
     * Checks if the schedule is partially conflicting, i.e., part of the scheduled program might be
     * recorded even if the priority of the schedule is not raised.
     * <p>
     * If the given schedule is not conflicting or is totally conflicting, i.e., cannot be recorded
     * at all, this method returns {@code false} in both cases.
     */
    public boolean isPartiallyConflicting(@NonNull ScheduledRecording schedule) {
        SoftPreconditions.checkState(mInitialized, TAG, "Not initialized yet");
        TvInputInfo input = Utils.getTvInputInfoForInputId(mContext, schedule.getInputId());
        SoftPreconditions.checkState(input != null, TAG, "Can't find input for channel ID : "
                + schedule.getChannelId());
        if (!mInitialized || input == null) {
            return false;
        }
        Map<Long, ConflictInfo> conflicts = mInputConflictInfoMap.get(input.getId());
        if (conflicts != null) {
            ConflictInfo conflictInfo = conflicts.get(schedule.getId());
            return conflictInfo != null && conflictInfo.partialConflict;
        }
        return false;
    }

    /**
     * Returns priority ordered list of all scheduled recordings that will not be recorded if
     * this channel is tuned to.
     */
    public List<ScheduledRecording> getConflictingSchedulesForTune(long channelId) {
        SoftPreconditions.checkState(mInitialized, TAG, "Not initialized yet");
        SoftPreconditions.checkState(channelId != Channel.INVALID_ID, TAG, "Invalid channel ID");
        TvInputInfo input = Utils.getTvInputInfoForChannelId(mContext, channelId);
        SoftPreconditions.checkState(input != null, TAG, "Can't find input for channel ID: "
                + channelId);
        if (!mInitialized || channelId == Channel.INVALID_ID || input == null) {
            return Collections.emptyList();
        }
        return getConflictingSchedulesForTune(input.getId(), channelId, System.currentTimeMillis(),
                suggestHighestPriority(), getStartedRecordings(input.getId()),
                input.getTunerCount());
    }

    @VisibleForTesting
    public static List<ScheduledRecording> getConflictingSchedulesForTune(String inputId,
            long channelId, long currentTimeMs, long newPriority,
            List<ScheduledRecording> startedRecordings, int tunerCount) {
        boolean channelFound = false;
        for (ScheduledRecording schedule : startedRecordings) {
            if (schedule.getChannelId() == channelId) {
                channelFound = true;
                break;
            }
        }
        List<ScheduledRecording> schedules;
        if (!channelFound) {
            // The current channel is not being recorded.
            schedules = new ArrayList<>(startedRecordings);
            schedules.add(ScheduledRecording
                    .builder(inputId, channelId, currentTimeMs, currentTimeMs + 1)
                    .setPriority(newPriority)
                    .build());
        } else {
            schedules = startedRecordings;
        }
        return getConflictingSchedules(schedules, tunerCount);
    }

    /**
     * Returns priority ordered list of all scheduled recordings that will not be recorded if
     * the user keeps watching this channel.
     * <p>
     * Note that if the user keeps watching the channel, the channel can be recorded.
     */
    public List<ScheduledRecording> getConflictingSchedulesForWatching(long channelId) {
        SoftPreconditions.checkState(mInitialized, TAG, "Not initialized yet");
        SoftPreconditions.checkState(channelId != Channel.INVALID_ID, TAG, "Invalid channel ID");
        TvInputInfo input = Utils.getTvInputInfoForChannelId(mContext, channelId);
        SoftPreconditions.checkState(input != null, TAG, "Can't find input for channel ID: "
                + channelId);
        if (!mInitialized || channelId == Channel.INVALID_ID || input == null) {
            return Collections.emptyList();
        }
        List<ScheduledRecording> schedules = mInputScheduleMap.get(input.getId());
        if (schedules == null || schedules.isEmpty()) {
            return Collections.emptyList();
        }
        return getConflictingSchedulesForWatching(input.getId(), channelId,
                System.currentTimeMillis(), suggestNewPriority(), schedules, input.getTunerCount());
    }

    private List<ScheduledRecording> getConflictingSchedules(TvInputInfo input,
            List<ScheduledRecording> schedulesToAdd) {
        SoftPreconditions.checkNotNull(input);
        if (input == null || !input.canRecord() || input.getTunerCount() <= 0) {
            return Collections.emptyList();
        }
        List<ScheduledRecording> currentSchedules = mInputScheduleMap.get(input.getId());
        if (currentSchedules == null || currentSchedules.isEmpty()) {
            return Collections.emptyList();
        }
        return getConflictingSchedules(schedulesToAdd, currentSchedules, input.getTunerCount());
    }

    @VisibleForTesting
    static List<ScheduledRecording> getConflictingSchedulesForWatching(String inputId,
            long channelId, long currentTimeMs, long newPriority,
            @NonNull List<ScheduledRecording> schedules, int tunerCount) {
        List<ScheduledRecording> schedulesToCheck = new ArrayList<>(schedules);
        List<ScheduledRecording> schedulesSameChannel = new ArrayList<>();
        for (ScheduledRecording schedule : schedules) {
            if (schedule.getChannelId() == channelId) {
                schedulesSameChannel.add(schedule);
                schedulesToCheck.remove(schedule);
            }
        }
        // Assume that the user will watch the current channel forever.
        schedulesToCheck.add(ScheduledRecording
                .builder(inputId, channelId, currentTimeMs, Long.MAX_VALUE)
                .setPriority(newPriority)
                .build());
        List<ScheduledRecording> result = new ArrayList<>();
        result.addAll(getConflictingSchedules(schedulesSameChannel, 1));
        result.addAll(getConflictingSchedules(schedulesToCheck, tunerCount));
        Collections.sort(result, RESULT_COMPARATOR);
        return result;
    }

    @VisibleForTesting
    static List<ScheduledRecording> getConflictingSchedules(List<ScheduledRecording> schedulesToAdd,
            List<ScheduledRecording> currentSchedules, int tunerCount) {
        List<ScheduledRecording> schedulesToCheck = new ArrayList<>(currentSchedules);
        // When the duplicate schedule is to be added, remove the current duplicate recording.
        for (Iterator<ScheduledRecording> iter = schedulesToCheck.iterator(); iter.hasNext(); ) {
            ScheduledRecording schedule = iter.next();
            for (ScheduledRecording toAdd : schedulesToAdd) {
                if (schedule.getType() == ScheduledRecording.TYPE_PROGRAM) {
                    if (toAdd.getProgramId() == schedule.getProgramId()) {
                        iter.remove();
                        break;
                    }
                } else {
                    if (toAdd.getChannelId() == schedule.getChannelId()
                            && toAdd.getStartTimeMs() == schedule.getStartTimeMs()
                            && toAdd.getEndTimeMs() == schedule.getEndTimeMs()) {
                        iter.remove();
                        break;
                    }
                }
            }
        }
        schedulesToCheck.addAll(schedulesToAdd);
        List<Range<Long>> ranges = new ArrayList<>();
        for (ScheduledRecording schedule : schedulesToAdd) {
            ranges.add(new Range<>(schedule.getStartTimeMs(), schedule.getEndTimeMs()));
        }
        return getConflictingSchedules(schedulesToCheck, tunerCount, ranges);
    }

    /**
     * Returns all conflicting scheduled recordings for the given schedules and count of tuner.
     */
    public static List<ScheduledRecording> getConflictingSchedules(
            List<ScheduledRecording> schedules, int tunerCount) {
        return getConflictingSchedules(schedules, tunerCount, null);
    }

    @VisibleForTesting
    static List<ScheduledRecording> getConflictingSchedules(
            List<ScheduledRecording> schedules, int tunerCount, List<Range<Long>> periods) {
        List<ScheduledRecording> result = new ArrayList<>();
        for (ConflictInfo conflictInfo :
                getConflictingSchedulesInfo(schedules, tunerCount, periods)) {
            result.add(conflictInfo.schedule);
        }
        return result;
    }

    @VisibleForTesting
    static List<ConflictInfo> getConflictingSchedulesInfo(List<ScheduledRecording> schedules,
            int tunerCount) {
        return getConflictingSchedulesInfo(schedules, tunerCount, null);
    }

    /**
     * This is the core method to calculate all the conflicting schedules (in given periods).
     * <p>
     * Note that this method will ignore duplicated schedules with a same hash code. (Please refer
     * to {@link ScheduledRecording#hashCode}.)
     *
     * @return A {@link HashMap} from {@link ScheduledRecording} to {@link Boolean}. The boolean
     *         value denotes if the scheduled recording is partially conflicting, i.e., is possible
     *         to be partially recorded under the given schedules and tuner count {@code true},
     *         or not {@code false}.
     */
    private static List<ConflictInfo> getConflictingSchedulesInfo(
            List<ScheduledRecording> schedules, int tunerCount, List<Range<Long>> periods) {
        List<ScheduledRecording> schedulesToCheck = new ArrayList<>(schedules);
        // Sort by the same order as that in InputTaskScheduler.
        Collections.sort(schedulesToCheck, InputTaskScheduler.getRecordingOrderComparator());
        List<ScheduledRecording> recordings = new ArrayList<>();
        Map<ScheduledRecording, ConflictInfo> conflicts = new HashMap<>();
        Map<ScheduledRecording, ScheduledRecording> modified2OriginalSchedules = new HashMap<>();
        // Simulate InputTaskScheduler.
        while (!schedulesToCheck.isEmpty()) {
            ScheduledRecording schedule = schedulesToCheck.remove(0);
            removeFinishedRecordings(recordings, schedule.getStartTimeMs());
            if (recordings.size() < tunerCount) {
                recordings.add(schedule);
                if (modified2OriginalSchedules.containsKey(schedule)) {
                    // Schedule has been modified, which means it's already conflicted.
                    // Modify its state to partially conflicted.
                    ScheduledRecording originalSchedule = modified2OriginalSchedules.get(schedule);
                    conflicts.put(originalSchedule, new ConflictInfo(originalSchedule, true));
                }
            } else {
                ScheduledRecording candidate = findReplaceableRecording(recordings, schedule);
                if (candidate != null) {
                    if (!modified2OriginalSchedules.containsKey(candidate)) {
                        conflicts.put(candidate, new ConflictInfo(candidate, true));
                    }
                    recordings.remove(candidate);
                    recordings.add(schedule);
                    if (modified2OriginalSchedules.containsKey(schedule)) {
                        // Schedule has been modified, which means it's already conflicted.
                        // Modify its state to partially conflicted.
                        ScheduledRecording originalSchedule =
                                modified2OriginalSchedules.get(schedule);
                        conflicts.put(originalSchedule, new ConflictInfo(originalSchedule, true));
                    }
                } else {
                    if (!modified2OriginalSchedules.containsKey(schedule)) {
                        // if schedule has been modified, it's already conflicted.
                        // No need to add it again.
                        conflicts.put(schedule, new ConflictInfo(schedule, false));
                    }
                    long earliestEndTime = getEarliestEndTime(recordings);
                    if (earliestEndTime < schedule.getEndTimeMs()) {
                        // The schedule can starts when other recording ends even though it's
                        // clipped.
                        ScheduledRecording modifiedSchedule = ScheduledRecording.buildFrom(schedule)
                                .setStartTimeMs(earliestEndTime).build();
                        ScheduledRecording originalSchedule =
                                modified2OriginalSchedules.getOrDefault(schedule, schedule);
                        modified2OriginalSchedules.put(modifiedSchedule, originalSchedule);
                        int insertPosition = Collections.binarySearch(schedulesToCheck,
                                modifiedSchedule,
                                ScheduledRecording.START_TIME_THEN_PRIORITY_THEN_ID_COMPARATOR);
                        if (insertPosition >= 0) {
                            schedulesToCheck.add(insertPosition, modifiedSchedule);
                        } else {
                            schedulesToCheck.add(-insertPosition - 1, modifiedSchedule);
                        }
                    }
                }
            }
        }
        // Returns only the schedules with the given range.
        if (periods != null && !periods.isEmpty()) {
            for (Iterator<ScheduledRecording> iter = conflicts.keySet().iterator();
                    iter.hasNext(); ) {
                boolean overlapping = false;
                ScheduledRecording schedule = iter.next();
                for (Range<Long> period : periods) {
                    if (schedule.isOverLapping(period)) {
                        overlapping = true;
                        break;
                    }
                }
                if (!overlapping) {
                    iter.remove();
                }
            }
        }
        List<ConflictInfo> result = new ArrayList<>(conflicts.values());
        Collections.sort(result, new Comparator<ConflictInfo>() {
            @Override
            public int compare(ConflictInfo lhs, ConflictInfo rhs) {
                return RESULT_COMPARATOR.compare(lhs.schedule, rhs.schedule);
            }
        });
        return result;
    }

    private static void removeFinishedRecordings(List<ScheduledRecording> recordings,
            long currentTimeMs) {
        for (Iterator<ScheduledRecording> iter = recordings.iterator(); iter.hasNext(); ) {
            if (iter.next().getEndTimeMs() <= currentTimeMs) {
                iter.remove();
            }
        }
    }

    /**
     * @see InputTaskScheduler#getReplacableTask
     */
    private static ScheduledRecording findReplaceableRecording(List<ScheduledRecording> recordings,
            ScheduledRecording schedule) {
        // Returns the recording with the following priority.
        // 1. The recording with the lowest priority is returned.
        // 2. If the priorities are the same, the recording which finishes early is returned.
        // 3. If 1) and 2) are the same, the early created schedule is returned.
        ScheduledRecording candidate = null;
        for (ScheduledRecording recording : recordings) {
            if (schedule.getPriority() > recording.getPriority()) {
                if (candidate == null || CANDIDATE_COMPARATOR.compare(candidate, recording) > 0) {
                    candidate = recording;
                }
            }
        }
        return candidate;
    }

    private static long getEarliestEndTime(List<ScheduledRecording> recordings) {
        long earliest = Long.MAX_VALUE;
        for (ScheduledRecording recording : recordings) {
            if (earliest > recording.getEndTimeMs()) {
                earliest = recording.getEndTimeMs();
            }
        }
        return earliest;
    }

    @VisibleForTesting
    static class ConflictInfo {
        public ScheduledRecording schedule;
        public boolean partialConflict;

        ConflictInfo(ScheduledRecording schedule, boolean partialConflict) {
            this.schedule = schedule;
            this.partialConflict = partialConflict;
        }
    }

    /**
     * A listener which is notified the initialization of schedule manager.
     */
    public interface OnInitializeListener {
        /**
         * Called when the schedule manager has been initialized.
         */
        void onInitialize();
    }

    /**
     * A listener which is notified the conflict state change of the schedules.
     */
    public interface OnConflictStateChangeListener {
        /**
         * Called when the conflicting schedules change.
         * <p>
         * Note that this can be called before
         * {@link ScheduledRecordingListener#onScheduledRecordingAdded} is called.
         *
         * @param conflict {@code true} if the {@code schedules} are the new conflicts, otherwise
         * {@code false}.
         * @param schedules the schedules
         */
        void onConflictStateChange(boolean conflict, ScheduledRecording... schedules);
    }
}