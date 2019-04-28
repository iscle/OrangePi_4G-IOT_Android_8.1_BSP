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

import android.annotation.TargetApi;
import android.content.Context;
import android.os.Build;
import android.support.annotation.MainThread;
import android.support.annotation.NonNull;
import android.util.ArraySet;
import android.util.Log;

import com.android.tv.common.SoftPreconditions;
import com.android.tv.common.feature.CommonFeatures;
import com.android.tv.dvr.data.RecordedProgram;
import com.android.tv.dvr.data.ScheduledRecording;
import com.android.tv.dvr.data.ScheduledRecording.RecordingState;
import com.android.tv.dvr.data.SeriesRecording;
import com.android.tv.util.Clock;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArraySet;

/**
 * Base implementation of @{link DataManagerInternal}.
 */
@MainThread
@TargetApi(Build.VERSION_CODES.N)
public abstract class BaseDvrDataManager implements WritableDvrDataManager {
    private final static String TAG = "BaseDvrDataManager";
    private final static boolean DEBUG = false;
    protected final Clock mClock;

    private final Set<OnDvrScheduleLoadFinishedListener> mOnDvrScheduleLoadFinishedListeners =
            new CopyOnWriteArraySet<>();
    private final Set<OnRecordedProgramLoadFinishedListener>
            mOnRecordedProgramLoadFinishedListeners = new CopyOnWriteArraySet<>();
    private final Set<ScheduledRecordingListener> mScheduledRecordingListeners = new ArraySet<>();
    private final Set<SeriesRecordingListener> mSeriesRecordingListeners = new ArraySet<>();
    private final Set<RecordedProgramListener> mRecordedProgramListeners = new ArraySet<>();
    private final HashMap<Long, ScheduledRecording> mDeletedScheduleMap = new HashMap<>();

    BaseDvrDataManager(Context context, Clock clock) {
        SoftPreconditions.checkFeatureEnabled(context, CommonFeatures.DVR, TAG);
        mClock = clock;
    }

    @Override
    public void addDvrScheduleLoadFinishedListener(OnDvrScheduleLoadFinishedListener listener) {
        mOnDvrScheduleLoadFinishedListeners.add(listener);
    }

    @Override
    public void removeDvrScheduleLoadFinishedListener(OnDvrScheduleLoadFinishedListener listener) {
        mOnDvrScheduleLoadFinishedListeners.remove(listener);
    }

    @Override
    public void addRecordedProgramLoadFinishedListener(
            OnRecordedProgramLoadFinishedListener listener) {
        mOnRecordedProgramLoadFinishedListeners.add(listener);
    }

    @Override
    public void removeRecordedProgramLoadFinishedListener(
            OnRecordedProgramLoadFinishedListener listener) {
        mOnRecordedProgramLoadFinishedListeners.remove(listener);
    }

    @Override
    public final void addScheduledRecordingListener(ScheduledRecordingListener listener) {
        mScheduledRecordingListeners.add(listener);
    }

    @Override
    public final void removeScheduledRecordingListener(ScheduledRecordingListener listener) {
        mScheduledRecordingListeners.remove(listener);
    }

    @Override
    public final void addSeriesRecordingListener(SeriesRecordingListener listener) {
        mSeriesRecordingListeners.add(listener);
    }

    @Override
    public final void removeSeriesRecordingListener(SeriesRecordingListener listener) {
        mSeriesRecordingListeners.remove(listener);
    }

    @Override
    public final void addRecordedProgramListener(RecordedProgramListener listener) {
        mRecordedProgramListeners.add(listener);
    }

    @Override
    public final void removeRecordedProgramListener(RecordedProgramListener listener) {
        mRecordedProgramListeners.remove(listener);
    }

    /**
     * Calls {@link OnDvrScheduleLoadFinishedListener#onDvrScheduleLoadFinished} for each listener.
     */
    protected final void notifyDvrScheduleLoadFinished() {
        for (OnDvrScheduleLoadFinishedListener l : mOnDvrScheduleLoadFinishedListeners) {
            if (DEBUG) Log.d(TAG, "notify DVR schedule load finished");
            l.onDvrScheduleLoadFinished();
        }
    }

    /**
     * Calls {@link OnRecordedProgramLoadFinishedListener#onRecordedProgramLoadFinished()}
     * for each listener.
     */
    protected final void notifyRecordedProgramLoadFinished() {
        for (OnRecordedProgramLoadFinishedListener l : mOnRecordedProgramLoadFinishedListeners) {
            if (DEBUG) Log.d(TAG, "notify recorded programs load finished");
            l.onRecordedProgramLoadFinished();
        }
    }

    /**
     * Calls {@link RecordedProgramListener#onRecordedProgramsAdded}
     * for each listener.
     */
    protected final void notifyRecordedProgramsAdded(RecordedProgram... recordedPrograms) {
        for (RecordedProgramListener l : mRecordedProgramListeners) {
            if (DEBUG) Log.d(TAG, "notify " + l + " added " + Arrays.asList(recordedPrograms));
            l.onRecordedProgramsAdded(recordedPrograms);
        }
    }

    /**
     * Calls {@link RecordedProgramListener#onRecordedProgramsChanged}
     * for each listener.
     */
    protected final void notifyRecordedProgramsChanged(RecordedProgram... recordedPrograms) {
        for (RecordedProgramListener l : mRecordedProgramListeners) {
            if (DEBUG) Log.d(TAG, "notify " + l + " changed " + Arrays.asList(recordedPrograms));
            l.onRecordedProgramsChanged(recordedPrograms);
        }
    }

    /**
     * Calls {@link RecordedProgramListener#onRecordedProgramsRemoved}
     * for each  listener.
     */
    protected final void notifyRecordedProgramsRemoved(RecordedProgram... recordedPrograms) {
        for (RecordedProgramListener l : mRecordedProgramListeners) {
            if (DEBUG) Log.d(TAG, "notify " + l + " removed " + Arrays.asList(recordedPrograms));
            l.onRecordedProgramsRemoved(recordedPrograms);
        }
    }

    /**
     * Calls {@link SeriesRecordingListener#onSeriesRecordingAdded}
     * for each listener.
     */
    protected final void notifySeriesRecordingAdded(SeriesRecording... seriesRecordings) {
        for (SeriesRecordingListener l : mSeriesRecordingListeners) {
            if (DEBUG) Log.d(TAG, "notify " + l + " added  " + Arrays.asList(seriesRecordings));
            l.onSeriesRecordingAdded(seriesRecordings);
        }
    }

    /**
     * Calls {@link SeriesRecordingListener#onSeriesRecordingRemoved}
     * for each listener.
     */
    protected final void notifySeriesRecordingRemoved(SeriesRecording... seriesRecordings) {
        for (SeriesRecordingListener l : mSeriesRecordingListeners) {
            if (DEBUG) Log.d(TAG, "notify " + l + " removed " + Arrays.asList(seriesRecordings));
            l.onSeriesRecordingRemoved(seriesRecordings);
        }
    }

    /**
     * Calls
     * {@link SeriesRecordingListener#onSeriesRecordingChanged}
     * for each listener.
     */
    protected final void notifySeriesRecordingChanged(SeriesRecording... seriesRecordings) {
        for (SeriesRecordingListener l : mSeriesRecordingListeners) {
            if (DEBUG) Log.d(TAG, "notify " + l + " changed " + Arrays.asList(seriesRecordings));
            l.onSeriesRecordingChanged(seriesRecordings);
        }
    }

    /**
     * Calls {@link ScheduledRecordingListener#onScheduledRecordingAdded}
     * for each listener.
     */
    protected final void notifyScheduledRecordingAdded(ScheduledRecording... scheduledRecording) {
        for (ScheduledRecordingListener l : mScheduledRecordingListeners) {
            if (DEBUG) Log.d(TAG, "notify " + l + " added  " + Arrays.asList(scheduledRecording));
            l.onScheduledRecordingAdded(scheduledRecording);
        }
    }

    /**
     * Calls {@link ScheduledRecordingListener#onScheduledRecordingRemoved}
     * for each listener.
     */
    protected final void notifyScheduledRecordingRemoved(ScheduledRecording... scheduledRecording) {
        for (ScheduledRecordingListener l : mScheduledRecordingListeners) {
            if (DEBUG) Log.d(TAG, "notify " + l + " removed " + Arrays.asList(scheduledRecording));
            l.onScheduledRecordingRemoved(scheduledRecording);
        }
    }

    /**
     * Calls
     * {@link ScheduledRecordingListener#onScheduledRecordingStatusChanged}
     * for each listener.
     */
    protected final void notifyScheduledRecordingStatusChanged(
            ScheduledRecording... scheduledRecording) {
        for (ScheduledRecordingListener l : mScheduledRecordingListeners) {
            if (DEBUG) Log.d(TAG, "notify " + l + " changed " + Arrays.asList(scheduledRecording));
            l.onScheduledRecordingStatusChanged(scheduledRecording);
        }
    }

    /**
     * Returns a new list with only {@link ScheduledRecording} with a {@link
     * ScheduledRecording#getEndTimeMs() endTime} after now.
     */
    private List<ScheduledRecording> filterEndTimeIsPast(List<ScheduledRecording> originals) {
        List<ScheduledRecording> results = new ArrayList<>(originals.size());
        for (ScheduledRecording r : originals) {
            if (r.getEndTimeMs() > mClock.currentTimeMillis()) {
                results.add(r);
            }
        }
        return results;
    }

    @Override
    public List<ScheduledRecording> getAvailableScheduledRecordings() {
        return filterEndTimeIsPast(getRecordingsWithState(
                ScheduledRecording.STATE_RECORDING_IN_PROGRESS,
                ScheduledRecording.STATE_RECORDING_NOT_STARTED));
    }

    @Override
    public List<ScheduledRecording> getStartedRecordings() {
        return filterEndTimeIsPast(getRecordingsWithState(
                ScheduledRecording.STATE_RECORDING_IN_PROGRESS));
    }

    @Override
    public List<ScheduledRecording> getNonStartedScheduledRecordings() {
        return filterEndTimeIsPast(getRecordingsWithState(
                ScheduledRecording.STATE_RECORDING_NOT_STARTED));
    }

    @Override
    public void changeState(ScheduledRecording scheduledRecording, @RecordingState int newState) {
        if (scheduledRecording.getState() != newState) {
            updateScheduledRecording(ScheduledRecording.buildFrom(scheduledRecording)
                    .setState(newState).build());
        }
    }

    @Override
    public Collection<ScheduledRecording> getDeletedSchedules() {
        return mDeletedScheduleMap.values();
    }

    @NonNull
    @Override
    public Collection<Long> getDisallowedProgramIds() {
        return mDeletedScheduleMap.keySet();
    }

    /**
     * Returns the map which contains the deleted schedules which are mapped from the program ID.
     */
    protected Map<Long, ScheduledRecording> getDeletedScheduleMap() {
        return mDeletedScheduleMap;
    }

    /**
     * Returns the schedules whose state is contained by states.
     */
    protected abstract List<ScheduledRecording> getRecordingsWithState(int... states);

    @Override
    public List<RecordedProgram> getRecordedPrograms(long seriesRecordingId) {
        SeriesRecording seriesRecording = getSeriesRecording(seriesRecordingId);
        if (seriesRecording == null) {
            return Collections.emptyList();
        }
        List<RecordedProgram> result = new ArrayList<>();
        for (RecordedProgram r : getRecordedPrograms()) {
            if (seriesRecording.getSeriesId().equals(r.getSeriesId())) {
                result.add(r);
            }
        }
        return result;
    }

    @Override
    public void checkAndRemoveEmptySeriesRecording(long... seriesRecordingIds) {
        List<SeriesRecording> toRemove = new ArrayList<>();
        for (long rId : seriesRecordingIds) {
            SeriesRecording seriesRecording = getSeriesRecording(rId);
            if (seriesRecording != null && isEmptySeriesRecording(seriesRecording)) {
                toRemove.add(seriesRecording);
            }
        }
        removeSeriesRecording(SeriesRecording.toArray(toRemove));
    }

    /**
     * Returns {@code true}, if the series recording is empty and can be removed. If a series
     * recording is in NORMAL state or has recordings or schedules, it is not empty and cannot be
     * removed.
     */
    protected final boolean isEmptySeriesRecording(@NonNull SeriesRecording seriesRecording) {
        if (!seriesRecording.isStopped()) {
            return false;
        }
        long seriesRecordingId = seriesRecording.getId();
        for (ScheduledRecording r : getAvailableScheduledRecordings()) {
            if (r.getSeriesRecordingId() == seriesRecordingId) {
                return false;
            }
        }
        String seriesId = seriesRecording.getSeriesId();
        for (RecordedProgram r : getRecordedPrograms()) {
            if (seriesId.equals(r.getSeriesId())) {
                return false;
            }
        }
        return true;
    }

    @Override
    public void forgetStorage(String inputId) { }
}
