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

import android.annotation.TargetApi;
import android.content.Context;
import android.media.tv.TvInputInfo;
import android.os.Build;
import android.support.v17.leanback.widget.ClassPresenterSelector;
import android.util.ArrayMap;
import android.util.Log;

import com.android.tv.ApplicationSingletons;
import com.android.tv.R;
import com.android.tv.TvApplication;
import com.android.tv.common.SoftPreconditions;
import com.android.tv.data.Program;
import com.android.tv.dvr.DvrDataManager;
import com.android.tv.dvr.DvrManager;
import com.android.tv.dvr.data.ScheduledRecording;
import com.android.tv.dvr.data.SeriesRecording;
import com.android.tv.dvr.ui.list.SchedulesHeaderRow.SeriesRecordingHeaderRow;
import com.android.tv.util.Utils;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

/**
 * An adapter for series schedule row.
 */
@TargetApi(Build.VERSION_CODES.N)
class SeriesScheduleRowAdapter extends ScheduleRowAdapter {
    private static final String TAG = "SeriesRowAdapter";
    private static final boolean DEBUG = false;

    private final SeriesRecording mSeriesRecording;
    private final String mInputId;
    private final DvrManager mDvrManager;
    private final DvrDataManager mDataManager;
    private final Map<Long, Program> mPrograms = new ArrayMap<>();
    private SeriesRecordingHeaderRow mHeaderRow;

    public SeriesScheduleRowAdapter(Context context, ClassPresenterSelector classPresenterSelector,
            SeriesRecording seriesRecording) {
        super(context, classPresenterSelector);
        mSeriesRecording = seriesRecording;
        TvInputInfo input = Utils.getTvInputInfoForInputId(context, mSeriesRecording.getInputId());
        if (SoftPreconditions.checkNotNull(input) != null) {
            mInputId = input.getId();
        } else {
            mInputId = null;
        }
        ApplicationSingletons singletons = TvApplication.getSingletons(context);
        mDvrManager = singletons.getDvrManager();
        mDataManager = singletons.getDvrDataManager();
        setHasStableIds(true);
    }

    @Override
    public void start() {
        setPrograms(Collections.emptyList());
    }

    @Override
    public void stop() {
        super.stop();
    }

    /**
     * Sets the programs to show.
     */
    public void setPrograms(List<Program> programs) {
        if (programs == null) {
            programs = Collections.emptyList();
        }
        clear();
        mPrograms.clear();
        List<Program> sortedPrograms = new ArrayList<>(programs);
        Collections.sort(sortedPrograms);
        List<EpisodicProgramRow> rows = new ArrayList<>();
        mHeaderRow = new SeriesRecordingHeaderRow(mSeriesRecording.getTitle(),
                null, sortedPrograms.size(), mSeriesRecording, programs);
        for (Program program : sortedPrograms) {
            ScheduledRecording schedule =
                    mDataManager.getScheduledRecordingForProgramId(program.getId());
            if (schedule != null && !willBeKept(schedule)) {
                schedule = null;
            }
            rows.add(new EpisodicProgramRow(mInputId, program, schedule, mHeaderRow));
            mPrograms.put(program.getId(), program);
        }
        mHeaderRow.setDescription(getDescription());
        add(mHeaderRow);
        for (EpisodicProgramRow row : rows) {
            add(row);
        }
        sendNextUpdateMessage(System.currentTimeMillis());
    }

    private String getDescription() {
        int conflicts = 0;
        for (long programId : mPrograms.keySet()) {
            if (mDvrManager.isConflicting(
                    mDataManager.getScheduledRecordingForProgramId(programId))) {
                ++conflicts;
            }
        }
        return conflicts == 0 ? null : getContext().getResources().getQuantityString(
                R.plurals.dvr_series_schedules_header_description, conflicts, conflicts);
    }

    @Override
    public long getId(int position) {
        Object obj = get(position);
        if (obj instanceof EpisodicProgramRow) {
            return ((EpisodicProgramRow) obj).getProgram().getId();
        }
        if (obj instanceof SeriesRecordingHeaderRow) {
            return 0;
        }
        return super.getId(position);
    }

    @Override
    public void onScheduledRecordingAdded(ScheduledRecording schedule) {
        if (DEBUG) Log.d(TAG, "onScheduledRecordingAdded: " + schedule);
        int index = findRowIndexByProgramId(schedule.getProgramId());
        if (index != -1) {
            EpisodicProgramRow row = (EpisodicProgramRow) get(index);
            if (!row.isStartRecordingRequested()) {
                setScheduleToRow(row, schedule);
                notifyArrayItemRangeChanged(index, 1);
            }
        }
    }

    @Override
    public void onScheduledRecordingRemoved(ScheduledRecording schedule) {
        if (DEBUG) Log.d(TAG, "onScheduledRecordingRemoved: " + schedule);
        int index = findRowIndexByProgramId(schedule.getProgramId());
        if (index != -1) {
            EpisodicProgramRow row = (EpisodicProgramRow) get(index);
            row.setSchedule(null);
            notifyArrayItemRangeChanged(index, 1);
        }
    }

    @Override
    public void onScheduledRecordingUpdated(ScheduledRecording schedule, boolean conflictChange) {
        if (DEBUG) Log.d(TAG, "onScheduledRecordingUpdated: " + schedule);
        int index = findRowIndexByProgramId(schedule.getProgramId());
        if (index != -1) {
            EpisodicProgramRow row = (EpisodicProgramRow) get(index);
            if (conflictChange && isStartOrStopRequested()) {
                // Delay the conflict update until it gets the response of the start/stop request.
                // The purpose is to avoid the intermediate conflict change.
                addPendingUpdate(row);
                return;
            }
            if (row.isStopRecordingRequested()) {
                // Wait until the recording is finished
                if (schedule.getState() == ScheduledRecording.STATE_RECORDING_FINISHED
                        || schedule.getState() == ScheduledRecording.STATE_RECORDING_CLIPPED
                        || schedule.getState() == ScheduledRecording.STATE_RECORDING_FAILED) {
                    row.setStopRecordingRequested(false);
                    if (!isStartOrStopRequested()) {
                        executePendingUpdate();
                    }
                    row.setSchedule(null);
                }
            } else if (row.isStartRecordingRequested()) {
                // When the start recording was requested, we give the highest priority. So it is
                // guaranteed that the state will be changed from NOT_STARTED to the other state.
                // Update the row with the next state not to show the intermediate state to avoid
                // blinking.
                if (schedule.getState() != ScheduledRecording.STATE_RECORDING_NOT_STARTED) {
                    row.setStartRecordingRequested(false);
                    if (!isStartOrStopRequested()) {
                        executePendingUpdate();
                    }
                    setScheduleToRow(row, schedule);
                }
            } else {
                setScheduleToRow(row, schedule);
            }
            notifyArrayItemRangeChanged(index, 1);
        }
    }

    public void onSeriesRecordingUpdated(SeriesRecording seriesRecording) {
        if (seriesRecording.getId() == mSeriesRecording.getId()) {
            mHeaderRow.setSeriesRecording(seriesRecording);
            notifyArrayItemRangeChanged(0, 1);
        }
    }

    private void setScheduleToRow(ScheduleRow row, ScheduledRecording schedule) {
        if (schedule != null && willBeKept(schedule)) {
            row.setSchedule(schedule);
        } else {
            row.setSchedule(null);
        }
    }

    private int findRowIndexByProgramId(long programId) {
        for (int i = 0; i < size(); i++) {
            Object item = get(i);
            if (item instanceof EpisodicProgramRow) {
                if (((EpisodicProgramRow) item).getProgram().getId() == programId) {
                    return i;
                }
            }
        }
        return -1;
    }

    @Override
    public void notifyArrayItemRangeChanged(int positionStart, int itemCount) {
        mHeaderRow.setDescription(getDescription());
        super.notifyArrayItemRangeChanged(0, 1);
        super.notifyArrayItemRangeChanged(positionStart, itemCount);
    }

    @Override
    protected void handleUpdateRow(long currentTimeMs) {
        for (Iterator<Program> iter = mPrograms.values().iterator(); iter.hasNext(); ) {
            Program program = iter.next();
            if (program.getEndTimeUtcMillis() <= currentTimeMs) {
                // Remove the old program.
                removeItems(findRowIndexByProgramId(program.getId()), 1);
                iter.remove();
            } else if (program.getStartTimeUtcMillis() < currentTimeMs) {
                // Change the button "START RECORDING"
                notifyItemRangeChanged(findRowIndexByProgramId(program.getId()), 1);
            }
        }
    }

    /**
     * Should take the current time argument which is the time when the programs are checked in
     * handler.
     */
    @Override
    protected long getNextTimerMs(long currentTimeMs) {
        long earliest = Long.MAX_VALUE;
        for (Program program : mPrograms.values()) {
            if (earliest > program.getStartTimeUtcMillis()
                    && program.getStartTimeUtcMillis() >= currentTimeMs) {
                // Need the button from "CREATE SCHEDULE" to "START RECORDING"
                earliest = program.getStartTimeUtcMillis();
            } else if (earliest > program.getEndTimeUtcMillis()) {
                // Need to remove the row.
                earliest = program.getEndTimeUtcMillis();
            }
        }
        return earliest;
    }
}
