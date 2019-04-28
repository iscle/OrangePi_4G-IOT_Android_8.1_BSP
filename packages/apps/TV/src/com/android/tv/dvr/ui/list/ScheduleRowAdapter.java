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
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.support.v17.leanback.widget.ArrayObjectAdapter;
import android.support.v17.leanback.widget.ClassPresenterSelector;
import android.text.format.DateUtils;
import android.util.ArraySet;
import android.util.Log;

import com.android.tv.R;
import com.android.tv.TvApplication;
import com.android.tv.common.SoftPreconditions;
import com.android.tv.dvr.DvrManager;
import com.android.tv.dvr.ui.list.SchedulesHeaderRow.DateHeaderRow;
import com.android.tv.dvr.data.ScheduledRecording;
import com.android.tv.util.Utils;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;

/**
 * An adapter for {@link ScheduleRow}.
 */
class ScheduleRowAdapter extends ArrayObjectAdapter {
    private static final String TAG = "ScheduleRowAdapter";
    private static final boolean DEBUG = false;

    private final static long ONE_DAY_MS = TimeUnit.DAYS.toMillis(1);

    private static final int MSG_UPDATE_ROW = 1;

    private Context mContext;
    private final List<String> mTitles = new ArrayList<>();
    private final Set<ScheduleRow> mPendingUpdate = new ArraySet<>();

    private final Handler mHandler = new Handler(Looper.getMainLooper()) {
        @Override
        public void handleMessage(Message msg) {
            if (msg.what == MSG_UPDATE_ROW) {
                long currentTimeMs = System.currentTimeMillis();
                handleUpdateRow(currentTimeMs);
                sendNextUpdateMessage(currentTimeMs);
            }
        }
    };

    public ScheduleRowAdapter(Context context, ClassPresenterSelector classPresenterSelector) {
        super(classPresenterSelector);
        mContext = context;
        mTitles.add(mContext.getString(R.string.dvr_date_today));
        mTitles.add(mContext.getString(R.string.dvr_date_tomorrow));
    }

    /**
     * Returns context.
     */
    protected Context getContext() {
        return mContext;
    }

    /**
     * Starts schedule row adapter.
     */
    public void start() {
        clear();
        List<ScheduledRecording> recordingList = TvApplication.getSingletons(mContext)
                .getDvrDataManager().getNonStartedScheduledRecordings();
        recordingList.addAll(TvApplication.getSingletons(mContext).getDvrDataManager()
                .getStartedRecordings());
        Collections.sort(recordingList,
                ScheduledRecording.START_TIME_THEN_PRIORITY_THEN_ID_COMPARATOR);
        long deadLine = Utils.getLastMillisecondOfDay(System.currentTimeMillis());
        for (int i = 0; i < recordingList.size();) {
            ArrayList<ScheduledRecording> section = new ArrayList<>();
            while (i < recordingList.size() && recordingList.get(i).getStartTimeMs() < deadLine) {
                section.add(recordingList.get(i++));
            }
            if (!section.isEmpty()) {
                SchedulesHeaderRow headerRow = new DateHeaderRow(calculateHeaderDate(deadLine),
                        mContext.getResources().getQuantityString(
                        R.plurals.dvr_schedules_section_subtitle, section.size(), section.size()),
                        section.size(), deadLine);
                add(headerRow);
                for(ScheduledRecording recording : section){
                    add(new ScheduleRow(recording, headerRow));
                }
            }
            deadLine += ONE_DAY_MS;
        }
        sendNextUpdateMessage(System.currentTimeMillis());
    }

    private String calculateHeaderDate(long deadLine) {
        int titleIndex = (int) ((deadLine -
                Utils.getLastMillisecondOfDay(System.currentTimeMillis())) / ONE_DAY_MS);
        String headerDate;
        if (titleIndex < mTitles.size()) {
            headerDate = mTitles.get(titleIndex);
        } else {
            headerDate = DateUtils.formatDateTime(getContext(), deadLine,
                    DateUtils.FORMAT_SHOW_WEEKDAY | DateUtils.FORMAT_SHOW_DATE
                            | DateUtils.FORMAT_ABBREV_MONTH);
        }
        return headerDate;
    }

    /**
     * Stops schedules row adapter.
     */
    public void stop() {
        mHandler.removeCallbacksAndMessages(null);
        DvrManager dvrManager = TvApplication.getSingletons(getContext()).getDvrManager();
        for (int i = 0; i < size(); i++) {
            if (get(i) instanceof ScheduleRow) {
                ScheduleRow row = (ScheduleRow) get(i);
                if (row.isScheduleCanceled()) {
                    dvrManager.removeScheduledRecording(row.getSchedule());
                }
            }
        }
    }

    /**
     * Gets which {@link ScheduleRow} the {@link ScheduledRecording} belongs to.
     */
    public ScheduleRow findRowByScheduledRecording(ScheduledRecording recording) {
        if (recording == null) {
            return null;
        }
        for (int i = 0; i < size(); i++) {
            Object item = get(i);
            if (item instanceof ScheduleRow && ((ScheduleRow) item).getSchedule() != null) {
                if (((ScheduleRow) item).getSchedule().getId() == recording.getId()) {
                    return (ScheduleRow) item;
                }
            }
        }
        return null;
    }

    private ScheduleRow findRowWithStartRequest(ScheduledRecording schedule) {
        for (int i = 0; i < size(); i++) {
            Object item = get(i);
            if (!(item instanceof ScheduleRow)) {
                continue;
            }
            ScheduleRow row = (ScheduleRow) item;
            if (row.getSchedule() != null && row.isStartRecordingRequested()
                    && row.matchSchedule(schedule)) {
                return row;
            }
        }
        return null;
    }

    private void addScheduleRow(ScheduledRecording recording) {
        // This method must not be called from inherited class.
        SoftPreconditions.checkState(getClass().equals(ScheduleRowAdapter.class));
        if (recording != null) {
            int pre = -1;
            int index = 0;
            for (; index < size(); index++) {
                if (get(index) instanceof ScheduleRow) {
                    ScheduleRow scheduleRow = (ScheduleRow) get(index);
                    if (ScheduledRecording.START_TIME_THEN_PRIORITY_THEN_ID_COMPARATOR.compare(
                            scheduleRow.getSchedule(), recording) > 0) {
                        break;
                    }
                    pre = index;
                }
            }
            long deadLine = Utils.getLastMillisecondOfDay(recording.getStartTimeMs());
            if (pre >= 0 && getHeaderRow(pre).getDeadLineMs() == deadLine) {
                SchedulesHeaderRow headerRow = ((ScheduleRow) get(pre)).getHeaderRow();
                headerRow.setItemCount(headerRow.getItemCount() + 1);
                ScheduleRow addedRow = new ScheduleRow(recording, headerRow);
                add(++pre, addedRow);
                updateHeaderDescription(headerRow);
            } else if (index < size() && getHeaderRow(index).getDeadLineMs() == deadLine) {
                SchedulesHeaderRow headerRow = ((ScheduleRow) get(index)).getHeaderRow();
                headerRow.setItemCount(headerRow.getItemCount() + 1);
                ScheduleRow addedRow = new ScheduleRow(recording, headerRow);
                add(index, addedRow);
                updateHeaderDescription(headerRow);
            } else {
                SchedulesHeaderRow headerRow = new DateHeaderRow(calculateHeaderDate(deadLine),
                        mContext.getResources().getQuantityString(
                        R.plurals.dvr_schedules_section_subtitle, 1, 1), 1, deadLine);
                add(++pre, headerRow);
                ScheduleRow addedRow = new ScheduleRow(recording, headerRow);
                add(pre, addedRow);
            }
        }
    }

    private DateHeaderRow getHeaderRow(int index) {
        return ((DateHeaderRow) ((ScheduleRow) get(index)).getHeaderRow());
    }

    private void removeScheduleRow(ScheduleRow scheduleRow) {
        // This method must not be called from inherited class.
        SoftPreconditions.checkState(getClass().equals(ScheduleRowAdapter.class));
        if (scheduleRow != null) {
            scheduleRow.setSchedule(null);
            SchedulesHeaderRow headerRow = scheduleRow.getHeaderRow();
            remove(scheduleRow);
            // Changes the count information of header which the removed row belongs to.
            if (headerRow != null) {
                int currentCount = headerRow.getItemCount();
                headerRow.setItemCount(--currentCount);
                if (headerRow.getItemCount() == 0) {
                    remove(headerRow);
                } else {
                    replace(indexOf(headerRow), headerRow);
                    updateHeaderDescription(headerRow);
                }
            }
        }
    }

    private void updateHeaderDescription(SchedulesHeaderRow headerRow) {
        headerRow.setDescription(mContext.getResources().getQuantityString(
                R.plurals.dvr_schedules_section_subtitle,
                headerRow.getItemCount(), headerRow.getItemCount()));
    }

    /**
     * Called when a schedule recording is added to dvr date manager.
     */
    public void onScheduledRecordingAdded(ScheduledRecording schedule) {
        if (DEBUG) Log.d(TAG, "onScheduledRecordingAdded: " + schedule);
        ScheduleRow row = findRowWithStartRequest(schedule);
        // If the start recording is requested, onScheduledRecordingAdded is called with NOT_STARTED
        // state. And then onScheduleRecordingUpdated will be called with IN_PROGRESS.
        // It happens in a short time and causes blinking. To avoid this intermediate state change,
        // update the row in onScheduleRecordingUpdated when the state changes to IN_PROGRESS
        // instead of in this method.
        if (row == null) {
            addScheduleRow(schedule);
            sendNextUpdateMessage(System.currentTimeMillis());
        }
    }

    /**
     * Called when a schedule recording is removed from dvr date manager.
     */
    public void onScheduledRecordingRemoved(ScheduledRecording schedule) {
        if (DEBUG) Log.d(TAG, "onScheduledRecordingRemoved: " + schedule);
        ScheduleRow row = findRowByScheduledRecording(schedule);
        if (row != null) {
            removeScheduleRow(row);
            notifyArrayItemRangeChanged(indexOf(row), 1);
            sendNextUpdateMessage(System.currentTimeMillis());
        }
    }

    /**
     * Called when a schedule recording is updated in dvr date manager.
     */
    public void onScheduledRecordingUpdated(ScheduledRecording schedule, boolean conflictChange) {
        if (DEBUG) Log.d(TAG, "onScheduledRecordingUpdated: " + schedule);
        ScheduleRow row = findRowByScheduledRecording(schedule);
        if (row != null) {
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
                    row.setSchedule(schedule);
                }
            } else {
                row.setSchedule(schedule);
                if (!willBeKept(schedule)) {
                    removeScheduleRow(row);
                }
            }
            notifyArrayItemRangeChanged(indexOf(row), 1);
            sendNextUpdateMessage(System.currentTimeMillis());
        } else {
            row = findRowWithStartRequest(schedule);
            // When the start recording was requested, we give the highest priority. So it is
            // guaranteed that the state will be changed from NOT_STARTED to the other state.
            // Update the row with the next state not to show the intermediate state which causes
            // blinking.
            if (row != null
                    && schedule.getState() != ScheduledRecording.STATE_RECORDING_NOT_STARTED) {
                // This can be called multiple times, so do not call
                // ScheduleRow.setStartRecordingRequested(false) here.
                row.setStartRecordingRequested(false);
                if (!isStartOrStopRequested()) {
                    executePendingUpdate();
                }
                row.setSchedule(schedule);
                notifyArrayItemRangeChanged(indexOf(row), 1);
                sendNextUpdateMessage(System.currentTimeMillis());
            }
        }
    }

    /**
     * Checks if there is a row which requested start/stop recording.
     */
    protected boolean isStartOrStopRequested() {
        for (int i = 0; i < size(); i++) {
            Object item = get(i);
            if (item instanceof ScheduleRow) {
                ScheduleRow row = (ScheduleRow) item;
                if (row.isStartRecordingRequested() || row.isStopRecordingRequested()) {
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * Delays update of the row.
     */
    protected void addPendingUpdate(ScheduleRow row) {
        mPendingUpdate.add(row);
    }

    /**
     * Executes the pending updates.
     */
    protected void executePendingUpdate() {
        for (ScheduleRow row : mPendingUpdate) {
            int index = indexOf(row);
            if (index != -1) {
                notifyArrayItemRangeChanged(index, 1);
            }
        }
        mPendingUpdate.clear();
    }

    /**
     * To check whether the recording should be kept or not.
     */
    protected boolean willBeKept(ScheduledRecording schedule) {
        // CANCELED state means that the schedule was removed temporarily, which should be shown
        // in the list so that the user can reschedule it.
        return schedule.getEndTimeMs() > System.currentTimeMillis()
                && (schedule.getState() == ScheduledRecording.STATE_RECORDING_IN_PROGRESS
                || schedule.getState() == ScheduledRecording.STATE_RECORDING_NOT_STARTED
                || schedule.getState() == ScheduledRecording.STATE_RECORDING_CANCELED);
    }

    /**
     * Handle the message to update/remove rows.
     */
    protected void handleUpdateRow(long currentTimeMs) {
        for (int i = 0; i < size(); i++) {
            Object item = get(i);
            if (item instanceof ScheduleRow) {
                ScheduleRow row = (ScheduleRow) item;
                if (row.getEndTimeMs() <= currentTimeMs) {
                    removeScheduleRow(row);
                }
            }
        }
    }

    /**
     * Returns the next update time. Return {@link Long#MAX_VALUE} if no timer is necessary.
     */
    protected long getNextTimerMs(long currentTimeMs) {
        long earliest = Long.MAX_VALUE;
        for (int i = 0; i < size(); i++) {
            Object item = get(i);
            if (item instanceof ScheduleRow) {
                // If the schedule was finished earlier than the end time, it should be removed
                // when it reaches the end time in this class.
                ScheduleRow row = (ScheduleRow) item;
                if (earliest > row.getEndTimeMs()) {
                    earliest = row.getEndTimeMs();
                }
            }
        }
        return earliest;
    }

    /**
     * Send update message at the time returned by {@link #getNextTimerMs}.
     */
    protected final void sendNextUpdateMessage(long currentTimeMs) {
        mHandler.removeMessages(MSG_UPDATE_ROW);
        long nextTime = getNextTimerMs(currentTimeMs);
        if (nextTime != Long.MAX_VALUE) {
            mHandler.sendEmptyMessageDelayed(MSG_UPDATE_ROW,
                    nextTime - System.currentTimeMillis());
        }
    }
}
