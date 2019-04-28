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

import android.content.Context;
import android.os.Build;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.test.filters.SdkSuppress;
import android.text.TextUtils;
import android.util.Log;
import android.util.Range;

import com.android.tv.common.SoftPreconditions;
import com.android.tv.dvr.data.RecordedProgram;
import com.android.tv.dvr.data.ScheduledRecording;
import com.android.tv.dvr.data.ScheduledRecording.RecordingState;
import com.android.tv.dvr.data.SeriesRecording;
import com.android.tv.util.Clock;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

/** A DVR Data manager that stores values in memory suitable for testing. */
@SdkSuppress(minSdkVersion = Build.VERSION_CODES.N)
public final class DvrDataManagerInMemoryImpl extends BaseDvrDataManager {
    private final static String TAG = "DvrDataManagerInMemory";
    private final AtomicLong mNextId = new AtomicLong(1);
    private final Map<Long, ScheduledRecording> mScheduledRecordings = new HashMap<>();
    private final Map<Long, RecordedProgram> mRecordedPrograms = new HashMap<>();
    private final Map<Long, SeriesRecording> mSeriesRecordings = new HashMap<>();

    public DvrDataManagerInMemoryImpl(Context context, Clock clock) {
        super(context, clock);
    }

    @Override
    public boolean isInitialized() {
        return true;
    }

    @Override
    public boolean isDvrScheduleLoadFinished() {
        return true;
    }

    @Override
    public boolean isRecordedProgramLoadFinished() {
        return true;
    }

    private List<ScheduledRecording> getScheduledRecordingsPrograms() {
        return new ArrayList<>(mScheduledRecordings.values());
    }

    @Override
    public List<RecordedProgram> getRecordedPrograms() {
        return new ArrayList<>(mRecordedPrograms.values());
    }

    @Override
    public List<ScheduledRecording> getAllScheduledRecordings() {
        return new ArrayList<>(mScheduledRecordings.values());
    }

    public List<SeriesRecording> getSeriesRecordings() {
        return new ArrayList<>(mSeriesRecordings.values());
    }

    @Override
    public List<SeriesRecording> getSeriesRecordings(String inputId) {
        List<SeriesRecording> result = new ArrayList<>();
        for (SeriesRecording r : mSeriesRecordings.values()) {
            if (TextUtils.equals(r.getInputId(), inputId)) {
                result.add(r);
            }
        }
        return result;
    }

    @Override
    public long getNextScheduledStartTimeAfter(long startTime) {

        List<ScheduledRecording> temp =  getNonStartedScheduledRecordings();
        Collections.sort(temp, ScheduledRecording.START_TIME_COMPARATOR);
        for (ScheduledRecording r : temp) {
            if (r.getStartTimeMs() > startTime) {
                return r.getStartTimeMs();
            }
        }
        return DvrDataManager.NEXT_START_TIME_NOT_FOUND;
    }

    @Override
    public List<ScheduledRecording> getScheduledRecordings(Range<Long> period,
            @RecordingState int state) {
        List<ScheduledRecording> temp = getScheduledRecordingsPrograms();
        List<ScheduledRecording> result = new ArrayList<>();
        for (ScheduledRecording r : temp) {
            if (r.isOverLapping(period) && r.getState() == state) {
                result.add(r);
            }
        }
        return result;
    }

    @Override
    public List<ScheduledRecording> getScheduledRecordings(long seriesRecordingId) {
        List<ScheduledRecording> result = new ArrayList<>();
        for (ScheduledRecording r : mScheduledRecordings.values()) {
            if (r.getSeriesRecordingId() == seriesRecordingId) {
                result.add(r);
            }
        }
        return result;
    }

    @Override
    public List<ScheduledRecording> getScheduledRecordings(String inputId) {
        List<ScheduledRecording> result = new ArrayList<>();
        for (ScheduledRecording r : mScheduledRecordings.values()) {
            if (TextUtils.equals(r.getInputId(), inputId)) {
                result.add(r);
            }
        }
        return result;
    }

    /**
     * Add a new scheduled recording.
     */
    @Override
    public void addScheduledRecording(ScheduledRecording... scheduledRecordings) {
        for (ScheduledRecording r : scheduledRecordings) {
            addScheduledRecordingInternal(r);
        }
    }


    public void addRecordedProgram(RecordedProgram recordedProgram) {
        addRecordedProgramInternal(recordedProgram);
    }

    public void updateRecordedProgram(RecordedProgram r) {
        long id = r.getId();
        if (mRecordedPrograms.containsKey(id)) {
            mRecordedPrograms.put(id, r);
            notifyRecordedProgramsChanged(r);
        } else {
            throw new IllegalArgumentException("Recording not found:" + r);
        }
    }

    public void removeRecordedProgram(RecordedProgram scheduledRecording) {
        mRecordedPrograms.remove(scheduledRecording.getId());
        notifyRecordedProgramsRemoved(scheduledRecording);
    }


    public ScheduledRecording addScheduledRecordingInternal(ScheduledRecording scheduledRecording) {
        SoftPreconditions
                .checkState(scheduledRecording.getId() == ScheduledRecording.ID_NOT_SET, TAG,
                        "expected id of " + ScheduledRecording.ID_NOT_SET + " but was "
                                + scheduledRecording);
        scheduledRecording = ScheduledRecording.buildFrom(scheduledRecording)
                .setId(mNextId.incrementAndGet())
                .build();
        mScheduledRecordings.put(scheduledRecording.getId(), scheduledRecording);
        notifyScheduledRecordingAdded(scheduledRecording);
        return scheduledRecording;
    }

    public RecordedProgram addRecordedProgramInternal(RecordedProgram recordedProgram) {
        SoftPreconditions.checkState(recordedProgram.getId() == RecordedProgram.ID_NOT_SET, TAG,
                "expected id of " + RecordedProgram.ID_NOT_SET + " but was " + recordedProgram);
        recordedProgram = RecordedProgram.buildFrom(recordedProgram)
                .setId(mNextId.incrementAndGet())
                .build();
        mRecordedPrograms.put(recordedProgram.getId(), recordedProgram);
        notifyRecordedProgramsAdded(recordedProgram);
        return recordedProgram;
    }

    @Override
    public void addSeriesRecording(SeriesRecording... seriesRecordings) {
        for (SeriesRecording r : seriesRecordings) {
            mSeriesRecordings.put(r.getId(), r);
        }
        notifySeriesRecordingAdded(seriesRecordings);
    }

    @Override
    public void removeScheduledRecording(ScheduledRecording... scheduledRecordings) {
        for (ScheduledRecording r : scheduledRecordings) {
            mScheduledRecordings.remove(r.getId());
        }
        notifyScheduledRecordingRemoved(scheduledRecordings);
    }

    @Override
    public void removeScheduledRecording(boolean forceRemove, ScheduledRecording... schedule) {
        removeScheduledRecording(schedule);
    }

    @Override
    public void removeSeriesRecording(SeriesRecording... seriesRecordings) {
        for (SeriesRecording r : seriesRecordings) {
            mSeriesRecordings.remove(r.getId());
        }
        notifySeriesRecordingRemoved(seriesRecordings);
    }

    @Override
    public void updateScheduledRecording(ScheduledRecording... scheduledRecordings) {
        for (ScheduledRecording r : scheduledRecordings) {
            long id = r.getId();
            if (mScheduledRecordings.containsKey(id)) {
                mScheduledRecordings.put(id, r);
            } else {
                Log.d(TAG, "Recording not found:" + r);
            }
        }
        notifyScheduledRecordingStatusChanged(scheduledRecordings);
    }

    @Override
    public void updateSeriesRecording(SeriesRecording... seriesRecordings) {
        for (SeriesRecording r : seriesRecordings) {
            long id = r.getId();
            if (mSeriesRecordings.containsKey(id)) {
                mSeriesRecordings.put(id, r);
            } else {
                throw new IllegalArgumentException("Recording not found:" + r);
            }
        }
        notifySeriesRecordingChanged(seriesRecordings);
    }

    @Nullable
    @Override
    public ScheduledRecording getScheduledRecording(long id) {
        return mScheduledRecordings.get(id);
    }

    @Nullable
    @Override
    public ScheduledRecording getScheduledRecordingForProgramId(long programId) {
        for (ScheduledRecording r : mScheduledRecordings.values()) {
            if (r.getProgramId() == programId) {
                    return r;
            }
        }
        return null;
    }

    @Nullable
    @Override
    public SeriesRecording getSeriesRecording(long seriesRecordingId) {
        return mSeriesRecordings.get(seriesRecordingId);
    }

    @Nullable
    @Override
    public SeriesRecording getSeriesRecording(String seriesId) {
        for (SeriesRecording r : mSeriesRecordings.values()) {
            if (r.getSeriesId().equals(seriesId)) {
                return r;
            }
        }
        return null;
    }

    @Nullable
    @Override
    public RecordedProgram getRecordedProgram(long recordingId) {
        return mRecordedPrograms.get(recordingId);
    }

    @Override
    @NonNull
    protected List<ScheduledRecording> getRecordingsWithState(int... states) {
        ArrayList<ScheduledRecording> result = new ArrayList<>();
        for (ScheduledRecording r : mScheduledRecordings.values()) {
            for (int state : states) {
                if (r.getState() == state) {
                    result.add(r);
                    break;
                }
            }
        }
        return result;
    }
}
